package javi.awt;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;

import javi.EventQueue;
import javi.TestInit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AwtCircBuffer} — the AWT clipboard-integrated
 * circular buffer. Exercises the Transferable interface, clipboard
 * enable/disable, and data flavour support.
 */
class AwtCircBufferJUnitTest {

   private static AwtCircBuffer cbuf;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
      TestInit.initCommands();
      EventQueue.biglock2.lock();
      try {
         AwtCircBuffer.initCmd();
         // retrieve singleton via reflection
         java.lang.reflect.Field f =
            AwtCircBuffer.class.getDeclaredField("cbuf");
         f.setAccessible(true);
         cbuf = (AwtCircBuffer) f.get(null);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @BeforeEach
   void lock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void unlock() {
      EventQueue.biglock2.unlock();
   }

   // ── Transferable interface ────────────────────────────────

   @Nested
   @DisplayName("Transferable interface")
   class TransferableTests {

      @Test
      @DisplayName("getTransferDataFlavors returns stringFlavor")
      void flavorsContainStringFlavor() {
         DataFlavor[] flavors = cbuf.getTransferDataFlavors();
         assertNotNull(flavors);
         assertEquals(1, flavors.length);
         assertEquals(DataFlavor.stringFlavor, flavors[0]);
      }

      @Test
      @DisplayName("isDataFlavorSupported true for stringFlavor")
      void supportsStringFlavor() {
         assertTrue(cbuf.isDataFlavorSupported(
            DataFlavor.stringFlavor));
      }

      @Test
      @DisplayName("isDataFlavorSupported false for imageFlavor")
      void doesNotSupportImageFlavor() {
         assertFalse(cbuf.isDataFlavorSupported(
            DataFlavor.imageFlavor));
      }

      @Test
      @DisplayName("getTransferData returns empty string when empty")
      void transferDataEmptyWhenNoContent()
            throws Exception {
         // flush is package-private in CircBuffer; use reflection
         java.lang.reflect.Method flush =
            cbuf.getClass().getSuperclass()
               .getDeclaredMethod("flush");
         flush.setAccessible(true);
         flush.invoke(cbuf);
         Object data = cbuf.getTransferData(
            DataFlavor.stringFlavor);
         assertEquals("", data);
      }

      @Test
      @DisplayName("getTransferData returns buffer content")
      void transferDataReturnsBufferContent()
            throws Exception {
         cbuf.add("test-data");
         Object data = cbuf.getTransferData(
            DataFlavor.stringFlavor);
         assertEquals("test-data", data);
      }

      @Test
      @DisplayName("getTransferData throws for unsupported flavor")
      void transferDataThrowsForBadFlavor() {
         assertThrows(UnsupportedFlavorException.class,
            () -> cbuf.getTransferData(DataFlavor.imageFlavor));
      }
   }

   // ── ClipboardOwner ────────────────────────────────────────

   @Nested
   @DisplayName("ClipboardOwner")
   class ClipboardOwnerTests {

      @Test
      @DisplayName("lostOwnership does not throw")
      void lostOwnershipDoesNotThrow() {
         assertDoesNotThrow(
            () -> cbuf.lostOwnership(null, null));
      }
   }

   // ── Clipboard enable/disable ──────────────────────────────

   @Nested
   @DisplayName("Clipboard enable/disable")
   class ClipboardToggle {

      @Test
      @DisplayName("enableClip off disables clipboard")
      void disableClipboard() throws Exception {
         cbuf.enableClip("off");
         java.lang.reflect.Field f =
            AwtCircBuffer.class.getDeclaredField("systemclip");
         f.setAccessible(true);
         Object clip = f.get(cbuf);
         // after disabling, systemclip should be null
         assertEquals(null, clip);
      }

      @Test
      @DisplayName("enableClip on re-enables clipboard")
      void enableClipboard() throws Exception {
         cbuf.enableClip("off");
         cbuf.enableClip("on");
         java.lang.reflect.Field f =
            AwtCircBuffer.class.getDeclaredField("systemclip");
         f.setAccessible(true);
         Object clip = f.get(cbuf);
         assertNotNull(clip, "systemclip should be restored");
      }

      @Test
      @DisplayName("setclip with null clipboard is no-op")
      void setclipNullClipboardNoOp() {
         cbuf.enableClip("off");
         assertDoesNotThrow(() -> cbuf.setclip());
      }

      @Test
      @DisplayName("getclip with null clipboard is no-op")
      void getclipNullClipboardNoOp() {
         cbuf.enableClip("off");
         assertDoesNotThrow(() -> cbuf.getclip());
      }
   }

   // ── initCmd singleton ─────────────────────────────────────

   @Test
   @DisplayName("initCmd creates singleton and registers with Buffers")
   void initCmdCreatesSingleton() {
      assertNotNull(cbuf, "cbuf singleton should exist");
      cbuf.add("singleton-test");
      assertEquals("singleton-test", cbuf.get(0));
   }
}
