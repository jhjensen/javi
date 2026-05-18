package javi.tutorial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javi.Rgroup;

/**
 * Tutorial lesson sequencing engine.
 *
 * <p>Manages lesson progression with skip-already-known logic.
 * Queries {@link Rgroup#getCommandCounts()} to determine which
 * commands the user has already mastered, and skips those lessons.</p>
 */
final class TutorialEngine {

   private final TutorialLesson[] allLessons;
   private int currentIndex;
   private final List<String> skippedLessons = new ArrayList<>();

   TutorialEngine() {
      this.allLessons = TutorialLessons.ALL_LESSONS;
      this.currentIndex = 0;
   }

   /**
    * Get the next unskipped lesson, advancing past any that
    * the user has already mastered based on command counts.
    *
    * @return the next lesson to present, or null if all done
    */
   TutorialLesson nextLesson() {
      Map<String, Integer> counts = Rgroup.getCommandCounts();
      while (currentIndex < allLessons.length) {
         TutorialLesson lesson = allLessons[currentIndex];
         if (shouldSkip(lesson, counts)) {
            skippedLessons.add(lesson.getTitle());
            currentIndex++;
         } else {
            currentIndex++;
            return lesson;
         }
      }
      return null;
   }

   /**
    * Find a lesson by ID or title prefix (case-insensitive).
    *
    * @param query lesson ID or title prefix
    * @return the matching lesson, or null
    */
   TutorialLesson findLesson(String query) {
      String lower = query.toLowerCase();
      for (TutorialLesson lesson : allLessons) {
         if (lesson.getId().equalsIgnoreCase(query))
            return lesson;
         if (lesson.getTitle().toLowerCase().startsWith(lower))
            return lesson;
      }
      return null;
   }

   /**
    * Get the list of all lessons with their status.
    *
    * @return formatted status lines
    */
   String[] listLessons() {
      Map<String, Integer> counts = Rgroup.getCommandCounts();
      String[] lines = new String[allLessons.length + 2];
      lines[0] = "=== Tutorial Lessons ===";
      lines[1] = "";
      for (int i = 0; i < allLessons.length; i++) {
         TutorialLesson lesson = allLessons[i];
         String status = shouldSkip(lesson, counts)
            ? "[mastered]" : "[available]";
         lines[i + 2] = String.format("  %d. %-25s %s",
            i + 1, lesson.getTitle(), status);
      }
      return lines;
   }

   /** Get lessons skipped in the most recent nextLesson() sequence. */
   List<String> getSkippedLessons() {
      return skippedLessons;
   }

   /**
    * Get the current lesson without advancing.
    * Returns the first lesson if none has been shown yet.
    *
    * @return the current lesson, or null if no lessons exist
    */
   TutorialLesson currentLesson() {
      if (allLessons.length == 0)
         return null;
      int idx = currentIndex > 0 ? currentIndex - 1 : 0;
      return allLessons[idx];
   }

   /**
    * Get the first lesson.
    *
    * @return the first lesson, or null if no lessons exist
    */
   TutorialLesson firstLesson() {
      if (allLessons.length == 0)
         return null;
      currentIndex = 1;
      return allLessons[0];
   }

   /** Reset to the beginning. */
   void reset() {
      currentIndex = 0;
      skippedLessons.clear();
   }

   /**
    * Go back to the previous lesson.
    *
    * @return the previous lesson, or null if already at the start
    */
   TutorialLesson prevLesson() {
      if (currentIndex <= 1)
         return (allLessons.length > 0) ? allLessons[0] : null;
      currentIndex -= 2;
      return allLessons[currentIndex++];
   }

   /**
    * A lesson should be skipped if ALL target commands have been
    * used at least skipThreshold times.
    */
   private boolean shouldSkip(TutorialLesson lesson,
         Map<String, Integer> counts) {
      int threshold = lesson.getSkipThreshold();
      for (String cmd : lesson.getTargetCommands()) {
         int used = counts.getOrDefault(cmd, 0);
         if (used < threshold)
            return false;
      }
      return true;
   }
}
