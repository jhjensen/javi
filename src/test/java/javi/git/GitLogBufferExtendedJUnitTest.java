package javi.git;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended tests for {@link GitLogBuffer} — buildFoldedLog, formatLog,
 * and findMessage logic.
 */
class GitLogBufferExtendedJUnitTest {

   // ── formatLog ────────────────────────────────────────────

   @Nested
   @DisplayName("formatLog()")
   class FormatLog {

      @Test
      @DisplayName("includes header lines before content")
      void includesHeader() {
         List<String> result = GitLogBuffer.formatLog(
            Arrays.asList("* abc1234 First commit"));
         assertTrue(result.size() > 1);
         assertEquals("Git Log", result.get(0));
         assertTrue(result.get(1).contains("Enter"));
         assertTrue(result.get(2).contains("Fold"));
         assertEquals("======================", result.get(3));
         assertEquals("", result.get(4));
      }

      @Test
      @DisplayName("appends all log lines after header")
      void appendsLogLines() {
         List<String> input = Arrays.asList(
            "* abc1234 First",
            "* def5678 Second",
            "* 9876543 Third");
         List<String> result = GitLogBuffer.formatLog(input);
         // Header is 5 lines, then 3 log lines
         assertEquals(8, result.size());
         assertEquals("* abc1234 First", result.get(5));
         assertEquals("* def5678 Second", result.get(6));
         assertEquals("* 9876543 Third", result.get(7));
      }

      @Test
      @DisplayName("empty input produces only header")
      void emptyInput() {
         List<String> result = GitLogBuffer.formatLog(
            Collections.emptyList());
         assertEquals(5, result.size());
         assertEquals("Git Log", result.get(0));
      }
   }

   // ── buildFoldedLog ───────────────────────────────────────

   @Nested
   @DisplayName("buildFoldedLog()")
   class BuildFoldedLog {

      @Test
      @DisplayName("empty input produces header plus pagination")
      void emptyInput() {
         List<int[]> foldRanges = new ArrayList<>();
         List<String> result = GitLogBuffer.buildFoldedLog(
            Collections.emptyList(),
            Collections.emptyMap(),
            foldRanges,
            null,
            null);
         assertTrue(result.size() >= 5);
         assertEquals("Git Log", result.get(0));
         // Pagination sentinel at end
         assertTrue(result.get(result.size() - 2)
            .contains("--- more ---"));
         // At least the pagination fold range
         assertFalse(foldRanges.isEmpty());
      }

      @Test
      @DisplayName("non-commit lines passed through without folds")
      void nonCommitLines() {
         List<String> logLines = Arrays.asList(
            "| |", "| /", "|/");
         List<int[]> foldRanges = new ArrayList<>();
         List<String> result = GitLogBuffer.buildFoldedLog(
            logLines,
            Collections.emptyMap(),
            foldRanges,
            null,
            null);
         // 5 header + 3 lines + pagination (2 lines)
         assertEquals(10, result.size());
         assertEquals("| |", result.get(5));
         assertEquals("| /", result.get(6));
         assertEquals("|/", result.get(7));
         // Only pagination fold range
         assertEquals(1, foldRanges.size());
      }

      @Test
      @DisplayName("commit line generates message + diff sub-fold")
      void commitLineWithMessage() {
         List<String> logLines = Arrays.asList(
            "* abc1234 Fix bug");
         Map<String, List<String>> messages = new LinkedHashMap<>();
         messages.put("abc1234", Arrays.asList(
            "  Author: Alice",
            "  Date:   2024-01-01",
            "  ",
            "  Fix bug"));
         List<int[]> foldRanges = new ArrayList<>();
         List<String> result = GitLogBuffer.buildFoldedLog(
            logLines,
            messages,
            foldRanges,
            null,
            null);
         // Should have header(5) + graph(1) + msg(4) + diff-show(2) + pagination(2) = 14
         assertTrue(result.contains("* abc1234 Fix bug"));
         assertTrue(result.contains("  Author: Alice"));
         // Should have a DIFF_SHOW_PREFIX line
         boolean hasShowMarker = result.stream()
            .anyMatch(l -> l.startsWith(
               GitLogBuffer.DIFF_SHOW_PREFIX));
         assertTrue(hasShowMarker);
         // 2 fold ranges for commit (diff sub-fold + commit fold)
         // plus 1 for pagination = 3
         assertEquals(3, foldRanges.size());
      }

