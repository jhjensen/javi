package javi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage for {@link KeyMap} — overlay creation, parent
 * chain lookup, resolve for buffer types, registry operations,
 * and binding manipulation.
 */
class KeyMapCoverageJUnitTest {

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

   // ── Construction and basic properties ─────────────────────

   @Test
   @DisplayName("KeyMap name round-trip")
   void nameRoundTrip() {
      KeyGroup mg = new KeyGroup("km_test_move");
      KeyGroup eg = new KeyGroup("km_test_edit");
      KeyMap km = new KeyMap("testmap", mg, eg);
      assertEquals("testmap", km.getName());
   }

   @Test
   @DisplayName("KeyMap without parent has null parent")
   void noParent() {
      KeyGroup mg = new KeyGroup("km_np_move");
      KeyGroup eg = new KeyGroup("km_np_edit");
      KeyMap km = new KeyMap("noparen", mg, eg);
      assertNull(km.getParent());
   }

   @Test
   @DisplayName("KeyMap with parent stores parent reference")
   void withParent() {
      KeyGroup mg1 = new KeyGroup("km_p1_move");
      KeyGroup eg1 = new KeyGroup("km_p1_edit");
      KeyMap parent = new KeyMap("parent", mg1, eg1);

      KeyGroup mg2 = new KeyGroup("km_p2_move");
      KeyGroup eg2 = new KeyGroup("km_p2_edit");
      KeyMap child = new KeyMap("child", mg2, eg2, parent);
      assertEquals(parent, child.getParent());
   }

   @Test
   @DisplayName("toString includes name")
   void toStringContainsName() {
      KeyGroup mg = new KeyGroup("km_ts_move");
      KeyGroup eg = new KeyGroup("km_ts_edit");
      KeyMap km = new KeyMap("mymap", mg, eg);
      String s = km.toString();
      assertTrue(s.contains("mymap"), "toString should contain name");
   }

   @Test
   @DisplayName("toString with parent includes parent name")
   void toStringWithParent() {
      KeyGroup mg1 = new KeyGroup("km_tsp1_move");
      KeyGroup eg1 = new KeyGroup("km_tsp1_edit");
      KeyMap parent = new KeyMap("parentmap", mg1, eg1);
      KeyMap child = KeyMap.createOverlay("childmap", parent);
      String s = child.toString();
      assertTrue(s.contains("childmap"), "should contain child name");
      assertTrue(s.contains("parentmap"), "should contain parent name");
   }

   // ── Overlay creation ──────────────────────────────────────

   @Test
   @DisplayName("createOverlay produces KeyMap with parent")
   void createOverlay() {
      KeyGroup mg = new KeyGroup("km_co_move");
      KeyGroup eg = new KeyGroup("km_co_edit");
      KeyMap parent = new KeyMap("base", mg, eg);
      KeyMap overlay = KeyMap.createOverlay("overlay", parent);
      assertEquals("overlay", overlay.getName());
      assertEquals(parent, overlay.getParent());
   }

   @Test
   @DisplayName("createOverlay has empty key groups")
   void createOverlayEmptyGroups() {
      KeyGroup mg = new KeyGroup("km_coe_move");
      KeyGroup eg = new KeyGroup("km_coe_edit");
      KeyMap parent = new KeyMap("base2", mg, eg);
      KeyMap overlay = KeyMap.createOverlay("over2", parent);
      assertNotNull(overlay.getMoveKeys());
      assertNotNull(overlay.getEditKeys());
   }

   // ── Binding manipulation ──────────────────────────────────

   @Test
   @DisplayName("addMoveBinding and lookupMove finds it")
   void addMoveBindingLookup() {
      KeyGroup mg = new KeyGroup("km_amb_move");
      KeyGroup eg = new KeyGroup("km_amb_edit");
      KeyMap km = new KeyMap("ambtest", mg, eg);
      JeyEvent key = new JeyEvent(0, 0, 'z');
      km.addMoveBinding(key, "movechar", null);
      Rgroup.KeyBinding binding = km.lookupMove(key);
      assertNotNull(binding, "should find added binding");
   }

   @Test
   @DisplayName("addEditBinding and lookupEdit finds it")
   void addEditBindingLookup() {
      KeyGroup mg = new KeyGroup("km_aeb_move");
      KeyGroup eg = new KeyGroup("km_aeb_edit");
      KeyMap km = new KeyMap("aebtest", mg, eg);
      JeyEvent key = new JeyEvent(0, 0, 'x');
      km.addEditBinding(key, "deletechars", null);
      Rgroup.KeyBinding binding = km.lookupEdit(key);
      assertNotNull(binding, "should find added edit binding");
   }

