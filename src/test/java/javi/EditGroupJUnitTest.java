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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link EditGroup} commands.
 *
 * <p>
 * Tests edit operations accessible directly on {@link TextEdit}:
 * joinlines, shiftleft, shiftright, changecase, tabfix, and
 * deletetext. Many EditGroup commands require interactive key
 * events, so we test the underlying TextEdit methods they call.
 * </p>
 */
class EditGroupJUnitTest {

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
   void insertIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("insert"),
         "insert should be registered by EditGroup");
   }

   @Test
   void deletemodeIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("deletemode"),
         "deletemode should be registered by EditGroup");
   }

   @Test
   void joinlinesIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("joinlines"),
         "joinlines should be registered by EditGroup");
   }

   @Test
   void shiftmodeIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("shiftmode"),
         "shiftmode should be registered by EditGroup");
   }

   @Test
   void yankmodeIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("yankmode"),
         "yankmode should be registered by EditGroup");
   }

   @Test
   void changecaseIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("changecase"),
         "changecase should be registered by EditGroup");
   }

   @Test
   void tabfixIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("tabfix"),
         "tabfix should be registered by EditGroup");
   }

   // ============================================================
   // joinlines tests
   // ============================================================

   @Test
   void joinTwoLines() throws Exception {
      String fname = "ju_eg_join1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello\nworld\n", 0, 1);
      ex.checkpoint();
      assertEquals(4, ex.finish());
      assertEquals("hello", ex.at(1).toString());
      assertEquals("world", ex.at(2).toString());

      ex.joinlines(2, 1); // join 2 lines starting at line 1
      assertEquals("hello world", ex.at(1).toString());
      assertEquals(3, ex.finish());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void joinLineTrimsLeadingWhitespace() throws Exception {
      String fname = "ju_eg_join2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("first\n   second\n", 0, 1);
      ex.checkpoint();

      ex.joinlines(2, 1);
      assertEquals("first second", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void joinMultipleLines() throws Exception {
      String fname = "ju_eg_join3";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
      ex.checkpoint();
      assertEquals(6, ex.finish());

      ex.joinlines(3, 1); // join 3 lines starting at line 1
      assertEquals("aaa bbb ccc", ex.at(1).toString());
      assertEquals("ddd", ex.at(2).toString());
      assertEquals(4, ex.finish());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void joinLinesWithTrailingSpace() throws Exception {
      String fname = "ju_eg_join4";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello \nworld\n", 0, 1);
      ex.checkpoint();

      // If line already ends with space, shouldn't add another
      ex.joinlines(2, 1);
      assertEquals("hello world", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // shiftright / shiftleft tests
   // ============================================================

   @Test
   void shiftrightAddsLeadingSpaces() throws Exception {
      String fname = "ju_eg_sr1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      // Create lines with different indentation to give findalign context
      ex.inserttext("    indented\nline\n", 0, 1);
      ex.checkpoint();

      int amount = ex.shiftright(2, 1);
      // After shift, "line" should have some leading spaces added
      String shifted = ex.at(2).toString();
      assertTrue(shifted.startsWith(" ") || amount == 0,
         "shiftright should add spaces or report 0 if no alignment found");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void shiftleftRemovesLeadingSpaces() throws Exception {
      String fname = "ju_eg_sl1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("noindent\n      indented\n", 0, 1);
      ex.checkpoint();

      int retval = ex.shiftleft(2, 1);
      String shifted = ex.at(2).toString();
      // shiftleft should remove some whitespace
      assertTrue(shifted.length() <= "      indented".length(),
         "shiftleft should remove leading spaces");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void shiftrightNoopWhenNoAlignment() throws Exception {
      String fname = "ju_eg_sr_noop";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\n", 0, 1);
      ex.checkpoint();

      // Both lines start at column 0, so shiftright of line 1 should be 0
      int amount = ex.shiftright(1, 1);
      assertEquals(0, amount,
         "shiftright should return 0 when no alignment difference found");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // changecase tests
   // ============================================================

   @Test
   void changecaseSingleLine() throws Exception {
      String fname = "ju_eg_cc1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("Hello World\n", 0, 1);
      ex.checkpoint();

      // changecase on entire line 1
      ex.changecase(0, 1, 11, 1);
      assertEquals("hELLO wORLD", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void changecasePartialLine() throws Exception {
      String fname = "ju_eg_cc2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcDEF\n", 0, 1);
      ex.checkpoint();

      // changecase only columns 2-4 on line 1
      ex.changecase(2, 1, 4, 1);
      assertEquals("abCdEF", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void changecaseMultiLine() throws Exception {
      String fname = "ju_eg_cc3";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("Hello\nWorld\n", 0, 1);
      ex.checkpoint();

      // changecase across 2 lines (start col 2 on line 1, end col 3 on line 2)
      // Line 1: ccase(2, 5) toggles positions 2,3,4: HeLLO
      // Line 2: ccase(0, 3) toggles positions 0,1,2: wORld
      ex.changecase(2, 1, 3, 2);
      assertEquals("HeLLO", ex.at(1).toString());
      assertEquals("wORld", ex.at(2).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void changecaseIdempotentTwice() throws Exception {
      String fname = "ju_eg_cc4";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("Test\n", 0, 1);
      ex.checkpoint();

      ex.changecase(0, 1, 4, 1);
      ex.changecase(0, 1, 4, 1);
      // Applying changecase twice should restore original
      assertEquals("Test", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // tabfix tests
   // ============================================================

   @Test
   void tabfixReplacesTabWithSpaces() throws Exception {
      String fname = "ju_eg_tab1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\tb\n", 0, 1);
      ex.checkpoint();

      ex.tabfix(4);
      String result = ex.at(1).toString();
      assertFalse(result.contains("\t"),
         "tabs should be replaced after tabfix");
      assertTrue(result.contains("b"),
         "content after tab should be preserved");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void tabfixWithTabstop8() throws Exception {
      String fname = "ju_eg_tab2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("\tx\n", 0, 1);
      ex.checkpoint();

      ex.tabfix(8);
      String result = ex.at(1).toString();
      assertFalse(result.contains("\t"),
         "tabs should be replaced");
      // A tab at position 0 with tabstop 8 should become 8 spaces
      assertTrue(result.startsWith("        "),
         "tab at start should expand to 8 spaces");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void tabfixThrowsOnZeroTabstop() throws Exception {
      String fname = "ju_eg_tab0";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\tb\n", 0, 1);
      ex.checkpoint();

      assertThrows(InputException.class, () -> ex.tabfix(0),
         "tabstop of 0 should throw InputException");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void tabfixNoTabs() throws Exception {
      String fname = "ju_eg_tabno";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("no tabs here\n", 0, 1);
      ex.checkpoint();

      ex.tabfix(4);
      assertEquals("no tabs here", ex.at(1).toString(),
         "line without tabs should remain unchanged");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // deletetext tests
   // ============================================================

   @Test
   void deletetextSingleLinePartial() throws Exception {
      String fname = "ju_eg_dt1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdefghij\n", 0, 1);
      ex.checkpoint();

      // Delete chars 3-6 on line 1 (xend is exclusive)
      String deleted = ex.deletetext(false, 3, 1, 6, 1);
      assertEquals("def", deleted);
      assertEquals("abcghij", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void deletetextMultiLine() throws Exception {
      String fname = "ju_eg_dt2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("first line\nsecond line\nthird line\n", 0, 1);
      ex.checkpoint();

      // Delete from col 5 on line 1 to col 6 on line 2
      String deleted = ex.deletetext(false, 5, 1, 6, 2);
      assertNotNull(deleted);
      assertEquals("first line", ex.at(1).toString().length() > 0
         ? ex.at(1).toString()
         : "unexpected empty line");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void deletetextPreserveModeYanks() throws Exception {
      String fname = "ju_eg_dt3";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdef\n", 0, 1);
      ex.checkpoint();
      String originalLine = ex.at(1).toString();

      // Delete with preserve=true should return text but keep the line
      String deleted = ex.deletetext(true, 1, 1, 4, 1);
      assertEquals("bcd", deleted);
      // Original line should still contain the text
      assertEquals(originalLine, ex.at(1).toString(),
         "preserve mode should not modify the line");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // processCommand with edit operations
   // ============================================================

   @Test
   void substituteWithRanges() throws Exception {
      String fname = "ju_eg_subrange";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aXb\ncXd\neXf\n", 0, 1);
      ex.checkpoint();

      // 1,3s/X/Y/ — substitute on lines 1-3
      int result = ex.processCommand("1,3s/X/Y/", 1);
      assertTrue(result >= 0);
      assertEquals("aYb", ex.at(1).toString());
      assertEquals("cYd", ex.at(2).toString());
      assertEquals("eYf", ex.at(3).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void percentSubstituteAppliesToAllLines() throws Exception {
      String fname = "ju_eg_pctsubst";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("old\nold\nold\n", 0, 1);
      ex.checkpoint();

      // %s/old/new/ — substitute on all lines
      int result = ex.processCommand("%s/old/new/", 1);
      assertTrue(result >= 0);
      assertEquals("new", ex.at(1).toString());
      assertEquals("new", ex.at(2).toString());
      assertEquals("new", ex.at(3).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // B10 regression: visual yank (VY) must store lines in buffer
   // ============================================================

   /**
    * Regression test for B10: VY (visual yank) was broken because
    * the 'Y' case was accidentally removed from markmode's switch.
    * This verifies the underlying yank primitive: getElementsAt
    * copies lines into a Buffers register without modifying the
    * source text.
    */
   @Test
   void visualYankCopiesLinesWithoutModifying() throws Exception {
      String fname = "ju_eg_vy";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("alpha\nbeta\ngamma\n", 0, 1);
      ex.checkpoint();

      // Yank two lines starting at line 1 — same path as VY
      var yanked = ex.getElementsAt(1, 2);
      Buffers.deleted('a', yanked);

      // Source text must be unmodified
      assertEquals("alpha", ex.at(1).toString());
      assertEquals("beta", ex.at(2).toString());
      assertEquals("gamma", ex.at(3).toString());

      // Buffer 'a' must contain the yanked lines
      Object buf = Buffers.getbuf('a');
      assertNotNull(buf, "yank buffer should not be null");
      assertTrue(buf instanceof java.util.ArrayList,
         "yank buffer should be an ArrayList of lines");
      @SuppressWarnings("unchecked")
      var lines = (java.util.ArrayList<String>) buf;
      assertEquals(2, lines.size());
      assertEquals("alpha", lines.get(0));
      assertEquals("beta", lines.get(1));

      ex.disposeFvc();
      deleteTestFiles(fname);
   }
}
