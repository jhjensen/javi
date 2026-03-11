package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Tests for {@link BufInIoc} — buffered-reader-backed IoConverter.
 */
class BufInIocJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void lock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void unlock() {
      EventQueue.biglock2.unlock();
   }

   private BufInIoc<String> makeBufInIoc(String content) {
      BufferedReader reader = new BufferedReader(new StringReader(content));
      FileProperties<String> fp = new FileProperties<>(
         FileDescriptor.InternalFd.make("bufinIocTest"),
         StringIoc.converter);
      return new BufInIoc<>(fp, false, reader);
   }

   private String callGetLine(BufInIoc<?> ioc) throws Exception {
      Method m = BufInIoc.class.getDeclaredMethod("getLine");
      m.setAccessible(true);
      return (String) m.invoke(ioc);
   }

   // ── getLine tests ──────────────────────────────────────────

   @Test
   void getLineReadsSingleLine() throws Exception {
      BufInIoc<String> ioc = makeBufInIoc("hello\n");
      assertEquals("hello", callGetLine(ioc));
   }

   @Test
   void getLineReadsMultipleLines() throws Exception {
      BufInIoc<String> ioc = makeBufInIoc("line1\nline2\nline3\n");
      assertEquals("line1", callGetLine(ioc));
      assertEquals("line2", callGetLine(ioc));
      assertEquals("line3", callGetLine(ioc));
   }

   @Test
   void getLineReturnsNullAtEOF() throws Exception {
      BufInIoc<String> ioc = makeBufInIoc("only\n");
      assertEquals("only", callGetLine(ioc));
      assertNull(callGetLine(ioc));
   }

   @Test
   void getLineReturnsNullForEmptyContent() throws Exception {
      BufInIoc<String> ioc = makeBufInIoc("");
      assertNull(callGetLine(ioc));
   }

   @Test
   void getLineAfterNullKeepsReturningNull() throws Exception {
      BufInIoc<String> ioc = makeBufInIoc("x\n");
      callGetLine(ioc); // "x"
      callGetLine(ioc); // null (closes input)
      assertNull(callGetLine(ioc)); // input is null now
   }

   @Test
   void getLineHandlesNoTrailingNewline() throws Exception {
      BufInIoc<String> ioc = makeBufInIoc("no newline");
      assertEquals("no newline", callGetLine(ioc));
      assertNull(callGetLine(ioc));
   }

   @Test
   void getLineHandlesBlankLines() throws Exception {
      BufInIoc<String> ioc = makeBufInIoc("\n\n\n");
      assertEquals("", callGetLine(ioc));
      assertEquals("", callGetLine(ioc));
      assertEquals("", callGetLine(ioc));
      assertNull(callGetLine(ioc));
   }

   @Test
   void getLineHandlesLongLine() throws Exception {
      String longLine = "a".repeat(10000);
      BufInIoc<String> ioc = makeBufInIoc(longLine + "\n");
      assertEquals(longLine, callGetLine(ioc));
   }

   // ── dispose tests ──────────────────────────────────────────

   @Test
   void disposeClosesInput() throws Exception {
      BufInIoc<String> ioc = makeBufInIoc("data\n");
      ioc.dispose();
      // After dispose, getLine should return null (input is null)
      assertNull(callGetLine(ioc));
   }

   @Test
   void disposeWhenAlreadyNull() throws Exception {
      BufInIoc<String> ioc = makeBufInIoc("");
      callGetLine(ioc); // triggers EOF → sets input to null
      // dispose should not throw even with null input
      ioc.dispose();
   }

   // ── constructor tests ──────────────────────────────────────

   @Test
   void constructorSetsProperties() throws Exception {
      BufInIoc<String> ioc = makeBufInIoc("test\n");
      assertNotNull(ioc.prop);
   }

   @Test
   void constructorWithNullReader() throws Exception {
      FileProperties<String> fp = new FileProperties<>(
         FileDescriptor.InternalFd.make("nullReaderTest"),
         StringIoc.converter);
      BufInIoc<String> ioc = new BufInIoc<>(fp, false, null);
      // getLine should return null immediately since input is null
      assertNull(callGetLine(ioc));
   }

   @Test
   void toStringNotNull() throws Exception {
      BufInIoc<String> ioc = makeBufInIoc("test\n");
      assertNotNull(ioc.toString());
   }
}
