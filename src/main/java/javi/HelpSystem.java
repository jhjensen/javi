package javi;

/**
 * Built-in help system for Javi editor.
 *
 * <p>Provides documentation for vi-style commands, key bindings, and
 * editor features. Help is displayed in a read-only buffer accessible
 * via the {@code :help} command.</p>
 *
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code :help} - Show help overview/index</li>
 *   <li>{@code :help movement} - Show movement commands</li>
 *   <li>{@code :help editing} - Show editing commands</li>
 *   <li>{@code :help search} - Show search commands</li>
 *   <li>{@code :help files} - Show file commands</li>
 *   <li>{@code :help ex} - Show ex/colon commands</li>
 * </ul>
 *
 * @see Command
 * @see Rgroup
 */
public final class HelpSystem {

   /** The singleton help buffer. */
   private static TextEdit<String> helpBuffer;

   /** Private constructor to prevent instantiation. */
   private HelpSystem() {
   }

   /**
    * Get help for a specific topic.
    *
    * @param topic the help topic (null or empty for index)
    * @return TextEdit buffer containing help content
    */
   public static TextEdit<String> getHelp(String topic) {
      if (null == helpBuffer) {
         createHelpBuffer();
      }

      // Clear and repopulate with requested topic
      clearBuffer();

      String normalizedTopic = (null == topic || topic.isEmpty())
         ? "index"
         : topic.toLowerCase().trim();

      switch (normalizedTopic) {
         case "index":
         case "help":
            appendIndex();
            break;
         case "movement":
         case "move":
         case "motion":
            appendMovementHelp();
            break;
         case "editing":
         case "edit":
            appendEditingHelp();
            break;
         case "search":
         case "find":
            appendSearchHelp();
            break;
         case "files":
         case "file":
         case "buffers":
         case "buffer":
            appendFileHelp();
            break;
         case "ex":
         case "command":
         case "commands":
         case "colon":
            appendExHelp();
            break;
         case "visual":
         case "mark":
         case "selection":
            appendVisualHelp();
            break;
         case "undo":
         case "redo":
            appendUndoHelp();
            break;
         case "window":
         case "screen":
         case "scroll":
            appendWindowHelp();
            break;
         default:
            appendUnknownTopic(normalizedTopic);
            break;
      }

      return helpBuffer;
   }

   /**
    * Get a buffer listing all current key bindings.
    *
    * @return TextEdit buffer containing formatted key bindings
    */
   public static TextEdit<String> getKeyBindings() {
      if (null == helpBuffer) {
         createHelpBuffer();
      }
      clearBuffer();
      append("KEY BINDINGS");
      append("============");
      append("");

      // Delegate to MapEvent for the actual binding list
      java.util.List<String> bindings = MapEvent.getAllBindings();
      for (String line : bindings) {
         append(line);
      }

      if (bindings.isEmpty()) {
         append("  (no bindings registered)");
      }

      return helpBuffer;
   }

   /**
    * Create the help buffer if it doesn't exist.
    */
   private static void createHelpBuffer() {
      StringIoc sio = new StringIoc("*help*", "");
      helpBuffer = new TextEdit<>(sio, sio.prop);
   }

   /**
    * Clear the help buffer content.
    */
   private static void clearBuffer() {
      int finish = helpBuffer.finish();
      if (finish > 2) {
         // remove(startLine, numberOfLines) - keep the first empty line
         helpBuffer.remove(1, finish - 2);
      }
   }

   /**
    * Append a line to the help buffer.
    */
   private static void append(String line) {
      helpBuffer.insertOne(line, helpBuffer.finish());
   }

