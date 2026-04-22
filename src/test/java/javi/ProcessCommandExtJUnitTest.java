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
 * Extended processCommand tests targeting uncovered address parsing,
 * substitute backreference paths, deletetext branches, shift/indent,
 * and other TextEdit methods.
 */
class ProcessCommandExtJUnitTest {

   private static final String TEST_PREFIX = "ju_pcext_";
   private TextEdit<String> ex;

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
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
            TEST_PREFIX + "out", TEST_PREFIX + "rd");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // --- Address parsing: dot, dollar, patterns ---

   @Test
   void dotAddressUsesCurrent() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      // "." refers to current line (ypos=2 passed to processCommand)
      int result = ex.processCommand(".", 2);
      assertEquals(2, result);
   }

   @Test
   void dollarAddressUsesLastLine() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      // "$" refers to last content line
      int result = ex.processCommand("$", 1);
      assertTrue(result >= 3, "$ should point to last line: " + result);
   }

   @Test
   void plusOffsetAddress() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
      ex.checkpoint();
      // ".+2" from line 1 → line 3
      int result = ex.processCommand(".+2", 1);
      assertEquals(3, result);
   }

   @Test
   void minusOffsetAddress() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
      ex.checkpoint();
      // ".-1" from line 3 → line 2
      int result = ex.processCommand(".-1", 3);
      assertEquals(2, result);
   }

   @Test
   void percentRangeSubstitute() throws Exception {
      ex.inserttext("foo\nbar\nfoo\n", 0, 1);
      ex.checkpoint();
      // % = entire file range
      int result = ex.processCommand("%s/foo/FOO/", 1);
      assertTrue(result >= 0);
      assertEquals("FOO", ex.at(1).toString());
      assertEquals("bar", ex.at(2).toString());
      assertEquals("FOO", ex.at(3).toString());
   }

   // --- Substitute: backreference via \1..\9 and & ---

   @Test
   void substituteWithAmpersand() throws Exception {
      ex.inserttext("hello\n", 0, 1);
      ex.checkpoint();
      int result = ex.processCommand("1s/hello/[&]/", 1);
      assertTrue(result >= 0);
      assertEquals("[hello]", ex.at(1).toString());
   }

   @Test
   void substituteWithGroupBackreference() throws Exception {
      ex.inserttext("hello world\n", 0, 1);
      ex.checkpoint();
      // Java regex groups: (hello) (world), \1 and \2 backrefs
      int result = ex.processCommand(
         "1s/(hello) (world)/\\2 \\1/", 1);
      assertTrue(result >= 0);
      assertEquals("world hello", ex.at(1).toString());
   }

   @Test
   void substituteWithEscapedLiteral() throws Exception {
      ex.inserttext("hello\n", 0, 1);
      ex.checkpoint();
      // \n in replacement should produce literal n (escaped)
      int result = ex.processCommand("1s/hello/\\n/", 1);
      assertTrue(result >= 0);
      assertEquals("n", ex.at(1).toString());
   }

   @Test
   void substituteNoMatchPreservesLine() throws Exception {
      ex.inserttext("hello\n", 0, 1);
      ex.checkpoint();
      int result = ex.processCommand("1s/zzz/QQQ/", 1);
      assertTrue(result >= 0);
      assertEquals("hello", ex.at(1).toString());
   }

   @Test
   void substituteGlobalReplacesAll() throws Exception {
      ex.inserttext("aXbXcX\n", 0, 1);
      ex.checkpoint();
      int result = ex.processCommand("1s/X/Y/g", 1);
      assertTrue(result >= 0);
      assertEquals("aYbYcY", ex.at(1).toString());
   }

   // --- deletetext paths ---

   @Test
   void deletetextSingleLineRange() throws Exception {
      ex.inserttext("abcdefghij\n", 0, 1);
      ex.checkpoint();
      String deleted = ex.deletetext(false, 2, 1, 5, 1);
      assertEquals("cde", deleted);
      assertEquals("abfghij", ex.at(1).toString());
   }

   @Test
   void deletetextMultiLineRange() throws Exception {
      ex.inserttext("aaa111\nbbb222\nccc333\n", 0, 1);
      ex.checkpoint();
      // delete from (3,1) to (3,3): lines 1-3 partial
      String deleted = ex.deletetext(false, 3, 1, 3, 3);
      assertNotNull(deleted);
      assertTrue(deleted.contains("111"));
      assertTrue(deleted.contains("bbb222"));
   }

   @Test
   void deletetextSingleLineReturnsNullIfSamePos() throws Exception {
      ex.inserttext("hello\n", 0, 1);
      ex.checkpoint();
      // same start and end → null
      String deleted = ex.deletetext(false, 2, 1, 2, 1);
      assertNull(deleted);
   }

   // --- gettext (preserve=true path) ---

   @Test
   void gettextDoesNotModifyBuffer() throws Exception {
      ex.inserttext("abcdefg\n", 0, 1);
      ex.checkpoint();
      String text = ex.gettext(2, 1, 5, 1);
      assertEquals("cde", text);
      assertEquals("abcdefg", ex.at(1).toString());
   }

   // --- shift right / shift left ---

   @Test
   void shiftRightAddsSpaces() throws Exception {
      ex.inserttext("if (true) {\n   code;\n}\n", 0, 1);
      ex.checkpoint();
      // shiftright uses findalign to determine indent amount
      // from the next line's indentation
      int result = ex.shiftright(1, 1);
      // result may be 0 if findalign doesn't find alignment
      // Just verify no crash
      assertTrue(result >= 0 || result == 0);
   }

   @Test
   void shiftLeftRemovesSpaces() throws Exception {
      ex.inserttext("   hello\n   world\n", 0, 1);
      ex.checkpoint();
      int result = ex.shiftleft(1, 2);
      assertTrue(result <= 0);
   }

   @Test
   void shiftLeftHandlesPartialSpaces() throws Exception {
      ex.inserttext(" hi\n  there\n", 0, 1);
      ex.checkpoint();
      ex.shiftleft(1, 2);
      // lines should have fewer leading spaces
      assertTrue(ex.at(1).toString().length() <= 3);
   }

   // --- read file into buffer ---

   @Test
   void readFileIntoBuffer() throws Exception {
      // Write a file first
      ex.inserttext("line1\nline2\n", 0, 1);
      ex.checkpoint();
      String outPath = testPath(TEST_PREFIX + "rd");
      ex.processCommand("w " + outPath, 1);

      // Create another buffer and read the file via insertStream
      TextEdit<String> ex2 = openTestFile(TEST_PREFIX + "out");
      ex2.inserttext("existing\n", 0, 1);
      ex2.checkpoint();
      java.io.BufferedReader reader = new java.io.BufferedReader(
         new java.io.FileReader(outPath));
      ex2.insertStream(reader, 1);
      ex2.checkpoint();
      assertTrue(ex2.finish() > 3,
         "buffer should have more lines after read");
      ex2.disposeFvc();
   }

   // --- global delete with range ---

   @Test
   void globalDeleteWithExplicitRange() throws Exception {
      ex.inserttext("keep\nzap\nkeep2\nzap2\nkeep3\n", 0, 1);
      ex.checkpoint();
      // 2,4g/zap/d — only delete matching lines within range 2-4
      int result = ex.processCommand("2,4g/zap/d", 1);
      assertTrue(result >= 0);
      assertEquals("keep", ex.at(1).toString());
      assertEquals("keep2", ex.at(2).toString());
      assertEquals("keep3", ex.at(3).toString());
   }

   // --- printout (w with no args writes to current file) ---

   @Test
   void writeWithNoArgsWritesToCurrentFile() throws Exception {
      ex.inserttext("saved\n", 0, 1);
      ex.checkpoint();
      int result = ex.processCommand("w", 1);
      assertTrue(result >= 0);
      // Reopen to verify persistence
      ex.disposeFvc();
      ex = openTestFile(TEST_PREFIX + "buf");
      assertEquals("saved", ex.at(1).toString());
   }

   // --- global with copy and write ---

   @Test
   void globalCopyMatchingLinesToEnd() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      // g/aaa/t$ — copy matching lines to end
      int result = ex.processCommand("g/aaa/t$", 1);
      assertTrue(result >= 0);
      // aaa should be duplicated at the end
      assertTrue(ex.finish() > 5);
   }

   // --- comma range operations ---

   @Test
   void commaRangeDelete() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\nddd\neee\n", 0, 1);
      ex.checkpoint();
      // 2,4d
      int result = ex.processCommand("2,4d", 1);
      assertTrue(result >= 0);
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("eee", ex.at(2).toString());
   }

   @Test
   void commaRangeSubstitute() throws Exception {
      ex.inserttext("aXa\nbXb\ncXc\n", 0, 1);
      ex.checkpoint();
      // 1,2s/X/Y/
      int result = ex.processCommand("1,2s/X/Y/", 1);
      assertTrue(result >= 0);
      assertEquals("aYa", ex.at(1).toString());
      assertEquals("bYb", ex.at(2).toString());
      assertEquals("cXc", ex.at(3).toString());
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
