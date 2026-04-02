package javi.ai.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javi.ai.AIException;

import static history.Tools.trace;

/**
 * AI tool for reading file contents.
 *
 * <p>Reads a file from disk and returns its contents. This is the
 * primary mechanism for giving the AI access to files without the
 * user having to paste content manually.</p>
 *
 * <p>Output is limited to a configurable number of lines to avoid
 * overwhelming the AI's context window.</p>
 *
 * @see AITool
 * @see AIToolRegistry
 */
public final class FileReadTool implements AITool {

   /** Maximum lines to return (avoids context overflow). */
   private static final int MAX_LINES = 500;

   /** Maximum characters to return. */
   private static final int MAX_CHARS = 40_000;

   @Override
   public String name() {
      return "file_read";
   }

   @Override
   public String description() {
      return "Read the contents of a file from disk. "
         + "Returns file text limited to " + MAX_LINES
         + " lines. Use start_line and end_line to read "
         + "specific sections of larger files.";
   }

   @Override
   public String inputSchema() {
      return "{"
         + "\"type\":\"object\","
         + "\"properties\":{"
         + "\"path\":{"
         + "\"type\":\"string\","
         + "\"description\":\"Absolute or relative file path to read\""
         + "},"
         + "\"start_line\":{"
         + "\"type\":\"integer\","
         + "\"description\":\"First line to read (1-based, optional)\""
         + "},"
         + "\"end_line\":{"
         + "\"type\":\"integer\","
         + "\"description\":\"Last line to read (1-based, inclusive, optional)\""
         + "}"
         + "},"
         + "\"required\":[\"path\"]"
         + "}";
   }

   @Override
   public PermissionLevel permissionLevel() {
      return PermissionLevel.AUTO;
   }

   @Override
   public String execute(Map<String, String> params)
         throws AIException {
      String pathStr = params.get("path");
      if (null == pathStr || pathStr.isEmpty()) {
         throw new AIException("file_read: 'path' parameter required");
      }

      Path path = Path.of(pathStr);
      if (!Files.exists(path)) {
         return "Error: file not found: " + pathStr;
      }
      if (!Files.isRegularFile(path)) {
         return "Error: not a regular file: " + pathStr;
      }
      if (!Files.isReadable(path)) {
         return "Error: file not readable: " + pathStr;
      }

      try {
         var allLines = Files.readAllLines(path);
         int totalLines = allLines.size();

         // Parse optional line range
         int startLine = 1;
         int endLine = totalLines;
         String startStr = params.get("start_line");
         String endStr = params.get("end_line");
         if (null != startStr && !startStr.isEmpty()) {
            startLine = Math.max(1, Integer.parseInt(startStr));
         }
         if (null != endStr && !endStr.isEmpty()) {
            endLine = Math.min(totalLines,
               Integer.parseInt(endStr));
         }

         // Enforce limits
         int lineCount = endLine - startLine + 1;
         if (lineCount > MAX_LINES) {
            endLine = startLine + MAX_LINES - 1;
            lineCount = MAX_LINES;
         }

         StringBuilder sb = new StringBuilder(lineCount * 80);
         sb.append("File: ").append(pathStr)
            .append(" (").append(totalLines).append(" lines total");
         if (startLine > 1 || endLine < totalLines) {
            sb.append(", showing lines ")
               .append(startLine).append('-').append(endLine);
         }
         sb.append(")\n");

         int charCount = sb.length();
         for (int i = startLine - 1;
               i < endLine && i < allLines.size(); i++) {
            String line = allLines.get(i);
            charCount += line.length() + 1;
            if (charCount > MAX_CHARS) {
               sb.append("\n... truncated at ")
                  .append(MAX_CHARS).append(" chars");
               break;
            }
            sb.append(line).append('\n');
         }

         if (endLine < totalLines) {
            sb.append("\n(").append(totalLines - endLine)
               .append(" more lines not shown)");
         }

         return sb.toString();
      } catch (IOException e) {
         return "Error reading file: " + e.getMessage();
      } catch (NumberFormatException e) {
         return "Error: invalid line number: " + e.getMessage();
      }
   }
}
