package javi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for {@link JeyEvent}, the editor's key event class.
 * Tests constructor logic, modifier handling, equality, hashing,
 * and the extended-modifier conversion utility.
 */
class JeyEventJUnitTest {

   // --- constructor and getKeyChar / getKeyCode ---

   @Test
   @DisplayName("character key stores char as code")
   void characterKeyBasic() {
      JeyEvent ev = new JeyEvent(0, 0, 'a');
      assertEquals('a', ev.getKeyChar());
      assertEquals('a', ev.getKeyCode());
   }

   @Test
   @DisplayName("action key (CHAR_UNDEFINED) uses keyCode")
   void actionKeyBasic() {
      JeyEvent ev = new JeyEvent(0, JeyEvent.VK_F1,
         JeyEvent.CHAR_UNDEFINED);
      assertEquals(JeyEvent.CHAR_UNDEFINED, ev.getKeyChar());
      assertEquals(JeyEvent.VK_F1, ev.getKeyCode());
   }

   @Test
   @DisplayName("shift modifier stripped for regular characters")
   void shiftStrippedForRegularChar() {
      JeyEvent shifted = new JeyEvent(JeyEvent.SHIFT_MASK, 0, 'A');
      JeyEvent unshifted = new JeyEvent(0, 0, 'A');
      // Shift is stripped for chars other than space, backspace, ctrl-Z
      assertEquals(unshifted, shifted);
   }

   @Test
   @DisplayName("shift modifier preserved for space")
   void shiftPreservedForSpace() {
      JeyEvent shifted = new JeyEvent(JeyEvent.SHIFT_MASK, 0, ' ');
      JeyEvent unshifted = new JeyEvent(0, 0, ' ');
      assertNotEquals(unshifted, shifted);
   }

   @Test
   @DisplayName("shift modifier preserved for backspace (char 8)")
   void shiftPreservedForBackspace() {
      JeyEvent shifted = new JeyEvent(JeyEvent.SHIFT_MASK, 0, (char) 8);
      JeyEvent unshifted = new JeyEvent(0, 0, (char) 8);
      assertNotEquals(unshifted, shifted);
   }

   @Test
   @DisplayName("shift modifier preserved for ctrl-Z (char 26)")
   void shiftPreservedForCtrlZ() {
      JeyEvent shifted = new JeyEvent(JeyEvent.SHIFT_MASK, 0, (char) 26);
      JeyEvent unshifted = new JeyEvent(0, 0, (char) 26);
      assertNotEquals(unshifted, shifted);
   }

   @Test
   @DisplayName("ctrl modifier preserved for characters")
   void ctrlModifierPreserved() {
      JeyEvent ctrl = new JeyEvent(JeyEvent.CTRL_MASK, 0, 'a');
      JeyEvent plain = new JeyEvent(0, 0, 'a');
      assertNotEquals(plain, ctrl);
   }

   @Test
   @DisplayName("shift modifier preserved for action keys")
   void shiftPreservedForActionKey() {
      JeyEvent shifted = new JeyEvent(JeyEvent.SHIFT_MASK,
         JeyEvent.VK_LEFT, JeyEvent.CHAR_UNDEFINED);
      JeyEvent unshifted = new JeyEvent(0,
         JeyEvent.VK_LEFT, JeyEvent.CHAR_UNDEFINED);
      assertNotEquals(unshifted, shifted);
   }

   // --- equals ---

   @Test
   @DisplayName("same event equals itself")
   void equalsSameInstance() {
      JeyEvent ev = new JeyEvent(0, 0, 'x');
      assertEquals(ev, ev);
   }

   @Test
   @DisplayName("identical events are equal")
   void equalsIdentical() {
      JeyEvent a = new JeyEvent(JeyEvent.CTRL_MASK,
         JeyEvent.VK_F5, JeyEvent.CHAR_UNDEFINED);
      JeyEvent b = new JeyEvent(JeyEvent.CTRL_MASK,
         JeyEvent.VK_F5, JeyEvent.CHAR_UNDEFINED);
      assertEquals(a, b);
   }

   @Test
   @DisplayName("different keys are not equal")
   void equalsNotEqualDifferentCode() {
      JeyEvent a = new JeyEvent(0, 0, 'a');
      JeyEvent b = new JeyEvent(0, 0, 'b');
      assertNotEquals(a, b);
   }

   @Test
   @DisplayName("null is not equal to JeyEvent")
   void equalsNull() {
      JeyEvent ev = new JeyEvent(0, 0, 'a');
      assertNotEquals(null, ev);
      assertFalse(ev.equals(null));
   }

