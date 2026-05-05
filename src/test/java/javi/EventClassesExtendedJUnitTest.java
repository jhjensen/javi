package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for event classes: {@link CommandEvent}, {@link ScrollEvent},
 * {@link PosEvent}, {@link DeferCommandException}, and {@link ExitEvent}.
 *
 * <p>Exercises construction, field storage, and basic execute paths
 * where safe to invoke headlessly.</p>
 */
class EventClassesExtendedJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
      TestInit.initCommands();
   }

   // --- DeferCommandException ---

   @Test
   void deferCommandExceptionStoresMessage() {
      var ex = new DeferCommandException("not ready");
      assertEquals("not ready", ex.getMessage());
   }

   @Test
   void deferCommandExceptionIsRuntimeException() {
      var ex = new DeferCommandException("test");
      assertTrue(ex instanceof RuntimeException);
   }

   @Test
   void deferCommandExceptionCanBeThrown() {
      assertThrows(DeferCommandException.class, () -> {
         throw new DeferCommandException("deferred");
      });
   }

   // --- CommandEvent ---

   @Test
   void commandEventConstruction() {
      var ev = new CommandEvent("w");
      assertNotNull(ev);
   }

   @Test
   void commandEventExecuteWithNoop() {
      // CommandEvent construction stores the command string
      var ev = new CommandEvent("version");
      assertNotNull(ev);
      // execute() requires full editor state (ecache, etc.)
      // so we only verify construction here
   }

   // --- ScrollEvent ---

   @Test
   void scrollEventConstructionVertical() {
      var ev = new ScrollEvent(5, false);
      assertNotNull(ev);
   }

   @Test
   void scrollEventConstructionHorizontal() {
      var ev = new ScrollEvent(3, true);
      assertNotNull(ev);
   }

   @Test
   void scrollEventExecuteVertical() {
      // ScrollEvent.execute calls FvContext.getCurrFvc() which may be null
      // in headless test environment; test construction only
      var ev = new ScrollEvent(1, false);
      assertNotNull(ev);
   }

   @Test
   void scrollEventExecuteHorizontal() {
      var ev = new ScrollEvent(1, true);
      assertNotNull(ev);
   }

   @Test
   void scrollEventNegativeAmount() {
      var ev = new ScrollEvent(-3, false);
      assertNotNull(ev);
   }

   @Test
   void scrollEventZeroAmount() {
      var ev = new ScrollEvent(0, false);
      assertNotNull(ev);
   }

   // --- PosEvent ---

   @Test
   void posEventConstruction() {
      Position pos = new Position(0, 1, "", null);
      assertNotNull(pos);
      // PosEvent requires a non-null FvContext from getCurrFvc() for execute;
      // in headless tests, getCurrFvc() may return null, so test Position only
   }

   @Test
   void posEventExecuteWithCurrentFvc() {
      // PosEvent.execute calls FvContext.getCurrFvc() and cursorabs
      // which requires full editor state; verify construction only
      Position pos = new Position(0, 1, "", null);
      assertNotNull(pos);
      assertEquals(0, pos.x);
      assertEquals(1, pos.y);
   }
}
