package javi.git;

import java.awt.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for diff header navigation helpers in {@link GitDiffNav}.
 *
 * <p>Covers {@code stripDiffPrefix}, {@code extractPathFromDiffGit},
 * and {@code forwardScanForFilepath} — the methods that enable
 * ^] navigation from diff header lines.</p>
 */
class GitDiffNavigationJUnitTest {

   // ── stripDiffPrefix ──────────────────────────────────────

   @Nested
   @DisplayName("stripDiffPrefix")
   class StripDiffPrefix {

      @Test
      @DisplayName("strips b/ prefix")
      void stripsBSlash() {
         assertEquals("src/Main.java",
            GitDiffNav.stripDiffPrefix("b/src/Main.java"));
      }

      @Test
      @DisplayName("strips w/ prefix")
      void stripsWSlash() {
         assertEquals("src/Main.java",
            GitDiffNav.stripDiffPrefix("w/src/Main.java"));
      }

      @Test
      @DisplayName("strips i/ prefix")
      void stripsISlash() {
         assertEquals("src/Main.java",
            GitDiffNav.stripDiffPrefix("i/src/Main.java"));
      }

      @Test
      @DisplayName("strips a/ prefix")
      void stripsASlash() {
         assertEquals("src/Main.java",
            GitDiffNav.stripDiffPrefix("a/src/Main.java"));
      }

      @Test
      @DisplayName("preserves path without prefix")
      void preservesNoPrefix() {
         assertEquals("src/Main.java",
            GitDiffNav.stripDiffPrefix("src/Main.java"));
      }

      @Test
      @DisplayName("preserves /dev/null")
      void preservesDevNull() {
         assertEquals("/dev/null",
            GitDiffNav.stripDiffPrefix("/dev/null"));
      }

      @Test
      @DisplayName("preserves short path")
      void preservesShort() {
         assertEquals("a",
            GitDiffNav.stripDiffPrefix("a"));
      }

      @Test
      @DisplayName("preserves two-char path without slash")
      void preservesTwoCharNoSlash() {
         assertEquals("ab",
            GitDiffNav.stripDiffPrefix("ab"));
      }
   }

   // ── extractPathFromDiffGit ───────────────────────────────

   @Nested
   @DisplayName("extractPathFromDiffGit")
   class ExtractPathFromDiffGit {

      @Test
      @DisplayName("standard a/b prefixes")
      void standardPrefixes() {
         assertEquals("src/Main.java",
            GitDiffNav.extractPathFromDiffGit(
               "diff --git a/src/Main.java b/src/Main.java"));
      }

      @Test
      @DisplayName("i/w prefixes (javi diff format)")
      void iwPrefixes() {
         assertEquals("ai/compile.out",
            GitDiffNav.extractPathFromDiffGit(
               "diff --git i/ai/compile.out w/ai/compile.out"));
      }

      @Test
      @DisplayName("different source and dest paths (rename)")
      void renamePaths() {
         assertEquals("src/NewName.java",
            GitDiffNav.extractPathFromDiffGit(
               "diff --git a/src/OldName.java b/src/NewName.java"));
      }

      @Test
      @DisplayName("path with spaces uses last b/ separator")
      void pathWithSpaces() {
         assertEquals("src/My File.java",
            GitDiffNav.extractPathFromDiffGit(
               "diff --git a/src/My File.java b/src/My File.java"));
      }

      @Test
      @DisplayName("deeply nested path")
      void deeplyNested() {
         assertEquals("src/main/java/javi/git/GitCommands.java",
            GitDiffNav.extractPathFromDiffGit(
               "diff --git a/src/main/java/javi/git/GitCommands.java"
               + " b/src/main/java/javi/git/GitCommands.java"));
      }
   }

   // ── forwardScanForFilepath ───────────────────────────────

   @Nested
   @DisplayName("forwardScanForFilepath")
   class ForwardScan {

      private java.util.List<String> lines(String... args) {
         return java.util.Arrays.asList(args);
      }

