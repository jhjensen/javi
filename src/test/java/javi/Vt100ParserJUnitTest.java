package javi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
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
      int lastInsertCharsCount;
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

      @Override
      void insertChars(int count, StringBuilder sb) {
         calls.add("insertChars:" + count);
         lastInsertCharsCount = count;
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

      int mouseTrackMode;
      boolean mouseTrackEnable;
      boolean sgrMouseEnabled;
      boolean bracketedPasteEnabled;
      boolean focusEventsEnabled;
      boolean autowrapEnabled;
      boolean cursorBlinkEnabled;
      boolean appCursorKeysEnabled;
      boolean cursorVisibleState = true;
      boolean respondDACalled;
      boolean respondCPRCalled;
      boolean handleTabCalled;
      boolean indexCalled, reverseIndexCalled, nextLineCalled;
      boolean screenAlignmentCalled;
      int scrollRegionTop, scrollRegionBottom;
      boolean originModeEnabled;

      @Override
      void setMouseTracking(int mode, boolean enable) {
         calls.add("setMouseTracking:" + mode + ":" + enable);
         mouseTrackMode = mode;
         mouseTrackEnable = enable;
      }

      @Override
      void setSgrMouseMode(boolean enable) {
         calls.add("setSgrMouseMode:" + enable);
         sgrMouseEnabled = enable;
      }

      @Override
      void setBracketedPasteMode(boolean enable) {
         calls.add("setBracketedPasteMode:" + enable);
         bracketedPasteEnabled = enable;
      }

      @Override
      void setFocusEventsMode(boolean enable) {
         calls.add("setFocusEventsMode:" + enable);
         focusEventsEnabled = enable;
      }

      @Override
      void setAutowrapMode(boolean enable) {
         calls.add("setAutowrapMode:" + enable);
         autowrapEnabled = enable;
      }

      @Override
      void setCursorBlinkMode(boolean enable) {
         calls.add("setCursorBlinkMode:" + enable);
         cursorBlinkEnabled = enable;
      }

      @Override
      void setApplicationCursorKeys(boolean enable) {
         calls.add("setApplicationCursorKeys:" + enable);
         appCursorKeysEnabled = enable;
      }

      @Override
      void setCursorVisible(boolean visible) {
         calls.add("setCursorVisible:" + visible);
         cursorVisibleState = visible;
      }

      @Override
      void respondDeviceAttributes(StringBuilder sb) {
         calls.add("respondDeviceAttributes");
         respondDACalled = true;
      }

      @Override
      void respondCursorPosition(StringBuilder sb) {
         calls.add("respondCursorPosition");
         respondCPRCalled = true;
      }

      @Override
      void handleTab(StringBuilder sb) {
         calls.add("handleTab");
         handleTabCalled = true;
      }

      @Override
      void setScrollRegion(int top, int bottom, StringBuilder sb) {
         calls.add("setScrollRegion:" + top + "," + bottom);
         scrollRegionTop = top;
         scrollRegionBottom = bottom;
      }

      @Override
      void index(StringBuilder sb) {
         calls.add("index");
         indexCalled = true;
      }

      @Override
      void reverseIndex(StringBuilder sb) {
         calls.add("reverseIndex");
         reverseIndexCalled = true;
      }

      @Override
      void nextLine(StringBuilder sb) {
         calls.add("nextLine");
         nextLineCalled = true;
      }

      @Override
      void screenAlignmentDisplay(StringBuilder sb) {
         calls.add("screenAlignmentDisplay");
         screenAlignmentCalled = true;
      }

      @Override
      void setOriginMode(boolean enable) {
         calls.add("setOriginMode:" + enable);
         originModeEnabled = enable;
      }

      boolean respondRectChecksumCalled;
      int[] rectChecksumParams;
      int rectChecksumHighParam;

      @Override
      void respondRectChecksum(int[] params, int highParam,
            StringBuilder sb) {
         calls.add("respondRectChecksum");
         respondRectChecksumCalled = true;
         rectChecksumParams = params.clone();
         rectChecksumHighParam = highParam;
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
      assertTrue(screen.handleTabCalled,
         "HT should be dispatched to handleTab");
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
   void escMCallsReverseIndexOld() throws Exception {
      feed("\u001bM");
      assertTrue(screen.reverseIndexCalled,
         "ESC M should call reverseIndex");
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
      // No digit: default parameter = 1 per VT100 spec
      assertEquals(-1, screen.lastIncY);
   }

   @Test
   void csiCursorUpWithCount() throws Exception {
      feed("\u001b[5A");
      assertEquals(-5, screen.lastIncY);
   }

   @Test
   void csiCursorDownDefault() throws Exception {
      feed("\u001b[B");
      // No digit: default parameter = 1 per VT100 spec
      assertEquals(1, screen.lastIncY);
   }

   @Test
   void csiCursorDownWithCount() throws Exception {
      feed("\u001b[3B");
      assertEquals(3, screen.lastIncY);
   }

   @Test
   void csiCursorRightDefault() throws Exception {
      feed("\u001b[C");
      // No digit: default parameter = 1 per VT100 spec
      assertEquals(1, screen.lastIncX);
   }

   @Test
   void csiCursorRightWithCount() throws Exception {
      feed("\u001b[4C");
      assertEquals(4, screen.lastIncX);
   }

   @Test
   void csiCursorLeftDefault() throws Exception {
      feed("\u001b[D");
      // No digit: default parameter = 1 per VT100 spec
      assertEquals(-1, screen.lastIncX);
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
      // No digits: def=true → case 0: setXY(1, 1) (home)
      assertEquals(1, screen.lastSetXYx);
      assertEquals(1, screen.lastSetXYy);
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
      // No digit: default parameter = 1 per VT100 spec
      assertEquals(1, screen.lastEraseCharsCount);
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
      // No digit: default parameter = 1 per VT100 spec
      assertEquals(1, screen.lastDeleteLinesCount);
   }

   @Test
   void csiDeleteLinesWithCount() throws Exception {
      feed("\u001b[3M");
      assertEquals(3, screen.lastDeleteLinesCount);
   }

   @Test
   void csiScrollUpDefault() throws Exception {
      feed("\u001b[S");
      // No digit: default parameter = 1 per VT100 spec
      assertEquals(1, screen.lastScrollUpCount);
   }

   @Test
   void csiScrollUpWithCount() throws Exception {
      feed("\u001b[5S");
      assertEquals(5, screen.lastScrollUpCount);
   }

   @Test
   void csiScrollDownDefault() throws Exception {
      feed("\u001b[T");
      // No digit: default parameter = 1 per VT100 spec
      assertEquals(1, screen.lastScrollDownCount);
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
      // No digit: def=true → case 0: setXY(1, 1) (home)
      assertEquals(1, screen.lastSetXYx);
      assertEquals(1, screen.lastSetXYy);
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

   // ── SGR 256-colour tests ─────────────────────────────────

   @Test
   void sgr256FgColor() throws Exception {
      // ESC[38;5;196m — 256-color foreground (red)
      feed("\u001b[38;5;196m");
      assertNotNull(screen.lastSGR, "SGR should be called");
      assertArrayEquals(new int[]{38, 5, 196},
         screen.lastSGR);
   }

   @Test
   void sgr256BgColor() throws Exception {
      // ESC[48;5;21m — 256-color background (blue)
      feed("\u001b[48;5;21m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{48, 5, 21},
         screen.lastSGR);
   }

   @Test
   void sgrTrueColorFg() throws Exception {
      // ESC[38;2;255;128;0m — true color foreground (orange)
      feed("\u001b[38;2;255;128;0m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{38, 2, 255, 128, 0},
         screen.lastSGR);
   }

   @Test
   void sgrTrueColorBg() throws Exception {
      // ESC[48;2;0;128;255m — true color background
      feed("\u001b[48;2;0;128;255m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{48, 2, 0, 128, 255},
         screen.lastSGR);
   }

   @Test
   void sgrBrightFgColors() throws Exception {
      // ESC[90m — bright black (dark gray) foreground
      feed("\u001b[90m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{90}, screen.lastSGR);
   }

   @Test
   void sgrBrightFg97() throws Exception {
      // ESC[97m — bright white foreground
      feed("\u001b[97m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{97}, screen.lastSGR);
   }

   @Test
   void sgrBrightBgColors() throws Exception {
      // ESC[100m — bright black (dark gray) background
      feed("\u001b[100m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{100}, screen.lastSGR);
   }

   @Test
   void sgrBrightBg107() throws Exception {
      // ESC[107m — bright white background
      feed("\u001b[107m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{107}, screen.lastSGR);
   }

   @Test
   void sgrBoldWith256Color() throws Exception {
      // ESC[1;38;5;196m — bold + 256-color fg
      feed("\u001b[1;38;5;196m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{1, 38, 5, 196},
         screen.lastSGR);
   }

   @Test
   void sgrResetSequence() throws Exception {
      // ESC[0m — reset all attributes
      feed("\u001b[0m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{0}, screen.lastSGR);
   }

   @Test
   void sgrMultipleAttrsInSingleSequence() throws Exception {
      // ESC[1;4;38;5;82m — bold + underline + 256-fg
      feed("\u001b[1;4;38;5;82m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{1, 4, 38, 5, 82},
         screen.lastSGR);
   }

   @Test
   void sgrDefaultFg() throws Exception {
      // ESC[39m — default foreground
      feed("\u001b[39m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{39}, screen.lastSGR);
   }

   @Test
   void sgrDefaultBg() throws Exception {
      // ESC[49m — default background
      feed("\u001b[49m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{49}, screen.lastSGR);
   }

   @Test
   void sgrStandardFgAndBg() throws Exception {
      // ESC[31;42m — red fg, green bg
      feed("\u001b[31;42m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{31, 42},
         screen.lastSGR);
   }

   @Test
   void sgrFgBg256Combined() throws Exception {
      // ESC[38;5;196;48;5;21m — fg 196 + bg 21
      feed("\u001b[38;5;196;48;5;21m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(
         new int[]{38, 5, 196, 48, 5, 21},
         screen.lastSGR);
   }

   @Test
   void sgrNoParamDefaultsToReset() throws Exception {
      // ESC[m — no params, defaults to 0 (reset)
      feed("\u001b[m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{0}, screen.lastSGR);
   }

   @Test
   void sgrDimAttribute() throws Exception {
      // ESC[2m — dim/faint (should pass through as param 2)
      feed("\u001b[2m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{2}, screen.lastSGR);
   }

   @Test
   void sgrItalicAttribute() throws Exception {
      // ESC[3m — italic
      feed("\u001b[3m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{3}, screen.lastSGR);
   }

   @Test
   void sgrReverseAttribute() throws Exception {
      // ESC[7m — reverse video
      feed("\u001b[7m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{7}, screen.lastSGR);
   }

   @Test
   void sgrResetBold() throws Exception {
      // ESC[22m — reset bold
      feed("\u001b[22m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{22}, screen.lastSGR);
   }

   @Test
   void sgrTrueColorFgBgCombined() throws Exception {
      // ESC[38;2;255;0;0;48;2;0;0;255m — truecolor fg red + bg blue
      feed("\u001b[38;2;255;0;0;48;2;0;0;255m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(
         new int[]{38, 2, 255, 0, 0, 48, 2, 0, 0, 255},
         screen.lastSGR);
   }

   @Test
   void sgrBoldReverseWithBrightFg() throws Exception {
      // ESC[1;7;93m — bold + reverse + bright yellow fg
      feed("\u001b[1;7;93m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{1, 7, 93}, screen.lastSGR);
   }

   @Test
   void sgrAllBrightFgRange() throws Exception {
      // Test all bright foreground codes 90-97
      for (int code = 90; code <= 97; code++) {
         screen.lastSGR = null;
         feed("\u001b[" + code + "m");
         assertNotNull(screen.lastSGR,
            "SGR " + code + " should be dispatched");
         assertEquals(code, screen.lastSGR[0],
            "SGR param for bright fg " + code);
      }
   }

   @Test
   void sgrAllBrightBgRange() throws Exception {
      // Test all bright background codes 100-107
      for (int code = 100; code <= 107; code++) {
         screen.lastSGR = null;
         feed("\u001b[" + code + "m");
         assertNotNull(screen.lastSGR,
            "SGR " + code + " should be dispatched");
         assertEquals(code, screen.lastSGR[0],
            "SGR param for bright bg " + code);
      }
   }

   @Test
   void sgr256BoundaryColor0() throws Exception {
      // ESC[38;5;0m — 256-color index 0 (black)
      feed("\u001b[38;5;0m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{38, 5, 0}, screen.lastSGR);
   }

   @Test
   void sgr256BoundaryColor254() throws Exception {
      // ESC[38;5;254m — 256-color index 254 (max storable)
      feed("\u001b[38;5;254m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{38, 5, 254}, screen.lastSGR);
   }

   @Test
   void sgr256BoundaryColor255() throws Exception {
      // ESC[38;5;255m — 256-color index 255 (known overflow)
      feed("\u001b[38;5;255m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{38, 5, 255}, screen.lastSGR);
   }

   @Test
   void sgrMultipleResetsInSequence() throws Exception {
      // ESC[0;1;0m — reset, bold, reset again
      feed("\u001b[0;1;0m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{0, 1, 0}, screen.lastSGR);
   }

   @Test
   void sgrResetFgAndBg() throws Exception {
      // ESC[39;49m — reset fg and bg to defaults
      feed("\u001b[39;49m");
      assertNotNull(screen.lastSGR);
      assertArrayEquals(new int[]{39, 49}, screen.lastSGR);
   }

   @Test
   void sgrSequentialSgrSequences() throws Exception {
      // Two separate SGR sequences in a row
      feed("\u001b[1m\u001b[31m");
      assertNotNull(screen.lastSGR);
      // Last SGR should be [31] (the second one)
      assertArrayEquals(new int[]{31}, screen.lastSGR);
      // But both should have been dispatched
      long sgrCount = screen.calls.stream()
         .filter(c -> c.equals("setGraphicRendition")).count();
      assertEquals(2, sgrCount,
         "two SGR sequences should produce two calls");
   }

   // ── OSC title tests ──────────────────────────────────────

   @Test
   void oscSetTitleWithSTTerminator() throws Exception {
      // ESC ] 0 ; title ESC BACKSLASH (ST terminator)
      feed("\u001b]0;Test Title\u001b\\");
      assertEquals("Test Title", screen.lastTitle);
   }

   @Test
   void oscEmptyTitle() throws Exception {
      // ESC ] 0 ; BEL (empty title)
      feed("\u001b]0;\u0007");
      assertEquals("", screen.lastTitle);
   }

   @Test
   void oscTitleWithSpecialChars() throws Exception {
      // ESC ] 0 ; title with spaces and symbols BEL
      feed("\u001b]0;user@host: ~/dir\u0007");
      assertEquals("user@host: ~/dir", screen.lastTitle);
   }

   @Test
   void oscIgnoredSequence() throws Exception {
      // ESC ] 7 ; unused-data BEL — code 7 sets working dir
      feed("\u001b]7;file:///path\u0007");
      // Code 7 is "set working directory" — ignored, no title
      assertNull(screen.lastTitle);
      assertEquals(NORM, state());
   }

   // ── Private mode: mouse tracking tests ─────────────────────

   @Test
   void privateMode1000EnablesNormalMouseTracking() throws Exception {
      feed("\u001b[?1000h");
      assertEquals(1000, screen.mouseTrackMode);
      assertTrue(screen.mouseTrackEnable);
   }

   @Test
   void privateMode1000DisablesNormalMouseTracking() throws Exception {
      feed("\u001b[?1000l");
      assertEquals(1000, screen.mouseTrackMode);
      assertFalse(screen.mouseTrackEnable);
   }

   @Test
   void privateMode1002EnablesButtonEventTracking() throws Exception {
      feed("\u001b[?1002h");
      assertEquals(1002, screen.mouseTrackMode);
      assertTrue(screen.mouseTrackEnable);
   }

   @Test
   void privateMode1003EnablesAnyEventTracking() throws Exception {
      feed("\u001b[?1003h");
      assertEquals(1003, screen.mouseTrackMode);
      assertTrue(screen.mouseTrackEnable);
   }

   @Test
   void privateMode1006EnablesSgrMouse() throws Exception {
      feed("\u001b[?1006h");
      assertTrue(screen.sgrMouseEnabled);
   }

   @Test
   void privateMode1006DisablesSgrMouse() throws Exception {
      feed("\u001b[?1006l");
      assertFalse(screen.sgrMouseEnabled);
   }

   // ── Private mode: bracketed paste ──────────────────────────

   @Test
   void privateMode2004EnablesBracketedPaste() throws Exception {
      feed("\u001b[?2004h");
      assertTrue(screen.bracketedPasteEnabled);
   }

   @Test
   void privateMode2004DisablesBracketedPaste() throws Exception {
      feed("\u001b[?2004l");
      assertFalse(screen.bracketedPasteEnabled);
   }

   // ── Private mode: focus events ─────────────────────────────

   @Test
   void privateMode1004EnablesFocusEvents() throws Exception {
      feed("\u001b[?1004h");
      assertTrue(screen.focusEventsEnabled);
   }

   @Test
   void privateMode1004DisablesFocusEvents() throws Exception {
      feed("\u001b[?1004l");
      assertFalse(screen.focusEventsEnabled);
   }

   // ── Private mode: autowrap ─────────────────────────────────

   @Test
   void privateMode7EnablesAutowrap() throws Exception {
      feed("\u001b[?7h");
      assertTrue(screen.autowrapEnabled);
   }

   @Test
   void privateMode7DisablesAutowrap() throws Exception {
      feed("\u001b[?7l");
      assertFalse(screen.autowrapEnabled);
   }

   // ── Private mode: cursor blink ─────────────────────────────

   @Test
   void privateMode12EnablesCursorBlink() throws Exception {
      feed("\u001b[?12h");
      assertTrue(screen.cursorBlinkEnabled);
   }

   @Test
   void privateMode12DisablesCursorBlink() throws Exception {
      feed("\u001b[?12l");
      assertFalse(screen.cursorBlinkEnabled);
   }

   // ── Private mode: application cursor keys (DECCKM) ─────────

   @Test
   void privateMode1EnablesApplicationCursorKeys() throws Exception {
      feed("\u001b[?1h");
      assertTrue(screen.appCursorKeysEnabled);
   }

   @Test
   void privateMode1DisablesApplicationCursorKeys() throws Exception {
      feed("\u001b[?1l");
      assertFalse(screen.appCursorKeysEnabled);
   }

   // ── Private mode: cursor visibility (DECTCEM) ──────────────

   @Test
   void privateMode25ShowsCursor() throws Exception {
      feed("\u001b[?25h");
      assertTrue(screen.cursorVisibleState);
   }

   @Test
   void privateMode25HidesCursor() throws Exception {
      feed("\u001b[?25l");
      assertFalse(screen.cursorVisibleState);
   }

   // ── Private mode: multiple modes in one sequence ───────────

   @Test
   void multiplePrivateModesSameSequence() throws Exception {
      // ESC[?1000;1006h — enable both normal tracking and SGR mode
      feed("\u001b[?1000;1006h");
      assertEquals(1000, screen.mouseTrackMode);
      assertTrue(screen.mouseTrackEnable);
      assertTrue(screen.sgrMouseEnabled);
   }

   @Test
   void multiplePrivateModesDisable() throws Exception {
      // Enable first, then disable both
      feed("\u001b[?1000;1006h");
      feed("\u001b[?1000;1006l");
      assertFalse(screen.mouseTrackEnable);
      assertFalse(screen.sgrMouseEnabled);
   }

   // ── Device status report / device attributes ───────────────

   @Test
   void csiDeviceStatusReportCursorPosition() throws Exception {
      // ESC[6n — requests cursor position report
      feed("\u001b[6n");
      assertTrue(screen.respondCPRCalled);
   }

   @Test
   void csiDeviceAttributes() throws Exception {
      // ESC[c — requests device attributes
      feed("\u001b[c");
      assertTrue(screen.respondDACalled);
   }

   @Test
   void csiDeviceAttributesWithZero() throws Exception {
      // ESC[0c — also device attributes request
      feed("\u001b[0c");
      assertTrue(screen.respondDACalled);
   }

   // ── Charset designation ────────────────────────────────────

   @Test
   void escParenBDesignatesG0Charset() throws Exception {
      // ESC ( B — designate US-ASCII as G0
      feed("\u001b(B");
      assertEquals(NORM, state());
   }

   @Test
   void escParenZeroDesignatesG0Special() throws Exception {
      // ESC ( 0 — designate DEC Special Graphics as G0
      feed("\u001b(0");
      assertEquals(NORM, state());
   }

   @Test
   void escCloseParenDesignatesG1() throws Exception {
      // ESC ) B — designate US-ASCII as G1
      feed("\u001b)B");
      assertEquals(NORM, state());
   }

   @Test
   void escSpaceDesignatesCharset() throws Exception {
      // ESC SP F — 7-bit controls, just consume the byte
      feed("\u001b F");
      assertEquals(NORM, state());
   }

   @Test
   void charsetDesignationFollowedByText() throws Exception {
      // ESC ( B then normal text should work
      feed("\u001b(BHello");
      assertEquals(NORM, state());
      assertTrue(sbContents().contains("Hello"));
   }

   // ── DISCARD state tests ────────────────────────────────────

   @Test
   void csiGreaterEntersDiscard() throws Exception {
      // CSI > c — Secondary Device Attributes → discard
      feed("\u001b[>c");
      assertEquals(NORM, state());
   }

   @Test
   void csiEqualsEntersDiscard() throws Exception {
      // CSI = c — Tertiary Device Attributes → discard
      feed("\u001b[=c");
      assertEquals(NORM, state());
   }

   @Test
   void discardStateConsumesTillFinalByte() throws Exception {
      // CSI > 0 ; 1 ; 2 c — full secondary DA with params
      feed("\u001b[>0;1;2c");
      assertEquals(NORM, state());
   }

   // ── Control character tests ────────────────────────────────

   @Test
   void shiftOutIgnored() throws Exception {
      // SO (0x0E / char 14) — select G1 character set, ignored
      feed("\u000e");
      assertEquals(NORM, state());
      assertEquals("", sbContents());
   }

   @Test
   void shiftInIgnored() throws Exception {
      // SI (0x0F / char 15) — select G0 character set, ignored
      feed("\u000f");
      assertEquals(NORM, state());
      assertEquals("", sbContents());
   }

   @Test
   void formFeedAppendsToBuffer() throws Exception {
      // FF (0x0C / char 12) — normalized to LF per VT100 spec
      feed("\u000c");
      assertEquals("\n", sbContents());
   }

   @Test
   void verticalTabAppendsToBuffer() throws Exception {
      // VT (0x0B / char 11) — normalized to LF per VT100 spec
      feed("\u000b");
      assertEquals("\n", sbContents());
   }

   // ── CSI intermediate byte handling ─────────────────────────

   @Test
   void csiSpaceQIsConsumed() throws Exception {
      // ESC[0 q — DECSCUSR (cursor style), space is intermediate
      feed("\u001b[0 q");
      assertEquals(NORM, state());
      // Should not append 'q' to the output buffer
      assertFalse(sbContents().contains("q"));
   }

   @Test
   void csiGreaterParamsConsumedBeforeFinal() throws Exception {
      // ESC[>4;2m — modifyOtherKeys (xterm).  CSI_GT must consume
      // all parameter bytes (4, ;, 2) before the final byte (m).
      feed("\u001b[>4;2m");
      assertEquals(NORM, state());
      assertFalse(sbContents().contains(";"));
      assertFalse(sbContents().contains("m"));
   }

   @Test
   void colonSubparameterConsumed() throws Exception {
      // ESC[4:3m — curly underline via colon subparameter
      feed("\u001b[4:3m");
      assertEquals(NORM, state());
      assertFalse(sbContents().contains(":"));
   }

   // ── CSI @ (Insert blank characters) ───────────────────────

   @Test
   void csiInsertBlanksDefault() throws Exception {
      // ESC[@ — insert 1 blank character at cursor
      feed("\u001b[@");
      // def=true (highestSet=-1), so insertChars(1)
      assertEquals(1, screen.lastInsertCharsCount);
   }

   @Test
   void csiInsertBlanksWithCount() throws Exception {
      // ESC[3@ — insert 3 blank characters
      feed("\u001b[3@");
      assertEquals(3, screen.lastInsertCharsCount);
   }

   // ── CSI r (DECCARA) ───────────────────────────────────────

   @Test
   void csiDeccaraDoesNotCrash() throws Exception {
      // ESC[1;1;24;80r — Change Attributes in Rectangular Area
      feed("\u001b[1;1;24;80r");
      assertEquals(NORM, state());
   }

   // ── Double escape ─────────────────────────────────────────

   @Test
   void doubleEscapeStaysInEscState() throws Exception {
      // ESC ESC [ 2 J — second ESC should stay in ESC state
      feed("\u001b\u001b[2J");
      assertTrue(screen.eraseScreenCalled);
   }

   // ── ESC[3J (erase scrollback) ─────────────────────────────

   @Test
   void csiEraseScrollbackIgnored() throws Exception {
      // ESC[3J — erase scrollback buffer (xterm extension, ignored)
      feed("\u001b[3J");
      assertEquals(NORM, state());
      assertFalse(screen.eraseScreenCalled);
      assertFalse(screen.eraseScreenToEndCalled);
   }

   // ── CSI cursor with default (no digits) ────────────────────

   @Test
   void csiCursorUpDefaultIsOneWhenNoDigit() throws Exception {
      // ESC[A with def=true → incY(-1) (default 1)
      feed("\u001b[A");
      // highestSet=-1, currnumacc=0 → def=true → incY(-1)
      assertEquals(-1, screen.lastIncY);
   }

   @Test
   void csiCursorRightDefaultIsOneWhenNoDigit() throws Exception {
      feed("\u001b[C");
      // def=true → incX(1)
      assertEquals(1, screen.lastIncX);
   }

   // ── CSI X (erase character, same as P) ─────────────────────

   @Test
   void csiEraseCharXDefault() throws Exception {
      // ESC[X with no count — def=true → eraseChars(1)
      feed("\u001b[X");
      // highestSet=-1 → def=true → eraseChars(1)
      assertEquals(1, screen.lastEraseCharsCount);
   }

   // ── OSC code 1 (set icon name) ────────────────────────────

   @Test
   void oscSetIconName() throws Exception {
      // ESC ] 1 ; icon BEL — sets icon name, not title
      feed("\u001b]1;My Icon\u0007");
      // Code 1 sets icon name only, not title
      assertNull(screen.lastTitle);
      assertEquals(NORM, state());
   }

   // ── OSC code 4 (change color palette) ─────────────────────

   @Test
   void oscChangeColorPaletteIgnored() throws Exception {
      // ESC ] 4 ; data BEL — change color palette (ignored)
      feed("\u001b]4;1;rgb:ff/00/00\u0007");
      assertNull(screen.lastTitle);
      assertEquals(NORM, state());
   }

   // ── Private mode: alt screen variants ──────────────────────

   @Test
   void privateMode47Off() throws Exception {
      feed("\u001b[?47l");
      assertFalse(screen.altScreenEnabled);
   }

   @Test
   void privateMode1047Off() throws Exception {
      feed("\u001b[?1047l");
      assertFalse(screen.altScreenEnabled);
   }

   // ── CSI E/F cursor line movement with defaults ─────────────

   @Test
   void csiNextLineDefault() throws Exception {
      // ESC[E — no count, def=true → incY(1), setX(1)
      feed("\u001b[E");
      assertEquals(1, screen.lastIncY);
      assertEquals(1, screen.lastSetX);
   }

   @Test
   void csiPrevLineDefault() throws Exception {
      feed("\u001b[F");
      // def=true → incY(-1), setX(1)
      assertEquals(-1, screen.lastIncY);
      assertEquals(1, screen.lastSetX);
   }

   // ── CSI G/d column/row with defaults ───────────────────────

   @Test
   void csiColumnAbsoluteDefault() throws Exception {
      // ESC[G with no number — def=true → setX(1)
      feed("\u001b[G");
      assertEquals(1, screen.lastSetX);
   }

   @Test
   void csiRowAbsoluteDefault() throws Exception {
      // ESC[d with no number — def=true → setY(1)
      feed("\u001b[d");
      assertEquals(1, screen.lastSetY);
   }

   // ── CSI M (delete lines) default ──────────────────────────

   @Test
   void csiDeleteLinesDefaultIsOne() throws Exception {
      // ESC[M — delete lines, def=true → deleteLines(1)
      feed("\u001b[M");
      assertEquals(1, screen.lastDeleteLinesCount);
   }

   // ── CSI S/T (scroll) defaults ──────────────────────────────

   @Test
   void csiScrollUpDefaultIsOne() throws Exception {
      feed("\u001b[S");
      // def=true → scrollUp(1)
      assertEquals(1, screen.lastScrollUpCount);
   }

   @Test
   void csiScrollDownDefaultIsOne() throws Exception {
      feed("\u001b[T");
      // def=true → scrollDown(1)
      assertEquals(1, screen.lastScrollDownCount);
   }

   // ── Unrecognized ESC code falls through ────────────────────

   @Test
   void unhandledEscReturnsToNorm() throws Exception {
      // ESC followed by an unrecognized character
      feed("\u001bZ");
      assertEquals(NORM, state());
   }

   // ── 0xFFFF character ──────────────────────────────────────

   @Test
   void charFfffHandled() throws Exception {
      // char 0xFFFF (often returned for -1 cast to char)
      feed("\uffff");
      assertEquals(NORM, state());
      // Should not append to buffer
      assertEquals("", sbContents());
   }

   // ── Low control character warning ──────────────────────────

   @Test
   void lowControlCharDoesNotCrash() throws Exception {
      // char 0x01 (SOH) — unhandled low control character
      feed("\u0001");
      assertEquals(NORM, state());
   }

   @Test
   void controlChar0x05DoesNotCrash() throws Exception {
      // char 0x05 (ENQ)
      feed("\u0005");
      assertEquals(NORM, state());
   }

   @Test
   void tabCallsHandleTab() throws Exception {
      feed("\t");
      assertTrue(screen.handleTabCalled,
         "HT (0x09) should call handleTab");
      assertTrue(screen.calls.contains("handleTab"));
      assertEquals(NORM, state());
   }

   // ── IND / RI / NEL tests ──────────────────────────────────

   @Test
   void escDCallsIndex() throws Exception {
      feed("\u001bD");
      assertTrue(screen.indexCalled, "ESC D should call index");
      assertEquals(NORM, state());
   }

   @Test
   void escECallsNextLine() throws Exception {
      feed("\u001bE");
      assertTrue(screen.nextLineCalled,
         "ESC E should call nextLine");
      assertEquals(NORM, state());
   }

   // ── DECSTBM (CSI r) tests ─────────────────────────────────

   @Test
   void csiSetsScrollRegion() throws Exception {
      feed("\u001b[5;15r");
      assertEquals(5, screen.scrollRegionTop);
      assertEquals(15, screen.scrollRegionBottom);
   }

   @Test
   void csiResetScrollRegion() throws Exception {
      feed("\u001b[5;15r"); // set first
      feed("\u001b[r");     // reset
      assertEquals(0, screen.scrollRegionTop);
      assertEquals(0, screen.scrollRegionBottom);
   }

   // ── DECALN (ESC # 8) test ─────────────────────────────────

   @Test
   void escHash8CallsDecaln() throws Exception {
      feed("\u001b#8");
      assertTrue(screen.screenAlignmentCalled,
         "ESC # 8 should call screenAlignmentDisplay");
      assertEquals(NORM, state());
   }

   @Test
   void escHashOtherIgnored() throws Exception {
      feed("\u001b#3");
      assertFalse(screen.screenAlignmentCalled,
         "ESC # 3 should not call screenAlignmentDisplay");
      assertEquals(NORM, state());
   }

   // ── DECOM (ESC[?6h/l) tests ───────────────────────────────

   @Test
   void originModeOn() throws Exception {
      feed("\u001b[?6h");
      assertTrue(screen.originModeEnabled);
   }

   @Test
   void originModeOff() throws Exception {
      feed("\u001b[?6h"); // enable
      feed("\u001b[?6l"); // disable
      assertFalse(screen.originModeEnabled);
   }

   // ── DEC Special Graphics charset tests ────────────────────

   @Test
   void escParen0ActivatesDecGraphicsOnG0() throws Exception {
      // ESC ( 0 → G0 = DEC Special Graphics
      // 'q' (0x71) should map to horizontal line U+2500
      feed("\u001b(0q");
      assertEquals("\u2500", sbContents(),
         "G0 DEC graphics: 'q' should map to horiz line");
      assertEquals(NORM, state());
   }

   @Test
   void escParenBResetsG0ToAscii() throws Exception {
      feed("\u001b(0");  // G0 = DEC graphics
      feed("\u001b(B");  // G0 = ASCII
      feed("q");
      assertEquals("q", sbContents(),
         "G0 ASCII: 'q' should remain 'q'");
   }

   @Test
   void decGraphicsMapsBorderChars() throws Exception {
      // ESC ( 0, then send box drawing mnemonics: l q k x x m q j
      // l=top-left, q=horiz, k=top-right, x=vert, m=bot-left, j=bot-right
      feed("\u001b(0lqkxxmqj");
      String expected = "\u250C\u2500\u2510\u2502\u2502\u2514\u2500\u2518";
      assertEquals(expected, sbContents());
   }

   @Test
   void decGraphicsOnlyAffectsRange60To7E() throws Exception {
      // Characters below 0x60 should pass through unchanged
      feed("\u001b(0ABC 123");
      assertEquals("ABC 123", sbContents());
   }

   @Test
   void soSelectsG1() throws Exception {
      // Set G1 to DEC graphics, then SO to activate G1
      feed("\u001b)0");   // G1 = DEC graphics
      feed("\u000E");     // SO = select G1
      feed("q");
      assertEquals("\u2500", sbContents(),
         "SO should activate G1 (DEC graphics)");
   }

   @Test
   void siSelectsG0() throws Exception {
      // Set G1 to DEC graphics, SO to select G1, then SI to select G0
      feed("\u001b)0");   // G1 = DEC graphics
      feed("\u000E");     // SO = select G1
      feed("\u000F");     // SI = select G0 (ASCII by default)
      feed("q");
      assertEquals("q", sbContents(),
         "SI should revert to G0 (ASCII)");
   }

   @Test
   void g1DecGraphicsViaParenRight() throws Exception {
      // ESC ) 0 = set G1 to DEC graphics
      feed("\u001b)0");
      // G0 is still ASCII, so normal chars unchanged
      feed("q");
      assertEquals("q", sbContents(),
         "G1 designation should not affect G0 output");
   }

   @Test
   void decGraphicsAllMappedChars() throws Exception {
      // Verify the full map: 0x60 through 0x7E
      char[] expected = Vt100Parser.DEC_GRAPHICS_MAP;
      feed("\u001b(0"); // G0 = DEC graphics
      StringBuilder input = new StringBuilder();
      for (char c = 0x60; c <= 0x7E; c++)
         input.append(c);
      feed(input.toString());
      String result = sbContents();
      assertEquals(expected.length, result.length());
      for (int i = 0; i < expected.length; i++)
         assertEquals(expected[i], result.charAt(i),
            "char 0x" + Integer.toHexString(0x60 + i));
   }

   @Test
   void escSpaceConsumedWithoutAffectingCharsets()
         throws Exception {
      // ESC SP F — should consume 'F' without changing charsets
      feed("\u001b F");
      assertEquals(NORM, state());
      // G0 should still be ASCII
      feed("q");
      assertEquals("q", sbContents());
   }

   // ── DCS / APC / PM / SOS string sequences ────────────────

   @Test
   @DisplayName("DCS sequence consumed until ST (ESC backslash)")
   void dcsConsumedUntilSt() throws Exception {
      // ESC P ... ESC \  (DCS with ESC \ terminator)
      feed("\u001bPsome DCS data\u001b\\");
      assertEquals(NORM, state());
      // Nothing should leak into the output
      assertEquals("", sbContents());
   }

   @Test
   @DisplayName("APC sequence consumed until ST")
   void apcConsumedUntilSt() throws Exception {
      feed("\u001b_APC content\u001b\\");
      assertEquals(NORM, state());
      assertEquals("", sbContents());
   }

   @Test
   @DisplayName("DCS followed by normal text")
   void dcsFollowedByText() throws Exception {
      feed("\u001bPdcs stuff\u001b\\hello");
      assertEquals(NORM, state());
      assertEquals("hello", sbContents());
   }

   @Test
   @DisplayName("DCS with 8-bit ST (0x9C) terminator")
   void dcsTerminatedBy8bitSt() throws Exception {
      feed("\u001bPdcs data\u009C");
      assertEquals(NORM, state());
      assertEquals("", sbContents());
   }

   @Test
   @DisplayName("PM sequence consumed without output")
   void pmConsumedWithoutOutput() throws Exception {
      feed("\u001b^privacy message\u001b\\");
      assertEquals(NORM, state());
      assertEquals("", sbContents());
   }

   // ── handleModeSet bug fix tests ────────────────────────────

   @Test
   @DisplayName("CSI 4h sets insert mode (single param)")
   void modeSetSingleParam() throws Exception {
      feed("\u001b[4h");
      long setCount = screen.calls.stream()
         .filter(c -> c.equals("setInsertMode:true"))
         .count();
      assertEquals(1, setCount,
         "single param should call setInsertMode once");
   }

   @Test
   @DisplayName("CSI 20;4h — unknown mode 20, then insert mode 4")
   void modeSetMultiParamsProcessesEach() throws Exception {
      // Old buggy code checked numacc[currnumacc] (=4) for both
      // iterations, calling setInsertMode twice.
      // Fixed code checks numacc[0]=20 (unknown) then numacc[1]=4.
      feed("\u001b[20;4h");
      long setCount = screen.calls.stream()
         .filter(c -> c.equals("setInsertMode:true"))
         .count();
      assertEquals(1, setCount,
         "mode 20 is unknown; only mode 4 should set insert mode");
   }

   @Test
   @DisplayName("CSI 4;4h — duplicate mode 4 calls setInsertMode twice")
   void modeSetDuplicateParamsBothProcessed() throws Exception {
      feed("\u001b[4;4h");
      long setCount = screen.calls.stream()
         .filter(c -> c.equals("setInsertMode:true"))
         .count();
      assertEquals(2, setCount,
         "both params are 4, so setInsertMode called twice");
   }

   @Test
   @DisplayName("CSI 4l resets insert mode (single param)")
   void modeResetSingleParam() throws Exception {
      feed("\u001b[4h"); // enable first
      feed("\u001b[4l"); // then disable
      long resetCount = screen.calls.stream()
         .filter(c -> c.equals("setInsertMode:false"))
         .count();
      assertEquals(1, resetCount);
   }

   // ── DEC private mode tests ─────────────────────────────────

   @Test
   @DisplayName("CSI ?1048h saves cursor via private mode")
   void decMode1048hSavesCursor() throws Exception {
      feed("\u001b[?1048h");
      assertTrue(screen.saveCursorCalled,
         "mode 1048h should save cursor");
   }

   @Test
   @DisplayName("CSI ?1048l restores cursor via private mode")
   void decMode1048lRestoresCursor() throws Exception {
      feed("\u001b[?1048l");
      assertTrue(screen.restoreCursorCalled,
         "mode 1048l should restore cursor");
   }

   @Test
   @DisplayName("CSI ?9h enables X10 mouse tracking as normal")
   void decMode9EnablesMouse() throws Exception {
      feed("\u001b[?9h");
      assertEquals(1000, screen.mouseTrackMode);
      assertTrue(screen.mouseTrackEnable);
   }

   @Test
   @DisplayName("CSI ?5h reverse video accepted without error")
   void decMode5AcceptedSilently() throws Exception {
      feed("\u001b[?5h");
      assertEquals(NORM, state());
      // No error logged — just accepted
   }

   @Test
   @DisplayName("CSI ?5l reverse video reset accepted")
   void decMode5ResetAccepted() throws Exception {
      feed("\u001b[?5l");
      assertEquals(NORM, state());
   }

   // ── DECRQCRA (CSI * y) parser tests ───────────────────────

   @Test
   @DisplayName("CSI Pid;Pp;Pt;Pl;Pb;Pr * y dispatches DECRQCRA")
   void decrqcraDispatched() throws Exception {
      feed("\u001b[1;1;1;1;24;80*y");
      assertTrue(screen.respondRectChecksumCalled,
         "DECRQCRA should be dispatched");
      assertEquals(NORM, state());
   }

   @Test
   @DisplayName("DECRQCRA passes all 6 parameters")
   void decrqcraPassesParams() throws Exception {
      feed("\u001b[7;1;2;3;10;40*y");
      assertTrue(screen.respondRectChecksumCalled);
      assertEquals(7, screen.rectChecksumParams[0], "Pid");
      assertEquals(1, screen.rectChecksumParams[1], "Pp");
      assertEquals(2, screen.rectChecksumParams[2], "Pt");
      assertEquals(3, screen.rectChecksumParams[3], "Pl");
      assertEquals(10, screen.rectChecksumParams[4], "Pb");
      assertEquals(40, screen.rectChecksumParams[5], "Pr");
      assertEquals(5, screen.rectChecksumHighParam, "highParam");
   }

   @Test
   @DisplayName("CSI * z (unknown final) discarded")
   void csiStarUnknownDiscarded() throws Exception {
      feed("\u001b[1;1*z");
      assertFalse(screen.respondRectChecksumCalled,
         "unknown CSI * final should not dispatch DECRQCRA");
      assertEquals(NORM, state());
   }

   @Test
   @DisplayName("CSI * y with no params dispatches with defaults")
   void decrqcraNoParams() throws Exception {
      feed("\u001b[*y");
      assertTrue(screen.respondRectChecksumCalled,
         "DECRQCRA with no params should still dispatch");
   }
}
