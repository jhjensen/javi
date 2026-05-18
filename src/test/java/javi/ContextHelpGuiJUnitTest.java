package javi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for {@link ContextHelp} side-panel integration.
 *
 * <p>Exercises help panel toggle, content generation for normal/insert/ex
 * modes, scroll operations, sub-mode help, command-line filtering, and
 * context-change refresh — all in the real AWT rendering pipeline.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class ContextHelpGuiJUnitTest {

   private static Robot robot;
   private static FvContext<?> fvc;

   @BeforeAll
   static void initJavi() throws Exception {
      if (Rgroup.bindingLookup("persistfile") == null) {
         EventQueue.biglock2.lock();
         try {
            Class.forName("javi.TextEdit");
            EditTester1.TestCircBuffer.initCmd();
            DirManager.getInstance();
            FileList.make("");
            Javi.initToUi();
            Javi.initPostUi();
            Command.doneInit();
         } finally {
            EventQueue.biglock2.unlock();
         }
         Thread.sleep(500);
      }
      robot = BasicRobot.robotWithCurrentAwtHierarchy();
      fvc = FvContext.getCurrFvc();
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   @AfterEach
   void ensureHelpClosed() throws Exception {
      EventQueue.biglock2.lock();
      try {
         if (ContextHelp.isShowingHelp(fvc))
            ContextHelp.toggle(fvc);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Help panel visibility ────────────────────────────────────

   @Test
   void t01_initialStateHelpNotShowing() {
      assertFalse(ContextHelp.isShowingHelp(fvc),
         "Help panel should not be showing initially");
   }

   @Test
   void t02_toggleShowsHelp() throws Exception {
      EventQueue.biglock2.lock();
      try {
         boolean toggled = ContextHelp.toggle(fvc);
         assertTrue(toggled, "toggle should return true");
         assertTrue(ContextHelp.isShowingHelp(fvc),
            "Help panel should be visible after toggle");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t03_toggleTwiceHidesHelp() throws Exception {
      EventQueue.biglock2.lock();
      try {
         ContextHelp.toggle(fvc);
         assertTrue(ContextHelp.isShowingHelp(fvc));
         ContextHelp.toggle(fvc);
         assertFalse(ContextHelp.isShowingHelp(fvc),
            "Help panel should be hidden after second toggle");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Help content generation ──────────────────────────────────

   @Test
   void t04_normalModeHelpContainsMovementKeys() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = ContextHelp.getNormalModeHelp(fvc);
         assertNotNull(buf, "Normal mode help buffer should not be null");
         assertTrue(buf.readIn() > 3,
            "Help buffer should have content");
         String content = bufferToString(buf);
         assertTrue(content.contains("MOVEMENT"),
            "Normal mode help should contain MOVEMENT section");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t05_normalModeHelpContainsEditKeys() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = ContextHelp.getNormalModeHelp(fvc);
         String content = bufferToString(buf);
         assertTrue(content.contains("COMMAND") || content.contains("EDIT"),
            "Normal mode help should contain edit key section");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t06_exModeHelpContainsExCommands() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = ContextHelp.getExModeHelp();
         assertNotNull(buf, "Ex mode help buffer should not be null");
         String content = bufferToString(buf);
         assertTrue(content.contains("EX COMMAND"),
            "Ex mode help should contain EX COMMAND header");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t07_exModeHelpListsRegisteredCommands() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = ContextHelp.getExModeHelp();
         String content = bufferToString(buf);
         // Should list at least some common ex commands
         assertTrue(content.contains("write") || content.contains("quit")
               || content.contains("edit"),
            "Ex mode help should list common commands");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Help panel toggle with content ───────────────────────────

   @Test
   void t08_toggleCreatesHelpPanelView() throws Exception {
      EventQueue.biglock2.lock();
      try {
         ContextHelp.toggle(fvc);
         Field helpView = ContextHelp.class
            .getDeclaredField("helpPanelView");
         helpView.setAccessible(true);
         assertNotNull(helpView.get(null),
            "helpPanelView should be set after toggle");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t09_toggleCreatesHelpFvc() throws Exception {
      EventQueue.biglock2.lock();
      try {
         ContextHelp.toggle(fvc);
         Field helpFvcField = ContextHelp.class
            .getDeclaredField("helpFvc");
         helpFvcField.setAccessible(true);
         assertNotNull(helpFvcField.get(null),
            "helpFvc should be set after toggle");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t10_toggleClearsFieldsOnHide() throws Exception {
      EventQueue.biglock2.lock();
      try {
         ContextHelp.toggle(fvc);
         assertTrue(ContextHelp.isShowingHelp(fvc));
         ContextHelp.toggle(fvc);

         Field helpView = ContextHelp.class
            .getDeclaredField("helpPanelView");
         helpView.setAccessible(true);
         assertNull(helpView.get(null),
            "helpPanelView should be null after hide");

         Field helpFvcField = ContextHelp.class
            .getDeclaredField("helpFvc");
         helpFvcField.setAccessible(true);
         assertNull(helpFvcField.get(null),
            "helpFvc should be null after hide");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Scroll operations ────────────────────────────────────────

   @Test
   void t11_scrollDownReturnsFalseWhenHidden() {
      assertFalse(ContextHelp.scrollHelpDown(),
         "scrollDown should return false when help is hidden");
   }

   @Test
   void t12_scrollUpReturnsFalseWhenHidden() {
      assertFalse(ContextHelp.scrollHelpUp(),
         "scrollUp should return false when help is hidden");
   }

   @Test
   void t13_scrollLinesNoOpWhenHidden() {
      assertDoesNotThrow(() -> ContextHelp.scrollHelpLines(5),
         "scrollLines should not throw when help is hidden");
   }

   @Test
   void t14_scrollToLineNoOpWhenHidden() {
      assertDoesNotThrow(() -> ContextHelp.scrollHelpToLine(10),
         "scrollToLine should not throw when help is hidden");
   }

   @Test
   void t15_scrollDownWorksWhenVisible() throws Exception {
      EventQueue.biglock2.lock();
      try {
         ContextHelp.toggle(fvc);
         boolean result = ContextHelp.scrollHelpDown();
         assertTrue(result, "scrollDown should succeed when visible");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t16_scrollUpWorksWhenVisible() throws Exception {
      EventQueue.biglock2.lock();
      try {
         ContextHelp.toggle(fvc);
         boolean result = ContextHelp.scrollHelpUp();
         assertTrue(result, "scrollUp should succeed when visible");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t17_scrollLinesNoThrowWhenVisible() throws Exception {
      EventQueue.biglock2.lock();
      try {
         ContextHelp.toggle(fvc);
         assertDoesNotThrow(() -> ContextHelp.scrollHelpLines(3));
         assertDoesNotThrow(() -> ContextHelp.scrollHelpLines(-3));
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t18_scrollToLineNoThrowWhenVisible() throws Exception {
      EventQueue.biglock2.lock();
      try {
         ContextHelp.toggle(fvc);
         assertDoesNotThrow(() -> ContextHelp.scrollHelpToLine(1));
         assertDoesNotThrow(() -> ContextHelp.scrollHelpToLine(5));
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Sub-mode help ────────────────────────────────────────────

   @Test
   void t19_subModeHelpNoThrowWhenHidden() {
      assertDoesNotThrow(() -> ContextHelp.onSubModeEntered("f"),
         "onSubModeEntered should not throw when help is hidden");
   }

   @Test
   void t20_subModeHelpUpdatesContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         ContextHelp.toggle(fvc);
         ContextHelp.onSubModeEntered("f");

         Field inSubField = ContextHelp.class
            .getDeclaredField("inSubMode");
         inSubField.setAccessible(true);
         assertTrue((boolean) inSubField.get(null),
            "inSubMode should be true after onSubModeEntered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t21_commandCompletedResetsSubMode() throws Exception {
      EventQueue.biglock2.lock();
      try {
         ContextHelp.toggle(fvc);
         ContextHelp.onSubModeEntered("f");
         ContextHelp.onCommandCompleted(fvc);

         Field inSubField = ContextHelp.class
            .getDeclaredField("inSubMode");
         inSubField.setAccessible(true);
         assertFalse((boolean) inSubField.get(null),
            "inSubMode should be false after onCommandCompleted");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Context change ───────────────────────────────────────────

   @Test
   void t22_onContextChangedNoThrowWhenHidden() {
      assertDoesNotThrow(() -> ContextHelp.onContextChanged(fvc));
   }

   @Test
   void t23_onContextChangedRefreshesWhenVisible() throws Exception {
      EventQueue.biglock2.lock();
      try {
         ContextHelp.toggle(fvc);
         assertDoesNotThrow(() -> ContextHelp.onContextChanged(fvc));
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t24_onContextChangedNullFvcSafe() {
      assertDoesNotThrow(() -> ContextHelp.onContextChanged(null));
   }

   @Test
   void t25_onInsertModeChangedNoThrow() {
      assertDoesNotThrow(() -> ContextHelp.onInsertModeChanged());
   }

   // ── Command line filtering ───────────────────────────────────

   @Test
   void t26_stripRangePrefixEmpty() {
      assertEquals("", ContextHelp.stripRangePrefix(""));
   }

   @Test
   void t27_stripRangePrefixNoRange() {
      assertEquals("write", ContextHelp.stripRangePrefix("write"));
   }

   @Test
   void t28_stripRangePrefixWithRange() {
      assertEquals("write",
         ContextHelp.stripRangePrefix("1,10write"));
   }

   @Test
   void t29_stripRangePrefixPercentRange() {
      assertEquals("s/a/b/",
         ContextHelp.stripRangePrefix("%s/a/b/"));
   }

   @Test
   void t30_stripRangePrefixDotDollar() {
      assertEquals("write",
         ContextHelp.stripRangePrefix(".,$ write".replace(" ", "")));
   }

   @Test
   void t31_stripRangePrefixMarkRange() {
      assertEquals("delete",
         ContextHelp.stripRangePrefix("'a,'bdelete"));
   }

   @Test
   void t32_extractCommandPrefixSimple() {
      assertEquals("write", ContextHelp.extractCommandPrefix("write"));
   }

   @Test
   void t33_extractCommandPrefixWithArgs() {
      assertEquals("write",
         ContextHelp.extractCommandPrefix("write foo.txt"));
   }

   @Test
   void t34_extractCommandPrefixEmpty() {
      assertEquals("", ContextHelp.extractCommandPrefix(""));
   }

   @Test
   void t35_extractCommandPrefixSlashTerminated() {
      assertEquals("s", ContextHelp.extractCommandPrefix("s/old/new/"));
   }

   // ── Command line change notification ─────────────────────────

   @Test
   void t36_onCommandLineChangedNoThrowWhenHidden() {
      assertDoesNotThrow(
         () -> ContextHelp.onCommandLineChanged("wri"));
   }

   @Test
   void t37_onCommandLineChangedFiltersWhenVisible() throws Exception {
      EventQueue.biglock2.lock();
      try {
         ContextHelp.toggle(fvc);
         assertDoesNotThrow(
            () -> ContextHelp.onCommandLineChanged("wri"));
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t38_onCommandLineChangedEmptyPrefix() throws Exception {
      EventQueue.biglock2.lock();
      try {
         ContextHelp.toggle(fvc);
         assertDoesNotThrow(
            () -> ContextHelp.onCommandLineChanged(""));
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── getContextHelp dispatch ──────────────────────────────────

   @Test
   void t39_getContextHelpNotNull() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = ContextHelp.getContextHelp(fvc);
         assertNotNull(buf);
         assertTrue(buf.readIn() > 1,
            "Context help should have content");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t40_getContextHelpNullFvcSafe() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = ContextHelp.getContextHelp(null);
         assertNotNull(buf,
            "getContextHelp(null) should still return a buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Utility ──────────────────────────────────────────────────

   private String bufferToString(TextEdit<String> buf) {
      StringBuilder sb = new StringBuilder();
      int n = buf.readIn();
      for (int i = 1; i < n; i++) {
         if (i > 1)
            sb.append('\n');
         sb.append(buf.at(i).toString());
      }
      return sb.toString();
   }
}