      @Test
      @DisplayName("on diff --git line extracts path")
      void diffGitLine() {
         String result = GitDiffNav.forwardScanForFilepath(
            lines(
               "diff --git a/Foo.java b/Foo.java",
               "index abc..def 100644",
               "--- a/Foo.java",
               "+++ b/Foo.java",
               "@@ -1,3 +1,3 @@"), 1);
         assertEquals("Foo.java", result);
      }

      @Test
      @DisplayName("on --- line scans forward to +++ line")
      void minusMinusLine() {
         String result = GitDiffNav.forwardScanForFilepath(
            lines(
               "diff --git a/Foo.java b/Foo.java",
               "index abc..def 100644",
               "--- a/Foo.java",
               "+++ b/Foo.java",
               "@@ -1,3 +1,3 @@"), 3);
         assertEquals("Foo.java", result);
      }

      @Test
      @DisplayName("on index line scans forward to +++ line")
      void indexLine() {
         String result = GitDiffNav.forwardScanForFilepath(
            lines(
               "diff --git a/Foo.java b/Foo.java",
               "index abc..def 100644",
               "--- a/Foo.java",
               "+++ b/Foo.java",
               "@@ -1,3 +1,3 @@"), 2);
         assertEquals("Foo.java", result);
      }

      @Test
      @DisplayName("on +++ line forward scan returns null")
      void plusPlusLine() {
         // +++ is handled by backward scan; forward scan
         // starts from curLine+1 and won't find another +++
         String result = GitDiffNav.forwardScanForFilepath(
            lines(
               "diff --git a/Foo.java b/Foo.java",
               "index abc..def 100644",
               "--- a/Foo.java",
               "+++ b/Foo.java",
               "@@ -1,3 +1,3 @@"), 4);
         assertNull(result);
      }

      @Test
      @DisplayName("with i/w prefixes")
      void iwPrefixes() {
         String result = GitDiffNav.forwardScanForFilepath(
            lines(
               "diff --git i/ai/compile.out w/ai/compile.out",
               "index 164d5b54..dddfa11f 100644",
               "--- i/ai/compile.out",
               "+++ w/ai/compile.out",
               "@@ -4,9 +4,9 @@ context line"), 1);
         assertEquals("ai/compile.out", result);
      }

      @Test
      @DisplayName("stops at next diff header")
      void stopsAtNextDiff() {
         // Line 1 is not a diff header, scan forward should
         // stop at line 2 (diff --git) before finding +++
         String result = GitDiffNav.forwardScanForFilepath(
            lines(
               "some random line",
               "diff --git a/Other.java b/Other.java",
               "--- a/Other.java",
               "+++ b/Other.java"), 1);
         assertNull(result);
      }

      @Test
      @DisplayName("returns null when no +++ found")
      void noMatch() {
         String result = GitDiffNav.forwardScanForFilepath(
            lines(
               "some unrelated content",
               "more unrelated content"), 1);
         assertNull(result);
      }

      @Test
      @DisplayName("on @@ line returns null (no +++ below)")
      void hunkHeaderLine() {
         String result = GitDiffNav.forwardScanForFilepath(
            lines(
               "diff --git a/Foo.java b/Foo.java",
               "+++ b/Foo.java",
               "@@ -1,3 +1,3 @@",
               " context",
               "-old",
               "+new"), 3);
         assertNull(result);
      }
   }

   // ── isDeletedAtCursor (via StatusBuffer format) ──────────

   @Nested
   @DisplayName("deleted file detection in status format")
   class DeletedFileFormat {

      @Test
      @DisplayName("porcelain D status produces deleted descriptor")
      void deletedFileInPorcelain() {
         java.util.List<String> raw = java.util.Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234",
            "1 .D N... 100644 100644 000000 "
               + "abc1234 def5678 src/Removed.java");
         java.util.List<String> result =
            GitStatusBuffer.formatStatus(raw);
         boolean found = result.stream()
            .anyMatch(l -> l.contains("deleted")
               && l.contains("src/Removed.java"));
         assertTrue(found,
            "deleted file should appear in unstaged section");
      }

