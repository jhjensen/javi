package javi;
import java.awt.event.KeyEvent;
import java.util.LinkedList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

public final class EventQueue {
   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";

//static {
//       Thread.dumpStack();
//}

   static final class DebugLock extends ReentrantLock {
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
      void assertUnOwned() {
         if (isHeldByCurrentThread())
            throw new RuntimeException(
               "lock not held " + Thread.currentThread());
      }
      public boolean tryLock(long time, TimeUnit tu) throws
            InterruptedException {
         //Tools.trace("locking " + this,1);
         if (!super.tryLock(time, TimeUnit.SECONDS)) {
            Tools.trace("failed to get lock, shutdown anyway.", 1);
            Tools.trace("owning thread: " + getOwner(), 1);
            return false;
         }
         return true;
      }
   }

//static ReentrantLock biglock = new ReentrantLock();
   static final DebugLock biglock2 = new DebugLock();
   private static EventQueue eventq = new EventQueue();

   private static LinkedList<Object> queue = new LinkedList<Object>();
//private PrintWriter capstream;
//private BufferedReader sourceStream;


   private static final int timeout = 500;

   abstract static class IEvent {
      abstract void execute() throws ExitException;
   }

   interface Idler {
      void idle() throws IOException;
   }

   private static ArrayList<Idler> iList = new ArrayList<Idler>(3);

   static void registerIdle(Idler inst) {
      //trace("adding Idler " + inst);
      iList.add(inst);
   }

   private static Object inextEvent(View vi) {
      Object ev = null;
      biglock2.unlock();
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

      vi.cursoron();
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

      vi.cursoroff();
      //trace("eventqueue.java returning " + ev);
      biglock2.lock();
      return ev;
   }

   static Object nextEvent(View vi) throws ExitException {
      while (true) {
         Object ev = inextEvent(vi);
         if (ev instanceof IEvent) {
            ((IEvent) ev).execute();
         } else
            return ev;
      }
   }

   static char nextKey(View vi) throws ExitException {
      return nextKeye(vi).getKeyChar();
   }

   static synchronized KeyEvent nextKeye(View vi) throws ExitException {
      while (true) {
         Object e = nextEvent(vi);
         if (e instanceof KeyEvent)
            return (KeyEvent) e;
         else {
            //trace("nextKeye returning esc ");
            pushback(e);
            return new KeyEvent(vi, KeyEvent.KEY_PRESSED, 0, 0, 27, (char) 27);
         }
      }
   }

   static synchronized void pushback(Object e) {
      queue.addFirst(e);
      EventQueue.class.notifyAll();
   }

   static synchronized void insert(Object e) {
      //trace("inserting " + e);
      queue.addLast(e);
      EventQueue.class.notifyAll();
   }

   static void trace(String str) {
      Tools.trace(str, 1);
   }
}
