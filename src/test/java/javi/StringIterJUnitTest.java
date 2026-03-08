package javi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StringIter} — Iterator&lt;String&gt; adapter.
 */
class StringIterJUnitTest {

   @Test
   void iteratesOverStrings() {
      List<String> list = List.of("a", "b", "c");
      StringIter si = new StringIter(list.iterator());
      assertEquals("a", si.next());
      assertEquals("b", si.next());
      assertEquals("c", si.next());
      assertFalse(si.hasNext());
   }

   @Test
   void emptyIterator() {
      StringIter si = new StringIter(Collections.emptyIterator());
      assertFalse(si.hasNext());
   }

   @Test
   void convertsNonStringsViaToString() {
      List<Integer> nums = List.of(1, 2, 3);
      @SuppressWarnings("unchecked")
      StringIter si = new StringIter(nums.iterator());
      assertEquals("1", si.next());
      assertEquals("2", si.next());
      assertEquals("3", si.next());
   }

   @Test
   void singleElement() {
      StringIter si = new StringIter(List.of("only").iterator());
      assertTrue(si.hasNext());
      assertEquals("only", si.next());
      assertFalse(si.hasNext());
   }

   @Test
   void removeThrowsUnsupported() {
      StringIter si = new StringIter(List.of("x").iterator());
      assertThrows(UnsupportedOperationException.class, si::remove);
   }

   @Test
   void hasNextDoesNotAdvance() {
      StringIter si = new StringIter(List.of("a", "b").iterator());
      assertTrue(si.hasNext());
      assertTrue(si.hasNext()); // second call should still be true
      assertEquals("a", si.next());
   }

   @Test
   void nextOnExhaustedThrows() {
      StringIter si = new StringIter(Collections.emptyIterator());
      assertThrows(NoSuchElementException.class, si::next);
   }
}
