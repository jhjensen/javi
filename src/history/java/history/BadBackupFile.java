package history;

//public class BadBackupFile extends java.io.IOException {
public class BadBackupFile extends RuntimeException {

   public BadBackupFile(String st) {
      super(st);
   }

   public BadBackupFile(String st, Throwable e) {
      super(st, e);
   }
}
