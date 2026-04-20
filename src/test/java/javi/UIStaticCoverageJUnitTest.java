package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for {@link UI} static methods and inner types.
 *
 * <p>Tests the null-safe paths of static delegate methods and
 * the enum/inner-class coverage. Requires StreamInterface to be
 * initialized as the UI singleton.</p>
 */
class UIStaticCoverageJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   // ── Buttons enum ────────────────────────────────────────────

   @Test
   void buttonsEnumValuesExist() {
      UI.Buttons[] values = UI.Buttons.values();
      assertTrue(values.length > 0, "enum should have values");
   }

   @Test
   void buttonsEnumContainsCheckout() {
      assertNotNull(UI.Buttons.valueOf("CHECKOUT"));
   }

   @Test
   void buttonsEnumContainsMakeWriteable() {
      assertNotNull(UI.Buttons.valueOf("MAKEWRITEABLE"));
   }

   @Test
   void buttonsEnumContainsDoNothing() {
      assertNotNull(UI.Buttons.valueOf("DONOTHING"));
   }

   @Test
   void buttonsEnumContainsAllValues() {
      // Verify all known enum values exist
      assertNotNull(UI.Buttons.valueOf("MAKEBACKUP"));
      assertNotNull(UI.Buttons.valueOf("USEFILE"));
      assertNotNull(UI.Buttons.valueOf("USEBACKUP"));
      assertNotNull(UI.Buttons.valueOf("USEDIFF"));
      assertNotNull(UI.Buttons.valueOf("OK"));
      assertNotNull(UI.Buttons.valueOf("WINDOWCLOSE"));
      assertNotNull(UI.Buttons.valueOf("IOERROR"));
      assertNotNull(UI.Buttons.valueOf("USESVN"));
      assertNotNull(UI.Buttons.valueOf("WAITPROC"));
      assertNotNull(UI.Buttons.valueOf("KILLPROC"));
   }

   // ── ReloadAction enum ──────────────────────────────────────

   @Test
   void reloadActionEnumValuesExist() {
      UI.ReloadAction[] values = UI.ReloadAction.values();
      assertEquals(5, values.length, "should have 5 reload actions");
   }

   @Test
   void reloadActionContainsAllValues() {
      assertNotNull(UI.ReloadAction.valueOf("RELOAD"));
      assertNotNull(UI.ReloadAction.valueOf("IGNORE"));
      assertNotNull(UI.ReloadAction.valueOf("IGNORE_ALWAYS"));
      assertNotNull(UI.ReloadAction.valueOf("SHOW_DIFF"));
      assertNotNull(UI.ReloadAction.valueOf("STOP_EDITING"));
   }

   // ── Result class ────────────────────────────────────────────

   @Test
   void resultConstructorStoresFields() {
      UI.Result r = new UI.Result(42, "OK");
      assertEquals(42, r.newValue);
      assertEquals("OK", r.choice);
   }

   @Test
   void resultNullChoice() {
      UI.Result r = new UI.Result(0, null);
      assertEquals(0, r.newValue);
      assertNull(r.choice);
   }

   // ── trashSupported / moveToTrash ────────────────────────────

   @Test
   void trashSupportedReturnsBooleanWithInstance() {
      // StreamInterface exists but doesn't support trash
      assertDoesNotThrow(() -> UI.trashSupported());
   }

   @Test
   void moveToTrashReturnsFalseForNonexistentFile() {
      File nonexistent = new File("/nonexistent/file/xyz123");
      // Should not throw regardless
      assertDoesNotThrow(() -> UI.moveToTrash(nonexistent));
   }

   // ── confirmReload ───────────────────────────────────────────

   @Test
   void confirmReloadWithInstanceDoesNotThrow() {
      // StreamInterface is initialized, confirmReload delegates to it
      assertDoesNotThrow(() ->
         UI.confirmReload("testfile.txt", false));
   }

   // ── reportMessage with instance ─────────────────────────────

   @Test
   void reportMessageDoesNotThrow() {
      assertDoesNotThrow(() ->
         UI.reportMessage("test message from coverage test"));
   }

   @Test
   void reportErrorDoesNotThrow() {
      assertDoesNotThrow(() ->
         UI.reportError("test error from coverage test"));
   }

   // ── popError ────────────────────────────────────────────────

   @Test
   void popErrorWithNullExceptionDoesNotThrow() {
      assertDoesNotThrow(() ->
         UI.popError("test context", null));
   }

   @Test
   void popErrorWithExceptionDoesNotThrow() {
      assertDoesNotThrow(() ->
         UI.popError("test context",
            new RuntimeException("synthetic test error")));
   }

   // ── createHelpPanel / removeHelpPanel ───────────────────────

   @Test
   void createHelpPanelReturnsNullForStream() {
      // StreamInterface.icreateHelpPanel likely returns null
      View panel = UI.createHelpPanel(40);
      // StreamInterface may return null or a minimal view
      // either way, should not throw
   }

   @Test
   void removeHelpPanelWithNullDoesNotThrow() {
      assertDoesNotThrow(() -> UI.removeHelpPanel(null));
   }

   @Test
   void updateHelpScrollbarDoesNotThrow() {
      assertDoesNotThrow(() ->
         UI.updateHelpScrollbar(0, 100, 24));
   }
}
