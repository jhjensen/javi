package javi;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import org.mozilla.universalchardet.UniversalDetector;
import java.nio.charset.Charset;

import history.Tools;
//import static history.Tools.trace;

public class FileDescriptor implements Serializable {

   private static final long serialVersionUID = 1;
   final String canonName;
   final String shortName;

   private static final String separator = File.separator;
   private static String separator2 = "." + File.separator;

   boolean exists() {
      return false;
   }

   boolean canWrite() {
      return false;
   }

   boolean isFile() {
      return false;
   }

   java.nio.file.Path toPath() throws IOException {
      throw new IOException("no path");
   }
   private FileDescriptor(String shortNamei, String cnamei) {
      canonName = cnamei;
      shortName = shortNamei;
   }

   FileDescriptor getPersistantFd() {
      return null;
   }


   static FileDescriptor make(String fname) {
      // for now only do local files.  later parse fname for uri
      return LocalFile.make(fname);
   }

   void renameTo(LocalFile target, boolean overWrite) throws IOException {
      throw new IOException("not a renameable file");
   }

   OutputStream getOutputStream() throws IOException {
      throw new IOException("unable to create an output stream");
   }

   String getString() throws IOException {
      throw new IOException("unable to open file");
   }

   static final boolean isSpecial(String file) {
      return file.startsWith(separator2) || file.startsWith(separator)
         || (file.length() > 1 && file.charAt(1) == ':'
            && Character.isLetter(file.charAt(0)));
   }

   void delete() throws IOException {
      Tools.trace("delete1");
      throw new IOException("undeletable");
   }

   private static final class Fiter implements Iterable<File> {

      private final Iterator<FileDescriptor.LocalFile> input;

      Fiter(Iterable<FileDescriptor.LocalFile> inputi) {
         input =  inputi.iterator();
      }

      private final class Iter implements Iterator<File> {
         public boolean hasNext() {
            return input.hasNext();
         }

         public File next() {
            File retval = input.next().fh;
            return retval;
         }

         public void remove() {
            input.remove();
         }
      }

      public Iterator<File> iterator() {
         return new Iter();
      }

   }

   static final Iterable<? extends JavaFileObject> getFileObjs(
         StandardJavaFileManager fileManager,
         Iterable<FileDescriptor.LocalFile> flist) {
      return fileManager.getJavaFileObjectsFromFiles(new Fiter(flist));
   }

   public static final class InternalFd extends FileDescriptor {
      private static long uniqCtr;
      private static final long serialVersionUID = 1;

      public static InternalFd make(String fname) {
         return new InternalFd(fname);
      }

      public InternalFd(String fname) {
         super(fname, fname + " " + ++uniqCtr);
      }
   }

   static final class LocalDir extends LocalFile {

      private static final long serialVersionUID = 1;
      static LocalDir make(String fname) {
         LocalFile fh = LocalFile.make(fname);

         return fh.isDirectory()
            ? (LocalDir) fh
            : new LocalDir(fh.shortName, "Bad Dir:" + fh.canonName, fh.fh);
      }

      FileDescriptor getPersistantFd() {
         return null;
      }

      LocalFile createFile(String fileName) {
         return LocalFile.make(shortName + separator + fileName);
      }

      private LocalDir(String fname, String cname, File fhi) {
         super(fname, cname, fhi);
      }
   }

   static class LocalFile extends FileDescriptor {
      private static final long serialVersionUID = 1;
      private static File cwd;
      private static String cwdCanon;
      private static String[] canonArray;
      private static final Matcher bslash =
         Pattern.compile("\\\\").matcher("");
      final File fh; // temp change, do not check in

//boolean isPersistant() {
//   return true;
//}

      FileDescriptor getPersistantFd() {
         return make(canonName + ".dmp2");
      }

      private static void setCwd(File curDir) throws IOException {

         cwd = curDir;
         cwdCanon  = bslash.reset(
            curDir.getCanonicalPath()).replaceAll("/");

         if (cwdCanon.charAt(cwdCanon.length() - 1) != '/')
            cwdCanon += '/';
         int count = 0;
         for (int index = 0;
                -1 != (index = cwdCanon.indexOf('/', index)); index++)
            count++;

         StringBuilder sb = new StringBuilder(3 * count);

         while (--count > 0)
            sb.append("../");

         String canonRep = sb.toString();
         canonArray = new String[1 + cwdCanon.length()];
         for (int ii = 0; ii < cwdCanon.length(); ii++) {
            canonArray[ii] = canonRep.substring(count * 3);
            if ('/' == cwdCanon.charAt(ii))
               count++;
         }
         canonArray[cwdCanon.length()] = "";

      }

      static {
         try {
            setCwd(new File("."));
         } catch (IOException e) {
            throw new RuntimeException(
               "FileDescriptor unable to resolve canonical path of cwd", e);
         }
      }

