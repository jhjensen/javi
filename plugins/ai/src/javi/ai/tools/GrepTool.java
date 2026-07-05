package javi.ai.tools;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import javi.ai.AIException;

import static history.Tools.trace;

/**
 * AI tool for searching text across files.
 *
 * <p>Performs regex or literal text search across files in a
 * directory tree. Returns matching lines with file paths and
 * line numbers, similar to {@code grep -rn}.</p>
 *
 * <p>Search is bounded by configurable limits on the number of
 * matches returned and the directory depth traversed to avoid
 * overwhelming the AI's context window.</p>
 *
 * @see AITool
 * @see AIToolRegistry
 */
public final class GrepTool implements AITool {

   /** Maximum matches to return. */
   private static final int MAX_MATCHES = 100;

   /** Maximum directory depth to traverse. */
   private static final int MAX_DEPTH = 10;

   /** Maximum file size to search (1 MB). */
   private static final long MAX_FILE_SIZE = 1_048_576;

   @Override
   public String name() {
      return "grep";
   }

   @Override
   public String description() {
      return "Search for a text pattern across files in a "
         + "directory. Returns matching lines with file paths "
         + "and line numbers. Supports regex patterns. "
         + "Limited to " + MAX_MATCHES + " matches.";
   }

   @Override
   public String inputSchema() {
      return "{"
         + "\"type\":\"object\","
         + "\"properties\":{"
         + "\"pattern\":{"
         + "\"type\":\"string\","
         + "\"description\":\"Text or regex pattern to search for\""
         + "},"
         + "\"path\":{"
         + "\"type\":\"string\","
         + "\"description\":\"Directory path to search in "
         + "(default: current directory)\""
         + "},"
         + "\"include\":{"
         + "\"type\":\"string\","
         + "\"description\":\"File name pattern to include "
         + "(e.g. *.java). Default: all files\""
         + "},"
         + "\"case_sensitive\":{"
         + "\"type\":\"boolean\","
         + "\"description\":\"Whether search is case sensitive "
         + "(default: true)\""
         + "}"
         + "},"
         + "\"required\":[\"pattern\"]"
         + "}";
   }

   @Override
   public PermissionLevel permissionLevel() {
      return PermissionLevel.AUTO;
   }

   @Override
   public String execute(Map<String, String> params)
         throws AIException {
      String patternStr = params.get("pattern");
      if (null == patternStr || patternStr.isEmpty()) {
         throw new AIException(
            "grep: 'pattern' parameter required");
      }

      String pathStr = params.get("path");
      Path searchDir = (null != pathStr && !pathStr.isEmpty())
         ? Path.of(pathStr)
         : Path.of(System.getProperty("user.dir"));

      if (!Files.isDirectory(searchDir)) {
         return "Error: not a directory: " + searchDir;
      }

      String includeGlob = params.get("include");
      String caseSensitive = params.get("case_sensitive");
      int flags = !"false".equalsIgnoreCase(caseSensitive)
         ? 0 : Pattern.CASE_INSENSITIVE;

      Pattern pattern;
      try {
         pattern = Pattern.compile(patternStr, flags);
      } catch (PatternSyntaxException e) {
         return "Error: invalid regex pattern: "
            + e.getMessage();
      }

      StringBuilder sb = new StringBuilder(4096);
      sb.append("Search: /").append(patternStr).append("/");
      if (0 != (flags & Pattern.CASE_INSENSITIVE)) {
         sb.append('i');
      }
      sb.append(" in ").append(searchDir).append('\n');

      int[] matchCount = {0};

      try (Stream<Path> walker = Files.walk(
            searchDir, MAX_DEPTH,
            FileVisitOption.FOLLOW_LINKS)) {
         walker
            .filter(Files::isRegularFile)
            .filter(p -> matchesInclude(p, includeGlob))
            .filter(p -> isSearchable(p))
            .forEach(file -> {
               if (matchCount[0] >= MAX_MATCHES) {
                  return;
               }
               searchFile(file, searchDir, pattern,
                  sb, matchCount);
            });
      } catch (IOException e) {
         return "Error walking directory: " + e.getMessage();
      }

      sb.append('\n').append(matchCount[0])
         .append(" match")
         .append(matchCount[0] == 1 ? "" : "es")
         .append(" found");
      if (matchCount[0] >= MAX_MATCHES) {
         sb.append(" (limit reached, results truncated)");
      }

      trace("grep: " + matchCount[0] + " matches for '"
         + patternStr + "' in " + searchDir);
      return sb.toString();
   }

   private void searchFile(Path file, Path baseDir,
         Pattern pattern, StringBuilder sb,
         int[] matchCount) {
      try {
         var lines = Files.readAllLines(file);
         String relPath = baseDir.relativize(file).toString();
         for (int i = 0; i < lines.size(); i++) {
            if (matchCount[0] >= MAX_MATCHES) {
               return;
            }
            String line = lines.get(i);
            Matcher m = pattern.matcher(line);
            if (m.find()) {
               matchCount[0]++;
               sb.append(relPath).append(':')
                  .append(i + 1).append(':')
                  .append(trimLine(line))
                  .append('\n');
            }
         }
      } catch (IOException e) {
         // Skip unreadable files silently
      }
   }

   private static boolean matchesInclude(
         Path file, String includeGlob) {
      if (null == includeGlob || includeGlob.isEmpty()) {
         return true;
      }
      String name = file.getFileName().toString();
      return globMatch(name, includeGlob);
   }

   private static boolean isSearchable(Path file) {
      try {
         long size = Files.size(file);
         if (size > MAX_FILE_SIZE || size == 0) {
            return false;
         }
      } catch (IOException e) {
         return false;
      }
      String name = file.getFileName().toString();
      // Skip binary-ish extensions
      return !name.endsWith(".class")
         && !name.endsWith(".jar")
         && !name.endsWith(".zip")
         && !name.endsWith(".gz")
         && !name.endsWith(".png")
         && !name.endsWith(".jpg")
         && !name.endsWith(".gif")
         && !name.endsWith(".ico")
         && !name.endsWith(".so")
         && !name.endsWith(".dylib")
         && !name.endsWith(".o");
   }

   private static String trimLine(String line) {
      if (line.length() <= 200) {
         return line;
      }
      return line.substring(0, 200) + "...";
   }

   /**
    * Simple glob matching for file name patterns.
    * Supports {@code *} (any chars) and {@code ?} (single char).
    */
   public static boolean globMatch(String text, String glob) {
      String regex = "^"
         + glob.replace(".", "\\.")
               .replace("*", ".*")
               .replace("?", ".")
         + "$";
      return text.matches(regex);
   }
}
