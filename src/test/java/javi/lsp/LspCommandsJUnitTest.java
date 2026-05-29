package javi.lsp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javi.InputException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * JUnit 5 tests for {@link LspCommands} — the command-layer integration
 * for `:lsp.status`, `:lsp.diag`, `:lsp.enable`, `:lsp.disable`.
 */
class LspCommandsJUnitTest {

   private static final LspSession.NotificationSink NOOP_SINK =
      new LspSession.NotificationSink() {
         public void onDiagnostics(LspSession session, String uri,
               List<Map<String, Object>> diagnostics) {
         }

         public void onStateChanged(LspSession session,
               LspSession.State newState) {
         }
      };

   private static LspRegistry makeRegistry(LspServerConfig... cfgs) {
      Map<String, LspServerConfig> configs = new HashMap<>();
      for (LspServerConfig cfg : cfgs) {
         configs.put(cfg.languageId, cfg);
      }
      return new LspRegistry(configs, NOOP_SINK);
   }

   private static Map<String, Object> diagnostic(int startLine,
         int startChar, int endLine, int endChar,
         int severity, String message) {
      Map<String, Object> start = new HashMap<>();
      start.put("line", startLine);
      start.put("character", startChar);

      Map<String, Object> end = new HashMap<>();
      end.put("line", endLine);
      end.put("character", endChar);

      Map<String, Object> range = new HashMap<>();
      range.put("start", start);
      range.put("end", end);

      Map<String, Object> diag = new HashMap<>();
      diag.put("range", range);
      diag.put("severity", severity);
      diag.put("message", message);
      return diag;
   }

   // ---------------------------------------------------------------
   // :lsp.status
   // ---------------------------------------------------------------

   @Test
   @DisplayName("lsp.status shows not installed for missing server")
   void statusShowsNotInstalled() {
      LspServerConfig cfg = new LspServerConfig(
         "java", new String[]{"/no/such/binary"},
         new String[]{".java"}, null);
      LspRegistry registry = makeRegistry(cfg);
      LspDiagnostics diag = new LspDiagnostics();
      LspCommands commands = new LspCommands(registry, diag);

      try {
         commands.doStatus(null);
         fail("doStatus should throw InputException");
      } catch (InputException e) {
         assertTrue(e.getMessage().contains("java:"),
            "Should contain server name: " + e.getMessage());
         assertTrue(e.getMessage().contains("not installed"),
            "Should report not installed: " + e.getMessage());
      }
   }

   @Test
   @DisplayName("lsp.status shows disabled for disabled server")
   void statusShowsDisabled() {
      LspServerConfig cfg = new LspServerConfig(
         "harper", new String[]{"/no/such/binary"},
         new String[]{".md"}, null);
      LspRegistry registry = makeRegistry(cfg);
      registry.setEnabled("harper", false);
      LspDiagnostics diag = new LspDiagnostics();
      LspCommands commands = new LspCommands(registry, diag);

      try {
         commands.doStatus(null);
         fail("doStatus should throw InputException");
      } catch (InputException e) {
         assertTrue(e.getMessage().contains("harper: disabled"),
            "Should show disabled: " + e.getMessage());
      }
   }

   @Test
   @DisplayName("lsp.status shows multiple servers")
   void statusShowsMultipleServers() {
      LspServerConfig java = new LspServerConfig(
         "java", new String[]{"/no/such/binary"},
         new String[]{".java"}, null);
      LspServerConfig harper = new LspServerConfig(
         "harper", new String[]{"/no/such/binary2"},
         new String[]{".md"}, null);
      LspRegistry registry = makeRegistry(java, harper);
      LspDiagnostics diag = new LspDiagnostics();
      LspCommands commands = new LspCommands(registry, diag);

      try {
         commands.doStatus(null);
         fail("doStatus should throw InputException");
      } catch (InputException e) {
         assertTrue(e.getMessage().contains("java:"),
            "Should contain java: " + e.getMessage());
         assertTrue(e.getMessage().contains("harper:"),
            "Should contain harper: " + e.getMessage());
      }
   }

   // ---------------------------------------------------------------
   // :lsp.diag
   // ---------------------------------------------------------------

   @Test
   @DisplayName("lsp.diag shows OK when no diagnostics")
   void diagShowsOkWhenEmpty() {
      LspRegistry registry = makeRegistry();
      LspDiagnostics diag = new LspDiagnostics();
      LspCommands commands = new LspCommands(registry, diag);

      try {
         commands.doDiag(null);
         fail("doDiag should throw InputException");
      } catch (InputException e) {
         assertTrue(e.getMessage().contains("OK"),
            "Should show OK: " + e.getMessage());
         assertTrue(e.getMessage().contains("0 total"),
            "Should show 0 total: " + e.getMessage());
      }
   }

   @Test
   @DisplayName("lsp.diag shows error and warning counts")
   void diagShowsErrorAndWarningCounts() {
      LspRegistry registry = makeRegistry();
      LspDiagnostics diag = new LspDiagnostics();
      diag.update("jdtls", "file:///tmp/Foo.java", List.of(
         diagnostic(0, 0, 0, 5, 1, "err1"),
         diagnostic(1, 0, 1, 5, 1, "err2"),
         diagnostic(2, 0, 2, 5, 2, "warn1")));
      LspCommands commands = new LspCommands(registry, diag);

      try {
         commands.doDiag(null);
         fail("doDiag should throw InputException");
      } catch (InputException e) {
         assertTrue(e.getMessage().contains("E:2"),
            "Should show E:2: " + e.getMessage());
         assertTrue(e.getMessage().contains("W:1"),
            "Should show W:1: " + e.getMessage());
         assertTrue(e.getMessage().contains("3 total"),
            "Should show 3 total: " + e.getMessage());
      }
   }

