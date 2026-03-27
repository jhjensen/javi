package javi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FoldModel} and {@link FoldDetector}.
 */
class FoldModelJUnitTest {

   private FoldModel model;

   @BeforeEach
   void setUp() {
      model = new FoldModel();
   }

   // --- Basic fold operations ---

   @Test
   void emptyModelHasNoFolds() {
      assertTrue(model.isEmpty());
      assertEquals(0, model.size());
   }

   @Test
   void addFoldIncreasesSize() {
      model.addFold(1, 5);
      assertEquals(1, model.size());
      assertFalse(model.isEmpty());
   }

   @Test
   void addFoldRejectsSingleLine() {
      model.addFold(3, 3);
      assertTrue(model.isEmpty());
   }

   @Test
   void addFoldRejectsInverted() {
      model.addFold(5, 1);
      assertTrue(model.isEmpty());
   }

   @Test
   void removeFoldByStartLine() {
      model.addFold(1, 5);
      model.addFold(7, 10);
      model.removeFold(1);
      assertEquals(1, model.size());
      assertNull(model.findFoldAtStart(1));
      assertNotNull(model.findFoldAtStart(7));
   }

   // --- Toggle, open, close ---

   @Test
   void toggleFoldChangesState() {
      model.addFold(1, 10);
      FoldModel.FoldRange f = model.toggleFold(1);
      assertNotNull(f);
      assertTrue(f.collapsed);
      model.toggleFold(1);
      assertFalse(f.collapsed);
   }

   @Test
   void toggleFoldOnInteriorLine() {
      model.addFold(1, 10);
      FoldModel.FoldRange f = model.toggleFold(5);
      assertNotNull(f);
      assertTrue(f.collapsed);
   }

   @Test
   void toggleFoldReturnsNullForNoFold() {
      model.addFold(1, 10);
      assertNull(model.toggleFold(15));
   }

   @Test
   void openFoldAlreadyOpen() {
      model.addFold(1, 10);
      FoldModel.FoldRange f = model.openFold(1);
      assertNotNull(f);
      assertFalse(f.collapsed);
   }

   @Test
   void closeFoldSetsCollapsed() {
      model.addFold(1, 10);
      FoldModel.FoldRange f = model.closeFold(1);
      assertNotNull(f);
      assertTrue(f.collapsed);
   }

   @Test
   void openAllAndCloseAll() {
      model.addFold(1, 5);
      model.addFold(7, 12);
      model.closeAll();
      assertTrue(model.getFolds().get(0).collapsed);
      assertTrue(model.getFolds().get(1).collapsed);
      model.openAll();
      assertFalse(model.getFolds().get(0).collapsed);
      assertFalse(model.getFolds().get(1).collapsed);
   }

   // --- isFolded ---

   @Test
   void isFoldedReturnsFalseWhenOpen() {
      model.addFold(1, 10);
      assertFalse(model.isFolded(5));
   }

   @Test
   void isFoldedReturnsTrueWhenClosed() {
      model.addFold(1, 10);
      model.closeAll();
      assertTrue(model.isFolded(5));
      assertFalse(model.isFolded(1));
   }

   @Test
   void isFoldedStartLineIsVisible() {
      model.addFold(1, 10);
      model.closeAll();
      assertFalse(model.isFolded(1));
   }

   // --- Visible line count ---

   @Test
   void visibleLineCountAllOpen() {
      model.addFold(1, 10);
      assertEquals(20, model.getVisibleLineCount(20));
   }

   @Test
   void visibleLineCountOneClosed() {
      model.addFold(1, 10);
      model.closeAll();
      // Lines 2-10 hidden (9 lines), line 1 still visible
      assertEquals(11, model.getVisibleLineCount(20));
   }

   @Test
   void visibleLineCountMultipleClosed() {
      model.addFold(1, 5);
      model.addFold(10, 15);
      model.closeAll();
      // 4 hidden from first, 5 hidden from second = 9
      assertEquals(11, model.getVisibleLineCount(20));
   }

   // --- Screen-to-buffer mapping ---

