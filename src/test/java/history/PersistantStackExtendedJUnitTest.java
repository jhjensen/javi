package history;

import java.io.File;
import java.io.IOException;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended JUnit 5 coverage for {@link PersistantStack} — navigation,
 * index management, invalidation, push-in-middle truncation, and
 * unsupported operations.
 */
class PersistantStackExtendedJUnitTest {

   private static final byte[] A = {10, 20, 30};
   private static final byte[] B = {40, 50};
   private static final byte[] C = {60, 70, 80, 90};
   private static final byte[] D = {1};

   // ── Navigation: previous / curr / hasNext / hasPrevious ──

   @Test
   void previousReturnsEarlierRecord(@TempDir File tmpDir) throws IOException {
      File hf = new File(tmpDir, "ps-prev.test");
      TestPS stack = new TestPS();
      stack.newFile(hf);
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(A);
      iter.push(B);
      iter.push(C);
      iter.close();

      TestPS stack2 = new TestPS();
      stack2.setFile(hf);
      PersistantStack<Object>.PSIterator iter2 = stack2.createIterator();
      iter2.resetCache();

      assertArrayEquals(A, (byte[]) iter2.next());
      assertArrayEquals(B, (byte[]) iter2.next());
      assertArrayEquals(C, (byte[]) iter2.next());
      // now navigate backwards
      assertArrayEquals(B, (byte[]) iter2.previous());
      assertArrayEquals(A, (byte[]) iter2.previous());
      iter2.close();
   }

   @Test
   void currReturnsSameRecordRepeatedly(@TempDir File tmpDir)
         throws IOException {
      File hf = new File(tmpDir, "ps-curr.test");
      TestPS stack = new TestPS();
      stack.newFile(hf);
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(A);
      iter.push(B);
      iter.close();

      TestPS stack2 = new TestPS();
      stack2.setFile(hf);
      PersistantStack<Object>.PSIterator iter2 = stack2.createIterator();
      iter2.resetCache();

      iter2.next(); // A
      iter2.next(); // B
      assertArrayEquals(B, (byte[]) iter2.curr());
      assertArrayEquals(B, (byte[]) iter2.curr());
      iter2.close();
   }

   @Test
   void hasNextAndHasPrevious(@TempDir File tmpDir) throws IOException {
      File hf = new File(tmpDir, "ps-hasnext.test");
      TestPS stack = new TestPS();
      stack.newFile(hf);
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(A);
      iter.push(B);
      iter.close();

      TestPS stack2 = new TestPS();
      stack2.setFile(hf);
      PersistantStack<Object>.PSIterator iter2 = stack2.createIterator();
      iter2.resetCache();

      assertTrue(iter2.hasNext(), "should have next before any reads");
      assertFalse(iter2.hasPrevious(), "no previous at start");

      iter2.next(); // A — index 0
      assertTrue(iter2.hasNext(), "should have next after first");
      // hasPrevious() returns recordIndex > 0, so false at index 0
      assertFalse(iter2.hasPrevious(), "no previous at index 0");

      iter2.next(); // B — index 1
      assertFalse(iter2.hasNext(), "no more after last");
      assertTrue(iter2.hasPrevious(), "should have previous at index 1");
      iter2.close();
   }

   // ── Index management ─────────────────────────────────────

   @Test
   void getIndexTracksPosition(@TempDir File tmpDir) throws IOException {
      File hf = new File(tmpDir, "ps-index.test");
      TestPS stack = new TestPS();
      stack.newFile(hf);
      PersistantStack<Object>.PSIterator iter = stack.createIterator();

      assertEquals(-1, iter.getIndex(), "initial index should be -1");
      iter.push(A);
      assertEquals(0, iter.getIndex());
      iter.push(B);
      assertEquals(1, iter.getIndex());
      iter.close();
   }

   @Test
   void nextIndexAndPreviousIndex(@TempDir File tmpDir) throws IOException {
      File hf = new File(tmpDir, "ps-nextidx.test");
      TestPS stack = new TestPS();
      stack.newFile(hf);
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(A);
      iter.push(B);
      iter.close();

      TestPS stack2 = new TestPS();
      stack2.setFile(hf);
      PersistantStack<Object>.PSIterator iter2 = stack2.createIterator();
      iter2.resetCache();

      assertEquals(0, iter2.nextIndex());
      assertEquals(-2, iter2.previousIndex());

      iter2.next(); // A
      assertEquals(1, iter2.nextIndex());
      assertEquals(-1, iter2.previousIndex());
      iter2.close();
   }

   @Test
   void incrementAndDecrement(@TempDir File tmpDir) throws IOException {
      File hf = new File(tmpDir, "ps-incdec.test");
      TestPS stack = new TestPS();
      stack.newFile(hf);
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(A);
      iter.push(B);
      iter.push(C);
      iter.close();

      TestPS stack2 = new TestPS();
      stack2.setFile(hf);
      PersistantStack<Object>.PSIterator iter2 = stack2.createIterator();
      iter2.resetCache();

      iter2.next(); // A — index 0
      iter2.increment();
      assertEquals(1, iter2.getIndex());
      assertArrayEquals(B, (byte[]) iter2.curr());

      iter2.decrement();
      assertEquals(0, iter2.getIndex());
      assertArrayEquals(A, (byte[]) iter2.curr());
      iter2.close();
   }

   // ── Invalidation ─────────────────────────────────────────

