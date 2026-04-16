package javi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for surrogate pair / emoji handling in core string
 * operations. Verifies that grapheme-cluster-aware logic
 * correctly handles multi-char Unicode characters, including
 * ZWJ sequences, flag sequences, and skin-tone modifiers.
 */
class EmojiHandlingJUnitTest {

   // Simple emoji test strings
   private static final String GRINNING = "\uD83D\uDE00"; // 😀
   private static final String WAVE = "\uD83D\uDC4B"; // 👋
   private static final String HEART = "\u2764"; // ❤ (BMP, single char)

   // Combined emoji: ZWJ family (man + ZWJ + woman + ZWJ + girl)
   private static final String FAMILY =
      "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67";
   // Flag sequence: regional indicator U+S U+E (Sweden)
   private static final String FLAG_SE =
      "\uD83C\uDDF8\uD83C\uDDEA";
   // Skin tone modifier: wave + medium skin tone
   private static final String WAVE_SKIN =
      "\uD83D\uDC4B\uD83C\uDFFD";

   @Test
   void surrogateCharCount() {
      assertEquals(2, GRINNING.length());
      assertEquals(2, Character.charCount(
         Character.codePointAt(GRINNING, 0)));
      assertEquals(1, GRINNING.codePointCount(0,
         GRINNING.length()));
   }

   @Test
   void bmpEmojiIsSingleChar() {
      assertEquals(1, HEART.length());
      assertEquals(1, HEART.codePointCount(0, HEART.length()));
   }

   @Test
   void forwardDeleteEmoji() {
      // Simulate deleteChars forward logic
      String line = "ab" + GRINNING + "cd";
      int insertx = 2; // cursor at position of emoji
      int count = 1; // delete 1 character (should delete whole emoji)
      int end = insertx;
      for (int i = 0; i < count && end < line.length(); i++) {
         int cp = Character.codePointAt(line, end);
         end += Character.charCount(cp);
      }
      String result = line.substring(0, insertx)
         + line.substring(end);
      assertEquals("abcd", result);
   }

   @Test
   void backwardDeleteEmoji() {
      // Simulate deleteChars backward logic
      String line = "ab" + GRINNING + "cd";
      int insertx = 4; // after the emoji (2 + 2 chars)
      int count = 1;
      int start = insertx;
      for (int i = 0; i < count && start > 0; i++) {
         int cp = Character.codePointBefore(line, start);
         start -= Character.charCount(cp);
      }
      String result = line.substring(0, start)
         + line.substring(insertx);
      assertEquals("abcd", result);
   }

   @Test
   void forwardDeleteMultipleEmoji() {
      String line = GRINNING + WAVE + "text";
      int insertx = 0;
      int count = 2; // delete 2 codepoints
      int end = insertx;
      for (int i = 0; i < count && end < line.length(); i++) {
         int cp = Character.codePointAt(line, end);
         end += Character.charCount(cp);
      }
      String result = line.substring(0, insertx)
         + line.substring(end);
      assertEquals("text", result);
   }

   @Test
   void cursorxForwardOverEmoji() {
      // Simulate cursorx(+1) logic
      String line = "a" + GRINNING + "b";
      int pos = 1; // at start of emoji
      int x = 1; // move 1 codepoint right
      for (int i = 0; i < x && pos < line.length(); i++) {
         int cp = Character.codePointAt(line, pos);
         pos += Character.charCount(cp);
      }
      assertEquals(3, pos); // should skip past both surrogate chars
   }

   @Test
   void cursorxBackwardOverEmoji() {
      String line = "a" + GRINNING + "b";
      int pos = 3; // after emoji, at 'b'
      int x = 1; // move 1 codepoint left
      for (int i = 0; i < x && pos > 0; i++) {
         int cp = Character.codePointBefore(line, pos);
         pos -= Character.charCount(cp);
      }
      assertEquals(1, pos); // should jump back to before emoji
   }

   @Test
   void cursorxabsAvoidsLowSurrogate() {
      // Simulate cursorxabs: if landing on low surrogate, back up
      String line = "a" + GRINNING + "b";
      int x = 2; // middle of surrogate pair
      if (x > 0 && x < line.length()
            && Character.isLowSurrogate(line.charAt(x)))
         x--;
      assertEquals(1, x); // backed up to start of emoji
   }