   @Test
   void mapScreenToBufferNoFolds() {
      assertEquals(1, model.mapScreenToBuffer(1));
      assertEquals(5, model.mapScreenToBuffer(5));
   }

   @Test
   void mapScreenToBufferWithCollapsed() {
      model.addFold(2, 5);
      model.closeAll();
      // screen 1 → buffer 1
      // screen 2 → buffer 2 (fold start, shows summary)
      // screen 3 → buffer 6 (past the fold)
      assertEquals(1, model.mapScreenToBuffer(1));
      assertEquals(2, model.mapScreenToBuffer(2));
      assertEquals(6, model.mapScreenToBuffer(3));
   }

   @Test
   void mapScreenToBufferTwoCollapsed() {
      model.addFold(2, 5);
      model.addFold(8, 10);
      model.closeAll();
      // screen 1 → buffer 1
      // screen 2 → buffer 2 (fold)
      // screen 3 → buffer 6
      // screen 4 → buffer 7
      // screen 5 → buffer 8 (fold)
      // screen 6 → buffer 11
      assertEquals(1, model.mapScreenToBuffer(1));
      assertEquals(2, model.mapScreenToBuffer(2));
      assertEquals(6, model.mapScreenToBuffer(3));
      assertEquals(7, model.mapScreenToBuffer(4));
      assertEquals(8, model.mapScreenToBuffer(5));
      assertEquals(11, model.mapScreenToBuffer(6));
   }

   // --- Buffer-to-screen mapping ---

   @Test
   void mapBufferToScreenNoFolds() {
      assertEquals(1, model.mapBufferToScreen(1));
      assertEquals(5, model.mapBufferToScreen(5));
   }

   @Test
   void mapBufferToScreenHiddenReturnsNeg1() {
      model.addFold(2, 5);
      model.closeAll();
      assertEquals(-1, model.mapBufferToScreen(3));
      assertEquals(-1, model.mapBufferToScreen(5));
   }

   @Test
   void mapBufferToScreenFoldStart() {
      model.addFold(2, 5);
      model.closeAll();
      assertEquals(1, model.mapBufferToScreen(1));
      assertEquals(2, model.mapBufferToScreen(2));
      assertEquals(3, model.mapBufferToScreen(6));
   }

   // --- Nested folds ---

   @Test
   void nestedFoldsOuterCollapse() {
      model.addFold(1, 20);
      model.addFold(5, 10);
      model.closeFold(1);
      // Everything inside outer fold is hidden
      assertTrue(model.isFolded(5));
      assertTrue(model.isFolded(10));
      assertTrue(model.isFolded(15));
   }

   // --- findFold ---

   @Test
   void findFoldReturnsCorrectRange() {
      model.addFold(5, 15);
      FoldModel.FoldRange f = model.findFold(10);
      assertNotNull(f);
      assertEquals(5, f.startLine);
      assertEquals(15, f.endLine);
   }

   @Test
   void findFoldReturnsNullOutsideRange() {
      model.addFold(5, 15);
      assertNull(model.findFold(20));
   }

   // --- clear ---

   @Test
   void clearRemovesAll() {
      model.addFold(1, 5);
      model.addFold(10, 20);
      model.clear();
      assertTrue(model.isEmpty());
   }

   // --- statusSummary ---

   @Test
   void statusSummaryNoFolds() {
      assertEquals("no folds", model.statusSummary());
   }

   @Test
   void statusSummaryWithFolds() {
      model.addFold(1, 5);
      model.addFold(7, 10);
      model.closeFold(1);
      assertEquals("2 folds (1 closed)", model.statusSummary());
   }

   // --- FoldRange ---

   @Test
   void foldRangeHiddenLines() {
      model.addFold(1, 10);
      FoldModel.FoldRange f = model.getFolds().get(0);
      assertEquals(0, f.hiddenLines());
      f.collapsed = true;
      assertEquals(9, f.hiddenLines());
   }

   @Test
   void foldRangeSpan() {
      model.addFold(1, 10);
      assertEquals(10, model.getFolds().get(0).span());
   }

   @Test
   void foldRangeToString() {
      model.addFold(1, 10);
      String s = model.getFolds().get(0).toString();
      assertTrue(s.contains("1-10"));
      assertTrue(s.contains("open"));
   }

