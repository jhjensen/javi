package javi.git;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed commit from git log output.
 *
 * <p>Holds the SHA, parent SHAs, subject, author, date, and
 * decoration text (branch/tag refs). Used by {@link GitLogGraph}
 * for lane assignment and by {@link GitLogPanel} for rendering.</p>
 */
public final class GitLogEntry {

   public final String sha;
   public final List<String> parents;
   public final String subject;
   public final String author;
   public final String date;
   public final String decoration;

   public GitLogEntry(String commitSha, List<String> commitParents,
         String commitSubject, String commitAuthor,
         String commitDate, String commitDecoration) {
      this.sha = commitSha;
      this.parents = Collections.unmodifiableList(commitParents);
      this.subject = commitSubject;
      this.author = commitAuthor;
      this.date = commitDate;
      this.decoration = commitDecoration;
   }

   /**
    * Parse git log output into a list of entries.
    * Expects format: {@code %H|%P|%d|%s|%an|%ai}
    *
    * @param lines output from git log with the expected format
    * @return list of parsed entries
    */
   public static List<GitLogEntry> parse(List<String> lines) {
      ArrayList<GitLogEntry> result = new ArrayList<>();
      for (String line : lines) {
         if (line.isEmpty()) {
            continue;
         }
         // Format: SHA|PARENTS|DECORATION|SUBJECT|AUTHOR|DATE
         // Parents are space-separated; decoration may be empty
         String[] parts = line.split("\\|", 6);
         if (parts.length < 6) {
            continue;
         }
         String sha = parts[0].trim();
         List<String> parents = parseParents(parts[1].trim());
         String deco = parts[2].trim();
         if (deco.startsWith("(") && deco.endsWith(")")) {
            deco = deco.substring(1, deco.length() - 1).trim();
         }
         if (deco.isEmpty()) {
            deco = null;
         }
         String subject = parts[3];
         String author = parts[4];
         String date = parts[5];
         result.add(new GitLogEntry(sha, parents, subject,
            author, date, deco));
      }
      return result;
   }

   private static List<String> parseParents(String raw) {
      if (raw.isEmpty()) {
         return Collections.emptyList();
      }
      String[] shas = raw.split(" ");
      ArrayList<String> result = new ArrayList<>(shas.length);
      for (String s : shas) {
         String trimmed = s.trim();
         if (!trimmed.isEmpty()) {
            result.add(trimmed);
         }
      }
      return result;
   }

   /** Short SHA for display (first 7 characters). */
   public String shortSha() {
      if (sha.length() > 7) {
         return sha.substring(0, 7);
      }
      return sha;
   }
}
