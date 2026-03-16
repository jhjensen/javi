package javi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link Vt100Parser} state machine logic.
 *
 * <p>
 * Uses reflection to invoke the private {@code doChar} method
 * directly, bypassing the reader thread and EventQueue. A recording
 * {@link VScreen} subclass captures all method calls for assertion.
 * </p>
 */
class Vt100ParserJUnitTest {

   private RecordingScreen screen;
   private Vt100Parser parser;
   private Method doChar;
   private Field stateField;
   private Field sbField;
   private PipedOutputStream pipeOut;

   // State constants mirroring Vt100Parser private fields
   private static final int NORM = 0;
   private static final int ESC = 1;
   private static final int GETNUM = 2;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void setUp() throws Exception {
      screen = new RecordingScreen();
      // Use PipedInputStream — the reader thread blocks on read()
      // since we hold the write end. This keeps the thread idle.
      pipeOut = new PipedOutputStream();
      PipedInputStream pipeIn = new PipedInputStream(pipeOut);
      BufferedInputStream bis = new BufferedInputStream(pipeIn);
      parser = new Vt100Parser(screen, bis);
      // Thread is now blocked on PipedInputStream.read() which
      // internally calls wait() — interruptible by stop().

      doChar = Vt100Parser.class.getDeclaredMethod("doChar", char.class);
      doChar.setAccessible(true);
      stateField = Vt100Parser.class.getDeclaredField("state");
      stateField.setAccessible(true);
      sbField = Vt100Parser.class.getDeclaredField("sb");
      sbField.setAccessible(true);
   }

   @AfterEach
   void tearDown() throws Exception {
      parser.stop();
      pipeOut.close();
      // Give the thread time to exit
      Field rtField = Vt100Parser.class.getDeclaredField("rthread");
      rtField.setAccessible(true);
      Thread rt = (Thread) rtField.get(parser);
      rt.join(2000);
   }

   private void feed(String chars) throws Exception {
      for (int i = 0; i < chars.length(); i++)
         doChar.invoke(parser, chars.charAt(i));
   }

   private int state() throws Exception {
      return stateField.getInt(parser);
   }

   private String sbContents() throws Exception {
      return ((StringBuilder) sbField.get(parser)).toString();
   }

   // ── Recording VScreen ──────────────────────────────────────

   static class RecordingScreen extends VScreen {
      final List<String> calls = new ArrayList<>();
      int lastIncX, lastIncY, lastSetX, lastSetY;
      int lastSetXYx, lastSetXYy;
      int lastEraseCharsCount, lastInsertLinesCount;
      int lastDeleteLinesCount;
      int lastScrollUpCount, lastScrollDownCount;
      boolean insertMode;
      boolean saveCursorCalled, restoreCursorCalled;
      boolean eraseScreenCalled, eraseToEndCalled;
      boolean eraseLineCalled;
      boolean eraseScreenToEndCalled;
      boolean eraseScreenToBeginningCalled;
      boolean eraseToBeginningCalled;
      boolean bellCalled;
      boolean altScreenEnabled;
      String lastTitle;
      int[] lastSGR;

      void incX(int amount, StringBuilder sb) {
         calls.add("incX:" + amount);
         lastIncX = amount;
      }

      void incY(int amount, StringBuilder sb) {
         calls.add("incY:" + amount);
         lastIncY = amount;
      }

      void setX(int val, StringBuilder sb) {
         calls.add("setX:" + val);
         lastSetX = val;
      }

      void setY(int val, StringBuilder sb) {
         calls.add("setY:" + val);
         lastSetY = val;
      }

      void setXY(int xval, int yval, StringBuilder sb) {
         calls.add("setXY:" + xval + "," + yval);
         lastSetXYx = xval;
         lastSetXYy = yval;
      }

      void eraseScreen(StringBuilder sb) {
         calls.add("eraseScreen");
         eraseScreenCalled = true;
      }

      void eraseToEnd(StringBuilder sb) {
         calls.add("eraseToEnd");
         eraseToEndCalled = true;
      }

      void eraseLine(StringBuilder sb) {
         calls.add("eraseLine");
         eraseLineCalled = true;
      }

      void eraseChars(int count, StringBuilder sb) {
         calls.add("eraseChars:" + count);
         lastEraseCharsCount = count;
      }

      void insertLines(int count, StringBuilder sb) {
         calls.add("insertLines:" + count);
         lastInsertLinesCount = count;
      }

      void setInsertMode(boolean val, StringBuilder sb) {
         calls.add("setInsertMode:" + val);
         insertMode = val;
      }

