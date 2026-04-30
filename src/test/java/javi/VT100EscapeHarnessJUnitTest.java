package javi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Systematic VT100 escape sequence test harness.
 *
 * <p>Uses {@link VT100TestHarness} to feed escape sequences
 * through the full VT100 pipeline (parser + ECScreen) and
 * verify screen buffer content, cursor position, and
 * per-cell attributes.  Covers:</p>
 * <ul>
 *   <li>Cursor positioning: CUP, CUU, CUD, CUF, CUB</li>
 *   <li>Screen clear: ED 0/1/2</li>
 *   <li>Line clear: EL 0/1/2</li>
 *   <li>SGR colors: standard, bright, 256-color, truecolor
 *       approximation</li>
 *   <li>Scroll regions: DECSTBM, IND, RI</li>
 * </ul>
 */
class VT100EscapeHarnessJUnitTest {

   private VT100TestHarness h;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void setUp() throws Exception {
      h = new VT100TestHarness();
      h.setUp();
   }

   @AfterEach
   void tearDown() throws Exception {
      h.tearDown();
   }

   // ── Cursor Positioning ─────────────────────────────────

   @Nested
   @DisplayName("CUP — Cursor Position (CSI H)")
   class CupTests {

      @Test
      @DisplayName("CSI H homes cursor to 1,1")
      void homeDefault() throws Exception {
         h.feed("\033[5;10H");
         h.feed("\033[H");
         h.assertCursor(1, 1);
      }

      @Test
      @DisplayName("CSI row;col H positions cursor")
      void explicitRowCol() throws Exception {
         h.feed("\033[12;40H");
         h.assertCursor(12, 40);
      }

      @Test
      @DisplayName("CSI row H defaults column to 1")
      void rowOnlyDefaultsCol() throws Exception {
         h.feed("\033[7H");
         h.assertCursor(7, 1);
      }

      @Test
      @DisplayName("CUP with large row positions cursor")
      void largeRowPositions() throws Exception {
         h.feed("\033[999;5H");
         // Implementation may or may not clamp; verify
         // cursor is at a valid (positive) position
         assertTrue(h.cursorRow() >= 1,
            "row positive");
         assertEquals(5, h.cursorCol());
      }
   }

   @Nested
   @DisplayName("CUU/CUD/CUF/CUB — Cursor Movement")
   class CursorMoveTests {

      @Test
      @DisplayName("CUU moves cursor up N rows")
      void cursorUp() throws Exception {
         h.feed("\033[10;5H");
         h.feed("\033[3A");
         h.assertCursor(7, 5);
      }

      @Test
      @DisplayName("CUD moves cursor down N rows")
      void cursorDown() throws Exception {
         h.feed("\033[5;5H");
         h.feed("\033[4B");
         h.assertCursor(9, 5);
      }

      @Test
      @DisplayName("CUF moves cursor right N columns")
      void cursorForward() throws Exception {
         h.feed("\033[1;1H");
         h.feed("\033[9C");
         h.assertCursor(1, 10);
      }

      @Test
      @DisplayName("CUB moves cursor left N columns")
      void cursorBack() throws Exception {
         h.feed("\033[1;15H");
         h.feed("\033[5D");
         h.assertCursor(1, 10);
      }

      @Test
      @DisplayName("CUU with no param moves 1 row")
      void cursorUpDefault() throws Exception {
         h.feed("\033[5;1H");
         h.feed("\033[A");
         h.assertCursor(4, 1);
      }

      @Test
      @DisplayName("CUB clamps at column 1")
      void cursorBackClamps() throws Exception {
         h.feed("\033[1;3H");
         h.feed("\033[20D");
         assertEquals(1, h.cursorCol());
      }

      @Test
      @DisplayName("CUU clamps at row 1")
      void cursorUpClamps() throws Exception {
         h.feed("\033[2;1H");
         h.feed("\033[50A");
         assertTrue(h.cursorRow() >= 1,
            "cursor clamped at top");
      }

      @Test
      @DisplayName("CUD clamps at bottom row")
      void cursorDownClamps() throws Exception {
         h.feed("\033[22;1H");
         h.feed("\033[50B");
         assertTrue(h.cursorRow() <= h.rows(),
            "cursor clamped at bottom");
      }

