package javi;

//cycle MapEvent InsertBuffer?
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import history.BadBackupFile;

import static history.Tools.trace;

/**
 * Abstract interface for user interaction (GUI or stream-based).
 *
 * <p>UI defines the interface between editor logic and presentation layer.
 * Two implementations exist:
 * <ul>
 *   <li>{@link javi.awt.AwtInterface} - Graphical AWT interface</li>
 *   <li>{@link StreamInterface} - Text stream interface for testing/scripting</li>
 * </ul>
 *
 * <h2>Singleton Pattern</h2>
 * <p>UI uses a singleton pattern with protected constructor. Only one UI
 * instance can exist. Access via {@link #getInstance()}.</p>
 *
 * <h2>Key Responsibilities</h2>
 * <ul>
 *   <li><b>Dialogs</b>: File conflict resolution, error display, confirmations</li>
 *   <li><b>Status bar</b>: Message display, command echo</li>
 *   <li><b>Window management</b>: Show, hide, focus, resize</li>
 *   <li><b>State persistence</b>: Save/restore UI state</li>
 * </ul>
 *
 * <h2>Dialog Methods</h2>
 * <ul>
 *   <li>{@link #ireportDiff} - File/backup version conflict dialog</li>
 *   <li>{@link #ichooseWriteable} - Read-only file handling</li>
 *   <li>{@link #iconfirmReload} - Confirm file reload from disk</li>
 *   <li>{@link #ireportBadBackup} - Corrupt backup file handling</li>
 * </ul>
 *
 * <h2>Known Issues</h2>
 * <p><b>WARNING</b>: Infinite loop potential in {@code reportDiff} if
 * unhandled button value returned. See BUGS.md B8.</p>
 *
 * @see javi.awt.AwtInterface
 * @see StreamInterface
 */
public abstract class UI {
   public enum Buttons {
      CHECKOUT, MAKEWRITEABLE, DONOTHING, MAKEBACKUP,
      USEFILE, USEBACKUP, USEDIFF, OK, WINDOWCLOSE, IOERROR, USESVN,
      WAITPROC, KILLPROC
   };

   /**
    * Actions for file change detection popup.
    * 
    * <p>When a file is modified externally while open in Javi, the user
    * can choose one of these actions:</p>
    * <ul>
    *   <li>{@link #RELOAD} - Reload the file from disk, discarding buffer changes</li>
    *   <li>{@link #IGNORE} - Ignore this change, keep buffer contents</li>
    *   <li>{@link #IGNORE_ALWAYS} - Permanently ignore external changes for this file</li>
    *   <li>{@link #SHOW_DIFF} - Show diff between buffer and disk version</li>
    *   <li>{@link #STOP_EDITING} - Close this buffer</li>
    * </ul>
    */
   public enum ReloadAction {
      /** Reload the file from disk, discarding buffer changes. */
      RELOAD,
      /** Ignore this change notification, keep buffer contents. */
      IGNORE,
      /** Permanently ignore external changes for this file during this session. */
      IGNORE_ALWAYS,
      /** Show diff between current buffer and disk version. */
      SHOW_DIFF,
      /** Close this buffer (prompts to save if modified). */
      STOP_EDITING
   }

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
                                 String[]buttonVals, long limit);

   public abstract void isizeChange();

   public abstract Buttons istopConverter(String commandname);
   public abstract boolean ireportBadBackup(String filename, BadBackupFile e);

   /**
    * Ask user how to handle a file that was modified externally.
    * 
    * @param filename the name of the modified file
    * @param isModified true if the buffer also has unsaved changes
    * @return the user's chosen action
    */
   public abstract ReloadAction iconfirmReload(String filename, boolean isModified);

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

   // B8: Maximum iterations to prevent infinite loop on unhandled button values
   private static final int MAX_REPORT_DIFF_ITERATIONS = 100;

   @SuppressWarnings("fallthrough")
   static final boolean reportDiff(String filename, int linenum,
         Object filevers, Object backupvers,
         BackupStatus status, String backupname) throws IOException {
      //trace(" filename = " + filename + " linenum = " + linenum + " filevers = " + filevers + " backupvers = " + backupvers + " status = " + status );
      int iterations = 0;  // B8: Track iterations to detect infinite loop
      while (iterations++ < MAX_REPORT_DIFF_ITERATIONS) {
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
               //trace("got ok backupvers", backupvers, "filevers", filevers, "status", status);
               if (null == backupvers && null == filevers)
                  return false;
               if  (status.error instanceof history.FileLockException)
                  return true;
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
      // B8: Reached max iterations, log error and default to using file
      trace("reportDiff: max iterations (" + MAX_REPORT_DIFF_ITERATIONS
         + ") reached for " + filename + ", defaulting to use file");
      return true;
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

   public static final boolean reportBadBackup(
         String filename, BadBackupFile e) {
      if (null != instance)
         return instance.ireportBadBackup(filename, e);
      else {
         Thread.dumpStack();
         trace("unhandled Messege:" + e);
         return false;
      }
   }

   /**
    * Ask user how to handle a file that was modified externally.
    * 
    * @param filename the name of the modified file
    * @param isModified true if the buffer also has unsaved changes
    * @return the user's chosen action, or IGNORE if no UI instance
    */
   public static final ReloadAction confirmReload(String filename, boolean isModified) {
      if (null != instance)
         return instance.iconfirmReload(filename, isModified);
      else {
         trace("confirmReload called with no UI instance");
         return ReloadAction.IGNORE;
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
                              String[] buttonVals, long limit) {

      return instance.ireportModVal(caption, units, buttonVals, limit);
   }

   static final void sizeChange() {
      instance.isizeChange();
   }
}
