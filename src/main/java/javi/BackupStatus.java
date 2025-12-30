package javi;

public final class BackupStatus {
   public final boolean cleanQuit;
   public final boolean isQuitAtEnd;
   public final Throwable error;

   BackupStatus(boolean c, boolean i, Throwable e) {
      cleanQuit = c;
      isQuitAtEnd = i;
      error = e;
   }

   public String toString() {
      return "clean quit  = " + cleanQuit + "  isQuitAtEnd = "
             + isQuitAtEnd + " + error = " + error;
   }

   boolean clean() {
      return cleanQuit && isQuitAtEnd && null == error;
   }

}
