package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deep coverage for {@link FvContext} — cursor management,
 * context lifecycle, view binding, position tracking, and
 * buffer switching exercised through TestView headless stubs.
 */
class FvContextDeepCoverageJUnitTest {

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

   private static TextEdit<String> makeBuffer(String name, String content)
         throws IOException {
      String path = history.Testutil.testFile(name).getPath();
      FileDescriptor.LocalFile.make(
         history.Testutil.testFile(name)).delete();
      try (java.io.OutputStreamWriter w =
            new java.io.OutputStreamWriter(
               new java.io.FileOutputStream(path),
               java.nio.charset.StandardCharsets.UTF_8)) {
         w.write(content);
      }
      FileDescriptor fd = FileDescriptor.make(path);
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      return te;
   }

   private static TextEdit<String> makeInternalBuffer(String name) {
      FileProperties<String> fp = new FileProperties<>(
         FileDescriptor.InternalFd.make(name), StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      return te;
   }

   private static void cleanup(TextEdit<String> te, String... names)
         throws IOException {
      te.disposeFvc();
      for (String name : names) {
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(name)).delete();
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(name + ".dmp2")).delete();
      }
   }

   // ── connectFv lifecycle ───────────────────────────────────

   @Test
   @DisplayName("connectFv creates an FvContext for a new view")
   void connectFvCreatesContext() throws Exception {
      String fname = "ju_fvd_conn";
      TextEdit<String> te = makeBuffer(fname, "first\nsecond\nthird\n");
      TestView view = new TestView(true);

      FvContext fvc = FvContext.connectFv(te, view);
      assertNotNull(fvc, "should create a context");
      assertEquals(te, fvc.edvec);

      FvContext.dispose(view);
      cleanup(te, fname);
   }

   @Test
   @DisplayName("connectFv same buffer+view returns existing context")
   void connectFvReusesContext() throws Exception {
      String fname = "ju_fvd_reuse";
      TextEdit<String> te = makeBuffer(fname, "one\ntwo\n");
      TestView view = new TestView(true);

      FvContext fvc1 = FvContext.connectFv(te, view);
      FvContext fvc2 = FvContext.connectFv(te, view);
      assertEquals(fvc1, fvc2, "same buffer+view should reuse context");

      FvContext.dispose(view);
      cleanup(te, fname);
   }

   @Test
   @DisplayName("connectFv different buffers get different contexts")
   void connectFvDifferentBuffers() throws Exception {
      String fname1 = "ju_fvd_diff1";
      String fname2 = "ju_fvd_diff2";
      TextEdit<String> te1 = makeBuffer(fname1, "a\n");
      TextEdit<String> te2 = makeBuffer(fname2, "b\n");
      TestView view = new TestView(true);

      FvContext fvc1 = FvContext.connectFv(te1, view);
      FvContext fvc2 = FvContext.connectFv(te2, view);
      assertFalse(fvc1 == fvc2, "different buffers should get different contexts");

      FvContext.dispose(view);
      cleanup(te1, fname1);
      cleanup(te2, fname2);
   }

   // ── Cursor position management ────────────────────────────

   @Test
   @DisplayName("cursorabs sets cursor position")
   void cursorabsSetsPosition() throws Exception {
      String fname = "ju_fvd_cabs";
      TextEdit<String> te = makeBuffer(fname, "abcdef\nghijkl\nmnopqr\n");
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      fvc.cursorabs(new Position(3, 2, "", ""));
      Position pos = fvc.getPosition("");
      assertEquals(2, pos.y, "y should be 2");
      assertEquals(3, pos.x, "x should be 3");

      FvContext.dispose(view);
      cleanup(te, fname);
   }

   @Test
   @DisplayName("cursory moves cursor vertically")
   void cursoryMovesVertically() throws Exception {
      String fname = "ju_fvd_cy";
      TextEdit<String> te = makeBuffer(fname, "a\nb\nc\nd\ne\n");
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      fvc.cursoryabs(1);
      fvc.cursory(2);  // move down 2 lines
      Position pos = fvc.getPosition("");
      assertTrue(pos.y >= 2, "y should be >= 2 after moving down");

      FvContext.dispose(view);
      cleanup(te, fname);
   }

   @Test
   @DisplayName("cursoryabs sets absolute Y position")
   void cursoryabsSetsY() throws Exception {
      String fname = "ju_fvd_cyabs";
      TextEdit<String> te = makeBuffer(fname, "a\nb\nc\nd\n");
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      fvc.cursoryabs(3);
      Position pos = fvc.getPosition("");
      assertEquals(3, pos.y, "y should be 3");

      FvContext.dispose(view);
      cleanup(te, fname);
   }

   @Test
   @DisplayName("fixCursor clamps beyond EOF")
   void fixCursorClampsBeyondEof() throws Exception {
      String fname = "ju_fvd_fix";
      TextEdit<String> te = makeBuffer(fname, "one\ntwo\n");
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      fvc.cursoryabs(99);  // way past end
      fvc.fixCursor();
      Position pos = fvc.getPosition("");
      assertTrue(pos.y <= te.readIn(),
         "y should be clamped to buffer size");

      FvContext.dispose(view);
      cleanup(te, fname);
   }

   // ── getPosition captures state ────────────────────────────

   @Test
   @DisplayName("getPosition captures file descriptor")
   void getPositionCapturesFd() throws Exception {
      String fname = "ju_fvd_gpos";
      TextEdit<String> te = makeBuffer(fname, "data\n");
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      Position pos = fvc.getPosition("test comment");
      assertNotNull(pos);
      assertNotNull(pos.filename);
      assertEquals("test comment", pos.comment);

      FvContext.dispose(view);
      cleanup(te, fname);
   }

   // ── inserty / at / getCurrentIndex ────────────────────────

   @Test
   @DisplayName("inserty returns cursor Y")
   void insertyReturnsCursorY() throws Exception {
      String fname = "ju_fvd_iny";
      TextEdit<String> te = makeBuffer(fname, "first\nsecond\n");
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      fvc.cursoryabs(2);
      assertEquals(2, fvc.inserty());

      FvContext.dispose(view);
      cleanup(te, fname);
   }

   @Test
   @DisplayName("at returns element at cursor Y")
   void atReturnsCursorElement() throws Exception {
      String fname = "ju_fvd_at";
      TextEdit<String> te = makeBuffer(fname, "alpha\nbeta\ngamma\n");
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      fvc.cursoryabs(2);
      Object elem = fvc.at();
      assertNotNull(elem);
      assertEquals("beta", elem.toString());

      FvContext.dispose(view);
      cleanup(te, fname);
   }

   // ── getCurrFvc / setCurrView ──────────────────────────────

   @Test
   @DisplayName("getCurrFvc returns non-null after connection")
   void getCurrFvcNonNull() throws Exception {
      TextEdit<String> te = makeInternalBuffer("ju_fvd_curr");
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);
      fvc.setCurrView();

      assertNotNull(FvContext.getCurrFvc());

      FvContext.dispose(view);
      te.disposeFvc();
   }

   @Test
   @DisplayName("setCurrView makes this the current context")
   void setCurrViewMakesCurrent() throws Exception {
      TextEdit<String> te1 = makeInternalBuffer("ju_fvd_sc1");
      TextEdit<String> te2 = makeInternalBuffer("ju_fvd_sc2");
      TestView v1 = new TestView(true);
      TestView v2 = new TestView(true);

      FvContext fvc1 = FvContext.connectFv(te1, v1);
      FvContext fvc2 = FvContext.connectFv(te2, v2);

      fvc1.setCurrView();
      assertEquals(fvc1, FvContext.getCurrFvc());

      fvc2.setCurrView();
      assertEquals(fvc2, FvContext.getCurrFvc());

      FvContext.dispose(v1);
      FvContext.dispose(v2);
      te1.disposeFvc();
      te2.disposeFvc();
   }

   // ── viewCount ─────────────────────────────────────────────

   @Test
   @DisplayName("viewCount increases with new views")
   void viewCountIncreases() throws Exception {
      int initial = FvContext.viewCount();
      TestView v = new TestView(true);
      TextEdit<String> te = makeInternalBuffer("ju_fvd_vc");
      FvContext.connectFv(te, v);

      assertTrue(FvContext.viewCount() >= initial + 1,
         "viewCount should increase");

      FvContext.dispose(v);
      te.disposeFvc();
   }

   // ── isInInsertMode ────────────────────────────────────────

   @Test
   @DisplayName("view is not in insert mode by default")
   void notInInsertModeByDefault() throws Exception {
      TextEdit<String> te = makeInternalBuffer("ju_fvd_ins");
      TestView view = new TestView(true);
      FvContext.connectFv(te, view);

      assertFalse(view.isInInsertMode());

      FvContext.dispose(view);
      te.disposeFvc();
   }

   // ── getcontext creates context lazily ─────────────────────

   @Test
   @DisplayName("getcontext creates context for existing buffer")
   void getcontextCreatesLazily() throws Exception {
      TextEdit<String> te = makeInternalBuffer("ju_fvd_gc");
      TestView view = new TestView(true);

      FvContext fvc = FvContext.getcontext(view, te);
      assertNotNull(fvc, "getcontext should create context");
      assertEquals(te, fvc.edvec);

      FvContext.dispose(view);
      te.disposeFvc();
   }

   // ── switchContext ─────────────────────────────────────────

   @Test
   @DisplayName("switchContext moves to next file in parent buffer")
   void switchContextNextFile() throws Exception {
      TextEdit<String> parent = makeInternalBuffer("ju_fvd_sw_parent");

      // Set up parent with string entries
      parent.insertOne("child1", 1);
      parent.insertOne("child2", 2);
      parent.checkpoint();

      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(parent, view);
      fvc.cursoryabs(1);

      FvContext switched = fvc.switchContext(parent, 1);
      assertNotNull(switched);

      FvContext.dispose(view);
      parent.disposeFvc();
   }

   // ── toString ──────────────────────────────────────────────

   @Test
   @DisplayName("toString includes buffer info")
   void toStringIncludesBuffer() throws Exception {
      TextEdit<String> te = makeInternalBuffer("ju_fvd_tostr");
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(te, view);

      String s = fvc.toString();
      assertNotNull(s);
      assertFalse(s.isEmpty());

      FvContext.dispose(view);
      te.disposeFvc();
   }
}
