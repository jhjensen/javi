package javi.git;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import javi.Command;
import javi.EventQueue;
import javi.FvContext;
import javi.InputException;
import javi.Rgroup;

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

/**
 * AssertJ Swing GUI tests for {@link GitCommands} doroutine dispatch.
 *
 * <p>Requires a full Javi GUI context. Uses reflection for package-private
 * init methods since this class is in the javi.git package. Tests exercise
 * git commands that read from the file system and editor buffer state.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class GitCommandsGuiJUnitTest {

   private static Robot robot;
   private static GitCommands gitCmds;

   @BeforeAll
   static void initJavi() throws Exception {
      // Guard: skip init if another GUI test class already initialized Javi
      Class<?> rgroupClass = Class.forName("javi.Rgroup");
      Method blookup = rgroupClass.getDeclaredMethod("bindingLookup",
         String.class);
      blookup.setAccessible(true);
      boolean alreadyInit = blookup.invoke(null, "persistfile") != null;

      if (!alreadyInit) {
         EventQueue.biglock2.lock();
         try {
            Class.forName("javi.TextEdit");
            // EditTester1.TestCircBuffer.initCmd() via reflection
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
            // DirManager.getInstance() via reflection
            Class<?> dirMgrClass = Class.forName("javi.DirManager");
            Method getInstance = dirMgrClass.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            getInstance.invoke(null);
            // FileList.make("") via reflection
            Class<?> fileListClass = Class.forName("javi.FileList");
            Method makeMethod = fileListClass.getDeclaredMethod("make",
               String.class);
            makeMethod.setAccessible(true);
            makeMethod.invoke(null, "");
            // Javi.initToUi() + Javi.initPostUi()
            Class<?> javiClass = Class.forName("javi.Javi");
            Method initToUi = javiClass.getDeclaredMethod("initToUi");
            initToUi.setAccessible(true);
            initToUi.invoke(null);
            Method initPostUi = javiClass.getDeclaredMethod("initPostUi");
            initPostUi.setAccessible(true);
            initPostUi.invoke(null);
            // Command.doneInit() via reflection
            Method doneInit = Command.class.getDeclaredMethod("doneInit");
            doneInit.setAccessible(true);
            doneInit.invoke(null);
         } finally {
            EventQueue.biglock2.unlock();
         }
         Thread.sleep(500);
      }
      robot = BasicRobot.robotWithCurrentAwtHierarchy();

      // Get existing GitCommands instance from the command registry
      // (static initializer already created one and registered commands)
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

   /** Helper: call Rgroup.bindingLookup via reflection. */
   private static Object bindingLookup(String name) throws Exception {
      Method m = Rgroup.class.getDeclaredMethod("bindingLookup",
         String.class);
      m.setAccessible(true);
      return m.invoke(null, name);
   }

   // ── Command registration tests ──────────────────────────────

   @Test
   void t01_gitStatusRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(bindingLookup("git_status"),
            "git_status should be registered");
         assertNotNull(bindingLookup("git_diff"),
            "git_diff should be registered");
         assertNotNull(bindingLookup("git_log"),
            "git_log should be registered");
         assertNotNull(bindingLookup("git_branch"),
            "git_branch should be registered");
         assertNotNull(bindingLookup("git_commit"),
            "git_commit should be registered");
         assertNotNull(bindingLookup("git_blame"),
            "git_blame should be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t02_gitStageRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(bindingLookup("git_stage"),
            "git_stage should be registered");
         assertNotNull(bindingLookup("git_unstage"),
            "git_unstage should be registered");
         assertNotNull(bindingLookup("git_stage_hunk"),
            "git_stage_hunk should be registered");
         assertNotNull(bindingLookup("git_unstage_hunk"),
            "git_unstage_hunk should be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t03_gitNavigationRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(bindingLookup("git_expand"),
            "git_expand should be registered");
         assertNotNull(bindingLookup("git_expand_all"),
            "git_expand_all should be registered");
         assertNotNull(bindingLookup("git_collapse_all"),
            "git_collapse_all should be registered");
         assertNotNull(bindingLookup("git_goto_file"),
            "git_goto_file should be registered");
         assertNotNull(bindingLookup("git_show"),
            "git_show should be registered");
         assertNotNull(bindingLookup("git_log_diff"),
            "git_log_diff should be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t04_gitBranchOpsRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(bindingLookup("git_branch_create"),
            "git_branch_create should be registered");
         assertNotNull(bindingLookup("git_branch_switch"),
            "git_branch_switch should be registered");
         assertNotNull(bindingLookup("git_branch_delete"),
            "git_branch_delete should be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t05_gitRemoteOpsRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(bindingLookup("git_fetch"),
            "git_fetch should be registered");
         assertNotNull(bindingLookup("git_pull"),
            "git_pull should be registered");
         assertNotNull(bindingLookup("git_push"),
            "git_push should be registered");
         assertNotNull(bindingLookup("git_rebase"),
            "git_rebase should be registered");
         assertNotNull(bindingLookup("git_merge"),
            "git_merge should be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t06_gitStashOpsRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(bindingLookup("git_stash"),
            "git_stash should be registered");
         assertNotNull(bindingLookup("git_stash_pop"),
            "git_stash_pop should be registered");
         assertNotNull(bindingLookup("git_stash_list"),
            "git_stash_list should be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t07_gitMiscRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(bindingLookup("git_toggle"),
            "git_toggle should be registered");
         assertNotNull(bindingLookup("git_discard"),
            "git_discard should be registered");
         assertNotNull(bindingLookup("git_refresh"),
            "git_refresh should be registered");
         assertNotNull(bindingLookup("git_do_commit"),
            "git_do_commit should be registered");
         assertNotNull(bindingLookup("git_amend"),
            "git_amend should be registered");
         assertNotNull(bindingLookup("git_patch"),
            "git_patch should be registered");
         assertNotNull(bindingLookup("git"),
            "git should be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Command execution tests ─────────────────────────────────

   @Test
   void t08_gitStatusViaExCommand() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         assertNotNull(fvc, "FvContext must exist");
         // Command.command catches InputException internally;
         // it either opens a status buffer or reports an error message
         Command.command("git_status", fvc, null);
         assertNotNull(fvc.edvec,
            "Editor should still have a buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t09_gitDiffViaExCommand() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_diff", fvc, null);
         // Either opened a diff buffer or reported "No differences"
         assertNotNull(fvc.edvec, "Buffer should exist");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t10_gitBranchViaExCommand() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_branch", fvc, null);
         assertNotNull(fvc.edvec, "Buffer should exist");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t11_gitStageRequiresArgument() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         try {
            gitCmds.doroutine(2, null, 0, 0, fvc, false);
         } catch (InputException e) {
            assertTrue(
               e.getMessage().contains("requires a filename")
               || e.getMessage().contains("Not a git"),
               "Expected 'requires a filename' or 'Not a git'");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t12_gitUnstageRequiresArgument() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         try {
            gitCmds.doroutine(3, null, 0, 0, fvc, false);
         } catch (InputException e) {
            assertTrue(
               e.getMessage().contains("requires a filename")
               || e.getMessage().contains("Not a git"),
               "Expected argument or repo error");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t13_pluginInfoExists() {
      assertNotNull(GitCommands.pluginInfo,
         "pluginInfo should be non-null");
      assertFalse(GitCommands.pluginInfo.isEmpty(),
         "pluginInfo should not be empty");
   }

   @Test
   void t14_getBufferDirReturnsNullForUnknown() {
      File result = GitCommands.getBufferDir("nonexistent_buffer");
      assertNull(result,
         "getBufferDir for unknown name should return null");
   }

   @Test
   void t15_editorStateConsistentAfterGitCommands() throws Exception {
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

   @Test
   void t16_gitLogViaExCommand() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_log", fvc, null);
         assertNotNull(fvc.edvec, "Buffer should exist");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
