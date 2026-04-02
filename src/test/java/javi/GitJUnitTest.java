package javi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javi.git.GitCommands;
import javi.git.GitLogBuffer;
import javi.git.GitLogEntry;
import javi.git.GitLogGraph;
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

   @Nested
   class LogBufferShaExtractionTests {

      @Test
      void extractShaFromSimpleLine() {
         assertEquals("abc1234",
            GitLogBuffer.extractSha("* abc1234 Fix the bug"));
      }

      @Test
      void extractShaFromBranchLine() {
         assertEquals("def5678",
            GitLogBuffer.extractSha(
               "| * def5678 (HEAD -> main) Add feature"));
      }

      @Test
      void extractShaFromMergeLine() {
         assertEquals("9876543",
            GitLogBuffer.extractSha("|/  9876543 Merge branch"));
      }

      @Test
      void extractShaFromIndentedLine() {
         assertEquals("aaa1111",
            GitLogBuffer.extractSha(
               "|  * aaa1111 Some commit message"));
      }

      @Test
      void extractShaFullLength() {
         String fullSha = "abc1234def5678901234567890abcdef12345678";
         assertEquals(fullSha,
            GitLogBuffer.extractSha("* " + fullSha + " msg"));
      }

      @Test
      void extractShaReturnsNullForNoSha() {
         assertNull(GitLogBuffer.extractSha(
            "Git Log (all branches)"));
      }

      @Test
      void extractShaReturnsNullForBlankLine() {
         assertNull(GitLogBuffer.extractSha(""));
      }

      @Test
      void extractShaReturnsNullForNull() {
         assertNull(GitLogBuffer.extractSha(null));
      }

      @Test
      void extractShaFromGraphOnlyLine() {
         assertNull(GitLogBuffer.extractSha("| |"));
      }

      @Test
      void extractShaWithBackslashGraph() {
         assertEquals("bbb2222",
            GitLogBuffer.extractSha(
               "|\\ * bbb2222 Branch point"));
      }
   }

   @Nested
   class LogBufferDecorationTests {

      @Test
      void extractDecorationHeadAndBranch() {
         assertEquals("HEAD -> main",
            GitLogBuffer.extractDecoration(
               "* abc1234 (HEAD -> main) Latest commit"));
      }

      @Test
      void extractDecorationMultipleRefs() {
         assertEquals("HEAD -> main, origin/main, tag: v1.0",
            GitLogBuffer.extractDecoration(
               "* abc1234 (HEAD -> main, origin/main,"
               + " tag: v1.0) Release"));
      }

      @Test
      void extractDecorationNone() {
         assertNull(GitLogBuffer.extractDecoration(
            "* abc1234 Plain commit"));
      }

      @Test
      void extractDecorationNull() {
         assertNull(GitLogBuffer.extractDecoration(null));
      }
   }

   @Nested
   class LogBufferFormatTests {

      @Test
      void formatLogIncludesHeader() {
         List<String> logLines = Arrays.asList(
            "* abc1234 First commit",
            "* def5678 Second commit"
         );
         List<String> result = GitLogBuffer.formatLog(logLines);
         String joined = String.join("\n", result);
         assertTrue(joined.contains("Git Log (all branches)"),
            "Should have log header");
         assertTrue(joined.contains("abc1234"),
            "Should contain first commit SHA");
         assertTrue(joined.contains("def5678"),
            "Should contain second commit SHA");
      }

      @Test
      void formatLogIncludesHelpText() {
         List<String> logLines = Arrays.asList(
            "* abc1234 First commit");
         List<String> result = GitLogBuffer.formatLog(logLines);
         String joined = String.join("\n", result);
         assertTrue(joined.contains(":git_show"),
            "Should include git_show in help text");
         assertTrue(joined.contains(":git_log_diff"),
            "Should include git_log_diff in help text");
      }

      @Test
      void formatLogPreservesGraphStructure() {
         List<String> logLines = Arrays.asList(
            "* abc1234 Latest",
            "| * def5678 Branch work",
            "|/",
            "* 1111111 Common ancestor"
         );
         List<String> result = GitLogBuffer.formatLog(logLines);
         // The graph lines should be preserved in order
         assertTrue(result.contains("* abc1234 Latest"));
         assertTrue(result.contains("| * def5678 Branch work"));
         assertTrue(result.contains("|/"));
         assertTrue(result.contains("* 1111111 Common ancestor"));
      }
   }

   @Nested
   class LogCommandRegistrationTests {

      @BeforeAll
      static void setUp() throws Exception {
         TestInit.init();
         Class.forName("javi.git.GitCommands");
      }

      @Test
      void gitShowRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_show"),
            "git_show should be registered");
      }

      @Test
      void gitLogDiffRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_log_diff"),
            "git_log_diff should be registered");
      }
   }

   @Nested
   class LogEntryParseTests {

      @Test
      void parseSingleCommit() {
         List<String> lines = Arrays.asList(
            "abc1234def5678901234567890abcdef12345678|"
            + "| (HEAD -> main)|Fix bug|Alice|2025-01-15"
         );
         List<GitLogEntry> entries = GitLogEntry.parse(lines);
         assertEquals(1, entries.size());
         GitLogEntry e = entries.get(0);
         assertEquals("abc1234def5678901234567890abcdef12345678",
            e.sha);
         assertEquals("Fix bug", e.subject);
         assertEquals("Alice", e.author);
         assertEquals("HEAD -> main", e.decoration);
         assertTrue(e.parents.isEmpty());
      }

      @Test
      void parseCommitWithParent() {
         List<String> lines = Arrays.asList(
            "aaaa|bbbb| |Add feature|Bob|2025-01-14"
         );
         List<GitLogEntry> entries = GitLogEntry.parse(lines);
         assertEquals(1, entries.size());
         assertEquals(1, entries.get(0).parents.size());
         assertEquals("bbbb", entries.get(0).parents.get(0));
      }

      @Test
      void parseMergeCommit() {
         List<String> lines = Arrays.asList(
            "aaaa|bbbb cccc| |Merge|Charlie|2025-01-13"
         );
         List<GitLogEntry> entries = GitLogEntry.parse(lines);
         assertEquals(1, entries.size());
         assertEquals(2, entries.get(0).parents.size());
         assertEquals("bbbb", entries.get(0).parents.get(0));
         assertEquals("cccc", entries.get(0).parents.get(1));
      }

      @Test
      void parseSkipsEmptyLines() {
         List<String> lines = Arrays.asList("",
            "aaaa|| |Msg|A|2025-01-12", "");
         List<GitLogEntry> entries = GitLogEntry.parse(lines);
         assertEquals(1, entries.size());
      }

      @Test
      void parseNoDecoration() {
         List<String> lines = Arrays.asList(
            "aaaa|bbbb| |Msg|A|2025-01-12"
         );
         List<GitLogEntry> entries = GitLogEntry.parse(lines);
         assertNull(entries.get(0).decoration);
      }

      @Test
      void shortShaReturnsSevenChars() {
         List<String> lines = Arrays.asList(
            "abcdef1234567890|| |Msg|A|2025-01-12"
         );
         List<GitLogEntry> entries = GitLogEntry.parse(lines);
         assertEquals("abcdef1", entries.get(0).shortSha());
      }
   }

   @Nested
   class LogGraphLaneTests {

      @Test
      void singleCommitGetsLaneZero() {
         List<String> lines = Arrays.asList(
            "aaaa|| |Root|A|2025-01-12"
         );
         List<GitLogEntry> entries = GitLogEntry.parse(lines);
         List<GitLogGraph.Row> rows =
            GitLogGraph.assignLanes(entries);
         assertEquals(1, rows.size());
         assertEquals(0, rows.get(0).lane);
      }

      @Test
      void linearHistoryStaysInLaneZero() {
         List<String> lines = Arrays.asList(
            "aaaa|bbbb| |Second|A|2025-01-12",
            "bbbb|| |First|A|2025-01-11"
         );
         List<GitLogEntry> entries = GitLogEntry.parse(lines);
         List<GitLogGraph.Row> rows =
            GitLogGraph.assignLanes(entries);
         assertEquals(2, rows.size());
         assertEquals(0, rows.get(0).lane);
         assertEquals(0, rows.get(1).lane);
      }

      @Test
      void mergeCommitHasTwoParentLanes() {
         List<String> lines = Arrays.asList(
            "aaaa|bbbb cccc| |Merge|A|2025-01-12",
            "bbbb|dddd| |Main work|A|2025-01-11",
            "cccc|dddd| |Branch work|A|2025-01-10",
            "dddd|| |Common ancestor|A|2025-01-09"
         );
         List<GitLogEntry> entries = GitLogEntry.parse(lines);
         List<GitLogGraph.Row> rows =
            GitLogGraph.assignLanes(entries);
         assertEquals(4, rows.size());
         // Merge commit at lane 0
         assertEquals(0, rows.get(0).lane);
         // Should have 2 parent lanes
         assertEquals(2, rows.get(0).parentLanes.length);
         // First parent inherits lane, second gets new lane
         assertEquals(0, rows.get(0).parentLanes[0]);
         assertTrue(rows.get(0).parentLanes[1] > 0,
            "Second parent should be in a different lane");
      }
   }
}
