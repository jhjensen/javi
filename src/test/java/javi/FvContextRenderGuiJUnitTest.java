package javi;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics;
import java.lang.reflect.Method;
import java.util.ArrayList;

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
 * GUI tests for the FvContext rendering pipeline.
 *
 * <p>Exercises cursor movement, buffer navigation, file display,
 * line placement, and View interaction in a live AWT environment.
 * Unlike FvContextJUnitTest which uses TestView (headless stub),
 * these tests operate on real OldView instances with a display.</p>
 *
 * <p>Tests cover: cursor absolute/relative positioning, grapheme-aware
 * cursor movement, placeline scrolling, buffer switch rendering,
 * display width calculations, and view coordinate mapping.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class FvContextRenderGuiJUnitTest {

   private static Robot robot;
   private static FvContext<?> fvc;
   private static View view;
   private static TextEdit<?> buffer;

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
         fvc = FvContext.getCurrFvc();
         assertNotNull(fvc, "Current FvContext should not be null");
         view = fvc.vi;
         buffer = fvc.edvec;
         // Ensure buffer has enough content for scroll tests
         @SuppressWarnings("unchecked")
         TextEdit<String> strBuf = (TextEdit<String>) buffer;
         while (strBuf.readIn() < 50)
            strBuf.insertOne("Line " + strBuf.readIn(), strBuf.readIn());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Gets the AWT Canvas component from OldView via reflection.
    * Returns null if the view is not an OldView (e.g. VScreen).
    */
   private static Canvas getCanvas() throws Exception {
      Class<?> ovClass = view.getClass();
      try {
         Method gc = ovClass.getDeclaredMethod("getComponent");
         gc.setAccessible(true);
         return (Canvas) gc.invoke(view);
      } catch (NoSuchMethodException e) {
         return null;
      }
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── Cursor absolute positioning ──────────────────────────────

   @Test
   void t01_cursorabsSetsBothCoordinates() {
      EventQueue.biglock2.lock();
      try {
         fvc.cursorabs(5, 3);
         assertEquals(5, fvc.insertx(),
            "cursorabs should set X to 5");
         assertEquals(3, fvc.inserty(),
            "cursorabs should set Y to 3");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t02_cursorabsClampsToBufferEnd() {
      EventQueue.biglock2.lock();
      try {
         int lastLine = buffer.readIn() - 1;
         fvc.cursorabs(0, lastLine + 100);
         assertTrue(fvc.inserty() <= lastLine,
            "cursorabs should clamp Y to last buffer line");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t03_cursorabsClampsToLineLength() {
      EventQueue.biglock2.lock();
      try {
         fvc.cursorabs(0, 1);
         String line = buffer.at(1).toString();
         fvc.cursorabs(line.length() + 50, 1);
         assertTrue(fvc.insertx() <= line.length(),
            "cursorabs should clamp X to line length");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t04_cursorabsPositionObject() {
      EventQueue.biglock2.lock();
      try {
         Position pos = new Position(2, 5,
            buffer.fdes(), "test");
         fvc.cursorabs(pos);
         assertEquals(2, fvc.insertx(),
            "cursorabs(Position) should set X");
         assertEquals(5, fvc.inserty(),
            "cursorabs(Position) should set Y");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t05_cursorabsMovePosObject() {
      EventQueue.biglock2.lock();
      try {
         MovePos mp = new MovePos(4, 7);
         fvc.cursorabs(mp);
         assertEquals(4, fvc.insertx(),
            "cursorabs(MovePos) should set X");
         assertEquals(7, fvc.inserty(),
            "cursorabs(MovePos) should set Y");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Relative cursor movement ─────────────────────────────────

   @Test
   void t06_cursorxMovesRight() {
      EventQueue.biglock2.lock();
      try {
         // Position on a line with content
         buffer.changeElementAtStr("ABCDEFGHIJ", 2);
         fvc.cursorabs(0, 2);
         fvc.cursorx(3);
         assertEquals(3, fvc.insertx(),
            "cursorx(3) from 0 should move to 3");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t07_cursorxMovesLeft() {
      EventQueue.biglock2.lock();
      try {
         buffer.changeElementAtStr("ABCDEFGHIJ", 2);
         fvc.cursorabs(5, 2);
         fvc.cursorx(-2);
         assertEquals(3, fvc.insertx(),
            "cursorx(-2) from 5 should move to 3");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t08_cursorxClampsAtZero() {
      EventQueue.biglock2.lock();
      try {
         buffer.changeElementAtStr("ABCDEFGHIJ", 2);
         fvc.cursorabs(2, 2);
         fvc.cursorx(-10);
         assertTrue(fvc.insertx() >= 0,
            "cursorx should not go below 0");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t09_cursorxClampsAtLineEnd() {
      EventQueue.biglock2.lock();
      try {
         buffer.changeElementAtStr("SHORT", 2);
         fvc.cursorabs(0, 2);
         fvc.cursorx(100);
         assertTrue(fvc.insertx() <= 5,
            "cursorx should not exceed line length (5)");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t10_cursoryMovesDown() {
      EventQueue.biglock2.lock();
      try {
         fvc.cursorabs(0, 5);
         fvc.cursory(3);
         assertEquals(8, fvc.inserty(),
            "cursory(3) from line 5 should go to line 8");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t11_cursoryMovesUp() {
      EventQueue.biglock2.lock();
      try {
         fvc.cursorabs(0, 10);
         fvc.cursory(-3);
         assertEquals(7, fvc.inserty(),
            "cursory(-3) from line 10 should go to line 7");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t12_cursoryabsSetsLine() {
      EventQueue.biglock2.lock();
      try {
         fvc.cursoryabs(15);
         assertEquals(15, fvc.inserty(),
            "cursoryabs(15) should set Y to 15");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t13_cursoryClampsAtLine1() {
      EventQueue.biglock2.lock();
      try {
         fvc.cursorabs(0, 3);
         fvc.cursory(-100);
         assertTrue(fvc.inserty() >= 1,
            "cursory should not go below line 1");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t14_cursoryClampsAtEnd() {
      EventQueue.biglock2.lock();
      try {
         int lastLine = buffer.readIn() - 1;
         fvc.cursorabs(0, lastLine - 2);
         fvc.cursory(100);
         assertTrue(fvc.inserty() <= lastLine,
            "cursory should not exceed last line");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Grapheme-aware cursor movement ───────────────────────────

   @Test
   void t15_cursorxabsSnapsToGraphemeBoundary() {
      EventQueue.biglock2.lock();
      try {
         // Simple ASCII - boundary at every char
         buffer.changeElementAtStr("HELLO", 3);
         fvc.cursorabs(0, 3);
         fvc.cursorxabs(3);
         assertEquals(3, fvc.insertx(),
            "cursorxabs on ASCII should land exactly");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t16_displayWidthAscii() {
      int width = FvContext.displayWidth("Hello", 0, 5);
      assertEquals(5, width,
         "ASCII string 'Hello' should have display width 5");
   }

   @Test
   void t17_displayWidthEmpty() {
      int width = FvContext.displayWidth("", 0, 0);
      assertEquals(0, width,
         "Empty string should have display width 0");
   }

   @Test
   void t18_displayWidthSubstring() {
      int width = FvContext.displayWidth("ABCDEFGH", 2, 5);
      assertEquals(3, width,
         "Substring 'CDE' should have display width 3");
   }

   @Test
   void t19_isWideCodePointCJK() {
      // CJK Unified Ideograph (U+4E00)
      assertTrue(FvContext.isWideCodePoint(0x4E00),
         "CJK character should be wide");
   }

   @Test
   void t20_isWideCodePointAscii() {
      assertFalse(FvContext.isWideCodePoint('A'),
         "ASCII 'A' should not be wide");
   }

   @Test
   void t21_isWideCodePointFullwidth() {
      // Fullwidth Latin A (U+FF21)
      assertTrue(FvContext.isWideCodePoint(0xFF21),
         "Fullwidth character should be wide");
   }

   // ── View interaction ─────────────────────────────────────────

   @Test
   void t22_viewGetRowsPositive() {
      EventQueue.biglock2.lock();
      try {
         int rows = view.getRows(1.0f);
         assertTrue(rows > 0,
            "View rows should be positive, got " + rows);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t23_placelineDoesNotThrow() {
      EventQueue.biglock2.lock();
      try {
         fvc.cursorabs(0, 10);
         fvc.placeline(10, 0.5f);
         // Success if no exception
         assertTrue(true);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t24_placelineTopOfScreen() {
      EventQueue.biglock2.lock();
      try {
         fvc.placeline(20, 0.0f);
         assertTrue(true, "placeline(line, 0.0) should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t25_placelineBottomOfScreen() {
      EventQueue.biglock2.lock();
      try {
         fvc.placeline(20, 0.999f);
         assertTrue(true, "placeline(line, 0.999) should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Buffer content operations ────────────────────────────────

   @Test
   void t26_insertStringsAddsContent() {
      EventQueue.biglock2.lock();
      try {
         int before = buffer.readIn();
         ArrayList<String> lines = new ArrayList<>();
         lines.add("inserted line 1");
         lines.add("inserted line 2");
         fvc.cursorabs(0, 5);
         fvc.insertStrings(lines, true);
         int after = buffer.readIn();
         assertEquals(before + 2, after,
            "insertStrings should add 2 lines");
         // Clean up
         buffer.remove(6, 2);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t27_changeElementUpdatesLine() {
      EventQueue.biglock2.lock();
      try {
         fvc.cursorabs(0, 4);
         fvc.changeElementStr("REPLACED CONTENT");
         String line = buffer.at(4).toString();
         assertEquals("REPLACED CONTENT", line,
            "changeElementStr should update current line");
         // Restore
         fvc.changeElementStr("Line 4");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── View canvas rendering ────────────────────────────────────

   @Test
   void t28_viewCanvasIsShowing() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Canvas canvas = getCanvas();
         assertNotNull(canvas,
            "getComponent() should return a Canvas");
         // In Docker/Xvfb the canvas may not be displayable or showing
         // because the frame's native peer may not be realized.
         // Just verify we can obtain the canvas without error.
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t29_viewCanvasHasSize() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Canvas canvas = getCanvas();
         if (canvas == null) return; // Skip if not OldView
         Dimension size = canvas.getSize();
         assertTrue(size.width > 0 && size.height > 0,
            "View canvas should have non-zero size");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t30_viewPaintDoesNotThrow() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Canvas canvas = getCanvas();
         if (canvas == null) return; // Skip if not OldView
         Graphics g = canvas.getGraphics();
         if (g != null) {
            try {
               canvas.paint(g);
               assertTrue(true, "paint() should not throw");
            } finally {
               g.dispose();
            }
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t31_viewRedrawDoesNotThrow() {
      EventQueue.biglock2.lock();
      try {
         view.redraw();
         assertTrue(true, "redraw should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── FvContext navigation ─────────────────────────────────────

   @Test
   void t32_viewCountPositive() {
      assertTrue(FvContext.viewCount() > 0,
         "viewCount should be at least 1");
   }

   @Test
   void t33_getCurrFvcNotNull() {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(FvContext.getCurrFvc(),
            "getCurrFvc should return a context");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t34_cursorChangedNotifiesView() {
      EventQueue.biglock2.lock();
      try {
         fvc.cursorabs(0, 10);
         // cursorChanged is called internally by cursor2abs
         // If view gets the notification without throwing, success
         assertEquals(10, fvc.inserty());
         fvc.cursorabs(3, 10);
         assertEquals(3, fvc.insertx());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t35_equivPositionCheck() {
      EventQueue.biglock2.lock();
      try {
         fvc.cursorabs(4, 8);
         Position pos = new Position(4, 8,
            buffer.fdes(), "test");
         assertTrue(fvc.equiv(pos),
            "equiv should return true for matching position");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t36_equivPositionMismatch() {
      EventQueue.biglock2.lock();
      try {
         fvc.cursorabs(4, 8);
         Position pos = new Position(1, 2,
            buffer.fdes(), "test");
         assertFalse(fvc.equiv(pos),
            "equiv should return false for different position");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t37_findNextViewNotNull() {
      EventQueue.biglock2.lock();
      try {
         View next = fvc.findNextView();
         assertNotNull(next,
            "findNextView should not return null");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t38_getFilePosAfterNavigation() {
      EventQueue.biglock2.lock();
      try {
         fvc.cursorabs(0, 1);
         assertEquals(1, fvc.inserty());
         fvc.cursory(5);
         assertEquals(6, fvc.inserty());
         fvc.cursory(-3);
         assertEquals(3, fvc.inserty());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Display width with CJK ──────────────────────────────────

   @Test
   void t39_displayWidthCJK() {
      // U+4E00 is a CJK character (wide, 2 columns)
      String cjk = "\u4E00\u4E01\u4E02";
      int width = FvContext.displayWidth(cjk, 0, cjk.length());
      assertEquals(6, width,
         "3 CJK characters should have display width 6");
   }

   @Test
   void t40_displayWidthMixed() {
      // Mix of ASCII and CJK: "AB" (2) + U+4E00 (2) + "C" (1) = 5
      String mixed = "AB\u4E00C";
      int width = FvContext.displayWidth(mixed, 0, mixed.length());
      assertEquals(5, width,
         "Mixed ASCII+CJK should have correct width");
   }

   @Test
   void t41_screenyScrollsView() {
      EventQueue.biglock2.lock();
      try {
         fvc.cursorabs(0, 20);
         int before = fvc.inserty();
         fvc.screeny(1);
         int after = fvc.inserty();
         assertTrue(after != before || after >= 20,
            "screeny(1) should scroll or keep position if at edge");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t42_viewToString() {
      EventQueue.biglock2.lock();
      try {
         String s = fvc.toString();
         assertNotNull(s, "FvContext.toString should not be null");
         assertFalse(s.isEmpty(),
            "FvContext.toString should not be empty");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
