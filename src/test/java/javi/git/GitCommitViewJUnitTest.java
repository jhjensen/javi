package javi.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GitCommitView} -- commit view buffer building,
 * message extraction, and hunk position parsing.
 */
class GitCommitViewJUnitTest {

   private static List<String> lines(String... lines) {
      return Arrays.asList(lines);
   }

   // -- Message extraction --

   @Nested
   @DisplayName("message extraction")
   class MessageExtraction {

      @Test
      @DisplayName("extracts message before staged separator")
      void basicMessage() {
         List<String> viewLines = lines(
            "Fix the broken test",
            "# Enter commit message above",
            "# Lines starting with '#' will be ignored.",
            "#",
            GitCommitView.STAGED_SEPARATOR,
            "#   file.java | 2 +-",
            "#",
            GitCommitView.UNSTAGED_SEPARATOR,
            "diff --git a/Foo.java b/Foo.java");
         String msg = GitCommitView.extractMessage(viewLines);
         assertEquals("Fix the broken test", msg);
      }

      @Test
      @DisplayName("extracts multi-line message")
      void multiLineMessage() {
         List<String> viewLines = lines(
            "First line",
            "",
            "Detailed description here",
            "# comment line",
            "#",
            GitCommitView.STAGED_SEPARATOR,
            "#   file.java | 1 +");
         String msg = GitCommitView.extractMessage(viewLines);
         assertEquals("First line\n\nDetailed description here",
            msg);
      }

      @Test
      @DisplayName("returns empty for blank message")
      void emptyMessage() {
         List<String> viewLines = lines(
            "",
            "# Enter commit message above",
            "#",
            GitCommitView.STAGED_SEPARATOR);
         String msg = GitCommitView.extractMessage(viewLines);
         assertEquals("", msg);
      }

      @Test
      @DisplayName("skips comment lines in message area")
      void skipsComments() {
         List<String> viewLines = lines(
            "Real message",
            "# this is a comment",
            "More message",
            "#",
            GitCommitView.STAGED_SEPARATOR);
         String msg = GitCommitView.extractMessage(viewLines);
         assertEquals("Real message\nMore message", msg);
      }
   }

   // -- Message preservation --

   @Nested
   @DisplayName("message preservation")
   class MessagePreservation {

      @Test
      @DisplayName("preserves message lines for refresh")
      void preserveBasic() {
         List<String> viewLines = lines(
            "WIP: fixing tests",
            "# instruction",
            "#",
            GitCommitView.STAGED_SEPARATOR,
            "#   stuff");
         List<String> preserved =
            GitCommitView.preserveMessage(viewLines);
         assertEquals(1, preserved.size());
         assertEquals("WIP: fixing tests", preserved.get(0));
      }

      @Test
      @DisplayName("trims trailing blanks from preserved message")
      void trimTrailingBlanks() {
         List<String> viewLines = lines(
            "message",
            "",
            "",
            "# instruction",
            "#",
            GitCommitView.STAGED_SEPARATOR);
         List<String> preserved =
            GitCommitView.preserveMessage(viewLines);
         assertEquals(1, preserved.size());
         assertEquals("message", preserved.get(0));
      }
   }

   // -- Hunk position parsing --

   @Nested
   @DisplayName("commit view hunk parsing")
   class ViewHunkParsing {

