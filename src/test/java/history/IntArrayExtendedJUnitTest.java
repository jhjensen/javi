package history;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Extended edge-case coverage for {@link IntArray}.
 */
class IntArrayExtendedJUnitTest {

   // ── set beyond current length expands ────────────────────

   @Test
   void setBeyondLengthExpandsArray() {
      IntArray arr = new IntArray(4);
      arr.add(10);
      arr.add(20);
      // set beyond length expands backing array but does NOT update len,
      // so get() at the new index throws IndexOutOfBoundsException
      arr.set(10, 99);
      // original elements still accessible
      assertEquals(10, arr.get(0));
      assertEquals(20, arr.get(1));
      assertEquals(2, arr.size(), "set does not change logical size");
   }

   // ── get out of bounds ────────────────────────────────────

   @Test
   void getOutOfBoundsThrows() {
      IntArray arr = new IntArray(4);
      arr.add(10);
      assertThrows(IndexOutOfBoundsException.class,
         () -> arr.get(1));
   }

   @Test
   void getFromEmptyThrows() {
      IntArray arr = new IntArray(4);
      assertThrows(IndexOutOfBoundsException.class,
         () -> arr.get(0));
   }

   // ── removeRange edge cases ───────────────────────────────

   @Test
   void removeRangeFromEndReducesSize() {
      IntArray arr = new IntArray(8);
      arr.add(10);
      arr.add(20);
      arr.add(30);
      arr.add(40);

      arr.removeRange(2, 4);

      assertEquals(2, arr.size());
      assertEquals(10, arr.get(0));
      assertEquals(20, arr.get(1));
   }

   @Test
   void removeRangeAllElementsLeavesEmpty() {
      IntArray arr = new IntArray(4);
      arr.add(10);
      arr.add(20);
      arr.add(30);

      arr.removeRange(0, 3);
      assertEquals(0, arr.size());
   }

   @Test
   void removeRangeInvalidFromGreaterThanToThrows() {
      IntArray arr = new IntArray(4);
      arr.add(10);
      arr.add(20);
      assertThrows(IndexOutOfBoundsException.class,
         () -> arr.removeRange(2, 1));
   }

   @Test
   void removeRangeToBeyondLengthThrows() {
      IntArray arr = new IntArray(4);
      arr.add(10);
      arr.add(20);
      assertThrows(IndexOutOfBoundsException.class,
         () -> arr.removeRange(0, 5));
   }

   @Test
   void removeRangeEmptyRangeIsNoOp() {
      IntArray arr = new IntArray(4);
      arr.add(10);
      arr.add(20);

      arr.removeRange(1, 1); // from == to
      assertEquals(2, arr.size());
      assertEquals(10, arr.get(0));
      assertEquals(20, arr.get(1));
   }

   // ── clear ────────────────────────────────────────────────

   @Test
   void clearResetsToEmpty() {
      IntArray arr = new IntArray(4);
      arr.add(10);
      arr.add(20);
      arr.add(30);

      arr.clear();

      assertEquals(0, arr.size());
   }

   @Test
   void clearThenAddWorksNormally() {
      IntArray arr = new IntArray(4);
      arr.add(10);
      arr.add(20);
      arr.clear();

      arr.add(99);
      assertEquals(1, arr.size());
      assertEquals(99, arr.get(0));
   }

   // ── growth doubling ──────────────────────────────────────

   @Test
   void manyAddsGrowCorrectly() {
      IntArray arr = new IntArray(1);
      for (int i = 0; i < 100; i++)
         arr.add(i);

      assertEquals(100, arr.size());
      for (int i = 0; i < 100; i++)
         assertEquals(i, arr.get(i));
   }

   // ── set at exact boundary ────────────────────────────────

   @Test
   void setAtExactLengthThrowsArrayOutOfBounds() {
      IntArray arr = new IntArray(4);
      arr.add(10);
      arr.add(20);

      // set(index == len) hits a sizing edge case: new array size == len,
      // so store[index] is out of bounds
      assertThrows(ArrayIndexOutOfBoundsException.class,
         () -> arr.set(2, 30));
   }

   @Test
   void setWithinLengthUpdatesInPlace() {
      IntArray arr = new IntArray(4);
      arr.add(10);
      arr.add(20);
      arr.add(30);

      arr.set(0, 99);
      assertEquals(99, arr.get(0));
      assertEquals(3, arr.size());
   }
}
