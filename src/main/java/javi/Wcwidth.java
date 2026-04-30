package javi;

/**
 * Unicode character display width for terminal emulation.
 *
 * <p>Provides {@link #of(int)} which returns the number of terminal
 * columns a character occupies: 0 for combining marks and control
 * characters, 2 for East Asian wide/fullwidth characters and emoji,
 * and 1 for everything else.  This matches the POSIX
 * {@code wcwidth(3)} function used by xterm and other terminal
 * emulators.</p>
 *
 * <p>The wide-character ranges are derived from Unicode 15.1
 * East_Asian_Width property (W and F categories) plus common emoji
 * ranges.  The combining mark ranges cover general category Mn/Mc
 * (non-spacing and spacing combining marks).</p>
 */
public final class Wcwidth {

   /** Padding character for the second column of a wide character. */
   static final char WIDE_PAD = '\0';

   private Wcwidth() { }

   /**
    * Returns the display width of a Unicode code point.
    *
    * @param cp the Unicode code point
    * @return 0 for non-spacing marks, 2 for wide/fullwidth, 1 otherwise;
    *         -1 for non-printable control characters (C0/C1 except NUL)
    */
   static int of(int cp) {
      if (cp == 0)
         return 0;
      // C0/C1 control characters
      if (cp < 32 || (cp >= 0x7f && cp < 0xa0))
         return -1;
      // Combining characters (zero width)
      if (isCombining(cp))
         return 0;
      // Wide characters (2 columns)
      if (isWide(cp))
         return 2;
      return 1;
   }

   /**
    * Returns the display width of a string (sum of character widths).
    *
    * @param s the string to measure
    * @return total display columns
    */
   public static int stringWidth(String s) {
      int width = 0;
      int i = 0;
      while (i < s.length()) {
         int cp = s.codePointAt(i);
         int w = of(cp);
         if (w > 0)
            width += w;
         i += Character.charCount(cp);
      }
      return width;
   }

   /**
    * Returns the display width of a substring.
    *
    * @param s     the string
    * @param start start index (inclusive)
    * @param end   end index (exclusive)
    * @return total display columns
    */
   static int stringWidth(String s, int start, int end) {
      int width = 0;
      int i = start;
      while (i < end && i < s.length()) {
         int cp = s.codePointAt(i);
         int w = of(cp);
         if (w > 0)
            width += w;
         i += Character.charCount(cp);
      }
      return width;
   }

   /**
    * Expands a string by inserting {@link #WIDE_PAD} after each wide
    * (2-column) character.  If no wide characters are present, returns
    * the original string unchanged.
    *
    * @param text the input string
    * @return expanded string where buffer length equals display width
    */
   static String expandWide(String text) {
      boolean needsPad = false;
      for (int i = 0; i < text.length();) {
         int cp = text.codePointAt(i);
         int cc = Character.charCount(cp);
         // Only BMP wide chars need padding (1 Java char → 2 cols).
         // Supplementary wide chars (surrogate pairs) already
         // occupy 2 Java chars = 2 buffer positions = 2 cols.
         if (of(cp) == 2 && cc == 1) {
            needsPad = true;
            break;
         }
         i += cc;
      }
      if (!needsPad)
         return text;
      StringBuilder sb = new StringBuilder(text.length() + 4);
      for (int i = 0; i < text.length();) {
         int cp = text.codePointAt(i);
         int cc = Character.charCount(cp);
         sb.appendCodePoint(cp);
         if (of(cp) == 2 && cc == 1)
            sb.append(WIDE_PAD);
         i += cc;
      }
      return sb.toString();
   }

   /**
    * Strips {@link #WIDE_PAD} characters from a buffer string,
    * producing the display string for rendering.
    *
    * @param text buffer text (may contain WIDE_PAD)
    * @return text without WIDE_PAD characters
    */
   public static String stripPadding(String text) {
      if (text.indexOf(WIDE_PAD) < 0)
         return text;
      StringBuilder sb = new StringBuilder(text.length());
      for (int i = 0; i < text.length(); i++) {
         char ch = text.charAt(i);
         if (ch != WIDE_PAD)
            sb.append(ch);
      }
      return sb.toString();
   }

   /**
    * Compresses an attribute array by removing entries at
    * {@link #WIDE_PAD} positions in the buffer text.
    *
    * @param text  buffer text (may contain WIDE_PAD)
    * @param attrs attribute array aligned with buffer positions
    * @return compressed array matching stripped display text
    */
   public static int[] compressAttrs(String text, int[] attrs) {
      if (attrs == null || text.indexOf(WIDE_PAD) < 0)
         return attrs;
      int count = 0;
      for (int i = 0; i < text.length(); i++)
         if (text.charAt(i) != WIDE_PAD)
            count++;
      int[] result = new int[count];
      int j = 0;
      int limit = Math.min(text.length(), attrs.length);
      for (int i = 0; i < limit; i++) {
         if (text.charAt(i) != WIDE_PAD)
            result[j++] = attrs[i];
      }
      return result;
   }

