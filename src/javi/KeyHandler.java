package javi;
abstract class KeyHandler {
   abstract boolean dispatchKeyEvent(JeyEvent ev);
   abstract void startDispatch(FvContext fvc);
}
