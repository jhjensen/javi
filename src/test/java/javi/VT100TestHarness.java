package javi;

import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reusable test harness for VT100 terminal escape sequence
 * testing.
 *
 * <p>Encapsulates the boilerplate of creating a {@link Vt100}
 * instance with piped I/O, feeding escape sequences through the
 * parser, and reading back screen content and attributes.
 * Call {@link #setUp()} before tests and {@link #tearDown()}
 * after — typically from JUnit lifecycle methods.</p>
 *
 * <p>biglock2 is acquired in setUp and released in tearDown.</p>
 */
final class VT100TestHarness {

   private static int instanceCounter;

   private Vt100 vt100;
   private Vt100Parser parser;
   private Method doChar;
   private PipedOutputStream pipeOut;
   private int termRows = 24;
   private int termCols = 80;

   /**
    * Creates the Vt100 instance and parser, acquiring
    * biglock2 for thread safety.
    */
   void setUp() throws Exception {
      EventQueue.biglock2.lock();

      pipeOut = new PipedOutputStream();
      PipedInputStream pipeIn = new PipedInputStream(pipeOut);
      BufferedInputStream bis = new BufferedInputStream(pipeIn);
      OutputStream nullOut = OutputStream.nullOutputStream();
      vt100 = new Vt100(nullOut, bis,
         new StringIoc(
            "harness-" + instanceCounter++, null),
         StandardCharsets.UTF_8);

      Field parserField =
         Vt100.class.getDeclaredField("parser");
      parserField.setAccessible(true);
      parser = (Vt100Parser) parserField.get(vt100);

      doChar = Vt100Parser.class.getDeclaredMethod(
         "doChar", char.class);
      doChar.setAccessible(true);

      Field rowsField =
         Vt100.class.getDeclaredField("rows");
      rowsField.setAccessible(true);
      rowsField.setInt(vt100, termRows);

      while (vt100.readIn() < termRows + 1)
         vt100.insertOne("", vt100.readIn());
   }

   /** Releases resources and biglock2. */
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
    * Feeds a string character-by-character through the VT100
    * parser.
    */
   void feed(String chars) throws Exception {
      for (int i = 0; i < chars.length(); i++)
         doChar.invoke(parser, chars.charAt(i));
   }

   /** Returns the cursor position (internal MovePos). */
   MovePos cursor() throws Exception {
      Field vtcurField =
         Vt100.class.getDeclaredField("vtcursor");
      vtcurField.setAccessible(true);
      return (MovePos) vtcurField.get(vt100);
   }

   /** Returns 1-based terminal row of the cursor. */
   int cursorRow() throws Exception {
      return cursor().y - vt100.readIn() + termRows + 1;
   }

   /** Returns 1-based terminal column of the cursor. */
   int cursorCol() throws Exception {
      return cursor().x + 1;
   }

   /** Returns content of a 1-based terminal row. */
   String lineContent(int termRow) throws Exception {
      int absLine = vt100.readIn() - 1 - termRows + termRow;
      if (absLine < 1 || absLine >= vt100.readIn())
         return "";
      return vt100.at(absLine);
   }

   /**
    * Dumps screen content for rows firstRow..lastRow
    * (both 1-based, inclusive).
    */
   String[] screenDump(int firstRow, int lastRow)
         throws Exception {
      String[] lines = new String[lastRow - firstRow + 1];
      for (int r = firstRow; r <= lastRow; r++)
         lines[r - firstRow] = lineContent(r);
      return lines;
   }

   /** Returns the ScreenAttributes grid. */
   ScreenAttributes screenAttrs() {
      return vt100.getScreenAttributes();
   }

   /**
    * Returns the packed CellAttr at a 1-based terminal
    * row and column.
    */
   int getAttr(int termRow, int termCol) throws Exception {
      int absLine = vt100.readIn() - 1 - termRows + termRow;
      return screenAttrs().getAttr(absLine, termCol - 1);
   }

   /**
    * Asserts cursor is at the given 1-based row and column.
    */
   void assertCursor(int row, int col) throws Exception {
      assertEquals(row, cursorRow(),
         "cursor row");
      assertEquals(col, cursorCol(),
         "cursor col");
   }

   /**
    * Asserts that terminal rows starting at startRow match
    * the expected strings. Trailing spaces are trimmed from
    * actual content for comparison.
    */
   void assertScreen(int startRow, String... expected)
         throws Exception {
      for (int i = 0; i < expected.length; i++) {
         int row = startRow + i;
         String actual = lineContent(row);
         // Pad actual to expected length if shorter
         String trimmed = actual.length() > expected[i].length()
            ? actual.substring(0, expected[i].length())
            : actual;
         // If expected has trailing spaces, compare as-is
         if (expected[i].endsWith(" ")) {
            String padded = actual;
            while (padded.length() < expected[i].length())
               padded += " ";
            assertEquals(expected[i],
               padded.substring(0, expected[i].length()),
               "row " + row);
         } else {
            // Trim trailing spaces from actual for comparison
            assertEquals(expected[i], trimmed.stripTrailing(),
               "row " + row);
         }
      }
   }

   /** Returns the configured number of terminal rows. */
   int rows() {
      return termRows;
   }
}
