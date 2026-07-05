package javi;

import javi.ai.RequestLog;
import javi.ai.tools.GrepTool;
import javi.ai.tools.PermissionLevel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for GrepTool and RequestLog.
 */
class GrepAndRequestLogJUnitTest {

   @Nested
   @DisplayName("GrepTool")
   class GrepToolTests {

      private GrepTool tool;

      @TempDir
      Path tempDir;

      @BeforeEach
      void setUp() {
         tool = new GrepTool();
      }

      @Test
      @DisplayName("tool metadata is correct")
      void toolMetadata() {
         assertEquals("grep", tool.name());
         assertNotNull(tool.description());
         assertNotNull(tool.inputSchema());
         assertEquals(PermissionLevel.AUTO,
            tool.permissionLevel());
      }

      @Test
      @DisplayName("finds matching lines in files")
      void findsMatchingLines() throws Exception {
         Path file = tempDir.resolve("test.java");
         Files.writeString(file,
            "public class Foo {\n"
            + "   int bar = 42;\n"
            + "   String baz = \"hello\";\n"
            + "}\n");

         String result = tool.execute(Map.of(
            "pattern", "bar",
            "path", tempDir.toString()));

         assertTrue(result.contains("test.java:2:"),
            "should show file:line");
         assertTrue(result.contains("bar"),
            "should contain matching text");
         assertTrue(result.contains("1 match"),
            "should report match count");
      }

      @Test
      @DisplayName("respects case sensitivity flag")
      void caseSensitive() throws Exception {
         Path file = tempDir.resolve("test.txt");
         Files.writeString(file,
            "Hello World\nhello world\n");

         // Case sensitive (default) - only lowercase
         String result = tool.execute(Map.of(
            "pattern", "hello",
            "path", tempDir.toString()));
         assertTrue(result.contains("1 match"));

         // Case insensitive - both
         result = tool.execute(Map.of(
            "pattern", "hello",
            "path", tempDir.toString(),
            "case_sensitive", "false"));
         assertTrue(result.contains("2 match"));
      }

      @Test
      @DisplayName("respects file include pattern")
      void includePattern() throws Exception {
         Files.writeString(tempDir.resolve("code.java"),
            "public class Foo {}");
         Files.writeString(tempDir.resolve("notes.txt"),
            "public notes");

         String result = tool.execute(Map.of(
            "pattern", "public",
            "path", tempDir.toString(),
            "include", "*.java"));

         assertTrue(result.contains("code.java"));
         assertFalse(result.contains("notes.txt"));
         assertTrue(result.contains("1 match"));
      }

      @Test
      @DisplayName("handles invalid regex gracefully")
      void invalidRegex() throws Exception {
         String result = tool.execute(Map.of(
            "pattern", "[invalid",
            "path", tempDir.toString()));

         assertTrue(result.contains("Error"));
      }

      @Test
      @DisplayName("handles nonexistent directory")
      void nonexistentDir() throws Exception {
         String result = tool.execute(Map.of(
            "pattern", "test",
            "path", "/nonexistent/dir"));

         assertTrue(result.contains("Error"));
      }

      @Test
      @DisplayName("skips binary files")
      void skipsBinaryFiles() throws Exception {
         Files.writeString(
            tempDir.resolve("code.java"), "public int x;");
         Files.write(
            tempDir.resolve("data.class"),
            new byte[]{0, 1, 2, 3});

         String result = tool.execute(Map.of(
            "pattern", ".*",
            "path", tempDir.toString()));

         assertTrue(result.contains("code.java"));
         assertFalse(result.contains("data.class"));
      }

      @Test
      @DisplayName("searches subdirectories")
      void searchesSubdirectories() throws Exception {
         Path sub = tempDir.resolve("sub");
         Files.createDirectories(sub);
         Files.writeString(sub.resolve("deep.java"),
            "found it here\n");

         String result = tool.execute(Map.of(
            "pattern", "found it",
            "path", tempDir.toString()));

         assertTrue(result.contains("deep.java"));
         assertTrue(result.contains("1 match"));
      }

