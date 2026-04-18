package javi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for fold-aware dd/yy/p behavior: deleting a collapsed
 * fold removes lines and fold marks, pasting restores them.
 */
class FoldEditJUnitTest {

   private FoldModel model;

   @BeforeEach
   void setUp() {
      model = new FoldModel();
   }

   // --- removeFoldsInRange ---

   @Test
   void removeFoldsInRangeClearsNestedFolds() {
      model.addFold(5, 20);
      model.addFold(8, 12);
      model.addFold(14, 18);
      assertEquals(3, model.size());
      model.removeFoldsInRange(5, 20);
      assertEquals(0, model.size());
   }

   @Test
   void removeFoldsInRangeKeepsOuterFold() {
      model.addFold(1, 30);
      model.addFold(5, 15);
      model.removeFoldsInRange(5, 15);
      assertEquals(1, model.size());
      assertNotNull(model.findFoldAtStart(1));
   }

   @Test
   void removeFoldsInRangeKeepsFoldsOutsideRange() {
      model.addFold(1, 3);
      model.addFold(5, 15);
      model.addFold(20, 25);
      model.removeFoldsInRange(5, 15);
      assertEquals(2, model.size());
      assertNotNull(model.findFoldAtStart(1));
      assertNotNull(model.findFoldAtStart(20));
      assertNull(model.findFoldAtStart(5));
   }

   // --- dd on collapsed fold simulation ---

   @Test
   void ddOnCollapsedFoldRemovesFoldAndNested() {
      model.addFold(5, 15);
      model.addFold(7, 10);
      model.closeAll();
      FoldModel.FoldRange fr = model.findFoldAtStart(5);
      assertNotNull(fr);
      assertTrue(fr.collapsed);
      int span = fr.span();
      assertEquals(11, span);
      model.removeFoldsInRange(fr.startLine, fr.endLine);
      assertEquals(0, model.size());
   }

   @Test
   void ddOnCollapsedFoldExpansionCoversFullSpan() {
      model.addFold(10, 20);
      model.closeAll();
      FoldModel.FoldRange fr = model.findFoldAtStart(10);
      assertNotNull(fr);
      assertEquals(11, fr.span());
   }

   @Test
   void ddOnOpenFoldDoesNotExpand() {
      model.addFold(5, 15);
      // Open fold — findFoldAtStart returns it but
      // collapsed is false, so no expansion.
      FoldModel.FoldRange fr = model.findFoldAtStart(5);
      assertNotNull(fr);
      assertFalse(fr.collapsed);
   }

   // --- Buffers fold span metadata ---

   @Test
   void foldSpanMetadataRoundTrip() {
      Buffers.clearFoldSpan();
      assertEquals(0, Buffers.getLastFoldSpan());
      Buffers.setLastFoldSpan(11);
      assertEquals(11, Buffers.getLastFoldSpan());
      Buffers.clearFoldSpan();
      assertEquals(0, Buffers.getLastFoldSpan());
   }

   @Test
   void foldSpanClearedAfterNonFoldOperation() {
      Buffers.setLastFoldSpan(5);
      Buffers.clearFoldSpan();
      assertEquals(0, Buffers.getLastFoldSpan());
   }

   // --- Paste fold recreation simulation ---

   @Test
   void pasteRecreatesFoldFromMetadata() {
      model.addFold(1, 3);
      int insertAt = 4;
      int foldSpan = 11;
      int foldEnd = insertAt + foldSpan - 1;
      model.addFold(insertAt, foldEnd);
      FoldModel.FoldRange nf =
         model.findFoldAtStart(insertAt);
      assertNotNull(nf);
      nf.collapsed = true;
      assertTrue(nf.collapsed);
      assertEquals(insertAt, nf.startLine);
      assertEquals(foldEnd, nf.endLine);
      assertEquals(foldSpan, nf.span());
   }

   @Test
   void ddThenPasteRestoredFoldMaintainsModel() {
      // Simulate: buffer has folds at 1-3, 5-15, 20-25
      model.addFold(1, 3);
      model.addFold(5, 15);
      model.addFold(20, 25);
      model.closeAll();

      // dd on fold 5-15: record span, remove folds in range
      FoldModel.FoldRange fr = model.findFoldAtStart(5);
      assertNotNull(fr);
      int span = fr.span(); // 11
      assertEquals(11, span);
      model.removeFoldsInRange(5, 15);
      assertEquals(2, model.size());

      // Simulate buffer deletion (adjustForEdit -11 at index 5)
      model.adjustForEdit(5, -11);
      // Fold 1-3 stays. Fold 20-25 shifts to 9-14.
      assertNotNull(model.findFoldAtStart(1));
      FoldModel.FoldRange shifted = model.findFoldAtStart(9);
      assertNotNull(shifted);
      assertEquals(14, shifted.endLine);

      // Paste: insert 11 lines at position 10 (after fold 9-14)
      int pasteAt = 15;
      model.adjustForEdit(pasteAt, 11);
      model.addFold(pasteAt, pasteAt + span - 1);
      FoldModel.FoldRange pasted =
         model.findFoldAtStart(pasteAt);
      assertNotNull(pasted);
      pasted.collapsed = true;
      assertEquals(span, pasted.span());
      assertEquals(3, model.size());
   }
}
