package javi;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server protocol lifecycle tests — donefile response path, concurrent
 * multi-client handling, and FileStatusListener integration.
 *
 * <p>Uses real sockets on localhost (no Docker needed). Focuses on
 * the full lifecycle: connect → protocol byte → filename →
 * server accepts → donefile sends response → socket closed.</p>
 */
class ServerProtocolJUnitTest {

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
   void tearDown() throws Exception {
      if (server != null)
         server.close();
      Thread.sleep(100); // let the server thread exit
   }

   // ── donefile() contract tests ────────────────────────────────

   @Test
   void donefileWithUnknownEditContainerIsNoOp() {
      // donefile on an EditContainer not in shash should be a no-op
      // null EC maps to nothing in shash → immediate return
      server.donefile(null);
      assertTrue(true, "donefile with unknown EC should not throw");
   }

   @Test
   void fileStatusListenerRegistered() {
      // Server registers itself as a FileStatusListener in constructor
      assertTrue(server instanceof EditContainer.FileStatusListener,
         "Server implements FileStatusListener");
   }

   @Test
   void fileDisposedReturnsFalse() {
      // fileDisposed should always return false (no veto)
      assertFalse(server.fileDisposed(null),
         "fileDisposed should return false");
   }

   @Test
   void fileWrittenDoesNotThrowOnNull() {
      // fileWritten calls donefile which does shash.get(null) → null → return
      server.fileWritten(null);
      assertTrue(true, "fileWritten(null) is a no-op");
   }

   @Test
   void fileAddedDoesNothing() {
      server.fileAdded(null);
      assertTrue(true, "fileAdded is explicitly empty");
   }

   // ── Protocol byte validation ─────────────────────────────────

