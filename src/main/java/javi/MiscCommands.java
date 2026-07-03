package javi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import history.Tools;
import static history.Tools.trace;

public final class MiscCommands extends Rgroup {
   enum Cmd {
      UNUSED,      // 0
      XXX_UNUSED,  // 1: free slot
      Z_PROCESS,   // 2: z-position scroll
      REDRAW,      // 3: redraw screen
      UNDO,        // 4: undo (u)
      REDO,        // 5: redo (^R)
      UNDO_LINE,   // 6: undo line (U)
      VT,          // 7: start shell
      LOAD_GROUP,  // 8: load group
      COMM,        // 9: start com
      EXEC,        // 10: execute command
      LINES,       // 11: set height
      SET_WIDTH,   // 12: set width
      SHELLS,      // 13: list shells
      SHELL_CLOSE, // 14: close shell
      SHELL_NEXT,  // 15: next shell
      SHELL_PREV,  // 16: prev shell
      SHELL_NAME,  // 17: rename shell
      SHELL_ENV,   // 18: set shell env var
      SHELL_HISTORY, // 19: show shell output history
      SHELL_NEW,   // 20: create new shell
      MAP_KEY,     // 21: :mapkey command
      UNMAP_KEY,   // 22: :unmapkey command
      SHOW_KEYMAP, // 23: :keymap - show current keymap
      SAVE_MAP_KEYS, // 24: :savemapkeys - persist user bindings
      LOAD_MAP_KEYS, // 25: :loadmapkeys - load user bindings
      FOLD,          // 26: :fold - detect/manage folds
      FOLD_INDENT,   // 27: :foldindent - indent-based folds
      FOLD_MARKER,   // 28: :foldmarker - marker-based folds
      LOAD_PLUGIN,   // 29: :loadplugin - load a plugin JAR
      LOAD_CLASS,    // 30: :loadclass - load a class by name (plugin)
      CONTEXT_HELP,  // 31: toggle context help panel
      HEAP_DUMP,     // 32: :heapdump - write HPROF heap dump
      TERM_LOG,      // 33: :termlog - toggle terminal I/O logging
   }

   private static final Cmd[] CMDS = Cmd.values();

   MiscCommands() {
      final String[] rnames = {
         "",
         "xxxunused",
         null,               // 2: zprocess — via registerCommand
         null,               // 3: redraw — via registerCommand
         null,               // 4: undo — via registerCommand
         null,               // 5: redo — via registerCommand
         null,               // 6: undoline — via registerCommand
         null,               // 7: vt — via registerArgCommand
         null,               // 8: loadgroup — via registerArgCommand
         null,               // 9: comm — moved to RXTX plugin
         null,               // 10: exec — via registerArgCommand
         null,               // 11: lines — via registerArgCommand
         null,               // 12: setwidth — via registerArgCommand
         null,               // 13: shells — via registerCommand
         null,               // 14: shellclose — via registerArgCommand
         null,               // 15: shellnext — via registerCommand
         null,               // 16: shellprev — via registerCommand
         null,               // 17: shellname — via registerArgCommand
         null,               // 18: shellenv — via registerArgCommand
         null,               // 19: shellhistory — via registerCommand
         null,               // 20: shellnew — via registerArgCommand
         null,               // 21: mapkey — via registerArgCommand
         null,               // 22: unmapkey — via registerArgCommand
         null,               // 23: keymap — via registerCommand
         null,               // 24: savemapkeys — via registerCommand
         null,               // 25: loadmapkeys — via registerCommand
         null,               // 26: fold — via registerArgCommand
         null,               // 27: foldindent — via registerArgCommand
         null,               // 28: foldmarker — via registerCommand
         null,               // 29: loadplugin — via registerArgCommand
         "loadclass",        // 30
         null,               // 31: contexthelp — via registerCommand
         "heapdump",         // 32
      };
      final String[] descs = {
         null,
         null,
         null,               // 2: migrated
         null,               // 3: migrated
         null,               // 4: migrated
         null,               // 5: migrated
         null,               // 6: migrated
         null,               // 7: migrated
         null,               // 8: migrated
         null,               // 9: migrated
         null,               // 10: migrated
         null,               // 11: migrated
         null,               // 12: migrated
         null,               // 13: migrated
         null,               // 14: migrated
         null,               // 15: migrated
         null,               // 16: migrated
         null,               // 17: migrated
         null,               // 18: migrated
         null,               // 19: migrated
         null,               // 20: migrated
         null,               // 21: migrated
         null,               // 22: migrated
         null,               // 23: migrated
         null,               // 24: migrated
         null,               // 25: migrated
         null,               // 26: migrated
         null,               // 27: migrated
         null,               // 28: migrated
         null,               // 29: migrated
         null,               // 30: migrated
      };
      register(rnames, descs);

      registerNoArgCommands();
      registerArgCommands();
   }

