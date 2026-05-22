package javi.typingtutor;

import java.util.Random;

/**
 * Automatically adjusts lesson difficulty based on measured typing speed.
 *
 * <p>Rather than requiring manual configuration, this class computes
 * the user's average CPM from KeyStats and introduces progressively
 * harder content as speed increases:</p>
 *
 * <ul>
 *   <li>Level 0 (under 100 CPM): lowercase words only</li>
 *   <li>Level 1 (100–149 CPM): occasional capitalized words</li>
 *   <li>Level 2 (150–199 CPM): mixed case + basic punctuation
 *       (periods, commas)</li>
 *   <li>Level 3 (200–249 CPM): more punctuation (semicolons,
 *       colons, quotes)</li>
 *   <li>Level 4 (250+ CPM): full punctuation + numbers</li>
 * </ul>
 */
final class AdaptiveDifficulty {

   private static final int LEVEL_1_CPM = 100;
   private static final int LEVEL_2_CPM = 150;
   private static final int LEVEL_3_CPM = 200;
   private static final int LEVEL_4_CPM = 250;

   /** Probability of capitalizing a word at level 1. */
   private static final double CAPITALIZE_PROB_L1 = 0.15;
   /** Probability of capitalizing a word at levels 2+. */
   private static final double CAPITALIZE_PROB_L2 = 0.25;

   /** Probability of appending punctuation at level 2. */
   private static final double PUNCT_PROB_L2 = 0.20;
   /** Probability of appending punctuation at level 3+. */
   private static final double PUNCT_PROB_L3 = 0.30;

   /** Basic punctuation (level 2). */
   private static final char[] BASIC_PUNCT = {'.', ',', '!', '?'};
   /** Extended punctuation (level 3+). */
   private static final char[] EXTENDED_PUNCT =
      {'.', ',', '!', '?', ';', ':', '"', '\''};
   /** Number strings to inject at level 4. */
   private static final String[] NUMBER_WORDS =
      {"42", "100", "256", "1024", "3.14", "7", "99", "128"};

   /** Probability of injecting a number word at level 4. */
   private static final double NUMBER_PROB = 0.10;

   private final Random random;

   AdaptiveDifficulty(Random random) {
      this.random = random;
   }

   /**
    * Compute the current difficulty level from the user's average CPM.
    *
    * @param keyStats the per-key statistics (used for average speed)
    * @return difficulty level 0–4
    */
   int computeLevel(KeyStats keyStats) {
      double avgCpm = averageCpm(keyStats);
      if (avgCpm >= LEVEL_4_CPM) return 4;
      if (avgCpm >= LEVEL_3_CPM) return 3;
      if (avgCpm >= LEVEL_2_CPM) return 2;
      if (avgCpm >= LEVEL_1_CPM) return 1;
      return 0;
   }

   /**
    * Compute the user's overall average CPM from key statistics.
    *
    * <p>Uses only keys with at least 3 samples to avoid
    * skewed results from a single measurement.</p>
    */
   double averageCpm(KeyStats keyStats) {
      double totalAvgMs = 0;
      int counted = 0;
      for (char ch = 'a'; ch <= 'z'; ch++) {
         KeyStats.KeyStat stat = keyStats.getStat(ch);
         if (stat != null && stat.samples >= 3) {
            totalAvgMs += stat.avgTimeMs();
            counted++;
         }
      }
      if (counted == 0 || totalAvgMs == 0)
         return 0;
      double overallAvgMs = totalAvgMs / counted;
      return 60000.0 / overallAvgMs;
   }

   /**
    * Apply adaptive transformations to a word based on difficulty level.
    *
    * <p>May capitalize, add punctuation, or leave unchanged depending
    * on the current level and random chance.</p>
    *
    * @param word the base word (lowercase)
    * @param level current difficulty level (0–4)
    * @return the possibly-transformed word
    */
   String transform(String word, int level) {
      if (level <= 0)
         return word;

      String result = word;

      // Capitalize first letter
      double capProb = level >= 2 ? CAPITALIZE_PROB_L2
         : CAPITALIZE_PROB_L1;
      if (random.nextDouble() < capProb)
         result = capitalize(result);

      // Append punctuation
      if (level >= 3 && random.nextDouble() < PUNCT_PROB_L3) {
         result = result + EXTENDED_PUNCT[
            random.nextInt(EXTENDED_PUNCT.length)];
      } else if (level >= 2 && random.nextDouble() < PUNCT_PROB_L2) {
         result = result + BASIC_PUNCT[
            random.nextInt(BASIC_PUNCT.length)];
      }

      return result;
   }

   /**
    * Decide whether to inject a number word at level 4.
    *
    * @param level current difficulty level
    * @return a number string to use, or null if no injection
    */
   String maybeNumberWord(int level) {
      if (level >= 4 && random.nextDouble() < NUMBER_PROB)
         return NUMBER_WORDS[random.nextInt(NUMBER_WORDS.length)];
      return null;
   }

   /**
    * Get a human-readable description of the current level.
    */
   static String levelDescription(int level) {
      return switch (level) {
         case 0 -> "Beginner (lowercase)";
         case 1 -> "Novice (+ capitals)";
         case 2 -> "Intermediate (+ basic punctuation)";
         case 3 -> "Advanced (+ full punctuation)";
         case 4 -> "Expert (+ numbers)";
         default -> "Unknown";
      };
   }

   private static String capitalize(String word) {
      if (word.isEmpty())
         return word;
      return Character.toUpperCase(word.charAt(0))
         + word.substring(1);
   }
}
