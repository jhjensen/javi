package javi.typingtutor;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static history.Tools.trace;

/**
 * Tracks per-word typing statistics with spaced repetition weights.
 *
 * <p>Stats are persisted to {@code ~/.javi/typing-stats.json}
 * as simple key=value lines (no external JSON dependency).</p>
 */
final class TypingStats {

   private static final String STATS_DIR = ".javi";
   private static final String STATS_FILE = "typing-stats.dat";
   private static final double BASE_WEIGHT = 1.0;
   private static final double ERROR_BOOST = 3.0;

   private final Map<String, WordStat> wordStats = new HashMap<>();
   private int totalSessions;
   private int totalWords;

   private static final class WordStat {
      int attempts;
      int errors;
      long totalTimeMs;
      long lastSeen; // epoch millis
      double easiness = 2.5; // SM-2 easiness factor

      double errorRate() {
         return attempts > 0 ? (double) errors / attempts : 0;
      }

      double avgTimeMs() {
         return attempts > 0 ? (double) totalTimeMs / attempts : 0;
      }
   }

   /**
    * Get the spaced repetition weight for a word.
    * Higher weight = more likely to appear in lessons.
    * Incorporates error rate, recency, and SM-2 easiness.
    */
   double getWeight(String word) {
      WordStat ws = wordStats.get(word);
      if (ws == null)
         return BASE_WEIGHT;

      // Error factor: words with more errors get higher weight
      double errorFactor = 1.0 + ws.errorRate() * ERROR_BOOST;

      // Recency factor: words not seen recently get a boost
      long ageSec = (System.currentTimeMillis() - ws.lastSeen) / 1000;
      double recencyFactor = 1.0 + Math.min(ageSec / 3600.0, 2.0);

      // Easiness factor: lower easiness = harder word = higher weight
      // easiness ranges from 1.3 to 2.5; invert so hard words score higher
      double easinessFactor = 2.5 / ws.easiness;

      return BASE_WEIGHT * errorFactor * recencyFactor * easinessFactor;
   }

   /**
    * Record the result of typing a word in a lesson.
    * Adjusts the SM-2 easiness factor based on accuracy.
    *
    * @param word the word that was typed
    * @param correct whether it was typed correctly
    * @param timeMs time taken (approximate, per-line average)
    */
   void recordWord(String word, boolean correct, long timeMs) {
      WordStat ws = wordStats.computeIfAbsent(word,
         k -> new WordStat());
      ws.attempts++;
      if (!correct)
         ws.errors++;
      ws.totalTimeMs += timeMs;
      ws.lastSeen = System.currentTimeMillis();

      // SM-2 easiness adjustment: grade 0-5 based on correctness
      int grade = correct ? 4 : 1;
      ws.easiness += 0.1 - (5 - grade) * (0.08 + (5 - grade) * 0.02);
      if (ws.easiness < 1.3)
         ws.easiness = 1.3;

      totalWords++;
   }

   void recordSession() {
      totalSessions++;
   }

   /**
    * Clear all word statistics and reset counters.
    */
   void reset() {
      wordStats.clear();
      totalSessions = 0;
      totalWords = 0;
   }

   /**
    * Format a human-readable stats report.
    */
   String formatReport() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== TYPING PRACTICE STATISTICS ===\n\n");
      sb.append(String.format("Total sessions: %d\n", totalSessions));
      sb.append(String.format("Total words practiced: %d\n", totalWords));
      sb.append(String.format("Unique words: %d\n\n", wordStats.size()));

      if (wordStats.isEmpty()) {
         sb.append("No practice data yet. Run :typingpractice\n");
         return sb.toString();
      }

      // Sort by error rate descending (worst words first)
      TreeMap<String, WordStat> sorted = new TreeMap<>((a, b) -> {
         double ea = wordStats.get(a).errorRate();
         double eb = wordStats.get(b).errorRate();
         int cmp = Double.compare(eb, ea);
         return cmp != 0 ? cmp : a.compareTo(b);
      });
      sorted.putAll(wordStats);

      sb.append(String.format("%-15s %8s %8s %8s %8s\n",
         "Word", "Attempts", "Errors", "ErrRate", "AvgMs"));
      sb.append("-".repeat(55)).append("\n");

      int shown = 0;
      for (Map.Entry<String, WordStat> entry : sorted.entrySet()) {
         if (shown++ >= 30)
            break;
         WordStat ws = entry.getValue();
         sb.append(String.format("%-15s %8d %8d %7.1f%% %8.0f\n",
            entry.getKey(), ws.attempts, ws.errors,
            ws.errorRate() * 100, ws.avgTimeMs()));
      }
      return sb.toString();
   }

   /**
    * Load stats from disk.
    */
   static TypingStats load() {
      TypingStats ts = new TypingStats();
      File f = statsFile();
      if (!f.exists())
         return ts;
      try (BufferedReader br = new BufferedReader(
            new FileReader(f, StandardCharsets.UTF_8))) {
         String line;
         while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
               continue;
            String[] parts = line.split("\t");
            if (parts.length < 5)
               continue;
            if ("meta".equals(parts[0])) {
               ts.totalSessions = Integer.parseInt(parts[1]);
               ts.totalWords = Integer.parseInt(parts[2]);
            } else {
               WordStat ws = new WordStat();
               ws.attempts = Integer.parseInt(parts[1]);
               ws.errors = Integer.parseInt(parts[2]);
               ws.totalTimeMs = Long.parseLong(parts[3]);
               ws.lastSeen = Long.parseLong(parts[4]);
               if (parts.length >= 6)
                  ws.easiness = Double.parseDouble(parts[5]);
               ts.wordStats.put(parts[0], ws);
            }
         }
      } catch (IOException e) {
         trace("typing stats load failed: " + e);
      } catch (NumberFormatException e) {
         trace("typing stats parse error: " + e);
      }
      return ts;
   }

   /**
    * Save stats to disk.
    */
   void save() {
      recordSession();
      File f = statsFile();
      f.getParentFile().mkdirs();
      try (BufferedWriter bw = new BufferedWriter(
            new FileWriter(f, StandardCharsets.UTF_8))) {
         bw.write("# Javi typing practice stats\n");
         bw.write("meta\t" + totalSessions + "\t" + totalWords
            + "\t0\t0\n");
         for (Map.Entry<String, WordStat> entry
               : wordStats.entrySet()) {
            WordStat ws = entry.getValue();
            bw.write(entry.getKey() + "\t" + ws.attempts + "\t"
               + ws.errors + "\t" + ws.totalTimeMs + "\t"
               + ws.lastSeen + "\t" + ws.easiness + "\n");
         }
      } catch (IOException e) {
         trace("typing stats save failed: " + e);
      }
   }

   private static File statsFile() {
      return new File(System.getProperty("user.home"),
         STATS_DIR + File.separator + STATS_FILE);
   }
}
