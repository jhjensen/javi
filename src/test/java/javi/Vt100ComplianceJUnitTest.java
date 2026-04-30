package javi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * VT100 compliance tests targeting sequences used by vttest test 1.
 *
 * <p>Tests the full pipeline: escape sequences fed through
 * {@link Vt100Parser} into the real {@link Vt100.ECScreen},
 * verifying cursor position and screen content. Covers:
 * DECSTBM, DECOM, IND, RI, NEL, DECALN, scroll region
 * operations, and cursor movement within margins.</p>
 */
class Vt100ComplianceJUnitTest {

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
      EventQueue.biglock2.lock();

      pipeOut = new PipedOutputStream();
      PipedInputStream pipeIn = new PipedInputStream(pipeOut);
      BufferedInputStream bis = new BufferedInputStream(pipeIn);
      OutputStream nullOut = OutputStream.nullOutputStream();
      vt100 = new Vt100(nullOut, bis,
         new StringIoc("vt100-comp-" + instanceCounter++, null),
         StandardCharsets.UTF_8);

      Field parserField = Vt100.class.getDeclaredField("parser");
      parserField.setAccessible(true);
      parser = (Vt100Parser) parserField.get(vt100);

      doChar = Vt100Parser.class.getDeclaredMethod("doChar",
         char.class);
      doChar.setAccessible(true);

      // Ensure terminal has enough rows for testing
      Field rowsField = Vt100.class.getDeclaredField("rows");
      rowsField.setAccessible(true);
      rowsField.setInt(vt100, 24);

      // Pre-populate the buffer with 24 blank lines
      while (vt100.readIn() < 25)
         vt100.insertOne("", vt100.readIn());
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

   private void feed(String chars) throws Exception {
      for (int i = 0; i < chars.length(); i++)
         doChar.invoke(parser, chars.charAt(i));
   }

   /** Flushes pending characters accumulated in the parser's sb. */
   private void flush() throws Exception {
      Field sbField =
         Vt100Parser.class.getDeclaredField("sb");
      sbField.setAccessible(true);
      StringBuilder parserSb =
         (StringBuilder) sbField.get(parser);
      Field windowField =
         Vt100Parser.class.getDeclaredField("window");
      windowField.setAccessible(true);
      VScreen window = (VScreen) windowField.get(parser);
      window.updateScreen(parserSb);
   }

   private MovePos cursor() throws Exception {
      Field vtcurField = Vt100.class.getDeclaredField("vtcursor");
      vtcurField.setAccessible(true);
      return (MovePos) vtcurField.get(vt100);
   }

   /** Returns 1-based terminal row from cursor position. */
   private int cursorRow() throws Exception {
      Field rowsField = Vt100.class.getDeclaredField("rows");
      rowsField.setAccessible(true);
      int rows = rowsField.getInt(vt100);
      return cursor().y - vt100.readIn() + rows + 1;
   }

   /** Returns 1-based terminal column from cursor position. */
   private int cursorCol() throws Exception {
      return cursor().x + 1;
   }

   /** Returns the content of a terminal row (1-based). */
   private String lineContent(int termRow) throws Exception {
      Field rowsField = Vt100.class.getDeclaredField("rows");
      rowsField.setAccessible(true);
      int rows = rowsField.getInt(vt100);
      int absLine = vt100.readIn() - 1 - rows + termRow;
      if (absLine < 1 || absLine >= vt100.readIn())
         return "";
      return vt100.at(absLine);
   }

   // ── DECALN (ESC # 8) ──────────────────────────────────────

   @Nested
   @DisplayName("DECALN — Screen Alignment Display")
   class DecalnTests {

      @Test
      void fillsScreenWithE() throws Exception {
         feed("\033#8");
         // Every visible row should contain 'E' characters
         for (int r = 1; r <= 24; r++) {
            String line = lineContent(r);
            assertTrue(line.length() > 0,
               "Row " + r + " should not be empty");
            for (int i = 0; i < line.length(); i++)
               assertEquals('E', line.charAt(i),
                  "Row " + r + " col " + (i + 1));
         }
      }

      @Test
      void homesCursor() throws Exception {
         // Move cursor away first
         feed("\033[10;20H");
         feed("\033#8");
         assertEquals(1, cursorRow(), "row after DECALN");
         assertEquals(1, cursorCol(), "col after DECALN");
      }
   }

   // ── IND (ESC D) ───────────────────────────────────────────

   @Nested
   @DisplayName("IND — Index")
   class IndTests {

      @Test
      void movesCursorDown() throws Exception {
         feed("\033[1;1H"); // home
         feed("\033D");     // index
         assertEquals(2, cursorRow(), "cursor should move down");
      }

      @Test
      void scrollsAtBottomMargin() throws Exception {
         // Set scroll region rows 2-5, position cursor at row 5
         feed("\033[2;5r");
         feed("\033[5;1H");
         // Put text on row 2 so we can verify it scrolls
         feed("\033[2;1H");
         feed("MARKER");
         feed("\033[5;1H");
         feed("\033D"); // index at bottom margin — should scroll
         // Cursor stays at row 5, row 2's MARKER should be gone
         assertEquals(5, cursorRow(),
            "cursor stays at bottom margin");
         String row2 = lineContent(2);
         assertTrue(!row2.contains("MARKER"),
            "MARKER should have scrolled off");
      }
   }

   // ── RI (ESC M) ────────────────────────────────────────────

   @Nested
   @DisplayName("RI — Reverse Index")
   class RiTests {

      @Test
      void movesCursorUp() throws Exception {
         feed("\033[5;1H"); // row 5
         feed("\033M");     // reverse index
         assertEquals(4, cursorRow(), "cursor should move up");
      }

      @Test
      void scrollsDownAtTopMargin() throws Exception {
         // Set scroll region rows 3-10
         feed("\033[3;10r");
         // Position at top margin
         feed("\033[3;1H");
         // Put text on row 10 so we can verify it shifts
         feed("\033[10;1H");
         feed("BOTTOM");
         // Go back to top margin and reverse index
         feed("\033[3;1H");
         feed("\033M"); // RI at top margin — should scroll down
         assertEquals(3, cursorRow(),
            "cursor stays at top margin");
         // Row 3 should be blank (new line inserted)
         String row3 = lineContent(3);
         assertEquals("", row3, "new blank line at top of region");
      }
   }

   // ── NEL (ESC E) ───────────────────────────────────────────

   @Nested
   @DisplayName("NEL — Next Line")
   class NelTests {

      @Test
      void movesToNextLineCol1() throws Exception {
         feed("\033[5;20H"); // row 5, col 20
         feed("\033E");      // next line
         assertEquals(6, cursorRow(), "row after NEL");
         assertEquals(1, cursorCol(), "col after NEL");
      }

      @Test
      void scrollsAtBottomMargin() throws Exception {
         feed("\033[3;8r");  // region 3-8
         feed("\033[8;15H"); // at bottom margin, col 15
         feed("\033E");      // NEL at bottom — scroll
         assertEquals(8, cursorRow(),
            "cursor stays at bottom margin");
         assertEquals(1, cursorCol(), "col reset to 1");
      }
   }

   // ── DECSTBM (CSI r) ───────────────────────────────────────

   @Nested
   @DisplayName("DECSTBM — Set Top/Bottom Margins")
   class DecstbmTests {

      @Test
      void homesCursorAfterSet() throws Exception {
         feed("\033[10;20H"); // move away
         feed("\033[5;15r");   // set margins
         assertEquals(1, cursorRow(),
            "DECSTBM homes cursor to row 1");
         assertEquals(1, cursorCol(),
            "DECSTBM homes cursor to col 1");
      }

      @Test
      void resetMarginsHomes() throws Exception {
         feed("\033[5;15r"); // set margins
         feed("\033[10;5H"); // move away
         feed("\033[r");     // reset margins
         assertEquals(1, cursorRow(), "reset homes cursor");
      }

      @Test
      void scrollRespectsMargins() throws Exception {
         // Fill rows 1-24 with distinct content
         for (int r = 1; r <= 24; r++) {
            feed("\033[" + r + ";1H");
            feed("Line" + r);
         }
         // Set scroll region to rows 5-10
         feed("\033[5;10r");
         // Position at row 10 (bottom margin) and index
         feed("\033[10;1H");
         feed("\033D");
         // Row 1 (outside region) should be unchanged
         assertTrue(lineContent(1).startsWith("Line1"),
            "Row 1 outside region unchanged");
         // Row 11 (below region) should be unchanged
         assertTrue(lineContent(11).startsWith("Line11"),
            "Row 11 below region unchanged");
      }
   }

   // ── DECOM (ESC[?6h/l) ─────────────────────────────────────

   @Nested
   @DisplayName("DECOM — Origin Mode")
   class DecomTests {

      @Test
      void cursorPositioningRelativeToRegion() throws Exception {
         feed("\033[5;15r");  // margins 5-15
         feed("\033[?6h");    // origin mode ON
         // CUP 1;1 should put cursor at terminal row 5
         feed("\033[1;1H");
         assertEquals(5, cursorRow(),
            "origin mode: row 1 maps to margin top");
      }

      @Test
      void originModeOffIsAbsolute() throws Exception {
         feed("\033[5;15r");  // margins 5-15
         feed("\033[?6h");    // origin on
         feed("\033[?6l");    // origin off
         feed("\033[1;1H");
         assertEquals(1, cursorRow(),
            "origin mode off: row 1 is absolute");
      }

      @Test
      void homesCursorOnEnable() throws Exception {
         feed("\033[5;15r");
         feed("\033[10;10H"); // move away
         feed("\033[?6h");    // origin mode homes cursor
         assertEquals(5, cursorRow(),
            "DECOM on homes to top margin");
         assertEquals(1, cursorCol());
      }
   }

   // ── Delete Lines (CSI M) ──────────────────────────────────

   @Nested
   @DisplayName("Delete Lines (CSI M)")
   class DeleteLinesTests {

      @Test
      void deletesLineAtCursor() throws Exception {
         feed("\033[3;1H");
         feed("THREELINE");
         feed("\033[4;1H");
         feed("FOURLINE");
         feed("\033[3;1H");
         feed("\033[1M");  // delete 1 line at row 3
         assertEquals("FOURLINE", lineContent(3).trim(),
            "row 4 content should shift up to row 3");
      }
   }

   // ── Scroll Up/Down (CSI S / CSI T) ────────────────────────

   @Nested
   @DisplayName("Scroll Up / Scroll Down")
   class ScrollTests {

      @Test
      void scrollUpShiftsContent() throws Exception {
         feed("\033[1;1H");
         feed("TopRow");
         feed("\033[2;1H");
         feed("SecondRow");
         feed("\033[1S"); // scroll up 1
         // TopRow scrolls off, SecondRow becomes row 1
         String row1 = lineContent(1);
         assertTrue(row1.contains("SecondRow"),
            "SecondRow should now be at row 1");
      }

      @Test
      void scrollDownShiftsContent() throws Exception {
         feed("\033[1;1H");
         feed("TopRow");
         feed("\033[1T"); // scroll down 1
         // TopRow should move to row 2, row 1 should be blank
         String row1 = lineContent(1);
         assertEquals("", row1, "row 1 should be blank after scroll down");
         String row2 = lineContent(2);
         assertTrue(row2.contains("TopRow"),
            "TopRow should be at row 2");
      }
   }

   // ── Cursor movement vttest patterns ────────────────────────

   @Nested
   @DisplayName("Cursor Movement — vttest test 1 patterns")
   class CursorMovementTests {

      @Test
      void cupPositionsCorrectly() throws Exception {
         feed("\033[10;30H");
         assertEquals(10, cursorRow());
         assertEquals(30, cursorCol());
      }

      @Test
      void hvpSameAsCup() throws Exception {
         feed("\033[15;40f");
         assertEquals(15, cursorRow());
         assertEquals(40, cursorCol());
      }

      @Test
      void cursorUpClampedAtTop() throws Exception {
         feed("\033[1;1H");
         feed("\033[10A"); // move up 10 from row 1
         // Should clamp at row 1
         assertTrue(cursorRow() >= 1,
            "cursor should not go above row 1");
      }

      @Test
      void cursorDownClampedAtBottom() throws Exception {
         feed("\033[24;1H");
         feed("\033[10B"); // move down 10 from row 24
         // Should clamp at bottom
         assertTrue(cursorRow() <= 24,
            "cursor should not exceed bottom");
      }

      @Test
      void cursorForwardAndBack() throws Exception {
         feed("\033[1;1H");
         feed("\033[5C");   // right 5
         assertEquals(6, cursorCol());
         feed("\033[2D");   // left 2
         assertEquals(4, cursorCol());
      }

      @Test
      void leadingZerosInCsiIgnored() throws Exception {
         // vttest test 1 sends CSI with leading zeros
         feed("\033[00000000004;00000000010H");
         assertEquals(4, cursorRow());
         assertEquals(10, cursorCol());
      }
   }

   // ── Erase operations ──────────────────────────────────────

   @Nested
   @DisplayName("Erase operations")
   class EraseTests {

      @Test
      void eraseToEndOfLine() throws Exception {
         feed("\033[1;1H");
         feed("Hello World");
         feed("\033[1;6H");  // position at column 6
         feed("\033[0K");    // erase to end of line
         String line = lineContent(1);
         assertTrue(line.startsWith("Hello"),
            "first 5 chars preserved");
         assertTrue(line.length() <= 5
            || line.substring(5).isBlank(),
            "chars from col 6 erased");
      }

      @Test
      void eraseFromBeginningOfLine() throws Exception {
         feed("\033[1;1H");
         feed("ABCDEFGHIJ");
         feed("\033[1;5H");  // position at column 5
         feed("\033[1K");    // EL 1: erase from beginning to cursor
         String line = lineContent(1);
         // Cols 1-5 should be spaces, cols 6-10 preserved
         for (int i = 0; i < 5 && i < line.length(); i++)
            assertEquals(' ', line.charAt(i),
               "col " + (i + 1) + " should be erased");
         assertTrue(line.length() >= 10
            && line.substring(5).startsWith("FGHIJ"),
            "cols 6-10 should be preserved");
      }

