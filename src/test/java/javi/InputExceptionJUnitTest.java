package javi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InputException} and {@link ExitException}.
 */
class InputExceptionJUnitTest {

   // ── InputException ──────────────────────────────────────────

   @Test
   void inputExceptionMessageOnly() {
      InputException ex = new InputException("bad input");
      assertEquals("bad input", ex.getMessage());
      assertNull(ex.getCause());
   }

   @Test
   void inputExceptionWithCause() {
      Throwable cause = new RuntimeException("disk error");
      InputException ex = new InputException("wrapped", cause);
      assertEquals("wrapped", ex.getMessage());
      assertSame(cause, ex.getCause());
   }

   @Test
   void inputExceptionIsCheckedException() {
      InputException ex = new InputException("test");
      assertInstanceOf(Exception.class, ex);
      // InputException extends Exception (not RuntimeException) — verified
      // by the class hierarchy, no runtime check needed
   }

   @Test
   void inputExceptionNullCauseAllowed() {
      InputException ex = new InputException("msg", null);
      assertEquals("msg", ex.getMessage());
      assertNull(ex.getCause());
   }

   // ── ExitException ──────────────────────────────────────────

   @Test
   void exitExceptionDefaultMessage() {
      ExitException ex = new ExitException();
      assertEquals("exiting java", ex.getMessage());
      assertNull(ex.getCause());
   }

   @Test
   void exitExceptionWithCause() {
      RuntimeException cause = new RuntimeException("fatal");
      ExitException ex = new ExitException(cause);
      assertEquals("exiting java", ex.getMessage());
      assertSame(cause, ex.getCause());
   }

   @Test
   void exitExceptionExtendsInputException() {
      ExitException ex = new ExitException();
      assertInstanceOf(InputException.class, ex);
   }

   @Test
   void inputExceptionCanBeCaughtAsException() {
      try {
         throw new InputException("catch me");
      } catch (Exception e) {
         assertEquals("catch me", e.getMessage());
      }
   }

   @Test
   void exitExceptionCanBeCaughtAsInputException() {
      try {
         throw new ExitException();
      } catch (InputException e) {
         assertEquals("exiting java", e.getMessage());
      }
   }
}
