package javt;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;
/* Copyright 1996 James Jensen all rights reserved */

abstract class vcs extends Rgroup { //xyzzy

Hashtable ftoahash;
Hashtable atofhash = new Hashtable();
String promotionGroup;


String[] rnames = {
  "",
  "vcslock",        //
  "vcsunlock",      //
  "vcscmp",         //
  "vcsinstalled",   //
  "vcscheckout",   //
};

abstract void lockfile(fvcontext fvc,String filename) 
   throws IOException,FileNotFoundException,inputexception;
abstract void unlockfile(fvcontext fvc,String filename) 
   throws IOException,FileNotFoundException,inputexception;
abstract boolean difffile(fvcontext fvc,String filename)
   throws IOException,FileNotFoundException,inputexception;
abstract void checkout(fvcontext fvc,String filename) 
   throws IOException,FileNotFoundException,inputexception;
vcs()/* throws IOException,InterruptedException*/ { //xyzzy
/*
   try {
     setProject();
   } catch (IOException e) {
//      e.printStackTrace();
       throw e;
   } catch (InterruptedException e) {
       e.printStackTrace();
       throw e;
   }
*/
   register(rnames);
}

Object doroutine(int rnum,Object arg,int count,int rcount, fvcontext fvc,
     eventqueue eventq,boolean dotmode) throws 
     IOException,FileNotFoundException,inputexception {
   switch (rnum) {
      case 0: return null; // noop
      case 1: lockfile(fvc,(String) arg);return null;
      case 2: unlockfile(fvc,(String) arg);return null;
      case 3: return Boolean.valueOf(difffile(fvc,(String) arg));
      case 4: return Boolean.valueOf(true);
      case 5: checkout(fvc,(String) arg); return null;
      default:
           throw new RuntimeException();
    }

}

static class cmvc extends vcs {

String cmvc_top="e:\\build\\curr";
String cmvc_path="d:\\bin\\";
String cmvc_release="portable";

String cmvc_com="-release " +  cmvc_release  + " -top " + cmvc_top + " ";

private String getAfile(fvcontext fvc,String filename) {
trace("getAfile file = " + filename);
   if (filename==null)
      filename=fvc.edvec.ioc.getName();
   filename = filename.replace('/','\\');
trace("getAfile2 file = " + filename);
   return filename.startsWith(cmvc_top) ?
         filename.substring(cmvc_top.length(),filename.length())
      :  filename;
      
}
void lockfile(fvcontext fvc,String filename) throws IOException,FileNotFoundException {
   String Afile= getAfile(fvc,filename);
   String command = cmvc_path + "file.exe -lock " + cmvc_com + Afile;
   Vector v = new execvec2(command);
   int i;
   for (i=0;i<v.size();i++)
     ui.trace(v.elementAt(i).toString());
}

void checkout(fvcontext fvc,String filename) throws IOException,FileNotFoundException,inputexception {
   String Afile= getAfile(fvc,filename);
//    [-defect Number | -feature Number]*** 
   
   String command = cmvc_path + "file.exe -extract -version 1.6.1.5 -stdout " + cmvc_com + Afile;
trace("checkout:command = " + command);
   execvec2 v = new execvec2(command);
trace("checkout:execvec = " + v);
   if (v.exitValue!=0) {
      int i;
      StringBuffer errstring = new StringBuffer("cmvc extract returned non zero value = " + v.exitValue);
      for (i=0;i<v.size();i++) {
        errstring.append("\n");
        errstring.append(v.elementAt(i).toString());
      }

      throw new inputexception(errstring.toString());
   }
//   File ft = new File("tempfile");
//   if (cmpfile(fvc.edvec,ft)) {
//      String fname=fvc.edvec.ioc.getName();
//      fname = fname.substring(0,fname.lastIndexOf('.')) + ".orig";
//      fvc.edvec.setReadOnly(false);
//   } else
//      throw new inputexception("checked out file not identical to existing file");
}
/*
private boolean cmpfile(editvec ed,File ft) throws FileNotFoundException,IOException {
  BufferedReader input = new BufferedReader(new FileReader(ft));
  String line;
  int i = 1;
  int finish = ed.finish();
  while ( null != (line = input.readLine())) {
    input.readLine(); //pvcs seems to put in extraneous ^m
    if (i==finish) {
trace("finish = " + finish + " i = " + i);
       return false;
    } else if (!line.equals(ed.at(i++))) {
trace("line = " + line);
trace("edati = " + ed.at(i-1));
       return false;
    }
  }
  input.close();
  return (i== finish);
}
*/
void unlockfile(fvcontext fvc,String filename) throws IOException,FileNotFoundException {
   String Afile= getAfile(fvc,filename);
   String command = cmvc_com + "vcs.exe -U " + Afile;
   Vector v = new execvec2(command);
   int i;
   for (i=0;i<v.size();i++)
     ui.trace(v.elementAt(i).toString());
}
boolean difffile(fvcontext fvc,String filename) throws IOException,FileNotFoundException,inputexception {
   int i;
   String Afile= getAfile(fvc,filename);
trace("pvcs.difffile:filename= " + filename);
   if (filename==null) {
      throw new inputexception("pvcs diff needs a filename");
      //filename=fvc.edvec.finfo.filename;
      //filename= filename.substring( filename.lastIndexOf("\\")+1,filename.length());
      //filename=vic.findFile(filename);
   }
   String command = cmvc_com + "vdiff.exe -T " + Afile + " " + filename;
trace("pvcs.difffile:command = " + command);
   execvec2 v = new execvec2(command);
   for (i=0;i<v.size();i++)
     ui.trace(v.elementAt(i).toString());
   switch (v.exitValue) {
      case 0: 
         ui.reportMessege("vdiff reports no differences");
         return false;
      case 1:
         throw new IOException("vdiff failed");
      case 2:
         ui.reportMessege("vdiff reports differences");
         return true;
      default:
         throw new IOException("vdiff return invalid value");
   }
}
}
}
/*  pvcs commands unused ???
       } else if (command.equals("pvcsedit")) {
          pv = (pvcs) new ObjectInputStream(new FileInputStream("pvcsdata")).
             readObject();
          extext plist = propedit.getbeaninfo(pv);
          connect(plist,currview,ev);
          ev.runreturn();
          new ObjectOutputStream(new FileOutputStream("pvcsdata")).
             writeObject(pv);
          connect(flist.currentedit(currview),currview,ev);
       } else if (command.equals("pvcscreate")) {
          pv = (pvcs)Beans.instantiate(getClass().getClassLoader(),"javt.pvcs");
          //pv = new pvcs();
          extext plist = propedit.getbeaninfo(pv);
          connect(plist,currview,ev);
          ev.runreturn();
          new ObjectOutputStream(new FileOutputStream("pvcsdata")).
             writeObject(pv);
          connect(flist.currentedit(currview),currview,ev);
*/
