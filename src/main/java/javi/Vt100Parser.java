package javi;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import history.Tools;
import static history.Tools.trace;

/**
 * Parser for VT100/ANSI escape sequences.
 *
 * <p>Vt100Parser reads bytes from a terminal input stream and translates
 * VT100/ANSI escape sequences into calls on a {@link VScreen} interface.
 * Regular characters are buffered and passed through.</p>
 *
 * <h2>State Machine</h2>
 * <p>The parser operates as a state machine with states for:</p>
 * <ul>
 *   <li>NORM - Normal character processing</li>
 *   <li>ESC - Escape character received, awaiting command</li>
 *   <li>GETNUM - Processing CSI sequence with numeric parameters</li>
 *   <li>MODE - Processing private mode sequences</li>
 *   <li>OSCMODE - Processing Operating System Command sequences</li>
 * </ul>
 *
 * <h2>Supported Sequences</h2>
 * <table border="1">
 *   <tr><th>Sequence</th><th>Description</th></tr>
 *   <tr><td>ESC[nA</td><td>Cursor up n rows</td></tr>
 *   <tr><td>ESC[nB</td><td>Cursor down n rows</td></tr>
 *   <tr><td>ESC[nC</td><td>Cursor right n columns</td></tr>
 *   <tr><td>ESC[nD</td><td>Cursor left n columns</td></tr>
 *   <tr><td>ESC[n;mH</td><td>Cursor position (row;column)</td></tr>
 *   <tr><td>ESC[nJ</td><td>Erase display (0=to end, 1=to start, 2=all)</td></tr>
 *   <tr><td>ESC[nK</td><td>Erase line (0=to end, 1=to start, 2=all)</td></tr>
 *   <tr><td>ESC[nL</td><td>Insert n lines</td></tr>
 *   <tr><td>ESC[nM</td><td>Delete n lines</td></tr>
 *   <tr><td>ESC[nP</td><td>Delete n characters</td></tr>
 *   <tr><td>ESC[nS</td><td>Scroll up n lines</td></tr>
 *   <tr><td>ESC[nT</td><td>Scroll down n lines</td></tr>
 *   <tr><td>ESC[n@</td><td>Insert n blank characters</td></tr>
 *   <tr><td>ESC[nm</td><td>Set graphic rendition (colors/attributes)</td></tr>
 *   <tr><td>ESC 7</td><td>Save cursor position</td></tr>
 *   <tr><td>ESC 8</td><td>Restore cursor position</td></tr>
 *   <tr><td>ESC M</td><td>Reverse index (move cursor up)</td></tr>
 * </table>
 *
 * @see VScreen
 * @see Vt100
 */
final class Vt100Parser extends EventQueue.IEvent implements Runnable {

   /** Current parser state. */
   private int state = NORM;

   /** State: Normal character processing. */
   private static final int NORM = 0;

   /** State: Escape character received. */
   private static final int ESC = 1;

   /** State: Processing CSI sequence with numeric parameters. */
   private static final int GETNUM = 2;

   /** State: Processing private mode sequences. */
   private static final int MODE = 3;

   /** State: Processing OSC sequence start. */
   private static final int OSCMODE = 5;

   /** State: Processing OSC sequence separator. */
   private static final int OSCMODE2 = 6;

   /** State: Processing OSC sequence content. */
   private static final int OSCMODE3 = 7;

   /** State: Discarding unrecognized CSI sequence (e.g., DA2, DA3). */
   private static final int DISCARD = 8;

   /** State: Designate character set (ESC ( or ESC ) + final byte). */
   private static final int CHARSET_DESIGNATE = 10;

   /** State: DEC line attribute (ESC # + final byte). */
   private static final int DEC_LINE_ATTR = 11;

   /** State: Waiting for DECSTR final byte 'p' after CSI !. */
   private static final int DISCARD_DECSTR = 12;

   /** State: Processing CSI &gt; sequence (Secondary DA). */
   private static final int CSI_GT = 13;

   /** State: Discarding a string sequence (APC, PM, SOS). */
   private static final int STRING_DISCARD = 14;

   /** State: CSI params collected, awaiting final byte after '*'. */
   private static final int CSI_STAR = 15;

   /** State: Accumulating DCS content. */
   private static final int DCS_CONTENT = 16;

   /** State: Carriage return received. */
   private static final int CR = 9;

   /** The virtual screen to update. */
   private final VScreen window;

   /** Buffer for accumulating normal characters. */
   private final StringBuilder sb = new StringBuilder(200);

   /** Array for accumulating numeric parameters in CSI sequences. */
   private int[] numacc = null;

   /** Current index into numacc array. */
   private int currnumacc;

   /** Highest numacc index that was explicitly set. */
   private int highestSet;

   /** Mode numbers for private mode sequences (handles ; separators). */
   private int[] modeNumbers = new int[8];

   /** Count of accumulated mode numbers (0-based index). */
   private int modeCount;

   /** OSC command number (supports multi-digit: 4, 10, 104, etc.). */
   private int oscnum;

   /** Buffer for OSC string content (payload after first ';'). */
   private final StringBuilder oscstring = new StringBuilder(80);

   /** Buffer for DCS content accumulation. */
   private final StringBuilder dcsContent = new StringBuilder(80);

   /**
    * Which character set (G0 or G1) is being designated.
    * 0 = G0, 1 = G1, -1 = ignore (e.g. ESC SP).
    */
   private int charsetTarget = -1;

   /** True if G0 is DEC Special Graphics (line drawing). */
   private boolean g0DecGraphics;

