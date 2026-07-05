package javi;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended tests for {@link FvContext} — cursor manipulation,
 * display width, fold model, keymap, font override, and
 * character deletion.
 */
class FvContextExtendedJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
   }

   @AfterEach
   void cleanUp() throws IOException {
      EventQueue.biglock2.lock();
      try {
         for (String name : new String[]{
            "fvx_test1", "fvx_test2", "fvx_test3"}) {
            try {
               FileDescriptor.LocalFile.make(
                  history.Testutil.testFile(name)).delete();
            } catch (Exception ignore) {
            }
            try {
               FileDescriptor.LocalFile.make(
                  history.Testutil.testFile(name + ".dmp2")).delete();
            } catch (Exception ignore) {
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

   // ── displayWidth tests ───────────────────────────────────────

   @Test
   void displayWidthAscii() {
      assertEquals(5, FvContext.displayWidth("hello", 0, 5));
   }

   @Test
   void displayWidthEmptyString() {
      assertEquals(0, FvContext.displayWidth("", 0, 0));
   }

   @Test
   void displayWidthSubstring() {
      assertEquals(3, FvContext.displayWidth("hello", 1, 4));
   }

   @Test
   void displayWidthCjk() {
      // CJK ideographs are double-width
      String cjk = "\u4e2d\u6587"; // 中文
      int width = FvContext.displayWidth(cjk, 0, cjk.length());
      assertEquals(4, width,
         "two CJK characters should be 4 columns wide");
   }

   @Test
   void displayWidthMixed() {
      // "A中B" = 1 + 2 + 1 = 4
      String mixed = "A\u4e2dB";
      assertEquals(4, FvContext.displayWidth(mixed, 0, mixed.length()));
   }

   // ── isWideCodePoint tests ────────────────────────────────────

   @Test
   void isWideCodePointAscii() {
      assertFalse(FvContext.isWideCodePoint('A'));
      assertFalse(FvContext.isWideCodePoint(' '));
   }

   @Test
   void isWideCodePointCjk() {
      assertTrue(FvContext.isWideCodePoint(0x4E00)); // CJK start
      assertTrue(FvContext.isWideCodePoint(0x9FFF)); // CJK end
   }

   @Test
   void isWideCodePointCjkExtA() {
      assertTrue(FvContext.isWideCodePoint(0x3400));
      assertTrue(FvContext.isWideCodePoint(0x4DBF));
   }

   @Test
   void isWideCodePointFullwidth() {
      assertTrue(FvContext.isWideCodePoint(0xFF01)); // ！
      assertTrue(FvContext.isWideCodePoint(0xFF60));
   }

   @Test
   void isWideCodePointCompatibility() {
      assertTrue(FvContext.isWideCodePoint(0xF900));
      assertTrue(FvContext.isWideCodePoint(0xFAFF));
   }

   @Test
   void isWideCodePointSupplementary() {
      assertTrue(FvContext.isWideCodePoint(0x1F600)); // emoji
   }

   // ── keyMap tests ─────────────────────────────────────────────

   @Test
   void keyMapNullByDefault() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("km test\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         assertNull(fvc.getKeyMap(),
            "keyMap should be null by default");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void setKeyMapRoundTrip() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("km test\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         KeyMap km = new KeyMap("test-keymap", null, null);
         fvc.setKeyMap(km);
         assertSame(km, fvc.getKeyMap());

         fvc.setKeyMap(null);
         assertNull(fvc.getKeyMap());

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── fold model tests ─────────────────────────────────────────

   @Test
   void foldModelNullByDefault() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("fold test\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         assertNull(fvc.getFoldModel(),
            "foldModel should be null by default");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void setFoldModelRoundTrip() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("fold test\nline 2\nline 3\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         FoldModel fm = new FoldModel();
         fvc.setFoldModel(fm);
         assertSame(fm, fvc.getFoldModel());

         fvc.setFoldModel(null);
         assertNull(fvc.getFoldModel());

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── overrideFont tests ───────────────────────────────────────

   @Test
   void overrideFontNullByDefault() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("font test\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         assertNull(fvc.getOverrideFont(),
            "override font should be null by default");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void setOverrideFontRoundTrip() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("font test\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         Object sentinel = "mock-font";
         fvc.setOverrideFont(sentinel);
         assertSame(sentinel, fvc.getOverrideFont());

         fvc.setOverrideFont(null);
         assertNull(fvc.getOverrideFont());

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── findContext tests ────────────────────────────────────────

   @Test
   void findContextReturnsNullForUnknown() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("find test\n", 0, 1);
         te.checkpoint();

         TestView view1 = new TestView(true);
         TestView view2 = new TestView(true);
         FvContext.connectFv(te, view1);

         // view2 was never connected to te
         FvContext<?> result = FvContext.findContext(view2, te);
         assertNull(result,
            "findContext should return null for unconnected view");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void findContextReturnsConnected() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("find test\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         FvContext<?> result = FvContext.findContext(view, te);
         assertSame(fvc, result,
            "findContext should return the connected FvContext");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── cursor movement edge cases ───────────────────────────────

   @Test
   void cursoryabsClampsToRange() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("line 1\nline 2\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         // Move beyond end
         fvc.cursoryabs(999);
         assertTrue(fvc.inserty() <= te.readIn() - 1,
            "cursor Y should be clamped to file end");

         // Move before start
         fvc.cursoryabs(-5);
         assertEquals(1, fvc.inserty(),
            "cursor Y should be clamped to 1");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void cursorabsMovePosSetsXY() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("hello world\nsecond line\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         MovePos mp = new MovePos(3, 2);
         fvc.cursorabs(mp);
         assertEquals(2, fvc.inserty());
         assertEquals(3, fvc.insertx());

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void cursorxabsSnapsToGraphemeBoundary() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         // Surrogate pair: 𝐀 (U+1D400, Mathematical Bold A)
         String surr = "\uD835\uDC00 normal";
         te.inserttext(surr + "\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         // Position 1 is middle of surrogate pair — should snap
         fvc.cursorxabs(1);
         int x = fvc.insertx();
         // Should be 0 (snapped back) or 2 (full cluster)
         assertTrue(x == 0 || x == 2,
            "cursor should snap to grapheme boundary, got: " + x);

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── deleteChars tests ────────────────────────────────────────

   @Test
   void deleteCharsForward() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("abcdef\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         // Cursor at position 0, delete 2 chars forward
         fvc.deleteChars('a', true, true, 2);
         String after = te.at(1).toString();
         assertEquals("cdef", after,
            "2 forward deletes from start should leave 'cdef'");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void deleteCharsBackward() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("abcdef\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         // Move cursor to position 4
         fvc.cursorabs(4, 1);
         fvc.deleteChars('a', true, false, 2);
         String after = te.at(1).toString();
         assertEquals("abef", after,
            "2 backward deletes from pos 4 should leave 'abef'");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void deleteCharsForwardSingleChar() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("xyz\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         fvc.deleteChars('a', true, true, 1);
         assertEquals("yz", te.at(1).toString());

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── insertStrings tests ──────────────────────────────────────

   @Test
   @SuppressWarnings({ "unchecked", "rawtypes" })
   void insertStringsAfterCursor() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("line 1\nline 3\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         // Cursor at line 1, insert after
         java.util.ArrayList<String> lines = new java.util.ArrayList<>();
         lines.add("line 2");
         fvc.insertStrings(lines, true);

         assertEquals("line 2", te.at(2).toString(),
            "inserted line should appear at line 2");
         assertEquals("line 3", te.at(3).toString(),
            "original line 2 should shift to line 3");

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   @SuppressWarnings({ "unchecked", "rawtypes" })
   void insertStringsBeforeCursor() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("line 2\nline 3\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         java.util.ArrayList<String> lines = new java.util.ArrayList<>();
         lines.add("line 1");
         fvc.insertStrings(lines, false);

         assertEquals("line 1", te.at(1).toString());
         assertEquals("line 2", te.at(2).toString());

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── changeElement tests ──────────────────────────────────────

   @Test
   void changeElementModifiesCurrentLine() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("original\nsecond\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         fvc.changeElementStr("modified");
         assertEquals("modified", te.at(1).toString());
         assertEquals("second", te.at(2).toString());

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── getElementsAt tests ──────────────────────────────────────

   @Test
   void getElementsAtReturnsCursorLines() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("a\nb\nc\nd\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         @SuppressWarnings({ "unchecked", "rawtypes" })
         java.util.ArrayList<String> result =
            fvc.getElementsAt(2);
         assertNotNull(result);
         assertEquals(2, result.size());
         assertEquals("a", result.get(0));
         assertEquals("b", result.get(1));

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── wrapInc via switchContext ─────────────────────────────────

   @Test
   void getPositionCapturesCurrentState() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = openTestFile("fvx_test1");
         te.inserttext("pos test\nline 2\n", 0, 1);
         te.checkpoint();

         TestView view = new TestView(true);
         FvContext fvc = FvContext.connectFv(te, view);

         fvc.cursoryabs(2);
         fvc.cursorabs(3, 2);
         Position pos = fvc.getPosition("snapshot");
         assertEquals(3, pos.x);
         assertEquals(2, pos.y);
         assertEquals("snapshot", pos.comment);

         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
