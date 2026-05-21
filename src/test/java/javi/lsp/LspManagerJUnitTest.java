package javi.lsp;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link LspManager} — root detection and status.
 */
class LspManagerJUnitTest {

   @TempDir
   File tempDir;

   @Test
   @DisplayName("detectRootPath finds marker file")
   void detectRootFindsMarker() throws IOException {
      // Create project structure: tempDir/build.gradle
      File marker = new File(tempDir, "build.gradle");
      assertTrue(marker.createNewFile());

      // Create subdirectory with a source file
      File subdir = new File(tempDir, "src/main/java");
      assertTrue(subdir.mkdirs());
      File srcFile = new File(subdir, "Foo.java");
      assertTrue(srcFile.createNewFile());

      String root = LspManager.detectRootPath(
         srcFile.getAbsolutePath(), "build.gradle");
      assertEquals(tempDir.getAbsolutePath(), root);
   }

   @Test
   @DisplayName("detectRootPath falls back to cwd when no marker")
   void detectRootFallsBackToCwd() throws IOException {
      File subdir = new File(tempDir, "src");
      assertTrue(subdir.mkdirs());
      File srcFile = new File(subdir, "Foo.java");
      assertTrue(srcFile.createNewFile());

      String root = LspManager.detectRootPath(
         srcFile.getAbsolutePath(), "nonexistent.marker");
      // Should fall back to user.dir
      assertNotNull(root);
   }

   @Test
   @DisplayName("detectRootPath handles null rootPattern")
   void detectRootNullPattern() {
      String root = LspManager.detectRootPath("/some/file.java", null);
      assertNotNull(root);
   }

   @Test
   @DisplayName("detectRootPath handles null filePath")
   void detectRootNullFile() {
      String root = LspManager.detectRootPath(null, "build.gradle");
      assertNotNull(root);
   }

   @Test
   @DisplayName("getStatus returns non-null string")
   void getStatusReturnsString() {
      LspManager mgr = LspManager.getInstance();
      String status = mgr.getStatus();
      assertNotNull(status);
      assertTrue(status.contains("LSP:"));
   }

   @Test
   @DisplayName("setEnabled toggles correctly")
   void setEnabledToggles() {
      LspManager mgr = LspManager.getInstance();
      boolean original = mgr.isEnabled();
      try {
         mgr.setEnabled(false);
         assertTrue(!mgr.isEnabled());
         mgr.setEnabled(true);
         assertTrue(mgr.isEnabled());
      } finally {
         mgr.setEnabled(original);
      }
   }

   @Test
   @DisplayName("getConfig returns config for known language")
   void getConfigKnownLanguage() {
      LspManager mgr = LspManager.getInstance();
      LspServerConfig config = mgr.getConfig("java");
      assertNotNull(config);
      assertEquals("java", config.languageId);
   }

   @Test
   @DisplayName("setConfig and getConfig roundtrip")
   void setConfigRoundtrip() {
      LspManager mgr = LspManager.getInstance();
      LspServerConfig config = new LspServerConfig(
         "test-lang",
         new String[]{"test-server"},
         new String[]{".test"},
         null);
      mgr.setConfig("test-lang", config);
      LspServerConfig retrieved = mgr.getConfig("test-lang");
      assertNotNull(retrieved);
      assertEquals("test-lang", retrieved.languageId);
   }

   @Test
   @DisplayName("getOverlayConfigs returns harper")
   void getOverlayConfigsIncludesHarper() {
      LspManager mgr = LspManager.getInstance();
      java.util.List<LspServerConfig> overlays =
         mgr.getOverlayConfigs();
      boolean foundHarper = false;
      for (LspServerConfig cfg : overlays) {
         if ("harper".equals(cfg.languageId)) {
            foundHarper = true;
            assertTrue(cfg.overlay);
         }
      }
      assertTrue(foundHarper, "harper should be in overlay configs");
   }

   @Test
   @DisplayName("isOverlayRunning returns false when not started")
   void isOverlayRunningFalseWhenNotStarted() {
      LspManager mgr = LspManager.getInstance();
      mgr.setEnabled(false);
      assertTrue(!mgr.isOverlayRunning("harper"),
         "harper should not be running initially");
      mgr.setEnabled(true);
   }

   @Test
   @DisplayName("getConfig returns harper configuration")
   void getConfigHarper() {
      LspManager mgr = LspManager.getInstance();
      LspServerConfig harper = mgr.getConfig("harper");
      assertNotNull(harper, "harper config should exist");
      assertTrue(harper.overlay, "harper should be an overlay");
      assertEquals("harper", harper.languageId);
   }
}
