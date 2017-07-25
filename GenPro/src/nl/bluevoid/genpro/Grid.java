/*
 * Copyright 2002-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.bluevoid.genpro;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

import nl.bluevoid.genpro.cell.Calculable;
import nl.bluevoid.genpro.cell.CallCell;
import nl.bluevoid.genpro.cell.Cell;
import nl.bluevoid.genpro.cell.CellInterface;
import nl.bluevoid.genpro.cell.ConstantCell;
import nl.bluevoid.genpro.cell.InputCell;
import nl.bluevoid.genpro.cell.LibraryCell;
import nl.bluevoid.genpro.cell.NoCellFoundException;
import nl.bluevoid.genpro.cell.NoCellOfTypeFoundException;
import nl.bluevoid.genpro.cell.ReferenceCell;
import nl.bluevoid.genpro.cell.UnconnectableGridException;
import nl.bluevoid.genpro.cell.ValueCell;
import nl.bluevoid.genpro.cell.switx.BooleanSwitchCell;
import nl.bluevoid.genpro.cell.switx.NumberSwitchCell;
import nl.bluevoid.genpro.cell.switx.SwitchCell;
import nl.bluevoid.genpro.util.Sneak;
import nl.bluevoid.genpro.util.XMLBuilder;

/**
 * @author Rob van der Veer
 * @since 1.0
 */
public class Grid implements Cloneable, Comparable<Grid> {
  public static final Random random = new Random(System.currentTimeMillis());

  private final Setup setup;
  private double score = -1;

  public String name;
  // calc execution
  private HashMap<String, ValueCell> inOutCellsMap = new HashMap<String, ValueCell>();
  private InputCell[] inputCells;
  private LibraryCell[] libraryCells;

  // mutatables: voor cross en mutate
  private ConstantCell[] constantCells;
  private Calculable[] callCells = new Calculable[0];
  private ReferenceCell[] outputCells;

  private int mutatedConstants = 0;
  private int mutatedParams = 0;
  private int mutatedMethods = 0;

  public Thread calculatedBy;

  public double[] oldCalcResults;
  public double[] calcResults;

  private ArrayList<String> history = new ArrayList<String>();

  private ArrayList<GridExecutionError> errors = new ArrayList<GridExecutionError>();

  // benodigde weergaves:

  // inOutCellsMap: inputs en outputs. Lijst voor input/result voor en na berekenen.
  // calculables: callcells and outputs. Lijst voor berekenen.
  // mutatables: constants + calculables. Alle objects voor cross.

  // targetsAndParamCells: inputs, libs + mutatables. Dynamische lijst opgebouwd tijdens create, mutate and
  // restore.
  // callTargetMethods: alle methods op targetAndParamCells. Dynamische lijst opgebouwd tijdens create and
  // mutate.

  // basis: mutatables met pointer voor calculables??

  // params/methods cross mutate restore calc in/out toJava persist
  // setup x
  // inputs x x x x x
  // libs x x x x
  // constants x x x x x x
  // callcells x x x x x x x
  // outputs ? x x x x x x

  // http://jung.sourceforge.net/applet/pluggablerendererdemo.html
  // http://jung.sourceforge.net/doc/index.html

  // javax.tools.javaCompiler

  Grid(Setup setup, ConstantCell[] constantCells2, LibraryCell[] libraryCells2) {
    this.setup = setup;
    constantCells = constantCells2;
    libraryCells = libraryCells2;
  }

  public void calc() throws GridExecutionError {
    // list calcCells & call calculate
    try {
      for (final Calculable cell : callCells) {
        try {
          if (cell.isUsedForOutput()) {// only call calc on cells that are really used
            cell.calc();
          }
        } catch (final GridExecutionError e) {
          errors.add(e);
          throw e;
        }
      }
    } catch (final RuntimeException e) {
      e.printStackTrace();
      printSolution();
      throw e;
    } catch (IllegalAccessException e) {
      Sneak.sneakyThrow(e);
    } catch (InvocationTargetException e) {
      Sneak.sneakyThrow(e);
    }
  }

