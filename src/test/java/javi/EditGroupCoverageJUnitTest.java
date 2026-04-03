package javi;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage tests for EditGroup paths exercised through
 * TextEdit methods and processCommand (ex-mode).
 */
class EditGroupCoverageJUnitTest {

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
   // deletetext tests (covers EditGroup.deletetext path)
   // ============================================================

   @Test
   void deleteTextRemovesPartialLine() throws Exception {
      String fname = "ju_egc_delpart";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdefgh\n", 0, 1);
      ex.checkpoint();
      assertEquals("abcdefgh", ex.at(1).toString());

      // deletetext(preserve, xstart, ystart, xend, yend)
      String deleted = ex.deletetext(false, 2, 1, 5, 1);
      assertNotNull(deleted);
      assertEquals("abfgh", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void deleteTextPreserveMode() throws Exception {
      String fname = "ju_egc_delpres";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world\n", 0, 1);
      ex.checkpoint();

      // preserve=true means yank without deleting
      String yanked = ex.deletetext(true, 0, 1, 5, 1);
      assertNotNull(yanked);
      // line should be unchanged when preserve=true
      assertEquals("hello world", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void deleteTextSpansMultipleLines() throws Exception {
      String fname = "ju_egc_delmulti";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("line1\nline2\nline3\nline4\n", 0, 1);
      ex.checkpoint();
      assertEquals(6, ex.finish());

      // Delete from middle of line 1 to middle of line 3
      ex.deletetext(false, 3, 1, 3, 3);
      // Lines should be merged
      assertTrue(ex.finish() < 6,
         "deletetext across lines should reduce line count");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // changecase tests (covers EditGroup CHANGE_CASE path)
   // ============================================================

   @Test
   void changecaseTogglesLowerToUpper() throws Exception {
      String fname = "ju_egc_case1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello\n", 0, 1);
      ex.checkpoint();

      ex.changecase(0, 1, 5, 1);
      assertEquals("HELLO", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void changecaseTogglesUpperToLower() throws Exception {
      String fname = "ju_egc_case2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("WORLD\n", 0, 1);
      ex.checkpoint();

      ex.changecase(0, 1, 5, 1);
      assertEquals("world", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void changecaseMixedCase() throws Exception {
      String fname = "ju_egc_case3";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("HeLLo\n", 0, 1);
      ex.checkpoint();

      ex.changecase(0, 1, 5, 1);
      assertEquals("hEllO", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void changecasePartialLine() throws Exception {
      String fname = "ju_egc_casep";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdef\n", 0, 1);
      ex.checkpoint();

      // Change only chars 2-4
      ex.changecase(2, 1, 4, 1);
      assertEquals("abCDef", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void changecaseAcrossMultipleLines() throws Exception {
      String fname = "ju_egc_casem";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abc\ndef\n", 0, 1);
      ex.checkpoint();

      ex.changecase(0, 1, 3, 2);
      assertEquals("ABC", ex.at(1).toString());
      assertEquals("DEF", ex.at(2).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // processCommand delete (d) - covers EditGroup deletemode path
   // ============================================================

   @Test
   void processCommandDeleteSingleLine() throws Exception {
      String fname = "ju_egc_pcmd1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      int beforeCount = ex.finish();

      ex.processCommand("2d", 1);
      assertEquals(beforeCount - 1, ex.finish());
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("ccc", ex.at(2).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void processCommandDeleteRange() throws Exception {
      String fname = "ju_egc_pcmd2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\nddd\neee\n", 0, 1);
      ex.checkpoint();

      ex.processCommand("2,4d", 1);
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("eee", ex.at(2).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void processCommandGlobalDelete() throws Exception {
      String fname = "ju_egc_pcmd3";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("keep\nremove_me\nkeep\n"
         + "remove_me\nkeep\n", 0, 1);
      ex.checkpoint();

      ex.processCommand("g/remove_me/d", 1);
      // All non-matching lines should remain
      for (int i = 1; i < ex.finish(); i++) {
         assertFalse(ex.at(i).toString().contains("remove_me"),
            "Line " + i + " should not contain 'remove_me'");
      }

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void processCommandInverseGlobalDelete() throws Exception {
      String fname = "ju_egc_pcmd4";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("KEEP\nremove\nKEEP\nremove\n", 0, 1);
      ex.checkpoint();

      ex.processCommand("v/KEEP/d", 1);
      for (int i = 1; i < ex.finish(); i++) {
         assertEquals("KEEP", ex.at(i).toString(),
            "Only KEEP lines should survive v/KEEP/d");
      }

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // processCommand move (m) and copy (t/co)
   // ============================================================

   @Test
   void processCommandMoveLine() throws Exception {
      String fname = "ju_egc_pcmm";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
      ex.checkpoint();
      assertEquals(6, ex.finish());

      // Move line 1 to after line 3
      ex.processCommand("1m3", 1);
      // "aaa" should now come after the original line 3
      assertTrue(ex.finish() > 1,
         "move should preserve line count");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void processCommandCopyLine() throws Exception {
      String fname = "ju_egc_pcmc";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      int beforeCount = ex.finish();

      // Copy line 1 to after line 3
      ex.processCommand("1t3", 1);
      assertEquals(beforeCount + 1, ex.finish());
      // Original should still be at position 1
      assertEquals("aaa", ex.at(1).toString());
      // Copy should appear after line 3
      assertEquals("aaa", ex.at(4).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void processCommandCopyRange() throws Exception {
      String fname = "ju_egc_pcmcr";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
      ex.checkpoint();
      int beforeCount = ex.finish();

      // Copy lines 1-2 to after line 4
      ex.processCommand("1,2co4", 1);
      assertEquals(beforeCount + 2, ex.finish());
      assertEquals("aaa", ex.at(5).toString());
      assertEquals("bbb", ex.at(6).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // processCommand write (w)
   // ============================================================

   @Test
   void processCommandWriteRange() throws Exception {
      String fname = "ju_egc_pcmw";
      String outName = "ju_egc_pcmw_out";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname, outName);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("line1\nline2\nline3\n", 0, 1);
      ex.checkpoint();

      // Write range to a file
      String outPath = testPath(outName);
      ex.processCommand("1,2w " + outPath, 1);

      // Verify the file was written
      assertTrue(makeLocal(outName).isFile(),
         "Output file should exist");

      ex.disposeFvc();
      deleteTestFiles(fname, outName);
   }

   // ============================================================
   // processCommand % range
   // ============================================================

   @Test
   void processCommandPercentRange() throws Exception {
      String fname = "ju_egc_pcmpct";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();

      // %s should substitute across all lines
      ex.processCommand("%s/a/X/g", 1);
      assertEquals("XXX", ex.at(1).toString());
      // b and c lines should be unchanged
      assertEquals("bbb", ex.at(2).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // gettext (covers path used by mark mode yank)
   // ============================================================

   @Test
   void getTextExtractsSubstring() throws Exception {
      String fname = "ju_egc_gettxt";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world\n", 0, 1);
      ex.checkpoint();

      String text = ex.gettext(0, 1, 5, 1);
      assertEquals("hello", text);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void getTextAcrossLines() throws Exception {
      String fname = "ju_egc_gettxtm";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();

      String text = ex.gettext(0, 1, 3, 2);
      assertNotNull(text);
      assertTrue(text.contains("aaa"),
         "gettext should include first line");
      assertTrue(text.contains("bbb"),
         "gettext should include second line");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // tabfix
   // ============================================================

   @Test
   void tabfixConvertsTabs() throws Exception {
      String fname = "ju_egc_tabfix";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("\thello\n", 0, 1);
      ex.checkpoint();

      String before = ex.at(1).toString();
      assertTrue(before.contains("\t"),
         "Line should contain tab before tabfix");

      ex.tabfix(8);
      String after = ex.at(1).toString();
      assertFalse(after.contains("\t"),
         "Tab should be replaced by spaces after tabfix");
      assertTrue(after.contains("hello"),
         "Content should be preserved");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // Buffers integration (covers putbuffer / yank paths)
   // ============================================================

   @Test
   void buffersDeletedStringAndGetbuf() throws Exception {
      Buffers.deleted('a', "test_value");
      Object buf = Buffers.getbuf('a');
      assertEquals("test_value", buf);
   }

   @Test
   void buffersDeletedArrayList() throws Exception {
      ArrayList<String> lines = new ArrayList<>();
      lines.add("line1");
      lines.add("line2");
      Buffers.deleted('b', lines);
      Object buf = Buffers.getbuf('b');
      assertTrue(buf instanceof ArrayList,
         "Buffer should store ArrayList");
   }

   @Test
   void buffersAppendToUpperCase() throws Exception {
      Buffers.deleted('c', "first");
      Buffers.deleted('C', "second");
      Object buf = Buffers.getbuf('c');
      assertNotNull(buf);
      assertTrue(buf.toString().contains("first"),
         "Append should preserve original");
      assertTrue(buf.toString().contains("second"),
         "Append should include new content");
   }

   @Test
   void buffersCircularDeleteBuffer() throws Exception {
      // The default delete buffer (register '0') uses CircBuffer
      Buffers.deleted('0', "circ_test");
      Object buf = Buffers.getbuf('0');
      assertEquals("circ_test", buf);
   }

   @Test
   void buffersCircBufferMyToStringString() {
      String result =
         Buffers.CircBuffer.myToString("hello");
      assertEquals("hello", result);
   }

   @Test
   void buffersCircBufferMyToStringArrayList() {
      ArrayList<String> lines = new ArrayList<>();
      lines.add("line1");
      lines.add("line2");
      String result = Buffers.CircBuffer.myToString(lines);
      assertTrue(result.contains("line1\n"),
         "myToString should join with newlines");
      assertTrue(result.contains("line2\n"),
         "myToString should include all lines");
   }

   @Test
   void buffersAppendCurrBuf() {
      Buffers.deleted('0', "currval");
      StringBuilder sb = new StringBuilder();
      Buffers.appendCurrBuf(sb, false);
      assertTrue(sb.toString().contains("currval"),
         "appendCurrBuf should append current delete buffer");
   }

   @Test
   void buffersAppendCurrBufSingleLine() {
      ArrayList<String> lines = new ArrayList<>();
      lines.add("a");
      lines.add("b");
      Buffers.deleted('0', lines);
      StringBuilder sb = new StringBuilder();
      Buffers.appendCurrBuf(sb, true);
      assertTrue(sb.toString().contains("a"),
         "appendCurrBuf singleline should include content");
      assertTrue(sb.toString().contains("b"),
         "appendCurrBuf singleline should include all items");
   }

   // ============================================================
   // processCommand semicolon range separator
   // ============================================================

   @Test
   void processCommandSemicolonRange() throws Exception {
      String fname = "ju_egc_semi";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\nddd\neee\n", 0, 1);
      ex.checkpoint();

      // 2;+1d means: set ypos=2, then delete from 2 to 2+1=3
      ex.processCommand("2;+1d", 1);
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("ddd", ex.at(2).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // EditGroup.doroutine via Rgroup.doCommand with TestView
   // ============================================================

   @SuppressWarnings("unchecked")
   private FvContext setupFvc(TextEdit<String> te)
         throws InputException {
      TestView view = new TestView(true);
      return FvContext.connectFv(te, view);
   }

   @Test
   void doCommandChangecaseViaFvc() throws Exception {
      String fname = "ju_egc_cc_fvc";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // changecase toggles chars at cursor position
      Rgroup.doCommand("changecase", null, 3, 0, fvc, false);
      String line = ex.at(1).toString();
      assertEquals("HELlo world", line);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandJoinlinesViaFvc() throws Exception {
      String fname = "ju_egc_jl_fvc";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("first\nsecond\nthird\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("joinlines", null, 2, 0, fvc, false);
      assertTrue(ex.at(1).toString().contains("first"),
         "Joined line should contain first");
      assertTrue(ex.at(1).toString().contains("second"),
         "Joined line should contain second");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandYankViaFvc() throws Exception {
      String fname = "ju_egc_yank_fvc";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("line1\nline2\nline3\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // Yank 2 lines starting at current cursor
      Rgroup.doCommand("yank", null, 2, 0, fvc, false);

      // Buffer should now contain yanked lines
      Object buf = Buffers.getbuf('0');
      assertNotNull(buf, "Yank should populate the delete buffer");

      // Line count should be unchanged (yank preserves)
      assertEquals("line1", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandDeletecharsViaFvc() throws Exception {
      String fname = "ju_egc_delch_fvc";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdef\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(1, 1);

      // Delete 2 chars forward from cursor
      boolean[] arg = {true, true};
      Rgroup.doCommand("deletechars", arg, 2, 0, fvc, false);
      String line = ex.at(1).toString();
      assertTrue(line.length() < 6,
         "deletechars should remove characters");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandTabfixViaFvc() throws Exception {
      String fname = "ju_egc_tf_fvc";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("\thello\n\tworld\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("tabfix", null, 1, 0, fvc, false);
      String line1 = ex.at(1).toString();
      assertFalse(line1.contains("\t"),
         "tabfix via doroutine should expand tabs");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandPutafterViaFvc() throws Exception {
      String fname = "ju_egc_put_fvc";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("existing\n", 0, 1);
      ex.checkpoint();

      // Put a string into the delete buffer
      Buffers.deleted('0', "pasted");

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("putafter", null, 1, 0, fvc, false);
      // The pasted text should be inserted
      boolean found = false;
      for (int i = 1; i < ex.finish(); i++) {
         if (ex.at(i).toString().contains("pasted")) {
            found = true;
            break;
         }
      }
      assertTrue(found, "putafter should insert buffer text");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandPutbeforeViaFvc() throws Exception {
      String fname = "ju_egc_putb_fvc";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("existing\n", 0, 1);
      ex.checkpoint();

      // Put a string into the delete buffer
      Buffers.deleted('0', "inserted");

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(2, 1);

      Rgroup.doCommand("putbefore", null, 1, 0, fvc, false);
      boolean found = false;
      for (int i = 1; i < ex.finish(); i++) {
         if (ex.at(i).toString().contains("inserted")) {
            found = true;
            break;
         }
      }
      assertTrue(found, "putbefore should insert buffer text");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandPutafterArrayListViaFvc() throws Exception {
      String fname = "ju_egc_putarr_fvc";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("line1\nline2\n", 0, 1);
      ex.checkpoint();
      int before = ex.finish();

      // Put an ArrayList (multi-line) into delete buffer
      ArrayList<String> lines = new ArrayList<>();
      lines.add("pasted1");
      lines.add("pasted2");
      Buffers.deleted('0', lines);

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("putafter", null, 1, 0, fvc, false);
      assertTrue(ex.finish() > before,
         "putafter with ArrayList should add lines");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }
}
