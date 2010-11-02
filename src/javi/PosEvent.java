package javi;

public class PosEvent extends EventQueue.IEvent {

   private FvContext fvc;
   private Position pos;

   public PosEvent(FvContext fvci, Position posi) {
      fvc = fvci;
      pos = posi;
   }

   public final void execute() {
      if (fvc != FvContext.getCurrFvc()) {
         UI.setTitle(fvc.edvec.toString());
         fvc.setCurrView();
      }
      fvc.cursorabs(pos);
   }
}
