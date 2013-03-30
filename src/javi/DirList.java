package javi;

import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import static history.Tools.trace;

final class DirList extends TextEdit<DirEntry> {
   private static final long serialVersionUID = 1;

   private static final DirList deflist;

   private transient int dindex;
   private transient int findex = 0;
   private transient int maxIndex;
   private transient Matcher regex;
   private transient String searchName;

   static {
      FileProperties fp = new FileProperties(
         FileDescriptor.InternalFd.make("dirlist"), DirConverter.dirConverter);
      deflist = new DirList(fp);
   }

   private DirList(FileProperties<DirEntry> fp) {
      super(new IoConverter(fp, true), fp);
      //Thread.dumpStack();
      finish();
   }

   ArrayList<FileDescriptor.LocalFile> fileList(FilenameFilter fl) {
      int size = readIn();
      ArrayList<FileDescriptor.LocalFile> flist =
         new ArrayList<FileDescriptor.LocalFile>(100 * size);
      for (int ii = 1; ii < size; ii++) {
         for (FileDescriptor.LocalFile str : at(ii).fh.listDes(fl))  {
            flist.add(str);
         }
      }
      return flist;
   }

   TextEdit<Position> globalgrep(String searchstr) {
      int size = readIn();
      ArrayList<DirEntry> dlist = new ArrayList<DirEntry>(size);
      for (int ii = 1; ii < size; ii++) {
         //trace("adding directory " + at(ii));
         dlist.add(at(ii));
      }
      GrepReader conv = new GrepReader(searchstr, dlist, true);
      return new TextEdit<Position>(conv, conv.prop);
   }

   void flushCache() {
      //trace("dirList flushing "+ readIn() + " entries");
      for (DirEntry dir : this)
         dir.flushCache();
   }

   boolean addSearchDir(FileDescriptor.LocalDir fh) {
      DirEntry de = new DirEntry(fh);
      int numEntrys = readIn();
      //trace("numEntrys " + numEntrys + " de " + de);

      for (int ii = 1; ii < numEntrys; ii++) {
         if (de.equals(at(ii)))
            return false;
      }

      insertOne(de, readIn());
      checkpoint();
      return true;
   }

   static DirList getDefault() {
      return deflist;
   }

   void initSearch(String searchNamei) {

      dindex = 0;
      findex = -1;
      maxIndex = readIn();
      //searchName = FileDescriptor.LocalFile.make(searchNamei).shortName;
      searchName = searchNamei;
   }

   boolean initSearchR() {
      //trace("searchName " + searchName);
      dindex = 0;
      findex = -1;
      maxIndex = readIn();
      try {
         regex =  Pattern.compile(searchName,
            Pattern.CASE_INSENSITIVE).matcher("");
         //trace("match regex = " + regex);
      } catch (PatternSyntaxException e) {
         return false;
      }
      return true;
   }

   FileDescriptor.LocalFile findNextFile() {
      //trace("findNextFile dindex " + dindex + " maxIndex " + maxIndex);
      while (++dindex < maxIndex) {
         DirEntry de = at(dindex);
         //trace("dindex = " + dindex + " de " + de);
         FileDescriptor.LocalFile fh =  1 == dindex
            ? FileDescriptor.LocalFile.make(searchName)
            : de.fh.createFile(searchName);
         //trace("new fh " + fh + " isFile " + fh.isFile());
         if (fh.isFile() || fh.isDirectory())
            return fh;
      }
      return null;
   }

   FileDescriptor findNextFileR() {
      while (dindex < maxIndex) {
         DirEntry de = at(dindex);
         //trace("dindex = " + dindex + " de " + de);
         String [] flist = de.getCache();

         if (null != flist)
            while (++findex < flist.length)  {

               //trace(flist[findex]);
               if (null == regex)
                  throw new RuntimeException("regex not initialized");

               if (regex.reset(flist[findex]).matches()) {
                  FileDescriptor.LocalFile fh = 1 == dindex
                     ? FileDescriptor.LocalFile.make(flist[findex])
                     : de.fh.createFile(flist[findex]);
                  if (fh.isFile() || fh.isDirectory())
                     return fh;
               }
            }

         dindex++;
         findex = -1;
      }
      return null;
   }

   private static final class DirConverter extends ClassConverter<DirEntry> {

