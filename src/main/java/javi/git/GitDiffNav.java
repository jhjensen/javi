package javi.git;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import javi.TextEdit;

/**
 * Diff navigation utilities — extracted from GitCommands
 * to keep that class within checkstyle FileLength limits.
 *
 * <p>Methods for parsing diff headers, stripping path prefixes,
 * and scanning for file paths in diff output.</p>
 */
final class GitDiffNav {

   private GitDiffNav() { }

   /**
    * Strip a single-character prefix (b/, w/, i/, etc.) from a
    * diff file path.  Returns the original path if no prefix.
    */
   static String stripDiffPrefix(String path) {
      if (path.length() > 2 && path.charAt(1) == '/')
         return path.substring(2);
      return path;
   }

   /**
    * Scan forward from curLine to find a {@code +++} line for the
    * filename.  Also handles {@code diff --git} lines by extracting
    * the second (destination) path.
    *
    * @return the file path, or null if not found
    */
   @SuppressWarnings("unchecked")
   static String forwardScanForFilepath(
         TextEdit<String> buf, int curLine) {
      // Delegate to List-based overload for testability
      List<String> lines = new ArrayList<>();
      for (int i = 1; i < buf.readIn(); i++)
         lines.add(buf.at(i).toString());
      return forwardScanForFilepath(lines, curLine);
   }

   /**
    * List-based overload for unit testing.  Line numbers are
    * 1-based (matching TextEdit convention).
    */
   static String forwardScanForFilepath(
         List<String> lines, int curLine) {
      String curText = (curLine >= 1 && curLine <= lines.size())
         ? lines.get(curLine - 1) : "";

      // diff --git a/path b/path  OR  diff --git i/path w/path
      if (curText.startsWith("diff --git ")) {
         return extractPathFromDiffGit(curText);
      }

      // Scan forward (limited to 5 lines) for +++ header
      int limit = Math.min(curLine + 5, lines.size());
      for (int i = curLine + 1; i <= limit; i++) {
         String line = lines.get(i - 1);
         if (line.startsWith("+++ ")) {
            return stripDiffPrefix(line.substring(4).trim());
         }
         // Stop at next diff header or hunk start
         if (line.startsWith("diff --git ")
               || line.startsWith("@@ "))
            break;
      }
      return null;
   }

   /**
    * Extract the destination file path from a {@code diff --git}
    * header line.  Handles both standard ({@code a/...  b/...})
    * and custom ({@code i/...  w/...}) prefixes.
    *
    * @param line the full diff --git header
    * @return extracted path, or null if unparseable
    */
   static String extractPathFromDiffGit(String line) {
      // Format: "diff --git <prefix>/<path> <prefix>/<path>"
      // The second path is the destination.
      String rest = line.substring("diff --git ".length());
      // Find the second path: split on space, but paths can
      // contain spaces.  The reliable marker is the prefix
      // char + '/' appearing twice.
      // Try: find " b/" or " w/" as separator
      int sep = rest.lastIndexOf(" b/");
      if (sep < 0) sep = rest.lastIndexOf(" w/");
      if (sep < 0) {
         // Fallback: split on first space, take second half
         int sp = rest.indexOf(' ');
         if (sp > 0) {
            return stripDiffPrefix(rest.substring(sp + 1).trim());
         }
         return null;
      }
      return rest.substring(sep + 3).trim();
   }

   /**
    * Parse an integer, returning 0 on failure.
    */
   static int parseIntSafe(String s) {
      try {
         return Integer.parseInt(s.trim());
      } catch (NumberFormatException e) {
         return 0;
      }
   }

   /**
    * Compute the new-file line offset of the first added line
    * in a diff hunk body.  Counts context and added lines
    * (non-{@code -} prefix) until the first {@code +} line,
    * stopping at hunk boundaries.
    *
    * @param hunkBody lines of the hunk body (after the @@ header)
    * @return number of new-file lines up to and including
    *         the first {@code +} line, or total non-minus lines
    *         if no {@code +} line exists
    */
   static int computeFirstChangedLineOffset(List<String> hunkBody) {
      int offset = 0;
      for (String line : hunkBody) {
         if (line.startsWith("@@ ") || line.startsWith("diff --git"))
            break;
         if (!line.startsWith("-"))
            offset++;
         if (line.startsWith("+"))
            break;
      }
      return offset;
   }

   /**
    * Color used for diff header lines ({@code ---}, {@code +++},
    * {@code diff --git}).  Matches the fold-summary color in
    * {@code AtView}.
    */
   static final Color DIFF_HEADER_COLOR = Color.YELLOW;

   /**
    * Determine the foreground color for a line in a git diff buffer.
    * Header lines ({@code ---}, {@code +++}, {@code diff --git})
    * get yellow (fold-summary color); removed lines red; added lines
    * green; hunk headers ({@code @@}) cyan.
    *
    * @param line the text of the line
    * @return the color, or {@code null} for default foreground
    */
   static Color diffLineColor(String line) {
      if (line == null || line.isEmpty())
         return null;
      if (line.startsWith("---") || line.startsWith("+++")
            || line.startsWith("diff --git"))
         return DIFF_HEADER_COLOR;
      char c0 = line.charAt(0);
      if (c0 == '-')
         return Color.red;
      if (c0 == '+')
         return Color.green;
      if (c0 == '@')
         return Color.cyan;
      return null;
   }
}
