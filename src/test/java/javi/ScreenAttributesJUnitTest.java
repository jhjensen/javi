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
}
