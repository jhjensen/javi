package javi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BackupStatus} — backup result data class.
 */
class BackupStatusJUnitTest {

   @Test
   void cleanQuitAndQuitAtEndAndNoError() {
      BackupStatus bs = new BackupStatus(true, true, null);
      assertTrue(bs.clean());
   }

   @Test
   void notCleanWhenNotCleanQuit() {
      BackupStatus bs = new BackupStatus(false, true, null);
      assertFalse(bs.clean());
   }

   @Test
   void notCleanWhenNotQuitAtEnd() {
      BackupStatus bs = new BackupStatus(true, false, null);
      assertFalse(bs.clean());
   }

   @Test
   void notCleanWhenErrorPresent() {
      BackupStatus bs = new BackupStatus(true, true,
            new RuntimeException("test"));
      assertFalse(bs.clean());
   }

   @Test
   void notCleanAllFalseWithError() {
      BackupStatus bs = new BackupStatus(false, false,
            new IOException("io"));
      assertFalse(bs.clean());
   }

   @Test
   void fieldsAccessible() {
      Throwable t = new RuntimeException("err");
      BackupStatus bs = new BackupStatus(true, false, t);
      assertTrue(bs.cleanQuit);
      assertFalse(bs.isQuitAtEnd);
      assertSame(t, bs.error);
   }

   @Test
   void toStringContainsAllFields() {
      BackupStatus bs = new BackupStatus(true, false, null);
      String s = bs.toString();
      assertTrue(s.contains("true"), "should contain cleanQuit value");
      assertTrue(s.contains("false"), "should contain isQuitAtEnd value");
      assertTrue(s.contains("null"), "should contain error value");
   }

   @Test
   void toStringWithError() {
      RuntimeException ex = new RuntimeException("boom");
      BackupStatus bs = new BackupStatus(false, false, ex);
      String s = bs.toString();
      assertTrue(s.contains("boom"), "should contain error message");
   }

   // Private IOException for testing non-RuntimeException error path
   private static class IOException extends Exception {
      IOException(String msg) {
         super(msg);
      }
   }
}
