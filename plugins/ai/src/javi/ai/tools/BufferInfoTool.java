package javi.ai.tools;

import java.util.Map;

import javi.ai.AIException;

/**
 * AI tool for getting information about the current editor state.
 *
 * <p>Returns metadata about the current buffer and editor context:
 * buffer name, line count, cursor position, and whether a visual
 * selection is active.</p>
 *
 * <p>This tool does not access editor state directly (which would
 * require AWT thread access). Instead, it reports cached context
 * that was captured at request time via {@link #setContext}.</p>
 *
 * @see AITool
 * @see AIToolRegistry
 */
public final class BufferInfoTool implements AITool {

   private static volatile String cachedBufferName;
   private static volatile int cachedLineCount;
   private static volatile int cachedCursorLine;
   private static volatile int cachedCursorCol;
   private static volatile boolean cachedHasSelection;

   @Override
   public String name() {
      return "buffer_info";
   }

   @Override
   public String description() {
      return "Get information about the current editor buffer: "
         + "file name, line count, cursor position, and "
         + "whether a visual selection is active.";
   }

   @Override
   public String inputSchema() {
      return "{"
         + "\"type\":\"object\","
         + "\"properties\":{}"
         + "}";
   }

   @Override
   public PermissionLevel permissionLevel() {
      return PermissionLevel.AUTO;
   }

   @Override
   public String execute(Map<String, String> params)
         throws AIException {
      StringBuilder sb = new StringBuilder();
      sb.append("Buffer: ").append(
         null != cachedBufferName
            ? cachedBufferName : "(none)");
      sb.append('\n');
      sb.append("Lines: ").append(cachedLineCount);
      sb.append('\n');
      sb.append("Cursor: line ").append(cachedCursorLine)
         .append(", column ").append(cachedCursorCol);
      sb.append('\n');
      sb.append("Selection: ").append(
         cachedHasSelection ? "active" : "none");
      return sb.toString();
   }

   /**
    * Update the cached editor context.
    *
    * <p>Called from the AWT thread before dispatching an AI request,
    * so the tool has access to editor state without needing to
    * synchronize with the UI thread.</p>
    *
    * @param bufferName the current buffer name
    * @param lineCount total lines in the buffer
    * @param cursorLine current cursor line (1-based)
    * @param cursorCol current cursor column
    * @param hasSelection whether a visual selection is active
    */
   public static void setContext(String bufferName,
         int lineCount, int cursorLine, int cursorCol,
         boolean hasSelection) {
      cachedBufferName = bufferName;
      cachedLineCount = lineCount;
      cachedCursorLine = cursorLine;
      cachedCursorCol = cursorCol;
      cachedHasSelection = hasSelection;
   }
}