   /**
    * Append the help index/overview.
    */
   private static void appendIndex() {
      append("JAVI EDITOR HELP");
      append("================");
      append("");
      append("Javi is a vi-like text editor written in Java.");
      append("");
      append("HELP TOPICS");
      append("-----------");
      append("  :help movement   - Cursor movement commands");
      append("  :help editing    - Text editing commands");
      append("  :help search     - Search and replace");
      append("  :help files      - File and buffer management");
      append("  :help ex         - Ex/colon commands");
      append("  :help visual     - Visual selection mode");
      append("  :help undo       - Undo and redo");
      append("  :help window     - Window and scrolling");
      append("");
      append("QUICK REFERENCE");
      append("---------------");
      append("  h j k l          Move cursor (left/down/up/right)");
      append("  i a              Insert/Append text");
      append("  x dd             Delete char/line");
      append("  yy p             Yank (copy) and paste");
      append("  u Ctrl-R         Undo/Redo");
      append("  / ?              Search forward/backward");
      append("  :w :q            Save/Quit");
      append("  :e <file>        Edit file");
      append("");
      append("FUNCTION KEYS");
      append("-------------");
      append("  F1               Next position in position list");
      append("  F2               File list");
      append("  F3               Directory list");
      append("  F4               Font list");
      append("  F5               Position list");
      append("  F6               Plugin list");
      append("  F7               Make (build)");
      append("  F8               Terminal (vt100)");
      append("  F11              Toggle fullscreen");
      append("  Ctrl-L           Redraw screen");
      append("");
      append("Type :help <topic> for more information on a topic.");
   }

