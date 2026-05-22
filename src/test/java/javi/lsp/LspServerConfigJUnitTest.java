package javi.lsp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link LspServerConfig} — language server configuration.
 */
class LspServerConfigJUnitTest {

   @Nested
   @DisplayName("construction")
   class ConstructionTests {

      @Test
      @DisplayName("basic construction stores fields")
      void basicConstruction() {
         LspServerConfig config = new LspServerConfig(
            "java",
            new String[]{"jdtls"},
            new String[]{".java"},
            "build.gradle");
         assertEquals("java", config.languageId);
         assertEquals(1, config.command.length);
         assertEquals("jdtls", config.command[0]);
         assertEquals(1, config.fileExtensions.length);
         assertEquals(".java", config.fileExtensions[0]);
         assertEquals("build.gradle", config.rootPattern);
         assertTrue(config.enabled);
      }

      @Test
      @DisplayName("multi-arg command")
      void multiArgCommand() {
         LspServerConfig config = new LspServerConfig(
            "typescript",
            new String[]{"typescript-language-server", "--stdio"},
            new String[]{".ts", ".js"},
            "package.json");
         assertEquals(2, config.command.length);
         assertEquals("--stdio", config.command[1]);
         assertEquals(2, config.fileExtensions.length);
      }

      @Test
      @DisplayName("null rootPattern is allowed")
      void nullRootPattern() {
         LspServerConfig config = new LspServerConfig(
            "text", new String[]{"textls"}, new String[]{".txt"}, null);
         assertNull(config.rootPattern);
      }

      @Test
      @DisplayName("toString contains languageId and command")
      void toStringContainsInfo() {
         LspServerConfig config = new LspServerConfig(
            "java", new String[]{"jdtls"}, new String[]{".java"}, null);
         String str = config.toString();
         assertTrue(str.contains("java"));
         assertTrue(str.contains("jdtls"));
      }
   }

   @Nested
   @DisplayName("getDefaults()")
   class DefaultsTests {

      @Test
      @DisplayName("returns non-empty map")
      void nonEmpty() {
         Map<String, LspServerConfig> defaults =
            LspServerConfig.getDefaults();
         assertNotNull(defaults);
         assertFalse(defaults.isEmpty());
      }

      @Test
      @DisplayName("contains java configuration")
      void containsJava() {
         Map<String, LspServerConfig> defaults =
            LspServerConfig.getDefaults();
         LspServerConfig java = defaults.get("java");
         assertNotNull(java, "should have java config");
         assertEquals("java", java.languageId);
         assertTrue(java.fileExtensions.length > 0);
         assertEquals(".java", java.fileExtensions[0]);
      }

      @Test
      @DisplayName("contains python configuration")
      void containsPython() {
         Map<String, LspServerConfig> defaults =
            LspServerConfig.getDefaults();
         assertNotNull(defaults.get("python"));
      }

      @Test
      @DisplayName("contains typescript configuration")
      void containsTypescript() {
         Map<String, LspServerConfig> defaults =
            LspServerConfig.getDefaults();
         LspServerConfig ts = defaults.get("typescript");
         assertNotNull(ts);
         assertTrue(ts.fileExtensions.length >= 2,
            "typescript should handle .ts and .js at minimum");
      }

      @Test
      @DisplayName("contains c/c++ configuration")
      void containsC() {
         Map<String, LspServerConfig> defaults =
            LspServerConfig.getDefaults();
         assertNotNull(defaults.get("c"));
      }

      @Test
      @DisplayName("contains rust configuration")
      void containsRust() {
         Map<String, LspServerConfig> defaults =
            LspServerConfig.getDefaults();
         assertNotNull(defaults.get("rust"));
      }
   }

   @Nested
   @DisplayName("getExtension()")
   class GetExtensionTests {

      @Test
      @DisplayName("extracts .java from filename")
      void javaExtension() {
         assertEquals(".java",
            LspServerConfig.getExtension("Foo.java"));
      }

      @Test
      @DisplayName("extracts .py from path")
      void pyExtension() {
         assertEquals(".py",
            LspServerConfig.getExtension("/home/user/script.py"));
      }

      @Test
      @DisplayName("returns empty for no extension")
      void noExtension() {
         assertEquals("",
            LspServerConfig.getExtension("Makefile"));
      }

      @Test
      @DisplayName("returns empty for null")
      void nullFile() {
         assertEquals("",
            LspServerConfig.getExtension(null));
      }

      @Test
      @DisplayName("extracts last extension from multiple dots")
      void multipleDots() {
         assertEquals(".gz",
            LspServerConfig.getExtension("archive.tar.gz"));
      }
   }

   @Nested
   @DisplayName("forExtension()")
   class ForExtensionTests {

      @Test
      @DisplayName("finds java config for .java")
      void findsJava() {
         Map<String, LspServerConfig> defaults =
            LspServerConfig.getDefaults();
         LspServerConfig config =
            LspServerConfig.forExtension(defaults, ".java");
         assertNotNull(config);
         assertEquals("java", config.languageId);
      }

