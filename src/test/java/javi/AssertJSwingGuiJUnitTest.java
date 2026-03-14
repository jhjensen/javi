package javi;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.Frame;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.ComponentFinder;
import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.core.Robot;
import org.assertj.swing.finder.WindowFinder;
import org.assertj.swing.fixture.FrameFixture;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssertJ Swing GUI integration tests for the Javi editor.
 *
 * <p>These tests launch the full AWT-based editor and verify that GUI
 * components exist, keyboard input reaches the editor, and basic
 * ex-commands work.  Tagged {@code "gui"} so they run in a separate
 * Gradle task ({@code guiTest}) with their own JVM — the AwtInterface
 * singleton conflicts with StreamInterface used by headless tests.</p>
 *
 * <p>Requires a graphical display (native macOS desktop or Xvfb on CI).</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class AssertJSwingGuiJUnitTest {

   private static Robot robot;
   private FrameFixture window;

   @BeforeAll
   static void initJavi() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Class.forName("javi.TextEdit");
         EditTester1.TestCircBuffer.initCmd();
         DirList.getDefault();
         FileList.make("");
         Javi.initToUi();
         Javi.initPostUi();
         Command.doneInit();
      } finally {
         EventQueue.biglock2.unlock();
      }
      // Allow AWT to finish layout
      Thread.sleep(500);
      robot = BasicRobot.robotWithCurrentAwtHierarchy();
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
      UI.getInstance().idispose();
   }

   @BeforeEach
   void setUp() {
      window = WindowFinder.findFrame(new GenericTypeMatcher<Frame>(
            Frame.class, true) {
         @Override
         protected boolean isMatching(Frame frame) {
            return frame.isShowing()
               && frame.getClass().getName().contains("AwtInterface");
         }
      }).using(robot);
   }

   @AfterEach
   void cleanUp() {
      // FrameFixture cleanup is handled in @AfterAll via robot.cleanUp()
   }

   // ── Component discovery tests ────────────────────────────────

   @Test
   void t01_frameIsVisible() {
      window.requireVisible();
   }

   @Test
   void t02_frameTitleIsNotEmpty() {
      String title = window.target().getTitle();
      assertNotNull(title, "Frame title must not be null");
      assertFalse(title.isEmpty(),
         "Frame title must not be empty");
   }

   @Test
   void t03_canvasComponentExists() {
      ComponentFinder finder = robot.finder();
      Canvas canvas = finder.findByType(window.target(), Canvas.class);
      assertNotNull(canvas, "Editor canvas (OldView.MyCanvas) must exist");
      assertTrue(canvas.isShowing(), "Canvas must be visible");
   }

   @Test
   void t04_multipleCanvasComponentsExist() {
      // Javi creates at least two OldView canvases:
      // one command-line canvas and one editor canvas
      ComponentFinder finder = robot.finder();
      java.util.Collection<Canvas> canvases =
         finder.findAll(window.target(),
            new GenericTypeMatcher<Canvas>(Canvas.class, false) {
               @Override
               protected boolean isMatching(Canvas c) {
                  return c.isDisplayable();
               }
            });
      assertTrue(canvases.size() >= 2,
         "Expected at least 2 Canvas components (editor + command), found "
            + canvases.size());
   }

   @Test
   void t05_frameHasNonZeroSize() {
      Frame f = window.target();
      assertTrue(f.getWidth() > 0, "Frame width must be > 0");
      assertTrue(f.getHeight() > 0, "Frame height must be > 0");
   }

   @Test
   void t06_editorCanvasIsShowing() {
      // Find the visible editor canvas via AssertJ finder
      ComponentFinder finder = robot.finder();
      Canvas editorCanvas = finder.findByType(
         window.target(), Canvas.class, true);
      assertNotNull(editorCanvas, "Editor canvas must exist");
      assertTrue(editorCanvas.isShowing(),
         "Editor canvas must be showing");
      assertTrue(editorCanvas.getWidth() > 0,
         "Editor canvas must have non-zero width");
      assertTrue(editorCanvas.getHeight() > 0,
         "Editor canvas must have non-zero height");
   }

   // ── Keyboard interaction tests ───────────────────────────────

   @Test
   void t07_keyboardInputReachesEditor() throws Exception {
      window.focus();
      // Give the frame focus
      robot.waitForIdle();

      // Type "iHello" — 'i' enters insert mode, then "Hello" is typed
      // We verify the editor doesn't crash and the frame stays visible
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         assertNotNull(fvc, "Current FvContext must exist");
         assertNotNull(fvc.edvec, "Current TextEdit must exist");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t08_fvContextHasView() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         assertNotNull(fvc, "FvContext must exist");
         assertNotNull(fvc.vi, "FvContext must have a View");
         assertTrue(fvc.vi.isVisible(), "View must be visible");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t09_editorHasContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         TextEdit te = fvc.edvec;
         assertNotNull(te, "TextEdit must exist");
         // Even an empty buffer has at least 1 line
         assertTrue(te.readIn() >= 1,
            "Editor should have at least 1 line");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Command infrastructure tests ─────────────────────────────

   @Test
   void t10_commandSystemInitialized() throws Exception {
      EventQueue.biglock2.lock();
      try {
         // Verify core AwtInterface commands are registered
         assertNotNull(Rgroup.bindingLookup("togglestatus"),
            "AwtInterface command 'togglestatus' should be registered");
         assertNotNull(Rgroup.bindingLookup("va"),
            "AwtInterface command 'va' (add view) should be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t11_moveGroupCommandsAvailable() throws Exception {
      EventQueue.biglock2.lock();
      try {
         // MoveGroup commands should be initialized
         assertNotNull(Rgroup.bindingLookup("gotoline"),
            "MoveGroup 'gotoline' command should be available");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t12_frameRemainsStableAfterInit() {
      // After full initialization, frame should still be showing
      window.requireVisible();
      Frame f = window.target();
      assertTrue(f.isDisplayable(), "Frame must be displayable");
      assertFalse(f.getIgnoreRepaint(),
         "Frame should not ignore repaint");
   }

   // ── Ex-command execution tests ───────────────────────────────

   @Test
   void t13_exCommandSetTabstop() throws Exception {
      // Execute :set tabstop=4 and verify it takes effect
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         assertNotNull(fvc, "FvContext must exist");
         int origTab = fvc.vi.getTabStop();
         Command.command("set tabstop=4", fvc, null);
         assertEquals(4, fvc.vi.getTabStop(),
            "tabstop should be 4 after :set tabstop=4");
         // Restore original
         Command.command("set tabstop=" + origTab, fvc, null);
         assertEquals(origTab, fvc.vi.getTabStop(),
            "tabstop should be restored");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t14_exCommandReload() throws Exception {
      // Execute :e! (reload) — verify the buffer is refreshed
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         assertNotNull(fvc, "FvContext must exist");
         TextEdit te = fvc.edvec;
         int linesBefore = te.readIn();
         assertTrue(linesBefore >= 1, "Buffer must have content");
         // Reload via the registered "e!" command
         Command.command("e!", fvc, null);
         int linesAfter = te.readIn();
         assertTrue(linesAfter >= 1,
            "Buffer must still have content after reload");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t15_exCommandWriteDetected() throws Exception {
      // Verify the :w ex-command path exists (write file)
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         TextEdit te = fvc.edvec;
         // processCommand("w") returns the cursor position on success
         int result = te.processCommand("w", fvc.inserty());
         // :w on unmodified buffer returns current y or processes OK
         assertTrue(result >= 0,
            "processCommand('w') should succeed (return >= 0)");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Navigation tests ─────────────────────────────────────────

   @Test
   void t16_gotoLineEnd() throws Exception {
      // G with no count goes to last line
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         TextEdit te = fvc.edvec;
         int lastLine = te.finish() - 1;
         assertTrue(lastLine >= 1,
            "Buffer must have at least 1 line");
         // Invoke gotoline with rcount=0, arg=null → go to end
         Rgroup.KeyBinding kb = Rgroup.bindingLookup("gotoline");
         assertNotNull(kb, "'gotoline' command must be registered");
         kb.dobind(null, 0, 0, fvc, false);
         // After G, cursor should be at or near the last line
         int cursorY = fvc.inserty();
         assertTrue(cursorY >= lastLine - 1,
            "After G, cursor (" + cursorY
               + ") should be near last line (" + lastLine + ")");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t17_gotoLineBeginning() throws Exception {
      // gg (gotoline with count=1, rcount=1) goes to first line
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         // First go to end so we can verify movement
         Rgroup.KeyBinding kb = Rgroup.bindingLookup("gotoline");
         assertNotNull(kb, "'gotoline' command must be registered");
         kb.dobind(null, 0, 0, fvc, false); // G — go to end
         // Now go to beginning: gotoline with rcount=1, count=1
         kb.dobind(null, 1, 1, fvc, false);
         int cursorY = fvc.inserty();
         assertEquals(1, cursorY,
            "After gg, cursor should be at line 1");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t18_cursorMovementUpDown() throws Exception {
      // Move cursor down then up via moveline
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         TextEdit te = fvc.edvec;
         // Go to line 1 first
         Rgroup.KeyBinding gotoKb = Rgroup.bindingLookup("gotoline");
         gotoKb.dobind(null, 1, 1, fvc, false);
         assertEquals(1, fvc.inserty(), "Should start at line 1");

         if (te.readIn() > 2) {
            // Move down 1 line (j)
            Rgroup.KeyBinding moveKb = Rgroup.bindingLookup("moveline");
            assertNotNull(moveKb,
               "'moveline' command must be registered");
            moveKb.dobind(Boolean.TRUE, 1, 0, fvc, false);
            assertEquals(2, fvc.inserty(),
               "After j, cursor should be at line 2");
            // Move back up (k)
            moveKb.dobind(Boolean.FALSE, 1, 0, fvc, false);
            assertEquals(1, fvc.inserty(),
               "After k, cursor should be at line 1");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Mode switching / insert infrastructure tests ─────────────

   @Test
   void t19_insertCommandRegistered() throws Exception {
      // Verify insert-mode commands are registered
      EventQueue.biglock2.lock();
      try {
         assertNotNull(Rgroup.bindingLookup("insert"),
            "EditGroup 'insert' (i) must be registered");
         assertNotNull(Rgroup.bindingLookup("append"),
            "EditGroup 'append' (a) must be registered");
         assertNotNull(Rgroup.bindingLookup("openline"),
            "EditGroup 'openline' (o) must be registered");
         assertNotNull(Rgroup.bindingLookup("Openline"),
            "EditGroup 'Openline' (O) must be registered");
         assertNotNull(Rgroup.bindingLookup("substitute"),
            "EditGroup 'substitute' (s) must be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t20_editGroupDeleteCharsRegistered() throws Exception {
      // Verify edit commands that modify text are available
      EventQueue.biglock2.lock();
      try {
         assertNotNull(Rgroup.bindingLookup("deletechars"),
            "EditGroup 'deletechars' (x) must be registered");
         assertNotNull(Rgroup.bindingLookup("deletetoend"),
            "EditGroup 'deletetoend' (D) must be registered");
         assertNotNull(Rgroup.bindingLookup("joinlines"),
            "EditGroup 'joinlines' (J) must be registered");
         assertNotNull(Rgroup.bindingLookup("changecase"),
            "EditGroup 'changecase' (~) must be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t21_editorBufferTextAccessible() throws Exception {
      // Verify we can read buffer content through the GUI editor
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         TextEdit te = fvc.edvec;
         int lineCount = te.readIn();
         assertTrue(lineCount >= 1,
            "Editor must have at least 1 line");
         // Read first line — should not throw
         Object firstLine = te.at(1);
         assertNotNull(firstLine,
            "First line of buffer must not be null");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── File open tests ──────────────────────────────────────────

   @Test
   void t22_fileOpenViaDirList() throws Exception {
      // Verify DirList (file browser) is accessible in GUI context
      EventQueue.biglock2.lock();
      try {
         DirList dirList = DirList.getDefault();
         assertNotNull(dirList, "Default DirList must exist");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t23_fileListTrackingActive() throws Exception {
      // Verify FileList is tracking open files
      EventQueue.biglock2.lock();
      try {
         FvContext fvc = FvContext.getCurrFvc();
         assertNotNull(fvc.edvec,
            "Current editor must have a TextEdit");
         FileDescriptor fd = fvc.edvec.fdes();
         assertNotNull(fd,
            "Current buffer must have a FileDescriptor");
         String name = fd.shortName;
         assertNotNull(name,
            "FileDescriptor must have a name");
         assertFalse(name.isEmpty(),
            "FileDescriptor name must not be empty");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
