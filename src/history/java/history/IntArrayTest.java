package history;

import static history.Tools.trace;

/**
 * Unit tests for IntArray class.
 * Run via: make intarraytest
 */
public final class IntArrayTest {

   private static int testCount = 0;
   private static int passCount = 0;

   public static void main(String[] args) throws Exception {
      trace("IntArray unit tests starting");

      testBasicAddAndGet();
      testGrowth();
      testSet();
      testRemoveRange();
      testRemoveRangeMiddle();

      trace("Tests complete: " + passCount + "/" + testCount + " passed");

      if (passCount != testCount) {
         throw new RuntimeException("Some tests failed!");
      }
   }

   static void testBasicAddAndGet() {
      testCount++;
      IntArray arr = new IntArray(4);

      arr.add(10);
      arr.add(20);
      arr.add(30);

      assert arr.size() == 3 : "size should be 3";
      assert arr.get(0) == 10 : "get(0) should be 10";
      assert arr.get(1) == 20 : "get(1) should be 20";
      assert arr.get(2) == 30 : "get(2) should be 30";

      trace("testBasicAddAndGet: PASS");
      passCount++;
   }

   static void testGrowth() {
      testCount++;
      IntArray arr = new IntArray(2);

      // Add more than initial capacity
      for (int i = 0; i < 10; i++) {
         arr.add(i * 10);
      }

      assert arr.size() == 10 : "size should be 10";
      for (int i = 0; i < 10; i++) {
         assert arr.get(i) == i * 10 : "get(" + i + ") should be " + (i * 10);
      }

      trace("testGrowth: PASS");
      passCount++;
   }

   static void testSet() {
      testCount++;
      IntArray arr = new IntArray(4);

      arr.add(10);
      arr.add(20);
      arr.add(30);

      arr.set(1, 99);
      assert arr.get(1) == 99 : "get(1) should be 99 after set";

      trace("testSet: PASS");
      passCount++;
   }

   static void testRemoveRange() {
      testCount++;
      IntArray arr = new IntArray(8);

      // [10, 20, 30, 40, 50]
      arr.add(10);
      arr.add(20);
      arr.add(30);
      arr.add(40);
      arr.add(50);

      trace("Before removeRange(0,2): size=" + arr.size());
      for (int i = 0; i < arr.size(); i++) {
         trace("  arr[" + i + "]=" + arr.get(i));
      }

      // Remove first two elements: should become [30, 40, 50]
      arr.removeRange(0, 2);

      trace("After removeRange(0,2): size=" + arr.size());
      for (int i = 0; i < arr.size(); i++) {
         trace("  arr[" + i + "]=" + arr.get(i));
      }

      assert arr.size() == 3 : "size should be 3 after removing 2 elements, got " + arr.size();
      assert arr.get(0) == 30 : "get(0) should be 30, got " + arr.get(0);
      assert arr.get(1) == 40 : "get(1) should be 40, got " + arr.get(1);
      assert arr.get(2) == 50 : "get(2) should be 50, got " + arr.get(2);

      trace("testRemoveRange: PASS");
      passCount++;
   }

   static void testRemoveRangeMiddle() {
      testCount++;
      IntArray arr = new IntArray(8);

      // [10, 20, 30, 40, 50]
      arr.add(10);
      arr.add(20);
      arr.add(30);
      arr.add(40);
      arr.add(50);

      trace("Before removeRange(1,3): size=" + arr.size());
      for (int i = 0; i < arr.size(); i++) {
         trace("  arr[" + i + "]=" + arr.get(i));
      }

      // Remove middle elements (indices 1,2): should become [10, 40, 50]
      arr.removeRange(1, 3);

      trace("After removeRange(1,3): size=" + arr.size());
      for (int i = 0; i < arr.size(); i++) {
         trace("  arr[" + i + "]=" + arr.get(i));
      }

      assert arr.size() == 3 : "size should be 3, got " + arr.size();
      assert arr.get(0) == 10 : "get(0) should be 10, got " + arr.get(0);
      assert arr.get(1) == 40 : "get(1) should be 40, got " + arr.get(1);
      assert arr.get(2) == 50 : "get(2) should be 50, got " + arr.get(2);

      trace("testRemoveRangeMiddle: PASS");
      passCount++;
   }
}