   /** Returns true if the code point is a combining character. */
   private static boolean isCombining(int cp) {
      // Unicode combining marks (Mn, Mc categories)
      // Covers the major ranges needed for terminal emulation
      if (cp >= 0x0300 && cp <= 0x036F) return true;  // Combining Diacriticals
      if (cp >= 0x0483 && cp <= 0x0489) return true;  // Cyrillic combining
      if (cp >= 0x0591 && cp <= 0x05BD) return true;  // Hebrew combining
      if (cp == 0x05BF) return true;
      if (cp >= 0x05C1 && cp <= 0x05C2) return true;
      if (cp >= 0x05C4 && cp <= 0x05C5) return true;
      if (cp == 0x05C7) return true;
      if (cp >= 0x0610 && cp <= 0x061A) return true;  // Arabic combining
      if (cp >= 0x064B && cp <= 0x065F) return true;
      if (cp == 0x0670) return true;
      if (cp >= 0x06D6 && cp <= 0x06DC) return true;
      if (cp >= 0x06DF && cp <= 0x06E4) return true;
      if (cp >= 0x06E7 && cp <= 0x06E8) return true;
      if (cp >= 0x06EA && cp <= 0x06ED) return true;
      if (cp == 0x0711) return true;                   // Syriac
      if (cp >= 0x0730 && cp <= 0x074A) return true;
      if (cp >= 0x07A6 && cp <= 0x07B0) return true;   // Thaana
      if (cp >= 0x0900 && cp <= 0x0903) return true;   // Devanagari
      if (cp >= 0x093A && cp <= 0x094F) return true;
      if (cp >= 0x0951 && cp <= 0x0957) return true;
      if (cp >= 0x0962 && cp <= 0x0963) return true;
      if (cp >= 0x0981 && cp <= 0x0983) return true;   // Bengali
      if (cp == 0x09BC) return true;
      if (cp >= 0x09BE && cp <= 0x09C4) return true;
      if (cp >= 0x09C7 && cp <= 0x09C8) return true;
      if (cp >= 0x09CB && cp <= 0x09CD) return true;
      if (cp == 0x09D7) return true;
      if (cp >= 0x09E2 && cp <= 0x09E3) return true;
      if (cp == 0x0A3C) return true;                   // Gurmukhi
      if (cp >= 0x0A3E && cp <= 0x0A42) return true;
      if (cp >= 0x0A47 && cp <= 0x0A48) return true;
      if (cp >= 0x0A4B && cp <= 0x0A4D) return true;
      if (cp >= 0x0A70 && cp <= 0x0A71) return true;
      if (cp >= 0x1AB0 && cp <= 0x1ACE) return true;   // Combining Diacriticals Extended
      if (cp >= 0x1DC0 && cp <= 0x1DFF) return true;   // Combining Diacriticals Supp
      if (cp >= 0x20D0 && cp <= 0x20F0) return true;   // Combining for Symbols
      if (cp >= 0xFE00 && cp <= 0xFE0F) return true;   // Variation Selectors
      if (cp >= 0xFE20 && cp <= 0xFE2F) return true;   // Combining Half Marks
      if (cp >= 0xE0100 && cp <= 0xE01EF) return true;  // Variation Selectors Supplement
      // Use Character.getType for remaining ranges
      int type = Character.getType(cp);
      return type == Character.NON_SPACING_MARK
         || type == Character.ENCLOSING_MARK;
   }

