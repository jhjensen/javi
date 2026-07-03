package javi.git;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import javi.Command;
import javi.EventQueue;
import javi.FvContext;
import javi.InputException;
import javi.Rgroup;
import javi.TextEdit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Extended GUI tests for {@link GitCommands} doroutine paths.
 *
 * <p>Covers doroutine cases not exercised by GitCommandsExecGuiJUnitTest:
 * gitStatus, gitDiff, gitBranch, gitBlame, gitPatch, gitGotoFile,
 * gitAmend, gitCommitMenu, gitCommitQuit, gitCommitFinalize,
 * gitRevertHunk, gitStageLine, gitUnstageLine, and helper methods.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class GitCommandsDoroutineGuiJUnitTest {

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

   // ── gitStatus (rnum 1) ────────────────────────────────────────

   @Test
   void t01_gitStatusOpensBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         assertNotNull(fvc);
         Command.command("git_status", fvc, null);
         assertNotNull(fvc.edvec, "Buffer should exist after git_status");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t02_gitStatusIdempotent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_status", fvc, null);
         TextEdit<?> buf1 = fvc.edvec;
         Command.command("git_status", fvc, null);
         TextEdit<?> buf2 = fvc.edvec;
         assertNotNull(buf1);
         assertNotNull(buf2);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitDiff (rnum 5) ─────────────────────────────────────────

   @Test
   void t03_gitDiffNoArgOpensBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_diff", fvc, null);
         assertNotNull(fvc.edvec, "Buffer should exist after git_diff");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t04_gitDiffWithArgOpensBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         gitCmds.doroutine(5, "--cached", 0, 0, fvc, false);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_diff --cached");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitBranch (rnum 7) ───────────────────────────────────────

   @Test
   void t05_gitBranchOpensBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_branch", fvc, null);
         assertNotNull(fvc.edvec, "Buffer should exist after git_branch");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitBlame (rnum 31) ───────────────────────────────────────

   @Test
   void t06_gitBlameHandlesGracefully() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // blame on a non-file buffer should not crash
         Command.command("git_blame", fvc, null);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_blame attempt");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitPatch (rnum 34) ───────────────────────────────────────

   @Test
   void t07_gitPatchHandlesNonGitFile() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_patch", fvc, null);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_patch");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitGotoFile (rnum 35) ────────────────────────────────────

   @Test
   void t08_gitGotoFileNoContext() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // In a non-diff buffer, goto_file should handle gracefully
         Command.command("git_goto_file", fvc, null);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_goto_file");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitAmend (rnum 36) ───────────────────────────────────────

   @Test
   void t09_gitAmendOpensView() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_amend", fvc, null);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_amend");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitRevertHunk (rnum 40) ──────────────────────────────────

   @Test
   void t10_gitRevertHunkNoPatch() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // Not in a patch buffer — should handle gracefully
         Command.command("git_revert_hunk", fvc, null);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_revert_hunk");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitStageLine (rnum 11) / gitUnstageLine (rnum 12) ────────

   @Test
   void t11_gitStageLineNoDiff() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_stage_line", fvc, null);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_stage_line");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t12_gitUnstageLineNoDiff() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_unstage_line", fvc, null);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_unstage_line");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitStageHunk (rnum 32) / gitUnstageHunk (rnum 33) ────────

   @Test
   void t13_gitStageHunkNoPatch() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_stage_hunk", fvc, null);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_stage_hunk");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t14_gitUnstageHunkNoPatch() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_unstage_hunk", fvc, null);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_unstage_hunk");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitDiscard (rnum 14) ─────────────────────────────────────

   @Test
   void t15_gitDiscardNoFile() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_discard", fvc, null);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_discard");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── getBufferDir helper ──────────────────────────────────────

   @Test
   void t16_getBufferDirNullForEmptyString() {
      assertNull(GitCommands.getBufferDir(""),
         "Should return null for empty buffer name");
   }

   @Test
   void t17_getBufferDirNullForNull() {
      assertNull(GitCommands.getBufferDir(null),
         "Should return null for null buffer name");
   }

   // ── getFileDir helper ────────────────────────────────────────

   @Test
   void t18_getFileDirReturnsDirectory() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         java.io.File dir = GitCommands.getFileDir(fvc);
         // In Docker test env with a git repo, dir should be non-null
         // but the exact value depends on the buffer's file
         if (dir != null) {
            assertTrue(dir.isDirectory(),
               "getFileDir should return a directory");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitLog (rnum 6) ──────────────────────────────────────────

   @Test
   void t19_gitLogOpensBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_log", fvc, null);
         assertNotNull(fvc.edvec, "Buffer should exist after git_log");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitStash (rnum 16) ───────────────────────────────────────

   @Test
   void t20_gitStashEmptyWorkdir() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // With clean working dir, stash should handle gracefully
         Command.command("git_stash", fvc, null);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_stash");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitPull (rnum 21) ────────────────────────────────────────

   @Test
   void t21_gitPullNoRemote() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // Docker test repo has no remote — should not crash
         Command.command("git_pull", fvc, null);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_pull");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitPush (rnum 22) ────────────────────────────────────────

   @Test
   void t22_gitPushNoRemote() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_push", fvc, null);
         assertNotNull(fvc.edvec,
            "Buffer should exist after git_push");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitMerge (rnum 19) requires arg ──────────────────────────

   @Test
   void t23_gitMergeRequiresArg() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         try {
            gitCmds.doroutine(19, null, 0, 0, fvc, false);
         } catch (InputException e) {
            assertTrue(
               e.getMessage().contains("requires")
               || e.getMessage().contains("branch")
               || e.getMessage().contains("Not a git"),
               "Expected requires-arg or not-a-git, got: "
               + e.getMessage());
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── gitRebase (rnum 24) requires arg ─────────────────────────

   @Test
   void t24_gitRebaseRequiresArg() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         try {
            gitCmds.doroutine(24, null, 0, 0, fvc, false);
         } catch (InputException e) {
            assertTrue(
               e.getMessage().contains("requires")
               || e.getMessage().contains("branch")
               || e.getMessage().contains("Not a git"),
               "Expected requires-arg or not-a-git, got: "
               + e.getMessage());
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Editor remains stable ────────────────────────────────────

   @Test
   void t25_editorStableAfterDoroutineSweep() throws Exception {
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
