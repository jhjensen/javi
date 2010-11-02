package javi;

//cycle MapEvent View
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import static history.Tools.trace;

public abstract class UI {
   public enum Buttons {
      CHECKOUT , MAKEWRITEABLE , DONOTHING , MAKEBACKUP ,
      USEFILE , USEBACKUP , USEDIFF , OK , WINDOWCLOSE , IOERROR , USESVN
   };

   private static UI instance = null;
   protected static void setInstance(UI inst) {
      if (instance != null)
         throw new RuntimeException("attempt to create two Awt singletons");
      instance = inst;
   }

   protected static UI getInstance() {
      return instance;
   }

   public abstract void itoggleStatus();
   public abstract void isetStream(Reader inreader);
   public abstract Buttons ireportDiff(String filename, int linenum,
      Object filevers, Object backupvers, UndoHistory.BackupStatus status,
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

   public abstract void isetViewSize(View vi, int width, int height);

   public abstract InsertBuffer igetInsertBuffer(MapEvent me);

   public static InsertBuffer getInsertBuffer(MapEvent me) {
      return instance.igetInsertBuffer(me);
   }

   static void saveState(java.io.ObjectOutputStream os) throws IOException {
//      os.writeObject (new Boolean(instance instanceof AwtInterface));
      instance.iflush(true);
      os.writeObject(instance);
      instance.iSaveState(os);
   }

   static void restoreState(java.io.ObjectInputStream is) throws
         IOException, ClassNotFoundException {
//      instance = (Boolean) is.readObject()
//         ? (UI)new AwtInterface()
//         :(UI) new StreamInterface();
      instance = (UI) is.readObject();
      instance.iRestoreState(is);
      instance.ishow();
      instance.toFront();
   }

   public static void init(boolean isAwt) throws ExitException {
      //trace("");
      instance = isAwt
         ? (UI) new javi.awt.AwtInterface()
         : (UI) new StreamInterface();
   }

   static void setStream(Reader inreader) {
      instance.isetStream(inreader);
   }

   @SuppressWarnings("fallthrough")
   static boolean reportDiff(String filename, int linenum, Object filevers,
         Object backupvers, UndoHistory.BackupStatus status,
         String backupname) throws IOException {
      //trace(
      //   " filename = " + filename
      //   + " linenum = " + linenum
      //   + " filevers = " + filevers
      //   + " backupvers = " + backupvers
      //   + " status = " + status
      //);
      while (true)  {
         while (instance == null)
            try {
               Thread.sleep(200);
            } catch (InterruptedException e) {
               trace("ignoring interrupted exception");
            }
         Buttons diaflag = instance.ireportDiff(filename, linenum,
            filevers, backupvers, status, backupname);
         switch (diaflag) {
            case WINDOWCLOSE:
               break;

            case OK:
               //trace("got ok backupvers = " + backupvers + " filevers " + filevers);
               if (backupvers == null && filevers == null)
                  return false;
               break;

            case USEBACKUP:
               return false;

            case USEFILE:
               return true;

            case IOERROR:
               trace("got error in reportDiff");
               throw new IOException();

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

   static void showCommand() {
      instance.ishowCommand();
   }

   static void hideCommand() {
      instance.ihideCommand();
   }

   static void repaint() {
      instance.irepaint();
   }

   static void dispose() {
      if (instance != null)
         instance.idispose();
      instance = null;
   }
   static String getFile() {
      return instance.igetFile();
   }

   static boolean isVisible() {
      return instance.iisVisible();
   }


   static void show() {
      instance.ishow();
   }

   public static void showmenu(int x, int y) {
      instance.ishowmenu(x, y);
   }
   static void toFront() {
      instance.itoFront();
   }

   static void hide() {
      instance.itransferFocus();
   }


   static Buttons chooseWriteable(String filename) {
      return instance.ichooseWriteable(filename);
   }

   public static void popError(String errs, Throwable ex) {
      trace("poperror exception trace " + (errs == null ? "" : errs) + ex);

      StackTraceElement[] st = ex == null
                               ? Thread.currentThread().getStackTrace()
                               : ex.getStackTrace();

      StringWriter sw = new StringWriter();
      PrintWriter wr = new PrintWriter(sw);

      wr.println(errs);
      if (ex != null) {
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

      if (instance != null)
         if (instance.ipopstring(sw.toString()))
            throw new RuntimeException("rethrow ", ex);

      return;
   }

   static void setTitle(String str) {
      instance.isetTitle(str);
   }

   static void flush() {
      instance.iflush(false);
   }

   public static void reportError(String s) {
      instance.istatusaddline(s);
   }

   static void reportMessage(String s) {
      if (instance != null)
         instance.istatusaddline(s);
      else {
         Thread.dumpStack();
         trace("unhandled Messege:" + s);
      }
   }

   static void setline(String s) {
      instance.istatusSetline(s);
   }

   static void clearStatus() {
      instance.iclearStatus();
   }

   public static class Result {
      public final int newValue;
      public final String choice;

      public Result(int newValuei, String choicei) {
         newValue = newValuei;
         choice = choicei;
      }
   }

   static Result reportModVal(String caption, String units,
                              String []buttonVals, long limit) {

      return instance.ireportModVal(caption, units, buttonVals, limit);
   }

   static void setViewSize(View vi, int width, int height) {
      instance.isetViewSize(vi, width, height);
   }
}
