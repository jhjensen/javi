package history;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JUnit 5 coverage for {@link PersistantStack} via the package-private
 * {@code TestPS}/{@code TestIterator} helper defined in PSTest.java.
 *
 * <p>These tests replace (and are a subset of) the equivalent cases in the
 * legacy {@code PSTest} main-method suite; full coverage including the
 * callback / delete-file scenarios remains in {@code PSTest.java}.
 */
class PersistantStackJUnitTest {

   private static final byte[] B1 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 0};
   private static final byte[] B2 = {11};
   private static final byte[] R3 = {0, 0, 0, 1, 0, 0, 0, 2};

   /**
    * Push three records and confirm they can be read back in order.
    */
   @Test
   void pushAndIterateRoundTrip(@TempDir File tmpDir) throws IOException {
      File hf = new File(tmpDir, "psjunit-roundtrip.test");

      TestPS stack = new TestPS();
      stack.newFile(hf);

      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(B1);
      iter.push(B2);
      iter.push(R3);
      iter.close();

      // re-open the file and verify
      TestPS stack2 = new TestPS();
      stack2.setFile(hf);
      PersistantStack<Object>.PSIterator iter2 = stack2.createIterator();
      iter2.resetCache();

      assertArrayEquals(B1, (byte[]) iter2.next(), "first record must be B1");
      assertArrayEquals(B2, (byte[]) iter2.next(), "second record must be B2");
      assertArrayEquals(R3, (byte[]) iter2.next(), "third record must be R3");
      iter2.close();
   }

   /**
    * After a push the stack size increases; after decrement + push the trailing
    * record is replaced.
    */
   @Test
   void decrementThenPushReplacesTopRecord(@TempDir File tmpDir)
         throws IOException {
      File hf = new File(tmpDir, "psjunit-decrement.test");

      TestPS stack = new TestPS();
      stack.newFile(hf);

      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(B1);
      iter.push(B2); // stack: [B1, B2]
      iter.decrement();
      iter.push(R3); // replaces B2: stack [B1, R3]
      iter.idleSave();
      stack.terminateWEP();

      TestPS stack2 = new TestPS();
      stack2.setFile(hf);
      PersistantStack<Object>.PSIterator iter2 = stack2.createIterator();
      iter2.resetCache();

      assertArrayEquals(B1, (byte[]) iter2.next(), "first record must be B1");
      assertArrayEquals(R3, (byte[]) iter2.next(), "second record must be R3 after decrement+push");
      iter2.close();
   }

   /**
    * Iterating past the last record must throw {@link java.util.NoSuchElementException}.
    */
   @Test
   void iteratingPastEndThrows(@TempDir File tmpDir) throws IOException {
      File hf = new File(tmpDir, "psjunit-empty.test");

      TestPS stack = new TestPS();
      stack.newFile(hf);

      PersistantStack<Object>.PSIterator iter = stack.createIterator();
      iter.push(B2);
      iter.close();

      TestPS stack2 = new TestPS();
      stack2.setFile(hf);
      PersistantStack<Object>.PSIterator iter2 = stack2.createIterator();
      iter2.resetCache();

      iter2.next(); // consume only record
      assertThrows(java.util.NoSuchElementException.class, iter2::next,
         "iterating past end must throw NoSuchElementException");
      iter2.close();
   }
}
