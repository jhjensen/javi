package javi;

public final class MarkEvent extends EventQueue.IEvent {

   private Position pos;

   public MarkEvent(Position posi) {
      pos = posi;
   }

   public void execute() {
      FvContext fvc = FvContext.getCurrFvc();
      Position oldPos = fvc.getPosition("");
      fvc.cursorabs(pos);
      fvc.vi.setMark(oldPos);
      try {
         Rgroup.doCommand("markmode", 0, 1, 1, fvc, false);
      } catch (InterruptedException ex) {
         UI.popError("unexpected Interruption ", ex);
      } catch (java.io.IOException ex) {
         UI.popError("unexpected Interruption ", ex);
      } catch (InputException ex) {
         UI.popError("unexpected Interruption ", ex);
      }
   }
}
