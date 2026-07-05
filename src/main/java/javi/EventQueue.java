package javi;

import java.io.IOException;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import history.Tools;
import static history.Tools.trace;
import static history.Tools.traceLev;

/**
 * Central event dispatch and synchronization for the Javi editor.
 *
 * <p>
 * EventQueue manages:
 * <ul>
 * <li><b>Event queue</b>: Key events, commands, and other inputs</li>
 * <li><b>Global lock</b>: {@link #biglock2} coordinates all editor
 * operations</li>
 * <li><b>Idle processing</b>: Background tasks run when queue empty</li>
 * <li><b>Cursor blinking</b>: Timer-based cursor visibility toggle</li>
 * </ul>
 *
 * <h2>The Big Lock (biglock2)</h2>
 * <p>
 * <b>CRITICAL</b>: {@link #biglock2} is the primary synchronization mechanism.
 * Nearly all editor operations must hold this lock. It is a {@link DebugLock}
 * (extended ReentrantLock) with debugging support:
 * </p>
 * <ul>
 * <li>{@code assertOwned()} - Verify current thread holds lock</li>
 * <li>{@code assertUnOwned()} - Verify current thread doesn't hold lock</li>
 * <li>Timeout-based acquisition with logging on contention</li>
 * </ul>
 *
 * <h2>Event Loop</h2>
 * <p>
 * The main loop in {@link #nextEvent} handles:
 * </p>
 * <ol>
 * <li>Check queue for pending events</li>
 * <li>If empty, run idle handlers (file backup, etc.)</li>
 * <li>Turn on cursor, wait for input with timeout</li>
 * <li>Blink cursor on timeout, repeat</li>
 * <li>Periodic GC after ~1 minute idle</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * <b>WARNING</b>: Lock ordering issues exist. See BUGS.md B3 and B7.
 * The pattern of unlocking {@code biglock2} in {@code inextEvent} while
 * other code may hold it can lead to deadlock.
 * </p>
 *
 * @see DebugLock
 * @see Idler
 * @see IEvent
 */
public final class EventQueue {

   private EventQueue() {
   }

   public static final class DebugLock extends ReentrantLock {
      private static final long serialVersionUID = 1;

      public void lock() {
         // Tools.trace("locking " + this,1);
         // super.lock();
         while (true) {
            try {
               if (super.tryLock(2, TimeUnit.SECONDS))
                  return;
            } catch (InterruptedException e) {
               trace("caught " + e);
            }
            // trace("failed to get lock", 1);
            // trace("owning thread: " + getOwner(), 1);
            // Thread.dumpStack();
         }
      }

      // public void unlock() {
      // //Tools.trace("unlocking " + this,1);
      // super.unlock();
      // }

      void assertOwned() {
         if (!isHeldByCurrentThread())
            throw new RuntimeException(
                  "lock not held " + Thread.currentThread());
      }

      public void assertUnOwned() {
         if (isHeldByCurrentThread())
            throw new RuntimeException(
                  "lock held " + Thread.currentThread());
      }

      public boolean tryLock(long time, TimeUnit tu) throws InterruptedException {
         // Tools.trace("locking " + this,1);
         if (!super.tryLock(time, TimeUnit.SECONDS)) {
            traceLev("failed to get lock continueing .", 1);
            traceLev("owning thread: " + getOwner(), 1);
            return false;
         }
         return true;
      }
   }

   // static ReentrantLock biglock = new ReentrantLock();
   public static final DebugLock biglock2 = new DebugLock();

   private static LinkedList<Object> queue = new LinkedList<>();

   private static volatile boolean focused = true;
   /** Blink cursor for this many cycles then hold steady. */
   private static final int MAX_BLINKS = 20; // ~10s at 500ms

   public abstract static class IEvent {
      public abstract void execute() throws InputException;
   }

   public interface Idler {
      void idle() throws IOException;
   }

   private static ArrayList<Idler> iList = new ArrayList<>(3);

   public static void registerIdle(Idler inst) {
      // trace("adding Idler " + inst);
      iList.add(inst);
   }

   abstract static class CursorControl {
      abstract void setCursorOn();

      abstract void setCursorOff();

      abstract void blinkcursor();
   }