      @Test
      void eraseFromBeginningAtCol1() throws Exception {
         feed("\033[1;1H");
         feed("XYZW");
         feed("\033[1;1H");  // column 1
         feed("\033[1K");    // EL 1 at col 1
         String line = lineContent(1);
         // Col 1 should be erased, rest preserved
         assertTrue(line.length() >= 1
            && line.charAt(0) == ' ',
            "char at cursor should be erased");
         assertTrue(line.substring(1).startsWith("YZW"),
            "chars after cursor preserved");
      }

      @Test
      void eraseEntireLine() throws Exception {
         feed("\033[1;1H");
         feed("Some text here");
         feed("\033[1;5H");
         feed("\033[2K");    // erase entire line
         String line = lineContent(1);
         assertEquals("", line, "entire line erased");
      }

      @Test
      void eraseScreenFromCursorToEnd() throws Exception {
         feed("\033[1;1H");
         feed("Row1Text");
         feed("\033[2;1H");
         feed("Row2Text");
         feed("\033[1;1H");
         feed("\033[0J");    // erase from cursor to end of screen
         // Row 1 still has cursor position text from col 0+ erased
         // Row 2 should be erased
         String row2 = lineContent(2);
         assertEquals("", row2, "row 2 erased by ED 0");
      }
   }

   // ── ED2 scrollback preservation ───────────────────────────

   @Nested
   @DisplayName("ED 2 scrollback preservation")
   class Ed2ScrollbackTests {

      @Test
      void ed2PreservesContentInScrollback() throws Exception {
         // Write content on several rows
         feed("\033[1;1H");
         feed("LINE_ONE");
         feed("\033[2;1H");
         feed("LINE_TWO");
         feed("\033[3;1H");
         feed("LINE_THREE");
         flush();
         int readInBefore = vt100.readIn();
         // ESC[2J should scroll content into scrollback
         feed("\033[2J");
         flush();
         // Buffer should have grown by 24 (rows) new blank lines
         assertEquals(readInBefore + 24, vt100.readIn(),
            "buffer grows by rows count");
         // Visible viewport should now be blank (fixline may
         // pad the cursor line with spaces, so check isBlank)
         for (int r = 1; r <= 24; r++)
            assertTrue(lineContent(r).isBlank(),
               "viewport row " + r + " should be blank, got: "
               + lineContent(r));
         // Old content is in scrollback (above viewport)
         // Scrollback line at old position should still have content
         int oldRow1Abs = readInBefore - 1 - 24 + 1;
         if (oldRow1Abs >= 1) {
            String scrollback = vt100.at(oldRow1Abs);
            assertTrue(scrollback.startsWith("LINE_ONE"),
               "scrollback preserves old row 1, got: " + scrollback);
         }
      }

      @Test
      void ed2CursorStaysAtSameTerminalRow() throws Exception {
         // Move cursor to row 5, col 10
         feed("\033[5;10H");
         flush();
         int rowBefore = cursorRow();
         int colBefore = cursorCol();
         assertEquals(5, rowBefore);
         assertEquals(10, colBefore);
         // ED2 does not move cursor
         feed("\033[2J");
         flush();
         assertEquals(5, cursorRow(),
            "cursor terminal row preserved after ED2");
         assertEquals(10, cursorCol(),
            "cursor column preserved after ED2");
      }

      @Test
      void clearCommandPreservesScrollback() throws Exception {
         // Simulate the 'clear' command: ESC[H ESC[2J
         feed("\033[1;1H");
         feed("HISTORY LINE A");
         feed("\033[2;1H");
         feed("HISTORY LINE B");
         flush();
         // Now run 'clear': home + erase display
         feed("\033[H");
         feed("\033[2J");
         flush();
         // Cursor should be at home (1,1)
         assertEquals(1, cursorRow(), "cursor at row 1 after clear");
         assertEquals(1, cursorCol(), "cursor at col 1 after clear");
         // Viewport should be blank
         assertEquals("", lineContent(1), "row 1 blank after clear");
         assertEquals("", lineContent(2), "row 2 blank after clear");
      }

      @Test
      void clearWithEd3PreservesScrollback() throws Exception {
         // On macOS, /usr/bin/clear sends ESC[H ESC[2J ESC[3J.
         // ED3 (ESC[3J) must NOT erase content that ED2 pushed
         // into scrollback — otherwise 'clear' destroys history.
         feed("\033[1;1H");
         feed("IMPORTANT_HISTORY");
         feed("\033[2;1H");
         feed("MORE_HISTORY");
         flush();
         int readInBefore = vt100.readIn();
         // Full 'clear' sequence including ED3
         feed("\033[H");
         feed("\033[2J");
         feed("\033[3J");
         flush();
         // Buffer should have grown (ED2 pushed content up)
         assertTrue(vt100.readIn() > readInBefore,
            "buffer grew after ED2");
         // Viewport should be blank
         assertTrue(lineContent(1).isBlank(),
            "row 1 blank after clear+ED3");
         // Scrollback should still contain old content
         int oldRow1Abs = readInBefore - 1 - 24 + 1;
         if (oldRow1Abs >= 1) {
            String scrollback = vt100.at(oldRow1Abs);
            assertTrue(scrollback.startsWith("IMPORTANT_HISTORY"),
               "scrollback preserved despite ED3, got: "
               + scrollback);
         }
      }

      @Test
      void ed2OnAltScreenClearsInPlace() throws Exception {
         // Write content on normal screen
         feed("\033[1;1H");
         feed("NORMAL_CONTENT");
         flush();
         // Switch to alt screen
         feed("\033[?1049h");
         flush();
         feed("\033[1;1H");
         feed("ALT_CONTENT");
         flush();
         int readInBefore = vt100.readIn();
         // ED2 on alt screen should NOT grow the buffer
         feed("\033[2J");
         flush();
         assertEquals(readInBefore, vt100.readIn(),
            "buffer should not grow on alt screen ED2");
         // Alt screen viewport should be cleared (fixline may
         // pad cursor line with spaces)
         assertTrue(lineContent(1).isBlank(),
            "alt screen row 1 cleared, got: " + lineContent(1));
      }
   }

   // ── Combined vttest-like sequences ────────────────────────

   @Nested
   @DisplayName("Combined vttest-like sequences")
   class VttestPatterns {

      @Test
      void decalnThenEraseCreatesFrame() throws Exception {
         // vttest test 1: DECALN, then erase inner area
         feed("\033#8"); // fill with E
         // Erase from beginning of screen to cursor (ED 1)
         feed("\033[9;10H");
         feed("\033[1J");
         // ED 1 erases from start of screen through cursor:
         // rows 1-8 erased, row 9 partially erased up to col 10
         String row1 = lineContent(1);
         assertTrue(!row1.contains("E"),
            "row 1 should be erased by ED 1");
         // Row 10 (below cursor) should still have E's
         String row10 = lineContent(10);
         assertTrue(row10.contains("E"),
            "row 10 below cursor still has E's");
      }

      @Test
      void eraseScreenToBeginningIncludesCursor()
            throws Exception {
         // ED 1 must erase the character AT the cursor
         feed("\033[1;1H");
         feed("ABCDEFGHIJ");
         feed("\033[1;5H");
         feed("\033[1J");    // ED 1
         String line = lineContent(1);
         // Cols 1-5 must be erased (spaces or empty)
         for (int i = 0; i < 5 && i < line.length(); i++)
            assertEquals(' ', line.charAt(i),
               "col " + (i + 1)
               + " should be erased by ED 1");
      }

      /**
       * Reproduces the vttest test 1 "first screen" frame:
       * DECALN fills with E, then ED and EL carve out
       * the inner area leaving an E-frame.
       *
       * <p>For 80 columns: inner_l=10, inner_r=71.
       * After all erase operations:
       * <ul>
       *   <li>Rows 1-8: completely erased (no E's)</li>
       *   <li>Row 9: E's in cols 11-70 only</li>
       *   <li>Rows 10-16: E's in cols 11-70 only</li>
       *   <li>Row 17: fully erased</li>
       *   <li>Rows 18-24: fully erased</li>
       * </ul></p>
       */
      @Test
      void vttestScreen1FramePattern() throws Exception {
         // Replicate tst_movements() first screen (80-col)
         int width = 80;
         int innerL = (width - 60) / 2;   // 10
         int innerR = 61 + innerL;         // 71

         // 1. DECALN — fill with E
         feed("\033#8");

         // 2. cup(9, innerL); ed(1);
         feed("\033[9;" + innerL + "H");
         feed("\033[1J");

         // 3. cup(18, 60); ed(0);
         feed("\033[18;60H");
         feed("\033[0J");

         // 4. el(1) on row 18
         feed("\033[1K");

         // 5. cup(9, innerR); el(0);
         feed("\033[9;" + innerR + "H");
         feed("\033[0K");

         // 6. Rows 10-16: el(1) at innerL, el(0) at innerR
         for (int row = 10; row <= 16; row++) {
            feed("\033[" + row + ";" + innerL + "H");
            feed("\033[1K");
            feed("\033[" + row + ";" + innerR + "H");
            feed("\033[0K");
         }

         // 7. Row 17: cup(17,30); el(2)
         feed("\033[17;30H");
         feed("\033[2K");

         // ── Verify the E-frame ──
         // Rows 1-8: fully erased
         for (int r = 1; r <= 8; r++) {
            String line = lineContent(r);
            assertTrue(!line.contains("E"),
               "row " + r + " should be fully erased");
         }

         // Row 9: cols 1-10 erased (ED 1 cleared through
         // col 10), cols 11-70 have E, cols 71-80 erased
         String row9 = lineContent(9);
         for (int c = 0;
               c < innerL && c < row9.length(); c++)
            assertTrue(row9.charAt(c) != 'E',
               "row 9 col " + (c + 1)
               + " should be erased");
         for (int c = innerL;
               c < innerR - 1 && c < row9.length(); c++)
            assertEquals('E', row9.charAt(c),
               "row 9 col " + (c + 1)
               + " should have E");

         // Rows 10-16: cols 1-10 erased (EL 1),
         // cols 11-70 have E, cols 71-80 erased (EL 0)
         for (int r = 10; r <= 16; r++) {
            String line = lineContent(r);
            for (int c = 0;
                  c < innerL && c < line.length(); c++)
               assertTrue(line.charAt(c) != 'E',
                  "row " + r + " col " + (c + 1)
                  + " should be erased by EL 1");
            for (int c = innerL;
                  c < innerR - 1 && c < line.length();
                  c++)
               assertEquals('E', line.charAt(c),
                  "row " + r + " col " + (c + 1)
                  + " should have E");
         }

         // Row 17: fully erased (EL 2)
         assertTrue(!lineContent(17).contains("E"),
            "row 17 should be fully erased");

         // Rows 18-24: fully erased (ED 0 + EL 1)
         for (int r = 18; r <= 24; r++)
            assertTrue(!lineContent(r).contains("E"),
               "row " + r
               + " should be fully erased");
      }

      @Test
      void scrollRegionWithIndRi() throws Exception {
         // Set region 5-20, use IND and RI within it
         feed("\033[5;20r");
         // Fill region with content
         for (int r = 5; r <= 20; r++) {
            feed("\033[" + r + ";1H");
            feed("R" + r);
         }
         // IND at bottom of region
         feed("\033[20;1H");
         feed("\033D");
         // Row 5 should now have what was row 6
         assertTrue(lineContent(5).startsWith("R6"),
            "row 5 has R6 after IND scroll");
         // RI at top of region
         feed("\033[5;1H");
         feed("\033M");
         // Row 5 should be blank (new line inserted)
         assertEquals("", lineContent(5),
            "row 5 blank after RI at top");
      }

      @Test
      void originModeWithScrollRegion() throws Exception {
         feed("\033[10;20r"); // margins 10-20
         feed("\033[?6h");    // origin mode
         // CUP (1,1) in origin mode = absolute row 10
         feed("\033[1;1H");
         assertEquals(10, cursorRow());
         // CUP (5,1) in origin mode = absolute row 14
         feed("\033[5;1H");
         assertEquals(14, cursorRow());
         feed("\033[?6l"); // origin mode off
      }

      /**
       * Tests the vttest border-drawing with IND and RI.
       * vttest draws a column of '+' going down column 2
       * using IND, then a column of '+' going up at
       * column width-1 using RI.
       */
      @Test
      void vttestBorderWithIndRi() throws Exception {
         // Draw + down column 2 using IND (ESC D)
         feed("\033[2;2H"); // row 2, col 2
         for (int r = 2; r <= 23; r++) {
            feed("+");
            feed("\033[D"); // CUB 1 (backspace)
            feed("\033D");  // IND (index down)
         }
         // Check column 2 has + on rows 2-23
         for (int r = 2; r <= 23; r++) {
            String line = lineContent(r);
            assertTrue(line.length() >= 2
               && line.charAt(1) == '+',
               "row " + r + " col 2 should have +");
         }

         // Draw + up column 79 using RI (ESC M)
         feed("\033[23;79H"); // row 23, col 79
         for (int r = 23; r >= 2; r--) {
            feed("+");
            feed("\033[D"); // CUB 1
            feed("\033M");  // RI (reverse index up)
         }
         // Check column 79 has + on rows 2-23
         for (int r = 2; r <= 23; r++) {
            String line = lineContent(r);
            assertTrue(line.length() >= 79
               && line.charAt(78) == '+',
               "row " + r + " col 79 should have +");
         }
      }

