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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end command-parser tests for yank, paste, and delete
 * operations with vim-style register targeting.
 *
 * <p>Tests exercise the full command dispatch path through
 * {@link Rgroup#doCommand} and verify register state in
 * {@link Buffers}.
 */
class RegisterCommandsJUnitTest {

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

   // ── Helpers ───────────────────────────────────────────────

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

   /** CircBuffer that captures clipboard writes for verification. */
   private static final class TestClipCircBuffer
         extends Buffers.CircBuffer {
      String clipboard = null;

      @Override
      public void setclip() { }

      @Override
      public String readClipboard() {
         return clipboard;
      }

      @Override
      public void writeClipboard(String text) {
         clipboard = text;
      }
   }

   private void initBuffers() {
      Buffers.init(new TestClipCircBuffer());
   }

   private TestClipCircBuffer initClipBuffers() {
      TestClipCircBuffer clip = new TestClipCircBuffer();
      Buffers.init(clip);
      return clip;
   }

   // ── Yank (yy) to default register ────────────────────────

   @Test
   @DisplayName("yank single line populates numbered ring and unnamed")
   void yankSingleLinePopulatesRegisters() throws Exception {
      String fname = "ju_rc_ysl";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("alpha\nbeta\ngamma\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // yank 1 line (count=1 via "yank" command, dispatches to YANK)
      Rgroup.doCommand("yank", null, 1, 0, fvc, false);

      // Numbered ring slot 0 should hold the yanked line
      Object buf0 = Buffers.getbuf('0');
      assertNotNull(buf0, "yank should populate buffer 0");
      assertTrue(buf0 instanceof ArrayList,
         "line yank should produce ArrayList");
      @SuppressWarnings("unchecked")
      ArrayList<String> lines = (ArrayList<String>) buf0;
      assertEquals(1, lines.size());
      assertEquals("alpha", lines.get(0));

      // Unnamed register should also hold the yanked content
      Object unnamed = Buffers.getbuf('"');
      assertNotNull(unnamed, "yank should set unnamed register");
      assertTrue(Buffers.isUnnamedLinewise(),
         "line yank is linewise");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("yank multiple lines via count")
   void yankMultipleLines() throws Exception {
      String fname = "ju_rc_yml";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("one\ntwo\nthree\nfour\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 2);

      Rgroup.doCommand("yank", null, 2, 0, fvc, false);

      Object buf0 = Buffers.getbuf('0');
      assertTrue(buf0 instanceof ArrayList);
      @SuppressWarnings("unchecked")
      ArrayList<String> lines = (ArrayList<String>) buf0;
      assertEquals(2, lines.size());
      assertEquals("two", lines.get(0));
      assertEquals("three", lines.get(1));

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Delete (dd) to default register ──────────────────────

   @Test
   @DisplayName("dd removes line and populates unnamed register")
   void ddRemovesLinePopulatesUnnamed() throws Exception {
      String fname = "ju_rc_dd1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("keep\ndelete me\nstay\n", 0, 1);
      ex.checkpoint();
      assertEquals(5, ex.finish()); // 3 content + empty + trailing

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 2);

      // deletemode dispatches "dd" — but that requires key event.
      // Instead, directly test the underlying: remove + record.
      Buffers.recordDelete('0', ex.remove(2, 1));
      ex.checkpoint();

      // Line "delete me" should be gone
      assertEquals("keep", ex.at(1).toString());
      assertEquals("stay", ex.at(2).toString());

      // Unnamed register should hold the deleted line
      Object unnamed = Buffers.getbuf('"');
      assertNotNull(unnamed);
      assertTrue(unnamed instanceof ArrayList);
      @SuppressWarnings("unchecked")
      ArrayList<String> deleted = (ArrayList<String>) unnamed;
      assertEquals(1, deleted.size());
      assertEquals("delete me", deleted.get(0));

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("multiple dd rotates numbered registers")
   void multipleDdRotatesRegisters() throws Exception {
      String fname = "ju_rc_dd2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("A\nB\nC\nD\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);

      // Delete lines one at a time, simulating repeated dd
      Buffers.recordDelete('0', ex.remove(1, 1));
      ex.checkpoint();
      Buffers.recordDelete('0', ex.remove(1, 1));
      ex.checkpoint();
      Buffers.recordDelete('0', ex.remove(1, 1));
      ex.checkpoint();

      // Most recent delete at slot 0, previous at 1, etc.
      Object b0 = Buffers.getbuf('0');
      Object b1 = Buffers.getbuf('1');
      Object b2 = Buffers.getbuf('2');

      assertTrue(b0 instanceof ArrayList);
      assertTrue(b1 instanceof ArrayList);
      assertTrue(b2 instanceof ArrayList);

      @SuppressWarnings("unchecked")
      ArrayList<String> l0 = (ArrayList<String>) b0;
      @SuppressWarnings("unchecked")
      ArrayList<String> l1 = (ArrayList<String>) b1;
      @SuppressWarnings("unchecked")
      ArrayList<String> l2 = (ArrayList<String>) b2;

      assertEquals("C", l0.get(0)); // most recent
      assertEquals("B", l1.get(0));
      assertEquals("A", l2.get(0)); // oldest

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Named register targeting ─────────────────────────────

   @Test
   @DisplayName("yank to named register 'a' stores content there")
   void yankToNamedRegister() throws Exception {
      String fname = "ju_rc_yna";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("target line\nother\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // Simulate "a yank — recordYank with named register
      @SuppressWarnings({ "unchecked", "rawtypes" })
      ArrayList<String> yanked = fvc.getElementsAt(1);
      Buffers.recordYank('a', yanked);

      Object regA = Buffers.getbuf('a');
      assertNotNull(regA, "register 'a' should hold content");
      assertTrue(regA instanceof ArrayList);
      @SuppressWarnings("unchecked")
      ArrayList<String> lines = (ArrayList<String>) regA;
      assertEquals("target line", lines.get(0));

      // Unnamed register also updated
      assertNotNull(Buffers.getbuf('"'));

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("uppercase register appends to existing content")
   @SuppressWarnings({ "unchecked", "rawtypes" })
   void uppercaseRegisterAppends() throws Exception {
      String fname = "ju_rc_app";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("first\nsecond\nthird\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);

      // Yank first line to 'a'
      fvc.cursorabs(0, 1);
      Buffers.recordYank('a', fvc.getElementsAt(1));

      // Yank second line to 'A' (append)
      fvc.cursorabs(0, 2);
      Buffers.recordYank('A', fvc.getElementsAt(1));

      // Register 'a' should now contain both
      Object regA = Buffers.getbuf('a');
      assertTrue(regA instanceof ArrayList);
      ArrayList<String> lines = (ArrayList<String>) regA;
      assertEquals(2, lines.size());
      assertEquals("first", lines.get(0));
      assertEquals("second", lines.get(1));

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Black-hole register ───────────────────────────────────

   @Test
   @DisplayName("delete to black-hole register does not update unnamed")
   void deleteToBlackHoleDiscards() throws Exception {
      String fname = "ju_rc_bh1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("keep\ntarget\nstay\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);

      // First, yank something so unnamed has a value
      Buffers.recordYank('0', "original");
      assertEquals("original", Buffers.getbuf('"'));

      // Delete to black-hole register
      Buffers.recordDelete('_', ex.remove(2, 1).toString());
      ex.checkpoint();

      // Unnamed register should still hold original
      assertEquals("original", Buffers.getbuf('"'));
      assertNull(Buffers.getbuf('_'));

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Clipboard register (* and +) ─────────────────────────

   @Test
   @DisplayName("yank to * writes system clipboard")
   void yankToStarWritesSystemClipboard() throws Exception {
      String fname = "ju_rc_ysc";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      TestClipCircBuffer clip = initClipBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("clipboard line\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      @SuppressWarnings({ "unchecked", "rawtypes" })
      ArrayList<String> yanked = fvc.getElementsAt(1);
      Buffers.recordYank('*', yanked);

      assertNotNull(clip.clipboard,
         "clipboard should be written");
      assertTrue(clip.clipboard.contains("clipboard line"),
         "clipboard should contain yanked text");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("paste from * reads system clipboard")
   void pasteFromStarReadsClipboard() throws Exception {
      String fname = "ju_rc_psc";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      TestClipCircBuffer clip = initClipBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("line1\nline2\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);

      // Simulate system clipboard content
      clip.clipboard = "pasted from system";
      Object fromClip = Buffers.getbuf('*');
      assertEquals("pasted from system", fromClip);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Put (paste) operations ────────────────────────────────

   @Test
   @DisplayName("putafter inserts line below cursor")
   void putAfterInsertsBelow() throws Exception {
      String fname = "ju_rc_pa1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("above\nbelow\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // Put a multi-line yank into the ring
      ArrayList<String> toPut = new ArrayList<>();
      toPut.add("inserted1");
      toPut.add("inserted2");
      Buffers.deleted('0', toPut);

      Rgroup.doCommand("putafter", null, 1, 0, fvc, false);

      int lineCount = ex.finish();
      assertTrue(lineCount >= 5,
         "putafter should add 2 lines to the buffer");
      // Lines should be inserted after line 1
      assertEquals("above", ex.at(1).toString());
      assertEquals("inserted1", ex.at(2).toString());
      assertEquals("inserted2", ex.at(3).toString());
      assertEquals("below", ex.at(4).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("putbefore inserts line above cursor")
   void putBeforeInsertsAbove() throws Exception {
      String fname = "ju_rc_pb1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("above\nbelow\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 2);

      ArrayList<String> toPut = new ArrayList<>();
      toPut.add("pasted");
      Buffers.deleted('0', toPut);

      Rgroup.doCommand("putbefore", null, 1, 0, fvc, false);

      int lineCount = ex.finish();
      assertTrue(lineCount >= 4,
         "putbefore should add 1 line");
      // "pasted" should be inserted at line 2 (before "below")
      assertEquals("above", ex.at(1).toString());
      assertEquals("pasted", ex.at(2).toString());
      assertEquals("below", ex.at(3).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("putafter with charwise string inserts inline")
   void putAfterCharwiseInsertsInline() throws Exception {
      String fname = "ju_rc_pac";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(4, 1); // cursor at 'o' in hello

      Buffers.deleted('0', "XY");

      Rgroup.doCommand("putafter", null, 1, 0, fvc, false);

      String line = ex.at(1).toString();
      assertTrue(line.contains("XY"),
         "putafter with string should insert inline after cursor");
      assertTrue(line.contains("hello"),
         "original text should be preserved");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── deletetoend (D) with register ────────────────────────

   @Test
   @DisplayName("deletetoend captures deleted text in unnamed register")
   void deletetoendCapturesInUnnamed() throws Exception {
      String fname = "ju_rc_dte";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world foo\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(5, 1); // cursor at space before "world"

      Rgroup.doCommand("deletetoend", null, 1, 0, fvc, false);

      String line = ex.at(1).toString();
      assertEquals("hello", line,
         "deletetoend should remove from cursor to end");

      // Deleted text should be in unnamed register
      Object unnamed = Buffers.getbuf('"');
      assertNotNull(unnamed,
         "deleted text should populate unnamed register");
      String deletedText = unnamed.toString();
      assertTrue(deletedText.contains("world"),
         "unnamed should contain deleted text");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Small-delete register ("-) ────────────────────────────

   @Test
   @DisplayName("charwise delete populates small-delete register")
   void charwiseDeletePopulatesSmallDelete() throws Exception {
      String fname = "ju_rc_sd1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdefgh\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(2, 1);

      // deleteChars simulates 'x' — deletes chars at cursor
      fvc.deleteChars('0', true, true, 3);

      // Small-delete register should have the 3 chars
      Object smallDel = Buffers.getbuf('-');
      assertNotNull(smallDel,
         "charwise delete should populate small-delete register");
      assertEquals("cde", smallDel.toString());

      // Line should be modified
      String line = ex.at(1).toString();
      assertEquals("abfgh", line);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("linewise delete does not change small-delete register")
   void linewiseDeleteDoesNotChangeSmallDelete() throws Exception {
      String fname = "ju_rc_sd2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("line1\nline2\nline3\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);

      // First, do a charwise delete to set small-delete
      Buffers.recordDelete('0', "prior");
      assertEquals("prior", Buffers.getbuf('-'));

      // Now do a linewise delete
      ArrayList<String> removed = ex.remove(2, 1);
      Buffers.recordDelete('0', removed);
      ex.checkpoint();

      // Small-delete should still be "prior"
      assertEquals("prior", Buffers.getbuf('-'));
      // But unnamed should reflect linewise delete
      assertTrue(Buffers.isUnnamedLinewise());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Register summary ──────────────────────────────────────

   @Test
   @DisplayName("getRegisterSummary reflects full editing session")
   @SuppressWarnings({ "unchecked", "rawtypes" })
   void registerSummaryAfterEditSession() throws Exception {
      String fname = "ju_rc_sum";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("alpha\nbeta\ngamma\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);

      // Yank to named register
      fvc.cursorabs(0, 1);
      Buffers.recordYank('a', fvc.getElementsAt(1));

      // Delete a line
      Buffers.recordDelete('0', ex.remove(2, 1));
      ex.checkpoint();

      // Check summary includes all relevant info
      String summary = Buffers.getRegisterSummary();
      assertTrue(summary.contains("--- Registers ---"),
         "should have header");
      assertTrue(summary.contains("\"a"),
         "should show named register a");
      assertTrue(summary.contains("alpha"),
         "register a should contain yanked text");
      assertTrue(summary.contains("\"\""),
         "should show unnamed register");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── Yank then paste round-trip ────────────────────────────

   @Test
   @DisplayName("yank then putafter produces correct buffer state")
   void yankThenPutRoundTrip() throws Exception {
      String fname = "ju_rc_ypr";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("A\nB\nC\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // Yank line 1 ("A")
      Rgroup.doCommand("yank", null, 1, 0, fvc, false);

      // Move cursor to line 3 and paste after
      fvc.cursorabs(0, 3);
      Rgroup.doCommand("putafter", null, 1, 0, fvc, false);

      // Buffer should now be: A, B, C, A
      assertEquals("A", ex.at(1).toString());
      assertEquals("B", ex.at(2).toString());
      assertEquals("C", ex.at(3).toString());
      assertEquals("A", ex.at(4).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("delete then putbefore restores deleted content")
   void deleteThenPutRestores() throws Exception {
      String fname = "ju_rc_dpr";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      initBuffers();

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("first\nsecond\nthird\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 2);

      // Delete line 2 ("second")
      Buffers.recordDelete('0', ex.remove(2, 1));
      ex.checkpoint();

      // Now we should have first, third
      assertEquals("first", ex.at(1).toString());
      assertEquals("third", ex.at(2).toString());

      // Put before line 2 should restore
      fvc.cursorabs(0, 2);
      Rgroup.doCommand("putbefore", null, 1, 0, fvc, false);

      assertEquals("first", ex.at(1).toString());
      assertEquals("second", ex.at(2).toString());
      assertEquals("third", ex.at(3).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }
}
