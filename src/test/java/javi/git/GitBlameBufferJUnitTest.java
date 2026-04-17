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
 * Tests for {@link GitBlameBuffer} — porcelain blame parser.
 */
class GitBlameBufferJUnitTest {

   /**
    * Parse porcelain lines into BlameEntry list.
    */
   private static List<GitBlameBuffer.BlameEntry> parse(String... lines) {
      return GitBlameBuffer.parsePorcelain(Arrays.asList(lines));
   }

   // ── Porcelain parsing ────────────────────────────────────

   @Nested
   @DisplayName("porcelain parsing")
   class PorcelainParsing {

      @Test
      @DisplayName("parses single blame entry")
      void singleEntry() {
         List<GitBlameBuffer.BlameEntry> entries = parse(
            "abc123def456789012345678901234567890abcd 1 1 1",
            "author John Doe",
            "author-time 1700000000",
            "author-mail <john@example.com>",
            "committer Jane Doe",
            "committer-time 1700000000",
            "committer-mail <jane@example.com>",
            "summary Initial commit",
            "filename src/Foo.java",
            "\tpublic class Foo {");
         assertEquals(1, entries.size());
         assertEquals("abc123de", entries.get(0).sha);
         assertEquals("John Doe", entries.get(0).author);
         assertEquals("public class Foo {", entries.get(0).line);
      }

      @Test
      @DisplayName("parses multiple entries")
      void multipleEntries() {
         List<GitBlameBuffer.BlameEntry> entries = parse(
            "abc123def456789012345678901234567890abcd 1 1 2",
            "author Alice",
            "author-time 1700000000",
            "summary First",
            "filename src/Foo.java",
            "\tline one",
            "abc123def456789012345678901234567890abcd 2 2",
            "\tline two",
            "def456789012345678901234567890abcd12345678 3 3 1",
            "author Bob",
            "author-time 1710000000",
            "summary Second",
            "filename src/Foo.java",
            "\tline three");
         assertEquals(3, entries.size());
         assertEquals("Alice", entries.get(0).author);
         assertEquals("Alice", entries.get(1).author);
         assertEquals("Bob", entries.get(2).author);
         assertEquals("line one", entries.get(0).line);
         assertEquals("line two", entries.get(1).line);
         assertEquals("line three", entries.get(2).line);
      }

      @Test
      @DisplayName("caches author across repeated SHA")
      void cachedAuthor() {
         List<GitBlameBuffer.BlameEntry> entries = parse(
            "abc123def456789012345678901234567890abcd 1 1 1",
            "author Cached Author",
            "author-time 1700000000",
            "summary test",
            "filename f.java",
            "\tfirst line",
            "abc123def456789012345678901234567890abcd 2 2",
            "\tsecond line");
         assertEquals(2, entries.size());
         assertEquals("Cached Author", entries.get(0).author);
         assertEquals("Cached Author", entries.get(1).author);
      }

      @Test
      @DisplayName("handles empty input")
      void emptyInput() {
         List<GitBlameBuffer.BlameEntry> entries = parse();
         assertTrue(entries.isEmpty());
      }
   }

   // ── Formatting ───────────────────────────────────────────

   @Nested
   @DisplayName("formatting")
   class Formatting {

      @Test
      @DisplayName("formats entries with aligned columns")
      void alignedColumns() {
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
         String line = formatted.get(0);
         assertTrue(line.contains("abc123de"));
         assertTrue(line.contains("Alice"));
         assertTrue(line.contains("hello world"));
         assertTrue(line.contains("|"));
      }

      @Test
      @DisplayName("truncates long author names")
      void truncatesLongAuthor() {
         List<GitBlameBuffer.BlameEntry> entries = parse(
            "abc123def456789012345678901234567890abcd 1 1 1",
            "author A Very Long Author Name That Exceeds Limit",
            "author-time 1700000000",
            "summary test",
            "filename f.java",
            "\tcode");
         List<String> formatted =
            GitBlameBuffer.formatBlame(entries);
         assertEquals(1, formatted.size());
         // Author should be truncated to 20 chars
         String line = formatted.get(0);
         assertTrue(line.contains("A Very Long Author N"));
      }
   }

   // ── Date conversion ──────────────────────────────────────

   @Nested
   @DisplayName("date conversion")
   class DateConversion {

      @Test
      @DisplayName("converts epoch to date string")
      void convertsEpoch() {
         // 2023-11-14 (approximately, timezone dependent)
         String date = GitBlameBuffer.epochToDate("1700000000");
         assertNotNull(date);
         assertTrue(date.startsWith("2023-11"));
      }

      @Test
      @DisplayName("handles invalid epoch gracefully")
      void invalidEpoch() {
         String date = GitBlameBuffer.epochToDate("not-a-number");
         assertEquals("not-a-number", date);
      }
   }
}