      /**
       * Tests cursor control characters inside CSI sequences.
       * vttest test 1 sends "A B C D E F G H I" using
       * CUF 2 with embedded BS character.
       */
      @Test
      void controlCharsInsideCsi() throws Exception {
         feed("\033[2J");    // clear screen
         feed("\033[4;1H");
         // vttest: print char, then CSI 2 BS C
         // (forward 2, backspace 1 = net forward 1)
         for (int i = 1; i < 10; i++) {
            char ch = (char) ('@' + i);
            feed(String.valueOf(ch));
            // CSI 2 <BS> C → cursor forward, BS consumed
            // as control inside CSI
            feed("\033[2\010C");
         }
         String line = lineContent(4);
         // Should produce "A B C D E F G H I" spaced out
         assertTrue(line.contains("A"),
            "line should contain A");
         assertTrue(line.contains("I"),
            "line should contain I");
      }
   }

   // ── DECSC/DECRC SGR save/restore ──────────────────────────

   @Nested
   @DisplayName("DECSC/DECRC — Save/Restore Cursor + SGR")
   class DecscDecrcTests {

      /** Gets the current SGR packed attribute from ECScreen. */
      private int getCurrentAttr() throws Exception {
         Field ecField =
            Vt100.class.getDeclaredField("ecscreen");
         ecField.setAccessible(true);
         Object ecscreen = ecField.get(vt100);
         Field attrField =
            ecscreen.getClass().getDeclaredField("currentAttr");
         attrField.setAccessible(true);
         return attrField.getInt(ecscreen);
      }

      @Test
      void savesAndRestoresPosition() throws Exception {
         feed("\033[5;20H"); // position at row 5, col 20
         feed("\0337");      // DECSC
         feed("\033[10;40H"); // move away
         feed("\0338");       // DECRC
         assertEquals(5, cursorRow(), "row restored");
         assertEquals(20, cursorCol(), "col restored");
      }

      @Test
      void savesAndRestoresSgrAttributes() throws Exception {
         // Set bold + fg red
         feed("\033[1;31m");
         int boldRedAttr = getCurrentAttr();
         assertTrue(boldRedAttr != CellAttr.DEFAULT,
            "attr should not be default after SGR 1;31");
         // Save cursor + attrs
         feed("\0337");
         // Change to underline + fg green
         feed("\033[0;4;32m");
         int ulGreenAttr = getCurrentAttr();
         assertTrue(ulGreenAttr != boldRedAttr,
            "attr should differ after SGR change");
         // Restore cursor — should restore bold+red
         feed("\0338");
         int restored = getCurrentAttr();
         assertEquals(boldRedAttr, restored,
            "SGR attributes should be restored by DECRC");
      }

      @Test
      void restoreAfterResetRecoversSaved() throws Exception {
         // Set bold
         feed("\033[1m");
         feed("\0337");      // DECSC (save bold)
         // Reset all SGR
         feed("\033[0m");
         assertEquals(CellAttr.DEFAULT, getCurrentAttr(),
            "after reset, attr should be default");
         // Restore — bold should come back
         feed("\0338");
         assertTrue(getCurrentAttr() != CellAttr.DEFAULT,
            "bold should be restored after DECRC");
      }

      /**
       * vttest test 2 pattern: save cursor with SGR, move
       * around, print with different SGR, restore and
       * continue printing with original SGR.
       */
      @Test
      void vttestDecscDecrcPattern() throws Exception {
         // Set reverse video + underline
         feed("\033[7;4m");
         int savedAttr = getCurrentAttr();
         // DECSC — save cursor (row 1 col 1)
         // and SGR (reverse+underline)
         feed("\033[1;1H");
         feed("\0337");
         // Move to row 10 col 20 and set plain text
         feed("\033[10;20H");
         feed("\033[0m");
         feed("A"); // printed with default attrs
         // DECRC — restore position and SGR
         feed("\0338");
         assertEquals(1, cursorRow(), "row restored");
         assertEquals(1, cursorCol(), "col restored");
         assertEquals(savedAttr, getCurrentAttr(),
            "reverse+underline restored by DECRC");
      }
   }

   // ── CUU/CUD scroll region clamping ────────────────────────

   @Nested
   @DisplayName("CUU/CUD — Scroll region clamping")
   class CuuCudClampTests {

      @Test
      void cuuClampsAtScrollTop() throws Exception {
         // Set scroll region rows 5-15
         feed("\033[5;15r");
         // Move to row 8 (inside region)
         feed("\033[8;1H");
         // CUU 20 — should clamp at row 5
         feed("\033[20A");
         assertEquals(5, cursorRow(),
            "CUU should clamp at scroll top");
      }

      @Test
      void cudClampsAtScrollBottom() throws Exception {
         // Set scroll region rows 5-15
         feed("\033[5;15r");
         // Move to row 8 (inside region)
         feed("\033[8;1H");
         // CUD 20 — should clamp at row 15
         feed("\033[20B");
         assertEquals(15, cursorRow(),
            "CUD should clamp at scroll bottom");
      }

      @Test
      void cuuFromOutsideRegionNotClamped() throws Exception {
         // Set scroll region rows 10-20
         feed("\033[10;20r");
         // Position above scroll region at row 5
         feed("\033[5;1H");
         // CUU 3 — cursor is outside region, should not
         // clamp at scroll top
         feed("\033[3A");
         assertEquals(2, cursorRow(),
            "CUU from outside region moves freely");
      }

      @Test
      void cudFromOutsideRegionNotClamped() throws Exception {
         // Set scroll region rows 5-10
         feed("\033[5;10r");
         // Position below scroll region at row 15
         feed("\033[15;1H");
         // CUD 5 — cursor is outside region, should not
         // clamp at scroll bottom
         feed("\033[5B");
         assertEquals(20, cursorRow(),
            "CUD from outside region moves freely");
      }

      @Test
      void vttestDoScrollingCudClamp() throws Exception {
         // vttest do_scrolling: decom(TRUE), decstbm(first,
         // last), then cud(max_lines) — cursor must clamp
         // at bottom of region
         feed("\033[?6h");    // origin mode
         feed("\033[12;13r"); // 2-line scroll region
         // CUD max_lines — should clamp at row 2 (origin
         // mode: bottom of 2-line region)
         feed("\033[24B");
         // In origin mode, row 2 = absolute row 13
         assertEquals(13, cursorRow(),
            "CUD with origin mode clamps at bottom");
         feed("\033[?6l"); // origin mode off
      }
   }

   // ── HTS / TBC — Tab stop management ───────────────────────

   @Nested
   @DisplayName("HTS / TBC — Tab stop management")
   class TabStopTests {

      @Test
      void defaultTabStopsEvery8Columns() throws Exception {
         feed("\033[1;1H"); // col 1
         feed("\t");        // tab to next stop
         // Default tab stops at columns 1, 9, 17, 25, ... (per VT100 spec)
         // From col 0 (0-based), next stop is at col 8 (0-based)
         // → terminal col 9
         assertEquals(9, cursorCol(),
            "default tab from col 1 to col 9");
      }

      @Test
      void htsSetsCustomTabStop() throws Exception {
         // Clear all tab stops
         feed("\033[3g");
         // Set tab stop at column 5
         feed("\033[1;5H");
         feed("\033H");     // HTS
         // Move to column 1 and tab
         feed("\033[1;1H");
         feed("\t");
         assertEquals(5, cursorCol(),
            "tab should advance to custom stop at col 5");
      }

      @Test
      void tbcClearsSingleStop() throws Exception {
         // Clear all, then set stops at 10 and 20
         feed("\033[3g");
         feed("\033[1;10H");
         feed("\033H");
         feed("\033[1;20H");
         feed("\033H");
         // Clear the stop at col 10
         feed("\033[1;10H");
         feed("\033[0g");
         // Tab from col 1 — should skip 10, go to 20
         feed("\033[1;1H");
         feed("\t");
         assertEquals(20, cursorCol(),
            "tab should skip cleared stop and go to 20");
      }

      @Test
      void tbcClearsAllStops() throws Exception {
         // Clear all tab stops
         feed("\033[3g");
         // Tab from col 1 — no stops, go to end of line
         feed("\033[1;1H");
         feed("\t");
         int cols = 80;
         assertEquals(cols, cursorCol(),
            "with no tab stops, tab goes to end of line");
      }

      /**
       * vttest test 2 tab pattern: clear all, set every 3
       * columns, then clear every 6, then tab across.
       */
      @Test
      void vttestTabSetResetPattern() throws Exception {
         // TBC 3 — clear all
         feed("\033[3g");
         // Set tab stops every 3 columns (cols 4,7,10,...)
         for (int col = 1; col <= 78; col += 3) {
            feed("\033[1;" + col + "H");
            feed("\033[3C");  // CUF 3
            feed("\033H");    // HTS
         }
         // Clear every 6th from col 4
         feed("\033[1;4H");
         for (int col = 4; col <= 78; col += 6) {
            feed("\033[0g");  // TBC 0
            feed("\033[6C");  // CUF 6
         }
         // Tab from col 1 — should land at col 7 (first
         // remaining stop after clearing)
         feed("\033[1;1H");
         feed("\t");
         assertEquals(7, cursorCol(),
            "vttest tab pattern: first stop at col 7");
      }
   }

   // ── CUF(0)/CUB(0)/CUU(0)/CUD(0) — parameter 0 = 1 ──

   @Nested
   @DisplayName("Cursor movement with parameter 0")
   class CursorZeroParamTests {

      @Test
      void cufZeroMovesRight1() throws Exception {
         feed("\033[1;1H");
         feed("\033[0C");
         assertEquals(2, cursorCol(),
            "CUF 0 should move right 1");
      }

      @Test
      void cubZeroMovesLeft1() throws Exception {
         feed("\033[1;5H");
         feed("\033[0D");
         assertEquals(4, cursorCol(),
            "CUB 0 should move left 1");
      }

      @Test
      void cuuZeroMovesUp1() throws Exception {
         feed("\033[5;1H");
         feed("\033[0A");
         assertEquals(4, cursorRow(),
            "CUU 0 should move up 1");
      }

      @Test
      void cudZeroMovesDown1() throws Exception {
         feed("\033[5;1H");
         feed("\033[0B");
         assertEquals(6, cursorRow(),
            "CUD 0 should move down 1");
      }

      /**
       * vttest top border pattern: cuf(0)/cub(2)/cuf(1) should
       * advance cursor by 1 net position per iteration.
       */
      @Test
      void vttestTopBorderPattern() throws Exception {
         // Start at row 2, col 3
         feed("\033[2;3H");
         // Write '+', cuf(0), cub(2), cuf(1) — net +1 col
         for (int col = 3; col <= 10; col++) {
            feed("+");
            feed("\033[0C");  // CUF 0
            feed("\033[2D");  // CUB 2
            feed("\033[1C");  // CUF 1
         }
         // Row 2 should have '+' from col 3 to col 10
         String row2 = lineContent(2);
         for (int c = 2; c <= 9; c++)
            assertEquals('+', row2.charAt(c),
               "row 2 col " + (c + 1)
               + " should be '+'");
      }

      /**
       * vttest bottom border pattern: cub(1)/cuf(1)/cub(0)/BS
       * should move cursor left by 1 net position per iteration.
       */
      @Test
      void vttestBottomBorderPattern() throws Exception {
         // Fill row 23 with spaces so we can check characters
         feed("\033[23;1H");
         for (int i = 0; i < 80; i++)
            feed(" ");
         // Start at row 23, col 10
         feed("\033[23;10H");
         for (int col = 10; col >= 3; col--) {
            feed("+");
            feed("\033[1D");   // CUB 1
            feed("\033[1C");   // CUF 1
            feed("\033[0D");   // CUB 0
            feed("\b");        // BS
         }
         String row23 = lineContent(23);
         for (int c = 2; c <= 9; c++)
            assertEquals('+', row23.charAt(c),
               "row 23 col " + (c + 1)
               + " should be '+'");
      }
   }

   // ── DECCOLM (CSI ?3h/l) — 80/132 column mode ─────────

   @Nested
   @DisplayName("DECCOLM — 80/132 column mode")
   class DeccolmTests {

      @Test
      void deccolmSetClearsScreen() throws Exception {
         feed("\033[?40h"); // Allow 80/132 switching
         feed("\033#8");   // Fill with E
         assertTrue(lineContent(5).contains("E"),
            "before DECCOLM: row 5 has E");
         feed("\033[?3h"); // Set 132-col mode
         String line = lineContent(5);
         assertTrue(!line.contains("E"),
            "after DECCOLM set: screen cleared");
      }

      @Test
      void deccolmResetClearsScreen() throws Exception {
         feed("\033[?40h"); // Allow 80/132 switching
         feed("\033#8");
         feed("\033[?3l"); // Reset to 80-col mode
         String line = lineContent(5);
         assertTrue(!line.contains("E"),
            "after DECCOLM reset: screen cleared");
      }

      @Test
      void deccolmHomesCursor() throws Exception {
         feed("\033[?40h"); // Allow 80/132 switching
         feed("\033[10;40H");
         feed("\033[?3h");
         assertEquals(1, cursorRow(), "row after DECCOLM");
         assertEquals(1, cursorCol(), "col after DECCOLM");
      }

      @Test
      void deccolmResetsScrollRegion() throws Exception {
         feed("\033[?40h"); // Allow 80/132 switching
         feed("\033[5;20r");  // Set scroll region
         feed("\033[?3h");    // DECCOLM
         // After DECCOLM, region should be reset (full screen)
         // IND at row 24 should not fail
         feed("\033[24;1H");
         feed("\033D");       // IND
         // Cursor should stay at row 24 (scrolled)
         assertEquals(24, cursorRow(),
            "IND at bottom after DECCOLM reset");
      }

      @Test
      void decalnFills132ColsInDeccolm() throws Exception {
         feed("\033[?40h"); // Allow 80/132 switching
         feed("\033[?3h"); // 132-col mode
         feed("\033#8");   // DECALN
         String row = lineContent(5);
         assertEquals(132, row.length(),
            "DECALN should fill 132 columns in DECCOLM");
         for (int i = 0; i < 132; i++)
            assertEquals('E', row.charAt(i),
               "col " + (i + 1) + " should be E");
      }

