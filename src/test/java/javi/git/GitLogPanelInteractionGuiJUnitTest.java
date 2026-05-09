package javi.git;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.ScrollPane;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;
import org.assertj.swing.fixture.FrameFixture;

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
 * GUI interaction tests for {@link GitLogPanel} — mouse events,
 * resize behavior, scroll state, empty/large graph handling,
 * and paint with clipping rectangles.
 *
 * <p>Exercises code paths not covered by the basic rendering tests:
 * mouse clicks dispatching selection, window resize triggering
 * preferred size recalculation, painting with clip bounds, and
 * stability under rapid repaint cycles.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class GitLogPanelInteractionGuiJUnitTest {

   private static Robot robot;
   private static Frame testFrame;
   private static GitLogPanel panel;
   private static FrameFixture window;
   private static List<GitLogGraph.Row> graphRows;

   @BeforeAll
   static void setUp() throws Exception {
      // Build a 10-commit linear history for interaction testing
      List<GitLogEntry> entries = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
         String sha = String.format("%07d", i).repeat(6).substring(0, 40);
         String parentSha = i < 9
            ? String.format("%07d", i + 1).repeat(6).substring(0, 40)
            : null;
         List<String> parents = parentSha != null
            ? Collections.singletonList(parentSha)
            : Collections.emptyList();
         String decoration = i == 0 ? "HEAD -> main" : null;
         entries.add(new GitLogEntry(sha, parents,
            "Commit message " + i, "author" + (i % 3),
            "2026-03-" + String.format("%02d", 20 - i) + " 10:00:00",
            decoration));
      }

      graphRows = GitLogGraph.assignLanes(entries);

      Constructor<GitLogPanel> ctor = GitLogPanel.class
         .getDeclaredConstructor(List.class);
      ctor.setAccessible(true);
      panel = ctor.newInstance(graphRows);

      java.awt.EventQueue.invokeAndWait(() -> {
         testFrame = new Frame("GitLogPanel Interaction Test");
         ScrollPane scroll = new ScrollPane(
            ScrollPane.SCROLLBARS_AS_NEEDED);
         scroll.add(panel);
         testFrame.add(scroll);
         testFrame.setSize(900, 500);
         testFrame.setLocationRelativeTo(null);
         testFrame.setVisible(true);
      });

      Thread.sleep(300);
      robot = BasicRobot.robotWithCurrentAwtHierarchy();
      window = new FrameFixture(robot, testFrame);
   }

   @AfterAll
   static void tearDown() {
      if (window != null)
         window.cleanUp();
      if (testFrame != null) {
         java.awt.EventQueue.invokeLater(() -> testFrame.dispose());
      }
   }

   // ── Mouse click on panel ─────────────────────────────────────

   @Test
   void t01_mouseClickOnFirstRow() {
      // Click near the first row (y = ROW_HEIGHT/2 = 12)
      MouseEvent me = new MouseEvent(panel,
         MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
         0, 50, 12, 1, false, MouseEvent.BUTTON1);
      panel.dispatchEvent(me);
      robot.waitForIdle();
      assertTrue(panel.isShowing(),
         "Panel should remain visible after click");
   }

   @Test
   void t02_mouseClickOnLastRow() {
      // Click on the last row (y ~ 10 * 24 - 12 = 228)
      MouseEvent me = new MouseEvent(panel,
         MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
         0, 50, 228, 1, false, MouseEvent.BUTTON1);
      panel.dispatchEvent(me);
      robot.waitForIdle();
      assertTrue(panel.isShowing(),
         "Panel should remain visible after last-row click");
   }

   @Test
   void t03_mouseClickOutsideRows() {
      // Click below all rows
      Dimension pref = panel.getPreferredSize();
      MouseEvent me = new MouseEvent(panel,
         MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
         0, 50, pref.height + 50, 1, false, MouseEvent.BUTTON1);
      panel.dispatchEvent(me);
      robot.waitForIdle();
      assertTrue(panel.isShowing(),
         "Panel should remain visible after out-of-bounds click");
   }

   @Test
   void t04_rightClickOnPanel() {
      MouseEvent me = new MouseEvent(panel,
         MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
         0, 50, 36, 1, false, MouseEvent.BUTTON3);
      panel.dispatchEvent(me);
      robot.waitForIdle();
      assertTrue(panel.isShowing(),
         "Right click should not crash panel");
   }

   // ── Resize behavior ──────────────────────────────────────────

   @Test
   void t05_resizeFrameUpdatesPanel() throws Exception {
      java.awt.EventQueue.invokeAndWait(() ->
         testFrame.setSize(600, 300));
      robot.waitForIdle();
      Thread.sleep(100);
      assertTrue(panel.isShowing(),
         "Panel should be visible after resize");
      Dimension size = panel.getSize();
      assertTrue(size.width > 0 && size.height > 0,
         "Panel should have positive size after resize");
   }

   @Test
   void t06_resizeToMinimal() throws Exception {
      java.awt.EventQueue.invokeAndWait(() ->
         testFrame.setSize(200, 100));
      robot.waitForIdle();
      Thread.sleep(100);
      assertTrue(panel.isShowing(),
         "Panel should be visible at minimal size");
      // Restore
      java.awt.EventQueue.invokeAndWait(() ->
         testFrame.setSize(900, 500));
      robot.waitForIdle();
   }

   // ── Paint with clipping ──────────────────────────────────────

   @Test
   void t07_paintWithClipBoundsSmall() {
      BufferedImage img = new BufferedImage(200, 100,
         BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      try {
         g.setClip(0, 0, 200, 100);
         panel.paint(g);
         assertTrue(true, "paint with small clip should not throw");
      } finally {
         g.dispose();
      }
   }

   @Test
   void t08_paintWithZeroClip() {
      BufferedImage img = new BufferedImage(1, 1,
         BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      try {
         g.setClip(0, 0, 0, 0);
         panel.paint(g);
         assertTrue(true, "paint with zero clip should not throw");
      } finally {
         g.dispose();
      }
   }

   @Test
   void t09_paintFullSize() {
      Dimension pref = panel.getPreferredSize();
      BufferedImage img = new BufferedImage(pref.width, pref.height,
         BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      try {
         panel.paint(g);
         assertTrue(true, "Full-size paint should not throw");
      } finally {
         g.dispose();
      }
   }

   // ── Rapid repaint cycle ──────────────────────────────────────

   @Test
   void t10_rapidRepaintStable() {
      for (int i = 0; i < 20; i++) {
         panel.repaint();
      }
      robot.waitForIdle();
      assertTrue(panel.isShowing(),
         "Panel should be stable after rapid repaints");
   }

   // ── Preferred size correctness ───────────────────────────────

   @Test
   void t11_preferredSizeReflectsRows() {
      Dimension pref = panel.getPreferredSize();
      // 10 rows * 24 + 24 = 264
      assertTrue(pref.height >= 10 * 24,
         "Preferred height should accommodate 10 rows, got "
         + pref.height);
   }

   @Test
   void t12_preferredWidthIncludesTextArea() {
      Dimension pref = panel.getPreferredSize();
      // Width should include lane area + text area (at least 600)
      assertTrue(pref.width >= 600,
         "Preferred width should include text area, got "
         + pref.width);
   }

   // ── Graph lane correctness for linear history ────────────────

   @Test
   void t13_allLinearCommitsInLaneZero() {
      for (GitLogGraph.Row row : graphRows) {
         assertEquals(0, row.lane,
            "Linear commits should all be in lane 0, got lane "
            + row.lane + " for " + row.entry.subject);
      }
   }

   @Test
   void t14_parentLanesAllZeroForLinear() {
      for (int i = 0; i < graphRows.size() - 1; i++) {
         GitLogGraph.Row row = graphRows.get(i);
         assertEquals(1, row.parentLanes.length,
            "Linear commit should have 1 parent lane");
         assertEquals(0, row.parentLanes[0],
            "Parent lane should be 0 for linear");
      }
   }

   @Test
   void t15_rootHasNoParents() {
      GitLogGraph.Row last = graphRows.get(graphRows.size() - 1);
      assertEquals(0, last.parentLanes.length,
         "Root commit should have 0 parent lanes");
   }

   // ── Panel background color ───────────────────────────────────

   @Test
   void t16_backgroundIsDark() {
      Color bg = panel.getBackground();
      assertNotNull(bg);
      assertTrue(bg.getRed() < 50 && bg.getGreen() < 50
         && bg.getBlue() < 50,
         "Background should be very dark, got " + bg);
   }

   // ── Double-click event ───────────────────────────────────────

   @Test
   void t17_doubleClickDoesNotCrash() {
      MouseEvent me = new MouseEvent(panel,
         MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
         0, 100, 48, 2, false, MouseEvent.BUTTON1);
      panel.dispatchEvent(me);
      robot.waitForIdle();
      assertTrue(panel.isShowing(),
         "Double click should not crash");
   }

   // ── Mouse drag on panel ──────────────────────────────────────

   @Test
   void t18_mouseDragDoesNotCrash() {
      MouseEvent press = new MouseEvent(panel,
         MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
         0, 50, 36, 1, false, MouseEvent.BUTTON1);
      panel.dispatchEvent(press);
      MouseEvent drag = new MouseEvent(panel,
         MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
         0, 100, 100, 1, false, MouseEvent.BUTTON1);
      panel.dispatchEvent(drag);
      MouseEvent release = new MouseEvent(panel,
         MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
         0, 100, 100, 1, false, MouseEvent.BUTTON1);
      panel.dispatchEvent(release);
      robot.waitForIdle();
      assertTrue(panel.isShowing(),
         "Drag should not crash panel");
   }

   // ── findParentRow via paint ───────────────────────────────────

   @Test
   void t19_paintWithMergeTopology() throws Exception {
      // Build a merge commit graph
      List<GitLogEntry> entries = new ArrayList<>();
      entries.add(new GitLogEntry(
         "fff0000000000000000000000000000000000000f",
         Arrays.asList(
            "aaa0000000000000000000000000000000000000a",
            "bbb0000000000000000000000000000000000000b"),
         "Merge commit", "dev", "2026-03-01 10:00:00",
         "HEAD -> main"));
      entries.add(new GitLogEntry(
         "aaa0000000000000000000000000000000000000a",
         Collections.singletonList(
            "ccc0000000000000000000000000000000000000c"),
         "Feature A", "dev", "2026-02-28 10:00:00", null));
      entries.add(new GitLogEntry(
         "bbb0000000000000000000000000000000000000b",
         Collections.singletonList(
            "ccc0000000000000000000000000000000000000c"),
         "Feature B", "dev", "2026-02-27 10:00:00", null));
      entries.add(new GitLogEntry(
         "ccc0000000000000000000000000000000000000c",
         Collections.emptyList(),
         "Root", "dev", "2026-02-26 10:00:00", null));

      List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(entries);
      Constructor<GitLogPanel> ctor = GitLogPanel.class
         .getDeclaredConstructor(List.class);
      ctor.setAccessible(true);
      GitLogPanel mergePanel = ctor.newInstance(rows);

      Dimension pref = mergePanel.getPreferredSize();
      BufferedImage img = new BufferedImage(pref.width, pref.height,
         BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      try {
         mergePanel.paint(g);
         assertTrue(true, "Paint merge topology should not throw");
      } finally {
         g.dispose();
      }
   }

   // ── Empty panel stability ────────────────────────────────────

   @Test
   void t20_emptyPanelPaintSafe() throws Exception {
      // Create panel with a single entry (minimum graph)
      List<GitLogEntry> entries = new ArrayList<>();
      entries.add(new GitLogEntry(
         "0000000000000000000000000000000000000000",
         Collections.emptyList(),
         "Only commit", "dev", "2026-01-01 00:00:00", null));

      List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(entries);
      Constructor<GitLogPanel> ctor = GitLogPanel.class
         .getDeclaredConstructor(List.class);
      ctor.setAccessible(true);
      GitLogPanel singlePanel = ctor.newInstance(rows);

      Dimension pref = singlePanel.getPreferredSize();
      assertTrue(pref.height >= 24,
         "Single-entry panel should have height for 1 row");

      BufferedImage img = new BufferedImage(
         Math.max(pref.width, 100), Math.max(pref.height, 50),
         BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      try {
         singlePanel.paint(g);
         assertTrue(true, "Single-entry paint should not throw");
      } finally {
         g.dispose();
      }
   }

   // ── Large graph stability ────────────────────────────────────

   @Test
   void t21_largePanelPreferredSize() throws Exception {
      List<GitLogEntry> entries = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
         String sha = String.format("a%039d", i);
         String parentSha = i < 199
            ? String.format("a%039d", i + 1) : null;
         List<String> parents = parentSha != null
            ? Collections.singletonList(parentSha)
            : Collections.emptyList();
         entries.add(new GitLogEntry(sha, parents,
            "Commit " + i, "dev", "2026-01-01 00:00:00", null));
      }

      List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(entries);
      Constructor<GitLogPanel> ctor = GitLogPanel.class
         .getDeclaredConstructor(List.class);
      ctor.setAccessible(true);
      GitLogPanel largePanel = ctor.newInstance(rows);

      Dimension pref = largePanel.getPreferredSize();
      assertTrue(pref.height >= 200 * 24,
         "Large panel should accommodate 200 rows, got "
         + pref.height);
   }

   // ── Frame is properly showing ────────────────────────────────

   @Test
   void t22_frameContainsCanvas() {
      window.requireVisible();
      Canvas found = robot.finder().findByType(testFrame, Canvas.class);
      assertNotNull(found, "Frame should contain GitLogPanel Canvas");
      assertEquals(panel, found,
         "Found canvas should be our panel");
   }
}
