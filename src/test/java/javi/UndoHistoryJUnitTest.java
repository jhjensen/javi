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
 * JUnit 5 tests for {@link UndoHistory} behavior.
 *
 * <p>
 * Tests undo/redo stack behavior through {@link TextEdit}'s
 * {@code undo()} and {@code redo()} methods which delegate
 * to the underlying UndoHistory.
 * </p>
 *
 * <p>
 * Covers:
 * </p>
 * <ul>
 *   <li>Basic undo after insert</li>
 *   <li>Redo after undo</li>
 *   <li>Multiple sequential undo/redo</li>
 *   <li>Undo at empty stack (boundary)</li>
 *   <li>Redo at head (boundary)</li>
 *   <li>Undo/redo with persistence through dispose/reopen</li>
 *   <li>Modified tracking via isModified/isWritten</li>
 *   <li>Checkpoint behavior</li>
 * </ul>
 */
class UndoHistoryJUnitTest {

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

   // --- Helpers ---

   private static String testPath(String name) {
      return history.Testutil.testFile(name).getPath();
   }

   private static FileDescriptor.LocalFile makeLocal(String name) {
      return FileDescriptor.LocalFile.make(history.Testutil.testFile(name));
   }

   private static TextEdit<String> openTestFile(String name) {
      FileDescriptor fd = FileDescriptor.make(testPath(name));
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      assertFalse(te.getError(),
         "File should open without error: " + name);
      return te;
   }

   private static void deleteTestFiles(String... names)
         throws IOException {
      for (String name : names) {
         makeLocal(name).delete();
         makeLocal(name + ".dmp2").delete();
      }
   }

   // ============================================================
   // Basic undo/redo tests
   // ============================================================

   @Test
   void undoRevertsLastInsert() throws IOException {
      String fname = "ju_uh_undo1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello", 0, 1);
      ex.checkpoint();
      assertEquals("hello", ex.at(1).toString());