  /**
   * 
   * @param grid
   * @param generationNr
   * @return an Array of Grids, where some might be null! This happens when crossing fails to give a working
   *         result
   */
  public synchronized Grid[] cross(final Grid grid, int generationNr) {
    Grid g1 = grid.clone();
    Grid g2 = this.clone();

    // for crossing we need a list of all constants, callcells and outputs
    final ArrayList<Cell> gCells1 = g1.getCellsForCrossing();
    final ArrayList<Cell> gCells2 = g2.getCellsForCrossing();

    // find smallest solution
    final int size = Math.min(gCells1.size(), gCells2.size());
    // cut somewhere, minimum 1
    final int cut = Math.max(1, random.nextInt(size - 1));
    final ArrayList<Cell> child = new ArrayList<Cell>();
    final ArrayList<Cell> child2 = new ArrayList<Cell>();

    child.addAll(gCells1.subList(0, cut));
    child.addAll(gCells2.subList(cut, gCells2.size()));

    child2.addAll(gCells2.subList(0, cut));
    child2.addAll(gCells1.subList(cut, gCells1.size()));

    g1 = g1.tryToSet(child);
    g2 = g2.tryToSet(child2);

    return new Grid[] { g2, g1 };
  }

  /**
   * 
   * @param g1
   * @param child
   * @return null on error!! or this on succes!
   */
  private Grid tryToSet(final ArrayList<Cell> child) {
    try {
      // divide cells: constants, callcells, outputcells
      final ArrayList<ConstantCell> constant = new ArrayList<ConstantCell>();
      final ArrayList<Calculable> call = new ArrayList<Calculable>();
      final ArrayList<ReferenceCell> output = new ArrayList<ReferenceCell>();

      for (final Cell valueCell : child) {
        switch (valueCell.getCellType()) {
        case ConstantCell:
          constant.add((ConstantCell) valueCell);
          break;
        case ReferenceCell:
          output.add((ReferenceCell) valueCell);
          break;
        case CallCell:
        case NumberSwitchCell:
        case BooleanSwitchCell:
          call.add((Calculable) valueCell);
          break;
        default:
          throw new IllegalArgumentException(" invalid celltype: " + valueCell);
        }
      }
      constantCells = constant.toArray(new ConstantCell[constant.size()]);
      callCells = call.toArray(new Calculable[call.size()]);
      setOutPutCells(output.toArray(new ReferenceCell[output.size()]));
      restoreConnections();
      history.clear();
      recalcAndFixConnectivity();
    } catch (UnconnectableGridException t) {
      return null;
    } catch (NoCellFoundException e) {
      for (Cell cell : child) {
        e.addInfo("" + cell.getCellType() + " " + cell.getName() + " " + ((ValueCell) cell).getValueType());
      }
      return null;
    }
    return this;
  }

  private ArrayList<Cell> getCellsForCrossing() {
    final ArrayList<Cell> cells = new ArrayList<Cell>();
    Util.addCells(constantCells, cells);
    Util.addCells(callCells, cells);
    Util.addCells(outputCells, cells);
    return cells;
  }

  private void restoreConnections() throws NoCellFoundException {
    final CellMap hm = new CellMap();

    // add inputs
    for (final ValueCell valueCell : inputCells) {
      hm.putByName(valueCell);
    }
    // add constants
    for (final ValueCell valueCell : constantCells) {
      hm.putByName(valueCell);
    }
    try {
      // let cells find right cells from map
      for (final Calculable valueCell : callCells) {
        ((Calculable) valueCell).restoreConnections(hm);
        // add cell so follow up cells can refer to it
        hm.putByName((ValueCell) valueCell);
      }
      for (final ReferenceCell valueCell : outputCells) {
        ((ReferenceCell) valueCell).restoreConnections(hm);
      }
    } catch (NoCellOfTypeFoundException ncotf) {
      throw new UnconnectableGridException(ncotf);
    }
  }

  public void mutate(final String historyPrefix) throws NoCellFoundException {
    final int num = constantCells.length + callCells.length;

    if (num == 0)
      return;
    final int choice = random.nextInt(num);
    if (choice < constantCells.length) {
      // mutate constant
      if (constantCells[choice].canMutate()) {// TODO make this not select-able
        constantCells[choice].mutate();
        mutatedConstants++;
        if (setup.isGridHistoryTrackingOn()) {
          addToHistory(historyPrefix + "mutated constant " + constantCells[choice].getName());
        }
      }
      // TODO this is absolutely weird but needed to keep good solutions, why? doe sthe clone of bestsolution
      // need a fix??
      recalcAndFixConnectivity();
    } else {
      // mutate callcell
      final int place2 = choice - constantCells.length;
      // gather all options
      final ArrayList<ValueCell> paramCells = getParamsTillCallcell(place2);
      final HashMap<Class<?>, ArrayList<CallTarget>> callTargetsByReturnType = getCallTargetsTillCallCell(place2);

      callCells[place2].mutate(callTargetsByReturnType, paramCells);
      if (setup.isGridHistoryTrackingOn()) {
        addToHistory(historyPrefix + "mutated callcell " + callCells[place2].getName());
      }
      recalcAndFixConnectivity();
    }

    // TODO: delete Cell
    // TODO: add Cell
    // TODO: mutate output to point at different cell
  }

