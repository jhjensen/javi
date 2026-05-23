package javi;

import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;

import history.Tools;
import static history.Tools.trace;

/**
 * VT100 terminal emulator for shell and serial port connections.
 *
 * <p>Vt100 provides terminal emulation by parsing VT100/ANSI escape sequences
 * and rendering the output in a TextEdit buffer. It supports:</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>VT100/ANSI escape sequence parsing</li>
 *   <li>Cursor movement and positioning</li>
 *   <li>Screen clearing and line editing</li>
 *   <li>Insert mode support</li>
 *   <li>Auto-detected or explicit charset encoding</li>
 *   <li>Multiple session support via {@link ShellManager}</li>
 * </ul>
 *
 * <h2>Supported Escape Sequences</h2>
 * <ul>
 *   <li>Cursor movement: up, down, left, right, absolute positioning</li>
 *   <li>Screen operations: clear screen, clear line, erase characters</li>
 *   <li>Mode control: insert mode, cursor save/restore</li>
 *   <li>OSC sequences: window title changes</li>
 * </ul>
 *
 * <h2>Connection Types</h2>
 * <ul>
 *   <li>{@link Telnet} - Local shell or SSH connections</li>
 *   <li>Serial port connections (via RXTX plugin)</li>
 * </ul>
 *
 * @see Vt100Parser
 * @see VScreen
 * @see ShellSession
 */
public class Vt100 extends TextEdit<String> {


   private static final long serialVersionUID = 1;

   /** Writer for sending data to the terminal process. */
   private final OutputStreamWriter writer;

   /** The charset used for encoding terminal I/O. */
   private final Charset charset;

   /** Lock for synchronizing all writes to the PTY writer. */
   private final Object writerLock = new Object();

   /** Current file-view context for display updates. */
   private FvContext currfvc = null;

   /** Current cursor position in the virtual terminal. */
   private MovePos vtcursor = new MovePos(0, 1);

   /** Whether insert mode is active (vs overwrite). */
   private boolean insertmode = false;

   /** Number of visible rows in the terminal. */
   private int rows;

   /** Parser for VT100 escape sequences. */
   private final Vt100Parser parser;

   /** Terminal I/O logger for debugging. */
   final TermLog termLog = new TermLog();

   /** The ECScreen instance (kept for attribute recording). */
   private final ECScreen ecscreen;

   /** Mouse tracking mode: 0=off, 1000=normal, 1002=button, 1003=any. */
   private volatile int mouseTrackingMode;

   /** Whether SGR (1006) mouse encoding is active vs legacy X10. */
   private volatile boolean sgrMouseMode;

   /** Whether bracketed paste mode (2004) is active. */
   private volatile boolean bracketedPasteMode;

   /** Whether focus event reporting (1004) is active. */
   private volatile boolean focusEventsMode;

   /** Whether autowrap mode (mode 7) is active. */
   private volatile boolean autowrapMode = true;

   /** Whether cursor blink mode (mode 12) is active. */
   private volatile boolean cursorBlinkMode;

   /** Whether application cursor key mode (DECCKM, mode 1) is active. */
   private volatile boolean applicationCursorKeys;

   /** Whether cursor is visible (DECTCEM, mode 25). Default is visible. */
   private volatile boolean cursorVisible = true;

   /** Whether reverse-wraparound inline mode (DECSET 45) is active.
    * In xterm &ge; 383, mode 45 only wraps across autowrapped lines. */
   private volatile boolean reverseWrapMode;

   /** Whether reverse-wraparound extend mode (DECSET 1045) is active.
    * Wraps around top/bottom of scroll region unconditionally. */
   private volatile boolean reverseWrapExtendMode;

   /** Last window title set by the terminal (OSC 0 or OSC 2). */
   private volatile String oscTitle;

   /** Last icon title set by the terminal (OSC 0 or OSC 1). */
   private volatile String oscIconTitle;

   /**
    * Creates a VT100 terminal with auto-detected charset.
    *
    * <p>Charset is detected from environment variables (LANG, LC_ALL, etc.)
    * via {@link CharsetDetector}.</p>
    *
    * @param ostri output stream to the terminal process
    * @param istr input stream from the terminal process
    * @param ioc I/O converter for the terminal buffer
    * @throws java.io.UnsupportedEncodingException if charset is unsupported
    * @deprecated Use {@link #Vt100(OutputStream,
    *    BufferedInputStream, IoConverter, Charset)}
    */
   @Deprecated
   protected Vt100(OutputStream ostri, BufferedInputStream istr,
         IoConverter<String> ioc)
         throws java.io.UnsupportedEncodingException {
      this(ostri, istr, ioc, CharsetDetector.detectTerminalCharset());
   }

   /**
    * Creates a VT100 terminal with explicit charset.
    *
    * @param ostri output stream to the terminal process
    * @param istr input stream from the terminal process
    * @param ioc I/O converter for the terminal buffer
    * @param charsetToUse the charset for encoding I/O
    */
   protected Vt100(OutputStream ostri, BufferedInputStream istr,
         IoConverter<String> ioc, Charset charsetToUse) {
      super(ioc, ioc.prop);
      undoDisabled = true;
      this.charset = charsetToUse;
      this.ecscreen = new ECScreen();
      parser = new Vt100Parser(ecscreen, istr, charset);
      parser.termLog = termLog;
      writer = new OutputStreamWriter(ostri, charset);
      trace("Vt100: initialized with charset " + charset.name());
   }

   /**
    * Gets the charset used for this terminal.
    *
    * @return the terminal charset
    */
   public Charset getCharset() {
      return charset;
   }

   /**
    * Returns the last OSC title set by the terminal process,
    * or null if no title has been set.
    */
   String getOscTitle() {
      return oscTitle;
   }

   /**
    * Returns the per-character attribute grid for this terminal.
    *
    * <p>Used by the rendering layer to query attributes for each
    * cell on the visible screen.</p>
    *
    * @return the screen attribute grid (never null)
    */
   public ScreenAttributes getScreenAttributes() {
      return ecscreen.screenAttrs;
   }

   @Override
   public final ScreenAttributes getTerminalAttributes() {
      return ecscreen.screenAttrs;
   }

   public final void startHandle(FvContext fvc) {
      //trace("startHandle " + fvc);
      if (null != fvc) {
//         oldfont = FontList.setFontName("Courier New" , fvc.vi);
//        oldfont = FontList.setFontName("Vrinda" ,fvc.vi);
//        oldsize =  FontList.setFontSize(new Float(15.0) ,fvc.vi);
         //trace("oldfont = " + oldfont);
         rows = fvc.vi.getRows(1.0f);
         int neededRows = rows - readIn();
         //trace("rows " + rows + " readIn " + ev.readIn() + " neededRows " + neededRows);
         while (--neededRows > -1)  {
            //trace("setfvc inserting line at "  + ev.readIn());
            insertOne("", readIn());
         }
      }

      currfvc = fvc;

      // Apply saved cursor visibility state to the new view
      if (null != fvc && !cursorVisible)
         fvc.vi.setCursorOff();

      //trace("leave setfvc readIn = " + ev.readIn());
   }

   /**
    * Sends stty command to update the PTY dimensions.
    *
    * @param termRows number of rows
    * @param termCols number of columns
    */
   void sendStty(int termRows, int termCols) {
      try {
         synchronized (writerLock) {
            writer.write("stty rows " + termRows
               + " cols " + termCols + "\n");
            writer.flush();
         }
      } catch (IOException e) {
         trace("sendStty failed: " + e);
      }
   }

   /**
    * Notifies this terminal that the display has been resized.
    *
    * <p>Posts a resize event on the {@link EventQueue} so that all
    * internal state mutations (rows, cursor, scroll region, tab stops,
    * screen attributes) happen on the event-processing thread under
    * {@code biglock2}. This eliminates the AWT-thread race where the
    * parser thread could be modifying the same fields concurrently.</p>
    *
    * <p>The optional {@code afterResize} callback runs on the event
    * thread immediately after the internal state is updated. This
    * ensures that PTY dimension updates (stty + SIGWINCH) happen
    * only after the terminal state matches the new size, preventing
    * the race where a child app receives SIGWINCH and redraws using
    * escape sequences that are processed against stale state.</p>
    *
    * @param newRows new number of rows
    * @param newCols new number of columns
    * @param afterResize optional callback to run after internal
    *    state is updated (may be null)
    */
   void notifyResize(int newRows, int newCols,
         Runnable afterResize) {
      if (newRows <= 0 || newCols <= 0)
         return;
      EventQueue.insert(new EventQueue.IEvent() {
         public void execute() {
            applyResize(newRows, newCols);
            if (afterResize != null)
               afterResize.run();
         }
      });
   }

   /**
    * Notifies this terminal that the display has been resized.
    *
    * @param newRows new number of rows
    * @param newCols new number of columns
    */
   void notifyResize(int newRows, int newCols) {
      notifyResize(newRows, newCols, null);
   }

   /**
    * Applies the resize state changes on the event-processing thread.
    * Must be called under {@code biglock2}. Package-private to allow
    * direct invocation from unit tests that bypass the event queue.
    */
   void applyResize(int newRows, int newCols) {
      int oldRows = rows;
      rows = newRows;
      int neededRows = rows - readIn();
      while (--neededRows > -1)
         insertOne("", readIn());
      // Clamp cursor to valid range after resize
      if (vtcursor.y < 0)
         vtcursor.y = 0;
      if (vtcursor.y >= readIn())
         vtcursor.y = readIn() - 1;
      if (vtcursor.x >= newCols)
         vtcursor.x = newCols - 1;
      ecscreen.pendingWrap = false;
      ecscreen.handleResize(oldRows, newRows, newCols);
      if (null != currfvc) {
         currfvc.cursorabs(vtcursor.x, vtcursor.y);
         currfvc.placeline(readIn() - 1, .99999f);
      }
      trace("Vt100: resized to " + newRows + "x" + newCols);
   }

   /**
    * Checks if mouse tracking is currently enabled.
    *
    * @return true if any mouse tracking mode is active
    */
   boolean isMouseTrackingEnabled() {
      return mouseTrackingMode != 0;
   }

   /**
    * Sets the mouse tracking mode.
    *
    * @param mode 0=off, 1000=normal, 1002=button-event, 1003=any-event
    * @param enable true to enable, false to disable
    */
   void setMouseTracking(int mode, boolean enable) {
      if (enable)
         mouseTrackingMode = mode;
      else if (mouseTrackingMode == mode)
         mouseTrackingMode = 0;
      trace("Vt100: mouse tracking mode=" + mouseTrackingMode);
   }

   /**
    * Sets SGR (mode 1006) mouse encoding on or off.
    */
   void setSgrMouseMode(boolean enable) {
      sgrMouseMode = enable;
      trace("Vt100: SGR mouse mode=" + sgrMouseMode);
   }

   /**
    * Sets bracketed paste mode (2004) on or off.
    */
   void setBracketedPasteMode(boolean enable) {
      bracketedPasteMode = enable;
      trace("Vt100: bracketed paste mode=" + bracketedPasteMode);
   }

   /**
    * Sets focus event reporting mode (1004) on or off.
    */
   void setFocusEventsMode(boolean enable) {
      focusEventsMode = enable;
      trace("Vt100: focus events mode=" + focusEventsMode);
   }

