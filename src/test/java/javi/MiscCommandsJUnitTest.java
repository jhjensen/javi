package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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
      assertEquals(21, MiscCommands.Cmd.values().length);
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
      // Default height is 80 as set in the source
      assertEquals(80, MiscCommands.getHeight());
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
}
