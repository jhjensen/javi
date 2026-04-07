package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for {@link EditGroup} edit operations —
 * deleteChars, deletetoend, joinlines, changecase, yank/put.
 */
class EditGroupExtendedCoverageJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
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

   private static FileDescriptor.LocalFile makeLocal(String name) {
      return FileDescriptor.LocalFile.make(
         history.Testutil.testFile(name));
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
         makeLocal(name).delete();
         makeLocal(name + ".dmp2").delete();
      }
   }

   // ── joinlines ──────────────────────────────────────────────

   @Test
   void joinlinesMergesTwoLines() throws Exception {
      String fname = "ju_egx_join1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("hello\nworld\n", 0, 1);
      te.checkpoint();
      assertEquals("hello", te.at(1).toString());
      assertEquals("world", te.at(2).toString());

      int newX = te.joinlines(1, 1);
      assertTrue(newX >= 0);
      assertEquals("hello world", te.at(1).toString());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void joinlinesMultipleCount() throws Exception {
      String fname = "ju_egx_join2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("a\nb\nc\nd\n", 0, 1);
      te.checkpoint();
      int before = te.finish();

      te.joinlines(2, 1);
      assertTrue(te.finish() < before,
         "joining should reduce line count");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── changecase ─────────────────────────────────────────────

   @Test
   void changecaseTogglesCase() throws Exception {
      String fname = "ju_egx_case1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("Hello\n", 0, 1);
      te.checkpoint();
      assertEquals("Hello", te.at(1).toString());

      te.changecase(0, 1, 5, 1);
      assertEquals("hELLO", te.at(1).toString());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void changecaseSingleChar() throws Exception {
      String fname = "ju_egx_case2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("abc\n", 0, 1);
      te.checkpoint();

      te.changecase(0, 1, 1, 1);
      assertEquals("Abc", te.at(1).toString());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── deletetext ─────────────────────────────────────────────

   @Test
   void deletetextRemovesPartOfLine() throws Exception {
      String fname = "ju_egx_del1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("line1\nline2\nline3\n", 0, 1);
      te.checkpoint();
      assertEquals("line2", te.at(2).toString());

      String deleted = te.deletetext(false, 0, 2, 3, 2);
      assertNotNull(deleted);
      // After deleting chars 0-3 of line 2, only "e2" remains
      assertEquals("e2", te.at(2).toString());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void deletetextCrossLineRange() throws Exception {
      String fname = "ju_egx_del2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("aaa\nbbb\nccc\n", 0, 1);
      te.checkpoint();

      String deleted = te.deletetext(false, 1, 1, 1, 3);
      assertNotNull(deleted);
      assertTrue(deleted.length() > 0);

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── inserttext + undo ──────────────────────────────────────

   @Test
   void insertUndoRestoresOriginal() throws Exception {
      String fname = "ju_egx_undo1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("original\n", 0, 1);
      te.checkpoint();
      assertEquals("original", te.at(1).toString());

      te.inserttext("extra\n", 0, 2);
      te.checkpoint();

      te.undo();
      assertEquals("original", te.at(1).toString());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── deleteChars via FvContext ───────────────────────────────

   @Test
   void deleteCharsForward() throws Exception {
      String fname = "ju_egx_delch1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("abcdef\n", 0, 1);
      te.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(te, view);
      fvc.cursorabs(0, 1);

      fvc.deleteChars('0', true, true, 2);
      String line = te.at(1).toString();
      assertEquals("cdef", line,
         "should delete 2 chars forward from col 0");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void deleteCharsBackward() throws Exception {
      String fname = "ju_egx_delch2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("abcdef\n", 0, 1);
      te.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(te, view);
      fvc.cursorabs(3, 1);

      fvc.deleteChars('0', false, false, 2);
      String line = te.at(1).toString();
      assertEquals("adef", line,
         "should delete 2 chars backward from col 3");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Yank (Y) via Rgroup dispatch ───────────────────────────

   @Test
   void yankLinesStoresInBuffer() throws Exception {
      String fname = "ju_egx_yank1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("line1\nline2\nline3\n", 0, 1);
      te.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(te, view);
      fvc.cursorabs(0, 1);

      // Yank 2 lines ("Y" is rnum 21 in EditGroup)
      Rgroup.doCommand("yank", null, 2, 0, fvc, false);

      // The yanked text should be in the delete buffer
      @SuppressWarnings("unchecked")
      java.util.ArrayList<String> buf =
         (java.util.ArrayList<String>) Buffers.getbuf('0');
      assertNotNull(buf);
      assertFalse(buf.isEmpty());
      assertEquals("line1", buf.get(0));
      assertEquals("line2", buf.get(1));

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Substitute line (S) ────────────────────────────────────

   @Test
   void editGroupRegistersAllCommands() {
      assertNotNull(Rgroup.bindingLookup("insert"));
      assertNotNull(Rgroup.bindingLookup("append"));
      assertNotNull(Rgroup.bindingLookup("openline"));
      assertNotNull(Rgroup.bindingLookup("deletechars"));
      assertNotNull(Rgroup.bindingLookup("deletetoend"));
      assertNotNull(Rgroup.bindingLookup("deletemode"));
      assertNotNull(Rgroup.bindingLookup("joinlines"));
      assertNotNull(Rgroup.bindingLookup("subchar"));
      assertNotNull(Rgroup.bindingLookup("changecase"));
      assertNotNull(Rgroup.bindingLookup("changemode"));
      assertNotNull(Rgroup.bindingLookup("putbefore"));
      assertNotNull(Rgroup.bindingLookup("putafter"));
      assertNotNull(Rgroup.bindingLookup("yankmode"));
      assertNotNull(Rgroup.bindingLookup("yank"));
      assertNotNull(Rgroup.bindingLookup("doover"));
      assertNotNull(Rgroup.bindingLookup("markmode"));
      assertNotNull(Rgroup.bindingLookup("shiftmode"));
   }
}
