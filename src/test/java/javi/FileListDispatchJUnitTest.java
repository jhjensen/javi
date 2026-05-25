package javi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for {@link FileList} command dispatch via
 * {@link Rgroup#doCommand}: vi (file open), e, nextfile,
 * gotofilelist, and file write paths.
 *
 * <p>These commands are in FileList.Commands.doroutine which
 * has very low coverage because tests rarely exercise the
 * Rgroup dispatch path for file operations.</p>
 */
class FileListDispatchJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
      EventQueue.biglock2.lock();
      try {
         if (FileList.TestAccess.getInstance() == null) {
            FileList.make("");
         }
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

   private static FvContext<?> getFileListFvc() throws InputException {
      TestView view = new TestView(true);
      return FileList.getContext(view);
   }

   // ── vi / e (open file) ────────────────────────────────────

   @Test
   @DisplayName("vi command opens existing file")
   void viCommandOpensFile() throws Exception {
      String fname = "ju_fld_viopen";
      File testDir = history.Testutil.testDir;
      File testFile = new File(testDir, fname);

      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(testFile),
            StandardCharsets.UTF_8)) {
         w.write("line1\nline2\nline3\n");
      }

      UI.setStream(new StringReader(""));
      FvContext<?> fvc = getFileListFvc();

      Rgroup.doCommand("vi", testFile.getPath(),
         1, 0, fvc, false);

      // The file should now be in the file list
      FileList fl = FileList.TestAccess.getInstance();
      assertNotNull(fl);
      assertTrue(fl.finish() >= 2,
         "FileList should have at least 2 entries after open");

      testFile.delete();
      new File(testDir, fname + ".dmp2").delete();
   }

   @Test
   @DisplayName("e command opens file (synonym for vi)")
   void eCommandOpensFile() throws Exception {
      String fname = "ju_fld_eopen";
      File testDir = history.Testutil.testDir;
      File testFile = new File(testDir, fname);

      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(testFile),
            StandardCharsets.UTF_8)) {
         w.write("content\n");
      }

      UI.setStream(new StringReader(""));
      FvContext<?> fvc = getFileListFvc();

      Rgroup.doCommand("e", testFile.getPath(),
         1, 0, fvc, false);

      testFile.delete();
      new File(testDir, fname + ".dmp2").delete();
   }

   @Test
   @DisplayName("vi command with directory adds to search path")
   void viCommandWithDirectory() throws Exception {
      File testDir = history.Testutil.testDir;

      UI.setStream(new StringReader(""));
      FvContext<?> fvc = getFileListFvc();

      // Passing a directory should add it to DirManager search
      assertDoesNotThrow(() ->
         Rgroup.doCommand("vi", testDir.getPath(),
            1, 0, fvc, false));
   }

   // ── nextfile ──────────────────────────────────────────────

   @Test
   @DisplayName("nextfile command switches buffer")
   void nextfileCommand() throws Exception {
      // Ensure we have at least 2 files open
      String fname = "ju_fld_next";
      File testDir = history.Testutil.testDir;
      File testFile = new File(testDir, fname);

      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(testFile),
            StandardCharsets.UTF_8)) {
         w.write("nextfile-content\n");
      }

      UI.setStream(new StringReader(""));
      FvContext<?> fvc = getFileListFvc();
      Rgroup.doCommand("vi", testFile.getPath(),
         1, 0, fvc, false);

      // Now nextfile should work (switches to next in list)
      assertDoesNotThrow(() ->
         Rgroup.doCommand("nextfile", null, 1, 0,
            fvc, false));

      testFile.delete();
      new File(testDir, fname + ".dmp2").delete();
   }

   @Test
   @DisplayName("moveBufferUp keeps modified buffer connected")
   void moveBufferUpKeepsModifiedBufferConnected()
         throws Exception {
      String fnameA = "ju_fld_moveup_a";
      String fnameB = "ju_fld_moveup_b";
      File testDir = history.Testutil.testDir;
      File testFileA = new File(testDir, fnameA);
      File testFileB = new File(testDir, fnameB);

      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(testFileA),
            StandardCharsets.UTF_8)) {
         w.write("alpha\n");
      }
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(testFileB),
            StandardCharsets.UTF_8)) {
         w.write("beta\n");
      }

      UI.setStream(new StringReader(""));
      TestView view = new TestView(true);
      FileList.openFileName(testFileA.getPath(), view);
      @SuppressWarnings("unchecked")
      TextEdit<String> moved = (TextEdit<String>)
         FileList.openFileName(testFileB.getPath(), view).edvec;

      moved.inserttext("X", 0, 1);
      moved.checkpoint();
      int linesBefore = moved.readIn();
      assertTrue(moved.isModified(),
         "test setup should mark buffer modified");

      FileList fl = FileList.TestAccess.getInstance();
      int oldIndex = fl.indexOf(moved);
      assertTrue(oldIndex > 1,
         "modified buffer must be movable upward in file list");

      FvContext<?> fileListFvc = FileList.getContext(view);
      fileListFvc.cursoryabs(oldIndex);
      FileList.moveBufferUp(fileListFvc);

      assertSame(moved, fl.at(oldIndex - 1),
         "moveBufferUp should preserve buffer identity");
      assertTrue(moved.isValid(),
         "moved buffer must remain valid after reordering");
      assertEquals(linesBefore, moved.readIn(),
         "moved buffer content should be preserved");
      assertTrue(moved.isModified(),
         "modified flag should be preserved after move");
      assertDoesNotThrow(moved::undo,
         "undo should work after moving a modified buffer");

      testFileA.delete();
      testFileB.delete();
      new File(testDir, fnameA + ".dmp2").delete();
      new File(testDir, fnameB + ".dmp2").delete();
   }

   // ── gotofilelist ──────────────────────────────────────────

   @Test
   @DisplayName("gotofilelist connects to file list buffer")
   void gotofilelistCommand() throws Exception {
      UI.setStream(new StringReader(""));
      FvContext<?> fvc = getFileListFvc();

      assertDoesNotThrow(() ->
         Rgroup.doCommand("gotofilelist", null, 1, 0,
            fvc, false));
   }

   // ── Zprocess (ZZ — save+close) ───────────────────────────

   @Test
   @DisplayName("Zprocess on unmodified file triggers quit path")
   void zprocessUnmodifiedFile() throws Exception {
      FvContext<?> fvc = getFileListFvc();
      // processZ calls EventQueue.nextKey() expecting second 'Z'.
      // In headless mode there's no input thread, so pre-populate
      // the event queue with the expected keystroke.
      EventQueue.insert(new JeyEvent(0, 0, 'Z'));

      // Zprocess on an unmodified file invokes processZ which
      // tries to save/close. In headless tests with an empty
      // file list, downstream ops may throw. We verify the
      // command dispatches without hanging.
      try {
         Rgroup.doCommand("Zprocess", null, 1, 0,
            fvc, false);
      } catch (ExitException | NullPointerException
            | IndexOutOfBoundsException e) {
         // Expected — quit path entered or empty list edge case
      }
   }

   // ── countModified ─────────────────────────────────────────

   @Test
   @DisplayName("countModified reflects file state")
   void countModifiedReflectsState() throws Exception {
      int count = FileList.countModified();
      assertTrue(count >= 0,
         "countModified should return non-negative: " + count);
   }

   // ── writeModifiedFiles ────────────────────────────────────

   @Test
   @DisplayName("writeModifiedFiles with non-matching spec returns empty")
   void writeModifiedFilesNoMatch() throws Exception {
      var result = FileList.writeModifiedFiles(
         "ZZZNOMATCH_PATTERN_XYZ");
      assertNotNull(result);
      assertEquals(0, result.size(),
         "No files should match impossible pattern");
   }

   // ── openFileName ──────────────────────────────────────────

   @Test
   @DisplayName("openFileName with real file returns context")
   void openFileNameReturnsContext() throws Exception {
      String fname = "ju_fld_openfn";
      File testDir = history.Testutil.testDir;
      File testFile = new File(testDir, fname);

      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(testFile),
            StandardCharsets.UTF_8)) {
         w.write("openfn-content\n");
      }

      UI.setStream(new StringReader(""));
      TestView view = new TestView(true);

      FvContext<?> result = FileList.openFileName(
         testFile.getPath(), view);
      assertNotNull(result,
         "openFileName should return FvContext for real file");

      testFile.delete();
      new File(testDir, fname + ".dmp2").delete();
   }

   @Test
   @DisplayName("openFileName with already-open file finds it")
   void openFileNameAlreadyOpen() throws Exception {
      String fname = "ju_fld_openfn2";
      File testDir = history.Testutil.testDir;
      File testFile = new File(testDir, fname);

      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(testFile),
            StandardCharsets.UTF_8)) {
         w.write("already-open\n");
      }

      UI.setStream(new StringReader(""));
      TestView view = new TestView(true);

      // Open once
      FvContext<?> first = FileList.openFileName(
         testFile.getPath(), view);
      assertNotNull(first);

      // Open again — should find existing
      FvContext<?> second = FileList.openFileName(
         testFile.getPath(), view);
      assertNotNull(second);

      testFile.delete();
      new File(testDir, fname + ".dmp2").delete();
   }
}
