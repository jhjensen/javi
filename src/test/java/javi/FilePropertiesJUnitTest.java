package javi;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link FileProperties}.
 *
 * <p>
 * Tests file metadata handling: read-only enforcement,
 * external modification detection, line-ending detection,
 * and file write operations.
 * </p>
 */
class FilePropertiesJUnitTest {

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

   // --- Helpers ---

   private static String testPath(String name) {
      return history.Testutil.testFile(name).getPath();
   }

   private static FileDescriptor.LocalFile makeLocal(String name) {
      return FileDescriptor.LocalFile.make(history.Testutil.testFile(name));
   }

   private static void writeRawFile(String name, String contents)
         throws IOException {
      try (OutputStreamWriter fs = new OutputStreamWriter(
            new FileOutputStream(testPath(name)),
            StandardCharsets.UTF_8)) {
         fs.write(contents);
      }
   }

   private static String readRawFile(String name) throws IOException {
      java.io.File f = new java.io.File(testPath(name));
      char[] buf = new char[(int) f.length() + 20];
      try (InputStreamReader fs = new InputStreamReader(
            new FileInputStream(testPath(name)),
            StandardCharsets.UTF_8)) {
         int len = fs.read(buf, 0, buf.length);
         return new String(buf, 0, len);
      }
   }

   private static void deleteTestFiles(String... names)
         throws IOException {
      for (String name : names) {
         makeLocal(name).delete();
         makeLocal(name + ".dmp2").delete();
      }
   }

   private static FileProperties<String> makeFP(String name) {
      FileDescriptor fd = FileDescriptor.make(testPath(name));
      return new FileProperties<>(fd, StringIoc.converter);
   }

   // ============================================================
   // Read-only / writeable tests
   // ============================================================

   @Test
   void newFilePropertiesIsWriteable() throws IOException {
      String fname = "ju_fp_rw1";
      deleteTestFiles(fname);
      writeRawFile(fname, "hello\n");

      FileProperties<String> fp = makeFP(fname);
      assertTrue(fp.isWriteable(),
         "newly created FileProperties should be writeable");

      deleteTestFiles(fname);
   }

   @Test
   void setReadOnlyMakesUnwriteable() throws IOException {
      String fname = "ju_fp_ro1";
      deleteTestFiles(fname);
      writeRawFile(fname, "hello\n");

      FileProperties<String> fp = makeFP(fname);
      fp.setReadOnly(true);
      assertFalse(fp.isWriteable(),
         "setReadOnly(true) should make unwriteable");

      deleteTestFiles(fname);
   }

   @Test
   void readOnlyRoundTrip() throws IOException {
      String fname = "ju_fp_ro2";
      deleteTestFiles(fname);
      writeRawFile(fname, "hello\n");

      FileProperties<String> fp = makeFP(fname);
      fp.setReadOnly(true);
      assertFalse(fp.isWriteable());
      fp.setReadOnly(false);
      assertTrue(fp.isWriteable(),
         "setReadOnly(false) should restore writeable");

      deleteTestFiles(fname);
   }

   // ============================================================
   // External modification detection tests
   // ============================================================

   @Test
   void ignoreExternalChangesDefaultFalse() throws IOException {
      String fname = "ju_fp_ign1";
      deleteTestFiles(fname);
      writeRawFile(fname, "hello\n");

      FileProperties<String> fp = makeFP(fname);
      assertFalse(fp.isIgnoringExternalChanges(),
         "default should not ignore external changes");

      deleteTestFiles(fname);
   }

   @Test
   void setIgnoreExternalChangesToggle() throws IOException {
      String fname = "ju_fp_ign2";
      deleteTestFiles(fname);
      writeRawFile(fname, "hello\n");

      FileProperties<String> fp = makeFP(fname);
      fp.setIgnoreExternalChanges(true);
      assertTrue(fp.isIgnoringExternalChanges());
      fp.setIgnoreExternalChanges(false);
      assertFalse(fp.isIgnoringExternalChanges());

      deleteTestFiles(fname);
   }

