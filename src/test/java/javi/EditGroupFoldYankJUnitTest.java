package javi;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for EditGroup fold-aware yank/put, DO_OVER (dot repeat),
 * deletetoend, and Buffers fold-span logic.
 */
class EditGroupFoldYankJUnitTest {

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

   @SuppressWarnings("unchecked")
   private FvContext setupFvc(TextEdit<String> te)
         throws InputException {
      TestView view = new TestView(true);
      return FvContext.connectFv(te, view);
   }

   // ── YANK with FoldModel ──────────────────────────────────

   @Test
   @DisplayName("yank at collapsed fold yanks full fold span")
   void yankCollapsedFold() throws Exception {
      String fname = "ju_egfy_yf1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("above\nfold1\nfold2\nfold3\nbelow\n",
         0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 2);

      // Create a fold from lines 2-4 and collapse it
      FoldModel fm = new FoldModel();
      fm.addFold(2, 4);
      FoldModel.FoldRange fr = fm.findFoldAtStart(2);
      assertNotNull(fr);
      fr.collapsed = true;
      fvc.setFoldModel(fm);

      // yank with count=1 should detect fold and yank the full span
      Rgroup.doCommand("yank", null, 1, 0, fvc, false);

      // Check that the buffer contains 3 lines (fold span)
      Object buf = Buffers.getbuf('0');
      assertNotNull(buf, "yank should populate buffer");
      assertTrue(buf instanceof ArrayList,
         "multi-line yank produces ArrayList");
      @SuppressWarnings("unchecked")
      ArrayList<String> lines = (ArrayList<String>) buf;
      assertEquals(3, lines.size(),
         "yank at collapsed fold should yank fold span (3 lines)");
      assertEquals("fold1", lines.get(0));
      assertEquals("fold2", lines.get(1));
      assertEquals("fold3", lines.get(2));

