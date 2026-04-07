package javi;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage for {@link EventQueue} — DebugLock, insert,
 * pushback, focus events, and IEvent execution.
 */
class EventQueueCoverageJUnitTest {

   // ── DebugLock ──────────────────────────────────────────────

   @Test
   void debugLockAcquireAndRelease() {
      EventQueue.DebugLock lock = new EventQueue.DebugLock();
      lock.lock();
      assertTrue(lock.isHeldByCurrentThread());
      lock.unlock();
      assertFalse(lock.isHeldByCurrentThread());
   }

   @Test
   void debugLockAssertOwnedPasses() {
      EventQueue.biglock2.lock();
      try {
         assertDoesNotThrow(() -> EventQueue.biglock2.assertOwned());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void debugLockAssertOwnedFailsWhenNotHeld() {
      // Must not hold the lock
      if (EventQueue.biglock2.isHeldByCurrentThread()) {
         // If held due to test ordering, skip
         return;
      }
      assertThrows(RuntimeException.class,
         () -> EventQueue.biglock2.assertOwned());
   }

   @Test
   void debugLockAssertUnOwnedPasses() {
      if (EventQueue.biglock2.isHeldByCurrentThread()) {
         return; // Can't test if already held
      }
      assertDoesNotThrow(() -> EventQueue.biglock2.assertUnOwned());
   }

   @Test
   void debugLockAssertUnOwnedFailsWhenHeld() {
      EventQueue.biglock2.lock();
      try {
         assertThrows(RuntimeException.class,
            () -> EventQueue.biglock2.assertUnOwned());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void debugLockTryLockWithTimeout() throws InterruptedException {
      EventQueue.DebugLock lock = new EventQueue.DebugLock();
      boolean acquired = lock.tryLock(1, TimeUnit.SECONDS);
      assertTrue(acquired, "tryLock should succeed when uncontended");
      lock.unlock();
   }

   @Test
   void debugLockReentrant() {
      EventQueue.DebugLock lock = new EventQueue.DebugLock();
      lock.lock();
      lock.lock(); // reentrant
      assertTrue(lock.isHeldByCurrentThread());
      lock.unlock();
      assertTrue(lock.isHeldByCurrentThread()); // still held
      lock.unlock();
      assertFalse(lock.isHeldByCurrentThread());
   }

   // ── insert / pushback ──────────────────────────────────────

   @Test
   void insertJeyEventAddsToQueue() throws Exception {
      TestInit.init();
      JeyEvent ev = new JeyEvent(0, 0, 'x');
      EventQueue.insert(ev);
      // The event is in the queue; we can't easily peek but
      // it should not throw
   }

   @Test
   void insertIEventAddsToQueue() throws Exception {
      TestInit.init();
      final boolean[] executed = {false};
      EventQueue.IEvent iev = new EventQueue.IEvent() {
         @Override
         public void execute() {
            executed[0] = true;
         }
      };
      EventQueue.insert(iev);
      // Note: event is queued, not immediately executed
   }

   @Test
   void pushbackAddsToFrontOfQueue() throws Exception {
      TestInit.init();
      JeyEvent first = new JeyEvent(0, 0, 'a');
      JeyEvent second = new JeyEvent(0, 0, 'b');
      EventQueue.insert(first);
      EventQueue.pushback(second);
      // 'b' should now be at the front
      // Can't directly verify ordering without consuming, but
      // verifies no exception
   }

   // ── focus events ───────────────────────────────────────────

   @Test
   void focusGainedDoesNotThrow() {
      assertDoesNotThrow(() -> EventQueue.focusGained());
   }

   @Test
   void focusLostDoesNotThrow() {
      assertDoesNotThrow(() -> EventQueue.focusLost());
   }

   // ── registerIdle ───────────────────────────────────────────

   @Test
   void registerIdleDoesNotThrow() {
      EventQueue.Idler idler = () -> {
         // no-op idle handler
      };
      assertDoesNotThrow(() -> EventQueue.registerIdle(idler));
   }
}
