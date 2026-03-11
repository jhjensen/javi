package javi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ExitException} and its parent {@link InputException}.
 */
class ExitExceptionJUnitTest {

   @Test
   void defaultMessageContainsExiting() {
      ExitException ex = new ExitException();
      assertTrue(ex.getMessage().contains("exiting"));
   }

   @Test
   void isInputException() {
      ExitException ex = new ExitException();
      assertInstanceOf(InputException.class, ex);
   }

   @Test
   void isException() {
      ExitException ex = new ExitException();
      assertInstanceOf(Exception.class, ex);
   }

   @Test
   void causeConstructorPreservesCause() {
      RuntimeException cause = new RuntimeException("boom");
      ExitException ex = new ExitException(cause);
      assertSame(cause, ex.getCause());
   }

   @Test
   void causeConstructorHasMessage() {
      RuntimeException cause = new RuntimeException("boom");
      ExitException ex = new ExitException(cause);
      assertTrue(ex.getMessage().contains("exiting"));
   }

   @Test
   void defaultConstructorHasNullCause() {
      ExitException ex = new ExitException();
      assertNull(ex.getCause());
   }

   @Test
   void canBeCaughtAsInputException() {
      boolean caught = false;
      try {
         throw new ExitException();
      } catch (InputException e) {
         caught = true;
      }
      assertTrue(caught);
   }

   @Test
   void inputExceptionSingleArgMessage() {
      InputException ex = new InputException("test msg");
      assertEquals("test msg", ex.getMessage());
   }

   @Test
   void inputExceptionTwoArgPreservesCause() {
      Exception cause = new Exception("inner");
      InputException ex = new InputException("outer", cause);
      assertEquals("outer", ex.getMessage());
      assertSame(cause, ex.getCause());
   }
}