      @Test
      @DisplayName("globMatch helper works")
      void globMatchBasics() {
         assertTrue(GrepTool.globMatch(
            "Foo.java", "*.java"));
         assertFalse(GrepTool.globMatch(
            "Foo.txt", "*.java"));
         assertTrue(GrepTool.globMatch(
            "Test.java", "Test.*"));
         assertTrue(GrepTool.globMatch(
            "x", "?"));
         assertFalse(GrepTool.globMatch(
            "xx", "?"));
      }
   }

   @Nested
   @DisplayName("RequestLog")
   class RequestLogTests {

      @BeforeEach
      void setUp() {
         RequestLog.clear();
      }

      @Test
      @DisplayName("logs and retrieves requests")
      void logAndRetrieve() {
         int idx = RequestLog.logRequest(
            "chat", "gpt-4.1", "copilot", false,
            "Foo.java", 500, 20);

         assertEquals(0, idx);
         assertEquals(1, RequestLog.size());

         var entries = RequestLog.getEntries();
         assertEquals(1, entries.size());
         assertEquals("chat", entries.get(0).command());
         assertEquals("gpt-4.1", entries.get(0).model());
         assertEquals("Foo.java",
            entries.get(0).sourceName());
         assertEquals(500, entries.get(0).contextChars());
         assertEquals(20, entries.get(0).contextLines());
         assertEquals(125, entries.get(0).estInputTokens());
         assertFalse(entries.get(0).premium());
      }

      @Test
      @DisplayName("tracks premium model count")
      void premiumTracking() {
         RequestLog.logRequest(
            "chat", "gpt-4.1", "copilot", false,
            "a.java", 100, 5);
         RequestLog.logRequest(
            "review", "claude-sonnet-4-20250514", "copilot", true,
            "b.java", 200, 10);
         RequestLog.logRequest(
            "explain", "o3-mini", "copilot", true,
            "c.java", 300, 15);

         assertEquals(3, RequestLog.size());
         assertEquals(2, RequestLog.getPremiumCount());
      }

      @Test
      @DisplayName("updates response data")
      void updateResponse() {
         int idx = RequestLog.logRequest(
            "chat", "gpt-4.1", "copilot", false,
            "Foo.java", 500, 20);

         RequestLog.updateResponse(idx, 800, 2, 1500);

         var entry = RequestLog.getEntries().get(idx);
         assertEquals(200, entry.estOutputTokens());
         assertEquals(2, entry.toolCalls());
         assertEquals(1500, entry.durationMs());
         assertEquals(2, RequestLog.getToolCallCount());
         assertEquals(1500,
            RequestLog.getTotalDurationMs());
      }

      @Test
      @DisplayName("evicts old entries at max capacity")
      void eviction() {
         for (int i = 0;
               i < RequestLog.MAX_ENTRIES + 10; i++) {
            RequestLog.logRequest(
               "chat", "gpt-4.1", "copilot", false,
               "file" + i, 100, 5);
         }

         assertEquals(RequestLog.MAX_ENTRIES,
            RequestLog.size());
      }

      @Test
      @DisplayName("formatRecent shows recent entries")
      void formatRecent() {
         RequestLog.logRequest(
            "chat", "gpt-4.1", "copilot", false,
            "a.java", 400, 20);
         RequestLog.logRequest(
            "review", "o1", "copilot", true,
            "b.java", 800, 40);

         String summary = RequestLog.formatRecent(5);
         assertTrue(summary.contains(":chat"));
         assertTrue(summary.contains(":review"));
         assertTrue(summary.contains("PREMIUM"));
         assertTrue(summary.contains("a.java"));
         assertTrue(summary.contains("b.java"));
      }

      @Test
      @DisplayName("clear resets all state")
      void clearResets() {
         RequestLog.logRequest(
            "chat", "gpt-4.1", "copilot", true,
            "a.java", 100, 5);
         RequestLog.updateResponse(0, 200, 1, 500);

         RequestLog.clear();

         assertEquals(0, RequestLog.size());
         assertEquals(0, RequestLog.getPremiumCount());
         assertEquals(0, RequestLog.getToolCallCount());
         assertEquals(0, RequestLog.getTotalDurationMs());
      }
   }
}
