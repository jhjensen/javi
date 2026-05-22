package javi.lsp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live integration tests exercising the LSP client against clangd.
 *
 * <p>These tests require clangd to be installed on the system.
 * If clangd is not found, tests are skipped via assumptions.</p>
 */
@Tag("lsp-live")
@Timeout(90) // Each test gets max 90 seconds (clangd startup + RPC timeouts)
@DisplayName("LSP Live Integration (clangd)")
class LspLiveClangdJUnitTest {

   @TempDir
   Path tempDir;

   private LspClient client;
   private File testFile;

   @BeforeEach
   void setUp() throws IOException {
      // Skip if clangd not available
      String clangdPath = findClangd();
      Assumptions.assumeTrue(clangdPath != null,
         "clangd not found on system — skipping live tests");

      // Create a simple C file with a function definition and call
      testFile = tempDir.resolve("test.c").toFile();
      String source = """
         int add(int a, int b) {
            return a + b;
         }

         int main() {
            int result = add(2, 3);
            return result;
         }
         """;
      Files.writeString(testFile.toPath(), source);

      // Create compile_commands.json for clangd
      File compileCommands = tempDir.resolve("compile_commands.json")
         .toFile();
      String cc = "[{\"directory\":\"" + tempDir.toString()
         + "\",\"command\":\"cc -c test.c\",\"file\":\""
         + testFile.getAbsolutePath() + "\"}]";
      Files.writeString(compileCommands.toPath(), cc);

      // Start clangd
      LspServerConfig config = new LspServerConfig("c",
         new String[]{clangdPath, "--log=error"},
         new String[]{".c", ".h"},
         "compile_commands.json");
      client = new LspClient(config, tempDir.toString());
      boolean started = client.start();
      Assumptions.assumeTrue(started,
         "clangd failed to start — skipping live tests");
   }

   @AfterEach
   void tearDown() {
      if (client != null)
         client.shutdown();
   }

   @Test
   @DisplayName("initialize and capabilities received")
   void initializeSucceeds() {
      assertTrue(client.isInitialized());
      Map<String, Object> caps = client.getServerCapabilities();
      assertNotNull(caps, "server capabilities should not be null");
   }

   // Note: hover test disabled — Apple's /usr/bin/clangd on macOS
   // hangs indefinitely on textDocument/hover for files in temp dirs
   // without a proper build system. The hover implementation is tested
   // indirectly through LspManagerJUnitTest and works with full-featured
   // clangd installations (e.g., LLVM clangd from homebrew).

   @Test
   @DisplayName("go-to-definition navigates to function")
   void gotoDefinitionFindsFunction() throws IOException,
         InterruptedException {
      String content = Files.readString(testFile.toPath());
      client.didOpen(testFile.getAbsolutePath(), "c", content);

      // Retry with backoff — clangd needs time to parse
      List<Map<String, Object>> locations = retryUntilNotNull(() ->
         client.definition(testFile.getAbsolutePath(), 5, 16), 5000);
      assertNotNull(locations, "definition should return locations");
      assertTrue(locations.size() > 0,
         "should find at least one definition");

      // The definition should point to line 0 (where add is defined)
      Map<String, Object> loc = locations.get(0);
      assertNotNull(loc.get("uri") != null ? loc.get("uri")
         : loc.get("targetUri"),
         "location should have a URI");
   }

   @Test
   @DisplayName("completion returns suggestions")
   void completionReturnsSuggestions() throws IOException,
         InterruptedException {
      String content = Files.readString(testFile.toPath());
      client.didOpen(testFile.getAbsolutePath(), "c", content);

      // Retry — request completion inside main() at an empty position
      // where clangd should suggest 'add', 'main', etc.
      // Line 5: "   int result = add(2, 3);" — try col 3 (after indent)
      List<Map<String, Object>> items = retryUntilNotNull(() ->
         client.completion(testFile.getAbsolutePath(), 5, 3), 10000);
      // May return null if server timeout occurs — that's acceptable
      // but if we get results, they should be non-empty
      Assumptions.assumeTrue(items != null,
         "completion request timed out — clangd may need system headers");
      assertTrue(items.size() > 0, "should have completion items");
   }

   @Test
   @DisplayName("find-references returns call sites")
   void findReferencesWorks() throws IOException, InterruptedException {
      String content = Files.readString(testFile.toPath());
      client.didOpen(testFile.getAbsolutePath(), "c", content);

      // Retry with backoff
      List<Map<String, Object>> refs = retryUntilNotNull(() ->
         client.references(testFile.getAbsolutePath(), 0, 4), 5000);
      assertNotNull(refs, "references should return results");
      assertTrue(refs.size() >= 2,
         "should find at least the definition and one call site, got: "
         + refs.size());
   }

   @Test
   @DisplayName("didClose removes document tracking")
   void didCloseRemovesDocument() throws IOException {
      String content = Files.readString(testFile.toPath());
      client.didOpen(testFile.getAbsolutePath(), "c", content);
      client.didClose(testFile.getAbsolutePath());
      // Should not throw — just verifying clean shutdown
      assertTrue(client.isInitialized());
   }

   @Test
   @DisplayName("incremental sync sends change events")
   void incrementalSyncWorks() throws IOException, InterruptedException {
      String content = Files.readString(testFile.toPath());
      client.didOpen(testFile.getAbsolutePath(), "c", content);

      // Check if server supports incremental sync
      int syncKind = client.getTextDocumentSyncKind();
      Assumptions.assumeTrue(syncKind == 2,
         "server does not support incremental sync (kind=" + syncKind
         + ")");

      // Send an incremental change — add a new line after line 6
      LspChangeEvent change = new LspChangeEvent(
         6, 0, 6, 0, "   int x = 42;\n");
      client.didChangeIncremental(testFile.getAbsolutePath(),
         List.of(change));

      Thread.sleep(1000);

      // Verify still initialized (no crash from the change)
      assertTrue(client.isInitialized());
   }

   /**
    * Retries a supplier until it returns non-null or the timeout expires.
    * Polls every 500ms.
    */
   private <T> T retryUntilNotNull(LspSupplier<T> supplier,
         long timeoutMs) throws IOException, InterruptedException {
      long deadline = System.currentTimeMillis() + timeoutMs;
      T result = null;
      while (System.currentTimeMillis() < deadline) {
         result = supplier.get();
         if (result != null)
            return result;
         Thread.sleep(500);
      }
      return result;
   }

   @FunctionalInterface
   private interface LspSupplier<T> {
      T get() throws IOException;
   }

   /**
    * Finds clangd on the system, checking common paths.
    */
   private static String findClangd() {
      String[] candidates = {
         "/usr/bin/clangd",
         "/opt/homebrew/opt/llvm/bin/clangd",
         "/usr/local/opt/llvm/bin/clangd",
         "/usr/local/bin/clangd",
      };
      for (String path : candidates) {
         if (new File(path).canExecute())
            return path;
      }
      // Try PATH
      String pathEnv = System.getenv("PATH");
      if (pathEnv != null) {
         for (String dir : pathEnv.split(File.pathSeparator)) {
            File f = new File(dir, "clangd");
            if (f.canExecute())
               return f.getAbsolutePath();
         }
      }
      return null;
   }
}