      @Test
      @DisplayName("finds typescript config for .ts")
      void findsTs() {
         Map<String, LspServerConfig> defaults =
            LspServerConfig.getDefaults();
         LspServerConfig config =
            LspServerConfig.forExtension(defaults, ".ts");
         assertNotNull(config);
         assertEquals("typescript", config.languageId);
      }

      @Test
      @DisplayName("finds typescript config for .js")
      void findsJs() {
         Map<String, LspServerConfig> defaults =
            LspServerConfig.getDefaults();
         LspServerConfig config =
            LspServerConfig.forExtension(defaults, ".js");
         assertNotNull(config);
         assertEquals("typescript", config.languageId);
      }

      @Test
      @DisplayName("returns null for unknown extension")
      void unknownExt() {
         Map<String, LspServerConfig> defaults =
            LspServerConfig.getDefaults();
         assertNull(
            LspServerConfig.forExtension(defaults, ".xyz"));
      }

      @Test
      @DisplayName("disabled config is not found")
      void disabledConfig() {
         Map<String, LspServerConfig> configs =
            LspServerConfig.getDefaults();
         LspServerConfig java = configs.get("java");
         java.enabled = false;
         assertNull(LspServerConfig.forExtension(configs, ".java"));
         java.enabled = true; // restore
      }
   }

   @Nested
   @DisplayName("config persistence")
   class ConfigPersistenceTests {

      @TempDir
      File tempDir;

      /**
       * Writes content to a file at the given path under tempDir.
       */
      private File writeFile(String subpath, String content)
            throws IOException {
         File f = new File(tempDir, subpath);
         f.getParentFile().mkdirs();
         try (FileWriter w = new FileWriter(f)) {
            w.write(content);
         }
         return f;
      }

      @Test
      @DisplayName("loadUserConfigs overrides command for known language")
      void loadOverridesKnownLanguage() throws IOException {
         File configFile = writeFile(".javi/lsp.conf",
            "java = /custom/path/jdtls --data /tmp\n");

         // Temporarily override user.home
         String origHome = System.getProperty("user.home");
         try {
            System.setProperty("user.home", tempDir.getAbsolutePath());
            Map<String, LspServerConfig> configs =
               LspServerConfig.getDefaults();
            LspServerConfig.loadUserConfigs(configs);

            LspServerConfig java = configs.get("java");
            assertNotNull(java);
            assertEquals("/custom/path/jdtls", java.command[0]);
            assertEquals("--data", java.command[1]);
            assertEquals("/tmp", java.command[2]);
            // Extensions preserved from defaults
            assertEquals(".java", java.fileExtensions[0]);
            assertTrue(java.enabled);
         } finally {
            System.setProperty("user.home", origHome);
         }
      }

      @Test
      @DisplayName("loadUserConfigs handles disabled server")
      void loadDisabledServer() throws IOException {
         writeFile(".javi/lsp.conf", "!rust = rust-analyzer\n");

         String origHome = System.getProperty("user.home");
         try {
            System.setProperty("user.home", tempDir.getAbsolutePath());
            Map<String, LspServerConfig> configs =
               LspServerConfig.getDefaults();
            LspServerConfig.loadUserConfigs(configs);

            LspServerConfig rust = configs.get("rust");
            assertNotNull(rust);
            assertFalse(rust.enabled);
            assertEquals("rust-analyzer", rust.command[0]);
         } finally {
            System.setProperty("user.home", origHome);
         }
      }

      @Test
      @DisplayName("loadUserConfigs adds new language")
      void loadNewLanguage() throws IOException {
         writeFile(".javi/lsp.conf", "go = gopls\n");

         String origHome = System.getProperty("user.home");
         try {
            System.setProperty("user.home", tempDir.getAbsolutePath());
            Map<String, LspServerConfig> configs =
               LspServerConfig.getDefaults();
            LspServerConfig.loadUserConfigs(configs);

            LspServerConfig go = configs.get("go");
            assertNotNull(go);
            assertEquals("gopls", go.command[0]);
            assertEquals(".go", go.fileExtensions[0]);
            assertTrue(go.enabled);
         } finally {
            System.setProperty("user.home", origHome);
         }
      }

      @Test
      @DisplayName("loadUserConfigs skips comments and blank lines")
      void loadSkipsComments() throws IOException {
         writeFile(".javi/lsp.conf",
            "# This is a comment\n\n   \njava = custom-jdtls\n");

         String origHome = System.getProperty("user.home");
         try {
            System.setProperty("user.home", tempDir.getAbsolutePath());
            Map<String, LspServerConfig> configs =
               LspServerConfig.getDefaults();
            LspServerConfig.loadUserConfigs(configs);

            LspServerConfig java = configs.get("java");
            assertNotNull(java);
            assertEquals("custom-jdtls", java.command[0]);
         } finally {
            System.setProperty("user.home", origHome);
         }
      }

