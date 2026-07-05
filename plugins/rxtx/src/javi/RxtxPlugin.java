package javi.rxtx;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.List;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import javi.EditContainer;
import javi.FvContext;
import javi.InputException;
import javi.Plugin;
import javi.Rgroup;
import javi.StringIoc;
import javi.TextEdit;
import javi.Vt100;

import static history.Tools.trace;
/**
 * RXTX serial communication plugin for javi.
 *
 * <p>Provides the {@code :comm} command for connecting to serial
 * ports via the RXTX library (gnu.io). This plugin is optional;
 * when not loaded, serial port functionality is unavailable.</p>
 *
 * <p>Usage: {@code :loadplugin rxtx} then {@code :comm COM1 9600}</p>
 */
public final class RxtxPlugin extends Rgroup implements Plugin {

   public static final String pluginInfo = "RXTX serial communication";

   private static TextEdit commCon;
   private static String portname = "COM1";
   private static int baudrate = 38400;

   public Plugin create(List<String> args) throws IOException {
       return new RxtxPlugin();
   }

   RxtxPlugin() {
      final String[] rnames = {
         "",
      };
      register(rnames);

      trace("registering comm command");
      registerArgCommand("comm",
         "communication (serial port)", "shell",
         (arg, count, rcount, fvc, dot) -> {
            startcom(
               arg instanceof String ? (String) arg : null,
               fvc);
            return null;
         });

      EditContainer.registerListener(commListener);
   }

   protected Object doroutine(int rnum, Object arg, int count,
         int rcount, FvContext fvc, boolean dotmode) {
      throw new RuntimeException("RxtxPlugin: unexpected doroutine");
   }

   private static void startcom(String arg, FvContext fvc)
         throws IOException, InputException {
      if (null == commCon) {
         if (null != arg)
            try {
               String[] args = arg.split(" +");
               if (2 != args.length)
                  throw new InputException(
                     "invalid arguments to comm command:" + arg);
               baudrate = Integer.parseInt(args[1]);
               portname = args[0];
            } catch (NumberFormatException e) {
               throw new InputException(
                  "invalid number in arguments", e);
            }
         commCon = CommReader.make(portname, baudrate);
         FvContext.connectFv(commCon, fvc.vi);
      } else
         FvContext.connectFv(commCon, fvc.vi);
   }

   private static final EditContainer.FileStatusListener commListener =
      new EditContainer.FileStatusListener() {
         public void fileAdded(EditContainer ev) {
         }

         public void fileWritten(EditContainer ev) {
         }

         public boolean fileDisposed(EditContainer ev) {
            if (ev == commCon)
               commCon = null;
            return false;
         }
      };

   static final class CommReader extends Vt100 {

      private static final long serialVersionUID = 1;
      private transient SerialPort port;

      static Vt100 make(String comport, int baud) throws
            InputException, IOException {
         try {
            CommPortIdentifier portid =
               CommPortIdentifier.getPortIdentifier(comport);
            SerialPort ports =
               (SerialPort) portid.open("CommReader", 100);
            try {
               ports.setFlowControlMode(
                  SerialPort.FLOWCONTROL_NONE);
               ports.setSerialPortParams(baud,
                  SerialPort.DATABITS_8,
                  SerialPort.STOPBITS_1,
                  SerialPort.PARITY_NONE);
               return new CommReader(baud, comport, ports);
            } catch (IOException e) {
               ports.close();
               throw e;
            } catch (UnsupportedCommOperationException e) {
               ports.close();
               throw new IOException(
                  "serial port: " + e.getMessage(), e);
            }
         } catch (NoSuchPortException e) {
            throw new InputException(
               "invalid serial port name", e);
         } catch (PortInUseException e) {
            throw new InputException(
               "serial port in use", e);
         }
      }

      @SuppressWarnings("deprecation")
      CommReader(int baud, String comport, SerialPort porti)
            throws IOException {
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
