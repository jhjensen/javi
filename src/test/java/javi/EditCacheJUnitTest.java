package javi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for {@link EditCache}, an ArrayList-backed storage
 * used internally by EditContainer for text lines and other elements.
 */
class EditCacheJUnitTest {

   private EditCache<String> cache;

   @BeforeEach
   void setUp() {
      cache = new EditCache<>();
   }

   // --- add / size / get ---

   @Test
   @DisplayName("new cache is empty")
   void emptyCache() {
      assertEquals(0, cache.size());
   }

   @Test
   @DisplayName("add single element and retrieve it")
   void addAndGet() {
      cache.add("hello");
      assertEquals(1, cache.size());
      assertEquals("hello", cache.get(0));
   }

   @Test
   @DisplayName("add multiple elements preserves order")
   void addMultiple() {
      cache.add("a");
      cache.add("b");
      cache.add("c");
      assertEquals(3, cache.size());
      assertEquals("a", cache.get(0));
      assertEquals("b", cache.get(1));
      assertEquals("c", cache.get(2));
   }

   @Test
   @DisplayName("add array of elements")
   void addArray() {
      cache.add(new String[]{"x", "y", "z"});
      assertEquals(3, cache.size());
      assertEquals("y", cache.get(1));
   }

   // --- set ---

   @Test
   @DisplayName("set replaces element at index")
   void setElement() {
      cache.add("old");
      cache.set(0, "new");
      assertEquals("new", cache.get(0));
   }

   // --- clear ---

   @Test
   @DisplayName("clear() removes all elements")
   void clearAll() {
      cache.add("a");
      cache.add("b");
      cache.clear();
      assertEquals(0, cache.size());
   }

   @Test
   @DisplayName("clear(start) removes from start to end")
   void clearFromStart() {
      cache.add("a");
      cache.add("b");
      cache.add("c");
      cache.clear(1);
      assertEquals(1, cache.size());
      assertEquals("a", cache.get(0));
   }

   @Test
   @DisplayName("clear(start, end) removes range")
   void clearRange() {
      cache.add("a");
      cache.add("b");
      cache.add("c");
      cache.add("d");
      cache.clear(1, 3); // remove "b" and "c"
      assertEquals(2, cache.size());
      assertEquals("a", cache.get(0));
      assertEquals("d", cache.get(1));
   }

   // --- contains / indexOf ---

   @Test
   @DisplayName("contains returns true for same reference")
   void containsIdentity() {
      String s = "test";
      cache.add(s);
      assertTrue(cache.contains(s));
   }

   @Test
   @DisplayName("contains returns false when not present")
   void containsMissing() {
      cache.add("a");
      assertFalse(cache.contains("missing"));
   }

   @Test
   @DisplayName("indexOf returns correct position")
   void indexOfBasic() {
      cache.add("a");
      cache.add("b");
      cache.add("c");
      assertEquals(1, cache.indexOf("b"));
   }

   @Test
   @DisplayName("indexOf returns -1 for missing element")
   void indexOfMissing() {
      cache.add("a");
      assertEquals(-1, cache.indexOf("missing"));
   }

   // --- addAll ---

   @Test
   @DisplayName("addAll with index inserts array at position")
   void addAllAtIndex() {
      cache.add("a");
      cache.add("d");
      cache.addAll(1, new String[]{"b", "c"});
      assertEquals(4, cache.size());
      assertEquals("a", cache.get(0));
      assertEquals("b", cache.get(1));
      assertEquals("c", cache.get(2));
      assertEquals("d", cache.get(3));
   }

   @Test
   @DisplayName("addAll with Collection appends to end")
   void addAllCollection() {
      cache.add("existing");
      cache.addAll(Arrays.asList("x", "y"));
      assertEquals(3, cache.size());
      assertEquals("x", cache.get(1));
      assertEquals("y", cache.get(2));
   }

   @Test
   @DisplayName("addAll with EditCache inserts at index")
   void addAllEditCache() {
      cache.add("a");
      cache.add("c");
      EditCache<String> other = new EditCache<>();
      other.add("b");
      cache.addAll(1, other);
      assertEquals(3, cache.size());
      assertEquals("b", cache.get(1));
   }

   // --- getElementsAt ---

   @Test
   @DisplayName("getElementsAt returns string list for range")
   void getElementsAt() {
      cache.add("alpha");
      cache.add("beta");
      cache.add("gamma");
      ArrayList<String> result = cache.getElementsAt(0, 2);
      assertEquals(2, result.size());
      assertEquals("alpha", result.get(0));
      assertEquals("beta", result.get(1));
   }

   // --- rangeAsStrings ---

   @Test
   @DisplayName("rangeAsStrings returns string list for range")
   void rangeAsStrings() {
      cache.add("one");
      cache.add("two");
      cache.add("three");
      ArrayList<String> result = cache.rangeAsStrings(1, 3);
      assertEquals(2, result.size());
      assertEquals("two", result.get(0));
      assertEquals("three", result.get(1));
   }

   // --- getArr ---

   @Test
   @DisplayName("getArr returns Object array of elements")
   void getArr() {
      cache.add("a");
      cache.add("b");
      cache.add("c");
      Object[] arr = cache.getArr(1, 2);
      assertEquals(2, arr.length);
      assertEquals("b", arr[0]);
      assertEquals("c", arr[1]);
   }

   // --- iterator ---

   @Test
   @DisplayName("iterator traverses all elements")
   void iteratorBasic() {
      cache.add("x");
      cache.add("y");
      cache.add("z");
      Iterator<String> it = cache.iterator();
      assertTrue(it.hasNext());
      assertEquals("x", it.next());
      assertEquals("y", it.next());
      assertEquals("z", it.next());
      assertFalse(it.hasNext());
   }

   @Test
   @DisplayName("for-each loop works via Iterable")
   void forEachLoop() {
      cache.add("1");
      cache.add("2");
      cache.add("3");
      StringBuilder sb = new StringBuilder();
      for (String s : cache)
         sb.append(s);
      assertEquals("123", sb.toString());
   }

   // --- edge cases ---

   @Test
   @DisplayName("get throws on out of bounds index")
   void getOutOfBounds() {
      cache.add("only");
      assertThrows(IndexOutOfBoundsException.class,
         () -> cache.get(5));
   }

   @Test
   @DisplayName("clear on empty cache is safe")
   void clearEmpty() {
      assertDoesNotThrow(() -> cache.clear());
      assertEquals(0, cache.size());
   }

   @Test
   @DisplayName("addSome copies from offset of another cache")
   void addSomeCopiesFromOffset() {
      cache.add("sentinel");
      EditCache<String> source = new EditCache<>();
      source.add("zero");
      source.add("one");
      source.add("two");
      source.add("three");
      // addSome inserts at position 1, from offset to end
      cache.addSome(source, 2);
      // cache should be: sentinel, two, three
      assertEquals(3, cache.size());
      assertEquals("sentinel", cache.get(0));
      assertEquals("two", cache.get(1));
      assertEquals("three", cache.get(2));
   }
}
