package javi;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link ShellManager} — session management
 * API on an empty manager instance.
 *
 * <p>Does not create real shell sessions (which spawn processes)
 * because they hang the Gradle test runner on cleanup.</p>
 */
class ShellManagerJUnitTest {

   private static ShellManager mgr;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
      mgr = ShellManager.getInstance();
      mgr.closeAll(); // ensure clean state
   }

   // ================================================================
   // Singleton & initial state
   // ================================================================

   @Test
   @DisplayName("getInstance returns same instance")
   void singletonIdentity() {
      assertSame(mgr, ShellManager.getInstance());
   }

   @Test
   @DisplayName("no sessions initially after closeAll")
   void initialCountZero() {
      assertEquals(0, mgr.getSessionCount());
   }

   @Test
   @DisplayName("activeIndex is -1 when empty")
   void activeIndexNegativeWhenEmpty() {
      assertEquals(-1, mgr.getActiveIndex());
   }

   @Test
   @DisplayName("getActive returns null when empty")
   void getActiveNullWhenEmpty() {
      assertNull(mgr.getActive());
   }

   @Test
   @DisplayName("getActiveBuffer returns null when empty")
   void getActiveBufferNullWhenEmpty() {
      assertNull(mgr.getActiveBuffer());
   }

   @Test
   @DisplayName("getSessions returns empty list when empty")
   void getSessionsEmptyWhenEmpty() {
      List<ShellSession> sessions = mgr.getSessions();
      assertNotNull(sessions);
      assertTrue(sessions.isEmpty());
   }

   @Test
   @DisplayName("getShellList reports no active shells")
   void shellListEmptyMessage() {
      String list = mgr.getShellList();
      assertNotNull(list);
      assertTrue(list.contains("No active shells"),
         "should say 'No active shells', got: " + list);
   }

   // ================================================================
   // Invalid operations on empty manager
   // ================================================================

   @Test
   @DisplayName("switchTo with invalid index returns false")
   void switchToInvalidIndex() {
      assertFalse(mgr.switchTo(0));
      assertFalse(mgr.switchTo(-1));
      assertFalse(mgr.switchTo(100));
   }

   @Test
   @DisplayName("switchToId with no sessions returns false")
   void switchToIdNotFound() {
      assertFalse(mgr.switchToId(999));
   }

   @Test
   @DisplayName("switchToName with no sessions returns false")
   void switchToNameNotFound() {
      assertFalse(mgr.switchToName("nonexistent"));
   }

   @Test
   @DisplayName("nextShell with no sessions returns false")
   void nextShellEmpty() {
      assertFalse(mgr.nextShell());
   }

   @Test
   @DisplayName("previousShell with no sessions returns false")
   void previousShellEmpty() {
      assertFalse(mgr.previousShell());
   }

   @Test
   @DisplayName("closeActiveShell with no sessions returns false")
   void closeActiveShellEmpty() {
      assertFalse(mgr.closeActiveShell());
   }

   @Test
   @DisplayName("closeShell with unknown ID returns false")
   void closeShellUnknownId() {
      assertFalse(mgr.closeShell(999));
   }

   @Test
   @DisplayName("closeShellAt with invalid index returns false")
   void closeShellAtInvalid() {
      assertFalse(mgr.closeShellAt(0));
      assertFalse(mgr.closeShellAt(-1));
   }

   @Test
   @DisplayName("isMouseTrackingActive returns false when empty")
   void mouseTrackingFalseWhenEmpty() {
      assertFalse(mgr.isMouseTrackingActive());
   }

   @Test
   @DisplayName("findByName returns null when empty")
   void findByNameNullWhenEmpty() {
      assertNull(mgr.findByName("local"));
   }

   @Test
   @DisplayName("findByBuffer returns null when empty")
   void findByBufferNullWhenEmpty() {
      assertNull(mgr.findByBuffer(null));
   }

   @Test
   @DisplayName("closeAll on empty manager is no-op")
   void closeAllEmptyNoOp() {
      mgr.closeAll();
      assertEquals(0, mgr.getSessionCount());
      assertEquals(-1, mgr.getActiveIndex());
   }

   @Test
   @DisplayName("forwardMouseEvent on empty manager is no-op")
   void forwardMouseEventEmptyNoOp() {
      // Should not throw even with no active session
      mgr.forwardMouseEvent(0, 1, 1, true);
   }

   @Test
   @DisplayName("notifyResize on empty manager is no-op")
   void notifyResizeEmptyNoOp() {
      // Should not throw even with no sessions
      mgr.notifyResize(24, 80);
   }

   // ================================================================
   // isShellBuffer — empty manager
   // ================================================================

   @Test
   @DisplayName("isShellBuffer returns false for null buffer")
   void isShellBufferNullFalse() {
      assertFalse(mgr.isShellBuffer(null));
   }

   // ================================================================
   // extractBufferText — text extraction for shell clipboard
   // ================================================================

   private static int bufSeq;

   private TextEdit<String> makeBuffer(String contents)
         throws java.io.IOException {
      String fname = "shellmgr_ext_" + (++bufSeq) + ".txt";
      String path = history.Testutil.testFile(fname).getPath();
      FileDescriptor.LocalFile.make(
         history.Testutil.testFile(fname)).delete();
      try (java.io.OutputStreamWriter w =
            new java.io.OutputStreamWriter(
               new java.io.FileOutputStream(path),
               java.nio.charset.StandardCharsets.UTF_8)) {
         w.write(contents);
      }
      FileDescriptor fd = FileDescriptor.make(path);
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      return te;
   }

   @Test
   @DisplayName("extractBufferText: single line partial")
   void extractSingleLinePartial() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = makeBuffer("Hello World\n");
         Position start = new Position(0, 1, "", "");
         Position end = new Position(5, 1, "", "");
         assertEquals("Hello",
            ShellManager.extractBufferText(buf, start, end));
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   @DisplayName("extractBufferText: full single line")
   void extractFullSingleLine() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = makeBuffer("Hello World\n");
         Position start = new Position(0, 1, "", "");
         Position end = new Position(11, 1, "", "");
         assertEquals("Hello World",
            ShellManager.extractBufferText(buf, start, end));
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   @DisplayName("extractBufferText: multi line")
   void extractMultiLine() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf =
            makeBuffer("aaa\nbbb\nccc\n");
         Position start = new Position(1, 1, "", "");
         Position end = new Position(2, 3, "", "");
         assertEquals("aa\nbbb\ncc",
            ShellManager.extractBufferText(buf, start, end));
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   @DisplayName("extractBufferText: reversed selection")
   void extractReversedSelection() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = makeBuffer("Hello World\n");
         Position start = new Position(5, 1, "", "");
         Position end = new Position(0, 1, "", "");
         assertEquals("Hello",
            ShellManager.extractBufferText(buf, start, end));
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   @DisplayName("extractBufferText: same position returns empty")
   void extractSamePositionEmpty() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = makeBuffer("Hello\n");
         Position pos = new Position(3, 1, "", "");
         assertEquals("",
            ShellManager.extractBufferText(buf, pos, pos));
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   @DisplayName("extractBufferText: x beyond line length clamps")
   void extractClampsBeyondLineLength() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = makeBuffer("Hi\n");
         Position start = new Position(0, 1, "", "");
         Position end = new Position(99, 1, "", "");
         assertEquals("Hi",
            ShellManager.extractBufferText(buf, start, end));
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   @DisplayName("extractBufferText: empty line returns empty")
   void extractEmptyLine() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = makeBuffer("\n");
         Position start = new Position(0, 1, "", "");
         Position end = new Position(0, 1, "", "");
         assertEquals("",
            ShellManager.extractBufferText(buf, start, end));
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   @DisplayName("extractBufferText: multi-line end beyond last line")
   void extractBeyondLastLine() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> buf = makeBuffer("abc\ndef\n");
         Position start = new Position(0, 1, "", "");
         Position end = new Position(3, 99, "", "");
         String text = ShellManager.extractBufferText(
            buf, start, end);
         assertTrue(text.contains("abc"),
            "should contain first line, got: " + text);
         assertTrue(text.contains("def"),
            "should contain second line, got: " + text);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ================================================================
   // getLeafProcessName — process tree walking
   // ================================================================

   @Test
   @DisplayName("getLeafProcessName returns name for current PID")
   void leafProcessCurrentPid() {
      long myPid = ProcessHandle.current().pid();
      String name = ShellSession.getLeafProcessName(myPid);
      // The current process is java — should return something
      assertNotNull(name, "should resolve current PID");
      assertFalse(name.isEmpty(), "name should not be empty");
   }

   @Test
   @DisplayName("getLeafProcessName returns null for invalid PID")
   void leafProcessInvalidPid() {
      // Use a valid-sized PID that is unlikely to exist
      String name = ShellSession.getLeafProcessName(99998L);
      // Either null or empty is acceptable for a non-existent PID
      assertTrue(null == name || name.isEmpty(),
         "should be null/empty for non-existent PID, got: " + name);
   }

   @Test
   @DisplayName("LABEL_UPDATE_INTERVAL_MS is positive")
   void labelUpdateIntervalIsPositive() {
      assertTrue(ShellSession.LABEL_UPDATE_INTERVAL_MS > 0,
         "label update interval must be positive");
   }

   @Test
   @DisplayName("getLeafProcessName strips path prefix from comm output")
   void leafProcessStripsPath() {
      // getLeafProcessName for our own JVM should return a bare name
      // (no '/' characters) because it strips the path prefix
      long myPid = ProcessHandle.current().pid();
      String name = ShellSession.getLeafProcessName(myPid);
      assertNotNull(name);
      assertFalse(name.contains("/"),
         "name should not contain path separators: " + name);
   }

   // ================================================================
   // Clipboard round-trip (skipped in headless environments)
   // ================================================================

   @Test
   @DisplayName("clipboard copy/read round-trip")
   void clipboardRoundTrip() throws Exception {
      if (java.awt.GraphicsEnvironment.isHeadless()) {
         return; // skip in headless CI
      }
      String testText = "javi-clipboard-test-" + System.nanoTime();
      java.awt.datatransfer.Clipboard clip =
         java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
      clip.setContents(
         new java.awt.datatransfer.StringSelection(testText),
         null);
      java.awt.datatransfer.Transferable tr = clip.getContents(null);
      assertNotNull(tr);
      assertTrue(tr.isDataFlavorSupported(
         java.awt.datatransfer.DataFlavor.stringFlavor));
      String result = (String) tr.getTransferData(
         java.awt.datatransfer.DataFlavor.stringFlavor);
      assertEquals(testText, result);
   }
}

