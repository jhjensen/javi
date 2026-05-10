package javi;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the escape sequence output of Vt100 input-protocol methods.
 *
 * <p>Tests {@code sendMouseEvent()}, {@code sendText()},
 * {@code sendFocusEvent()}, and {@code sendResponse()} by capturing
 * bytes written to the PTY output stream and comparing against the
 * expected VT100/xterm escape sequences.</p>
 *
 * <p>Also tests mode flag interactions: SGR vs legacy mouse encoding,
 * bracketed paste wrapping, and focus event gating.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class Vt100InputProtocolGuiJUnitTest {

   private static Robot robot;
   private static Vt100 vt100;
   private static ByteArrayOutputStream capture;
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

      capture = new ByteArrayOutputStream();

      // Input side: pipe that blocks (parser reads from this)
      PipedOutputStream dummyInput = new PipedOutputStream();
      PipedInputStream pipeIn = new PipedInputStream(dummyInput);
      BufferedInputStream bis = new BufferedInputStream(pipeIn);

      EventQueue.biglock2.lock();
      try {
         IoConverter<String> ioc = new StringIoc("input-protocol-test", "");
         vt100 = new Vt100(capture, bis, ioc, StandardCharsets.UTF_8);

         Field parserField = Vt100.class.getDeclaredField("parser");
         parserField.setAccessible(true);
         parser = (Vt100Parser) parserField.get(vt100);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @AfterAll
   static void tearDownAll() {
      if (parser != null)
         parser.stop();
      if (robot != null)
         robot.cleanUp();
   }

   @BeforeEach
   void resetCapture() {
      capture.reset();
      // Reset all mode flags to defaults
      vt100.setSgrMouseMode(false);
      vt100.setBracketedPasteMode(false);
      vt100.setFocusEventsMode(false);
      vt100.setMouseTracking(1000, false);
      vt100.setMouseTracking(1002, false);
      vt100.setMouseTracking(1003, false);
   }

   private String captured() {
      return capture.toString(StandardCharsets.UTF_8);
   }

   // ── sendResponse ─────────────────────────────────────────────

   @Test
   void t01_sendResponseWritesExactString() {
      vt100.sendResponse("\033[?62;22c");
      assertEquals("\033[?62;22c", captured());
   }

   @Test
   void t02_sendResponseCPR() {
      vt100.sendResponse("\033[5;10R");
      assertEquals("\033[5;10R", captured());
   }

   @Test
   void t03_sendResponseEmptyString() {
      vt100.sendResponse("");
      assertEquals("", captured());
   }

   @Test
   void t04_sendResponseMultipleSequential() {
      vt100.sendResponse("\033[?62;22c");
      vt100.sendResponse("\033[1;1R");
      assertEquals("\033[?62;22c\033[1;1R", captured());
   }

   // ── sendFocusEvent ───────────────────────────────────────────

   @Test
   void t05_sendFocusEventDisabledNoOutput() {
      vt100.setFocusEventsMode(false);
      vt100.sendFocusEvent(true);
      assertEquals("", captured(),
         "Focus event should be suppressed when mode is off");
   }

   @Test
   void t06_sendFocusEventGainedWritesEscI() {
      vt100.setFocusEventsMode(true);
      vt100.sendFocusEvent(true);
      assertEquals("\033[I", captured());
   }

   @Test
   void t07_sendFocusEventLostWritesEscO() {
      vt100.setFocusEventsMode(true);
      vt100.sendFocusEvent(false);
      assertEquals("\033[O", captured());
   }

   @Test
   void t08_sendFocusEventGainLostSequence() {
      vt100.setFocusEventsMode(true);
      vt100.sendFocusEvent(true);
      vt100.sendFocusEvent(false);
      assertEquals("\033[I\033[O", captured());
   }

   @Test
   void t09_sendFocusEventTogglingMode() {
      vt100.setFocusEventsMode(true);
      vt100.sendFocusEvent(true);
      vt100.setFocusEventsMode(false);
      vt100.sendFocusEvent(false); // should be suppressed
      assertEquals("\033[I", captured(),
         "Second event suppressed after mode disabled");
   }

   // ── sendText (bracketed paste) ───────────────────────────────

   @Test
   void t10_sendTextPlainNoPaste() {
      vt100.setBracketedPasteMode(false);
      vt100.sendText("hello");
      assertEquals("hello", captured());
   }

   @Test
   void t11_sendTextWithBracketedPaste() {
      vt100.setBracketedPasteMode(true);
      vt100.sendText("hello");
      assertEquals("\033[200~hello\033[201~", captured());
   }

   @Test
   void t12_sendTextEmptyWithPaste() {
      vt100.setBracketedPasteMode(true);
      vt100.sendText("");
      assertEquals("\033[200~\033[201~", captured());
   }

   @Test
   void t13_sendTextMultilineWithPaste() {
      vt100.setBracketedPasteMode(true);
      vt100.sendText("line1\nline2\nline3");
      assertEquals("\033[200~line1\nline2\nline3\033[201~", captured());
   }

   @Test
   void t14_sendTextTogglePasteModeMidStream() {
      vt100.setBracketedPasteMode(false);
      vt100.sendText("plain");
      vt100.setBracketedPasteMode(true);
      vt100.sendText("pasted");
      assertEquals("plain\033[200~pasted\033[201~", captured());
   }

   @Test
   void t15_sendTextUnicodeContent() {
      vt100.setBracketedPasteMode(false);
      vt100.sendText("\u00e9\u00e8\u00ea"); // accented chars
      assertEquals("\u00e9\u00e8\u00ea", captured());
   }

   // ── sendMouseEvent (legacy X10 encoding) ─────────────────────

   @Test
   void t16_mouseEventLegacyLeftPress() {
      vt100.setSgrMouseMode(false);
      vt100.sendMouseEvent(0, 1, 1, true);
      String result = captured();
      assertEquals("\033[M", result.substring(0, 3));
      assertEquals((char) (0 + 32), result.charAt(3)); // button
      assertEquals((char) (1 + 32), result.charAt(4)); // col
      assertEquals((char) (1 + 32), result.charAt(5)); // row
   }

   @Test
   void t17_mouseEventLegacyRightPress() {
      vt100.setSgrMouseMode(false);
      vt100.sendMouseEvent(2, 10, 5, true);
      String result = captured();
      assertEquals("\033[M", result.substring(0, 3));
      assertEquals((char) (2 + 32), result.charAt(3));  // button=2
      assertEquals((char) (10 + 32), result.charAt(4)); // col=10
      assertEquals((char) (5 + 32), result.charAt(5));  // row=5
   }

   @Test
   void t18_mouseEventLegacyScrollUp() {
      vt100.setSgrMouseMode(false);
      vt100.sendMouseEvent(64, 20, 15, true);
      String result = captured();
      assertEquals("\033[M", result.substring(0, 3));
      assertEquals((char) (64 + 32), result.charAt(3)); // scrollUp
   }

   @Test
   void t19_mouseEventLegacyColTooLarge() {
      // Legacy encoding limited to col/row <= 222 (+ 32 = 254)
      vt100.setSgrMouseMode(false);
      vt100.sendMouseEvent(0, 223, 1, true);
      assertEquals("", captured(),
         "Should silently drop if col > 222");
   }

   @Test
   void t20_mouseEventLegacyRowTooLarge() {
      vt100.setSgrMouseMode(false);
      vt100.sendMouseEvent(0, 1, 223, true);
      assertEquals("", captured(),
         "Should silently drop if row > 222");
   }

   @Test
   void t21_mouseEventLegacyMaxValidPosition() {
      vt100.setSgrMouseMode(false);
      vt100.sendMouseEvent(0, 222, 222, true);
      String result = captured();
      assertEquals(6, result.length());
      assertEquals((char) (222 + 32), result.charAt(4)); // col
      assertEquals((char) (222 + 32), result.charAt(5)); // row
   }

   // ── sendMouseEvent (SGR mode 1006 encoding) ─────────────────

   @Test
   void t22_mouseEventSgrLeftPress() {
      vt100.setSgrMouseMode(true);
      vt100.sendMouseEvent(0, 1, 1, true);
      assertEquals("\033[<0;1;1M", captured());
   }

   @Test
   void t23_mouseEventSgrLeftRelease() {
      vt100.setSgrMouseMode(true);
      vt100.sendMouseEvent(0, 1, 1, false);
      assertEquals("\033[<0;1;1m", captured()); // lowercase m = release
   }

   @Test
   void t24_mouseEventSgrRightPress() {
      vt100.setSgrMouseMode(true);
      vt100.sendMouseEvent(2, 50, 30, true);
      assertEquals("\033[<2;50;30M", captured());
   }

   @Test
   void t25_mouseEventSgrScrollDown() {
      vt100.setSgrMouseMode(true);
      vt100.sendMouseEvent(65, 10, 20, true);
      assertEquals("\033[<65;10;20M", captured());
   }

   @Test
   void t26_mouseEventSgrLargeCoordinates() {
      // SGR mode has no coordinate limit (unlike legacy)
      vt100.setSgrMouseMode(true);
      vt100.sendMouseEvent(0, 500, 300, true);
      assertEquals("\033[<0;500;300M", captured());
   }

   @Test
   void t27_mouseEventSgrMiddleButton() {
      vt100.setSgrMouseMode(true);
      vt100.sendMouseEvent(1, 80, 24, true);
      assertEquals("\033[<1;80;24M", captured());
   }

   @Test
   void t28_mouseEventSgrPressReleasePair() {
      vt100.setSgrMouseMode(true);
      vt100.sendMouseEvent(0, 10, 5, true);
      vt100.sendMouseEvent(0, 10, 5, false);
      assertEquals("\033[<0;10;5M\033[<0;10;5m", captured());
   }

   // ── Mode flag interactions ───────────────────────────────────

   @Test
   void t29_mouseTrackingModeCycle() {
      assertFalse(vt100.isMouseTrackingEnabled());
      vt100.setMouseTracking(1000, true);
      assertTrue(vt100.isMouseTrackingEnabled());
      vt100.setMouseTracking(1000, false);
      assertFalse(vt100.isMouseTrackingEnabled());
   }

   @Test
   void t30_mouseTrackingModeUpgrade() {
      vt100.setMouseTracking(1000, true);
      assertTrue(vt100.isMouseTrackingEnabled());
      // Upgrade to any-event tracking
      vt100.setMouseTracking(1003, true);
      assertTrue(vt100.isMouseTrackingEnabled());
      // Disabling old mode shouldn't affect new mode
      vt100.setMouseTracking(1000, false);
      assertTrue(vt100.isMouseTrackingEnabled(),
         "Mode 1003 still active after disabling 1000");
   }

   @Test
   void t31_sgrModeSwitch() {
      // Switch between legacy and SGR encoding mid-stream
      vt100.setSgrMouseMode(false);
      vt100.sendMouseEvent(0, 5, 5, true);
      int legacyLen = captured().length();
      assertEquals(6, legacyLen, "Legacy event is 6 chars");

      capture.reset();
      vt100.setSgrMouseMode(true);
      vt100.sendMouseEvent(0, 5, 5, true);
      assertEquals("\033[<0;5;5M", captured());
   }

   @Test
   void t32_reverseWrapModeToggle() throws Exception {
      Field f = Vt100.class.getDeclaredField("reverseWrapMode");
      f.setAccessible(true);
      assertFalse(f.getBoolean(vt100));
      vt100.setReverseWrapMode(true);
      assertTrue(f.getBoolean(vt100));
      vt100.setReverseWrapMode(false);
      assertFalse(f.getBoolean(vt100));
   }

   @Test
   void t33_reverseWrapExtendModeToggle() throws Exception {
      Field f = Vt100.class.getDeclaredField("reverseWrapExtendMode");
      f.setAccessible(true);
      assertFalse(f.getBoolean(vt100));
      vt100.setReverseWrapExtendMode(true);
      assertTrue(f.getBoolean(vt100));
      vt100.setReverseWrapExtendMode(false);
      assertFalse(f.getBoolean(vt100));
   }

   @Test
   void t34_applicationCursorKeysFlag() throws Exception {
      Field f = Vt100.class.getDeclaredField("applicationCursorKeys");
      f.setAccessible(true);
      assertFalse(f.getBoolean(vt100));
      vt100.setApplicationCursorKeys(true);
      assertTrue(f.getBoolean(vt100));
      vt100.setApplicationCursorKeys(false);
      assertFalse(f.getBoolean(vt100));
   }

   @Test
   void t35_cursorVisibilityToggle() {
      assertTrue(vt100.isCursorVisible());
      vt100.setCursorVisible(false);
      assertFalse(vt100.isCursorVisible());
      vt100.setCursorVisible(true);
      assertTrue(vt100.isCursorVisible());
   }

   @Test
   void t36_cursorBlinkModeToggle() throws Exception {
      Field f = Vt100.class.getDeclaredField("cursorBlinkMode");
      f.setAccessible(true);
      assertFalse(f.getBoolean(vt100));
      vt100.setCursorBlinkMode(true);
      assertTrue(f.getBoolean(vt100));
      vt100.setCursorBlinkMode(false);
      assertFalse(f.getBoolean(vt100));
   }

   // ── Combined protocol sequences ─────────────────────────────

   @Test
   void t37_focusThenMouseSequence() {
      vt100.setFocusEventsMode(true);
      vt100.setSgrMouseMode(true);
      vt100.sendFocusEvent(true);
      vt100.sendMouseEvent(0, 1, 1, true);
      assertEquals("\033[I\033[<0;1;1M", captured());
   }

   @Test
   void t38_responseAndTextInterleaved() {
      vt100.sendResponse("\033[?62;22c");
      vt100.sendText("typed");
      assertEquals("\033[?62;22c" + "typed", captured());
   }

   @Test
   void t39_pastedTextWithMouseAfter() {
      vt100.setBracketedPasteMode(true);
      vt100.setSgrMouseMode(true);
      vt100.sendText("pasted");
      vt100.sendMouseEvent(0, 5, 5, true);
      assertEquals(
         "\033[200~pasted\033[201~\033[<0;5;5M",
         captured());
   }

   @Test
   void t40_allModesActiveProtocolMix() {
      vt100.setFocusEventsMode(true);
      vt100.setBracketedPasteMode(true);
      vt100.setSgrMouseMode(true);
      vt100.setMouseTracking(1003, true);

      vt100.sendFocusEvent(true);
      vt100.sendText("paste");
      vt100.sendMouseEvent(0, 10, 10, true);
      vt100.sendMouseEvent(0, 10, 10, false);
      vt100.sendResponse("\033[1;1R");

      String expected = "\033[I"
         + "\033[200~paste\033[201~"
         + "\033[<0;10;10M"
         + "\033[<0;10;10m"
         + "\033[1;1R";
      assertEquals(expected, captured());
   }
}
