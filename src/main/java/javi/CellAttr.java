package javi;

/**
 * Packed bit-field representation of per-character terminal attributes.
 *
 * <p>Each cell on the terminal screen carries a set of visual attributes
 * (bold, underline, reverse, foreground/background colors).  This class
 * encodes all attributes into a single {@code int} value for compact
 * storage in a flat array.</p>
 *
 * <h2>Bit Layout</h2>
 * <pre>
 *   bits  0-7 : foreground colour (0-255, 256-colour palette)
 *   bits  8-15: background colour (0-255, 256-colour palette)
 *   bit  16   : bold
 *   bit  17   : underline
 *   bit  18   : reverse video
 *   bit  19   : italic  (reserved)
 *   bit  20   : strikethrough (reserved)
 *   bits 21-31: unused / future
 * </pre>
 *
 * <p>Colour value 0 means "default" (terminal default fg/bg).  Standard
 * ANSI colours 0-7 are stored as 1-8 (offset by one so that 0 can
 * represent "default").  Extended 256-colour values are stored as-is
 * plus one.</p>
 *
 * @see ScreenAttributes
 */
public final class CellAttr {

   /** Default (no attributes, default colours). */
   public static final int DEFAULT = 0;

   // --- bit positions ---
   private static final int FG_SHIFT   = 0;
   private static final int BG_SHIFT   = 8;
   private static final int BOLD_BIT   = 1 << 16;
   private static final int ULINE_BIT  = 1 << 17;
   private static final int REVERSE_BIT = 1 << 18;
   private static final int ITALIC_BIT = 1 << 19;
   private static final int STRIKE_BIT = 1 << 20;

   private static final int COLOR_MASK = 0xFF;

   private CellAttr() { } // utility class

   // ----- builders -----

   /**
    * Packs the given attribute fields into a single int.
    *
    * @param bold   true for bold
    * @param uline  true for underline
    * @param rev    true for reverse video
    * @param fg     foreground colour in ANSI -1..255 range
    *               (-1 = default)
    * @param bg     background colour in ANSI -1..255 range
    *               (-1 = default)
    * @return packed attribute value
    */
   public static int pack(boolean bold, boolean uline, boolean rev,
         int fg, int bg) {
      int val = 0;
      if (bold)  val |= BOLD_BIT;
      if (uline) val |= ULINE_BIT;
      if (rev)   val |= REVERSE_BIT;
      val |= (encodeColor(fg) & COLOR_MASK) << FG_SHIFT;
      val |= (encodeColor(bg) & COLOR_MASK) << BG_SHIFT;
      return val;
   }

   // ----- extractors -----

   /** Returns {@code true} if the bold flag is set. */
   public static boolean isBold(int attr) {
      return (attr & BOLD_BIT) != 0;
   }

   /** Returns {@code true} if the underline flag is set. */
   public static boolean isUnderline(int attr) {
      return (attr & ULINE_BIT) != 0;
   }

   /** Returns {@code true} if the reverse-video flag is set. */
   public static boolean isReverse(int attr) {
      return (attr & REVERSE_BIT) != 0;
   }

   /** Returns {@code true} if the italic flag is set (reserved). */
   public static boolean isItalic(int attr) {
      return (attr & ITALIC_BIT) != 0;
   }

   /** Returns {@code true} if the strikethrough flag is set (reserved). */
   public static boolean isStrikethrough(int attr) {
      return (attr & STRIKE_BIT) != 0;
   }

   /**
    * Returns the foreground colour in ANSI range (-1 = default,
    * 0-255 = palette index).
    */
   public static int fgColor(int attr) {
      return decodeColor((attr >> FG_SHIFT) & COLOR_MASK);
   }

   /**
    * Returns the background colour in ANSI range (-1 = default,
    * 0-255 = palette index).
    */
   public static int bgColor(int attr) {
      return decodeColor((attr >> BG_SHIFT) & COLOR_MASK);
   }

   /**
    * Returns an attribute with only the background colour from the
    * given attribute. Used for BCE (background colour erase) — erase
    * operations fill with the current background colour.
    *
    * @param attr packed attribute
    * @return bg-only attribute, or {@link #DEFAULT} if no bg is set
    */
   public static int bgOnly(int attr) {
      int bg = bgColor(attr);
      return bg < 0 ? DEFAULT : pack(false, false, false, -1, bg);
   }

   // ----- colour encoding helpers -----

   /**
    * Encodes an ANSI colour value (-1..255) into the 8-bit storage
    * format (0 = default, 1-256 = colour+1).
    */
   static int encodeColor(int ansiColor) {
      return ansiColor < 0 ? 0 : ansiColor + 1;
   }

   /**
    * Decodes an 8-bit stored colour back to ANSI range
    * (-1 = default, 0-255 = palette index).
    */
   static int decodeColor(int stored) {
      return stored == 0 ? -1 : stored - 1;
   }

   /**
    * Returns a human-readable description of the packed attribute
    * value, useful for debugging.
    *
    * @param attr packed attribute
    * @return description string
    */
   public static String describe(int attr) {
      if (attr == DEFAULT)
         return "DEFAULT";
      StringBuilder sb = new StringBuilder();
      if (isBold(attr))          sb.append("BOLD ");
      if (isUnderline(attr))     sb.append("ULINE ");
      if (isReverse(attr))       sb.append("REV ");
      if (isItalic(attr))        sb.append("ITAL ");
      if (isStrikethrough(attr)) sb.append("STRIKE ");
      int fg = fgColor(attr);
      int bg = bgColor(attr);
      if (fg >= 0)
         sb.append("fg=").append(fg).append(' ');
      if (bg >= 0)
         sb.append("bg=").append(bg).append(' ');
      return sb.toString().trim();
   }

   /**
    * Approximates an RGB true color to the nearest 256-color
    * palette index.  Uses the 6x6x6 color cube (indices 16-231)
    * or the grayscale ramp (indices 232-254).
    *
    * @param r red component (0-255, clamped)
    * @param g green component (0-255, clamped)
    * @param b blue component (0-255, clamped)
    * @return palette index in the range 16-254
    */
   static int approxTrueColor(int r, int g, int b) {
      r = Math.max(0, Math.min(255, r));
      g = Math.max(0, Math.min(255, g));
      b = Math.max(0, Math.min(255, b));
      // Check if grayscale (r == g == b)
      if (r == g && g == b) {
         if (r < 8) return 16;   // black in cube
         if (r > 248) return 231; // white in cube
         int idx = 232 + (r - 8) / 10;
         return Math.min(idx, 254);
      }
      // Map to 6x6x6 color cube
      int ri = (r * 5 + 127) / 255;
      int gi = (g * 5 + 127) / 255;
      int bi = (b * 5 + 127) / 255;
      return 16 + 36 * ri + 6 * gi + bi;
   }
}
