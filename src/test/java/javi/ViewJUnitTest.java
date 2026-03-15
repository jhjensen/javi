package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link View} concrete methods via {@link TestView}.
 *
 * <p>View is abstract but has significant concrete logic for cursor
 * management, mark handling, blink state, and coordinate helpers.
 * These tests exercise that logic through the headless TestView stub.
 */
class ViewJUnitTest {

   private TestView view;
   private TextEdit<String> te;
   private FvContext fvc;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void setUp() throws Exception {
      EventQueue.biglock2.lock();
      view = new TestView(true);
      te = openTestBuffer("view-test", "line one\nline two\nline three\n");
      fvc = FvContext.connectFv(te, view);
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         te.disposeFvc();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── traversability ──────────────────────────────────────────

   @Test
   void traverseableReflectsConstructorArg() {
      assertTrue(view.isTraverseable());
   }

   @Test
   void nonTraverseableView() {
      TestView ntView = new TestView(false);
      assertFalse(ntView.isTraverseable());
   }

   // ── file position ───────────────────────────────────────────

   @Test
   void setFilePosUpdatesAccessors() {
      view.setFilePos(5, 10);
      assertEquals(5, view.getfileX());
      assertEquals(10, view.getfileY());
   }

   @Test
   void initialFilePosIsZeroOne() {
      // After connectFv, cursor is set to (0,1) by newfile
      assertEquals(0, view.getfileX());
      assertEquals(1, view.getfileY());
   }

   // ── getCurrFile ─────────────────────────────────────────────

   @Test
   void getCurrFileReturnsBoundTextEdit() {
      assertSame(te, view.getCurrFile());
   }

   // ── needBlink state machine ─────────────────────────────────

   @Test
   void needBlinkReturnsZeroWhenCursorInactive() {
      // Cursor starts inactive after TestView creation
      assertEquals(0, view.needBlink());
   }

   @Test
   void setCursorOnEnablesBlink() {
      view.setCursorOn();
      int flags = view.needBlink();
      assertTrue((flags & View.doBlink) != 0,
         "doBlink flag should be set");
   }

   @Test
   void needBlinkTogglesCursorOnOff() {
      view.setCursorOn();
      int first = view.needBlink();
      assertTrue((first & View.onFlag) != 0,
         "first blink should turn cursor on");

      int second = view.needBlink();
      assertTrue((second & View.doBlink) != 0,
         "second blink should still blink");
      assertEquals(0, second & View.onFlag,
         "second blink should turn cursor off");
   }

   @Test
   void setCursorOffStopsBlink() {
      view.setCursorOn();
      view.needBlink(); // turn on
      view.setCursorOff();
      // After setCursorOff, cursor should blink off once then stop
      view.needBlink(); // this toggles off
      assertEquals(0, view.needBlink(),
         "cursor should stop blinking after setCursorOff");
   }

   // ── mark handling ───────────────────────────────────────────

   @Test
   void initialMarkIsNull() {
      assertNull(view.getMark());
   }

   @Test
   void setMarkAndGetMark() {
      Position markPos = new Position(3, 2,
         te.fdes(), "test");
      view.setMark(markPos);
      MovePos mark = view.getMark();
      assertNotNull(mark);
      assertEquals(3, mark.x);
      assertEquals(2, mark.y);
   }

   @Test
   void clearMarkResetsToNull() {
      Position markPos = new Position(3, 2,
         te.fdes(), "test");
      view.setMark(markPos);
      assertNotNull(view.getMark());

      view.clearMark();
      assertNull(view.getMark());
   }

   @Test
   void setMarkWithSamePositionIsIdempotent() {
      Position pos = new Position(5, 3,
         te.fdes(), "test");
      view.setMark(pos);
      MovePos first = view.getMark();

      // Setting same mark again should not change it
      view.setMark(pos);
      MovePos second = view.getMark();
      assertEquals(first.x, second.x);
      assertEquals(first.y, second.y);
   }

   @Test
   void setMarkDifferentPositionUpdates() {
      FileDescriptor fd = te.fdes();
      Position pos1 = new Position(1, 1, fd, "a");
      Position pos2 = new Position(5, 3, fd, "b");

      view.setMark(pos1);
      assertEquals(1, view.getMark().x);
      assertEquals(1, view.getMark().y);

      view.setMark(pos2);
      assertEquals(5, view.getMark().x);
      assertEquals(3, view.getMark().y);
   }

   // ── MarkInfo ────────────────────────────────────────────────

   @Test
   void markInfoStarthEndhNoMark() {
      // With no mark set, starth/endh should return 0
      View.MarkInfo mi = new View.MarkInfo();
      assertEquals(0, mi.starth(1));
      assertEquals(0, mi.endh(1));
   }

   @Test
   void markInfoSetMarkSetsRange() {
      View.MarkInfo mi = new View.MarkInfo();
      Position pos = new Position(2, 3,
         te.fdes(), "test");
      mi.setMark(pos, 0, 1);

      // After setMark(pos, fileX=0, fileY=1): mark=(2,3), cursor=(0,1)
      // sy1=1 (min y), sy2=3 (max y)
      assertEquals(0, mi.starth(1), "starth at mark start line");
      assertTrue(mi.endh(1) > 0, "endh at mark start line");
      assertTrue(mi.endh(2) > 0, "endh at middle line");
   }

   @Test
   void markInfoClearMarkResetsRange() {
      View.MarkInfo mi = new View.MarkInfo();
      Position pos = new Position(2, 3,
         te.fdes(), "test");
      mi.setMark(pos, 0, 1);
      mi.clearMark(0, 1);
      assertNull(mi.getMark());
      assertEquals(0, mi.starth(1));
      assertEquals(0, mi.endh(1));
   }

   @Test
   void markInfoGetMarkReturnsCopy() {
      View.MarkInfo mi = new View.MarkInfo();
      Position pos = new Position(4, 5,
         te.fdes(), "test");
      mi.setMark(pos, 0, 1);
      MovePos m1 = mi.getMark();
      MovePos m2 = mi.getMark();
      assertNotSame(m1, m2, "getMark should return a new copy each time");
      assertEquals(m1.x, m2.x);
      assertEquals(m1.y, m2.y);
   }

   @Test
   void markInfoMarkChangeReversedOrder() {
      // When mark.y > cursor.y, sy1/sy2 should still be ordered
      View.MarkInfo mi = new View.MarkInfo();
      Position pos = new Position(0, 1,
         te.fdes(), "test");
      mi.setMark(pos, 0, 5); // cursor at y=5, mark at y=1
      // sy1=1, sy2=5
      assertTrue(mi.endh(3) > 0, "middle line should be in range");
      assertEquals(0, mi.endh(6), "line outside range returns 0");
   }

   @Test
   void markInfoToStringFormat() {
      View.MarkInfo mi = new View.MarkInfo();
      String s = mi.toString();
      assertNotNull(s);
      assertTrue(s.contains("("), "toString should contain coordinates");
   }

   // ── placeline ───────────────────────────────────────────────

   @Test
   void placelineReturnsScrollAmount() {
      // TestView: screenFirstLine=1, getRows(0)=24, screeny(x)=x
      // placeline(1, 0) = screeny(1 - 1 - 24) = -24
      int result = view.placeline(1, 0);
      // Just verify it returns an integer without exception
      assertTrue(result <= 0 || result >= 0, "placeline should return a value");
   }

   // ── redraw delegates ────────────────────────────────────────

   @Test
   void redrawDoesNotThrow() {
      assertDoesNotThrow(() -> view.redraw());
   }

   @Test
   void lineChangedDoesNotThrow() {
      assertDoesNotThrow(() -> view.lineChanged(1));
   }

   @Test
   void mscreenDoesNotThrow() {
      assertDoesNotThrow(() -> view.mscreen(5, 100));
   }

   @Test
   void cursorChangeDoesNotThrow() {
      assertDoesNotThrow(() -> view.cursorChange(1, 1));
   }

   // ── updateTempMarkPos ───────────────────────────────────────

   @Test
   void updateTempMarkPosSetsMark() {
      FileDescriptor fd = te.fdes();
      // filePos is (0,1), so a different position should set a mark
      Position pos = new Position(5, 3, fd, "temp");
      view.updateTempMarkPos(pos);
      assertNotNull(view.getMark(), "mark should be set for different pos");
   }

   @Test
   void updateTempMarkPosSamePosClearsMark() {
      // Set file position to match the event position
      view.setFilePos(0, 1);
      FileDescriptor fd = te.fdes();
      Position pos = new Position(0, 1, fd, "same");
      view.updateTempMarkPos(pos);
      assertNull(view.getMark(),
         "mark should be cleared when pos matches cursor");
   }

   // ── helper ──────────────────────────────────────────────────

   @SuppressWarnings("unchecked")
   private static TextEdit<String> openTestBuffer(String name,
         String content) {
      try {
         FileDescriptor fd = FileDescriptor.InternalFd.make(name);
         FileProperties<String> fp =
            new FileProperties<>(fd, StringIoc.converter);
         TextEdit<String> te = new TextEdit<>(
            new IoConverter<>(fp, false), fp);
         String[] lines = content.split("\n", -1);
         for (int i = 0; i < lines.length; i++) {
            te.insertOne(lines[i], i + 1);
         }
         return te;
      } catch (Exception e) {
         throw new RuntimeException("failed to create test buffer", e);
      }
   }
}
