package javi.ai.tools;

import java.util.Map;

import javi.EditContainer;
import javi.EventQueue;
import javi.ai.AIException;

import static history.Tools.trace;

/**
 * AI tool for modifying editor buffer contents with undo support.
 *
 * <p>Edits are applied through the editor's normal editing API,
 * which means they participate in the undo history. The user can
 * undo AI-applied changes with the standard 'u' command.</p>
 *
 * <p>Supports two operations:</p>
 * <ul>
 *   <li><b>replace</b>: Replace a range of lines with new text</li>
 *   <li><b>insert</b>: Insert new lines at a position</li>
 * </ul>
 *
 * @see AITool
 * @see AIToolRegistry
 */
public final class BufferWriteTool implements AITool {

   @Override
   public String name() {
      return "buffer_write";
   }

   @Override
   public String description() {
      return "Modify an editor buffer's contents. Edits go "
         + "through the editor's undo system so the user can "
         + "reverse them. Use operation 'replace' to replace "
         + "lines, or 'insert' to add new lines.";
   }

   @Override
   public String inputSchema() {
      return "{"
         + "\"type\":\"object\","
         + "\"properties\":{"
         + "\"name\":{"
         + "\"type\":\"string\","
         + "\"description\":\"Buffer name to modify\""
         + "},"
         + "\"operation\":{"
         + "\"type\":\"string\","
         + "\"enum\":[\"replace\",\"insert\"],"
         + "\"description\":\"replace: overwrite lines "
         + "start_line..end_line; insert: add lines "
         + "after start_line\""
         + "},"
         + "\"start_line\":{"
         + "\"type\":\"integer\","
         + "\"description\":\"Line number (1-based). For "
         + "replace: first line to replace. For insert: "
         + "line after which to insert (0 = before line 1)\""
         + "},"
         + "\"end_line\":{"
         + "\"type\":\"integer\","
         + "\"description\":\"Last line to replace "
         + "(1-based, for replace only)\""
         + "},"
         + "\"text\":{"
         + "\"type\":\"string\","
         + "\"description\":\"New text content "
         + "(lines separated by newlines)\""
         + "}"
         + "},"
         + "\"required\":[\"name\",\"operation\","
         + "\"start_line\",\"text\"]"
         + "}";
   }

   @Override
   public PermissionLevel permissionLevel() {
      return PermissionLevel.CONFIRM_FIRST;
   }

   @Override
   @SuppressWarnings("unchecked")
   public String execute(Map<String, String> params)
         throws AIException {
      String bufName = params.get("name");
      String operation = params.get("operation");
      String startStr = params.get("start_line");
      String endStr = params.get("end_line");
      String text = params.get("text");

      if (null == bufName || bufName.isEmpty()) {
         throw new AIException(
            "buffer_write: 'name' parameter required");
      }
      if (null == operation || operation.isEmpty()) {
         throw new AIException(
            "buffer_write: 'operation' required "
            + "(replace or insert)");
      }
      if (null == startStr || startStr.isEmpty()) {
         throw new AIException(
            "buffer_write: 'start_line' required");
      }
      if (null == text) {
         text = "";
      }

      int startLine;
      try {
         startLine = Integer.parseInt(startStr);
      } catch (NumberFormatException e) {
         return "Error: invalid start_line: " + startStr;
      }

      EventQueue.biglock2.lock();
      try {
         EditContainer ec =
            EditContainer.grepfile(bufName);
         if (null == ec) {
            return "Error: buffer not found: "
               + bufName;
         }

         String[] newLines = text.split("\n", -1);
         if (newLines.length > 1
               && newLines[newLines.length - 1]
                  .isEmpty()) {
            String[] trimmed =
               new String[newLines.length - 1];
            System.arraycopy(
               newLines, 0, trimmed, 0,
               trimmed.length);
            newLines = trimmed;
         }

         switch (operation) {
            case "replace":
               return doReplace(ec, bufName,
                  startLine, endStr, newLines);
            case "insert":
               return doInsert(ec, bufName,
                  startLine, newLines);
            default:
               return "Error: unknown operation '"
                  + operation
                  + "'. Use 'replace' or 'insert'.";
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Replace a range of lines in the buffer.
    */
   @SuppressWarnings("unchecked")
   private String doReplace(EditContainer ec,
         String bufName, int startLine, String endStr,
         String[] newLines) {
      int totalLines = ec.readIn() - 1;
      int endLine = startLine;
      if (null != endStr && !endStr.isEmpty()) {
         try {
            endLine = Integer.parseInt(endStr);
         } catch (NumberFormatException e) {
            return "Error: invalid end_line: " + endStr;
         }
      }

      if (startLine < 1 || startLine > totalLines) {
         return "Error: start_line " + startLine
            + " out of range (1-" + totalLines + ")";
      }
      if (endLine < startLine || endLine > totalLines) {
         return "Error: end_line " + endLine
            + " out of range (" + startLine
            + "-" + totalLines + ")";
      }

      // Remove old lines from end to start
      int removeCount = endLine - startLine + 1;
      ec.remove(startLine, removeCount);

      // Insert new lines
      for (int i = 0; i < newLines.length; i++) {
         ec.insertOne(newLines[i], startLine + i);
      }

      ec.checkpoint();
      trace("buffer_write: replaced lines "
         + startLine + "-" + endLine + " with "
         + newLines.length + " lines in " + bufName);
      return "Replaced " + removeCount
         + " lines with " + newLines.length
         + " lines at line " + startLine;
   }

   /**
    * Insert new lines after a given position.
    */
   @SuppressWarnings("unchecked")
   private String doInsert(EditContainer ec,
         String bufName, int startLine,
         String[] newLines) {
      int totalLines = ec.readIn() - 1;
      if (startLine < 0 || startLine > totalLines) {
         return "Error: start_line " + startLine
            + " out of range (0-" + totalLines + ")";
      }

      int insertAt = startLine + 1;
      for (int i = 0; i < newLines.length; i++) {
         ec.insertOne(newLines[i], insertAt + i);
      }

      ec.checkpoint();
      trace("buffer_write: inserted "
         + newLines.length + " lines after line "
         + startLine + " in " + bufName);
      return "Inserted " + newLines.length
         + " lines after line " + startLine;
   }
}
