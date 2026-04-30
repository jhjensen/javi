package javi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JUnit 5 tests for {@link ScreenAttributes} grid operations.
 */
class ScreenAttributesJUnitTest {

   private ScreenAttributes sa;

   @BeforeEach
   void setUp() {
      sa = new ScreenAttributes(80);
   }

   @Test
   @DisplayName("getAttr returns DEFAULT for unset cell")
   void unsetCellReturnsDefault() {
      assertEquals(CellAttr.DEFAULT, sa.getAttr(1, 0));
   }

   @Test
   @DisplayName("setAttr / getAttr round-trip")
   void setGetRoundTrip() {
      int attr = CellAttr.pack(true, false, false, 2, -1);
      sa.setAttr(5, 10, attr);
      assertEquals(attr, sa.getAttr(5, 10));
      assertEquals(CellAttr.DEFAULT, sa.getAttr(5, 9));
   }

   @Test
   @DisplayName("fillAttr sets a range of columns")
   void fillRange() {
      int attr = CellAttr.pack(false, true, false, -1, 3);
      sa.fillAttr(3, 5, 10, attr);
      assertEquals(CellAttr.DEFAULT, sa.getAttr(3, 4));
      for (int c = 5; c < 10; c++) {
         assertEquals(attr, sa.getAttr(3, c),
            "column " + c + " should have attribute");
      }
      assertEquals(CellAttr.DEFAULT, sa.getAttr(3, 10));
   }

   @Test
   @DisplayName("eraseLine removes attribute row")
   void eraseLineRemovesRow() {
      sa.setAttr(7, 0, CellAttr.pack(true, false, false, -1, -1));
      assertEquals(1, sa.size());
      sa.eraseLine(7);
      assertEquals(0, sa.size());
      assertEquals(CellAttr.DEFAULT, sa.getAttr(7, 0));
   }

   @Test
   @DisplayName("eraseScreen removes range of rows")
   void eraseScreenRange() {
      for (int line = 1; line <= 5; line++)
         sa.setAttr(line, 0, CellAttr.pack(true, false, false, -1, -1));
      assertEquals(5, sa.size());
      sa.eraseScreen(2, 5); // erase lines 2, 3, 4
      assertEquals(2, sa.size());
      assertNull(sa.getRow(3));
   }

   @Test
   @DisplayName("eraseToEnd clears from column to end")
   void eraseToEnd() {
      int attr = CellAttr.pack(false, false, true, 1, -1);
      sa.fillAttr(1, 0, 20, attr);
      sa.eraseToEnd(1, 10);
      assertEquals(attr, sa.getAttr(1, 9));
      assertEquals(CellAttr.DEFAULT, sa.getAttr(1, 10));
      assertEquals(CellAttr.DEFAULT, sa.getAttr(1, 19));
   }

   @Test
   @DisplayName("clear removes all rows")
   void clearAll() {
      sa.setAttr(1, 0, 1);
      sa.setAttr(2, 0, 2);
      sa.clear();
      assertEquals(0, sa.size());
   }

   @Test
   @DisplayName("evictBefore discards old lines")
   void evictBefore() {
      sa.setAttr(10, 0, 1);
      sa.setAttr(20, 0, 2);
      sa.setAttr(30, 0, 3);
      sa.evictBefore(20);
      assertEquals(2, sa.size());
      assertEquals(CellAttr.DEFAULT, sa.getAttr(10, 0));
      assertEquals(2, sa.getAttr(20, 0));
   }

   @Test
   @DisplayName("setColumns updates capacity for new rows")
   void setColumns() {
      sa.setColumns(120);
      assertEquals(120, sa.getColumns());
   }

   @Test
   @DisplayName("setAttr auto-grows row when column exceeds capacity")
   void autoGrowRow() {
      sa.setAttr(1, 200, 42);
      assertEquals(42, sa.getAttr(1, 200));
   }

   @Test
   @DisplayName("getAttr with negative column returns DEFAULT")
   void negativeColumnReturnsDefault() {
      assertEquals(CellAttr.DEFAULT, sa.getAttr(1, -1));
   }

