package javi;
import java.awt.event.KeyEvent;
import java.util.LinkedList;
import java.io.IOException;
import java.util.ArrayList;

public final class EventQueue {
/* Copyright 1996 James Jensen all rights reserved */
static final String copyright = "Copyright 1996 James Jensen";

//static {
//       Thread.dumpStack();
//}
static Object biglock = new Object();
private static EventQueue eventq = new EventQueue(); 

private static LinkedList<Object> queue = new LinkedList<Object>();
//private PrintWriter capstream;
//private BufferedReader sourceStream;


private static final int timeout=500;

static abstract class IEvent {
  abstract void execute() throws MapEvent.ExitException;
}

interface idler {
    void idle() throws IOException;
}

static ArrayList<idler> iList = new ArrayList<idler>(3);

static void registerIdle(idler inst) {
   //trace("adding idler " + inst);
   iList.add(inst);
}

private static Object inextEvent(View vi) {
   Object ev = null;
   synchronized (EventQueue.class) {
       if (queue.size() != 0) 
            ev = queue.removeFirst();
   }

   if (ev!=null)
      return ev;
   while (true) try {
      for (idler id : iList) {
         //trace("executing idler " + id);
         id.idle();
      }
      break;
   } catch (IOException e) {
      UI.popError("exception caught in idle loop",e);
      e.printStackTrace();
   }

   boolean cursoron = true;
   vi.cursoron();
   int gccount = 60*1000/timeout; // gc after about a minute of idle

   while (ev==null) {
      synchronized(EventQueue.class) {
         if (queue.size()!= 0)
            ev = queue.removeFirst();

         else  if (gccount-- == 0 )  { // after idle awhile gc once
            gccount = 5*60*60*1000/timeout;   // after 5 hours do another gc
            Tools.doGC();
         } else {
            try {EventQueue.class.wait(timeout);}  catch (InterruptedException e) {/*ignoring interrupts */}
            //trace("about to blink cursor on " +vi);
            cursoron = vi.blinkcursor(); // flip on cursor
         }
      }
   }
 
   vi.cursoroff();
 //trace("eventqueue.java returning " + ev);
   return ev;
}

static Object nextEvent(View vi) throws MapEvent.ExitException {
    while(true) {
      Object ev = inextEvent(vi);
      if (ev instanceof IEvent)
         ((IEvent)ev).execute();
      else 
         return ev;
   }
}

static char nextKey(View vi) throws MapEvent.ExitException {
   return nextKeye(vi).getKeyChar();
}

static synchronized KeyEvent nextKeye(View vi) throws MapEvent.ExitException {
   while (true) {
      Object e = nextEvent(vi);
      if (e instanceof KeyEvent)
         return (KeyEvent)e;
      else {
         //trace("nextKeye returning esc ");
         pushback(e);
         return new KeyEvent(vi,KeyEvent.KEY_PRESSED,0,0,27,(char)27);
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
   Tools.trace(str,1);
}
}
