package javi;

public class ExitException extends InputException {
   private static final long serialVersionUID = 1;
   public ExitException() {
      super("exiting java");
   }
   ExitException(Throwable e) {
      super("exiting java", e);
   }
}
