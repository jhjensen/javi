package javi;

import java.awt.Color;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * JUnit 5 tests for {@link TerminalPalette}.
 */
class TerminalPaletteJUnitTest {

   @Test
   @DisplayName("standard ANSI colors are correct")
   void standardAnsiColors() {
      assertEquals(new Color(0, 0, 0), TerminalPalette.toAwtColor(0));
      assertEquals(new Color(205, 0, 0), TerminalPalette.toAwtColor(1));
      assertEquals(new Color(0, 205, 0), TerminalPalette.toAwtColor(2));
      assertEquals(new Color(205, 205, 0), TerminalPalette.toAwtColor(3));
      assertEquals(new Color(0, 0, 238), TerminalPalette.toAwtColor(4));
      assertEquals(new Color(229, 229, 229), TerminalPalette.toAwtColor(7));
   }

   @Test
   @DisplayName("bright ANSI colors are correct")
   void brightAnsiColors() {
      assertEquals(new Color(127, 127, 127),
         TerminalPalette.toAwtColor(8));
      assertEquals(new Color(255, 0, 0),
         TerminalPalette.toAwtColor(9));
      assertEquals(new Color(255, 255, 255),
         TerminalPalette.toAwtColor(15));
   }

   @Test
   @DisplayName("color cube index 16 is black")
   void colorCubeBlack() {
      assertEquals(new Color(0, 0, 0),
         TerminalPalette.toAwtColor(16));
   }

   @Test
   @DisplayName("color cube index 231 is white")
   void colorCubeWhite() {
      assertEquals(new Color(0xFF, 0xFF, 0xFF),
         TerminalPalette.toAwtColor(231));
   }

   @Test
   @DisplayName("color cube red (196) is pure red")
   void colorCubeRed() {
      // 196 = 16 + 36*5 + 6*0 + 0 = 196
      assertEquals(new Color(0xFF, 0, 0),
         TerminalPalette.toAwtColor(196));
   }

   @Test
   @DisplayName("grayscale ramp endpoints")
   void grayscaleRamp() {
      assertEquals(new Color(8, 8, 8),
         TerminalPalette.toAwtColor(232));
      assertEquals(new Color(238, 238, 238),
         TerminalPalette.toAwtColor(255));
   }

   @Test
   @DisplayName("out-of-range returns null")
   void outOfRange() {
      assertNull(TerminalPalette.toAwtColor(-1));
      assertNull(TerminalPalette.toAwtColor(256));
   }

   @Test
   @DisplayName("all 256 palette entries are non-null")
   void allEntriesNonNull() {
      for (int i = 0; i < 256; i++)
         assertNotNull(TerminalPalette.toAwtColor(i),
            "palette index " + i);
   }

   @Test
   @DisplayName("nearestColor maps exact palette colors back")
   void nearestColorExactMatch() {
      // Pure red (205,0,0) is ANSI 1
      assertEquals(1, TerminalPalette.nearestColor(205, 0, 0));
      // Pure white (255,255,255) is ANSI 15
      assertEquals(15, TerminalPalette.nearestColor(255, 255, 255));
   }

   @Test
   @DisplayName("nearestColor maps true gray to grayscale ramp")
   void nearestColorGray() {
      // Mid-gray (128,128,128) should hit grayscale ramp
      int idx = TerminalPalette.nearestColor(128, 128, 128);
      Color c = TerminalPalette.toAwtColor(idx);
      // Should be reasonably close
      int dist = Math.abs(c.getRed() - 128)
         + Math.abs(c.getGreen() - 128)
         + Math.abs(c.getBlue() - 128);
      assertEquals(true, dist < 30,
         "gray distance " + dist + " too large");
   }

   @Test
   @DisplayName("nearestColor approximates truecolor")
   void nearestColorApprox() {
      // Some arbitrary RGB — just verify it returns valid index
      int idx = TerminalPalette.nearestColor(100, 150, 200);
      assertNotNull(TerminalPalette.toAwtColor(idx));
      assertEquals(true, idx >= 0 && idx <= 255);
   }
}
