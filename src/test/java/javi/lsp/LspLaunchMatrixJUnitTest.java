package javi.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration-style matrix tests for supported language servers.
 *
 * <p>These tests intentionally exercise the same launch path used by javi:
 * ProcessBuilder -> LspSession -> JsonRpc initialize -> request dispatch.
 * They use a deterministic mock server script so the tests are stable in CI.
 */
@Timeout(45)
class LspLaunchMatrixJUnitTest {

   private static final class TestSink implements LspSession.NotificationSink {
      final CountDownLatch readyLatch = new CountDownLatch(1);
      final CopyOnWriteArrayList<LspSession.State> states =
         new CopyOnWriteArrayList<>();

      @Override
      public void onDiagnostics(LspSession session, String uri,
            List<Map<String, Object>> diagnostics) {
      }

      @Override
      public void onStateChanged(LspSession session,
            LspSession.State newState) {
         states.add(newState);
         if (LspSession.State.READY == newState)
            readyLatch.countDown();
      }
   }

   private static String createMatrixMockServerScript() throws IOException {
      Path dir = Files.createTempDirectory("lsp-matrix-");
      Path script = dir.resolve("mock-lsp.sh");
      String body = "#!/bin/bash\n"
         + "while IFS= read -r line; do\n"
         + "  if [[ \"$line\" == Content-Length:* ]]; then\n"
         + "    len=${line#Content-Length: }\n"
         + "    len=${len%%$'\\r'}\n"
         + "    read -r blank\n"
         + "    payload=$(dd bs=1 count=$len 2>/dev/null)\n"
         + "    if echo \"$payload\" | grep -q '\"method\":\"initialize\"'; then\n"
         + "      id=$(echo \"$payload\" | sed -n 's/.*\"id\":\\([0-9]*\\).*/\\1/p')\n"
         + "      resp='{\"jsonrpc\":\"2.0\",\"id\":'\"$id\"',\"result\":{\"capabilities\":{\"definitionProvider\":true,\"referencesProvider\":true,\"hoverProvider\":true,\"completionProvider\":{},\"codeActionProvider\":true}}}'\n"
         + "      printf 'Content-Length: %d\\r\\n\\r\\n%s' ${#resp} \"$resp\"\n"
         + "    elif echo \"$payload\" | grep -q '\"method\":\"shutdown\"'; then\n"
         + "      id=$(echo \"$payload\" | sed -n 's/.*\"id\":\\([0-9]*\\).*/\\1/p')\n"
         + "      resp='{\"jsonrpc\":\"2.0\",\"id\":'\"$id\"',\"result\":null}'\n"
         + "      printf 'Content-Length: %d\\r\\n\\r\\n%s' ${#resp} \"$resp\"\n"
         + "      exit 0\n"
         + "    elif echo \"$payload\" | grep -q '\"id\":'; then\n"
         + "      id=$(echo \"$payload\" | sed -n 's/.*\"id\":\\([0-9]*\\).*/\\1/p')\n"
         + "      resp='{\"jsonrpc\":\"2.0\",\"id\":'\"$id\"',\"result\":{}}'\n"
         + "      printf 'Content-Length: %d\\r\\n\\r\\n%s' ${#resp} \"$resp\"\n"
         + "    fi\n"
         + "  fi\n"
         + "done\n";
      Files.writeString(script, body);
      script.toFile().setExecutable(true);
      return script.toAbsolutePath().toString();
   }

   private static LspServerConfig cfg(String languageId, String script,
         String extension) {
      return new LspServerConfig(languageId,
         new String[]{"/bin/bash", script},
         new String[]{extension}, null);
   }

   @Test
   @DisplayName("launch matrix: java/c/python/rust/harper request path")
   void launchMatrixForSupportedLanguages() throws Exception {
      String script = createMatrixMockServerScript();

      Map<String, String> methods = new HashMap<>();
      methods.put("java", "textDocument/definition");
      methods.put("c", "textDocument/references");
      methods.put("python", "textDocument/completion");
      methods.put("rust", "textDocument/hover");
      methods.put("harper", "textDocument/codeAction");

      Map<String, String> ext = new HashMap<>();
      ext.put("java", ".java");
      ext.put("c", ".c");
      ext.put("python", ".py");
      ext.put("rust", ".rs");
      ext.put("harper", ".txt");

      for (Map.Entry<String, String> e : methods.entrySet()) {
         String lang = e.getKey();
         String method = e.getValue();
         TestSink sink = new TestSink();
         LspSession session = new LspSession(
            cfg(lang, script, ext.get(lang)),
            System.getProperty("user.dir"), sink);

         try {
            session.start();
            assertTrue(sink.readyLatch.await(10, TimeUnit.SECONDS),
               "Session should reach READY for " + lang);
            assertEquals(LspSession.State.READY, session.getState(),
               "Session should be READY for " + lang);

            CompletableFuture<Map<String, Object>> future =
               session.submit(method, Map.of(
                  "textDocument", Map.of("uri", "file:///matrix"),
                  "position", Map.of("line", Integer.valueOf(0),
                     "character", Integer.valueOf(0))));
            Map<String, Object> result = future.get(5, TimeUnit.SECONDS);
            assertNotNull(result,
               "Request should return a result map for " + lang);
         } finally {
            session.stop();
         }
      }
   }

   @Test
   @DisplayName("dead session is replaced on next sessionFor call")
   void deadSessionRecovery() throws Exception {
      String script = createMatrixMockServerScript();
      Map<String, LspServerConfig> configs = new HashMap<>();
      LspServerConfig cfg = new LspServerConfig("testlang",
         new String[]{"/bin/bash", script},
         new String[]{".test"}, null);
      configs.put("testlang", cfg);

      TestSink sink = new TestSink();
      LspRegistry registry = new LspRegistry(configs, sink);
      registry.setProjectRoot(System.getProperty("user.dir"));

      // Start session, verify READY
      LspSession s1 = registry.sessionFor(".test");
      assertNotNull(s1, "First session should be created");
      assertTrue(sink.readyLatch.await(10, TimeUnit.SECONDS),
         "First session should reach READY");
      assertEquals(LspSession.State.READY, s1.getState());

      // Stop the session — simulates it ending up STOPPED
      s1.stop();
      Thread.sleep(200);

      // sessionFor should now get a new session, not the dead one
      LspSession s2 = registry.sessionFor(".test");
      assertNotNull(s2, "Second session should be created after dead removal");
      // Wait for the new session to reach READY
      int waited = 0;
      while (LspSession.State.READY != s2.getState() && waited < 50) {
         Thread.sleep(200);
         waited++;
      }
      assertEquals(LspSession.State.READY, s2.getState(),
         "Replacement session should reach READY");

      s2.stop();
   }
}
