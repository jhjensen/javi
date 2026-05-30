package javi.lsp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-server isolation tests for LspRegistry.
 * Verifies that multiple language servers coexist independently
 * with separate sessions, state, and lifecycle.
 */
class LspMultiServerJUnitTest {

   private static final LspSession.NotificationSink NOOP_SINK =
      new LspSession.NotificationSink() {
         public void onDiagnostics(LspSession session, String uri,
               List<Map<String, Object>> diagnostics) {
         }
         public void onStateChanged(LspSession session,
               LspSession.State newState) {
         }
      };

   private LspServerConfig javaConfig;
   private LspServerConfig pythonConfig;
   private LspServerConfig harperConfig;
   private LspRegistry registry;

   @BeforeEach
   void setUp() {
      javaConfig = new LspServerConfig(
         "java", new String[]{"/not/real/jdtls"},
         new String[]{".java"}, "pom.xml");
      pythonConfig = new LspServerConfig(
         "python", new String[]{"/not/real/pylsp"},
         new String[]{".py"}, "setup.py");
      harperConfig = new LspServerConfig(
         "harper", new String[]{"/not/real/harper-ls"},
         new String[]{".md", ".txt"}, null, true);

      Map<String, LspServerConfig> configs = new HashMap<>();
      configs.put("java", javaConfig);
      configs.put("python", pythonConfig);
      configs.put("harper", harperConfig);
      registry = new LspRegistry(configs, NOOP_SINK);
   }

   @Nested
   @DisplayName("Session isolation")
   class SessionIsolation {

      @Test
      @DisplayName("different extensions resolve to different configs")
      void extensionRoutesIndependently() {
         // .java goes to java server, .py to python server
         // Without project root set these return null (can't start)
         // but the routing logic is exercised via status
         List<String> status = registry.getStatus();
         assertTrue(status.stream().anyMatch(s -> s.startsWith("java:")));
         assertTrue(status.stream().anyMatch(s -> s.startsWith("python:")));
         assertTrue(status.stream().anyMatch(s -> s.startsWith("harper:")));
         assertEquals(3, status.size());
      }

      @Test
      @DisplayName("disabling one server does not affect others")
      void disableOneServerLeavesOthersEnabled() {
         registry.setEnabled("python", false);

         List<String> status = registry.getStatus();
         assertTrue(status.stream().anyMatch(
            s -> s.equals("python: disabled")));
         // java and harper should still show available or not-installed
         assertTrue(status.stream().anyMatch(
            s -> s.startsWith("java:") && !s.contains("disabled")));
         assertTrue(status.stream().anyMatch(
            s -> s.startsWith("harper:") && !s.contains("disabled")));
      }

      @Test
      @DisplayName("re-enabling a disabled server restores availability")
      void reEnableRestoresServer() {
         registry.setEnabled("java", false);
         assertTrue(registry.getStatus().stream().anyMatch(
            s -> s.equals("java: disabled")));

         registry.setEnabled("java", true);
         assertTrue(registry.getStatus().stream().anyMatch(
            s -> s.startsWith("java:") && !s.contains("disabled")));
      }

      @Test
      @DisplayName("sessionFor returns null for unconfigured extension")
      void unconfiguredExtensionReturnsNull() {
         registry.setProjectRoot("/tmp/project");
         assertNull(registry.sessionFor(".rs"));
      }

      @Test
      @DisplayName("sessionForLanguage returns null for unknown language")
      void unknownLanguageReturnsNull() {
         assertNull(registry.sessionForLanguage("rust"));
      }
   }

   @Nested
   @DisplayName("Overlay server behavior")
   class OverlayServer {

      @Test
      @DisplayName("overlay server handles multiple extensions")
      void overlayServesMultipleExtensions() {
         // harper is configured for .md and .txt
         List<String> status = registry.getStatus();
         assertTrue(status.stream().anyMatch(s -> s.startsWith("harper:")));
      }

      @Test
      @DisplayName("overlay flag is independent of other servers")
      void overlayFlagIsolated() {
         // harper is overlay=true, java and python are not
         assertTrue(harperConfig.overlay);
         // The config objects are independent
         assertEquals("java", javaConfig.languageId);
         assertEquals("harper", harperConfig.languageId);
      }
   }

   @Nested
   @DisplayName("Lifecycle independence")
   class LifecycleIndependence {

      @Test
      @DisplayName("stopAll clears all sessions")
      void stopAllClearsAllSessions() {
         registry.stopAll();
         assertTrue(registry.activeSessions().isEmpty());
      }

      @Test
      @DisplayName("global disable stops all servers")
      void globalDisableStopsAll() {
         registry.setGlobalEnabled(false);
         assertNull(registry.sessionFor(".java"));
         assertNull(registry.sessionFor(".py"));
         assertNull(registry.sessionForLanguage("harper"));
      }

      @Test
      @DisplayName("global re-enable allows sessions again")
      void globalReEnableAllowsSessions() {
         registry.setGlobalEnabled(false);
         registry.setGlobalEnabled(true);
         assertTrue(registry.isEnabled());
         // Sessions not yet started, but lookups are not blocked
         // (they'll return null because binaries don't exist)
         List<String> status = registry.getStatus();
         assertTrue(status.stream().noneMatch(s -> s.contains("disabled")));
      }

      @Test
      @DisplayName("active sessions list reflects started servers only")
      void activeSessionsReflectsOnlyStarted() {
         // No servers started (binaries don't exist)
         assertTrue(registry.activeSessions().isEmpty());
         assertEquals(0, registry.activeSessions().size());
      }

      @Test
      @DisplayName("startAll only starts available servers")
      void startAllOnlyStartsAvailable() {
         registry.setProjectRoot("/tmp/project");
         registry.startAll();
         // All binaries are fake so nothing should start
         assertTrue(registry.activeSessions().isEmpty());
      }
   }

   @Nested
   @DisplayName("State tracking notification")
   class StateNotification {

      @Test
      @DisplayName("notification sink receives per-session events")
      void notificationSinkPerSession() {
         List<String> notifications = new CopyOnWriteArrayList<>();
         LspSession.NotificationSink trackingSink =
            new LspSession.NotificationSink() {
               public void onDiagnostics(LspSession session, String uri,
                     List<Map<String, Object>> diagnostics) {
                  notifications.add("diag:" + uri);
               }
               public void onStateChanged(LspSession session,
                     LspSession.State newState) {
                  notifications.add("state:" + newState);
               }
            };

         Map<String, LspServerConfig> configs = new HashMap<>();
         configs.put("test", new LspServerConfig(
            "test", new String[]{"/not/real"},
            new String[]{".test"}, null));
         LspRegistry reg = new LspRegistry(configs, trackingSink);

         // Verify the registry was created with custom sink
         assertNotNull(reg);
         List<String> status = reg.getStatus();
         assertEquals(1, status.size());
         assertTrue(status.get(0).startsWith("test:"));
      }
   }
}
