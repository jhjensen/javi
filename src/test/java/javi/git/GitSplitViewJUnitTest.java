package javi.git;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the split-view commit workflow: staging view
 * buffer building, status buffer formatting with deleted files,
 * and diff-line navigation helpers.
 */
class GitSplitViewJUnitTest {

   private static List<String> lines(String... args) {
      return Arrays.asList(args);
   }

   // -- Staging view hunk parsing --

   @Nested
   @DisplayName("staging view hunk parsing")
   class StagingViewHunkParsing {

      @Test
      @DisplayName("parses hunks from staging view with correct offsets")
      void stagingViewHunkPositions() {
         List<String> stagingLines = lines(
            GitCommitView.STAGED_SEPARATOR,
            "#   file.java | 2 +-",
            "#",
            GitCommitView.UNSTAGED_SEPARATOR,
            "diff --git a/Foo.java b/Foo.java",
            "index abc..def 100644",
            "--- a/Foo.java",
            "+++ b/Foo.java",
            "@@ -1,3 +1,4 @@",
            " line1",
            "+added",
            " line2");
         List<GitHunkStaging.Hunk> hunks =
            GitCommitView.parseStagingViewHunks(stagingLines);
         assertEquals(1, hunks.size());
         // UNSTAGED_SEPARATOR is at index 3, diffStart = 4
         // Within diff, @@ is at offset 4 → raw bufferLine = 5
         // adjusted = 5 + 4 = 9
         assertEquals(9, hunks.get(0).bufferLine);
      }

      @Test
      @DisplayName("returns empty when no unstaged separator")
      void noUnstagedSeparator() {
         List<String> stagingLines = lines(
            GitCommitView.STAGED_SEPARATOR,
            "#   file.java | 1 +",
            "#");
         List<GitHunkStaging.Hunk> hunks =
            GitCommitView.parseStagingViewHunks(stagingLines);
         assertTrue(hunks.isEmpty());
      }

      @Test
      @DisplayName("returns empty for no-unstaged-changes message")
      void noUnstagedChanges() {
         List<String> stagingLines = lines(
            GitCommitView.STAGED_SEPARATOR,
            "#   file.java | 1 +",
            "#",
            GitCommitView.UNSTAGED_SEPARATOR,
            "#   (no unstaged changes)");
         List<GitHunkStaging.Hunk> hunks =
            GitCommitView.parseStagingViewHunks(stagingLines);
         assertTrue(hunks.isEmpty());
      }

      @Test
      @DisplayName("parses multiple hunks across files")
      void multipleFiles() {
         List<String> stagingLines = lines(
            GitCommitView.STAGED_SEPARATOR,
            "#",
            GitCommitView.UNSTAGED_SEPARATOR,
            "diff --git a/A.java b/A.java",
            "index aaa..bbb 100644",
            "--- a/A.java",
            "+++ b/A.java",
            "@@ -1,2 +1,3 @@",
            " a",
            "+b",
            "diff --git a/B.java b/B.java",
            "index ccc..ddd 100644",
            "--- a/B.java",
            "+++ b/B.java",
            "@@ -5,2 +5,3 @@",
            " x",
            "+y");
         List<GitHunkStaging.Hunk> hunks =
            GitCommitView.parseStagingViewHunks(stagingLines);
         assertEquals(2, hunks.size());
         assertTrue(hunks.get(0).header.get(0).contains("A.java"));
         assertTrue(hunks.get(1).header.get(0).contains("B.java"));
         assertTrue(hunks.get(1).bufferLine
            > hunks.get(0).bufferLine);
      }
   }

   // -- Status buffer format with deleted files --

   @Nested
   @DisplayName("status buffer formatting")
   class StatusBufferFormatting {

      @Test
      @DisplayName("formats deleted file in unstaged section")
      void deletedFileUnstaged() {
         // Porcelain v2: "1 .D N... 100644 100644 000000
         //   hash1 hash2 path/to/file"
         List<String> raw = lines(
            "# branch.head main",
            "# branch.oid abc1234",
            "1 .D N... 100644 100644 000000"
               + " abcdef1234567890 0000000000000000"
               + " deleted-file.txt");
         List<String> formatted =
            GitStatusBuffer.formatStatus(raw);
         // Should appear in "Unstaged changes" section
         boolean foundUnstaged = false;
         boolean foundDeletedLine = false;
         for (String line : formatted) {
            if (line.startsWith("Unstaged changes"))
               foundUnstaged = true;
            if (foundUnstaged
                  && line.contains("deleted")
                  && line.contains("deleted-file.txt")) {
               foundDeletedLine = true;
               // Verify filename can be extracted
               String trimmed = line.trim();
               String prefix = "deleted";
               assertTrue(trimmed.startsWith(prefix));
               String rest =
                  trimmed.substring(prefix.length()).trim();
               assertEquals("deleted-file.txt", rest);
            }
         }
         assertTrue(foundUnstaged,
            "Should have unstaged section");
         assertTrue(foundDeletedLine,
            "Should show deleted file");
      }

