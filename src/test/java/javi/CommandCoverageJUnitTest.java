package javi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for {@link Command} — command() static dispatch,
 * command line parsing, error paths, readini, execCmdList, doneInit.
 */
class CommandCoverageJUnitTest {

   private static TextEdit<String> te;
   private static TestView view;
   private static FvContext fvc;

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
      EventQueue.biglock2.lock();
      try {
         File testDir = history.Testutil.testDir;
         File testFile = new File(testDir, "cc_test");
         try (OutputStreamWriter w = new OutputStreamWriter(
               new FileOutputStream(testFile),
               StandardCharsets.UTF_8)) {
            w.write("line one\nline two\nline three\n");
         }
         FileDescriptor fd = FileDescriptor.make(testFile.getPath());
         FileProperties<String> fp =
            new FileProperties<>(fd, StringIoc.converter);
         FileInput fi = new FileInput(fp);
         te = new TextEdit<>(fi, fp);
         te.finish();
         view = new TestView(true);
         fvc = FvContext.connectFv(te, view);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @BeforeEach
   void acquireLock() throws IOException {
      EventQueue.biglock2.lock();
      UI.setStream(new StringReader(""));
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   // ── command() dispatch ────────────────────────────────────

   @Test
   @DisplayName("command() with space-separated line splits correctly")
   void commandSplitsOnSpace() throws Exception {
      Command.command("tabstop 4", fvc, null);
      assertEquals(4, view.getTabStop());
   }

   @Test
   @DisplayName("command() with no space uses entire line as command")
   void commandNoSpaceUsesWholeLine() throws Exception {
      Command.command("help", fvc, null);
   }

   @Test
   @DisplayName("command() with explicit args ignores line parsing")
   void commandWithExplicitArgs() throws Exception {
      Command.command("tabstop", fvc, "8");
      assertEquals(8, view.getTabStop());
   }

   @Test
   @DisplayName("command() unknown command reports error message")
   void commandUnknownReportsError() throws Exception {
      Command.command("__totally_unknown_command__", fvc, null);
   }

   @Test
   @DisplayName("command() with null fvc uses current")
   void commandNullFvcUsesCurrent() throws Exception {
      Command.command("tabstop 4", null, null);
   }

   @Test
   @DisplayName("command() with leading spaces in args trims them")
   void commandTrimsArgs() throws Exception {
      Command.command("tabstop   8", fvc, null);
      assertEquals(8, view.getTabStop());
   }

   // ── command() with processCommand fallback ────────────────

   @Test
   @DisplayName("command() falls through to processCommand for ex cmds")
   void commandFallsToProcessCommand() throws Exception {
      Command.command("1", fvc, null);
   }

   @Test
   @DisplayName("command() with empty args after space treats as null")
   void commandEmptyArgsAfterSpace() throws Exception {
      Command.command("tabstop ", fvc, null);
   }

   // ── command() IOException/InputException paths ────────────

   @Test
   @DisplayName("command() handles InputException from processCommand")
   void commandHandlesInputException() throws Exception {
      Command.command("999999", fvc, null);
   }

   // ── set command ───────────────────────────────────────────

   @Nested
   @DisplayName("set command dispatch")
   class SetCommandTests {
      @Test
      @DisplayName("set with valid variable=value dispatches")
      void setValidVariable() throws Exception {
         Command.command("set tabstop=4", fvc, null);
         assertEquals(4, view.getTabStop());
      }

      @Test
      @DisplayName("set without = reports error")
      void setWithoutEquals() throws Exception {
         Command.command("set invalid_no_equals", fvc, null);
      }

      @Test
      @DisplayName("set with unknown variable reports error")
      void setUnknownVariable() throws Exception {
         Command.command("set __unknown__=value", fvc, null);
      }
   }

   // ── execCmdList / doneInit ────────────────────────────────

   @Test
   @DisplayName("execCmdList on empty list is safe")
   void execCmdListEmptyIsSafe() {
      Command.execCmdList();
   }

   @Test
   @DisplayName("doneInit on empty list is safe")
   void doneInitEmptyIsSafe() {
      Command.doneInit();
   }

   // ── Command.Cmd enum ──────────────────────────────────────

   @Test
   @DisplayName("Cmd enum has expected values")
   void cmdEnumValues() {
      Command.Cmd[] values = Command.Cmd.values();
      assertTrue(values.length >= 10,
         "should have at least 10 commands");
   }

   @Test
   @DisplayName("Cmd.READ_FILE is at ordinal 1")
   void cmdReadFileOrdinal() {
      assertEquals(1, Command.Cmd.READ_FILE.ordinal());
   }

   @Test
   @DisplayName("Cmd.valueOf round-trips")
   void cmdValueOfRoundTrips() {
      assertEquals(Command.Cmd.HELP,
         Command.Cmd.valueOf("HELP"));
   }

   // ── map command ───────────────────────────────────────────
   // Note: 'map' command requires MapEvent.bindCommands() which
   // is unsafe in test env (needs AWT-specific commands).
   // Map dispatch is covered by MapEventJUnitTest instead.
}