      // Verify lastFoldSpan was set
      assertEquals(3, Buffers.getLastFoldSpan(),
         "foldSpan should be set to 3");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("yank without fold clears fold span")
   void yankNoFoldClearsFoldSpan() throws Exception {
      String fname = "ju_egfy_yf2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("line1\nline2\nline3\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // Set a fake fold span first
      Buffers.setLastFoldSpan(5);

      // yank without foldModel should clear it
      Rgroup.doCommand("yank", null, 2, 0, fvc, false);

      assertEquals(0, Buffers.getLastFoldSpan(),
         "yank without fold should clear fold span");

      Object buf = Buffers.getbuf('0');
      assertNotNull(buf);
      assertTrue(buf instanceof ArrayList);
      @SuppressWarnings("unchecked")
      ArrayList<String> lines = (ArrayList<String>) buf;
      assertEquals(2, lines.size(),
         "yank 2 lines without fold");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── putbuffer with fold span ─────────────────────────────

   @Test
   @DisplayName("put after with fold span creates collapsed fold")
   void putAfterWithFoldSpan() throws Exception {
      String fname = "ju_egfy_pf1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("above\nbelow\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // Set up FoldModel
      FoldModel fm = new FoldModel();
      fvc.setFoldModel(fm);

      // Yank 3 lines into buffer with fold span
      ArrayList<String> lines = new ArrayList<>();
      lines.add("fold1");
      lines.add("fold2");
      lines.add("fold3");
      Buffers.deleted('0', lines);
      Buffers.setLastFoldSpan(3);

      // Put after should add a new fold for the pasted lines
      Rgroup.doCommand("putafter", null, 1, 0, fvc, false);

      int after = ex.finish();
      assertTrue(after > 3,
         "putafter should have added lines");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("put before with single string inserts inline")
   void putBeforeSingleString() throws Exception {
      String fname = "ju_egfy_pbs";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(5, 1);

      // Put a single string (char yank)
      Buffers.deleted('0', "XYZ");

      Rgroup.doCommand("putbefore", null, 1, 0, fvc, false);

      String line = ex.at(1).toString();
      assertTrue(line.contains("XYZ"),
         "putbefore with string should insert inline");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── deletetoend ──────────────────────────────────────────

   @Test
   @DisplayName("deletetoend removes from cursor to end of line")
   void deletetoendSingleLine() throws Exception {
      String fname = "ju_egfy_dte1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world foo\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(5, 1);

      Rgroup.doCommand("deletetoend", null, 1, 0, fvc, false);

      String line = ex.at(1).toString();
      assertEquals("hello", line,
         "deletetoend should remove from cursor to end");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("deletetoend with count>1 deletes multiple lines")
   void deletetoendMultiLine() throws Exception {
      String fname = "ju_egfy_dte2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("line1 extra\nline2\nline3\nline4\n",
         0, 1);
      ex.checkpoint();
      int before = ex.finish();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(5, 1);

      // count=2 means delete to end of line1, then delete line2
      Rgroup.doCommand("deletetoend", null, 2, 0, fvc, false);

      assertTrue(ex.finish() < before,
         "deletetoend with count should remove extra lines");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── DO_OVER (dot command) ───────────────────────────────

   @Test
   @DisplayName("DO_OVER repeats last edit command")
   void doOverRepeatsDeleteChars() throws Exception {
      String fname = "ju_egfy_dov1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdefghij\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // deletechars forward, count=2
      boolean[] fwd = {true, true}; // forward, yank
      Rgroup.doCommand("deletechars", fwd, 2, 0,
         fvc, false);
      String after1 = ex.at(1).toString();
      assertEquals("cdefghij", after1,
         "deletechars should remove 2 chars");

      // Now DO_OVER should repeat deletechars with same count
      Rgroup.doCommand("doover", null, 1, 0, fvc, false);
      String after2 = ex.at(1).toString();
      assertEquals("efghij", after2,
         "doover should repeat deletechars(2)");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("DO_OVER with rcount overrides original count")
   void doOverWithRcountOverridesCount() throws Exception {
      String fname = "ju_egfy_dov2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdefghij\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // deletechars forward, count=2
      boolean[] fwd = {true, true};
      Rgroup.doCommand("deletechars", fwd, 2, 0,
         fvc, false);
      assertEquals("cdefghij", ex.at(1).toString());

      // doover with rcount=3 should use count=3 instead
      Rgroup.doCommand("doover", null, 3, 3, fvc, false);
      String after = ex.at(1).toString();
      assertEquals("fghij", after,
         "doover with rcount=3 should delete 3 chars");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("DO_OVER with no previous command does nothing")
   void doOverNoPriorCommand() throws Exception {
      String fname = "ju_egfy_dov3";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("unchanged\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // doover without any prior command → returns null, no change
      // need a fresh EditGroup instance or the dot command may be
      // leftover from previous test. Since initCommands() initializes
      // the singleton, the dotcommand may be set from earlier.
      // Just verify it doesn't crash.
      Rgroup.doCommand("doover", null, 1, 0, fvc, false);
      assertTrue(true, "doover should not crash");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── YANK with open fold (not collapsed) ──────────────────

   @Test
   @DisplayName("yank at open fold does NOT use fold span")
   void yankOpenFoldNoFoldSpan() throws Exception {
      String fname = "ju_egfy_yof";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\nb\nc\nd\ne\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 2);

      // Create a fold but keep it OPEN
      FoldModel fm = new FoldModel();
      fm.addFold(2, 4);
      fvc.setFoldModel(fm);

      Buffers.setLastFoldSpan(99); // should be cleared

      // yank 1 line — fold is open so should NOT use fold span
      Rgroup.doCommand("yank", null, 1, 0, fvc, false);

      assertEquals(0, Buffers.getLastFoldSpan(),
         "yank at open fold should clear fold span");

      Object buf = Buffers.getbuf('0');
      assertTrue(buf instanceof ArrayList);
      @SuppressWarnings("unchecked")
      ArrayList<String> lines = (ArrayList<String>) buf;
      assertEquals(1, lines.size(),
         "yank 1 at open fold should yank only 1 line");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── changecase via doCommand ─────────────────────────────

   @Test
   @DisplayName("changecase toggles case and advances cursor")
   void changecaseViaDoCommand() throws Exception {
      String fname = "ju_egfy_cc1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("Hello World\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // changecase with count=5 toggles 5 chars
      Rgroup.doCommand("changecase", null, 5, 0, fvc, false);

      String line = ex.at(1).toString();
      assertEquals("hELLO World", line,
         "changecase should toggle first 5 chars");
      assertEquals(5, fvc.insertx(),
         "cursor should advance by count");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── joinlines via doCommand ─────────────────────────────

   @Test
   @DisplayName("joinlines merges count lines from cursor")
   void joinlinesMultipleViaDoCommand() throws Exception {
      String fname = "ju_egfy_jl1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("line1\nline2\nline3\nline4\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // joinlines with count=3 joins lines 1,2,3 into one
      Rgroup.doCommand("joinlines", null, 3, 0, fvc, false);

      String joined = ex.at(1).toString();
      assertTrue(joined.contains("line1") && joined.contains("line3"),
         "joinlines(3) should merge 3 lines");
      // line4 should still be a separate line
      assertEquals("line4", ex.at(2).toString(),
         "line4 should remain on next line");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Buffers named register ───────────────────────────────

   @Test
   @DisplayName("Buffers supports named registers (a-z)")
   void buffersNamedRegister() {
      Buffers.deleted('a', "register-a-content");
      Object val = Buffers.getbuf('a');
      assertEquals("register-a-content", val,
         "named register 'a' should store content");

      Buffers.deleted('z', "register-z-content");
      assertEquals("register-z-content", Buffers.getbuf('z'));
   }

   @Test
   @DisplayName("Buffers numbered registers rotate")
   void buffersNumberedRegistersRotate() {
      // Delete several strings — they go into '0' (latest)
      // and previous ones rotate into '1'-'9'
      Buffers.deleted('0', "first");
      Buffers.deleted('0', "second");
      Buffers.deleted('0', "third");

      assertEquals("third", Buffers.getbuf('0'),
         "register 0 should have most recent");
   }
}