   /** Returns true if the code point occupies 2 terminal columns. */
   private static boolean isWide(int cp) {
      // CJK Unified Ideographs and related blocks
      if (cp >= 0x1100 && cp <= 0x115F) return true;   // Hangul Jamo
      if (cp >= 0x231A && cp <= 0x231B) return true;   // Hourglass, Watch
      if (cp >= 0x2329 && cp <= 0x232A) return true;   // Angle brackets
      if (cp >= 0x23E9 && cp <= 0x23F3) return true;   // Various symbols
      if (cp == 0x23F8 || cp == 0x23F9 || cp == 0x23FA)
         return true;
      if (cp == 0x25FD || cp == 0x25FE) return true;   // Medium squares
      if (cp >= 0x2614 && cp <= 0x2615) return true;   // Umbrella, Hot beverage
      if (cp >= 0x2648 && cp <= 0x2653) return true;   // Zodiac signs
      if (cp == 0x267F) return true;                    // Wheelchair
      if (cp == 0x2693) return true;                    // Anchor
      if (cp == 0x26A1) return true;                    // High voltage
      if (cp >= 0x26AA && cp <= 0x26AB) return true;   // Circles
      if (cp >= 0x26BD && cp <= 0x26BE) return true;   // Balls
      if (cp >= 0x26C4 && cp <= 0x26C5) return true;   // Snowman, Sun
      if (cp == 0x26CE) return true;                    // Ophiuchus
      if (cp == 0x26D4) return true;                    // No entry
      if (cp == 0x26EA) return true;                    // Church
      if (cp >= 0x26F2 && cp <= 0x26F3) return true;   // Fountain, Golf
      if (cp == 0x26F5) return true;                    // Sailboat
      if (cp == 0x26FA) return true;                    // Tent
      if (cp == 0x26FD) return true;                    // Fuel pump
      if (cp == 0x2702) return true;                    // Scissors
      if (cp == 0x2705) return true;                    // Check mark
      if (cp >= 0x2708 && cp <= 0x270D) return true;   // Airplane..
      if (cp == 0x270F) return true;                    // Pencil
      if (cp == 0x2712) return true;                    // Black nib
      if (cp == 0x2714) return true;                    // Check mark
      if (cp == 0x2716) return true;                    // X mark
      if (cp == 0x271D) return true;                    // Latin cross
      if (cp == 0x2721) return true;                    // Star of David
      if (cp == 0x2728) return true;                    // Sparkles
      if (cp >= 0x2733 && cp <= 0x2734) return true;   // Asterisks
      if (cp == 0x2744) return true;                    // Snowflake
      if (cp == 0x2747) return true;                    // Sparkle
      if (cp == 0x274C) return true;                    // X mark
      if (cp == 0x274E) return true;                    // X mark
      if (cp >= 0x2753 && cp <= 0x2755) return true;   // Question marks
      if (cp == 0x2757) return true;                    // Exclamation
      if (cp >= 0x2763 && cp <= 0x2764) return true;   // Heart
      if (cp >= 0x2795 && cp <= 0x2797) return true;   // Math signs
      if (cp == 0x27A1) return true;                    // Arrow
      if (cp == 0x27B0) return true;                    // Curly loop
      if (cp == 0x27BF) return true;                    // Double curly loop
      if (cp >= 0x2934 && cp <= 0x2935) return true;   // Arrows
      if (cp >= 0x2B05 && cp <= 0x2B07) return true;   // Arrows
      if (cp >= 0x2B1B && cp <= 0x2B1C) return true;   // Squares
      if (cp == 0x2B50) return true;                    // Star
      if (cp == 0x2B55) return true;                    // Circle
      if (cp >= 0x2E80 && cp <= 0x303E) return true;   // CJK radicals + misc
      if (cp >= 0x3041 && cp <= 0x33BF) return true;   // Hiragana + Katakana
      if (cp >= 0x3400 && cp <= 0x4DBF) return true;   // CJK Ext A
      if (cp >= 0x4E00 && cp <= 0x9FFF) return true;   // CJK Unified
      if (cp >= 0xA000 && cp <= 0xA4CF) return true;   // Yi Syllables + Radicals
      if (cp >= 0xA960 && cp <= 0xA97F) return true;   // Hangul Jamo Extended-A
      if (cp >= 0xAC00 && cp <= 0xD7AF) return true;   // Hangul Syllables
      if (cp >= 0xF900 && cp <= 0xFAFF) return true;   // CJK Compatibility
      if (cp >= 0xFE10 && cp <= 0xFE19) return true;   // Vertical forms
      if (cp >= 0xFE30 && cp <= 0xFE6F) return true;   // CJK Compatibility Forms
      if (cp >= 0xFF01 && cp <= 0xFF60) return true;   // Fullwidth forms
      if (cp >= 0xFFE0 && cp <= 0xFFE6) return true;   // Fullwidth signs
      // Supplementary wide ranges
      if (cp >= 0x1F004 && cp <= 0x1F004) return true;  // Mahjong tile
      if (cp == 0x1F0CF) return true;                    // Joker card
      if (cp >= 0x1F170 && cp <= 0x1F171) return true;  // Blood types
      if (cp == 0x1F17E || cp == 0x1F17F) return true;  // Parking + more
      if (cp == 0x1F18E) return true;                    // AB button
      if (cp >= 0x1F191 && cp <= 0x1F19A) return true;  // Squared symbols
      if (cp >= 0x1F1E0 && cp <= 0x1F1FF) return true;  // Flags
      if (cp >= 0x1F200 && cp <= 0x1F202) return true;  // CJK ideographic
      if (cp == 0x1F21A || cp == 0x1F22F) return true;
      if (cp >= 0x1F232 && cp <= 0x1F23A) return true;
      if (cp >= 0x1F250 && cp <= 0x1F251) return true;
      if (cp >= 0x1F300 && cp <= 0x1F64F) return true;  // Misc Symbols + Emoticons
      if (cp >= 0x1F680 && cp <= 0x1F6FF) return true;  // Transport + Map
      if (cp >= 0x1F900 && cp <= 0x1F9FF) return true;  // Supplemental Symbols
      if (cp >= 0x1FA00 && cp <= 0x1FA6F) return true;  // Chess + extended
      if (cp >= 0x1FA70 && cp <= 0x1FAFF) return true;  // Symbols Extended-A
      if (cp >= 0x20000 && cp <= 0x2FFFD) return true;  // CJK Ext B-F
      if (cp >= 0x30000 && cp <= 0x3FFFD) return true;  // CJK Ext G+
      return false;
   }
}
