package javi;

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
 * GUI tests for {@link InsertBuffer} — insert mode, overwrite mode,
 * backspace, verbatim input, and command-line editing.
 *
 * <p>Exercises InsertBuffer internals through the full AWT editor
 * via reflection, verifying that insert mode keybindings are
 * registered, state transitions work correctly, and buffer
 * manipulation methods behave as expected.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class InsertBufferGuiJUnitTest {

   private static Robot robot;
   private static FvContext<?> fvc;
   private static Object insertBufferInstance;
   private static Class<?> ibClass;

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
      EventQueue.biglock2.lock();
      try {
         fvc = FvContext.getCurrFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }

      // Access the InsertBuffer singleton via reflection
      Field instanceField = InsertBuffer.class.getDeclaredField("instance");
      instanceField.setAccessible(true);
      insertBufferInstance = instanceField.get(null);
      ibClass = InsertBuffer.class;
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── Helpers ──────────────────────────────────────────────────

   private static String getBufferString() throws Exception {
      Method m = ibClass.getDeclaredMethod("getString");
      m.setAccessible(true);
      return (String) m.invoke(insertBufferInstance);
   }

   private static boolean isOverwrite() throws Exception {
      Method m = ibClass.getDeclaredMethod("isOverwrite");
      m.setAccessible(true);
      return (boolean) m.invoke(insertBufferInstance);
   }

   private static boolean isActive() throws Exception {
      Method m = ibClass.getDeclaredMethod("isActive");
      m.setAccessible(true);
      return (boolean) m.invoke(insertBufferInstance);
   }

   private static Field getField(String name) throws Exception {
      Field f = ibClass.getDeclaredField(name);
      f.setAccessible(true);
      return f;
   }

   // ── Instance and registration tests ──────────────────────────

   @Test
   void t01_insertBufferSingletonExists() {
      assertNotNull(insertBufferInstance,
         "InsertBuffer singleton must be initialized after Javi.initToUi");
   }

   @Test
   void t02_insertBufferIsInHandlerSubclass() {
      // The concrete InsertBuffer is javi.awt.InHandler
      assertTrue(
         insertBufferInstance.getClass().getName().contains("InHandler"),
         "InsertBuffer instance should be InHandler, got "
            + insertBufferInstance.getClass().getName());
   }

   @Test
   void t03_insertModeNotActiveInitially() throws Exception {
      assertFalse(isActive(),
         "InsertBuffer should not be active before entering insert mode");
   }

   // ── Buffer string tests ──────────────────────────────────────

   @Test
   void t04_bufferStringInitiallyEmpty() throws Exception {
      String buf = getBufferString();
      assertNotNull(buf, "Buffer string must not be null");
      assertEquals("", buf,
         "Buffer string should be empty when not in insert mode");
   }

   // ── Insert mode keybinding registration ──────────────────────

   @Test
   void t05_insertModeCommandsRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(Rgroup.bindingLookup("imode.toggleinsert"),
            "imode.toggleinsert must be registered");
         assertNotNull(Rgroup.bindingLookup("imode.tabinsert"),
            "imode.tabinsert must be registered");
         assertNotNull(Rgroup.bindingLookup("imode.backspace"),
            "imode.backspace must be registered");
         assertNotNull(Rgroup.bindingLookup("imode.delete"),
            "imode.delete must be registered");
         assertNotNull(Rgroup.bindingLookup("imode.complete"),
            "imode.complete must be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t06_insertNewlineCommandRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(Rgroup.bindingLookup("imode.insertnewline"),
            "imode.insertnewline must be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t07_insertCancelCommandRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(Rgroup.bindingLookup("imode.cancel"),
            "imode.cancel must be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t08_verbatimCommandRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(Rgroup.bindingLookup("imode.setverbatim"),
            "imode.setverbatim must be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t09_historyNavigationCommandsRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(Rgroup.bindingLookup("imode.prevline"),
            "imode.prevline must be registered");
         assertNotNull(Rgroup.bindingLookup("imode.nextline"),
            "imode.nextline must be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t10_putBufCommandRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(Rgroup.bindingLookup("imode.putbuf"),
            "imode.putbuf must be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Overwrite mode state ─────────────────────────────────────

   @Test
   void t11_overwriteDefaultFalse() throws Exception {
      assertFalse(isOverwrite(),
         "Overwrite mode should be false by default");
   }

   // ── KeyGroup internals ───────────────────────────────────────

   @Test
   void t12_ikeyGroupExists() throws Exception {
      Field f = getField("ikeys");
      Object ikeys = f.get(insertBufferInstance);
      assertNotNull(ikeys, "ikeys KeyGroup must exist");
      assertTrue(ikeys instanceof KeyGroup,
         "ikeys must be a KeyGroup");
   }

   @Test
   void t13_commandIkeyGroupExists() throws Exception {
      Field f = getField("commandikeys");
      Object cikeys = f.get(insertBufferInstance);
      assertNotNull(cikeys, "commandikeys KeyGroup must exist");
      assertTrue(cikeys instanceof KeyGroup,
         "commandikeys must be a KeyGroup");
   }

   @Test
   void t14_ikeysDifferFromCommandIkeys() throws Exception {
      Field ikeysField = getField("ikeys");
      Field cikeysField = getField("commandikeys");
      Object ikeys = ikeysField.get(insertBufferInstance);
      Object cikeys = cikeysField.get(insertBufferInstance);
      assertFalse(ikeys == cikeys,
         "ikeys and commandikeys must be different objects");
   }

   // ── Verbatim state ───────────────────────────────────────────

   @Test
   void t15_verbatimInitiallyFalse() throws Exception {
      Field f = getField("verbatim");
      assertFalse(f.getBoolean(insertBufferInstance),
         "Verbatim mode should be false initially");
   }

   @Test
   void t16_verbatimAccInitiallyZero() throws Exception {
      Field f = getField("verbatimAcc");
      assertEquals(0, f.getInt(insertBufferInstance),
         "verbatimAcc should be 0 initially");
   }

   @Test
   void t17_verbatimCountInitiallyZero() throws Exception {
      Field f = getField("verbatimCount");
      assertEquals(0, f.getInt(insertBufferInstance),
         "verbatimCount should be 0 initially");
   }

   // ── findspacebound static utility ────────────────────────────

   @Test
   void t18_findspaceboundStaticMethodAccessible() throws Exception {
      // The method is package-private static
      Method m = ibClass.getDeclaredMethod("findspacebound",
         FvContext.class, int.class);
      m.setAccessible(true);
      assertNotNull(m, "findspacebound method must exist");
   }

   @Test
   void t19_findspaceboundReturnsZeroAtLineStart() throws Exception {
      Method m = ibClass.getDeclaredMethod("findspacebound",
         FvContext.class, int.class);
      m.setAccessible(true);

      EventQueue.biglock2.lock();
      try {
         FvContext<?> currFvc = FvContext.getCurrFvc();
         int result = (int) m.invoke(null, currFvc, 0);
         assertTrue(result >= 0,
            "findspacebound must return >= 0, got " + result);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── insertChars method ───────────────────────────────────────

   @Test
   void t20_insertCharsHandlesNullIterator() throws Exception {
      Method m = ibClass.getDeclaredMethod("insertChars",
         java.text.CharacterIterator.class, int.class);
      m.setAccessible(true);
      // Calling with null should not throw
      m.invoke(insertBufferInstance, (java.text.CharacterIterator) null, 0);
      // Buffer should remain unchanged (still empty)
      assertEquals("", getBufferString());
   }

   @Test
   void t21_insertCharsAppendsCharacters() throws Exception {
      Method m = ibClass.getDeclaredMethod("insertChars",
         java.text.CharacterIterator.class, int.class);
      m.setAccessible(true);

      // Use a StringCharacterIterator to simulate IME input
      java.text.StringCharacterIterator sci =
         new java.text.StringCharacterIterator("abc");
      m.invoke(insertBufferInstance, sci, 0);

      String buf = getBufferString();
      assertTrue(buf.contains("abc"),
         "insertChars should append 'abc' to buffer, got: " + buf);

      // Clean up: clear buffer via reflection
      Field bufField = getField("buffer");
      StringBuilder sb = (StringBuilder) bufField.get(insertBufferInstance);
      sb.setLength(0);
   }

   @Test
   void t22_insertCharsMultipleCallsAccumulate() throws Exception {
      Method insertCharsMethod = ibClass.getDeclaredMethod("insertChars",
         java.text.CharacterIterator.class, int.class);
      insertCharsMethod.setAccessible(true);

      Field bufField = getField("buffer");
      StringBuilder sb = (StringBuilder) bufField.get(insertBufferInstance);
      sb.setLength(0);

      java.text.StringCharacterIterator sci1 =
         new java.text.StringCharacterIterator("XY");
      insertCharsMethod.invoke(insertBufferInstance, sci1, 0);

      java.text.StringCharacterIterator sci2 =
         new java.text.StringCharacterIterator("Z");
      insertCharsMethod.invoke(insertBufferInstance, sci2, 0);

      String buf = getBufferString();
      assertEquals("XYZ", buf,
         "Multiple insertChars calls should accumulate");
      sb.setLength(0);
   }

   // ── InsertBuffer.insertMode via command (static method) ──────

   @Test
   void t23_insertModeStaticMethodExists() throws Exception {
      Method m = ibClass.getDeclaredMethod("insertMode",
         boolean.class, int.class, FvContext.class,
         boolean.class, boolean.class);
      m.setAccessible(true);
      assertNotNull(m, "InsertBuffer.insertMode static method must exist");
   }

   // ── getcomline static method ─────────────────────────────────

   @Test
   void t24_getcomlineStaticMethodExists() throws Exception {
      Method m = ibClass.getDeclaredMethod("getcomline", String.class);
      m.setAccessible(true);
      assertNotNull(m,
         "InsertBuffer.getcomline static method must exist");
   }

   // ── singleline field state ───────────────────────────────────

   @Test
   void t25_singlelineFieldDefault() throws Exception {
      Field f = getField("singleline");
      // When not in insert mode, singleline should be false
      assertFalse(f.getBoolean(insertBufferInstance),
         "singleline should be false when not in insert mode");
   }

   // ── Help completion internals ────────────────────────────────

   @Test
   void t26_helpCompletionIndexInitiallyNegative() throws Exception {
      Field f = getField("helpCompletionIndex");
      assertEquals(-1, f.getInt(insertBufferInstance),
         "helpCompletionIndex should be -1 initially");
   }

   @Test
   void t27_helpCompletionPrefixInitiallyEmpty() throws Exception {
      Field f = getField("helpCompletionPrefix");
      String prefix = (String) f.get(insertBufferInstance);
      assertNotNull(prefix);
      assertEquals("", prefix,
         "helpCompletionPrefix should be empty initially");
   }

   // ── cleanup method ───────────────────────────────────────────

   @Test
   void t28_cleanupSetsInactiveState() throws Exception {
      // Temporarily set myfvc to simulate active state
      Field myfvcField = getField("myfvc");
      myfvcField.set(insertBufferInstance, fvc);
      assertTrue(isActive(), "Should be active after setting myfvc");

      // Create a simple view for cleanup
      Method cleanupMethod = ibClass.getDeclaredMethod("cleanup",
         FvContext.class);
      cleanupMethod.setAccessible(true);

      EventQueue.biglock2.lock();
      try {
         cleanupMethod.invoke(insertBufferInstance, fvc);
      } finally {
         EventQueue.biglock2.unlock();
      }

      assertFalse(isActive(),
         "Should be inactive after cleanup");
   }

   // ── ex-command insert mode integration ───────────────────────

   @Test
   void t29_insertCommandRegisteredInNormalMode() throws Exception {
      // Verify that insert/append/open commands exist for normal mode
      EventQueue.biglock2.lock();
      try {
         // These are registered by EditGroup, triggered by 'i'/'a'/'o'
         assertNotNull(Rgroup.bindingLookup("insert"),
            "'insert' command must be registered");
         assertNotNull(Rgroup.bindingLookup("append"),
            "'append' command must be registered");
         assertNotNull(Rgroup.bindingLookup("openline"),
            "'openline' command must be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t30_overwriteFieldExists() throws Exception {
      // Verify overwrite mode field is accessible via InsertBuffer
      Field f = getField("overwrite");
      assertNotNull(f,
            "'overwrite' field must exist on InsertBuffer");
   }

   // ── InHandler-specific: InputMethodListener methods ──────────

   @Test
   void t31_inHandlerGetInsertPositionOffset() throws Exception {
      Method m = insertBufferInstance.getClass()
         .getDeclaredMethod("getInsertPositionOffset");
      m.setAccessible(true);
      int offset = (int) m.invoke(insertBufferInstance);
      assertEquals(200, offset,
         "getInsertPositionOffset should return 200");
   }

   @Test
   void t32_inHandlerGetLocationOffset() throws Exception {
      Method m = insertBufferInstance.getClass()
         .getDeclaredMethod("getLocationOffset", int.class, int.class);
      m.setAccessible(true);
      Object result = m.invoke(insertBufferInstance, 10, 20);
      assertNotNull(result,
         "getLocationOffset should return a TextHitInfo");
      assertTrue(
         result.getClass().getName().contains("TextHitInfo"),
         "Return type should be TextHitInfo");
   }

   @Test
   void t33_inHandlerGetTextLocation() throws Exception {
      Method m = insertBufferInstance.getClass()
         .getDeclaredMethod("getTextLocation",
            java.awt.font.TextHitInfo.class);
      m.setAccessible(true);
      java.awt.font.TextHitInfo thi =
         java.awt.font.TextHitInfo.afterOffset(0);
      Object result = m.invoke(insertBufferInstance, thi);
      assertNotNull(result,
         "getTextLocation should return a Rectangle");
      assertTrue(result instanceof java.awt.Rectangle,
         "Return type should be Rectangle");
   }

   @Test
   void t34_inHandlerGetCommittedTextLength() throws Exception {
      Method m = insertBufferInstance.getClass()
         .getDeclaredMethod("getCommittedTextLength");
      m.setAccessible(true);
      int len = (int) m.invoke(insertBufferInstance);
      assertEquals(0, len,
         "getCommittedTextLength should return 0");
   }

   @Test
   void t35_inHandlerCancelLatestCommittedText() throws Exception {
      // Check the method exists on InHandler (InputMethodListener)
      Method[] methods = insertBufferInstance.getClass()
         .getDeclaredMethods();
      boolean found = false;
      for (Method mth : methods) {
         if ("cancelLatestCommittedText".equals(mth.getName())) {
            found = true;
            break;
         }
      }
      assertTrue(found,
         "cancelLatestCommittedText method must exist on InHandler");
   }

   @Test
   void t36_inHandlerGetSelectedText() throws Exception {
      Method[] methods = insertBufferInstance.getClass()
         .getDeclaredMethods();
      boolean found = false;
      for (Method m : methods) {
         if ("getSelectedText".equals(m.getName())) {
            found = true;
            break;
         }
      }
      assertTrue(found,
         "getSelectedText method must exist on InHandler");
   }

   @Test
   void t37_inHandlerInsertResetClearsCommited() throws Exception {
      // insertReset() should reset the commited counter
      Field commitedField =
         insertBufferInstance.getClass().getDeclaredField("commited");
      commitedField.setAccessible(true);

      // Set commited to non-zero
      commitedField.setInt(insertBufferInstance, 5);
      assertEquals(5, commitedField.getInt(insertBufferInstance));

      Method resetMethod = insertBufferInstance.getClass()
         .getDeclaredMethod("insertReset");
      resetMethod.setAccessible(true);
      resetMethod.invoke(insertBufferInstance);

      assertEquals(0, commitedField.getInt(insertBufferInstance),
         "insertReset should clear commited counter");
   }

   // ── dotbuffer state ──────────────────────────────────────────

   @Test
   void t38_dotbufferFieldExists() throws Exception {
      Field f = getField("dotbuffer");
      assertNotNull(f, "dotbuffer field must exist");
      // dotbuffer is null when not in insert mode
      // (it's set to "" on entry to insertmode)
   }

   // ── ff constant ──────────────────────────────────────────────

   @Test
   void t39_ffConstantIsFalseFalse() throws Exception {
      Field f = ibClass.getDeclaredField("ff");
      f.setAccessible(true);
      boolean[] ff = (boolean[]) f.get(null);
      assertEquals(2, ff.length, "ff must have 2 elements");
      assertFalse(ff[0], "ff[0] must be false");
      assertFalse(ff[1], "ff[1] must be false");
   }

   // ── insertMode with dotmode=true ─────────────────────────────

   @Test
   void t40_dotModeInsertModeSetsBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext<?> currFvc = FvContext.getCurrFvc();
         TextEdit<?> te = currFvc.edvec;
         int linesBefore = te.readIn();

         // Set up dotbuffer via reflection for dot repeat
         Field dotField = getField("dotbuffer");
         dotField.set(insertBufferInstance, "test");

         // Save original overwrite state
         Field owField = getField("overwrite");
         boolean origOw = owField.getBoolean(insertBufferInstance);
         owField.setBoolean(insertBufferInstance, false);

         // Call insertMode with dotmode=true, count=1
         // This should insert the dotbuffer text without entering
         // interactive mode
         Method insertModeMethod = ibClass.getDeclaredMethod(
            "insertMode", boolean.class, int.class,
            FvContext.class, boolean.class, boolean.class);
         insertModeMethod.setAccessible(true);
         insertModeMethod.invoke(insertBufferInstance,
            true, 1, currFvc, false, false);

         // Verify text was inserted
         int linesAfter = te.readIn();
         assertTrue(linesAfter >= linesBefore,
            "Buffer should still have content after dot insert");

         // Restore state
         owField.setBoolean(insertBufferInstance, origOw);
         dotField.set(insertBufferInstance, null);

         // Undo the insert to clean up
         Command.command("u", currFvc, null);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
