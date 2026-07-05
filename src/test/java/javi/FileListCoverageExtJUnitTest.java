package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage for {@link FileList} — isContentUnchanged,
 * countModified, and file-level utility methods exercised
 * without AWT.
 */
class FileListCoverageExtJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   private static String testPath(String name) {
      return history.Testutil.testFile(name).getPath();
   }

   private static FileDescriptor.LocalFile makeLocal(String name) {
      return FileDescriptor.LocalFile.make(
         history.Testutil.testFile(name));
   }

   private static TextEdit<String> openTestFile(String name)
         throws IOException {
      String path = testPath(name);
      makeLocal(name).delete();
      try (java.io.OutputStreamWriter w =
            new java.io.OutputStreamWriter(
               new java.io.FileOutputStream(path),
               java.nio.charset.StandardCharsets.UTF_8)) {
         w.write("line one\nline two\nline three\n");
      }
      FileDescriptor fd = FileDescriptor.make(path);
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      return te;
   }

   private static void deleteTestFiles(String... names)
         throws IOException {
      for (String name : names) {
         makeLocal(name).delete();
         makeLocal(name + ".dmp2").delete();
      }
   }

   // ── isContentUnchanged ──────────────────────────────────

   @Test
   @DisplayName("isContentUnchanged: matching content returns true")
   void isContentUnchangedMatching() throws Exception {
      String fname = "ju_flce_match";
      deleteTestFiles(fname);
      TextEdit<String> te = openTestFile(fname);
      @SuppressWarnings({ "unchecked", "rawtypes" })
      FileProperties<String> fp = te.getFileProperties();
      fp.updateModifiedTime();

      assertTrue(FileList.TestAccess.isContentUnchanged(te, fp),
         "file content matches buffer — should be unchanged");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("isContentUnchanged: modified buffer returns false")
   void isContentUnchangedModified() throws Exception {
      String fname = "ju_flce_mod";
      deleteTestFiles(fname);
      TextEdit<String> te = openTestFile(fname);
      @SuppressWarnings({ "unchecked", "rawtypes" })
      FileProperties<String> fp = te.getFileProperties();
      fp.updateModifiedTime();

      // Modify buffer without saving
      te.inserttext("extra\n", 0, 1);
      te.checkpoint();

      assertFalse(FileList.TestAccess.isContentUnchanged(te, fp),
         "buffer was modified — should not match disk");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("isContentUnchanged: empty file returns true")
   void isContentUnchangedEmptyFile() throws Exception {
      String fname = "ju_flce_empty";
      deleteTestFiles(fname);
      String path = testPath(fname);
      try (java.io.FileOutputStream fos =
            new java.io.FileOutputStream(path)) {
         // empty file
      }
      FileDescriptor fd = FileDescriptor.make(path);
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      fp.updateModifiedTime();

      assertTrue(FileList.TestAccess.isContentUnchanged(te, fp),
         "empty file should be unchanged");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("isContentUnchanged: internal FD returns false")
   void isContentUnchangedInternalFd() throws Exception {
      FileProperties<String> fp = new FileProperties<>(
         FileDescriptor.InternalFd.make("ju_flce_internal"),
         StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();

      assertFalse(FileList.TestAccess.isContentUnchanged(te, fp),
         "internal FD should return false");

      te.disposeFvc();
   }

   @Test
   @DisplayName("isContentUnchanged: file size differs returns false")
   void isContentUnchangedSizeDiffers() throws Exception {
      String fname = "ju_flce_size";
      deleteTestFiles(fname);
      TextEdit<String> te = openTestFile(fname);
      @SuppressWarnings({ "unchecked", "rawtypes" })
      FileProperties<String> fp = te.getFileProperties();
      fp.updateModifiedTime();

      // Rewrite file with different content (different size)
      String path = testPath(fname);
      try (java.io.OutputStreamWriter w =
            new java.io.OutputStreamWriter(
               new java.io.FileOutputStream(path),
               java.nio.charset.StandardCharsets.UTF_8)) {
         w.write("different content with extra chars\n");
      }

      assertFalse(FileList.TestAccess.isContentUnchanged(te, fp),
         "file size changed — should return false");

      te.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── FileProperties external modification detection ──────

   @Test
   @DisplayName("FileProperties checkModified: unmodified file")
   void checkModifiedUnchanged() throws Exception {
      String fname = "ju_flce_chkmod";
      deleteTestFiles(fname);
      String path = testPath(fname);
      try (java.io.OutputStreamWriter w =
            new java.io.OutputStreamWriter(
               new java.io.FileOutputStream(path),
               java.nio.charset.StandardCharsets.UTF_8)) {
         w.write("hello\n");
      }
      FileDescriptor fd = FileDescriptor.make(path);
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      fp.updateModifiedTime();

      assertFalse(fp.checkModified(),
         "file not touched — checkModified should be false");

      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("FileProperties checkModified: file rewritten externally")
   void checkModifiedAfterRewrite() throws Exception {
      String fname = "ju_flce_chkmod2";
      deleteTestFiles(fname);
      String path = testPath(fname);
      try (java.io.OutputStreamWriter w =
            new java.io.OutputStreamWriter(
               new java.io.FileOutputStream(path),
               java.nio.charset.StandardCharsets.UTF_8)) {
         w.write("original\n");
      }
      FileDescriptor fd = FileDescriptor.make(path);
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      fp.updateModifiedTime();

      // Sleep briefly then rewrite to get different mtime
      Thread.sleep(1100);
      try (java.io.OutputStreamWriter w =
            new java.io.OutputStreamWriter(
               new java.io.FileOutputStream(path),
               java.nio.charset.StandardCharsets.UTF_8)) {
         w.write("modified\n");
      }

      assertTrue(fp.checkModified(),
         "file was rewritten — checkModified should be true");

      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("FileProperties ignoreExternalChanges suppresses check")
   void ignoreExternalChanges() throws Exception {
      String fname = "ju_flce_ignore";
      deleteTestFiles(fname);
      String path = testPath(fname);
      try (java.io.OutputStreamWriter w =
            new java.io.OutputStreamWriter(
               new java.io.FileOutputStream(path),
               java.nio.charset.StandardCharsets.UTF_8)) {
         w.write("data\n");
      }
      FileDescriptor fd = FileDescriptor.make(path);
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      fp.updateModifiedTime();
      fp.setIgnoreExternalChanges(true);

      assertTrue(fp.isIgnoringExternalChanges());

      // Even after rewrite, checkModified should return false
      Thread.sleep(1100);
      try (java.io.OutputStreamWriter w =
            new java.io.OutputStreamWriter(
               new java.io.FileOutputStream(path),
               java.nio.charset.StandardCharsets.UTF_8)) {
         w.write("changed\n");
      }

      assertFalse(fp.checkModified(),
         "ignore flag should suppress modification detection");

      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("FileProperties readOnly round-trip")
   void readOnlyRoundTrip() throws Exception {
      FileDescriptor fd = FileDescriptor.InternalFd.make("ju_flce_ro");
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);

      assertTrue(fp.isWriteable(), "default is writable");
      fp.setReadOnly(true);
      assertFalse(fp.isWriteable(), "should be read-only after set");
      fp.setReadOnly(false);
      assertTrue(fp.isWriteable(), "should be writable again");
   }

   @Test
   @DisplayName("FileProperties checkModified: internal FD returns false")
   void checkModifiedInternalFd() {
      FileDescriptor fd = FileDescriptor.InternalFd.make("ju_flce_intmod");
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      fp.updateModifiedTime();
      assertFalse(fp.checkModified(),
         "internal FD should not report modified");
   }

   @Test
   @DisplayName("FileProperties checkModified: uninitialized returns false")
   void checkModifiedUninitialized() throws Exception {
      String fname = "ju_flce_uninit";
      deleteTestFiles(fname);
      String path = testPath(fname);
      try (java.io.OutputStreamWriter w =
            new java.io.OutputStreamWriter(
               new java.io.FileOutputStream(path),
               java.nio.charset.StandardCharsets.UTF_8)) {
         w.write("test\n");
      }
      FileDescriptor fd = FileDescriptor.make(path);
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      // do NOT call updateModifiedTime — leave uninitialized
      assertFalse(fp.checkModified(),
         "uninitialized mod time should not report modified");

      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("FileProperties getLineSeparator returns system default")
   void lineSeparatorDefault() {
      FileDescriptor fd = FileDescriptor.InternalFd.make("ju_flce_lsep");
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      assertNotNull(fp.getLineSeparator());
      assertEquals(System.getProperty("line.separator"),
         fp.getLineSeparator());
   }

   @Test
   @DisplayName("FileProperties getLastFileSize for internal FD is 0")
   void fileSizeInternalFd() {
      FileDescriptor fd = FileDescriptor.InternalFd.make("ju_flce_fsize");
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      assertEquals(0, fp.getLastFileSize());
   }

   @Test
   @DisplayName("FileProperties getLastFileSize after update")
   void fileSizeAfterUpdate() throws Exception {
      String fname = "ju_flce_fsup";
      deleteTestFiles(fname);
      String path = testPath(fname);
      try (java.io.OutputStreamWriter w =
            new java.io.OutputStreamWriter(
               new java.io.FileOutputStream(path),
               java.nio.charset.StandardCharsets.UTF_8)) {
         w.write("0123456789\n"); // 11 bytes
      }
      FileDescriptor fd = FileDescriptor.make(path);
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      fp.updateModifiedTime();
      assertTrue(fp.getLastFileSize() > 0,
         "file size should be positive after update");

      deleteTestFiles(fname);
   }
}
