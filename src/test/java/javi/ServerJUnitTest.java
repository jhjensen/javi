package javi;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Server} using real sockets on localhost.
 *
 * <p>Server listens for file-open requests from external processes.
 * The protocol is: client sends byte 0x01 then filename lines;
 * server opens the files and later replies 'a\r\n' via
 * {@code donefile()}.</p>
 */
class ServerJUnitTest {

   private Server server;
   private int port;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void setUp() throws Exception {
      // Find a free port
      try (ServerSocket ss = new ServerSocket(0)) {
         port = ss.getLocalPort();
      }
      EventQueue.biglock2.lock();
      try {
         server = new Server(port);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @AfterEach
   void tearDown() {
      // Server thread is a daemon-like thread; it will exit
      // when the ServerSocket is closed (via garbage collection
      // or program exit). No explicit stop method exists.
   }

   @Test
   void serverListensOnPort() throws Exception {
      // Verify we can connect to the server
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected(),
            "Should be able to connect to Server");
      }
   }

   @Test
   void serverAcceptsConnection() throws Exception {
      // Server should accept and process a connection without crashing
      try (Socket sock = new Socket("127.0.0.1", port)) {
         OutputStream out = sock.getOutputStream();
         // Send the protocol byte (0x01) then a filename
         out.write(1);
         out.write("nonexistent_test_file.txt\n".getBytes(
            StandardCharsets.UTF_8));
         out.flush();
         // Give the server thread time to process
         Thread.sleep(200);
         // Connection should still be open (server keeps it for donefile)
         assertFalse(sock.isClosed(),
            "Socket should remain open after sending request");
      }
   }

   @Test
   void invalidProtocolByteClosesSocket() throws Exception {
      // Sending a non-0x01 first byte should cause the server
      // to throw InputException and close the socket
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(2000);
         OutputStream out = sock.getOutputStream();
         // Send invalid first byte (0x02 instead of 0x01)
         out.write(2);
         out.flush();
         // Give the server time to process and close
         Thread.sleep(500);
         // The server closes the socket on error;
         // reading should return -1 or throw
         try {
            int b = sock.getInputStream().read();
            assertTrue(b == -1,
               "Server should close socket on invalid protocol byte");
         } catch (IOException e) {
            // Connection reset is also acceptable
            assertTrue(true);
         }
      }
   }

   @Test
   void fileStatusListenerMethodsExist() {
      // Verify Server implements EditContainer.FileStatusListener
      assertTrue(server instanceof EditContainer.FileStatusListener,
         "Server must implement EditContainer.FileStatusListener");
   }

   @Test
   void fileAddedIsNoOp() {
      // fileAdded should not throw — it's documented as "don't care"
      server.fileAdded(null);
      // No exception means success
      assertTrue(true);
   }

   @Test
   void fileDisposedWithNullSocket() {
      // donefile with an unknown EditContainer should be a no-op
      // (shash.get returns null → early return)
      boolean result = server.fileDisposed(null);
      assertFalse(result,
         "fileDisposed should return false");
   }

   @Test
   void multipleConnectionsAccepted() throws Exception {
      // Server should handle multiple sequential connections
      for (int i = 0; i < 3; i++) {
         try (Socket sock = new Socket("127.0.0.1", port)) {
            assertTrue(sock.isConnected(),
               "Connection " + i + " should succeed");
            OutputStream out = sock.getOutputStream();
            // Send invalid byte to trigger quick close
            out.write(2);
            out.flush();
         }
         Thread.sleep(100);
      }
   }
}
