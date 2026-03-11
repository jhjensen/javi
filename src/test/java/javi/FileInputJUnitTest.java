package javi;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * JUnit 5 tests for {@link FileInput}.
 *
 * <p>Tests line-ending detection and splitting for Unix (\n),
 * MS (\r\n), and mixed (\r, \n) modes through TextEdit integration.</p>
 */
class FileInputJUnitTest {

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

   private static void writeRawBytes(String name, String contents)
         throws IOException {
      try (OutputStreamWriter fs = new OutputStreamWriter(
            new FileOutputStream(testPath(name)),
            StandardCharsets.UTF_8)) {
         fs.write(contents);
      }
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

   private static void deleteTestFiles(String... names)
         throws IOException {
      for (String name : names) {
         makeLocal(name).delete();
         makeLocal(name + ".dmp2").delete();
      }
   }

   // ============================================================
   // Unix line endings (\n)
   // ============================================================

   @Test
   void unixLineEndingsProduceCorrectLines() throws IOException {
      String fname = "ju_fi_unix1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      writeRawBytes(fname, "alpha\nbeta\ngamma\n");

      TextEdit<String> te = openTestFile(fname);
      assertEquals("alpha", te.at(1).toString());
      assertEquals("beta", te.at(2).toString());
      assertEquals("gamma", te.at(3).toString());
      assertEquals(4, te.finish(), "3 lines + sentinel = 4");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void unixSingleLineNoTrailingNewline() throws IOException {
      String fname = "ju_fi_unix2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      writeRawBytes(fname, "onlyone");

      TextEdit<String> te = openTestFile(fname);
      assertEquals("onlyone", te.at(1).toString());
      assertEquals(2, te.finish(), "1 line no trailing newline = 2");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void unixEmptyLinesPreserved() throws IOException {
      String fname = "ju_fi_unix3";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      writeRawBytes(fname, "a\n\n\nb\n");

      TextEdit<String> te = openTestFile(fname);
      assertEquals("a", te.at(1).toString());
      assertEquals("", te.at(2).toString());
      assertEquals("", te.at(3).toString());
      assertEquals("b", te.at(4).toString());
      assertEquals(5, te.finish());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // MS line endings (\r\n)
   // ============================================================

   @Test
   void msLineEndingsProduceCorrectLines() throws IOException {
      String fname = "ju_fi_ms1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      writeRawBytes(fname, "alpha\r\nbeta\r\ngamma\r\n");

      TextEdit<String> te = openTestFile(fname);
      assertEquals("alpha", te.at(1).toString());
      assertEquals("beta", te.at(2).toString());
      assertEquals("gamma", te.at(3).toString());
      assertEquals(4, te.finish(), "3 lines + sentinel = 4");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void msSingleLineWithCRLF() throws IOException {
      String fname = "ju_fi_ms2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      writeRawBytes(fname, "hello\r\n");

      TextEdit<String> te = openTestFile(fname);
      assertEquals("hello", te.at(1).toString());
      assertEquals(2, te.finish());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void msEmptyLinesPreserved() throws IOException {
      String fname = "ju_fi_ms3";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      writeRawBytes(fname, "x\r\n\r\n\r\ny\r\n");

      TextEdit<String> te = openTestFile(fname);
      assertEquals("x", te.at(1).toString());
      assertEquals("", te.at(2).toString());
      assertEquals("", te.at(3).toString());
      assertEquals("y", te.at(4).toString());
      assertEquals(5, te.finish());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // Empty file
   // ============================================================

   @Test
   void emptyFileProducesSentinelOnly() throws IOException {
      String fname = "ju_fi_empty";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      writeRawBytes(fname, "");

      TextEdit<String> te = openTestFile(fname);
      assertEquals(2, te.finish(), "empty file should have sentinel only");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // Single character files
   // ============================================================

   @Test
   void singleNewlineFile() throws IOException {
      String fname = "ju_fi_nl";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);
      writeRawBytes(fname, "\n");

      TextEdit<String> te = openTestFile(fname);
      assertEquals("", te.at(1).toString());
      assertEquals(2, te.finish());

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // Large content
   // ============================================================

   @Test
   void manyLinesReadCorrectly() throws IOException {
      String fname = "ju_fi_many";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 100; i++)
         sb.append("line").append(i).append('\n');
      writeRawBytes(fname, sb.toString());

      TextEdit<String> te = openTestFile(fname);
      assertEquals("line0", te.at(1).toString());
      assertEquals("line50", te.at(51).toString());
      assertEquals("line99", te.at(100).toString());
      assertEquals(101, te.finish(), "100 lines + sentinel = 101");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void longLineReadCorrectly() throws IOException {
      String fname = "ju_fi_long";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      String longLine = "x".repeat(10000);
      writeRawBytes(fname, longLine + "\n");

      TextEdit<String> te = openTestFile(fname);
      assertEquals(10000, te.at(1).toString().length());
      assertEquals(longLine, te.at(1).toString());

      te.disposeFvc();
      deleteTestFiles(fname);
   }
}
