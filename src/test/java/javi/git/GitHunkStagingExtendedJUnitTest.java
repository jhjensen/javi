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
 * Extended tests for {@link GitHunkStaging} — formatAnnotatedDiff
 * and additional edge cases.
 */
class GitHunkStagingExtendedJUnitTest {

   private static List<String> lines(String... lines) {
      return Arrays.asList(lines);
   }

   // ── formatAnnotatedDiff ──────────────────────────────────

   @Nested
   @DisplayName("formatAnnotatedDiff()")
   class FormatAnnotatedDiff {

      @Test
      @DisplayName("adds header and includes all diff lines")
      void addsHeaderAndContent() {
         List<String> diffLines = lines(
            "diff --git a/Foo.java b/Foo.java",
            "index abc..def 100644",
            "--- a/Foo.java",
            "+++ b/Foo.java",
            "@@ -1,2 +1,3 @@",
            " line1",
            "+added",
            " line2");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diffLines);
         List<String> result =
            GitHunkStaging.formatAnnotatedDiff(diffLines, hunks);
         // First line is the instruction header
         assertTrue(result.get(0).contains("stage"),
            "first line should mention stage");
         assertTrue(result.get(0).contains("unstage"),
            "first line should mention unstage");
         // Second line is blank separator
         assertEquals("", result.get(1));
         // Rest is the diff content
         assertEquals("diff --git a/Foo.java b/Foo.java",
            result.get(2));
         assertEquals("+added", result.get(8));
      }

