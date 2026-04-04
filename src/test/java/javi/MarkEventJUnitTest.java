package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MarkEvent} — construction and field storage.
 *
 * <p>{@code execute()} enters interactive markmode (blocks on
 * {@code EventQueue.nextKeye}), so only construction is tested.</p>
 */
class MarkEventJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   @Test
   void constructionStoresPosition() {
      Position pos = new Position(5, 10, "testfile", "test tag");
      MarkEvent me = new MarkEvent(pos);
      assertNotNull(me, "MarkEvent should be constructable");
   }

   @Test
   void isIEvent() {
      Position pos = new Position(0, 1, "f", "t");
      MarkEvent me = new MarkEvent(pos);
      assertNotNull(me,
         "MarkEvent is an IEvent subclass");
      assertTrue(me instanceof EventQueue.IEvent);
   }
}
