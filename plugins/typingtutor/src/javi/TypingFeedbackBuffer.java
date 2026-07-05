package javi.typingtutor;

import javi.CellAttr;
import javi.Position;
import javi.ScreenAttributes;
import javi.StringIoc;
import javi.TextEdit;

import java.util.List;
import java.util.function.Supplier;

import static history.Tools.trace;

/**
 * A TextEdit buffer that provides real-time color feedback for typing
 * practice.
 *
 * <p>Overrides {@link #getTerminalAttributes()} to return a
 * {@link ScreenAttributes} grid that colors user-typed characters:
 * green for correct, red for incorrect. The colors are recalculated
 * on every render pass, giving immediate visual feedback as the user
 * types.</p>
 *
 * <p>Expected text lines are rendered in cyan to visually distinguish
 * them from the user's input lines.</p>
 */
final class TypingFeedbackBuffer extends TextEdit<String> {

   private final ScreenAttributes screenAttrs = new ScreenAttributes();
   private final List<String> expectedLines;
   private final int headerLines;

   /** Time when all user lines first had content, or 0 if not yet. */
   private volatile long finishTime;

   /** Time when the first user line got content, or 0 if not yet. */
   private volatile long firstTypingTime;

   /** Callback invoked when Enter is pressed on the last user line. */
   private Supplier<String> lessonCompleteHandler;

   /** Callback invoked when Enter is pressed on "Press enter" line. */
   private Runnable continueHandler;

   /** True once results have been appended to the buffer. */
   private volatile boolean resultsShown;

   /**
    * Sets the results-shown flag. Called by TypingTutorPlugin when
    * results are appended externally (e.g. on Escape).
    */
   void setResultsShown(boolean shown) {
      resultsShown = shown;
   }

   /** Buffer line number of the "Press enter to continue" prompt. */
   private volatile int continueLineNum;

   /** Green foreground for correctly-typed characters. */
   private static final int CORRECT_ATTR =
      CellAttr.pack(false, false, false, 2, -1);

   /** Bold red foreground for incorrectly-typed characters. */
   private static final int INCORRECT_ATTR =
      CellAttr.pack(true, false, false, 1, -1);

   /** Very dark red background for erroneous trailing spaces. */
   private static final int SPACE_ERROR_ATTR =
      CellAttr.pack(false, false, false, -1, 52);

   /** Cyan foreground for expected-text reference lines. */
   private static final int EXPECTED_ATTR =
      CellAttr.pack(false, false, false, 6, -1);

   /**
    * Creates a feedback buffer.
    *
    * @param sio the StringIoc providing initial content
    * @param expected the list of expected typing lines
    * @param headerLineCount number of header lines before content pairs
    */
   TypingFeedbackBuffer(StringIoc sio, List<String> expected,
         int headerLineCount) {
      super(sio, sio.prop);
      this.expectedLines = expected;
      this.headerLines = headerLineCount;
   }

   /**
    * Returns the epoch millis when all user lines first had content,
    * or 0 if the user has not yet finished typing all lines.
    */
   long getFinishTime() {
      return finishTime;
   }

   /**
    * Returns the epoch millis when the first user line got content
    * (i.e. the user started typing), or 0 if no typing yet.
    */
   long getFirstTypingTime() {
      return firstTypingTime;
   }

   /**
    * Sets the callback invoked when Enter is pressed on the last
    * user line. The callback computes and returns the results text
    * to append to the buffer.
    */
   void setLessonCompleteHandler(Supplier<String> handler) {
      this.lessonCompleteHandler = handler;
   }

   /**
    * Sets the callback invoked when Enter is pressed on the
    * "Press enter to continue" line after results are shown.
    */
   void setContinueHandler(Runnable handler) {
      this.continueHandler = handler;
   }

