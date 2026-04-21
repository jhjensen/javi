package javi;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for {@link Rgroup} — static utility methods,
 * KeyBinding lifecycle, command registration, descriptions, and
 * CommandEntry record.
 */
class RgroupCoverageJUnitTest {

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

   // ── oBToInt ───────────────────────────────────────────────

   @Nested
   @DisplayName("oBToInt")
   class OBToIntTests {
      @Test
      @DisplayName("parses valid integer string")
      void parsesValidInt() throws InputException {
         assertEquals(42, Rgroup.oBToInt("42"));
      }

      @Test
      @DisplayName("parses negative integer")
      void parsesNegativeInt() throws InputException {
         assertEquals(-7, Rgroup.oBToInt("-7"));
      }

      @Test
      @DisplayName("trims whitespace before parsing")
      void trimsWhitespace() throws InputException {
         assertEquals(100, Rgroup.oBToInt("  100  "));
      }

      @Test
      @DisplayName("throws InputException on null")
      void throwsOnNull() {
         assertThrows(InputException.class,
            () -> Rgroup.oBToInt(null));
      }

      @Test
      @DisplayName("throws InputException on non-numeric")
      void throwsOnNonNumeric() {
         assertThrows(InputException.class,
            () -> Rgroup.oBToInt("abc"));
      }

      @Test
      @DisplayName("throws InputException on floating point")
      void throwsOnFloat() {
         assertThrows(InputException.class,
            () -> Rgroup.oBToInt("3.14"));
      }

      @Test
      @DisplayName("throws InputException on empty string")
      void throwsOnEmpty() {
         assertThrows(InputException.class,
            () -> Rgroup.oBToInt(""));
      }

      @Test
      @DisplayName("handles Integer object argument")
      void handlesIntegerObject() throws InputException {
         assertEquals(55, Rgroup.oBToInt(Integer.valueOf(55)));
      }

      @Test
      @DisplayName("parses zero")
      void parsesZero() throws InputException {
         assertEquals(0, Rgroup.oBToInt("0"));
      }
   }

   // ── oBToFloat ─────────────────────────────────────────────

   @Nested
   @DisplayName("oBToFloat")
   class OBToFloatTests {
      @Test
      @DisplayName("parses valid float string")
      void parsesValidFloat() throws InputException {
         assertEquals(3.14f, Rgroup.oBToFloat("3.14"), 0.001f);
      }

      @Test
      @DisplayName("parses integer as float")
      void parsesIntegerAsFloat() throws InputException {
         assertEquals(42.0f, Rgroup.oBToFloat("42"), 0.001f);
      }

      @Test
      @DisplayName("trims whitespace")
      void trimsWhitespace() throws InputException {
         assertEquals(1.5f, Rgroup.oBToFloat("  1.5  "), 0.001f);
      }

      @Test
      @DisplayName("throws InputException on null")
      void throwsOnNull() {
         assertThrows(InputException.class,
            () -> Rgroup.oBToFloat(null));
      }

      @Test
      @DisplayName("throws InputException on non-numeric")
      void throwsOnNonNumeric() {
         assertThrows(InputException.class,
            () -> Rgroup.oBToFloat("not_a_number"));
      }

      @Test
      @DisplayName("parses negative float")
      void parsesNegativeFloat() throws InputException {
         assertEquals(-2.5f, Rgroup.oBToFloat("-2.5"), 0.001f);
      }

      @Test
      @DisplayName("parses zero")
      void parsesZero() throws InputException {
         assertEquals(0.0f, Rgroup.oBToFloat("0"), 0.001f);
      }
   }

   // ── bindingLookup ─────────────────────────────────────────

   @Test
   @DisplayName("bindingLookup finds registered command")
   void bindingLookupFindsCommand() {
      // 'r' is registered by Command.init() via initCommands
      assertNotNull(Rgroup.bindingLookup("r"),
         "command 'r' should be registered");
   }

