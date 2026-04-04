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
 * Extended tests for {@link EditGroup} — additional coverage of
 * text editing operations through TextEdit API and processCommand.
 */
class EditGroupExtraJUnitTest {

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

   // --- Helpers ---

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
   // inserttext tests
   // ============================================================

   @Test
   void insertTextSingleLine() throws Exception {
      String fname = "ju_ege_ins1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello\n", 0, 1);
      ex.checkpoint();

      assertEquals("hello", ex.at(1).toString());
      assertEquals(3, ex.finish()); // 1 content + 1 trailing + 1

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void insertTextMultipleLines() throws Exception {
      String fname = "ju_ege_ins2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\nb\nc\n", 0, 1);
      ex.checkpoint();

      assertEquals("a", ex.at(1).toString());
      assertEquals("b", ex.at(2).toString());
      assertEquals("c", ex.at(3).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void insertTextAtMiddle() throws Exception {
      String fname = "ju_ege_insm";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("first\nlast\n", 0, 1);
      ex.checkpoint();

      // Insert between first and last
      ex.inserttext("middle\n", 0, 2);
      assertEquals("first", ex.at(1).toString());
      assertEquals("middle", ex.at(2).toString());
      assertEquals("last", ex.at(3).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // deletetext additional tests
   // ============================================================

   @Test
   void deletetextEntireLine() throws Exception {
      String fname = "ju_ege_dtf";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world\n", 0, 1);
      ex.checkpoint();

      String deleted = ex.deletetext(false, 0, 1, 11, 1);
      assertEquals("hello world", deleted);
      assertEquals("", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void deletetextFromBeginning() throws Exception {
      String fname = "ju_ege_dtb";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdefghij\n", 0, 1);
      ex.checkpoint();

      String deleted = ex.deletetext(false, 0, 1, 3, 1);
      assertEquals("abc", deleted);
      assertEquals("defghij", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void deletetextFromEnd() throws Exception {
      String fname = "ju_ege_dte";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdefghij\n", 0, 1);
      ex.checkpoint();

      String deleted = ex.deletetext(false, 7, 1, 10, 1);
      assertEquals("hij", deleted);
      assertEquals("abcdefg", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // changecase additional tests
   // ============================================================

   @Test
   void changecaseNumbers() throws Exception {
      String fname = "ju_ege_ccn";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abc123DEF\n", 0, 1);
      ex.checkpoint();

      ex.changecase(0, 1, 9, 1);
      assertEquals("ABC123def", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void changecaseSingleChar() throws Exception {
      String fname = "ju_ege_cc1c";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("Hello\n", 0, 1);
      ex.checkpoint();

      ex.changecase(0, 1, 1, 1);
      assertEquals("hello", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void changecaseSpecialChars() throws Exception {
      String fname = "ju_ege_ccs";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a!B@c\n", 0, 1);
      ex.checkpoint();

      ex.changecase(0, 1, 5, 1);
      // Letters toggle, symbols stay
      assertEquals("A!b@C", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // joinlines additional tests
   // ============================================================

   @Test
   void joinSingleLine() throws Exception {
      String fname = "ju_ege_j1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("only line\nsecond\n", 0, 1);
      ex.checkpoint();

      // join count=1 means join current line with next
      ex.joinlines(1, 1);
      assertEquals("only line second", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void joinAllLines() throws Exception {
      String fname = "ju_ege_jall";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\nb\nc\nd\n", 0, 1);
      ex.checkpoint();

      ex.joinlines(4, 1);
      assertEquals("a b c d", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // tabfix additional tests
   // ============================================================

   @Test
   void tabfixMultipleLinesWithTabs() throws Exception {
      String fname = "ju_ege_tfml";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("\ta\n\tb\n\tc\n", 0, 1);
      ex.checkpoint();

      ex.tabfix(4);
      assertFalse(ex.at(1).toString().contains("\t"));
      assertFalse(ex.at(2).toString().contains("\t"));
      assertFalse(ex.at(3).toString().contains("\t"));
      assertTrue(ex.at(1).toString().endsWith("a"));
      assertTrue(ex.at(2).toString().endsWith("b"));
      assertTrue(ex.at(3).toString().endsWith("c"));

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void tabfixConsecutiveTabs() throws Exception {
      String fname = "ju_ege_tfct";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("\t\tX\n", 0, 1);
      ex.checkpoint();

      ex.tabfix(4);
      String result = ex.at(1).toString();
      assertFalse(result.contains("\t"));
      assertTrue(result.endsWith("X"));
      // Two tabs at tabstop 4: 4+4=8 spaces before X
      assertEquals("        X", result);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // processCommand read (r) tests
   // ============================================================

   @Test
   void readCommandNotHandledByProcessCommand() throws Exception {
      String fname = "ju_ege_rcmd";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("existing\n", 0, 1);
      ex.checkpoint();

      // processCommand does not support 'r' — returns -1
      int result = ex.processCommand("1r /dev/null", 1);
      assertEquals(-1, result,
         "r command not handled by processCommand");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // Substitute with range addressing
   // ============================================================

   @Test
   void substituteOnCurrentLine() throws Exception {
      String fname = "ju_ege_scur";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("old value\nold value\n", 0, 1);
      ex.checkpoint();

      // Substitute only on line 1 (current)
      int result = ex.processCommand("1s/old/new/", 1);
      assertTrue(result >= 0);
      assertEquals("new value", ex.at(1).toString());
      assertEquals("old value", ex.at(2).toString(),
         "line 2 should be unchanged");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void substituteOnLastLine() throws Exception {
      String fname = "ju_ege_slast";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("untouched\nold text\n", 0, 1);
      ex.checkpoint();

      int result = ex.processCommand("$s/old/new/", 1);
      assertTrue(result >= 0);
      assertEquals("untouched", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }
}