      void updateScreen(StringBuilder sb) {
         calls.add("updateScreen");
      }

      void saveCursor(StringBuilder sb) {
         calls.add("saveCursor");
         saveCursorCalled = true;
      }

      void restoreCursor(StringBuilder sb) {
         calls.add("restoreCursor");
         restoreCursorCalled = true;
      }

      @Override
      void deleteLines(int count, StringBuilder sb) {
         calls.add("deleteLines:" + count);
         lastDeleteLinesCount = count;
      }

      @Override
      void scrollUp(int count, StringBuilder sb) {
         calls.add("scrollUp:" + count);
         lastScrollUpCount = count;
      }

      @Override
      void scrollDown(int count, StringBuilder sb) {
         calls.add("scrollDown:" + count);
         lastScrollDownCount = count;
      }

      @Override
      void eraseToBeginning(StringBuilder sb) {
         calls.add("eraseToBeginning");
         eraseToBeginningCalled = true;
      }

      @Override
      void eraseScreenToBeginning(StringBuilder sb) {
         calls.add("eraseScreenToBeginning");
         eraseScreenToBeginningCalled = true;
      }

      @Override
      void eraseScreenToEnd(StringBuilder sb) {
         calls.add("eraseScreenToEnd");
         eraseScreenToEndCalled = true;
      }

      @Override
      void setGraphicRendition(int[] params, StringBuilder sb) {
         calls.add("setGraphicRendition");
         lastSGR = params.clone();
      }

      @Override
      void bell() {
         calls.add("bell");
         bellCalled = true;
      }

      @Override
      void setTitle(String title) {
         calls.add("setTitle:" + title);
         lastTitle = title;
      }

      @Override
      void switchAlternateScreen(boolean enable, StringBuilder sb) {
         calls.add("switchAlternateScreen:" + enable);
         altScreenEnabled = enable;
      }
   }

   // ── NORM state tests ───────────────────────────────────────

   @Test
   void normalCharAppendedToBuffer() throws Exception {
      feed("A");
      assertEquals("A", sbContents());
      assertEquals(NORM, state());
   }

   @Test
   void multipleNormalCharsAccumulate() throws Exception {
      feed("Hello");
      assertEquals("Hello", sbContents());
   }

   @Test
   void escSwitchesToEscState() throws Exception {
      feed("\u001b");
      assertEquals(ESC, state());
   }

   @Test
   void bellCharacterCallsBell() throws Exception {
      feed("\u0007");
      assertTrue(screen.bellCalled);
   }

   @Test
   void backspaceMovesLeft() throws Exception {
      feed("\b");
      assertEquals(-1, screen.lastIncX);
   }

   @Test
   void carriageReturnEntersCRState() throws Exception {
      feed("\r");
      assertEquals(9, state()); // CR state
   }

   @Test
   void newlineAppendsToBuffer() throws Exception {
      feed("\n");
      assertEquals("\n", sbContents());
   }

   @Test
   void tabAppendsToBuffer() throws Exception {
      feed("\t");
      assertEquals("\t", sbContents());
   }

   @Test
   void nullCharIgnored() throws Exception {
      feed("\0");
      assertEquals("", sbContents());
   }

   @Test
   void delCharIgnored() throws Exception {
      feed("\177");
      assertEquals("", sbContents());
   }

   // ── CR state tests ─────────────────────────────────────────

   @Test
   void crFollowedByNewlineReturnsToNorm() throws Exception {
      feed("X\r\n");
      assertEquals(NORM, state());
   }

   @Test
   void crFollowedByCharSetsXAndProcesses() throws Exception {
      feed("X\rY");
      assertEquals(1, screen.lastSetX);
      assertEquals(NORM, state());
   }

   // ── ESC state tests ───────────────────────────────────────

   @Test
   void escBracketEntersGetnum() throws Exception {
      feed("\u001b[");
      assertEquals(GETNUM, state());
   }

   @Test
   void escSevenSavesCursor() throws Exception {
      feed("\u001b7");
      assertTrue(screen.saveCursorCalled);
   }

   @Test
   void escEightRestoresCursor() throws Exception {
      feed("\u001b8");
      assertTrue(screen.restoreCursorCalled);
   }

   @Test
   void escMMovesUp() throws Exception {
      feed("\u001bM");
      assertEquals(-1, screen.lastIncY);
   }

   @Test
   void escGreaterReturnsToNorm() throws Exception {
      feed("\u001b>");
      assertEquals(NORM, state());
   }

   @Test
   void escEqualsReturnsToNorm() throws Exception {
      feed("\u001b=");
      assertEquals(NORM, state());
   }

