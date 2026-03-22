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

   // ---- Convenience binding methods (preferred API) ----

   /**
    * Bind a character key as a movement binding.
    * Preferred over calling KeyGroup.keybind() directly.
    */
   void bindMoveKey(char ch, String command, Object arg) {
      moveKeys.keybind(ch, command, arg);
   }

   /**
    * Bind a character key with modifiers as a movement binding.
    */
   void bindMoveKey(char ch, String command, Object arg, int modifiers) {
      moveKeys.keybind(ch, command, arg, modifiers);
   }

   /**
    * Bind an action key (VK_ code) as a movement binding.
    */
   void bindMoveAction(int keyCode, String command, Object arg, int modifiers) {
      moveKeys.keyactionbind(keyCode, command, arg, modifiers);
   }

   /**
    * Bind a character key as an edit binding.
    * Preferred over calling KeyGroup.keybind() directly.
    */
   void bindEditKey(char ch, String command, Object arg) {
      editKeys.keybind(ch, command, arg);
   }

   /**
    * Bind a character key with modifiers as an edit binding.
    */
   void bindEditKey(char ch, String command, Object arg, int modifiers) {
      editKeys.keybind(ch, command, arg, modifiers);
   }

   /**
    * Bind an action key (VK_ code) as an edit binding.
    */
   void bindEditAction(int keyCode, String command, Object arg, int modifiers) {
      editKeys.keyactionbind(keyCode, command, arg, modifiers);
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

   /**
    * Get all registered overlay keymaps (keymaps with a parent).
    * Used by KeyBindingPersistence for saving overlay user bindings.
    *
    * @return list of overlay KeyMaps
    */
   static java.util.List<KeyMap> getOverlayKeymaps() {
      java.util.List<KeyMap> overlays = new java.util.ArrayList<>();
      for (KeyMap km : registry.values()) {
         if (km.parent != null)
            overlays.add(km);
      }
      return overlays;
   }

   /**
    * Build a combined reverse binding map (command name to key descriptions)
    * from this keymap's move and edit groups.
    *
    * @return map of command name to formatted key strings
    */
   java.util.Map<String, java.util.List<String>> getReverseBindingMap() {
      java.util.Map<String, java.util.List<String>> result =
         new java.util.LinkedHashMap<>();
      result.putAll(moveKeys.getReverseBindingMap());
      for (var entry : editKeys.getReverseBindingMap().entrySet()) {
         result.merge(entry.getKey(), entry.getValue(), (a, b) -> {
            java.util.List<String> merged = new java.util.ArrayList<>(a);
            merged.addAll(b);
            return merged;
         });
      }
      return result;
   }

   // ---- Buffer-type keymap initialization ----

   /**
    * Create and register overlay keymaps for known buffer types.
    * Called once from {@link MapEvent#bindCommands()} after the normal
    * keymap is built.
    */
   static void initBufferKeyMaps(KeyMap normalMap) {
      // FileList overlay: Enter opens file at cursor instead of movelinestart
      boolean[] ff = {false, false};
      KeyMap filelistMap = createOverlay("filelist", normalMap);
      filelistMap.bindMoveKey((char) 13, "nextpos", ff);  // CR
      filelistMap.bindMoveKey((char) 10, "nextpos", ff);  // LF
      register(filelistMap);

      // Directory overlay: DirEdit buffer-specific bindings
      KeyMap dirMap = createOverlay("directory", normalMap);
      dirMap.bindEditKey('s', "diredit_sort", null);
      dirMap.bindEditKey('S', "dirmanager_toggle_searchpath", null);
      dirMap.bindEditKey('R', "diredit_refresh", null);
      dirMap.bindEditKey('q', "diredit_quit", null);
      dirMap.bindMoveKey((char) 13, "diredit_open", null);  // CR
      dirMap.bindMoveKey((char) 10, "diredit_open", null);  // LF
      dirMap.bindEditKey('-', "diredit_parent", null);
      dirMap.bindEditKey('.', "diredit_hidden", null);
      dirMap.bindEditKey('D', "diredit_delete", null);
      dirMap.bindEditKey('o', "diredit_create", null);
      dirMap.bindEditKey('O', "diredit_create", null);
      register(dirMap);

      // Shell overlay: shell-specific bindings when viewing a Vt100 buffer
      KeyMap shellMap = createOverlay("shell", normalMap);
      shellMap.bindEditKey('i', "vt", null);           // enter passthrough
      shellMap.bindEditAction(JeyEvent.VK_INSERT,
         "vt", null, 0);                               // Insert key
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
      if (buffer instanceof DirEdit)
         return get("directory");
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
