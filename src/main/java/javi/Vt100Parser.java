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

   /** OSC command character. */
   private char oscmode;

   /** Buffer for OSC string content. */
   private final StringBuilder oscstring = new StringBuilder(80);

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
      sb.append(inc);
      state = NORM;
      if ('\n' == inc)
         return;
      else
         window.setX(1, sb);
      caseNORM(inc);
   }

   private void caseNORM(char inc) {
      switch (inc) {
            //case '\t':
            //   sbprocess();
            //  window.insertTab();
            //  break;
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
            break;
         case 15: // SI - Shift In (select G0 character set)
            trace1("receive SI character select G0 character set");
            break;
         case 12:
         case 11:
         case 10:
         case 9:
            sb.append(inc);
            break;
         case (char) 0xffff:
            trace("received -1");
            Thread.dumpStack();
            break;
         default:
            if (inc < 20)
               trace1("unhandeld control character 0x"
                  + Integer.toHexString(inc));
            sb.append(inc);
      }
   }

   private void caseGETNUM(int inc) {
      if (inc >= '0' && inc <= '9') {
         numacc[currnumacc] = numacc[currnumacc] * 10 + inc - '0';
         highestSet = currnumacc;
      } else {
         int newstate = NORM;
         boolean def = highestSet != currnumacc;
         switch (inc) {
            case 'A':
               trace1("cursor up " + numacc[currnumacc]);
               window.incY(-1 * (def ? 1 :  numacc[currnumacc]), sb);
               break;
            case 'B':
               trace1("cursor down " + numacc[currnumacc]);
               window.incY(1 * (def ? 1 :  numacc[currnumacc]), sb);
               break;
            case 'C':
               trace1("cursor right " + numacc[currnumacc]);
               window.incX(1 * (def ? 1 :  numacc[currnumacc]), sb);
               break;
            case 'D':
               trace1("cursor left " + numacc[currnumacc]);
               window.incX(-1 * (def ? 1 :  numacc[currnumacc]), sb);
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
            case 'P': // from xterm doc erase number of characters
               window.eraseChars(def ? 1 : numacc[currnumacc], sb);
               break;
            case 'm': // Character Attributes (SGR - Select Graphic Rendition)
               // Pass parameters to window for potential color/attribute handling
               trace1("[m (graphic Rendition)");
               window.setGraphicRendition(numacc, sb);
               break;
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
            case 27:
               newstate = ESC;
               break;
            case '@':
               for (int ii = 0; ii <= numacc[currnumacc]; ii++)
                  sb.append(' ');
               break;
            case 'h': //set mode xterm
            case 'l': //reset mode xterm
               handleModeSet('h' == inc);
               break;

            case 'r': // Change Attributes in Rectangular Area (DECCARA).
               //P t ; P l ; P b ; P r denotes the rectangle.
               //P s denotes the SGR attributes to change: 0, 1, 4, 5, 7
               trace1("receive DECCARA count = " + currnumacc);
               break;
            case 'L': // insert lines before or after???
               window.insertLines(0 == numacc[currnumacc]
                  ? 1
                  : numacc[currnumacc], sb);
               break;
            case 'M': // Delete lines (ESC[nM)
               trace1("delete " + (def ? 1 : numacc[currnumacc]) + " lines");
               window.deleteLines(def ? 1 : numacc[currnumacc], sb);
               break;
            case 'S': // Scroll up (ESC[nS)
               trace1("scroll up " + (def ? 1 : numacc[currnumacc]) + " lines");
               window.scrollUp(def ? 1 : numacc[currnumacc], sb);
               break;
            case 'T': // Scroll down (ESC[nT)
               trace1("scroll down " + (def ? 1 : numacc[currnumacc]) + " lines");
               window.scrollDown(def ? 1 : numacc[currnumacc], sb);
               break;
            case 'f': // Horizontal and Vertical Position (same as H)
               handleCursorPosition(def);
               break;
            case 'G': // Cursor Character Absolute (ESC[nG) - move to column n
               trace1("cursor to column " + (def ? 1 : numacc[currnumacc]));
               window.setX(def ? 1 : numacc[currnumacc], sb);
               break;
            case 'd': // Line Position Absolute (ESC[nd) - move to row n
               trace1("cursor to row " + (def ? 1 : numacc[currnumacc]));
               window.setY(def ? 1 : numacc[currnumacc], sb);
               break;
            case 'E': // Cursor Next Line (ESC[nE) - move to beginning of line n down
               trace1("cursor next line " + (def ? 1 : numacc[currnumacc]));
               window.incY(def ? 1 : numacc[currnumacc], sb);
               window.setX(1, sb);
               break;
            case 'F': // Cursor Previous Line (ESC[nF) - move to beginning of line n up
               trace1("cursor prev line " + (def ? 1 : numacc[currnumacc]));
               window.incY(-(def ? 1 : numacc[currnumacc]), sb);
               window.setX(1, sb);
               break;
            case 'X': // Erase Character (ESC[nX)
               // erase n characters (replace with spaces)
               trace1("erase " + (def ? 1 : numacc[currnumacc]) + " characters");
               window.eraseChars(def ? 1 : numacc[currnumacc], sb);
               break;
            case 'n': // Device Status Report
               if (!def && numacc[currnumacc] == 6) {
                  trace1("cursor position report (responding)");
                  window.respondCursorPosition(sb);
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
               trace("unkown [ terminator " + (char) inc + " decimal "  + inc
                  + " 0x" + Integer.toHexString(inc));
         }
         //trace("completed [" + (char) inc);
         state = newstate;
      }
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
            trace1("erase scrollback (ignored)");
            break;
         default:
            trace1("unknown [J index " + numacc[currnumacc]);
      }
   }

   private void handleEraseLine() {
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
         switch (numacc[currnumacc]) {
            case 4:
               window.setInsertMode(val, sb);
               break;
            default:
               trace("unknown mode " + numacc[currnumacc]);
         }
   }

   private void caseMODE(int inc) {
      if (inc >= '0' && inc <= '9')
         modeNumbers[modeCount] = modeNumbers[modeCount] * 10 + inc - '0';
      else if (';' == inc) {
         // Multiple mode numbers separated by ';' (e.g., ESC[?1000;1006h)
         modeCount++;
         if (modeCount < modeNumbers.length)
            modeNumbers[modeCount] = 0;
      } else {
         boolean enable = 'h' == inc;
         for (int i = 0; i <= modeCount && i < modeNumbers.length; i++)
            applyMode(modeNumbers[i], enable);
         state = NORM;
      }
   }

   private void applyMode(int modeNum, boolean enable) {
      switch (modeNum) {
         case 1: // Application cursor keys (DECCKM)
            window.setApplicationCursorKeys(enable);
            break;
         case 2:
            trace("vt52 mode shouldn't happen");
            break;
         case 3:
            trace("132 column mode ???");
            break;
         case 4:
            trace("smooth scrolling ???");
            break;
         case 47:
         case 1047: // F10: use alternate screen buffer
         case 1049: // F10: save cursor + use alternate screen buffer
            window.switchAlternateScreen(enable, sb);
            break;
         case 25: // DECTCEM: show/hide cursor
            window.setCursorVisible(enable);
            break;

         case 1000: // Normal mouse tracking
         case 1002: // Button-event mouse tracking
         case 1003: // Any-event mouse tracking
            window.setMouseTracking(modeNum, enable);
            break;
         case 1006: // SGR extended mouse mode
            window.setSgrMouseMode(enable);
            break;
         case 7: // Autowrap mode
            window.setAutowrapMode(enable);
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
         String title = oscstring.toString();
         oscstring.setLength(0);
         switch (oscmode) {
            case '0': // Set icon name and window title
               trace1("change icon and title: " + title);
               window.setTitle(title);
               break;
            case '1': // Set icon name
               trace1("change icon name: " + title);
               break;
            case '2': // Set window title
               trace1("change window title: " + title);
               window.setTitle(title);
               break;
            case '4': // Set/change color palette (ignored)
               trace1("change color palette (ignored): " + title);
               break;
            case '7': // Set working directory (iTerm2, etc.)
               trace1("set working directory (ignored): " + title);
               break;
            default:
               trace1("unexpected oscmode " + oscmode + ": " + title);
         }
      } else if ('\\' == inc && oscstring.length() > 0
            && oscstring.charAt(oscstring.length() - 1) == 27) {
         // ESC \ also terminates OSC (String Terminator)
         oscstring.setLength(oscstring.length() - 1);
         caseOSCMODE3(7); // Process as if BEL was received
      } else {
         oscstring.append((char) inc);
      }
   }

   private void caseESC(int inc) {

      switch (inc) {
         case '[': //91
            state = GETNUM;
            numacc = new int[1];
            numacc[0] = 0;
            currnumacc = 0;

            break;
         case '7': // save cursor and attributes
            window.saveCursor(sb);
            state = NORM;
            break;
         case '8': // restore cursor and attributes
            window.restoreCursor(sb);
            break;
         case 'M':
            trace1("reverse index, whatever that means");
            window.incY(-1, sb);
            state = NORM;
            break;

         case ' ':
            trace("what does escape ' ' mean?");
            state = NORM;
            break;

         case 27:
            trace("what does double escape mean?");
            // I think it means stay in escape
            break;
         case '>':
            trace1("keypad numeric mode");
            state = NORM;
            break;
         case '=':
            trace1("exit keypad numeric mode");
            state = NORM;
            break;
         case ']':
            state = OSCMODE;
            break;
         default:
            trace("unhandled escape code " + (char) inc + " decimal "  + inc
               + " 0x" + Integer.toHexString(inc));
            state = NORM;
      }
   }

   private void caseOSCMODE2(int inc) {
      if (';' != inc)
         trace("unexpected OSCMODE2 character");
      state = OSCMODE3;
   }

   private void caseOSCMODE(int inc) {
      oscmode = (char) inc;
      state = OSCMODE2;
   }

   private void doChar(char inc) {
      //trace("state " + state + " received byte " + (char)inc + " decimal "  + inc+ " 0x" + Integer.toHexString(inc));
      if (inc == -1)
         trace("received -1 on Vt100");
      //throw new InputException("end of input for Vt100");
      else
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
            case OSCMODE3:
               caseOSCMODE3(inc);
               break;
            case OSCMODE2:
               caseOSCMODE2(inc);
               break;
            case OSCMODE:
               caseOSCMODE(inc);
               break;
            case ESC:
               caseESC(inc);
               break;
            default:
               trace("unhandled state = " + state);
         }
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

   private static final boolean traceflag = false;

   private static void trace1(String str) {
      if (traceflag)
         Tools.traceLev(str, 2);
   }
}
