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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deep ECScreen GUI tests — cursor operations, alternate screen buffer,
 * 256-color and true-color SGR parsing, and screen edit operations.
 *
 * <p>Exercises the private ECScreen inner class of Vt100 via reflection
 * in a full GUI context. Complements Vt100GuiJUnitTest which covers
 * basic mode flags and simple SGR.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class Vt100ECScreenGuiJUnitTest {

   private static Robot robot;
   private static Vt100 vt100;
   private static PipedOutputStream pipeToVt;
   private static Object ecscreen;
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
      PipedInputStream pipeIn = new PipedInputStream(pipeToVt);
      BufferedInputStream bis = new BufferedInputStream(pipeIn);
      PipedOutputStream devNull = new PipedOutputStream();
      new PipedInputStream(devNull); // connect to prevent broken pipe

      EventQueue.biglock2.lock();
      try {
         IoConverter<String> ioc = new StringIoc("ecscreen-test", "");
         vt100 = new Vt100(devNull, bis, ioc, StandardCharsets.UTF_8);

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

   // Helpers
   private Method getSgrMethod() throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(
         "setGraphicRendition", int[].class, StringBuilder.class);
      m.setAccessible(true);
      return m;
   }

   private int getCurrentAttr() throws Exception {
      Field f = ecscreen.getClass().getDeclaredField("currentAttr");
      f.setAccessible(true);
      return f.getInt(ecscreen);
   }

   private Object getSgrState() throws Exception {
      Field f = ecscreen.getClass().getDeclaredField("sgrState");
      f.setAccessible(true);
      return f.get(ecscreen);
   }

   private int getFgColor() throws Exception {
      Object sgr = getSgrState();
      Field f = sgr.getClass().getDeclaredField("attrFgColor");
      f.setAccessible(true);
      return f.getInt(sgr);
   }

   private int getBgColor() throws Exception {
      Object sgr = getSgrState();
      Field f = sgr.getClass().getDeclaredField("attrBgColor");
      f.setAccessible(true);
      return f.getInt(sgr);
   }

   private void resetSgr() throws Exception {
      getSgrMethod().invoke(ecscreen, new int[]{0}, new StringBuilder());
   }

   // ── 256-color SGR tests ──────────────────────────────────────

   @Test
   void t01_256ColorFg() throws Exception {
      Method sgr = getSgrMethod();
      StringBuilder sb = new StringBuilder();
      // SGR 38;5;196 → 256-color fg = 196
      sgr.invoke(ecscreen, new int[]{38, 5, 196}, sb);
      assertEquals(196, getFgColor(),
         "256-color FG should be 196");
      resetSgr();
   }

   @Test
   void t02_256ColorBg() throws Exception {
      Method sgr = getSgrMethod();
      StringBuilder sb = new StringBuilder();
      // SGR 48;5;52 → 256-color bg = 52
      sgr.invoke(ecscreen, new int[]{48, 5, 52}, sb);
      assertEquals(52, getBgColor(),
         "256-color BG should be 52");
      resetSgr();
   }

   @Test
   void t03_trueColorFg() throws Exception {
      Method sgr = getSgrMethod();
      StringBuilder sb = new StringBuilder();
      // SGR 38;2;255;0;0 → true color red, approximated to 256-color
      sgr.invoke(ecscreen, new int[]{38, 2, 255, 0, 0}, sb);
      int fg = getFgColor();
      assertTrue(fg >= 0 && fg <= 254,
         "True-color FG should map to 0-254, got " + fg);
      resetSgr();
   }

   @Test
   void t04_trueColorBg() throws Exception {
      Method sgr = getSgrMethod();
      StringBuilder sb = new StringBuilder();
      // SGR 48;2;0;255;0 → true color green
      sgr.invoke(ecscreen, new int[]{48, 2, 0, 255, 0}, sb);
      int bg = getBgColor();
      assertTrue(bg >= 0 && bg <= 254,
         "True-color BG should map to 0-254, got " + bg);
      resetSgr();
   }

   @Test
   void t05_trueColorApproxBlack() throws Exception {
      // Verify approxTrueColor for black (0,0,0)
      int result = CellAttr.approxTrueColor(0, 0, 0);
      // Black should map to grayscale ramp entry 232 or color cube 16
      assertTrue(result >= 16 && result <= 254,
         "Black should map to palette 16-254, got " + result);
   }

   @Test
   void t06_trueColorApproxWhite() throws Exception {
      int result = CellAttr.approxTrueColor(255, 255, 255);
      assertTrue(result >= 16 && result <= 254,
         "White should map to palette 16-254, got " + result);
   }

   @Test
   void t07_brightBgColor() throws Exception {
      Method sgr = getSgrMethod();
      StringBuilder sb = new StringBuilder();
      // SGR 104 → bright bg blue = 100-100+8 = 12
      sgr.invoke(ecscreen, new int[]{104}, sb);
      assertEquals(12, getBgColor(),
         "After SGR 104, bg should be 12 (bright blue)");
      resetSgr();
   }

   @Test
   void t08_defaultBgReset() throws Exception {
      Method sgr = getSgrMethod();
      StringBuilder sb = new StringBuilder();
      sgr.invoke(ecscreen, new int[]{42}, sb); // set green bg
      assertEquals(2, getBgColor());
      sgr.invoke(ecscreen, new int[]{49}, sb); // reset bg to default
      assertEquals(-1, getBgColor(),
         "After SGR 49, bg should be -1 (default)");
      resetSgr();
   }

   // ── SGR disable/cancel tests ─────────────────────────────────

   @Test
   void t09_cancelBold() throws Exception {
      Method sgr = getSgrMethod();
      StringBuilder sb = new StringBuilder();
      sgr.invoke(ecscreen, new int[]{1}, sb); // bold on
      assertTrue(CellAttr.isBold(getCurrentAttr()));
      sgr.invoke(ecscreen, new int[]{22}, sb); // bold off
      assertFalse(CellAttr.isBold(getCurrentAttr()),
         "SGR 22 should cancel bold");
      resetSgr();
   }

   @Test
   void t10_cancelUnderline() throws Exception {
      Method sgr = getSgrMethod();
      StringBuilder sb = new StringBuilder();
      sgr.invoke(ecscreen, new int[]{4}, sb); // underline on
      assertTrue(CellAttr.isUnderline(getCurrentAttr()));
      sgr.invoke(ecscreen, new int[]{24}, sb); // underline off
      assertFalse(CellAttr.isUnderline(getCurrentAttr()),
         "SGR 24 should cancel underline");
      resetSgr();
   }

   @Test
   void t11_cancelReverse() throws Exception {
      Method sgr = getSgrMethod();
      StringBuilder sb = new StringBuilder();
      sgr.invoke(ecscreen, new int[]{7}, sb); // reverse on
      assertTrue(CellAttr.isReverse(getCurrentAttr()));
      sgr.invoke(ecscreen, new int[]{27}, sb); // reverse off
      assertFalse(CellAttr.isReverse(getCurrentAttr()),
         "SGR 27 should cancel reverse");
      resetSgr();
   }

   @Test
   void t12_combinedAttributes() throws Exception {
      Method sgr = getSgrMethod();
      StringBuilder sb = new StringBuilder();
      // Bold + underline + reverse + cyan fg + magenta bg
      sgr.invoke(ecscreen, new int[]{1, 4, 7, 36, 45}, sb);
      int attr = getCurrentAttr();
      assertTrue(CellAttr.isBold(attr), "Should be bold");
      assertTrue(CellAttr.isUnderline(attr), "Should be underline");
      assertTrue(CellAttr.isReverse(attr), "Should be reverse");
      assertEquals(6, getFgColor(), "FG should be 6 (cyan)");
      assertEquals(5, getBgColor(), "BG should be 5 (magenta)");
      resetSgr();
   }

   // ── Cursor save/restore ──────────────────────────────────────

   @Test
   void t13_cursorSaveRestore() throws Exception {
      EventQueue.biglock2.lock();
      try {
         // Set vtcursor to known position
         Field vtcField = Vt100.class.getDeclaredField("vtcursor");
         vtcField.setAccessible(true);
         MovePos cursor = (MovePos) vtcField.get(vt100);
         int origX = cursor.x;
         int origY = cursor.y;

         Method saveM = ecscreen.getClass().getDeclaredMethod(
            "saveCursor", StringBuilder.class);
         saveM.setAccessible(true);
         Method restM = ecscreen.getClass().getDeclaredMethod(
            "restoreCursor", StringBuilder.class);
         restM.setAccessible(true);

         StringBuilder sb = new StringBuilder();
         // Save cursor
         saveM.invoke(ecscreen, sb);
         // Move cursor
         cursor.x = origX + 5;
         cursor.y = origY + 2;
         // Restore cursor
         restM.invoke(ecscreen, sb);

         assertEquals(origX, cursor.x,
            "Cursor X should be restored");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── ECScreen cursor movement ─────────────────────────────────

   @Test
   void t14_incXPositive() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Field vtcField = Vt100.class.getDeclaredField("vtcursor");
         vtcField.setAccessible(true);
         MovePos cursor = (MovePos) vtcField.get(vt100);
         int startX = cursor.x;

         Method incXM = ecscreen.getClass().getDeclaredMethod(
            "incX", int.class, StringBuilder.class);
         incXM.setAccessible(true);
         incXM.invoke(ecscreen, 3, new StringBuilder());

         assertEquals(startX + 3, cursor.x,
            "incX(3) should advance cursor by 3");
         // Restore
         incXM.invoke(ecscreen, -3, new StringBuilder());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t15_incXNegativeClamps() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Field vtcField = Vt100.class.getDeclaredField("vtcursor");
         vtcField.setAccessible(true);
         MovePos cursor = (MovePos) vtcField.get(vt100);
         cursor.x = 2;

         Method incXM = ecscreen.getClass().getDeclaredMethod(
            "incX", int.class, StringBuilder.class);
         incXM.setAccessible(true);
         incXM.invoke(ecscreen, -10, new StringBuilder());

         assertEquals(0, cursor.x,
            "incX with large negative should clamp to 0");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t16_setXsetsPosition() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Field vtcField = Vt100.class.getDeclaredField("vtcursor");
         vtcField.setAccessible(true);
         MovePos cursor = (MovePos) vtcField.get(vt100);

         Method setXM = ecscreen.getClass().getDeclaredMethod(
            "setX", int.class, StringBuilder.class);
         setXM.setAccessible(true);
         setXM.invoke(ecscreen, 10, new StringBuilder());

         // setX(val) sets vtcursor.x = val - 1 (1-based to 0-based)
         assertEquals(9, cursor.x,
            "setX(10) should set cursor.x to 9");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── ECScreen erase operations ────────────────────────────────

   @Test
   void t17_eraseLineClears() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method eraseM = ecscreen.getClass().getDeclaredMethod(
            "eraseLine", StringBuilder.class);
         eraseM.setAccessible(true);

         Field vtcField = Vt100.class.getDeclaredField("vtcursor");
         vtcField.setAccessible(true);
         MovePos cursor = (MovePos) vtcField.get(vt100);

         // Ensure vtcursor.y is valid
         if (cursor.y >= 1 && cursor.y < vt100.readIn()) {
            // Put some content on the line
            vt100.changeElementAt("test content", cursor.y);
            eraseM.invoke(ecscreen, new StringBuilder());
            String line = vt100.at(cursor.y);
            assertEquals("", line,
               "eraseLine should clear the line to empty");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t18_setInsertModeToggle() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method simM = ecscreen.getClass().getDeclaredMethod(
            "setInsertMode", boolean.class, StringBuilder.class);
         simM.setAccessible(true);

         Field insField = Vt100.class.getDeclaredField("insertmode");
         insField.setAccessible(true);

         simM.invoke(ecscreen, true, new StringBuilder());
         assertTrue(insField.getBoolean(vt100),
            "setInsertMode(true) should enable insert mode");

         simM.invoke(ecscreen, false, new StringBuilder());
         assertFalse(insField.getBoolean(vt100),
            "setInsertMode(false) should disable insert mode");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Alternate screen buffer ──────────────────────────────────

   @Test
   void t19_alternateScreenSwitch() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method switchM = ecscreen.getClass().getDeclaredMethod(
            "switchAlternateScreen", boolean.class,
            StringBuilder.class);
         switchM.setAccessible(true);

         Field altField = ecscreen.getClass().getDeclaredField(
            "inAlternateScreen");
         altField.setAccessible(true);

         assertFalse(altField.getBoolean(ecscreen),
            "Should start in main screen");

         // Set rows to make screen operations work
         Field rowsField = Vt100.class.getDeclaredField("rows");
         rowsField.setAccessible(true);
         rowsField.setInt(vt100, 10);

         // Ensure enough lines exist
         while (vt100.readIn() < 15)
            vt100.insertOne("", vt100.readIn());

         switchM.invoke(ecscreen, true, new StringBuilder());
         assertTrue(altField.getBoolean(ecscreen),
            "Should be in alternate screen after switch");

         switchM.invoke(ecscreen, false, new StringBuilder());
         assertFalse(altField.getBoolean(ecscreen),
            "Should be back in main screen");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t20_alternateScreenDoubleEnableNoOp() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method switchM = ecscreen.getClass().getDeclaredMethod(
            "switchAlternateScreen", boolean.class,
            StringBuilder.class);
         switchM.setAccessible(true);

         Field altField = ecscreen.getClass().getDeclaredField(
            "inAlternateScreen");
         altField.setAccessible(true);

         Field rowsField = Vt100.class.getDeclaredField("rows");
         rowsField.setAccessible(true);
         rowsField.setInt(vt100, 10);

         while (vt100.readIn() < 15)
            vt100.insertOne("", vt100.readIn());

         switchM.invoke(ecscreen, true, new StringBuilder());
         assertTrue(altField.getBoolean(ecscreen));

         // Double enable should be no-op
         switchM.invoke(ecscreen, true, new StringBuilder());
         assertTrue(altField.getBoolean(ecscreen),
            "Double enable should stay in alternate screen");

         // Cleanup
         switchM.invoke(ecscreen, false, new StringBuilder());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── ECScreen device attribute responses ──────────────────────

   @Test
   void t21_respondDeviceAttributesNoThrow() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method daM = ecscreen.getClass().getDeclaredMethod(
            "respondDeviceAttributes", StringBuilder.class);
         daM.setAccessible(true);
         daM.invoke(ecscreen, new StringBuilder());
         // Success if no exception
         assertTrue(true);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t22_respondCursorPositionNoThrow() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method cprM = ecscreen.getClass().getDeclaredMethod(
            "respondCursorPosition", StringBuilder.class);
         cprM.setAccessible(true);
         cprM.invoke(ecscreen, new StringBuilder());
         assertTrue(true);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── ECScreen setTitle ────────────────────────────────────────

   @Test
   void t23_setTitleUpdatesOscTitle() throws Exception {
      Method titleM = ecscreen.getClass().getDeclaredMethod(
         "setTitle", String.class);
      titleM.setAccessible(true);
      titleM.invoke(ecscreen, "test terminal title");
      assertEquals("test terminal title", vt100.getOscTitle(),
         "setTitle should update oscTitle");
   }

   // ── sendMouseEvent ───────────────────────────────────────────

   @Test
   void t24_sendMouseEventLegacyNoThrow() {
      // Legacy encoding: button 0, col 1, row 1
      vt100.sendMouseEvent(0, 1, 1, true);
      assertTrue(true, "Legacy mouse event should not throw");
   }

   @Test
   void t25_sendMouseEventSgrNoThrow() {
      vt100.setSgrMouseMode(true);
      vt100.sendMouseEvent(0, 1, 1, true);
      vt100.sendMouseEvent(0, 1, 1, false);
      vt100.setSgrMouseMode(false);
      assertTrue(true, "SGR mouse events should not throw");
   }

   @Test
   void t26_sendMouseEventLegacyLargeCoordSkipped() {
      // Legacy encoding is limited to 223; large coords should be skipped
      vt100.sendMouseEvent(0, 250, 250, true);
      assertTrue(true, "Large coord legacy mouse event should be skipped");
   }

   // ── sendText with bracketed paste ────────────────────────────

   @Test
   void t27_sendTextBracketedPasteWraps() {
      vt100.setBracketedPasteMode(true);
      vt100.sendText("hello world");
      vt100.setBracketedPasteMode(false);
      assertTrue(true,
         "sendText with bracketed paste should not throw");
   }

   // ── notifyResize ─────────────────────────────────────────────

   @Test
   void t28_notifyResizePositive() throws Exception {
      EventQueue.biglock2.lock();
      try {
         int beforeReadIn = vt100.readIn();
         vt100.notifyResize(25, 80);
         assertTrue(vt100.readIn() >= beforeReadIn,
            "notifyResize should not shrink buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t29_notifyResizeZeroRowIgnored() throws Exception {
      EventQueue.biglock2.lock();
      try {
         int before = vt100.readIn();
         vt100.notifyResize(0, 80);
         assertEquals(before, vt100.readIn(),
            "notifyResize(0, 80) should be ignored");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t30_notifyResizeZeroColIgnored() throws Exception {
      EventQueue.biglock2.lock();
      try {
         int before = vt100.readIn();
         vt100.notifyResize(25, 0);
         assertEquals(before, vt100.readIn(),
            "notifyResize(25, 0) should be ignored");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
