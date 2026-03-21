package javi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
   private static final HelpBuffer helpBuf =
      new HelpBuffer("*help*");

   /** Private constructor to prevent instantiation. */
   private HelpSystem() {
   }

   /**
    * Provider interface for dynamically registered help topics.
    */
   public interface HelpTopicProvider {
      void appendHelp();
   }

   private static final ArrayList<RegisteredTopic> registeredTopics =
      new ArrayList<>();

   private static final class RegisteredTopic {
      final String name;
      final String[] aliases;
      final HelpTopicProvider provider;

      RegisteredTopic(String name, String[] aliases,
            HelpTopicProvider provider) {
         this.name = name;
         this.aliases = aliases;
         this.provider = provider;
      }

      boolean matches(String topic) {
         if (name.equals(topic))
            return true;
         for (String alias : aliases)
            if (alias.equals(topic))
               return true;
         return false;
      }
   }

   /**
    * Register a help topic dynamically.
    *
    * @param name the primary topic name
    * @param aliases alternative names that also match
    * @param provider callback that appends help content
    */
   public static void registerHelpTopic(String name, String[] aliases,
         HelpTopicProvider provider) {
      registeredTopics.add(new RegisteredTopic(name, aliases, provider));
   }

   /**
    * Get help for a specific topic.
    *
    * @param topic the help topic (null or empty for index)
    * @return TextEdit buffer containing help content
    */
   public static TextEdit<String> getHelp(String topic) {
      helpBuf.ensure();

      // Clear and repopulate with requested topic
      helpBuf.clear();

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
         case "shell":
         case "terminal":
         case "vt100":
            appendShellHelp();
            break;
         case "diredit":
            appendDirEditHelp();
            break;
         case "filelist":
         case "files-list":
            appendFileListHelp();
            break;
         case "directory":
         case "dir":
         case "dirlist":
            appendDirectoryHelp();
            break;
         case "keybindings":
         case "keymap":
         case "bindings":
         case "keys":
            appendKeybindingsHelp();
            break;
         case "folding":
         case "fold":
         case "folds":
            appendFoldingHelp();
            break;
         case "git":
         case "vcs":
            appendGitHelp();
            break;
         case "tutorial":
            appendTutorialHelp();
            break;
         case "ai":
         case "chat":
         case "copilot":
            appendAiHelp();
            break;
         default:
            // Check dynamically registered topics
            boolean found = false;
            for (RegisteredTopic rt : registeredTopics) {
               if (rt.matches(normalizedTopic)) {
                  rt.provider.appendHelp();
                  found = true;
                  break;
               }
            }
            if (!found)
               appendUnknownTopic(normalizedTopic);
            break;
      }

      annotateBindingsInBuffer();
      return helpBuf.getBuffer();
   }

   /** Primary help topic names for tab completion. */
   private static final String[] BUILT_IN_TOPICS = {
      "index", "movement", "editing", "search", "files", "ex",
      "visual", "undo", "window", "shell", "diredit", "filelist",
      "directory", "keybindings", "folding", "tutorial"
   };

   /**
    * Get all help topic names (built-in + registered, for tab
    * completion).
    */
   static String[] getTopics() {
      if (registeredTopics.isEmpty())
         return BUILT_IN_TOPICS;
      String[] result = new String[BUILT_IN_TOPICS.length
         + registeredTopics.size()];
      System.arraycopy(BUILT_IN_TOPICS, 0, result, 0,
         BUILT_IN_TOPICS.length);
      for (int i = 0; i < registeredTopics.size(); i++)
         result[BUILT_IN_TOPICS.length + i] =
            registeredTopics.get(i).name;
      return result;
   }

   /**
    * Get a buffer listing all current key bindings.
    *
    * @return TextEdit buffer containing formatted key bindings
    */
   public static TextEdit<String> getKeyBindings() {
      helpBuf.ensure();
      helpBuf.clear();
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

      return helpBuf.getBuffer();
   }

   /**
    * Get a buffer listing context-aware key bindings for the active keymap.
    *
    * @param fvc the current file-view context
    * @return TextEdit buffer containing bindings for the active keymap
    */
   public static TextEdit<String> getContextBindings(FvContext fvc) {
      helpBuf.ensure();
      helpBuf.clear();

      KeyMap active = MapEvent.getActiveKeyMap(fvc);
      append("ACTIVE KEY BINDINGS");
      append("===================");
      append("");

      // Show keymap chain
      StringBuilder chain = new StringBuilder("Keymap chain: ");
      chain.append(active.getName());
      KeyMap p = active.getParent();
      while (p != null) {
         chain.append(" -> ").append(p.getName());
         p = p.getParent();
      }
      append(chain.toString());
      append("");

      // Show overlay bindings if this is not the root keymap
      if (active.getParent() != null) {
         java.util.List<String> moveOverrides =
            active.getMoveKeys().getBindingList();
         java.util.List<String> editOverrides =
            active.getEditKeys().getBindingList();
         if (!moveOverrides.isEmpty() || !editOverrides.isEmpty()) {
            append("OVERLAY BINDINGS (" + active.getName() + ")");
            append("------------------------------------------");
            if (!moveOverrides.isEmpty()) {
               append("  Movement overrides:");
               for (String b : moveOverrides)
                  append("    " + b);
            }
            if (!editOverrides.isEmpty()) {
               append("  Edit overrides:");
               for (String b : editOverrides)
                  append("    " + b);
            }
            append("");
         }
      }

      // Show all effective bindings from the base keymap
      java.util.List<String> bindings = MapEvent.getAllBindings();
      append("BASE BINDINGS (normal)");
      append("----------------------");
      for (String line : bindings) {
         append(line);
      }

      return helpBuf.getBuffer();
   }

   /**
    * Get a buffer listing bindings filtered by keymap name.
    *
    * <p>If the named keymap is an overlay, shows only its override
    * bindings. If it is the root keymap, shows all bindings.
    * Returns unknown-keymap message if name is not registered.</p>
    *
    * @param keymapName the keymap name to filter by
    * @return TextEdit buffer containing filtered bindings
    */
   public static TextEdit<String> getFilteredBindings(String keymapName) {
      helpBuf.ensure();
      helpBuf.clear();

      KeyMap km = KeyMap.get(keymapName);
      if (km == null) {
         append("Unknown keymap: " + keymapName);
         append("");
         append("Registered keymaps: "
            + KeyMap.registeredNames());
         return helpBuf.getBuffer();
      }

      append("KEY BINDINGS: " + keymapName);
      append("=".repeat(15 + keymapName.length()));
      append("");

      // Show keymap chain
      StringBuilder chain = new StringBuilder("Keymap chain: ");
      chain.append(km.getName());
      KeyMap p = km.getParent();
      while (p != null) {
         chain.append(" -> ").append(p.getName());
         p = p.getParent();
      }
      append(chain.toString());
      append("");

      // Show this keymap's own bindings
      java.util.List<String> moveBindings =
         km.getMoveKeys().getBindingList();
      java.util.List<String> editBindings =
         km.getEditKeys().getBindingList();

      if (!moveBindings.isEmpty()) {
         append("MOVEMENT KEYS");
         append("-------------");
         for (String b : moveBindings)
            append(b);
         append("");
      }

      if (!editBindings.isEmpty()) {
         append("COMMAND KEYS");
         append("------------");
         for (String b : editBindings)
            append(b);
         append("");
      }

      if (moveBindings.isEmpty() && editBindings.isEmpty()) {
         if (km.getParent() != null)
            append("  (no overrides - all keys inherited from "
               + km.getParent().getName() + ")");
         else
            append("  (no bindings)");
      }

      return helpBuf.getBuffer();
   }

   /**
    * Check if the given buffer is the static help buffer.
    */
   static boolean isHelpBuffer(EditContainer buf) {
      return helpBuf.getBuffer() != null
         && helpBuf.getBuffer() == buf;
   }

   /**
    * Append a line to the help buffer.
    */
   private static void append(String line) {
      helpBuf.append(line);
   }

   /** Pattern to match colon command references like :help, :shells, :mapkey */
   private static final Pattern COLON_CMD_PATTERN =
      Pattern.compile(":(\\w+)");

   /**
    * Post-process the help buffer to annotate colon command references
    * with their bound keys. For example, if ":mk" appears in help text
    * and "mk" is bound to F7, the line is annotated with "[F7]".
    *
    * <p>Only annotates lines that contain colon commands with known
    * key bindings. Lines that are headers, separators, or already
    * show key information are left unchanged.</p>
    */
   private static void annotateBindingsInBuffer() {
      KeyMap normalMap = MapEvent.getNormalKeyMap();
      if (normalMap == null)
         return;

      Map<String, List<String>> reverseMap =
         normalMap.getReverseBindingMap();
      if (reverseMap.isEmpty())
         return;

      int end = helpBuf.getBuffer().finish();
      for (int i = 1; i < end; i++) {
         String line = helpBuf.getBuffer().at(i).toString();
         String annotated = annotateLineBindings(line, reverseMap);
         if (annotated != null) {
            helpBuf.getBuffer().remove(i, 1);
            helpBuf.getBuffer().insertOne(annotated, i);
            // re-fetch end since buffer was modified
            end = helpBuf.getBuffer().finish();
         }
      }
   }

   /**
    * Annotate a single line with key binding information.
    *
    * @return annotated line, or null if no annotation needed
    */
   private static String annotateLineBindings(String line,
         Map<String, List<String>> reverseMap) {
      // Skip headers, separator lines, and empty lines
      if (line.isBlank() || line.startsWith("===") || line.startsWith("---"))
         return null;

      Matcher m = COLON_CMD_PATTERN.matcher(line);
      StringBuilder sb = null;
      int lastEnd = 0;

      while (m.find()) {
         String cmdName = m.group(1);
         List<String> keys = reverseMap.get(cmdName);
         if (keys != null && !keys.isEmpty()) {
            if (sb == null)
               sb = new StringBuilder();
            sb.append(line, lastEnd, m.end());
            sb.append(" [").append(String.join(", ", keys)).append(']');
            lastEnd = m.end();
         }
      }

      if (sb == null)
         return null;

      sb.append(line, lastEnd, line.length());
      return sb.toString();
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
      append("  :help shell      - Shell / terminal commands");
      append("  :help diredit    - Directory editor (DirEdit)");
      append("  :help filelist   - File list buffer");
      append("  :help directory  - Directory list buffer");
      append("  :help keybindings - Key binding architecture");
      append("  :help folding    - Code folding commands");
      append("  :help git        - Git integration commands");
      append("  :help tutorial   - Tutorial commands");
      for (RegisteredTopic rt : registeredTopics)
         append("  :help " + rt.name
            + " ".repeat(Math.max(1, 11 - rt.name.length()))
            + "- " + rt.name + " help");
      append("");
      append("QUICK REFERENCE");
      append("---------------");
      append("  h j k l          Move cursor (left/down/up/right)");
      append("  i a              Insert/Append text");
      append("  x dd             Delete char/line");
      append("  yy p             Yank (copy) and paste");
      append("  u ^R         Undo/Redo");
      append("  / ?              Search forward/backward");
      append("  :w :q            Save/Quit");
      append("  :e <file>        Edit file");
      append("");
      append("FUNCTION KEYS");
      append("-------------");
      append("  F1               Next position in position list");
      append("  Shift-F1         Toggle context help panel");
      append("  F2               File list");
      append("  F3               Directory list");
      append("  F4               Font list");
      append("  F5               Position list");
      append("  F6               Position list list (buffer hub)");
      append("  F7               Make (build)");
      append("  F8               Terminal (vt100)");
      append("  F11              Toggle fullscreen");
      append("  ^L           Redraw screen");
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
      append(
         "  W                Forward to start of WORD (whitespace-delimited)");
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
      append("  ^F           Page forward");
      append("  ^B           Page backward");
      append("  ^D           Half page down");
      append("  ^U           Half page up");
      append("");
      append("FILE MOVEMENT");
      append("-------------");
      append("  1G, Shift-Home   Go to first line");
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
      append("  ^F3          Repeat search forward");
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
      append("  ^]           Jump to tag under cursor");
      append("  ^T           Pop tag stack (return)");
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
      append("  ^^           Switch to alternate file");
      append("  :n               Next file in argument list");
      append("  :N               Previous file in argument list");
      append("");
      append("FILE INFO");
      append("---------");
      append("  ^G           Show file status");
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
      append("GIT (see :help git for full list)");
      append("---");
      append("  :git status      Show staged/unstaged/untracked files");
      append("  :git commit      Open commit message buffer");
      append("  :git do commit   Finalize commit");
      append("  :git diff        Show diff");
      append("  :git log         Show log with graph");
      append("  :git blame       Per-line blame annotations");
      append("  :git push        Push to remote");
      append("");
      append("OTHER COMMANDS");
      append("--------------");
      append("  :help            Show help");
      append("  :mk              Run make");
      append("  :!<cmd>          Run shell command");
      append("");
      append("TUTORIAL (see :help tutorial for full list)");
      append("---------");
      append("  :tutorial        Show current lesson");
      append("  :tutorial next   Advance to next lesson");
      append("  :tutorial prev   Go to previous lesson");
      append("  :tutorial reset  Restart from first lesson");
      append("  :tutorial list   Show all lessons");
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
      append("  ^R           Redo last undone change");
      append("  ^Z           Undo (alternate)");
      append("  ^Y           Redo (alternate)");
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
      append("  ^F, Page Down   Scroll forward one page");
      append("  ^B, Page Up     Scroll backward one page");
      append("  ^D              Scroll down half page");
      append("  ^U              Scroll up half page");
      append("  ^E              Scroll down one line");
      append("  ^Y              Scroll up one line");
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
      append("  ^L           Redraw screen");
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
    * Append shell/terminal help.
    */
   private static void appendShellHelp() {
      append("SHELL / TERMINAL COMMANDS");
      append("=========================");
      append("");
      append("Javi includes an integrated VT100 terminal emulator.");
      append("You can run shell commands inside the editor without");
      append("leaving your editing session.");
      append("");
      append("STARTING A SHELL");
      append("----------------");
      append("  F8               Open or toggle to shell");
      append("  :shell            Alias for F8 (open / toggle)");
      append("  :shell <host>    Open SSH session to host");
      append("  :shell <n>       Switch to shell by ID");
      append("");
      append("PASSTHROUGH MODE");
      append("----------------");
      append("  F8 (in shell)    Enter passthrough mode");
      append("                   All keystrokes go to the shell.");
      append("  F8 (passthrough) Exit passthrough, back to editor");
      append("");
      append("SHELL MANAGEMENT");
      append("----------------");
      append("  :shells          List all active shells");
      append("  :shellclose      Close current shell");
      append("  :shellclose <n>  Close shell by ID");
      append("  :shellnext       Switch to next shell");
      append("  :shellprev       Switch to previous shell");
      append("  :shellname <n>   Rename current shell");
      append("");
      append("ENVIRONMENT");
      append("-----------");
      append("  :shellenv K=V    Set env variable in current shell");
      append("                   Exports it immediately via 'export'.");
      append("");
      append("HISTORY");
      append("-------");
      append("  :shellhistory    Open full scrollback in read-only buffer");
      append("");
      append("CLOSING");
      append("-------");
      append("  ZZ               Close shell buffer AND kill process");
      append("  :shellclose      Same as above for the active shell");
      append("");
      append("NOTES");
      append("-----");
      append("  - TERM is set to 'xterm' for each shell.");
      append("  - COLUMNS and LINES are sent on shell creation.");
      append("  - The shell runs under 'script' for PTY support.");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append directory editor help.
    */
   private static void appendDirEditHelp() {
      append("DIRECTORY EDITOR (DirEdit)");
      append("=========================");
      append("");
      append("The directory editor provides netrw-like filesystem browsing.");
      append("Open a directory with :e <dir> or :diredit <dir>");
      append("");
      append("NAVIGATION");
      append("----------");
      append("  Enter            Open file or enter directory");
      append("  -                Go to parent directory");
      append("  .                Toggle hidden (dot) files");
      append("  q                Quit directory browser (go to file list)");
      append("");
      append("DISPLAY");
      append("-------");
      append("  s                Cycle sort mode (name/size/date/type)");
      append("  R                Refresh directory listing");
      append("");
      append("FILE OPERATIONS");
      append("---------------");
      append("  D                Delete file under cursor (with confirmation)");
      append("  dd               Delete file under cursor (via normal mode)");
      append("  :diredit_rename <name>   Rename file under cursor");
      append("  :diredit_mkdir <name>    Create new subdirectory");
      append("  :diredit_newfile <name>  Create new empty file");
      append("  :diredit_copy <dest>     Copy file under cursor");
      append("");
      append("SEARCH PATH");
      append("-----------");
      append("  S                Toggle directory in/out of search path");
      append("  Directories in the search path are marked with 'S'.");
      append("");
      append("MARKS");
      append("-----");
      append("  Files marked for deletion show 'D' at the start of the line.");
      append("");
      append("DISPLAY FORMAT");
      append("--------------");
      append("  Each line shows: [mark] permissions  size  date  name");
      append("  Directories have a trailing '/' and show <DIR> for size.");
      append("  The '../' entry navigates to the parent directory.");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append file list buffer help.
    */
   private static void appendFileListHelp() {
      append("FILE LIST BUFFER");
      append("================");
      append("");
      append("The file list (F2) shows all open files/buffers.");
      append("It uses a dedicated keymap overlay ('filelist') where");
      append("some keys behave differently from normal editing.");
      append("");
      append("NAVIGATION");
      append("----------");
      append("  j, Down          Move to next file");
      append("  k, Up            Move to previous file");
      append("  Enter, F1        Open file at cursor");
      append("  ^F1          Open and wait for position list");
      append("  Shift-F1         Toggle context help panel");
      append("");
      append("FILE MANAGEMENT");
      append("---------------");
      append("  F2               Return to previous buffer");
      append("  :w               Save current file");
      append("  :e <file>        Open a new file");
      append("");
      append("SEARCHING");
      append("---------");
      append("  / ?              Search file names forward/backward");
      append("  n N              Repeat search");
      append("");
      append("KEYBINDING OVERLAY");
      append("------------------");
      append("  The filelist uses a 'filelist' keymap layered on top");
      append("  of the normal keymap. Enter/CR is remapped to open");
      append("  the file at cursor instead of moving down a line.");
      append("  All other normal-mode keys work as usual.");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append directory list buffer help.
    */
   private static void appendDirectoryHelp() {
      append("DIRECTORY LIST BUFFER");
      append("=====================");
      append("");
      append("The directory list (F3) shows configured directories.");
      append("Use it to browse and search files across directories.");
      append("");
      append("NAVIGATION");
      append("----------");
      append("  j, Down          Move to next directory");
      append("  k, Up            Move to previous directory");
      append("  Enter, F1        Open/expand directory at cursor");
      append("");
      append("SEARCHING");
      append("---------");
      append("  / ?              Search directory names forward/backward");
      append("  n N              Repeat search");
      append("");
      append("DIRECTORY OPERATIONS");
      append("--------------------");
      append("  F3               Toggle/refresh directory list");
      append("  :e .             Edit current directory");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append keybindings architecture help.
    */
   private static void appendKeybindingsHelp() {
      append("KEY BINDING ARCHITECTURE");
      append("========================");
      append("");
      append("Javi uses layered keymaps for context-sensitive key bindings.");
      append("");
      append("KEYMAP HIERARCHY");
      append("----------------");
      append("  buffer-specific keymap  (e.g. 'filelist', 'shell')");
      append("         | parent");
      append("  mode-based keymap       (e.g. 'normal')");
      append("");
      append("  Keys are looked up in the buffer-specific overlay first.");
      append("  If not found, lookup falls through to the parent keymap.");
      append("");
      append("REGISTERED KEYMAPS");
      append("------------------");
      java.util.Set<String> names = KeyMap.registeredNames();
      for (String name : names) {
         KeyMap km = KeyMap.get(name);
         StringBuilder sb = new StringBuilder("  ");
         sb.append(name);
         if (km.getParent() != null)
            sb.append("  (parent: ").append(km.getParent().getName())
               .append(')');
         append(sb.toString());
      }
      append("");
      append("KEY GROUPS");
      append("----------");
      append("  Each keymap has two key groups:");
      append("  - movement keys  (hjkl, arrows, word motion, scrolling)");
      append("  - editing keys   (i/a/o, d/c/y, function keys, etc.)");
      append("");
      append("RUNTIME MODIFICATION");
      append("--------------------");
      append("  :mapkey <group> <key> <command>   Bind a key");
      append("  :unmapkey <group> <key>           Unbind a key");
      append("  :keymap                           Show active keymap chain");
      append("  :map                              Show all key bindings");
      append("  :map <keymap>                     Show bindings for a keymap");
      append("");
      append("  group: 'move' for movement keys, 'edit' for editing keys");
      append("  key:   single char, C-x (ctrl), S-x (shift), F1-F12,");
      append("         Up, Down, Left, Right, Home, End, PgUp, PgDn");
      append("");
      append("PERSISTENCE");
      append("-----------");
      append("  :savemapkeys                      Save user bindings to disk");
      append("  :loadmapkeys                      Load user bindings from disk");
      append("");
      append("  Bindings are saved to ~/.javi/keybindings in a format");
      append("  compatible with :mapkey commands. To auto-load bindings");
      append("  on startup, add 'loadmapkeys' to your .javini file.");
      append("");
      append("BUFFER-TYPE AUTO-DETECTION");
      append("-------------------------");
      append("  When you switch to a buffer, its type is detected:");
      append("  - File list (F2)     -> 'filelist' keymap");
      append("  - Shell/Terminal (F8) -> 'shell' keymap");
      append("  - Regular file       -> 'normal' keymap");
      append("");
      append("  The :help command also adapts: in a shell buffer,");
      append("  :help with no topic shows shell help instead of the index.");
      append("  Similarly for filelist and directory buffers.");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append git integration help.
    */
   private static void appendGitHelp() {
      append("GIT INTEGRATION");
      append("===============");
      append("");
      append("Javi provides Magit-inspired Git commands accessible via");
      append("colon commands. Requires git on PATH and a git repository.");
      append("");
      append("SHORTHAND");
      append("---------");
      append("  :git <subcmd>      Shorthand for :git_<subcmd>");
      append("                     Spaces become underscores:");
      append("                     :git do commit  =>  :git_do_commit");
      append("                     :git status     =>  :git_status");
      append("");
      append("STATUS AND STAGING");
      append("------------------");
      append("  :git status        Show staged, unstaged, and untracked files");
      append("  :git stage <file>  Stage a file (git add)");
      append("  :git unstage <file> Unstage a file (git restore --staged)");
      append("  :git stage line    Stage the file on the cursor line");
      append("  :git unstage line  Unstage the file on the cursor line");
      append("  :git toggle        Toggle staged/unstaged for cursor line");
      append("  :git discard       Discard unstaged changes for cursor line");
      append("  :git refresh       Refresh the status buffer");
      append("");
      append("COMMITTING");
      append("----------");
      append("  :git commit        Open commit message buffer");
      append("  :git do commit     Finalize commit with message from buffer");
      append("");
      append("VIEWING");
      append("-------");
      append("  :git diff [file]   Show diff (all or specific file)");
      append("  :git log           Show last 100 log entries (graph)");
      append("  :git blame [file]  Per-line blame annotations");
      append("  :git branch        Show all branches");
      append("");
      append("BRANCH OPERATIONS");
      append("-----------------");
      append("  :git branch create <name>  Create a new branch");
      append("  :git branch switch <name>  Switch to a branch");
      append("  :git branch delete <name>  Delete a merged branch");
      append("  :git merge <branch>        Merge branch into current");
      append("  :git rebase <branch>       Rebase current branch onto branch");
      append("  :git rebase --continue     Continue rebase after resolving conflicts");
      append("  :git rebase --abort        Abort an in-progress rebase");
      append("");
      append("REMOTE OPERATIONS");
      append("-----------------");
      append("  :git fetch         Fetch from remote");
      append("  :git pull          Pull from remote (fetch + merge)");
      append("  :git push          Push to remote");
      append("");
      append("STASH");
      append("-----");
      append("  :git stash         Stash working directory changes");
      append("  :git stash pop     Pop the top stash entry");
      append("  :git stash list    Show stash list in a buffer");
      append("");
      append("WORKFLOW");
      append("--------");
      append("  1. :git status                  View state");
      append("  2. :git stage <file>            Stage changes");
      append("  3. :git commit                  Open commit editor");
      append("  4. :git do commit               Finalize commit");
      append("  5. :git push                    Push to remote");
      append("");
      append("STATUS BUFFER KEYS");
      append("------------------");
      append("  s       Stage file at cursor");
      append("  u       Unstage file at cursor");
      append("  X       Discard changes for file at cursor");
      append("  cc      Open commit view (type message, then :git do_commit)");
      append("  ca      Amend last commit");
      append("  cA      Amend without editing message");
      append("  R / ^L  Refresh status");
      append("  p       Open patch view for file at cursor");
      append("  d       Show diff");
      append("  ^]      Go to file at cursor or within diff");
      append("  Enter   Toggle staged/unstaged");
      append("  q       Close buffer");
      append("");
      append("PATCH / COMMIT VIEW KEYS");
      append("------------------------");
      append("  s       Stage hunk at cursor");
      append("  u       Unstage hunk at cursor");
      append("  ^]      Go to file at diff location");
      append("  ^L      Refresh view");
      append("  q       Close buffer");
      append("  (visual) s/u  Stage/unstage selected lines");
      append("");
      append("The status buffer auto-refreshes when files are saved.");
      append("Output buffers (*git-status*, *git-diff*, etc.) are");
      append("standard read-only buffers navigable with normal vi keys.");
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
      append("  shell      - Shell / terminal");
      append("  diredit    - Directory editor");
      append("  filelist   - File list buffer");
      append("  directory  - Directory list buffer");
      append("  keybindings - Key binding architecture");
      append("  folding    - Code folding");
      append("  git        - Git integration");
      append("  lsp        - Language Server Protocol");
      append("  tutorial   - Tutorial commands");
      append("  ai         - AI assistant");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append folding command help.
    */
   private static void appendFoldingHelp() {
      append("FOLDING COMMANDS");
      append("================");
      append("");
      append("Javi supports code folding to collapse sections of text.");
      append("Folds can be created from brace matching, indentation,");
      append("or {{{ / }}} markers.");
      append("");
      append("CREATING FOLDS");
      append("--------------");
      append("  :fold            Detect folds by brace matching");
      append("  :foldindent      Detect folds by indentation level");
      append("  :foldmarker      Detect folds by {{{ / }}} markers");
      append("");
      append("OPENING AND CLOSING");
      append("-------------------");
      append("  zo               Open fold at cursor");
      append("  zc               Close fold at cursor");
      append("  za               Toggle fold at cursor");
      append("  zR               Open all folds (repeat to clear folds)");
      append("  zM               Close all folds");
      append("");
      append("MOUSE");
      append("-----");
      append("  Click fold gutter (+/- column)   Toggle fold");
      append("  Click collapsed fold summary     Expand fold");
      append("");
      append("FOLD DISPLAY");
      append("------------");
      append("  The left gutter shows fold indicators:");
      append("    -              Line inside an open fold");
      append("    +              Collapsed fold (click to expand)");
      append("    |              Continuation of a fold");
      append("");
      append("  Collapsed folds display as a single summary line");
      append("  showing the first line and the number of hidden lines.");
      append("");
      append("PERSISTENCE");
      append("-----------");
      append("  Fold state is saved alongside the buffer in .dmp2 files.");
      append("  Folds are restored when the file is reopened.");
      append("");
      append("NOTES");
      append("-----");
      append("  - Cursor movement (j/k) skips over closed folds.");
      append("  - Search (/) opens folds containing matches.");
      append("  - zR with all folds already open clears all folds.");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append a line to the help buffer. Package-private for use
    * by registered help topic providers.
    */
   public static void appendLine(String line) {
      append(line);
   }

   /**
    * Append tutorial command help.
    */
   private static void appendTutorialHelp() {
      append("TUTORIAL COMMANDS");
      append("=================");
      append("");
      append("The tutorial plugin provides interactive lessons");
      append("for javi-specific features.");
      append("");
      append("Load the plugin first:  :loadplugin tutorial");
      append("");
      append("COMMANDS");
      append("--------");
      append("  :tutorial           Show the current lesson");
      append("                      (first lesson if none active)");
      append("  :tutorial next      Advance to the next lesson");
      append("  :tutorial prev      Go back to the previous lesson");
      append("  :tutorial reset     Restart from the first lesson");
      append("  :tutorial list      Show all lessons with status");
      append("  :tutorial <name>    Jump to a lesson by name");
      append("");
      append("LESSON TOPICS");
      append("-------------");
      append("  start     - Getting started with the tutorial");
      append("  shell1    - Shell management");
      append("  shell2    - Shell navigation");
      append("  window1   - Window management (views)");
      append("  buffer1   - Buffer / file navigation");
      append("  dirman1   - Directory manager");
      append("  build1    - Build integration (:mk)");
      append("  motion1   - Javi-specific motions");
      append("");
      append("NOTES");
      append("-----");
      append("  - The tutorial tracks which commands you have used.");
      append("  - Lessons for mastered commands are auto-skipped.");
      append("  - Type :tutorial to return to a lesson at any time.");
      append("");
      append("Type :help for index.");
   }

   /**
    * Append AI assistant help.
    */
   private static void appendAiHelp() {
      append("AI ASSISTANT COMMANDS");
      append("=====================");
      append("");
      append("Javi includes an AI assistant for code explanation,");
      append("review, documentation, and interactive chat.");
      append("");
      append("CHAT");
      append("----");
      append("  :ai <message>       Send a chat message");
      append("  :ai chat            Interactive chat prompt");
      append("  :ai clear           Clear conversation history");
      append("");
      append("CODE ANALYSIS");
      append("-------------");
      append("  :ai explain         Explain current buffer code");
      append("  :ai review          Review code for bugs/issues");
      append("  :ai doc             Generate documentation");
      append("  :ai refactor <ins>  Refactor with instruction");
      append("");
      append("CODE COMPLETION");
      append("---------------");
      append("  :ai complete        AI code completion at cursor");
      append("  :ai accept          Accept ghost text completion");
      append("  :ai dismiss         Dismiss ghost text completion");
      append("  :ai cancel          Cancel in-flight AI request");
      append("");
      append("CONFIGURATION");
      append("-------------");
      append("  :ai config          Show current AI settings");
      append("  :ai test            Test provider connectivity");
      append("  :ai help            Show this help in chat buffer");
      append("");
      append("  :set ai.provider=openai|anthropic|copilot");
      append("  :set ai.model=<model-name>");
      append("  :set ai.apikey=<key>");
      append("  :set ai.maxTokens=<number>");
      append("");
      append("NOTES");
      append("-----");
      append("  - Responses appear in the *ai-chat* buffer.");
      append("  - Chat history persists until :ai clear.");
      append("  - API key can also be set via environment variable");
      append("    (OPENAI_API_KEY or ANTHROPIC_API_KEY).");
      append("");
      append("KEYBINDINGS");
      append("-----------");
      append("  F9               AI chat (:ai)");
      append("  Shift-F9         Explain code (:ai explain)");
      append("  Ctrl-F9          Review code (:ai review)");
      append("  F12              Code completion (:ai complete)");
      append("  Shift-F12        Generate docs (:ai doc)");
      append("  Ctrl-F12         Cancel AI request (:ai cancel)");
      append("");
      append("Type :help for index.");
   }
}
