package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage tests for MoveGroup exercising word motion,
 * search, processCommand navigation, and FoldModel integration.
 */
class MoveGroupCoverageJUnitTest {

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
      assertFalse(te.getError(),
         "File should open without error: " + name);
      return te;
   }

   private static void deleteTestFiles(String... names)
         throws IOException {
      for (String name : names) {
         makeLocal(name).delete();
         makeLocal(name + ".dmp2").delete();
      }
   }

   // ============================================================
   // processCommand regex search with substitute
   // ============================================================

   @Test
   void searchSubstituteWithRegex() throws Exception {
      String fname = "ju_mgc_regex1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("foo123bar\nfoo456bar\n", 0, 1);
      ex.checkpoint();

      // Substitute digits with X
      ex.processCommand("1s/[0-9]+/X/", 1);
      assertEquals("fooXbar", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void globalSubstituteFlag() throws Exception {
      String fname = "ju_mgc_gsub";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aXaXa\n", 0, 1);
      ex.checkpoint();

      // Without 'g' flag, only first match is replaced
      ex.processCommand("1s/X/Y/", 1);
      assertEquals("aYaXa", ex.at(1).toString());

      // With 'g' flag, all matches are replaced
      ex.processCommand("1s/X/Z/g", 1);
      assertEquals("aYaZa", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void substituteWithDotSlash() throws Exception {
      String fname = "ju_mgc_dotslash";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext(
         "current line\nother line\nthird\n", 0, 1);
      ex.checkpoint();

      // . refers to current position (ypos)
      int result = ex.processCommand(".s/current/UPDATED/", 1);
      assertTrue(result >= 0);
      assertEquals("UPDATED line", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // processCommand navigation: $, +, - offsets
   // ============================================================

   @Test
   void dollarMinusOffset() throws Exception {
      String fname = "ju_mgc_doloff";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\nb\nc\nd\ne\n", 0, 1);
      ex.checkpoint();

      int result = ex.processCommand("$-2", 1);
      int lastLine = ex.finish() - 1;
      assertEquals(lastLine - 2, result);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void plusOffset() throws Exception {
      String fname = "ju_mgc_plusoff";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\nb\nc\nd\ne\n", 0, 1);
      ex.checkpoint();

      int result = ex.processCommand("1+2", 1);
      assertEquals(3, result);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void dotNavigation() throws Exception {
      String fname = "ju_mgc_dotnav";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\nb\nc\n", 0, 1);
      ex.checkpoint();

      // From ypos=2, "." should return 2
      int result = ex.processCommand(".", 2);
      assertEquals(2, result);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // processCommand global search + substitute
   // ============================================================

   @Test
   void globalSearchAndSubstitute() throws Exception {
      String fname = "ju_mgc_gss";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("foo bar\nbaz foo\n"
         + "qux\nfoo end\n", 0, 1);
      ex.checkpoint();

      // g/foo/s/foo/FOO/ — on lines matching "foo", sub foo→FOO
      ex.processCommand("g/foo/s/foo/FOO/", 1);
      assertEquals("FOO bar", ex.at(1).toString());
      assertEquals("baz FOO", ex.at(2).toString());
      assertEquals("qux", ex.at(3).toString());
      assertEquals("FOO end", ex.at(4).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void inverseGlobalSubstitute() throws Exception {
      String fname = "ju_mgc_vgss";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("foo\nbar\nfoo\nbaz\n", 0, 1);
      ex.checkpoint();

      // v/foo/s/./X/ — on lines NOT matching "foo",
      // sub first char with X
      ex.processCommand("v/foo/s/./X/", 1);
      assertEquals("foo", ex.at(1).toString());
      assertEquals("Xar", ex.at(2).toString());
      assertEquals("foo", ex.at(3).toString());
      assertEquals("Xaz", ex.at(4).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // processCommand move and copy across ranges
   // ============================================================

   @Test
   void moveRangeToAfterLine() throws Exception {
      String fname = "ju_mgc_mrange";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aa\nbb\ncc\ndd\nee\n", 0, 1);
      ex.checkpoint();
      int before = ex.finish();

      // Move lines 2-3 to after line 5
      ex.processCommand("2,3m5", 1);
      // Line count should be preserved
      assertEquals(before, ex.finish(),
         "move should not change total line count");
      // First line should still be "aa"
      assertEquals("aa", ex.at(1).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void copyToBeginning() throws Exception {
      String fname = "ju_mgc_copy0";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aa\nbb\ncc\n", 0, 1);
      ex.checkpoint();
      int before = ex.finish();

      // Copy line 3 to beginning (after line 0)
      ex.processCommand("3t0", 1);
      assertEquals(before + 1, ex.finish());
      assertEquals("cc", ex.at(1).toString());
      assertEquals("aa", ex.at(2).toString());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ============================================================
   // FoldModel tests for coverage
   // ============================================================

   @Test
   void foldModelMapScreenToBuffer() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 6);
      fm.closeFold(3);

      // Lines 1,2 visible, fold at 3 collapsed (hides 4-6)
      // Screen line 1 = buffer 1
      // Screen line 3 = buffer 3 (fold header)
      // Screen line 4 = buffer 7 (after fold)
      assertEquals(1, fm.mapScreenToBuffer(1));
      assertEquals(3, fm.mapScreenToBuffer(3));
      assertEquals(7, fm.mapScreenToBuffer(4));
   }

   @Test
   void foldModelMapBufferToScreen() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 6);
      fm.closeFold(3);

      // Buffer line 4 is hidden
      assertEquals(-1, fm.mapBufferToScreen(4));
      // Buffer line 1 is visible as screen 1
      assertEquals(1, fm.mapBufferToScreen(1));
   }

   @Test
   void foldModelNextVisible() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 6);
      fm.closeFold(3);

      // From fold start, next visible skips to endLine + 1
      assertEquals(7, fm.nextVisible(3));
      // Normal lines step by 1
      assertEquals(2, fm.nextVisible(1));
   }

   @Test
   void foldModelPrevVisible() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 6);
      fm.closeFold(3);

      // From line 7, prev visible should be 3 (fold start)
      assertEquals(3, fm.prevVisible(4));
      // Before line 1 should be 0
      assertEquals(0, fm.prevVisible(1));
   }

   @Test
   void foldModelGetFoldIndicator() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 6);

      // Open fold: start gets '-'
      assertEquals('-', fm.getFoldIndicator(3));
      // Body of open fold gets '|'
      assertEquals('|', fm.getFoldIndicator(4));
      // Outside fold gets '\0'
      assertEquals('\0', fm.getFoldIndicator(1));

      fm.closeFold(3);
      // Collapsed fold start gets '+'
      assertEquals('+', fm.getFoldIndicator(3));
   }

   @Test
   void foldModelStatusSummary() {
      FoldModel fm = new FoldModel();
      assertEquals("no folds", fm.statusSummary());

      fm.addFold(1, 5);
      fm.addFold(10, 15);
      fm.closeFold(1);
      assertTrue(fm.statusSummary().contains("2 folds"),
         "Summary should include fold count");
      assertTrue(fm.statusSummary().contains("1 closed"),
         "Summary should include closed count");
   }

   @Test
   void foldModelAdjustForInsert() {
      FoldModel fm = new FoldModel();
      fm.addFold(5, 10);

      // Insert 3 lines at index 3 (before fold)
      fm.adjustForEdit(3, 3);
      FoldModel.FoldRange fr = fm.getFolds().get(0);
      assertEquals(8, fr.startLine);
      assertEquals(13, fr.endLine);
   }

   @Test
   void foldModelAdjustForDelete() {
      FoldModel fm = new FoldModel();
      fm.addFold(5, 10);

      // Delete 2 lines at index 2 (before fold)
      fm.adjustForEdit(2, -2);
      FoldModel.FoldRange fr = fm.getFolds().get(0);
      assertEquals(3, fr.startLine);
      assertEquals(8, fr.endLine);
   }

   @Test
   void foldSummaryTextFormat() {
      String text = FoldModel.foldSummaryText(
         10, 20, "   public void foo() {");
      assertEquals("+--  10 lines: public void foo() {", text);
   }

   @Test
   void foldRangeSpanAndHiddenLines() {
      FoldModel fm = new FoldModel();
      fm.addFold(5, 10);
      FoldModel.FoldRange fr = fm.findFoldAtStart(5);
      assertNotNull(fr);

      assertEquals(6, fr.span());
      assertEquals(0, fr.hiddenLines());

      fr.collapsed = true;
      assertEquals(5, fr.hiddenLines());
   }

   @Test
   void foldModelGetVisibleLineCount() {
      FoldModel fm = new FoldModel();
      fm.addFold(3, 6);
      fm.closeFold(3);

      // 10 total lines, fold hides 3 lines (4, 5, 6)
      assertEquals(7, fm.getVisibleLineCount(10));
   }

   // ============================================================
   // MoveGroup.doroutine via Rgroup.doCommand with TestView
   // ============================================================

   @SuppressWarnings("unchecked")
   private FvContext setupFvc(TextEdit<String> te)
         throws InputException {
      TestView view = new TestView(true);
      return FvContext.connectFv(te, view);
   }

   @Test
   void doCommandMovecharForward() throws Exception {
      String fname = "ju_mgc_mc_fwd";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdefgh\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);
      int startX = fvc.insertx();

      // movechar forward (true = forward, count=3)
      Rgroup.doCommand("movechar", Boolean.TRUE, 3, 0,
         fvc, false);
      assertTrue(fvc.insertx() > startX,
         "movechar forward should advance cursor");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandMovecharBackward() throws Exception {
      String fname = "ju_mgc_mc_bk";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("abcdefgh\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(5, 1);

      Rgroup.doCommand("movechar", Boolean.FALSE, 2, 0,
         fvc, false);
      assertTrue(fvc.insertx() < 5,
         "movechar backward should move cursor left");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandMovelineDown() throws Exception {
      String fname = "ju_mgc_ml_down";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("moveline", Boolean.TRUE, 2, 0,
         fvc, false);
      assertEquals(3, fvc.inserty(),
         "moveline down 2 should move to line 3");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandMovelineUp() throws Exception {
      String fname = "ju_mgc_ml_up";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 3);

      Rgroup.doCommand("moveline", Boolean.FALSE, 1, 0,
         fvc, false);
      assertEquals(2, fvc.inserty(),
         "moveline up 1 should move to line 2");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandStarttext() throws Exception {
      String fname = "ju_mgc_start";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("   indented line\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(10, 1);

      Rgroup.doCommand("starttext", null, 1, 0, fvc, false);
      assertEquals(3, fvc.insertx(),
         "starttext should move to first non-blank");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandGotoline() throws Exception {
      String fname = "ju_mgc_goto_fvc";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\nb\nc\nd\ne\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      // gotoline with rcount > 0 goes to that line
      Rgroup.doCommand("gotoline", null, 3, 3, fvc, false);
      assertEquals(3, fvc.inserty(),
         "gotoline should go to line 3");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandForwardword() throws Exception {
      String fname = "ju_mgc_fwd_word";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world foo bar\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);
      int startX = fvc.insertx();

      Rgroup.doCommand("forwardword", null, 1, 0,
         fvc, false);
      assertTrue(fvc.insertx() > startX,
         "forwardword should advance cursor");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandBackwardword() throws Exception {
      String fname = "ju_mgc_bk_word";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world foo\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(12, 1);

      Rgroup.doCommand("backwardword", null, 1, 0,
         fvc, false);
      assertTrue(fvc.insertx() < 12,
         "backwardword should move cursor left");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandEndword() throws Exception {
      String fname = "ju_mgc_endword";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello world\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("endword", null, 1, 0, fvc, false);
      assertTrue(fvc.insertx() > 0,
         "endword should move to end of current word");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandForwardWordBig() throws Exception {
      String fname = "ju_mgc_fwd_W";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("foo.bar baz.qux\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("forwardWord", null, 1, 0,
         fvc, false);
      assertTrue(fvc.insertx() > 0,
         "forwardWord (big) should advance cursor");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandBackwardWordBig() throws Exception {
      String fname = "ju_mgc_bk_W";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("foo.bar baz.qux\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(10, 1);

      Rgroup.doCommand("backwardWord", null, 1, 0,
         fvc, false);
      assertTrue(fvc.insertx() < 10,
         "backwardWord (big) should move cursor left");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void doCommandEndWordBig() throws Exception {
      String fname = "ju_mgc_end_W";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("foo.bar baz.qux\n", 0, 1);
      ex.checkpoint();

      FvContext fvc = setupFvc(ex);
      fvc.cursorabs(0, 1);

      Rgroup.doCommand("endWord", null, 1, 0, fvc, false);
      assertTrue(fvc.insertx() > 0,
         "endWord (big) should move to end of WORD");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }
}