      static final String normalize(String filename, String cname) {
         int commonLen = findCommon(cname, cwdCanon);

         //trace("filename " + filename);
         //trace("cname " + cname);
         //trace("cwdCanon " + cwdCanon);
         //trace("commonLen " + commonLen);
         //trace("cname.length() " + cname.length());
         //trace("commonstr " + cwdCanon.substring(0,commonLen));
         //trace("ca cl = " + canonArray[commonLen]);
         //trace("ca len = " + canonArray.length);

         if (0 == commonLen)
            return filename;

         //trace("canonArray[commonLen] " + canonArray[commonLen]);

         String xname =
            canonArray[commonLen] + (cwdCanon.length() == commonLen + 1
               ? commonLen + 1  == cname.length()
                  ? "."
                  : cname.substring(commonLen + 1)
               : cname.substring(commonLen + 1));

         //trace("commonLen " + commonLen + "ca cl "+ canonArray[commonLen-1] + " xname:" + xname + " cname: + " + cname);
         return xname.length() < cname.length()
                ? xname
                : cname;
      }

      static final String[] cwdlist(FilenameFilter fl) {
         return cwd.list(fl);
      }

      static final String[] cwdlist() {
         return cwd.list();
      }

      final String[] list() {
         return fh.list();
      }

      final String[] list(FilenameFilter fl) {
         return fh.list(fl);
      }

      final ArrayList<FileDescriptor.LocalFile>  listDes(FilenameFilter fl) {
         File[] flist = fh.listFiles(fl);
         if (flist == null)
            return null;
         ArrayList<FileDescriptor.LocalFile> dlist =
            new ArrayList<FileDescriptor.LocalFile>(flist.length);
         for (File file : flist)
            dlist.add(LocalFile.make(file));
         return dlist;
      }

      final void renameTo(LocalFile target, boolean overWrite) throws
            IOException {

         if (target.isFile() && overWrite) {
            // check that file already exists
            if  (!target.fh.delete()) {
               throw new IOException("unable to delete");
            }
         }
         if (!fh.renameTo(target.fh))
            throw new IOException("unable to rename " + fh + " to " + target);
      }

      private static int findCommon(String str1, String str2) {
         //trace("findcommon");
         int maxlen = (str1.length() > str2.length()
                       ? str2.length()
                       : str1.length());

         //trace("maxlen " + maxlen + "strlen 1 " + str1.length() + "\nstr1 " + str1 + "\nstr2 " + str2 + "\ncmn  " +  str1.substring(0,maxlen));
         int ii = 0;

         for (; ii < maxlen; ii++)
            if (str1.charAt(ii) != str2.charAt(ii))
               break;

         //trace("xx1 ii " + ii + "\nstr1 " + str1 + "\nstr2 " + str2 + "\ncomn " + str1.substring(0,ii));
         while (ii > 0) {
            if ('/' == str1.charAt(--ii))
               break;
         }
         //trace("xx2 ii " + ii + "\nstr1 " + str1 + "\nstr2 " + str2 + "\ncomn " + str1.substring(0,ii));
         return ii;
      }

      private static String makecname(File fh) {
         try {
            //trace("cpath " + fh.getCanonicalPath());
            String cname = bslash.reset(fh.getCanonicalPath()).replaceAll("/");
            if (cname.charAt(cname.length() - 1) != '/' && fh.isDirectory())
               cname += '/';
            //trace("cname " + cname);
            return cname;
         } catch (Throwable e) {
            return "BAD file:" + fh.getName() + ":" + e;
         }
      }

      private static Map<String, WeakReference<LocalFile>> map
         = new WeakHashMap();

      static LocalFile make(String fname) {
         //trace("make " + fname);
         File fh = new File(fname);
         if (!fh.exists()) {
            // fix up cygwin names
            String fname2 = 0 == fname.length()
               ? fname
               : '/' == fname.charAt(0)
                  ? separator.equals("/")
                     ? fname
                     : 1 == fname.indexOf("cygdrive")
                        ? fname.charAt(10) + ":" + fname.substring(11)
                        : "c:/cygwin" + fname
                  : fname;
            File fh2 = new File(fname2);
            if (fh2.exists()) {
               fh = fh2;
               fname = fname2;
            }
         }
         String cname = LocalFile.makecname(fh);
         String normName = LocalFile.normalize(fname, cname);
         //trace("exists " + fh.exists() + " cname " + cname  + " normName " + normName + " map " + map);

         WeakReference<LocalFile> ref = map.get(cname);
         fh = new File(normName);
         fname = normName;

         LocalFile fd = ref != null
            ? ref.get()
            : null;

         if (fd != null)
            return fd;

         fd = fh.isDirectory()
                ? new LocalDir(fname, cname, fh)
                : new LocalFile(fname, cname, fh);
         map.put(cname, new WeakReference(fd));
         return fd;
      }

      static final LocalFile make(File fh) {
         String cname = LocalFile.makecname(fh);
         String normName = LocalFile.normalize(fh.getName(), cname);
         //trace("cname " + cname  + " normName " + normName);
         return fh.isDirectory()
                ? new LocalDir(normName, cname, fh)
                : new LocalFile(normName, cname, fh);
      }

