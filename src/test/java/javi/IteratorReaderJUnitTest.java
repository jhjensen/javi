package javi;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JUnit 5 tests for {@link IteratorReader} — a java.io.Reader that
 * wraps an Iterator of Strings, joining them with newlines.
 */
class IteratorReaderJUnitTest {

   private IteratorReader readerOf(String... lines) {
      return new IteratorReader(Arrays.asList(lines).iterator());
   }

   // ── single-char read() ───────────────────────────────────────

   @Nested
   @DisplayName("read() single char")
   class SingleCharRead {

      @Test
      @DisplayName("reads characters from single line")
      void singleLine() {
         IteratorReader r = readerOf("abc");
         assertEquals('a', r.read());
         assertEquals('b', r.read());
         assertEquals('c', r.read());
      }

      @Test
      @DisplayName("returns newline between iterator elements")
      void newlineBetweenElements() {
         IteratorReader r = readerOf("a", "b");
         assertEquals('a', r.read());
         assertEquals('\n', r.read());
         assertEquals('b', r.read());
      }

      @Test
      @DisplayName("returns -1 after all elements exhausted")
      void returnsMinusOneAtEnd() {
         IteratorReader r = readerOf("x");
         assertEquals('x', r.read());
         assertEquals(-1, r.read());
      }
   }

   // ── bulk read(char[], int, int) ──────────────────────────────

   @Nested
   @DisplayName("read(char[], off, len)")
   class BulkRead {

      @Test
      @DisplayName("reads full content into buffer")
      void fullContent() {
         IteratorReader r = readerOf("hello");
         char[] buf = new char[10];
         int n = r.read(buf, 0, 5);
         assertEquals(5, n);
         assertEquals("hello", new String(buf, 0, 5));
      }

      @Test
      @DisplayName("reads across iterator boundaries with newline")
      void acrossBoundary() {
         IteratorReader r = readerOf("ab", "cd");
         char[] buf = new char[10];
         // Read "ab" then newline
         int n1 = r.read(buf, 0, 3);
         assertEquals(3, n1);
         assertEquals('a', buf[0]);
         assertEquals('b', buf[1]);
         assertEquals('\n', buf[2]);
      }

      @Test
      @DisplayName("returns -1 when iterator is exhausted and no chars read")
      void minusOneWhenExhausted() {
         IteratorReader r = readerOf("x");
         char[] buf = new char[5];
         r.read(buf, 0, 1); // consume 'x'
         int n = r.read(buf, 0, 5);
         // After 'x' is consumed, next read should end iterator
         // The behavior returns remaining chars + newline, or -1
         // if no chars at all were read
         // (implementation: catches NoSuchElementException, returns
         // index+1 if index>0, or -1 if index==0)
         assertEquals(-1, n);
      }

      @Test
      @DisplayName("uses offset correctly")
      void respectsOffset() {
         IteratorReader r = readerOf("ab");
         char[] buf = new char[10];
         buf[0] = 'Z'; // should not be overwritten
         int n = r.read(buf, 1, 2);
         assertEquals(2, n);
         assertEquals('Z', buf[0]);
         assertEquals('a', buf[1]);
         assertEquals('b', buf[2]);
      }
   }

   // ── close() ──────────────────────────────────────────────────

   @Test
   @DisplayName("close nulls out internal state")
   void closeNullsState() throws IOException {
      IteratorReader r = readerOf("test");
      r.close();
      // After close, read() should throw NPE since it and str
      // are null
      assertThrows(NullPointerException.class, () -> r.read());
   }

   // ── empty iterator element ───────────────────────────────────

   @Nested
   @DisplayName("edge cases")
   class EdgeCases {

      @Test
      @DisplayName("empty string element produces newline separator")
      void emptyElement() {
         IteratorReader r = readerOf("a", "", "b");
         assertEquals('a', r.read());
         assertEquals('\n', r.read()); // transition to empty element
         assertEquals('\n', r.read()); // transition from empty to "b"
         assertEquals('b', r.read());
      }

      @Test
      @DisplayName("reads multi-line content correctly")
      void multiLineContent() {
         IteratorReader r = readerOf("line1", "line2", "line3");
         StringBuilder sb = new StringBuilder();
         int ch;
         while ((ch = r.read()) != -1) {
            sb.append((char) ch);
         }
         assertEquals("line1\nline2\nline3", sb.toString());
      }
   }
}
