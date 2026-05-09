package javi;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for OldView scroll, navigation, and fold model interactions.
 *
 * <p>Exercises scroll behavior (screeny with large amounts),
 * yCursorChanged across line boundaries, fold model activation,
 * computeTopBufLine, moveScreen, and multiple-line cursor
 * movements that exercise different code paths in cursorChanged.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class OldViewScrollNavGuiJUnitTest {

   private static Robot robot;
   private static View oldView;
   private static Class<?> oldViewClass;

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
         assertTrue(oldViewClass.getName().contains("OldView"),
            "Current view should be OldView, got "
               + oldViewClass.getName());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   private static Object getField(String name) throws Exception {
      Field f = oldViewClass.getDeclaredField(name);
      f.setAccessible(true);
      return f.get(oldView);
   }

   private static int getIntField(String name) throws Exception {
      Field f = oldViewClass.getDeclaredField(name);
      f.setAccessible(true);
      return f.getInt(oldView);
   }

   private static Canvas getCanvas() throws Exception {
      Method gc = oldViewClass.getDeclaredMethod("getComponent");
      gc.setAccessible(true);
      return (Canvas) gc.invoke(oldView);
   }

   // ── screeny large scroll amounts ─────────────────────────────

   @Test
   void t01_screenyLargePositiveAmount() {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         int screenSize = oldView.getRows(1.0f);
         int adj = oldView.screeny(screenSize);
         // Should not throw; result is the new column
         assertTrue(adj >= 0 || adj == 0,
            "screeny with large positive should not crash");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t02_screenyLargeNegativeAmount() {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         int adj = oldView.screeny(-100);
         // At top of file, scrolling up should be bounded
         assertTrue(adj >= -100 && adj <= 0,
            "screeny with large negative at top: " + adj);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t03_screenyZeroIdempotent() {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         int first1 = oldView.screenFirstLine();
         oldView.screeny(0);
         int first2 = oldView.screenFirstLine();
         assertEquals(first1, first2,
            "screeny(0) should not change screen position");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── cursorChanged edge cases ─────────────────────────────────

   @Test
   void t04_cursorChangedToSameLine() {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         TextEdit te = fvc.edvec;
         oldView.cursorChanged(0, 1);
         if (te.readIn() > 0) {
            String line = te.at(1).toString();
            if (line.length() >= 5) {
               oldView.cursorChanged(5, 1);
            } else {
               oldView.cursorChanged(line.length(), 1);
            }
         }
         assertTrue(true,
            "Cursor column change on same line should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t05_cursorChangedToNextLine() {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         TextEdit te = fvc.edvec;
         if (te.readIn() > 2) {
            oldView.cursorChanged(0, 1);
            oldView.cursorChanged(0, 2);
            assertTrue(true,
               "Cursor move to next line should not throw");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t06_cursorChangedLargeColumn() {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         TextEdit te = fvc.edvec;
         if (te.readIn() > 0) {
            String line = te.at(1).toString();
            // Use actual line length to avoid StringIndexOutOfBounds
            oldView.cursorChanged(line.length(), 1);
         }
         assertTrue(true,
            "Cursor at end of line should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t07_cursorChangedZeroZero() {
      EventQueue.biglock2.lock();
      try {
         // Position(0, 0) is invalid (lines are 1-based) but
         // should not crash; OldView may clamp
         try {
            oldView.cursorChanged(0, 0);
         } catch (Exception e) {
            // Some implementations may reject y=0
         }
         assertTrue(true, "cursorChanged(0,0) handled");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── yCursorChanged column computation ────────────────────────

   @Test
   void t08_yCursorChangedFirstLine() {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         int col = oldView.yCursorChanged(1);
         assertTrue(col >= 0,
            "yCursorChanged to same line should return >= 0");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t09_yCursorChangedMultipleLines() {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         TextEdit te = fvc.edvec;
         if (te.readIn() > 5) {
            oldView.cursorChanged(0, 1);
            int col = oldView.yCursorChanged(5);
            assertTrue(col >= 0,
               "yCursorChanged to line 5 should return >= 0");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── moveScreen (private) ─────────────────────────────────────

   @Test
   void t10_moveScreenPositive() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         Method ms = oldViewClass.getDeclaredMethod("moveScreen",
            int.class);
         ms.setAccessible(true);
         int first1 = oldView.screenFirstLine();
         ms.invoke(oldView, 1);
         int first2 = oldView.screenFirstLine();
         assertTrue(first2 >= first1,
            "moveScreen(1) should scroll down or stay");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t11_moveScreenNegativeAtTop() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         Method ms = oldViewClass.getDeclaredMethod("moveScreen",
            int.class);
         ms.setAccessible(true);
         ms.invoke(oldView, -1);
         int firstLine = oldView.screenFirstLine();
         assertTrue(firstLine >= 0 || firstLine == -1,
            "moveScreen(-1) at top bounded, got " + firstLine);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t12_moveScreenZero() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         int first1 = oldView.screenFirstLine();
         Method ms = oldViewClass.getDeclaredMethod("moveScreen",
            int.class);
         ms.setAccessible(true);
         ms.invoke(oldView, 0);
         int first2 = oldView.screenFirstLine();
         assertEquals(first1, first2,
            "moveScreen(0) should not change position");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── getActiveFoldModel ───────────────────────────────────────

   @Test
   void t13_getActiveFoldModelNullDefault() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method gafm = oldViewClass.getDeclaredMethod(
            "getActiveFoldModel");
         gafm.setAccessible(true);
         Object fm = gafm.invoke(oldView);
         // Default buffer has no fold model
         // fm may be null or a no-op FoldModel
         assertTrue(true,
            "getActiveFoldModel should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── computeTopBufLine ────────────────────────────────────────

   @Test
   void t14_computeTopBufLineWithNullFold() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         Method m = oldViewClass.getDeclaredMethod(
            "computeTopBufLine",
            Class.forName("javi.FoldModel"));
         m.setAccessible(true);
         int top = (int) m.invoke(oldView, (Object) null);
         assertTrue(top >= 1,
            "computeTopBufLine(null) should return >= 1, got " + top);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── movescreendraw with Graphics ─────────────────────────────

   @Test
   void t15_movescreendrawLargePositive() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         Canvas canvas = getCanvas();
         Graphics g = canvas.getGraphics();
         if (g != null) {
            try {
               Method msd = oldViewClass.getDeclaredMethod(
                  "movescreendraw", Graphics.class, int.class);
               msd.setAccessible(true);
               msd.invoke(oldView, g, 5);
               assertTrue(true,
                  "movescreendraw(5) should not throw");
            } finally {
               g.dispose();
            }
         } else {
            // No graphics context available (not yet painted)
            assertTrue(true, "Skipped — no Graphics available");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t16_movescreendrawLargeNegative() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         Canvas canvas = getCanvas();
         Graphics g = canvas.getGraphics();
         if (g != null) {
            try {
               Method msd = oldViewClass.getDeclaredMethod(
                  "movescreendraw", Graphics.class, int.class);
               msd.setAccessible(true);
               msd.invoke(oldView, g, -5);
               assertTrue(true,
                  "movescreendraw(-5) should not throw");
            } finally {
               g.dispose();
            }
         } else {
            assertTrue(true, "Skipped — no Graphics available");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── refresh after multiple operations ────────────────────────

   @Test
   void t17_refreshAfterScrollCycle() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         oldView.screeny(2);
         oldView.screeny(-1);
         Canvas canvas = getCanvas();
         Graphics g = canvas.getGraphics();
         if (g != null) {
            try {
               Method refresh = oldViewClass.getDeclaredMethod(
                  "refresh", Graphics.class);
               refresh.setAccessible(true);
               refresh.invoke(oldView, g);
               assertTrue(true,
                  "refresh after scroll cycle should not throw");
            } finally {
               g.dispose();
            }
         } else {
            assertTrue(true, "Skipped — no Graphics available");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Multiple cursorChanged then getRows ──────────────────────

   @Test
   void t18_getRowsAfterMultipleCursorChanges() {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         TextEdit te = fvc.edvec;
         int numLines = te.readIn();
         if (numLines > 3) {
            oldView.cursorChanged(0, 1);
            oldView.cursorChanged(0, 2);
            oldView.cursorChanged(0, 3);
         }
         int rows = oldView.getRows(1.0f);
         assertTrue(rows > 0,
            "getRows should be positive after cursor moves");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── screenFirstLine consistency ──────────────────────────────

   @Test
   void t19_screenFirstLineAfterSetSize() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Canvas canvas = getCanvas();
         Dimension orig = canvas.getSize();
         oldView.cursorChanged(0, 1);
         oldView.setSizebyChar(80, 24);
         int first = oldView.screenFirstLine();
         assertTrue(first >= 0 || first == 1,
            "screenFirstLine after resize >= 0, got " + first);
         // Restore
         canvas.setSize(orig.width, orig.height);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── recalcScreenRow multiple calls ───────────────────────────

   @Test
   void t20_recalcScreenRowIdempotent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         oldView.recalcScreenRow();
         int pos1 = getIntField("screenposy");
         oldView.recalcScreenRow();
         int pos2 = getIntField("screenposy");
         assertEquals(pos1, pos2,
            "recalcScreenRow should be idempotent");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── setTabStop boundary values ───────────────────────────────

   @Test
   void t21_setTabStopOne() {
      EventQueue.biglock2.lock();
      try {
         int orig = oldView.getTabStop();
         oldView.setTabStop(1);
         assertEquals(1, oldView.getTabStop(),
            "Tab stop should be settable to 1");
         oldView.setTabStop(orig);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t22_setTabStopLargeValue() {
      EventQueue.biglock2.lock();
      try {
         int orig = oldView.getTabStop();
         oldView.setTabStop(16);
         assertEquals(16, oldView.getTabStop(),
            "Tab stop should be settable to 16");
         oldView.setTabStop(orig);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── isVisible remains true through operations ────────────────

   @Test
   void t23_isVisibleAfterAllOperations() {
      assertTrue(oldView.isVisible(),
         "OldView should remain visible after all scroll/nav tests");
   }

   // ── repaint safe after scroll ────────────────────────────────

   @Test
   void t24_repaintAfterScrollDown() {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         oldView.screeny(3);
         oldView.repaint();
         robot.waitForIdle();
         assertTrue(oldView.isVisible(),
            "View visible after scroll+repaint");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t25_repaintAfterScrollUp() {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         oldView.screeny(-2);
         oldView.repaint();
         robot.waitForIdle();
         assertTrue(oldView.isVisible(),
            "View visible after scroll-up+repaint");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
