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

   /**
    * Print usage information to stderr.
    *
    * <p>Called when -h/--help is specified or when invalid arguments
    * are provided.</p>
    */
   private static void printUsage() {
      System.err.println("Usage: javi [options] [files...]");
      System.err.println("Options:");
      System.err.println("  -h, --help     Show this help message");
      System.err.println("  -c <command>   Execute command on startup");
      System.err.println("  -p <file>      Use persistence file for session state");
      System.err.println("  files...       Files to edit");
   }

   /**
    * Main entry point for the Javi editor.
    *
    * <p>Parses command line arguments and initializes the editor.
    * Supports the following options:</p>
    * <ul>
    *   <li>{@code -h, --help} - Print usage and exit</li>
    *   <li>{@code -c <command>} - Execute command after startup</li>
    *   <li>{@code -p <file>} - Use persistence file for session state</li>
    * </ul>
    *
    * @param args command line arguments
    */
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
      // B10: Improved command line argument handling with validation
      for (int i = 0; i < args.length; i++) {
         String str = args[i];
         //trace("commandline: " + str);
         if (cflag) {
            command = str;
            cflag = false;
         } else if (pflag) {
            persistName = str;
            pflag = false;
         } else if (str.equals("-h") || str.equals("--help")) {
            printUsage();
            System.exit(0);
         } else if (str.equals("-c")) {
            // B10: Check that -c has an argument
            if (i + 1 >= args.length) {
               System.err.println("Error: -c requires a command argument");
               printUsage();
               System.exit(1);
            }
            cflag = true;
         } else if (str.equals("-p")) {
            // B10: Check that -p has an argument
            if (i + 1 >= args.length) {
               System.err.println("Error: -p requires a file argument");
               printUsage();
               System.exit(1);
            }
            pflag = true;
         } else if (str.startsWith("-") && str.length() > 1) {
            // B10: Reject unknown flags
            System.err.println("Error: Unknown option: " + str);
            printUsage();
            System.exit(1);
         } else {
            sb.append(str);
            sb.append('\n');
         }
      }
      // B10: Handle trailing flag without argument (shouldn't happen with above checks)
      if (cflag) {
         System.err.println("Error: -c requires a command argument");
         printUsage();
         System.exit(1);
      }
      if (pflag) {
         System.err.println("Error: -p requires a file argument");
         printUsage();
         System.exit(1);
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
