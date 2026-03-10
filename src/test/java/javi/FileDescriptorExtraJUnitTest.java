package javi;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional JUnit 5 tests for {@link FileDescriptor} covering
 * {@code isSpecial}, {@code InternalFd}, and {@code LocalFile} operations
 * not exercised by the path-normalization tests in
 * {@link FileDescriptorJUnitTest}.
 */
class FileDescriptorExtraJUnitTest {

   // ================================================================
   // isSpecial
   // ================================================================

   @Nested
   @DisplayName("isSpecial")
   class IsSpecialTests {

      @Test
      @DisplayName("absolute path starting with / is special")
      void absoluteSlashIsSpecial() {
         assertTrue(FileDescriptor.isSpecial("/usr/bin/foo"));
      }

      @Test
      @DisplayName("dotslash prefix is special")
      void dotSlashIsSpecial() {
         assertTrue(FileDescriptor.isSpecial("./myfile"));
      }

      @Test
      @DisplayName("plain filename is not special")
      void plainNameNotSpecial() {
         assertFalse(FileDescriptor.isSpecial("readme.txt"));
      }

      @Test
      @DisplayName("relative path without ./ is not special")
      void relativeNotSpecial() {
         assertFalse(FileDescriptor.isSpecial("src/main/Foo.java"));
      }

      @Test
      @DisplayName("dot alone is not special")
      void dotAloneNotSpecial() {
         assertFalse(FileDescriptor.isSpecial("."));
      }

      @Test
      @DisplayName("dotdot prefix is not special")
      void dotDotNotSpecial() {
         assertFalse(FileDescriptor.isSpecial("../other"));
      }

      @Test
      @DisplayName("drive letter C: is special")
      void driveLetterSpecial() {
         assertTrue(FileDescriptor.isSpecial("C:/windows"));
      }

      @Test
      @DisplayName("lowercase drive letter is special")
      void lowerDriveLetterSpecial() {
         assertTrue(FileDescriptor.isSpecial("d:/data"));
      }

      @Test
      @DisplayName("non-letter first char with colon is not special")
      void nonLetterColonNotSpecial() {
         assertFalse(FileDescriptor.isSpecial("1:/bad"));
      }

      @Test
      @DisplayName("single char filename is not special")
      void singleCharNotSpecial() {
         assertFalse(FileDescriptor.isSpecial("x"));
      }
   }

   // ================================================================
   // InternalFd
   // ================================================================

   @Nested
   @DisplayName("InternalFd")
   class InternalFdTests {

      @Test
      @DisplayName("make returns non-null")
      void makeReturnsNonNull() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("scratch");
         assertNotNull(fd);
      }

