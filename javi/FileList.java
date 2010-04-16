package javi;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.EmptyStackException;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

class FileList extends TextEdit<TextEdit<String>> {
   static class FileConverter extends ClassConverter<TextEdit<String>> {
      public TextEdit<String> fromString(String str) {
         //trace("S = "  + S);
         if (str.length() == 0)
            return findfileopen("dummy" + dummycount++, true);


         int sindex = str.indexOf(' ');
         if (sindex != -1)
            str = str.substring(0, sindex);

         FileDescriptor.LocalFile fh = FileDescriptor.LocalFile.make(str);

         if (EditContainer.findfile(fh) != null)
            return findfileopen("dup file" + str + dummycount++, true);

         TextEdit<String> retval = findfileopen(fh, true);
         return retval == null
                ? findfileopen(str + "___bad filename" + dummycount++, true)
                : retval;
      }

      private static FileConverter fileConverter = new FileConverter();
      private static int dummycount;
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
         TextEdit<String> ev = new TextEdit<String>(new FileInput(fp),
               FileList.instance == null
               ? TextEdit.root
               : FileList.instance,
               fp); //??? should be filelist

         //trace("opened ev = " + ev);
         if (fh.isFile() && !fh.canWrite())
            try {
               ev.setReadOnly(true);
            } catch (IOException e) {
               return null;
            }
         return ev;
      }
      private static TextEdit<String> findfileopen(String filename,
            boolean createok) {
         return findfileopen(FileDescriptor.LocalFile.make(filename), createok);
      }

   }

   private class Commands extends Rgroup {
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
            IOException, MapEvent.ExitException {

         //trace("rnum = " + rnum);
         switch (rnum) {
         case 0:
            return null; // noop
         case 1:
         case 2:
            //trace("fopen command arg" + arg);
            //trace("instance " + instance);
            return instance.openFileName((String)arg, fvc.vi);
         case 3:
            processZ(fvc);
            return null;
         case 4:
            nextfile(fvc);
            return null;
         case 5:
            UI.connectfv(instance, fvc.vi);
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

   static void iclearUndo() {
      instance.clearUndo();
   }

   static void saveState(java.io.ObjectOutputStream os) throws IOException {
      os.writeObject(instance);
      instance.saveList(os);
   }

   static void restoreState(java.io.ObjectInputStream is)
         throws IOException, ClassNotFoundException {

      instance = (FileList)is.readObject();
      instance.restoreList(is);
      instance.new Commands();
   }

   private static FileList instance = null;

   /*
   static Vector<TextEdit<String>> getFileArray() {
      return instance.varray;
   }
   */

   static final String Copyright = "Copyright 1996 James Jensen";

   static class FileListEvent extends MapEvent.JaviEvent {
      List mlist;

      FileListEvent(List lst) {
         mlist = lst;
      }

      void execute() {
         try {
            Iterator eve = mlist.iterator();
            while (eve.hasNext())
               Rgroup.doroutine("vi", eve.next().toString(), 1,
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

   static void make(String fnames) { // takes \n seperated now
      //trace("FileList.make entered fnames" + fnames);
      if (instance == null) {
         FileProperties prop = new FileProperties(
            FileDescriptor.InternalFd.make("filelist") ,
            FileConverter.fileConverter);

         FileParser fp =  new FileParser(prop, fnames);
         instance = new FileList(fp, prop);
      } else {
         trace("");
         //instance.openFile(fnames, null);
         try {
            Object nobj = instance.openFileListp(fnames, null);
            if (nobj == null) {
               // should only happen when the given files are already open
               int nindex = fnames.indexOf('\n');
               if (nindex != -1)
                  instance.open1File(fnames.substring(0, nindex), null);
            }

         } catch (IOException e) {
            UI.popError("error opening command line file " , e);
         } catch (Exception e) {
            UI.popError("error opening command line file " , e);
         }
      }
   }

   private FileList  (FileParser fileParse, FileProperties prop) {
      super(fileParse, prop);
      new Commands();
   }

   static FvContext getContext(View newView) {
      return FvContext.getcontext(newView, instance);
   }

   private static  int findModified() {
      return instance.findModifiedi();
   }

   private int findModifiedi() {

      for (int i = 1; i<finish(); i++) {
         EditContainer ef = at(i);
         if (ef.isModified())
            return i;
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
            regex.reset(ef.toString());
            if (regex.matches()) {
               //trace("ef matches");
               svec.add(ef);
            } else
               ;//trace("ef does not match " + regex);

         }
      }
      //trace("returning array size " + retarray.length);
      return svec;
   }

   private static boolean isReadyQuit(FvContext fvc) {
      int fileIndex = instance.findModifiedi();
      if (fileIndex!= 0) {
         FvContext.getcontext(fvc.vi, instance).cursoryabs(fileIndex);
         TextEdit mfile = (TextEdit)instance.at(fileIndex);
         UI.reportMessage(mfile + " is modified");
         //trace("currfvc.quit going to currfvc =  " +  currfvc);
         try {
            UI.connectfv( mfile, fvc.vi);
            return true;
         } catch (Throwable  e) {
            throw new RuntimeException("unexpected inability to connect", e);
         }
      }
      return false;
   }

   static ArrayList<EditContainer> writeModifiedFiles(String spec)
         throws IOException, InputException {
      return instance.writeModifiedFilesi(spec);
   }

   private ArrayList<EditContainer> writeModifiedFilesi(String spec)
         throws IOException {
      ArrayList<EditContainer>efs = findfilemod(spec);
      for (EditContainer ef:efs)
         ef.printout();
      return efs;
   }

   static boolean gotoposition(Position p, boolean setstatus, View vi)
        throws InputException {
      //trace("gotoposition p " + p);
      if (p == null  || p.filename == null)
         return false;
      try {
         //trace("goto position " + p);
         boolean retval = instance.openFile(p, vi);
         if (retval && setstatus && (p.comment != null))
            UI.setline(p.comment);
         return retval;
      } catch (IOException e) {
         trace("gotoposition caught " + e + " position = " + p);
         return  false;
      }
   }

   private boolean openFile(Position pos, View vi)
   throws IOException, ReadOnlyException, InputException {

      FileDescriptor fh = pos.filename;
      TextEdit ec = (TextEdit)EditContainer.findfile(fh);

      //trace("openFile fh " + fh + " ec " + ec + "  fh.isFile() "   + fh.isFile());
      if (ec == null && fh instanceof FileDescriptor.LocalFile) {
         FileDescriptor.LocalFile fhl = (FileDescriptor.LocalFile)fh;
         if (fhl.canRead() && fhl.isFile()) {
            FileProperties fp = new FileProperties(fh, StringIoc.converter);
            TextEdit<String>text = new TextEdit<String>
               (new FileInput(fp), instance, fp); //??? should be filelist

            if (!fh.canWrite())
               text.setReadOnly(true);
            int index = FvContext.getcontext(vi, this).getCurrentIndex()+1;
            insertOne(text, index);
            instance.checkpoint();
            ec = text;
         }
      }
      //trace("openFile ec " + ec);
      if (null != ec) {
         int index = indexOf(ec);
         if (index >0)
            FvContext.getcontext(vi, this).cursoryabs(index);
         FvContext fv2 = UI.connectfv(ec, vi);
         fv2.cursorabs(pos);
      }
      return ec != null;
   }

   static FvContext openFileName(String fname, View vi) 
         throws IOException,InputException  {
      return instance.open1File(fname, vi);
   }

   private TextEdit<String> findOpenWithDirlist(String fname) {

      DirList dlist = DirList.getDefault();
      DirList.getDefault().initSearch(fname);
      //trace(" looking for " + fname + "in directory list"); EditContainer.dumpStatic();
      for (FileDescriptor.LocalFile fh; null != (fh = dlist.findNextFile());) {
         TextEdit<String> te = (TextEdit<String>) EditContainer.findfile(fh);
         //trace("returning te " + te);
         if (te != null)
            return te;
      }
      return null;
   }

   private TextEdit<String> openFileListp(String flist, View vi)
         throws IOException, InputException {
      return openFileListp(new BufferedReader(new StringReader(flist)), vi);
   }

   static TextEdit<String> openFileList(BufferedReader flist, View vi)
        throws IOException, InputException {
      return instance.openFileListp(flist, vi);
   }

   private TextEdit<String> openFileListp(BufferedReader flist, View vi)
         throws IOException, InputException {
      int index = vi == null
                  ? 1
                  : FvContext.getcontext(vi, this).getCurrentIndex()+1;
      TextEdit<String> text = insertStream(flist, index);
      instance.checkpoint();
      showEdit(text, vi);
      return text;
   }

   private FvContext showEdit(TextEdit<String> ed, View vi) {
      //trace("ed " + ed);
      if (null != ed) {
         int index = indexOf(ed);
         if (vi == null)
            vi = FvContext.getCurrFvc().vi;
         if (vi!= null) {
            FvContext.getcontext(vi, this).cursoryabs(index);
            try {
               return UI.connectfv(ed, vi);
            } catch (InputException e) {
            }
         }
      }
      return null;
   }

   private FvContext open1File(String fname, View vi)  
         throws IOException,InputException {
      //trace("openFile fname " + fname);
      if (fname == null) {
         fname =  UI.getFile();
         if (fname == null)
            return null;
      }

      TextEdit<String> text = findOpenWithDirlist(fname);
      //trace("fname:" + fname + ":");
      //trace("openFile fh " + fh + " text " + text + "  fh.isFile() "   + fh.isFile());
      //trace("text " + text);
      if (text == null)
         text = (TextEdit<String>)EditContainer.grepfile(fname);

      //trace("openFile text " + text);
      if (text == null) {
         text = openFileListp(fname,vi);
//         text = FileConverter.findfileopen(fname, true);
//         int index = FvContext.getcontext(
//            FvContext.getCurrFvc().vi, instance).inserty();
//         instance.insertOne(text, index);
      }
      return showEdit(text, vi);
   }


   private static class FileParser extends BufInIoc<TextEdit<String>> {
      private int stage = 1;
      private static final long serialVersionUID=1;
      transient private String searchName;
      transient private boolean dupflag = false;
      transient private boolean foundf = false;


      FileParser(FileProperties fp, String fnames)  {
         super(fp, true);
         //input = new BufferedReader( new StringReader(
         //                               fnames.replace(' ','\n')));
         input = new BufferedReader( new StringReader( fnames));
      }

      @SuppressWarnings("fallthrough")
      public TextEdit<String> getnext()  {
         TextEdit<String> edv = null;
         //trace("file list getnext stage " + stage);
         oloop: while (true) switch (stage) {
            case 1:  //trace("stage 1 ");

               dupflag = false;
               if (input == null)
                  break oloop;

               try {
                  searchName = input.readLine();
                  //trace("searching for file " + searchName);
               } catch (IOException e) {
                  searchName = null;
               }
               if (searchName == null) {
                  input = null;
                  break oloop;
               }
               foundf = false;
               //trace("looking for " + searchName + " edv = " + edv);
               // for special names try and open right away an create
               if (FileDescriptor.isSpecial(searchName))  {
                  FileDescriptor fh = FileDescriptor.make(searchName);
                  if (fh instanceof FileDescriptor.LocalDir) {
                     foundf |= DirList.getDefault().addSearchDir((
                        FileDescriptor.LocalDir)fh);
                     continue oloop;
                  } else {
                     if (EditContainer.findfile(fh)!= null)
                        dupflag = true;
                     else if (fh instanceof FileDescriptor.LocalFile) {
                        edv = FileConverter.findfileopen((
                           FileDescriptor.LocalFile)fh, true);
                        if (edv!= null)
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
                  if (fh!= null) {
                     if (fh instanceof FileDescriptor.LocalDir) {
                        foundf |= DirList.getDefault().addSearchDir((
                           FileDescriptor.LocalDir)fh);
                     } else {
                        if (EditContainer.findfile(fh)!= null) {
                           dupflag = true;
                        } else {
                           edv = FileConverter.findfileopen(fh, false);
                           if (edv!= null)
                              break oloop;
                        }
                     }
                  } else
                     break ;
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
               for (FileDescriptor fh; null != (fh = dlist.findNextFileR());) {
                  if (fh instanceof FileDescriptor.LocalDir) {
                     foundf |= DirList.getDefault().addSearchDir((
                        FileDescriptor.LocalDir)fh);
                  } else {
                     if (EditContainer.findfile(fh)!= null)
                        dupflag = true;
                     else  {
                        if (EditContainer.findfile(fh)!= null)
                           dupflag = true;
                        else if (fh instanceof FileDescriptor.LocalFile) {
                           edv = FileConverter.findfileopen((
                              FileDescriptor.LocalFile)fh, false);
                           if (edv != null)
                              break oloop;
                        }
                     }
                  }
               }
            case 4:
               //trace("stage 4  if all else fails create file" + searchName + " edv = " + edv);
               //trace("foundf = " + foundf);
               if (!foundf) {
                  //trace("last chance create file");
                  FileDescriptor fh = FileDescriptor.make(searchName);
                  if (fh instanceof FileDescriptor.LocalDir) {
                     foundf |= DirList.getDefault().addSearchDir((
                        FileDescriptor.LocalDir)fh);
                  } else {
                     if (EditContainer.findfile(fh)!= null)
                        dupflag = true;
                     else if (fh instanceof FileDescriptor.LocalFile) {
                        edv = FileConverter.findfileopen((
                           FileDescriptor.LocalFile)fh, true) ;
                        if (edv!= null)
                           break oloop;
                     }
                  }
               }

               stage = 1;
               break;
            }

         if (edv!= null) {
            //trace("end of getnext " + edv.canonname());
            foundf = true;
         }
         //trace("get next returning edv " + edv);
         return edv;
      }
   }

   private void nextfile (FvContext fvc) throws InputException {
      FvContext fileListFvc = fvc.switchContext(this, 1);
      if (fvc.edvec!= this)
         UI.connectfv((TextEdit)fileListFvc.at(), fvc.vi);
   }

   private static class ExitEvent extends EventQueue.IEvent {
      void execute() throws MapEvent.ExitException {
         //trace("ExitEvent");
         throw new MapEvent.ExitException();
      }
   }

   static void quit(boolean save, FvContext fvc) {
      //trace("vic.quit reached");
      if (save && fvc!= null) {
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

   private static void processZ(FvContext fvc)
         throws MapEvent.ExitException, InputException {
      //trace("processZ");
      View currview = fvc.vi;
      if (EventQueue.nextKey(currview) == 'Z') {
         try {
            TextEdit ev =  fvc.edvec;
            //trace("processZ ev " + ev + " instance " + instance);
            if (ev == instance)
               ev =(TextEdit)FvContext.getcurobj(instance);

                  // gross hack because if a dummy file is created first the parent is root.
            if (ev.parentEq (instance) || (ev.parentEq(TextEdit.root)
                  && (-1 != instance.indexOf(ev)))) {
               if (ev.isModified())
                  ev.printout();

               if (instance.finish()== 2) // on last file
                  quit(true, fvc);
               FvContext fv = FvContext.getcontext(currview, instance);
               int remindex = fv.inserty();
               fv.cursory(-1); // the remove already move the cursor up by one.
               instance.remove(remindex, 1);
            } else if (!(ev.at(0) instanceof Position))
               if (!(ev instanceof FontList))
                  if (!(ev instanceof PosListList))
                     if (!(ev instanceof DirList))
                        FvContext.dispose(ev);

         } catch (IOException e) {
            UI.reportMessage("unable to save file" +e);
         } finally {
            TextEdit newfile = (TextEdit)FvContext.getcontext(
               currview, instance).at();
            //trace("newfile = " + newfile);
            UI.connectfv(newfile, currview);
         }
      }
   }

   public static void main (String args[]) {
      try {
         //new editgroup(null);
         //perftest();
         make("test1\ntest2");
         instance.finish();
         Tools.Assert(instance.contains(2), instance);
         instance.checkpoint();
         //trace("instance[1] " + instance.at(1));
         //trace("instance[2] " + instance.at(2));
//     trace("instance[3] " + instance.at(3));
         Tools.Assert((instance.at(1)).finish() == 2,
            Integer.valueOf((instance.at(1)).finish()));
         Tools.Assert((instance.at(2)).finish() == 2,
            Integer.valueOf((instance.at(1)).finish()));
         instance.remove(1, 1);
         instance.checkpoint();
         //trace("instance[1] " + instance.at(1));
         Tools.Assert(instance.finish()== 2, instance.finish());
         instance.undo();

         //Tools.Assert(instance.finish()== 3, instance.finish());
         findModified();
         instance.disposeFvc();
         trace("test executed successfully");
      } catch (Throwable e) {
         trace("main caught exception " + e);
         e.printStackTrace();
      }
      System.exit(0);

   }
}
