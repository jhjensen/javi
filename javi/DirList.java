package javi;
import java.io.IOException;
import java.util.ArrayList;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;

class DirList extends TextEdit<DirEntry> {
static final String copyright = "Copyright 1996 James Jensen";
private static final long serialVersionUID=1;

   private static final DirList deflist;

   transient private int dindex;
   transient private int findex = 0;
   transient private int maxIndex;
   transient private Matcher regex;
   transient private String searchName;

   static {
      FileProperties fp = new FileProperties(FileDescriptor.InternalFd.make("dirlist"),
         DirConverter.dirConverter);
      deflist= new DirList(fp);
   }

   private DirList(FileProperties<DirEntry> fp) {
      super(new IoConverter(fp,true),fp);
      //Thread.dumpStack();
      finish();
   }

   TextEdit<Position> globalgrep(String searchstr) {
      int size = readIn();
      ArrayList<DirEntry> dlist = new ArrayList<DirEntry>(size);
      for (int i=1;i<size;i++) {
         //trace("adding directory " + at(i));
         dlist.add(at(i));
      }
      GrepReader conv = new GrepReader(searchstr,dlist,true);
      return new TextEdit<Position>(conv,conv.prop);
   }

   void flushCache() {
      //trace("dirList flushing "+ readIn() + " entries");
      for (DirEntry dir:this) 
         dir.flushCache();
   }

   boolean addSearchDir (FileDescriptor.LocalDir fh) {
      DirEntry de = new DirEntry(fh);
      int numEntrys = readIn(); 
      //trace("numEntrys " + numEntrys + " de " + de);

      for (int i = 1;i<numEntrys;i++) {
         if (de.equals(at(i)))
            return false;
      }

      insertOne(de,readIn());
      checkpoint();
      return true;
   }

   static DirList getDefault() {
      return deflist;
   }

   void initSearch(String searchNamei) {
      dindex=0;
      findex=-1;
      maxIndex = readIn();
      searchName=FileDescriptor.LocalFile.make(searchNamei).shortName;
   }

   boolean initSearchR() {
      dindex=0;
      findex=-1;
      maxIndex = readIn();
      try {
         regex =  Pattern.compile(searchName,Pattern.CASE_INSENSITIVE).matcher(""); // case insensitive
         //trace("match regex = " + regex);
      } catch (PatternSyntaxException e) {
         return false;
      }
      return true;
   }

   FileDescriptor.LocalFile findNextFile() {
      //trace("findNextFile dindex " + dindex + " maxIndex " + maxIndex);
      while (++dindex<maxIndex) {
         DirEntry de = at(dindex);
         //trace("dindex = " + dindex + " de " + de);
         FileDescriptor.LocalFile fh =  dindex ==1
            ? FileDescriptor.LocalFile.make(searchName)
            : de.fh.createFile(searchName);
         //trace("new fh " + fh + " isFile " + fh.isFile());
         if (fh.isFile() || fh.isDirectory())
            return fh;
      }
      return null;
   }

   FileDescriptor findNextFileR() {
      while (dindex<maxIndex) {
         DirEntry de = at(dindex);
         //trace("dindex = " + dindex + " de " + de);
         String [] flist =de.getCache();

         if (flist != null) while (++findex< flist.length)  {
            
            //trace(flist[findex]);
            if (regex == null)
               throw new RuntimeException("regex not initialized");
            regex.reset(flist[findex]);
            if (regex.matches()) {
               FileDescriptor.LocalFile fh =  dindex ==1
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
             
static void trace(String str) {
   Tools.trace(str,1);
}

private static class DirConverter extends ClassConverter<DirEntry> {

   public DirEntry fromString(String S) {
          return new DirEntry(S);
   }

   static DirConverter dirConverter = new DirConverter();
}

private static class GrepReader extends PositionIoc {

private String searchterm;
transient private Matcher matcher;
private ArrayList<DirEntry> dirlist;
transient private boolean invert = false;

private final static String filespec = "(.*\\.o$)|(.*\\.lib$)|(.*\\.obj$)|(.*\\.pdb$)|(.*\\.ilk$)|(^tags$)|(.*\\.exe$)|(^ID$)|(.*\\.class$)|(.*\\.hex$)|(.*\\.dmp$)|(.*\\.dmp2$)|(.*\\.jar)|(^tags$)";
private final static Matcher fileMatcher=  Pattern.compile(filespec).matcher("");
private static final long serialVersionUID=1;

static private long sizeLimit = 1;

GrepReader(String spec,ArrayList<DirEntry> dirlisti,boolean inverti) {
     super("grep " + spec);
     dirlist=dirlisti;
     searchterm=spec;
     matcher = Pattern.compile("(^.*(" + spec + ").*$)|(^(.*)$)",Pattern.MULTILINE).matcher("");
        //,RE.REG_MULTILINE);
     invert = inverti;
}

String getCanonicalName() {
  return searchterm + " grep" + hashCode(); //??? hash code can be deleted if we make searches unique
}

String getName() {
  return searchterm + " grep";
}

protected void dorun() {
      for (DirEntry dir :dirlist) {
         //trace("GrepReader dir = " + dir);
         for (String filename :dir.getCache()) {
            //trace("Grepreader checking file " + filename);
            //trace("Grepreader Matcher " + fileMatcher.pattern());
            fileMatcher.reset(filename);
            if (invert ^ fileMatcher.find()) {
               FileDescriptor.LocalFile fd = dir.fh.createFile(filename);
               if (!fd.isDirectory()) {
                  if (fd.length() > sizeLimit*1000000) {
                     String[] choices = {"skip","grep","quit grep","remove file"};

                     UI.Result res = UI.reportModVal(
                        fd.shortName + " length " + (fd.length()/1000000) + 
                        " Mb is over grep size limit","MB",
                        choices ,sizeLimit);
                     sizeLimit = res.newValue;
                     if ("skip".equals(res.choice))
                        continue;
                     else if ("quit grep".equals(res.choice))
                        return;
                     else if ("remove file".equals(res.choice)) {
                        try {
                           fd.delete();
                        }  catch (IOException e) {
                           UI.popError("removing file failed" + fd ,e);
                        }
                        continue;
                     }
                  }
                  int linecount=1;
                  matcher.reset(new String(fd.readFile()));
                  while (matcher.find()) {
                     if (matcher.start(2) != -1)  {
                         //trace("matcher found " + matcher.group(0));
                         Position pos = new Position(matcher.start(2)-matcher.start(),linecount,fd,
                            matcher.group(0));
                         
                          addElement(pos);
                     }
                     linecount++;
                  } 
               } 
            } 
         } // a valid line
      }
} 
}
}

class DirEntry {
   final FileDescriptor.LocalDir fh;
   String []fcache;

   DirEntry(FileDescriptor.LocalDir fh) {
      this.fh = fh;
   }

   DirEntry(String filename) {
      int sindex = filename.indexOf(' ');
      if (sindex != -1 )
         filename=filename.substring(0,sindex);
   
       if (filename.length() ==0)
             filename = ".";

       fh = FileDescriptor.LocalDir.make(filename);
   }

   public String toString()  {
      return fh.toString();
   }

   public boolean equals(Object de) {
      return de instanceof DirEntry
         ?  fh.equals(((DirEntry)de).fh)
         : false;
   }
      
   public int hashCode() {
      return fh.hashCode();
   }
   void flushCache() {
      fcache= null;
   }
    
   String [] getCache() {
         if (fcache == null) {
            fcache = fh.list();
            //for (int i = 0;i<flist.length;i++)
               //trace("flist added " + flist[i]);
         }
         return fcache;
   }
}