   /** Phase 2b-2: arg-free commands with closure handlers. */
   private void registerNoArgCommands() {
      registerCommand(new CommandEntry("zprocess",
         "scroll screen to position", "screen",
         (count, rcount, fvc, dot) -> {
            zprocess(rcount, fvc); return null;
         }));
      registerCommand(new CommandEntry("redraw",
         "redraw screen", "screen",
         (count, rcount, fvc, dot) -> {
            redraw(true); return null;
         }));
      registerCommand(new CommandEntry("undo",
         "undo last change", "edit",
         (count, rcount, fvc, dot) -> {
            undo(fvc); return null;
         }));
      registerCommand(new CommandEntry("redo",
         "redo undone change", "edit",
         (count, rcount, fvc, dot) -> {
            redo(fvc); return null;
         }));
      registerCommand(new CommandEntry("undoline",
         "undo entire line", "edit",
         (count, rcount, fvc, dot) -> null));
      registerCommand(new CommandEntry("shells",
         "list active shells", "shell",
         (count, rcount, fvc, dot) -> {
            listShells(fvc); return null;
         }));
      registerCommand(new CommandEntry("shellnext",
         "next shell", "shell",
         (count, rcount, fvc, dot) -> {
            nextShell(fvc); return null;
         }));
      registerCommand(new CommandEntry("shellprev",
         "previous shell", "shell",
         (count, rcount, fvc, dot) -> {
            prevShell(fvc); return null;
         }));
      registerCommand(new CommandEntry("shellhistory",
         "show shell scrollback", "shell",
         (count, rcount, fvc, dot) -> {
            showShellHistory(fvc); return null;
         }));
      registerCommand(new CommandEntry("keymap",
         "show current keymap", "keymap",
         (count, rcount, fvc, dot) -> {
            showKeyMap(fvc); return null;
         }));
      registerCommand(new CommandEntry("savemapkeys",
         "save user key bindings", "keymap",
         (count, rcount, fvc, dot) -> {
            saveMapKeys(); return null;
         }));
      registerCommand(new CommandEntry("loadmapkeys",
         "load user key bindings", "keymap",
         (count, rcount, fvc, dot) -> {
            loadMapKeys(); return null;
         }));
      registerCommand(new CommandEntry("foldmarker",
         "marker-based folds", "fold",
         (count, rcount, fvc, dot) -> {
            doFoldMarker(fvc); return null;
         }));
      registerCommand(new CommandEntry("contexthelp",
         "toggle help panel (Shift-F1)", "help",
         (count, rcount, fvc, dot) -> {
            ContextHelp.toggle(fvc); return null;
         }));
      registerCommand(new CommandEntry("helpscrolldown",
         "scroll help panel down", "help",
         (count, rcount, fvc, dot) -> {
            ContextHelp.scrollHelpDown(); return null;
         }));
      registerCommand(new CommandEntry("helpscrollup",
         "scroll help panel up", "help",
         (count, rcount, fvc, dot) -> {
            ContextHelp.scrollHelpUp(); return null;
         }));
      registerCommand(new CommandEntry("check_external",
         "check if file changed on disk", "file",
         (count, rcount, fvc, dot) -> {
            FileList.checkCurrentFileExternal(fvc);
            return null;
         }));
      registerCommand(new CommandEntry("registers",
         "display register contents", "edit",
         (count, rcount, fvc, dot) -> {
            showRegisters(fvc); return null;
         }));
   }

   /** Phase 2b-3: arg-dependent commands with closure handlers. */
   private void registerArgCommands() {
      registerArgCommand("vt",
         "open/toggle shell (F8)", "shell",
         (arg, count, rcount, fvc, dot) -> {
            startshell(fvc,
               arg instanceof String ? (String) arg : null);
            return null;
         });
      registerArgCommand("loadgroup",
         "load command group", "misc",
         (arg, count, rcount, fvc, dot) -> {
            loadgroup(fvc.edvec.getName(),
               arg instanceof String ? (String) arg : null);
            return null;
         });
      registerArgCommand("exec",
         "execute external command", "shell",
         (arg, count, rcount, fvc, dot) -> {
            startcmd(
               arg instanceof String ? (String) arg : null,
               fvc);
            return null;
         });
      registerArgCommand("awt.lines",
         "set window height", "display",
         (arg, count, rcount, fvc, dot) -> {
            defheight = oBToInt(arg); return null;
         });
      registerArgCommand("awt.setwidth",
         "set window width", "display",
         (arg, count, rcount, fvc, dot) -> {
            defwidth = oBToInt(arg); return null;
         });
      registerArgCommand("shellclose",
         "close current shell", "shell",
         (arg, count, rcount, fvc, dot) -> {
            closeShell(fvc,
               arg instanceof String ? (String) arg : null);
            return null;
         });
      registerArgCommand("shellname",
         "rename shell", "shell",
         (arg, count, rcount, fvc, dot) -> {
            renameShell(fvc,
               arg instanceof String ? (String) arg : null);
            return null;
         });
      registerArgCommand("shellenv",
         "set shell env variable", "shell",
         (arg, count, rcount, fvc, dot) -> {
            setShellEnv(fvc,
               arg instanceof String ? (String) arg : null);
            return null;
         });
      registerArgCommand("shellnew",
         "create new shell", "shell",
         (arg, count, rcount, fvc, dot) -> {
            newShellCommand(fvc,
               arg instanceof String ? (String) arg : null);
            return null;
         });
      registerArgCommand("mapkey",
         "bind a key to command", "keymap",
         (arg, count, rcount, fvc, dot) -> {
            doMap(
               arg instanceof String ? (String) arg : null);
            return null;
         });
      registerArgCommand("unmapkey",
         "unbind a key", "keymap",
         (arg, count, rcount, fvc, dot) -> {
            doUnmap(
               arg instanceof String ? (String) arg : null);
            return null;
         });
      registerArgCommand("fold",
         "detect/manage folds", "fold",
         (arg, count, rcount, fvc, dot) -> {
            doFold(fvc,
               arg instanceof String ? (String) arg : null);
            return null;
         });
      registerArgCommand("foldindent",
         "indent-based folds", "fold",
         (arg, count, rcount, fvc, dot) -> {
            doFoldIndent(fvc,
               arg instanceof String ? (String) arg : null);
            return null;
         });
      registerArgCommand("loadplugin",
         "load a plugin JAR", "misc",
         (arg, count, rcount, fvc, dot) -> {

            return switch (arg) {
               case String arg1 -> {
                  List<String> args = new ArrayList<>(List.of(arg1.split(" ")));
                  var pname = args.remove(0);
                  Plugin.load(pname, args);
                  yield null;
               }
               case Object ob -> null;
               case null -> null;
            };
         });
      registerArgCommand("termlog",
         "toggle terminal I/O logging", "shell",
         (arg, count, rcount, fvc, dot) -> {
            doTermLog(fvc,
               arg instanceof String ? (String) arg : null);
            return null;
         });
      registerArgCommand("gid",
         "search ID database for exact identifier", "navigation",
         (arg, count, rcount, fvc, dot) -> {
            String pattern = arg instanceof String ? (String) arg : null;
            if (pattern == null || pattern.isEmpty())
               throw new InputException("gid requires a pattern argument");
            MkidSearch.gidSearch(pattern);
            return null;
         });
      registerArgCommand("lid",
         "search ID database for identifier pattern", "navigation",
         (arg, count, rcount, fvc, dot) -> {
            String pattern = arg instanceof String ? (String) arg : null;
            if (pattern == null || pattern.isEmpty())
               throw new InputException("lid requires a pattern argument");
            MkidSearch.lidSearch(pattern);
            return null;
         });
   }

