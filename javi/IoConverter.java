package javi;
 
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Collection;

/** this is an abstract class which defines the necessary methods
    for an editvec to to IO
*/
abstract class ClassConverter<OType>  implements Serializable {

   public abstract OType fromString(String S);

   OType newExternal(history.ByteInput dis) {
      return fromString(dis.readUTF());
   }

   void saveExternal(Object ob,DataOutputStream dos) throws IOException {
      dos.writeUTF(ob.toString());
   }
   static void trace(String str) {
      Tools.trace(str,1);
   }
}

class FileProperties<OType> implements Serializable {
    private static String staticLine = System.getProperty("line.separator");
    final FileDescriptor fdes;
    final ClassConverter<OType> conv;
    String lsep = staticLine;

    FileProperties(FileDescriptor fd,ClassConverter<OType> convi) {
       fdes = fd;
       conv = convi;
    }

    public String toString() {
       return fdes.toString();
    }
  
    void setSeperator(String sep) {
       if (lsep !=null)
          throw new RuntimeException("attempt to reset line seperator");
       lsep = sep;
   }
}


public class IoConverter<OType> implements Runnable,Serializable{

final FileProperties<OType> prop;
transient private EditCache<OType> ioarray;
transient private int compIndex =0;
transient private EditCache<OType> mainArray=null;
transient private Thread  rthread;
transient private UndoHistory.BackupStatus backupstatus;
transient private BuildCB aNotify;
transient private ThreadState tstate;

private enum ThreadState {INIT,INITSTART,STARTED,FINISHED};


IoConverter(FileProperties<OType> fpi,boolean quickThread) {
   tstate = quickThread
      ? ThreadState.INITSTART
      : ThreadState.INIT;
   prop = fpi;
}

private void readObject(java.io.ObjectInputStream is) 
       throws ClassNotFoundException,java.io.IOException {
   is.defaultReadObject();
   tstate = ThreadState.INIT;
}

protected void restart(boolean quickThread) {
   tstate = quickThread
      ? ThreadState.INITSTART
      : ThreadState.INIT;
   // this is intended for serialization to restart the read
}
OType getnext() {
   return null;
}

public synchronized void dispose() throws IOException {
   //trace("rthread = " + rthread);
   try {
      if (rthread !=null) {
         rthread.interrupt();
         //trace ("waiting for thread to die " +this);
         wait(1000);
         if (rthread != null)
            UI.popError("thread didn't die " +this ,null);
   }
   } catch (InterruptedException e) {/*Ignore*/};
   truncIo();
   aNotify=null;
   ioarray=null;
   mainArray=null;
   
     
}

KeyHandler getKeyHandler() {
   //trace("ioc getKeyHandler");
   return null;
}

public final String toString() {
    return prop.fdes.shortName;
}

synchronized void init1(EditCache<OType> evi,BuildCB arr) {
  if (tstate == ThreadState.INITSTART)
     startThread();
   ioarray=evi;
   mainArray=ioarray;
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

EditCache<OType> convertStream()
      throws ReadOnlyException,IOException {
  //trace("index = " + index + " " + fdes + "class " + this.getClass());

  EditCache<OType> ret = new EditCache<OType>();
  //trace("retval " + retval);
   for (OType ob;(ob= getnext())!=null;)
      ret.add(ob);
 
   return ret;
}

void reload() {
  //trace("reload state = "  + tstate);
  preRun();
  try {
     dorun();
  } catch (InterruptedException ad) {
      UI.popError("IoConverter caught ",ad);
  }
  truncIo() ;
  
}
// should not be called after returning 0.
//This should only be called from editvec.

final synchronized protected void startThread() {
   //Thread.dumpStack();
   //trace("starting thread " + this);
   tstate = ThreadState.STARTED;
   rthread = new Thread(this,"Thread " + prop.fdes.shortName);
   //trace("creating rthread " + rthread);
   rthread.start();
}

abstract static class BuildCB {
   abstract void notify(EditCache ed);
   abstract UndoHistory.BackupStatus getBackupStatus();
}

void handleDiff( OType fileObj,OType backObj) throws IOException {
      //trace("handleDiff fileObj " +fileObj + " backObj "  + backObj);
      FileDescriptor.LocalFile tfile = FileDescriptor.LocalFile.createTempFile("javi",".tmp");
      tfile.deleteOnExit();
      tfile.writeAll(new StringIter(mainArray.iterator()),prop.lsep);

      String bname = tfile.shortName;
      boolean useorig = UI.reportDiff(prop.fdes.shortName,compIndex,fileObj, 
         backObj,backupstatus,bname);
      if (useorig) {
         mainArray= ioarray;
         aNotify.notify(mainArray);
      } else {
         ioarray=mainArray;
         truncIo();
      }
      //trace("setting backupstatus to null mainArray == ioarray");
      //trace("ioarray " + ioarray + " mainArray " + mainArray);
      backupstatus = null;
}

final synchronized boolean expand(int desired) throws IOException {

   //trace("enter expand "  + this + " desired = " + desired);

   while (true)  {

      //trace(" ioarray.size " + ioarray.size() );
      //trace(" mainArrya " + mainArray.size());
      //trace(" backupstatus " + backupstatus);
      //trace(" tstate " + tstate);
      //trace(" ioarray " + ioarray + " mainArray" + mainArray);
      if (null != backupstatus)  {
         assert (ioarray!= mainArray);
         int maxcomp = ioarray.size()<mainArray.size()
            ? ioarray.size()
            :  mainArray.size();
         for (;compIndex<maxcomp;compIndex++) {
            OType backObj = mainArray.get(compIndex);
            OType fileObj = ioarray.get(compIndex);
            if (!fileObj.equals(backObj)) {
                handleDiff(fileObj,backObj);
                break;
             }
         }

         if (ioarray.size() > mainArray.size())
            handleDiff(ioarray.get(mainArray.size()),null);
      }

      //ecache.addSome(ioarray,compIndex);
      //compIndex = ioarray.size();

      switch (tstate)  {

          case INITSTART:
          case INIT:
             if ((desired == 0 ||desired >mainArray.size())) 
                startThread();
             else 
               return false;
             continue;
          case STARTED:
             if (mainArray.size()>=desired) 
                return false;
             try {
                //trace("about to wait 2000");
                wait(2000); //??????? jhj fix
                //trace("done to wait 2000");
             } catch (InterruptedException ex) {UI.popError("ignored Interrupted Exception",null);/* Ignore Interrupts */}
             continue;
          case FINISHED:
             if (backupstatus != null ) {
                if (ioarray.size() < mainArray.size()) 
                   handleDiff(mainArray.get(ioarray.size()),null);
                else if (!backupstatus.clean())
                   handleDiff(null,null);
//                else
//???                   aNotify.forceWritten();
         }
         if (ioarray!=mainArray)
            ioarray.clear();
         return true;
      }
   }
   //UI.popError("shouldn't get here",null);
   //return ecache.size();
}

void dumpCollection(String name,Collection cont) {
         trace(name);
         for(Object obj:cont)
            trace("   "  + obj);
}

// this is a runtime exception so that report in javacompiler can throw it.
static class DoneAdding extends RuntimeException {   
   private static final long serialVersionUID=1;
}

final synchronized protected void addElement(OType ob) {
   //trace("add element ob " +ob );
   ioarray.add(ob);
}

void dorun() throws InterruptedException{
   OType ob;
   while ((ob = getnext())!=null) //??? get rid of getnext
      addElement(ob);
}

protected void preRun() {
}

protected void truncIo() {
   if (rthread!=null)
      rthread.interrupt();
}

public final void run() {
   //trace("start run vcount =" +vcount + " " + evec);
   try {
      Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
      synchronized(this) {
         backupstatus = aNotify.getBackupStatus();
         //trace("backupstatus " + backupstatus);
         if (backupstatus != null) {
            //trace("changeing ioarray to ecache");
            ioarray=new EditCache<OType>();
            ioarray.add(prop.conv.fromString(""));
         }
         
      }

      preRun();
      //trace("before dorun ioarray size =" +ioarray.size() + " " + this);
      dorun();
      //trace("after dorun ioarray size=" + ioarray.size() + " " + this);
   } catch (Throwable e) {
      UI.popError("IoConverter caught ",e);
   }
   synchronized(this) {
      rthread=null;
      //trace("thread finished , notify all" + this);
      tstate = ThreadState.FINISHED;
      truncIo();
      notifyAll();
   }
   //trace("run exit reached " + this);
}

final static void trace(String str) {
   Tools.trace(str,1);
}
}
