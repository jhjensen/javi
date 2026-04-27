package javi;

import javi.ai.AIException;
import javi.ai.tools.BufferReadTool;
import javi.ai.tools.BufferWriteTool;
import javi.ai.tools.PermissionLevel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link BufferWriteTool} with real editor
 * buffers. Exercises replace and insert operations through the
 * tool's execute() method against open EditContainer instances.
 *
 * <p>Requires full editor initialization via {@link TestInit}
 * because BufferWriteTool operates on the live buffer registry
 * (EditContainer.grepfile) and uses undo-capable editing APIs.</p>
 */
@DisplayName("BufferWriteTool integration")
class BufferWriteToolJUnitTest {

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
      return te;
   }

   private static void deleteTestFiles(String... names)
         throws IOException {
      for (String name : names) {
         makeLocal(name).delete();
         makeLocal(name + ".dmp2").delete();
      }
   }

   // ── Metadata ─────────────────────────────────────────────

   @Test
   @DisplayName("tool metadata is correct")
   void metadata() {
      BufferWriteTool tool = new BufferWriteTool();
      assertEquals("buffer_write", tool.name());
      assertNotNull(tool.description());
      assertNotNull(tool.inputSchema());
      assertEquals(PermissionLevel.CONFIRM_FIRST,
         tool.permissionLevel());
   }

   // ── Replace operations ───────────────────────────────────

   @Nested
   @DisplayName("replace operation")
   class ReplaceTests {

      private BufferWriteTool tool;

      @BeforeEach
      void setUp() {
         tool = new BufferWriteTool();
      }

      @Test
      @DisplayName("replaces a single line")
      void replaceSingleLine() throws Exception {
         String fname = "ju_bwt_rep1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
         ex.checkpoint();
         ex.finish();

         String result = tool.execute(Map.of(
            "name", fname,
            "operation", "replace",
            "start_line", "2",
            "end_line", "2",
            "text", "XXX"));

         assertTrue(result.contains("Replaced"),
            "should confirm replacement: " + result);
         assertEquals("aaa", ex.at(1).toString());
         assertEquals("XXX", ex.at(2).toString());
         assertEquals("ccc", ex.at(3).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("replaces multiple lines with different count")
      void replaceMultipleLines() throws Exception {
         String fname = "ju_bwt_rep2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("line1\nline2\nline3\nline4\n", 0, 1);
         ex.checkpoint();
         ex.finish();

         // Replace lines 2-3 with a single line
         String result = tool.execute(Map.of(
            "name", fname,
            "operation", "replace",
            "start_line", "2",
            "end_line", "3",
            "text", "MERGED"));

         assertTrue(result.contains("Replaced"),
            "should confirm replacement: " + result);
         assertEquals("line1", ex.at(1).toString());
         assertEquals("MERGED", ex.at(2).toString());
         assertEquals("line4", ex.at(3).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("replaces single line with multiple lines")
      void expandsLineCount() throws Exception {
         String fname = "ju_bwt_rep3";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("before\ntarget\nafter\n", 0, 1);
         ex.checkpoint();
         ex.finish();

         String result = tool.execute(Map.of(
            "name", fname,
            "operation", "replace",
            "start_line", "2",
            "end_line", "2",
            "text", "new1\nnew2\nnew3"));

         assertTrue(result.contains("Replaced"),
            "should confirm: " + result);
         assertEquals("before", ex.at(1).toString());
         assertEquals("new1", ex.at(2).toString());
         assertEquals("new2", ex.at(3).toString());
         assertEquals("new3", ex.at(4).toString());
         assertEquals("after", ex.at(5).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("returns error for out-of-range start_line")
      void outOfRangeStart() throws Exception {
         String fname = "ju_bwt_rep4";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("only\n", 0, 1);
         ex.checkpoint();
         ex.finish();

         String result = tool.execute(Map.of(
            "name", fname,
            "operation", "replace",
            "start_line", "99",
            "end_line", "99",
            "text", "nope"));

         assertTrue(result.contains("Error"),
            "should return error: " + result);
         assertTrue(result.contains("out of range"),
            "should mention out of range: " + result);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("returns error for end_line before start_line")
      void endBeforeStart() throws Exception {
         String fname = "ju_bwt_rep5";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
         ex.checkpoint();
         ex.finish();

         String result = tool.execute(Map.of(
            "name", fname,
            "operation", "replace",
            "start_line", "3",
            "end_line", "1",
            "text", "nope"));

         assertTrue(result.contains("Error"),
            "should return error: " + result);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── Insert operations ────────────────────────────────────

   @Nested
   @DisplayName("insert operation")
   class InsertTests {

      private BufferWriteTool tool;

      @BeforeEach
      void setUp() {
         tool = new BufferWriteTool();
      }

      @Test
      @DisplayName("inserts lines after a given position")
      void insertAfterLine() throws Exception {
         String fname = "ju_bwt_ins1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("first\nsecond\n", 0, 1);
         ex.checkpoint();
         ex.finish();

         String result = tool.execute(Map.of(
            "name", fname,
            "operation", "insert",
            "start_line", "1",
            "text", "inserted"));

         assertTrue(result.contains("Inserted"),
            "should confirm insertion: " + result);
         assertEquals("first", ex.at(1).toString());
         assertEquals("inserted", ex.at(2).toString());
         assertEquals("second", ex.at(3).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("inserts multiple lines")
      void insertMultipleLines() throws Exception {
         String fname = "ju_bwt_ins2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("alpha\nomega\n", 0, 1);
         ex.checkpoint();
         ex.finish();

         String result = tool.execute(Map.of(
            "name", fname,
            "operation", "insert",
            "start_line", "1",
            "text", "beta\ngamma\ndelta"));

         assertTrue(result.contains("Inserted"),
            "should confirm: " + result);
         assertTrue(result.contains("3 lines"),
            "should report 3 lines: " + result);
         assertEquals("alpha", ex.at(1).toString());
         assertEquals("beta", ex.at(2).toString());
         assertEquals("gamma", ex.at(3).toString());
         assertEquals("delta", ex.at(4).toString());
         assertEquals("omega", ex.at(5).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("insert at position 0 prepends")
      void insertAtZeroPrepends() throws Exception {
         String fname = "ju_bwt_ins3";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("existing\n", 0, 1);
         ex.checkpoint();
         ex.finish();

         String result = tool.execute(Map.of(
            "name", fname,
            "operation", "insert",
            "start_line", "0",
            "text", "prepended"));

         assertTrue(result.contains("Inserted"),
            "should confirm: " + result);
         assertEquals("prepended", ex.at(1).toString());
         assertEquals("existing", ex.at(2).toString());

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("returns error for out-of-range insert position")
      void insertOutOfRange() throws Exception {
         String fname = "ju_bwt_ins4";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("only\n", 0, 1);
         ex.checkpoint();
         ex.finish();

         String result = tool.execute(Map.of(
            "name", fname,
            "operation", "insert",
            "start_line", "99",
            "text", "nope"));

         assertTrue(result.contains("Error"),
            "should return error: " + result);
         assertTrue(result.contains("out of range"),
            "should mention out of range: " + result);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── Error handling ───────────────────────────────────────

   @Nested
   @DisplayName("error handling")
   class ErrorTests {

      private BufferWriteTool tool;

      @BeforeEach
      void setUp() {
         tool = new BufferWriteTool();
      }

      @Test
      @DisplayName("throws on missing name parameter")
      void missingName() {
         assertThrows(AIException.class,
            () -> tool.execute(Map.of(
               "operation", "replace",
               "start_line", "1",
               "text", "x")));
      }

      @Test
      @DisplayName("throws on missing operation parameter")
      void missingOperation() {
         assertThrows(AIException.class,
            () -> tool.execute(Map.of(
               "name", "test",
               "start_line", "1",
               "text", "x")));
      }

      @Test
      @DisplayName("throws on missing start_line parameter")
      void missingStartLine() {
         assertThrows(AIException.class,
            () -> tool.execute(Map.of(
               "name", "test",
               "operation", "replace",
               "text", "x")));
      }

      @Test
      @DisplayName("returns error for nonexistent buffer")
      void nonexistentBuffer() throws Exception {
         String result = tool.execute(Map.of(
            "name", "no_such_buffer_xyz_12345",
            "operation", "replace",
            "start_line", "1",
            "text", "x"));

         assertTrue(result.contains("Error"),
            "should return error: " + result);
         assertTrue(result.contains("not found"),
            "should mention not found: " + result);
      }

      @Test
      @DisplayName("returns error for unknown operation")
      void unknownOperation() throws Exception {
         String fname = "ju_bwt_err1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("data\n", 0, 1);
         ex.checkpoint();
         ex.finish();

         String result = tool.execute(Map.of(
            "name", fname,
            "operation", "delete",
            "start_line", "1",
            "text", "x"));

         assertTrue(result.contains("Error"),
            "should return error: " + result);
         assertTrue(
            result.contains("unknown operation"),
            "should mention unknown operation: " + result);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("returns error for non-numeric start_line")
      void nonNumericStartLine() throws Exception {
         String fname = "ju_bwt_err2";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("data\n", 0, 1);
         ex.checkpoint();
         ex.finish();

         String result = tool.execute(Map.of(
            "name", fname,
            "operation", "replace",
            "start_line", "abc",
            "text", "x"));

         assertTrue(result.contains("Error"),
            "should return error: " + result);
         assertTrue(result.contains("invalid"),
            "should mention invalid: " + result);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── BufferReadTool round-trip ─────────────────────────────

   @Nested
   @DisplayName("round-trip with BufferReadTool")
   class RoundTripTests {

      @Test
      @DisplayName("write then read reflects changes")
      void writeThenRead() throws Exception {
         String fname = "ju_bwt_rt1";
         UI.setStream(new StringReader(""));
         deleteTestFiles(fname);

         TextEdit<String> ex = openTestFile(fname);
         ex.inserttext("original\n", 0, 1);
         ex.checkpoint();
         ex.finish();

         BufferWriteTool writeTool = new BufferWriteTool();
         writeTool.execute(Map.of(
            "name", fname,
            "operation", "replace",
            "start_line", "1",
            "end_line", "1",
            "text", "modified"));

         BufferReadTool readTool = new BufferReadTool();
         String content = readTool.execute(
            Map.of("name", fname));

         assertTrue(content.contains("modified"),
            "read should reflect write: " + content);

         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }
}
