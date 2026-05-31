package javi.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature-level integration tests for each supported LSP feature.
 *
 * <p>Exercises each LSP feature (definition, references, hover,
 * completion, codeAction, diagnostics) using the same code path
 * javi uses: LspSession.start() -> submit() -> parse response.
 * A feature-aware mock server returns realistic LSP responses.</p>
 */
@Timeout(30)
class LspFeatureMatrixJUnitTest {

   private static final String[] LANGUAGES =
      {"java", "c", "python", "rust", "harper"};
   private static final String[] EXTENSIONS =
      {".java", ".c", ".py", ".rs", ".txt"};
   private static final String[] FEATURES = {
      "textDocument/definition",
      "textDocument/references",
      "textDocument/hover",
      "textDocument/completion",
      "textDocument/codeAction",
   };

   private String scriptPath;
   private LspSession session;
   private TestSink sink;

   private static final class TestSink implements LspSession.NotificationSink {
      final CountDownLatch readyLatch = new CountDownLatch(1);
      final CopyOnWriteArrayList<List<Map<String, Object>>> diagnostics =
         new CopyOnWriteArrayList<>();

      @Override
      public void onDiagnostics(LspSession session, String uri,
            List<Map<String, Object>> diags) {
         diagnostics.add(diags);
      }

      @Override
      public void onStateChanged(LspSession session,
            LspSession.State newState) {
         if (LspSession.State.READY == newState)
            readyLatch.countDown();
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      scriptPath = createFeatureMockScript();
      sink = new TestSink();
   }

   @AfterEach
   void tearDown() {
      if (null != session) {
         try {
            session.stop();
         } catch (Exception ignored) {
         }
      }
   }

   private LspSession startSession(String lang, String ext) throws Exception {
      LspServerConfig cfg = new LspServerConfig(lang,
         new String[]{"/bin/bash", scriptPath},
         new String[]{ext}, null);
      session = new LspSession(cfg,
         System.getProperty("user.dir"), sink);
      session.start();
      assertTrue(sink.readyLatch.await(10, TimeUnit.SECONDS),
         "Session should reach READY for " + lang);
      return session;
   }

   @Test
   @DisplayName("definition: returns location with uri and range")
   void definitionReturnsLocation() throws Exception {
      for (int i = 0; i < LANGUAGES.length; i++) {
         LspSession s = startSession(LANGUAGES[i], EXTENSIONS[i]);
         Map<String, Object> params = makeTextDocPosition(
            "file:///test." + EXTENSIONS[i].substring(1), 10, 5);
         CompletableFuture<Map<String, Object>> future =
            s.submit("textDocument/definition", params);
         Map<String, Object> result = future.get(5, TimeUnit.SECONDS);
         assertNotNull(result, "definition result for " + LANGUAGES[i]);
         assertTrue(result.containsKey("uri") || result.containsKey("result"),
            "definition should have uri or result for " + LANGUAGES[i]);
         s.stop();
         session = null;
         sink = new TestSink();
      }
   }

   @Test
   @DisplayName("references: returns array of locations")
   void referencesReturnsLocations() throws Exception {
      for (int i = 0; i < LANGUAGES.length; i++) {
         LspSession s = startSession(LANGUAGES[i], EXTENSIONS[i]);
         Map<String, Object> params = makeTextDocPosition(
            "file:///test." + EXTENSIONS[i].substring(1), 5, 3);
         params.put("context", Map.of("includeDeclaration", Boolean.TRUE));
         CompletableFuture<Map<String, Object>> future =
            s.submit("textDocument/references", params);
         Map<String, Object> result = future.get(5, TimeUnit.SECONDS);
         assertNotNull(result, "references result for " + LANGUAGES[i]);
         s.stop();
         session = null;
         sink = new TestSink();
      }
   }

   @Test
   @DisplayName("hover: returns contents with value field")
   void hoverReturnsContents() throws Exception {
      for (int i = 0; i < LANGUAGES.length; i++) {
         LspSession s = startSession(LANGUAGES[i], EXTENSIONS[i]);
         Map<String, Object> params = makeTextDocPosition(
            "file:///test." + EXTENSIONS[i].substring(1), 1, 0);
         CompletableFuture<Map<String, Object>> future =
            s.submit("textDocument/hover", params);
         Map<String, Object> result = future.get(5, TimeUnit.SECONDS);
         assertNotNull(result, "hover result for " + LANGUAGES[i]);
         assertTrue(result.containsKey("contents"),
            "hover should have contents for " + LANGUAGES[i]);
         s.stop();
         session = null;
         sink = new TestSink();
      }
   }

