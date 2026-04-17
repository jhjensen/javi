package javi.git;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GitHunkStaging} — diff parsing and hunk extraction.
 */
class GitHunkStagingJUnitTest {

   private static List<String> lines(String... lines) {
      return Arrays.asList(lines);
   }

   // ── Hunk parsing ─────────────────────────────────────────

   @Nested
   @DisplayName("hunk parsing")
   class HunkParsing {

      @Test
      @DisplayName("parses single hunk from one file")
      void singleHunk() {
         List<String> diff = lines(
            "diff --git a/Foo.java b/Foo.java",
            "index abc1234..def5678 100644",
            "--- a/Foo.java",
            "+++ b/Foo.java",
            "@@ -1,3 +1,4 @@",
            " line1",
            "+added",
            " line2",
            " line3");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         assertEquals(1, hunks.size());
         assertEquals(0, hunks.get(0).index);
         assertEquals(5, hunks.get(0).body.size());
      }

      @Test
      @DisplayName("parses two hunks from one file")
      void twoHunks() {
         List<String> diff = lines(
            "diff --git a/Foo.java b/Foo.java",
            "index abc1234..def5678 100644",
            "--- a/Foo.java",
            "+++ b/Foo.java",
            "@@ -1,3 +1,4 @@",
            " line1",
            "+added",
            " line2",
            "@@ -10,3 +11,4 @@",
            " line10",
            "+another",
            " line11",
            " line12");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         assertEquals(2, hunks.size());
         assertEquals(0, hunks.get(0).index);
         assertEquals(1, hunks.get(1).index);
         assertEquals(4, hunks.get(0).body.size());
         assertEquals(5, hunks.get(1).body.size());
      }

      @Test
      @DisplayName("hunks from multiple files")
      void multipleFiles() {
         List<String> diff = lines(
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
            GitHunkStaging.parseHunks(diff);
         assertEquals(2, hunks.size());
         // First hunk header is from A.java
         assertTrue(hunks.get(0).header.get(0)
            .contains("A.java"));
         // Second hunk header is from B.java
         assertTrue(hunks.get(1).header.get(0)
            .contains("B.java"));
      }

      @Test
      @DisplayName("empty diff returns no hunks")
      void emptyDiff() {
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(lines());
         assertTrue(hunks.isEmpty());
      }

      @Test
      @DisplayName("header includes index and --- +++ lines")
      void headerContent() {
         List<String> diff = lines(
            "diff --git a/Foo.java b/Foo.java",
            "index abc..def 100644",
            "--- a/Foo.java",
            "+++ b/Foo.java",
            "@@ -1,2 +1,3 @@",
            " line",
            "+new");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         assertEquals(1, hunks.size());
         assertEquals(4, hunks.get(0).header.size());
         assertTrue(hunks.get(0).header.get(0)
            .startsWith("diff --git"));
         assertTrue(hunks.get(0).header.get(1)
            .startsWith("index"));
         assertTrue(hunks.get(0).header.get(2)
            .startsWith("---"));
         assertTrue(hunks.get(0).header.get(3)
            .startsWith("+++"));
      }
   }

   // ── Patch generation ─────────────────────────────────────

   @Nested
   @DisplayName("patch generation")
   class PatchGeneration {

      @Test
      @DisplayName("toPatch produces valid patch content")
      void toPatch() {
         List<String> diff = lines(
            "diff --git a/Foo.java b/Foo.java",
            "index abc..def 100644",
            "--- a/Foo.java",
            "+++ b/Foo.java",
            "@@ -1,2 +1,3 @@",
            " line",
            "+new",
            " end");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         String patch = hunks.get(0).toPatch();
         assertTrue(patch.startsWith("diff --git"));
         assertTrue(patch.contains("@@ -1,2 +1,3 @@"));
         assertTrue(patch.contains("+new"));
         assertTrue(patch.endsWith("\n"));
      }
   }

   // ── Hunk lookup ──────────────────────────────────────────

   @Nested
   @DisplayName("hunk lookup by line")
   class HunkLookup {

      @Test
      @DisplayName("finds hunk at @@ line")
      void findsAtHeader() {
         List<String> diff = lines(
            "diff --git a/F.java b/F.java",
            "index a..b 100644",
            "--- a/F.java",
            "+++ b/F.java",
            "@@ -1,2 +1,3 @@",
            " line",
            "+new",
            " end");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         // @@ is at diff line index 4, bufferLine = 5 (1-based)
         GitHunkStaging.Hunk found =
            GitHunkStaging.findHunkAtLine(hunks, 5);
         assertNotNull(found);
         assertEquals(0, found.index);
      }

      @Test
      @DisplayName("finds hunk within body lines")
      void findsWithinBody() {
         List<String> diff = lines(
            "diff --git a/F.java b/F.java",
            "index a..b 100644",
            "--- a/F.java",
            "+++ b/F.java",
            "@@ -1,2 +1,3 @@",
            " line",
            "+new",
            " end");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         // +new is at diff line index 6, bufferLine = 7
         GitHunkStaging.Hunk found =
            GitHunkStaging.findHunkAtLine(hunks, 7);
         assertNotNull(found);
         assertEquals(0, found.index);
      }

      @Test
      @DisplayName("returns null for header area")
      void nullForHeader() {
         List<String> diff = lines(
            "diff --git a/F.java b/F.java",
            "index a..b 100644",
            "--- a/F.java",
            "+++ b/F.java",
            "@@ -1,2 +1,3 @@",
            " line",
            "+new");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         // Line 2 is within file header, not a hunk
         GitHunkStaging.Hunk found =
            GitHunkStaging.findHunkAtLine(hunks, 2);
         assertNull(found);
      }

      @Test
      @DisplayName("finds correct hunk with two hunks")
      void findsSecondHunk() {
         List<String> diff = lines(
            "diff --git a/F.java b/F.java",
            "index a..b 100644",
            "--- a/F.java",
            "+++ b/F.java",
            "@@ -1,2 +1,3 @@",
            " a",
            "+b",
            "@@ -10,2 +11,3 @@",
            " x",
            "+y",
            " z");
         List<GitHunkStaging.Hunk> hunks =
            GitHunkStaging.parseHunks(diff);
         assertEquals(2, hunks.size());
         // Second @@ is at diff line 7, bufferLine = 8
         GitHunkStaging.Hunk found =
            GitHunkStaging.findHunkAtLine(hunks, 8);
         assertNotNull(found);
         assertEquals(1, found.index);
      }
   }
}
