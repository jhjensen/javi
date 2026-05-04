package javi;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Context-sensitive help generator.
 *
 * <p>Dynamically generates help content by querying the keybinding
 * system rather than using hardcoded text. Designed to eventually
 * replace the static help content in {@link HelpSystem}.</p>
 *
 * <ul>
 *   <li>Normal mode: shows all bound keys from the active keymap</li>
 *   <li>Ex mode: shows ex-command syntax with regex patterns</li>
 * </ul>
 *
 * @see HelpSystem
 * @see KeyMap
 * @see MapEvent
 */
public final class ContextHelp {

   private static final HelpBuffer helpBuf =
      new HelpBuffer("*context-help*");

   /** The side-panel view for help display, or null if hidden. */
   private static View helpPanelView;

   /** FvContext for the help panel, used for scrolling. */
   private static FvContext<?> helpFvc;

   /** True when sub-mode help is being displayed. */
   private static boolean inSubMode;

   /** Width of the help panel in characters. */
   private static final int HELP_PANEL_WIDTH = 45;

   private ContextHelp() {
   }

   /**
    * Toggle context-sensitive help side panel.
    *
    * <p>If the help panel is visible, removes it.
    * Otherwise, creates a side panel showing context-sensitive
    * help that does not take keyboard focus.</p>
    *
    * @param fvc the current file-view context
    * @return true if help was toggled (shown or hidden)
    */
   static boolean toggle(FvContext fvc) throws InputException {
      if (helpPanelView != null) {
         UI.removeHelpPanel(helpPanelView);
         helpPanelView = null;
         helpFvc = null;
         inSubMode = false;
         return true;
      }
      TextEdit<String> buf = getContextHelp(fvc);
      helpPanelView = UI.createHelpPanel(HELP_PANEL_WIDTH);
      if (helpPanelView != null) {
         helpFvc =
            FvContext.getcontext(helpPanelView, buf);
         helpFvc.activateDisplay();
         updateScrollbar();
      }
      return true;
   }

   /**
    * Check if the help side panel is currently visible.
    */
   static boolean isShowingHelp(FvContext fvc) {
      return helpPanelView != null;
   }

   /**
    * Called when the editor context changes (buffer switch,
    * mode change). Refreshes help panel content if visible.
    *
    * @param fvc the new active file-view context
    */
   static void onContextChanged(FvContext fvc) {
      if (helpPanelView == null || fvc == null)
         return;
      if (inSubMode)
         return;
      if (fvc.vi == helpPanelView)
         return;
      getContextHelp(fvc);
      if (helpFvc != null)
         helpFvc.cursorabs(0, 1);
      helpPanelView.repaint();
      updateScrollbar();
   }

   /**
    * Called when insert mode is entered or exited.
    * Refreshes help panel if visible.
    */
   static void onInsertModeChanged() {
      FvContext<?> fvc = FvContext.getCurrFvc();
      if (fvc != null)
         onContextChanged(fvc);
   }

   /**
    * Called after a top-level command completes.
    * If a sub-mode was active, resets the help display
    * to the base context help for the current mode.
    *
    * @param fvc the current file-view context
    */
   static void onCommandCompleted(FvContext fvc) {
      if (!inSubMode || helpPanelView == null)
         return;
      inSubMode = false;
      onContextChanged(fvc);
   }

   /**
    * Scroll the help panel down by one page.
    *
    * @return true if scrolled
    */
   static boolean scrollHelpDown() {
      if (helpFvc == null || helpPanelView == null)
         return false;
      int pageSize = helpPanelView.getRows(1.0f);
      if (pageSize <= 1)
         pageSize = 20;
      int newTop = helpPanelView.screenFirstLine()
         + pageSize - 1;
      int maxY = helpFvc.edvec.finish() - 1;
      if (newTop > maxY)
         newTop = maxY;
      if (newTop < 1)
         newTop = 1;
      helpFvc.cursorabs(0, newTop);
      helpFvc.placeline(newTop, 0);
      helpPanelView.repaint();
      updateScrollbar();
      return true;
   }

