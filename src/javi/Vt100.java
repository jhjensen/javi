package javi;

import java.io.IOException;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.awt.event.KeyEvent;


abstract class Vt100 extends TextEdit<String> {


   /* Copyright 1996 James Jensen all rights reserved */

   static final String copyright = "Copyright 1996 James Jensen";

   private final OutputStreamWriter writer;
//   private final OutputStream str;

   private FvContext currfvc = null;
   private MovePos savecursor = new MovePos(0, 1);
   private MovePos vtcursor = new MovePos(0, 1);
   private boolean insertmode = false;
   private int rows;
   private String oldfont;
//   private Float oldsize;

   private final Vt100Parser parser;

   Vt100(OutputStream ostri, BufferedInputStream istr,
         IoConverter<String> ioc) {
      //StringIoc ioc = new StringIoc("vt100 start",null);
      super(ioc, ioc.prop);
      //str = ostri;
      parser = new Vt100Parser(this, istr);
      writer = new OutputStreamWriter(ostri);
   }

   private class Keyhandle extends KeyHandler {

      boolean dispatchKeyEvent(KeyEvent ev) {
         return handleKey(ev);
      }
      void startDispatch(FvContext fvc) {
         startHandle(fvc);
      }
   }

   KeyHandler getKeyHandler() {
      //trace("vt100 return keyHandle");
      return new Keyhandle();
   }

   public void startHandle(FvContext fvc) {
      //trace("startHandle " + fvc);
      if (fvc != null) {
//         oldfont = FontList.setFontName("Courier New" , fvc.vi);
//        oldfont = FontList.setFontName("Vrinda" ,fvc.vi);
//        oldsize =  FontList.setFontSize(new Float(15.0) ,fvc.vi);
         //trace("oldfont = " + oldfont);
         rows = fvc.vi.getRows((float) 1.0);
         int neededRows = rows - readIn() + 1;
         //trace("rows " + rows + " readIn " + ev.readIn() + " neededRows " + neededRows);
         while (--neededRows > -1)  {
            //trace("setfvc inserting line at "  + ev.readIn());
            insertOne("", readIn());
         }
      }

      currfvc = fvc;
      //trace("leave setfvc readIn = " + ev.readIn());
   }

   public String fromString(String str) {
      return str;
   }

   public boolean handleKey(KeyEvent kev) {
      //trace("dispatchKeyEvent" + kev);
      char ch = kev.getKeyChar();
      try {
         if (ch == KeyEvent.CHAR_UNDEFINED) {
            switch (kev.getKeyCode()) {
               default:
                  trace("unhandle KeyCode " + kev.getKeyCode());
                  return true;
               case KeyEvent.VK_LEFT:
                  //writer.write("\33D");
                  writer.write("\33[D");
                  break;
               case KeyEvent.VK_RIGHT:
                  writer.write("\33[C");
                  //writer.write("\33C");
                  break;
               case KeyEvent.VK_UP:
                  writer.write("\33[A");
                  //writer.write("\33A");
                  break;
               case KeyEvent.VK_DOWN:
                  writer.write("\33[B");
                  //writer.write("\33B");
                  break;
               case KeyEvent.VK_INSERT:
                  return false;
            }
            writer.flush();
            return true;
         } else {
            if (ch == '\r' && kev.getKeyCode() == '\r') // this was really a cr
               ch = '\n';
            //trace ("passing through ch " + ch + " 0x" + Integer.toHexString(ch));
            //trace ("passing through code " + Integer.toHexString(kev.getKeyCode()));
            //trace ("passing key location " + kev.getKeyLocation());
            //trace ("passing key Text " + kev.getKeyText(kev.getKeyCode()));
            //trace ("key modifier " + kev.getModifiersExText(kev.getModifiersEx()));
            writer.write(ch);
            writer.flush();
            return true;
         }
      } catch (IOException e) {
         trace("caught IOException " + e);
         return false;
      }
   }

   void incX(int amount, StringBuffer sb) {
      insertString(sb);
      vtcursor.x += amount;
      if (vtcursor.x < 0)
         vtcursor.x = 0;
   }

   void incY(int amount, StringBuffer sb)  {
      updateScreen(sb);
      vtcursor.y += amount;
      if (vtcursor.y < readIn() - rows)
         vtcursor.y = readIn() - rows;
   }
   void setX(int val, StringBuffer sb) {
      insertString(sb);
      vtcursor.x = val - 1;
   }

   void setY(int val, StringBuffer sb) {
      //trace("setY " + val);
      updateScreen(sb);
      vtcursor.y = readIn() - 1 - rows + val;
      //trace("after setY vtcursor" + vtcursor + " ev.readIn = " + ev.readIn() + " rows " + rows + " val " + val);
   }

   void setXY(int xval, int yval, StringBuffer sb) {
      //trace("setXY " + xval + "," + yval  + " readIn " + ev.readIn()  + " vtcursor = " + vtcursor);

      updateScreen(sb);
      //trace("setXY " + xval + "," + yval  + " readIn " + ev.readIn()  + " vtcursor = " + vtcursor + " rows " + rows );
      vtcursor.y = readIn() - 1 - rows + yval;
      vtcursor.x = xval - 1;
      //trace("setXY " + xval + "," + yval  + " readIn " + ev.readIn()  + " vtcursor = " + vtcursor);
      //updateScreen(sb);
      //trace("setXY " + xval + "," + yval  + " readIn " + ev.readIn()  + " vtcursor = " + vtcursor);
   }

