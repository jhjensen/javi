package javi;

import java.awt.Cursor;
import java.awt.Shape;
import java.awt.Color;
import java.awt.Canvas;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.AWTEvent;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Font;
import static javi.View.Opcode.*;

abstract class View  extends Canvas {

   abstract void insertedElementsdraw(Graphics gr, int start, int amount);
   abstract void deletedElementsdraw(Graphics gr, int start, int amount);
   abstract void changeddraw(Graphics gr, int start, int amount);
   abstract void movescreendraw(Graphics gr, int amount);
   abstract void refresh(Graphics gr);
   abstract int yCursorChanged(int ychange);
   abstract void cursorChanged(int ychange);
   abstract Position mousepos(MouseEvent event);
   abstract int getRows(float scramount);
   abstract void setSizebyChar(int x, int y);
   abstract int screenFirstLine();
   abstract void screeny(int amount);
   abstract Shape updateCursorShape(Shape sh);
   abstract void setTabStop(int ts);
   abstract int getTabStop();
   abstract void ssetFont(Font font);
   abstract void newGraphics();

   /* Copyright 1996 James Jensen all rights reserved */
   private static final String copyright = "Copyright 1996 James Jensen";

   abstract static class Inserter {
      abstract String getString();
      abstract boolean getOverwrite();
   }

   protected final String getInsertString() {
      //trace("getInsertString " + inserter);
      if (inserter == null)
         return null;
      //trace("getInsertString " + inserter.getString());
      return inserter.getString();
   }

   protected final boolean getOverwrite() {
      if (inserter == null)
         return false;
      return inserter.getOverwrite();
   }

   final void setInsert(Inserter ins)  {
      inserter = ins;
   }

   final void clearInsert()  {
      inserter = null;
   }

   private transient Inserter inserter;

   final boolean nextFlag;

   enum Opcode { NOOP, INSERT, CHANGE,
      DELETE, REDRAW , MSCREEN, BLINKCURSOR
   }

   final class ChangeOpt {

      private  Opcode currop = NOOP;
      private  int saveamount;
      private int savestart;

      void redraw() {
         //trace("redraw");
         currop = Opcode.REDRAW;
         repaint();
      }

      void blink() {
         if (currop == NOOP) {
            currop = BLINKCURSOR;
            //trace("blink cursor repaint");
            repaint();
         } else {
            //trace("blink cursor saveop not null " + currop);
         }
      }

      boolean insert(int start, int amount) {
         //trace("insert currop " + currop +" start " + start  + " amount " + amount);
         pmark.resetMark(fcontext);  //???
         if (currop == Opcode.NOOP || currop == Opcode.BLINKCURSOR) {
            currop = Opcode.INSERT;
            savestart = start;
            saveamount = amount;
         } else {
            //trace("doing redraw oldsaveop = " + saveop);
            currop = Opcode.REDRAW;
         }
         repaint();
         return currop == Opcode.REDRAW;
      }

      boolean lineChanged(int index) {
         //trace("linechange currop " + currop + " index " + index);
         pmark.resetMark(fcontext);
         return changedpro(index, index);
      }   

      void cursorChange(int xChange, int yChange) {
         //trace("cursorChange currop " + currop + " xchange " + xChange + " yChange " + yChange);
         pmark.markChange(fcontext.insertx() + xChange, fcontext.inserty());

//         if (pmark.getMark() != null)
            changedpro(fcontext.inserty(), fcontext.inserty() - yChange);
      } 

      private boolean changedpro(int index1, int index2) {
         //trace("changedpro currop " + currop + "(" + index1 + "," + index2 + ")" );

         if (index2 < index1) {
            int temp = index1;
            index1 = index2;
            index2 = temp;
         }

         switch (currop) {
            case NOOP:
            case BLINKCURSOR:
               currop = Opcode.CHANGE;
               repaint();
               savestart = index1;
               saveamount = index2;
               break;
            case CHANGE:
               if (savestart > index1)
                  savestart = index1;
               if (saveamount < index2)
                  saveamount = index2;
               break;
            default:
               //trace("doing redraw oldsaveop = " + currop);
               currop = Opcode.REDRAW;
               repaint();
         }
         return currop == Opcode.REDRAW;
      }