      private static final long serialVersionUID = 1;
      public DirEntry fromString(String st) {
         return new DirEntry(st);
      }

      private static DirConverter dirConverter = new DirConverter();
   }

   private static final class GrepReader extends PositionIoc {

      private transient Matcher matcher;
      private ArrayList<DirEntry> dirlist;
      private transient boolean invert = false;

      private static final String filespec =
         "(.*\\.bin)|(.*\\.ml3)|(.*\\.rom)|(.*\\.loc)|(.*\\.axe)|"
         + "(.*\\.o)|(.*\\.class)|(.*\\.lib)|(.*\\.obj)|(.*\\.pdb)|"
         + "(.*\\.ilk)|(^tags)|(.*\\.exe)|(^ID)|(.*\\.cla +ss)|"
         + "(.*\\.core)|(.*\\.dll)|"
         + "(.*\\.hex)|(.*\\.dmp)|(.*\\.dmp2)|(.*\\.jar)|(^tags)$";

      private static final Matcher fileMatcher =  Pattern.compile(
               filespec, Pattern.CASE_INSENSITIVE).matcher("");
      private static final long serialVersionUID = 1;

      private static long sizeLimit = 1;

      GrepReader(String spec, ArrayList<DirEntry> dirlisti, boolean inverti) {
         super("grep " + spec, null, pconverter);
         dirlist = dirlisti;
         matcher = Pattern.compile("(^.*(" + spec
            + ").*$)|(^(.*)$)", Pattern.MULTILINE).matcher("");
         //,RE.REG_MULTILINE);
         invert = inverti;
      }

      protected void dorun() {
         for (DirEntry dir : dirlist) {
            //trace("GrepReader dir = " + dir);
            for (String filename : dir.getCache()) {
               //trace("Grepreader checking file " + filename);
               //trace("Grepreader Matcher " + fileMatcher.pattern());

               if (invert ^ fileMatcher.reset(filename).find()) {
                  FileDescriptor.LocalFile fd = dir.fh.createFile(filename);
                  if (!fd.isDirectory()) {
                     if (fd.length() > sizeLimit * 1000000) {
                        String[] choices = {
                           "skip", "grep", "quit grep", "remove file"};

                        UI.Result res = UI.reportModVal(
                           fd.shortName + " length " + (fd.length() / 1000000)
                           + " Mb is over grep size limit", "MB",
                           choices, sizeLimit);

                        sizeLimit = res.newValue;
                        if ("skip".equals(res.choice))
                           continue;
                        else if ("quit grep".equals(res.choice))
                           return;
                        else if ("remove file".equals(res.choice)) {
                           try {
                              fd.delete();
                           }  catch (IOException e) {
                              UI.popError("removing file failed" + fd, e);
                           }
                           continue;
                        }
                     }
                     try {
                        int linecount = 1;
                        matcher.reset(fd.getString());
                        while (matcher.find()) {
                           if (matcher.start(2) != -1)  {
                              //trace("matcher found " + matcher.group(0));
                              Position pos = new Position(matcher.start(2)
                                 - matcher.start(), linecount,
                                 fd, matcher.group(0));

                              addElement(pos);
                           }
                           linecount++;
                        }
                     } catch (IOException e) {
                        trace("caught IOexception while grepping file " + fd);
                     }
                  }
               }
            } // a valid line
         }
      }
   }
}

final class DirEntry {

   final FileDescriptor.LocalDir fh;
   private String []fcache;

   DirEntry(FileDescriptor.LocalDir fhi) {
      fh = fhi;
   }

   DirEntry(String filename) {
      int sindex = filename.indexOf(' ');
      if (sindex != -1)
         filename = filename.substring(0, sindex);

      if (0 == filename.length())
         filename = ".";

      fh = FileDescriptor.LocalDir.make(filename);
   }

   public String toString()  {
      return fh.toString();
   }

   public boolean equals(Object de) {
      return de == this
         ? true
         : de instanceof DirEntry
            ?  fh.equals(((DirEntry) de).fh)
            : false;
   }

   public int hashCode() {
      return fh.hashCode();
   }

   void flushCache() {
      fcache = null;
   }

   String [] getCache() {
      if (null == fcache) {
         fcache = fh.list();
         //for (int i = 0;i<flist.length;i++)
         //trace("flist added " + flist[i]);
      }
      return fcache;
   }
}
