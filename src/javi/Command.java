package javi;
/* Copyright 1996 James Jensen all rights reserved */

import java.io.IOException;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Iterator;


public final class Command extends Rgroup {

   static final String copyright = "Copyright 1996 James Jensen";
   private static Command instance;

   private static EditGroup egroup;

   private static final String[] rnames = {
      "",
      "r",
      "tabstop",
      "terminatewep",
      "commandproc",
      "checkout",
      "set",
      "e!",
   };

   static void init(EditGroup egroupi)  {
      instance = new Command();
      instance.register(rnames);
      egroup = egroupi;
   }

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws InterruptedException {
      //trace("vic doroutine rnum = " + rnum);
      try  {
         switch(rnum) {
            case 0:
               return null; // noop
            case 1:
               return readFile(arg, fvc);
            case 2:
               fvc.vi.setTabStop(oToInt(arg));
               return null;
            case 3:
               Runtime.getRuntime().halt(0);
               return null;
            case 4:
               commandproc(fvc);
               return null;
            case 5:
               checkout((String) arg, fvc);
               return null;
            case 6:
               setcommand((String) arg, fvc);
               return null;
            case 7:
               fvc.edvec.reload();
               return null;
            default:
               throw new RuntimeException("doroutine called with " + rnum);
         }
      } catch (IOException e)  {
         UI.reportMessage("command caught IOException" + e);
         e.printStackTrace();
      } catch (InputException e) {
         e.printStackTrace();
         UI.reportMessage(e.toString());
      }
      return null;
   }

   public static final ArrayList<String> cmdlist = new ArrayList<String>();
   static void readini() throws IOException {
      FileDescriptor.LocalFile ifile = FileDescriptor.LocalFile.make(".javini");
      if (!ifile.isFile())
         return;
      BufferedReader ini = ifile.getBufferedReader();
      try {
         String line;
         while (null != (line = ini.readLine()))
            cmdlist.add(line);
      } finally {
         ini.close();
      }
   }

   public static void execCmdList() {
      Iterator<String> cit = cmdlist.iterator();
      while (cit.hasNext()) {
         command(cit.next(), null, null);
         cit.remove();
      }
   }

   static void doneInit() {
      if (cmdlist.size() != 0) {
         StringBuffer bf = new StringBuffer(
            "command list has unexecuted commands:\n");
         Iterator<String> cit = cmdlist.iterator();
         while (cit.hasNext()) {
            bf.append(cit.next());
            cit.remove();
         }
         UI.reportMessage(bf.toString());
      }
   }

   private Object readFile(Object arg, FvContext fvc) throws
         InputException, IOException {
      if (arg == null)
         throw new InputException("read command needs filename");
      String fname = arg.toString().trim();
      if (fname.charAt(0) == '<') {
         // Executer ex = new Executer("bash -c " + fname.substring(1,fname.length()));
         String [] cmd = {"bash", "-c", fname.substring(1, fname.length())};
         fvc.edvec.insertStream(Tools.runcmd(cmd), fvc.inserty());
      } else
         fvc.edvec.insertStream(FileDescriptor.getBufferedReader(
            arg.toString()), fvc.inserty());
      return null;
   }

   private static void commandproc(FvContext fvc) {

      String line = getcomline(":");
      line = line.substring(1, line.length());
      command(line, fvc, null);
   }

   private static void checkout(String filename, FvContext fvc) {
      command("vcscheckout", fvc, filename);
   }

   private Object setcommand(String argline, FvContext fvc) throws
         IOException, InterruptedException, InputException {
      if (argline == null)
         throw new InputException("read command needs filename");
      int eqindex = argline.indexOf('=');
      if (eqindex == -1)
         throw new InputException("invalid set command");
      String var = argline.substring(0, eqindex).trim();
      String val = argline.substring(eqindex + 1, argline.length()).trim();
      KeyBinding kb = bindingLookup(var);
      if (kb == null)
         throw new InputException("setting unknown variable:" + var);

      return kb.rg.doroutine(kb.index, val, 0, 0, fvc, false);
   }

   private int oToInt(Object str) throws InputException {
      if (str == null)
         throw new InputException("command needs decimal number");
      try {
         return Integer.parseInt(str.toString().trim());
      } catch (NumberFormatException e) {
         throw new InputException("command needs decimal number", e);
      }
   }

   static String getcomline(String prompt) {
      FvContext<String> commFvc =  UI.startComLine();
      EditContainer ev = commFvc.edvec;
      try {
         if (!(commFvc.at(ev.finish() - 1).toString().equals(prompt))) {
            ev.insertOne(prompt, ev.finish());
         }
         commFvc.cursorabs(prompt.length(), ev.finish() - 1);
         egroup.setInsertMode(commFvc, false);

      } catch (InputException e) {
         UI.reportMessage(e.toString());
      } catch (Throwable e) {
         UI.popError("exception in command processing ", e);
      }

      String line = UI.endComLine();
      if (line.startsWith(prompt, 0))
         return line;
      else {
         UI.reportMessage("deleted prompt");
         return prompt;
      }
   }

   public static void command(String line, FvContext fvc, Object args) {
      //trace("command " + line);
      String command;
      if (fvc == null)
         fvc = FvContext.getCurrFvc();
      if (args == null)
         if (line.indexOf(' ') != -1) {
            command = line.substring(0, line.indexOf(' '));
            String l2 = line.substring(command.length(), line.length()).trim();
            args = "".equals(l2)  ? null : l2;
         } else {
            command = line;
         }
      else
         command = line;
      try {
         KeyBinding kb = bindingLookup(command);
         //trace("command kb = " + kb);
         boolean comdone = false;
         if (kb != null) {
            if (args == null && kb.arg != null)
               args = kb.arg;
            if (kb.rg == instance) {
               instance.doroutine(kb.index, args, 0, 0, fvc, false);
               comdone = true;
            }
         }
         //trace("vic.command 3 kb= " + kb + "comdone " + comdone + " fvc " + fvc);
         if (!comdone) {
            if (fvc != null)  {
               int newpos = fvc.edvec.processCommand(line, fvc.inserty());
               //trace("newpos " + newpos);
               if (newpos == -1) {
                  if (kb != null) {
                     //trace("doing routine " +kb);
                     kb.rg.doroutine(kb.index, args, 0, 0, fvc, false);
                  } else
                     UI.reportMessage("Unknown Command:" + line);
               } else {
                  //???fvc.fixCursor();
                  fvc.cursoryabs(newpos);
               }
            } else {
               if (kb != null) {
                  //trace("doing routine " +kb);
                  kb.rg.doroutine(kb.index, args, 0, 0, null, false);
               } else {
                  UI.reportMessage("Unknown Command:" + line);
               }

            }
         }
      } catch (InputException e) {
         e.printStackTrace();
         UI.reportMessage(e.toString());
      } catch (IOException e) {
         UI.reportMessage(e.toString());
      } catch (InterruptedException e) {
         UI.reportMessage(e.toString());
      }
   }

}