   /** True if G1 is DEC Special Graphics (line drawing). */
   private boolean g1DecGraphics;

   /** True when G1 is the active character set (SO). */
   private boolean useG1;

   /** Last graphic character output (for REP, CSI b). */
   private char lastGraphicChar;

   /**
    * DEC Special Graphics character map for ASCII 0x60-0x7E.
    * Maps line-drawing mnemonics to Unicode box-drawing glyphs.
    */
   static final char[] DEC_GRAPHICS_MAP = {
      '\u25C6', // 0x60 ` → Diamond
      '\u2592', // 0x61 a → Checkerboard
      '\u2409', // 0x62 b → HT symbol
      '\u240C', // 0x63 c → FF symbol
      '\u240D', // 0x64 d → CR symbol
      '\u240A', // 0x65 e → LF symbol
      '\u00B0', // 0x66 f → Degree sign
      '\u00B1', // 0x67 g → Plus/minus
      '\u2424', // 0x68 h → NL symbol
      '\u240B', // 0x69 i → VT symbol
      '\u2518', // 0x6A j → Bottom-right corner
      '\u2510', // 0x6B k → Top-right corner
      '\u250C', // 0x6C l → Top-left corner
      '\u2514', // 0x6D m → Bottom-left corner
      '\u253C', // 0x6E n → Crossing lines
      '\u23BA', // 0x6F o → Scan line 1
      '\u23BB', // 0x70 p → Scan line 3
      '\u2500', // 0x71 q → Horizontal line
      '\u23BC', // 0x72 r → Scan line 7
      '\u23BD', // 0x73 s → Scan line 9
      '\u251C', // 0x74 t → Left T-piece
      '\u2524', // 0x75 u → Right T-piece
      '\u2534', // 0x76 v → Bottom T-piece
      '\u252C', // 0x77 w → Top T-piece
      '\u2502', // 0x78 x → Vertical line
      '\u2264', // 0x79 y → Less-than-or-equal
      '\u2265', // 0x7A z → Greater-than-or-equal
      '\u03C0', // 0x7B { → Pi
      '\u2260', // 0x7C | → Not equal
      '\u00A3', // 0x7D } → Pound sterling
      '\u00B7', // 0x7E ~ → Middle dot
   };

   /** Terminal I/O logger (shared with Vt100). */
   TermLog termLog;

   /** Input stream from terminal process. */
   private final BufferedInputStream input;

   /** Reader that decodes the input stream using the terminal charset. */
   private final InputStreamReader reader;

   /** Most recently read character. */
   private char recbyte;

   /** Parser thread. */
   private Thread rthread = new Thread(this, "vt100 parser thread");

   /**
    * Creates a new VT100 parser.
    *
    * @param win the VScreen to update
    * @param ins the input stream to read from
    * @throws NullPointerException if ins is null
    */
   Vt100Parser(VScreen win, BufferedInputStream ins) {
      this(win, ins, Charset.defaultCharset());
   }

   Vt100Parser(VScreen win, BufferedInputStream ins, Charset charset) {
      if (null == ins) throw new NullPointerException("invalid initialisation");
      input = ins;
      reader = new InputStreamReader(ins, charset);
      window = win;
      rthread.start();
   }

   /**
    * Stops the parser thread.
    */
   void stop() {
      rthread.interrupt();
   }

   /**
    * Main parser loop - reads bytes and dispatches to event queue.
    */
   public void run() {
      try {
         while (true) {
            synchronized (this) {
               int rec = reader.read();
               //trace("rec = " + (int)rec);

               if (rec == -1)  {
                  //trace("recevied EOF exiting input loop");
                  //return;
                  Thread.sleep(200);
               } else {
                  //trace("rec " + rec);
                  recbyte = (char) rec;
                  //trace("insert wakeup recbyte " + (int)recbyte);
                  EventQueue.insert(this);
                  wait(10000);
               }
            }
         }
      } catch (InterruptedException e) {
         trace("ignoring InterruptedException");
      } catch (Throwable e) {
         UI.popError("Vt100 caught ", e);
      }
   }

   private void caseCR(char inc) {
      sb.setLength(sb.length() - 1);
      state = NORM;
      if ('\n' == inc) {
         window.setX(1, sb);  // execute CR: flush text, move to col 0
         sb.append('\n');      // queue LF for later advanceLine
         return;
      }
      sb.append(inc);
      window.setX(1, sb);
      caseNORM(inc);
   }

   private void caseNORM(char inc) {
      switch (inc) {
         case  27:
            state = ESC;
            break;
         case 0: //ignored
         case '\177': //ignored
            break;
         case 7:
            // BEL character - ring the bell
            trace1("bell character");
            window.bell();
            break;
         case '\b': //??? sbprocess
            window.incX(-1, sb);
            break;
         case '\r': // may want to just move to beginning of line - vt100 spec

            //trace("!!!! flush");
            //sbprocess();
            //window.setX(1);
            sb.append(inc);
            state = CR;
            break;
         case 14: // SO - Shift Out (select G1 character set)
            trace1("receive SO character select G1 character set");
            useG1 = true;
            break;
         case 15: // SI - Shift In (select G0 character set)
            trace1("receive SI character select G0 character set");
            useG1 = false;
            break;
         case 9: // HT - Horizontal Tab
            window.handleTab(sb);
            break;
         case 12: // FF — behaves as LF per VT100 spec
         case 11: // VT — behaves as LF per VT100 spec
         case 10:
            sb.append('\n');
            break;
         case (char) 0xffff:
            trace("received -1");
            Thread.dumpStack();
            break;
         default:
            if (inc < 20)
               trace1("unhandeld control character 0x"
                  + Integer.toHexString(inc));
            char translated = translateCharset(inc);
            lastGraphicChar = translated;
            sb.append(translated);
      }
   }

