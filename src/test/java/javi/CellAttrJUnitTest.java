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
}
