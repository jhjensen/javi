package javi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GState} — global search regex state.
 */
class GStateJUnitTest {

   @BeforeEach
   void resetState() {
      GState.setRegex("", 0);
   }

   @Test
   void initialRegexMatchesEmptyString() {
      Matcher m = GState.getRegex();
      assertTrue(m.find(), "empty pattern should match empty input");
   }

   @Test
   void setRegexChangesPattern() {
      GState.setRegex("hello", 0);
      Matcher m = GState.getRegex();
      m.reset("say hello world");
      assertTrue(m.find());
      assertEquals("hello", m.group());
   }

   @Test
   void setRegexCaseInsensitive() {
      GState.setRegex("abc", Pattern.CASE_INSENSITIVE);
      Matcher m = GState.getRegex();
      m.reset("XYZ ABC DEF");
      assertTrue(m.find());
      assertEquals("ABC", m.group());
   }

   @Test
   void setRegexNoMatchReturnsFalse() {
      GState.setRegex("zzz", 0);
      Matcher m = GState.getRegex();
      m.reset("no match here");
      assertFalse(m.find());
   }

   @Test
   void getRegexReturnsNewMatcherEachTime() {
      GState.setRegex("x", 0);
      Matcher m1 = GState.getRegex();
      Matcher m2 = GState.getRegex();
      assertNotSame(m1, m2);
   }

   @Test
   void setRegexWithDotAll() {
      GState.setRegex("a.b", Pattern.DOTALL);
      Matcher m = GState.getRegex();
      m.reset("a\nb");
      assertTrue(m.find(), "DOTALL should make . match newline");
   }

   @Test
   void setRegexOverwritesPrevious() {
      GState.setRegex("first", 0);
      GState.setRegex("second", 0);
      Matcher m = GState.getRegex();
      m.reset("first second");
      assertTrue(m.find());
      assertEquals("second", m.group());
   }

   @Test
   void emptyPatternMatchesAnything() {
      GState.setRegex("", 0);
      Matcher m = GState.getRegex();
      m.reset("anything");
      assertTrue(m.find());
   }
}