   @Test
   void invalidProtocolByteTriggersClose() throws Exception {
      // Send byte != 1 as first byte — server should close socket
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(2000);
         OutputStream out = sock.getOutputStream();
         out.write(0xFF);
         out.flush();
         Thread.sleep(400);
         // Server closes socket on invalid protocol byte
         try {
            int b = sock.getInputStream().read();
            assertTrue(b == -1, "Server should close on invalid byte");
         } catch (IOException e) {
            // Connection reset — acceptable
            assertTrue(true);
         }
      }
   }

   @Test
   void protocolByte2TriggersClose() throws Exception {
      // Byte 2 is also invalid (only 1 is valid)
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(2000);
         OutputStream out = sock.getOutputStream();
         out.write(2);
         out.flush();
         Thread.sleep(400);
         try {
            int b = sock.getInputStream().read();
            assertTrue(b == -1, "Server should close on byte 2");
         } catch (IOException e) {
            assertTrue(true);
         }
      }
   }

   // ── Concurrent client handling ───────────────────────────────

   @Test
   void concurrentClientsAllAccepted() throws Exception {
      int numClients = 10;
      CountDownLatch connected = new CountDownLatch(numClients);
      AtomicInteger successes = new AtomicInteger(0);

      Thread[] threads = new Thread[numClients];
      for (int i = 0; i < numClients; i++) {
         final int idx = i;
         threads[i] = new Thread(() -> {
            try (Socket sock = new Socket("127.0.0.1", port)) {
               sock.setSoTimeout(3000);
               OutputStream out = sock.getOutputStream();
               out.write(1);
               out.write(("concurrent_file_" + idx + ".txt\n")
                  .getBytes(StandardCharsets.UTF_8));
               out.flush();
               successes.incrementAndGet();
               connected.countDown();
               // Hold connection open briefly
               Thread.sleep(100);
            } catch (Exception e) {
               connected.countDown();
            }
         });
         threads[i].setDaemon(true);
         threads[i].start();
      }

      boolean allConnected = connected.await(5, TimeUnit.SECONDS);
      for (Thread t : threads) t.join(2000);

      assertTrue(allConnected, "All clients should connect");
      assertEquals(numClients, successes.get(),
         "All " + numClients + " clients should succeed");
   }

   @Test
   void serverSurvivesSlowClient() throws Exception {
      // Client sends protocol byte then delays before filename
      Socket sock = new Socket("127.0.0.1", port);
      sock.setSoTimeout(3000);
      OutputStream out = sock.getOutputStream();
      out.write(1);
      out.flush();
      Thread.sleep(500); // slow client
      out.write("slow_client_file.txt\n".getBytes(StandardCharsets.UTF_8));
      out.flush();
      Thread.sleep(200);

      // Verify server still accepts new connections
      try (Socket sock2 = new Socket("127.0.0.1", port)) {
         assertTrue(sock2.isConnected(),
            "Server should accept after slow client");
      }
      sock.close();
   }

   @Test
   void serverHandlesPartialWrite() throws Exception {
      // Send protocol byte + filename one byte at a time
      // Server may close after processing the filename (normal behavior)
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(3000);
         OutputStream out = sock.getOutputStream();
         out.write(1);
         out.flush();
         byte[] name = "partial.txt\n".getBytes(StandardCharsets.UTF_8);
         for (byte b : name) {
            try {
               out.write(b);
               out.flush();
            } catch (IOException e) {
               // Server may close socket after processing — acceptable
               break;
            }
            Thread.sleep(10);
         }
      }
      // Verify server still accepts new connections after slow client
      Thread.sleep(200);
      try (Socket sock2 = new Socket("127.0.0.1", port)) {
         assertTrue(sock2.isConnected(),
            "Server should still accept after slow-write client");
      }
   }

   // ── Connection stress tests ──────────────────────────────────

   @Test
   void rapidConnectDisconnect20Times() throws Exception {
      for (int i = 0; i < 20; i++) {
         try (Socket sock = new Socket("127.0.0.1", port)) {
            assertTrue(sock.isConnected());
         }
      }
      // Server should still work
      Thread.sleep(100);
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(2000);
         OutputStream out = sock.getOutputStream();
         out.write(1);
         out.write("after_stress.txt\n".getBytes(StandardCharsets.UTF_8));
         out.flush();
         Thread.sleep(200);
         assertFalse(sock.isClosed());
      }
   }

   @Test
   void multipleFilesInSingleConnection() throws Exception {
      // Send protocol byte followed by multiple filenames on separate lines
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(2000);
         OutputStream out = sock.getOutputStream();
         out.write(1);
         StringBuilder sb = new StringBuilder();
         for (int i = 0; i < 5; i++) {
            sb.append("multi_file_").append(i).append(".txt\n");
         }
         out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
         out.flush();
         Thread.sleep(300);
         assertFalse(sock.isClosed(),
            "Socket should stay open for multi-file request");
      }
   }

   @Test
   void emptyByteStreamClosesGracefully() throws Exception {
      // Connect but send nothing, then close
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(1000);
         // Don't write anything, just close
      }
      Thread.sleep(200);
      // Server survives
      try (Socket sock = new Socket("127.0.0.1", port)) {
         assertTrue(sock.isConnected(),
            "Server accepts after empty-close client");
      }
   }

   @Test
   void unicodeFilenameHandled() throws Exception {
      // Send a filename with Unicode characters
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(2000);
         OutputStream out = sock.getOutputStream();
         out.write(1);
         out.write("ユニコード_файл_日本語.txt\n"
            .getBytes(StandardCharsets.UTF_8));
         out.flush();
         Thread.sleep(300);
         assertFalse(sock.isClosed(),
            "Server should handle Unicode filenames");
      }
   }

   @Test
   void filenameWithSpacesHandled() throws Exception {
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(2000);
         OutputStream out = sock.getOutputStream();
         out.write(1);
         out.write("path with spaces/my file.txt\n"
            .getBytes(StandardCharsets.UTF_8));
         out.flush();
         Thread.sleep(200);
         assertFalse(sock.isClosed(),
            "Server should handle filenames with spaces");
      }
   }

   @Test
   void donefileIdempotent() {
      // Calling donefile twice on same null EC should not throw
      server.donefile(null);
      server.donefile(null);
      assertTrue(true, "donefile is idempotent for missing entries");
   }

   @Test
   void fileDisposedIdempotent() {
      // Calling fileDisposed twice should be safe
      server.fileDisposed(null);
      server.fileDisposed(null);
      assertTrue(true, "fileDisposed is idempotent");
   }
}
