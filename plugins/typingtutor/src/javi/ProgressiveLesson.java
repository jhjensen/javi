package javi.typingtutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Implements keybr-style progressive letter introduction.
 *
 * <p>Letters are unlocked one at a time once the current set
 * reaches a confidence threshold. The weakest key in the unlocked
 * set becomes the "focused" key, guaranteed to appear in every
 * generated word.</p>
 *
 * <p>The progression starts with home row keys and expands
 * outward through the keyboard layout in order of letter
 * frequency.</p>
 */
final class ProgressiveLesson {

   /**
    * Letter introduction order, roughly by frequency
    * within common English words and keyboard position.
    * Starts with home row, then top row common letters,
    * then bottom row and remaining.
    */
   static final String LETTER_ORDER =
      "asdfghjkl"   // home row
      + "eritnuo"   // top row common (by frequency)
      + "mcvbw"     // bottom row common
      + "ypqxz";    // rare letters

   /** Minimum confidence threshold to unlock the next letter. */
   static final double UNLOCK_THRESHOLD = 1.0;

   /** Minimum samples per key before it counts as mastered. */
   static final int MIN_SAMPLES = 5;

   /** Initial set size (letters unlocked from the start). */
   static final int INITIAL_UNLOCK = 4;

   private static final int WORDS_PER_LINE = 6;
   private static final int MIN_WORD_LENGTH = 2;
   private static final int MAX_WORD_LENGTH = 7;

   private final KeyStats keyStats;
   private final Random random = new Random();

   ProgressiveLesson(KeyStats keyStats) {
      this.keyStats = keyStats;
   }

   /**
    * Determine how many letters are currently unlocked.
    *
    * <p>The first {@link #INITIAL_UNLOCK} letters are always
    * available. Additional letters unlock once all currently-
    * unlocked letters have confidence &gt;= threshold with at
    * least MIN_SAMPLES each.</p>
    */
   int unlockedCount() {
      int count = INITIAL_UNLOCK;
      while (count < LETTER_ORDER.length()) {
         // Check if all letters up to 'count' are mastered
         String current = LETTER_ORDER.substring(0, count);
         if (!isMastered(current))
            break;
         count++;
      }
      return Math.min(count, LETTER_ORDER.length());
   }

   /**
    * Get the string of currently unlocked letters.
    */
   String unlockedLetters() {
      return LETTER_ORDER.substring(0, unlockedCount());
   }

   /**
    * Check whether all characters in the set are mastered
    * (have enough samples and meet confidence threshold).
    */
   private boolean isMastered(String chars) {
      for (int i = 0; i < chars.length(); i++) {
         char ch = chars.charAt(i);
         KeyStats.KeyStat stat = keyStats.getStat(ch);
         if (stat == null || stat.samples < MIN_SAMPLES)
            return false;
         double targetTimeMs = 60000.0 / keyStats.targetCpm();
         if (stat.confidence(targetTimeMs) < UNLOCK_THRESHOLD)
            return false;
      }
      return true;
   }

   /**
    * Get the focused key (weakest among unlocked letters).
    */
   char focusedKey() {
      return keyStats.weakestKey(unlockedLetters());
   }

   /**
    * Generate a progressive lesson.
    *
    * <p>Uses only words whose characters are all within the
    * unlocked set. The focused (weakest) key is guaranteed
    * to appear in at least one word per line.</p>
    *
    * @param lineCount number of lines to generate
    * @return list of lesson lines
    */
   List<String> generateLesson(int lineCount) {
      String unlocked = unlockedLetters();
      char focused = focusedKey();

      // Gather real words that use only unlocked letters
      List<String> available = WordBank.wordsForChars(unlocked);
      List<String> focusedWords = new ArrayList<>();
      for (String w : available) {
         if (w.indexOf(focused) >= 0)
            focusedWords.add(w);
      }

      // If not enough real words, generate pseudo-words
      if (available.size() < 5)
         addPseudoWords(available, unlocked, 15);
      if (focusedWords.isEmpty())
         addFocusedPseudoWords(focusedWords, unlocked, focused, 5);

      List<String> lines = new ArrayList<>(lineCount);
      for (int line = 0; line < lineCount; line++) {
         StringBuilder sb = new StringBuilder();
         boolean hasFocused = false;
         for (int w = 0; w < WORDS_PER_LINE; w++) {
            if (w > 0)
               sb.append(' ');
            // Ensure at least one word per line contains focused key
            if (w == WORDS_PER_LINE - 1 && !hasFocused
                  && !focusedWords.isEmpty()) {
               String word = focusedWords.get(
                  random.nextInt(focusedWords.size()));
               sb.append(word);
               hasFocused = true;
            } else {
               String word = available.get(
                  random.nextInt(available.size()));
               sb.append(word);
               if (word.indexOf(focused) >= 0)
                  hasFocused = true;
            }
         }
         lines.add(sb.toString());
      }
      return lines;
   }

