package javi.typingtutor;

import java.io.IOException;
import java.util.List;

import javi.Command;
import javi.EventQueue;
import javi.FvContext;
import javi.InputException;
import javi.InsertBuffer;
import javi.Plugin;
import javi.Rgroup;
import javi.StringIoc;
import javi.TextEdit;
import javi.UI;

import static history.Tools.trace;

/**
 * Typing practice plugin for javi.
 *
 * <p>Provides the {@code :typingpractice} command to start a typing
 * lesson and {@code :typingstats} to view performance statistics.
 * Uses spaced repetition to emphasize words that are mistyped or
 * typed slowly.</p>
 *
 * <p>Usage: {@code :loadplugin typingtutor} then
 * {@code :typingpractice}</p>
 */
public final class TypingTutorPlugin extends Rgroup implements Plugin {

   public static final String pluginInfo = "Typing practice tutor";

   private static TypingStats stats;
   private static KeyStats keyStats;
   private static CommandTracker commandTracker;
   private static SessionHistory sessionHistory;
   private static TypingSession activeSession;

   static {
      new TypingTutorPlugin();
   }

   public TypingTutorPlugin() {
      stats = TypingStats.load();
      keyStats = KeyStats.load();
      commandTracker = CommandTracker.load();
      sessionHistory = SessionHistory.load();

      // Register command execution observer to track usage
      Command.setCommandObserver(name -> {
         commandTracker.recordCommand(name);
         commandTracker.save();
      });

      registerArgCommand("typingpractice",
         "Start a typing practice lesson", "plugin",
         (arg, count, rcount, fvc, dot) -> {
            startPractice(fvc, arg != null ? arg.toString() : null);
            return null;
         });

      registerArgCommand("typingstats",
         "Show typing practice statistics", "plugin",
         (arg, count, rcount, fvc, dot) -> {
            showStats(fvc);
            return null;
         });

      registerArgCommand("typingcheck",
         "Check current typing lesson progress", "plugin",
         (arg, count, rcount, fvc, dot) -> {
            checkLesson(fvc);
            return null;
         });

      registerArgCommand("typingreset",
         "Reset all typing practice statistics", "plugin",
         (arg, count, rcount, fvc, dot) -> {
            resetStats();
            return null;
         });

      registerArgCommand("typingprogress",
         "Show progressive letter unlock status", "plugin",
         (arg, count, rcount, fvc, dot) -> {
            showProgress(fvc);
            return null;
         });

      registerArgCommand("typingtarget",
         "Set target speed in CPM (e.g. :typingtarget 200)",
         "plugin",
         (arg, count, rcount, fvc, dot) -> {
            setTarget(arg != null ? arg.toString() : null);
            return null;
         });

      registerArgCommand("typinglines",
         "Set lesson line count (e.g. :typinglines 8)",
         "plugin",
         (arg, count, rcount, fvc, dot) -> {
            setLessonLines(arg != null ? arg.toString() : null);
            return null;
         });
   }

   protected Object doroutine(int rnum, Object arg, int count,
         int rcount, FvContext fvc, boolean dotmode) {
      throw new RuntimeException("TypingTutorPlugin: unexpected doroutine");
   }

