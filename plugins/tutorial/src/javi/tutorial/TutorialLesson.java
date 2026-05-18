package javi.tutorial;

/**
 * A single tutorial lesson definition.
 *
 * <p>Each lesson teaches one or more commands specific to javi.
 * The lesson tracks which target commands have been used during
 * the lesson session, enabling passive validation.</p>
 */
public final class TutorialLesson {

   private final String id;
   private final String title;
   private final String[] targetCommands;
   private final String[] instructions;
   private final int skipThreshold;

   /**
    * Create a lesson.
    *
    * @param id short identifier (e.g. "shell1")
    * @param title display title
    * @param targetCommands command names this lesson teaches
    * @param instructions text lines shown to the user
    * @param skipThreshold number of prior uses indicating mastery
    */
   TutorialLesson(String id, String title, String[] targetCommands,
         String[] instructions, int skipThreshold) {
      this.id = id;
      this.title = title;
      this.targetCommands = targetCommands;
      this.instructions = instructions;
      this.skipThreshold = skipThreshold;
   }

   /** Short identifier for this lesson. */
   public String getId() {
      return id;
   }

   /** Display title. */
   public String getTitle() {
      return title;
   }

   /** Command names this lesson teaches. */
   public String[] getTargetCommands() {
      return targetCommands;
   }

   /** Instruction text lines shown to the user. */
   public String[] getInstructions() {
      return instructions;
   }

   /**
    * Number of prior uses of a target command that indicates
    * the user already knows it and the lesson can be skipped.
    */
   public int getSkipThreshold() {
      return skipThreshold;
   }
}
