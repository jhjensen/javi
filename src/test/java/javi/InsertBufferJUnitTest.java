package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.text.StringCharacterIterator;

/**
 * Tests for {@link InsertBuffer} — testable methods only.
 */
class InsertBufferJUnitTest {

   private static TestInsertBuffer ib;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
      TestInit.initCommands();
      ib = new TestInsertBuffer();
   }

   @BeforeEach
   void setUp() throws Exception {
      EventQueue.biglock2.lock();
      ib.resetCalled = false;
      // Clear internal buffer via reflection
      java.lang.reflect.Field bf =
         InsertBuffer.class.getDeclaredField("buffer");
      bf.setAccessible(true);
      ((StringBuilder) bf.get(ib)).setLength(0);
   }

   @AfterEach
   void tearDown() throws Exception {
      EventQueue.biglock2.unlock();
   }

   // Concrete subclass for testing
   private static class TestInsertBuffer extends InsertBuffer {
      boolean resetCalled = false;

      @Override
      public void insertReset() {
         resetCalled = true;
      }
   }

   // ── getString / insertChars tests ───────────────────────────

   @Test
   void getStringInitiallyEmpty() throws Exception {
      assertEquals("", ib.getString());
   }

   @Test
   void insertCharsAppendsText() throws Exception {
      ib.insertChars(new StringCharacterIterator("hello"), 0);
      assertEquals("hello", ib.getString());
   }

   @Test
   void insertCharsAppendsMultiple() throws Exception {
      ib.insertChars(new StringCharacterIterator("abc"), 0);
      ib.insertChars(new StringCharacterIterator("def"), 0);
      assertEquals("abcdef", ib.getString());
   }

   @Test
   void insertCharsHandlesNull() throws Exception {
      ib.insertChars(null, 0);
      assertEquals("", ib.getString());
   }

   @Test
   void insertCharsSingleChar() throws Exception {
      ib.insertChars(new StringCharacterIterator("x"), 0);
      assertEquals("x", ib.getString());
   }

   // ── isOverwrite / isActive tests ────────────────────────────

   @Test
   void isOverwriteInitiallyFalse() throws Exception {
      assertFalse(ib.isOverwrite());
   }

   @Test
   void isActiveInitiallyFalse() throws Exception {
      assertFalse(ib.isActive());
   }
}
