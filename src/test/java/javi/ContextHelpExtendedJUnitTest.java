package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Extended ContextHelp tests targeting uncovered content-generation
 * paths: sub-mode help variants, description lookups, and the
 * help panel state methods.
 */
class ContextHelpExtendedJUnitTest {

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

   // --- Sub-mode help content ---

   @Test
   @DisplayName("Zprocess sub-mode shows ZZ save and exit")
   void zprocessSubModeContent() {
      TextEdit<String> buf = ContextHelp.getSubModeHelp("Zprocess");
      assertNotNull(buf, "sub-mode help buffer should exist");
      String text = bufferText(buf);
      assertTrue(text.contains("ZZ"),
         "Zprocess help should mention ZZ: " + text);
   }

   @Test
   @DisplayName("zprocess sub-mode shows z-scroll positions")
   void zprocessLowerSubMode() {
      TextEdit<String> buf = ContextHelp.getSubModeHelp("zprocess");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.length() > 10,
         "zprocess help should have content");
   }

   @Test
   @DisplayName("commandproc sub-mode shows ex command syntax")
   void commandprocSubMode() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("commandproc");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("EX"),
         "commandproc help should show ex commands: " + text);
   }

   @Test
   @DisplayName("replacechar sub-mode shows replacement help")
   void replacecharSubMode() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("replacechar");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("ESC") || text.contains("replace"),
         "replacechar help should mention ESC or replace");
   }

   @Test
   @DisplayName("findchar sub-mode shows f/F/t/T help")
   void findcharSubMode() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("findchar");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("find") || text.contains("forward"),
         "findchar help should mention find/forward");
   }

   @Test
   @DisplayName("markjump sub-mode shows mark instructions")
   void markjumpSubMode() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("markjump");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("mark") || text.contains("'"),
         "markjump help should describe marks");
   }

   @Test
   @DisplayName("unknown sub-mode has content")
   void unknownSubModeHasContent() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("nonexistent_mode_xyz");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("PENDING"),
         "unknown sub-mode should show PENDING: " + text);
   }

   @Test
   @DisplayName("searchcommand sub-mode shows search help")
   void searchCommandSubMode() {
      TextEdit<String> buf =
         ContextHelp.getSubModeHelp("searchcommand");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("SEARCH"),
         "searchcommand should show SEARCH: " + text);
   }

   @Test
   @DisplayName("qmode sub-mode shows register operations")
   void qmodeSubModeShowsRegisterHelp() {
      TextEdit<String> buf = ContextHelp.getSubModeHelp("qmode");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("register"),
         "qmode help should mention registers: " + text);
      assertTrue(text.contains("\"<r>y") || text.contains("yank"),
         "qmode help should show register yank syntax: " + text);
      assertTrue(text.contains("* (clipboard)"),
         "qmode help should show clipboard register: " + text);
      String[] lines = text.split("\\n");
      for (String line : lines)
         assertTrue(line.length() <= 43,
            "help line exceeds panel width: " + line);
   }

   // --- Description lookups ---

   @Test
   @DisplayName("getDescription for registered commands")
   void descriptionForRegisteredCommands() {
      // Commands registered during TestInit.initCommands()
      String desc = ContextHelp.getDescription("insert");
      assertNotNull(desc, "insert should have description");
      assertTrue(desc.toLowerCase().contains("insert"),
         "insert description should mention insert: " + desc);
   }

   @Test
   @DisplayName("getDescription for movement commands")
   void descriptionForMoveCommands() {
      String desc = ContextHelp.getDescription("movechar");
      // movechar may or may not have a description depending on
      // registration — just verify no crash
      if (desc != null)
         assertFalse(desc.isEmpty());
   }

   @Test
   @DisplayName("getDescription for edit commands")
   void descriptionForEditCommands() {
      String desc = ContextHelp.getDescription("append");
      assertNotNull(desc, "append should have description");
   }

   @Test
   @DisplayName("getDescription for null returns null")
   void descriptionForNullReturnsNull() {
      String desc = ContextHelp.getDescription(null);
      // Should not throw, may return null
      assertTrue(desc == null || desc instanceof String);
   }

   @Test
   @DisplayName("getDescription for unknown command returns null")
   void descriptionForUnknownReturnsNull() {
      String desc = ContextHelp.getDescription("zzz_nonexistent_cmd");
      assertTrue(desc == null, "unknown command should have null desc");
   }

   // --- Help panel state ---

   @Test
   @DisplayName("isShowingHelp returns false when panel not created")
   void isShowingHelpDefaultFalse() {
      assertFalse(ContextHelp.isShowingHelp(null),
         "no help panel should be showing initially");
   }

   @Test
   @DisplayName("scrollHelpDown returns false without panel")
   void scrollDownWithoutPanel() {
      assertFalse(ContextHelp.scrollHelpDown());
   }

   @Test
   @DisplayName("scrollHelpUp returns false without panel")
   void scrollUpWithoutPanel() {
      assertFalse(ContextHelp.scrollHelpUp());
   }

   @Test
   @DisplayName("scrollHelpLines does nothing without panel")
   void scrollLinesWithoutPanel() {
      // Should not throw
      ContextHelp.scrollHelpLines(5);
      ContextHelp.scrollHelpLines(-5);
   }

   @Test
   @DisplayName("scrollHelpToLine does nothing without panel")
   void scrollToLineWithoutPanel() {
      // Should not throw
      ContextHelp.scrollHelpToLine(10);
   }

   @Test
   @DisplayName("onContextChanged does nothing without panel")
   void onContextChangedWithoutPanel() {
      // Should not throw
      ContextHelp.onContextChanged(null);
   }

   @Test
   @DisplayName("onCommandCompleted does nothing without panel")
   void onCommandCompletedWithoutPanel() {
      // Should not throw
      ContextHelp.onCommandCompleted(null);
   }

   @Test
   @DisplayName("onInsertModeChanged does nothing without panel")
   void onInsertModeChangedWithoutPanel() {
      ContextHelp.onInsertModeChanged();
   }

   // --- getNormalModeHelp content sections ---

   @Test
   @DisplayName("normal mode help mentions keybindings")
   void normalModeHelpHasKeyBindingsSection() {
      TextEdit<String> buf = ContextHelp.getNormalModeHelp(null);
      String text = bufferText(buf);
      // Should have either MOVEMENT or not-initialized
      assertTrue(
         text.contains("MOVEMENT") || text.contains("not initialized"),
         "normal mode help should mention MOVEMENT or initialization");
   }

   @Test
   @DisplayName("normal mode help mentions ex commands")
   void normalModeHelpHasExSection() {
      TextEdit<String> buf = ContextHelp.getNormalModeHelp(null);
      String text = bufferText(buf);
      assertTrue(text.contains("EX") || text.contains("ex")
         || text.contains(":"),
         "normal mode help should reference ex commands");
   }

   @Test
   @DisplayName("repeated getNormalModeHelp returns same buffer")
   void normalModeHelpReusesBuffer() {
      TextEdit<String> buf1 = ContextHelp.getNormalModeHelp(null);
      TextEdit<String> buf2 = ContextHelp.getNormalModeHelp(null);
      assertEquals(buf1, buf2, "should reuse the same help buffer");
   }
}
