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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TextEdit} ex-mode command processing
 * ({@code processCommand}), text operations, and edge cases.
 */
class TextEditCommandsJUnitTest {

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

   // ── processCommand: substitute ───────────────────────────────

   @Nested
   @DisplayName("processCommand substitute")
   class SubstituteTests {

      @Test
      @DisplayName("simple substitute on current line")
      void simpleSubstitute() throws Exception {
         String fname = "ju_tepc_sub1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("hello world\n", 0, 1);
         ex.checkpoint();

         int result = ex.processCommand("s/hello/goodbye/", 1);
         assertEquals(1, result);
         assertEquals("goodbye world", ex.at(1).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("global substitute on current line")
      void globalSubstitute() throws Exception {
         String fname = "ju_tepc_sub2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("aaa bbb aaa ccc aaa\n", 0, 1);
         ex.checkpoint();

         int result = ex.processCommand("s/aaa/xxx/g", 1);
         assertEquals(1, result);
         assertEquals("xxx bbb xxx ccc xxx",
            ex.at(1).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("substitute on line range")
      void rangeSubstitute() throws Exception {
         String fname = "ju_tepc_sub3";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("old\nold\nold\n", 0, 1);
         ex.checkpoint();

         int result = ex.processCommand("1,3s/old/new/", 1);
         assertEquals(1, result);
         assertEquals("new", ex.at(1).toString());
         assertEquals("new", ex.at(2).toString());
         assertEquals("new", ex.at(3).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("substitute on all lines with %")
      void percentSubstitute() throws Exception {
         String fname = "ju_tepc_sub4";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("abc\ndef\nabc\n", 0, 1);
         ex.checkpoint();

         int result = ex.processCommand("%s/abc/xyz/", 1);
         assertEquals(1, result);
         assertEquals("xyz", ex.at(1).toString());
         assertEquals("def", ex.at(2).toString());
         assertEquals("xyz", ex.at(3).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("substitute with regex metacharacters")
      void regexSubstitute() throws Exception {
         String fname = "ju_tepc_sub5";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("foo123bar\n", 0, 1);
         ex.checkpoint();

         int result = ex.processCommand("s/\\d+/NUM/", 1);
         assertEquals(1, result);
         assertEquals("fooNUMbar", ex.at(1).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── processCommand: delete ───────────────────────────────────

   @Nested
   @DisplayName("processCommand delete")
   class DeleteTests {

      @Test
      @DisplayName("delete current line")
      void deleteCurrentLine() throws Exception {
         String fname = "ju_tepc_del1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("keep\nremove\nkeep\n", 0, 1);
         ex.checkpoint();

         int result = ex.processCommand("2d", 1);
         assertEquals(2, result);
         assertEquals("keep", ex.at(1).toString());
         assertEquals("keep", ex.at(2).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("delete line range")
      void deleteRange() throws Exception {
         String fname = "ju_tepc_del2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("a\nb\nc\nd\ne\n", 0, 1);
         ex.checkpoint();

         int result = ex.processCommand("2,4d", 1);
         assertEquals(2, result);
         assertEquals("a", ex.at(1).toString());
         assertEquals("e", ex.at(2).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("delete with global filter")
      void deleteWithGlobal() throws Exception {
         String fname = "ju_tepc_del3";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("keep\nDELETE\nkeep\nDELETE\n", 0, 1);
         ex.checkpoint();

         int result = ex.processCommand("%g/DELETE/d", 1);
         assertEquals(1, result);
         assertEquals("keep", ex.at(1).toString());
         assertEquals("keep", ex.at(2).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("delete with inverse global filter (v)")
      void deleteWithInverseGlobal() throws Exception {
         String fname = "ju_tepc_del4";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("KEEP\nremove\nKEEP\nremove\n", 0, 1);
         ex.checkpoint();

         int result = ex.processCommand("%v/KEEP/d", 1);
         assertEquals(1, result);
         assertEquals("KEEP", ex.at(1).toString());
         assertEquals("KEEP", ex.at(2).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── processCommand: copy/move ────────────────────────────────

   @Nested
   @DisplayName("processCommand copy/move")
   class CopyMoveTests {

      @Test
      @DisplayName("copy line to another position")
      void copyLine() throws Exception {
         String fname = "ju_tepc_cp1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
         ex.checkpoint();

         // copy line 1 to after line 3
         int result = ex.processCommand("1t3", 1);
         assertEquals(1, result);
         // Should have: aaa, bbb, ccc, aaa
         assertEquals("aaa", ex.at(1).toString());
         assertEquals("aaa", ex.at(4).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("move line to another position")
      void moveLine() throws Exception {
         String fname = "ju_tepc_mv1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
         ex.checkpoint();

         // move line 3 to after line 1
         int result = ex.processCommand("3m1", 1);
         assertTrue(result > 0, "move should succeed");
         // aaa, ccc, bbb, ddd
         assertEquals("aaa", ex.at(1).toString());
         assertEquals("ccc", ex.at(2).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── processCommand: line addressing ──────────────────────────

   @Nested
   @DisplayName("processCommand line addressing")
   class AddressingTests {

      @Test
      @DisplayName("number alone goes to that line")
      void numberGoesToLine() throws Exception {
         String fname = "ju_tepc_addr1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("a\nb\nc\n", 0, 1);
         ex.checkpoint();

         int result = ex.processCommand("2", 1);
         assertEquals(2, result);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("invalid command returns -1")
      void invalidCommandReturns() throws Exception {
         String fname = "ju_tepc_addr2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("a\n", 0, 1);
         ex.checkpoint();

         int result = ex.processCommand("z", 1);
         assertEquals(-1, result);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── gettext / deletetext ─────────────────────────────────────

   @Nested
   @DisplayName("gettext()")
   class GetTextTests {

      @Test
      @DisplayName("gettext extracts range within a single line")
      void singleLineExtract() throws Exception {
         String fname = "ju_tegt1";
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
      @DisplayName("gettext across multiple lines")
      void multiLineExtract() throws Exception {
         String fname = "ju_tegt2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
         ex.checkpoint();

         String text = ex.gettext(0, 1, 3, 2);
         assertNotEquals(null, text);
         assertTrue(text.contains("aaa"),
            "should contain first line");

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("gettext same position returns null")
      void samePosition() throws Exception {
         String fname = "ju_tegt3";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("content\n", 0, 1);
         ex.checkpoint();

         String text = ex.gettext(0, 1, 0, 1);
         assertEquals(null, text);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── inserttext and multi-line operations ─────────────────────

   @Nested
   @DisplayName("inserttext()")
   class InsertTextTests {

      @Test
      @DisplayName("insert empty string is no-op")
      void insertEmpty() throws Exception {
         String fname = "ju_teit1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         int before = ex.readIn();

         ex.inserttext("", 0, 1);
         assertEquals(before, ex.readIn());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("insert multi-line text creates multiple lines")
      void insertMultiLine() throws Exception {
         String fname = "ju_teit2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("line1\nline2\nline3\n", 0, 1);
         ex.checkpoint();

         assertEquals("line1", ex.at(1).toString());
         assertEquals("line2", ex.at(2).toString());
         assertEquals("line3", ex.at(3).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("insert at middle of buffer")
      void insertAtMiddle() throws Exception {
         String fname = "ju_teit3";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("first\nlast\n", 0, 1);
         ex.checkpoint();

         ex.inserttext("middle\n", 0, 2);
         ex.checkpoint();

         // Verify middle was inserted between first and last
         assertEquals("first", ex.at(1).toString());
         assertEquals("middle", ex.at(2).toString());
         assertEquals("last", ex.at(3).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── changeElementAtStr with undo ─────────────────────────────

   @Nested
   @DisplayName("changeElementAtStr undo chain")
   class ChangeUndoChainTests {

      @Test
      @DisplayName("multiple changes can be undone in sequence")
      void multipleChangesUndo() throws Exception {
         String fname = "ju_tecuc1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("original\n", 0, 1);
         ex.checkpoint();

         ex.changeElementAtStr("change1", 1);
         ex.checkpoint();
         ex.changeElementAtStr("change2", 1);
         ex.checkpoint();
         ex.changeElementAtStr("change3", 1);
         ex.checkpoint();

         assertEquals("change3", ex.at(1).toString());

         ex.undo();
         assertEquals("change2", ex.at(1).toString());

         ex.undo();
         assertEquals("change1", ex.at(1).toString());

         ex.undo();
         assertEquals("original", ex.at(1).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("redo after undo restores changes")
      void redoAfterUndo() throws Exception {
         String fname = "ju_tecuc2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);
         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("base\n", 0, 1);
         ex.checkpoint();

         ex.changeElementAtStr("modified", 1);
         ex.checkpoint();
         assertEquals("modified", ex.at(1).toString());

         ex.undo();
         assertEquals("base", ex.at(1).toString());

         ex.redo();
         assertEquals("modified", ex.at(1).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }
}
