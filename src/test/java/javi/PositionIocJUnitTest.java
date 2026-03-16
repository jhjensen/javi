package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.StringReader;

/**
 * Tests for {@link PositionIoc} and its inner {@code PositionConverter}.
 */
class PositionIocJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void lock() throws Exception {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void unlock() throws Exception {
      EventQueue.biglock2.unlock();
   }

   // ── PositionConverter.fromString tests ──────────────────────

   @Test
   void converterParsesStandardFormat() throws Exception {
      Position pos = ((ClassConverter<Position>) PositionIoc.pconverter).fromString(
            "src/Foo.java(10,5 -error: something");
      assertEquals(10, pos.x);
      assertEquals(5, pos.y);
      assertEquals("error: something", pos.comment);
   }

   @Test
   void converterParsesNoCommaFormat() throws Exception {
      // When comma comes after dash (or no comma), x=0
      Position pos = ((ClassConverter<Position>) PositionIoc.pconverter).fromString(
            "file.c(42 -warning: unused");
      assertEquals(0, pos.x);
      assertEquals(42, pos.y);
      assertEquals("warning: unused", pos.comment);
   }

   @Test
   void converterParsesEmptyString() throws Exception {
      Position pos = ((ClassConverter<Position>) PositionIoc.pconverter).fromString("");
      assertSame(PositionIoc.defpos, pos);
   }

   @Test
   void converterParsesLargeLineNumbers() throws Exception {
      Position pos = ((ClassConverter<Position>) PositionIoc.pconverter).fromString(
            "Big.java(999,12345 -info");
      assertEquals(999, pos.x);
      assertEquals(12345, pos.y);
      assertEquals("info", pos.comment);
   }

   @Test
   void converterParsesCommentWithSpecialChars() throws Exception {
      Position pos = ((ClassConverter<Position>) PositionIoc.pconverter).fromString(
            "test.java(1,2 -error: expected ')' but found '}'");
      assertEquals(1, pos.x);
      assertEquals(2, pos.y);
      assertTrue(pos.comment.contains("expected ')'"));
   }

   @Test
   void converterParsesMinimalInput() throws Exception {
      Position pos = ((ClassConverter<Position>) PositionIoc.pconverter).fromString(
            "a(1 -x");
      assertEquals(0, pos.x);
      assertEquals(1, pos.y);
      assertEquals("x", pos.comment);
   }

   // ── PositionIoc construction and getnext tests ──────────────

   private PositionIoc makeIoc(String name, String input) {
      BufferedReader reader = new BufferedReader(new StringReader(input));
      return new PositionIoc(name, reader, PositionIoc.pconverter);
   }

   private int drainResults(PositionIoc ioc) {
      int count = 0;
      while (ioc.getnext() != null)
         count++;
      return count;
   }

   @Test
   void getnextParsesPositionLines() throws Exception {
      PositionIoc ioc = makeIoc("test-parse",
            "src/A.java(10,5 -err1\nsrc/B.java(20 -err2\n");

      assertEquals(2, drainResults(ioc));
      assertEquals(2, ioc.resultCount);
   }

   @Test
   void getnextSkipsUnparseableLines() throws Exception {
      PositionIoc ioc = makeIoc("test-skip",
            "Building project...\nCompiling 5 files...\n"
                  + "src/Foo.java(10,3 -error: syntax\nBUILD SUCCESSFUL\n");

      assertEquals(1, drainResults(ioc));
      assertEquals(1, ioc.resultCount);
   }

   @Test
   void getnextHandlesEmptyInput() throws Exception {
      PositionIoc ioc = makeIoc("test-empty", "");

      assertEquals(0, drainResults(ioc));
      assertEquals(0, ioc.resultCount);
   }

   @Test
   void formatCompletionMessageWithResults() throws Exception {
      PositionIoc ioc = makeIoc("testfmt",
            "src/A.java(1,2 -err\nDONE\n");

      drainResults(ioc);

      String msg = ioc.formatCompletionMessage();
      assertTrue(msg.contains("complete"));
      assertTrue(msg.contains("1 results"));
   }

   @Test
   void formatCompletionMessageNoOutput() throws Exception {
      PositionIoc ioc = makeIoc("testfmt2", "");

      drainResults(ioc);

      String msg = ioc.formatCompletionMessage();
      assertTrue(msg.contains("no output"));
      assertTrue(msg.contains("0 results"));
   }

   @Test
   void formatCompletionMessageTruncatesLongLine() throws Exception {
      StringBuilder longLine = new StringBuilder("src/X.java(1 -");
      for (int i = 0; i < 250; i++)
         longLine.append('x');
      PositionIoc ioc = makeIoc("testfmt3", longLine.toString() + "\n");

      drainResults(ioc);

      String msg = ioc.formatCompletionMessage();
      assertTrue(msg.length() < 300, "Message should be bounded");
   }

   @Test
   void multiplePositionsParsed() throws Exception {
      PositionIoc ioc = makeIoc("test-multi",
            "a.java(1,2 -e1\nb.java(3,4 -e2\nc.java(5,6 -e3\n");

      assertEquals(3, drainResults(ioc));
      assertEquals(3, ioc.resultCount);
   }

   @Test
   void emptyLinesSkipped() throws Exception {
      PositionIoc ioc = makeIoc("test-blanks",
            "\n\nsrc/A.java(1,2 -e\n\n");

      assertEquals(1, drainResults(ioc));
      assertEquals(1, ioc.resultCount);
   }

   @Test
   void defposHasZeroCoordinates() throws Exception {
      Position dp = PositionIoc.defpos;
      assertEquals(0, dp.x);
      assertEquals(0, dp.y);
      assertNotNull(dp.filename);
   }
}