   private static void startPractice(FvContext fvc, String arg)
         throws IOException, InputException {
      LessonMode mode = LessonMode.parse(arg);

      // Lesson continuation loop: each iteration creates a fresh
      // lesson and insertMode. ContinueLessonException breaks out
      // of insertMode and loops for the next lesson — no recursion.
      while (true) {
         TypingLesson lesson = new TypingLesson(stats, keyStats,
            commandTracker, mode);
         List<String> lines = lesson.generateLesson(
            keyStats.lessonLines());

         StringBuilder content = new StringBuilder();
         int headerCount = 0;
         content.append("=== TYPING PRACTICE ===\n");
         headerCount++;
         content.append("Mode: ").append(mode).append(" | Target: ")
            .append(keyStats.targetCpm()).append(" CPM");
         if (mode == LessonMode.ADAPTIVE || mode == LessonMode.CODE
               || mode == LessonMode.EDITOR) {
            int level = lesson.difficultyLevel();
            content.append(" | Difficulty: ")
               .append(AdaptiveDifficulty.levelDescription(level));
            double avgCpm = lesson.measuredCpm();
            if (avgCpm > 0)
               content.append(
                  String.format(" (%.0f CPM avg)", avgCpm));
         }
         content.append("\n");
         headerCount++;
         if (mode == LessonMode.HOMEROW) {
            ProgressiveLesson prog = new ProgressiveLesson(keyStats);
            content.append("Letters: ")
               .append(prog.unlockedLetters())
               .append(" (").append(prog.unlockedCount()).append("/")
               .append(ProgressiveLesson.LETTER_ORDER.length())
               .append(")\n");
            headerCount++;
            content.append("Focused: '").append(prog.focusedKey())
               .append("'\n");
            headerCount++;
         }
         content.append("Type below each line."
            + " Enter advances to next line."
            + " Results shown after last line.\n");
         headerCount++;
         content.append("\n");
         headerCount++;

         for (String line : lines) {
            content.append(line).append("\n");
            content.append("\n"); // blank line for user to type on
         }

         StringIoc sio = new StringIoc("*typing-practice*",
            content.toString());
         TypingFeedbackBuffer buf = new TypingFeedbackBuffer(
            sio, lines, headerCount);

         activeSession = new TypingSession(lines, buf,
            System.currentTimeMillis(), mode, headerCount);

         // Auto-check: when Enter is pressed on the last user line,
         // compute results and append them to the buffer.
         buf.setLessonCompleteHandler(() -> {
            String results = computeResults();
            activeSession = null;
            return results;
         });

         // Continue handler: throw ContinueLessonException to exit
         // the current insertMode cleanly. The outer loop catches
         // it and starts a fresh lesson — no recursive insertMode.
         buf.setContinueHandler(() -> {
            throw new ContinueLessonException();
         });

         // Force all content to be loaded before connecting the
         // view. StringIoc reads lines in a background thread;
         // without this, readIn() may return 1 causing cursorabs
         // to clamp fileposy to 0 and crash in insertMode.
         buf.finish();

         // Position cursor on the first user typing line
         FvContext<?> newFvc = FvContext.connectFv(buf, fvc.vi);
         newFvc.cursoryabs(headerCount + 2);
         newFvc.cursorabs(0, headerCount + 2);
         UI.reportMessage("Typing practice started ("
            + mode + ") — type below each line");

         // Enter insert mode. Exits on ESC, ExitException (window
         // close), or ContinueLessonException (next lesson).
         boolean shouldContinue = false;
         try {
            InsertBuffer.insertMode(false, 1, newFvc, false, false);
         } catch (ContinueLessonException e) {
            EventQueue.drainEnterEvents();
            shouldContinue = true;
         } finally {
            activeSession = null;
            FvContext.connectFv(fvc.edvec, fvc.vi);
         }

         if (!shouldContinue)
            break; // ESC pressed — exit practice
      }
      UI.reportMessage("Typing practice ended");
   }

   /**
    * Computes scoring for the active session, records stats, and
    * returns the formatted results text. Returns null if no session.
    */
   private static String computeResults() {
      if (activeSession == null)
         return null;

      TypingFeedbackBuffer feedbackBuf =
         (TypingFeedbackBuffer) activeSession.buffer();
      long endTime = feedbackBuf.getFinishTime();
      if (endTime == 0)
         endTime = System.currentTimeMillis();

      long startTime = feedbackBuf.getFirstTypingTime();
      if (startTime == 0)
         startTime = activeSession.startTime();
      long elapsed = endTime - startTime;

      TextEdit<String> buf = activeSession.buffer();
      List<String> expected = activeSession.expectedLines();
      int hdr = activeSession.headerLines();

      int totalChars = 0;
      int correctChars = 0;
      int totalWords = 0;
      int correctWords = 0;
      int editErrors = 0;

      int searchFrom = hdr + 1;
      for (int i = 0; i < expected.size(); i++) {
         String exp = expected.get(i);
         int expLine = -1;
         for (int line = searchFrom; line <= buf.readIn(); line++) {
            if (buf.at(line).toString().equals(exp)) {
               expLine = line;
               break;
            }
         }
         int userLine = (expLine > 0) ? expLine + 1
            : hdr + 2 + (i * 2);
         if (expLine > 0)
            searchFrom = expLine + 2;

         String typed = "";
         if (userLine <= buf.readIn())
            typed = buf.at(userLine).toString();

         // Use edit distance for accurate error counting
         int lineErrors = EditDistance.distance(exp, typed);
         int lineCorrect = EditDistance.correctChars(exp, typed);
         totalChars += exp.length();
         correctChars += lineCorrect;
         editErrors += lineErrors;

         String[] expWords = exp.split("\\s+");
         String[] typedWords = typed.split("\\s+");
         for (int w = 0; w < expWords.length; w++) {
            String ew = expWords[w];
            String tw = w < typedWords.length ? typedWords[w] : "";
            totalWords++;
            boolean wordCorrect = ew.equals(tw);
            if (wordCorrect)
               correctWords++;
            stats.recordWord(ew, wordCorrect,
               elapsed / expected.size());
         }

         long perCharTimeMs = (elapsed / expected.size())
            / Math.max(exp.length(), 1);
         for (int c = 0; c < exp.length(); c++) {
            boolean charCorrect = c < typed.length()
               && typed.charAt(c) == exp.charAt(c);
            keyStats.record(exp.charAt(c), perCharTimeMs,
               charCorrect);
         }
      }

      double accuracy = totalChars > 0
         ? 100.0 * correctChars / totalChars : 0;
      double minutes = elapsed / 60000.0;
      // WPM = correct characters / 5 / minutes (standard formula)
      double wpm = minutes > 0
         ? (correctChars / 5.0) / minutes : 0;
      // CPM = correct characters / minutes
      double cpm = minutes > 0 ? correctChars / minutes : 0;

      StringBuilder report = new StringBuilder();
      report.append("=== TYPING RESULTS ===\n");
      report.append(String.format("Characters: %d\n", totalChars));
      report.append(String.format("Accuracy: %.1f%%  (%d/%d chars,"
         + " %d edit errors)\n",
         accuracy, correctChars, totalChars, editErrors));
      report.append(String.format("Words: %d/%d correct\n",
         correctWords, totalWords));
      report.append(String.format("Speed: %.1f WPM  (%.0f CPM)\n",
         wpm, cpm));
      report.append(String.format("Time: %.1f seconds\n",
         elapsed / 1000.0));

      AdaptiveDifficulty ad = new AdaptiveDifficulty(
         new java.util.Random());
      int level = ad.computeLevel(keyStats);
      report.append("Difficulty: ")
         .append(AdaptiveDifficulty.levelDescription(level))
         .append("\n");

      // Record session and show historical comparison
      LessonMode mode = activeSession.mode();
      sessionHistory.recordSession(wpm, cpm, accuracy,
         totalChars, mode);
      String comparison = sessionHistory.formatComparison(
         wpm, cpm, accuracy, keyStats.targetCpm());
      if (comparison != null)
         report.append(comparison);

      stats.save();
      keyStats.save();
      sessionHistory.save();

      return report.toString();
   }