   /**
    * Scroll the help panel up by one page.
    *
    * @return true if scrolled
    */
   static boolean scrollHelpUp() {
      if (helpFvc == null || helpPanelView == null)
         return false;
      int pageSize = helpPanelView.getRows(1.0f);
      if (pageSize <= 1)
         pageSize = 20;
      int newTop = helpPanelView.screenFirstLine()
         - pageSize + 1;
      if (newTop < 1)
         newTop = 1;
      helpFvc.cursorabs(0, newTop);
      helpFvc.placeline(newTop, 0);
      helpPanelView.repaint();
      updateScrollbar();
      return true;
   }

   /**
    * Scroll the help panel by the given number of lines.
    * Positive values scroll down, negative values scroll up.
    * Called from the AWT layer for mouse wheel scrolling.
    *
    * @param lines number of lines to scroll (positive=down)
    */
   public static void scrollHelpLines(int lines) {
      if (helpFvc == null || helpPanelView == null)
         return;
      int newTop = helpPanelView.screenFirstLine() + lines;
      int maxY = helpFvc.edvec.finish() - 1;
      if (newTop > maxY)
         newTop = maxY;
      if (newTop < 1)
         newTop = 1;
      helpFvc.cursorabs(0, newTop);
      helpFvc.placeline(newTop, 0);
      helpPanelView.repaint();
      updateScrollbar();
   }

   /**
    * Scroll the help panel to an absolute line number.
    * Called from the AWT scrollbar adjustment listener.
    *
    * @param line the target line number (1-based)
    */
   public static void scrollHelpToLine(int line) {
      if (helpFvc == null || helpPanelView == null)
         return;
      int maxY = helpFvc.edvec.finish() - 1;
      int newY = line;
      if (newY > maxY)
         newY = maxY;
      if (newY < 1)
         newY = 1;
      helpFvc.cursorabs(0, newY);
      helpFvc.placeline(newY, 0);
      helpPanelView.repaint();
   }

   /**
    * Update the help scrollbar to reflect current position.
    */
   static void updateScrollbar() {
      if (helpFvc == null || helpPanelView == null)
         return;
      int maxY = helpFvc.edvec.finish();
      int visRows = helpPanelView.getRows(1.0f);
      if (visRows <= 1)
         visRows = 20;
      int curTop = helpPanelView.screenFirstLine();
      UI.updateHelpScrollbar(curTop, maxY, visRows);
   }

   /**
    * Called when a sub-mode is entered (e.g. 'f' for find-char,
    * 'Z' for ZZ exit, ':' for ex commands). Shows a brief help
    * for the pending keystroke.
    *
    * @param subMode short description of the sub-mode
    */
   public static void onSubModeEntered(String subMode) {
      if (helpPanelView == null)
         return;
      inSubMode = true;
      getSubModeHelp(subMode);
      if (helpFvc != null)
         helpFvc.cursorabs(0, 1);
      helpPanelView.repaint();
      updateScrollbar();
   }

   /**
    * Generate context-sensitive help for the current mode.
    *
    * @param fvc the current file-view context
    * @return TextEdit buffer with context-appropriate help
    */
   static TextEdit<String> getContextHelp(FvContext fvc) {
      helpBuf.ensure();
      helpBuf.clear();

      if (fvc != null && fvc.edvec instanceof Vt100) {
         appendShellModeHelp();
      } else if (fvc != null && fvc.vi != null
            && fvc.vi.isInInsertMode()) {
         appendInsertModeHelp();
      } else {
         appendNormalModeHelp(fvc);
      }

      return helpBuf.getBuffer();
   }

   /**
    * Generate normal-mode help from registered keybindings.
    *
    * @param fvc the current file-view context (for keymap
    *            resolution)
    * @return TextEdit buffer with keybinding help
    */
   static TextEdit<String> getNormalModeHelp(FvContext fvc) {
      helpBuf.ensure();
      helpBuf.clear();
      appendNormalModeHelp(fvc);
      return helpBuf.getBuffer();
   }

