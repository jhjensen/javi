package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests exercising EditGroup, MoveGroup, and FileList
 * code paths through the StreamInterface and processCommand API.
 * Targets low-coverage areas in these classes.
 */
class EditIntegrationJUnitTest {

   private static final String TEST_PREFIX = "ju_editint_";
   private TextEdit<String> ex;

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void setUp() throws Exception {
      EventQueue.biglock2.lock();
      UI.setStream(new StringReader(""));
      ex = openTestFile(TEST_PREFIX + "buf");
   }

   @AfterEach
   void tearDown() {
      try {
         if (ex != null)
            try { ex.disposeFvc(); } catch (IOException e) { /* ok */ }
         deleteTestFiles(TEST_PREFIX + "buf",
            TEST_PREFIX + "out", TEST_PREFIX + "wt");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // --- Global command with substitute (exercises commandproc2) ---

   @Test
   void globalSubstituteFlagG() throws Exception {
      ex.inserttext("aXXa\nbXXb\n", 0, 1);
      ex.checkpoint();
      // g/X/s/X/Y/g — global substitute with g flag on matching lines
      int result = ex.processCommand("g/X/s/X/Y/g", 1);
      assertTrue(result >= 0);
      assertEquals("aYYa", ex.at(1).toString());
      assertEquals("bYYb", ex.at(2).toString());
   }

   @Test
   void inverseGlobalCopy() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      // v/bbb/t0 — copy lines NOT matching "bbb" to top
      int result = ex.processCommand("v/bbb/t0", 1);
      assertTrue(result >= 0);
      // "aaa" and "ccc" are copied to top
      assertTrue(ex.finish() > 4,
         "should have more lines after copy");
   }

   @Test
   void moveLineForward() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
      ex.checkpoint();
      // 1m3 — move line 1 to after line 3
      int result = ex.processCommand("1m3", 1);
      assertTrue(result >= 0);
      assertEquals("bbb", ex.at(1).toString());
      assertEquals("aaa", ex.at(2).toString());
      assertEquals("ccc", ex.at(3).toString());
      assertEquals("ddd", ex.at(4).toString());
   }

