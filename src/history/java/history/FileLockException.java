package history;

/**
 * Exception thrown when a file lock cannot be acquired.
 *
 * <p>This exception indicates that a .dmp2 backup file is locked
 * by another process, typically another Javi instance editing the
 * same file.</p>
 *
 * <h3>Common Causes</h3>
 * <ul>
 *   <li>Another Javi instance has the same file open</li>
 *   <li>The file is read-only or permissions prevent locking</li>
 *   <li>A previous Javi instance crashed and the lock wasn't released</li>
 * </ul>
 *
 * @see PersistantStack#makeReal()
 */
public class FileLockException extends java.io.IOException {
   private static final long serialVersionUID = 1L;

   /**
    * Constructs a FileLockException with the specified message.
    *
    * @param st the detail message
    */
   public FileLockException(String st) {
      super(st);
   }

   /**
    * Constructs a FileLockException with the specified message and cause.
    *
    * @param st the detail message
    * @param e the cause of this exception
    */
   public FileLockException(String st, Throwable e) {
      super(st, e);
   }
}