   /**
    * Generate ex-mode command help with syntax and regex
    * explanations.
    *
    * @return TextEdit buffer with ex-command help
    */
   static TextEdit<String> getExModeHelp() {
      helpBuf.ensure();
      helpBuf.clear();
      appendExModeHelp();
      return helpBuf.getBuffer();
   }

   private static void appendNormalModeHelp(FvContext fvc) {
      KeyMap active = resolveKeyMap(fvc);

      append("CONTEXT-SENSITIVE HELP: Normal Mode");
      append("====================================");
      append("");

      if (active == null) {
         append("  (key bindings not initialized)");
         append("");
      } else {
         appendKeymapInfo(active);
         // When an overlay is active, show overlay-specific bindings
         // first (they're the most relevant context).
         if (active.getParent() != null) {
            appendOverrideBindings(active);
         }
         appendMovementBindings(active);
         appendEditBindings(active);
      }

      appendSeeAlso("movement", "editing", "search");
   }

   private static KeyMap resolveKeyMap(FvContext fvc) {
      if (fvc != null) {
         KeyMap km = MapEvent.getActiveKeyMap(fvc);
         if (km != null)
            return km;
      }
      return MapEvent.getNormalKeyMap();
   }

   private static void appendKeymapInfo(KeyMap active) {
      StringBuilder chain = new StringBuilder("Active keymap: ");
      chain.append(active.getName());
      KeyMap p = active.getParent();
      while (p != null) {
         chain.append(" -> ").append(p.getName());
         p = p.getParent();
      }
      append(chain.toString());
      append("");
   }

   /**
    * Emit combined overlay bindings (move + edit) as a single
    * prominent section at the top of the help screen, before
    * the inherited base bindings.
    */
   private static void appendOverrideBindings(KeyMap active) {
      List<String[]> moveOverrides =
         active.getMoveKeys().getBindingEntries();
      List<String[]> editOverrides =
         active.getEditKeys().getBindingEntries();
      if (moveOverrides.isEmpty() && editOverrides.isEmpty())
         return;
      append(active.getName().toUpperCase()
         + " KEYS (overrides)");
      append("-".repeat(active.getName().length() + 17));
      if (!moveOverrides.isEmpty())
         appendDescribedEntries(moveOverrides);
      if (!editOverrides.isEmpty())
         appendDescribedEntries(editOverrides);
      append("");
   }

   private static void appendMovementBindings(KeyMap active) {
      append("MOVEMENT KEYS");
      append("-------------");

      if (active.getParent() != null) {
         // Overlay keys shown in the override section above;
         // show only inherited keys here.
         appendDescribedEntries(
            active.getParent().getMoveKeys()
               .getBindingEntries());
      } else {
         appendDescribedEntries(
            active.getMoveKeys().getBindingEntries());
      }
      append("");
   }

   private static void appendEditBindings(KeyMap active) {
      append("COMMAND/EDIT KEYS");
      append("-----------------");

      if (active.getParent() != null) {
         // Overlay keys shown in the override section above;
         // show only inherited keys here.
         appendDescribedEntries(
            active.getParent().getEditKeys()
               .getBindingEntries());
      } else {
         appendDescribedEntries(
            active.getEditKeys().getBindingEntries());
      }
      append("");
   }

   /**
    * Format binding entries with aligned columns and descriptions.
    */
   private static void appendDescribedEntries(
         List<String[]> entries) {
      for (String[] entry : entries) {
         String key = entry[0];
         String cmd = entry[1];
         String desc = Rgroup.getDescription(cmd);
         if (desc != null) {
            append(String.format("  %-16s %-20s %s",
               key, cmd, desc));
         } else {
            append(String.format("  %-16s %s", key, cmd));
         }
      }
   }

