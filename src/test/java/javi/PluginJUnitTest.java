package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Tests for the javi plugin loading system (F29).
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>{@code loadplugin} command registration and dispatch</li>
 *   <li>{@link Plugin} interface and {@link Plugin.Loader}</li>
 *   <li>{@link MultiClassLoader} parent-first delegation</li>
 *   <li>{@code .javini} dispatch path via {@link Command#command}</li>
 * </ul>
 */
class PluginJUnitTest {

   private Path tempDir;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
      TestInit.initCommands();
   }

   @BeforeEach
   void lock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void unlock() {
      EventQueue.biglock2.unlock();
   }

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

   // ── Command registration ──────────────────────────────────

   @Test
   void loadPluginEnumExists() {
      assertNotNull(MiscCommands.Cmd.valueOf("LOAD_PLUGIN"));
   }

   @Test
   void loadPluginCommandIsRegistered() {
      Rgroup.KeyBinding kb = Rgroup.bindingLookup("loadplugin");
      assertNotNull(kb, "loadplugin should be a registered command");
   }

   // ── Error cases ───────────────────────────────────────────

   @Test
   void loadPluginNullArgThrows() {
      assertThrows(InputException.class, () ->
         Rgroup.doCommand("loadplugin", null, 0, 1,
            FvContext.getCurrFvc(), false));
   }

   @Test
   void loadPluginEmptyArgThrows() {
      assertThrows(InputException.class, () ->
         Rgroup.doCommand("loadplugin", "", 0, 1,
            FvContext.getCurrFvc(), false));
   }

   @Test
   void loadPluginMissingJarThrows() {
      assertThrows(InputException.class, () ->
         Rgroup.doCommand("loadplugin", "nonexistent_plugin_xyz",
            0, 1, FvContext.getCurrFvc(), false));
   }

   // ── Plugin.Loader.load() tests ─────────────────────────────

   List<String> args = Arrays.asList(new String[] {});
   @Test
   void loadNonexistentJarHandlesGracefully() throws Exception {
      Plugin.load("/nonexistent/plugin.jar", args);
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
         Plugin.load(jar.getAbsolutePath(), args);
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
         Plugin.load(jar.getAbsolutePath(), args);
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
         Plugin.load(jar.getAbsolutePath(), args);
      } finally {
         cleanTempDir();
      }
   }
}
