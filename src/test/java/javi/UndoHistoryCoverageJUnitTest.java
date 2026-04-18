package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage tests for {@link UndoHistory} — addState,
 * forceWritten, checkpoint patterns, and edge cases.
 */
class UndoHistoryCoverageJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   private static String testPath(String name) {
      return history.Testutil.testFile(name).getPath();
   }

   private static TextEdit<String> openTestFile(String name) {
      FileDescriptor fd = FileDescriptor.make(testPath(name));
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      return te;
   }

   private static void deleteTestFiles(String... names)
         throws IOException {
      for (String name : names) {
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(name)).delete();
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(name + ".dmp2")).delete();
      }
   }

   // ── addState tests ───────────────────────────────────────────

   @Test
   void addStateOnNewFileShowsUnchanged() throws IOException {
      String fname = "ju_uhc_state1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      StringBuilder sb = new StringBuilder();
      ex.addState(sb);
      String state = sb.toString();
      assertTrue(state.contains("unchanged"),
         "new file addState should contain 'unchanged', got: " + state);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void addStateAfterInsertShowsModified() throws IOException {
      String fname = "ju_uhc_state2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("data\n", 0, 1);
      ex.checkpoint();

      StringBuilder sb = new StringBuilder();
      ex.addState(sb);
      String state = sb.toString();
      assertFalse(state.contains("unchanged"),
         "modified file should NOT show 'unchanged', got: " + state);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── isModified edge cases ────────────────────────────────────

   @Test
   void undoAndRedoLeavesModified() throws IOException {
      String fname = "ju_uhc_modcycle";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("A", 0, 1);
      ex.checkpoint();
      ex.inserttext("B", 0, 1);
      ex.checkpoint();

      ex.undo(); // undo B
      ex.redo(); // redo B
      // Should still show as modified (two inserts)
      assertTrue(ex.isModified(),
         "after undo+redo cycle, file should still be modified");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void multipleCheckpointsDoNotCorruptState() throws IOException {
      String fname = "ju_uhc_multicp";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("X", 0, 1);
      ex.checkpoint();
      ex.checkpoint(); // double checkpoint should be safe
      ex.checkpoint(); // triple checkpoint

      assertEquals("X", ex.at(1).toString());
      ex.undo();
      assertEquals(0, ex.at(1).toString().length());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Remove + undo tests ──────────────────────────────────────

   @Test
   void removeMiddleLineAndUndo() throws IOException {
      String fname = "ju_uhc_remmid";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("alpha\nbeta\ngamma\ndelta\n", 0, 1);
      ex.checkpoint();
      assertEquals(6, ex.finish());

      ex.remove(2, 2); // remove beta, gamma
      ex.checkpoint();
      assertEquals("alpha", ex.at(1).toString());
      assertEquals("delta", ex.at(2).toString());
      assertEquals(4, ex.finish());

      ex.undo();
      assertEquals("alpha", ex.at(1).toString());
      assertEquals("beta", ex.at(2).toString());
      assertEquals("gamma", ex.at(3).toString());
      assertEquals("delta", ex.at(4).toString());
      assertEquals(6, ex.finish());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Change + undo + redo sequence ────────────────────────────

   @Test
   void sequentialChangesUndoRedoInOrder() throws IOException {
      String fname = "ju_uhc_seq";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("original\n", 0, 1);
      ex.checkpoint();

      ex.changeElementAtStr("first edit", 1);
      ex.checkpoint();
      assertEquals("first edit", ex.at(1).toString());

      ex.changeElementAtStr("second edit", 1);
      ex.checkpoint();
      assertEquals("second edit", ex.at(1).toString());

      ex.undo(); // back to "first edit"
      assertEquals("first edit", ex.at(1).toString());

      ex.undo(); // back to "original"
      assertEquals("original", ex.at(1).toString());

      ex.redo(); // forward to "first edit"
      assertEquals("first edit", ex.at(1).toString());

      ex.redo(); // forward to "second edit"
      assertEquals("second edit", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Insert at different positions ────────────────────────────

   @Test
   void insertAtEndAndUndo() throws IOException {
      String fname = "ju_uhc_insend";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("line1\n", 0, 1);
      ex.checkpoint();
      // Insert at end of first line
      ex.inserttext(" appended", 5, 1);
      ex.checkpoint();
      assertEquals("line1 appended", ex.at(1).toString());

      ex.undo();
      assertEquals("line1", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void undoPreservesOtherLines() throws IOException {
      String fname = "ju_uhc_preserve";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();

      ex.changeElementAtStr("BBB", 2);
      ex.checkpoint();
      assertEquals("BBB", ex.at(2).toString());
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("ccc", ex.at(3).toString());

      ex.undo();
      assertEquals("bbb", ex.at(2).toString());
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("ccc", ex.at(3).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Undo past beginning is safe ──────────────────────────────

   @Test
   void repeatedUndoAtBottomIsSafe() throws IOException {
      String fname = "ju_uhc_undobot";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("data", 0, 1);
      ex.checkpoint();

      ex.undo();
      ex.undo(); // already at base
      ex.undo(); // should still be safe
      assertEquals(0, ex.at(1).toString().length());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void repeatedRedoAtTopIsSafe() throws IOException {
      String fname = "ju_uhc_redotop";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("data", 0, 1);
      ex.checkpoint();

      ex.redo(); // already at head
      ex.redo(); // should still be safe
      assertEquals("data", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }
}
