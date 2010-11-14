package javi;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import static history.Tools.trace;

final class Server implements Runnable, EditContainer.FileStatusListener {

   //vic serv;
   private HashMap<EditContainer, BufferedOutputStream> shash =
      new HashMap<EditContainer, BufferedOutputStream>(10);

   private ServerSocket lsock;

   Server(int port) throws IOException {
      lsock = new ServerSocket(port);
      new Thread(this, "VI Server Thread").start();
      EditContainer.registerListener(this);
   }

   public void run() {
      while (true) {
         Socket sock = null;
         BufferedOutputStream outstream = null;
         BufferedReader instream = null;
         try {
            sock = lsock.accept();
            outstream =
               new BufferedOutputStream(sock.getOutputStream());

            instream = new BufferedReader(
               new InputStreamReader(sock.getInputStream(), "UTF-8"));

            if (1 != instream.read())  {
               throw new InputException("invalid byte from remote");
            }
            EditContainer ed = FileList.openFileList(instream, null);
            if (null != ed) {
               shash.put(ed, outstream);
               UI.toFront();
            }
         } catch (Throwable e) {
            trace("server.run caught exception " + e);
            e.printStackTrace();
         } finally {
            try {
               if (null != sock)
                  sock.close();
               if (null != instream)
                  instream.close();
               if (null != outstream)
                  outstream.close();
            } catch (IOException e) {
               trace("caught exception while trying to close" + e);
               e.printStackTrace();
            }
         }
      }
   }

   void donefile(EditContainer ev) {
      //trace("server.donefile entered " + ev);
      BufferedOutputStream outstream = shash.get(ev);
      if (null == outstream)
         return;
      try {
         outstream.write(1);
         outstream.close();
         shash.remove(ev);
         UI.hide();
      } catch (IOException e) {
         trace("server.donefile caught exception " + e);
      }

   }

   public boolean fileDisposed(EditContainer ev) {
      donefile(ev);
      return false;
   }

   public void fileWritten(EditContainer ev) {
      donefile(ev);
   }

   public void fileAdded(EditContainer ev) { /* don't care */ }

}
