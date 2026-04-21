package javi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage tests for {@link EditCache}.
 *
 * <p>Exercises edge cases in clear, addSome, addAll, rangeAsStrings,
 * getElementsAt, getArr, contains, indexOf, set, iterator, and
 * the array-based add overload.</p>
 */
class EditCacheCoverageJUnitTest {

   private EditCache<String> cache;

   @BeforeEach
   void setUp() {
      cache = new EditCache<>();
   }

   // ── size / empty ──────────────────────────────────────────

   @Test
   @DisplayName("new cache has size 0")
   void newCacheIsEmpty() {
      assertEquals(0, cache.size());
   }

   @Test
   @DisplayName("add increases size")
   void addIncreasesSize() {
      cache.add("a");
      cache.add("b");
      assertEquals(2, cache.size());
   }

   // ── add(OType[]) ─────────────────────────────────────────

   @Test
   @DisplayName("add(array) adds all elements")
   void addArrayAddsAll() {
      cache.add(new String[]{"x", "y", "z"});
      assertEquals(3, cache.size());
      assertEquals("x", cache.get(0));
      assertEquals("y", cache.get(1));
      assertEquals("z", cache.get(2));
   }

   @Test
   @DisplayName("add(empty array) does not change size")
   void addEmptyArrayNoChange() {
      cache.add("pre");
      cache.add(new String[]{});
      assertEquals(1, cache.size());
   }

   // ── get / set ─────────────────────────────────────────────

   @Test
   @DisplayName("set replaces element at index")
   void setReplacesElement() {
      cache.add("a");
      cache.add("b");
      cache.set(0, "replaced");
      assertEquals("replaced", cache.get(0));
      assertEquals("b", cache.get(1));
   }

   @Test
   @DisplayName("get throws on out-of-bounds")
   void getOutOfBoundsThrows() {
      cache.add("only");
      assertThrows(IndexOutOfBoundsException.class,
         () -> cache.get(5));
   }

   // ── contains / indexOf ────────────────────────────────────

   @Test
   @DisplayName("contains uses identity comparison")
   void containsUsesIdentity() {
      String s = "hello";
      cache.add(s);
      assertTrue(cache.contains(s));
      // Different String object with same value should NOT match
      // (contains uses ==, not equals)
      assertFalse(cache.contains(new String("hello")));
   }

   @Test
   @DisplayName("indexOf returns correct index")
   void indexOfReturnsCorrectIndex() {
      String a = "a";
      String b = "b";
      String c = "c";
      cache.add(a);
      cache.add(b);
      cache.add(c);
      assertEquals(0, cache.indexOf(a));
      assertEquals(1, cache.indexOf(b));
      assertEquals(2, cache.indexOf(c));
   }

   @Test
   @DisplayName("indexOf returns -1 for missing element")
   void indexOfMissingReturnsNegative() {
      cache.add("only");
      assertEquals(-1, cache.indexOf("missing"));
   }

   // ── clear variants ────────────────────────────────────────

   @Test
   @DisplayName("clear() removes all elements")
   void clearRemovesAll() {
      cache.add("a");
      cache.add("b");
      cache.clear();
      assertEquals(0, cache.size());
   }

   @Test
   @DisplayName("clear(start) removes from start to end")
   void clearFromStartRemovesTail() {
      cache.add("a");
      cache.add("b");
      cache.add("c");
      cache.clear(1);
      assertEquals(1, cache.size());
      assertEquals("a", cache.get(0));
   }

   @Test
   @DisplayName("clear(start, end) removes range")
   void clearRangeRemovesSubset() {
      cache.add("a");
      cache.add("b");
      cache.add("c");
      cache.add("d");
      cache.clear(1, 3); // removes b, c
      assertEquals(2, cache.size());
      assertEquals("a", cache.get(0));
      assertEquals("d", cache.get(1));
   }

   @Test
   @DisplayName("clear(0) on populated cache empties it")
   void clearFromZeroEmpties() {
      cache.add("x");
      cache.add("y");
      cache.clear(0);
      assertEquals(0, cache.size());
   }

   // ── addAll variants ───────────────────────────────────────

