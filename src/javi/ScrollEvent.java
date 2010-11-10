package javi;

public final class ScrollEvent extends EventQueue.IEvent {

   private int amount;

   public ScrollEvent(int amt) {
      amount = amt;
   }

   public void execute() {
      FvContext fvc = FvContext.getCurrFvc();
      fvc.cursory(amount);
   }
}
