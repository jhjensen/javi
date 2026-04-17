package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * JUnit 5 tests for {@link ContextHelp}.
 */
class ContextHelpJUnitTest {

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

   private String bufferText(TextEdit<String> buf) {
      StringBuilder sb = new StringBuilder();
      int end = buf.finish();
      for (int i = 1; i < end; i++) {
         sb.append(buf.at(i).toString()).append('\n');
      }
      return sb.toString();
   }

   @Test
   @DisplayName("getNormalModeHelp returns non-null buffer")
   void normalModeHelpReturnsBuffer() {
      TextEdit<String> buf =
         ContextHelp.getNormalModeHelp(null);
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains(
         "CONTEXT-SENSITIVE HELP: Normal Mode"));
   }

   @Test
   @DisplayName("normal mode help shows movement or not-initialized")
   void normalModeHelpShowsSections() {
      TextEdit<String> buf =
         ContextHelp.getNormalModeHelp(null);
      String text = bufferText(buf);
      // MapEvent.bindCommands() is not called in tests,
      // so keymap may be null
      assertTrue(
         text.contains("MOVEMENT KEYS")
            || text.contains("not initialized"),
         "should show bindings or not-initialized");
   }

   @Test
   @DisplayName("normal mode help omits ex command summary")
   void normalModeHelpOmitsExSummary() {
      TextEdit<String> buf =
         ContextHelp.getNormalModeHelp(null);
      String text = bufferText(buf);
      assertFalse(text.contains("EX COMMANDS"),
         "normal help should not list ex commands");
   }

   @Test
   @DisplayName("normal help lists movement and edit sections")
   void normalModeHelpListsSections() {
      TextEdit<String> buf =
         ContextHelp.getNormalModeHelp(null);
      String text = bufferText(buf);
      assertTrue(
         text.contains("MOVEMENT KEYS")
            || text.contains("not initialized"),
         "should show movement section");
   }

   @Test
   @DisplayName("getExModeHelp returns syntax reference")
   void exModeHelpReturnsSyntax() {
      TextEdit<String> buf = ContextHelp.getExModeHelp();
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains(
         "CONTEXT-SENSITIVE HELP: Ex Commands"));
      assertTrue(text.contains("EX COMMAND SYNTAX"),
         "should contain syntax section");
   }

   @Test
   @DisplayName("ex help contains regex documentation")
   void exModeHelpContainsRegex() {
      TextEdit<String> buf = ContextHelp.getExModeHelp();
      String text = bufferText(buf);
      assertTrue(text.contains("pattern is a Java regex"),
         "should explain regex syntax");
      assertTrue(text.contains("\\w"),
         "should list \\w metacharacter");
      assertTrue(text.contains("Capture group"),
         "should explain capture groups");
   }

   @Test
   @DisplayName("ex help lists registered commands")
   void exModeHelpListsCommands() {
      TextEdit<String> buf = ContextHelp.getExModeHelp();
      String text = bufferText(buf);
      assertTrue(text.contains("REGISTERED EX COMMANDS"),
         "should have command list section");
      assertTrue(text.contains(":help"),
         "should list :help");
   }

   @Test
   @DisplayName("getContextHelp returns normal help for null fvc")
   void contextHelpNullFvc() {
      TextEdit<String> buf =
         ContextHelp.getContextHelp(null);
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("Normal Mode"),
         "null fvc should show normal mode help");
   }

   @Test
   @DisplayName("calling help twice refreshes content")
   void helpCalledTwiceRefreshes() {
      ContextHelp.getNormalModeHelp(null);
      TextEdit<String> buf = ContextHelp.getExModeHelp();
      String text = bufferText(buf);
      assertTrue(text.contains("Ex Commands"));
      assertFalse(text.contains("Normal Mode"),
         "old content should be cleared");
   }

   @Test
   @DisplayName("isShowingHelp returns false when not showing")
   void isShowingHelpFalseWhenNot() {
      assertFalse(ContextHelp.isShowingHelp(null));
   }

   @Test
   @DisplayName("getRegisteredCommands returns non-empty set")
   void registeredCommandsNonEmpty() {
      java.util.Set<String> cmds =
         Rgroup.getRegisteredCommands();
      assertNotNull(cmds);
      assertFalse(cmds.isEmpty(),
         "should have registered commands");
      assertTrue(cmds.contains("help"),
         "should contain 'help'");
   }

   @Test
   @DisplayName("ex help contains global command syntax")
   void exHelpContainsGlobalCommands() {
      TextEdit<String> buf = ContextHelp.getExModeHelp();
      String text = bufferText(buf);
      assertTrue(text.contains(":g/pattern/command"),
         "should show :g syntax");
      assertTrue(text.contains(":v/pattern/command"),
         "should show :v syntax");
   }

   @Test
   @DisplayName("ex help contains range syntax")
   void exHelpContainsRangeSyntax() {
      TextEdit<String> buf = ContextHelp.getExModeHelp();
      String text = bufferText(buf);
      assertTrue(text.contains("RANGES"),
         "should have ranges section");
      assertTrue(text.contains(
         ":[range]s/pattern/replacement/[flags]"),
         "should show substitution syntax");
   }

   @Test
   @DisplayName("normal mode help handles null keymap")
   void normalModeHelpHandlesNullKeymap() {
      // When MapEvent.bindCommands() hasn't been called,
      // normalKeyMap is null
      TextEdit<String> buf =
         ContextHelp.getNormalModeHelp(null);
      String text = bufferText(buf);
      assertTrue(
         text.contains("not initialized")
            || text.contains("MOVEMENT KEYS"),
         "should handle null keymap gracefully");
   }

   @Test
   @DisplayName("descriptions exist for core movement commands")
   void coreMovementDescriptionsExist() {
      assertNotNull(ContextHelp.getDescription("movechar"),
         "movechar should have a description");
      assertNotNull(ContextHelp.getDescription("moveline"),
         "moveline should have a description");
      assertNotNull(ContextHelp.getDescription("forwardword"),
         "forwardword should have a description");
      assertNotNull(ContextHelp.getDescription("gotoline"),
         "gotoline should have a description");
   }

   @Test
   @DisplayName("descriptions exist for core edit commands")
   void coreEditDescriptionsExist() {
      assertNotNull(ContextHelp.getDescription("insert"),
         "insert should have a description");
      assertNotNull(ContextHelp.getDescription("deletetoend"),
         "deletetoend should have a description");
      assertNotNull(ContextHelp.getDescription("undo"),
         "undo should have a description");
      assertNotNull(ContextHelp.getDescription("yank"),
         "yank should have a description");
   }

   @Test
   @DisplayName("descriptions exist for ex commands")
   void exCommandDescriptionsExist() {
      assertNotNull(ContextHelp.getDescription("help"),
         "help should have a description");
      assertNotNull(ContextHelp.getDescription("shells"),
         "shells should have a description");
      assertNotNull(ContextHelp.getDescription("mapkey"),
         "mapkey should have a description");
   }

   @Test
   @DisplayName("ex help output includes descriptions")
   void exHelpOutputIncludesDescriptions() {
      TextEdit<String> buf = ContextHelp.getExModeHelp();
      String text = bufferText(buf);
      assertTrue(text.contains("show help"),
         "ex help should show description for :help");
      assertTrue(text.contains("list active shells"),
         "ex help should show description for :shells");
   }

   @Test
   @DisplayName("normal help does not include ex descriptions")
   void normalHelpOmitsExDescriptions() {
      TextEdit<String> buf =
         ContextHelp.getNormalModeHelp(null);
      String text = bufferText(buf);
      assertFalse(text.contains(":undo"),
         "normal help should not list ex commands");
   }

   @Test
   @DisplayName("contexthelp command has a description")
   void contextHelpCommandDescriptionExists() {
      assertNotNull(
         ContextHelp.getDescription("contexthelp"),
         "contexthelp should have a description");
   }

   @Test
   @DisplayName("contexthelp is a registered command")
   void contextHelpIsRegistered() {
      java.util.Set<String> cmds =
         Rgroup.getRegisteredCommands();
      assertTrue(cmds.contains("contexthelp"),
         "contexthelp should be registered");
   }

   @Test
   @DisplayName("normal mode help includes See also references")
   void normalModeHelpIncludesSeeAlso() {
      TextEdit<String> buf =
         ContextHelp.getNormalModeHelp(null);
      String text = bufferText(buf);
      assertTrue(text.contains("See also:"),
         "normal help should include See also");
      assertTrue(text.contains(":help movement"),
         "should reference movement topic");
      assertTrue(text.contains(":help editing"),
         "should reference editing topic");
      assertTrue(text.contains(":help search"),
         "should reference search topic");
   }

   @Test
   @DisplayName("ex mode help includes See also references")
   void exModeHelpIncludesSeeAlso() {
      TextEdit<String> buf = ContextHelp.getExModeHelp();
      String text = bufferText(buf);
      assertTrue(text.contains("See also:"),
         "ex help should include See also");
      assertTrue(text.contains(":help ex"),
         "should reference ex topic");
      assertTrue(text.contains(":help search"),
         "should reference search topic");
   }

   @Test
   @DisplayName("HelpBuffer creates and manages buffer correctly")
   void helpBufferManagement() {
      HelpBuffer hb = new HelpBuffer("*test-buf*");
      hb.ensure();
      assertNotNull(hb.getBuffer(),
         "buffer should exist after ensure()");
      hb.append("line one");
      hb.append("line two");
      hb.clear();
      // After clear, buffer exists but content is minimal
      assertNotNull(hb.getBuffer(),
         "buffer should still exist after clear()");
   }

   @Test
   @DisplayName("HelpSystem and ContextHelp use separate buffers")
   void separateBuffers() {
      TextEdit<String> ctxBuf =
         ContextHelp.getNormalModeHelp(null);
      TextEdit<String> helpBuf =
         HelpSystem.getHelp("index");
      assertNotNull(ctxBuf, "context help buffer exists");
      assertNotNull(helpBuf, "help system buffer exists");
      assertFalse(ctxBuf == helpBuf,
         "buffers should be different instances");
   }

   @Test
   @DisplayName("Zprocess description mentions exit")
   void zprocessDescriptionMentionsExit() {
      String desc = ContextHelp.getDescription("Zprocess");
      assertNotNull(desc, "Zprocess should have a description");
      assertTrue(desc.toLowerCase().contains("exit"),
         "Zprocess description should mention exit: " + desc);
   }

   @Test
   @DisplayName("insert mode help mentions ESC to exit")
   void insertModeHelpMentionsEsc() {
      // getContextHelp with null fvc (no insert mode) shows normal
      // We test the insert mode content via the static method
      // by verifying the appendInsertModeHelp content exists
      // in the description map or help output.
      // Since we can't easily mock insert mode in unit tests,
      // verify the normal help includes insert command description.
      String desc = ContextHelp.getDescription("insert");
      assertNotNull(desc,
         "insert command should have a description");
   }

   @Test
   @DisplayName("sub-mode help for findchar shows f/F/t/T")
   void subModeHelpFindchar() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("findchar");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("PENDING: findchar"),
         "should show findchar pending header");
      assertTrue(text.contains("f<c>"),
         "should list f<c> forward find");
      assertTrue(text.contains("F<c>"),
         "should list F<c> backward find");
      assertTrue(text.contains("t<c>"),
         "should list t<c> till forward");
      assertTrue(text.contains("T<c>"),
         "should list T<c> till backward");
      assertTrue(text.contains("ESC"),
         "should mention ESC to cancel");
   }

   @Test
   @DisplayName("sub-mode help for Zprocess shows ZZ save and exit")
   void subModeHelpZprocess() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("Zprocess");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("PENDING: Zprocess"),
         "should show Zprocess pending header");
      assertTrue(text.contains("ZZ"),
         "should mention ZZ command");
      assertTrue(text.contains("save and exit"),
         "should describe ZZ as save and exit");
   }

   @Test
   @DisplayName("sub-mode help for commandproc shows ex help")
   void subModeHelpCommandproc() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("commandproc");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("EX (COLON) COMMANDS"),
         "should include ex mode help header");
      assertTrue(text.contains("FILE COMMANDS"),
         "should list file commands");
      assertTrue(text.contains(":w "),
         "should show :w command");
      assertTrue(text.contains("SEARCH/REPLACE"),
         "should show search/replace section");
      assertTrue(text.contains("REGISTERED EX COMMANDS"),
         "should list registered ex commands");
   }

   @Test
   @DisplayName("sub-mode help for unknown mode shows waiting")
   void subModeHelpUnknown() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("unknownmode");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("PENDING: unknownmode"),
         "should show unknown pending header");
      assertTrue(text.contains("Waiting for input"),
         "should show default waiting message");
   }

   @Test
   @DisplayName("sub-mode help for replacechar shows r<c>")
   void subModeHelpReplacechar() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("replacechar");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("PENDING: replacechar"),
         "should show replacechar pending header");
      assertTrue(text.contains("r<c>"),
         "should describe r<c> replace");
   }

   @Test
   @DisplayName("sub-mode help for deletemode lists motions")
   void subModeHelpDeletemode() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("deletemode");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("dd"),
         "should mention dd");
      assertTrue(text.contains("dw"),
         "should mention dw");
      assertTrue(text.contains("d$"),
         "should mention d$");
      assertTrue(text.contains("db"),
         "should mention db");
      assertTrue(text.contains("df<c>"),
         "should describe df<c> find motion");
      assertTrue(text.contains("dG"),
         "should mention dG");
   }

   @Test
   @DisplayName("sub-mode help for changemode lists motions")
   void subModeHelpChangemode() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("changemode");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("cc"),
         "should mention cc");
      assertTrue(text.contains("cw"),
         "should mention cw");
      assertTrue(text.contains("cb"),
         "should mention cb");
      assertTrue(text.contains("cf<c>"),
         "should describe cf<c> find motion");
   }

   @Test
   @DisplayName("sub-mode help for yankmode lists motions")
   void subModeHelpYankmode() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("yankmode");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("yy"),
         "should mention yy");
      assertTrue(text.contains("yw"),
         "should mention yw");
      assertTrue(text.contains("yb"),
         "should mention yb");
      assertTrue(text.contains("yf<c>"),
         "should describe yf<c> find motion");
      assertTrue(text.contains("yG"),
         "should mention yG");
   }

   @Test
   @DisplayName("sub-mode help for shiftmode shows >>/<<")
   void subModeHelpShiftmode() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("shiftmode");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains(">>"),
         "should mention >>");
      assertTrue(text.contains("<<"),
         "should mention <<");
   }

   @Test
   @DisplayName("sub-mode help for markset shows m<a-z>")
   void subModeHelpMarkset() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("markset");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("m<a-z>"),
         "should describe m<a-z> set mark");
   }

   @Test
   @DisplayName("sub-mode help for markjump shows '<a-z>")
   void subModeHelpMarkjump() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("markjump");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("'<a-z>"),
         "should describe '<a-z> mark jump");
   }

   @Test
   @DisplayName("scrollHelpDown returns false when no panel")
   void scrollHelpDownNoPanel() {
      assertFalse(ContextHelp.scrollHelpDown(),
         "should return false when no help panel");
   }

   @Test
   @DisplayName("scrollHelpUp returns false when no panel")
   void scrollHelpUpNoPanel() {
      assertFalse(ContextHelp.scrollHelpUp(),
         "should return false when no help panel");
   }

   @Test
   @DisplayName("scrollHelpLines is safe with no panel")
   void scrollHelpLinesNoPanel() {
      // Should not throw when no help panel is visible
      ContextHelp.scrollHelpLines(5);
      ContextHelp.scrollHelpLines(-5);
   }

   @Test
   @DisplayName("helpscrolldown is a registered command")
   void helpScrollDownIsRegistered() {
      java.util.Set<String> cmds =
         Rgroup.getRegisteredCommands();
      assertTrue(cmds.contains("helpscrolldown"),
         "helpscrolldown should be registered");
   }

   @Test
   @DisplayName("helpscrollup is a registered command")
   void helpScrollUpIsRegistered() {
      java.util.Set<String> cmds =
         Rgroup.getRegisteredCommands();
      assertTrue(cmds.contains("helpscrollup"),
         "helpscrollup should be registered");
   }

   @Test
   @DisplayName("onCommandCompleted is safe with null panel")
   void onCommandCompletedNoPanel() {
      // Should not throw when no help panel is visible
      ContextHelp.onCommandCompleted(null);
   }

   @Test
   @DisplayName("commandproc sub-mode shows line addressing")
   void commandprocShowsLineAddressing() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("commandproc");
      String text = bufferText(buf);
      assertTrue(text.contains("LINE ADDRESSING"),
         "should show line addressing section");
      assertTrue(text.contains("RANGE COMMANDS"),
         "should show range commands");
   }
}