   @Test
   void cursorxabsOnHighSurrogateStays() {
      String line = "a" + GRINNING + "b";
      int x = 1; // high surrogate position
      if (x > 0 && x < line.length()
            && Character.isLowSurrogate(line.charAt(x)))
         x--;
      assertEquals(1, x); // stays — char at 1 is high surrogate
   }

   @Test
   void mixedBmpAndSurrogateDelete() {
      // Mix of BMP emoji (❤) and supplementary (😀)
      String line = HEART + GRINNING + "x";
      int insertx = 0;
      int count = 1;
      int end = insertx;
      for (int i = 0; i < count && end < line.length(); i++) {
         int cp = Character.codePointAt(line, end);
         end += Character.charCount(cp);
      }
      String result = line.substring(0, insertx)
         + line.substring(end);
      // Should delete just ❤ (1 char), leaving 😀x
      assertEquals(GRINNING + "x", result);
   }

   @Test
   void deleteAsciiNearEmoji() {
      String line = "a" + GRINNING + "b";
      // Delete 'a' (forward from pos 0)
      int end = 0;
      for (int i = 0; i < 1 && end < line.length(); i++) {
         int cp = Character.codePointAt(line, end);
         end += Character.charCount(cp);
      }
      String result = line.substring(end);
      assertEquals(GRINNING + "b", result);
   }

   @Test
   void highSurrogateIdentification() {
      assertTrue(Character.isHighSurrogate(GRINNING.charAt(0)));
      assertTrue(Character.isLowSurrogate(GRINNING.charAt(1)));
      assertFalse(Character.isHighSurrogate(HEART.charAt(0)));
      assertFalse(Character.isSurrogate(HEART.charAt(0)));
   }

   @Test
   void displayWidthAscii() {
      assertEquals(5, FvContext.displayWidth("hello", 0, 5));
   }

   @Test
   void displayWidthEmoji() {
      // Supplementary code point emoji = 2 columns
      assertEquals(2, FvContext.displayWidth(GRINNING, 0,
         GRINNING.length()));
   }

   @Test
   void displayWidthMixed() {
      String line = "ab" + GRINNING + "cd";
      // 2 + 2 + 2 = 6 columns
      assertEquals(6, FvContext.displayWidth(line, 0,
         line.length()));
   }

   @Test
   void displayWidthCJK() {
      // CJK character U+4E2D (中) = 2 columns
      String cjk = "\u4E2D";
      assertEquals(2, FvContext.displayWidth(cjk, 0,
         cjk.length()));
   }

   @Test
   void displayWidthBmpHeart() {
      // BMP heart U+2764 is NOT a wide character
      assertEquals(1, FvContext.displayWidth(HEART, 0,
         HEART.length()));
   }

   @Test
   void isWideCodePointEmoji() {
      int cp = Character.codePointAt(GRINNING, 0);
      assertTrue(FvContext.isWideCodePoint(cp));
   }

   @Test
   void isWideCodePointAscii() {
      assertFalse(FvContext.isWideCodePoint('a'));
   }

   // --- Combined emoji / grapheme cluster tests ---

   @Test
   void familyEmojiCharLength() {
      // ZWJ family: 3 emoji + 2 ZWJ = 8 chars (surrogates)
      assertEquals(8, FAMILY.length());
   }

   @Test
   void displayWidthFamily() {
      // Family ZWJ sequence should be 2 columns (one cluster)
      assertEquals(2, FvContext.displayWidth(FAMILY, 0,
         FAMILY.length()));
   }

   @Test
   void displayWidthFlagSequence() {
      // Flag sequence should be 2 columns (one cluster)
      assertEquals(2, FvContext.displayWidth(FLAG_SE, 0,
         FLAG_SE.length()));
   }

   @Test
   void displayWidthSkinTone() {
      // Wave + skin tone = one cluster, 2 columns
      assertEquals(2, FvContext.displayWidth(WAVE_SKIN, 0,
         WAVE_SKIN.length()));
   }

   @Test
   void displayWidthMixedCombined() {
      // "a" + family + "b" = 1 + 2 + 1 = 4 columns
      String line = "a" + FAMILY + "b";
      assertEquals(4, FvContext.displayWidth(line, 0,
         line.length()));
   }

   @Test
   void displayWidthMultipleCombined() {
      // flag + family = 2 + 2 = 4 columns
      String line = FLAG_SE + FAMILY;
      assertEquals(4, FvContext.displayWidth(line, 0,
         line.length()));
   }