   @Test
   void invalidateAndIsValid(@TempDir File tmpDir) throws IOException {
      File hf = new File(tmpDir, "ps-invalid.test");
      TestPS stack = new TestPS();
      stack.newFile(hf);
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(A);

      assertTrue(iter.isValid());
      iter.invalidate();
      assertFalse(iter.isValid());
      // Push after invalidate should still work (re-creates valid state)
   }

   // ── setEqual ─────────────────────────────────────────────

   @Test
   void setEqualCopiesIndex(@TempDir File tmpDir) throws IOException {
      File hf = new File(tmpDir, "ps-seteq.test");
      TestPS stack = new TestPS();
      stack.newFile(hf);
      PersistantStack<Object>.PSIterator iter1 = stack.createIterator();
      PersistantStack<Object>.PSIterator iter2 = stack.createIterator();
      iter1.push(A);
      iter1.push(B);
      // iter1 is at index 1
      assertEquals(1, iter1.getIndex());
      assertEquals(-1, iter2.getIndex());

      iter2.setEqual(iter1);
      assertEquals(1, iter2.getIndex());
   }

   // ── Push-in-middle truncation ────────────────────────────

   @Test
   void pushInMiddleTruncatesRemaining(@TempDir File tmpDir)
         throws IOException {
      File hf = new File(tmpDir, "ps-trunc.test");
      TestPS stack = new TestPS();
      stack.newFile(hf);
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(A);
      iter.push(B);
      iter.push(C);
      // now at index 2 with 3 elements
      iter.decrement(); // back to index 1
      iter.decrement(); // back to index 0
      iter.push(D); // replaces B and C; stack is now [A, D]
      iter.close();

      TestPS stack2 = new TestPS();
      stack2.setFile(hf);
      PersistantStack<Object>.PSIterator iter2 = stack2.createIterator();
      iter2.resetCache();

      assertArrayEquals(A, (byte[]) iter2.next());
      assertArrayEquals(D, (byte[]) iter2.next());
      assertFalse(iter2.hasNext(), "should only have 2 elements after truncation");
      iter2.close();
   }

   // ── UnsupportedOperationException ────────────────────────

   @Test
   void setThrowsUnsupported(@TempDir File tmpDir) throws IOException {
      File hf = new File(tmpDir, "ps-set.test");
      TestPS stack = new TestPS();
      stack.newFile(hf);
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      assertThrows(UnsupportedOperationException.class,
         () -> iter.set(A));
   }

   @Test
   void addThrowsUnsupported(@TempDir File tmpDir) throws IOException {
      File hf = new File(tmpDir, "ps-add.test");
      TestPS stack = new TestPS();
      stack.newFile(hf);
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      assertThrows(UnsupportedOperationException.class,
         () -> iter.add(A));
   }

   @Test
   void removeThrowsUnsupported(@TempDir File tmpDir) throws IOException {
      File hf = new File(tmpDir, "ps-remove.test");
      TestPS stack = new TestPS();
      stack.newFile(hf);
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      assertThrows(UnsupportedOperationException.class,
         iter::remove);
   }

   // ── toString ─────────────────────────────────────────────

   @Test
   void toStringContainsIndexAndSize(@TempDir File tmpDir)
         throws IOException {
      File hf = new File(tmpDir, "ps-tostr.test");
      TestPS stack = new TestPS();
      stack.newFile(hf);
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(A);

      String str = iter.toString();
      assertTrue(str.contains("index"), "toString should mention index");
      assertTrue(str.contains("size"), "toString should mention size");
   }

   // ── Memory-only mode (no file) ──────────────────────────

   @Test
   void memoryOnlyPushAndNavigate() {
      TestPS stack = new TestPS();
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(A);
      iter.push(B);
      iter.push(C);

      assertEquals(2, iter.getIndex());
      assertArrayEquals(C, (byte[]) iter.curr());
      assertArrayEquals(B, (byte[]) iter.previous());
      assertArrayEquals(A, (byte[]) iter.previous());
   }

   @Test
   void memoryOnlyPushInMiddleTruncates() {
      TestPS stack = new TestPS();
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(A);
      iter.push(B);
      iter.push(C);

      iter.decrement(); // index 1
      iter.push(D); // replaces C

      assertEquals(2, iter.getIndex());
      assertArrayEquals(D, (byte[]) iter.curr());
      assertFalse(iter.hasNext());
   }

   @Test
   void memoryOnlySizeTracking() {
      TestPS stack = new TestPS();
      PersistantStack<Object>.PSIterator iter = stack.createIterator();

      assertEquals(0, stack.size());
      iter.push(A);
      assertEquals(1, stack.size());
      iter.push(B);
      assertEquals(2, stack.size());
      iter.push(C);
      assertEquals(3, stack.size());
   }

   @Test
   void nextPastEndThrowsInMemoryMode() {
      TestPS stack = new TestPS();
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(A);

      // After push(A), index is already 0 (size 1).
      // next() tries to advance to index 1, which is >= size.
      assertThrows(NoSuchElementException.class, iter::next);
   }

   // ── Stack size ───────────────────────────────────────────

   @Test
   void sizeReflectsFilePersistedRecords(@TempDir File tmpDir)
         throws IOException {
      File hf = new File(tmpDir, "ps-size.test");
      TestPS stack = new TestPS();
      stack.newFile(hf);
      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(A);
      iter.push(B);
      iter.push(C);
      assertEquals(3, stack.size());
      iter.close();

      TestPS stack2 = new TestPS();
      stack2.setFile(hf);
      assertEquals(3, stack2.size());
   }
}
