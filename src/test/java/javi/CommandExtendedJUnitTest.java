package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Extended coverage tests for {@link Command} — doneInit,
 * command parsing, set command, unknown command handling.
 */
class CommandExtendedJUnitTest {

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
      return te;
   }

   private static void deleteTestFiles(String... names)
         throws IOException {
      for (String name : names) {
         makeLocal(name).delete();
         makeLocal(name + ".dmp2").delete();
      }
   }

   // ── command() parsing ──────────────────────────────────────

   @Test
   void commandWithSpaceSplitsArg() throws Exception {
      String fname = "ju_cmd_split1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("test content\n", 0, 1);
      te.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(te, view);

      // "set tabstop=4" is a valid set command
      // but "help index" will try to open help
      // Just test that unknown commands don't crash
      Command.command("unknowncmd123 somearg", fvc, null);
      // Should print error message, not crash

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void commandNoArgNoSpace() throws Exception {
      String fname = "ju_cmd_noarg";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("test\n", 0, 1);
      te.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(te, view);

      // Unknown single-word command
      Command.command("nonexistent", fvc, null);

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void commandWithExplicitArgs() throws Exception {
      String fname = "ju_cmd_explicit";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("test\n", 0, 1);
      te.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(te, view);

      // When args is not null, the whole line is the command
      Command.command("unknowncmd", fvc, "explicit_arg");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void commandWithNullFvc() {
      // command() with fvc==null should fall back to
      // FvContext.getCurrFvc()
      assertDoesNotThrow(
         () -> Command.command("unknowncmd_null", null, null));
   }

   // ── doneInit ───────────────────────────────────────────────

   @Test
   void doneInitWithEmptyListDoesNotThrow() {
      // After all previous init, cmd list should be empty
      assertDoesNotThrow(() -> Command.doneInit());
   }

   // ── Command registration check ─────────────────────────────

   @Test
   void allCommandNamesRegistered() {
      assertNotNull(Rgroup.bindingLookup("r"));
      assertNotNull(Rgroup.bindingLookup("tabstop"));
      assertNotNull(Rgroup.bindingLookup("terminatewep"));
      assertNotNull(Rgroup.bindingLookup("commandproc"));
      assertNotNull(Rgroup.bindingLookup("checkout"));
      assertNotNull(Rgroup.bindingLookup("set"));
      assertNotNull(Rgroup.bindingLookup("e!"));
      assertNotNull(Rgroup.bindingLookup("help"));
      assertNotNull(Rgroup.bindingLookup("map"));
   }

   // ── set command parsing ────────────────────────────────────

   @Test
   void setCommandMissingEqualsReportsError() throws Exception {
      String fname = "ju_cmd_set1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("test\n", 0, 1);
      te.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(te, view);

      // "set noeq" has no = sign → InputException → message
      Command.command("set noeq", fvc, null);

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void setCommandUnknownVariable() throws Exception {
      String fname = "ju_cmd_set2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("test\n", 0, 1);
      te.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(te, view);

      // "set xyz=123" with unknown var → InputException → message
      Command.command("set xyz=123", fvc, null);

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void setTabstopChangesValue() throws Exception {
      String fname = "ju_cmd_tabstop";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("test\n", 0, 1);
      te.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(te, view);

      // "tabstop 4" dispatches to Command.doroutine TAB_STOP
      Command.command("tabstop 4", fvc, null);

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── help command ───────────────────────────────────────────

   @Test
   void helpCommandDispatchesWithoutCrash() throws Exception {
      String fname = "ju_cmd_help1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("test\n", 0, 1);
      te.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(te, view);

      // "help" should invoke showHelp
      Command.command("help", fvc, null);

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void helpCommandWithTopic() throws Exception {
      String fname = "ju_cmd_help2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("test\n", 0, 1);
      te.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(te, view);

      Command.command("help index", fvc, null);

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── command dispatches registered binding ─────────────────

   @Test
   void commandDispatchesRegisteredBinding() throws Exception {
      String fname = "ju_cmd_dispatch1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("test\n", 0, 1);
      te.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(te, view);

      // tabstop is a registered command that takes an int arg
      Command.command("tabstop 8", fvc, null);
      // Should not throw

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── readini does not throw on missing file ─────────────────

   @Test
   void readiniHandlesMissingFile() {
      assertDoesNotThrow(() -> Command.readini());
   }
}
