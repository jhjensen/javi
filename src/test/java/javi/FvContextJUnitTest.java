package javi;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FvContext} lifecycle management.
 *
 * <p>Uses a minimal {@link TestView} stub so that FvContext instances
 * can be created and managed without an actual AWT display.  All
 * calls are made under {@code biglock2} because FvContext's internal
 * {@link FvContext.FvMap} asserts lock ownership.
 */
class FvContextJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
   }

   /** Clean up any test files created during each test. */
   @AfterEach
   void cleanUp() throws IOException {
      EventQueue.biglock2.lock();
      try {
         for (String name : new String[]{
               "fvc_test1", "fvc_test2", "fvc_test3"}) {
            try {
               FileDescriptor.LocalFile.make(
                  history.Testutil.testFile(name)).delete();
            } catch (Exception ignore) {
               // file may not have been created
            }
            try {
               FileDescriptor.LocalFile.make(
                  history.Testutil.testFile(name + ".dmp2")).delete();
            } catch (Exception ignore) {
               // dmp2 may not exist
            }
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Helpers ──────────────────────────────────────────────────

   private static TextEdit<String> openTestFile(String name) {
      String path = history.Testutil.testFile(name).getPath();
      FileDescriptor fd = FileDescriptor.make(path);
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      return te;
   }

   // ── Tests ────────────────────────────────────────────────────

   /**
    * Create an FvContext by binding a TextEdit to a TestView,
    * then verify it becomes the current context.
    */
   @Test
   void connectFvSetsCurrFvc() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvc_test1");
         te.inserttext("line one\nline two\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         assertNotNull(fvc, "connectedFv should return non-null");
         assertSame(fvc, FvContext.getCurrFvc(),
            "connected context should be the current one");
         assertSame(te, fvc.edvec,
            "context should reference the TextEdit");
         assertSame(view, fvc.vi,
            "context should reference the View");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Switching contexts: connect two different files to the same
    * view, verify the current context switches.
    */
   @Test
   void switchFileOnSameView() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te1 = openTestFile("fvc_test1");
         te1.inserttext("file one\n", 0, 1);
         te1.checkpoint();

         TextEdit<String> te2 = openTestFile("fvc_test2");
         te2.inserttext("file two\n", 0, 1);
         te2.checkpoint();

         TestView view = new TestView(true);

         // Connect first file
         FvContext fvc1 = FvContext.connectFv(te1, view);
         assertSame(te1, FvContext.getCurrFvc().edvec);

         // Switch to second file on same view
         FvContext fvc2 = FvContext.connectFv(te2, view);
         assertSame(te2, FvContext.getCurrFvc().edvec);
         assertSame(fvc2, FvContext.getCurrFvc());

         // Switch back to first file
         FvContext fvc1b = FvContext.connectFv(te1, view);
         assertSame(te1, FvContext.getCurrFvc().edvec);

         te1.disposeFvc();
         te2.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Verify cursor position accessors after connecting.
    */
   @Test
   void initialCursorAfterConnect() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvc_test1");
         te.inserttext("hello world\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         assertEquals(1, fvc.inserty(),
            "initial cursor Y should be 1");
         assertEquals(0, fvc.insertx(),
            "initial cursor X should be 0");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Verify getCurrState returns a non-empty status string.
    */
   @Test
   void getCurrStateReturnsStatus() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvc_test1");
         te.inserttext("status test\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext.connectFv(te, view);

         String state = FvContext.getCurrState();
         assertNotNull(state, "state should not be null");
         assertTrue(state.contains("1,1"),
            "state should contain cursor position '1,1', got: " + state);

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Verify that getcontext returns the same FvContext for the same
    * (View, TextEdit) pair.
    */
   @Test
   void getcontextReturnsSameInstance() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvc_test1");
         te.inserttext("context test\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc1 = FvContext.getcontext(view, te);
         FvContext fvc2 = FvContext.getcontext(view, te);
         assertSame(fvc1, fvc2,
            "same (view, textedit) should yield same FvContext");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Different views bound to the same TextEdit produce different
    * FvContext instances.
    */
   @Test
   void differentViewsSameFileDifferentContexts() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvc_test1");
         te.inserttext("multi-view\n", 0, 1);
         te.checkpoint();

         TestView view1 = new TestView(true);
         TestView view2 = new TestView(true);
         FvContext fvc1 = FvContext.getcontext(view1, te);
         FvContext fvc2 = FvContext.getcontext(view2, te);
         assertTrue(fvc1 != fvc2,
            "different views should produce different FvContext");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Verify viewCount changes as views are added.
    */
   @Test
   void viewCountReflectsActiveViews() throws Exception {
      EventQueue.biglock2.lock();
      try {
         int initialCount = FvContext.viewCount();
         TextEdit<String> te = openTestFile("fvc_test1");
         te.inserttext("view test\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext.connectFv(te, view);
         assertTrue(FvContext.viewCount() >= initialCount + 1,
            "viewCount should increase after connect");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Test the at() method on FvContext returns current line content.
    */
   @Test
   void atReturnsCurrentLineContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvc_test1");
         te.inserttext("first line\nsecond line\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         // Cursor is at line 1 initially
         Object line = fvc.at();
         assertNotNull(line);
         assertEquals("first line", line.toString());

         // Access by index
         assertEquals("second line", fvc.at(2).toString());

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Test getPosition returns valid position data.
    */
   @Test
   void getPositionReturnsValidPosition() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvc_test1");
         te.inserttext("pos test\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         Position pos = fvc.getPosition("test position");
         assertNotNull(pos);
         assertEquals(1, pos.y);
         assertEquals(0, pos.x);
         assertEquals("test position", pos.comment);

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Test equiv() checks position equivalence with FvContext.
    */
   @Test
   void equivMatchesCurrentPosition() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvc_test1");
         te.inserttext("equiv test\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         Position pos = fvc.getPosition("test");
         assertTrue(fvc.equiv(pos),
            "equiv should match the position just retrieved");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Test that toString() produces a readable representation.
    */
   @Test
   void toStringContainsCursorInfo() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvc_test1");
         te.inserttext("str test\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         String repr = fvc.toString();
         assertNotNull(repr);
         assertTrue(repr.contains("("),
            "toString should contain cursor coords: " + repr);

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Test getCurrentIndex() returns the cursor line number.
    */
   @Test
   void getCurrentIndexReturnsCursorLine() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvc_test1");
         te.inserttext("idx test\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         assertEquals(1, fvc.getCurrentIndex());

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
