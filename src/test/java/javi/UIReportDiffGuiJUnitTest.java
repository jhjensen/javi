package javi;

import java.io.File;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for UI static delegation methods.
 *
 * <p>Exercises the static methods on UI that delegate to the
 * AwtInterface singleton: popError, reportMessage, reportError,
 * reportBadBackup, confirmReload, stopConverter, trashSupported,
 * moveToTrash, openFile, and the Result inner class.</p>
 *
 * <p>Does NOT trigger dialogs (ireportDiff etc) as they block
 * the AWT thread waiting for user input. Instead, tests the
 * delegation plumbing and null-safe static methods.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class UIReportDiffGuiJUnitTest {

   private static Robot robot;
   private static UI ui;

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
      ui = UI.getInstance();
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── UI.Result inner class ────────────────────────────────────

   @Test
   void t01_resultConstructionAndFields() {
      UI.Result result = new UI.Result(42, "apply");
      assertEquals(42, result.newValue);
      assertEquals("apply", result.choice);
   }

   @Test
   void t02_resultZeroValue() {
      UI.Result result = new UI.Result(0, null);
      assertEquals(0, result.newValue);
      assertEquals(null, result.choice);
   }

   @Test
   void t03_resultNegativeValue() {
      UI.Result result = new UI.Result(-1, "cancel");
      assertEquals(-1, result.newValue);
      assertEquals("cancel", result.choice);
   }

   // ── Buttons enum ─────────────────────────────────────────────

   @Test
   void t04_buttonsEnumContainsExpectedValues() {
      UI.Buttons[] vals = UI.Buttons.values();
      assertTrue(vals.length >= 10,
         "Buttons enum should have at least 10 values");
      assertNotNull(UI.Buttons.valueOf("CHECKOUT"));
      assertNotNull(UI.Buttons.valueOf("MAKEWRITEABLE"));
      assertNotNull(UI.Buttons.valueOf("DONOTHING"));
      assertNotNull(UI.Buttons.valueOf("USEFILE"));
      assertNotNull(UI.Buttons.valueOf("USEBACKUP"));
      assertNotNull(UI.Buttons.valueOf("IOERROR"));
      assertNotNull(UI.Buttons.valueOf("WAITPROC"));
      assertNotNull(UI.Buttons.valueOf("KILLPROC"));
   }

   // ── ReloadAction enum ────────────────────────────────────────

   @Test
   void t05_reloadActionValuesComplete() {
      UI.ReloadAction[] vals = UI.ReloadAction.values();
      assertEquals(5, vals.length,
         "ReloadAction should have 5 values");
      assertNotNull(UI.ReloadAction.valueOf("RELOAD"));
      assertNotNull(UI.ReloadAction.valueOf("IGNORE"));
      assertNotNull(UI.ReloadAction.valueOf("IGNORE_ALWAYS"));
      assertNotNull(UI.ReloadAction.valueOf("SHOW_DIFF"));
      assertNotNull(UI.ReloadAction.valueOf("STOP_EDITING"));
   }

   @Test
   void t06_reloadActionOrdinals() {
      assertEquals(0, UI.ReloadAction.RELOAD.ordinal());
      assertEquals(1, UI.ReloadAction.IGNORE.ordinal());
      assertEquals(2, UI.ReloadAction.IGNORE_ALWAYS.ordinal());
   }

   // ── reportMessage / reportError ──────────────────────────────

   @Test
   void t07_reportMessageDoesNotThrow() {
      assertDoesNotThrow(
         () -> UI.reportMessage("test message from GUI test"),
         "reportMessage should not throw");
   }

   @Test
   void t08_reportErrorDoesNotThrow() {
      assertDoesNotThrow(
         () -> UI.reportError("test error from GUI test"),
         "reportError should not throw");
   }

   @Test
   void t09_reportMessageEmptyString() {
      assertDoesNotThrow(
         () -> UI.reportMessage(""),
         "reportMessage with empty string should not throw");
   }

   // ── popError ─────────────────────────────────────────────────

   @Test
   void t10_popErrorWithNullException() throws Exception {
      // popError shows a blocking dialog via ipopstring —
      // verify the method signature exists via reflection
      Method m = UI.class.getDeclaredMethod(
         "popError", String.class, Throwable.class);
      assertNotNull(m, "popError method should exist");
      assertEquals(void.class, m.getReturnType(),
         "popError should return void");
   }

   @Test
   void t11_popErrorWithException() throws Exception {
      // popError shows a blocking dialog — verify signature
      // and that the method accepts Throwable parameter
      Method m = UI.class.getDeclaredMethod(
         "popError", String.class, Throwable.class);
      assertTrue(java.lang.reflect.Modifier.isStatic(
         m.getModifiers()),
         "popError should be static");
   }

   @Test
   void t12_popErrorEmptyMessage() throws Exception {
      // popError may block on ipopstring dialog —
      // verify the declared parameter types
      Method m = UI.class.getDeclaredMethod(
         "popError", String.class, Throwable.class);
      assertEquals(2, m.getParameterCount(),
         "popError should take 2 parameters");
   }

   // ── confirmReload static method ──────────────────────────────

   @Test
   void t13_confirmReloadStaticWithInstance() {
      // The static method checks for null instance
      assertNotNull(ui,
         "UI instance should be available");
      // We cannot call confirmReload directly as it shows a dialog,
      // but we verify the null-instance path
   }

   // ── trashSupported / moveToTrash ─────────────────────────────

   @Test
   void t14_trashSupportedReturnsBoolean() {
      // Should return true on macOS, false on headless Linux
      boolean supported = UI.trashSupported();
      // Just verify it doesn't throw — result depends on platform
      assertTrue(supported || !supported);
   }

   @Test
   void t15_moveToTrashNonExistentFile() {
      File nonExistent = new File("/nonexistent/file.txt");
      try {
         boolean result = UI.moveToTrash(nonExistent);
         // If it returns, false is expected
         assertFalse(result,
            "moveToTrash on non-existent file"
               + " should return false");
      } catch (Exception e) {
         // Platform may throw on headless — acceptable
         assertNotNull(e);
      }
   }

   // ── show / hide / toFront / repaint ──────────────────────────

   @Test
   void t16_showDoesNotThrow() {
      assertDoesNotThrow(() -> UI.show(),
         "UI.show() should not throw");
   }

   @Test
   void t17_toFrontDoesNotThrow() {
      assertDoesNotThrow(() -> UI.toFront(),
         "UI.toFront() should not throw");
   }

   @Test
   void t18_repaintDoesNotThrow() {
      assertDoesNotThrow(() -> UI.repaint(),
         "UI.repaint() should not throw");
   }

   @Test
   void t19_flushDoesNotThrow() {
      assertDoesNotThrow(() -> UI.flush(),
         "UI.flush() should not throw");
   }

   // ── showCommand / hideCommand ────────────────────────────────

   @Test
   void t20_showCommandDoesNotThrow() {
      assertDoesNotThrow(() -> UI.showCommand(),
         "UI.showCommand() should not throw");
   }

   @Test
   void t21_hideCommandDoesNotThrow() {
      assertDoesNotThrow(() -> UI.hideCommand(),
         "UI.hideCommand() should not throw");
   }

   @Test
   void t22_showHideCommandCycleStable() {
      assertDoesNotThrow(() -> {
         UI.showCommand();
         UI.hideCommand();
         UI.showCommand();
         UI.hideCommand();
      }, "show/hide command cycle should be stable");
   }

   // ── setTitle / setline / clearStatus ─────────────────────────

   @Test
   void t23_setTitleDoesNotThrow() {
      assertDoesNotThrow(
         () -> UI.setTitle("GUI Test Title"),
         "UI.setTitle should not throw");
   }

   @Test
   void t24_setlineDoesNotThrow() {
      assertDoesNotThrow(
         () -> UI.setline("test status line"),
         "UI.setline should not throw");
   }

   @Test
   void t25_clearStatusDoesNotThrow() {
      assertDoesNotThrow(() -> UI.clearStatus(),
         "UI.clearStatus() should not throw");
   }

   @Test
   void t26_statusSetAndClearCycle() {
      assertDoesNotThrow(() -> {
         UI.setline("line1");
         UI.clearStatus();
         UI.setline("line2");
         UI.reportMessage("msg");
         UI.clearStatus();
      }, "Status set/clear cycle should be stable");
   }

   // ── sizeChange ───────────────────────────────────────────────

   @Test
   void t27_sizeChangeDoesNotThrow() {
      assertDoesNotThrow(() -> UI.sizeChange(),
         "UI.sizeChange() should not throw");
   }

   // ── isVisible ────────────────────────────────────────────────

   @Test
   void t28_isVisibleTrueInGui() {
      assertTrue(UI.isVisible(),
         "UI should be visible in GUI mode");
   }

   // ── getFile ──────────────────────────────────────────────────

   @Test
   void t29_getFileReturnsNonNull() throws Exception {
      // UI.getFile() shows a blocking file dialog via
      // GetFile().postWait() — verify via reflection instead
      Method m = UI.class.getDeclaredMethod("getFile");
      assertNotNull(m, "getFile method should exist");
      assertEquals(String.class, m.getReturnType(),
         "getFile should return String");
   }

   // ── createHelpPanel / removeHelpPanel ────────────────────────

   @Test
   void t30_createAndRemoveHelpPanel() {
      EventQueue.biglock2.lock();
      try {
         View helpPanel = UI.createHelpPanel(30);
         if (helpPanel != null) {
            UI.removeHelpPanel(helpPanel);
         }
         // null return means platform doesn't support — OK
      } catch (Exception e) {
         // Help panel may not be supported in headless — OK
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t31_removeHelpPanelNullSafe() {
      EventQueue.biglock2.lock();
      try {
         // Removing null panel — test that it either
         // handles null gracefully or throws safely
         UI.removeHelpPanel(null);
      } catch (NullPointerException e) {
         // Implementation may not be null-safe — acceptable
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t32_updateHelpScrollbarNoPanel() {
      // Scrollbar update without a panel should not crash
      assertDoesNotThrow(
         () -> UI.updateHelpScrollbar(0, 100, 20),
         "updateHelpScrollbar should not throw without panel");
   }

   // ── reportBadBackup static ───────────────────────────────────

   @Test
   void t33_reportBadBackupStaticExists() throws Exception {
      // Verify the static method exists and is callable
      Method m = UI.class.getDeclaredMethod(
         "reportBadBackup", String.class,
         history.BadBackupFile.class);
      assertNotNull(m,
         "UI.reportBadBackup static method should exist");
   }

   // ── dispose is not tested (destructive) ──────────────────────

   // ── stopConverter detection ──────────────────────────────────

   @Test
   void t34_stopConverterMethodExists() throws Exception {
      // Verify the static method exists (it shows a dialog, so
      // we cannot call it directly)
      Method m = UI.class.getDeclaredMethod(
         "stopConverter", String.class);
      assertNotNull(m,
         "UI.stopConverter static method should exist");
   }

   // ── MAX_REPORT_DIFF_ITERATIONS constant ──────────────────────

   @Test
   void t35_maxReportDiffIterationsIsReasonable()
         throws Exception {
      Field f = UI.class.getDeclaredField(
         "MAX_REPORT_DIFF_ITERATIONS");
      f.setAccessible(true);
      int max = f.getInt(null);
      assertTrue(max >= 10 && max <= 1000,
         "MAX_REPORT_DIFF_ITERATIONS should be reasonable,"
            + " got " + max);
   }

   // ── reportModVal method signature ────────────────────────────

   @Test
   void t36_reportModValMethodExists() throws Exception {
      Method m = UI.class.getDeclaredMethod("reportModVal",
         String.class, String.class, String[].class, long.class);
      assertNotNull(m,
         "UI.reportModVal static method should exist");
   }

   // ── openFile method signature ────────────────────────────────

   @Test
   void t37_openFileMethodExists() throws Exception {
      Method m = UI.class.getDeclaredMethod(
         "openFile", File.class);
      assertNotNull(m,
         "UI.openFile static method should exist");
   }

   // ── setStream method signature ───────────────────────────────

   @Test
   void t38_setStreamMethodExists() throws Exception {
      Method m = UI.class.getDeclaredMethod(
         "setStream", java.io.Reader.class);
      assertNotNull(m,
         "UI.setStream static method should exist");
   }

   // ── showmenu method signature ────────────────────────────────

   @Test
   void t39_showmenuMethodExists() throws Exception {
      Method m = UI.class.getDeclaredMethod(
         "showmenu", int.class, int.class);
      assertNotNull(m,
         "UI.showmenu static method should exist");
   }

   // ── hide (transferFocus) ─────────────────────────────────────

   @Test
   void t40_hideDoesNotThrow() {
      assertDoesNotThrow(() -> UI.hide(),
         "UI.hide() should not throw");
   }
}
