package javi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for {@link Wcwidth} Unicode character width utility.
 */
class WcwidthJUnitTest {

   @Nested
   @DisplayName("Basic character widths")
   class BasicWidths {

      @Test
      void asciiLetterIsWidth1() {
         assertEquals(1, Wcwidth.of('A'));
         assertEquals(1, Wcwidth.of('z'));
         assertEquals(1, Wcwidth.of('0'));
      }

      @Test
      void asciiPunctuationIsWidth1() {
         assertEquals(1, Wcwidth.of('.'));
         assertEquals(1, Wcwidth.of('!'));
         assertEquals(1, Wcwidth.of(' '));
      }

      @Test
      void nulIsWidth0() {
         assertEquals(0, Wcwidth.of(0));
      }

      @Test
      void controlCharsAreNegative() {
         assertEquals(-1, Wcwidth.of(1));
         assertEquals(-1, Wcwidth.of(0x1B)); // ESC
         assertEquals(-1, Wcwidth.of(0x7F)); // DEL
         assertEquals(-1, Wcwidth.of(0x80)); // C1
         assertEquals(-1, Wcwidth.of(0x9F)); // C1 end
      }
   }

   @Nested
   @DisplayName("Wide characters (width 2)")
   class WideChars {

      @Test
      void cjkUnifiedIdeograph() {
         assertEquals(2, Wcwidth.of(0x4E00)); // first CJK
         assertEquals(2, Wcwidth.of(0x9FFF)); // last CJK
      }

      @Test
      void hangulSyllable() {
         assertEquals(2, Wcwidth.of(0xAC00));
         assertEquals(2, Wcwidth.of(0xD7AF));
      }

      @Test
      void hiragana() {
         assertEquals(2, Wcwidth.of(0x3041)); // small 'a'
         assertEquals(2, Wcwidth.of(0x3042)); // 'a'
      }

      @Test
      void katakana() {
         assertEquals(2, Wcwidth.of(0x30A0));
         assertEquals(2, Wcwidth.of(0x30FF));
      }

      @Test
      void fullwidthLatin() {
         assertEquals(2, Wcwidth.of(0xFF01)); // fullwidth !
         assertEquals(2, Wcwidth.of(0xFF5A)); // fullwidth z
      }

      @Test
      void emoji() {
         assertEquals(2, Wcwidth.of(0x1F600)); // grinning face
         assertEquals(2, Wcwidth.of(0x1F4A9)); // pile of poo
         assertEquals(2, Wcwidth.of(0x2764));  // red heart
         assertEquals(2, Wcwidth.of(0x1F680)); // rocket
      }

      @Test
      void cjkExtensionB() {
         assertEquals(2, Wcwidth.of(0x20000)); // CJK Ext B
         assertEquals(2, Wcwidth.of(0x2A6DF));
      }
   }

   @Nested
   @DisplayName("Combining characters (width 0)")
   class CombiningChars {

      @Test
      void combiningDiacriticals() {
         assertEquals(0, Wcwidth.of(0x0300)); // grave accent
         assertEquals(0, Wcwidth.of(0x0301)); // acute accent
         assertEquals(0, Wcwidth.of(0x036F)); // end of range
      }

      @Test
      void variationSelectors() {
         assertEquals(0, Wcwidth.of(0xFE00));
         assertEquals(0, Wcwidth.of(0xFE0F));
      }

      @Test
      void combiningForSymbols() {
         assertEquals(0, Wcwidth.of(0x20D0));
         assertEquals(0, Wcwidth.of(0x20F0));
      }
   }

   @Nested
   @DisplayName("String width measurement")
   class StringWidths {

      @Test
      void asciiString() {
         assertEquals(5, Wcwidth.stringWidth("hello"));
      }

      @Test
      void emptyString() {
         assertEquals(0, Wcwidth.stringWidth(""));
      }

      @Test
      void mixedAsciiAndCjk() {
         // "A" (1) + CJK char (2) + "B" (1) = 4
         assertEquals(4, Wcwidth.stringWidth(
            "A\u4E00B"));
      }

      @Test
      void emojiString() {
         // Two emoji (each 2 cols) = 4
         String s = new String(Character.toChars(0x1F600))
            + new String(Character.toChars(0x1F680));
         assertEquals(4, Wcwidth.stringWidth(s));
      }

