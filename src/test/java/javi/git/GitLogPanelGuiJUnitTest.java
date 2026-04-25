package javi.git;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.ScrollPane;
import java.lang.reflect.Constructor;
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
 * AssertJ Swing GUI tests for {@link GitLogPanel} AWT Canvas rendering.
 *
 * <p>Creates a GitLogPanel via reflection (private constructor), adds it
 * to a Frame, and verifies rendering, sizing, and paint behavior.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class GitLogPanelGuiJUnitTest {

   private static Robot robot;
   private static Frame testFrame;
   private static GitLogPanel panel;
   private static FrameFixture window;
   private static List<GitLogGraph.Row> graphRows;

   @BeforeAll
   static void setUp() throws Exception {
      // Build synthetic git log entries
      List<GitLogEntry> entries = new ArrayList<>();
      entries.add(new GitLogEntry(
         "aaaaaaa1111111222222233333334444444aaaaaaa",
         Collections.singletonList(
            "bbbbbbb1111111222222233333334444444bbbbbbb"),
         "Initial commit", "author1", "2026-01-15 10:00:00",
         "HEAD -> main"));
      entries.add(new GitLogEntry(
         "bbbbbbb1111111222222233333334444444bbbbbbb",
         Arrays.asList(
            "ccccccc1111111222222233333334444444ccccccc",
            "ddddddd1111111222222233333334444444ddddddd"),
         "Merge branch feature", "author2", "2026-01-14 09:00:00",
         null));
      entries.add(new GitLogEntry(
         "ccccccc1111111222222233333334444444ccccccc",
         Collections.singletonList(
            "eeeeeee1111111222222233333334444444eeeeeee"),
         "Add feature X", "author1", "2026-01-13 08:00:00",
         "feature/x"));
      entries.add(new GitLogEntry(
         "ddddddd1111111222222233333334444444ddddddd",
         Collections.singletonList(
            "eeeeeee1111111222222233333334444444eeeeeee"),
         "Fix bug Y", "author3", "2026-01-12 07:00:00",
         null));
      entries.add(new GitLogEntry(
         "eeeeeee1111111222222233333334444444eeeeeee",
         Collections.emptyList(),
         "Root commit", "author1", "2026-01-11 06:00:00",
         null));

      graphRows = GitLogGraph.assignLanes(entries);

      // Create panel via reflection (private constructor)
      Constructor<GitLogPanel> ctor = GitLogPanel.class.getDeclaredConstructor(
         List.class);
      ctor.setAccessible(true);
      panel = ctor.newInstance(graphRows);

      // Show in a Frame
      java.awt.EventQueue.invokeAndWait(() -> {
         testFrame = new Frame("GitLogPanel Test");
         ScrollPane scroll = new ScrollPane(
            ScrollPane.SCROLLBARS_AS_NEEDED);
         scroll.add(panel);
         testFrame.add(scroll);
         testFrame.setSize(800, 400);
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

   @Test
   void t01_panelIsShowing() {
      assertTrue(panel.isShowing(),
         "GitLogPanel should be visible");
   }

   @Test
   void t02_panelHasNonZeroSize() {
      Dimension size = panel.getPreferredSize();
      assertTrue(size.width > 0,
         "Panel preferred width should be > 0, got " + size.width);
      assertTrue(size.height > 0,
         "Panel preferred height should be > 0, got " + size.height);
   }

   @Test
   void t03_preferredSizeReflectsRowCount() {
      Dimension size = panel.getPreferredSize();
      // 5 entries * ROW_HEIGHT(24) + ROW_HEIGHT(24) = 144
      assertTrue(size.height >= 5 * 24,
         "Height should accommodate 5 rows, got " + size.height);
   }

   @Test
   void t04_panelBackgroundIsDark() {
      Color bg = panel.getBackground();
      assertNotNull(bg, "Background should not be null");
      // BG_COLOR is 0x1e1e1e (dark)
      assertTrue(bg.getRed() < 64 && bg.getGreen() < 64
         && bg.getBlue() < 64,
         "Background should be dark, got " + bg);
   }

   @Test
   void t05_paintDoesNotThrow() {
      // Get a real Graphics context and call paint
      Graphics g = panel.getGraphics();
      assertNotNull(g, "Canvas should provide Graphics");
      try {
         panel.paint(g);
         // If we get here, paint() completed without throwing
         assertTrue(true);
      } finally {
         g.dispose();
      }
   }

   @Test
   void t06_paintWithMultipleCallsStable() {
      Graphics g = panel.getGraphics();
      assertNotNull(g, "Canvas should provide Graphics");
      try {
         // Multiple paint() calls should not throw or corrupt state
         panel.paint(g);
         panel.paint(g);
         panel.paint(g);
         assertTrue(panel.isShowing(),
            "Panel should remain showing after repeated paints");
      } finally {
         g.dispose();
      }
   }

   @Test
   void t07_frameContainsPanel() {
      window.requireVisible();
      Canvas found = robot.finder().findByType(
         testFrame, Canvas.class);
      assertNotNull(found,
         "Frame should contain a Canvas (GitLogPanel)");
      assertEquals(panel, found,
         "Canvas found should be our GitLogPanel");
   }

   @Test
   void t08_graphRowsHaveCorrectLanes() {
      // The first commit (HEAD) should get lane 0
      assertEquals(0, graphRows.get(0).lane,
         "HEAD commit should be lane 0");
      // Merge commit inherits lane 0 from first parent
      assertEquals(0, graphRows.get(1).lane,
         "Merge commit should inherit lane 0");
   }

   @Test
   void t09_graphRowsHaveValidParentLanes() {
      for (GitLogGraph.Row row : graphRows) {
         for (int pl : row.parentLanes) {
            assertTrue(pl >= 0,
               "Parent lane should be >= 0, got " + pl);
         }
      }
   }

   @Test
   void t10_mergeCommitHasMultipleParentLanes() {
      // Entry at index 1 is the merge commit with 2 parents
      GitLogGraph.Row mergeRow = graphRows.get(1);
      assertEquals(2, mergeRow.parentLanes.length,
         "Merge commit should have 2 parent lanes");
   }

   @Test
   void t11_rootCommitHasNoParentLanes() {
      // Entry at index 4 is the root commit
      GitLogGraph.Row rootRow = graphRows.get(4);
      assertEquals(0, rootRow.parentLanes.length,
         "Root commit should have 0 parent lanes");
   }

   @Test
   void t12_repaintDoesNotThrow() {
      panel.repaint();
      robot.waitForIdle();
      assertTrue(panel.isShowing(),
         "Panel should remain visible after repaint");
   }

   @Test
   void t13_panelIsDisplayable() {
      assertTrue(panel.isDisplayable(),
         "Panel should be displayable when added to Frame");
   }

   @Test
   void t14_shortShaRenderedCorrectly() {
      // Verify entries have correct short SHA for rendering
      assertEquals("aaaaaaa",
         graphRows.get(0).entry.shortSha(),
         "First entry short SHA should be first 7 chars");
   }

   @Test
   void t15_decorationsPresent() {
      // First entry should have decoration
      assertNotNull(graphRows.get(0).entry.decoration,
         "HEAD commit should have decoration");
      assertEquals("HEAD -> main",
         graphRows.get(0).entry.decoration);
      // Last entry should have no decoration
      GitLogEntry root = graphRows.get(4).entry;
      assertTrue(root.decoration == null,
         "Root commit should have no decoration");
   }
}
