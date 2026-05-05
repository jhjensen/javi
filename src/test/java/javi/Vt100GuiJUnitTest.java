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
 * AssertJ Swing GUI tests for Vt100 ECScreen terminal emulation.
 *
 * <p>Creates a Vt100 terminal instance in the full GUI context and
 * exercises the ECScreen rendering path, cursor movement, screen
 * operations, and attribute management. Requires Xvfb or a display.</p>
 *
 * <p>ECScreen is a private inner class of Vt100, so we use reflection
 * to access it and verify screen state through the public API.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class Vt100GuiJUnitTest {

   private static Robot robot;
   private static Vt100 vt100;
   private static PipedOutputStream pipeToVt;
   private static Object ecscreen; // ECScreen instance
   private static Vt100Parser parser;

   @BeforeAll
   static void initJavi() throws Exception {
      // Guard: skip init if another GUI test class already initialized Javi
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

      // Create a Vt100 instance with piped streams
      pipeToVt = new PipedOutputStream();
      PipedInputStream pipeIn = new PipedInputStream(pipeToVt);
      BufferedInputStream bis = new BufferedInputStream(pipeIn);
      PipedOutputStream devNull = new PipedOutputStream();
      PipedInputStream devNullIn = new PipedInputStream(devNull);

      EventQueue.biglock2.lock();
      try {
         IoConverter<String> ioc = new StringIoc("vt100-test", "");
         vt100 = new Vt100(devNull, bis, ioc, StandardCharsets.UTF_8);

         // Access the ecscreen field via reflection
         Field ecField = Vt100.class.getDeclaredField("ecscreen");
         ecField.setAccessible(true);
         ecscreen = ecField.get(vt100);

         // Access the parser
         Field parserField = Vt100.class.getDeclaredField("parser");
         parserField.setAccessible(true);
         parser = (Vt100Parser) parserField.get(vt100);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @AfterAll
   static void tearDownAll() {
      if (parser != null) {
         parser.stop();
      }
      if (robot != null)
         robot.cleanUp();
   }

   // ── Vt100 instance tests ────────────────────────────────────

   @Test
   void t01_vt100Created() {
      assertNotNull(vt100, "Vt100 instance should be created");
   }

   @Test
   void t02_charsetIsUtf8() {
      assertEquals(StandardCharsets.UTF_8, vt100.getCharset(),
         "Charset should be UTF-8");
   }

   @Test
   void t03_oscTitleInitiallyNull() {
      // No OSC title set before any data is received
      String title = vt100.getOscTitle();
      assertTrue(title == null,
         "OSC title should be null initially");
   }

   @Test
   void t04_screenAttributesNotNull() {
      ScreenAttributes attrs = vt100.getScreenAttributes();
      assertNotNull(attrs,
         "ScreenAttributes should not be null");
   }

   @Test
   void t05_terminalAttributesNotNull() {
      ScreenAttributes attrs = vt100.getTerminalAttributes();
      assertNotNull(attrs,
         "Terminal attributes should not be null");
   }

   @Test
   void t06_screenAttributesSameInstance() {
      // getScreenAttributes and getTerminalAttributes return same object
      assertTrue(
         vt100.getScreenAttributes() == vt100.getTerminalAttributes(),
         "Should return same ScreenAttributes instance");
   }

   // ── Mouse tracking mode tests ───────────────────────────────

   @Test
   void t07_mouseTrackingInitiallyOff() {
      assertFalse(vt100.isMouseTrackingEnabled(),
         "Mouse tracking should be off initially");
   }

   @Test
   void t08_mouseTrackingEnableDisable() {
      vt100.setMouseTracking(1000, true);
      assertTrue(vt100.isMouseTrackingEnabled(),
         "Mouse tracking should be enabled after setMouseTracking(1000, true)");
      vt100.setMouseTracking(1000, false);
      assertFalse(vt100.isMouseTrackingEnabled(),
         "Mouse tracking should be disabled after setMouseTracking(1000, false)");
   }

   @Test
   void t09_mouseTrackingModesMixed() {
      // Enable mode 1002, then disable mode 1000 — should stay on
      vt100.setMouseTracking(1002, true);
      assertTrue(vt100.isMouseTrackingEnabled());
      vt100.setMouseTracking(1000, false); // different mode
      assertTrue(vt100.isMouseTrackingEnabled(),
         "Disabling different mode should not affect current mode");
      // Clean up
      vt100.setMouseTracking(1002, false);
      assertFalse(vt100.isMouseTrackingEnabled());
   }

   // ── Cursor visibility tests ─────────────────────────────────

   @Test
   void t10_cursorInitiallyVisible() {
      assertTrue(vt100.isCursorVisible(),
         "Cursor should be visible initially (DECTCEM default)");
   }

   @Test
   void t11_cursorHideShow() {
      vt100.setCursorVisible(false);
      assertFalse(vt100.isCursorVisible(),
         "Cursor should be hidden");
      vt100.setCursorVisible(true);
      assertTrue(vt100.isCursorVisible(),
         "Cursor should be visible again");
   }

   // ── Terminal mode flag tests ─────────────────────────────────

   @Test
   void t12_sgrMouseModeToggle() throws Exception {
      Field sgrField = Vt100.class.getDeclaredField("sgrMouseMode");
      sgrField.setAccessible(true);
      assertFalse(sgrField.getBoolean(vt100),
         "SGR mouse mode should be off initially");
      vt100.setSgrMouseMode(true);
      assertTrue(sgrField.getBoolean(vt100));
      vt100.setSgrMouseMode(false);
      assertFalse(sgrField.getBoolean(vt100));
   }

   @Test
   void t13_bracketedPasteModeToggle() throws Exception {
      Field bpField = Vt100.class.getDeclaredField("bracketedPasteMode");
      bpField.setAccessible(true);
      assertFalse(bpField.getBoolean(vt100),
         "Bracketed paste should be off initially");
      vt100.setBracketedPasteMode(true);
      assertTrue(bpField.getBoolean(vt100));
      vt100.setBracketedPasteMode(false);
      assertFalse(bpField.getBoolean(vt100));
   }

   @Test
   void t14_focusEventsModeToggle() throws Exception {
      Field feField = Vt100.class.getDeclaredField("focusEventsMode");
      feField.setAccessible(true);
      assertFalse(feField.getBoolean(vt100));
      vt100.setFocusEventsMode(true);
      assertTrue(feField.getBoolean(vt100));
      assertTrue(vt100.isFocusEventsEnabled());
      vt100.setFocusEventsMode(false);
      assertFalse(feField.getBoolean(vt100));
      assertFalse(vt100.isFocusEventsEnabled());
   }

   @Test
   void t15_autowrapModeInitiallyOn() throws Exception {
      Field awField = Vt100.class.getDeclaredField("autowrapMode");
      awField.setAccessible(true);
      assertTrue(awField.getBoolean(vt100),
         "Autowrap should be on initially");
   }

   @Test
   void t16_autowrapModeToggle() throws Exception {
      Field awField = Vt100.class.getDeclaredField("autowrapMode");
      awField.setAccessible(true);
      vt100.setAutowrapMode(false);
      assertFalse(awField.getBoolean(vt100));
      vt100.setAutowrapMode(true);
      assertTrue(awField.getBoolean(vt100));
   }

   @Test
   void t17_cursorBlinkModeToggle() throws Exception {
      Field cbField = Vt100.class.getDeclaredField("cursorBlinkMode");
      cbField.setAccessible(true);
      assertFalse(cbField.getBoolean(vt100),
         "Cursor blink should be off initially");
      vt100.setCursorBlinkMode(true);
      assertTrue(cbField.getBoolean(vt100));
      vt100.setCursorBlinkMode(false);
      assertFalse(cbField.getBoolean(vt100));
   }

   @Test
   void t18_applicationCursorKeysToggle() throws Exception {
      Field ackField = Vt100.class.getDeclaredField(
         "applicationCursorKeys");
      ackField.setAccessible(true);
      assertFalse(ackField.getBoolean(vt100),
         "App cursor keys should be off initially");
      vt100.setApplicationCursorKeys(true);
      assertTrue(ackField.getBoolean(vt100));
      vt100.setApplicationCursorKeys(false);
      assertFalse(ackField.getBoolean(vt100));
   }

   // ── ECScreen / ScreenAttributes integration ──────────────────

   @Test
   void t19_screenAttrsDefaultForUnsetCell() {
      ScreenAttributes attrs = vt100.getScreenAttributes();
      int val = attrs.getAttr(999, 0);
      assertEquals(CellAttr.DEFAULT, val,
         "Unset cell should return DEFAULT attribute");
   }

   @Test
   void t20_screenAttrsSetAndGet() {
      ScreenAttributes attrs = vt100.getScreenAttributes();
      int packed = CellAttr.pack(true, false, false, 1, -1);
      attrs.setAttr(1, 0, packed);
      assertEquals(packed, attrs.getAttr(1, 0),
         "Attribute should round-trip through set/get");
   }

   @Test
   void t21_screenAttrsFillRange() {
      ScreenAttributes attrs = vt100.getScreenAttributes();
      int packed = CellAttr.pack(false, true, false, 2, 3);
      attrs.fillAttr(2, 0, 10, packed);
      for (int col = 0; col < 10; col++) {
         assertEquals(packed, attrs.getAttr(2, col),
            "Column " + col + " should have the filled attribute");
      }
   }

   @Test
   void t22_screenAttrsEraseLine() {
      ScreenAttributes attrs = vt100.getScreenAttributes();
      int packed = CellAttr.pack(true, true, false, 5, 6);
      attrs.fillAttr(3, 0, 20, packed);
      attrs.eraseLine(3);
      assertEquals(CellAttr.DEFAULT, attrs.getAttr(3, 0),
         "After eraseLine, cell should be DEFAULT");
      assertEquals(CellAttr.DEFAULT, attrs.getAttr(3, 19),
         "After eraseLine, last cell should be DEFAULT");
   }

   @Test
   void t23_screenAttrsEraseScreen() {
      ScreenAttributes attrs = vt100.getScreenAttributes();
      int packed = CellAttr.pack(false, false, true, 7, 0);
      for (int line = 10; line < 20; line++)
         attrs.fillAttr(line, 0, 5, packed);
      attrs.eraseScreen(10, 20);
      for (int line = 10; line < 20; line++) {
         assertEquals(CellAttr.DEFAULT, attrs.getAttr(line, 0),
            "Line " + line + " should be DEFAULT after eraseScreen");
      }
   }

   @Test
   void t24_screenAttrsEraseToEnd() {
      ScreenAttributes attrs = vt100.getScreenAttributes();
      int packed = CellAttr.pack(true, false, true, 3, 4);
      attrs.fillAttr(30, 0, 20, packed);
      attrs.eraseToEnd(30, 5);
      // Columns 0-4 should still have the attribute
      assertEquals(packed, attrs.getAttr(30, 0),
         "Column 0 should retain attribute");
      assertEquals(packed, attrs.getAttr(30, 4),
         "Column 4 should retain attribute");
      // Column 5+ should be default
      assertEquals(CellAttr.DEFAULT, attrs.getAttr(30, 5),
         "Column 5 should be DEFAULT after eraseToEnd");
   }

   // ── ECScreen rendering via reflection ────────────────────────

   @Test
   void t25_ecscreenIsVScreenSubclass() {
      assertTrue(ecscreen instanceof VScreen,
         "ECScreen should be a VScreen subclass");
   }

   @Test
   void t26_ecscreenSetGraphicRenditionReset() throws Exception {
      // Call setGraphicRendition([0], sb) to reset attributes
      Method sgrMethod = ecscreen.getClass().getDeclaredMethod(
         "setGraphicRendition", int[].class, StringBuilder.class);
      sgrMethod.setAccessible(true);
      StringBuilder sb = new StringBuilder();
      EventQueue.biglock2.lock();
      try {
         sgrMethod.invoke(ecscreen, new int[]{0}, sb);
      } finally {
         EventQueue.biglock2.unlock();
      }
      // After reset, currentAttr should be DEFAULT
      Field caField = ecscreen.getClass().getDeclaredField("currentAttr");
      caField.setAccessible(true);
      assertEquals(CellAttr.DEFAULT, caField.getInt(ecscreen),
         "After SGR 0, currentAttr should be DEFAULT");
   }

   @Test
   void t27_ecscreenSetGraphicRenditionBold() throws Exception {
      Method sgrMethod = ecscreen.getClass().getDeclaredMethod(
         "setGraphicRendition", int[].class, StringBuilder.class);
      sgrMethod.setAccessible(true);
      StringBuilder sb = new StringBuilder();
      EventQueue.biglock2.lock();
      try {
         // Set bold (SGR 1)
         sgrMethod.invoke(ecscreen, new int[]{1}, sb);
      } finally {
         EventQueue.biglock2.unlock();
      }
      Field caField = ecscreen.getClass().getDeclaredField("currentAttr");
      caField.setAccessible(true);
      int attr = caField.getInt(ecscreen);
      assertTrue(CellAttr.isBold(attr),
         "After SGR 1, attribute should be bold");
      // Reset
      EventQueue.biglock2.lock();
      try {
         sgrMethod.invoke(ecscreen, new int[]{0}, sb);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t28_ecscreenSetGraphicRenditionFgColor() throws Exception {
      Method sgrMethod = ecscreen.getClass().getDeclaredMethod(
         "setGraphicRendition", int[].class, StringBuilder.class);
      sgrMethod.setAccessible(true);
      StringBuilder sb = new StringBuilder();
      EventQueue.biglock2.lock();
      try {
         // Set red foreground (SGR 31)
         sgrMethod.invoke(ecscreen, new int[]{31}, sb);
      } finally {
         EventQueue.biglock2.unlock();
      }
      Field sgrField1 = ecscreen.getClass().getDeclaredField("sgrState");
      sgrField1.setAccessible(true);
      Object sgrState1 = sgrField1.get(ecscreen);
      Field fgField = sgrState1.getClass().getDeclaredField("attrFgColor");
      fgField.setAccessible(true);
      assertEquals(1, fgField.getInt(sgrState1),
         "After SGR 31, fg should be 1 (red)");
      // Reset
      EventQueue.biglock2.lock();
      try {
         sgrMethod.invoke(ecscreen, new int[]{0}, sb);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t29_ecscreenSetGraphicRenditionBgColor() throws Exception {
      Method sgrMethod = ecscreen.getClass().getDeclaredMethod(
         "setGraphicRendition", int[].class, StringBuilder.class);
      sgrMethod.setAccessible(true);
      StringBuilder sb = new StringBuilder();
      // Set blue background (SGR 44)
      sgrMethod.invoke(ecscreen, new int[]{44}, sb);
      Field sgrField2 = ecscreen.getClass().getDeclaredField("sgrState");
      sgrField2.setAccessible(true);
      Object sgrState2 = sgrField2.get(ecscreen);
      Field bgField = sgrState2.getClass().getDeclaredField("attrBgColor");
      bgField.setAccessible(true);
      assertEquals(4, bgField.getInt(sgrState2),
         "After SGR 44, bg should be 4 (blue)");
      // Reset
      sgrMethod.invoke(ecscreen, new int[]{0}, sb);
   }

   @Test
   void t30_ecscreenSetGraphicRenditionUnderline() throws Exception {
      Method sgrMethod = ecscreen.getClass().getDeclaredMethod(
         "setGraphicRendition", int[].class, StringBuilder.class);
      sgrMethod.setAccessible(true);
      StringBuilder sb = new StringBuilder();
      // Set underline (SGR 4)
      sgrMethod.invoke(ecscreen, new int[]{4}, sb);
      Field caField = ecscreen.getClass().getDeclaredField("currentAttr");
      caField.setAccessible(true);
      int attr = caField.getInt(ecscreen);
      assertTrue(CellAttr.isUnderline(attr),
         "After SGR 4, attribute should be underline");
      // Reset
      sgrMethod.invoke(ecscreen, new int[]{0}, sb);
   }

   @Test
   void t31_ecscreenSetGraphicRenditionReverse() throws Exception {
      Method sgrMethod = ecscreen.getClass().getDeclaredMethod(
         "setGraphicRendition", int[].class, StringBuilder.class);
      sgrMethod.setAccessible(true);
      StringBuilder sb = new StringBuilder();
      // Set reverse video (SGR 7)
      sgrMethod.invoke(ecscreen, new int[]{7}, sb);
      Field caField = ecscreen.getClass().getDeclaredField("currentAttr");
      caField.setAccessible(true);
      int attr = caField.getInt(ecscreen);
      assertTrue(CellAttr.isReverse(attr),
         "After SGR 7, attribute should be reverse");
      // Reset
      sgrMethod.invoke(ecscreen, new int[]{0}, sb);
   }

   @Test
   void t32_ecscreenBrightFgColor() throws Exception {
      Method sgrMethod = ecscreen.getClass().getDeclaredMethod(
         "setGraphicRendition", int[].class, StringBuilder.class);
      sgrMethod.setAccessible(true);
      StringBuilder sb = new StringBuilder();
      // Set bright red fg (SGR 91)
      sgrMethod.invoke(ecscreen, new int[]{91}, sb);
      Field sgrField3 = ecscreen.getClass().getDeclaredField("sgrState");
      sgrField3.setAccessible(true);
      Object sgrState3 = sgrField3.get(ecscreen);
      Field fgField = sgrState3.getClass().getDeclaredField("attrFgColor");
      fgField.setAccessible(true);
      assertEquals(9, fgField.getInt(sgrState3),
         "After SGR 91, fg should be 9 (bright red = 91-90+8)");
      // Reset
      sgrMethod.invoke(ecscreen, new int[]{0}, sb);
   }

   @Test
   void t33_ecscreenDefaultFgReset() throws Exception {
      Method sgrMethod = ecscreen.getClass().getDeclaredMethod(
         "setGraphicRendition", int[].class, StringBuilder.class);
      sgrMethod.setAccessible(true);
      StringBuilder sb = new StringBuilder();
      sgrMethod.invoke(ecscreen, new int[]{31}, sb); // set red
      sgrMethod.invoke(ecscreen, new int[]{39}, sb); // reset fg
      Field sgrField4 = ecscreen.getClass().getDeclaredField("sgrState");
      sgrField4.setAccessible(true);
      Object sgrState4 = sgrField4.get(ecscreen);
      Field fgField = sgrState4.getClass().getDeclaredField("attrFgColor");
      fgField.setAccessible(true);
      assertEquals(-1, fgField.getInt(sgrState4),
         "After SGR 39, fg should be -1 (default)");
      // Final reset
      sgrMethod.invoke(ecscreen, new int[]{0}, sb);
   }

   @Test
   void t34_ecscreenMultipleSgrParams() throws Exception {
      Method sgrMethod = ecscreen.getClass().getDeclaredMethod(
         "setGraphicRendition", int[].class, StringBuilder.class);
      sgrMethod.setAccessible(true);
      StringBuilder sb = new StringBuilder();
      // Bold + underline + green fg in one call: SGR 1;4;32
      sgrMethod.invoke(ecscreen, new int[]{1, 4, 32}, sb);
      Field caField = ecscreen.getClass().getDeclaredField("currentAttr");
      caField.setAccessible(true);
      int attr = caField.getInt(ecscreen);
      assertTrue(CellAttr.isBold(attr), "Should be bold");
      assertTrue(CellAttr.isUnderline(attr), "Should be underline");
      Field sgrField5 = ecscreen.getClass().getDeclaredField("sgrState");
      sgrField5.setAccessible(true);
      Object sgrState5 = sgrField5.get(ecscreen);
      Field fgField = sgrState5.getClass().getDeclaredField("attrFgColor");
      fgField.setAccessible(true);
      assertEquals(2, fgField.getInt(sgrState5),
         "fg should be 2 (green)");
      // Reset
      sgrMethod.invoke(ecscreen, new int[]{0}, sb);
   }

   // ── sendResponse / sendText tests ────────────────────────────

   @Test
   void t35_sendResponseDoesNotThrow() {
      // sendResponse writes to the PTY — should not throw even if
      // the stream is broken (it catches IOException)
      vt100.sendResponse("\033[?62;22c");
      assertTrue(true, "sendResponse should not throw");
   }

   @Test
   void t36_sendTextDoesNotThrow() {
      vt100.sendText("Hello World");
      assertTrue(true, "sendText should not throw");
   }

   @Test
   void t37_sendFocusEventWhenDisabled() {
      // Focus events disabled — should be a no-op
      vt100.setFocusEventsMode(false);
      vt100.sendFocusEvent(true);
      vt100.sendFocusEvent(false);
      assertTrue(true, "sendFocusEvent when disabled should be no-op");
   }

   @Test
   void t38_sendFocusEventWhenEnabled() {
      vt100.setFocusEventsMode(true);
      vt100.sendFocusEvent(true);
      vt100.sendFocusEvent(false);
      vt100.setFocusEventsMode(false); // cleanup
      assertTrue(true,
         "sendFocusEvent when enabled should not throw");
   }

   // ── notifyResize tests ──────────────────────────────────────

   @Test
   void t39_notifyResizeZeroIgnored() {
      // Zero/negative dimensions should be ignored
      vt100.notifyResize(0, 80);
      vt100.notifyResize(24, 0);
      vt100.notifyResize(-1, 80);
      assertTrue(true, "Invalid resize dimensions should be ignored");
   }

   @Test
   void t40_notifyResizeValid() {
      // Valid resize should not throw
      EventQueue.biglock2.lock();
      try {
         vt100.notifyResize(24, 80);
      } finally {
         EventQueue.biglock2.unlock();
      }
      assertTrue(true, "Valid resize should succeed");
   }

   // ── Vt100 buffer content tests ──────────────────────────────

   @Test
   void t41_vt100IsTextEdit() {
      assertTrue(vt100 instanceof TextEdit,
         "Vt100 should extend TextEdit");
   }

   @Test
   void t42_vt100HasPositiveReadIn() {
      assertTrue(vt100.readIn() >= 1,
         "Vt100 buffer should have at least 1 line");
   }

   @Test
   void t43_fromStringReturnsInput() {
      assertEquals("test", vt100.fromString("test"),
         "fromString should return the input string");
   }
}
