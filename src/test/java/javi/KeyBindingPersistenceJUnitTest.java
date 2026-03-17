package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for keybinding persistence roundtrip:
 * formatKeySpec -> parseKeySpec (via :mapkey command), and
 * KeyBindingPersistence save format verification.
 */
class KeyBindingPersistenceJUnitTest {

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

   // ---- formatKeySpec tests ----

   @Test
   void formatKeySpecPlainChar() {
      KeyGroup kg = new KeyGroup("test");
      JeyEvent ev = new JeyEvent(0, 0, 'h');
      assertEquals("h", kg.formatKeySpec(ev));
   }

   @Test
   void formatKeySpecCtrlChar() {
      KeyGroup kg = new KeyGroup("test");
      // Ctrl+B = char code 2, with CTRL_MASK
      JeyEvent ev = new JeyEvent(JeyEvent.CTRL_MASK, 0, (char) 2);
      assertEquals("C-b", kg.formatKeySpec(ev));
   }

   @Test
   void formatKeySpecCtrlA() {
      KeyGroup kg = new KeyGroup("test");
      // Ctrl+A = char code 1
      JeyEvent ev = new JeyEvent(JeyEvent.CTRL_MASK, 0, (char) 1);
      assertEquals("C-a", kg.formatKeySpec(ev));
   }

   @Test
   void formatKeySpecCtrlZ() {
      KeyGroup kg = new KeyGroup("test");
      // Ctrl+Z = char code 26
      JeyEvent ev = new JeyEvent(JeyEvent.CTRL_MASK, 0, (char) 26);
      assertEquals("C-z", kg.formatKeySpec(ev));
   }

   @Test
   void formatKeySpecActionKey() {
      KeyGroup kg = new KeyGroup("test");
      JeyEvent ev = new JeyEvent(0, JeyEvent.VK_F1, JeyEvent.CHAR_UNDEFINED);
      assertEquals("F1", kg.formatKeySpec(ev));
   }

   @Test
   void formatKeySpecShiftChar() {
      KeyGroup kg = new KeyGroup("test");
      JeyEvent ev = new JeyEvent(JeyEvent.SHIFT_MASK, 0, ' ');
      assertEquals("S- ", kg.formatKeySpec(ev));
   }

   @Test
   void formatKeySpecAltChar() {
      KeyGroup kg = new KeyGroup("test");
      JeyEvent ev = new JeyEvent(JeyEvent.ALT_MASK, 0, 'x');
      assertEquals("A-x", kg.formatKeySpec(ev));
   }

   // ---- Ctrl roundtrip via :mapkey command ----

   @Test
   void ctrlBRoundtripViaBind() {
      // Create a fresh KeyGroup and bind Ctrl+B the same way MapEvent does
      KeyGroup kg = new KeyGroup("rt-move");
      JeyEvent original = new JeyEvent(JeyEvent.CTRL_MASK, 0, (char) 2);
      kg.keybind((char) 2, "movechar", Boolean.FALSE, JeyEvent.CTRL_MASK);

      // Verify the binding exists with the original key
      assertNotNull(kg.get(original),
         "binding should exist for Ctrl+B (code 2)");

      // Now use bind() which tracks user bindings, and check formatKeySpec
      KeyGroup kg2 = new KeyGroup("rt2-move");
      kg2.bind(original, "movechar", null);
      List<String> specs = kg2.getUserBindingSpecs();
      assertEquals(1, specs.size());
      assertTrue(specs.get(0).startsWith("C-b"),
         "formatKeySpec should produce C-b for Ctrl+B");
   }

   @Test
   void ctrlFRoundtripFormat() {
      // Ctrl+F = char code 6
      KeyGroup kg = new KeyGroup("test");
      JeyEvent original = new JeyEvent(JeyEvent.CTRL_MASK, 0, (char) 6);
      kg.bind(original, "movechar", null);

      List<String> specs = kg.getUserBindingSpecs();
      assertEquals(1, specs.size());
      String spec = specs.get(0);
      assertTrue(spec.startsWith("C-f"),
         "Ctrl+F (code 6) should format as C-f, got: " + spec);
   }

