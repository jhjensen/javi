package javi.typingtutor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static history.Tools.trace;

/**
 * Tracks per-key (per-character) typing statistics.
 *
 * <p>Each character the user types has its own timing and error
 * history. This enables the adaptive algorithm to identify weak
 * keys and weight words containing those keys more heavily.</p>
 *
 * <p>Stats are persisted to {@code ~/.javi/typing-keystats.dat}
 * as tab-separated values.</p>
 */
final class KeyStats {

   private static final String STATS_DIR = ".javi";
   private static final String STATS_FILE = "typing-keystats.dat";

   /** Default target speed: 175 characters per minute (~35 WPM). */
   static final int DEFAULT_TARGET_CPM = 175;
   /** Default number of lines per lesson. */
   static final int DEFAULT_LESSON_LINES = 5;

   private final Map<Character, KeyStat> keyMap = new HashMap<>();
   private int targetCpm = DEFAULT_TARGET_CPM;
   private int lessonLines = DEFAULT_LESSON_LINES;

   /**
    * Per-key statistics record.
    */
   static final class KeyStat {
      int samples;
      long totalTimeMs;
      long bestTimeMs = Long.MAX_VALUE;
      int errors;
      long lastSeen;

      double avgTimeMs() {
         return samples > 0 ? (double) totalTimeMs / samples : 0;
      }

      /**
       * Confidence relative to target time.
       * 1.0 = exactly at target speed, &gt;1.0 = faster, &lt;1.0 = slower.
       */
      double confidence(double targetTimeMs) {
         double avg = avgTimeMs();
         if (avg <= 0)
            return 1.0;
         return targetTimeMs / avg;
      }
   }

   /**
    * Record a keystroke timing sample.
    *
    * @param ch the character typed
    * @param timeMs time in millis between this keystroke and the previous one
    * @param correct whether the character was typed correctly
    */
   void record(char ch, long timeMs, boolean correct) {
      KeyStat ks = keyMap.computeIfAbsent(ch, k -> new KeyStat());
      if (timeMs > 0) {
         ks.samples++;
         ks.totalTimeMs += timeMs;
         if (timeMs < ks.bestTimeMs)
            ks.bestTimeMs = timeMs;
      }
      if (!correct)
         ks.errors++;
      ks.lastSeen = System.currentTimeMillis();
   }

   /**
    * Get the confidence for a character (1.0 = at target speed).
    */
   double confidence(char ch) {
      KeyStat ks = keyMap.get(ch);
      if (ks == null || ks.samples == 0)
         return 1.0; // no data = assume at target
      double targetTimeMs = 60000.0 / targetCpm;
      return ks.confidence(targetTimeMs);
   }

   /**
    * Get the inverse-confidence difficulty factor for a word.
    * Higher value = harder word (contains slower keys).
    */
   double wordDifficulty(String word) {
      if (word.isEmpty())
         return 1.0;
      double sum = 0;
      for (int i = 0; i < word.length(); i++) {
         double conf = confidence(word.charAt(i));
         sum += (conf > 0) ? 1.0 / conf : 2.0;
      }
      return sum / word.length();
   }

   /**
    * Check if all characters in the set have confidence at or above
    * the threshold.
    */
   boolean allAboveThreshold(String chars, double threshold) {
      for (int i = 0; i < chars.length(); i++) {
         if (confidence(chars.charAt(i)) < threshold)
            return false;
      }
      return true;
   }

   /**
    * Get the weakest key (lowest confidence) from a set.
    *
    * @param chars set of characters to check
    * @return the weakest character, or first char if no data
    */
   char weakestKey(String chars) {
      char weakest = chars.charAt(0);
      double weakestConf = Double.MAX_VALUE;
      for (int i = 0; i < chars.length(); i++) {
         double conf = confidence(chars.charAt(i));
         if (conf < weakestConf) {
            weakestConf = conf;
            weakest = chars.charAt(i);
         }
      }
      return weakest;
   }

   /**
    * Get the raw stat record for a character, or null if none.
    */
   KeyStat getStat(char ch) {
      return keyMap.get(ch);
   }

