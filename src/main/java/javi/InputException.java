package javi;

public class InputException extends Exception {

   public InputException(String st) {
      super(st);
   }

   public InputException(String st, Throwable e) {
      super(st, e);
   }
}