   /**
    * Handles C0 control characters encountered during CSI sequence
    * parsing. Per VT100 spec, these are executed immediately
    * without terminating the in-progress escape sequence.
    */
   private void handleC0Control(int inc) {
      switch (inc) {
         case 8: // BS — move cursor left
            window.incX(-1, sb);
            break;
         case 13: // CR — move cursor to column 1
            window.setX(1, sb);
            break;
         case 10: // LF
         case 11: // VT
         case 12: // FF — move cursor down
            window.incY(1, sb);
            break;
         case 9: // HT — horizontal tab
            window.handleTab(sb);
            break;
         case 7: // BEL
            window.bell();
            break;
         default:
            trace("C0 control 0x" + Integer.toHexString(inc)
               + " inside CSI — parsed, not used");
            break;
      }
   }

   private void caseGETNUM(int inc) {
      if (inc >= '0' && inc <= '9') {
         numacc[currnumacc] = numacc[currnumacc] * 10 + inc - '0';
         highestSet = currnumacc;
      } else if (inc >= 0x20 && inc <= 0x2F && inc != '!'
            && inc != '*') {
         // Intermediate bytes (ECMA-48): space, ", #, $, etc.
         // Precede final byte in ESC[0 q (DECSCUSR), ESC["p (DECSCL).
         // '!' excluded — has its own handler for DECSTR (CSI ! p).
         // '*' excluded — has its own handler for DECRQCRA (CSI * y).
         trace("CSI intermediate '" + (char) inc
            + "' — discarding sequence");
         state = DISCARD;
      } else if (inc < 0x20 && inc != 27) {
         // C0 control characters inside CSI sequences are
         // executed immediately without terminating the sequence.
         // VT100 spec: BS, CR, LF, VT, FF, HT, BEL are
         // processed, then CSI parsing continues.
         handleC0Control(inc);
      } else {
         int newstate = NORM;
         boolean def = highestSet != currnumacc;
         switch (inc) {
            case 'A':
               trace1("cursor up " + numacc[currnumacc]);
               warnUnusedParams('A', 1);
               window.incY(-1 * Math.max(1,
                  def ? 1 : numacc[currnumacc]), sb);
               break;
            case 'B':
               trace1("cursor down " + numacc[currnumacc]);
               warnUnusedParams('B', 1);
               window.incY(Math.max(1,
                  def ? 1 : numacc[currnumacc]), sb);
               break;
            case 'C':
               trace1("cursor right " + numacc[currnumacc]);
               warnUnusedParams('C', 1);
               window.incX(Math.max(1,
                  def ? 1 : numacc[currnumacc]), sb);
               break;
            case 'D':
               trace1("cursor left " + numacc[currnumacc]);
               warnUnusedParams('D', 1);
               window.incX(-1 * Math.max(1,
                  def ? 1 : numacc[currnumacc]), sb);
               break;
            case 'H':
               handleCursorPosition(def);
               break;
            case 'J':
               handleEraseScreen();
               break;
            case 'K':
               handleEraseLine();
               break;
            case 'P': // Delete Character (DCH) - ESC[nP
               warnUnusedParams('P', 1);
               window.deleteChars(def ? 1 : numacc[currnumacc], sb);
               break;
            case 'm': // Character Attributes (SGR - Select Graphic Rendition)
               // Pass parameters to window for potential color/attribute handling
               trace1("[m (graphic Rendition)");
               window.setGraphicRendition(numacc, sb);
               break;
            case ':': // Colon subparameter separator (ECMA-48)
            case ';':
               if (currnumacc <= numacc.length - 1) {
                  int[] temp = new int[numacc.length + 1];
                  System.arraycopy(numacc, 0, temp, 0, numacc.length);
                  numacc = temp;
               }
               //trace("accumulate new number " + numacc[currnumacc]);
               currnumacc++;
               numacc[currnumacc] = 0;
               newstate = state;
               break;

            case '?':
               newstate = MODE;
               modeNumbers[0] = 0;
               modeCount = 0;
               break;
            case '>':
               // CSI > ... c = Secondary DA; collect in CSI_GT
               newstate = CSI_GT;
               break;
            case '=':
               trace("CSI intermediate '=' — discarding sequence");
               newstate = DISCARD;
               break;
            case '!':
               // CSI ! p = DECSTR (soft reset); consume '!'
               // and wait for final byte via DISCARD_DECSTR
               newstate = DISCARD_DECSTR;
               break;
            case '*':
               // CSI params * y = DECRQCRA; consume '*'
               // and wait for final byte via CSI_STAR
               newstate = CSI_STAR;
               break;
            case 27:
               newstate = ESC;
               break;
            case '@':
               warnUnusedParams('@', 1);
               window.insertChars(def ? 1 : numacc[currnumacc],
                  sb);
               break;
            case 'h': //set mode xterm
            case 'l': //reset mode xterm
               handleModeSet('h' == inc);
               break;

            case 'r': // Set Top and Bottom Margins (DECSTBM)
               if (currnumacc == 0 && def) {
                  // CSI r with no params — reset margins
                  window.setScrollRegion(0, 0, sb);
               } else if (currnumacc >= 1) {
                  window.setScrollRegion(
                     numacc[0], numacc[1], sb);
               } else {
                  window.setScrollRegion(
                     numacc[0], 0, sb);
               }
               break;
            case 'L': // insert lines before or after???
               warnUnusedParams('L', 1);
               window.insertLines(0 == numacc[currnumacc]
                  ? 1
                  : numacc[currnumacc], sb);
               break;
            case 'M': // Delete lines (ESC[nM)
               trace1("delete " + (def ? 1 : numacc[currnumacc]) + " lines");
               warnUnusedParams('M', 1);
               window.deleteLines(def ? 1 : numacc[currnumacc], sb);
               break;
            case 'S': // Scroll up (ESC[nS)
               trace1("scroll up " + (def ? 1 : numacc[currnumacc]) + " lines");
               warnUnusedParams('S', 1);
               window.scrollUp(def ? 1 : numacc[currnumacc], sb);
               break;
            case 'T': // Scroll down (ESC[nT)
               trace1("scroll down " + (def ? 1 : numacc[currnumacc]) + " lines");
               warnUnusedParams('T', 1);
               window.scrollDown(def ? 1 : numacc[currnumacc], sb);
               break;
            case 'f': // Horizontal and Vertical Position (same as H)
               handleCursorPosition(def);
               break;
            default:
               newstate = handleCsiPosition(inc, def, newstate);
         }
         //trace("completed [" + (char) inc);
         state = newstate;
      }
   }