      /**
       * vttest test 1 second pass: 132-col E-frame carve-out.
       */
      @Test
      void vttestScreen1Frame132Col() throws Exception {
         int width = 132;
         int innerL = (width - 60) / 2;   // 36
         int innerR = 61 + innerL;         // 97
         int hlfxtra = (width - 80) / 2;   // 26

         feed("\033[?40h"); // Allow 80/132 switching
         feed("\033[?3h"); // 132-col mode
         feed("\033#8");   // DECALN

         // cup(9, innerL); ed(1);
         feed("\033[9;" + innerL + "H");
         feed("\033[1J");

         // cup(18, 60+hlfxtra=86); ed(0);
         feed("\033[18;" + (60 + hlfxtra) + "H");
         feed("\033[0J");

         // el(1) on row 18
         feed("\033[1K");

         // cup(9, innerR=97); el(0);
         feed("\033[9;" + innerR + "H");
         feed("\033[0K");

         // Rows 10-16: el(1) at innerL, el(0) at innerR
         for (int row = 10; row <= 16; row++) {
            feed("\033[" + row + ";" + innerL + "H");
            feed("\033[1K");
            feed("\033[" + row + ";" + innerR + "H");
            feed("\033[0K");
         }

         // Row 17: el(2)
         feed("\033[17;30H");
         feed("\033[2K");

         // Verify E-frame: rows 9-16, cols innerL+1 to innerR-1
         // (1-based cols 37-96)
         for (int r = 1; r <= 8; r++)
            assertTrue(!lineContent(r).contains("E"),
               "row " + r + " should be erased");

         String row9 = lineContent(9);
         for (int c = 0; c < innerL && c < row9.length(); c++)
            assertTrue(row9.charAt(c) != 'E',
               "row 9 col " + (c + 1)
               + " should be erased");
         for (int c = innerL;
               c < innerR - 1 && c < row9.length(); c++)
            assertEquals('E', row9.charAt(c),
               "row 9 col " + (c + 1)
               + " should have E");
         // Past innerR should be erased
         for (int c = innerR - 1;
               c < row9.length(); c++)
            assertTrue(row9.charAt(c) != 'E',
               "row 9 col " + (c + 1)
               + " should be erased past innerR");

         for (int r = 17; r <= 24; r++)
            assertTrue(!lineContent(r).contains("E"),
               "row " + r + " should be erased");
      }
   }

   // ── Full vttest test 1 screen (80-col) with borders ───

   @Nested
   @DisplayName("vttest test 1 — full screen with borders")
   class VttestFullScreen1 {

      /**
       * Reproduces the full tst_movements() first pass (80-col)
       * from vttest. After DECALN + ED/EL carve-out + border
       * drawing + inner text clearing, verifies the border is
       * unbroken and E-frame is correctly positioned.
       */
      @Test
      void fullBorderAndEFrame80Col() throws Exception {
         int width = 80;
         int maxLines = 24;
         int innerL = (width - 60) / 2;   // 10
         int innerR = 61 + innerL;         // 71
         int hlfxtra = (width - 80) / 2;   // 0

         // 1. DECALN
         feed("\033#8");

         // 2-7. Carve out the E-frame
         feed("\033[9;" + innerL + "H");
         feed("\033[1J");
         feed("\033[18;" + (60 + hlfxtra) + "H");
         feed("\033[0J");
         feed("\033[1K");
         feed("\033[9;" + innerR + "H");
         feed("\033[0K");
         for (int row = 10; row <= 16; row++) {
            feed("\033[" + row + ";" + innerL + "H");
            feed("\033[1K");
            feed("\033[" + row + ";" + innerR + "H");
            feed("\033[0K");
         }
         feed("\033[17;30H");
         feed("\033[2K");

         // 8. Top and bottom '*' borders
         for (int col = 1; col <= width; col++) {
            feed("\033[" + maxLines + ";" + col + "H");
            feed("*");
            feed("\033[1;" + col + "H");
            feed("*");
         }

         // 9. Left border '+' via IND
         feed("\033[2;2H");
         for (int row = 2; row <= maxLines - 1; row++) {
            feed("+");
            feed("\033[1D");
            feed("\033D");   // IND
         }

         // 10. Right border '+' via RI
         feed("\033[" + (maxLines - 1) + ";"
            + (width - 1) + "H");
         for (int row = maxLines - 1; row >= 2; row--) {
            feed("+");
            feed("\033[1D");
            feed("\033M");   // RI
         }

         // 11. Left/right '*' per row
         feed("\033[2;1H");
         for (int row = 2; row <= maxLines - 1; row++) {
            feed("*");
            feed("\033[" + row + ";" + width + "H");
            feed("*");
            feed("\033[10D");
            if (row < 10)
               feed("\033E");  // NEL
            else
               feed("\033E");  // NEL (always CR+LF)
         }

         // 12. Top '+' border (row 2, cols 3-78)
         feed("\033[2;10H");
         feed("\033[" + (42 + hlfxtra) + "D");
         feed("\033[2C");
         for (int col = 3; col <= width - 2; col++) {
            feed("+");
            feed("\033[0C");  // CUF 0
            feed("\033[2D");  // CUB 2
            feed("\033[1C");  // CUF 1
         }

         // 13. Bottom '+' border (row 23)
         feed("\033[" + (maxLines - 1) + ";"
            + (innerR - 1) + "H");
         feed("\033[" + (42 + hlfxtra) + "C");
         feed("\033[2D");
         for (int col = width - 2; col >= 3; col--) {
            feed("+");
            feed("\033[1D");  // CUB 1
            feed("\033[1C");  // CUF 1
            feed("\033[0D");  // CUB 0
            feed("\b");       // BS
         }

         // ── Verify top border ──
         String row1 = lineContent(1);
         for (int c = 0; c < width && c < row1.length();
              c++)
            assertEquals('*', row1.charAt(c),
               "row 1 col " + (c + 1)
               + " should be *");

         // ── Verify row 2: *, +...+, * ──
         String row2 = lineContent(2);
         assertEquals('*', row2.charAt(0),
            "row 2 col 1 = *");
         assertEquals('+', row2.charAt(1),
            "row 2 col 2 = +");
         for (int c = 2; c < width - 2
              && c < row2.length(); c++)
            assertEquals('+', row2.charAt(c),
               "row 2 col " + (c + 1)
               + " should be +");
         assertEquals('*', row2.charAt(width - 1),
            "row 2 col " + width + " = *");

         // ── Verify E-frame rows 9-16 ──
         for (int r = 9; r <= 16; r++) {
            String line = lineContent(r);
            for (int c = innerL;
                  c < innerR - 1 && c < line.length();
                  c++)
               assertEquals('E', line.charAt(c),
                  "row " + r + " col " + (c + 1)
                  + " should have E");
         }

         // ── Verify bottom border row 23 ──
         String row23 = lineContent(maxLines - 1);
         assertEquals('*', row23.charAt(0),
            "row 23 col 1 = *");
         for (int c = 2; c < width - 2
              && c < row23.length(); c++)
            assertEquals('+', row23.charAt(c),
               "row 23 col " + (c + 1) + " = +");
         assertEquals('*', row23.charAt(width - 1),
            "row 23 col " + width + " = *");

         // ── Verify bottom row 24 ──
         String row24 = lineContent(maxLines);
         for (int c = 0; c < width && c < row24.length();
              c++)
            assertEquals('*', row24.charAt(c),
               "row 24 col " + (c + 1)
               + " should be *");

         // ── Verify NO remaining E's in border rows ──
         for (int r = 1; r <= 8; r++) {
            String line = lineContent(r);
            assertTrue(!line.contains("E"),
               "row " + r
               + " should have no E's after borders");
         }
         assertTrue(!lineContent(17).contains("E"),
            "row 17 should be erased");
         for (int r = 18; r <= maxLines; r++) {
            String line = lineContent(r);
            assertTrue(!line.contains("E"),
               "row " + r + " should have no E's");
         }
      }
   }

   // ── Resize with active scroll region ──────────────────────

   @Nested
   @DisplayName("Resize — scroll region bounds after vertical resize")
   class ResizeScrollRegionTests {

      @Test
      @DisplayName("scrollDown after vertical shrink does not crash")
      void scrollDownAfterVerticalShrink() throws Exception {
         // Set scroll region: rows 1-20 in a 24-row terminal
         feed("\033[1;20r");
         // Put some content in the scroll region
         feed("\033[1;1H");
         feed("top line content");
         feed("\033[20;1H");
         feed("bottom line content");

         // Shrink terminal from 24 rows to 10 rows
         vt100.applyResize(10, 80);

         // Send scroll down (ESC[T) — this must not crash
         feed("\033[T");

         // If we get here without AIOOB, the fix works
         assertTrue(vt100.readIn() > 0,
            "buffer should still be valid after resize + scroll");
      }

      @Test
      @DisplayName("scrollUp after vertical shrink does not crash")
      void scrollUpAfterVerticalShrink() throws Exception {
         // Set scroll region with high boundaries
         feed("\033[1;22r");
         feed("\033[10;1H");
         feed("middle content");

         // Shrink terminal drastically
         vt100.applyResize(5, 80);

         // Send scroll up (ESC[S) — must not crash
         feed("\033[S");

         assertTrue(vt100.readIn() > 0,
            "buffer should still be valid after resize + scroll");
      }

      @Test
      @DisplayName("reverseIndex after vertical shrink does not crash")
      void reverseIndexAfterVerticalShrink() throws Exception {
         // Set scroll region
         feed("\033[1;20r");
         // Move cursor to top of scroll region
         feed("\033[1;1H");

         // Shrink terminal
         vt100.applyResize(8, 80);

         // Send reverse index (ESC M) — triggers scrollRegionDown
         feed("\033M");

         assertTrue(vt100.readIn() > 0,
            "buffer should still be valid after RI + resize");
      }

      @Test
      @DisplayName("deleteLines after vertical shrink does not crash")
      void deleteLinesAfterVerticalShrink() throws Exception {
         feed("\033[1;20r");
         feed("\033[5;1H");

         vt100.applyResize(8, 80);

         // Delete lines (ESC[M)
         feed("\033[3M");

         assertTrue(vt100.readIn() > 0,
            "buffer valid after deleteLines + resize");
      }

      @Test
      @DisplayName("scroll region clamped after resize")
      void scrollRegionClampedAfterResize() throws Exception {
         // Set scroll region to rows 10-20 in 24-row terminal
         feed("\033[10;20r");

         // Shrink to 5 rows — scrollTop (10) exceeds newRows (5)
         vt100.applyResize(5, 80);

         // Multiple scrolls should all be safe
         feed("\033[3S");
         feed("\033[3T");
         feed("\033M");

         assertTrue(vt100.readIn() > 0,
            "buffer valid after clamped scroll region scrolls");
      }

      @Test
      @DisplayName("cursor X clamped after horizontal shrink")
      void cursorXClampedAfterHorizontalShrink() throws Exception {
         // Move cursor to column 70
         feed("\033[1;70H");
         assertEquals(70, cursorCol(), "cursor should be at col 70");

         // Shrink columns from 80 to 40
         vt100.applyResize(24, 40);

         // Cursor X should be clamped to new width - 1
         assertTrue(cursor().x < 40,
            "cursor X should be clamped below new column count");
      }

      @Test
      @DisplayName("cursor Y clamped after vertical shrink")
      void cursorYClampedAfterVerticalShrink() throws Exception {
         // Position cursor at row 20
         feed("\033[20;1H");

         // Shrink from 24 to 8 rows
         vt100.applyResize(8, 80);

         // Cursor must be within valid buffer range
         assertTrue(cursor().y >= 0, "cursor Y should be >= 0");
         assertTrue(cursor().y < vt100.readIn(),
            "cursor Y should be < readIn after shrink");
      }

      @Test
      @DisplayName("terminal grows — new blank lines appended")
      void terminalGrowsAppendsLines() throws Exception {
         int readInBefore = vt100.readIn();
         // Grow from 24 to 40 rows
         vt100.applyResize(40, 80);

         assertTrue(vt100.readIn() >= readInBefore,
            "readIn should not decrease when terminal grows");
         // The buffer should have at least 40+1 lines
         assertTrue(vt100.readIn() > 40,
            "buffer should have enough lines for 40-row terminal");
      }

      @Test
      @DisplayName("tab stops reinitialised on column resize")
      void tabStopsReinitOnColumnResize() throws Exception {
         // Set a custom tab stop at column 5
         feed("\033[1;6H"); // move to col 6 (0-based 5)
         feed("\033H");     // HTS: set tab at current column

         // Resize columns to 120
         vt100.applyResize(24, 120);

         // After resize, tab stops should be reinitialised to
         // default 8-column stops; the custom stop is gone.
         // Move to col 1 and tab — should land on col 9
         feed("\033[1;1H");
         feed("\t");
         assertEquals(9, cursorCol(),
            "tab should land on default stop after resize");
      }
   }

   // ── Autowrap (DECAWM) ─────────────────────────────────────

   @Nested
   @DisplayName("Autowrap — column boundary wrapping")
   class AutowrapTests {

      @Test
      @DisplayName("text wraps at column boundary")
      void textWrapsAtColumnBoundary() throws Exception {
         // 80-column terminal. Write 80 chars on row 1.
         feed("\033[1;1H"); // cursor to row 1, col 1
         StringBuilder line = new StringBuilder();
         for (int i = 0; i < 80; i++)
            line.append('A');
         feed(line.toString());
         flush();
         // After 80 chars, cursor should be at col 80 with
         // pending wrap (1-based col 80)
         assertEquals(80, cursorCol(),
            "cursor at last column after filling row");
         assertEquals(1, cursorRow(),
            "cursor still on row 1 before wrap triggers");
         // Next character triggers wrap to row 2, col 1
         feed("B");
         flush();
         assertEquals(2, cursorRow(),
            "cursor moved to row 2 after wrap");
         assertEquals(2, cursorCol(),
            "cursor at col 2 after wrap + one char");
         // Row 1 has exactly 80 A's
         String row1 = lineContent(1);
         assertTrue(row1.length() >= 80,
            "row 1 should have 80 characters");
         assertEquals('A', row1.charAt(0));
         assertEquals('A', row1.charAt(79));
         // Row 2 starts with B
         String row2 = lineContent(2);
         assertTrue(row2.startsWith("B"),
            "row 2 should start with B");
      }

