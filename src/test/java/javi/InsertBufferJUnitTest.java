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
      java.lang.reflect.Field bf = InsertBuffer.class.getDeclaredField("buffer");
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

   // ── insertChars edge cases ──────────────────────────────────

   @Test
   void insertCharsWithUnicode() throws Exception {
      ib.insertChars(new StringCharacterIterator("\u00e9\u00e8\u00ea"), 0);
      assertEquals("\u00e9\u00e8\u00ea", ib.getString());
   }

   @Test
   void insertCharsEmptyIterator() throws Exception {
      ib.insertChars(new StringCharacterIterator(""), 0);
      assertEquals("", ib.getString());
   }

   @Test
   void insertCharsPreservesNewlines() throws Exception {
      ib.insertChars(new StringCharacterIterator("a\nb\nc"), 0);
      assertEquals("a\nb\nc", ib.getString());
   }

   @Test
   void insertCharsPreservesTabs() throws Exception {
      ib.insertChars(new StringCharacterIterator("col1\tcol2"), 0);
      assertEquals("col1\tcol2", ib.getString());
   }

   // ── findspacebound tests ────────────────────────────────────

   @Test
   void findspaceboundReturnsZeroOnEmptyBuffer() throws Exception {
      // Create a minimal FvContext with no content above cursor
      TextEdit<String> te = createTestEdit("only line\n");
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);
      fvc.cursoryabs(1);

      assertEquals(0, InsertBuffer.findspacebound(fvc, 0));
      te.disposeFvc();
   }

   @Test
   void findspaceboundFindsNextTabStop() throws Exception {
      // Line above has spaces then text at position 8
      TextEdit<String> te = createTestEdit("        code here\ncursor\n");
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);
      fvc.cursoryabs(2);

      // At linepos 0, should find distance to next non-space after pos 0
      int result = InsertBuffer.findspacebound(fvc, 0);
      // "        code here": first non-space is at 8, so distance = 8
      assertEquals(8, result);
      te.disposeFvc();
   }

   @Test
   void findspaceboundSkipsBlankLines() throws Exception {
      // Two lines above: blank then indented
      TextEdit<String> te = createTestEdit(
         "    content\n\ncursor\n");
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);
      fvc.cursoryabs(3);

      // Line 2 is empty (no match), should check line 1
      int result = InsertBuffer.findspacebound(fvc, 0);
      // "    content": space->non-space at 4, so distance=4
      assertEquals(4, result);
      te.disposeFvc();
   }

   @Test
   void findspaceboundAtCursorLine1ReturnsZero() throws Exception {
      // Cursor at line 1 => no lines above => 0
      TextEdit<String> te = createTestEdit("first line\n");
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);
      fvc.cursoryabs(1);

      assertEquals(0, InsertBuffer.findspacebound(fvc, 0));
      te.disposeFvc();
   }

   private TextEdit<String> createTestEdit(String content) {
      String path = history.Testutil.testFile("ib_test").getPath();
      FileDescriptor fd = FileDescriptor.make(path);
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.inserttext(content, 0, 1);
      te.checkpoint();
      te.finish();
      return te;
   }
}
