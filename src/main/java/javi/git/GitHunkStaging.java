package javi.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive hunk staging — parse unified diffs into individual
 * hunks and apply selected hunks to the staging area.
 *
 * <p>A "hunk" is a contiguous block of changes in a unified diff,
 * delimited by {@code @@} header lines. This class lets the user
 * stage or unstage individual hunks instead of whole files.</p>
 */
public final class GitHunkStaging {

   /** Private constructor to prevent instantiation. */
   private GitHunkStaging() {
   }

   /**
    * A parsed hunk from a unified diff, including the file
    * header lines needed to construct a valid patch.
    */
   static final class Hunk {
      /** File header lines (diff --git, index, ---, +++). */
      final List<String> header;
      /** The @@ line and all context/change lines. */
      final List<String> body;
      /** Zero-based index of this hunk within its file. */
      final int index;
      /** Buffer line number where this hunk's @@ line appears. */
      final int bufferLine;

      Hunk(List<String> hdrLines, List<String> bodyLines,
            int hunkIndex, int startLine) {
         this.header = hdrLines;
         this.body = bodyLines;
         this.index = hunkIndex;
         this.bufferLine = startLine;
      }

      /**
       * Reconstruct a minimal patch that applies just this hunk.
       *
       * @return the patch content as a string with trailing newline
       */
      String toPatch() {
         StringBuilder sb = new StringBuilder();
         for (String h : header) {
            sb.append(h).append('\n');
         }
         for (String b : body) {
            sb.append(b).append('\n');
         }
         return sb.toString();
      }
   }

   /**
    * Parse unified diff output into individual hunks.
    *
    * <p>Each hunk carries its file header so it can be applied
    * independently. The {@code bufferLine} field records the
    * 1-based buffer line where each hunk's {@code @@} appears.</p>
    *
    * @param diffLines output from {@code git diff} or
    *                  {@code git diff --cached}
    * @return list of parsed hunks
    */
   static List<Hunk> parseHunks(List<String> diffLines) {
      List<Hunk> hunks = new ArrayList<>();
      List<String> currentHeader = new ArrayList<>();
      List<String> currentBody = null;
      int hunkIndex = 0;
      int hunkStartLine = 0;

      for (int i = 0; i < diffLines.size(); i++) {
         String line = diffLines.get(i);

         if (line.startsWith("diff --git ")) {
            // New file — flush any pending hunk
            if (currentBody != null && !currentBody.isEmpty()) {
               hunks.add(new Hunk(
                  new ArrayList<>(currentHeader),
                  currentBody, hunkIndex, hunkStartLine));
            }
            currentHeader.clear();
            currentHeader.add(line);
            currentBody = null;
            hunkIndex = 0;
         } else if (currentBody == null
               && (line.startsWith("index ")
                  || line.startsWith("--- ")
                  || line.startsWith("+++ ")
                  || line.startsWith("old mode ")
                  || line.startsWith("new mode ")
                  || line.startsWith("new file mode ")
                  || line.startsWith("deleted file mode ")
                  || line.startsWith("similarity index ")
                  || line.startsWith("rename from ")
                  || line.startsWith("rename to ")
                  || line.startsWith("Binary files "))) {
            currentHeader.add(line);
         } else if (line.startsWith("@@ ")) {
            // New hunk within current file
            if (currentBody != null && !currentBody.isEmpty()) {
               hunks.add(new Hunk(
                  new ArrayList<>(currentHeader),
                  currentBody, hunkIndex, hunkStartLine));
               hunkIndex++;
            }
            currentBody = new ArrayList<>();
            currentBody.add(line);
            hunkStartLine = i + 1; // 1-based
         } else if (currentBody != null) {
            currentBody.add(line);
         }
      }
      // Flush last hunk
      if (currentBody != null && !currentBody.isEmpty()) {
         hunks.add(new Hunk(
            new ArrayList<>(currentHeader),
            currentBody, hunkIndex, hunkStartLine));
      }
      return hunks;
   }

   /**
    * Find the hunk that contains the given buffer line.
    *
    * @param hunks list of parsed hunks
    * @param bufferLine 1-based buffer line number
    * @return the containing hunk, or null if not in a hunk
    */
   static Hunk findHunkAtLine(List<Hunk> hunks, int bufferLine) {
      Hunk best = null;
      for (Hunk h : hunks) {
         if (h.bufferLine <= bufferLine) {
            int hunkEnd = h.bufferLine + h.body.size() - 1;
            if (bufferLine <= hunkEnd) {
               return h;
            }
            // Cursor may be past last hunk's @@ line but
            // within its body range
            best = h;
         }
      }
      // If cursor is past the last hunk's @@ but within body range
      if (best != null) {
         int hunkEnd = best.bufferLine + best.body.size() - 1;
         if (bufferLine <= hunkEnd)
            return best;
      }
      return null;
   }

   /**
    * Stage a single hunk by piping its patch to
    * {@code git apply --cached}.
    *
    * @param hunk the hunk to stage
    * @return null on success, or an error message
    * @throws IOException if git command fails
    */
   static String stageHunk(Hunk hunk) throws IOException {
      String patch = hunk.toPatch();
      GitProcess.Result res = GitProcess.executeWithStdin(
         patch, "apply", "--cached");
      if (0 == res.exitCode) {
         return null;
      }
      return res.output.isEmpty()
         ? "Failed to stage hunk"
         : String.join(" ", res.output);
   }

   /**
    * Unstage a single hunk by piping its reverse patch to
    * {@code git apply --cached --reverse}.
    *
    * @param hunk the hunk to unstage
    * @return null on success, or an error message
    * @throws IOException if git command fails
    */
   static String unstageHunk(Hunk hunk) throws IOException {
      String patch = hunk.toPatch();
      GitProcess.Result res = GitProcess.executeWithStdin(
         patch, "apply", "--cached", "--reverse");
      if (0 == res.exitCode) {
         return null;
      }
      return res.output.isEmpty()
         ? "Failed to unstage hunk"
         : String.join(" ", res.output);
   }

   /**
    * Get the diff for a single file (unstaged changes).
    *
    * @param filepath the file path relative to repo root
    * @return diff output lines
    * @throws IOException if git command fails
    */
   static List<String> getFileDiff(String filepath)
         throws IOException {
      return GitProcess.execute("diff", filepath);
   }

   /**
    * Get the staged diff for a single file.
    *
    * @param filepath the file path relative to repo root
    * @return diff output lines
    * @throws IOException if git command fails
    */
   static List<String> getStagedFileDiff(String filepath)
         throws IOException {
      return GitProcess.execute("diff", "--cached", filepath);
   }

   /**
    * Format a hunk-annotated diff for display in a buffer.
    * Adds hunk number markers before each {@code @@} line.
    *
    * @param diffLines raw diff output
    * @param hunks parsed hunks
    * @return formatted lines with hunk markers
    */
   static List<String> formatAnnotatedDiff(
         List<String> diffLines, List<Hunk> hunks) {
      List<String> result = new ArrayList<>();
      result.add("Diff (s=stage hunk  u=unstage hunk  q=quit)");
      result.add("");
      result.addAll(diffLines);
      return result;
   }
}