      boolean delete(int start, int amount) {
         //trace("delete currop " + currop + " start " + start + " amount " + amount);
         pmark.resetMark(fcontext);
         if (currop == Opcode.NOOP || currop == Opcode.BLINKCURSOR) {
            currop = Opcode.DELETE;
            savestart = start;
            saveamount = amount;
         } else {
            //trace("doing redraw oldcurrop = " + currop);
            currop = Opcode.REDRAW;
         }
         repaint();
         return currop == Opcode.REDRAW;
      }
       
      void mscreen(int amount, int limit) {
         //trace("mscreen currop " + currop +"  amount " + amount  + " limit " + limit);
         //Thread.dumpStack();
         if (currop == NOOP || currop == Opcode.BLINKCURSOR) {
            saveamount = 0;
            currop = MSCREEN;
         }

         if (currop == MSCREEN && Math.abs(amount) < limit)
            saveamount += amount;
         else {
            currop = REDRAW;
         }
         repaint();
      }

      private void rpaint(Graphics2D gr) {
         if (currop != NOOP) {
            //if (currop != BLINKCURSOR) trace("rpaint currop = " + currop + " this " + this);
            //trace("rpaint currop = " + currop + " this " + this);

            // cursor must be off before other drawing is done, or it messes up XOR
            if (currop ==   BLINKCURSOR || cursoron) 
               bcursor(gr);

            switch (currop) {

               case REDRAW:
                  refresh(gr);
                  break;

               case INSERT:
                  insertedElementsdraw(gr, savestart, saveamount);
                  break;

               case DELETE:
                  deletedElementsdraw(gr, savestart, saveamount);
                  break;

               case CHANGE:
                  changeddraw(gr, savestart, saveamount);
                  break;

               case MSCREEN:
                  movescreendraw(gr, saveamount);
                  break;

               case NOOP:
               case BLINKCURSOR:
                  break;
            }
            if (currop != BLINKCURSOR)
               bcursor(gr); // always leave cursor on after doing something
         }
         currop = NOOP;
      }

   };

   final void redraw() {
      op.redraw();
   }

   void blinkcursor() {
      //trace("this blink cursor " + this);
      op.blink();
   }

   private final ChangeOpt op = new ChangeOpt();

   void lineChanged(int index) {
      op.lineChanged(index);
   }

   void mscreen(int amount, int limit) {
      op.mscreen(amount,limit);
   }

   void cursorChange(int xChange, int yChange) {
      op.cursorChange(xChange,yChange);
   }

   protected static final transient int inset = 2;

   private transient boolean delayerflag;

   protected FvContext fcontext;

   protected transient MarkInfo pmark = new MarkInfo();

   private transient boolean cursoron = false;
   private transient boolean cursoractive = false;
   private transient Color cursorcolor =  AtView.cursorColor;
   private transient Shape cursorshape;
   private transient Graphics oldgr;
   private boolean checkCursor= true;

   private void readObject(java.io.ObjectInputStream is)
         throws ClassNotFoundException, java.io.IOException {

      is.defaultReadObject();
      pmark = new MarkInfo();
      common();
   }

   public boolean isFocusable() {
      return false;
   }

   private void common() {
      /*
         HashSet<AWTKeyStroke> keyset =
             new HashSet<AWTKeyStroke>(getFocusTraversalKeys(
             KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
         );

         for (Iterator it = keyset.iterator();it.hasNext();) {
             AWTKeyStroke key = (AWTKeyStroke)(it.next());
             if (key.getKeyCode()== KeyEvent.VK_TAB && key.getModifiers() == 0)
               it.remove();
         }
         setFocusTraversalKeys(KeyboardFocusManager.
            FORWARD_TRAVERSAL_KEYS, keyset);

         enableInputMethods(false);
      */
      enableEvents(AWTEvent.MOUSE_EVENT_MASK);
   }