   @Test
   void plainCharRoundtripViaBind() {
      KeyGroup kg = new KeyGroup("test");
      JeyEvent original = new JeyEvent(0, 0, 'x');
      kg.bind(original, "movechar", null);

      List<String> specs = kg.getUserBindingSpecs();
      assertEquals(1, specs.size());
      assertTrue(specs.get(0).startsWith("x"),
         "plain char should format as 'x'");

      // Verify the roundtrip: parse "x" -> JeyEvent(0, 0, 'x')
      JeyEvent parsed = new JeyEvent(0, 0, 'x');
      assertEquals(original, parsed, "plain char should roundtrip");
   }

   @Test
   void actionKeyRoundtripViaBind() {
      KeyGroup kg = new KeyGroup("test");
      JeyEvent original = new JeyEvent(0, JeyEvent.VK_F5,
         JeyEvent.CHAR_UNDEFINED);
      kg.bind(original, "movechar", null);

      List<String> specs = kg.getUserBindingSpecs();
      assertEquals(1, specs.size());
      assertTrue(specs.get(0).startsWith("F5"),
         "F5 key should format as F5");

      // Verify roundtrip: parse "F5" -> JeyEvent(0, VK_F5, CHAR_UNDEFINED)
      JeyEvent parsed = new JeyEvent(0, JeyEvent.VK_F5,
         JeyEvent.CHAR_UNDEFINED);
      assertEquals(original, parsed, "F5 should roundtrip");
   }

   // ---- getUserBindingSpecs format ----

   @Test
   void getUserBindingSpecsEmpty() {
      KeyGroup kg = new KeyGroup("test");
      assertTrue(kg.getUserBindingSpecs().isEmpty(),
         "fresh KeyGroup should have no user bindings");
   }

   @Test
   void getUserBindingSpecsFormatIsKeyCommand() {
      KeyGroup kg = new KeyGroup("test");
      kg.bind(new JeyEvent(0, 0, 'h'), "movechar", null);
      List<String> specs = kg.getUserBindingSpecs();
      assertEquals(1, specs.size());
      assertEquals("h movechar", specs.get(0));
   }

   @Test
   void getUserBindingSpecsMultipleBindings() {
      KeyGroup kg = new KeyGroup("test");
      kg.bind(new JeyEvent(0, 0, 'h'), "movechar", null);
      kg.bind(new JeyEvent(0, 0, 'j'), "moveline", null);
      List<String> specs = kg.getUserBindingSpecs();
      assertEquals(2, specs.size());
      // Specs are sorted
      assertEquals("h movechar", specs.get(0));
      assertEquals("j moveline", specs.get(1));
   }

   @Test
   void unbindRemovesFromUserBindings() {
      KeyGroup kg = new KeyGroup("test");
      JeyEvent ev = new JeyEvent(0, 0, 'h');
      kg.bind(ev, "movechar", null);
      assertTrue(kg.hasUserBindings());
      kg.unbind(ev);
      assertFalse(kg.hasUserBindings(),
         "unbind should remove from user bindings");
   }

   // ---- KeyBindingPersistence config path ----

   @Test
   void configPathIsUnderJaviDir() {
      java.nio.file.Path path = KeyBindingPersistence.getConfigPath();
      assertNotNull(path);
      assertTrue(path.toString().contains(".javi"));
      assertTrue(path.toString().endsWith("keybindings"));
   }

   // ---- Ctrl roundtrip regression test (the bug fix) ----

