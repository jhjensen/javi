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
 * Tests for {@link InsertBuffer#findspacebound} — the tab-to-space
 * alignment helper used during insert mode.
 */
class InsertBufferExtraJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
      TestInit.initCommands();
   }

   @BeforeEach
   void setUp() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void tearDown() {
      EventQueue.biglock2.unlock();
   }

   // --- Helpers ---

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

   // ============================================================
   // findspacebound tests
   // ============================================================

   @Test
   void findspaceboundFindsNextTab() throws Exception {
      String fname = "ju_ibe_fsb1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("    code here\n  cursor\n", 0, 1);
      ex.checkpoint();
      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(ex, view);
      fvc.cursorabs(0, 2);

      // From line 2 col 0, looking back at line 1 which has
      // 4-space indent: next non-space after spaces at pos 0 is 4
      int bound = InsertBuffer.findspacebound(fvc, 0);
      assertTrue(bound >= 0,
         "findspacebound should return non-negative");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void findspaceboundOnFirstLine() throws Exception {
      String fname = "ju_ibe_fsb2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("hello\n", 0, 1);
      ex.checkpoint();
      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(ex, view);
      fvc.cursorabs(0, 1);

      // On line 1, no previous lines to search
      int bound = InsertBuffer.findspacebound(fvc, 0);
      assertEquals(0, bound,
         "no prior lines should return 0");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void findspaceboundWithDeepIndent() throws Exception {
      String fname = "ju_ibe_fsb3";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("        deep indent\n  shallow\n", 0, 1);
      ex.checkpoint();
      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(ex, view);
      fvc.cursorabs(0, 2);

      int bound = InsertBuffer.findspacebound(fvc, 0);
      assertTrue(bound >= 0);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }
}
