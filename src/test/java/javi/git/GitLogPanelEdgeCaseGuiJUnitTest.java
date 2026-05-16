package javi.git;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.ScrollPane;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
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
 * Edge-case GUI tests for {@link GitLogPanel} rendering.
 *
 * <p>Exercises rendering paths with unusual graph topologies:
 * deeply nested branches (many lanes), very long commit messages,
 * special characters in subjects and decorations, identical SHAs
 * (degenerate input), large graphs (100+ commits), and panels
 * painted to offscreen images at various sizes.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class GitLogPanelEdgeCaseGuiJUnitTest {

   private static final int ROW_HEIGHT = 24;

   private static Robot robot;
   private static Frame testFrame;
   private static FrameFixture window;

   // Panels for various topologies
   private static GitLogPanel deepPanel;
   private static GitLogPanel longMsgPanel;
   private static GitLogPanel specialCharPanel;
   private static GitLogPanel largePanel;
   private static List<GitLogGraph.Row> deepRows;
   private static List<GitLogGraph.Row> longMsgRows;
   private static List<GitLogGraph.Row> specialCharRows;
   private static List<GitLogGraph.Row> largeRows;

   private static GitLogPanel createPanel(List<GitLogGraph.Row> rows)
         throws Exception {
      Constructor<GitLogPanel> ctor = GitLogPanel.class
         .getDeclaredConstructor(List.class);
      ctor.setAccessible(true);
      return ctor.newInstance(rows);
   }

   private static String sha(int i) {
      return String.format("%040d", i);
   }

   @BeforeAll
   static void setUp() throws Exception {
      // Deep branching topology: 8 concurrent branches merging back
      List<GitLogEntry> deepEntries = new ArrayList<>();
      String mergeSha = sha(100);
      List<String> mergeParents = new ArrayList<>();
      for (int b = 0; b < 8; b++) {
         mergeParents.add(sha(200 + b));
      }
      deepEntries.add(new GitLogEntry(mergeSha, mergeParents,
         "Octopus merge of 8 branches", "dev", "2026-05-01 10:00:00",
         "HEAD -> main"));
      for (int b = 0; b < 8; b++) {
         deepEntries.add(new GitLogEntry(sha(200 + b),
            Collections.singletonList(sha(300)),
            "Branch " + b + " work", "dev" + b,
            "2026-04-" + String.format("%02d", 30 - b) + " 09:00:00",
            "feature/" + b));
      }
      deepEntries.add(new GitLogEntry(sha(300),
         Collections.emptyList(),
         "Root", "root", "2026-04-01 08:00:00", null));
      deepRows = GitLogGraph.assignLanes(deepEntries);
      deepPanel = createPanel(deepRows);

      // Long commit message topology
      List<GitLogEntry> longMsgEntries = new ArrayList<>();
      String longMsg = "A".repeat(500) + " very long commit message "
         + "B".repeat(500);
      longMsgEntries.add(new GitLogEntry(sha(400),
         Collections.singletonList(sha(401)),
         longMsg, "author", "2026-05-01 10:00:00",
         "HEAD -> main, origin/main, tag:v1.0.0"));
      longMsgEntries.add(new GitLogEntry(sha(401),
         Collections.emptyList(),
         "Root", "author", "2026-04-30 10:00:00", null));
      longMsgRows = GitLogGraph.assignLanes(longMsgEntries);
      longMsgPanel = createPanel(longMsgRows);

      // Special characters in subjects and decorations
      List<GitLogEntry> specialEntries = new ArrayList<>();
      specialEntries.add(new GitLogEntry(sha(500),
         Collections.singletonList(sha(501)),
         "fix: handle <html> & \"quotes\" in output",
         "dev\u00e9", "2026-05-01 10:00:00",
         "HEAD -> feature/fix-<html>"));
      specialEntries.add(new GitLogEntry(sha(501),
         Collections.singletonList(sha(502)),
         "chore: update deps (\u2192 v2.0)",
         "author", "2026-04-30 10:00:00",
         "tag:v2.0-rc.1"));
      specialEntries.add(new GitLogEntry(sha(502),
         Collections.emptyList(),
         "\u2603 snowman commit \u2764",
         "author", "2026-04-29 10:00:00", null));
      specialCharRows = GitLogGraph.assignLanes(specialEntries);
      specialCharPanel = createPanel(specialCharRows);

      // Large graph: 100 linear commits
      List<GitLogEntry> largeEntries = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
         List<String> parents = i < 99
            ? Collections.singletonList(sha(600 + i + 1))
            : Collections.emptyList();
         String deco = i == 0 ? "HEAD -> main" : null;
         largeEntries.add(new GitLogEntry(sha(600 + i), parents,
            "Commit #" + i, "dev" + (i % 5),
            "2026-05-" + String.format("%02d", 16 - i / 5)
            + " " + String.format("%02d", i % 24) + ":00:00",
            deco));
      }
      largeRows = GitLogGraph.assignLanes(largeEntries);
      largePanel = createPanel(largeRows);

      // Show deep panel in a Frame for visibility tests
      java.awt.EventQueue.invokeAndWait(() -> {
         testFrame = new Frame("GitLogPanel Edge Case Test");
         ScrollPane scroll = new ScrollPane(
            ScrollPane.SCROLLBARS_AS_NEEDED);
         scroll.add(deepPanel);
         testFrame.add(scroll);
         testFrame.setSize(1000, 600);
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

   // ── Deep branching (8 lanes) ─────────────────────────────────

   @Test
   void t01_deepPanelIsShowing() {
      assertTrue(deepPanel.isShowing(),
         "Deep panel should be visible");
   }

   @Test
   void t02_deepPanelPreferredSizeAccommodatesLanes() {
      Dimension pref = deepPanel.getPreferredSize();
      // 8 branches + merge + root = 10 rows
      assertTrue(pref.height >= 10 * ROW_HEIGHT,
         "Height should accommodate 10 rows, got " + pref.height);
      // Width should include space for 8+ lanes
      assertTrue(pref.width > 600,
         "Width should be wide enough for 8 lanes, got " + pref.width);
   }

   @Test
   void t03_deepPanelPaintStable() {
      Dimension pref = deepPanel.getPreferredSize();
      BufferedImage img = new BufferedImage(
         pref.width, pref.height, BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      try {
         deepPanel.paint(g);
         assertTrue(true, "Deep panel paint should not throw");
      } finally {
         g.dispose();
      }
   }

   @Test
   void t04_deepGraphHasMultipleLanes() {
      int maxLane = 0;
      for (GitLogGraph.Row r : deepRows) {
         if (r.lane > maxLane)
            maxLane = r.lane;
      }
      assertTrue(maxLane >= 1,
         "Deep graph should use multiple lanes, max=" + maxLane);
   }

   @Test
   void t05_deepMergeHas8Parents() {
      GitLogGraph.Row merge = deepRows.get(0);
      assertEquals(8, merge.parentLanes.length,
         "Octopus merge should have 8 parent lanes");
   }

   // ── Long commit message ──────────────────────────────────────

   @Test
   void t06_longMsgPanelPaintStable() {
      Dimension pref = longMsgPanel.getPreferredSize();
      BufferedImage img = new BufferedImage(
         pref.width, pref.height, BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      try {
         longMsgPanel.paint(g);
         assertTrue(true, "Long message paint should not throw");
      } finally {
         g.dispose();
      }
   }

   @Test
   void t07_longMsgPreferredSizeReasonable() {
      Dimension pref = longMsgPanel.getPreferredSize();
      assertTrue(pref.width > 0,
         "Width should be positive for long message panel");
      // Height: 2 rows * 24 + 24 = 72
      assertTrue(pref.height >= 2 * ROW_HEIGHT,
         "Height for 2 rows should be >= " + (2 * ROW_HEIGHT));
   }

   @Test
   void t08_longDecorationRendered() {
      // First entry has multiple decorations
      GitLogEntry first = longMsgRows.get(0).entry;
      assertNotNull(first.decoration);
      assertTrue(first.decoration.contains("tag:v1.0.0"),
         "Decoration should contain tag");
      assertTrue(first.decoration.contains("origin/main"),
         "Decoration should contain origin/main");
   }

   // ── Special characters ───────────────────────────────────────

   @Test
   void t09_specialCharPaintStable() {
      Dimension pref = specialCharPanel.getPreferredSize();
      BufferedImage img = new BufferedImage(
         pref.width, pref.height, BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      try {
         specialCharPanel.paint(g);
         assertTrue(true,
            "Special char panel paint should not throw");
      } finally {
         g.dispose();
      }
   }

   @Test
   void t10_htmlCharsInSubject() {
      GitLogEntry entry = specialCharRows.get(0).entry;
      assertTrue(entry.subject.contains("<html>"),
         "Subject should contain angle brackets");
      assertTrue(entry.subject.contains("&"),
         "Subject should contain ampersand");
      assertTrue(entry.subject.contains("\""),
         "Subject should contain quotes");
   }

   @Test
   void t11_unicodeInSubject() {
      GitLogEntry entry = specialCharRows.get(2).entry;
      assertTrue(entry.subject.contains("\u2603"),
         "Subject should contain snowman");
      assertTrue(entry.subject.contains("\u2764"),
         "Subject should contain heart");
   }

   @Test
   void t12_unicodeArrowInSubject() {
      GitLogEntry entry = specialCharRows.get(1).entry;
      assertTrue(entry.subject.contains("\u2192"),
         "Subject should contain right arrow");
   }

   // ── Large graph (100 commits) ────────────────────────────────

   @Test
   void t13_largeGraphRowCount() {
      assertEquals(100, largeRows.size(),
         "Large graph should have 100 rows");
   }

   @Test
   void t14_largeGraphPreferredSize() {
      Dimension pref = largePanel.getPreferredSize();
      assertTrue(pref.height >= 100 * ROW_HEIGHT,
         "Height should accommodate 100 rows, got " + pref.height);
   }

   @Test
   void t15_largeGraphPaintStable() {
      Dimension pref = largePanel.getPreferredSize();
      BufferedImage img = new BufferedImage(
         pref.width, pref.height, BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      try {
         largePanel.paint(g);
         assertTrue(true, "Large graph paint should not throw");
      } finally {
         g.dispose();
      }
   }

   @Test
   void t16_largeGraphAllLinear() {
      for (GitLogGraph.Row row : largeRows) {
         assertEquals(0, row.lane,
            "All linear commits should be lane 0, "
            + "got lane " + row.lane + " for " + row.entry.subject);
      }
   }

   @Test
   void t17_largeGraphPaintWithSmallClip() {
      // Paint only first 5 rows (clip to 120px height)
      BufferedImage img = new BufferedImage(800, 120,
         BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      try {
         g.setClip(0, 0, 800, 120);
         largePanel.paint(g);
         assertTrue(true,
            "Large graph paint with small clip should not throw");
      } finally {
         g.dispose();
      }
   }

   // ── Offscreen rendering at various sizes ─────────────────────

   @Test
   void t18_paintAt1x1Pixel() {
      BufferedImage img = new BufferedImage(1, 1,
         BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      try {
         deepPanel.paint(g);
         assertTrue(true, "1x1 pixel paint should not throw");
      } finally {
         g.dispose();
      }
   }

   @Test
   void t19_paintWideButShort() {
      BufferedImage img = new BufferedImage(2000, 10,
         BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      try {
         deepPanel.paint(g);
         assertTrue(true, "Wide-but-short paint should not throw");
      } finally {
         g.dispose();
      }
   }

   @Test
   void t20_paintNarrowButTall() {
      BufferedImage img = new BufferedImage(50, 500,
         BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      try {
         deepPanel.paint(g);
         assertTrue(true, "Narrow-but-tall paint should not throw");
      } finally {
         g.dispose();
      }
   }

   // ── Panel field access via reflection ────────────────────────

   @Test
   void t21_maxLanesReflectsTopology() throws Exception {
      Field maxLanesField = GitLogPanel.class.getDeclaredField(
         "maxLanes");
      maxLanesField.setAccessible(true);
      int maxLanes = maxLanesField.getInt(deepPanel);
      assertTrue(maxLanes >= 2,
         "Deep panel maxLanes should be >= 2, got " + maxLanes);
   }

   @Test
   void t22_rowsFieldMatchesInput() throws Exception {
      Field rowsField = GitLogPanel.class.getDeclaredField("rows");
      rowsField.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<GitLogGraph.Row> rows =
         (List<GitLogGraph.Row>) rowsField.get(deepPanel);
      assertEquals(deepRows.size(), rows.size(),
         "Panel rows should match input rows");
   }

   // ── Background color consistency ─────────────────────────────

   @Test
   void t23_allPanelsHaveDarkBackground() {
      for (GitLogPanel p : new GitLogPanel[]{
            deepPanel, longMsgPanel, specialCharPanel, largePanel}) {
         Color bg = p.getBackground();
         assertNotNull(bg);
         assertTrue(bg.getRed() < 64 && bg.getGreen() < 64
            && bg.getBlue() < 64,
            "All panels should have dark background, got " + bg);
      }
   }

   // ── Repeated rapid paints ────────────────────────────────────

   @Test
   void t24_rapidPaintCyclesStable() {
      Dimension pref = deepPanel.getPreferredSize();
      BufferedImage img = new BufferedImage(
         pref.width, pref.height, BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      try {
         for (int i = 0; i < 50; i++) {
            deepPanel.paint(g);
         }
         assertTrue(true,
            "50 rapid paint cycles should not corrupt state");
      } finally {
         g.dispose();
      }
   }

   // ── Frame interactions ───────────────────────────────────────

   @Test
   void t25_frameResize() throws Exception {
      java.awt.EventQueue.invokeAndWait(() ->
         testFrame.setSize(500, 300));
      robot.waitForIdle();
      Thread.sleep(100);
      assertTrue(deepPanel.isShowing(),
         "Panel visible after resize to 500x300");
      // Restore
      java.awt.EventQueue.invokeAndWait(() ->
         testFrame.setSize(1000, 600));
      robot.waitForIdle();
   }

   @Test
   void t26_repaintAfterResize() {
      deepPanel.repaint();
      robot.waitForIdle();
      assertTrue(deepPanel.isShowing(),
         "Panel should remain showing after repaint");
   }
}
