package javi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ChangeOpt} enum and abstract structure.
 */
class ChangeOptJUnitTest {

   @Test
   void opcodeValuesExist() {
      ChangeOpt.Opcode[] vals = ChangeOpt.Opcode.values();
      assertTrue(vals.length > 0);
   }

   @Test
   void opcodeContainsNoop() {
      assertNotNull(ChangeOpt.Opcode.valueOf("NOOP"));
   }

   @Test
   void opcodeContainsInsert() {
      assertNotNull(ChangeOpt.Opcode.valueOf("INSERT"));
   }

   @Test
   void opcodeContainsChange() {
      assertNotNull(ChangeOpt.Opcode.valueOf("CHANGE"));
   }

   @Test
   void opcodeContainsDelete() {
      assertNotNull(ChangeOpt.Opcode.valueOf("DELETE"));
   }

   @Test
   void opcodeContainsRedraw() {
      assertNotNull(ChangeOpt.Opcode.valueOf("REDRAW"));
   }

   @Test
   void opcodeContainsMscreen() {
      assertNotNull(ChangeOpt.Opcode.valueOf("MSCREEN"));
   }

   @Test
   void opcodeContainsBlinkCursor() {
      assertNotNull(ChangeOpt.Opcode.valueOf("BLINKCURSOR"));
   }

   @Test
   void opcodeCount() {
      assertEquals(7, ChangeOpt.Opcode.values().length);
   }

   @Test
   void opcodeOrdinals() {
      assertEquals(0, ChangeOpt.Opcode.NOOP.ordinal());
      assertEquals(1, ChangeOpt.Opcode.INSERT.ordinal());
      assertEquals(2, ChangeOpt.Opcode.CHANGE.ordinal());
      assertEquals(3, ChangeOpt.Opcode.DELETE.ordinal());
      assertEquals(4, ChangeOpt.Opcode.REDRAW.ordinal());
      assertEquals(5, ChangeOpt.Opcode.MSCREEN.ordinal());
      assertEquals(6, ChangeOpt.Opcode.BLINKCURSOR.ordinal());
   }

   @Test
   void valueOfRoundTrips() {
      for (ChangeOpt.Opcode op : ChangeOpt.Opcode.values()) {
         assertEquals(op, ChangeOpt.Opcode.valueOf(op.name()));
      }
   }

   @Test
   void invalidValueOfThrows() {
      assertThrows(IllegalArgumentException.class,
            () -> ChangeOpt.Opcode.valueOf("NONEXISTENT"));
   }
}
