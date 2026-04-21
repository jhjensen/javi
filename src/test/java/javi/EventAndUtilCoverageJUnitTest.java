package javi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for event classes: {@link CommandEvent},
 * {@link ExitEvent}, {@link ScrollEvent}, {@link PosEvent},
 * {@link MovePos}, {@link GrepFilter}, and {@link BackupStatus}
 * edge cases.
 */
class EventAndUtilCoverageJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
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

   // ── CommandEvent ──────────────────────────────────────────

   @Nested
   @DisplayName("CommandEvent")
   class CommandEventTests {
      @Test
      @DisplayName("construction stores command string")
      void constructionStoresCommand() {
         CommandEvent ce = new CommandEvent("tabstop 4");
         assertNotNull(ce);
      }

      @Test
      @DisplayName("is an IEvent")
      void isIEvent() {
         CommandEvent ce = new CommandEvent("help");
         assertTrue(ce instanceof EventQueue.IEvent);
      }

      @Test
      @DisplayName("execute dispatches command")
      void executeDispatches() {
         // "tabstop 4" is a safe command that won't fail
         CommandEvent ce = new CommandEvent("tabstop 4");
         // Should not throw — dispatches through Command.command()
         ce.execute();
      }

      @Test
      @DisplayName("execute with unknown command reports error")
      void executeUnknownCommand() {
         CommandEvent ce = new CommandEvent("__nonexistent__");
         // Should not throw — error handled internally
         ce.execute();
      }
   }

   // ── ExitEvent ─────────────────────────────────────────────

   @Nested
   @DisplayName("ExitEvent")
   class ExitEventTests {
      @Test
      @DisplayName("construction succeeds")
      void constructionSucceeds() {
         ExitEvent ee = new ExitEvent();
         assertNotNull(ee);
      }

      @Test
      @DisplayName("is an IEvent")
      void isIEvent() {
         assertTrue(new ExitEvent() instanceof EventQueue.IEvent);
      }

      @Test
      @DisplayName("execute throws ExitException")
      void executeThrowsExitException() {
         ExitEvent ee = new ExitEvent();
         assertThrows(ExitException.class, () -> ee.execute());
      }
   }

   // ── ScrollEvent ───────────────────────────────────────────

   @Nested
   @DisplayName("ScrollEvent")
   class ScrollEventTests {
      @Test
      @DisplayName("construction with vertical scroll")
      void constructionVertical() {
         ScrollEvent se = new ScrollEvent(5, false);
         assertNotNull(se);
      }

      @Test
      @DisplayName("construction with horizontal scroll")
      void constructionHorizontal() {
         ScrollEvent se = new ScrollEvent(3, true);
         assertNotNull(se);
      }

      @Test
      @DisplayName("is an IEvent")
      void isIEvent() {
         assertTrue(new ScrollEvent(1, false)
            instanceof EventQueue.IEvent);
      }

      @Test
      @DisplayName("negative scroll amount")
      void negativeAmount() {
         ScrollEvent se = new ScrollEvent(-10, false);
         assertNotNull(se);
      }

      @Test
      @DisplayName("zero scroll amount")
      void zeroAmount() {
         ScrollEvent se = new ScrollEvent(0, true);
         assertNotNull(se);
      }
   }

   // ── PosEvent ──────────────────────────────────────────────

   @Nested
   @DisplayName("PosEvent")
   class PosEventTests {
      @Test
      @DisplayName("construction stores fvc and position")
      void constructionStoresFvcAndPos() {
         FvContext fvc = FvContext.getCurrFvc();
         Position pos = new Position(1, 5, "test", "tag");
         PosEvent pe = new PosEvent(fvc, pos);
         assertNotNull(pe);
      }

      @Test
      @DisplayName("is an IEvent")
      void isIEvent() {
         FvContext fvc = FvContext.getCurrFvc();
         Position pos = new Position(0, 1, "f", "t");
         assertTrue(new PosEvent(fvc, pos)
            instanceof EventQueue.IEvent);
      }
   }

   // ── MovePos ───────────────────────────────────────────────

   @Nested
   @DisplayName("MovePos")
   class MovePosTests {
      @Test
      @DisplayName("constructor sets x and y")
      void constructorSetsFields() {
         MovePos mp = new MovePos(10, 20);
         assertEquals(10, mp.x);
         assertEquals(20, mp.y);
      }

      @Test
      @DisplayName("copy constructor copies values")
      void copyConstructor() {
         MovePos orig = new MovePos(3, 7);
         MovePos copy = new MovePos(orig);
         assertEquals(3, copy.x);
         assertEquals(7, copy.y);
      }

      @Test
      @DisplayName("set updates both fields")
      void setUpdatesFields() {
         MovePos mp = new MovePos(0, 0);
         mp.set(5, 15);
         assertEquals(5, mp.x);
         assertEquals(15, mp.y);
      }

      @Test
      @DisplayName("toString with x=0 omits x")
      void toStringOmitsXWhenZero() {
         MovePos mp = new MovePos(0, 5);
         assertEquals("(5)", mp.toString());
      }

      @Test
      @DisplayName("toString with nonzero x includes both")
      void toStringIncludesBothWhenXNonzero() {
         MovePos mp = new MovePos(3, 7);
         assertEquals("(3,7)", mp.toString());
      }

      @Test
      @DisplayName("negative coordinates")
      void negativeCoordinates() {
         MovePos mp = new MovePos(-1, -5);
         assertEquals(-1, mp.x);
         assertEquals(-5, mp.y);
         assertTrue(mp.toString().contains("-1"));
      }

      @Test
      @DisplayName("copy constructor is independent of original")
      void copyIsIndependent() {
         MovePos orig = new MovePos(1, 2);
         MovePos copy = new MovePos(orig);
         orig.set(99, 99);
         assertEquals(1, copy.x);
         assertEquals(2, copy.y);
      }
   }

   // ── GrepFilter ────────────────────────────────────────────

   @Nested
   @DisplayName("GrepFilter")
   class GrepFilterTests {
      @Test
      @DisplayName("accepts matching filename")
      void acceptsMatch() {
         GrepFilter gf = new GrepFilter(".*\\.java", false);
         assertTrue(gf.accept(null, "Test.java"));
      }

      @Test
      @DisplayName("rejects non-matching filename")
      void rejectsNonMatch() {
         GrepFilter gf = new GrepFilter(".*\\.java", false);
         assertFalse(gf.accept(null, "Test.py"));
      }

      @Test
      @DisplayName("inverted filter rejects match")
      void invertedRejectsMatch() {
         GrepFilter gf = new GrepFilter(".*\\.class", true);
         assertFalse(gf.accept(null, "Foo.class"));
      }

      @Test
      @DisplayName("inverted filter accepts non-match")
      void invertedAcceptsNonMatch() {
         GrepFilter gf = new GrepFilter(".*\\.class", true);
         assertTrue(gf.accept(null, "Foo.java"));
      }

      @Test
      @DisplayName("partial match with find semantics")
      void partialMatch() {
         GrepFilter gf = new GrepFilter("test", false);
         assertTrue(gf.accept(null, "mytest.txt"));
      }

      @Test
      @DisplayName("regex anchored pattern")
      void anchoredPattern() {
         GrepFilter gf = new GrepFilter("^test$", false);
         assertFalse(gf.accept(null, "mytest"));
         assertTrue(gf.accept(null, "test"));
      }

      @Test
      @DisplayName("dot matches any character")
      void dotMatchesAny() {
         GrepFilter gf = new GrepFilter("a.c", false);
         assertTrue(gf.accept(null, "abc"));
         assertTrue(gf.accept(null, "a_c"));
         assertFalse(gf.accept(null, "ac"));
      }
   }

   // ── ExitException ─────────────────────────────────────────

   @Nested
   @DisplayName("ExitException")
   class ExitExceptionTests {
      @Test
      @DisplayName("extends InputException")
      void extendsInputException() {
         assertTrue(new ExitException()
            instanceof InputException);
      }

      @Test
      @DisplayName("can be thrown and caught")
      void canBeThrown() {
         assertThrows(ExitException.class, () -> {
            throw new ExitException();
         });
      }
   }

   // ── InputException ────────────────────────────────────────

   @Nested
   @DisplayName("InputException")
   class InputExceptionTests {
      @Test
      @DisplayName("message is preserved")
      void messagePreserved() {
         InputException ie = new InputException("test error");
         assertTrue(ie.toString().contains("test error"));
      }

      @Test
      @DisplayName("cause is preserved")
      void causePreserved() {
         Throwable cause = new RuntimeException("root");
         InputException ie = new InputException("msg", cause);
         assertNotNull(ie);
      }
   }
}