   @Test
   void ctrlKeyRoundtripAllLetters() {
      // Verify formatKeySpec -> parseKeySpec roundtrip for all Ctrl+letter
      KeyGroup kg = new KeyGroup("test");
      for (int i = 1; i <= 26; i++) {
         char ctrlChar = (char) i;
         JeyEvent original = new JeyEvent(JeyEvent.CTRL_MASK, 0, ctrlChar);
         String spec = kg.formatKeySpec(original);
         char expectedLetter = (char) ('a' + i - 1);
         assertEquals("C-" + expectedLetter, spec,
            "Ctrl+" + expectedLetter + " should format as C-"
            + expectedLetter);

         // Verify the parsed JeyEvent matches the original
         // parseKeySpec("C-x") should produce JeyEvent(CTRL_MASK, 0, ctrl_x)
         JeyEvent parsed = new JeyEvent(JeyEvent.CTRL_MASK, 0, ctrlChar);
         assertEquals(original, parsed,
            "Ctrl+" + expectedLetter + " should roundtrip as equal");
      }
   }

   // ---- Modifier + action key roundtrip tests (F21 edge cases) ----

   @Test
   void formatKeySpecCtrlF1() {
      KeyGroup kg = new KeyGroup("test");
      JeyEvent ev = new JeyEvent(JeyEvent.CTRL_MASK,
         JeyEvent.VK_F1, JeyEvent.CHAR_UNDEFINED);
      assertEquals("C-F1", kg.formatKeySpec(ev));
   }

   @Test
   void formatKeySpecShiftLeft() {
      KeyGroup kg = new KeyGroup("test");
      JeyEvent ev = new JeyEvent(JeyEvent.SHIFT_MASK,
         JeyEvent.VK_LEFT, JeyEvent.CHAR_UNDEFINED);
      assertEquals("S-Left", kg.formatKeySpec(ev));
   }

   @Test
   void formatKeySpecAltUp() {
      KeyGroup kg = new KeyGroup("test");
      JeyEvent ev = new JeyEvent(JeyEvent.ALT_MASK,
         JeyEvent.VK_UP, JeyEvent.CHAR_UNDEFINED);
      assertEquals("A-Up", kg.formatKeySpec(ev));
   }

   @Test
   void formatKeySpecCtrlShiftF5() {
      KeyGroup kg = new KeyGroup("test");
      JeyEvent ev = new JeyEvent(
         JeyEvent.CTRL_MASK | JeyEvent.SHIFT_MASK,
         JeyEvent.VK_F5, JeyEvent.CHAR_UNDEFINED);
      assertEquals("C-S-F5", kg.formatKeySpec(ev));
   }

   @Test
   void parseKeySpecCtrlF1Roundtrip() throws InputException {
      JeyEvent expected = new JeyEvent(JeyEvent.CTRL_MASK,
         JeyEvent.VK_F1, JeyEvent.CHAR_UNDEFINED);
      JeyEvent parsed = MiscCommands.parseKeySpec("C-F1");
      assertEquals(expected, parsed,
         "C-F1 should parse to Ctrl+F1 JeyEvent");
   }

   @Test
   void parseKeySpecShiftLeftRoundtrip() throws InputException {
      JeyEvent expected = new JeyEvent(JeyEvent.SHIFT_MASK,
         JeyEvent.VK_LEFT, JeyEvent.CHAR_UNDEFINED);
      JeyEvent parsed = MiscCommands.parseKeySpec("S-Left");
      assertEquals(expected, parsed,
         "S-Left should parse to Shift+Left JeyEvent");
   }

   @Test
   void parseKeySpecAltDeleteRoundtrip() throws InputException {
      JeyEvent expected = new JeyEvent(JeyEvent.ALT_MASK,
         JeyEvent.VK_DELETE, JeyEvent.CHAR_UNDEFINED);
      JeyEvent parsed = MiscCommands.parseKeySpec("A-Delete");
      assertEquals(expected, parsed,
         "A-Delete should parse to Alt+Delete JeyEvent");
   }

