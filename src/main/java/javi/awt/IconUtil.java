package javi.awt;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for creating application icons for the Javi editor.
 *
 * <p>Generates a programmatic icon with a warm reddish-orange
 * background, a translucent glass coffee cup with dark brown
 * coffee, an orange saucer, and a bold "Vi" label.</p>
 *
 * <h2>Icon Design</h2>
 * <ul>
 *   <li><b>Background</b>: Warm reddish-orange</li>
 *   <li><b>Cup body</b>: Translucent glass with handle</li>
 *   <li><b>Coffee</b>: Dark brown fill visible through glass</li>
 *   <li><b>Saucer</b>: Orange (#D47B2A)</li>
 *   <li><b>Steam</b>: Translucent white wisps</li>
 *   <li><b>Text</b>: Bold orange "Vi" beneath the saucer</li>
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

   /** Warm reddish-orange background. */
   static final Color BACKGROUND = new Color(205, 75, 35);

   /** Translucent glass cup fill. */
   static final Color CUP_FILL =
      new Color(200, 225, 245, 70);

   /** Cup outline / glass edge highlight. */
   private static final Color CUP_EDGE =
      new Color(160, 180, 200);

   /** Orange colour for saucer and "Vi" text. */
   static final Color ORANGE = new Color(212, 123, 42);

   /** Translucent white steam. */
   private static final Color STEAM =
      new Color(255, 255, 255, 120);

   /** Dark brown coffee fill. */
   static final Color COFFEE_FILL = new Color(55, 25, 10);

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
         drawCoffeeFill(g, size);
         drawCupBody(g, size);
         drawHandle(g, size);
         drawSaucer(g, size);
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

      // Glass highlight reflection
      g.setColor(new Color(255, 255, 255, 60));
      float hlX = cupL + cupW * 0.12f;
      float hlW = cupW * 0.10f;
      float hlY = cupT + cupH * 0.15f;
      float hlH = cupH * 0.55f;
      g.fill(new RoundRectangle2D.Float(
         hlX, hlY, hlW, hlH, hlW, hlW));
   }

   /** Draws a C-shaped handle attached to the cup body. */
   private static void drawHandle(Graphics2D g, int size) {
      float cupR = size * 0.68f;
      float topY = size * 0.30f;
      float botY = size * 0.52f;
      float bulgX = size * 0.84f;
      float sw = Math.max(1.5f, size / 32f);

      GeneralPath p = new GeneralPath();
      p.moveTo(cupR, topY);
      p.curveTo(bulgX, topY, bulgX, botY, cupR, botY);

      g.setStroke(new BasicStroke(sw,
         BasicStroke.CAP_ROUND,
         BasicStroke.JOIN_ROUND));
      g.setColor(CUP_FILL);
      g.draw(p);
      g.setColor(CUP_EDGE);
      g.setStroke(new BasicStroke(
         Math.max(1f, size / 60f)));
      g.draw(p);
   }

   /** Fills dark-brown coffee visible through the glass cup. */
   private static void drawCoffeeFill(Graphics2D g, int size) {
      float cupL = size * 0.20f;
      float cupR = size * 0.68f;
      float cupB = size * 0.62f;
      float cupW = cupR - cupL;
      float taper = cupW * 0.06f;
      float inset = size * 0.02f;
      float coffeeTop = size * 0.34f;
      float rad = cupW * 0.10f;

      float cL = cupL + inset;
      float cR = cupR - inset;
      float cB = cupB - inset;

      GeneralPath p = new GeneralPath();
      p.moveTo(cL, coffeeTop);
      p.lineTo(cR, coffeeTop);
      p.lineTo(cR - taper, cB - rad);
      p.quadTo(cR - taper, cB,
         cR - taper - rad, cB);
      p.lineTo(cL + taper + rad, cB);
      p.quadTo(cL + taper, cB,
         cL + taper, cB - rad);
      p.lineTo(cL, coffeeTop);
      p.closePath();

      g.setColor(COFFEE_FILL);
      g.fill(p);

      // Lighter coffee surface ellipse
      g.setColor(new Color(90, 50, 20));
      g.fill(new Ellipse2D.Float(
         cL, coffeeTop - size * 0.02f,
         cR - cL, size * 0.045f));
   }

   /** Draws an orange saucer directly below the cup. */
   private static void drawSaucer(Graphics2D g, int size) {
      g.setColor(ORANGE);
      float saucerW = size * 0.38f;
      float saucerH = size * 0.06f;
      float saucerX = size * 0.44f - saucerW / 2;
      float saucerY = size * 0.625f;
      g.fill(new Ellipse2D.Float(
         saucerX, saucerY, saucerW, saucerH));
   }

   /** Draws bold orange "Vi" text beneath the saucer. */
   private static void drawViLabel(Graphics2D g, int size) {
      g.setColor(ORANGE);
      int fontSize = Math.max(8, (int) (size * 0.30));
      g.setFont(new Font(Font.SERIF, Font.BOLD, fontSize));
      java.awt.FontMetrics fm = g.getFontMetrics();
      String text = "Vi";
      int tw = fm.stringWidth(text);
      int x = (int) (size * 0.44f) - tw / 2;
      int y = (int) (size * 0.82f) + fm.getAscent() / 2;
      g.drawString(text, x, y);
   }
}