   @Test
   void escBracketRightEntersOSC() throws Exception {
      feed("\u001b]");
      assertEquals(5, state()); // OSCMODE
   }

   // ── CSI cursor movement tests ─────────────────────────────

   @Test
   void csiCursorUpDefault() throws Exception {
      feed("\u001b[A");
      // No digit entered: numacc[0]=0, def=false → incY(0)
      assertEquals(0, screen.lastIncY);
   }

   @Test
   void csiCursorUpWithCount() throws Exception {
      feed("\u001b[5A");
      assertEquals(-5, screen.lastIncY);
   }

   @Test
   void csiCursorDownDefault() throws Exception {
      feed("\u001b[B");
      assertEquals(0, screen.lastIncY);
   }

   @Test
   void csiCursorDownWithCount() throws Exception {
      feed("\u001b[3B");
      assertEquals(3, screen.lastIncY);
   }

   @Test
   void csiCursorRightDefault() throws Exception {
      feed("\u001b[C");
      assertEquals(0, screen.lastIncX);
   }

   @Test
   void csiCursorRightWithCount() throws Exception {
      feed("\u001b[4C");
      assertEquals(4, screen.lastIncX);
   }

   @Test
   void csiCursorLeftDefault() throws Exception {
      feed("\u001b[D");
      assertEquals(0, screen.lastIncX);
   }

   @Test
   void csiCursorLeftWithCount() throws Exception {
      feed("\u001b[2D");
      assertEquals(-2, screen.lastIncX);
   }

   // ── CSI cursor position tests ─────────────────────────────

   @Test
   void csiHomeDefault() throws Exception {
      feed("\u001b[H");
      // No digits: currnumacc=0, def=false → case 1: setXY(1, numacc[0], sb)
      assertEquals(1, screen.lastSetXYx);
      assertEquals(0, screen.lastSetXYy);
   }

   @Test
   void csiHomeWithRow() throws Exception {
      feed("\u001b[5H");
      assertEquals(1, screen.lastSetXYx);
      assertEquals(5, screen.lastSetXYy);
   }

   @Test
   void csiHomeWithRowAndCol() throws Exception {
      feed("\u001b[10;20H");
      assertEquals(20, screen.lastSetXYx);
      assertEquals(10, screen.lastSetXYy);
   }

   @Test
   void csiColumnAbsolute() throws Exception {
      feed("\u001b[15G");
      assertEquals(15, screen.lastSetX);
   }

   @Test
   void csiRowAbsolute() throws Exception {
      feed("\u001b[8d");
      assertEquals(8, screen.lastSetY);
   }

   @Test
   void csiNextLine() throws Exception {
      feed("\u001b[3E");
      assertEquals(3, screen.lastIncY);
      assertEquals(1, screen.lastSetX);
   }

   @Test
   void csiPrevLine() throws Exception {
      feed("\u001b[2F");
      assertEquals(-2, screen.lastIncY);
      assertEquals(1, screen.lastSetX);
   }

   // ── CSI erase tests ───────────────────────────────────────

   @Test
   void csiEraseScreenToEnd() throws Exception {
      feed("\u001b[0J");
      assertTrue(screen.eraseScreenToEndCalled);
   }

   @Test
   void csiEraseScreenToBeginning() throws Exception {
      feed("\u001b[1J");
      assertTrue(screen.eraseScreenToBeginningCalled);
   }

   @Test
   void csiEraseEntireScreen() throws Exception {
      feed("\u001b[2J");
      assertTrue(screen.eraseScreenCalled);
   }

   @Test
   void csiEraseLineToEnd() throws Exception {
      feed("\u001b[0K");
      assertTrue(screen.eraseToEndCalled);
   }

   @Test
   void csiEraseLineToBeginning() throws Exception {
      feed("\u001b[1K");
      assertTrue(screen.eraseToBeginningCalled);
   }

   @Test
   void csiEraseEntireLine() throws Exception {
      feed("\u001b[2K");
      assertTrue(screen.eraseLineCalled);
   }

   @Test
   void csiEraseCharsDefault() throws Exception {
      feed("\u001b[P");
      // def=false → eraseChars(numacc[0]) = eraseChars(0)
      assertEquals(0, screen.lastEraseCharsCount);
   }

   @Test
   void csiEraseCharsWithCount() throws Exception {
      feed("\u001b[5P");
      assertEquals(5, screen.lastEraseCharsCount);
   }

   @Test
   void csiEraseCharX() throws Exception {
      feed("\u001b[3X");
      assertEquals(3, screen.lastEraseCharsCount);
   }

   // ── CSI line operations ────────────────────────────────────

