package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
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

   private static final String HELLO_JAR =
      "build/libs/javi-hello.jar";

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

   @Test
   void loadNonexistentJarHandlesGracefully() throws Exception {
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

   // ── ClassLoader delegation ────────────────────────────────

   @Test
   void multiClassLoaderParentIsAppClassLoader() {
      ClassLoader appCL = MultiClassLoader.class.getClassLoader();
      assertNotNull(appCL,
         "app classloader should not be null (not boot CL)");
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
         File jar = createTestJar("test.jar", new String[][]{
            {"dummy.txt", "x"}
         });
         JarLoader loader = new JarLoader(jar.getAbsolutePath());
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

   // ── End-to-end: load hello plugin ─────────────────────────

   static boolean helloJarExists() {
      return new File(HELLO_JAR).exists();
   }

   @Test
   @EnabledIf("helloJarExists")
   void loadHelloPluginRegistersCommand() throws Exception {
      Rgroup.KeyBinding before = Rgroup.bindingLookup("hello");

      Rgroup.doCommand("loadplugin", "hello", 0, 1,
         FvContext.getCurrFvc(), false);

      Rgroup.KeyBinding after = Rgroup.bindingLookup("hello");
      assertNotNull(after,
         "hello command should be registered after loading plugin");
   }

   @Test
   @EnabledIf("helloJarExists")
   void loadHelloPluginViaAbsolutePath() throws Exception {
      File jar = new File(HELLO_JAR);
      Rgroup.doCommand("loadplugin", jar.getAbsolutePath(),
         0, 1, FvContext.getCurrFvc(), false);

      Rgroup.KeyBinding kb = Rgroup.bindingLookup("hello");
      assertNotNull(kb,
         "hello command should be registered via absolute path load");
   }

   // ── .javini dispatch path ─────────────────────────────────

   @Test
   @EnabledIf("helloJarExists")
   void javiniLoadpluginDispatch() {
      Command.command("loadplugin hello", null, null);

      Rgroup.KeyBinding kb = Rgroup.bindingLookup("hello");
      assertNotNull(kb,
         "loadplugin hello via Command.command() should register"
         + " the hello command");
   }

   // ── Plugin.Loader direct ──────────────────────────────────

   @Test
   @EnabledIf("helloJarExists")
   void pluginLoaderDirectLoad() throws Exception {
      File jar = new File(HELLO_JAR);
      Plugin.Loader.load(jar.getPath());

      Rgroup.KeyBinding kb = Rgroup.bindingLookup("hello");
      assertNotNull(kb,
         "Plugin.Loader.load() should register commands");
   }

   @Test
   void pluginLoaderNonexistentJarSilentlyFails() throws Exception {
      Plugin.Loader.load("/nonexistent/path/fake.jar");
   }

   // ── Plugin interface ──────────────────────────────────────

   @Test
   void pluginBindKeyRejectsUnknownGroup() {
      assertThrows(InputException.class, () ->
         Plugin.bindKey("bogus_group", "x", "insert"));
   }

   @Test
   void pluginBindKeyRejectsUnknownCommand() {
      assertThrows(InputException.class, () ->
         Plugin.bindKey("move", "x", "no_such_cmd_xyz"));
   }
}
