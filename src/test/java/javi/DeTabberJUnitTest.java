package javi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JUnit 5 port of the inline tests from {@link DeTabber#main}.
 * Tests the {@code tabFind} algorithm which maps a pixel/character offset
 * to a string index, accounting for tab-stop expansion.
 */
class DeTabberJUnitTest {

   @Test
   void tabFindInMiddleOfLineAtTabStop() {
      // Char offset 34 falls in the second tab-expanded region; string index 27
      assertEquals(27, DeTabber.tabFind(
         "123456789012345678\t01234567\t901234567 ", 18, 8, 34));
   }

   @Test
   void tabFindAtStartOfLineWithLeadingTab() {
      // Leading tab expands to 8 chars; offset 8 maps to index 1 (char 'a')
      assertEquals(1, DeTabber.tabFind("\tat ", 0, 8, 8));
   }

   @Test
   void tabFindAfterLeadingTab() {
      // Offset 10 is 2 chars past the tab stop; maps to index 3 ('t')
      assertEquals(3, DeTabber.tabFind("\tat ", 0, 8, 10));
   }

   @Test
   void tabFindAtLineStart() {
      // Offset 0 always maps to string index 0
      assertEquals(0, DeTabber.tabFind(
         "123456789012345678\t01234567\t901234567 ", 18, 8, 0));
   }

   @Test
   void tabFindWithSmallTabStopInsideTabbed() {
      // tabstop=4: "1234" occupies columns 4-7; offset 5 maps to index 4
      assertEquals(4, DeTabber.tabFind("1234\t1234", 4, 4, 5));
   }

   @Test
   void tabFindAtEndOfLineWithTabs() {
      // Offset 37 is after the last tab; maps back to index 27
      assertEquals(27, DeTabber.tabFind(
         "123456789012345678\t01234567\t901234567 ", 18, 8, 37));
   }

   @Test
   void tabFindInStackTraceLineWithTabs() {
      // Stack trace line with leading tab; offset 0 -> index 0
      assertEquals(0, DeTabber.tabFind(
         "\tat history.PersistantStack.set\tFile(PersistantStack.java:518",
         0, 8, 0));
   }

   @Test
   void tabFindAtTabBoundarySmallTabStop() {
      // Offset exactly at the tab character start; tabstop=4, offset 4 -> index 4
      assertEquals(4, DeTabber.tabFind("1234\t1234", 4, 4, 4));
   }

}