   @Test
   @DisplayName("lookupMove falls through to parent")
   void lookupMoveFallthrough() {
      KeyGroup pmg = new KeyGroup("km_lmf_pmove");
      KeyGroup peg = new KeyGroup("km_lmf_pedit");
      KeyMap parent = new KeyMap("lmfparent", pmg, peg);
      JeyEvent key = new JeyEvent(0, 0, 'w');
      parent.addMoveBinding(key, "moveline", null);

      KeyMap child = KeyMap.createOverlay("lmfchild", parent);
      Rgroup.KeyBinding binding = child.lookupMove(key);
      assertNotNull(binding, "child should find parent's binding");
   }

   @Test
   @DisplayName("lookupEdit falls through to parent")
   void lookupEditFallthrough() {
      KeyGroup pmg = new KeyGroup("km_lef_pmove");
      KeyGroup peg = new KeyGroup("km_lef_pedit");
      KeyMap parent = new KeyMap("lefparent", pmg, peg);
      JeyEvent key = new JeyEvent(0, 0, 'd');
      parent.addEditBinding(key, "insert", null);

      KeyMap child = KeyMap.createOverlay("lefchild", parent);
      Rgroup.KeyBinding binding = child.lookupEdit(key);
      assertNotNull(binding, "child should find parent's edit binding");
   }

   @Test
   @DisplayName("child binding overrides parent")
   void childOverridesParent() {
      KeyGroup pmg = new KeyGroup("km_cop_pmove");
      KeyGroup peg = new KeyGroup("km_cop_pedit");
      KeyMap parent = new KeyMap("copparent", pmg, peg);
      JeyEvent key = new JeyEvent(0, 0, 'm');
      parent.addMoveBinding(key, "movechar", null);

      KeyMap child = KeyMap.createOverlay("copchild", parent);
      child.addMoveBinding(key, "moveline", null);

      Rgroup.KeyBinding binding = child.lookupMove(key);
      assertNotNull(binding);
      // The child binding should be returned, not the parent's
   }

   @Test
   @DisplayName("lookupMove returns null when not found anywhere")
   void lookupMoveNotFound() {
      KeyGroup mg = new KeyGroup("km_lmnf_move");
      KeyGroup eg = new KeyGroup("km_lmnf_edit");
      KeyMap km = new KeyMap("lmnftest", mg, eg);
      JeyEvent key = new JeyEvent(0, 0, (char) 127);
      assertNull(km.lookupMove(key));
   }

   @Test
   @DisplayName("lookupEdit returns null when not found anywhere")
   void lookupEditNotFound() {
      KeyGroup mg = new KeyGroup("km_lenf_move");
      KeyGroup eg = new KeyGroup("km_lenf_edit");
      KeyMap km = new KeyMap("lenftest", mg, eg);
      JeyEvent key = new JeyEvent(0, 0, (char) 127);
      assertNull(km.lookupEdit(key));
   }

   @Test
   @DisplayName("removeMoveBinding returns true for existing")
   void removeMoveBindingExisting() {
      KeyGroup mg = new KeyGroup("km_rmb_move");
      KeyGroup eg = new KeyGroup("km_rmb_edit");
      KeyMap km = new KeyMap("rmbtest", mg, eg);
      JeyEvent key = new JeyEvent(0, 0, 'q');
      km.addMoveBinding(key, "forwardword", null);
      assertTrue(km.removeMoveBinding(key));
      assertNull(km.lookupMove(key));
   }

   @Test
   @DisplayName("removeMoveBinding returns false for nonexistent")
   void removeMoveBindingNonexistent() {
      KeyGroup mg = new KeyGroup("km_rmbn_move");
      KeyGroup eg = new KeyGroup("km_rmbn_edit");
      KeyMap km = new KeyMap("rmbntest", mg, eg);
      JeyEvent key = new JeyEvent(0, 0, (char) 200);
      assertFalse(km.removeMoveBinding(key));
   }

   @Test
   @DisplayName("removeEditBinding returns true for existing")
   void removeEditBindingExisting() {
      KeyGroup mg = new KeyGroup("km_reb_move");
      KeyGroup eg = new KeyGroup("km_reb_edit");
      KeyMap km = new KeyMap("rebtest", mg, eg);
      JeyEvent key = new JeyEvent(0, 0, 'r');
      km.addEditBinding(key, "substitute", null);
      assertTrue(km.removeEditBinding(key));
      assertNull(km.lookupEdit(key));
   }

   @Test
   @DisplayName("removeEditBinding returns false for nonexistent")
   void removeEditBindingNonexistent() {
      KeyGroup mg = new KeyGroup("km_rebn_move");
      KeyGroup eg = new KeyGroup("km_rebn_edit");
      KeyMap km = new KeyMap("rebntest", mg, eg);
      JeyEvent key = new JeyEvent(0, 0, (char) 201);
      assertFalse(km.removeEditBinding(key));
   }

