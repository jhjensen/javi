package javi;

public class InputException extends Exception {
   private static final long serialVersionUID = 1;
   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";
   public InputException(String st) {
      super(st);
   }
   public InputException(String st, Throwable e) {
      super(st, e);
   }
}