      @Test
      @DisplayName("pending wrap cleared by cursor movement")
      void pendingWrapClearedByCursorMove() throws Exception {
         feed("\033[1;1H");
         StringBuilder line = new StringBuilder();
         for (int i = 0; i < 80; i++)
            line.append('X');
         feed(line.toString());
         // pendingWrap should be set. CUP escape flushes
         // and clears pendingWrap.
         feed("\033[1;1H"); // CUP to home
         assertEquals(1, cursorRow(), "cursor at row 1");
         assertEquals(1, cursorCol(), "cursor at col 1");
         // Write a character — should NOT wrap to line 2
         feed("Z");
         flush();
         assertEquals(1, cursorRow(),
            "cursor still on row 1 after CUP + char");
         assertEquals(2, cursorCol(),
            "cursor at col 2 after CUP + char");
      }

      @Test
      @DisplayName("long text wraps across multiple lines")
      void longTextWrapsMultipleLines() throws Exception {
         feed("\033[1;1H");
         // Write 200 characters — should fill 2 full lines
         // plus 40 on the third
         StringBuilder text = new StringBuilder();
         for (int i = 0; i < 200; i++)
            text.append((char) ('0' + (i % 10)));
         feed(text.toString());
         flush();
         assertEquals(3, cursorRow(),
            "cursor on row 3 after 200 chars in 80-col");
         assertEquals(41, cursorCol(),
            "cursor at col 41 (200 mod 80 = 40, +1)");
      }

      @Test
      @DisplayName("wrap scrolls at bottom margin")
      void wrapScrollsAtBottomMargin() throws Exception {
         // Set scroll region to rows 1-3
         feed("\033[1;3r");
         feed("\033[3;1H"); // cursor at row 3
         StringBuilder line = new StringBuilder();
         for (int i = 0; i < 80; i++)
            line.append('Q');
         feed(line.toString());
         // Fill the row. Now add a character — should scroll.
         feed("R");
         flush();
         // After scroll, cursor stays at bottom row
         assertEquals(3, cursorRow(),
            "after scroll, cursor stays at bottom row");
         assertEquals(2, cursorCol(),
            "cursor at col 2 after scroll + char");
      }
   }

   // ── Reverse-Wraparound (mode 45) ─────────────────────────

   @Nested
   @DisplayName("Reverse-wraparound — BS wraps to previous line")
   class ReverseWrapTests {

      @Test
      @DisplayName("BS at column 0 without mode 45 stays at 0")
      void bsAtCol0WithoutMode45StaysAtZero() throws Exception {
         feed("\033[2;1H"); // cursor at row 2, col 1
         feed("\b"); // backspace
         flush();
         assertEquals(2, cursorRow(),
            "cursor stays on row 2 without reverse wrap");
         assertEquals(1, cursorCol(),
            "cursor stays at col 1 (clamped)");
      }

      @Test
      @DisplayName("BS at column 0 with mode 45 wraps to prev line")
      void bsAtCol0WithMode45WrapsUp() throws Exception {
         // Mode 45 (inline) only wraps across autowrapped lines.
         // Fill row 1 to trigger autowrap to row 2.
         feed("[?7h");  // DECAWM on
         feed("[?45h"); // reverse-wraparound on
         feed("[1;1H");
         StringBuilder line = new StringBuilder();
         for (int i = 0; i < 80; i++)
            line.append('X');
         feed(line.toString()); // fills row 1, sets pendingWrap
         feed("Y"); // triggers autowrap to row 2
         flush();
         assertEquals(2, cursorRow(), "on row 2 after autowrap");
         // Now BS from col 1 should wrap back since row 2 was autowrapped.
         feed("[2;1H"); // cursor at row 2, col 1
         feed("");
         flush();
         assertEquals(1, cursorRow(),
            "cursor wraps to row 1 across autowrapped line");
         assertEquals(80, cursorCol(),
            "cursor at last column of previous line");
      }

      @Test
      @DisplayName("Mode 45 does NOT wrap across non-autowrapped lines")
      void mode45DoesNotWrapNonAutowrappedLine()
            throws Exception {
         // Mode 45 (inline) should NOT wrap when the current line
         // was NOT produced by autowrap.
         feed("[?7h");
         feed("[?45h");
         feed("[2;1H");
         feed("");
         flush();
         assertEquals(2, cursorRow(),
            "cursor stays on row 2 (non-autowrapped)");
         assertEquals(1, cursorCol(),
            "cursor stays at col 1");
      }

      @Test
      @DisplayName("Mode 1045 wraps even without autowrap flag")
      void mode1045WrapsWithoutAutowrapFlag()
            throws Exception {
         feed("[?7h");
         feed("[?1045h");
         feed("[2;1H");
         feed("");
         flush();
         assertEquals(1, cursorRow(),
            "cursor wraps to row 1 with mode 1045");
         assertEquals(80, cursorCol(),
            "cursor at last column");
      }

      @Test
      @DisplayName("Mode 1045 at top of scroll region wraps to bottom")
      void mode1045WrapsTopToBottom()
            throws Exception {
         feed("[?7h");
         feed("[?1045h"); // reverse-wraparound extend
         feed("[3;24r"); // set scroll region rows 3-24
         feed("[3;1H");  // cursor at top of scroll region
         feed("");
         flush();
         assertEquals(24, cursorRow(),
            "cursor wraps to bottom of scroll region");
         assertEquals(80, cursorCol(),
            "cursor at last column");
      }


      @Test
      @DisplayName("BS at pending-wrap position has no effect")
      void bsAtPendingWrapDoesNothing() throws Exception {
         feed("\033[?7h");
         feed("\033[?45h");
         feed("\033[1;79H"); // CUP row 1, col 79
         feed("a");           // col 80
         flush();
         assertEquals(80, cursorCol(),
            "after writing 'a' at col 79 cursor at col 80");
         feed("b");           // pending wrap is now set
         flush();
         assertEquals(80, cursorCol(),
            "after writing 'b' cursor stays at col 80");
         feed("\b");          // BS consumes pending wrap
         flush();
         assertEquals(80, cursorCol(),
            "BS at pending-wrap leaves cursor at col 80");
         assertEquals(1, cursorRow(),
            "BS at pending-wrap does not change row");
      }

      @Test
      @DisplayName("CUB across line boundary wraps correctly")
      void cubMultiCellWrapsAcrossLine() throws Exception {
         feed("\033[?7h");
         feed("\033[1;79H"); // CUP row 1, col 79
         feed("abcd");        // wraps after 'b' to row 2
         flush();
         feed("\033[?45h");
         // After "abcd": 'a' at (79,1), pending-wrap consumed by
         // 'b' which autowraps; 'c' at (1,2), 'd' at (2,2);
         // cursor at col 3 row 2.
         assertEquals(3, cursorCol(),
            "cursor at col 3 after autowrap of cd");
         assertEquals(2, cursorRow(),
            "cursor on row 2 after autowrap");
         feed("\033[4D");    // CUB 4
         flush();
         // x = 2 - 4 = -2 -> wrap to prev line, x = 80 + -2 = 78
         assertEquals(79, cursorCol(),
            "CUB(4) lands at col 79 of prev line");
         assertEquals(1, cursorRow(),
            "CUB(4) lands on previous row");
      }

      @Test
      @DisplayName("BS at column 0 with mode 45 but no DECAWM")
      void bsWithMode45ButNoDecawm() throws Exception {
         feed("\033[?7l");  // DECAWM off
         feed("\033[?45h"); // reverse-wraparound on
         feed("\033[2;1H");
         feed("\b");
         flush();
         // Both DECAWM and mode 45 are needed for reverse wrap
         assertEquals(2, cursorRow(),
            "cursor stays on row 2 without DECAWM");
         assertEquals(1, cursorCol(),
            "cursor clamped at col 1 without DECAWM");
      }

      @Test
      @DisplayName("DECSTR resets reverse-wraparound mode")
      void decstrResetsReverseWrap() throws Exception {
         feed("\033[?7h");
         feed("\033[?45h");
         // Soft reset
         feed("\033[!p");
         // Now reverse wrap should be off
         feed("\033[2;1H");
         feed("\b");
         flush();
         assertEquals(2, cursorRow(),
            "after DECSTR, reverse wrap is off");
         assertEquals(1, cursorCol(),
            "cursor clamped at col 1 after DECSTR");
      }

      // -- esctest2 scenario tests --

      @Test
      @DisplayName("esctest2: BS_InitialReverseWraparound — "
         + "mode 45 BS after NEL does not wrap")
      void esctest2_BS_InitialReverseWraparound() throws Exception {
         // Mode 45 (inline): BS should not wrap when the line
         // above was NOT created by autowrap (NEL ≠ autowrap).
         feed("\033[?7h");   // DECAWM on
         feed("\033[?45h");  // mode 45 on
         feed("\033[1;1H");  // CUP(1,1)
         feed("\033E");      // NEL — moves to row 2 col 1
         flush();
         assertEquals(2, cursorRow(), "NEL moves to row 2");
         feed("\b");
         flush();
         assertEquals(2, cursorRow(),
            "BS does not wrap (line was not autowrapped)");
         assertEquals(1, cursorCol(),
            "cursor stays at col 1");
      }

      @Test
      @DisplayName("esctest2: BS_ReverseWrapGoesToBottom — "
         + "mode 1045 wraps from top to bottom of scroll region")
      void esctest2_BS_ReverseWrapGoesToBottom() throws Exception {
         // Mode 1045 (extend): BS at col 1 of scroll-region top
         // wraps to last col of scroll-region bottom.
         feed("\033[?7h");    // DECAWM on
         feed("\033[?1045h"); // mode 1045 on
         feed("\033[2;5r");   // scroll region rows 2-5
         feed("\033[2;1H");   // CUP at top of scroll region
         feed("\b");
         flush();
         assertEquals(5, cursorRow(),
            "cursor wraps to bottom of scroll region");
         assertEquals(80, cursorCol(),
            "cursor at last column");
      }

      @Test
      @DisplayName("esctest2: CUB_AfterNoWrappedInlines — "
         + "mode 45 CUB stops at non-autowrapped boundary")
      void esctest2_CUB_AfterNoWrappedInlines() throws Exception {
         // Two lines written with explicit newlines (not autowrap).
         // CUB should stop at the start of the first non-autowrapped
         // line (row 4 col 1 in mode 45 with xterm_reverse_wrap>=383).
         feed("\033[?7h");
         feed("\033[?45h");
         feed("\033[3;1H");
         // Write 78 chars + newline twice (no autowrap)
         StringBuilder fill = new StringBuilder();
         for (int i = 0; i < 78; i++)
            fill.append('*');
         feed(fill.toString() + "\n");
         feed(fill.toString() + "\n");
         // Cursor now at row 5, col 1. Move to col 5.
         feed("\033[5;5H");
         flush();
         assertEquals(5, cursorRow(), "cursor at row 5");
         assertEquals(5, cursorCol(), "cursor at col 5");
         // CUB by 2 * 80 = 160 — would go way back if unconstrained
         feed("\033[160D");
         flush();
         // Mode 45: stops at start of line 4 (no autowrap flag on
         // line 3, so cannot cross from row 4 to row 3).
         assertEquals(4, cursorRow(),
            "CUB stops at row 4 (no autowrap above)");
         assertEquals(1, cursorCol(),
            "cursor at col 1");
      }

      @Test
      @DisplayName("esctest2: CUB_AfterOneWrappedInline — "
         + "mode 45 CUB wraps through autowrap chain then stops")
      void esctest2_CUB_AfterOneWrappedInline() throws Exception {
         // Write enough chars to autowrap across lines, then a
         // CR+LF to break the chain, then another autowrap sequence.
         // CUB should wrap within the second chain but stop at its
         // start because the line before the chain was not autowrapped.
         feed("\033[?7h");
         feed("\033[?45h");
         feed("\033[3;1H");
         // 162 chars = 80 + 80 + 2 → autowraps rows 3→4, 4→5
         StringBuilder fill = new StringBuilder();
         for (int i = 0; i < 162; i++)
            fill.append('*');
         String fillStr = fill.toString();
         feed(fillStr);
         flush();
         assertEquals(5, cursorRow(), "first fill ends on row 5");
         assertEquals(3, cursorCol(), "first fill cursor at col 3");
         // CR+LF to move to next line col 1 (breaks autowrap chain)
         feed("\r\n");
         flush();
         assertEquals(6, cursorRow(), "after CR+LF on row 6");
         assertEquals(1, cursorCol(), "after CR+LF at col 1");
         // Second fill: rows 6→7, 7→8, cursor at (8, col 3)
         feed(fillStr);
         flush();
         assertEquals(8, cursorRow(), "second fill ends on row 8");
         assertEquals(3, cursorCol(), "second fill cursor at col 3");
         // CUB by 5*80=400
         feed("\033[400D");
         flush();
         // Should stop at row 6 col 1 — cannot cross into row 5
         // because row 5 was NOT autowrapped (the CR+LF broke it).
         assertEquals(6, cursorRow(),
            "CUB stops at start of second autowrap chain");
         assertEquals(1, cursorCol(),
            "cursor at col 1");
      }

      @Test
      @DisplayName("esctest2: DECSET_ReverseWraparoundLastCol_BS — "
         + "mode 1045 BS at pending wrap does nothing")
      void esctest2_DECSET_ReverseWraparoundLastCol_BS() throws Exception {
         // With mode 1045: write at col 79 and 80, BS should
         // just consume the pending wrap (cursor stays at col 80).
         feed("\033[?1045h");
         feed("\033[?7h");
         feed("\033[1;79H"); // CUP row 1, col 79
         feed("a");          // writes at col 79, cursor at col 80
         flush();
         assertEquals(80, cursorCol(), "cursor at col 80 after 'a'");
         feed("b");          // writes at col 80, pendingWrap set
         flush();
         assertEquals(80, cursorCol(), "cursor still at col 80 after 'b'");
         feed("\b");         // BS consumes pending wrap
         flush();
         assertEquals(80, cursorCol(),
            "BS at pending-wrap leaves cursor at col 80");
         assertEquals(1, cursorRow(),
            "cursor stays on row 1");
      }

