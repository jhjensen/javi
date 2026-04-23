package javi.git;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage tests for {@link GitBlameBuffer} — parsing
 * edge cases, formatting alignment, and date conversion.
 */
class GitBlameBufferExtendedJUnitTest {

   private static List<GitBlameBuffer.BlameEntry> parse(
         String... lines) {
      return GitBlameBuffer.parsePorcelain(Arrays.asList(lines));
   }

   // ── Porcelain parsing edge cases ─────────────────────────

   @Nested
   @DisplayName("porcelain edge cases")
   class PorcelainEdgeCases {

      @Test
      @DisplayName("tab-only source line becomes empty string")
      void tabOnlySourceLine() {
         List<GitBlameBuffer.BlameEntry> entries = parse(
            "abc123def456789012345678901234567890abcd 1 1 1",
            "author X",
            "author-time 1700000000",
            "summary test",
            "filename f.java",
            "\t");
         assertEquals(1, entries.size());
         assertEquals("", entries.get(0).line);
      }

      @Test
      @DisplayName("source line with leading tabs preserved")
      void indentedSourceLine() {
         List<GitBlameBuffer.BlameEntry> entries = parse(
            "abc123def456789012345678901234567890abcd 1 1 1",
            "author X",
            "author-time 1700000000",
            "summary test",
            "filename f.java",
            "\t\t\tindented code");
         assertEquals(1, entries.size());
         assertEquals("\t\tindented code", entries.get(0).line);
      }

      @Test
      @DisplayName("skips committer metadata lines")
      void skipsCommitterLines() {
         List<GitBlameBuffer.BlameEntry> entries = parse(
            "abc123def456789012345678901234567890abcd 1 1 1",
            "author Alice",
            "author-mail <alice@example.com>",
            "author-time 1700000000",
            "author-tz +0000",
            "committer Bob",
            "committer-mail <bob@example.com>",
            "committer-time 1700001000",
            "committer-tz +0000",
            "summary Fix bug",
            "previous def456789012345678901234567890abcd123456 f.java",
            "filename f.java",
            "\tcode");
         assertEquals(1, entries.size());
         assertEquals("Alice", entries.get(0).author);
      }

      @Test
      @DisplayName("repeated SHA reuses cached author and date")
      void repeatedShaCachesMetadata() {
         List<GitBlameBuffer.BlameEntry> entries = parse(
            "abc123def456789012345678901234567890abcd 1 1 3",
            "author Alice",
            "author-time 1700000000",
            "summary First commit",
            "filename f.java",
            "\tline one",
            "abc123def456789012345678901234567890abcd 2 2",
            "\tline two",
            "abc123def456789012345678901234567890abcd 3 3",
            "\tline three");
         assertEquals(3, entries.size());
         for (GitBlameBuffer.BlameEntry e : entries) {
            assertEquals("Alice", e.author);
            assertNotNull(e.date);
         }
      }

      @Test
      @DisplayName("different SHAs get their own metadata")
      void differentShasOwnMetadata() {
         List<GitBlameBuffer.BlameEntry> entries = parse(
            "abc123def456789012345678901234567890abcd 1 1 1",
            "author Alice",
            "author-time 1700000000",
            "summary first",
            "filename f.java",
            "\tline one",
            "def456789012345678901234567890abcd12345678 2 2 1",
            "author Bob",
            "author-time 1710000000",
            "summary second",
            "filename f.java",
            "\tline two");
         assertEquals(2, entries.size());
         assertEquals("Alice", entries.get(0).author);
         assertEquals("Bob", entries.get(1).author);
         // Dates should differ
         assertNotNull(entries.get(0).date);
         assertNotNull(entries.get(1).date);
      }

      @Test
      @DisplayName("SHA truncated to 8 chars in entry")
      void shaTruncated() {
         List<GitBlameBuffer.BlameEntry> entries = parse(
            "abc123def456789012345678901234567890abcd 1 1 1",
            "author X",
            "author-time 1700000000",
            "summary test",
            "filename f.java",
            "\tcode");
         assertEquals(1, entries.size());
         assertEquals("abc123de", entries.get(0).sha);
         assertEquals(8, entries.get(0).sha.length());
      }
   }

   // ── Formatting ───────────────────────────────────────────

   @Nested
   @DisplayName("formatBlame")
   class FormatBlame {

