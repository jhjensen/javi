package javi.plugin;

import java.lang.reflect.Field;

import javi.ClassConverter;
import javi.Position;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for {@link FindBugs} — the FindBugs/SpotBugs plugin.
 *
 * <p>Exercises the {@code FindBugConv.fromString()} parser that
 * converts FindBugs emacs-format output into {@link Position}
 * objects. The converter is accessed via reflection since
 * {@code FindBugConv} is a private inner class.</p>
 */
class FindBugsJUnitTest {

   private static ClassConverter<Position> converter;

   @BeforeAll
   @SuppressWarnings("unchecked")
   static void extractConverter() throws Exception {
      // FindBugRunner is a package-private static inner class of FindBugs.
      // FindBugConv is private inside FindBugRunner.
      // Access the static 'converter' field via reflection.
      Class<?> runnerClass = Class.forName(
         "javi.plugin.FindBugs$FindBugRunner");
      Field f = runnerClass.getDeclaredField("converter");
      f.setAccessible(true);
      converter = (ClassConverter<Position>) f.get(null);
      assertNotNull(converter, "converter field should be non-null");
   }

   // ── Valid line parsing ───────────────────────────────────

   @Nested
   @DisplayName("fromString — valid lines")
   class ValidLines {

      @Test
      @DisplayName("standard path:line:message format")
      void standardFormat() {
         Position pos = converter.fromString(
            "javi/TextEdit.java:42:possible null dereference");
         assertEquals(42, pos.y);
         assertEquals(0, pos.x);
         assertEquals("possible null dereference", pos.comment);
         assertEquals("src/javi/TextEdit.java", pos.filename.getShortName());
      }

      @Test
      @DisplayName("nested path with subdirectories")
      void nestedPath() {
         Position pos = converter.fromString(
            "javi/git/GitProcess.java:100:unused import");
         assertEquals(100, pos.y);
         assertEquals("unused import", pos.comment);
         assertEquals("src/javi/git/GitProcess.java",
            pos.filename.getShortName());
      }

      @Test
      @DisplayName("large line number")
      void largeLineNumber() {
         Position pos = converter.fromString(
            "Foo.java:99999:warning");
         assertEquals(99999, pos.y);
         assertEquals("warning", pos.comment);
      }

      @Test
      @DisplayName("message with colons")
      void messageWithColons() {
         Position pos = converter.fromString(
            "Bar.java:10:error: expected ';' at: line end");
         assertEquals(10, pos.y);
         assertEquals("error: expected ';' at: line end", pos.comment);
      }

      @Test
      @DisplayName("line number with surrounding spaces is trimmed")
      void lineNumberTrimmed() {
         Position pos = converter.fromString(
            "Test.java: 7 :info");
         assertEquals(7, pos.y);
         assertEquals("info", pos.comment);
      }
   }

   // ── Edge cases ───────────────────────────────────────────

   @Nested
   @DisplayName("fromString — edge cases")
   class EdgeCases {

      @Test
      @DisplayName("empty line returns badpos")
      void emptyLine() {
         Position pos = converter.fromString("");
         assertSame(Position.badpos, pos);
      }

      @Test
      @DisplayName("no colon after first 3 chars returns badpos")
      void noColonReturnsBadpos() {
         Position pos = converter.fromString("ab");
         assertSame(Position.badpos, pos);
      }

      @Test
      @DisplayName("line number 0 normalized to 1")
      void lineNumberZeroNormalized() {
         Position pos = converter.fromString("File.java:0:msg");
         assertEquals(1, pos.y);
      }

      @Test
      @DisplayName("negative line number normalized to 1")
      void negativeLineNumberNormalized() {
         Position pos = converter.fromString("File.java:-5:msg");
         assertEquals(1, pos.y);
      }

      @Test
      @DisplayName("path gets src/ prefix")
      void pathGetsSrcPrefix() {
         Position pos = converter.fromString(
            "com/example/Main.java:1:test");
         assertEquals("src/com/example/Main.java",
            pos.filename.getShortName());
      }
   }
}