      @Test
      @DisplayName("loadUserConfigs tolerates missing file")
      void loadMissingFile() {
         String origHome = System.getProperty("user.home");
         try {
            System.setProperty("user.home", tempDir.getAbsolutePath());
            Map<String, LspServerConfig> configs =
               LspServerConfig.getDefaults();
            int sizeBefore = configs.size();
            LspServerConfig.loadUserConfigs(configs);
            assertEquals(sizeBefore, configs.size());
         } finally {
            System.setProperty("user.home", origHome);
         }
      }

      @Test
      @DisplayName("saveUserConfigs writes only overrides")
      void saveWritesOverrides() throws IOException {
         String origHome = System.getProperty("user.home");
         try {
            System.setProperty("user.home", tempDir.getAbsolutePath());

            Map<String, LspServerConfig> configs =
               LspServerConfig.getDefaults();
            // Override java command
            LspServerConfig custom = new LspServerConfig("java",
               new String[]{"/my/jdtls"},
               new String[]{".java"}, "build.gradle");
            configs.put("java", custom);

            LspServerConfig.saveUserConfigs(configs);

            File saved = LspServerConfig.getConfigFile();
            assertTrue(saved.isFile(), "config file should exist");

            // Reload and verify
            Map<String, LspServerConfig> reloaded =
               LspServerConfig.getDefaults();
            LspServerConfig.loadUserConfigs(reloaded);
            assertEquals("/my/jdtls", reloaded.get("java").command[0]);
         } finally {
            System.setProperty("user.home", origHome);
         }
      }

      @Test
      @DisplayName("save/load roundtrip preserves disabled flag")
      void roundtripDisabled() throws IOException {
         String origHome = System.getProperty("user.home");
         try {
            System.setProperty("user.home", tempDir.getAbsolutePath());

            Map<String, LspServerConfig> configs =
               LspServerConfig.getDefaults();
            LspServerConfig rust = configs.get("rust");
            rust.enabled = false;

            LspServerConfig.saveUserConfigs(configs);

            Map<String, LspServerConfig> reloaded =
               LspServerConfig.getDefaults();
            LspServerConfig.loadUserConfigs(reloaded);
            assertFalse(reloaded.get("rust").enabled);
         } finally {
            System.setProperty("user.home", origHome);
         }
      }

      @Test
      @DisplayName("save does not write file when no overrides")
      void noOverridesNoFile() {
         String origHome = System.getProperty("user.home");
         try {
            System.setProperty("user.home", tempDir.getAbsolutePath());

            Map<String, LspServerConfig> configs =
               LspServerConfig.getDefaults();
            LspServerConfig.saveUserConfigs(configs);

            File saved = LspServerConfig.getConfigFile();
            assertFalse(saved.exists(),
               "should not create file when no overrides");
         } finally {
            System.setProperty("user.home", origHome);
         }
      }
   }

   @Nested
   @DisplayName("overlay/spell checker")
   class OverlayTests {

      @Test
      @DisplayName("harper config is present in defaults")
      void harperInDefaults() {
         Map<String, LspServerConfig> defaults =
            LspServerConfig.getDefaults();
         LspServerConfig harper = defaults.get("harper");
         assertNotNull(harper, "should have harper config");
         assertEquals("harper", harper.languageId);
         assertTrue(harper.overlay, "harper should be an overlay");
      }

      @Test
      @DisplayName("harper handles markdown extension")
      void harperHandlesMarkdown() {
         Map<String, LspServerConfig> defaults =
            LspServerConfig.getDefaults();
         LspServerConfig harper = defaults.get("harper");
         assertNotNull(harper);
         boolean hasMd = false;
         for (String ext : harper.fileExtensions) {
            if (".md".equals(ext))
               hasMd = true;
         }
         assertTrue(hasMd, "harper should handle .md files");
      }

      @Test
      @DisplayName("harper handles typst extension")
      void harperHandlesTypst() {
         Map<String, LspServerConfig> defaults =
            LspServerConfig.getDefaults();
         LspServerConfig harper = defaults.get("harper");
         assertNotNull(harper);
         boolean hasTyp = false;
         for (String ext : harper.fileExtensions) {
            if (".typ".equals(ext))
               hasTyp = true;
         }
         assertTrue(hasTyp, "harper should handle .typ files");
      }

      @Test
      @DisplayName("non-overlay configs have overlay=false by default")
      void nonOverlayDefault() {
         LspServerConfig config = new LspServerConfig(
            "java", new String[]{"jdtls"}, new String[]{".java"},
            "build.gradle");
         assertFalse(config.overlay);
      }

      @Test
      @DisplayName("overlay constructor sets flag correctly")
      void overlayConstructor() {
         LspServerConfig config = new LspServerConfig(
            "spell", new String[]{"spell-ls"}, new String[]{".md"},
            null, true);
         assertTrue(config.overlay);
         assertTrue(config.enabled);
      }

      @Test
      @DisplayName("harper command uses --stdio flag")
      void harperUsesStdio() {
         Map<String, LspServerConfig> defaults =
            LspServerConfig.getDefaults();
         LspServerConfig harper = defaults.get("harper");
         assertNotNull(harper);
         assertTrue(harper.command.length >= 2);
         assertEquals("--stdio", harper.command[1]);
      }
   }
}