   View(boolean next) {
      super();
      nextFlag = next;
      setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
      common();
   }

   void newfile(FvContext newfvc) {
      //trace("newfvc = " + newfvc);
      if (fcontext != null)
         fcontext.setVisible(false);


      fcontext = newfvc;
      fcontext.setVisible(true);

      if (!fcontext.edvec.contains(1))
         throw new RuntimeException(fcontext.edvec
            + " must contain at least line one ");

      fcontext.fixCursor();
      clearMark();
      op.redraw();
   }

   TextEdit getCurrFile() {
      return fcontext.edvec;
   }

   public void setFont(Font font) {

      EventQueue.biglock2.assertOwned();
      ssetFont(font);
      super.setFont(font);
   }

   public void paint(Graphics g) {
      //trace("paint called ");
      try {
         if (g != oldgr) {
            oldgr = g;
            newGraphics();
         }
         op.redraw();
         npaint((Graphics2D) g);
      } catch (Throwable e) {
         UI.popError("unexpected exception",e);
      }
   }

   public void update(Graphics g) { //  paint will do it's own clearing
      try {
      //trace("update called ");
      //if (op.currop == REDRAW) trace(" got update REDRAW!!");
         if (g != oldgr) {
            oldgr = g;
            newGraphics();
         }
         npaint((Graphics2D) g);
      } catch (Throwable e) {
         UI.popError("unexpected exception",e);
      }
   }

