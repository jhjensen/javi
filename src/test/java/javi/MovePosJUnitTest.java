package javi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MovePos} — position data class.
 */
class MovePosJUnitTest {

   @Test
   void constructorSetsFields() {
      MovePos mp = new MovePos(10, 20);
      assertEquals(10, mp.x);
      assertEquals(20, mp.y);
   }

   @Test
   void copyConstructor() {
      MovePos orig = new MovePos(3, 7);
      MovePos copy = new MovePos(orig);
      assertEquals(3, copy.x);
      assertEquals(7, copy.y);
   }

   @Test
   void copyConstructorIsIndependent() {
      MovePos orig = new MovePos(3, 7);
      MovePos copy = new MovePos(orig);
      orig.x = 99;
      assertEquals(3, copy.x, "copy should not be affected by original");
   }

   @Test
   void setUpdatesFields() {
      MovePos mp = new MovePos(0, 0);
      mp.set(5, 10);
      assertEquals(5, mp.x);
      assertEquals(10, mp.y);
   }

   @Test
   void toStringXZeroOmitsX() {
      MovePos mp = new MovePos(0, 42);
      assertEquals("(42)", mp.toString());
   }

   @Test
   void toStringXNonZeroIncludesBoth() {
      MovePos mp = new MovePos(5, 42);
      assertEquals("(5,42)", mp.toString());
   }

   @Test
   void toStringNegativeValues() {
      MovePos mp = new MovePos(-1, -5);
      assertEquals("(-1,-5)", mp.toString());
   }

   @Test
   void zeroPosition() {
      MovePos mp = new MovePos(0, 0);
      assertEquals("(0)", mp.toString());
   }

   @Test
   void setToZero() {
      MovePos mp = new MovePos(99, 88);
      mp.set(0, 0);
      assertEquals(0, mp.x);
      assertEquals(0, mp.y);
   }

   @Test
   void fieldsAreMutable() {
      MovePos mp = new MovePos(1, 2);
      mp.x = 100;
      mp.y = 200;
      assertEquals(100, mp.x);
      assertEquals(200, mp.y);
   }
}
