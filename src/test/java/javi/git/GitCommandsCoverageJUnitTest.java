package javi.git;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Coverage tests for {@link GitCommands} utility methods.
 *
 * <p>Tests the static helper methods accessible via reflection.
 * Does not test the full doroutine dispatch which requires a
 * fully initialized editor environment.</p>
 */
class GitCommandsCoverageJUnitTest {

   // ── getBufferDir ────────────────────────────────────────────

   @Test
   void getBufferDirReturnsNullForUnknownName() {
      assertNull(GitCommands.getBufferDir("nonexistent-buffer"),
         "unknown buffer name should return null");
   }

   @Test
   void getBufferDirReturnsNullForNull() {
      assertNull(GitCommands.getBufferDir(null),
         "null buffer name should return null");
   }

   @Test
   void getBufferDirReturnsNullForEmptyString() {
      assertNull(GitCommands.getBufferDir(""),
         "empty buffer name should return null");
   }

   // ── parseLogArgs (private, via reflection) ──────────────────

   @Test
   void parseLogArgsNullReturnsNull() throws Exception {
      Method m = GitCommands.class.getDeclaredMethod(
         "parseLogArgs", Object.class);
      m.setAccessible(true);
      assertNull(m.invoke(null, (Object) null));
   }

   @Test
   void parseLogArgsEmptyStringReturnsNull() throws Exception {
      Method m = GitCommands.class.getDeclaredMethod(
         "parseLogArgs", Object.class);
      m.setAccessible(true);
      assertNull(m.invoke(null, ""));
   }

   @Test
   void parseLogArgsWhitespaceOnlyReturnsNull() throws Exception {
      Method m = GitCommands.class.getDeclaredMethod(
         "parseLogArgs", Object.class);
      m.setAccessible(true);
      assertNull(m.invoke(null, "   "));
   }

   @Test
   void parseLogArgsSingleArgReturnsSingleElement() throws Exception {
      Method m = GitCommands.class.getDeclaredMethod(
         "parseLogArgs", Object.class);
      m.setAccessible(true);
      String[] result = (String[]) m.invoke(null, "--all");
      assertNotNull(result);
      assertEquals(1, result.length);
      assertEquals("--all", result[0]);
   }

   @Test
   void parseLogArgsMultipleArgsSplitsOnWhitespace() throws Exception {
      Method m = GitCommands.class.getDeclaredMethod(
         "parseLogArgs", Object.class);
      m.setAccessible(true);
      String[] result = (String[]) m.invoke(null,
         "--all --oneline src/");
      assertNotNull(result);
      assertEquals(3, result.length);
      assertArrayEquals(
         new String[]{"--all", "--oneline", "src/"},
         result);
   }

   @Test
   void parseLogArgsExtraWhitespaceIgnored() throws Exception {
      Method m = GitCommands.class.getDeclaredMethod(
         "parseLogArgs", Object.class);
      m.setAccessible(true);
      String[] result = (String[]) m.invoke(null, "  -n  10  ");
      assertNotNull(result);
      assertEquals(2, result.length);
      assertEquals("-n", result[0]);
      assertEquals("10", result[1]);
   }

   // ── pluginInfo field ────────────────────────────────────────

   @Test
   void pluginInfoIsNonNull() {
      assertNotNull(GitCommands.pluginInfo);
   }

   @Test
   void pluginInfoContainsGit() {
      assertNotNull(GitCommands.pluginInfo);
      org.junit.jupiter.api.Assertions.assertTrue(
         GitCommands.pluginInfo.contains("git"),
         "pluginInfo should mention git");
   }
}
