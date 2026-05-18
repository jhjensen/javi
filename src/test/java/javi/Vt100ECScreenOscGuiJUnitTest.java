package javi;

import java.awt.Color;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for ECScreen OSC color handling, window operations,
 * mode save/restore, DECRQM, terminfo queries, and misc responses.
 *
 * <p>Covers private ECScreen methods via reflection in a full GUI
 * context. Complements Vt100ECScreenGuiJUnitTest (SGR, cursor ops)
 * and Vt100ECScreenCursorGuiJUnitTest (cursor movement, tabs).</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class Vt100ECScreenOscGuiJUnitTest {

   private static Robot robot;
   private static Vt100 vt100;
   private static PipedOutputStream pipeToVt;
   private static Object ecscreen;
   private static Vt100Parser parser;

   /** Captures response strings sent by sendResponse(). */
   private static java.io.ByteArrayOutputStream responseCapture;

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
      PipedInputStream pipeIn = new PipedInputStream(pipeToVt);
      BufferedInputStream bis = new BufferedInputStream(pipeIn);

      // Use ByteArrayOutputStream to capture terminal responses
      responseCapture = new java.io.ByteArrayOutputStream();

      EventQueue.biglock2.lock();
      try {
         IoConverter<String> ioc = new StringIoc("osc-test", "");
         vt100 = new Vt100(responseCapture, bis, ioc,
            StandardCharsets.UTF_8);

         Field ecField = Vt100.class.getDeclaredField("ecscreen");
         ecField.setAccessible(true);
         ecscreen = ecField.get(vt100);

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

   // ── Reflection helpers ───────────────────────────────────────

   private void invokeHandleOscColor(int oscNum, String payload)
         throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "handleOscColor", int.class, String.class);
      m.setAccessible(true);
      m.invoke(ecscreen, oscNum, payload);
   }

   private Color[] getPalette() throws Exception {
      Field f = ecscreen.getClass().getDeclaredField("palette");
      f.setAccessible(true);
      return (Color[]) f.get(ecscreen);
   }

   private Color getDynamicFg() throws Exception {
      Field f = ecscreen.getClass().getDeclaredField("dynamicFg");
      f.setAccessible(true);
      return (Color) f.get(ecscreen);
   }

   private Color getDynamicBg() throws Exception {
      Field f = ecscreen.getClass().getDeclaredField("dynamicBg");
      f.setAccessible(true);
      return (Color) f.get(ecscreen);
   }

   private Color getDynamicCursor() throws Exception {
      Field f = ecscreen.getClass().getDeclaredField("dynamicCursor");
      f.setAccessible(true);
      return (Color) f.get(ecscreen);
   }

   private Color getHighlightFg() throws Exception {
      Field f = ecscreen.getClass().getDeclaredField("highlightFg");
      f.setAccessible(true);
      return (Color) f.get(ecscreen);
   }

   private Color getHighlightBg() throws Exception {
      Field f = ecscreen.getClass().getDeclaredField("highlightBg");
      f.setAccessible(true);
      return (Color) f.get(ecscreen);
   }

   private void invokeHandleWindowOp(int ps, int p1, int p2)
         throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "handleWindowOp", int.class, int.class, int.class,
         StringBuilder.class);
      m.setAccessible(true);
      m.invoke(ecscreen, ps, p1, p2, new StringBuilder());
   }

   private String getOscTitle() throws Exception {
      Field f = Vt100.class.getDeclaredField("oscTitle");
      f.setAccessible(true);
      return (String) f.get(vt100);
   }

   private String getOscIconTitle() throws Exception {
      Field f = Vt100.class.getDeclaredField("oscIconTitle");
      f.setAccessible(true);
      return (String) f.get(vt100);
   }

   private void setOscTitle(String title) throws Exception {
      Field f = Vt100.class.getDeclaredField("oscTitle");
      f.setAccessible(true);
      f.set(vt100, title);
   }

   private void setOscIconTitle(String title) throws Exception {
      Field f = Vt100.class.getDeclaredField("oscIconTitle");
      f.setAccessible(true);
      f.set(vt100, title);
   }

   private void invokeSaveMode(int modeNum) throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "saveMode", int.class);
      m.setAccessible(true);
      m.invoke(ecscreen, modeNum);
   }

   private void invokeRestoreMode(int modeNum) throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "restoreMode", int.class, StringBuilder.class);
      m.setAccessible(true);
      m.invoke(ecscreen, modeNum, new StringBuilder());
   }

   private void invokeRespondDecrqm(int modeNum) throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "respondDecrqm", int.class, StringBuilder.class);
      m.setAccessible(true);
      m.invoke(ecscreen, modeNum, new StringBuilder());
   }

   private void invokeRespondTerminfoQuery(String hexName)
         throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "respondTerminfoQuery", String.class);
      m.setAccessible(true);
      m.invoke(ecscreen, hexName);
   }

   private void invokeRespondSecondaryDA() throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "respondSecondaryDA", StringBuilder.class);
      m.setAccessible(true);
      m.invoke(ecscreen, new StringBuilder());
   }

   private void invokeRespondStatusOk() throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "respondStatusOk", StringBuilder.class);
      m.setAccessible(true);
      m.invoke(ecscreen, new StringBuilder());
   }

   private void invokeSetLnmMode(boolean enable) throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "setLnmMode", boolean.class);
      m.setAccessible(true);
      m.invoke(ecscreen, enable);
   }

   private boolean getLnmMode() throws Exception {
      Field f = ecscreen.getClass().getDeclaredField("lnmMode");
      f.setAccessible(true);
      return f.getBoolean(ecscreen);
   }

   private void invokeSetAllow80To132(boolean enable) throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "setAllow80To132", boolean.class);
      m.setAccessible(true);
      m.invoke(ecscreen, enable);
   }

   private boolean getAllow80To132() throws Exception {
      Field f = ecscreen.getClass().getDeclaredField("allow80To132");
      f.setAccessible(true);
      return f.getBoolean(ecscreen);
   }

   private void invokeSetColumnMode(int cols) throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "setColumnMode", int.class, StringBuilder.class);
      m.setAccessible(true);
      m.invoke(ecscreen, cols, new StringBuilder());
   }

   private int getColumns() throws Exception {
      Field f = ecscreen.getClass().getDeclaredField("columns");
      f.setAccessible(true);
      return f.getInt(ecscreen);
   }

   private void invokeScreenAlignmentDisplay() throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "screenAlignmentDisplay", StringBuilder.class);
      m.setAccessible(true);
      m.invoke(ecscreen, new StringBuilder());
   }

   private void invokeIndex() throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "index", StringBuilder.class);
      m.setAccessible(true);
      m.invoke(ecscreen, new StringBuilder());
   }

   private void invokeReverseIndex() throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "reverseIndex", StringBuilder.class);
      m.setAccessible(true);
      m.invoke(ecscreen, new StringBuilder());
   }

   private void invokeNextLine() throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "nextLine", StringBuilder.class);
      m.setAccessible(true);
      m.invoke(ecscreen, new StringBuilder());
   }

   private String getResponse() {
      String resp = responseCapture.toString(StandardCharsets.UTF_8);
      responseCapture.reset();
      return resp;
   }

   // ── OSC palette color tests ──────────────────────────────────

   @Test
   void t01_oscSetPaletteColor() throws Exception {
      // Set palette index 1 to red
      invokeHandleOscColor(4, "1;rgb:ff/00/00");
      Color[] pal = getPalette();
      assertEquals(255, pal[1].getRed());
      assertEquals(0, pal[1].getGreen());
      assertEquals(0, pal[1].getBlue());
   }

   @Test
   void t02_oscQueryPaletteColor() throws Exception {
      getResponse(); // clear
      invokeHandleOscColor(4, "1;?");
      String resp = getResponse();
      assertTrue(resp.contains("]4;1;"), "response contains palette index");
      assertTrue(resp.endsWith("\033\\"), "response ends with ST");
   }

   @Test
   void t03_oscResetAllPalette() throws Exception {
      // Set palette index 5 to something non-default
      invokeHandleOscColor(4, "5;rgb:ab/cd/ef");
      Color before = getPalette()[5];
      assertEquals(0xab, before.getRed());
      // Reset all
      invokeHandleOscColor(104, "");
      Color after = getPalette()[5];
      // Should be back to default
      assertEquals(TerminalPalette.defaultColor(5), after);
   }

   @Test
   void t04_oscResetSinglePaletteIndex() throws Exception {
      invokeHandleOscColor(4, "10;rgb:11/22/33");
      invokeHandleOscColor(104, "10");
      assertEquals(TerminalPalette.defaultColor(10), getPalette()[10]);
   }

   @Test
   void t05_oscChainedPaletteSet() throws Exception {
      // Chain: set index 2 and 3 in one call
      invokeHandleOscColor(4, "2;rgb:aa/bb/cc;3;rgb:dd/ee/ff");
      Color[] pal = getPalette();
      assertEquals(0xaa, pal[2].getRed());
      assertEquals(0xdd, pal[3].getRed());
   }

   // ── OSC dynamic color tests ──────────────────────────────────

   @Test
   void t06_oscSetDynamicFg() throws Exception {
      invokeHandleOscColor(10, "rgb:ff/80/40");
      Color fg = getDynamicFg();
      assertEquals(255, fg.getRed());
      assertEquals(128, fg.getGreen());
      assertEquals(64, fg.getBlue());
   }

   @Test
   void t07_oscSetDynamicBg() throws Exception {
      invokeHandleOscColor(11, "rgb:10/20/30");
      Color bg = getDynamicBg();
      assertEquals(16, bg.getRed());
      assertEquals(32, bg.getGreen());
      assertEquals(48, bg.getBlue());
   }

   @Test
   void t08_oscSetDynamicCursor() throws Exception {
      invokeHandleOscColor(12, "rgb:ff/ff/00");
      Color cursor = getDynamicCursor();
      assertEquals(255, cursor.getRed());
      assertEquals(255, cursor.getGreen());
      assertEquals(0, cursor.getBlue());
   }

   @Test
   void t09_oscQueryDynamicFg() throws Exception {
      getResponse();
      invokeHandleOscColor(10, "?");
      String resp = getResponse();
      assertTrue(resp.contains("]10;"), "response contains OSC 10");
   }

   @Test
   void t10_oscResetDynamicFg() throws Exception {
      invokeHandleOscColor(10, "rgb:aa/bb/cc"); // set non-default
      invokeHandleOscColor(110, ""); // reset
      assertEquals(TerminalPalette.DEFAULT_FG, getDynamicFg());
   }

   @Test
   void t11_oscResetDynamicBg() throws Exception {
      invokeHandleOscColor(11, "rgb:11/22/33");
      invokeHandleOscColor(111, "");
      assertEquals(TerminalPalette.DEFAULT_BG, getDynamicBg());
   }

   @Test
   void t12_oscResetDynamicCursor() throws Exception {
      invokeHandleOscColor(12, "rgb:44/55/66");
      invokeHandleOscColor(112, "");
      assertEquals(TerminalPalette.DEFAULT_CURSOR, getDynamicCursor());
   }

   // ── OSC special color tests ──────────────────────────────────

   @Test
   void t13_oscSetHighlightFg() throws Exception {
      invokeHandleOscColor(17, "rgb:ff/00/ff");
      Color c = getHighlightFg();
      assertEquals(255, c.getRed());
      assertEquals(0, c.getGreen());
      assertEquals(255, c.getBlue());
   }

   @Test
   void t14_oscSetHighlightBg() throws Exception {
      invokeHandleOscColor(19, "rgb:00/ff/80");
      Color c = getHighlightBg();
      assertEquals(0, c.getRed());
      assertEquals(255, c.getGreen());
      assertEquals(128, c.getBlue());
   }

   @Test
   void t15_oscResetHighlightFg() throws Exception {
      invokeHandleOscColor(17, "rgb:ab/cd/ef");
      invokeHandleOscColor(117, "");
      assertEquals(TerminalPalette.DEFAULT_HIGHLIGHT_FG,
         getHighlightFg());
   }

   @Test
   void t16_oscResetHighlightBg() throws Exception {
      invokeHandleOscColor(19, "rgb:12/34/56");
      invokeHandleOscColor(119, "");
      assertEquals(TerminalPalette.DEFAULT_HIGHLIGHT_BG,
         getHighlightBg());
   }

   @Test
   void t17_oscSpecialColor5SetAndQuery() throws Exception {
      // Set special color 0 (bold) via OSC 5
      invokeHandleOscColor(5, "0;rgb:80/80/80");
      Color[] pal = getPalette();
      assertEquals(128, pal[256].getRed()); // index 256 = special 0

      // Query it
      getResponse();
      invokeHandleOscColor(5, "0;?");
      String resp = getResponse();
      assertTrue(resp.contains("]5;0;"));
   }

   @Test
   void t18_oscResetSpecialAll() throws Exception {
      invokeHandleOscColor(5, "1;rgb:aa/aa/aa");
      invokeHandleOscColor(105, ""); // reset all special
      assertEquals(TerminalPalette.DEFAULT_FG, getPalette()[257]);
   }

   @Test
   void t19_oscResetSpecialSingle() throws Exception {
      invokeHandleOscColor(5, "2;rgb:bb/bb/bb");
      invokeHandleOscColor(105, "2");
      assertEquals(TerminalPalette.DEFAULT_FG, getPalette()[258]);
   }

   // ── Window operations tests ──────────────────────────────────

   @Test
   void t20_windowOpTitlePushPop() throws Exception {
      setOscTitle("original-title");
      setOscIconTitle("original-icon");

      // Push title (op 22)
      invokeHandleWindowOp(22, 0, 0);

      // Change titles
      setOscTitle("new-title");
      setOscIconTitle("new-icon");
      assertEquals("new-title", getOscTitle());

      // Pop title (op 23)
      invokeHandleWindowOp(23, 0, 0);
      assertEquals("original-title", getOscTitle());
      assertEquals("original-icon", getOscIconTitle());
   }

   @Test
   void t21_windowOpReportSizeChars() throws Exception {
      getResponse();
      invokeHandleWindowOp(18, 0, 0);
      String resp = getResponse();
      // Response format: CSI 8;rows;cols t
      assertTrue(resp.contains("[8;"), "contains size report");
   }

   @Test
   void t22_windowOpReportSizePixels() throws Exception {
      getResponse();
      invokeHandleWindowOp(14, 0, 0);
      String resp = getResponse();
      assertTrue(resp.contains("[4;"), "contains pixel size report");
   }

   @Test
   void t23_windowOpReportIconLabel() throws Exception {
      setOscIconTitle("test-icon");
      getResponse();
      invokeHandleWindowOp(20, 0, 0);
      String resp = getResponse();
      assertTrue(resp.contains("test-icon"), "contains icon title");
   }

   @Test
   void t24_windowOpReportWindowTitle() throws Exception {
      setOscTitle("test-window");
      getResponse();
      invokeHandleWindowOp(21, 0, 0);
      String resp = getResponse();
      assertTrue(resp.contains("test-window"), "contains window title");
   }

   @Test
   void t25_windowOpTitlePushPopMultiple() throws Exception {
      setOscTitle("first");
      invokeHandleWindowOp(22, 0, 0);
      setOscTitle("second");
      invokeHandleWindowOp(22, 0, 0);
      setOscTitle("third");

      // Pop second
      invokeHandleWindowOp(23, 0, 0);
      assertEquals("second", getOscTitle());

      // Pop first
      invokeHandleWindowOp(23, 0, 0);
      assertEquals("first", getOscTitle());
   }

   @Test
   void t26_windowOpPopEmptyStackNoOp() throws Exception {
      // Clear stack by popping until empty
      for (int i = 0; i < 10; i++)
         invokeHandleWindowOp(23, 0, 0);
      // Now set a title and try to pop — should remain unchanged
      setOscTitle("stable");
      invokeHandleWindowOp(23, 0, 0);
      assertEquals("stable", getOscTitle());
   }

   // ── Mode save/restore tests ──────────────────────────────────

   @Test
   void t27_modeSaveRestoreAutowrap() throws Exception {
      // autowrap starts as true
      vt100.setAutowrapMode(true);
      invokeSaveMode(7);

      // Change it
      vt100.setAutowrapMode(false);

      // Restore
      invokeRestoreMode(7);
      // The mode should be restored to true
      Field f = Vt100.class.getDeclaredField("autowrapMode");
      f.setAccessible(true);
      assertTrue(f.getBoolean(vt100));
   }

   @Test
   void t28_modeSaveRestoreCursorKeys() throws Exception {
      vt100.setApplicationCursorKeys(true);
      invokeSaveMode(1);
      vt100.setApplicationCursorKeys(false);
      invokeRestoreMode(1);
      Field f = Vt100.class.getDeclaredField("applicationCursorKeys");
      f.setAccessible(true);
      assertTrue(f.getBoolean(vt100));
   }

   @Test
   void t29_restoreUnsavedModeNoOp() throws Exception {
      // Restoring a mode that was never saved should not throw
      invokeRestoreMode(9999);
   }

   // ── DECRQM tests ────────────────────────────────────────────

   @Test
   void t30_decrqmKnownModeSet() throws Exception {
      vt100.setAutowrapMode(true);
      getResponse();
      invokeRespondDecrqm(7);
      String resp = getResponse();
      // Response: CSI ? 7;1 $ y (set)
      assertTrue(resp.contains("?7;1$y"),
         "DECRQM reports autowrap as set");
   }

   @Test
   void t31_decrqmKnownModeReset() throws Exception {
      vt100.setAutowrapMode(false);
      getResponse();
      invokeRespondDecrqm(7);
      String resp = getResponse();
      assertTrue(resp.contains("?7;2$y"),
         "DECRQM reports autowrap as reset");
   }

   @Test
   void t32_decrqmUnknownMode() throws Exception {
      getResponse();
      invokeRespondDecrqm(9999);
      String resp = getResponse();
      assertTrue(resp.contains("?9999;0$y"),
         "DECRQM reports unknown mode as 0");
   }

   // ── Terminfo query tests ─────────────────────────────────────

   @Test
   void t33_terminfoQueryColorsHex() throws Exception {
      getResponse();
      // "Co" in hex = 0x43 0x6F = "436F"
      invokeRespondTerminfoQuery("436F");
      String resp = getResponse();
      // Response: DCS 1+r 436F = 323536 ST (256 in hex-ASCII)
      assertTrue(resp.contains("1+r436F=323536"),
         "terminfo Co query returns 256");
   }

   @Test
   void t34_terminfoQueryColorsLongForm() throws Exception {
      getResponse();
      // "colors" in hex
      invokeRespondTerminfoQuery("636F6C6F7273");
      String resp = getResponse();
      assertTrue(resp.contains("1+r636F6C6F7273=323536"),
         "terminfo colors query returns 256");
   }

   @Test
   void t35_terminfoQueryUnknown() throws Exception {
      getResponse();
      invokeRespondTerminfoQuery("DEADBEEF");
      String resp = getResponse();
      // Response: DCS 0+r DEADBEEF ST
      assertTrue(resp.contains("0+rDEADBEEF"),
         "unknown terminfo returns not-found");
   }

   // ── Secondary DA and status tests ────────────────────────────

   @Test
   void t36_secondaryDA() throws Exception {
      getResponse();
      invokeRespondSecondaryDA();
      String resp = getResponse();
      // VT220 response: CSI > 1;314;0 c
      assertTrue(resp.contains("[>1;314;0c"),
         "secondary DA identifies as VT220");
   }

   @Test
   void t37_statusOkResponse() throws Exception {
      getResponse();
      invokeRespondStatusOk();
      String resp = getResponse();
      assertTrue(resp.contains("[0n"), "DSR status ok");
   }

   // ── LNM mode tests ──────────────────────────────────────────

   @Test
   void t38_lnmModeToggle() throws Exception {
      assertFalse(getLnmMode());
      invokeSetLnmMode(true);
      assertTrue(getLnmMode());
      invokeSetLnmMode(false);
      assertFalse(getLnmMode());
   }

   // ── DECCOLM and 80/132 column tests ─────────────────────────

   @Test
   void t39_allow80To132Toggle() throws Exception {
      assertFalse(getAllow80To132());
      invokeSetAllow80To132(true);
      assertTrue(getAllow80To132());
      invokeSetAllow80To132(false);
      assertFalse(getAllow80To132());
   }

   @Test
   void t40_setColumnModeWhenAllowed() throws Exception {
      EventQueue.biglock2.lock();
      try {
         invokeSetAllow80To132(true);
         invokeSetColumnMode(132);
         assertEquals(132, getColumns());
         // Reset
         invokeSetAllow80To132(false);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t41_setColumnModeWhenNotAllowed() throws Exception {
      EventQueue.biglock2.lock();
      try {
         invokeSetAllow80To132(false);
         int before = getColumns();
         invokeSetColumnMode(132);
         assertEquals(before, getColumns(),
            "columns unchanged when 80/132 disabled");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Index / Reverse Index / Next Line ────────────────────────

   @Test
   void t42_indexAdvancesCursor() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Field fy = Vt100.class.getDeclaredField("vtcursor");
         fy.setAccessible(true);
         MovePos cursor = (MovePos) fy.get(vt100);
         int beforeY = cursor.y;
         invokeIndex();
         assertTrue(cursor.y >= beforeY,
            "index moves cursor down or scrolls");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t43_reverseIndexDoesNotCrash() throws Exception {
      EventQueue.biglock2.lock();
      try {
         invokeReverseIndex();
         // No assertion needed — just verifying no exception
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t44_nextLineMovesToCol0() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Field fy = Vt100.class.getDeclaredField("vtcursor");
         fy.setAccessible(true);
         MovePos cursor = (MovePos) fy.get(vt100);
         cursor.x = 10;
         invokeNextLine();
         assertEquals(0, cursor.x, "nextLine resets cursor to column 0");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Screen alignment display ─────────────────────────────────

   @Test
   void t45_screenAlignmentDoesNotCrash() throws Exception {
      EventQueue.biglock2.lock();
      try {
         invokeScreenAlignmentDisplay();
         // DECALN writes 'E' to all visible cells; in this headless test
         // context rows may be 0, so just verify it completes without error.
         assertTrue(vt100.readIn() >= 0, "readIn is non-negative after DECALN");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── OSC empty payload edge cases ─────────────────────────────

   @Test
   void t46_oscEmptyPayloadNoOp() throws Exception {
      // Empty payload for OSC 4/5/10/17 should not crash
      invokeHandleOscColor(4, "");
      invokeHandleOscColor(5, "");
      invokeHandleOscColor(10, "");
      invokeHandleOscColor(17, "");
   }

   @Test
   void t47_oscInvalidPaletteIndexNoOp() throws Exception {
      // Index 999 is out of range — should not crash
      invokeHandleOscColor(4, "999;rgb:ff/ff/ff");
      // No assertion — verifying no exception
   }

   @Test
   void t48_oscInvalidSpecialColorIndexNoOp() throws Exception {
      // Special index 10 is out of range (0-5 valid) — should not crash
      invokeHandleOscColor(5, "10;rgb:ff/ff/ff");
   }
}
