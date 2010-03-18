package javi;
 
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Iterator;

public class FileDescriptor implements Serializable {

   final String canonName;
   final String shortName;

   private final static String separator=File.separator;
   private static String separator2="." + File.separator;

   boolean exists() {
      return false;
   }

   boolean canWrite() {
      return false;
   }

   boolean isFile() {
      return false;
   }

   private FileDescriptor(String shortNamei,String cnamei) {
      canonName=cnamei;
      shortName=shortNamei;
   }

   FileDescriptor getPersistantFd() {
      return null;
   }

   void writeAll(Iterator<String> sitr,String lsep)  throws IOException
   {
   
      OutputStreamWriter ow = 
         new OutputStreamWriter(new BufferedOutputStream(getOutputStream() ));

      try {
         while (sitr.hasNext()){
            String line = sitr.next();
            //trace("writing:" + line);
            ow.write(line,0,line.length());
            ow.write(lsep,0,lsep.length());
         }
         ow.flush();
      } finally {
         ow.close();
      }
   }

   static FileDescriptor make(String fname) {
      // for now only do local files.  later parse fname for uri
      return LocalFile.make(fname);
   }

   void renameTo(LocalFile target) throws IOException {
      throw new IOException("not a renameable file");
   }
      
   OutputStream getOutputStream() throws IOException {
      throw new IOException("unable to create an output stream");
   }

   InputStream getInputStream() throws IOException {
      throw new IOException("unable to create an output stream");
   }

   static BufferedReader getBufferedReader(String fname) throws IOException {
      return new BufferedReader(new InputStreamReader(new FileInputStream(fname),"UTF-8"));
   }

   static boolean isSpecial(String file) {
          return file.startsWith(separator2) || file.startsWith(separator) ||
                 (file.length() >1 && file.charAt(1)==':'
                  && Character.isLetter(file.charAt(0)));
   }

/*
   static FileDescriptor make(FileDescriptor directory ,String fileName) {
      String nfile = directory.shortName + separator + fileName;
      return LocalFile.make(nfile);
   }

*/

public static class InternalFd extends FileDescriptor {
   private static long uniqCtr;

   public static InternalFd make(String fname) {
      return new InternalFd(fname);
   }
   public InternalFd(String fname) {
      super(fname ,fname + " " + ++uniqCtr);
   }
}

static class LocalDir extends LocalFile {

   static LocalDir make(String fname) {
      File fh = new File(fname);
      String cname = LocalFile.makecname(fh);
      String normName = LocalFile.normalize(fname,cname);
      if (!fh.isDirectory())
         cname = "Bad Dir:" + cname;
      return new LocalDir(normName,cname,fh);
   }

   FileDescriptor getPersistantFd() {
      return null;
   }
  LocalFile createFile(String fileName) {
      return LocalFile.make(shortName + separator + fileName);
   }

   private LocalDir(String fname,String cname,File fhi) {
      super(fname,cname,fhi);
   }
}

static class LocalFile extends FileDescriptor {
   private static File cwd;
   private static String cwdCanon;
   private static String[] canonArray;
   private static Matcher bslash =  Pattern.compile("\\\\").matcher("");

   final File fh; // temp change, do not check in

//boolean isPersistant() {
//   return true;
//}

   FileDescriptor getPersistantFd() {
      return make(canonName + ".dmp2");
   }

  private static void setCwd(File curDir) throws IOException {
         bslash.reset(curDir.getCanonicalPath());
         cwd = curDir;
         cwdCanon  = bslash.replaceAll("/") + '/';

         int index = 0;
         int count = 0;
         while (-1 != (index = cwdCanon.indexOf('/',index))) {
            count++;
            index ++;
         }

         StringBuilder sb = new StringBuilder(3*count);

//         sb.append("../");
         while (--count>0)
            sb.append("../");

         String canonRep = sb.toString();
         canonArray = new String[1+cwdCanon.length()];
         for (int ii = 0; ii <cwdCanon.length();ii++) {
            canonArray[ii]=canonRep.substring(count*3);
            if (cwdCanon.charAt(ii) =='/')
               count++;
         }
         canonArray[cwdCanon.length()]="";
         
   }