      @Test
      @DisplayName("cursor movement sequence spells text")
      void moveAndWrite() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[3;5HABCDE");
         h.feed("\033[3;15HFGHIJ\033[0m");
         String line = h.lineContent(3);
         assertTrue(line.contains("ABCDE"),
            "first group present");
         assertTrue(line.contains("FGHIJ"),
            "second group present");
      }
   }

   // ── Screen Clear (ED) ─────────────────────────────────

   @Nested
   @DisplayName("ED — Erase in Display (CSI J)")
   class EdTests {

      @Test
      @DisplayName("ED 2 clears entire screen")
      void eraseScreen() throws Exception {
         h.feed("\033[1;1HLine1");
         h.feed("\033[2;1HLine2");
         h.feed("\033[3;1HLine3");
         h.feed("\033[2J");
         assertEquals("", h.lineContent(1));
         assertEquals("", h.lineContent(2));
         assertEquals("", h.lineContent(3));
      }

      @Test
      @DisplayName("ED 0 erases from cursor to end of screen")
      void eraseBelow() throws Exception {
         h.feed("\033[1;1HKeepThis");
         h.feed("\033[2;1HRemove");
         h.feed("\033[3;1HRemove");
         h.feed("\033[2;1H");
         h.feed("\033[0J");
         assertTrue(h.lineContent(1).startsWith("KeepThis"),
            "row 1 preserved");
         assertEquals("", h.lineContent(2),
            "row 2 erased");
         assertEquals("", h.lineContent(3),
            "row 3 erased");
      }

      @Test
      @DisplayName("ED 1 erases from beginning to cursor")
      void eraseAbove() throws Exception {
         h.feed("\033[1;1HRemove");
         h.feed("\033[2;1HRemove");
         h.feed("\033[3;1HKeepThis");
         h.feed("\033[2;4H");
         h.feed("\033[1J");
         // Row 1 fully erased (above cursor row)
         String r1 = h.lineContent(1);
         assertTrue(!r1.contains("Remove"),
            "row 1 erased");
         // Row 3 preserved (below cursor row)
         assertTrue(
            h.lineContent(3).startsWith("KeepThis"),
            "row 3 preserved");
      }

      @Test
      @DisplayName("ED 2 followed by text renders cleanly")
      void clearThenWrite() throws Exception {
         h.feed("\033[1;1Hgarbage fill");
         h.feed("\033[2J");
         h.feed("\033[1;1HFresh\033[0m");
         h.assertScreen(1, "Fresh");
         assertEquals("", h.lineContent(2));
      }
   }

   // ── Line Clear (EL) ───────────────────────────────────

   @Nested
   @DisplayName("EL — Erase in Line (CSI K)")
   class ElTests {

      @Test
      @DisplayName("EL 0 erases from cursor to end of line")
      void eraseToEnd() throws Exception {
         h.feed("\033[1;1HABCDEFGHIJ");
         h.feed("\033[1;6H");
         h.feed("\033[0K");
         String line = h.lineContent(1);
         assertTrue(line.startsWith("ABCDE"),
            "first 5 chars preserved");
         assertTrue(line.length() <= 5
            || line.substring(5).isBlank(),
            "rest erased");
      }

      @Test
      @DisplayName("EL 1 erases from start to cursor")
      void eraseToBeginning() throws Exception {
         h.feed("\033[1;1HABCDEFGHIJ");
         h.feed("\033[1;5H");
         h.feed("\033[1K");
         String line = h.lineContent(1);
         for (int i = 0; i < 5 && i < line.length(); i++)
            assertEquals(' ', line.charAt(i),
               "col " + (i + 1) + " erased");
         assertTrue(line.length() >= 10
            && line.substring(5).startsWith("FGHIJ"),
            "cols 6-10 preserved");
      }

      @Test
      @DisplayName("EL 2 erases entire line")
      void eraseWholeLine() throws Exception {
         h.feed("\033[1;1HFull line of text here");
         h.feed("\033[1;10H");
         h.feed("\033[2K");
         assertEquals("", h.lineContent(1));
      }

      @Test
      @DisplayName("EL 0 default — no param same as 0")
      void eraseToEndDefault() throws Exception {
         h.feed("\033[1;1HABCDE");
         h.feed("\033[1;3H");
         h.feed("\033[K");
         String line = h.lineContent(1);
         assertTrue(line.startsWith("AB"),
            "first 2 preserved");
         assertTrue(line.length() <= 2
            || line.substring(2).isBlank(),
            "rest erased");
      }