   private static void appendExCommandSummary() {
      FvContext<?> fvc = FvContext.getCurrFvc();
      String bufName = (fvc != null && fvc.edvec != null)
         ? fvc.edvec.fdes().getShortName() : "";
      boolean isGitContext = bufName.startsWith("*git-");

      Set<String> cmds = Rgroup.getRegisteredCommands();
      TreeSet<String> sorted = new TreeSet<>(cmds);

      if (isGitContext) {
         append("GIT COMMANDS (type : to enter)");
         append("-----------------------------");
         for (String cmd : sorted) {
            if (cmd.startsWith("git"))
               appendExSummaryEntry(cmd);
         }
         append("");
         append("OTHER EX COMMANDS");
         append("-----------------");
         for (String cmd : sorted) {
            if (!cmd.isEmpty() && !cmd.startsWith("xxx")
                  && !cmd.startsWith("git"))
               appendExSummaryEntry(cmd);
         }
      } else {
         append("EX COMMANDS (type : to enter)");
         append("----------------------------");
         for (String cmd : sorted) {
            if (!cmd.isEmpty() && !cmd.startsWith("xxx"))
               appendExSummaryEntry(cmd);
         }
      }
      append("");
   }

   private static void appendExSummaryEntry(String cmd) {
      String desc = Rgroup.getDescription(cmd);
      if (desc != null) {
         append(String.format("  :%-18s %s", cmd, desc));
      } else {
         append("  :" + cmd);
      }
   }

   private static void appendCommandprocHelp() {
      FvContext<?> fvc = FvContext.getCurrFvc();
      String bufName = (fvc != null && fvc.edvec != null)
         ? fvc.edvec.fdes().getShortName() : "";
      boolean isGitContext = bufName.startsWith("*git-");

      if (isGitContext) {
         append("EX (COLON) COMMANDS — Git Context");
         append("==================================");
         append("");
         appendExCommandList();
         append("");
         appendGenericExHelp();
      } else {
         append("EX (COLON) COMMANDS");
         append("===================");
         append("");
         appendGenericExHelp();
         appendExCommandList();
      }
   }

   private static void appendGenericExHelp() {
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
      append("  :%               Entire file (1,$)");
      append("");
      append("RANGE COMMANDS");
      append("--------------");
      append("  :<range>d        Delete lines");
      append("  :<range>y        Yank (copy) lines");
      append("  :<range>m<n>     Move lines after line n");
      append("  :<range>t<n>     Copy lines after line n");
      append("");
      append("GLOBAL COMMANDS");
      append("---------------");
      append("  :g/<pattern>/d   Delete matching lines");
      append("  :v/<pattern>/d   Delete non-matching");
      append("");
      append("OTHER COMMANDS");
      append("--------------");
      append("  :set <opt>=<val> Set option");
      append("  :tabstop <n>     Set tab width");
      append("  :help            Show help");
      append("  :mk              Run make");
      append("  :!<cmd>          Run shell command");
      append("");
   }

   private static void appendExModeHelp() {
      append("CONTEXT-SENSITIVE HELP: Ex Commands");
      append("====================================");
      append("");
      appendExSyntaxHelp();
      appendExCommandList();
   }

   private static void appendExSyntaxHelp() {
      append("EX COMMAND SYNTAX");
      append("-----------------");
      append("  :[range]command[!] [args]");
      append("");
      append("RANGES");
      append("------");
      append("  .           Current line");
      append("  $           Last line");
      append("  %           Entire file (alias for 1,$)");
      append("  n           Line number n");
      append("  n,m         Lines n through m");
      append("  '<mark>     Line of mark");
      append("");
      append("SEARCH/REPLACE (regex-based)");
      append("---------------------------");
      append("  :[range]s/pattern/replacement/[flags]");
      append("");
      append("  pattern is a Java regex:");
      append("    .         Any single character");
      append("    \\w        Word char: [a-zA-Z0-9_]");
      append("    \\d        Digit: [0-9]");
      append("    \\s        Whitespace: [ \\t\\n\\r\\f]");
      append("    ^         Start of line");
      append("    $         End of line");
      append("    [abc]     Character class");
      append("    [^abc]    Negated class");
      append("    (x|y)     Alternation");
      append("    (group)   Capture group");
      append("    x*        Zero or more");
      append("    x+        One or more");
      append("    x?        Zero or one");
      append("    x{n,m}    Between n and m");
      append("");
      append("  replacement:");
      append("    $1, $2    Backreference to capture group");
      append("    \\\\        Literal backslash");
      append("");
      append("  flags:");
      append("    g         Global (all occurrences on line)");
      append("    (none)    First occurrence only");
      append("");
      append("GLOBAL COMMANDS (regex-based)");
      append("----------------------------");
      append("  :g/pattern/command    Execute on matching lines");
      append("  :v/pattern/command    Execute on non-matching");
      append("");
   }

