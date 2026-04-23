package javi.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static history.Tools.trace;

/**
 * Builds and manages the combined commit view buffer.
 *
 * <p>The commit view shows an editable commit message area at
 * the top, followed by staged files (as {@code #} comments)
 * and unstaged changes with inline diff hunks. The user edits
 * the message in insert mode and stages hunks in normal mode
 * using single-key commands.</p>
 *
 * <p>Buffer layout:</p>
 * <pre>
 * (editable message area)
 * # Enter commit message above, then :git_do_commit
 * # Lines starting with '#' will be ignored.
 * #
 * # --- staged files ---
 * #   file1.java | 3 +-
 * #   file2.java | 10 ++++++
 * #
 * # --- unstaged changes ---
 * diff --git a/file3.java b/file3.java
 * --- a/file3.java
 * +++ b/file3.java
 * @@ -10,6 +10,8 @@
 *  context
 * +added line
 *
 * # s=stage hunk  u=unstage  ^]=goto file  q=quit
 * </pre>
 */
public final class GitCommitView {

   /** Separator marking the start of the staged files section. */
   static final String STAGED_SEPARATOR = "# --- staged files ---";

   /** Separator marking the start of unstaged changes. */
   static final String UNSTAGED_SEPARATOR =
      "# --- unstaged changes ---";

   /** Separator marking the start of untracked files. */
   static final String UNTRACKED_SEPARATOR =
      "# --- untracked files ---";

   /** Private constructor to prevent instantiation. */
   private GitCommitView() {
   }

   /**
    * Build the staging-only view for the split-view commit workflow.
    * Shows staged files and unstaged changes with inline diff hunks.
    * No message area — the message lives in a separate buffer.
    *
    * @return the staging view buffer lines
    * @throws IOException if git commands fail
    */
   static List<String> buildStagingView() throws IOException {
      List<String> lines = new ArrayList<>();

      // Staged section
      List<String> staged = GitProcess.execute(
         "diff", "--cached", "--stat");
      lines.add(STAGED_SEPARATOR);
      if (staged.isEmpty()) {
         lines.add("#   (no staged changes)");
      } else {
         for (String s : staged) {
            lines.add("#   " + s);
         }
      }
      lines.add("#");

      // Unstaged section with full diff
      lines.add(UNSTAGED_SEPARATOR);
      List<String> diff = GitProcess.execute("diff");
      if (diff.isEmpty()) {
         lines.add("#   (no unstaged changes)");
      } else {
         lines.addAll(diff);
      }
      lines.add("");

      // Untracked files section
      List<String> untracked = GitProcess.execute(
         "ls-files", "--others", "--exclude-standard");
      lines.add(UNTRACKED_SEPARATOR);
      if (untracked.isEmpty()) {
         lines.add("#   (no untracked files)");
      } else {
         for (String f : untracked) {
            lines.add("  " + f);
         }
      }

      lines.add("");
      lines.add("# s=stage hunk  u=unstage  ^]=goto file"
         + "  q=quit");

      return lines;
   }

   /**
    * Parse hunks from the staging view buffer.
    * The staging view has no message preamble, so positions
    * are simpler than the combined view.
    *
    * @param viewLines the staging view buffer lines
    * @return parsed hunks with correct buffer-line positions
    */
   static List<GitHunkStaging.Hunk> parseStagingViewHunks(
         List<String> viewLines) {
      // Find where the unstaged diff starts
      int diffStart = -1;
      for (int i = 0; i < viewLines.size(); i++) {
         if (viewLines.get(i).equals(UNSTAGED_SEPARATOR)) {
            diffStart = i + 1;
            break;
         }
      }
      if (diffStart < 0 || diffStart >= viewLines.size())
         return new ArrayList<>();

      // Extract just the diff lines (stop at untracked section)
      List<String> diffLines = new ArrayList<>();
      for (int i = diffStart; i < viewLines.size(); i++) {
         String line = viewLines.get(i);
         if (line.equals(UNTRACKED_SEPARATOR))
            break;
         if (line.startsWith("# ") && line.contains("stage"))
            break;
         if (line.isEmpty()
               && i + 1 < viewLines.size()
               && (viewLines.get(i + 1).startsWith("# ")
                  || viewLines.get(i + 1).equals(
                     UNTRACKED_SEPARATOR)))
            break;
         diffLines.add(line);
      }
      if (diffLines.isEmpty())
         return new ArrayList<>();

      List<GitHunkStaging.Hunk> rawHunks =
         GitHunkStaging.parseHunks(diffLines);

      // Adjust bufferLine: raw hunks are 1-based within diffLines,
      // add diffStart offset to get buffer-line positions.
      List<GitHunkStaging.Hunk> adjusted = new ArrayList<>();
      for (GitHunkStaging.Hunk h : rawHunks) {
         adjusted.add(new GitHunkStaging.Hunk(
            h.header, h.body, h.index,
            h.bufferLine + diffStart));
      }
      return adjusted;
   }