   // ---------------------------------------------------------------
   // :lsp.enable / :lsp.disable
   // ---------------------------------------------------------------

   @Test
   @DisplayName("lsp.enable enables a specific language")
   void enableSpecificLanguage() {
      LspServerConfig cfg = new LspServerConfig(
         "harper", new String[]{"/no/such/binary"},
         new String[]{".md"}, null);
      LspRegistry registry = makeRegistry(cfg);
      registry.setEnabled("harper", false);

      LspDiagnostics diag = new LspDiagnostics();
      LspCommands commands = new LspCommands(registry, diag);

      try {
         commands.doEnable("harper");
         fail("doEnable should throw InputException");
      } catch (InputException e) {
         assertTrue(e.getMessage().contains("enabled harper"),
            "Should confirm: " + e.getMessage());
      }
      // Verify status now shows available (not disabled)
      List<String> status = registry.getStatus();
      assertTrue(status.stream().anyMatch(
         s -> s.contains("harper") && !s.contains("disabled")),
         "harper should not be disabled: " + status);
   }

   @Test
   @DisplayName("lsp.disable disables a specific language")
   void disableSpecificLanguage() {
      LspServerConfig cfg = new LspServerConfig(
         "java", new String[]{"/no/such/binary"},
         new String[]{".java"}, null);
      LspRegistry registry = makeRegistry(cfg);

      LspDiagnostics diag = new LspDiagnostics();
      LspCommands commands = new LspCommands(registry, diag);

      try {
         commands.doDisable("java");
         fail("doDisable should throw InputException");
      } catch (InputException e) {
         assertTrue(e.getMessage().contains("disabled java"),
            "Should confirm: " + e.getMessage());
      }
      assertTrue(registry.getStatus().stream().anyMatch(
         s -> s.contains("java: disabled")),
         "java should be disabled");
   }

   @Test
   @DisplayName("lsp.enable with no arg enables globally")
   void enableGlobally() {
      LspServerConfig cfg = new LspServerConfig(
         "java", new String[]{"/no/such/binary"},
         new String[]{".java"}, null);
      LspRegistry registry = makeRegistry(cfg);
      registry.setGlobalEnabled(false);

      LspDiagnostics diag = new LspDiagnostics();
      LspCommands commands = new LspCommands(registry, diag);

      try {
         commands.doEnable(null);
         fail("doEnable should throw InputException");
      } catch (InputException e) {
         assertTrue(e.getMessage().contains("globally enabled"),
            "Should confirm global: " + e.getMessage());
      }
      assertTrue(registry.isEnabled());
   }

   @Test
   @DisplayName("lsp.disable with no arg disables globally")
   void disableGlobally() {
      LspServerConfig cfg = new LspServerConfig(
         "java", new String[]{"/no/such/binary"},
         new String[]{".java"}, null);
      LspRegistry registry = makeRegistry(cfg);

      LspDiagnostics diag = new LspDiagnostics();
      LspCommands commands = new LspCommands(registry, diag);

      try {
         commands.doDisable(null);
         fail("doDisable should throw InputException");
      } catch (InputException e) {
         assertTrue(e.getMessage().contains("globally disabled"),
            "Should confirm global: " + e.getMessage());
      }
      assertFalse(registry.isEnabled());
   }

   // ---------------------------------------------------------------
   // Multi-server diagnostics isolation
   // ---------------------------------------------------------------

   @Test
   @DisplayName("diagnostics from different servers are kept separate")
   void diagnosticsFromDifferentServersIsolated() {
      LspDiagnostics diag = new LspDiagnostics();
      String uri = "file:///tmp/Test.java";

      // Simulate jdtls sending errors
      diag.update("jdtls", uri, List.of(
         diagnostic(0, 0, 0, 5, 1, "type error"),
         diagnostic(5, 0, 5, 10, 1, "syntax error")));

      // Simulate harper sending spelling warnings
      diag.update("harper", uri, List.of(
         diagnostic(2, 3, 2, 8, 2, "misspelled: teh")));

      // Verify isolation
      assertEquals(2, diag.forSource("jdtls").size(),
         "jdtls should have 2 diagnostics");
      assertEquals(1, diag.forSource("harper").size(),
         "harper should have 1 diagnostic");
      assertEquals(3, diag.forUri(uri).size(),
         "URI should show all 3 diagnostics combined");
      assertEquals("E:2 W:1", diag.summary());

      // Clearing one source doesn't affect the other
      diag.clearSource("jdtls");
      assertEquals(0, diag.forSource("jdtls").size());
      assertEquals(1, diag.forSource("harper").size());
      assertEquals(1, diag.forUri(uri).size());
   }

   @Test
   @DisplayName("update replaces only diagnostics from same source")
   void updateReplacesOnlySameSource() {
      LspDiagnostics diag = new LspDiagnostics();
      String uri = "file:///tmp/Test.java";

      diag.update("harper", uri, List.of(
         diagnostic(0, 0, 0, 5, 2, "old warning")));
      diag.update("jdtls", uri, List.of(
         diagnostic(1, 0, 1, 5, 1, "error stays")));

      // harper re-publishes (replace)
      diag.update("harper", uri, List.of(
         diagnostic(3, 0, 3, 5, 2, "new warning")));

      assertEquals(1, diag.forSource("harper").size());
      assertEquals("new warning",
         diag.forSource("harper").get(0).message);
      assertEquals(1, diag.forSource("jdtls").size());
      assertEquals("error stays",
         diag.forSource("jdtls").get(0).message);
   }
}
