package javi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import history.Tools;
import static history.Tools.trace;

public final class MiscCommands extends Rgroup {
   MiscCommands() {
      final String[] rnames = {
         "",
         "xxxunused",
         "zprocess",
         "redraw",
         "undo",
         "redo",             // 5
         "undoline",
         "vt",
         "loadgroup",
         "comm",
         "exec",            //10
         "lines", // 5
         "setwidth",
         "shells",           // 13
         "shellclose",       // 14
         "shellnext",        // 15
         "shellprev",        // 16
         "shellname",        // 17
      };
      register(rnames);
   }

   private static TextEdit debugfile;
   private static TextEdit cmdfile;
   private static int defwidth = 80;
   private static int defheight = 80;

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws IOException, InputException {
      // trace("rnum = " + rnum );
      switch (rnum) {
         case 1:
            // free
            return null;
         case 2:
            zprocess(rcount, fvc);
            return null;
         case 3:
            redraw(true);
            return null;
         case 4:
            undo(fvc);
            return null;
         case 5:
            redo(fvc);
            return null;
         case 6:
            return null; // fvc.edvec.undoElement(fvc.inserty()); return null;
         case 7:
            // Type safety: arg should be String from key mapping
            startshell(fvc, arg instanceof String ? (String) arg : null);
            return null;
         case 8:
            // Type safety: arg should be String from key mapping
            loadgroup(fvc.edvec.getName(), arg instanceof String ? (String) arg : null);
            return null;
         case 9:
            // Type safety: arg should be String from key mapping
            startcom(arg instanceof String ? (String) arg : null, fvc);
            return null;
         case 10:
            // Type safety: arg should be String from key mapping
            startcmd(arg instanceof String ? (String) arg : null, fvc);
            return null;
         case 11:
            defheight = oBToInt(arg);
            return null;
         case 12:
            defwidth = oBToInt(arg);
            return null;
         case 13:
            listShells(fvc);
            return null;
         case 14:
            closeShell(fvc, (String) arg);
            return null;
         case 15:
            nextShell(fvc);
            return null;
         case 16:
            prevShell(fvc);
            return null;
         case 17:
            renameShell(fvc, arg instanceof String ? (String) arg : null);
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
         // ShellManager handles shell session cleanup
         if (ev == commCon)
            commCon = null;
         return false;
      }
   }

   private static MyFl fli = new MyFl();

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

   private void startshell(FvContext fvc, String host) throws IOException, InputException {
      trace("reached startshell " + host);

      // F8 toggle: if already viewing a shell buffer, enter passthrough mode
      if (null == host && fvc.edvec instanceof Vt100) {
         ((Vt100) fvc.edvec).handleKeys(fvc);
         return;
      }

      ShellManager mgr = ShellManager.getInstance();

      // If no argument and we have an active shell, switch to it
      if (null == host && mgr.getSessionCount() > 0) {
         ShellSession active = mgr.getActive();
         if (null != active) {
            FvContext.connectFv(active.getBuffer(), fvc.vi);
            return;
         }
      }

      // Try to parse arg as a session number
      if (null != host) {
         try {
            int shellId = Integer.parseInt(host);
            if (mgr.switchToId(shellId)) {
               ShellSession session = mgr.getActive();
               FvContext.connectFv(session.getBuffer(), fvc.vi);
               return;
            }
            // Fall through to create new shell if not found
         } catch (NumberFormatException e) {
            // Not a number — try as session name
            if (mgr.switchToName(host)) {
               ShellSession session = mgr.getActive();
               FvContext.connectFv(session.getBuffer(), fvc.vi);
               return;
            }
            // Not found by name — treat as SSH hostname
         }
      }

      // Create new shell session
      EditContainer.registerListener(fli);
      ShellSession session = mgr.newShell(host);
      FvContext.connectFv(session.getBuffer(), fvc.vi);
   }

   /**
    * Lists all active shell sessions.
    *
    * @param fvc the current file-view context
    */
   private static void listShells(FvContext fvc) {
      ShellManager mgr = ShellManager.getInstance();
      String list = mgr.getShellList();
      UI.reportMessage(list.replaceAll("\n", " | "));
   }

   /**
    * Closes the current or specified shell session.
    *
    * @param fvc the current file-view context
    * @param arg optional shell ID to close
    * @throws InputException if shell ID is invalid
    */
   private static void closeShell(FvContext fvc, String arg)
         throws InputException {
      ShellManager mgr = ShellManager.getInstance();

      if (null != arg && !arg.isEmpty()) {
         try {
            int shellId = Integer.parseInt(arg);
            if (!mgr.closeShell(shellId)) {
               throw new InputException("No shell with ID " + shellId);
            }
         } catch (NumberFormatException e) {
            throw new InputException("Invalid shell ID: " + arg, e);
         }
      } else {
         if (!mgr.closeActiveShell()) {
            throw new InputException("No active shell to close");
         }
      }
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
            FvContext.connectFv(active.getBuffer(), fvc.vi);
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
            FvContext.connectFv(active.getBuffer(), fvc.vi);
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

   private static TextEdit commCon;
   private static String portname = "COM1";
   private static int baudrate = 38400;

   private static void startcom(String arg, FvContext fvc) throws IOException, InputException {
      // trace("reached startcommCon");
      if (null == commCon) {
         if (null != arg)
            try {
               String[] args = arg.split(" +");
               if (2 != args.length)
                  throw new InputException("invalid arguments to comm command:"
                        + arg);
               baudrate = Integer.parseInt(args[1]);
               portname = args[0];
            } catch (NumberFormatException e) {
               throw new InputException("invalid number in arguments", e);
            }
         EditContainer.registerListener(fli);
         commCon = Vt100.CommReader.make(portname, baudrate);
         FvContext.connectFv(commCon, fvc.vi);
      } else
         FvContext.connectFv(commCon, fvc.vi);
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

   private static String[] lastcmd = { "bash", "-i", "-c",
         "(cd ../javitests; java -Xshare:off javitests.PerfTest )"
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

   private void zprocess(int rcount, FvContext fvc) throws InputException {
      int scchange = 0;
      float scrpos = 0.f;

      outloop: while (true) {
         int key = EventQueue.nextKey(fvc.vi);
         if (key >= '0' && key <= '9')
            scchange = scchange * 10 + (key & 0x0f);
         else {
            // trace("scchange " + scchange);
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
               default:
                  return;
            }
         }
      }

      fvc.placeline(fvc.inserty(), scrpos);
   }

   private static Date lastredraw = new Date();

   static void redraw(boolean flushFlag) throws IOException {
      // trace("redraw flushFlag " + flushFlag + " currFvc " +
      // FvContext.getCurrFvc());
      UI.repaint();
      if (flushFlag) {
         Date nDate = new Date();
         long elapsed = nDate.getTime() - lastredraw.getTime();
         trace("elapsed " + elapsed);
         if (elapsed < 500) { // two redraws in <.5 seconds
            trace("start flush elapsed" + elapsed);
            DirList.getDefault().flushCache();
            PosListList.Cmd.flush();
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

}
