package javi;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended JUnit 5 tests for {@link KeyGroup} — covers bind/unbind,
 * getCommandName, getBindingEntries, user binding persistence,
 * reverse binding map, and formatKeySpec.
 */
class KeyGroupExtendedJUnitTest {

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

   // ── getName ──────────────────────────────────────────────────

   @Test
   @DisplayName("getName returns name set in constructor")
   void getNameReturnsConstructorName() {
      KeyGroup kg = new KeyGroup("movement");
      assertEquals("movement", kg.getName());
   }

   @Test
   @DisplayName("getName returns null for no-arg constructor")
   void getNameNullForDefaultConstructor() {
      KeyGroup kg = new KeyGroup();
      assertNull(kg.getName());
   }

   // ── getCommandName ───────────────────────────────────────────

   @Test
   @DisplayName("getCommandName returns command for bound key")
   void getCommandNameReturnsBound() {
      KeyGroup kg = new KeyGroup();
      kg.keybind('j', "moveline", null);
      JeyEvent ev = new JeyEvent(0, 0, 'j');
      assertEquals("moveline", kg.getCommandName(ev));
   }

   @Test
   @DisplayName("getCommandName returns null for unbound key")
   void getCommandNameReturnsNullUnbound() {
      KeyGroup kg = new KeyGroup();
      JeyEvent ev = new JeyEvent(0, 0, 'z');
      assertNull(kg.getCommandName(ev));
   }

   // ── bind (runtime) ───────────────────────────────────────────

   @Nested
   @DisplayName("bind()")
   class BindTests {

      @Test
      @DisplayName("bind adds a new runtime binding")
      void bindAddsNew() {
         KeyGroup kg = new KeyGroup();
         JeyEvent ev = new JeyEvent(0, 0, 'q');
         kg.bind(ev, "deletechars", null);
         assertNotNull(kg.get(ev));
         assertEquals("deletechars", kg.getCommandName(ev));
      }

      @Test
      @DisplayName("bind overwrites existing binding without throwing")
      void bindOverwritesExisting() {
         KeyGroup kg = new KeyGroup();
         kg.keybind('x', "deletechars", null);
         JeyEvent ev = new JeyEvent(0, 0, 'x');
         kg.bind(ev, "joinlines", null);
         assertEquals("joinlines", kg.getCommandName(ev));
      }

      @Test
      @DisplayName("bind marks as user binding")
      void bindTracksUserBinding() {
         KeyGroup kg = new KeyGroup();
         assertFalse(kg.hasUserBindings());
         JeyEvent ev = new JeyEvent(0, 0, 'r');
         kg.bind(ev, "redraw", null);
         assertTrue(kg.hasUserBindings());
      }
   }

   // ── unbind ───────────────────────────────────────────────────

   @Nested
   @DisplayName("unbind()")
   class UnbindTests {

      @Test
      @DisplayName("unbind removes existing binding and returns true")
      void unbindExisting() {
         KeyGroup kg = new KeyGroup();
         kg.keybind('w', "forwardword", null);
         JeyEvent ev = new JeyEvent(0, 0, 'w');
         assertTrue(kg.unbind(ev));
         assertNull(kg.get(ev));
         assertNull(kg.getCommandName(ev));
      }

      @Test
      @DisplayName("unbind returns false for non-existent binding")
      void unbindNonExistent() {
         KeyGroup kg = new KeyGroup();
         JeyEvent ev = new JeyEvent(0, 0, 'z');
         assertFalse(kg.unbind(ev));
      }

      @Test
      @DisplayName("unbind clears user binding tracking")
      void unbindClearsUserBinding() {
         KeyGroup kg = new KeyGroup();
         JeyEvent ev = new JeyEvent(0, 0, 'q');
         kg.bind(ev, "deletechars", null);
         assertTrue(kg.hasUserBindings());
         kg.unbind(ev);
         assertFalse(kg.hasUserBindings());
      }
   }

   // ── hasUserBindings ──────────────────────────────────────────

   @Test
   @DisplayName("hasUserBindings false when empty")
   void hasUserBindingsFalseEmpty() {
      KeyGroup kg = new KeyGroup();
      assertFalse(kg.hasUserBindings());
   }

   @Test
   @DisplayName("hasUserBindings false after only keybind (not bind)")
   void hasUserBindingsFalseAfterKeybind() {
      KeyGroup kg = new KeyGroup();
      kg.keybind('a', "append", null);
      assertFalse(kg.hasUserBindings());
   }

   // ── getBindingEntries ────────────────────────────────────────

   @Nested
   @DisplayName("getBindingEntries()")
   class BindingEntriesTests {

      @Test
      @DisplayName("returns empty list for empty KeyGroup")
      void emptyKeyGroup() {
         KeyGroup kg = new KeyGroup();
         List<String[]> entries = kg.getBindingEntries();
         assertTrue(entries.isEmpty());
      }

      @Test
      @DisplayName("returns key-command pairs sorted by key")
      void returnsSortedPairs() {
         KeyGroup kg = new KeyGroup();
         kg.keybind('k', "movechar", null);
         kg.keybind('j', "moveline", null);
         List<String[]> entries = kg.getBindingEntries();
         assertEquals(2, entries.size());
         assertEquals(2, entries.get(0).length);
         // sorted: j before k
         assertTrue(entries.get(0)[0].compareTo(entries.get(1)[0]) <= 0);
      }

      @Test
      @DisplayName("entries contain correct command names")
      void entriesHaveCorrectCommands() {
         KeyGroup kg = new KeyGroup();
         kg.keybind('w', "forwardword", null);
         List<String[]> entries = kg.getBindingEntries();
         assertEquals(1, entries.size());
         assertEquals("forwardword", entries.get(0)[1]);
      }
   }