   private int handleCsiPosition(int inc, boolean def, int defState) {
      switch (inc) {
         case 'G': // Cursor Character Absolute (ESC[nG)
            trace1("cursor to column "
               + (def ? 1 : numacc[currnumacc]));
            warnUnusedParams('G', 1);
            window.setX(def ? 1 : numacc[currnumacc], sb);
            break;
         case '`': // Character Position Absolute (HPA)
            trace1("HPA to column "
               + (def ? 1 : numacc[currnumacc]));
            warnUnusedParams('`', 1);
            window.setX(def ? 1 : numacc[currnumacc], sb);
            break;
         case 'I': // Cursor Forward Tabulation (CHT)
            trace1("forward tab "
               + (def ? 1 : numacc[currnumacc]));
            warnUnusedParams('I', 1);
            for (int ti = 0;
                  ti < (def ? 1 : numacc[currnumacc]); ti++)
               window.handleTab(sb);
            break;
         case 'g': // Tabulation Clear (TBC)
            warnUnusedParams('g', 1);
            window.clearTabStop(
               def ? 0 : numacc[currnumacc], sb);
            break;
         case 'd': // Line Position Absolute (ESC[nd)
            trace1("cursor to row "
               + (def ? 1 : numacc[currnumacc]));
            warnUnusedParams('d', 1);
            window.setY(def ? 1 : numacc[currnumacc], sb);
            break;
         case 'E': // Cursor Next Line (ESC[nE)
            trace1("cursor next line "
               + (def ? 1 : numacc[currnumacc]));
            window.incY(def ? 1 : numacc[currnumacc], sb);
            window.setX(1, sb);
            break;
         case 'F': // Cursor Previous Line (ESC[nF)
            trace1("cursor prev line "
               + (def ? 1 : numacc[currnumacc]));
            window.incY(
               -(def ? 1 : numacc[currnumacc]), sb);
            window.setX(1, sb);
            break;
         case 'X': // Erase Character (ESC[nX)
            trace1("erase "
               + (def ? 1 : numacc[currnumacc])
               + " characters");
            warnUnusedParams('X', 1);
            window.eraseChars(
               def ? 1 : numacc[currnumacc], sb);
            break;
         case 'Z': // Cursor Backward Tabulation (CSI nZ)
            trace1("backward tab "
               + (def ? 1 : numacc[currnumacc]));
            warnUnusedParams('Z', 1);
            window.backwardTab(
               def ? 1 : numacc[currnumacc], sb);
            break;
         case 'a': // Character Position Relative (HPR)
            trace1("HPR right "
               + (def ? 1 : numacc[currnumacc]));
            warnUnusedParams('a', 1);
            window.incX(def ? 1 : numacc[currnumacc], sb);
            break;
         case 'e': // Line Position Relative (VPR)
            trace1("VPR down "
               + (def ? 1 : numacc[currnumacc]));
            warnUnusedParams('e', 1);
            window.incY(def ? 1 : numacc[currnumacc], sb);
            break;
         case 'b': // Repeat graphic character (CSI nb)
            trace1("repeat char "
               + (def ? 1 : numacc[currnumacc]));
            warnUnusedParams('b', 1);
            window.repeatChar(lastGraphicChar,
               def ? 1 : numacc[currnumacc], sb);
            break;
         case 't': // Window operations (XTWINOPS)
            window.handleWindowOp(
               def ? 0 : numacc[0],
               currnumacc >= 1 ? numacc[1] : 0,
               currnumacc >= 2 ? numacc[2] : 0, sb);
            break;
         default:
            return handleCsiMisc(inc, def, defState);
      }
      return defState;
   }

   private int handleCsiMisc(int inc, boolean def, int defState) {
      switch (inc) {
         case 'n': // Device Status Report
            if (!def && numacc[currnumacc] == 6) {
               trace1("cursor position report (responding)");
               window.respondCursorPosition(sb);
            } else if (!def && numacc[currnumacc] == 5) {
               trace1("status report (responding OK)");
               window.respondStatusOk(sb);
            } else {
               trace1("device status report "
                  + numacc[currnumacc] + " (ignored)");
            }
            break;
         case 'c': // Device Attributes
            trace1("device attributes request (responding)");
            window.respondDeviceAttributes(sb);
            break;
         case 's': // Save Cursor Position (ANSI.SYS / SCO)
            trace1("save cursor (ANSI.SYS)");
            window.saveCursor(sb);
            break;
         case 'u': // Restore Cursor Position (ANSI.SYS / SCO)
            trace1("restore cursor (ANSI.SYS)");
            window.restoreCursor(sb);
            break;
         default:
            trace("unkown [ terminator " + (char) inc
               + " decimal "  + inc
               + " 0x" + Integer.toHexString(inc));
      }
      return defState;
   }

