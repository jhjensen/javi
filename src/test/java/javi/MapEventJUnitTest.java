package javi;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link MapEvent} key binding and
 * {@link KeyGroup} key-to-command mapping.
 *
 * <p>
 * Tests key binding, lookup, and the {@code getAllBindings()}
 * documentation method WITHOUT calling {@code MapEvent.bindCommands()},
 * which requires AwtInterface-only commands.
 * </p>
 *
 * <p>
 * Instead, we create private {@link KeyGroup} instances and bind
 * commands that are registered by {@link TestInit#initCommands()}.
 * </p>
 */
class MapEventJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
   }

   // ============================================================
   // KeyGroup — keybind and lookup
   // ============================================================

   @Test
   void keybindAndLookupBasicChar() {
      KeyGroup kg = new KeyGroup();
      // "movechar" is registered by MoveGroup via initCommands
      kg.keybind('h', "movechar", Boolean.FALSE);

      JeyEvent ev = new JeyEvent(0, 0, 'h');
      Rgroup.KeyBinding kb = kg.get(ev);

      assertNotNull(kb, "should find binding for 'h'");
   }

   @Test
   void keybindAndLookupWithModifier() {
      KeyGroup kg = new KeyGroup();
      kg.keybind((char) 6, "movechar", Boolean.TRUE, JeyEvent.CTRL_MASK);

      JeyEvent ev = new JeyEvent(JeyEvent.CTRL_MASK, 0, (char) 6);
      Rgroup.KeyBinding kb = kg.get(ev);

      assertNotNull(kb, "should find Ctrl-F binding");
   }

   @Test
   void keyactionbindAndLookup() {
      KeyGroup kg = new KeyGroup();
      kg.keyactionbind(JeyEvent.VK_LEFT, "movechar", Boolean.FALSE, 0);

      JeyEvent ev = new JeyEvent(0, JeyEvent.VK_LEFT, JeyEvent.CHAR_UNDEFINED);
      Rgroup.KeyBinding kb = kg.get(ev);

      assertNotNull(kb, "should find VK_LEFT binding");
   }

   @Test
   void lookupMissingKeyReturnsNull() {
      KeyGroup kg = new KeyGroup();
      kg.keybind('a', "movechar", Boolean.FALSE);

      JeyEvent ev = new JeyEvent(0, 0, 'z');
      Rgroup.KeyBinding kb = kg.get(ev);

      assertNull(kb, "lookup for unbound key should return null");
   }

   @Test
   void sameCharDifferentModifiersAreDistinct() {
      KeyGroup kg = new KeyGroup();
      kg.keybind('x', "movechar", Boolean.FALSE);
      kg.keybind('x', "moveline", Boolean.TRUE, JeyEvent.CTRL_MASK);

      JeyEvent plain = new JeyEvent(0, 0, 'x');
      JeyEvent ctrl = new JeyEvent(JeyEvent.CTRL_MASK, 0, 'x');

      assertNotNull(kg.get(plain));
      assertNotNull(kg.get(ctrl));
      // They should be different bindings
      assertFalse(kg.get(plain).toString().equals(
         kg.get(ctrl).toString()),
         "different modifiers should map to different bindings");
   }

   // ============================================================
   // KeyGroup — getBindingList
   // ============================================================

   @Test
   void getBindingListContainsEntries() {
      KeyGroup kg = new KeyGroup();
      kg.keybind('w', "forwardword", null);
      kg.keybind('b', "backwardword", null);

      List<String> bindings = kg.getBindingList();
      assertNotNull(bindings);
      assertEquals(2, bindings.size());
   }

   @Test
   void getBindingListIsSorted() {
      KeyGroup kg = new KeyGroup();
      kg.keybind('z', "movechar", Boolean.FALSE);
      kg.keybind('a', "moveline", Boolean.TRUE);

      List<String> bindings = kg.getBindingList();
      assertTrue(bindings.get(0).compareTo(bindings.get(1)) <= 0,
         "binding list should be sorted");
   }

   @Test
   void getBindingListEmptyForEmptyGroup() {
      KeyGroup kg = new KeyGroup();
      List<String> bindings = kg.getBindingList();
      assertNotNull(bindings);
      assertTrue(bindings.isEmpty(),
         "empty KeyGroup should return empty list");
   }

   // ============================================================
   // KeyBinding — toString
   // ============================================================

   @Test
   void keyBindingToStringFormatHasTwoPipes() {
      Rgroup.KeyBinding kb = Rgroup.bindingLookup("movechar");
      assertNotNull(kb);
      String s = kb.toString();
      long pipeCount = s.chars().filter(c -> c == '|').count();
      assertEquals(2, pipeCount,
         "toString format should be 'Rgroup|arg|index' with two pipes");
   }

   @Test
   void keyBindingProtoChangesArg() {
      Rgroup.KeyBinding original = Rgroup.bindingLookup("movechar");
      assertNotNull(original);
      Rgroup.KeyBinding proto = original.proto(Boolean.TRUE);
      assertNotNull(proto);
      // proto with a different arg should produce a new binding
      assertTrue(proto.toString().contains("true"),
         "proto binding should reflect the new arg");
   }

   @Test
   void keyBindingProtoSameArgReturnsSelf() {
      // When proto is called with same arg, it returns the same binding
      Rgroup.KeyBinding original = Rgroup.bindingLookup("movechar");
      assertNotNull(original);
      // The default arg for registered commands is null
      Rgroup.KeyBinding proto = original.proto(null);
      // Should return the same object since arg matches
      assertEquals(original, proto,
         "proto with same arg should return same binding");
   }

   // ============================================================
   // JeyEvent — equality and hashing
   // ============================================================

   @Test
   void jeyEventEqualsSameParams() {
      JeyEvent a = new JeyEvent(0, 0, 'j');
      JeyEvent b = new JeyEvent(0, 0, 'j');
      assertEquals(a, b, "same params should be equal");
      assertEquals(a.hashCode(), b.hashCode(),
         "equal events should have same hashCode");
   }

   @Test
   void jeyEventNotEqualsDifferentChar() {
      JeyEvent a = new JeyEvent(0, 0, 'j');
      JeyEvent b = new JeyEvent(0, 0, 'k');
      assertFalse(a.equals(b),
         "different chars should not be equal");
   }

   @Test
   void jeyEventNotEqualsDifferentModifiers() {
      JeyEvent a = new JeyEvent(0, 0, 'x');
      JeyEvent b = new JeyEvent(JeyEvent.CTRL_MASK, 0, 'x');
      assertFalse(a.equals(b),
         "different modifiers should not be equal");
   }

   @Test
   void jeyEventActionKeyVsCharKey() {
      JeyEvent action = new JeyEvent(0, JeyEvent.VK_LEFT,
         JeyEvent.CHAR_UNDEFINED);
      JeyEvent charKey = new JeyEvent(0, 0, 'h');
      assertFalse(action.equals(charKey),
         "action key and char key should not be equal");
   }

   @Test
   void jeyEventGetKeyCharForCharEvent() {
      JeyEvent ev = new JeyEvent(0, 0, 'x');
      assertEquals('x', ev.getKeyChar());
   }

   @Test
   void jeyEventGetKeyCharForActionEvent() {
      JeyEvent ev = new JeyEvent(0, JeyEvent.VK_F1,
         JeyEvent.CHAR_UNDEFINED);
      assertEquals(JeyEvent.CHAR_UNDEFINED, ev.getKeyChar(),
         "action events should return CHAR_UNDEFINED");
   }

   @Test
   void jeyEventToStringContainsInfo() {
      JeyEvent ev = new JeyEvent(JeyEvent.CTRL_MASK, 0, 'a');
      String s = ev.toString();
      assertNotNull(s);
      assertTrue(s.contains("JeyEvent"),
         "toString should contain class name");
   }
}
