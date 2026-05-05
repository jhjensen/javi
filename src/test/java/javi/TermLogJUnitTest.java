package javi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TermLog} — the VT100 terminal I/O logger.
 *
 * <p>All tests are headless. Uses real file I/O to verify log output.</p>
 */
class TermLogJUnitTest {

   @TempDir
   Path tempDir;

   private TermLog log;
   private Path logFile;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void setUp() {
      log = new TermLog();
      logFile = tempDir.resolve("test-termlog.txt");
   }

   @AfterEach
   void tearDown() {
      log.disable();
   }

   @Test
   void initiallyDisabled() {
      assertFalse(log.isEnabled());
   }

   @Test
   void enableSetsEnabledTrue() {
      log.enable(logFile);
      assertTrue(log.isEnabled());
   }

   @Test
   void disableSetsEnabledFalse() {
      log.enable(logFile);
      log.disable();
      assertFalse(log.isEnabled());
   }

   @Test
   void enableCreatesLogFile() {
      log.enable(logFile);
      assertTrue(Files.exists(logFile));
   }

   @Test
   void enableWritesStartMarker() throws IOException {
      log.enable(logFile);
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("=== TERMLOG STARTED"));
   }

   @Test
   void disableWritesStopMarker() throws IOException {
      log.enable(logFile);
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("=== TERMLOG STOPPED"));
   }

   @Test
   void logRecvWritesPrintableChar() throws IOException {
      log.enable(logFile);
      log.logRecv('A');
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("RECV hex=41 ch='A'"));
   }

   @Test
   void logRecvWritesControlChar() throws IOException {
      log.enable(logFile);
      log.logRecv('\n');
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("RECV hex=0a LF"));
   }

   @Test
   void logRecvWritesEscChar() throws IOException {
      log.enable(logFile);
      log.logRecv((char) 27);
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("RECV hex=1b ESC"));
   }

   @Test
   void logRecvWritesCR() throws IOException {
      log.enable(logFile);
      log.logRecv('\r');
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("RECV hex=0d CR"));
   }

   @Test
   void logRecvWritesTab() throws IOException {
      log.enable(logFile);
      log.logRecv('\t');
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("RECV hex=09 TAB"));
   }

   @Test
   void logRecvWritesBackspace() throws IOException {
      log.enable(logFile);
      log.logRecv('\b');
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("RECV hex=08 BS"));
   }

   @Test
   void logRecvWritesBEL() throws IOException {
      log.enable(logFile);
      log.logRecv((char) 7);
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("RECV hex=07 BEL"));
   }

   @Test
   void logRecvWritesControlAsCaretNotation() throws IOException {
      log.enable(logFile);
      log.logRecv((char) 1);  // ^A
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("RECV hex=01 ^A"));
   }

   @Test
   void logSendCharWritesFormat() throws IOException {
      log.enable(logFile);
      log.logSend('Z');
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("SEND hex=5a ch='Z'"));
   }

   @Test
   void logSendStringWritesEscapeSequence() throws IOException {
      log.enable(logFile);
      log.logSend("\033[?62;22c");
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("SEND str=\"\\e[?62;22c\""));
   }

   @Test
   void logSendStringWritesControlChars() throws IOException {
      log.enable(logFile);
      log.logSend("\033[6\001R");
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("\\e"));
      assertTrue(content.contains("\\x01"));
   }

   @Test
   void logStateWritesTransition() throws IOException {
      log.enable(logFile);
      log.logState(0, 1, (char) 27);
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("STATE NORM -> ESC trigger=hex=1b ESC"));
   }

   @Test
   void logStateSameStateSkipsWrite() throws IOException {
      log.enable(logFile);
      log.logState(2, 2, '5');
      log.disable();
      String content = Files.readString(logFile);
      assertFalse(content.contains("STATE"));
   }

   @Test
   void logModeSetWritesFormat() throws IOException {
      log.enable(logFile);
      log.logMode(1049, true);
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("MODE SET 1049"));
   }

   @Test
   void logModeResetWritesFormat() throws IOException {
      log.enable(logFile);
      log.logMode(25, false);
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("MODE RST 25"));
   }

   @Test
   void logInfoWritesFormat() throws IOException {
      log.enable(logFile);
      log.log("test message here");
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("INFO test message here"));
   }

   @Test
   void stateNameReturnsKnownNames() {
      assertEquals("NORM", TermLog.stateName(0));
      assertEquals("ESC", TermLog.stateName(1));
      assertEquals("GETNUM", TermLog.stateName(2));
      assertEquals("MODE", TermLog.stateName(3));
      assertEquals("OSCMODE", TermLog.stateName(5));
      assertEquals("CHARSET_DESIGNATE", TermLog.stateName(10));
      assertEquals("CSI_STAR", TermLog.stateName(15));
   }

   @Test
   void stateNameReturnsQuestionMarkForUnknown() {
      assertEquals("?99", TermLog.stateName(99));
      assertEquals("?-1", TermLog.stateName(-1));
   }

   @Test
   void logRecvWhenDisabledDoesNothing() throws IOException {
      log.enable(logFile);
      log.disable();
      long sizeAfterDisable = Files.size(logFile);
      log.logRecv('X');
      assertEquals(sizeAfterDisable, Files.size(logFile));
   }

   @Test
   void logSendCharWhenDisabledDoesNothing() throws IOException {
      log.enable(logFile);
      log.disable();
      long sizeAfterDisable = Files.size(logFile);
      log.logSend('Y');
      assertEquals(sizeAfterDisable, Files.size(logFile));
   }

   @Test
   void logSendStringWhenDisabledDoesNothing() throws IOException {
      log.enable(logFile);
      log.disable();
      long sizeAfterDisable = Files.size(logFile);
      log.logSend("test");
      assertEquals(sizeAfterDisable, Files.size(logFile));
   }

   @Test
   void logStateWhenDisabledDoesNothing() throws IOException {
      log.enable(logFile);
      log.disable();
      long sizeAfterDisable = Files.size(logFile);
      log.logState(0, 1, 'x');
      assertEquals(sizeAfterDisable, Files.size(logFile));
   }

   @Test
   void logModeWhenDisabledDoesNothing() throws IOException {
      log.enable(logFile);
      log.disable();
      long sizeAfterDisable = Files.size(logFile);
      log.logMode(1, true);
      assertEquals(sizeAfterDisable, Files.size(logFile));
   }

   @Test
   void logWhenDisabledDoesNothing() throws IOException {
      log.enable(logFile);
      log.disable();
      long sizeAfterDisable = Files.size(logFile);
      log.log("ignored");
      assertEquals(sizeAfterDisable, Files.size(logFile));
   }

   @Test
   void reEnableToNewFileWorks() throws IOException {
      log.enable(logFile);
      log.logRecv('A');
      Path secondFile = tempDir.resolve("second-termlog.txt");
      log.enable(secondFile);
      log.logRecv('B');
      log.disable();
      String first = Files.readString(logFile);
      String second = Files.readString(secondFile);
      assertTrue(first.contains("RECV hex=41"));
      assertFalse(first.contains("RECV hex=42"));
      assertTrue(second.contains("RECV hex=42"));
   }

   @Test
   void multipleEntriesWriteMultipleLines() throws IOException {
      log.enable(logFile);
      log.logRecv('A');
      log.logRecv('B');
      log.logRecv('C');
      log.disable();
      List<String> lines = Files.readAllLines(logFile);
      long recvCount = lines.stream()
         .filter(l -> l.startsWith("RECV")).count();
      assertEquals(3, recvCount);
   }

   @Test
   void highByteCharFormatsCorrectly() throws IOException {
      log.enable(logFile);
      log.logRecv((char) 0x7f);  // DEL — not printable, not control < 0x20
      log.disable();
      String content = Files.readString(logFile);
      assertTrue(content.contains("RECV hex=7f"));
   }

   @Test
   void disableWithoutEnableDoesNotThrow() {
      log.disable();  // should be safe
   }

   @Test
   void logRecvWithoutEnableDoesNotThrow() {
      log.logRecv('X');
   }

   @Test
   void logSendWithoutEnableDoesNotThrow() {
      log.logSend('X');
      log.logSend("test");
   }

   @Test
   void logStateWithoutEnableDoesNotThrow() {
      log.logState(0, 1, 'x');
   }
}
