package javi;

import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

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
 *   <li>{@link CommReader} - Serial port connections</li>
 * </ul>
 *
 * @see Vt100Parser
 * @see VScreen
 * @see ShellSession
 */
class Vt100 extends TextEdit<String> {


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
    * @deprecated Use {@link #Vt100(OutputStream, BufferedInputStream, IoConverter, Charset)}
    */
   @Deprecated
   Vt100(OutputStream ostri, BufferedInputStream istr,
         IoConverter<String> ioc) throws java.io.UnsupportedEncodingException {
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
   Vt100(OutputStream ostri, BufferedInputStream istr,
         IoConverter<String> ioc, Charset charsetToUse) {
      super(ioc, ioc.prop);
      this.charset = charsetToUse;
      parser = new Vt100Parser(new ECScreen(), istr, charset);
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

   public final void startHandle(FvContext fvc) {
      //trace("startHandle " + fvc);
      if (null != fvc) {
//         oldfont = FontList.setFontName("Courier New" , fvc.vi);
//        oldfont = FontList.setFontName("Vrinda" ,fvc.vi);
//        oldsize =  FontList.setFontSize(new Float(15.0) ,fvc.vi);
         //trace("oldfont = " + oldfont);
         rows = fvc.vi.getRows(1.0f);
         int neededRows = rows - readIn() + 1;
         //trace("rows " + rows + " readIn " + ev.readIn() + " neededRows " + neededRows);
         while (--neededRows > -1)  {
            //trace("setfvc inserting line at "  + ev.readIn());
            insertOne("", readIn());
         }
         // Update shell PTY size to match actual screen dimensions
         sendStty(rows, MiscCommands.getWidth());
      }

      currfvc = fvc;
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
    * <p>Updates internal row count, adds lines if the terminal grew,
    * and sends stty to inform the shell process of the new dimensions.</p>
    *
    * @param newRows new number of rows
    * @param newCols new number of columns
    */
   void notifyResize(int newRows, int newCols) {
      if (newRows <= 0 || newCols <= 0)
         return;
      rows = newRows;
      int neededRows = rows - readIn() + 1;
      while (--neededRows > -1)
         insertOne("", readIn());
      sendStty(rows, newCols);
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
    * Sets cursor blink mode (mode 12) on or off.
    */
   void setCursorBlinkMode(boolean enable) {
      cursorBlinkMode = enable;
      trace("Vt100: cursor blink mode=" + cursorBlinkMode);
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
                        writer.write("\33[D");
                        break;
                     case JeyEvent.VK_RIGHT:
                        writer.write("\33[C");
                        break;
                     case JeyEvent.VK_UP:
                        writer.write("\33[A");
                        break;
                     case JeyEvent.VK_DOWN:
                        writer.write("\33[B");
                        break;
                     case JeyEvent.VK_INSERT:
                        return;
                     case JeyEvent.VK_F8:
                        return;
                     default:
                        trace("unhandle KeyCode "
                           + kev.getKeyCode());
                  }
                  writer.flush();
               }
            } else {
               // Cmd+V (macOS) / Ctrl+V clipboard paste
               if (('v' == ch || 'V' == ch)
                     && (kev.getModifiers()
                        & JeyEvent.META_MASK) != 0) {
                  pasteClipboard();
                  continue;
               }
               if ('\r' == ch
                     && '\r' == kev.getKeyCode())
                  ch = '\n';
               synchronized (writerLock) {
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
      void incX(int amount, StringBuilder sb) {
         insertString(sb);
         vtcursor.x += amount;
         if (vtcursor.x < 0)
            vtcursor.x = 0;
      }

      void incY(int amount, StringBuilder sb)  {
         updateScreen(sb);
         vtcursor.y += amount;
         if (vtcursor.y < readIn() - rows)
            vtcursor.y = readIn() - rows;
      }

      void setX(int val, StringBuilder sb) {
         setXmy(val, sb);
      }

      void setY(int val, StringBuilder sb) {
         //trace("setY " + val);
         updateScreen(sb);
         vtcursor.y = readIn() - 1 - rows + val;
         //trace("after setY vtcursor" + vtcursor + " ev.readIn = " + ev.readIn() + " rows " + rows + " val " + val);
      }

      void setXY(int xval, int yval, StringBuilder sb) {
         //trace("setXY " + xval + "," + yval  + " readIn " + ev.readIn()  + " vtcursor = " + vtcursor);

         updateScreen(sb);
         //trace("setXY " + xval + "," + yval  + " readIn " + ev.readIn()  + " vtcursor = " + vtcursor + " rows " + rows );
         vtcursor.y = readIn() - 1 - rows + yval;
         vtcursor.x = xval - 1;
         //trace("setXY " + xval + "," + yval  + " readIn " + ev.readIn()  + " vtcursor = " + vtcursor);
         //updateScreen(sb);
         //trace("setXY " + xval + "," + yval  + " readIn " + ev.readIn()  + " vtcursor = " + vtcursor);
      }

      void eraseScreen(StringBuilder sb) {
         updateScreen(sb);
         int end = readIn();
         int start = end - rows - 1;
         if (start < 1)
            start = 1;
         for (int ii = start; ii < end; ii++)
            changeElementAt("", ii);

         //???vtcursor.x=0;
         //???vtcursor.y=ev.readIn()-1;
      }

      void eraseToEnd(StringBuilder sb) {
         updateScreen(sb);
         if (vtcursor.y < readIn()) {
            int lend = at(vtcursor.y).length();
            deletetext(false, vtcursor.x, vtcursor.y, lend, vtcursor.y);
         }
      }

      void eraseLine(StringBuilder sb) {
         insertString(sb);
         changeElementAt("", vtcursor.y);
      }

      void eraseChars(int count, StringBuilder sb) {
         insertString(sb);
         int delto = vtcursor.x + count;
         int strlen =  at(vtcursor.y).length();
         if (delto > strlen) {
            delto = strlen;
            trace("attempt to delete more character that possible delto "
               + delto + " strlen " + strlen);
         }
         deletetext(false, vtcursor.x, vtcursor.y, delto, vtcursor.y);
      }

      void insertLines(int count, StringBuilder sb) {
         trace("insertLines count " + count);
         insertString(sb);
         remove(readIn() - count, count);
         while (--count >= 0)
            insertOne("", vtcursor.y);
         trace("leave insertLines readIn = " + readIn());
      }

      void setInsertMode(boolean val, StringBuilder sb) {
         insertString(sb);
         insertmode = val;
      }

      void updateScreen(StringBuilder sb) {
         insertString(sb);
         fixline();
         //rows = currfvc.vi.getRows((float).99999);
         if (null != currfvc) {
            currfvc.cursorabs(vtcursor.x, vtcursor.y);
            currfvc.placeline(readIn() - 1, .99999f);
         }
         //trace("leaving update screen vtcursor = " + vtcursor + " readIn  " + ev.readIn());

      }

      void saveCursor(StringBuilder sb) {
         insertString(sb);
         savecursor.x = vtcursor.x;
         savecursor.y = readIn() - vtcursor.y;
      }

      void restoreCursor(StringBuilder sb) {
         insertString(sb);
         vtcursor.y = readIn() - savecursor.y;
         vtcursor.x = savecursor.x;
      }

      // F10: Alternate screen buffer state
      private ArrayList<String> savedScreen;
      private MovePos savedAltCursor;
      private boolean inAlternateScreen;

      void switchAlternateScreen(boolean enable, StringBuilder sb) {
         insertString(sb);
         if (enable && !inAlternateScreen) {
            // Save main screen content
            int end = readIn();
            int start = end - rows - 1;
            if (start < 1)
               start = 1;
            savedScreen = new ArrayList<>(rows);
            for (int ii = start; ii < end; ii++)
               savedScreen.add(at(ii).toString());
            savedAltCursor = new MovePos(vtcursor.x, vtcursor.y);
            // Clear screen for alternate buffer
            for (int ii = start; ii < end; ii++)
               changeElementAt("", ii);
            vtcursor.x = 0;
            vtcursor.y = start;
            inAlternateScreen = true;
            trace("switched to alternate screen buffer");
         } else if (!enable && inAlternateScreen) {
            // Restore main screen content
            if (savedScreen != null) {
               int end = readIn();
               int start = end - rows - 1;
               if (start < 1)
                  start = 1;
               for (int ii = 0;
                     ii < savedScreen.size() && start + ii < end;
                     ii++)
                  changeElementAt(savedScreen.get(ii), start + ii);
               savedScreen = null;
            }
            if (savedAltCursor != null) {
               vtcursor.x = savedAltCursor.x;
               vtcursor.y = savedAltCursor.y;
               savedAltCursor = null;
            }
            inAlternateScreen = false;
            trace("restored main screen buffer");
         }
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

      void setCursorBlinkMode(boolean enable) {
         Vt100.this.setCursorBlinkMode(enable);
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
      vtcursor.x = val - 1;
   }

   private void insertString(StringBuilder sb) {
      //trace("insertString " + this);
      if (vtcursor.y > readIn() - 1) {
         trace("shouldn't get here unless some one deleted lines " + readIn());
         insertOne("", readIn());
         if (currfvc != null) {
            trace("in terminal mode inserting line at" + readIn());
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
      //trace("insertString vtcursor = " + vtcursor + " readIn" + readIn() + " text:" + text);
      int sbused = 0;

      if (!insertmode && vtcursor.y < readIn()) {
         while (sbused < text.length()) {
            String eline = fixline();
            if (eline.length() < vtcursor.x)
               break;
            int nindex = text.indexOf('\n', sbused);
            int linelen = eline.length();
            if (nindex == -1) {
               //trace("sbprocess no new line linelen " + linelen);
               if (vtcursor.x <= linelen)  {

                  int delval = text.length() - sbused + vtcursor.x;
                  if (delval > linelen)
                     delval = linelen;
                  deletetext(false, vtcursor.x, vtcursor.y,
                     delval, vtcursor.y);
               }

               inserttext(text.substring(sbused),
                  vtcursor.x, vtcursor.y).posMove(vtcursor);
               sbused = text.length();

            } else if (nindex == sbused) {
               //trace("nindex " + nindex + " sbused " + sbused);
               vtcursor.y++;
               vtcursor.x = 0;
               sbused++;
               if (vtcursor.y >= readIn()) {
                  insertOne("", readIn());
                  //trace("came to end of file");
                  break;
               }

            } else if (nindex > 0) {
               //trace("sbprocess insert into line ");
               int delval = text.length() - sbused + vtcursor.x;
               if (delval > linelen)
                  delval = linelen;
               if (vtcursor.x < linelen)
                  deletetext(false, vtcursor.x, vtcursor.y, delval,
                     vtcursor.y);
               String newinfo = text.substring(sbused, nindex);
               inserttext(newinfo, vtcursor.x, vtcursor.y).posMove(vtcursor);
               vtcursor.y++;
               vtcursor.x = 0; //???
               sbused = nindex + 1;
               if (vtcursor.y >= readIn()) {
                  insertOne("", readIn());
                  break;
               }
            }
         }
      }
      if (sbused < text.length()) {
         String itext = text.substring(sbused);
         //trace("sbprocess insert at end text:"  + itext  );
         //vtcursor = ((extext)ev).inserttext (itext,currlinelen,ev.readIn()-1);
         inserttext(itext, vtcursor.x, vtcursor.y).posMove(vtcursor);
      }
      if (setxflag)
         setXmy(1, sb);
      //trace("insertString exit vtcursor " + vtcursor + " readIn = " + ev.readIn());
   }

   public final String getnext() {
      return null;
   }

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

   static final class CommReader extends Vt100 {

      private static final long serialVersionUID = 1;
      private transient SerialPort port;

      static Vt100 make(String comport, int baud) throws
            InputException, IOException {
         try {
            //trace("comport " + comport);
            CommPortIdentifier portid =
               CommPortIdentifier.getPortIdentifier(comport);
            SerialPort ports = (SerialPort) portid.open("CommReader", 100);
            try {
               ports.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
               ports.setSerialPortParams(baud, SerialPort.DATABITS_8,
                  SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
               return new Vt100.CommReader(baud, comport, ports);
            }  catch (IOException e) {
               ports.close();
               throw e; // new IOException("serial port: " + e.getMessage());
            }  catch (UnsupportedCommOperationException e) {
               ports.close();
               throw new IOException("serial port: " + e.getMessage(), e);
            }
         } catch (NoSuchPortException e) {
            throw new InputException("invalid serial port name", e);
         } catch (PortInUseException e) {
            throw new InputException("serial port in use", e);
         }
      }

      CommReader(int baud, String comport, SerialPort porti) throws
            IOException {
         super(
            porti.getOutputStream(),
            new BufferedInputStream(porti.getInputStream()),
            new StringIoc("vt100 start", null)
         );
         port = porti;
      }

      public void disposeFvc() throws IOException {
         super.disposeFvc();
         if (null != port) {
            port.close();
            port = null;
         }
      }

   }
}
