package javi;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static history.Tools.trace;

public abstract class UI {
   public enum Buttons {
      CHECKOUT , MAKEWRITEABLE , DONOTHING , MAKEBACKUP ,
      USEFILE , USEBACKUP , USEDIFF , OK , WINDOWCLOSE , IOERROR , USESVN
   };

   public static Buttons diaflag;
   private static UI instance = null;
   public abstract void itoggleStatus();
   public abstract void isetStream(Reader inreader);
   public abstract void ireportDiff(String filename, int linenum,
      Object filevers, Object backupvers, UndoHistory.BackupStatus status,
      String backupName);

   public abstract FvContext istartComLine();
   public abstract String iendComLine();
   public abstract void irepaint();
   public abstract void idispose();
   public abstract String igetFile();
   public abstract boolean iisVisible();
   public abstract void iremove(View vi);
   public abstract void ishow();
   public abstract void ishowmenu(int x, int y);
   public abstract void itoFront();
   public abstract void itransferFocus();
   public abstract void ichooseWriteable(String filename);
   public abstract boolean ipopstring(String str);
   public abstract void iflush(boolean totalFlush);
   public abstract void istatusaddline(String str);
   public abstract void istatusSetline(String str);
   public abstract void iclearStatus();
   public abstract boolean iisGotoOk(FvContext fvc);
   public abstract FvContext iconnectfv(TextEdit file,
      View vi) throws InputException;
   public abstract void isetView(FvContext fvc);

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
         instance.ireportDiff(filename, linenum, filevers, backupvers,
             status, backupname);
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

   static FvContext startComLine() {
      return instance.istartComLine();
   }
   static String endComLine() {
      return instance.iendComLine();
   }
   static  boolean isGotoOk(FvContext fvc) {
      return instance.iisGotoOk(fvc);
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
   static void remove(View vi) {
      instance.iremove(vi);
   }

   static void show() {
      instance.ishow();
   }
//   static void pack() {
//      instance.ipack();
//   }
   public static void showmenu(int x, int y) {
      instance.ishowmenu(x, y);
   }
   static void toFront() {
      instance.itoFront();
   }

   static void hide() {
      instance.itransferFocus();
   }

   private static Matcher findfile =
      Pattern.compile("(.*[\\\\/])([^\\/]*)$").matcher("");

   static void makeWriteable(EditContainer edv, String filename) throws
         IOException {
      instance.ichooseWriteable(filename);
      switch (diaflag) {

         case CHECKOUT:
            Command.command("vcscheckout", null, filename);
            break;
         case MAKEWRITEABLE:
            edv.setReadOnly(false);
            break;
         case DONOTHING:
         case WINDOWCLOSE:
            break;
         case MAKEBACKUP:
            edv.backup(".orig");
            break;
         case USESVN:
            findfile.reset(filename);
            String svnstr =  (findfile.find()
               ? findfile.group(1) + ".svn/text-base/" + findfile.group(2)
               : "./.svn/text-base/" + filename
               )  + ".svn-base";

            //trace("svnstr "  + svnstr);
            BufferedReader fr = new BufferedReader(new FileReader(svnstr));
            try {
               int lineno = 0;
               int linemax = edv.finish();
               String line;
               while ((line = fr.readLine()) != null) {
                  if ((++lineno  >= linemax))
                     break;
                  if (!line.equals(edv.at(lineno))) {
                     reportMessage(
                        "svn base file not equal to current file at "
                        + (lineno - 1) + ":" + edv.at(lineno - 1) + ":"
                        + line + ":");
                     return;
                  }
               }
               if (line == null && lineno + 1 == linemax)
                  edv.setReadOnly(false);
               else
                  reportMessage("svn base file not equal to current file");
            } finally {
               fr.close();
            }
            break;
         default:

            throw new RuntimeException("bad diaflag = " + diaflag);
      }
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

   static FvContext connectfv(TextEdit file, View vi) throws InputException {
      return instance.iconnectfv(file, vi);
   }
   static void setView(FvContext fvc) {
      instance.isetView(fvc);
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
