package javi;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for {@link MapEvent} and {@link KeyMap} binding
 * mechanics — exercises KeyMap creation, overlay keymaps,
 * KeyGroup binding/lookup, and getActiveKeyMap resolution.
 *
 * <p>These tests do NOT call {@code MapEvent.bindCommands()} (which
 * requires AWT commands). Instead they directly create and exercise
 * KeyMap/KeyGroup instances to cover the binding infrastructure.</p>
 */
class MapEventBindingsJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void lock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void unlock() {
      EventQueue.biglock2.unlock();
   }

   // ── KeyMap creation and naming ─────────────────────────────

   @Test
   void keymapNameIsPreserved() {
      KeyGroup move = new KeyGroup("test-move");
      KeyGroup edit = new KeyGroup("test-edit");
      KeyMap km = new KeyMap("testmap", move, edit);
      assertEquals("testmap", km.getName());
   }

   @Test
   void keymapGetMoveAndEditKeys() {
      KeyGroup move = new KeyGroup("t-move");
      KeyGroup edit = new KeyGroup("t-edit");
      KeyMap km = new KeyMap("tmap", move, edit);
      assertEquals(move, km.getMoveKeys());
      assertEquals(edit, km.getEditKeys());
   }

   @Test
   void keymapToString() {
      KeyGroup move = new KeyGroup("m");
      KeyGroup edit = new KeyGroup("e");
      KeyMap km = new KeyMap("mymap", move, edit);
      String s = km.toString();
      assertTrue(s.contains("mymap"),
         "toString should contain keymap name");
   }

   // ── KeyMap overlay creation ────────────────────────────────

   @Test
   void overlayKeymapHasParent() {
      KeyGroup move = new KeyGroup("p-move");
      KeyGroup edit = new KeyGroup("p-edit");
      KeyMap parent = new KeyMap("parent", move, edit);

      KeyMap overlay = KeyMap.createOverlay("child", parent);
      assertNotNull(overlay);
      assertEquals("child", overlay.getName());
      String s = overlay.toString();
      assertTrue(s.contains("parent"),
         "overlay toString should reference parent");
   }

   // ── KeyGroup binding and lookup ────────────────────────────

   @Test
   void bindAndLookupCharKey() {
      KeyGroup kg = new KeyGroup("bind-test");
      // "movechar" is registered by MoveGroup.init()
      JeyEvent ev = new JeyEvent(0, 0, 'h');
      kg.bind(ev, "movechar", null);

      Rgroup.KeyBinding kb = kg.get(ev);
      assertNotNull(kb, "should find binding for 'h'");
   }

   @Test
   void lookupUnboundReturnsNull() {
      KeyGroup kg = new KeyGroup("bind-test2");
      JeyEvent ev = new JeyEvent(0, 0, 'z');
      assertNull(kg.get(ev),
         "unbound key should return null");
   }

   @Test
   void bindMultipleKeysAndLookup() {
      KeyGroup kg = new KeyGroup("bind-multi");
      JeyEvent evH = new JeyEvent(0, 0, 'h');
      JeyEvent evL = new JeyEvent(0, 0, 'l');
      kg.bind(evH, "movechar", Boolean.FALSE);
      kg.bind(evL, "movechar", Boolean.TRUE);

      assertNotNull(kg.get(evH));
      assertNotNull(kg.get(evL));
   }

   @Test
   void bindWithModifiers() {
      KeyGroup kg = new KeyGroup("mod-test");
      JeyEvent ev = new JeyEvent(JeyEvent.CTRL_MASK, 0, (char) 6);
      kg.bind(ev, "movechar", null);

      assertNotNull(kg.get(ev));
      // Without modifier shouldn't match
      JeyEvent noMod = new JeyEvent(0, 0, (char) 6);
      assertNull(kg.get(noMod));
   }

   @Test
   void getBindingListNotEmpty() {
      KeyGroup kg = new KeyGroup("list-test");
      JeyEvent ev = new JeyEvent(0, 0, 'j');
      kg.bind(ev, "moveline", Boolean.TRUE);

      List<String> bindings = kg.getBindingList();
      assertNotNull(bindings);
      assertFalse(bindings.isEmpty());
      assertTrue(bindings.get(0).contains("moveline"),
         "binding list should mention command name");
   }

   @Test
   void getUserBindingSpecsEmpty() {
      KeyGroup kg = new KeyGroup("specs-test");
      List<String> specs = kg.getUserBindingSpecs();
      assertNotNull(specs);
      assertTrue(specs.isEmpty(),
         "no user bindings should be empty");
   }

   @Test
   void getUserBindingSpecsAfterBind() {
      KeyGroup kg = new KeyGroup("specs-test2");
      JeyEvent ev = new JeyEvent(
         JeyEvent.CTRL_MASK, 0, (char) 1);
      kg.bind(ev, "movechar", null);

      List<String> specs = kg.getUserBindingSpecs();
      assertEquals(1, specs.size());
   }

   // ── KeyMap bind methods (move/edit) ────────────────────────

   @Test
   void keymapBindMoveKeyAndLookup() {
      KeyGroup move = new KeyGroup("km-move");
      KeyGroup edit = new KeyGroup("km-edit");
      KeyMap km = new KeyMap("km-test", move, edit);

      km.bindMoveKey('h', "movechar", Boolean.FALSE);

      JeyEvent ev = new JeyEvent(0, 0, 'h');
      assertNotNull(km.lookupMove(ev));
      assertNull(km.lookupEdit(ev),
         "move key should not be in edit group");
   }

   @Test
   void keymapBindEditKeyAndLookup() {
      KeyGroup move = new KeyGroup("km-move2");
      KeyGroup edit = new KeyGroup("km-edit2");
      KeyMap km = new KeyMap("km-test2", move, edit);

      km.bindEditKey('u', "undo", null);

      JeyEvent ev = new JeyEvent(0, 0, 'u');
      assertNotNull(km.lookupEdit(ev));
      assertNull(km.lookupMove(ev),
         "edit key should not be in move group");
   }

   @Test
   void keymapBindActionKey() {
      KeyGroup move = new KeyGroup("km-move3");
      KeyGroup edit = new KeyGroup("km-edit3");
      KeyMap km = new KeyMap("km-test3", move, edit);

      km.bindMoveAction(JeyEvent.VK_PAGE_UP, "movechar",
         Boolean.FALSE, 0);

      JeyEvent ev = new JeyEvent(0, JeyEvent.VK_PAGE_UP,
         JeyEvent.CHAR_UNDEFINED);
      assertNotNull(km.lookupMove(ev));
   }

   @Test
   void keymapBindEditAction() {
      KeyGroup move = new KeyGroup("km-move4");
      KeyGroup edit = new KeyGroup("km-edit4");
      KeyMap km = new KeyMap("km-test4", move, edit);

      km.bindEditAction(JeyEvent.VK_F7, "redo", null, 0);

      JeyEvent ev = new JeyEvent(0, JeyEvent.VK_F7,
         JeyEvent.CHAR_UNDEFINED);
      assertNotNull(km.lookupEdit(ev));
   }

   // ── KeyMap.get and register ────────────────────────────────

   @Test
   void keymapGetReturnsRegistered() {
      KeyGroup move = new KeyGroup("reg-move");
      KeyGroup edit = new KeyGroup("reg-edit");
      KeyMap km = new KeyMap("test_reg_km", move, edit);
      KeyMap.register(km);

      KeyMap found = KeyMap.get("test_reg_km");
      assertNotNull(found);
      assertEquals("test_reg_km", found.getName());
   }

   @Test
   void keymapGetReturnsNullForUnknown() {
      assertNull(KeyMap.get("doesnotexist_97531"));
   }

   // ── MapEvent.getKeyGroup ───────────────────────────────────

   @Test
   void getKeyGroupNullWhenNoNormalKeyMap() {
      // If normalKeyMap is null, getKeyGroup returns null
      if (MapEvent.getNormalKeyMap() == null) {
         assertNull(MapEvent.getKeyGroup("move"));
         assertNull(MapEvent.getKeyGroup("edit"));
      }
   }

   @Test
   void getKeyGroupBadGroupNameReturnsNull() {
      KeyGroup kg = MapEvent.getKeyGroup("nonexistent.move");
      assertNull(kg);
   }

   // ── MapEvent.getAllBindings ─────────────────────────────────

   @Test
   void getAllBindingsReturnsNonEmptyList() {
      List<String> bindings = MapEvent.getAllBindings();
      assertNotNull(bindings);
      assertFalse(bindings.isEmpty());
   }

   // ── Overlay keymap lookup falls through to parent ──────────

   @Test
   void overlayLookupFallsToParent() {
      KeyGroup parentMove = new KeyGroup("pm");
      KeyGroup parentEdit = new KeyGroup("pe");
      KeyMap parent = new KeyMap("p", parentMove, parentEdit);
      parent.bindMoveKey('j', "moveline", Boolean.TRUE);

      KeyMap overlay = KeyMap.createOverlay("ov", parent);
      overlay.bindMoveKey((char) 13, "moveline", Boolean.TRUE);

      // 'j' not bound in overlay, should fall through to parent
      JeyEvent evJ = new JeyEvent(0, 0, 'j');
      assertNotNull(overlay.lookupMove(evJ),
         "overlay should fall through to parent for 'j'");

      // CR bound in overlay
      JeyEvent evCR = new JeyEvent(0, 0, (char) 13);
      assertNotNull(overlay.lookupMove(evCR),
         "overlay should find its own binding for CR");
   }

   // ── KeyMap.getOverlayKeymaps ───────────────────────────────

   @Test
   void getOverlayKeymapsReturnsList() {
      List<KeyMap> overlays = KeyMap.getOverlayKeymaps();
      assertNotNull(overlays);
   }
}
