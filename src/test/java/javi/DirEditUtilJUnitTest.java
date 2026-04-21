package javi;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for {@link DirEdit} private utility methods
 * via reflection — formatSize and getExtension.
 */
class DirEditUtilJUnitTest {

   @TempDir
   static File tempDir;

   private static DirEdit dirEdit;

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.init();
      EventQueue.biglock2.lock();
      try {
         FileDescriptor.LocalDir localDir =
            FileDescriptor.LocalDir.make(tempDir.getAbsolutePath());
         dirEdit = new DirEdit(localDir);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @BeforeEach
   void setUp() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void tearDown() {
      EventQueue.biglock2.unlock();
   }

   // ── formatSize (private instance method) ─────────────────

   @Nested
   @DisplayName("formatSize()")
   class FormatSize {

      private String invoke(long size) throws Exception {
         Method m = DirEdit.class.getDeclaredMethod(
            "formatSize", long.class);
         m.setAccessible(true);
         return (String) m.invoke(dirEdit, size);
      }

      @Test
      @DisplayName("bytes format for small sizes")
      void bytesFormat() throws Exception {
         assertEquals("0B", invoke(0));
         assertEquals("1B", invoke(1));
         assertEquals("100B", invoke(100));
         assertEquals("1023B", invoke(1023));
      }

      @Test
      @DisplayName("kilobyte format")
      void kilobyteFormat() throws Exception {
         assertEquals("1.0K", invoke(1024));
         assertEquals("1.5K", invoke(1536));
         assertEquals("10.0K", invoke(10240));
      }

      @Test
      @DisplayName("megabyte format")
      void megabyteFormat() throws Exception {
         assertEquals("1.0M", invoke(1024 * 1024));
         assertEquals("2.5M", invoke((long) (2.5 * 1024 * 1024)));
      }

      @Test
      @DisplayName("gigabyte format")
      void gigabyteFormat() throws Exception {
         assertEquals("1.0G", invoke(1024L * 1024 * 1024));
         assertEquals("3.5G",
            invoke((long) (3.5 * 1024 * 1024 * 1024)));
      }
   }

   // ── getExtension (private instance method) ───────────────

   @Nested
   @DisplayName("getExtension()")
   class GetExtension {

      private String invoke(String name) throws Exception {
         Method m = DirEdit.class.getDeclaredMethod(
            "getExtension", String.class);
         m.setAccessible(true);
         return (String) m.invoke(dirEdit, name);
      }

      @Test
      @DisplayName("returns extension for normal files")
      void normalExtension() throws Exception {
         assertEquals("java", invoke("Foo.java"));
         assertEquals("txt", invoke("readme.txt"));
         assertEquals("cc", invoke("main.cc"));
      }

      @Test
      @DisplayName("returns last extension for multiple dots")
      void multipleDots() throws Exception {
         assertEquals("gz", invoke("archive.tar.gz"));
         assertEquals("java", invoke("my.test.java"));
      }

      @Test
      @DisplayName("returns empty for no extension")
      void noExtension() throws Exception {
         assertEquals("", invoke("Makefile"));
         assertEquals("", invoke("README"));
      }

      @Test
      @DisplayName("returns empty for dot at start only")
      void dotAtStart() throws Exception {
         assertEquals("", invoke(".gitignore"));
      }

      @Test
      @DisplayName("returns empty for dot at end")
      void dotAtEnd() throws Exception {
         assertEquals("", invoke("file."));
      }
   }

   // ── DirSizeCalculator static methods ─────────────────────

   @Nested
   @DisplayName("DirSizeCalculator")
   class DirSizeCalc {

      @Test
      @DisplayName("walkDirectorySize counts file bytes")
      void walkCountsBytes(@TempDir Path walkDir) throws IOException {
         // Create some files with known sizes
         Files.writeString(walkDir.resolve("a.txt"), "hello"); // 5 bytes
         Files.writeString(walkDir.resolve("b.txt"), "world!"); // 6 bytes
         long size = DirEdit.DirSizeCalculator.walkDirectorySize(
            walkDir.toString());
         assertEquals(11, size);
      }

      @Test
      @DisplayName("walkDirectorySize includes subdirectory files")
      void walkIncludesSubdirs(@TempDir Path walkDir)
            throws IOException {
         Path sub = walkDir.resolve("sub");
         Files.createDirectories(sub);
         Files.writeString(walkDir.resolve("top.txt"), "aaa"); // 3
         Files.writeString(sub.resolve("deep.txt"), "bbbbb"); // 5
         long size = DirEdit.DirSizeCalculator.walkDirectorySize(
            walkDir.toString());
         assertEquals(8, size);
      }

      @Test
      @DisplayName("walkDirectorySize returns 0 for empty directory")
      void walkEmptyDir(@TempDir Path walkDir) {
         long size = DirEdit.DirSizeCalculator.walkDirectorySize(
            walkDir.toString());
         assertEquals(0, size);
      }

      @Test
      @DisplayName("walkDirectorySize returns 0 for nonexistent path")
      void walkNonexistent() {
         long size = DirEdit.DirSizeCalculator.walkDirectorySize(
            "/nonexistent/path/xyz123");
         assertEquals(0, size);
      }

      @Test
      @DisplayName("getCachedSize returns null for unknown path")
      void getCachedNull() {
         assertNull(DirEdit.DirSizeCalculator.getCachedSize(
            "/some/random/uncached/path"));
      }

      @Test
      @DisplayName("clearCache empties the cache")
      void clearCacheWorks(@TempDir Path walkDir) throws IOException {
         // Walk a directory to populate cache
         Files.writeString(walkDir.resolve("f.txt"), "data");
         // Manually put into cache by walking
         DirEdit.DirSizeCalculator.walkDirectorySize(
            walkDir.toString());
         // clearCache should remove everything
         DirEdit.DirSizeCalculator.clearCache();
         assertEquals(0, DirEdit.DirSizeCalculator.cacheSize());
      }

      @Test
      @DisplayName("cacheSize returns current count")
      void cacheSizeReturnsCount() {
         DirEdit.DirSizeCalculator.clearCache();
         assertEquals(0, DirEdit.DirSizeCalculator.cacheSize());
      }

      @Test
      @DisplayName("watchCount returns non-negative")
      void watchCountNonNeg() {
         assertTrue(DirEdit.DirSizeCalculator.watchCount() >= 0);
      }
   }
}
