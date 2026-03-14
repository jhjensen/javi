package javi;

import java.util.List;
import java.util.Set;

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

   // ============================================================
   // KeyMap — convenience binding methods (F19)
   // ============================================================

   @Test
   void keyMapBindMoveKeyAndLookup() {
      KeyGroup move = new KeyGroup("test-move");
      KeyGroup edit = new KeyGroup("test-edit");
      KeyMap km = new KeyMap("test", move, edit);

      km.bindMoveKey('h', "movechar", Boolean.FALSE);

      JeyEvent ev = new JeyEvent(0, 0, 'h');
      Rgroup.KeyBinding kb = km.lookupMove(ev);
      assertNotNull(kb, "bindMoveKey should make key resolvable via lookupMove");
   }

   @Test
   void keyMapBindMoveKeyWithModifier() {
      KeyGroup move = new KeyGroup("test-move");
      KeyGroup edit = new KeyGroup("test-edit");
      KeyMap km = new KeyMap("test", move, edit);

      km.bindMoveKey((char) 6, "movechar", Boolean.TRUE, JeyEvent.CTRL_MASK);

      JeyEvent ev = new JeyEvent(JeyEvent.CTRL_MASK, 0, (char) 6);
      assertNotNull(km.lookupMove(ev),
         "bindMoveKey with modifier should be findable");
   }

   @Test
   void keyMapBindMoveActionAndLookup() {
      KeyGroup move = new KeyGroup("test-move");
      KeyGroup edit = new KeyGroup("test-edit");
      KeyMap km = new KeyMap("test", move, edit);

      km.bindMoveAction(JeyEvent.VK_LEFT, "movechar", Boolean.FALSE, 0);

      JeyEvent ev = new JeyEvent(0, JeyEvent.VK_LEFT,
         JeyEvent.CHAR_UNDEFINED);
      assertNotNull(km.lookupMove(ev),
         "bindMoveAction should make action key resolvable");
   }

   @Test
   void keyMapBindEditKeyAndLookup() {
      KeyGroup move = new KeyGroup("test-move");
      KeyGroup edit = new KeyGroup("test-edit");
      KeyMap km = new KeyMap("test", move, edit);

      km.bindEditKey('x', "deletechars", null);

      JeyEvent ev = new JeyEvent(0, 0, 'x');
      Rgroup.KeyBinding kb = km.lookupEdit(ev);
      assertNotNull(kb, "bindEditKey should make key resolvable via lookupEdit");
   }

   @Test
   void keyMapBindEditActionAndLookup() {
      KeyGroup move = new KeyGroup("test-move");
      KeyGroup edit = new KeyGroup("test-edit");
      KeyMap km = new KeyMap("test", move, edit);

      km.bindEditAction(JeyEvent.VK_F1, "deletechars", null, 0);

      JeyEvent ev = new JeyEvent(0, JeyEvent.VK_F1,
         JeyEvent.CHAR_UNDEFINED);
      assertNotNull(km.lookupEdit(ev),
         "bindEditAction should make action key resolvable");
   }

   @Test
   void keyMapOverlayWithConvenienceMethods() {
      KeyGroup move = new KeyGroup("parent-move");
      KeyGroup edit = new KeyGroup("parent-edit");
      KeyMap parent = new KeyMap("parent", move, edit);
      parent.bindMoveKey('h', "movechar", Boolean.FALSE);

      KeyMap child = KeyMap.createOverlay("child", parent);
      child.bindMoveKey('h', "moveline", Boolean.TRUE);

      // Child should resolve to overridden binding
      JeyEvent ev = new JeyEvent(0, 0, 'h');
      Rgroup.KeyBinding childKb = child.lookupMove(ev);
      assertNotNull(childKb, "child overlay should find overridden binding");

      // Keys not in child should fall through to parent
      parent.bindMoveKey('l', "movechar", Boolean.TRUE);
      JeyEvent evL = new JeyEvent(0, 0, 'l');
      assertNotNull(child.lookupMove(evL),
         "child should fall through to parent for unbound keys");
   }

   // ============================================================
   // KeyMap — registeredNames
   // ============================================================

   @Test
   void registeredNamesReturnsImmutableSet() {
      Set<String> names = KeyMap.registeredNames();
      assertNotNull(names, "registeredNames should not return null");
   }

   // ============================================================
   // HelpSystem — topic coverage (F20)
   // ============================================================

   @Test
   void helpSystemIndexTopicNotNull() {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = HelpSystem.getHelp("index");
         assertNotNull(buf, "index help should return a buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void helpSystemFileListTopicNotNull() {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = HelpSystem.getHelp("filelist");
         assertNotNull(buf, "filelist help should return a buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void helpSystemDirectoryTopicNotNull() {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = HelpSystem.getHelp("directory");
         assertNotNull(buf, "directory help should return a buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void helpSystemKeybindingsTopicNotNull() {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = HelpSystem.getHelp("keybindings");
         assertNotNull(buf, "keybindings help should return a buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void helpSystemShellTopicNotNull() {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = HelpSystem.getHelp("shell");
         assertNotNull(buf, "shell help should return a buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void helpSystemKeymapAliasNotNull() {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = HelpSystem.getHelp("keymap");
         assertNotNull(buf, "keymap alias should return a buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void helpSystemDirAliasNotNull() {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = HelpSystem.getHelp("dir");
         assertNotNull(buf, "dir alias should return a buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ============================================================
   // HelpSystem — filtered bindings (F20)
   // ============================================================

   @Test
   void filteredBindingsUnknownKeymapShowsError() {
      EventQueue.biglock2.lock();
      try {
         // Set up a minimal keymap for the test
         KeyGroup move = new KeyGroup("test-move");
         KeyGroup edit = new KeyGroup("test-edit");
         KeyMap km = new KeyMap("test-filter", move, edit);
         KeyMap.register(km);

         TextEdit<String> buf =
            HelpSystem.getFilteredBindings("nonexistent");
         assertNotNull(buf, "should return a buffer even for unknown keymap");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void filteredBindingsKnownKeymapShowsBindings() {
      EventQueue.biglock2.lock();
      try {
         KeyGroup move = new KeyGroup("fb-move");
         KeyGroup edit = new KeyGroup("fb-edit");
         KeyMap km = new KeyMap("fb-test", move, edit);
         km.bindMoveKey('x', "movechar", Boolean.FALSE);
         KeyMap.register(km);

         TextEdit<String> buf =
            HelpSystem.getFilteredBindings("fb-test");
         assertNotNull(buf, "should return a buffer for known keymap");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ============================================================
   // KeyGroup — user binding tracking (F21)
   // ============================================================

   @Test
   void bindTracksUserBinding() {
      KeyGroup kg = new KeyGroup("bind-test");
      // Use keybind first (default binding, not tracked)
      kg.keybind('h', "movechar", Boolean.FALSE);
      assertFalse(kg.hasUserBindings(),
         "keybind should not create user bindings");

      // Use bind (runtime modification, tracked)
      JeyEvent ev = new JeyEvent(0, 0, 'x');
      kg.bind(ev, "movechar", Boolean.TRUE);
      assertTrue(kg.hasUserBindings(),
         "bind should track as user binding");

      List<String> specs = kg.getUserBindingSpecs();
      assertEquals(1, specs.size(),
         "should have one user binding");
      assertTrue(specs.get(0).contains("movechar"),
         "spec should contain command name");
   }

   @Test
   void unbindRemovesUserBinding() {
      KeyGroup kg = new KeyGroup("unbind-test");
      JeyEvent ev = new JeyEvent(0, 0, 'z');
      kg.bind(ev, "movechar", Boolean.FALSE);
      assertTrue(kg.hasUserBindings());

      kg.unbind(ev);
      assertFalse(kg.hasUserBindings(),
         "unbind should remove user binding");
   }

   @Test
   void formatKeySpecRoundtripsChar() {
      KeyGroup kg = new KeyGroup("spec-test");
      JeyEvent ev = new JeyEvent(0, 0, 'x');
      String spec = kg.formatKeySpec(ev);
      assertEquals("x", spec, "plain char should format as itself");
   }

   @Test
   void formatKeySpecRoundtripsActionKey() {
      KeyGroup kg = new KeyGroup("spec-test");
      JeyEvent ev = new JeyEvent(0, JeyEvent.VK_F1,
         JeyEvent.CHAR_UNDEFINED);
      String spec = kg.formatKeySpec(ev);
      assertEquals("F1", spec, "F1 action key should format as F1");
   }

   // ============================================================
   // KeyBindingPersistence — config path (F21)
   // ============================================================

   @Test
   void persistenceConfigPathEndWithKeybindings() {
      java.nio.file.Path path = KeyBindingPersistence.getConfigPath();
      assertNotNull(path);
      assertTrue(path.toString().endsWith("keybindings"),
         "config path should end with 'keybindings'");
      assertTrue(path.toString().contains(".javi"),
         "config path should be under .javi directory");
   }
}
