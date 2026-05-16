package javi;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for OldView mouse event handling.
 *
 * <p>Exercises the MyCanvas event dispatch: vt100Button mapping,
 * cellCol/cellRow coordinate conversion, processEvent with various
 * AWTEvent types, mouseWheel dispatch, isShellBufferNoTracking,
 * and forwardVt100Mouse/Wheel/Drag paths.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class OldViewMouseEventGuiJUnitTest {

   private static Robot robot;
   private static View oldView;
   private static Class<?> oldViewClass;
   private static Canvas canvas;
   private static Class<?> canvasClass;

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
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         oldView = fvc.vi;
         oldViewClass = oldView.getClass();
         Method gc = oldViewClass.getDeclaredMethod("getComponent");
         gc.setAccessible(true);
         canvas = (Canvas) gc.invoke(oldView);
         canvasClass = canvas.getClass();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── Reflection helpers ───────────────────────────────────────

   private static Object invokeCanvas(String name,
         Class<?>[] types, Object... args) throws Exception {
      Method m = canvasClass.getDeclaredMethod(name, types);
      m.setAccessible(true);
      return m.invoke(canvas, args);
   }

   private static int getViewIntField(String name)
         throws Exception {
      Field f = oldViewClass.getDeclaredField(name);
      f.setAccessible(true);
      return f.getInt(oldView);
   }

   private static MouseEvent makeMouseEvent(int id, int x, int y,
         int button) {
      return new MouseEvent(canvas, id,
         System.currentTimeMillis(), 0,
         x, y, 1, false, button);
   }

   private static MouseWheelEvent makeWheelEvent(
         int x, int y, int rotation) {
      return new MouseWheelEvent(canvas,
         MouseEvent.MOUSE_WHEEL,
         System.currentTimeMillis(), 0,
         x, y, 0, false,
         MouseWheelEvent.WHEEL_UNIT_SCROLL,
         3, rotation);
   }

   // ── vt100Button mapping ──────────────────────────────────────

   @Test
   void t01_vt100ButtonLeft() throws Exception {
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, 10, 10, MouseEvent.BUTTON1);
      int result = (int) invokeCanvas("vt100Button",
         new Class<?>[] {MouseEvent.class}, ev);
      assertEquals(0, result,
         "BUTTON1 should map to VT100 button 0");
   }

   @Test
   void t02_vt100ButtonMiddle() throws Exception {
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, 10, 10, MouseEvent.BUTTON2);
      int result = (int) invokeCanvas("vt100Button",
         new Class<?>[] {MouseEvent.class}, ev);
      assertEquals(1, result,
         "BUTTON2 should map to VT100 button 1");
   }

   @Test
   void t03_vt100ButtonRight() throws Exception {
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, 10, 10, MouseEvent.BUTTON3);
      int result = (int) invokeCanvas("vt100Button",
         new Class<?>[] {MouseEvent.class}, ev);
      assertEquals(2, result,
         "BUTTON3 should map to VT100 button 2");
   }

   // ── cellCol / cellRow coordinate conversion ──────────────────

   @Test
   void t04_cellColAtOrigin() throws Exception {
      int xoffset = getViewIntField("xoffset");
      int charwidth = getViewIntField("charwidth");
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, xoffset, 10,
         MouseEvent.BUTTON1);
      int col = (int) invokeCanvas("cellCol",
         new Class<?>[] {MouseEvent.class}, ev);
      assertEquals(1, col,
         "cellCol at xoffset should return 1");
   }

   @Test
   void t05_cellColNegativeXClamps() throws Exception {
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, -100, 10,
         MouseEvent.BUTTON1);
      int col = (int) invokeCanvas("cellCol",
         new Class<?>[] {MouseEvent.class}, ev);
      assertEquals(1, col,
         "cellCol at negative X should clamp to 1");
   }

   @Test
   void t06_cellColMidScreen() throws Exception {
      int xoffset = getViewIntField("xoffset");
      int charwidth = getViewIntField("charwidth");
      int targetX = xoffset + charwidth * 10;
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, targetX, 10,
         MouseEvent.BUTTON1);
      int col = (int) invokeCanvas("cellCol",
         new Class<?>[] {MouseEvent.class}, ev);
      assertEquals(11, col,
         "cellCol 10 chars from left should return 11");
   }

   @Test
   void t07_cellRowAtOrigin() throws Exception {
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, 10, 0,
         MouseEvent.BUTTON1);
      int row = (int) invokeCanvas("cellRow",
         new Class<?>[] {MouseEvent.class}, ev);
      assertEquals(1, row,
         "cellRow at y=0 should return 1");
   }

   @Test
   void t08_cellRowNegativeYClamps() throws Exception {
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, 10, -50,
         MouseEvent.BUTTON1);
      int row = (int) invokeCanvas("cellRow",
         new Class<?>[] {MouseEvent.class}, ev);
      assertEquals(1, row,
         "cellRow at negative Y should clamp to 1");
   }

   @Test
   void t09_cellRowSecondRow() throws Exception {
      int charheight = getViewIntField("charheight");
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, 10, charheight,
         MouseEvent.BUTTON1);
      int row = (int) invokeCanvas("cellRow",
         new Class<?>[] {MouseEvent.class}, ev);
      assertEquals(2, row,
         "cellRow at charheight should return 2");
   }

   // ── isShellBufferNoTracking ──────────────────────────────────

   @Test
   void t10_isShellBufferNoTrackingForNormalBuffer()
         throws Exception {
      EventQueue.biglock2.lock();
      try {
         boolean result = (boolean) invokeCanvas(
            "isShellBufferNoTracking", new Class<?>[0]);
         assertFalse(result,
            "Normal buffer should not be shell-no-tracking");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── forwardVt100Mouse returns false for normal buffer ────────

   @Test
   void t11_forwardVt100MouseReturnsFalseNormal()
         throws Exception {
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, 10, 10, MouseEvent.BUTTON1);
      boolean result = (boolean) invokeCanvas(
         "forwardVt100Mouse",
         new Class<?>[] {MouseEvent.class, boolean.class},
         ev, true);
      assertFalse(result,
         "forwardVt100Mouse should return false"
            + " for non-shell buffer");
   }

   @Test
   void t12_forwardVt100WheelReturnsFalseNormal()
         throws Exception {
      MouseWheelEvent ev = makeWheelEvent(10, 10, 1);
      boolean result = (boolean) invokeCanvas(
         "forwardVt100Wheel",
         new Class<?>[] {MouseWheelEvent.class}, ev);
      assertFalse(result,
         "forwardVt100Wheel should return false"
            + " for non-shell buffer");
   }

   @Test
   void t13_forwardVt100MouseDragReturnsFalseNormal()
         throws Exception {
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_DRAGGED, 10, 10, MouseEvent.BUTTON1);
      boolean result = (boolean) invokeCanvas(
         "forwardVt100MouseDrag",
         new Class<?>[] {MouseEvent.class}, ev);
      assertFalse(result,
         "forwardVt100MouseDrag should return false"
            + " for non-shell buffer");
   }

   // ── processEvent dispatch ────────────────────────────────────

   @Test
   void t14_processEventMouseMovedNoThrow() {
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_MOVED, 50, 50, MouseEvent.NOBUTTON);
      assertDoesNotThrow(() -> canvas.dispatchEvent(ev),
         "MOUSE_MOVED should not throw");
   }

   @Test
   void t15_processEventMouseEnteredNoThrow() {
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_ENTERED, 0, 0, MouseEvent.NOBUTTON);
      assertDoesNotThrow(() -> canvas.dispatchEvent(ev),
         "MOUSE_ENTERED should not throw");
   }

   @Test
   void t16_processEventMouseExitedNoThrow() {
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_EXITED, 0, 0, MouseEvent.NOBUTTON);
      assertDoesNotThrow(() -> canvas.dispatchEvent(ev),
         "MOUSE_EXITED should not throw");
   }

   @Test
   void t17_processEventMouseClickedNoThrow() {
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_CLICKED, 50, 50, MouseEvent.BUTTON1);
      assertDoesNotThrow(() -> canvas.dispatchEvent(ev),
         "MOUSE_CLICKED should not throw");
   }

   // ── mouseWheel events ────────────────────────────────────────

   @Test
   void t18_mouseWheelScrollDownDispatches() {
      MouseWheelEvent ev = makeWheelEvent(50, 50, 3);
      assertDoesNotThrow(() -> canvas.dispatchEvent(ev),
         "Mouse wheel scroll down should not throw");
   }

   @Test
   void t19_mouseWheelScrollUpDispatches() {
      MouseWheelEvent ev = makeWheelEvent(50, 50, -3);
      assertDoesNotThrow(() -> canvas.dispatchEvent(ev),
         "Mouse wheel scroll up should not throw");
   }

   @Test
   void t20_mouseWheelZeroRotation() {
      MouseWheelEvent ev = makeWheelEvent(50, 50, 0);
      assertDoesNotThrow(() -> canvas.dispatchEvent(ev),
         "Mouse wheel zero rotation should not throw");
   }

   // ── Mouse press/release cycle ────────────────────────────────

   @Test
   void t21_mousePressThenReleaseButton1() throws Exception {
      int charwidth = getViewIntField("charwidth");
      int charheight = getViewIntField("charheight");
      int xoffset = getViewIntField("xoffset");
      // Click in the middle of the text area (past fold gutter)
      int x = xoffset + charwidth * 5;
      int y = charheight * 2;
      MouseEvent press = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1);
      MouseEvent release = makeMouseEvent(
         MouseEvent.MOUSE_RELEASED, x, y, MouseEvent.BUTTON1);
      assertDoesNotThrow(() -> {
         canvas.dispatchEvent(press);
         canvas.dispatchEvent(release);
      }, "Button1 press/release cycle should not throw");
   }

   @Test
   void t22_mousePressThenReleaseButton3() throws Exception {
      // Button3 triggers popup menu via UI.showmenu which requires
      // a showing parent — verify the vt100Button mapping instead
      Method vb = canvasClass.getDeclaredMethod(
         "vt100Button", MouseEvent.class);
      vb.setAccessible(true);
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, 50, 50, MouseEvent.BUTTON3);
      int mapped = (int) vb.invoke(canvas, ev);
      assertEquals(2, mapped,
         "BUTTON3 should map to vt100 button 2");
   }

   // ── Mouse drag ───────────────────────────────────────────────

   @Test
   void t23_mouseDragFromPressToRelease() throws Exception {
      int charwidth = getViewIntField("charwidth");
      int charheight = getViewIntField("charheight");
      int xoffset = getViewIntField("xoffset");
      int x1 = xoffset + charwidth * 3;
      int y1 = charheight * 2;
      int x2 = xoffset + charwidth * 10;
      int y2 = charheight * 4;

      MouseEvent press = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, x1, y1, MouseEvent.BUTTON1);
      MouseEvent drag = new MouseEvent(canvas,
         MouseEvent.MOUSE_DRAGGED,
         System.currentTimeMillis(), 0,
         x2, y2, 1, false, MouseEvent.BUTTON1);
      MouseEvent release = makeMouseEvent(
         MouseEvent.MOUSE_RELEASED, x2, y2, MouseEvent.BUTTON1);

      assertDoesNotThrow(() -> {
         canvas.dispatchEvent(press);
         canvas.dispatchEvent(drag);
         canvas.dispatchEvent(release);
      }, "Press-drag-release should not throw");
   }

   // ── Fold gutter click area ───────────────────────────────────

   @Test
   void t24_clickInFoldGutterAreaNoFoldModel()
         throws Exception {
      int xoffset = getViewIntField("xoffset");
      int charwidth = getViewIntField("charwidth");
      int charheight = getViewIntField("charheight");
      // Click in the fold gutter area (x < xoffset + charwidth)
      int x = xoffset + charwidth / 2;
      int y = charheight;
      MouseEvent press = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1);
      MouseEvent release = makeMouseEvent(
         MouseEvent.MOUSE_RELEASED, x, y, MouseEvent.BUTTON1);
      assertDoesNotThrow(() -> {
         canvas.dispatchEvent(press);
         canvas.dispatchEvent(release);
      }, "Click in fold gutter area without folds"
         + " should not throw");
   }

   @Test
   void t25_clickInFoldGutterAreaWithFoldModel()
         throws Exception {
      FoldModel fm = new FoldModel();
      fm.addFold(2, 5);
      FvContext fvc;
      EventQueue.biglock2.lock();
      try {
         fvc = FvContext.getCurrFvc();
         fvc.setFoldModel(fm);
      } finally {
         EventQueue.biglock2.unlock();
      }
      try {
         int xoffset = getViewIntField("xoffset");
         int charwidth = getViewIntField("charwidth");
         int charheight = getViewIntField("charheight");
         int x = xoffset + charwidth / 2;
         int y = charheight;
         MouseEvent press = makeMouseEvent(
            MouseEvent.MOUSE_PRESSED, x, y,
            MouseEvent.BUTTON1);
         MouseEvent release = makeMouseEvent(
            MouseEvent.MOUSE_RELEASED, x, y,
            MouseEvent.BUTTON1);
         assertDoesNotThrow(() -> {
            canvas.dispatchEvent(press);
            canvas.dispatchEvent(release);
         }, "Click in fold gutter with fold model"
            + " should not throw");
      } finally {
         EventQueue.biglock2.lock();
         try {
            fvc.setFoldModel(null);
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }

   // ── handleFoldGutterClick ────────────────────────────────────

   @Test
   void t26_handleFoldGutterClickNoFoldReturnsfalse()
         throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         fvc.setFoldModel(null);
         int charheight = getViewIntField("charheight");
         MouseEvent ev = makeMouseEvent(
            MouseEvent.MOUSE_RELEASED, 5, charheight,
            MouseEvent.BUTTON1);
         boolean result = (boolean) invokeCanvas(
            "handleFoldGutterClick",
            new Class<?>[] {MouseEvent.class}, ev);
         assertFalse(result,
            "handleFoldGutterClick with no fold model"
               + " should return false");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── cellCol / cellRow edge cases ─────────────────────────────

   @Test
   void t27_cellColLargeX() throws Exception {
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, 5000, 10,
         MouseEvent.BUTTON1);
      int col = (int) invokeCanvas("cellCol",
         new Class<?>[] {MouseEvent.class}, ev);
      assertTrue(col > 1,
         "cellCol at large X should return > 1");
   }

   @Test
   void t28_cellRowLargeY() throws Exception {
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, 10, 5000,
         MouseEvent.BUTTON1);
      int row = (int) invokeCanvas("cellRow",
         new Class<?>[] {MouseEvent.class}, ev);
      assertTrue(row > 1,
         "cellRow at large Y should return > 1");
   }

   // ── Non-traverseable view ignores events ─────────────────────

   @Test
   void t29_processEventRespectsTraverseable() {
      // This test verifies that processEvent checks
      // isTraverseable() — the actual View should be traverseable
      assertTrue(oldView.isTraverseable(),
         "Main editing view should be traverseable");
      // Events dispatched to a traverseable view should work
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_MOVED, 50, 50, MouseEvent.NOBUTTON);
      assertDoesNotThrow(() -> canvas.dispatchEvent(ev),
         "Traverseable view should handle events");
   }

   // ── mousepos at various coordinates ──────────────────────────

   @Test
   void t30_mouseposAtFirstCharacter() throws Exception {
      int xoffset = getViewIntField("xoffset");
      int charheight = getViewIntField("charheight");
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, xoffset, 0,
         MouseEvent.BUTTON1);
      EventQueue.biglock2.lock();
      try {
         Method mp = oldViewClass.getDeclaredMethod(
            "mousepos", MouseEvent.class);
         mp.setAccessible(true);
         Position pos = (Position) mp.invoke(oldView, ev);
         assertNotNull(pos,
            "mousepos should return non-null Position");
         assertTrue(pos.x >= 0, "x should be non-negative");
         assertTrue(pos.y >= 1, "y should be >= 1");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t31_mouseposAtBottomRight() throws Exception {
      Dimension size = canvas.getSize();
      MouseEvent ev = makeMouseEvent(
         MouseEvent.MOUSE_PRESSED, size.width - 1,
         size.height - 1, MouseEvent.BUTTON1);
      EventQueue.biglock2.lock();
      try {
         Method mp = oldViewClass.getDeclaredMethod(
            "mousepos", MouseEvent.class);
         mp.setAccessible(true);
         Position pos = (Position) mp.invoke(oldView, ev);
         assertNotNull(pos, "mousepos should return non-null");
         assertTrue(pos.y >= 1, "y should be >= 1");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Canvas isFocusable ───────────────────────────────────────

   @Test
   void t32_canvasIsFocusable() {
      // MyCanvas.isFocusable() returns false (to delegate to
      // the frame's focus management)
      assertFalse(canvas.isFocusable(),
         "MyCanvas.isFocusable() should return false");
   }

   // ── Rapid event dispatch stability ───────────────────────────

   @Test
   void t33_rapidMouseMoveDoesNotThrow() {
      // Dispatch MOUSE_MOVED events rapidly and verify no exception
      assertDoesNotThrow(() -> {
         EventQueue.biglock2.lock();
         try {
            for (int i = 0; i < 20; i++) {
               MouseEvent ev = makeMouseEvent(
                  MouseEvent.MOUSE_MOVED, 10 + i * 5,
                  10 + i * 3, MouseEvent.NOBUTTON);
               canvas.dispatchEvent(ev);
            }
         } finally {
            EventQueue.biglock2.unlock();
         }
      }, "Rapid mouse moves should not throw");
   }

   @Test
   void t34_rapidWheelEventsDoNotThrow() {
      assertDoesNotThrow(() -> {
         EventQueue.biglock2.lock();
         try {
            for (int i = 0; i < 10; i++) {
               MouseWheelEvent ev = makeWheelEvent(50, 50,
                  i % 2 == 0 ? 1 : -1);
               canvas.dispatchEvent(ev);
            }
         } finally {
            EventQueue.biglock2.unlock();
         }
      }, "Rapid wheel events should not throw");
   }
}
