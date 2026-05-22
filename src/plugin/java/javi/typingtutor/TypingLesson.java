package javi.typingtutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates typing lessons using spaced repetition.
 *
 * <p>Words are selected based on a hybrid weight formula combining:
 * word error history, per-key difficulty (from KeyStats), and
 * recency. The lesson mode determines which word bank is used.</p>
 */
final class TypingLesson {

   private static final int WORDS_PER_LINE = 8;
   private static final double KEY_DIFFICULTY_FACTOR = 2.0;

   private final TypingStats stats;
   private final KeyStats keyStats;
   private final CommandTracker commandTracker;
   private final LessonMode mode;
   private final Random random = new Random();
   private final AdaptiveDifficulty adaptive;

   TypingLesson(TypingStats stats, KeyStats keyStats,
         CommandTracker commandTracker, LessonMode mode) {
      this.stats = stats;
      this.keyStats = keyStats;
      this.commandTracker = commandTracker;
      this.mode = mode;
      this.adaptive = new AdaptiveDifficulty(random);
   }

   /**
    * Generate lesson lines using weighted random selection.
    *
    * @param lineCount number of lines to generate
    * @return list of lesson lines
    */
   List<String> generateLesson(int lineCount) {
      // HOMEROW mode uses progressive letter introduction
      if (mode == LessonMode.HOMEROW) {
         ProgressiveLesson progressive = new ProgressiveLesson(
            keyStats);
         return progressive.generateLesson(lineCount);
      }

      String[] words = WordBank.wordsForMode(mode);
      double[] weights = new double[words.length];
      double totalWeight = 0;

      for (int i = 0; i < words.length; i++) {
         weights[i] = computeWeight(words[i]);
         totalWeight += weights[i];
      }

      int level = adaptive.computeLevel(keyStats);
      List<String> lines = new ArrayList<>(lineCount);
      for (int line = 0; line < lineCount; line++) {
         StringBuilder sb = new StringBuilder();
         for (int w = 0; w < WORDS_PER_LINE; w++) {
            if (w > 0)
               sb.append(' ');
            // At level 4, occasionally inject number words
            String numWord = adaptive.maybeNumberWord(level);
            if (numWord != null) {
               sb.append(numWord);
            } else {
               String picked = pickWord(words, weights, totalWeight);
               sb.append(adaptive.transform(picked, level));
            }
         }
         lines.add(sb.toString());
      }
      return lines;
   }

   /**
    * Get the current adaptive difficulty level (0–4).
    */
   int difficultyLevel() {
      return adaptive.computeLevel(keyStats);
   }

   /**
    * Get the user's measured average CPM.
    */
   double measuredCpm() {
      return adaptive.averageCpm(keyStats);
   }

   /**
    * Compute the selection weight for a word.
    * Combines word-level error/recency stats with per-key difficulty.
    * In EDITOR mode, also factors in command usage frequency.
    */
   private double computeWeight(String word) {
      double wordWeight = stats.getWeight(word);
      double keyDifficulty = keyStats.wordDifficulty(word);
      double base = wordWeight * (1.0 + (keyDifficulty - 1.0)
         * KEY_DIFFICULTY_FACTOR);
      if (mode == LessonMode.EDITOR && commandTracker != null)
         base *= commandTracker.commandWeight(word);
      return base;
   }

   private String pickWord(String[] words, double[] weights,
         double totalWeight) {
      double r = random.nextDouble() * totalWeight;
      double cumulative = 0;
      for (int i = 0; i < words.length; i++) {
         cumulative += weights[i];
         if (r <= cumulative)
            return words[i];
      }
      return words[words.length - 1];
   }
}
