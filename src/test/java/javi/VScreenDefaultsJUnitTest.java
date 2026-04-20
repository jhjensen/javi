package javi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests for {@link VScreen} default method implementations.
 *
 * <p>VScreen is abstract with many default no-op methods. This test
 * creates a minimal concrete subclass that implements only the required
 * abstract methods, and verifies that all default methods execute
 * without throwing.</p>
 */
class VScreenDefaultsJUnitTest {

   private VScreen screen;
   private StringBuilder sb;

   /** Minimal concrete VScreen implementing only abstract methods. */
   static class MinimalScreen extends VScreen {
      int incXAmount, incYAmount;
      int setXVal, setYVal;
      int setXYx, setXYy;
      boolean eraseScreenCalled, eraseToEndCalled;
      boolean eraseLineCalled;
      int eraseCharsCount;
      int insertLinesCount;
      boolean insertModeVal;
      boolean updateScreenCalled;
      boolean saveCursorCalled, restoreCursorCalled;

      void incX(int amount, StringBuilder sb) {
         incXAmount = amount;
      }

      void incY(int amount, StringBuilder sb) {
         incYAmount = amount;
      }

      void setX(int val, StringBuilder sb) {
         setXVal = val;
      }

      void setY(int val, StringBuilder sb) {
         setYVal = val;
      }

      void setXY(int xval, int yval, StringBuilder sb) {
         setXYx = xval;
         setXYy = yval;
      }

      void eraseScreen(StringBuilder sb) {
         eraseScreenCalled = true;
      }

      void eraseToEnd(StringBuilder sb) {
         eraseToEndCalled = true;
      }

      void eraseLine(StringBuilder sb) {
         eraseLineCalled = true;
      }

      void eraseChars(int count, StringBuilder sb) {
         eraseCharsCount = count;
      }

      void insertLines(int count, StringBuilder sb) {
         insertLinesCount = count;
      }

      void setInsertMode(boolean val, StringBuilder sb) {
         insertModeVal = val;
      }

      void updateScreen(StringBuilder sb) {
         updateScreenCalled = true;
      }

      void saveCursor(StringBuilder sb) {
         saveCursorCalled = true;
      }

      void restoreCursor(StringBuilder sb) {
         restoreCursorCalled = true;
      }
   }

   @BeforeEach
   void setUp() {
      screen = new MinimalScreen();
      sb = new StringBuilder();
   }

   // ── Default no-op methods should not throw ──────────────────

   @Test
   void deleteLinesDefaultNoOp() {
      assertDoesNotThrow(() -> screen.deleteLines(5, sb));
   }

   @Test
   void scrollUpDefaultNoOp() {
      assertDoesNotThrow(() -> screen.scrollUp(3, sb));
   }

   @Test
   void scrollDownDefaultNoOp() {
      assertDoesNotThrow(() -> screen.scrollDown(3, sb));
   }

   @Test
   void eraseToBeginningDefaultNoOp() {
      assertDoesNotThrow(() -> screen.eraseToBeginning(sb));
   }

   @Test
   void eraseScreenToBeginningDefaultNoOp() {
      assertDoesNotThrow(() -> screen.eraseScreenToBeginning(sb));
   }

   @Test
   void eraseScreenToEndDefaultNoOp() {
      assertDoesNotThrow(() -> screen.eraseScreenToEnd(sb));
   }

   @Test
   void setGraphicRenditionDefaultNoOp() {
      assertDoesNotThrow(() ->
         screen.setGraphicRendition(new int[]{1, 4, 7}, sb));
   }

   @Test
   void setGraphicRenditionEmptyParamsDefaultNoOp() {
      assertDoesNotThrow(() ->
         screen.setGraphicRendition(new int[0], sb));
   }

   @Test
   void bellDefaultNoOp() {
      assertDoesNotThrow(() -> screen.bell());
   }

   @Test
   void setTitleDefaultNoOp() {
      assertDoesNotThrow(() -> screen.setTitle("test title"));
   }

   @Test
   void setTitleNullDefaultNoOp() {
      assertDoesNotThrow(() -> screen.setTitle(null));
   }

