package javi.tutorial;

import java.io.IOException;
import java.util.List;
import javi.FvContext;
import javi.InputException;
import javi.Plugin;
import javi.Rgroup;
import javi.StringIoc;
import javi.TextEdit;
import javi.UI;

/**
 * Interactive tutorial plugin for javi.
 *
 * <p>Teaches javi-specific features that differ from stock vi.
 * Tracks which commands the user has already used and skips
 * lessons for mastered commands.</p>
 *
 * <p>Usage:</p>
 * <ul>
 *   <li>{@code :tutorial} — start or continue the tutorial</li>
 *   <li>{@code :tutorial list} — show all lessons with status</li>
 *   <li>{@code :tutorial next} — advance to the next lesson</li>
 *   <li>{@code :tutorial prev} — go back to the previous lesson</li>
 *   <li>{@code :tutorial shell} — jump to a specific lesson</li>
 *   <li>{@code :tutorial reset} — restart from the beginning</li>
 * </ul>
 *
 * <p>The JAR manifest declares {@code Plugin-Class} pointing
 * to this class.</p>
 */
public final class TutorialPlugin extends Rgroup implements Plugin {

   enum Cmd {
      UNUSED,
      TUTORIAL,
   }

   private static final Cmd[] CMDS = Cmd.values();
   private TutorialEngine engine;
   private TutorialLesson currentLesson;

   public Plugin create(List<String> args) {
       return new  TutorialPlugin();
   }
   public TutorialPlugin() {
      final String[] rnames = {
         "",
         "tutorial",
      };
      register(rnames);
      engine = new TutorialEngine();
   }

   public Object doroutine(int rnum, Object arg, int count,
         int rcount, FvContext fvc, boolean dotmode)
         throws IOException, InputException {
      switch (CMDS[rnum]) {
         case TUTORIAL:
            return handleTutorial(arg, fvc);
         default:
            throw new RuntimeException("TutorialPlugin: unknown command");
      }
   }

   private Object handleTutorial(Object arg, FvContext fvc)
         throws InputException {
      String subCmd = (arg instanceof String) ? ((String) arg).trim() : "";

      if (subCmd.isEmpty())
         return showCurrentLesson(fvc);
      if ("next".equalsIgnoreCase(subCmd))
         return showNextLesson(fvc);
      if ("prev".equalsIgnoreCase(subCmd)
            || "back".equalsIgnoreCase(subCmd))
         return showPrevLesson(fvc);
      if ("list".equalsIgnoreCase(subCmd))
         return showLessonList(fvc);
      if ("reset".equalsIgnoreCase(subCmd))
         return resetTutorial(fvc);

      // Try to find a lesson by name
      TutorialLesson lesson = engine.findLesson(subCmd);
      if (lesson != null)
         return showLesson(lesson, fvc);

      UI.reportMessage("Unknown tutorial command: " + subCmd
         + " (try: list, next, prev, reset, or a lesson name)");
      return null;
   }

   private Object showCurrentLesson(FvContext fvc) throws InputException {
      TutorialLesson lesson = (currentLesson == null)
         ? engine.firstLesson() : engine.currentLesson();
      if (lesson == null) {
         UI.reportMessage("No tutorial lessons available.");
         return null;
      }
      return showLesson(lesson, fvc);
   }

   private Object showNextLesson(FvContext fvc) throws InputException {
      TutorialLesson lesson = engine.nextLesson();
      List<String> skipped = engine.getSkippedLessons();
      if (!skipped.isEmpty()) {
         UI.reportMessage("Skipped " + skipped.size()
            + " mastered lesson(s): " + String.join(", ", skipped));
         engine.getSkippedLessons().clear();
      }
      if (lesson == null) {
         UI.reportMessage(
            "Tutorial complete! All lessons done or mastered. "
            + "Use :tutorial reset to start over.");
         return null;
      }
      return showLesson(lesson, fvc);
   }

   private Object showPrevLesson(FvContext fvc) throws InputException {
      TutorialLesson lesson = engine.prevLesson();
      if (lesson == null) {
         UI.reportMessage("Already at the first lesson.");
         return null;
      }
      return showLesson(lesson, fvc);
   }

   private Object showLesson(TutorialLesson lesson, FvContext fvc)
         throws InputException {
      currentLesson = lesson;
      TextEdit<String> buf = createBuffer("*tutorial*",
         lesson.getInstructions());
      FvContext.connectFv(buf, fvc.vi);
      return null;
   }

   private Object showLessonList(FvContext fvc) throws InputException {
      String[] lines = engine.listLessons();
      TextEdit<String> buf = createBuffer("*tutorial*", lines);
      FvContext.connectFv(buf, fvc.vi);
      return null;
   }

   private Object resetTutorial(FvContext fvc) throws InputException {
      engine.reset();
      TutorialLesson lesson = engine.firstLesson();
      if (lesson == null) {
         UI.reportMessage("No tutorial lessons available.");
         return null;
      }
      return showLesson(lesson, fvc);
   }

   /**
    * Create a read-only buffer with the given content lines.
    */
   private TextEdit<String> createBuffer(String name, String[] lines) {
      String content = String.join("\n", lines);
      StringIoc sio = new StringIoc(name, content);
      return new TextEdit<>(sio, sio.prop);
   }
}