      @Test
      @DisplayName("commit without message shows fallback text")
      void commitWithoutMessage() {
         List<String> logLines = Arrays.asList(
            "* abc1234 Fix bug");
         List<int[]> foldRanges = new ArrayList<>();
         List<String> result = GitLogBuffer.buildFoldedLog(
            logLines,
            Collections.emptyMap(),
            foldRanges,
            null,
            null);
         boolean hasFallback = result.stream()
            .anyMatch(l -> l.contains("git_show"));
         assertTrue(hasFallback,
            "should show fallback text for missing message");
      }

      @Test
      @DisplayName("expanded diff includes diff lines")
      void expandedDiff() {
         List<String> logLines = Arrays.asList(
            "* abc1234 Fix bug");
         Map<String, List<String>> messages = new LinkedHashMap<>();
         messages.put("abc1234", Arrays.asList("  Fix bug"));

         Set<String> expanded = new HashSet<>();
         expanded.add("abc1234");

         Map<String, List<String>> diffCache = new HashMap<>();
         diffCache.put("abc1234", Arrays.asList(
            "diff --git a/foo.java b/foo.java",
            "--- a/foo.java",
            "+++ b/foo.java",
            "@@ -1,3 +1,3 @@",
            "-old line",
            "+new line"));

         List<int[]> foldRanges = new ArrayList<>();
         List<String> result = GitLogBuffer.buildFoldedLog(
            logLines,
            messages,
            foldRanges,
            expanded,
            diffCache);
         // Should have DIFF_HIDE_PREFIX marker
         boolean hasHideMarker = result.stream()
            .anyMatch(l -> l.startsWith(
               GitLogBuffer.DIFF_HIDE_PREFIX));
         assertTrue(hasHideMarker);
         // Should have diff content with "  | " prefix
         boolean hasDiffLine = result.stream()
            .anyMatch(l -> l.contains("  | diff --git"));
         assertTrue(hasDiffLine);
         // Should have closing separator
         boolean hasSeparator = result.stream()
            .anyMatch(l -> l.contains("+--------"));
         assertTrue(hasSeparator);
      }

      @Test
      @DisplayName("multiple commits create multiple fold ranges")
      void multipleCommits() {
         List<String> logLines = Arrays.asList(
            "* abc1234 First",
            "* def5678 Second");
         Map<String, List<String>> messages = new LinkedHashMap<>();
         messages.put("abc1234", Arrays.asList("  First"));
         messages.put("def5678", Arrays.asList("  Second"));

         List<int[]> foldRanges = new ArrayList<>();
         List<String> result = GitLogBuffer.buildFoldedLog(
            logLines,
            messages,
            foldRanges,
            null,
            null);
         // 2 commits × 2 folds each + 1 pagination = 5
         assertEquals(5, foldRanges.size());
      }

      @Test
      @DisplayName("pagination sentinel appears at end")
      void paginationSentinel() {
         List<String> logLines = Arrays.asList(
            "* abc1234 Commit");
         Map<String, List<String>> messages = new LinkedHashMap<>();
         messages.put("abc1234", Arrays.asList("  Commit"));

         List<int[]> foldRanges = new ArrayList<>();
         List<String> result = GitLogBuffer.buildFoldedLog(
            logLines,
            messages,
            foldRanges,
            null,
            null);
         String lastContent = result.get(result.size() - 2);
         assertEquals(GitLogBuffer.PAGINATION_MARKER, lastContent);
         String sentinelBody = result.get(result.size() - 1);
         assertTrue(sentinelBody.contains("graph lines shown"));
      }

