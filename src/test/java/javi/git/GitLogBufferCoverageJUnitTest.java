package javi.git;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for {@link GitLogBuffer} SHA/decoration extraction,
 * marker parsing, and {@link GitLogEntry} parsing edge cases.
 */
class GitLogBufferCoverageJUnitTest {

   // ── extractSha ───────────────────────────────────────────

   @Nested
   @DisplayName("extractSha")
   class ExtractSha {

      @Test
      @DisplayName("extracts SHA from simple oneline")
      void simpleOneline() {
         assertEquals("abc1234",
            GitLogBuffer.extractSha("* abc1234 Fix bug"));
      }

      @Test
      @DisplayName("extracts SHA from branching graph line")
      void branchingGraph() {
         assertEquals("def5678",
            GitLogBuffer.extractSha("| * def5678 Add feature"));
      }

      @Test
      @DisplayName("extracts SHA with decoration")
      void withDecoration() {
         String sha = GitLogBuffer.extractSha(
            "* abc1234 (HEAD -> main) Initial commit");
         assertEquals("abc1234", sha);
      }

      @Test
      @DisplayName("extracts SHA from merge graph")
      void mergeLine() {
         String sha = GitLogBuffer.extractSha(
            "|\\  9876543 Merge branch 'feature'");
         assertNotNull(sha);
      }

      @Test
      @DisplayName("returns null for graph-only line")
      void graphOnly() {
         assertNull(GitLogBuffer.extractSha("| |"));
      }

      @Test
      @DisplayName("returns null for empty line")
      void emptyLine() {
         assertNull(GitLogBuffer.extractSha(""));
      }

      @Test
      @DisplayName("returns null for null input")
      void nullInput() {
         assertNull(GitLogBuffer.extractSha(null));
      }

      @Test
      @DisplayName("SHA must be at least 7 hex chars")
      void shortShaIgnored() {
         assertNull(GitLogBuffer.extractSha("* abc12 Short"));
      }

      @Test
      @DisplayName("extracts 40-character full SHA")
      void fullSha() {
         String full = "abc1234def5678901234567890abcdef12345678";
         String sha = GitLogBuffer.extractSha("* " + full + " msg");
         assertEquals(full, sha);
      }
   }

   // ── extractDecoration ────────────────────────────────────

   @Nested
   @DisplayName("extractDecoration")
   class ExtractDecoration {

      @Test
      @DisplayName("extracts HEAD -> branch")
      void headBranch() {
         String deco = GitLogBuffer.extractDecoration(
            "* abc1234 (HEAD -> main) Fix bug");
         assertEquals("HEAD -> main", deco);
      }

      @Test
      @DisplayName("extracts tag name")
      void tagName() {
         String deco = GitLogBuffer.extractDecoration(
            "* abc1234 (tag: v1.0) Release");
         assertEquals("tag: v1.0", deco);
      }

      @Test
      @DisplayName("extracts multiple refs in parens")
      void multipleRefs() {
         String deco = GitLogBuffer.extractDecoration(
            "* abc1234 (HEAD -> main, origin/main) msg");
         assertNotNull(deco);
         assertTrue(deco.contains("HEAD -> main"));
         assertTrue(deco.contains("origin/main"));
      }

      @Test
      @DisplayName("returns null when no decoration")
      void noDecoration() {
         assertNull(GitLogBuffer.extractDecoration(
            "* abc1234 Plain commit"));
      }

      @Test
      @DisplayName("returns null for null")
      void nullInput() {
         assertNull(GitLogBuffer.extractDecoration(null));
      }
   }

   // ── extractShaFromMarker ─────────────────────────────────

   @Nested
   @DisplayName("extractShaFromMarker")
   class ExtractShaFromMarker {

      @Test
      @DisplayName("extracts from show-diffs marker")
      void showMarker() {
         assertEquals("abc1234",
            GitLogBuffer.extractShaFromMarker(
               "  >> Show diffs (abc1234)"));
      }

      @Test
      @DisplayName("extracts from hide-diffs marker")
      void hideMarker() {
         assertEquals("def5678",
            GitLogBuffer.extractShaFromMarker(
               "  << Diffs (def5678)"));
      }

