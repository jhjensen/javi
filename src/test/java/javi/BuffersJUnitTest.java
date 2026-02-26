package javi;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link Buffers} — the named buffer (register)
 * management system.
 *
 * <p>
 * Covers:
 * </p>
 * <ul>
 *   <li>Named buffer store/retrieve for lowercase letters</li>
 *   <li>Uppercase letter append behavior</li>
 *   <li>Numeric (delete) buffer via CircBuffer</li>
 *   <li>CircBuffer wrapping and get offsets</li>
 *   <li>{@code myToString} conversion</li>
 *   <li>{@code appendCurrBuf} helper</li>
 * </ul>
 */
class BuffersJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   /**
    * Minimal CircBuffer for testing — setclip is a no-op.
    */
   private static final class TestCircBuf extends Buffers.CircBuffer {
      public void setclip() {
         // no clipboard interaction needed in tests
      }
   }

   // ============================================================
   // Named buffer (letter) tests
   // ============================================================

   @Test
   void deletedStoresStringInLowercaseBuffer() {
      Buffers.init(new TestCircBuf());

      Buffers.deleted('a', "hello");
      Object result = Buffers.getbuf('a');

      assertNotNull(result);
      assertEquals("hello", result.toString());
   }

   @Test
   void deletedOverwritesLowercaseBuffer() {
      Buffers.init(new TestCircBuf());

      Buffers.deleted('b', "first");
      Buffers.deleted('b', "second");

      Object result = Buffers.getbuf('b');
      assertEquals("second", result.toString());
   }

   @Test
   void deletedUppercaseAppendsToExistingString() {
      Buffers.init(new TestCircBuf());

      Buffers.deleted('c', "start");
      Buffers.deleted('C', "end");

      Object result = Buffers.getbuf('c');
      assertEquals("startend", result.toString());
   }

   @Test
   void deletedUppercaseCreatesNewWhenEmpty() {
      Buffers.init(new TestCircBuf());

      Buffers.deleted('D', "only");
      Object result = Buffers.getbuf('d');

      assertEquals("only", result.toString());
   }

   @Test
   void getbufUppercaseLookupNormalizesToLowercase() {
      Buffers.init(new TestCircBuf());

      Buffers.deleted('e', "data");
      Object result = Buffers.getbuf('E');

      assertNotNull(result);
      assertEquals("data", result.toString());
   }

   @Test
   void getbufUnsetBufferReturnsNull() {
      Buffers.init(new TestCircBuf());

      assertNull(Buffers.getbuf('z'));
   }

   @Test
   void deletedNullStringIsIgnored() {
      Buffers.init(new TestCircBuf());

      Buffers.deleted('a', (String) null);
      assertNull(Buffers.getbuf('a'));
   }

   // ============================================================
   // ArrayList buffer tests
   // ============================================================

   @Test
   void deletedArrayListStoresInLowercaseBuffer() {
      Buffers.init(new TestCircBuf());

      ArrayList<String> lines = new ArrayList<>();
      lines.add("line1");
      lines.add("line2");
      Buffers.deleted('f', lines);

      Object result = Buffers.getbuf('f');
      assertTrue(result instanceof ArrayList);
      @SuppressWarnings("unchecked")
      ArrayList<String> arr = (ArrayList<String>) result;
      assertEquals(2, arr.size());
      assertEquals("line1", arr.get(0));
   }

   @Test
   void deletedUppercaseArrayListAppendsToExistingList() {
      Buffers.init(new TestCircBuf());

      ArrayList<String> first = new ArrayList<>();
      first.add("line1");
      Buffers.deleted('g', first);

      ArrayList<String> second = new ArrayList<>();
      second.add("line2");
      Buffers.deleted('G', second);

      Object result = Buffers.getbuf('g');
      assertTrue(result instanceof ArrayList);
      @SuppressWarnings("unchecked")
      ArrayList<String> arr = (ArrayList<String>) result;
      assertEquals(2, arr.size());
      assertEquals("line1", arr.get(0));
      assertEquals("line2", arr.get(1));
   }

   @Test
   void deletedUppercaseArrayListAppendsToExistingString() {
      Buffers.init(new TestCircBuf());

      Buffers.deleted('h', "prefix");

      ArrayList<String> list = new ArrayList<>();
      list.add("suffix");
      Buffers.deleted('H', list);

      Object result = Buffers.getbuf('h');
      assertTrue(result instanceof ArrayList,
         "string + arraylist append should produce ArrayList");
      @SuppressWarnings("unchecked")
      ArrayList<String> arr = (ArrayList<String>) result;
      assertEquals("prefix", arr.get(0));
      assertEquals("suffix", arr.get(1));
   }

   @Test
   void deletedNullArrayListIsIgnored() {
      Buffers.init(new TestCircBuf());

      Buffers.deleted('a', (ArrayList<String>) null);
      assertNull(Buffers.getbuf('a'));
   }

   // ============================================================
   // CircBuffer (delete buffer / digit register) tests
   // ============================================================

   @Test
   void deleteBufferStoresAndRetrievesViaDigitZero() {
      TestCircBuf cb = new TestCircBuf();
      Buffers.init(cb);

      Buffers.deleted('0', "deleted-line");
      Object result = Buffers.getbuf('0');

      assertEquals("deleted-line", result.toString());
   }

   @Test
   void deleteBufferStacksMultipleDeletes() {
      TestCircBuf cb = new TestCircBuf();
      Buffers.init(cb);

      Buffers.deleted('0', "first");
      Buffers.deleted('0', "second");
      Buffers.deleted('0', "third");

      // offset 0 = most recent
      assertEquals("third", Buffers.getbuf('0').toString());
      // offset 1 = previous
      assertEquals("second", cb.get(1).toString());
      // offset 2 = oldest of 3
      assertEquals("first", cb.get(2).toString());
   }

   @Test
   void circBufferWrapsAroundCapacity() {
      TestCircBuf cb = new TestCircBuf();
      Buffers.init(cb);

      // circSize is 10 — add 12 items to force wrap
      for (int i = 0; i < 12; i++) {
         cb.add("item" + i);
      }
      // Most recent is item11
      assertEquals("item11", cb.get(0).toString());
      // 9 back is item2
      assertEquals("item2", cb.get(9).toString());
   }

   @Test
   void circBufferFlushClearsAllEntries() {
      TestCircBuf cb = new TestCircBuf();
      cb.add("a");
      cb.add("b");

      cb.flush();

      assertNull(cb.get(0));
      assertNull(cb.get(1));
   }

   // ============================================================
   // myToString tests
   // ============================================================

   @Test
   void myToStringConvertsString() {
      assertEquals("hello", Buffers.CircBuffer.myToString("hello"));
   }

   @Test
   void myToStringConvertsArrayList() {
      ArrayList<String> lines = new ArrayList<>();
      lines.add("alpha");
      lines.add("beta");

      String result = Buffers.CircBuffer.myToString(lines);
      assertEquals("alpha\nbeta\n", result);
   }

   @Test
   void myToStringConvertsGenericObject() {
      Object obj = Integer.valueOf(42);
      assertEquals("42", Buffers.CircBuffer.myToString(obj));
   }

   // ============================================================
   // appendCurrBuf tests
   // ============================================================

   @Test
   void appendCurrBufAppendsStringContent() {
      TestCircBuf cb = new TestCircBuf();
      Buffers.init(cb);
      Buffers.deleted('0', "linedata");

      StringBuilder sb = new StringBuilder();
      Buffers.appendCurrBuf(sb, false);

      assertEquals("linedata", sb.toString());
   }

   @Test
   void appendCurrBufAppendsArrayListWithNewlines() {
      TestCircBuf cb = new TestCircBuf();
      Buffers.init(cb);

      ArrayList<String> lines = new ArrayList<>();
      lines.add("one");
      lines.add("two");
      Buffers.deleted('0', lines);

      StringBuilder sb = new StringBuilder();
      Buffers.appendCurrBuf(sb, false);

      assertEquals("one\ntwo\n", sb.toString());
   }

   @Test
   void appendCurrBufSingleLineUsesSpace() {
      TestCircBuf cb = new TestCircBuf();
      Buffers.init(cb);

      ArrayList<String> lines = new ArrayList<>();
      lines.add("one");
      lines.add("two");
      Buffers.deleted('0', lines);

      StringBuilder sb = new StringBuilder();
      Buffers.appendCurrBuf(sb, true);

      assertEquals("one two ", sb.toString());
   }

   @Test
   void appendCurrBufEmptyBufferAppendsNothing() {
      TestCircBuf cb = new TestCircBuf();
      Buffers.init(cb);

      StringBuilder sb = new StringBuilder("prefix");
      Buffers.appendCurrBuf(sb, false);

      assertEquals("prefix", sb.toString());
   }
}