   @Test
   @DisplayName("fillAttr with empty range is a no-op")
   void fillEmptyRange() {
      sa.fillAttr(1, 5, 5, 42);
      assertEquals(0, sa.size());
   }

   @Test
   @DisplayName("shiftLinesUp moves rows upward and clears bottom")
   void shiftLinesUp() {
      int a1 = CellAttr.pack(true, false, false, 1, -1);
      int a2 = CellAttr.pack(false, true, false, 2, -1);
      int a3 = CellAttr.pack(false, false, true, 3, -1);
      sa.setAttr(10, 0, a1);
      sa.setAttr(11, 0, a2);
      sa.setAttr(12, 0, a3);
      sa.shiftLinesUp(10, 13, 1);
      // line 10 now has what was in line 11
      assertEquals(a2, sa.getAttr(10, 0));
      // line 11 now has what was in line 12
      assertEquals(a3, sa.getAttr(11, 0));
      // line 12 was cleared
      assertEquals(CellAttr.DEFAULT, sa.getAttr(12, 0));
   }

   @Test
   @DisplayName("shiftLinesDown moves rows downward and clears top")
   void shiftLinesDown() {
      int a1 = CellAttr.pack(true, false, false, 1, -1);
      int a2 = CellAttr.pack(false, true, false, 2, -1);
      int a3 = CellAttr.pack(false, false, true, 3, -1);
      sa.setAttr(10, 0, a1);
      sa.setAttr(11, 0, a2);
      sa.setAttr(12, 0, a3);
      sa.shiftLinesDown(10, 13, 1);
      // line 10 was cleared (shifted out)
      assertEquals(CellAttr.DEFAULT, sa.getAttr(10, 0));
      // line 11 now has what was in line 10
      assertEquals(a1, sa.getAttr(11, 0));
      // line 12 now has what was in line 11
      assertEquals(a2, sa.getAttr(12, 0));
   }

   @Test
   @DisplayName("shiftLinesUp with count=0 is a no-op")
   void shiftUpZeroCount() {
      sa.setAttr(5, 0, 42);
      sa.shiftLinesUp(5, 10, 0);
      assertEquals(42, sa.getAttr(5, 0));
   }

   @Test
   @DisplayName("shiftLinesDown with count=0 is a no-op")
   void shiftDownZeroCount() {
      sa.setAttr(5, 0, 42);
      sa.shiftLinesDown(5, 10, 0);
      assertEquals(42, sa.getAttr(5, 0));
   }

   @Test
   @DisplayName("snapshot creates an independent deep copy")
   void snapshotIsDeepCopy() {
      int attr = CellAttr.pack(true, false, false, 2, -1);
      sa.fillAttr(1, 0, 10, attr);
      sa.setAttr(5, 3, 99);

      ScreenAttributes copy = sa.snapshot();

      // Verify copy has the same data
      for (int c = 0; c < 10; c++)
         assertEquals(sa.getAttr(1, c), copy.getAttr(1, c),
            "col " + c + " should match");
      assertEquals(99, copy.getAttr(5, 3));

      // Modify original — copy should be unaffected
      sa.setAttr(1, 0, 77);
      sa.eraseLine(5);
      assertEquals(attr, copy.getAttr(1, 0),
         "copy should not be affected by original change");
      assertEquals(99, copy.getAttr(5, 3),
         "copy should not be affected by original erase");
   }

   @Test
   @DisplayName("restoreFrom replaces all data from source")
   void restoreFromReplacesData() {
      sa.setAttr(1, 0, 11);
      sa.setAttr(2, 0, 22);

      ScreenAttributes saved = new ScreenAttributes(80);
      saved.setAttr(10, 0, 100);
      saved.setAttr(20, 0, 200);

      sa.restoreFrom(saved);

      // Original data should be gone
      assertEquals(CellAttr.DEFAULT, sa.getAttr(1, 0));
      assertEquals(CellAttr.DEFAULT, sa.getAttr(2, 0));
      // Restored data should be present
      assertEquals(100, sa.getAttr(10, 0));
      assertEquals(200, sa.getAttr(20, 0));
   }

