package javi;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import history.Tools;
import static history.Tools.trace;

public final class FileList extends TextEdit<TextEdit<String>> {
   private static final long serialVersionUID = 1;
   private static final class FileConverter
         extends ClassConverter<TextEdit<String>> {
      private static final long serialVersionUID = 1;
      public TextEdit<String> fromString(String str) {
         //trace("S = "  + S);
         if (0 == str.length())
            return findfileopen("dummy" + dummycount++, true);

         int sindex = str.indexOf(' ');
         if (sindex != -1)
            str = str.substring(0, sindex);

         FileDescriptor.LocalFile fh = FileDescriptor.LocalFile.make(str);

         if (null != EditContainer.findfile(fh))
            return findfileopen("dup file" + str + dummycount++, true);

         TextEdit<String> retval = findfileopen(fh, true);
         return null == retval
                ? findfileopen(str + "___bad filename" + dummycount++, true)
                : retval;
      }

      private static FileConverter fileConverter = new FileConverter();
      private static int dummycount = 0;

      private static TextEdit<String> findfileopen(
            FileDescriptor.LocalFile fh, boolean createok) {
         //trace("fh = " +fh);
         if (!fh.canRead() && fh.isFile()) {
            trace("returning null, !canRead" + fh);
            return null;  // cant read
         }
         if (!fh.isFile() && !createok) {
            trace("returning null, !createok" + fh);
            return null;
         }

         //trace("fh = " +fh);

         FileProperties<String>  fp =
            new FileProperties<String>(fh, StringIoc.converter);
         TextEdit<String> ev = null == FileList.instance
            ? new TextEdit<String>(new FileInput(fp), fp)
            : new TextEdit<String>(new FileInput(fp), FileList.instance, fp);

         //trace("opened ev = " + ev);
         if (fh.isFile() && !fh.canWrite())
            ev.setReadOnly(true);
         return ev;
      }

      private static TextEdit<String> findfileopen(String filename,
            boolean createok) {
         return findfileopen(FileDescriptor.LocalFile.make(filename), createok);
      }

   }

   private final class Commands extends Rgroup {
      Commands() {
         final String[] rnames = {
            "",
            "vi",
            "e",
            "Zprocess",
            "nextfile",
            "gotofilelist", //5
            "q",
            "x",
            "wq",
            "q!",
         };
         register(rnames);
      }

      public Object doroutine(int rnum, Object arg, int count, int rcount,
            FvContext fvc, boolean dotmode) throws InputException,
         IOException {

         //trace("rnum = " + rnum);
         switch (rnum) {
            case 0:
               return null; // noop
            case 1:
            case 2:
               //trace("fopen command arg" + arg);
               //trace("instance " + instance);
               return openFileName((String) arg, fvc.vi);
            case 3:
               processZ(fvc);
               return null;
            case 4:
               nextfile(fvc);
               return null;
            case 5:
               FvContext.connectFv(instance, fvc.vi);
               return null;
            case 6:
            case 7:
               quit(true, fvc);
               return null;
            case 8:
               fvc.edvec.processCommand("w", fvc.inserty());
               quit(true, fvc);
               return null;

            case 9:
               quit(false, fvc);
               return null;
            default:
               throw new RuntimeException("doroutine called with " + rnum);
         }
      }

   }

   static void iclearUndo() throws IOException {
      instance.clearUndo();
   }

   static void saveState(java.io.ObjectOutputStream os) throws IOException {
      os.writeObject(instance);
      instance.saveList(os);
   }

   static void restoreState(java.io.ObjectInputStream is) throws
         IOException, ClassNotFoundException {

      instance = (FileList) is.readObject();
      instance.restoreList(is);
      instance.new Commands();
   }

   private static FileList instance = null;

   public static final class FileListEvent extends EventQueue.IEvent {
      private List mlist;

      public FileListEvent(List lst) {
         mlist = lst;
      }

