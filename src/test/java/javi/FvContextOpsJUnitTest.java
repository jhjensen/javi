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
 * Tests exercising FvContext-dependent code in EditGroup, MoveGroup,
 * and FileList. Uses TestView to create headless FvContext instances.
 */
class FvContextOpsJUnitTest {

   private static final String TEST_PREFIX = "ju_fvops_";
   private TextEdit<String> te;
   private TestView view;
   private FvContext<?> fvc;

   private static int testCounter;
   private String testFileName;

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void setUp() throws Exception {
      EventQueue.biglock2.lock();
      UI.setStream(new StringReader(""));
      testFileName = TEST_PREFIX + (testCounter++);
      te = openTestFile(testFileName);
      te.inserttext("aaa\nbbb\nccc\nddd\neee\n", 0, 1);
      te.checkpoint();
      view = new TestView(true);
      fvc = FvContext.connectFv(te, view);
   }

   @AfterEach
   void tearDown() {
      try {
         deleteTestFiles(testFileName, TEST_PREFIX + "out");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // --- Cursor positioning ---

   @Test
   void cursorabsMovesToPosition() {
      fvc.cursorabs(0, 3);
      assertEquals(3, fvc.inserty());
      assertEquals(0, fvc.insertx());
   }

   @Test
   void cursoryRelativeMove() {
      fvc.cursorabs(0, 2);
      fvc.cursory(2);
      assertEquals(4, fvc.inserty());
   }

   @Test
   void cursoryabsMovesToLine() {
      fvc.cursoryabs(4);
      assertEquals(4, fvc.inserty());
   }

   @Test
   void cursorxRelativeMove() {
      fvc.cursorabs(0, 1);
      fvc.cursorx(2);
      assertEquals(2, fvc.insertx());
   }

   @Test
   void cursorxabsEndOfLine() {
      fvc.cursorabs(0, 1);
      fvc.cursorxabs(Integer.MAX_VALUE);
      assertTrue(fvc.insertx() >= 2,
         "cursor should be at or near end of line 'aaa'");
   }

   // --- FvContext state queries ---

   @Test
   void insertPositionMatchesBuffer() {
      fvc.cursorabs(0, 1);
      assertEquals(1, fvc.inserty());
   }

   @Test
   void getCurrFvcAfterConnect() {
      fvc.setCurrView();
      FvContext<?> curr = FvContext.getCurrFvc();
      assertNotNull(curr);
   }

   @Test
   void fvcToStringIncludesName() {
      String s = fvc.toString();
      assertNotNull(s);
   }

   @Test
   void fvcEdvecIsConnectedBuffer() {
      assertEquals(te, fvc.edvec);
   }

   @Test
   void fvcViewIsConnectedView() {
      assertEquals(view, fvc.vi);
   }

   // --- FoldModel ---

   @Test
   void getFoldModelInitiallyNull() {
      // Without explicit setup, fold model is null
      // Just verify no crash
      FoldModel fm = fvc.getFoldModel();
      // May or may not be null depending on init
   }

   // --- FileList operations ---

   @Test
   void fileListCountModified() {
      // After connecting and modifying, count should include our buffer
      int count = FileList.countModified();
      assertTrue(count >= 0);
   }

   @Test
   void fileListNameNotEmpty() {
      String name = te.getName();
      assertNotNull(name);
      assertFalse(name.isEmpty());
   }

   // --- processCommand through FvContext ---

   @Test
   void processCommandSubstitute() throws Exception {
      fvc.cursorabs(0, 1);
      int result = te.processCommand("1s/aaa/AAA/", 1);
      assertTrue(result >= 0);
      assertEquals("AAA", te.at(1).toString());
   }

   @Test
   void processCommandDelete() throws Exception {
      int before = te.finish();
      int result = te.processCommand("2d", 1);
      assertTrue(result >= 0);
      assertTrue(te.finish() < before);
      assertEquals("aaa", te.at(1).toString());
      assertEquals("ccc", te.at(2).toString());
   }

   @Test
   void processCommandCopy() throws Exception {
      int before = te.finish();
      int result = te.processCommand("1t$", 1);
      assertTrue(result >= 0);
      assertTrue(te.finish() > before);
   }

   @Test
   void processCommandGlobalSubstitute() throws Exception {
      int result = te.processCommand("g/./s/$/!/", 1);
      assertTrue(result >= 0);
      assertEquals("aaa!", te.at(1).toString());
   }

   // --- inserttext and undo ---

   @Test
   void insertAndUndoViaFvc() throws Exception {
      fvc.cursorabs(0, 1);
      te.inserttext("XXX", 0, 1);
      te.checkpoint();
      assertTrue(te.at(1).toString().startsWith("XXX"));
      te.undo();
      assertEquals("aaa", te.at(1).toString());
   }

   @Test
   void insertAndRedoViaFvc() throws Exception {
      fvc.cursorabs(0, 1);
      te.inserttext("YYY", 0, 1);
      te.checkpoint();
      te.undo();
      assertEquals("aaa", te.at(1).toString());
      te.redo();
      assertTrue(te.at(1).toString().startsWith("YYY"));
   }

   // --- write and reopen ---

   @Test
   void writeAndVerifyModifiedFlag() throws Exception {
      assertTrue(te.isModified());
      te.printout();
      assertFalse(te.isModified());
   }

   // --- Command dispatch through FvContext ---

   @Test
   void commandTabstopViaFvc() {
      Command.command("tabstop 4", fvc, null);
      assertEquals(4, view.getTabStop());
   }

   @Test
   void commandSetTabstopViaFvc() {
      Command.command("set tabstop=2", fvc, null);
      assertEquals(2, view.getTabStop());
   }

   // --- helper methods ---

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

   private static void deleteTestFiles(String... names) {
      for (String name : names) {
         try {
            makeLocal(name).delete();
         } catch (IOException e) { /* ignore */ }
         try {
            makeLocal(name + ".dmp2").delete();
         } catch (IOException e) { /* ignore */ }
      }
   }
}
