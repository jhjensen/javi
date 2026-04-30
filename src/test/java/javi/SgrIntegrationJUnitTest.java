package javi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * End-to-end integration tests for the SGR color pipeline.
 *
 * <p>Verifies the full path: VT100 escape sequences fed through
 * {@link Vt100Parser} → {@link Vt100.ECScreen} processing →
 * {@link ScreenAttributes} storage.  Unlike {@link SgrStateJUnitTest}
 * (which tests the state machine in isolation) and
 * {@link Vt100ParserJUnitTest} (which uses a recording stub),
 * these tests exercise the real ECScreen and verify that per-cell
 * attributes appear in the screen attribute grid.</p>
 *
 * <p>The biglock2 lock is held during the entire test lifecycle
 * (setUp through tearDown) because the Vt100/EditContainer
 * constructor and all ECScreen insert/delete operations call
 * {@code assertOwned()} on biglock2.</p>
 */
class SgrIntegrationJUnitTest {

   private static int instanceCounter;

   private Vt100 vt100;
   private Vt100Parser parser;
   private Method doChar;
   private PipedOutputStream pipeOut;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void setUp() throws Exception {
      // Acquire biglock2 BEFORE constructing Vt100 — the
      // EditContainer constructor calls registeruniq() which
      // calls assertOwned().  Held until tearDown().
      EventQueue.biglock2.lock();

      pipeOut = new PipedOutputStream();
      PipedInputStream pipeIn = new PipedInputStream(pipeOut);
      BufferedInputStream bis = new BufferedInputStream(pipeIn);
      // Null output stream — we don't send responses in tests
      OutputStream nullOut = OutputStream.nullOutputStream();
      vt100 = new Vt100(nullOut, bis,
         new StringIoc("sgr-integ-" + instanceCounter++, null),
         StandardCharsets.UTF_8);

      // Access the parser via reflection
      Field parserField = Vt100.class.getDeclaredField("parser");
      parserField.setAccessible(true);
      parser = (Vt100Parser) parserField.get(vt100);

      doChar = Vt100Parser.class.getDeclaredMethod("doChar",
         char.class);
      doChar.setAccessible(true);
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         parser.stop();
         pipeOut.close();
         Field rtField =
            Vt100Parser.class.getDeclaredField("rthread");
         rtField.setAccessible(true);
         Thread rt = (Thread) rtField.get(parser);
         rt.join(2000);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Feeds a string character-by-character through the parser.
    * biglock2 is already held (acquired in setUp).
    */
   private void feed(String chars) throws Exception {
      for (int i = 0; i < chars.length(); i++)
         doChar.invoke(parser, chars.charAt(i));
   }

   /** Shorthand for getting the screen attribute grid. */
   private ScreenAttributes attrs() {
      return vt100.getScreenAttributes();
   }

   /**
    * Returns the line number at which the cursor sits.
    * The vtcursor field is private — read via reflection.
    */
   private int cursorLine() throws Exception {
      Field vtcurField = Vt100.class.getDeclaredField("vtcursor");
      vtcurField.setAccessible(true);
      MovePos cursor = (MovePos) vtcurField.get(vt100);
      return cursor.y;
   }

   // ── Basic ANSI colors ────────────────────────────────────

   @Nested
   @DisplayName("standard ANSI foreground colors")
   class AnsiForeground {

      @Test
      @DisplayName("red foreground (SGR 31) applied to text")
      void redForeground() throws Exception {
         // ESC[31m sets red fg, "Hi" is written, ESC[0m resets
         // The reset flushes the pending "Hi" with the red attr
         feed("\033[31mHi\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         assertEquals(1, CellAttr.fgColor(attr),
            "fg should be ANSI red (1)");
         assertFalse(CellAttr.isBold(attr));
      }

      @Test
      @DisplayName("green foreground (SGR 32)")
      void greenForeground() throws Exception {
         feed("\033[32mAB\033[0m");
         int line = cursorLine();
         assertEquals(2, CellAttr.fgColor(attrs().getAttr(line, 0)),
            "fg should be ANSI green (2)");
         assertEquals(2, CellAttr.fgColor(attrs().getAttr(line, 1)),
            "second char should also be green");
      }

      @Test
      @DisplayName("all 8 standard colors produce distinct values")
      void allStandardColors() throws Exception {
         // Write one char per color: ESC[30mA ESC[31mB ... ESC[37mH
         for (int c = 0; c < 8; c++) {
            feed("\033[" + (30 + c) + "m" + (char) ('A' + c));
         }
         feed("\033[0m"); // flush last char
         int line = cursorLine();
         for (int c = 0; c < 8; c++) {
            int attr = attrs().getAttr(line, c);
            assertEquals(c, CellAttr.fgColor(attr),
               "char " + c + " should have ANSI fg color " + c);
         }
      }
   }

   // ── Background colors ────────────────────────────────────

   @Nested
   @DisplayName("standard ANSI background colors")
   class AnsiBackground {

      @Test
      @DisplayName("blue background (SGR 44)")
      void blueBackground() throws Exception {
         feed("\033[44mX\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         assertEquals(4, CellAttr.bgColor(attr),
            "bg should be ANSI blue (4)");
      }

      @Test
      @DisplayName("combined fg + bg (SGR 31;42)")
      void combinedFgBg() throws Exception {
         feed("\033[31;42mXY\033[0m");
         int line = cursorLine();
         int attr0 = attrs().getAttr(line, 0);
         assertEquals(1, CellAttr.fgColor(attr0),
            "fg should be ANSI red (1)");
         assertEquals(2, CellAttr.bgColor(attr0),
            "bg should be ANSI green (2)");
      }
   }

   // ── Bright colors ────────────────────────────────────────

   @Nested
   @DisplayName("bright/high-intensity colors")
   class BrightColors {

      @Test
      @DisplayName("bright red foreground (SGR 91)")
      void brightRed() throws Exception {
         feed("\033[91mZ\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         assertEquals(9, CellAttr.fgColor(attr),
            "bright red should be palette index 9");
      }

      @Test
      @DisplayName("bright cyan background (SGR 106)")
      void brightCyanBg() throws Exception {
         feed("\033[106mW\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         assertEquals(14, CellAttr.bgColor(attr),
            "bright cyan bg should be palette index 14");
      }
   }

   // ── Text attributes ──────────────────────────────────────

   @Nested
   @DisplayName("text attributes (bold, underline, reverse)")
   class TextAttributes {

      @Test
      @DisplayName("bold + color")
      void boldAndColor() throws Exception {
         feed("\033[1;33mB\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         assertTrue(CellAttr.isBold(attr), "should be bold");
         assertEquals(3, CellAttr.fgColor(attr),
            "fg should be ANSI yellow (3)");
      }

      @Test
      @DisplayName("underline persists across chars")
      void underline() throws Exception {
         feed("\033[4mABC\033[0m");
         int line = cursorLine();
         for (int i = 0; i < 3; i++) {
            assertTrue(CellAttr.isUnderline(attrs().getAttr(line, i)),
               "char " + i + " should have underline");
         }
      }

      @Test
      @DisplayName("reverse video")
      void reverseVideo() throws Exception {
         feed("\033[7mR\033[0m");
         int line = cursorLine();
         assertTrue(CellAttr.isReverse(attrs().getAttr(line, 0)));
      }
   }

   // ── 256-color palette ────────────────────────────────────

   @Nested
   @DisplayName("256-color palette (SGR 38;5;N / 48;5;N)")
   class Color256 {

      @Test
      @DisplayName("256-color foreground index 196 (bright red)")
      void fg256() throws Exception {
         feed("\033[38;5;196mP\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         assertEquals(196, CellAttr.fgColor(attr),
            "fg should be palette index 196");
      }

      @Test
      @DisplayName("256-color background index 22 (dark green)")
      void bg256() throws Exception {
         feed("\033[48;5;22mQ\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         assertEquals(22, CellAttr.bgColor(attr),
            "bg should be palette index 22");
      }

      @Test
      @DisplayName("256-color fg + bg combined")
      void fg256AndBg256() throws Exception {
         feed("\033[38;5;51;48;5;208mC\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         assertEquals(51, CellAttr.fgColor(attr),
            "fg should be 51 (cyan)");
         assertEquals(208, CellAttr.bgColor(attr),
            "bg should be 208 (orange)");
      }

      @Test
      @DisplayName("palette boundary: index 0 (black)")
      void paletteIndex0() throws Exception {
         feed("\033[38;5;0mK\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         assertEquals(0, CellAttr.fgColor(attr),
            "fg should be palette black (0)");
      }

      @Test
      @DisplayName("palette boundary: index 254 (near-white gray)")
      void paletteIndex254() throws Exception {
         feed("\033[38;5;254mG\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         assertEquals(254, CellAttr.fgColor(attr),
            "fg should be palette index 254");
      }
   }

   // ── Truecolor approximation ──────────────────────────────

   @Nested
   @DisplayName("truecolor (SGR 38;2;R;G;B) → nearest palette")
   class Truecolor {

      @Test
      @DisplayName("pure red truecolor → nearest palette index")
      void truecolorRed() throws Exception {
         feed("\033[38;2;255;0;0mT\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         int expected = CellAttr.approxTrueColor(255, 0, 0);
         assertEquals(expected, CellAttr.fgColor(attr),
            "truecolor red should map to palette index "
            + expected);
      }

      @Test
      @DisplayName("truecolor bg (SGR 48;2;R;G;B)")
      void truecolorBg() throws Exception {
         feed("\033[48;2;0;128;0mU\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         int expected = CellAttr.approxTrueColor(0, 128, 0);
         assertEquals(expected, CellAttr.bgColor(attr),
            "truecolor green bg should map to palette index "
            + expected);
      }

      @Test
      @DisplayName("truecolor fg + standard bg")
      void fgTruecolorBgStandard() throws Exception {
         feed("\033[38;2;100;200;255;44mM\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         int expectedFg = CellAttr.approxTrueColor(100, 200, 255);
         assertEquals(expectedFg, CellAttr.fgColor(attr),
            "truecolor fg");
         assertEquals(4, CellAttr.bgColor(attr),
            "standard blue bg");
      }
   }

   // ── Reset and default ────────────────────────────────────

   @Nested
   @DisplayName("reset and default color handling")
   class ResetAndDefault {

      @Test
      @DisplayName("SGR 0 resets to DEFAULT for subsequent text")
      void resetClearsAttributes() throws Exception {
         feed("\033[1;31mA\033[0mB\033[0m");
         int line = cursorLine();
         int attrA = attrs().getAttr(line, 0);
         int attrB = attrs().getAttr(line, 1);
         assertTrue(CellAttr.isBold(attrA), "A should be bold");
         assertEquals(1, CellAttr.fgColor(attrA),
            "A should be red");
         assertEquals(CellAttr.DEFAULT, attrB,
            "B should be DEFAULT after reset");
      }

      @Test
      @DisplayName("SGR 39 resets fg only, bg preserved")
      void resetFgOnly() throws Exception {
         feed("\033[31;42mA\033[39mB\033[0m");
         int line = cursorLine();
         int attrA = attrs().getAttr(line, 0);
         assertEquals(1, CellAttr.fgColor(attrA), "A has red fg");
         assertEquals(2, CellAttr.bgColor(attrA), "A has green bg");
         int attrB = attrs().getAttr(line, 1);
         assertEquals(-1, CellAttr.fgColor(attrB),
            "B fg should be default after SGR 39");
         assertEquals(2, CellAttr.bgColor(attrB),
            "B should keep green bg");
      }

      @Test
      @DisplayName("SGR 49 resets bg only, fg preserved")
      void resetBgOnly() throws Exception {
         feed("\033[31;42mA\033[49mB\033[0m");
         int line = cursorLine();
         int attrB = attrs().getAttr(line, 1);
         assertEquals(1, CellAttr.fgColor(attrB),
            "B should keep red fg");
         assertEquals(-1, CellAttr.bgColor(attrB),
            "B bg should be default after SGR 49");
      }
   }

   // ── Multi-line sequences ─────────────────────────────────

   @Nested
   @DisplayName("multi-line color sequences")
   class MultiLine {

      @Test
      @DisplayName("color persists across newline")
      void colorPersistsAcrossNewline() throws Exception {
         // Set color, write on line 1, CR+LF, write on line 2
         // (real PTY output converts \n to \r\n via onlcr)
         feed("\033[36m");    // cyan fg
         feed("L1\r\nL2");
         feed("\033[0m");     // flush "L2"

         // Line 1 text was "L1" followed by newline.
         // The cursor is now on the next line after "L2".
         int line2 = cursorLine();
         int line1 = line2 - 1;

         // Both lines should have cyan fg
         assertEquals(6, CellAttr.fgColor(attrs().getAttr(line1, 0)),
            "line 1 char should be cyan (6)");
         assertEquals(6, CellAttr.fgColor(attrs().getAttr(line2, 0)),
            "line 2 char should be cyan (6)");
      }
   }

   // ── htop-style combined sequences ────────────────────────

   @Nested
   @DisplayName("htop-style multi-param sequences")
   class HtopStyle {

      @Test
      @DisplayName("combined bold+fg+bg in single sequence")
      void combinedBoldFgBg() throws Exception {
         // ESC[1;37;44m — bold + white fg + blue bg
         feed("\033[1;37;44mH\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         assertTrue(CellAttr.isBold(attr), "bold");
         assertEquals(7, CellAttr.fgColor(attr), "white fg (7)");
         assertEquals(4, CellAttr.bgColor(attr), "blue bg (4)");
      }

      @Test
      @DisplayName("multiple SGR sequences accumulate state")
      void sequentialAccumulation() throws Exception {
         // Bold in one sequence, color in another
         feed("\033[1m\033[31mXYZ\033[0m");
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         assertTrue(CellAttr.isBold(attr),
            "bold from first sequence");
         assertEquals(1, CellAttr.fgColor(attr),
            "red from second sequence");
         // All three chars should match
         assertEquals(attr, attrs().getAttr(line, 1));
         assertEquals(attr, attrs().getAttr(line, 2));
      }

      @Test
      @DisplayName("mid-line color change")
      void midLineColorChange() throws Exception {
         feed("\033[31mAA\033[32mBB\033[0m");
         int line = cursorLine();
         assertEquals(1, CellAttr.fgColor(attrs().getAttr(line, 0)),
            "first char red");
         assertEquals(1, CellAttr.fgColor(attrs().getAttr(line, 1)),
            "second char red");
         assertEquals(2, CellAttr.fgColor(attrs().getAttr(line, 2)),
            "third char green");
         assertEquals(2, CellAttr.fgColor(attrs().getAttr(line, 3)),
            "fourth char green");
      }
   }

   // ── Erase with background color ──────────────────────────

   @Nested
   @DisplayName("erase operations with active background")
   class EraseWithBackground {

      @Test
      @DisplayName("eraseLine preserves current bg (BCE)")
      void eraseLineWithBg() throws Exception {
         // Set bg, write text, then erase line
         feed("\033[44m");      // blue bg
         feed("text");
         feed("\033[2K");       // erase entire line
         // After erase, the line attributes should show blue bg
         int line = cursorLine();
         int attr = attrs().getAttr(line, 0);
         assertEquals(4, CellAttr.bgColor(attr),
            "erased cell should have blue bg (BCE)");
      }
   }
}