  public Collection<Cell> getUsedCells() {
    final CellMap used = new CellMap();
    // go backtrack what is used
    for (final ReferenceCell out : outputCells) {
      Util.addUsedCellsRecursivly(used, out.getReferedCell());
    }
    // Debug.println("used cells:");
    // Util.printCells(used.getCells().toArray(new ValueCell[0]));
    return used.getCells();
  }

  public void stripUnusedCells() {
    final ArrayList<Cell> ccs = new ArrayList<Cell>();
    final Collection<? extends CellInterface> used = getUsedCells();
    for (final CellInterface cell : callCells) { // TODO make faster, un-optimized
      if (used.contains(cell)) {
        ccs.add((Cell) cell);
      }
    }
    callCells = ccs.toArray(new Calculable[ccs.size()]);

    final ArrayList<ConstantCell> consts = new ArrayList<ConstantCell>();
    for (final ConstantCell cell : constantCells) {
      if (used.contains(cell)) {
        consts.add(cell);
      }
    }
    constantCells = consts.toArray(new ConstantCell[consts.size()]);
  }

  /**
   * 
   * @param place
   *          till, does not include the cell with placenr
   * @return
   */
  private HashMap<Class<?>, ArrayList<CallTarget>> getCallTargetsTillCallCell(final int place) {
    final HashMap<Class<?>, ArrayList<CallTarget>> callTargetsByReturnType = new HashMap<Class<?>, ArrayList<CallTarget>>();
    Util.addCallTargets(libraryCells, callTargetsByReturnType);
    for (int i = 0; i < place; i++) {
      Util.addCallTarget(callCells[i], callTargetsByReturnType);
    }
    return callTargetsByReturnType;
  }

  private ArrayList<ValueCell> getParamsTillCallcell(final int place) {
    final ArrayList<ValueCell> paramCells = new ArrayList<ValueCell>();
    for (final LibraryCell lCell : libraryCells) {
      if (!lCell.isStaticOnly()) {
        paramCells.add(lCell);
      }
    }
    Util.addCells(inputCells, paramCells);
    Util.addCells(constantCells, paramCells);
    for (int i = 0; i < place; i++) {
      if (callCells[i] instanceof ValueCell) {
        paramCells.add((ValueCell) callCells[i]);
      }
    }
    return paramCells;
  }

  public synchronized void createSolution() {
    boolean success = false;
    int tries = 0;
    while (!success)
      try {
        tryToCreateSolution();
        recalcIsUsedForOutput();
        success = true;
      } catch (NoCellFoundException e) {
        if (++tries > 80) {
          throw new IllegalStateException("No succesfull solution within 80 tries!!??", e);
        }
      }
  }

