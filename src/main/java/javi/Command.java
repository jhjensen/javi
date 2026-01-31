package javi;

import java.io.IOException;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Iterator;

import history.Tools;

public final class Command extends Rgroup {

   private static Command instance;

   private static final String[] rnames = {
      "",
      "r",
      "tabstop",
      "terminatewep",
      "commandproc",
      "checkout",  //5
      "set",
      "e!",
      "help",       //8
   };

   static void init()  {
      instance = new Command();
      instance.register(rnames);
   }

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws InterruptedException {
      //trace("vic doroutine rnum = " + rnum);
      try  {
         switch (rnum) {
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
            case 8:
               showHelp((String) arg, fvc);
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

   private static final ArrayList<String> cmdlist = new ArrayList<String>();
   static void readini() throws IOException {
      FileDescriptor.LocalFile ifile = FileDescriptor.LocalFile.make(".javini");
      if (!ifile.isFile())
         return;
      BufferedReader ini = ifile.getBufferedReader();
      try {
         for (String line; null != (line = ini.readLine());)
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
         StringBuilder bf = new StringBuilder(
            "command list has unexecuted commands:\n");
         Iterator<String> cit = cmdlist.iterator();
         while (cit.hasNext()) {
            bf.append(cit.next());
            cit.remove();
         }
         UI.reportMessage(bf.toString());
      }
   }

   /**
    * Display help for the given topic.
    *
    * @param topic the help topic (null for index)
    * @param fvc the current file-view context
    * @throws InputException if help cannot be displayed
    */
   private static void showHelp(String topic, FvContext fvc)
         throws InputException {
      TextEdit<String> helpBuffer = HelpSystem.getHelp(topic);
      FvContext.connectFv(helpBuffer, fvc.vi);
   }

   private Object readFile(Object arg, FvContext fvc) throws
         InputException, IOException {
      if (arg == null)
         throw new InputException("read command needs filename");
      String fname = arg.toString().trim();
      if (fname.charAt(0) == '<') {
         // Executer ex = new Executer("bash -c " + fname.substring(1,fname.length()));
         String[] cmd = {"bash", "-c", fname.substring(1, fname.length())};
         fvc.edvec.insertStream(Tools.runcmd(cmd), fvc.inserty());
      } else {
         FileDescriptor.LocalFile ifile =
            FileDescriptor.LocalFile.make(arg.toString());
         fvc.edvec.insertStream(ifile.getBufferedReader(), fvc.inserty());
      }
      return null;
   }

   private static void commandproc(FvContext fvc) {

      String line = InsertBuffer.getcomline(":");
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

      return kb.dobind(val, 0, 0, fvc, false);
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

   public static void command(String line, FvContext fvc, Object args) {
      //trace("command " + line);
      String command;
      if (fvc == null)
         fvc = FvContext.getCurrFvc();
      if (args == null)
         if (line.indexOf(' ') != -1) {
            command = line.substring(0, line.indexOf(' '));
            String l2 = line.substring(command.length(), line.length()).trim();
            args = l2.length() == 0  ? null : l2;
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
            if (kb.matches(instance)) {
               kb.dobind(args, 0, 0, fvc, false);
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
                     kb.dobind(args, 0, 0, fvc, false);
                  } else
                      throw new InputException("Unknown Command:" + line);
               } else {
                  //???fvc.fixCursor();
                  fvc.cursoryabs(newpos);
               }
            } else {
               if (kb != null) {
                  //trace("doing routine " +kb);
                  kb.dobind(args, 0, 0, null, false);
               } else {
                  throw new InputException("Unknown Command:" + line);
               }

            }
         }
      } catch (InputException e) {
         UI.reportMessage(e.toString());
      } catch (IOException e) {
         UI.reportMessage(e.toString());
      } catch (InterruptedException e) {
         UI.reportMessage(e.toString());
      }
   }

}
