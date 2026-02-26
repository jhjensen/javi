package javi;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for small value/utility classes in javi:
 * {@link Position}, {@link MovePos}, {@link BackupStatus},
 * {@link GrepFilter}, {@link GState}, and {@link StringIter}.
 *
 * <p>Position and GState require FileDescriptor / static state,
 * so we initialize via {@link TestInit#init()}.
 */
class PositionJUnitTest {

   @BeforeAll
   static void setUp() throws Exception {
      TestInit.init();
   }

   // ================================================================
   // Position tests
   // ================================================================

   @Nested
   @DisplayName("Position")
   class PositionTests {

      @Test
      @DisplayName("constructor with string filename")
      void constructorString() {
         Position p = new Position(5, 10, "test.txt", "comment");
         assertEquals(5, p.x);
         assertEquals(10, p.y);
         assertNotNull(p.filename);
         assertEquals("comment", p.comment);
      }

      @Test
      @DisplayName("constructor with FileDescriptor")
      void constructorFd() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("internal");
         Position p = new Position(3, 7, fd, "note");
         assertEquals(3, p.x);
         assertEquals(7, p.y);
         assertSame(fd, p.filename);
         assertEquals("note", p.comment);
      }

      @Test
      @DisplayName("constructor with null FileDescriptor throws NPE")
      void constructorNullFdThrows() {
         assertThrows(NullPointerException.class,
            () -> new Position(0, 0, (FileDescriptor) null, ""));
      }

      @Test
      @DisplayName("equals: same file, x, y are equal")
      void equalsIdentical() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("eq");
         Position a = new Position(1, 2, fd, "c1");
         Position b = new Position(1, 2, fd, "c2");
         assertEquals(a, b);
      }

      @Test
      @DisplayName("equals: different y not equal")
      void equalsDiffY() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("neq");
         Position a = new Position(1, 2, fd, "c");
         Position b = new Position(1, 3, fd, "c");
         assertNotEquals(a, b);
      }

      @Test
      @DisplayName("equals: different x not equal")
      void equalsDiffX() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("neqx");
         Position a = new Position(1, 2, fd, "c");
         Position b = new Position(5, 2, fd, "c");
         assertNotEquals(a, b);
      }

      @Test
      @DisplayName("equals: null returns false")
      void equalsNull() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("n");
         Position p = new Position(0, 1, fd, "");
         assertFalse(p.equals(null));
      }

      @Test
      @DisplayName("equals: same instance returns true")
      void equalsSelf() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("s");
         Position p = new Position(0, 1, fd, "");
         assertTrue(p.equals(p));
      }

      @Test
      @DisplayName("equals: non-Position returns false")
      void equalsNonPosition() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("np");
         Position p = new Position(0, 1, fd, "");
         assertFalse(p.equals("string"));
      }

      @Test
      @DisplayName("hashCode: equal positions have same hash")
      void hashCodeEqual() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("hc");
         Position a = new Position(3, 4, fd, "x");
         Position b = new Position(3, 4, fd, "y");
         assertEquals(a.hashCode(), b.hashCode());
      }

      @Test
      @DisplayName("toString: x=0 omits column")
      void toStringNoColumn() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("ts");
         Position p = new Position(0, 5, fd, "test");
         String str = p.toString();
         assertTrue(str.contains("(5)"), "line only: " + str);
         assertTrue(str.contains("test"), "comment: " + str);
      }

      @Test
      @DisplayName("toString: x>0 includes column and line")
      void toStringWithColumn() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("ts2");
         Position p = new Position(3, 5, fd, "note");
         String str = p.toString();
         assertTrue(str.contains("3"), "column: " + str);
         assertTrue(str.contains("5"), "line: " + str);
      }

      @Test
      @DisplayName("badpos is at origin with empty filename")
      void badpos() {
         assertEquals(0, Position.badpos.x);
         assertEquals(0, Position.badpos.y);
         assertNotNull(Position.badpos.filename);
      }

      @Test
      @DisplayName("getMovable returns MovePos with same coords")
      void getMovable() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("gm");
         Position p = new Position(7, 12, fd, "");
         MovePos mp = p.getMovable();
         assertEquals(7, mp.x);
         assertEquals(12, mp.y);
      }

      @Test
      @DisplayName("posMove sets MovePos to position coords")
      void posMove() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("pm");
         Position p = new Position(4, 8, fd, "");
         MovePos mp = new MovePos(0, 0);
         p.posMove(mp);
         assertEquals(4, mp.x);
         assertEquals(8, mp.y);
      }

      @Test
      @DisplayName("equiv returns true for matching MovePos")
      void equivTrue() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("ev");
         Position p = new Position(2, 3, fd, "");
         MovePos mp = new MovePos(2, 3);
         assertTrue(p.equiv(mp));
      }

      @Test
      @DisplayName("equiv returns false for non-matching MovePos")
      void equivFalse() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("evf");
         Position p = new Position(2, 3, fd, "");
         MovePos mp = new MovePos(2, 4);
         assertFalse(p.equiv(mp));
      }

      @Test
      @DisplayName("equiv returns false for null")
      void equivNull() {
         FileDescriptor fd = FileDescriptor.InternalFd.make("evn");
         Position p = new Position(0, 1, fd, "");
         assertFalse(p.equiv(null));
      }
   }

   // ================================================================
   // MovePos tests
   // ================================================================

   @Nested
   @DisplayName("MovePos")
   class MovePosTests {

      @Test
      @DisplayName("constructor sets x and y")
      void constructorBasic() {
         MovePos mp = new MovePos(10, 20);
         assertEquals(10, mp.x);
         assertEquals(20, mp.y);
      }

      @Test
      @DisplayName("copy constructor copies values")
      void copyConstructor() {
         MovePos orig = new MovePos(5, 15);
         MovePos copy = new MovePos(orig);
         assertEquals(5, copy.x);
         assertEquals(15, copy.y);
      }

      @Test
      @DisplayName("set updates x and y")
      void set() {
         MovePos mp = new MovePos(0, 0);
         mp.set(42, 99);
         assertEquals(42, mp.x);
         assertEquals(99, mp.y);
      }

      @Test
      @DisplayName("toString with x=0 omits column")
      void toStringNoColumn() {
         MovePos mp = new MovePos(0, 5);
         assertEquals("(5)", mp.toString());
      }

      @Test
      @DisplayName("toString with x>0 includes column and line")
      void toStringWithColumn() {
         MovePos mp = new MovePos(3, 7);
         assertEquals("(3,7)", mp.toString());
      }

      @Test
      @DisplayName("mutation does not affect original in copy")
      void copyIndependence() {
         MovePos orig = new MovePos(1, 2);
         MovePos copy = new MovePos(orig);
         copy.set(99, 88);
         assertEquals(1, orig.x);
         assertEquals(2, orig.y);
      }
   }

   // ================================================================
   // BackupStatus tests
   // ================================================================

   @Nested
   @DisplayName("BackupStatus")
   class BackupStatusTests {

      @Test
      @DisplayName("clean() true when all conditions met")
      void cleanTrue() {
         BackupStatus bs = new BackupStatus(true, true, null);
         assertTrue(bs.clean());
      }

      @Test
      @DisplayName("clean() false when not cleanQuit")
      void cleanFalseNotClean() {
         BackupStatus bs = new BackupStatus(false, true, null);
         assertFalse(bs.clean());
      }

      @Test
      @DisplayName("clean() false when not isQuitAtEnd")
      void cleanFalseNotQuit() {
         BackupStatus bs = new BackupStatus(true, false, null);
         assertFalse(bs.clean());
      }

      @Test
      @DisplayName("clean() false when error present")
      void cleanFalseWithError() {
         BackupStatus bs = new BackupStatus(true, true,
            new RuntimeException("oops"));
         assertFalse(bs.clean());
      }

      @Test
      @DisplayName("toString contains status fields")
      void toStringFormat() {
         BackupStatus bs = new BackupStatus(true, false, null);
         String str = bs.toString();
         assertTrue(str.contains("true"));
         assertTrue(str.contains("false"));
      }

      @Test
      @DisplayName("fields are accessible")
      void fieldsAccessible() {
         Throwable err = new Exception("test");
         BackupStatus bs = new BackupStatus(false, true, err);
         assertFalse(bs.cleanQuit);
         assertTrue(bs.isQuitAtEnd);
         assertSame(err, bs.error);
      }
   }

   // ================================================================
   // GrepFilter tests
   // ================================================================

   @Nested
   @DisplayName("GrepFilter")
   class GrepFilterTests {

      @Test
      @DisplayName("accept matches filename against regex")
      void acceptMatches() {
         GrepFilter gf = new GrepFilter(".*\\.java$", false);
         assertTrue(gf.accept(new File("."), "Main.java"));
      }

      @Test
      @DisplayName("accept rejects non-matching filename")
      void acceptRejects() {
         GrepFilter gf = new GrepFilter(".*\\.java$", false);
         assertFalse(gf.accept(new File("."), "readme.txt"));
      }

      @Test
      @DisplayName("inverted filter rejects matches")
      void invertedRejects() {
         GrepFilter gf = new GrepFilter(".*\\.class$", true);
         assertFalse(gf.accept(new File("."), "Main.class"));
      }

      @Test
      @DisplayName("inverted filter accepts non-matches")
      void invertedAccepts() {
         GrepFilter gf = new GrepFilter(".*\\.class$", true);
         assertTrue(gf.accept(new File("."), "Main.java"));
      }

      @Test
      @DisplayName("partial match accepted (find not matches)")
      void partialMatch() {
         GrepFilter gf = new GrepFilter("test", false);
         assertTrue(gf.accept(new File("."), "mytest123.txt"));
      }
   }

   // ================================================================
   // GState tests
   // ================================================================

   @Nested
   @DisplayName("GState")
   class GStateTests {

      @Test
      @DisplayName("empty regex matches any string")
      void emptyRegexMatchesAll() {
         GState.setRegex("", 0);
         assertTrue(GState.getRegex().reset("anything").find());
      }

      @Test
      @DisplayName("setRegex changes the search pattern")
      void setRegex() {
         GState.setRegex("hello", 0);
         assertTrue(GState.getRegex().reset("say hello world").find());
         assertFalse(GState.getRegex().reset("goodbye").find());
      }

      @Test
      @DisplayName("setRegex with CASE_INSENSITIVE flag")
      void setRegexCaseInsensitive() {
         GState.setRegex("hello",
            java.util.regex.Pattern.CASE_INSENSITIVE);
         assertTrue(GState.getRegex().reset("HELLO").find());
      }

      @Test
      @DisplayName("getRegex returns fresh Matcher each call")
      void getRegexFreshMatcher() {
         GState.setRegex("test", 0);
         var m1 = GState.getRegex();
         var m2 = GState.getRegex();
         // Both matchers work independently
         assertTrue(m1.reset("test").find());
         assertTrue(m2.reset("test case").find());
      }
   }

   // ================================================================
   // StringIter tests
   // ================================================================

   @Nested
   @DisplayName("StringIter")
   class StringIterTests {

      @Test
      @DisplayName("iterates over elements calling toString")
      void iteratesToString() {
         ArrayList<Object> list = new ArrayList<>();
         list.add(42);
         list.add("hello");
         list.add(3.14);
         StringIter si = new StringIter(list.iterator());
         assertTrue(si.hasNext());
         assertEquals("42", si.next());
         assertEquals("hello", si.next());
         assertEquals("3.14", si.next());
         assertFalse(si.hasNext());
      }

      @Test
      @DisplayName("remove throws UnsupportedOperationException")
      void removeThrows() {
         StringIter si = new StringIter(
            Arrays.asList("a").iterator());
         assertThrows(UnsupportedOperationException.class,
            si::remove);
      }

      @Test
      @DisplayName("empty iterator has no elements")
      void emptyIterator() {
         StringIter si = new StringIter(
            new ArrayList<>().iterator());
         assertFalse(si.hasNext());
      }
   }
}
