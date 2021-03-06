package javi;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import static history.Tools.trace;

public final class Javi {

/*  attempt to speed up loading, didn't seem to work
   static class Preloader implements Runnable {

      public void run() {
         try {
            //trace("preload javi.RealJs");
            Class.forName("javi.RealJs");
            //trace("done ");
         } catch (Throwable e) {
            UI.popError("preloader ", e);
         }
      }
   }
*/

//static String persistName = "testpersist";
   private static String persistName = null;

   static final class Jcmds extends Rgroup  {
      private final String[] rnames = {
         "",
         "persistfile",
      };

      Jcmds() {
         register(rnames);
      }

      public Object doroutine(int rnum, Object arg, int count, int rcount,
            FvContext fvc, boolean dotmode) {
         switch (rnum) {
            case 1:
               persistName = arg.toString();
               return null;

            default:
               throw new RuntimeException("doroutine called with " + rnum);
         }
      }
   }

   public static void initToUi() throws ExitException {
      //try {Thread.sleep(1000);} catch (InterruptedException e) {/*Ignore*/}

      new Jcmds();
      new MiscCommands();
      try {
         Command.readini();
      } catch (Exception e) {
         trace("error reading ini file" + e);
         e.printStackTrace();
         UI.reportMessage("error reading ini file" + e);
         e.printStackTrace();
      }
      //trace("");
      //UI instance = isAwt
      //   ? new javi.awt.AwtInterface()
       //  : new StreamInterface();
      new javi.awt.AwtInterface();

      //trace("");
   }

   public static void initPostUi() throws IOException {
      new EditGroup();
      Command.init();
      new PosListList.Cmd();

      MoveGroup.init();
      new JS.JSR();
      new MakeCmd();
      MapEvent.bindCommands();
      try {
         //trace("preserver");
         new Server(6001);
      } catch (Exception e) {
         trace("error starting Server" + e);
      }

      //new v8();
      //new msvc();
      try {
         Plugin.Loader.load("plugin/plugin.jar"); //new FindBugs();
      } catch (Throwable e) {
         UI.reportError("error trying to load plugins " + e);
      }
      //new FindBugs();
      new JavaCompiler();
      new CheckStyle();
      //trace("unexpectedly slow");

      //new vcs.cmvc();

      //trace("javi Version " + version);
   }

   public static void main(String[] args) {
      //trace(System.getProperties().toString());
      //trace("prop : \n" + System.getProperties());
      EventQueue.biglock2.lock();
      trace("enter Javi Main");
      //new Thread(new Preloader(), "preloader").start();
      Thread curr = Thread.currentThread();
      curr.setPriority(curr.getPriority() - 1);
      StringBuilder sb = new StringBuilder(args.length * 10);
      String command = null;
      boolean cflag = false;
      boolean pflag = false;
      for (String str : args) {
         //trace("commandline: " + str);
         if (cflag) {
            command = str;
            cflag = false;
         } else if (pflag) {
            persistName = str;
            pflag = false;
         } else if (str.equals("-c")) {
            cflag = true;
         } else if (str.equals("-p")) {
            pflag = true;
         } else {
            sb.append(str);
            sb.append('\n');
         }
      }
      File pfile = null == persistName
                             ? null
                             : new File(persistName);

      boolean normalInit = true;
      if (null != pfile) {
         ObjectInputStream pis = null;
         try {
            pis = new ObjectInputStream(
               new BufferedInputStream(new FileInputStream(pfile)));
            //UI.trace("!!!!!!!!!!!!!!!! start restore ");
            TextEdit.restoreState(pis);
            FileList.restoreState(pis);
            FvContext.restoreState(pis);
            UI.restoreState(pis);
            //UI.trace("!!!!!!!!!!!!!!!! end restore ");
            //FvContext fvc = FvContext.getCurrFvc();
            //fvc.vi.requestFocus();

            normalInit = false;
         } catch (IOException e) {
            trace("Exception while restoring state " + e);
            e.printStackTrace();
            pis = null;
         } catch (ClassNotFoundException e) {
            trace("Exception while restoring state " + e);
            e.printStackTrace();
            System.exit(0);
         } catch (Throwable e) {
            trace("Exception while restoring state " + e);
            e.printStackTrace();
            System.exit(0);
            trace("");
         } finally {
            if (pis != null)
               try {
                  pis.close();
               } catch (IOException e) {
                  trace("caught exception try to close input stream!");
               }
         }
      }
      DirList.getDefault(); // force initialization of dirlist
      FileList.make(sb.toString());
      try {
         if (normalInit) {
            initToUi();
         }

         initPostUi();
         if (null != command) {
            //UI.trace("doing command " + command);
            Command.command(command, null, null);
         }
         Command.doneInit();
         MapEvent.run();
      } catch (ExitException e) {
      } catch (Throwable e) {
         if (!(e instanceof ExitException)) {
            trace("main caught vic exception "  + e);
            e.printStackTrace();
            trace("exiting");
         }
      }

      if (null != pfile)  {
         //DebuggingObjectOutputStream pout = null;
         try {
            ObjectOutputStream pout =
               new ObjectOutputStream(
                  new BufferedOutputStream(new FileOutputStream(pfile)));
            try {
               //trace("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! start save");
               TextEdit.saveState(pout);
               FileList.saveState(pout);
               FvContext.saveState(pout);
               UI.saveState(pout);
               //trace("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! end save");

            } catch (Throwable  e) {
               UI.popError("Serialization error ", e);
            } finally {
               pout.close();
            }
         } catch (IOException e) {
            trace("caught exception trying to open serialization file");
         }
      }

      EventQueue.biglock2.unlock();
      //trace("calling UI.dispose");
      UI.dispose();
      trace("calling System.exit");
      System.exit(0);
   }
}