   /**
    * Intercepts newline insertion in the typing practice area.
    * Instead of inserting a new line, Enter moves the cursor to
    * the next user-typing line. On the last user line, the lesson
    * results are automatically appended to the buffer.
    */
   @Override
   protected Position inserttext(String iStr, int xstart, int ystart) {
      if (!iStr.contains("\n")) {
         trace("inserttext TEXT: y=" + ystart + " x=" + xstart
            + " len=" + iStr.length()
            + " text='" + iStr.substring(0, Math.min(iStr.length(), 40))
            + "' isFirst=" + (ystart == headerLines + 2));
         return super.inserttext(iStr, xstart, ystart);
      }

      // Handle "Press enter to continue" — start new practice
      if (resultsShown && continueHandler != null) {
         trace("inserttext CONTINUE: invoking continueHandler"
            + " y=" + ystart + " resultsShown=" + resultsShown);
         continueHandler.run();
         return new Position(0, ystart, fdes(), "typingpractice");
      }

      if (!isUserLine(ystart)) {
         trace("inserttext: non-user line y=" + ystart
            + " passing to super");
         return super.inserttext(iStr, xstart, ystart);
      }

      // Insert only the text before the newline
      int nlIdx = iStr.indexOf('\n');
      String before = iStr.substring(0, nlIdx);
      String lineBefore = at(ystart).toString();

      trace("inserttext ENTER: y=" + ystart + " x=" + xstart
         + " before='" + before + "' lineBefore='" + lineBefore
         + "' isFirst=" + (ystart == headerLines + 2));

      if (!before.isEmpty())
         super.inserttext(before, xstart, ystart);

      String lineAfter = at(ystart).toString();
      trace("inserttext ENTER: lineAfter='" + lineAfter + "'");

      // Guard: never advance past an empty user line. This catches
      // double-Enter, key-repeat, and any edge case where the text
      // commit didn't persist (e.g. action-key flush + timing).
      if (lineAfter.isEmpty()) {
         trace("inserttext ENTER: GUARD fired — line empty after"
            + " insert, NOT advancing");
         return new Position(xstart, ystart, fdes(),
            "typingpractice");
      }

      // Auto-check on last user line
      if (isLastUserLine(ystart) && lessonCompleteHandler != null) {
         if (finishTime == 0)
            finishTime = System.currentTimeMillis();
         String results = lessonCompleteHandler.get();
         if (results != null) {
            super.inserttext("\n" + results
               + "\nPress enter to continue", 0, readIn());
            resultsShown = true;
            continueLineNum = readIn();
         }
         return new Position(0, readIn(), fdes(),
            "typingpractice");
      }

      // Move cursor to next user-typing line
      trace("inserttext ENTER: advancing to y=" + (ystart + 2));
      return new Position(0, ystart + 2, fdes(), "typingpractice");
   }

   /** True if y is a user-typing line (even offset from header). */
   private boolean isUserLine(int y) {
      int offset = y - headerLines;
      return offset >= 2 && offset <= expectedLines.size() * 2
         && offset % 2 == 0;
   }

   /** True if y is the last user-typing line. */
   private boolean isLastUserLine(int y) {
      return y == headerLines + expectedLines.size() * 2;
   }

   @Override
   public ScreenAttributes getTerminalAttributes() {
      updateColors();
      return screenAttrs;
   }

   /**
    * Recalculates the color grid based on current buffer content
    * versus expected text. Called on every render pass.
    */
   private void updateColors() {
      screenAttrs.clear();

      boolean allTyped = true;
      int searchFrom = headerLines + 1;
      for (int i = 0; i < expectedLines.size(); i++) {
         String exp = expectedLines.get(i);

         // Search for expected line by content (insert-mode typing
         // may have shifted lines from their original positions)
         int expectedBufLine = -1;
         for (int line = searchFrom; line <= readIn(); line++) {
            if (at(line).toString().equals(exp)) {
               expectedBufLine = line;
               break;
            }
         }
         if (expectedBufLine < 0)
            continue;
         int userBufLine = expectedBufLine + 1;
         searchFrom = expectedBufLine + 2;

         // Color expected-text line in cyan
         if (!exp.isEmpty()) {
            screenAttrs.fillAttr(expectedBufLine, 0, exp.length(),
               EXPECTED_ATTR);
         }

         // Color user-typed line using edit-distance alignment so
         // a single insertion error doesn't cascade to the rest
         if (userBufLine > readIn()) {
            allTyped = false;
            continue;
         }
         String typed = at(userBufLine).toString();
         if (typed.isEmpty()) {
            allTyped = false;
            continue;
         }

         // Record when the user first starts typing
         if (firstTypingTime == 0)
            firstTypingTime = System.currentTimeMillis();

         boolean[] correct =
            EditDistance.alignCorrectness(exp, typed);
         for (int c = 0; c < typed.length(); c++) {
            int attr;
            if (correct[c]) {
               attr = CORRECT_ATTR;
            } else if (typed.charAt(c) == ' ') {
               attr = SPACE_ERROR_ATTR;
            } else {
               attr = INCORRECT_ATTR;
            }
            screenAttrs.setAttr(userBufLine, c, attr);
         }
      }

      // Record finish time when all user lines have content
      if (allTyped && finishTime == 0)
         finishTime = System.currentTimeMillis();
   }
}