   static { 
      try {
         setCwd(new File("."));
      } catch (IOException e) { 
          throw new RuntimeException("FileDescriptor unable to resolve canonical path of cwd" , e);
      }
   }

   static final String normalize(String filename,String cname) {
      int commonLen = findCommon(cname,cwdCanon);

      //trace("filename " + filename);
      //trace("cname " + cname);
      //trace("cwdCanon " + cwdCanon);
      //trace("commonLen " + commonLen);
      //trace("cname.length() " + cname.length());
      //trace("commonstr " + cwdCanon.substring(0,commonLen));
      //trace("ca cl = " + canonArray[commonLen]);
      //trace("ca len = " + canonArray.length);

      if (commonLen==0)
         return filename;

      //trace("canonArray[commonLen] " + canonArray[commonLen]);

      String xname = canonArray[commonLen] + (cwdCanon.length() == commonLen +1 
         ? commonLen +1  == cname.length()
            ? "."
            : cname.substring(commonLen +1 )
         : cname.substring(commonLen +1));

      //trace("commonLen " + commonLen + "ca cl "+ canonArray[commonLen-1] + " xname:" + xname + " cname: + " + cname);
      return xname.length()<cname.length()
            ? xname
            : cname;
   }

   static String[] cwdlist(FilenameFilter fl) {
      return (cwd.list(fl));
   }

   static String[] cwdlist() {
      return (cwd.list());
   }

   String[] list() {
      return (fh.list());
   }

   String[] list(FilenameFilter fl) {
      return (fh.list(fl));
   } 

   void renameTo(LocalFile target) throws IOException{
         if (!fh.renameTo(target.fh))
           throw new IOException("unable to rename file");
   }
      

   static private final int findCommon(String str1,String str2) {
       //trace("findcommon");
       int maxlen = (str1.length()>str2.length()
          ? str2.length() 
          : str1.length());

       //trace("maxlen " + maxlen + "strlen 1 " + str1.length() + "\nstr1 " + str1 + "\nstr2 " + str2 + "\ncmn  " +  str1.substring(0,maxlen));
       int ii = 0;

       for (;ii <maxlen; ii++)
          if (str1.charAt(ii) != str2.charAt(ii)) 
             break;

       //trace("xx1 ii " + ii + "\nstr1 " + str1 + "\nstr2 " + str2 + "\ncomn " + str1.substring(0,ii));
       while (ii>0) {
          if (str1.charAt(--ii)== '/') 
             break;
       }
       //trace("xx2 ii " + ii + "\nstr1 " + str1 + "\nstr2 " + str2 + "\ncomn " + str1.substring(0,ii));
       return ii;
   }

   private static final String makecname(File fh) {
      try {
         bslash.reset(fh.getCanonicalPath());
         String cname = bslash.replaceAll("/");
         if (fh.isDirectory())
            cname += '/';
         return cname;
      } catch (Throwable e) {
         return "BAD file:" + fh.getName() + ":" + e;
      }
    }

   static LocalFile make(String fname) {
      File fh =  new File(fname);
      String cname = LocalFile.makecname(fh);
      String normName = LocalFile.normalize(fname,cname);
      //trace("cname " + cname  + " normName " + normName);
      return fh.isDirectory()
         ?new LocalDir(normName,cname,fh)
         :new LocalFile(normName,cname,fh);
   }

   static LocalFile createTempFile(String name,String ext) throws IOException {
       File tmp = File.createTempFile(name,ext);
       String shortName=tmp.getPath();
       String cname = makecname(tmp);
       return new LocalFile(shortName,cname,tmp);
   }

   private LocalFile(String iname,String cname,File fhi) {
         super(iname,cname);
         fh = fhi;
   }


  boolean isFile() {
     return fh.isFile();
  }