   private static void appendExCommandList() {
      FvContext<?> fvc = FvContext.getCurrFvc();
      String bufName = (fvc != null && fvc.edvec != null)
         ? fvc.edvec.fdes().getShortName() : "";
      boolean isGitContext = bufName.startsWith("*git-");

      Set<String> cmds = Rgroup.getRegisteredCommands();
      TreeSet<String> sorted = new TreeSet<>(cmds);

      // In git context, show git commands first
      if (isGitContext) {
         append("GIT COMMANDS");
         append("------------");
         for (String cmd : sorted) {
            if (cmd.startsWith("git")) {
               appendExCommandEntry(cmd);
            }
         }
         append("");
         append("OTHER EX COMMANDS");
         append("-----------------");
         for (String cmd : sorted) {
            if (!cmd.isEmpty() && !cmd.startsWith("xxx")
                  && !cmd.startsWith("git")) {
               appendExCommandEntry(cmd);
            }
         }
      } else {
         append("REGISTERED EX COMMANDS");
         append("----------------------");
         for (String cmd : sorted) {
            if (!cmd.isEmpty() && !cmd.startsWith("xxx")) {
               appendExCommandEntry(cmd);
            }
         }
      }
      append("");
      append("Type :help <topic> for detailed topic help.");
      append("");
      appendSeeAlso("ex", "search");
   }

   private static void appendExCommandEntry(String cmd) {
      String desc = Rgroup.getDescription(cmd);
      if (desc != null) {
         append(String.format("  :%-18s %s", cmd, desc));
      } else {
         append("  :" + cmd);
      }
   }

   private static void appendShellModeHelp() {
      append("CONTEXT-SENSITIVE HELP: Shell Mode");
      append("===================================");
      append("");
      append("SHELL KEYBINDINGS");
      append("-----------------");

      KeyMap shellMap = KeyMap.get("shell");
      if (shellMap != null) {
         List<String> editBindings =
            shellMap.getEditKeys().getBindingList();
         if (!editBindings.isEmpty()) {
            append("  Shell overlay bindings:");
            for (String b : editBindings)
               append(b);
         }
         append("");
         if (shellMap.getParent() != null) {
            append("  Inherited from "
               + shellMap.getParent().getName() + ":");
            List<String> parentEdits =
               shellMap.getParent().getEditKeys()
                  .getBindingList();
            for (String b : parentEdits)
               append(b);
         }
      } else {
         append("  (shell keymap not initialized)");
      }
      append("");
      append("SHELL COMMANDS");
      append("--------------");
      append("  :shell            Open/toggle shell");
      append("  :shell <host>     SSH to host");
      append("  :shells           List active shells");
      append("  :shellclose       Close current shell");
      append("  :shellnext        Next shell");
      append("  :shellprev        Previous shell");
      append("  :shellname <n>    Rename shell");
      append("  :shellenv K=V     Set env variable");
      append("  :shellhistory     Full scrollback");
      append("");
      append("  F8 (in shell)     Enter passthrough");
      append("  F8 (passthrough)  Exit passthrough");
      append("");
      appendSeeAlso("shell");
   }

   private static void appendInsertModeHelp() {
      append("CONTEXT-SENSITIVE HELP: Insert Mode");
      append("====================================");
      append("");
      append("You are in INSERT MODE.");
      append("Characters you type are inserted into");
      append("the buffer at the cursor position.");
      append("");
      append("KEYS");
      append("----");
      append("  ESC             Exit insert mode");
      append("  Backspace       Delete char before cursor");
      append("  Delete          Delete char at cursor");
      append("  Tab             Insert tab / indent");
      append("  Enter           Insert new line");
      append("  Insert          Toggle overwrite mode");
      append("  ^V              Insert literal character");
      append("  ^P              Paste from buffer");
      append("  ^L              Redraw screen");
   }

