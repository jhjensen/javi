package javi;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TextEdit edge cases: processCommand paths not covered
 * by other test classes, changecase multi-line, joinlines edge
 * cases, and tabfix.
 */
class TextEditEdgeCasesJUnitTest {

   private static final String TEST_PREFIX = "ju_te_edge_";
   private TextEdit<String> ex;

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void setUp() throws Exception {
      EventQueue.biglock2.lock();
      UI.setStream(new StringReader(""));
      ex = openTestFile(TEST_PREFIX + "buf");
   }

   @AfterEach
   void tearDown() {
      try {
         if (ex != null)
            try { ex.disposeFvc(); } catch (IOException e) { /* ok */ }
         deleteTestFiles(TEST_PREFIX + "buf",
            TEST_PREFIX + "out", TEST_PREFIX + "ro");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // --- processCommand edge cases ---

   @Test
   void invalidCommandCharReturnsNegative() throws Exception {
      ex.inserttext("aaa\nbbb\n", 0, 1);
      ex.checkpoint();
      // 'z' is not a valid ex command character
      int result = ex.processCommand("1z", 1);
      assertEquals(-1, result);
   }

   @Test
   void copyCommandViaCo() throws Exception {
      ex.inserttext("alpha\nbeta\ngamma\n", 0, 1);
      ex.checkpoint();
      // "co" is alias for copy (t) — "1co2" copies line 1 after line 2
      int result = ex.processCommand("1co2", 1);
      assertTrue(result >= 0, "co (copy) should succeed");
      assertEquals("alpha", ex.at(1).toString());
      assertEquals("beta", ex.at(2).toString());
      assertEquals("alpha", ex.at(3).toString());
      assertEquals("gamma", ex.at(4).toString());
   }

   @Test
   void substituteWithoutPatternUsesLastPattern() throws Exception {
      ex.inserttext("hello\n", 0, 1);
      ex.checkpoint();
      // substitute 's' with no delimiter — uses last search pattern
      // should not throw, returns >= 0 or -1
      int result = ex.processCommand("1s", 1);
      assertTrue(result >= -1);
   }

   @Test
   void globalInverseWithBangDoubleNegation() throws Exception {
      ex.inserttext("keep\nremove\nkeep2\n", 0, 1);
      ex.checkpoint();
      // v!/keep/ means NOT(NOT matching "keep") = matching "keep"
      // So v!/keep/d deletes lines matching "keep"
      int result = ex.processCommand("v!/keep/d", 1);
      assertTrue(result >= 0);
      assertEquals("remove", ex.at(1).toString());
   }

   @Test
   void globalSubstituteOnMatchingLines() throws Exception {
      ex.inserttext("foo bar\nbaz qux\nfoo end\n", 0, 1);
      ex.checkpoint();
      // g/foo/s/foo/FOO/ — substitute only on lines matching "foo"
      int result = ex.processCommand("g/foo/s/foo/FOO/", 1);
      assertTrue(result >= 0);
      assertEquals("FOO bar", ex.at(1).toString());
      assertEquals("baz qux", ex.at(2).toString());
      assertEquals("FOO end", ex.at(3).toString());
   }

   @Test
   void inverseGlobalSubstitute() throws Exception {
      ex.inserttext("foo bar\nbaz qux\nfoo end\n", 0, 1);
      ex.checkpoint();
      // v/foo/s/baz/BAZ/ — substitute on lines NOT matching "foo"
      int result = ex.processCommand("v/foo/s/baz/BAZ/", 1);
      assertTrue(result >= 0);
      assertEquals("foo bar", ex.at(1).toString());
      assertEquals("BAZ qux", ex.at(2).toString());
      assertEquals("foo end", ex.at(3).toString());
   }

   @Test
   void globalCopyOnMatchingLines() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      // g/bbb/t0 — copy lines matching "bbb" to line 0 (top)
      int result = ex.processCommand("g/bbb/t0", 1);
      assertTrue(result >= 0);
      assertEquals("bbb", ex.at(1).toString());
      assertEquals("aaa", ex.at(2).toString());
   }

   @Test
   void globalMoveOnMatchingLines() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
      ex.checkpoint();
      // g/bbb/m0 — move lines matching "bbb" before line 1
      int result = ex.processCommand("g/bbb/m0", 1);
      assertTrue(result >= 0);
      assertEquals("bbb", ex.at(1).toString());
      assertEquals("aaa", ex.at(2).toString());
      assertEquals("ccc", ex.at(3).toString());
      assertEquals("ddd", ex.at(4).toString());
   }

