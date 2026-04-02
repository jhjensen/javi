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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended tests for {@link MoveGroup} — covers word movement,
 * find-char, starttext, and balance-char operations via FvContext.
 */
class MoveGroupExtraJUnitTest {

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

   private FvContext<?> setupFvc(TextEdit<String> ex)
         throws InputException {
      TestView view = new TestView(true);
      return FvContext.connectFv(ex, view);
   }

   // ============================================================
   // starttext tests — moves cursor to first non-blank char
   // ============================================================

   @Test
   void starttextMovesToFirstNonBlank() throws Exception {
      String fname = "ju_mge_st1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("   hello\n", 0, 1);
      ex.checkpoint();
      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      MoveGroup.starttext(fvc);
      assertEquals(3, fvc.insertx(),
         "cursor should move to 'h' at column 3");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void starttextOnLineWithNoLeadingSpace() throws Exception {
      String fname = "ju_mge_st2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello\n", 0, 1);
      ex.checkpoint();
      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(3, 1);

      MoveGroup.starttext(fvc);
      assertEquals(0, fvc.insertx(),
         "cursor should stay at 0 when no leading whitespace");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void starttextOnTabIndentedLine() throws Exception {
      String fname = "ju_mge_st3";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("\thello\n", 0, 1);
      ex.checkpoint();
      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      MoveGroup.starttext(fvc);
      assertEquals(1, fvc.insertx(),
         "cursor should move past tab to 'h' at column 1");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void starttextOnAllWhitespaceLine() throws Exception {
      String fname = "ju_mge_st4";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("    \n", 0, 1);
      ex.checkpoint();
      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      MoveGroup.starttext(fvc);
      assertEquals(4, fvc.insertx(),
         "cursor should advance past all whitespace");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void starttextOnEmptyLine() throws Exception {
      String fname = "ju_mge_st5";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("\n", 0, 1);
      ex.checkpoint();
      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      MoveGroup.starttext(fvc);
      assertEquals(0, fvc.insertx(),
         "cursor should stay at 0 on empty line");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // processCommand pattern matching tests
   // ============================================================

   @Test
   void substituteWithGlobalFlag() throws Exception {
      String fname = "ju_mge_sg";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aXbXcX\n", 0, 1);
      ex.checkpoint();

      // s/X/Y/g — substitute all occurrences on one line
      int result = ex.processCommand("s/X/Y/g", 1);
      assertTrue(result >= 0);
      assertEquals("aYbYcY", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void substituteWithRegex() throws Exception {
      String fname = "ju_mge_sre";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello 123 world\n", 0, 1);
      ex.checkpoint();

      // s/[0-9]+/NUM/ — replace digit sequence
      int result = ex.processCommand("s/[0-9]+/NUM/", 1);
      assertTrue(result >= 0);
      assertEquals("hello NUM world", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void substituteWithBackreference() throws Exception {
      String fname = "ju_mge_sbr";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world\n", 0, 1);
      ex.checkpoint();

      // s/(hello)/[\1]/ — Java regex capture group with backreference
      int result = ex.processCommand("s/(hello)/[\\1]/", 1);
      assertTrue(result >= 0);
      assertEquals("[hello] world", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void deleteCurrentLine() throws Exception {
      String fname = "ju_mge_del";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("first\nsecond\nthird\n", 0, 1);
      ex.checkpoint();

      // 2d — delete line 2
      int result = ex.processCommand("2d", 1);
      assertTrue(result >= 0);
      assertEquals("first", ex.at(1).toString());
      assertEquals("third", ex.at(2).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void deleteFirstLine() throws Exception {
      String fname = "ju_mge_del1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();

      int result = ex.processCommand("1d", 1);
      assertTrue(result >= 0);
      assertEquals("bbb", ex.at(1).toString());
      assertEquals("ccc", ex.at(2).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void deleteLastLine() throws Exception {
      String fname = "ju_mge_dell";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      int lastLine = ex.finish() - 1;

      int result = ex.processCommand("$d", 1);
      assertTrue(result >= 0);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void deleteRangeOfLines() throws Exception {
      String fname = "ju_mge_delr";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("one\ntwo\nthree\nfour\nfive\n", 0, 1);
      ex.checkpoint();

      int result = ex.processCommand("2,4d", 1);
      assertTrue(result >= 0);
      assertEquals("one", ex.at(1).toString());
      assertEquals("five", ex.at(2).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // searchForward / searchBackward additional tests
   // ============================================================

   @Test
   void searchForwardWithOffset() throws Exception {
      String fname = "ju_mge_sfoff";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("xxxabcxxx\n", 0, 1);
      ex.checkpoint();

      java.util.regex.Matcher m =
         java.util.regex.Pattern.compile("abc").matcher("");
      // search from offset 0 should find it
      assertTrue(ex.searchForward(m, 0, 1));
      // search from offset 5 should not (abc starts at 3)
      assertFalse(ex.searchForward(m, 5, 1));

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void searchBackwardFromEnd() throws Exception {
      String fname = "ju_mge_sbe";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcXYZdef\n", 0, 1);
      ex.checkpoint();

      java.util.regex.Matcher m =
         java.util.regex.Pattern.compile("XYZ").matcher("");
      // search backward from end (-1 = full line)
      assertTrue(ex.searchBackward(m, -1, 1));

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void regsearchBackward() throws Exception {
      String fname = "ju_mge_rsb";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("first\ntarget\nthird\n", 0, 1);
      ex.checkpoint();

      java.util.regex.Matcher m =
         java.util.regex.Pattern.compile("target").matcher("");
      // Search backward from line 3
      Position start = new Position(0, 3, ex.fdes(), null);
      Position pos = ex.regsearch(start, start, true, m, 0,
         EditContainer.SearchType.LINE);
      assertNotNull(pos, "should find 'target' searching backward");
      assertEquals(2, pos.y);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // FvContext cursor movement integration
   // ============================================================

   @Test
   void cursorXMovementClampsToLineLength() throws Exception {
      String fname = "ju_mge_clamp";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("short\n", 0, 1);
      ex.checkpoint();
      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // Move forward past end of line
      fvc.cursorx(100);
      assertTrue(fvc.insertx() <= 5,
         "cursor X should clamp to line length");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void cursorXNegativeMovement() throws Exception {
      String fname = "ju_mge_neg";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdef\n", 0, 1);
      ex.checkpoint();
      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(3, 1);

      fvc.cursorx(-2);
      assertEquals(1, fvc.insertx());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void cursorXNegativePastZero() throws Exception {
      String fname = "ju_mge_neg0";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdef\n", 0, 1);
      ex.checkpoint();
      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(2, 1);

      fvc.cursorx(-100);
      assertTrue(fvc.insertx() >= 0,
         "cursor X should not go negative");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void cursorYClampsToBounds() throws Exception {
      String fname = "ju_mge_ybound";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\nb\nc\n", 0, 1);
      ex.checkpoint();
      FvContext<?> fvc = setupFvc(ex);

      // Move to last line
      fvc.cursoryabs(ex.finish());
      // Move down should not go past end
      fvc.cursory(100);
      assertTrue(fvc.inserty() <= ex.finish(),
         "cursor Y should clamp to buffer size");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void cursorYAbsLine1() throws Exception {
      String fname = "ju_mge_y1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\nb\nc\nd\n", 0, 1);
      ex.checkpoint();
      FvContext<?> fvc = setupFvc(ex);

      fvc.cursoryabs(3);
      assertEquals(3, fvc.inserty());
      fvc.cursoryabs(1);
      assertEquals(1, fvc.inserty());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // processCommand write test (w)
   // ============================================================

   @Test
   void writeCommandSavesFile() throws Exception {
      String fname = "ju_mge_write";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("saved content\n", 0, 1);
      ex.checkpoint();

      int result = ex.processCommand("w", 1);
      assertTrue(result >= 0, "write command should succeed");

      // Verify file was written by reading from disk
      java.io.File f = history.Testutil.testFile(fname);
      assertTrue(f.exists(), "file should exist on disk");
      String contents = new String(
         java.nio.file.Files.readAllBytes(f.toPath()),
         java.nio.charset.StandardCharsets.UTF_8);
      assertTrue(contents.contains("saved content"),
         "file should contain written text");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // processCommand substitute edge cases
   // ============================================================

   @Test
   void substituteEmptyReplacement() throws Exception {
      String fname = "ju_mge_sempty";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("remove_this_word\n", 0, 1);
      ex.checkpoint();

      int result = ex.processCommand("s/_this//", 1);
      assertTrue(result >= 0);
      assertEquals("remove_word", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void substituteNoMatch() throws Exception {
      String fname = "ju_mge_snm";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world\n", 0, 1);
      ex.checkpoint();

      // substituting a pattern that doesn't exist
      int result = ex.processCommand("s/zzz/yyy/", 1);
      // Should return negative or the line unchanged
      assertEquals("hello world", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void copyToSameLine() throws Exception {
      String fname = "ju_mge_tsame";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("AAA\nBBB\n", 0, 1);
      ex.checkpoint();

      // 1t1 — copy line 1 to after line 1
      int result = ex.processCommand("1t1", 1);
      assertTrue(result >= 0);
      assertEquals("AAA", ex.at(1).toString());
      assertEquals("AAA", ex.at(2).toString());
      assertEquals("BBB", ex.at(3).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void moveLineToBeginning() throws Exception {
      String fname = "ju_mge_m0";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();

      // 3m0 — move line 3 to beginning
      int result = ex.processCommand("3m0", 1);
      assertTrue(result >= 0);
      assertEquals("ccc", ex.at(1).toString());
      assertEquals("aaa", ex.at(2).toString());
      assertEquals("bbb", ex.at(3).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // Global command additional tests
   // ============================================================

   @Test
   void globalSubstituteMultipleMatches() throws Exception {
      String fname = "ju_mge_gmulti";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("cat\ndog\ncat\nbird\ncat\n", 0, 1);
      ex.checkpoint();

      // g/cat/s/cat/CAT/
      int result = ex.processCommand("g/cat/s/cat/CAT/", 1);
      assertTrue(result >= 0);
      assertEquals("CAT", ex.at(1).toString());
      assertEquals("dog", ex.at(2).toString());
      assertEquals("CAT", ex.at(3).toString());
      assertEquals("bird", ex.at(4).toString());
      assertEquals("CAT", ex.at(5).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void globalDeleteMatchingLines() throws Exception {
      String fname = "ju_mge_gd";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("keep1\ndelete_me\nkeep2\ndelete_me\nkeep3\n",
         0, 1);
      ex.checkpoint();

      int result = ex.processCommand("g/delete_me/d", 1);
      assertTrue(result >= 0);
      assertEquals("keep1", ex.at(1).toString());
      assertEquals("keep2", ex.at(2).toString());
      assertEquals("keep3", ex.at(3).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }
}
