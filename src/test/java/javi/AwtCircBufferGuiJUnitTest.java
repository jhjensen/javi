package javi;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for {@code javi.awt.AwtCircBuffer} — the clipboard-integrated
 * circular buffer that manages yank/paste operations with the system
 * clipboard.
 *
 * <p>Exercises the Transferable/ClipboardOwner interfaces, data flavor
 * support, clipboard enable/disable, paste command, and the transfer
 * data extraction. Requires a display for clipboard access.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class AwtCircBufferGuiJUnitTest {

   private static Robot robot;
   private static Object awtCircBuf; // AwtCircBuffer instance
   private static Class<?> cbClass;

   @BeforeAll
   static void initJavi() throws Exception {
      if (Rgroup.bindingLookup("persistfile") == null) {
         EventQueue.biglock2.lock();
         try {
            Class.forName("javi.TextEdit");
            EditTester1.TestCircBuffer.initCmd();
            DirManager.getInstance();
            FileList.make("");
            Javi.initToUi();
            Javi.initPostUi();
            Command.doneInit();
         } finally {
            EventQueue.biglock2.unlock();
         }
         Thread.sleep(500);
      }
      robot = BasicRobot.robotWithCurrentAwtHierarchy();

      // Get the AwtCircBuffer singleton via reflection
      cbClass = Class.forName("javi.awt.AwtCircBuffer");
      Field cbufField = cbClass.getDeclaredField("cbuf");
      cbufField.setAccessible(true);
      awtCircBuf = cbufField.get(null);
      assertNotNull(awtCircBuf, "AwtCircBuffer singleton should exist");
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── Transferable interface ───────────────────────────────────

   @Test
   void t01_getTransferDataFlavorsReturnsStringFlavor() throws Exception {
      Method m = cbClass.getMethod("getTransferDataFlavors");
      DataFlavor[] flavors = (DataFlavor[]) m.invoke(awtCircBuf);
      assertNotNull(flavors);
      assertEquals(1, flavors.length,
         "Should support exactly one flavor");
      assertEquals(DataFlavor.stringFlavor, flavors[0],
         "Supported flavor should be stringFlavor");
   }

   @Test
   void t02_isDataFlavorSupportedString() throws Exception {
      Method m = cbClass.getMethod("isDataFlavorSupported",
         DataFlavor.class);
      boolean supported = (boolean) m.invoke(awtCircBuf,
         DataFlavor.stringFlavor);
      assertTrue(supported,
         "stringFlavor should be supported");
   }

   @Test
   void t03_isDataFlavorSupportedImageFalse() throws Exception {
      Method m = cbClass.getMethod("isDataFlavorSupported",
         DataFlavor.class);
      boolean supported = (boolean) m.invoke(awtCircBuf,
         DataFlavor.imageFlavor);
      assertFalse(supported,
         "imageFlavor should not be supported");
   }

   @Test
   void t04_getTransferDataInvalidFlavorThrows() throws Exception {
      Method m = cbClass.getMethod("getTransferData", DataFlavor.class);
      try {
         m.invoke(awtCircBuf, DataFlavor.imageFlavor);
         // If it doesn't throw via reflection, InvocationTargetException wraps it
         assertTrue(false, "Should have thrown");
      } catch (java.lang.reflect.InvocationTargetException e) {
         assertTrue(
            e.getCause() instanceof UnsupportedFlavorException,
            "Should throw UnsupportedFlavorException, got "
               + e.getCause().getClass().getName());
      }
   }

   @Test
   void t05_getTransferDataStringFlavorReturnsString() throws Exception {
      // Add something to the buffer first
      Method addM = cbClass.getSuperclass().getDeclaredMethod(
         "add", String.class);
      addM.setAccessible(true);
      addM.invoke(awtCircBuf, "clipboard test data");

      Method m = cbClass.getMethod("getTransferData", DataFlavor.class);
      Object result = m.invoke(awtCircBuf, DataFlavor.stringFlavor);
      assertNotNull(result, "Transfer data should not be null");
      assertTrue(result instanceof String,
         "Transfer data should be a String");
      assertTrue(result.toString().length() > 0,
         "Transfer data should not be empty");
   }

   // ── Clipboard enable/disable ─────────────────────────────────

   @Test
   void t06_enableClipOn() throws Exception {
      Method m = cbClass.getDeclaredMethod("enableClip", Object.class);
      m.setAccessible(true);
      m.invoke(awtCircBuf, "on");
      // Verify systemclip is set
      Field scField = cbClass.getDeclaredField("systemclip");
      scField.setAccessible(true);
      Object clip = scField.get(awtCircBuf);
      assertNotNull(clip,
         "systemclip should be non-null after enableClip(on)");
   }

   @Test
   void t07_enableClipOff() throws Exception {
      Method m = cbClass.getDeclaredMethod("enableClip", Object.class);
      m.setAccessible(true);
      m.invoke(awtCircBuf, "off");
      Field scField = cbClass.getDeclaredField("systemclip");
      scField.setAccessible(true);
      Object clip = scField.get(awtCircBuf);
      // After "off", systemclip should be null
      assertEquals(null, clip,
         "systemclip should be null after enableClip(off)");
      // Re-enable for subsequent tests
      m.invoke(awtCircBuf, "on");
   }

   // ── setclip / getclip ────────────────────────────────────────

   @Test
   void t08_setclipDoesNotCrash() throws Exception {
      Method m = cbClass.getDeclaredMethod("setclip");
      m.setAccessible(true);
      m.invoke(awtCircBuf); // should not throw
      assertNotNull(awtCircBuf);
   }

   @Test
   void t09_getclipDoesNotCrash() throws Exception {
      Method m = cbClass.getDeclaredMethod("getclip");
      m.setAccessible(true);
      m.invoke(awtCircBuf); // should not throw
      assertNotNull(awtCircBuf);
   }

   @Test
   void t10_setclipWithNullClipboardNoOp() throws Exception {
      // Disable clipboard, then setclip should be a no-op
      Method enableM = cbClass.getDeclaredMethod(
         "enableClip", Object.class);
      enableM.setAccessible(true);
      enableM.invoke(awtCircBuf, "off");

      Method setM = cbClass.getDeclaredMethod("setclip");
      setM.setAccessible(true);
      setM.invoke(awtCircBuf); // should not throw

      // Re-enable
      enableM.invoke(awtCircBuf, "on");
   }

   @Test
   void t11_getclipWithNullClipboardNoOp() throws Exception {
      Method enableM = cbClass.getDeclaredMethod(
         "enableClip", Object.class);
      enableM.setAccessible(true);
      enableM.invoke(awtCircBuf, "off");

      Method getM = cbClass.getDeclaredMethod("getclip");
      getM.setAccessible(true);
      getM.invoke(awtCircBuf); // should not throw

      enableM.invoke(awtCircBuf, "on");
   }

   // ── lostOwnership ────────────────────────────────────────────

   @Test
   void t12_lostOwnershipDoesNotCrash() throws Exception {
      Method m = cbClass.getMethod("lostOwnership",
         java.awt.datatransfer.Clipboard.class,
         java.awt.datatransfer.Transferable.class);
      m.invoke(awtCircBuf, (Object) null, (Object) null);
      assertNotNull(awtCircBuf);
   }

   // ── CircBuffer add and retrieve round-trip ───────────────────

   @Test
   void t13_addAndGetTransferDataRoundTrip() throws Exception {
      Method addM = cbClass.getSuperclass().getDeclaredMethod(
         "add", String.class);
      addM.setAccessible(true);
      addM.invoke(awtCircBuf, "round trip text");

      Method getM = cbClass.getMethod("getTransferData",
         DataFlavor.class);
      Object result = getM.invoke(awtCircBuf, DataFlavor.stringFlavor);
      assertEquals("round trip text", result.toString(),
         "getTransferData should return most recently added text");
   }

   @Test
   void t14_addMultipleRetrievesLatest() throws Exception {
      Method addM = cbClass.getSuperclass().getDeclaredMethod(
         "add", String.class);
      addM.setAccessible(true);
      addM.invoke(awtCircBuf, "first");
      addM.invoke(awtCircBuf, "second");
      addM.invoke(awtCircBuf, "third");

      Method getM = cbClass.getMethod("getTransferData",
         DataFlavor.class);
      Object result = getM.invoke(awtCircBuf, DataFlavor.stringFlavor);
      assertEquals("third", result.toString(),
         "getTransferData should return most recently added");
   }
}
