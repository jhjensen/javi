package javi;

public abstract class ChangeOpt {

   public enum Opcode { NOOP, INSERT, CHANGE,
      DELETE, REDRAW , MSCREEN, BLINKCURSOR
   }

   protected abstract int getSaveAmount();
   protected abstract int getSaveStart();
   protected abstract Opcode resetOp();
   abstract void redraw();
   abstract void blink();
   abstract boolean insert(int start, int amount);
   abstract boolean lineChanged(int index);
   abstract void cursorChange(int xChange, int yChange);
   abstract boolean changedpro(int index1, int index2);
   abstract boolean delete(int start, int amount);
   abstract void mscreen(int amount, int limit);
}