   @Test
   @DisplayName("JeyEvent not equal to non-JeyEvent")
   void equalsNonJeyEvent() {
      JeyEvent ev = new JeyEvent(0, 0, 'a');
      assertFalse(ev.equals("not a JeyEvent"));
   }

   // --- hashCode ---

   @Test
   @DisplayName("equal events have same hashCode")
   void hashCodeConsistent() {
      JeyEvent a = new JeyEvent(JeyEvent.ALT_MASK,
         JeyEvent.VK_UP, JeyEvent.CHAR_UNDEFINED);
      JeyEvent b = new JeyEvent(JeyEvent.ALT_MASK,
         JeyEvent.VK_UP, JeyEvent.CHAR_UNDEFINED);
      assertEquals(a.hashCode(), b.hashCode());
   }

   @Test
   @DisplayName("different events likely have different hashCode")
   void hashCodeDiffers() {
      JeyEvent a = new JeyEvent(0, 0, 'a');
      JeyEvent b = new JeyEvent(0, 0, 'z');
      // Not guaranteed, but very likely different for different chars
      assertNotEquals(a.hashCode(), b.hashCode());
   }

   // --- getModifiers ---

   @Test
   @DisplayName("getModifiers returns bitmask without ACT_MASK")
   void getModifiersBasic() {
      JeyEvent ev = new JeyEvent(
         JeyEvent.CTRL_MASK | JeyEvent.SHIFT_MASK,
         JeyEvent.VK_F1, JeyEvent.CHAR_UNDEFINED);
      int mods = ev.getModifiers();
      assertTrue((mods & JeyEvent.CTRL_MASK) != 0);
      assertTrue((mods & JeyEvent.SHIFT_MASK) != 0);
      assertEquals(0, mods & 16); // ACT_MASK=16 should not leak
   }

   @Test
   @DisplayName("getModifiers for plain character has no modifiers")
   void getModifiersPlainChar() {
      JeyEvent ev = new JeyEvent(0, 0, 'x');
      assertEquals(0, ev.getModifiers());
   }

   // --- convertExtendedModifiers ---

   @Test
   @DisplayName("convertExtendedModifiers maps SHIFT_DOWN_MASK")
   void convertShift() {
      assertEquals(JeyEvent.SHIFT_MASK,
         JeyEvent.convertExtendedModifiers(0x40));
   }

   @Test
   @DisplayName("convertExtendedModifiers maps CTRL_DOWN_MASK")
   void convertCtrl() {
      assertEquals(JeyEvent.CTRL_MASK,
         JeyEvent.convertExtendedModifiers(0x80));
   }

   @Test
   @DisplayName("convertExtendedModifiers maps META_DOWN_MASK")
   void convertMeta() {
      assertEquals(JeyEvent.META_MASK,
         JeyEvent.convertExtendedModifiers(0x100));
   }

   @Test
   @DisplayName("convertExtendedModifiers maps ALT_DOWN_MASK")
   void convertAlt() {
      assertEquals(JeyEvent.ALT_MASK,
         JeyEvent.convertExtendedModifiers(0x200));
   }

   @Test
   @DisplayName("convertExtendedModifiers handles combined modifiers")
   void convertCombined() {
      int ext = 0x40 | 0x80 | 0x100 | 0x200; // all four
      int expected = JeyEvent.SHIFT_MASK | JeyEvent.CTRL_MASK
         | JeyEvent.META_MASK | JeyEvent.ALT_MASK;
      assertEquals(expected,
         JeyEvent.convertExtendedModifiers(ext));
   }

   @Test
   @DisplayName("convertExtendedModifiers returns 0 for no modifiers")
   void convertNone() {
      assertEquals(0, JeyEvent.convertExtendedModifiers(0));
   }

   // --- toString ---

   @Test
   @DisplayName("toString includes modifier and code info")
   void toStringFormat() {
      JeyEvent ev = new JeyEvent(JeyEvent.CTRL_MASK, 0, 'c');
      String str = ev.toString();
      assertTrue(str.contains("JeyEvent"));
   }

   // --- VK constants ---

   @Test
   @DisplayName("VK constants have expected values")
   void vkConstants() {
      assertEquals(155, JeyEvent.VK_INSERT);
      assertEquals(33, JeyEvent.VK_PAGE_UP);
      assertEquals(34, JeyEvent.VK_PAGE_DOWN);
      assertEquals(37, JeyEvent.VK_LEFT);
      assertEquals(38, JeyEvent.VK_UP);
      assertEquals(39, JeyEvent.VK_RIGHT);
      assertEquals(40, JeyEvent.VK_DOWN);
      assertEquals(112, JeyEvent.VK_F1);
      assertEquals(127, JeyEvent.VK_DELETE);
   }
}
