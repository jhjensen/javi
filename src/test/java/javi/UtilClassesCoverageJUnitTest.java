package javi;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Coverage tests for {@link GrepFilter}, {@link GState},
 * {@link ClassConverter}, {@link Rgroup} static utilities
 * ({@code oBToInt}, {@code oBToFloat}), and small event
 * classes.
 */
class UtilClassesCoverageJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   // ── GrepFilter ────────────────────────────────────────────

   @Test
   @DisplayName("GrepFilter: matches simple pattern")
   void grepFilterMatches() {
      GrepFilter gf = new GrepFilter(".*\\.java$", false);
      assertTrue(gf.accept(new File("."), "Foo.java"));
   }

   @Test
   @DisplayName("GrepFilter: rejects non-matching pattern")
   void grepFilterRejects() {
      GrepFilter gf = new GrepFilter(".*\\.java$", false);
      assertFalse(gf.accept(new File("."), "Foo.txt"));
   }

   @Test
   @DisplayName("GrepFilter: invert matches non-matching")
   void grepFilterInvertMatches() {
      GrepFilter gf = new GrepFilter(".*\\.java$", true);
      assertTrue(gf.accept(new File("."), "Foo.txt"));
   }

   @Test
   @DisplayName("GrepFilter: invert rejects matching")
   void grepFilterInvertRejects() {
      GrepFilter gf = new GrepFilter(".*\\.java$", true);
      assertFalse(gf.accept(new File("."), "Foo.java"));
   }

   @Test
   @DisplayName("GrepFilter: partial match with find()")
   void grepFilterPartialMatch() {
      GrepFilter gf = new GrepFilter("test", false);
      assertTrue(gf.accept(new File("."), "my_test_file.txt"));
   }

   @Test
   @DisplayName("GrepFilter: no match for unrelated string")
   void grepFilterNoMatch() {
      GrepFilter gf = new GrepFilter("^xyz", false);
      assertFalse(gf.accept(new File("."), "abcdef.txt"));
   }

   @Test
   @DisplayName("GrepFilter: empty pattern matches everything")
   void grepFilterEmptyPattern() {
      GrepFilter gf = new GrepFilter("", false);
      assertTrue(gf.accept(new File("."), "anything.txt"));
   }

   @Test
   @DisplayName("GrepFilter: complex regex")
   void grepFilterComplexRegex() {
      GrepFilter gf = new GrepFilter(
         "^(Build|Makefile|CMake)", false);
      assertTrue(gf.accept(new File("."), "Makefile"));
      assertTrue(gf.accept(new File("."), "BuildFile.txt"));
      assertFalse(gf.accept(new File("."), "readme.txt"));
   }

   // ── GState ────────────────────────────────────────────────

   @Test
   @DisplayName("GState: setRegex stores pattern")
   void gstateSetRegex() {
      GState.setRegex("hello", 0);
      java.util.regex.Matcher m = GState.getRegex();
      assertTrue(m.reset("say hello world").find());
      assertFalse(m.reset("goodbye world").find());
   }

   @Test
   @DisplayName("GState: getRegex returns non-null")
   void gstateGetRegexNonNull() {
      assertNotNull(GState.getRegex());
   }

   @Test
   @DisplayName("GState: setRegex with CASE_INSENSITIVE flag")
   void gstateSetRegexCaseInsensitive() {
      GState.setRegex("hello",
         java.util.regex.Pattern.CASE_INSENSITIVE);
      java.util.regex.Matcher m = GState.getRegex();
      assertTrue(m.reset("HELLO WORLD").find());
   }

   @Test
   @DisplayName("GState: setRegex replaces previous")
   void gstateSetRegexReplaces() {
      GState.setRegex("first", 0);
      GState.setRegex("second", 0);
      java.util.regex.Matcher m = GState.getRegex();
      assertFalse(m.reset("first").find());
      assertTrue(m.reset("second").find());
   }

   // ── ClassConverter (via StringIoc.converter) ──────────────

   @Test
   @DisplayName("StringIoc.converter fromString round-trip")
   void classConverterFromString() {
      ClassConverter<String> cc = StringIoc.converter;
      assertEquals("hello", cc.fromString("hello"));
   }

   @Test
   @DisplayName("StringIoc.converter fromString empty string")
   void classConverterFromStringEmpty() {
      ClassConverter<String> cc = StringIoc.converter;
      assertEquals("", cc.fromString(""));
   }

   // ── Rgroup.oBToInt / oBToFloat ────────────────────────────

   @Test
   @DisplayName("oBToInt parses integer string")
   void oBToIntParsesInteger() throws InputException {
      assertEquals(42, Rgroup.oBToInt("42"));
   }

   @Test
   @DisplayName("oBToInt parses Integer object")
   void oBToIntParsesIntegerObj() throws InputException {
      assertEquals(99, Rgroup.oBToInt(Integer.valueOf(99)));
   }

   @Test
   @DisplayName("oBToFloat parses float string")
   void oBToFloatParsesString() throws InputException {
      assertEquals(3.14f, Rgroup.oBToFloat("3.14"), 0.001f);
   }

   @Test
   @DisplayName("oBToFloat parses Float object")
   void oBToFloatParsesFloatObj() throws InputException {
      assertEquals(2.5f, Rgroup.oBToFloat(Float.valueOf(2.5f)), 0.001f);
   }

   @Test
   @DisplayName("oBToInt with non-numeric throws InputException")
   void oBToIntNonNumeric() {
      try {
         Rgroup.oBToInt("abc");
         fail("should throw InputException");
      } catch (InputException e) {
         // expected
      }
   }

   // ── ExitEvent / ExitException ─────────────────────────────

   @Test
   @DisplayName("ExitEvent execute throws ExitException")
   void exitEventThrows() {
      ExitEvent ev = new ExitEvent();
      try {
         ev.execute();
         fail("should throw ExitException");
      } catch (ExitException e) {
         // expected
      }
   }

   @Test
   @DisplayName("ExitException is Throwable")
   void exitExceptionIsThrowable() {
      ExitException ex = new ExitException();
      assertNotNull(ex);
      assertTrue(ex instanceof Exception);
   }

   // ── CommandEvent ──────────────────────────────────────────

   @Test
   @DisplayName("CommandEvent construction does not throw")
   void commandEventConstruction() {
      CommandEvent ev = new CommandEvent("testcmd");
      assertNotNull(ev);
   }

   // ── ScrollEvent ───────────────────────────────────────────

   @Test
   @DisplayName("ScrollEvent construction does not throw")
   void scrollEventConstruction() {
      ScrollEvent ev = new ScrollEvent(5, false);
      assertNotNull(ev);
   }

   @Test
   @DisplayName("ScrollEvent horizontal construction")
   void scrollEventHorizontal() {
      ScrollEvent ev = new ScrollEvent(3, true);
      assertNotNull(ev);
   }

   // ── InputException ────────────────────────────────────────

   @Test
   @DisplayName("InputException message preserved")
   void inputExceptionMessage() {
      InputException ie = new InputException("test error");
      assertTrue(ie.toString().contains("test error"));
   }

   // ── Position basic methods ────────────────────────────────

   @Test
   @DisplayName("Position equiv same coordinates")
   void positionEquivSame() {
      MovePos mp = new MovePos(5, 10);
      Position p1 = new Position(5, 10, "", "");
      assertTrue(p1.equiv(mp));
   }

   @Test
   @DisplayName("Position equiv different coordinates")
   void positionEquivDifferent() {
      MovePos mp = new MovePos(6, 10);
      Position p1 = new Position(5, 10, "", "");
      assertFalse(p1.equiv(mp));
   }

   @Test
   @DisplayName("Position getMovable returns MovePos copy")
   void positionGetMovable() {
      Position p = new Position(3, 7, "", "comment");
      MovePos mp = p.getMovable();
      assertNotNull(mp);
      assertEquals(3, mp.x);
      assertEquals(7, mp.y);
   }

   @Test
   @DisplayName("MovePos construction")
   void movePosConstruction() {
      MovePos mp = new MovePos(5, 10);
      assertEquals(5, mp.x);
      assertEquals(10, mp.y);
   }

   @Test
   @DisplayName("MovePos copy constructor")
   void movePosCopy() {
      MovePos original = new MovePos(8, 15);
      MovePos mp = new MovePos(original);
      assertEquals(8, mp.x);
      assertEquals(15, mp.y);
   }
}
