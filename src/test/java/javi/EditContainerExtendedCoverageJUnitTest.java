package javi;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage for {@link EditContainer} — iterator,
 * searchForward/searchBackward, toString, indexOf, donereading,
 * readIn, contains vs containsNow, registerListener,
 * addState, and ReadOnlyException.
 */
class EditContainerExtendedCoverageJUnitTest {

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
      return te;
   }

   private static void deleteTestFiles(String... names)
         throws IOException {
      for (String name : names) {
         makeLocal(name).delete();
         makeLocal(name + ".dmp2").delete();
      }
   }

   // ================================================================
   // Iterator
   // ================================================================

   @Nested
   @DisplayName("Iterator")
   class IteratorTests {

      @Test
      @DisplayName("iterator covers all content lines")
      void iteratorCoversAllLines() throws IOException {
         String fname = "ju_ecx_iter";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> te = openTestFile(fname);
         te.inserttext("line1\nline2\nline3\n", 0, 1);
         te.checkpoint();

         int count = 0;
         for (String s : te)
            count++;
         assertEquals(te.finish() - 1, count,
            "iterator should yield finish()-1 elements");

         te.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("iterator over empty file yields no elements")
      void iteratorEmptyFile() throws IOException {
         String fname = "ju_ecx_iterempty";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> te = openTestFile(fname);
         int count = 0;
         for (String s : te)
            count++;
         // empty file has 1 empty line after sentinel
         assertTrue(count <= 1,
            "empty file iterator should yield at most 1 element");

         te.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ================================================================
   // Search
   // ================================================================

   @Nested
   @DisplayName("searchForward / searchBackward")
   class SearchTests {

      @Test
      @DisplayName("searchForward finds pattern in line")
      void searchForwardFinds() throws IOException {
         String fname = "ju_ecx_srchf";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> te = openTestFile(fname);
         te.inserttext("hello world\nfoo bar\n", 0, 1);
         te.checkpoint();

         Matcher m = Pattern.compile("world").matcher("");
         assertTrue(te.searchForward(m, 0, 1),
            "searchForward should find 'world' in line 1");

         te.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("searchForward with offset past match returns false")
      void searchForwardOffsetPast() throws IOException {
         String fname = "ju_ecx_srchf2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> te = openTestFile(fname);
         te.inserttext("hello\n", 0, 1);
         te.checkpoint();

         Matcher m = Pattern.compile("hello").matcher("");
         assertFalse(te.searchForward(m, 5, 1),
            "searchForward past end of match should fail");

         te.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("searchBackward finds last occurrence")
      void searchBackwardFinds() throws IOException {
         String fname = "ju_ecx_srchb";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> te = openTestFile(fname);
         te.inserttext("aXbXcX\n", 0, 1);
         te.checkpoint();

         Matcher m = Pattern.compile("X").matcher("");
         assertTrue(te.searchBackward(m, -1, 1),
            "searchBackward should find 'X' in line");

         te.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("searchBackward with charoff limits scope")
      void searchBackwardCharoff() throws IOException {
         String fname = "ju_ecx_srchb2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> te = openTestFile(fname);
         te.inserttext("abcdef\n", 0, 1);
         te.checkpoint();

         Matcher m = Pattern.compile("e").matcher("");
         // charoff=3 means search only in "abc"
         assertFalse(te.searchBackward(m, 3, 1),
            "searchBackward limited to first 3 chars should not find 'e'");

         te.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("searchForward returns false for no match")
      void searchForwardNoMatch() throws IOException {
         String fname = "ju_ecx_srchno";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> te = openTestFile(fname);
         te.inserttext("hello\n", 0, 1);
         te.checkpoint();

         Matcher m = Pattern.compile("xyz").matcher("");
         assertFalse(te.searchForward(m, 0, 1));

         te.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ================================================================
   // toString
   // ================================================================

   @Test
   @DisplayName("toString contains file name")
   void toStringContainsFileName() throws IOException {
      String fname = "ju_ecx_tostr";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      String str = te.toString();
      assertTrue(str.contains(fname),
         "toString should contain the file name");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("toString of unmodified file does not say MODIFIED")
   void toStringNotModified() throws IOException {
      String fname = "ju_ecx_tostr2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      String str = te.toString();
      assertFalse(str.contains("MODIFIED"),
         "fresh file toString should not show MODIFIED");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ================================================================
   // indexOf
   // ================================================================

   @Test
   @DisplayName("indexOf returns -1 for absent object")
   void indexOfAbsent() throws IOException {
      String fname = "ju_ecx_indexOf";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("aaa\nbbb\n", 0, 1);
      te.checkpoint();
      assertEquals(-1, te.indexOf("nothere"),
         "indexOf should return -1 for missing element");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ================================================================
   // donereading / readIn
   // ================================================================

   @Test
   @DisplayName("donereading is true after finish()")
   void donereadingTrue() throws IOException {
      String fname = "ju_ecx_done";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.finish();
      assertTrue(te.donereading(),
         "after finish(), donereading should be true");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("readIn returns consistent value")
   void readInConsistent() throws IOException {
      String fname = "ju_ecx_readin";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.finish();
      assertEquals(te.finish(), te.readIn(),
         "readIn after finish should equal finish()");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ================================================================
   // contains vs containsNow
   // ================================================================

   @Test
   @DisplayName("contains(0) is always true")
   void containsZero() throws IOException {
      String fname = "ju_ecx_cont";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      assertTrue(te.contains(0), "index 0 (sentinel) should exist");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("containsNow false for large index")
   void containsNowFalse() throws IOException {
      String fname = "ju_ecx_contnow";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      assertFalse(te.containsNow(999999),
         "containsNow should be false for huge index");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ================================================================
   // isValid
   // ================================================================

   @Test
   @DisplayName("isValid returns true for active container")
   void isValidTrue() throws IOException {
      String fname = "ju_ecx_valid";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      assertTrue(te.isValid(), "active EditContainer should be valid");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ================================================================
   // fdes()
   // ================================================================

   @Test
   @DisplayName("fdes returns the FileDescriptor")
   void fdesReturns() throws IOException {
      String fname = "ju_ecx_fdes";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      assertNotNull(te.fdes(),
         "fdes() should return non-null descriptor");
      assertTrue(te.fdes().shortName.contains(fname));

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ================================================================
   // ReadOnlyException
   // ================================================================

   @Test
   @DisplayName("ReadOnlyException is UnsupportedOperationException")
   void readOnlyExceptionType() throws IOException {
      String fname = "ju_ecx_ro";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.setReadOnly(true);
      assertThrows(UnsupportedOperationException.class,
         () -> te.inserttext("x", 0, 1));

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ================================================================
   // getElementsAt range
   // ================================================================

   @Test
   @DisplayName("getElementsAt returns correct range")
   void getElementsAtRange() throws IOException {
      String fname = "ju_ecx_range";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("aa\nbb\ncc\ndd\n", 0, 1);
      te.checkpoint();

      ArrayList<String> elems = te.getElementsAt(1, 2);
      assertNotNull(elems);
      assertEquals(2, elems.size(),
         "getElementsAt(1,2) should return 2 elements");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("getElementsAt single element")
   void getElementsAtSingle() throws IOException {
      String fname = "ju_ecx_range1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("only\n", 0, 1);
      te.checkpoint();

      ArrayList<String> elems = te.getElementsAt(1, 1);
      assertNotNull(elems);
      assertEquals(1, elems.size());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ================================================================
   // forceWritten
   // ================================================================

   @Test
   @DisplayName("forceWritten clears modified flag")
   void forceWrittenClearsModified() throws IOException {
      String fname = "ju_ecx_fw";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("data\n", 0, 1);
      te.checkpoint();
      assertTrue(te.isModified(), "after insert, should be modified");
      te.forceWritten();
      assertFalse(te.isModified(), "after forceWritten, not modified");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ================================================================
   // Multiple undo/redo cycles
   // ================================================================

   @Test
   @DisplayName("multiple checkpoint-undo-redo cycle")
   void multiCheckpointUndoRedo() throws IOException {
      String fname = "ju_ecx_multi";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("one\n", 0, 1);
      te.checkpoint();
      te.inserttext("two\n", 0, 2);
      te.checkpoint();
      int afterTwo = te.finish();

      te.undo(); // undo "two"
      int afterUndo1 = te.finish();
      assertTrue(afterUndo1 < afterTwo,
         "undo should reduce size");

      te.undo(); // undo "one"
      int afterUndo2 = te.finish();
      assertTrue(afterUndo2 < afterUndo1,
         "second undo should further reduce size");

      te.redo(); // redo "one"
      assertEquals(afterUndo1, te.finish(),
         "redo should restore first insert");

      te.redo(); // redo "two"
      assertEquals(afterTwo, te.finish(),
         "second redo should restore second insert");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ================================================================
   // changElementAt on modified buffer
   // ================================================================

   @Test
   @DisplayName("changeElementAtStr modifies and marks modified")
   void changeElementAtStrModifies() throws IOException {
      String fname = "ju_ecx_chgstr";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("old\n", 0, 1);
      te.checkpoint();
      te.forceWritten();
      assertFalse(te.isModified());

      te.changeElementAtStr("new", 1);
      te.checkpoint();
      assertTrue(te.isModified(),
         "changeElementAtStr should mark modified");
      assertEquals("new", te.at(1).toString());

      te.disposeFvc();
      deleteTestFiles(fname);
   }
}
