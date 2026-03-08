package javi;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GrepFilter} — filename filtering via regex.
 */
class GrepFilterJUnitTest {

   private static final File DUMMY_DIR = new File(".");

   // ── Normal (non-inverted) matching ───────────────────────────

   @Test
   void matchesSimplePattern() {
      GrepFilter gf = new GrepFilter("foo", false);
      assertTrue(gf.accept(DUMMY_DIR, "foobar.txt"));
   }

   @Test
   void noMatchReturnsFalse() {
      GrepFilter gf = new GrepFilter("foo", false);
      assertFalse(gf.accept(DUMMY_DIR, "barbaz.txt"));
   }

   @Test
   void matchesRegexDot() {
      GrepFilter gf = new GrepFilter("\\.java$", false);
      assertTrue(gf.accept(DUMMY_DIR, "Main.java"));
      assertFalse(gf.accept(DUMMY_DIR, "Main.class"));
   }

   @Test
   void matchesAnywhere() {
      GrepFilter gf = new GrepFilter("test", false);
      assertTrue(gf.accept(DUMMY_DIR, "my_test_file.txt"));
   }

   @Test
   void caseMatters() {
      GrepFilter gf = new GrepFilter("Foo", false);
      assertFalse(gf.accept(DUMMY_DIR, "foo"));
      assertTrue(gf.accept(DUMMY_DIR, "Foo"));
   }

   // ── Inverted matching ────────────────────────────────────────

   @Test
   void invertedMatchExcludes() {
      GrepFilter gf = new GrepFilter("\\.class$", true);
      assertFalse(gf.accept(DUMMY_DIR, "Main.class"));
   }

   @Test
   void invertedNonMatchIncludes() {
      GrepFilter gf = new GrepFilter("\\.class$", true);
      assertTrue(gf.accept(DUMMY_DIR, "Main.java"));
   }

   @Test
   void invertedAllIncluded() {
      GrepFilter gf = new GrepFilter("NOMATCH", true);
      assertTrue(gf.accept(DUMMY_DIR, "anything.txt"));
      assertTrue(gf.accept(DUMMY_DIR, "file2.java"));
   }

   // ── Edge cases ───────────────────────────────────────────────

   @Test
   void emptyRegexMatchesEverything() {
      GrepFilter gf = new GrepFilter("", false);
      assertTrue(gf.accept(DUMMY_DIR, "anything"));
      assertTrue(gf.accept(DUMMY_DIR, ""));
   }

   @Test
   void dotStarMatchesAll() {
      GrepFilter gf = new GrepFilter(".*", false);
      assertTrue(gf.accept(DUMMY_DIR, "file.txt"));
   }

   @Test
   void complexRegexWorks() {
      GrepFilter gf = new GrepFilter("^(src|test).*\\.java$", false);
      assertTrue(gf.accept(DUMMY_DIR, "src/Main.java"));
      assertFalse(gf.accept(DUMMY_DIR, "build/out.class"));
   }

   @Test
   void specialCharsInFilename() {
      GrepFilter gf = new GrepFilter("\\[", false);
      assertTrue(gf.accept(DUMMY_DIR, "file[1].txt"));
      assertFalse(gf.accept(DUMMY_DIR, "file.txt"));
   }
}