  private void tryToCreateSolution() throws NoCellFoundException {
    final ArrayList<ValueCell> paramCells = new ArrayList<ValueCell>();
    final HashMap<Class<?>, ArrayList<CallTarget>> callTargetsByReturnType = new HashMap<Class<?>, ArrayList<CallTarget>>();

    // add libcells to calltargets
    Util.addCallTargets(libraryCells, callTargetsByReturnType);
    for (final LibraryCell lCell : libraryCells) {
      if (lCell.getValue() != null) {
        paramCells.add(lCell);
      }
    }

    // create inputcells
    inputCells = new InputCell[setup.inputCellDataMap.size()];
    {
      int i = 0;
      for (final String name : setup.inputCellDataMap.keySet()) {
        final InputCell cell = new InputCell(name, setup.inputCellDataMap.get(name));
        inOutCellsMap.put(name, cell);
        inputCells[i++] = cell;
      }
    }
    Util.addCells(inputCells, paramCells);

    // create new constants with different value
    for (int i = 0; i < constantCells.length; i++) {
      if (constantCells[i].canMutate()) {
        constantCells[i].setRandomValue();
      }
    }
    Util.addCells(constantCells, paramCells);

    // create Calculable's
    LinkedList<Calculable> gridCells_local = new LinkedList<Calculable>();
    // printCallTargets();

    int switchNrAdded = 0;
    double switchNrRatio = setup.getMaxSwitchCellNr() / (double) setup.getCallCellNumber();
    for (int i = 0; i < setup.getCallCellNumber(); i++) {
      String name = setup.getCallCellNamePrefix() + (i + 1);
      if (switchNrAdded < setup.getMaxSwitchCellNr() && random.nextDouble() < switchNrRatio) {
        gridCells_local.add(getSwitchCell(name));
        switchNrAdded++;
      } else {
        gridCells_local.add(new CallCell(name, setup.getRandomCallCellType()));
      }
    }

    // connect callcells
    for (final Calculable cell : gridCells_local) {
      // Debug.println("Connecting:" + cell);
      cell.connectCell(callTargetsByReturnType, paramCells);
      switch (cell.getCellType()) {
      case CallCell:
      case NumberSwitchCell:
      case BooleanSwitchCell:
        paramCells.add((ValueCell) cell);
        // it is connected so it can be used to connect to and use as parameter
        Util.addCallTarget(cell, callTargetsByReturnType);
        break;
      // case IfCell:
      // final ArrayList<? extends ValueCell> values = ((IfCell) cell).getValueCells();
      // paramCells.addAll(values);
      // Util.addCallTargets(values, callTargetsByReturnType);
      // break;
      default:
        throw new IllegalArgumentException("not supported:" + cell);
      }
    }
    callCells = gridCells_local.toArray(new Calculable[gridCells_local.size()]);

    // create outputcells
    final ReferenceCell[] outputs_local = new ReferenceCell[setup.outputCellDataMap.size()];
    int i = 0;
    for (final String name : setup.outputCellDataMap.keySet()) {
      outputs_local[i++] = new ReferenceCell(name, setup.outputCellDataMap.get(name));
    }

    if (callCells.length < 1) {
      throw new IllegalStateException("We need 1 Callcell at least to connect the outputs to!");
    }
    // connect outputcells, we only connect to callcells, so expect 1 callcell at least!!
    for (ReferenceCell output : outputs_local) {
      connectOutput(output);
    }
    setOutPutCells(outputs_local);
  }

  private void connectOutput(ReferenceCell output) throws NoCellFoundException {
    int tries = 0;
    final int maxTries = callCells.length * 2;
    while (true) {
      // we search for a cell that connects to an input otherwise it will be a dead grid
      ValueCell cell = Util.getRandomCellFromCalculables(output.getValueType(), callCells, true);

      if (cell.isLeadsToInputCell()) {
        output.setReferedCell(cell);
        break;
      }
      tries++;
      if (tries > maxTries) {
        // failed after many tries, give up
        throw new NoCellFoundException("cannot find a valid connection that leads to an input:"
            + Util.toStringCells(callCells));
      }
    }
  }

  private SwitchCell getSwitchCell(String name) {
    Class<?> type = setup.getRandomCallCellTypeForSwitch();
    Class<?> valueType = setup.getRandomCallCellType();

    if (type.equals(Boolean.class)) {
      return new BooleanSwitchCell(name, valueType);
    } else if (Number.class.isAssignableFrom(type)) {
      return new NumberSwitchCell(name, valueType, 10, -10000, +10000);// TODO make configurable
    } else {
      throw new IllegalStateException("unsupported type:" + type.getName());
    }
  }

  private void setOutPutCells(final ReferenceCell[] outputs) {
    outputCells = outputs;
    for (final ReferenceCell outputCell : outputCells) {
      inOutCellsMap.put(outputCell.getName(), outputCell);
    }
  }

  public void printSolution() {
    System.out.println("inputCells\n" + Util.toStringCells(inputCells));
    System.out.println("libraryCells\n" + Util.toStringCells(libraryCells));
    System.out.println("constantCells\n" + Util.toStringCells(constantCells));
    System.out.println("gridCells\n" + Util.toStringCells(callCells));
    System.out.println("outputCells\n" + Util.toStringCells(outputCells));
  }

