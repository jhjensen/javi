package javi.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse and format git log graph output for display in a buffer.
 *
 * <p>Provides methods to extract commit SHAs from log lines and
 * to retrieve full commit details or diffs for a given commit.</p>
 */
public final class GitLogBuffer {

   /**
    * Pattern matching a commit SHA in a git log --oneline --graph line.
    * Matches 7-40 hex chars preceded by graph characters or whitespace.
    * Examples:
    * <pre>
    *   * abc1234 Fix the bug
    *   | * def5678 (HEAD -&gt; main) Add feature
    *   |/  9876543 Merge branch
    * </pre>
    */
   static final Pattern SHA_PATTERN = Pattern.compile(
      "(?:^|[*|/\\\\ ]+)\\s*([0-9a-f]{7,40})\\b");

   /**
    * Pattern for decorated refs in parentheses after the SHA.
    * Matches {@code (HEAD -> main, origin/main, tag: v1.0)}.
    */
   static final Pattern DECORATION_PATTERN = Pattern.compile(
      "\\(([^)]+)\\)");

   /** Private constructor to prevent instantiation. */
   private GitLogBuffer() {
   }

   /**
    * Run git log with graph, decorations, and all branches.
    *
    * @param count number of log entries to show
    * @return list of log output lines
    * @throws IOException if git command fails
    */
   public static List<String> getLogLines(int count) throws IOException {
      return GitProcess.execute(
         "log", "--oneline", "--graph", "--all", "--decorate",
         "-" + count);
   }

   /**
    * Extract the commit SHA from a git log --oneline --graph line.
    *
    * @param line a line from git log --oneline --graph output
    * @return the 7+ character SHA, or null if no SHA found
    */
   public static String extractSha(String line) {
      if (null == line) {
         return null;
      }
      Matcher m = SHA_PATTERN.matcher(line);
      if (m.find()) {
         return m.group(1);
      }
      return null;
   }

   /**
    * Extract decoration text (branch/tag names) from a log line.
    *
    * @param line a line from git log output
    * @return the decoration text without parens, or null if none
    */
   public static String extractDecoration(String line) {
      if (null == line) {
         return null;
      }
      Matcher m = DECORATION_PATTERN.matcher(line);
      if (m.find()) {
         return m.group(1);
      }
      return null;
   }

   /**
    * Get full commit details for a given SHA.
    * Shows author, date, full message, and diff stat.
    *
    * @param sha the commit SHA (abbreviated or full)
    * @return formatted lines for display
    * @throws IOException if git command fails
    */
   public static List<String> getCommitDetails(String sha)
         throws IOException {
      List<String> raw = GitProcess.execute(
         "show", "--stat", "--format=fuller", sha);
      ArrayList<String> result = new ArrayList<>();
      result.add("Commit Details: " + sha);
      result.add("==============");
      result.add("");
      result.addAll(raw);
      return result;
   }

   /**
    * Get the full diff for a given commit SHA.
    *
    * @param sha the commit SHA (abbreviated or full)
    * @return diff output lines
    * @throws IOException if git command fails
    */
   public static List<String> getCommitDiff(String sha)
         throws IOException {
      List<String> raw = GitProcess.execute("show", sha);
      ArrayList<String> result = new ArrayList<>();
      result.add("Diff: " + sha);
      result.add("=====");
      result.add("");
      result.addAll(raw);
      return result;
   }

   /**
    * Format the log output with a header and help text.
    *
    * @param logLines raw git log output lines
    * @return formatted lines including header and keybinding help
    */
   public static List<String> formatLog(List<String> logLines) {
      ArrayList<String> result = new ArrayList<>();
      result.add("Git Log (all branches)");
      result.add("Commands: :git_show (commit details)"
         + "  :git_log_diff (full diff)");
      result.add("======================");
      result.add("");
      result.addAll(logLines);
      return result;
   }
}
