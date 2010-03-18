package javitests;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Date;
import javi.InputException;
import javi.JaviFacade;
import javi.TextEdit;
import javi.Tools;

class PerfTest {

static void trace(String str) {
   Tools.trace(str,1);
}

static boolean myassert(boolean flag,Object dump) {
   if (!flag) 
      throw new RuntimeException(" ASSERTION FAILURE " + dump.toString());
   return flag;
}
static void clearmem() { 
     System.gc();
     System.runFinalization();
     System.gc();
     System.runFinalization();
     System.gc();
     System.runFinalization();
     System.gc();
}

static void quicktest() throws IOException,InputException {

     int tot = 20000;
     int after = 13123;
     long targettime = 1000;
     long targetmem =1300000; // should be 3100000 in old version
     //int tot = 100;
     //int after = 82;
     //int tot = 10;
     //int after = 10;
     File dd = new File("perftest.dmp2");
     dd.delete();
     FileWriter fs = new FileWriter("perftest");
     try {
        for (int i = 0;i<tot;i++)
           fs.write("xxline " + i + '\n');
     } finally {
        fs.close();
     }
     dd=null;
     clearmem();
     trace("start memory " + Runtime.getRuntime().totalMemory());
     Date start = new Date();
     TextEdit ex = JaviFacade.createFileTE ("perftest");

     myassert(ex.finish()==tot+1,Integer.valueOf(ex.finish()));
     //vic vico = new vic("");
     ex.processCommand("g/9/d",1);
     myassert(ex.finish()==after,Integer.valueOf(ex.finish()));
     ex.finish();
     ex.printout();
     ex.dispose();

     ex = JaviFacade.createFileTE ("perftest");
     ex.printout();
     myassert(ex.finish()==after,Integer.valueOf(ex.finish()));
     Date end = new Date();
     long elapsed =  end.getTime() - start.getTime();
     ex.dispose();
     ex=null;
     fs=null;
    
     clearmem();
     long mem =  Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
     trace("end memory " + mem);
     trace("elapsed time = " + elapsed + " milliseconds");
     myassert(mem < targetmem,Long.valueOf(mem));
     myassert(elapsed < targettime,Long.valueOf(elapsed));
}

void runcmd(String ... str) {
   
   BufferedReader input = Tools.runcmd(str);
   for (String s;null != (s = input.readLine());)
      trace(s);
   proc.waitFor();
   myassert(proc.exitValue()==0,proc.exitValue());
} 

static void slowTest() throws Exception{

     int tot = 20000;
     int after = 13123;
     long targettime = 1000;
     long targetmem =1300000; // should be 3100000 in old version
     
     runcmd("rm","-rf","javtestcp");
     runcmd("cp","-r","../javi","javtestcp");

     clearmem();
     trace("start memory " + Runtime.getRuntime().totalMemory());
     Date start = new Date();

     javi.Javi.initToUi("temp");
     Date toUI = new Date();

     javi.Javi.initPostUi();
     Date postUi = new Date();
     javi.Command.command("e .*java",null,null);
     Date filesReadIn = new Date();
     
     TextEdit[] flist = JaviFacade.getFileList ();
     for (TextEdit file:flist)
        file.finish();

     Date filesComplete = new Date();
     clearmem();
     long memreadin =  Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

     for (TextEdit file:flist) {
        file.processCommand("1,10d",1);
        file.printout();
        file.dispose();
     }

     Date end = new Date();
     long elapsed =  end.getTime() - start.getTime();
    
     clearmem();
     long mem =  Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
     trace ("to UI elapsed " +(toUI.getTime() - start.getTime()));
     trace ("to postUI elapsed " + (postUi.getTime() - toUI.getTime()));
     trace ("to end elapsed " + (end.getTime() - postUi.getTime()));

     trace("end memory " + mem);
     trace("total elapsed time = " + elapsed + " milliseconds");
     myassert(mem < targetmem,Long.valueOf(mem));
     myassert(elapsed < targettime,Long.valueOf(elapsed));
}

public static void main (String args[]) {
  try {
     //new editgroup(null);
     //quicktest();
     slowTest();
     trace("test executed successfully");
   } catch (Throwable e) {
      trace("main caught exception " + e);
      e.printStackTrace();
   }
   System.exit(0);
  
}
}
