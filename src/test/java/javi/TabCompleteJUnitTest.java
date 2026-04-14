package javi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for tab-completion logic.
 */
class TabCompleteJUnitTest {

   @Test
   void nonColonLineReturnsNull() {
      assertNull(TabComplete.complete("/search", 7, ""));
   }

   @Test
   void commandWithoutSpaceReturnsNull() {
      assertNull(TabComplete.complete(":git_status", 11, ""));
   }

   @Test
   void nonBranchCommandReturnsNull() {
      assertNull(TabComplete.complete(":git_log", 8, " foo"));
   }

   @Test
   void shortInputReturnsNull() {
      assertNull(TabComplete.complete(":", 1, ""));
   }
}