   @Test
   void switchAlternateScreenEnableDefaultNoOp() {
      assertDoesNotThrow(() ->
         screen.switchAlternateScreen(true, sb));
   }

   @Test
   void switchAlternateScreenDisableDefaultNoOp() {
      assertDoesNotThrow(() ->
         screen.switchAlternateScreen(false, sb));
   }

   @Test
   void setMouseTrackingDefaultNoOp() {
      assertDoesNotThrow(() ->
         screen.setMouseTracking(1000, true));
   }

   @Test
   void setMouseTrackingDisableDefaultNoOp() {
      assertDoesNotThrow(() ->
         screen.setMouseTracking(0, false));
   }

   @Test
   void setSgrMouseModeDefaultNoOp() {
      assertDoesNotThrow(() -> screen.setSgrMouseMode(true));
   }

   @Test
   void setBracketedPasteModeDefaultNoOp() {
      assertDoesNotThrow(() -> screen.setBracketedPasteMode(true));
   }

   @Test
   void setFocusEventsModeDefaultNoOp() {
      assertDoesNotThrow(() -> screen.setFocusEventsMode(true));
   }

   @Test
   void setAutowrapModeDefaultNoOp() {
      assertDoesNotThrow(() -> screen.setAutowrapMode(true));
   }

   @Test
   void setCursorBlinkModeDefaultNoOp() {
      assertDoesNotThrow(() -> screen.setCursorBlinkMode(true));
   }

   @Test
   void setApplicationCursorKeysDefaultNoOp() {
      assertDoesNotThrow(() -> screen.setApplicationCursorKeys(true));
   }

   @Test
   void setCursorVisibleDefaultNoOp() {
      assertDoesNotThrow(() -> screen.setCursorVisible(false));
   }

   @Test
   void respondDeviceAttributesDefaultNoOp() {
      assertDoesNotThrow(() -> screen.respondDeviceAttributes(sb));
   }

   @Test
   void respondCursorPositionDefaultNoOp() {
      assertDoesNotThrow(() -> screen.respondCursorPosition(sb));
   }

   // ── Abstract method delegation verification ─────────────────

   @Test
   void incXDelegatesToConcrete() {
      MinimalScreen ms = (MinimalScreen) screen;
      ms.incX(10, sb);
      org.junit.jupiter.api.Assertions.assertEquals(10, ms.incXAmount);
   }

   @Test
   void incYDelegatesToConcrete() {
      MinimalScreen ms = (MinimalScreen) screen;
      ms.incY(-5, sb);
      org.junit.jupiter.api.Assertions.assertEquals(-5, ms.incYAmount);
   }

   @Test
   void setXYDelegatesToConcrete() {
      MinimalScreen ms = (MinimalScreen) screen;
      ms.setXY(3, 7, sb);
      org.junit.jupiter.api.Assertions.assertEquals(3, ms.setXYx);
      org.junit.jupiter.api.Assertions.assertEquals(7, ms.setXYy);
   }

   @Test
   void eraseScreenDelegatesToConcrete() {
      MinimalScreen ms = (MinimalScreen) screen;
      ms.eraseScreen(sb);
      org.junit.jupiter.api.Assertions.assertTrue(ms.eraseScreenCalled);
   }

   @Test
   void setInsertModeDelegatesToConcrete() {
      MinimalScreen ms = (MinimalScreen) screen;
      ms.setInsertMode(true, sb);
      org.junit.jupiter.api.Assertions.assertTrue(ms.insertModeVal);
   }

   @Test
   void saveCursorDelegatesToConcrete() {
      MinimalScreen ms = (MinimalScreen) screen;
      ms.saveCursor(sb);
      org.junit.jupiter.api.Assertions.assertTrue(ms.saveCursorCalled);
   }

   @Test
   void restoreCursorDelegatesToConcrete() {
      MinimalScreen ms = (MinimalScreen) screen;
      ms.restoreCursor(sb);
      org.junit.jupiter.api.Assertions.assertTrue(ms.restoreCursorCalled);
   }
}
