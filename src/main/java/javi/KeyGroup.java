package javi;
import java.util.HashMap;
//import static history.Tools.trace;

final class KeyGroup {
   private HashMap<JeyEvent, Rgroup.KeyBinding> map =
      new HashMap<JeyEvent, Rgroup.KeyBinding>(200);

   private static Rgroup.KeyBinding getkb(String name, Object arg) {
      //trace("looking up " + name);
      Rgroup.KeyBinding kb = Rgroup.bindingLookup(name);
      return kb.proto(arg); // use default bind if arguments the same
   }

   void keybind(char c, String name, Object arg, int modifiers) {
      JeyEvent binding = new JeyEvent(modifiers, 0, c);
      //trace("keybind " + c + " name:" + name + " modifiers " + modifiers + " binding " + binding);
      if (null != map.put(binding, getkb(name, arg)))
         throw new RuntimeException("mapping identical keymaps: " + binding);
   }

   void keybind(char c, String name, Object arg) {
      JeyEvent binding = new JeyEvent(0, 0, c);
      //trace("keybind " + c + " name:" + name  + " binding " + binding + " binding hash " + binding.hashCode());
      if (null != map.put(binding, getkb(name, arg)))
         throw new RuntimeException("mapping identical keymaps: " + binding);
   }

   void keyactionbind(int c, String name, Object arg, int modifiers)  {
      JeyEvent binding = new JeyEvent(modifiers, c, JeyEvent.CHAR_UNDEFINED);

      //trace("keyactionbind " + c + " name:" + name + " binding " + binding);
      if (null != map.put(binding, getkb(name, arg)))
         throw new RuntimeException("mapping identical keymaps: " + binding);
   }

   Rgroup.KeyBinding get(JeyEvent e) {
      //trace("get " + e + " return " + map.get(e) jjjjjjjjjjjjjjjjjjjjjjjjjjjj+ " event hash " + e.hashCode());
      return map.get(e);
   }
}
