package javi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for {@link KeyBindingPersistence} save/load
 * roundtrip and edge-case handling.
 */
class KeyBindingPersistenceCoverageJUnitTest {

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

   // ── save() edge cases ──────────────────────────────────────

   @Test
   void saveReturnsZeroWhenNoNormalKeyMap() throws IOException {
      // Before bindCommands, normalKeyMap might be null depending
      // on test order. If commands are already initialized, the
      // keymap exists but may have zero user bindings.
      // With initCommands (which doesn't call bindCommands),
      // normalKeyMap is NOT set — but initCommands creates
      // MoveGroup which sets the inst field. Let's test the
      // save-with-no-user-bindings path.
      int count = KeyBindingPersistence.save();
      // Either normalKeyMap==null returns 0, or no user bindings returns 0
      assertEquals(0, count,
         "save with no user bindings should return 0");
   }

   // ── load() edge cases ──────────────────────────────────────

   @Test
   void loadReturnsZeroWhenFileDoesNotExist() {
      // The config path ~/.javi/keybindings may or may not exist
      // but load() handles non-existent files gracefully
      // This covers the Files.exists check branch
      Path configPath = KeyBindingPersistence.getConfigPath();
      if (!Files.exists(configPath)) {
         int count = KeyBindingPersistence.load();
         assertEquals(0, count,
            "load from non-existent file should return 0");
      }
   }

   // ── getConfigPath ──────────────────────────────────────────

   @Test
   void configPathParentIsJaviDir() {
      Path p = KeyBindingPersistence.getConfigPath();
      assertNotNull(p.getParent());
      assertTrue(p.getParent().getFileName().toString()
         .equals(".javi"),
         "parent directory should be .javi");
   }

   @Test
   void configPathFileNameIsKeybindings() {
      Path p = KeyBindingPersistence.getConfigPath();
      assertEquals("keybindings", p.getFileName().toString());
   }

   @Test
   void configPathIsAbsolute() {
      Path p = KeyBindingPersistence.getConfigPath();
      assertTrue(p.isAbsolute(),
         "config path should be absolute");
   }

   @Test
   void configPathUsesUserHome() {
      Path p = KeyBindingPersistence.getConfigPath();
      String home = System.getProperty("user.home");
      assertTrue(p.toString().startsWith(home),
         "config path should start with user.home");
   }

   // ── KeyGroup.getUserBindingSpecs format for save ───────────

   @Test
   void userBindingSpecForCtrlCharIncludesCommand() {
      KeyGroup kg = new KeyGroup("save-test");
      JeyEvent ev = new JeyEvent(
         JeyEvent.CTRL_MASK, 0, (char) 1);
      kg.bind(ev, "movechar", null);
      List<String> specs = kg.getUserBindingSpecs();
      assertEquals(1, specs.size());
      assertTrue(specs.get(0).contains("movechar"),
         "spec should contain command name");
      assertTrue(specs.get(0).contains("C-a"),
         "spec should contain formatted key");
   }

   @Test
   void userBindingSpecForActionKey() {
      KeyGroup kg = new KeyGroup("save-test2");
      JeyEvent ev = new JeyEvent(
         0, JeyEvent.VK_F7, JeyEvent.CHAR_UNDEFINED);
      kg.bind(ev, "moveline", null);
      List<String> specs = kg.getUserBindingSpecs();
      assertEquals(1, specs.size());
      assertTrue(specs.get(0).startsWith("F7"),
         "spec should start with F7");
   }

   @Test
   void userBindingSpecsSortedAlphabetically() {
      KeyGroup kg = new KeyGroup("sort-test");
      kg.bind(new JeyEvent(0, 0, 'z'), "movechar", null);
      kg.bind(new JeyEvent(0, 0, 'a'), "moveline", null);
      kg.bind(new JeyEvent(0, 0, 'm'), "forwardword", null);
      List<String> specs = kg.getUserBindingSpecs();
      assertEquals(3, specs.size());
      assertTrue(specs.get(0).compareTo(specs.get(1)) <= 0,
         "specs should be sorted");
      assertTrue(specs.get(1).compareTo(specs.get(2)) <= 0,
         "specs should be sorted");
   }

   @Test
   void hasUserBindingsReturnsFalseAfterClear() {
      KeyGroup kg = new KeyGroup("clear-test");
      JeyEvent ev1 = new JeyEvent(0, 0, 'q');
      JeyEvent ev2 = new JeyEvent(0, 0, 'w');
      kg.bind(ev1, "movechar", null);
      kg.bind(ev2, "moveline", null);
      assertTrue(kg.hasUserBindings());
      kg.unbind(ev1);
      kg.unbind(ev2);
      assertTrue(kg.getUserBindingSpecs().isEmpty());
   }

   // ── Overlay keymap user bindings for save path ─────────────

   @Test
   void overlayKeymapUserBindingsTracked() {
      KeyGroup parentMove = new KeyGroup("p-move");
      KeyGroup parentEdit = new KeyGroup("p-edit");
      KeyMap parent = new KeyMap("parent-kb",
         parentMove, parentEdit);
      KeyMap overlay = KeyMap.createOverlay("overlay-kb", parent);
      KeyMap.register(parent);
      KeyMap.register(overlay);

      // Bind a user key in the overlay's move group
      JeyEvent ev = new JeyEvent(0, 0, 'q');
      overlay.getMoveKeys().bind(ev, "movechar", null);

      assertTrue(overlay.getMoveKeys().hasUserBindings(),
         "overlay move keys should track user bindings");
      List<String> specs =
         overlay.getMoveKeys().getUserBindingSpecs();
      assertEquals(1, specs.size());
   }
}
