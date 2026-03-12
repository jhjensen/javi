package javi;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named, layered set of key bindings for modal editing.
 *
 * <p>KeyMap encapsulates two {@link KeyGroup}s (movement and editing) and
 * supports parent-chain fallback: a buffer-specific KeyMap can overlay the
 * default mode-based KeyMap so that overridden keys resolve locally while
 * everything else falls through to the parent.</p>
 *
 * <h2>Built-in keymaps</h2>
 * <ul>
 *   <li>{@code "normal"} – default vi normal mode (move + edit)</li>
 *   <li>{@code "insert"} – insert mode (managed by InsertBuffer)</li>
 * </ul>
 *
 * <h2>Layering</h2>
 * <pre>
 *   buffer-specific keymap  (e.g. "directory", "filelist", "shell")
 *          ↓ parent
 *   mode-based keymap       (e.g. "normal")
 * </pre>
 *
 * @see KeyGroup
 * @see MapEvent
 */
final class KeyMap {

   private static final Map<String, KeyMap> registry = new LinkedHashMap<>();

   private final String name;
   private final KeyGroup moveKeys;
   private final KeyGroup editKeys;
   private final KeyMap parent; // null for root keymaps

   KeyMap(String name, KeyGroup moveKeys, KeyGroup editKeys, KeyMap parent) {
      this.name = name;
      this.moveKeys = moveKeys;
      this.editKeys = editKeys;
      this.parent = parent;
   }

   KeyMap(String name, KeyGroup moveKeys, KeyGroup editKeys) {
      this(name, moveKeys, editKeys, null);
   }

   String getName() {
      return name;
   }

   KeyMap getParent() {
      return parent;
   }

   // ---- Lookup with parent-chain fallback ----

   /**
    * Look up a movement binding, falling through to parent if not found locally.
    */
   Rgroup.KeyBinding lookupMove(JeyEvent key) {
      Rgroup.KeyBinding binding = moveKeys.get(key);
      if (binding != null)
         return binding;
      return (parent != null) ? parent.lookupMove(key) : null;
   }

   /**
    * Look up an edit/static binding, falling through to parent if not found.
    */
   Rgroup.KeyBinding lookupEdit(JeyEvent key) {
      Rgroup.KeyBinding binding = editKeys.get(key);
      if (binding != null)
         return binding;
      return (parent != null) ? parent.lookupEdit(key) : null;
   }

   // ---- Runtime binding modification ----

   /**
    * Add or replace a movement key binding.
    */
   void addMoveBinding(JeyEvent key, String commandName, Object arg) {
      moveKeys.bind(key, commandName, arg);
   }

   /**
    * Add or replace an edit key binding.
    */
   void addEditBinding(JeyEvent key, String commandName, Object arg) {
      editKeys.bind(key, commandName, arg);
   }

   /**
    * Remove a movement key binding from this layer only.
    */
   boolean removeMoveBinding(JeyEvent key) {
      return moveKeys.unbind(key);
   }

   /**
    * Remove an edit key binding from this layer only.
    */
   boolean removeEditBinding(JeyEvent key) {
      return editKeys.unbind(key);
   }

   // ---- Access to underlying KeyGroups ----

   KeyGroup getMoveKeys() {
      return moveKeys;
   }

   KeyGroup getEditKeys() {
      return editKeys;
   }

   // ---- Registry of named keymaps ----

   /**
    * Register a keymap so it can be looked up by name.
    */
   static void register(KeyMap km) {
      registry.put(km.name, km);
   }

   /**
    * Look up a registered keymap by name.
    *
    * @return the KeyMap, or null if not found
    */
   static KeyMap get(String name) {
      return registry.get(name);
   }

   /**
    * Create a new child keymap that overlays the given parent.
    * The child starts with empty KeyGroups; only overridden bindings
    * need to be added.
    */
   static KeyMap createOverlay(String name, KeyMap parentMap) {
      return new KeyMap(name,
         new KeyGroup(name + "-move"),
         new KeyGroup(name + "-edit"),
         parentMap);
   }

   /**
    * Get all registered keymap names.
    */
   static java.util.Set<String> registeredNames() {
      return java.util.Collections.unmodifiableSet(registry.keySet());
   }

   // ---- Buffer-type keymap initialization ----

   /**
    * Create and register overlay keymaps for known buffer types.
    * Called once from {@link MapEvent#bindCommands()} after the normal
    * keymap is built.
    */
   static void initBufferKeyMaps(KeyMap normalMap) {
      // FileList overlay: Enter opens file at cursor instead of movelinestart
      KeyMap filelistMap = createOverlay("filelist", normalMap);
      boolean[] ff = {false, false};
      JeyEvent enterCR = new JeyEvent(0, 0, (char) 13); // ^M / CR
      JeyEvent enterLF = new JeyEvent(0, 0, (char) 10); // ^J / LF
      filelistMap.addMoveBinding(enterCR, "nextpos", ff);
      filelistMap.addMoveBinding(enterLF, "nextpos", ff);
      register(filelistMap);

      // Shell overlay: extensibility point for shell-specific bindings
      KeyMap shellMap = createOverlay("shell", normalMap);
      register(shellMap);
   }

   /**
    * Resolve the appropriate buffer-type keymap for a given buffer.
    * Returns null if the buffer uses the default normal keymap.
    */
   @SuppressWarnings("rawtypes")
   static KeyMap resolveForBuffer(TextEdit buffer) {
      if (buffer instanceof FileList)
         return get("filelist");
      if (buffer instanceof Vt100)
         return get("shell");
      return null;
   }

   @Override
   public String toString() {
      return "KeyMap[" + name
         + (parent != null ? " -> " + parent.name : "") + "]";
   }
}
