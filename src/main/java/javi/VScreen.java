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
    * <p>Implements ESC[nX (erase n characters). Replaces characters
    * at the cursor position with spaces.</p>
    *
    * @param count number of characters to erase
    * @param sb pending output buffer to flush
    */
   abstract void eraseChars(int count, StringBuilder sb);

   /**
    * Deletes the specified number of characters at cursor.
    *
    * <p>Implements ESC[nP (delete n characters). Removes characters
    * at the cursor position and shifts remaining characters left.</p>
    *
    * @param count number of characters to delete
    * @param sb pending output buffer to flush
    */
   void deleteChars(int count, StringBuilder sb) {
      // Default: fall back to eraseChars
      eraseChars(count, sb);
   }

   /**
    * Inserts blank characters at cursor, shifting text right.
    *
    * <p>Implements CSI n {@code @} (ICH). Characters shifted
    * past the right margin are lost.</p>
    *
    * @param count number of blank characters to insert
    * @param sb pending output buffer to flush
    */
   void insertChars(int count, StringBuilder sb) {
      // Default no-op; overridden in ECScreen
   }

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
    * Erases the scrollback buffer above the visible screen.
    *
    * <p>Implements ESC[3J (xterm extension). Removes all lines
    * above the visible terminal area.</p>
    *
    * @param sb pending output buffer to flush
    */
   void eraseScrollback(StringBuilder sb) {
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
    * Sets the icon (tab) title.
    *
    * <p>Implements OSC 1;title BEL.</p>
    *
    * @param title the new icon title
    */
   void setIconTitle(String title) {
      // Default no-op
   }

   /**
    * Saves the current state of a DEC private mode (XTERM_SAVE).
    *
    * <p>Implements CSI ? Ps s.</p>
    *
    * @param modeNum the DEC private mode number
    */
   void saveMode(int modeNum) {
      // Default no-op
   }

   /**
    * Restores a previously saved DEC private mode (XTERM_RESTORE).
    *
    * <p>Implements CSI ? Ps r.</p>
    *
    * @param modeNum the DEC private mode number
    * @param sb pending output buffer to flush
    */
   void restoreMode(int modeNum, StringBuilder sb) {
      // Default no-op
   }

   /**
    * Responds to DECRQM (DEC Private Mode Report request).
    *
    * <p>Implements CSI ? Ps $ p. Responds with DECRPM:
    * CSI ? Ps; Pm $ y where Pm indicates mode state.</p>
    *
    * @param modeNum the DEC private mode number to report
    * @param sb pending output buffer to flush
    */
   void respondDecrqm(int modeNum, StringBuilder sb) {
      // Default: report mode not recognized (Pm=0)
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
    * Sets reverse-wraparound mode (mode 45).
    *
    * @param enable true to enable, false to disable
    */
   void setReverseWrapMode(boolean enable) {
      // Default no-op
   }

   /**
    * Sets reverse-wraparound extend mode (mode 1045).
    * Wraps around top/bottom of scroll region unconditionally.
    *
    * @param enable true to enable, false to disable
    */
   void setReverseWrapExtendMode(boolean enable) {
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

   /**
    * Sets application cursor key mode (DECCKM, mode 1).
    *
    * @param enable true to enable, false to disable
    */
   void setApplicationCursorKeys(boolean enable) {
      // Default no-op
   }

   /**
    * Sets Line Feed/New Line mode (ANSI mode 20).
    *
    * <p>When enabled, LF acts as CR+LF (moves to column 1 and down).
    * When disabled (default), LF only moves down.</p>
    *
    * @param enable true to enable LNM, false to disable
    */
   void setLnmMode(boolean enable) {
      // Default no-op
   }

   /**
    * Sets cursor visibility (DECTCEM, mode 25).
    *
    * <p>When disabled, the cursor should be hidden. This implements
    * ESC[?25l (hide) and ESC[?25h (show).</p>
    *
    * @param visible true to show cursor, false to hide
    */
   void setCursorVisible(boolean visible) {
      // Default no-op
   }

   /**
    * Responds to a Primary Device Attributes request (CSI c).
    *
    * @param sb pending output buffer to flush
    */
   void respondDeviceAttributes(StringBuilder sb) {
      // Default no-op
   }

   /**
    * Responds to a Secondary Device Attributes request (CSI &gt; c).
    *
    * @param sb pending output buffer to flush
    */
   void respondSecondaryDA(StringBuilder sb) {
      // Default no-op
   }

   /**
    * Responds to a Cursor Position Report request (CSI 6 n).
    *
    * @param sb pending output buffer to flush
    */
   void respondCursorPosition(StringBuilder sb) {
      // Default no-op
   }

   /**
    * Responds to a Device Status Report (DSR 5) with "OK".
    *
    * @param sb pending output buffer to flush
    */
   void respondStatusOk(StringBuilder sb) {
      // Default no-op
   }

   /**
    * Responds to DECRQCRA (Request Checksum of Rectangular Area).
    *
    * <p>Implements CSI Pid ; Pp ; Pt ; Pl ; Pb ; Pr * y.
    * Response is DCS Pid ! ~ D..D ST where D..D is a hex-encoded
    * 16-bit checksum of character values in the rectangle.</p>
    *
    * @param params the CSI numeric parameters
    * @param highParam index of the highest parameter set
    * @param sb pending output buffer to flush
    */
   void respondRectChecksum(int[] params, int highParam,
         StringBuilder sb) {
      // Default no-op
   }

   /**
    * Handles window operations (CSI Ps t — XTWINOPS).
    * Ps=18: report terminal size in characters.
    *
    * @param ps the operation code
    * @param p1 first parameter
    * @param p2 second parameter
    * @param sb pending output buffer to flush
    */
   void handleWindowOp(int ps, int p1, int p2, StringBuilder sb) {
      // Default no-op
   }

   /**
    * Sets the scrolling region (top and bottom margins).
    *
    * <p>Implements DECSTBM (CSI Pt;Pb r). Lines outside the
    * scrolling region are unaffected by scroll operations.</p>
    *
    * @param top 1-based top margin row (0 means reset to default)
    * @param bottom 1-based bottom margin row (0 means reset to default)
    * @param sb pending output buffer to flush
    */
   void setScrollRegion(int top, int bottom, StringBuilder sb) {
      // Default no-op
   }

   /**
    * Index — move cursor down one line, scrolling if at bottom margin.
    *
    * <p>Implements IND (ESC D). If the cursor is at the bottom
    * margin of the scrolling region, the region scrolls up.</p>
    *
    * @param sb pending output buffer to flush
    */
   void index(StringBuilder sb) {
      // Default: just move down
      incY(1, sb);
   }

   /**
    * Reverse Index — move cursor up one line, scrolling if at top margin.
    *
    * <p>Implements RI (ESC M). If the cursor is at the top margin
    * of the scrolling region, the region scrolls down.</p>
    *
    * @param sb pending output buffer to flush
    */
   void reverseIndex(StringBuilder sb) {
      // Default: just move up
      incY(-1, sb);
   }

   /**
    * Next Line — move cursor to beginning of next line, scrolling
    * if at bottom margin.
    *
    * <p>Implements NEL (ESC E). Equivalent to CR + IND.</p>
    *
    * @param sb pending output buffer to flush
    */
   void nextLine(StringBuilder sb) {
      // Default: CR + move down
      setX(1, sb);
      incY(1, sb);
   }

   /**
    * Screen Alignment Display — fills the screen with 'E' characters.
    *
    * <p>Implements DECALN (ESC # 8). Used for screen alignment testing.</p>
    *
    * @param sb pending output buffer to flush
    */
   void screenAlignmentDisplay(StringBuilder sb) {
      // Default no-op
   }

   /**
    * Sets origin mode (DECOM).
    *
    * <p>When enabled, cursor addressing is relative to the scroll
    * region. When disabled, cursor addressing is absolute.</p>
    *
    * @param enable true for relative addressing, false for absolute
    */
   void setOriginMode(boolean enable) {
      // Default no-op
   }

   /**
    * Handles a horizontal tab character (HT, 0x09).
    *
    * <p>Advances the cursor to the next tab stop (every 8 columns).
    * The default implementation appends a literal tab character.</p>
    *
    * @param sb pending output buffer to flush
    */
   void handleTab(StringBuilder sb) {
      sb.append('\t');
   }

   /**
    * Sets a tab stop at the current cursor column (HTS, ESC H).
    *
    * @param sb pending output buffer to flush
    */
   void setTabStop(StringBuilder sb) {
      // Default no-op
   }

   /**
    * Clears tab stops per mode (TBC, CSI g).
    *
    * <p>mode 0 = clear at cursor column; mode 3 = clear all.</p>
    *
    * @param mode the TBC mode parameter
    * @param sb pending output buffer to flush
    */
   void clearTabStop(int mode, StringBuilder sb) {
      // Default no-op
   }

   /**
    * Sets the column mode (DECCOLM, CSI ?3h / ?3l).
    *
    * <p>Per VT100 spec, toggling DECCOLM clears the screen,
    * resets scroll margins, and homes the cursor. Mode 80
    * sets 80-column mode; mode 132 sets 132-column mode.</p>
    *
    * @param columns the column count (80 or 132)
    * @param sb pending output buffer to flush
    */
   void setColumnMode(int columns, StringBuilder sb) {
      // Default no-op
   }

   /**
    * Sets whether 80/132 column switching (DECCOLM, mode 3) is allowed.
    *
    * <p>Mode 40 (xterm): when disabled, DECCOLM changes are ignored.</p>
    *
    * @param enable true to allow 80/132 switching
    */
   void setAllow80To132(boolean enable) {
      // Default no-op
   }

   /**
    * Moves the cursor backward by the specified number of tab
    * stops (CBT, CSI Z).
    *
    * @param count number of tab stops to move backward
    * @param sb pending output buffer to flush
    */
   void backwardTab(int count, StringBuilder sb) {
      // Default no-op
   }

   /**
    * Performs a soft terminal reset (DECSTR, CSI !p).
    *
    * @param sb pending output buffer to flush
    */
   void softReset(StringBuilder sb) {
      // Default no-op
   }

   /**
    * Performs a hard terminal reset (RIS, ESC c).
    *
    * <p>Resets all terminal state and homes the cursor.
    * More thorough than softReset: also clears the screen,
    * resets character sets, and moves cursor to (1,1).</p>
    *
    * @param sb pending output buffer to flush
    */
   void hardReset(StringBuilder sb) {
      // Default no-op
   }

   /**
    * Responds to DEC-private Device Status Reports (CSI ? Ps n).
    *
    * <p>Handles keyboard language (26), printer port (15),
    * and UDK lock (25) queries.</p>
    *
    * @param ps the DEC-private DSR parameter
    * @param sb pending output buffer to flush
    */
   void respondDecdsr(int ps, StringBuilder sb) {
      // Default no-op
   }

   /**
    * Repeats a graphic character (REP, CSI b).
    *
    * @param ch the character to repeat
    * @param count number of times to repeat
    * @param sb pending output buffer to flush
    */
   void repeatChar(char ch, int count, StringBuilder sb) {
      // Default no-op
   }

   /**
    * Handles an OSC color sequence (set, query, or reset).
    *
    * <p>Covers OSC 4 (palette), OSC 10/11/12 (dynamic colors),
    * OSC 17/19 (special colors), and OSC 104/110-119 (resets).</p>
    *
    * @param oscNum the OSC number
    * @param payload the content after the first semicolon
    */
   void handleOscColor(int oscNum, String payload) {
      // Default no-op
   }

   /**
    * Responds to a DCS +q terminfo capability query.
    *
    * @param hexName hex-encoded capability name (e.g. "436F" for "Co")
    */
   void respondTerminfoQuery(String hexName) {
      // Default no-op
   }
}
