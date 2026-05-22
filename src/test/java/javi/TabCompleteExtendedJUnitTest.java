package javi;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Extended tests for {@link TabComplete} — commonPrefix logic,
 * branch-command matching, and edge cases.
 */
class TabCompleteExtendedJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initAllCommands();
   }

   // ── commonPrefix via reflection ──────────────────────────────

   private static String callCommonPrefix(String a, String b) throws Exception {
      Method m = TabComplete.class.getDeclaredMethod(
         "commonPrefix", String.class, String.class);
      m.setAccessible(true);
      return (String) m.invoke(null, a, b);
   }

   @Test
   void commonPrefixIdentical() throws Exception {
      assertEquals("hello", callCommonPrefix("hello", "hello"));
   }

   @Test
   void commonPrefixPartial() throws Exception {
      assertEquals("hel", callCommonPrefix("hello", "help"));
   }

   @Test
   void commonPrefixNoMatch() throws Exception {
      assertEquals("", callCommonPrefix("abc", "xyz"));
   }

   @Test
   void commonPrefixOneEmpty() throws Exception {
      assertEquals("", callCommonPrefix("", "abc"));
   }

   @Test
   void commonPrefixBothEmpty() throws Exception {
      assertEquals("", callCommonPrefix("", ""));
   }

   @Test
   void commonPrefixSubstring() throws Exception {
      assertEquals("feature/T", callCommonPrefix("feature/T1", "feature/T3"));
   }

   @Test
   void commonPrefixSingleChar() throws Exception {
      assertEquals("f", callCommonPrefix("foo", "far"));
   }

   // ── complete() edge cases ────────────────────────────────────

   @Test
   void emptyLineReturnsNull() {
      assertNull(TabComplete.complete("", 0, ""));
   }

   @Test
   void singleColonReturnsNull() {
      assertNull(TabComplete.complete(":", 1, ""));
   }

   @Test
   void unknownCommandReturnsNull() {
      assertNull(TabComplete.complete(":set", 4, " opt"));
   }

   @Test
   void gitStatusNotBranchCommand() {
      assertNull(TabComplete.complete(":git_status", 11, " "));
   }

   @Test
   void gitLogNotBranchCommand() {
      assertNull(TabComplete.complete(":git_log ", 9, ""));
   }

   @Test
   void branchSwitchWithoutSpaceReturnsNull() {
      // No space after command name — no partial branch name
      assertNull(TabComplete.complete(":git_branch_switch", 18, ""));
   }

   @Test
   void branchDeleteRecognized() {
      // With space but no partial — should try completion
      // (may return null if no branches match empty string,
      //  but shouldn't throw)
      TabComplete.complete(":git_branch_delete", 18, " ");
      // No exception = pass
   }

   @Test
   void gitMergeRecognized() {
      TabComplete.complete(":git_merge", 10, " ");
      // No exception = pass
   }

   @Test
   void pendingTextAppendedToLineText() {
      // lineText + pending must form ":cmd partial"
      // If lineText is short and pending provides the rest
      assertNull(TabComplete.complete(":git", 4, "_log foo"),
         "git_log is not a branch command");
   }

   @Test
   void insertXBeyondLineLength() {
      // insertX beyond lineText — substring clamped
      assertNull(TabComplete.complete(":x", 999, ""));
   }
}
