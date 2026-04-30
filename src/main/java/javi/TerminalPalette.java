package javi;

import java.awt.Color;

/**
 * Xterm 256-color palette mapping to AWT Colors.
 *
 * <p>The palette follows the standard xterm-256color specification:</p>
 * <ul>
 *   <li>0-7: standard ANSI colors</li>
 *   <li>8-15: bright/high-intensity ANSI colors</li>
 *   <li>16-231: 6x6x6 color cube</li>
 *   <li>232-255: 24-step grayscale ramp</li>
 * </ul>
 *
 * @see CellAttr
 */
public final class TerminalPalette {

   /** The 256-color palette as AWT Color objects. */
   private static final Color[] PALETTE = new Color[256];

   /** The 6x6x6 color cube axis values. */
   private static final int[] CUBE_VALUES =
      {0, 0x5F, 0x87, 0xAF, 0xD7, 0xFF};

   static {
      // 0-7: standard ANSI colors
      PALETTE[0]  = new Color(0, 0, 0);       // black
      PALETTE[1]  = new Color(205, 0, 0);      // red
      PALETTE[2]  = new Color(0, 205, 0);      // green
      PALETTE[3]  = new Color(205, 205, 0);    // yellow
      PALETTE[4]  = new Color(0, 0, 238);      // blue
      PALETTE[5]  = new Color(205, 0, 205);    // magenta
      PALETTE[6]  = new Color(0, 205, 205);    // cyan
      PALETTE[7]  = new Color(229, 229, 229);  // white

      // 8-15: bright/high-intensity ANSI colors
      PALETTE[8]  = new Color(127, 127, 127);  // bright black
      PALETTE[9]  = new Color(255, 0, 0);      // bright red
      PALETTE[10] = new Color(0, 255, 0);      // bright green
      PALETTE[11] = new Color(255, 255, 0);    // bright yellow
      PALETTE[12] = new Color(92, 92, 255);    // bright blue
      PALETTE[13] = new Color(255, 0, 255);    // bright magenta
      PALETTE[14] = new Color(0, 255, 255);    // bright cyan
      PALETTE[15] = new Color(255, 255, 255);  // bright white

      // 16-231: 6x6x6 color cube
      for (int r = 0; r < 6; r++)
         for (int g = 0; g < 6; g++)
            for (int b = 0; b < 6; b++)
               PALETTE[16 + 36 * r + 6 * g + b] =
                  new Color(CUBE_VALUES[r], CUBE_VALUES[g],
                     CUBE_VALUES[b]);

      // 232-255: 24-step grayscale ramp
      for (int i = 0; i < 24; i++) {
         int v = 8 + 10 * i;
         PALETTE[232 + i] = new Color(v, v, v);
      }
   }

   /** Default foreground color for VT100 protocol (xterm resource default).
    * This is the value OSC 110 resets to; rendering uses OldView's theme. */
   static final Color DEFAULT_FG = new Color(0, 0, 0);

   /** Default background color (xterm default: black). */
   static final Color DEFAULT_BG = new Color(0, 0, 0);

   /** Default cursor color (xterm default: light gray). */
   static final Color DEFAULT_CURSOR = new Color(229, 229, 229);

   /** Default highlight foreground (no default — use null). */
   static final Color DEFAULT_HIGHLIGHT_FG = null;

   /** Default highlight background (no default — use null). */
   static final Color DEFAULT_HIGHLIGHT_BG = null;

   private TerminalPalette() { } // utility class

   /**
    * Converts a 256-color palette index to an AWT Color.
    *
    * @param index palette index (0-255)
    * @return the corresponding Color, or null if out of range
    */
   public static Color toAwtColor(int index) {
      if (index < 0 || index > 255)
         return null;
      return PALETTE[index];
   }

   /**
    * Finds the nearest palette index for an RGB triplet.
    *
    * <p>Uses minimum squared-distance in RGB space.
    * Suitable for approximating truecolor SGR values.</p>
    *
    * @param r red component (0-255)
    * @param g green component (0-255)
    * @param b blue component (0-255)
    * @return nearest palette index (0-255)
    */
   public static int nearestColor(int r, int g, int b) {
      int bestDist = Integer.MAX_VALUE;
      int bestIdx = 0;

      // Check the 16 standard ANSI colors
      for (int i = 0; i < 16; i++) {
         int d = colorDistSq(r, g, b, PALETTE[i]);
         if (d < bestDist) {
            bestDist = d;
            bestIdx = i;
         }
      }

      // Check the 6x6x6 cube — use the nearest axis values
      int ri = nearestCubeAxis(r);
      int gi = nearestCubeAxis(g);
      int bi = nearestCubeAxis(b);
      int cubeIdx = 16 + 36 * ri + 6 * gi + bi;
      int cubeDist = colorDistSq(r, g, b, PALETTE[cubeIdx]);
      if (cubeDist < bestDist) {
         bestDist = cubeDist;
         bestIdx = cubeIdx;
      }

      // Check grayscale ramp — may be closer for near-gray colors
      int grayBase = (r + g + b) / 3;
      int gi2 = (grayBase - 8) / 10;
      if (gi2 < 0) gi2 = 0;
      if (gi2 > 23) gi2 = 23;
      for (int delta = -1; delta <= 1; delta++) {
         int idx = gi2 + delta;
         if (idx >= 0 && idx <= 23) {
            int d = colorDistSq(r, g, b, PALETTE[232 + idx]);
            if (d < bestDist) {
               bestDist = d;
               bestIdx = 232 + idx;
            }
         }
      }
      return bestIdx;
   }

