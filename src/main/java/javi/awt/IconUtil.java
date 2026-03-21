package javi.awt;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for creating application icons for the Javi editor.
 *
 * <p>Generates a programmatic icon with a light brown background,
 * a stylized glass coffee cup with an orange stem/saucer, and a
 * small orange "vi" label beneath the cup.</p>
 *
 * <h2>Icon Design</h2>
 * <ul>
 *   <li><b>Background</b>: Light brown/tan (#D2A66A)</li>
 *   <li><b>Cup body</b>: Cream/white glass shape with a handle</li>
 *   <li><b>Stem/saucer</b>: Orange (#D47B2A)</li>
 *   <li><b>Steam</b>: Translucent white wisps above the cup</li>
 *   <li><b>Text</b>: Orange "vi" beneath the cup</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Image icon = IconUtil.createJaviIcon(128);
 * List<Image> icons = IconUtil.createJaviIcons();
 * }</pre>
 *
 * @see java.awt.Frame#setIconImage(Image)
 * @see java.awt.Frame#setIconImages(List)
 */
public final class IconUtil {

   /** Light brown/tan background. */
   static final Color BACKGROUND = new Color(210, 166, 106);

   /** Cup body fill — off-white / cream glass. */
   static final Color CUP_FILL = new Color(245, 238, 225);

   /** Cup outline / edge highlight. */
   private static final Color CUP_EDGE = new Color(180, 140, 90);

   /** Orange colour for stem, saucer and "vi" text. */
   static final Color ORANGE = new Color(212, 123, 42);

   /** Translucent white steam. */
   private static final Color STEAM =
      new Color(255, 255, 255, 120);

   /** Darker orange for cup rim accent. */
   private static final Color COFFEE_RIM =
      new Color(160, 90, 30, 100);

   private IconUtil() {
   }

   /**
    * Creates a Javi icon at the specified square size.
    *
    * @param size width and height in pixels
    * @return a new BufferedImage containing the icon
    */
   public static BufferedImage createJaviIcon(int size) {
      BufferedImage img = new BufferedImage(
         size, size, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      try {
         enableAntialiasing(g);
         drawBackground(g, size);
         drawSteam(g, size);
         drawCupBody(g, size);
         drawHandle(g, size);
         drawCoffeeRim(g, size);
         drawStemAndSaucer(g, size);
         drawViLabel(g, size);
      } finally {
         g.dispose();
      }
      return img;
   }

   /**
    * Creates a default 128x128 Javi icon.
    *
    * @return a new 128x128 icon image
    */
   public static BufferedImage createJaviIcon() {
      return createJaviIcon(128);
   }

   /**
    * Creates icons at standard sizes including 128 and 512.
    *
    * @return list of icon images at 16, 32, 48, 64, 128, 512
    */
   public static List<Image> createJaviIcons() {
      List<Image> icons = new ArrayList<>();
      for (int sz : new int[]{16, 32, 48, 64, 128, 512})
         icons.add(createJaviIcon(sz));
      return icons;
   }

   // ---- drawing helpers (package-private for testing) ----

   private static void enableAntialiasing(Graphics2D g) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
         RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
         RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
   }

   /** Fills the square with the tan background and rounded border. */
   private static void drawBackground(Graphics2D g, int size) {
      float r = size * 0.12f;
      g.setColor(BACKGROUND);
      g.fill(new RoundRectangle2D.Float(0, 0, size, size, r, r));
      // subtle darker border
      g.setColor(CUP_EDGE);
      float bw = Math.max(1f, size / 64f);
      g.setStroke(new BasicStroke(bw));
      g.draw(new RoundRectangle2D.Float(
         bw / 2, bw / 2, size - bw, size - bw, r, r));
   }

   /** Draws three curved steam wisps above the cup. */
   private static void drawSteam(Graphics2D g, int size) {
      g.setColor(STEAM);
      float sw = Math.max(1f, size / 40f);
      g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND,
         BasicStroke.JOIN_ROUND));
      float cx = size * 0.44f;
      float top = size * 0.08f;
      float bot = size * 0.22f;
      float dx = size * 0.09f;
      for (int i = -1; i <= 1; i++) {
         float x = cx + i * dx;
         drawSteamWisp(g, x, top, bot, size * 0.04f);
      }
   }

   /** Draws one S-shaped steam wisp from bottom to top. */
   private static void drawSteamWisp(
         Graphics2D g, float x, float top, float bot, float amp) {
      GeneralPath p = new GeneralPath();
      p.moveTo(x, bot);
      float mid = (top + bot) / 2f;
      p.curveTo(x - amp, bot - (bot - mid) * 0.4f,
                x + amp, mid + (bot - mid) * 0.2f,
                x, mid);
      p.curveTo(x - amp, mid - (mid - top) * 0.3f,
                x + amp, top + (mid - top) * 0.5f,
                x, top);
      g.draw(p);
   }

   /** Draws the cup body as a tapered rounded rectangle. */
   private static void drawCupBody(Graphics2D g, int size) {
      // Cup occupies roughly the center of the icon
      float cupL = size * 0.20f;
      float cupR = size * 0.68f;
      float cupT = size * 0.22f;
      float cupB = size * 0.62f;
      float cupW = cupR - cupL;
      float cupH = cupB - cupT;

      // Slight taper: narrower at bottom via GeneralPath
      float taper = cupW * 0.06f;
      float rad = cupW * 0.15f;
      GeneralPath cup = new GeneralPath();
      cup.moveTo(cupL, cupT + rad);
      // top-left arc
      cup.quadTo(cupL, cupT, cupL + rad, cupT);
      // top edge
      cup.lineTo(cupR - rad, cupT);
      // top-right arc
      cup.quadTo(cupR, cupT, cupR, cupT + rad);
      // right side (tapers in toward bottom)
      cup.lineTo(cupR - taper, cupB - rad);
      // bottom-right arc
      cup.quadTo(cupR - taper, cupB,
                 cupR - taper - rad, cupB);
      // bottom edge
      cup.lineTo(cupL + taper + rad, cupB);
      // bottom-left arc
      cup.quadTo(cupL + taper, cupB,
                 cupL + taper, cupB - rad);
      // left side
      cup.lineTo(cupL, cupT + rad);
      cup.closePath();

      g.setColor(CUP_FILL);
      g.fill(cup);
      g.setColor(CUP_EDGE);
      float ew = Math.max(1f, size / 60f);
      g.setStroke(new BasicStroke(ew));
      g.draw(cup);
   }

   /** Draws a C-shaped handle on the right side of the cup. */
   private static void drawHandle(Graphics2D g, int size) {
      float hx = size * 0.68f;
      float hy = size * 0.30f;
      float hw = size * 0.14f;
      float hh = size * 0.22f;
      float sw = Math.max(1.5f, size / 40f);
      g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND,
         BasicStroke.JOIN_ROUND));
      g.setColor(CUP_FILL);
      g.draw(new Arc2D.Float(hx, hy, hw, hh, -90, 180,
         Arc2D.OPEN));
      g.setColor(CUP_EDGE);
      g.setStroke(new BasicStroke(
         Math.max(1f, size / 60f)));
      g.draw(new Arc2D.Float(hx, hy, hw, hh, -90, 180,
         Arc2D.OPEN));
   }

   /** Fills a thin ellipse at the cup rim to suggest coffee. */
   private static void drawCoffeeRim(Graphics2D g, int size) {
      g.setColor(COFFEE_RIM);
      float rimL = size * 0.22f;
      float rimW = size * 0.44f;
      float rimY = size * 0.23f;
      float rimH = size * 0.06f;
      g.fill(new Ellipse2D.Float(rimL, rimY, rimW, rimH));
   }

   /** Draws the narrow stem and small saucer below the cup. */
   private static void drawStemAndSaucer(Graphics2D g, int size) {
      g.setColor(ORANGE);
      // stem: narrow rectangle
      float stemW = size * 0.06f;
      float stemH = size * 0.07f;
      float stemX = size * 0.44f - stemW / 2;
      float stemY = size * 0.62f;
      g.fill(new RoundRectangle2D.Float(
         stemX, stemY, stemW, stemH, stemW * 0.3f, stemW * 0.3f));

      // saucer: flattened ellipse
      float saucerW = size * 0.38f;
      float saucerH = size * 0.06f;
      float saucerX = size * 0.44f - saucerW / 2;
      float saucerY = stemY + stemH - saucerH * 0.3f;
      g.fill(new Ellipse2D.Float(
         saucerX, saucerY, saucerW, saucerH));
   }

   /** Draws small orange "vi" text beneath the saucer. */
   private static void drawViLabel(Graphics2D g, int size) {
      g.setColor(ORANGE);
      int fontSize = Math.max(6, (int) (size * 0.18));
      g.setFont(new Font(Font.SERIF, Font.BOLD, fontSize));
      java.awt.FontMetrics fm = g.getFontMetrics();
      String text = "vi";
      int tw = fm.stringWidth(text);
      int x = (int) (size * 0.44f) - tw / 2;
      int y = (int) (size * 0.78f) + fm.getAscent() / 2;
      g.drawString(text, x, y);
   }
}