   private static void checkLesson(FvContext fvc)
         throws InputException {
      if (activeSession == null)
         throw new InputException(
            "No active typing session. Run :typingpractice first.");

      TypingFeedbackBuffer feedbackBuf =
         (TypingFeedbackBuffer) activeSession.buffer();
      String results = computeResults();
      activeSession = null;

      if (results != null) {
         // Append results to the practice buffer
         feedbackBuf.inserttext("\n" + results, 0,
            feedbackBuf.readIn());
         FvContext.connectFv(feedbackBuf, fvc.vi);
      }
      UI.reportMessage("Lesson complete — stats saved");
   }

   private static void resetStats() {
      stats.reset();
      stats.save();
      keyStats.reset();
      keyStats.save();
      sessionHistory.reset();
      sessionHistory.save();
      activeSession = null;
      UI.reportMessage("Typing practice stats cleared");
   }

   private static void showProgress(FvContext fvc)
         throws IOException, InputException {
      ProgressiveLesson prog = new ProgressiveLesson(keyStats);
      String report = prog.formatProgress();
      StringIoc sio = new StringIoc("*typing-progress*", report);
      TextEdit<String> buf = new TextEdit<>(sio, sio.prop);
      FvContext.connectFv(buf, fvc.vi);
   }

   private static void setTarget(String arg) throws InputException {
      if (arg == null || arg.isEmpty())
         throw new InputException(
            "Usage: :typingtarget <cpm> (e.g. :typingtarget 200)");
      try {
         int cpm = Integer.parseInt(arg.trim());
         if (cpm < 50 || cpm > 1000)
            throw new InputException(
               "Target CPM must be between 50 and 1000");
         keyStats.setTargetCpm(cpm);
         keyStats.save();
         UI.reportMessage("Target speed set to " + cpm + " CPM ("
            + (cpm / 5) + " WPM)");
      } catch (NumberFormatException e) {
         throw new InputException(
            "Invalid number: " + arg
            + ". Usage: :typingtarget <cpm>");
      }
   }

   private static void setLessonLines(String arg)
         throws InputException {
      if (arg == null || arg.isEmpty())
         throw new InputException(
            "Usage: :typinglines <count> (e.g. :typinglines 8)");
      try {
         int lines = Integer.parseInt(arg.trim());
         if (lines < 1 || lines > 20)
            throw new InputException(
               "Lesson lines must be between 1 and 20");
         keyStats.setLessonLines(lines);
         keyStats.save();
         UI.reportMessage("Lesson length set to " + lines
            + " lines");
      } catch (NumberFormatException e) {
         throw new InputException(
            "Invalid number: " + arg
            + ". Usage: :typinglines <count>");
      }
   }

   private static void showStats(FvContext fvc)
         throws IOException, InputException {
      StringBuilder combined = new StringBuilder();
      combined.append(stats.formatReport());
      if (keyStats.hasData()) {
         combined.append("\n=== PER-KEY STATISTICS ===\n\n");
         combined.append(keyStats.formatReport());
      }
      if (sessionHistory.sessionCount() > 0) {
         combined.append('\n');
         combined.append(sessionHistory.formatFullReport(
            keyStats.targetCpm()));
      }
      if (commandTracker.hasData()) {
         combined.append('\n');
         combined.append(commandTracker.formatReport());
      }
      StringIoc sio = new StringIoc("*typing-stats*",
         combined.toString());
      TextEdit<String> buf = new TextEdit<>(sio, sio.prop);
      FvContext.connectFv(buf, fvc.vi);
   }
}
