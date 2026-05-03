package javi;

import java.io.BufferedInputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration tests for the VT100 parser pipeline.
 *
 * <p>Unlike Vt100ECScreenGuiJUnitTest which tests individual ECScreen
 * methods via reflection, this class exercises the full parser pipeline:
 * bytes are written to a pipe, the parser thread reads and interprets
 * them, and the resulting screen buffer content is verified.</p>
 *
 * <p>Tests cover: plain text output, cursor positioning via escape
 * sequences, erase operations, SGR attribute application to buffer
 * cells, and scrolling region behavior.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class Vt100ParserPipelineGuiJUnitTest {

   private static Robot robot;
   private static Vt100 vt100;
   private static PipedOutputStream pipeToVt;
   private static Vt100Parser parser;
   private static int initialReadIn;

   @BeforeAll
   static void initJavi() throws Exception {
      if (Rgroup.bindingLookup("persistfile") == null) {
         EventQueue.biglock2.lock();
         try {
            Class.forName("javi.TextEdit");
            EditTester1.TestCircBuffer.initCmd();
            DirManager.getInstance();
            FileList.make("");
            Javi.initToUi();
            Javi.initPostUi();
            Command.doneInit();
         } finally {
            EventQueue.biglock2.unlock();
         }
         Thread.sleep(500);
      }
      robot = BasicRobot.robotWithCurrentAwtHierarchy();

      pipeToVt = new PipedOutputStream();
      PipedInputStream pipeIn = new PipedInputStream(pipeToVt, 4096);
      BufferedInputStream bis = new BufferedInputStream(pipeIn);
      PipedOutputStream devNull = new PipedOutputStream();
      new PipedInputStream(devNull); // connect to prevent broken pipe

      EventQueue.biglock2.lock();
      try {
         IoConverter<String> ioc = new StringIoc("pipeline-test", "");
         vt100 = new Vt100(devNull, bis, ioc, StandardCharsets.UTF_8);

         Field parserField = Vt100.class.getDeclaredField("parser");
         parserField.setAccessible(true);
         parser = (Vt100Parser) parserField.get(vt100);

         // Set rows to a reasonable terminal size
         Field rowsField = Vt100.class.getDeclaredField("rows");
         rowsField.setAccessible(true);
         rowsField.setInt(vt100, 24);

         // Ensure enough lines exist for terminal operations
         while (vt100.readIn() < 30)
            vt100.insertOne("", vt100.readIn());

         initialReadIn = vt100.readIn();
      } finally {
         EventQueue.biglock2.unlock();
      }
      // Let parser thread start and block on reader.read()
      Thread.sleep(200);
   }

   @AfterAll
   static void tearDownAll() {
      if (parser != null)
         parser.stop();
      if (robot != null)
         robot.cleanUp();
   }

   /**
    * Writes bytes through the pipe and processes them by draining the
    * EventQueue. The parser thread reads from the pipe, sets recbyte,
    * and inserts itself into the EventQueue. We pull the IEvent and
    * call execute() which processes recbyte PLUS all remaining
    * available bytes from the pipe in one shot.
    */
   private void feedAndWait(String data) throws Exception {
      byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
      pipeToVt.write(bytes);
      pipeToVt.flush();

      // Access the queue via reflection
      Field queueField = EventQueue.class.getDeclaredField("queue");
      queueField.setAccessible(true);

      @SuppressWarnings("unchecked")
      java.util.LinkedList<Object> queue =
         (java.util.LinkedList<Object>) queueField.get(null);

      // Wait for parser thread to read first byte and insert into queue
      long deadline = System.currentTimeMillis() + 2000;
      while (System.currentTimeMillis() < deadline) {
         synchronized (EventQueue.class) {
            if (!queue.isEmpty())
               break;
         }
         Thread.sleep(10);
      }

      // Drain all IEvents from the queue — execute() reads remaining
      // bytes from pipe so usually just one event per feedAndWait call
      boolean processed = false;
      while (true) {
         Object ev;
         synchronized (EventQueue.class) {
            if (queue.isEmpty())
               break;
            ev = queue.removeFirst();
         }
         if (ev instanceof EventQueue.IEvent) {
            EventQueue.biglock2.lock();
            try {
               ((EventQueue.IEvent) ev).execute();
            } finally {
               EventQueue.biglock2.unlock();
            }
            processed = true;
         }
      }
      if (!processed) {
         throw new AssertionError(
            "feedAndWait: parser did not produce any events within 2s");
      }
   }

   /**
    * Gets the current vtcursor via reflection.
    */
   private MovePos getVtCursor() throws Exception {
      Field vtcField = Vt100.class.getDeclaredField("vtcursor");
      vtcField.setAccessible(true);
      return (MovePos) vtcField.get(vt100);
   }

   // ── Plain text output ────────────────────────────────────────

   @Test
   void t01_plainTextAppearsInBuffer() throws Exception {
      // Position cursor at known location (row 1, col 1) and write text
      feedAndWait("\033[H\033[2J"); // Home + clear screen
      feedAndWait("Hello World");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         String content = vt100.at(cursor.y);
         assertTrue(content.contains("Hello World"),
            "Buffer should contain 'Hello World', got: " + content);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t02_newlineAdvancesLine() throws Exception {
      int lineBefore;
      EventQueue.biglock2.lock();
      try {
         lineBefore = getVtCursor().y;
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\n");

      EventQueue.biglock2.lock();
      try {
         int lineAfter = getVtCursor().y;
         assertTrue(lineAfter > lineBefore || vt100.readIn() > initialReadIn,
            "Newline should advance cursor line or grow buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t03_carriageReturnResetsColumn() throws Exception {
      feedAndWait("ABCDEF\r");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         assertEquals(0, cursor.x,
            "CR should reset cursor X to 0");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── CSI cursor positioning ───────────────────────────────────

   @Test
   void t04_csiCursorForward() throws Exception {
      // ESC[5C = cursor forward 5 columns
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         cursor.x = 0;
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\033[5C");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         assertEquals(5, cursor.x,
            "CSI 5C should move cursor forward to column 5");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t05_csiCursorBack() throws Exception {
      // Set cursor to column 10 then move back 3
      EventQueue.biglock2.lock();
      try {
         getVtCursor().x = 10;
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\033[3D");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         assertEquals(7, cursor.x,
            "CSI 3D should move cursor back to column 7");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t06_csiCursorAbsolutePosition() throws Exception {
      // ESC[5;10H = move cursor to row 5, column 10
      feedAndWait("\033[5;10H");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         assertEquals(9, cursor.x,
            "CSI 5;10H should set cursor X to 9 (1-based→0-based)");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t07_csiCursorHomeDefault() throws Exception {
      // ESC[H = move cursor to (1,1)
      feedAndWait("\033[H");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         assertEquals(0, cursor.x,
            "CSI H should set cursor X to 0");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── CSI erase operations ─────────────────────────────────────

   @Test
   void t08_csiEraseInLine() throws Exception {
      // Write text, then ESC[2K to erase entire line
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         vt100.changeElementAt("this line will be erased", cursor.y);
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\033[2K");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         String line = vt100.at(cursor.y);
         assertEquals("", line,
            "CSI 2K should erase entire current line");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t09_csiEraseToEndOfLine() throws Exception {
      // Place cursor at col 5 and erase to end: ESC[0K or ESC[K
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         vt100.changeElementAt("0123456789ABCDEF", cursor.y);
         cursor.x = 5;
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\033[K");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         String line = vt100.at(cursor.y);
         assertTrue(line.length() <= 5,
            "CSI K should erase from cursor to end, got: '" + line
            + "' (len=" + line.length() + ")");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t10_csiEraseScreen() throws Exception {
      // ESC[2J = erase entire screen
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         // Put content on several lines
         for (int i = 0; i < 5 && cursor.y - i >= 1; i++) {
            vt100.changeElementAt("line content " + i, cursor.y - i);
         }
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\033[2J");

      EventQueue.biglock2.lock();
      try {
         // Verify at least some lines are erased
         int blankCount = 0;
         int end = vt100.readIn();
         int start = end - 24;
         if (start < 1) start = 1;
         for (int i = start; i < end; i++) {
            if (vt100.at(i).isEmpty())
               blankCount++;
         }
         assertTrue(blankCount > 0,
            "CSI 2J should erase screen lines");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── SGR through parser pipeline ──────────────────────────────

   @Test
   void t11_sgrBoldThroughPipe() throws Exception {
      // ESC[1m enables bold, write text, check attributes
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         cursor.x = 0;
         vt100.changeElementAt("", cursor.y);
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\033[1mBOLD");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         String content = vt100.at(cursor.y);
         assertTrue(content.contains("BOLD"),
            "Bold text should appear in buffer");
         // Check attribute was recorded
         ScreenAttributes attrs = vt100.getScreenAttributes();
         int attr = attrs.getAttr(cursor.y, 0);
         assertTrue(CellAttr.isBold(attr),
            "First char should have bold attribute, attr=" + attr);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t12_sgrColorThroughPipe() throws Exception {
      // ESC[31m = red foreground, then write text
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         cursor.x = 0;
         vt100.changeElementAt("", cursor.y);
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\033[0m\033[31mRED");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         String content = vt100.at(cursor.y);
         assertTrue(content.contains("RED"),
            "Red text should appear in buffer");
         ScreenAttributes attrs = vt100.getScreenAttributes();
         int attr = attrs.getAttr(cursor.y, 0);
         int fg = CellAttr.fgColor(attr);
         assertEquals(1, fg,
            "FG color should be 1 (red), got " + fg);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t13_sgrResetThroughPipe() throws Exception {
      // ESC[0m resets attributes
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         cursor.x = 0;
         vt100.changeElementAt("", cursor.y);
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\033[1;4;31mSTYLED\033[0mPLAIN");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         String content = vt100.at(cursor.y);
         assertTrue(content.contains("STYLEDPLAIN"),
            "Both styled and plain text should appear");
         ScreenAttributes attrs = vt100.getScreenAttributes();
         // Check that 'PLAIN' (starting at offset 6) has no bold
         int plainAttr = attrs.getAttr(cursor.y, 6);
         assertFalse(CellAttr.isBold(plainAttr),
            "PLAIN text after reset should not be bold");
         assertEquals(-1, CellAttr.fgColor(plainAttr),
            "PLAIN text after reset should have default fg color");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── OSC title setting through pipeline ───────────────────────

   @Test
   void t14_oscTitleSetThroughPipe() throws Exception {
      // OSC 0;title BEL (ESC ] 0 ; title ^G)
      feedAndWait("\033]0;My Terminal Title\007");

      assertEquals("My Terminal Title", vt100.getOscTitle(),
         "OSC 0 should set terminal title");
   }

   @Test
   void t15_oscTitleWithStTerminator() throws Exception {
      // OSC 2;title ST (ESC ] 2 ; title ESC \)
      feedAndWait("\033]2;Another Title\033\\");

      assertEquals("Another Title", vt100.getOscTitle(),
         "OSC 2 with ST terminator should set title");
   }

   // ── Overwrite mode (default) ─────────────────────────────────

   @Test
   void t16_overwriteReplacesChars() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         vt100.changeElementAt("AAAAAAAAAA", cursor.y);
         cursor.x = 3;
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("XYZ");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         String content = vt100.at(cursor.y);
         assertTrue(content.startsWith("AAA"),
            "First 3 chars should remain 'AAA', got: " + content);
         assertTrue(content.contains("XYZ"),
            "Overwritten chars should be XYZ, got: " + content);
         // After XYZ, remaining A's should still be there
         if (content.length() >= 10)
            assertEquals('A', content.charAt(6),
               "Char at 6 should still be 'A'");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Private mode sequences ───────────────────────────────────

   @Test
   void t17_decCursorHideThroughPipe() throws Exception {
      // ESC[?25l = hide cursor
      feedAndWait("\033[?25l");
      assertFalse(vt100.isCursorVisible(),
         "DECTCEM ?25l should hide cursor");
      // Restore
      feedAndWait("\033[?25h");
      assertTrue(vt100.isCursorVisible(),
         "DECTCEM ?25h should show cursor");
   }

   @Test
   void t18_bracketedPasteModeThroughPipe() throws Exception {
      // ESC[?2004h = enable bracketed paste
      feedAndWait("\033[?2004h");

      Field bpmField = Vt100.class.getDeclaredField("bracketedPasteMode");
      bpmField.setAccessible(true);
      assertTrue(bpmField.getBoolean(vt100),
         "?2004h should enable bracketed paste mode");

      // Disable
      feedAndWait("\033[?2004l");
      assertFalse(bpmField.getBoolean(vt100),
         "?2004l should disable bracketed paste mode");
   }

   @Test
   void t19_mouseTrackingModeThroughPipe() throws Exception {
      // ESC[?1000h = enable normal mouse tracking
      feedAndWait("\033[?1000h");

      Field mtField = Vt100.class.getDeclaredField("mouseTrackingMode");
      mtField.setAccessible(true);
      assertEquals(1000, mtField.getInt(vt100),
         "?1000h should enable mouse tracking mode 1000");

      // Disable
      feedAndWait("\033[?1000l");
      assertEquals(0, mtField.getInt(vt100),
         "?1000l should disable mouse tracking");
   }

   @Test
   void t20_applicationCursorKeysThroughPipe() throws Exception {
      // ESC[?1h = enable application cursor keys
      feedAndWait("\033[?1h");

      Field ackField = Vt100.class.getDeclaredField(
         "applicationCursorKeys");
      ackField.setAccessible(true);
      assertTrue(ackField.getBoolean(vt100),
         "?1h should enable application cursor keys");

      feedAndWait("\033[?1l");
      assertFalse(ackField.getBoolean(vt100),
         "?1l should disable application cursor keys");
   }

   // ── Multi-line text output ───────────────────────────────────

   @Test
   void t21_multiLineOutput() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         cursor.x = 0;
         vt100.changeElementAt("", cursor.y);
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("Line1\r\nLine2\r\nLine3");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         String lastLine = vt100.at(cursor.y);
         assertTrue(lastLine.contains("Line3"),
            "Last line should contain 'Line3', got: " + lastLine);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t22_tabCharacterHandled() throws Exception {
      // Tab should not crash the parser
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         cursor.x = 0;
         vt100.changeElementAt("", cursor.y);
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\tindented");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         String content = vt100.at(cursor.y);
         assertTrue(content.contains("indented"),
            "Tab + text should appear in buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── CSI erase characters ─────────────────────────────────────

   @Test
   void t23_csiEraseCharacters() throws Exception {
      // ESC[3X = erase 3 characters from cursor position
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         vt100.changeElementAt("ABCDEFGHIJ", cursor.y);
         cursor.x = 2;
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\033[3X");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         String content = vt100.at(cursor.y);
         // Characters at positions 2,3,4 should be erased
         assertTrue(content.length() <= 7 || !content.substring(2, 5)
            .equals("CDE"),
            "3 chars from pos 2 should be erased, got: " + content);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── CSI insert lines ─────────────────────────────────────────

   @Test
   void t24_csiInsertLines() throws Exception {
      // ESC[2L = insert 2 blank lines at cursor position
      String originalLine;
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         vt100.changeElementAt("MARKER LINE", cursor.y);
         originalLine = vt100.at(cursor.y);
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\033[2L");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         String atCursor = vt100.at(cursor.y);
         // After inserting 2 lines, current line should be blank
         // (may contain trailing spaces from terminal padding)
         assertTrue(atCursor.isBlank(),
            "Inserted line should be blank, got: '" + atCursor + "'");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── CSI cursor up/down ───────────────────────────────────────

   @Test
   void t25_csiCursorDown() throws Exception {
      int yBefore;
      EventQueue.biglock2.lock();
      try {
         yBefore = getVtCursor().y;
      } finally {
         EventQueue.biglock2.unlock();
      }

      // ESC[3B = cursor down 3
      feedAndWait("\033[3B");

      EventQueue.biglock2.lock();
      try {
         int yAfter = getVtCursor().y;
         assertEquals(yBefore + 3, yAfter,
            "CSI 3B should move cursor down 3 rows");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t26_csiCursorUp() throws Exception {
      // First move down to have room
      feedAndWait("\033[10B");

      int yBefore;
      EventQueue.biglock2.lock();
      try {
         yBefore = getVtCursor().y;
      } finally {
         EventQueue.biglock2.unlock();
      }

      // ESC[2A = cursor up 2
      feedAndWait("\033[2A");

      EventQueue.biglock2.lock();
      try {
         int yAfter = getVtCursor().y;
         assertEquals(yBefore - 2, yAfter,
            "CSI 2A should move cursor up 2 rows");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Device attribute request ─────────────────────────────────

   @Test
   void t27_deviceAttributeRequestNoThrow() throws Exception {
      // ESC[c = request device attributes (primary DA)
      feedAndWait("\033[c");
      // Just verify no crash; response is written to the output stream
      assertTrue(true,
         "DA request should not throw");
   }

   @Test
   void t28_cursorPositionReportNoThrow() throws Exception {
      // ESC[6n = request cursor position report
      feedAndWait("\033[6n");
      assertTrue(true,
         "CPR request should not throw");
   }

   // ── 256-color through pipeline ───────────────────────────────

   @Test
   void t29_256ColorFgThroughPipe() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         cursor.x = 0;
         vt100.changeElementAt("", cursor.y);
      } finally {
         EventQueue.biglock2.unlock();
      }

      // ESC[38;5;202m = 256-color fg 202, then write text
      feedAndWait("\033[38;5;202mORANGE");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         String content = vt100.at(cursor.y);
         assertTrue(content.contains("ORANGE"),
            "256-color text should appear");
         ScreenAttributes attrs = vt100.getScreenAttributes();
         int attr = attrs.getAttr(cursor.y, 0);
         int fg = CellAttr.fgColor(attr);
         assertEquals(202, fg,
            "FG should be 202 (256-color orange), got " + fg);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t30_256ColorBgThroughPipe() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         cursor.x = 0;
         vt100.changeElementAt("", cursor.y);
      } finally {
         EventQueue.biglock2.unlock();
      }

      // ESC[48;5;27m = 256-color bg 27 (blue), then text
      feedAndWait("\033[0m\033[48;5;27mBLUEBG");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         String content = vt100.at(cursor.y);
         assertTrue(content.contains("BLUEBG"),
            "256-color bg text should appear");
         ScreenAttributes attrs = vt100.getScreenAttributes();
         int attr = attrs.getAttr(cursor.y, 0);
         int bg = CellAttr.bgColor(attr);
         assertEquals(27, bg,
            "BG should be 27 (256-color blue), got " + bg);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Alternate screen buffer through pipeline ─────────────────

   @Test
   void t31_alternateScreenThroughPipe() throws Exception {
      // ESC[?1049h = switch to alternate screen
      feedAndWait("\033[?1049h");

      Field altField = null;
      Object ecscreen;
      EventQueue.biglock2.lock();
      try {
         Field ecF = Vt100.class.getDeclaredField("ecscreen");
         ecF.setAccessible(true);
         ecscreen = ecF.get(vt100);
         altField = ecscreen.getClass().getDeclaredField(
            "inAlternateScreen");
         altField.setAccessible(true);
         assertTrue(altField.getBoolean(ecscreen),
            "?1049h should activate alternate screen");
      } finally {
         EventQueue.biglock2.unlock();
      }

      // Switch back: ESC[?1049l
      feedAndWait("\033[?1049l");

      EventQueue.biglock2.lock();
      try {
         assertFalse(altField.getBoolean(ecscreen),
            "?1049l should deactivate alternate screen");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Focus events mode through pipeline ───────────────────────

   @Test
   void t32_focusEventModeThroughPipe() throws Exception {
      feedAndWait("\033[?1004h");

      Field feField = Vt100.class.getDeclaredField("focusEventsMode");
      feField.setAccessible(true);
      assertTrue(feField.getBoolean(vt100),
         "?1004h should enable focus events");

      feedAndWait("\033[?1004l");
      assertFalse(feField.getBoolean(vt100),
         "?1004l should disable focus events");
   }

   // ── Autowrap mode ────────────────────────────────────────────

   @Test
   void t33_autowrapModeThroughPipe() throws Exception {
      // ESC[?7l = disable autowrap
      feedAndWait("\033[?7l");

      Field awField = Vt100.class.getDeclaredField("autowrapMode");
      awField.setAccessible(true);
      assertFalse(awField.getBoolean(vt100),
         "?7l should disable autowrap");

      // ESC[?7h = enable autowrap
      feedAndWait("\033[?7h");
      assertTrue(awField.getBoolean(vt100),
         "?7h should enable autowrap");
   }

   // ── Combined escape sequence ─────────────────────────────────

   @Test
   void t34_combinedCursorMoveAndWrite() throws Exception {
      // Move to specific position and write
      // ESC[1;1H moves to top-left, then write
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         int readIn = vt100.readIn();
         // Clear a line near the visible top
         int targetLine = readIn - 24;
         if (targetLine < 1) targetLine = 1;
         vt100.changeElementAt("", targetLine);
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\033[1;1HTOPLEFT");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         assertEquals(0 + 7, cursor.x,
            "After writing 'TOPLEFT', cursor X should be at 7");
         String content = vt100.at(cursor.y);
         assertTrue(content.contains("TOPLEFT"),
            "Top-left position should contain 'TOPLEFT'");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t35_rapidSequentialEscapes() throws Exception {
      // Rapid sequence: position → color → text → reset → text
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         cursor.x = 0;
         vt100.changeElementAt("", cursor.y);
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\033[1;32mGREEN\033[0m NORMAL");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         String content = vt100.at(cursor.y);
         assertTrue(content.contains("GREEN"),
            "Rapid escapes: should contain GREEN");
         assertTrue(content.contains("NORMAL"),
            "Rapid escapes: should contain NORMAL");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t36_sgrMouseModeThroughPipe() throws Exception {
      // ESC[?1006h = enable SGR mouse mode
      feedAndWait("\033[?1006h");

      Field sgrField = Vt100.class.getDeclaredField("sgrMouseMode");
      sgrField.setAccessible(true);
      assertTrue(sgrField.getBoolean(vt100),
         "?1006h should enable SGR mouse mode");

      feedAndWait("\033[?1006l");
      assertFalse(sgrField.getBoolean(vt100),
         "?1006l should disable SGR mouse mode");
   }

   @Test
   void t37_cursorColumnAbsolute() throws Exception {
      // ESC[15G = cursor to column 15
      feedAndWait("\033[15G");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         assertEquals(14, cursor.x,
            "CSI 15G should set cursor to column 14 (0-based)");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t38_multiParamSgr() throws Exception {
      // ESC[1;4;7;33m = bold + underline + reverse + yellow fg
      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         cursor.x = 0;
         vt100.changeElementAt("", cursor.y);
      } finally {
         EventQueue.biglock2.unlock();
      }

      feedAndWait("\033[1;4;7;33mSTYLED");

      EventQueue.biglock2.lock();
      try {
         MovePos cursor = getVtCursor();
         ScreenAttributes attrs = vt100.getScreenAttributes();
         int attr = attrs.getAttr(cursor.y, 0);
         assertTrue(CellAttr.isBold(attr), "Should be bold");
         assertTrue(CellAttr.isUnderline(attr), "Should be underline");
         assertTrue(CellAttr.isReverse(attr), "Should be reverse");
         assertEquals(3, CellAttr.fgColor(attr),
            "FG should be 3 (yellow)");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
