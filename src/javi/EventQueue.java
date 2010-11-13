package javi;
import java.util.LinkedList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

public final class EventQueue {
   private EventQueue() { }
   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";

//static {
//       Thread.dumpStack();
//}

   public static final class DebugLock extends ReentrantLock {
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
            Tools.trace("failed to get lock", 1);
            Tools.trace("owning thread: " + getOwner(), 1);
            Thread.dumpStack();
         }
      }

      public   void unlock() {
         //Tools.trace("unlocking " + this,1);
         super.unlock();
      }
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
            Tools.trace("failed to get lock continueing .", 1);
            Tools.trace("owning thread: " + getOwner(), 1);
            return false;
         }
         return true;
      }
   }

//static ReentrantLock biglock = new ReentrantLock();
   public static final DebugLock biglock2 = new DebugLock();

   private static LinkedList<Object> queue = new LinkedList<Object>();
//private PrintWriter capstream;
//private BufferedReader sourceStream;


   private static int timeout = 500;

   public abstract static class IEvent {
      public abstract void execute() throws InputException;
   }

   public interface Idler {
      void idle() throws IOException;
   }

   private static ArrayList<Idler> iList = new ArrayList<Idler>(3);

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
         if (queue.size() != 0)
            ev = queue.removeFirst();
      }

      if (ev != null) {
         biglock2.lock();
         return ev;
      }

      while (true)
         try {
            for (Idler id : iList) {
               //trace("executing Idler " + id);
               biglock2.lock();
               id.idle();
               biglock2.unlock();
            }
            break;
         } catch (IOException e) {
            UI.popError("exception caught in idle loop", e);
            e.printStackTrace();
         }

      vi.setCursorOn();
      int gccount = 60 * 1000 / timeout; // gc after about a minute of idle

      while (ev == null) {
         synchronized (EventQueue.class) {
            if (queue.size() != 0) {
               ev = queue.removeFirst();
               break;
            } else if (gccount-- == 0)  { // after idle awhile gc once
               // after 5 hours do another gc
               gccount = 5 * 60 * 60 * 1000 / timeout;
               Tools.doGC();
               continue;
            } else {
               try {
                  EventQueue.class.wait(timeout);
               } catch (InterruptedException e) {
                  UI.popError("unexpected interrupt " , e);
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
      return nextKeye(vi).getKeyChar();
   }

   static synchronized JeyEvent nextKeye(CursorControl vi) throws
         InputException {
      while (true) {
         Object e = nextEvent(vi);
         if (e instanceof JeyEvent)
            return (JeyEvent) e;
         else {
            //trace("nextKeye returning esc ");
            pushback(e);
            return new JeyEvent(0, 27, (char) 27);
         }
      }
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

   static void trace(String str) {
      Tools.trace(str, 1);
   }

}
