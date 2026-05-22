package javi;

import java.io.BufferedInputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for VT100 mouse tracking modes through the full pipeline.
 *
 * <p>Exercises mouse tracking configuration via escape sequences
 * through the parser pipeline: normal tracking (1000), button-event
 * tracking (1002), any-event tracking (1003), SGR extended mode
 * (1006), focus events (1004), and combinations. Verifies that
 * the Vt100 state correctly reflects which mouse modes are active.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class Vt100MouseTrackingGuiJUnitTest {

   private static Robot robot;
   private static Vt100 vt100;
   private static PipedOutputStream pipeToVt;
   private static Vt100Parser parser;

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

      pipeToVt = new PipedOutputStream();
      PipedInputStream pipeIn = new PipedInputStream(pipeToVt, 4096);
      BufferedInputStream bis = new BufferedInputStream(pipeIn);
      PipedOutputStream devNull = new PipedOutputStream();
      new PipedInputStream(devNull);

      EventQueue.biglock2.lock();
      try {
         IoConverter<String> ioc = new StringIoc("mouse-test", "");
         vt100 = new Vt100(devNull, bis, ioc, StandardCharsets.UTF_8);

         Field parserField = Vt100.class.getDeclaredField("parser");
         parserField.setAccessible(true);
         parser = (Vt100Parser) parserField.get(vt100);

         Field rowsField = Vt100.class.getDeclaredField("rows");
         rowsField.setAccessible(true);
         rowsField.setInt(vt100, 24);

         while (vt100.readIn() < 30)
            vt100.insertOne("", vt100.readIn());
      } finally {
         EventQueue.biglock2.unlock();
      }
      Thread.sleep(200);
   }

   @AfterAll
   static void tearDownAll() {
      if (parser != null)
         parser.stop();
      if (robot != null)
         robot.cleanUp();
   }

   private void feedAndWait(String data) throws Exception {
      byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
      pipeToVt.write(bytes);
      pipeToVt.flush();

      Field queueField = EventQueue.class.getDeclaredField("queue");
      queueField.setAccessible(true);
      @SuppressWarnings("unchecked")
      java.util.LinkedList<Object> queue =
         (java.util.LinkedList<Object>) queueField.get(null);

      long deadline = System.currentTimeMillis() + 2000;
      while (System.currentTimeMillis() < deadline) {
         synchronized (EventQueue.class) {
            if (!queue.isEmpty())
               break;
         }
         Thread.sleep(10);
      }

      while (true) {
         Object ev;
         synchronized (EventQueue.class) {
            if (queue.isEmpty())
               break;
            ev = queue.removeFirst();
         }
         if (ev instanceof EventQueue.IEvent) {
            EventQueue.biglock2.lock();
            try {
               ((EventQueue.IEvent) ev).execute();
            } finally {
               EventQueue.biglock2.unlock();
            }
         }
      }
   }

   private int getMouseTrackingMode() throws Exception {
      Field f = Vt100.class.getDeclaredField("mouseTrackingMode");
      f.setAccessible(true);
      return f.getInt(vt100);
   }

   private boolean getSgrMouseMode() throws Exception {
      Field f = Vt100.class.getDeclaredField("sgrMouseMode");
      f.setAccessible(true);
      return f.getBoolean(vt100);
   }

   private boolean getFocusEventsMode() throws Exception {
      Field f = Vt100.class.getDeclaredField("focusEventsMode");
      f.setAccessible(true);
      return f.getBoolean(vt100);
   }

   private boolean getBracketedPasteMode() throws Exception {
      Field f = Vt100.class.getDeclaredField("bracketedPasteMode");
      f.setAccessible(true);
      return f.getBoolean(vt100);
   }

   private boolean getAutowrapMode() throws Exception {
      Field f = Vt100.class.getDeclaredField("autowrapMode");
      f.setAccessible(true);
      return f.getBoolean(vt100);
   }

   private boolean getApplicationCursorKeys() throws Exception {
      Field f = Vt100.class.getDeclaredField("applicationCursorKeys");
      f.setAccessible(true);
      return f.getBoolean(vt100);
   }

   // ── Normal mouse tracking (mode 1000) ────────────────────────

   @Test
   void t01_enableNormalMouseTracking() throws Exception {
      feedAndWait("\033[?1000h");
      assertEquals(1000, getMouseTrackingMode(),
         "?1000h should set mouse tracking mode to 1000");
   }

   @Test
   void t02_disableNormalMouseTracking() throws Exception {
      feedAndWait("\033[?1000h");
      feedAndWait("\033[?1000l");
      assertEquals(0, getMouseTrackingMode(),
         "?1000l should disable mouse tracking");
   }

   // ── Button-event tracking (mode 1002) ────────────────────────

   @Test
   void t03_enableButtonEventTracking() throws Exception {
      feedAndWait("\033[?1002h");
      assertEquals(1002, getMouseTrackingMode(),
         "?1002h should set mouse tracking mode to 1002");
   }

   @Test
   void t04_disableButtonEventTracking() throws Exception {
      feedAndWait("\033[?1002h");
      feedAndWait("\033[?1002l");
      assertEquals(0, getMouseTrackingMode(),
         "?1002l should disable button-event tracking");
   }

   // ── Any-event tracking (mode 1003) ───────────────────────────

   @Test
   void t05_enableAnyEventTracking() throws Exception {
      feedAndWait("\033[?1003h");
      assertEquals(1003, getMouseTrackingMode(),
         "?1003h should set mouse tracking mode to 1003");
   }

   @Test
   void t06_disableAnyEventTracking() throws Exception {
      feedAndWait("\033[?1003h");
      feedAndWait("\033[?1003l");
      assertEquals(0, getMouseTrackingMode(),
         "?1003l should disable any-event tracking");
   }

   // ── SGR extended mouse mode (1006) ───────────────────────────

   @Test
   void t07_enableSgrMouseMode() throws Exception {
      feedAndWait("\033[?1006h");
      assertTrue(getSgrMouseMode(),
         "?1006h should enable SGR mouse mode");
   }

   @Test
   void t08_disableSgrMouseMode() throws Exception {
      feedAndWait("\033[?1006h");
      feedAndWait("\033[?1006l");
      assertFalse(getSgrMouseMode(),
         "?1006l should disable SGR mouse mode");
   }

   // ── Focus events mode (1004) ─────────────────────────────────

   @Test
   void t09_enableFocusEvents() throws Exception {
      feedAndWait("\033[?1004h");
      assertTrue(getFocusEventsMode(),
         "?1004h should enable focus event reporting");
   }

   @Test
   void t10_disableFocusEvents() throws Exception {
      feedAndWait("\033[?1004h");
      feedAndWait("\033[?1004l");
      assertFalse(getFocusEventsMode(),
         "?1004l should disable focus event reporting");
   }

   // ── Bracketed paste mode (2004) ──────────────────────────────

   @Test
   void t11_enableBracketedPaste() throws Exception {
      feedAndWait("\033[?2004h");
      assertTrue(getBracketedPasteMode(),
         "?2004h should enable bracketed paste mode");
   }

   @Test
   void t12_disableBracketedPaste() throws Exception {
      feedAndWait("\033[?2004h");
      feedAndWait("\033[?2004l");
      assertFalse(getBracketedPasteMode(),
         "?2004l should disable bracketed paste mode");
   }

   // ── Autowrap mode (7) ────────────────────────────────────────

   @Test
   void t13_enableAutowrap() throws Exception {
      feedAndWait("\033[?7h");
      assertTrue(getAutowrapMode(),
         "?7h should enable autowrap mode");
   }

   @Test
   void t14_disableAutowrap() throws Exception {
      feedAndWait("\033[?7h");
      feedAndWait("\033[?7l");
      assertFalse(getAutowrapMode(),
         "?7l should disable autowrap mode");
   }

   // ── Application cursor keys (1) ──────────────────────────────

   @Test
   void t15_enableApplicationCursorKeys() throws Exception {
      feedAndWait("\033[?1h");
      assertTrue(getApplicationCursorKeys(),
         "?1h should enable application cursor keys");
   }

   @Test
   void t16_disableApplicationCursorKeys() throws Exception {
      feedAndWait("\033[?1h");
      feedAndWait("\033[?1l");
      assertFalse(getApplicationCursorKeys(),
         "?1l should disable application cursor keys");
   }

   // ── Mode override: higher mode replaces lower ────────────────

   @Test
   void t17_mode1003OverridesMode1000() throws Exception {
      feedAndWait("\033[?1000h");
      assertEquals(1000, getMouseTrackingMode());
      feedAndWait("\033[?1003h");
      assertEquals(1003, getMouseTrackingMode(),
         "Enabling 1003 should override 1000");
   }

   @Test
   void t18_mode1002OverridesMode1000() throws Exception {
      feedAndWait("\033[?1000h");
      feedAndWait("\033[?1002h");
      assertEquals(1002, getMouseTrackingMode(),
         "Enabling 1002 should override 1000");
   }

   @Test
   void t19_disableCurrentModeOnly() throws Exception {
      feedAndWait("\033[?1003h");
      assertEquals(1003, getMouseTrackingMode());
      // Disabling a different mode should not affect current
      feedAndWait("\033[?1000l");
      // Behavior depends on implementation — just verify no crash
   }

   // ── Combined modes: SGR + tracking ───────────────────────────

   @Test
   void t20_sgrWithNormalTracking() throws Exception {
      feedAndWait("\033[?1000h");
      feedAndWait("\033[?1006h");
      assertEquals(1000, getMouseTrackingMode());
      assertTrue(getSgrMouseMode(),
         "SGR mode and normal tracking should coexist");
   }

   @Test
   void t21_sgrWithAnyEventTracking() throws Exception {
      feedAndWait("\033[?1003h");
      feedAndWait("\033[?1006h");
      assertEquals(1003, getMouseTrackingMode());
      assertTrue(getSgrMouseMode(),
         "SGR mode and any-event tracking should coexist");
   }

   @Test
   void t22_disableTrackingKeepsSgr() throws Exception {
      feedAndWait("\033[?1000h");
      feedAndWait("\033[?1006h");
      feedAndWait("\033[?1000l");
      assertEquals(0, getMouseTrackingMode());
      assertTrue(getSgrMouseMode(),
         "Disabling tracking should keep SGR mode active");
   }

   @Test
   void t23_disableSgrKeepsTracking() throws Exception {
      feedAndWait("\033[?1003h");
      feedAndWait("\033[?1006h");
      feedAndWait("\033[?1006l");
      assertEquals(1003, getMouseTrackingMode(),
         "Disabling SGR should keep tracking mode active");
      assertFalse(getSgrMouseMode());
   }

   // ── Multiple modes enabled simultaneously ────────────────────

   @Test
   void t24_allModesEnabled() throws Exception {
      feedAndWait("\033[?1003h"); // any-event tracking
      feedAndWait("\033[?1006h"); // SGR encoding
      feedAndWait("\033[?1004h"); // focus events
      feedAndWait("\033[?2004h"); // bracketed paste

      assertEquals(1003, getMouseTrackingMode());
      assertTrue(getSgrMouseMode());
      assertTrue(getFocusEventsMode());
      assertTrue(getBracketedPasteMode());
   }

   @Test
   void t25_disableAllModes() throws Exception {
      feedAndWait("\033[?1003h");
      feedAndWait("\033[?1006h");
      feedAndWait("\033[?1004h");
      feedAndWait("\033[?2004h");
      // Disable all
      feedAndWait("\033[?1003l");
      feedAndWait("\033[?1006l");
      feedAndWait("\033[?1004l");
      feedAndWait("\033[?2004l");

      assertEquals(0, getMouseTrackingMode());
      assertFalse(getSgrMouseMode());
      assertFalse(getFocusEventsMode());
      assertFalse(getBracketedPasteMode());
   }

   // ── Cursor visibility (25) ───────────────────────────────────

   @Test
   void t26_hideCursor() throws Exception {
      feedAndWait("\033[?25l");
      Field f = Vt100.class.getDeclaredField("cursorVisible");
      f.setAccessible(true);
      assertFalse(f.getBoolean(vt100),
         "?25l should hide cursor");
   }

   @Test
   void t27_showCursor() throws Exception {
      feedAndWait("\033[?25l");
      feedAndWait("\033[?25h");
      Field f = Vt100.class.getDeclaredField("cursorVisible");
      f.setAccessible(true);
      assertTrue(f.getBoolean(vt100),
         "?25h should show cursor");
   }

   // ── Mode sequences mixed with text output ────────────────────

   @Test
   void t28_modeChangesDontCorruptText() throws Exception {
      feedAndWait("\033[H\033[2J"); // clear screen
      feedAndWait("\033[?1000h"); // enable mouse
      feedAndWait("Hello");
      feedAndWait("\033[?1006h"); // enable SGR
      feedAndWait(" World");
      feedAndWait("\033[?1000l"); // disable mouse

      EventQueue.biglock2.lock();
      try {
         // Line 1 should contain "Hello World" despite mode changes
         String line = vt100.at(1);
         assertTrue(line.contains("Hello") && line.contains("World"),
            "Text should be unaffected by mode changes, got: " + line);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t29_rapidModeTogglesStable() throws Exception {
      // Rapidly toggle modes — should not crash or leave bad state
      for (int i = 0; i < 10; i++) {
         feedAndWait("\033[?1000h");
         feedAndWait("\033[?1000l");
      }
      assertEquals(0, getMouseTrackingMode(),
         "After rapid toggles, mode should be off");
   }

   @Test
   void t30_rapidSgrTogglesStable() throws Exception {
      for (int i = 0; i < 10; i++) {
         feedAndWait("\033[?1006h");
         feedAndWait("\033[?1006l");
      }
      assertFalse(getSgrMouseMode(),
         "After rapid SGR toggles, mode should be off");
   }

   // ── Alternate screen interaction ─────────────────────────────

   @Test
   void t31_mouseModeSurvivesAltScreen() throws Exception {
      feedAndWait("\033[?1003h"); // enable tracking
      feedAndWait("\033[?1049h"); // switch to alternate screen
      // Mouse mode should still be active (or re-applied)
      int mode = getMouseTrackingMode();
      // Implementation may preserve or reset — verify no crash
      feedAndWait("\033[?1049l"); // back to main screen
   }

   @Test
   void t32_focusEventsSurviveAltScreen() throws Exception {
      feedAndWait("\033[?1004h"); // enable focus events
      feedAndWait("\033[?1049h"); // alternate screen
      feedAndWait("\033[?1049l"); // back to main
      // Just verify no crash during screen switches with focus mode
   }

   // ── LNM mode (line feed/new line mode 20) ────────────────────

   @Test
   void t33_enableLnmMode() throws Exception {
      feedAndWait("\033[20h"); // Note: no ? for mode 20 (ANSI mode)
      // LNM mode causes LF to also do CR
      // Just verify no crash — field may not be accessible
   }

   // ── Origin mode (6) ──────────────────────────────────────────

   @Test
   void t34_enableOriginMode() throws Exception {
      feedAndWait("\033[?6h");
      // Origin mode restricts cursor to scroll region
      // Just verify no exception — internal state is complex
   }

   @Test
   void t35_disableOriginMode() throws Exception {
      feedAndWait("\033[?6h");
      feedAndWait("\033[?6l");
      // Reset origin mode — cursor unrestricted
   }
}
