package javi.git;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GitLogEntry} — git log line parser and data holder.
 */
class GitLogEntryJUnitTest {

   // Format expected by parse(): SHA|PARENTS|DECORATION|SUBJECT|AUTHOR|DATE

   @Nested
   @DisplayName("parse()")
   class Parse {

      @Test
      @DisplayName("single commit with no parents or decoration")
      void singleCommitNoParents() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "abc1234|||(initial commit)|Alice|2025-01-01"));
         assertEquals(1, entries.size());
         GitLogEntry e = entries.get(0);
         assertEquals("abc1234", e.sha);
         assertTrue(e.parents.isEmpty());
         assertNull(e.decoration);
         assertEquals("(initial commit)", e.subject);
         assertEquals("Alice", e.author);
         assertEquals("2025-01-01", e.date);
      }

      @Test
      @DisplayName("commit with one parent")
      void oneParent() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "def5678|abc1234||Fix bug|Bob|2025-02-15"));
         assertEquals(1, entries.size());
         GitLogEntry e = entries.get(0);
         assertEquals(List.of("abc1234"), e.parents);
      }

      @Test
      @DisplayName("merge commit with two parents")
      void mergeCommit() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "aaa1111|bbb2222 ccc3333||Merge branch|Carol|2025-03-01"));
         GitLogEntry e = entries.get(0);
         assertEquals(List.of("bbb2222", "ccc3333"), e.parents);
      }

      @Test
      @DisplayName("decoration is extracted from parens")
      void decorationExtracted() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "fed9876|abc1234|(HEAD -> main, tag: v1.0)|Release|Dan|2025-04-01"));
         GitLogEntry e = entries.get(0);
         assertEquals("HEAD -> main, tag: v1.0", e.decoration);
      }

      @Test
      @DisplayName("empty decoration becomes null")
      void emptyDecorationIsNull() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "aaa1111|bbb2222||subject|author|date"));
         assertNull(entries.get(0).decoration);
      }

      @Test
      @DisplayName("empty lines are skipped")
      void emptyLinesSkipped() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "", "aaa1111|||subject|author|date", ""));
         assertEquals(1, entries.size());
      }

      @Test
      @DisplayName("malformed lines (too few fields) are skipped")
      void malformedLinesSkipped() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "not|enough|fields"));
         assertTrue(entries.isEmpty());
      }

      @Test
      @DisplayName("multiple commits parsed in order")
      void multipleCommits() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "aaa1111|||first|A|d1",
            "bbb2222|aaa1111||second|B|d2",
            "ccc3333|bbb2222||third|C|d3"));
         assertEquals(3, entries.size());
         assertEquals("aaa1111", entries.get(0).sha);
         assertEquals("bbb2222", entries.get(1).sha);
         assertEquals("ccc3333", entries.get(2).sha);
      }

      @Test
      @DisplayName("empty input returns empty list")
      void emptyInput() {
         List<GitLogEntry> entries =
            GitLogEntry.parse(Collections.emptyList());
         assertTrue(entries.isEmpty());
      }

      @Test
      @DisplayName("parents list is unmodifiable")
      void parentsUnmodifiable() {
         List<GitLogEntry> entries = GitLogEntry.parse(List.of(
            "aaa|bbb ccc||s|a|d"));
         try {
            entries.get(0).parents.add("zzz");
            // Should not reach here
            assertTrue(false, "parents should be unmodifiable");
         } catch (UnsupportedOperationException e) {
            // expected
         }
      }
   }

   @Nested
   @DisplayName("shortSha()")
   class ShortSha {

      @Test
      @DisplayName("long SHA truncated to 7 chars")
      void longShaTruncated() {
         GitLogEntry e = new GitLogEntry(
            "abcdef1234567890", Collections.emptyList(),
            "s", "a", "d", null);
         assertEquals("abcdef1", e.shortSha());
      }

      @Test
      @DisplayName("7-char SHA unchanged")
      void sevenCharShaUnchanged() {
         GitLogEntry e = new GitLogEntry(
            "abc1234", Collections.emptyList(),
            "s", "a", "d", null);
         assertEquals("abc1234", e.shortSha());
      }

      @Test
      @DisplayName("short SHA returned as-is")
      void shortShaReturnedAsIs() {
         GitLogEntry e = new GitLogEntry(
            "abc", Collections.emptyList(),
            "s", "a", "d", null);
         assertEquals("abc", e.shortSha());
      }
   }
}