   /**
    * Sets autowrap mode (mode 7) on or off.
    */
   void setAutowrapMode(boolean enable) {
      autowrapMode = enable;
      trace("Vt100: autowrap mode=" + autowrapMode);
   }

   /**
    * Sets reverse-wraparound inline mode (mode 45) on or off.
    * When enabled along with DECAWM, BS at column 0 wraps to the
    * end of the previous line only if the current line was autowrapped.
    */
   void setReverseWrapMode(boolean enable) {
      reverseWrapMode = enable;
      trace("Vt100: reverse wrap mode=" + reverseWrapMode);
   }

   /**
    * Sets reverse-wraparound extend mode (mode 1045) on or off.
    * When enabled along with DECAWM, BS at column 0 wraps around
    * the top/bottom of the scroll region unconditionally.
    */
   void setReverseWrapExtendMode(boolean enable) {
      reverseWrapExtendMode = enable;
      trace("Vt100: reverse wrap extend mode=" + reverseWrapExtendMode);
   }

   /**
    * Sets cursor blink mode (mode 12) on or off.
    */
   void setCursorBlinkMode(boolean enable) {
      cursorBlinkMode = enable;
      trace("Vt100: cursor blink mode=" + cursorBlinkMode);
   }

   /**
    * Sets application cursor key mode (DECCKM, mode 1).
    *
    * <p>When enabled, arrow keys send ESC O A/B/C/D instead of
    * ESC [ A/B/C/D.</p>
    */
   void setApplicationCursorKeys(boolean enable) {
      applicationCursorKeys = enable;
      trace("Vt100: application cursor keys=" + applicationCursorKeys);
   }

   /**
    * Sets cursor visibility (DECTCEM, mode 25).
    *
    * <p>When disabled, the cursor is hidden. When enabled, the cursor
    * is shown. Propagates to the View if a display context is active.</p>
    *
    * @param visible true to show cursor, false to hide
    */
   void setCursorVisible(boolean visible) {
      cursorVisible = visible;
      trace("Vt100: cursor visible=" + cursorVisible);
      if (null != currfvc) {
         if (visible)
            currfvc.vi.setCursorOn();
         else
            currfvc.vi.setCursorOff();
      }
   }

   /**
    * Checks if the cursor is visible (DECTCEM, mode 25).
    *
    * @return true if cursor should be displayed
    */
   boolean isCursorVisible() {
      return cursorVisible;
   }

   /**
    * Sends a response string back to the PTY.
    *
    * <p>Used for terminal queries like Device Attributes (DA) and
    * Cursor Position Report (CPR).</p>
    *
    * @param response the escape sequence to send
    */
   void sendResponse(String response) {
      try {
         if (termLog.isEnabled())
            termLog.logSend(response);
         synchronized (writerLock) {
            writer.write(response);
            writer.flush();
         }
      } catch (IOException e) {
         trace("sendResponse failed: " + e);
      }
   }

   /**
    * Checks if focus event reporting is enabled.
    *
    * @return true if mode 1004 is active
    */
   boolean isFocusEventsEnabled() {
      return focusEventsMode;
   }

   /**
    * Sends a focus event to the shell if focus reporting is enabled.
    *
    * @param focusIn true for focus gained, false for focus lost
    */
   void sendFocusEvent(boolean focusIn) {
      if (!focusEventsMode)
         return;
      try {
         synchronized (writerLock) {
            writer.write(focusIn ? "\033[I" : "\033[O");
            writer.flush();
         }
      } catch (IOException e) {
         trace("sendFocusEvent failed: " + e);
      }
   }

   /**
    * Sends text to the shell process, wrapping with bracketed paste
    * markers if bracketed paste mode is enabled.
    *
    * @param text the text to send
    */
   void sendText(String text) {
      try {
         synchronized (writerLock) {
            if (bracketedPasteMode)
               writer.write("\033[200~");
            writer.write(text);
            if (bracketedPasteMode)
               writer.write("\033[201~");
            writer.flush();
         }
      } catch (IOException e) {
         trace("sendText failed: " + e);
      }
   }

   /**
    * Sends a mouse event to the shell process as an escape sequence.
    *
    * <p>Encodes the event using SGR (mode 1006) if active, otherwise
    * legacy X10 encoding. SGR format: ESC[&lt;button;col;rowM (press)
    * or ESC[&lt;button;col;rowm (release). Legacy format: ESC[Mcbxy
    * where cb=button+32, x=col+32, y=row+32.</p>
    *
    * @param button 0=left, 1=middle, 2=right, 64=scrollUp, 65=scrollDown
    * @param col 1-based column
    * @param row 1-based row
    * @param pressed true for press, false for release
    */
   void sendMouseEvent(int button, int col, int row, boolean pressed) {
      try {
         synchronized (writerLock) {
            if (sgrMouseMode) {
               // SGR extended mode: ESC[<button;col;rowM/m
               writer.write("\033[<" + button + ";" + col + ";"
                  + row + (pressed ? "M" : "m"));
            } else {
               // Legacy X10: ESC[M cb cx cy (add 32 to each)
               if (col > 222 || row > 222)
                  return; // legacy encoding limited to 223
               writer.write("\033[M");
               writer.write((char) (button + 32));
               writer.write((char) (col + 32));
               writer.write((char) (row + 32));
            }
            writer.flush();
         }
      } catch (IOException e) {
         trace("sendMouseEvent failed: " + e);
      }
   }

   public final String fromString(String str) {
      return str;
   }

   public final void handleKeys(FvContext fvc) throws InputException {
      trace("handleKeys");
      startHandle(fvc);
      try {
         while (true) {
            JeyEvent kev = EventQueue.nextEvent(fvc.vi);
            char ch = kev.getKeyChar();
            if (ch == JeyEvent.CHAR_UNDEFINED) {
               synchronized (writerLock) {
                  switch (kev.getKeyCode()) {
                     case JeyEvent.VK_LEFT:
                        writer.write(applicationCursorKeys
                           ? "\33OD" : "\33[D");
                        break;
                     case JeyEvent.VK_RIGHT:
                        writer.write(applicationCursorKeys
                           ? "\33OC" : "\33[C");
                        break;
                     case JeyEvent.VK_UP:
                        writer.write(applicationCursorKeys
                           ? "\33OA" : "\33[A");
                        break;
                     case JeyEvent.VK_DOWN:
                        writer.write(applicationCursorKeys
                           ? "\33OB" : "\33[B");
                        break;
                     case JeyEvent.VK_HOME:
                        writer.write(applicationCursorKeys
                           ? "\33OH" : "\33[H");
                        break;
                     case JeyEvent.VK_END:
                        writer.write(applicationCursorKeys
                           ? "\33OF" : "\33[F");
                        break;
                     case JeyEvent.VK_PAGE_UP:
                        writer.write("\33[5~");
                        break;
                     case JeyEvent.VK_PAGE_DOWN:
                        writer.write("\33[6~");
                        break;
                     case JeyEvent.VK_INSERT:
                        writer.write("\33[2~");
                        break;
                     case JeyEvent.VK_DELETE:
                        writer.write("\33[3~");
                        break;
                     case JeyEvent.VK_F1:
                        writer.write("\33OP");
                        break;
                     case JeyEvent.VK_F2:
                        writer.write("\33OQ");
                        break;
                     case JeyEvent.VK_F3:
                        writer.write("\33OR");
                        break;
                     case JeyEvent.VK_F4:
                        writer.write("\33OS");
                        break;
                     case JeyEvent.VK_F5:
                        writer.write("\33[15~");
                        break;
                     case JeyEvent.VK_F6:
                        writer.write("\33[17~");
                        break;
                     case JeyEvent.VK_F7:
                        writer.write("\33[18~");
                        break;
                     case JeyEvent.VK_F8:
                        return;
                     case JeyEvent.VK_F9:
                        writer.write("\33[20~");
                        break;
                     case JeyEvent.VK_F10:
                        writer.write("\33[21~");
                        break;
                     case JeyEvent.VK_F11:
                        writer.write("\33[23~");
                        break;
                     case JeyEvent.VK_F12:
                        writer.write("\33[24~");
                        break;
                     default:
                        trace("unhandle KeyCode "
                           + kev.getKeyCode());
                  }
                  writer.flush();
                  if (termLog.isEnabled())
                     termLog.log("KEY special code="
                        + kev.getKeyCode());
               }
            } else {
               // Cmd+C (macOS) clipboard copy of selection
               if (('c' == ch || 'C' == ch)
                     && (kev.getModifiers()
                        & JeyEvent.META_MASK) != 0) {
                  copySelection(fvc);
                  continue;
               }
               // Cmd+V (macOS) / Ctrl+V clipboard paste
               if (('v' == ch || 'V' == ch)
                     && (kev.getModifiers()
                        & JeyEvent.META_MASK) != 0) {
                  pasteClipboard();
                  continue;
               }
               // Terminals send CR for Enter; the PTY line
               // discipline converts CR→NL in cooked mode.
               // In raw mode (readline ^R), apps expect CR.
               if ('\n' == ch || '\r' == ch)
                  ch = '\r';
               synchronized (writerLock) {
                  if (termLog.isEnabled())
                     termLog.logSend(ch);
                  writer.write(ch);
                  writer.flush();
               }
            }
         }
      } catch (IOException e) {
         trace("caught IOException " + e);
         return;
      }
   }

