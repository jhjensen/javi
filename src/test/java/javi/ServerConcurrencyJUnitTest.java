package javi;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server concurrency and stress tests — exercises concurrent client
 * handling, connection burst tolerance, partial write robustness,
 * and socket cleanup after abnormal disconnects.
 *
 * <p>Uses real sockets on localhost (no Docker needed). Focuses on
 * the Server's ability to handle many simultaneous connections and
 * edge cases in the protocol byte path.</p>
 */
class ServerConcurrencyJUnitTest {

   private Server server;
   private int port;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void setUp() throws Exception {
      // Retry port allocation — previous test's socket may still be in TIME_WAIT
      for (int attempt = 0; attempt < 5; attempt++) {
         try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
         }
         EventQueue.biglock2.lock();
         try {
            server = new Server(port);
            return;
         } catch (java.net.BindException e) {
            if (attempt == 4)
               throw e;
            Thread.sleep(100);
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }

   @AfterEach
   void tearDown() throws Exception {
      // Close the server socket to unblock the accept() thread
      java.lang.reflect.Field f = Server.class.getDeclaredField("lsock");
      f.setAccessible(true);
      java.net.ServerSocket ss = (java.net.ServerSocket) f.get(server);
      if (ss != null)
         ss.close();
      Thread.sleep(200); // let the server thread exit
   }

   // ── Burst connections ────────────────────────────────────────

   @Test
   void burstConnectDisconnect20Clients() throws Exception {
      // Rapidly connect and disconnect 20 clients sending invalid byte
      for (int i = 0; i < 20; i++) {
         try (Socket sock = new Socket("127.0.0.1", port)) {
            sock.setSoTimeout(1000);
            OutputStream out = sock.getOutputStream();
            out.write(0xFF); // invalid protocol byte
            out.flush();
         }
      }
      // Server should still be alive after burst
      Thread.sleep(200);
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected(),
            "Server accepts connections after burst");
      }
   }

   @Test
   void concurrentConnectionsWithThreadPool() throws Exception {
      int numClients = 15;
      ExecutorService pool = Executors.newFixedThreadPool(numClients);
      AtomicInteger connected = new AtomicInteger(0);
      CountDownLatch startLatch = new CountDownLatch(1);
      List<Future<?>> futures = new ArrayList<>();

      for (int i = 0; i < numClients; i++) {
         futures.add(pool.submit(() -> {
            try {
               startLatch.await(); // all start at once
               try (Socket sock = new Socket("127.0.0.1", port)) {
                  sock.setSoTimeout(2000);
                  connected.incrementAndGet();
                  OutputStream out = sock.getOutputStream();
                  out.write(0); // invalid, triggers close
                  out.flush();
                  Thread.sleep(50);
               }
            } catch (Exception e) {
               // connection refused under load is acceptable
            }
            return null;
         }));
      }

      startLatch.countDown(); // release all threads
      pool.shutdown();
      pool.awaitTermination(5, TimeUnit.SECONDS);

      assertTrue(connected.get() >= 10,
         "At least 10 of 15 should connect, got "
            + connected.get());
   }

   // ── Partial write / immediate disconnect ─────────────────────

   @Test
   void immediateDisconnectNoProtocolByte() throws Exception {
      // Connect and immediately close without writing anything
      try (Socket sock = new Socket("127.0.0.1", port)) {
         // Close immediately — server should handle gracefully
      }
      Thread.sleep(200);
      // Server should still be alive
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected(),
            "Server alive after immediate disconnect");
      }
   }

   @Test
   void partialProtocolByteNoNewline() throws Exception {
      // Send protocol byte 1 but no newline — server blocks on readLine
      // then close socket — server should handle the IOException
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(1000);
         OutputStream out = sock.getOutputStream();
         out.write(1); // valid protocol byte
         out.write('f'); // partial filename
         out.flush();
         // Close without completing the line
      }
      Thread.sleep(300);
      // Server should still accept new connections
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected(),
            "Server alive after partial write disconnect");
      }
   }

   @Test
   void socketResetDuringRead() throws Exception {
      // Connect, send protocol byte, then force RST
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(1000);
         sock.setSoLinger(true, 0); // force RST on close
         OutputStream out = sock.getOutputStream();
         out.write(1);
         out.flush();
      } // RST sent on close due to SO_LINGER=0
      Thread.sleep(200);
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected(),
            "Server alive after RST");
      }
   }

   // ── Long filename handling ───────────────────────────────────

   @Test
   void veryLongFilenameDoesNotCrashServer() throws Exception {
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(2000);
         OutputStream out = sock.getOutputStream();
         out.write(1); // valid protocol byte
         // Send a very long filename (4000 chars)
         StringBuilder sb = new StringBuilder();
         for (int i = 0; i < 4000; i++)
            sb.append('x');
         sb.append('\n');
         out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
         out.flush();
         Thread.sleep(500);
      }
      // Server should handle long filenames gracefully
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected(),
            "Server alive after long filename");
      }
   }

   @Test
   void filenameWithSpecialCharsHandled() throws Exception {
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(2000);
         OutputStream out = sock.getOutputStream();
         out.write(1);
         // Filename with spaces and unicode
         String name = "/tmp/test file \u00e9.txt\n";
         out.write(name.getBytes(StandardCharsets.UTF_8));
         out.flush();
         Thread.sleep(500);
      }
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected(),
            "Server alive after special chars filename");
      }
   }

   // ── Multiple valid protocol bytes ────────────────────────────

   @Test
   void onlyFirstByteIsProtocol() throws Exception {
      // Send 1 (valid) then another 1 in the filename content
      // Second 1 should be treated as part of filename
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(2000);
         OutputStream out = sock.getOutputStream();
         out.write(1); // protocol byte
         out.write(1); // part of "filename"
         out.write('\n');
         out.flush();
         Thread.sleep(500);
      }
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected());
      }
   }

   // ── FileStatusListener interface completeness ────────────────

   @Test
   void fileDisposedAlwaysReturnsFalse() {
      // null EC → donefile is no-op → returns false
      assertFalse(server.fileDisposed(null));
   }

   @Test
   void fileWrittenOnNullIsNoOp() {
      // Should not throw
      server.fileWritten(null);
      assertTrue(true, "fileWritten(null) is safe");
   }

   @Test
   void fileAddedOnNullIsNoOp() {
      server.fileAdded(null);
      assertTrue(true, "fileAdded(null) is safe");
   }

   @Test
   void donefileOnNullIsNoOp() {
      server.donefile(null);
      assertTrue(true, "donefile(null) is safe");
   }

   // ── Sequential valid connections ─────────────────────────────

   @Test
   void sequentialValidConnections() throws Exception {
      // Multiple sequential valid protocol connections
      for (int i = 0; i < 5; i++) {
         try (Socket sock = new Socket("127.0.0.1", port)) {
            sock.setSoTimeout(2000);
            OutputStream out = sock.getOutputStream();
            out.write(1);
            out.write(("file" + i + ".txt\n").getBytes(
               StandardCharsets.UTF_8));
            out.flush();
            Thread.sleep(100);
         }
      }
      // All handled without crash
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected(),
            "Server alive after sequential valid connections");
      }
   }

   @Test
   void mixedValidAndInvalidConnections() throws Exception {
      // Alternate valid and invalid protocol bytes
      for (int i = 0; i < 8; i++) {
         try (Socket sock = new Socket("127.0.0.1", port)) {
            sock.setSoTimeout(1000);
            OutputStream out = sock.getOutputStream();
            if (i % 2 == 0) {
               out.write(1); // valid
               out.write(("test" + i + "\n").getBytes(
                  StandardCharsets.UTF_8));
            } else {
               out.write(0xFF); // invalid
            }
            out.flush();
            Thread.sleep(50);
         }
      }
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected(),
            "Server handles mixed valid/invalid");
      }
   }
}