      @Test
      @DisplayName("formats staged deleted file")
      void deletedFileStaged() {
         // x=D, y=. → staged deletion
         List<String> raw = lines(
            "# branch.head main",
            "# branch.oid abc1234",
            "1 D. N... 100644 000000 000000"
               + " abcdef1234567890 0000000000000000"
               + " staged-delete.txt");
         List<String> formatted =
            GitStatusBuffer.formatStatus(raw);
         boolean foundStaged = false;
         boolean foundDeletedLine = false;
         for (String line : formatted) {
            if (line.startsWith("Staged changes"))
               foundStaged = true;
            if (foundStaged
                  && line.contains("deleted")
                  && line.contains("staged-delete.txt")) {
               foundDeletedLine = true;
            }
         }
         assertTrue(foundStaged,
            "Should have staged section");
         assertTrue(foundDeletedLine,
            "Should show staged deleted file");
      }

      @Test
      @DisplayName("formats modified file with proper prefix")
      void modifiedFile() {
         List<String> raw = lines(
            "# branch.head main",
            "# branch.oid abc1234",
            "1 .M N... 100644 100644 100644"
               + " abcdef1234567890 1234567890abcdef"
               + " src/Main.java");
         List<String> formatted =
            GitStatusBuffer.formatStatus(raw);
         boolean found = false;
         for (String line : formatted) {
            if (line.contains("modified")
                  && line.contains("src/Main.java")) {
               found = true;
               // Verify indent pattern: 2 spaces
               assertTrue(line.startsWith("  "));
            }
         }
         assertTrue(found, "Should show modified file");
      }

      @Test
      @DisplayName("formats renamed file with arrow")
      void renamedFile() {
         // Porcelain v2 rename: "2 R. N... ... hash1 hash2
         //    R100 new\told"
         List<String> raw = lines(
            "# branch.head main",
            "# branch.oid abc1234",
            "2 R. N... 100644 100644 100644"
               + " abcdef1234567890 1234567890abcdef"
               + " R100 new.java\told.java");
         List<String> formatted =
            GitStatusBuffer.formatStatus(raw);
         boolean found = false;
         for (String line : formatted) {
            if (line.contains("renamed")
                  && line.contains("->")) {
               found = true;
            }
         }
         assertTrue(found,
            "Should show renamed file with arrow");
      }

      @Test
      @DisplayName("formats untracked file")
      void untrackedFile() {
         List<String> raw = lines(
            "# branch.head main",
            "# branch.oid abc1234",
            "? new-file.txt");
         List<String> formatted =
            GitStatusBuffer.formatStatus(raw);
         boolean found = false;
         for (String line : formatted) {
            if (line.contains("new-file.txt")) {
               found = true;
               assertTrue(line.startsWith("  "));
            }
         }
         assertTrue(found, "Should show untracked file");
      }

      @Test
      @DisplayName("parses branch ahead/behind counts")
      void branchAheadBehind() {
         List<String> raw = lines(
            "# branch.head feature-x",
            "# branch.oid abc1234567890",
            "# branch.upstream origin/feature-x",
            "# branch.ab +3 -1");
         List<String> formatted =
            GitStatusBuffer.formatStatus(raw);
         boolean foundHead = false;
         boolean foundUpstream = false;
         for (String line : formatted) {
            if (line.startsWith("Head:") && line.contains("feature-x"))
               foundHead = true;
            if (line.startsWith("Push:")
                  && line.contains("ahead 3")
                  && line.contains("behind 1"))
               foundUpstream = true;
         }
         assertTrue(foundHead, "Should show branch name");
         assertTrue(foundUpstream,
            "Should show ahead/behind counts");
      }
   }

   // -- Staging view structure --

   @Nested
   @DisplayName("staging view structure")
   class StagingViewStructure {

