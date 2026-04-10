package javi.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

   /** Prefix for diff show markers in unexpanded state. */
   static final String DIFF_SHOW_PREFIX = "  >> Show diffs (";

   /** Prefix for diff header markers in expanded state. */
   static final String DIFF_HIDE_PREFIX = "  << Diffs (";

   /** Pagination sentinel line text. */
   static final String PAGINATION_MARKER = "--- more ---";

   /** Private constructor to prevent instantiation. */
   private GitLogBuffer() {
   }

   /**
    * Run git log with graph and decorations for the current branch.
    *
    * @param count number of log entries to show
    * @param dir working directory for the git command, or null
    * @return list of log output lines
    * @throws IOException if git command fails
    */
   public static List<String> getLogLines(int count, java.io.File dir)
         throws IOException {
      return GitProcess.execute(dir,
         "log", "--oneline", "--graph", "--decorate",
         "-" + count, "HEAD");
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
    * Extract the SHA from a diff marker line.
    * Works with both {@link #DIFF_SHOW_PREFIX} and
    * {@link #DIFF_HIDE_PREFIX} markers.
    *
    * @param line a diff marker line
    * @return the SHA, or null if not a marker line
    */
   public static String extractShaFromMarker(String line) {
      if (null == line) {
         return null;
      }
      int open = line.indexOf('(');
      int close = line.indexOf(')', open + 1);
      if (open >= 0 && close > open) {
         return line.substring(open + 1, close);
      }
      return null;
   }

   /**
    * Fetch diff output for a commit, excluding the commit header.
    *
    * @param sha the commit SHA
    * @param dir working directory for the git command, or null
    * @return list of diff output lines
    * @throws IOException if git command fails
    */
   public static List<String> getDiffLines(String sha,
         java.io.File dir) throws IOException {
      return GitProcess.execute(dir,
         "show", "--format=", "--stat", "-p", sha);
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
      result.add("Git Log");
      result.add("Keys: Enter/o=expand  O=full diff  "
         + "R=expand all  q=quit");
      result.add("Fold: za=toggle  zo=open  zc=close  "
         + "zR=open-all  zM=close-all");
      result.add("======================");
      result.add("");
      result.addAll(logLines);
      return result;
   }

   /**
    * Fetch commit messages for the given count of recent commits.
    * Returns a map from abbreviated SHA to message lines.
    *
    * @param count number of commits to fetch
    * @param dir working directory for the git command, or null
    * @return ordered map of sha to message body lines
    * @throws IOException if git command fails
    */
   public static Map<String, List<String>> getCommitMessages(
         int count, java.io.File dir) throws IOException {
      List<String> raw = GitProcess.execute(dir,
         "log", "--format=%h%x00%s%x00%an <%ae>%x00%ai%x00%b%x00%x01",
         "-" + count);
      Map<String, List<String>> result = new LinkedHashMap<>();
      StringBuilder block = new StringBuilder();
      for (String line : raw) {
         block.append(line).append("\n");
      }
      String[] commits = block.toString().split("\u0001\n?");
      for (String entry : commits) {
         entry = entry.trim();
         if (entry.isEmpty())
            continue;
         String[] parts = entry.split("\u0000", 5);
         if (parts.length < 4)
            continue;
         String sha = parts[0].trim();
         String subject = parts[1].trim();
         String author = parts[2].trim();
         String date = parts[3].trim();
         String body = parts.length > 4
            ? parts[4].trim() : "";
         ArrayList<String> lines = new ArrayList<>();
         lines.add("  Author: " + author);
         lines.add("  Date:   " + date);
         lines.add("  ");
         lines.add("  " + subject);
         if (!body.isEmpty()) {
            lines.add("  ");
            for (String bline : body.split("\n")) {
               lines.add("  " + bline);
            }
         }
         result.put(sha, lines);
      }
      return result;
   }

   /**
    * Build log content with commit messages interleaved
    * after each graph line, including diff sub-folds.
    * Returns the content lines and records fold ranges
    * in the provided list as int[2] pairs of
    * {startLine, endLine} (1-based).
    *
    * <p>Each commit has an outer fold (graph line to end)
    * and a nested diff sub-fold. When diffs are not expanded,
    * the sub-fold contains a placeholder. When expanded,
    * it contains the actual diff output.</p>
    *
    * @param logLines graph log lines
    * @param messages sha-to-message map from getCommitMessages
    * @param foldRanges output list; each entry is {start,end}
    * @param diffExpanded set of SHAs with diffs shown, or null
    * @param diffCache SHA-to-diff-lines map, or null
    * @return fully built content lines (with header)
    */
   public static List<String> buildFoldedLog(
         List<String> logLines,
         Map<String, List<String>> messages,
         List<int[]> foldRanges,
         java.util.Set<String> diffExpanded,
         Map<String, List<String>> diffCache) {
      ArrayList<String> result = new ArrayList<>();
      result.add("Git Log");
      result.add("Keys: Enter/o=expand  O=full diff  "
         + "R=expand all  q=quit");
      result.add("Fold: za=toggle  zo=open  zc=close  "
         + "zR=open-all  zM=close-all");
      result.add("======================");
      result.add("");
      for (String line : logLines) {
         int lineNum = result.size() + 1; // 1-based
         result.add(line);
         String sha = extractSha(line);
         if (sha != null) {
            int commitFoldStart = lineNum;
            List<String> msg = findMessage(sha, messages);
            if (msg != null && !msg.isEmpty()) {
               for (String mline : msg) {
                  result.add(mline);
               }
            } else {
               result.add(
                  "  (use :git_show for details)");
            }
            // Diff sub-fold
            int diffFoldStart = result.size() + 1;
            boolean hasDiff = diffExpanded != null
               && diffExpanded.contains(sha);
            if (hasDiff) {
               result.add(DIFF_HIDE_PREFIX + sha + ") <<");
               List<String> diffs = diffCache != null
                  ? diffCache.get(sha) : null;
               if (diffs != null) {
                  for (String dline : diffs) {
                     result.add("  | " + dline);
                  }
               }
               result.add(
                  "  +----------------------------");
            } else {
               result.add(DIFF_SHOW_PREFIX + sha + ")");
               result.add(
                  "  (toggle to view diffs)");
            }
            int diffFoldEnd = result.size();
            int commitFoldEnd = diffFoldEnd;
            foldRanges.add(
               new int[]{diffFoldStart, diffFoldEnd});
            foldRanges.add(
               new int[]{commitFoldStart, commitFoldEnd});
         }
      }
      // Pagination sentinel fold at the bottom
      int sentinelStart = result.size() + 1;
      result.add(PAGINATION_MARKER);
      result.add("  " + logLines.size()
         + " graph lines shown."
         + " Open this fold to load more.");
      int sentinelEnd = result.size();
      foldRanges.add(new int[]{sentinelStart, sentinelEnd});
      return result;
   }

   /**
    * Find a message by matching abbreviated SHA prefix.
    */
   private static List<String> findMessage(String sha,
         Map<String, List<String>> messages) {
      List<String> exact = messages.get(sha);
      if (exact != null)
         return exact;
      for (Map.Entry<String, List<String>> e
            : messages.entrySet()) {
         if (e.getKey().startsWith(sha)
               || sha.startsWith(e.getKey())) {
            return e.getValue();
         }
      }
      return null;
   }
}