   void eraseScreen(StringBuffer sb) {
      updateScreen(sb);
      int end = readIn();
      int start = end - rows - 1;
      if (start < 1)
         start = 1;
      for (int i = start; i < end; i++)
         changeElementAt("", i);

      //???vtcursor.x=0;
      //???vtcursor.y=ev.readIn()-1;
   }

   void eraseToEnd(StringBuffer sb) {
      updateScreen(sb);
      if (vtcursor.y < readIn()) {
         int lend = at(vtcursor.y).length();
         deletetext(false, vtcursor.x, vtcursor.y, lend, vtcursor.y);
      }
   }

   void eraseLine(StringBuffer sb) {
      insertString(sb);
      changeElementAt("", vtcursor.y);
   }

   void eraseChars(int count, StringBuffer sb) {
      insertString(sb);
      int delto = vtcursor.x + count;
      int strlen =  at(vtcursor.y).length();
      if (delto > strlen) {
         delto = strlen;
         trace("attempt to delete more character that possible delto "
            + delto + " strlen " + strlen);
      }
      deletetext(false, vtcursor.x, vtcursor.y, delto , vtcursor.y);
   }

   void insertLines(int count, StringBuffer sb) {
      trace("insertLines count " + count);
      insertString(sb);
      remove(readIn() - count, count);
      while (--count >= 0)
         insertOne("", vtcursor.y);
      trace("leave insertLines readIn = " + readIn());
   }

   void setInsertMode(boolean val, StringBuffer sb) {
      insertString(sb);
      insertmode = val;
   }

   void updateScreen(StringBuffer sb) {
      insertString(sb);
      fixline();
      //rows = currfvc.vi.getRows((float).99999);
      if (currfvc != null) {
         currfvc.cursorabs(vtcursor.x, vtcursor.y);
         currfvc.placeline(readIn() - 1, (float) .99999);
      }
      //trace("leaving update screen vtcursor = " + vtcursor + " readIn  " + ev.readIn());

   }

   void saveCursor(StringBuffer sb) {
      insertString(sb);
      savecursor.x = vtcursor.x;
      savecursor.y = readIn() - vtcursor.y;
   }

   void restoreCursor(StringBuffer sb) {
      insertString(sb);
      vtcursor.y = readIn() - savecursor.y;
      vtcursor.x = savecursor.x;
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

   private void insertString(StringBuffer sb) {
      if (vtcursor.y > readIn() - 1) {
         trace("shouldn't get here unless some one deleted lines " + readIn());
         insertOne("", readIn());
         if (currfvc != null) {
            trace("in terminal mode inserting line at" + readIn());
            int neededRows = currfvc.vi.getRows((float) 1.0) - readIn() + 1;
            while (--neededRows > -1)
               insertOne("", readIn());
         }
         vtcursor.y = readIn() - 1;
         vtcursor.x = 0;
      }
      fixline();
      if (sb.length() == 0)
         return;

      boolean setxflag = false;
      if (sb.charAt(sb.length() - 1) == '\r') {
         sb.setLength(sb.length() - 1);
         setxflag = true;
      }
      String text = sb.toString();
      sb.setLength(0);
      //trace("insertString vtcursor = " + vtcursor + " ev.readIn" + ev.readIn() + " text:" + text);
      int sbused = 0;

      if (!insertmode && vtcursor.y < readIn())
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

               vtcursor.set(inserttext(text.substring(sbused),
                  vtcursor.x, vtcursor.y));
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
               vtcursor.set(inserttext(newinfo, vtcursor.x, vtcursor.y));
               vtcursor.y++;
               vtcursor.x = 0; //???
               sbused = nindex + 1;
               if (vtcursor.y >= readIn()) {
                  insertOne("", readIn());
                  break;
               }
            }
         }
      if (sbused < text.length()) {
         String itext = text.substring(sbused);
         //trace("sbprocess insert at end text:"  + itext  );
         //vtcursor = ((extext)ev).inserttext (itext,currlinelen,ev.readIn()-1);
         vtcursor.set(inserttext(itext, vtcursor.x, vtcursor.y));
      }
      if (setxflag)
         setX(1, sb);
      //trace("insertString exit vtcursor " + vtcursor + " readIn = " + ev.readIn());
   }



   public String getnext() {
      return null;
   }

   public void disposeFvc() throws IOException {
      parser.stop();
      super.disposeFvc();
   }
   static final class Telnet extends Vt100 {

      private Process proc;

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

         String[] cmd = host == null
                        ? execstring1
                        : execstring2;

         if (host != null)
            cmd[3] = host;

         Vt100 vt = new Vt100.Telnet(cmd[0], Tools.iocmd(cmd));
         return  vt;
      }

      private Telnet(String execstringi, Process proci) {
         super(proci.getOutputStream(),
               new BufferedInputStream(proci.getInputStream()),
               new StringIoc("vt100 start", null)
              );
         proc = proci;
      }

      public void disposeFvc() throws IOException {
         super.disposeFvc();
         if (proc != null) {
            proc.destroy();
            proc = null;
         }
      }
   }

   static class CommReader extends Vt100 {

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
            throw new InputException("invalid serial port name" , e);
         } catch (PortInUseException e) {
            throw new InputException("serial port in use" , e);
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
         if (port != null) {
            port.close();
            port = null;
         }
      }

   }
}
