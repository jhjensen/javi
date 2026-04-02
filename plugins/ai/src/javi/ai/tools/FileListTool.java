package javi.ai.tools;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javi.ai.AIException;

/**
 * AI tool for listing directory contents.
 *
 * <p>Lists files and subdirectories in a given path. Directories
 * are indicated with a trailing slash. Results are sorted
 * alphabetically.</p>
 *
 * @see AITool
 * @see AIToolRegistry
 */
public final class FileListTool implements AITool {

   /** Maximum entries to return. */
   private static final int MAX_ENTRIES = 200;

   @Override
   public String name() {
      return "file_list";
   }

   @Override
   public String description() {
      return "List files and directories in a given path. "
         + "Directories are shown with a trailing '/'. "
         + "Limited to " + MAX_ENTRIES + " entries.";
   }

   @Override
   public String inputSchema() {
      return "{"
         + "\"type\":\"object\","
         + "\"properties\":{"
         + "\"path\":{"
         + "\"type\":\"string\","
         + "\"description\":\"Directory path to list\""
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
         throw new AIException(
            "file_list: 'path' parameter required");
      }

      Path dir = Path.of(pathStr);
      if (!Files.exists(dir)) {
         return "Error: path not found: " + pathStr;
      }
      if (!Files.isDirectory(dir)) {
         return "Error: not a directory: " + pathStr;
      }

      try (DirectoryStream<Path> stream =
            Files.newDirectoryStream(dir)) {
         List<String> entries = new ArrayList<>();
         for (Path entry : stream) {
            String entryName = entry.getFileName().toString();
            if (Files.isDirectory(entry)) {
               entryName += "/";
            }
            entries.add(entryName);
            if (entries.size() >= MAX_ENTRIES) {
               break;
            }
         }
         Collections.sort(entries);

         StringBuilder sb = new StringBuilder();
         sb.append("Directory: ").append(pathStr)
            .append(" (").append(entries.size())
            .append(" entries)\n");
         for (String entry : entries) {
            sb.append(entry).append('\n');
         }

         if (entries.size() >= MAX_ENTRIES) {
            sb.append("... truncated at ")
               .append(MAX_ENTRIES).append(" entries");
         }
         return sb.toString();
      } catch (IOException e) {
         return "Error listing directory: " + e.getMessage();
      }
   }
}
