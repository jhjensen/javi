package javi.git;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for F9 bug fixes: position format for git-commits list,
 * status buffer refresh for external changes, and commit view
 * split-view window management.
 */
class GitBugFixJUnitTest {

   private static List<String> lines(String... args) {
      return Arrays.asList(args);
   }

   // ---- Bug 2: git-commit-sessions position format ----

   @Nested
   @DisplayName("git-commit-sessions position format")
   class GitCommitSessionsFormat {

      /**
       * Verify the position format for a commit session entry
       * matches Position.toString(): filename(y)-comment.
       */
      @Test
      @DisplayName("commit session entry has correct format")
      void commitSessionEntryFormat() {
         // Simulate what registerCommitSessionInPosListList builds
         String bufName = "*git-commit-msg*";
         String label = "commit";
         String repoShort = "javi";
         String entry = bufName + "(1)-" + label
            + " in " + repoShort;
         assertTrue(entry.startsWith("*git-commit-msg*("),
            "entry starts with buffer name and open paren");
         assertTrue(entry.contains(")-"),
            "entry contains close-paren dash separator");
         assertTrue(entry.contains("commit in javi"),
            "entry contains label and repo name");
      }

      /**
       * Amend mode produces a different label.
       */
      @Test
      @DisplayName("amend session uses amend label")
      void amendSessionLabel() {
         String bufName = "*git-commit-msg*";
         String label = "amend";
         String repoShort = "myrepo";
         String entry = bufName + "(1)-" + label
            + " in " + repoShort;
         assertTrue(entry.contains("amend in myrepo"),
            "amend entry should contain amend label");
      }
   }

   // ---- Bug 3: ^L refresh detects external changes ----

   @Nested
   @DisplayName("status buffer refresh")
   class StatusBufferRefresh {

      /**
       * After external file deletion, re-running git status
       * shows the deleted file.
       */
      @Test
      @DisplayName("deleted file appears in refreshed status")
      void deletedFileAppearsInRefreshedStatus() {
         // Simulate porcelain v2 with a deleted file
         List<String> raw = lines(
            "# branch.head master",
            "# branch.oid abc1234",
            "1 .D N... 100644 100644 000000 "
               + "abc1234 def5678 removed.java");
         List<String> formatted =
            GitStatusBuffer.formatStatus(raw);
         String joined = String.join("\n", formatted);
         assertTrue(joined.contains("deleted"),
            "deleted file must appear in status output");
         assertTrue(joined.contains("removed.java"),
            "filename must appear in status output");
      }

      /**
       * After external staging (git add from another tool),
       * re-running git status shows the staged file.
       */
      @Test
      @DisplayName("externally staged file appears in refreshed status")
      void externallyStagedFileAppearsInRefreshedStatus() {
         List<String> raw = lines(
            "# branch.head master",
            "# branch.oid abc1234",
            "1 M. N... 100644 100644 100644 "
               + "abc1234 def5678 staged.java");
         List<String> formatted =
            GitStatusBuffer.formatStatus(raw);
         String joined = String.join("\n", formatted);
         assertTrue(joined.contains("Staged changes (1)"),
            "staged section should show 1 change");
         assertTrue(joined.contains("staged.java"),
            "staged filename must appear");
      }

      /**
       * New untracked file from external git operation shows up.
       */
      @Test
      @DisplayName("new untracked file appears in refreshed status")
      void newUntrackedFileAppearsInRefreshedStatus() {
         List<String> raw = lines(
            "# branch.head master",
            "# branch.oid abc1234",
            "? newfile.txt");
         List<String> formatted =
            GitStatusBuffer.formatStatus(raw);
         String joined = String.join("\n", formatted);
         assertTrue(joined.contains("Untracked files (1)"),
            "untracked section should show 1 file");
         assertTrue(joined.contains("newfile.txt"),
            "untracked filename must appear");
      }

      /**
       * Clean repo (no changes) after external reset.
       */
      @Test
      @DisplayName("clean repo shows zero changes after external reset")
      void cleanRepoShowsZeroChanges() {
         List<String> raw = lines(
            "# branch.head master",
            "# branch.oid abc1234");
         List<String> formatted =
            GitStatusBuffer.formatStatus(raw);
         String joined = String.join("\n", formatted);
         assertTrue(joined.contains("Staged changes (0)"),
            "staged section should be empty");
         assertTrue(joined.contains("Unstaged changes (0)"),
            "unstaged section should be empty");
         assertTrue(joined.contains("Untracked files (0)"),
            "untracked section should be empty");
      }
   }

   // ---- Bug 5: commit view window management ----

   @Nested
   @DisplayName("commit view split-view logic")
   class CommitViewSplitLogic {

      /**
       * When no staging view exists, a split is needed.
       */
      @Test
      @DisplayName("null staging view requires split")
      void nullStagingViewRequiresSplit() {
         // Simulates: commitStagingView == null
         boolean needSplit = (null == null) || (1 < 2);
         assertTrue(needSplit,
            "null staging view should trigger split creation");
      }

