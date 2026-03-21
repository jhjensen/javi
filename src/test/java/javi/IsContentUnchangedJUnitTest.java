package javi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link FileList#isContentUnchanged}.
 *
 * <p>Verifies the smart-reload logic that compares disk content
 * against the in-memory buffer to suppress unnecessary reload
 * dialogs when a file is touched but not actually changed.</p>
 */
class IsContentUnchangedJUnitTest {

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

   private static void writeRawFile(String name, String contents)
         throws IOException {
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(testPath(name)),
            StandardCharsets.UTF_8)) {
         w.write(contents);
      }
   }

   private static void deleteTestFiles(String name) throws IOException {
      File f = history.Testutil.testFile(name);
      if (f.exists()) {
         f.delete();
      }
      File dmp = new File(f.getPath() + ".dmp2");
      if (dmp.exists()) {
         dmp.delete();
      }
   }

   /**
    * Open a file as a TextEdit with its FileProperties, simulating
    * what FileList.FileConverter.findfileopen does. Forces the read
    * to complete so lastFileSize is populated.
    */
   private static TextEdit<String> openFile(String name) {
      FileDescriptor.LocalFile fh =
         FileDescriptor.LocalFile.make(testPath(name));
      FileProperties<String> fp =
         new FileProperties<>(fh, StringIoc.converter);
      TextEdit<String> ev = new TextEdit<>(new FileInput(fp), fp);
      ev.finish(); // force lazy read to complete (sets lastFileSize)
      return ev;
   }

   // --- Tests ---

   @Test
   void unchangedFileReturnsTrue() throws IOException {
      String fname = "b9_unchanged";
      deleteTestFiles(fname);
      writeRawFile(fname, "hello\nworld\n");

      TextEdit<String> ev = openFile(fname);
      FileProperties fp = ev.getFileProperties();
      // File on disk is identical to buffer — should return true
      assertTrue(
         FileList.TestAccess.isContentUnchanged(ev, fp),
         "identical disk and buffer content should return true");

      deleteTestFiles(fname);
   }

   @Test
   void sameSizeDifferentContentReturnsFalse() throws IOException {
      String fname = "b9_samesize";
      deleteTestFiles(fname);
      writeRawFile(fname, "aaaa\n");

      TextEdit<String> ev = openFile(fname);
      FileProperties fp = ev.getFileProperties();

      // Overwrite with same-length but different content
      writeRawFile(fname, "bbbb\n");

      assertFalse(
         FileList.TestAccess.isContentUnchanged(ev, fp),
         "same size but different content should return false");

      deleteTestFiles(fname);
   }

   @Test
   void differentSizeReturnsFalse() throws IOException {
      String fname = "b9_diffsize";
      deleteTestFiles(fname);
      writeRawFile(fname, "short\n");

      TextEdit<String> ev = openFile(fname);
      FileProperties fp = ev.getFileProperties();

      // Overwrite with different-length content
      writeRawFile(fname, "much longer content here\n");

      assertFalse(
         FileList.TestAccess.isContentUnchanged(ev, fp),
         "different file size should return false");

      deleteTestFiles(fname);
   }

   @Test
   void emptyFileUnchangedReturnsTrue() throws IOException {
      String fname = "b9_empty";
      deleteTestFiles(fname);
      writeRawFile(fname, "");

      TextEdit<String> ev = openFile(fname);
      FileProperties fp = ev.getFileProperties();

      assertTrue(
         FileList.TestAccess.isContentUnchanged(ev, fp),
         "empty file unchanged should return true");

      deleteTestFiles(fname);
   }

   @Test
   void fileDeletedReturnsFalse() throws IOException {
      String fname = "b9_deleted";
      deleteTestFiles(fname);
      writeRawFile(fname, "content\n");

      TextEdit<String> ev = openFile(fname);
      FileProperties fp = ev.getFileProperties();

      // Delete the file — length() returns 0, mismatches lastFileSize
      history.Testutil.testFile(fname).delete();

      assertFalse(
         FileList.TestAccess.isContentUnchanged(ev, fp),
         "deleted file should return false (size mismatch)");

      deleteTestFiles(fname);
   }

   @Test
   void multiLineFileUnchangedReturnsTrue() throws IOException {
      String fname = "b9_multiline";
      deleteTestFiles(fname);
      writeRawFile(fname, "line one\nline two\nline three\n");

      TextEdit<String> ev = openFile(fname);
      FileProperties fp = ev.getFileProperties();

      assertTrue(
         FileList.TestAccess.isContentUnchanged(ev, fp),
         "multi-line unchanged file should return true");

      deleteTestFiles(fname);
   }

   /**
    * When a non-existent file is opened and then created externally,
    * checkModified should detect the creation.
    */
   @Test
   void newFileCreatedExternallyDetected() throws IOException {
      String fname = "b9_newfile";
      deleteTestFiles(fname);
      // Do NOT create the file — simulate opening a new (non-existent) file
      FileDescriptor.LocalFile fh =
         FileDescriptor.LocalFile.make(testPath(fname));
      FileProperties<String> fp =
         new FileProperties<>(fh, StringIoc.converter);
      // Simulate what javi does when opening a file: initFile reads
      // file content (empty for non-existent) and sets lastModifiedTime
      fp.initFile();

      // checkModified should return false when file doesn't exist
      assertFalse(fp.checkModified(),
         "non-existent file should not report as modified");

      // Now create the file externally
      writeRawFile(fname, "external content\n");

      // checkModified should detect the external creation
      assertTrue(fp.checkModified(),
         "newly created file should be detected as modified");

      deleteTestFiles(fname);
   }

   /**
    * When a non-existent file is opened and then created externally
    * with content, isContentUnchanged should return false (buffer is
    * empty but disk has content).
    */
   @Test
   void newFileCreatedWithContentIsChanged() throws IOException {
      String fname = "b9_newcontent";
      deleteTestFiles(fname);
      // Open non-existent file — buffer will be empty
      FileDescriptor.LocalFile fh =
         FileDescriptor.LocalFile.make(testPath(fname));
      FileProperties<String> fp =
         new FileProperties<>(fh, StringIoc.converter);
      TextEdit<String> ev = new TextEdit<>(new FileInput(fp), fp);
      ev.finish(); // force read — will be empty since file doesn't exist

      // Create the file externally with content
      writeRawFile(fname, "external content\n");

      // isContentUnchanged should return false (size mismatch: 0 vs >0)
      assertFalse(
         FileList.TestAccess.isContentUnchanged(ev, fp),
         "new file with content should not be considered unchanged");

      deleteTestFiles(fname);
   }
}
