package javi;

abstract class EventHandler {
   abstract boolean hevent(Object e,FvContext fvc)
      throws InputException, InterruptedException ,java.io.IOException;
   Object result() {
      return null;
   }
}
