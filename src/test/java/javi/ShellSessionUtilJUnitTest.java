package javi;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for static utility methods in {@link ShellSession}.
 *
 * <p>Uses reflection to test private static methods that are pure
 * logic with no process or I/O dependencies.</p>
 */
class ShellSessionUtilJUnitTest {

   private static Method shellQuote;
   private static Method isShellOrWrapper;
   private static Method buildCommand;

   @BeforeAll
   static void initOnce() throws Exception {
      shellQuote = ShellSession.class.getDeclaredMethod(
         "shellQuote", String.class);
      shellQuote.setAccessible(true);

      isShellOrWrapper = ShellSession.class.getDeclaredMethod(
         "isShellOrWrapper", String.class);
      isShellOrWrapper.setAccessible(true);

      buildCommand = ShellSession.class.getDeclaredMethod(
         "buildCommand", String.class);
      buildCommand.setAccessible(true);
   }

   private String quote(String val) throws Exception {
      return (String) shellQuote.invoke(null, val);
   }

   private boolean isWrapper(String cmd) throws Exception {
      return (boolean) isShellOrWrapper.invoke(null, cmd);
   }

   private String[] cmd(String host) throws Exception {
      return (String[]) buildCommand.invoke(null, host);
   }

   // ── shellQuote tests ──────────────────────────────────────

   @Test
   void quoteSimpleValue() throws Exception {
      assertEquals("'hello'", quote("hello"));
   }

   @Test
   void quoteEmptyString() throws Exception {
      assertEquals("''", quote(""));
   }

   @Test
   void quoteWithSpaces() throws Exception {
      assertEquals("'hello world'", quote("hello world"));
   }

   @Test
   void quoteWithSingleQuote() throws Exception {
      // Embedded single quote should be escaped
      assertEquals("'it'\\''s'", quote("it's"));
   }

   @Test
   void quoteWithMultipleSingleQuotes() throws Exception {
      assertEquals("'a'\\''b'\\''c'", quote("a'b'c"));
   }

   @Test
   void quoteWithDoubleQuotes() throws Exception {
      // Double quotes are not special inside single quotes
      assertEquals("'say \"hi\"'", quote("say \"hi\""));
   }

   @Test
   void quoteWithSpecialChars() throws Exception {
      assertEquals("'$HOME'", quote("$HOME"));
   }

   @Test
   void quoteWithBackslash() throws Exception {
      assertEquals("'path\\to\\file'", quote("path\\to\\file"));
   }

   // ── isShellOrWrapper tests ────────────────────────────────

   @Test
   void bashIsShell() throws Exception {
      assertTrue(isWrapper("bash"));
   }

   @Test
   void zshIsShell() throws Exception {
      assertTrue(isWrapper("zsh"));
   }

   @Test
   void shIsShell() throws Exception {
      assertTrue(isWrapper("sh"));
   }

   @Test
   void fishIsShell() throws Exception {
      assertTrue(isWrapper("fish"));
   }

   @Test
   void scriptIsWrapper() throws Exception {
      assertTrue(isWrapper("script"));
   }

   @Test
   void loginIsWrapper() throws Exception {
      assertTrue(isWrapper("login"));
   }

   @Test
   void fullPathBash() throws Exception {
      assertTrue(isWrapper("/bin/bash"));
   }

   @Test
   void fullPathZsh() throws Exception {
      assertTrue(isWrapper("/usr/bin/zsh"));
   }

   @Test
   void loginShellDashBash() throws Exception {
      // Login shells start with a dash
      assertTrue(isWrapper("-bash"));
   }

   @Test
   void loginShellDashZsh() throws Exception {
      assertTrue(isWrapper("-zsh"));
   }

   @Test
   void loginShellFullPathDash() throws Exception {
      // Full path + login dash
      assertTrue(isWrapper("/bin/-bash"));
   }

   @Test
   void htopIsNotShell() throws Exception {
      assertFalse(isWrapper("htop"));
   }

   @Test
   void vimIsNotShell() throws Exception {
      assertFalse(isWrapper("vim"));
   }

   @Test
   void sshIsNotShell() throws Exception {
      assertFalse(isWrapper("ssh"));
   }

   @Test
   void pythonIsNotShell() throws Exception {
      assertFalse(isWrapper("python3"));
   }

   @Test
   void fullPathPython() throws Exception {
      assertFalse(isWrapper("/usr/bin/python3"));
   }

   // ── buildCommand tests ────────────────────────────────────

   @Test
   void localCommandUsesScript() throws Exception {
      String[] result = cmd(null);
      assertNotNull(result);
      assertEquals("script", result[0]);
   }

   @Test
   void localCommandIncludesInteractiveFlag() throws Exception {
      String[] result = cmd(null);
      // On macOS: script -q /dev/null <shell> -i
      // On Linux: script -q -c "<shell> -i" /dev/null
      boolean hasInteractive = false;
      for (String arg : result) {
         if (arg.contains("-i")) {
            hasInteractive = true;
            break;
         }
      }
      assertTrue(hasInteractive, "should include -i for interactive");
   }

   @Test
   void sshCommandStartsWithSsh() throws Exception {
      String[] result = cmd("myhost");
      assertNotNull(result);
      assertEquals("ssh", result[0]);
   }

   @Test
   void sshCommandIncludesHost() throws Exception {
      String[] result = cmd("myhost");
      assertEquals("myhost", result[result.length - 1]);
   }

   @Test
   void sshCommandHasTtyFlags() throws Exception {
      String[] result = cmd("remotehost");
      // Should have -t -t for force TTY allocation
      int tCount = 0;
      for (String arg : result)
         if ("-t".equals(arg)) tCount++;
      assertEquals(2, tCount, "ssh should have two -t flags");
   }

   // ── getLeafProcessName tests ──────────────────────────────

   @Test
   void getLeafProcessNameInvalidPid() {
      // Non-existent PID should return null
      String result = ShellSession.getLeafProcessName(-1);
      assertNull(result);
   }

   @Test
   void getLeafProcessNameCurrentProcess() {
      // Current process PID should return something
      long pid = ProcessHandle.current().pid();
      // The test JVM has child processes (Gradle worker), so
      // the leaf process name may vary. Just ensure no exception.
      ShellSession.getLeafProcessName(pid);
      // No assertion on value — just verifying no exception
   }

   @Test
   void getLeafProcessNameZeroPid() {
      // PID 0 behavior is OS-specific — just verify no exception
      ShellSession.getLeafProcessName(0);
   }
}
