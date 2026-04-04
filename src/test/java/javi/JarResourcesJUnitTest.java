package javi;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Tests for {@link JarResources} — ZIP/JAR resource extraction.
 * Creates temporary ZIP files to test resource loading without
 * requiring actual JAR dependencies or AWT.
 */
class JarResourcesJUnitTest {

   private Path tempDir;

   @BeforeEach
   void setUp() throws IOException {
      tempDir = Files.createTempDirectory("jarresources-test");
   }

   @AfterEach
   void tearDown() throws IOException {
      if (tempDir != null) {
         Files.walk(tempDir)
            .sorted(java.util.Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
      }
   }

   private File createTestZip(String name, String[][] entries)
         throws IOException {
      File zipFile = tempDir.resolve(name).toFile();
      try (ZipOutputStream zos = new ZipOutputStream(
            new FileOutputStream(zipFile))) {
         for (String[] entry : entries) {
            zos.putNextEntry(new ZipEntry(entry[0]));
            zos.write(entry[1].getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
         }
      }
      return zipFile;
   }

   @Test
   void testLoadSingleResource() throws IOException {
      File zip = createTestZip("test.zip", new String[][]{
         {"hello.txt", "Hello, World!"}
      });
      JarResources jr = new JarResources(zip.getAbsolutePath());
      byte[] data = jr.getResource("hello.txt");
      assertNotNull(data);
      assertEquals("Hello, World!",
         new String(data, StandardCharsets.UTF_8));
   }

   @Test
   void testLoadMultipleResources() throws IOException {
      File zip = createTestZip("multi.zip", new String[][]{
         {"file1.txt", "content1"},
         {"file2.txt", "content2"},
         {"dir/file3.txt", "content3"}
      });
      JarResources jr = new JarResources(zip.getAbsolutePath());
      assertEquals("content1",
         new String(jr.getResource("file1.txt"), StandardCharsets.UTF_8));
      assertEquals("content2",
         new String(jr.getResource("file2.txt"), StandardCharsets.UTF_8));
      assertEquals("content3",
         new String(jr.getResource("dir/file3.txt"),
            StandardCharsets.UTF_8));
   }

   @Test
   void testResourceNotFound() throws IOException {
      File zip = createTestZip("test.zip", new String[][]{
         {"exists.txt", "data"}
      });
      JarResources jr = new JarResources(zip.getAbsolutePath());
      assertNull(jr.getResource("nonexistent.txt"));
   }

   @Test
   void testEmptyResource() throws IOException {
      File zip = createTestZip("empty.zip", new String[][]{
         {"empty.txt", ""}
      });
      JarResources jr = new JarResources(zip.getAbsolutePath());
      byte[] data = jr.getResource("empty.txt");
      assertNotNull(data);
      assertEquals(0, data.length);
   }

   @Test
   void testDirectoryEntriesSkipped() throws IOException {
      File zipFile = tempDir.resolve("withdir.zip").toFile();
      try (ZipOutputStream zos = new ZipOutputStream(
            new FileOutputStream(zipFile))) {
         // Add a directory entry
         ZipEntry dirEntry = new ZipEntry("mydir/");
         zos.putNextEntry(dirEntry);
         zos.closeEntry();
         // Add a file inside the directory
         zos.putNextEntry(new ZipEntry("mydir/file.txt"));
         zos.write("inside dir".getBytes(StandardCharsets.UTF_8));
         zos.closeEntry();
      }
      JarResources jr = new JarResources(zipFile.getAbsolutePath());
      // Directory entry should not be loaded as a resource
      assertNull(jr.getResource("mydir/"));
      // File inside directory should be loaded
      assertNotNull(jr.getResource("mydir/file.txt"));
   }

   @Test
   void testLargeResource() throws IOException {
      // Create a resource larger than typical buffer sizes
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 10000; i++) {
         sb.append("Line ").append(i).append('\n');
      }
      String largeContent = sb.toString();
      File zip = createTestZip("large.zip", new String[][]{
         {"large.txt", largeContent}
      });
      JarResources jr = new JarResources(zip.getAbsolutePath());
      byte[] data = jr.getResource("large.txt");
      assertNotNull(data);
      assertEquals(largeContent,
         new String(data, StandardCharsets.UTF_8));
   }

   @Test
   void testInvalidFileThrows() {
      assertThrows(IOException.class, () -> {
         new JarResources("/nonexistent/path/fake.jar");
      });
   }

   @Test
   void testBinaryResource() throws IOException {
      // Test with binary data (non-UTF-8 bytes)
      File zipFile = tempDir.resolve("binary.zip").toFile();
      byte[] binaryData = new byte[256];
      for (int i = 0; i < 256; i++) {
         binaryData[i] = (byte) i;
      }
      try (ZipOutputStream zos = new ZipOutputStream(
            new FileOutputStream(zipFile))) {
         zos.putNextEntry(new ZipEntry("data.bin"));
         zos.write(binaryData);
         zos.closeEntry();
      }
      JarResources jr = new JarResources(zipFile.getAbsolutePath());
      byte[] data = jr.getResource("data.bin");
      assertNotNull(data);
      assertArrayEquals(binaryData, data);
   }

   @Test
   void testJarLoaderLoadClassBytes() throws IOException {
      // JarLoader delegates to JarResources for class bytes
      File zip = createTestZip("classes.jar", new String[][]{
         {"com/example/Test.class", "fake class data"}
      });
      JarLoader loader = new JarLoader(zip.getAbsolutePath());
      // loadClassBytes finds the entry via formatClassName
      // but defineClass rejects invalid bytecode with ClassFormatError
      assertThrows(ClassFormatError.class, () -> {
         loader.loadClass("com.example.Test");
      });
   }

   @Test
   void testMultiClassLoaderReplacementChar() throws IOException {
      File zip = createTestZip("classes.jar", new String[][]{
         {"com_example_Hello.class", "fake class data"}
      });
      JarLoader loader = new JarLoader(zip.getAbsolutePath());
      loader.setClassNameReplacementChar('_');
      // With replacement char '_', "com.example.Hello" → "com_example_Hello.class"
      assertThrows(ClassFormatError.class, () -> {
         loader.loadClass("com.example.Hello");
      });
   }
}
