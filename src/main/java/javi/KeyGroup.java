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
   private final String name;
   private HashMap<JeyEvent, Rgroup.KeyBinding> bindingMap =
      new HashMap<JeyEvent, Rgroup.KeyBinding>(200);

   /** Stores the command name for each binding for documentation purposes. */
   private HashMap<JeyEvent, String> commandNames =
      new HashMap<JeyEvent, String>(200);

   /** Tracks keys modified at runtime via :mapkey (for persistence). */
   private final HashMap<JeyEvent, String> userBindings =
      new HashMap<>();

   KeyGroup(String name) {
      this.name = name;
   }

   KeyGroup() {
      this.name = null;
   }

   String getName() {
      return name;
   }

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
    * Add or replace a runtime binding (for :mapkey command).
    * Unlike keybind(), this allows overwriting existing bindings
    * and tracks the change as a user modification for persistence.
    */
   void bind(JeyEvent key, String commandName, Object arg) {
      Rgroup.KeyBinding kb = Rgroup.bindingLookup(commandName);
      bindingMap.put(key, kb.proto(arg));
      commandNames.put(key, commandName);
      userBindings.put(key, commandName);
   }

   /**
    * Remove a binding (for :unmap command).
    *
    * @return true if a binding was removed
    */
   boolean unbind(JeyEvent key) {
      commandNames.remove(key);
      userBindings.remove(key);
      return null != bindingMap.remove(key);
   }

   /**
    * Get the command name bound to a key, or null.
    */
   String getCommandName(JeyEvent key) {
      return commandNames.get(key);
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
    * Get user-modified bindings as serializable "key command" pairs.
    * Each entry is formatted as "keyspec command" suitable for
    * writing to a keybinding config file.
    *
    * @return list of user-modified bindings in persistence format
    */
   List<String> getUserBindingSpecs() {
      List<String> result = new ArrayList<>();
      for (Map.Entry<JeyEvent, String> entry : userBindings.entrySet()) {
         String keySpec = formatKeySpec(entry.getKey());
         result.add(keySpec + " " + entry.getValue());
      }
      result.sort(String::compareTo);
      return result;
   }

   /**
    * Check whether any user-modified bindings exist.
    */
   boolean hasUserBindings() {
      return !userBindings.isEmpty();
   }

   /**
    * Build a reverse map from command name to list of key descriptions.
    * Used by HelpSystem to annotate help text with bound keys.
    *
    * @return map of command name to formatted key strings
    */
   Map<String, List<String>> getReverseBindingMap() {
      Map<String, List<String>> result = new java.util.LinkedHashMap<>();
      for (Map.Entry<JeyEvent, String> entry : commandNames.entrySet()) {
         String cmdName = entry.getValue();
         String keyStr = formatKey(entry.getKey());
         result.computeIfAbsent(cmdName, k -> new ArrayList<>()).add(keyStr);
      }
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

      char keyChar = ev.getKeyChar();

      if (keyChar != JeyEvent.CHAR_UNDEFINED) {
         // Character-based event
         if (keyChar >= 32) {
            sb.append(keyChar);
         } else {
            // Control character - display as Ctrl-X
            if (sb.length() == 0) {
               sb.append("Ctrl-");
            }
            sb.append((char) (keyChar + 'A' - 1));
         }
      } else {
         // Action key event - use keyCode
         int keyCode = ev.getKeyCode();
         if (keyCode != 0) {
            sb.append(getKeyCodeName(keyCode));
         } else {
            sb.append("?");
         }
      }

      return sb.toString();
   }

   /**
    * Format a JeyEvent as a key specification that can be parsed by
    * MiscCommands.parseKeySpec(). Roundtrippable format:
    * single char, C-x for ctrl+char, or special names (F1-F12, etc.)
    */
   String formatKeySpec(JeyEvent ev) {
      int mods = ev.getModifiers();
      char keyChar = ev.getKeyChar();

      // Character-based events (keyChar is not CHAR_UNDEFINED)
      if (keyChar != JeyEvent.CHAR_UNDEFINED) {
         if ((mods & JeyEvent.CTRL_MASK) != 0 && keyChar < 32) {
            return "C-" + (char) (keyChar + 'a' - 1);
         }
         if ((mods & JeyEvent.SHIFT_MASK) != 0) {
            return "S-" + keyChar;
         }
         if ((mods & JeyEvent.ALT_MASK) != 0) {
            return "A-" + keyChar;
         }
         if (keyChar >= 32) {
            return String.valueOf(keyChar);
         }
         return "?";
      }

      // Action key events (keyChar is CHAR_UNDEFINED, use keyCode)
      int keyCode = ev.getKeyCode();
      if (keyCode != 0) {
         return getKeyCodeName(keyCode);
      }
      return "?";
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
