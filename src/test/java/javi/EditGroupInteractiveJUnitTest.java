package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for EditGroup interactive-mode methods that have 0% coverage:
 * subChar, deletemode, yankmode, changemode, shiftmode, qmode, putbuffer.
 *
 * Uses EventQueue.insert() to pre-load keys that the interactive methods
 * will read via EventQueue.nextKey().
 */
class EditGroupInteractiveJUnitTest {

   private static final String TEST_PREFIX = "ju_egint_";
   private TextEdit<String> te;
   private TestView view;
   private FvContext<?> fvc;
   private static int testCounter;
   private String testFileName;

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initAllCommands();
      if (MapEvent.getNormalKeyMap() == null)
         MapEvent.bindCommands();
   }

   @BeforeEach
   void setUp() throws Exception {
      EventQueue.biglock2.lock();
      UI.setStream(new StringReader(""));
      testFileName = TEST_PREFIX + (testCounter++);
      te = openTestFile(testFileName);
      te.inserttext("hello world\nsecond line\nthird line\n", 0, 1);
      te.checkpoint();
      view = new TestView(true);
      fvc = FvContext.connectFv(te, view);
      fvc.cursorabs(0, 1);
   }

   @AfterEach
   void tearDown() {
      try {
         if (fvc != null)
            fvc.edvec.disposeFvc();
         deleteTestFiles(testFileName);
      } catch (Exception e) {
         // ignore cleanup errors
      }
      EventQueue.biglock2.unlock();
   }

   // --- Helpers ---

   private static String testPath(String name) {
      return history.Testutil.testFile(name).getPath();
   }

   private static FileDescriptor.LocalFile makeLocal(String name) {
      return FileDescriptor.LocalFile.make(history.Testutil.testFile(name));
   }

   private static TextEdit<String> openTestFile(String name) {
      FileDescriptor fd = FileDescriptor.make(testPath(name));
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> tex = new TextEdit<>(fi, fp);
      tex.finish();
      return tex;
   }

   private static void deleteTestFiles(String... names) throws IOException {
      for (String name : names) {
         makeLocal(name).delete();
         makeLocal(name + ".dmp2").delete();
      }
   }

   private static void queueChar(char c) {
      EventQueue.insert(new JeyEvent(0, 0, c));
   }

   // ============================================================
   // subChar (replace character — 'r' in vi)
   // ============================================================

   @Test
   void subCharReplacesSingleChar() throws Exception {
      // Position at 'h' in "hello world"
      fvc.cursorabs(0, 1);
      // Queue the replacement character
      queueChar('X');
      // Invoke subchar command (not dotmode, count=1)
      Rgroup.doCommand("subchar", null, 1, 0, fvc, false);
      assertEquals("Xello world", fvc.at(1).toString());
   }

   @Test
   void subCharReplacesMultipleChars() throws Exception {
      fvc.cursorabs(0, 1);
      queueChar('Z');
      Rgroup.doCommand("subchar", null, 3, 0, fvc, false);
      assertEquals("ZZZlo world", fvc.at(1).toString());
   }

   @Test
   void subCharEscCancels() throws Exception {
      fvc.cursorabs(0, 1);
      queueChar('\u001b'); // ESC
      Rgroup.doCommand("subchar", null, 1, 0, fvc, false);
      // Line should be unchanged
      assertEquals("hello world", fvc.at(1).toString());
   }

   @Test
   void subCharAtEndOfLine() throws Exception {
      // "hello world" has 11 chars (0-10)
      fvc.cursorabs(10, 1); // at 'd'
      queueChar('!');
      Rgroup.doCommand("subchar", null, 1, 0, fvc, false);
      assertEquals("hello worl!", fvc.at(1).toString());
   }

   @Test
   void subCharCountExceedsLineLength() throws Exception {
      // At position 9, count=5 but only 2 chars remain
      fvc.cursorabs(9, 1); // at 'l' in "world"
      queueChar('*');
      Rgroup.doCommand("subchar", null, 5, 0, fvc, false);
      // Should replace only available chars (ld)
      assertEquals("hello wor**", fvc.at(1).toString());
   }

   // ============================================================
   // deletemode (d + motion — 'dd', 'dw', etc.)
   // ============================================================

   @Test
   void deletemodeDeleteLine() throws Exception {
      fvc.cursorabs(0, 1);
      // Queue 'd' for dd (delete line)
      queueChar('d');
      Rgroup.doCommand("deletemode", null, 1, 0, fvc, false);
      assertEquals("second line", fvc.at(1).toString());
   }

   @Test
   void deletemodeDeleteMultipleLines() throws Exception {
      fvc.cursorabs(0, 1);
      queueChar('d');
      Rgroup.doCommand("deletemode", null, 2, 0, fvc, false);
      assertEquals("third line", fvc.at(1).toString());
   }

   // ============================================================
   // yankmode (y + motion — 'yy', 'yw', etc.)
   // ============================================================

   @Test
   void yankmodeYankLine() throws Exception {
      fvc.cursorabs(0, 1);
      // Queue 'y' for yy (yank line)
      queueChar('y');
      Rgroup.doCommand("yankmode", null, 1, 0, fvc, false);
      // Line should be unchanged (yank is non-destructive)
      assertEquals("hello world", fvc.at(1).toString());
      // Verify something was yanked by attempting put
      Rgroup.doCommand("putafter", null, 1, 0, fvc, false);
      assertEquals("hello world", fvc.at(2).toString());
   }

   @Test
   void yankmodeYankMultipleLines() throws Exception {
      fvc.cursorabs(0, 1);
      queueChar('y');
      Rgroup.doCommand("yankmode", null, 2, 0, fvc, false);
      // Original unchanged
      assertEquals("hello world", fvc.at(1).toString());
      assertEquals("second line", fvc.at(2).toString());
   }

   // ============================================================
   // shiftmode (> + motion, < + motion)
   // ============================================================

   @Test
   void shiftmodeShiftRight() throws Exception {
      // Indent first to give findalign context
      te.changeElementAtStr("   hello world", 1);
      te.changeElementAtStr("   second line", 2);
      fvc.cursorabs(0, 3); // third line has no indent
      // Queue '>' for >> (shift right current line)
      queueChar('>');
      Rgroup.doCommand("shiftmode", Integer.valueOf(1), 1, 0, fvc, false);
      // shiftright should add spaces based on findalign
      String result = fvc.at(3).toString();
      assertTrue(result.startsWith(" ") || result.startsWith("\t"),
         "Line should be indented after shift right: '" + result + "'");
   }

   @Test
   void shiftmodeShiftLeft() throws Exception {
      // First indent the line
      te.changeElementAtStr("   hello world", 1);
      fvc.cursorabs(0, 1);
      queueChar('<');
      Rgroup.doCommand("shiftmode", Integer.valueOf(0), 1, 0, fvc, false);
      // Line should have less indentation
      String result = fvc.at(1).toString();
      assertTrue(!result.startsWith("   "),
         "Line should be de-indented after shift left: '" + result + "'");
   }

   // ============================================================
   // putbuffer (p/P — put after/before)
   // ============================================================

   @Test
   void putAfterInsertsYankedLine() throws Exception {
      // First yank a line
      fvc.cursorabs(0, 1);
      queueChar('y');
      Rgroup.doCommand("yankmode", null, 1, 0, fvc, false);
      // Now put after
      Rgroup.doCommand("putafter", null, 1, 0, fvc, false);
      assertEquals("hello world", fvc.at(2).toString());
   }

   @Test
   void putBeforeInsertsYankedLine() throws Exception {
      fvc.cursorabs(0, 2); // on "second line"
      // Yank current line
      queueChar('y');
      Rgroup.doCommand("yankmode", null, 1, 0, fvc, false);
      // Move to line 1 and put before
      fvc.cursorabs(0, 1);
      Rgroup.doCommand("putbefore", null, 1, 0, fvc, false);
      assertEquals("second line", fvc.at(1).toString());
      assertEquals("hello world", fvc.at(2).toString());
   }
}
