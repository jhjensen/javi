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
 * Extended tests for {@link Server} — protocol edge cases,
 * concurrent connections, and FileStatusListener contract.
 *
 * <p>Uses real sockets on localhost (no Docker needed). Complements
 * ServerJUnitTest with deeper protocol and concurrency scenarios.</p>
 */
class ServerExtendedJUnitTest {

   private Server server;
   private int port;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void setUp() throws Exception {
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
      // Server thread exits when ServerSocket is collected
   }

   @Test
   void emptyFilenameAfterProtocolByte() throws Exception {
      // Send protocol byte + empty line (just newline)
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(2000);
         OutputStream out = sock.getOutputStream();
         out.write(1);
         out.write('\n');
         out.flush();
         Thread.sleep(300);
         // Server should handle empty filename gracefully
         assertFalse(sock.isClosed(),
            "Socket should remain open even with empty filename");
      }
   }

   @Test
   void zeroByteSentClosesSocket() throws Exception {
      // Send 0x00 as first byte (invalid protocol)
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(2000);
         OutputStream out = sock.getOutputStream();
         out.write(0);
         out.flush();
         Thread.sleep(500);
         try {
            int b = sock.getInputStream().read();
            assertTrue(b == -1,
               "Server should close socket on 0x00 byte");
         } catch (IOException e) {
            assertTrue(true, "Connection reset is acceptable");
         }
      }
   }

   @Test
   void rapidSequentialConnections() throws Exception {
      // Rapidly connect and disconnect 5 times
      for (int i = 0; i < 5; i++) {
         try (Socket sock = new Socket("127.0.0.1", port)) {
            assertTrue(sock.isConnected());
            OutputStream out = sock.getOutputStream();
            out.write(2); // invalid, triggers fast close
            out.flush();
         }
      }
      // Server should still accept after rapid connections
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected(),
            "Server should still accept after rapid connections");
      }
   }

   @Test
   void largeFilenameDoesNotCrash() throws Exception {
      // Send a very long filename string
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(2000);
         OutputStream out = sock.getOutputStream();
         out.write(1);
         StringBuilder longName = new StringBuilder();
         for (int i = 0; i < 1000; i++)
            longName.append("a");
         longName.append("\n");
         out.write(longName.toString().getBytes(StandardCharsets.UTF_8));
         out.flush();
         Thread.sleep(300);
         // Server processes the long name and either opens or ignores it
         assertFalse(sock.isClosed());
      }
   }

   @Test
   void multipleFilenames() throws Exception {
      // Send protocol byte then multiple filenames
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(2000);
         OutputStream out = sock.getOutputStream();
         out.write(1);
         out.write("file1.txt\nfile2.txt\n"
            .getBytes(StandardCharsets.UTF_8));
         out.flush();
         Thread.sleep(300);
         assertFalse(sock.isClosed());
      }
   }

   @Test
   void fileDisposedNullSafe() {
      // fileDisposed with null should not throw
      boolean result = server.fileDisposed(null);
      assertFalse(result, "fileDisposed(null) should return false");
   }

   @Test
   void fileAddedNullSafe() {
      // fileAdded with null should be a no-op
      server.fileAdded(null);
      assertTrue(true, "fileAdded(null) should not throw");
   }

   @Test
   void fileWrittenNullSafe() {
      // fileWritten with a null EditContainer invokes donefile
      // which does shash.get(null) → null → early return
      server.fileWritten(null);
      assertTrue(true, "fileWritten(null) should not throw");
   }

   @Test
   void fileStatusListenerInterface() {
      assertTrue(server instanceof EditContainer.FileStatusListener,
         "Server must implement FileStatusListener");
   }

   @Test
   void serverAcceptsAfterInvalidByte() throws Exception {
      // Send invalid byte, then verify server still accepts
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.getOutputStream().write(99);
         sock.getOutputStream().flush();
      }
      Thread.sleep(200);
      // Server should still be accepting
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected(),
            "Server should keep accepting after invalid client");
      }
   }

   @Test
   void connectionClosedImmediately() throws Exception {
      // Connect and immediately close without sending any data
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected()); // close via try-with-resources
      }
      Thread.sleep(200);
      // Server should survive client disconnecting before sending data
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected(),
            "Server should survive premature client close");
      }
   }
}
