package javi.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parse git blame output and format it for display in a buffer.
 *
 * <p>Uses {@code git blame --porcelain} for machine-readable output,
 * then formats each line with author, date, and short SHA alongside
 * the source code.</p>
 */
public final class GitBlameBuffer {

   /** Private constructor to prevent instantiation. */
   private GitBlameBuffer() {
   }

   /**
    * A single blame entry associating a source line with its commit.
    */
   static final class BlameEntry {
      final String sha;
      final String author;
      final String date;
      final String line;

      BlameEntry(String commitSha, String authorName,
            String commitDate, String sourceLine) {
         this.sha = commitSha;
         this.author = authorName;
         this.date = commitDate;
         this.line = sourceLine;
      }
   }

   /**
    * Run git blame on a file and return parsed entries.
    *
    * @param filepath the file to blame (relative to repo root)
    * @param dir working directory for the git command, or null
    * @return list of blame entries, one per source line
    * @throws IOException if git command fails
    */
   public static List<BlameEntry> getBlameEntries(String filepath,
         java.io.File dir) throws IOException {
      List<String> raw = GitProcess.execute(dir,
         "blame", "--porcelain", filepath);
      return parsePorcelain(raw);
   }

   /**
    * Parse git blame --porcelain output into BlameEntry objects.
    *
    * <p>Porcelain format groups: a header line with SHA and line numbers,
    * followed by key-value metadata, then a tab-prefixed source line.</p>
    *
    * @param lines raw porcelain output lines
    * @return parsed blame entries
    */
   static List<BlameEntry> parsePorcelain(List<String> lines) {
      List<BlameEntry> entries = new ArrayList<>();
      Map<String, String> commitAuthors = new LinkedHashMap<>();
      Map<String, String> commitDates = new LinkedHashMap<>();

      String currentSha = null;
      String currentAuthor = null;
      String currentDate = null;

      for (String line : lines) {
         if (line.startsWith("\t")) {
            // Source line — finalize this entry
            String sourceLine = line.substring(1);
            if (currentSha != null) {
               String author = currentAuthor != null
                  ? currentAuthor
                  : commitAuthors.getOrDefault(currentSha, "?");
               String date = currentDate != null
                  ? currentDate
                  : commitDates.getOrDefault(currentSha, "");
               entries.add(new BlameEntry(
                  shortSha(currentSha), author, date, sourceLine));
            }
            currentAuthor = null;
            currentDate = null;
         } else if (line.startsWith("author-time ")) {
            String epoch = line.substring("author-time ".length());
            currentDate = epochToDate(epoch);
            if (currentSha != null) {
               commitDates.put(currentSha, currentDate);
            }
         } else if (line.startsWith("author-mail ")
               || line.startsWith("author-tz ")) {
            continue; // skip other author metadata
         } else if (line.startsWith("author ")) {
            currentAuthor = line.substring("author ".length());
            if (currentSha != null) {
               commitAuthors.put(currentSha, currentAuthor);
            }
         } else if (line.length() >= 40
               && isHexChar(line.charAt(0))) {
            // Header line: SHA origLine finalLine [numLines]
            String[] parts = line.split(" ");
            if (parts.length >= 3) {
               currentSha = parts[0];
               // Inherit cached metadata for repeated SHAs
               currentAuthor = commitAuthors.get(currentSha);
               currentDate = commitDates.get(currentSha);
            }
         }
         // Other metadata lines (committer, summary, etc.) are ignored
      }
      return entries;
   }

   /**
    * Format blame entries into display lines for the editor buffer.
    *
    * <p>Each line shows: {@code SHA author date | source}</p>
    *
    * @param entries parsed blame entries
    * @return formatted lines for display
    */
   public static List<String> formatBlame(List<BlameEntry> entries) {
      // Calculate column widths for alignment
      int maxAuthor = 0;
      for (BlameEntry e : entries) {
         if (e.author.length() > maxAuthor)
            maxAuthor = e.author.length();
      }
      if (maxAuthor > 20)
         maxAuthor = 20;

      List<String> result = new ArrayList<>();
      for (BlameEntry e : entries) {
         String author = e.author;
         if (author.length() > 20)
            author = author.substring(0, 20);
         String formatted = String.format("%-8s %-" + maxAuthor
            + "s %s | %s", e.sha, author, e.date, e.line);
         result.add(formatted);
      }
      return result;
   }

   /**
    * Truncate a full SHA to 8 characters.
    */
   private static String shortSha(String sha) {
      if (sha == null)
         return "????????";
      if (sha.length() > 8)
         return sha.substring(0, 8);
      return sha;
   }

   /**
    * Convert a Unix epoch timestamp to a YYYY-MM-DD string.
    */
   static String epochToDate(String epoch) {
      try {
         long seconds = Long.parseLong(epoch.trim());
         java.time.Instant instant =
            java.time.Instant.ofEpochSecond(seconds);
         java.time.LocalDate date = instant
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate();
         return date.toString();
      } catch (NumberFormatException e) {
         return epoch;
      }
   }

   /**
    * Check if a character is a hex digit.
    */
   private static boolean isHexChar(char c) {
      return (c >= '0' && c <= '9')
         || (c >= 'a' && c <= 'f')
         || (c >= 'A' && c <= 'F');
   }
}
