package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage tests for {@link MiscCommands} — focuses on
 * {@code parseKeySpec}, {@code doMap}, and {@code doUnmap} code paths.
 */
class MiscCommandsCoverageJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
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

   // ── parseKeySpec: single character ─────────────────────────

   @Test
   void parseKeySpecSingleChar() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("x");
      assertEquals('x', ev.getKeyChar());
      assertEquals(0, ev.getModifiers());
   }

   @Test
   void parseKeySpecSingleDigit() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("5");
      assertEquals('5', ev.getKeyChar());
   }

   // ── parseKeySpec: modifier prefixes ────────────────────────

   @Test
   void parseKeySpecCtrlModifier() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("C-a");
      assertEquals(JeyEvent.CTRL_MASK, ev.getModifiers());
      // ctrl+a → char code 1
      assertEquals(1, ev.getKeyChar());
   }

   @Test
   void parseKeySpecShiftModifierWithActionKey()
         throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("S-F1");
      assertEquals(JeyEvent.SHIFT_MASK, ev.getModifiers());
      assertEquals(JeyEvent.VK_F1, ev.getKeyCode());
      assertEquals(JeyEvent.CHAR_UNDEFINED, ev.getKeyChar());
   }

   @Test
   void parseKeySpecAltModifier() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("A-x");
      assertEquals(JeyEvent.ALT_MASK, ev.getModifiers());
      assertEquals('x', ev.getKeyChar());
   }

   @Test
   void parseKeySpecMetaModifier() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("M-x");
      assertEquals(JeyEvent.META_MASK, ev.getModifiers());
      assertEquals('x', ev.getKeyChar());
   }

   @Test
   void parseKeySpecMultipleModifiers() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("C-S-F7");
      int expected = JeyEvent.CTRL_MASK | JeyEvent.SHIFT_MASK;
      assertEquals(expected, ev.getModifiers());
      assertEquals(JeyEvent.VK_F7, ev.getKeyCode());
   }

   // ── parseKeySpec: action key names ─────────────────────────

   @Test
   void parseKeySpecActionKeyF7() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("F7");
      assertEquals(JeyEvent.VK_F7, ev.getKeyCode());
      assertEquals(0, ev.getModifiers());
   }

   @Test
   void parseKeySpecActionKeyUp() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("Up");
      assertEquals(JeyEvent.VK_UP, ev.getKeyCode());
   }

   @Test
   void parseKeySpecActionKeyDown() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("Down");
      assertEquals(JeyEvent.VK_DOWN, ev.getKeyCode());
   }

   @Test
   void parseKeySpecActionKeyHome() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("Home");
      assertEquals(JeyEvent.VK_HOME, ev.getKeyCode());
   }

   @Test
   void parseKeySpecActionKeyEnd() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("End");
      assertEquals(JeyEvent.VK_END, ev.getKeyCode());
   }

   @Test
   void parseKeySpecActionKeyDelete() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("Delete");
      assertEquals(JeyEvent.VK_DELETE, ev.getKeyCode());
   }

   @Test
   void parseKeySpecActionKeyInsert() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("Insert");
      assertEquals(JeyEvent.VK_INSERT, ev.getKeyCode());
   }

   @Test
   void parseKeySpecActionKeyPgUp() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("PgUp");
      assertEquals(JeyEvent.VK_PAGE_UP, ev.getKeyCode());
   }

   @Test
   void parseKeySpecActionKeyPgDn() throws InputException {
      JeyEvent ev = MiscCommands.parseKeySpec("PgDn");
      assertEquals(JeyEvent.VK_PAGE_DOWN, ev.getKeyCode());
   }

   // ── parseKeySpec: error cases ──────────────────────────────

   @Test
   void parseKeySpecUnknownKeyThrows() {
      assertThrows(InputException.class,
         () -> MiscCommands.parseKeySpec("BOGUS"));
   }

   @Test
   void parseKeySpecUnknownModifierThrows() {
      assertThrows(InputException.class,
         () -> MiscCommands.parseKeySpec("X-a"));
   }

   // ── doMap / doUnmap via Rgroup.doCommand ───────────────────

   @Test
   void mapkeyNullArgThrows() {
      assertThrows(InputException.class,
         () -> Rgroup.doCommand("mapkey", null, 0, 1,
            FvContext.getCurrFvc(), false));
   }

   @Test
   void mapkeyBlankArgThrows() {
      assertThrows(InputException.class,
         () -> Rgroup.doCommand("mapkey", "  ", 0, 1,
            FvContext.getCurrFvc(), false));
   }

   @Test
   void mapkeyInsufficientPartsThrows() {
      assertThrows(InputException.class,
         () -> Rgroup.doCommand("mapkey", "move h", 0, 1,
            FvContext.getCurrFvc(), false));
   }

   @Test
   void mapkeyUnknownGroupThrows() {
      // normalKeyMap is null (bindCommands not called),
      // so getKeyGroup("move") returns null → InputException
      assertThrows(InputException.class,
         () -> Rgroup.doCommand("mapkey", "move h movechar",
            0, 1, FvContext.getCurrFvc(), false));
   }

   @Test
   void unmapkeyNullArgThrows() {
      assertThrows(InputException.class,
         () -> Rgroup.doCommand("unmapkey", null, 0, 1,
            FvContext.getCurrFvc(), false));
   }

   @Test
   void unmapkeyBlankArgThrows() {
      assertThrows(InputException.class,
         () -> Rgroup.doCommand("unmapkey", "  ", 0, 1,
            FvContext.getCurrFvc(), false));
   }

   @Test
   void unmapkeyInsufficientPartsThrows() {
      assertThrows(InputException.class,
         () -> Rgroup.doCommand("unmapkey", "move", 0, 1,
            FvContext.getCurrFvc(), false));
   }

   // ── Rgroup registration checks ────────────────────────────

   @Test
   void mapkeyCommandIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("mapkey"),
         "mapkey should be a registered command");
   }

   @Test
   void unmapkeyCommandIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("unmapkey"),
         "unmapkey should be a registered command");
   }

   @Test
   void savemapkeysCommandIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("savemapkeys"),
         "savemapkeys should be a registered command");
   }

   @Test
   void loadmapkeysCommandIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("loadmapkeys"),
         "loadmapkeys should be a registered command");
   }

   @Test
   void foldCommandIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("fold"),
         "fold should be a registered command");
   }

   @Test
   void foldindentCommandIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("foldindent"),
         "foldindent should be a registered command");
   }

   @Test
   void foldmarkerCommandIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("foldmarker"),
         "foldmarker should be a registered command");
   }

   @Test
   void shellnewCommandIsRegistered() {
      assertNotNull(Rgroup.bindingLookup("shellnew"),
         "shellnew should be a registered command");
   }
}