      static final LocalFile createTempFile(String name, String ext) throws
            IOException {
         File tmp = File.createTempFile(name, ext);
         String shortName = tmp.getPath();
         String cname = makecname(tmp);
         return new LocalFile(shortName, cname, tmp);
      }

      private LocalFile(String iname, String cname, File fhi) {
         super(iname, cname);
         fh = fhi;
      }

      final boolean isFile() {
         return fh.isFile();
      }

      final boolean exists() {
         return fh.exists();
      }

      final boolean isDirectory() {
         return fh.isDirectory();
      }

      final boolean canRead() {
         return fh.canRead();
      }

      final boolean canWrite() {
         return fh.canWrite();
      }

      final java.nio.file.Path toPath() {
         return fh.toPath();
      }

      final void deleteOnExit() {
         fh.deleteOnExit();
      }

      final BufferedReader getBufferedReader() throws IOException {
         byte[] filebyte = readFile();
         UniversalDetector detector = new UniversalDetector(null);
         detector.handleData(filebyte, 0, filebyte.length);
         detector.dataEnd();
         String encoding = detector.getDetectedCharset();
         detector.reset();

         Charset charSet = encoding == null
            ? Charset.defaultCharset()
            : Charset.forName(encoding);
         return new BufferedReader(new InputStreamReader(
            new ByteArrayInputStream(filebyte), charSet));
      }

      final String getString() throws IOException {
         byte[] filebyte = readFile();
         UniversalDetector detector = new UniversalDetector(null);
         detector.handleData(filebyte, 0, filebyte.length);
         detector.dataEnd();
         String encoding = detector.getDetectedCharset();
         detector.reset();

         Charset charSet = encoding == null
            ? Charset.defaultCharset()
            : Charset.forName(encoding);
         return new String(filebyte, charSet);

      }

      final OutputStream getOutputStream() throws FileNotFoundException {
         return new FileOutputStream(fh);
      }


      final void delete() throws IOException {
         if (fh.exists() && !fh.delete()) {
            throw new IOException("unable to delete " + fh);
         }
      }

      public final boolean equals(Object de) {
         return de == this
            ? true
            : de instanceof FileDescriptor
                ? canonName.equals(((FileDescriptor) de).canonName)
                : false;
      }

      public final String toString()  {
         return shortName  + " " + canonName;
      }

      public final int hashCode() {
         return canonName.hashCode();
      }

      private static final byte[] zBytes = new byte[0];
      final byte[] readFile() throws IOException {

         if (!exists())
            return zBytes;

         FileInputStream localInput = new FileInputStream(fh);
         try {
            int length = (int) fh.length();
            byte[] iarray = new byte[length];
            int ilen = localInput.read(iarray, 0, length);
            if (ilen != length)
               throw new RuntimeException(
                  "filereader.getFile: read in length doesnt match");
            //??? should take care of case of growing file      if (-1!= input.read(iarray,0,length))
            //         throw new RuntimeException("filereader.getFile: read has more data to go");
            //String str = new String(iarray);
            //      String str = new String(iarray,"UTF-8");
            //return str;
            return iarray;
         } finally {
            localInput.close();
         }
      }

      final long length() {
         return fh.length();
      }

   }

   //String curdir = x.getAbsolutePath();
   static final void ntest(String iname, String shortname) {
      FileDescriptor tf = LocalFile.make(iname);
      Tools.Assert(tf.shortName.equals(shortname), tf.shortName);
   }

   public static void main(String[] args) {
      try {
         //LocalFile.setCwd(new File("C:/cygwin/home/jjensen/javt/javi"));

         ntest("../ja/xx", "../ja/xx");
         //ntest("fi.*java","BAD file:fi.*java:java.io.IOException: Invalid argument");
         ntest("fi.*java", "fi.*java");
         ntest("../javitests/ms", "../javitests/ms");
         ntest("c:/asdf", "C:/asdf");
         ntest("C:/cygwin/home/jjensen/javt/history/xxx", "../history/xxx");
         ntest("asdf", "asdf");
         ntest("c:", ".");
         ntest("../../xxx/../yy/../yy", "../../yy");
         ntest("../../xxx", "../../xxx");
         ntest("xx/../yy", "yy");
         ntest("/asdf/xx/../yy", "C:/asdf/yy");
         ntest("asdf/xx/../yy", "asdf/yy");
         ntest("asdf/xx/./yy", "asdf/xx/yy");
         ntest("asdf\\xx\\.\\yy", "asdf/xx/yy");
         ntest("./", ".");
         ntest("./xxx", "xxx");
         ntest("asdf/", "asdf");
         ntest("./asdf", "asdf");
         ntest("\\asdf", "C:/asdf");
         ntest("c:\\asdf", "C:/asdf");
         Tools.trace("completed test");
      } catch (Throwable e) {
         Tools.trace("main caught exception " + e);
         e.printStackTrace();
      }
   }
}