      @Test
      void combiningDoesNotAddWidth() {
         // 'e' (1) + combining acute (0) = 1
         assertEquals(1, Wcwidth.stringWidth("e\u0301"));
      }

      @Test
      void substringWidth() {
         String s = "AB\u4E00CD";
         // "B\u4E00" = 1 + 2 = 3
         assertEquals(3, Wcwidth.stringWidth(s, 1, 3));
      }
   }

   @Nested
   @DisplayName("expandWide")
   class ExpandWideTests {

      @Test
      void asciiOnlyUnchanged() {
         assertEquals("hello", Wcwidth.expandWide("hello"));
      }

      @Test
      void singleWideCharGetsPadding() {
         // U+4E00 (CJK) -> char + \0
         assertEquals("\u4E00\0", Wcwidth.expandWide("\u4E00"));
      }

      @Test
      void mixedAsciiAndWide() {
         assertEquals("A\u4E00\0B", Wcwidth.expandWide("A\u4E00B"));
      }

      @Test
      void multipleWideChars() {
         assertEquals("\u4F60\0\u597D\0",
            Wcwidth.expandWide("\u4F60\u597D")); // 你好
      }

      @Test
      void emptyStringUnchanged() {
         assertEquals("", Wcwidth.expandWide(""));
      }

      @Test
      void surrogatePairNotPadded() {
         // U+1F600 (grinning face) — supplementary code point.
         // Surrogate pair already occupies 2 Java chars = 2 cols,
         // so no WIDE_PAD is needed.
         String emoji = new String(Character.toChars(0x1F600));
         String expanded = Wcwidth.expandWide(emoji);
         assertEquals(2, expanded.length());
         assertEquals(emoji, expanded);
      }

      @Test
      void bmpWidePadded_supplementaryNot() {
         // BMP CJK (1 char) gets padded; emoji (2 chars) does not.
         String input = "\u4E00"
            + new String(Character.toChars(0x1F600));
         String expanded = Wcwidth.expandWide(input);
         // CJK: 1 + pad = 2; emoji: 2 (no pad) = 2; total = 4
         assertEquals(4, expanded.length());
         assertEquals('\u4E00', expanded.charAt(0));
         assertEquals('\0', expanded.charAt(1));
      }

      @Test
      void multipleSupplementaryEmoji() {
         String grin = new String(Character.toChars(0x1F600));
         String rocket = new String(Character.toChars(0x1F680));
         String expanded = Wcwidth.expandWide(grin + rocket);
         // No padding for either: 2 + 2 = 4 Java chars
         assertEquals(4, expanded.length());
         assertEquals(grin + rocket, expanded);
      }
   }

   @Nested
   @DisplayName("stripPadding")
   class StripPaddingTests {

      @Test
      void noPaddingReturnsOriginal() {
         String s = "hello";
         assertSame(s, Wcwidth.stripPadding(s));
      }

      @Test
      void stripsNulPadding() {
         assertEquals("\u4E00", Wcwidth.stripPadding("\u4E00\0"));
      }

      @Test
      void mixedContent() {
         assertEquals("A\u4E00B",
            Wcwidth.stripPadding("A\u4E00\0B"));
      }
   }

   @Nested
   @DisplayName("compressAttrs")
   class CompressAttrsTests {

      @Test
      void nullAttrsReturnsNull() {
         assertEquals(null,
            Wcwidth.compressAttrs("A\0B", null));
      }

      @Test
      void noPaddingReturnsOriginal() {
         int[] attrs = {1, 2, 3};
         assertSame(attrs,
            Wcwidth.compressAttrs("ABC", attrs));
      }

      @Test
      void removesPaddingPositions() {
         // Buffer: "A\u4E00\0B" — attrs [10, 20, 20, 30]
         // Compressed: "A\u4E00B" — attrs [10, 20, 30]
         int[] attrs = {10, 20, 20, 30};
         int[] result = Wcwidth.compressAttrs(
            "A\u4E00\0B", attrs);
         assertEquals(3, result.length);
         assertEquals(10, result[0]);
         assertEquals(20, result[1]);
         assertEquals(30, result[2]);
      }
   }
}
