package javi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link CellAttr} bit-packing and extraction.
 */
class CellAttrJUnitTest {

   @Test
   @DisplayName("DEFAULT has no flags and default colours")
   void defaultIsZero() {
      int attr = CellAttr.DEFAULT;
      assertFalse(CellAttr.isBold(attr));
      assertFalse(CellAttr.isUnderline(attr));
      assertFalse(CellAttr.isReverse(attr));
      assertFalse(CellAttr.isItalic(attr));
      assertFalse(CellAttr.isStrikethrough(attr));
      assertEquals(-1, CellAttr.fgColor(attr));
      assertEquals(-1, CellAttr.bgColor(attr));
   }

   @Test
   @DisplayName("pack and extract bold flag")
   void boldFlag() {
      int attr = CellAttr.pack(true, false, false, -1, -1);
      assertTrue(CellAttr.isBold(attr));
      assertFalse(CellAttr.isUnderline(attr));
      assertFalse(CellAttr.isReverse(attr));
   }

   @Test
   @DisplayName("pack and extract underline flag")
   void underlineFlag() {
      int attr = CellAttr.pack(false, true, false, -1, -1);
      assertFalse(CellAttr.isBold(attr));
      assertTrue(CellAttr.isUnderline(attr));
   }

   @Test
   @DisplayName("pack and extract reverse flag")
   void reverseFlag() {
      int attr = CellAttr.pack(false, false, true, -1, -1);
      assertTrue(CellAttr.isReverse(attr));
   }

   @Test
   @DisplayName("pack and extract foreground colour")
   void fgColour() {
      int attr = CellAttr.pack(false, false, false, 3, -1);
      assertEquals(3, CellAttr.fgColor(attr));
      assertEquals(-1, CellAttr.bgColor(attr));
   }

   @Test
   @DisplayName("pack and extract background colour")
   void bgColour() {
      int attr = CellAttr.pack(false, false, false, -1, 5);
      assertEquals(-1, CellAttr.fgColor(attr));
      assertEquals(5, CellAttr.bgColor(attr));
   }

   @Test
   @DisplayName("pack all flags and colours together")
   void allFlags() {
      int attr = CellAttr.pack(true, true, true, 7, 0);
      assertTrue(CellAttr.isBold(attr));
      assertTrue(CellAttr.isUnderline(attr));
      assertTrue(CellAttr.isReverse(attr));
      assertEquals(7, CellAttr.fgColor(attr));
      assertEquals(0, CellAttr.bgColor(attr));
   }

   @Test
   @DisplayName("encode/decode default colour round-trip")
   void colourDefaultRoundTrip() {
      assertEquals(0, CellAttr.encodeColor(-1));
      assertEquals(-1, CellAttr.decodeColor(0));
   }

   @Test
   @DisplayName("encode/decode palette colour round-trip")
   void colourPaletteRoundTrip() {
      for (int c = 0; c < 255; c++) {
         int stored = CellAttr.encodeColor(c);
         assertEquals(c, CellAttr.decodeColor(stored),
            "round-trip failed for colour " + c);
      }
   }

   @Test
   @DisplayName("describe returns DEFAULT for zero")
   void describeDefault() {
      assertEquals("DEFAULT", CellAttr.describe(CellAttr.DEFAULT));
   }

   @Test
   @DisplayName("describe includes flag names")
   void describeFlags() {
      int attr = CellAttr.pack(true, false, true, 2, -1);
      String desc = CellAttr.describe(attr);
      assertTrue(desc.contains("BOLD"), desc);
      assertTrue(desc.contains("REV"), desc);
      assertTrue(desc.contains("fg=2"), desc);
   }

   // ── 256-colour support tests ──────────────────────────────

   @Test
   @DisplayName("256-colour fg round-trip for standard ANSI (0-7)")
   void colour256StandardAnsi() {
      for (int c = 0; c <= 7; c++) {
         int attr = CellAttr.pack(false, false, false, c, -1);
         assertEquals(c, CellAttr.fgColor(attr),
            "fg colour " + c);
         assertEquals(-1, CellAttr.bgColor(attr));
      }
   }

