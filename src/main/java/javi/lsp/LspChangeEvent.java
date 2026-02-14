package javi.lsp;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single content change event for LSP incremental sync.
 *
 * <p>Maps directly to a TextDocumentContentChangeEvent in the LSP
 * protocol. All line/character values are 0-based per LSP convention.
 * Provides factory methods that convert from Javi's 1-based ecache
 * indices to LSP 0-based positions.</p>
 *
 * @see LspClient#didChangeIncremental
 */
public final class LspChangeEvent {

   /** 0-based start line in the old document. */
   final int startLine;

   /** 0-based start character offset. */
   final int startChar;

   /** 0-based end line in the old document. */
   final int endLine;

   /** 0-based end character offset. */
   final int endChar;

   /** Replacement text (may contain newlines). */
   final String text;

   public LspChangeEvent(int sLine, int sChar,
         int eLine, int eChar, String txt) {
      this.startLine = sLine;
      this.startChar = sChar;
      this.endLine = eLine;
      this.endChar = eChar;
      this.text = txt;
   }

   /**
    * Creates a change event for a single-line modification.
    *
    * <p>Called before the edit is applied — the old line content is read
    * from ecache at the given index.</p>
    *
    * @param lineIndex 1-based ecache line index
    * @param oldText the old line content (before edit)
    * @param newText the new line content (replacement)
    * @return the change event
    */
   public static LspChangeEvent forModify(int lineIndex, String oldText,
         String newText) {
      int lspLine = lineIndex - 1;
      return new LspChangeEvent(lspLine, 0,
         lspLine, oldText.length(), newText);
   }

   /**
    * Creates a change event for inserting lines.
    *
    * <p>Called before the insert is applied. Handles three cases:
    * empty document, insert before existing line, and append at end.</p>
    *
    * @param insertIndex 1-based ecache index where lines are inserted
    * @param lines the line contents to insert
    * @param oldLineCount number of actual lines before this insert
    *     (ecache.size() - 1)
    * @param lastLineLength length of the last line before this insert;
    *     ignored when oldLineCount is 0
    * @return the change event
    */
   public static LspChangeEvent forInsert(int insertIndex, String[] lines,
         int oldLineCount, int lastLineLength) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < lines.length; i++) {
         if (i > 0)
            sb.append('\n');
         sb.append(lines[i]);
      }

      if (oldLineCount == 0) {
         return new LspChangeEvent(0, 0, 0, 0, sb.toString());
      }
      if (insertIndex <= oldLineCount) {
         int lspLine = insertIndex - 1;
         return new LspChangeEvent(lspLine, 0, lspLine, 0,
            sb.toString() + "\n");
      }
      // Appending after the last line
      int lastLspLine = oldLineCount - 1;
      return new LspChangeEvent(lastLspLine, lastLineLength,
         lastLspLine, lastLineLength, "\n" + sb.toString());
   }

   /**
    * Creates a change event for deleting lines.
    *
    * <p>Called before the delete is applied. Must supply line lengths
    * from the current ecache state. Handles three cases: delete in
    * middle, delete at end (preserving preceding newline), and
    * delete all lines.</p>
    *
    * @param start 1-based ecache index of first line to delete
    * @param count number of lines to delete
    * @param oldLineCount number of actual lines before this delete
    * @param prevLineLength length of ecache line at (start - 1), or
    *     -1 if start == 1
    * @param lastLineOfDocLength length of ecache line at oldLineCount
    * @return the change event
    */
   public static LspChangeEvent forDelete(int start, int count,
         int oldLineCount, int prevLineLength,
         int lastLineOfDocLength) {
      int end = start + count - 1;

      if (end < oldLineCount) {
         // Deleting in the middle — range includes trailing newlines
         return new LspChangeEvent(start - 1, 0,
            start + count - 1, 0, "");
      }
      if (start > 1) {
         // Deleting to end — include preceding newline
         return new LspChangeEvent(start - 2, prevLineLength,
            oldLineCount - 1, lastLineOfDocLength, "");
      }
      // Deleting all lines
      return new LspChangeEvent(0, 0,
         oldLineCount - 1, lastLineOfDocLength, "");
   }

   /**
    * Converts this event to an LSP contentChange map with range.
    *
    * @return the map suitable for the contentChanges array
    */
   Map<String, Object> toContentChange() {
      Map<String, Object> start = new HashMap<>();
      start.put("line", Integer.valueOf(startLine));
      start.put("character", Integer.valueOf(startChar));

      Map<String, Object> end = new HashMap<>();
      end.put("line", Integer.valueOf(endLine));
      end.put("character", Integer.valueOf(endChar));

      Map<String, Object> range = new HashMap<>();
      range.put("start", start);
      range.put("end", end);

      Map<String, Object> change = new HashMap<>();
      change.put("range", range);
      change.put("text", text);
      return change;
   }

   @Override
   public String toString() {
      return "LspChangeEvent[(" + startLine + "," + startChar
         + ")-(" + endLine + "," + endChar + ") text="
         + text.replace("\n", "\\n") + "]";
   }
}
