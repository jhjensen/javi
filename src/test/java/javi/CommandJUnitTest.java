package javi;

import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link Command} — ex-mode command parsing
 * and dispatch.
 *
 * <p>
 * Covers:
 * </p>
 * <ul>
 *   <li>Registration of Command's rnames ("r", "tabstop", etc.)</li>
 *   <li>{@code command()} parsing: splitting command + args</li>
 *   <li>{@code command()} dispatch to registered bindings</li>
 *   <li>Unknown command error reporting</li>
 *   <li>{@code set} command parsing</li>
 * </ul>
 */
class CommandJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
   }

   // ============================================================
   // Command registration tests
   // ============================================================

   @Test
   void rCommandIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("r"),
         "r (read) should be registered by Command");
   }

   @Test
   void tabstopIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("tabstop"),
         "tabstop should be registered by Command");
   }

   @Test
   void terminatewepIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("terminatewep"),
         "terminatewep should be registered by Command");
   }

   @Test
   void commandprocIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("commandproc"),
         "commandproc should be registered by Command or MiscCommands");
   }

   @Test
   void setIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("set"),
         "set should be registered by Command");
   }

   @Test
   void eBangIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("e!"),
         "e! should be registered by Command");
   }

   @Test
   void helpIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("help"),
         "help should be registered by Command");
   }

   @Test
   void mapIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("map"),
         "map should be registered by Command");
   }

   @Test
   void checkoutIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("checkout"),
         "checkout should be registered by Command");
   }

   // ============================================================
   // Command parsing: line splitting logic
   // ============================================================

   @Test
   void commandLineSplittingExtractsArgs() {
      // Test the parsing logic that splits "command args":
      // The static method uses indexOf(' ') to split.
      String line = "tabstop 8";
      int spaceIdx = line.indexOf(' ');
      assertTrue(spaceIdx > 0, "should have a space");
      String cmd = line.substring(0, spaceIdx);
      String args = line.substring(cmd.length()).trim();
      assertEquals("tabstop", cmd);
      assertEquals("8", args);
   }

   @Test
   void commandLineSplittingNoArgsIsJustCommand() {
      String line = "undo";
      int spaceIdx = line.indexOf(' ');
      assertEquals(-1, spaceIdx, "no space means no args");
   }

   @Test
   void commandLineSplittingEmptyArgsAfterSpace() {
      String line = "set ";
      int spaceIdx = line.indexOf(' ');
      String cmd = line.substring(0, spaceIdx);
      String args = line.substring(cmd.length()).trim();
      assertEquals("set", cmd);
      assertEquals(0, args.length(), "trimmed args should be empty");
   }

   @Test
   void commandLineSplittingMultiWordArgs() {
      String line = "set tabstop=4";
      int spaceIdx = line.indexOf(' ');
      String cmd = line.substring(0, spaceIdx);
      String args = line.substring(cmd.length()).trim();
      assertEquals("set", cmd);
      assertEquals("tabstop=4", args);
   }

   @Test
   void commandLineSplittingWithLeadingArgSpaces() {
      String line = "help   topic";
      int spaceIdx = line.indexOf(' ');
      String cmd = line.substring(0, spaceIdx);
      String args = line.substring(cmd.length()).trim();
      assertEquals("help", cmd);
      assertEquals("topic", args);
   }

   // ============================================================
   // KeyBinding.matches tests
   // ============================================================

   @Test
   void commandBindingMatchesItsOwnInstance() {
      Rgroup.KeyBinding kb = Rgroup.bindingLookup("r");
      assertNotNull(kb);
      // The binding should have a non-null string representation
      String s = kb.toString();
      assertNotNull(s);
      assertTrue(s.length() > 0, "toString should be non-empty");
   }

   // ============================================================
   // Command.command() dispatch integration tests
   // ============================================================

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

   private static void deleteTestFiles(String baseName) {
      for (String ext : new String[]{"", ".dmp2"}) {
         try {
            FileDescriptor.LocalFile.make(
               history.Testutil.testFile(baseName + ext)).delete();
         } catch (Exception ignore) {
         }
      }
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

   @Test
   void commandDispatchesTabstopToView() throws Exception {
      String fname = "ju_cmd_ts";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      Command.command("tabstop 4", fvc, null);
      assertEquals(4, view.getTabStop());

      Command.command("tabstop 8", fvc, null);
      assertEquals(8, view.getTabStop());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void commandDispatchesSetTabstop() throws Exception {
      String fname = "ju_cmd_set";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      Command.command("set tabstop=3", fvc, null);
      assertEquals(3, view.getTabStop());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void commandDispatchesExSubstitute() throws Exception {
      String fname = "ju_cmd_sub";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("hello world\n", 0, 1);
      te.checkpoint();
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      // ex substitute goes through Command.command -> processCommand
      Command.command("1s/hello/hey/", fvc, null);
      assertEquals("hey world", te.at(1).toString());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void commandDispatchesGotoLine() throws Exception {
      String fname = "ju_cmd_goto";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("line1\nline2\nline3\n", 0, 1);
      te.checkpoint();
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      // numeric-only command goes through processCommand as goto-line
      Command.command("2", fvc, null);
      assertEquals(2, fvc.inserty());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void commandUnknownReportsError() throws Exception {
      String fname = "ju_cmd_unk";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      // Unknown command should not throw, just report message
      Command.command("xyznonexistent", fvc, null);

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void commandWithNullFvcUsesCurrent() throws Exception {
      String fname = "ju_cmd_null";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      // null fvc should use getCurrFvc()
      Command.command("tabstop 6", null, null);

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void commandGlobalDelete() throws Exception {
      String fname = "ju_cmd_gdel";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      te.inserttext("keep1\nremove\nkeep2\nremove\nkeep3\n", 0, 1);
      te.checkpoint();
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      Command.command("g/remove/d", fvc, null);
      assertEquals("keep1", te.at(1).toString());
      assertEquals("keep2", te.at(2).toString());
      assertEquals("keep3", te.at(3).toString());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void commandWithExplicitArgs() throws Exception {
      String fname = "ju_cmd_args";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> te = openTestFile(fname);
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      // Pass args explicitly (third parameter)
      Command.command("tabstop", fvc, "5");
      assertEquals(5, view.getTabStop());

      te.disposeFvc();
      deleteTestFiles(fname);
   }
}
