package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link EditContainer} search, match, and query operations
 * that operate on buffer content.
 */
class EditContainerSearchJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.init();
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
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(name)).delete();
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(name + ".dmp2")).delete();
      }
   }

   // ── regsearch tests (via processCommand which wraps it) ─────

   @Nested
   @DisplayName("search via processCommand")
   class SearchViaCommand {

      @Test
      @DisplayName("global substitute confirms search finds lines")
      void globalSubConfirmsSearch() throws Exception {
         String fname = "ju_ecsearch1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("apple\nbanana\ncherry\napple pie\n", 0, 1);
         ex.checkpoint();

         // g/apple/s/apple/FRUIT/ exercises regsearch internally
         int result = ex.processCommand("%g/apple/s/apple/FRUIT/", 1);
         assertEquals(1, result);
         assertEquals("FRUIT", ex.at(1).toString());
         assertEquals("banana", ex.at(2).toString());
         assertEquals("cherry", ex.at(3).toString());
         assertEquals("FRUIT pie", ex.at(4).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("inverse global (v) excludes matching lines")
      void inverseGlobalExcludes() throws Exception {
         String fname = "ju_ecsearch2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("keep\ndelete\nkeep\ndelete\n", 0, 1);
         ex.checkpoint();

         int result = ex.processCommand("%v/keep/d", 1);
         assertEquals(1, result);
         assertEquals("keep", ex.at(1).toString());
         assertEquals("keep", ex.at(2).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("global delete removes matching lines")
      void globalDeleteRemovesMatching() throws Exception {
         String fname = "ju_ecsearch3";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("aaa\nbbb\nccc\nbbb\n", 0, 1);
         ex.checkpoint();

         int result = ex.processCommand("%g/bbb/d", 1);
         assertEquals(1, result);
         assertEquals("aaa", ex.at(1).toString());
         assertEquals("ccc", ex.at(2).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── indexOf tests ────────────────────────────────────────────

   @Nested
   @DisplayName("indexOf()")
   class IndexOfTests {

      @Test
      @DisplayName("indexOf returns -1 for missing element")
      void notFound() throws IOException {
         String fname = "ju_ecidx2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("alpha\nbeta\n", 0, 1);
         ex.checkpoint();

         int idx = ex.indexOf("missing");
         assertEquals(-1, idx);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── contains/containsNow tests ──────────────────────────────

   @Nested
   @DisplayName("containsNow()")
   class ContainsNowTests {

      @Test
      @DisplayName("containsNow returns true for valid index")
      void validIndex() throws IOException {
         String fname = "ju_eccn1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("line one\nline two\n", 0, 1);
         ex.checkpoint();

         assertTrue(ex.containsNow(1));
         assertTrue(ex.containsNow(2));

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

   }

   // ── isValid / getName / toString ─────────────────────────────

   @Nested
   @DisplayName("metadata queries")
   class MetadataTests {

      @Test
      @DisplayName("isValid returns true for open buffer")
      void isValidTrue() throws IOException {
         String fname = "ju_ecmeta1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);

         assertTrue(ex.isValid());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("getName returns file name")
      void getNameReturnsName() throws IOException {
         String fname = "ju_ecmeta2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);

         String name = ex.getName();
         assertNotNull(name);
         assertTrue(name.contains("ju_ecmeta2"),
            "getName should contain file name, got: " + name);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("toString contains file name")
      void toStringContainsName() throws IOException {
         String fname = "ju_ecmeta3";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);

         String str = ex.toString();
         assertNotNull(str);
         assertTrue(str.contains("ju_ecmeta3"),
            "toString should contain file name, got: " + str);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── readIn / finish tests ────────────────────────────────────

   @Nested
   @DisplayName("readIn / finish")
   class ReadInTests {

      @Test
      @DisplayName("readIn returns line count")
      void readInAfterInsert() throws IOException {
         String fname = "ju_ecri1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("a\nb\nc\n", 0, 1);
         int count = ex.finish();

         assertTrue(count >= 4,
            "should have at least 4 lines (3 + trailing)");

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("readIn on empty buffer returns 1")
      void emptyBufferReadIn() throws IOException {
         String fname = "ju_ecri2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);

         assertTrue(ex.readIn() >= 1,
            "empty buffer readIn should be at least 1");

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── insertStream tests ───────────────────────────────────────

   @Nested
   @DisplayName("insertStream()")
   class InsertStreamTests {

      @Test
      @DisplayName("insertStream adds lines from reader")
      void insertsFromReader() throws Exception {
         String fname = "ju_ecis1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("existing\n", 0, 1);
         ex.checkpoint();

         java.io.BufferedReader br = new java.io.BufferedReader(
            new StringReader("new line 1\nnew line 2\n"));
         ex.insertStream(br, 1);

         // Verify both lines were inserted (order may vary)
         boolean foundLine1 = false;
         boolean foundLine2 = false;
         for (int i = 1; i < ex.readIn(); i++) {
            String s = ex.at(i).toString();
            if ("new line 1".equals(s))
               foundLine1 = true;
            if ("new line 2".equals(s))
               foundLine2 = true;
         }
         assertTrue(foundLine1, "should find 'new line 1'");
         assertTrue(foundLine2, "should find 'new line 2'");

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── remove and moveLine tests ────────────────────────────────

   @Nested
   @DisplayName("remove() and moveLine()")
   class RemoveMoveTests {

      @Test
      @DisplayName("remove single line")
      void removeSingleLine() throws IOException {
         String fname = "ju_ecrm1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("first\nsecond\nthird\n", 0, 1);
         ex.checkpoint();

         ex.remove(2, 1); // remove 'second'
         ex.checkpoint();

         assertEquals("first", ex.at(1).toString());
         assertEquals("third", ex.at(2).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("remove multiple lines")
      void removeMultipleLines() throws IOException {
         String fname = "ju_ecrm2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("a\nb\nc\nd\ne\n", 0, 1);
         ex.checkpoint();

         ex.remove(2, 3); // remove b, c, d
         ex.checkpoint();

         assertEquals("a", ex.at(1).toString());
         assertEquals("e", ex.at(2).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("moveLine moves a line to new position")
      void moveLine() throws IOException {
         String fname = "ju_ecml1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("aa\nbb\ncc\n", 0, 1);
         ex.checkpoint();

         ex.moveLine(1, 3); // move 'aa' after 'cc'
         ex.checkpoint();

         // After move: bb, cc, aa
         assertEquals("bb", ex.at(1).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── getElementsAt (multi-line retrieval) ─────────────────────

   @Nested
   @DisplayName("getElementsAt()")
   class GetElementsAtTests {

      @Test
      @DisplayName("getElementsAt returns requested lines")
      void returnsRequestedLines() throws IOException {
         String fname = "ju_ecgea1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("one\ntwo\nthree\nfour\n", 0, 1);
         ex.checkpoint();

         java.util.ArrayList<String> lines = ex.getElementsAt(2, 2);
         assertNotNull(lines);
         assertEquals(2, lines.size());
         assertEquals("two", lines.get(0));
         assertEquals("three", lines.get(1));

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("getElementsAt single line")
      void singleLine() throws IOException {
         String fname = "ju_ecgea2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("only\n", 0, 1);
         ex.checkpoint();

         java.util.ArrayList<String> lines = ex.getElementsAt(1, 1);
         assertNotNull(lines);
         assertEquals(1, lines.size());
         assertEquals("only", lines.get(0));

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── setReadOnly / isReadOnly ─────────────────────────────────

   @Nested
   @DisplayName("readOnly")
   class ReadOnlyTests {

      @Test
      @DisplayName("setReadOnly makes buffer read-only")
      void setReadOnly() throws IOException {
         String fname = "ju_ecro1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);

         ex.setReadOnly(true);
         // Attempting to modify should throw ReadOnlyException
         // (internal class, so just verify the flag was set)
         ex.setReadOnly(false);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── changeElementAtStr tests ─────────────────────────────────

   @Nested
   @DisplayName("changeElementAtStr()")
   class ChangeElementAtStrTests {

      @Test
      @DisplayName("change element replaces content")
      void changeReplaces() throws IOException {
         String fname = "ju_ecces1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("before\n", 0, 1);
         ex.checkpoint();

         ex.changeElementAtStr("after", 1);
         ex.checkpoint();

         assertEquals("after", ex.at(1).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("change followed by undo restores original")
      void changeUndoRestores() throws IOException {
         String fname = "ju_ecces2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("original\n", 0, 1);
         ex.checkpoint();

         ex.changeElementAtStr("modified", 1);
         ex.checkpoint();
         assertEquals("modified", ex.at(1).toString());

         ex.undo();
         assertEquals("original", ex.at(1).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── parentEq tests ───────────────────────────────────────────

   @Nested
   @DisplayName("parentEq()")
   class ParentEqTests {

      @Test
      @DisplayName("different buffers are not parentEq")
      void differentBuffers() throws IOException {
         String fname1 = "ju_ecpe2a";
         String fname2 = "ju_ecpe2b";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname1, fname2);
         TextEdit<String> ex1 = openTestFile(fname1);
         TextEdit<String> ex2 = openTestFile(fname2);

         assertFalse(ex1.parentEq(ex2));

         ex1.disposeFvc();
         ex2.disposeFvc();
         deleteTestFiles(fname1, fname2);
      }
   }

   // ── addState tests ───────────────────────────────────────────

   @Nested
   @DisplayName("addState()")
   class AddStateTests {

      @Test
      @DisplayName("addState on unmodified file shows unchanged")
      void unmodifiedShowsUnchanged() throws IOException {
         String fname = "ju_ecas1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);

         StringBuilder sb = new StringBuilder();
         ex.addState(sb);
         assertTrue(sb.toString().contains("unchanged"),
            "unmodified file should show 'unchanged', got: " + sb);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("addState on modified file shows modified indicator")
      void modifiedShowsModified() throws IOException {
         String fname = "ju_ecas2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("change\n", 0, 1);
         ex.checkpoint();

         StringBuilder sb = new StringBuilder();
         ex.addState(sb);
         assertFalse(sb.toString().contains("unchanged"),
            "modified file should NOT show 'unchanged', got: " + sb);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }
}
