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
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Tests for DECRQCRA (Request Checksum of Rectangular Area).
 *
 * <p>DECRQCRA (CSI Pid ; Pp ; Pt ; Pl ; Pb ; Pr * y) computes a
 * 16-bit checksum of character values in a rectangular screen area
 * and responds with DCS Pid ! ~ XXXX ST.</p>
 *
 * <p>Uses a ByteArrayOutputStream to capture DCS responses sent
 * via {@link Vt100#sendResponse}.</p>
 */
class DecrqcraJUnitTest {

   private static int instanceCounter;

   private Vt100 vt100;
   private Vt100Parser parser;
   private Method doChar;
   private PipedOutputStream pipeOut;
   private ByteArrayOutputStream capturedOutput;

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
      capturedOutput = new ByteArrayOutputStream();
      vt100 = new Vt100(capturedOutput, bis,
         new StringIoc("decrqcra-" + instanceCounter++, null),
         StandardCharsets.UTF_8);

      Field parserField = Vt100.class.getDeclaredField("parser");
      parserField.setAccessible(true);
      parser = (Vt100Parser) parserField.get(vt100);

      doChar = Vt100Parser.class.getDeclaredMethod("doChar",
         char.class);
      doChar.setAccessible(true);

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

   /** Returns the captured DCS response as a string. */
   private String capturedResponse() {
      return capturedOutput.toString(StandardCharsets.UTF_8);
   }

   /** Clears the captured output for the next assertion. */
   private void clearCaptured() {
      capturedOutput.reset();
   }

   /**
    * Computes expected DECRQCRA checksum for a rectangular area.
    *
    * @param chars 2D array of characters [row][col]
    * @return hex-encoded 16-bit checksum (4 uppercase hex digits)
    */
   private static String expectedChecksum(char[][] chars) {
      int sum = 0;
      for (char[] row : chars)
         for (char ch : row)
            sum += ch;
      return String.format("%04X", sum & 0xFFFF);
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

   // ── Response format tests ──────────────────────────────────

   @Nested
   @DisplayName("DECRQCRA — Response Format")
   class ResponseFormatTests {

      @Test
      void emptyScreenAllSpaces() throws Exception {
         // Request checksum of row 1, cols 1-10
         // CSI 1 ; 1 ; 1 ; 1 ; 1 ; 10 * y
         feed("\033[1;1;1;1;1;10*y");
         String resp = capturedResponse();
         // Should be DCS 1 ! ~ XXXX ST
         assertTrue(resp.startsWith("\033P1!~"),
            "response should start with DCS Pid!~: " + resp);
         assertTrue(resp.endsWith("\033\\"),
            "response should end with ST: " + resp);
         // 10 spaces = 10 * 0x20 = 320 = 0x0140
         String hex = resp.substring(5, resp.length() - 2);
         assertEquals("0140", hex,
            "10 spaces checksum");
      }

      @Test
      void pidEchoedInResponse() throws Exception {
         // Use Pid=42
         feed("\033[42;1;1;1;1;1*y");
         String resp = capturedResponse();
         assertTrue(resp.startsWith("\033P42!~"),
            "Pid 42 should be echoed: " + resp);
      }

      @Test
      void pidDefaultsToZero() throws Exception {
         // CSI * y with no parameters
         // No params → Pid=0, defaults for everything
         feed("\033[*y");
         String resp = capturedResponse();
         assertTrue(resp.startsWith("\033P0!~"),
            "default Pid should be 0: " + resp);
      }
   }

   // ── Checksum computation tests ─────────────────────────────

   @Nested
   @DisplayName("DECRQCRA — Checksum Computation")
   class ChecksumTests {

      @Test
      void singleCharacterCell() throws Exception {
         // Put 'A' at row 1, col 1
         feed("\033[1;1H");
         feed("A");
         flush();
         clearCaptured();
         // Request checksum of just that cell
         feed("\033[1;1;1;1;1;1*y");
         String resp = capturedResponse();
         // 'A' = 0x41 = 65
         String hex = resp.substring(5, resp.length() - 2);
         assertEquals("0041", hex, "single 'A' checksum");
      }

      @Test
      void multipleCharacters() throws Exception {
         // Write "ABC" at row 1 starting at col 1
         feed("\033[1;1H");
         feed("ABC");
         flush();
         clearCaptured();
         // Request checksum of row 1, cols 1-3
         feed("\033[1;1;1;1;1;3*y");
         String resp = capturedResponse();
         // A(65) + B(66) + C(67) = 198 = 0x00C6
         String hex = resp.substring(5, resp.length() - 2);
         assertEquals("00C6", hex, "ABC checksum");
      }

      @Test
      void multipleRows() throws Exception {
         // Write "AB" on row 1, "CD" on row 2
         feed("\033[1;1H");
         feed("AB");
         feed("\033[2;1H");
         feed("CD");
         flush();
         clearCaptured();
         // Request checksum of rows 1-2, cols 1-2
         feed("\033[1;1;1;1;2;2*y");
         String resp = capturedResponse();
         // A(65)+B(66)+C(67)+D(68) = 266 = 0x010A
         String hex = resp.substring(5, resp.length() - 2);
         assertEquals("010A", hex, "2x2 grid checksum");
      }

      @Test
      void emptyColumnsAreSpaces() throws Exception {
         // Row 1 has content "X" at col 1, rest is blank
         feed("\033[1;1H");
         feed("X");
         flush();
         clearCaptured();
         // Request checksum of row 1, cols 1-5
         feed("\033[1;1;1;1;1;5*y");
         String resp = capturedResponse();
         // X(88) + 4 spaces(4*32=128) = 216 = 0x00D8
         String hex = resp.substring(5, resp.length() - 2);
         assertEquals("00D8", hex,
            "char + trailing spaces checksum");
      }

      @Test
      void decalnFillsWithE() throws Exception {
         // DECALN fills entire screen with 'E'
         feed("\033#8");
         flush();
         clearCaptured();
         // Request checksum of row 1, cols 1-10
         feed("\033[1;1;1;1;1;10*y");
         String resp = capturedResponse();
         // 10 * 'E'(69) = 690 = 0x02B2
         String hex = resp.substring(5, resp.length() - 2);
         assertEquals("02B2", hex,
            "10 E characters checksum");
      }
   }

   // ── Parameter defaulting tests ─────────────────────────────

   @Nested
   @DisplayName("DECRQCRA — Parameter Defaults")
   class ParameterDefaultTests {

      @Test
      void fullScreenDefault() throws Exception {
         // Fill screen with 'E' via DECALN
         feed("\033#8");
         flush();
         clearCaptured();
         // Request with only Pid — rest default to full screen
         // CSI 1 * y
         feed("\033[1*y");
         String resp = capturedResponse();
         assertTrue(resp.startsWith("\033P1!~"),
            "response format: " + resp);
         // Verify it computed something (non-zero for 'E' filled screen)
         String hex = resp.substring(5, resp.length() - 2);
         int checksum = Integer.parseInt(hex, 16);
         assertTrue(checksum > 0,
            "full screen of E should have non-zero checksum");
      }

      @Test
      void partialParamsDefaultRemaining() throws Exception {
         // Write 'Z' at row 1, col 1
         feed("\033[1;1H");
         feed("Z");
         flush();
         clearCaptured();
         // CSI 1 ; 1 ; 1 ; 1 * y — Pb and Pr default
         feed("\033[1;1;1;1*y");
         String resp = capturedResponse();
         assertTrue(resp.startsWith("\033P1!~"),
            "response format: " + resp);
         // Checksum covers entire screen from row 1,col 1
         String hex = resp.substring(5, resp.length() - 2);
         int checksum = Integer.parseInt(hex, 16);
         assertTrue(checksum > 0,
            "non-empty screen should have non-zero checksum");
      }
   }

   // ── Parser state machine tests ─────────────────────────────

   @Nested
   @DisplayName("DECRQCRA — Parser")
   class ParserTests {

      @Test
      void starYRecognized() throws Exception {
         // Verify the parser recognizes * y and returns to NORM
         Field stateField =
            Vt100Parser.class.getDeclaredField("state");
         stateField.setAccessible(true);
         feed("\033[1;1;1;1;1;1*y");
         assertEquals(0, stateField.getInt(parser),
            "parser should return to NORM after * y");
      }

      @Test
      void starNonYDiscarded() throws Exception {
         // CSI params * z — should discard, return to NORM
         Field stateField =
            Vt100Parser.class.getDeclaredField("state");
         stateField.setAccessible(true);
         feed("\033[1;1*z");
         assertEquals(0, stateField.getInt(parser),
            "parser should return to NORM after * z");
         // No response should be sent
         assertEquals("", capturedResponse(),
            "no response for unknown CSI * z");
      }

      @Test
      void normalAfterDecrqcra() throws Exception {
         // Verify normal text works after DECRQCRA
         feed("\033[1;1;1;1;1;1*y");
         clearCaptured();
         feed("Hello");
         flush();
         // Check row 1 has "Hello" (cursor was at wherever it was)
         // Just verify no crash and parser is functional
         Field stateField =
            Vt100Parser.class.getDeclaredField("state");
         stateField.setAccessible(true);
         assertEquals(0, stateField.getInt(parser),
            "parser in NORM after text");
      }
   }

   // ── Edge case tests ────────────────────────────────────────

   @Nested
   @DisplayName("DECRQCRA — Edge Cases")
   class EdgeCaseTests {

      @Test
      void singleCellRectangle() throws Exception {
         feed("\033[1;1H");
         feed("Q");
         flush();
         clearCaptured();
         // Pt=Pb=1, Pl=Pr=1 — single cell
         feed("\033[1;1;1;1;1;1*y");
         String resp = capturedResponse();
         String hex = resp.substring(5, resp.length() - 2);
         // Q = 81 = 0x0051
         assertEquals("0051", hex, "single cell Q");
      }

      @Test
      void rectangleBeyondContent() throws Exception {
         // Row 1 has "A" at col 1, request cols 1-100
         feed("\033[1;1H");
         feed("A");
         flush();
         clearCaptured();
         // Cols beyond screen width get clamped
         Field ecField = Vt100.class.getDeclaredField("ecscreen");
         ecField.setAccessible(true);
         Object ecscreen = ecField.get(vt100);
         Method getColsMethod =
            ecscreen.getClass().getDeclaredMethod("getColumns");
         getColsMethod.setAccessible(true);
         int cols = (int) getColsMethod.invoke(ecscreen);

         feed("\033[1;1;1;1;1;" + cols + "*y");
         String resp = capturedResponse();
         String hex = resp.substring(5, resp.length() - 2);
         // A(65) + (cols-1)*space(32)
         int expected = (65 + (cols - 1) * 32) & 0xFFFF;
         assertEquals(String.format("%04X", expected), hex,
            "A + trailing spaces for full row");
      }

      @Test
      void checksumWraps16Bits() throws Exception {
         // Fill screen with DECALN ('E'=69), request large area
         // to get checksum > 0xFFFF, verifying 16-bit wrap
         feed("\033#8");
         flush();
         clearCaptured();

         Field ecField = Vt100.class.getDeclaredField("ecscreen");
         ecField.setAccessible(true);
         Object ecscreen = ecField.get(vt100);
         Method getColsMethod =
            ecscreen.getClass().getDeclaredMethod("getColumns");
         getColsMethod.setAccessible(true);
         int cols = (int) getColsMethod.invoke(ecscreen);

         // Full screen checksum: 24 rows * cols * 69
         feed("\033[1*y");
         String resp = capturedResponse();
         String hex = resp.substring(5, resp.length() - 2);
         int expected = (24 * cols * 69) & 0xFFFF;
         assertEquals(String.format("%04X", expected), hex,
            "full DECALN screen checksum (16-bit wrapped)");
      }
   }
}
