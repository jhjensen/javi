package javi;

public class ExitEvent extends EventQueue.IEvent {
   public final void execute() throws ExitException {
      //trace("ExitEvent");
      throw new ExitException();
   }
}
