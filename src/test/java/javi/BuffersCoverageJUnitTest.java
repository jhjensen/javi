package javi;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage for {@link Buffers} — fold span tracking,
 * uppercase append with ArrayList, multi-line deleted patterns,
 * CircBuffer wrapping edge cases, and appendCurrBuf.
 */
class BuffersCoverageJUnitTest {

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

   /** Minimal CircBuffer for testing — setclip is a no-op. */
   private static final class TestCircBuffer extends Buffers.CircBuffer {
      @Override
      public void setclip() {
         // no-op
      }
   }

   private void initBuffers() {
      Buffers.init(new TestCircBuffer());
   }

   // ── Fold span tracking ────────────────────────────────────

   @Test
   @DisplayName("initial fold span is 0")
   void initialFoldSpanZero() {
      Buffers.clearFoldSpan();
      assertEquals(0, Buffers.getLastFoldSpan());
   }

   @Test
   @DisplayName("setLastFoldSpan stores value")
   void setFoldSpanStores() {
      Buffers.setLastFoldSpan(42);
      assertEquals(42, Buffers.getLastFoldSpan());
   }

   @Test
   @DisplayName("clearFoldSpan resets to 0")
   void clearFoldSpanResetsToZero() {
      Buffers.setLastFoldSpan(99);
      Buffers.clearFoldSpan();
      assertEquals(0, Buffers.getLastFoldSpan());
   }

   @Test
   @DisplayName("setLastFoldSpan with negative value")
   void setFoldSpanNegative() {
      Buffers.setLastFoldSpan(-1);
      assertEquals(-1, Buffers.getLastFoldSpan());
   }

   // ── Named buffer store/retrieve with lowercase ────────────

   @Test
   @DisplayName("deleted(lowercase) stores string, getbuf retrieves")
   void lowercaseStoreRetrieve() {
      initBuffers();
      Buffers.deleted('a', "hello");
      assertEquals("hello", Buffers.getbuf('a'));
   }

   @Test
   @DisplayName("deleted(lowercase) overwrites previous value")
   void lowercaseOverwrites() {
      initBuffers();
      Buffers.deleted('b', "first");
      Buffers.deleted('b', "second");
      assertEquals("second", Buffers.getbuf('b'));
   }

   @Test
   @DisplayName("getbuf for unset register returns null")
   void getbufUnsetReturnsNull() {
      initBuffers();
      assertNull(Buffers.getbuf('z'));
   }

   // ── Uppercase append (String to String) ───────────────────

   @Test
   @DisplayName("uppercase append concatenates strings")
   void uppercaseAppendStrings() {
      initBuffers();
      Buffers.deleted('c', "first");
      Buffers.deleted('C', "second");
      assertEquals("firstsecond", Buffers.getbuf('c'));
   }

   @Test
   @DisplayName("uppercase append to empty register creates new")
   void uppercaseAppendToEmpty() {
      initBuffers();
      Buffers.deleted('D', "value");
      assertEquals("value", Buffers.getbuf('d'));
   }

   // ── ArrayList deleted ─────────────────────────────────────

   @Test
   @DisplayName("deleted(char, ArrayList) stores list")
   void deletedArrayListStores() {
      initBuffers();
      ArrayList<String> lines = new ArrayList<>();
      lines.add("line1");
      lines.add("line2");
      Buffers.deleted('e', lines);
      Object result = Buffers.getbuf('e');
      assertNotNull(result);
      assertTrue(result instanceof ArrayList);
   }

   @Test
   @DisplayName("deleted(char, ArrayList) with uppercase appends to list")
   void deletedArrayListUppercaseAppends() {
      initBuffers();
      ArrayList<String> first = new ArrayList<>();
      first.add("a");
      Buffers.deleted('f', first);
      ArrayList<String> second = new ArrayList<>();
      second.add("b");
      Buffers.deleted('F', second);
      Object result = Buffers.getbuf('f');
      assertTrue(result instanceof ArrayList);
      @SuppressWarnings("unchecked")
      ArrayList<String> list = (ArrayList<String>) result;
      assertTrue(list.size() >= 2);
      assertTrue(list.contains("a"));
      assertTrue(list.contains("b"));
   }

   @Test
   @DisplayName("deleted(char, ArrayList) uppercase over string prepends")
   void deletedArrayListUppercaseOverString() {
      initBuffers();
      Buffers.deleted('g', "existing");
      ArrayList<String> newLines = new ArrayList<>();
      newLines.add("added");
      Buffers.deleted('G', newLines);
      Object result = Buffers.getbuf('g');
      assertTrue(result instanceof ArrayList);
      @SuppressWarnings("unchecked")
      ArrayList<String> list = (ArrayList<String>) result;
      assertEquals("existing", list.get(0));
      assertEquals("added", list.get(1));
   }

   @Test
   @DisplayName("deleted with null string is no-op")
   void deletedNullStringNoOp() {
      initBuffers();
      Buffers.deleted('h', "before");
      Buffers.deleted('h', (String) null);
      assertEquals("before", Buffers.getbuf('h'));
   }