   // --- JSON detection ---

   @Test
   void detectJsonSimpleObject() {
      String[] lines = {
         "", // line 0 (unused, 1-based)
         "{",
         "   \"key\": \"value\"",
         "}",
      };
      FoldDetector.LineFetcher fetcher = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectJsonFolds(fetcher, lines.length);
      assertEquals(1, fm.size());
      FoldModel.FoldRange f = fm.getFolds().get(0);
      assertEquals(1, f.startLine);
      assertEquals(3, f.endLine);
   }

   @Test
   void detectJsonNestedObjects() {
      String[] lines = {
         "",
         "{",
         "   \"obj\": {",
         "      \"a\": 1",
         "   }",
         "}",
      };
      FoldDetector.LineFetcher fetcher = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectJsonFolds(fetcher, lines.length);
      assertEquals(2, fm.size());
   }

   @Test
   void detectJsonArrays() {
      String[] lines = {
         "",
         "[",
         "   1,",
         "   2,",
         "   3",
         "]",
      };
      FoldDetector.LineFetcher fetcher = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectJsonFolds(fetcher, lines.length);
      assertEquals(1, fm.size());
      FoldModel.FoldRange f = fm.getFolds().get(0);
      assertEquals(1, f.startLine);
      assertEquals(5, f.endLine);
   }

   @Test
   void detectJsonSameLineBracesNoFold() {
      String[] lines = {
         "",
         "{ \"inline\": true }",
      };
      FoldDetector.LineFetcher fetcher = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectJsonFolds(fetcher, lines.length);
      assertTrue(fm.isEmpty());
   }

   @Test
   void detectJsonEmptyBuffer() {
      FoldDetector.LineFetcher fetcher = i -> "";
      FoldModel fm =
         FoldDetector.detectJsonFolds(fetcher, 1);
      assertTrue(fm.isEmpty());
   }

   // --- nextVisible / prevVisible ---

   @Test
   void nextVisibleNoFolds() {
      assertEquals(6, model.nextVisible(5));
   }

   @Test
   void nextVisibleAtCollapsedFoldStart() {
      model.addFold(5, 10);
      model.closeAll();
      // At fold start → skip to endLine + 1
      assertEquals(11, model.nextVisible(5));
   }

   @Test
   void nextVisibleBeforeCollapsedFold() {
      model.addFold(5, 10);
      model.closeAll();
      // Line 4 → next is 5 (fold start, visible)
      assertEquals(5, model.nextVisible(4));
   }

   @Test
   void nextVisibleAtOpenFoldStart() {
      model.addFold(5, 10);
      // Open fold → normal increment
      assertEquals(6, model.nextVisible(5));
   }

   @Test
   void nextVisiblePastCollapsedFold() {
      model.addFold(5, 10);
      model.closeAll();
      // Line after fold
      assertEquals(12, model.nextVisible(11));
   }

   @Test
   void prevVisibleNoFolds() {
      assertEquals(4, model.prevVisible(5));
   }

   @Test
   void prevVisibleIntoCollapsedFold() {
      model.addFold(5, 10);
      model.closeAll();
      // Line 11 → prev is 10, inside fold → snap to 5
      assertEquals(5, model.prevVisible(11));
   }

   @Test
   void prevVisibleAtFoldStart() {
      model.addFold(5, 10);
      model.closeAll();
      // Line 5 → prev is 4 (not in fold)
      assertEquals(4, model.prevVisible(5));
   }

   @Test
   void prevVisibleBeforeStart() {
      assertEquals(0, model.prevVisible(1));
   }

   @Test
   void prevVisibleOpenFold() {
      model.addFold(5, 10);
      // Not collapsed → normal prev
      assertEquals(9, model.prevVisible(10));
   }

   // --- foldSummaryText ---

   @Test
   void foldSummaryTextFormat() {
      String s = FoldModel.foldSummaryText(
         5, 15, "   function foo() {");
      assertEquals("+--  10 lines: function foo() {", s);
   }

   // --- Navigation through multiple folds ---