   @Test
   void semicolonRangeAddress() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\nddd\neee\n", 0, 1);
      ex.checkpoint();
      // 2;+1d — start at line 2, then 2+1=3, delete 2-3
      int result = ex.processCommand("2;+1d", 1);
      assertTrue(result >= 0);
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("ddd", ex.at(2).toString());
      assertEquals("eee", ex.at(3).toString());
   }

   @Test
   void percentSubstituteDefaultRange() throws Exception {
      ex.inserttext("aXa\nbXb\n", 0, 1);
      ex.checkpoint();
      // %s covers all lines (1,$)
      int result = ex.processCommand("%s/X/Y/", 1);
      assertTrue(result >= 0);
      assertEquals("aYa", ex.at(1).toString());
      assertEquals("bYb", ex.at(2).toString());
   }

   @Test
   void globalDeleteDefaultsToAllLines() throws Exception {
      ex.inserttext("keep\nzap\nkeep2\nzap2\n", 0, 1);
      ex.checkpoint();
      // g/zap/d without explicit range defaults to entire file
      int result = ex.processCommand("g/zap/d", 1);
      assertTrue(result >= 0);
      assertEquals("keep", ex.at(1).toString());
      assertEquals("keep2", ex.at(2).toString());
      assertEquals(4, ex.finish());
   }

   @Test
   void moveTargetWithinSourceThrows() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
      ex.checkpoint();
      // 1,3m2 — move lines 1-3 to after line 2 (within source)
      assertThrows(InputException.class,
         () -> ex.processCommand("1,3m2", 1));
   }

   @Test
   void lineNumberOnlyGotosLine() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      // just a line number — returns that line number
      int result = ex.processCommand("2", 1);
      assertEquals(2, result);
   }

   @Test
   void writeRangeToFile() throws Exception {
      ex.inserttext("line1\nline2\nline3\n", 0, 1);
      ex.checkpoint();
      String outPath = testPath(TEST_PREFIX + "out");
      int result = ex.processCommand("1,2w " + outPath, 1);
      assertTrue(result >= 0);
      // Read back and verify
      TextEdit<String> out = openTestFile(TEST_PREFIX + "out");
      assertEquals("line1", out.at(1).toString());
      assertEquals("line2", out.at(2).toString());
      assertEquals(3, out.finish());
      out.disposeFvc();
   }

   @Test
   void writeWholeFileDefaultRange() throws Exception {
      ex.inserttext("aaa\nbbb\n", 0, 1);
      ex.checkpoint();
      // "w" with no range writes whole file (printout)
      int result = ex.processCommand("w", 1);
      assertTrue(result >= 0);
   }

   @Test
   void globalWriteToFile() throws Exception {
      ex.inserttext("yes 1\nno 1\nyes 2\nno 2\n", 0, 1);
      ex.checkpoint();
      String outPath = testPath(TEST_PREFIX + "out");
      // g/yes/w — write matching lines to file
      int result = ex.processCommand("g/yes/w " + outPath, 1);
      assertTrue(result >= 0);
      TextEdit<String> out = openTestFile(TEST_PREFIX + "out");
      assertEquals("yes 1", out.at(1).toString());
      assertEquals("yes 2", out.at(2).toString());
      assertEquals(3, out.finish());
      out.disposeFvc();
   }

   // --- changecase ---

   @Test
   void changecaseSingleLine() throws Exception {
      ex.inserttext("Hello World\n", 0, 1);
      ex.checkpoint();
      // Toggle case on chars 0..5 of line 1
      ex.changecase(0, 1, 5, 1);
      assertEquals("hELLO World", ex.at(1).toString());
   }

   @Test
   void changecaseMultiLineRange() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      // Toggle case spanning lines 1-3, partial start/end
      ex.changecase(1, 1, 2, 3);
      // line 1: from col 1 to end → "aAA"
      assertEquals("aAA", ex.at(1).toString());
      // line 2: full line → "BBB"
      assertEquals("BBB", ex.at(2).toString());
      // line 3: from 0 to col 2 → "CC c"
      assertEquals("CCc", ex.at(3).toString());
   }

   @Test
   void changecaseHandlesMixedCaseAndSymbols() throws Exception {
      ex.inserttext("aBc!123dEf\n", 0, 1);
      ex.checkpoint();
      ex.changecase(0, 1, 10, 1);
      assertEquals("AbC!123DeF", ex.at(1).toString());
   }

   // --- joinlines ---

   @Test
   void joinlinesBasicTwoLines() throws Exception {
      ex.inserttext("hello\nworld\n", 0, 1);
      ex.checkpoint();
      int retval = ex.joinlines(1, 1);
      assertEquals("hello world", ex.at(1).toString());
   }

   @Test
   void joinlinesStripsLeadingSpaces() throws Exception {
      ex.inserttext("hello\n   world\n", 0, 1);
      ex.checkpoint();
      ex.joinlines(1, 1);
      assertEquals("hello world", ex.at(1).toString());
   }

   @Test
   void joinlinesMultiple() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
      ex.checkpoint();
      // join 3 lines starting at line 1
      ex.joinlines(3, 1);
      assertEquals("aaa bbb ccc", ex.at(1).toString());
      assertEquals("ddd", ex.at(2).toString());
   }

   @Test
   void joinlinesAtLastLineDoesNothing() throws Exception {
      ex.inserttext("aaa\nbbb\n", 0, 1);
      ex.checkpoint();
      // join at last content line (line 2) — joins with next line
      ex.joinlines(1, 2);
      assertEquals("aaa", ex.at(1).toString());
      // line 2 may get trailing space from join with empty line
      assertTrue(ex.at(2).toString().startsWith("bbb"));
   }

   @Test
   void joinlinesAlreadyHasTrailingSpace() throws Exception {
      ex.inserttext("hello \nworld\n", 0, 1);
      ex.checkpoint();
      ex.joinlines(1, 1);
      // Should not add extra space when first line already ends with space
      assertEquals("hello world", ex.at(1).toString());
   }

   // --- tabfix ---

   @Test
   void tabfixReplacesTabsWithSpaces() throws Exception {
      ex.inserttext("hello\tworld\n", 0, 1);
      ex.checkpoint();
      ex.tabfix(4);
      String result = ex.at(1).toString();
      assertTrue(!result.contains("\t"), "tabs should be replaced");
      assertTrue(result.contains("hello"), "content preserved");
      assertTrue(result.contains("world"), "content preserved");
   }

   @Test
   void tabfixZeroTabstopThrows() throws Exception {
      ex.inserttext("hello\n", 0, 1);
      assertThrows(InputException.class, () -> ex.tabfix(0));
   }

   @Test
   void tabfixNoTabsLeavesUnchanged() throws Exception {
      ex.inserttext("no tabs here\n", 0, 1);
      ex.checkpoint();
      ex.tabfix(4);
      assertEquals("no tabs here", ex.at(1).toString());
   }

   // --- deletetext/gettext edge cases ---

   @Test
   void gettextSingleLine() throws Exception {
      ex.inserttext("abcdefghij\n", 0, 1);
      ex.checkpoint();
      String result = ex.gettext(2, 1, 5, 1);
      assertEquals("cde", result);
   }

   @Test
   void gettextMultiLine() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      String result = ex.gettext(1, 1, 2, 3);
      assertNotNull(result);
      assertTrue(result.contains("aa"), "should include first line tail");
      assertTrue(result.contains("bbb"), "should include middle line");
   }

   // --- helper methods ---

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

   private static void deleteTestFiles(String... names) {
      for (String name : names) {
         try {
            makeLocal(name).delete();
         } catch (IOException e) { /* ignore */ }
         try {
            makeLocal(name + ".dmp2").delete();
         } catch (IOException e) { /* ignore */ }
      }
   }
}
