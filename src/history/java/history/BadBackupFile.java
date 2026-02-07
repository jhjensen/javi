package history;

/**
 * Exception thrown when a backup/dmp2 file is corrupted or inconsistent.
 *
 * <p>This exception indicates problems with the persistent undo history file,
 * such as inconsistent file sizes or corrupted data. It extends IOException
 * because backup file problems are fundamentally I/O errors.</p>
 *
 * @see PersistantStack
 */
public class BadBackupFile extends java.io.IOException {

   public BadBackupFile(String st) {
      super(st);
   }

   public BadBackupFile(String st, Throwable e) {
      super(st, e);
   }
}