   @Test
   @DisplayName("256-colour fg round-trip for bright ANSI (8-15)")
   void colour256BrightAnsi() {
      for (int c = 8; c <= 15; c++) {
         int attr = CellAttr.pack(false, false, false, c, -1);
         assertEquals(c, CellAttr.fgColor(attr),
            "fg colour " + c);
      }
   }

   @Test
   @DisplayName("256-colour fg round-trip for 6x6x6 cube (16-231)")
   void colour256Cube() {
      for (int c = 16; c <= 231; c++) {
         int attr = CellAttr.pack(false, false, false, c, -1);
         assertEquals(c, CellAttr.fgColor(attr),
            "fg colour " + c);
      }
   }

   @Test
   @DisplayName("256-colour fg round-trip for grayscale (232-254)")
   void colour256Grayscale() {
      for (int c = 232; c <= 254; c++) {
         int attr = CellAttr.pack(false, false, false, c, -1);
         assertEquals(c, CellAttr.fgColor(attr),
            "fg colour " + c);
      }
   }

   @Test
   @DisplayName("256-colour bg round-trip full range")
   void colour256BgFullRange() {
      for (int c = 0; c <= 254; c++) {
         int attr = CellAttr.pack(false, false, false, -1, c);
         assertEquals(c, CellAttr.bgColor(attr),
            "bg colour " + c);
         assertEquals(-1, CellAttr.fgColor(attr));
      }
   }

   @Test
   @DisplayName("fg and bg 256-colours coexist without overlap")
   void colour256FgBgCoexist() {
      int attr = CellAttr.pack(false, false, false, 196, 21);
      assertEquals(196, CellAttr.fgColor(attr));
      assertEquals(21, CellAttr.bgColor(attr));
   }

   @Test
   @DisplayName("256-colour with bold flag preserved")
   void colour256WithBold() {
      int attr = CellAttr.pack(true, false, false, 196, 21);
      assertTrue(CellAttr.isBold(attr));
      assertEquals(196, CellAttr.fgColor(attr));
      assertEquals(21, CellAttr.bgColor(attr));
   }

   @Test
   @DisplayName("describe shows 256-colour values")
   void describe256Colour() {
      int attr = CellAttr.pack(false, false, false, 196, 21);
      String desc = CellAttr.describe(attr);
      assertTrue(desc.contains("fg=196"), desc);
      assertTrue(desc.contains("bg=21"), desc);
   }

   @Test
   @DisplayName("boundary colour 0 round-trips correctly")
   void colourBoundaryZero() {
      int attr = CellAttr.pack(false, false, false, 0, 0);
      assertEquals(0, CellAttr.fgColor(attr));
      assertEquals(0, CellAttr.bgColor(attr));
   }

   @Test
   @DisplayName("boundary colour 254 round-trips correctly")
   void colourBoundary254() {
      int attr = CellAttr.pack(false, false, false, 254, 254);
      assertEquals(254, CellAttr.fgColor(attr));
      assertEquals(254, CellAttr.bgColor(attr));
   }

   @Test
   @DisplayName("colour 255 cannot be stored (8-bit limit)")
   void colour255OverflowsToDefault() {
      // encodeColor(255) = 256, which wraps to 0 in 8 bits
      // This is a known limitation — 255 maps to default
      int attr = CellAttr.pack(false, false, false, 255, -1);
      assertEquals(-1, CellAttr.fgColor(attr),
         "color 255 overflows 8-bit storage to default");
   }

   // ── true-color approximation tests ────────────────────────

   @Test
   @DisplayName("approxTrueColor: pure black → palette 16")
   void approxBlack() {
      assertEquals(16, CellAttr.approxTrueColor(0, 0, 0));
   }

   @Test
   @DisplayName("approxTrueColor: pure white → palette 231")
   void approxWhite() {
      assertEquals(231, CellAttr.approxTrueColor(255, 255, 255));
   }

   @Test
   @DisplayName("approxTrueColor: pure red → palette 196")
   void approxRed() {
      // Pure red (255,0,0) → cube(5,0,0) = 16 + 180 = 196
      assertEquals(196, CellAttr.approxTrueColor(255, 0, 0));
   }

