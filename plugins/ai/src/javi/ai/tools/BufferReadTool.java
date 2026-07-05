package javi.ai.tools;

import java.util.Map;

import javi.EditContainer;
import javi.EventQueue;
import javi.ai.AIException;

import static history.Tools.trace;

/**
 * AI tool for reading editor buffer contents.
 *
 * <p>Reads from buffers already open in the editor, avoiding
 * external file I/O. This allows the AI to access the current
 * in-memory state of files being edited, including unsaved
 * changes.</p>
 *
 * <p>If the requested buffer is not open, returns an error
 * message rather than reading from disk — use {@link FileReadTool}
 * for disk reads.</p>
 *
 * @see AITool
 * @see AIToolRegistry
 */
public final class BufferReadTool implements AITool {

   /** Maximum lines to return. */
   private static final int MAX_LINES = 500;

   @Override
   public String name() {
      return "buffer_read";
   }

   @Override
   public String description() {
      return "Read contents of an editor buffer (open file). "
         + "Returns the in-memory text including unsaved edits. "
         + "Use buffer_info to see available buffers. "
         + "Use start_line and end_line for large files.";
   }

   @Override
   public String inputSchema() {
      return "{"
         + "\"type\":\"object\","
         + "\"properties\":{"
         + "\"name\":{"
         + "\"type\":\"string\","
         + "\"description\":\"Buffer name (filename or "
         + "special name like *ai-chat*)\""
         + "},"
         + "\"start_line\":{"
         + "\"type\":\"integer\","
         + "\"description\":\"First line to read "
         + "(1-based, optional)\""
         + "},"
         + "\"end_line\":{"
         + "\"type\":\"integer\","
         + "\"description\":\"Last line to read "
         + "(1-based, inclusive, optional)\""
         + "}"
         + "},"
         + "\"required\":[\"name\"]"
         + "}";
   }

   @Override
   public PermissionLevel permissionLevel() {
      return PermissionLevel.AUTO;
   }

   @Override
   @SuppressWarnings("unchecked")
   public String execute(Map<String, String> params)
         throws AIException {
      String bufName = params.get("name");
      if (null == bufName || bufName.isEmpty()) {
         throw new AIException(
            "buffer_read: 'name' parameter required");
      }

      EventQueue.biglock2.lock();
      try {
         return readBufferLocked(bufName, params);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @SuppressWarnings("unchecked")
   private String readBufferLocked(String bufName,
         Map<String, String> params) {
      EditContainer ec =
         EditContainer.grepfile(bufName);
      if (null == ec) {
         return "Error: buffer not found: " + bufName
            + ". Use buffer_info to list open buffers.";
      }

      int totalLines = ec.readIn() - 1;
      if (totalLines <= 0) {
         return "(empty buffer)";
      }

      int startLine = 1;
      int endLine = totalLines;

      String startStr = params.get("start_line");
      String endStr = params.get("end_line");
      if (null != startStr && !startStr.isEmpty()) {
         try {
            startLine = Integer.parseInt(startStr);
         } catch (NumberFormatException e) {
            return "Error: invalid start_line: "
               + startStr;
         }
      }
      if (null != endStr && !endStr.isEmpty()) {
         try {
            endLine = Integer.parseInt(endStr);
         } catch (NumberFormatException e) {
            return "Error: invalid end_line: "
               + endStr;
         }
      }

      if (startLine < 1) startLine = 1;
      if (endLine > totalLines) endLine = totalLines;
      if (startLine > endLine) {
         return "Error: start_line > end_line";
      }

      int lineCount = endLine - startLine + 1;
      if (lineCount > MAX_LINES) {
         endLine = startLine + MAX_LINES - 1;
         lineCount = MAX_LINES;
      }

      StringBuilder sb = new StringBuilder(lineCount * 80);
      for (int i = startLine; i <= endLine; i++) {
         Object line = ec.at(i);
         if (null != line) {
            sb.append(line.toString());
         }
         sb.append('\n');
      }

      String header = "Buffer: " + bufName
         + " (lines " + startLine + "-" + endLine
         + " of " + totalLines + ")\n";
      trace("buffer_read: " + bufName + " lines "
         + startLine + "-" + endLine);
      return header + sb.toString();
   }
}
