package javi;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link EventQueue} — event dispatch and
 * synchronization primitives.
 *
 * <p>Covers the {@link EventQueue.DebugLock} inner class, idle
 * registration, and IEvent subclasses.</p>
 */
class EventQueueJUnitTest {

   // ================================================================
   // DebugLock tests
   // ================================================================

   @Nested
   @DisplayName("DebugLock")
   class DebugLockTests {

      @Test
      @DisplayName("lock and unlock succeed on same thread")
      void lockUnlock() {
         EventQueue.DebugLock lock = new EventQueue.DebugLock();
         lock.lock();
         try {
            assertTrue(lock.isHeldByCurrentThread());
         } finally {
            lock.unlock();
         }
         assertFalse(lock.isHeldByCurrentThread());
      }

      @Test
      @DisplayName("assertOwned passes when lock is held")
      void assertOwnedHeld() {
         EventQueue.DebugLock lock = new EventQueue.DebugLock();
         lock.lock();
         try {
            assertDoesNotThrow(lock::assertOwned);
         } finally {
            lock.unlock();
         }
      }

      @Test
      @DisplayName("assertOwned throws when lock not held")
      void assertOwnedNotHeld() {
         EventQueue.DebugLock lock = new EventQueue.DebugLock();
         assertThrows(RuntimeException.class, lock::assertOwned);
      }

      @Test
      @DisplayName("assertUnOwned passes when lock not held")
      void assertUnOwnedNotHeld() {
         EventQueue.DebugLock lock = new EventQueue.DebugLock();
         assertDoesNotThrow(lock::assertUnOwned);
      }

      @Test
      @DisplayName("assertUnOwned throws when lock held")
      void assertUnOwnedHeld() {
         EventQueue.DebugLock lock = new EventQueue.DebugLock();
         lock.lock();
         try {
            assertThrows(RuntimeException.class,
               lock::assertUnOwned);
         } finally {
            lock.unlock();
         }
      }

      @Test
      @DisplayName("lock is reentrant")
      void reentrant() {
         EventQueue.DebugLock lock = new EventQueue.DebugLock();
         lock.lock();
         lock.lock();
         try {
            assertTrue(lock.isHeldByCurrentThread());
            assertEquals(2, lock.getHoldCount());
         } finally {
            lock.unlock();
            lock.unlock();
         }
      }

      @Test
      @DisplayName("tryLock succeeds when uncontested")
      void tryLockSucceeds() throws InterruptedException {
         EventQueue.DebugLock lock = new EventQueue.DebugLock();
         assertTrue(lock.tryLock(1, TimeUnit.SECONDS));
         lock.unlock();
      }

      @Test
      @DisplayName("biglock2 singleton exists")
      void biglock2Exists() {
         assertNotNull(EventQueue.biglock2);
      }
   }

   // ================================================================
   // IEvent tests
   // ================================================================

   @Nested
   @DisplayName("IEvent subclasses")
   class IEventTests {

      @Test
      @DisplayName("ExitEvent.execute throws ExitException")
      void exitEventThrows() {
         ExitEvent ev = new ExitEvent();
         assertThrows(ExitException.class, ev::execute);
      }

      @Test
      @DisplayName("ExitException message")
      void exitExceptionMessage() {
         ExitException ex = new ExitException();
         assertTrue(ex.getMessage().contains("exiting"));
      }

      @Test
      @DisplayName("ExitException with cause")
      void exitExceptionWithCause() {
         Throwable cause = new RuntimeException("test");
         ExitException ex = new ExitException(cause);
         assertNotNull(ex.getCause());
      }

      @Test
      @DisplayName("InputException message")
      void inputExceptionMessage() {
         InputException ex = new InputException("test msg");
         assertEquals("test msg", ex.getMessage());
      }

      @Test
      @DisplayName("InputException with cause")
      void inputExceptionWithCause() {
         Throwable cause = new RuntimeException("root");
         InputException ex = new InputException("wrapper", cause);
         assertEquals("wrapper", ex.getMessage());
         assertNotNull(ex.getCause());
      }

      @Test
      @DisplayName("ExitException is InputException")
      void exitExceptionIsInputException() {
         ExitException ex = new ExitException();
         assertTrue(ex instanceof InputException);
      }

      @Test
      @DisplayName("ScrollEvent constructs without error")
      void scrollEventConstruction() {
         ScrollEvent se = new ScrollEvent(5, false);
         assertNotNull(se);
         ScrollEvent sh = new ScrollEvent(-3, true);
         assertNotNull(sh);
      }

      @Test
      @DisplayName("CommandEvent constructs without error")
      void commandEventConstruction() {
         CommandEvent ce = new CommandEvent("test");
         assertNotNull(ce);
      }
   }

   // ================================================================
   // Idler registration
   // ================================================================

   @Nested
   @DisplayName("Idler")
   class IdlerTests {

      @Test
      @DisplayName("registerIdle does not throw")
      void registerIdleWorks() {
         assertDoesNotThrow(() ->
            EventQueue.registerIdle(() -> { })
         );
      }
   }

   private static void assertEquals(Object expected, Object actual) {
      org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
   }
}
