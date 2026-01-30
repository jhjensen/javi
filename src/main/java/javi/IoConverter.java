package javi;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import static history.Tools.trace;

/**
 * Abstract base class for asynchronous I/O conversion in EditContainer.
 *
 * <p>IoConverter defines the interface for converting input streams into
 * elements for {@link EditContainer}. It supports:
 * <ul>
 *   <li><b>Background loading</b>: Runs in separate thread for non-blocking file reads</li>
 *   <li><b>Lazy expansion</b>: Content loaded on demand as user scrolls</li>
 *   <li><b>Build callbacks</b>: Notification when loading completes</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p><b>WARNING</b>: The {@link #expandLock} method releases {@link EventQueue#biglock2}
 * while holding {@code this} monitor, which can lead to deadlock if another thread
 * acquires locks in opposite order. See BUGS.md B3.</p>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Construct with {@link FileProperties}</li>
 *   <li>{@link #init1} called by EditContainer to set cache and callbacks</li>
 *   <li>{@link #startThread} spawns background reader if needed</li>
 *   <li>{@link #getnext} called repeatedly to read elements</li>
 *   <li>{@link #dispose} cleans up when file closed</li>
 * </ol>
 *
 * <h2>Subclass Implementations</h2>
 * <ul>
 *   <li>{@link StringIoc} - Line-by-line text file reading</li>
 *   <li>{@link ClassConverter} - For non-string element types</li>
 *   <li>{@link PositionIoc} - For position list files</li>
 * </ul>
 *
 * @param <OType> Element type produced (typically String)
 * @see EditContainer
 * @see EditCache
 */
public class IoConverter<OType> implements Runnable, Serializable {
   private static final long serialVersionUID = 1;

   public final FileProperties<OType> prop;
   private transient EditCache<OType> ioarray;
   private transient EditCache<OType> mainArray = null;
   private transient Thread  rthread;
   private transient BackupStatus backupstatus;
   private transient BuildCB aNotify;
   private transient ThreadState tstate;
   private transient boolean swapArray;

   private enum ThreadState { INIT, INITSTART, STARTED, FINISHED };

