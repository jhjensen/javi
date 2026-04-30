package javi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SgrState} — the SGR (Select Graphic Rendition)
 * state machine that processes VT100 color and attribute sequences.
 *
 * <p>Verifies that escape-sequence parameters produce the correct
 * packed {@link CellAttr} values, covering standard ANSI colors,
 * bright colors, 256-color palette, truecolor approximation, and
 * text attribute flags (bold, underline, reverse).</p>
 */
class SgrStateJUnitTest {

   private SgrState sgr;

   @BeforeEach
   void setUp() {
      sgr = new SgrState();
   }

   // ── Initial state ─────────────────────────────────────────

   @Test
   @DisplayName("initial state is DEFAULT")
   void initialStateIsDefault() {
      assertEquals(CellAttr.DEFAULT, sgr.currentAttr());
   }

   // ── Reset (SGR 0) ────────────────────────────────────────

   @Test
   @DisplayName("SGR 0 resets to DEFAULT")
   void resetClearsAll() {
      sgr.process(new int[]{1, 31, 42});
      sgr.process(new int[]{0});
      assertEquals(CellAttr.DEFAULT, sgr.currentAttr());
   }

   @Test
   @DisplayName("empty SGR (ESC[m) defaults to reset")
   void emptyParamsDefaultsToReset() {
      sgr.process(new int[]{1});
      sgr.process(new int[]{0}); // parser sends 0 for ESC[m
      assertEquals(CellAttr.DEFAULT, sgr.currentAttr());
   }

   // ── Text attributes ──────────────────────────────────────

   @Nested
   @DisplayName("text attribute flags")
   class TextAttributes {

      @Test
      @DisplayName("SGR 1 sets bold")
      void boldOn() {
         sgr.process(new int[]{1});
         assertTrue(CellAttr.isBold(sgr.currentAttr()));
      }

      @Test
      @DisplayName("SGR 22 clears bold")
      void boldOff() {
         sgr.process(new int[]{1});
         sgr.process(new int[]{22});
         assertFalse(CellAttr.isBold(sgr.currentAttr()));
      }

      @Test
      @DisplayName("SGR 4 sets underline")
      void underlineOn() {
         sgr.process(new int[]{4});
         assertTrue(CellAttr.isUnderline(sgr.currentAttr()));
      }

      @Test
      @DisplayName("SGR 24 clears underline")
      void underlineOff() {
         sgr.process(new int[]{4});
         sgr.process(new int[]{24});
         assertFalse(CellAttr.isUnderline(sgr.currentAttr()));
      }

      @Test
      @DisplayName("SGR 7 sets reverse video")
      void reverseOn() {
         sgr.process(new int[]{7});
         assertTrue(CellAttr.isReverse(sgr.currentAttr()));
      }

      @Test
      @DisplayName("SGR 27 clears reverse video")
      void reverseOff() {
         sgr.process(new int[]{7});
         sgr.process(new int[]{27});
         assertFalse(CellAttr.isReverse(sgr.currentAttr()));
      }

      @Test
      @DisplayName("bold + underline + reverse all set")
      void allFlagsSet() {
         sgr.process(new int[]{1, 4, 7});
         int attr = sgr.currentAttr();
         assertTrue(CellAttr.isBold(attr));
         assertTrue(CellAttr.isUnderline(attr));
         assertTrue(CellAttr.isReverse(attr));
      }

      @Test
      @DisplayName("reset clears all flags")
      void resetClearsFlags() {
         sgr.process(new int[]{1, 4, 7});
         sgr.process(new int[]{0});
         int attr = sgr.currentAttr();
         assertFalse(CellAttr.isBold(attr));
         assertFalse(CellAttr.isUnderline(attr));
         assertFalse(CellAttr.isReverse(attr));
      }
   }

   // ── Standard ANSI colors (30-37, 40-47) ──────────────────

   @Nested
   @DisplayName("standard ANSI colors")
   class StandardColors {

      @Test
      @DisplayName("SGR 30-37 set foreground colors 0-7")
      void standardFgRange() {
         for (int code = 30; code <= 37; code++) {
            sgr.process(new int[]{0}); // reset
            sgr.process(new int[]{code});
            assertEquals(code - 30, CellAttr.fgColor(
               sgr.currentAttr()),
               "SGR " + code + " should set fg=" + (code - 30));
         }
      }

