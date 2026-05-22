package javi.typingtutor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static history.Tools.trace;

/**
 * Tracks typing practice session results over time for historical
 * comparison and learning rate prediction.
 *
 * <p>Each completed session is recorded with its WPM, CPM, accuracy,
 * and timestamp. This enables session-over-session comparisons
 * ("you improved 5 WPM since last session") and learning rate
 * prediction via polynomial regression.</p>
 *
 * <p>Session history is persisted to
 * {@code ~/.javi/typing-sessions.dat} as tab-separated records.</p>
 */
final class SessionHistory {

   private static final String STATS_DIR = ".javi";
   private static final String SESSIONS_FILE = "typing-sessions.dat";
   private static final int MAX_HISTORY = 200;

   private final List<SessionRecord> sessions = new ArrayList<>();

   /**
    * A single session's summary result.
    */
   static final class SessionRecord {
      final long timestamp; // epoch millis
      final double wpm;
      final double cpm;
      final double accuracy; // 0-100
      final int totalChars;
      final LessonMode mode;

      SessionRecord(long timestamp, double wpm, double cpm,
            double accuracy, int totalChars, LessonMode mode) {
         this.timestamp = timestamp;
         this.wpm = wpm;
         this.cpm = cpm;
         this.accuracy = accuracy;
         this.totalChars = totalChars;
         this.mode = mode;
      }
   }

   /**
    * Records a completed session.
    */
   void recordSession(double wpm, double cpm, double accuracy,
         int totalChars, LessonMode mode) {
      sessions.add(new SessionRecord(System.currentTimeMillis(),
         wpm, cpm, accuracy, totalChars, mode));
      // Trim history to MAX_HISTORY most recent sessions
      while (sessions.size() > MAX_HISTORY)
         sessions.remove(0);
   }

   /**
    * Returns the number of recorded sessions.
    */
   int sessionCount() {
      return sessions.size();
   }

   /**
    * Returns a CPM history array for use with LearningRate.
    */
   double[] cpmHistory() {
      double[] history = new double[sessions.size()];
      for (int i = 0; i < sessions.size(); i++)
         history[i] = sessions.get(i).cpm;
      return history;
   }

   /**
    * Formats a session summary comparing the latest session to
    * historical performance.
    *
    * @param currentWpm WPM from the session just completed
    * @param currentCpm CPM from the session just completed
    * @param currentAccuracy accuracy from the session just completed
    * @param targetCpm the user's target CPM
    * @return formatted summary string, or null if no history
    */
   String formatComparison(double currentWpm, double currentCpm,
         double currentAccuracy, int targetCpm) {
      StringBuilder sb = new StringBuilder();
      sb.append("\n--- Session Summary ---\n");

      if (sessions.size() <= 1) {
         sb.append("First recorded session! Keep practicing.\n");
         return sb.toString();
      }

      // Compare to previous session
      SessionRecord prev = sessions.get(sessions.size() - 2);
      double wpmDiff = currentWpm - prev.wpm;
      double cpmDiff = currentCpm - prev.cpm;
      double accDiff = currentAccuracy - prev.accuracy;

      sb.append(String.format("vs last session: %+.1f WPM, "
         + "%+.0f CPM, %+.1f%% accuracy\n",
         wpmDiff, cpmDiff, accDiff));

      // Compare to average of last 5 sessions (excluding current)
      int windowSize = Math.min(5, sessions.size() - 1);
      if (windowSize >= 2) {
         double avgWpm = 0, avgCpm = 0, avgAcc = 0;
         int start = sessions.size() - 1 - windowSize;
         for (int i = start; i < sessions.size() - 1; i++) {
            SessionRecord r = sessions.get(i);
            avgWpm += r.wpm;
            avgCpm += r.cpm;
            avgAcc += r.accuracy;
         }
         avgWpm /= windowSize;
         avgCpm /= windowSize;
         avgAcc /= windowSize;
         sb.append(String.format(
            "vs last %d avg: %+.1f WPM, %+.0f CPM, %+.1f%% acc\n",
            windowSize, currentWpm - avgWpm,
            currentCpm - avgCpm, currentAccuracy - avgAcc));
      }

      // Compare to sessions from ~7 days ago if available
      long weekAgo = System.currentTimeMillis() - 7L * 24 * 3600000;
      SessionRecord weekRecord = null;
      for (int i = sessions.size() - 2; i >= 0; i--) {
         if (sessions.get(i).timestamp <= weekAgo) {
            weekRecord = sessions.get(i);
            break;
         }
      }
      if (weekRecord != null) {
         sb.append(String.format(
            "vs ~1 week ago: %+.1f WPM, %+.0f CPM\n",
            currentWpm - weekRecord.wpm,
            currentCpm - weekRecord.cpm));
      }

      // Learning rate prediction
      double[] cpmHist = cpmHistory();
      LearningRate lr = new LearningRate(cpmHist, cpmHist.length);
      double rate = lr.improvementRate();
      if (Math.abs(rate) > 0.1) {
         sb.append(String.format("Trend: %+.1f CPM/session", rate));
         double consistency = lr.consistency();
         if (consistency > 0.5)
            sb.append(String.format(" (R²=%.2f)", consistency));
         sb.append("\n");
      }

      int sessionsNeeded = lr.sessionsToTarget(targetCpm);
      if (sessionsNeeded > 0) {
         sb.append(String.format(
            "Estimated %d more sessions to reach %d CPM target\n",
            sessionsNeeded, targetCpm));
      } else if (sessionsNeeded == 0) {
         sb.append("Target reached! Consider increasing your"
            + " target with :typingtarget\n");
      }

      // Best session ever
      double bestCpm = 0;
      for (SessionRecord r : sessions) {
         if (r.cpm > bestCpm)
            bestCpm = r.cpm;
      }
      if (currentCpm >= bestCpm && sessions.size() > 1) {
         sb.append("*** New personal best! ***\n");
      } else {
         sb.append(String.format("Personal best: %.0f CPM"
            + " (%.1f WPM)\n", bestCpm, bestCpm / 5.0));
      }

      return sb.toString();
   }

