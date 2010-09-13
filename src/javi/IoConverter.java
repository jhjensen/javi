package javi;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;

/** this is an abstract class which defines the necessary methods
    for an editvec to to IO
*/
class FileProperties<OType> implements Serializable {
   private static String staticLine = System.getProperty("line.separator");
   final FileDescriptor fdes;
   final ClassConverter<OType> conv;
   private String lsep = staticLine; //??? final

   FileProperties(FileDescriptor fd, ClassConverter<OType> convi) {
      fdes = fd;
      conv = convi;
   }

   public String toString() {
      return fdes.toString();
   }

   String getSeperator() {
      return lsep;
   }

   void setSeperator(String sep) {
      //e! resets this if (lsep != staticLine)
      //   throw new RuntimeException("attempt to reset line seperator");
      lsep = sep;
   }
}


public class IoConverter<OType> implements Runnable, Serializable {
   private static final long serialVersionUID = 1;

   final FileProperties<OType> prop;
   private transient EditCache<OType> ioarray;
   private transient EditCache<OType> mainArray = null;
   private transient Thread  rthread;
   private transient UndoHistory.BackupStatus backupstatus;
   private transient BuildCB aNotify;
   private transient ThreadState tstate;
   private transient boolean swapArray;

   private enum ThreadState { INIT, INITSTART, STARTED, FINISHED };


   IoConverter(FileProperties<OType> fpi, boolean quickThread) {
      tstate = quickThread
               ? ThreadState.INITSTART
               : ThreadState.INIT;
      prop = fpi;
   }

   private void readObject(java.io.ObjectInputStream is) throws
         ClassNotFoundException, java.io.IOException {
      is.defaultReadObject();
      tstate = ThreadState.INIT;
   }

   OType getnext() {
      return null;
   }

   public synchronized void dispose() throws IOException {
      //trace("rthread = " + rthread);
      try {
         if (rthread != null) {
            rthread.interrupt();
            //trace ("waiting for thread to die " +this);
            wait(1000);
            if (rthread != null)
               UI.popError("thread didn't die " + this , null);
         }
      } catch (InterruptedException e) {
         UI.popError("IoConverter caught ", e);
      }
      truncIo();
      aNotify = null;
      ioarray = null;
      mainArray = null;


   }

   final KeyHandler getKeyHandler() {
      //trace("ioc getKeyHandler");
      return null;
   }

   public final String toString() {
      return prop.fdes.shortName;
   }

   final synchronized void init1(EditCache<OType> evi, BuildCB arr) {
      if (tstate == ThreadState.INITSTART)
         startThread();
      ioarray = evi;
      mainArray = ioarray;
      aNotify = arr;
      //trace("IoConverter.java init1 "  + fdes.canonName);
   }

   /** inserts a stream in the indicated position in the vector.  the stream
       is interpreted by the iocontroller for this editvector.
   @param input the stream to be inserted
   @param index the position to insert the stream
   this uses getnext, So getnext must be implemented for this to work
   This should only be called from editvec.
   */

   final EditCache<OType> convertStream() throws
         IOException {
      //trace("index = " + index + " " + fdes + "class " + this.getClass());

      EditCache<OType> ret = new EditCache<OType>();
      //trace("retval " + retval);
      OType ob;
      while ((ob = getnext()) != null)
         ret.add(ob);

      return ret;
   }

   final void reload() {
      //trace("reload state = "  + tstate);
      try {
         preRun();
         dorun();
      } catch (InterruptedException ad) {
         UI.popError("IoConverter caught ", ad);
      } catch (IOException ie) {
         UI.popError("IoConverter caught ", ie);
      } finally {
         truncIo();
      }
   }
// should not be called after returning 0.
//This should only be called from editvec.

   protected final synchronized void startThread() {
      //Thread.dumpStack();
      //trace("starting thread " + this);
      tstate = ThreadState.STARTED;
      rthread = new Thread(this, "Thread " + prop.fdes.shortName);
      //trace("creating rthread " + rthread);
      rthread.start();
   }

   abstract static class BuildCB {
      abstract void notify(EditCache ed);
      abstract UndoHistory.BackupStatus getBackupStatus();
   }

   final boolean expand(int desired) throws IOException {
      // this layer prevents a deadlock
      int eret;
      while (2 == (eret = expandLock(desired)))
         EventQueue.biglock2.lock();
      return eret == 0
             ? false
             : true;
   }


