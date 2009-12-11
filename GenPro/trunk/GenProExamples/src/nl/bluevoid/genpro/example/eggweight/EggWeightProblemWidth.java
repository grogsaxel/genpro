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

package nl.bluevoid.genpro.example.eggweight;

import nl.bluevoid.genpro.Grid;
import nl.bluevoid.genpro.GridSolutionEvaluator;
import nl.bluevoid.genpro.ScoringType;
import nl.bluevoid.genpro.Setup;
import nl.bluevoid.genpro.TestSet;
import nl.bluevoid.genpro.TestSetSolutionEvaluator;
import nl.bluevoid.genpro.cell.ConstantCell;
import nl.bluevoid.genpro.cell.LibraryCell;
import nl.bluevoid.genpro.cell.ReferenceCell;
import nl.bluevoid.genpro.operations.NumberOperations;
import nl.bluevoid.genpro.view.TrainerVisual;

/**
 * 
 * @author Rob van der Veer
 * @since 1.0
 */
public class EggWeightProblemWidth extends TrainerVisual {

  public static void main(String[] args) throws Exception {
    EggWeightProblemWidth ep = new EggWeightProblemWidth();
    ep.startTraining();
  }

  @Override
  public Setup createSetup() {
    Setup setup = new Setup();

    // create all cells
    setup.addInputCell("width", Double.class);
    setup.addOutputCell("weight", Double.class);

    setup.setCallCells(7, "c", Double.class);

    ConstantCell cCell1 = new ConstantCell("const1", Double.class, -100, 100);
    ConstantCell cCell2 = new ConstantCell("const2", Double.class, -100, 100);
    ConstantCell cCell3 = new ConstantCell("const3", Double.class, -100, 100);
    setup.setConstantCells(cCell1, cCell2, cCell3);
    setup.setLibraryCells(NumberOperations.NUM_OPS, // NumberOperations.MATH_CLASS,
        GonioOperations.GONIO_OPS, new LibraryCell(Egg.class));

    setup.setGenerationSize(2000);
    setup.setMutatePercentage(90);
    setup.setCrossingPercentage(30);
    setup.setMaxIndividualsWithSameScore(30);
    setup.setMinimumScoreForSaving(10);
    setup.setEvaluateMultiThreaded(false);
    // setup.setSolutionInterface(EggWeightSolution.class);
    return setup;
  }

  @Override
  public TestSetSolutionEvaluator createEvaluator() {
    GridSolutionEvaluator gse = new GridSolutionEvaluator() {
      public double scoreOutput(ReferenceCell cell, Object calculated, Object expected) {
        return getAbsoluteNumberDifferencePercentage((Number) calculated, (Number) expected);
      }

      @Override
      public double scoreGridException(Throwable t) {
        return 0;
      }

      @Override
      public double scoreGrid(Grid g) {
        return (g.getNrOfUsedCallCells() - 2) * 0.1; // each cell may cost a 0.1 gram deviation
      }

      @Override
      public TestSet createTestSet() {
        TestSet testSet = new TestSet(setup, "width", "weight");
        testSet.addCellValuesFromFile("eggData.txt", TestSet.SKIP_COLUMN, "width", "weight");
        return testSet;
      }
    };
    
    gse.setScoringType(ScoringType.SCORING_HIGHEST_PERCENTAGE_OF_TESTCASES);
    return gse;
  }
}