   @Test
   @DisplayName("approxTrueColor: pure green → palette 46")
   void approxGreen() {
      // Pure green (0,255,0) → cube(0,5,0) = 16 + 30 = 46
      assertEquals(46, CellAttr.approxTrueColor(0, 255, 0));
   }

   @Test
   @DisplayName("approxTrueColor: pure blue → palette 21")
   void approxBlue() {
      // Pure blue (0,0,255) → cube(0,0,5) = 16 + 5 = 21
      assertEquals(21, CellAttr.approxTrueColor(0, 0, 255));
   }

   @Test
   @DisplayName("approxTrueColor: orange (255,128,0) → cube")
   void approxOrange() {
      // r=5, g≈2.5→3, b=0 → 16 + 180 + 18 = 214
      int result = CellAttr.approxTrueColor(255, 128, 0);
      assertEquals(214, result);
   }

   @Test
   @DisplayName("approxTrueColor: mid-gray → grayscale ramp")
   void approxMidGray() {
      // Gray(128,128,128) → 232 + (128-8)/10 = 232 + 12 = 244
      assertEquals(244, CellAttr.approxTrueColor(128, 128, 128));
   }

   @Test
   @DisplayName("approxTrueColor: dark gray → grayscale ramp")
   void approxDarkGray() {
      // Gray(50,50,50) → 232 + (50-8)/10 = 232 + 4 = 236
      assertEquals(236, CellAttr.approxTrueColor(50, 50, 50));
   }

   @Test
   @DisplayName("approxTrueColor: near-black gray → palette 16")
   void approxNearBlack() {
      // Gray(5,5,5) → r<8, return 16 (black in cube)
      assertEquals(16, CellAttr.approxTrueColor(5, 5, 5));
   }

   @Test
   @DisplayName("approxTrueColor: near-white gray → palette 231")
   void approxNearWhite() {
      // Gray(250,250,250) → r>248, return 231 (white in cube)
      assertEquals(231, CellAttr.approxTrueColor(250, 250, 250));
   }

   @Test
   @DisplayName("approxTrueColor clamps negative values")
   void approxClampsNegative() {
      // Should clamp to (0,0,0) → grayscale → black → 16
      assertEquals(16, CellAttr.approxTrueColor(-10, -5, -1));
   }

   @Test
   @DisplayName("approxTrueColor clamps values above 255")
   void approxClampsHigh() {
      // Should clamp to (255,255,255) → white → 231
      assertEquals(231, CellAttr.approxTrueColor(300, 400, 500));
   }

   @Test
   @DisplayName("approxTrueColor: yellow (255,255,0) → cube")
   void approxYellow() {
      // r=5, g=5, b=0 → 16 + 180 + 30 = 226
      assertEquals(226, CellAttr.approxTrueColor(255, 255, 0));
   }

   @Test
   @DisplayName("approxTrueColor: cyan (0,255,255) → cube")
   void approxCyan() {
      // r=0, g=5, b=5 → 16 + 30 + 5 = 51
      assertEquals(51, CellAttr.approxTrueColor(0, 255, 255));
   }

   @Test
   @DisplayName("approxTrueColor: magenta (255,0,255) → cube")
   void approxMagenta() {
      // r=5, g=0, b=5 → 16 + 180 + 5 = 201
      assertEquals(201, CellAttr.approxTrueColor(255, 0, 255));
   }

   @Test
   @DisplayName("approxTrueColor: gray boundary at 8")
   void approxGrayBoundary8() {
      // Gray(8,8,8) → 232 + (8-8)/10 = 232
      assertEquals(232, CellAttr.approxTrueColor(8, 8, 8));
   }

   @Test
   @DisplayName("approxTrueColor: gray boundary at 248")
   void approxGrayBoundary248() {
      // Gray(248,248,248) → 232 + (248-8)/10 = 256 → capped to 254
      assertEquals(254, CellAttr.approxTrueColor(248, 248, 248));
   }

   @Test
   @DisplayName("approxTrueColor: non-gray similar channels → cube")
   void approxAlmostGray() {
      // (100,101,100) → r != g, so NOT grayscale path
      // Maps to cube instead
      int result = CellAttr.approxTrueColor(100, 101, 100);
      assertTrue(result >= 16 && result <= 231,
         "near-gray with unequal channels uses cube: " + result);
   }
}