   /**
    * Copies the current visual selection (mark) to the system
    * clipboard. Called when Cmd+C is pressed in a shell session.
    *
    * @param fvc the current file-view context
    */
   private void copySelection(FvContext fvc) {
      EventQueue.biglock2.lock();
      try {
         MovePos mark = fvc.vi.getMark();
         if (null == mark)
            return;
         int cx = fvc.vi.getfileX();
         int cy = fvc.vi.getfileY();
         Position start = new Position(mark.x, mark.y, "", "");
         Position end = new Position(cx, cy, "", "");
         String text = ShellManager.extractBufferText(
            this, start, end);
         if (text.isEmpty())
            return;
         java.awt.datatransfer.Clipboard clip =
            java.awt.Toolkit.getDefaultToolkit()
               .getSystemClipboard();
         clip.setContents(
            new java.awt.datatransfer.StringSelection(text),
            null);
         trace("Cmd+C copied: " + text.length() + " chars");
      } catch (Exception e) {
         trace("copySelection failed: " + e);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Reads clipboard text and sends it to the shell, wrapping with
    * bracketed paste markers if mode 2004 is active.
    */
   private void pasteClipboard() {
      try {
         java.awt.datatransfer.Clipboard clip =
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
         java.awt.datatransfer.Transferable tr = clip.getContents(null);
         if (tr != null && tr.isDataFlavorSupported(
               java.awt.datatransfer.DataFlavor.stringFlavor)) {
            String text = (String) tr.getTransferData(
               java.awt.datatransfer.DataFlavor.stringFlavor);
            if (text != null && !text.isEmpty())
               sendText(text);
         }
      } catch (Exception e) {
         trace("pasteClipboard failed: " + e);
      }
   }

   private final class ECScreen extends VScreen {
      private MovePos savecursor = new MovePos(0, 1);

      /** Whether DECSC has been called (for DECRC default behavior). */
      private boolean cursorSaved;

      /** Line Feed/New Line mode (ANSI mode 20). */
      private boolean lnmMode;

      /** DECNCSM mode 40: allow 80/132 column switching (DECCOLM). */
      private boolean allow80To132;

      /**
       * Padding character for the second column of a wide character.
       * @see Wcwidth#WIDE_PAD
       */
      static final char WIDE_PAD = Wcwidth.WIDE_PAD;

      /**
       * Deferred autowrap flag. Set when a character is written at the
       * last column. The next graphic character triggers wrap to col 0
       * of the next line (scrolling at bottom margin). Cleared by any
       * explicit cursor movement.
       */
      private boolean pendingWrap;

      /** SGR attribute state machine. */
      final SgrState sgrState = new SgrState();

      /** Packed attribute computed from the current SGR state. */
      private int currentAttr = CellAttr.DEFAULT;

      /** Per-character attribute grid for the terminal screen. */
      final ScreenAttributes screenAttrs = new ScreenAttributes();

      /**
       * Flag indicating that screen content was modified by a bulk
       * operation (scroll, alt screen switch, erase, insert/delete
       * lines) that changes lines other than just the cursor line.
       * When set, {@link #updateScreen} forces a full redraw instead
       * of the incremental CHANGE that only repaints the cursor line.
       */
      private boolean screenDirty;

      /** Saved attribute grid for the alternate screen buffer. */
      private ScreenAttributes savedScreenAttrs;

      /** Top margin of scroll region (1-based terminal row). */
      private int scrollTop = 1;

      /** Bottom margin of scroll region (1-based terminal row, 0=use rows). */
      private int scrollBottom;

      /** Whether origin mode (DECOM) is active. */
      private boolean originMode;

      /** Tab stop positions (0-based column indices). */
      private boolean[] tabStops;

      /** Column count for DECCOLM mode (80 or 132). */
      private int columns;

      /**
       * Per-line autowrap flag. Tracks which absolute line numbers
       * overflowed (source lines) during autowrap. When a line's
       * content exceeds the column width and wraps to the next line,
       * the SOURCE line number is recorded here. Used by
       * reverse-wraparound (mode 45) to decide whether BS at col 0
       * should wrap to the previous line's last column: the check
       * is autowrappedLines.contains(y - 1).
       */
      private final java.util.HashSet<Integer> autowrappedLines =
         new java.util.HashSet<>();

      /**
       * Mutable 256-color palette plus 6 special color slots.
       * Indices 0-255: standard palette colors.
       * Indices 256-261: special colors (bold, underline, blink,
       *   reverse, italic, strikethrough).
       * Modified by OSC 4 (set) / OSC 104 (reset).
       * Queried by OSC 4;N;? and OSC 5;N;?.
       */
      private final java.awt.Color[] palette = new java.awt.Color[262];
      {
         for (int i = 0; i < 256; i++)
            palette[i] = TerminalPalette.defaultColor(i);
         // Special colors default to foreground color
         for (int i = 256; i < 262; i++)
            palette[i] = TerminalPalette.DEFAULT_FG;
      }

      /** Current dynamic foreground color (OSC 10). */
      private java.awt.Color dynamicFg = TerminalPalette.DEFAULT_FG;

      /** Current dynamic background color (OSC 11). */
      private java.awt.Color dynamicBg = TerminalPalette.DEFAULT_BG;

      /** Current cursor color (OSC 12). */
      private java.awt.Color dynamicCursor =
         TerminalPalette.DEFAULT_CURSOR;

      /** Current highlight foreground (OSC 17), null = default. */
      private java.awt.Color highlightFg =
         TerminalPalette.DEFAULT_HIGHLIGHT_FG;

      /** Current highlight background (OSC 19), null = default. */
      private java.awt.Color highlightBg =
         TerminalPalette.DEFAULT_HIGHLIGHT_BG;

      /** Stack for CSI 22/23 t title push/pop.
       *  Each entry is a String[2] = {icon, window}. */
      private final java.util.ArrayDeque<String[]> titleStack =
         new java.util.ArrayDeque<>();

      /** Saved DEC private mode state for XTERM_SAVE/RESTORE. */
      private final java.util.HashMap<Integer, Boolean> savedModes =
         new java.util.HashMap<>();

      /** Returns the effective column count (DECCOLM or window). */
      int getColumns() {
         return columns > 0 ? columns : MiscCommands.getWidth();
      }

      /** Initializes default tab stops every 8 columns. */
      private void initTabStops() {
         int cols = getColumns();
         tabStops = new boolean[cols > 0 ? cols : 80];
         for (int c = 0; c < tabStops.length; c += 8)
            tabStops[c] = true;
      }

      void setGraphicRendition(int[] params, StringBuilder sb) {
         insertString(sb);
         sgrState.process(params);
         currentAttr = sgrState.currentAttr();
      }

      void handleTab(StringBuilder sb) {
         insertString(sb);
         if (tabStops == null)
            initTabStops();
         int col = vtcursor.x;
         int limit = tabStops.length;
         // Find the next tab stop after the current column
         int nextStop = -1;
         for (int c = col + 1; c < limit; c++) {
            if (tabStops[c]) {
               nextStop = c;
               break;
            }
         }
         // If no stop found, advance to last column
         if (nextStop < 0)
            nextStop = limit - 1;
         // HT moves cursor to tab stop without modifying content.
         // Per VT100 spec, HT does not cause line wrap.
         vtcursor.x = nextStop;
         pendingWrap = false;
      }

      /** Sets a tab stop at the current cursor column (HTS). */
      void setTabStop(StringBuilder sb) {
         insertString(sb);
         if (tabStops == null)
            initTabStops();
         int col = vtcursor.x;
         if (col >= 0 && col < tabStops.length)
            tabStops[col] = true;
      }

      /** Clears tab stops per TBC mode. */
      void clearTabStop(int mode, StringBuilder sb) {
         insertString(sb);
         if (tabStops == null)
            initTabStops();
         switch (mode) {
            case 0: // Clear tab stop at current column
               if (vtcursor.x >= 0
                     && vtcursor.x < tabStops.length)
                  tabStops[vtcursor.x] = false;
               break;
            case 3: // Clear all tab stops
               for (int c = 0; c < tabStops.length; c++)
                  tabStops[c] = false;
               break;
            default:
               trace("TBC mode " + mode
                  + " — parsed, not used");
               break;
         }
      }

      @Override
      void backwardTab(int count, StringBuilder sb) {
         insertString(sb);
         if (tabStops == null)
            initTabStops();
         for (int n = 0; n < count; n++) {
            int col = vtcursor.x - 1;
            while (col > 0 && !tabStops[col])
               col--;
            vtcursor.x = Math.max(0, col);
         }
      }

      @Override
      void softReset(StringBuilder sb) {
         insertString(sb);
         sgrState.reset();
         currentAttr = CellAttr.DEFAULT;
         insertmode = false;
         originMode = false;
         autowrapMode = true;
         reverseWrapMode = false;
         reverseWrapExtendMode = false;
         lnmMode = false;
         allow80To132 = false;
         columns = 0;
         scrollTop = 1;
         scrollBottom = 0;
         cursorSaved = false;
         savecursor = new MovePos(0, 1);
         autowrappedLines.clear();
         initTabStops();
         screenDirty = true;
         // DECSTR does NOT move the cursor (per DEC STD 070)
      }

      /**
       * Hard reset (RIS, ESC c). Resets all terminal state,
       * clears the screen, and homes the cursor to (1,1).
       */
      @Override
      void hardReset(StringBuilder sb) {
         softReset(sb);
         pendingWrap = false;
         cursorSaved = false;
         savecursor = new MovePos(0, 1);
         autowrappedLines.clear();
         // Reset palette to defaults
         for (int i = 0; i < 256; i++)
            palette[i] = TerminalPalette.defaultColor(i);
         for (int i = 256; i < palette.length; i++)
            palette[i] = TerminalPalette.DEFAULT_FG;
         dynamicFg = TerminalPalette.DEFAULT_FG;
         dynamicBg = TerminalPalette.DEFAULT_BG;
         dynamicCursor = TerminalPalette.DEFAULT_CURSOR;
         highlightFg = TerminalPalette.DEFAULT_HIGHLIGHT_FG;
         highlightBg = TerminalPalette.DEFAULT_HIGHLIGHT_BG;
         // Clear screen
         int end = readIn();
         int start = end - rows;
         if (start < 0)
            start = 0;
         for (int ii = start; ii < end; ii++)
            changeElementAt("", ii);
         screenAttrs.eraseScreen(start, end);
         // Home cursor
         vtcursor.y = termRowToAbs(1);
         vtcursor.x = 0;
         screenDirty = true;
      }

      /**
       * DEC-private Device Status Report (CSI ? Ps n).
       * Responds to keyboard, printer, and UDK queries.
       */
      @Override
      void respondDecdsr(int ps, StringBuilder sb) {
         insertString(sb);
         switch (ps) {
            case 6: // Extended CPR (DECOM-aware)
               int row = vtcursor.y - readIn() + rows + 1;
               int col = vtcursor.x + 1;
               if (row < 1) row = 1;
               if (col < 1) col = 1;
               Vt100.this.sendResponse(
                  "\033[?" + row + ";" + col + "R");
               break;
            case 15: // Printer port — no printer
               Vt100.this.sendResponse("\033[?13n");
               break;
            case 25: // UDK — locked
               Vt100.this.sendResponse("\033[?21n");
               break;
            case 26: // Keyboard — North American, ready
               // VT220 level: 2 params only (27;language)
               Vt100.this.sendResponse("\033[?27;1n");
               break;
            default:
               trace("DECDSR " + ps + " — not implemented");
         }
      }

      @Override
      void repeatChar(char ch, int count, StringBuilder sb) {
         insertString(sb);
         if (ch == 0)
            return;
         for (int i = 0; i < count; i++)
            sb.append(ch);
         insertString(sb);
      }

      /**
       * Executes a deferred autowrap: moves cursor to column 0
       * of the next line, scrolling if at the bottom margin.
       * Records the SOURCE line (the line that overflowed) in
       * autowrappedLines so reverse-wrap (mode 45) can determine
       * whether the previous line overflowed into the current one.
       */
      void doAutowrap() {
         pendingWrap = false;
         int sourceLine = vtcursor.y;
         vtcursor.x = 0;
         int bottomAbs = termRowToAbs(effectiveBottom());
         if (vtcursor.y >= bottomAbs) {
            scrollRegionUp(1);
            // After scroll-up the source content shifted from
            // bottomAbs to bottomAbs-1; record that position.
            autowrappedLines.add(vtcursor.y - 1);
         } else {
            vtcursor.y++;
            if (vtcursor.y >= readIn())
               insertOne("", readIn());
            autowrappedLines.add(sourceLine);
         }
      }

      void incX(int amount, StringBuilder sb) {
         insertString(sb);
         boolean anyReverseWrap =
            (reverseWrapMode || reverseWrapExtendMode)
            && autowrapMode;
         // BS / CUB at the pending-wrap position consumes the
         // pending wrap without moving the cursor (xterm behavior;
         // esctest2 test_DECSET_ReverseWraparoundLastCol_BS).
         if (amount < 0 && pendingWrap && anyReverseWrap) {
            pendingWrap = false;
            return;
         }
         pendingWrap = false;
         vtcursor.x += amount;
         if (vtcursor.x < 0 && anyReverseWrap) {
            int cols = getColumns();
            int topAbs = termRowToAbs(scrollTop);
            int botAbs = termRowToAbs(effectiveBottom());
            if (reverseWrapExtendMode && !reverseWrapMode) {
               // Mode 1045: unconditional wrap around scroll region
               while (vtcursor.x < 0) {
                  if (vtcursor.y > topAbs)
                     vtcursor.y--;
                  else
                     vtcursor.y = botAbs;
                  vtcursor.x += cols;
               }
            } else {
               // Mode 45 (inline): only wrap across autowrapped lines.
               // Check the PREVIOUS line (y-1) — a source line being
               // in autowrappedLines means it overflowed into the
               // current line, so reverse-wrap is permitted.
               while (vtcursor.x < 0) {
                  if (!autowrappedLines.contains(vtcursor.y - 1)) {
                     vtcursor.x = 0;
                     break;
                  }
                  if (vtcursor.y > topAbs)
                     vtcursor.y--;
                  else {
                     vtcursor.x = 0;
                     break;
                  }
                  vtcursor.x += cols;
               }
            }
         } else if (vtcursor.x < 0) {
            vtcursor.x = 0;
         }
         int maxCol = getColumns() - 1;
         if (vtcursor.x > maxCol)
            vtcursor.x = maxCol;
      }

      void incY(int amount, StringBuilder sb)  {
         updateScreen(sb);
         pendingWrap = false;
         int topAbs = termRowToAbs(scrollTop);
         int botAbs = termRowToAbs(effectiveBottom());
         vtcursor.y += amount;
         // Clamp at scroll region boundaries when cursor
         // started inside the region (CUU/CUD per VT100 spec)
         if (vtcursor.y < topAbs
               && vtcursor.y - amount >= topAbs)
            vtcursor.y = topAbs;
         if (vtcursor.y > botAbs
               && vtcursor.y - amount <= botAbs)
            vtcursor.y = botAbs;
         // General buffer bounds clamping
         if (vtcursor.y < readIn() - rows)
            vtcursor.y = readIn() - rows;
         if (vtcursor.y < 0)
            vtcursor.y = 0;
         if (vtcursor.y >= readIn())
            vtcursor.y = readIn() - 1;
      }

      void setX(int val, StringBuilder sb) {
         pendingWrap = false;
         // Clamp to screen width when terminal size is known
         int maxCol = getColumns();
         if (maxCol > 0 && val > maxCol)
            val = maxCol;
         setXmy(val, sb);
      }

      void eraseScreen(StringBuilder sb) {
         updateScreen(sb);
         if (!inAlternateScreen) {
            // Normal screen: scroll existing content into scrollback
            // by appending blank lines, preserving history.
            for (int ii = 0; ii < rows; ii++)
               insertOne("", readIn());
            vtcursor.y += rows;
            int newEnd = readIn();
            int newStart = newEnd - rows;
            int bgAttr = CellAttr.bgOnly(currentAttr);
            if (bgAttr != CellAttr.DEFAULT) {
               int fillCols = getColumns();
               for (int ii = newStart; ii < newEnd; ii++)
                  screenAttrs.fillAttr(ii, 0, fillCols, bgAttr);
            } else {
               screenAttrs.eraseScreen(newStart, newEnd);
            }
         } else {
            // Alt screen: clear in place (no scrollback)
            int end = readIn();
            int start = end - rows;
            if (start < 0)
               start = 0;
            for (int ii = start; ii < end; ii++) {
               changeElementAt("", ii);
               autowrappedLines.remove(ii);
            }
            int bgAttr = CellAttr.bgOnly(currentAttr);
            if (bgAttr != CellAttr.DEFAULT) {
               int fillCols = getColumns();
               for (int ii = start; ii < end; ii++)
                  screenAttrs.fillAttr(ii, 0, fillCols, bgAttr);
            } else {
               screenAttrs.eraseScreen(start, end);
            }
         }
         screenDirty = true;
      }

      @Override
      void eraseScrollback(StringBuilder sb) {
         // ED3 (ESC[3J) intentionally a no-op.  The 'clear' command
         // on macOS sends ESC[H ESC[2J ESC[3J — ED2 pushes viewport
         // content into scrollback so the user can scroll up to see
         // history.  Honoring ED3 would immediately erase that
         // preserved content, defeating the purpose of scrollback.
      }

      void eraseToEnd(StringBuilder sb) {
         updateScreen(sb);
         if (vtcursor.y < readIn()) {
            String line = at(vtcursor.y);
            int lend = line.length();
            // Fix wide char split: cursor at WIDE_PAD
            if (vtcursor.x > 0 && vtcursor.x < lend
                  && line.charAt(vtcursor.x) == WIDE_PAD) {
               StringBuilder fix = new StringBuilder(line);
               fix.setCharAt(vtcursor.x - 1, ' ');
               changeElementAt(fix.toString(), vtcursor.y);
            }
            deletetext(false, vtcursor.x, vtcursor.y, lend, vtcursor.y);
            int bgAttr = CellAttr.bgOnly(currentAttr);
            if (bgAttr != CellAttr.DEFAULT)
               screenAttrs.fillAttr(vtcursor.y, vtcursor.x,
                  getColumns(), bgAttr);
            else
               screenAttrs.eraseToEnd(vtcursor.y, vtcursor.x);
         }
      }

      @Override
      void eraseToBeginning(StringBuilder sb) {
         insertString(sb);
         if (vtcursor.y >= readIn())
            return;
         String line = at(vtcursor.y);
         int end = Math.min(vtcursor.x + 1, line.length());
         if (end > 0) {
            StringBuilder newLine = new StringBuilder(line);
            for (int i = 0; i < end; i++)
               newLine.setCharAt(i, ' ');
            // Fix wide char split: erase end splits a pair
            if (end < line.length() && line.charAt(end) == WIDE_PAD)
               newLine.setCharAt(end, ' ');
            changeElementAt(newLine.toString(), vtcursor.y);
         }
         int bgAttr = CellAttr.bgOnly(currentAttr);
         if (bgAttr != CellAttr.DEFAULT)
            screenAttrs.fillAttr(vtcursor.y, 0,
               vtcursor.x + 1, bgAttr);
         else
            screenAttrs.fillAttr(vtcursor.y, 0,
               vtcursor.x + 1, CellAttr.DEFAULT);
      }

      @Override
      void eraseScreenToEnd(StringBuilder sb) {
         updateScreen(sb);
         int bgAttr = CellAttr.bgOnly(currentAttr);
         // Erase from cursor to end of current line
         if (vtcursor.y < readIn()) {
            int lend = at(vtcursor.y).length();
            if (vtcursor.x < lend)
               deletetext(false, vtcursor.x, vtcursor.y,
                  lend, vtcursor.y);
            if (bgAttr != CellAttr.DEFAULT)
               screenAttrs.fillAttr(vtcursor.y, vtcursor.x,
                  getColumns(), bgAttr);
            else
               screenAttrs.eraseToEnd(vtcursor.y, vtcursor.x);
         }
         // Erase all lines below cursor
         screenDirty = true;
         int fillCols = getColumns();
         for (int ii = vtcursor.y + 1; ii < readIn(); ii++) {
            changeElementAt("", ii);
            if (bgAttr != CellAttr.DEFAULT)
               screenAttrs.fillAttr(ii, 0, fillCols, bgAttr);
            else
               screenAttrs.eraseLine(ii);
         }
      }

      @Override
      void eraseScreenToBeginning(StringBuilder sb) {
         updateScreen(sb);
         int bgAttr = CellAttr.bgOnly(currentAttr);
         int start = readIn() - rows;
         if (start < 0) start = 0;
         // Erase all lines above cursor
         int fillCols = getColumns();
         for (int ii = start; ii < vtcursor.y; ii++) {
            changeElementAt("", ii);
            if (bgAttr != CellAttr.DEFAULT)
               screenAttrs.fillAttr(ii, 0, fillCols, bgAttr);
            else
               screenAttrs.eraseLine(ii);
         }
         screenDirty = true;
         // Erase from beginning of cursor line through cursor
         if (vtcursor.y < readIn()) {
            String line = at(vtcursor.y);
            int end = Math.min(vtcursor.x + 1, line.length());
            if (end > 0) {
               StringBuilder newLine = new StringBuilder(line);
               for (int i = 0; i < end; i++)
                  newLine.setCharAt(i, ' ');
               changeElementAt(newLine.toString(), vtcursor.y);
            }
            if (bgAttr != CellAttr.DEFAULT)
               screenAttrs.fillAttr(vtcursor.y, 0,
                  vtcursor.x + 1, bgAttr);
            else
               screenAttrs.fillAttr(vtcursor.y, 0,
                  vtcursor.x + 1, CellAttr.DEFAULT);
         }
      }

      void eraseLine(StringBuilder sb) {
         insertString(sb);
         changeElementAt("", vtcursor.y);
         int bgAttr = CellAttr.bgOnly(currentAttr);
         if (bgAttr != CellAttr.DEFAULT)
            screenAttrs.fillAttr(vtcursor.y, 0,
               getColumns(), bgAttr);
         else
            screenAttrs.eraseLine(vtcursor.y);
      }

      /**
       * Fixes orphaned wide-character pairs at a boundary position.
       * If position {@code pos} is a WIDE_PAD whose preceding wide
       * char is outside the operation range, replaces the wide char
       * with a space.  If position {@code pos-1} is a wide char whose
       * WIDE_PAD at {@code pos} has been removed, replaces the wide
       * char with a space.
       *
       * @param line  mutable line content
       * @param pos   boundary position to check
       */
      private void fixWideBoundary(StringBuilder line, int pos) {
         int len = line.length();
         // Check if pos points to an orphaned WIDE_PAD
         if (pos >= 0 && pos < len && line.charAt(pos) == WIDE_PAD) {
            if (pos == 0 || Wcwidth.of(line.charAt(pos - 1)) != 2)
               line.setCharAt(pos, ' ');
         }
         // Check if pos-1 is a wide char missing its WIDE_PAD
         if (pos > 0 && pos - 1 < len) {
            char ch = line.charAt(pos - 1);
            if (Wcwidth.of(ch) == 2) {
               if (pos >= len || line.charAt(pos) != WIDE_PAD)
                  line.setCharAt(pos - 1, ' ');
            }
         }
      }

      void eraseChars(int count, StringBuilder sb) {
         insertString(sb);
         if (vtcursor.y >= readIn())
            return;
         String line = at(vtcursor.y);
         int strlen = line.length();
         if (vtcursor.x >= strlen)
            return;
         int end = Math.min(vtcursor.x + count, strlen);
         StringBuilder newLine = new StringBuilder(line);
         for (int i = vtcursor.x; i < end; i++)
            newLine.setCharAt(i, ' ');
         // Fix wide char split at left boundary
         if (vtcursor.x > 0 && vtcursor.x < strlen
               && line.charAt(vtcursor.x) == WIDE_PAD)
            newLine.setCharAt(vtcursor.x - 1, ' ');
         // Fix wide char split at right boundary
         if (end < strlen && line.charAt(end) == WIDE_PAD)
            newLine.setCharAt(end, ' ');
         changeElementAt(newLine.toString(), vtcursor.y);
         screenAttrs.fillAttr(vtcursor.y, vtcursor.x, end,
            CellAttr.bgOnly(currentAttr));
      }

      void deleteChars(int count, StringBuilder sb) {
         insertString(sb);
         if (vtcursor.y >= readIn())
            return;
         String line = at(vtcursor.y);
         int strlen = line.length();
         int delCount = Math.min(count, strlen - vtcursor.x);
         if (delCount <= 0)
            return;
         int delto = vtcursor.x + delCount;
         // Fix wide char split: cursor at WIDE_PAD of a pair
         StringBuilder fixLine = null;
         if (vtcursor.x > 0 && vtcursor.x < strlen
               && line.charAt(vtcursor.x) == WIDE_PAD) {
            fixLine = new StringBuilder(line);
            fixLine.setCharAt(vtcursor.x - 1, ' ');
         }
         // Fix wide char split: deletion end splits a pair
         if (delto < strlen && line.charAt(delto) == WIDE_PAD) {
            if (fixLine == null)
               fixLine = new StringBuilder(line);
            fixLine.setCharAt(delto, ' ');
         }
         if (fixLine != null)
            changeElementAt(fixLine.toString(), vtcursor.y);
         deletetext(false, vtcursor.x, vtcursor.y, delto, vtcursor.y);
         int bgAttr = CellAttr.bgOnly(currentAttr);
         screenAttrs.shiftCellsLeft(vtcursor.y, vtcursor.x,
            delCount, getColumns(), bgAttr);
      }

      @Override
      void insertChars(int count, StringBuilder sb) {
         insertString(sb);
         if (vtcursor.y >= readIn())
            return;
         String line = at(vtcursor.y);
         int cols = getColumns();
         int cx = vtcursor.x;
         // Build new line: prefix + spaces + suffix, truncated
         StringBuilder newLine = new StringBuilder(cols);
         for (int i = 0; i < cx && i < line.length(); i++)
            newLine.append(line.charAt(i));
         while (newLine.length() < cx)
            newLine.append(' ');
         for (int i = 0; i < count; i++)
            newLine.append(' ');
         for (int i = cx; i < line.length(); i++)
            newLine.append(line.charAt(i));
         if (newLine.length() > cols)
            newLine.setLength(cols);
         // Fix wide char split at cursor position
         fixWideBoundary(newLine, cx);
         // Fix wide char split at truncation boundary
         if (newLine.length() == cols)
            fixWideBoundary(newLine, cols);
         changeElementAt(newLine.toString(), vtcursor.y);
         int bgAttr = CellAttr.bgOnly(currentAttr);
         screenAttrs.shiftCellsRight(vtcursor.y, cx, count,
            cols, bgAttr);
      }

      void insertLines(int count, StringBuilder sb) {
         insertString(sb);
         int bottomAbs = termRowToAbs(effectiveBottom());
         if (bottomAbs < 0 || bottomAbs >= readIn())
            return;
         int actual = Math.min(count,
            bottomAbs - vtcursor.y + 1);
         if (actual <= 0)
            return;
         // Remove lines at bottom of scroll region,
         // insert blanks at cursor position.
         for (int i = 0; i < actual; i++) {
            if (bottomAbs < readIn())
               remove(bottomAbs, 1);
            insertOne("", vtcursor.y);
         }
         screenAttrs.shiftLinesDown(
            vtcursor.y, bottomAbs + 1, actual);
         screenDirty = true;
      }

      void setInsertMode(boolean val, StringBuilder sb) {
         insertString(sb);
         insertmode = val;
      }

      @Override
      void setLnmMode(boolean enable) {
         lnmMode = enable;
      }

      void updateScreen(StringBuilder sb) {
         insertString(sb);
         fixline();
         //rows = currfvc.vi.getRows((float).99999);
         if (null != currfvc) {
            currfvc.cursorabs(vtcursor.x, vtcursor.y);
            currfvc.placeline(readIn() - 1, .99999f);
            // When a bulk operation (scroll, erase, alt screen
            // switch, insert/delete lines) changed lines beyond
            // just the cursor line, force a full redraw.  The
            // cursorabs call above only marks the cursor line as
            // CHANGE; without this, all other modified lines
            // would not be repainted until the next focus event.
            if (screenDirty) {
               screenDirty = false;
               currfvc.vi.redraw();
            }
         }
         // Evict offscreen attribute rows to prevent unbounded memory
         // growth (e.g. htop refreshing rapidly). Keep 2x visible rows
         // as scrollback margin.
         if (rows > 0) {
            int minVisible = readIn() - rows * 3;
            if (minVisible > 1)
               screenAttrs.evictBefore(minVisible);
         }
         // Trim scrollback buffer to prevent unbounded text buffer
         // growth during long terminal sessions.
         trimScrollback();
      }

      /** Maximum scrollback lines beyond the visible screen. */
      private static final int MAX_SCROLLBACK = 1000;

      /**
       * Trims the text buffer when it exceeds the scrollback limit.
       * Removes the oldest lines and adjusts cursor and attribute
       * line numbers accordingly.
       */
      private void trimScrollback() {
         int excess = readIn() - 1 - rows - MAX_SCROLLBACK;
         if (excess > 0) {
            remove(1, excess);
            vtcursor.y -= excess;
            if (vtcursor.y < 0)
               vtcursor.y = 0;
            // savedAltCursor.y is viewport-relative — no adjustment
            // needed when trimming scrollback.
            screenAttrs.shiftAllLines(-excess);
            // Shift autowrap flags for removed lines
            java.util.HashSet<Integer> shifted =
               new java.util.HashSet<>();
            autowrappedLines.forEach(line -> {
               int newLine = line - excess;
               if (newLine >= 1)
                  shifted.add(newLine);
            });
            autowrappedLines.clear();
            autowrappedLines.addAll(shifted);
         }
      }

      void saveCursor(StringBuilder sb) {
         insertString(sb);
         savecursor.x = vtcursor.x;
         savecursor.y = readIn() - vtcursor.y;
         cursorSaved = true;
         sgrState.saveState();
      }

      void restoreCursor(StringBuilder sb) {
         insertString(sb);
         pendingWrap = false;
         if (!cursorSaved) {
            // Per VT spec: DECRC without prior DECSC homes cursor
            vtcursor.y = termRowToAbs(1);
            vtcursor.x = 0;
         } else {
            vtcursor.y = readIn() - savecursor.y;
            vtcursor.x = savecursor.x;
         }
         sgrState.restoreState();
         currentAttr = sgrState.currentAttr();
      }

      // F10: Alternate screen buffer state
      private ArrayList<String> savedScreen;
      private MovePos savedAltCursor;
      private boolean inAlternateScreen;
      /** Main screen's DECSC save slot (preserved during alt screen). */
      private MovePos savedMainDecsc;
      private boolean savedMainDecscValid;
      /** Alt screen's DECSC save slot (preserved when returning to main). */
      private MovePos savedAltDecsc;
      private boolean savedAltDecscValid;

      void switchAlternateScreen(boolean enable, StringBuilder sb) {
         insertString(sb);
         if (termLog.isEnabled())
            termLog.log("ALT_SCREEN " + (enable ? "ON" : "OFF")
               + " currently=" + inAlternateScreen
               + " cursor=" + vtcursor.x + "," + vtcursor.y
               + " rows=" + rows + " readIn=" + readIn());
         if (enable && !inAlternateScreen) {
            // Preserve main screen's DECSC/DECRC save slot
            savedMainDecsc = new MovePos(savecursor.x,
               savecursor.y);
            savedMainDecscValid = cursorSaved;
            // Restore alt screen's DECSC save slot, or reset
            if (savedAltDecsc != null) {
               savecursor = savedAltDecsc;
               cursorSaved = savedAltDecscValid;
               savedAltDecsc = null;
            } else {
               savecursor = new MovePos(0, 1);
               cursorSaved = false;
            }
            // Save main screen content and attributes
            int end = readIn();
            int start = end - rows;
            if (start < 0)
               start = 0;
            savedScreen = new ArrayList<>(rows);
            for (int ii = start; ii < end; ii++)
               savedScreen.add(at(ii).toString());
            // Save cursor relative to viewport start so it survives
            // buffer growth during the alternate screen session.
            savedAltCursor = new MovePos(vtcursor.x,
               vtcursor.y - start);
            savedScreenAttrs = screenAttrs.snapshot();
            // Clear screen for alternate buffer
            for (int ii = start; ii < end; ii++)
               changeElementAt("", ii);
            screenAttrs.clear();
            vtcursor.x = 0;
            vtcursor.y = start;
            inAlternateScreen = true;
            screenDirty = true;
            trace("switched to alternate screen buffer");
         } else if (!enable && inAlternateScreen) {
            // Preserve alt screen's DECSC/DECRC save slot
            savedAltDecsc = new MovePos(savecursor.x,
               savecursor.y);
            savedAltDecscValid = cursorSaved;
            // Restore main screen's DECSC/DECRC save slot
            if (savedMainDecsc != null) {
               savecursor = savedMainDecsc;
               cursorSaved = savedMainDecscValid;
               savedMainDecsc = null;
            }
            // Restore main screen content and attributes
            int end = readIn();
            int start = end - rows;
            if (start < 0)
               start = 0;
            // Clear all viewport lines first — the buffer may have
            // grown during the alt screen session, so lines beyond
            // savedScreen.size() would otherwise retain alt content.
            for (int ii = start; ii < end; ii++)
               changeElementAt("", ii);
            screenAttrs.eraseScreen(start, end);
            if (savedScreen != null) {
               for (int ii = 0;
                     ii < savedScreen.size() && start + ii < end;
                     ii++)
                  changeElementAt(savedScreen.get(ii), start + ii);
               savedScreen = null;
            }
            if (savedScreenAttrs != null) {
               screenAttrs.restoreFrom(savedScreenAttrs);
               savedScreenAttrs = null;
            }
            if (savedAltCursor != null) {
               // Restore cursor relative to current viewport
               vtcursor.x = savedAltCursor.x;
               vtcursor.y = start + savedAltCursor.y;
               if (vtcursor.y < start)
                  vtcursor.y = start;
               if (vtcursor.y >= end)
                  vtcursor.y = end - 1;
               savedAltCursor = null;
            }
            inAlternateScreen = false;
            screenDirty = true;
            trace("restored main screen buffer");
         }
         // For enable: update display immediately so the blank alt
         // screen is visible.  For disable: defer the updateScreen
         // so that the caller can adjust the cursor first (e.g.,
         // mode 1049 calls restoreCursor after this method).  The
         // screenDirty flag ensures the next updateScreen triggers
         // a full redraw.
         if (enable)
            updateScreen(sb);
      }

      void setMouseTracking(int mode, boolean enable) {
         Vt100.this.setMouseTracking(mode, enable);
      }

      void setSgrMouseMode(boolean enable) {
         Vt100.this.setSgrMouseMode(enable);
      }

      void setBracketedPasteMode(boolean enable) {
         Vt100.this.setBracketedPasteMode(enable);
      }

      void setFocusEventsMode(boolean enable) {
         Vt100.this.setFocusEventsMode(enable);
      }

      void setAutowrapMode(boolean enable) {
         Vt100.this.setAutowrapMode(enable);
      }

      void setReverseWrapMode(boolean enable) {
         Vt100.this.setReverseWrapMode(enable);
      }

      void setReverseWrapExtendMode(boolean enable) {
         Vt100.this.setReverseWrapExtendMode(enable);
      }

      void setCursorBlinkMode(boolean enable) {
         Vt100.this.setCursorBlinkMode(enable);
      }

      void setApplicationCursorKeys(boolean enable) {
         Vt100.this.setApplicationCursorKeys(enable);
      }

      void setCursorVisible(boolean visible) {
         Vt100.this.setCursorVisible(visible);
      }

      @Override
      void setTitle(String title) {
         oscTitle = title;
      }

      @Override
      void setIconTitle(String title) {
         oscIconTitle = title;
      }

      @Override
      void saveMode(int modeNum) {
         savedModes.put(modeNum, getModeState(modeNum));
      }

      @Override
      void restoreMode(int modeNum, StringBuilder sb) {
         Boolean saved = savedModes.get(modeNum);
         if (saved != null)
            applyModeInState(modeNum, saved, sb);
      }

      @Override
      void respondDecrqm(int modeNum, StringBuilder sb) {
         insertString(sb);
         // DECRPM response: CSI ? Ps; Pm $ y
         // Pm: 0=not recognized, 1=set, 2=reset
         int pm;
         if (isKnownMode(modeNum))
            pm = getModeState(modeNum) ? 1 : 2;
         else
            pm = 0;
         Vt100.this.sendResponse("\033[?" + modeNum + ";"
            + pm + "$y");
      }

      /** Returns true if modeNum is a mode we track. */
      private boolean isKnownMode(int modeNum) {
         switch (modeNum) {
            case 1: case 6: case 7: case 25: case 40:
            case 45: case 47: case 1000: case 1002: case 1003:
            case 1006: case 1045: case 1047: case 1048: case 1049:
            case 2004:
               return true;
            default:
               return false;
         }
      }

      private boolean getModeState(int modeNum) {
         switch (modeNum) {
            case 1:    return applicationCursorKeys;
            case 6:    return originMode;
            case 7:    return autowrapMode;
            case 25:   return cursorVisible;
            case 40:   return allow80To132;
            case 45:   return reverseWrapMode;
            case 47:
            case 1047:
            case 1049: return inAlternateScreen;
            case 1000: return Vt100.this.mouseTrackingMode == 1000;
            case 1002: return Vt100.this.mouseTrackingMode == 1002;
            case 1003: return Vt100.this.mouseTrackingMode == 1003;
            case 1006: return Vt100.this.sgrMouseMode;
            case 1045: return reverseWrapExtendMode;
            case 1048: return cursorSaved;
            case 2004: return bracketedPasteMode;
            default:   return false;
         }
      }

      private void applyModeInState(int modeNum, boolean enable,
            StringBuilder sb) {
         switch (modeNum) {
            case 1:    setApplicationCursorKeys(enable); break;
            case 6:    setOriginMode(enable); break;
            case 7:    setAutowrapMode(enable); break;
            case 25:   setCursorVisible(enable); break;
            case 40:   setAllow80To132(enable); break;
            case 45:   setReverseWrapMode(enable); break;
            case 1045: setReverseWrapExtendMode(enable); break;
            case 47:
            case 1047:
            case 1049: switchAlternateScreen(enable, sb); break;
            case 2004: setBracketedPasteMode(enable); break;
            default: break;
         }
      }

      @Override
      void respondTerminfoQuery(String hexName) {
         // DCS +q: query terminfo capability by hex-encoded name.
         // "Co" (436F) = number of indexed colors.
         // "colors" (636F6C6F7273) is the long form.
         if ("436F".equalsIgnoreCase(hexName)
               || "636F6C6F7273".equalsIgnoreCase(hexName)) {
            // Respond: DCS 1+r hexName = hexValue ST
            // 256 in hex-encoded ASCII: '2'=32, '5'=35, '6'=36
            Vt100.this.sendResponse(
               "\033P1+r" + hexName + "=323536\033\\");
         } else {
            // Respond "not found": DCS 0+r hexName ST
            Vt100.this.sendResponse(
               "\033P0+r" + hexName + "\033\\");
         }
      }

      void respondDeviceAttributes(StringBuilder sb) {
         insertString(sb);
         // VT220: 62=VT220, 1=132cols, 2=printer, 6=selective-erase,
         // 9=national-replacement, 15=tech-chars, 22=ANSI-color, 29=ANSI-text-locator
         Vt100.this.sendResponse(
            "\033[?62;1;2;6;9;15;22;29c");
      }

      void respondSecondaryDA(StringBuilder sb) {
         insertString(sb);
         // VT220 (1), version 314, no ROM cartridge (0)
         Vt100.this.sendResponse("\033[>1;314;0c");
      }

      void respondCursorPosition(StringBuilder sb) {
         insertString(sb);
         int row = vtcursor.y - readIn() + rows + 1;
         int col = vtcursor.x + 1;
         if (row < 1) row = 1;
         if (col < 1) col = 1;
         Vt100.this.sendResponse("\033[" + row + ";" + col + "R");
      }

      void respondStatusOk(StringBuilder sb) {
         insertString(sb);
         Vt100.this.sendResponse("\033[0n");
      }

      /**
       * Responds to DECRQCRA (Request Checksum of Rectangular Area).
       *
       * <p>Computes a 16-bit checksum of character values in the
       * specified rectangle and responds with a DCS sequence.</p>
       *
       * @param params CSI parameters [Pid, Pp, Pt, Pl, Pb, Pr]
       * @param highParam index of the highest parameter set
       * @param sb pending output buffer to flush
       */
      @Override
      void respondRectChecksum(int[] params, int highParam,
            StringBuilder sb) {
         insertString(sb);
         int pid = highParam >= 0 ? params[0] : 0;
         // Pp (page) is params[1], ignored — single page
         int cols = getColumns();
         int pt = highParam >= 2 ? params[2] : 1;
         int pl = highParam >= 3 ? params[3] : 1;
         int pb = highParam >= 4 ? params[4] : rows;
         int pr = highParam >= 5 ? params[5] : cols;
         // Clamp defaults: 0 means default
         if (pt <= 0) pt = 1;
         if (pl <= 0) pl = 1;
         if (pb <= 0) pb = rows;
         if (pr <= 0) pr = cols;
         // Clamp to screen bounds
         if (pt > rows) pt = rows;
         if (pb > rows) pb = rows;
         if (pl > cols) pl = cols;
         if (pr > cols) pr = cols;
         int checksum = 0;
         for (int r = pt; r <= pb; r++) {
            int absLine = termRowToAbs(r);
            String line = absLine >= 1 && absLine < readIn()
               ? at(absLine) : "";
            for (int c = pl; c <= pr; c++) {
               int idx = c - 1; // 0-based column index
               char ch = idx < line.length()
                  ? line.charAt(idx) : ' ';
               checksum += ch;
            }
         }
         checksum &= 0xFFFF;
         Vt100.this.sendResponse("\033P" + pid + "!~"
            + String.format("%04X", checksum) + "\033\\");
      }

      @Override
      void handleWindowOp(int ps, int p1, int p2,
            StringBuilder sb) {
         insertString(sb);
         int cols = getColumns();
         switch (ps) {
            case 8: { // Resize terminal in characters (CSI 8;rows;cols t)
               if (p1 > 0 || p2 > 0) {
                  int newRows = p1 > 0 ? p1 : rows;
                  int newCols = p2 > 0 ? p2 : cols;
                  if (currfvc != null && currfvc.vi != null)
                     currfvc.vi.setSizebyChar(newCols, newRows);
               }
               break;
            }
            case 14: // Report terminal size in pixels
               // Approximate: 8 pixels/char width, 16 pixels/char height
               Vt100.this.sendResponse(
                  "\033[4;" + (rows * 16) + ";" + (cols * 8) + "t");
               break;
            case 18: // Report terminal size in characters
               Vt100.this.sendResponse(
                  "\033[8;" + rows + ";" + cols + "t");
               break;
            case 20: { // Report icon label
               String icoTitle =
                  oscIconTitle != null ? oscIconTitle : "";
               Vt100.this.sendResponse(
                  "\033]L" + icoTitle + "\033\\");
               break;
            }
            case 21: { // Report window title
               String winTitle = oscTitle != null ? oscTitle : "";
               Vt100.this.sendResponse(
                  "\033]l" + winTitle + "\033\\");
               break;
            }
            case 22: { // Save title (push to stack)
               String icoT =
                  oscIconTitle != null ? oscIconTitle : "";
               String winT = oscTitle != null ? oscTitle : "";
               titleStack.push(new String[]{icoT, winT});
               break;
            }
            case 23: { // Restore title (pop from stack)
               if (!titleStack.isEmpty()) {
                  String[] entry = titleStack.pop();
                  if (p1 == 0 || p1 == 1)
                     oscIconTitle = entry[0];
                  if (p1 == 0 || p1 == 2)
                     oscTitle = entry[1];
               }
               break;
            }
            default:
               // Other window ops (iconify, resize, etc.) ignored
               trace("handleWindowOp: CSI " + ps + "t"
                  + " — unhandled window operation (ignored)");
               break;
         }
      }

      @Override
      void handleOscColor(int oscNum, String payload) {
         switch (oscNum) {
            case 4:
               handleOscPaletteColor(payload);
               break;
            case 5:
               handleOscSpecialColor5(payload);
               break;
            case 10:
               handleOscDynamicColor(10, payload);
               break;
            case 11:
               handleOscDynamicColor(11, payload);
               break;
            case 12:
               handleOscDynamicColor(12, payload);
               break;
            case 17:
               handleOscSpecialColor(17, payload);
               break;
            case 19:
               handleOscSpecialColor(19, payload);
               break;
            case 104:
               handleOscResetPalette(payload);
               break;
            case 105:
               handleOscResetSpecial(payload);
               break;
            case 110:
               dynamicFg = TerminalPalette.DEFAULT_FG;
               break;
            case 111:
               dynamicBg = TerminalPalette.DEFAULT_BG;
               break;
            case 112:
               dynamicCursor = TerminalPalette.DEFAULT_CURSOR;
               break;
            case 117:
               highlightFg = TerminalPalette.DEFAULT_HIGHLIGHT_FG;
               break;
            case 119:
               highlightBg = TerminalPalette.DEFAULT_HIGHLIGHT_BG;
               break;
            default:
               trace("handleOscColor: unhandled OSC " + oscNum);
         }
      }

      /**
       * Handles OSC 4 — palette color set/query.
       *
       * <p>Payload format: {@code N;spec} to set, {@code N;?} to query.
       * Multiple pairs can be chained: {@code N;spec;M;spec}.</p>
       */
      private void handleOscPaletteColor(String payload) {
         if (payload.isEmpty())
            return;
         String[] parts = payload.split(";");
         int i = 0;
         while (i < parts.length) {
            int idx;
            try {
               idx = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
               break;
            }
            if (idx < 0 || idx >= palette.length)
               break;
            i++;
            if (i >= parts.length)
               break;
            String spec = parts[i];
            i++;
            if ("?".equals(spec)) {
               // Query: respond with current color
               java.awt.Color c = palette[idx];
               Vt100.this.sendResponse("\033]4;" + idx + ";"
                  + TerminalPalette.formatX11Color(c) + "\033\\");
            } else {
               // Set palette color
               java.awt.Color c =
                  TerminalPalette.parseX11Color(spec);
               if (c != null)
                  palette[idx] = c;
            }
         }
      }

      /**
       * Handles OSC 10/11/12 — dynamic color set/query.
       *
       * <p>OSC 10 = foreground, 11 = background, 12 = cursor.
       * When chained ({@code OSC 10;spec;spec;spec}), sets
       * fg, bg, cursor in sequence.</p>
       */
      private void handleOscDynamicColor(int startOsc,
            String payload) {
         if (payload.isEmpty())
            return;
         String[] parts = payload.split(";");
         int osc = startOsc;
         for (String spec : parts) {
            if (osc > 12)
               break;
            if ("?".equals(spec)) {
               java.awt.Color c = getDynamicColor(osc);
               if (c != null)
                  Vt100.this.sendResponse("\033]" + osc + ";"
                     + TerminalPalette.formatX11Color(c)
                     + "\033\\");
            } else {
               java.awt.Color c =
                  TerminalPalette.parseX11Color(spec);
               if (c != null)
                  setDynamicColor(osc, c);
            }
            osc++;
         }
      }

      /**
       * Handles OSC 17/19 — special color set/query.
       */
      private void handleOscSpecialColor(int oscNum,
            String payload) {
         if (payload.isEmpty())
            return;
         String[] parts = payload.split(";");
         int osc = oscNum;
         for (String spec : parts) {
            if ("?".equals(spec)) {
               java.awt.Color c = getSpecialColor(osc);
               if (c != null)
                  Vt100.this.sendResponse("\033]" + osc + ";"
                     + TerminalPalette.formatX11Color(c)
                     + "\033\\");
            } else {
               java.awt.Color c =
                  TerminalPalette.parseX11Color(spec);
               if (c != null)
                  setSpecialColor(osc, c);
            }
            osc += 2; // 17→19 (highlight fg→bg)
         }
      }

      /**
       * Handles OSC 104 — reset palette color(s).
       *
       * <p>Empty payload resets all. Otherwise semicolon-separated
       * indices to reset individually.</p>
       */
      private void handleOscResetPalette(String payload) {
         if (payload.isEmpty()) {
            for (int i = 0; i < 256; i++)
               palette[i] = TerminalPalette.defaultColor(i);
         } else {
            for (String s : payload.split(";")) {
               try {
                  int idx = Integer.parseInt(s);
                  if (idx >= 0 && idx <= 255)
                     palette[idx] =
                        TerminalPalette.defaultColor(idx);
               } catch (NumberFormatException e) {
                  // skip invalid index
               }
            }
         }
      }

      /**
       * Handles OSC 5 — special color set/query (alternative to
       * OSC 4 with offset).
       *
       * <p>Maps special color index N to palette[256+N].</p>
       */
      private void handleOscSpecialColor5(String payload) {
         if (payload.isEmpty())
            return;
         String[] parts = payload.split(";");
         int i = 0;
         while (i < parts.length) {
            int idx;
            try {
               idx = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
               break;
            }
            if (idx < 0 || idx > 5)
               break;
            i++;
            if (i >= parts.length)
               break;
            String spec = parts[i];
            i++;
            int palIdx = 256 + idx;
            if ("?".equals(spec)) {
               java.awt.Color c = palette[palIdx];
               Vt100.this.sendResponse("\033]5;" + idx + ";"
                  + TerminalPalette.formatX11Color(c)
                  + "\033\\");
            } else {
               java.awt.Color c =
                  TerminalPalette.parseX11Color(spec);
               if (c != null)
                  palette[palIdx] = c;
            }
         }
      }

      /**
       * Handles OSC 105 — reset special color(s).
       *
       * <p>Empty payload resets all. Otherwise semicolon-separated
       * special color indices to reset individually.
       * Special color N maps to palette[256+N].</p>
       */
      private void handleOscResetSpecial(String payload) {
         if (payload.isEmpty()) {
            for (int i = 256; i < 262; i++)
               palette[i] = TerminalPalette.DEFAULT_FG;
         } else {
            for (String s : payload.split(";")) {
               try {
                  int idx = Integer.parseInt(s);
                  if (idx >= 0 && idx <= 5)
                     palette[256 + idx] =
                        TerminalPalette.DEFAULT_FG;
               } catch (NumberFormatException e) {
                  // skip invalid index
               }
            }
         }
      }

      /** Returns the current dynamic color for an OSC number. */
      private java.awt.Color getDynamicColor(int osc) {
         switch (osc) {
            case 10: return dynamicFg;
            case 11: return dynamicBg;
            case 12: return dynamicCursor;
            default: return null;
         }
      }

      /** Sets the dynamic color for an OSC number. */
      private void setDynamicColor(int osc, java.awt.Color c) {
         switch (osc) {
            case 10: dynamicFg = c; break;
            case 11: dynamicBg = c; break;
            case 12: dynamicCursor = c; break;
            default: break;
         }
      }

      /** Returns the current special color for an OSC number. */
      private java.awt.Color getSpecialColor(int osc) {
         switch (osc) {
            case 17: return highlightFg;
            case 19: return highlightBg;
            default: return null;
         }
      }

      /** Sets the special color for an OSC number. */
      private void setSpecialColor(int osc, java.awt.Color c) {
         switch (osc) {
            case 17: highlightFg = c; break;
            case 19: highlightBg = c; break;
            default: break;
         }
      }

      /** Returns the effective bottom margin (1-based terminal row). */
      private int effectiveBottom() {
         return scrollBottom > 0 ? scrollBottom : rows;
      }

      /** Converts 1-based terminal row to absolute buffer line. */
      private int termRowToAbs(int termRow) {
         return readIn() - 1 - rows + termRow;
      }

      void setScrollRegion(int top, int bottom, StringBuilder sb) {
         insertString(sb);
         if (top == 0 && bottom == 0) {
            scrollTop = 1;
            scrollBottom = 0;
         } else {
            scrollTop = Math.max(1, top);
            scrollBottom = Math.max(scrollTop, bottom);
         }
         // DECSTBM homes the cursor
         vtcursor.y = termRowToAbs(originMode ? scrollTop : 1);
         vtcursor.x = 0;
      }

      void setOriginMode(boolean enable) {
         originMode = enable;
         // DECOM change homes the cursor
         if (originMode)
            vtcursor.y = termRowToAbs(scrollTop);
         else
            vtcursor.y = termRowToAbs(1);
         vtcursor.x = 0;
      }

      @Override
      void setXY(int xval, int yval, StringBuilder sb) {
         updateScreen(sb);
         pendingWrap = false;
         int cols = getColumns();
         // Clamp column to screen width when terminal size is known
         if (cols > 0 && xval > cols)
            xval = cols;
         // Clamp row to screen height (or scroll region in DECOM)
         if (rows > 0) {
            int maxRow = originMode ? effectiveBottom() : rows;
            if (yval > maxRow)
               yval = maxRow;
         }
         int row = Math.max(1, yval);
         if (originMode)
            row = yval + scrollTop - 1;
         vtcursor.y = termRowToAbs(row);
         if (vtcursor.y < 0)
            vtcursor.y = 0;
         vtcursor.x = Math.max(0, xval - 1);
      }

      @Override
      void setY(int val, StringBuilder sb) {
         updateScreen(sb);
         pendingWrap = false;
         // Clamp row to screen height (or scroll region in DECOM)
         if (rows > 0) {
            int maxRow = originMode ? effectiveBottom() : rows;
            if (val > maxRow)
               val = maxRow;
         }
         int row = Math.max(1, val);
         if (originMode)
            row = val + scrollTop - 1;
         vtcursor.y = termRowToAbs(row);
         if (vtcursor.y < 0)
            vtcursor.y = 0;
      }

      void index(StringBuilder sb) {
         updateScreen(sb);
         pendingWrap = false;
         int bottomAbs = termRowToAbs(effectiveBottom());
         if (vtcursor.y >= bottomAbs) {
            // At or past bottom margin — scroll region up
            scrollRegionUp(1);
         } else {
            vtcursor.y++;
            if (vtcursor.y >= readIn()) {
               insertOne("", readIn());
            }
         }
      }

      void reverseIndex(StringBuilder sb) {
         updateScreen(sb);
         pendingWrap = false;
         int topAbs = termRowToAbs(scrollTop);
         if (vtcursor.y <= topAbs) {
            // At or above top margin — scroll region down
            scrollRegionDown(1);
         } else {
            vtcursor.y--;
            if (vtcursor.y < 0)
               vtcursor.y = 0;
         }
      }

      void nextLine(StringBuilder sb) {
         updateScreen(sb);
         pendingWrap = false;
         vtcursor.x = 0;
         int bottomAbs = termRowToAbs(effectiveBottom());
         if (vtcursor.y >= bottomAbs) {
            scrollRegionUp(1);
         } else {
            vtcursor.y++;
            if (vtcursor.y >= readIn())
               insertOne("", readIn());
         }
      }

      void screenAlignmentDisplay(StringBuilder sb) {
         insertString(sb);
         int cols = getColumns();
         StringBuilder eline = new StringBuilder(cols);
         for (int i = 0; i < cols; i++)
            eline.append('E');
         String estr = eline.toString();
         int start = readIn() - rows;
         if (start < 0)
            start = 0;
         for (int ii = start; ii < readIn(); ii++)
            changeElementAt(estr, ii);
         // DECALN resets margins and moves cursor home
         scrollTop = 1;
         scrollBottom = 0;
         originMode = false;
         vtcursor.y = termRowToAbs(1);
         vtcursor.x = 0;
         screenDirty = true;
      }

      @Override
      void setColumnMode(int cols, StringBuilder sb) {
         insertString(sb);
         if (!allow80To132)
            return;
         columns = cols;
         // Per VT100 spec: clear screen, reset margins, home cursor
         eraseScreen(sb);
         scrollTop = 1;
         scrollBottom = 0;
         originMode = false;
         vtcursor.y = termRowToAbs(1);
         vtcursor.x = 0;
         initTabStops();
      }

      @Override
      void setAllow80To132(boolean enable) {
         allow80To132 = enable;
         if (!enable && columns != 0) {
            // When disabling, revert to actual terminal width
            columns = 0;
         }
      }

      /**
       * Adjusts scroll region, tab stops, and column count
       * after terminal resize. If scrollBottom matched the old
       * row count (full screen), reset it to track the new
       * size. Reinitializes tab stops for the new column count.
       */
      void handleResize(int oldRows, int newRows, int newCols) {
         if (scrollBottom == oldRows || scrollBottom > newRows)
            scrollBottom = 0;
         if (scrollTop > newRows)
            scrollTop = 1;
         columns = 0; // clear DECCOLM override; use actual size
         screenAttrs.setColumns(newCols);
         initTabStops();
      }

      @Override
      void deleteLines(int count, StringBuilder sb) {
         insertString(sb);
         int bottomAbs = termRowToAbs(effectiveBottom());
         if (bottomAbs < 0 || bottomAbs >= readIn())
            return;
         int actual = Math.min(count, bottomAbs - vtcursor.y + 1);
         if (actual <= 0)
            return;
         // Remove lines at cursor, insert blanks at bottom of region
         for (int i = 0; i < actual; i++) {
            if (vtcursor.y < readIn())
               remove(vtcursor.y, 1);
            insertOne("", bottomAbs);
         }
         // Shift attributes
         screenAttrs.shiftLinesUp(vtcursor.y, bottomAbs + 1, actual);
         screenDirty = true;
      }

      @Override
      void scrollUp(int count, StringBuilder sb) {
         updateScreen(sb);
         for (int i = 0; i < count; i++)
            scrollRegionUp(1);
      }

      @Override
      void scrollDown(int count, StringBuilder sb) {
         updateScreen(sb);
         for (int i = 0; i < count; i++)
            scrollRegionDown(1);
      }

      /**
       * Scrolls the scroll region up by one line: removes top line
       * of region and inserts a blank at the bottom.
       */
      private void scrollRegionUp(int count) {
         int topAbs = termRowToAbs(scrollTop);
         int bottomAbs = termRowToAbs(effectiveBottom());
         if (topAbs < 0 || bottomAbs < topAbs || bottomAbs >= readIn())
            return;
         for (int i = 0; i < count; i++) {
            if (topAbs < readIn()) {
               remove(topAbs, 1);
               insertOne("", bottomAbs);
            }
            // Shift autowrap flags: lines above topAbs are gone,
            // lines in [topAbs+1, bottomAbs] shift down by 1.
            shiftAutowrapLines(topAbs, bottomAbs, -1);
         }
         screenAttrs.shiftLinesUp(topAbs, bottomAbs + 1, count);
         screenDirty = true;
      }

      /**
       * Scrolls the scroll region down by one line: removes bottom
       * line of region and inserts a blank at the top.
       */
      private void scrollRegionDown(int count) {
         int topAbs = termRowToAbs(scrollTop);
         int bottomAbs = termRowToAbs(effectiveBottom());
         if (topAbs < 0 || bottomAbs < topAbs || bottomAbs >= readIn())
            return;
         for (int i = 0; i < count; i++) {
            if (bottomAbs < readIn()) {
               remove(bottomAbs, 1);
               insertOne("", topAbs);
            }
            shiftAutowrapLines(topAbs, bottomAbs, 1);
         }
         screenAttrs.shiftLinesDown(topAbs, bottomAbs + 1, count);
         screenDirty = true;
      }

      /**
       * Shifts autowrap flags within a scroll region.
       *
       * @param topAbs  absolute line of region top
       * @param botAbs  absolute line of region bottom
       * @param delta   -1 for scroll up, +1 for scroll down
       */
      private void shiftAutowrapLines(int topAbs, int botAbs,
            int delta) {
         java.util.HashSet<Integer> shifted = new java.util.HashSet<>();
         java.util.Iterator<Integer> it = autowrappedLines.iterator();
         while (it.hasNext()) {
            int line = it.next();
            if (line >= topAbs && line <= botAbs) {
               it.remove();
               int newLine = line + delta;
               if (newLine >= topAbs && newLine <= botAbs)
                  shifted.add(newLine);
            }
         }
         autowrappedLines.addAll(shifted);
      }

   }

   private String fixline() {
      String eline = at(vtcursor.y);
      if (vtcursor.x > eline.length()) {
         StringBuilder ebuf = new StringBuilder(eline);
         while (vtcursor.x > ebuf.length())
            ebuf.append(' ');
         eline = ebuf.toString();
         changeElementAt(eline, vtcursor.y);
      }
      return eline;
   }

   private void setXmy(int val, StringBuilder sb) {
      insertString(sb);
      vtcursor.x = Math.max(0, val - 1);
   }

   /**
    * Records the current SGR attribute for a range of cells
    * starting at (line, col) for {@code len} characters.
    */
   private void recordAttrs(int line, int col, int len) {
      if (len > 0) {
         ecscreen.screenAttrs.fillAttr(line, col, col + len,
            ecscreen.currentAttr);
      }
   }

   private void insertString(StringBuilder sb) {
      //trace("insertString " + this);
      if (vtcursor.y < 0) {
         vtcursor.y = 0;
         vtcursor.x = 0;
      }
      if (vtcursor.y > readIn() - 1) {
         trace("cursor past buffer end: vtcursor.y=" + vtcursor.y
            + " readIn=" + readIn() + " rows=" + rows
            + " altScreen=" + ecscreen.inAlternateScreen);
         insertOne("", readIn());
         if (currfvc != null) {
            trace("extending buffer to match view rows at " + readIn());
            int neededRows = currfvc.vi.getRows(1.0f) - readIn() + 1;
            while (-1 < --neededRows)
               insertOne("", readIn());
         }
         vtcursor.y = readIn() - 1;
         vtcursor.x = 0;
      }
      fixline();
      if (0 == sb.length())
         return;

      boolean setxflag = false;
      if ('\r' == sb.charAt(sb.length() - 1)) {
         sb.setLength(sb.length() - 1);
         setxflag = true;
      }
      String text = sb.toString();
      sb.setLength(0);
      int sbused = 0;

      // Resolve pending autowrap before writing new text
      if (ecscreen.pendingWrap && text.length() > 0
            && autowrapMode) {
         ecscreen.doAutowrap();
      }

      if (!insertmode && vtcursor.y < readIn()) {
         while (sbused < text.length()) {
            String eline = fixline();
            if (eline.length() < vtcursor.x)
               break;
            int nindex = text.indexOf('\n', sbused);
            if (nindex == -1) {
               sbused += insertChunk(
                  text.substring(sbused));
            } else if (nindex == sbused) {
               sbused++;
               advanceLine();
            } else {
               sbused += insertChunk(
                  text.substring(sbused, nindex));
               sbused = nindex + 1;
               advanceLine();
            }
         }
      }
      if (sbused < text.length()) {
         String itext = Wcwidth.expandWide(text.substring(sbused));
         int startCol = vtcursor.x;
         int startLine = vtcursor.y;
         inserttext(itext, vtcursor.x, vtcursor.y).posMove(vtcursor);
         recordAttrs(startLine, startCol, itext.length());
      }
      if (setxflag)
         setXmy(1, sb);
   }

   /**
    * Advances the cursor to the next line during text insertion.
    * In LNM mode, LF acts as CR+LF (move to column 0 and down).
    * When LNM is off, LF only moves down (column unchanged).
    * Handles scroll region boundaries (IND behavior at bottom margin).
    */
   private void advanceLine() {
      if (ecscreen.lnmMode)
         vtcursor.x = 0;
      if (rows > 0) {
         int bottomAbs = ecscreen.termRowToAbs(
            ecscreen.effectiveBottom());
         if (vtcursor.y >= bottomAbs) {
            ecscreen.scrollRegionUp(1);
            return;
         }
      }
      vtcursor.y++;
      if (vtcursor.y >= readIn())
         insertOne("", readIn());
   }

   /**
    * Inserts a text chunk (no newlines) at the cursor, splitting
    * at the column boundary when autowrap is active. Returns the
    * number of characters consumed.
    */
   private int insertChunk(String chunk) {
      int cols = ecscreen.getColumns();
      int consumed = 0;

      while (consumed < chunk.length()) {
         int available = cols - vtcursor.x;
         if (available <= 0) {
            if (autowrapMode) {
               ecscreen.doAutowrap();
               available = cols;
            } else {
               vtcursor.x = cols - 1;
               available = 1;
            }
         }

         // Scan original chars, tracking display width,
         // and build expanded string with wide-char padding.
         StringBuilder expanded = new StringBuilder();
         int origChars = 0;
         int dispWidth = 0;
         int pos = consumed;
         while (pos < chunk.length()) {
            int cp = chunk.codePointAt(pos);
            int cc = Character.charCount(cp);
            int w = Wcwidth.of(cp);
            if (w < 1)
               w = 1; // control chars treated as 1 column
            if (autowrapMode && dispWidth + w > available)
               break;
            expanded.appendCodePoint(cp);
            // Only pad BMP wide chars; supplementary pairs
            // already occupy 2 buffer positions = 2 columns.
            if (w == 2 && cc == 1)
               expanded.append(ECScreen.WIDE_PAD);
            dispWidth += w;
            origChars += cc;
            pos += cc;
         }
         if (origChars == 0) {
            // Wide char doesn't fit in available columns
            if (autowrapMode) {
               ecscreen.doAutowrap();
               continue;
            }
            break;
         }
         String toInsert = expanded.toString();

         String eline = fixline();
         int linelen = eline.length();
         if (vtcursor.x <= linelen) {
            int delval = toInsert.length() + vtcursor.x;
            if (delval > linelen)
               delval = linelen;
            deletetext(false, vtcursor.x, vtcursor.y,
               delval, vtcursor.y);
         }
         int startCol = vtcursor.x;
         inserttext(toInsert, vtcursor.x,
            vtcursor.y).posMove(vtcursor);
         recordAttrs(vtcursor.y, startCol,
            toInsert.length());
         consumed += origChars;

         if (autowrapMode && vtcursor.x >= cols) {
            vtcursor.x = cols - 1;
            if (consumed < chunk.length()) {
               ecscreen.doAutowrap();
            } else {
               ecscreen.pendingWrap = true;
            }
         }
      }
      return consumed;
   }

   public final String getnext() {
      return null;
   }

   /** {@inheritDoc}
    * Subclasses should call {@code super.disposeFvc()} to stop the
    * parser, then release their own resources (e.g. close a serial port).
    */
   public void disposeFvc() throws IOException {
      parser.stop();
      super.disposeFvc();
   }

   static final class Telnet extends Vt100 {

      private Process proc;
      private static final long serialVersionUID = 1;

      //String execstring = ("c:\\cygwin\\bin\\telnetcyg 127.0.0.1");

      //execstring ="ssh -t -t -e none 127.0.0.1";
      //execstring ="ssh -t -t  -v -e none localhost";
      //execstring = "c:\\windows\\system32\\telnet 127.0.0.1";
      //execstring = "telnet 9.22.73.31";
      //String execstring = "telnet localhost";

      //String execstring ="ssh speedy -t -t";
      //String execstring ="cmd /C c:/cygwin/bin/bash -i -l";
      //String execstring ="c:/cygwin/bin/printenv";
      //String execstring ="c:/windows/system32/cmd";
      //static String[] execstring ={"c:\\cygwin\\bin\\bash.exe","-c ","netstat 1"};

      static final String[] execstring1 = {"bash", "-i", ""};
      static final String[] execstring2 = {"ssh", "-t", "-t", ""};

      static Vt100 make(String host) throws IOException {

         String[] cmd = null == host
                        ? execstring1
                        : execstring2;

         if (null != host)
            cmd[3] = host;

         Vt100 vt = new Vt100.Telnet(cmd[0], Tools.iocmd(cmd));
         return  vt;
      }

      private Telnet(String execstringi, Process proci) throws
            java.io.UnsupportedEncodingException {
         super(proci.getOutputStream(),
               new BufferedInputStream(proci.getInputStream()),
               new StringIoc("vt100 start", null)
         );
         proc = proci;
      }

      public void disposeFvc() throws IOException {
         super.disposeFvc();
         if (null != proc) {
            proc.destroy();
            proc = null;
         }
      }
   }

}