   /**
    * Returns the nearest 6x6x6 cube axis index for a component.
    */
   private static int nearestCubeAxis(int val) {
      // CUBE_VALUES = {0, 0x5F, 0x87, 0xAF, 0xD7, 0xFF}
      if (val < 0x2F) return 0;
      if (val < 0x73) return 1;
      if (val < 0x9B) return 2;
      if (val < 0xC3) return 3;
      if (val < 0xEB) return 4;
      return 5;
   }

   /** Squared RGB distance between an RGB triplet and a Color. */
   private static int colorDistSq(int r, int g, int b, Color c) {
      int dr = r - c.getRed();
      int dg = g - c.getGreen();
      int db = b - c.getBlue();
      return dr * dr + dg * dg + db * db;
   }

   /**
    * Returns the default palette color for the given index.
    *
    * @param index palette index (0-255)
    * @return the default Color, or null if out of range
    */
   static Color defaultColor(int index) {
      if (index < 0 || index > 255)
         return null;
      return PALETTE[index];
   }

   /**
    * Formats a Color as an X11 16-bit rgb response string.
    *
    * <p>Each 8-bit component is scaled to 16-bit by byte-doubling
    * (e.g., 0xCD → CDCD), producing {@code rgb:RRRR/GGGG/BBBB}.</p>
    *
    * @param c the Color to format
    * @return X11 rgb string like "rgb:cdcd/0000/cdcd"
    */
   static String formatX11Color(Color c) {
      return String.format("rgb:%04x/%04x/%04x",
         c.getRed() * 0x101, c.getGreen() * 0x101,
         c.getBlue() * 0x101);
   }

   /**
    * Parses an X11 color specification to an AWT Color.
    *
    * <p>Supported formats:</p>
    * <ul>
    *   <li>{@code #RGB} — 4-bit per channel</li>
    *   <li>{@code #RRGGBB} — 8-bit per channel</li>
    *   <li>{@code #RRRGGGBBB} — 12-bit per channel</li>
    *   <li>{@code #RRRRGGGGBBBB} — 16-bit per channel</li>
    *   <li>{@code rgb:R/G/B} — 1-4 hex digits per channel</li>
    *   <li>{@code rgbi:R/G/B} — floating point 0.0-1.0</li>
    * </ul>
    *
    * @param spec the color specification string
    * @return the parsed Color, or null if unparseable
    */
   static Color parseX11Color(String spec) {
      if (spec == null || spec.isEmpty())
         return null;
      if (spec.startsWith("#"))
         return parseHashColor(spec);
      if (spec.startsWith("rgb:"))
         return parseRgbColor(spec.substring(4));
      if (spec.startsWith("rgbi:"))
         return parseRgbiColor(spec.substring(5));
      return null;
   }

   /**
    * Parses a {@code #RGB}, {@code #RRGGBB}, {@code #RRRGGGBBB},
    * or {@code #RRRRGGGGBBBB} color.
    */
   private static Color parseHashColor(String spec) {
      String hex = spec.substring(1);
      int len = hex.length();
      int digits;
      if (len == 3) digits = 1;
      else if (len == 6) digits = 2;
      else if (len == 9) digits = 3;
      else if (len == 12) digits = 4;
      else return null;
      try {
         int r = Integer.parseInt(hex.substring(0, digits), 16);
         int g = Integer.parseInt(
            hex.substring(digits, digits * 2), 16);
         int b = Integer.parseInt(
            hex.substring(digits * 2, digits * 3), 16);
         // Scale to 8-bit: left-shift to fill 8 bits
         r = scaleToEight(r, digits);
         g = scaleToEight(g, digits);
         b = scaleToEight(b, digits);
         return new Color(r, g, b);
      } catch (NumberFormatException e) {
         return null;
      }
   }

   /**
    * Parses a {@code R/G/B} string where each component is 1-4
    * hex digits.
    */
   private static Color parseRgbColor(String spec) {
      String[] parts = spec.split("/");
      if (parts.length != 3)
         return null;
      try {
         int r = Integer.parseInt(parts[0], 16);
         int g = Integer.parseInt(parts[1], 16);
         int b = Integer.parseInt(parts[2], 16);
         r = scaleToEight(r, parts[0].length());
         g = scaleToEight(g, parts[1].length());
         b = scaleToEight(b, parts[2].length());
         return new Color(r, g, b);
      } catch (NumberFormatException e) {
         return null;
      }
   }

   /**
    * Parses an {@code R/G/B} string with floating point 0.0-1.0
    * per channel.
    */
   private static Color parseRgbiColor(String spec) {
      String[] parts = spec.split("/");
      if (parts.length != 3)
         return null;
      try {
         float r = Float.parseFloat(parts[0]);
         float g = Float.parseFloat(parts[1]);
         float b = Float.parseFloat(parts[2]);
         return new Color(
            Math.max(0, Math.min(255, Math.round(r * 255))),
            Math.max(0, Math.min(255, Math.round(g * 255))),
            Math.max(0, Math.min(255, Math.round(b * 255))));
      } catch (NumberFormatException e) {
         return null;
      }
   }

   /**
    * Scales a hex value with the given number of hex digits to 8-bit.
    *
    * <p>1 digit: left-shift by 4 (f → f0).
    * 2 digits: as-is (ff → ff).
    * 3 digits: right-shift by 4 (fff → ff).
    * 4 digits: right-shift by 8 (ffff → ff).</p>
    */
   private static int scaleToEight(int val, int hexDigits) {
      switch (hexDigits) {
         case 1: return (val << 4) & 0xFF;
         case 2: return val & 0xFF;
         case 3: return (val >> 4) & 0xFF;
         case 4: return (val >> 8) & 0xFF;
         default: return val & 0xFF;
      }
   }
}
