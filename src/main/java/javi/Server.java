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
   private HashMap<EditContainer, Socket> shash =
      new HashMap<EditContainer, Socket>(10);

   private ServerSocket lsock;

   Server(int port) throws IOException {
      lsock = new ServerSocket(port);
      new Thread(this, "VI Server Thread").start();
      EditContainer.registerListener(this);
   }

   /**
    * Main server loop that accepts incoming socket connections.
    * Reads file names from the remote client and opens them in the editor.
    *
    * <p>Note: The instream (BufferedReader) is intentionally NOT closed in the
    * normal path because closing it would also close the underlying socket,
    * which needs to remain open for the donefile() callback later. The socket
    * is stored in shash and will be closed by donefile() when the file is
    * written or disposed.</p>
    */
   public void run() {
      while (true) {
         Socket sock = null;
         BufferedReader instream = null;
         try {
            sock = lsock.accept();

            instream = new BufferedReader(
               new InputStreamReader(sock.getInputStream(), "UTF-8"));

            if (1 != instream.read())  {
               throw new InputException("invalid byte from remote");
            }

            EventQueue.biglock2.lock();
            EditContainer ed = FileList.openFileList(instream, null);
            if (null != ed) {
               trace("save socket");
               shash.put(ed, sock);
               UI.toFront();
            }
         } catch (Throwable e) {
            trace("server.run caught exception " + e);
            e.printStackTrace();
            try {
               //trace("closing socket");
               if (null != sock)
                  sock.close();
            } catch (IOException e1) {
               trace("caught exception while trying to close" + e1);
               e1.printStackTrace();
            }
         } finally {
            if (EventQueue.biglock2.isHeldByCurrentThread())
               EventQueue.biglock2.unlock();
///!!!!!!!!!!!!!!!!!!!!!!!!!!
// for some reason closing the inputstream seems to close the entire socket
// hope this doesn't cause any leaks.  It used to work.
//            try {
//      trace("closing instream");
//               if (null != instream)
//                  instream.close();
//            } catch (IOException e) {
//               trace("caught exception while trying to close" + e);
//               e.printStackTrace();
//            }
         }
      }
   }

   /**
    * Notifies the remote client that the file edit is complete.
    * Sends a response byte and closes the socket connection.
    *
    * @param ev the EditContainer whose file has been processed
    */
   void donefile(EditContainer ev) {
      //trace("server.donefile entered " + ev);
      Socket outsock = shash.get(ev);
      if (null == outsock)
         return;
      // Note: closing outsock also closes the output stream, but we use
      // try-with-resources for proper cleanup ordering (flush before close)
      try (Socket sockToClose = outsock;
           BufferedOutputStream outstream =
               new BufferedOutputStream(outsock.getOutputStream())) {
         outstream.write('a');
         outstream.write('\r');
         outstream.write('\n');
         outstream.flush();
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