   /**
    * Generate pseudo-words using only the given characters.
    */
   private void addPseudoWords(List<String> dest, String chars,
         int count) {
      String vowels = filterVowels(chars);
      String consonants = filterConsonants(chars);
      // If no vowels available, just use all chars
      if (vowels.isEmpty())
         vowels = chars;
      if (consonants.isEmpty())
         consonants = chars;

      for (int i = 0; i < count; i++) {
         int len = MIN_WORD_LENGTH
            + random.nextInt(MAX_WORD_LENGTH - MIN_WORD_LENGTH + 1);
         StringBuilder word = new StringBuilder(len);
         for (int c = 0; c < len; c++) {
            // Alternate consonant-vowel for pronounceability
            if (c % 2 == 0)
               word.append(consonants.charAt(
                  random.nextInt(consonants.length())));
            else
               word.append(vowels.charAt(
                  random.nextInt(vowels.length())));
         }
         dest.add(word.toString());
      }
   }

   /**
    * Generate pseudo-words that contain the focused character.
    */
   private void addFocusedPseudoWords(List<String> dest, String chars,
         char focused, int count) {
      for (int i = 0; i < count; i++) {
         int len = MIN_WORD_LENGTH
            + random.nextInt(MAX_WORD_LENGTH - MIN_WORD_LENGTH + 1);
         StringBuilder word = new StringBuilder(len);
         int focusPos = random.nextInt(len);
         String vowels = filterVowels(chars);
         String consonants = filterConsonants(chars);
         if (vowels.isEmpty())
            vowels = chars;
         if (consonants.isEmpty())
            consonants = chars;

         for (int c = 0; c < len; c++) {
            if (c == focusPos) {
               word.append(focused);
            } else if (c % 2 == 0) {
               word.append(consonants.charAt(
                  random.nextInt(consonants.length())));
            } else {
               word.append(vowels.charAt(
                  random.nextInt(vowels.length())));
            }
         }
         dest.add(word.toString());
      }
   }

   private static String filterVowels(String chars) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < chars.length(); i++) {
         char ch = chars.charAt(i);
         if ("aeiou".indexOf(ch) >= 0)
            sb.append(ch);
      }
      return sb.toString();
   }

   private static String filterConsonants(String chars) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < chars.length(); i++) {
         char ch = chars.charAt(i);
         if ("aeiou".indexOf(ch) < 0)
            sb.append(ch);
      }
      return sb.toString();
   }

   /**
    * Format a progress report showing unlocked letters and stats.
    */
   String formatProgress() {
      int unlocked = unlockedCount();
      String unlockedStr = unlockedLetters();
      char focused = focusedKey();
      double targetTimeMs = 60000.0 / keyStats.targetCpm();

      StringBuilder sb = new StringBuilder();
      sb.append("=== PROGRESSIVE TYPING PROGRESS ===\n\n");
      sb.append(String.format("Target: %d CPM (%.0f ms/char)\n",
         keyStats.targetCpm(), targetTimeMs));
      sb.append(String.format("Unlocked: %d/%d letters\n",
         unlocked, LETTER_ORDER.length()));
      sb.append(String.format("Focused key: '%c' (weakest)\n\n",
         focused));

      // Show unlocked letters with confidence
      sb.append("UNLOCKED:\n");
      sb.append(String.format("  %-6s %8s %8s %8s\n",
         "Key", "Samples", "AvgMs", "Conf"));
      sb.append("  ").append("-".repeat(36)).append("\n");
      for (int i = 0; i < unlocked; i++) {
         char ch = LETTER_ORDER.charAt(i);
         KeyStats.KeyStat stat = keyStats.getStat(ch);
         if (stat != null && stat.samples > 0) {
            sb.append(String.format("  %-6c %8d %8.0f %7.2f%s\n",
               ch, stat.samples, stat.avgTimeMs(),
               stat.confidence(targetTimeMs),
               ch == focused ? " <--" : ""));
         } else {
            sb.append(String.format("  %-6c %8s %8s %7s%s\n",
               ch, "-", "-", "-",
               ch == focused ? " <--" : ""));
         }
      }

      // Show next letters to unlock
      if (unlocked < LETTER_ORDER.length()) {
         sb.append("\nNEXT TO UNLOCK:\n");
         int show = Math.min(5, LETTER_ORDER.length() - unlocked);
         for (int i = unlocked; i < unlocked + show; i++) {
            sb.append(String.format("  '%c'\n",
               LETTER_ORDER.charAt(i)));
         }
         sb.append("\nMaster all unlocked keys (conf >= ")
            .append(String.format("%.1f", UNLOCK_THRESHOLD))
            .append(", min ").append(MIN_SAMPLES)
            .append(" samples) to unlock the next letter.\n");
      } else {
         sb.append("\nAll letters unlocked! Full keyboard mastered.\n");
      }

      return sb.toString();
   }
}
