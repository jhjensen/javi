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
 * JUnit 5 tests for {@link MoveGroup} commands.
 *
 * <p>
 * Tests movement-related operations accessible through
 * {@code processCommand} (ex-mode navigation) and through
 * direct {@code TextEdit}/{@code EditContainer} search APIs.
 * </p>
 *
 * <p>
 * Also verifies that key MoveGroup commands are properly
 * registered via {@code bindingLookup}.
 * </p>
 */
class MoveGroupJUnitTest {

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
   // Command registration tests
   // ============================================================

   @Test
   void movecharIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("movechar"),
         "movechar should be registered by MoveGroup");
   }

   @Test
   void movelineIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("moveline"),
         "moveline should be registered by MoveGroup");
   }

   @Test
   void forwardwordIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("forwardword"),
         "forwardword should be registered by MoveGroup");
   }

   @Test
   void backwardwordIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("backwardword"),
         "backwardword should be registered by MoveGroup");
   }

   @Test
   void gotolineIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("gotoline"),
         "gotoline should be registered by MoveGroup");
   }

   @Test
   void regsearchIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("regsearch"),
         "regsearch should be registered by MoveGroup");
   }

   @Test
   void searchcommandIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("searchcommand"),
         "searchcommand should be registered by MoveGroup");
   }

   @Test
   void markIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("mark"),
         "mark should be registered by MoveGroup");
   }

   @Test
   void findmarkIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("findmark"),
         "findmark should be registered by MoveGroup");
   }

   @Test
   void balancecharIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("balancechar"),
         "balancechar should be registered by MoveGroup");
   }

   // ============================================================
   // processCommand line-navigation tests
   // ============================================================

   @Test
   void gotoLineByNumber() throws Exception {
      String fname = "ju_mg_goto1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("line1\nline2\nline3\nline4\nline5\n", 0, 1);
      ex.checkpoint();

      // Line number returns that line
      assertEquals(3, ex.processCommand("3", 1));
      assertEquals(5, ex.processCommand("5", 1));
      assertEquals(1, ex.processCommand("1", 1));

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void gotoDollarReturnsLastLine() throws Exception {
      String fname = "ju_mg_dollar";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();

      int result = ex.processCommand("$", 1);
      assertEquals(ex.finish() - 1, result,
         "$ should refer to last content line");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void lineOffsetWithPlus() throws Exception {
      String fname = "ju_mg_offset";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\nddd\neee\n", 0, 1);
      ex.checkpoint();

      // "2+1" should resolve to line 3
      int result = ex.processCommand("2+1", 1);
      assertEquals(3, result);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void lineOffsetWithMinus() throws Exception {
      String fname = "ju_mg_offminus";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\nddd\neee\n", 0, 1);
      ex.checkpoint();

      // "$-1" should be the second-to-last content line
      int lastLine = ex.finish() - 1;
      int result = ex.processCommand("$-1", 1);
      assertEquals(lastLine - 1, result);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // processCommand search tests (via substitute to verify match)
   // ============================================================

   @Test
   void searchSubstituteOnSpecificLine() throws Exception {
      String fname = "ju_mg_search1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("apple\nbanana\ncherry\n", 0, 1);
      ex.checkpoint();

      // Substitute on line containing "banana"
      int result = ex.processCommand("2s/banana/grape/", 1);
      assertTrue(result >= 0);
      assertEquals("grape", ex.at(2).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void globalSubstituteMatchesPattern() throws Exception {
      String fname = "ju_mg_gsearch";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("foo bar\nbaz foo\nqux\nfoo end\n", 0, 1);
      ex.checkpoint();

      // g/foo/s/foo/FOO/ — on lines matching "foo", substitute first foo
      int result = ex.processCommand("g/foo/s/foo/FOO/", 1);
      assertTrue(result >= 0);
      assertEquals("FOO bar", ex.at(1).toString());
      assertEquals("baz FOO", ex.at(2).toString());
      assertEquals("qux", ex.at(3).toString());
      assertEquals("FOO end", ex.at(4).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void inverseGlobalDeleteRemovesNonMatching() throws Exception {
      String fname = "ju_mg_ginv";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("keep\nremove\nkeep\nremove2\n", 0, 1);
      ex.checkpoint();

      // g!/keep/d — delete lines NOT matching "keep"
      // Also deletes trailing empty line from inserttext's final \n
      int result = ex.processCommand("g!/keep/d", 1);
      assertTrue(result >= 0);
      assertEquals("keep", ex.at(1).toString());
      assertEquals("keep", ex.at(2).toString());
      assertEquals(3, ex.finish());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // processCommand move/copy tests
   // ============================================================

   @Test
   void moveCommandReordersLines() throws Exception {
      String fname = "ju_mg_move";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      assertEquals(5, ex.finish());

      // 1m3 — move line 1 to after line 3
      // moveLine(1,3): insert at 3 then delete at 1 → bbb, aaa, ccc
      int result = ex.processCommand("1m3", 1);
      assertTrue(result >= 0);
      assertEquals("bbb", ex.at(1).toString());
      assertEquals("aaa", ex.at(2).toString());
      assertEquals("ccc", ex.at(3).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void copyCommandPreservesOriginal() throws Exception {
      String fname = "ju_mg_copy";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();

      // 2t0 — copy line 2 to before line 1
      int result = ex.processCommand("2t0", 1);
      assertTrue(result >= 0);
      assertEquals("bbb", ex.at(1).toString());
      assertEquals("aaa", ex.at(2).toString());
      assertEquals("bbb", ex.at(3).toString());
      assertEquals("ccc", ex.at(4).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // Regex search on EditContainer (forward / backward)
   // ============================================================

   @Test
   void searchForwardFindsPattern() throws Exception {
      String fname = "ju_mg_sfwd";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("alpha\nbeta\ngamma\ndelta\n", 0, 1);
      ex.checkpoint();

      java.util.regex.Matcher m =
         java.util.regex.Pattern.compile("gamma").matcher("");
      assertTrue(ex.searchForward(m, 0, 3),
         "should find 'gamma' on line 3");
      assertFalse(ex.searchForward(m, 0, 1),
         "should NOT find 'gamma' on line 1");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void searchBackwardFindsPattern() throws Exception {
      String fname = "ju_mg_sbwd";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("xyzabc\n", 0, 1);
      ex.checkpoint();

      java.util.regex.Matcher m =
         java.util.regex.Pattern.compile("xyz").matcher("");
      assertTrue(ex.searchBackward(m, -1, 1),
         "should find 'xyz' on line 1 with full line search");
      assertFalse(ex.searchBackward(m, 1, 1),
         "should NOT find 'xyz' searching only first char");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void regsearchFindsPositionForward() throws Exception {
      String fname = "ju_mg_regsrch";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("one\ntwo\nthree\nfour\n", 0, 1);
      ex.checkpoint();

      java.util.regex.Matcher m =
         java.util.regex.Pattern.compile("three").matcher("");
      Position start = new Position(0, 1, ex.fdes(), null);
      Position pos = ex.regsearch(start, start, false, m, 0,
         EditContainer.SearchType.LINE);
      assertNotNull(pos, "should find 'three'");
      assertEquals(3, pos.y, "should be on line 3");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void regsearchWrapsAround() throws Exception {
      String fname = "ju_mg_wrap";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("target\nmiddle\nend\n", 0, 1);
      ex.checkpoint();

      java.util.regex.Matcher m =
         java.util.regex.Pattern.compile("target").matcher("");
      // Start from line 2 — should wrap and find "target" on line 1
      Position start = new Position(0, 2, ex.fdes(), null);
      Position pos = ex.regsearch(start, start, false, m, 0,
         EditContainer.SearchType.LINE);
      assertNotNull(pos, "should find 'target' after wrapping");
      assertEquals(1, pos.y, "'target' should be on line 1");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void regsearchReturnsNullOnNoMatch() throws Exception {
      String fname = "ju_mg_nomatch";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();

      java.util.regex.Matcher m =
         java.util.regex.Pattern.compile("zzz").matcher("");
      Position start = new Position(0, 1, ex.fdes(), null);
      Position pos = ex.regsearch(start, start, false, m, 0,
         EditContainer.SearchType.LINE);
      assertEquals(null, pos, "should return null when pattern not found");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }
}