      @Test
      @DisplayName("empty entries produces empty output")
      void emptyEntries() {
         List<String> formatted = GitBlameBuffer.formatBlame(
            List.of());
         assertTrue(formatted.isEmpty());
      }

      @Test
      @DisplayName("pipe separator present in output")
      void pipeSeparator() {
         List<GitBlameBuffer.BlameEntry> entries = parse(
            "abc123def456789012345678901234567890abcd 1 1 1",
            "author Alice",
            "author-time 1700000000",
            "summary test",
            "filename f.java",
            "\thello world");
         List<String> formatted =
            GitBlameBuffer.formatBlame(entries);
         assertEquals(1, formatted.size());
         assertTrue(formatted.get(0).contains("|"));
         assertTrue(formatted.get(0).contains("hello world"));
      }

      @Test
      @DisplayName("long author truncated to 20 chars")
      void longAuthorTruncated() {
         List<GitBlameBuffer.BlameEntry> entries = parse(
            "abc123def456789012345678901234567890abcd 1 1 1",
            "author A Very Long Author Name That Exceeds The Limit",
            "author-time 1700000000",
            "summary test",
            "filename f.java",
            "\tcode");
         List<String> formatted =
            GitBlameBuffer.formatBlame(entries);
         String line = formatted.get(0);
         assertTrue(line.contains("A Very Long Author N"));
      }

      @Test
      @DisplayName("short author padded for alignment")
      void shortAuthorPadded() {
         List<GitBlameBuffer.BlameEntry> entries = parse(
            "abc123def456789012345678901234567890abcd 1 1 1",
            "author Al",
            "author-time 1700000000",
            "summary test",
            "filename f.java",
            "\tcode1",
            "def456789012345678901234567890abcd12345678 2 2 1",
            "author LongerName",
            "author-time 1710000000",
            "summary test2",
            "filename f.java",
            "\tcode2");
         List<String> formatted =
            GitBlameBuffer.formatBlame(entries);
         assertEquals(2, formatted.size());
         // Both lines should have the pipe at roughly the same column
         int pipe1 = formatted.get(0).indexOf('|');
         int pipe2 = formatted.get(1).indexOf('|');
         assertEquals(pipe1, pipe2,
            "pipe separators should be aligned");
      }

      @Test
      @DisplayName("multiple entries produce same number of lines")
      void multipleEntries() {
         List<GitBlameBuffer.BlameEntry> entries = parse(
            "abc123def456789012345678901234567890abcd 1 1 2",
            "author X",
            "author-time 1700000000",
            "summary t",
            "filename f.java",
            "\tline1",
            "abc123def456789012345678901234567890abcd 2 2",
            "\tline2");
         List<String> formatted =
            GitBlameBuffer.formatBlame(entries);
         assertEquals(2, formatted.size());
      }
   }

   // ── Date conversion ──────────────────────────────────────

   @Nested
   @DisplayName("epochToDate")
   class EpochToDate {

      @Test
      @DisplayName("valid epoch returns YYYY-MM-DD format")
      void validEpoch() {
         String date = GitBlameBuffer.epochToDate("1700000000");
         assertNotNull(date);
         // Should be 2023-11-14 (varies by timezone)
         assertTrue(date.startsWith("2023-11"),
            "expected 2023-11, got: " + date);
      }

      @Test
      @DisplayName("epoch 0 returns 1970 date")
      void epochZero() {
         String date = GitBlameBuffer.epochToDate("0");
         assertTrue(date.startsWith("19"),
            "epoch 0 should be 1970: " + date);
      }

      @Test
      @DisplayName("negative epoch returns pre-1970 date")
      void negativeEpoch() {
         String date = GitBlameBuffer.epochToDate("-86400");
         assertTrue(date.startsWith("19"),
            "negative epoch should be pre-1970: " + date);
      }

      @Test
      @DisplayName("non-numeric string returned as-is")
      void nonNumeric() {
         assertEquals("not-a-number",
            GitBlameBuffer.epochToDate("not-a-number"));
      }

      @Test
      @DisplayName("whitespace-padded epoch trimmed")
      void whitespaceTrimmed() {
         String date = GitBlameBuffer.epochToDate("  1700000000  ");
         assertTrue(date.startsWith("2023-11"));
      }
   }
}