   private static TextEdit debugfile;
   private static TextEdit cmdfile;
   private static volatile int defwidth = 80;
   private static volatile int defheight = 80;
   /** Set to true after the first real OldView layout provides actual dimensions. */
   private static volatile boolean layoutComplete;

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws IOException, InputException {
      // trace("rnum = " + rnum );
      switch (CMDS[rnum]) {
         case XXX_UNUSED:
            // free
            return null;
         case LOAD_CLASS:
            loadClass(arg instanceof String ? (String) arg : null);
            return null;
         case HEAP_DUMP:
            doHeapDump(fvc);
            return null;

         default:
            throw new RuntimeException("vigroup:default");
      }

      // trace("end ");
   }

   public static int getHeight() {
      return defheight;
   }

   public static int getWidth() {
      return defwidth;
   }

   /**
    * Updates the default screen dimensions from actual window metrics.
    * Called by OldView when the canvas is resized so that new shells
    * inherit the correct column/row counts.
    *
    * @param rows number of character rows
    * @param cols number of character columns
    */
   public static void updateScreenDimensions(int rows, int cols) {
      if (rows > 0)
         defheight = rows;
      if (cols > 0)
         defwidth = cols;
      layoutComplete = true;
   }

   /** Returns true once updateScreenDimensions has been called with real values. */
   public static boolean isLayoutComplete() {
      return layoutComplete;
   }

   private static final class MyFl implements EditContainer.FileStatusListener {

      public void fileAdded(EditContainer ev) {
      }

      public void fileWritten(EditContainer ev) {
      }

      public boolean fileDisposed(EditContainer ev) {
         if (ev == cmdfile)
            cmdfile = null;
         if (ev == debugfile)
            debugfile = null;
         // Clean up shell session when its buffer is closed (e.g., via ZZ)
         ShellManager mgr = ShellManager.getInstance();
         if (mgr.closeByBuffer(ev))
            refreshShellPositionList(mgr);
         return false;
      }
   }

   static final MyFl fli = new MyFl();

   private static void undo(FvContext fvc) throws IOException {
      int index = fvc.edvec.undo();
      if (index != -1)
         fvc.cursoryabs(index);
      fvc.fixCursor();
   }

   private static void redo(FvContext fvc) throws IOException {
      int index = fvc.edvec.redo();
      if (index != -1)
         fvc.cursoryabs(index);
      fvc.fixCursor();
   }

   private void startshell(FvContext fvc, String host)
      throws IOException, InputException {
      trace("reached startshell " + host);

      // F8 toggle: if already viewing a shell buffer, enter passthrough mode
      if (null == host && fvc.edvec instanceof Vt100) {
         // Sync ShellManager active session to match this buffer
         ShellManager mgr = ShellManager.getInstance();
         ShellSession session = mgr.findByBuffer(fvc.edvec);
         if (null != session)
            mgr.switchToId(session.getId());
         ((Vt100) fvc.edvec).handleKeys(fvc);
         refreshShellPositionList(mgr);
         return;
      }

      ShellManager mgr = ShellManager.getInstance();

      // If no argument and we have an active shell, switch to it
      if (null == host && mgr.getSessionCount() > 0) {
         ShellSession active = mgr.getActive();
         if (null != active) {
            if (!active.isAlive()) {
               // Shell process died — clean up orphaned session
               mgr.closeActiveShell();
            } else {
               FvContext newFvc =
                  FvContext.connectFv(active.getBuffer(), fvc.vi);
               ((Vt100) active.getBuffer()).handleKeys(newFvc);
               refreshShellPositionList(mgr);
               return;
            }
         }
      }

      // Try to parse arg as a session number
      if (null != host) {
         try {
            int shellId = Integer.parseInt(host);
            if (mgr.switchToId(shellId)) {
               ShellSession session = mgr.getActive();
               FvContext newFvc =
                  FvContext.connectFv(session.getBuffer(), fvc.vi);
               ((Vt100) session.getBuffer()).handleKeys(newFvc);
               refreshShellPositionList(mgr);
               return;
            }
            // Fall through to create new shell if not found
         } catch (NumberFormatException e) {
            // Not a number — try as session name
            if (mgr.switchToName(host)) {
               ShellSession session = mgr.getActive();
               FvContext newFvc =
                  FvContext.connectFv(session.getBuffer(), fvc.vi);
               ((Vt100) session.getBuffer()).handleKeys(newFvc);
               refreshShellPositionList(mgr);
               return;
            }
            // Not found by name — treat as SSH hostname
         }
      }

      // Create new shell session
      EditContainer.registerListener(fli);
      ShellSession session = mgr.newShell(host);

      // Auto-register/refresh shell list in F6 position list
      refreshShellPositionList(mgr);

      FvContext newFvc = FvContext.connectFv(session.getBuffer(), fvc.vi);
      session.getVt100().startHandle(newFvc);
      // Sync PTY size to the view's actual dimensions after
      // startHandle has set the Vt100's rows from the view.
      // This prevents the initial-size bug where the PTY has
      // stale dimensions from before the view was connected.
      session.syncPtyToView(newFvc.vi.getRows(1.0f), getWidth());
      ((Vt100) session.getBuffer()).handleKeys(newFvc);
      refreshShellPositionList(mgr);
   }

