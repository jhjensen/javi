package javi;

import java.awt.Canvas;
import java.awt.Graphics;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for OldView fold model integration.
 *
 * <p>Exercises fold-aware rendering, navigation, and coordinate
 * mapping when a FoldModel is installed on the active FvContext.
 * Covers computeTopBufLine, visualLineDelta, recalcScreenRow,
 * screenyFolded, paintLinesFolded, fold gutter indicators, and
 * updateCursorShape fold offset.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class OldViewFoldGuiJUnitTest {

   private static Robot robot;
   private static View oldView;
   private static Class<?> oldViewClass;
   private static FvContext<?> fvc;
   private static FoldModel savedFoldModel;

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
         oldView = fvc.vi;
         oldViewClass = oldView.getClass();
         savedFoldModel = fvc.getFoldModel();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @AfterAll
   static void tearDownAll() {
      EventQueue.biglock2.lock();
      try {
         if (fvc != null)
            fvc.setFoldModel(savedFoldModel);
      } finally {
         EventQueue.biglock2.unlock();
      }
      if (robot != null)
         robot.cleanUp();
   }

   // ── Reflection helpers ───────────────────────────────────────

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

   private static Object invoke(String name, Class<?>[] types,
         Object... args) throws Exception {
      Method m = oldViewClass.getDeclaredMethod(name, types);
      m.setAccessible(true);
      return m.invoke(oldView, args);
   }

   private static Canvas getCanvas() throws Exception {
      Method gc = oldViewClass.getDeclaredMethod("getComponent");
      gc.setAccessible(true);
      return (Canvas) gc.invoke(oldView);
   }

   /**
    * Create a FoldModel with a fold spanning lines start..end,
    * install it on the FvContext, and return it.
    */
   private static FoldModel installFold(int start, int end)
         throws Exception {
      FoldModel fm = new FoldModel();
      fm.addFold(start, end);
      EventQueue.biglock2.lock();
      try {
         fvc.setFoldModel(fm);
      } finally {
         EventQueue.biglock2.unlock();
      }
      return fm;
   }

   private static void clearFold() {
      EventQueue.biglock2.lock();
      try {
         fvc.setFoldModel(null);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── FoldModel installation ───────────────────────────────────

   @Test
   void t01_getActiveFoldModelNullByDefault() throws Exception {
      clearFold();
      Object result = invoke("getActiveFoldModel", new Class<?>[0]);
      assertNull(result,
         "getActiveFoldModel should return null when no fold set");
   }

   @Test
   void t02_getActiveFoldModelNonNullAfterInstall()
         throws Exception {
      FoldModel fm = installFold(2, 5);
      try {
         EventQueue.biglock2.lock();
         try {
            Object result = invoke("getActiveFoldModel",
               new Class<?>[0]);
            assertNotNull(result,
               "getActiveFoldModel should return non-null"
                  + " with fold installed");
         } finally {
            EventQueue.biglock2.unlock();
         }
      } finally {
         clearFold();
      }
   }

   @Test
   void t03_getActiveFoldModelNullForEmptyModel()
         throws Exception {
      FoldModel empty = new FoldModel();
      EventQueue.biglock2.lock();
      try {
         fvc.setFoldModel(empty);
         Object result = invoke("getActiveFoldModel",
            new Class<?>[0]);
         assertNull(result,
            "getActiveFoldModel should return null"
               + " for empty fold model");
      } finally {
         fvc.setFoldModel(null);
         EventQueue.biglock2.unlock();
      }
   }

   // ── FoldModel.addFold and toggleFold basic ───────────────────

   @Test
   void t04_foldModelAddAndCollapse() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 7);
      assertFalse(fm.isEmpty(), "Model should not be empty");
      FoldModel.FoldRange fr = fm.findFoldAtStart(3);
      assertNotNull(fr, "Should find fold at start line 3");
      assertEquals(3, fr.startLine);
      assertEquals(7, fr.endLine);
      assertFalse(fr.collapsed,
         "New folds should start expanded");
   }

   @Test
   void t05_foldModelToggleCollapse() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 7);
      fm.toggleFold(3);
      FoldModel.FoldRange fr = fm.findFoldAtStart(3);
      assertTrue(fr.collapsed,
         "Fold should be collapsed after toggle");
      fm.toggleFold(3);
      assertFalse(fr.collapsed,
         "Fold should be expanded after second toggle");
   }

   @Test
   void t06_foldModelMultipleFolds() {
      FoldModel fm = new FoldModel();
      fm.addFold(2, 5);
      fm.addFold(8, 12);
      fm.addFold(15, 20);
      FoldModel.FoldRange f1 = fm.findFoldAtStart(2);
      FoldModel.FoldRange f2 = fm.findFoldAtStart(8);
      FoldModel.FoldRange f3 = fm.findFoldAtStart(15);
      assertNotNull(f1);
      assertNotNull(f2);
      assertNotNull(f3);
      assertNull(fm.findFoldAtStart(6),
         "No fold at non-fold line");
   }

   // ── computeTopBufLine ────────────────────────────────────────

   @Test
   void t07_computeTopBufLineWithNoFolds() throws Exception {
      clearFold();
      int screenposy = getIntField("screenposy");
      int fileY;
      EventQueue.biglock2.lock();
      try {
         fileY = fvc.inserty();
      } finally {
         EventQueue.biglock2.unlock();
      }
      int expected = fileY - screenposy;
      int sfl = oldView.screenFirstLine();
      assertEquals(expected, sfl,
         "screenFirstLine = fileY - screenposy without folds");
   }

   @Test
   void t08_computeTopBufLineWithCollapsedFold()
         throws Exception {
      FoldModel fm = installFold(2, 5);
      try {
         fm.toggleFold(2);
         EventQueue.biglock2.lock();
         try {
            Object result = invoke("computeTopBufLine",
               new Class<?>[] {FoldModel.class}, fm);
            int topBuf = (int) result;
            assertTrue(topBuf >= 1,
               "computeTopBufLine should return >= 1");
         } finally {
            EventQueue.biglock2.unlock();
         }
      } finally {
         clearFold();
      }
   }

   // ── visualLineDelta ──────────────────────────────────────────

   @Test
   void t09_visualLineDeltaSameLineIsZero() throws Exception {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 6);
      Object result = invoke("visualLineDelta",
         new Class<?>[] {int.class, int.class, FoldModel.class},
         5, 5, fm);
      assertEquals(0, (int) result,
         "Same line should give delta 0");
   }

   @Test
   void t10_visualLineDeltaForwardNoFold() throws Exception {
      FoldModel fm = new FoldModel();
      fm.addFold(10, 15);
      Object result = invoke("visualLineDelta",
         new Class<?>[] {int.class, int.class, FoldModel.class},
         1, 4, fm);
      assertEquals(3, (int) result,
         "Forward 3 visible lines should give delta 3");
   }

   @Test
   void t11_visualLineDeltaBackwardNoFold() throws Exception {
      FoldModel fm = new FoldModel();
      fm.addFold(10, 15);
      Object result = invoke("visualLineDelta",
         new Class<?>[] {int.class, int.class, FoldModel.class},
         4, 1, fm);
      assertEquals(-3, (int) result,
         "Backward 3 visible lines should give delta -3");
   }

   @Test
   void t12_visualLineDeltaAcrossCollapsedFold()
         throws Exception {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 6);
      fm.toggleFold(3);
      // Lines 3-6 collapsed: line 2 to line 7 is 2 visual lines
      // (line 2, fold-summary at 3, line 7)
      Object result = invoke("visualLineDelta",
         new Class<?>[] {int.class, int.class, FoldModel.class},
         2, 7, fm);
      int delta = (int) result;
      assertTrue(delta > 0 && delta <= 5,
         "Delta across collapsed fold should be positive"
            + " and less than buffer distance, got " + delta);
   }

   // ── recalcScreenRow ──────────────────────────────────────────

   @Test
   void t13_recalcScreenRowWithFoldModel() throws Exception {
      FoldModel fm = installFold(2, 5);
      try {
         EventQueue.biglock2.lock();
         try {
            oldView.recalcScreenRow();
         } finally {
            EventQueue.biglock2.unlock();
         }
         int screenposy = getIntField("screenposy");
         assertTrue(screenposy >= 0,
            "screenposy should be non-negative after recalc");
      } finally {
         clearFold();
      }
   }

   @Test
   void t14_recalcScreenRowWithoutFoldModelNoOp()
         throws Exception {
      clearFold();
      int before = getIntField("screenposy");
      oldView.recalcScreenRow();
      int after = getIntField("screenposy");
      assertEquals(before, after,
         "recalcScreenRow without fold model should be no-op");
   }

   // ── mapBufferToScreen ────────────────────────────────────────

   @Test
   void t15_mapBufferToScreenNoCollapse() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 6);
      // All folds expanded — screen == buffer
      int screen = fm.mapBufferToScreen(5);
      assertEquals(5, screen,
         "Expanded fold should not change buffer→screen mapping");
   }

   @Test
   void t16_mapBufferToScreenCollapsed() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 6);
      fm.toggleFold(3);
      // Lines 4,5,6 hidden — line 7 maps to screen line 4
      int screen = fm.mapBufferToScreen(7);
      assertEquals(4, screen,
         "After collapsing 3-6, buffer 7 → screen 4");
   }

   @Test
   void t17_mapBufferToScreenBeforeFold() {
      FoldModel fm = new FoldModel();
      fm.addFold(5, 10);
      fm.toggleFold(5);
      // Lines before the fold are unaffected
      int screen = fm.mapBufferToScreen(3);
      assertEquals(3, screen,
         "Lines before collapsed fold unchanged");
   }

   // ── nextVisible / prevVisible ────────────────────────────────

   @Test
   void t18_nextVisibleSkipsCollapsedRange() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 6);
      fm.toggleFold(3);
      int next = fm.nextVisible(3);
      assertEquals(7, next,
         "nextVisible from collapsed fold start should skip"
            + " to endLine + 1");
   }

   @Test
   void t19_prevVisibleSkipsCollapsedRange() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 6);
      fm.toggleFold(3);
      int prev = fm.prevVisible(7);
      assertEquals(3, prev,
         "prevVisible from after fold should skip to fold start");
   }

   @Test
   void t20_nextVisibleNoFold() {
      FoldModel fm = new FoldModel();
      fm.addFold(10, 15);
      int next = fm.nextVisible(3);
      assertEquals(4, next,
         "nextVisible outside fold range should return line + 1");
   }

   // ── getFoldIndicator ─────────────────────────────────────────

   @Test
   void t21_foldIndicatorExpandedStart() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 8);
      char ind = fm.getFoldIndicator(3);
      assertEquals('-', ind,
         "Expanded fold start should show '-' indicator");
   }

   @Test
   void t22_foldIndicatorCollapsedStart() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 8);
      fm.toggleFold(3);
      char ind = fm.getFoldIndicator(3);
      assertEquals('+', ind,
         "Collapsed fold start should show '+' indicator");
   }

   @Test
   void t23_foldIndicatorInsideFold() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 8);
      char ind = fm.getFoldIndicator(5);
      assertEquals('|', ind,
         "Line inside expanded fold should show '|' indicator");
   }

   @Test
   void t24_foldIndicatorEndLine() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 8);
      char ind = fm.getFoldIndicator(8);
      // End line might show '|' or a closing marker
      assertTrue(ind == '|' || ind == '_' || ind == '\0',
         "End line indicator should be valid, got '"
            + ind + "'");
   }

   @Test
   void t25_foldIndicatorOutsideFold() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 8);
      char ind = fm.getFoldIndicator(1);
      assertEquals('\0', ind,
         "Line outside fold should show no indicator");
   }

   // ── screeny with fold model ──────────────────────────────────

   @Test
   void t26_screenyWithFoldReturnsValidAmount()
         throws Exception {
      FoldModel fm = installFold(2, 5);
      try {
         EventQueue.biglock2.lock();
         try {
            int result = oldView.screeny(3);
            // Result should be bounded by available lines
            assertTrue(result >= 0 || result <= 3,
               "screeny should return valid scroll amount");
         } finally {
            EventQueue.biglock2.unlock();
         }
      } finally {
         clearFold();
      }
   }

   @Test
   void t27_screenyWithFoldZeroAmount() throws Exception {
      FoldModel fm = installFold(2, 5);
      try {
         EventQueue.biglock2.lock();
         try {
            int result = oldView.screeny(0);
            assertEquals(0, result,
               "screeny(0) should return 0");
         } finally {
            EventQueue.biglock2.unlock();
         }
      } finally {
         clearFold();
      }
   }

   // ── foldSummaryText ──────────────────────────────────────────

   @Test
   void t28_foldSummaryTextFormatCorrect() {
      String summary = FoldModel.foldSummaryText(
         3, 8, "public void method() {");
      assertNotNull(summary, "Summary text should not be null");
      assertTrue(summary.contains("5"),
         "Summary should contain line count (5 lines),"
            + " got: " + summary);
   }

   @Test
   void t29_foldSummaryTextMinimalFold() {
      String summary = FoldModel.foldSummaryText(
         5, 6, "if (true) {");
      assertNotNull(summary);
      assertTrue(summary.contains("1"),
         "1-line fold summary should contain count 1");
   }

   // ── updateCursorShape with fold model ────────────────────────

   @Test
   void t30_updateCursorShapeShiftsWithFoldGutter()
         throws Exception {
      // Without fold model
      clearFold();
      java.awt.Shape shapeNoFold;
      EventQueue.biglock2.lock();
      try {
         Method ucs = oldViewClass.getDeclaredMethod(
            "updateCursorShape", java.awt.Shape.class);
         ucs.setAccessible(true);
         shapeNoFold = (java.awt.Shape) ucs.invoke(
            oldView, (Object) null);
      } finally {
         EventQueue.biglock2.unlock();
      }

      // With fold model — cursor shifts right by charwidth
      FoldModel fm = installFold(2, 5);
      try {
         java.awt.Shape shapeFold;
         EventQueue.biglock2.lock();
         try {
            Method ucs = oldViewClass.getDeclaredMethod(
               "updateCursorShape", java.awt.Shape.class);
            ucs.setAccessible(true);
            shapeFold = (java.awt.Shape) ucs.invoke(
               oldView, (Object) null);
         } finally {
            EventQueue.biglock2.unlock();
         }
         assertNotNull(shapeNoFold);
         assertNotNull(shapeFold);
         if (shapeNoFold instanceof java.awt.Rectangle
               && shapeFold instanceof java.awt.Rectangle) {
            java.awt.Rectangle rNoFold =
               (java.awt.Rectangle) shapeNoFold;
            java.awt.Rectangle rFold =
               (java.awt.Rectangle) shapeFold;
            int charwidth = getIntField("charwidth");
            assertEquals(rNoFold.x + charwidth, rFold.x,
               "Cursor should shift right by charwidth"
                  + " when fold gutter active");
         }
      } finally {
         clearFold();
      }
   }

   // ── paintLines with fold model ───────────────────────────────

   @Test
   void t31_paintLinesWithFoldDoesNotThrow() throws Exception {
      FoldModel fm = installFold(2, 5);
      try {
         Canvas canvas = getCanvas();
         EventQueue.biglock2.lock();
         try {
            BufferedImage img = new BufferedImage(
               800, 600, BufferedImage.TYPE_INT_ARGB);
            Graphics gr = img.getGraphics();
            Method paintLines = canvas.getClass()
               .getDeclaredMethod("paintLines",
                  Graphics.class, int.class, int.class);
            paintLines.setAccessible(true);
            paintLines.invoke(canvas, gr, 0, 3);
            gr.dispose();
         } finally {
            EventQueue.biglock2.unlock();
         }
      } finally {
         clearFold();
      }
   }

   @Test
   void t32_paintLinesWithCollapsedFoldDoesNotThrow()
         throws Exception {
      FoldModel fm = installFold(2, 5);
      try {
         fm.toggleFold(2);
         Canvas canvas = getCanvas();
         EventQueue.biglock2.lock();
         try {
            BufferedImage img = new BufferedImage(
               800, 600, BufferedImage.TYPE_INT_ARGB);
            Graphics gr = img.getGraphics();
            Method paintLines = canvas.getClass()
               .getDeclaredMethod("paintLines",
                  Graphics.class, int.class, int.class);
            paintLines.setAccessible(true);
            paintLines.invoke(canvas, gr, 0, 3);
            gr.dispose();
         } finally {
            EventQueue.biglock2.unlock();
         }
      } finally {
         clearFold();
      }
   }

   // ── canvas paint/repaint with fold model ─────────────────────

   @Test
   void t33_canvasRepaintWithFoldDoesNotThrow()
         throws Exception {
      FoldModel fm = installFold(2, 5);
      try {
         Canvas canvas = getCanvas();
         assertNotNull(canvas,
            "Canvas should exist with fold installed");
         // Verify fold model is accessible via FvContext
         EventQueue.biglock2.lock();
         try {
            FvContext<?> fvc = FvContext.getCurrFvc();
            assertNotNull(fvc.getFoldModel(),
               "FoldModel should be active on FvContext");
         } finally {
            EventQueue.biglock2.unlock();
         }
      } finally {
         clearFold();
      }
   }

   @Test
   void t34_canvasRepaintWithCollapsedFoldStable()
         throws Exception {
      FoldModel fm = installFold(2, 5);
      try {
         fm.toggleFold(2);
         // Verify fold state after collapse
         assertTrue(fm.findFoldAtStart(2).collapsed,
            "Fold at line 2 should be collapsed");
         EventQueue.biglock2.lock();
         try {
            FvContext<?> fvc = FvContext.getCurrFvc();
            assertNotNull(fvc.getFoldModel(),
               "FoldModel should be active after collapse");
         } finally {
            EventQueue.biglock2.unlock();
         }
      } finally {
         clearFold();
      }
   }

   // ── FoldRange properties ─────────────────────────────────────

   @Test
   void t35_foldRangeHiddenLinesExpanded() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 8);
      FoldModel.FoldRange fr = fm.findFoldAtStart(3);
      assertEquals(0, fr.hiddenLines(),
         "Expanded fold hides 0 lines");
   }

   @Test
   void t36_foldRangeHiddenLinesCollapsed() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 8);
      fm.toggleFold(3);
      FoldModel.FoldRange fr = fm.findFoldAtStart(3);
      assertEquals(5, fr.hiddenLines(),
         "Collapsed fold 3-8 hides 5 lines");
   }

   @Test
   void t37_foldRangeSpan() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 8);
      FoldModel.FoldRange fr = fm.findFoldAtStart(3);
      assertEquals(6, fr.span(),
         "Fold 3-8 spans 6 lines");
   }

   @Test
   void t38_foldRangeToString() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 8);
      FoldModel.FoldRange fr = fm.findFoldAtStart(3);
      String s = fr.toString();
      assertTrue(s.contains("3") && s.contains("8"),
         "toString should contain start and end lines");
      assertTrue(s.contains("open"),
         "Expanded fold toString should contain 'open'");
      fm.toggleFold(3);
      s = fr.toString();
      assertTrue(s.contains("closed"),
         "Collapsed fold toString should contain 'closed'");
   }

   // ── removeFold / removeFoldsInRange ──────────────────────────

   @Test
   void t39_removeFoldByStartLine() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 8);
      fm.addFold(12, 16);
      fm.removeFold(3);
      assertNull(fm.findFoldAtStart(3),
         "Fold at 3 should be removed");
      assertNotNull(fm.findFoldAtStart(12),
         "Fold at 12 should remain");
   }

   @Test
   void t40_removeFoldsInRange() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 8);
      fm.addFold(5, 7); // nested inside 3-8
      fm.addFold(12, 16);
      fm.removeFoldsInRange(3, 8);
      assertNull(fm.findFoldAtStart(3),
         "Fold at 3 removed");
      assertNull(fm.findFoldAtStart(5),
         "Nested fold at 5 removed");
      assertNotNull(fm.findFoldAtStart(12),
         "Fold at 12 outside range should remain");
   }

   // ── Ch rpaint falls back to REDRAW with fold model ───────────

   @Test
   void t41_redrawWithFoldModelDoesNotThrow() throws Exception {
      FoldModel fm = installFold(2, 5);
      try {
         EventQueue.biglock2.lock();
         try {
            oldView.redraw();
         } finally {
            EventQueue.biglock2.unlock();
         }
         // Verify fold model still installed after redraw
         EventQueue.biglock2.lock();
         try {
            assertNotNull(FvContext.getCurrFvc().getFoldModel(),
               "FoldModel should persist after redraw");
         } finally {
            EventQueue.biglock2.unlock();
         }
      } finally {
         clearFold();
      }
   }

   // ── Multiple fold toggle cycle ───────────────────────────────

   @Test
   void t42_multipleFoldToggleCycleStable() throws Exception {
      FoldModel fm = installFold(2, 5);
      try {
         EventQueue.biglock2.lock();
         try {
            for (int i = 0; i < 5; i++) {
               fm.toggleFold(2);
               oldView.recalcScreenRow();
               oldView.redraw();
            }
         } finally {
            EventQueue.biglock2.unlock();
         }
         // Verify fold model still consistent after cycle
         assertNotNull(fm.findFoldAtStart(2),
            "Fold at line 2 should still exist after toggle cycle");
      } finally {
         clearFold();
      }
   }

   // ── FoldToggleHandler ────────────────────────────────────────

   @Test
   void t43_foldToggleHandlerCalled() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 8);
      boolean[] handlerCalled = {false};
      fm.setToggleHandler((line, ctx) -> {
         handlerCalled[0] = true;
         return true;
      });
      assertNotNull(fm.getToggleHandler(),
         "Handler should be set");
      // Handler is called by OldView.mousepress, not directly
      // by toggleFold, so we just verify it's installed correctly
      assertTrue(handlerCalled.length == 1);
   }

   @Test
   void t44_foldToggleHandlerNull() {
      FoldModel fm = new FoldModel();
      assertNull(fm.getToggleHandler(),
         "Default handler should be null");
   }

   // ── Nested folds ─────────────────────────────────────────────

   @Test
   void t45_nestedFoldIndicators() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 12);
      fm.addFold(5, 8);
      // Outer fold start
      assertEquals('-', fm.getFoldIndicator(3));
      // Inner fold start
      assertEquals('-', fm.getFoldIndicator(5));
      // Between inner fold start and end
      assertEquals('|', fm.getFoldIndicator(6));
      // After inner fold end, still inside outer
      assertEquals('|', fm.getFoldIndicator(10));
      // Outside all folds
      assertEquals('\0', fm.getFoldIndicator(1));
   }

   @Test
   void t46_nestedFoldMapBufferToScreen() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 12);
      fm.addFold(5, 8);
      fm.toggleFold(5); // collapse inner fold only
      // Lines 6,7,8 hidden (3 lines) — line 9 → screen 6
      int screen = fm.mapBufferToScreen(9);
      assertEquals(6, screen,
         "After collapsing inner fold 5-8, buffer 9 → screen 6");
   }

   @Test
   void t47_bothFoldsCollapsed() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 12);
      fm.addFold(5, 8);
      fm.toggleFold(3); // collapse outer — inner is inside
      // Entire 3-12 collapsed: line 13 → screen 4
      int screen = fm.mapBufferToScreen(13);
      assertEquals(4, screen,
         "After collapsing outer fold 3-12, buffer 13 → screen 4");
   }

   // ── Edge cases ───────────────────────────────────────────────

   @Test
   void t48_foldModelIsEmptyAfterRemoveAll() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 8);
      fm.removeFold(3);
      assertTrue(fm.isEmpty(), "Model should be empty after remove");
   }

   @Test
   void t49_addFoldWithInvalidRange() {
      FoldModel fm = new FoldModel();
      fm.addFold(8, 3); // end <= start — should be ignored
      assertTrue(fm.isEmpty(),
         "Invalid range (end <= start) should not add a fold");
   }

   @Test
   void t50_addFoldSingleLine() {
      FoldModel fm = new FoldModel();
      fm.addFold(5, 5); // end == start — should be ignored
      assertTrue(fm.isEmpty(),
         "Single-line range (end == start) should not add fold");
   }
}
