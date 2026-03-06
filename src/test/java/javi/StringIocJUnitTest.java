package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JUnit 5 tests for {@link StringIoc} — string-to-line splitting
 * I/O converter.
 *
 * <p>Covers single-line, multi-line, empty, and trailing-newline cases
 * for {@code getnext()}.</p>
 */
class StringIocJUnitTest {

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

   @Test
   @DisplayName("getnext returns single line for input without newline")
   void singleLineNoNewline() {
      StringIoc sio = new StringIoc("test1", "hello world");
      assertEquals("hello world", sio.getnext());
      assertNull(sio.getnext());
   }

   @Test
   @DisplayName("getnext splits on newlines")
   void multiLine() {
      StringIoc sio = new StringIoc("test2", "line1\nline2\nline3");
      assertEquals("line1", sio.getnext());
      assertEquals("line2", sio.getnext());
      assertEquals("line3", sio.getnext());
      assertNull(sio.getnext());
   }

   @Test
   @DisplayName("getnext handles empty string")
   void emptyString() {
      StringIoc sio = new StringIoc("test3", "");
      assertEquals("", sio.getnext());
      assertNull(sio.getnext());
   }

   @Test
   @DisplayName("getnext handles trailing newline")
   void trailingNewline() {
      StringIoc sio = new StringIoc("test4", "abc\n");
      assertEquals("abc", sio.getnext());
      assertEquals("", sio.getnext());
      assertNull(sio.getnext());
   }

   @Test
   @DisplayName("getnext handles leading newline")
   void leadingNewline() {
      StringIoc sio = new StringIoc("test5", "\nabc");
      assertEquals("", sio.getnext());
      assertEquals("abc", sio.getnext());
      assertNull(sio.getnext());
   }

   @Test
   @DisplayName("getnext handles consecutive newlines")
   void consecutiveNewlines() {
      StringIoc sio = new StringIoc("test6", "a\n\nb");
      assertEquals("a", sio.getnext());
      assertEquals("", sio.getnext());
      assertEquals("b", sio.getnext());
      assertNull(sio.getnext());
   }

   @Test
   @DisplayName("getnext returns null after all lines consumed")
   void exhaustedReturnsNull() {
      StringIoc sio = new StringIoc("test7", "only");
      sio.getnext();
      assertNull(sio.getnext());
      assertNull(sio.getnext()); // idempotent
   }

   @Test
   @DisplayName("prop is set from constructor")
   void propIsSet() {
      StringIoc sio = new StringIoc("proptest", "data");
      assertNotNull(sio.prop);
   }

   @Test
   @DisplayName("converter field is accessible")
   void converterAccessible() {
      assertNotNull(StringIoc.converter);
   }
}
