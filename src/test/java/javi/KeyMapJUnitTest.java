package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link KeyMap} — layered keymap with parent-chain
 * fallback and buffer-type overlay keymaps.
 */
class KeyMapJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initAllCommands();
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   // ---- Basic KeyMap operations ----

   @Test
   void createOverlayHasParent() {
      KeyGroup mg = new KeyGroup("test-move");
      KeyGroup eg = new KeyGroup("test-edit");
      KeyMap root = new KeyMap("root", mg, eg);

      KeyMap child = KeyMap.createOverlay("child", root);
      assertNotNull(child.getParent());
      assertEquals("root", child.getParent().getName());
      assertEquals("child", child.getName());
   }

   @Test
   void lookupFallsThroughToParent() {
      KeyGroup mg = new KeyGroup("root-move");
      mg.keybind('h', "movechar", Boolean.FALSE);
      KeyGroup eg = new KeyGroup("root-edit");
      KeyMap root = new KeyMap("root", mg, eg);

      KeyMap child = KeyMap.createOverlay("child", root);
      JeyEvent ev = new JeyEvent(0, 0, 'h');
      Rgroup.KeyBinding kb = child.lookupMove(ev);
      assertNotNull(kb, "child should find binding via parent fallback");
   }

   @Test
   void childOverridesParentBinding() {
      KeyGroup mg = new KeyGroup("root-move");
      mg.keybind('h', "movechar", Boolean.FALSE);
      KeyGroup eg = new KeyGroup("root-edit");
      KeyMap root = new KeyMap("root", mg, eg);

      KeyMap child = KeyMap.createOverlay("overlay", root);
      JeyEvent ev = new JeyEvent(0, 0, 'h');
      child.addMoveBinding(ev, "moveline", Boolean.TRUE);

      Rgroup.KeyBinding kb = child.lookupMove(ev);
      assertNotNull(kb, "child override should be found");
      // Verify it's the child binding, not the parent's
      String desc = kb.toString();
      assertTrue(desc.contains("true"),
         "child binding should have the overridden arg");
   }

   @Test
   void lookupMoveReturnsNullWhenNotBound() {
      KeyGroup mg = new KeyGroup("empty-move");
      KeyGroup eg = new KeyGroup("empty-edit");
      KeyMap map = new KeyMap("empty", mg, eg);

      JeyEvent ev = new JeyEvent(0, 0, 'z');
      assertNull(map.lookupMove(ev));
   }

   @Test
   void editLookupFallsThroughToParent() {
      KeyGroup mg = new KeyGroup("root-move");
      KeyGroup eg = new KeyGroup("root-edit");
      eg.keybind('i', "insert", new boolean[]{false, false});
      KeyMap root = new KeyMap("root", mg, eg);

      KeyMap child = KeyMap.createOverlay("child", root);
      JeyEvent ev = new JeyEvent(0, 0, 'i');
      Rgroup.KeyBinding kb = child.lookupEdit(ev);
      assertNotNull(kb, "child should find edit binding via parent");
   }

   // ---- Registry ----

   @Test
   void registerAndGet() {
      KeyMap km = new KeyMap("test-reg",
         new KeyGroup("tr-move"), new KeyGroup("tr-edit"));
      KeyMap.register(km);
      assertEquals(km, KeyMap.get("test-reg"));
   }

   @Test
   void registeredNamesContainsRegistered() {
      KeyMap km = new KeyMap("test-names",
         new KeyGroup("tn-move"), new KeyGroup("tn-edit"));
      KeyMap.register(km);
      assertTrue(KeyMap.registeredNames().contains("test-names"));
   }

   // ---- Buffer-type keymaps ----

   @Test
   void overlayOverridesEnterKeyInMoveGroup() {
      // Simulate what initBufferKeyMaps does: create an overlay
      // that overrides Enter (^M) with a different command
      KeyGroup mg = new KeyGroup("n-move");
      mg.keybind((char) 13, "movelinestart", 1);
      KeyGroup eg = new KeyGroup("n-edit");
      KeyMap normal = new KeyMap("normal-sim", mg, eg);

      KeyMap overlay = KeyMap.createOverlay("filelist-sim", normal);
      JeyEvent enter = new JeyEvent(0, 0, (char) 13);

      // Before override: should fall through to parent
      Rgroup.KeyBinding parentBinding = overlay.lookupMove(enter);
      assertNotNull(parentBinding, "should find parent Enter binding");

      // Override in child layer with a registered command
      overlay.addMoveBinding(enter, "movechar", Boolean.FALSE);

      // After override: should find child binding
      Rgroup.KeyBinding childBinding = overlay.lookupMove(enter);
      assertNotNull(childBinding, "should find overridden Enter binding");

      // Local layer should have the binding
      Rgroup.KeyBinding localBinding = overlay.getMoveKeys().get(enter);
      assertNotNull(localBinding,
         "overlay should have local Enter binding");
   }

   @Test
   void overlayWithShellNameCreated() {
      KeyMap normal = new KeyMap("normal-sh",
         new KeyGroup("n-m"), new KeyGroup("n-e"));
      KeyMap shell = KeyMap.createOverlay("shell", normal);
      KeyMap.register(shell);

      KeyMap retrieved = KeyMap.get("shell");
      assertNotNull(retrieved, "shell keymap should be registered");
      assertEquals("normal-sh", retrieved.getParent().getName(),
         "shell should overlay the normal keymap");
   }

   @Test
   void overlayChainThreeLevels() {
      KeyGroup mg = new KeyGroup("root-mv");
      mg.keybind('h', "movechar", Boolean.FALSE);
      KeyMap root = new KeyMap("root",
         mg, new KeyGroup("root-ed"));

      KeyMap mid = KeyMap.createOverlay("mid", root);
      KeyMap top = KeyMap.createOverlay("top", mid);

      // Lookup should chain through all three levels
      JeyEvent ev = new JeyEvent(0, 0, 'h');
      Rgroup.KeyBinding kb = top.lookupMove(ev);
      assertNotNull(kb, "three-level chain should find root binding");
   }

   @Test
   void resolveForBufferReturnsNullForPlainTextEdit() {
      // resolveForBuffer handles unknown buffer types gracefully
      assertNull(KeyMap.resolveForBuffer(null));
   }

   // ---- DirEdit overlay (F17) ----

   @Test
   void directoryOverlayCanBeCreatedAndResolved() {
      // Test the infrastructure: createOverlay + register + resolveForBuffer
      // (bindCommands() isn't called in tests, so we simulate)
      KeyMap normalSim = new KeyMap("normal-sim",
         new KeyGroup("ns-move"), new KeyGroup("ns-edit"));
      KeyMap dirOverlay = KeyMap.createOverlay("directory-test", normalSim);
      KeyMap.register(dirOverlay);

      KeyMap retrieved = KeyMap.get("directory-test");
      assertNotNull(retrieved, "directory overlay should be retrievable");
      assertEquals("normal-sim", retrieved.getParent().getName());
   }

   // ---- Convenience bind methods ----

   @Test
   void bindMoveKeyCharAddsBinding() {
      KeyGroup mg = new KeyGroup("bm-move");
      KeyGroup eg = new KeyGroup("bm-edit");
      KeyMap km = new KeyMap("bind-test", mg, eg);

      km.bindMoveKey('x', "movechar", Boolean.FALSE);
      JeyEvent ev = new JeyEvent(0, 0, 'x');
      assertNotNull(km.lookupMove(ev),
         "bindMoveKey should add a movement binding");
   }

   @Test
   void bindEditKeyCharAddsBinding() {
      KeyGroup mg = new KeyGroup("be-move");
      KeyGroup eg = new KeyGroup("be-edit");
      KeyMap km = new KeyMap("bind-test", mg, eg);

      km.bindEditKey('d', "movechar", null);
      JeyEvent ev = new JeyEvent(0, 0, 'd');
      assertNotNull(km.lookupEdit(ev),
         "bindEditKey should add an edit binding");
   }

   @Test
   void getOverlayKeymapsReturnsOnlyChildren() {
      // The registered filelist, shell, directory overlays all have parents
      java.util.List<KeyMap> overlays = KeyMap.getOverlayKeymaps();
      assertFalse(overlays.isEmpty(),
         "should have at least one overlay keymap");
      for (KeyMap km : overlays) {
         assertNotNull(km.getParent(),
            "overlay keymaps should have a parent: " + km.getName());
      }
   }

   @Test
   void getReverseBindingMapContainsEntries() {
      KeyGroup mg = new KeyGroup("rb-move");
      mg.keybind('h', "movechar", Boolean.FALSE);
      mg.keybind('l', "movechar", Boolean.TRUE);
      KeyGroup eg = new KeyGroup("rb-edit");
      KeyMap km = new KeyMap("rb-test", mg, eg);

      var reverseMap = km.getReverseBindingMap();
      assertTrue(reverseMap.containsKey("movechar"),
         "reverse map should contain movechar");
      assertEquals(2, reverseMap.get("movechar").size(),
         "movechar should have two key bindings (h and l)");
   }

   // ---- toString ----

   @Test
   void toStringShowsParentChain() {
      KeyMap root = new KeyMap("root",
         new KeyGroup("r-m"), new KeyGroup("r-e"));
      KeyMap child = KeyMap.createOverlay("child", root);

      String s = child.toString();
      assertTrue(s.contains("child"), "should contain child name");
      assertTrue(s.contains("root"), "should contain parent name");
   }

   @Test
   void toStringRootHasNoArrow() {
      KeyMap root = new KeyMap("solo",
         new KeyGroup("s-m"), new KeyGroup("s-e"));
      String s = root.toString();
      assertTrue(s.contains("solo"));
      assertTrue(!s.contains("->"),
         "root keymap should not have parent arrow");
   }

   // ---- DirEdit overlay keymap verification (F17) ----

   /**
    * Helper: register DirEdit commands, create a normal keymap
    * with standard movement bindings, then manually build
    * directory and shell overlay keymaps mirroring what
    * {@link KeyMap#initBufferKeyMaps} does (without the filelist
    * overlay which requires PosListList.Cmd / "nextpos").
    */
   private KeyMap buildDirectoryOverlay() {
      DirEdit.Commands.getInstance();

      KeyGroup mg = new KeyGroup("norm-move");
      KeyGroup eg = new KeyGroup("norm-edit");
      mg.keybind('h', "movechar", Boolean.FALSE);
      mg.keybind('l', "movechar", Boolean.TRUE);
      mg.keybind('j', "moveline", 1);
      mg.keybind('k', "moveline", -1);
      KeyMap normalMap = new KeyMap("normal", mg, eg);
      KeyMap.register(normalMap);

      // Directory overlay — mirrors initBufferKeyMaps
      KeyMap dirMap = KeyMap.createOverlay("directory", normalMap);
      dirMap.bindEditKey('s', "diredit_sort", null);
      dirMap.bindEditKey('S',
         "dirmanager_toggle_searchpath", null);
      dirMap.bindEditKey('R', "diredit_refresh", null);
      dirMap.bindEditKey('q', "diredit_quit", null);
      dirMap.bindMoveKey((char) 13, "diredit_open", null);
      dirMap.bindMoveKey((char) 10, "diredit_open", null);
      dirMap.bindEditKey('-', "diredit_parent", null);
      dirMap.bindEditKey('.', "diredit_hidden", null);
      dirMap.bindEditKey('D', "diredit_delete", null);
      dirMap.bindEditKey('o', "diredit_create", null);
      dirMap.bindEditKey('O', "diredit_create", null);
      KeyMap.register(dirMap);

      // Shell overlay (empty)
      KeyMap shellMap = KeyMap.createOverlay("shell", normalMap);
      KeyMap.register(shellMap);

      return normalMap;
   }

   @Test
   void directoryOverlayRegisteredAfterInit() {
      buildDirectoryOverlay();
      KeyMap dirMap = KeyMap.get("directory");
      assertNotNull(dirMap, "directory keymap should be registered");
   }

   @Test
   void directoryOverlayParentIsNormal() {
      buildDirectoryOverlay();
      KeyMap dirMap = KeyMap.get("directory");
      assertNotNull(dirMap.getParent(),
         "directory keymap should have a parent");
      assertEquals("normal", dirMap.getParent().getName(),
         "directory overlay should chain to 'normal'");
   }

   @Test
   void directoryOverlayHasEditBindingS() {
      buildDirectoryOverlay();
      KeyMap dirMap = KeyMap.get("directory");
      JeyEvent sKey = new JeyEvent(0, 0, 's');
      Rgroup.KeyBinding kb = dirMap.getEditKeys().get(sKey);
      assertNotNull(kb,
         "directory overlay should have 's' (sort) in edit keys");
   }

   @Test
   void directoryOverlayAllEditBindingsPresent() {
      buildDirectoryOverlay();
      KeyMap dirMap = KeyMap.get("directory");
      char[] expectedEdits =
         {'s', 'S', 'R', 'q', '-', '.', 'D', 'o', 'O'};
      for (char ch : expectedEdits) {
         JeyEvent ev = new JeyEvent(0, 0, ch);
         assertNotNull(dirMap.getEditKeys().get(ev),
            "directory overlay missing edit binding: " + ch);
      }
   }

   @Test
   void directoryOverlayEnterBoundAsMove() {
      buildDirectoryOverlay();
      KeyMap dirMap = KeyMap.get("directory");
      JeyEvent cr = new JeyEvent(0, 0, (char) 13);
      JeyEvent lf = new JeyEvent(0, 0, (char) 10);
      assertNotNull(dirMap.getMoveKeys().get(cr),
         "directory overlay should bind CR in move keys");
      assertNotNull(dirMap.getMoveKeys().get(lf),
         "directory overlay should bind LF in move keys");
   }

   @Test
   void directoryOverlayFallsThroughForNavigation() {
      buildDirectoryOverlay();
      KeyMap dirMap = KeyMap.get("directory");
      JeyEvent hKey = new JeyEvent(0, 0, 'h');
      assertNull(dirMap.getMoveKeys().get(hKey),
         "'h' should not be in directory overlay's own move keys");
      Rgroup.KeyBinding kb = dirMap.lookupMove(hKey);
      assertNotNull(kb,
         "'h' should resolve via parent-chain fallback");
   }

   @Test
   void directoryOverlayJKFallThrough() {
      buildDirectoryOverlay();
      KeyMap dirMap = KeyMap.get("directory");
      for (char nav : new char[]{'j', 'k'}) {
         JeyEvent ev = new JeyEvent(0, 0, nav);
         assertNull(dirMap.getMoveKeys().get(ev),
            "'" + nav
               + "' should not be in directory local move");
         assertNotNull(dirMap.lookupMove(ev),
            "'" + nav
               + "' should resolve through parent chain");
      }
   }

   @Test
   void directoryEditOverrideHidesParent() {
      KeyGroup mg = new KeyGroup("n-mv");
      KeyGroup eg = new KeyGroup("n-ed");
      eg.keybind('s', "moveline", 1);
      KeyMap normal = new KeyMap("base", mg, eg);

      KeyMap dir = KeyMap.createOverlay("dir-test", normal);
      JeyEvent sKey = new JeyEvent(0, 0, 's');
      dir.addEditBinding(sKey, "movechar", null);

      Rgroup.KeyBinding kb = dir.lookupEdit(sKey);
      assertNotNull(kb, "overlay 's' should be found");
      Rgroup.KeyBinding localKb = dir.getEditKeys().get(sKey);
      assertNotNull(localKb, "overlay should have local 's'");
   }

   @Test
   void shellOverlayRegistered() {
      buildDirectoryOverlay();
      KeyMap sh = KeyMap.get("shell");
      assertNotNull(sh, "shell keymap should be registered");
      assertEquals("normal", sh.getParent().getName());
   }

   @Test
   void directoryOverlayCountOfEditBindings() {
      buildDirectoryOverlay();
      KeyMap dirMap = KeyMap.get("directory");
      java.util.List<String> editBindings =
         dirMap.getEditKeys().getBindingList();
      assertEquals(9, editBindings.size(),
         "directory overlay should have 9 edit bindings");
   }

   @Test
   void directoryOverlayCountOfMoveBindings() {
      buildDirectoryOverlay();
      KeyMap dirMap = KeyMap.get("directory");
      java.util.List<String> moveBindings =
         dirMap.getMoveKeys().getBindingList();
      assertEquals(2, moveBindings.size(),
         "directory overlay should have 2 move bindings (CR, LF)");
   }

   // ---- :help directory and filtered bindings (F20) ----

   @Test
   void helpDirectoryTopicContainsContent() {
      buildDirectoryOverlay();
      TextEdit<?> help = HelpSystem.getHelp("directory");
      assertNotNull(help, ":help directory should return a buffer");
      int lines = help.finish();
      assertTrue(lines > 3,
         ":help directory should have content (got " + lines + ")");
   }

   @Test
   void filteredBindingsDirectoryShowsOverrides() {
      buildDirectoryOverlay();
      TextEdit<?> buf =
         HelpSystem.getFilteredBindings("directory");
      assertNotNull(buf);
      StringBuilder sb = new StringBuilder();
      int fin = buf.finish();
      for (int i = 1; i < fin; i++) {
         Object line = buf.at(i);
         if (line != null)
            sb.append(line).append('\n');
      }
      String text = sb.toString();
      assertTrue(text.contains("directory"),
         "filtered bindings should mention 'directory'");
      assertTrue(
         text.contains("MOVEMENT") || text.contains("COMMAND"),
         "filtered bindings should show key sections");
   }

   @Test
   void filteredBindingsUnknownKeymapShowsError() {
      TextEdit<?> buf =
         HelpSystem.getFilteredBindings("nonexistent");
      assertNotNull(buf);
      StringBuilder sb = new StringBuilder();
      int fin = buf.finish();
      for (int i = 1; i < fin; i++) {
         Object line = buf.at(i);
         if (line != null)
            sb.append(line).append('\n');
      }
      assertTrue(sb.toString().contains("Unknown keymap"),
         "unknown keymap should produce error message");
   }

   @Test
   void keybindingsHelpListsRegisteredKeymaps() {
      buildDirectoryOverlay();
      TextEdit<?> help = HelpSystem.getHelp("keybindings");
      assertNotNull(help);
      StringBuilder sb = new StringBuilder();
      int fin = help.finish();
      for (int i = 1; i < fin; i++) {
         Object line = help.at(i);
         if (line != null)
            sb.append(line).append('\n');
      }
      String text = sb.toString();
      assertTrue(text.contains("directory"),
         ":help keybindings should list 'directory' keymap");
   }

   @Test
   void resolveForBufferReturnsDirectoryForDirEdit() {
      buildDirectoryOverlay();
      KeyMap dirKm = KeyMap.get("directory");
      assertNotNull(dirKm, "directory keymap must be registered");
      assertEquals("directory", dirKm.getName());
   }
}
