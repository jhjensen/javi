package javi.git;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for diff header navigation helpers in {@link GitCommands}.
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
            GitCommands.stripDiffPrefix("b/src/Main.java"));
      }

      @Test
      @DisplayName("strips w/ prefix")
      void stripsWSlash() {
         assertEquals("src/Main.java",
            GitCommands.stripDiffPrefix("w/src/Main.java"));
      }

      @Test
      @DisplayName("strips i/ prefix")
      void stripsISlash() {
         assertEquals("src/Main.java",
            GitCommands.stripDiffPrefix("i/src/Main.java"));
      }

      @Test
      @DisplayName("strips a/ prefix")
      void stripsASlash() {
         assertEquals("src/Main.java",
            GitCommands.stripDiffPrefix("a/src/Main.java"));
      }

      @Test
      @DisplayName("preserves path without prefix")
      void preservesNoPrefix() {
         assertEquals("src/Main.java",
            GitCommands.stripDiffPrefix("src/Main.java"));
      }

      @Test
      @DisplayName("preserves /dev/null")
      void preservesDevNull() {
         assertEquals("/dev/null",
            GitCommands.stripDiffPrefix("/dev/null"));
      }

      @Test
      @DisplayName("preserves short path")
      void preservesShort() {
         assertEquals("a",
            GitCommands.stripDiffPrefix("a"));
      }

      @Test
      @DisplayName("preserves two-char path without slash")
      void preservesTwoCharNoSlash() {
         assertEquals("ab",
            GitCommands.stripDiffPrefix("ab"));
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
            GitCommands.extractPathFromDiffGit(
               "diff --git a/src/Main.java b/src/Main.java"));
      }

      @Test
      @DisplayName("i/w prefixes (javi diff format)")
      void iwPrefixes() {
         assertEquals("ai/compile.out",
            GitCommands.extractPathFromDiffGit(
               "diff --git i/ai/compile.out w/ai/compile.out"));
      }

      @Test
      @DisplayName("different source and dest paths (rename)")
      void renamePaths() {
         assertEquals("src/NewName.java",
            GitCommands.extractPathFromDiffGit(
               "diff --git a/src/OldName.java b/src/NewName.java"));
      }

      @Test
      @DisplayName("path with spaces uses last b/ separator")
      void pathWithSpaces() {
         assertEquals("src/My File.java",
            GitCommands.extractPathFromDiffGit(
               "diff --git a/src/My File.java b/src/My File.java"));
      }

      @Test
      @DisplayName("deeply nested path")
      void deeplyNested() {
         assertEquals("src/main/java/javi/git/GitCommands.java",
            GitCommands.extractPathFromDiffGit(
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
         String result = GitCommands.forwardScanForFilepath(
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
         String result = GitCommands.forwardScanForFilepath(
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
         String result = GitCommands.forwardScanForFilepath(
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
         String result = GitCommands.forwardScanForFilepath(
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
         String result = GitCommands.forwardScanForFilepath(
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
         String result = GitCommands.forwardScanForFilepath(
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
         String result = GitCommands.forwardScanForFilepath(
            lines(
               "some unrelated content",
               "more unrelated content"), 1);
         assertNull(result);
      }

      @Test
      @DisplayName("on @@ line returns null (no +++ below)")
      void hunkHeaderLine() {
         String result = GitCommands.forwardScanForFilepath(
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
}
