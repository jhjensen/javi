package javi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@code .javini "e file1 file2"} deferred multi-file open
 * (F56). Exercises {@link FileList#splitFileArgs} and the deferred-open
 * path that is hit when {@link Command#isDuringInit()} is {@code true}.
 *
 * <p>Also verifies that {@link FileList#removeDummyIfNotNeeded()}
 * removes the {@code dummy0} placeholder once real files have been
 * added through the deferred path.</p>
 */
class JaviniDeferredOpenJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
      EventQueue.biglock2.lock();
      try {
         if (FileList.TestAccess.getInstance() == null)
            FileList.make("");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @BeforeEach
   void lock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void unlock() {
      EventQueue.biglock2.unlock();
   }

   // ── splitFileArgs ────────────────────────────────────────

   @Test
   @DisplayName("splitFileArgs splits on unescaped spaces")
   void splitFileArgsBasic() {
      List<String> r = FileList.splitFileArgs("file1 file2");
      assertEquals(2, r.size());
      assertEquals("file1", r.get(0));
      assertEquals("file2", r.get(1));
   }

   @Test
   @DisplayName("splitFileArgs preserves backslash-escaped spaces")
   void splitFileArgsEscapedSpace() {
      List<String> r = FileList.splitFileArgs("a\\ b c");
      assertEquals(2, r.size());
      assertEquals("a b", r.get(0));
      assertEquals("c", r.get(1));
   }

   @Test
   @DisplayName("splitFileArgs collapses repeated spaces")
   void splitFileArgsRepeatedSpaces() {
      List<String> r = FileList.splitFileArgs("  x   y  ");
      assertEquals(2, r.size());
      assertEquals("x", r.get(0));
      assertEquals("y", r.get(1));
   }

   @Test
   @DisplayName("splitFileArgs handles a single token")
   void splitFileArgsSingle() {
      List<String> r = FileList.splitFileArgs("only");
      assertEquals(1, r.size());
      assertEquals("only", r.get(0));
   }

   @Test
   @DisplayName("splitFileArgs handles all-escaped spaces filename")
   void splitFileArgsAllEscaped() {
      List<String> r = FileList.splitFileArgs("name\\ with\\ spaces.txt");
      assertEquals(1, r.size());
      assertEquals("name with spaces.txt", r.get(0));
   }

   // ── deferred multi-file open via .javini ─────────────────

   /**
    * Simulates {@code .javini "e file1 file2"} by adding the command
    * to the pending list and invoking {@link Command#execCmdList()}.
    * Verifies both files are added to the file list and that the
    * pre-existing {@code dummy0} placeholder is removed by
    * {@link FileList#removeDummyIfNotNeeded()}.
    */
   @Test
   @DisplayName(".javini 'e f1 f2' adds both files and dummy0 is removed")
   void javiniDeferredMultiOpenRemovesDummy() throws Exception {
      File testDir = history.Testutil.testDir;
      String n1 = "ju_javini_def1";
      String n2 = "ju_javini_def2";
      File f1 = new File(testDir, n1);
      File f2 = new File(testDir, n2);
      writeFile(f1, "alpha\n");
      writeFile(f2, "beta\n");

      UI.setStream(new StringReader(""));
      // Ensure a current FvContext exists so command() finds one.
      TestView view = new TestView(true);
      FvContext<?> fvc = FileList.getContext(view);
      assertNotNull(fvc);

      FileList fl = FileList.TestAccess.getInstance();
      int beforeFinish = fl.finish();
      int beforeIdx1 = indexOfShortName(fl, n1);
      int beforeIdx2 = indexOfShortName(fl, n2);
      assertEquals(-1, beforeIdx1,
         "test setup: file1 must not be open yet");
      assertEquals(-1, beforeIdx2,
         "test setup: file2 must not be open yet");

      // Drive deferred-open path: execCmdList sets isDuringInit=true
      // while dispatching the queued command.
      Command.addToCmdList("e " + f1.getPath() + " " + f2.getPath());
      Command.execCmdList();

      int idx1 = indexOfShortName(fl, n1);
      int idx2 = indexOfShortName(fl, n2);
      assertTrue(idx1 > 0,
         "file1 should be added to file list (idx=" + idx1 + ")");
      assertTrue(idx2 > 0,
         "file2 should be added to file list (idx=" + idx2 + ")");
      assertTrue(fl.finish() >= beforeFinish + 2,
         "FileList should have grown by at least 2 entries");

      // dummy0 placeholder must be removable once real files exist.
      FileList.removeDummyIfNotNeeded();
      assertFalse(hasDummyPlaceholder(fl),
         "removeDummyIfNotNeeded should drop dummy* placeholders "
         + "when real local files are present");
      // The real files must still be there.
      assertTrue(indexOfShortName(fl, n1) > 0,
         "file1 must remain after dummy removal");
      assertTrue(indexOfShortName(fl, n2) > 0,
         "file2 must remain after dummy removal");

      f1.delete();
      f2.delete();
      new File(testDir, n1 + ".dmp2").delete();
      new File(testDir, n2 + ".dmp2").delete();
   }

   // ── helpers ──────────────────────────────────────────────

   private static void writeFile(File f, String content) throws Exception {
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(f), StandardCharsets.UTF_8)) {
         w.write(content);
      }
   }

   private static int indexOfShortName(FileList fl, String shortName) {
      for (int ii = 1; ii < fl.finish(); ii++) {
         TextEdit<?> te = (TextEdit<?>) fl.at(ii);
         String sn = te.fdes().shortName;
         if (sn == null)
            continue;
         if (shortName.equals(sn) || sn.endsWith("/" + shortName))
            return ii;
      }
      return -1;
   }

   private static boolean hasDummyPlaceholder(FileList fl) {
      for (int ii = 1; ii < fl.finish(); ii++) {
         TextEdit<?> te = (TextEdit<?>) fl.at(ii);
         String sn = te.fdes().shortName;
         if (sn != null && sn.startsWith("dummy"))
            return true;
      }
      return false;
   }
}