   @Test
   @DisplayName("completion: returns items array")
   void completionReturnsItems() throws Exception {
      for (int i = 0; i < LANGUAGES.length; i++) {
         LspSession s = startSession(LANGUAGES[i], EXTENSIONS[i]);
         Map<String, Object> params = makeTextDocPosition(
            "file:///test." + EXTENSIONS[i].substring(1), 2, 8);
         CompletableFuture<Map<String, Object>> future =
            s.submit("textDocument/completion", params);
         Map<String, Object> result = future.get(5, TimeUnit.SECONDS);
         assertNotNull(result, "completion result for " + LANGUAGES[i]);
         assertTrue(result.containsKey("items") || result.containsKey("result"),
            "completion should have items for " + LANGUAGES[i]);
         s.stop();
         session = null;
         sink = new TestSink();
      }
   }

   @Test
   @DisplayName("codeAction: returns actions array")
   void codeActionReturnsActions() throws Exception {
      for (int i = 0; i < LANGUAGES.length; i++) {
         LspSession s = startSession(LANGUAGES[i], EXTENSIONS[i]);
         Map<String, Object> params = Map.of(
            "textDocument", Map.of("uri", "file:///test." + EXTENSIONS[i].substring(1)),
            "range", Map.of(
               "start", Map.of("line", Integer.valueOf(0), "character", Integer.valueOf(0)),
               "end", Map.of("line", Integer.valueOf(0), "character", Integer.valueOf(5))),
            "context", Map.of("diagnostics", List.of()));
         CompletableFuture<Map<String, Object>> future =
            s.submit("textDocument/codeAction", params);
         Map<String, Object> result = future.get(5, TimeUnit.SECONDS);
         assertNotNull(result, "codeAction result for " + LANGUAGES[i]);
         s.stop();
         session = null;
         sink = new TestSink();
      }
   }

   @Test
   @DisplayName("diagnostics: server-pushed notification received by sink")
   void diagnosticsPushedFromServer() throws Exception {
      LspSession s = startSession("java", ".java");
      // The mock server sends a publishDiagnostics notification after
      // receiving any request with method containing "diagnostic"
      Map<String, Object> params = makeTextDocPosition(
         "file:///Diag.java", 0, 0);
      s.submit("textDocument/definition", params).get(5, TimeUnit.SECONDS);
      // The mock sends a diagnostic notification after every request
      Thread.sleep(500);
      assertTrue(sink.diagnostics.size() > 0,
         "Should receive at least one diagnostics notification");
      s.stop();
   }

   @Test
   @DisplayName("all features work per language: full matrix")
   void fullFeatureMatrix() throws Exception {
      for (int i = 0; i < LANGUAGES.length; i++) {
         LspSession s = startSession(LANGUAGES[i], EXTENSIONS[i]);
         for (String feature : FEATURES) {
            Map<String, Object> params;
            if ("textDocument/codeAction".equals(feature)) {
               params = Map.of(
                  "textDocument", Map.of("uri", "file:///t." + EXTENSIONS[i].substring(1)),
                  "range", Map.of(
                     "start", Map.of("line", Integer.valueOf(0), "character", Integer.valueOf(0)),
                     "end", Map.of("line", Integer.valueOf(0), "character", Integer.valueOf(1))),
                  "context", Map.of("diagnostics", List.of()));
            } else {
               params = makeTextDocPosition(
                  "file:///t." + EXTENSIONS[i].substring(1), 0, 0);
               if ("textDocument/references".equals(feature))
                  params.put("context",
                     Map.of("includeDeclaration", Boolean.TRUE));
            }
            CompletableFuture<Map<String, Object>> future =
               s.submit(feature, params);
            Map<String, Object> result = future.get(5, TimeUnit.SECONDS);
            assertNotNull(result, feature + " for " + LANGUAGES[i]
               + " should return non-null");
         }
         s.stop();
         session = null;
         sink = new TestSink();
      }
   }

   private static Map<String, Object> makeTextDocPosition(
         String uri, int line, int character) {
      return new java.util.HashMap<>(Map.of(
         "textDocument", Map.of("uri", uri),
         "position", Map.of(
            "line", Integer.valueOf(line),
            "character", Integer.valueOf(character))));
   }

