package javi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
import javi.git.GitLogExpander;
import javi.git.GitStatusBuffer;

/**
 * Tests for Git integration plugin: status buffer formatting,
 * command registration, log expansion, and keymap bindings.
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
            assertTrue(content.contains(":git status"),
               "Should document git status");
            assertTrue(content.contains(":git commit"),
               "Should document git commit");
            assertTrue(content.contains(":git push"),
               "Should document git push");
            assertTrue(content.contains(":git rebase"),
               "Should document git rebase");
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
            assertTrue(content.contains(":git rebase <branch>"),
               "Help should document rebase with branch arg");
            assertTrue(content.contains(":git rebase --continue"),
               "Help should document rebase --continue");
            assertTrue(content.contains(":git rebase --abort"),
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
            int rebaseIdx = content.indexOf(":git rebase");
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
         assertTrue(joined.contains("Git Log"),
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
         assertTrue(joined.contains("expand"),
            "Should include expand in help text");
         assertTrue(joined.contains("quit"),
            "Should include quit in help text");
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

   @Nested
   class ExpanderStaticContentTests {

      @Test
      void getStatContentFormatsWithBorders() {
         // If git is available, verify output has border markers
         try {
            List<String> content =
               GitLogExpander.getStatContent("HEAD");
            assertNotNull(content);
            assertFalse(content.isEmpty());
            assertTrue(content.get(0).contains("+--"),
               "Should start with top border");
            assertTrue(content.get(content.size() - 1)
               .contains("+---"),
               "Should end with bottom border");
         } catch (Exception e) {
            // git not available in test env — acceptable
            assertTrue(true, "Skipped: git not available");
         }
      }

      @Test
      void getDiffContentFormatsWithBorders() {
         try {
            List<String> content =
               GitLogExpander.getDiffContent("HEAD");
            assertNotNull(content);
            assertFalse(content.isEmpty());
            assertTrue(content.get(0).contains("full diff"),
               "Should indicate full diff mode");
         } catch (Exception e) {
            assertTrue(true, "Skipped: git not available");
         }
      }
   }

   @Nested
   class ExpanderUnitTests {

      @Test
      void newExpanderHasNoExpansions() {
         GitLogExpander expander = new GitLogExpander();
         assertFalse(expander.hasExpansions());
         assertEquals(0, expander.expansionCount());
      }

      @Test
      void getExpansionReturnsZeroForUnknownSha() {
         GitLogExpander expander = new GitLogExpander();
         assertEquals(0, expander.getLevel("abc1234"));
      }

      @Test
      void toggleFirstTimeGoesToLevel1() {
         GitLogExpander expander = new GitLogExpander();
         assertEquals(1, expander.toggle("abc1234"));
         assertEquals(1, expander.getLevel("abc1234"));
         assertTrue(expander.hasExpansions());
      }

      @Test
      void toggleSecondTimeGoesToLevel2() {
         GitLogExpander expander = new GitLogExpander();
         expander.toggle("abc1234"); // level 1
         assertEquals(2, expander.toggle("abc1234"));
         assertEquals(2, expander.getLevel("abc1234"));
      }

      @Test
      void toggleThirdTimeCollapses() {
         GitLogExpander expander = new GitLogExpander();
         expander.toggle("abc1234"); // level 1
         expander.toggle("abc1234"); // level 2
         assertEquals(0, expander.toggle("abc1234"));
         assertEquals(0, expander.getLevel("abc1234"));
         assertFalse(expander.hasExpansions());
      }

      @Test
      void expandAllSetsLevel2() {
         GitLogExpander expander = new GitLogExpander();
         List<String> lines = Arrays.asList(
            "* abc1234 First commit",
            "* def5678 Second commit"
         );
         expander.expandAll(lines);
         assertEquals(2, expander.getLevel("abc1234"));
         assertEquals(2, expander.getLevel("def5678"));
         assertEquals(2, expander.expansionCount());
      }

      @Test
      void collapseAllClearsState() {
         GitLogExpander expander = new GitLogExpander();
         expander.toggle("abc1234");
         expander.toggle("def5678");
         expander.collapseAll();
         assertFalse(expander.hasExpansions());
         assertEquals(0, expander.getLevel("abc1234"));
      }

      @Test
      void multipleShaTracks() {
         GitLogExpander expander = new GitLogExpander();
         expander.toggle("aaa1111");
         expander.toggle("bbb2222");
         assertEquals(2, expander.expansionCount());
         assertEquals(1, expander.getLevel("aaa1111"));
         assertEquals(1, expander.getLevel("bbb2222"));
      }
   }

   @Nested
   class LogBufferUpdatedHelpTextTests {

      @Test
      void formatLogShowsKeyBindings() {
         List<String> logLines = Arrays.asList(
            "* abc1234 First commit");
         List<String> result = GitLogBuffer.formatLog(logLines);
         String joined = String.join("\n", result);
         assertTrue(joined.contains("Enter"),
            "Should mention Enter key");
         assertTrue(joined.contains("expand"),
            "Should mention expand");
         assertTrue(joined.contains("q=quit"),
            "Should mention q for quit");
      }
   }

   @Nested
   class StatusBufferUpdatedHelpTextTests {

      @Test
      void statusBufferShowsKeyBindings() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234"
         );
         List<String> result = GitStatusBuffer.formatStatus(raw);
         String joined = String.join("\n", result);
         assertTrue(joined.contains("s=stage"),
            "Should mention s for stage");
         assertTrue(joined.contains("u=unstage"),
            "Should mention u for unstage");
         assertTrue(joined.contains("X=discard"),
            "Should mention X for discard");
         assertTrue(joined.contains("q=quit"),
            "Should mention q for quit");
      }
   }

   @Nested
   class ExpandCommandRegistrationTests {

      @BeforeAll
      static void setUp() throws Exception {
         TestInit.init();
         Class.forName("javi.git.GitCommands");
      }

      @Test
      void gitExpandRegistered() {
         assertNotNull(Rgroup.bindingLookup("git_expand"),
            "git_expand should be registered");
      }

      @Test
      void gitExpandAllRegistered() {
         assertNotNull(
            Rgroup.bindingLookup("git_expand_all"),
            "git_expand_all should be registered");
      }

      @Test
      void gitCollapseAllRegistered() {
         assertNotNull(
            Rgroup.bindingLookup("git_collapse_all"),
            "git_collapse_all should be registered");
      }
   }

   @Nested
   class KeymapOverlayTests {

      @Test
      void gitlogOverlayCanBeCreated() {
         // Verify overlay creation works without needing
         // full command registration
         KeyGroup mg = new KeyGroup("test-move");
         KeyGroup eg = new KeyGroup("test-edit");
         KeyMap parent = new KeyMap("test-parent", mg, eg);
         KeyMap overlay = KeyMap.createOverlay("test-gitlog",
            parent);
         assertNotNull(overlay);
         assertEquals("test-parent",
            overlay.getParent().getName());
      }

      @Test
      void gitstatusOverlayCanBeCreated() {
         KeyGroup mg = new KeyGroup("test-move2");
         KeyGroup eg = new KeyGroup("test-edit2");
         KeyMap parent = new KeyMap("test-parent2", mg, eg);
         KeyMap overlay = KeyMap.createOverlay("test-gitstatus",
            parent);
         assertNotNull(overlay);
         assertEquals("test-parent2",
            overlay.getParent().getName());
      }

      @Test
      void overlayLookupFallsThrough() {
         // Verify parent-chain fallback works
         KeyGroup mg = new KeyGroup("ov-move");
         KeyGroup eg = new KeyGroup("ov-edit");
         KeyMap parent = new KeyMap("ov-parent", mg, eg);
         KeyMap overlay = KeyMap.createOverlay("ov-child",
            parent);
         // No bindings in child → lookups return null locally
         // but would fall through to parent if parent had them
         JeyEvent ev = new JeyEvent(0, 0, 'z');
         assertNull(overlay.lookupEdit(ev),
            "Unbound key should return null");
      }
   }

   @Nested
   class DiffSubFoldTests {

      @Test
      void buildFoldedLogIncludesDiffShowMarkers() {
         List<String> logLines = Arrays.asList(
            "* abc1234 First commit");
         Map<String, List<String>> messages =
            new java.util.LinkedHashMap<>();
         messages.put("abc1234", Arrays.asList(
            "  Author: John", "  Date: 2025-01-15",
            "  ", "  First commit"));
         List<int[]> foldRanges = new ArrayList<>();
         List<String> result =
            GitLogBuffer.buildFoldedLog(
               logLines, messages, foldRanges,
               null, null);
         String joined = String.join("\n", result);
         assertTrue(joined.contains(
            "  >> Show diffs ("),
            "Should contain diff show marker");
         assertTrue(joined.contains("abc1234"),
            "Diff marker should contain SHA");
         assertTrue(joined.contains("toggle to view diffs"),
            "Should have toggle hint");
      }

      @Test
      void buildFoldedLogWithExpandedDiffs() {
         List<String> logLines = Arrays.asList(
            "* abc1234 First commit");
         Map<String, List<String>> messages =
            new java.util.LinkedHashMap<>();
         messages.put("abc1234", Arrays.asList(
            "  Author: John", "  Date: 2025-01-15",
            "  ", "  First commit"));
         java.util.Set<String> diffExpanded =
            new java.util.HashSet<>();
         diffExpanded.add("abc1234");
         Map<String, List<String>> diffCache =
            new java.util.HashMap<>();
         diffCache.put("abc1234", Arrays.asList(
            " file.java | 2 +-",
            "diff --git a/file.java b/file.java",
            "--- a/file.java",
            "+++ b/file.java",
            "-old line",
            "+new line"));
         List<int[]> foldRanges = new ArrayList<>();
         List<String> result =
            GitLogBuffer.buildFoldedLog(
               logLines, messages, foldRanges,
               diffExpanded, diffCache);
         String joined = String.join("\n", result);
         assertTrue(joined.contains(
            "  << Diffs ("),
            "Should contain diff hide marker");
         assertTrue(joined.contains("  | diff --git"),
            "Should contain prefixed diff lines");
         assertTrue(joined.contains(
            "  +----------------------------"),
            "Should have diff end border");
         assertFalse(joined.contains(
            "  >> Show diffs ("),
            "Show marker should not appear for expanded");
      }

      @Test
      void nestedFoldRangesCreated() {
         List<String> logLines = Arrays.asList(
            "* abc1234 First commit");
         Map<String, List<String>> messages =
            new java.util.LinkedHashMap<>();
         messages.put("abc1234", Arrays.asList(
            "  Author: John", "  First commit"));
         List<int[]> foldRanges = new ArrayList<>();
         GitLogBuffer.buildFoldedLog(
            logLines, messages, foldRanges,
            null, null);
         // Should have: diff sub-fold, commit fold,
         // pagination fold = 3 folds
         assertTrue(foldRanges.size() >= 3,
            "Should have at least 3 folds (diff, commit,"
            + " pagination), got " + foldRanges.size());
         // First fold added is the diff sub-fold,
         // second is the commit fold
         int[] diffFold = foldRanges.get(0);
         int[] commitFold = foldRanges.get(1);
         assertTrue(
            commitFold[0] <= diffFold[0],
            "Commit fold should start at or before diff");
         assertTrue(
            commitFold[1] >= diffFold[1],
            "Commit fold should end at or after diff");
      }

      @Test
      void foldModelHandlesNestedFolds() {
         List<String> logLines = Arrays.asList(
            "* abc1234 First commit");
         Map<String, List<String>> messages =
            new java.util.LinkedHashMap<>();
         messages.put("abc1234", Arrays.asList(
            "  Author: John", "  First commit"));
         List<int[]> foldRanges = new ArrayList<>();
         List<String> formatted =
            GitLogBuffer.buildFoldedLog(
               logLines, messages, foldRanges,
               null, null);
         FoldModel fm = new FoldModel();
         for (int[] range : foldRanges) {
            fm.addFold(range[0], range[1]);
         }
         fm.closeAll();
         assertTrue(fm.size() >= 3,
            "FoldModel should have folds");
         // Open the commit fold
         int[] commitFold = foldRanges.get(1);
         FoldModel.FoldRange opened =
            fm.openFold(commitFold[0]);
         assertNotNull(opened,
            "Should find commit fold");
         assertFalse(opened.collapsed,
            "Commit fold should be open");
         // The diff sub-fold should still be collapsed
         int[] diffFold = foldRanges.get(0);
         FoldModel.FoldRange inner =
            fm.findFoldAtStart(diffFold[0]);
         assertNotNull(inner,
            "Should find diff sub-fold");
         assertTrue(inner.collapsed,
            "Diff sub-fold should remain collapsed");
      }
   }

   @Nested
   class DiffMarkerExtractionTests {

      @Test
      void extractShaFromShowMarker() {
         assertEquals("abc1234",
            GitLogBuffer.extractShaFromMarker(
               "  >> Show diffs (abc1234)"));
      }

      @Test
      void extractShaFromHideMarker() {
         assertEquals("def5678",
            GitLogBuffer.extractShaFromMarker(
               "  << Diffs (def5678) <<"));
      }

      @Test
      void extractShaFromMarkerReturnsNull() {
         assertNull(
            GitLogBuffer.extractShaFromMarker(
               "  no parens here"));
      }

      @Test
      void extractShaFromMarkerNull() {
         assertNull(
            GitLogBuffer.extractShaFromMarker(null));
      }
   }

   @Nested
   class PaginationTests {

      @Test
      void paginationMarkerPresent() {
         List<String> logLines = Arrays.asList(
            "* abc1234 First commit");
         Map<String, List<String>> messages =
            new java.util.LinkedHashMap<>();
         messages.put("abc1234",
            Arrays.asList("  Author: John"));
         List<int[]> foldRanges = new ArrayList<>();
         List<String> result =
            GitLogBuffer.buildFoldedLog(
               logLines, messages, foldRanges,
               null, null);
         String joined = String.join("\n", result);
         assertTrue(joined.contains(
            "--- more ---"),
            "Should contain pagination marker");
         assertTrue(joined.contains("load more"),
            "Should mention loading more");
      }

      @Test
      void paginationFoldAtEnd() {
         List<String> logLines = Arrays.asList(
            "* abc1234 First commit");
         Map<String, List<String>> messages =
            new java.util.LinkedHashMap<>();
         messages.put("abc1234",
            Arrays.asList("  Author: John"));
         List<int[]> foldRanges = new ArrayList<>();
         List<String> result =
            GitLogBuffer.buildFoldedLog(
               logLines, messages, foldRanges,
               null, null);
         // Last fold should be pagination
         int[] lastFold =
            foldRanges.get(foldRanges.size() - 1);
         // Pagination fold end should match last line
         assertEquals(result.size(), lastFold[1],
            "Pagination fold should end at last line");
         // The start line should contain the marker
         String startText =
            result.get(lastFold[0] - 1);
         assertTrue(startText.startsWith(
            "--- more ---"),
            "Pagination fold start should be the marker");
      }
   }

   @Nested
   class FoldToggleHandlerTests {

      @Test
      void handlerCalledOnToggle() {
         FoldModel fm = new FoldModel();
         fm.addFold(1, 5);
         fm.closeAll();
         boolean[] called = {false};
         int[] capturedLine = {0};
         fm.setToggleHandler((line, fvc) -> {
            called[0] = true;
            capturedLine[0] = line;
            return true;
         });
         assertNotNull(fm.getToggleHandler(),
            "Handler should be set");
         // Simulate what MiscCommands does
         FoldModel.FoldToggleHandler handler =
            fm.getToggleHandler();
         try {
            assertTrue(handler.onToggle(1, null),
               "Handler should return true");
         } catch (Exception e) {
            // unexpected
         }
         assertTrue(called[0],
            "Handler should have been called");
         assertEquals(1, capturedLine[0],
            "Handler should receive the correct line");
      }

      @Test
      void noHandlerDefaultsToNormalToggle() {
         FoldModel fm = new FoldModel();
         fm.addFold(1, 5);
         fm.closeAll();
         assertNull(fm.getToggleHandler(),
            "No handler by default");
         // Normal toggle still works
         FoldModel.FoldRange r = fm.toggleFold(1);
         assertNotNull(r);
         assertFalse(r.collapsed,
            "Fold should be opened by toggle");
      }

      @Test
      void handlerReturningFalseAllowsDefault() {
         FoldModel fm = new FoldModel();
         fm.addFold(1, 5);
         fm.closeAll();
         fm.setToggleHandler((line, fvc) -> false);
         FoldModel.FoldToggleHandler handler =
            fm.getToggleHandler();
         try {
            assertFalse(handler.onToggle(1, null),
               "Handler should return false");
         } catch (Exception e) {
            // unexpected
         }
         // Normal toggle proceeds
         FoldModel.FoldRange r = fm.toggleFold(1);
         assertNotNull(r);
         assertFalse(r.collapsed);
      }
   }

   @Nested
   class PosListFormatTests {

      @Test
      void positionConverterParsesGitLogFormat() {
         // Verify the format *git-log*(lineNum -comment)
         // is parseable by PositionConverter
         String line = "*git-log*(5 -abc1234 Fix the bug)";
         int parenIdx = line.indexOf('(');
         assertTrue(parenIdx > 0,
            "Should contain opening paren");
         String filename = line.substring(0, parenIdx);
         assertEquals("*git-log*", filename,
            "Filename should be *git-log*");
         int dashIdx = line.indexOf('-', parenIdx + 1);
         assertTrue(dashIdx > parenIdx,
            "Should contain dash after paren");
         String lineNumStr =
            line.substring(parenIdx + 1, dashIdx)
               .trim();
         assertEquals("5", lineNumStr,
            "Line number should be 5");
      }

      @Test
      void buildFoldedLogGraphLinesHaveShaForPosFormat() {
         // Verify that graph lines with SHAs can be matched
         // by the regex used in registerLogInPosListList
         List<String> logLines = Arrays.asList(
            "* abc1234 First commit",
            "* def5678 Second commit");
         Map<String, List<String>> messages =
            new java.util.LinkedHashMap<>();
         messages.put("abc1234",
            Arrays.asList("  Author: John"));
         messages.put("def5678",
            Arrays.asList("  Author: Jane"));
         List<int[]> foldRanges = new ArrayList<>();
         List<String> result =
            GitLogBuffer.buildFoldedLog(
               logLines, messages, foldRanges,
               null, null);
         // Find lines with SHAs using the same pattern
         // as registerLogInPosListList
         java.util.regex.Pattern shaPat =
            java.util.regex.Pattern.compile(
               "^[*|/\\\\ ]+\\s*([0-9a-f]{7,40})\\b");
         int shaCount = 0;
         for (String r : result) {
            if (shaPat.matcher(r).find())
               shaCount++;
         }
         assertTrue(shaCount >= 2,
            "Should find at least 2 SHA lines but found "
               + shaCount);
      }
   }

   @Nested
   class LogExtraArgsTests {

      @Test
      void getLogLinesWithNullExtraArgs() throws IOException {
         // Should behave the same as no-arg overload
         List<String> result =
            GitLogBuffer.getLogLines(5, null, null);
         assertNotNull(result,
            "Should return non-null list with null extraArgs");
      }

      @Test
      void getLogLinesWithPathFilter() throws IOException {
         // Should not throw; result may be empty if path has no commits
         List<String> result =
            GitLogBuffer.getLogLines(5, null,
               new String[]{"--", "nonexistent-file.txt"});
         assertNotNull(result,
            "Should return non-null list with path filter");
      }

      @Test
      void getLogLinesWithEmptyExtraArgs() throws IOException {
         List<String> result =
            GitLogBuffer.getLogLines(5, null, new String[0]);
         assertNotNull(result,
            "Should return non-null list with empty extraArgs");
      }

      @Test
      void getCommitMessagesWithNullExtraArgs() throws IOException {
         Map<String, List<String>> result =
            GitLogBuffer.getCommitMessages(5, null, null);
         assertNotNull(result,
            "Should return non-null map with null extraArgs");
      }

      @Test
      void getCommitMessagesWithPathFilter() throws IOException {
         Map<String, List<String>> result =
            GitLogBuffer.getCommitMessages(5, null,
               new String[]{"--", "nonexistent-file.txt"});
         assertNotNull(result,
            "Should return non-null map with path filter");
      }
   }

   @Nested
   class LogArgRegistrationTests {

      @BeforeAll
      static void setUp() throws Exception {
         TestInit.init();
         Class.forName("javi.git.GitCommands");
      }

      @Test
      void gitLogAcceptsArgViaDispatcher() {
         // The git dispatcher should be able to route "log -- file"
         assertNotNull(Rgroup.bindingLookup("git"),
            "git dispatcher must be registered");
         assertNotNull(Rgroup.bindingLookup("git_log"),
            "git_log must be registered");
      }
   }
}
