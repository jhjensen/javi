package javi;

/**
 * Abstract interface for VT100 terminal screen operations.
 *
 * <p>VScreen defines the operations needed to emulate a VT100 terminal display.
 * The {@link Vt100Parser} translates escape sequences into calls to these methods,
 * and concrete implementations update the actual display buffer.</p>
 *
 * <h2>Coordinate System</h2>
 * <ul>
 *   <li>X coordinates are 1-based column numbers (1 = leftmost column)</li>
 *   <li>Y coordinates are 1-based row numbers (1 = top row)</li>
 *   <li>Origin (1,1) is at the top-left corner</li>
 * </ul>
 *
 * <h2>Buffer Management</h2>
 * <p>Most methods accept a StringBuilder parameter containing pending text
 * that should be flushed to the screen before the operation. This allows
 * batching of character output for efficiency.</p>
 *
 * <h2>Implementation Notes</h2>
 * <p>The concrete implementation {@link Vt100.ECScreen} maintains cursor position
 * and updates a {@link TextEdit} buffer as the backing store.</p>
 *
 * @see Vt100Parser
 * @see Vt100
 */
abstract class VScreen {

   /**
    * Moves the cursor horizontally by the specified amount.
    *
    * @param amount columns to move (positive = right, negative = left)
    * @param sb pending output buffer to flush
    */
   abstract void incX(int amount, StringBuilder sb);

   /**
    * Moves the cursor vertically by the specified amount.
    *
    * @param amount rows to move (positive = down, negative = up)
    * @param sb pending output buffer to flush
    */
   abstract void incY(int amount, StringBuilder sb);

   /**
    * Sets the cursor to the specified column.
    *
    * @param val the 1-based column number
    * @param sb pending output buffer to flush
    */
   abstract void setX(int val, StringBuilder sb);

   /**
    * Sets the cursor to the specified row.
    *
    * @param val the 1-based row number
    * @param sb pending output buffer to flush
    */
   abstract void setY(int val, StringBuilder sb);

   /**
    * Sets the cursor to the specified absolute position.
    *
    * @param xval the 1-based column number
    * @param yval the 1-based row number
    * @param sb pending output buffer to flush
    */
   abstract void setXY(int xval, int yval, StringBuilder sb);

   /**
    * Erases the entire screen.
    *
    * <p>Implements ESC[2J (erase entire display).</p>
    *
    * @param sb pending output buffer to flush
    */
   abstract void eraseScreen(StringBuilder sb);

   /**
    * Erases from cursor to end of line.
    *
    * <p>Implements ESC[0K (erase from cursor to end of line).</p>
    *
    * @param sb pending output buffer to flush
    */
   abstract void eraseToEnd(StringBuilder sb);

   /**
    * Erases the entire current line.
    *
    * <p>Implements ESC[2K (erase entire line).</p>
    *
    * @param sb pending output buffer to flush
    */
   abstract void eraseLine(StringBuilder sb);

   /**
    * Erases the specified number of characters at cursor.
    *
    * <p>Implements ESC[nP (delete n characters).</p>
    *
    * @param count number of characters to erase
    * @param sb pending output buffer to flush
    */
   abstract void eraseChars(int count, StringBuilder sb);

   /**
    * Inserts blank lines at the cursor position.
    *
    * <p>Implements ESC[nL (insert n lines).</p>
    *
    * @param count number of blank lines to insert
    * @param sb pending output buffer to flush
    */
   abstract void insertLines(int count, StringBuilder sb);

   /**
    * Sets or clears insert mode.
    *
    * <p>In insert mode, new characters push existing text to the right.
    * In overwrite mode (default), new characters replace existing text.</p>
    *
    * @param val true to enable insert mode, false for overwrite
    * @param sb pending output buffer to flush
    */
   abstract void setInsertMode(boolean val, StringBuilder sb);

   /**
    * Updates the screen display with pending changes.
    *
    * <p>Flushes the pending output buffer and repaints the display.</p>
    *
    * @param sb pending output buffer to flush
    */
   abstract void updateScreen(StringBuilder sb);

   /**
    * Saves the current cursor position.
    *
    * <p>Implements ESC 7 (save cursor).</p>
    *
    * @param sb pending output buffer to flush
    */
   abstract void saveCursor(StringBuilder sb);

   /**
    * Restores the previously saved cursor position.
    *
    * <p>Implements ESC 8 (restore cursor).</p>
    *
    * @param sb pending output buffer to flush
    */
   abstract void restoreCursor(StringBuilder sb);

