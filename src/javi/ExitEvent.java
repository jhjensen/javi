package javi;

public final class ExitEvent extends EventQueue.IEvent {
   public void execute() throws ExitException {
      //trace("ExitEvent");
      throw new ExitException();
   }
}