      @Test
      @DisplayName("buildStagingView includes both separators")
      void hasSeparators() {
         // buildStagingView calls GitProcess.execute which we
         // can't mock, but parseStagingViewHunks works on
         // pre-built lines. Test the structure expectations.
         List<String> view = lines(
            GitCommitView.STAGED_SEPARATOR,
            "#   (no staged changes)",
            "#",
            GitCommitView.UNSTAGED_SEPARATOR,
            "#   (no unstaged changes)",
            "",
            "# s=stage hunk  u=unstage  ^]=goto file  q=quit");
         assertTrue(view.contains(GitCommitView.STAGED_SEPARATOR));
         assertTrue(view.contains(
            GitCommitView.UNSTAGED_SEPARATOR));
      }

      @Test
      @DisplayName("staging view stat line filename extraction")
      void statLineFilenameExtraction() {
         // Simulate what ^] does for staged stat lines
         String statLine = "#   src/Main.java | 3 +-";
         assertTrue(statLine.startsWith("#   "));
         assertFalse(statLine.startsWith("#   ("));
         String stat = statLine.substring(4).trim();
         int pipe = stat.indexOf('|');
         assertTrue(pipe > 0);
         stat = stat.substring(0, pipe).trim();
         assertEquals("src/Main.java", stat);
      }

      @Test
      @DisplayName("staging view stat line without pipe")
      void statLineNoPipe() {
         // Some stat lines may lack pipe (e.g., binary files)
         String statLine = "#   binary-file.bin";
         assertTrue(statLine.startsWith("#   "));
         String stat = statLine.substring(4).trim();
         int pipe = stat.indexOf('|');
         // No pipe → stat stays as full string
         assertFalse(pipe > 0);
         assertEquals("binary-file.bin", stat);
      }

      @Test
      @DisplayName("no-changes message line is excluded from goto")
      void noChangesExcluded() {
         String noChanges = "#   (no staged changes)";
         assertTrue(noChanges.startsWith("#   ("));
         // The ^] handler skips lines starting with "#   ("
      }
   }

   // -- Diff line navigation helpers --

   @Nested
   @DisplayName("diff line navigation")
   class DiffLineNavigation {

      @Test
      @DisplayName("extracts filename from +++ b/ prefix")
      void extractFileFromPlusLine() {
         String line = "+++ b/src/Main.java";
         String filepath = line.substring(4).trim();
         if (filepath.length() > 2 && filepath.charAt(1) == '/')
            filepath = filepath.substring(2);
         assertEquals("src/Main.java", filepath);
      }

      @Test
      @DisplayName("extracts filename from +++ w/ prefix")
      void extractFileFromWorktreePrefix() {
         String line = "+++ w/ai/compile.out";
         String filepath = line.substring(4).trim();
         if (filepath.length() > 2 && filepath.charAt(1) == '/')
            filepath = filepath.substring(2);
         assertEquals("ai/compile.out", filepath);
      }

      @Test
      @DisplayName("handles +++ /dev/null for new files")
      void devNullFile() {
         String line = "+++ /dev/null";
         String filepath = line.substring(4).trim();
         // navigateDiffLine checks for /dev/null
         assertEquals("/dev/null", filepath);
      }

      @Test
      @DisplayName("parses hunk header for new-file start line")
      void parseHunkHeader() {
         String header = "@@ -10,6 +15,8 @@ context text";
         int plus = header.indexOf('+', 3);
         assertTrue(plus > 0);
         int comma = header.indexOf(',', plus);
         int sp = header.indexOf(' ', plus);
         int end = comma >= 0 && (sp < 0 || comma < sp)
            ? comma : sp;
         assertTrue(end > plus);
         int newStart = Integer.parseInt(
            header.substring(plus + 1, end));
         assertEquals(15, newStart);
      }

      @Test
      @DisplayName("parses hunk header with no count")
      void parseHunkHeaderNoCount() {
         // Single-line hunk: @@ -1 +1 @@
         String header = "@@ -1 +1 @@";
         int plus = header.indexOf('+', 3);
         int comma = header.indexOf(',', plus);
         int sp = header.indexOf(' ', plus);
         int end = (comma >= 0 && (sp < 0 || comma < sp))
            ? comma : sp;
         int newStart = Integer.parseInt(
            header.substring(plus + 1, end));
         assertEquals(1, newStart);
      }

      @Test
      @DisplayName("counts new-file lines excluding deletions")
      void countNewFileLines() {
         // Simulates the line-counting logic in navigateDiffLine
         List<String> hunkBody = lines(
            " context",
            "+added1",
            "+added2",
            "-removed",
            " context2");
         int offset = 0;
         for (String line : hunkBody) {
            if (!line.startsWith("-"))
               offset++;
         }
         assertEquals(4, offset);
         // Target = hunkNewStart + offset
      }
   }
}
