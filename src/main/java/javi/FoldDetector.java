package javi;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Detects foldable regions in text buffers.
 * Supports JSON brace matching and indent-based detection.
 */
public final class FoldDetector {

   private FoldDetector() {
   }

   /**
    * Scan a buffer for JSON fold regions ({}/{@code []}).
    * Returns a new FoldModel with detected folds, all open.
    *
    * @param buffer text buffer to scan (1-based lines)
    * @param lineCount total lines in file (readIn())
    * @return FoldModel with detected folds
    */
   public static FoldModel detectJsonFolds(
         LineFetcher buffer, int lineCount) {
      FoldModel model = new FoldModel();
      Deque<Integer> stack = new ArrayDeque<>();

      for (int line = 1; line < lineCount; line++) {
         String text = buffer.getLine(line);
         if (text == null)
            continue;
         boolean inString = false;
         boolean inChar = false;
         boolean escaped = false;
         for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) {
               escaped = false;
               continue;
            }
            if (ch == '\\' && (inString || inChar)) {
               escaped = true;
               continue;
            }
            if (ch == '"' && !inChar) {
               inString = !inString;
               continue;
            }
            if (ch == '\'' && !inString) {
               inChar = !inChar;
               continue;
            }
            if (inString || inChar)
               continue;
            if (ch == '/' && i + 1 < text.length()
                  && text.charAt(i + 1) == '/')
               break;
            if (ch == '{' || ch == '[') {
               stack.push(line);
            } else if (ch == '}' || ch == ']') {
               if (!stack.isEmpty()) {
                  int startLine = stack.pop();
                  if (line > startLine)
                     model.addFold(startLine, line);
               }
            }
         }
      }
      return model;
   }

   /**
    * Detect foldable regions by indentation level (like vim's
    * {@code foldmethod=indent}). Lines at deeper indentation
    * are folded under lines at shallower indentation.
    *
    * <p>Blank lines inherit the minimum indent level of the
    * surrounding non-blank lines so they do not break folds.</p>
    *
    * @param buffer text buffer to scan (1-based lines)
    * @param lineCount total lines in file (readIn())
    * @param tabSize number of spaces per indent level
    * @return FoldModel with detected folds
    */
   public static FoldModel detectIndentFolds(
         LineFetcher buffer, int lineCount, int tabSize) {
      if (tabSize < 1)
         tabSize = 3;
      FoldModel model = new FoldModel();
      if (lineCount <= 2)
         return model;

      int lastLine = lineCount - 1;
      int[] levels = new int[lineCount];
      boolean[] blank = new boolean[lineCount];

      // Pass 1: compute raw indent levels
      for (int i = 1; i < lineCount; i++) {
         String text = buffer.getLine(i);
         if (text == null || text.isBlank()) {
            blank[i] = true;
            levels[i] = -1;
         } else {
            levels[i] = indentLevel(text, tabSize);
         }
      }

      // Pass 2: resolve blank lines to min of neighbors
      for (int i = 1; i < lineCount; i++) {
         if (blank[i]) {
            int prev = prevNonBlankLevel(levels, blank, i);
            int next =
               nextNonBlankLevel(levels, blank, i, lineCount);
            levels[i] = Math.min(prev, next);
         }
      }

      // Pass 3: stack-based fold detection
      Deque<int[]> stack = new ArrayDeque<>();
      for (int line = 1; line <= lastLine; line++) {
         int level = levels[line];
         // Close folds whose level >= current level
         while (!stack.isEmpty()
               && stack.peek()[0] >= level) {
            int[] top = stack.pop();
            if (line - 1 > top[1])
               model.addFold(top[1], line - 1);
         }
         // Start a new fold if next line is deeper
         if (line < lastLine
               && levels[line + 1] > level) {
            stack.push(new int[]{level, line});
         }
      }
      // Close any remaining open folds
      while (!stack.isEmpty()) {
         int[] top = stack.pop();
         if (lastLine > top[1])
            model.addFold(top[1], lastLine);
      }
      return model;
   }

   /**
    * Count leading whitespace columns and divide by tabSize.
    * Tabs advance to the next multiple of tabSize.
    */
   static int indentLevel(String line, int tabSize) {
      int cols = 0;
      for (int i = 0; i < line.length(); i++) {
         char ch = line.charAt(i);
         if (ch == ' ')
            cols++;
         else if (ch == '\t')
            cols += tabSize - (cols % tabSize);
         else
            break;
      }
      return tabSize > 0 ? cols / tabSize : 0;
   }

   private static int prevNonBlankLevel(
         int[] levels, boolean[] blank, int from) {
      for (int i = from - 1; i >= 1; i--) {
         if (!blank[i])
            return levels[i];
      }
      return 0;
   }

   private static int nextNonBlankLevel(
         int[] levels, boolean[] blank,
         int from, int lineCount) {
      for (int i = from + 1; i < lineCount; i++) {
         if (!blank[i])
            return levels[i];
      }
      return 0;
   }

   /**
    * Detect foldable regions by vim-style markers
    * ({@code {{{} / {@code }}}}). Supports optional level
    * numbers: {@code {{{1} matches {@code }}}1}.
    *
    * <p>Unmatched {@code {{{} markers extend to end of file.
    * Markers are found anywhere on a line (typically in
    * comments).</p>
    *
    * @param buffer text buffer to scan (1-based lines)
    * @param lineCount total lines in file (readIn())
    * @return FoldModel with detected folds
    */
   public static FoldModel detectMarkerFolds(
         LineFetcher buffer, int lineCount) {
      FoldModel model = new FoldModel();
      if (lineCount <= 2)
         return model;

      int lastLine = lineCount - 1;
      // Stack of [level, startLine]. Level -1 means unleveled.
      Deque<int[]> stack = new ArrayDeque<>();

      for (int line = 1; line < lineCount; line++) {
         String text = buffer.getLine(line);
         if (text == null)
            continue;
         // Check for end marker first so a line with both
         // close and open is handled correctly.
         int endIdx = text.indexOf("}}}");
         if (endIdx >= 0) {
            int endLevel = parseMarkerLevel(
               text, endIdx + 3);
            closeMarker(model, stack, endLevel, line);
         }
         int startIdx = indexOfStartMarker(text);
         if (startIdx >= 0) {
            int startLevel = parseMarkerLevel(
               text, startIdx + 3);
            stack.push(new int[]{startLevel, line});
         }
      }

      // Close any unmatched start markers at EOF
      while (!stack.isEmpty()) {
         int[] top = stack.pop();
         if (lastLine > top[1])
            model.addFold(top[1], lastLine);
      }
      return model;
   }

   /**
    * Find "{{{" that is NOT preceded by "}" (which would
    * make it part of "}}}{"). Returns -1 if not found.
    */
   private static int indexOfStartMarker(String text) {
      int pos = 0;
      while (pos < text.length()) {
         int idx = text.indexOf("{{{", pos);
         if (idx < 0)
            return -1;
         // Reject if this is actually "}}}" + "{{{"
         // overlapping — the "}}}" at idx-1 already ended.
         // But a real concern: "}}}{{{". The "}}}" is at
         // idx-1 but that's fine — they are separate tokens.
         // We only need to avoid finding "{{{" inside
         // "}}}" — but that can't happen since "}" != "{".
         return idx;
      }
      return -1;
   }

   /**
    * Parse an optional level number immediately after the
    * 3-char marker. Returns -1 if no level specified.
    */
   static int parseMarkerLevel(
         String text, int afterMarker) {
      if (afterMarker >= text.length())
         return -1;
      char ch = text.charAt(afterMarker);
      if (ch >= '0' && ch <= '9')
         return ch - '0';
      return -1;
   }

   /**
    * Close the most recent matching start marker on the
    * stack and add the fold. Leveled end markers match
    * the nearest start marker with the same level;
    * unlabeled end markers match the nearest start marker.
    */
   private static void closeMarker(
         FoldModel model, Deque<int[]> stack,
         int endLevel, int line) {
      if (stack.isEmpty())
         return;
      if (endLevel < 0) {
         // Unlabeled end: match nearest start
         int[] top = stack.pop();
         if (line > top[1])
            model.addFold(top[1], line);
      } else {
         // Leveled end: find matching level
         Deque<int[]> temp = new ArrayDeque<>();
         boolean found = false;
         while (!stack.isEmpty()) {
            int[] top = stack.pop();
            if (!found && top[0] == endLevel) {
               if (line > top[1])
                  model.addFold(top[1], line);
               found = true;
               break;
            }
            temp.push(top);
         }
         // Restore unmatched entries
         while (!temp.isEmpty())
            stack.push(temp.pop());
         if (!found) {
            // No matching level — treat as unlabeled
            if (!stack.isEmpty()) {
               int[] top = stack.pop();
               if (line > top[1])
                  model.addFold(top[1], line);
            }
         }
      }
   }

   /**
    * Detect foldable regions in Markdown files based on
    * header levels ({@code #}, {@code ##}, {@code ###}, etc.)
    * and list blocks. Each header starts a fold that extends
    * to the next header of the same or higher level, or to
    * end of file. A non-list line followed by list items
    * creates a fold from the introducing line through the
    * last consecutive list item.
    *
    * @param buffer text buffer to scan (1-based lines)
    * @param lineCount total lines in file (readIn())
    * @return FoldModel with detected folds
    */
   public static FoldModel detectMarkdownFolds(
         LineFetcher buffer, int lineCount) {
      FoldModel model = new FoldModel();
      if (lineCount <= 2)
         return model;

      int lastLine = lineCount - 1;
      // Stack of [headerLevel, startLine]
      Deque<int[]> stack = new ArrayDeque<>();

      for (int line = 1; line < lineCount; line++) {
         String text = buffer.getLine(line);
         if (text == null)
            continue;
         int level = markdownHeaderLevel(text);
         if (level > 0) {
            // Close folds at same or deeper level
            while (!stack.isEmpty()
                  && stack.peek()[0] >= level) {
               int[] top = stack.pop();
               if (line - 1 > top[1])
                  model.addFold(top[1], line - 1);
            }
            stack.push(new int[]{level, line});
         }
      }
      // Close remaining folds at EOF
      while (!stack.isEmpty()) {
         int[] top = stack.pop();
         if (lastLine > top[1])
            model.addFold(top[1], lastLine);
      }

      // Second pass: detect list blocks
      detectMarkdownLists(buffer, lineCount, model);

      return model;
   }

   /**
    * Detect list blocks in Markdown. A non-blank, non-list
    * line followed by one or more list items creates a fold
    * from the introducing line through the last consecutive
    * list item.
    */
   private static void detectMarkdownLists(
         LineFetcher buffer, int lineCount,
         FoldModel model) {
      int lastLine = lineCount - 1;
      int line = 1;
      while (line <= lastLine) {
         String text = buffer.getLine(line);
         if (text == null || text.isBlank()
               || isListItem(text)
               || markdownHeaderLevel(text) > 0) {
            line++;
            continue;
         }
         // Non-list, non-blank, non-header line — check
         // if next line starts a list
         int nextLine = line + 1;
         if (nextLine > lastLine) {
            line++;
            continue;
         }
         String nextText = buffer.getLine(nextLine);
         if (nextText == null || !isListItem(nextText)) {
            line++;
            continue;
         }
         // Found a list block — scan to end of list
         int listEnd = nextLine;
         for (int k = nextLine + 1; k <= lastLine; k++) {
            String lt = buffer.getLine(k);
            if (lt == null || (!isListItem(lt)
                  && !lt.isBlank()))
               break;
            if (isListItem(lt))
               listEnd = k;
         }
         if (listEnd > line)
            model.addFold(line, listEnd);
         line = listEnd + 1;
      }
   }

   /**
    * Return true if the line is a Markdown list item.
    * Matches lines starting with optional whitespace then
    * {@code - }, {@code * }, {@code + }, or a number
    * followed by {@code . } or {@code ) }.
    */
   static boolean isListItem(String text) {
      int len = text.length();
      int i = 0;
      while (i < len && (text.charAt(i) == ' '
            || text.charAt(i) == '\t'))
         i++;
      if (i >= len)
         return false;
      char ch = text.charAt(i);
      if ((ch == '-' || ch == '*' || ch == '+')
            && i + 1 < len && text.charAt(i + 1) == ' ')
         return true;
      if (ch >= '0' && ch <= '9') {
         int j = i + 1;
         while (j < len && text.charAt(j) >= '0'
               && text.charAt(j) <= '9')
            j++;
         if (j < len && (text.charAt(j) == '.'
               || text.charAt(j) == ')')
               && j + 1 < len
               && text.charAt(j + 1) == ' ')
            return true;
      }
      return false;
   }

   /**
    * Return the Markdown header level (1-6) for a line
    * starting with one or more '#' followed by a space.
    * Returns 0 if the line is not a header.
    */
   static int markdownHeaderLevel(String text) {
      int len = text.length();
      int i = 0;
      while (i < len && text.charAt(i) == '#')
         i++;
      if (i > 0 && i <= 6 && i < len
            && text.charAt(i) == ' ')
         return i;
      return 0;
   }

   /**
    * Abstraction so we can test without depending on TextEdit.
    */
   public interface LineFetcher {
      /** Get line content. Lines are 1-based. */
      String getLine(int lineNumber);
   }
}
