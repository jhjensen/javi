package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
      TestInit.initCommands();
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
}