   /**
    * Creates a mock server that returns realistic per-feature responses
    * and also pushes a publishDiagnostics notification after each request.
    */
   private static String createFeatureMockScript() throws IOException {
      Path dir = Files.createTempDirectory("lsp-feature-matrix-");
      Path script = dir.resolve("feature-mock.sh");
      String body = "#!/bin/bash\n"
         + "while IFS= read -r line; do\n"
         + "  if [[ \"$line\" == Content-Length:* ]]; then\n"
         + "    len=${line#Content-Length: }\n"
         + "    len=${len%%$'\\r'}\n"
         + "    read -r blank\n"
         + "    payload=$(dd bs=1 count=$len 2>/dev/null)\n"
         + "    id=$(echo \"$payload\" | sed -n 's/.*\"id\":\\([0-9]*\\).*/\\1/p')\n"
         + "    method=$(echo \"$payload\" | sed -n 's/.*\"method\":\"\\([^\"]*\\)\".*/\\1/p')\n"
         + "    if [[ \"$method\" == \"initialize\" ]]; then\n"
         + "      resp='{\"jsonrpc\":\"2.0\",\"id\":'\"$id\"',\"result\":{\"capabilities\":{\"definitionProvider\":true,\"referencesProvider\":true,\"hoverProvider\":true,\"completionProvider\":{\"triggerCharacters\":[\".\"]},\"codeActionProvider\":true,\"textDocumentSync\":1}}}'\n"
         + "      printf 'Content-Length: %d\\r\\n\\r\\n%s' ${#resp} \"$resp\"\n"
         + "    elif [[ \"$method\" == \"shutdown\" ]]; then\n"
         + "      resp='{\"jsonrpc\":\"2.0\",\"id\":'\"$id\"',\"result\":null}'\n"
         + "      printf 'Content-Length: %d\\r\\n\\r\\n%s' ${#resp} \"$resp\"\n"
         + "      exit 0\n"
         + "    elif [[ \"$method\" == \"textDocument/definition\" ]]; then\n"
         + "      resp='{\"jsonrpc\":\"2.0\",\"id\":'\"$id\"',\"result\":{\"uri\":\"file:///found.java\",\"range\":{\"start\":{\"line\":10,\"character\":4},\"end\":{\"line\":10,\"character\":14}}}}'\n"
         + "      printf 'Content-Length: %d\\r\\n\\r\\n%s' ${#resp} \"$resp\"\n"
         + "    elif [[ \"$method\" == \"textDocument/references\" ]]; then\n"
         + "      resp='{\"jsonrpc\":\"2.0\",\"id\":'\"$id\"',\"result\":[{\"uri\":\"file:///ref1.java\",\"range\":{\"start\":{\"line\":1,\"character\":0},\"end\":{\"line\":1,\"character\":5}}},{\"uri\":\"file:///ref2.java\",\"range\":{\"start\":{\"line\":3,\"character\":2},\"end\":{\"line\":3,\"character\":7}}}]}'\n"
         + "      printf 'Content-Length: %d\\r\\n\\r\\n%s' ${#resp} \"$resp\"\n"
         + "    elif [[ \"$method\" == \"textDocument/hover\" ]]; then\n"
         + "      resp='{\"jsonrpc\":\"2.0\",\"id\":'\"$id\"',\"result\":{\"contents\":{\"kind\":\"markdown\",\"value\":\"```java\\npublic void foo()\\n```\"}}}'\n"
         + "      printf 'Content-Length: %d\\r\\n\\r\\n%s' ${#resp} \"$resp\"\n"
         + "    elif [[ \"$method\" == \"textDocument/completion\" ]]; then\n"
         + "      resp='{\"jsonrpc\":\"2.0\",\"id\":'\"$id\"',\"result\":{\"items\":[{\"label\":\"toString\",\"kind\":2},{\"label\":\"hashCode\",\"kind\":2}]}}'\n"
         + "      printf 'Content-Length: %d\\r\\n\\r\\n%s' ${#resp} \"$resp\"\n"
         + "    elif [[ \"$method\" == \"textDocument/codeAction\" ]]; then\n"
         + "      resp='{\"jsonrpc\":\"2.0\",\"id\":'\"$id\"',\"result\":[{\"title\":\"Extract method\",\"kind\":\"refactor.extract\"}]}'\n"
         + "      printf 'Content-Length: %d\\r\\n\\r\\n%s' ${#resp} \"$resp\"\n"
         + "    elif [[ -n \"$id\" ]]; then\n"
         + "      resp='{\"jsonrpc\":\"2.0\",\"id\":'\"$id\"',\"result\":{}}'\n"
         + "      printf 'Content-Length: %d\\r\\n\\r\\n%s' ${#resp} \"$resp\"\n"
         + "    fi\n"
         + "    # Push a diagnostics notification after every request\n"
         + "    if [[ \"$method\" != \"initialize\" && \"$method\" != \"shutdown\" && \"$method\" != \"initialized\" ]]; then\n"
         + "      diag='{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/publishDiagnostics\",\"params\":{\"uri\":\"file:///test.java\",\"diagnostics\":[{\"range\":{\"start\":{\"line\":0,\"character\":0},\"end\":{\"line\":0,\"character\":5}},\"severity\":1,\"message\":\"test error\"}]}}'\n"
         + "      printf 'Content-Length: %d\\r\\n\\r\\n%s' ${#diag} \"$diag\"\n"
         + "    fi\n"
         + "  fi\n"
         + "done\n";
      Files.writeString(script, body);
      script.toFile().setExecutable(true);
      return script.toAbsolutePath().toString();
   }
}