      @Test
      @DisplayName("returns null for regular line")
      void regularLine() {
         assertNull(GitLogBuffer.extractShaFromMarker(
            "* abc1234 Regular commit"));
      }

      @Test
      @DisplayName("returns null for null input")
      void nullInput() {
         assertNull(GitLogBuffer.extractShaFromMarker(null));
      }

      @Test
      @DisplayName("returns null when no parentheses")
      void noParens() {
         assertNull(GitLogBuffer.extractShaFromMarker(
            "  >> Show diffs no parens"));
      }
   }

   // ── GitLogEntry ──────────────────────────────────────────

   @Nested
   @DisplayName("GitLogEntry parsing")
   class EntryParsing {

      @Test
      @DisplayName("parses standard pipe-delimited line")
      void standardLine() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "abc1234|def5678|(HEAD -> main)|Fix bug|Alice|2025-01-01"));
         assertEquals(1, entries.size());
         assertEquals("abc1234", entries.get(0).sha);
         assertEquals("def5678", entries.get(0).parents.get(0));
         assertEquals("HEAD -> main", entries.get(0).decoration);
         assertEquals("Fix bug", entries.get(0).subject);
         assertEquals("Alice", entries.get(0).author);
      }

      @Test
      @DisplayName("parses merge with two parents")
      void twoParents() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "aaa|bbb ccc||Merge|Author|Date"));
         assertEquals(2, entries.get(0).parents.size());
         assertEquals("bbb", entries.get(0).parents.get(0));
         assertEquals("ccc", entries.get(0).parents.get(1));
      }

      @Test
      @DisplayName("parses root commit — empty parents")
      void rootCommit() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "aaa|||Initial|Author|Date"));
         assertTrue(entries.get(0).parents.isEmpty());
      }

      @Test
      @DisplayName("decoration null when empty parens")
      void emptyDecoration() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "aaa|bbb||Subject|Author|Date"));
         assertNull(entries.get(0).decoration);
      }

      @Test
      @DisplayName("skips blank lines")
      void skipsBlankLines() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "", "aaa|bbb||Sub|Auth|Date", ""));
         assertEquals(1, entries.size());
      }

      @Test
      @DisplayName("skips lines with fewer than 6 pipe-delimited parts")
      void skipsMalformed() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "too|few|parts"));
         assertTrue(entries.isEmpty());
      }

      @Test
      @DisplayName("parses multiple entries in order")
      void multipleEntries() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "aaa|bbb||S1|A1|D1",
            "ccc|ddd||S2|A2|D2",
            "eee|||S3|A3|D3"));
         assertEquals(3, entries.size());
         assertEquals("aaa", entries.get(0).sha);
         assertEquals("ccc", entries.get(1).sha);
         assertEquals("eee", entries.get(2).sha);
      }
   }

   // ── GitLogEntry.shortSha ─────────────────────────────────

   @Nested
   @DisplayName("shortSha")
   class ShortSha {

      @Test
      @DisplayName("truncates long SHA to 7 chars")
      void truncates() {
         GitLogEntry e = new GitLogEntry(
            "abc1234def567890", Collections.emptyList(),
            "sub", "auth", "date", null);
         assertEquals("abc1234", e.shortSha());
      }

      @Test
      @DisplayName("returns full SHA when 7 or fewer chars")
      void shortInput() {
         GitLogEntry e = new GitLogEntry(
            "abc", Collections.emptyList(),
            "sub", "auth", "date", null);
         assertEquals("abc", e.shortSha());
      }

      @Test
      @DisplayName("returns exactly 7 for exactly 7 chars")
      void exact7() {
         GitLogEntry e = new GitLogEntry(
            "abc1234", Collections.emptyList(),
            "sub", "auth", "date", null);
         assertEquals("abc1234", e.shortSha());
      }
   }

   // ── Constants ────────────────────────────────────────────

   @Test
   @DisplayName("PAGINATION_MARKER value")
   void paginationMarker() {
      assertEquals("--- more ---", GitLogBuffer.PAGINATION_MARKER);
   }
}