   /**
    * Build the combined commit view buffer lines.
    *
    * @param prevMessage message lines to preserve across refresh,
    *                    or null for a fresh commit
    * @param amend true if amending the previous commit
    * @return the buffer lines
    * @throws IOException if git commands fail
    */
   static List<String> buildView(List<String> prevMessage,
         boolean amend) throws IOException {
      List<String> lines = new ArrayList<>();

      // Message area
      if (prevMessage != null && !prevMessage.isEmpty()) {
         lines.addAll(prevMessage);
      } else if (amend) {
         List<String> lastMsg = GitProcess.execute(
            "log", "-1", "--pretty=%B");
         for (String m : lastMsg) {
            // %B adds a trailing blank; keep non-empty lines
            if (!m.isEmpty() || !lines.isEmpty())
               lines.add(m);
         }
         // Trim trailing blanks from %B output
         while (!lines.isEmpty()
               && lines.get(lines.size() - 1).isEmpty())
            lines.remove(lines.size() - 1);
      } else {
         lines.add("");
      }

      // Instructions
      if (amend) {
         lines.add(
            "# Amend — :git_do_commit will amend the last commit");
      } else {
         lines.add(
            "# Enter commit message above, then :git_do_commit");
      }
      lines.add("# Lines starting with '#' will be ignored.");
      lines.add("#");

      // Staged section
      List<String> staged = GitProcess.execute(
         "diff", "--cached", "--stat");
      lines.add(STAGED_SEPARATOR);
      if (staged.isEmpty()) {
         lines.add("#   (no staged changes)");
      } else {
         for (String s : staged) {
            lines.add("#   " + s);
         }
      }
      lines.add("#");

      // Unstaged section with full diff
      lines.add(UNSTAGED_SEPARATOR);
      List<String> diff = GitProcess.execute("diff");
      if (diff.isEmpty()) {
         lines.add("#   (no unstaged changes)");
      } else {
         lines.addAll(diff);
      }

      lines.add("");

      // Untracked files section
      List<String> untracked = GitProcess.execute(
         "ls-files", "--others", "--exclude-standard");
      lines.add(UNTRACKED_SEPARATOR);
      if (untracked.isEmpty()) {
         lines.add("#   (no untracked files)");
      } else {
         for (String f : untracked) {
            lines.add("  " + f);
         }
      }

      lines.add("");
      lines.add("# s=stage hunk  u=unstage  ^]=goto file"
         + "  q=quit");

      return lines;
   }

   /**
    * Extract the commit message from a commit view buffer.
    * Reads all non-comment, non-empty lines before the first
    * {@code # ---} separator.
    *
    * @param buf the commit view buffer
    * @return the trimmed commit message
    */
   static String extractMessage(javi.TextEdit<?> buf) {
      List<String> lines = bufferToLines(buf);
      return extractMessage(lines);
   }

   /**
    * Extract the commit message from a list of view lines.
    *
    * @param viewLines the commit view buffer lines
    * @return the trimmed commit message
    */
   static String extractMessage(List<String> viewLines) {
      StringBuilder msg = new StringBuilder();
      for (String line : viewLines) {
         if (line.equals(STAGED_SEPARATOR)
               || line.equals(UNSTAGED_SEPARATOR))
            break;
         if (!line.startsWith("#")) {
            if (msg.length() > 0)
               msg.append('\n');
            msg.append(line);
         }
      }
      return msg.toString().trim();
   }

   /**
    * Preserve the user's in-progress message from the buffer
    * for re-insertion after a refresh. Returns all non-comment
    * lines before the staged separator.
    *
    * @param buf the commit view buffer
    * @return list of message lines (may be empty)
    */
   static List<String> preserveMessage(javi.TextEdit<?> buf) {
      return preserveMessage(bufferToLines(buf));
   }