   private void handleCursorPosition(boolean def) {
      switch (currnumacc + (def ? 0 : 1)) {
         case 0:
            trace1("move to home");
            window.setXY(1, 1, sb);
            break;
         case 1:
            trace1("move to row " + numacc[0]);
            window.setXY(1, numacc[0], sb);
            break;
         case 2:
            trace1("move XY(" + numacc[1] + "," + numacc[0] + ")");
            window.setXY(numacc[1], numacc[0], sb);
            break;
         default:
            trace("bad number accumulated: " + currnumacc);
      }
   }

   private void handleEraseScreen() {
      warnUnusedParams('J', 1);
      switch (numacc[currnumacc]) {
         case 0:
            trace1("erase from cursor to end of screen");
            window.eraseScreenToEnd(sb);
            break;
         case 1:
            trace1("erase from start of screen to cursor");
            window.eraseScreenToBeginning(sb);
            break;
         case 2:
            window.eraseScreen(sb);
            trace1("erase entire screen");
            break;
         case 3:
            // ESC[3J - erase scrollback buffer (xterm extension)
            trace1("erase scrollback");
            window.eraseScrollback(sb);
            break;
         default:
            trace1("unknown [J index " + numacc[currnumacc]);
      }
   }

   private void handleEraseLine() {
      warnUnusedParams('K', 1);
      switch (numacc[currnumacc]) {
         case 0:
            trace1("erase from cursor to end of line");
            window.eraseToEnd(sb);
            break;
         case 1:
            trace1("erase from beginning of line to cursor");
            window.eraseToBeginning(sb);
            break;
         case 2:
            trace1("erase entire line at cursor");
            window.eraseLine(sb);
            break;
         default:
            trace1("unexpected erase character");
      }
   }

   private void handleModeSet(boolean val) {
      for (int ii = 0; ii <= currnumacc; ii++)
         switch (numacc[ii]) {
            case 4:
               window.setInsertMode(val, sb);
               break;
            case 20: // LNM — Line Feed/New Line mode
               window.setLnmMode(val);
               break;
            default:
               trace("unknown mode " + numacc[ii]);
         }
   }

   /** State: intermediate '$' seen in MODE; waiting for final byte. */
   private static final int MODE_DOLLAR = 17;

   private void caseMODE(int inc) {
      if (inc >= '0' && inc <= '9')
         modeNumbers[modeCount] = modeNumbers[modeCount] * 10 + inc - '0';
      else if (';' == inc) {
         // Multiple mode numbers separated by ';' (e.g., ESC[?1000;1006h)
         modeCount++;
         if (modeCount < modeNumbers.length)
            modeNumbers[modeCount] = 0;
      } else if ('$' == inc) {
         // Intermediate '$': CSI ? Ps $ p = DECRQM (request mode)
         state = MODE_DOLLAR;
      } else if ('n' == inc) {
         // DECDSR — DEC-private Device Status Report
         window.respondDecdsr(modeNumbers[0], sb);
         state = NORM;
      } else if ('s' == inc) {
         // XTERM_SAVE — save DEC private mode values
         for (int i = 0; i <= modeCount && i < modeNumbers.length; i++)
            window.saveMode(modeNumbers[i]);
         state = NORM;
      } else if ('r' == inc) {
         // XTERM_RESTORE — restore DEC private mode values
         for (int i = 0; i <= modeCount && i < modeNumbers.length; i++)
            window.restoreMode(modeNumbers[i], sb);
         state = NORM;
      } else {
         boolean enable = 'h' == inc;
         for (int i = 0; i <= modeCount && i < modeNumbers.length; i++)
            applyMode(modeNumbers[i], enable);
         state = NORM;
      }
   }

   /**
    * Handles the final byte after CSI ? Ps $ in MODE_DOLLAR state.
    * CSI ? Ps $ p = DECRQM (DEC Private Mode Report request).
    */
   private void caseModeDollar(int inc) {
      if ('p' == inc) {
         // DECRQM — respond with DECRPM: CSI ? Ps; Pm $ y
         // Pm: 0=not recognized, 1=set, 2=reset,
         //     3=permanently set, 4=permanently reset
         window.respondDecrqm(modeNumbers[0], sb);
      } else {
         trace("CSI ? ... $ " + (char) inc
            + " — parsed, not used");
      }
      state = NORM;
   }

   /** Discard characters until a CSI final byte (0x40-0x7E) ends the sequence. */
   private void caseDiscard(int inc) {
      if (inc >= 0x40 && inc <= 0x7E) {
         trace("discarded CSI sequence final '" + (char) inc
            + "' — parsed, not used");
         state = NORM;
      }
      // else: intermediate bytes/parameters — keep discarding
   }

