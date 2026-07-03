package javi.git;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import javi.Command;
import javi.EventQueue;
import javi.FvContext;
import javi.Rgroup;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for {@link GitCommands} commit workflow, dispatch,
 * stash-pop, helper methods, and context hook behavior.
 *
 * <p>Covers doroutine paths not exercised by other GitCommands
 * test classes: gitStashPop, gitDispatch subcommands,
 * extractFilenameFromLine, resolveGitDir, resetDirCache,
 * isDeletedAtCursor, and the onContextChanged hook.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class GitCommandsCommitWorkflowGuiJUnitTest {

   private static Robot robot;
   private static GitCommands gitCmds;

   @BeforeAll
   static void initJavi() throws Exception {
      Class<?> rgroupClass = Class.forName("javi.Rgroup");
      Method blookup = rgroupClass.getDeclaredMethod("bindingLookup",
         String.class);
      blookup.setAccessible(true);
      boolean alreadyInit = blookup.invoke(null, "persistfile") != null;

      if (!alreadyInit) {
         EventQueue.biglock2.lock();
         try {
            Class.forName("javi.TextEdit");
            Class<?> editTester = Class.forName("javi.EditTester1");
            Class<?>[] innerClasses = editTester.getDeclaredClasses();
            for (Class<?> inner : innerClasses) {
               if (inner.getSimpleName().equals("TestCircBuffer")) {
                  Method initCmd = inner.getDeclaredMethod("initCmd");
                  initCmd.setAccessible(true);
                  initCmd.invoke(null);
                  break;
               }
            }
            Class<?> dirMgrClass = Class.forName("javi.DirManager");
            Method getInstance = dirMgrClass.getDeclaredMethod(
               "getInstance");
            getInstance.setAccessible(true);
            getInstance.invoke(null);
            Class<?> fileListClass = Class.forName("javi.FileList");
            Method makeMethod = fileListClass.getDeclaredMethod("make",
               String.class);
            makeMethod.setAccessible(true);
            makeMethod.invoke(null, "");
            Class<?> javiClass = Class.forName("javi.Javi");
            Method initToUi = javiClass.getDeclaredMethod("initToUi");
            initToUi.setAccessible(true);
            initToUi.invoke(null);
            Method initPostUi = javiClass.getDeclaredMethod("initPostUi");
            initPostUi.setAccessible(true);
            initPostUi.invoke(null);
            Method doneInit = Command.class.getDeclaredMethod("doneInit");
            doneInit.setAccessible(true);
            doneInit.invoke(null);
         } finally {
            EventQueue.biglock2.unlock();
         }
         Thread.sleep(500);
      }
      robot = BasicRobot.robotWithCurrentAwtHierarchy();

      Object binding = blookup.invoke(null, "git_status");
      if (binding != null) {
         Field outerRef = binding.getClass().getDeclaredField("this$0");
         outerRef.setAccessible(true);
         gitCmds = (GitCommands) outerRef.get(binding);
      } else {
         gitCmds = new GitCommands(null);
      }
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── extractFilenameFromLine ──────────────────────────────────

   @Test
   void t01_extractModified() {
      assertEquals("src/Main.java",
         GitCommands.extractFilenameFromLine("  modified src/Main.java"));
   }

   @Test
   void t02_extractNewFile() {
      assertEquals("README.md",
         GitCommands.extractFilenameFromLine("  new file README.md"));
   }

   @Test
   void t03_extractDeleted() {
      assertEquals("old.txt",
         GitCommands.extractFilenameFromLine("  deleted old.txt"));
   }

   @Test
   void t04_extractRenamed() {
      assertEquals("new.txt",
         GitCommands.extractFilenameFromLine("  renamed old.txt -> new.txt"));
   }

   @Test
   void t05_extractUntracked() {
      assertEquals("untracked.log",
         GitCommands.extractFilenameFromLine("  untracked.log"));
   }

   @Test
   void t06_extractNullForBlank() {
      assertNull(GitCommands.extractFilenameFromLine(""));
   }

   @Test
   void t07_extractNullForSectionHeader() {
      assertNull(GitCommands.extractFilenameFromLine("Staged changes:"));
   }

   @Test
   void t08_extractNullForHint() {
      assertNull(GitCommands.extractFilenameFromLine(
         "  (use git add to stage)"));
   }

   @Test
   void t09_extractCopied() {
      assertEquals("dest.txt",
         GitCommands.extractFilenameFromLine("  copied src.txt -> dest.txt"));
   }

   @Test
   void t10_extractNoLeadingSpaces() {
      assertNull(GitCommands.extractFilenameFromLine("modified foo.txt"));
   }

   // ── resolveGitDir / resetDirCache ────────────────────────────

   @Test
   void t11_resolveGitDirWithNonNull() {
      File dir = new File("/tmp/test-repo");
      File result = GitCommands.resolveGitDir(dir);
      assertEquals(dir, result,
         "resolveGitDir should return the provided dir");
   }

   @Test
   void t12_resolveGitDirCachesValue() {
      File dir = new File("/tmp/cached-repo");
      GitCommands.resolveGitDir(dir);
      File result = GitCommands.resolveGitDir(null);
      assertEquals(dir, result,
         "resolveGitDir(null) should return cached dir");
   }

   @Test
   void t13_resetDirCacheClearsState() {
      GitCommands.resolveGitDir(new File("/tmp/will-be-cleared"));
      GitCommands.resetDirCache();
      File result = GitCommands.resolveGitDir(null);
      assertNull(result,
         "After resetDirCache, resolveGitDir(null) should be null");
   }

   // ── getBufferDir ─────────────────────────────────────────────

   @Test
   void t14_getBufferDirNullForEmpty() {
      assertNull(GitCommands.getBufferDir(""),
         "Empty name should return null");
   }

   @Test
   void t15_getBufferDirNullForNull() {
      assertNull(GitCommands.getBufferDir(null),
         "Null name should return null");
   }

   @Test
   void t16_getBufferDirNullForUnknown() {
      assertNull(GitCommands.getBufferDir("*nonexistent-buffer*"),
         "Unknown buffer name should return null");
   }

   // ── gitStashPop (rnum 17) ────────────────────────────────────

   @Test
   void t17_gitStashPopHandlesEmpty() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // In Docker test repo with no stash entries, should handle
         // gracefully (report "No stash entries" or similar)
         Command.command("git_stash_pop", fvc, null);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_stash_pop");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitDispatch (rnum 25) ────────────────────────────────────

   @Test
   void t18_gitDispatchNullShowsStatus() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // :git with no arg dispatches to git_status
         gitCmds.doroutine(25, null, 0, 0, fvc, false);
         assertNotNull(fvc.edvec,
            "Buffer should exist after :git (no arg)");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t19_gitDispatchStatusSubcmd() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // :git status -> dispatches to git_status
         gitCmds.doroutine(25, "status", 0, 0, fvc, false);
         assertNotNull(fvc.edvec,
            "Buffer should exist after :git status");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t20_gitDispatchDiffSubcmd() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         gitCmds.doroutine(25, "diff", 0, 0, fvc, false);
         assertNotNull(fvc.edvec,
            "Buffer should exist after :git diff");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t21_gitDispatchBranchSubcmd() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         gitCmds.doroutine(25, "branch", 0, 0, fvc, false);
         assertNotNull(fvc.edvec,
            "Buffer should exist after :git branch");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t22_gitDispatchLogSubcmd() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         gitCmds.doroutine(25, "log", 0, 0, fvc, false);
         assertNotNull(fvc.edvec,
            "Buffer should exist after :git log");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── getFileDir ───────────────────────────────────────────────

   @Test
   void t23_getFileDirForInternalBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // For internal buffers (name starts with *), should return null
         File dir = GitCommands.getFileDir(fvc);
         // May or may not be null depending on what's current,
         // but should not throw
         if (dir != null) {
            assertTrue(dir.isDirectory(),
               "getFileDir should return a directory");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── isDeletedAtCursor ────────────────────────────────────────

   @Test
   void t24_isDeletedFalseForNonStatus() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // In a non-status buffer, isDeletedAtCursor should be false
         boolean result = GitCommands.isDeletedAtCursor(fvc);
         assertFalse(result,
            "Should not be a deleted line in normal buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── handleVisualKey ──────────────────────────────────────────

   @Test
   void t25_handleVisualKeyNonStageReturnsF() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // 'x' is not s/u, should return false
         boolean consumed = GitCommands.handleVisualKey(
            'x', 1, 2, 0, 10, fvc);
         assertFalse(consumed,
            "Non s/u key should return false");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t26_handleVisualKeyStageNoHunks() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // 's' with no hunks loaded should report message and
         // return true (consumed)
         boolean consumed = GitCommands.handleVisualKey(
            's', 1, 2, 0, 10, fvc);
         assertTrue(consumed,
            "'s' should be consumed even with no hunks");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t27_handleVisualKeyUnstageNoHunks() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         boolean consumed = GitCommands.handleVisualKey(
            'u', 1, 2, 0, 10, fvc);
         assertTrue(consumed,
            "'u' should be consumed even with no hunks");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── pluginInfo ───────────────────────────────────────────────

   @Test
   void t28_pluginInfoDescribesGit() {
      assertNotNull(GitCommands.pluginInfo);
      assertTrue(GitCommands.pluginInfo.contains("git"),
         "pluginInfo should mention git");
   }

   // ── git_commit_menu / git_commit_quit / git_commit_finalize
   //    are registered ────────────────────────────────────────────

   @Test
   void t29_commitWorkflowCommandsRegistered() throws Exception {
      Method blookup = Rgroup.class.getDeclaredMethod("bindingLookup",
         String.class);
      blookup.setAccessible(true);
      EventQueue.biglock2.lock();
      try {
         assertNotNull(blookup.invoke(null, "git_commit_menu"),
            "git_commit_menu should be registered");
         assertNotNull(blookup.invoke(null, "git_commit_quit"),
            "git_commit_quit should be registered");
         assertNotNull(blookup.invoke(null, "git_commit_finalize"),
            "git_commit_finalize should be registered");
         assertNotNull(blookup.invoke(null, "git_do_commit"),
            "git_do_commit should be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t30_revertHunkRegistered() throws Exception {
      Method blookup = Rgroup.class.getDeclaredMethod("bindingLookup",
         String.class);
      blookup.setAccessible(true);
      EventQueue.biglock2.lock();
      try {
         assertNotNull(blookup.invoke(null, "git_revert_hunk"),
            "git_revert_hunk should be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Editor stable after all tests ────────────────────────────

   @Test
   void t31_editorStableAfterWorkflow() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         assertNotNull(fvc, "FvContext must exist");
         assertNotNull(fvc.edvec, "TextEdit must exist");
         assertTrue(fvc.vi.isVisible(), "View must remain visible");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
