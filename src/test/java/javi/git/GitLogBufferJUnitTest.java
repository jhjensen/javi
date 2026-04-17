package javi.git;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GitLogBuffer} — SHA extraction and pattern matching.
 */
class GitLogBufferJUnitTest {

   // ── extractSha ───────────────────────────────────────────

   @Nested
   @DisplayName("extractSha()")
   class ExtractSha {

      @Test
      @DisplayName("standard oneline graph format")
      void standardOneline() {
         String sha = GitLogBuffer.extractSha(
            "* abc1234 Fix the bug");
         assertEquals("abc1234", sha);
      }

      @Test
      @DisplayName("nested graph with branch")
      void nestedGraph() {
         String sha = GitLogBuffer.extractSha(
            "| * def5678 (HEAD -> main) Add feature");
         assertEquals("def5678", sha);
      }

      @Test
      @DisplayName("graph merge point")
      void mergePoint() {
         String sha = GitLogBuffer.extractSha(
            "|/  9876543 Merge branch");
         assertEquals("9876543", sha);
      }

      @Test
      @DisplayName("full 40-char SHA")
      void fullSha() {
         String line = "* " + "a".repeat(40) + " Full sha commit";
         String sha = GitLogBuffer.extractSha(line);
         assertEquals("a".repeat(40), sha);
      }

      @Test
      @DisplayName("null input returns null")
      void nullInput() {
         assertNull(GitLogBuffer.extractSha(null));
      }

      @Test
      @DisplayName("line without SHA returns null")
      void noSha() {
         assertNull(GitLogBuffer.extractSha("| |"));
      }

      @Test
      @DisplayName("SHA too short (6 chars) returns null")
      void tooShortSha() {
         assertNull(GitLogBuffer.extractSha("* abc123 short"));
      }
   }

   // ── extractDecoration ────────────────────────────────────

   @Nested
   @DisplayName("extractDecoration()")
   class ExtractDecoration {

      @Test
      @DisplayName("single branch")
      void singleBranch() {
         String deco = GitLogBuffer.extractDecoration(
            "* abc1234 (main) commit msg");
         assertEquals("main", deco);
      }

      @Test
      @DisplayName("HEAD and branch")
      void headAndBranch() {
         String deco = GitLogBuffer.extractDecoration(
            "* abc1234 (HEAD -> main, origin/main) msg");
         assertEquals("HEAD -> main, origin/main", deco);
      }

      @Test
      @DisplayName("tag decoration")
      void tagDecoration() {
         String deco = GitLogBuffer.extractDecoration(
            "* abc1234 (tag: v1.0) Release");
         assertEquals("tag: v1.0", deco);
      }

      @Test
      @DisplayName("no decoration returns null")
      void noDecoration() {
         assertNull(GitLogBuffer.extractDecoration(
            "* abc1234 plain commit"));
      }

      @Test
      @DisplayName("null returns null")
      void nullReturnsNull() {
         assertNull(GitLogBuffer.extractDecoration(null));
      }
   }

   // ── extractShaFromMarker ─────────────────────────────────

   @Nested
   @DisplayName("extractShaFromMarker()")
   class ExtractShaFromMarker {

      @Test
      @DisplayName("show diff marker")
      void showDiffMarker() {
         String sha = GitLogBuffer.extractShaFromMarker(
            "  >> Show diffs (abc1234)");
         assertEquals("abc1234", sha);
      }

      @Test
      @DisplayName("hide diff marker")
      void hideDiffMarker() {
         String sha = GitLogBuffer.extractShaFromMarker(
            "  << Diffs (def5678)");
         assertEquals("def5678", sha);
      }

      @Test
      @DisplayName("no parens returns null")
      void noParens() {
         assertNull(GitLogBuffer.extractShaFromMarker(
            "no markers here"));
      }

      @Test
      @DisplayName("null returns null")
      void nullReturnsNull() {
         assertNull(GitLogBuffer.extractShaFromMarker(null));
      }
   }

   // ── Constants ────────────────────────────────────────────

   @Test
   @DisplayName("DIFF_SHOW_PREFIX is correct")
   void diffShowPrefix() {
      assertTrue(GitLogBuffer.DIFF_SHOW_PREFIX.startsWith("  >> Show"));
   }

   @Test
   @DisplayName("PAGINATION_MARKER is correct")
   void paginationMarker() {
      assertEquals("--- more ---", GitLogBuffer.PAGINATION_MARKER);
   }
}