      @Test
      @DisplayName("parses hunks with correct buffer positions")
      void hunkPositions() {
         List<String> viewLines = lines(
            "commit message",
            "# instruction",
            "#",
            GitCommitView.STAGED_SEPARATOR,
            "#   file.java | 1 +",
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
            GitCommitView.parseViewHunks(viewLines);
         assertEquals(1, hunks.size());
         // diffStart (0-based) = index 6 (UNSTAGED_SEP) + 1 = 7
         // Within diff subset starting at index 7:
         //   offset 0: "diff --git ..."
         //   offset 1: "index ..."
         //   offset 2: "--- ..."
         //   offset 3: "+++ ..."
         //   offset 4: "@@ ..." -> raw bufferLine = 5 (1-based)
         // adjusted = 5 + 7 = 12
         assertEquals(12, hunks.get(0).bufferLine);
      }

      @Test
      @DisplayName("returns empty list when no unstaged changes")
      void noUnstagedChanges() {
         List<String> viewLines = lines(
            "message",
            "#",
            GitCommitView.STAGED_SEPARATOR,
            "#   file | 1 +",
            "#",
            GitCommitView.UNSTAGED_SEPARATOR,
            "#   (no unstaged changes)");
         List<GitHunkStaging.Hunk> hunks =
            GitCommitView.parseViewHunks(viewLines);
         assertTrue(hunks.isEmpty());
      }

      @Test
      @DisplayName("parses multiple hunks from multiple files")
      void multipleFilesHunks() {
         List<String> viewLines = lines(
            "",
            "# instruction",
            "#",
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
            GitCommitView.parseViewHunks(viewLines);
         assertEquals(2, hunks.size());
         assertTrue(hunks.get(0).header.get(0)
            .contains("A.java"));
         assertTrue(hunks.get(1).header.get(0)
            .contains("B.java"));
         assertTrue(hunks.get(0).bufferLine > 6);
         assertTrue(hunks.get(1).bufferLine
            > hunks.get(0).bufferLine);
      }
   }

   // -- findDiffStart --

   @Nested
   @DisplayName("findDiffStart")
   class FindDiffStart {

      @Test
      @DisplayName("finds line after unstaged separator")
      void findsStart() {
         List<String> viewLines = lines(
            "msg",
            "#",
            GitCommitView.STAGED_SEPARATOR,
            "#",
            GitCommitView.UNSTAGED_SEPARATOR,
            "diff --git ...");
         int start = GitCommitView.findDiffStart(viewLines);
         assertEquals(6, start);
      }

      @Test
      @DisplayName("returns -1 when no separator")
      void noSeparator() {
         List<String> viewLines = lines(
            "msg",
            "no separator here");
         int start = GitCommitView.findDiffStart(viewLines);
         assertEquals(-1, start);
      }
   }

   // -- Message persistence --

   @Nested
   @DisplayName("message persistence")
   class MessagePersistence {

      @TempDir
      Path tempDir;

      private String setupFakeRepo() throws IOException {
         Path gitDir = tempDir.resolve(".git");
         Files.createDirectories(gitDir);
         return tempDir.toAbsolutePath().toString();
      }

      @Test
      @DisplayName("saves and loads message")
      void saveAndLoad() throws IOException {
         String root = setupFakeRepo();
         List<String> msg = Arrays.asList(
            "Fix the build", "", "Detailed explanation");
         GitCommitView.saveMessage(root, msg);

         List<String> loaded = GitCommitView.loadMessage(root);
         assertEquals(3, loaded.size());
         assertEquals("Fix the build", loaded.get(0));
         assertEquals("", loaded.get(1));
         assertEquals("Detailed explanation", loaded.get(2));
      }

      @Test
      @DisplayName("clears saved message")
      void clearMessage() throws IOException {
         String root = setupFakeRepo();
         List<String> msg = Arrays.asList("test msg");
         GitCommitView.saveMessage(root, msg);
         // Verify it was saved
         assertTrue(
            Files.exists(tempDir.resolve(".git/JAVI_COMMIT_MSG")));

         GitCommitView.clearSavedMessage(root);
         assertFalse(
            Files.exists(tempDir.resolve(".git/JAVI_COMMIT_MSG")));
      }

      @Test
      @DisplayName("returns null when no saved message")
      void loadReturnsNullWhenNone() throws IOException {
         String root = setupFakeRepo();
         assertNull(GitCommitView.loadMessage(root));
      }

      @Test
      @DisplayName("does not save blank messages")
      void blankMessageNotSaved() throws IOException {
         String root = setupFakeRepo();
         List<String> blank = Arrays.asList("", "  ", "");
         GitCommitView.saveMessage(root, blank);
         assertFalse(
            Files.exists(tempDir.resolve(".git/JAVI_COMMIT_MSG")));
      }

      @Test
      @DisplayName("handles null repo root gracefully")
      void nullRepoRoot() {
         // Should not throw
         GitCommitView.saveMessage(null,
            Arrays.asList("test"));
         assertNull(GitCommitView.loadMessage(null));
         GitCommitView.clearSavedMessage(null);
      }

      @Test
      @DisplayName("deletes file when saving empty list")
      void emptyListDeletesFile() throws IOException {
         String root = setupFakeRepo();
         // First save something
         GitCommitView.saveMessage(root,
            Arrays.asList("initial"));
         assertTrue(
            Files.exists(tempDir.resolve(".git/JAVI_COMMIT_MSG")));

         // Save empty list — should delete
         GitCommitView.saveMessage(root, Arrays.asList());
         assertFalse(
            Files.exists(tempDir.resolve(".git/JAVI_COMMIT_MSG")));
      }
   }

   // -- Message area boundary detection --

   @Nested
   @DisplayName("message area boundary")
   class MessageAreaBoundary {

      @Test
      @DisplayName("first line is in message area")
      void firstLineInMessageArea() {
         List<String> viewLines = lines(
            "Fix bug",
            "# Enter commit message above",
            "#",
            GitCommitView.STAGED_SEPARATOR);
         assertTrue(GitCommitView.isInMessageArea(viewLines, 1));
      }

      @Test
      @DisplayName("comment line is not in message area")
      void commentLineNotEditable() {
         List<String> viewLines = lines(
            "Fix bug",
            "# Enter commit message above",
            "#",
            GitCommitView.STAGED_SEPARATOR);
         assertFalse(GitCommitView.isInMessageArea(viewLines, 2));
      }

      @Test
      @DisplayName("multi-line message all editable")
      void multiLineMessageEditable() {
         List<String> viewLines = lines(
            "First line",
            "",
            "Detailed description",
            "# comment",
            "#",
            GitCommitView.STAGED_SEPARATOR);
         assertTrue(GitCommitView.isInMessageArea(viewLines, 1));
         assertTrue(GitCommitView.isInMessageArea(viewLines, 2));
         assertTrue(GitCommitView.isInMessageArea(viewLines, 3));
         assertFalse(GitCommitView.isInMessageArea(viewLines, 4));
      }

      @Test
      @DisplayName("diff area is not in message area")
      void diffAreaNotEditable() {
         List<String> viewLines = lines(
            "Commit msg",
            "# comment",
            "#",
            GitCommitView.STAGED_SEPARATOR,
            "#   file.java | 2 +-",
            "#",
            GitCommitView.UNSTAGED_SEPARATOR,
            "diff --git a/Foo.java b/Foo.java",
            "--- a/Foo.java",
            "+++ b/Foo.java",
            "@@ -1,3 +1,4 @@");
         assertFalse(GitCommitView.isInMessageArea(viewLines, 8));
         assertFalse(GitCommitView.isInMessageArea(viewLines, 11));
      }

      @Test
      @DisplayName("line zero is not in message area")
      void lineZeroNotEditable() {
         List<String> viewLines = lines(
            "Msg",
            "# comment");
         assertFalse(GitCommitView.isInMessageArea(viewLines, 0));
      }

      @Test
      @DisplayName("getMessageAreaEnd returns correct boundary")
      void messageAreaEndCorrect() {
         List<String> viewLines = lines(
            "Line 1",
            "Line 2",
            "# first comment",
            "#",
            GitCommitView.STAGED_SEPARATOR);
         assertEquals(2, GitCommitView.getMessageAreaEnd(viewLines));
      }

      @Test
      @DisplayName("all-comment buffer has zero-length message area")
      void allCommentsNoMessageArea() {
         List<String> viewLines = lines(
            "# comment from start",
            "# more comments",
            GitCommitView.STAGED_SEPARATOR);
         assertEquals(0, GitCommitView.getMessageAreaEnd(viewLines));
         assertFalse(GitCommitView.isInMessageArea(viewLines, 1));
      }

      @Test
      @DisplayName("buffer with no comments — all lines editable")
      void noCommentsAllEditable() {
         List<String> viewLines = lines(
            "Line 1",
            "Line 2",
            "Line 3");
         assertEquals(3, GitCommitView.getMessageAreaEnd(viewLines));
         assertTrue(GitCommitView.isInMessageArea(viewLines, 3));
      }
   }
}
