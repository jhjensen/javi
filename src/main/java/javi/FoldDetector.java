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
         for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
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
    * Abstraction so we can test without depending on TextEdit.
    */
   public interface LineFetcher {
      /** Get line content. Lines are 1-based. */
      String getLine(int lineNumber);
   }
}