   @Test
   void csiInsertLinesDefault() throws Exception {
      feed("\u001b[L");
      assertEquals(1, screen.lastInsertLinesCount);
   }

   @Test
   void csiInsertLinesWithCount() throws Exception {
      feed("\u001b[4L");
      assertEquals(4, screen.lastInsertLinesCount);
   }

   @Test
   void csiDeleteLinesDefault() throws Exception {
      feed("\u001b[M");
      // def=false → deleteLines(numacc[0]) = deleteLines(0)
      // But code has: deleteLines(def ? 1 : numacc[currnumacc])
      assertEquals(0, screen.lastDeleteLinesCount);
   }

   @Test
   void csiDeleteLinesWithCount() throws Exception {
      feed("\u001b[3M");
      assertEquals(3, screen.lastDeleteLinesCount);
   }

   @Test
   void csiScrollUpDefault() throws Exception {
      feed("\u001b[S");
      assertEquals(0, screen.lastScrollUpCount);
   }

   @Test
   void csiScrollUpWithCount() throws Exception {
      feed("\u001b[5S");
      assertEquals(5, screen.lastScrollUpCount);
   }

   @Test
   void csiScrollDownDefault() throws Exception {
      feed("\u001b[T");
      assertEquals(0, screen.lastScrollDownCount);
   }

   @Test
   void csiScrollDownWithCount() throws Exception {
      feed("\u001b[2T");
      assertEquals(2, screen.lastScrollDownCount);
   }

   // ── CSI mode tests ─────────────────────────────────────────

   @Test
   void csiSetInsertMode() throws Exception {
      feed("\u001b[4h");
      assertTrue(screen.insertMode);
   }

   @Test
   void csiResetInsertMode() throws Exception {
      feed("\u001b[4l");
      assertFalse(screen.insertMode);
   }

   @Test
   void csiSGRCalled() throws Exception {
      feed("\u001b[1m");
      assertNotNull(screen.lastSGR);
   }

   // ── CSI save/restore cursor ────────────────────────────────

   @Test
   void csiSaveCursor() throws Exception {
      feed("\u001b[s");
      assertTrue(screen.saveCursorCalled);
   }

   @Test
   void csiRestoreCursor() throws Exception {
      feed("\u001b[u");
      assertTrue(screen.restoreCursorCalled);
   }

   // ── Private mode tests ─────────────────────────────────────

   @Test
   void csiPrivateModeAltScreenOn() throws Exception {
      feed("\u001b[?1049h");
      assertTrue(screen.altScreenEnabled);
   }

   @Test
   void csiPrivateModeAltScreenOff() throws Exception {
      feed("\u001b[?1049l");
      assertFalse(screen.altScreenEnabled);
   }

   @Test
   void csiPrivateMode47On() throws Exception {
      feed("\u001b[?47h");
      assertTrue(screen.altScreenEnabled);
   }

   @Test
   void csiPrivateMode1047On() throws Exception {
      feed("\u001b[?1047h");
      assertTrue(screen.altScreenEnabled);
   }

   // ── OSC tests ──────────────────────────────────────────────

   @Test
   void oscSetTitle() throws Exception {
      // ESC ] 0 ; title BEL
      feed("\u001b]0;My Title\u0007");
      assertEquals("My Title", screen.lastTitle);
   }

   @Test
   void oscSetWindowTitle() throws Exception {
      // ESC ] 2 ; title BEL
      feed("\u001b]2;Window Title\u0007");
      assertEquals("Window Title", screen.lastTitle);
   }

   // ── HVP (f) tests ─────────────────────────────────────────

   @Test
   void hvpHomeDefault() throws Exception {
      feed("\u001b[f");
      // Same as H default: case 1 → setXY(1, numacc[0], sb)
      assertEquals(1, screen.lastSetXYx);
      assertEquals(0, screen.lastSetXYy);
   }

   @Test
   void hvpWithRowAndCol() throws Exception {
      feed("\u001b[5;10f");
      assertEquals(10, screen.lastSetXYx);
      assertEquals(5, screen.lastSetXYy);
   }

   // ── Multiple sequences ─────────────────────────────────────

   @Test
   void textThenEscapeSequence() throws Exception {
      feed("abc\u001b[2J");
      assertTrue(screen.eraseScreenCalled);
      assertTrue(sbContents().contains("abc"));
   }

   @Test
   void multipleSequencesInRow() throws Exception {
      feed("\u001b[5A\u001b[3B");
      // Both sequences dispatched
      assertTrue(screen.calls.contains("incY:-5"));
      assertTrue(screen.calls.contains("incY:3"));
   }
}