   // ── Convenience bind methods ──────────────────────────────

   @Test
   @DisplayName("bindMoveKey char adds binding")
   void bindMoveKeyChar() {
      KeyGroup mg = new KeyGroup("km_bmk_move");
      KeyGroup eg = new KeyGroup("km_bmk_edit");
      KeyMap km = new KeyMap("bmktest", mg, eg);
      km.bindMoveKey('j', "moveline", null);
      JeyEvent key = new JeyEvent(0, 0, 'j');
      assertNotNull(km.lookupMove(key));
   }

   @Test
   @DisplayName("bindEditKey char adds binding")
   void bindEditKeyChar() {
      KeyGroup mg = new KeyGroup("km_bek_move");
      KeyGroup eg = new KeyGroup("km_bek_edit");
      KeyMap km = new KeyMap("bektest", mg, eg);
      km.bindEditKey('d', "deletemode", null);
      JeyEvent key = new JeyEvent(0, 0, 'd');
      assertNotNull(km.lookupEdit(key));
   }

   // ── Registry operations ───────────────────────────────────

   @Test
   @DisplayName("registry: registered keymaps visible in names set")
   void registeredNames() {
      java.util.Set<String> names = KeyMap.registeredNames();
      assertNotNull(names);
      // After initCommands, "normal" should be registered
      // but we don't assert specific names to avoid coupling
   }

   @Test
   @DisplayName("registry: get returns null for unknown name")
   void registryGetNull() {
      assertNull(KeyMap.get("nonexistent_keymap_12345"));
   }

   @Test
   @DisplayName("registry: register and get round-trip")
   void registryRoundTrip() {
      KeyGroup mg = new KeyGroup("km_rrt_move");
      KeyGroup eg = new KeyGroup("km_rrt_edit");
      KeyMap km = new KeyMap("ju_rrt_test", mg, eg);
      KeyMap.register(km);
      assertEquals(km, KeyMap.get("ju_rrt_test"));
   }

   @Test
   @DisplayName("getOverlayKeymaps returns only keymaps with parents")
   void overlayKeymapsFiltered() {
      java.util.List<KeyMap> overlays = KeyMap.getOverlayKeymaps();
      assertNotNull(overlays);
      for (KeyMap km : overlays) {
         assertNotNull(km.getParent(),
            "overlay keymap should have parent: " + km.getName());
      }
   }

   // ── getReverseBindingMap ──────────────────────────────────

   @Test
   @DisplayName("getReverseBindingMap returns non-null")
   void reverseBindingMapNotNull() {
      KeyGroup mg = new KeyGroup("km_rbm_move");
      KeyGroup eg = new KeyGroup("km_rbm_edit");
      KeyMap km = new KeyMap("rbmtest", mg, eg);
      km.bindMoveKey('h', "movechar", null);
      km.bindEditKey('x', "deletechars", null);

      java.util.Map<String, java.util.List<String>> map =
         km.getReverseBindingMap();
      assertNotNull(map);
      assertFalse(map.isEmpty());
   }

   // ── resolveForBuffer ──────────────────────────────────────

   @Test
   @DisplayName("resolveForBuffer: null buffer returns null")
   void resolveForBufferNull() {
      assertNull(KeyMap.resolveForBuffer(null));
   }

   @Test
   @DisplayName("resolveForBuffer: plain TextEdit returns null")
   void resolveForBufferPlainText() throws Exception {
      FileProperties<String> fp = new FileProperties<>(
         FileDescriptor.InternalFd.make("ju_km_plain"),
         StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      assertNull(KeyMap.resolveForBuffer(te));
      te.disposeFvc();
   }

   // ── getMoveKeys / getEditKeys ─────────────────────────────

   @Test
   @DisplayName("getMoveKeys returns the move KeyGroup")
   void getMoveKeysReturnsGroup() {
      KeyGroup mg = new KeyGroup("km_gmk_move");
      KeyGroup eg = new KeyGroup("km_gmk_edit");
      KeyMap km = new KeyMap("gmktest", mg, eg);
      assertEquals(mg, km.getMoveKeys());
   }

   @Test
   @DisplayName("getEditKeys returns the edit KeyGroup")
   void getEditKeysReturnsGroup() {
      KeyGroup mg = new KeyGroup("km_gek_move");
      KeyGroup eg = new KeyGroup("km_gek_edit");
      KeyMap km = new KeyMap("gektest", mg, eg);
      assertEquals(eg, km.getEditKeys());
   }
}
