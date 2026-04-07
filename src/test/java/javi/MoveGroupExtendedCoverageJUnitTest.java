package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional coverage for {@link MoveGroup} — character motions,
 * word motions, line position commands, substitute variations.
 * Uses Rgroup.doCommand() to exercise MoveGroup.doroutine directly,
 * avoiding the need for MapEvent.bindCommands() which fails headless.
 */
class MoveGroupExtendedCoverageJUnitTest {

   private static final Boolean TRUE = Boolean.TRUE;
   private static final Boolean FALSE = Boolean.FALSE;

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

   private static String testPath(String name) {
      return history.Testutil.testFile(name).getPath();
   }

   private static FileDescriptor.LocalFile makeLocal(String name) {
      return FileDescriptor.LocalFile.make(
         history.Testutil.testFile(name));
   }

   private static TextEdit<String> openTestFile(String name) {
      FileDescriptor fd = FileDescriptor.make(testPath(name));
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      return te;
   }

   private static void deleteTestFiles(String... names)
         throws IOException {
      for (String name : names) {
         makeLocal(name).delete();
         makeLocal(name + ".dmp2").delete();
      }
   }

   private FvContext<?> setupFvc(TextEdit<String> ex)
         throws Exception {
      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(ex, view);
      fvc.cursoryabs(1);
      return fvc;
   }

   // ── character motions (movechar) ───────────────────────────

   @Test
   void movecharRight() throws Exception {
      String fname = "ju_mgxc_r";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdef\n", 0, 1);
      ex.checkpoint();

      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("movechar", TRUE, 1, 0, fvc, false);
      assertEquals(1, fvc.insertx(), "movechar right by 1");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void movecharLeft() throws Exception {
      String fname = "ju_mgxc_l";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdef\n", 0, 1);
      ex.checkpoint();

      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(3, 1);

      Rgroup.doCommand("movechar", FALSE, 1, 0, fvc, false);
      assertEquals(2, fvc.insertx(), "movechar left by 1");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void movecharRightMultiple() throws Exception {
      String fname = "ju_mgxc_r3";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdef\n", 0, 1);
      ex.checkpoint();

      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("movechar", TRUE, 3, 0, fvc, false);
      assertEquals(3, fvc.insertx(), "movechar right by 3");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── line motions (moveline) ────────────────────────────────

   @Test
   void movelineDown() throws Exception {
      String fname = "ju_mgxc_dn";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("line1\nline2\nline3\n", 0, 1);
      ex.checkpoint();

      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("moveline", TRUE, 1, 0, fvc, false);
      assertEquals(2, fvc.inserty(), "moveline down");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void movelineUp() throws Exception {
      String fname = "ju_mgxc_up";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("line1\nline2\nline3\n", 0, 1);
      ex.checkpoint();

      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(0, 3);

      Rgroup.doCommand("moveline", FALSE, 1, 0, fvc, false);
      assertEquals(2, fvc.inserty(), "moveline up");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── word motions ───────────────────────────────────────────

   @Test
   void forwardWord() throws Exception {
      String fname = "ju_mgxc_fw";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world foo\n", 0, 1);
      ex.checkpoint();

      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("forwardword", null, 1, 0, fvc, false);
      assertTrue(fvc.insertx() > 0, "forwardword should advance");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void backwardWord() throws Exception {
      String fname = "ju_mgxc_bw";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world\n", 0, 1);
      ex.checkpoint();

      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(8, 1);

      int startX = fvc.insertx();
      Rgroup.doCommand("backwardword", null, 1, 0, fvc, false);
      assertTrue(fvc.insertx() < startX, "backwardword should go back");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void endWord() throws Exception {
      String fname = "ju_mgxc_ew";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world\n", 0, 1);
      ex.checkpoint();

      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("endword", null, 1, 0, fvc, false);
      assertTrue(fvc.insertx() > 0, "endword should advance");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── line position (linepos) ────────────────────────────────

   @Test
   void gotoLine() throws Exception {
      String fname = "ju_mgxc_gl";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\nb\nc\nd\ne\n", 0, 1);
      ex.checkpoint();

      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("gotoline", null, 3, 3, fvc, false);
      assertEquals(3, fvc.inserty(), "gotoline to line 3");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void startText() throws Exception {
      String fname = "ju_mgxc_st";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("   hello\n", 0, 1);
      ex.checkpoint();

      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("starttext", null, 1, 0, fvc, false);
      assertEquals(3, fvc.insertx(), "starttext to first non-blank");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── substitute tests (via processCommand) ─────────────────

   @Test
   void substituteGlobalFlag() throws Exception {
      String fname = "ju_mgxc_gsub";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aXbXcX\n", 0, 1);
      ex.checkpoint();

      ex.processCommand("1s/X/Y/g", 1);
      assertEquals("aYbYcY", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void substituteMultiLine() throws Exception {
      String fname = "ju_mgxc_msub";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("AAA\nAAA\nAAA\n", 0, 1);
      ex.checkpoint();

      ex.processCommand("1,3s/AAA/BBB/", 1);
      assertEquals("BBB", ex.at(1).toString());
      assertEquals("BBB", ex.at(2).toString());
      assertEquals("BBB", ex.at(3).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void balanceChar() throws Exception {
      String fname = "ju_mgxc_pct";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("(hello)\n", 0, 1);
      ex.checkpoint();

      FvContext<?> fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("balancechar", null, 1, 0, fvc, false);
      assertEquals(6, fvc.insertx(), "% should match closing paren");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }
}