      @Test
      @DisplayName("shortName matches input")
      void shortNameMatches() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("mybuf");
         assertEquals("mybuf", fd.shortName);
      }

      @Test
      @DisplayName("canonName contains shortName")
      void canonNameContainsShort() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("test");
         assertTrue(fd.canonName.startsWith("test "));
      }

      @Test
      @DisplayName("two InternalFds have different canonNames")
      void uniqueCanonNames() {
         FileDescriptor a = FileDescriptor.InternalFd.make("dup");
         FileDescriptor b = FileDescriptor.InternalFd.make("dup");
         assertEquals(a.shortName, b.shortName);
         assertNotEquals(a.canonName, b.canonName);
      }

      @Test
      @DisplayName("exists returns false")
      void existsReturnsFalse() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("nofile");
         assertFalse(fd.exists());
      }

      @Test
      @DisplayName("canWrite returns false")
      void canWriteReturnsFalse() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("nowrite");
         assertFalse(fd.canWrite());
      }

      @Test
      @DisplayName("isFile returns false")
      void isFileReturnsFalse() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("nofile2");
         assertFalse(fd.isFile());
      }

      @Test
      @DisplayName("toPath throws IOException")
      void toPathThrows() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("nopath");
         assertThrows(IOException.class, fd::toPath);
      }

      @Test
      @DisplayName("getOutputStream throws IOException")
      void getOutputStreamThrows() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("noout");
         assertThrows(IOException.class, fd::getOutputStream);
      }

      @Test
      @DisplayName("getString throws IOException")
      void getStringThrows() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("noread");
         assertThrows(IOException.class, fd::getString);
      }

      @Test
      @DisplayName("delete throws IOException")
      void deleteThrows() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("nodel");
         assertThrows(IOException.class, fd::delete);
      }

      @Test
      @DisplayName("getPersistantFd returns null")
      void persistantFdNull() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("nop");
         assertNull(fd.getPersistantFd());
      }
   }

   // ================================================================
   // LocalFile via temp files
   // ================================================================

   @Nested
   @DisplayName("LocalFile")
   class LocalFileTests {

      @Test
      @DisplayName("createTempFile produces existing file")
      void tempFileExists() throws IOException {
         FileDescriptor.LocalFile tf =
            FileDescriptor.LocalFile.createTempFile("junit", ".tmp");
         tf.deleteOnExit();
         assertTrue(tf.exists());
         assertTrue(tf.isFile());
         tf.delete();
      }

      @Test
      @DisplayName("canWrite on temp file")
      void tempFileCanWrite() throws IOException {
         FileDescriptor.LocalFile tf =
            FileDescriptor.LocalFile.createTempFile("junit", ".tmp");
         tf.deleteOnExit();
         assertTrue(tf.canWrite());
         tf.delete();
      }

      @Test
      @DisplayName("delete removes file")
      void deleteRemovesFile() throws IOException {
         FileDescriptor.LocalFile tf =
            FileDescriptor.LocalFile.createTempFile("junit", ".tmp");
         assertTrue(tf.exists());
         tf.delete();
         assertFalse(tf.exists());
      }

      @Test
      @DisplayName("toPath returns valid Path")
      void toPathValid() throws IOException {
         FileDescriptor.LocalFile tf =
            FileDescriptor.LocalFile.createTempFile("junit", ".tmp");
         tf.deleteOnExit();
         Path p = tf.toPath();
         assertNotNull(p);
         assertTrue(java.nio.file.Files.exists(p));
         tf.delete();
      }

      @Test
      @DisplayName("getOutputStream writes data")
      void getOutputStreamWrites() throws IOException {
         FileDescriptor.LocalFile tf =
            FileDescriptor.LocalFile.createTempFile("junit", ".tmp");
         tf.deleteOnExit();
         try (OutputStream os = tf.getOutputStream()) {
            os.write("hello".getBytes(StandardCharsets.UTF_8));
         }
         String content = tf.getString();
         assertEquals("hello", content);
         tf.delete();
      }

      @Test
      @DisplayName("getString reads file content")
      void getStringReadsContent() throws IOException {
         FileDescriptor.LocalFile tf =
            FileDescriptor.LocalFile.createTempFile("junit", ".tmp");
         tf.deleteOnExit();
         try (OutputStream os = tf.getOutputStream()) {
            os.write("test data".getBytes(StandardCharsets.UTF_8));
         }
         assertEquals("test data", tf.getString());
         tf.delete();
      }

      @Test
      @DisplayName("equals is identity-based on canonName")
      void equalsOnCanonName() {
         FileDescriptor a = FileDescriptor.make("build.gradle");
         FileDescriptor b = FileDescriptor.make("build.gradle");
         assertEquals(a, b);
      }

      @Test
      @DisplayName("hashCode consistent with equals")
      void hashCodeConsistent() {
         FileDescriptor a = FileDescriptor.make("build.gradle");
         FileDescriptor b = FileDescriptor.make("build.gradle");
         assertEquals(a.hashCode(), b.hashCode());
      }

      @Test
      @DisplayName("make for nonexistent file still creates descriptor")
      void makeNonexistent() {
         FileDescriptor fd = FileDescriptor.make("no_such_file_xyz.txt");
         assertNotNull(fd);
         assertFalse(fd.exists());
      }

      @Test
      @DisplayName("length of temp file matches written bytes")
      void lengthMatchesWritten() throws IOException {
         FileDescriptor.LocalFile tf =
            FileDescriptor.LocalFile.createTempFile("junit", ".tmp");
         tf.deleteOnExit();
         byte[] data = "twelve chars".getBytes(StandardCharsets.UTF_8);
         try (OutputStream os = tf.getOutputStream()) {
            os.write(data);
         }
         assertEquals(data.length, tf.length());
         tf.delete();
      }

      @Test
      @DisplayName("lastModified is positive for existing file")
      void lastModifiedPositive() throws IOException {
         FileDescriptor.LocalFile tf =
            FileDescriptor.LocalFile.createTempFile("junit", ".tmp");
         tf.deleteOnExit();
         assertTrue(tf.lastModified() > 0);
         tf.delete();
      }

      @Test
      @DisplayName("getPersistantFd returns dmp2 descriptor")
      void persistantFdDmp2() throws IOException {
         FileDescriptor.LocalFile tf =
            FileDescriptor.LocalFile.createTempFile("junit", ".tmp");
         tf.deleteOnExit();
         FileDescriptor pfd = tf.getPersistantFd();
         assertNotNull(pfd);
         assertTrue(pfd.canonName.endsWith(".dmp2"));
         tf.delete();
      }
   }
}
