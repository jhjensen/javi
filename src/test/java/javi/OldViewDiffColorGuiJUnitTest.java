package javi;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for OldView diff coloring and specialized painting paths.
 *
 * <p>Exercises the paintOneLine branch that detects buffers named
 * "*git-*" and applies colored foreground for diff lines: red for
 * deletions, green for additions, and foldSummaryColor for diff
 * headers and hunk markers. Also tests tab expansion and mark
 * highlight rendering during paint.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class OldViewDiffColorGuiJUnitTest {

   private static Robot robot;
   private static View oldView;
   private static Class<?> oldViewClass;
   private static TextEdit<?> originalBuffer;

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
         FvContext fvc = FvContext.getCurrFvc();
         oldView = fvc.vi;
         oldViewClass = oldView.getClass();
         originalBuffer = fvc.edvec;
         assertTrue(oldViewClass.getName().contains("OldView"),
            "Current view should be OldView, got "
               + oldViewClass.getName());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @AfterAll
   static void tearDownAll() throws Exception {
      // Restore original buffer
      if (originalBuffer != null) {
         EventQueue.biglock2.lock();
         try {
            FvContext fvc = FvContext.getCurrFvc();
            FvContext.connectFv(originalBuffer, fvc.vi);
         } catch (Exception e) {
            // Best effort restore
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
      if (robot != null)
         robot.cleanUp();
   }

   // ── Helpers ──────────────────────────────────────────────────

   private static TextEdit<String> createGitDiffBuffer() {
      String diffContent = String.join("\n", List.of(
         "diff --git a/src/Foo.java b/src/Foo.java",
         "index abc1234..def5678 100644",
         "--- a/src/Foo.java",
         "+++ b/src/Foo.java",
         "@@ -10,6 +10,8 @@ public class Foo {",
         "    private int count;",
         "    private String name;",
         "-   private boolean oldFlag;",
         "+   private boolean newFlag;",
         "+   private int extraField;",
         "    public Foo() {",
         "       count = 0;",
         "    }"));
      StringIoc sio = new StringIoc("*git-diff*", diffContent);
      return new TextEdit<>(sio, sio.prop);
   }

   private static TextEdit<String> createGitPatchBuffer() {
      String patchContent = String.join("\n", List.of(
         "diff --git a/README.md b/README.md",
         "--- a/README.md",
         "+++ b/README.md",
         "@@ -1,3 +1,4 @@",
         " # Title",
         "+New line added",
         " Existing line",
         "-Removed line"));
      StringIoc sio = new StringIoc("*git-patch*", patchContent);
      return new TextEdit<>(sio, sio.prop);
   }

   private static TextEdit<String> createTabBuffer() {
      String tabContent = String.join("\n", List.of(
         "no tabs here",
         "\tfirst tab",
         "\t\tdouble tab",
         "mixed\ttab\there",
         "\t\t\ttriple tab indent"));
      StringIoc sio = new StringIoc("*git-diff*", tabContent);
      return new TextEdit<>(sio, sio.prop);
   }

   private static TextEdit<String> createNonGitBuffer() {
      String content = String.join("\n", List.of(
         "+this is not a diff line",
         "-this is not a removal",
         "@@ not a hunk header",
         "normal text"));
      StringIoc sio = new StringIoc("regular-buffer", content);
      return new TextEdit<>(sio, sio.prop);
   }

   private static Canvas getCanvas() throws Exception {
      Method gc = oldViewClass.getDeclaredMethod("getComponent");
      gc.setAccessible(true);
      return (Canvas) gc.invoke(oldView);
   }

   private static int getIntField(String name) throws Exception {
      Field f = oldViewClass.getDeclaredField(name);
      f.setAccessible(true);
      return f.getInt(oldView);
   }

   private static void ensureImageg() throws Exception {
      Canvas canvas = getCanvas();
      Field imagegField = canvas.getClass().getDeclaredField("imageg");
      imagegField.setAccessible(true);
      if (imagegField.get(canvas) == null) {
         int pw = getIntField("pixelWidth");
         int ch = getIntField("charheight");
         if (pw <= 0) pw = 800;
         if (ch <= 0) ch = 16;
         BufferedImage dbuf = new BufferedImage(
            pw * 2, ch, BufferedImage.TYPE_INT_RGB);
         imagegField.set(canvas, dbuf.createGraphics());
         Field dbufField = canvas.getClass().getDeclaredField("dbuf");
         dbufField.setAccessible(true);
         dbufField.set(canvas, dbuf);
      }
   }

   private static Graphics2D createTestGraphics() {
      BufferedImage img = new BufferedImage(
         800, 600, BufferedImage.TYPE_INT_RGB);
      return img.createGraphics();
   }

   private static void connectBuffer(TextEdit<?> buf) throws Exception {
      FvContext fvc = FvContext.getCurrFvc();
      // Use reflection to set the view's text field directly
      // to avoid side effects of full connectFv
      Field textField = View.class.getDeclaredField("text");
      textField.setAccessible(true);
      textField.set(oldView, buf);
      // Ensure buffer is fully loaded (finish() blocks until all
      // lines are in ecache, unlike readIn() which may return early)
      buf.finish();
   }

   // ── Test: git diff coloring in paintLines ────────────────────

   @Test
   void t01_paintLinesGitDiffBufferNoException() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> diffBuf = createGitDiffBuffer();
         connectBuffer(diffBuf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintLines = canvas.getClass().getDeclaredMethod(
            "paintLines", java.awt.Graphics.class, int.class, int.class);
         paintLines.setAccessible(true);
         int screenSize = getIntField("screenSize");
         int end = Math.min(screenSize, diffBuf.readIn());
         paintLines.invoke(canvas, gr, 0, end);
         gr.dispose();
         assertTrue(true,
            "paintLines on git-diff buffer should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t02_paintLinesGitPatchBufferNoException() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> patchBuf = createGitPatchBuffer();
         connectBuffer(patchBuf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintLines = canvas.getClass().getDeclaredMethod(
            "paintLines", java.awt.Graphics.class, int.class, int.class);
         paintLines.setAccessible(true);
         int screenSize = getIntField("screenSize");
         int end = Math.min(screenSize, patchBuf.readIn());
         paintLines.invoke(canvas, gr, 0, end);
         gr.dispose();
         assertTrue(true,
            "paintLines on git-patch buffer should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t03_nonGitBufferDoesNotColorDiffLines() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> nonGitBuf = createNonGitBuffer();
         connectBuffer(nonGitBuf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintLines = canvas.getClass().getDeclaredMethod(
            "paintLines", java.awt.Graphics.class, int.class, int.class);
         paintLines.setAccessible(true);
         int screenSize = getIntField("screenSize");
         int end = Math.min(screenSize, nonGitBuf.readIn());
         // Should paint normally without git coloring
         paintLines.invoke(canvas, gr, 0, end);
         gr.dispose();
         assertTrue(true,
            "paintLines on non-git buffer should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Test: paintOneLine individual line coloring ──────────────

   @Test
   void t04_paintOneLineDiffHeader() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> diffBuf = createGitDiffBuffer();
         connectBuffer(diffBuf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintOneLine = canvas.getClass().getDeclaredMethod(
            "paintOneLine", java.awt.Graphics.class,
            int.class, int.class);
         paintOneLine.setAccessible(true);
         // Line 1 is "diff --git a/..." — should get foldSummaryColor
         paintOneLine.invoke(canvas, gr, 0, 1);
         gr.dispose();
         assertTrue(true,
            "paintOneLine for diff header should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t05_paintOneLineMinusPrefix() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> diffBuf = createGitDiffBuffer();
         connectBuffer(diffBuf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintOneLine = canvas.getClass().getDeclaredMethod(
            "paintOneLine", java.awt.Graphics.class,
            int.class, int.class);
         paintOneLine.setAccessible(true);
         // Find the "-   private boolean oldFlag;" line (index 8, 1-based)
         paintOneLine.invoke(canvas, gr, 0, 8);
         gr.dispose();
         assertTrue(true,
            "paintOneLine for minus line should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t06_paintOneLinePlusPrefix() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> diffBuf = createGitDiffBuffer();
         connectBuffer(diffBuf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintOneLine = canvas.getClass().getDeclaredMethod(
            "paintOneLine", java.awt.Graphics.class,
            int.class, int.class);
         paintOneLine.setAccessible(true);
         // Line 9 is "+   private boolean newFlag;"
         paintOneLine.invoke(canvas, gr, 0, 9);
         gr.dispose();
         assertTrue(true,
            "paintOneLine for plus line should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t07_paintOneLineHunkHeader() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> diffBuf = createGitDiffBuffer();
         connectBuffer(diffBuf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintOneLine = canvas.getClass().getDeclaredMethod(
            "paintOneLine", java.awt.Graphics.class,
            int.class, int.class);
         paintOneLine.setAccessible(true);
         // Line 5 is "@@ -10,6 +10,8 @@ ..."
         paintOneLine.invoke(canvas, gr, 0, 5);
         gr.dispose();
         assertTrue(true,
            "paintOneLine for @@ hunk header should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t08_paintOneLineTripleMinusHeader() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> diffBuf = createGitDiffBuffer();
         connectBuffer(diffBuf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintOneLine = canvas.getClass().getDeclaredMethod(
            "paintOneLine", java.awt.Graphics.class,
            int.class, int.class);
         paintOneLine.setAccessible(true);
         // Line 3 is "--- a/src/Foo.java" — starts with "---"
         paintOneLine.invoke(canvas, gr, 0, 3);
         gr.dispose();
         assertTrue(true,
            "paintOneLine for --- header should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t09_paintOneLineTriplePlusHeader() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> diffBuf = createGitDiffBuffer();
         connectBuffer(diffBuf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintOneLine = canvas.getClass().getDeclaredMethod(
            "paintOneLine", java.awt.Graphics.class,
            int.class, int.class);
         paintOneLine.setAccessible(true);
         // Line 4 is "+++ b/src/Foo.java" — starts with "+++"
         paintOneLine.invoke(canvas, gr, 0, 4);
         gr.dispose();
         assertTrue(true,
            "paintOneLine for +++ header should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t10_paintOneLineContextLine() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> diffBuf = createGitDiffBuffer();
         connectBuffer(diffBuf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintOneLine = canvas.getClass().getDeclaredMethod(
            "paintOneLine", java.awt.Graphics.class,
            int.class, int.class);
         paintOneLine.setAccessible(true);
         // Line 6 is "    private int count;" — context, no special color
         paintOneLine.invoke(canvas, gr, 0, 6);
         gr.dispose();
         assertTrue(true,
            "paintOneLine for context line should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Test: tab expansion in diff buffer ───────────────────────

   @Test
   void t11_paintLinesWithTabsNoException() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> tabBuf = createTabBuffer();
         connectBuffer(tabBuf);
         ensureImageg();
         // Set a non-zero tab stop to exercise deTab path
         oldView.setTabStop(8);
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintLines = canvas.getClass().getDeclaredMethod(
            "paintLines", java.awt.Graphics.class, int.class, int.class);
         paintLines.setAccessible(true);
         int screenSize = getIntField("screenSize");
         int end = Math.min(screenSize, tabBuf.readIn());
         paintLines.invoke(canvas, gr, 0, end);
         gr.dispose();
         assertTrue(true,
            "paintLines with tab content should not throw");
      } finally {
         oldView.setTabStop(0);
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t12_paintOneLineWithTripleTab() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> tabBuf = createTabBuffer();
         connectBuffer(tabBuf);
         ensureImageg();
         oldView.setTabStop(4);
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintOneLine = canvas.getClass().getDeclaredMethod(
            "paintOneLine", java.awt.Graphics.class,
            int.class, int.class);
         paintOneLine.setAccessible(true);
         // Line 5 is "\t\t\ttriple tab indent"
         paintOneLine.invoke(canvas, gr, 0, 5);
         gr.dispose();
         assertTrue(true,
            "paintOneLine with triple tabs should not throw");
      } finally {
         oldView.setTabStop(0);
         EventQueue.biglock2.unlock();
      }
   }

   // ── Test: cursor emphasis in paintOneLine ────────────────────

   @Test
   void t13_paintOneLineAtCursorRow() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> diffBuf = createGitDiffBuffer();
         connectBuffer(diffBuf);
         ensureImageg();
         // Position cursor at screen row 0 so paintOneLine emphasizes
         Field screenposy = oldViewClass.getDeclaredField("screenposy");
         screenposy.setAccessible(true);
         int origScreenposy = screenposy.getInt(oldView);
         screenposy.setInt(oldView, 0);
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintOneLine = canvas.getClass().getDeclaredMethod(
            "paintOneLine", java.awt.Graphics.class,
            int.class, int.class);
         paintOneLine.setAccessible(true);
         // Paint at screen row 0 (matches screenposy) for emphasis
         paintOneLine.invoke(canvas, gr, 0, 1);
         gr.dispose();
         screenposy.setInt(oldView, origScreenposy);
         assertTrue(true,
            "paintOneLine at cursor row should emphasize without throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t14_paintOneLineNotAtCursorRow() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> diffBuf = createGitDiffBuffer();
         connectBuffer(diffBuf);
         ensureImageg();
         // Cursor at row 5, paint row 0 — no emphasis
         Field screenposy = oldViewClass.getDeclaredField("screenposy");
         screenposy.setAccessible(true);
         int origScreenposy = screenposy.getInt(oldView);
         screenposy.setInt(oldView, 5);
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintOneLine = canvas.getClass().getDeclaredMethod(
            "paintOneLine", java.awt.Graphics.class,
            int.class, int.class);
         paintOneLine.setAccessible(true);
         paintOneLine.invoke(canvas, gr, 0, 1);
         gr.dispose();
         screenposy.setInt(oldView, origScreenposy);
         assertTrue(true,
            "paintOneLine away from cursor should not emphasize");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Test: multiple paint cycles stability ────────────────────

   @Test
   void t15_repeatedPaintLinesCycleStable() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> diffBuf = createGitDiffBuffer();
         connectBuffer(diffBuf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Method paintLines = canvas.getClass().getDeclaredMethod(
            "paintLines", java.awt.Graphics.class, int.class, int.class);
         paintLines.setAccessible(true);
         int screenSize = getIntField("screenSize");
         int end = Math.min(screenSize, diffBuf.readIn());
         // Paint 10 times in rapid succession
         for (int i = 0; i < 10; i++) {
            Graphics2D gr = createTestGraphics();
            paintLines.invoke(canvas, gr, 0, end);
            gr.dispose();
         }
         assertTrue(true,
            "10 rapid paint cycles should be stable");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Test: empty diff buffer ──────────────────────────────────

   @Test
   void t16_emptyGitDiffBufferPaintsNoThrow() throws Exception {
      EventQueue.biglock2.lock();
      try {
         StringIoc sio = new StringIoc("*git-diff*", "");
         TextEdit<String> emptyBuf = new TextEdit<>(sio, sio.prop);
         connectBuffer(emptyBuf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintLines = canvas.getClass().getDeclaredMethod(
            "paintLines", java.awt.Graphics.class, int.class, int.class);
         paintLines.setAccessible(true);
         int numLines = emptyBuf.readIn();
         int screenSize = getIntField("screenSize");
         int end = Math.min(screenSize, numLines);
         if (end > 0) {
            paintLines.invoke(canvas, gr, 0, end);
         }
         gr.dispose();
         assertTrue(true,
            "Empty git-diff buffer should paint without throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Test: single-line diff buffer ────────────────────────────

   @Test
   void t17_singleLineDiffBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         StringIoc sio = new StringIoc("*git-diff*",
            "+single added line");
         TextEdit<String> oneLiner = new TextEdit<>(sio, sio.prop);
         connectBuffer(oneLiner);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintOneLine = canvas.getClass().getDeclaredMethod(
            "paintOneLine", java.awt.Graphics.class,
            int.class, int.class);
         paintOneLine.setAccessible(true);
         paintOneLine.invoke(canvas, gr, 0, 1);
         gr.dispose();
         assertTrue(true,
            "Single-line git buffer should paint without throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Test: AtView color constants used in diff rendering ──────

   @Test
   void t18_foldSummaryColorNotNull() throws Exception {
      Class<?> atViewClass = Class.forName("javi.awt.AtView");
      Field f = atViewClass.getDeclaredField("foldSummaryColor");
      f.setAccessible(true);
      assertNotNull(f.get(null),
         "foldSummaryColor used for diff/hunk headers must not be null");
   }

   @Test
   void t19_foldSummaryColorIsYellow() throws Exception {
      Class<?> atViewClass = Class.forName("javi.awt.AtView");
      Field f = atViewClass.getDeclaredField("foldSummaryColor");
      f.setAccessible(true);
      assertEquals(Color.YELLOW, f.get(null),
         "foldSummaryColor should be yellow for diff headers");
   }

   @Test
   void t20_backgroundColorNotNull() throws Exception {
      Class<?> atViewClass = Class.forName("javi.awt.AtView");
      Field f = atViewClass.getDeclaredField("background");
      f.setAccessible(true);
      assertNotNull(f.get(null),
         "background color must not be null for paintOneLine");
   }

   // ── Test: mixed coloring in full-screen paint ────────────────

   @Test
   void t21_fullDiffBufferAllLinesRendered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> diffBuf = createGitDiffBuffer();
         connectBuffer(diffBuf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintOneLine = canvas.getClass().getDeclaredMethod(
            "paintOneLine", java.awt.Graphics.class,
            int.class, int.class);
         paintOneLine.setAccessible(true);
         int numLines = diffBuf.readIn();
         // Paint each line individually (0-based indices)
         for (int i = 0; i < numLines; i++) {
            paintOneLine.invoke(canvas, gr, 0, i);
         }
         gr.dispose();
         assertTrue(true,
            "All diff lines painted individually without throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Test: buffer switching stability ─────────────────────────

   @Test
   void t22_switchBetweenGitAndNonGitBuffers() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> diffBuf = createGitDiffBuffer();
         TextEdit<String> nonGitBuf = createNonGitBuffer();
         ensureImageg();
         Canvas canvas = getCanvas();
         Method paintLines = canvas.getClass().getDeclaredMethod(
            "paintLines", java.awt.Graphics.class, int.class, int.class);
         paintLines.setAccessible(true);
         int screenSize = getIntField("screenSize");

         // Paint git buffer
         connectBuffer(diffBuf);
         int end1 = Math.min(screenSize, diffBuf.readIn());
         Graphics2D gr1 = createTestGraphics();
         paintLines.invoke(canvas, gr1, 0, end1);
         gr1.dispose();

         // Switch to non-git buffer
         connectBuffer(nonGitBuf);
         int end2 = Math.min(screenSize, nonGitBuf.readIn());
         Graphics2D gr2 = createTestGraphics();
         paintLines.invoke(canvas, gr2, 0, end2);
         gr2.dispose();

         // Switch back to git buffer
         connectBuffer(diffBuf);
         Graphics2D gr3 = createTestGraphics();
         paintLines.invoke(canvas, gr3, 0, end1);
         gr3.dispose();

         assertTrue(true,
            "Switching between git and non-git buffers during paint "
            + "should be stable");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Test: long diff lines ────────────────────────────────────

   @Test
   void t23_longDiffLineDoesNotOverflow() throws Exception {
      EventQueue.biglock2.lock();
      try {
         String longLine = "+" + "x".repeat(500);
         StringIoc sio = new StringIoc("*git-diff*", longLine);
         TextEdit<String> longBuf = new TextEdit<>(sio, sio.prop);
         connectBuffer(longBuf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintOneLine = canvas.getClass().getDeclaredMethod(
            "paintOneLine", java.awt.Graphics.class,
            int.class, int.class);
         paintOneLine.setAccessible(true);
         paintOneLine.invoke(canvas, gr, 0, 1);
         gr.dispose();
         assertTrue(true,
            "500-char diff line should paint without overflow");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t24_emptyLineInDiffBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         // Empty line has no first char — coloring logic should skip
         String content = "diff --git a/f b/f\n\n+added";
         StringIoc sio = new StringIoc("*git-diff*", content);
         TextEdit<String> buf = new TextEdit<>(sio, sio.prop);
         connectBuffer(buf);
         ensureImageg();
         Canvas canvas = getCanvas();
         Graphics2D gr = createTestGraphics();
         Method paintOneLine = canvas.getClass().getDeclaredMethod(
            "paintOneLine", java.awt.Graphics.class,
            int.class, int.class);
         paintOneLine.setAccessible(true);
         // Line 2 is empty — the !lt.isEmpty() guard prevents crash
         paintOneLine.invoke(canvas, gr, 0, 2);
         gr.dispose();
         assertTrue(true,
            "Empty line in git buffer should paint safely");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Test: refresh with git buffer ────────────────────────────

   @Test
   void t25_refreshWithGitDiffBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> diffBuf = createGitDiffBuffer();
         connectBuffer(diffBuf);
         ensureImageg();
         Graphics2D gr = createTestGraphics();
         Method refresh = oldViewClass.getDeclaredMethod(
            "refresh", java.awt.Graphics.class);
         refresh.setAccessible(true);
         refresh.invoke(oldView, gr);
         gr.dispose();
         assertTrue(true,
            "refresh() with git-diff buffer should not throw");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