   @Test
   @DisplayName("restoreFrom is a deep copy, not shared")
   void restoreFromIsDeepCopy() {
      ScreenAttributes source = new ScreenAttributes(80);
      source.setAttr(5, 0, 42);

      sa.restoreFrom(source);

      // Modify source — sa should be unaffected
      source.setAttr(5, 0, 99);
      assertEquals(42, sa.getAttr(5, 0),
         "restored data should be independent of source");
   }

   // ----- shiftCellsLeft (DCH) -----

   @Test
   @DisplayName("shiftCellsLeft shifts attributes toward column 0")
   void shiftCellsLeftBasic() {
      // Set attrs: col 0=10, 1=11, 2=12, 3=13, 4=14
      for (int c = 0; c < 5; c++)
         sa.setAttr(1, c, 10 + c);
      // Delete 2 chars at col 1 — cols 3,4 shift to 1,2
      sa.shiftCellsLeft(1, 1, 2, 5, 99);
      assertEquals(10, sa.getAttr(1, 0), "col 0 unchanged");
      assertEquals(13, sa.getAttr(1, 1), "col 3 shifted to 1");
      assertEquals(14, sa.getAttr(1, 2), "col 4 shifted to 2");
      assertEquals(99, sa.getAttr(1, 3), "col 3 filled");
      assertEquals(99, sa.getAttr(1, 4), "col 4 filled");
   }

   @Test
   @DisplayName("shiftCellsLeft on absent row is a no-op")
   void shiftCellsLeftAbsentRow() {
      sa.shiftCellsLeft(1, 0, 3, 80, 99);
      assertEquals(CellAttr.DEFAULT, sa.getAttr(1, 0));
   }

   @Test
   @DisplayName("shiftCellsLeft at end fills all shifted positions")
   void shiftCellsLeftAtEnd() {
      for (int c = 0; c < 4; c++)
         sa.setAttr(1, c, 20 + c);
      // Delete 3 at col 1 — only col 0 survives, rest filled
      sa.shiftCellsLeft(1, 1, 3, 4, 77);
      assertEquals(20, sa.getAttr(1, 0));
      assertEquals(77, sa.getAttr(1, 1));
      assertEquals(77, sa.getAttr(1, 2));
      assertEquals(77, sa.getAttr(1, 3));
   }

   // ----- shiftCellsRight (ICH) -----

   @Test
   @DisplayName("shiftCellsRight shifts attributes away from col 0")
   void shiftCellsRightBasic() {
      // Set attrs: col 0=10, 1=11, 2=12, 3=13
      for (int c = 0; c < 4; c++)
         sa.setAttr(1, c, 10 + c);
      // Insert 2 at col 1 — cols 1,2 shift to 3,4; col 3 lost
      sa.shiftCellsRight(1, 1, 2, 5, 88);
      assertEquals(10, sa.getAttr(1, 0), "col 0 unchanged");
      assertEquals(88, sa.getAttr(1, 1), "col 1 filled");
      assertEquals(88, sa.getAttr(1, 2), "col 2 filled");
      assertEquals(11, sa.getAttr(1, 3), "col 1 shifted to 3");
      assertEquals(12, sa.getAttr(1, 4), "col 2 shifted to 4");
   }

   @Test
   @DisplayName("shiftCellsRight on absent row creates row")
   void shiftCellsRightAbsentRow() {
      sa.shiftCellsRight(1, 5, 3, 80, 55);
      assertEquals(55, sa.getAttr(1, 5));
      assertEquals(55, sa.getAttr(1, 6));
      assertEquals(55, sa.getAttr(1, 7));
      assertEquals(CellAttr.DEFAULT, sa.getAttr(1, 4));
   }

   @Test
   @DisplayName("shiftCellsRight truncates at column limit")
   void shiftCellsRightTruncates() {
      for (int c = 0; c < 5; c++)
         sa.setAttr(1, c, 30 + c);
      // Insert 2 at col 3 in 5-col terminal — cols 3,4 shift to 5,6 (lost)
      sa.shiftCellsRight(1, 3, 2, 5, 66);
      assertEquals(30, sa.getAttr(1, 0));
      assertEquals(31, sa.getAttr(1, 1));
      assertEquals(32, sa.getAttr(1, 2));
      assertEquals(66, sa.getAttr(1, 3));
      assertEquals(66, sa.getAttr(1, 4));
   }
}
