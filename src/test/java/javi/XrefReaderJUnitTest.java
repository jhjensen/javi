package javi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests for {@link XrefReader} inner converter logic.
 *
 * <p>
 * The XrefConv.fromString method parses grep-style output
 * (file:line:comment) into Position objects. These tests exercise
 * that parsing without needing external commands.
 * </p>
 */
class XrefReaderJUnitTest {

   /**
    * Access the static xconverter field and call fromString.
    */
   private Position convert(String line) throws Exception {
      java.lang.reflect.Field f = XrefReader.class.getDeclaredField("xconverter");
      f.setAccessible(true);
      ClassConverter<Position> conv = (ClassConverter<Position>) f.get(null);
      return conv.fromString(line);
   }

   @Test
   void parsesStandardGrepLine() throws Exception {
      Position pos = convert("src/main/Foo.java:42: public void bar()");
      assertNotNull(pos);
      assertEquals(42, pos.y);
      assertTrue(pos.filename.toString().contains("src/main/Foo.java"));
   }

   @Test
   void parsesLineWithColonInPath() throws Exception {
      // Drive letter path like C:src/file.java:10: something
      Position pos = convert("C:src/file.java:10: int x = 0;");
      assertNotNull(pos);
      assertEquals(10, pos.y);
   }

   @Test
   void parsesSimpleFileLine() throws Exception {
      Position pos = convert("test.c:1: #include <stdio.h>");
      assertNotNull(pos);
      assertEquals(1, pos.y);
      assertTrue(pos.filename.toString().contains("test.c"));
   }

   @Test
   void parsesDeepPath() throws Exception {
      Position pos = convert(
            "src/main/java/javi/Vt100Parser.java:100:    private int state;");
      assertNotNull(pos);
      assertEquals(100, pos.y);
   }

   @Test
   void parsesLargeLineNumber() throws Exception {
      Position pos = convert("big.java:99999: // end of file");
      assertNotNull(pos);
      assertEquals(99999, pos.y);
   }

   @Test
   void parsesLineWithSpacesInComment() throws Exception {
      Position pos = convert("foo.py:7:    def hello(self, name):");
      assertNotNull(pos);
      assertEquals(7, pos.y);
      assertTrue(pos.comment.contains("def hello"));
   }

   @Test
   void emptyLineReturnsDefaultPosition() throws Exception {
      Position pos = convert("");
      // Empty line returns defpos (the default position)
      assertNotNull(pos);
   }

   @Test
   void linpatMatchesStandardGrepFormat() throws Exception {
      // Verify the regex pattern used internally
      java.lang.reflect.Field f = XrefReader.class.getDeclaredField("linepat");
      f.setAccessible(true);
      Matcher m = (Matcher) f.get(null);
      assertTrue(m.reset("src/Foo.java:10: hello").matches());
   }

   @Test
   void linpatMatchesTildeInPath() throws Exception {
      java.lang.reflect.Field f = XrefReader.class.getDeclaredField("linepat");
      f.setAccessible(true);
      Matcher m = (Matcher) f.get(null);
      assertTrue(m.reset("~/src/Foo.java:10: hello").matches());
   }

   @Test
   void linpatMatchesBackslashPath() throws Exception {
      java.lang.reflect.Field f = XrefReader.class.getDeclaredField("linepat");
      f.setAccessible(true);
      Matcher m = (Matcher) f.get(null);
      assertTrue(m.reset("src\\main\\Foo.java:10: hello").matches());
   }

   @Test
   void linpatMatchesDriveLetter() throws Exception {
      java.lang.reflect.Field f = XrefReader.class.getDeclaredField("linepat");
      f.setAccessible(true);
      Matcher m = (Matcher) f.get(null);
      assertTrue(m.reset("C:src/Foo.java:10: hello").matches());
   }

   @Test
   void linpatRejectsNoLineNumber() throws Exception {
      java.lang.reflect.Field f = XrefReader.class.getDeclaredField("linepat");
      f.setAccessible(true);
      Matcher m = (Matcher) f.get(null);
      assertFalse(m.reset("src/Foo.java: no number").matches());
   }

   @Test
   void converterHandlesMalformedLine() throws Exception {
      // Should not throw — returns defpos
      Position pos = convert("not a valid grep line at all");
      assertNotNull(pos);
   }

   @Test
   void converterHandlesMinimalLine() throws Exception {
      Position pos = convert("a:1:x");
      assertNotNull(pos);
   }
}
