package javi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.NoSuchElementException;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import history.ByteInput;
import java.io.DataOutputStream;
import java.io.Serializable;

/** This class is the basic storage for an editor written in java.

   It implements a vector that fills itself automaticly from
   a stream.  this allows the editor to  work with  a large, or networked file
   without waiting for the entire file. to be read in.</P>

   It has undo capability which allows infinite undo to the start of the editing
   session. It also allows undo to a particular element. It also allows undo
   to a particular element.</p>

   it has position tracking capability. this allows marking a position in
   a file that follows a particular element. If element before
   it are added or deleted.</p>

   Care must be taken to use contains() to protect references
   to the finish method, because any reference to the finish method
   does not return until the stream is completely read. </P>

   likewise it is necessary to call contains before calling at() if the
   editvec is not finished reading.  Otherwise an exception may be thrown
   for a element that really is in the file.</p>

   It should be noted that the file iocontroller assumes that
   the cannonical name
   of a file is unique, which is not really true on MS-DOS where you
   can get different cannonical names for the same file.  this can
   cause registeruniq to register the same file twice.</p>
*/

interface ReAnimator {  // private for now might be public later
   void disposeFvc() throws IOException;
   void reAnimate();
};

public class EditContainer<OType> implements
   Serializable, ReAnimator, Iterable<OType> {

   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";
   private static final long serialVersionUID = 1;

   final String getName() {
      return prop.fdes.shortName;
   }

   final FileDescriptor fdes() {
      return prop.fdes;
   }

   KeyHandler getKeyHandler() {
      return ioc.getKeyHandler();
   }

   final void reload() { // ??? should go a way with inherited extext
      reload(true);
      ioc.reload();
   }

   final void clearUndo() {
      trace("clearUndo " + this);
      backup.ereset();
      backup.baseRecord();
   }

   final void reload(boolean cleararray) {
      //trace("cleararray = " + cleararray);
      backupMade = false;
      backup.ereset();
      if (cleararray)
         ecache.clear(1);
      finishedread = false;
   }

   private transient EditCache<OType> ecache;

   private final IoConverter<OType> ioc;

   /** head of linked list for fvcontexts that point to this editvec */

   private final FileProperties<OType> prop;
   private final EditContainer parent;

   private transient UndoHistory backup;
   private transient boolean finishedread = false;
   private transient boolean ioError = false;
   private transient boolean backupMade;
   private transient boolean readonly = false;
   private transient Vector<MovePos> fixedpos;
   private transient UndoHistory.ChangeRecord []prototypes;

   private void writeObject(java.io.ObjectOutputStream os) throws IOException {
      os.defaultWriteObject();
      trace("saving state for " + this);
      Thread.dumpStack();
   }
   private void readObject(java.io.ObjectInputStream is) throws
         IOException, ClassNotFoundException {
      is.defaultReadObject();
      common(null);
   }


   final void saveList(java.io.ObjectOutputStream os) throws IOException {
      os.writeInt(ecache.size());
      Iterator<OType> eve = iterator();
      while (eve.hasNext()) {
         OType te = (OType) eve.next();
         os.writeObject(te);
      }
   }

   final void restoreList(java.io.ObjectInputStream is) throws
      IOException , ClassNotFoundException {
      int count = is.readInt();
      for (int ii = 1; ii < count; ii++) {
         OType te = (OType) is.readObject();
         ecache.add(te);
      }
      //trace("at end of restore dump list " + this); dump();
   }

   final boolean getError() {
      return ioError;
   }

   final void dump() {
      trace("dumping " + this);
      ecache.dump();
   }

// objects that want to hear about certain events
   private static final ArrayList<FileStatusListener> listeners =
      new ArrayList<FileStatusListener>();

// hash table of files edited, used to keep files unique
   private static HashMap<FileDescriptor, EditContainer> filehash =
      new HashMap<FileDescriptor, EditContainer>();

   static {
      EventQueue.registerIdle(new IdleHandler());
   }

   static void dumpStatic() {
      Thread.dumpStack();
      trace("dump listeners");
      for (Object obj : listeners)
         System.err.println("      listener : " + obj);
      trace("dump filelist");
      for (Object obj : filehash.entrySet())
         System.err.println("      file : " + obj);
   }

   private void cleanup() {
      EventQueue.biglock2.assertOwned();
      //trace("start clean up " + this);
      filehash.remove(fdes());
      synchronized  (listeners) {
         Iterator<FileStatusListener> it;
         for (it = listeners.iterator(); it.hasNext();)
            if (it.next().fileDisposed(this))
               it.remove();
         //trace("end clean up " + this);
      }
   }

//for debugging only.  disposes of vector without doing backup.
   final void terminate() {
      backup.terminate();
      cleanup();
   }

// should generally be called from FvContext
   public void disposeFvc() throws IOException {

      //trace("dispose " + super.toString()  + "\"" + prop.fdes.canonName+  "\" class = " + prop.conv.getClass() );
      cleanup();
      fixedpos = null;
      ecache = null;
      try {
         ioc.dispose();
      } finally {
         try {
            if (backup != null)
               backup.close();
         } finally {
            backup = null;
         }
      }
   }

   boolean isValid() {
      return ecache != null;
   }

   static void disposeAll() throws IOException {
      Iterator<Map.Entry<FileDescriptor, EditContainer>> eve;
      for (eve = filehash.entrySet().iterator(); eve.hasNext();)
         try {
            Map.Entry<FileDescriptor, EditContainer> me = eve.next();
            EditContainer ec = me.getValue();
            eve.remove();
            ec.disposeFvc();
            //trace("!!!! disposeing of unexpected file, should have been done in fvcontext" + prop.fdes.canonName);
         } catch (Throwable t) {
            trace("disposeall caught " + t);
         }
   }

   private void common(OType[] inarr) {
      //trace("common " + this);
      if (backup != null)
         return;
      if (ioc == null)
         throw new RuntimeException("editvec must have ioc");
      if (parent == null)
         if (!("root EditContainer 1".equals(prop.fdes.canonName)))
            throw new RuntimeException("editvec must have parent except root");
      //trace("creating editvec type =" + prop.conv.getClass());

      UndoHistory.ChangeRecord[] temp = {
         new DeleteRecord(),
         new InsertRecord(),
         new ModRecord(),
         new InsertStringRecord(),
      };
      prototypes = temp;

      ecache = new EditCache<OType>();
      ioc.init1(ecache, new ArrayChange());
      OType ob =  prop.conv.fromString("");

      backup = new UndoHistory(prop, prototypes);
      ecache.add(ob);

      if (inarr != null) {
         if (prop.conv.fromString("").getClass() != inarr[0].getClass())
            throw new RuntimeException(
               "editvec called with invalid type inarr[0] class:"
               + inarr[0].getClass());
         ecache.add(inarr);
      }
      registeruniq();
      //if (toString().equals(""))
      //   Thread.dumpStack();
      //trace("ev common  " + this + " parent " + (parent == null ? "NULL" : parent.toString())  /*+ " backup = " + backup*/);
      //if (root == parent)
      //   Thread.dumpStack();
      //trace("at end of common dump "); dump();

   }

   public final void reAnimate() {
      common(null);
   }
   /**
   @param ioci iocontroller for this edit vec.
   the
   <a href="javi.nullparser.html">
   nullparser  </a>
   is a useful
   iocontroller for editvecs that just have strings in them.
   */

   EditContainer(IoConverter<OType> ioci, OType[] inarr,
         EditContainer parenti, FileProperties<OType> propi) {
      ioc = ioci;
      prop = propi;
      parent = parenti;
      common(inarr);
   }

   /**
   @param ioci iocontroller for this edit vec.
   the
   <a href="javi.nullparser.html">
   nullparser  </a>
   is a useful
   iocontroller for editvecs that just have strings in them.
   */

   EditContainer(IoConverter<OType> ioci, EditContainer parenti,
         FileProperties<OType> propi) {
      ioc = ioci;
      prop = propi;
      parent = parenti;
      common(null);
   }


   /** editvec will notify object of evlistener events */
   static void registerListener(FileStatusListener evl) {
      synchronized  (listeners) {
         //trace("listeners add");
         //Thread.dumpStack();
         listeners.add(evl);
         //trace("done listeners add");
      }
   }

   static void unRegisterListener(FileStatusListener evl) {
      synchronized  (listeners) {
         //trace("unRegisterListener");
         //Thread.dumpStack();
         listeners.remove(evl);
      }
   }

   /** find an editvec given the registered cannonical name */
   static EditContainer findfile(FileDescriptor fh)  {
      EventQueue.biglock2.assertOwned();
      //trace("looking for " + fh);
      //trace("returning " + filehash.get(fh));
      return filehash.get(fh);
   }

//static Matcher normalize1 =  Pattern.compile("(\\\\|/)").matcher("");
//static Matcher normalize2 =  Pattern.compile("\\.").matcher("");

   static EditContainer grepfile(String spec) {
      // jdk1.5 change to LITERAL
//   normalize1.reset(spec);
//   spec = normalize1.replaceAll("(\\\\\\\\|/)");
//   normalize2.reset(spec);
//   spec = normalize2.replaceAll("\\\\.");
      spec = Pattern.quote(spec);
      spec = spec + '$';
      //trace("grepfile " + spec);
      try {
         Matcher regex =  Pattern.compile(spec,
            Pattern.CASE_INSENSITIVE).matcher("");
         //trace("grepfile pattern " + regex.pattern().pattern());
         EventQueue.biglock2.assertOwned();
         for (Map.Entry<FileDescriptor, EditContainer> me : filehash.entrySet())  {
            String cname = me.getKey().shortName;
            //trace("matching against " + cname);
            regex.reset(cname);
            if (regex.find()) {
               EditContainer retval = me.getValue();

               //trace("matched against " + cname);
//               if (retval.ioc instanceof FileInput)
               return retval;
//               else
//                 trace("not a filereader");
            }
         }
      } catch (java.util.regex.PatternSyntaxException e) {
         return null;
      }
      return null;
   }

   enum SearchType { LINE , END, START };

   @SuppressWarnings("fallthrough")
   final Position regsearch(Position start, Position finish, boolean direction,
         Matcher exp, int offset, SearchType type) {

      // this doesn't quite work right.  It will search the entire
      //     finish line.  This seems unimportant currently.

      //trace("regsearch direction " + direction + " start " + start + " finish " + finish);
      int lptr;
      lptr = start.y;
      if (exp == null)
         throw new RuntimeException();
      if (!direction) {
         if (!searchForward(exp, start.x + 1, lptr))
            for (lptr++; true; lptr++) {
               if (!containsNow(lptr))
                  lptr = 1;  // wrap
               if (searchForward(exp, 0, lptr))
                  break;
               if (lptr == finish.y)
                  return null;
            }
      } else if ((start.x <= 0) || !searchBackward(exp, start.x - 1, lptr)) {
         for (lptr--; true; lptr--) {
            if (lptr == 0)
               lptr = finish() - 1; // wrap
            if (searchBackward(exp, -1 , lptr))
               break;
            if (lptr == finish.y)
               return null;
         }
      }
      //trace("EditContainer regsearch type " + type + " offset " + offset + " lptr " + lptr + "readIn " + readIn());
      switch (type) {
         default:
            trace("received unexpected regsearch type");
            return null;
         case LINE:
            return new Position(exp.start(), lptr + offset, prop.fdes, null);
         case END:
            return new Position(exp.end() + offset, lptr, prop.fdes, null);
         case START:
            return new Position(exp.start() + offset, lptr, prop.fdes, null);
      }
   }

   final boolean parentEq(EditContainer ev) {
      //trace("parentEq ev "+ ev + " parent " + parent);
      return ev == parent;
   }

   /** tell editvec class that this editvecs name is unique and an
          attempt to open another editvec with the same name is an error */
   private void registeruniq() {
      EventQueue.biglock2.assertOwned();
      //trace("register uniq:" + prop.fdes.canonName);
      if (filehash.put(fdes(), this) != null)
         throw new RuntimeException("non unique file added " + fdes() + this);

      synchronized  (listeners) {
         for (FileStatusListener evl : listeners)
            evl.fileAdded(this);
      }
   }

   private static class IdleHandler implements EventQueue.Idler {
      public void idle() throws IOException {

         //trace("idle handler ");
         EventQueue.biglock2.assertOwned();
         for (EditContainer ev : filehash.values())
            if (ev.backup != null)
               try {
                  //trace("backing up " + ev);
                  ev.backup.idleSave();
               } catch (Throwable e) {
                  UI.popError("Problem with backup File starting over" , e);
                  ev.reload(false);
               }
      }
   }

// for testing
   final void idleSave() throws IOException {
      backup.idleSave();
   }

   final void finishedRead() {
      //trace(this + " backupMade = " + backupMade);
      if (finishedread)
         return;
      //trace(this + " backupMade = " + backupMade);
      //for (int i=0;i<ecache.size();i++)
      //   trace("obj[i] = " +at(i));
      finishedread = true;
      if (!backupMade) {
         while (ecache.size() <= 1) {
            OType [] obarray = (OType []) new Object[1];
            obarray[0] =  prop.conv.fromString("");
            //trace("creating entry for " + this);
            insertRecord(obarray, 0);
         }
         backup.baseRecord();
      }
      //trace("exit " + this);
   }

   class ArrayChange extends IoConverter.BuildCB {
      void notify(EditCache ec) {
         //trace("ArrayChange notified");
         ecache = ec;
         reload(false);
      }

      UndoHistory.BackupStatus getBackupStatus() {
         FileDescriptor fd = prop.fdes.getPersistantFd();
         if (fd != null) {
            if  (fd.exists()) {
               if (fd.canWrite()) {
                  try {
                     Exception seterror = backup.setFile(
                        ((FileDescriptor.LocalFile) fd).fh);
                     ecache.clear(1); // why do I need this?
                     UndoHistory.BackupStatus readError = backup.readToQuit();
                     backupMade = true;

                     return seterror == null
                            ? readError
                            : new UndoHistory.BackupStatus(
                               false, false, seterror);
                  } catch (Throwable e) {
                     trace("backup failed exception = " + e);
                     backup.ereset();
                     e.printStackTrace();
                     return new UndoHistory.BackupStatus(false, false, e);
                  }
               } else {
                  return new UndoHistory.BackupStatus(false, false,
                     new IOException("backup file not writeable"));
               }
            }
         }

         return null;
      }

   }

   private void myexpand(int desired) {
      //trace("desired " + desired  + " this " + this);
      try {
         boolean finished = ioc.expand(/*ecache,*/desired);
         if (finished) {
            finishedRead();
//      } else  if ((ecache.size()<desired)  && ( desired!=-1)) {
//???         finishedRead();
         }
      } catch (IOException e) {
         UI.popError("in my expand ", e);
         finishedRead();
         ioError = true;
      }
      //trace("maxline " + maxline + " desired " + desired);
      //trace("ecache.size " + ecache.size());
      //trace("finishedread " + finishedread);
   }

   /** This routine returns the number of elements in the vector.  It will not
   return until the entire input stream is read.
   */

   public final int finish() {
      //trace("finish finishedread " + finishedread + " " + getName());
      if (!finishedread)
         myexpand(Integer.MAX_VALUE);
      //trace(this + " ecache.size = " + ecache.size() + " " +  curr);
      return ecache.size();
   }

   /** returns true if IO is complete
       also reads :*/
   final boolean donereading() {
//trace(this);
      if (finishedread)
         return true;
      myexpand(0);
      return finishedread;
   }

   /** This routine number of elements currently in the vector.  It does not
   wait for the input stream to finish reading.
   */
   final int readIn() {
      if (!finishedread)
         myexpand(0);
      return ecache.size();
   }

   /** checks that the specified element is in the editvec.  If IO is still
       in progress contains will wait until the element is read in.
   @param desired the desired element number
   */
   final boolean contains(int desired) {
      //trace("requested = " + desired + " finishedread = " + finishedread + " editvec = " + this);
      if (!finishedread)
         myexpand(desired + 1);
      //trace(this + "!contain desired = " + desired );
      return desired < ecache.size();
   }

   final boolean containsNow(int desired) {
      //trace("requested = " + desired + " editvec = " + this);
      if (!finishedread)
         myexpand(0);
      //trace(this + "!contain desired = " + desired);
      return desired < ecache.size();
   }

   /** find an object in this vector.  This is not a fast operation currently
   @param ob object searched for
   */
   final int indexOf(OType ob) {

      for (int i = 1;; i++) {
         if (i >= ecache.size())
            if (finishedread)
               return -1;
            else
               myexpand(i + 1);
         if (ecache.get(i) == ob)
            return i;
      }
   }

   /** setReadOnly flag.  This allows the editvec to be written to.  an
       attempt to write the file may still fail if the
       file permissions do not allow writing and renameing.
   */
   final void setReadOnly(boolean flag) throws IOException {
      if (this == TextEdit.root)
         throw new IOException("not allowed to make this writable");
      readonly = flag;
   }
   /** checks the read only flag of this editvec.  This may or may not match
       the read only status of an underlying file. */
   final boolean getReadOnly() {
      return readonly;
   }

   public String toString() {
      return (ioError
              ? "!!! IOError reading in file "
              : "")
             + prop.fdes.shortName
             + (isModified()
                ? " MODIFIED "
                : " ")
             + (ecache == null
                ? "!!!! disposed"
                : "")

             + prop.fdes.canonName;
   }
   /** this registers a position as fixed.  if a element is deleted before this
       position in the file the position is modified to point at the new element
       number. huge numbers of fixed position should be avoided because
       it will affect insert and delete performance.
   */

   final void fixposition(Position p) {
//trace("p = " + p);
      if (fixedpos == null)
         fixedpos = new Vector<MovePos>();
      fixedpos.add(new MovePos(p));
   }


   /** this un-registers a position as fixed. */
   final void unfixposition(Position p) {
//trace("p = " + p);
      if (fixedpos != null) {
         fixedpos.remove(new MovePos(p));
         if (fixedpos.size() == 0)
            fixedpos = null;
      }
   }

   final OType insertStream(BufferedReader input, int index) throws
         InputException, IOException {

      //trace("input = " + input);
      finish();
      if (ioc instanceof BufInIoc) {
         EditCache<OType> ed = ((BufInIoc) ioc).convertStream(input);
         if (ed.size() > 0) {
            //trace("inserting stream :");
            insertObjs(ed, index);
            return ed.get(0);
         } else
            return null;
      } else
         throw new InputException("trying to input stream into non input file");
   }

   final UndoHistory.EhMark copyCurr() {
      if (backup == null)
         return null;
      return backup.copyCurr();
   }

   final synchronized int undo() throws IOException {
      finish();
      if (getReadOnly())
         throw new ReadOnlyException(this, fdes().shortName);

      return backup.undo();
   }

   final synchronized int redo() throws IOException {
      finish();
      //??? nice to have (currmark.hasNext()))
      if (getReadOnly())
         throw new ReadOnlyException(this, fdes().shortName);
      return backup.redo();
   }

   /*
   synchronized void undoElement(int index)
      throws readOnlyException {
      finish();
      changerecord.undoElement(this,index);
   }
   */


   /** get multiple objects at a given index */
   /** this function removes the specified elements from the editvec*/

   public final synchronized ArrayList<String> getElementsAt(
         int start, int number) {

      if (!contains(start + number))
         number = finish() - start;

      if (number <= 0)
         throw new RuntimeException("get negative number of Elements");

      return ecache.getElementsAt(start, start + number);
   }

   final synchronized ArrayList<String> remove(
         int start, int number) {

      if (!contains(start + number - 1))
         number = ecache.size() - start;
      if (number <= 0)
         throw new RuntimeException(
            "remove called with invalid number of lines");


      ArrayList<String> olist = ecache.rangeAsStrings(start, number + start);

      mkback(start + number - 1);
      deleteRecord(start, number);
      if (ecache.size() <= 1)
         insertOne(prop.conv.fromString(""), 1);
//trace("maxline = " + maxline + " arraysize = " + array.size() );
      return olist;
   }

   final synchronized void moveLine(int from, int to) {
      OType[] arr = ecache.getArr(from, 1);
      if (from > to) {
         mkback(from);
         deleteRecord(from, 1);
         insertRecord(arr, to);
      } else {
         mkback(to);
         insertRecord(arr, to);
         deleteRecord(from, 1);
      }
   }

   final synchronized void copyLine(int from, int to) {
      //trace("copyLine from " + from + " to " + to);
      OType[] arr = ecache.getArr(from, 1);
      mkback((from > to ? from : to) - 1);
      insertRecord(arr, to);
   }


   /** cover routine for insert() that takes a single object. */
   final void insertOne(OType ob, int index) {
      OType[] obarray = (OType []) new Object[1];
      obarray[0] = ob;
      mkback(index - 1);
      insertRecord(obarray, index);
   }

   /** insert an array of objects.  The objects should be the type of object
       that the iocontroller produces.  If a string is passed and the
       iocontroller has a method to convert a string to the proper object
       that will be done.  If a null Object is passed in a RuntimeException
       will be generated.  */
   final synchronized void insertStrings(
         ArrayList<String> obarray, int index) {
      //for (Object ob:obarray) if (ob==null) {Thread.dumpStack() ;trace("inserting null object");}
      //trace("inserting strings at line " + index + " editvec = " + this + " ob.get(0)= " + obarray.get(0).getClass());
      //trace("editvec = " + this + " ob[1]= " + obarray[0].getClass());
      mkback(index - 1);
      insertRecord((obarray.toArray(new String[obarray.size()])), index);
   }

   final synchronized void insertObjs(EditCache<OType> obs, int index) {
      //for (Object ob:obarray) if (ob==null) {Thread.dumpStack() ;trace("inserting null object");}
      //trace("inserting strings at line " + index + " editvec = " + this + " ob.get(0)= " + obarray.get(0).getClass());
      //trace("editvec = " + this + " ob[1]= " + obarray[0].getClass());
      mkback(index - 1);
      insertRecord(obs.iterator(), index, obs.size());
   }

   final void checkpoint() {
      //trace("backup = " + backup);
      backup.checkRecord();
   }

   final synchronized void changeElementAtStr(String ob, int index) {

      //if (ob==null) {Thread.dumpStack() ;trace("changing null object");}
      //trace("ob = " + ob);
      mkback(index);
      modRecord(prop.conv.fromString(ob), index);

   }

   /** substitutes the object at the given index with the new object */
   final synchronized void changeElementAt(OType ob, int index) {

      if (ob instanceof ReAnimator)
         try {
            ((ReAnimator) at(index)).disposeFvc();
         } catch (IOException e) {
            UI.popError("unable to dispose obj", e);
         }
      //Thread.dumpStack() ;trace("changing to object Class" + ob.getClass()) ;
      //if (ob==null) {Thread.dumpStack() ;trace("changing null object");}
      //trace("ob = " + ob);

      mkback(index);

      modRecord(ob, index);

   }

   /** return the object at the given index calls to this function must
       already know that the index is valid by using contains()*/
   public final OType at(int index) {
      //if (varray ==null)
      //   trace("ex = " + toString() + " varray = " + varray);
      return ecache.get(index); //??? mystery cast ???
   }

   /** is this file the same as the written version if the file */
   final synchronized boolean isModified() {
      //trace("isModified " + prop.fdes.shortName  + " finishedread " + finishedread + " backup " + backup);
      return  finishedread
              ? backup == null
              ?  false
              : (!backup.isWritten())
              : false; // currently finish reading before we allow modification.
   }

   final synchronized void forceWritten() {
      backup.forceWritten();
   }

   private void mkback(int index) {
      //trace("mkback");
      finish();
      if (index != 0 && !containsNow(index))
         throw new ArrayIndexOutOfBoundsException(index);
      if (readonly)
         throw new ReadOnlyException(this, fdes().shortName);

      if (!backupMade) {
         FileDescriptor bfd = prop.fdes.getPersistantFd();
         if (bfd != null) {
            if ((ecache.size() > 2) || !"".equals(ecache.get(1).toString())) {
               //trace("adding insert and base record");
               Iterator it = ecache.iterator();
               it.next();
               insertRecordDone(it, 1, ecache.size() - 1);
               backup.baseRecord();
               //trace(" mkback dumping ecache:"); ecache.dump();
            }
            backup.newFile(((FileDescriptor.LocalFile) bfd).fh);
            backupMade = true;
            //trace("backup = " + backup);
         }
      }

   }

// should only be called by changerecords.
   final void addObjects(int cindex, OType [] objs) {
      //trace("cindex = " + cindex + " currsize = " + ecache.size() + " objtype = " + objs[0].getClass() );
      if (objs[0] instanceof ReAnimator)
         for (int i = 0; i < objs.length; i++)
            ((ReAnimator) objs[i]).reAnimate();
      ecache.addAll(cindex, objs);

      if (fixedpos != null)
         for (MovePos p : fixedpos)
            if (p.y >= cindex)
               p.y += objs.length;
   }

   final void deleteObjects(int cindex, Object [] objs) {
      //trace("deleteObjects");
      //trace("cindex = " + cindex + " currsize = " + ecache.size() + " objtype = " + objs[0].getClass() );

      int number = objs.length + cindex;
      ecache.clear(cindex, number);

      if (fixedpos != null)
         for (MovePos p : fixedpos)
            if (p.y >= cindex)
               p.y -= number;
      if (objs[0] instanceof ReAnimator)
         for (int i = 0; i < objs.length; i++)
            try {
               //trace("disposeFVC about to be called on " + objs[i]);
               ((ReAnimator) objs[i]).disposeFvc();
            } catch (IOException e) {
               UI.popError("unable to dispose obj", e);
            }
   }

   final void addState(StringBuilder sb) {
      //trace("\"" + prop.fdes.shortName +  "\" class = " + prop.conv.getClass() );
      if (!donereading())
         sb.append(" still reading file");

      if (backup != null) // should never happen, but it does.
         backup.addState(sb);
      else
         sb.append(" dead file!!!!");

      sb.append("(");
      sb.append(Integer.toString(readIn() - 1));
      sb.append(")");
      sb.append(fdes().shortName);
   }

   static void trace(String str) {
      Tools.trace(str, 1);
   }

   final boolean searchForward(Matcher reg, int charoff, int lineno) {
      //trace("searchForward charoff = " + charoff  + " lineno = " + lineno + "line:" + at(lineno).toString());
      String line = at(lineno).toString();
      if (charoff > line.length())
         return false;
      reg.reset(at(lineno).toString());
      return reg.find(charoff);
   }

   final boolean searchBackward(Matcher reg, int charoff, int lineno) {
      //trace("searchBackward charoff = " + charoff  + " lineno = " + lineno + "line:" + at(lineno).toString());
      String str = at(lineno).toString();

      if (charoff != -1)
         str = str.substring(0, charoff);

      reg.reset(str);
      int lastfound = -1;

      while (reg.find())
         lastfound = reg.start();

      if (lastfound == -1)
         return false;

      reg.reset();
      while (reg.find())
         if (lastfound == reg.start())
            return true;

      throw new RuntimeException("editvec.searchBackward impossible");
   }

   private final class EIter implements Iterator<OType> {
      private int index;
      public boolean hasNext() {
         if (ecache.size() > index)
            return true;
         return index < readIn();
      }

      public OType next() {
         if ((ecache.size() > index) || (readIn() > index)) {
            OType ret = at(index++);
            //trace("next returning " + ret);
            return ret;
         }
         throw new NoSuchElementException();
      }

      public void remove() {
         EditContainer.this.remove(index, 1);
         index--;
      }
   }

   /*
   class StringIter implements Iterator<String> {

         Iterator baseIter = iterator();

         public boolean hasNext() {
            return baseIter.hasNext();
         }

         public String next() {
            return baseIter.next().toString();
         }

         public void remove() {
            throw new UnsupportedOperationException();
         }
   }
   */

   final Iterator<String> getStringIter() {
      return new StringIter(iterator());
   }

   public final Iterator<OType> iterator() {
      Iterator<OType> ret = new EIter();
      ret.next(); // skip 0 untill zero based
      return ret;

   }

   final void printout() throws IOException {
      //trace("editvec.printout " + this);

      mkback(0);
      FileDescriptor.LocalFile tempFile =
         FileDescriptor.LocalFile.make(prop.fdes.canonName + ".new");

      try {
         tempFile.writeAll(getStringIter(), prop.getSeperator());

      } catch (IOException e) {
         tempFile.delete();
         throw e;
      }
      tempFile.movefile(prop.fdes.canonName);
      backup.writeRecord();
      synchronized  (listeners) {
         for (FileStatusListener evl : listeners)
            evl.fileWritten(this);
      }
   }

   final void backup(String extension) throws IOException {

      FileDescriptor.LocalFile file2 =
         FileDescriptor.LocalFile.make(prop.fdes.shortName + extension);

      if (!prop.fdes.canWrite()
            && "Microsoft Corp.".equals(System.getProperty("java.vendor")))
         Tools.execute("d:\\cygwin\\bin\\chmod +w " + prop.fdes.canonName);

      prop.fdes.renameTo(file2);

      prop.fdes.writeAll(getStringIter(), prop.getSeperator());
      setReadOnly(false);
   }

   private void insertRecord(OType[] obarray, int indexi) {
      backup.push(new InsertRecord(obarray, indexi));
   }
   private void insertRecord(String[] obarray, int indexi) {
      backup.push(new InsertStringRecord(obarray, indexi));
   }
   private void insertRecord(Iterator it, int indexi, int count) {
      backup.push(new InsertRecord(it, indexi, count, true));
   }
   private void insertRecordDone(Iterator it, int indexi, int count) {
      backup.push(new InsertRecord(it, indexi, count, false));
   }
   private void modRecord(OType ob, int index) {
      //trace("ModRecord ob " + ob);
      backup.push(new ModRecord(ob, index));
   }
   private void deleteRecord(int start, int number) {
      //trace("DeleteRecord");
      backup.push(new DeleteRecord(start, number));
   }
   private class InsertRecord extends UndoHistory.ChangeRecord<OType> {
      private OType[] obj;

      void readExternal(ByteInput dis, ClassConverter conv) {
         //trace("InsertRecord.readExternal");
         super.readExternal(dis, conv);
         obj = super.readObjs(dis, conv);
      }

      void writeExternal(DataOutputStream dos, ClassConverter conv) {
         super.writeExternal(dos, conv);
         super.writeObjs(dos, conv, obj);
      }

      byte getType() {
         return INSERT;
      }

      InsertRecord newOne() {
         return new InsertRecord();
      }

      public String toString() {
         StringBuilder str = new StringBuilder('i' + cindex  + ':'
                                               + obj.length + ':');
         for (OType elem : obj) {
            str.append(elem);
            str.append('\n');
         }
         return str.toString();
      }
      InsertRecord() { }
      InsertRecord(OType[] obarray, int indexi) {
         super(indexi);
         //trace("InsertRecord ob type = " + obarray[0].getClass());
         obj = obarray;
         redocr();
         //trace("InsertRecord " + this  + " ev = " + ev  + " InsertRecord type = " + obj.getClass());
         //trace("InsertRecord array.size = " + ev.array.size());
         //trace("InsertRecord array.finish = " + ev.finish());
      }

      InsertRecord(Iterator<OType> it, int indexi, int count, boolean redo) {
         super(indexi);
         obj = (OType []) new Object[count];
         for (int i = 0; i < count; i++) {
            obj[i] = it.next();
            //trace("InsertRecord(it): inserted " + obj[i]);
         }
         if (redo)
            redocr();
         //trace("InsertRecord " + this  + " ev = " +/* this.EditContainer */ "???" + " InsertRecord obj = " + obj);
         //trace(" ecache.size = " + ecache.size());
         //trace("ecache.finish = " + finish());
      }

      int redocr() {

         addObjects(cindex, obj);
         return cindex;
      }

      int undocr() {
         deleteObjects(cindex, obj);
         return cindex;
      }
      boolean redocr(View.ChangeOpt vi) {
         return vi.insert(cindex, obj.length);
      }

      boolean undocr(View.ChangeOpt vi) {
         return vi.delete(cindex, obj.length);
      }
   }

   private class InsertStringRecord extends UndoHistory.ChangeRecord<OType> {
      private String[] obj;

      void readExternal(ByteInput dis, ClassConverter conv) {
         //trace("InsertStringRecord.readExternal");
         super.readExternal(dis, conv);
         obj = readStrs(dis);
      }

      void writeExternal(DataOutputStream dos, ClassConverter conv) {
         super.writeExternal(dos, conv);
         writeStrs(dos, obj);
      }

      String[] readStrs(ByteInput dis) {
         int ocount = dis.readInt();
         String []xobj = new String[ocount];
         while (--ocount >= 0)
            xobj[ocount] = dis.readUTF();
         return xobj;
      }
      void writeStrs(DataOutputStream dos, String [] xobj) {
         try {
            int ocount = xobj.length;
            dos.writeInt(ocount);
            while (--ocount >= 0)
               dos.writeUTF(xobj[ocount]);
         } catch (IOException e) {
            throw new RuntimeException(
               "InsertStringRecord.writeExternal unexpected " , e);
         }
      }

      byte getType() {
         return INSERTSTRING;
      }

      InsertStringRecord newOne() {
         return new InsertStringRecord();
      }

      public String toString() {
         StringBuilder str = new StringBuilder("s" + cindex  + ':'
                                               + obj.length + ':');
         for (String elem : obj) {
            str.append(elem);
            str.append('\n');
         }
         return str.toString();
      }

      InsertStringRecord() { }

      InsertStringRecord(String[] obarray, int indexi) {
         super(indexi);
         //trace("InsertStringRecord ob type = " + obarray[0].getClass());
         obj = obarray;
         redocr();
         //trace("InsertStringRecord " + this.toString()  + " InsertStringRecord type = " + obj.getClass());
      }

      InsertStringRecord(Iterator<String> it, int indexi, int count) {
         super(indexi);
         obj = new String[count];
         for (int i = 0; i < count; i++) {
            obj[i] = it.next();
            //trace("InsertRecord(it): size = " + ev.varray.size() + " inserted " + obj[i]);
         }
         //trace("InsertRecord " + this  + " curr = "  + ev.curr + " ev = " + ev  + " InsertRecord obj = " + obj);
         //        trace(" array.size = " + ev.array.size());
         //        trace("array.finish = " + ev.finish());
      }
      OType[] mkobj() {
         OType[] ob2 = (OType []) new Object[obj.length];
         for (int i = 0; i < obj.length; i++) {
            //trace("ar = " + objs + " i = " + i);
            OType nobj = prop.conv.fromString(obj[i]);
            if (nobj == null)
               throw new RuntimeException(
                  "inserting null made from object from " + obj[i]);
            ob2[i] = nobj;
         }
         return ob2;
      }

      int redocr() {
         addObjects(cindex, mkobj());
         return cindex;
      }

      int undocr() {
         deleteObjects(cindex, obj);
         return cindex;
      }
      boolean redocr(View.ChangeOpt vi) {
         return vi.insert(cindex, obj.length);
      }

      boolean undocr(View.ChangeOpt vi) {
         return vi.delete(cindex, obj.length);
      }
   }

   private class DeleteRecord extends UndoHistory.ChangeRecord<OType> {

      private OType[] obj;

      public String toString() {
         StringBuilder str = new StringBuilder("d" + cindex  + ":"
                                               + obj.length + ":");
         for (OType elem : obj)  {
            str.append(elem);
            str.append('\n');
         }
         return str.toString();
      }

      byte getType() {
         return DELETE;
      }

      DeleteRecord newOne() {
         return new DeleteRecord();
      }

      void readExternal(ByteInput dis, ClassConverter conv) {
         super.readExternal(dis, conv);
         obj = super.readObjs(dis, conv);
//             trace("readExternal " + this);
      }

      void writeExternal(DataOutputStream dos, ClassConverter conv) {
         super.writeExternal(dos, conv);
         super.writeObjs(dos, conv, obj);
      }

      DeleteRecord() { }

      DeleteRecord(int indexi, int number) {
         super(indexi);
         obj = (OType []) new Object[number];
         for (int i = 0; i < number; i++) {
            obj[i] = at(cindex + i);
         }
         redocr();
         //trace(this + " curr = "  + ev.curr + " ev = " + ev );
      }

      int undocr() {
         addObjects(cindex, obj);
         return cindex;
      }

      int redocr() {
         //trace("at cindex" + cindex);
         //trace("deleteing " + obj.length + " remaining lines " +  ev.readIn());
         deleteObjects(cindex, obj);
         return cindex;
      }
      boolean redocr(View.ChangeOpt vi) {
         return vi.delete(cindex, obj.length);
      }

      boolean undocr(View.ChangeOpt vi) {
         return vi.insert(cindex, obj.length);
      }
   }

   private class ModRecord extends UndoHistory.ChangeRecord<OType> {
      private OType oldobj;
      private OType newobj;

      public String toString() {
         return "m" + cindex  + ":" + newobj.toString()
            + ":" + oldobj.toString();
      }

      void readExternal(ByteInput dis, ClassConverter<OType> conv) {
//             trace("ModRecord.readExternal");
         super.readExternal(dis, conv);
         oldobj = conv.newExternal(dis);
         newobj = conv.newExternal(dis);
      }

      public void writeExternal(DataOutputStream dos, ClassConverter conv) {
         //trace("ModRecord.writeExternal");
         super.writeExternal(dos, conv);
         try {
            conv.saveExternal(oldobj, dos);
            conv.saveExternal(newobj, dos);
         } catch (IOException e) {
            throw new RuntimeException(
               "ModRecord.writeExternal unexpected " , e);
         }
      }

      byte getType() {
         return MOD;
      }

      ModRecord newOne() {
         return new ModRecord();
      }

      ModRecord() { }

      ModRecord(OType newelement, int indexi) {
         super(indexi);
         newobj = newelement; // save old value
         oldobj =  at(cindex);

         redocr();
         //trace(this + " curr = "  + ev.curr + " ev = " + ev );
      }

      // swaps obj in array
      int redocr() {
         //trace("setting to " + newobj);
         ecache.set(cindex, newobj);
         return cindex;
      }

      int undocr() {
         //trace("ModRecord.undocr " + this);
         ecache.set(cindex, oldobj);
         return cindex;
      }
      boolean redocr(View.ChangeOpt vi) {
         return vi.lineChanged(cindex);
      }

      boolean undocr(View.ChangeOpt vi) {
         return vi.lineChanged(cindex);
      }
   }
}
