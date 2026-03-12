package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static javi.ChangeOpt.Opcode.*;

/**
 * Tests for {@link View.COpt} state machine transitions.
 */
class ViewCOptJUnitTest {

   private static TestView testView;
   private View.COpt copt;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void setUp() throws Exception {
      EventQueue.biglock2.lock();
      testView = new TestView(true);
      copt = (View.COpt) testView.getChangeOpt();
   }

   @AfterEach
   void tearDown() throws Exception {
      EventQueue.biglock2.unlock();
   }

   // ── resetOp defaults ────────────────────────────────────────

   @Test
   void initialStateIsNoop() throws Exception {
      assertEquals(NOOP, copt.resetOp());
   }

   @Test
   void resetOpReturnsToNoop() throws Exception {
      copt.redraw();
      assertEquals(REDRAW, copt.resetOp());
      assertEquals(NOOP, copt.resetOp());
   }

   // ── redraw tests ────────────────────────────────────────────

   @Test
   void redrawSetsRedrawOp() throws Exception {
      copt.redraw();
      assertEquals(REDRAW, copt.resetOp());
   }

   @Test
   void multipleRedrawsStillRedraw() throws Exception {
      copt.redraw();
      copt.redraw();
      assertEquals(REDRAW, copt.resetOp());
   }

   // ── blink tests ─────────────────────────────────────────────

   @Test
   void blinkFromNoopSetsBlinkcursor() throws Exception {
      copt.blink();
      assertEquals(BLINKCURSOR, copt.resetOp());
   }

   @Test
   void blinkSuppressedWhenNotNoop() throws Exception {
      copt.redraw();
      copt.blink(); // should be suppressed
      assertEquals(REDRAW, copt.resetOp());
   }

   // ── insert tests ────────────────────────────────────────────

   @Test
   void insertFromNoopSetsInsert() throws Exception {
      // Need to wire up text for pmark.resetMark
      TextEdit<String> te = openTestBuffer("insert-test", "line1\nline2\n");
      FvContext fvc = FvContext.connectFv(te, testView);
      try {
         boolean escalated = copt.insert(1, 1);
         assertFalse(escalated);
         assertEquals(INSERT, copt.resetOp());
      } finally {
         te.disposeFvc();
      }
   }

   @Test
   void insertFromBlinkSetsInsert() throws Exception {
      TextEdit<String> te = openTestBuffer("insert-blink", "text\n");
      FvContext fvc = FvContext.connectFv(te, testView);
      try {
         copt.blink();
         boolean escalated = copt.insert(0, 1);
         assertFalse(escalated);
         assertEquals(INSERT, copt.resetOp());
      } finally {
         te.disposeFvc();
      }
   }

   @Test
   void insertEscalatesToRedraw() throws Exception {
      TextEdit<String> te = openTestBuffer("insert-esc", "text\n");
      FvContext fvc = FvContext.connectFv(te, testView);
      try {
         copt.insert(0, 1);
         boolean escalated = copt.insert(0, 1);
         assertTrue(escalated);
         assertEquals(REDRAW, copt.resetOp());
      } finally {
         te.disposeFvc();
      }
   }

   @Test
   void insertSavesStartAndAmount() throws Exception {
      TextEdit<String> te = openTestBuffer("ins-save", "text\n");
      FvContext fvc = FvContext.connectFv(te, testView);
      try {
         copt.insert(5, 3);
         assertEquals(5, copt.getSaveStart());
         assertEquals(3, copt.getSaveAmount());
      } finally {
         te.disposeFvc();
      }
   }

   // ── delete tests ────────────────────────────────────────────

   @Test
   void deleteFromNoopSetsDelete() throws Exception {
      TextEdit<String> te = openTestBuffer("del-test", "line1\nline2\n");
      FvContext fvc = FvContext.connectFv(te, testView);
      try {
         boolean escalated = copt.delete(1, 1);
         assertFalse(escalated);
         assertEquals(DELETE, copt.resetOp());
      } finally {
         te.disposeFvc();
      }
   }

   @Test
   void deleteEscalatesToRedraw() throws Exception {
      TextEdit<String> te = openTestBuffer("del-esc", "text\n");
      FvContext fvc = FvContext.connectFv(te, testView);
      try {
         copt.delete(0, 1);
         boolean escalated = copt.delete(0, 1);
         assertTrue(escalated);
         assertEquals(REDRAW, copt.resetOp());
      } finally {
         te.disposeFvc();
      }
   }

   // ── changedpro / lineChanged tests ──────────────────────────

   @Test
   void lineChangedFromNoopSetsChange() throws Exception {
      TextEdit<String> te = openTestBuffer("chg-test", "line1\nline2\n");
      FvContext fvc = FvContext.connectFv(te, testView);
      try {
         boolean escalated = copt.lineChanged(1);
         assertFalse(escalated);
         assertEquals(CHANGE, copt.resetOp());
      } finally {
         te.disposeFvc();
      }
   }

   @Test
   void lineChangedMergesRange() throws Exception {
      TextEdit<String> te = openTestBuffer("chg-merge", "a\nb\nc\nd\n");
      FvContext fvc = FvContext.connectFv(te, testView);
      try {
         copt.lineChanged(2);
         copt.lineChanged(5); // should merge
         assertEquals(CHANGE, copt.resetOp());
      } finally {
         te.disposeFvc();
      }
   }

   @Test
   void lineChangedAfterInsertEscalates() throws Exception {
      TextEdit<String> te = openTestBuffer("chg-ins", "text\n");
      FvContext fvc = FvContext.connectFv(te, testView);
      try {
         copt.insert(0, 1);
         boolean escalated = copt.lineChanged(1);
         assertTrue(escalated);
         assertEquals(REDRAW, copt.resetOp());
      } finally {
         te.disposeFvc();
      }
   }

   // ── mscreen tests ───────────────────────────────────────────

   @Test
   void mscreenFromNoopSets() throws Exception {
      copt.mscreen(5, 100);
      assertEquals(MSCREEN, copt.resetOp());
   }

   @Test
   void mscreenAccumulatesAmount() throws Exception {
      copt.mscreen(5, 100);
      copt.mscreen(3, 100);
      assertEquals(8, copt.getSaveAmount());
      assertEquals(MSCREEN, copt.resetOp());
   }

   @Test
   void mscreenEscalatesWhenExceedsLimit() throws Exception {
      copt.mscreen(50, 100);
      copt.mscreen(150, 100); // exceeds limit
      assertEquals(REDRAW, copt.resetOp());
   }

   // ── helper ──────────────────────────────────────────────────

   @SuppressWarnings("unchecked")
   private static TextEdit<String> openTestBuffer(String name,
         String content) {
      try {
         FileDescriptor fd = FileDescriptor.InternalFd.make(name);
         FileProperties<String> fp = new FileProperties<>(fd, StringIoc.converter);
         TextEdit<String> te = new TextEdit<>(
               new IoConverter<>(fp, false), fp);
         // Insert content lines
         String[] lines = content.split("\n", -1);
         for (int i = 0; i < lines.length; i++) {
            te.insertOne(lines[i], i + 1);
         }
         return te;
      } catch (Exception e) {
         throw new RuntimeException("failed to create test buffer", e);
      }
   }
}
