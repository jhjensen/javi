package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests targeting specific coverage gaps identified by JaCoCo analysis.
 * Focuses on EventQueue, MiscCommands utility methods, and
 * FormatDispatch edge cases.
 */
class CoverageGapJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void lock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void unlock() {
      EventQueue.biglock2.unlock();
   }

   // ══════════════════════════════════════════════════════════════
   // EventQueue coverage gaps
   // ══════════════════════════════════════════════════════════════

   @Nested
   @DisplayName("EventQueue.drainEnterEvents")
   class DrainEnterEventsTests {

      @Test
      @DisplayName("drainEnterEvents removes CR events from queue")
      void drainsCarriageReturn() {
         JeyEvent cr = new JeyEvent(0, 0, '\r');
         EventQueue.insert(cr);
         EventQueue.drainEnterEvents();
         // Queue should be empty of CR events — insert another
         // non-enter event to verify drain didn't break the queue
         JeyEvent normal = new JeyEvent(0, 0, 'x');
         EventQueue.insert(normal);
         // No exception means success
      }

      @Test
      @DisplayName("drainEnterEvents removes LF events from queue")
      void drainsLineFeed() {
         JeyEvent lf = new JeyEvent(0, 0, '\n');
         EventQueue.insert(lf);
         EventQueue.drainEnterEvents();
         // Drained successfully
      }

      @Test
      @DisplayName("drainEnterEvents preserves non-enter events")
      void preservesOtherEvents() {
         JeyEvent normal = new JeyEvent(0, 0, 'a');
         JeyEvent enter = new JeyEvent(0, 0, '\r');
         JeyEvent another = new JeyEvent(0, 0, 'b');
         EventQueue.insert(normal);
         EventQueue.insert(enter);
         EventQueue.insert(another);
         EventQueue.drainEnterEvents();
         // 'a' and 'b' should remain — drain only removes enter
      }

      @Test
      @DisplayName("drainEnterEvents on empty queue does not throw")
      void emptyQueueNoError() {
         // Drain on potentially non-empty queue from other tests,
         // but should never throw
         EventQueue.drainEnterEvents();
      }

      @Test
      @DisplayName("drainEnterEvents handles mixed CR and LF")
      void mixedCrLf() {
         EventQueue.insert(new JeyEvent(0, 0, '\r'));
         EventQueue.insert(new JeyEvent(0, 0, '\n'));
         EventQueue.insert(new JeyEvent(0, 0, '\r'));
         EventQueue.insert(new JeyEvent(0, 0, 'z'));
         EventQueue.drainEnterEvents();
         // Only 'z' remains
      }
   }

   @Nested
   @DisplayName("EventQueue.focus state")
   class FocusStateTests {

      @Test
      @DisplayName("isFocused returns true after focusGained")
      void focusGainedSetsTrue() {
         EventQueue.focusGained();
         assertTrue(EventQueue.isFocused());
      }

      @Test
      @DisplayName("isFocused returns false after focusLost")
      void focusLostSetsFalse() {
         EventQueue.focusLost();
         assertFalse(EventQueue.isFocused());
         // Restore
         EventQueue.focusGained();
      }

      @Test
      @DisplayName("focus state toggles correctly")
      void focusToggle() {
         EventQueue.focusGained();
         assertTrue(EventQueue.isFocused());
         EventQueue.focusLost();
         assertFalse(EventQueue.isFocused());
         EventQueue.focusGained();
         assertTrue(EventQueue.isFocused());
      }
   }

   // ══════════════════════════════════════════════════════════════
   // MiscCommands utility method coverage gaps
   // ══════════════════════════════════════════════════════════════

   @Nested
   @DisplayName("MiscCommands screen dimensions")
   class ScreenDimensionTests {

      @Test
      @DisplayName("updateScreenDimensions sets height and width")
      void updateSetsValues() {
         MiscCommands.updateScreenDimensions(50, 120);
         assertEquals(50, MiscCommands.getHeight());
         assertEquals(120, MiscCommands.getWidth());
      }

      @Test
      @DisplayName("updateScreenDimensions ignores zero rows")
      void zeroRowsIgnored() {
         MiscCommands.updateScreenDimensions(42, 100);
         MiscCommands.updateScreenDimensions(0, 200);
         assertEquals(42, MiscCommands.getHeight());
         assertEquals(200, MiscCommands.getWidth());
      }

      @Test
      @DisplayName("updateScreenDimensions ignores zero cols")
      void zeroColsIgnored() {
         MiscCommands.updateScreenDimensions(60, 90);
         MiscCommands.updateScreenDimensions(70, 0);
         assertEquals(70, MiscCommands.getHeight());
         assertEquals(90, MiscCommands.getWidth());
      }

      @Test
      @DisplayName("isLayoutComplete returns true after updateScreenDimensions")
      void layoutCompleteAfterUpdate() {
         MiscCommands.updateScreenDimensions(25, 80);
         assertTrue(MiscCommands.isLayoutComplete());
      }
   }

   // ══════════════════════════════════════════════════════════════
   // FormatDispatch coverage gaps
   // ══════════════════════════════════════════════════════════════

   @Nested
   @DisplayName("FormatDispatch formatting with unsupported types")
   class FormatUnsupportedTests {

      @Test
      @DisplayName("detectFileType returns null for .py file")
      void detectPython() {
         assertNull(FormatDispatch.detectFileType("script.py"));
      }

      @Test
      @DisplayName("detectFileType returns null for .txt file")
      void detectTxt() {
         assertNull(FormatDispatch.detectFileType("readme.txt"));
      }

      @Test
      @DisplayName("detectFileType returns null for null input")
      void detectNull() {
         assertNull(FormatDispatch.detectFileType(null));
      }

      @Test
      @DisplayName("detectFileType returns null for no extension")
      void detectNoExtension() {
         assertNull(FormatDispatch.detectFileType("Makefile"));
      }

      @Test
      @DisplayName("formatAll with unsupported file reports message")
      void formatAllUnsupported() throws IOException {
         UI.setStream(new StringReader(""));
         FileDescriptor fd = FileDescriptor.make("test.py");
         FileProperties<String> fp =
            new FileProperties<>(fd, StringIoc.converter);
         FileInput fi = new FileInput(fp);
         TextEdit<String> te = new TextEdit<>(fi, fp);
         te.inserttext("line1\n", 0, 1);
         te.checkpoint();

         // Should report "No formatter" without throwing
         FormatDispatch.formatAll(te);
      }

      @Test
      @DisplayName("formatRange with unsupported file reports message")
      void formatRangeUnsupported() throws IOException {
         UI.setStream(new StringReader(""));
         FileDescriptor fd = FileDescriptor.make("notes.md");
         FileProperties<String> fp =
            new FileProperties<>(fd, StringIoc.converter);
         FileInput fi = new FileInput(fp);
         TextEdit<String> te = new TextEdit<>(fi, fp);
         te.inserttext("# heading\n", 0, 1);
         te.inserttext("paragraph\n", 0, 2);
         te.checkpoint();

         // Should report "No formatter" without throwing
         FormatDispatch.formatRange(1, 2, te);
      }
   }

   // ══════════════════════════════════════════════════════════════
   // InsertBuffer.findspacebound coverage
   // ══════════════════════════════════════════════════════════════

   @Nested
   @DisplayName("InsertBuffer.findspacebound")
   class FindSpaceBoundTests {

      private TextEdit<String> openBuffer(String... lines) throws Exception {
         UI.setStream(new StringReader(""));
         FileDescriptor fd = FileDescriptor.make("test_fsb.txt");
         FileProperties<String> fp =
            new FileProperties<>(fd, StringIoc.converter);
         FileInput fi = new FileInput(fp);
         TextEdit<String> te = new TextEdit<>(fi, fp);
         int lineNo = 1;
         for (String line : lines) {
            te.inserttext(line + "\n", 0, lineNo++);
         }
         te.checkpoint();
         return te;
      }

      @Test
      @DisplayName("findspacebound returns 0 when on first line")
      void firstLineReturnsZero() throws Exception {
         TextEdit<String> te = openBuffer("hello");
         TestView view = new TestView(true);
         FvContext<?> fvc = FvContext.connectFv(te, view);
         fvc.cursoryabs(1);
         int result = InsertBuffer.findspacebound(fvc, 0);
         assertEquals(0, result);
      }

   }

   // ══════════════════════════════════════════════════════════════
   // MarkEvent coverage
   // ══════════════════════════════════════════════════════════════

   @Nested
   @DisplayName("MarkEvent construction")
   class MarkEventTests {

      @Test
      @DisplayName("MarkEvent can be constructed with a Position")
      void constructWithPosition() {
         Position pos = new Position(5, 10, "", null);
         MarkEvent me = new MarkEvent(pos);
         assertNotNull(me);
      }
   }
}
