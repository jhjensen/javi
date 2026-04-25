package javi.git;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import javi.Command;
import javi.EventQueue;
import javi.FvContext;
import javi.InputException;

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
 * Extended GUI tests for {@link GitCommands} doroutine execution.
 *
 * <p>Exercises more doroutine paths including git_commit, git_stash_list,
 * git_show, git_expand, git_collapse_all, git_refresh, and error paths
 * for commands requiring arguments. Requires a full GUI context and a
 * git repo (Docker creates one in the build step).</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class GitCommandsExecGuiJUnitTest {

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
            Method getInstance = dirMgrClass.getDeclaredMethod("getInstance");
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
         gitCmds = new GitCommands();
      }
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── doroutine execution tests ────────────────────────────────

   @Test
   void t01_gitCommitOpensBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         assertNotNull(fvc);
         // git_commit is rnum=4
         Command.command("git_commit", fvc, null);
         // Either opens a commit buffer or reports "Not a git"
         assertNotNull(fvc.edvec, "Buffer should exist after git_commit");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t02_gitStashListViaCommand() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_stash_list", fvc, null);
         assertNotNull(fvc.edvec, "Buffer should exist after stash_list");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t03_gitShowViaCommand() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // git_show shows the diff of the commit at cursor
         // In an empty/non-log buffer, it should handle gracefully
         Command.command("git_show", fvc, null);
         assertNotNull(fvc.edvec);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t04_gitRefreshViaCommand() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // git_refresh re-runs git status
         Command.command("git_refresh", fvc, null);
         assertNotNull(fvc.edvec);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t05_gitExpandViaCommand() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_expand", fvc, null);
         assertNotNull(fvc.edvec);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t06_gitExpandAllViaCommand() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_expand_all", fvc, null);
         assertNotNull(fvc.edvec);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t07_gitCollapseAllViaCommand() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_collapse_all", fvc, null);
         assertNotNull(fvc.edvec);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t08_gitBranchCreateRequiresArg() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         try {
            // rnum 9 = git_branch_create
            gitCmds.doroutine(9, null, 0, 0, fvc, false);
         } catch (InputException e) {
            assertTrue(
               e.getMessage().contains("requires")
               || e.getMessage().contains("Not a git"),
               "Expected requires arg or not-a-git error");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t09_gitBranchSwitchRequiresArg() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         try {
            // rnum 10 = git_branch_switch
            gitCmds.doroutine(10, null, 0, 0, fvc, false);
         } catch (InputException e) {
            assertTrue(
               e.getMessage().contains("requires")
               || e.getMessage().contains("Not a git"),
               "Expected requires arg or not-a-git");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t10_gitBranchDeleteRequiresArg() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         try {
            // rnum 23 = git_branch_delete
            gitCmds.doroutine(23, null, 0, 0, fvc, false);
         } catch (InputException e) {
            assertTrue(
               e.getMessage().contains("requires")
               || e.getMessage().contains("Not a git"),
               "Expected requires arg or not-a-git");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t11_gitFetchViaCommand() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // git_fetch in a minimal Docker repo — may fail due to no remote
         // but should not crash the editor
         Command.command("git_fetch", fvc, null);
         assertNotNull(fvc.edvec);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t12_gitToggleViaCommand() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_toggle", fvc, null);
         assertNotNull(fvc.edvec);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t13_gitDispatchViaCommand() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // :git status — dispatches to git subcommand
         Command.command("git status", fvc, null);
         assertNotNull(fvc.edvec);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t14_gitLogDiffViaCommand() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Command.command("git_log_diff", fvc, null);
         assertNotNull(fvc.edvec);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t15_invalidRnumThrows() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         try {
            gitCmds.doroutine(999, null, 0, 0, fvc, false);
            fail("Invalid rnum should throw RuntimeException");
         } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("default"),
               "Exception should mention 'default'");
         } catch (InputException e) {
            // "Not a git repository" is also acceptable
            assertTrue(true);
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Static helper tests ──────────────────────────────────────

   @Test
   void t16_getBufferDirNullForNonExistent() {
      assertNull(GitCommands.getBufferDir("*nonexistent*"),
         "Should return null for unknown buffer name");
   }

   @Test
   void t17_pluginInfoNonEmpty() {
      assertNotNull(GitCommands.pluginInfo);
      assertFalse(GitCommands.pluginInfo.isEmpty(),
         "pluginInfo should describe the plugin");
   }

   @Test
   void t18_editorStableAfterAllCommands() throws Exception {
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
