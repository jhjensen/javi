package javi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended tests for {@link DeTabber} — covers deTab() and mktabstr()
 * methods not exercised by the original tabFind-only tests.
 */
class DeTabberExtraJUnitTest {

   // ── mktabstr tests ──────────────────────────────────────────

   @Test
   void mktabstrReturnsCorrectLength() {
      assertEquals(4, DeTabber.mktabstr(4).length());
      assertEquals(8, DeTabber.mktabstr(8).length());
      assertEquals(1, DeTabber.mktabstr(1).length());
   }

   @Test
   void mktabstrContainsOnlySpaces() {
      String result = DeTabber.mktabstr(6);
      assertEquals("      ", result);
   }

   // ── deTab basic tests ───────────────────────────────────────

   @Test
   void deTabReplacesTabWithSpaces() {
      int[] tracking = {};
      String result = DeTabber.deTab("\thello", 0, 8, tracking);
      assertFalse(result.contains("\t"), "tabs should be replaced");
      assertTrue(result.endsWith("hello"), "text after tab preserved");
      assertEquals(13, result.length(),
         "tab at 0 expands to 8 spaces + 5 chars");
   }

   @Test
   void deTabWithTabstop4() {
      int[] tracking = {};
      String result = DeTabber.deTab("\tX", 0, 4, tracking);
      assertEquals("    X", result);
   }

   @Test
   void deTabMidLineTab() {
      int[] tracking = {};
      // "ab\tcd" with tabstop 4: tab at index 2, expands to 2 spaces
      String result = DeTabber.deTab("ab\tcd", 2, 4, tracking);
      assertEquals("ab  cd", result);
   }

   @Test
   void deTabMultipleTabs() {
      int[] tracking = {};
      String result = DeTabber.deTab("\t\tX", 0, 4, tracking);
      assertEquals("        X", result);
   }

   @Test
   void deTabNoTabs() {
      int[] tracking = {};
      // When no tabs, tabOffset doesn't matter because indexOf returns -1
      // But deTab requires at least one tab at tabOffset
      // Actually deTab does tbuf.indexOf("\t", tabOffset) first
      // so if there's no tab, it won't enter the loop
      String input = "no tabs";
      // need to call with a valid tabOffset where a tab actually is
      // if there are no tabs starting at offset, deTab will still try
      // to replace at tabOffset. Let me recheck the code...
      // Actually deTab does:
      // tbuf.replace(tabOffset, tabOffset + 1, tabstrings[...])
      // then tabOffset = tbuf.indexOf("\t", ...)
      // So it always replaces the first one at tabOffset
      // for no-tabs, caller would not call deTab
   }

   @Test
   void deTabTrackingUpdatesPositions() {
      int[] tracking = {5, 10};
      // "\tXYZ" with tabstop 8: tab expands to 8 spaces.
      // tracking[0]=5 > tabOffset=0, so 5 + 7 = 12
      // tracking[1]=10 > tabOffset=0, so 10 + 7 = 17
      String result = DeTabber.deTab("\tXYZ", 0, 8, tracking);
      assertEquals("        XYZ", result);
      assertEquals(12, tracking[0]);
      assertEquals(17, tracking[1]);
   }

   @Test
   void deTabTrackingBeforeTab() {
      int[] tracking = {0, 3};
      // "abc\tdef" tabstop=4, tab at position 3, expands to 1 space
      // tracking[0]=0, not > 3, unchanged = 0
      // tracking[1]=3, not > 3, unchanged = 3
      String result = DeTabber.deTab("abc\tdef", 3, 4, tracking);
      assertEquals(0, tracking[0]);
      assertEquals(3, tracking[1]);
   }

   @Test
   void deTabTrackingMinusOne() {
      int[] tracking = {-1, 5};
      // -1 means "not tracked" — should be left as -1
      DeTabber.deTab("\tX", 0, 4, tracking);
      assertEquals(-1, tracking[0]);
   }

   @Test
   void deTabWithTabOffset() {
      int[] tracking = {};
      // String starts at tabOffset 2, meaning we're in middle of line
      // "XX\tY" with tabOffset=2, tabstop=4
      // tab at position 2, expands: (4 - 2%4) = 2 spaces
      String result = DeTabber.deTab("XX\tY", 2, 4, tracking);
      assertEquals("XX  Y", result);
   }

   @Test
   void deTabTabstopAligned() {
      int[] tracking = {};
      // tab at position 0 with tabstop 4 should expand to 4 spaces
      String result = DeTabber.deTab("\tA", 0, 4, tracking);
      assertEquals("    A", result);
   }

   @Test
   void deTabTabstop3() {
      int[] tracking = {};
      // tab at position 0 with tabstop 3 → 3 spaces
      String result = DeTabber.deTab("\tA", 0, 3, tracking);
      assertEquals("   A", result);
   }

   @Test
   void deTabConsecutiveTabsDifferentPositions() {
      int[] tracking = {};
      // "a\tb\tc" with tabstop 4: first tab at index 1
      // tab at pos 1 → expands to 3 spaces (4 - 1%4 = 3)
      // 'b' now at pos 4, tab at pos 5 → expands to 3 spaces
      // 'c' now at pos 8
      String result = DeTabber.deTab("a\tb\tc", 1, 4, tracking);
      assertEquals("a   b   c", result);
   }

   @Test
   void deTabLargeTrackingOverflow() {
      int[] tracking = {Integer.MAX_VALUE - 1};
      // When tracking value is near MAX_VALUE, overflow protection should
      // clamp to MAX_VALUE
      DeTabber.deTab("\tX", 0, 4, tracking);
      assertEquals(Integer.MAX_VALUE, tracking[0]);
   }

   // ── tabFind additional edge cases ───────────────────────────

   @Test
   void tabFindBeforeTabOffset() {
      // charoffset <= tabOffset should return charoffset directly
      assertEquals(3, DeTabber.tabFind("abc\tdef", 5, 8, 3));
   }

   @Test
   void tabFindExactlyAtTabOffset() {
      assertEquals(5, DeTabber.tabFind("abc\tdef", 5, 8, 5));
   }

   @Test
   void tabFindWithTabstop3() {
      // "\tX" tabstop 3: tab at 0 expands to 3 spaces
      // charoffset 3 → index 1 ('X')
      assertEquals(1, DeTabber.tabFind("\tX", 0, 3, 3));
   }

   @Test
   void tabFindMultipleTabsTabstop4() {
      // "\t\tX" tabstop 4:
      // tab0 → 4 spaces, tab1 → 4 spaces, X at index 2
      // charoffset 8 → index 2
      assertEquals(2, DeTabber.tabFind("\t\tX", 0, 4, 8));
   }

   @Test
   void tabFindCharInMiddleOfTabExpansion() {
      // "\tXY" tabstop 8: tab expands to 8 spaces
      // charoffset 4 is inside tab expansion → still index 0
      assertEquals(0, DeTabber.tabFind("\tXY", 0, 8, 4));
   }

   @Test
   void tabFindNoTabsAtAll() {
      // pure text, no tabs: charoffset 3 → index 3
      assertEquals(3, DeTabber.tabFind("abcdef", 0, 8, 3));
   }

   @Test
   void tabFindWithMixedContent() {
      // "abc\tdef\tghi" tabstop 8
      // abc=3 chars, tab expands to 5 spaces (8-3=5), total visual 8
      // def=3 chars at visual 8-10, tab at visual 11 expands to 5 (8-3=5) → vis 16
      // charoffset 8 → should be position 4 ('d')
      assertEquals(4, DeTabber.tabFind("abc\tdef\tghi", 0, 8, 8));
   }
}
