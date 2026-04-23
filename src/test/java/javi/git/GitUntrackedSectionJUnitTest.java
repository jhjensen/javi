package javi.git;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the F9 untracked-files inclusion in the commit / staging
 * views (Bug 1: new untracked files were not appearing in the commit
 * view, so users could not stage them).
 *
 * <p>{@link GitCommitView#buildView} and {@link GitCommitView#buildStagingView}
 * shell out to git via {@link GitProcess}, which makes them awkward
 * to exercise in a unit test (they use the JVM CWD).  These tests
 * instead validate the structural contract that lets the untracked
 * section coexist with the diff parser:</p>
 *
 * <ul>
 *   <li>{@link GitCommitView#parseStagingViewHunks} stops at the
 *       untracked separator and does not mis-parse untracked lines
 *       as part of the diff.</li>
 *   <li>{@link GitCommitView#parseViewHunks} does the same in the
 *       combined commit view.</li>
 *   <li>{@link GitCommands#extractFilenameFromLine} recognises bare
 *       untracked filenames (the format {@code buildView} writes for
 *       new files), so {@code git_goto_file} ({@code ^]}) can open
 *       them.</li>
 * </ul>
 */
class GitUntrackedSectionJUnitTest {

   private static List<String> lines(String... lines) {
      return Arrays.asList(lines);
   }

   private static final String DIFF_HEADER_A =
      "diff --git a/Foo.java b/Foo.java";
   private static final String DIFF_INDEX_A =
      "index 1234567..abcdef0 100644";
   private static final String DIFF_OLD_A = "--- a/Foo.java";
   private static final String DIFF_NEW_A = "+++ b/Foo.java";
   private static final String HUNK_A = "@@ -1,3 +1,4 @@";

   // ----- parseStagingViewHunks: untracked section is ignored

   @Nested
   @DisplayName("staging view ignores untracked section")
   class StagingViewParser {

      @Test
      @DisplayName("hunk parsed; untracked filenames excluded")
      void hunkParsedUntrackedExcluded() {
         List<String> view = lines(
            GitCommitView.STAGED_SEPARATOR,
            "#   (no staged changes)",
            "#",
            GitCommitView.UNSTAGED_SEPARATOR,
            DIFF_HEADER_A,
            DIFF_INDEX_A,
            DIFF_OLD_A,
            DIFF_NEW_A,
            HUNK_A,
            " ctx",
            "-old",
            "+new",
            "+added",
            "",
            GitCommitView.UNTRACKED_SEPARATOR,
            "  NewFile.java",
            "  another.txt");
         List<GitHunkStaging.Hunk> hunks =
            GitCommitView.parseStagingViewHunks(view);
         assertEquals(1, hunks.size(),
            "exactly one hunk should be parsed from the diff");
         GitHunkStaging.Hunk h = hunks.get(0);
         // Untracked filenames must not appear in the hunk body.
         for (String body : h.body) {
            assertTrue(!body.contains("NewFile.java"),
               "untracked filename leaked into hunk body: " + body);
            assertTrue(!body.contains("another.txt"),
               "untracked filename leaked into hunk body: " + body);
         }
      }

      @Test
      @DisplayName("no hunks when only untracked section present")
      void onlyUntracked() {
         List<String> view = lines(
            GitCommitView.STAGED_SEPARATOR,
            "#   (no staged changes)",
            "#",
            GitCommitView.UNSTAGED_SEPARATOR,
            "#   (no unstaged changes)",
            "",
            GitCommitView.UNTRACKED_SEPARATOR,
            "  NewFile.java");
         List<GitHunkStaging.Hunk> hunks =
            GitCommitView.parseStagingViewHunks(view);
         assertEquals(0, hunks.size());
      }
   }

   // ----- parseViewHunks: combined commit view also stops at untracked

   @Nested
   @DisplayName("combined commit view ignores untracked section")
   class CommitViewParser {

      @Test
      @DisplayName("untracked filenames excluded from hunks")
      void untrackedExcluded() {
         List<String> view = lines(
            "WIP commit message",
            "# instruction",
            "#",
            GitCommitView.STAGED_SEPARATOR,
            "#   (no staged changes)",
            "#",
            GitCommitView.UNSTAGED_SEPARATOR,
            DIFF_HEADER_A,
            DIFF_INDEX_A,
            DIFF_OLD_A,
            DIFF_NEW_A,
            HUNK_A,
            " ctx",
            "-old",
            "+new",
            "+added",
            "",
            GitCommitView.UNTRACKED_SEPARATOR,
            "  brand-new.java");
         List<GitHunkStaging.Hunk> hunks =
            GitCommitView.parseViewHunks(view);
         assertEquals(1, hunks.size());
         for (String body : hunks.get(0).body) {
            assertTrue(!body.contains("brand-new.java"),
               "untracked file leaked into hunk: " + body);
         }
      }
   }

   // ----- extractFilenameFromLine: bare untracked filenames are read

   @Nested
   @DisplayName("untracked filename extraction (^] navigation)")
   class FilenameExtraction {

      @Test
      @DisplayName("bare two-space-indented filename is returned")
      void bareUntrackedFile() {
         String line = "  NewFile.java";
         assertEquals("NewFile.java",
            GitCommands.extractFilenameFromLine(line));
      }

      @Test
      @DisplayName("untracked file with subdirectory")
      void untrackedFileInSubdir() {
         String line = "  src/main/java/foo/NewFile.java";
         assertEquals("src/main/java/foo/NewFile.java",
            GitCommands.extractFilenameFromLine(line));
      }

      @Test
      @DisplayName("placeholder line '(no untracked files)' rejected")
      void noUntrackedPlaceholder() {
         String line = "  (no untracked files)";
         assertNull(GitCommands.extractFilenameFromLine(line),
            "placeholder must not be treated as a filename");
      }

      @Test
      @DisplayName("modified-line still parsed (not regressed)")
      void modifiedLine() {
         String line = "  modified    src/Foo.java";
         assertEquals("src/Foo.java",
            GitCommands.extractFilenameFromLine(line));
      }

      @Test
      @DisplayName("renamed-line returns the new name")
      void renamedLine() {
         String line =
            "  renamed    src/Old.java -> src/New.java";
         assertEquals("src/New.java",
            GitCommands.extractFilenameFromLine(line));
      }

      @Test
      @DisplayName("non-file line returns null")
      void nonFileLine() {
         assertNull(
            GitCommands.extractFilenameFromLine("# heading"));
         assertNull(GitCommands.extractFilenameFromLine(""));
         assertNull(GitCommands.extractFilenameFromLine("no indent"));
      }
   }

   // ----- separator constant sanity

   @Test
   @DisplayName("UNTRACKED_SEPARATOR constant has a stable value")
   void separatorConstant() {
      assertEquals("# --- untracked files ---",
         GitCommitView.UNTRACKED_SEPARATOR);
   }
}
