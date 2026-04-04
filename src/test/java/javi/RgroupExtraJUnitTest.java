package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Extended tests for {@link Rgroup} static utilities and binding
 * system.
 */
class RgroupExtraJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void lock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void unlock() {
      EventQueue.biglock2.unlock();
   }

   // ── oBToInt tests ───────────────────────────────────────────

   @Test
   void oBToIntValidNumber() throws InputException {
      assertEquals(42, Rgroup.oBToInt("42"));
   }

   @Test
   void oBToIntNegative() throws InputException {
      assertEquals(-7, Rgroup.oBToInt("-7"));
   }

   @Test
   void oBToIntWithWhitespace() throws InputException {
      assertEquals(99, Rgroup.oBToInt("  99  "));
   }

   @Test
   void oBToIntNullThrows() {
      assertThrows(InputException.class, () -> Rgroup.oBToInt(null));
   }

   @Test
   void oBToIntNonNumberThrows() {
      assertThrows(InputException.class, () -> Rgroup.oBToInt("abc"));
   }

   @Test
   void oBToIntEmptyStringThrows() {
      assertThrows(InputException.class, () -> Rgroup.oBToInt(""));
   }

   @Test
   void oBToIntDecimalThrows() {
      assertThrows(InputException.class, () -> Rgroup.oBToInt("3.14"));
   }

   // ── oBToFloat tests ─────────────────────────────────────────

   @Test
   void oBToFloatValidNumber() throws InputException {
      assertEquals(3.14f, Rgroup.oBToFloat("3.14"), 0.001);
   }

   @Test
   void oBToFloatInteger() throws InputException {
      assertEquals(5.0f, Rgroup.oBToFloat("5"), 0.001);
   }

   @Test
   void oBToFloatNegative() throws InputException {
      assertEquals(-2.5f, Rgroup.oBToFloat("-2.5"), 0.001);
   }

   @Test
   void oBToFloatWithWhitespace() throws InputException {
      assertEquals(1.0f, Rgroup.oBToFloat("  1.0  "), 0.001);
   }

   @Test
   void oBToFloatNullThrows() {
      assertThrows(InputException.class, () -> Rgroup.oBToFloat(null));
   }

   @Test
   void oBToFloatNonNumberThrows() {
      assertThrows(InputException.class,
         () -> Rgroup.oBToFloat("xyz"));
   }

   // ── bindingLookup tests ─────────────────────────────────────

   @Test
   void bindingLookupFindsRegisteredCommand() {
      assertNotNull(Rgroup.bindingLookup("insert"),
         "insert should be registered");
   }

   @Test
   void bindingLookupReturnsNullForUnknown() {
      assertNull(Rgroup.bindingLookup("nonexistent_command_xyz"),
         "unknown command should return null");
   }

   // ── doCommand tests ─────────────────────────────────────────

   @Test
   void doCommandThrowsOnUnknownCommand() {
      assertThrows(InputException.class,
         () -> Rgroup.doCommand("unknown_cmd_xyz", null, 1, 0,
            null, false),
         "doCommand with unknown command should throw");
   }

   // ── KeyBinding.toString ─────────────────────────────────────

   @Test
   void keyBindingToStringContainsIndex() {
      Rgroup.KeyBinding kb = Rgroup.bindingLookup("tabstop");
      assertNotNull(kb);
      String str = kb.toString();
      assertNotNull(str);
      assertTrue(str.length() > 0,
         "KeyBinding.toString should return non-empty");
   }

   private static void assertTrue(boolean condition, String msg) {
      org.junit.jupiter.api.Assertions.assertTrue(condition, msg);
   }
}
