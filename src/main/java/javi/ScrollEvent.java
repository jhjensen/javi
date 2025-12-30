package javi;

public final class ScrollEvent extends EventQueue.IEvent {

   private int amount;
   private boolean horizontal;

   public ScrollEvent(int amt, boolean horiz) {
      amount = amt;
      horizontal = horiz;
   }

   public void execute() {
      FvContext fvc = FvContext.getCurrFvc();
      if  (horizontal)
         fvc.cursorx(amount);
      else
         fvc.cursory(amount);
   }
}
