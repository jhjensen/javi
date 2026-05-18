package javi;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import static history.Tools.trace;

/**
 * Server plugin for handling external file open requests.
 *
 * <p>Allows other processes to request Javi to open files via socket connection.
 * Used primarily for integrating Javi as an external editor with other tools.
 * This is an optional plugin &mdash; load it with {@code :loadclass javi.Server}.</p>
 *
 * <p>The server port defaults to 6001 and can be configured via the system
 * property {@code javi.server.port}. Once loaded, use the {@code :server}
 * command to query status, stop, or restart on a different port.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>The server runs in its own thread and acquires {@link EventQueue#biglock2}
 * only when opening files. The socket hash is protected by this class's implicit
 * synchronization through single-threaded access patterns.</p>
 *
 * @see EditContainer.FileStatusListener
 */
final class Server implements Runnable, EditContainer.FileStatusListener, Plugin {

   /** Plugin descriptor for the plugin loader. */
   public static final String pluginInfo = "external file-open server";

   /** Default listening port. */
   private static final int DEFAULT_PORT = 6001;

   /** The singleton instance, null if not started. */
   private static volatile Server instance;

   static {
      // Register the :server command
      new Rgroup() {
         {
            registerArgCommand("server",
               "start/stop/status external file server", "misc",
               (arg, count, rcount, fvc, dot) -> {
                  serverCommand(
                     arg instanceof String ? (String) arg : null);
                  return null;
               });
         }
         protected Object doroutine(int rnum, Object arg, int count,
               int rcount, FvContext fvc, boolean dotmode) {
            throw new UnsupportedOperationException();
         }
      };

      // Start server on configured port
      int port = DEFAULT_PORT;
      String portProp = System.getProperty("javi.server.port");
      if (portProp != null) {
         try {
            port = Integer.parseInt(portProp);
         } catch (NumberFormatException e) {
            trace("invalid javi.server.port: " + portProp);
         }
      }
      try {
         instance = new Server(port);
      } catch (IOException e) {
         trace("error starting Server: " + e);
      }
   }

   private HashMap<EditContainer, Socket> shash =
      new HashMap<>(10);

   private ServerSocket lsock;
   private final int port;
   private volatile boolean stopped;

   Server(int port) throws IOException {
      this.port = port;
      lsock = new ServerSocket(port);
      Thread t = new Thread(this, "VI Server Thread");
      t.setDaemon(true);
      t.start();
      EditContainer.registerListener(this);
   }

   void close() {
      stopped = true;
      try {
         lsock.close();
      } catch (IOException e) {
         // ignore
      }
   }

   /**
    * Stop the server, closing the listening socket.
    * The server thread exits cleanly on the next accept() attempt.
    */
   void stop() {
      stopped = true;
      try {
         lsock.close();
      } catch (IOException e) {
         // ignore
      }
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
      while (!stopped) {
         Socket sock = null;
         try {
            sock = lsock.accept();

            BufferedReader instream = new BufferedReader(
               new InputStreamReader(sock.getInputStream(),
                  StandardCharsets.UTF_8));

            if (1 != instream.read()) {
               throw new InputException("invalid byte from remote");
            }

            EventQueue.biglock2.lock();
            EditContainer ed = FileList.openFileList(instream, null);
            if (null != ed) {
               trace("save socket");
               shash.put(ed, sock);
               UI.toFront();
            }
         } catch (SocketException e) {
            if (stopped || lsock.isClosed())
               break; // clean shutdown
            trace("server.run socket exception", e);
         } catch (Throwable e) {
            if (stopped)
               break;
            trace("server.run caught exception", e);
            if (!(e instanceof IOException))
               e.printStackTrace();
            try {
               if (null != sock)
                  sock.close();
            } catch (IOException e1) {
               trace("caught exception while trying to close socket", e1);
            }
         } finally {
            if (EventQueue.biglock2.isHeldByCurrentThread())
               EventQueue.biglock2.unlock();
         }
      }
      trace("Server thread exiting");
   }

   /**
    * Handle the :server command.
    *
    * @param arg subcommand: "start", "stop", "status", or a port number
    */
   private static void serverCommand(String arg) {
      if (arg == null || arg.isEmpty() || "status".equals(arg)) {
         if (instance != null && !instance.stopped)
            UI.reportMessage("server running on port " + instance.port);
         else
            UI.reportMessage("server not running");
      } else if ("stop".equals(arg)) {
         if (instance != null) {
            instance.stop();
            instance = null;
         }
         UI.reportMessage("server stopped");
      } else if ("start".equals(arg)) {
         startServer(DEFAULT_PORT);
      } else {
         try {
            int p = Integer.parseInt(arg);
            startServer(p);
         } catch (NumberFormatException e) {
            UI.reportError("server: unknown arg: " + arg
               + " (use start, stop, status, or port number)");
         }
      }
   }

   private static void startServer(int port) {
      if (instance != null && !instance.stopped) {
         UI.reportMessage(
            "server already running on port " + instance.port);
         return;
      }
      try {
         instance = new Server(port);
         UI.reportMessage("server started on port " + port);
      } catch (IOException e) {
         UI.reportError("server start failed: " + e.getMessage());
      }
   }

   /**
    * Notifies the remote client that the file edit is complete.
    * Sends a response byte and closes the socket connection.
    *
    * @param ev the EditContainer whose file has been processed
    */
   void donefile(EditContainer ev) {
      Socket outsock = shash.get(ev);
      if (null == outsock)
         return;
      try (outsock) {
         BufferedOutputStream outstream =
               new BufferedOutputStream(outsock.getOutputStream());
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