   /**
    * Preserve the user's in-progress message from a list of
    * view lines.
    *
    * @param viewLines the commit view buffer lines
    * @return list of message lines (may be empty)
    */
   static List<String> preserveMessage(List<String> viewLines) {
      List<String> msg = new ArrayList<>();
      for (String line : viewLines) {
         if (line.equals(STAGED_SEPARATOR)
               || line.equals(UNSTAGED_SEPARATOR))
            break;
         if (!line.startsWith("#"))
            msg.add(line);
      }
      // Trim trailing blank lines
      while (!msg.isEmpty()
            && msg.get(msg.size() - 1).isEmpty())
         msg.remove(msg.size() - 1);
      return msg;
   }

   /**
    * Find the buffer line number where the unstaged diff content
    * starts (the line after the unstaged separator).
    *
    * @param buf the commit view buffer
    * @return the 1-based line number, or -1 if not found
    */
   static int findDiffStart(javi.TextEdit<?> buf) {
      return findDiffStart(bufferToLines(buf));
   }

   /**
    * Find the line number where the unstaged diff content starts.
    *
    * @param viewLines the commit view buffer lines
    * @return the 1-based line number, or -1 if not found
    */
   static int findDiffStart(List<String> viewLines) {
      for (int i = 0; i < viewLines.size(); i++) {
         if (viewLines.get(i).equals(UNSTAGED_SEPARATOR))
            return i + 2; // 1-based, line after separator
      }
      return -1;
   }

   /**
    * Parse hunks from the commit view buffer, returning hunks
    * with buffer-line positions adjusted for the message area
    * and staged-file preamble.
    *
    * @param viewLines the full commit view buffer lines
    * @return parsed hunks with correct buffer-line positions
    */
   static List<GitHunkStaging.Hunk> parseViewHunks(
         List<String> viewLines) {
      // Find where the unstaged diff starts
      int diffStart = -1;
      for (int i = 0; i < viewLines.size(); i++) {
         if (viewLines.get(i).equals(UNSTAGED_SEPARATOR)) {
            diffStart = i + 1;
            break;
         }
      }
      if (diffStart < 0 || diffStart >= viewLines.size())
         return new ArrayList<>();

      // Extract just the diff lines
      List<String> diffLines = new ArrayList<>();
      for (int i = diffStart; i < viewLines.size(); i++) {
         String line = viewLines.get(i);
         // Stop at untracked section or trailing help line
         if (line.equals(UNTRACKED_SEPARATOR))
            break;
         if (line.startsWith("# ") && line.contains("stage"))
            break;
         if (line.isEmpty()
               && i + 1 < viewLines.size()
               && (viewLines.get(i + 1).startsWith("# ")
                  || viewLines.get(i + 1).equals(
                     UNTRACKED_SEPARATOR)))
            break;
         diffLines.add(line);
      }
      if (diffLines.isEmpty())
         return new ArrayList<>();

      // Parse hunks from the diff subset
      List<GitHunkStaging.Hunk> rawHunks =
         GitHunkStaging.parseHunks(diffLines);

      // Adjust bufferLine to account for lines before the diff.
      // rawHunks have 1-based positions within diffLines.
      // The diff starts at (diffStart + 1) in the buffer (1-based).
      List<GitHunkStaging.Hunk> adjusted = new ArrayList<>();
      for (GitHunkStaging.Hunk h : rawHunks) {
         adjusted.add(new GitHunkStaging.Hunk(
            h.header, h.body, h.index,
            h.bufferLine + diffStart));
      }
      return adjusted;
   }

   /**
    * Convert a TextEdit buffer to a list of string lines.
    * Lines are 1-based in TextEdit; returns 0-based list.
    */
   private static List<String> bufferToLines(
         javi.TextEdit<?> buf) {
      List<String> lines = new ArrayList<>();
      int size = buf.readIn();
      for (int i = 1; i < size; i++) {
         lines.add(buf.at(i).toString());
      }
      return lines;
   }

   /** Filename used to persist in-progress commit messages. */
   private static final String MSG_FILE = "JAVI_COMMIT_MSG";

   /**
    * Save the in-progress commit message to disk for the given
    * repo.  The file is stored as {@code JAVI_COMMIT_MSG} inside
    * the git directory (supports worktrees).
    *
    * @param repoRoot absolute path to the repo root
    * @param message the message lines to save
    */
   static void saveMessage(String repoRoot, List<String> message) {
      if (repoRoot == null)
         return;
      try {
         Path msgPath = resolveMessagePath(repoRoot);
         if (msgPath == null)
            return;
         if (message == null || message.isEmpty()
               || isBlank(message)) {
            Files.deleteIfExists(msgPath);
         } else {
            Files.write(msgPath, message, StandardCharsets.UTF_8);
         }
      } catch (IOException e) {
         trace("Failed to save commit message: " + e);
      }
   }

