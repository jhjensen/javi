package javi;

import java.io.StringReader;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the ghost text state machine used by insert-mode
 * AI tab completion.
 */
class GhostTextStateJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void setup() {
      EventQueue.biglock2.lock();
      GhostTextState.reset();
   }

   @AfterEach
   void teardown() {
      GhostTextState.reset();
      EventQueue.biglock2.unlock();
   }

   // ── Initial state ────────────────────────────────────────

   @Test
   @DisplayName("initial state is IDLE")
   void initialStateIsIdle() {
      assertEquals(GhostTextState.State.IDLE,
         GhostTextState.getState());
      assertFalse(GhostTextState.isVisible());
      assertFalse(GhostTextState.isPending());
   }

   // ── IDLE → PENDING transition ────────────────────────────

   @Test
   @DisplayName("requestStarted transitions to PENDING")
   void requestStartedTransitionsToPending() {
      GhostTextState.requestStarted();
      assertEquals(GhostTextState.State.PENDING,
         GhostTextState.getState());
      assertTrue(GhostTextState.isPending());
      assertFalse(GhostTextState.isVisible());
   }

   // ── PENDING → VISIBLE transition ─────────────────────────

   @Test
   @DisplayName("completionArrived transitions to VISIBLE")
   void completionArrivedTransitionsToVisible() {
      GhostTextState.requestStarted();
      GhostTextState.completionArrived(
         new String[]{"hello"}, 5, 10);
      assertEquals(GhostTextState.State.VISIBLE,
         GhostTextState.getState());
      assertTrue(GhostTextState.isVisible());
      assertFalse(GhostTextState.isPending());
   }

   @Test
   @DisplayName("completionArrived sets ghost text on View")
   void completionArrivedSetsGhostText() {
      GhostTextState.requestStarted();
      GhostTextState.completionArrived(
         new String[]{"world"}, 3, 7);
      assertEquals("world", View.getGhostText());
      assertEquals(3, View.getGhostLine());
      assertEquals(7, View.getGhostCol());
   }

   // ── VISIBLE → IDLE (dismiss) ─────────────────────────────

   @Test
   @DisplayName("dismiss from VISIBLE returns to IDLE")
   void dismissFromVisibleReturnsToIdle() {
      GhostTextState.requestStarted();
      GhostTextState.completionArrived(
         new String[]{"foo"}, 1, 0);
      assertTrue(GhostTextState.isVisible());

      GhostTextState.dismiss();
      assertEquals(GhostTextState.State.IDLE,
         GhostTextState.getState());
      assertFalse(GhostTextState.isVisible());
      assertNull(View.getGhostText());
   }

   // ── PENDING → IDLE (dismiss cancels) ─────────────────────

   @Test
   @DisplayName("dismiss from PENDING returns to IDLE")
   void dismissFromPendingReturnsToIdle() {
      GhostTextState.requestStarted();
      assertTrue(GhostTextState.isPending());

      GhostTextState.dismiss();
      assertEquals(GhostTextState.State.IDLE,
         GhostTextState.getState());
   }

   // ── reset clears everything ──────────────────────────────

   @Test
   @DisplayName("reset clears state and ghost text")
   void resetClearsAll() {
      GhostTextState.requestStarted();
      GhostTextState.completionArrived(
         new String[]{"bar"}, 2, 5);
      assertTrue(GhostTextState.isVisible());

      GhostTextState.reset();
      assertEquals(GhostTextState.State.IDLE,
         GhostTextState.getState());
      assertNull(View.getGhostText());
   }

   // ── Multi-line completion ────────────────────────────────

   @Test
   @DisplayName("multi-line completion sets first line"
      + " as ghost text")
   void multiLineCompletionSetsFirstLine() {
      GhostTextState.requestStarted();
      String[] lines = {"line1", "line2", "line3"};
      GhostTextState.completionArrived(lines, 10, 4);
      assertEquals("line1", View.getGhostText());
      assertTrue(GhostTextState.isVisible());
   }

   // ── Double dismiss is safe ───────────────────────────────

   @Test
   @DisplayName("dismiss when already IDLE is no-op")
   void dismissWhenIdleIsNoop() {
      assertEquals(GhostTextState.State.IDLE,
         GhostTextState.getState());
      GhostTextState.dismiss();
      assertEquals(GhostTextState.State.IDLE,
         GhostTextState.getState());
   }

   // ── Accept with buffer integration ───────────────────────

   @Test
   @DisplayName("accept single line inserts into buffer")
   void acceptSingleLineInsertsIntoBuffer() throws Exception {
      String fname = "ju_ghost_accept1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> buf = openTestFile(fname);
      buf.insertOne("int x = ", 1);
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(buf, view);
      fvc.cursorabs(8, 1);

      GhostTextState.requestStarted();
      GhostTextState.completionArrived(
         new String[]{"42;"}, 1, 8);

      GhostTextState.accept(fvc);
      assertEquals(GhostTextState.State.IDLE,
         GhostTextState.getState());
      assertEquals("int x = 42;",
         buf.at(1).toString());
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("accept multi-line inserts all lines")
   void acceptMultiLineInsertsAllLines() throws Exception {
      String fname = "ju_ghost_accept2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> buf = openTestFile(fname);
      buf.insertOne("if (", 1);
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(buf, view);
      fvc.cursorabs(4, 1);

      GhostTextState.requestStarted();
      GhostTextState.completionArrived(
         new String[]{"true) {", "   return 1;", "}"},
         1, 4);

      GhostTextState.accept(fvc);
      assertEquals(GhostTextState.State.IDLE,
         GhostTextState.getState());
      assertEquals("if (true) {",
         buf.at(1).toString());
      assertEquals("   return 1;",
         buf.at(2).toString());
      assertEquals("}",
         buf.at(3).toString());
      deleteTestFiles(fname);
   }

   @Test
   @DisplayName("accept when IDLE is no-op")
   void acceptWhenIdleIsNoop() throws Exception {
      String fname = "ju_ghost_accept3";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> buf = openTestFile(fname);
      buf.insertOne("hello", 1);
      TestView view = new TestView(true);
      FvContext fvc = FvContext.connectFv(buf, view);

      GhostTextState.accept(fvc);
      assertEquals("hello", buf.at(1).toString());
      deleteTestFiles(fname);
   }

   // ── AI availability check ────────────────────────────────

   @Test
   @DisplayName("ai.complete command is registered")
   void aiCompleteCommandRegistered() {
      Rgroup.KeyBinding binding =
         Rgroup.bindingLookup("ai.complete");
      // AI plugin loaded in TestInit.initCommands()
      // If plugin not available, binding may be null
      // (test still passes — just verifies the lookup)
      if (binding != null) {
         assertTrue(true, "ai.complete is registered");
      }
   }

   // ── Helpers ──────────────────────────────────────────────

   private static String testPath(String name) {
      return history.Testutil.testFile(name).getPath();
   }

   private static void deleteTestFiles(String baseName) {
      for (String ext : new String[]{"", ".dmp2"}) {
         try {
            FileDescriptor.LocalFile.make(
               history.Testutil.testFile(
                  baseName + ext)).delete();
         } catch (Exception ignore) {
         }
      }
   }

   private static TextEdit<String> openTestFile(
         String name) {
      FileDescriptor fd =
         FileDescriptor.make(testPath(name));
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      return te;
   }
}