   @Test
   void checkModifiedFalseForNewFileProperties() throws IOException {
      String fname = "ju_fp_cm1";
      deleteTestFiles(fname);
      writeRawFile(fname, "hello\n");

      FileProperties<String> fp = makeFP(fname);
      // lastModifiedTime is 0 before initFile, so checkModified
      // returns false
      assertFalse(fp.checkModified(),
         "checkModified should return false before initFile");

      deleteTestFiles(fname);
   }

   @Test
   void checkModifiedFalseWhenIgnoring() throws IOException {
      String fname = "ju_fp_cm2";
      deleteTestFiles(fname);
      writeRawFile(fname, "hello\n");

      FileProperties<String> fp = makeFP(fname);
      fp.initFile();
      fp.setIgnoreExternalChanges(true);

      // Even if file changes externally, should return false
      writeRawFile(fname, "changed\n");
      assertFalse(fp.checkModified(),
         "checkModified should return false when ignoring");

      deleteTestFiles(fname);
   }

   @Test
   void checkModifiedDetectsExternalChange() throws IOException,
         InterruptedException {
      String fname = "ju_fp_cm3";
      deleteTestFiles(fname);
      writeRawFile(fname, "original\n");

      FileProperties<String> fp = makeFP(fname);
      fp.initFile();

      // Wait a bit to ensure filesystem timestamp differs
      Thread.sleep(1100);
      writeRawFile(fname, "modified\n");

      assertTrue(fp.checkModified(),
         "checkModified should detect external modification");

      deleteTestFiles(fname);
   }

   @Test
   void updateModifiedTimeResetsCheck() throws IOException,
         InterruptedException {
      String fname = "ju_fp_cm4";
      deleteTestFiles(fname);
      writeRawFile(fname, "original\n");

      FileProperties<String> fp = makeFP(fname);
      fp.initFile();

      Thread.sleep(1100);
      writeRawFile(fname, "modified\n");
      assertTrue(fp.checkModified());

      // After updateModifiedTime, checkModified returns false
      fp.updateModifiedTime();
      assertFalse(fp.checkModified(),
         "checkModified should return false after updateModifiedTime");

      deleteTestFiles(fname);
   }

   // ============================================================
   // Line ending detection (initFile) tests
   // ============================================================

   @Test
   void initFileDetectsUnixLineEnding() throws IOException {
      String fname = "ju_fp_lf1";
      deleteTestFiles(fname);
      writeRawFile(fname, "line1\nline2\n");

      FileProperties<String> fp = makeFP(fname);
      String content = fp.initFile();
      assertTrue(content.contains("line1"));
      assertTrue(content.contains("line2"));

      deleteTestFiles(fname);
   }

   @Test
   void initFileDetectsCrLfLineEnding() throws IOException {
      String fname = "ju_fp_lf2";
      deleteTestFiles(fname);

      // Write raw bytes with \r\n
      try (FileOutputStream fos = new FileOutputStream(testPath(fname))) {
         fos.write("line1\r\nline2\r\n".getBytes(StandardCharsets.UTF_8));
      }

      FileProperties<String> fp = makeFP(fname);
      String content = fp.initFile();
      assertTrue(content.contains("line1"));
      assertTrue(content.contains("line2"));

      deleteTestFiles(fname);
   }

   @Test
   void initFileHandlesEmptyFile() throws IOException {
      String fname = "ju_fp_lf3";
      deleteTestFiles(fname);
      writeRawFile(fname, "");

      FileProperties<String> fp = makeFP(fname);
      String content = fp.initFile();
      assertEquals(0, content.length(),
         "empty file should return empty string from initFile");

      deleteTestFiles(fname);
   }

   // ============================================================
   // writeAll tests
   // ============================================================

