package javi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javi.git.GitCommands;
import javi.git.GitStatusBuffer;

/**
 * Tests for Git integration plugin: status buffer formatting,
 * command registration, and help content.
 */
class GitJUnitTest {

   @Nested
   class StatusBufferTests {

      @Test
      void formatEmptyStatus() {
         List<String> result = GitStatusBuffer.formatStatus(
            Collections.emptyList());
         assertNotNull(result);
         assertFalse(result.isEmpty());
         assertTrue(result.get(0).contains("Git Status"));
      }

      @Test
      void formatBranchHeader() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678"
         );
         List<String> result = GitStatusBuffer.formatStatus(raw);
         String joined = String.join("\n", result);
         assertTrue(joined.contains("Head: main"),
            "Should show branch name");
         assertTrue(joined.contains("abc1234"),
            "Should show abbreviated oid");
      }

      @Test
      void formatStagedFile() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678",
            "1 M. N... 100644 100644 100644"
               + " aaa bbb src/main/java/Example.java"
         );
         List<String> result = GitStatusBuffer.formatStatus(raw);
         String joined = String.join("\n", result);
         assertTrue(joined.contains("Staged changes"),
            "Should have staged section");
         assertTrue(joined.contains("Example.java"),
            "Should contain the staged filename");
      }

      @Test
      void formatUnstagedFile() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234",
            "1 .M N... 100644 100644 100644"
               + " aaa bbb README.md"
         );
         List<String> result = GitStatusBuffer.formatStatus(raw);
         String joined = String.join("\n", result);
         assertTrue(joined.contains("Unstaged changes"),
            "Should have unstaged section");
         assertTrue(joined.contains("README.md"),
            "Should contain the unstaged filename");
      }

      @Test
      void formatUntrackedFile() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234",
            "? tmp/test.txt"
         );
         List<String> result = GitStatusBuffer.formatStatus(raw);
         String joined = String.join("\n", result);
         assertTrue(joined.contains("Untracked files"),
            "Should have untracked section");
         assertTrue(joined.contains("tmp/test.txt"),
            "Should contain untracked filename");
      }

      @Test
      void formatUpstreamWithAheadBehind() {
         List<String> raw = Arrays.asList(
            "# branch.head feature",
            "# branch.oid abc1234",
            "# branch.upstream origin/feature",
            "# branch.ab +3 -1"
         );
         List<String> result = GitStatusBuffer.formatStatus(raw);
         String joined = String.join("\n", result);
         assertTrue(joined.contains("origin/feature"),
            "Should show upstream");
         assertTrue(joined.contains("ahead 3"),
            "Should show ahead count");
         assertTrue(joined.contains("behind 1"),
            "Should show behind count");
      }

      @Test
      void formatMultipleSections() {
         List<String> raw = Arrays.asList(
            "# branch.head develop",
            "# branch.oid deadbeef",
            "1 M. N... 100644 100644 100644 aaa bbb staged.java",
            "1 .M N... 100644 100644 100644 aaa bbb unstaged.java",
            "? newfile.txt"
         );
         List<String> result = GitStatusBuffer.formatStatus(raw);
         String joined = String.join("\n", result);
         assertTrue(joined.contains("Staged changes"));
         assertTrue(joined.contains("Unstaged changes"));
         assertTrue(joined.contains("Untracked files"));
         assertTrue(joined.contains("staged.java"));
         assertTrue(joined.contains("unstaged.java"));
         assertTrue(joined.contains("newfile.txt"));
      }
   }

   @Nested
   class PluginTests {

      @Test
      void implementsPluginInterface() {
         assertTrue(Plugin.class.isAssignableFrom(GitCommands.class),
            "GitCommands should implement Plugin");
      }

      @Test
      void hasPluginInfo() throws Exception {
         java.lang.reflect.Field f =
            GitCommands.class.getDeclaredField("pluginInfo");
         assertNotNull(f, "Should have pluginInfo field");
         String info = (String) f.get(null);
         assertNotNull(info, "pluginInfo should not be null");
         assertFalse(info.isEmpty(), "pluginInfo should not be empty");
      }
   }

   @Nested
   class CommandRegistrationTests {

      @BeforeAll
      static void setUp() throws Exception {
         TestInit.init();
         Class.forName("javi.git.GitCommands");
      }

      @Test
      void gitStatusRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_status"),
            "git_status should be registered");
      }

      @Test
      void gitStageRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_stage"),
            "git_stage should be registered");
      }

      @Test
      void gitUnstageRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_unstage"),
            "git_unstage should be registered");
      }

      @Test
      void gitCommitRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_commit"),
            "git_commit should be registered");
      }

      @Test
      void gitDiffRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_diff"),
            "git_diff should be registered");
      }

      @Test
      void gitLogRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_log"),
            "git_log should be registered");
      }

      @Test
      void gitBranchRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_branch"),
            "git_branch should be registered");
      }

      @Test
      void gitDoCommitRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_do_commit"),
            "git_do_commit should be registered");
      }

      @Test
      void gitPushRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_push"),
            "git_push should be registered");
      }

      @Test
      void gitPullRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_pull"),
            "git_pull should be registered");
      }

      @Test
      void gitFetchRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_fetch"),
            "git_fetch should be registered");
      }

      @Test
      void gitStashRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_stash"),
            "git_stash should be registered");
      }

      @Test
      void gitBranchDeleteRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_branch_delete"),
            "git_branch_delete should be registered");
      }

      @Test
      void gitRebaseRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_rebase"),
            "git_rebase should be registered");
      }
   }

   @Nested
   class HelpTests {

      @BeforeAll
      static void setUp() throws Exception {
         TestInit.init();
      }

      @Test
      void gitHelpTopicExists() {
         EventQueue.biglock2.lock();
         try {
            TextEdit help = HelpSystem.getHelp("git");
            assertNotNull(help, "git help topic should exist");
            int lines = help.readIn();
            assertTrue(lines > 5,
               "git help should have substantial content");
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < lines; i++) {
               sb.append(help.at(i).toString()).append('\n');
            }
            String content = sb.toString();
            assertTrue(content.contains("GIT INTEGRATION"),
               "Should have git integration header");
            assertTrue(content.contains(":git_status"),
               "Should document git_status");
            assertTrue(content.contains(":git_commit"),
               "Should document git_commit");
            assertTrue(content.contains(":git_push"),
               "Should document git_push");
            assertTrue(content.contains(":git_rebase"),
               "Should document git_rebase");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      void gitHelpVcsAlias() {
         EventQueue.biglock2.lock();
         try {
            TextEdit help = HelpSystem.getHelp("vcs");
            assertNotNull(help, "vcs alias should work for git help");
            StringBuilder sb = new StringBuilder();
            int lines = help.readIn();
            for (int i = 1; i < lines; i++) {
               sb.append(help.at(i).toString()).append('\n');
            }
            assertTrue(sb.toString().contains("GIT INTEGRATION"),
               "vcs should show git help");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      void indexIncludesGit() {
         EventQueue.biglock2.lock();
         try {
            TextEdit help = HelpSystem.getHelp("index");
            assertNotNull(help);
            StringBuilder sb = new StringBuilder();
            int lines = help.readIn();
            for (int i = 1; i < lines; i++) {
               sb.append(help.at(i).toString()).append('\n');
            }
            assertTrue(sb.toString().contains(":help git"),
               "Help index should include git topic");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }

   @Nested
   class RebaseTests {

      @BeforeAll
      static void setUp() throws Exception {
         TestInit.init();
      }

      @Test
      void rebaseHelpDocumentsAllForms() {
         EventQueue.biglock2.lock();
         try {
            TextEdit help = HelpSystem.getHelp("git");
            assertNotNull(help);
            StringBuilder sb = new StringBuilder();
            int lines = help.readIn();
            for (int i = 1; i < lines; i++) {
               sb.append(help.at(i).toString()).append('\n');
            }
            String content = sb.toString();
            assertTrue(content.contains(":git_rebase <branch>"),
               "Help should document rebase with branch arg");
            assertTrue(content.contains(":git_rebase --continue"),
               "Help should document rebase --continue");
            assertTrue(content.contains(":git_rebase --abort"),
               "Help should document rebase --abort");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      void rebaseInBranchOperationsSection() {
         EventQueue.biglock2.lock();
         try {
            TextEdit help = HelpSystem.getHelp("git");
            assertNotNull(help);
            StringBuilder sb = new StringBuilder();
            int lines = help.readIn();
            for (int i = 1; i < lines; i++) {
               sb.append(help.at(i).toString()).append('\n');
            }
            String content = sb.toString();
            int branchIdx = content.indexOf("BRANCH OPERATIONS");
            int rebaseIdx = content.indexOf(":git_rebase");
            int remoteIdx = content.indexOf("REMOTE OPERATIONS");
            assertTrue(branchIdx >= 0,
               "Should have branch operations section");
            assertTrue(rebaseIdx > branchIdx,
               "Rebase should be after branch operations header");
            assertTrue(rebaseIdx < remoteIdx,
               "Rebase should be before remote operations");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }

   @Nested
   class DispatcherTests {

      @BeforeAll
      static void setUp() throws Exception {
         TestInit.init();
         Class.forName("javi.git.GitCommands");
      }

      @Test
      void gitDispatcherRegistered() {
         assertNotNull(Rgroup.bindingLookup("git"),
            "git dispatcher should be registered");
      }
   }

   @Nested
   class LoadClassTests {

      @BeforeAll
      static void setUp() throws Exception {
         TestInit.init();
      }

      @Test
      void loadclassCommandRegistered() {
         assertNotNull(Rgroup.bindingLookup("loadclass"),
            "loadclass command should be registered");
      }
   }
}