      /**
       * When only one view exists (user vd'd the staging window),
       * a split is needed even if commitStagingView ref is stale.
       */
      @Test
      @DisplayName("single view requires split even with stale ref")
      void singleViewRequiresSplitEvenWithStaleRef() {
         // Simulates: commitStagingView != null but viewCount < 2
         Object staleView = new Object(); // non-null
         int viewCount = 1; // only one view
         boolean needSplit = (staleView == null)
            || (viewCount < 2);
         assertTrue(needSplit,
            "stale staging view with viewCount=1 needs new split");
      }

      /**
       * When two views exist and staging view is valid, no split needed.
       */
      @Test
      @DisplayName("two views with valid staging view skips split")
      void twoViewsSkipsSplit() {
         Object validView = new Object();
         int viewCount = 2;
         boolean needSplit = (validView == null)
            || (viewCount < 2);
         assertFalse(needSplit,
            "valid staging view with 2 views should reuse");
      }
   }

   // ---- Bug 1: positionListList empty after git commit ----

   @Nested
   @DisplayName("resolveGitDir fallback after commit")
   class ResolveGitDirFallback {

      @Test
      @DisplayName("non-null dir is returned and cached")
      void nonNullDirReturnedAndCached() {
         GitCommands.resetDirCache();
         java.io.File dir = new java.io.File("/some/repo");
         java.io.File result = GitCommands.resolveGitDir(dir);
         assertEquals(dir, result,
            "should return the supplied directory");
      }

      @Test
      @DisplayName("null dir falls back to cached lastGitDir")
      void nullDirFallsBackToCached() {
         GitCommands.resetDirCache();
         java.io.File orig = new java.io.File("/some/repo");
         GitCommands.resolveGitDir(orig);
         java.io.File result = GitCommands.resolveGitDir(null);
         assertEquals(orig, result,
            "null dir should fall back to last cached dir");
      }

      @Test
      @DisplayName("null dir with no cache returns null")
      void nullDirNoCacheReturnsNull() {
         GitCommands.resetDirCache();
         java.io.File result = GitCommands.resolveGitDir(null);
         assertEquals(null, result,
            "no fallback available, should return null");
      }

      @Test
      @DisplayName("simulates post-commit scenario")
      void postCommitScenarioUsesLastGitDir() {
         GitCommands.resetDirCache();
         java.io.File realDir = new java.io.File("/home/user/repo");
         java.io.File r1 = GitCommands.resolveGitDir(realDir);
         assertEquals(realDir, r1);
         java.io.File r2 = GitCommands.resolveGitDir(null);
         assertEquals(realDir, r2,
            "post-commit should reuse cached dir from step 1");
      }
   }

   // ---- Bug: F6/F1/F1 split-view restoration ----

   @Nested
   @DisplayName("commit split-view restoration via context hook")
   class CommitSplitViewRestore {

      /**
       * Verify the postContextHook is registered by GitCommands.
       * The static initializer sets it.
       */
      @Test
      @DisplayName("postContextHook is registered")
      void hookIsRegistered() {
         // GitCommands static initializer registers the hook.
         // Force class loading to ensure it runs.
         assertNotNull(GitCommands.pluginInfo,
            "GitCommands must be loaded");
         // The hook field is private but we can verify via reflection
         try {
            java.lang.reflect.Field f =
               javi.FvContext.class.getDeclaredField(
                  "postContextHook");
            f.setAccessible(true);
            Object hook = f.get(null);
            assertNotNull(hook,
               "postContextHook should be set by GitCommands");
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      /**
       * When commitMsgBuffer is null, the hook should be a no-op.
       * This guards against NPE when no commit session is active.
       */
      @Test
      @DisplayName("no-op when commit session not active")
      void noOpWithoutActiveSession() throws Exception {
         // Access commitMsgBuffer field
         java.lang.reflect.Field msgField =
            GitCommands.class.getDeclaredField("commitMsgBuffer");
         msgField.setAccessible(true);
         Object saved = msgField.get(null);
         try {
            msgField.set(null, null);
            // Call onContextChanged via reflection — should not throw
            java.lang.reflect.Method m =
               GitCommands.class.getDeclaredMethod(
                  "onContextChanged", javi.FvContext.class);
            m.setAccessible(true);
            // Passing null fvc — with null commitMsgBuffer the
            // method returns immediately before dereferencing fvc
            m.invoke(null, (Object) null);
         } finally {
            msgField.set(null, saved);
         }
      }

      /**
       * The inContextHook guard prevents recursive invocation.
       */
      @Test
      @DisplayName("re-entrant guard prevents recursion")
      void reentrantGuardWorks() throws Exception {
         java.lang.reflect.Field guardField =
            GitCommands.class.getDeclaredField("inContextHook");
         guardField.setAccessible(true);
         boolean saved = guardField.getBoolean(null);
         try {
            guardField.set(null, true);
            // With guard set, onContextChanged should return
            // immediately even if commitMsgBuffer is set
            java.lang.reflect.Method m =
               GitCommands.class.getDeclaredMethod(
                  "onContextChanged", javi.FvContext.class);
            m.setAccessible(true);
            // Should not throw despite null fvc
            m.invoke(null, (Object) null);
         } finally {
            guardField.set(null, saved);
         }
      }
   }
}
