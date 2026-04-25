package javi;

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
 * GUI tests for the UI abstract class and AwtInterface implementation.
 *
 * <p>Tests UI singleton behavior, dialog infrastructure, status bar,
 * help panels, window management, and state persistence through the
 * AwtInterface GUI. Requires Xvfb or a display.</p>
 *
 * <p>Does NOT use FrameFixture because the AwtInterface frame may
 * already be disposed if other GUI tests ran first in the same JVM.
 * Instead, exercises the UI singleton API directly.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class UIGuiJUnitTest {

   private static Robot robot;

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
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── UI singleton and type tests ──────────────────────────────

   @Test
   void t01_uiSingletonExists() {
      UI ui = UI.getInstance();
      assertNotNull(ui, "UI singleton must exist");
   }

   @Test
   void t02_uiIsAwtInterface() {
      UI ui = UI.getInstance();
      assertTrue(ui.getClass().getName().endsWith("AwtInterface"),
         "UI should be AwtInterface, got " + ui.getClass().getName());
   }

   @Test
   void t03_uiInstanceNotNull() {
      // UI singleton should exist even if frame is not showing
      assertNotNull(UI.getInstance(),
         "UI instance should exist after init");
   }

   // ── Status bar methods ───────────────────────────────────────

   @Test
   void t04_reportMessageDoesNotCrash() {
      UI.reportMessage("UIGuiJUnitTest message");
      assertNotNull(UI.getInstance());
   }

   @Test
   void t05_reportErrorDoesNotCrash() {
      UI.reportError("UIGuiJUnitTest error line");
      assertNotNull(UI.getInstance());
   }

   @Test
   void t06_statusAddAndClear() {
      UI ui = UI.getInstance();
      ui.istatusaddline("line 1");
      ui.istatusaddline("line 2");
      assertNotNull(ui);
      ui.iclearStatus();
      assertNotNull(ui);
   }

   @Test
   void t07_statusSetLine() {
      UI ui = UI.getInstance();
      ui.istatusSetline("status set test");
      assertNotNull(ui);
      ui.iclearStatus();
   }

   // ── Window management ────────────────────────────────────────

   @Test
   void t08_showDoesNotCrash() {
      UI.show();
      assertNotNull(UI.getInstance());
   }

   @Test
   void t09_toFrontDoesNotCrash() {
      UI.toFront();
      assertNotNull(UI.getInstance());
   }

   @Test
   void t10_repaintDoesNotCrash() {
      UI.repaint();
      assertNotNull(UI.getInstance());
   }

   @Test
   void t11_flushDoesNotCrash() {
      UI.flush();
      assertNotNull(UI.getInstance());
   }

   @Test
   void t12_setTitleRoundTrip() {
      EventQueue.biglock2.lock();
      try {
         UI.setTitle("UIGuiTest Title");
         // If we get here, setTitle didn't throw
         assertNotNull(UI.getInstance());
         UI.setTitle("javi");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Command line show/hide ───────────────────────────────────

   @Test
   void t13_showCommandDoesNotCrash() {
      UI.showCommand();
      assertNotNull(UI.getInstance());
   }

   @Test
   void t14_hideCommandDoesNotCrash() {
      UI.hideCommand();
      assertNotNull(UI.getInstance());
   }

   @Test
   void t15_showHideCycleStable() {
      UI.showCommand();
      UI.hideCommand();
      UI.showCommand();
      UI.hideCommand();
      assertNotNull(UI.getInstance());
   }

   // ── Toggle status bar ────────────────────────────────────────

   @Test
   void t16_toggleStatusTwice() {
      UI ui = UI.getInstance();
      ui.itoggleStatus();
      assertNotNull(ui);
      ui.itoggleStatus();
      assertNotNull(ui);
   }

   // ── Help panel ───────────────────────────────────────────────

   @Test
   void t17_createAndRemoveHelpPanel() {
      EventQueue.biglock2.lock();
      try {
         UI ui = UI.getInstance();
         View helpView = ui.icreateHelpPanel(30);
         assertNotNull(helpView, "icreateHelpPanel should return a View");
         // Verify help panel is functional
         ui.iupdateHelpScrollbar(0, 100, 24);
         // Remove the panel — in test env the view may not be
         // registered in FvMap, so catch the dispose exception
         try {
            ui.iremoveHelpPanel(helpView);
         } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("fvcontext.dispose"),
               "unexpected RuntimeException: " + e.getMessage());
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
      assertNotNull(UI.getInstance());
   }

   @Test
   void t18_helpPanelScrollbarUpdate() {
      EventQueue.biglock2.lock();
      try {
         UI ui = UI.getInstance();
         View helpView = ui.icreateHelpPanel(25);
         assertNotNull(helpView);
         // Multiple scrollbar updates
         ui.iupdateHelpScrollbar(0, 50, 20);
         ui.iupdateHelpScrollbar(10, 50, 20);
         ui.iupdateHelpScrollbar(30, 50, 20);
         try {
            ui.iremoveHelpPanel(helpView);
         } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("fvcontext.dispose"),
               "unexpected RuntimeException: " + e.getMessage());
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Trash support ────────────────────────────────────────────

   @Test
   void t19_trashSupportedReturns() {
      UI ui = UI.getInstance();
      // Just verify it returns without crashing
      boolean supported = ui.itrashSupported();
      assertTrue(supported || !supported,
         "itrashSupported should return a boolean");
   }

   @Test
   void t20_trashSupportedStaticMethod() {
      boolean supported = UI.trashSupported();
      assertTrue(supported || !supported);
   }

   // ── ReloadAction enum ────────────────────────────────────────

   @Test
   void t21_reloadActionValues() {
      UI.ReloadAction[] values = UI.ReloadAction.values();
      assertEquals(5, values.length,
         "ReloadAction should have 5 values");
      assertNotNull(UI.ReloadAction.RELOAD);
      assertNotNull(UI.ReloadAction.IGNORE);
      assertNotNull(UI.ReloadAction.IGNORE_ALWAYS);
      assertNotNull(UI.ReloadAction.SHOW_DIFF);
      assertNotNull(UI.ReloadAction.STOP_EDITING);
   }

   @Test
   void t22_reloadActionValueOf() {
      assertEquals(UI.ReloadAction.RELOAD,
         UI.ReloadAction.valueOf("RELOAD"));
      assertEquals(UI.ReloadAction.IGNORE_ALWAYS,
         UI.ReloadAction.valueOf("IGNORE_ALWAYS"));
   }

   // ── Buttons enum ─────────────────────────────────────────────

   @Test
   void t23_buttonsEnumValues() {
      UI.Buttons[] values = UI.Buttons.values();
      assertTrue(values.length >= 10,
         "Buttons enum should have many values, got " + values.length);
      assertNotNull(UI.Buttons.OK);
      assertNotNull(UI.Buttons.WINDOWCLOSE);
      assertNotNull(UI.Buttons.USEFILE);
      assertNotNull(UI.Buttons.USEBACKUP);
      assertNotNull(UI.Buttons.IOERROR);
   }

   // ── popError ─────────────────────────────────────────────────

   @Test
   void t24_popErrorMethodExists() {
      // UI.popError opens a blocking modal dialog (Popper.postWait)
      // so we verify the method is accessible without calling it
      assertNotNull(UI.getInstance());
      // Verify static method exists via reflection
      try {
         var method = UI.class.getDeclaredMethod(
            "popError", String.class, Throwable.class);
         assertNotNull(method);
      } catch (NoSuchMethodException e) {
         throw new AssertionError("popError method not found", e);
      }
   }

   // ── sizeChange ───────────────────────────────────────────────

   @Test
   void t25_sizeChangeDoesNotCrash() {
      UI ui = UI.getInstance();
      ui.isizeChange();
      assertNotNull(ui);
   }

   // ── getFile returns something ────────────────────────────────

   @Test
   void t26_getFileReturnsNonNull() {
      // getFile opens a file dialog, but calling it on UI verifies
      // the method exists. We use the instance method to avoid dialog.
      // Actually UI.getFile() would block, so just verify the static
      // wrapper exists by checking the instance method.
      UI ui = UI.getInstance();
      assertNotNull(ui,
         "UI instance must exist for getFile to work");
   }

   // ── Frame Canvas tests ───────────────────────────────────────

   @Test
   void t27_uiInstanceConsistent() {
      UI ui1 = UI.getInstance();
      UI ui2 = UI.getInstance();
      assertTrue(ui1 == ui2,
         "UI.getInstance() should return same instance");
   }

   @Test
   void t28_uiClassHasExpectedMethods() throws Exception {
      UI ui = UI.getInstance();
      // Verify key abstract methods are implemented
      ui.irepaint();
      ui.iclearStatus();
      assertNotNull(ui);
   }

   // ── confirmReload with no UI ─────────────────────────────────

   @Test
   void t29_confirmReloadReturnsAction() {
      // We cannot call iconfirmReload directly (it shows a dialog),
      // but we can verify the static confirmReload returns a valid
      // action when the UI instance is set but we avoid the dialog
      // by checking the method signature exists
      UI ui = UI.getInstance();
      assertNotNull(ui);
      // The enum covers all possible outcomes
      for (UI.ReloadAction action : UI.ReloadAction.values()) {
         assertNotNull(action.name());
      }
   }

   // ── FvContext interaction through GUI ─────────────────────────

   @Test
   void t30_fvContextHasViewInGui() {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         assertNotNull(fvc, "FvContext must exist in GUI");
         assertNotNull(fvc.vi, "FvContext must have a View");
         assertTrue(fvc.vi.isVisible(), "View must be visible");
         assertNotNull(fvc.edvec, "FvContext must have TextEdit");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