   private void applyMode(int modeNum, boolean enable) {
      if (termLog != null && termLog.isEnabled())
         termLog.logMode(modeNum, enable);
      switch (modeNum) {
         case 1: // Application cursor keys (DECCKM)
            window.setApplicationCursorKeys(enable);
            break;
         case 2:
            trace("vt52 mode shouldn't happen");
            break;
         case 3: // DECCOLM — 80/132 column mode
            window.setColumnMode(enable ? 132 : 80, sb);
            break;
         case 4:
            trace("DEC mode 4: smooth scrolling "
               + (enable ? "on" : "off") + " — parsed, not used");
            break;
         case 5: // DECSCNM — Reverse video (swap fg/bg globally)
            // Accepted but not implemented — would require
            // inverting all screen colors on every repaint.
            trace("DEC mode 5: reverse video "
               + (enable ? "on" : "off")
               + " — parsed, not implemented");
            break;
         case 6: // DECOM: Origin mode
            window.setOriginMode(enable);
            break;
         case 40: // Allow 80/132 column switching (xterm)
            window.setAllow80To132(enable);
            break;
         case 47: // Use Alternate Screen Buffer
            window.switchAlternateScreen(enable, sb);
            break;
         case 1047: // Use Alternate Screen Buffer
            // Per xterm: DECRST clears screen first if in alt
            if (!enable)
               window.eraseScreen(sb);
            window.switchAlternateScreen(enable, sb);
            break;
         case 1049: // Save cursor + alt screen + clear
            // Per xterm: DECSET = DECSC + switch to alt + clear
            //            DECRST = switch to normal + DECRC
            if (enable) {
               window.saveCursor(sb);
               window.switchAlternateScreen(true, sb);
            } else {
               window.switchAlternateScreen(false, sb);
               window.restoreCursor(sb);
            }
            break;
         case 1048: // xterm: save/restore cursor (like DECSC/DECRC)
            if (enable)
               window.saveCursor(sb);
            else
               window.restoreCursor(sb);
            break;
         case 25: // DECTCEM: show/hide cursor
            window.setCursorVisible(enable);
            break;

         case 1000: // Normal mouse tracking
         case 1002: // Button-event mouse tracking
         case 1003: // Any-event mouse tracking
            window.setMouseTracking(modeNum, enable);
            break;
         case 9: // X10 mouse tracking (legacy)
            // Accepted but treated as normal tracking
            window.setMouseTracking(1000, enable);
            break;
         case 1005: // UTF-8 extended mouse mode
         case 1015: // urxvt extended mouse mode
            // Accepted but not implemented — we use SGR (1006)
            trace("DEC mode " + modeNum + ": mouse encoding "
               + (enable ? "on" : "off") + " — parsed, not used");
            break;
         case 1006: // SGR extended mouse mode
            window.setSgrMouseMode(enable);
            break;
         case 7: // Autowrap mode
            window.setAutowrapMode(enable);
            break;
         case 45: // Reverse-wraparound inline mode (xterm >= 383)
            window.setReverseWrapMode(enable);
            break;
         case 1045: // Reverse-wraparound extend mode (xterm >= 383)
            window.setReverseWrapExtendMode(enable);
            break;
         case 12: // Cursor blink
            window.setCursorBlinkMode(enable);
            break;
         case 1004: // Focus event reporting
            window.setFocusEventsMode(enable);
            break;
         case 2004: // Bracketed paste mode
            window.setBracketedPasteMode(enable);
            break;

         default:
            trace("setting unknown mode " + modeNum
               + " 0x" + Integer.toHexString(modeNum)
               + " to " + (enable ? "on" : "off"));
      }
   }

   private void caseOSCMODE3(int inc) {
      if (7 == inc || 0x9c == inc) { // BEL or ST terminates OSC
         state = NORM;
         String payload = oscstring.toString();
         oscstring.setLength(0);
         dispatchOsc(oscnum, payload);
      } else if ('\\' == inc && oscstring.length() > 0
            && oscstring.charAt(oscstring.length() - 1) == 27) {
         // ESC \ also terminates OSC (String Terminator)
         oscstring.setLength(oscstring.length() - 1);
         caseOSCMODE3(7); // Process as if BEL was received
      } else {
         oscstring.append((char) inc);
      }
   }

   /**
    * Dispatches a completed OSC sequence to the appropriate handler.
    *
    * @param oscNum the OSC number (0, 1, 2, 4, 10, 11, 12, etc.)
    * @param payload the content after the first semicolon
    */
   private void dispatchOsc(int oscNum, String payload) {
      switch (oscNum) {
         case 0: // Set icon name and window title
            trace1("change icon and title: " + payload);
            window.setTitle(payload);
            window.setIconTitle(payload);
            break;
         case 1: // Set icon name
            trace1("change icon name: " + payload);
            window.setIconTitle(payload);
            break;
         case 2: // Set window title
            trace1("change window title: " + payload);
            window.setTitle(payload);
            break;
         case 4: // Set/query color palette
            window.handleOscColor(4, payload);
            break;
         case 5: // Set/query special color (alternative numbering)
            window.handleOscColor(5, payload);
            break;
         case 7: // Set working directory (iTerm2, etc.)
            trace("OSC 7: working directory '" + payload
               + "' — parsed, not used");
            break;
         case 10: // foreground color
         case 11: // background color
         case 12: // cursor color
            window.handleOscColor(oscNum, payload);
            break;
         case 17: // highlight foreground (special color)
         case 19: // highlight background (special color)
            window.handleOscColor(oscNum, payload);
            break;
         case 104: // Reset palette color(s)
         case 105: // Reset special color(s)
         case 110: // Reset foreground
         case 111: // Reset background
         case 112: // Reset cursor color
         case 117: // Reset highlight foreground
         case 119: // Reset highlight background
            window.handleOscColor(oscNum, payload);
            break;
         default:
            trace1("unexpected OSC " + oscNum + ": " + payload);
      }
   }

