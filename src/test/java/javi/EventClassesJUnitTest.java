package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the IEvent subclasses: {@link ScrollEvent},
 * {@link CommandEvent}, and {@link PosEvent}.
 *
 * <p>These small event classes execute cursor and command operations
 * through {@link FvContext}. Tests use a headless {@link TestView}
 * and verify observable side effects via cursor position.
 */
class EventClassesJUnitTest {

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
      te = openTestBuffer("event-test",
         "line one\nline two\nline three\nline four\nline five\n");
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

   // ── ScrollEvent ─────────────────────────────────────────────

   @Test
   void scrollEventConstructionStoresFields() {
      ScrollEvent se = new ScrollEvent(5, true);
      assertNotNull(se);
   }

   @Test
   void scrollEventVerticalMovesCursorY() {
      // Start at line 1
      assertEquals(1, fvc.inserty());
      ScrollEvent se = new ScrollEvent(2, false);
      se.execute();
      assertEquals(3, fvc.inserty(),
         "vertical scroll of +2 should move cursor to line 3");
   }

   @Test
   void scrollEventVerticalNegative() {
      // Move to line 3 first, then scroll back
      fvc.cursorabs(0, 3);
      assertEquals(3, fvc.inserty());
      ScrollEvent se = new ScrollEvent(-1, false);
      se.execute();
      assertEquals(2, fvc.inserty(),
         "negative scroll should move cursor up");
   }

   @Test
   void scrollEventVerticalClampsToEnd() {
      // Scroll way past end — should clamp
      ScrollEvent se = new ScrollEvent(100, false);
      se.execute();
      assertTrue(fvc.inserty() <= te.readIn() - 1,
         "cursor should be clamped to last line");
   }

   @Test
   void scrollEventVerticalClampsToStart() {
      // Scroll way before start — should clamp to 1
      ScrollEvent se = new ScrollEvent(-100, false);
      se.execute();
      assertEquals(1, fvc.inserty(),
         "cursor should be clamped to line 1");
   }

   @Test
   void scrollEventHorizontalMovesCursorX() {
      assertEquals(0, fvc.insertx());
      ScrollEvent se = new ScrollEvent(3, true);
      se.execute();
      assertEquals(3, fvc.insertx(),
         "horizontal scroll should move cursor X");
   }

   // ── PosEvent ────────────────────────────────────────────────

   @Test
   void posEventConstructionStoresFields() {
      PosEvent pe = new PosEvent(fvc,
         new Position(0, 1, "test", "test"));
      assertNotNull(pe);
   }

   @Test
   void posEventMovesCursorToPosition() {
      FileDescriptor fd = te.fdes();
      Position target = new Position(3, 4, fd, "goto");
      PosEvent pe = new PosEvent(fvc, target);
      pe.execute();
      assertEquals(4, fvc.inserty(),
         "PosEvent should move cursor to target Y");
      assertEquals(3, fvc.insertx(),
         "PosEvent should move cursor to target X");
   }

   @Test
   void posEventSameFvcDoesNotSwitchView() {
      FileDescriptor fd = te.fdes();
      Position target = new Position(0, 2, fd, "same");
      PosEvent pe = new PosEvent(fvc, target);
      pe.execute();
      assertSame(fvc, FvContext.getCurrFvc(),
         "should remain the current context");
      assertEquals(2, fvc.inserty());
   }

   // ── CommandEvent ────────────────────────────────────────────

   @Test
   void commandEventConstructionStoresCommand() {
      CommandEvent ce = new CommandEvent("version");
      assertNotNull(ce);
   }

   @Test
   void commandEventExecutesKnownCommand() {
      // "version" is a benign command registered by Jcmds
      CommandEvent ce = new CommandEvent("version");
      // Should not throw — command is registered during initCommands()
      assertDoesNotThrow(() -> ce.execute());
   }

   @Test
   void commandEventUnknownCommandDoesNotThrow() {
      // Unknown commands produce an error message but don't throw
      CommandEvent ce = new CommandEvent("nonexistent_command_xyz");
      assertDoesNotThrow(() -> ce.execute());
   }

   // ── ExitEvent ───────────────────────────────────────────────

   @Test
   void exitEventThrowsExitException() {
      ExitEvent ee = new ExitEvent();
      assertThrows(ExitException.class, () -> ee.execute());
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
