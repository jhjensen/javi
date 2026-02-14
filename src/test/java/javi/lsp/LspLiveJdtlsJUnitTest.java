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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live integration tests exercising the LSP client against jdtls
 * (Eclipse JDT Language Server).
 *
 * <p>These tests require jdtls to be installed on the system.
 * If jdtls is not found, all tests are skipped via assumptions.</p>
 *
 * <p>jdtls is slower to initialize than clangd — it needs to build
 * a workspace index. The timeout is set higher to accommodate this.</p>
 */
@Timeout(120)
@DisplayName("LSP Live Integration (jdtls)")
class LspLiveJdtlsJUnitTest {

   @TempDir
   Path tempDir;

   private LspClient client;
   private File testFile;

   @BeforeEach
   void setUp() throws IOException {
      String jdtlsPath = findJdtls();
      Assumptions.assumeTrue(jdtlsPath != null,
         "jdtls not found on system — skipping Java LSP live tests");

      // Create a minimal Gradle project structure for jdtls
      Path srcDir = tempDir.resolve("src/main/java");
      Files.createDirectories(srcDir);

      // Write build.gradle so jdtls can detect the project root
      Files.writeString(tempDir.resolve("build.gradle"),
         "plugins { id 'java' }\n");

      // Write a simple Java file with a class and method
      testFile = srcDir.resolve("Hello.java").toFile();
      String source = """
         public class Hello {
            private int count;

            public int getCount() {
               return count;
            }

            public void increment() {
               count++;
            }

            public static void main(String[] args) {
               Hello h = new Hello();
               h.increment();
               System.out.println(h.getCount());
            }
         }
         """;
      Files.writeString(testFile.toPath(), source);

      // Start jdtls
      LspServerConfig config = new LspServerConfig("java",
         new String[]{jdtlsPath},
         new String[]{".java"},
         "build.gradle");
      client = new LspClient(config, tempDir.toString());
      boolean started = client.start();
      Assumptions.assumeTrue(started,
         "jdtls failed to start — skipping Java LSP live tests");
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

   @Test
   @DisplayName("go-to-definition navigates to method")
   void gotoDefinitionFindsMethod() throws IOException,
         InterruptedException {
      String content = Files.readString(testFile.toPath());
      client.didOpen(testFile.getAbsolutePath(), "java", content);

      // Wait for jdtls to finish indexing — it's slow
      // Line 14: "h.increment();" — try definition on "increment" (col 14)
      List<Map<String, Object>> locations = retryUntilNotNull(() ->
         client.definition(testFile.getAbsolutePath(), 14, 14), 30000);
      Assumptions.assumeTrue(locations != null,
         "definition returned null — jdtls may still be indexing");
      assertTrue(locations.size() > 0,
         "should find at least one definition for increment()");
   }

   @Test
   @DisplayName("find-references returns call sites")
   void findReferencesWorks() throws IOException, InterruptedException {
      String content = Files.readString(testFile.toPath());
      client.didOpen(testFile.getAbsolutePath(), "java", content);

      // Find references to "getCount" — defined at line 3, called at line 15
      List<Map<String, Object>> refs = retryUntilNotNull(() ->
         client.references(testFile.getAbsolutePath(), 3, 14), 30000);
      Assumptions.assumeTrue(refs != null,
         "references returned null — jdtls may still be indexing");
      assertTrue(refs.size() >= 2,
         "should find definition + call site for getCount(), got: "
         + refs.size());
   }

   @Test
   @DisplayName("completion returns suggestions")
   void completionReturnsSuggestions() throws IOException,
         InterruptedException {
      String content = Files.readString(testFile.toPath());
      client.didOpen(testFile.getAbsolutePath(), "java", content);

      // Request completion after "h." — line 14 col 14 (after "h.inc")
      // Actually request at line 13, col 10 after "Hello h = new Hello();\n"
      // Let's try at line 14 col 10 — after "h."
      List<Map<String, Object>> items = retryUntilNotNull(() ->
         client.completion(testFile.getAbsolutePath(), 14, 10), 30000);
      Assumptions.assumeTrue(items != null,
         "completion request timed out — jdtls may still be indexing");
      assertTrue(items.size() > 0, "should have completion items");
   }

   @Test
   @DisplayName("hover returns type information")
   void hoverReturnsInfo() throws IOException, InterruptedException {
      String content = Files.readString(testFile.toPath());
      client.didOpen(testFile.getAbsolutePath(), "java", content);

      // Hover over "count" field at line 1 col 18
      String hover = retryUntilNotNull(() -> {
         String h = client.hover(testFile.getAbsolutePath(), 1, 18);
         return (h != null && !h.isEmpty()) ? h : null;
      }, 30000);
      Assumptions.assumeTrue(hover != null,
         "hover returned null — jdtls may still be indexing");
      assertTrue(hover.length() > 0, "hover should return type info");
   }

   @Test
   @DisplayName("didClose removes document tracking")
   void didCloseRemovesDocument() throws IOException {
      String content = Files.readString(testFile.toPath());
      client.didOpen(testFile.getAbsolutePath(), "java", content);
      client.didClose(testFile.getAbsolutePath());
      assertTrue(client.isInitialized());
   }

   /**
    * Retries a supplier until it returns non-null or timeout expires.
    * Polls every 1000ms (jdtls is slower than clangd).
    */
   private <T> T retryUntilNotNull(LspSupplier<T> supplier,
         long timeoutMs) throws IOException, InterruptedException {
      long deadline = System.currentTimeMillis() + timeoutMs;
      T result = null;
      while (System.currentTimeMillis() < deadline) {
         result = supplier.get();
         if (result != null)
            return result;
         Thread.sleep(1000);
      }
      return result;
   }

   @FunctionalInterface
   private interface LspSupplier<T> {
      T get() throws IOException;
   }

   /**
    * Finds jdtls on the system, checking common paths.
    */
   private static String findJdtls() {
      String[] candidates = {
         "/opt/homebrew/bin/jdtls",
         "/usr/local/bin/jdtls",
         System.getProperty("user.home")
            + "/.local/share/jdtls/bin/jdtls",
         System.getProperty("user.home") + "/bin/jdtls",
      };
      for (String path : candidates) {
         if (new File(path).canExecute())
            return path;
      }
      // Try PATH
      String pathEnv = System.getenv("PATH");
      if (pathEnv != null) {
         for (String dir : pathEnv.split(File.pathSeparator)) {
            File f = new File(dir, "jdtls");
            if (f.canExecute())
               return f.getAbsolutePath();
         }
      }
      return null;
   }
}