   @Test
   @DisplayName("addAll(index, OType[]) inserts array at position")
   void addAllArrayAtIndex() {
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
   @DisplayName("addAll(index, EditCache) inserts cache contents")
   void addAllEditCacheAtIndex() {
      cache.add("a");
      cache.add("d");
      EditCache<String> other = new EditCache<>();
      other.add("b");
      other.add("c");
      cache.addAll(1, other);
      assertEquals(4, cache.size());
      assertEquals("b", cache.get(1));
      assertEquals("c", cache.get(2));
   }

   @Test
   @DisplayName("addAll(index, Collection) inserts at position")
   void addAllCollectionAtIndex() {
      cache.add("first");
      cache.add("last");
      cache.addAll(1, Arrays.asList("mid1", "mid2"));
      assertEquals(4, cache.size());
      assertEquals("mid1", cache.get(1));
      assertEquals("mid2", cache.get(2));
   }

   @Test
   @DisplayName("addAll(Collection) appends all")
   void addAllCollectionAppends() {
      cache.add("pre");
      cache.addAll(Arrays.asList("a", "b", "c"));
      assertEquals(4, cache.size());
      assertEquals("pre", cache.get(0));
      assertEquals("c", cache.get(3));
   }

   // ── addSome ───────────────────────────────────────────────

   @Test
   @DisplayName("addSome inserts subset from offset at index 1")
   void addSomeInsertsFromOffset() {
      cache.add("root");
      cache.add("tail");
      EditCache<String> source = new EditCache<>();
      source.add("skip0");
      source.add("skip1");
      source.add("want2");
      source.add("want3");
      cache.addSome(source, 2);
      // addSome inserts at index 1, elements from offset 2 onward
      assertEquals(4, cache.size());
      assertEquals("root", cache.get(0));
      assertEquals("want2", cache.get(1));
      assertEquals("want3", cache.get(2));
      assertEquals("tail", cache.get(3));
   }

   // ── rangeAsStrings ────────────────────────────────────────

   @Test
   @DisplayName("rangeAsStrings returns toString of range")
   void rangeAsStringsConverts() {
      cache.add("line1");
      cache.add("line2");
      cache.add("line3");
      ArrayList<String> result = cache.rangeAsStrings(0, 2);
      assertEquals(2, result.size());
      assertEquals("line1", result.get(0));
      assertEquals("line2", result.get(1));
   }

   @Test
   @DisplayName("rangeAsStrings with equal indices returns empty")
   void rangeAsStringsEmptyRange() {
      cache.add("only");
      ArrayList<String> result = cache.rangeAsStrings(0, 0);
      assertEquals(0, result.size());
   }

   // ── getElementsAt ─────────────────────────────────────────

   @Test
   @DisplayName("getElementsAt returns toString of range")
   void getElementsAtReturnsRange() {
      cache.add("a");
      cache.add("b");
      cache.add("c");
      ArrayList<String> result = cache.getElementsAt(1, 3);
      assertEquals(2, result.size());
      assertEquals("b", result.get(0));
      assertEquals("c", result.get(1));
   }

   @Test
   @DisplayName("getElementsAt with same start/end is empty")
   void getElementsAtEmptyRange() {
      cache.add("x");
      ArrayList<String> result = cache.getElementsAt(0, 0);
      assertEquals(0, result.size());
   }

   // ── getArr ────────────────────────────────────────────────

   @Test
   @DisplayName("getArr extracts Object array of count elements")
   void getArrExtractsSubset() {
      cache.add("a");
      cache.add("b");
      cache.add("c");
      cache.add("d");
      Object[] arr = cache.getArr(1, 2);
      assertEquals(2, arr.length);
      assertEquals("b", arr[0]);
      assertEquals("c", arr[1]);
   }

   @Test
   @DisplayName("getArr with count 0 returns empty array")
   void getArrZeroCount() {
      cache.add("x");
      Object[] arr = cache.getArr(0, 0);
      assertEquals(0, arr.length);
   }

   // ── iterator ──────────────────────────────────────────────

   @Test
   @DisplayName("iterator traverses all elements in order")
   void iteratorTraversesAll() {
      cache.add("a");
      cache.add("b");
      cache.add("c");
      List<String> collected = new ArrayList<>();
      for (String s : cache)
         collected.add(s);
      assertEquals(Arrays.asList("a", "b", "c"), collected);
   }

   @Test
   @DisplayName("iterator on empty cache has no elements")
   void iteratorOnEmptyCache() {
      Iterator<String> it = cache.iterator();
      assertFalse(it.hasNext());
   }

   // ── Integer cache ─────────────────────────────────────────

   @Test
   @DisplayName("EditCache works with non-String types")
   void worksWithIntegers() {
      EditCache<Integer> intCache = new EditCache<>();
      intCache.add(10);
      intCache.add(20);
      intCache.add(30);
      assertEquals(3, intCache.size());
      assertEquals(20, intCache.get(1));
      ArrayList<String> strs = intCache.rangeAsStrings(0, 3);
      assertEquals("10", strs.get(0));
      assertEquals("30", strs.get(2));
   }
}
