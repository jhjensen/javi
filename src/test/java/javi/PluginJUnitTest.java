package javi;

import org.junit.jupiter.api.Test;
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
 * Tests for {@link Plugin} interface and {@link Plugin.Loader}.
 *
 * <p>Tests plugin loading error paths — invalid JARs, missing classes,
 * malformed class files. Loader.load() catches all errors internally,
 * so these test that it handles errors gracefully without throwing.
 */
class PluginJUnitTest {

   private Path tempDir;

   private void createTempDir() throws IOException {
      tempDir = Files.createTempDirectory("plugin-test");
   }

   private void cleanTempDir() throws IOException {
      if (tempDir != null) {
         Files.walk(tempDir)
            .sorted(java.util.Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
      }
   }

   private File createTestJar(String name, String[][] entries)
         throws IOException {
      File jarFile = tempDir.resolve(name).toFile();
      try (ZipOutputStream zos = new ZipOutputStream(
            new FileOutputStream(jarFile))) {
         for (String[] entry : entries) {
            zos.putNextEntry(new ZipEntry(entry[0]));
            zos.write(entry[1].getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
         }
      }
      return jarFile;
   }

   // ── Plugin.Loader.load() tests ─────────────────────────────

   @Test
   void loadNonexistentJarHandlesGracefully() throws Exception {
      // load() catches all Throwable — should not throw
      Plugin.Loader.load("/nonexistent/plugin.jar");
   }

   @Test
   void loadEmptyJarHandlesGracefully() throws Exception {
      createTempDir();
      try {
         File jar = tempDir.resolve("empty.jar").toFile();
         try (ZipOutputStream zos = new ZipOutputStream(
               new FileOutputStream(jar))) {
            // empty JAR — no entries
         }
         Plugin.Loader.load(jar.getAbsolutePath());
      } finally {
         cleanTempDir();
      }
   }

   @Test
   void loadJarWithoutPluginClassHandlesGracefully() throws Exception {
      createTempDir();
      try {
         File jar = createTestJar("noplugin.jar", new String[][]{
            {"README.txt", "not a plugin"}
         });
         Plugin.Loader.load(jar.getAbsolutePath());
      } finally {
         cleanTempDir();
      }
   }

   @Test
   void loadJarWithMalformedClassHandlesGracefully() throws Exception {
      createTempDir();
      try {
         File jar = createTestJar("badclass.jar", new String[][]{
            {"javi/plugin/FindBugs.class", "not valid bytecode"}
         });
         Plugin.Loader.load(jar.getAbsolutePath());
      } finally {
         cleanTempDir();
      }
   }

   // ── MultiClassLoader tests ─────────────────────────────────

   @Test
   void formatClassNameDefaultUsesDotToSlash() throws IOException {
      createTempDir();
      try {
         File jar = createTestJar("test.jar", new String[][]{
            {"dummy.txt", "x"}
         });
         JarLoader loader = new JarLoader(jar.getAbsolutePath());
         // Default: dots → slashes, appends .class
         String formatted = loader.formatClassName("com.example.Hello");
         assertEquals("com/example/Hello.class", formatted);
      } finally {
         cleanTempDir();
      }
   }

   @Test
   void formatClassNameWithReplacementChar() throws IOException {
      createTempDir();
      try {
         File jar = createTestJar("test.jar", new String[][]{
            {"dummy.txt", "x"}
         });
         JarLoader loader = new JarLoader(jar.getAbsolutePath());
         loader.setClassNameReplacementChar('_');
         String formatted = loader.formatClassName("com.example.Hello");
         assertEquals("com_example_Hello.class", formatted);
      } finally {
         cleanTempDir();
      }
   }

   @Test
   void formatClassNameNoPackage() throws IOException {
      createTempDir();
      try {
         File jar = createTestJar("test.jar", new String[][]{
            {"dummy.txt", "x"}
         });
         JarLoader loader = new JarLoader(jar.getAbsolutePath());
         String formatted = loader.formatClassName("Simple");
         assertEquals("Simple.class", formatted);
      } finally {
         cleanTempDir();
      }
   }

   @Test
   void loaderCachesLoadedClass() throws Exception {
      createTempDir();
      try {
         // Test that system classes are cached properly
         File jar = createTestJar("test.jar", new String[][]{
            {"dummy.txt", "x"}
         });
         JarLoader loader = new JarLoader(jar.getAbsolutePath());
         // Loading a system class should work and cache it
         Class<?> c1 = loader.loadClass("java.lang.String");
         Class<?> c2 = loader.loadClass("java.lang.String");
         assertSame(c1, c2);
      } finally {
         cleanTempDir();
      }
   }

   @Test
   void loaderThrowsForMissingClass() throws IOException {
      createTempDir();
      try {
         File jar = createTestJar("test.jar", new String[][]{
            {"dummy.txt", "x"}
         });
         JarLoader loader = new JarLoader(jar.getAbsolutePath());
         assertThrows(ClassNotFoundException.class, () -> {
            loader.loadClass("com.nonexistent.DoesNotExist");
         });
      } finally {
         cleanTempDir();
      }
   }
}
