package javi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

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

   @Test
   void detectJsonBracesInStringIgnored() {
      String[] lines = {
         "",
         "public class Foo {",
         "   String s = \"}\";",
         "   void bar() {",
         "      println(\"{\");",
         "   }",
         "}",
      };
      FoldDetector.LineFetcher fetcher = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectJsonFolds(fetcher, lines.length);
      assertEquals(2, fm.size());
      FoldModel.FoldRange outer = fm.getFolds().get(0);
      assertEquals(1, outer.startLine);
      assertEquals(6, outer.endLine);
      FoldModel.FoldRange inner = fm.getFolds().get(1);
      assertEquals(3, inner.startLine);
      assertEquals(5, inner.endLine);
   }

   @Test
   void detectJsonBracesInLineCommentIgnored() {
      String[] lines = {
         "",
         "void foo() {",
         "   // } this brace should be ignored",
         "   doSomething();",
         "}",
      };
      FoldDetector.LineFetcher fetcher = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectJsonFolds(fetcher, lines.length);
      assertEquals(1, fm.size());
      FoldModel.FoldRange f = fm.getFolds().get(0);
      assertEquals(1, f.startLine);
      assertEquals(4, f.endLine);
   }

   @Test
   void detectJsonEscapedQuoteInString() {
      String[] lines = {
         "",
         "void foo() {",
         "   String s = \"\\\"}\\\"\";",
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

   // --- Marker-based fold detection ---

   @Test
   void detectMarkerSimplePair() {
      String[] lines = {
         "",
         "// {{{",
         "int x = 1;",
         "int y = 2;",
         "// }}}",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectMarkerFolds(f, lines.length);
      assertEquals(1, fm.size());
      FoldModel.FoldRange fr = fm.getFolds().get(0);
      assertEquals(1, fr.startLine);
      assertEquals(4, fr.endLine);
   }

   @Test
   void detectMarkerNestedPairs() {
      String[] lines = {
         "",
         "// {{{",
         "// {{{",
         "inner",
         "// }}}",
         "outer tail",
         "// }}}",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectMarkerFolds(f, lines.length);
      assertEquals(2, fm.size());
      // Outer fold (sorted by startLine)
      assertEquals(1, fm.getFolds().get(0).startLine);
      assertEquals(6, fm.getFolds().get(0).endLine);
      // Inner fold
      assertEquals(2, fm.getFolds().get(1).startLine);
      assertEquals(4, fm.getFolds().get(1).endLine);
   }

   @Test
   void detectMarkerLeveledPairs() {
      String[] lines = {
         "",
         "// {{{1",
         "section 1",
         "// {{{2",
         "section 2",
         "// }}}2",
         "// }}}1",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectMarkerFolds(f, lines.length);
      assertEquals(2, fm.size());
      // Level 1 fold (outer, sorted first)
      assertEquals(1, fm.getFolds().get(0).startLine);
      assertEquals(6, fm.getFolds().get(0).endLine);
      // Level 2 fold (inner)
      assertEquals(3, fm.getFolds().get(1).startLine);
      assertEquals(5, fm.getFolds().get(1).endLine);
   }

   @Test
   void detectMarkerUnmatchedExtendsToEof() {
      String[] lines = {
         "",
         "// {{{",
         "no closing marker",
         "more lines",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectMarkerFolds(f, lines.length);
      assertEquals(1, fm.size());
      FoldModel.FoldRange fr = fm.getFolds().get(0);
      assertEquals(1, fr.startLine);
      assertEquals(3, fr.endLine);
   }

   @Test
   void detectMarkerNoMarkers() {
      String[] lines = {
         "",
         "plain line 1",
         "plain line 2",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectMarkerFolds(f, lines.length);
      assertTrue(fm.isEmpty());
   }

   @Test
   void detectMarkerEmptyBuffer() {
      FoldDetector.LineFetcher f = i -> "";
      FoldModel fm =
         FoldDetector.detectMarkerFolds(f, 1);
      assertTrue(fm.isEmpty());
   }

   @Test
   void detectMarkerAllFoldsOpen() {
      String[] lines = {
         "",
         "// {{{",
         "content",
         "// }}}",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectMarkerFolds(f, lines.length);
      for (FoldModel.FoldRange fr : fm.getFolds())
         assertFalse(fr.collapsed);
   }

   @Test
   void detectMarkerMultipleSections() {
      String[] lines = {
         "",
         "// {{{",
         "section A",
         "// }}}",
         "gap",
         "// {{{",
         "section B",
         "// }}}",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectMarkerFolds(f, lines.length);
      assertEquals(2, fm.size());
      assertEquals(1, fm.getFolds().get(0).startLine);
      assertEquals(3, fm.getFolds().get(0).endLine);
      assertEquals(5, fm.getFolds().get(1).startLine);
      assertEquals(7, fm.getFolds().get(1).endLine);
   }

   @Test
   void detectMarkerInlineComment() {
      // Markers embedded in code comments
      String[] lines = {
         "",
         "int x = 0; // {{{",
         "int y = 1;",
         "int z = 2; // }}}",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectMarkerFolds(f, lines.length);
      assertEquals(1, fm.size());
      assertEquals(1, fm.getFolds().get(0).startLine);
      assertEquals(3, fm.getFolds().get(0).endLine);
   }

   @Test
   void detectMarkerSameLineStartEndNoFold() {
      // {{{ and }}} on same line: start is processed after
      // end, so }}} has nothing to close, then {{{ opens.
      // The {{{ is left unmatched -> extends to EOF.
      String[] lines = {
         "",
         "// }}} {{{",
         "tail",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectMarkerFolds(f, lines.length);
      assertEquals(1, fm.size());
      assertEquals(1, fm.getFolds().get(0).startLine);
      assertEquals(2, fm.getFolds().get(0).endLine);
   }

   @Test
   void parseMarkerLevelDigit() {
      assertEquals(1,
         FoldDetector.parseMarkerLevel("{{{1", 3));
      assertEquals(0,
         FoldDetector.parseMarkerLevel("{{{0", 3));
      assertEquals(9,
         FoldDetector.parseMarkerLevel("{{{9", 3));
   }

   @Test
   void parseMarkerLevelNoDigit() {
      assertEquals(-1,
         FoldDetector.parseMarkerLevel("{{{ ", 3));
      assertEquals(-1,
         FoldDetector.parseMarkerLevel("{{{", 3));
   }

   @Test
   void detectMarkerExtraEndIgnored() {
      // Extra }}} with no matching {{{ is ignored
      String[] lines = {
         "",
         "// {{{",
         "content",
         "// }}}",
         "// }}}",
      };
      FoldDetector.LineFetcher f = i -> lines[i];
      FoldModel fm =
         FoldDetector.detectMarkerFolds(f, lines.length);
      assertEquals(1, fm.size());
      assertEquals(1, fm.getFolds().get(0).startLine);
      assertEquals(3, fm.getFolds().get(0).endLine);
   }

   // --- Fold persistence tests ---

   @Test
   void saveAndLoadFoldsRoundTrip(@TempDir Path tmpDir) {
      String fakePath = tmpDir.resolve("test.txt")
         .toString();
      model.addFold(5, 20);
      model.addFold(30, 50);
      model.closeFold(5);
      model.saveFolds(fakePath);

      File stateFile = FoldModel.foldStateFile(fakePath);
      assertTrue(stateFile.exists(),
         "fold state file should exist");

      FoldModel loaded = FoldModel.loadFolds(fakePath);
      assertNotNull(loaded);
      assertEquals(2, loaded.size());

      FoldModel.FoldRange f1 = loaded.findFoldAtStart(5);
      assertNotNull(f1);
      assertEquals(20, f1.endLine);
      assertTrue(f1.collapsed);

      FoldModel.FoldRange f2 = loaded.findFoldAtStart(30);
      assertNotNull(f2);
      assertEquals(50, f2.endLine);
      assertFalse(f2.collapsed);
   }

   @Test
   void loadFoldsReturnsNullWhenNoFile(@TempDir Path tmp) {
      String noFile = tmp.resolve("nonexistent.txt")
         .toString();
      assertNull(FoldModel.loadFolds(noFile));
   }

   @Test
   void loadFoldsReturnsNullForNullPath() {
      assertNull(FoldModel.loadFolds(null));
   }

   @Test
   void saveFoldsNullPathDoesNotThrow() {
      model.addFold(1, 10);
      model.saveFolds(null); // should be a no-op
   }

   @Test
   void saveFoldsEmptyModelDoesNotWrite(
         @TempDir Path tmpDir) {
      String fakePath = tmpDir.resolve("empty.txt")
         .toString();
      model.saveFolds(fakePath);
      File stateFile = FoldModel.foldStateFile(fakePath);
      assertFalse(stateFile.exists(),
         "empty model should not create file");
   }

   @Test
   void deleteFoldStateRemovesFile(@TempDir Path tmpDir) {
      String fakePath = tmpDir.resolve("del.txt")
         .toString();
      model.addFold(1, 10);
      model.saveFolds(fakePath);
      File stateFile = FoldModel.foldStateFile(fakePath);
      assertTrue(stateFile.exists());

      FoldModel.deleteFoldState(fakePath);
      assertFalse(stateFile.exists());
   }

   @Test
   void deleteFoldStateNullPathDoesNotThrow() {
      FoldModel.deleteFoldState(null); // no-op
   }

   @Test
   void loadFoldsSkipsMalformedLines(
         @TempDir Path tmpDir)
         throws IOException {
      String fakePath = tmpDir.resolve("bad.txt")
         .toString();
      File stateFile = FoldModel.foldStateFile(fakePath);
      try (FileWriter fw = new FileWriter(stateFile)) {
         fw.write("5:20:true\n");
         fw.write("badline\n");
         fw.write("not:a:number\n");
         fw.write("30:50:false\n");
         fw.write("\n");
      }
      FoldModel loaded = FoldModel.loadFolds(fakePath);
      assertNotNull(loaded);
      assertEquals(2, loaded.size());
      assertTrue(loaded.findFoldAtStart(5).collapsed);
      assertFalse(loaded.findFoldAtStart(30).collapsed);
   }

   @Test
   void foldStateFileDerived() {
      File f = FoldModel.foldStateFile("/tmp/test.java");
      assertEquals("/tmp/test.java.foldstate",
         f.getPath());
   }

   @Test
   void loadFoldsRejectsInvalidRanges(
         @TempDir Path tmpDir)
         throws IOException {
      String fakePath = tmpDir.resolve("inv.txt")
         .toString();
      File stateFile = FoldModel.foldStateFile(fakePath);
      try (FileWriter fw = new FileWriter(stateFile)) {
         fw.write("10:5:true\n");   // inverted
         fw.write("3:3:false\n");   // same line
         fw.write("1:20:true\n");   // valid
      }
      FoldModel loaded = FoldModel.loadFolds(fakePath);
      assertNotNull(loaded);
      assertEquals(1, loaded.size());
      assertEquals(1, loaded.findFoldAtStart(1).startLine);
   }

   // --- Innermost fold selection ---

   @Test
   void findFoldReturnsInnermostNested() {
      model.addFold(1, 20);
      model.addFold(5, 10);
      FoldModel.FoldRange f = model.findFold(7);
      assertNotNull(f);
      assertEquals(5, f.startLine);
      assertEquals(10, f.endLine);
   }

   @Test
   void findFoldReturnsOuterWhenNotInInner() {
      model.addFold(1, 20);
      model.addFold(5, 10);
      FoldModel.FoldRange f = model.findFold(15);
      assertNotNull(f);
      assertEquals(1, f.startLine);
      assertEquals(20, f.endLine);
   }

   @Test
   void closeFoldClosesInnermost() {
      model.addFold(1, 20);
      model.addFold(5, 10);
      FoldModel.FoldRange fc = model.closeFold(7);
      assertNotNull(fc);
      assertEquals(5, fc.startLine);
      assertTrue(fc.collapsed);
      // Outer fold should remain open
      FoldModel.FoldRange outer = model.findFoldAtStart(1);
      assertFalse(outer.collapsed);
   }

   @Test
   void openFoldOpensInnermost() {
      model.addFold(1, 20);
      model.addFold(5, 10);
      model.closeFold(7);
      FoldModel.FoldRange fo = model.openFold(5);
      assertNotNull(fo);
      assertFalse(fo.collapsed);
   }

   @Test
   void closeFoldAtStartLineClosesInnermost() {
      model.addFold(1, 20);
      model.addFold(5, 10);
      FoldModel.FoldRange fc = model.closeFold(5);
      assertNotNull(fc);
      assertEquals(5, fc.startLine);
      assertTrue(fc.collapsed);
      assertFalse(model.findFoldAtStart(1).collapsed);
   }

   @Test
   void findFoldTripleNesting() {
      model.addFold(1, 30);
      model.addFold(5, 20);
      model.addFold(8, 12);
      FoldModel.FoldRange f = model.findFold(10);
      assertNotNull(f);
      assertEquals(8, f.startLine);
      assertEquals(12, f.endLine);
   }

   @Test
   void nestedFoldIndicatorsShowDashAtAllBraceLines() {
      // Simulates user's test case:
      // line 1: {  1
      // line 2: {  2
      // line 3: {  3
      // line 4: }
      // line 5: }
      // line 6: }
      model.addFold(3, 4);  // inner
      model.addFold(2, 5);  // middle
      model.addFold(1, 6);  // outer
      // All fold start lines should show '-' (open fold)
      assertEquals('-', model.getFoldIndicator(1));
      assertEquals('-', model.getFoldIndicator(2));
      assertEquals('-', model.getFoldIndicator(3));
      // Closing brace lines should show '|' (inside fold)
      assertEquals('|', model.getFoldIndicator(4));
      assertEquals('|', model.getFoldIndicator(5));
      assertEquals('|', model.getFoldIndicator(6));
   }

   @Test
   void nestedFoldIndicatorsCollapsedInner() {
      model.addFold(3, 4);
      model.addFold(2, 5);
      model.addFold(1, 6);
      model.closeFold(3);  // collapse innermost
      assertEquals('-', model.getFoldIndicator(1));
      assertEquals('-', model.getFoldIndicator(2));
      assertEquals('+', model.getFoldIndicator(3));
   }

   @Test
   void foldDetectorNestedBraces() {
      // Verify FoldDetector creates correct folds for
      // nested braces.
      String[] lines = {
         null,        // index 0 unused (1-based)
         "{  1",      // line 1
         "{  2",      // line 2
         "{  3",      // line 3
         "}",         // line 4
         "}",         // line 5
         "}",         // line 6
      };
      FoldDetector.LineFetcher fetcher =
         lineNum -> lines[lineNum];
      FoldModel fm =
         FoldDetector.detectJsonFolds(fetcher, 7);
      assertEquals(3, fm.size());
      assertEquals('-', fm.getFoldIndicator(1));
      assertEquals('-', fm.getFoldIndicator(2));
      assertEquals('-', fm.getFoldIndicator(3));
      assertEquals('|', fm.getFoldIndicator(4));
      assertEquals('|', fm.getFoldIndicator(5));
      assertEquals('|', fm.getFoldIndicator(6));
   }

   // --- Performance: nextVisible/prevVisible O(n) per call ---

   @Test
   void nextVisibleLargeFoldCountIsEfficient() {
      // Build 500 adjacent collapsed folds (2 lines each)
      for (int i = 0; i < 500; i++) {
         int s = 2 + i * 3;
         model.addFold(s, s + 1);
      }
      model.closeAll();
      // Walk the entire file; should complete quickly
      int line = 1;
      int steps = 0;
      while (line < 1600 && steps < 2000) {
         line = model.nextVisible(line);
         steps++;
      }
      assertTrue(steps < 1100,
         "expected under 1100 steps, got " + steps);
   }

   @Test
   void prevVisibleLargeFoldCountIsEfficient() {
      for (int i = 0; i < 500; i++) {
         int s = 2 + i * 3;
         model.addFold(s, s + 1);
      }
      model.closeAll();
      int line = 1502;
      int steps = 0;
      while (line > 1 && steps < 2000) {
         line = model.prevVisible(line);
         if (line < 1)
            break;
         steps++;
      }
      assertTrue(steps < 1100,
         "expected under 1100 steps, got " + steps);
   }

   @Test
   void mapBufferToScreenLargeFoldCount() {
      for (int i = 0; i < 200; i++) {
         int s = 2 + i * 5;
         model.addFold(s, s + 2);
      }
      model.closeAll();
      // Line 1001 is past all folds
      int vis = model.mapBufferToScreen(1001);
      // 200 folds × 2 hidden lines each = 400 hidden
      assertEquals(601, vis);
   }

   @Test
   void mapBufferToScreenNestedFoldsAvoidDoubleCount() {
      // Outer 1-100 collapsed, inner 10-20 collapsed
      model.addFold(1, 100);
      model.addFold(10, 20);
      model.closeAll();
      // Line 110: outer hides 99 lines (2-100)
      // Inner is nested inside outer, should not double count
      int vis = model.mapBufferToScreen(110);
      assertEquals(110 - 99, vis);
   }

   @Test
   void nextVisibleNestedCollapsedAdjacentFolds() {
      // Outer open fold containing two collapsed inner folds
      model.addFold(1, 30);  // open
      model.addFold(5, 10);  // collapsed
      model.addFold(11, 20); // collapsed
      model.closeFold(5);
      model.closeFold(11);
      // From 4 → 5 (fold start)
      assertEquals(5, model.nextVisible(4));
      // From 5 (fold start) → 11 (next fold start)
      assertEquals(11, model.nextVisible(5));
      // From 11 (fold start) → 21
      assertEquals(21, model.nextVisible(11));
   }

   // --- Bug fix: off-by-one in adjustForEdit for adjacent deletions ---

   @Test
   void adjustForEditDeleteLine1ShiftsFoldDown() {
      // Bug: deleting line 1 before a fold at line 2 didn't
      // move the fold mark up — fold stayed at 2 instead of 1
      model.addFold(2, 10);
      model.closeFold(2);
      // Delete 1 line at position 1 (line 1)
      model.adjustForEdit(1, -1);
      FoldModel.FoldRange f = model.getFolds().get(0);
      assertEquals(1, f.startLine,
         "fold should shift from line 2 to line 1");
      assertEquals(9, f.endLine,
         "fold end should shift from line 10 to line 9");
      assertTrue(f.collapsed);
   }

   @Test
   void adjustForEditDeleteAdjacentLineAboveFold() {
      // Delete the line immediately before a fold
      model.addFold(5, 15);
      model.closeFold(5);
      // Delete 1 line at position 4 (the line just above the fold)
      model.adjustForEdit(4, -1);
      FoldModel.FoldRange f = model.getFolds().get(0);
      assertEquals(4, f.startLine,
         "fold should shift from line 5 to line 4");
      assertEquals(14, f.endLine,
         "fold end should shift from line 15 to line 14");
      assertTrue(f.collapsed);
   }

   @Test
   void adjustForEditDeleteMultipleLinesBeforeAdjacentFold() {
      // Delete 3 lines (1-3) before a fold starting at line 4
      model.addFold(4, 12);
      model.adjustForEdit(1, -3);
      FoldModel.FoldRange f = model.getFolds().get(0);
      assertEquals(1, f.startLine,
         "fold should shift from line 4 to line 1");
      assertEquals(9, f.endLine,
         "fold end should shift from line 12 to line 9");
   }

   // --- Tests from user fold bug description (todo.md) ---
   // File content:
   //   # 1    (line 1)
   //   ## a   (line 2)
   //   x      (line 3)
   //   # 2    (line 4)
   //   ## b   (line 5)
   //   y      (line 6)
   //   # 3    (line 7)
   //   ## c   (line 8)
   //   z      (line 9)

   /**
    * Verify markdown fold detection produces correctly-sized
    * top-level folds for a file with three H1 sections each
    * containing an H2 and body line. Each H1 fold should span
    * only its section (3 lines), not the entire file.
    * Bug: zM showed first fold as 8 lines instead of 2.
    */
   @Test
   void markdownThreeH1SectionsFoldSizes() {
      FoldDetector.LineFetcher buf = lineNum -> {
         switch (lineNum) {
            case 1: return "# 1";
            case 2: return "## a";
            case 3: return "x";
            case 4: return "# 2";
            case 5: return "## b";
            case 6: return "y";
            case 7: return "# 3";
            case 8: return "## c";
            case 9: return "z";
            default: return null;
         }
      };
      FoldModel fm =
         FoldDetector.detectMarkdownFolds(buf, 10);
      // 6 folds: #1(1-3), ##a(2-3), #2(4-6), ##b(5-6),
      //          #3(7-9), ##c(8-9)
      assertEquals(6, fm.size(),
         "expected 6 folds (3 H1 + 3 H2), got: "
         + fm.getFolds());
      // Verify top-level H1 folds span exactly 3 lines each
      FoldModel.FoldRange h1first = fm.findFoldAtStart(1);
      assertNotNull(h1first, "H1 fold at line 1");
      assertEquals(3, h1first.endLine,
         "first H1 fold should end at line 3, not "
         + h1first.endLine);
      // Close fold to verify hiddenLines count
      h1first.collapsed = true;
      assertEquals(2, h1first.hiddenLines(),
         "first H1 fold should hide 2 lines when collapsed");

      FoldModel.FoldRange h1second = fm.findFoldAtStart(4);
      assertNotNull(h1second, "H1 fold at line 4");
      assertEquals(6, h1second.endLine,
         "second H1 fold should end at line 6");

      FoldModel.FoldRange h1third = fm.findFoldAtStart(7);
      assertNotNull(h1third, "H1 fold at line 7");
      assertEquals(9, h1third.endLine,
         "third H1 fold should end at line 9");
   }

   /**
    * After closeAll on the markdown test file, only the three
    * H1 start lines should be visible. Verify foldSummaryText
    * reports correct line count for each fold.
    */
   @Test
   void markdownCloseAllVisibleLinesAndSummary() {
      FoldDetector.LineFetcher buf = lineNum -> {
         switch (lineNum) {
            case 1: return "# 1";
            case 2: return "## a";
            case 3: return "x";
            case 4: return "# 2";
            case 5: return "## b";
            case 6: return "y";
            case 7: return "# 3";
            case 8: return "## c";
            case 9: return "z";
            default: return null;
         }
      };
      FoldModel fm =
         FoldDetector.detectMarkdownFolds(buf, 10);
      fm.closeAll();

      // Only 3 visible lines: fold starts at 1, 4, 7
      assertEquals(3, fm.getVisibleLineCount(9),
         "only H1 start lines should be visible");

      // Summary text for first fold: 2 hidden lines, not 8
      FoldModel.FoldRange h1 = fm.findFoldAtStart(1);
      String summary = FoldModel.foldSummaryText(
         h1.startLine, h1.endLine, "# 1");
      assertEquals("+--  2 lines: # 1", summary,
         "first fold summary should show 2 lines");
   }

   /**
    * After closeAll, mapScreenToBuffer should map screen
    * line 1 to buffer line 1 ("# 1"), screen 2 to buffer
    * line 4 ("# 2"), screen 3 to buffer line 7 ("# 3").
    * Bug: cursor on first line showed "# a" instead of "# 1"
    * (suggesting screen-to-buffer mapping returned line 2).
    */
   @Test
   void markdownCloseAllScreenToBufferMapping() {
      FoldDetector.LineFetcher buf = lineNum -> {
         switch (lineNum) {
            case 1: return "# 1";
            case 2: return "## a";
            case 3: return "x";
            case 4: return "# 2";
            case 5: return "## b";
            case 6: return "y";
            case 7: return "# 3";
            case 8: return "## c";
            case 9: return "z";
            default: return null;
         }
      };
      FoldModel fm =
         FoldDetector.detectMarkdownFolds(buf, 10);
      fm.closeAll();

      // Screen line 1 → buffer line 1 (# 1)
      assertEquals(1, fm.mapScreenToBuffer(1),
         "screen 1 should map to buffer line 1 (# 1)");
      // Screen line 2 → buffer line 4 (# 2)
      assertEquals(4, fm.mapScreenToBuffer(2),
         "screen 2 should map to buffer line 4 (# 2)");
      // Screen line 3 → buffer line 7 (# 3)
      assertEquals(7, fm.mapScreenToBuffer(3),
         "screen 3 should map to buffer line 7 (# 3)");
   }

   /**
    * After closeAll, buffer-to-screen mapping should return
    * correct screen positions for visible lines and -1 for
    * hidden lines.
    */
   @Test
   void markdownCloseAllBufferToScreenMapping() {
      FoldDetector.LineFetcher buf = lineNum -> {
         switch (lineNum) {
            case 1: return "# 1";
            case 2: return "## a";
            case 3: return "x";
            case 4: return "# 2";
            case 5: return "## b";
            case 6: return "y";
            case 7: return "# 3";
            case 8: return "## c";
            case 9: return "z";
            default: return null;
         }
      };
      FoldModel fm =
         FoldDetector.detectMarkdownFolds(buf, 10);
      fm.closeAll();

      // Visible lines: 1, 4, 7
      assertEquals(1, fm.mapBufferToScreen(1));
      assertEquals(-1, fm.mapBufferToScreen(2),
         "line 2 (## a) should be hidden");
      assertEquals(-1, fm.mapBufferToScreen(3),
         "line 3 (x) should be hidden");
      assertEquals(2, fm.mapBufferToScreen(4),
         "line 4 (# 2) should be screen line 2");
      assertEquals(-1, fm.mapBufferToScreen(5));
      assertEquals(-1, fm.mapBufferToScreen(6));
      assertEquals(3, fm.mapBufferToScreen(7),
         "line 7 (# 3) should be screen line 3");
      assertEquals(-1, fm.mapBufferToScreen(8));
      assertEquals(-1, fm.mapBufferToScreen(9));
   }

   /**
    * Verify nextVisible correctly walks through three
    * collapsed H1 folds — each jump from a fold start
    * should land on the next fold start, not one past it.
    */
   @Test
   void nextVisibleThreeCollapsedMarkdownFolds() {
      // Replicate the exact fold structure from markdown
      // detection on the test file
      model.addFold(1, 3);
      model.addFold(2, 3);
      model.addFold(4, 6);
      model.addFold(5, 6);
      model.addFold(7, 9);
      model.addFold(8, 9);
      model.closeAll();

      // Walking forward: 1 → 4 → 7 → 10
      assertEquals(4, model.nextVisible(1),
         "from fold start 1, next visible should be 4");
      assertEquals(7, model.nextVisible(4),
         "from fold start 4, next visible should be 7");
      assertEquals(10, model.nextVisible(7),
         "from fold start 7, next visible should be 10");
   }

   /**
    * Verify prevVisible correctly walks backward through
    * three collapsed H1 folds.
    */
   @Test
   void prevVisibleThreeCollapsedMarkdownFolds() {
      model.addFold(1, 3);
      model.addFold(2, 3);
      model.addFold(4, 6);
      model.addFold(5, 6);
      model.addFold(7, 9);
      model.addFold(8, 9);
      model.closeAll();

      // Walking backward: 10 → 7 → 4 → 1
      assertEquals(7, model.prevVisible(10),
         "from line 10, prev visible should be fold 7");
      assertEquals(4, model.prevVisible(7),
         "from fold start 7, prev visible should be 4");
      assertEquals(1, model.prevVisible(4),
         "from fold start 4, prev visible should be 1");
   }

   // --- isValid validation tests ---

   @Test
   void isValidAcceptsCorrectFolds() {
      model.addFold(1, 3);
      model.addFold(4, 6);
      assertTrue(model.isValid(6),
         "folds within range should be valid");
      assertTrue(model.isValid(10),
         "folds within larger range should be valid");
   }

   @Test
   void isValidRejectsOutOfRange() {
      model.addFold(1, 3);
      model.addFold(4, 9);
      assertFalse(model.isValid(8),
         "fold end 9 exceeds maxLine 8");
   }

   @Test
   void isValidRejectsZeroStart() {
      model.addFold(0, 5);
      assertFalse(model.isValid(10),
         "fold start 0 is invalid (1-based lines)");
   }

   @Test
   void isValidEmptyModel() {
      assertTrue(model.isValid(0),
         "empty model is always valid");
   }

   // --- openAllEnclosing ---

   @Test
   void openAllEnclosingOpensOuterAndInner() {
      model.addFold(1, 100);
      model.addFold(10, 20);
      model.closeAll();
      assertTrue(model.isFolded(15));
      boolean opened = model.openAllEnclosing(15);
      assertTrue(opened);
      assertFalse(model.isFolded(15));
      // both folds should now be open
      assertFalse(model.findFold(15).collapsed);
      assertFalse(model.findFold(50).collapsed);
   }

   @Test
   void openAllEnclosingReturnsFalseWhenNoFolds() {
      assertFalse(model.openAllEnclosing(5));
   }

   @Test
   void openAllEnclosingIgnoresAlreadyOpen() {
      model.addFold(1, 10);
      // fold is open by default
      assertFalse(model.openAllEnclosing(5));
   }

   // --- Fold-aware insert/paste boundary tests ---

   @Test
   void insertAfterCollapsedFoldDoesNotExtendFold() {
      // Fold 5-15 collapsed. Insert 1 line at endLine+1 (16).
      model.addFold(5, 15);
      model.closeAll();
      // Simulate insertStrings at endLine+1: adjustForEdit(16, 1)
      model.adjustForEdit(16, 1);
      FoldModel.FoldRange f = model.findFoldAtStart(5);
      assertNotNull(f);
      assertEquals(5, f.startLine);
      assertEquals(15, f.endLine); // unchanged
      assertTrue(f.collapsed);
   }

   @Test
   void insertInsideCollapsedFoldExtendsFold() {
      // Fold 5-15 collapsed. Insert at startLine+1 (6) pushes end.
      model.addFold(5, 15);
      model.closeAll();
      model.adjustForEdit(6, 1);
      FoldModel.FoldRange f = model.findFoldAtStart(5);
      assertNotNull(f);
      assertEquals(5, f.startLine);
      assertEquals(16, f.endLine); // extended
   }

   @Test
   void insertBeforeCollapsedFoldShiftsFold() {
      // Fold 5-15 collapsed. Insert at line 3 shifts fold.
      model.addFold(5, 15);
      model.closeAll();
      model.adjustForEdit(3, 2); // insert 2 lines at pos 3
      FoldModel.FoldRange f = model.findFoldAtStart(7);
      assertNotNull(f);
      assertEquals(7, f.startLine);
      assertEquals(17, f.endLine);
      assertTrue(f.collapsed);
   }

   @Test
   void pasteMultipleLinesAfterCollapsedFold() {
      // Fold 5-15 collapsed, paste 3 lines after fold.
      // Insert at endLine+1 = 16, count = 3
      model.addFold(5, 15);
      model.closeAll();
      model.adjustForEdit(16, 3);
      FoldModel.FoldRange f = model.findFoldAtStart(5);
      assertNotNull(f);
      assertEquals(5, f.startLine);
      assertEquals(15, f.endLine); // unchanged
      assertTrue(f.collapsed);
   }

   @Test
   void pasteBeforeCollapsedFoldShiftsFold() {
      // Fold 5-15 collapsed, paste 3 lines before fold (at 5).
      model.addFold(5, 15);
      model.closeAll();
      model.adjustForEdit(5, 3);
      FoldModel.FoldRange f = model.findFoldAtStart(8);
      assertNotNull(f);
      assertEquals(8, f.startLine);
      assertEquals(18, f.endLine);
      assertTrue(f.collapsed);
   }

   @Test
   void nextVisibleAfterInsertPastFold() {
      // After inserting lines after a collapsed fold,
      // nextVisible from fold start should land on first
      // inserted line (endLine + 1).
      model.addFold(5, 15);
      model.closeAll();
      // Simulate insert of 3 lines after fold
      model.adjustForEdit(16, 3);
      // nextVisible(5) should skip fold to 16
      assertEquals(16, model.nextVisible(5));
   }
}
