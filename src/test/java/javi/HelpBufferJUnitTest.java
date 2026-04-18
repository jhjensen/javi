package javi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link HelpBuffer} lifecycle and content management.
 */
class HelpBufferJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   private HelpBuffer hb;

   @BeforeEach
   void setUp() {
      EventQueue.biglock2.lock();
      hb = new HelpBuffer("*test-help*");
   }

   @AfterEach
   void tearDown() {
      EventQueue.biglock2.unlock();
   }

   @Test
   void getBufferNullBeforeEnsure() {
      assertNull(hb.getBuffer(),
         "getBuffer should be null before ensure()");
   }

   @Test
   void ensureCreatesBuffer() {
      hb.ensure();
      assertNotNull(hb.getBuffer(),
         "getBuffer should be non-null after ensure()");
   }

   @Test
   void ensureIdempotent() {
      hb.ensure();
      TextEdit<String> first = hb.getBuffer();
      hb.ensure();
      TextEdit<String> second = hb.getBuffer();
      assertEquals(first, second,
         "ensure() called twice should return same buffer");
   }

   @Test
   void appendAddsLine() {
      hb.ensure();
      hb.append("first line");
      TextEdit<String> buf = hb.getBuffer();
      boolean found = false;
      for (int i = 1; i < buf.finish(); i++) {
         if ("first line".equals(buf.at(i).toString())) {
            found = true;
            break;
         }
      }
      assertEquals(true, found,
         "buffer should contain appended line");
   }

   @Test
   void appendMultipleLines() {
      hb.ensure();
      hb.append("line A");
      hb.append("line B");
      hb.append("line C");
      TextEdit<String> buf = hb.getBuffer();
      // At least 3 content lines plus the initial empty line
      int contentLines = buf.finish() - 1;
      assertEquals(true, contentLines >= 3,
         "buffer should have at least 3 lines, got: " + contentLines);
   }

   @Test
   void clearRemovesContent() {
      hb.ensure();
      hb.append("line 1");
      hb.append("line 2");
      hb.append("line 3");
      hb.clear();
      TextEdit<String> buf = hb.getBuffer();
      // After clear, buffer should be mostly empty
      int contentLines = buf.finish() - 1;
      assertEquals(true, contentLines <= 2,
         "after clear, buffer should have <= 2 lines, got: "
            + contentLines);
   }

   @Test
   void clearThenAppend() {
      hb.ensure();
      hb.append("old content");
      hb.clear();
      hb.append("new content");
      TextEdit<String> buf = hb.getBuffer();
      boolean found = false;
      for (int i = 1; i < buf.finish(); i++) {
         if ("new content".equals(buf.at(i).toString())) {
            found = true;
            break;
         }
      }
      assertEquals(true, found,
         "after clear+append, buffer should contain new content");
   }

   @Test
   void differentNamesCreateIndependentBuffers() {
      HelpBuffer hb2 = new HelpBuffer("*other-help*");
      hb.ensure();
      hb2.ensure();
      hb.append("buf1 line");
      // hb2 should not contain hb's content
      TextEdit<String> buf2 = hb2.getBuffer();
      boolean found = false;
      for (int i = 1; i < buf2.finish(); i++) {
         if ("buf1 line".equals(buf2.at(i).toString())) {
            found = true;
            break;
         }
      }
      assertEquals(false, found,
         "independent buffers should not share content");
   }
}