      @Test
      @DisplayName("fold ranges are valid line numbers")
      void foldRangesValid() {
         List<String> logLines = Arrays.asList(
            "* abc1234 First",
            "| |",
            "* def5678 Second");
         Map<String, List<String>> messages = new LinkedHashMap<>();
         messages.put("abc1234", Arrays.asList("  First"));
         messages.put("def5678", Arrays.asList("  Second"));

         List<int[]> foldRanges = new ArrayList<>();
         List<String> result = GitLogBuffer.buildFoldedLog(
            logLines,
            messages,
            foldRanges,
            null,
            null);
         for (int[] range : foldRanges) {
            assertTrue(range[0] >= 1,
               "start line should be >= 1");
            assertTrue(range[1] >= range[0],
               "end should be >= start");
            assertTrue(range[1] <= result.size(),
               "end should be <= total lines");
         }
      }

      @Test
      @DisplayName("expanded diff with null cache shows no diff lines")
      void expandedDiffNullCache() {
         List<String> logLines = Arrays.asList(
            "* abc1234 Fix");
         Map<String, List<String>> messages = new LinkedHashMap<>();
         messages.put("abc1234", Arrays.asList("  Fix"));

         Set<String> expanded = new HashSet<>();
         expanded.add("abc1234");

         List<int[]> foldRanges = new ArrayList<>();
         List<String> result = GitLogBuffer.buildFoldedLog(
            logLines,
            messages,
            foldRanges,
            expanded,
            null);
         // Should have DIFF_HIDE marker but no diff content
         boolean hasHideMarker = result.stream()
            .anyMatch(l -> l.startsWith(
               GitLogBuffer.DIFF_HIDE_PREFIX));
         assertTrue(hasHideMarker);
      }
   }

   // ── findMessage (private, via reflection) ────────────────

   @Nested
   @DisplayName("findMessage()")
   class FindMessage {

      private List<String> invokeFindMessage(String sha,
            Map<String, List<String>> messages) throws Exception {
         Method m = GitLogBuffer.class.getDeclaredMethod(
            "findMessage", String.class, Map.class);
         m.setAccessible(true);
         @SuppressWarnings("unchecked")
         List<String> result =
            (List<String>) m.invoke(null, sha, messages);
         return result;
      }

      @Test
      @DisplayName("exact match returns message")
      void exactMatch() throws Exception {
         Map<String, List<String>> msgs = new LinkedHashMap<>();
         msgs.put("abc1234", Arrays.asList("  msg"));
         List<String> result = invokeFindMessage("abc1234", msgs);
         assertNotNull(result);
         assertEquals("  msg", result.get(0));
      }

      @Test
      @DisplayName("prefix match: short SHA matches longer key")
      void prefixMatch() throws Exception {
         Map<String, List<String>> msgs = new LinkedHashMap<>();
         msgs.put("abc1234def", Arrays.asList("  found"));
         List<String> result = invokeFindMessage("abc1234", msgs);
         assertNotNull(result);
         assertEquals("  found", result.get(0));
      }

      @Test
      @DisplayName("key prefix matches longer SHA query")
      void keyPrefixMatch() throws Exception {
         Map<String, List<String>> msgs = new LinkedHashMap<>();
         msgs.put("abc1234", Arrays.asList("  found"));
         List<String> result =
            invokeFindMessage("abc1234def567", msgs);
         assertNotNull(result);
      }

      @Test
      @DisplayName("no match returns null")
      void noMatch() throws Exception {
         Map<String, List<String>> msgs = new LinkedHashMap<>();
         msgs.put("abc1234", Arrays.asList("  msg"));
         List<String> result = invokeFindMessage("zzz9999", msgs);
         assertNull(result);
      }

      @Test
      @DisplayName("empty map returns null")
      void emptyMap() throws Exception {
         List<String> result = invokeFindMessage("abc1234",
            Collections.emptyMap());
         assertNull(result);
      }
   }
}