   /**
    * Append movement command help.
    */
   private static void appendMovementHelp() {
      append("MOVEMENT COMMANDS");
      append("=================");
      append("");
      append("BASIC MOVEMENT");
      append("--------------");
      append("  h, Left          Move cursor left");
      append("  j, Down          Move cursor down");
      append("  k, Up            Move cursor up");
      append("  l, Right         Move cursor right");
      append("");
      append("WORD MOVEMENT");
      append("-------------");
      append("  w                Forward to start of word");
      append("  W                Forward to start of WORD (whitespace-delimited)");
      append("  b                Backward to start of word");
      append("  B                Backward to start of WORD");
      append("  e                Forward to end of word");
      append("  E                Forward to end of WORD");
      append("");
      append("LINE MOVEMENT");
      append("-------------");
      append("  0                Start of line");
      append("  ^                First non-blank character");
      append("  $                End of line");
      append("  +, Enter         First character of next line");
      append("  -                First character of previous line");
      append("");
      append("SCREEN MOVEMENT");
      append("---------------");
      append("  H                Top of screen");
      append("  M                Middle of screen");
      append("  L                Bottom of screen");
      append("  Ctrl-F           Page forward");
      append("  Ctrl-B           Page backward");
      append("  Ctrl-D           Half page down");
      append("  Ctrl-U           Half page up");
      append("");
      append("FILE MOVEMENT");
      append("-------------");
      append("  gg, Shift-Home   Go to first line");
      append("  G, Shift-End     Go to last line");
      append("  <n>G             Go to line n");
      append("");
      append("CHARACTER SEARCH");
      append("----------------");
      append("  f<char>          Find char forward on line");
      append("  F<char>          Find char backward on line");
      append("  t<char>          To char forward (before char)");
      append("  T<char>          To char backward (after char)");
      append("  ;                Repeat last f/F/t/T");
      append("  ,                Repeat last f/F/t/T (opposite direction)");
      append("");
      append("MARKS");
      append("-----");
      append("  m<a-z>           Set mark at current position");
      append("  '<a-z>           Jump to mark");
      append("");
      append("OTHER");
      append("-----");
      append("  %                Jump to matching bracket");
      append("  (, )             Previous/next sentence");
      append("  {, }             Previous/next paragraph");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append editing command help.
    */
   private static void appendEditingHelp() {
      append("EDITING COMMANDS");
      append("================");
      append("");
      append("ENTERING INSERT MODE");
      append("--------------------");
      append("  i                Insert before cursor");
      append("  I                Insert at start of line");
      append("  a                Append after cursor");
      append("  A                Append at end of line");
      append("  o                Open line below");
      append("  O                Open line above");
      append("  s                Substitute character");
      append("  S                Substitute entire line");
      append("  R                Replace mode (overwrite)");
      append("");
      append("EXITING INSERT MODE");
      append("-------------------");
      append("  Escape           Return to command mode");
      append("");
      append("DELETING");
      append("--------");
      append("  x, Delete        Delete character under cursor");
      append("  X, Backspace     Delete character before cursor");
      append("  d<motion>        Delete with motion (e.g., dw, d$)");
      append("  dd               Delete entire line");
      append("  D                Delete to end of line");
      append("");
      append("CHANGING");
      append("--------");
      append("  c<motion>        Change with motion (delete + insert)");
      append("  cc               Change entire line");
      append("  C                Change to end of line");
      append("  r<char>          Replace single character");
      append("  ~                Toggle case of character");
      append("");
      append("COPYING AND PASTING");
      append("-------------------");
      append("  y<motion>        Yank (copy) with motion");
      append("  yy, Y            Yank entire line");
      append("  p                Paste after cursor");
      append("  P                Paste before cursor");
      append("  \"<a-z>y          Yank to named register");
      append("  \"<a-z>p          Paste from named register");
      append("");
      append("INDENTING");
      append("---------");
      append("  ><motion>        Shift right");
      append("  <<motion>        Shift left");
      append("  >>               Shift current line right");
      append("  <<               Shift current line left");
      append("");
      append("JOINING");
      append("-------");
      append("  J                Join current line with next");
      append("");
      append("REPEATING");
      append("---------");
      append("  .                Repeat last change");
      append("  <n><command>     Repeat command n times");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append search command help.
    */
   private static void appendSearchHelp() {
      append("SEARCH COMMANDS");
      append("===============");
      append("");
      append("BASIC SEARCH");
      append("------------");
      append("  /<pattern>       Search forward for pattern");
      append("  ?<pattern>       Search backward for pattern");
      append("  n                Repeat search in same direction");
      append("  N                Repeat search in opposite direction");
      append("  Ctrl-F3          Repeat search forward");
      append("");
      append("PATTERNS");
      append("--------");
      append("  Patterns are Java regular expressions.");
      append("  Common patterns:");
      append("    .              Any character");
      append("    \\w             Word character");
      append("    \\s             Whitespace");
      append("    ^              Start of line");
      append("    $              End of line");
      append("    *              Zero or more of previous");
      append("    +              One or more of previous");
      append("    [abc]          Character class");
      append("    \\(, \\)         Grouping (escaped in vi style)");
      append("");
      append("SEARCH AND REPLACE");
      append("------------------");
      append("  :s/old/new/      Substitute first on current line");
      append("  :s/old/new/g     Substitute all on current line");
      append("  :%s/old/new/g    Substitute all in file");
      append("  :<range>s/old/new/g  Substitute in range");
      append("");
      append("TAGS");
      append("----");
      append("  Ctrl-]           Jump to tag under cursor");
      append("  Ctrl-T           Pop tag stack (return)");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append file and buffer command help.
    */
   private static void appendFileHelp() {
      append("FILE AND BUFFER COMMANDS");
      append("========================");
      append("");
      append("FILE OPERATIONS");
      append("---------------");
      append("  :e <file>        Edit file");
      append("  :e!              Reload current file (discard changes)");
      append("  :w               Write (save) file");
      append("  :w <file>        Write to file");
      append("  :wq              Write and quit");
      append("  :q               Quit (fails if unsaved changes)");
      append("  :q!              Quit without saving");
      append("  :r <file>        Read file into buffer");
      append("");
      append("BUFFER NAVIGATION");
      append("-----------------");
      append("  F2               Show file list");
      append("  Ctrl-^           Switch to alternate file");
      append("  :n               Next file in argument list");
      append("  :N               Previous file in argument list");
      append("");
      append("FILE INFO");
      append("---------");
      append("  Ctrl-G           Show file status");
      append("");
      append("DIRECTORY BROWSING");
      append("------------------");
      append("  F3               Show directory list");
      append("  :e .             Edit current directory");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append ex/colon command help.
    */
   private static void appendExHelp() {
      append("EX (COLON) COMMANDS");
      append("===================");
      append("");
      append("Colon commands are entered after pressing ':' in command mode.");
      append("");
      append("FILE COMMANDS");
      append("-------------");
      append("  :w               Write current file");
      append("  :w <file>        Write to named file");
      append("  :q               Quit");
      append("  :wq              Write and quit");
      append("  :e <file>        Edit file");
      append("  :e!              Reload current file");
      append("  :r <file>        Read file into buffer");
      append("");
      append("SEARCH/REPLACE");
      append("--------------");
      append("  :s/old/new/      Substitute on current line");
      append("  :s/old/new/g     Global substitute on line");
      append("  :%s/old/new/g    Substitute in entire file");
      append("");
      append("LINE ADDRESSING");
      append("---------------");
      append("  :<n>             Go to line n");
      append("  :$               Go to last line");
      append("  :.               Current line");
      append("  :<n>,<m>         Range from line n to m");
      append("  :%               Entire file (same as 1,$)");
      append("");
      append("RANGE COMMANDS");
      append("--------------");
      append("  :<range>d        Delete lines");
      append("  :<range>y        Yank (copy) lines");
      append("  :<range>m<n>     Move lines to after line n");
      append("  :<range>t<n>     Copy lines to after line n");
      append("");
      append("GLOBAL COMMANDS");
      append("---------------");
      append("  :g/<pattern>/d   Delete lines matching pattern");
      append("  :v/<pattern>/d   Delete lines NOT matching pattern");
      append("");
      append("SETTINGS");
      append("--------");
      append("  :set <option>=<value>   Set option");
      append("  :tabstop <n>     Set tab width");
      append("");
      append("OTHER COMMANDS");
      append("--------------");
      append("  :help            Show help");
      append("  :mk              Run make");
      append("  :!<cmd>          Run shell command");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append visual/selection mode help.
    */
   private static void appendVisualHelp() {
      append("VISUAL (SELECTION) MODE");
      append("=======================");
      append("");
      append("ENTERING VISUAL MODE");
      append("--------------------");
      append("  v                Character-wise visual mode");
      append("  V                Line-wise visual mode");
      append("");
      append("IN VISUAL MODE");
      append("--------------");
      append("  Use movement keys to extend selection.");
      append("");
      append("  d                Delete selection");
      append("  y                Yank (copy) selection");
      append("  c                Change selection");
      append("  >                Shift selection right");
      append("  <                Shift selection left");
      append("  ~                Toggle case");
      append("  Escape           Exit visual mode");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append undo/redo help.
    */
   private static void appendUndoHelp() {
      append("UNDO AND REDO");
      append("=============");
      append("");
      append("COMMANDS");
      append("--------");
      append("  u                Undo last change");
      append("  Ctrl-R           Redo last undone change");
      append("  Ctrl-Z           Undo (alternate)");
      append("  Ctrl-Y           Redo (alternate)");
      append("  U                Undo all changes on current line");
      append("");
      append("PERSISTENCE");
      append("-----------");
      append("  Javi supports persistent undo. Your undo history");
      append("  is saved in .dmp2 files alongside each edited file.");
      append("  This allows you to undo changes even after closing");
      append("  and reopening a file.");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append window/scrolling help.
    */
   private static void appendWindowHelp() {
      append("WINDOW AND SCROLLING");
      append("====================");
      append("");
      append("SCROLLING");
      append("---------");
      append("  Ctrl-F, Page Down   Scroll forward one page");
      append("  Ctrl-B, Page Up     Scroll backward one page");
      append("  Ctrl-D              Scroll down half page");
      append("  Ctrl-U              Scroll up half page");
      append("  Ctrl-E              Scroll down one line");
      append("  Ctrl-Y              Scroll up one line");
      append("");
      append("CURSOR POSITIONING");
      append("------------------");
      append("  z<Enter>         Move current line to top of screen");
      append("  z.               Move current line to center of screen");
      append("  z-               Move current line to bottom of screen");
      append("");
      append("SCREEN POSITIONS");
      append("----------------");
      append("  H                Move to top of screen");
      append("  M                Move to middle of screen");
      append("  L                Move to bottom of screen");
      append("");
      append("DISPLAY");
      append("-------");
      append("  Ctrl-L           Redraw screen");
      append("  F11              Toggle fullscreen");
      append("");
      append("RESIZING");
      append("--------");
      append("  z<n><Enter>      Set window to n lines");
      append("  :lines <n>       Set default window height");
      append("  :setwidth <n>    Set default window width");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append help for unknown topic.
    */
   private static void appendUnknownTopic(String topic) {
      append("Unknown help topic: " + topic);
      append("");
      append("Available topics:");
      append("  movement   - Cursor movement");
      append("  editing    - Text editing");
      append("  search     - Search and replace");
      append("  files      - File management");
      append("  ex         - Ex/colon commands");
      append("  visual     - Visual selection");
      append("  undo       - Undo and redo");
      append("  window     - Window and scrolling");
      append("");
      append("Type :help for index.");
   }
}
