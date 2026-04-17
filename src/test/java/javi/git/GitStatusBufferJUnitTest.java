package javi.git;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GitStatusBuffer} — porcelain v2 output parser.
 */
class GitStatusBufferJUnitTest {

   private static List<String> format(String... lines) {
      return GitStatusBuffer.formatStatus(Arrays.asList(lines));
   }

   private static String findLine(List<String> lines, String prefix) {
      return lines.stream()
         .filter(l -> l.contains(prefix))
         .findFirst()
         .orElse(null);
   }

   // ── Branch header parsing ────────────────────────────────

   @Nested
   @DisplayName("branch header")
   class BranchHeader {

      @Test
      @DisplayName("parses branch name")
      void parsesBranchName() {
         List<String> out = format("# branch.head main");
         String headLine = findLine(out, "Head:");
         assertTrue(headLine.contains("main"));
      }

      @Test
      @DisplayName("parses oid and shows short SHA")
      void parsesOid() {
         List<String> out = format(
            "# branch.head main",
            "# branch.oid abcdef1234567890");
         String headLine = findLine(out, "Head:");
         assertTrue(headLine.contains("abcdef1"));
      }

      @Test
      @DisplayName("parses upstream and ahead/behind")
      void parsesUpstream() {
         List<String> out = format(
            "# branch.head feature",
            "# branch.oid abc1234",
            "# branch.upstream origin/feature",
            "# branch.ab +3 -1");
         String pushLine = findLine(out, "Push:");
         assertTrue(pushLine.contains("origin/feature"));
         assertTrue(pushLine.contains("ahead 3"));
         assertTrue(pushLine.contains("behind 1"));
      }

      @Test
      @DisplayName("up-to-date upstream")
      void upToDate() {
         List<String> out = format(
            "# branch.head main",
            "# branch.oid abc",
            "# branch.upstream origin/main",
            "# branch.ab +0 -0");
         String pushLine = findLine(out, "Push:");
         assertTrue(pushLine.contains("up to date"));
      }
   }

   // ── Change entries ───────────────────────────────────────

   @Nested
   @DisplayName("change entries")
   class ChangeEntries {

      @Test
      @DisplayName("staged modified file")
      void stagedModified() {
         List<String> out = format(
            "# branch.head main",
            "1 M. N... 100644 100644 100644"
               + " aaaa bbbb src/Foo.java");
         String stagedSection = findLine(out, "Staged changes");
         assertTrue(stagedSection.contains("1"));
         String modLine = findLine(out, "modified");
         assertTrue(modLine.contains("src/Foo.java"));
      }

      @Test
      @DisplayName("unstaged modified file")
      void unstagedModified() {
         List<String> out = format(
            "# branch.head main",
            "1 .M N... 100644 100644 100644"
               + " aaaa bbbb src/Bar.java");
         String section = findLine(out, "Unstaged changes");
         assertTrue(section.contains("1"));
      }

      @Test
      @DisplayName("new file in staging")
      void newFile() {
         List<String> out = format(
            "# branch.head main",
            "1 A. N... 000000 100644 100644"
               + " 0000 aaaa src/New.java");
         String line = findLine(out, "new file");
         assertTrue(line.contains("src/New.java"));
      }

      @Test
      @DisplayName("deleted file in staging")
      void deletedFile() {
         List<String> out = format(
            "# branch.head main",
            "1 D. N... 100644 000000 100644"
               + " aaaa 0000 src/Old.java");
         String line = findLine(out, "deleted");
         assertTrue(line.contains("src/Old.java"));
      }

      @Test
      @DisplayName("untracked file")
      void untrackedFile() {
         List<String> out = format(
            "# branch.head main",
            "? newfile.txt");
         String section = findLine(out, "Untracked files");
         assertTrue(section.contains("1"));
      }
   }

   // ── Empty status ─────────────────────────────────────────

   @Test
   @DisplayName("empty status has (none) in all sections")
   void emptyStatus() {
      List<String> out = format("# branch.head main");
      long noneCount = out.stream()
         .filter(l -> l.contains("(none)"))
         .count();
      assertEquals(3, noneCount); // staged, unstaged, untracked
   }

   // ── Rename entry ─────────────────────────────────────────

   @Test
   @DisplayName("type-2 rename entry shows arrow")
   void renameEntry() {
      List<String> out = format(
         "# branch.head main",
         "2 R. N... 100644 100644 100644"
            + " aaaa bbbb R100 new.java\told.java");
      String line = findLine(out, "renamed");
      assertTrue(line.contains("->"));
   }

   // ── Help keys section ────────────────────────────────────

   @Test
   @DisplayName("output includes keybinding help")
   void keybindingHelp() {
      List<String> out = format("# branch.head main");
      String helpLine = findLine(out, "Keys:");
      assertTrue(helpLine.contains("s=stage"));
   }
}
