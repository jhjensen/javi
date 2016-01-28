package history;

public class FileLockException extends java.io.IOException {

   public FileLockException(String st) {
      super(st);
   }

   public FileLockException(String st, Throwable e) {
      super(st, e);
   }
}