   /**
    * Dispatches a completed DCS sequence.
    *
    * <p>Handles DCS +q (Request Termcap/Terminfo String).
    * The "Co" capability returns the number of indexed colors.</p>
    */
   private void dispatchDcs(String content) {
      if (content.startsWith("+q")) {
         String hexName = content.substring(2);
         window.respondTerminfoQuery(hexName);
      } else {
         trace("DCS content: " + content + " — parsed, not used");
      }
   }

   private void caseESC(int inc) {

      switch (inc) {
         case '[': //91
            state = GETNUM;
            numacc = new int[1];
            numacc[0] = 0;
            currnumacc = 0;
            highestSet = -1;
            break;
         case '7': // save cursor and attributes
            window.saveCursor(sb);
            state = NORM;
            break;
         case '8': // restore cursor and attributes
            window.restoreCursor(sb);
            state = NORM;
            break;
         case 'D': // IND — Index (move cursor down, scroll at bottom)
            window.index(sb);
            state = NORM;
            break;
         case 'E': // NEL — Next Line (CR + LF, scroll at bottom)
            window.nextLine(sb);
            state = NORM;
            break;
         case 'H': // HTS — Horizontal Tab Set
            window.setTabStop(sb);
            state = NORM;
            break;
         case 'M': // RI — Reverse Index
            window.reverseIndex(sb);
            state = NORM;
            break;
         case 'c': // RIS — Reset to Initial State (hard reset)
            window.hardReset(sb);
            state = NORM;
            break;
         case 'Z': // DECID — Identify terminal (same as DA1)
            window.respondDeviceAttributes(sb);
            state = NORM;
            break;

         case '#': // ESC # n — DEC line attributes
            // Next byte determines the operation
            state = DEC_LINE_ATTR;
            break;
         case '(':
            // ESC ( X = Designate G0 charset
            charsetTarget = 0;
            state = CHARSET_DESIGNATE;
            break;
         case ')':
            // ESC ) X = Designate G1 charset
            charsetTarget = 1;
            state = CHARSET_DESIGNATE;
            break;
         case ' ':
            // ESC SP F or ESC SP G — consume next byte
            charsetTarget = -1;
            state = CHARSET_DESIGNATE;
            break;

         case 27:
            trace("what does double escape mean?");
            // I think it means stay in escape
            break;
         case '>':
            trace("DECKPNM: keypad numeric mode — parsed, not used");
            state = NORM;
            break;
         case '=':
            trace("DECKPAM: keypad application mode"
               + " — parsed, not used");
            state = NORM;
            break;
         case ']':
            state = OSCMODE;
            break;
         case 'P': // DCS — Device Control String
            dcsContent.setLength(0);
            state = DCS_CONTENT;
            break;
         case '_': // APC — Application Program Command
         case '^': // PM — Privacy Message
         case 'X': // SOS — Start of String
            // String sequences terminated by ST (ESC \ or 0x9C)
            trace("ESC " + (char) inc
               + " string sequence — discarding content");
            state = STRING_DISCARD;
            break;
         case '\\': // ST — String Terminator (ESC \)
            // Terminates DCS/APC/PM/SOS/OSC sequences.
            // If we arrive here outside a string sequence, it's
            // harmless — just return to NORM.
            state = NORM;
            break;
         default:
            trace("unhandled escape code " + (char) inc + " decimal "  + inc
               + " 0x" + Integer.toHexString(inc));
            state = NORM;
      }
   }

   private void caseOSCMODE2(int inc) {
      // Multi-digit OSC number accumulation continues
      if (inc >= '0' && inc <= '9') {
         oscnum = oscnum * 10 + (inc - '0');
         // stay in OSCMODE2
      } else if (';' == inc) {
         state = OSCMODE3;
      } else if (7 == inc || 0x9c == inc) {
         // OSC with no payload (e.g., OSC 104 BEL)
         state = NORM;
         oscstring.setLength(0);
         dispatchOsc(oscnum, "");
      } else if ('\\' == inc) {
         // ESC \ terminator — check if previous was ESC
         state = NORM;
         oscstring.setLength(0);
         dispatchOsc(oscnum, "");
      } else if (27 == inc) {
         // ESC — might be start of ESC \ (String Terminator).
         // Buffer it in oscstring so caseOSCMODE3 can match ESC \.
         oscstring.setLength(0);
         oscstring.append((char) 27);
         state = OSCMODE3;
      } else {
         trace("unexpected OSCMODE2 character: " + (char) inc);
         state = OSCMODE3;
      }
   }

   private void caseOSCMODE(int inc) {
      if (inc >= '0' && inc <= '9') {
         oscnum = inc - '0';
         state = OSCMODE2;
      } else if (7 == inc || 0x9c == inc) {
         // Bare OSC with no number
         state = NORM;
      } else {
         trace("unexpected first OSC char: " + (char) inc);
         state = NORM;
      }
   }

