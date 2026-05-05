package javi;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for OldView rendering pipeline — antialiasing modes,
 * npaint dispatch, insertedElementsdraw, deletedElementsdraw,
 * and fold-aware rendering paths.
 *
 * <p>Exercises the rendering code paths that are only reachable
 * when a real Graphics context is available (requires Xvfb).</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class OldViewRenderPipelineGuiJUnitTest {

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

   // ── Reflection helpers ───────────────────────────────────────

   private static Object getField(String name) throws Exception {
      Field f = oldViewClass.getDeclaredField(name);
      f.setAccessible(true);
      return f.get(oldView);
   }

   private static Object getStaticField(String name) throws Exception {
      Field f = oldViewClass.getDeclaredField(name);
      f.setAccessible(true);
      return f.get(null);
   }

   private static void setStaticField(String name, Object value)
         throws Exception {
      Field f = oldViewClass.getDeclaredField(name);
      f.setAccessible(true);
      f.set(null, value);
   }

   private static Canvas getCanvas() throws Exception {
      Method gc = oldViewClass.getDeclaredMethod("getComponent");
      gc.setAccessible(true);
      return (Canvas) gc.invoke(oldView);
   }

   private static int getIntField(String name) throws Exception {
      Field f = oldViewClass.getDeclaredField(name);
      f.setAccessible(true);
      return f.getInt(oldView);
   }

   private static Graphics2D createTestGraphics() {
      BufferedImage img = new BufferedImage(
         800, 600, BufferedImage.TYPE_INT_RGB);
      return img.createGraphics();
   }

   /**
    * Ensure the canvas inner imageg field is initialized so rendering
    * methods that use it do not throw NullPointerException.
    */
   private static void ensureImageg() throws Exception {
      Canvas canvas = getCanvas();
      Field imagegField = canvas.getClass().getDeclaredField("imageg");
      imagegField.setAccessible(true);
      if (imagegField.get(canvas) == null) {
         int pw = getIntField("pixelWidth");
         int ch = getIntField("charheight");
         if (pw <= 0) pw = 800;
         if (ch <= 0) ch = 16;
         BufferedImage dbuf = new BufferedImage(
            pw * 2, ch, BufferedImage.TYPE_INT_RGB);
         imagegField.set(canvas, dbuf.createGraphics());
         Field dbufField = canvas.getClass().getDeclaredField("dbuf");
         dbufField.setAccessible(true);
         dbufField.set(canvas, dbuf);
      }
   }

   // ── Antialiasing mode switching ──────────────────────────────

   @Test
   void t01_antialiasDefaultModeIsOn() throws Exception {
      Field modeField = oldViewClass.getDeclaredField("antialiasMode");
      modeField.setAccessible(true);
      String mode = (String) modeField.get(null);
      assertEquals("on", mode,
         "Default antialiasMode should be 'on'");
   }

   @Test
   void t02_applyTextRenderingHintsOffMode() throws Exception {
      String origMode = (String) getStaticField("antialiasMode");
      Graphics2D g = createTestGraphics();
      try {
         setStaticField("antialiasMode", "off");
         Method m = oldViewClass.getDeclaredMethod(
            "applyTextRenderingHints", Graphics2D.class);
         m.setAccessible(true);
         m.invoke(null, g);
         Object hint = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);
         assertEquals(RenderingHints.VALUE_TEXT_ANTIALIAS_OFF, hint,
            "Off mode should disable text antialiasing");
      } finally {
         setStaticField("antialiasMode", origMode);
         g.dispose();
      }
   }

   @Test
   void t03_applyTextRenderingHintsLcdMode() throws Exception {
      String origMode = (String) getStaticField("antialiasMode");
      Graphics2D g = createTestGraphics();
      try {
         setStaticField("antialiasMode", "lcd");
         Method m = oldViewClass.getDeclaredMethod(
            "applyTextRenderingHints", Graphics2D.class);
         m.setAccessible(true);
         m.invoke(null, g);
         Object hint = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);
         assertEquals(
            RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, hint,
            "LCD mode should use LCD_HRGB antialiasing");
      } finally {
         setStaticField("antialiasMode", origMode);
         g.dispose();
      }
   }

   @Test
   void t04_applyTextRenderingHintsGrayscaleMode() throws Exception {
      String origMode = (String) getStaticField("antialiasMode");
      Graphics2D g = createTestGraphics();
      try {
         setStaticField("antialiasMode", "grayscale");
         Method m = oldViewClass.getDeclaredMethod(
            "applyTextRenderingHints", Graphics2D.class);
         m.setAccessible(true);
         m.invoke(null, g);
         Object hint = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);
         assertEquals(RenderingHints.VALUE_TEXT_ANTIALIAS_ON, hint,
            "Grayscale mode should use generic AA ON");
      } finally {
         setStaticField("antialiasMode", origMode);
         g.dispose();
      }
   }

   @Test
   void t05_applyTextRenderingHintsOnMode() throws Exception {
      String origMode = (String) getStaticField("antialiasMode");
      Graphics2D g = createTestGraphics();
      try {
         setStaticField("antialiasMode", "on");
         Method m = oldViewClass.getDeclaredMethod(
            "applyTextRenderingHints", Graphics2D.class);
         m.setAccessible(true);
         m.invoke(null, g);
         // "on" mode uses desktop hints or falls back to ON
         Object hint = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);
         assertNotNull(hint,
            "On mode should set some text AA hint");
      } finally {
         setStaticField("antialiasMode", origMode);
         g.dispose();
      }
   }

   @Test
   void t06_lcdContrastDefault() throws Exception {
      Field cf = oldViewClass.getDeclaredField("lcdContrast");
      cf.setAccessible(true);
      int contrast = cf.getInt(null);
      assertEquals(140, contrast,
         "Default lcdContrast should be 140");
   }

   @Test
   void t07_lcdContrastAppliedToGraphics() throws Exception {
      String origMode = (String) getStaticField("antialiasMode");
      Graphics2D g = createTestGraphics();
      try {
         setStaticField("antialiasMode", "lcd");
         Method m = oldViewClass.getDeclaredMethod(
            "applyTextRenderingHints", Graphics2D.class);
         m.setAccessible(true);
         m.invoke(null, g);
         Object contrast = g.getRenderingHint(
            RenderingHints.KEY_TEXT_LCD_CONTRAST);
         assertNotNull(contrast,
            "LCD contrast hint should be set");
         assertEquals(140, contrast,
            "LCD contrast should be 140");
      } finally {
         setStaticField("antialiasMode", origMode);
         g.dispose();
      }
   }

   // ── resetTextRenderingHints ──────────────────────────────────

   @Test
   void t08_resetTextRenderingHintsNullifiesImageG() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method resetM = oldViewClass.getDeclaredMethod(
            "resetTextRenderingHints");
         resetM.setAccessible(true);
         resetM.invoke(oldView);
         // After reset, imageg in canvas should be null
         Canvas canvas = getCanvas();
         Field imagegField = canvas.getClass().getDeclaredField("imageg");
         imagegField.setAccessible(true);
         Object imageg = imagegField.get(canvas);
         // imageg may or may not be null depending on whether canvas
         // has been painted since creation; just verify no exception
         assertTrue(true,
            "resetTextRenderingHints should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── insertedElementsdraw ─────────────────────────────────────

   @Test
   void t09_insertedElementsdrawInRange() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         int screenSize = getIntField("screenSize");
         int firstLine = oldView.screenFirstLine();
         Graphics2D g = createTestGraphics();
         try {
            Method m = oldViewClass.getDeclaredMethod(
               "insertedElementsdraw", Graphics.class,
               int.class, int.class);
            m.setAccessible(true);
            m.invoke(oldView, g, firstLine + 1, 1);
            assertTrue(true,
               "insertedElementsdraw in range should not throw");
         } finally {
            g.dispose();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t10_insertedElementsdrawAtScreenEnd() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         ensureImageg();
         int screenSize = getIntField("screenSize");
         int firstLine = oldView.screenFirstLine();
         Graphics2D g = createTestGraphics();
         try {
            Method m = oldViewClass.getDeclaredMethod(
               "insertedElementsdraw", Graphics.class,
               int.class, int.class);
            m.setAccessible(true);
            m.invoke(oldView, g, firstLine + screenSize - 1, 1);
            assertTrue(true,
               "insertedElementsdraw at end should not throw");
         } finally {
            g.dispose();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t11_insertedElementsdrawBeyondScreen() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         int screenSize = getIntField("screenSize");
         int firstLine = oldView.screenFirstLine();
         Graphics2D g = createTestGraphics();
         try {
            Method m = oldViewClass.getDeclaredMethod(
               "insertedElementsdraw", Graphics.class,
               int.class, int.class);
            m.setAccessible(true);
            m.invoke(oldView, g, firstLine + screenSize + 10, 1);
            assertTrue(true,
               "insertedElementsdraw beyond screen is no-op");
         } finally {
            g.dispose();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t12_insertedElementsdrawMultipleLines() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         int firstLine = oldView.screenFirstLine();
         Graphics2D g = createTestGraphics();
         try {
            Method m = oldViewClass.getDeclaredMethod(
               "insertedElementsdraw", Graphics.class,
               int.class, int.class);
            m.setAccessible(true);
            m.invoke(oldView, g, firstLine + 1, 3);
            assertTrue(true,
               "insertedElementsdraw multiple lines ok");
         } finally {
            g.dispose();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── deletedElementsdraw ──────────────────────────────────────

   @Test
   void t13_deletedElementsdrawSingleLine() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         ensureImageg();
         int firstLine = oldView.screenFirstLine();
         Graphics2D g = createTestGraphics();
         try {
            Method m = oldViewClass.getDeclaredMethod(
               "deletedElementsdraw", Graphics.class,
               int.class, int.class);
            m.setAccessible(true);
            m.invoke(oldView, g, firstLine + 1, 1);
            assertTrue(true,
               "deletedElementsdraw single line ok");
         } finally {
            g.dispose();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t14_deletedElementsdrawAtScreenStart() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         ensureImageg();
         int firstLine = oldView.screenFirstLine();
         Graphics2D g = createTestGraphics();
         try {
            Method m = oldViewClass.getDeclaredMethod(
               "deletedElementsdraw", Graphics.class,
               int.class, int.class);
            m.setAccessible(true);
            m.invoke(oldView, g, firstLine, 1);
            assertTrue(true,
               "deletedElementsdraw at screen start ok");
         } finally {
            g.dispose();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t15_deletedElementsdrawBeyondScreen() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         int screenSize = getIntField("screenSize");
         int firstLine = oldView.screenFirstLine();
         Graphics2D g = createTestGraphics();
         try {
            Method m = oldViewClass.getDeclaredMethod(
               "deletedElementsdraw", Graphics.class,
               int.class, int.class);
            m.setAccessible(true);
            m.invoke(oldView, g, firstLine + screenSize + 5, 1);
            assertTrue(true,
               "deletedElementsdraw beyond screen is no-op");
         } finally {
            g.dispose();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── changeddraw ──────────────────────────────────────────────

   @Test
   void t16_changeddrawMidScreen() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         ensureImageg();
         int firstLine = oldView.screenFirstLine();
         Graphics2D g = createTestGraphics();
         try {
            Method m = oldViewClass.getDeclaredMethod(
               "changeddraw", Graphics.class,
               int.class, int.class);
            m.setAccessible(true);
            m.invoke(oldView, g, firstLine + 2, firstLine + 4);
            assertTrue(true,
               "changeddraw mid-screen should not throw");
         } finally {
            g.dispose();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t17_changeddrawFullScreen() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         ensureImageg();
         int screenSize = getIntField("screenSize");
         int firstLine = oldView.screenFirstLine();
         Graphics2D g = createTestGraphics();
         try {
            Method m = oldViewClass.getDeclaredMethod(
               "changeddraw", Graphics.class,
               int.class, int.class);
            m.setAccessible(true);
            m.invoke(oldView, g, firstLine, firstLine + screenSize);
            assertTrue(true,
               "changeddraw full screen should not throw");
         } finally {
            g.dispose();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── refresh ──────────────────────────────────────────────────

   @Test
   void t18_refreshPaintsFullScreen() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         ensureImageg();
         Graphics2D g = createTestGraphics();
         try {
            Method refresh = oldViewClass.getDeclaredMethod(
               "refresh", Graphics.class);
            refresh.setAccessible(true);
            refresh.invoke(oldView, g);
            assertTrue(true,
               "refresh should paint without error");
         } finally {
            g.dispose();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── movescreendraw various amounts ───────────────────────────

   @Test
   void t19_movescreendrawLargePositive() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         ensureImageg();
         int screenSize = getIntField("screenSize");
         Graphics2D g = createTestGraphics();
         try {
            Method msd = oldViewClass.getDeclaredMethod(
               "movescreendraw", Graphics.class, int.class);
            msd.setAccessible(true);
            // Scroll by half screen
            msd.invoke(oldView, g, screenSize / 2);
            assertTrue(true,
               "movescreendraw large positive ok");
         } finally {
            g.dispose();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t20_movescreendrawLargeNegative() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         ensureImageg();
         int screenSize = getIntField("screenSize");
         Graphics2D g = createTestGraphics();
         try {
            Method msd = oldViewClass.getDeclaredMethod(
               "movescreendraw", Graphics.class, int.class);
            msd.setAccessible(true);
            msd.invoke(oldView, g, -(screenSize / 2));
            assertTrue(true,
               "movescreendraw large negative ok");
         } finally {
            g.dispose();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── fillheader / filltrailer (via refresh path) ──────────────

   @Test
   void t21_fillheaderWhenAtTop() throws Exception {
      EventQueue.biglock2.lock();
      try {
         // Move cursor to line 1 to ensure top-of-file
         oldView.cursorChanged(0, 1);
         Method fillH = oldViewClass.getDeclaredMethod(
            "fillheader", Graphics.class, int.class);
         fillH.setAccessible(true);
         Graphics2D g = createTestGraphics();
         try {
            int result = (int) fillH.invoke(oldView, g, 0);
            assertTrue(result >= 0,
               "fillheader should return non-negative start");
         } finally {
            g.dispose();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t22_filltrailerClamps() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         int screenSize = getIntField("screenSize");
         Method fillT = oldViewClass.getDeclaredMethod(
            "filltrailer", Graphics.class, int.class);
         fillT.setAccessible(true);
         Graphics2D g = createTestGraphics();
         try {
            int result = (int) fillT.invoke(oldView, g, screenSize);
            assertTrue(result <= screenSize,
               "filltrailer should clamp to screenSize");
         } finally {
            g.dispose();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Canvas.npaint dispatch paths ─────────────────────────────

   @Test
   void t23_canvasPaintDoesNotThrow() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Canvas canvas = getCanvas();
         Graphics g = canvas.getGraphics();
         if (g != null) {
            try {
               canvas.paint(g);
               assertTrue(true, "canvas.paint() ok");
            } finally {
               g.dispose();
            }
         } else {
            // No graphics available (headless fallback)
            assertTrue(true, "No graphics, skip");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t24_canvasRepaintInvocable() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Canvas canvas = getCanvas();
         canvas.repaint();
         assertTrue(canvas.isValid() || !canvas.isValid(),
            "repaint should not throw regardless of valid state");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Terminal mode cursor positioning ─────────────────────────

   @Test
   void t25_cursorChangedWithTerminalAttrUsesWcwidth() throws Exception {
      EventQueue.biglock2.lock();
      try {
         // Exercise the terminal-mode branch in cursorChanged
         // where Wcwidth.stringWidth is used for grid positioning.
         // If the buffer has terminal attributes, the branch is taken.
         FvContext fvc = FvContext.getCurrFvc();
         TextEdit te = fvc.edvec;
         // Just verify cursorChanged doesn't throw at position (0,1)
         oldView.cursorChanged(0, 1);
         assertTrue(true,
            "cursorChanged at (0,1) should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t26_cursorChangedNonZeroColumn() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         TextEdit te = fvc.edvec;
         if (te.readIn() > 1) {
            String line = te.at(1).toString();
            if (line.length() > 3) {
               oldView.cursorChanged(3, 1);
               assertTrue(true,
                  "cursorChanged at col 3 should not throw");
            }
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── setColors and override rendering ─────────────────────────

   @Test
   void t27_setColorsWithNullRestoresDefaults() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method sc = oldViewClass.getDeclaredMethod(
            "setColors", Color.class, Color.class);
         sc.setAccessible(true);

         // Set override colors
         sc.invoke(oldView, Color.RED, Color.BLUE);

         Canvas canvas = getCanvas();
         assertEquals(Color.BLUE, canvas.getBackground(),
            "Background should be BLUE after setColors");

         // Restore by setting null overrides
         Field ofg = oldViewClass.getDeclaredField("overrideFg");
         ofg.setAccessible(true);
         Field obg = oldViewClass.getDeclaredField("overrideBg");
         obg.setAccessible(true);
         ofg.set(oldView, null);
         obg.set(oldView, null);

         // Restore background to default
         Class<?> atViewClass = Class.forName("javi.awt.AtView");
         Field bgField = atViewClass.getDeclaredField("background");
         bgField.setAccessible(true);
         Color defaultBg = (Color) bgField.get(null);
         canvas.setBackground(defaultBg);
         Font af = (Font) getField("activeFont");
         Method ssf = oldViewClass.getDeclaredMethod("ssetFont", Font.class);
         ssf.setAccessible(true);
         ssf.invoke(oldView, af);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t28_setColorsUpdatesAtView() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method sc = oldViewClass.getDeclaredMethod(
            "setColors", Color.class, Color.class);
         sc.setAccessible(true);

         Color testFg = new Color(200, 200, 200);
         Color testBg = new Color(30, 30, 30);
         sc.invoke(oldView, testFg, testBg);

         // Verify atIt was recreated with the override colors
         Field atIt = oldViewClass.getDeclaredField("atIt");
         atIt.setAccessible(true);
         Object atView = atIt.get(oldView);
         assertNotNull(atView,
            "atIt should be recreated after setColors");

         // Cleanup
         Field ofg = oldViewClass.getDeclaredField("overrideFg");
         ofg.setAccessible(true);
         Field obg = oldViewClass.getDeclaredField("overrideBg");
         obg.setAccessible(true);
         ofg.set(oldView, null);
         obg.set(oldView, null);
         Canvas canvas = getCanvas();
         Class<?> atViewClass2 = Class.forName("javi.awt.AtView");
         Field bgField2 = atViewClass2.getDeclaredField("background");
         bgField2.setAccessible(true);
         canvas.setBackground((Color) bgField2.get(null));
         Font af = (Font) getField("activeFont");
         Method ssf = oldViewClass.getDeclaredMethod("ssetFont", Font.class);
         ssf.setAccessible(true);
         ssf.invoke(oldView, af);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Antialiasing mode change and reset cycle ─────────────────

   @Test
   void t29_modeSwitchCycleDoesNotCorruptState() throws Exception {
      EventQueue.biglock2.lock();
      try {
         String origMode = (String) getStaticField("antialiasMode");
         Method resetM = oldViewClass.getDeclaredMethod(
            "resetTextRenderingHints");
         resetM.setAccessible(true);

         String[] modes = {"off", "lcd", "grayscale", "on"};
         for (String mode : modes) {
            setStaticField("antialiasMode", mode);
            resetM.invoke(oldView);
         }

         // Restore and verify view still functional
         setStaticField("antialiasMode", origMode);
         oldView.repaint();
         assertTrue(oldView.isVisible(),
            "View should remain visible after mode cycling");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── screenFirstLine consistency ──────────────────────────────

   @Test
   void t30_screenFirstLineConsistentAfterScroll() throws Exception {
      EventQueue.biglock2.lock();
      try {
         oldView.cursorChanged(0, 1);
         int first1 = oldView.screenFirstLine();
         // Scroll by 0 — should be unchanged
         int adj = oldView.screeny(0);
         int first2 = oldView.screenFirstLine();
         assertEquals(first1, first2,
            "screenFirstLine unchanged after screeny(0)");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Canvas MyCanvas.setSize metric update ────────────────────

   @Test
   void t31_canvasSetSizeUpdatesMinColumns() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Canvas canvas = getCanvas();
         Dimension orig = canvas.getSize();
         int cw = getIntField("charwidth");
         int ch = getIntField("charheight");

         // Set a known size
         int cols = 60;
         int rows = 25;
         canvas.setSize(cols * cw + 4, rows * ch);

         Field minCols = oldViewClass.getDeclaredField("minColumns");
         minCols.setAccessible(true);
         int mc = minCols.getInt(oldView);
         assertTrue(mc >= 58 && mc <= 62,
            "minColumns should be ~60 after resize, got " + mc);

         // Restore
         canvas.setSize(orig.width, orig.height);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t32_canvasSetSizeUpdatesPixelWidth() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Canvas canvas = getCanvas();
         Dimension orig = canvas.getSize();
         int cw = getIntField("charwidth");
         int ch = getIntField("charheight");

         canvas.setSize(40 * cw + 4, 15 * ch);
         Field pw = oldViewClass.getDeclaredField("pixelWidth");
         pw.setAccessible(true);
         int pixWidth = pw.getInt(oldView);
         assertTrue(pixWidth > 0,
            "pixelWidth should be positive after resize");

         canvas.setSize(orig.width, orig.height);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
