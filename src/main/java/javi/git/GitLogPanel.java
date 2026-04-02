package javi.git;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.ScrollPane;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;

/**
 * Separate AWT window displaying a git commit graph.
 *
 * <p>Renders a SourceTree-inspired visual history with colored
 * branch lanes, commit dots, and text labels. Displayed in its
 * own Frame rather than in an editor buffer.</p>
 */
public final class GitLogPanel extends Canvas {

   private static final long serialVersionUID = 1L;

   /** Colors for branch lanes (cycle through these). */
   private static final Color[] LANE_COLORS = {
      new Color(0x4078c0),  // blue
      new Color(0x6cc644),  // green
      new Color(0xbd2c00),  // red
      new Color(0xc9510c),  // orange
      new Color(0x6e5494),  // purple
      new Color(0x0e8a16),  // dark green
      new Color(0xd15f27),  // dark orange
      new Color(0x1d76db),  // light blue
   };

   private static final int ROW_HEIGHT = 24;
   private static final int LANE_WIDTH = 16;
   private static final int DOT_RADIUS = 4;
   private static final int LEFT_MARGIN = 8;
   private static final int TEXT_GAP = 12;
   private static final int SHA_WIDTH = 65;

   private static final Color BG_COLOR = new Color(0x1e1e1e);
   private static final Color TEXT_COLOR = new Color(0xd4d4d4);
   private static final Color SHA_COLOR = new Color(0xdcdcaa);
   private static final Color DECO_COLOR = new Color(0x4ec9b0);
   private static final Color DATE_COLOR = new Color(0x808080);

   /** The computed graph rows. */
   private final List<GitLogGraph.Row> rows;
   /** Maximum lane count across all rows. */
   private final int maxLanes;

   private GitLogPanel(List<GitLogGraph.Row> rows) {
      this.rows = rows;
      int max = 0;
      for (GitLogGraph.Row r : rows) {
         if (r.lane >= max) {
            max = r.lane + 1;
         }
         for (int pl : r.parentLanes) {
            if (pl >= max) {
               max = pl + 1;
            }
         }
      }
      this.maxLanes = max;
      setBackground(BG_COLOR);
   }

   @Override
   public Dimension getPreferredSize() {
      int width = LEFT_MARGIN + maxLanes * LANE_WIDTH + TEXT_GAP
         + SHA_WIDTH + 600;
      int height = rows.size() * ROW_HEIGHT + ROW_HEIGHT;
      return new Dimension(width, height);
   }

   @Override
   public void paint(Graphics g) {
      Graphics2D g2 = (Graphics2D) g;
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
         RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
         RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

      Font baseFont = new Font("Menlo", Font.PLAIN, 12);
      Font boldFont = baseFont.deriveFont(Font.BOLD);
      g2.setFont(baseFont);
      FontMetrics fm = g2.getFontMetrics();

      int textX = LEFT_MARGIN + maxLanes * LANE_WIDTH + TEXT_GAP;

      for (int i = 0; i < rows.size(); i++) {
         GitLogGraph.Row row = rows.get(i);
         int y = (i + 1) * ROW_HEIGHT;
         int dotY = y - ROW_HEIGHT / 4;

         // Draw lines to parents
         for (int pi = 0; pi < row.parentLanes.length; pi++) {
            int parentLane = row.parentLanes[pi];
            Color lineColor = laneColor(
               pi == 0 ? row.lane : parentLane);
            g2.setColor(lineColor);

            int x1 = LEFT_MARGIN + row.lane * LANE_WIDTH
               + LANE_WIDTH / 2;
            int x2 = LEFT_MARGIN + parentLane * LANE_WIDTH
               + LANE_WIDTH / 2;

            // Find next row using this parent lane
            int nextY = findParentRow(i, row.entry.parents.get(pi));
            int y2 = nextY * ROW_HEIGHT - ROW_HEIGHT / 4;

            if (x1 == x2) {
               // Straight line down
               g2.drawLine(x1, dotY, x2, y2);
            } else {
               // Curved merge/branch line
               int midY = dotY + ROW_HEIGHT / 2;
               g2.drawLine(x1, dotY, x1, midY);
               g2.drawLine(x1, midY, x2, midY + ROW_HEIGHT / 2);
               g2.drawLine(x2, midY + ROW_HEIGHT / 2, x2, y2);
            }
         }

         // Draw commit dot
         Color dotColor = laneColor(row.lane);
         g2.setColor(dotColor);
         int dotX = LEFT_MARGIN + row.lane * LANE_WIDTH
            + LANE_WIDTH / 2;
         g2.fillOval(dotX - DOT_RADIUS, dotY - DOT_RADIUS,
            DOT_RADIUS * 2, DOT_RADIUS * 2);

         // Draw SHA
         g2.setColor(SHA_COLOR);
         g2.setFont(baseFont);
         int tx = textX;
         g2.drawString(row.entry.shortSha(), tx, y - 4);
         tx += SHA_WIDTH;

         // Draw decoration if present
         if (null != row.entry.decoration) {
            g2.setColor(DECO_COLOR);
            g2.setFont(boldFont);
            String deco = " [" + row.entry.decoration + "] ";
            g2.drawString(deco, tx, y - 4);
            tx += fm.stringWidth(deco);
         }

         // Draw subject
         g2.setColor(TEXT_COLOR);
         g2.setFont(baseFont);
         g2.drawString(row.entry.subject, tx, y - 4);

         // Draw date on far right
         if (null != row.entry.date && row.entry.date.length() >= 10) {
            g2.setColor(DATE_COLOR);
            String shortDate = row.entry.date.substring(0, 10);
            int dateX = getPreferredSize().width - 100;
            g2.drawString(shortDate, dateX, y - 4);
         }
      }
   }

   /**
    * Find the row index where a parent commit appears.
    * Returns the row index + 1 (for line endpoint calculation).
    */
   private int findParentRow(int startRow, String parentSha) {
      for (int i = startRow + 1; i < rows.size(); i++) {
         if (rows.get(i).entry.sha.equals(parentSha)) {
            return i + 1;
         }
      }
      // Parent not in visible range — draw to bottom
      return rows.size() + 1;
   }

   private static Color laneColor(int lane) {
      return LANE_COLORS[lane % LANE_COLORS.length];
   }

   /**
    * Open a git log graph window.
    * Fetches log data, computes layout, and shows the frame.
    *
    * @param count number of commits to show
    * @throws IOException if git command fails
    */
   public static void showLogWindow(int count) throws IOException {
      List<String> rawLines = GitProcess.execute(
         "log", "--all", "--topo-order",
         "--format=format:%H|%P|%d|%s|%an|%ai",
         "-" + count);
      List<GitLogEntry> entries = GitLogEntry.parse(rawLines);
      if (entries.isEmpty()) {
         return;
      }
      List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(entries);

      java.awt.EventQueue.invokeLater(() -> {
         GitLogPanel panel = new GitLogPanel(rows);

         Frame frame = new Frame("Git Log — "
            + entries.size() + " commits");
         frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
               frame.dispose();
            }
         });

         ScrollPane scroll = new ScrollPane(
            ScrollPane.SCROLLBARS_AS_NEEDED);
         scroll.add(panel);

         frame.add(scroll);
         frame.setSize(900, 600);
         frame.setLocationRelativeTo(null);
         frame.setVisible(true);
      });
   }
}
