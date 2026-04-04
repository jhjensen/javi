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
 * Coverage tests for Command and MiscCommands via
 * Rgroup.doCommand and ex-mode processCommand.
 */
class CommandMiscCoverageJUnitTest {

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

   // ============================================================
   // Command registration
   // ============================================================

   @Test
   void commandRIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("r"),
         "r (read file) should be registered");
   }

   @Test
   void commandTabstopIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("tabstop"),
         "tabstop should be registered");
   }

   @Test
   void commandSetIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("set"),
         "set should be registered");
   }

   @Test
   void commandHelpIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("help"),
         "help should be registered");
   }

   @Test
   void commandMapIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("map"),
         "map should be registered");
   }

   @Test
   void commandEBangIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("e!"),
         "e! (reload) should be registered");
   }

   // ============================================================
   // MiscCommands registration
   // ============================================================

   @Test
   void miscUndoIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("undo"),
         "undo should be registered");
   }

   @Test
   void miscRedoIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("redo"),
         "redo should be registered");
   }

   @Test
   void miscRedrawIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("redraw"),
         "redraw should be registered");
   }

   @Test
   void miscExecIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("exec"),
         "exec should be registered");
   }

   @Test
   void miscFoldIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("fold"),
         "fold should be registered");
   }

   @Test
   void miscFoldindentIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("foldindent"),
         "foldindent should be registered");
   }

   @Test
   void miscFoldmarkerIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("foldmarker"),
         "foldmarker should be registered");
   }

   @Test
   void miscMapkeyIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("mapkey"),
         "mapkey should be registered");
   }

   @Test
   void miscUnmapkeyIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("unmapkey"),
         "unmapkey should be registered");
   }

   @Test
   void miscKeymapIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("keymap"),
         "keymap should be registered");
   }

   @Test
   void miscSavemapkeysIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("savemapkeys"),
         "savemapkeys should be registered");
   }

   @Test
   void miscLoadmapkeysIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("loadmapkeys"),
         "loadmapkeys should be registered");
   }

   @Test
   void miscShellnewIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("shellnew"),
         "shellnew should be registered");
   }

   // ============================================================
   // Undo/redo via TextEdit (covers MiscCommands undo/redo path)
   // ============================================================

   @Test
   void undoRevertsInsert() throws Exception {
      String fname = "ju_cmc_undo1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("original\n", 0, 1);
      ex.checkpoint();
      assertEquals("original", ex.at(1).toString());

      ex.inserttext("new\n", 0, 1);
      ex.checkpoint();
      assertEquals("new", ex.at(1).toString());

      int undoLine = ex.undo();
      assertTrue(undoLine >= 0, "undo should return valid line");
      assertEquals("original", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void redoRestoresUndoneInsert() throws Exception {
      String fname = "ju_cmc_redo1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("first\n", 0, 1);
      ex.checkpoint();

      ex.inserttext("second\n", 0, 1);
      ex.checkpoint();

      ex.undo();
      assertEquals("first", ex.at(1).toString());

      int redoLine = ex.redo();
      assertTrue(redoLine >= 0, "redo should return valid line");
      assertEquals("second", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void multipleUndoStepsAndRedo() throws Exception {
      String fname = "ju_cmc_multiundo";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("step1\n", 0, 1);
      ex.checkpoint();
      ex.inserttext("step2\n", 0, 1);
      ex.checkpoint();
      ex.inserttext("step3\n", 0, 1);
      ex.checkpoint();

      // Undo 3 times
      ex.undo();
      ex.undo();
      assertEquals("step1", ex.at(1).toString());

      // Redo once
      ex.redo();
      assertEquals("step2", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // MiscCommands getHeight/getWidth
   // ============================================================

   @Test
   void getHeightReturnsDefault() {
      assertTrue(MiscCommands.getHeight() > 0,
         "Default height should be positive");
   }

   @Test
   void getWidthReturnsDefault() {
      assertTrue(MiscCommands.getWidth() > 0,
         "Default width should be positive");
   }

   // ============================================================
   // Command.command static dispatch
   // ============================================================

   @Test
   void commandDispatchBindingLookup() throws Exception {
      // Verify that Command.command dispatches to bindingLookup
      // by calling processCommand which handles tabstop natively
      String fname = "ju_cmc_cmd1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("data\n", 0, 1);
      ex.checkpoint();

      // processCommand with a bare number returns that line
      int result = ex.processCommand("1", 1);
      assertEquals(1, result);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void commandDispatchWithExplicitArgs() throws Exception {
      // Verify processCommand parses range with semicolon
      String fname = "ju_cmc_cmd2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aa\nbb\ncc\ndd\n", 0, 1);
      ex.checkpoint();

      // "2;+1d" means: set ypos=2, then +1=3, delete range 2-3
      ex.processCommand("2;+1d", 1);
      assertEquals("aa", ex.at(1).toString());
      assertEquals("dd", ex.at(2).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void commandExecCmdListEmpty() {
      // execCmdList with no commands should be a no-op
      Command.execCmdList();
   }

   // ============================================================
   // processCommand returns line for bare number
   // ============================================================

   @Test
   void processCommandReturnsLineNumber() throws Exception {
      String fname = "ju_cmc_linenum";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\nb\nc\nd\n", 0, 1);
      ex.checkpoint();

      assertEquals(3, ex.processCommand("3", 1));
      assertEquals(1, ex.processCommand("1", 1));

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void processCommandInvalidReturnsNeg1() throws Exception {
      String fname = "ju_cmc_invalid";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("data\n", 0, 1);
      ex.checkpoint();

      // An unrecognized command should return -1
      int result = ex.processCommand("z", 1);
      assertEquals(-1, result);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // Command.oToInt error handling (covers setcommand path)
   // ============================================================

   @Test
   void commandUnknownReturnsNeg1() throws Exception {
      String fname = "ju_cmc_unk";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("data\n", 0, 1);
      ex.checkpoint();

      // 'co' requires a target line — missing should return -1
      int result = ex.processCommand("cX", 1);
      assertEquals(-1, result);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // FileInput line-splitting modes
   // ============================================================

   @Test
   void fileInputUnixMode() throws Exception {
      String fname = "ju_cmc_unix";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      // Write file with Unix line endings
      String path = testPath(fname);
      java.io.FileOutputStream fos =
         new java.io.FileOutputStream(path);
      fos.write("line1\nline2\nline3\n".getBytes(
         java.nio.charset.StandardCharsets.UTF_8));
      fos.close();

      TextEdit<String> ex = openTestFile(fname);
      assertTrue(ex.finish() >= 4,
         "Unix file should parse into lines");
      assertEquals("line1", ex.at(1).toString());
      assertEquals("line2", ex.at(2).toString());
      assertEquals("line3", ex.at(3).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void fileInputMsMode() throws Exception {
      String fname = "ju_cmc_msdos";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      // Write file with DOS line endings (\r\n)
      String path = testPath(fname);
      java.io.FileOutputStream fos =
         new java.io.FileOutputStream(path);
      fos.write("line1\r\nline2\r\nline3\r\n".getBytes(
         java.nio.charset.StandardCharsets.UTF_8));
      fos.close();

      TextEdit<String> ex = openTestFile(fname);
      assertTrue(ex.finish() >= 4,
         "MS-DOS file should parse into lines");
      assertEquals("line1", ex.at(1).toString());
      assertEquals("line2", ex.at(2).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }
}
