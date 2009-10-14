/*
 * This file is part of GenPro, Reflective Object Oriented Genetic Programming.
 *
 * GenPro offers a dual license model containing the GPL (GNU General Public License) version 2  
 * as well as a commercial license.
 *
 * For licensing information please see the file license.txt included with GenPro
 * or have a look at the top of class nl.bluevoid.genpro.cell.Cell which representatively
 * includes the GenPro license policy applicable for any file delivered with GenPro.
 */

package nl.bluevoid.genpro;

import java.util.ArrayList;
import java.util.HashMap;

import nl.bluevoid.genpro.cell.Calculable;
import nl.bluevoid.genpro.cell.CallCell;
import nl.bluevoid.genpro.cell.CellInterface;
import nl.bluevoid.genpro.cell.ConstantCell;
import nl.bluevoid.genpro.cell.InputCell;
import nl.bluevoid.genpro.cell.ReferenceCell;
import nl.bluevoid.genpro.cell.ValueCell;
import nl.bluevoid.genpro.cell.candidates.IfCell;
import nl.bluevoid.genpro.operations.BooleanOperations;
import nl.bluevoid.genpro.operations.NumberOperations;
import nl.bluevoid.genpro.util.Debug;
import nl.bluevoid.genpro.util.StringUtil;

public class JavaGenerator {
  HashMap<String, String> imports = new HashMap<String, String>();

  ArrayList<String> globalAttributes = new ArrayList<String>();

  ArrayList<String> methods = new ArrayList<String>();

  ArrayList<String> program = new ArrayList<String>();

  private boolean debug;

  // public static void printJavaProgram(Grid grid2, String className, boolean debug) {
  // Debug.checkNotNull(grid2, "grid2");
  // JavaGenerator gen = new JavaGenerator();
  // String java = gen.getProgram(className, "nl.bluevoid.gp", grid2, debug);
  // System.out.println(java);
  // }

  public static void printJavaProgram(Grid grid, String className,String packageName, boolean debug) {
    String java = getJavaProgram(grid, className, packageName, debug);
    System.out.println(java);
  }

  public static String getJavaProgram(Grid grid2, String className, String packageName, boolean debug) {
    Debug.checkNotNull(grid2, "grid2");
    JavaGenerator gen = new JavaGenerator();
    return gen.getProgram(className, packageName, grid2, debug);
  }

  public void addImport(String packageOrClassName) {
    imports.put(packageOrClassName, packageOrClassName);
  }

  public void addGlobalAttribute(ValueCell cell, Object value) {
    String v = value == null ? "" : " = " + value.toString();
    String f = value == null ? "" : "final ";
    globalAttributes.add("private " + f + cell.getValueType().getSimpleName() + " " + cell.getName()
        + getDebugInfo(cell) + v + ";" + (cell.isUsedForOutput() ? "  //to output" : ""));
  }

  // add more debug info
  private String getDebugInfo(CellInterface cell) {
    return (debug ? " (" + cell.getSerialNr() + ") " : "");
  }

  public void addCell(Calculable cell) {
    switch (cell.getCellType()) {
    case CallCell:
      addGlobalAttribute((CallCell) cell, null);
      addCall((CallCell) cell, 1);
      break;
//    case IfCell:
//      IfCell ifcell = (IfCell) cell;
//      for (ValueCell vcell : ifcell.getValueCells()) {
//        addGlobalAttribute(vcell, null);
//      }
//      addCall((IfCell) cell);
//      break;
    default:
      throw new IllegalArgumentException("not supported:" + cell.toString());
    }
  }

  public void addCell(InputCell cell) {
    addGlobalAttribute(cell, null);
    addSetMethod(cell.getName(), cell.getValueType());
  }

  private void addCell(ConstantCell cell) {
    addGlobalAttribute(cell, cell.getValue());
  }

  public void addCell(ReferenceCell cell) {
    // addGlobalAttribute(cell, null);
    addGetMethod(cell.getName(), cell.getValueType(), cell.getReferedCell());
  }

  public String getProgram(String className, String packageName, Grid grid, boolean debug) {
    this.debug = debug;
    processCells(grid);
    return getProgram(className, packageName);
  }
  