      @Test
      @DisplayName("esctest2: DECSET_ReverseWraparound_Multi — "
         + "mode 1045 CUB wraps back across autowrap")
      void esctest2_DECSET_ReverseWraparound_Multi() throws Exception {
         // Write "abcd" starting at col 79: 'a' at 79, 'b' at 80,
         // autowrap, 'c' at 1, 'd' at 2. CUB(4) wraps back to col 79.
         feed("\033[1;79H"); // CUP row 1, col 79
         feed("abcd");       // autowraps after 'b'
         flush();
         feed("\033[?1045h");
         feed("\033[?7h");
         feed("\033[4D");    // CUB 4
         flush();
         assertEquals(79, cursorCol(),
            "CUB(4) wraps to col 79 of previous line");
         assertEquals(1, cursorRow(),
            "CUB(4) lands on row 1");
      }
   }


   @Nested
   @DisplayName("Resize — scroll region and cursor clamping")
   class ResizeTests {

      @Test
      @DisplayName("scroll region reset when bottom exceeds new height")
      void scrollRegionResetOnShrink() throws Exception {
         // Set scroll region to rows 5-24 (24 is the full height)
         feed("\033[5;24r");
         // Resize to 15 rows — scrollBottom was 24 which
         // exceeds 15, so it should be reset to full screen.
         vt100.applyResize(15, 80);

         // Cursor at row 15, IND should scroll within 1-15
         feed("\033[15;1H");
         feed("before-scroll");
         flush();

         // IND at bottom of full region
         feed("\033D");
         flush();
         assertEquals(15, cursorRow(),
            "cursor stays at bottom after IND");
      }

      @Test
      @DisplayName("resize preserves cursor content")
      void resizePreservesCursorContent() throws Exception {
         // Write text at known position
         feed("\033[5;1HResize Test Line");
         flush();
         assertEquals("Resize Test Line",
            lineContent(5).trim());

         // Grow terminal from 24 to 40 rows
         vt100.applyResize(40, 80);

         // Text should still be present
         assertEquals("Resize Test Line",
            lineContent(5).trim());
      }
   }

   // ── Alternate screen attribute isolation ──────────────────

   @Nested
   @DisplayName("Alternate screen — attribute save/restore")
   class AltScreenAttrTests {

      @Test
      @DisplayName("main screen attrs survive alternate screen session")
      void mainScreenAttrsSurviveAltScreen() throws Exception {
         // Set bold red text on main screen line 3
         feed("\033[1;31m");
         feed("\033[3;1Hbold text");
         flush();

         ScreenAttributes sa = vt100.getScreenAttributes();
         int mainAbsLine = vt100.readIn() - 1 - 24 + 3;
         int attrBefore = sa.getAttr(mainAbsLine, 0);
         assertTrue(CellAttr.isBold(attrBefore),
            "main screen should have bold attr");

         // Enter alternate screen (mode 1049)
         feed("\033[?1049h");
         flush();

         // Write different attrs on alternate screen
         feed("\033[0m");
         feed("\033[3;1Hplain text");
         flush();

         // Exit alternate screen (mode 1049)
         feed("\033[?1049l");
         flush();

         // Main screen attributes should be restored
         int attrAfter = sa.getAttr(mainAbsLine, 0);
         assertEquals(attrBefore, attrAfter,
            "main screen attr should be restored after alt screen");
      }

      @Test
      @DisplayName("alternate screen attrs do not pollute main screen")
      void altScreenDoesNotPolluteMainAttrs() throws Exception {
         // Ensure main screen has no attrs on line 5
         ScreenAttributes sa = vt100.getScreenAttributes();
         int mainAbsLine = vt100.readIn() - 1 - 24 + 5;
         assertEquals(CellAttr.DEFAULT, sa.getAttr(mainAbsLine, 0),
            "main screen should have no attrs initially");

         // Enter alternate screen
         feed("\033[?1049h");
         flush();

         // Set colored attrs on alternate screen line 5
         feed("\033[42m"); // green bg
         feed("\033[5;1Hgreen bg text");
         flush();

         // Exit alternate screen
         feed("\033[?1049l");
         flush();

         // Main screen should NOT have green bg
         int attrAfter = sa.getAttr(mainAbsLine, 0);
         assertEquals(CellAttr.DEFAULT, attrAfter,
            "alt screen attrs should not leak to main screen");
      }
   }

   // ── Erase scrollback (ESC[3J) ────────────────────────────

   @Nested
   @DisplayName("ESC[3J — erase scrollback buffer")
   class EraseScrollbackTests {

      @Test
      @DisplayName("ED3 is no-op — preserves scrollback for clear")
      void ed3PreservesScrollback() throws Exception {
         // ED3 is intentionally a no-op: macOS 'clear' sends
         // ESC[H ESC[2J ESC[3J and we want scrollback preserved.
         for (int i = 0; i < 10; i++)
            vt100.insertOne("scrollback" + i, vt100.readIn());
         feed("\033[1;1HvisibleTop");
         flush();

         int readInBefore = vt100.readIn();
         assertTrue(readInBefore > 25,
            "should have scrollback lines");

         // ESC[3J — should be no-op
         feed("\033[3J");
         flush();

         assertEquals(readInBefore, vt100.readIn(),
            "readIn unchanged — ED3 is no-op");
         String topLine = lineContent(1);
         assertTrue(topLine.startsWith("visibleTop"),
            "visible content should survive ED3");
      }

      @Test
      @DisplayName("no-op when no scrollback exists")
      void noOpWithoutScrollback() throws Exception {
         int readInBefore = vt100.readIn();
         feed("\033[3J");
         flush();
         assertEquals(readInBefore, vt100.readIn(),
            "readIn should not change when no scrollback");
      }

      @Test
      @DisplayName("cursor position preserved after erase")
      void cursorPreservedAfterErase() throws Exception {
         // Add scrollback first
         for (int i = 0; i < 10; i++)
            vt100.insertOne("extra" + i, vt100.readIn());

         // Position cursor in the visible area (terminal row 5)
         feed("\033[5;3H");
         flush();
         int rowBefore = cursorRow();
         int colBefore = cursorCol();
         assertEquals(5, rowBefore, "sanity: cursor at row 5");

         feed("\033[3J");
         flush();

         assertEquals(rowBefore, cursorRow(),
            "cursor row should be preserved");
         assertEquals(colBefore, cursorCol(),
            "cursor col should be preserved");
      }
   }

   // ── Alternate screen cursor restore ──────────────────────

   @Nested
   @DisplayName("Alternate screen — cursor restore after buffer growth")
   class AltScreenCursorTests {

      @Test
      @DisplayName("cursor restored to visible area after buffer growth")
      void cursorRestoredAfterBufferGrowth() throws Exception {
         // Position cursor at row 10, col 5
         feed("\033[10;5H");
         flush();

         // Enter alternate screen
         feed("\033[?1049h");
         flush();

         // Simulate buffer growth during alt screen (e.g. from
         // autowrap or index operations that add lines)
         for (int i = 0; i < 15; i++)
            vt100.insertOne("growth" + i, vt100.readIn());

         // Exit alternate screen
         feed("\033[?1049l");
         flush();

         // Cursor should be in the visible area
         int row = cursorRow();
         assertTrue(row >= 1 && row <= 24,
            "cursor row " + row
               + " should be within visible area (1-24)");
         assertEquals(5, cursorCol(),
            "cursor column should be restored");
      }

      @Test
      @DisplayName("cursor row matches saved position without growth")
      void cursorRowMatchesWithoutGrowth() throws Exception {
         // Position cursor at row 12, col 8
         feed("\033[12;8H");
         flush();
         int savedRow = cursorRow();
         int savedCol = cursorCol();

         // Enter and immediately exit alternate screen (no growth)
         feed("\033[?1049h");
         flush();
         feed("\033[?1049l");
         flush();

         assertEquals(savedRow, cursorRow(),
            "cursor row should match saved position");
         assertEquals(savedCol, cursorCol(),
            "cursor col should match saved position");
      }
   }

   // ── DCH/ICH attribute shifting ───────────────────────────

   @Nested
   @DisplayName("DCH/ICH — character attribute shifting")
   class DchIchAttrTests {

      @Test
      @DisplayName("DCH shifts colored attrs left")
      void dchShiftsAttrsLeft() throws Exception {
         // Write "ABCDE" with red foreground at row 1
         feed("\033[31m");     // red fg
         feed("\033[1;1H");    // home
         feed("ABCDE");
         flush();

         ScreenAttributes sa = vt100.getScreenAttributes();
         int absLine = vt100.readIn() - 1 - 24 + 1;
         int redAttr = sa.getAttr(absLine, 0);
         assertTrue(redAttr != CellAttr.DEFAULT,
            "should have non-default attr after SGR 31");

         // Position at col 2 and delete 2 chars
         feed("\033[1;2H");    // row 1, col 2 (0-based col 1)
         feed("\033[2P");      // DCH 2
         flush();

         // "BCD" → "D"; line becomes "ADE__"
         // Col 0 should still be red (untouched)
         assertEquals(redAttr, sa.getAttr(absLine, 0),
            "col 0 attr should be preserved");
         // Cols 1-2 should be the shifted red attrs from cols 3-4
         assertEquals(redAttr, sa.getAttr(absLine, 1),
            "col 1 should have attr shifted from col 3");
         assertEquals(redAttr, sa.getAttr(absLine, 2),
            "col 2 should have attr shifted from col 4");
      }

      @Test
      @DisplayName("ICH shifts colored attrs right")
      void ichShiftsAttrsRight() throws Exception {
         // Write "ABC" with green foreground at row 2
         feed("\033[32m");     // green fg
         feed("\033[2;1H");    // row 2
         feed("ABC");
         flush();

         ScreenAttributes sa = vt100.getScreenAttributes();
         int absLine = vt100.readIn() - 1 - 24 + 2;
         int greenAttr = sa.getAttr(absLine, 0);
         assertTrue(greenAttr != CellAttr.DEFAULT,
            "should have non-default attr after SGR 32");

         // Position at col 1 and insert 2 blanks
         feed("\033[2;1H");    // row 2, col 1
         feed("\033[2@");      // ICH 2
         flush();

         // Original "A" at col 0 was shifted to col 2
         // Cols 0-1 should have current bg attr (insert fill)
         // Cols 2-4 should have green attrs shifted from 0-2
         assertEquals(greenAttr, sa.getAttr(absLine, 2),
            "col 2 should have shifted green attr from col 0");
         assertEquals(greenAttr, sa.getAttr(absLine, 3),
            "col 3 should have shifted green attr from col 1");
      }
   }

   // ── Wide character (wcwidth) integration ──────────────────

   @Nested
   @DisplayName("Wide character (wcwidth) integration")
   class WideCharTests {

      @Test
      @DisplayName("CJK char advances cursor by 2 columns")
      void cjkAdvancesCursorBy2() throws Exception {
         feed("\033[2;1H"); // row 2, col 1
         feed("\u4E00");    // CJK ideograph (width 2)
         flush();
         assertEquals(3, cursorCol(),
            "cursor should be at column 3 after 1 wide char");
      }

      @Test
      @DisplayName("CJK char inserts padding in buffer")
      void cjkInsertsPadding() throws Exception {
         feed("\033[2;1H");
         feed("\u4E00");
         flush();
         String line = lineContent(2);
         assertEquals(2, line.length(),
            "buffer should have 2 positions (char + pad)");
         assertEquals('\u4E00', line.charAt(0));
         assertEquals('\0', line.charAt(1));
      }

      @Test
      @DisplayName("mixed ASCII and CJK tracks cursor correctly")
      void mixedAsciiCjkCursor() throws Exception {
         feed("\033[2;1H");
         feed("A\u4E00B"); // A(1) + CJK(2) + B(1) = 4 cols
         flush();
         assertEquals(5, cursorCol(),
            "cursor at col 5 after 4 display columns");
         String line = lineContent(2);
         // Buffer: A + \u4E00 + \0 + B = 4 chars
         assertEquals(4, line.length());
         assertEquals('A', line.charAt(0));
         assertEquals('\u4E00', line.charAt(1));
         assertEquals('\0', line.charAt(2));
         assertEquals('B', line.charAt(3));
      }

      @Test
      @DisplayName("CJK at column boundary triggers autowrap")
      void wideCharWrapsAtBoundary() throws Exception {
         // Position at second-to-last column (col 79 of 80)
         feed("\033[2;79H");
         flush();
         assertEquals(79, cursorCol());
         // Write a wide char (needs 2 cols but only 2 available)
         feed("\u4E00");
         flush();
         // Should fit: cols 79-80
         String line2 = lineContent(2);
         assertTrue(line2.length() >= 80,
            "wide char should fit in last 2 columns");
      }

      @Test
      @DisplayName("CJK char that doesn't fit wraps to next line")
      void wideCharNoFitWraps() throws Exception {
         // Position at last column (col 80 of 80)
         feed("\033[2;80H");
         flush();
         assertEquals(80, cursorCol());
         // Write a wide char — only 1 column left, need 2
         feed("\u4E00");
         flush();
         // The pending wrap + wide char should move to row 3
         assertEquals(3, cursorRow(),
            "wide char should wrap to next line");
         assertEquals(3, cursorCol(),
            "cursor on next line after wide char");
      }

      @Test
      @DisplayName("cursor positioning after CJK is column-accurate")
      void cupAfterCjk() throws Exception {
         feed("\033[2;1H");
         feed("\u4E00\u4E00"); // 2 CJK = 4 columns
         flush();
         assertEquals(5, cursorCol());
         // Move cursor back to column 3 (the padding position)
         feed("\033[2;3H");
         flush();
         assertEquals(3, cursorCol(),
            "CUP should position at column 3");
      }