   private void npaint(Graphics2D gr) {
      //trace("npaint");
      if (!EventQueue.biglock2.tryLock())
         repaint(200);
     else try {
         fcontext.getChanges(op);
         op.rpaint(gr);
      } catch (Throwable e) {
         if (UI.popError("npaint caught", e))
            throw new RuntimeException("unignored error ",e);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   private void bcursor(Graphics2D gr) {

      // never move cursor or change cursor color except when off
      if (!cursoron && checkCursor) { 
         cursorcolor =  inserter == null
            ? AtView.cursorColor
            : AtView.insertCursor;
         cursorshape = updateCursorShape(cursorshape);
         checkCursor = false;
      }

      if (cursoractive || cursoron) { // if cursor is not active turn it off
         doCursor(gr);
      }
   }

   private void doCursor(Graphics2D gr) {
      //trace("doCursor cursoron " + cursoron + " cursorColor " + cursorcolor);
      cursoron = !cursoron;
      gr.setXORMode(cursorcolor);
      gr.setColor(AtView.background);
      gr.fill(cursorshape);
      gr.setPaintMode();
      //trace("doCursor cursoron " + cursoron);
   }

   class Delayer implements Runnable {
      private int readin;

      Delayer() {
         readin = fcontext.edvec.readIn();
      }

      public void run() {
         delayerflag = true;
         try {
            while (true) {
               EditContainer text = fcontext.edvec;
               trace("sleeping 50");
               Thread.sleep(50);
               if (text.readIn() > readin || text.donereading()) {
                  delayerflag = false;
                  repaint();
                  return;
               }
            }
         } catch (InterruptedException e) { /* ignore interrupts */
         }
         delayerflag = false;
      }
   }

   void needMoreText() {
      if (!delayerflag)
         new Thread(new Delayer(), "oldview delayer").start();
   }

   void setMark(Position markposi) {
      Position pos = pmark.getMark();
      if (pos == markposi)
         return;
      if (pos != null)
         pmark.clearMark(fcontext);
      pmark.setMark(markposi, fcontext);
      op.changedpro(markposi.y, fcontext.inserty());
      fcontext.cursorabs(markposi);
   }

   void clearMark() {
      Position pos = pmark.getMark();
      pmark.clearMark(fcontext);
      if (pos != null)
         op.changedpro(pos.y, fcontext.inserty());
   }

   Position getMark() {
      return pmark.getMark();
   }

   static final class MarkInfo {

      private int sx1 = 0;
      private int sx2 = 0;
      private int sy1 = 0;
      private int sy2 = 0;

      private MovePos markpos;

      public String toString() {
         return "(" + sx1 + "," + sy1 + "),(" + sx2 + "," + sy2 + ")";
      }

      void markChange(int x, int y) {
         if (markpos == null) {
            sy1 = 0;
            sx1 = 0;
            sy2 = 0;
            sx2 = 0;
         } else if (markpos.y < y) {
            sy1 = markpos.y;
            sx1 = markpos.x;
            sy2 = y;
            sx2 = x;
         } else  if (markpos.y > y) {
            sy1 = y;
            sx1 = x;
            sy2 = markpos.y;
            sx2 = markpos.x;
         } else if (markpos.x < x) {
            sy1 = markpos.y;
            sx1 = markpos.x;
            sy2 = y;
            sx2 = x;
         } else if (markpos.x > x) {
            sy1 = y;
            sx1 = x;
            sy2 = markpos.y;
            sx2 = markpos.x;
         } else {
            sy1 = 0;
            sx1 = 0;
            sy2 = 0;
            sx2 = 0;
         }
            //trace("sx1 " + sx1 + " sy1 " + sy1 + " sx2 " + sx2 + " sy2 " + sy2);
      }

      int endh(int tline) {
         return  (tline >= sy1 &&  tline <= sy2)
            ? (tline == sy2)
                   ? sx2
                   : Integer.MAX_VALUE
             : 0;
      }

      int starth(int tline) {
         return (tline >= sy1 &&  tline <= sy2 && tline == sy1)
            ? sx1
            : 0;
      }

      Position getMark() {
         return  markpos == null
            ? null
            : new Position(markpos, "vt100 emu", "savecursor");
      }

      void resetMark(FvContext fvc) {
         EditContainer ev = fvc.edvec;
         if (markpos != null) {
            if (!ev.containsNow(markpos.y))
               markpos.y = ev.finish() - 1;
            if (markpos.x > ev.at(markpos.y).toString().length())
               markpos.x = ev.at(markpos.y).toString().length();
            markChange(fvc.insertx(), fvc.inserty());
         }
      }

      void clearMark(FvContext fvc) {
         markpos = null;
         markChange(fvc.insertx(), fvc.inserty());
      }

      void setMark(Position markposi, FvContext fvc) {
         if (markposi == null)
            markpos = null;
         else if (markpos == null)
            markpos = new MovePos(markposi);
         else
            markpos.set(markposi);
         markChange(fvc.insertx(), fvc.inserty());
      }
   }

   @SuppressWarnings("fallthrough")
   public void processEvent(AWTEvent ev) {
      //trace("ev " + ev.getID() + "  has focus " + hasFocus());
      switch (ev.getID()) {
         case MouseEvent.MOUSE_PRESSED:
         case MouseEvent.MOUSE_RELEASED:
         case MouseEvent.MOUSE_WHEEL:
            EventQueue.insert(ev);
            break;
         case MouseEvent.MOUSE_ENTERED:
         case MouseEvent.MOUSE_EXITED:
         case MouseEvent.MOUSE_CLICKED:
         case KeyEvent.KEY_RELEASED:
         case KeyEvent.KEY_TYPED:
            break;

         default:
            trace("unhandle event ev " + ev + "  has focus " + hasFocus());
            super.processEvent(ev);
      }
   }

   void cursoroff() {
      //trace("cursoroff cursoractive " + cursoractive + " cursoron " + cursoron +"");
      cursoractive = false;
      if (cursoron)
         blinkcursor();
   }

   void cursoron() {
      //trace("cursoron cursoractive " + cursoractive + " cursoron " + cursoron +"");
      checkCursor = true;
      cursoractive = true;
      blinkcursor();
   }

   void placeline(int lineno, float amount) {
      int row = getRows(amount);
      screenFirstLine();
      row =  lineno - screenFirstLine() - row;
      screeny(row);
   }

   static void trace(String str) {
      Tools.trace(str, 1);
   }

}
