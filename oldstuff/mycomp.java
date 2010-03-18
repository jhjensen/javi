package javt;

//import com.sun.tools.javac.Main;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import sun.tools.javac.Main;


class mycomp extends positionioc {
/* Copyright 1996 James Jensen all rights reserved */
final String Copyright = "Copyright 1996 James Jensen";
String []compilestring;
private static BufferedReader compin;
private static PipedOutputStream compout;
private static boolean compiling;
static int errorcount;
private String files;

static Main javac;

mycomp(String[] comstring) throws IOException{
  super ("javac " + comstring[0] + " ...");
//  if (javac==null) {
     PipedInputStream compinpipe = new PipedInputStream();
     InputStreamReader in =  new InputStreamReader(compinpipe);
     compin =  new BufferedReader(in);
//     compin =  new BufferedReader(new InputStreamReader(compinpipe));
     //trace("encoding = " + in.getEncoding());
     compout= new PipedOutputStream(compinpipe);
     javac =  new Main(compout,"javac");
//  }
  String comstring2[] = new String[comstring.length+4] ;
  System.arraycopy(comstring,0,comstring2,4,comstring.length);
  comstring2[0] = "-deprecation";
  comstring2[1] = "-g";
  comstring2[2] = "-classpath";
  comstring2[3] = "e:\\";
  compilestring = comstring2;
  int i;
  for (i=0;i<compilestring.length;i++) 
     files += " " + compilestring[i];
  flush();
  ui.clearStatus();
  errorcount=0;
  //System.out.print("compiling : ");
  compiling=true;
}

void init(editvec evi) {
  //trace("");
  ev = evi;
  array = ev.varray;
  vcount=array.size();
  new Thread(this,"mycomp").start();
  new Thread(new comprunner(),"mycomp runner").start();
}

private class comprunner implements Runnable {
public void run () {
 //trace(files);
 javac.compile(compilestring);
 compilestring=null;
 compiling=false;
 //super.run();
}
}

synchronized int expand(int desired) {
   //trace("desired = " + desired  + " vcount = " + vcount);

   try {
      if (!compiling && !compin.ready()) 
         return -vcount;
      //trace("compiling = " + compiling + " ready = " + compin.ready() );
      while (vcount<desired || compin.ready()) {
        Object ob = getnext();
        if (ob==null) {
            compiling=false;
            break;
        }
        //trace("adding pos " + ob);
        array.addElement(ob);
        vcount++;
      }
   } catch (IOException e) {
       e.printStackTrace();
       System.out.println("mycomp.expand caught " + e);
       javac=null;
       return vcount;
   } catch (NumberFormatException e) {
       System.out.println("mycomp.expand caught " + e);
       return vcount;
   }
   //trace("iocontroller.expand wr = " + ev.writtenindex);
   //trace("mycomp.expand return " +vcount );
   return vcount;
}
void flush() {
  try {
     while (compin.ready())
         compin.readLine();
     } catch (IOException e) {/*Ignore */
     }
}

private String curdir;
Object parseline(String s) {return null;}

Object getnext() {
        //trace("compiling = " + compiling);
        try {
           while (compiling && !compin.ready()) {
              Thread.yield();
              if (compiling && !compin.ready()) 
                     Thread.sleep(100);
           }
           if (!compin.ready()) {
              ui.reportError("compile ended" + (errorcount ==0 
                  ? "" 
                  : " " + errorcount + " errors"));
              return null;
           }
           position pos = local.parseJavac(compin);
           if (pos==null)
              return null;
           // make position look like a canonical name
           if (pos.filename != null) {
             if (pos.filename.charAt(1)!=':')
                pos.filename = curdir + pos.filename ;
             File fh = new File(pos.filename);
             editvec evfix;
             if ((evfix = editvec.findfile(fh.getCanonicalPath()))!=null)
                evfix.fixposition(pos);
            }
           
           //trace("return " + pos); 
           errorcount++;
           return pos;
        } catch (IOException e) {
           return null;
        } catch (InterruptedException e) {
           return null;
        }
      
} 

}