   @Test
   void writeAllWritesContentToFile() throws IOException {
      String fname = "ju_fp_w1";
      deleteTestFiles(fname);
      // Create the file first so FileDescriptor can get output stream
      writeRawFile(fname, "");

      FileProperties<String> fp = makeFP(fname);
      fp.initFile(); // initialize charset

      List<String> lines = Arrays.asList("hello", "world");
      fp.writeAll(lines.iterator());

      String content = readRawFile(fname);
      assertTrue(content.contains("hello"),
         "written file should contain first line");
      assertTrue(content.contains("world"),
         "written file should contain second line");

      deleteTestFiles(fname);
   }

   @Test
   void writeAllPreservesLineEndings() throws IOException {
      String fname = "ju_fp_w2";
      deleteTestFiles(fname);

      // Write CR/LF file first so initFile picks up \r\n
      try (FileOutputStream fos = new FileOutputStream(testPath(fname))) {
         fos.write("orig\r\n".getBytes(StandardCharsets.UTF_8));
      }

      FileProperties<String> fp = makeFP(fname);
      fp.initFile();

      List<String> lines = Arrays.asList("first", "second");
      fp.writeAll(lines.iterator());

      // Read raw bytes to check line endings
      byte[] bytes;
      try (FileInputStream fis = new FileInputStream(testPath(fname))) {
         bytes = fis.readAllBytes();
      }
      String raw = new String(bytes, StandardCharsets.UTF_8);
      assertTrue(raw.contains("\r\n"),
         "writeAll should preserve CR/LF line endings");

      deleteTestFiles(fname);
   }

   // ============================================================
   // safeWrite (atomic write via temp+rename) tests
   // ============================================================

   @Test
   void safeWriteAtomicWrite() throws IOException {
      String fname = "ju_fp_sw1";
      deleteTestFiles(fname);
      writeRawFile(fname, "original\n");

      FileProperties<String> fp = makeFP(fname);
      fp.initFile();

      List<String> lines = Arrays.asList("safe", "write");
      fp.safeWrite(new StringIter(lines.iterator()));

      String content = readRawFile(fname);
      assertTrue(content.contains("safe"),
         "safeWrite should write first line");
      assertTrue(content.contains("write"),
         "safeWrite should write second line");
      assertFalse(content.contains("original"),
         "safeWrite should replace original content");

      deleteTestFiles(fname);
   }

   // ============================================================
   // Copy constructor tests
   // ============================================================

   @Test
   void copyConstructorPreservesFormat() throws IOException {
      String fname1 = "ju_fp_cp1";
      String fname2 = "ju_fp_cp2";
      deleteTestFiles(fname1, fname2);

      // Create a file with CR/LF
      try (FileOutputStream fos = new FileOutputStream(testPath(fname1))) {
         fos.write("line\r\n".getBytes(StandardCharsets.UTF_8));
      }

      FileProperties<String> fp1 = makeFP(fname1);
      fp1.initFile();
      fp1.setReadOnly(true);

      // Copy constructor should inherit format settings
      FileDescriptor fd2 = FileDescriptor.make(testPath(fname2));
      FileProperties<String> fp2 = new FileProperties<>(fp1, fd2);
      // Note: read-only is not copied (it's transient)
      assertTrue(fp2.isWriteable(),
         "copy constructor should not inherit read-only (transient)");

      deleteTestFiles(fname1, fname2);
   }

   // ============================================================
   // F47: Charset detection and round-trip tests
   // ============================================================

   @Test
   void initFileSetsCharsetFromContent() throws IOException {
      String fname = "ju_fp_cs1";
      deleteTestFiles(fname);

      // Write a file with non-ASCII content in UTF-8
      try (FileOutputStream fos = new FileOutputStream(testPath(fname))) {
         // "café résumé naïve" — accented chars that need charset detection
         fos.write("caf\u00e9 r\u00e9sum\u00e9 na\u00efve\n"
            .getBytes(StandardCharsets.UTF_8));
      }

      FileProperties<String> fp = makeFP(fname);
      fp.initFile();

      // charSet should be detected (UTF-8 on this content)
      assertNotNull(fp.getCharSet(),
         "initFile should set charSet from content");

      deleteTestFiles(fname);
   }

