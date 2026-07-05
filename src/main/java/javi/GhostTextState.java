package javi;

/**
 * State machine for insert-mode ghost text (AI tab completion).
 *
 * <p>States:
 * <ul>
 *   <li>{@code IDLE} — no ghost text active</li>
 *   <li>{@code PENDING} — AI request in flight</li>
 *   <li>{@code VISIBLE} — ghost text displayed, awaiting accept/dismiss</li>
 * </ul>
 *
 * <p>Transitions:
 * <pre>
 * IDLE → (Tab with text) → PENDING
 * PENDING → (completion arrives) → VISIBLE
 * PENDING → (Esc/any key) → IDLE (cancel request)
 * VISIBLE → (Tab) → IDLE (accept + insert text)
 * VISIBLE → (Esc) → IDLE (dismiss)
 * VISIBLE → (any char) → IDLE (dismiss + insert char)
 * </pre>
 */
public final class GhostTextState {

   /** Ghost text state values. */
   public enum State {
      IDLE,
      PENDING,
      VISIBLE
   }

   private static volatile State state = State.IDLE;
   private static volatile String[] completionLines;
   private static volatile int completionLine;
   private static volatile int completionCol;

   private GhostTextState() {
   }

   /** Get the current ghost text state. */
   public static State getState() {
      return state;
   }

   /** Transition to PENDING (AI request in flight). */
   public static void requestStarted() {
      state = State.PENDING;
   }

   /**
    * Transition to VISIBLE with the completed text.
    *
    * @param lines the completion text split by newline
    * @param line the cursor line where ghost starts
    * @param col the cursor column where ghost starts
    */
   public static void completionArrived(
         String[] lines, int line, int col) {
      completionLines = lines;
      completionLine = line;
      completionCol = col;
      state = State.VISIBLE;
      View.setGhostText(lines[0], line, col);
   }

   /**
    * Accept ghost text — inserts into the buffer.
    *
    * @param fvc the current file-view context
    */
   @SuppressWarnings("unchecked")
   public static void accept(FvContext fvc) {
      if (state != State.VISIBLE || completionLines == null) {
         return;
      }
      EditContainer ec = fvc.edvec;
      int curLine = completionLine;
      String[] lines = completionLines;

      String currentLine = ec.at(curLine).toString();
      String merged = currentLine.substring(
            0, completionCol)
         + lines[0]
         + currentLine.substring(completionCol);
      ec.changeElementAtStr(merged, curLine);

      for (int i = 1; i < lines.length; i++) {
         ec.insertOne(lines[i], curLine + i);
      }

      // Mark all AI-inserted lines yellow
      View.markAiInserted(curLine, lines.length);

      int newCol = (lines.length == 1)
         ? completionCol + lines[0].length()
         : lines[lines.length - 1].length();
      fvc.cursorabs(newCol,
         curLine + lines.length - 1);

      reset();
      fvc.vi.repaint();
      UI.reportMessage("AI: inserted " + lines.length
         + " line" + (lines.length > 1 ? "s" : ""));
   }

   /** Dismiss ghost text without inserting. */
   public static void dismiss() {
      reset();
   }

   /** Reset to IDLE, clearing all ghost state. */
   public static void reset() {
      state = State.IDLE;
      completionLines = null;
      View.clearGhostText();
   }

   /** Check whether ghost text is currently visible. */
   public static boolean isVisible() {
      return state == State.VISIBLE;
   }

   /** Check whether an AI request is in flight. */
   public static boolean isPending() {
      return state == State.PENDING;
   }
}
