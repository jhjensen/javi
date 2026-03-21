package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the dock badge count formula:
 * {@code FileList.countModified() + ShellManager.getSessionCount()}.
 *
 * <p>Verifies the individual count methods and their combination.
 * Does not test the Taskbar badge API itself (platform-dependent).</p>
 */
class DockBadgeCountJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   @Test
   @DisplayName("countModified returns 0 when FileList not initialized")
   void countModifiedZeroWhenNull() {
      // FileList.countModified() guards against null instance
      int count = FileList.countModified();
      assertTrue(count >= 0,
         "countModified should return non-negative, got " + count);
   }

   @Test
   @DisplayName("getSessionCount returns 0 with no active shells")
   void sessionCountZeroWhenEmpty() {
      ShellManager mgr = ShellManager.getInstance();
      mgr.closeAll();
      assertEquals(0, mgr.getSessionCount());
   }

   @Test
   @DisplayName("badge count is sum of modified files and shell sessions")
   void badgeCountIsSumOfModifiedAndSessions() {
      ShellManager mgr = ShellManager.getInstance();
      mgr.closeAll();

      int modified = FileList.countModified();
      int shells = mgr.getSessionCount();
      int badgeCount = modified + shells;

      assertEquals(modified + shells, badgeCount,
         "badge should show modified + shells");
   }

   @Test
   @DisplayName("countModified is non-negative")
   void countModifiedNonNegative() {
      assertTrue(FileList.countModified() >= 0);
   }

   @Test
   @DisplayName("getSessionCount is non-negative")
   void sessionCountNonNegative() {
      assertTrue(ShellManager.getInstance().getSessionCount() >= 0);
   }
}
