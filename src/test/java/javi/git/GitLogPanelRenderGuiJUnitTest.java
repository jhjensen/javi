package javi.git;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.ScrollPane;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
 * Extended GUI tests for {@link GitLogPanel} rendering and resizing.
 *
 * <p>Creates panels with various graph topologies (linear, octopus merge,
 * single commit, many branches) and verifies paint stability, preferred
 * size computation, and resize behavior.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class GitLogPanelRenderGuiJUnitTest {

   private static Robot robot;
   private static Frame testFrame;
   private static GitLogPanel panelLinear;
   private static GitLogPanel panelOctopus;
   private static GitLogPanel panelSingle;
   private static FrameFixture window;
   private static List<GitLogGraph.Row> linearRows;
   private static List<GitLogGraph.Row> octopusRows;

   @BeforeAll
   static void setUp() throws Exception {
      // Linear history: A → B → C (no branches)
      List<GitLogEntry> linearEntries = new ArrayList<>();
      linearEntries.add(new GitLogEntry(
         "aaa0000000000000000000000000000000000000a",
         Collections.singletonList(
            "bbb0000000000000000000000000000000000000b"),
         "Third commit", "dev1", "2026-03-01 12:00:00",
         "HEAD -> main"));
      linearEntries.add(new GitLogEntry(
         "bbb0000000000000000000000000000000000000b",
         Collections.singletonList(
            "ccc0000000000000000000000000000000000000c"),
         "Second commit", "dev1", "2026-02-28 12:00:00",
         null));
      linearEntries.add(new GitLogEntry(
         "ccc0000000000000000000000000000000000000c",
         Collections.emptyList(),
         "Initial commit", "dev1", "2026-02-27 12:00:00",
         null));

      // Octopus merge: M merges A, B, C
      List<GitLogEntry> octopusEntries = new ArrayList<>();
      octopusEntries.add(new GitLogEntry(
         "mmm0000000000000000000000000000000000000m",
         Arrays.asList(
            "aaa0000000000000000000000000000000000001a",
            "bbb0000000000000000000000000000000000001b",
            "ccc0000000000000000000000000000000000001c"),
         "Octopus merge", "merger", "2026-03-02 10:00:00",
         "HEAD -> main"));
      octopusEntries.add(new GitLogEntry(
         "aaa0000000000000000000000000000000000001a",
         Collections.singletonList(
            "rrr0000000000000000000000000000000000000r"),
         "Branch A work", "dev1", "2026-03-01 09:00:00",
         "feature/a"));
      octopusEntries.add(new GitLogEntry(
         "bbb0000000000000000000000000000000000001b",
         Collections.singletonList(
            "rrr0000000000000000000000000000000000000r"),
         "Branch B work", "dev2", "2026-03-01 08:00:00",
         "feature/b"));
      octopusEntries.add(new GitLogEntry(
         "ccc0000000000000000000000000000000000001c",
         Collections.singletonList(
            "rrr0000000000000000000000000000000000000r"),
         "Branch C work", "dev3", "2026-03-01 07:00:00",
         "feature/c"));
      octopusEntries.add(new GitLogEntry(
         "rrr0000000000000000000000000000000000000r",
         Collections.emptyList(),
         "Root", "dev1", "2026-02-28 06:00:00",
         null));

      // Single commit (edge case)
      List<GitLogEntry> singleEntry = new ArrayList<>();
      singleEntry.add(new GitLogEntry(
         "sss0000000000000000000000000000000000000s",
         Collections.emptyList(),
         "Only commit", "solo", "2026-01-01 00:00:00",
         "HEAD -> main"));

      linearRows = GitLogGraph.assignLanes(linearEntries);
      octopusRows = GitLogGraph.assignLanes(octopusEntries);
      List<GitLogGraph.Row> singleRows =
         GitLogGraph.assignLanes(singleEntry);

      Constructor<GitLogPanel> ctor = GitLogPanel.class
         .getDeclaredConstructor(List.class);
      ctor.setAccessible(true);
      panelLinear = ctor.newInstance(linearRows);
      panelOctopus = ctor.newInstance(octopusRows);
      panelSingle = ctor.newInstance(singleRows);

      java.awt.EventQueue.invokeAndWait(() -> {
         testFrame = new Frame("GitLogPanel Render Test");
         // Use linear panel as main display
         ScrollPane scroll = new ScrollPane(
            ScrollPane.SCROLLBARS_AS_NEEDED);
         scroll.add(panelLinear);
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
      if (testFrame != null)
         java.awt.EventQueue.invokeLater(() -> testFrame.dispose());
   }

   // ── Linear graph tests ───────────────────────────────────────

   @Test
   void t01_linearPanelShowing() {
      assertTrue(panelLinear.isShowing(),
         "Linear panel should be visible");
   }

   @Test
   void t02_linearPreferredSizeForThreeRows() {
      Dimension size = panelLinear.getPreferredSize();
      // 3 rows * 24 (ROW_HEIGHT) + 24 = 96
      assertTrue(size.height >= 72,
         "Height should accommodate 3 rows, got " + size.height);
   }

   @Test
   void t03_linearRowsAllLaneZero() {
      // Linear history: all commits on lane 0
      for (GitLogGraph.Row row : linearRows) {
         assertEquals(0, row.lane,
            "Linear history commits should all be lane 0");
      }
   }

   @Test
   void t04_linearPaintStable() {
      Graphics g = panelLinear.getGraphics();
      assertNotNull(g);
      try {
         panelLinear.paint(g);
         panelLinear.paint(g);
         assertTrue(panelLinear.isShowing());
      } finally {
         g.dispose();
      }
   }

   // ── Octopus merge tests ──────────────────────────────────────

   @Test
   void t05_octopusMergeHasThreeParents() {
      GitLogGraph.Row mergeRow = octopusRows.get(0);
      assertEquals(3, mergeRow.parentLanes.length,
         "Octopus merge should have 3 parent lanes");
   }

   @Test
   void t06_octopusPreferredSizeWider() {
      Dimension octSize = panelOctopus.getPreferredSize();
      Dimension linSize = panelLinear.getPreferredSize();
      // Octopus has more lanes, so preferred width should be >= linear
      assertTrue(octSize.width >= linSize.width,
         "Octopus panel should be at least as wide as linear");
   }

   @Test
   void t07_octopusPaintDoesNotThrow() {
      // Octopus merge requires complex branch line drawing
      // Create a temporary Frame for painting
      Frame tmpFrame = new Frame("octopus-test");
      try {
         ScrollPane sp = new ScrollPane();
         sp.add(panelOctopus);
         tmpFrame.add(sp);
         tmpFrame.setSize(800, 400);
         tmpFrame.setVisible(true);
         robot.waitForIdle();

         Graphics g = panelOctopus.getGraphics();
         if (g != null) {
            panelOctopus.paint(g);
            g.dispose();
         }
         assertTrue(true, "Octopus paint should not throw");
      } finally {
         tmpFrame.dispose();
      }
   }

   @Test
   void t08_octopusBranchDecorations() {
      // Entries 1-3 should have feature/a, feature/b, feature/c
      assertEquals("feature/a",
         octopusRows.get(1).entry.decoration);
      assertEquals("feature/b",
         octopusRows.get(2).entry.decoration);
      assertEquals("feature/c",
         octopusRows.get(3).entry.decoration);
   }

   // ── Single commit tests ──────────────────────────────────────

   @Test
   void t09_singleCommitPreferredSize() {
      Dimension size = panelSingle.getPreferredSize();
      // 1 row * 24 + 24 = 48
      assertTrue(size.height >= 24,
         "Single commit panel height should be >= 24, got "
            + size.height);
      assertTrue(size.width > 0);
   }

   @Test
   void t10_singleCommitPaintDoesNotThrow() {
      Frame tmpFrame = new Frame("single-test");
      try {
         ScrollPane sp = new ScrollPane();
         sp.add(panelSingle);
         tmpFrame.add(sp);
         tmpFrame.setSize(400, 200);
         tmpFrame.setVisible(true);
         robot.waitForIdle();

         Graphics g = panelSingle.getGraphics();
         if (g != null) {
            panelSingle.paint(g);
            g.dispose();
         }
         assertTrue(true);
      } finally {
         tmpFrame.dispose();
      }
   }

   // ── Rendering detail tests ───────────────────────────────────

   @Test
   void t11_panelHasDarkBackground() {
      Color bg = panelLinear.getBackground();
      assertNotNull(bg);
      assertTrue(bg.getRed() < 64 && bg.getGreen() < 64
         && bg.getBlue() < 64,
         "Background should be dark");
   }

   @Test
   void t12_shortShaIsSeven() {
      for (GitLogGraph.Row row : linearRows) {
         assertEquals(7, row.entry.shortSha().length(),
            "Short SHA should be 7 chars for "
               + row.entry.sha);
      }
   }

   @Test
   void t13_entryDatesPresent() {
      for (GitLogGraph.Row row : linearRows) {
         assertNotNull(row.entry.date,
            "Date should not be null");
         assertTrue(row.entry.date.length() >= 10,
            "Date should have at least 10 chars");
      }
   }

   @Test
   void t14_rootEntryHasNoParents() {
      GitLogGraph.Row rootRow =
         linearRows.get(linearRows.size() - 1);
      assertEquals(0, rootRow.parentLanes.length,
         "Root entry should have no parents");
   }

   @Test
   void t15_headEntryHasDecoration() {
      assertNotNull(linearRows.get(0).entry.decoration,
         "HEAD commit should have decoration");
      assertTrue(
         linearRows.get(0).entry.decoration.contains("main"),
         "HEAD decoration should contain 'main'");
   }

   // ── maxLanes computation ─────────────────────────────────────

   @Test
   void t16_linearMaxLanesIsOne() throws Exception {
      Field mlField = GitLogPanel.class.getDeclaredField("maxLanes");
      mlField.setAccessible(true);
      int maxLanes = mlField.getInt(panelLinear);
      assertEquals(1, maxLanes,
         "Linear history should have maxLanes = 1");
   }

   @Test
   void t17_octopusMaxLanesGreaterThanOne() throws Exception {
      Field mlField = GitLogPanel.class.getDeclaredField("maxLanes");
      mlField.setAccessible(true);
      int maxLanes = mlField.getInt(panelOctopus);
      assertTrue(maxLanes > 1,
         "Octopus merge should have maxLanes > 1, got " + maxLanes);
   }

   @Test
   void t18_panelIsDisplayable() {
      assertTrue(panelLinear.isDisplayable(),
         "Panel should be displayable");
   }

   @Test
   void t19_repaintStable() {
      panelLinear.repaint();
      robot.waitForIdle();
      assertTrue(panelLinear.isShowing(),
         "Panel should remain showing after repaint");
   }

   @Test
   void t20_frameContainsCanvas() {
      window.requireVisible();
      Canvas found = robot.finder().findByType(
         testFrame, Canvas.class);
      assertNotNull(found, "Frame should contain a Canvas");
   }
}
