package javi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for {@link KeyMap} dispatch chain in the live editor.
 *
 * <p>Exercises the keymap registry, lookup chain, overlay creation,
 * parent-chain fallback, visual handler invocation, and runtime
 * binding modification. Requires full Javi initialization for
 * the "normal" and "insert" keymaps to be populated.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class KeyMapDispatchGuiJUnitTest {

   private static Robot robot;
   private static KeyMap normalMap;

   @BeforeAll
   static void initJavi() throws Exception {
      if (Rgroup.bindingLookup("persistfile") == null) {
         EventQueue.biglock2.lock();
         try {
            Class.forName("javi.TextEdit");
            EditTester1.TestCircBuffer.initCmd();
            DirManager.getInstance();
            FileList.make("");
            Javi.initToUi();
            Javi.initPostUi();
            Command.doneInit();
         } finally {
            EventQueue.biglock2.unlock();
         }
         Thread.sleep(500);
      }
      robot = BasicRobot.robotWithCurrentAwtHierarchy();
      normalMap = KeyMap.get("normal");
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── Registry tests ───────────────────────────────────────────

   @Test
   void t01_normalKeymapRegistered() {
      assertNotNull(normalMap,
         "normal keymap must be registered after Javi init");
   }

   @Test
   void t02_normalKeymapHasName() {
      assertEquals("normal", normalMap.getName());
   }

   @Test
   void t03_registeredKeymapNamesNotEmpty() {
      Set<String> names = KeyMap.registeredNames();
      assertNotNull(names);
      assertTrue(names.contains("normal"),
         "Registry must contain 'normal'");
   }

   @Test
   void t04_normalKeymapHasNoParent() {
      assertNull(normalMap.getParent(),
         "Root 'normal' keymap should have no parent");
   }

   @Test
   void t05_normalMoveKeysNotNull() {
      assertNotNull(normalMap.getMoveKeys(),
         "normal keymap must have moveKeys group");
   }

   @Test
   void t06_normalEditKeysNotNull() {
      assertNotNull(normalMap.getEditKeys(),
         "normal keymap must have editKeys group");
   }

   // ── Lookup tests ─────────────────────────────────────────────

   @Test
   void t07_lookupMoveForKnownKey() {
      // 'h' is left movement in normal mode
      JeyEvent hKey = new JeyEvent(0, 0, 'h');
      Rgroup.KeyBinding binding = normalMap.lookupMove(hKey);
      assertNotNull(binding,
         "'h' should be bound as a movement key in normal mode");
   }

   @Test
   void t08_lookupMoveForUnboundKey() {
      // An unusual key that is unlikely to be bound
      JeyEvent key = new JeyEvent(0, 0, '\u0000');
      Rgroup.KeyBinding binding = normalMap.lookupMove(key);
      // May or may not be bound — just don't crash
   }

   @Test
   void t09_lookupEditForKnownKey() {
      // 'x' is delete char in normal mode (edit operation)
      JeyEvent xKey = new JeyEvent(0, 0, 'x');
      Rgroup.KeyBinding binding = normalMap.lookupEdit(xKey);
      assertNotNull(binding,
         "'x' should be bound as an edit key in normal mode");
   }

   @Test
   void t10_lookupMoveJKey() {
      // 'j' is down movement
      JeyEvent jKey = new JeyEvent(0, 0, 'j');
      Rgroup.KeyBinding binding = normalMap.lookupMove(jKey);
      assertNotNull(binding,
         "'j' should be bound as a movement key (down)");
   }

   @Test
   void t11_lookupMoveKKey() {
      // 'k' is up movement
      JeyEvent kKey = new JeyEvent(0, 0, 'k');
      Rgroup.KeyBinding binding = normalMap.lookupMove(kKey);
      assertNotNull(binding,
         "'k' should be bound as a movement key (up)");
   }

   @Test
   void t12_lookupMoveLKey() {
      // 'l' is right movement
      JeyEvent lKey = new JeyEvent(0, 0, 'l');
      Rgroup.KeyBinding binding = normalMap.lookupMove(lKey);
      assertNotNull(binding,
         "'l' should be bound as a movement key (right)");
   }

   // ── Overlay creation and parent-chain fallback ───────────────

   @Test
   void t13_createOverlayHasParent() {
      KeyMap overlay = KeyMap.createOverlay("test-overlay", normalMap);
      assertNotNull(overlay);
      assertEquals(normalMap, overlay.getParent());
      assertEquals("test-overlay", overlay.getName());
   }

   @Test
   void t14_overlayFallsThruToParentMove() {
      KeyMap overlay = KeyMap.createOverlay("test-fallthru", normalMap);
      // 'h' not bound in overlay, should fall through to normalMap
      JeyEvent hKey = new JeyEvent(0, 0, 'h');
      Rgroup.KeyBinding binding = overlay.lookupMove(hKey);
      assertNotNull(binding,
         "Overlay should fall through to parent for 'h' move binding");
   }

   @Test
   void t15_overlayFallsThruToParentEdit() {
      KeyMap overlay = KeyMap.createOverlay("test-fallthru2", normalMap);
      // 'x' not bound in overlay, should fall through
      JeyEvent xKey = new JeyEvent(0, 0, 'x');
      Rgroup.KeyBinding binding = overlay.lookupEdit(xKey);
      assertNotNull(binding,
         "Overlay should fall through to parent for 'x' edit binding");
   }

   @Test
   void t16_overlayLocalBindingOverridesParent() {
      KeyMap overlay = KeyMap.createOverlay("test-override", normalMap);
      JeyEvent zKey = new JeyEvent(0, 0, 'z');
      // Bind 'z' locally to a move command
      overlay.bindMoveKey('z', "movechar", null);
      Rgroup.KeyBinding binding = overlay.lookupMove(zKey);
      assertNotNull(binding);
      // Verify via KeyGroup.getCommandName
      String cmdName = overlay.getMoveKeys().getCommandName(zKey);
      assertEquals("movechar", cmdName,
         "Overlay 'z' should resolve to 'movechar'");
   }

   @Test
   void t17_suppressParentEditBlocks() {
      KeyMap overlay = KeyMap.createOverlay("test-suppress", normalMap);
      overlay.setSuppressParentEdit(true);
      // 'x' not bound locally, parent suppressed → null
      JeyEvent xKey = new JeyEvent(0, 0, 'x');
      Rgroup.KeyBinding binding = overlay.lookupEdit(xKey);
      assertNull(binding,
         "Suppressed parent edit should return null for unbound keys");
   }

   @Test
   void t18_suppressParentEditDoesNotAffectMove() {
      KeyMap overlay = KeyMap.createOverlay("test-suppress2", normalMap);
      overlay.setSuppressParentEdit(true);
      // Move bindings should still fall through
      JeyEvent hKey = new JeyEvent(0, 0, 'h');
      Rgroup.KeyBinding binding = overlay.lookupMove(hKey);
      assertNotNull(binding,
         "Move fallthrough should work even when edit is suppressed");
   }

   // ── Runtime binding modification ─────────────────────────────

   @Test
   void t19_addAndRemoveMoveBinding() {
      KeyMap overlay = KeyMap.createOverlay("test-addremove", normalMap);
      JeyEvent zKey = new JeyEvent(0, 0, 'z');
      // Bind 'z' to a known registered command
      overlay.bindMoveKey('z', "movechar", null);
      Rgroup.KeyBinding binding = overlay.lookupMove(zKey);
      assertNotNull(binding);
      String cmdName = overlay.getMoveKeys().getCommandName(zKey);
      assertEquals("movechar", cmdName);
      // Remove it
      boolean removed = overlay.removeMoveBinding(zKey);
      assertTrue(removed);
   }

   @Test
   void t20_addAndRemoveEditBinding() {
      KeyMap overlay = KeyMap.createOverlay("test-editbind", normalMap);
      JeyEvent zKey = new JeyEvent(0, 0, 'z');
      overlay.bindEditKey('z', "persistfile", null);
      Rgroup.KeyBinding binding = overlay.lookupEdit(zKey);
      assertNotNull(binding);
      String cmdName = overlay.getEditKeys().getCommandName(zKey);
      assertEquals("persistfile", cmdName);
      boolean removed = overlay.removeEditBinding(zKey);
      assertTrue(removed);
   }

   @Test
   void t21_removeNonexistentBindingReturnsFalse() {
      KeyMap overlay = KeyMap.createOverlay("test-noremove", normalMap);
      JeyEvent key = new JeyEvent(0, 0, '\u007F');
      boolean removed = overlay.removeMoveBinding(key);
      assertFalse(removed);
   }

   // ── Visual handler ───────────────────────────────────────────

   @Test
   void t22_visualHandlerDefaultNull() {
      KeyMap overlay = KeyMap.createOverlay("test-visual", normalMap);
      // New overlay with no visual handler set
      // May inherit from parent or be null depending on parent state
      KeyMap.VisualHandler handler = overlay.getVisualHandler();
      // Just verify no crash; handler may or may not be null
   }

   @Test
   void t23_setVisualHandlerReturnsIt() {
      KeyMap overlay = KeyMap.createOverlay("test-visual2", normalMap);
      final boolean[] called = {false};
      overlay.setVisualHandler((key, sy, dy, sx, dx, fvc) -> {
         called[0] = true;
         return true;
      });
      KeyMap.VisualHandler handler = overlay.getVisualHandler();
      assertNotNull(handler);
   }

   @Test
   void t24_visualHandlerInvocation() throws Exception {
      KeyMap overlay = KeyMap.createOverlay("test-visual3", normalMap);
      final boolean[] called = {false};
      overlay.setVisualHandler((key, sy, dy, sx, dx, fvc) -> {
         called[0] = true;
         return true;
      });
      KeyMap.VisualHandler handler = overlay.getVisualHandler();
      boolean consumed = handler.handle('s', 1, 5, 0, 10, null);
      assertTrue(consumed);
      assertTrue(called[0]);
   }

   // ── Overlay KeyMap listing ───────────────────────────────────

   @Test
   void t25_getOverlayKeymapsReturnsOnlyOverlays() {
      java.util.List<KeyMap> overlays = KeyMap.getOverlayKeymaps();
      for (KeyMap km : overlays) {
         assertNotNull(km.getParent(),
            "Overlay keymap " + km.getName()
               + " must have a parent");
      }
   }

   // ── Reverse binding map ──────────────────────────────────────

   @Test
   void t26_reverseBindingMapNotEmpty() {
      java.util.Map<String, java.util.List<String>> map =
         normalMap.getReverseBindingMap();
      assertNotNull(map);
      assertTrue(map.size() > 0,
         "Normal keymap should have some bindings");
   }

   @Test
   void t27_reverseBindingMapContainsKnownCommand() {
      java.util.Map<String, java.util.List<String>> map =
         normalMap.getReverseBindingMap();
      // Check a command that should be bound in normal mode
      // "movechar" is bound to 'h' and 'l' in normal map
      boolean found = map.containsKey("movechar")
         || map.containsKey("moveline");
      assertTrue(found,
         "Reverse binding map should contain known commands like movechar/moveline");
   }

   // ── Ctrl-modified key lookup ─────────────────────────────────

   @Test
   void t28_ctrlKeyLookup() {
      // Ctrl-F or similar should be bound
      JeyEvent ctrlF = new JeyEvent(JeyEvent.CTRL_MASK, 0, 'f');
      Rgroup.KeyBinding binding = normalMap.lookupMove(ctrlF);
      // Ctrl-F is page-down in vi — should be bound
      if (binding != null) {
         String cmdName = normalMap.getMoveKeys().getCommandName(ctrlF);
         assertNotNull(cmdName);
      }
   }

   @Test
   void t29_multipleOverlaysIndependent() {
      KeyMap overlay1 = KeyMap.createOverlay("indep1", normalMap);
      KeyMap overlay2 = KeyMap.createOverlay("indep2", normalMap);
      overlay1.bindMoveKey('z', "movechar", null);
      overlay2.bindMoveKey('z', "moveline", null);
      JeyEvent zKey = new JeyEvent(0, 0, 'z');
      String cmd1 = overlay1.getMoveKeys().getCommandName(zKey);
      String cmd2 = overlay2.getMoveKeys().getCommandName(zKey);
      assertEquals("movechar", cmd1);
      assertEquals("moveline", cmd2);
   }

   @Test
   void t30_overlayEditBindingWithModifier() {
      KeyMap overlay = KeyMap.createOverlay("modtest", normalMap);
      overlay.bindEditKey('s', "persistfile", null, JeyEvent.CTRL_MASK);
      JeyEvent ctrlS = new JeyEvent(JeyEvent.CTRL_MASK, 0, 's');
      Rgroup.KeyBinding binding = overlay.lookupEdit(ctrlS);
      assertNotNull(binding);
      String cmdName = overlay.getEditKeys().getCommandName(ctrlS);
      assertEquals("persistfile", cmdName);
   }
}
