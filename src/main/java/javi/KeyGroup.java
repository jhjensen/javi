package javi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
//import static history.Tools.trace;

/**
 * Stores key-to-command mappings for modal editing.
 *
 * <p>A KeyGroup maps {@link JeyEvent} keystroke events to
 * {@link Rgroup.KeyBinding} command bindings. Separate KeyGroups
 * are used for movement keys and static/editing keys.</p>
 */
final class KeyGroup {
   private HashMap<JeyEvent, Rgroup.KeyBinding> bindingMap =
      new HashMap<JeyEvent, Rgroup.KeyBinding>(200);

   /** Stores the command name for each binding for documentation purposes. */
   private HashMap<JeyEvent, String> commandNames =
      new HashMap<JeyEvent, String>(200);

   private static Rgroup.KeyBinding getkb(String name, Object arg) {
      //trace("looking up " + name);
      Rgroup.KeyBinding kb = Rgroup.bindingLookup(name);
      return kb.proto(arg); // use default bind if arguments the same
   }

   void keybind(char c, String name, Object arg, int modifiers) {
      JeyEvent binding = new JeyEvent(modifiers, 0, c);
      //trace("keybind " + c + " name:" + name + " modifiers " + modifiers
      //    + " binding " + binding);
      if (null != bindingMap.put(binding, getkb(name, arg)))
         throw new RuntimeException("mapping identical keymaps: " + binding);
      commandNames.put(binding, name);
   }

   void keybind(char c, String name, Object arg) {
      JeyEvent binding = new JeyEvent(0, 0, c);
      //trace("keybind " + c + " name:" + name  + " binding " + binding
      //    + " binding hash " + binding.hashCode());
      if (null != bindingMap.put(binding, getkb(name, arg)))
         throw new RuntimeException("mapping identical keymaps: " + binding);
      commandNames.put(binding, name);
   }

   void keyactionbind(int c, String name, Object arg, int modifiers)  {
      JeyEvent binding = new JeyEvent(modifiers, c, JeyEvent.CHAR_UNDEFINED);

      //trace("keyactionbind " + c + " name:" + name + " binding " + binding);
      if (null != bindingMap.put(binding, getkb(name, arg)))
         throw new RuntimeException("mapping identical keymaps: " + binding);
      commandNames.put(binding, name);
   }

   Rgroup.KeyBinding get(JeyEvent e) {
      //trace("get " + e + " return " + bindingMap.get(e) + " event hash "
      //    + e.hashCode());
      return bindingMap.get(e);
   }

   /**
    * Get all key bindings as a list of formatted strings.
    *
    * @return list of "key -> command" strings
    */
   List<String> getBindingList() {
      List<String> result = new ArrayList<>();
      for (Map.Entry<JeyEvent, String> entry : commandNames.entrySet()) {
         String keyStr = formatKey(entry.getKey());
         result.add(String.format("  %-18s %s", keyStr, entry.getValue()));
      }
      result.sort(String::compareTo);
      return result;
   }

   /**
    * Format a JeyEvent as a readable key description.
    */
   private String formatKey(JeyEvent ev) {
      StringBuilder sb = new StringBuilder();
      int mods = ev.getModifiers();

      if ((mods & JeyEvent.CTRL_MASK) != 0) {
         sb.append("Ctrl-");
      }
      if ((mods & JeyEvent.SHIFT_MASK) != 0) {
         sb.append("Shift-");
      }
      if ((mods & JeyEvent.ALT_MASK) != 0) {
         sb.append("Alt-");
      }

      int keyCode = ev.getKeyCode();
      char keyChar = ev.getKeyChar();

      if (keyCode != 0) {
         sb.append(getKeyCodeName(keyCode));
      } else if (keyChar != JeyEvent.CHAR_UNDEFINED && keyChar >= 32) {
         sb.append(keyChar);
      } else if (keyChar < 32) {
         // Control character - display as Ctrl-X
         if (sb.length() == 0) {
            sb.append("Ctrl-");
         }
         sb.append((char) (keyChar + 'A' - 1));
      } else {
         sb.append("?");
      }

      return sb.toString();
   }

   /**
    * Get a human-readable name for a key code.
    */
   private String getKeyCodeName(int keyCode) {
      return switch (keyCode) {
         case JeyEvent.VK_F1 -> "F1";
         case JeyEvent.VK_F2 -> "F2";
         case JeyEvent.VK_F3 -> "F3";
         case JeyEvent.VK_F4 -> "F4";
         case JeyEvent.VK_F5 -> "F5";
         case JeyEvent.VK_F6 -> "F6";
         case JeyEvent.VK_F7 -> "F7";
         case JeyEvent.VK_F8 -> "F8";
         case JeyEvent.VK_F9 -> "F9";
         case JeyEvent.VK_F10 -> "F10";
         case JeyEvent.VK_F11 -> "F11";
         case JeyEvent.VK_F12 -> "F12";
         case JeyEvent.VK_LEFT -> "Left";
         case JeyEvent.VK_RIGHT -> "Right";
         case JeyEvent.VK_UP -> "Up";
         case JeyEvent.VK_DOWN -> "Down";
         case JeyEvent.VK_HOME -> "Home";
         case JeyEvent.VK_END -> "End";
         case JeyEvent.VK_PAGE_UP -> "PageUp";
         case JeyEvent.VK_PAGE_DOWN -> "PageDown";
         case JeyEvent.VK_INSERT -> "Insert";
         case JeyEvent.VK_DELETE -> "Delete";
         default -> "Key" + keyCode;
      };
   }
}