   // ── getUserBindingSpecs ──────────────────────────────────────

   @Nested
   @DisplayName("getUserBindingSpecs()")
   class UserBindingSpecsTests {

      @Test
      @DisplayName("empty when no user bindings exist")
      void emptyWhenNoUserBindings() {
         KeyGroup kg = new KeyGroup();
         kg.keybind('x', "deletechars", null);
         List<String> specs = kg.getUserBindingSpecs();
         assertTrue(specs.isEmpty());
      }

      @Test
      @DisplayName("includes user-bound keys")
      void includesUserBoundKeys() {
         KeyGroup kg = new KeyGroup();
         JeyEvent ev = new JeyEvent(0, 0, 'q');
         kg.bind(ev, "deletechars", null);
         List<String> specs = kg.getUserBindingSpecs();
         assertEquals(1, specs.size());
         assertTrue(specs.get(0).contains("deletechars"));
      }

      @Test
      @DisplayName("sorted alphabetically")
      void sortedAlphabetically() {
         KeyGroup kg = new KeyGroup();
         kg.bind(new JeyEvent(0, 0, 'z'), "deletechars", null);
         kg.bind(new JeyEvent(0, 0, 'a'), "append", null);
         List<String> specs = kg.getUserBindingSpecs();
         assertEquals(2, specs.size());
         assertTrue(specs.get(0).compareTo(specs.get(1)) <= 0);
      }
   }

   // ── getReverseBindingMap ─────────────────────────────────────

   @Nested
   @DisplayName("getReverseBindingMap()")
   class ReverseBindingMapTests {

      @Test
      @DisplayName("empty KeyGroup returns empty map")
      void emptyKeyGroup() {
         KeyGroup kg = new KeyGroup();
         Map<String, List<String>> map = kg.getReverseBindingMap();
         assertTrue(map.isEmpty());
      }

      @Test
      @DisplayName("maps command name to key strings")
      void mapsCommandToKeys() {
         KeyGroup kg = new KeyGroup();
         kg.keybind('j', "moveline", null);
         Map<String, List<String>> map = kg.getReverseBindingMap();
         assertTrue(map.containsKey("moveline"));
         assertEquals(1, map.get("moveline").size());
      }

      @Test
      @DisplayName("multiple keys for same command are grouped")
      void multipleKeysGrouped() {
         KeyGroup kg = new KeyGroup();
         kg.keybind('j', "moveline", null);
         kg.keyactionbind(JeyEvent.VK_DOWN, "moveline", null, 0);
         Map<String, List<String>> map = kg.getReverseBindingMap();
         assertTrue(map.containsKey("moveline"));
         assertEquals(2, map.get("moveline").size());
      }
   }

   // ── formatKeySpec (tested via getUserBindingSpecs) ───────────

   @Nested
   @DisplayName("formatKeySpec()")
   class FormatKeySpecTests {

      @Test
      @DisplayName("plain char key formats as single char")
      void plainChar() {
         KeyGroup kg = new KeyGroup();
         JeyEvent ev = new JeyEvent(0, 0, 'x');
         String spec = kg.formatKeySpec(ev);
         assertEquals("x", spec);
      }

      @Test
      @DisplayName("ctrl+char formats as C-x")
      void ctrlChar() {
         KeyGroup kg = new KeyGroup();
         // Ctrl-a = char value 1
         JeyEvent ev = new JeyEvent(JeyEvent.CTRL_MASK, 0, (char) 1);
         String spec = kg.formatKeySpec(ev);
         assertEquals("C-a", spec);
      }

      @Test
      @DisplayName("shift+space formats as S-space")
      void shiftSpace() {
         KeyGroup kg = new KeyGroup();
         JeyEvent ev = new JeyEvent(JeyEvent.SHIFT_MASK, 0, ' ');
         String spec = kg.formatKeySpec(ev);
         assertEquals("S- ", spec);
      }

      @Test
      @DisplayName("alt+char formats as A-x")
      void altChar() {
         KeyGroup kg = new KeyGroup();
         JeyEvent ev = new JeyEvent(JeyEvent.ALT_MASK, 0, 'g');
         String spec = kg.formatKeySpec(ev);
         assertEquals("A-g", spec);
      }

      @Test
      @DisplayName("action key F1 formats as F1")
      void actionKeyF1() {
         KeyGroup kg = new KeyGroup();
         JeyEvent ev = new JeyEvent(0, JeyEvent.VK_F1,
            JeyEvent.CHAR_UNDEFINED);
         String spec = kg.formatKeySpec(ev);
         assertEquals("F1", spec);
      }

      @Test
      @DisplayName("ctrl+action key formats with C- prefix")
      void ctrlActionKey() {
         KeyGroup kg = new KeyGroup();
         JeyEvent ev = new JeyEvent(JeyEvent.CTRL_MASK, JeyEvent.VK_HOME,
            JeyEvent.CHAR_UNDEFINED);
         String spec = kg.formatKeySpec(ev);
         assertEquals("C-Home", spec);
      }

      @Test
      @DisplayName("shift+action key formats with S- prefix")
      void shiftActionKey() {
         KeyGroup kg = new KeyGroup();
         JeyEvent ev = new JeyEvent(JeyEvent.SHIFT_MASK, JeyEvent.VK_F5,
            JeyEvent.CHAR_UNDEFINED);
         String spec = kg.formatKeySpec(ev);
         assertEquals("S-F5", spec);
      }
   }
}