      @Test
      @DisplayName("SGR 40-47 set background colors 0-7")
      void standardBgRange() {
         for (int code = 40; code <= 47; code++) {
            sgr.process(new int[]{0});
            sgr.process(new int[]{code});
            assertEquals(code - 40, CellAttr.bgColor(
               sgr.currentAttr()),
               "SGR " + code + " should set bg=" + (code - 40));
         }
      }

      @Test
      @DisplayName("SGR 31;42 sets red fg, green bg")
      void fgAndBgCombined() {
         sgr.process(new int[]{31, 42});
         int attr = sgr.currentAttr();
         assertEquals(1, CellAttr.fgColor(attr));
         assertEquals(2, CellAttr.bgColor(attr));
      }

      @Test
      @DisplayName("SGR 39 resets foreground to default")
      void resetFg() {
         sgr.process(new int[]{31});
         sgr.process(new int[]{39});
         assertEquals(-1, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("SGR 49 resets background to default")
      void resetBg() {
         sgr.process(new int[]{42});
         sgr.process(new int[]{49});
         assertEquals(-1, CellAttr.bgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("SGR 39;49 resets both fg and bg")
      void resetBoth() {
         sgr.process(new int[]{31, 42});
         sgr.process(new int[]{39, 49});
         int attr = sgr.currentAttr();
         assertEquals(-1, CellAttr.fgColor(attr));
         assertEquals(-1, CellAttr.bgColor(attr));
      }
   }

   // ── Bright/high-intensity colors (90-97, 100-107) ────────

   @Nested
   @DisplayName("bright ANSI colors")
   class BrightColors {

      @Test
      @DisplayName("SGR 90-97 set bright fg colors 8-15")
      void brightFgRange() {
         for (int code = 90; code <= 97; code++) {
            sgr.process(new int[]{0});
            sgr.process(new int[]{code});
            assertEquals(code - 90 + 8,
               CellAttr.fgColor(sgr.currentAttr()),
               "SGR " + code);
         }
      }

      @Test
      @DisplayName("SGR 100-107 set bright bg colors 8-15")
      void brightBgRange() {
         for (int code = 100; code <= 107; code++) {
            sgr.process(new int[]{0});
            sgr.process(new int[]{code});
            assertEquals(code - 100 + 8,
               CellAttr.bgColor(sgr.currentAttr()),
               "SGR " + code);
         }
      }

      @Test
      @DisplayName("SGR 91 sets bright red fg (color 9)")
      void brightRed() {
         sgr.process(new int[]{91});
         assertEquals(9, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("SGR 103 sets bright yellow bg (color 11)")
      void brightYellowBg() {
         sgr.process(new int[]{103});
         assertEquals(11, CellAttr.bgColor(sgr.currentAttr()));
      }
   }

   // ── 256-color palette (38;5;N, 48;5;N) ───────────────────

   @Nested
   @DisplayName("256-color palette")
   class Color256 {

      @Test
      @DisplayName("38;5;196 sets fg to red (196)")
      void fg256Red() {
         sgr.process(new int[]{38, 5, 196});
         assertEquals(196, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("48;5;21 sets bg to blue (21)")
      void bg256Blue() {
         sgr.process(new int[]{48, 5, 21});
         assertEquals(21, CellAttr.bgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("38;5;0 sets fg to black (0)")
      void fg256Black() {
         sgr.process(new int[]{38, 5, 0});
         assertEquals(0, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("38;5;254 sets fg to near-white grayscale")
      void fg256Grayscale254() {
         sgr.process(new int[]{38, 5, 254});
         assertEquals(254, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("38;5;255 is rejected (cannot store 255)")
      void fg256Boundary255Rejected() {
         sgr.process(new int[]{38, 5, 255});
         // Color 255 is > 254, so it should not be stored
         assertEquals(-1, CellAttr.fgColor(sgr.currentAttr()),
            "color 255 cannot be stored in 8-bit field");
      }

      @Test
      @DisplayName("combined 256-color fg and bg")
      void fgAndBg256() {
         sgr.process(new int[]{38, 5, 196, 48, 5, 21});
         int attr = sgr.currentAttr();
         assertEquals(196, CellAttr.fgColor(attr));
         assertEquals(21, CellAttr.bgColor(attr));
      }

      @Test
      @DisplayName("bold + 256-color fg preserved")
      void boldWith256Color() {
         sgr.process(new int[]{1, 38, 5, 82});
         int attr = sgr.currentAttr();
         assertTrue(CellAttr.isBold(attr));
         assertEquals(82, CellAttr.fgColor(attr));
      }

      @Test
      @DisplayName("256-color fg survives attribute changes")
      void fg256SurvivesUnderline() {
         sgr.process(new int[]{38, 5, 196});
         sgr.process(new int[]{4}); // underline
         int attr = sgr.currentAttr();
         assertEquals(196, CellAttr.fgColor(attr));
         assertTrue(CellAttr.isUnderline(attr));
      }

      @Test
      @DisplayName("SGR 39 resets 256-color fg to default")
      void resetDefaultFgAfter256() {
         sgr.process(new int[]{38, 5, 196});
         sgr.process(new int[]{39});
         assertEquals(-1, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("malformed 38;5 with too few params is no-op")
      void malformed256TooFewParams() {
         sgr.process(new int[]{38, 5}); // missing color index
         assertEquals(-1, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("malformed 38;3 (unknown sub-command)")
      void malformed38UnknownSub() {
         sgr.process(new int[]{38, 3, 100});
         // 38 without valid sub-command → no color change
         assertEquals(-1, CellAttr.fgColor(sgr.currentAttr()));
      }
   }

   // ── Truecolor approximation (38;2;R;G;B, 48;2;R;G;B) ────

   @Nested
   @DisplayName("truecolor approximation")
   class Truecolor {

      @Test
      @DisplayName("38;2;255;0;0 → nearest red (196)")
      void truecolorRed() {
         sgr.process(new int[]{38, 2, 255, 0, 0});
         assertEquals(196, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("48;2;0;0;255 → nearest blue (21)")
      void truecolorBlueBg() {
         sgr.process(new int[]{48, 2, 0, 0, 255});
         assertEquals(21, CellAttr.bgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("38;2;0;255;0 → nearest green (46)")
      void truecolorGreen() {
         sgr.process(new int[]{38, 2, 0, 255, 0});
         assertEquals(46, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("38;2;255;128;0 → nearest orange (214)")
      void truecolorOrange() {
         sgr.process(new int[]{38, 2, 255, 128, 0});
         assertEquals(214, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("truecolor fg + bg combined")
      void truecolorFgAndBg() {
         sgr.process(
            new int[]{38, 2, 255, 0, 0, 48, 2, 0, 0, 255});
         int attr = sgr.currentAttr();
         assertEquals(196, CellAttr.fgColor(attr));
         assertEquals(21, CellAttr.bgColor(attr));
      }

      @Test
      @DisplayName("malformed 38;2 with too few params")
      void malformedTruecolorTooFew() {
         sgr.process(new int[]{38, 2, 255, 128});
         // Only 4 params, needs 5 → no color change
         assertEquals(-1, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("38;2;128;128;128 → grayscale")
      void truecolorGray() {
         sgr.process(new int[]{38, 2, 128, 128, 128});
         int fg = CellAttr.fgColor(sgr.currentAttr());
         // Should map to a grayscale ramp or gray cube entry
         assertTrue(fg >= 0 && fg <= 254,
            "gray should map to valid palette index, got " + fg);
      }
   }

   // ── Cumulative/sequential SGR sequences ──────────────────

   @Nested
   @DisplayName("cumulative SGR sequences")
   class Cumulative {

      @Test
      @DisplayName("attributes accumulate across calls")
      void attributesAccumulate() {
         sgr.process(new int[]{1});   // bold
         sgr.process(new int[]{31});  // red fg
         sgr.process(new int[]{42});  // green bg
         int attr = sgr.currentAttr();
         assertTrue(CellAttr.isBold(attr));
         assertEquals(1, CellAttr.fgColor(attr));
         assertEquals(2, CellAttr.bgColor(attr));
      }

      @Test
      @DisplayName("new fg color replaces old fg color")
      void fgColorReplaced() {
         sgr.process(new int[]{31}); // red
         sgr.process(new int[]{34}); // blue
         assertEquals(4, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("new bg color replaces old bg color")
      void bgColorReplaced() {
         sgr.process(new int[]{41}); // red bg
         sgr.process(new int[]{44}); // blue bg
         assertEquals(4, CellAttr.bgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("reset mid-sequence clears earlier attrs")
      void resetMidSequence() {
         sgr.process(new int[]{1, 31, 0, 34});
         int attr = sgr.currentAttr();
         // bold was set, then reset by 0, so no bold
         assertFalse(CellAttr.isBold(attr));
         // fg was red, then reset by 0, then set to blue
         assertEquals(4, CellAttr.fgColor(attr));
      }

      @Test
      @DisplayName("256-color fg replaces standard fg")
      void fg256ReplacesStandard() {
         sgr.process(new int[]{31});           // red (1)
         sgr.process(new int[]{38, 5, 196});   // 256-red
         assertEquals(196, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("standard fg replaces 256-color fg")
      void standardFgReplaces256() {
         sgr.process(new int[]{38, 5, 196});
         sgr.process(new int[]{31});
         assertEquals(1, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("bright fg replaces 256-color fg")
      void brightFgReplaces256() {
         sgr.process(new int[]{38, 5, 196});
         sgr.process(new int[]{91}); // bright red
         assertEquals(9, CellAttr.fgColor(sgr.currentAttr()));
      }
   }

   // ── reset() method ───────────────────────────────────────

   @Test
   @DisplayName("reset() clears all state")
   void resetMethod() {
      sgr.process(new int[]{1, 4, 7, 31, 42});
      sgr.reset();
      assertEquals(CellAttr.DEFAULT, sgr.currentAttr());
   }

   // ── Edge cases ───────────────────────────────────────────

   @Nested
   @DisplayName("edge cases")
   class EdgeCases {

      @Test
      @DisplayName("unknown SGR param is ignored")
      void unknownParamIgnored() {
         sgr.process(new int[]{999});
         assertEquals(CellAttr.DEFAULT, sgr.currentAttr());
      }

      @Test
      @DisplayName("SGR 2 (dim) is ignored by state machine")
      void dimIgnored() {
         sgr.process(new int[]{2});
         // Dim is not stored — attr stays default
         assertEquals(CellAttr.DEFAULT, sgr.currentAttr());
      }

      @Test
      @DisplayName("SGR 3 (italic) is ignored by state machine")
      void italicIgnored() {
         sgr.process(new int[]{3});
         assertEquals(CellAttr.DEFAULT, sgr.currentAttr());
      }

      @Test
      @DisplayName("single-element params array works")
      void singleElement() {
         sgr.process(new int[]{1});
         assertTrue(CellAttr.isBold(sgr.currentAttr()));
      }

      @Test
      @DisplayName("empty params array is no-op")
      void emptyParams() {
         sgr.process(new int[]{});
         assertEquals(CellAttr.DEFAULT, sgr.currentAttr());
      }

      @Test
      @DisplayName("negative color index in 38;5;-1 is rejected")
      void negativeColorIndex() {
         sgr.process(new int[]{38, 5, -1});
         assertEquals(-1, CellAttr.fgColor(sgr.currentAttr()));
      }

      @Test
      @DisplayName("color 0 (black) is distinct from default")
      void color0IsBlack() {
         sgr.process(new int[]{30}); // standard black fg
         int attr = sgr.currentAttr();
         assertEquals(0, CellAttr.fgColor(attr));
         // Not DEFAULT — color 0 is explicitly set
         assertTrue(attr != CellAttr.DEFAULT,
            "fg=0 (black) should differ from DEFAULT");
      }

      @Test
      @DisplayName("htop-style sequence: bold + 256-color + bg")
      void htopStyle() {
         // Typical htop: ESC[1;38;5;46;48;5;232m
         sgr.process(new int[]{1, 38, 5, 46, 48, 5, 232});
         int attr = sgr.currentAttr();
         assertTrue(CellAttr.isBold(attr));
         assertEquals(46, CellAttr.fgColor(attr));
         assertEquals(232, CellAttr.bgColor(attr));
      }
   }
}
