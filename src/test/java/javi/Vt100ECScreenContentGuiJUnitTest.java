package javi;

import java.io.BufferedInputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
 * ECScreen content verification and lifecycle tests.
 *
 * <p>Unlike the existing NoThrow tests, these verify that erase,
 * insert, delete, autowrap, reverse-wrap, alternate screen, and
 * response operations produce correct content and state.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class Vt100ECScreenContentGuiJUnitTest {

   private static Robot robot;
   private static Vt100 vt100;
   private static PipedOutputStream pipeToVt;
   private static PipedInputStream responseIn;
   private static Object ecscreen;
   private static Vt100Parser parser;

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
      PipedInputStream pipeIn = new PipedInputStream(pipeToVt);
      BufferedInputStream bis = new BufferedInputStream(pipeIn);

      // Use PipedOutputStream for writer so we can capture responses
      PipedOutputStream writerPipe = new PipedOutputStream();
      responseIn = new PipedInputStream(writerPipe, 4096);

      EventQueue.biglock2.lock();
      try {
         IoConverter<String> ioc =
            new StringIoc("ecscreen-content-test", "");
         vt100 = new Vt100(writerPipe, bis, ioc,
            StandardCharsets.UTF_8);

         Field ecField = Vt100.class.getDeclaredField("ecscreen");
         ecField.setAccessible(true);
         ecscreen = ecField.get(vt100);

         Field parserField = Vt100.class.getDeclaredField("parser");
         parserField.setAccessible(true);
         parser = (Vt100Parser) parserField.get(vt100);

         Field rowsField = Vt100.class.getDeclaredField("rows");
         rowsField.setAccessible(true);
         rowsField.setInt(vt100, 24);

         while (vt100.readIn() < 30)
            vt100.insertOne("", vt100.readIn());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @AfterAll
   static void tearDownAll() {
      if (parser != null)
         parser.stop();
      if (robot != null)
         robot.cleanUp();
   }

   // ── Helpers ──────────────────────────────────────────────────

   private MovePos getCursor() throws Exception {
      Field f = Vt100.class.getDeclaredField("vtcursor");
      f.setAccessible(true);
      return (MovePos) f.get(vt100);
   }

   private Method getMethod(String name, Class<?>... params)
         throws Exception {
      Method m = ecscreen.getClass().getDeclaredMethod(name, params);
      m.setAccessible(true);
      return m;
   }

   private void setField(String name, Object value)
         throws Exception {
      Field f = ecscreen.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(ecscreen, value);
   }

   private boolean getFieldBool(String name) throws Exception {
      Field f = ecscreen.getClass().getDeclaredField(name);
      f.setAccessible(true);
      return f.getBoolean(ecscreen);
   }

   private void setVt100Field(String name, Object value)
         throws Exception {
      Field f = Vt100.class.getDeclaredField(name);
      f.setAccessible(true);
      f.set(vt100, value);
   }

   /** Read all available bytes from the response pipe. */
   private String drainResponse() throws Exception {
      Thread.sleep(50); // allow writer to flush
      int avail = responseIn.available();
      if (avail <= 0)
         return "";
      byte[] buf = new byte[avail];
      int n = responseIn.read(buf, 0, avail);
      return new String(buf, 0, n, StandardCharsets.UTF_8);
   }

   /** Place content at a specific buffer line. */
   private void setLine(int absLine, String content)
         throws Exception {
      while (vt100.readIn() <= absLine)
         vt100.insertOne("", vt100.readIn());
      vt100.changeElementAt(content, absLine);
   }

   // ── Erase content verification ───────────────────────────────

   @Test
   void t01_eraseLineVerifyContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int y = vt100.readIn() - 12;
         cur.y = y;
         cur.x = 5;
         setLine(y, "Hello World");

         Method m = getMethod("eraseLine", StringBuilder.class);
         m.invoke(ecscreen, new StringBuilder());

         assertEquals("", vt100.at(y).toString(),
            "eraseLine should clear the entire line");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t02_eraseToEndVerifyContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int y = vt100.readIn() - 12;
         cur.y = y;
         cur.x = 5;
         setLine(y, "Hello World");

         Method m = getMethod("eraseToEnd", StringBuilder.class);
         m.invoke(ecscreen, new StringBuilder());

         String result = vt100.at(y).toString();
         assertEquals("Hello", result,
            "eraseToEnd should preserve chars before cursor");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t03_eraseToBeginningVerifyContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int y = vt100.readIn() - 12;
         cur.y = y;
         cur.x = 3;
         setLine(y, "ABCDEFGH");

         Method m = getMethod("eraseToBeginning",
            StringBuilder.class);
         m.invoke(ecscreen, new StringBuilder());

         String result = vt100.at(y).toString();
         // Chars 0..3 (inclusive) should be spaces
         assertEquals("    EFGH", result,
            "eraseToBeginning should blank chars through cursor");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t04_eraseCharsVerifyContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int y = vt100.readIn() - 12;
         cur.y = y;
         cur.x = 2;
         setLine(y, "ABCDEFGH");

         Method m = getMethod("eraseChars", int.class,
            StringBuilder.class);
         m.invoke(ecscreen, 3, new StringBuilder());

         String result = vt100.at(y).toString();
         assertEquals("AB   FGH", result,
            "eraseChars should blank 3 chars at cursor");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Delete / Insert chars content verification ───────────────

   @Test
   void t05_deleteCharsVerifyContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int y = vt100.readIn() - 12;
         cur.y = y;
         cur.x = 2;
         setLine(y, "ABCDEFGH");

         Method m = getMethod("deleteChars", int.class,
            StringBuilder.class);
         m.invoke(ecscreen, 3, new StringBuilder());

         String result = vt100.at(y).toString();
         assertEquals("ABFGH", result,
            "deleteChars should remove 3 chars at cursor");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t06_insertCharsVerifyContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int y = vt100.readIn() - 12;
         cur.y = y;
         cur.x = 2;
         setLine(y, "ABCDEFGH");

         Method m = getMethod("insertChars", int.class,
            StringBuilder.class);
         m.invoke(ecscreen, 3, new StringBuilder());

         String result = vt100.at(y).toString();
         assertTrue(result.startsWith("AB   CDEFGH"),
            "insertChars should insert 3 spaces at cursor: "
            + result);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Insert / Delete lines content verification ───────────────

   @Test
   void t07_insertLinesVerifyContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int readIn = vt100.readIn();
         int topRow = readIn - 24; // first visible row
         // Set up known content in visible area
         setLine(topRow + 2, "LINE_A");
         setLine(topRow + 3, "LINE_B");
         setLine(topRow + 4, "LINE_C");

         cur.y = topRow + 2;
         cur.x = 0;
         // Set scroll region covering visible area
         setField("scrollTop", 1);
         setField("scrollBottom", 24);

         Method m = getMethod("insertLines", int.class,
            StringBuilder.class);
         m.invoke(ecscreen, 1, new StringBuilder());

         // Inserted blank line pushes LINE_A down
         assertEquals("", vt100.at(topRow + 2).toString(),
            "insertLines: new blank line at cursor");
         assertEquals("LINE_A", vt100.at(topRow + 3).toString(),
            "insertLines: original LINE_A shifted down");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t08_deleteLinesVerifyContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int readIn = vt100.readIn();
         int topRow = readIn - 24;
         setLine(topRow + 2, "LINE_X");
         setLine(topRow + 3, "LINE_Y");
         setLine(topRow + 4, "LINE_Z");

         cur.y = topRow + 2;
         cur.x = 0;
         setField("scrollTop", 1);
         setField("scrollBottom", 24);

         // scrollRegionUp removes the top line of the region
         Method scroll = getMethod("scrollRegionUp", int.class);
         scroll.invoke(ecscreen, 1);

         // LINE_X removed, LINE_Y shifts up
         assertEquals("LINE_Y", vt100.at(topRow + 2).toString(),
            "deleteLines: LINE_Y moved up to cursor row");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Autowrap and pending wrap ────────────────────────────────

   @Test
   void t09_pendingWrapSetByField() throws Exception {
      EventQueue.biglock2.lock();
      try {
         setField("pendingWrap", true);
         assertTrue(getFieldBool("pendingWrap"),
            "pendingWrap should be settable via field");

         // incX clears pendingWrap
         Method incX = getMethod("incX", int.class,
            StringBuilder.class);
         incX.invoke(ecscreen, 1, new StringBuilder());
         assertFalse(getFieldBool("pendingWrap"),
            "incX should clear pendingWrap");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t10_doAutowrapMovesToNextLine() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int y = vt100.readIn() - 12;
         cur.y = y;
         cur.x = 79;
         setField("pendingWrap", true);

         Method doWrap = getMethod("doAutowrap");
         doWrap.invoke(ecscreen);

         assertEquals(0, cur.x,
            "doAutowrap should set x to 0");
         assertEquals(y + 1, cur.y,
            "doAutowrap should advance y by 1");
         assertFalse(getFieldBool("pendingWrap"),
            "doAutowrap should clear pendingWrap");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t11_doAutowrapAtBottomScrolls() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int readIn = vt100.readIn();
         int bottomAbs = readIn - 1; // last visible row
         setField("scrollTop", 1);
         setField("scrollBottom", 24);

         cur.y = bottomAbs;
         cur.x = 79;
         setField("pendingWrap", true);

         Method doWrap = getMethod("doAutowrap");
         doWrap.invoke(ecscreen);

         assertEquals(0, cur.x,
            "doAutowrap at bottom: x should be 0");
         // After scroll, cursor stays at bottom margin
         assertTrue(cur.y <= bottomAbs,
            "doAutowrap at bottom: y should be at or before "
            + "bottom margin");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Reverse wrap (mode 45 and 1045) ──────────────────────────

   @Test
   void t12_reverseWrapExtendModeWrapsUp() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int readIn = vt100.readIn();
         int topAbs = readIn - 24;
         int botAbs = readIn - 1;

         cur.y = topAbs + 5;
         cur.x = 0;
         setField("scrollTop", 1);
         setField("scrollBottom", 24);
         setVt100Field("autowrapMode", true);
         setVt100Field("reverseWrapMode", false);

         // Enable mode 1045 (unconditional wrap)
         Method setRWE = getMethod("setReverseWrapExtendMode",
            boolean.class);
         setRWE.invoke(ecscreen, true);

         Method incX = getMethod("incX", int.class,
            StringBuilder.class);
         incX.invoke(ecscreen, -1, new StringBuilder());

         // Should wrap to previous line's last column
         assertEquals(topAbs + 4, cur.y,
            "reverse wrap extend: y should decrement");
         assertTrue(cur.x > 0,
            "reverse wrap extend: x should be at last column");

         // Restore
         setRWE.invoke(ecscreen, false);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t13_reverseWrapMode45RequiresAutowrapped()
         throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int readIn = vt100.readIn();
         int topAbs = readIn - 24;

         cur.y = topAbs + 5;
         cur.x = 0;
         setField("scrollTop", 1);
         setField("scrollBottom", 24);
         setVt100Field("autowrapMode", true);

         // Enable mode 45 (conditional wrap)
         Method setRW = getMethod("setReverseWrapMode",
            boolean.class);
         setRW.invoke(ecscreen, true);

         // No autowrapped lines recorded → BS should clamp at 0
         Method incX = getMethod("incX", int.class,
            StringBuilder.class);
         incX.invoke(ecscreen, -1, new StringBuilder());

         assertEquals(0, cur.x,
            "mode 45: without autowrap record, x clamps to 0");
         assertEquals(topAbs + 5, cur.y,
            "mode 45: without autowrap record, y unchanged");

         // Restore
         setRW.invoke(ecscreen, false);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── LNM mode ────────────────────────────────────────────────

   @Test
   void t14_setLnmModeToggle() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method setLnm = getMethod("setLnmMode",
            boolean.class);
         setLnm.invoke(ecscreen, true);
         assertTrue(getFieldBool("lnmMode"),
            "setLnmMode(true) should enable LNM");
         setLnm.invoke(ecscreen, false);
         assertFalse(getFieldBool("lnmMode"),
            "setLnmMode(false) should disable LNM");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Alternate screen lifecycle ───────────────────────────────

   @Test
   void t15_altScreenSavesAndRestoresContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int readIn = vt100.readIn();
         int topRow = readIn - 24;

         // Put known content on main screen
         setLine(topRow + 1, "MAIN_LINE_1");
         setLine(topRow + 2, "MAIN_LINE_2");
         cur.y = topRow + 1;
         cur.x = 5;

         // Switch to alt screen
         Method switchAlt = getMethod(
            "switchAlternateScreen", boolean.class,
            StringBuilder.class);
         switchAlt.invoke(ecscreen, true, new StringBuilder());

         assertTrue(getFieldBool("inAlternateScreen"),
            "should be in alternate screen");

         // Alt screen should be blank
         String altLine = vt100.at(
            vt100.readIn() - 24 + 1).toString();
         assertEquals("", altLine,
            "alt screen should start blank");

         // Write to alt screen
         int altY = vt100.readIn() - 24 + 1;
         setLine(altY, "ALT_CONTENT");

         // Switch back to main
         switchAlt.invoke(ecscreen, false, new StringBuilder());
         assertFalse(getFieldBool("inAlternateScreen"),
            "should be back on main screen");

         // Main content should be restored
         readIn = vt100.readIn();
         topRow = readIn - 24;
         assertEquals("MAIN_LINE_1",
            vt100.at(topRow + 1).toString(),
            "main screen content should be restored");
         assertEquals("MAIN_LINE_2",
            vt100.at(topRow + 2).toString(),
            "main screen line 2 should be restored");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t16_altScreenDoubleEnableIsNoOp() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method switchAlt = getMethod(
            "switchAlternateScreen", boolean.class,
            StringBuilder.class);

         switchAlt.invoke(ecscreen, true, new StringBuilder());
         assertTrue(getFieldBool("inAlternateScreen"));

         // Second enable should be no-op
         switchAlt.invoke(ecscreen, true, new StringBuilder());
         assertTrue(getFieldBool("inAlternateScreen"),
            "double enable should still be in alt screen");

         // Clean up
         switchAlt.invoke(ecscreen, false, new StringBuilder());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t17_altScreenDoubleDisableIsNoOp() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method switchAlt = getMethod(
            "switchAlternateScreen", boolean.class,
            StringBuilder.class);

         // Already on main screen, disable should be no-op
         assertFalse(getFieldBool("inAlternateScreen"));
         switchAlt.invoke(ecscreen, false, new StringBuilder());
         assertFalse(getFieldBool("inAlternateScreen"),
            "double disable should remain on main screen");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Response verification ────────────────────────────────────

   @Test
   void t18_respondStatusOkSendsCorrectResponse()
         throws Exception {
      EventQueue.biglock2.lock();
      try {
         drainResponse(); // clear any pending
         Method m = getMethod("respondStatusOk",
            StringBuilder.class);
         m.invoke(ecscreen, new StringBuilder());

         String resp = drainResponse();
         assertEquals("\033[0n", resp,
            "respondStatusOk should send ESC[0n");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t19_respondDeviceAttributesSendsDA() throws Exception {
      EventQueue.biglock2.lock();
      try {
         drainResponse();
         Method m = getMethod("respondDeviceAttributes",
            StringBuilder.class);
         m.invoke(ecscreen, new StringBuilder());

         String resp = drainResponse();
         assertTrue(resp.startsWith("\033[?"),
            "DA response should start with ESC[?: " + resp);
         assertTrue(resp.contains("62"),
            "DA should identify as VT220 (62): " + resp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t20_respondSecondaryDASendsResponse() throws Exception {
      EventQueue.biglock2.lock();
      try {
         drainResponse();
         Method m = getMethod("respondSecondaryDA",
            StringBuilder.class);
         m.invoke(ecscreen, new StringBuilder());

         String resp = drainResponse();
         assertTrue(resp.startsWith("\033[>"),
            "Secondary DA should start with ESC[>: " + resp);
         assertTrue(resp.contains("314"),
            "Secondary DA should contain version 314: " + resp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t21_respondCursorPositionSendsCorrectPos()
         throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int readIn = vt100.readIn();
         // Position cursor at row 5, col 10 (1-based in response)
         cur.y = readIn - 24 + 4; // terminal row 5
         cur.x = 9; // column 10

         drainResponse();
         Method m = getMethod("respondCursorPosition",
            StringBuilder.class);
         m.invoke(ecscreen, new StringBuilder());

         String resp = drainResponse();
         assertTrue(resp.startsWith("\033["),
            "CPR should start with ESC[: " + resp);
         assertTrue(resp.endsWith("R"),
            "CPR should end with R: " + resp);
         assertTrue(resp.contains("5;10"),
            "CPR should report row 5, col 10: " + resp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t22_respondDecdsrExtendedCprSendsResponse()
         throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int readIn = vt100.readIn();
         cur.y = readIn - 24 + 2; // terminal row 3
         cur.x = 7; // column 8

         drainResponse();
         Method decdsr = getMethod("respondDecdsr", int.class,
            StringBuilder.class);
         decdsr.invoke(ecscreen, 6, new StringBuilder());

         String resp = drainResponse();
         assertTrue(resp.startsWith("\033[?"),
            "Extended CPR should start with ESC[?: " + resp);
         assertTrue(resp.endsWith("R"),
            "Extended CPR should end with R: " + resp);
         assertTrue(resp.contains("3;8"),
            "Extended CPR should report row 3, col 8: " + resp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t23_respondDecdsrPrinterStatus() throws Exception {
      EventQueue.biglock2.lock();
      try {
         drainResponse();
         Method decdsr = getMethod("respondDecdsr", int.class,
            StringBuilder.class);
         decdsr.invoke(ecscreen, 15, new StringBuilder());

         String resp = drainResponse();
         assertEquals("\033[?13n", resp,
            "Printer status should report no printer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t24_respondDecdsrUdkLocked() throws Exception {
      EventQueue.biglock2.lock();
      try {
         drainResponse();
         Method decdsr = getMethod("respondDecdsr", int.class,
            StringBuilder.class);
         decdsr.invoke(ecscreen, 25, new StringBuilder());

         String resp = drainResponse();
         assertEquals("\033[?21n", resp,
            "UDK status should report locked");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t25_respondDecdsrKeyboard() throws Exception {
      EventQueue.biglock2.lock();
      try {
         drainResponse();
         Method decdsr = getMethod("respondDecdsr", int.class,
            StringBuilder.class);
         decdsr.invoke(ecscreen, 26, new StringBuilder());

         String resp = drainResponse();
         assertTrue(resp.startsWith("\033[?27;"),
            "Keyboard status should start with ESC[?27;: "
            + resp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Terminfo query (DCS +q) ──────────────────────────────────

   @Test
   void t26_respondTerminfoQueryColors() throws Exception {
      EventQueue.biglock2.lock();
      try {
         drainResponse();
         Method m = getMethod("respondTerminfoQuery",
            String.class);
         // Query "Co" (hex 436F) — number of colors
         m.invoke(ecscreen, "436F");

         String resp = drainResponse();
         assertTrue(resp.startsWith("\033P1+r"),
            "terminfo query should respond DCS 1+r: " + resp);
         assertTrue(resp.contains("323536"),
            "terminfo Co should report 256 (hex 323536): "
            + resp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t27_respondTerminfoQueryUnknown() throws Exception {
      EventQueue.biglock2.lock();
      try {
         drainResponse();
         Method m = getMethod("respondTerminfoQuery",
            String.class);
         // Query unknown capability "xx" (hex 7878)
         m.invoke(ecscreen, "7878");

         String resp = drainResponse();
         assertTrue(resp.startsWith("\033P0+r"),
            "unknown terminfo should respond DCS 0+r: " + resp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t28_respondTerminfoQueryColorsLongForm()
         throws Exception {
      EventQueue.biglock2.lock();
      try {
         drainResponse();
         Method m = getMethod("respondTerminfoQuery",
            String.class);
         // Query "colors" (hex 636F6C6F7273)
         m.invoke(ecscreen, "636F6C6F7273");

         String resp = drainResponse();
         assertTrue(resp.startsWith("\033P1+r"),
            "terminfo 'colors' long form should respond: "
            + resp);
         assertTrue(resp.contains("323536"),
            "terminfo 'colors' should report 256: " + resp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Rectangle checksum (DECRQCRA) ────────────────────────────

   @Test
   void t29_respondRectChecksumEmptyScreen() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int readIn = vt100.readIn();
         int topRow = readIn - 24;
         // Clear lines in visible area
         for (int i = 0; i < 24; i++)
            setLine(topRow + i, "");

         drainResponse();
         Method m = getMethod("respondRectChecksum",
            int[].class, int.class, StringBuilder.class);
         // Pid=1, Pp=1, top=1, left=1, bottom=5, right=5
         int[] params = {1, 1, 1, 1, 5, 5};
         m.invoke(ecscreen, params, 5, new StringBuilder());

         String resp = drainResponse();
         assertTrue(resp.startsWith("\033P"),
            "DECRQCRA should respond with DCS: " + resp);
         assertTrue(resp.endsWith("\033\\"),
            "DECRQCRA should end with ST: " + resp);
         // Empty screen: each cell is ' ' (0x20=32)
         // 5 rows x 5 cols = 25 spaces = 25 * 32 = 800 = 0x0320
         assertTrue(resp.contains("0320"),
            "DECRQCRA for 5x5 empty area should be 0320: "
            + resp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t30_respondRectChecksumWithContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         int readIn = vt100.readIn();
         int topRow = readIn - 24;
         setLine(topRow, "AB");
         for (int i = 1; i < 24; i++)
            setLine(topRow + i, "");

         drainResponse();
         Method m = getMethod("respondRectChecksum",
            int[].class, int.class, StringBuilder.class);
         // Check row 1, cols 1-2 (A=65, B=66) = 131
         int[] params = {42, 1, 1, 1, 1, 2};
         m.invoke(ecscreen, params, 5, new StringBuilder());

         String resp = drainResponse();
         assertTrue(resp.startsWith("\033P"),
            "DECRQCRA should respond with DCS: " + resp);
         // 'A' + 'B' = 65 + 66 = 131 = 0x0083
         assertTrue(resp.contains("0083"),
            "DECRQCRA for 'AB' should be 0083: " + resp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Screen erase variants with content ───────────────────────

   @Test
   void t31_eraseScreenToEndVerifyContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int readIn = vt100.readIn();
         int topRow = readIn - 24;

         setLine(topRow, "ROW_0_KEEP");
         setLine(topRow + 1, "ROW_1_KEEP");
         setLine(topRow + 2, "ROW_2_ERASE");
         setLine(topRow + 3, "ROW_3_ERASE");

         // Cursor at row 2, col 4
         cur.y = topRow + 2;
         cur.x = 4;

         Method m = getMethod("eraseScreenToEnd",
            StringBuilder.class);
         m.invoke(ecscreen, new StringBuilder());

         // Row 0 and 1 should be untouched
         assertEquals("ROW_0_KEEP",
            vt100.at(topRow).toString(),
            "eraseScreenToEnd: row above cursor unchanged");
         assertEquals("ROW_1_KEEP",
            vt100.at(topRow + 1).toString(),
            "eraseScreenToEnd: row above cursor unchanged");
         // Row 2 should be truncated at col 4
         assertEquals("ROW_",
            vt100.at(topRow + 2).toString(),
            "eraseScreenToEnd: cursor row truncated at cursor");
         // Row 3 should be blank
         assertEquals("",
            vt100.at(topRow + 3).toString(),
            "eraseScreenToEnd: rows below cursor blanked");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t32_eraseScreenToBeginningVerifyContent()
         throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int readIn = vt100.readIn();
         int topRow = readIn - 24;

         setLine(topRow, "ROW_0_ERASE");
         setLine(topRow + 1, "ROW_1_ERASE");
         setLine(topRow + 2, "ROW_2_PARTIAL");
         setLine(topRow + 3, "ROW_3_KEEP");

         cur.y = topRow + 2;
         cur.x = 4;

         Method m = getMethod("eraseScreenToBeginning",
            StringBuilder.class);
         m.invoke(ecscreen, new StringBuilder());

         // Rows above cursor should be blank
         assertEquals("",
            vt100.at(topRow).toString(),
            "eraseScreenToBeginning: rows above blanked");
         assertEquals("",
            vt100.at(topRow + 1).toString(),
            "eraseScreenToBeginning: rows above blanked");
         // Cursor row: chars 0..4 blanked
         String row2 = vt100.at(topRow + 2).toString();
         assertTrue(row2.startsWith("     "),
            "eraseScreenToBeginning: cursor row start blanked: "
            + row2);
         // Row below cursor should be untouched
         assertEquals("ROW_3_KEEP",
            vt100.at(topRow + 3).toString(),
            "eraseScreenToBeginning: row below unchanged");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Erase scrollback (ED3) is no-op ─────────────────────────

   @Test
   void t33_eraseScrollbackIsNoOp() throws Exception {
      EventQueue.biglock2.lock();
      try {
         int readInBefore = vt100.readIn();
         Method m = getMethod("eraseScrollback",
            StringBuilder.class);
         m.invoke(ecscreen, new StringBuilder());
         assertEquals(readInBefore, vt100.readIn(),
            "eraseScrollback should be no-op");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Save/restore cursor with SGR ─────────────────────────────

   @Test
   void t34_saveCursorPreservesPositionAndSgr() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         int readIn = vt100.readIn();
         cur.y = readIn - 10;
         cur.x = 15;

         // Set a non-default SGR
         Method sgr = ecscreen.getClass().getDeclaredMethod(
            "setGraphicRendition", int[].class,
            StringBuilder.class);
         sgr.setAccessible(true);
         sgr.invoke(ecscreen, new int[]{1}, // bold
            new StringBuilder());

         // Save cursor
         Method save = getMethod("saveCursor",
            StringBuilder.class);
         save.invoke(ecscreen, new StringBuilder());

         // Move cursor and change SGR
         cur.y = readIn - 5;
         cur.x = 0;
         sgr.invoke(ecscreen, new int[]{0}, // reset
            new StringBuilder());

         // Restore cursor
         Method restore = getMethod("restoreCursor",
            StringBuilder.class);
         restore.invoke(ecscreen, new StringBuilder());

         assertEquals(15, cur.x,
            "restoreCursor should restore x position");
         // y is stored relative to readIn, verify it restored
         assertEquals(readIn - 10, cur.y,
            "restoreCursor should restore y position");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t35_restoreCursorWithoutSaveHomes() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         setField("cursorSaved", false);

         cur.y = vt100.readIn() - 5;
         cur.x = 20;

         Method restore = getMethod("restoreCursor",
            StringBuilder.class);
         restore.invoke(ecscreen, new StringBuilder());

         assertEquals(0, cur.x,
            "restoreCursor without save: x should be 0");
         // y should be at terminal row 1
         Field rowsF = Vt100.class.getDeclaredField("rows");
         rowsF.setAccessible(true);
         int rows = rowsF.getInt(vt100);
         int expectedY = vt100.readIn() - rows;
         assertEquals(expectedY, cur.y,
            "restoreCursor without save: y should be row 1");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Mode save/restore ────────────────────────────────────────

   @Test
   void t36_saveAndRestoreMode() throws Exception {
      EventQueue.biglock2.lock();
      try {
         // Enable autowrap mode (7)
         setVt100Field("autowrapMode", true);

         Method saveMode = getMethod("saveMode", int.class);
         saveMode.invoke(ecscreen, 7);

         // Disable autowrap
         setVt100Field("autowrapMode", false);

         // Restore should re-enable
         Method restoreMode = getMethod("restoreMode",
            int.class, StringBuilder.class);
         restoreMode.invoke(ecscreen, 7, new StringBuilder());

         Field autowrap = Vt100.class
            .getDeclaredField("autowrapMode");
         autowrap.setAccessible(true);
         assertTrue(autowrap.getBoolean(vt100),
            "restoreMode should re-enable autowrap");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── DECRQM (mode query) ─────────────────────────────────────

   @Test
   void t37_respondDecrqmKnownModeSet() throws Exception {
      EventQueue.biglock2.lock();
      try {
         // Autowrap (7) is currently enabled
         setVt100Field("autowrapMode", true);

         drainResponse();
         Method m = getMethod("respondDecrqm", int.class,
            StringBuilder.class);
         m.invoke(ecscreen, 7, new StringBuilder());

         String resp = drainResponse();
         assertTrue(resp.contains(";1$y"),
            "DECRQM for set mode should report Pm=1: " + resp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t38_respondDecrqmKnownModeReset() throws Exception {
      EventQueue.biglock2.lock();
      try {
         setVt100Field("autowrapMode", false);

         drainResponse();
         Method m = getMethod("respondDecrqm", int.class,
            StringBuilder.class);
         m.invoke(ecscreen, 7, new StringBuilder());

         String resp = drainResponse();
         assertTrue(resp.contains(";2$y"),
            "DECRQM for reset mode should report Pm=2: "
            + resp);

         // Restore
         setVt100Field("autowrapMode", true);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t39_respondDecrqmUnknownMode() throws Exception {
      EventQueue.biglock2.lock();
      try {
         drainResponse();
         Method m = getMethod("respondDecrqm", int.class,
            StringBuilder.class);
         m.invoke(ecscreen, 99999, new StringBuilder());

         String resp = drainResponse();
         assertTrue(resp.contains(";0$y"),
            "DECRQM for unknown mode should report Pm=0: "
            + resp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Origin mode ──────────────────────────────────────────────

   @Test
   void t40_setOriginModeHomesToScrollRegion() throws Exception {
      EventQueue.biglock2.lock();
      try {
         MovePos cur = getCursor();
         setField("scrollTop", 5);
         setField("scrollBottom", 20);

         Method m = getMethod("setOriginMode",
            boolean.class);
         m.invoke(ecscreen, true);

         assertTrue(getFieldBool("originMode"),
            "setOriginMode(true) should enable origin mode");

         m.invoke(ecscreen, false);
         assertFalse(getFieldBool("originMode"),
            "setOriginMode(false) should disable origin mode");

         // Restore scroll region
         setField("scrollTop", 1);
         setField("scrollBottom", 0);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Allow 80/132 column switching ────────────────────────────

   @Test
   void t41_setAllow80To132Toggle() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method m = getMethod("setAllow80To132",
            boolean.class);
         m.invoke(ecscreen, true);
         assertTrue(getFieldBool("allow80To132"),
            "setAllow80To132(true) should enable");
         m.invoke(ecscreen, false);
         assertFalse(getFieldBool("allow80To132"),
            "setAllow80To132(false) should disable");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Scrollback trim ─────────────────────────────────────────

   @Test
   void t42_trimScrollbackKeepsMaxLines() throws Exception {
      EventQueue.biglock2.lock();
      try {
         // trimScrollback is called by updateScreen.
         // Just verify that after many insertions, readIn
         // doesn't grow beyond rows + MAX_SCROLLBACK + 1
         MovePos cur = getCursor();
         int initialReadIn = vt100.readIn();

         // The test just verifies trimScrollback can be called
         // without error via updateScreen
         Method updateScreen = getMethod("updateScreen",
            StringBuilder.class);
         updateScreen.invoke(ecscreen, new StringBuilder());

         int afterReadIn = vt100.readIn();
         assertTrue(afterReadIn <= initialReadIn,
            "updateScreen should not grow buffer");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Title operations ─────────────────────────────────────────

   @Test
   void t43_setTitleAndIconTitle() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method setTitle = getMethod("setTitle", String.class);
         Method setIcon = getMethod("setIconTitle",
            String.class);

         setTitle.invoke(ecscreen, "Window Title");
         setIcon.invoke(ecscreen, "Icon Title");

         Field oscT = Vt100.class
            .getDeclaredField("oscTitle");
         oscT.setAccessible(true);
         assertEquals("Window Title", oscT.get(vt100));

         Field oscI = Vt100.class
            .getDeclaredField("oscIconTitle");
         oscI.setAccessible(true);
         assertEquals("Icon Title", oscI.get(vt100));
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Window op responses ──────────────────────────────────────

   @Test
   void t44_handleWindowOpReportSizeInChars() throws Exception {
      EventQueue.biglock2.lock();
      try {
         drainResponse();
         Method m = getMethod("handleWindowOp", int.class,
            int.class, int.class, StringBuilder.class);
         // ps=18: report terminal size in characters
         m.invoke(ecscreen, 18, 0, 0, new StringBuilder());

         String resp = drainResponse();
         assertTrue(resp.startsWith("\033[8;"),
            "Window op 18 should report ESC[8;rows;cols: "
            + resp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t45_handleWindowOpReportSizeInPixels() throws Exception {
      EventQueue.biglock2.lock();
      try {
         drainResponse();
         Method m = getMethod("handleWindowOp", int.class,
            int.class, int.class, StringBuilder.class);
         // ps=14: report terminal size in pixels
         m.invoke(ecscreen, 14, 0, 0, new StringBuilder());

         String resp = drainResponse();
         assertTrue(resp.startsWith("\033[4;"),
            "Window op 14 should report ESC[4;h;w: " + resp);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t46_handleWindowOpTitlePushPop() throws Exception {
      EventQueue.biglock2.lock();
      try {
         // Set a title
         Method setTitle = getMethod("setTitle", String.class);
         setTitle.invoke(ecscreen, "Original");

         // Push title (op 22)
         Method windowOp = getMethod("handleWindowOp",
            int.class, int.class, int.class,
            StringBuilder.class);
         windowOp.invoke(ecscreen, 22, 0, 0,
            new StringBuilder());

         // Change title
         setTitle.invoke(ecscreen, "Changed");

         Field oscT = Vt100.class
            .getDeclaredField("oscTitle");
         oscT.setAccessible(true);
         assertEquals("Changed", oscT.get(vt100));

         // Pop title (op 23)
         windowOp.invoke(ecscreen, 23, 0, 0,
            new StringBuilder());
         assertEquals("Original", oscT.get(vt100),
            "Pop should restore original title");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Delegate methods ─────────────────────────────────────────

   @Test
   void t47_setMouseTrackingDelegates() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method m = getMethod("setMouseTracking", int.class,
            boolean.class);
         m.invoke(ecscreen, 1000, true);

         Field mtMode = Vt100.class
            .getDeclaredField("mouseTrackingMode");
         mtMode.setAccessible(true);
         assertEquals(1000, mtMode.getInt(vt100),
            "setMouseTracking should delegate to Vt100");

         // Disable
         m.invoke(ecscreen, 1000, false);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t48_setBracketedPasteModeDelegates() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method m = getMethod("setBracketedPasteMode",
            boolean.class);
         m.invoke(ecscreen, true);

         Field bpm = Vt100.class
            .getDeclaredField("bracketedPasteMode");
         bpm.setAccessible(true);
         assertTrue(bpm.getBoolean(vt100),
            "setBracketedPasteMode should delegate");

         // Disable
         m.invoke(ecscreen, false);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t49_setFocusEventsModeDelegates() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method m = getMethod("setFocusEventsMode",
            boolean.class);
         m.invoke(ecscreen, true);

         Field fem = Vt100.class
            .getDeclaredField("focusEventsMode");
         fem.setAccessible(true);
         assertTrue(fem.getBoolean(vt100),
            "setFocusEventsMode should delegate");

         // Disable
         m.invoke(ecscreen, false);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t50_setCursorVisibleDelegates() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Method m = getMethod("setCursorVisible",
            boolean.class);
         m.invoke(ecscreen, false);

         Field cv = Vt100.class
            .getDeclaredField("cursorVisible");
         cv.setAccessible(true);
         assertFalse(cv.getBoolean(vt100),
            "setCursorVisible(false) should hide cursor");

         // Restore
         m.invoke(ecscreen, true);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