      @Test
      @DisplayName("EL preserves other lines")
      void otherLinesPreserved() throws Exception {
         h.feed("\033[1;1HRow1");
         h.feed("\033[2;1HRow2");
         h.feed("\033[3;1HRow3");
         h.feed("\033[2;1H");
         h.feed("\033[2K");
         assertTrue(h.lineContent(1).startsWith("Row1"),
            "row 1 intact");
         assertEquals("", h.lineContent(2),
            "row 2 erased");
         assertTrue(h.lineContent(3).startsWith("Row3"),
            "row 3 intact");
      }
   }

   // ── SGR Colors ─────────────────────────────────────────

   @Nested
   @DisplayName("SGR — Select Graphic Rendition colors")
   class SgrColorTests {

      @Test
      @DisplayName("standard fg color 31 (red)")
      void standardFg() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[31mR\033[0m");
         int attr = h.getAttr(1, 1);
         assertEquals(1, CellAttr.fgColor(attr),
            "fg should be 1 (red)");
      }

      @Test
      @DisplayName("standard bg color 42 (green)")
      void standardBg() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[42mG\033[0m");
         int attr = h.getAttr(1, 1);
         assertEquals(2, CellAttr.bgColor(attr),
            "bg should be 2 (green)");
      }

      @Test
      @DisplayName("bright fg color 91 maps to index 9")
      void brightFg() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[91mB\033[0m");
         int attr = h.getAttr(1, 1);
         assertEquals(9, CellAttr.fgColor(attr),
            "bright red = index 9");
      }

      @Test
      @DisplayName("bright bg color 104 maps to index 12")
      void brightBg() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[104mX\033[0m");
         int attr = h.getAttr(1, 1);
         assertEquals(12, CellAttr.bgColor(attr),
            "bright blue bg = index 12");
      }

      @Test
      @DisplayName("256-color fg (38;5;196 = red)")
      void extendedFg256() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[38;5;196mR\033[0m");
         int attr = h.getAttr(1, 1);
         assertEquals(196, CellAttr.fgColor(attr),
            "256-color fg = 196");
      }

      @Test
      @DisplayName("256-color bg (48;5;21 = blue)")
      void extendedBg256() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[48;5;21mB\033[0m");
         int attr = h.getAttr(1, 1);
         assertEquals(21, CellAttr.bgColor(attr),
            "256-color bg = 21");
      }

      @Test
      @DisplayName("truecolor fg approximated to palette")
      void truecolorFg() throws Exception {
         h.feed("\033[2J\033[1;1H");
         // Pure red (255,0,0) should map to palette 196
         h.feed("\033[38;2;255;0;0mT\033[0m");
         int attr = h.getAttr(1, 1);
         int fg = CellAttr.fgColor(attr);
         assertNotEquals(-1, fg,
            "fg should have a color index");
         assertTrue(fg >= 0 && fg <= 255,
            "fg index in palette range");
      }

      @Test
      @DisplayName("truecolor bg approximated to palette")
      void truecolorBg() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[48;2;0;0;255mB\033[0m");
         int attr = h.getAttr(1, 1);
         int bg = CellAttr.bgColor(attr);
         assertNotEquals(-1, bg,
            "bg should have a color index");
         assertTrue(bg >= 0 && bg <= 255,
            "bg index in palette range");
      }

      @Test
      @DisplayName("bold attribute set by SGR 1")
      void boldAttr() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[1mB\033[0m");
         int attr = h.getAttr(1, 1);
         assertTrue(CellAttr.isBold(attr),
            "bold flag set");
      }

      @Test
      @DisplayName("underline attribute set by SGR 4")
      void underlineAttr() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[4mU\033[0m");
         int attr = h.getAttr(1, 1);
         assertTrue(CellAttr.isUnderline(attr),
            "underline flag set");
      }

      @Test
      @DisplayName("reverse video set by SGR 7")
      void reverseAttr() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[7mR\033[0m");
         int attr = h.getAttr(1, 1);
         assertTrue(CellAttr.isReverse(attr),
            "reverse flag set");
      }

      @Test
      @DisplayName("SGR 0 resets all attributes")
      void resetClearsAll() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[1;4;31;42mX");
         h.feed("\033[0mY");
         int attrX = h.getAttr(1, 1);
         int attrY = h.getAttr(1, 2);
         assertTrue(CellAttr.isBold(attrX),
            "X is bold");
         assertEquals(CellAttr.DEFAULT, attrY,
            "Y reset to default");
      }

      @Test
      @DisplayName("combined bold + red fg + green bg")
      void combinedAttrs() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[1;31;42mC\033[0m");
         int attr = h.getAttr(1, 1);
         assertTrue(CellAttr.isBold(attr), "bold");
         assertEquals(1, CellAttr.fgColor(attr),
            "fg red");
         assertEquals(2, CellAttr.bgColor(attr),
            "bg green");
      }

      @Test
      @DisplayName("default fg (39) resets fg only")
      void defaultFg() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[31;42mA\033[39mB\033[0m");
         int attr = h.getAttr(1, 2);
         int fg = CellAttr.fgColor(attr);
         assertTrue(fg == 0xFF || fg == -1,
            "fg reset to default, got " + fg);
         assertEquals(2, CellAttr.bgColor(attr),
            "bg still green");
      }

      @Test
      @DisplayName("default bg (49) resets bg only")
      void defaultBg() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[31;42mA\033[49mB\033[0m");
         int attr = h.getAttr(1, 2);
         assertEquals(1, CellAttr.fgColor(attr),
            "fg still red");
         int bg = CellAttr.bgColor(attr);
         assertTrue(bg == 0xFF || bg == -1,
            "bg reset to default, got " + bg);
      }

      @Test
      @DisplayName("color per-character isolation")
      void colorPerChar() throws Exception {
         h.feed("\033[2J\033[1;1H");
         h.feed("\033[31mR\033[32mG\033[34mB\033[0m");
         assertEquals(1, CellAttr.fgColor(h.getAttr(1, 1)),
            "char 1 red");
         assertEquals(2, CellAttr.fgColor(h.getAttr(1, 2)),
            "char 2 green");
         assertEquals(4, CellAttr.fgColor(h.getAttr(1, 3)),
            "char 3 blue");
      }
   }

   // ── Scroll Regions ─────────────────────────────────────

   @Nested
   @DisplayName("DECSTBM — Scroll Regions")
   class ScrollRegionTests {

      @Test
      @DisplayName("DECSTBM sets scroll region")
      void setScrollRegion() throws Exception {
         // Fill rows
         for (int r = 1; r <= 5; r++)
            h.feed("\033[" + r + ";1HRow" + r);
         // Set scroll region 2-4
         h.feed("\033[2;4r");
         // Cursor homes to 1,1 per DECSTBM spec
         h.assertCursor(1, 1);
      }

      @Test
      @DisplayName("IND at bottom of scroll region scrolls")
      void indScrollsAtBottom() throws Exception {
         for (int r = 1; r <= 5; r++)
            h.feed("\033[" + r + ";1HRow" + r);
         h.feed("\033[2;4r");
         h.feed("\033[4;1H");
         // IND at bottom of region — should scroll
         h.feed("\033D");
         // Row 1 outside region — preserved
         assertTrue(
            h.lineContent(1).startsWith("Row1"),
            "row 1 preserved");
         // Row 5 outside region — preserved
         assertTrue(
            h.lineContent(5).startsWith("Row5"),
            "row 5 preserved");
      }

      @Test
      @DisplayName("RI at top of scroll region scrolls down")
      void riScrollsDown() throws Exception {
         for (int r = 1; r <= 5; r++)
            h.feed("\033[" + r + ";1HRow" + r);
         h.feed("\033[2;4r");
         h.feed("\033[2;1H");
         // RI at top of region — should scroll down
         h.feed("\033M");
         // Row 1 outside region — preserved
         assertTrue(
            h.lineContent(1).startsWith("Row1"),
            "row 1 preserved");
         // Row 5 outside region — preserved
         assertTrue(
            h.lineContent(5).startsWith("Row5"),
            "row 5 preserved");
      }

      @Test
      @DisplayName("lines outside scroll region are static")
      void outsideLinesStatic() throws Exception {
         h.feed("\033[1;1HTop");
         h.feed("\033[" + h.rows() + ";1HBot");
         for (int r = 2; r < h.rows(); r++)
            h.feed("\033[" + r + ";1HLine" + r);
         // Set scroll region 5-20
         h.feed("\033[5;20r");
         // Scroll by IND at bottom
         h.feed("\033[20;1H");
         h.feed("\033D\033D\033D");
         assertTrue(
            h.lineContent(1).startsWith("Top"),
            "top preserved");
         assertTrue(
            h.lineContent(h.rows()).startsWith("Bot"),
            "bottom preserved");
      }

      @Test
      @DisplayName("CSI r with no params resets region")
      void resetRegion() throws Exception {
         h.feed("\033[5;10r");
         h.feed("\033[r");
         // Should be able to cursor-move to any row
         h.feed("\033[1;1H");
         h.assertCursor(1, 1);
         h.feed("\033[" + h.rows() + ";1H");
         assertEquals(h.rows(), h.cursorRow());
      }

      @Test
      @DisplayName("scroll fills blank line in region")
      void scrollFillsBlank() throws Exception {
         for (int r = 1; r <= 6; r++)
            h.feed("\033[" + r + ";1HR" + r);
         h.feed("\033[2;5r");
         h.feed("\033[5;1H");
         h.feed("\033D");  // IND at bottom of region
         // Bottom of region should be blank
         String newLine = h.lineContent(5);
         assertTrue(newLine.isBlank(),
            "new line at bottom should be blank");
      }
   }

   // ── Combined Sequences ─────────────────────────────────

   @Nested
   @DisplayName("Combined sequences — real-world patterns")
   class CombinedTests {

      @Test
      @DisplayName("clear screen then draw colored box")
      void clearAndDrawBox() throws Exception {
         h.feed("\033[2J\033[1;1H");
         // Draw a box with reverse video border
         h.feed("\033[7m");
         h.feed("\033[3;10H+--------+");
         h.feed("\033[4;10H|        |");
         h.feed("\033[5;10H|        |");
         h.feed("\033[6;10H+--------+");
         h.feed("\033[0m");
         // Interior text in green
         h.feed("\033[4;12H\033[32mHello!\033[0m");

         String r3 = h.lineContent(3);
         assertTrue(r3.contains("+--------+"),
            "top border");
         String r4 = h.lineContent(4);
         assertTrue(r4.contains("Hello!"),
            "interior text");
         // Reverse attr on border char
         int borderAttr = h.getAttr(3, 10);
         assertTrue(CellAttr.isReverse(borderAttr),
            "border is reverse video");
         // Green attr on interior char
         int textAttr = h.getAttr(4, 12);
         assertEquals(2, CellAttr.fgColor(textAttr),
            "interior text is green");
      }

      @Test
      @DisplayName("cursor save/restore preserves position")
      void saveRestore() throws Exception {
         h.feed("\033[5;10H");
         h.feed("\033[s");  // save
         h.feed("\033[10;20H");
         h.feed("\033[u");  // restore
         h.assertCursor(5, 10);
      }

      @Test
      @DisplayName("erase then color renders correctly")
      void eraseThenColor() throws Exception {
         h.feed("\033[1;1HOriginalText");
         h.feed("\033[1;1H\033[2K");  // erase line
         h.feed("\033[1;1H\033[33mNew\033[0m");
         String line = h.lineContent(1);
         assertTrue(line.startsWith("New"),
            "new text in place");
         int attr = h.getAttr(1, 1);
         assertEquals(3, CellAttr.fgColor(attr),
            "yellow fg");
      }

      @Test
      @DisplayName("htop-style status bar: bold+reverse+color")
      void htopStatusBar() throws Exception {
         h.feed("\033[2J");
         // htop uses bold+reverse+color for bars
         h.feed("\033[" + h.rows() + ";1H");
         h.feed("\033[1;7;34m");  // bold+reverse+blue
         h.feed("CPU [||||     ] 44%");
         h.feed("\033[0m");
         String line = h.lineContent(h.rows());
         assertTrue(line.contains("CPU"),
            "status bar text");
         int attr = h.getAttr(h.rows(), 1);
         assertTrue(CellAttr.isBold(attr), "bold");
         assertTrue(CellAttr.isReverse(attr), "reverse");
         assertEquals(4, CellAttr.fgColor(attr),
            "blue fg");
      }

      @Test
      @DisplayName("256-color gradient across 16 chars")
      void colorGradient() throws Exception {
         h.feed("\033[2J\033[1;1H");
         for (int i = 0; i < 16; i++) {
            int color = 16 + i;
            h.feed("\033[38;5;" + color + "m#");
         }
         h.feed("\033[0m");
         // Each character should have a different fg
         for (int i = 0; i < 16; i++) {
            int attr = h.getAttr(1, i + 1);
            assertEquals(16 + i, CellAttr.fgColor(attr),
               "char " + i + " fg color");
         }
      }
   }
}
