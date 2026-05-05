package javi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for UI state persistence — save/restore cycle.
 *
 * <p>Exercises the serialization path through AwtInterface and AwtFontList.
 * Verifies that UI.saveState() produces serializable output and that the
 * saved data can be deserialized without corruption. Requires Xvfb or
 * a display.</p>
 *
 * <p>Note: Does NOT call UI.restoreState() as that would replace the
 * singleton and destabilize other tests. Instead, tests the individual
 * save/restore components independently.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class UIStatePersistenceGuiJUnitTest {

   private static Robot robot;
   private static UI ui;

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
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── UI.saveState produces output ─────────────────────────────

   @Test
   void t01_saveStateProducesNonEmptyBytes() throws Exception {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      EventQueue.biglock2.lock();
      try {
         UI.saveState(oos);
      } finally {
         EventQueue.biglock2.unlock();
      }
      oos.close();
      assertTrue(baos.size() > 0,
         "saveState should produce serialized bytes");
   }

   @Test
   void t02_saveStateOutputIsDeserializable() throws Exception {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      EventQueue.biglock2.lock();
      try {
         UI.saveState(oos);
      } finally {
         EventQueue.biglock2.unlock();
      }
      oos.close();

      // Verify the bytes can be read back as objects
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      ObjectInputStream ois = new ObjectInputStream(bais);
      Object restored = ois.readObject();
      assertNotNull(restored, "Deserialized UI object should not be null");
      assertTrue(restored.getClass().getName().endsWith("AwtInterface"),
         "Deserialized object should be AwtInterface, got "
         + restored.getClass().getName());
   }

   @Test
   void t03_saveStateIncludesFontData() throws Exception {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      EventQueue.biglock2.lock();
      try {
         UI.saveState(oos);
      } finally {
         EventQueue.biglock2.unlock();
      }
      oos.close();

      // The saved state includes: UI object + AwtFontList state
      // Minimum size check — UI + FontEntry + monoFontName
      assertTrue(baos.size() > 100,
         "Saved state should be substantial (font + UI), got "
         + baos.size() + " bytes");
   }

   // ── AwtFontList save/restore cycle ───────────────────────────

   @Test
   void t04_fontListSaveProducesOutput() throws Exception {
      Class<?> awtFontList = Class.forName("javi.awt.AwtFontList");
      Method saveState = awtFontList.getDeclaredMethod("saveState",
         ObjectOutputStream.class);
      saveState.setAccessible(true);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      saveState.invoke(null, oos);
      oos.close();

      assertTrue(baos.size() > 0,
         "AwtFontList.saveState should produce output");
   }

   @Test
   void t05_fontListSaveRestoreRoundTrip() throws Exception {
      Class<?> awtFontList = Class.forName("javi.awt.AwtFontList");
      Method saveState = awtFontList.getDeclaredMethod("saveState",
         ObjectOutputStream.class);
      saveState.setAccessible(true);
      Method restoreState = awtFontList.getDeclaredMethod("restoreState",
         ObjectInputStream.class);
      restoreState.setAccessible(true);

      // Save current font state
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      saveState.invoke(null, oos);
      oos.close();

      // Restore from the saved bytes
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      ObjectInputStream ois = new ObjectInputStream(bais);
      assertDoesNotThrow(() -> restoreState.invoke(null, ois),
         "AwtFontList.restoreState should not throw");
   }

   @Test
   void t06_fontListSaveContentIncludesFontEntry() throws Exception {
      Class<?> awtFontList = Class.forName("javi.awt.AwtFontList");
      Method saveState = awtFontList.getDeclaredMethod("saveState",
         ObjectOutputStream.class);
      saveState.setAccessible(true);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      saveState.invoke(null, oos);
      oos.close();

      // Deserialize and check types
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      ObjectInputStream ois = new ObjectInputStream(bais);
      Object fontEntry = ois.readObject();
      assertNotNull(fontEntry, "FontEntry should be non-null");
      assertTrue(fontEntry.getClass().getName().contains("FontEntry"),
         "First object should be FontEntry, got "
         + fontEntry.getClass().getName());

      Object monoFontName = ois.readObject();
      assertNotNull(monoFontName, "monoFontName should be non-null");
      assertTrue(monoFontName instanceof String,
         "Second object should be String (monoFontName)");
   }

   // ── AwtInterface serialization properties ────────────────────

   @Test
   void t07_awtInterfaceIsSerializable() {
      assertTrue(ui instanceof java.io.Serializable,
         "AwtInterface should implement Serializable");
   }

   @Test
   void t08_awtInterfaceHasSerialVersionUID() throws Exception {
      Class<?> awtIfClass = ui.getClass();
      Field svuid = awtIfClass.getDeclaredField("serialVersionUID");
      svuid.setAccessible(true);
      assertNotNull(svuid, "serialVersionUID field should exist");
   }

   @Test
   void t09_saveStateTwiceProducesSameSize() throws Exception {
      ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
      ObjectOutputStream oos1 = new ObjectOutputStream(baos1);
      EventQueue.biglock2.lock();
      try {
         UI.saveState(oos1);
      } finally {
         EventQueue.biglock2.unlock();
      }
      oos1.close();

      ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
      ObjectOutputStream oos2 = new ObjectOutputStream(baos2);
      EventQueue.biglock2.lock();
      try {
         UI.saveState(oos2);
      } finally {
         EventQueue.biglock2.unlock();
      }
      oos2.close();

      assertEquals(baos1.size(), baos2.size(),
         "Two consecutive saves should produce same-size output");
   }

   // ── iSaveState/iRestoreState direct tests ────────────────────

   @Test
   void t10_iSaveStateDoesNotThrow() throws Exception {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      assertDoesNotThrow(() -> ui.iSaveState(oos),
         "iSaveState should not throw");
      oos.close();
      assertTrue(baos.size() > 0);
   }

   @Test
   void t11_iRestoreStateFromSavedOutput() throws Exception {
      // Save
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      ui.iSaveState(oos);
      oos.close();

      // Restore
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      ObjectInputStream ois = new ObjectInputStream(bais);
      assertDoesNotThrow(() -> ui.iRestoreState(ois),
         "iRestoreState should accept data from iSaveState");
   }

   @Test
   void t12_saveStateCallsFlush() throws Exception {
      // UI.saveState calls instance.iflush(true) before serializing
      // Verify no exception during flush
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      EventQueue.biglock2.lock();
      try {
         ui.iflush(true);
         UI.saveState(oos);
      } finally {
         EventQueue.biglock2.unlock();
      }
      oos.close();
      assertTrue(baos.size() > 0,
         "saveState with explicit flush should work");
   }

   // ── State size and content stability ─────────────────────────

   @Test
   void t13_savedStateContainsAwtInterfaceClassName() throws Exception {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      EventQueue.biglock2.lock();
      try {
         UI.saveState(oos);
      } finally {
         EventQueue.biglock2.unlock();
      }
      oos.close();

      // The serialized bytes should contain the class name
      String content = new String(baos.toByteArray(), "ISO-8859-1");
      assertTrue(content.contains("AwtInterface"),
         "Serialized data should reference AwtInterface class");
   }

   @Test
   void t14_savedStateContainsFontEntry() throws Exception {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      EventQueue.biglock2.lock();
      try {
         UI.saveState(oos);
      } finally {
         EventQueue.biglock2.unlock();
      }
      oos.close();

      String content = new String(baos.toByteArray(), "ISO-8859-1");
      assertTrue(content.contains("FontEntry"),
         "Serialized data should reference FontEntry class");
   }

   @Test
   void t15_uiInstanceStableAfterSave() throws Exception {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      EventQueue.biglock2.lock();
      try {
         UI.saveState(oos);
      } finally {
         EventQueue.biglock2.unlock();
      }
      oos.close();

      // UI singleton should still be the same instance after save
      assertTrue(UI.getInstance() == ui,
         "UI instance should not change after saveState");
   }
}
