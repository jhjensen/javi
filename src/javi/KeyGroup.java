package javi;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;


class KeyGroup {
   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";
   private HashMap<String, KeyBinding>  map = new HashMap<String, KeyBinding>();
   private static String eventToString(KeyEvent ev) {
      char[] es  = new char[5];
      if (ev.isActionKey()) {
         es[0] = 'A';
         es[1] = (char) ev.getKeyCode();
         es[2] = (ev.getModifiers() & InputEvent.SHIFT_MASK) != 0 ? 'S' : 's';
      } else {
         es[0] = 'N';
         es[1] = ev.getKeyChar();
         switch(es[1]) {
            case ' ':
            case 8:
            case 26:
               es[2] = (ev.getModifiers()  & InputEvent.SHIFT_MASK) != 0
                   ? 'S'
                   : 's';
               break;
            default:
               es[2] = 'D'; // dont care about shift on non action keys
         }
      }

      es[3] = (ev.getModifiers() & InputEvent.CTRL_MASK) != 0 ? 'C' : 'c';
      es[4] = (ev.getModifiers() & InputEvent.ALT_MASK) != 0 ? 'A' : 'a';

      return new String(es);
   }
   /*
   private static KeyEvent stringToEvent(String s,Component c) throws
         inputexception {
      return new KeyEvent(c,KeyEvent.KEY_PRESSED,0,
               (s.charAt(2) == 's' ? 0 : KeyEvent.SHIFT_MASK)
             | (s.charAt(3) == 'c' ? 0 : KeyEvent.CTRL_MASK)
             | (s.charAt(4) == 'a' ? 0 : KeyEvent.ALT_MASK),
           s.charAt(0) == 'N' ? 0 :  s.charAt(1),
           s.charAt(0) == 'A' ? 0 :  s.charAt(1)
         );
   }
   */
   private String bindtostring(boolean isAction, int code, int modifiers) {
      char[] es  = new char[5];
      if (isAction) {
         es[0] = 'A';
         es[2] = (modifiers & InputEvent.SHIFT_MASK) != 0 ? 'S' : 's';
      } else {
         es[0] = 'N';
         switch(code) {
            case ' ':
            case 8:
            case 26:
               es[2] = (modifiers & InputEvent.SHIFT_MASK) != 0 ? 'S' : 's';
               break;
            default:
               es[2] = 'D'; // dont care about shift on non action keys
         }
      }

      es[1] = (char) code;
      es[3] = (modifiers & InputEvent.CTRL_MASK) != 0 ? 'C' : 'c';
     //trace("modifiers " + modifiers  + " ctrl = " + KeyEvent.CTRL_MASK + " es[3]= " + es[3]);
      es[4] = (modifiers & InputEvent.ALT_MASK) != 0 ? 'A' : 'a';
      return new String(es);
   }

   private static KeyBinding getkb(String name, Object arg) {
      //trace("looking up " + name);
      KeyBinding kb = Rgroup.bindingLookup(name);
      return (kb.arg != arg) // use default bind if arguments the same
             ? new KeyBinding(kb.rg, arg, kb.index)
             : kb;
   }

   void keybind(char c, String name, Object arg, int modifiers) {
      String binding = bindtostring(false, c, modifiers);
      //trace("keybind " + c + " name:" + name + " modifiers " + modifiers + " binding " + binding);
      if (null != map.put(binding, getkb(name, arg)))
         throw new RuntimeException("mapping identical keymaps: " + binding);
   }

   void keybind(char c, String name, Object arg) {
      String binding = bindtostring(false, c, 0);
      //trace("keybind " + c + " name:" + name  + " binding " + binding );
      if (null != map.put(binding, getkb(name, arg)))
         throw new RuntimeException("mapping identical keymaps: " + binding);
   }

   void keyactionbind(int c, String name, Object arg, int modifiers)  {
      String binding = bindtostring(true, c, modifiers);
//  trace("keyactionbind " + c + " name:" + name + " binding " + binding);
      if (null != map.put(binding, getkb(name, arg)))
         throw new RuntimeException("mapping identical keymaps: " + binding);
   }

   KeyBinding get(KeyEvent e) {
      //trace(e + " \n string = " + eventToString(e));
      return map.get(eventToString(e));
   }
   private static void trace(String str) {
      Tools.trace(str, 1);
   }
}
