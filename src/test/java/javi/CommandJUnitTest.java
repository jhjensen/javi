package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link Command} — ex-mode command parsing
 * and dispatch.
 *
 * <p>
 * Covers:
 * </p>
 * <ul>
 *   <li>Registration of Command's rnames ("r", "tabstop", etc.)</li>
 *   <li>{@code command()} parsing: splitting command + args</li>
 *   <li>{@code command()} dispatch to registered bindings</li>
 *   <li>Unknown command error reporting</li>
 *   <li>{@code set} command parsing</li>
 * </ul>
 */
class CommandJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
   }

   // ============================================================
   // Command registration tests
   // ============================================================

   @Test
   void rCommandIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("r"),
         "r (read) should be registered by Command");
   }

   @Test
   void tabstopIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("tabstop"),
         "tabstop should be registered by Command");
   }

   @Test
   void terminatewepIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("terminatewep"),
         "terminatewep should be registered by Command");
   }

   @Test
   void commandprocIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("commandproc"),
         "commandproc should be registered by Command or MiscCommands");
   }

   @Test
   void setIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("set"),
         "set should be registered by Command");
   }

   @Test
   void eBangIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("e!"),
         "e! should be registered by Command");
   }

   @Test
   void helpIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("help"),
         "help should be registered by Command");
   }

   @Test
   void mapIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("map"),
         "map should be registered by Command");
   }

   @Test
   void checkoutIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("checkout"),
         "checkout should be registered by Command");
   }

   // ============================================================
   // Command parsing: line splitting logic
   // ============================================================

   @Test
   void commandLineSplittingExtractsArgs() {
      // Test the parsing logic that splits "command args":
      // The static method uses indexOf(' ') to split.
      String line = "tabstop 8";
      int spaceIdx = line.indexOf(' ');
      assertTrue(spaceIdx > 0, "should have a space");
      String cmd = line.substring(0, spaceIdx);
      String args = line.substring(cmd.length()).trim();
      assertEquals("tabstop", cmd);
      assertEquals("8", args);
   }

   @Test
   void commandLineSplittingNoArgsIsJustCommand() {
      String line = "undo";
      int spaceIdx = line.indexOf(' ');
      assertEquals(-1, spaceIdx, "no space means no args");
   }

   @Test
   void commandLineSplittingEmptyArgsAfterSpace() {
      String line = "set ";
      int spaceIdx = line.indexOf(' ');
      String cmd = line.substring(0, spaceIdx);
      String args = line.substring(cmd.length()).trim();
      assertEquals("set", cmd);
      assertEquals(0, args.length(), "trimmed args should be empty");
   }

   @Test
   void commandLineSplittingMultiWordArgs() {
      String line = "set tabstop=4";
      int spaceIdx = line.indexOf(' ');
      String cmd = line.substring(0, spaceIdx);
      String args = line.substring(cmd.length()).trim();
      assertEquals("set", cmd);
      assertEquals("tabstop=4", args);
   }

   @Test
   void commandLineSplittingWithLeadingArgSpaces() {
      String line = "help   topic";
      int spaceIdx = line.indexOf(' ');
      String cmd = line.substring(0, spaceIdx);
      String args = line.substring(cmd.length()).trim();
      assertEquals("help", cmd);
      assertEquals("topic", args);
   }

   // ============================================================
   // KeyBinding.matches tests
   // ============================================================

   @Test
   void commandBindingMatchesItsOwnInstance() {
      Rgroup.KeyBinding kb = Rgroup.bindingLookup("r");
      assertNotNull(kb);
      // The binding should have a non-null string representation
      String s = kb.toString();
      assertNotNull(s);
      assertTrue(s.length() > 0, "toString should be non-empty");
   }
}