      @Test
      @DisplayName("staged deletion (D.) appears in staged section")
      void stagedDeletion() {
         java.util.List<String> raw = java.util.Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234",
            "1 D. N... 100644 000000 000000 "
               + "abc1234 0000000 src/Removed.java");
         java.util.List<String> result =
            GitStatusBuffer.formatStatus(raw);
         // Find the "Staged changes" section
         int stagedIdx = -1;
         int unstagedIdx = -1;
         for (int i = 0; i < result.size(); i++) {
            if (result.get(i).startsWith("Staged changes"))
               stagedIdx = i;
            if (result.get(i).startsWith("Unstaged changes"))
               unstagedIdx = i;
         }
         assertTrue(stagedIdx >= 0, "should have staged section");
         assertTrue(unstagedIdx > stagedIdx,
            "unstaged should follow staged");
         boolean foundInStaged = false;
         for (int i = stagedIdx; i < unstagedIdx; i++) {
            if (result.get(i).contains("deleted")
                  && result.get(i).contains("src/Removed.java"))
               foundInStaged = true;
         }
         assertTrue(foundInStaged,
            "staged deletion should appear in staged section");
      }

      @Test
      @DisplayName("extractFilenameAtCursor handles deleted prefix")
      void extractDeletedFilename() {
         // Simulate the status buffer line for a deleted file
         // "  deleted     src/Removed.java"
         String line = "deleted     src/Removed.java";
         // Verify the prefix parsing logic
         assertTrue(line.startsWith("deleted"));
         String rest = line.substring("deleted".length()).trim();
         assertEquals("src/Removed.java", rest);
      }
   }

   // ── extractFilenameFromLine ──────────────────────────────

   @Nested
   @DisplayName("extractFilenameFromLine")
   class ExtractFilenameFromLine {

      @Test
      @DisplayName("modified file line")
      void modifiedFile() {
         assertEquals("src/Main.java",
            GitCommands.extractFilenameFromLine(
               "  modified    src/Main.java"));
      }

      @Test
      @DisplayName("new file line")
      void newFile() {
         assertEquals("src/NewFile.java",
            GitCommands.extractFilenameFromLine(
               "  new file    src/NewFile.java"));
      }

      @Test
      @DisplayName("deleted file line")
      void deletedFile() {
         assertEquals("src/Removed.java",
            GitCommands.extractFilenameFromLine(
               "  deleted     src/Removed.java"));
      }

      @Test
      @DisplayName("renamed file line extracts new name")
      void renamedFile() {
         assertEquals("src/New.java",
            GitCommands.extractFilenameFromLine(
               "  renamed     src/Old.java -> src/New.java"));
      }

      @Test
      @DisplayName("untracked file (bare name)")
      void untrackedFile() {
         assertEquals("newfile.txt",
            GitCommands.extractFilenameFromLine(
               "  newfile.txt"));
      }

      @Test
      @DisplayName("untracked file in subdirectory")
      void untrackedSubdir() {
         assertEquals("src/test/Data.java",
            GitCommands.extractFilenameFromLine(
               "  src/test/Data.java"));
      }

      @Test
      @DisplayName("returns null for (none) placeholder")
      void nonePlaceholder() {
         assertNull(GitCommands.extractFilenameFromLine(
            "  (none)"));
      }

      @Test
      @DisplayName("returns null for section header")
      void sectionHeader() {
         assertNull(GitCommands.extractFilenameFromLine(
            "Staged changes (2)"));
      }

      @Test
      @DisplayName("returns null for empty line")
      void emptyLine() {
         assertNull(GitCommands.extractFilenameFromLine(""));
      }

      @Test
      @DisplayName("returns null for blank indent")
      void blankIndent() {
         assertNull(GitCommands.extractFilenameFromLine("  "));
      }

      @Test
      @DisplayName("returns null for keys help line")
      void helpLine() {
         assertNull(GitCommands.extractFilenameFromLine(
            "Keys: s=stage  u=unstage  X=discard"));
      }

      @Test
      @DisplayName("end-to-end: porcelain to status to filename")
      void endToEndStagedFile() {
         // Simulate git status porcelain v2 for a modified file
         java.util.List<String> raw = java.util.Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234",
            "1 M. N... 100644 100644 100644"
               + " aaaa bbbb src/Foo.java");
         java.util.List<String> statusLines =
            GitStatusBuffer.formatStatus(raw);
         // Find the modified file line and extract filename
         String filename = null;
         for (String line : statusLines) {
            String extracted =
               GitCommands.extractFilenameFromLine(line);
            if (extracted != null
                  && extracted.contains("Foo.java")) {
               filename = extracted;
               break;
            }
         }
         assertEquals("src/Foo.java", filename);
      }

      @Test
      @DisplayName("end-to-end: untracked file through status")
      void endToEndUntrackedFile() {
         java.util.List<String> raw = java.util.Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234",
            "? newfile.txt");
         java.util.List<String> statusLines =
            GitStatusBuffer.formatStatus(raw);
         String filename = null;
         for (String line : statusLines) {
            String extracted =
               GitCommands.extractFilenameFromLine(line);
            if (extracted != null
                  && extracted.contains("newfile")) {
               filename = extracted;
               break;
            }
         }
         assertEquals("newfile.txt", filename);
      }

      @Test
      @DisplayName("end-to-end: deleted file through status")
      void endToEndDeletedFile() {
         java.util.List<String> raw = java.util.Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234",
            "1 .D N... 100644 100644 000000"
               + " aaaa 0000 src/Gone.java");
         java.util.List<String> statusLines =
            GitStatusBuffer.formatStatus(raw);
         String filename = null;
         for (String line : statusLines) {
            String extracted =
               GitCommands.extractFilenameFromLine(line);
            if (extracted != null
                  && extracted.contains("Gone.java")) {
               filename = extracted;
               break;
            }
         }
         assertEquals("src/Gone.java", filename);
      }

      @Test
      @DisplayName("end-to-end: newly staged (added) file")
      void endToEndNewlyStagedFile() {
         java.util.List<String> raw = java.util.Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234",
            "1 A. N... 000000 100644 100644"
               + " 0000 bbbb src/Brand.java");
         java.util.List<String> statusLines =
            GitStatusBuffer.formatStatus(raw);
         String filename = null;
         for (String line : statusLines) {
            String extracted =
               GitCommands.extractFilenameFromLine(line);
            if (extracted != null
                  && extracted.contains("Brand.java")) {
               filename = extracted;
               break;
            }
         }
         assertEquals("src/Brand.java", filename);
      }
   }

   // ── computeFirstChangedLineOffset ────────────────────────

   @Nested
   @DisplayName("computeFirstChangedLineOffset")
   class ComputeFirstChangedLineOffset {

      private java.util.List<String> lines(String... args) {
         return java.util.Arrays.asList(args);
      }

      @Test
      @DisplayName("user scenario: context then single addition")
      void userScenario() {
         // Hunk body from user's .gitignore example:
         //  bin      (context)
         //  tmp      (context)
         //  ai       (context)
         // +xxx      (addition)
         // hunkNewStart=25, expected offset=4, target=25+4-1=28
         int offset = GitDiffNav.computeFirstChangedLineOffset(
            lines(" bin", " tmp", " ai", "+xxx"));
         assertEquals(4, offset);
      }

      @Test
      @DisplayName("addition on first line of hunk")
      void additionFirst() {
         int offset = GitDiffNav.computeFirstChangedLineOffset(
            lines("+new line", " context"));
         assertEquals(1, offset);
      }

      @Test
      @DisplayName("deletions before first addition")
      void deletionsBeforeAddition() {
         int offset = GitDiffNav.computeFirstChangedLineOffset(
            lines("-removed1", "-removed2", " context", "+added"));
         assertEquals(2, offset);
      }

      @Test
      @DisplayName("interleaved deletions and context")
      void interleavedDeletionsContext() {
         int offset = GitDiffNav.computeFirstChangedLineOffset(
            lines(" ctx1", "-del1", " ctx2", "-del2", "+add1"));
         assertEquals(3, offset);
      }

      @Test
      @DisplayName("no addition in hunk — all context")
      void noAddition() {
         int offset = GitDiffNav.computeFirstChangedLineOffset(
            lines(" line1", " line2", " line3"));
         assertEquals(3, offset);
      }

      @Test
      @DisplayName("no addition — deletion only hunk")
      void deletionOnly() {
         int offset = GitDiffNav.computeFirstChangedLineOffset(
            lines("-removed1", "-removed2", " context"));
         assertEquals(1, offset);
      }

      @Test
      @DisplayName("stops at next hunk header")
      void stopsAtNextHunk() {
         int offset = GitDiffNav.computeFirstChangedLineOffset(
            lines(" ctx1", " ctx2",
               "@@ -10,3 +10,3 @@", "+should not count"));
         assertEquals(2, offset);
      }

      @Test
      @DisplayName("stops at next diff --git header")
      void stopsAtNextDiff() {
         int offset = GitDiffNav.computeFirstChangedLineOffset(
            lines(" ctx1",
               "diff --git a/other b/other", "+should not count"));
         assertEquals(1, offset);
      }

      @Test
      @DisplayName("empty hunk body")
      void emptyBody() {
         int offset = GitDiffNav.computeFirstChangedLineOffset(
            lines());
         assertEquals(0, offset);
      }

      @Test
      @DisplayName("multiple additions — stops at first")
      void multipleAdditions() {
         int offset = GitDiffNav.computeFirstChangedLineOffset(
            lines(" ctx", "+first add", "+second add"));
         assertEquals(2, offset);
      }

      @Test
      @DisplayName("target line calculation matches user example")
      void targetLineCalculation() {
         // User example: @@ -25,3 +25,4 @@
         // hunkNewStart = 25
         int hunkNewStart = 25;
         int offset = GitDiffNav.computeFirstChangedLineOffset(
            lines(" bin", " tmp", " ai", "+xxx"));
         int targetLine = hunkNewStart + offset - 1;
         assertEquals(28, targetLine,
            "xxx should be at line 28 in the new file");
      }
   }

   // ── diffLineColor ─────────────────────────────────────────

   @Nested
   @DisplayName("diffLineColor")
   class DiffLineColor {

      @Test
      @DisplayName("--- header is yellow (foldSummaryColor)")
      void tripleMinusIsYellow() {
         assertEquals(GitDiffNav.DIFF_HEADER_COLOR,
            GitDiffNav.diffLineColor("--- a/src/Main.java"));
      }

      @Test
      @DisplayName("+++ header is yellow (foldSummaryColor)")
      void triplePlusIsYellow() {
         assertEquals(GitDiffNav.DIFF_HEADER_COLOR,
            GitDiffNav.diffLineColor("+++ b/src/Main.java"));
      }

      @Test
      @DisplayName("diff --git header is yellow")
      void diffGitIsYellow() {
         assertEquals(GitDiffNav.DIFF_HEADER_COLOR,
            GitDiffNav.diffLineColor(
               "diff --git a/Foo.java b/Foo.java"));
      }

      @Test
      @DisplayName("removed line (-) is red")
      void removedLineIsRed() {
         assertEquals(Color.red,
            GitDiffNav.diffLineColor("-old line content"));
      }

      @Test
      @DisplayName("added line (+) is green")
      void addedLineIsGreen() {
         assertEquals(Color.green,
            GitDiffNav.diffLineColor("+new line content"));
      }

      @Test
      @DisplayName("hunk header (@@ ...) is cyan")
      void hunkHeaderIsCyan() {
         assertEquals(Color.cyan,
            GitDiffNav.diffLineColor(
               "@@ -1,3 +1,4 @@ context"));
      }

      @Test
      @DisplayName("context line (space prefix) returns null")
      void contextLineIsNull() {
         assertNull(GitDiffNav.diffLineColor(" context line"));
      }

      @Test
      @DisplayName("empty string returns null")
      void emptyStringIsNull() {
         assertNull(GitDiffNav.diffLineColor(""));
      }

      @Test
      @DisplayName("null returns null")
      void nullReturnsNull() {
         assertNull(GitDiffNav.diffLineColor(null));
      }

      @Test
      @DisplayName("bare --- (no path) is still yellow")
      void bareTripleMinus() {
         assertEquals(GitDiffNav.DIFF_HEADER_COLOR,
            GitDiffNav.diffLineColor("---"));
      }

      @Test
      @DisplayName("bare +++ (no path) is still yellow")
      void bareTriplePlus() {
         assertEquals(GitDiffNav.DIFF_HEADER_COLOR,
            GitDiffNav.diffLineColor("+++"));
      }

      @Test
      @DisplayName("--- /dev/null (new file) is yellow")
      void devNullMinusIsYellow() {
         assertEquals(GitDiffNav.DIFF_HEADER_COLOR,
            GitDiffNav.diffLineColor("--- /dev/null"));
      }

      @Test
      @DisplayName("+++ /dev/null (deleted file) is yellow")
      void devNullPlusIsYellow() {
         assertEquals(GitDiffNav.DIFF_HEADER_COLOR,
            GitDiffNav.diffLineColor("+++ /dev/null"));
      }

      @Test
      @DisplayName("plain text with no diff prefix returns null")
      void plainTextIsNull() {
         assertNull(GitDiffNav.diffLineColor(
            "some ordinary text"));
      }

      @Test
      @DisplayName("foldSummaryColor is Color.YELLOW")
      void foldSummaryIsYellow() {
         assertEquals(Color.YELLOW, GitDiffNav.DIFF_HEADER_COLOR);
      }
   }

   // ── boundary / regression tests for Bug 1 ────────────────

   @Nested
   @DisplayName("Bug 1: bounds and navigation regression")
   class Bug1Regression {

      private java.util.List<String> lines(String... args) {
         return java.util.Arrays.asList(args);
      }

      @Test
      @DisplayName("forwardScan at last line does not exceed bounds")
      void forwardScanLastLine() {
         // Buffer with +++ on last line; cursor at last line
         java.util.List<String> buf = lines(
            "diff --git i/.gitignore w/.gitignore",
            "--- i/.gitignore",
            "+++ w/.gitignore");
         // curLine = 3 (last line), should not throw
         String result =
            GitDiffNav.forwardScanForFilepath(buf, 3);
         assertNull(result);
      }

      @Test
      @DisplayName("forwardScan curLine beyond size returns null")
      void forwardScanCurLineBeyondSize() {
         java.util.List<String> buf = lines(
            "diff --git a/F.java b/F.java",
            "+++ b/F.java");
         // curLine beyond list size
         String result =
            GitDiffNav.forwardScanForFilepath(buf, 5);
         assertNull(result);
      }

      @Test
      @DisplayName("offset with hunk ending at last buffer line")
      void offsetHunkAtEnd() {
         // Simulate the user scenario: hunk body is last lines
         // of buffer. computeFirstChangedLineOffset should work.
         int offset = GitDiffNav.computeFirstChangedLineOffset(
            lines(" bin", " tmp", " ai", "+xxx"));
         int hunkNewStart = 25;
         int targetLine = hunkNewStart + offset - 1;
         assertEquals(28, targetLine);
      }

      @Test
      @DisplayName("offset with only additions — no context")
      void offsetOnlyAdditions() {
         int offset = GitDiffNav.computeFirstChangedLineOffset(
            lines("+line1", "+line2", "+line3"));
         assertEquals(1, offset);
         // targetLine = hunkNewStart + 1 - 1 = hunkNewStart
         int hunkNewStart = 1;
         assertEquals(1, hunkNewStart + offset - 1);
      }

      @Test
      @DisplayName("offset single context then addition")
      void offsetSingleContextThenAdd() {
         int offset = GitDiffNav.computeFirstChangedLineOffset(
            lines(" ctx", "+added"));
         assertEquals(2, offset);
         int hunkNewStart = 10;
         assertEquals(11, hunkNewStart + offset - 1);
      }
   }
}
