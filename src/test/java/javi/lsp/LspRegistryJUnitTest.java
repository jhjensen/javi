package javi.lsp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link LspRegistry}.
 */
class LspRegistryJUnitTest {

   private static final LspSession.NotificationSink NOOP_SINK =
      new LspSession.NotificationSink() {
         public void onDiagnostics(LspSession session, String uri,
               List<Map<String, Object>> diagnostics) {
         }

         public void onStateChanged(LspSession session,
               LspSession.State newState) {
         }
      };

   private static LspRegistry makeRegistryWithConfig(
         LspServerConfig config) {
      Map<String, LspServerConfig> configs = new HashMap<>();
      configs.put(config.languageId, config);
      return new LspRegistry(configs, NOOP_SINK);
   }

   @Test
   @DisplayName("status shows not installed for missing binary")
   void statusForUnavailableServer() {
      LspServerConfig cfg = new LspServerConfig(
         "fake", new String[]{"/not/a/real/server"},
         new String[]{".fake"}, null);
      LspRegistry registry = makeRegistryWithConfig(cfg);

      List<String> status = registry.getStatus();
      assertTrue(status.stream().anyMatch(
         s -> s.equals("fake: not installed")));
   }

   @Test
   @DisplayName("setEnabled(false) disables language and status")
   void disableLanguage() {
      LspServerConfig cfg = new LspServerConfig(
         "fake", new String[]{"/not/a/real/server"},
         new String[]{".fake"}, null);
      LspRegistry registry = makeRegistryWithConfig(cfg);

      registry.setEnabled("fake", false);

      assertTrue(registry.getStatus().stream().anyMatch(
         s -> s.equals("fake: disabled")));
      assertNull(registry.sessionForLanguage("fake"));
   }

   @Test
   @DisplayName("global disable blocks session resolution")
   void globalDisableBlocksSessionLookup() {
      LspServerConfig cfg = new LspServerConfig(
         "fake", new String[]{"/not/a/real/server"},
         new String[]{".fake"}, null);
      LspRegistry registry = makeRegistryWithConfig(cfg);

      registry.setGlobalEnabled(false);

      assertFalse(registry.isEnabled());
      assertNull(registry.sessionFor(".fake"));
      assertNull(registry.sessionForLanguage("fake"));
   }

   @Test
   @DisplayName("sessionFor returns null when project root is unset")
   void projectRootRequired() {
      LspServerConfig cfg = new LspServerConfig(
         "shell", new String[]{"/bin/sh"},
         new String[]{".sh"}, null);
      LspRegistry registry = makeRegistryWithConfig(cfg);

      assertNull(registry.sessionFor(".sh"));
   }

   @Test
   @DisplayName("getActiveSessionStatus is empty when no sessions started")
   void activeSessionStatusEmptyInitially() {
      LspServerConfig cfg = new LspServerConfig(
         "fake", new String[]{"/not/a/real/server"},
         new String[]{".fake"}, null);
      LspRegistry registry = makeRegistryWithConfig(cfg);

      assertTrue(registry.getActiveSessionStatus().isEmpty());
      assertTrue(registry.activeSessions().isEmpty());

      // sessionFor on an unavailable server must not start a session
      registry.setProjectRoot("/tmp");
      assertNull(registry.sessionFor(".fake"));
      assertTrue(registry.getActiveSessionStatus().isEmpty());
   }
}