   /**
    * Deletes the specified number of lines at cursor.
    *
    * <p>Implements ESC[nM (delete n lines).</p>
    *
    * @param count number of lines to delete
    * @param sb pending output buffer to flush
    */
   void deleteLines(int count, StringBuilder sb) {
      // Default no-op implementation for backwards compatibility
   }

   /**
    * Scrolls the screen up by the specified number of lines.
    *
    * <p>Implements ESC[nS (scroll up n lines).</p>
    *
    * @param count number of lines to scroll
    * @param sb pending output buffer to flush
    */
   void scrollUp(int count, StringBuilder sb) {
      // Default no-op implementation
   }

   /**
    * Scrolls the screen down by the specified number of lines.
    *
    * <p>Implements ESC[nT (scroll down n lines).</p>
    *
    * @param count number of lines to scroll
    * @param sb pending output buffer to flush
    */
   void scrollDown(int count, StringBuilder sb) {
      // Default no-op implementation
   }

   /**
    * Erases from cursor to beginning of line.
    *
    * <p>Implements ESC[1K (erase from beginning of line to cursor).</p>
    *
    * @param sb pending output buffer to flush
    */
   void eraseToBeginning(StringBuilder sb) {
      // Default no-op implementation
   }

   /**
    * Erases from cursor to beginning of screen.
    *
    * <p>Implements ESC[1J (erase from beginning of screen to cursor).</p>
    *
    * @param sb pending output buffer to flush
    */
   void eraseScreenToBeginning(StringBuilder sb) {
      // Default no-op implementation
   }

   /**
    * Erases from cursor to end of screen.
    *
    * <p>Implements ESC[0J (erase from cursor to end of screen).</p>
    *
    * @param sb pending output buffer to flush
    */
   void eraseScreenToEnd(StringBuilder sb) {
      // Default no-op implementation
   }

   /**
    * Sets text attributes (bold, underline, color, etc.).
    *
    * <p>Implements ESC[nm where n is an SGR parameter.</p>
    *
    * @param params array of SGR parameters
    * @param sb pending output buffer to flush
    */
   void setGraphicRendition(int[] params, StringBuilder sb) {
      // Default no-op - terminal doesn't support colors
   }

   /**
    * Rings the terminal bell.
    *
    * <p>Implements BEL (0x07) character.</p>
    */
   void bell() {
      // Default no-op
   }

   /**
    * Sets the terminal title.
    *
    * <p>Implements OSC 0;title BEL or OSC 2;title BEL.</p>
    *
    * @param title the new window title
    */
   void setTitle(String title) {
      // Default no-op
   }

   /**
    * F10: Switch between main and alternate screen buffer.
    *
    * <p>Implements DEC private modes 47, 1047, and 1049.
    * When enabled, saves the current screen and shows a blank alternate
    * screen. When disabled, restores the saved screen.</p>
    *
    * @param enable true to switch to alternate buffer, false to restore
    * @param sb pending output buffer to flush
    */
   void switchAlternateScreen(boolean enable, StringBuilder sb) {
      // Default no-op — subclasses implement actual buffer swap
   }

   /**
    * Sets mouse tracking mode.
    *
    * @param mode 0=off, 1000=normal, 1002=button-event, 1003=any-event
    * @param enable true to enable, false to disable
    */
   void setMouseTracking(int mode, boolean enable) {
      // Default no-op — subclasses implement actual mouse tracking
   }

   /**
    * Sets SGR (mode 1006) mouse encoding.
    *
    * @param enable true for SGR format, false for legacy X10 format
    */
   void setSgrMouseMode(boolean enable) {
      // Default no-op
   }

   /**
    * Sets bracketed paste mode (2004).
    *
    * @param enable true to enable, false to disable
    */
   void setBracketedPasteMode(boolean enable) {
      // Default no-op
   }

   /**
    * Sets focus event reporting mode (1004).
    *
    * @param enable true to enable, false to disable
    */
   void setFocusEventsMode(boolean enable) {
      // Default no-op
   }

   /**
    * Sets autowrap mode (mode 7).
    *
    * @param enable true to enable, false to disable
    */
   void setAutowrapMode(boolean enable) {
      // Default no-op
   }

   /**
    * Sets cursor blink mode (mode 12).
    *
    * @param enable true to enable, false to disable
    */
   void setCursorBlinkMode(boolean enable) {
      // Default no-op
   }
}
