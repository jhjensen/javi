package javi;

import static javi.ChangeOpt.Opcode.*;
import static history.Tools.trace;

public abstract class View  extends
      EventQueue.CursorControl implements java.io.Serializable {

   public abstract void cursorChanged(int newX, int newY);
   public abstract int yCursorChanged(int newY);
   public abstract int getRows(float scramount);
   public abstract int screenFirstLine();
   public abstract int screeny(int amount);
   public abstract void setTabStop(int ts);
   public abstract int getTabStop();
   public abstract void repaint();
   public abstract boolean isVisible();
   public abstract void setSizebyChar(int xchar, int ychar);

   protected abstract void startInsertion(Inserter ins);
   protected abstract void endInsertion(Inserter ins);

   private transient boolean cursoron = false;

   protected final boolean getCursorOn() {
      return cursoron;
   }

   private transient boolean cursoractive = false;
   private boolean checkCursor = true;

   public abstract static class Inserter {
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
      startInsertion(ins);
   }

   final void clearInsert()  {
      endInsertion(inserter);
      inserter = null;
   }

   private transient Inserter inserter;

   private final boolean traverse;

   final boolean isTraverseable() {
      return traverse;
   }

   public abstract class COpt extends ChangeOpt {

      private  Opcode currop = NOOP;
      private  int saveAmount;
      protected final int getSaveAmount() {
         return saveAmount;
      }
      private int saveStart;
      protected final int getSaveStart() {
         return saveStart;
      }

      protected final Opcode resetOp() {
         Opcode retval = currop;
         currop = NOOP;
         return retval;
      }

      public final void redraw() {
         //trace("redraw");
         currop = REDRAW;
         repaint();
      }

      public final void blink() {
         if (currop == NOOP) {
            currop = BLINKCURSOR;
            //trace("blink cursor repaint");
            repaint();
         }
         //else trace("blink cursor saveop not null " + currop);
      }

      final boolean insert(int start, int amount) {
         //trace("insert currop " + currop +" start " + start  + " amount " + amount);
         pmark.resetMark(text, fileX, fileY);  //???
         if (currop == NOOP || currop == BLINKCURSOR) {
            currop = INSERT;
            saveStart = start;
            saveAmount = amount;
         } else {
            //trace("doing redraw oldsaveop = " + saveop);
            currop = REDRAW;
         }
         repaint();
         return currop == REDRAW;
      }

      public final boolean lineChanged(int index) {
         //trace("linechange currop " + currop + " index " + index);
         pmark.resetMark(text, fileX, fileY);
         return changedpro(index, index);
      }

      public final void cursorChange(int xChange, int yChange) {
         //trace("cursorChange currop " + currop + " xchange " + xChange + " yChange " + yChange);
         pmark.markChange(fileX + xChange, fileY);
         changedpro(fileY, fileY - yChange);
      }

      final boolean changedpro(int index1, int index2) {
         //trace("changedpro currop " + currop + "(" + index1 + "," + index2 + ")" );

         if (index2 < index1) {
            int temp = index1;
            index1 = index2;
            index2 = temp;
         }

         switch (currop) {
            case NOOP:
            case BLINKCURSOR:
               currop = CHANGE;
               repaint();
               saveStart = index1;
               saveAmount = index2;
               break;
            case CHANGE:
               if (saveStart > index1)
                  saveStart = index1;
               if (saveAmount < index2)
                  saveAmount = index2;
               break;
            default:
               //trace("doing redraw oldsaveop = " + currop);
               currop = REDRAW;
               repaint();
         }
         return currop == REDRAW;
      }

      final boolean delete(int start, int amount) {
         //trace("delete currop " + currop + " start " + start + " amount " + amount);
         pmark.resetMark(text, fileX, fileY);
         if (currop == NOOP || currop == BLINKCURSOR) {
            currop = DELETE;
            saveStart = start;
            saveAmount = amount;
         } else {
            //trace("doing redraw oldcurrop = " + currop);
            currop = REDRAW;
         }
         repaint();
         return currop == REDRAW;
      }

      final void mscreen(int amount, int limit) {
         //trace("mscreen currop " + currop +"  amount " + amount  + " limit " + limit);
         //Thread.dumpStack();
         if (currop == NOOP || currop == BLINKCURSOR) {
            saveAmount = 0;
            currop = MSCREEN;
         }

         if (currop == MSCREEN && Math.abs(amount) < limit)
            saveAmount += amount;
         else {
            currop = REDRAW;
         }
         repaint();
      }

   };

   public static final int updateCursor = 0x1;  // cursor needs updating
   public static final int doBlink = 0x2;            // cursor needs blinking
   public static final int insertFlag = 0x4;            // in insert mode
   public static final int onFlag = 0x8;              // cursor is turning on

   public final int needBlink() {

      // never move cursor or change cursor color except when off
      //trace("needBlink cursoron " + cursoron + " checkCursor " + checkCursor);
      boolean update = checkCursor && !cursoron;

      if (cursoractive || cursoron) { // if cursor is not active turn it off
         if (update)
            checkCursor = false;
         cursoron = !cursoron;
         return  doBlink
            | (inserter == null ? 0 : insertFlag)
            | (cursoron ? onFlag :  0)
            | (update ? updateCursor : 0);

      }
      return 0;
   }

   public final void redraw() {
      op.redraw();
   }

   final void blinkcursor() {
      //trace("this blink cursor " + this);
      op.blink();
   }

   final void lineChanged(int index) {
      op.lineChanged(index);
   }

   public final void mscreen(int amount, int limit) {
      op.mscreen(amount, limit);
   }

   public final void cursorChange(int xChange, int yChange) {
      op.cursorChange(xChange, yChange);
   }

   protected static final transient int inset = 2;

   private transient boolean delayerflag;

   private TextEdit text;
   protected final TextEdit gettext() {
      return text;
   }

   private transient int fileX = 0;
   protected final int getfileX() {
      return fileX;
   }

   private transient int fileY = 1;

   protected final int getfileY() {
      return fileY;
   }

   public final void setFilePos(int fx, int fy) {
      fileX = fx;
      fileY = fy;
   }

   private transient UndoHistory.EhMark chmark;

   public final void getChanges() {
      chmark.getChanges(op);
   }

   private transient MarkInfo pmark = new MarkInfo();
   protected final MarkInfo getPmark() {
      return pmark;
   }

   private void readObject(java.io.ObjectInputStream is) throws
         ClassNotFoundException, java.io.IOException {

      is.defaultReadObject();
      pmark = new MarkInfo();
   }

   public final boolean isFocusable() {
      return false;
   }

   private final ChangeOpt op;
   protected abstract ChangeOpt getChangeOpt();

   public View(boolean traversei) {
      op = getChangeOpt();
      //trace("op " + op + " this " + this);
      traverse = traversei;
      //trace("created view " + this);
   }

   final void newfile(TextEdit texti, int curX, int curY) {
      //trace("newfile curX" + curX + " curY " + curY + " " + texti);
      text = texti;
      chmark = text.copyCurr();

      clearMark();
      cursorChanged(curX, curY);
      op.redraw();
   }

   public final TextEdit getCurrFile() {
      return text;
   }

/*
   public void setFont(Font font) {

      //trace("setting View font " + font + " "  + this);
      ssetFont(font);
      super.setFont(font);
   }
*/

   final void checkValid(UndoHistory.EhMark ehm) {
      //trace("invalidateBack fvc " + fvc);
      //trace("invalidateBack chmark " + fvc.chmark);

      if (ehm.sameBack(chmark))
         if (chmark.getIndex() > ehm.getIndex())
            chmark.invalidate();
   }

   class Delayer implements Runnable {
      private int readin;
      private int needed;

      Delayer(int neededi) {
         readin = text.readIn();
         needed = neededi;
      }

      public void run() {
         delayerflag = true;
         try {
            int newReadin;
            do {
               newReadin = text.readIn();
               //trace("needed " + needed + " newReadin" + newReadin + " readin " + readin);
               trace("sleeping 200");
               Thread.sleep(200);
               if (newReadin > readin || text.donereading()) {
                  readin = newReadin;
                  op.redraw();
               }
            } while (!text.donereading() && newReadin <= needed);
         } catch (InterruptedException e) {
            trace("ignoring InterruptedException");
         }
         op.redraw();
         delayerflag = false;
      }
   }

   public final void needMoreText(int needed) {
      if (!delayerflag)
         new Thread(new Delayer(needed), "oldview delayer").start();
   }

   public final void updateTempMarkPos(Position evPos) {
      if (fileX != evPos.x || fileY != evPos.y)
         setMark(evPos);
      else
         clearMark();
   }

   final void setMark(Position markposi) {
      //trace("setMark");
      MovePos pos = pmark.getMark();
      if (pos != null) {
         if (markposi.equals(pos))
            return;
         pmark.clearMark(fileX, fileY);
      }
      pmark.setMark(markposi, fileX, fileY);
      op.changedpro(markposi.y, fileY);
   }

   final void clearMark() {
      //trace("clearMark");
      MovePos pos = pmark.getMark();
      pmark.clearMark(fileX, fileY);
      if (pos != null)
         op.changedpro(pos.y, fileY);
   }

   final MovePos getMark() {
      return pmark.getMark();
   }

   public static final class MarkInfo {

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

      public int endh(int tline) {
         return  (tline >= sy1 &&  tline <= sy2)
            ? (tline == sy2)
                   ? sx2
                   : Integer.MAX_VALUE
             : 0;
      }

      public int starth(int tline) {
         return (tline >= sy1 &&  tline <= sy2 && tline == sy1)
            ? sx1
            : 0;
      }

      public MovePos getMark() {
         return  markpos == null
            ? null
            : new MovePos(markpos);
      }

      void resetMark(EditContainer ev, int fileX, int fileY) {
         if (markpos != null) {
            if (!ev.containsNow(markpos.y))
               markpos.y = ev.readIn() - 1;
            if (markpos.x > ev.at(markpos.y).toString().length())
               markpos.x = ev.at(markpos.y).toString().length();
            markChange(fileX, fileY);
         }
      }

      void clearMark(int fileX, int fileY) {
         markpos = null;
         markChange(fileX, fileY);
      }

      void setMark(Position markposi, int fileX, int fileY) {
         if (markposi == null)
            markpos = null;
         else if (markpos == null)
            markpos = new MovePos(markposi);
         else
            markpos.set(markposi);
         markChange(fileX, fileY);
      }
   }

   @SuppressWarnings("fallthrough")

   final void cursoroff() {
      //trace("cursoroff cursoractive " + cursoractive + " cursoron " + cursoron +"");
      cursoractive = false;
      if (cursoron)
         blinkcursor();
   }

   final void cursoron() {
      //trace("cursoron cursoractive " + cursoractive + " cursoron " + cursoron +"");
      checkCursor = true;
      cursoractive = true;
      blinkcursor();
   }

   final int placeline(int lineno, float amount) {
      int row = getRows(amount);
      screenFirstLine();
      row =  lineno - screenFirstLine() - row;
      return screeny(row);
   }

}