   /**
    * Lists all active shell sessions.
    *
    * @param fvc the current file-view context
    * @throws IOException if buffer connection fails
    * @throws InputException if connection fails
    */
   private static void listShells(FvContext fvc)
         throws IOException, InputException {
      ShellManager mgr = ShellManager.getInstance();
      if (mgr.getSessionCount() == 0) {
         UI.reportMessage("No active shells");
         return;
      }
      ShellListIoc ioc = new ShellListIoc(mgr);
      TextEdit<Position> list =
         PosListList.Cmd.replacePositionIoc("shells", ioc);
      list.finish(); // wait for data before navigating
      FvContext.connectFv(list, fvc.vi);
   }

   /**
    * Registers or refreshes the shell position list in the F6
    * position list display. Replaces any existing "shells" entry.
    *
    * @param mgr the ShellManager instance
    */
   static void refreshShellPositionList(ShellManager mgr) {
      if (mgr.getSessionCount() == 0) {
         PosListList.Cmd.removePositionIoc("shells");
         return;
      }
      ShellListIoc ioc = new ShellListIoc(mgr);
      PosListList.Cmd.replacePositionIoc("shells", ioc);
   }

   private static final class ShellListIoc extends PositionIoc {
      private static final long serialVersionUID = 1;
      private final transient java.util.List<ShellSession> sessions;
      private final transient int activeId;

      ShellListIoc(ShellManager mgr) {
         super("shells", null, pconverter);
         this.sessions = mgr.getSessions();
         ShellSession active = mgr.getActive();
         this.activeId = null != active ? active.getId() : -1;
      }

      @Override
      void dorun() {
         for (ShellSession s : sessions) {
            s.invalidateLabelCache();
            s.updateLabel();
            String comment = String.format("%s [%s]%s",
               s.getName(),
               s.isAlive() ? "running" : "stopped",
               s.getId() == activeId ? " *" : "");
            addResult(new Position(0, 1,
               s.getBuffer().fdes(), comment));
         }
      }

      @Override
      protected void reportCompletion() {
         // suppress "shells complete N results" status message
      }
   }

