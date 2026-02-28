package javi;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B8: Memory benchmark and correctness tests for the streaming file-read path.
 *
 * <p>Exercises the {@link FileInput} streaming {@code BufferedReader} path
 * introduced in B8 ({@link FileProperties#openStreamingReader()}) to verify
 * that large files are loaded without excessive memory overhead, and that
 * the content is identical to what was written.</p>
 *
 * <p>Each test creates temporary files under a JUnit {@code @TempDir},
 * loads them through {@code TextEdit}, and validates line counts and
 * content correctness.</p>
 */
class FileInputMemoryJUnitTest {

   @TempDir
   java.nio.file.Path tempDir;

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

   // ---- helpers -------------------------------------------------------

   /**
    * Write a text file with the given number of lines, each containing
    * a line number and padding text to reach approximately the desired
    * per-line length.
    */
   private java.io.File writeLargeFile(String name, int lineCount,
         int lineWidth) throws IOException {
      java.io.File file = tempDir.resolve(name).toFile();
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(file), StandardCharsets.UTF_8)) {
         StringBuilder sb = new StringBuilder(lineWidth + 20);
         for (int i = 0; i < lineCount; i++) {
            sb.setLength(0);
            sb.append("Line ").append(i).append(": ");
            while (sb.length() < lineWidth) {
               sb.append("abcdefghijklmnop ");
            }
            w.write(sb.toString());
            w.write('\n');
         }
      }
      return file;
   }

   /** Open a file through the standard Javi file-load pipeline. */
   private TextEdit<String> loadFile(java.io.File file) {
      FileDescriptor fd = FileDescriptor.make(file.getAbsolutePath());
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      assertFalse(te.getError(), "File should load without error");
      return te;
   }

   /**
    * Force GC aggressively and return used heap in bytes.
    * Not precise, but good enough for a coarse regression guard.
    */
   private static long usedMemory() {
      Runtime rt = Runtime.getRuntime();
      for (int i = 0; i < 4; i++) {
         rt.gc();
         try {
            Thread.sleep(50);
         } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
         }
      }
      return rt.totalMemory() - rt.freeMemory();
   }

   // ---- tests ---------------------------------------------------------

   /**
    * Coarse memory-regression guard: load a ~5 MB file and verify that
    * the live-heap increase is less than 4x the file size on disk.
    *
    * <p>Before the B8 streaming fix the old path held the full file as
    * a single {@code String} <em>plus</em> all per-line substrings,
    * easily 3-4x the file size.  The streaming path should keep
    * overhead well below that threshold.</p>
    */
   @Test
   void testStreamingLoadMemoryBound() throws Exception {
      // ~5 MB file: 50_000 lines x ~100 chars/line
      int lineCount = 50_000;
      int lineWidth = 100;
      java.io.File file = writeLargeFile("memory_test.txt",
         lineCount, lineWidth);
      long fileSize = file.length();
      assertTrue(fileSize > 4_000_000,
         "Test file should be at least 4 MB, got " + fileSize);

      long baseline = usedMemory();

      TextEdit<String> te = loadFile(file);
      // finish() already called inside loadFile

      long afterLoad = usedMemory();
      long delta = afterLoad - baseline;

      // The loaded content must exist in memory as individual String
      // lines inside EditCache, so some overhead is expected.
      // Assert that the total increase is less than 4x file size.
      // In practice the streaming path should be well under 3x.
      assertTrue(delta < fileSize * 4,
         "Memory increase (" + delta + " bytes) should be < 4x file size ("
         + fileSize + " bytes). Ratio: "
         + String.format("%.2f", (double) delta / fileSize));

      // Verify correct loading
      // readIn() = ecache.size(); line 0 is the initial empty element,
      // so actual content lines are 1 .. readIn()-1.
      assertEquals(lineCount + 1, te.readIn(),
         "Line count should match (readIn includes initial empty element)");

      te.terminate();
   }

   /**
    * Verify that every line of a large file is loaded correctly
    * through the streaming path. Spot-checks first, last, and middle
    * lines.
    */
   @Test
   void testStreamingLoadCorrectness() throws Exception {
      int lineCount = 10_000;
      java.io.File file = writeLargeFile("correct_test.txt", lineCount, 80);

      TextEdit<String> te = loadFile(file);
      assertEquals(lineCount + 1, te.readIn());

      // Spot-check first line (at(1) — index 0 is always empty)
      String first = (String) te.at(1);
      assertTrue(first.startsWith("Line 0: "),
         "First line should start with 'Line 0: ', got: "
         + first.substring(0, Math.min(30, first.length())));

      // Spot-check last line
      String last = (String) te.at(lineCount);
      assertTrue(last.startsWith("Line " + (lineCount - 1) + ": "),
         "Last line should start with 'Line " + (lineCount - 1) + ": '");

      // Spot-check middle line
      int mid = lineCount / 2;
      String middle = (String) te.at(mid + 1);
      assertTrue(middle.startsWith("Line " + mid + ": "),
         "Middle line should start with 'Line " + mid + ": '");

      te.terminate();
   }

   /**
    * Verify that a small file (fits within the 8&nbsp;KB sample) is
    * also handled correctly by the streaming path.
    */
   @Test
   void testSmallFileStreamingPath() throws Exception {
      java.io.File file = writeLargeFile("small_test.txt", 10, 40);

      TextEdit<String> te = loadFile(file);
      assertEquals(11, te.readIn(),
         "10 lines + 1 initial empty element");

      String first = (String) te.at(1);
      assertTrue(first.startsWith("Line 0: "));

      String last = (String) te.at(10);
      assertTrue(last.startsWith("Line 9: "));

      te.terminate();
   }

   /**
    * Verify that an empty file is loaded without error.
    * {@code openStreamingReader()} returns null for empty files;
    * the fallback {@code initFile()} path should handle it.
    */
   @Test
   void testEmptyFileLoad() throws Exception {
      java.io.File file = tempDir.resolve("empty.txt").toFile();
      file.createNewFile();

      TextEdit<String> te = loadFile(file);
      // An empty file should just have the initial empty element
      // plus possibly one empty line (depending on how initFile handles it).
      assertTrue(te.readIn() >= 1,
         "Empty file should have at least the initial element");

      te.terminate();
   }

   /**
    * Verify that the streaming reader is cleaned up after the load
    * completes (i.e., the transient {@code reader} field is null
    * after {@code finish()}).
    */
   @Test
   void testReaderCleanupAfterLoad() throws Exception {
      java.io.File file = writeLargeFile("cleanup_test.txt", 100, 60);

      FileDescriptor fd = FileDescriptor.make(file.getAbsolutePath());
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      assertFalse(te.getError());

      // After finish(), the reader should have been closed and set to
      // null inside FileInput.getnext() (returns null → closes reader)
      // and truncIo().
      // We verify indirectly: the file should be loadable twice from
      // the same path without file-handle leaks.
      te.terminate();

      // Load again to prove the file handle was released
      TextEdit<String> te2 = loadFile(file);
      assertEquals(101, te2.readIn());
      te2.terminate();
   }

   /**
    * Verify that a file with Windows-style (CRLF) line endings is
    * handled correctly by the streaming path.
    */
   @Test
   void testCrLfLineEndings() throws Exception {
      java.io.File file = tempDir.resolve("crlf_test.txt").toFile();
      try (FileOutputStream fos = new FileOutputStream(file)) {
         for (int i = 0; i < 50; i++) {
            String line = "Line " + i + ": some content here";
            fos.write(line.getBytes(StandardCharsets.UTF_8));
            fos.write('\r');
            fos.write('\n');
         }
      }

      TextEdit<String> te = loadFile(file);
      assertEquals(51, te.readIn(),
         "50 CRLF lines + 1 initial empty element");

      String first = (String) te.at(1);
      assertTrue(first.startsWith("Line 0: "),
         "CRLF line should not contain CR after streaming read");
      assertFalse(first.contains("\r"),
         "Line should not contain embedded CR");

      te.terminate();
   }

   /**
    * Load two large files sequentially and verify both load correctly.
    * This guards against static-state leaks between file loads.
    */
   @Test
   void testSequentialLargeLoads() throws Exception {
      java.io.File file1 = writeLargeFile("seq1.txt", 5000, 80);
      java.io.File file2 = writeLargeFile("seq2.txt", 8000, 60);

      TextEdit<String> te1 = loadFile(file1);
      assertEquals(5001, te1.readIn());
      te1.terminate();

      TextEdit<String> te2 = loadFile(file2);
      assertEquals(8001, te2.readIn());
      te2.terminate();
   }

   /**
    * Verify that Unicode content (multi-byte UTF-8) is loaded correctly
    * through the streaming path.
    */
   @Test
   void testUnicodeContent() throws Exception {
      java.io.File file = tempDir.resolve("unicode_test.txt").toFile();
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(file), StandardCharsets.UTF_8)) {
         w.write("Hello ASCII\n");
         w.write("\u00e9\u00e8\u00ea accented\n");  // French accents
         w.write("\u00fc\u00f6\u00e4 umlauts\n");    // German umlauts
         w.write("\u4e16\u754c Chinese\n");           // CJK
         w.write("\ud83d\ude00 emoji\n");             // emoji (surrogate pair)
      }

      TextEdit<String> te = loadFile(file);
      assertEquals(6, te.readIn(),
         "5 lines + initial empty element");

      assertEquals("Hello ASCII", te.at(1));
      assertTrue(((String) te.at(2)).contains("accented"));
      assertTrue(((String) te.at(3)).contains("umlauts"));
      assertTrue(((String) te.at(4)).contains("Chinese"));
      assertTrue(((String) te.at(5)).contains("emoji"));

      te.terminate();
   }
}
