package javi;

public class CommandEvent extends EventQueue.IEvent {

   private String command;

   public CommandEvent(String cmd) {
      command = cmd;
   }

   public final void execute() {
      FvContext fvc = FvContext.getCurrFvc();
      Command.command(command, fvc, null);
   }
}
