package history;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for history exception classes:
 * {@link BadBackupFile} and {@link FileLockException}.
 */
class ExceptionClassesJUnitTest {

   // ── BadBackupFile ───────────────────────────────────────────

   @Test
   void badBackupFileMessageOnly() {
      BadBackupFile ex = new BadBackupFile("corrupt header");
      assertEquals("corrupt header", ex.getMessage());
      assertNull(ex.getCause());
   }

   @Test
   void badBackupFileWithCause() {
      Exception cause = new RuntimeException("disk error");
      BadBackupFile ex = new BadBackupFile("read failed", cause);
      assertEquals("read failed", ex.getMessage());
      assertSame(cause, ex.getCause());
   }

   @Test
   void badBackupFileIsIOException() {
      BadBackupFile ex = new BadBackupFile("test");
      assertInstanceOf(java.io.IOException.class, ex);
   }

   // ── FileLockException ───────────────────────────────────────

   @Test
   void fileLockExceptionMessageOnly() {
      FileLockException ex = new FileLockException("locked by pid 123");
      assertEquals("locked by pid 123", ex.getMessage());
      assertNull(ex.getCause());
   }

   @Test
   void fileLockExceptionWithCause() {
      Exception cause = new RuntimeException("lock failed");
      FileLockException ex = new FileLockException("cannot lock", cause);
      assertEquals("cannot lock", ex.getMessage());
      assertSame(cause, ex.getCause());
   }

   @Test
   void fileLockExceptionIsIOException() {
      FileLockException ex = new FileLockException("test");
      assertInstanceOf(java.io.IOException.class, ex);
   }
}