  @SuppressWarnings("unchecked")
  @Override
  public Grid clone() {
    try {
      final Grid clone = (Grid) super.clone();
      clone.constantCells = (ConstantCell[]) Util.clone(constantCells);
      clone.callCells = (Calculable[]) Util.clone(callCells);
      clone.inputCells = (InputCell[]) Util.clone(inputCells);
      clone.libraryCells = (LibraryCell[]) Util.clone(libraryCells);
      clone.outputCells = (ReferenceCell[]) Util.clone(outputCells);
      clone.inOutCellsMap = new HashMap<String, ValueCell>();
      clone.errors = new ArrayList<GridExecutionError>();
      clone.history = (ArrayList<String>) history.clone();
      clone.score = -1;

      for (final ValueCell cell : clone.outputCells) {
        clone.inOutCellsMap.put(cell.getName(), cell);
      }
      for (final ValueCell cell : clone.inputCells) {
        clone.inOutCellsMap.put(cell.getName(), cell);
      }
      clone.restoreConnections();
      return clone;
    } catch (CloneNotSupportedException e) {
      Sneak.sneakyThrow(e);
      return null;// never reached
    } catch (NoCellFoundException e) {
      Sneak.sneakyThrow(e);
      return null;// never reached
    }
  }

  public void setScore(final double score) {
    if (this.score != -1 && Math.abs(this.score - score) > 0.001) {// a score was set already
      throw new IllegalArgumentException("score was " + this.score + " and now set to " + score + " diff="
          + Math.abs(this.score - score) + " by thread " + Thread.currentThread().getName());
    }
    this.score = score;
    calculatedBy = Thread.currentThread();
  }

  public void resetGridExecutionErrors() {
    errors.clear();
  }

  public ArrayList<GridExecutionError> getGridExecutionErrors() {
    return errors;
  }

  public Setup getSetup() {
    return setup;
  }

  public double getScore() {
    return score;
  }

  public InputCell[] getInputCells() {
    return inputCells;
  }

  public ConstantCell[] getConstantCells() {
    return constantCells;
  }

  public ReferenceCell[] getOutputCells() {
    return outputCells;
  }

  public LibraryCell[] getLibraryCells() {
    return libraryCells;
  }

  public ReferenceCell getOutputCell(final String name) {
    return (ReferenceCell) inOutCellsMap.get(name);
  }

  public InputCell getInputCell(final String name) {
    return (InputCell) inOutCellsMap.get(name);
  }

  public Calculable[] getCallCells() {
    return callCells;
  }

  public int getCallCellSize() {
    return callCells.length;
  }

  public int compareTo(Grid o) {
    return (o.score - score <= 0) ? 1 : -1;
  }

  public int getMutatedConstants() {
    return mutatedConstants;
  }

  public int getMutatedParams() {
    return mutatedParams;
  }

  public int getMutatedMethods() {
    return mutatedMethods;
  }

  public String getStats() {
    return " mut consts " + getMutatedConstants() + " " + getMutatedParams() + " " + getMutatedMethods();
  }

  public String getXML() {
    XMLBuilder x = new XMLBuilder();
    x.startTag("grid");
    for (InputCell c : inputCells) {
      c.getXML(x);
    }
    for (LibraryCell c : libraryCells) {
      c.getXML(x);
    }
    for (ConstantCell c : constantCells) {
      c.getXML(x);
    }
    for (Calculable c : callCells) {
      c.getXML(x);
    }
    for (ReferenceCell c : outputCells) {
      c.getXML(x);
    }
    x.endTag();
    return x.toString();
  }

  private void recalcAndFixConnectivity() throws NoCellFoundException {
    recalcIsLeadsToInputCell();
    for (ReferenceCell output : outputCells) {
      if (!output.getReferedCell().isLeadsToInputCell()) {
        // not leading to input: reconnect!
        connectOutput(output);
      }
    }
    recalcIsUsedForOutput();
  }

  public void recalcIsUsedForOutput() {
    for (ReferenceCell c : outputCells) {
      c.setCascadeUsedForOutput();
    }
  }

  private void recalcIsLeadsToInputCell() {
    for (Calculable cell : callCells) {
      cell.validateLeadsToInputCell();
    }
  }

  public double getNrOfUsedCallCells() {
    int used = 0;
    for (Calculable c : callCells) {
      if (c.isUsedForOutput()) {
        used++;
      }
    }
    return used;
  }

  public void resetCellCallCounters() {
    for (Calculable cc : callCells) {
      cc.resetCallAndErrorCounter();
    }
  }

  public ArrayList<String> getHistory() {
    return history;
  }

  public void addToHistory(String historyString) {
    history.add(System.currentTimeMillis() + " : " + historyString);
  }
}