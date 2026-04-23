package javi.git;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javi.EventQueue;
import javi.TestInit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage tests for {@link GitCommands} — private utility
 * methods tested via reflection.
 */
class GitCommandsExtendedJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
   }

   // ── firstLine (private static) ───────────────────────────

   @Nested
   @DisplayName("firstLine()")
   class FirstLine {

      private String invoke(String input) throws Exception {
         Method m = GitCommands.class.getDeclaredMethod(
            "firstLine", String.class);
         m.setAccessible(true);
         return (String) m.invoke(null, input);
      }

      @Test
      @DisplayName("single line returns entire string")
      void singleLine() throws Exception {
         assertEquals("hello world", invoke("hello world"));
      }

      @Test
      @DisplayName("multi-line returns only first line")
      void multiLine() throws Exception {
         assertEquals("first", invoke("first\nsecond\nthird"));
      }

      @Test
      @DisplayName("empty string returns empty")
      void emptyString() throws Exception {
         assertEquals("", invoke(""));
      }

      @Test
      @DisplayName("leading newline returns empty first line")
      void leadingNewline() throws Exception {
         assertEquals("", invoke("\nsecond"));
      }

      @Test
      @DisplayName("trailing newline returns content before newline")
      void trailingNewline() throws Exception {
         assertEquals("content", invoke("content\n"));
      }
   }

   // ── parseIntSafe (private static) ────────────────────────

   @Nested
   @DisplayName("parseIntSafe()")
   class ParseIntSafe {

      private int invoke(String input) throws Exception {
         Method m = GitDiffNav.class.getDeclaredMethod(
            "parseIntSafe", String.class);
         m.setAccessible(true);
         return (int) m.invoke(null, input);
      }

      @Test
      @DisplayName("valid integer returns value")
      void validInt() throws Exception {
         assertEquals(42, invoke("42"));
      }

      @Test
      @DisplayName("negative integer returns value")
      void negativeInt() throws Exception {
         assertEquals(-7, invoke("-7"));
      }

      @Test
      @DisplayName("zero returns 0")
      void zero() throws Exception {
         assertEquals(0, invoke("0"));
      }

      @Test
      @DisplayName("whitespace-padded integer is trimmed")
      void paddedInt() throws Exception {
         assertEquals(123, invoke("  123  "));
      }

      @Test
      @DisplayName("non-numeric returns 0")
      void nonNumeric() throws Exception {
         assertEquals(0, invoke("abc"));
      }

      @Test
      @DisplayName("empty string returns 0")
      void emptyString() throws Exception {
         assertEquals(0, invoke(""));
      }

      @Test
      @DisplayName("mixed content returns 0")
      void mixed() throws Exception {
         assertEquals(0, invoke("12abc"));
      }

      @Test
      @DisplayName("large number returns value")
      void largeNumber() throws Exception {
         assertEquals(999999, invoke("999999"));
      }
   }

   // ── createBuffer (private static) ────────────────────────

   @Nested
   @DisplayName("createBuffer()")
   class CreateBuffer {

      @SuppressWarnings("unchecked")
      private javi.TextEdit<String> invoke(String name,
            List<String> lines) throws Exception {
         Method m = GitCommands.class.getDeclaredMethod(
            "createBuffer", String.class, List.class);
         m.setAccessible(true);
         EventQueue.biglock2.lock();
         try {
            return (javi.TextEdit<String>) m.invoke(null, name, lines);
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("creates non-null buffer from content")
      void createsNonNull() throws Exception {
         List<String> lines = Arrays.asList("line1", "line2", "line3");
         javi.TextEdit<String> buf = invoke("*cbuf-test1*", lines);
         assertNotNull(buf);
      }

      @Test
      @DisplayName("creates buffer from empty list")
      void emptyList() throws Exception {
         List<String> lines = Arrays.asList();
         javi.TextEdit<String> buf = invoke("*cbuf-empty*", lines);
         assertNotNull(buf);
      }

      @Test
      @DisplayName("buffer has at least one line")
      void hasContent() throws Exception {
         List<String> lines = Arrays.asList("hello");
         javi.TextEdit<String> buf = invoke("*cbuf-one*", lines);
         assertNotNull(buf);
         assertTrue(buf.readIn() >= 1,
            "buffer should have at least one line");
      }

      @Test
      @DisplayName("buffer file descriptor is not null")
      void hasFdes() throws Exception {
         List<String> lines = Arrays.asList("content");
         javi.TextEdit<String> buf = invoke("*cbuf-fdes*", lines);
         assertNotNull(buf);
         assertNotNull(buf.fdes());
      }
   }

   // ── getBufferDir edge cases ──────────────────────────────

   @Nested
   @DisplayName("getBufferDir() edge cases")
   class GetBufferDirEdgeCases {

      @Test
      @DisplayName("special chars in buffer name returns null")
      void specialCharsReturnNull() {
         assertNull(GitCommands.getBufferDir("*git-log:special*"));
      }

      @Test
      @DisplayName("very long name returns null")
      void longNameReturnsNull() {
         String longName = "x".repeat(1000);
         assertNull(GitCommands.getBufferDir(longName));
      }
   }

   // ── pluginInfo ───────────────────────────────────────────

   @Test
   @DisplayName("pluginInfo is descriptive and non-empty")
   void pluginInfoDescriptive() {
      assertNotNull(GitCommands.pluginInfo);
      assertTrue(GitCommands.pluginInfo.length() > 3,
         "pluginInfo should be descriptive");
   }
}