      public void execute() {
         try {
            //Iterator eve = mlist.iterator();
            //while (eve.hasNext())
            //while (eve.hasNext())
            //   Rgroup.doCommand("vi", eve.next().toString(), 1,
            //                    1, FvContext.getCurrFvc(), false);
            for (Object ob : mlist)
               Rgroup.doCommand("vi", ob.toString(), 1,
                  1, FvContext.getCurrFvc(), false);
         } catch (InterruptedException e) {
            UI.reportError("Unexpected InterruptedException " + e);
         } catch (InputException e) {
            UI.reportError(e.toString());
         } catch (IOException e) {
            UI.reportError(e.toString());
         }
      }
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   static void make(String fnames) { // takes \n seperated now
      //trace("FileList.make entered fnames" + fnames);
      if (null == instance) {
         FileProperties prop = new FileProperties(
            FileDescriptor.InternalFd.make("filelist"),
            FileConverter.fileConverter);

         FileParser fp =  new FileParser(prop, fnames);
         instance = new FileList(fp, prop);
      } else {
         trace("");
         //instance.openFile(fnames, null);
         try {
            Object nobj = instance.openFileListp(fnames, null);
            if (null == nobj) {
               // should only happen when the given files are already open
               int nindex = fnames.indexOf('\n');
               if (nindex != -1)
                  instance.open1File(fnames.substring(0, nindex), null);
            }

         } catch (IOException e) {
            UI.popError("error opening command line file ", e);
         } catch (Exception e) {
            UI.popError("error opening command line file ", e);
         }
      }
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private FileList(FileParser fileParse, FileProperties prop) {
      super(fileParse, prop);
      new Commands();
   }

   public static FvContext getContext(View newView) {
      return FvContext.getcontext(newView, instance);
   }

   private static  int findModified() {
      return instance.findModifiedi();
   }

   private int findModifiedi() {

      for (int ii = 1; ii < finish(); ii++) {
         EditContainer ef = at(ii);
         if (ef.isModified())
            return ii;
      }
      return 0;
   }

   private ArrayList<EditContainer> findfilemod(String str) {
      //trace("findfilemod " + str);
      Matcher regex =  Pattern.compile(str).matcher("");

      ArrayList<EditContainer> svec = new ArrayList<EditContainer>(20);

      for (EditContainer ef : this) {
         //trace("looking at ef" + ef.canonname());
         if (ef.isModified()) {
            //trace("ef is modified" + ef);

            if (regex.reset(ef.toString()).matches())
               svec.add(ef);
         }
      }
      //trace("returning array size " + retarray.length);
      return svec;
   }

   private static boolean isReadyQuit(FvContext fvc) {
      int fileIndex = instance.findModifiedi();
      if (0 != fileIndex) {
         FvContext.getcontext(fvc.vi, instance).cursoryabs(fileIndex);
         TextEdit mfile = instance.at(fileIndex);
         UI.reportMessage(mfile + " is modified");
         //trace(mfile + " is modified");
         //trace("quit fvc " + fvc);
         try {
            FvContext.connectFv(mfile, fvc.vi);
            return true;
         } catch (Throwable  e) {
            throw new RuntimeException("unexpected inability to connect", e);
         }
      }
      return false;
   }

   static ArrayList<EditContainer> writeModifiedFiles(String spec) throws
         IOException {
      return instance.writeModifiedFilesi(spec);
   }

   private ArrayList<EditContainer> writeModifiedFilesi(String spec) throws
         IOException {
      ArrayList<EditContainer> efs = findfilemod(spec);
      for (EditContainer ef : efs)
         ef.printout();
      return efs;
   }

   public static boolean gotoposition(Position p,
      boolean setstatus, View vi) throws InputException {
      //trace("gotoposition p " + p);
      if (null == p)
         return false;
      //trace("goto position " + p);
      boolean retval = instance.openFile(p, vi);
      if (retval && setstatus && (null != p.comment))
         UI.setline(p.comment);
      return retval;
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private boolean openFile(Position pos, View vi) throws
      InputException {

      FileDescriptor fh = pos.filename;
      TextEdit ec = (TextEdit) EditContainer.findfile(fh);

      //trace("openFile fh " + fh + " ec " + ec + "  fh.isFile() "   + fh.isFile());
      if (null == ec && fh instanceof FileDescriptor.LocalFile) {
         FileDescriptor.LocalFile fhl = (FileDescriptor.LocalFile) fh;
         if (fhl.canRead() && fhl.isFile()) {
            FileProperties fp = new FileProperties(fh, StringIoc.converter);
            TextEdit<String> text = new TextEdit<String>(
               new FileInput(fp), instance, fp); //??? should be filelist

            if (!fh.canWrite())
               text.setReadOnly(true);
            int index = FvContext.getcontext(vi, this).getCurrentIndex() + 1;
            insertOne(text, index);
            instance.checkpoint();
            ec = text;
         }
      }
      //trace("openFile ec " + ec);
      if (null != ec) {
         int index = indexOf(ec);
         if (index > 0)
            FvContext.getcontext(vi, this).cursoryabs(index);
         FvContext fv2 = FvContext.connectFv(ec, vi);
         fv2.edvec.contains(pos.y);
         fv2.cursorabs(pos);
      }
      return null != ec;
   }

   static FvContext openFileName(String fname, View vi) throws
         IOException, InputException  {
      return instance.open1File(fname, vi);
   }

   @SuppressWarnings("unchecked")
   private TextEdit<String> findOpenWithDirlist(String fname) {

      DirList dlist = DirList.getDefault();
      DirList.getDefault().initSearch(fname);
      //trace(" looking for " + fname + "in directory list"); EditContainer.dumpStatic();
      FileDescriptor.LocalFile fh;
      while (null != (fh = dlist.findNextFile())) {
         TextEdit<String> te = (TextEdit<String>) EditContainer.findfile(fh);
         //trace("returning te " + te);
         if (null != te)
            return te;
      }
      return null;
   }

   private TextEdit<String> openFileListp(String flist, View vi) throws
         IOException, InputException {
      return openFileListp(new BufferedReader(new StringReader(flist)), vi);
   }

   static TextEdit<String> openFileList(BufferedReader flist, View vi) throws
         IOException, InputException {
      return instance.openFileListp(flist, vi);
   }

   private TextEdit<String> openFileListp(BufferedReader flist, View vi) throws
         IOException, InputException {
      int index = null == vi
                  ? 1
                  : FvContext.getcontext(vi, this).getCurrentIndex() + 1;
      TextEdit<String> text = insertStream(flist, index);
      instance.checkpoint();
      showEdit(text, vi);
      return text;
   }

   private FvContext showEdit(TextEdit<String> ed, View vi) throws
         InputException {
      //trace("ed " + ed);
      if (null != ed) {
         int index = indexOf(ed);
         if (null == vi)
            vi = FvContext.getCurrFvc().vi;
         if (null != vi) {
            FvContext.getcontext(vi, this).cursoryabs(index);
            return FvContext.connectFv(ed, vi);
         }
      }
      return null;
   }

   @SuppressWarnings("unchecked")
   private FvContext open1File(String fname, View vi) throws
         IOException, InputException {
      //trace("openFile fname " + fname);
      if (null == fname) {
         fname =  UI.getFile();
         if (null == fname)
            return null;
      }

      TextEdit<String> text = findOpenWithDirlist(fname);
      //trace("fname:" + fname + ":");
      //trace("openFile fh " + fh + " text " + text + "  fh.isFile() "   + fh.isFile());
      //trace("text " + text);
      if (null == text)
         text = (TextEdit<String>) EditContainer.grepfile(fname);

      //trace("openFile text " + text);
      if (null == text) {
         text = openFileListp(fname, vi);
//         text = FileConverter.findfileopen(fname, true);
//         int index = FvContext.getcontext(
//            FvContext.getCurrFvc().vi, instance).inserty();
//         instance.insertOne(text, index);
      }
      return showEdit(text, vi);
   }

   private static final class FileParser extends BufInIoc<TextEdit<String>> {
      private int stage = 1;
      private static final long serialVersionUID = 1;
      private transient String searchName;
      private transient boolean dupflag = false;
      private transient boolean foundf = false;

      FileParser(FileProperties fp, String fnames)  {
         super(fp, true, new BufferedReader(new StringReader(fnames)));
      }

      @SuppressWarnings("fallthrough")
      public TextEdit<String> getnext()  {
         EventQueue.biglock2.lock();
         try {
            TextEdit<String> edv = null;
            //trace("file list getnext stage " + stage);
         oloop:
            while (true)
               switch (stage) {
                  case 1:  //trace("stage 1 ");

                     dupflag = false;

                     searchName = getLine();
                     //trace("searching for file " + searchName);
                     if (null == searchName || searchName.length() == 0)
                        break oloop;

                     foundf = false;
                     //trace("looking for " + searchName + " edv = " + edv);
                     // for special names try and open right away an create
                     if (FileDescriptor.isSpecial(searchName))  {
                        FileDescriptor fh = FileDescriptor.make(searchName);
                        if (fh instanceof FileDescriptor.LocalDir) {
                           foundf |= DirList.getDefault().addSearchDir(
                              (FileDescriptor.LocalDir) fh);
                           continue oloop;
                        } else {
                           if (null != EditContainer.findfile(fh))
                              dupflag = true;
                           else if (fh instanceof FileDescriptor.LocalFile) {
                              edv = FileConverter.findfileopen(
                                 (FileDescriptor.LocalFile) fh, true);
                              if (null != edv)
                                 break oloop;
                           }
                        }
                     }
                     DirList.getDefault().initSearch(searchName);
                     stage = 2;
                     // nobreak
                  case 2:
                     //trace("stage 2 looking for " + searchName + "in directory list");
                     DirList dlist = DirList.getDefault();
                     while (true) {
                        FileDescriptor.LocalFile fh = dlist.findNextFile();
                        //trace("dlist fh " + fh);
                        if (fh != null) {
                           if (fh instanceof FileDescriptor.LocalDir) {
                              foundf |= DirList.getDefault().addSearchDir(
                                  (FileDescriptor.LocalDir) fh);
                           } else {
                              if (EditContainer.findfile(fh) != null) {
                                 dupflag = true;
                              } else {
                                 edv = FileConverter.findfileopen(fh, false);
                                 if (edv != null)
                                    break oloop;
                              }
                           }
                        } else
                           break;
                        if ((dupflag)) {
                           //trace("dup file found");
                           stage = 1;
                           foundf = true;
                           continue oloop;
                        }
                     }
                     stage = !foundf && dlist.initSearchR() ? 3 : 4;
                     //trace("end stg2 stage " + stage + " foundf = " + foundf);

                     break;

                  case 3:
                     //trace("stage 3 regexp directory list search:" + searchName + " edv = " + edv);
                     dlist = DirList.getDefault();
                     FileDescriptor fha;
                     while (null != (fha = dlist.findNextFileR())) {
                        if (fha instanceof FileDescriptor.LocalDir) {
                           foundf |= DirList.getDefault().addSearchDir(
                              (FileDescriptor.LocalDir) fha);
                        } else {
                           if (null != EditContainer.findfile(fha))
                              dupflag = true;
                           else  {
                              if (null != EditContainer.findfile(fha))
                                 dupflag = true;
                              else if (fha
                                    instanceof FileDescriptor.LocalFile) {
                                 edv = FileConverter.findfileopen(
                                    (FileDescriptor.LocalFile) fha, false);
                                 if (null != edv)
                                    break oloop;
                              }
                           }
                        }
                     }
                     //intentional fallthrough
                  case 4:
                     //trace("stage 4  if all else fails create file" + searchName + " edv = " + edv);
                     //trace("foundf = " + foundf);
                     if (!foundf) {
                        //trace("last chance create file");
                        FileDescriptor fh = FileDescriptor.make(searchName);
                        if (fh instanceof FileDescriptor.LocalDir) {
                           foundf |= DirList.getDefault().addSearchDir(
                              (FileDescriptor.LocalDir) fh);
                        } else {
                           if (null != EditContainer.findfile(fh))
                              dupflag = true;
                           else if (fh instanceof FileDescriptor.LocalFile) {
                              edv = FileConverter.findfileopen(
                                 (FileDescriptor.LocalFile) fh, true);
                              if (null != edv)
                                 break oloop;
                           }
                        }
                     }

                     stage = 1;
                     break;
               }

            if (null != edv) {
               //trace("end of getnext " + edv.canonname());
               foundf = true;
            }
            //trace("get next returning edv " + edv);
            return edv;
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }

   private void nextfile(FvContext fvc) throws InputException {
      FvContext fileListFvc = fvc.switchContext(this, 1);
      if (fvc.edvec != this)
         FvContext.connectFv((TextEdit) fileListFvc.at(), fvc.vi);
   }

   static void quit(boolean save, FvContext fvc) {
      //trace("vic.quit reached");
      if (save && null != fvc) {
         try {
            //trace("fvc.quit going to fvc =  " +  fvc);
            if (isReadyQuit(fvc))
               return;
         } catch (Throwable e) {
            trace("caught exception in quit " + e);
            e.printStackTrace();
         }
      }
      //trace("vic.quit1 reached");
      EventQueue.insert(new ExitEvent());
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private static void processZ(FvContext fvc) throws
         InputException {
      View currview = fvc.vi;
      if ('Z' == EventQueue.nextKey(currview)) {
         try {
            TextEdit ev =  fvc.edvec;
            int fileIndex = instance.indexOf(ev);
            TextEdit nextFile;
            //trace("processZ ev " + ev + " instance " + instance);
            if (ev == instance) {
               ev = (TextEdit) FvContext.getcurobj(instance);
               fileIndex = instance.indexOf(ev);
               nextFile = instance;
            } else if (-1 == fileIndex) {
               nextFile = (TextEdit) FvContext.getcurobj(instance);
            } else {
               nextFile = instance.containsNow(fileIndex + 1)
                  ? instance.at(fileIndex + 1)
                  : instance.at(1);
            }

            // gross hack because if a dummy file is created first the parent is root.

            if (-1 != fileIndex) {

               if (ev.isModified())
                  ev.printout();
               if (2 == instance.finish()) { // on last file
                  quit(true, fvc);
               } else {
                  instance.remove(fileIndex, 1);
                  fvc.fixCursor();
                  FvContext.reconnect(ev, nextFile);
               }
            } else if (instance.isParent(ev) || (ev instanceof Vt100)) {
                  // This could be cleaned up with a misc file list
               FvContext.dispose(ev, nextFile);
            } else {
               FvContext.reconnect(ev, nextFile);
            }
         } catch (IOException e) {
            UI.reportMessage("unable to save file" + e);
         }
      }
   }

   public static void main(String[] args) {
      try {
         //new editgroup(null);
         //perftest();
         make("test1\ntest2");
         instance.finish();
         Tools.Assert(instance.containsNow(2), instance);
         instance.checkpoint();
         //trace("instance[1] " + instance.at(1));
         //trace("instance[2] " + instance.at(2));
//     trace("instance[3] " + instance.at(3));
         Tools.Assert((2 == instance.at(1).finish()),
                      Integer.valueOf((instance.at(1)).finish()));
         Tools.Assert((2 == instance.at(2).finish()),
                      Integer.valueOf((instance.at(1)).finish()));
         instance.remove(1, 1);
         instance.checkpoint();
         //trace("instance[1] " + instance.at(1));
         Tools.Assert(2 == instance.finish(), instance.finish());
         instance.undo();

         //Tools.Assert(instance.finish()== 3, instance.finish());
         findModified();
         instance.disposeFvc();
         trace("test executed successfully");
      } catch (Throwable e) {
         trace("main caught exception " + e);
         e.printStackTrace();
      }
   }
}
