package javt;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.Hashtable;
import java.util.Vector;

/* Copyright 1996 James Jensen all rights reserved */

class pvcs extends Rgroup implements Serializable{ //xyzzy

Hashtable ftoahash;
Hashtable atofhash = new Hashtable();
String promotionGroup;

String pvcspath = "M:\\pvcs\\win95\\";

String[] rnames = {
  "",
  "vcslock",        //
  "vcsunlock",      //
  "vcscmp",         //
  "vcsinstalled",   //
  "vcscheckout",   //
};
pvcs() throws IOException { //xyzzy
   try {
     setProject();
   } catch (IOException e) {
//      e.printStackTrace();
       throw e;
   }
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

String  env[]= new String[1];
String project;
String getProject() {
       return project;
   }
void setProject() throws IOException {
       int filecount=0;
       BufferedReader input = new BufferedReader(new FileReader("pvcslist"));
       String line;
       ftoahash = new Hashtable();
       line = input.readLine();
       int aname = line.indexOf(' ');
       int wname;
       if (aname==-1)
          project=line.trim();
       else {
          wname = line.indexOf(' ',aname+1);
          if (wname==-1)
          wname=line.length();
          project= line.substring(0,aname);
          promotionGroup = line.substring(aname+1,wname);
          promotionGroup=promotionGroup.trim();
          if (promotionGroup.equals(""))
            promotionGroup=null;
       }
System.out.println("proj = " +  project + " promotion group = " +promotionGroup);
       env[0] = "PVCSPROJ=" + project;
       while ( null != (line = input.readLine())) {
//       execvec alist = new execvec("c:\\djgpp\\bin\\perl pvcssearch");
//       for (i=0;i<=alist.size();i++)
//          line = (String)alist.elementAt(i);
          aname = line.indexOf(' ');
          wname = line.indexOf(' ',aname+1);
          if (wname==-1)
             wname=line.length();
          String archive = line.substring(0,aname);
          String file= line.substring(aname+1,wname);
          file= file.substring( file.lastIndexOf("\\")+1,file.length());
          if (ftoahash.containsKey(file))
             ftoahash.put(file,"duplicate entryies in pvslist");
          ftoahash.put(file,archive);
          filecount++;
//System.out.println("file = " + file + " archive = " +archive);
       }
       input.close();
//System.out.println("pvcslist contains " + filecount);
   }

   String getPromotionGroup() {
       return project;
   }
   void setPromotionGroup(String x) {
       promotionGroup = x;
   }


private String getAfile(fvcontext fvc,String filename) throws inputexception {
System.out.println("getAfile file = " + filename);
   if (filename==null)
      filename=fvc.edvec.ioc.getName();
   filename = filename.replace('/','\\');
   filename= filename.substring( filename.lastIndexOf("\\")+1,filename.length());
System.out.println("getAfile file = " + filename);
   String Afile= (String)ftoahash.get(filename);
   if (Afile==null)
      throw new inputexception("unable to find archive for " + filename);
   return Afile;
}
//   String [] parray ={"VCSID=chgt87x","USERID=chgt87x", "PVCSPROJ=" + project};
//   Vector v = new execvec(command,parray);
private void lockfile(fvcontext fvc,String filename) throws IOException,FileNotFoundException,inputexception {
   String Afile= getAfile(fvc,filename);
   String command;
   if (promotionGroup==null)
      command = pvcspath + "vcs.exe -L " + Afile;
   else 
      command = pvcspath + "vcs.exe -L -G" + promotionGroup  + " " + Afile;
   Vector v = new execvec2(command);
   int i;
   for (i=0;i<v.size();i++)
     System.out.println(v.elementAt(i));
}

private void checkout(fvcontext fvc,String filename) 
  throws IOException,FileNotFoundException,inputexception {
   String Afile= getAfile(fvc,filename);
   String command;
   if (promotionGroup==null)
      command = pvcspath + "get.exe -N -L -W -P -XO" + "tempfile" + " " + Afile;
   else
      command = pvcspath + "get.exe -N -L -W -P -G" + promotionGroup 
           + " -XO" + "tempfile" + " " + Afile;
        //+ " " + Afile;
System.out.println("checkout:command = " + command);
   execvec2 v = new execvec2(command);
   if (v.exitValue!=0) {
      int i;
      StringBuffer errstring = new StringBuffer("pvcs get returned non zero value = " + v.exitValue);
      for (i=0;i<v.size();i++) {
        errstring.append("\n");
        errstring.append(v.elementAt(i).toString());
      }
      throw new inputexception(errstring.toString());
   }
   File ft = new File("tempfile");
   if (cmpfile(fvc.edvec,ft)) {
      String fname=fvc.edvec.ioc.getName();
      fname = fname.substring(0,fname.lastIndexOf('.')) + ".orig";
      fvc.edvec.setReadOnly(false);
   } else
      throw new inputexception("checked out file not identical to existing file");
}

private boolean cmpfile(editvec ed,File ft) throws FileNotFoundException,IOException {
  BufferedReader input = new BufferedReader(new FileReader(ft));
  String line;
  int i = 1;
  int finish = ed.finish();

  while ( null != (line = input.readLine())) {
    input.readLine(); //pvcs seems to put in extraneous ^m
    if (i==finish)
       break;
    else if (!line.equals(ed.at(i++)))
       break;
  }

  input.close();
  return (i== finish && line==null);
}
private void unlockfile(fvcontext fvc,String filename) throws IOException,FileNotFoundException,inputexception {
   String Afile= getAfile(fvc,filename);
   String command = pvcspath + "vcs.exe -U " + Afile;
   Vector v = new execvec2(command);
   int i;
   for (i=0;i<v.size();i++)
     System.out.println(v.elementAt(i));
}
private boolean difffile(fvcontext fvc,String filename) throws IOException,FileNotFoundException,inputexception {
   int i;
   String Afile= getAfile(fvc,filename);
System.out.println("pvcs.difffile:filename= " + filename);
   if (filename==null) {
      throw new inputexception("pvcs diff needs a filename");
      //filename=fvc.edvec.finfo.filename;
      //filename= filename.substring( filename.lastIndexOf("\\")+1,filename.length());
      //filename=vic.findFile(filename);
   }
   String command = pvcspath + "vdiff.exe -T " + Afile + " " + filename;
System.out.println("pvcs.difffile:command = " + command);
   execvec2 v = new execvec2(command);
   for (i=0;i<v.size();i++)
     System.out.println(v.elementAt(i));
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

