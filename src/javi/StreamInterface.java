package javi;

import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import static history.Tools.trace;

public final class StreamInterface extends UI {

   public void isetStream(Reader inreader) {
      inStr = inreader;
   }
   private Reader inStr = new InputStreamReader(System.in);

   public Buttons ireportDiff(String filename, int linenum,
                    Object filevers, Object backupvers,
                    BackupStatus status, String backupname) {

      StringBuilder sb = new StringBuilder("problem found in file ");
      sb.append(filename);
      sb.append('\n');
      if (filevers == null && backupvers == null) {
         sb.append("the written versions of the file are consistent\n");
      } else   if (filevers == null) {
         sb.append("backup version has extra lines at end\n");
         sb.append(backupvers.toString());
      } else if (backupvers == null) {
         sb.append("file version has extra lines at end\n");
         sb.append(filevers.toString());
      } else  {
         sb.append("versions differ at line " + linenum + " :\n");
         sb.append(filevers.toString());
         sb.append('\n');
         sb.append(backupvers.toString());
      }
      if (status.error != null) {
         sb.append("\ncorrupt backup file read in as far as possible. ");
         sb.append(status.error);
      } else {
         if (!status.cleanQuit)
            sb.append("\nThe file was not cleanly closed");
         if (!status.isQuitAtEnd)
            sb.append("\nThere is undo history. user ^r");
      }
      sb.append("\nf(file) b(backup) d(diff) O(ok)\n");
      try {
         while (true) {
            if (!inStr.ready())
               Tools.trace(sb.toString());
            int ch = inStr.read();
            //trace("read in " + (char)ch);
            switch (ch) {
               case 'f':
                  return Buttons.USEFILE;
               case 'b':
                  return Buttons.USEBACKUP;
               case 'd':
                  return Buttons.USEDIFF;
               case 'o':
                  return Buttons.OK;
               case -1:
                  return Buttons.IOERROR;
               default:
                  trace("stream got unexpected char = " + ch);
            }
         }
      } catch (IOException e) {
         trace("ireportDiff can not read from input Stream ");
         return Buttons.IOERROR;
      }

   }

   public void irepaint() { /* unimplemented */ }
   public void idispose() { /* unimplemented */ }
   public String igetFile() { return "filename"; }
   public boolean iisVisible() { return true; }
   public void iremove(View vi) { /* unimplemented */ }
   public void ishow() { /* unimplemented */ }
   public void ishowmenu(int x, int y) { /* unimplemented */ }
   public void itoFront() { /* unimplemented */ }
   public void itransferFocus() { /* unimplemented */ }
   public Buttons ichooseWriteable(java.lang.String str) {
      throw new RuntimeException("unimplemented");
   }
   public boolean ipopstring(java.lang.String str) { return false; }
   public void iflush(boolean total) { /* unimplemented */ }
   public void itoggleStatus() { /* unimplemented */ }
   public void isetTitle(String str) { }
   public View iaddview(boolean newview, FvContext fvc) { return null; }

   public void istatusaddline(String s) {
      trace(s);
   }
   public void istatusSetline(String s) { /*unimplemented*/ }
   public void iclearStatus() { /* unimplemented */ }
   public void ishowCommand() {
      throw new RuntimeException("unimplemented");
   }

   public void ihideCommand() { }
   public Object doroutine(int rnum, Object arg, int count, int rcount,
                           FvContext fvc, boolean dotmode) {

      throw new RuntimeException("doroutine called with " + rnum);
   }

   public void inextView(FvContext fvc) {
   }

   public Result ireportModVal(String caption, String units,
                        String []buttonVals, long limit) {

      return null;
   }
   public void isizeChange() {
   }

   public void iRestoreState(java.io.ObjectInputStream is) {
   }
   public void iSaveState(java.io.ObjectOutputStream os) {
   }
   public InsertBuffer igetInsertBuffer() {
      return null;
   }
}