   // --- Grapheme boundary tests (charOffset positioning) ---

   @Test
   void graphemeBoundaryAscii() {
      // Every position in an ASCII string is a boundary
      String line = "hello";
      java.text.BreakIterator bi =
         java.text.BreakIterator.getCharacterInstance();
      bi.setText(line);
      for (int i = 0; i <= line.length(); i++)
         assertTrue(bi.isBoundary(i),
            "position " + i + " should be a boundary");
   }

   @Test
   void graphemeBoundaryEmoji() {
      // Surrogate pair: boundaries at 0 and 2, not at 1
      java.text.BreakIterator bi =
         java.text.BreakIterator.getCharacterInstance();
      bi.setText(GRINNING);
      assertTrue(bi.isBoundary(0));
      assertFalse(bi.isBoundary(1),
         "mid-surrogate should not be a boundary");
      assertTrue(bi.isBoundary(2));
   }

   @Test
   void graphemeBoundaryFamily() {
      // Family ZWJ: only 0 and 8 are boundaries
      java.text.BreakIterator bi =
         java.text.BreakIterator.getCharacterInstance();
      bi.setText(FAMILY);
      assertTrue(bi.isBoundary(0));
      for (int i = 1; i < FAMILY.length(); i++)
         assertFalse(bi.isBoundary(i),
            "position " + i + " inside family should not be"
            + " a boundary");
      assertTrue(bi.isBoundary(FAMILY.length()));
   }

   @Test
   void graphemeBoundaryMixed() {
      // "ab" + family + "cd": boundaries at 0,1,2,10,11,12
      String line = "ab" + FAMILY + "cd";
      java.text.BreakIterator bi =
         java.text.BreakIterator.getCharacterInstance();
      bi.setText(line);
      assertTrue(bi.isBoundary(0));
      assertTrue(bi.isBoundary(1));
      assertTrue(bi.isBoundary(2));  // before family
      // Inside family (positions 3..9) — not boundaries
      for (int i = 3; i < 2 + FAMILY.length(); i++)
         assertFalse(bi.isBoundary(i),
            "position " + i + " inside family");
      assertTrue(bi.isBoundary(2 + FAMILY.length())); // 10
      assertTrue(bi.isBoundary(2 + FAMILY.length() + 1)); // c
      assertTrue(bi.isBoundary(line.length()));
   }

   @Test
   void graphemeSnapPrecedingMidEmoji() {
      // Snapping from mid-surrogate should go to start
      String line = "a" + GRINNING + "b";
      java.text.BreakIterator bi =
         java.text.BreakIterator.getCharacterInstance();
      bi.setText(line);
      // Position 2 is the low surrogate — snap back to 1
      assertFalse(bi.isBoundary(2));
      assertEquals(1, bi.preceding(2));
   }

   @Test
   void graphemeSnapPrecedingMidFamily() {
      // Snapping from mid-ZWJ-sequence should go to start
      String line = "a" + FAMILY + "b";
      java.text.BreakIterator bi =
         java.text.BreakIterator.getCharacterInstance();
      bi.setText(line);
      // Position 5 is inside family — snap back to 1
      assertFalse(bi.isBoundary(5));
      assertEquals(1, bi.preceding(5));
   }

   @Test
   void graphemeFollowingSkipsCluster() {
      // following() from start of emoji lands after the
      // whole cluster, not at the next Java char
      String line = "a" + FAMILY + "b";
      java.text.BreakIterator bi =
         java.text.BreakIterator.getCharacterInstance();
      bi.setText(line);
      // following(1) should skip entire family to position 9
      assertEquals(1 + FAMILY.length(), bi.following(1));
   }

   // --- cursor2abs grapheme snapping tests ---

   /**
    * Simulates the grapheme-snapping logic in cursor2abs.
    * cursor2abs snaps fileposx to a grapheme cluster boundary
    * if it lands in the middle of a surrogate pair or
    * combining sequence.
    */
   private static int snapToGraphemeBoundary(String line, int pos) {
      if (pos <= 0 || pos >= line.length())
         return pos;
      java.text.BreakIterator bi =
         java.text.BreakIterator.getCharacterInstance();
      bi.setText(line);
      if (!bi.isBoundary(pos))
         return bi.preceding(pos);
      return pos;
   }