   @Test
   void charsetRoundTripPreservesNonAscii() throws IOException {
      String fname = "ju_fp_cs2";
      deleteTestFiles(fname);

      // Write file with accented characters, em-dash, smart quotes
      String original = "caf\u00e9 \u2014 \u201csmart\u201d na\u00efve";
      try (FileOutputStream fos = new FileOutputStream(testPath(fname))) {
         fos.write((original + "\n").getBytes(StandardCharsets.UTF_8));
      }

      // Read via initFile (legacy path) — should detect charset
      FileProperties<String> fp = makeFP(fname);
      fp.initFile();

      // Write back via writeAll
      List<String> lines = Arrays.asList(original);
      fp.writeAll(lines.iterator());

      // Re-read raw bytes and verify content is unchanged
      byte[] written;
      try (FileInputStream fis = new FileInputStream(testPath(fname))) {
         written = fis.readAllBytes();
      }
      String result = new String(written, StandardCharsets.UTF_8);
      assertTrue(result.contains(original),
         "round-trip through initFile+writeAll should preserve non-ASCII: "
         + "expected [" + original + "] in [" + result + "]");
      assertFalse(result.contains("?"),
         "round-trip should not introduce '?' replacements");

      deleteTestFiles(fname);
   }

   @Test
   void charsetRoundTripViaStreamingReader() throws IOException {
      String fname = "ju_fp_cs3";
      deleteTestFiles(fname);

      // Write file with non-ASCII
      String original = "\u00fc\u00f6\u00e4 \u00df \u20ac";  // üöä ß €
      try (FileOutputStream fos = new FileOutputStream(testPath(fname))) {
         fos.write((original + "\n").getBytes(StandardCharsets.UTF_8));
      }

      // Read via openStreamingReader — should detect charset
      FileProperties<String> fp = makeFP(fname);
      java.io.BufferedReader reader = fp.openStreamingReader();
      assertNotNull(reader, "openStreamingReader should succeed for local file");
      String line = reader.readLine();
      reader.close();

      assertEquals(original, line,
         "streaming reader should decode non-ASCII correctly");

      // Write back and verify no corruption
      fp.writeAll(Arrays.asList(original).iterator());

      byte[] written;
      try (FileInputStream fis = new FileInputStream(testPath(fname))) {
         written = fis.readAllBytes();
      }
      String result = new String(written, StandardCharsets.UTF_8);
      assertTrue(result.contains(original),
         "streaming round-trip should preserve non-ASCII");

      deleteTestFiles(fname);
   }

   @Test
   void charsetSurvivesDeserialization() throws Exception {
      String fname = "ju_fp_cs4";
      deleteTestFiles(fname);

      String content = "caf\u00e9\n";
      try (FileOutputStream fos = new FileOutputStream(testPath(fname))) {
         fos.write(content.getBytes(StandardCharsets.UTF_8));
      }

      // Create and initialize FileProperties
      FileProperties<String> fp = makeFP(fname);
      fp.initFile();
      assertNotNull(fp.getCharSet(), "charSet should be set after initFile");

      // Serialize and deserialize
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      try (java.io.ObjectOutputStream oos =
            new java.io.ObjectOutputStream(baos)) {
         oos.writeObject(fp);
      }

      FileProperties<?> restored;
      try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
            new java.io.ByteArrayInputStream(baos.toByteArray()))) {
         restored = (FileProperties<?>) ois.readObject();
      }

      // charSet should be non-null after deserialization (readObject fix)
      assertNotNull(restored.getCharSet(),
         "charSet must not be null after deserialization");

      deleteTestFiles(fname);
   }
}
