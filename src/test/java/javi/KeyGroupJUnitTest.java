package javi;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link KeyGroup} — modal key-to-command mapping.
 *
 * <p>Covers keybind(), keyactionbind(), get(), getBindingList(),
 * and duplicate-binding detection.</p>
 */
class KeyGroupJUnitTest {

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

   @Test
   @DisplayName("keybind stores and get retrieves char binding")
   void keybindAndGet() {
      KeyGroup kg = new KeyGroup();
      kg.keybind('x', "deletechars", null);

      JeyEvent ev = new JeyEvent(0, 0, 'x');
      Rgroup.KeyBinding kb = kg.get(ev);
      assertNotNull(kb, "binding for 'x' should exist");
   }

   @Test
   @DisplayName("keybind with modifiers stores and retrieves")
   void keybindWithModifiers() {
      KeyGroup kg = new KeyGroup();
      kg.keybind('l', "redraw", null, JeyEvent.CTRL_MASK);

      JeyEvent ev = new JeyEvent(JeyEvent.CTRL_MASK, 0, 'l');
      Rgroup.KeyBinding kb = kg.get(ev);
      assertNotNull(kb, "Ctrl-l binding should exist");
   }

   @Test
   @DisplayName("get returns null for unbound key")
   void getUnboundReturnsNull() {
      KeyGroup kg = new KeyGroup();
      JeyEvent ev = new JeyEvent(0, 0, 'z');
      assertNull(kg.get(ev));
   }

   @Test
   @DisplayName("keyactionbind stores action key binding")
   void keyactionbindAndGet() {
      KeyGroup kg = new KeyGroup();
      kg.keyactionbind(JeyEvent.VK_F1, "gotoline", null, 0);

      JeyEvent ev = new JeyEvent(0, JeyEvent.VK_F1,
         JeyEvent.CHAR_UNDEFINED);
      Rgroup.KeyBinding kb = kg.get(ev);
      assertNotNull(kb, "F1 binding should exist");
   }

   @Test
   @DisplayName("duplicate keybind throws RuntimeException")
   void duplicateKeybindThrows() {
      KeyGroup kg = new KeyGroup();
      kg.keybind('d', "deletechars", null);
      assertThrows(RuntimeException.class,
         () -> kg.keybind('d', "joinlines", null));
   }

   @Test
   @DisplayName("duplicate keyactionbind throws RuntimeException")
   void duplicateKeyactionbindThrows() {
      KeyGroup kg = new KeyGroup();
      kg.keyactionbind(JeyEvent.VK_F2, "moveline", null, 0);
      assertThrows(RuntimeException.class,
         () -> kg.keyactionbind(JeyEvent.VK_F2,
            "movechar", null, 0));
   }

   @Test
   @DisplayName("different modifiers for same char are distinct")
   void differentModifiersDistinct() {
      KeyGroup kg = new KeyGroup();
      kg.keybind('a', "append", null);
      kg.keybind('a', "Append", null, JeyEvent.CTRL_MASK);

      JeyEvent plain = new JeyEvent(0, 0, 'a');
      JeyEvent ctrl = new JeyEvent(JeyEvent.CTRL_MASK, 0, 'a');

      assertNotNull(kg.get(plain));
      assertNotNull(kg.get(ctrl));
   }

   @Test
   @DisplayName("getBindingList returns sorted list of bindings")
   void getBindingListNotEmpty() {
      KeyGroup kg = new KeyGroup();
      kg.keybind('j', "moveline", null);
      kg.keybind('k', "movechar", null);

      List<String> bindings = kg.getBindingList();
      assertNotNull(bindings);
      assertEquals(2, bindings.size());
      // List should be sorted
      assertTrue(bindings.get(0).compareTo(bindings.get(1)) <= 0,
         "binding list should be sorted");
   }

   @Test
   @DisplayName("getBindingList on empty KeyGroup returns empty list")
   void getBindingListEmpty() {
      KeyGroup kg = new KeyGroup();
      List<String> bindings = kg.getBindingList();
      assertNotNull(bindings);
      assertTrue(bindings.isEmpty());
   }

   @Test
   @DisplayName("getBindingList contains command names")
   void getBindingListContainsNames() {
      KeyGroup kg = new KeyGroup();
      kg.keybind('w', "forwardword", null);

      List<String> bindings = kg.getBindingList();
      assertEquals(1, bindings.size());
      assertTrue(bindings.get(0).contains("forwardword"));
   }

   @Test
   @DisplayName("action key and char key don't collide")
   void actionKeyAndCharKeyDistinct() {
      KeyGroup kg = new KeyGroup();
      kg.keybind('A', "Append", null);
      kg.keyactionbind(JeyEvent.VK_HOME, "starttext", null, 0);

      JeyEvent charEv = new JeyEvent(0, 0, 'A');
      JeyEvent actionEv = new JeyEvent(0, JeyEvent.VK_HOME,
         JeyEvent.CHAR_UNDEFINED);

      assertNotNull(kg.get(charEv));
      assertNotNull(kg.get(actionEv));
   }
}
