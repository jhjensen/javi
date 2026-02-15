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
 * <p>EventQueue manages:
 * <ul>
 *   <li><b>Event queue</b>: Key events, commands, and other inputs</li>
 *   <li><b>Global lock</b>: {@link #biglock2} coordinates all editor operations</li>
 *   <li><b>Idle processing</b>: Background tasks run when queue empty</li>
 *   <li><b>Cursor blinking</b>: Timer-based cursor visibility toggle</li>
 * </ul>
 *
 * <h2>The Big Lock (biglock2)</h2>
 * <p><b>CRITICAL</b>: {@link #biglock2} is the primary synchronization mechanism.
 * Nearly all editor operations must hold this lock. It is a {@link DebugLock}
 * (extended ReentrantLock) with debugging support:</p>
 * <ul>
 *   <li>{@code assertOwned()} - Verify current thread holds lock</li>
 *   <li>{@code assertUnOwned()} - Verify current thread doesn't hold lock</li>
 *   <li>Timeout-based acquisition with logging on contention</li>
 * </ul>
 *
 * <h2>Event Loop</h2>
 * <p>The main loop in {@link #nextEvent} handles:</p>
 * <ol>
 *   <li>Check queue for pending events</li>
 *   <li>If empty, run idle handlers (file backup, etc.)</li>
 *   <li>Turn on cursor, wait for input with timeout</li>
 *   <li>Blink cursor on timeout, repeat</li>
 *   <li>Periodic GC after ~1 minute idle</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * <p><b>WARNING</b>: Lock ordering issues exist. See BUGS.md B3 and B7.
 * The pattern of unlocking {@code biglock2} in {@code inextEvent} while
 * other code may hold it can lead to deadlock.</p>
 *
 * @see DebugLock
 * @see Idler
 * @see IEvent
 */
public final class EventQueue {

   private EventQueue() { }

   public static final class DebugLock extends ReentrantLock {
      private static final long serialVersionUID = 1;
      public   void lock() {
         //Tools.trace("locking " + this,1);
         //super.lock();
         while (true) {
            try {
               if (super.tryLock(2, TimeUnit.SECONDS))
                  return;
            } catch (InterruptedException e) {
               trace("caught " + e);
            }
            //trace("failed to get lock", 1);
            //trace("owning thread: " + getOwner(), 1);
            //Thread.dumpStack();
         }
      }

      //public   void unlock() {
      //   //Tools.trace("unlocking " + this,1);
      //   super.unlock();
      //}

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

      public boolean tryLock(long time, TimeUnit tu) throws
            InterruptedException {
         //Tools.trace("locking " + this,1);
         if (!super.tryLock(time, TimeUnit.SECONDS)) {
            traceLev("failed to get lock continueing .", 1);
            traceLev("owning thread: " + getOwner(), 1);
            return false;
         }
         return true;
      }
   }

//static ReentrantLock biglock = new ReentrantLock();
   public static final DebugLock biglock2 = new DebugLock();

   private static LinkedList<Object> queue = new LinkedList<>();

   private static int timeout = 500;

   public abstract static class IEvent {
      public abstract void execute() throws InputException;
   }

   public interface Idler {
      void idle() throws IOException;
   }

   private static ArrayList<Idler> iList = new ArrayList<>(3);

   public static void registerIdle(Idler inst) {
      //trace("adding Idler " + inst);
      iList.add(inst);
   }

   abstract static class CursorControl {
      abstract void setCursorOn();
      abstract void setCursorOff();
      abstract void blinkcursor();
   }

   private static Object inextEvent(CursorControl vi) {
      Object ev = null;
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
      int gccount = 60 * 1000 / timeout; // gc after about a minute of idle

      while (null == ev) {
         synchronized (EventQueue.class) {
            if (0 != queue.size()) {
               ev = queue.removeFirst();
               break;
            } else if (0 == gccount--)  { // after idle awhile gc once
               // after 5 hours do another gc
               gccount = 5 * 60 * 60 * 1000 / timeout;
               Tools.doGC();
               continue;
            } else {
               try {
                  EventQueue.class.wait(timeout);
               } catch (InterruptedException e) {
                  UI.popError("unexpected interrupt ", e);
               }
            }
         }
         //trace("about to blink cursor on " +vi);
         biglock2.lock();
         vi.blinkcursor(); // flip cursor
         biglock2.unlock();
      }

      vi.setCursorOff();
      //trace("eventqueue.java returning " + ev);
      biglock2.lock();
      return ev;
   }

   public static void focusGained() {
      synchronized (EventQueue.class) {
         EventQueue.class.notifyAll();  // make sure cursor starts blinking
         timeout = 500;
      }
   }

   public static void focusLost() {
      synchronized (EventQueue.class) {
         // redo cursor every once in a while, and do gc
         timeout = 1000 * 60 * 60;
      }
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

   static char nextKey(CursorControl vi) throws InputException {
      return nextEvent(vi).getKeyChar();
   }

   static synchronized JeyEvent nextKeye(CursorControl vi) throws
         InputException {
      return nextEvent(vi);
   }

   static synchronized void pushback(Object e) {
      queue.addFirst(e);
      EventQueue.class.notifyAll();
   }

   public static synchronized void insert(JeyEvent e) {
      //trace("inserting " + e);
      queue.addLast(e);
      EventQueue.class.notifyAll();
   }

   public static synchronized void insert(IEvent e) {
      //trace("inserting " + e);
      queue.addLast(e);
      EventQueue.class.notifyAll();
   }

}