   /**
    * Append "See also" references to HelpSystem static topics.
    */
   private static void appendSeeAlso(String... topics) {
      StringBuilder sb = new StringBuilder("See also:");
      for (String topic : topics)
         sb.append(" :help ").append(topic).append(',');
      sb.setLength(sb.length() - 1);
      append(sb.toString());
   }

   private static void append(String line) {
      helpBuf.append(line);
   }

   /**
    * Get the context help buffer (for testing).
    */
   static TextEdit<String> getBuffer() {
      return helpBuf.getBuffer();
   }

   /**
    * Generate sub-mode help for a pending keystroke.
    * Unlike {@link #onSubModeEntered}, this does not require
    * the help panel to be visible and is suitable for testing.
    *
    * @param subMode the sub-mode key (e.g. "findchar",
    *                "Zprocess", "commandproc")
    * @return TextEdit buffer with sub-mode help content
    */
   static TextEdit<String> getSubModeHelp(String subMode) {
      helpBuf.ensure();
      helpBuf.clear();
      switch (subMode) {
         case "commandproc":
            appendCommandprocHelp();
            break;
         case "searchcommand":
            append("SEARCH");
            append("======");
            append("");
            append("Type a search pattern (Java regex)");
            append("and press Enter.");
            append("");
            append("  ESC    cancel search");
            break;
         default:
            append("PENDING: " + subMode);
            append("========================");
            append("");
            appendSubModeContent(subMode);
      }
      return helpBuf.getBuffer();
   }

   private static void appendSubModeContent(String subMode) {
      switch (subMode) {
         case "searchcommand":
            appendSearchSubMode();
            break;
         case "findchar":
            append("Type a character to find on the");
            append("current line.");
            append("");
            append("  f<c>   find char forward");
            append("  F<c>   find char backward");
            append("  t<c>   till char forward");
            append("  T<c>   till char backward");
            append("");
            append("  ESC    cancel");
            break;
         case "Zprocess":
            append("Type Z to save and exit file,");
            append("or any other key to cancel.");
            append("");
            append("  ZZ     save and exit file");
            break;
         case "zprocess":
            appendZprocessSubMode();
            break;
         case "replacechar":
            append("Type a replacement character.");
            append("");
            append("  r<c>   replace char under cursor");
            append("  ESC    cancel");
            break;
         case "deletemode":
            appendDeleteSubMode();
            break;
         case "changemode":
            appendChangeSubMode();
            break;
         case "yankmode":
            appendYankSubMode();
            break;
         case "shiftmode":
            append("Type a motion or direction:");
            append("");
            append("  >>     shift line right");
            append("  <<     shift line left");
            append("  >{motion}  shift over motion");
            append("");
            append("  ESC    cancel");
            break;
         case "markset":
            append("Type a register letter (a-z)");
            append("to set a mark at this position.");
            append("");
            append("  m<a-z>   set mark");
            break;
         case "markjump":
            append("Type a register letter (a-z)");
            append("to jump to a mark.");
            append("");
            append("  '<a-z>   jump to mark line");
            append("  `<a-z>   jump to mark position");
            break;
         case "markmode":
            appendVisualSubMode();
            break;
         default:
            append("  Waiting for input...");
      }
   }

   private static void appendSearchSubMode() {
      append("Type a search pattern and press Enter.");
      append("");
      append("SEARCH (current command)");
      append("------------------------");
      append("  /pattern         search forward");
      append("  ?pattern         search backward");
      append("  /pattern/i       case-insensitive");
      append("  /pattern/v       literal (no regex)");
      append("");
      append("SEARCH NAVIGATION");
      append("-----------------");
      append("  n                next match forward");
      append("  N                next match backward");
      append("  *                search word under cursor");
      append("  #                search word backward");
      append("");
      append("SEARCH MODIFIERS (after closing /)");
      append("----------------------------------");
      append("  /pat/i           ignore case");
      append("  /pat/v           literal match");
      append("  /pat/e           cursor at end");
      append("  /pat/b           cursor at beginning");
      append("  /pat/+n          offset n lines down");
      append("  /pat/-n          offset n lines up");
      append("");
      append("  ESC              cancel search");
   }