   @Test
   void nextVisibleSkipsTwoConsecutiveFolds() {
      model.addFold(2, 5);
      model.addFold(6, 9);
      model.closeAll();
      // From line 1 → 2 (fold start)
      assertEquals(2, model.nextVisible(1));
      // From fold1 start → fold1 end+1 = 6 (fold2 start)
      assertEquals(6, model.nextVisible(2));
      // From fold2 start → fold2 end+1 = 10
      assertEquals(10, model.nextVisible(6));
   }

   @Test
   void prevVisibleThroughTwoFolds() {
      model.addFold(2, 5);
      model.addFold(6, 9);
      model.closeAll();
      // From 10 → prev is 9, inside fold2 → snap to 6
      assertEquals(6, model.prevVisible(10));
      // From 6 → prev is 5, inside fold1 → snap to 2
      assertEquals(2, model.prevVisible(6));
      // From 2 → prev is 1
      assertEquals(1, model.prevVisible(2));
   }

   // --- Fold span for dd support ---

   @Test
   void foldSpanCoversEntireRange() {
      model.addFold(5, 15);
      FoldModel.FoldRange f = model.findFoldAtStart(5);
      assertNotNull(f);
      assertEquals(11, f.span()); // lines 5-15 inclusive
   }

   @Test
   void removeFoldOnDeletedRange() {
      model.addFold(5, 15);
      model.addFold(20, 30);
      model.closeAll();
      // Simulate dd on fold: get span, remove fold
      FoldModel.FoldRange f = model.findFoldAtStart(5);
      assertNotNull(f);
      assertTrue(f.collapsed);
      assertEquals(11, f.span());
      model.removeFold(5);
      assertEquals(1, model.size());
      assertNull(model.findFoldAtStart(5));
      assertNotNull(model.findFoldAtStart(20));
   }

   @Test
   void findFoldAtStartReturnsNullForInterior() {
      model.addFold(5, 15);
      assertNull(model.findFoldAtStart(10));
      assertNotNull(model.findFoldAtStart(5));
   }

   @Test
   void ddOnNonFoldLineNoExpansion() {
      model.addFold(5, 15);
      // Line 3 is not a fold start
      assertNull(model.findFoldAtStart(3));
   }

   @Test
   void ddOnOpenFoldNoExpansion() {
      model.addFold(5, 15);
      // Fold is open (not collapsed)
      FoldModel.FoldRange f = model.findFoldAtStart(5);
      assertNotNull(f);
      assertFalse(f.collapsed);
   }

   // --- Visible traversal for screeny support ---

   @Test
   void nextVisibleWalkCountMatchesVisibleLines() {
      // 30-line file, fold at 5-15 (collapsed), fold at 20-25 (collapsed)
      model.addFold(5, 15);
      model.addFold(20, 25);
      model.closeAll();

      // Walk forward from line 1, counting visible steps
      int line = 1;
      int steps = 0;
      while (line < 30) {
         line = model.nextVisible(line);
         steps++;
      }
      // Lines: 1,2,3,4, 5(fold), 16,17,18,19, 20(fold), 26,27,28,29
      assertEquals(14, steps);
   }

   @Test
   void prevVisibleWalkCountMatchesVisibleLines() {
      model.addFold(5, 15);
      model.addFold(20, 25);
      model.closeAll();

      // Walk backward from line 29, counting visible steps
      int line = 29;
      int steps = 0;
      while (line > 1) {
         line = model.prevVisible(line);
         if (line < 1)
            break;
         steps++;
      }
      // 29,28,27,26, 20(fold), 19,18,17,16, 5(fold), 4,3,2,1
      assertEquals(13, steps);
   }

   // --- adjustForEdit: line-shift tracking ---

   @Test
   void adjustForEditInsertShiftsFoldsAfter() {
      model.addFold(10, 20);
      model.closeFold(10);
      // Insert 5 lines at 0-based index 5 (before fold)
      model.adjustForEdit(5, 5);
      FoldModel.FoldRange f = model.getFolds().get(0);
      assertEquals(15, f.startLine);
      assertEquals(25, f.endLine);
      assertTrue(f.collapsed);
   }