   public IoConverter(FileProperties<OType> fpi, boolean quickThread) {
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

   public OType getnext() {
      return null;
   }

   void stopIo() {
      rthread.interrupt();
   }

   public synchronized void dispose() throws IOException {
      //trace("rthread = " + rthread);

      while (null != rthread) {
         trace("stopIo rthread", this, "thread", rthread);
         if (UI.stopConverter(toString()))
            stopIo();
         try {
            wait(1000);
            trace("stopIo rthread "  + toString() + " thread " + rthread);
            //trace ("waiting for thread to die " +this);
         } catch (InterruptedException e) {
            UI.popError("IoConverter caught ", e);
         }
      }
      truncIo();
      aNotify = null;
      ioarray = null;
      mainArray = null;
   }

   public final String toString() {
      return prop.fdes.shortName;
   }

   final synchronized void init1(EditCache<OType> evi, BuildCB arr) {
      //trace("init1 tstate " + tstate + this);
      ioarray = evi;
      mainArray = ioarray;
      aNotify = arr;
      if (tstate == ThreadState.INITSTART)
         startThread();
      //trace("IoConverter.java init1 "  + fdes.canonName);
   }

   /** inserts a stream in the indicated position in the vector.  the stream
       is interpreted by the iocontroller for this editvector.
   @param input the stream to be inserted
   @param index the position to insert the stream
   this uses getnext, So getnext must be implemented for this to work
   This should only be called from editvec.
   */

   final EditCache<OType> convertStream() {
      //trace("index = " + index + " " + fdes + "class " + this.getClass());

      EditCache<OType> ret = new EditCache<OType>();
      //trace("ret " + ret);

      for (OType ob; null != (ob = getnext());)
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
      abstract void notifyDone(EditCache ed);
      abstract BackupStatus getBackupStatus();
   }

   /**
    * Expands the buffer to ensure at least 'desired' elements are loaded.
    * This wrapper method handles the complex lock ordering between biglock2
    * and this object's monitor to prevent deadlock.
    *
    * @param desired the minimum number of elements needed
    * @return true if loading is complete, false if still loading
    * @throws IOException if an I/O error occurs during loading
    */
   final boolean expand(int desired) throws IOException {
      // This layer prevents a deadlock by managing lock acquisition order.
      // expandLock() may release biglock2 while holding 'this' monitor
      // (to allow wait() to work), returning 2 to signal we need to
      // reacquire biglock2 before trying again.
      int eret;
      while (2 == (eret = expandLock(desired)))
         EventQueue.biglock2.lock();
      return eret == 0
             ? false
             : true;
   }


   /**
    * Internal method that checks/expands the buffer while holding this monitor.
    *
    * <p><strong>LOCK ORDERING WARNING:</strong> This method releases biglock2
    * while holding 'this' object's monitor (in the STARTED case). This is an
    * intentional pattern to allow wait() to function, but creates a potential
    * deadlock risk if not carefully managed:</p>
    *
    * <ul>
    * <li>Normal lock order: biglock2 → this (IoConverter instance)</li>
    * <li>This method temporarily inverts that order by releasing biglock2
    *     while holding 'this', then signaling caller to reacquire biglock2</li>
    * <li>The return value of 2 tells expand() to reacquire biglock2 and retry</li>
    * </ul>
    *
    * <p>This pattern works because:</p>
    * <ol>
    * <li>We always release biglock2 before waiting on 'this'</li>
    * <li>The I/O thread (rthread) only acquires 'this' monitor, never biglock2</li>
    * <li>The caller (expand) reacquires biglock2 after expandLock returns 2</li>
    * </ol>
    *
    * <p>TODO: Consider refactoring to use java.util.concurrent primitives
    * (CountDownLatch, Condition, etc.) for clearer lock management.</p>
    *
    * @param desired the minimum number of elements needed
    * @return 0 if data already available, 1 if loading finished, 2 if caller
    *         must reacquire biglock2 and retry
    * @throws IOException if an I/O error occurs during loading
    */
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
                  aNotify.notifyDone(mainArray);
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
      //trace("dorun " + tstate + this);
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

   private boolean handleDiff(OType fileObj, OType backObj, int index) {
      //trace("handleDiff fileObj " +fileObj + " backObj "  + backObj + " index " + index);
      FileDescriptor.LocalFile tfile;
      try {
         synchronized (this) {
            if (null == backupstatus)
               return  false;

            tfile = FileDescriptor.LocalFile.createTempFile("javi", ".tmp");
            tfile.deleteOnExit();
            FileProperties<OType> nProp = new FileProperties<>(prop, tfile);
            nProp.writeAll(new StringIter(mainArray.iterator()));
         }

         return (UI.reportDiff(prop.fdes.shortName, index, fileObj,
            backObj, backupstatus, tfile.shortName));

         //trace("setting backupstatus to null mainArray == ioarray");
         //trace("ioarray " + ioarray + " mainArray " + mainArray);
      } catch (IOException e) {
         UI.popError(
            "difference in files detected , error trying to display", e);
         return false;
      }
   }

   public final void run() {
      try {
         //trace("start of run " + this);
         Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
         EventQueue.biglock2.lock();
         BackupStatus temp = aNotify.getBackupStatus();
         EventQueue.biglock2.unlock();
         synchronized (this) {
            backupstatus = temp;
            if (backupstatus != null) {
               //trace("changeing ioarray to ecache");
               ioarray = new EditCache<OType>();
               ioarray.add(prop.conv.fromString(""));
            }
         }

         preRun();
         dorun();
         //trace("after dorun mainarray size =" +mainArray.size() + " " + this);
         //trace("after dorun ioarray size=" + ioarray.size() + " " + this);
      } catch (Throwable e) {
         UI.popError("IoConverter caught ", e);
      } finally {
         if (EventQueue.biglock2.isHeldByCurrentThread()) {
            EventQueue.biglock2.unlock();
         }
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
            if (maxcomp == compIndex) {
               if (ioarray.size() > mainArray.size()) {
                  fileObj = ioarray.get(compIndex);
               } else if (ioarray.size() < mainArray.size()) {
                  backObj = mainArray.get(compIndex);
               } else {
                  fileObj = null;
                  backObj = null;
                  if (backupstatus.cleanQuit && backupstatus.isQuitAtEnd)
                     backupstatus = null;
                     //trace("fileObj " + fileObj + " backObj " + backObj + " backupstatus " + backupstatus);
               }
            }
         }
      }

      //trace("fileObj " + fileObj + " backObj " + backObj + " backupstatus " + backupstatus);

      boolean useFile = handleDiff(fileObj, backObj, compIndex);

      synchronized (this) {
         //trace("swap array");
         swapArray = useFile;
         if (useFile && backupstatus != null
               && backupstatus.error instanceof history.FileLockException) {
            prop.setReadOnly(true);
         }

         backupstatus = null;
         rthread = null;
         //trace("thread finished , notify all" + this);
         tstate = ThreadState.FINISHED;
         truncIo();
         notifyAll();
      }
      //trace("run exit reached " + this);
   }

}
