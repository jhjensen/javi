package javi;
import java.awt.AWTEvent;
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
private static LinkedList<Object> lowqueue = new LinkedList<Object>();
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
         else if (!cursoron && lowqueue.size()!=0 )
            ev = lowqueue.removeFirst();

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
/* old version
private static synchronized Object inextEvent(View vi) {

 //trace("eventqueue.nextEvent + queue size " + queue.size() );
   while (queue.size()== 0) 
      try {
         for (idler id : iList) {
            //trace("executing idler " + iList.get(i));
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

   Object ev = null;

   while (ev==null) {
      if (queue.size()!= 0)
         ev = queue.removeFirst();
      else if (!cursoron && lowqueue.size()!=0 )
         ev = lowqueue.removeFirst();

      else  if (gccount-- == 0 )  { // after idle awhile gc once
         gccount = 5*60*60*1000/timeout;   // after 5 hours do another gc
         Tools.doGC();
      } else {
         try {EventQueue.class.wait(timeout);}  
         catch (InterruptedException e) {//ignoring interrupts
         }
         cursoron = vi.blinkcursor(); // flip on cursor
      }
   }
 
   vi.cursoroff();
 //trace("eventqueue.java returning " + ev);
   return ev;
}

*/
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

static synchronized void insertlow(Object e) {
   //trace("inserting low" + e);
   lowqueue.addLast(e);
}

static synchronized void insert(Object e) {
   //trace("inserting " + e);
   queue.addLast(e);
   EventQueue.class.notifyAll();
}

/*
private static final String[] rnames = {
  "",
  "capture",
  "endcapture",
  "include",
};

Object doroutine(int rnum,Object arg,int count,int rcount,fvcontext fvc,
     eventqueue evq,boolean dotmode) throws InterruptedException {
//trace("vic doroutine rnum = " + rnum);
   try  { 
      switch (rnum) {
         case 0: return null; // noop
         case 1: capture((String)arg);return null;
         case 2: endcapture();return null;
         case 3: source ((String)arg);return null;
      }
    } catch (IOException e)  {
        ui.reportMessege("command caught IOException" +e);
    }
 return null;
}

private void capture(String filename) throws IOException {
  if (filename==null)
     throw new IOException("null file name");
  capstream = new PrintWriter(new FileOutputStream(filename));
}

private void endcapture()throws IOException  {
  if (capstream!=null) {
     capstream.close();
     capstream = null;
  }
}
private void source(String filename) throws FileNotFoundException {
   sourceStream=new BufferedReader(new FileReader(filename));
}
*/

static void trace(String str) {
   Tools.trace(str,1);
}
}