  private String getProgram(String className, String packageName) {
    StringBuffer b = new StringBuffer();

    b.append("package " + packageName + ";");

    // b.append("\n\n//imports");
    for (String s : imports.keySet()) {
      if (!s.startsWith("java.lang.")) {
        b.append("\nimport ");
        b.append(s);
        b.append(";");
      }
    }
    b.append("\n\npublic class " + className + "{");

    b.append("\n\n\t//attributes");
    for (String s : globalAttributes) {
      b.append("\n\t");
      b.append(s);
    }
    b.append("\n\n\t//the program");

    b.append("\n\tpublic void calculate(){");
    for (String s : program) {
      b.append("\n\t");
      b.append(s);
    }
    b.append("\n\t}");

    // b.append("\n\n\t//methods");
    for (String s : methods) {
      b.append("\n");
      for (String line : s.split("\n")) {
        b.append("\n\t");
        b.append(line);
      }
    }
    b.append("\n}");
    return b.toString();
  }

  private void processCells(Grid grid) {
    for (InputCell c : grid.getInputCells()) {
      addCell(c);
    }
    for (ConstantCell c : grid.getConstantCells()) {
      addCell(c);
    }

    // TODO more cells
    for (Calculable c : grid.getCallCells()) {
      addCell(c);
    }

    for (ReferenceCell c : grid.getOutputCells()) {
      addCell(c);
    }
  }

  private void addCall(CallCell cell, int indent) {
    String call = getIndent(indent);
    call += cell.getName() + getDebugInfo(cell) + " = ";
    call += getCallMethod(cell);
    program.add(call);
  }

  /**
   * used to add call from ifcell
   * 
   * @param referenceCell
   * @param callCell
   * @param indent
   */
  private void addCall(ReferenceCell referenceCell, CallCell callCell, int indent) {
    String call = getIndent(indent);
    call += referenceCell.getName() + getDebugInfo(referenceCell) + " = ";
    call += getCallMethod(callCell);
    program.add(call);
  }

  private String getIndent(int indent) {
    String call = "";
    for (int i = 0; i < indent; i++) {
      call += "   ";
    }
    return call;
  }

  private String getCallMethod(CallCell cell) {
    String call = "";
    if (cell.getTargetCell().getValueType().equals(NumberOperations.class)) {
      // we have a +,-,*,/,%
      String op = NumberOperations.getJavaSyntax(cell.getTargetMethod().getName());
      String operation = joinParams(cell.getParams(), " " + op + " ");
      call += operation + ";";
    } else if (cell.getTargetCell().getValueType().equals(BooleanOperations.class)) {
      // we have a <,>,==,!=,&&,||, etc
      String op = BooleanOperations.getJavaSyntax(cell.getTargetMethod().getName());

      String operation = cell.hasParams() ? joinParams(cell.getParams(), " " + op + " ") : op;
      call += operation + ";";
    } else {
      // we have a method call
      String paramsStr = joinParams(cell.getParams(), ",");
      call += cell.getTargetCell().getName() + "." + cell.getTargetMethod().getName() + "(" + paramsStr
          + ");";
      addImport(cell.getTargetCell().getValueType().getName());
    }
    call += "  //" + (cell.isUsedForOutput() ? "to output, " : "");
    call += (cell.isLeadsToInputCell() ? "to input" : "");
    addImport(cell.getValueType().getName());
    return call;
  }

//  private void addCall(IfCell cell) {
//    program.add(getIndent(1) + "if ( " + cell.getBooleanExpression().getName() + " ){");
//    for (int i = 0; i < cell.getOnTrueList().size(); i++) {
//      addCall(cell.getValueCells().get(i), cell.getOnTrueList().get(i), 2);
//    }
//    program.add(getIndent(1) + "} else {");
//    for (int i = 0; i < cell.getOnFalseList().size(); i++) {
//      addCall(cell.getValueCells().get(i), cell.getOnFalseList().get(i), 2);
//    }
//    program.add(getIndent(1) + "}");
//  }
  
 
  private String joinParams(ValueCell[] cells, String seperator) {
    String[] names = new String[cells.length];
    for (int i = 0; i < names.length; i++) {
      names[i] = cells[i].getName() + getDebugInfo(cells[i]);
    }
    return StringUtil.join(seperator, names);
  }

  private void addSetMethod(String name, Class<?> clazz) {
    String code = "public void set" + StringUtil.capitalize(name) + "(" + clazz.getSimpleName() + " " + name
        + "){";
    code += "\n\tthis." + name + " = " + name + ";";
    code += "\n}";
    methods.add(code);
  }

  private void addGetMethod(String name, Class<?> clazz, ValueCell fromCell) {
    String code = "public " + clazz.getSimpleName() + " get" + StringUtil.capitalize(name) + "(){";
    code += "\n\treturn " + fromCell.getName() + getDebugInfo(fromCell) + ";";
    code += "\n}";
    methods.add(code);
  }
}