   /**
    * Formats a standalone summary report of all session history.
    */
   String formatFullReport(int targetCpm) {
      if (sessions.isEmpty())
         return "No session history yet.\n";

      StringBuilder sb = new StringBuilder();
      sb.append("=== SESSION HISTORY ===\n\n");
      sb.append(String.format("Total sessions: %d\n",
         sessions.size()));

      // Overall stats
      double sumWpm = 0, sumCpm = 0, sumAcc = 0;
      double bestWpm = 0, bestCpm = 0;
      for (SessionRecord r : sessions) {
         sumWpm += r.wpm;
         sumCpm += r.cpm;
         sumAcc += r.accuracy;
         if (r.wpm > bestWpm)
            bestWpm = r.wpm;
         if (r.cpm > bestCpm)
            bestCpm = r.cpm;
      }
      int n = sessions.size();
      sb.append(String.format("Average: %.1f WPM (%.0f CPM),"
         + " %.1f%% accuracy\n", sumWpm / n, sumCpm / n,
         sumAcc / n));
      sb.append(String.format("Best: %.1f WPM (%.0f CPM)\n",
         bestWpm, bestCpm));

      // Learning rate
      double[] cpmHist = cpmHistory();
      LearningRate lr = new LearningRate(cpmHist, cpmHist.length);
      double rate = lr.improvementRate();
      double consistency = lr.consistency();
      if (n >= 3) {
         sb.append(String.format("\nLearning rate: %+.1f"
            + " CPM/session (R²=%.2f)\n", rate, consistency));
         int sessionsNeeded = lr.sessionsToTarget(targetCpm);
         if (sessionsNeeded > 0) {
            sb.append(String.format("Estimated %d sessions to"
               + " reach %d CPM target\n",
               sessionsNeeded, targetCpm));
         } else if (sessionsNeeded == 0) {
            sb.append("Target of " + targetCpm
               + " CPM reached!\n");
         }
      }

      // Recent sessions table (last 10)
      int showCount = Math.min(10, n);
      sb.append("\n");
      sb.append(String.format("%-12s %6s %6s %8s %6s\n",
         "Date", "WPM", "CPM", "Accuracy", "Chars"));
      sb.append("-".repeat(44)).append("\n");
      for (int i = n - showCount; i < n; i++) {
         SessionRecord r = sessions.get(i);
         LocalDate date = Instant.ofEpochMilli(r.timestamp)
            .atZone(ZoneId.systemDefault()).toLocalDate();
         sb.append(String.format("%-12s %6.1f %6.0f %7.1f%% %6d\n",
            date, r.wpm, r.cpm, r.accuracy, r.totalChars));
      }

      return sb.toString();
   }

   /**
    * Clears all session history.
    */
   void reset() {
      sessions.clear();
   }

   /**
    * Loads session history from disk.
    */
   static SessionHistory load() {
      SessionHistory sh = new SessionHistory();
      File f = sessionsFile();
      if (!f.exists())
         return sh;
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
            long ts = Long.parseLong(parts[0]);
            double wpm = Double.parseDouble(parts[1]);
            double cpm = Double.parseDouble(parts[2]);
            double acc = Double.parseDouble(parts[3]);
            int chars = Integer.parseInt(parts[4]);
            LessonMode mode = LessonMode.ADAPTIVE;
            if (parts.length >= 6) {
               try {
                  mode = LessonMode.valueOf(parts[5]);
               } catch (IllegalArgumentException e) {
                  // ignore unknown modes
               }
            }
            sh.sessions.add(new SessionRecord(ts, wpm, cpm, acc,
               chars, mode));
         }
      } catch (IOException e) {
         trace("session history load failed: " + e);
      } catch (NumberFormatException e) {
         trace("session history parse error: " + e);
      }
      return sh;
   }

   /**
    * Saves session history to disk.
    */
   void save() {
      File f = sessionsFile();
      f.getParentFile().mkdirs();
      try (BufferedWriter bw = new BufferedWriter(
            new FileWriter(f, StandardCharsets.UTF_8))) {
         bw.write("# Javi typing session history\n");
         bw.write("# timestamp\twpm\tcpm\taccuracy\tchars\tmode\n");
         for (SessionRecord r : sessions) {
            bw.write(r.timestamp + "\t"
               + String.format("%.2f", r.wpm) + "\t"
               + String.format("%.1f", r.cpm) + "\t"
               + String.format("%.1f", r.accuracy) + "\t"
               + r.totalChars + "\t" + r.mode.name() + "\n");
         }
      } catch (IOException e) {
         trace("session history save failed: " + e);
      }
   }

   private static File sessionsFile() {
      return new File(System.getProperty("user.home"),
         STATS_DIR + File.separator + SESSIONS_FILE);
   }
}
