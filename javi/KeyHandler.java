package javi;
import java.awt.event.KeyEvent;
abstract class KeyHandler {
   abstract boolean dispatchKeyEvent(KeyEvent ev);
   abstract void startDispatch(FvContext fvc);
}
