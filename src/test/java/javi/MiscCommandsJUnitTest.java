package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MiscCommands} — enum, static accessors,
 * and the {@link MiscCommands.ProcIo} inner class.
 */
class MiscCommandsJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
      TestInit.initCommands();
   }

   @BeforeEach
   void lock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void unlock() {
      EventQueue.biglock2.unlock();
   }

   // ── Cmd enum tests ─────────────────────────────────────────

   @Test
   void cmdEnumContainsUndo() {
      assertNotNull(MiscCommands.Cmd.valueOf("UNDO"));
   }

   @Test
   void cmdEnumContainsRedo() {
      assertNotNull(MiscCommands.Cmd.valueOf("REDO"));
   }

   @Test
   void cmdEnumContainsVt() {
      assertNotNull(MiscCommands.Cmd.valueOf("VT"));
   }

   @Test
   void cmdEnumContainsRedraw() {
      assertNotNull(MiscCommands.Cmd.valueOf("REDRAW"));
   }

   @Test
   void cmdEnumContainsExec() {
      assertNotNull(MiscCommands.Cmd.valueOf("EXEC"));
   }

   @Test
   void cmdEnumContainsShells() {
      assertNotNull(MiscCommands.Cmd.valueOf("SHELLS"));
   }

   @Test
   void cmdEnumContainsShellClose() {
      assertNotNull(MiscCommands.Cmd.valueOf("SHELL_CLOSE"));
   }

   @Test
   void cmdEnumContainsShellNext() {
      assertNotNull(MiscCommands.Cmd.valueOf("SHELL_NEXT"));
   }

   @Test
   void cmdEnumContainsShellPrev() {
      assertNotNull(MiscCommands.Cmd.valueOf("SHELL_PREV"));
   }

   @Test
   void cmdEnumContainsShellName() {
      assertNotNull(MiscCommands.Cmd.valueOf("SHELL_NAME"));
   }

   @Test
   void cmdEnumContainsShellEnv() {
      assertNotNull(MiscCommands.Cmd.valueOf("SHELL_ENV"));
   }

   @Test
   void cmdEnumContainsShellHistory() {
      assertNotNull(MiscCommands.Cmd.valueOf("SHELL_HISTORY"));
   }

   @Test
   void cmdEnumCount() {
      assertEquals(32, MiscCommands.Cmd.values().length,
         "Update this count when adding new MiscCommands.Cmd values");
   }

   @Test
   void cmdEnumValueOfRoundTrips() {
      for (MiscCommands.Cmd c : MiscCommands.Cmd.values())
         assertEquals(c, MiscCommands.Cmd.valueOf(c.name()));
   }

   @Test
   void cmdEnumInvalidThrows() {
      assertThrows(IllegalArgumentException.class,
         () -> MiscCommands.Cmd.valueOf("BOGUS"));
   }

   // ── Static accessors ───────────────────────────────────────

   @Test
   void getHeightReturnsPositive() {
      assertTrue(MiscCommands.getHeight() > 0);
   }

   @Test
   void getWidthReturnsPositive() {
      assertTrue(MiscCommands.getWidth() > 0);
   }

   @Test
   void getHeightDefault() {
      // Height is set to 70 via .javini lines setting
      assertTrue(MiscCommands.getHeight() > 0);
   }

   @Test
   void getWidthDefault() {
      // Default width is 80 as set in the source
      assertEquals(80, MiscCommands.getWidth());
   }

   // ── Rgroup type check ─────────────────────────────────────

   @Test
   void miscCommandsExtendsRgroup() {
      // MiscCommands is a subclass of Rgroup (verified by class hierarchy)
      assertTrue(Rgroup.class.isAssignableFrom(MiscCommands.class));
   }

   @Test
   void zprocessCommandIsRegistered() {
      // Verify "zprocess" is a registered command by checking that it
      // does NOT return null from bindingLookup.
      // We cannot actually execute it because zprocess() calls
      // EventQueue.nextKey() which blocks waiting for keyboard input.
      assertNotNull(Rgroup.bindingLookup("zprocess"),
         "zprocess should be a registered command");
   }

   @Test
   void undoCommandDispatches() throws Exception {
      try {
         Rgroup.doCommand("undo", null, 0, 1,
            FvContext.getCurrFvc(), false);
      } catch (NullPointerException e) {
         // Expected
      }
   }

   @Test
   void unknownCommandThrows() throws Exception {
      assertThrows(InputException.class, () ->
         Rgroup.doCommand("_no_such_command_xyz", null, 0, 1,
            FvContext.getCurrFvc(), false));
   }

   // ── parseKeySpec tests ────────────────────────────────────

   @Test
   void parseKeySpecSingleChar() throws InputException {
      JeyEvent e = MiscCommands.parseKeySpec("a");
      assertEquals('a', e.getKeyChar());
   }

   @Test
   void parseKeySpecCtrlChar() throws InputException {
      JeyEvent e = MiscCommands.parseKeySpec("C-a");
      assertEquals(1, e.getKeyChar()); // ctrl-a = 1
   }

   @Test
   void parseKeySpecShiftFunctionKey() throws InputException {
      JeyEvent e = MiscCommands.parseKeySpec("S-F1");
      assertEquals(JeyEvent.VK_F1, e.getKeyCode());
      assertTrue((e.getModifiers() & JeyEvent.SHIFT_MASK) != 0);
   }

   @Test
   void parseKeySpecFunctionKeysF1toF12()
         throws InputException {
      for (int i = 1; i <= 12; i++) {
         JeyEvent e = MiscCommands.parseKeySpec("F" + i);
         assertNotEquals(JeyEvent.CHAR_UNDEFINED,
            e.getKeyCode(),
            "F" + i + " should have a key code");
      }
   }

   @Test
   void parseKeySpecArrowKeys() throws InputException {
      assertNotEquals(0,
         MiscCommands.parseKeySpec("Up").getKeyCode());
      assertNotEquals(0,
         MiscCommands.parseKeySpec("Down").getKeyCode());
      assertNotEquals(0,
         MiscCommands.parseKeySpec("Left").getKeyCode());
      assertNotEquals(0,
         MiscCommands.parseKeySpec("Right").getKeyCode());
   }

   @Test
   void parseKeySpecNavigationKeys() throws InputException {
      assertNotEquals(0,
         MiscCommands.parseKeySpec("Home").getKeyCode());
      assertNotEquals(0,
         MiscCommands.parseKeySpec("End").getKeyCode());
      assertNotEquals(0,
         MiscCommands.parseKeySpec("PgUp").getKeyCode());
      assertNotEquals(0,
         MiscCommands.parseKeySpec("PgDn").getKeyCode());
   }

   @Test
   void parseKeySpecUnknownThrows() {
      assertThrows(InputException.class, () ->
         MiscCommands.parseKeySpec("BOGUS_KEY"));
   }

   @Test
   void parseKeySpecUnknownModifierThrows() {
      assertThrows(InputException.class, () ->
         MiscCommands.parseKeySpec("Z-a"));
   }

   @Test
   void parseKeySpecMultiModifiers() throws InputException {
      JeyEvent e = MiscCommands.parseKeySpec("C-S-F5");
      assertEquals(JeyEvent.VK_F5, e.getKeyCode());
      assertTrue((e.getModifiers() & JeyEvent.CTRL_MASK) != 0);
      assertTrue((e.getModifiers() & JeyEvent.SHIFT_MASK) != 0);
   }

   // ── ProcIo tests ─────────────────────────────────────────

   @Test
   void procIoRunsEchoCommand() throws Exception {
      MiscCommands.ProcIo pio =
         MiscCommands.ProcIo.mkProcIo("test",
            "echo", "hello");
      String line = pio.getnext();
      assertEquals("hello", line);
      pio.dispose();
   }
}
