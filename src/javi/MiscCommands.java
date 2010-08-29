package javi;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Date;

class MiscCommands extends Rgroup  {
   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";
   MiscCommands() {
      final String[] rnames = {
         "",
         "jdebug",
         "zprocess",
         "redraw",
         "undo",
         "redo",             // 5
         "undoline",
         "vt",
         "loadgroup",
         "comm",
         "exec",            //10
      };
      register(rnames);
   }
   private static TextEdit debugfile;
   private static TextEdit shellfile;
   private static TextEdit cmdfile;
   private static String lastdebug = "javi.Javi";
   private static String lastshell = null;

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws IOException, InputException {
      //trace("rnum = " + rnum );
      switch (rnum) {
         case 1:
            startDebug((String) arg, fvc);
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
            return null; //fvc.edvec.undoElement(fvc.inserty()); return null;
         case 7:
            startshell(fvc, (String) arg);
            return null;
         case 8:
            loadgroup(fvc.edvec.getName(), (String) arg);
            return null;
         case 9:
            startcom((String) arg, fvc);
            return null;
         case 10:
            startcmd((String) arg, fvc);
            return null;
         default:
            throw new RuntimeException("vigroup:default");
      }

//trace("end ");
   }

   private static class MyFl implements FileStatusListener {
      public void fileAdded(EditContainer ev)  { }
      public void fileWritten(EditContainer ev) { }
      public boolean fileDisposed(EditContainer ev) {
         if (ev == cmdfile)
            cmdfile = null;
         if (ev == debugfile)
            debugfile = null;
         if (ev == shellfile)
            shellfile = null;
         //  if (ev == picCon)
         //     picCon=null;
         if (ev == commCon)
            commCon = null;
         return false;
      }
   }

   private static MyFl fli = new MyFl();

   private void undo(FvContext fvc) throws IOException {
      int index = fvc.edvec.undo();
      if (index != -1)
         fvc.cursoryabs(index);
      fvc.fixCursor();
   }
   private void redo(FvContext fvc)  throws IOException {
      int index = fvc.edvec.redo();
      if (index != -1)
         fvc.cursoryabs(index);
      fvc.fixCursor();
   }
   public String toString() {
      return ("vigroup");
   }

   private void startshell(FvContext fvc, String host) throws
         IOException, InputException {
      trace("reached startshell " + host);
      if (shellfile != null && host != null) {
         UI.connectfv(shellfile, fvc.vi);
      } else {
         EditContainer.registerListener(fli);
         if (host == null)
            host = lastshell;
         else
            lastshell = host;
         shellfile = Vt100.Telnet.make(host);
         FvContext newfvc = UI.connectfv(shellfile, fvc.vi);
         newfvc.addKeyEventDispatcher();
      }

   }
   /*
   private static TextEdit picCon;
   void startPicCon(FvContext fvc) throws IOException,InputException {
      //trace("reached startPicCon");
      if (picCon ==null) {
         picCon=new TextEdit<String>(Vt100.PicConsole.make(),
            EditContainer.root);
         FvContext newfvc = UI.connectfv(picCon,fvc.vi);
         newfvc.addKeyEventDispatcher();
         EditContainer.registerListener(this);
      } else
         UI.connectfv(picCon,fvc.vi);

   }

   */
   private  static TextEdit commCon;
   private  static String portname = "COM1";
   private  static int baudrate = 38400;

   private static void startcom(String arg, FvContext fvc) throws
         IOException, InputException {
      //trace("reached startcommCon");
      if (commCon == null) {
         if (arg != null)
            try {
               String [] args = arg.split(" +");
               if (args.length != 2)
                  throw new InputException("invalid arguments to comm command:"
                     + arg);
               baudrate = Integer.parseInt(args[1]);
               portname = args[0];
            } catch (NumberFormatException e) {
               throw new InputException("invalid number in arguments", e);
            }
         EditContainer.registerListener(fli);
         commCon = Vt100.CommReader.make(portname, baudrate);
         FvContext newfvc = UI.connectfv(commCon, fvc.vi);
         newfvc.addKeyEventDispatcher();
      } else
         UI.connectfv(commCon, fvc.vi);
   }