   final synchronized int expandLock(int desired) throws IOException {

      //trace("enter expand "  + this + " desired = " + desired);

      while (true)  {

         //trace(" ioarray.size " + ioarray.size() + " mainArrya " + mainArray.size());
         //trace(" backupstatus " + backupstatus);
         //trace(" tstate " + tstate);
         switch (tstate)  {

            case INITSTART:
            case INIT:
               if ((desired != 0  && desired <= mainArray.size()))
                  return 0;
               startThread();
               continue;
            case STARTED:
               if (mainArray.size() >= desired)
                  return 0;
               try {
                  //trace("about to wait 2000");
                  EventQueue.biglock2.unlock();
                  wait(2000); //??????? jhj fix
                  return 2;
                  //trace("done to wait 2000");
               } catch (InterruptedException ex) {
                  /* Ignore Interrupts */
                  UI.popError("ignored Interrupted Exception", null);
               }
               continue;
            case FINISHED:
               if (swapArray) {
                  mainArray = ioarray;
                  aNotify.notify(mainArray);
               } else {
                  ioarray = mainArray;
               }
               if (ioarray != mainArray)
                  ioarray.clear();
               return 1;
         }
      }
   }

   final void dumpCollection(String name, Collection cont) {
      trace(name);
      for (Object obj : cont)
         trace("   "  + obj);
   }

// this is a runtime exception so that report in javacompiler can throw it.
//   static class DoneAdding extends RuntimeException {
//      private static final long serialVersionUID = 1;
//   }

   protected final synchronized void addElement(OType ob) {
      //trace("add element ob " +ob );
      ioarray.add(ob);
   }

   void dorun() throws InterruptedException {
      OType ob;
      while ((ob = getnext()) != null) //??? get rid of getnext
         addElement(ob);
   }

   protected void preRun() throws IOException {
   }

   protected void truncIo() {
      if (rthread != null)
         rthread.interrupt();
   }

   final boolean handleDiff(OType fileObj, OType backObj, int index) {
      //trace("handleDiff fileObj " +fileObj + " backObj "  + backObj);
      try {
         FileDescriptor.LocalFile tfile =
            FileDescriptor.LocalFile.createTempFile("javi", ".tmp");
         tfile.deleteOnExit();
         tfile.writeAll(new StringIter(
            mainArray.iterator()), prop.getSeperator());

         return (UI.reportDiff(prop.fdes.shortName, index, fileObj,
                               backObj, backupstatus, tfile.shortName));

         //trace("setting backupstatus to null mainArray == ioarray");
         //trace("ioarray " + ioarray + " mainArray " + mainArray);
      } catch (IOException e) {
         UI.popError(
            "difference in files detected , error trying to display", e);
      }
      return false;
   }

   public final void run() {
      try {
         Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
         synchronized (this) {
            backupstatus = aNotify.getBackupStatus();
            //trace("backupstatus " + backupstatus);
            if (backupstatus != null) {
               //trace("changeing ioarray to ecache");
               ioarray = new EditCache<OType>();
               ioarray.add(prop.conv.fromString(""));
            }

         }

         preRun();
         //trace("before dorun ioarray size =" +ioarray.size() + " " + this);
         dorun();
         //trace("after dorun ioarray size=" + ioarray.size() + " " + this);
      } catch (Throwable e) {
         UI.popError("IoConverter caught ", e);
      }
      OType backObj = null;
      OType fileObj = null;
      int compIndex = 0;
      synchronized (this) {
         if (null != backupstatus)  {
            assert (ioarray != mainArray);
            int maxcomp = ioarray.size() < mainArray.size()
                          ? ioarray.size()
                          :  mainArray.size();
            for (; compIndex < maxcomp; compIndex++) {
               backObj = mainArray.get(compIndex);
               fileObj = ioarray.get(compIndex);
               if (!fileObj.equals(backObj)) {
                  break;
               }
            }
            if (fileObj.equals(backObj))
               fileObj = backObj = null;

            if (ioarray.size() > mainArray.size())
               fileObj = ioarray.get(mainArray.size());
         }
      }
      boolean tmpswp = (fileObj != null || backObj != null)
                       ? handleDiff(fileObj, backObj, compIndex)
                       : false;

      synchronized (this) {
         swapArray = tmpswp;
         backupstatus = null;
         rthread = null;
         //trace("thread finished , notify all" + this);
         tstate = ThreadState.FINISHED;
         truncIo();
         notifyAll();
      }
      //trace("run exit reached " + this);
   }

   static final void trace(String str) {
      Tools.trace(str, 1);
   }
}
