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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for {@link ShellManager} — session lifecycle, navigation,
 * listing, mouse forwarding, and buffer text extraction.
 *
 * <p>Tests ShellManager operations that require the full AWT editor
 * (UI singleton, OldView, etc.) to be initialized. Does NOT spawn
 * real shell processes (which hang Gradle); instead exercises the
 * manager API, buffer lookups, and text extraction utility on the
 * active GUI state.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class ShellManagerGuiJUnitTest {

   private static Robot robot;
   private static ShellManager mgr;
   private static FvContext<?> fvc;

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
      mgr = ShellManager.getInstance();
      EventQueue.biglock2.lock();
      try {
         mgr.closeAll();
         fvc = FvContext.getCurrFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @AfterAll
   static void tearDownAll() {
      if (mgr != null)
         mgr.closeAll();
      if (robot != null)
         robot.cleanUp();
   }

   // ── Singleton and initial state in GUI context ───────────────

   @Test
   void t01_singletonIsConsistentWithHeadlessInstance() {
      assertNotNull(mgr);
      assertNotNull(ShellManager.getInstance());
      assertEquals(mgr, ShellManager.getInstance(),
         "GUI and direct access should return same singleton");
   }

   @Test
   void t02_emptyManagerStateInGui() {
      assertEquals(0, mgr.getSessionCount());
      assertEquals(-1, mgr.getActiveIndex());
      assertNull(mgr.getActive());
      assertNull(mgr.getActiveBuffer());
   }

   @Test
   void t03_shellListFormattedWhenEmpty() {
      String list = mgr.getShellList();
      assertNotNull(list);
      assertTrue(list.contains("No active shells"),
         "Empty manager should report 'No active shells'");
   }

   // ── Shell command registration (requires GUI init) ───────────

   @Test
   void t04_shellCommandsRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(Rgroup.bindingLookup("vt"),
            "'vt' (shell) command must be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t05_shellCloseCommandRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         // shellclose is registered by ShellManager's Rgroup
         // It may be named differently; check common variations
         boolean found = Rgroup.bindingLookup("shellclose") != null
            || Rgroup.bindingLookup("shellnew") != null
            || Rgroup.bindingLookup("shells") != null;
         assertTrue(found,
            "At least one shell management command should be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Navigation on empty manager ──────────────────────────────

   @Test
   void t06_nextShellFalseWhenEmpty() {
      assertFalse(mgr.nextShell());
   }

   @Test
   void t07_previousShellFalseWhenEmpty() {
      assertFalse(mgr.previousShell());
   }

   @Test
   void t08_closeActiveShellFalseWhenEmpty() {
      assertFalse(mgr.closeActiveShell());
   }

   @Test
   void t09_closeShellByIdFalseWhenEmpty() {
      assertFalse(mgr.closeShell(1));
      assertFalse(mgr.closeShell(0));
      assertFalse(mgr.closeShell(-1));
   }

   @Test
   void t10_closeShellAtFalseWhenEmpty() {
      assertFalse(mgr.closeShellAt(0));
      assertFalse(mgr.closeShellAt(-1));
      assertFalse(mgr.closeShellAt(100));
   }

   // ── Mouse tracking on empty manager ──────────────────────────

   @Test
   void t11_mouseTrackingFalseWhenEmpty() {
      assertFalse(mgr.isMouseTrackingActive());
   }

   @Test
   void t12_isShellBufferFalseForEditorBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext<?> curr = FvContext.getCurrFvc();
         assertFalse(mgr.isShellBuffer(curr.edvec),
            "Editor buffer should not be a shell buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t13_mouseTrackingForBufferFalseForEditorBuffer()
         throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext<?> curr = FvContext.getCurrFvc();
         assertFalse(mgr.isMouseTrackingForBuffer(curr.edvec),
            "Editor buffer should not have mouse tracking");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t14_forwardMouseEventDoesNotCrashWhenEmpty() {
      // Should silently do nothing
      mgr.forwardMouseEvent(0, 1, 1, true);
      mgr.forwardMouseEvent(0, 1, 1, false);
      mgr.forwardMouseEvent(64, 5, 5, true); // scroll up
   }

   @Test
   void t15_forwardMouseEventToBufferDoesNotCrashWhenEmpty()
         throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext<?> curr = FvContext.getCurrFvc();
         mgr.forwardMouseEventToBuffer(curr.edvec, 0, 1, 1, true);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Focus event forwarding ───────────────────────────────────

   @Test
   void t16_forwardFocusEventDoesNotCrashWhenEmpty() {
      mgr.forwardFocusEvent(true);
      mgr.forwardFocusEvent(false);
   }

   // ── Buffer lookup on empty manager ───────────────────────────

   @Test
   void t17_findByBufferNullWhenEmpty() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext<?> curr = FvContext.getCurrFvc();
         assertNull(mgr.findByBuffer(curr.edvec),
            "findByBuffer should return null for editor buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t18_findByNameNullWhenEmpty() {
      assertNull(mgr.findByName("local"));
      assertNull(mgr.findByName(""));
      assertNull(mgr.findByName("nonexistent"));
   }

   @Test
   void t19_switchToNameFalseWhenEmpty() {
      assertFalse(mgr.switchToName("local"));
   }

   @Test
   void t20_switchToIdFalseWhenEmpty() {
      assertFalse(mgr.switchToId(1));
      assertFalse(mgr.switchToId(0));
   }

   // ── Screen attributes lookup ─────────────────────────────────

   @Test
   void t21_getScreenAttrsNullForEditorBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext<?> curr = FvContext.getCurrFvc();
         assertNull(mgr.getScreenAttrsForBuffer(curr.edvec),
            "Editor buffer should have no ScreenAttributes");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── notifyResize on empty manager ────────────────────────────

   @Test
   void t22_notifyResizeDoesNotCrashWhenEmpty() {
      mgr.notifyResize(24, 80);
      mgr.notifyResize(50, 132);
      mgr.notifyResize(0, 0);
   }

   // ── closeAll idempotent ──────────────────────────────────────

   @Test
   void t23_closeAllIdempotentWhenEmpty() {
      mgr.closeAll();
      assertEquals(0, mgr.getSessionCount());
      assertEquals(-1, mgr.getActiveIndex());
      mgr.closeAll(); // second call should also be safe
      assertEquals(0, mgr.getSessionCount());
   }

   // ── closeByBuffer on non-shell buffer ────────────────────────

   @Test
   void t24_closeByBufferReturnsFalseForEditorBuffer()
         throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext<?> curr = FvContext.getCurrFvc();
         assertFalse(mgr.closeByBuffer(curr.edvec),
            "closeByBuffer should return false for non-shell buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── extractBufferText static utility ─────────────────────────

   @Test
   void t25_extractBufferTextSingleLine() throws Exception {
      EventQueue.biglock2.lock();
      try {
         StringIoc sio = new StringIoc("smtest", "Hello World\n");
         TextEdit<String> te = new TextEdit<>(sio, sio.prop);
         te.finish();

         Position start = new Position(0, 1, "smtest", "");
         Position end = new Position(5, 1, "smtest", "");
         String text = ShellManager.extractBufferText(te, start, end);
         assertEquals("Hello", text,
            "Should extract 'Hello' from line 1 cols 0-5");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t26_extractBufferTextMultipleLines() throws Exception {
      EventQueue.biglock2.lock();
      try {
         StringIoc sio = new StringIoc("smtest",
            "line one\nline two\nline three\n");
         TextEdit<String> te = new TextEdit<>(sio, sio.prop);
         te.finish();

         Position start = new Position(5, 1, "smtest", "");
         Position end = new Position(4, 3, "smtest", "");
         String text = ShellManager.extractBufferText(te, start, end);
         assertTrue(text.contains("one"),
            "Should contain text from line 1");
         assertTrue(text.contains("\n"),
            "Multi-line extract should contain newlines");
         assertTrue(text.contains("line"),
            "Should contain text from later lines");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t27_extractBufferTextReversedSelection() throws Exception {
      EventQueue.biglock2.lock();
      try {
         StringIoc sio = new StringIoc("smtest", "test line\n");
         TextEdit<String> te = new TextEdit<>(sio, sio.prop);
         te.finish();

         Position start = new Position(5, 1, "smtest", "");
         Position end = new Position(0, 1, "smtest", "");
         String text = ShellManager.extractBufferText(te, start, end);
         assertEquals("test ", text,
            "Reversed selection should be handled correctly");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t28_extractBufferTextEmptyRange() throws Exception {
      EventQueue.biglock2.lock();
      try {
         StringIoc sio = new StringIoc("smtest", "content\n");
         TextEdit<String> te = new TextEdit<>(sio, sio.prop);
         te.finish();

         Position start = new Position(3, 1, "smtest", "");
         Position end = new Position(3, 1, "smtest", "");
         String text = ShellManager.extractBufferText(te, start, end);
         assertEquals("", text,
            "Same start and end should produce empty string");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t29_extractBufferTextClampsBeyondLineLength() throws Exception {
      EventQueue.biglock2.lock();
      try {
         StringIoc sio = new StringIoc("smtest", "short\n");
         TextEdit<String> te = new TextEdit<>(sio, sio.prop);
         te.finish();

         Position start = new Position(0, 1, "smtest", "");
         Position end = new Position(100, 1, "smtest", "");
         String text = ShellManager.extractBufferText(te, start, end);
         assertEquals("short", text,
            "Should clamp to actual line length");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t30_extractBufferTextClampsBeyondBufferEnd() throws Exception {
      EventQueue.biglock2.lock();
      try {
         StringIoc sio = new StringIoc("smtest", "only line\n");
         TextEdit<String> te = new TextEdit<>(sio, sio.prop);
         te.finish();

         Position start = new Position(0, 1, "smtest", "");
         Position end = new Position(5, 100, "smtest", "");
         String text = ShellManager.extractBufferText(te, start, end);
         assertNotNull(text,
            "Should not crash when end line exceeds buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Sessions list type ───────────────────────────────────────

   @Test
   void t31_getSessionsReturnsUnmodifiable() {
      java.util.List<ShellSession> sessions = mgr.getSessions();
      assertNotNull(sessions);
      try {
         sessions.add(null);
         // Should throw UnsupportedOperationException
         assertTrue(false,
            "getSessions should return unmodifiable list");
      } catch (UnsupportedOperationException e) {
         // expected
      }
   }

   // ── switchTo boundary conditions ─────────────────────────────

   @Test
   void t32_switchToNegativeIndexFails() {
      assertFalse(mgr.switchTo(-1));
      assertFalse(mgr.switchTo(Integer.MIN_VALUE));
   }

   @Test
   void t33_switchToLargeIndexFails() {
      assertFalse(mgr.switchTo(Integer.MAX_VALUE));
      assertFalse(mgr.switchTo(1000));
   }

   // ── Integration: manager state visible through GUI ───────────

   @Test
   void t34_uiReportMessageDoesNotAffectShellManager() {
      int countBefore = mgr.getSessionCount();
      UI.reportMessage("test from ShellManagerGuiJUnitTest");
      assertEquals(countBefore, mgr.getSessionCount(),
         "reportMessage should not affect shell session count");
   }

   @SuppressWarnings("unchecked")
   @Test
   void t35_shellManagerSurvivesBufferOperations() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext curr = FvContext.getCurrFvc();
         EditContainer te = curr.edvec;

         // Perform some buffer operations
         int linesBefore = te.readIn();
         te.insertOne("temporary line", te.finish());
         assertEquals(linesBefore + 1, te.readIn());

         // ShellManager should be unaffected
         assertEquals(0, mgr.getSessionCount(),
            "Buffer edits should not create shell sessions");

         // Clean up — undo the insert
         Command.command("u", curr, null);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