   @Test
   void snapMidSurrogatePair() {
      // Position 2 is the low surrogate of emoji — snap to 1
      String line = "a" + GRINNING + "b";
      assertEquals(1, snapToGraphemeBoundary(line, 2));
   }

   @Test
   void snapOnBoundaryNoChange() {
      // Position 1 is start of emoji — already a boundary
      String line = "a" + GRINNING + "b";
      assertEquals(1, snapToGraphemeBoundary(line, 1));
   }

   @Test
   void snapAfterEmoji() {
      // Position 3 is after emoji, on 'b' — already a boundary
      String line = "a" + GRINNING + "b";
      assertEquals(3, snapToGraphemeBoundary(line, 3));
   }

   @Test
   void snapAtZero() {
      // Position 0 should not snap (boundary condition)
      String line = GRINNING + "b";
      assertEquals(0, snapToGraphemeBoundary(line, 0));
   }

   @Test
   void snapAtEnd() {
      // Position at line.length() should not snap
      String line = "a" + GRINNING;
      assertEquals(3, snapToGraphemeBoundary(line, 3));
   }

   @Test
   void snapMidFamilyEmoji() {
      // Position 5 is inside ZWJ family — snap to 0
      assertEquals(0, snapToGraphemeBoundary(FAMILY, 5));
   }

   @Test
   void snapMidFlagSequence() {
      // Position 2 is inside flag sequence — snap to 0
      assertEquals(0, snapToGraphemeBoundary(FLAG_SE, 2));
   }

   @Test
   void snapMidSkinTone() {
      // Position 2 is between wave and skin tone modifier — snap to 0
      assertEquals(0, snapToGraphemeBoundary(WAVE_SKIN, 2));
   }

   // --- findchar absolute positioning tests ---

   @Test
   void findcharPositionAfterEmoji() {
      // Simulates findcharf: searching for 'b' in "a😀b"
      // The char 'b' is at Java index 3
      String line = "a" + GRINNING + "b";
      int target = -1;
      for (int i = 1; i < line.length(); i++) {
         if (line.charAt(i) == 'b') {
            target = i;
            break;
         }
      }
      assertEquals(3, target);
      // With cursorxabs(3), snapping: position 3 is a boundary
      assertEquals(3, snapToGraphemeBoundary(line, target));
   }

   @Test
   void findcharSkipsSurrogateHalves() {
      // Searching for a high surrogate char won't match ASCII
      String line = "a" + GRINNING + "b";
      // Searching for 'a' from position 1 should not match
      // the emoji's surrogate halves
      boolean foundInSurrogate = false;
      for (int i = 1; i < 3; i++) {
         if (line.charAt(i) == 'a')
            foundInSurrogate = true;
      }
      assertFalse(foundInSurrogate,
         "Surrogate halves should not match 'a'");
   }

   @Test
   void findchartPositionBeforeTarget() {
      // Simulates findchart: 't' command stops one before match
      // For "a😀bc", searching for 'c': match at 4, position at 3
      String line = "a" + GRINNING + "bc";
      int target = -1;
      for (int i = 2; i < line.length(); i++) {
         if (line.charAt(i) == 'c') {
            target = i;
            break;
         }
      }
      assertEquals(4, target);
      // t command positions at target - 1 = 3, which is a boundary
      int tPos = target - 1;
      assertEquals(3, snapToGraphemeBoundary(line, tPos));
   }

   @Test
   void deleteBackwardEmojiPosition() {
      // After backward delete of emoji, cursor should be at
      // the start position (where the emoji was)
      String line = "ab" + GRINNING + "cd";
      int insertx = 4; // after emoji
      java.text.BreakIterator bi =
         java.text.BreakIterator.getCharacterInstance();
      bi.setText(line);
      int start = insertx;
      int prev = bi.preceding(start);
      assertNotEquals(java.text.BreakIterator.DONE, prev);
      start = prev;
      assertEquals(2, start); // start of emoji
      // After deletion, line becomes "abcd"
      String newLine = line.substring(0, start)
         + line.substring(insertx);
      assertEquals("abcd", newLine);
      // Cursor should be at position 2 (absolute), not
      // calculated via cursorx(-charCount)
      assertEquals(2, start);
      assertEquals('c', newLine.charAt(start));
   }
}
