package javi;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Detects foldable regions in JSON files by matching brace
 * and bracket pairs. Each multi-line pair becomes a fold range.
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
    * Abstraction so we can test without depending on TextEdit.
    */
   public interface LineFetcher {
      /** Get line content. Lines are 1-based. */
      String getLine(int lineNumber);
   }
}
