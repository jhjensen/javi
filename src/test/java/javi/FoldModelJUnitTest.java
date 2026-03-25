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
}
