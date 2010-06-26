package javi;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.util.Iterator;
import static javi.Tools.trace;

public class Javi {

   static class Preloader implements Runnable {

   public void run() {
      try {
         Class.forName("javi.RealJs");
      } catch (Throwable e) {
          UI.popError("preloader ",e);
      }
   }
}

/* Copyright 1996 James Jensen all rights reserved */
static final String copyright = "Copyright 1996 James Jensen";

//static String persistName = "testpersist";
static String persistName = null;

static class Jcmds extends Rgroup  {
     final String[] rnames = {
       "",
       "persistfile" ,
     };
   public Object doroutine(int rnum,Object arg,int count,int rcount,FvContext fvc,
         boolean dotmode) throws InputException{
      switch (rnum) {
         case 1:
            persistName = arg.toString();
            return null;

         default:
            throw new RuntimeException("doroutine called with " + rnum);
      }
   }
}

public static void initToUi() {
      //try {Thread.sleep(1000);} catch (InterruptedException e) {/*Ignore*/}

      new Jcmds();
      FontList.init();
      try {
        Command.readini();
      } catch (Exception e) {
         trace("error reading ini file" + e);
         e.printStackTrace();
         UI.reportMessage("error reading ini file" + e);
         e.printStackTrace();
      }
      //trace("");
      UI.init(true);
      //trace("");
}

public static MapEvent initPostUi() throws Exception {

      MapEvent eview = new MapEvent();
      
      new MiscCommands();
      Command.init(new EditGroup(eview));
      new PosListList.Cmd();

      eview.bindCommands();
      try { 
         trace("preserver");
         new Server(6001);
      } catch (Exception e) {
         trace("error starting Server" + e );
      }
 
      //new v8();
      //new msvc();
      new MakeCmd();
      Plugin.Loader.load("plugin/plugin.jar");//new FindBugs();
      //new FindBugs();
      new JavaCompiler();
      new CheckStyle();
      Buffers.initCmd();
      trace("unexpectedly slow");
      new JS();
      //new vcs.cmvc();
 
     //trace("javi Version " + version);
     return eview;
}

public static void main (String args[]) {
  //trace(System.getProperties().toString());
   //trace("prop : \n" + System.getProperties());
   trace("enter Javi Main");;
   new Thread(new Preloader(),"preloader").start();
   Thread curr = Thread.currentThread();
   curr.setPriority(curr.getPriority() + 1);
   StringBuilder sb = new StringBuilder();
   int i;
   String command = null;
   boolean cflag = false;
   boolean pflag = false;
   for (String str:args) {
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
         sb.append("\n");
      }
   }
   FileDescriptor pfile =persistName == null 
      ? null
      : FileDescriptor.LocalFile.make(persistName);

   boolean normalInit = true;
   if (pfile != null) {
      ObjectInputStream pis;
      try { 
         pis = new ObjectInputStream(pfile.getInputStream());
      } catch (IOException e) {
         trace("Exception while restoring state " + e );
         e.printStackTrace();
         pis = null;
      }
      if (pis != null) {
         try {
            //UI.trace("!!!!!!!!!!!!!!!! start restore ");
            TextEdit.restoreState(pis);
            FileList.restoreState(pis);
            FvContext.restoreState(pis);
            FontList.restoreState(pis);
            UI.restoreState(pis);
            //UI.trace("!!!!!!!!!!!!!!!! end restore ");
            FvContext fvc = FvContext.getCurrFvc();
            //fvc.vi.requestFocus();
            
            normalInit = false;
         } catch (ClassNotFoundException e) {
            trace("Exception while restoring state " + e );
            e.printStackTrace();
            System.exit(0);
         } catch (Throwable e) {
            trace("Exception while restoring state " + e );
            e.printStackTrace();
            System.exit(0);
            UI.trace("");
         }
      }
   }
   FileList.make(sb.toString());
   if (normalInit) {
      initToUi();
   }
   try {
      
      MapEvent ev =  initPostUi();
      if (command!=null) {
         //UI.trace("doing command " + command);
         Command.command(command,null,null);
      }
      ev.run();
  } catch (Throwable e) {
     trace("main caught vic exception "  +e );
     e.printStackTrace();
     trace("exiting" );
     System.exit(0); //???
  }
   if (pfile !=null)  {
      //DebuggingObjectOutputStream pout = null;
      ObjectOutputStream pout;
      try {
         pout = new ObjectOutputStream(pfile.getOutputStream());
         //trace("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! start save");
         TextEdit.saveState(pout);
         FileList.saveState(pout);
         FvContext.saveState(pout);
         FontList.saveState(pout);
         UI.saveState(pout);
         //trace("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! end save");

      } catch (Throwable  e) {
         UI.popError("Serialization error " , e);
      }

  }

  UI.dispose();
  trace("calling System.exit");
  System.exit(0);
}
}