   @Test
   void adjustForEditInsertBeforeFoldNoChange() {
      model.addFold(10, 20);
      // Insert 3 lines at 0-based index 25 (after fold)
      model.adjustForEdit(25, 3);
      FoldModel.FoldRange f = model.getFolds().get(0);
      assertEquals(10, f.startLine);
      assertEquals(20, f.endLine);
   }

   @Test
   void adjustForEditInsertInsideFoldExpands() {
      model.addFold(5, 15);
      model.closeFold(5);
      // Insert 3 lines at 0-based index 10 (inside fold)
      model.adjustForEdit(10, 3);
      FoldModel.FoldRange f = model.getFolds().get(0);
      assertEquals(5, f.startLine);
      assertEquals(18, f.endLine);
      assertTrue(f.collapsed);
   }

   @Test
   void adjustForEditDeleteShiftsFoldsAfter() {
      model.addFold(10, 20);
      model.closeFold(10);
      // Delete 3 lines at 0-based index 2 (before fold)
      model.adjustForEdit(2, -3);
      FoldModel.FoldRange f = model.getFolds().get(0);
      assertEquals(7, f.startLine);
      assertEquals(17, f.endLine);
      assertTrue(f.collapsed);
   }

   @Test
   void adjustForEditDeleteRemovesFoldEntirely() {
      model.addFold(5, 10);
      // Delete 10 lines at 0-based index 3 (covers fold)
      model.adjustForEdit(3, -10);
      assertTrue(model.isEmpty());
   }

   @Test
   void adjustForEditDeleteShrinksFold() {
      model.addFold(5, 20);
      model.closeFold(5);
      // Delete lines 8-12 (0-based index 7, count -5)
      model.adjustForEdit(7, -5);
      FoldModel.FoldRange f = model.getFolds().get(0);
      assertEquals(5, f.startLine);
      assertEquals(15, f.endLine);
      assertTrue(f.collapsed);
   }

   @Test
   void adjustForEditDeleteAfterFoldNoChange() {
      model.addFold(5, 10);
      // Delete 3 lines starting at 0-based index 15
      model.adjustForEdit(15, -3);
      FoldModel.FoldRange f = model.getFolds().get(0);
      assertEquals(5, f.startLine);
      assertEquals(10, f.endLine);
   }

   @Test
   void adjustForEditMultipleFolds() {
      model.addFold(3, 8);
      model.addFold(15, 25);
      model.closeAll();
      // Insert 4 lines at 0-based index 10 (between folds)
      model.adjustForEdit(10, 4);
      FoldModel.FoldRange f1 = model.getFolds().get(0);
      FoldModel.FoldRange f2 = model.getFolds().get(1);
      assertEquals(3, f1.startLine);
      assertEquals(8, f1.endLine);
      assertEquals(19, f2.startLine);
      assertEquals(29, f2.endLine);
   }

   @Test
   void adjustForEditZeroDeltaNoChange() {
      model.addFold(5, 15);
      model.adjustForEdit(10, 0);
      FoldModel.FoldRange f = model.getFolds().get(0);
      assertEquals(5, f.startLine);
      assertEquals(15, f.endLine);
   }

   @Test
   void adjustForEditEmptyModelNoError() {
      model.adjustForEdit(5, 3);
      assertTrue(model.isEmpty());
   }

   // --- getFoldIndicator ---

   @Test
   void foldIndicatorCollapsedStartShowsPlus() {
      model.addFold(5, 15);
      model.closeFold(5);
      assertEquals('+', model.getFoldIndicator(5));
   }

   @Test
   void foldIndicatorOpenStartShowsMinus() {
      model.addFold(5, 15);
      assertEquals('-', model.getFoldIndicator(5));
   }

   @Test
   void foldIndicatorOpenBodyShowsBar() {
      model.addFold(5, 15);
      assertEquals('|', model.getFoldIndicator(6));
      assertEquals('|', model.getFoldIndicator(10));
      assertEquals('|', model.getFoldIndicator(15));
   }

   @Test
   void foldIndicatorOutsideFoldReturnsNul() {
      model.addFold(5, 15);
      assertEquals('\0', model.getFoldIndicator(4));
      assertEquals('\0', model.getFoldIndicator(16));
   }

