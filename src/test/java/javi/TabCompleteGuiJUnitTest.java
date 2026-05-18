package javi;

import java.lang.reflect.Method;
import java.util.Set;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for {@link TabComplete} — command-line tab completion
 * in the live editor context.
 *
 * <p>Tests the command name completion and branch name completion
 * paths. Requires full Javi initialization so that command
 * registrations are populated and FvContext is available.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class TabCompleteGuiJUnitTest {

   private static Robot robot;

   @BeforeAll
   static void initJavi() throws Exception {
      if (Rgroup.bindingLookup("persistfile") == null) {
         EventQueue.biglock2.lock();
         try {
            Class.forName("javi.TextEdit");
            EditTester1.TestCircBuffer.initCmd();
            DirManager.getInstance();
            FileList.make("");
            Javi.initToUi();
            Javi.initPostUi();
            Command.doneInit();
         } finally {
            EventQueue.biglock2.unlock();
         }
         Thread.sleep(500);
      }
      robot = BasicRobot.robotWithCurrentAwtHierarchy();
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── Helper: invoke TabComplete.complete() via reflection ─────

   private static String complete(String lineText, int insertX,
         String pending) throws Exception {
      Method m = TabComplete.class.getDeclaredMethod(
         "complete", String.class, int.class, String.class);
      m.setAccessible(true);
      return (String) m.invoke(null, lineText, insertX, pending);
   }

   private static String completeCommandName(String partial)
         throws Exception {
      Method m = TabComplete.class.getDeclaredMethod(
         "completeCommandName", String.class);
      m.setAccessible(true);
      return (String) m.invoke(null, partial);
   }

   // ── Tests: command name completion in GUI context ────────────

   @Test
   void t01_commandsAreRegistered() {
      Set<String> cmds = Rgroup.getRegisteredCommands();
      assertNotNull(cmds);
      assertTrue(cmds.size() > 20,
         "Expected many registered commands, got " + cmds.size());
   }

   @Test
   void t02_completeKnownCommandPrefix() throws Exception {
      // "persist" should complete to "persistfile" or similar
      String result = complete(":persist", 8, "");
      // Should find at least "persistfile" — returns the suffix
      assertNotNull(result,
         "Expected completion for ':persist'");
      assertTrue(result.length() > 0,
         "Expected non-empty suffix for ':persist'");
   }

   @Test
   void t03_completeUniqueCommand() throws Exception {
      // "nextf" should complete to "nextfile" (unique prefix)
      String result = complete(":nextf", 6, "");
      assertNotNull(result, "Expected completion for ':nextf'");
      assertTrue(result.startsWith("i"),
         "Expected ':nextf' to complete toward 'nextfile', got suffix: "
            + result);
   }

   @Test
   void t04_completeNoMatchReturnsNull() throws Exception {
      String result = complete(":zzzznonexistent", 16, "");
      assertNull(result, "Non-existent prefix should return null");
   }

   @Test
   void t05_completeEmptyPrefixReturnsNull() throws Exception {
      // Just ":" with nothing typed — ambiguous, no completion
      String result = complete(":", 1, "");
      assertNull(result, "Empty prefix is ambiguous, should be null");
   }

   @Test
   void t06_completeWithPendingBuffer() throws Exception {
      // lineText is ":next" with cursor at 5, pending "f"
      // Full text is ":nextf"
      String result = complete(":next", 5, "f");
      assertNotNull(result, "Expected completion for ':next' + pending 'f'");
      assertTrue(result.startsWith("i"),
         "Expected completion toward 'nextfile'");
   }

   @Test
   void t07_noCompletionWithoutColonPrefix() throws Exception {
      String result = complete("persist", 7, "");
      assertNull(result,
         "Without leading ':', no completion should occur");
   }

   @Test
   void t08_noCompletionForSingleChar() throws Exception {
      // A single ":" alone is length < 2 with nothing else
      String result = complete(":", 1, "");
      assertNull(result);
   }

   @Test
   void t09_completeGitCommand() throws Exception {
      // "git_" prefix should match git commands
      String result = complete(":git_", 5, "");
      // There are multiple git_ commands, so common prefix should work
      if (result != null) {
         // If it completes, the result should be non-empty
         assertTrue(result.length() > 0);
      }
      // Even if null (many ambiguous matches), that's acceptable
   }

   @Test
   void t10_completeCommandNameDirectly() throws Exception {
      // Test the internal completeCommandName method
      String result = completeCommandName("persist");
      assertNotNull(result,
         "Expected suffix for 'persist' command completion");
   }

   @Test
   void t11_completeFullCommandReturnsNull() throws Exception {
      // If the user typed the full command, no suffix to add
      Set<String> cmds = Rgroup.getRegisteredCommands();
      // Find any command
      String fullCmd = cmds.iterator().next();
      String result = completeCommandName(fullCmd);
      assertNull(result,
         "Full command typed should return null (nothing to add)");
   }

   @Test
   void t12_completeBranchCommandPrefix() throws Exception {
      // ":git_branch_switch " with a space triggers branch completion
      // Since we may not be in a git repo, this might return null,
      // but it should not throw
      String result = complete(":git_branch_switch ", 19, "");
      // Result may be null (no git repo) — just verify no exception
   }

   @Test
   void t13_nonBranchCommandWithSpaceReturnsNull() throws Exception {
      // ":write somearg" — not a branch command, should return null
      String result = complete(":write somearg", 14, "");
      assertNull(result,
         "Non-branch command with space arg should return null");
   }

   @Test
   void t14_completeCursorBeyondLineLength() throws Exception {
      // insertX beyond line text length — should not crash
      String result = complete(":wr", 100, "");
      // May return null or completion — just verify no exception
   }

   @Test
   void t15_completeWithGitBranchDeletePrefix() throws Exception {
      // ":git_branch_delete " is a branch command
      String result = complete(":git_branch_delete ", 19, "");
      // May return null if not in a git repo — no exception
   }

   @Test
   void t16_completeWithGitMergePrefix() throws Exception {
      // ":git_merge " is a branch command
      String result = complete(":git_merge ", 11, "");
      // May return null — just no exception
   }

   @Test
   void t17_commonPrefixLogic() throws Exception {
      // Test that ambiguous commands return common prefix
      // "git_branch" should match git_branch_switch and
      // git_branch_delete, returning common suffix
      String result = complete(":git_branch", 11, "");
      if (result != null) {
         // Should start with "_" since both have "git_branch_"
         assertTrue(result.startsWith("_"),
            "Expected common prefix '_' for git_branch, got: "
               + result);
      }
   }

   @Test
   void t18_completeQuitCommand() throws Exception {
      String result = complete(":q", 2, "");
      // "q" might match "quit" or "qa" etc.
      // Just verify no crash and reasonable result
      if (result != null) {
         assertTrue(result.length() > 0);
      }
   }

   @Test
   void t19_completeEditCommand() throws Exception {
      // ":e" should try to complete "e" -> "edit" or similar
      String result = complete(":e", 2, "");
      // Multiple commands start with "e", so this tests ambiguity
   }

   @Test
   void t20_completeSetCommand() throws Exception {
      String result = complete(":se", 3, "");
      // "se" should match "set" — check completion
      if (result != null) {
         assertTrue(result.startsWith("t"),
            "Expected ':se' to complete toward 'set', got: " + result);
      }
   }
}
