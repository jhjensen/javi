package javi;

class ExitEvent extends EventQueue.IEvent {
   void execute() throws ExitException {
      //trace("ExitEvent");
      throw new ExitException();
   }
}