      @Test
      @DisplayName("empty diff produces only header")
      void emptyDiff() {
         List<String> diffLines = lines();
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diffLines);
         List<String> result =
            GitHunkStaging.formatAnnotatedDiff(diffLines, hunks);
         assertEquals(2, result.size());
         assertTrue(result.get(0).contains("stage"));
         assertEquals("", result.get(1));
      }

      @Test
      @DisplayName("result size is diffLines + 2 header lines")
      void resultSizeMatchesInput() {
         List<String> diffLines = lines(
            "diff --git a/A.java b/A.java",
            "index x..y 100644",
            "--- a/A.java",
            "+++ b/A.java",
            "@@ -1,1 +1,2 @@",
            " existing",
            "+new");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diffLines);
         List<String> result =
            GitHunkStaging.formatAnnotatedDiff(diffLines, hunks);
         assertEquals(diffLines.size() + 2, result.size());
      }
   }

   // ── Hunk.toPatch edge cases ──────────────────────────────

   @Nested
   @DisplayName("Hunk.toPatch() edge cases")
   class ToPatchEdgeCases {

      @Test
      @DisplayName("patch ends with trailing newline")
      void trailingNewline() {
         List<String> diff = lines(
            "diff --git a/X.java b/X.java",
            "index a..b 100644",
            "--- a/X.java",
            "+++ b/X.java",
            "@@ -1,1 +1,2 @@",
            " x",
            "+y");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         String patch = hunks.get(0).toPatch();
         assertTrue(patch.endsWith("\n"));
      }

      @Test
      @DisplayName("patch contains all header lines")
      void containsAllHeaders() {
         List<String> diff = lines(
            "diff --git a/X.java b/X.java",
            "index abc1234..def5678 100644",
            "--- a/X.java",
            "+++ b/X.java",
            "@@ -1,1 +1,2 @@",
            " x",
            "+y");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         String patch = hunks.get(0).toPatch();
         assertTrue(patch.contains("diff --git a/X.java b/X.java"));
         assertTrue(patch.contains("index abc1234..def5678 100644"));
         assertTrue(patch.contains("--- a/X.java"));
         assertTrue(patch.contains("+++ b/X.java"));
      }

      @Test
      @DisplayName("patch of second hunk has same header")
      void secondHunkSameHeader() {
         List<String> diff = lines(
            "diff --git a/X.java b/X.java",
            "index a..b 100644",
            "--- a/X.java",
            "+++ b/X.java",
            "@@ -1,2 +1,3 @@",
            " a",
            "+b",
            "@@ -10,2 +11,3 @@",
            " c",
            "+d",
            " e");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         assertEquals(2, hunks.size());
         String patch0 = hunks.get(0).toPatch();
         String patch1 = hunks.get(1).toPatch();
         // Both patches share the same header
         assertTrue(patch0.contains("diff --git a/X.java"));
         assertTrue(patch1.contains("diff --git a/X.java"));
         // But have different @@ lines
         assertTrue(patch0.contains("@@ -1,2 +1,3 @@"));
         assertTrue(patch1.contains("@@ -10,2 +11,3 @@"));
      }
   }

   // ── parseHunks edge cases ────────────────────────────────

   @Nested
   @DisplayName("parseHunks() edge cases")
   class ParseHunksEdgeCases {

      @Test
      @DisplayName("handles new file mode header")
      void newFileMode() {
         List<String> diff = lines(
            "diff --git a/New.java b/New.java",
            "new file mode 100644",
            "index 0000000..abc1234",
            "--- /dev/null",
            "+++ b/New.java",
            "@@ -0,0 +1,3 @@",
            "+line1",
            "+line2",
            "+line3");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         assertEquals(1, hunks.size());
         // Header includes new file mode
         assertTrue(hunks.get(0).header.stream()
            .anyMatch(h -> h.contains("new file mode")));
         assertEquals(4, hunks.get(0).body.size());
      }

      @Test
      @DisplayName("handles deleted file mode header")
      void deletedFileMode() {
         List<String> diff = lines(
            "diff --git a/Old.java b/Old.java",
            "deleted file mode 100644",
            "index abc1234..0000000",
            "--- a/Old.java",
            "+++ /dev/null",
            "@@ -1,3 +0,0 @@",
            "-line1",
            "-line2",
            "-line3");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         assertEquals(1, hunks.size());
         assertTrue(hunks.get(0).header.stream()
            .anyMatch(h -> h.contains("deleted file mode")));
      }

      @Test
      @DisplayName("handles rename header lines")
      void renameHeader() {
         List<String> diff = lines(
            "diff --git a/Old.java b/New.java",
            "similarity index 95%",
            "rename from Old.java",
            "rename to New.java",
            "index abc..def 100644",
            "--- a/Old.java",
            "+++ b/New.java",
            "@@ -1,3 +1,3 @@",
            " line1",
            "-old",
            "+new",
            " line3");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         assertEquals(1, hunks.size());
         // Header includes similarity and rename lines
         assertTrue(hunks.get(0).header.stream()
            .anyMatch(h -> h.contains("similarity index")));
         assertTrue(hunks.get(0).header.stream()
            .anyMatch(h -> h.contains("rename from")));
         assertTrue(hunks.get(0).header.stream()
            .anyMatch(h -> h.contains("rename to")));
      }

      @Test
      @DisplayName("binary files line is captured in header")
      void binaryFiles() {
         List<String> diff = lines(
            "diff --git a/image.png b/image.png",
            "Binary files a/image.png and b/image.png differ");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         // Binary files have no @@ hunks
         assertTrue(hunks.isEmpty());
      }

      @Test
      @DisplayName("hunk bufferLine is 1-based")
      void bufferLineIsOneBased() {
         List<String> diff = lines(
            "diff --git a/F.java b/F.java",
            "index a..b 100644",
            "--- a/F.java",
            "+++ b/F.java",
            "@@ -1,2 +1,3 @@",
            " x",
            "+y");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         // @@ is at index 4 (0-based), so bufferLine = 5 (1-based)
         assertEquals(5, hunks.get(0).bufferLine);
      }
   }

   // ── findHunkAtLine edge cases ────────────────────────────

   @Nested
   @DisplayName("findHunkAtLine() edge cases")
   class FindHunkEdgeCases {

      @Test
      @DisplayName("returns null for line 0")
      void lineZero() {
         List<String> diff = lines(
            "diff --git a/F.java b/F.java",
            "index a..b 100644",
            "--- a/F.java",
            "+++ b/F.java",
            "@@ -1,2 +1,3 @@",
            " x",
            "+y");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         assertFalse(hunks.isEmpty());
         GitHunkStaging.Hunk found =
            GitHunkStaging.findHunkAtLine(hunks, 0);
         // Line 0 is before any hunk
         assertEquals(null, found);
      }

      @Test
      @DisplayName("returns null for line far past all hunks")
      void lineFarPast() {
         List<String> diff = lines(
            "diff --git a/F.java b/F.java",
            "index a..b 100644",
            "--- a/F.java",
            "+++ b/F.java",
            "@@ -1,2 +1,3 @@",
            " x",
            "+y");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         // Hunk body is 3 lines starting at line 5, so ends at line 7
         GitHunkStaging.Hunk found =
            GitHunkStaging.findHunkAtLine(hunks, 100);
         // Line 100 is way past the hunk body
         assertEquals(null, found);
      }

      @Test
      @DisplayName("returns null for empty hunk list")
      void emptyHunkList() {
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(lines());
         GitHunkStaging.Hunk found =
            GitHunkStaging.findHunkAtLine(hunks, 5);
         assertEquals(null, found);
      }
   }
}