   private static void appendZprocessSubMode() {
      append("Type a screen-position or fold");
      append("command:");
      append("");
      append("SCREEN POSITION");
      append("---------------");
      append("  z<CR>    cursor line to top");
      append("  z.       cursor line to center");
      append("  z-       cursor line to bottom");
      append("  z<n><CR> set window height to n");
      append("");
      append("FOLD COMMANDS");
      append("-------------");
      append("  zo       open fold at cursor");
      append("  zc       close fold at cursor");
      append("  za       toggle fold at cursor");
      append("  zR       open all folds");
      append("  zM       close all folds");
   }

   private static void appendDeleteSubMode() {
      append("Type a motion or operator:");
      append("");
      append("  dd     delete entire line");
      append("  dw     delete to next word");
      append("  dW     delete to next WORD");
      append("  db     delete to prev word");
      append("  de     delete to end of word");
      append("  d$     delete to end of line");
      append("  d0     delete to start of line");
      append("  d^     delete to first non-blank");
      append("  df<c>  delete to char <c> forward");
      append("  dt<c>  delete till char <c>");
      append("  d/pat  delete to search match");
      append("  dG     delete to end of file");
      append("  d'<m>  delete to mark <m>");
      append("");
      append("  ESC    cancel");
   }

   private static void appendChangeSubMode() {
      append("Type a motion or operator:");
      append("");
      append("  cc     change entire line");
      append("  cw     change to next word");
      append("  cW     change to next WORD");
      append("  cb     change to prev word");
      append("  ce     change to end of word");
      append("  c$     change to end of line");
      append("  c0     change to start of line");
      append("  c^     change to first non-blank");
      append("  cf<c>  change to char <c> forward");
      append("  ct<c>  change till char <c>");
      append("");
      append("  ESC    cancel");
   }

   private static void appendYankSubMode() {
      append("Type a motion or operator:");
      append("");
      append("  yy     yank entire line");
      append("  yw     yank to next word");
      append("  yW     yank to next WORD");
      append("  yb     yank to prev word");
      append("  ye     yank to end of word");
      append("  y$     yank to end of line");
      append("  y0     yank to start of line");
      append("  y^     yank to first non-blank");
      append("  yf<c>  yank to char <c> forward");
      append("  yt<c>  yank till char <c>");
      append("  y/pat  yank to search match");
      append("  yG     yank to end of file");
      append("  y'<m>  yank to mark <m>");
   }

   private static void appendVisualSubMode() {
      append("VISUAL SELECTION COMMANDS");
      append("------------------------");
      append("  Use movement keys to extend");
      append("  selection, then:");
      append("");
      append("  d        delete selection");
      append("  y        yank (copy) selection");
      append("  Y        yank lines");
      append("  D        delete lines");
      append("  ~        toggle case");
      append("  J        join lines");
      append("  >        shift right (indent)");
      append("  <        shift left (unindent)");
      append("  o        swap to other end");
      append("  s/S      search selection");
      append("  C/F      format selection");
      append("  !        evaluate as script");
      append("");
      append("  v/V/ESC  exit visual mode");
      append("");
      append("MOVEMENT (extends selection)");
      append("---------------------------");
      append("  h/l      char left/right");
      append("  j/k      line down/up");
      append("  w/b      word forward/backward");
      append("  e        end of word");
      append("  0/$      start/end of line");
      append("  ^        first non-blank");
      append("  G        go to line");
      append("  f/F<c>   find char fwd/back");
      append("  /        search forward");
      append("  ?        search backward");
   }

   /**
    * Get the command description for a command name (for testing).
    *
    * @param commandName the internal command name
    * @return human-readable description, or null if none
    */
   static String getDescription(String commandName) {
      return Rgroup.getDescription(commandName);
   }
}