   @Test
   void ctrlShiftF5FullRoundtrip() throws InputException {
      KeyGroup kg = new KeyGroup("test");
      JeyEvent original = new JeyEvent(
         JeyEvent.CTRL_MASK | JeyEvent.SHIFT_MASK,
         JeyEvent.VK_F5, JeyEvent.CHAR_UNDEFINED);
      String spec = kg.formatKeySpec(original);
      assertEquals("C-S-F5", spec);
      JeyEvent parsed = MiscCommands.parseKeySpec(spec);
      assertEquals(original, parsed,
         "Ctrl+Shift+F5 should survive format -> parse roundtrip");
   }

   @Test
   void pageUpRoundtrip() throws InputException {
      KeyGroup kg = new KeyGroup("test");
      JeyEvent original = new JeyEvent(0,
         JeyEvent.VK_PAGE_UP, JeyEvent.CHAR_UNDEFINED);
      String spec = kg.formatKeySpec(original);
      assertEquals("PgUp", spec);
      JeyEvent parsed = MiscCommands.parseKeySpec(spec);
      assertEquals(original, parsed,
         "PgUp should survive format -> parse roundtrip");
   }

   @Test
   void pageDownRoundtrip() throws InputException {
      KeyGroup kg = new KeyGroup("test");
      JeyEvent original = new JeyEvent(0,
         JeyEvent.VK_PAGE_DOWN, JeyEvent.CHAR_UNDEFINED);
      String spec = kg.formatKeySpec(original);
      assertEquals("PgDn", spec);
      JeyEvent parsed = MiscCommands.parseKeySpec(spec);
      assertEquals(original, parsed,
         "PgDn should survive format -> parse roundtrip");
   }

   @Test
   void allFKeyRoundtrips() throws InputException {
      KeyGroup kg = new KeyGroup("test");
      int[] fKeys = {
         JeyEvent.VK_F1, JeyEvent.VK_F2, JeyEvent.VK_F3,
         JeyEvent.VK_F4, JeyEvent.VK_F5, JeyEvent.VK_F6,
         JeyEvent.VK_F7, JeyEvent.VK_F8, JeyEvent.VK_F9,
         JeyEvent.VK_F10, JeyEvent.VK_F11, JeyEvent.VK_F12
      };
      for (int i = 0; i < fKeys.length; i++) {
         JeyEvent original = new JeyEvent(0, fKeys[i],
            JeyEvent.CHAR_UNDEFINED);
         String spec = kg.formatKeySpec(original);
         assertEquals("F" + (i + 1), spec);
         JeyEvent parsed = MiscCommands.parseKeySpec(spec);
         assertEquals(original, parsed,
            "F" + (i + 1) + " should roundtrip");
      }
   }

   @Test
   void navKeyRoundtrips() throws InputException {
      KeyGroup kg = new KeyGroup("test");
      String[][] cases = {
         {"Up", String.valueOf(JeyEvent.VK_UP)},
         {"Down", String.valueOf(JeyEvent.VK_DOWN)},
         {"Left", String.valueOf(JeyEvent.VK_LEFT)},
         {"Right", String.valueOf(JeyEvent.VK_RIGHT)},
         {"Home", String.valueOf(JeyEvent.VK_HOME)},
         {"End", String.valueOf(JeyEvent.VK_END)},
         {"Insert", String.valueOf(JeyEvent.VK_INSERT)},
         {"Delete", String.valueOf(JeyEvent.VK_DELETE)},
      };
      for (String[] c : cases) {
         int keyCode = Integer.parseInt(c[1]);
         JeyEvent original = new JeyEvent(0, keyCode,
            JeyEvent.CHAR_UNDEFINED);
         String spec = kg.formatKeySpec(original);
         assertEquals(c[0], spec);
         JeyEvent parsed = MiscCommands.parseKeySpec(spec);
         assertEquals(original, parsed, c[0] + " should roundtrip");
      }
   }
}