   @Test
   @DisplayName("deleted with null ArrayList is no-op")
   void deletedNullArrayListNoOp() {
      initBuffers();
      Buffers.deleted('i', "before");
      Buffers.deleted('i', (ArrayList<String>) null);
      assertEquals("before", Buffers.getbuf('i'));
   }

   // ── CircBuffer (delete buffer via '0') ────────────────────

   @Test
   @DisplayName("deleted('0', string) goes to CircBuffer")
   void deleteBufferStoresString() {
      initBuffers();
      Buffers.deleted('0', "deleted line");
      Object result = Buffers.getbuf('0');
      assertEquals("deleted line", result);
   }

   @Test
   @DisplayName("CircBuffer wraps after 10 entries")
   void circBufferWraps() {
      initBuffers();
      for (int i = 0; i < 12; i++)
         Buffers.deleted('0', "line" + i);
      // getbuf('0') returns most recent, '1' returns previous, etc.
      assertEquals("line11", Buffers.getbuf('0'));
      assertEquals("line10", Buffers.getbuf('1'));
   }

   @Test
   @DisplayName("deleted('0', ArrayList) stores in CircBuffer")
   void deleteBufferStoresArrayList() {
      initBuffers();
      ArrayList<String> lines = new ArrayList<>();
      lines.add("multi1");
      lines.add("multi2");
      Buffers.deleted('0', lines);
      Object result = Buffers.getbuf('0');
      assertTrue(result instanceof ArrayList);
   }

   // ── myToString ────────────────────────────────────────────

   @Test
   @DisplayName("myToString with String returns same string")
   void myToStringString() {
      assertEquals("hello", Buffers.CircBuffer.myToString("hello"));
   }

   @Test
   @DisplayName("myToString with ArrayList joins with newlines")
   void myToStringArrayList() {
      ArrayList<String> lines = new ArrayList<>();
      lines.add("a");
      lines.add("b");
      String result = Buffers.CircBuffer.myToString(lines);
      assertEquals("a\nb\n", result);
   }

   @Test
   @DisplayName("myToString with other object calls toString")
   void myToStringOtherObject() {
      String result = Buffers.CircBuffer.myToString(Integer.valueOf(42));
      assertEquals("42", result);
   }

   @Test
   @DisplayName("myToString with single-element list")
   void myToStringSingleElementList() {
      ArrayList<String> lines = new ArrayList<>();
      lines.add("only");
      String result = Buffers.CircBuffer.myToString(lines);
      assertEquals("only\n", result);
   }

   // ── appendCurrBuf ─────────────────────────────────────────

   @Test
   @DisplayName("appendCurrBuf with string appends to builder")
   void appendCurrBufString() {
      initBuffers();
      Buffers.deleted('0', "data");
      StringBuilder sb = new StringBuilder();
      Buffers.appendCurrBuf(sb, false);
      assertEquals("data", sb.toString());
   }

   @Test
   @DisplayName("appendCurrBuf with ArrayList multiline")
   void appendCurrBufArrayListMultiline() {
      initBuffers();
      ArrayList<String> lines = new ArrayList<>();
      lines.add("line1");
      lines.add("line2");
      Buffers.deleted('0', lines);
      StringBuilder sb = new StringBuilder();
      Buffers.appendCurrBuf(sb, false);
      assertTrue(sb.toString().contains("line1"));
      assertTrue(sb.toString().contains("line2"));
      assertTrue(sb.toString().contains("\n"));
   }

   @Test
   @DisplayName("appendCurrBuf singleline joins with spaces")
   void appendCurrBufSingleline() {
      initBuffers();
      ArrayList<String> lines = new ArrayList<>();
      lines.add("a");
      lines.add("b");
      Buffers.deleted('0', lines);
      StringBuilder sb = new StringBuilder();
      Buffers.appendCurrBuf(sb, true);
      assertTrue(sb.toString().contains("a"));
      assertTrue(sb.toString().contains("b"));
      assertFalse(sb.toString().contains("\n"));
   }

   @Test
   @DisplayName("appendCurrBuf with null buffer is no-op")
   void appendCurrBufNullBuffer() {
      initBuffers();
      // Don't add anything to delete buffer
      // getbuf('0') after init should return null or empty
      StringBuilder sb = new StringBuilder("existing");
      Buffers.appendCurrBuf(sb, false);
      // Should not throw and existing content preserved
      assertTrue(sb.toString().startsWith("existing"));
   }

   // ── getbuf case mapping ───────────────────────────────────

   @Test
   @DisplayName("getbuf('A') returns same as getbuf('a')")
   void getbufUppercaseMapsToLowercase() {
      initBuffers();
      Buffers.deleted('j', "value");
      assertEquals(Buffers.getbuf('j'), Buffers.getbuf('J'));
   }

   // ── CircBuffer.flush ──────────────────────────────────────

   @Test
   @DisplayName("CircBuffer flush clears all entries")
   void circBufferFlush() {
      TestCircBuffer cb = new TestCircBuffer();
      Buffers.init(cb);
      Buffers.deleted('0', "item");
      cb.flush();
      // After flush, get(0) should return null
      assertNull(cb.get(0));
   }
}
