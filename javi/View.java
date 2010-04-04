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

   abstract void insertedElementsdraw(int start, int amount);
   abstract void deletedElementsdraw(int start, int amount);
   abstract void changeddraw(int start, int amount);
   abstract void movescreendraw(int amount);
   abstract void refresh();
   abstract int yCursorChanged(int ychange);
   abstract void cursorChanged(int ychange);
//abstract int cursorchanged(int xchange, int ychange);
   abstract Position mousepos(MouseEvent event);
   abstract int getRows(float scramount);
   abstract void setSizebyChar(int x, int y);
   abstract int screenFirstLine();
   abstract void screeny(int amount);
   abstract Shape updateCursorShape(Shape sh);
//abstract void changey(int count, fvcontext fvc);
   abstract void setTabStop(int ts);
   abstract int getTabStop();
   abstract void ssetFont(Font font);

   /* Copyright 1996 James Jensen all rights reserved */
   private static final String copyright = "Copyright 1996 James Jensen";

   static abstract class Inserter {
      abstract String getString();
      abstract boolean getOverwrite();
   }

   protected final String getInsertString() {
      if (inserter == null)
         return null;
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

   protected enum Opcode { NOOP, INSERT, CHANGE, DELETE, REDRAW , MSCREEN };

   protected  transient Opcode saveop;
   protected  transient int saveamount;

   protected static final transient int inset = 2;
   protected transient Graphics2D cursorg;

   private transient boolean delayerflag;

   protected FvContext fcontext;
   protected transient MarkInfo pmark = new MarkInfo();

   private transient int savestart;
   private transient boolean cursoron = false;
   private transient boolean cursoractive = true;
   private transient Color cursorcolor;
   private transient Shape cursorshape;

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
      saveop = REDRAW;

   }

   TextEdit getCurrFile() {
      return fcontext.edvec;
   }

   public void setFont(Font font) {

      synchronized  (EventQueue.biglock) {
         ssetFont(font);
      }
      super.setFont(font);
   }

   void redraw() {
      saveop = REDRAW;
   }

   boolean insertedElements(int start, int amount) {
      pmark.resetMark(fcontext);  //???
      if (saveop == NOOP) {
         saveop = INSERT;
         savestart = start;
         saveamount = amount;
      } else {
         //trace("doing redraw oldsaveop = " + saveop);
         saveop = REDRAW;
      }
      return saveop == REDRAW;
   }


   boolean changed(int index) {
      pmark.resetMark(fcontext);
      changedpro(index, index);
      return saveop == REDRAW;
   }

   void changedpro(int index1, int index2) {
//trace("(" + index1 + "," + index2 + ")" );
      if (index2 < index1) {
         int temp = index1;
         index1 = index2;
         index2 = temp;
      }

      switch (saveop) {
         case NOOP:
            saveop = CHANGE;
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
            //trace("doing redraw oldsaveop = " + saveop);
            saveop = REDRAW;
      }
   }

   boolean deletedElements(int start, int amount) {
      pmark.resetMark(fcontext);
      if (saveop == NOOP) {
         saveop = DELETE;
         savestart = start;
         saveamount = amount;
      } else {
         //trace("doing redraw oldsaveop = " + saveop);
         saveop = REDRAW;
      }
      return saveop == REDRAW;
   }

   public void update(Graphics g) { //  paint will do it's own clearing
      paint(g);
   }


   public void repaint() {
      if (!isVisible())
         return;
      //trace("redrawing screen " + this);
      super.repaint();
   }

   void npaint() {
      try {
         fcontext.getChanges();
         //trace("npaint saveop = " +saveop);
         //trace("saveop = " + saveop );
         //+ " cursorg = " + cursorg);
         //trace("view " + this);
         if (cursorg != null && ((saveop != NOOP) || !isValid()))
            rpaint();
      } catch (Throwable e) {
         UI.popError("npaint caught", e);
      }
   }

   private class Redraw extends EventQueue.IEvent {

      void execute() {
         saveop = NOOP; // force entire screen to be updated
         rpaint();
      }
   }

   public void paint(Graphics g) {
      //trace("inserting redraw");
      EventQueue.insert(new Redraw());
   }

   @SuppressWarnings("fallthrough")
   private void rpaint() {
      //trace("saveop = " + saveop);
      switch (saveop) {
         case REDRAW:
            //trace("REDRAW");

            if (!isValid())
               saveop = NOOP;    // forces background update
            //intential fall through
         case NOOP:
            refresh();
            break;

         case INSERT:
            insertedElementsdraw(savestart, saveamount);
            break;

         case DELETE:
            deletedElementsdraw(savestart, saveamount);
            break;

         case CHANGE:
            changeddraw(savestart, saveamount);
            break;

         case MSCREEN:
            movescreendraw(saveamount);
            break;
      }
      saveop = NOOP;
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
         new Thread(new Delayer(),"oldview delayer").start();
   }

   void setMark(Position markposi) {
      Position pos = pmark.getMark();
      if (pos == markposi)
         return;
      if (pos != null)
         pmark.clearMark(fcontext);
      pmark.setMark(markposi, fcontext);
      changedpro(markposi.y, fcontext.inserty());
      fcontext.cursorabs(markposi);
   }

   void clearMark() {
      Position pos = pmark.getMark();
      pmark.clearMark(fcontext);
      if (pos != null)
         changedpro(pos.y, fcontext.inserty());
   }

   Position getMark() {
      return pmark.getMark();
   }

   static class MarkInfo {

      private int sx1 = 0;
      private int sx2 = 0;
      private int sy1 = 0;
      private int sy2 = 0;

      private MovePos markpos;

      public String toString() {
         return "(" + sx1 + "," + sy1 + "),(" + sx2 + "," + sy2 + ")";
      }
      void cursorChanged(int x, int y) {
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
            cursorChanged(fvc.insertx(), fvc.inserty());
         }
      }

      void clearMark(FvContext fvc) {
         markpos = null;
         cursorChanged(fvc.insertx(), fvc.inserty());
      }

      void setMark(Position markposi, FvContext fvc) {
         if (markposi == null)
            markpos = null;
         else if (markpos == null)
            markpos = new MovePos(markposi);
         else
            markpos.set(markposi);
         cursorChanged(fvc.insertx(), fvc.inserty());
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
      Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
      if (cursoron)
         blinkcursor();
      cursoractive = false;
   }

   void cursoron() {
      //trace("cursoron cursoractive " + cursoractive + " cursoron " + cursoron +"");
      cursorshape = updateCursorShape(cursorshape);
      cursorcolor =  inserter == null
                     ? AtView.cursorColor
                     : AtView.insertCursor;

      cursoractive = true;
      blinkcursor();
   }

   boolean blinkcursor() {
      //trace("blink cursor " + this);
      //trace("blink cursorshape.bounds = " + cursorshape.getBounds() + " cursorg = " + cursorg + " cursor on = " + cursoron  );
      //trace("blink cursor color " + cursorcolor);
//   if (!hasFocus()) {
//      KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
//      trace("current view doesn't have focus  activeWindow " + kfm.getActiveWindow());
//      trace("focusOwner " + kfm.getFocusOwner());
//   }

      if (!cursoractive)
         throw new RuntimeException();

      if (cursorg == null) {  // this really shouldn't happen ???
         trace("!!!!!! cursorg  null");
         return cursoron; // can happen on add view
      }

      cursorg.setXORMode(cursorcolor);
      cursorg.setColor(AtView.background);
      cursorg.fill(cursorshape);
      cursorg.setPaintMode();
      cursoron = !cursoron;
      return cursoron;
   }

   Graphics2D getGraphics2D() {
      Graphics2D g =  (Graphics2D) super.getGraphics();
      if  (g == null) {
         Tools.doGC();
         g = (Graphics2D) super.getGraphics();
      }
      //trace("getGraphics2d" );
      return g;
   }

   void placeline(int lineno, float amount) {
      int row = getRows(amount);
//   if (row >getRows() - 1)
//      row = getRows() - 1;
//trace("row " + row + " inserty" + fcontext.inserty() + "firstline " +
      screenFirstLine();
      row =  lineno - screenFirstLine() - row;
      screeny(row);
   }

   static void trace(String str) {
      Tools.trace(str, 1);
   }

}
