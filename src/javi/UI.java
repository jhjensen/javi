package javi;

//cycle MapEvent InsertBuffer?
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;

import static history.Tools.trace;

public abstract class UI {
   public enum Buttons {
      CHECKOUT , MAKEWRITEABLE , DONOTHING , MAKEBACKUP ,
      USEFILE , USEBACKUP , USEDIFF , OK , WINDOWCLOSE , IOERROR , USESVN,
      WAITPROC, KILLPROC
   };

   private static UI instance;

   protected UI() {
      if (null != instance)
         throw new RuntimeException("attempt to create two Awt singletons");
      instance = this;
   }

   protected static final UI getInstance() {
      return instance;
   }

   public abstract void itoggleStatus();
   public abstract void isetStream(Reader inreader) throws IOException;
   public abstract Buttons ireportDiff(String filename, int linenum,
      Object filevers, Object backupvers, BackupStatus status,
      String backupName);

   public abstract void ishowCommand();
   public abstract void ihideCommand();
   public abstract void irepaint();
   public abstract void idispose();
   public abstract String igetFile();
   public abstract boolean iisVisible();
   public abstract void ishow();
   public abstract void ishowmenu(int x, int y);
   public abstract void itoFront();
   public abstract void itransferFocus();
   public abstract Buttons ichooseWriteable(String filename);
   public abstract boolean ipopstring(String str);
   public abstract void iflush(boolean totalFlush);
   public abstract void istatusaddline(String str);
   public abstract void istatusSetline(String str);
   public abstract void iclearStatus();
   public abstract void isetTitle(String str);

   public abstract void iRestoreState(java.io.ObjectInputStream is) throws
      ClassNotFoundException, IOException;

   public abstract void iSaveState(java.io.ObjectOutputStream os) throws
      IOException;

   public abstract Result ireportModVal(String caption, String units,
                                 String []buttonVals, long limit);

   public abstract void isizeChange();

   public abstract Buttons istopConverter(String commandname);

   static final void saveState(ObjectOutputStream os) throws IOException {
//      os.writeObject (new Boolean(instance instanceof AwtInterface));
      instance.iflush(true);
      os.writeObject(instance);
      instance.iSaveState(os);
   }

   static final void restoreState(java.io.ObjectInputStream is) throws
         IOException, ClassNotFoundException {
//      instance = (Boolean) is.readObject()
//         ? (UI)new AwtInterface()
//         :(UI) new StreamInterface();
      instance = (UI) is.readObject();
      instance.iRestoreState(is);
      instance.ishow();
      toFront();
   }

   static final void setStream(Reader inreader)  throws IOException {
      instance.isetStream(inreader);
   }

   @SuppressWarnings("fallthrough")
   static final boolean reportDiff(String filename, int linenum,
         Object filevers, Object backupvers,
         BackupStatus status, String backupname) throws IOException {
      //trace( " filename = " + filename + " linenum = " + linenum + " filevers = " + filevers
      //   + " backupvers = " + backupvers + " status = " + status );
      while (true)  {
         while (null == instance)
            try {
               Thread.sleep(200);
            } catch (InterruptedException e) {
               trace("ignoring interrupted exception");
            }
         Buttons diaflag = instance.ireportDiff(filename, linenum,
            filevers, backupvers, status, backupname);

         //trace("got button " + diaflag);

         switch (diaflag) {
            case WINDOWCLOSE:
               break;

            case OK:
               //trace("got ok backupvers = " + backupvers + " filevers " + filevers);
               if (null == backupvers && null == filevers)
                  return false;
               break;

            case USEBACKUP:
               return false;

            case USEFILE:
               return true;

            case IOERROR:
               throw new IOException("unexpected io error");

            default:
               trace("Thread " + Thread.currentThread() + " filename "
                   + filename + " bad diaflag = " + diaflag);
               try {
                  Thread.sleep(5000);
               } catch (InterruptedException e) {
                  trace("ignoring InterruptedException");
               }
               trace("Thread " + Thread.currentThread() + " filename "
                  + filename + " bad diaflag = " + diaflag);
         }
         //trace("reportDiff returning diaflag = " + diaflag);
      }
   }

   static final void showCommand() {
      instance.ishowCommand();
   }

   static final void hideCommand() {
      instance.ihideCommand();
   }

   static final void repaint() {
      instance.irepaint();
   }

   static final void dispose() {
      if (null != instance)
         instance.idispose();
      instance = null;
   }

   static final String getFile() {
      return instance.igetFile();
   }

   static final boolean isVisible() {
      return instance.iisVisible();
   }

   static final void show() {
      instance.ishow();
   }

   public static final void showmenu(int x, int y) {
      instance.ishowmenu(x, y);
   }

   static final void toFront() {
      instance.itoFront();
   }

   static final void hide() {
      instance.itransferFocus();
   }

   static final Buttons chooseWriteable(String filename) {
      return instance.ichooseWriteable(filename);
   }

   static final boolean stopConverter(String commandname) {
      return Buttons.KILLPROC == instance.istopConverter(commandname);
   }

   public static final void popError(String errs, Throwable ex) {
      trace("poperror exception trace " + (null == errs ? "" : errs) + ex);

      StackTraceElement[] st = null == ex
                               ? Thread.currentThread().getStackTrace()
                               : ex.getStackTrace();

      StringWriter sw = new StringWriter();
      PrintWriter wr = new PrintWriter(sw);

      wr.println(errs);
      if (null != ex) {
         ex.printStackTrace();
         wr.println(ex);
      } else {
         Thread.dumpStack();
      }
      wr.println();
      for (StackTraceElement ste : st) {
         //trace("   " + ste.toString());
         wr.println(ste);
      }
      wr.close();
      if (null != instance)
         if (instance.ipopstring(sw.toString()))
            throw new RuntimeException("rethrow ", ex);

      return;
   }

   static final void setTitle(String str) {
      instance.isetTitle(str);
   }

   static final void flush() {
      instance.iflush(false);
   }

   public static final void reportError(String s) {
      instance.istatusaddline(s);
   }

   public static final void reportMessage(String s) {
      if (null != instance)
         instance.istatusaddline(s);
      else {
         Thread.dumpStack();
         trace("unhandled Messege:" + s);
      }
   }

   static final void setline(String s) {
      instance.istatusSetline(s);
   }

   static final void clearStatus() {
      instance.iclearStatus();
   }

   public static final class Result {
      public final int newValue;
      public final String choice;

      public Result(int newValuei, String choicei) {
         newValue = newValuei;
         choice = choicei;
      }
   }

   static final Result reportModVal(String caption, String units,
                              String []buttonVals, long limit) {

      return instance.ireportModVal(caption, units, buttonVals, limit);
   }

   static final void sizeChange() {
      instance.isizeChange();
   }
}
