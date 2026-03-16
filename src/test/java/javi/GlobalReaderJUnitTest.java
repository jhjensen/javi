package javi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Matcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link GlobalReader} — pattern matching in
 * the {@code linepat} Matcher and {@code GlobalConv.fromString}.
 */
class GlobalReaderJUnitTest {

   // Access private static linepat Matcher via reflection
   private static Matcher getLinePat() throws Exception {
      Field f = GlobalReader.class.getDeclaredField("linepat");
      f.setAccessible(true);
      return (Matcher) f.get(null);
   }

   // Access the xconverter (GlobalConv) instance
   private static ClassConverter<Position> getConverter() throws Exception {
      Field f = GlobalReader.class.getDeclaredField("xconverter");
      f.setAccessible(true);
      @SuppressWarnings("unchecked")
      ClassConverter<Position> conv = (ClassConverter<Position>) f.get(null);
      return conv;
   }

   // ================================================================
   // linepat regex tests
   // ================================================================

   @Nested
   @DisplayName("linepat pattern")
   class LinePatTests {

      @Test
      @DisplayName("matches standard file:line:comment format")
      void matchesStandardFormat() throws Exception {
         Matcher m = getLinePat();
         assertTrue(m.reset("src/Foo.java:42: hello world").matches());
         assertEquals("src/Foo.java", m.group(1));
         assertEquals("42", m.group(3));
         assertEquals("hello world", m.group(4));
      }

      @Test
      @DisplayName("matches single file name with line number")
      void matchesSingleFile() throws Exception {
         Matcher m = getLinePat();
         assertTrue(m.reset("Main.java:1: class Main").matches());
         assertEquals("Main.java", m.group(1));
         assertEquals("1", m.group(3));
      }

      @Test
      @DisplayName("matches path with nested directories")
      void matchesNestedDirs() throws Exception {
         Matcher m = getLinePat();
         assertTrue(m.reset("a/b/c/d.cpp:999: code").matches());
         assertEquals("a/b/c/d.cpp", m.group(1));
         assertEquals("999", m.group(3));
      }

      @Test
      @DisplayName("matches Windows drive letter path")
      void matchesWindowsDrive() throws Exception {
         Matcher m = getLinePat();
         assertTrue(m.reset("C:\\src\\File.cpp:100: text").matches());
         assertEquals("C:\\src\\File.cpp", m.group(1));
         assertEquals("C:", m.group(2));
         assertEquals("100", m.group(3));
      }

      @Test
      @DisplayName("matches tilde path")
      void matchesTildePath() throws Exception {
         Matcher m = getLinePat();
         assertTrue(m.reset("~/proj/test.py:7: def func").matches());
         assertEquals("~/proj/test.py", m.group(1));
         assertEquals("7", m.group(3));
      }

      @Test
      @DisplayName("does not match empty string")
      void noMatchEmpty() throws Exception {
         Matcher m = getLinePat();
         assertFalse(m.reset("").matches());
      }

      @Test
      @DisplayName("does not match text without line number")
      void noMatchNoLineNumber() throws Exception {
         Matcher m = getLinePat();
         assertFalse(m.reset("just some text").matches());
      }

      @Test
      @DisplayName("does not match incomplete reference")
      void noMatchIncomplete() throws Exception {
         Matcher m = getLinePat();
         assertFalse(m.reset("file.txt:").matches());
      }

      @Test
      @DisplayName("leading spaces prevent match")
      void noMatchLeadingSpaces() throws Exception {
         Matcher m = getLinePat();
         assertFalse(m.reset("  file.txt:10: code").matches());
      }

      @Test
      @DisplayName("matches file with dots in name")
      void matchesDottedFileName() throws Exception {
         Matcher m = getLinePat();
         assertTrue(m.reset("util.test.js:55: expect").matches());
         assertEquals("util.test.js", m.group(1));
      }

      @Test
      @DisplayName("captures comment field with leading spaces stripped")
      void commentFieldHasLeadingSpacesStripped() throws Exception {
         Matcher m = getLinePat();
         assertTrue(m.reset("Foo.java:10:   indented code").matches());
         assertEquals("indented code", m.group(4));
      }
   }

   // ================================================================
   // GlobalConv.fromString tests
   // ================================================================

   @Nested
   @DisplayName("GlobalConv.fromString")
   class GlobalConvTests {

      @Test
      @DisplayName("parses standard grep-format line")
      void standardGrepLine() throws Exception {
         ClassConverter<Position> conv = getConverter();
         Position pos = conv.fromString("src/Main.java:42: doStuff()");
         assertNotNull(pos);
         assertEquals(42, pos.y);
         assertEquals(0, pos.x);
         assertEquals("src/Main.java", pos.filename.shortName);
      }

      @Test
      @DisplayName("parses fallback colon-delimited format")
      void fallbackColonFormat() throws Exception {
         ClassConverter<Position> conv = getConverter();
         // This doesn't match linepat (has extra content), falls to
         // secondary parser
         Position pos = conv.fromString("Module.java:25:method call");
         assertNotNull(pos);
         assertEquals(25, pos.y);
      }

      @Test
      @DisplayName("returns default position for empty line")
      void emptyLineReturnsDefault() throws Exception {
         ClassConverter<Position> conv = getConverter();
         Position pos = conv.fromString("");
         // empty line returns defpos (the default position)
         assertNotNull(pos);
      }

      @Test
      @DisplayName("returns position for malformed line")
      void malformedLineReturnsPosition() throws Exception {
         ClassConverter<Position> conv = getConverter();
         Position pos = conv.fromString("no colons here at all");
         // malformed lines return defpos via exception path
         assertNotNull(pos);
      }

      @Test
      @DisplayName("parses line with high line number")
      void highLineNumber() throws Exception {
         ClassConverter<Position> conv = getConverter();
         Position pos = conv.fromString("bigfile.log:99999: lots of content");
         assertNotNull(pos);
         assertEquals(99999, pos.y);
      }

      @Test
      @DisplayName("parses nested path correctly")
      void nestedPathParsed() throws Exception {
         ClassConverter<Position> conv = getConverter();
         Position pos = conv.fromString("a/b/c/deep.h:1: #pragma once");
         assertNotNull(pos);
         assertEquals("a/b/c/deep.h", pos.filename.shortName);
         assertEquals(1, pos.y);
      }
   }
}