   @Test
   void foldIndicatorCollapsedBodyHidden() {
      // When collapsed, interior lines are hidden so only
      // the start line indicator matters. Interior lines
      // should not be queried in practice, but if they are,
      // they return '\0' because they are not inside an
      // open fold.
      model.addFold(5, 15);
      model.closeFold(5);
      assertEquals('+', model.getFoldIndicator(5));
      // Interior of collapsed fold returns '\0'
      assertEquals('\0', model.getFoldIndicator(10));
   }

   @Test
   void foldIndicatorMultipleFolds() {
      model.addFold(3, 8);
      model.addFold(12, 20);
      model.closeFold(12);
      // First fold is open
      assertEquals('-', model.getFoldIndicator(3));
      assertEquals('|', model.getFoldIndicator(5));
      assertEquals('|', model.getFoldIndicator(8));
      // Between folds
      assertEquals('\0', model.getFoldIndicator(9));
      // Second fold is collapsed
      assertEquals('+', model.getFoldIndicator(12));
      // After all folds
      assertEquals('\0', model.getFoldIndicator(21));
   }

   @Test
   void foldIndicatorNoFolds() {
      assertEquals('\0', model.getFoldIndicator(1));
      assertEquals('\0', model.getFoldIndicator(10));
   }

   // --- Indent-based fold detection ---