  boolean exists() {
     return fh.exists();
  }

  boolean isDirectory() {
     return fh.isDirectory();
  }

  boolean canRead() {
     return fh.canRead();
  }

  boolean canWrite() {
     return fh.canWrite();
  }


   void deleteOnExit() {
      fh.deleteOnExit();
   }

   void movefile(String newName) throws IOException {
      File file = new File(newName);
      if ((file.isFile()) && (!file.delete())) // check that file already exists
        throw new IOException("unable to delete");
      if (!fh.renameTo(file)) 
        throw new IOException("unable to rename " + fh + " to " + file);
   }

   BufferedReader getBufferedReader() throws IOException {
      return new BufferedReader(new InputStreamReader(new FileInputStream(fh),"UTF-8"));
   }
   OutputStream getOutputStream() throws IOException {
      return new FileOutputStream(fh) ;
   }
   InputStream getInputStream() throws IOException {
      return new FileInputStream(fh) ;
   }

   void delete() throws IOException {
      if (fh.exists() && !fh.delete()) {
         throw new IOException("unable to delete " +fh);
      }
   }

   public boolean equals(Object de) {
      return de instanceof FileDescriptor
         ? canonName.equals(((FileDescriptor)de).canonName)
         : false;
   }

   public String toString()  {
      return shortName  + " " + canonName;
   }

   public int hashCode() {
      return canonName.hashCode();
   }
      

   static void trace(String str) {
      Tools.trace(str,1);
   }

   byte[] readFile() {
      try {
         FileInputStream localInput = new FileInputStream(fh);
         try {
            int length=(int)fh.length();
            byte[] iarray=new byte[length];
            int ilen;
            ilen = localInput.read(iarray,0,length);
            if (ilen!=length)
               throw new RuntimeException("filereader.getFile: read in length doesnt match");
            //??? should take care of case of growing file      if (-1!= input.read(iarray,0,length))
            //         throw new RuntimeException("filereader.getFile: read has more data to go");
            //String str = new String(iarray);
           //      String str = new String(iarray,"UTF-8");
            //return str;
            return iarray;
         } finally {
            localInput.close();
         } 
      } catch (IOException e) {
        // happens all the time trace("caught " + e);
      }
      return new byte[0];
   }

   long length() {
      return fh.length();
   }

}
   //String curdir = x.getAbsolutePath();
   static void ntest(String iname,String shortname) {
       FileDescriptor t = LocalFile.make(iname);
       Tools.Assert(t.shortName.equals(shortname),t.shortName);
   }
   public static void main (String args[]) {
      try {
         //LocalFile.setCwd(new File("C:/cygwin/home/jjensen/javt/javi"));

         ntest("../ja/xx","../ja/xx");
         //ntest("fi.*java","BAD file:fi.*java:java.io.IOException: Invalid argument");
         ntest("fi.*java","fi.*java");
         ntest("../javitests/ms","../javitests/ms");
         ntest("c:/asdf","C:/asdf");
         ntest("C:/cygwin/home/jjensen/javt/history/xxx","../history/xxx");
         ntest("asdf","asdf");
         ntest("c:",".");
         ntest("../../xxx/../yy/../yy","../../yy");
         ntest("../../xxx","../../xxx");
         ntest("xx/../yy","yy");
         ntest("/asdf/xx/../yy","C:/asdf/yy");
         ntest("asdf/xx/../yy","asdf/yy");
         ntest("asdf/xx/./yy","asdf/xx/yy");
         ntest("asdf\\xx\\.\\yy","asdf/xx/yy");
         ntest("./",".");
         ntest("./xxx","xxx");
         ntest("asdf/","asdf");
         ntest("./asdf","asdf");
         ntest("\\asdf","C:/asdf");
         ntest("c:\\asdf","C:/asdf");
         Tools.trace("completed test");
      } catch (Throwable e) {
         Tools.trace("main caught exception " + e);
         e.printStackTrace();
      }
      System.exit(0);
   }
}
