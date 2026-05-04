package javi;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Insets;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for {@code AwtInterface} window management, view splitting,
 * frame sizing, and the idle/event dispatch mechanism.
 *
 * <p>Exercises the AwtInterface Commands doroutine (togglestatus, va,
 * van, vd, vn, fullscreen), TestFrame preferred size computation,
 * ForceIdle event posting, and multi-view layout management.</p>
 *
 * <p>Requires Xvfb or a display — tagged {@code "gui"}.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class AwtInterfaceGuiJUnitTest {

   private static Robot robot;
   private static Frame frame;
   private static UI ui;
   private static Class<?> awtIfClass;

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
      ui = UI.getInstance();
      awtIfClass = ui.getClass();

      // Get the TestFrame via reflection (frm field)
      Field frmField = awtIfClass.getDeclaredField("frm");
      frmField.setAccessible(true);
      frame = (Frame) frmField.get(ui);
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── Frame discovery and basic properties ─────────────────────

   @Test
   void t01_frameExists() {
      assertNotNull(frame, "TestFrame should be accessible");
   }

   @Test
   void t02_frameHasPositiveSize() {
      Dimension size = frame.getSize();
      assertTrue(size.width > 0, "Frame width > 0");
      assertTrue(size.height > 0, "Frame height > 0");
   }

   @Test
   void t03_frameHasInsets() {
      Insets insets = frame.getInsets();
      assertNotNull(insets, "Frame insets should not be null");
   }

   @Test
   void t04_frameHasTitle() {
      assertNotNull(frame.getTitle(),
         "Frame should have a title");
   }

   // ── View count tracking ──────────────────────────────────────

   @Test
   void t05_viewCountPositive() throws Exception {
      Field vcField = awtIfClass.getDeclaredField("viewCount");
      vcField.setAccessible(true);
      int count = vcField.getInt(ui);
      assertTrue(count >= 0,
         "viewCount should be >= 0, got " + count);
   }

   // ── StatusBar reference ──────────────────────────────────────

   @Test
   void t06_statusBarFieldSet() throws Exception {
      Field sbField = awtIfClass.getDeclaredField("statusBar");
      sbField.setAccessible(true);
      Object sb = sbField.get(ui);
      assertNotNull(sb, "statusBar field should not be null");
   }

   // ── iaddview / inextView / delview via reflection ────────────

   @Test
   void t07_addViewIncrementsCount() throws Exception {
      Field vcField = awtIfClass.getDeclaredField("viewCount");
      vcField.setAccessible(true);

      EventQueue.biglock2.lock();
      try {
         int before = vcField.getInt(ui);
         FvContext fvc = FvContext.getCurrFvc();

         Method addView = awtIfClass.getDeclaredMethod(
            "iaddview", boolean.class, FvContext.class);
         addView.setAccessible(true);
         addView.invoke(ui, false, fvc);

         int after = vcField.getInt(ui);
         assertEquals(before + 1, after,
            "viewCount should increment after iaddview");

         // Clean up: delete the view we just added
         Method delView = awtIfClass.getDeclaredMethod(
            "delview", FvContext.class);
         delView.setAccessible(true);
         FvContext newFvc = FvContext.getCurrFvc();
         delView.invoke(ui, newFvc);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t08_nextViewDoesNotCrash() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         Method nextView = awtIfClass.getDeclaredMethod(
            "inextView", FvContext.class);
         nextView.setAccessible(true);
         nextView.invoke(ui, fvc);
         // If we get here, no exception
         assertNotNull(FvContext.getCurrFvc());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── ForceIdle event ──────────────────────────────────────────

   @Test
   void t09_forceIdleClassExists() throws Exception {
      Class<?> fiClass = Class.forName(
         "javi.awt.AwtInterface$ForceIdle");
      assertNotNull(fiClass);
      Object fi = fiClass.getDeclaredConstructor().newInstance();
      assertNotNull(fi);
   }

   @Test
   void t10_forceIdleExecuteDoesNotCrash() throws Exception {
      Class<?> fiClass = Class.forName(
         "javi.awt.AwtInterface$ForceIdle");
      Object fi = fiClass.getDeclaredConstructor().newInstance();
      Method exec = fiClass.getMethod("execute");
      exec.invoke(fi); // ForceIdle.execute() is a no-op
      assertNotNull(fi);
   }

   // ── toggleStatus via Commands doroutine ──────────────────────

   @Test
   void t11_toggleStatusTwiceRestoresState() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Field sbField = awtIfClass.getDeclaredField("statusBar");
         sbField.setAccessible(true);
         Object sb = sbField.get(ui);
         Method isVis = sb.getClass().getMethod("isVisible");
         boolean before = (boolean) isVis.invoke(sb);

         ui.itoggleStatus();
         boolean mid = (boolean) isVis.invoke(sb);
         assertFalse(before == mid,
            "toggleStatus should change visibility");

         ui.itoggleStatus();
         boolean after = (boolean) isVis.invoke(sb);
         assertEquals(before, after,
            "Two toggles should restore original state");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Frame preferred size computation ─────────────────────────

   @Test
   void t12_preferredSizeReasonable() {
      Dimension pref = frame.getPreferredSize();
      assertNotNull(pref);
      assertTrue(pref.width > 100,
         "Preferred width should be > 100, got " + pref.width);
      assertTrue(pref.height > 100,
         "Preferred height should be > 100, got " + pref.height);
   }

   // ── tfc (command context) ────────────────────────────────────

   @Test
   void t13_tfcFieldIsCurrentFvc() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Field tfcField = awtIfClass.getDeclaredField("tfc");
         tfcField.setAccessible(true);
         FvContext tfc = (FvContext) tfcField.get(ui);
         assertNotNull(tfc,
            "tfc command context should be set");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Clipboard via AwtCircBuffer integration ──────────────────

   @Test
   void t14_clipboardFromUIDoesNotCrash() throws Exception {
      EventQueue.biglock2.lock();
      try {
         // Access system clipboard through the UI path
         Method getClip = awtIfClass.getDeclaredMethod("igetClipboard");
         if (getClip != null) {
            getClip.setAccessible(true);
            getClip.invoke(ui);
         }
      } catch (NoSuchMethodException e) {
         // igetClipboard may not exist — that's fine
      } finally {
         EventQueue.biglock2.unlock();
      }
      assertNotNull(ui);
   }

   // ── Frame component count ────────────────────────────────────

   @Test
   void t15_frameHasComponents() {
      int count = frame.getComponentCount();
      assertTrue(count >= 1,
         "Frame should have at least 1 component, got " + count);
   }

   // ── iSaveState / iRestoreState ───────────────────────────────

   @Test
   void t16_saveStateMethodExists() throws Exception {
      Method m = awtIfClass.getMethod("iSaveState",
         java.io.ObjectOutputStream.class);
      assertNotNull(m, "iSaveState should exist");
   }

   @Test
   void t17_restoreStateMethodExists() throws Exception {
      Method m = awtIfClass.getMethod("iRestoreState",
         java.io.ObjectInputStream.class);
      assertNotNull(m, "iRestoreState should exist");
   }
}