   private static void startDebug(String cname, FvContext fvc) {
      try {
         if (debugfile == null) {
            //??? make this workjavac.compcommand(null,true);
            if (cname == null)
               cname = lastdebug;
            else
               lastdebug = cname;
            EditContainer.registerListener(fli);
            JDebugger jd = new JDebugger(cname);
            debugfile = new TextEdit<String>(jd, jd.prop);
         }
         UI.connectfv(debugfile, fvc.vi);
      } catch (Throwable e) {
         UI.reportError("startDebug failed:" + e);
         e.printStackTrace();
      }
   }

   static final class ProcIo extends BufInIoc<String> {
      private static final long serialVersionUID = 1;
      private transient Process proc;

      static ProcIo mkProcIo(String namei, String ...cmd) throws
            IOException {
         Process proc = Tools.iocmd(cmd);
         BufferedReader input = new BufferedReader(new InputStreamReader(
            proc.getInputStream()));
         return new ProcIo(namei, proc, input, cmd);
      }

      private ProcIo(String namei, Process proci,
            BufferedReader inp, String ...cmd) throws IOException {
         super(new FileProperties(FileDescriptor.InternalFd.make(namei),
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

   private static String [] lastcmd =  {"bash", "-i", "-c",
      "(cd ../javitests; java -Xshare:off javitests.PerfTest )"
   };

   private static void startcmd(String cname, FvContext fvc) {
      //trace("startcmd:" + cname);
      try {
         if (cmdfile == null) {
            //??? make this workjavac.compcommand(null,true);
            if (cname != null)
               lastcmd[3] = cname;

            EditContainer.registerListener(fli);
            ProcIo pi  = ProcIo.mkProcIo(lastcmd[3], lastcmd);
            cmdfile = new TextEdit<String>(pi, pi.prop);
         }
         UI.connectfv(cmdfile, fvc.vi);
      } catch (Throwable e) {
         UI.reportError("startDebug failed:" + e);
         e.printStackTrace();
      }
   }

   private void zprocess(int rcount, FvContext fvc) throws InputException {
      int scchange = 0;
      float scrpos = (float) 0.;

   outloop:
      while (true) {
         int key = EventQueue.nextKey(fvc.vi);
         if (key >= '0' && key <= '9')
            scchange = scchange * 10 + (key & 0x0f);
         else  {
            //trace("scchange " + scchange);
            if (scchange != 0)
               UI.setViewSize(fvc.vi, -1, scchange);
            if (rcount != 0)
               fvc.cursoryabs(rcount);
            switch (key) {
               case 10: // return ^m
               case 13: // return ^m
                  break outloop;
               case '.':
               case ',':
                  scrpos = (float) .5;
                  break outloop;
               case '-':
                  scrpos = (float) .99999;
                  break outloop;
               default:
                  return;
            }
         }
      }

      fvc.placeline(fvc.inserty(), scrpos);
   }

   private static Date lastredraw = new Date();
   static void redraw(boolean flushFlag) {
      //trace("redraw flushFlag " + flushFlag + " currFvc " + FvContext.getCurrFvc());
      UI.repaint();
      if (flushFlag) {
         Date nDate = new Date();
         long elapsed =  nDate.getTime() - lastredraw.getTime();
         trace("elapsed " + elapsed);
         if (elapsed < 500) { // two redraws in <.5 seconds
            trace("start flush elapsed" + elapsed);
            DirList.getDefault().flushCache();
            PosListList.Cmd.flush();
            EventQueue.biglock2.unlock();
            UI.flush();
            EventQueue.biglock2.lock();
            Buffers.clearmem();
            FvContext.dump();
            EditContainer.dumpStatic();
            FileList.iclearUndo();

         }

         Tools.doGC();

         trace(" used memory " +  (Runtime.getRuntime().totalMemory()
            - Runtime.getRuntime().freeMemory())
            // + "total memory " + Runtime.getRuntime().totalMemory()
         );
         lastredraw = new Date();
         trace("GC time in milliseconds "
            + (lastredraw.getTime() - nDate.getTime()));
         //vic.memfree();
      }
   }

}
