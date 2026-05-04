package javi.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parse and format git status output into a readable buffer.
 *
 * <p>Parses {@code git status --porcelain=v2 --branch} output and
 * formats it into sections: Head, Staged, Unstaged, Untracked.</p>
 */
public final class GitStatusBuffer {

   /** Private constructor to prevent instantiation. */
   private GitStatusBuffer() {
   }

   /**
    * Generate formatted git status lines for display in a buffer.
    *
    * @return list of formatted lines
    * @throws IOException if git command fails
    */
   public static List<String> getStatusLines() throws IOException {
      List<String> raw = GitProcess.execute(
         "status", "--porcelain=v2", "--branch");
      return formatStatus(raw);
   }

   /**
    * Generate formatted git status lines running from a specific directory.
    *
    * @param dir working directory for git status command
    * @return list of formatted lines
    * @throws IOException if git command fails
    */
   public static List<String> getStatusLines(java.io.File dir)
         throws IOException {
      List<String> raw = GitProcess.execute(
         dir, "status", "--porcelain=v2", "--branch");
      return formatStatus(raw);
   }

   /**
    * Parse porcelain v2 output into formatted display lines.
    *
    * @param raw raw porcelain v2 output lines
    * @return formatted lines for display
    */
   public static List<String> formatStatus(List<String> raw) {
      String branch = "unknown";
      String oid = "";
      String upstream = "";
      int ahead = 0;
      int behind = 0;

      ArrayList<String> staged = new ArrayList<>();
      ArrayList<String> unstaged = new ArrayList<>();
      ArrayList<String> untracked = new ArrayList<>();

      for (String line : raw) {
         if (line.startsWith("# branch.head ")) {
            branch = line.substring("# branch.head ".length());
         } else if (line.startsWith("# branch.oid ")) {
            oid = line.substring("# branch.oid ".length());
            if (oid.length() > 7) {
               oid = oid.substring(0, 7);
            }
         } else if (line.startsWith("# branch.upstream ")) {
            upstream = line.substring("# branch.upstream ".length());
         } else if (line.startsWith("# branch.ab ")) {
            String ab = line.substring("# branch.ab ".length());
            String[] parts = ab.split(" ");
            if (parts.length >= 2) {
               try {
                  ahead = Integer.parseInt(parts[0].substring(1));
                  behind = Integer.parseInt(parts[1].substring(1));
               } catch (NumberFormatException e) {
                  // ignore parse errors
               }
            }
         } else if (line.startsWith("1 ") || line.startsWith("2 ")) {
            parseChangedEntry(line, staged, unstaged);
         } else if (line.startsWith("? ")) {
            String path = line.substring(2);
            untracked.add(path);
         } else if (line.startsWith("u ")) {
            // unmerged entry
            String[] parts = line.split(" ", 11);
            if (parts.length >= 11) {
               staged.add("unmerged    " + parts[10]);
            }
         }
      }

      ArrayList<String> result = new ArrayList<>();

      // Header section
      result.add("Git Status");
      result.add("==========");
      result.add("");
      StringBuilder headLine = new StringBuilder("Head: ");
      headLine.append(branch);
      if (!oid.isEmpty()) {
         headLine.append(" (").append(oid).append(")");
      }
      result.add(headLine.toString());

      if (!upstream.isEmpty()) {
         StringBuilder upLine = new StringBuilder("Push: ");
         upLine.append(upstream);
         if (ahead > 0 || behind > 0) {
            upLine.append(" (");
            if (ahead > 0) {
               upLine.append("ahead ").append(ahead);
            }
            if (ahead > 0 && behind > 0) {
               upLine.append(", ");
            }
            if (behind > 0) {
               upLine.append("behind ").append(behind);
            }
            upLine.append(")");
         } else {
            upLine.append(" (up to date)");
         }
         result.add(upLine.toString());
      }
      result.add("");

      // Staged section
      result.add("Staged changes (" + staged.size() + ")");
      if (staged.isEmpty()) {
         result.add("  (none)");
      } else {
         for (String s : staged) {
            result.add("  " + s);
         }
      }
      result.add("");

      // Unstaged section
      result.add("Unstaged changes (" + unstaged.size() + ")");
      if (unstaged.isEmpty()) {
         result.add("  (none)");
      } else {
         for (String s : unstaged) {
            result.add("  " + s);
         }
      }
      result.add("");

      // Untracked section
      result.add("Untracked files (" + untracked.size() + ")");
      if (untracked.isEmpty()) {
         result.add("  (none)");
      } else {
         for (String s : untracked) {
            result.add("  " + s);
         }
      }
      result.add("");

      // Key bindings help
      result.add("Keys: s=stage  u=unstage  X=discard  "
         + "c=commit  R=refresh  d=diff  q=quit");
      result.add("Commands: :git_toggle  :git_stage_line  "
         + ":git_unstage_line  :git_discard");
      result.add("          :git_commit  :git_diff  :git_log"
         + "  :git_stash  :git_stash_pop");
      result.add("          :git_merge <branch>  :git_fetch"
         + "  :git_pull  :git_push");

      return result;
   }

   /**
    * Parse a changed entry (type 1 or 2) from porcelain v2 format.
    *
    * <p>Format for type 1 (ordinary):
    * {@code 1 XY sub mH mI mW hH hI path}
    * Format for type 2 (rename/copy):
    * {@code 2 XY sub mH mI mW hH hI X### path\torigPath}</p>
    *
    * @param line the raw line
    * @param staged list to add staged changes to
    * @param unstaged list to add unstaged changes to
    */
   private static void parseChangedEntry(String line,
         List<String> staged, List<String> unstaged) {
      String[] parts = line.split(" ", 9);
      if (parts.length < 9) {
         return;
      }

      String xy = parts[1];
      char x = xy.charAt(0); // staged status
      char y = xy.charAt(1); // unstaged status

      String path;
      if (line.startsWith("2 ")) {
         // Rename/copy: path contains tab separator
         String pathPart = parts[8];
         int tabIdx = pathPart.indexOf('\t');
         if (tabIdx >= 0) {
            path = pathPart.substring(tabIdx + 1) + " -> "
               + pathPart.substring(0, tabIdx);
         } else {
            path = pathPart;
         }
      } else {
         path = parts[8];
      }

      if ('.' != x) {
         staged.add(describeChange(x) + " " + path);
      }
      if ('.' != y) {
         unstaged.add(describeChange(y) + " " + path);
      }
   }

   /**
    * Convert a status character to a human-readable description.
    *
    * @param code the status character (M, A, D, R, C, etc.)
    * @return human-readable description
    */
   private static String describeChange(char code) {
      return switch (code) {
         case 'M' -> "modified   ";
         case 'A' -> "new file   ";
         case 'D' -> "deleted    ";
         case 'R' -> "renamed    ";
         case 'C' -> "copied     ";
         case 'T' -> "typechange ";
         default  -> "changed    ";
      };
   }
}
