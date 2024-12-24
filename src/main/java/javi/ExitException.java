package javi;

public final class ExitException extends InputException {
   public ExitException() {
      super("exiting java");
   }

   ExitException(Throwable e) {
      super("exiting java", e);
   }
}