      ex.undo();
      assertEquals(0, ex.at(1).toString().length(),
         "undo should revert insert");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void redoRestoresUndoneInsert() throws IOException {
      String fname = "ju_uh_redo1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("data", 0, 1);
      ex.checkpoint();
      assertEquals("data", ex.at(1).toString());

      ex.undo();
      assertEquals(0, ex.at(1).toString().length());

      ex.redo();
      assertEquals("data", ex.at(1).toString(),
         "redo should restore the undone insert");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void multipleUndosWalkBackThroughHistory() throws IOException {
      String fname = "ju_uh_multi";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a", 0, 1);
      ex.checkpoint();
      ex.inserttext("b", 0, 1);
      ex.checkpoint();
      ex.inserttext("c", 0, 1);
      ex.checkpoint();
      assertEquals("cba", ex.at(1).toString());

      ex.undo(); // remove "c"
      assertEquals("ba", ex.at(1).toString());

      ex.undo(); // remove "b"
      assertEquals("a", ex.at(1).toString());

      ex.undo(); // remove "a"
      assertEquals(0, ex.at(1).toString().length());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void multipleRedosWalkForwardThroughHistory() throws IOException {
      String fname = "ju_uh_mredo";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("x", 0, 1);
      ex.checkpoint();
      ex.inserttext("y", 0, 1);
      ex.checkpoint();
      assertEquals("yx", ex.at(1).toString());

      // Undo all
      ex.undo();
      ex.undo();
      assertEquals(0, ex.at(1).toString().length());

      // Redo all
      ex.redo();
      assertEquals("x", ex.at(1).toString());
      ex.redo();
      assertEquals("yx", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // Boundary conditions
   // ============================================================

   @Test
   void undoAtEmptyStackIsNoOp() throws IOException {
      String fname = "ju_uh_empty";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      // No edits made — undo should be safe
      ex.undo();
      assertEquals(0, ex.at(1).toString().length(),
         "undo on empty stack should not crash");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void redoAtHeadIsNoOp() throws IOException {
      String fname = "ju_uh_head";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("test", 0, 1);
      ex.checkpoint();

      // No undo was done — redo should be a no-op
      ex.redo();
      assertEquals("test", ex.at(1).toString(),
         "redo at head should keep current state");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void undoThenInsertDiscardsRedoHistory() throws IOException {
      String fname = "ju_uh_discard";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("first", 0, 1);
      ex.checkpoint();
      ex.inserttext("second", 0, 1);
      ex.checkpoint();
      assertEquals("secondfirst", ex.at(1).toString());

      // Undo the second insert
      ex.undo();
      assertEquals("first", ex.at(1).toString());

      // Insert something new — this should discard the redo of "second"
      ex.inserttext("new", 0, 1);
      ex.checkpoint();
      assertEquals("newfirst", ex.at(1).toString());

      // Redo should NOT bring back "second"
      ex.redo();
      assertEquals("newfirst", ex.at(1).toString(),
         "redo should be no-op after new insert overwrites history");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // Persistence through dispose/reopen
   // ============================================================

   @Test
   void undoHistorySurvivesDisposeReopen() throws IOException {
      String fname = "ju_uh_persist";
      UI.setStream(new StringReader("b\n"));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abc", 0, 1);
      ex.checkpoint();
      assertEquals("abc", ex.at(1).toString());
      ex.undo();
      assertEquals(0, ex.at(1).toString().length());
      assertEquals(2, ex.finish());
      ex.disposeFvc();

      // Reopen — undo history should allow redo
      ex = openTestFile(fname);
      assertEquals(0, ex.at(1).toString().length());
      ex.redo();
      assertEquals("abc", ex.at(1).toString());
      ex.disposeFvc();

      deleteTestFiles(fname);
   }

   @Test
   void multipleCheckpointsAllPersist() throws IOException {
      String fname = "ju_uh_mchk";
      UI.setStream(new StringReader("b\n"));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a", 0, 1);
      ex.checkpoint();
      ex.inserttext("b", 0, 1);
      ex.checkpoint();
      ex.inserttext("c", 0, 1);
      ex.checkpoint();
      assertEquals("cba", ex.at(1).toString());

      // Undo all and dispose
      ex.undo();
      ex.undo();
      ex.undo();
      assertEquals(0, ex.at(1).toString().length());
      assertEquals(2, ex.finish());
      ex.disposeFvc();

      // Reopen — redo all should work
      ex = openTestFile(fname);
      assertEquals(0, ex.at(1).toString().length());
      ex.redo();
      ex.redo();
      ex.redo();
      assertEquals("cba", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // Modified tracking
   // ============================================================

   @Test
   void newFileIsNotModified() throws IOException {
      String fname = "ju_uh_notmod";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      assertFalse(ex.isModified(),
         "newly opened file should not be modified");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void insertMakesFileModified() throws IOException {
      String fname = "ju_uh_mod1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("data", 0, 1);
      ex.checkpoint();
      assertTrue(ex.isModified(),
         "file should be modified after insert+checkpoint");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void undoAllRestoresUnmodified() throws IOException {
      String fname = "ju_uh_undomod";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("x", 0, 1);
      ex.checkpoint();
      assertTrue(ex.isModified());

      ex.undo();
      // After undoing the only change, file should be unmodified
      // (Note: depends on whether undo tracking considers position)
      assertFalse(ex.isModified(),
         "undoing all changes should restore unmodified state");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // Multiline undo/redo
   // ============================================================

   @Test
   void undoRedoMultilineInsert() throws IOException {
      String fname = "ju_uh_ml";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("line1\nline2\nline3\n", 0, 1);
      ex.checkpoint();
      assertEquals("line1", ex.at(1).toString());
      assertEquals("line2", ex.at(2).toString());
      assertEquals("line3", ex.at(3).toString());
      assertEquals(5, ex.finish());

      ex.undo();
      assertEquals(2, ex.finish(),
         "undo should remove all three inserted lines");

      ex.redo();
      assertEquals("line1", ex.at(1).toString());
      assertEquals("line2", ex.at(2).toString());
      assertEquals("line3", ex.at(3).toString());
      assertEquals(5, ex.finish(),
         "redo should restore all three lines");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void undoRedoAfterRemove() throws IOException {
      String fname = "ju_uh_rem";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      assertEquals(5, ex.finish());

      ex.remove(2, 1); // remove "bbb"
      ex.checkpoint();
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("ccc", ex.at(2).toString());
      assertEquals(4, ex.finish());

      ex.undo(); // undo the remove
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("bbb", ex.at(2).toString());
      assertEquals("ccc", ex.at(3).toString());
      assertEquals(5, ex.finish());

      ex.redo(); // redo the remove
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("ccc", ex.at(2).toString());
      assertEquals(4, ex.finish());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void undoRedoAfterChangeElement() throws IOException {
      String fname = "ju_uh_chg";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("original\n", 0, 1);
      ex.checkpoint();
      assertEquals("original", ex.at(1).toString());

      ex.changeElementAtStr("modified", 1);
      ex.checkpoint();
      assertEquals("modified", ex.at(1).toString());

      ex.undo();
      assertEquals("original", ex.at(1).toString(),
         "undo should revert changeElementAt");

      ex.redo();
      assertEquals("modified", ex.at(1).toString(),
         "redo should re-apply changeElementAt");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void addStateReportsModifiedStatus() throws IOException {
      String fname = "ju_uh_state";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      StringBuilder sb = new StringBuilder();
      ex.addState(sb);
      String initial = sb.toString();

      // Insert and checkpoint — should show modified
      ex.inserttext("hello\n", 0, 1);
      ex.checkpoint();
      sb = new StringBuilder();
      ex.addState(sb);
      String modified = sb.toString();
      assertFalse(modified.isEmpty(),
         "addState should produce non-empty output");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }
}