   int targetCpm() {
      return targetCpm;
   }

   void setTargetCpm(int cpm) {
      this.targetCpm = cpm;
   }

   int lessonLines() {
      return lessonLines;
   }

   void setLessonLines(int lines) {
      this.lessonLines = lines;
   }

   boolean hasData() {
      return !keyMap.isEmpty();
   }

   /**
    * Format a per-key stats report.
    */
   String formatReport() {
      if (keyMap.isEmpty())
         return "No per-key data yet.\n";

      double targetTimeMs = 60000.0 / targetCpm;
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("Target: %d CPM (%.0f ms/char)\n\n",
         targetCpm, targetTimeMs));
      sb.append(String.format("%-6s %8s %8s %8s %8s\n",
         "Key", "Samples", "AvgMs", "BestMs", "Conf"));
      sb.append("-".repeat(44)).append("\n");

      TreeMap<Character, KeyStat> sorted = new TreeMap<>(keyMap);
      for (Map.Entry<Character, KeyStat> entry : sorted.entrySet()) {
         KeyStat ks = entry.getValue();
         char ch = entry.getKey();
         String display = ch == ' ' ? "SPC" : String.valueOf(ch);
         sb.append(String.format("%-6s %8d %8.0f %8d %7.2f\n",
            display, ks.samples, ks.avgTimeMs(),
            ks.bestTimeMs == Long.MAX_VALUE ? 0 : ks.bestTimeMs,
            ks.confidence(targetTimeMs)));
      }
      return sb.toString();
   }

   /**
    * Clear all key statistics.
    */
   void reset() {
      keyMap.clear();
      targetCpm = DEFAULT_TARGET_CPM;
      lessonLines = DEFAULT_LESSON_LINES;
   }

   /**
    * Load key stats from disk.
    */
   static KeyStats load() {
      KeyStats ks = new KeyStats();
      File f = statsFile();
      if (!f.exists())
         return ks;
      try (BufferedReader br = new BufferedReader(
            new FileReader(f, StandardCharsets.UTF_8))) {
         String line;
         while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
               continue;
            String[] parts = line.split("\t");
            if (parts.length < 6)
               continue;
            if ("meta".equals(parts[0])) {
               ks.targetCpm = Integer.parseInt(parts[1]);
               if (parts.length > 2) {
                  int ll = Integer.parseInt(parts[2]);
                  if (ll > 0)
                     ks.lessonLines = ll;
               }
            } else {
               char ch = parts[0].charAt(0);
               KeyStat stat = new KeyStat();
               stat.samples = Integer.parseInt(parts[1]);
               stat.totalTimeMs = Long.parseLong(parts[2]);
               stat.bestTimeMs = Long.parseLong(parts[3]);
               stat.errors = Integer.parseInt(parts[4]);
               stat.lastSeen = Long.parseLong(parts[5]);
               ks.keyMap.put(ch, stat);
            }
         }
      } catch (IOException e) {
         trace("key stats load failed: " + e);
      } catch (NumberFormatException e) {
         trace("key stats parse error: " + e);
      }
      return ks;
   }

   /**
    * Save key stats to disk.
    */
   void save() {
      File f = statsFile();
      f.getParentFile().mkdirs();
      try (BufferedWriter bw = new BufferedWriter(
            new FileWriter(f, StandardCharsets.UTF_8))) {
         bw.write("# Javi per-key typing stats\n");
         bw.write("meta\t" + targetCpm + "\t" + lessonLines
            + "\t0\t0\t0\n");
         for (Map.Entry<Character, KeyStat> entry
               : keyMap.entrySet()) {
            KeyStat stat = entry.getValue();
            bw.write(entry.getKey() + "\t" + stat.samples + "\t"
               + stat.totalTimeMs + "\t" + stat.bestTimeMs + "\t"
               + stat.errors + "\t" + stat.lastSeen + "\n");
         }
      } catch (IOException e) {
         trace("key stats save failed: " + e);
      }
   }

   private static File statsFile() {
      return new File(System.getProperty("user.home"),
         STATS_DIR + File.separator + STATS_FILE);
   }
}
