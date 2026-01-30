package javi.awt;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for creating application icons for the Javi editor.
 *
 * <p>Generates programmatic icons since we cannot include external graphics files.
 * Creates a distinctive icon with a large "J" watermark and "vi" text on a 
 * dark blue background, reflecting Javi's vi-style editing heritage.</p>
 *
 * <h2>Icon Design</h2>
 * <ul>
 *   <li><b>Background</b>: Dark blue (#1E3C78) - professional, easy to see</li>
 *   <li><b>Watermark</b>: Large "J" in light blue, centered behind text</li>
 *   <li><b>Text</b>: White "vi" in monospaced font</li>
 *   <li><b>Border</b>: Subtle lighter blue border for definition</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Single icon
 * Image icon = IconUtil.createJaviIcon(32);
 * frame.setIconImage(icon);
 *
 * // Multiple sizes (recommended for better rendering)
 * List<Image> icons = IconUtil.createJaviIcons();
 * frame.setIconImages(icons);
 * }</pre>
 *
 * @see java.awt.Frame#setIconImage(Image)
 * @see java.awt.Frame#setIconImages(List)
 */
public final class IconUtil {

   /** Dark blue background color for the icon. */
   private static final Color BACKGROUND_COLOR = new Color(30, 60, 120);

   /** Lighter blue for the border. */
   private static final Color BORDER_COLOR = new Color(50, 90, 160);

   /** Light blue color for the background "J" watermark (between blue and white). */
   private static final Color WATERMARK_COLOR = new Color(100, 140, 200);

   /** White text color. */
   private static final Color TEXT_COLOR = Color.WHITE;

   /** Private constructor to prevent instantiation. */
   private IconUtil() {
      // Utility class - no instances
   }

   /**
    * Creates a Javi icon at the specified size.
    *
    * <p>The icon features a large "J" watermark in the background with "vi" text 
    * centered on top, all on a dark blue background with a subtle border. 
    * The font sizes scale with the icon size.</p>
    *
    * @param size the width and height of the icon in pixels (square icon)
    * @return a new BufferedImage containing the icon
    */
   public static Image createJaviIcon(int size) {
      BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2d = img.createGraphics();

      try {
         // Enable antialiasing for smoother text
         g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                              RenderingHints.VALUE_ANTIALIAS_ON);
         g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                              RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

         // Draw background
         g2d.setColor(BACKGROUND_COLOR);
         g2d.fillRect(0, 0, size, size);

         // Draw large "J" watermark in the background
         g2d.setColor(WATERMARK_COLOR);
         int jFontSize = Math.max(12, (int) (size * 1.1));
         g2d.setFont(new Font(Font.SERIF, Font.BOLD, jFontSize));
         java.awt.FontMetrics jFm = g2d.getFontMetrics();
         String jText = "J";
         int jWidth = jFm.stringWidth(jText);
         int jHeight = jFm.getAscent();
         // Center the J, slightly offset down
         int jX = (size - jWidth) / 2;
         int jY = (size + jHeight) / 2 - jFm.getDescent() / 2 + size / 10;
         g2d.drawString(jText, jX, jY);

         // Draw border
         g2d.setColor(BORDER_COLOR);
         g2d.drawRect(0, 0, size - 1, size - 1);
         if (size >= 32) {
            g2d.drawRect(1, 1, size - 3, size - 3);
         }

         // Draw "vi" text
         g2d.setColor(TEXT_COLOR);

         // Scale font size based on icon size
         int fontSize = Math.max(8, (int) (size * 0.5));
         g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, fontSize));

         // Center the text
         java.awt.FontMetrics fm = g2d.getFontMetrics();
         String text = "vi";
         int textWidth = fm.stringWidth(text);
         int textHeight = fm.getAscent();

         int x = (size - textWidth) / 2;
         int y = (size + textHeight) / 2 - fm.getDescent() / 2;

         g2d.drawString(text, x, y);

      } finally {
         g2d.dispose();
      }

      return img;
   }

   /**
    * Creates a default 32x32 Javi icon.
    *
    * <p>This is a convenience method equivalent to calling
    * {@code createJaviIcon(32)}.</p>
    *
    * @return a new 32x32 BufferedImage containing the icon
    */
   public static Image createJaviIcon() {
      return createJaviIcon(32);
   }

   /**
    * Creates a list of Javi icons at multiple standard sizes.
    *
    * <p>Providing multiple icon sizes allows the system to choose
    * the most appropriate size for different contexts (taskbar,
    * window title, dock, etc.). The returned list contains icons
    * at 16x16, 32x32, 48x48, and 64x64 pixels.</p>
    *
    * @return a list of icon images at various sizes
    */
   public static List<Image> createJaviIcons() {
      List<Image> icons = new ArrayList<>();
      icons.add(createJaviIcon(16));
      icons.add(createJaviIcon(32));
      icons.add(createJaviIcon(48));
      icons.add(createJaviIcon(64));
      return icons;
   }
}