   private void doChar(char inc) {
      //trace("state " + state + " received byte " + (char)inc + " decimal "  + inc+ " 0x" + Integer.toHexString(inc));
      if (inc == -1) {
         trace("received -1 on Vt100");
         return;
      }
      if (termLog != null && termLog.isEnabled())
         termLog.logRecv(inc);
      // CAN (0x18) and SUB (0x1A) abort any in-progress escape
      // sequence and return to ground state (ECMA-48 §5.2).
      if (state != NORM && (0x18 == inc || 0x1A == inc)) {
         trace1("CAN/SUB abort state " + state);
         if (termLog != null && termLog.isEnabled())
            termLog.logState(state, NORM, inc);
         state = NORM;
         return;
      }
      int prevState = state;
      switch (state)  {
            case CR:
               caseCR(inc);
               break;
            case NORM:
               caseNORM(inc);
               break;
            case GETNUM:
               caseGETNUM(inc);
               break;
            case MODE:
               caseMODE(inc);
               break;
            case MODE_DOLLAR:
               caseModeDollar(inc);
               break;
            case DISCARD:
               caseDiscard(inc);
               break;
            case CSI_GT:
               // CSI > ... <final> — consume parameters/intermediates
               // until a final byte (0x40-0x7E).
               // Known sequences:
               //   CSI > c       Secondary Device Attributes
               //   CSI > Ps ; Pv m  modifyOtherKeys (xterm)
               if (inc >= 0x40 && inc <= 0x7E) {
                  if ('c' == inc)
                     window.respondSecondaryDA(sb);
                  else
                     trace("CSI > ... " + (char) inc
                        + " — parsed, not used");
                  state = NORM;
               }
               // else: parameter bytes (digits, ';') — keep consuming
               break;
            case DISCARD_DECSTR:
               // CSI ! p = soft reset (DECSTR)
               if ('p' == inc)
                  window.softReset(sb);
               else
                  trace("CSI ! " + (char) inc
                     + " — parsed, not used");
               state = NORM;
               break;
            case CSI_STAR:
               // CSI Pid;Pp;Pt;Pl;Pb;Pr * y = DECRQCRA
               if ('y' == inc)
                  window.respondRectChecksum(numacc,
                     currnumacc, sb);
               else
                  trace("CSI * " + (char) inc
                     + " — parsed, not used");
               state = NORM;
               break;
            case OSCMODE3:
               caseOSCMODE3(inc);
               break;
            case OSCMODE2:
               caseOSCMODE2(inc);
               break;
            case OSCMODE:
               caseOSCMODE(inc);
               break;
            case CHARSET_DESIGNATE:
               // ESC ( 0 = DEC Special Graphics, ESC ( B = ASCII
               if (charsetTarget == 0)
                  g0DecGraphics = ('0' == inc);
               else if (charsetTarget == 1)
                  g1DecGraphics = ('0' == inc);
               else
                  trace("charset designate target="
                     + charsetTarget + " '" + (char) inc
                     + "' — parsed, not used");
               trace1("charset designate G"
                  + charsetTarget + ": " + (char) inc);
               state = NORM;
               break;
            case DEC_LINE_ATTR:
               // ESC # 8 = DECALN (Screen Alignment Display)
               if ('8' == inc)
                  window.screenAlignmentDisplay(sb);
               else
                  trace("ESC # " + (char) inc
                     + " (DEC line attr) — parsed, not used");
               state = NORM;
               break;
            case STRING_DISCARD:
               // Discard APC/PM/SOS content until ST
               if (0x9C == inc) {
                  state = NORM; // 8-bit ST
               } else if (27 == inc) {
                  state = ESC;  // ESC may begin ST (ESC \)
               }
               // else: keep consuming string content
               break;
            case DCS_CONTENT:
               // Accumulate DCS content until ST
               if (0x9C == inc) {
                  dispatchDcs(dcsContent.toString());
                  dcsContent.setLength(0);
                  state = NORM;
               } else if (27 == inc) {
                  // Check if next is '\' for ESC \ (ST)
                  // Store ESC in buffer; caseESC will handle
                  state = ESC;
                  dispatchDcs(dcsContent.toString());
                  dcsContent.setLength(0);
               } else {
                  dcsContent.append((char) inc);
               }
               break;
            case ESC:
               caseESC(inc);
               break;
            default:
               trace("unhandled state = " + state);
         }
      if (termLog != null && termLog.isEnabled())
         termLog.logState(prevState, state, inc);
   }

   public synchronized void execute() {

      //trace("ParseInput executing on recbyte " + (int)recbyte);
      try {
         doChar(recbyte);
         while (reader.ready())   {
            //trace("input available");
            doChar((char) reader.read());
         }
         if (state == CR) {
            sb.setLength(sb.length() - 1);
            state = NORM;
            window.setX(1, sb);
         }
         window.updateScreen(sb);
         notify();
      } catch (Throwable e) {
         UI.popError("failure in VT100 io", e);
      }
   }

   /**
    * Logs a warning when a CSI command received more parameters
    * than it uses. Called for single-parameter commands that
    * ignore extra semicolon-separated values.
    *
    * @param cmd the CSI final byte (e.g. 'A', 'P', 'L')
    * @param used number of parameters actually consumed
    */
   private void warnUnusedParams(char cmd, int used) {
      if (currnumacc >= used) {
         StringBuilder msg = new StringBuilder();
         msg.append("CSI ");
         for (int i = 0; i <= currnumacc; i++) {
            if (i > 0) msg.append(';');
            msg.append(numacc[i]);
         }
         msg.append((char) cmd);
         msg.append(" — only ");
         msg.append(used);
         msg.append(" of ");
         msg.append(currnumacc + 1);
         msg.append(" params used");
         trace(msg.toString());
      }
   }

   /**
    * Translates a character through the active character set.
    * When DEC Special Graphics is active, maps 0x60-0x7E to
    * Unicode box-drawing equivalents.
    */
   private char translateCharset(char ch) {
      boolean decActive = useG1 ? g1DecGraphics : g0DecGraphics;
      if (decActive && ch >= 0x60 && ch <= 0x7E)
         return DEC_GRAPHICS_MAP[ch - 0x60];
      return ch;
   }

   private static final boolean traceflag = false;

   private static void trace1(String str) {
      if (traceflag)
         Tools.traceLev(str, 2);
   }
}