   /**
    * Load a previously saved commit message from disk.
    *
    * @param repoRoot absolute path to the repo root
    * @return saved message lines, or null if none saved
    */
   static List<String> loadMessage(String repoRoot) {
      if (repoRoot == null)
         return null;
      try {
         Path msgPath = resolveMessagePath(repoRoot);
         if (msgPath == null)
            return null;
         if (Files.exists(msgPath)) {
            List<String> lines = Files.readAllLines(
               msgPath, StandardCharsets.UTF_8);
            if (!isBlank(lines))
               return lines;
         }
      } catch (IOException e) {
         trace("Failed to load commit message: " + e);
      }
      return null;
   }

   /**
    * Delete the saved commit message file after a successful commit.
    *
    * @param repoRoot absolute path to the repo root
    */
   static void clearSavedMessage(String repoRoot) {
      if (repoRoot == null)
         return;
      try {
         Path msgPath = resolveMessagePath(repoRoot);
         if (msgPath == null)
            return;
         Files.deleteIfExists(msgPath);
      } catch (IOException e) {
         trace("Failed to clear commit message: " + e);
      }
   }

   /**
    * Resolve the path to the commit message persistence file.
    * Uses {@code git rev-parse --absolute-git-dir} to find the
    * correct directory, supporting both normal repos and worktrees.
    *
    * @param repoRoot absolute path to the repo root
    * @return the resolved path, or null if unable to determine
    */
   private static Path resolveMessagePath(String repoRoot) {
      String gitDir = GitProcess.getGitDir(
         new java.io.File(repoRoot));
      if (gitDir != null)
         return Paths.get(gitDir, MSG_FILE);
      // Fallback: use .git/ directly (may not work in worktrees)
      return Paths.get(repoRoot, ".git", MSG_FILE);
   }

   /**
    * Determine the 1-based line number of the last editable
    * message line in a commit view buffer. Lines in the message
    * area are those before the first comment line ({@code #}).
    *
    * @param buf the commit view buffer
    * @return the 1-based line number of the last message line,
    *         or 0 if the buffer is empty or starts with comments
    */
   static int getMessageAreaEnd(javi.TextEdit<?> buf) {
      int size = buf.readIn();
      for (int i = 1; i < size; i++) {
         String line = buf.at(i).toString();
         if (line.startsWith("#"))
            return i - 1;
      }
      return size - 1;
   }

   /**
    * Determine the 1-based line number of the last editable
    * message line from a list of view lines. Lines in the
    * message area are those before the first comment line.
    *
    * @param viewLines 0-based list of buffer lines
    * @return the 1-based line number of the last message line,
    *         or 0 if empty or starts with comments
    */
   static int getMessageAreaEnd(List<String> viewLines) {
      for (int i = 0; i < viewLines.size(); i++) {
         if (viewLines.get(i).startsWith("#"))
            return i; // i is 0-based index = 1-based line - 1
      }
      return viewLines.size();
   }

   /**
    * Check whether a 1-based cursor line is within the editable
    * message area of a commit view buffer (before any {@code #}
    * comment lines).
    *
    * @param buf the commit view buffer
    * @param lineNum 1-based cursor line number
    * @return true if the line is in the editable message area
    */
   public static boolean isInMessageArea(javi.TextEdit<?> buf,
         int lineNum) {
      return lineNum > 0 && lineNum <= getMessageAreaEnd(buf);
   }

   /**
    * Check whether a 1-based cursor line is within the editable
    * message area given a list of view lines.
    *
    * @param viewLines 0-based list of buffer lines
    * @param lineNum 1-based cursor line number
    * @return true if the line is in the editable message area
    */
   static boolean isInMessageArea(List<String> viewLines,
         int lineNum) {
      return lineNum > 0 && lineNum <= getMessageAreaEnd(viewLines);
   }

   /**
    * Check if a list of lines is effectively blank (all empty).
    */
   private static boolean isBlank(List<String> lines) {
      for (String line : lines) {
         if (!line.trim().isEmpty())
            return false;
      }
      return true;
   }
}