   private static Object inextEvent(CursorControl vi) {
      Object ev = null;
      // B7: verify the caller holds biglock2 before we release it.
      // This surfaces protocol violations immediately rather than allowing
      // a silent IllegalMonitorStateException from unlock().
      biglock2.assertOwned();
      try {
         biglock2.unlock();
         //trace("Init time trace: getting event");
         synchronized (EventQueue.class) {
            if (0 != queue.size())
               ev = queue.removeFirst();
         }

         if (null != ev) {
            biglock2.lock();
            return ev;
         }

         while (true)
            try {
               for (Idler id : iList) {
                  //trace("executing Idler " + id);
                  biglock2.lock();
                  try {
                     id.idle();
                  } finally {
                     biglock2.unlock();
                  }
               }
               break;
            } catch (IOException e) {
               UI.popError("exception caught in idle loop", e);
            }

         vi.setCursorOn();
         int blinkCount = 0;
         int gccount = 120; // gc after ~60s of blinking

         while (null == ev) {
            synchronized (EventQueue.class) {
               if (0 != queue.size()) {
                  ev = queue.removeFirst();
                  break;
               } else if (0 == gccount--) {
                  gccount = 600; // then every ~5 min
                  Tools.doGC();
                  continue;
               } else {
                  try {
                     if (focused && blinkCount < MAX_BLINKS)
                        EventQueue.class.wait(500);
                     else
                        EventQueue.class.wait(); // infinite — no more blinking
                  } catch (InterruptedException e) {
                     UI.popError("unexpected interrupt ", e);
                  }
               }
            }
            if (focused && blinkCount < MAX_BLINKS) {
               biglock2.lock();
               vi.blinkcursor();
               biglock2.unlock();
               blinkCount++;
            }
         }

         vi.setCursorOff();
         //trace("eventqueue.java returning " + ev);
         biglock2.lock();
         return ev;
      } finally {
         // B4: isHeldByCurrentThread() reflects actual lock state - no stale
         // flag risk if an exception is thrown before a lockHeld assignment.
         if (!biglock2.isHeldByCurrentThread()) {
            biglock2.lock();
         }
      }
   }

   public static void focusGained() {
      synchronized (EventQueue.class) {
         EventQueue.class.notifyAll();
         focused = true;
      }
      Vt100Parser.wakeAll(); // wake sleeping parser threads
   }

   public static void focusLost() {
      synchronized (EventQueue.class) {
         focused = false;
      }
   }

   /** Returns true when the application window has focus. */
   public static boolean isFocused() {
      return focused;
   }

   static JeyEvent nextEvent(CursorControl vi) throws InputException {
      while (true) {
         Object ev = inextEvent(vi);
         if (ev instanceof IEvent) {
            ((IEvent) ev).execute();
         } else
            return (JeyEvent) ev;
      }
   }

   public static char nextKey(CursorControl vi) throws InputException {
      return nextEvent(vi).getKeyChar();
   }

   // B4 FIX: removed 'synchronized' — the old declaration held the
   // EventQueue.class monitor for the entire nextEvent→inextEvent call,
   // which internally releases/reacquires biglock2. Any thread holding
   // biglock2 that called insert() (synchronized on EventQueue.class) would
   // deadlock (ABBA ordering). Queue access inside inextEvent is already
   // guarded by fine-grained synchronized(EventQueue.class) blocks, so the
   // outer synchronized was redundant and hazardous.
   public static JeyEvent nextKeye(CursorControl vi) throws InputException {
      return nextEvent(vi);
   }

   static synchronized void pushback(Object e) {
      queue.addFirst(e);
      EventQueue.class.notifyAll();
   }

   public static synchronized void insert(JeyEvent e) {
      // trace("inserting " + e);
      queue.addLast(e);
      EventQueue.class.notifyAll();
   }

   public static synchronized void insert(IEvent e) {
      // trace("inserting " + e);
      queue.addLast(e);
      EventQueue.class.notifyAll();
   }

   /**
    * Removes any pending Enter/newline JeyEvents from the queue.
    * Used to drain stale key-repeat events after a context switch
    * (e.g. lesson continuation) so they don't fire on the new state.
    */
   public static synchronized void drainEnterEvents() {
      queue.removeIf(ev -> {
         if (ev instanceof JeyEvent) {
            char c = ((JeyEvent) ev).getKeyChar();
            return c == '\r' || c == '\n';
         }
         return false;
      });
   }

}
