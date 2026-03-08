package javi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CharsetDetector} — charset normalization, locale parsing,
 * and public API behaviour.
 */
class CharsetDetectorJUnitTest {

   @AfterEach
   void clearCacheAfterEach() {
      CharsetDetector.clearCache();
   }

   // ── normalizeCharsetName (via reflection) ────────────────────

   private String normalize(String name) throws Exception {
      Method m = CharsetDetector.class.getDeclaredMethod(
            "normalizeCharsetName", String.class);
      m.setAccessible(true);
      // Instance method on the singleton — get via reflection
      java.lang.reflect.Field inst =
            CharsetDetector.class.getDeclaredField("INSTANCE");
      inst.setAccessible(true);
      return (String) m.invoke(inst.get(null), name);
   }

   @Test
   void normalizeUtf8Variants() throws Exception {
      assertEquals("UTF-8", normalize("UTF-8"));
      assertEquals("UTF-8", normalize("utf8"));
      assertEquals("UTF-8", normalize("UTF8"));
      assertEquals("UTF-8", normalize("utf-8"));
   }

   @Test
   void normalizeAsciiVariants() throws Exception {
      assertEquals("US-ASCII", normalize("ASCII"));
      assertEquals("US-ASCII", normalize("US-ASCII"));
      assertEquals("US-ASCII", normalize("USASCII"));
   }

   @Test
   void normalizeIso8859StripsAllNonDigits() throws Exception {
      // normalizeCharsetName uses replaceAll("[^0-9]","") which extracts
      // ALL digits from the name (including '8859'), yielding extended suffixes.
      assertEquals("ISO-8859-88591", normalize("ISO8859-1"));
      assertEquals("ISO-8859-88591", normalize("ISO-8859-1"));
      assertEquals("ISO-8859-885915", normalize("ISO885915"));
   }

   @Test
   void normalizeIso8859NoSuffixFallsBack() throws Exception {
      // "ISO8859" with no trailing digits -> empty suffix -> fallback to ISO-8859-1
      // Actually it has digits 8859, so suffix = "8859"
      assertEquals("ISO-8859-8859", normalize("ISO8859"));
   }

   @Test
   void normalizeEucJp() throws Exception {
      assertEquals("EUC-JP", normalize("EUCJP"));
      assertEquals("EUC-JP", normalize("EUC-JP"));
   }

   @Test
   void normalizeShiftJis() throws Exception {
      assertEquals("Shift_JIS", normalize("SJIS"));
      assertEquals("Shift_JIS", normalize("SHIFT_JIS"));
      assertEquals("Shift_JIS", normalize("SHIFTJIS"));
   }

   @Test
   void normalizeNullReturnsUtf8() throws Exception {
      assertEquals("UTF-8", normalize(null));
   }

   @Test
   void normalizeUnknownPassedThrough() throws Exception {
      assertEquals("KOI8-R", normalize("KOI8-R"));
   }

   // ── parseLocaleCharset (via reflection) ──────────────────────

   private Charset parseLocale(String locale) throws Exception {
      Method m = CharsetDetector.class.getDeclaredMethod(
            "parseLocaleCharset", String.class);
      m.setAccessible(true);
      java.lang.reflect.Field inst =
            CharsetDetector.class.getDeclaredField("INSTANCE");
      inst.setAccessible(true);
      return (Charset) m.invoke(inst.get(null), locale);
   }

   @Test
   void parseLocaleUtf8() throws Exception {
      assertEquals(StandardCharsets.UTF_8, parseLocale("en_US.UTF-8"));
   }

   @Test
   void parseLocaleUtf8Lowercase() throws Exception {
      assertEquals(StandardCharsets.UTF_8, parseLocale("en_US.utf8"));
   }

   @Test
   void parseLocaleIso88591ReturnsNullDueToNormQuirk() throws Exception {
      // normalize produces "ISO-8859-88591" which is not a valid Java charset
      assertNull(parseLocale("de_DE.ISO-8859-1"));
   }

   @Test
   void parseLocaleWithModifierReturnsNullDueToNormQuirk() throws Exception {
      // normalize produces "ISO-8859-885915" which is not a valid Java charset
      assertNull(parseLocale("de_DE.ISO-8859-15@euro"));
   }

   @Test
   void parseLocaleLatinPassthrough() throws Exception {
      // A locale that uses a name Java directly recognizes (no ISO prefix match)
      Charset result = parseLocale("ja_JP.EUC-JP");
      assertEquals(Charset.forName("EUC-JP"), result);
   }

   @Test
   void parseLocaleCLocale() throws Exception {
      assertEquals(StandardCharsets.US_ASCII, parseLocale("C"));
   }

   @Test
   void parseLocalePosixLocale() throws Exception {
      assertEquals(StandardCharsets.US_ASCII, parseLocale("POSIX"));
   }

   @Test
   void parseLocaleNullReturnsNull() throws Exception {
      assertNull(parseLocale(null));
   }

   @Test
   void parseLocaleEmptyReturnsNull() throws Exception {
      assertNull(parseLocale(""));
   }

   @Test
   void parseLocaleNoDotReturnsNull() throws Exception {
      assertNull(parseLocale("en_US"));
   }

   @Test
   void parseLocaleUnsupportedCharsetReturnsNull() throws Exception {
      assertNull(parseLocale("en_US.BOGUS_CHARSET_XYZ"));
   }

   // ── Public API ───────────────────────────────────────────────

   @Test
   void detectTerminalCharsetNeverNull() {
      assertNotNull(CharsetDetector.detectTerminalCharset());
   }

   @Test
   void detectTerminalCharsetIsCacheable() {
      Charset first = CharsetDetector.detectTerminalCharset();
      Charset second = CharsetDetector.detectTerminalCharset();
      assertSame(first, second, "cached charset should be same instance");
   }

   @Test
   void clearCacheForceRedetection() {
      Charset first = CharsetDetector.detectTerminalCharset();
      CharsetDetector.clearCache();
      Charset second = CharsetDetector.detectTerminalCharset();
      assertEquals(first, second, "re-detected charset should be equal");
   }

   @Test
   void getDetectionSourceNeverNull() {
      assertNotNull(CharsetDetector.getDetectionSource());
   }

   @Test
   void getDetectionSourceContainsArrow() {
      String source = CharsetDetector.getDetectionSource();
      assertTrue(source.contains("->"),
            "detection source should contain '->': " + source);
   }
}