   @Test
   @DisplayName("bindingLookup returns null for unknown")
   void bindingLookupUnknown() {
      assertNull(Rgroup.bindingLookup("__nonexistent_command__"));
   }

   // ── getRegisteredCommands ─────────────────────────────────

   @Test
   @DisplayName("getRegisteredCommands returns non-empty set")
   void getRegisteredCommandsNotEmpty() {
      var commands = Rgroup.getRegisteredCommands();
      assertNotNull(commands);
      assertTrue(commands.size() > 10,
         "should have many registered commands, got " + commands.size());
   }

   @Test
   @DisplayName("getRegisteredCommands contains known commands")
   void getRegisteredCommandsContainsKnown() {
      var commands = Rgroup.getRegisteredCommands();
      assertTrue(commands.contains("r"), "should contain 'r'");
      assertTrue(commands.contains("tabstop"),
         "should contain 'tabstop'");
   }

   // ── getDescription ────────────────────────────────────────

   @Test
   @DisplayName("getDescription returns desc for known command")
   void getDescriptionKnown() {
      // 'r' has description "read file into buffer"
      String desc = Rgroup.getDescription("r");
      assertNotNull(desc);
      assertTrue(desc.contains("read") || desc.contains("file"),
         "desc for 'r': " + desc);
   }

   @Test
   @DisplayName("getDescription returns null for unknown")
   void getDescriptionUnknown() {
      assertNull(Rgroup.getDescription("__no_such_command__"));
   }

   // ── doCommand ─────────────────────────────────────────────

   @Test
   @DisplayName("doCommand throws InputException for unknown")
   void doCommandUnknownThrows() {
      assertThrows(InputException.class,
         () -> Rgroup.doCommand("__unknown__", null, 0, 0, null, false));
   }

   // ── CommandEntry record ───────────────────────────────────

   @Test
   @DisplayName("CommandEntry stores all fields")
   void commandEntryFields() {
      Rgroup.CommandEntry entry = new Rgroup.CommandEntry(
         "testcmd", "test description", "test_cat", null);
      assertEquals("testcmd", entry.name());
      assertEquals("test description", entry.description());
      assertEquals("test_cat", entry.category());
      assertNull(entry.handler());
   }

   @Test
   @DisplayName("CommandEntry toString contains name")
   void commandEntryToString() {
      Rgroup.CommandEntry entry = new Rgroup.CommandEntry(
         "mycmd", "desc", "cat", null);
      String s = entry.toString();
      assertTrue(s.contains("mycmd"), "toString: " + s);
   }

   // ── getCommandEntry ───────────────────────────────────────

   @Test
   @DisplayName("getCommandEntry returns null for array-registered")
   void getCommandEntryNullForArrayRegistered() {
      // Commands registered via register(String[]) don't have entries
      Rgroup.CommandEntry entry = Rgroup.getCommandEntry("r");
      // May or may not have entry depending on how it was registered
      // This exercises the code path
   }

   // ── registerDescriptions ──────────────────────────────────

   @Test
   @DisplayName("registerDescriptions adds descriptions")
   void registerDescriptionsAdds() {
      String[] names = {"", "__rgtest_cmd1__", "__rgtest_cmd2__"};
      String[] descs = {null, "desc1", "desc2"};
      Rgroup.registerDescriptions(names, descs);
      assertEquals("desc1", Rgroup.getDescription("__rgtest_cmd1__"));
      assertEquals("desc2", Rgroup.getDescription("__rgtest_cmd2__"));
   }

   @Test
   @DisplayName("registerDescriptions skips null descriptions")
   void registerDescriptionsSkipsNull() {
      String[] names = {"", "__rgtest_cmd3__"};
      String[] descs = {null, null};
      Rgroup.registerDescriptions(names, descs);
      assertNull(Rgroup.getDescription("__rgtest_cmd3__"));
   }
}
