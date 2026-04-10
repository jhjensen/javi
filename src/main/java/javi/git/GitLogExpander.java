package javi.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages inline expansion of git log entries.
 *
 * <p>Each log line that contains a commit SHA can be expanded
 * in two levels:</p>
 * <ol>
 *   <li>Level 1 — commit stat: author, date, message,
 *       and diff stat ({@code git show --stat})</li>
 *   <li>Level 2 — full diff: complete patch output
 *       ({@code git show})</li>
 * </ol>
 *
 * <p>The expander does not modify buffers directly. Instead it
 * tracks expansion state and rebuilds the full content list via
 * {@link #buildContent(List)}. The caller creates a new buffer
 * with the returned content.</p>
 */
public final class GitLogExpander {

   /**
    * Map from SHA to expansion level.
    * 0 = collapsed (entry removed from map),
    * 1 = stat, 2 = diff.
    */
   private final Map<String, Integer> levels = new HashMap<>();

   /** Cache of fetched stat content, keyed by SHA. */
   private final Map<String, List<String>> statCache =
      new HashMap<>();

   /** Cache of fetched diff content, keyed by SHA. */
   private final Map<String, List<String>> diffCache =
      new HashMap<>();

   /**
    * Toggle expansion for the given SHA.
    * Collapsed to level 1, level 1 to level 2,
    * level 2 to collapsed.
    *
    * @param sha the commit SHA to toggle
    * @return the new level (0 = collapsed)
    */
   public int toggle(String sha) {
      Integer cur = levels.get(sha);
      if (null == cur || 0 == cur) {
         levels.put(sha, 1);
         return 1;
      }
      if (1 == cur) {
         levels.put(sha, 2);
         return 2;
      }
      levels.remove(sha);
      return 0;
   }

   /**
    * Expand all SHAs found in the given log lines to level 2.
    *
    * @param logLines the original (non-expanded) log lines
    */
   public void expandAll(List<String> logLines) {
      for (String line : logLines) {
         String sha = GitLogBuffer.extractSha(line);
         if (null != sha) {
            levels.put(sha, 2);
         }
      }
   }

   /**
    * Collapse all expansions.
    */
   public void collapseAll() {
      levels.clear();
   }

   /**
    * Build the full display content from original log lines,
    * inserting expansion content below each expanded entry.
    *
    * @param logLines the original (non-expanded) log lines
    * @return the complete content including expansions
    * @throws IOException if git commands fail
    */
   public List<String> buildContent(List<String> logLines)
         throws IOException {
      ArrayList<String> result = new ArrayList<>();
      for (String line : logLines) {
         result.add(line);
         String sha = GitLogBuffer.extractSha(line);
         if (null == sha) {
            continue;
         }
         Integer level = levels.get(sha);
         if (null == level || 0 == level) {
            continue;
         }
         if (1 == level) {
            result.addAll(fetchStat(sha));
         } else {
            result.addAll(fetchDiff(sha));
         }
      }
      return result;
   }

   /**
    * Fetch and cache stat content for a commit SHA.
    */
   private List<String> fetchStat(String sha)
         throws IOException {
      List<String> cached = statCache.get(sha);
      if (null != cached) {
         return cached;
      }
      List<String> content = getStatContent(sha);
      statCache.put(sha, content);
      return content;
   }

   /**
    * Fetch and cache diff content for a commit SHA.
    */
   private List<String> fetchDiff(String sha)
         throws IOException {
      List<String> cached = diffCache.get(sha);
      if (null != cached) {
         return cached;
      }
      List<String> content = getDiffContent(sha);
      diffCache.put(sha, content);
      return content;
   }

   /**
    * Get stat-level content for a commit.
    * Prefixed with box-drawing markers for visual separation.
    */
   public static List<String> getStatContent(String sha)
         throws IOException {
      List<String> raw = GitProcess.execute(
         "show", "--stat",
         "--format=  Author: %an <%ae>%n"
         + "  Date:   %ai%n  %n  %s%n  %b", sha);
      String shortSha = sha.substring(0,
         Math.min(7, sha.length()));
      ArrayList<String> result = new ArrayList<>();
      result.add("  +-- " + shortSha + " ----------");
      for (String line : raw) {
         result.add("  | " + line);
      }
      result.add("  +----------------------------");
      return result;
   }

   /**
    * Get full diff content for a commit.
    */
   public static List<String> getDiffContent(String sha)
         throws IOException {
      List<String> raw = GitProcess.execute("show", sha);
      String shortSha = sha.substring(0,
         Math.min(7, sha.length()));
      ArrayList<String> result = new ArrayList<>();
      result.add("  +-- " + shortSha + " (full diff) --");
      for (String line : raw) {
         result.add("  | " + line);
      }
      result.add("  +----------------------------");
      return result;
   }

   /** Check if any expansions are active. */
   public boolean hasExpansions() {
      return !levels.isEmpty();
   }

   /** Get the expansion count. */
   public int expansionCount() {
      return levels.size();
   }

   /**
    * Get the expansion level for a given SHA.
    *
    * @return 0 if not expanded, 1 for stat, 2 for diff
    */
   public int getLevel(String sha) {
      Integer level = levels.get(sha);
      return null == level ? 0 : level;
   }
}
