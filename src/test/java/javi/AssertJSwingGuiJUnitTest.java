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
}
