package javi.typingtutor;

/**
 * Available lesson modes for typing practice.
 */
enum LessonMode {
   /** Weighted random selection from full word bank. */
   ADAPTIVE,
   /** Progressive letter introduction starting from home row. */
   HOMEROW,
   /** Programming-focused word list. */
   CODE,
   /** Javi editor commands — practice commands you don't use. */
   EDITOR,
   /** Custom word list from ~/.javi/typing-words.txt. */
   CUSTOM;

   /**
    * Parse a mode string from user input.
    *
    * @param arg the user-supplied mode argument (may be null)
    * @return the matching LessonMode, or ADAPTIVE if null/unrecognized
    */
   static LessonMode parse(String arg) {
      if (arg == null || arg.isEmpty())
         return ADAPTIVE;
      return switch (arg.toLowerCase()) {
         case "homerow" -> HOMEROW;
         case "code" -> CODE;
         case "editor" -> EDITOR;
         case "custom" -> CUSTOM;
         case "adaptive" -> ADAPTIVE;
         default -> ADAPTIVE;
      };
   }
}