   @Test
   void detectIndentSimpleBlock() {
      // 3-space indent, one indented block
      String[] lines = {
         "",
         "def foo():",
         "   x = 1",
         "   y = 2",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectIndentFolds(f, lines.length, 3);
      assertEquals(1, fm.size());
      FoldModel.FoldRange fr = fm.getFolds().get(0);
      assertEquals(1, fr.startLine);
      assertEquals(3, fr.endLine);
   }

   @Test
   void detectIndentTwoBlocks() {
      String[] lines = {
         "",
         "def foo():",
         "   x = 1",
         "def bar():",
         "   y = 2",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectIndentFolds(f, lines.length, 3);
      assertEquals(2, fm.size());
      assertEquals(1, fm.getFolds().get(0).startLine);
      assertEquals(2, fm.getFolds().get(0).endLine);
      assertEquals(3, fm.getFolds().get(1).startLine);
      assertEquals(4, fm.getFolds().get(1).endLine);
   }

   @Test
   void detectIndentNestedFolds() {
      String[] lines = {
         "",
         "class Foo:",
         "   def method():",
         "      x = 1",
         "      y = 2",
         "   def other():",
         "      z = 3",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectIndentFolds(f, lines.length, 3);
      // Expect 3 folds: method body (2,4), other body (5,6),
      // and class body (1,6)
      assertEquals(3, fm.size());
      // Folds are sorted by startLine
      assertEquals(1, fm.getFolds().get(0).startLine);
      assertEquals(6, fm.getFolds().get(0).endLine);
      assertEquals(2, fm.getFolds().get(1).startLine);
      assertEquals(4, fm.getFolds().get(1).endLine);
      assertEquals(5, fm.getFolds().get(2).startLine);
      assertEquals(6, fm.getFolds().get(2).endLine);
   }

   @Test
   void detectIndentBlankLineInMiddle() {
      // Blank line should not break a fold
      String[] lines = {
         "",
         "def foo():",
         "   x = 1",
         "",
         "   y = 2",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectIndentFolds(f, lines.length, 3);
      assertEquals(1, fm.size());
      FoldModel.FoldRange fr = fm.getFolds().get(0);
      assertEquals(1, fr.startLine);
      assertEquals(4, fr.endLine);
   }

   @Test
   void detectIndentBlankLineAtEnd() {
      // Trailing blank line between blocks
      String[] lines = {
         "",
         "def foo():",
         "   x = 1",
         "",
         "def bar():",
         "   y = 2",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectIndentFolds(f, lines.length, 3);
      // Two separate folds (blank line is boundary)
      assertEquals(2, fm.size());
      assertEquals(1, fm.getFolds().get(0).startLine);
      assertEquals(2, fm.getFolds().get(0).endLine);
      assertEquals(4, fm.getFolds().get(1).startLine);
      assertEquals(5, fm.getFolds().get(1).endLine);
   }

   @Test
   void detectIndentTabsOnly() {
      // Tab-indented file with tabSize=4
      String[] lines = {
         "",
         "if (true) {",
         "\tx = 1;",
         "\ty = 2;",
         "}",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectIndentFolds(f, lines.length, 4);
      assertEquals(1, fm.size());
      assertEquals(1, fm.getFolds().get(0).startLine);
      assertEquals(3, fm.getFolds().get(0).endLine);
   }

   @Test
   void detectIndentMixedTabsSpaces() {
      // Tab = 4 spaces; "\t" = level 1, "\t\t" = level 2
      String[] lines = {
         "",
         "outer:",
         "\tinner:",
         "\t\tdeep",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectIndentFolds(f, lines.length, 4);
      // outer(1,3), inner(2,3)
      assertEquals(2, fm.size());
      assertEquals(1, fm.getFolds().get(0).startLine);
      assertEquals(3, fm.getFolds().get(0).endLine);
      assertEquals(2, fm.getFolds().get(1).startLine);
      assertEquals(3, fm.getFolds().get(1).endLine);
   }

   @Test
   void detectIndentNoIndentation() {
      String[] lines = {
         "",
         "line 1",
         "line 2",
         "line 3",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectIndentFolds(f, lines.length, 3);
      assertTrue(fm.isEmpty());
   }

   @Test
   void detectIndentEmptyBuffer() {
      FoldDetector.LineFetcher f = i -> "";
      FoldModel fm =
         FoldDetector.detectIndentFolds(f, 1, 3);
      assertTrue(fm.isEmpty());
   }

   @Test
   void detectIndentSingleLine() {
      String[] lines = {"", "only line"};
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectIndentFolds(f, lines.length, 3);
      assertTrue(fm.isEmpty());
   }

   @Test
   void detectIndentAllFoldsStartOpen() {
      String[] lines = {
         "",
         "a:",
         "   b",
         "   c",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectIndentFolds(f, lines.length, 3);
      for (FoldModel.FoldRange fr : fm.getFolds())
         assertFalse(fr.collapsed);
   }

   @Test
   void detectIndentMultipleBlanks() {
      // Multiple consecutive blank lines
      String[] lines = {
         "",
         "a:",
         "   x",
         "",
         "",
         "   y",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectIndentFolds(f, lines.length, 3);
      assertEquals(1, fm.size());
      assertEquals(1, fm.getFolds().get(0).startLine);
      assertEquals(5, fm.getFolds().get(0).endLine);
   }

   @Test
   void detectIndentThreeLevels() {
      String[] lines = {
         "",
         "L0",
         "   L1",
         "      L2",
         "         L3",
         "   back to L1",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectIndentFolds(f, lines.length, 3);
      // Folds: (1,5) for L0, (2,4) for L1, (3,4) for L2
      // Outer fold includes "back to L1" since level 1 > 0
      assertEquals(3, fm.size());
      assertEquals(1, fm.getFolds().get(0).startLine);
      assertEquals(5, fm.getFolds().get(0).endLine);
      assertEquals(2, fm.getFolds().get(1).startLine);
      assertEquals(4, fm.getFolds().get(1).endLine);
      assertEquals(3, fm.getFolds().get(2).startLine);
      assertEquals(4, fm.getFolds().get(2).endLine);
   }

   // --- indentLevel unit tests ---

   @Test
   void indentLevelSpaces() {
      assertEquals(0, FoldDetector.indentLevel("hello", 3));
      assertEquals(1, FoldDetector.indentLevel("   x", 3));
      assertEquals(2,
         FoldDetector.indentLevel("      x", 3));
   }

   @Test
   void indentLevelTabs() {
      assertEquals(1, FoldDetector.indentLevel("\tx", 4));
      assertEquals(2, FoldDetector.indentLevel("\t\tx", 4));
   }

   @Test
   void indentLevelMixed() {
      // tab=4, then 4 spaces = 2 levels
      assertEquals(2,
         FoldDetector.indentLevel("\t    x", 4));
   }
}