      @Test
      @DisplayName("DCH at WIDE_PAD replaces orphaned wide char")
      void dchAtWidePadReplacesWideChar() throws Exception {
         // Write "A" + CJK + "B" → buffer: A 韓 \0 B
         feed("\033[2;1H");
         feed("A\u97E9B");
         flush();
         String before = lineContent(2);
         assertEquals('A', before.charAt(0));
         assertEquals('\u97E9', before.charAt(1));
         assertEquals('\0', before.charAt(2));
         assertEquals('B', before.charAt(3));
         // Position cursor at col 3 (the WIDE_PAD position)
         feed("\033[2;3H");
         // Delete 1 char — removes the WIDE_PAD, orphaning the wide
         feed("\033[1P");
         flush();
         String after = lineContent(2);
         // The wide char at position 1 should become a space
         assertEquals('A', after.charAt(0));
         assertEquals(' ', after.charAt(1),
            "orphaned wide char should become space");
         assertEquals('B', after.charAt(2));
      }

      @Test
      @DisplayName("DCH splitting a wide char at end replaces orphaned pad")
      void dchSplitsWideAtEnd() throws Exception {
         // Write CJK + CJK → buffer: 韓 \0 中 \0
         feed("\033[2;1H");
         feed("\u97E9\u4E2D");
         flush();
         // Position cursor at col 1, delete 2 chars
         // Removes 韓 \0 — the 中 \0 shifts left, intact
         feed("\033[2;1H");
         feed("\033[2P");
         flush();
         String after = lineContent(2);
         assertEquals('\u4E2D', after.charAt(0));
         assertEquals('\0', after.charAt(1));
      }

      @Test
      @DisplayName("DCH deleting one char of wide pair orphans pad")
      void dchDeletesWideCharOrphansPad() throws Exception {
         // Buffer: A 韓 \0 B (cols: A=1, 韓=2-3, B=4)
         feed("\033[2;1H");
         feed("A\u97E9B");
         flush();
         // Position at col 2 (the wide char itself), delete 1
         feed("\033[2;2H");
         feed("\033[1P");
         flush();
         String after = lineContent(2);
         // The WIDE_PAD that was after the wide char becomes orphaned
         assertEquals('A', after.charAt(0));
         assertEquals(' ', after.charAt(1),
            "orphaned WIDE_PAD should become space");
         assertEquals('B', after.charAt(2));
      }

      @Test
      @DisplayName("ICH at WIDE_PAD splits wide char")
      void ichAtWidePadSplitsWideChar() throws Exception {
         // Buffer: 韓 \0 B
         feed("\033[2;1H");
         feed("\u97E9B");
         flush();
         // Position at col 2 (WIDE_PAD), insert 1 char
         feed("\033[2;2H");
         feed("\033[1@");
         flush();
         String after = lineContent(2);
         // The wide char at pos 0 lost its padding → space
         assertEquals(' ', after.charAt(0),
            "split wide char should become space");
         assertEquals(' ', after.charAt(1),
            "inserted space at cursor position");
      }

      @Test
      @DisplayName("ICH truncation at wide char boundary")
      void ichTruncatesWideChar() throws Exception {
         // Fill line with A's, put CJK near end
         // 80-col terminal: put CJK at cols 79-80
         feed("\033[2;79H");
         feed("\u4E00");
         flush();
         String before = lineContent(2);
         // Verify CJK is at positions 78,79
         assertEquals('\u4E00', before.charAt(78));
         assertEquals('\0', before.charAt(79));
         // Insert 1 char at col 79 — pushes CJK right,
         // but WIDE_PAD would be at col 81 (past end)
         feed("\033[2;79H");
         feed("\033[1@");
         flush();
         String after = lineContent(2);
         // The wide char pushed to col 80 can't keep its pad
         // → should become space
         int lastIdx = after.length() - 1;
         assertEquals(' ', after.charAt(lastIdx),
            "wide char truncated at margin should become space");
      }

      @Test
      @DisplayName("ECH at WIDE_PAD fixes orphaned wide char")
      void echAtWidePadFixesWideChar() throws Exception {
         // Buffer: A 韓 \0 B
         feed("\033[2;1H");
         feed("A\u97E9B");
         flush();
         // Position at col 3 (WIDE_PAD), erase 1 char
         feed("\033[2;3H");
         feed("\033[1X");
         flush();
         String after = lineContent(2);
         assertEquals('A', after.charAt(0));
         assertEquals(' ', after.charAt(1),
            "wide char whose pad was erased should become space");
         assertEquals(' ', after.charAt(2),
            "erased position should be space");
         assertEquals('B', after.charAt(3));
      }

      @Test
      @DisplayName("ECH end boundary splits wide char")
      void echEndSplitsWideChar() throws Exception {
         // Buffer: A 韓 \0 B
         feed("\033[2;1H");
         feed("A\u97E9B");
         flush();
         // Position at col 1, erase 2 chars (positions 0,1)
         // This erases A and 韓 but leaves \0 at position 2
         feed("\033[2;1H");
         feed("\033[2X");
         flush();
         String after = lineContent(2);
         assertEquals(' ', after.charAt(0));
         assertEquals(' ', after.charAt(1));
         // The orphaned WIDE_PAD should also become space
         assertEquals(' ', after.charAt(2),
            "orphaned WIDE_PAD after erase should become space");
         assertEquals('B', after.charAt(3));
      }

      @Test
      @DisplayName("EL 0 at WIDE_PAD fixes orphaned wide char")
      void eraseToEndAtWidePad() throws Exception {
         // Buffer: 韓 \0 B
         feed("\033[2;1H");
         feed("\u97E9B");
         flush();
         // Position at col 2 (WIDE_PAD), erase to end
         feed("\033[2;2H");
         feed("\033[0K");
         flush();
         String after = lineContent(2);
         // Wide char at pos 0 lost its WIDE_PAD → space
         assertEquals(' ', after.charAt(0),
            "wide char split by EL 0 should become space");
      }

      @Test
      @DisplayName("EL 1 end splits wide char pair")
      void eraseToBeginSplitsWideChar() throws Exception {
         // Buffer: A 韓 \0 B
         feed("\033[2;1H");
         feed("A\u97E9B");
         flush();
         // Position at col 2 (the wide char), erase to beginning
         // Erases positions 0,1 (A, 韓). WIDE_PAD at pos 2 orphaned.
         feed("\033[2;2H");
         feed("\033[1K");
         flush();
         String after = lineContent(2);
         assertEquals(' ', after.charAt(0));
         assertEquals(' ', after.charAt(1));
         // Orphaned WIDE_PAD at pos 2 should be fixed
         assertEquals(' ', after.charAt(2),
            "orphaned WIDE_PAD after EL 1 should become space");
         assertEquals('B', after.charAt(3));
      }
   }

   // -- Alt screen correctness ----

   @Nested
   @DisplayName("Alt screen save/restore correctness")
   class AltScreenCorrectnessTests {

      @Test
      @DisplayName("mode 1049 saves exactly visible rows")
      void altScreenSavesExactRows() throws Exception {
         for (int r = 1; r <= 24; r++) {
            feed("\033[" + r + ";1H");
            feed("ROW" + r);
         }
         flush();
         assertEquals("ROW1", lineContent(1).substring(0, 4));
         assertEquals("ROW24",
            lineContent(24).substring(0, 5));
         feed("\033[?1049h");
         flush();
         assertEquals("", lineContent(1));
         feed("\033[1;1Halt screen content");
         flush();
         feed("\033[?1049l");
         flush();
         assertEquals("ROW1",
            lineContent(1).substring(0, 4),
            "row 1 should be restored");
         assertEquals("ROW24",
            lineContent(24).substring(0, 5),
            "row 24 should be restored");
      }

      @Test
      @DisplayName("mode 1049 restores cursor via DECRC")
      void altScreenRestoresCursorViaDECRC() throws Exception {
         feed("\033[10;15H");
         flush();
         assertEquals(10, cursorRow());
         assertEquals(15, cursorCol());
         feed("\033[?1049h");
         flush();
         feed("\033[20;40H");
         flush();
         feed("\033[?1049l");
         flush();
         assertEquals(10, cursorRow(),
            "cursor row restored via DECRC");
         assertEquals(15, cursorCol(),
            "cursor col restored via DECRC");
      }

      @Test
      @DisplayName("mode 1049 restores SGR via DECRC")
      void altScreenRestoresSgrViaDECRC() throws Exception {
         feed("\033[1;31m");
         feed("\033[5;1Hbold red");
         flush();
         Field ecField =
            Vt100.class.getDeclaredField("ecscreen");
         ecField.setAccessible(true);
         Object ecscreen = ecField.get(vt100);
         Field attrField = ecscreen.getClass()
            .getDeclaredField("currentAttr");
         attrField.setAccessible(true);
         int attrBefore = attrField.getInt(ecscreen);
         assertTrue(CellAttr.isBold(attrBefore),
            "should be bold before alt screen");
         feed("\033[?1049h");
         flush();
         feed("\033[0m");
         feed("plain text");
         flush();
         int attrDuring = attrField.getInt(ecscreen);
         assertEquals(CellAttr.DEFAULT, attrDuring,
            "SGR should be reset during alt screen");
         feed("\033[?1049l");
         flush();
         int attrAfter = attrField.getInt(ecscreen);
         assertEquals(attrBefore, attrAfter,
            "SGR restored after alt screen exit");
      }

		@Test
		@DisplayName("mode 1049 restores content and cursor in one batch")
		void altScreenViLikeExit() throws Exception {
			// Simulate vi workflow: populate main screen, enter alt
			// screen, write content, exit alt screen - verify both
			// screen content and cursor are correct (Bug 1 regression).
			feed("\033[5;1H");
			feed("MAIN LINE 5");
			feed("\033[1;1H");
			feed("MAIN LINE 1");
			flush();
			assertEquals("MAIN LINE 1",
				lineContent(1).substring(0, 11));
			assertEquals("MAIN LINE 5",
				lineContent(5).substring(0, 11));
			// Enter alt screen (DECSC + alt + clear)
			feed("\033[?1049h");
			flush();
			assertEquals("", lineContent(1),
				"alt screen should be blank");
			// Write alt screen content like vi does
			feed("\033[1;1Halt content");
			feed("\033[24;1Hstatus line");
			flush();
			// Exit alt screen (alt off + DECRC)
			feed("\033[?1049l");
			flush();
			assertEquals("MAIN LINE 1",
				lineContent(1).substring(0, 11),
				"main screen row 1 content restored");
			assertEquals("MAIN LINE 5",
				lineContent(5).substring(0, 11),
				"main screen row 5 content restored");
			assertEquals(1, cursorRow(),
				"cursor row restored to DECSC position");
			assertEquals(12, cursorCol(),
				"cursor col restored after MAIN LINE 1");
		}


      @Test
      @DisplayName("mode 47 does not do DECSC/DECRC")
      void mode47NoSaveCursor() throws Exception {
         feed("\033[8;12H");
         flush();
         feed("\033[?47h");
         flush();
         feed("\033[3;5H");
         flush();
         feed("\033[?47l");
         flush();
         assertEquals(8, cursorRow(),
            "mode 47 restores internal cursor");
      }
   }

   @Nested
   @DisplayName("ED erase with background color")
   class EdBgAttrTests {

      @Test
      @DisplayName("ED 0 fills with current background color")
      void eraseScreenToEndUsesBgAttr() throws Exception {
         feed("\033[42m");
         feed("\033[5;10H");
         feed("\033[0J");
         flush();
         ScreenAttributes sa = vt100.getScreenAttributes();
         int absLine = vt100.readIn() - 1 - 24 + 10;
         int attr = sa.getAttr(absLine, 0);
         int bgColor = CellAttr.bgColor(attr);
         assertEquals(2, bgColor,
            "erased line should have green bg (2)");
      }

      @Test
      @DisplayName("ED 1 fills with current background color")
      void eraseScreenToBeginUsesBgAttr() throws Exception {
         feed("\033[41m");
         feed("\033[20;5H");
         feed("\033[1J");
         flush();
         ScreenAttributes sa = vt100.getScreenAttributes();
         int absLine = vt100.readIn() - 1 - 24 + 5;
         int attr = sa.getAttr(absLine, 0);
         int bgColor = CellAttr.bgColor(attr);
         assertEquals(1, bgColor,
            "erased line should have red bg (1)");
      }
   }

   // -- ESC 8 (DECRC) state transition -------------------------

   @Nested
   @DisplayName("DECRC -- parser state after ESC 8")
   class DecrcStateTests {

      @Test
      @DisplayName("ESC 8 returns parser to NORM state")
      void decrcReturnsToNormState() throws Exception {
         // Save cursor at row 5
         feed("\033[5;1H");
         feed("\0337"); // DECSC (ESC 7)
         // Move cursor to row 10
         feed("\033[10;1H");
         flush();
         assertEquals(10, cursorRow());
         // Restore cursor, then write text.
         // If ESC 8 doesn't set state=NORM, 'H' is consumed
         // as an escape code instead of being written.
         feed("\0338Hello");
         flush();
         assertEquals(5, cursorRow(),
            "cursor restored to row 5");
         String line = lineContent(5);
         assertTrue(line.contains("Hello"),
            "text after ESC 8 should be written: " + line);
      }

      @Test
      @DisplayName("ESC 8 does not swallow next character")
      void decrcDoesNotSwallowChar() throws Exception {
         feed("\033[3;1H");
         feed("\0337"); // save
         feed("\033[12;1H");
         flush();
         // ESC 8 followed immediately by 'p' -- 'p' must appear
         feed("\0338p");
         flush();
         String line = lineContent(3);
         assertTrue(line.startsWith("p"),
            "char after ESC 8 must not be swallowed: " + line);
      }
   }

   // -- VT/FF as LF -------------------------------------------

   @Nested
   @DisplayName("VT and FF behave as LF")
   class VtFfAsLfTests {