   /**
    * Closes the current or specified shell session.
    *
    * @param fvc the current file-view context
    * @param arg optional shell ID to close
    * @throws InputException if shell ID is invalid
    * @throws IOException if buffer connection fails
    */
   private static void closeShell(FvContext fvc, String arg)
         throws InputException, IOException {
      ShellManager mgr = ShellManager.getInstance();

      // Determine target shell ID
      int targetId;
      if (null != arg && !arg.isEmpty()) {
         try {
            targetId = Integer.parseInt(arg);
         } catch (NumberFormatException e) {
            throw new InputException("Invalid shell ID: " + arg, e);
         }
      } else {
         ShellSession active = mgr.getActive();
         if (null == active)
            throw new InputException("No active shell to close");
         targetId = active.getId();
      }

      // Find next shell to switch to (any shell other than the target)
      ShellSession nextShell = null;
      for (ShellSession s : mgr.getSessions()) {
         if (s.getId() != targetId) {
            nextShell = s;
            break;
         }
      }

      // Switch view BEFORE closing to avoid viewing a disposed buffer
      if (null != nextShell) {
         FvContext.connectFv(nextShell.getBuffer(), fvc.vi);
      } else {
         try {
            Rgroup.doCommand("nextfile", null, 0, 0, fvc, false);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      }

      // Now close the shell
      if (!mgr.closeShell(targetId)) {
         throw new InputException("No shell with ID " + targetId);
      }

      // Refresh shell list in F6
      refreshShellPositionList(mgr);

      if (null != nextShell) {
         UI.reportMessage("Shell closed. Switched to shell "
            + nextShell.getId());
      } else {
         UI.reportMessage("All shells closed");
      }
   }

   /**
    * Creates a new shell session (always, never reuses existing).
    *
    * @param fvc the current file-view context
    * @param host optional hostname for SSH, or null for local shell
    * @throws IOException if shell process cannot be started
    * @throws InputException if buffer connection fails
    */
   private static void newShellCommand(FvContext fvc, String host)
         throws IOException, InputException {
      ShellManager mgr = ShellManager.getInstance();
      EditContainer.registerListener(fli);
      ShellSession session = mgr.newShell(host);
      refreshShellPositionList(mgr);
      FvContext newFvc = FvContext.connectFv(session.getBuffer(), fvc.vi);
      // Initialize the Vt100's currfvc so screen updates and scrolling work
      session.getVt100().startHandle(newFvc);
      session.syncPtyToView(newFvc.vi.getRows(1.0f), getWidth());
      UI.reportMessage("Created shell " + session.getId()
         + " (" + session.getName() + ")");
      ((Vt100) session.getBuffer()).handleKeys(newFvc);
   }

   /**
    * Switches to the next shell session.
    *
    * @param fvc the current file-view context
    * @throws IOException if buffer connection fails
    * @throws InputException if connection fails
    */
   private static void nextShell(FvContext fvc) throws IOException, InputException {
      ShellManager mgr = ShellManager.getInstance();
      if (mgr.nextShell()) {
         ShellSession active = mgr.getActive();
         if (null != active) {
            FvContext newFvc = FvContext.connectFv(active.getBuffer(), fvc.vi);
            ((Vt100) active.getBuffer()).handleKeys(newFvc);
         }
      }
   }

   /**
    * Switches to the previous shell session.
    *
    * @param fvc the current file-view context
    * @throws IOException if buffer connection fails
    * @throws InputException if connection fails
    */
   private static void prevShell(FvContext fvc) throws IOException, InputException {
      ShellManager mgr = ShellManager.getInstance();
      if (mgr.previousShell()) {
         ShellSession active = mgr.getActive();
         if (null != active) {
            FvContext newFvc = FvContext.connectFv(active.getBuffer(), fvc.vi);
            ((Vt100) active.getBuffer()).handleKeys(newFvc);
         }
      }
   }

   /**
    * Renames the active shell session.
    *
    * @param fvc the current file-view context
    * @param newName the new name for the shell
    */
   private static void renameShell(FvContext fvc, String newName) {
      if (newName == null || newName.isEmpty()) {
         UI.reportMessage("Usage: :shellname <name>");
         return;
      }
      ShellManager mgr = ShellManager.getInstance();
      ShellSession active = mgr.getActive();
      if (null == active) {
         UI.reportMessage("No active shell to rename");
         return;
      }
      active.setName(newName);
      UI.reportMessage("Shell " + active.getId() + " renamed to: " + newName);
   }

   /**
    * Sets an environment variable for the active shell session.
    *
    * @param fvc the current file-view context
    * @param arg key=value pair
    */
   private static void setShellEnv(FvContext fvc, String arg) {
      if (arg == null || arg.isEmpty() || !arg.contains("=")) {
         UI.reportMessage("Usage: :shellenv <key>=<value>");
         return;
      }
      ShellManager mgr = ShellManager.getInstance();
      ShellSession active = mgr.getActive();
      if (null == active) {
         UI.reportMessage("No active shell");
         return;
      }
      int eq = arg.indexOf('=');
      String key = arg.substring(0, eq).trim();
      String value = arg.substring(eq + 1).trim();
      if (key.isEmpty()) {
         UI.reportMessage("Usage: :shellenv <key>=<value>");
         return;
      }
      active.setEnvVar(key, value);
      UI.reportMessage("Shell " + active.getId() + ": " + key + "=" + value);
   }

   /**
    * Shows the output history of the active shell in a read-only buffer.
    *
    * @param fvc the current file-view context
    * @throws InputException if buffer connection fails
    */
   private static void showShellHistory(FvContext fvc) throws InputException {
      ShellManager mgr = ShellManager.getInstance();
      ShellSession active = mgr.getActive();
      if (null == active) {
         UI.reportMessage("No active shell");
         return;
      }
      TextEdit<String> buffer = active.getBuffer();
      int lineCount = buffer.finish();
      StringBuilder sb = new StringBuilder();
      for (int i = 1; i < lineCount; i++) {
         if (i > 1)
            sb.append('\n');
         sb.append(buffer.at(i).toString());
      }
      String content = sb.toString();
      if (content.isEmpty())
         content = "(no output)";
      StringIoc ioc = new StringIoc(
         "shell-history-" + active.getId() + " (" + active.getName() + ")",
         content);
      TextEdit<String> histBuf = new TextEdit<>(ioc, ioc.prop);
      FvContext.connectFv(histBuf, fvc.vi);
   }

   static final class ProcIo extends BufInIoc<String> {

      private static final long serialVersionUID = 1;
      private transient Process proc;

      static ProcIo mkProcIo(String namei, String... cmd) throws IOException {
         Process proc = Tools.iocmd(cmd);
         BufferedReader input = new BufferedReader(new InputStreamReader(
               proc.getInputStream(), StandardCharsets.UTF_8));
         return new ProcIo(namei, proc, input, cmd);
      }

      private ProcIo(String namei, Process proci,
            BufferedReader inp, String... cmd) {
         super(new FileProperties<>(FileDescriptor.InternalFd.make(namei),
               StringIoc.converter), true, inp);
         proc = proci;
      }

      public String fromString(String str) {
         return str;
      }

      public void dispose() throws IOException {
         proc.destroy();
         super.dispose();
      }

      public String getnext() {
         return getLine();
      }
   };

   private static String[] lastcmd = {
      "bash", "-i", "-c",
      "(cd ../javitests;"
         + " java -Xshare:off javitests.PerfTest )"
   };

   private static void startcmd(String cname, FvContext fvc) {
      // trace("startcmd:" + cname);
      try {
         if (null == cmdfile) {
            // ??? make this workjavac.compcommand(null,true);
            if (null != cname)
               lastcmd[3] = cname;

            EditContainer.registerListener(fli);
            ProcIo pi = ProcIo.mkProcIo(lastcmd[3], lastcmd);
            cmdfile = new TextEdit<String>(pi, pi.prop);
         }
         FvContext.connectFv(cmdfile, fvc.vi);
      } catch (Throwable e) {
         UI.reportError("startCmd failed:" + e);
      }
   }

   private void zprocess(int rcount, FvContext fvc)
         throws InputException {
      ContextHelp.onSubModeEntered("zprocess");
      int scchange = 0;
      float scrpos = 0.f;

      outloop: while (true) {
         int key = EventQueue.nextKey(fvc.vi);
         if (key >= '0' && key <= '9')
            scchange = scchange * 10 + (key & 0x0f);
         else {
            if (0 != scchange) {
               defheight = scchange;
               fvc.vi.setSizebyChar(-1, scchange);
               UI.sizeChange();
            }
            if (0 != rcount)
               fvc.cursoryabs(rcount);
            switch (key) {
               case 10: // return ^m
               case 13: // return ^m
                  break outloop;
               case '.':
               case ',':
                  scrpos = .5f;
                  break outloop;
               case '-':
                  scrpos = .99999f;
                  break outloop;
               case 'o': case 'c': case 'a':
               case 'R': case 'M':
                  handleFoldCommand(key, fvc);
                  fvc.vi.redraw();
                  return;
               default:
                  return;
            }
         }
      }

      fvc.placeline(fvc.inserty(), scrpos);
   }

   private static void handleFoldCommand(
         int key, FvContext fvc) {
      FoldModel fm = fvc.getFoldModel();
      if (key != 'M' && (fm == null || fm.isEmpty())) {
         UI.reportMessage("no folds defined");
         return;
      }
      int line = fvc.inserty();
      switch (key) {
         case 'o':
            FoldModel.FoldToggleHandler oh =
               fm.getToggleHandler();
            if (oh != null) {
               try {
                  if (oh.onToggle(line, fvc))
                     break;
               } catch (IOException | InputException e) {
                  UI.reportMessage(e.getMessage());
                  break;
               }
            }
            FoldModel.FoldRange fo = fm.openFold(line);
            if (fo != null) {
               fvc.vi.recalcScreenRow();
               UI.reportMessage("opened fold at "
                  + fo.startLine);
            } else {
               UI.reportMessage("no fold at line " + line);
            }
            break;
         case 'c':
            FoldModel.FoldRange fc = fm.closeFold(line);
            if (fc != null) {
               if (line > fc.startLine)
                  fvc.cursoryabs(fc.startLine);
               fvc.vi.recalcScreenRow();
               UI.reportMessage("closed fold at "
                  + fc.startLine);
            } else {
               UI.reportMessage("no fold at line " + line);
            }
            break;
         case 'a':
            FoldModel.FoldToggleHandler th =
               fm.getToggleHandler();
            if (th != null) {
               try {
                  if (th.onToggle(line, fvc))
                     break;
               } catch (IOException | InputException e) {
                  UI.reportMessage(e.getMessage());
                  break;
               }
            }
            FoldModel.FoldRange fa = fm.toggleFold(line);
            if (fa != null) {
               if (fa.collapsed && line > fa.startLine)
                  fvc.cursoryabs(fa.startLine);
               fvc.vi.recalcScreenRow();
               UI.reportMessage("toggled fold at "
                  + fa.startLine + " \u2192 "
                  + (fa.collapsed ? "closed" : "open"));
            } else {
               UI.reportMessage("no fold at line " + line);
            }
            break;
         case 'R':
            boolean allOpen = fm.getFolds().stream()
               .noneMatch(f -> f.collapsed);
            if (allOpen) {
               fvc.setFoldModel(null);
               FileDescriptor fd = fvc.edvec.fdes();
               if (fd instanceof FileDescriptor.LocalFile)
                  FoldModel.deleteFoldState(
                     ((FileDescriptor.LocalFile) fd)
                        .canonName);
               UI.reportMessage("cleared all folds");
            } else {
               fm.openAll();
               fvc.vi.recalcScreenRow();
               UI.reportMessage("opened all folds");
            }
            break;
         case 'M':
            // Auto-detect folds for supported file types,
            // then close all. If file type not supported,
            // just close existing folds.
            FoldModel existingFm = fvc.getFoldModel();
            FoldDetector.LineFetcher mFetcher = lineNum ->
               fvc.edvec.at(lineNum).toString();
            int mLineCount = fvc.edvec.readIn();
            String mName = fvc.edvec.getName();
            FoldModel mFm = FoldDetector.detectForFile(
               mName, mFetcher, mLineCount);
            if (mFm == null) {
               if (existingFm == null
                     || existingFm.isEmpty()) {
                  UI.reportMessage("no folds defined");
                  break;
               }
               existingFm.closeAll();
               mFm = existingFm;
            } else {
               mFm.closeAll();
               fvc.setFoldModel(mFm);
            }
            int curLine = line;
            for (FoldModel.FoldRange cf : mFm.getFolds()) {
               if (cf.collapsed && curLine > cf.startLine
                     && curLine <= cf.endLine)
                  curLine = cf.startLine;
            }
            fvc.cursoryabs(curLine);
            fvc.vi.recalcScreenRow();
            UI.reportMessage("recalculated and closed all folds");
            break;
         default:
            break;
      }
      saveFoldState(fvc);
   }

   private static Date lastredraw = new Date();

   private static void doFold(FvContext fvc, String arg) {
      FoldDetector.LineFetcher fetcher = lineNum ->
         fvc.edvec.at(lineNum).toString();
      int lineCount = fvc.edvec.readIn();
      String name = fvc.edvec.getName();
      FoldModel fm = FoldDetector.detectForFile(
         name, fetcher, lineCount);
      if (fm == null) {
         // Fallback: brace detection for unknown types
         fm = FoldDetector.detectJsonFolds(
            fetcher, lineCount);
      }
      fvc.setFoldModel(fm);
      saveFoldState(fvc);
      fvc.vi.recalcScreenRow();
      fvc.vi.redraw();
      UI.reportMessage(fm.statusSummary());
   }

   private static void doFoldIndent(
         FvContext fvc, String arg) {
      int tabSize = 3;
      if (arg != null && !arg.isBlank()) {
         try {
            tabSize = Integer.parseInt(arg.trim());
         } catch (NumberFormatException e) {
            UI.reportMessage("foldindent: bad tabsize: "
               + arg);
            return;
         }
      }
      FoldDetector.LineFetcher fetcher = lineNum ->
         fvc.edvec.at(lineNum).toString();
      int lineCount = fvc.edvec.readIn();
      FoldModel fm = FoldDetector.detectIndentFolds(
         fetcher, lineCount, tabSize);
      fvc.setFoldModel(fm);
      saveFoldState(fvc);
      fvc.vi.recalcScreenRow();
      fvc.vi.redraw();
      UI.reportMessage(fm.statusSummary());
   }

   private static void doFoldMarker(FvContext fvc) {
      FoldDetector.LineFetcher fetcher = lineNum ->
         fvc.edvec.at(lineNum).toString();
      int lineCount = fvc.edvec.readIn();
      FoldModel fm = FoldDetector.detectMarkerFolds(
         fetcher, lineCount);
      fvc.setFoldModel(fm);
      saveFoldState(fvc);
      UI.reportMessage(fm.statusSummary());
   }

   /** Persist fold state for the given context's file. */
   public static void saveFoldState(FvContext fvc) {
      FoldModel fm = fvc.getFoldModel();
      if (fm == null)
         return;
      FileDescriptor fd = fvc.edvec.fdes();
      if (fd instanceof FileDescriptor.LocalFile) {
         String canonPath =
            ((FileDescriptor.LocalFile) fd).canonName;
         if (fm.isEmpty())
            FoldModel.deleteFoldState(canonPath);
         else
            fm.saveFolds(canonPath);
      }
   }

   // :mapkey <group> <key> <command> — bind a key in a keygroup at runtime
   // group: "move", "edit", or "keymap.move"/"keymap.edit" for overlays
   // key: single char, C-x for ctrl, or special name (F1-F12, Up, Down, etc.)
   private static void doMap(String arg) throws InputException {
      if (arg == null || arg.isBlank())
         throw new InputException(
            "usage: mapkey <group> <key> <command>"
            + " (group: move, edit, or keymap.move/keymap.edit)");
      String[] parts = arg.trim().split("\\s+", 3);
      if (parts.length < 3)
         throw new InputException(
            "usage: mapkey <group> <key> <command>"
            + " (group: move, edit, or keymap.move/keymap.edit)");

      KeyGroup kg = MapEvent.getKeyGroup(parts[0]);
      if (kg == null)
         throw new InputException("unknown keygroup: " + parts[0]
            + " (use move, edit, or keymap.move/keymap.edit)");

      JeyEvent key = parseKeySpec(parts[1]);
      String command = parts[2];

      if (Rgroup.bindingLookup(command) == null)
         throw new InputException("unknown command: " + command);

      kg.bind(key, command, null);
   }

   // :unmapkey <group> <key> — remove a key binding from a keygroup
   private static void doUnmap(String arg) throws InputException {
      if (arg == null || arg.isBlank())
         throw new InputException("usage: unmapkey <group> <key>");
      String[] parts = arg.trim().split("\\s+", 2);
      if (parts.length < 2)
         throw new InputException("usage: unmapkey <group> <key>");

      KeyGroup kg = MapEvent.getKeyGroup(parts[0]);
      if (kg == null)
         throw new InputException("unknown keygroup: " + parts[0]
            + " (use move, edit, or keymap.move/keymap.edit)");

      JeyEvent key = parseKeySpec(parts[1]);
      if (!kg.unbind(key))
         throw new InputException("no binding for key: " + parts[1]);
   }

   private static void showKeyMap(FvContext fvc) {
      KeyMap active = MapEvent.getActiveKeyMap(fvc);
      StringBuilder sb = new StringBuilder(active.getName());
      KeyMap p = active.getParent();
      while (p != null) {
         sb.append(" -> ").append(p.getName());
         p = p.getParent();
      }
      sb.append("  [registered: ").append(KeyMap.registeredNames()).append(']');
      UI.reportMessage(sb.toString());
   }

   private static void saveMapKeys() throws InputException {
      try {
         int count = KeyBindingPersistence.save();
         if (count == 0)
            UI.reportMessage("No user-modified keybindings to save");
         else
            UI.reportMessage("Saved " + count + " keybinding(s) to "
               + KeyBindingPersistence.getConfigPath());
      } catch (java.io.IOException e) {
         throw new InputException("Failed to save keybindings: "
            + e.getMessage());
      }
   }

   private static void loadMapKeys() {
      int count = KeyBindingPersistence.load();
      if (count == 0)
         UI.reportMessage("No keybinding file found at "
            + KeyBindingPersistence.getConfigPath());
      else
         UI.reportMessage("Loaded " + count + " keybinding(s)");
   }
   private static void doHeapDump(FvContext fvc) {
      String path = "/tmp/javi-heap-"
         + System.currentTimeMillis() + ".hprof";
      try {
         var server = java.lang.management.ManagementFactory
            .getPlatformMBeanServer();
         var mxBean = java.lang.management.ManagementFactory
            .newPlatformMXBeanProxy(server,
               "com.sun.management:type=HotSpotDiagnostic",
               com.sun.management.HotSpotDiagnosticMXBean.class);
         mxBean.dumpHeap(path, true);
         UI.reportMessage("Heap dump written to " + path);
      } catch (Exception e) {
         UI.reportMessage("Heap dump failed: " + e.getMessage());
         trace("doHeapDump: " + e);
      }
   }

   /**
    * Handles the :termlog command. Usage:
    * :termlog on — enable logging to ~/javi-termlog.txt
    * :termlog off — disable logging
    * :termlog /path/to/file — enable logging to specified file
    */
   private static void doTermLog(FvContext fvc, String arg) {
      ShellManager mgr = ShellManager.getInstance();
      ShellSession session = mgr.getActive();
      if (null == session) {
         UI.reportMessage("No active shell session");
         return;
      }
      Vt100 vt = session.getVt100();
      if (null == vt) {
         UI.reportMessage("No active terminal");
         return;
      }
      if (null == arg || arg.isEmpty() || "on".equals(arg)) {
         vt.termLog.enable();
         UI.reportMessage("Terminal logging enabled: "
            + "~/javi-termlog.txt");
      } else if ("off".equals(arg)) {
         vt.termLog.disable();
         UI.reportMessage("Terminal logging disabled");
      } else {
         vt.termLog.enable(java.nio.file.Path.of(arg));
         UI.reportMessage("Terminal logging enabled: " + arg);
      }
   }


   // Parse a key specification string into a JeyEvent.
   // Formats: single char (e.g. "x"), C-x for ctrl+char,
   // special names: F1-F12, Up, Down, Left, Right, Home, End, PgUp, PgDn,
   // Insert, Delete
   static JeyEvent parseKeySpec(String spec) throws InputException {
      if (spec.length() == 1)
         return new JeyEvent(0, 0, spec.charAt(0));

      // Extract modifier prefixes (C-, S-, A-, M-)
      int mods = 0;
      String rest = spec;
      while (rest.length() >= 2 && rest.charAt(1) == '-') {
         char mod = rest.charAt(0);
         switch (mod) {
            case 'C': mods |= JeyEvent.CTRL_MASK;  break;
            case 'S': mods |= JeyEvent.SHIFT_MASK; break;
            case 'A': mods |= JeyEvent.ALT_MASK;   break;
            case 'M': mods |= JeyEvent.META_MASK;  break;
            default:
               throw new InputException("unknown modifier: " + mod);
         }
         rest = rest.substring(2);
      }

      // Single char after modifiers
      if (rest.length() == 1) {
         char ch = rest.charAt(0);
         if ((mods & JeyEvent.CTRL_MASK) != 0) {
            char ctrlChar = (char) (Character.toLowerCase(ch) - 'a' + 1);
            return new JeyEvent(mods, 0, ctrlChar);
         }
         return new JeyEvent(mods, 0, ch);
      }

      // Action key name lookup
      int keyCode = switch (rest) {
         case "F1"  -> JeyEvent.VK_F1;
         case "F2"  -> JeyEvent.VK_F2;
         case "F3"  -> JeyEvent.VK_F3;
         case "F4"  -> JeyEvent.VK_F4;
         case "F5"  -> JeyEvent.VK_F5;
         case "F6"  -> JeyEvent.VK_F6;
         case "F7"  -> JeyEvent.VK_F7;
         case "F8"  -> JeyEvent.VK_F8;
         case "F9"  -> JeyEvent.VK_F9;
         case "F10" -> JeyEvent.VK_F10;
         case "F11" -> JeyEvent.VK_F11;
         case "F12" -> JeyEvent.VK_F12;
         case "Up"    -> JeyEvent.VK_UP;
         case "Down"  -> JeyEvent.VK_DOWN;
         case "Left"  -> JeyEvent.VK_LEFT;
         case "Right" -> JeyEvent.VK_RIGHT;
         case "Home"  -> JeyEvent.VK_HOME;
         case "End"   -> JeyEvent.VK_END;
         case "PgUp"  -> JeyEvent.VK_PAGE_UP;
         case "PgDn"  -> JeyEvent.VK_PAGE_DOWN;
         case "Insert" -> JeyEvent.VK_INSERT;
         case "Delete" -> JeyEvent.VK_DELETE;
         default -> -1;
      };

      if (keyCode == -1)
         throw new InputException("unknown key: " + spec);

      return new JeyEvent(mods, keyCode, JeyEvent.CHAR_UNDEFINED);
   }

   public static void redraw(boolean flushFlag) throws IOException {

      trace("redraw flushFlag " + flushFlag + " currFvc "
         + FvContext.getCurrFvc());
      UI.repaint();
      if (flushFlag) {
         Date nDate = new Date();
         long elapsed = nDate.getTime() - lastredraw.getTime();
         trace("elapsed " + elapsed);
         if (elapsed < 500) { // two redraws in <.5 seconds
            trace("start flush elapsed" + elapsed);
            DirManager.getInstance().flushCache();
            PosListList.Cmd.flush();
            DirEdit.DirSizeCalculator.clearCache();
            CharsetDetector.clearCache();
            ContextHelp.closePanel();
            int disposed = EditContainer.disposeByName("typing-practice");
            disposed += EditContainer.disposeByName("context-help");
            if (disposed > 0)
               trace("disposed " + disposed + " regenerable buffers");
            JS.clearOutput();
            EventQueue.biglock2.unlock();
            try {
               UI.flush();
            } finally {
               EventQueue.biglock2.lock();
            }
            FvContext.dump();
            EditContainer.dumpStatic();
            FileList.iclearUndo();

         }

         Tools.doGC();

         trace(" used memory " + (Runtime.getRuntime().totalMemory()
               - Runtime.getRuntime().freeMemory())
         // + "total memory " + Runtime.getRuntime().totalMemory()
         );
         lastredraw = new Date();
         trace("GC time in milliseconds "
               + (lastredraw.getTime() - nDate.getTime()));
         // vic.memfree();
      }
   }


   /**
    * Load a class by fully-qualified name, triggering its static
    * initializer.  Used from .javini to load plugins.
    *
    * @param className fully-qualified class name (e.g. "javi.lsp.LspCommands")
    */
   private static void loadClass(String className) {
      if (null == className || className.isEmpty()) {
         UI.reportMessage("loadclass requires a class name");
         return;
      }
      try {
         Class.forName(className.trim());
      } catch (ClassNotFoundException e) {
         UI.reportError("loadclass: class not found: " + className);
      }
   }

   /** Singleton buffer for :registers display. */
   private static final HelpBuffer registersBuf =
      new HelpBuffer("*registers*");

   /**
    * Display register contents in a buffer.
    * Populates a buffer named *registers* with the output of
    * {@link Buffers#getRegisterSummary()} and connects it to the
    * current view.
    */
   private static void showRegisters(FvContext fvc) throws InputException {
      registersBuf.ensure();
      registersBuf.clear();
      String summary = Buffers.getRegisterSummary();
      for (String line : summary.split("\n"))
         registersBuf.append(line);
      FvContext.connectFv(registersBuf.getBuffer(), fvc.vi);
   }
}
