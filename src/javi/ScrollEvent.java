package javi;

public class ScrollEvent extends EventQueue.IEvent {

   private int amount;

   public ScrollEvent(int amt) {
      amount = amt;
   }

   public final void execute() {
      FvContext fvc = FvContext.getCurrFvc();
      fvc.cursory(amount);
   }
}