      @Test
      @DisplayName("VT (0x0B) moves cursor down like LF")
      void vtMovesDown() throws Exception {
         feed("\033[3;1H");
         feed("line3");
         feed("\013"); // VT = 0x0B
         flush();
         assertEquals(4, cursorRow(),
            "VT should move cursor to next row");
      }

      @Test
      @DisplayName("FF (0x0C) moves cursor down like LF")
      void ffMovesDown() throws Exception {
         feed("\033[3;1H");
         feed("line3");
         feed("\014"); // FF = 0x0C
         flush();
         assertEquals(4, cursorRow(),
            "FF should move cursor to next row");
      }

      @Test
      @DisplayName("VT does not insert visible character")
      void vtNotVisible() throws Exception {
         feed("\033[5;1H");
         feed("before");
         feed("\013"); // VT
         feed("after");
         flush();
         String row5 = lineContent(5);
         assertTrue(row5.startsWith("before"),
            "row 5 should have 'before': " + row5);
         String row6 = lineContent(6);
         assertTrue(row6.contains("after"),
            "row 6 should have 'after': " + row6);
      }
   }


   // -- CUP parameter clamping --------------------------------

   @Nested
   @DisplayName("CUP parameter 0 treated as 1")
   class CupClampTests {

      @Test
      @DisplayName("CUP with x=0 clamps to column 1")
      void cupXZeroClampsToCol1() throws Exception {
         feed("\033[5;0H");
         flush();
         assertEquals(1, cursorCol(),
            "CUP x=0 should clamp to column 1");
         assertEquals(5, cursorRow(),
            "CUP y=5 should be row 5");
      }

      @Test
      @DisplayName("CUP with y=0 clamps to row 1")
      void cupYZeroClampsToRow1() throws Exception {
         feed("\033[0;10H");
         flush();
         assertEquals(1, cursorRow(),
            "CUP y=0 should clamp to row 1");
         assertEquals(10, cursorCol(),
            "CUP x=10 should be column 10");
      }

      @Test
      @DisplayName("CHA with param 0 clamps to column 1")
      void chaZeroClampsToCol1() throws Exception {
         feed("\033[5;20H");
         feed("\033[0G");
         flush();
         assertEquals(1, cursorCol(),
            "CHA 0 should clamp to column 1");
      }
   }

   // -- ECH attribute handling ---------------------------------

   @Nested
   @DisplayName("ECH uses bgOnly attribute")
   class EchAttrTests {

      @Test
      @DisplayName("ECH fills erased cells with bg-only attribute")
      void echUsesBgOnlyAttr() throws Exception {
         // Set bold + green fg + red bg
         feed("\033[1;32;41m");
         feed("\033[8;1HXXXXXXXXXX");
         flush();
         // Now erase 5 chars at column 3
         feed("\033[8;3H");
         feed("\033[5X");
         flush();
         ScreenAttributes sa = vt100.getScreenAttributes();
         int absLine = vt100.readIn() - 1 - 24 + 8;
         int attr = sa.getAttr(absLine, 3);
         // Erased cells should have only bg color, not bold/fg
         int bgColor = CellAttr.bgColor(attr);
         assertEquals(1, bgColor,
            "erased cell should have red bg (1)");
         assertTrue(!CellAttr.isBold(attr),
            "erased cell should not be bold");
         int fgColor = CellAttr.fgColor(attr);
         assertEquals(-1, fgColor,
            "erased cell should have default fg (-1)");
      }
   }

   // -- Emoji / supplementary character cursor tracking --------

   @Nested
   @DisplayName("Emoji cursor position")
   class EmojiCursorTests {

      // U+1F600 grinning face (supplementary, width 2)
      private final String GRIN =
         new String(Character.toChars(0x1F600));
      // U+4E00 CJK ideograph (BMP, width 2)
      private final String CJK = "\u4E00";

      @Test
      @DisplayName("cursor advances by 2 after supplementary emoji")
      void cursorAfterSingleEmoji() throws Exception {
         feed(GRIN);
         flush();
         assertEquals(3, cursorCol(),
            "cursor should be at column 3 (1 + width 2)");
      }

      @Test
      @DisplayName("cursor after emoji + ASCII matches display width")
      void cursorAfterEmojiAndAscii() throws Exception {
         feed(GRIN + "abc");
         flush();
         // emoji (2) + 3 ASCII = 5 display cols, cursor at 6
         assertEquals(6, cursorCol(),
            "cursor should be at column 6");
      }

      @Test
      @DisplayName("cursor after multiple emoji")
      void cursorAfterMultipleEmoji() throws Exception {
         String rocket = new String(Character.toChars(0x1F680));
         feed(GRIN + rocket + "x");
         flush();
         // 2 + 2 + 1 = 5 display cols, cursor at 6
         assertEquals(6, cursorCol(),
            "cursor should be at column 6");
      }

      @Test
      @DisplayName("emoji + BMP wide + ASCII cursor is correct")
      void mixedBmpAndSupplementary() throws Exception {
         feed(CJK + GRIN + "A");
         flush();
         // CJK (2) + emoji (2) + A (1) = 5, cursor at 6
         assertEquals(6, cursorCol(),
            "cursor should be at column 6");
      }

      @Test
      @DisplayName("CUF after emoji moves by display columns")
      void cufAfterEmoji() throws Exception {
         // Place cursor at (1,1), write emoji, then CUF 2
         feed("\033[1;1H");
         feed(GRIN);
         flush();
         // Cursor at col 3 after emoji
         assertEquals(3, cursorCol());
         // Move right 2 columns
         feed("\033[2C");
         flush();
         assertEquals(5, cursorCol(),
            "CUF 2 should land at column 5");
      }
   }


   @Nested
   @DisplayName("OSC Color Query Responses")
   class OscColorQueryTests {

      private java.io.ByteArrayOutputStream captureOut;
      private java.io.OutputStreamWriter origWriter;

      @BeforeEach
      void swapWriter() throws Exception {
         captureOut = new java.io.ByteArrayOutputStream();
         Field writerField = Vt100.class.getDeclaredField("writer");
         writerField.setAccessible(true);
         origWriter = (java.io.OutputStreamWriter) writerField.get(vt100);
         writerField.set(vt100,
            new java.io.OutputStreamWriter(captureOut,
               StandardCharsets.UTF_8));
      }

      @AfterEach
      void restoreWriter() throws Exception {
         if (origWriter != null) {
            Field writerField = Vt100.class.getDeclaredField("writer");
            writerField.setAccessible(true);
            writerField.set(vt100, origWriter);
         }
      }

      private String captured() {
         return captureOut.toString(StandardCharsets.UTF_8);
      }

      @Test
      @DisplayName("OSC 4;N;? queries palette color")
      void queryPaletteColor() throws Exception {
         feed("\033]4;0;?\033\\");
         assertEquals("\033]4;0;rgb:0000/0000/0000\033\\",
            captured());
      }

      @Test
      @DisplayName("OSC 4 set then query palette color")
      void setPaletteAndQuery() throws Exception {
         feed("\033]4;0;rgb:f0f0/f0f0/f0f0\033\\");
         assertEquals("", captured(), "set should not respond");
         captureOut.reset();
         feed("\033]4;0;?\033\\");
         assertEquals("\033]4;0;rgb:f0f0/f0f0/f0f0\033\\",
            captured());
      }

      @Test
      @DisplayName("OSC 4 multiple query returns two responses")
      void multipleQuery() throws Exception {
         feed("\033]4;0;?;1;?\033\\");
         String resp = captured();
         assertTrue(resp.contains("\033]4;0;"),
            "should contain color 0 response");
         assertTrue(resp.contains("\033]4;1;"),
            "should contain color 1 response");
      }

      @Test
      @DisplayName("OSC 10;? queries dynamic foreground color")
      void queryDynamicForeground() throws Exception {
         feed("\033]10;?\033\\");
         assertEquals("\033]10;rgb:0000/0000/0000\033\\",
            captured());
      }

      @Test
      @DisplayName("OSC 10 set then query dynamic fg")
      void setDynamicFgAndQuery() throws Exception {
         feed("\033]10;rgb:8080/8080/8080\033\\");
         captureOut.reset();
         feed("\033]10;?\033\\");
         assertEquals("\033]10;rgb:8080/8080/8080\033\\",
            captured());
      }

      @Test
      @DisplayName("OSC 11;? queries dynamic background color")
      void queryDynamicBackground() throws Exception {
         feed("\033]11;?\033\\");
         assertEquals("\033]11;rgb:0000/0000/0000\033\\",
            captured());
      }

      @Test
      @DisplayName("OSC 12;? queries cursor color")
      void queryCursorColor() throws Exception {
         feed("\033]12;?\033\\");
         assertEquals("\033]12;rgb:e5e5/e5e5/e5e5\033\\",
            captured());
      }

      @Test
      @DisplayName("OSC 104 resets palette color")
      void resetPaletteColor() throws Exception {
         feed("\033]4;0;rgb:aaaa/bbbb/cccc\033\\");
         feed("\033]104;0\007");
         captureOut.reset();
         feed("\033]4;0;?\033\\");
         assertEquals("\033]4;0;rgb:0000/0000/0000\033\\",
            captured());
      }

      @Test
      @DisplayName("OSC 110 resets dynamic foreground")
      void resetDynamicFg() throws Exception {
         feed("\033]10;rgb:1111/2222/3333\033\\");
         feed("\033]110\007");
         captureOut.reset();
         feed("\033]10;?\033\\");
         assertEquals("\033]10;rgb:0000/0000/0000\033\\",
            captured());
      }

      @Test
      @DisplayName("OSC 5;N;? queries special color")
      void querySpecialColor5() throws Exception {
         feed("\033]5;0;?\033\\");
         assertEquals("\033]5;0;rgb:0000/0000/0000\033\\",
            captured());
      }

      @Test
      @DisplayName("OSC 4 with special index queries via offset")
      void querySpecialViaOffset() throws Exception {
         feed("\033]4;256;?\033\\");
         assertEquals("\033]4;256;rgb:0000/0000/0000\033\\",
            captured());
      }

      @Test
      @DisplayName("OSC 4 set+query #rrggbb format works")
      void hashFormatRoundTrip() throws Exception {
         feed("\033]4;0;#aabbcc\033\\");
         captureOut.reset();
         feed("\033]4;0;?\033\\");
         assertEquals("\033]4;0;rgb:aaaa/bbbb/cccc\033\\",
            captured());
      }

      @Test
      @DisplayName("BEL terminator works for OSC")
      void belTerminator() throws Exception {
         feed("\033]4;0;?\007");
         assertEquals("\033]4;0;rgb:0000/0000/0000\033\\",
            captured());
      }
   }

   @Nested
   @DisplayName("DECRQM Mode Query")
   class DecrqmTests {

      private java.io.ByteArrayOutputStream captureOut;
      private java.io.OutputStreamWriter origWriter;

      @BeforeEach
      void swapWriter() throws Exception {
         captureOut = new java.io.ByteArrayOutputStream();
         Field writerField = Vt100.class.getDeclaredField("writer");
         writerField.setAccessible(true);
         origWriter = (java.io.OutputStreamWriter) writerField.get(vt100);
         writerField.set(vt100,
            new java.io.OutputStreamWriter(captureOut,
               StandardCharsets.UTF_8));
      }

      @AfterEach
      void restoreWriter() throws Exception {
         if (origWriter != null) {
            Field writerField = Vt100.class.getDeclaredField("writer");
            writerField.setAccessible(true);
            writerField.set(vt100, origWriter);
         }
      }

      private String captured() {
         return captureOut.toString(StandardCharsets.UTF_8);
      }

      @Test
      @DisplayName("DECRQM reports mode 1049 as reset")
      void decrqmAltScreenReset() throws Exception {
         feed("\033[?1049$p");
         assertEquals("\033[?1049;2$y", captured(),
            "mode 1049 should report reset (Pm=2)");
      }

      @Test
      @DisplayName("DECRQM reports mode 1049 as set after enable")
      void decrqmAltScreenSet() throws Exception {
         feed("\033[?1049h");
         flush();
         captureOut.reset();
         feed("\033[?1049$p");
         assertEquals("\033[?1049;1$y", captured(),
            "mode 1049 should report set (Pm=1)");
      }

      @Test
      @DisplayName("DECRQM does not alter screen content")
      void decrqmNoSideEffects() throws Exception {
         feed("\033[1;1H");
         feed("HELLO");
         flush();
         captureOut.reset();
         feed("\033[?1049$p");
         flush();
         assertEquals("HELLO",
            lineContent(1).substring(0, 5),
            "DECRQM must not switch alt screen");
      }

      @Test
      @DisplayName("DECRQM reports unknown mode as 0")
      void decrqmUnknownMode() throws Exception {
         feed("\033[?9999$p");
         assertEquals("\033[?9999;0$y", captured(),
            "unknown mode should report Pm=0");
      }

      @Test
      @DisplayName("DECRQM reports mode 25 cursor visible state")
      void decrqmCursorVisible() throws Exception {
         feed("\033[?25$p");
         assertEquals("\033[?25;1$y", captured(),
            "cursor visible mode should report set");
         captureOut.reset();
         feed("\033[?25l");
         feed("\033[?25$p");
         assertEquals("\033[?25;2$y", captured(),
            "cursor hidden mode should report reset");
      }

      @Test
      @DisplayName("vi exit sequence: DECRQM does not leak 'p'")
      void viExitDecrqmNoLeak() throws Exception {
         feed("\033[1;1H");
         feed("MAIN CONTENT");
         flush();
         feed("\033[?1049h");
         flush();
         feed("\033[1;1H");
         feed("ALT CONTENT");
         flush();
         feed("\033[?1049l");
         flush();
         // vi may query mode 1049 via DECRQM after exiting alt screen
         feed("\033[?1049$p");
         flush();
         String row1 = lineContent(1);
         assertTrue(row1.startsWith("MAIN CONTENT"),
            "main screen row 1 must be restored, got: " + row1);
      }
   }

}