   @Test
   void moveLineBackward() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
      ex.checkpoint();
      // 3m1 — move line 3 to after line 1
      int result = ex.processCommand("3m1", 1);
      assertTrue(result >= 0);
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("ccc", ex.at(2).toString());
      assertEquals("bbb", ex.at(3).toString());
      assertEquals("ddd", ex.at(4).toString());
   }

   @Test
   void copyLineForward() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      // 1t3 — copy line 1 to after line 3
      int result = ex.processCommand("1t3", 1);
      assertTrue(result >= 0);
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("bbb", ex.at(2).toString());
      assertEquals("ccc", ex.at(3).toString());
      assertEquals("aaa", ex.at(4).toString());
   }

   @Test
   void copyLineBackward() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      // 3t0 — copy line 3 to after line 0 (before line 1)
      int result = ex.processCommand("3t0", 1);
      assertTrue(result >= 0);
      assertEquals("ccc", ex.at(1).toString());
      assertEquals("aaa", ex.at(2).toString());
   }

   @Test
   void rangeDeleteMultipleLines() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\nddd\neee\n", 0, 1);
      ex.checkpoint();
      // 2,4d — delete lines 2-4
      int result = ex.processCommand("2,4d", 1);
      assertTrue(result >= 0);
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("eee", ex.at(2).toString());
      assertEquals(4, ex.finish());
   }

   @Test
   void rangeCopyMultipleLines() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      // 1,2t3 — copy lines 1-2 to after line 3
      int result = ex.processCommand("1,2t3", 1);
      assertTrue(result >= 0);
      assertEquals(7, ex.finish());
      assertEquals("aaa", ex.at(4).toString());
      assertEquals("bbb", ex.at(5).toString());
   }

   @Test
   void rangeMoveMultipleLines() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\nddd\neee\n", 0, 1);
      ex.checkpoint();
      // 4,5m1 — move lines 4-5 to after line 1 (backward)
      int result = ex.processCommand("4,5m1", 1);
      assertTrue(result >= 0);
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("ddd", ex.at(2).toString());
      assertEquals("eee", ex.at(3).toString());
      assertEquals("bbb", ex.at(4).toString());
      assertEquals("ccc", ex.at(5).toString());
   }

   // --- Substitute with regex features ---

   @Test
   void substituteWithBackreference() throws Exception {
      ex.inserttext("hello world\n", 0, 1);
      ex.checkpoint();
      // Capture group backreference using & for whole match
      int result = ex.processCommand("1s/hello/[&]/", 1);
      assertTrue(result >= 0);
      assertEquals("[hello] world", ex.at(1).toString());
   }

   @Test
   void substituteWithGlobalFlag() throws Exception {
      ex.inserttext("aXbXcX\n", 0, 1);
      ex.checkpoint();
      int result = ex.processCommand("1s/X/Y/g", 1);
      assertTrue(result >= 0);
      assertEquals("aYbYcY", ex.at(1).toString());
   }

   @Test
   void substituteNoMatchLeavesUnchanged() throws Exception {
      ex.inserttext("hello\n", 0, 1);
      ex.checkpoint();
      int result = ex.processCommand("1s/zzz/QQQ/", 1);
      assertTrue(result >= 0);
      assertEquals("hello", ex.at(1).toString());
   }

   // --- File I/O paths ---

   @Test
   void writeAndReopen() throws Exception {
      ex.inserttext("data1\ndata2\n", 0, 1);
      ex.checkpoint();
      ex.printout();
      ex.disposeFvc();

      ex = openTestFile(TEST_PREFIX + "buf");
      assertEquals("data1", ex.at(1).toString());
      assertEquals("data2", ex.at(2).toString());
   }

   @Test
   void writeFilteredByGlobal() throws Exception {
      ex.inserttext("include\nexclude\ninclude2\n", 0, 1);
      ex.checkpoint();
      String outPath = testPath(TEST_PREFIX + "out");
      // v/exclude/w — write non-matching lines
      int result = ex.processCommand("v/exclude/w " + outPath, 1);
      assertTrue(result >= 0);
      TextEdit<String> out = openTestFile(TEST_PREFIX + "out");
      assertEquals("include", out.at(1).toString());
      assertEquals("include2", out.at(2).toString());
      assertTrue(out.finish() >= 3);
      out.disposeFvc();
   }

   // --- EditContainer operations ---

   @Test
   void insertAndCheckModified() throws Exception {
      ex.inserttext("aaa\n", 0, 1);
      assertTrue(ex.isModified(), "should be modified after insert");
      ex.checkpoint();
   }

   @Test
   void checkpointAndVerify() throws Exception {
      ex.inserttext("aaa\n", 0, 1);
      ex.checkpoint();
      ex.inserttext("bbb\n", 0, 2);
      assertTrue(ex.isModified());
      ex.checkpoint();
   }

   @Test
   void removeAndVerify() throws Exception {
      ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
      ex.checkpoint();
      java.util.List<String> removed = ex.remove(2, 1);
      assertEquals(1, removed.size());
      assertEquals("bbb", removed.get(0));
      assertEquals("aaa", ex.at(1).toString());
      assertEquals("ccc", ex.at(2).toString());
   }

   // --- helper methods ---

   private static String testPath(String name) {
      return history.Testutil.testFile(name).getPath();
   }

   private static FileDescriptor.LocalFile makeLocal(String name) {
      return FileDescriptor.LocalFile.make(
         history.Testutil.testFile(name));
   }

   private static TextEdit<String> openTestFile(String name) {
      FileDescriptor fd = FileDescriptor.make(testPath(name));
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      assertFalse(te.getError(),
         "File should open without error: " + name);
      return te;
   }

   private static void deleteTestFiles(String... names) {
      for (String name : names) {
         try {
            makeLocal(name).delete();
         } catch (IOException e) { /* ignore */ }
         try {
            makeLocal(name + ".dmp2").delete();
         } catch (IOException e) { /* ignore */ }
      }
   }
}
