package javi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Extended coverage tests for {@link ScreenAttributes} — edge
 * cases, default constructor, fillAttr boundaries, and row access.
 */
class ScreenAttributesExtendedJUnitTest {

   private ScreenAttributes sa;

   @BeforeEach
   void setUp() {
      sa = new ScreenAttributes(80);
   }

   // ── Default constructor ──────────────────────────────────────

   @Test
   void defaultConstructorUses256Columns() {
      ScreenAttributes def = new ScreenAttributes();
      assertEquals(256, def.getColumns());
   }

   @Test
   void zeroColumnsUsesDefault() {
      ScreenAttributes zero = new ScreenAttributes(0);
      assertEquals(256, zero.getColumns());
   }

   @Test
   void negativeColumnsUsesDefault() {
      ScreenAttributes neg = new ScreenAttributes(-10);
      assertEquals(256, neg.getColumns());
   }

   // ── setAttr edge cases ───────────────────────────────────────

   @Test
   void setAttrNegativeColumnIgnored() {
      sa.setAttr(1, -1, 42);
      assertNull(sa.getRow(1),
         "setAttr with negative column should not create row");
   }

   @Test
   void setAttrExpandsExistingRow() {
      sa.setAttr(1, 0, 10);
      sa.setAttr(1, 300, 20); // beyond initial capacity
      assertEquals(10, sa.getAttr(1, 0));
      assertEquals(20, sa.getAttr(1, 300));
   }

   @Test
   void setAttrOnMultipleLines() {
      for (int line = 1; line <= 10; line++)
         sa.setAttr(line, 0, line * 100);
      assertEquals(10, sa.size());
      assertEquals(500, sa.getAttr(5, 0));
   }

   // ── fillAttr edge cases ──────────────────────────────────────

   @Test
   void fillAttrStartEqualsEndNoOp() {
      sa.fillAttr(1, 5, 5, 42);
      assertNull(sa.getRow(1),
         "fillAttr with start==end should not create row");
   }

   @Test
   void fillAttrStartGreaterThanEndNoOp() {
      sa.fillAttr(1, 10, 5, 42);
      assertNull(sa.getRow(1),
         "fillAttr with start>end should not create row");
   }

   @Test
   void fillAttrExpandsExistingRow() {
      sa.setAttr(1, 0, 10);
      sa.fillAttr(1, 200, 300, 42); // expand beyond initial
      assertEquals(10, sa.getAttr(1, 0));
      assertEquals(42, sa.getAttr(1, 250));
   }

   @Test
   void fillAttrSingleColumn() {
      sa.fillAttr(1, 5, 6, 99);
      assertEquals(99, sa.getAttr(1, 5));
      assertEquals(CellAttr.DEFAULT, sa.getAttr(1, 6));
   }

   // ── getRow tests ─────────────────────────────────────────────

   @Test
   void getRowReturnsNullForAbsentLine() {
      assertNull(sa.getRow(42));
   }

   @Test
   void getRowReturnsSameArray() {
      sa.setAttr(5, 3, 77);
      int[] row = sa.getRow(5);
      assertNotNull(row);
      assertEquals(77, row[3]);
   }

   // ── eraseToEnd edge cases ────────────────────────────────────

   @Test
   void eraseToEndOnAbsentLineNoOp() {
      // Should not crash
      sa.eraseToEnd(99, 0);
      assertNull(sa.getRow(99));
   }

   @Test
   void eraseToEndBeyondRowLength() {
      sa.setAttr(1, 0, 42);
      sa.eraseToEnd(1, 999); // beyond actual length
      // Should not crash; attr at 0 should still exist
      assertEquals(42, sa.getAttr(1, 0));
   }

   @Test
   void eraseToEndFromZero() {
      sa.fillAttr(1, 0, 20, 42);
      sa.eraseToEnd(1, 0);
      for (int c = 0; c < 20; c++)
         assertEquals(CellAttr.DEFAULT, sa.getAttr(1, c),
            "column " + c + " should be DEFAULT after erase from 0");
   }

   // ── evictBefore edge cases ───────────────────────────────────

   @Test
   void evictBeforeZeroKeepsAll() {
      sa.setAttr(1, 0, 10);
      sa.setAttr(2, 0, 20);
      sa.evictBefore(0);
      assertEquals(2, sa.size());
   }

   @Test
   void evictBeforeAllRemovesAll() {
      sa.setAttr(1, 0, 10);
      sa.setAttr(2, 0, 20);
      sa.evictBefore(100);
      assertEquals(0, sa.size());
   }

   // ── setColumns edge cases ────────────────────────────────────

   @Test
   void setColumnsZeroIgnored() {
      sa.setColumns(0);
      assertEquals(80, sa.getColumns(),
         "setColumns(0) should not change capacity");
   }

   @Test
   void setColumnsNegativeIgnored() {
      sa.setColumns(-5);
      assertEquals(80, sa.getColumns(),
         "setColumns(-5) should not change capacity");
   }

   @Test
   void setColumnsUpdatesCapacity() {
      sa.setColumns(200);
      assertEquals(200, sa.getColumns());
   }

   // ── size tests ───────────────────────────────────────────────

   @Test
   void sizeReflectsOperations() {
      assertEquals(0, sa.size());
      sa.setAttr(1, 0, 1);
      assertEquals(1, sa.size());
      sa.setAttr(2, 0, 2);
      assertEquals(2, sa.size());
      sa.eraseLine(1);
      assertEquals(1, sa.size());
      sa.clear();
      assertEquals(0, sa.size());
   }

   // ── getAttr out of range ─────────────────────────────────────

   @Test
   void getAttrBeyondRowLength() {
      sa.setAttr(1, 0, 42);
      assertEquals(CellAttr.DEFAULT, sa.getAttr(1, 999),
         "column beyond row length should return DEFAULT");
   }
}
