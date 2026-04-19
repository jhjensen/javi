package javi.git;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended tests for {@link GitStatusBuffer} — edge cases in
 * porcelain v2 parsing and formatting.
 */
class GitStatusBufferExtendedJUnitTest {

   // ── Branch metadata ──────────────────────────────────────

   @Nested
   @DisplayName("branch metadata parsing")
   class BranchMetadata {

      @Test
      @DisplayName("ahead only shows ahead count")
      void aheadOnly() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678901234567890abcdef12345678",
            "# branch.upstream origin/main",
            "# branch.ab +3 -0");
         List<String> result = GitStatusBuffer.formatStatus(raw);
         String pushLine = result.stream()
            .filter(l -> l.startsWith("Push:"))
            .findFirst().orElse("");
         assertTrue(pushLine.contains("ahead 3"));
         assertTrue(!pushLine.contains("behind"));
      }

      @Test
      @DisplayName("behind only shows behind count")
      void behindOnly() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678901234567890abcdef12345678",
            "# branch.upstream origin/main",
            "# branch.ab +0 -5");
         List<String> result = GitStatusBuffer.formatStatus(raw);
         String pushLine = result.stream()
            .filter(l -> l.startsWith("Push:"))
            .findFirst().orElse("");
         assertTrue(pushLine.contains("behind 5"));
         assertTrue(!pushLine.contains("ahead"));
      }

      @Test
      @DisplayName("up to date shows (up to date)")
      void upToDate() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678901234567890abcdef12345678",
            "# branch.upstream origin/main",
            "# branch.ab +0 -0");
         List<String> result = GitStatusBuffer.formatStatus(raw);
         String pushLine = result.stream()
            .filter(l -> l.startsWith("Push:"))
            .findFirst().orElse("");
         assertTrue(pushLine.contains("up to date"));
      }

      @Test
      @DisplayName("no upstream omits push line")
      void noUpstream() {
         List<String> raw = Arrays.asList(
            "# branch.head feature-x",
            "# branch.oid abc1234def5678901234567890abcdef12345678");
         List<String> result = GitStatusBuffer.formatStatus(raw);
         boolean hasPush = result.stream()
            .anyMatch(l -> l.startsWith("Push:"));
         assertTrue(!hasPush);
      }

      @Test
      @DisplayName("OID truncated to 7 chars")
      void oidTruncated() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678901234567890abcdef12345678");
         List<String> result = GitStatusBuffer.formatStatus(raw);
         String headLine = result.stream()
            .filter(l -> l.startsWith("Head:"))
            .findFirst().orElse("");
         assertTrue(headLine.contains("abc1234"));
         assertTrue(!headLine.contains("abc1234d"));
      }
   }

   // ── Changed entries ──────────────────────────────────────

   @Nested
   @DisplayName("changed entry parsing")
   class ChangedEntries {

      @Test
      @DisplayName("both staged and unstaged modifications")
      void bothStagedAndUnstaged() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678901234567890abcdef12345678",
            "1 MM N... 100644 100644 100644 "
               + "aaaa bbbb src/Foo.java");
         List<String> result = GitStatusBuffer.formatStatus(raw);
         // Should appear in both staged and unstaged
         long stagedCount = result.stream()
            .filter(l -> l.contains("modified")
               && l.contains("src/Foo.java"))
            .count();
         assertEquals(2, stagedCount);
      }

      @Test
      @DisplayName("new file (added)")
      void newFile() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678901234567890abcdef12345678",
            "1 A. N... 000000 100644 100644 "
               + "0000 aaaa src/New.java");
         List<String> result = GitStatusBuffer.formatStatus(raw);
         boolean hasNew = result.stream()
            .anyMatch(l -> l.contains("new file")
               && l.contains("src/New.java"));
         assertTrue(hasNew);
      }

      @Test
      @DisplayName("deleted file")
      void deletedFile() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678901234567890abcdef12345678",
            "1 D. N... 100644 000000 000000 "
               + "aaaa 0000 src/Old.java");
         List<String> result = GitStatusBuffer.formatStatus(raw);
         boolean hasDeleted = result.stream()
            .anyMatch(l -> l.contains("deleted")
               && l.contains("src/Old.java"));
         assertTrue(hasDeleted);
      }

      @Test
      @DisplayName("rename entry (type 2) shows arrow")
      void renameEntry() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678901234567890abcdef12345678",
            "2 R. N... 100644 100644 100644 "
               + "aaaa bbbb R100 new.java\told.java");
         List<String> result = GitStatusBuffer.formatStatus(raw);
         boolean hasRename = result.stream()
            .anyMatch(l -> l.contains("renamed")
               && l.contains("->")
               && l.contains("old.java"));
         assertTrue(hasRename);
      }

      @Test
      @DisplayName("unmerged entry")
      void unmergedEntry() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678901234567890abcdef12345678",
            "u UU N... 100644 100644 100644 100644 "
               + "aaaa bbbb cccc src/Conflict.java");
         List<String> result = GitStatusBuffer.formatStatus(raw);
         boolean hasUnmerged = result.stream()
            .anyMatch(l -> l.contains("unmerged")
               && l.contains("src/Conflict.java"));
         assertTrue(hasUnmerged);
      }

      @Test
      @DisplayName("untracked file")
      void untrackedFile() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678901234567890abcdef12345678",
            "? build/output.log");
         List<String> result = GitStatusBuffer.formatStatus(raw);
         boolean hasUntracked = result.stream()
            .anyMatch(l -> l.contains("build/output.log"));
         assertTrue(hasUntracked);
      }
   }

   // ── Section formatting ───────────────────────────────────

   @Nested
   @DisplayName("section formatting")
   class SectionFormatting {

      @Test
      @DisplayName("empty status shows (none) in all sections")
      void emptyStatus() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678901234567890abcdef12345678");
         List<String> result = GitStatusBuffer.formatStatus(raw);
         long noneCount = result.stream()
            .filter(l -> l.contains("(none)"))
            .count();
         assertEquals(3, noneCount);
      }

      @Test
      @DisplayName("section headers show counts")
      void sectionHeaderCounts() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678901234567890abcdef12345678",
            "1 M. N... 100644 100644 100644 aaaa bbbb a.java",
            "1 .M N... 100644 100644 100644 aaaa bbbb b.java",
            "1 .M N... 100644 100644 100644 aaaa bbbb c.java",
            "? new.txt");
         List<String> result = GitStatusBuffer.formatStatus(raw);
         boolean hasStagedCount = result.stream()
            .anyMatch(l -> l.contains("Staged changes (1)"));
         boolean hasUnstagedCount = result.stream()
            .anyMatch(l -> l.contains("Unstaged changes (2)"));
         boolean hasUntrackedCount = result.stream()
            .anyMatch(l -> l.contains("Untracked files (1)"));
         assertTrue(hasStagedCount, "staged count should be 1");
         assertTrue(hasUnstagedCount, "unstaged count should be 2");
         assertTrue(hasUntrackedCount,
            "untracked count should be 1");
      }

      @Test
      @DisplayName("key bindings help appears at end")
      void keyBindingsHelp() {
         List<String> result = GitStatusBuffer.formatStatus(
            Arrays.asList(
               "# branch.head main",
               "# branch.oid abc1234def"));
         String lastFew = String.join("\n",
            result.subList(result.size() - 4, result.size()));
         assertTrue(lastFew.contains("s=stage"));
         assertTrue(lastFew.contains("git_commit"));
      }

      @Test
      @DisplayName("ahead and behind shows both")
      void aheadAndBehind() {
         List<String> raw = Arrays.asList(
            "# branch.head main",
            "# branch.oid abc1234def5678901234567890abcdef12345678",
            "# branch.upstream origin/main",
            "# branch.ab +2 -3");
         List<String> result = GitStatusBuffer.formatStatus(raw);
         String pushLine = result.stream()
            .filter(l -> l.startsWith("Push:"))
            .findFirst().orElse("");
         assertTrue(pushLine.contains("ahead 2"));
         assertTrue(pushLine.contains("behind 3"));
      }
   }
}
