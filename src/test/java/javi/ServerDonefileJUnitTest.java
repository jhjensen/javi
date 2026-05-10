package javi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Server donefile wire protocol and FileStatusListener
 * callbacks using real socket pairs.
 *
 * <p>Verifies that when a file edit is completed, the Server sends
 * the expected {@code a\r\n} response to the originating client
 * socket. Tests also verify that the socket is properly closed
 * and the entry removed from the internal hash map.</p>
 *
 * <p>These tests use real sockets on localhost but do NOT require
 * a display (no GUI). The Server's run() loop is not exercised;
 * instead, socket entries are injected via reflection to isolate
 * the donefile/fileDisposed/fileWritten protocol paths.</p>
 */
class ServerDonefileJUnitTest {

   private Server server;
   private int port;
   private HashMap<EditContainer, Socket> shash;
   private static int ecCounter;

   /**
    * Creates a fresh EditContainer for use as a shash key.
    * Each call returns a distinct instance.
    * Must acquire biglock2 because TextEdit constructor calls
    * registeruniq() which asserts the lock is held.
    */
   private EditContainer makeTestEC() {
      String name = "ju_srvdf_" + (ecCounter++);
      String path = history.Testutil.testFile(name).getPath();
      FileDescriptor fd = FileDescriptor.make(path);
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      EventQueue.biglock2.lock();
      try {
         return new TextEdit<>(fi, fp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void setUp() throws Exception {
      for (int attempt = 0; attempt < 5; attempt++) {
         try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
         }
         EventQueue.biglock2.lock();
         try {
            server = new Server(port);
            break;
         } catch (java.net.BindException e) {
            if (attempt == 4)
               throw e;
            Thread.sleep(100);
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      // Access the internal shash map via reflection
      Field shashField = Server.class.getDeclaredField("shash");
      shashField.setAccessible(true);
      @SuppressWarnings("unchecked")
      HashMap<EditContainer, Socket> map =
         (HashMap<EditContainer, Socket>) shashField.get(server);
      shash = map;
   }

   @AfterEach
   void tearDown() throws Exception {
      // Close the server socket
      Field f = Server.class.getDeclaredField("lsock");
      f.setAccessible(true);
      java.net.ServerSocket ss = (java.net.ServerSocket) f.get(server);
      if (ss != null)
         ss.close();
      Thread.sleep(200);
   }

   /**
    * Creates a connected socket pair on localhost and returns
    * both ends. Index 0 is the "client" side (reads response),
    * index 1 is the "server" side (stored in shash).
    */
   private Socket[] createSocketPair() throws Exception {
      ServerSocket tmpServer = new ServerSocket(0);
      int tmpPort = tmpServer.getLocalPort();
      Socket client = new Socket("127.0.0.1", tmpPort);
      Socket serverSide = tmpServer.accept();
      tmpServer.close();
      return new Socket[] {client, serverSide};
   }

   // ── donefile protocol response ───────────────────────────────

   @Test
   void donefileSendsExpectedBytes() throws Exception {
      Socket[] pair = createSocketPair();
      try {
         EditContainer ec = makeTestEC();
         shash.put(ec, pair[1]);

         server.donefile(ec);

         // Read the response from the client side
         InputStream in = pair[0].getInputStream();
         byte[] buf = new byte[3];
         int read = in.read(buf);
         assertEquals(3, read, "Should receive exactly 3 bytes");
         assertArrayEquals(new byte[] {'a', '\r', '\n'}, buf,
            "Response should be 'a\\r\\n'");
      } finally {
         pair[0].close();
      }
   }

   @Test
   void donefileRemovesEntryFromHash() throws Exception {
      Socket[] pair = createSocketPair();
      try {
         EditContainer ec = makeTestEC();
         shash.put(ec, pair[1]);
         assertTrue(shash.containsKey(ec));

         server.donefile(ec);

         assertFalse(shash.containsKey(ec),
            "Entry should be removed after donefile");
      } finally {
         pair[0].close();
      }
   }

   @Test
   void donefileClosesServerSocket() throws Exception {
      Socket[] pair = createSocketPair();
      EditContainer ec = makeTestEC();
      shash.put(ec, pair[1]);

      server.donefile(ec);

      assertTrue(pair[1].isClosed(),
         "Server-side socket should be closed after donefile");
      pair[0].close();
   }

   @Test
   void donefileNoOpForUnknownEditContainer() throws Exception {
      // donefile with an EC not in shash should be safe
      EditContainer ec = makeTestEC();
      assertFalse(shash.containsKey(ec));
      server.donefile(ec); // should not throw
   }

   @Test
   void donefileCalledTwiceIsIdempotent() throws Exception {
      Socket[] pair = createSocketPair();
      try {
         EditContainer ec = makeTestEC();
         shash.put(ec, pair[1]);

         server.donefile(ec);
         server.donefile(ec); // second call — entry already removed
         // Should not throw
         assertFalse(shash.containsKey(ec));
      } finally {
         pair[0].close();
      }
   }

   // ── FileStatusListener callbacks ─────────────────────────────

   @Test
   void fileDisposedCallsDonefileAndReturnsFalse() throws Exception {
      Socket[] pair = createSocketPair();
      try {
         EditContainer ec = makeTestEC();
         shash.put(ec, pair[1]);

         boolean result = server.fileDisposed(ec);

         assertFalse(result);
         assertFalse(shash.containsKey(ec),
            "fileDisposed should remove entry");

         // Verify response was sent
         InputStream in = pair[0].getInputStream();
         byte[] buf = new byte[3];
         int read = in.read(buf);
         assertEquals(3, read);
         assertEquals('a', buf[0]);
      } finally {
         pair[0].close();
      }
   }

   @Test
   void fileWrittenCallsDonefile() throws Exception {
      Socket[] pair = createSocketPair();
      try {
         EditContainer ec = makeTestEC();
         shash.put(ec, pair[1]);

         server.fileWritten(ec);

         assertFalse(shash.containsKey(ec),
            "fileWritten should remove entry via donefile");

         InputStream in = pair[0].getInputStream();
         byte[] buf = new byte[3];
         int read = in.read(buf);
         assertEquals(3, read);
         assertArrayEquals(new byte[] {'a', '\r', '\n'}, buf);
      } finally {
         pair[0].close();
      }
   }

   @Test
   void fileAddedDoesNothing() {
      EditContainer ec = makeTestEC();
      int sizeBefore = shash.size();
      server.fileAdded(ec);
      assertEquals(sizeBefore, shash.size(),
         "fileAdded should not modify shash");
   }

   // ── Edge cases ───────────────────────────────────────────────

   @Test
   void donefileWithAlreadyClosedSocket() throws Exception {
      Socket[] pair = createSocketPair();
      EditContainer ec = makeTestEC();
      pair[1].close(); // close before donefile
      shash.put(ec, pair[1]);

      // Should handle IOException gracefully
      server.donefile(ec);
      pair[0].close();
   }

   @Test
   void multipleEditContainersIndependent() throws Exception {
      Socket[] pair1 = createSocketPair();
      Socket[] pair2 = createSocketPair();
      try {
         // Two distinct EditContainers with separate sockets
         EditContainer ec1 = makeTestEC();
         shash.put(ec1, pair1[1]);

         // Process first entry, verify response
         server.donefile(ec1);

         // Verify first response
         InputStream in1 = pair1[0].getInputStream();
         byte[] buf = new byte[3];
         assertEquals(3, in1.read(buf));
         assertEquals('a', buf[0]);

         // Re-add same EC with a new socket
         shash.put(ec1, pair2[1]);
         server.donefile(ec1);

         InputStream in2 = pair2[0].getInputStream();
         assertEquals(3, in2.read(buf));
         assertArrayEquals(new byte[] {'a', '\r', '\n'}, buf);
      } finally {
         pair1[0].close();
         pair2[0].close();
      }
   }
}
