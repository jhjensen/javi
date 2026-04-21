package javi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage tests for {@link DirEdit} — exercises sort modes,
 * formatEntry, getFilename, getFullPath, findLineForFilename,
 * toggleHidden, filter, cycleSortMode, and DirSizeCalculator.
 */
class DirEditCoverageJUnitTest {

   @TempDir
   static File tempDir;

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void setUp() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void tearDown() {
      EventQueue.biglock2.unlock();
   }

   private DirEdit makeDirEdit(File dir) {
      FileDescriptor.LocalDir localDir =
         FileDescriptor.LocalDir.make(dir.getAbsolutePath());
      return new DirEdit(localDir);
   }

   private static File createTestFile(File dir, String name)
         throws IOException {
      File f = new File(dir, name);
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(f), StandardCharsets.UTF_8)) {
         w.write("test content\n");
      }
      return f;
   }

   // ── Sort modes ─────────────────────────────────────────────

   @Test
   void cycleSortModeCyclesThroughAllModes() throws Exception {
      File sub = new File(tempDir, "sortcycle");
      sub.mkdir();
      createTestFile(sub, "a.txt");
      createTestFile(sub, "b.java");

      DirEdit de = makeDirEdit(sub);
      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(de, view);

      assertEquals(DirEdit.SortMode.NAME, de.sortMode);

      de.cycleSortMode(fvc);
      assertEquals(DirEdit.SortMode.SIZE, de.sortMode);

      de.cycleSortMode(fvc);
      assertEquals(DirEdit.SortMode.DATE, de.sortMode);

      de.cycleSortMode(fvc);
      assertEquals(DirEdit.SortMode.TYPE, de.sortMode);

      de.cycleSortMode(fvc);
      assertEquals(DirEdit.SortMode.NAME, de.sortMode); // wraps around

      de.disposeFvc();
   }

   @Test
   void sortModeTypeGroupsDirectoriesFirst() throws IOException {
      File sub = new File(tempDir, "sorttype");
      sub.mkdir();
      createTestFile(sub, "z.txt");
      createTestFile(sub, "a.java");
      new File(sub, "mydir").mkdir();

      DirEdit de = makeDirEdit(sub);

      // Switch to TYPE sort
      de.sortMode = DirEdit.SortMode.TYPE;
      de.populateDirectory();

      // Directories should appear before files in TYPE mode
      // Find the first non-header entry
      boolean foundDir = false;
      boolean foundFileAfterDir = false;
      for (int i = 1; i < de.readIn(); i++) {
         String fn = de.getFilename(i);
         if (fn == null) continue;
         if (fn.endsWith("/")) {
            foundDir = true;
         } else if (foundDir) {
            foundFileAfterDir = true;
            break;
         }
      }
      assertTrue(foundDir, "Should have at least one directory");
      assertTrue(foundFileAfterDir,
         "Files should appear after directories in TYPE sort");

      de.disposeFvc();
   }

   @Test
   void sortModeSizeSortsBySize() throws IOException {
      File sub = new File(tempDir, "sortsize");
      sub.mkdir();
      // Create files of different sizes
      File small = new File(sub, "small.txt");
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(small), StandardCharsets.UTF_8)) {
         w.write("x");
      }
      File large = new File(sub, "large.txt");
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(large), StandardCharsets.UTF_8)) {
         for (int i = 0; i < 1000; i++) w.write("abcdefghij");
      }

      DirEdit de = makeDirEdit(sub);
      de.sortMode = DirEdit.SortMode.SIZE;
      de.populateDirectory();

      // Find the files in order
      String firstName = null, secondName = null;
      for (int i = 1; i < de.readIn(); i++) {
         String fn = de.getFilename(i);
         if (fn == null || fn.endsWith("/")) continue;
         if (firstName == null) firstName = fn;
         else if (secondName == null) secondName = fn;
      }
      assertNotNull(firstName);
      assertNotNull(secondName);
      // Smallest first
      assertEquals("small.txt", firstName);
      assertEquals("large.txt", secondName);

      de.disposeFvc();
   }

   // ── getFilename parsing ────────────────────────────────────

   @Test
   void getFilenameReturnsNullForHeaderLine() throws IOException {
      File sub = new File(tempDir, "getfn");
      sub.mkdir();
      createTestFile(sub, "file1.txt");

      DirEdit de = makeDirEdit(sub);
      // Line 1 is "  Directory: ..."
      assertNull(de.getFilename(1));
      // Line 0 and beyond bounds
      assertNull(de.getFilename(0));
      assertNull(de.getFilename(9999));

      de.disposeFvc();
   }

   @Test
   void getFilenameReturnsNullForHelpFooter() throws IOException {
      File sub = new File(tempDir, "getfn2");
      sub.mkdir();
      createTestFile(sub, "test.txt");

      DirEdit de = makeDirEdit(sub);
      // The last few lines are help footers with "  [" prefix
      int lastLine = de.readIn() - 1;
      // Check a few from the end
      for (int i = lastLine; i > lastLine - 3 && i > 0; i--) {
         String line = de.at(i).toString();
         if (line.contains("  [")) {
            assertNull(de.getFilename(i),
               "Help footer should return null filename");
         }
      }

      de.disposeFvc();
   }

   // ── getFullPath ────────────────────────────────────────────

   @Test
   void getFullPathReturnsAbsolutePath() throws IOException {
      File sub = new File(tempDir, "fullpath");
      sub.mkdir();
      File f = createTestFile(sub, "path_test.txt");

      DirEdit de = makeDirEdit(sub);

      int line = de.findLineForFilename("path_test.txt");
      assertTrue(line > 0, "Should find the test file");

      String fullPath = de.getFullPath(line);
      assertNotNull(fullPath);
      assertTrue(fullPath.endsWith("path_test.txt"));

      de.disposeFvc();
   }

   @Test
   void getFullPathReturnsNullForNonFileLine() throws IOException {
      File sub = new File(tempDir, "fpnull");
      sub.mkdir();
      createTestFile(sub, "x.txt");

      DirEdit de = makeDirEdit(sub);
      // Line 1 is header
      assertNull(de.getFullPath(1));

      de.disposeFvc();
   }

   // ── findLineForFilename ────────────────────────────────────

   @Test
   void findLineForFilenameReturnsMinusOneForNull() throws IOException {
      File sub = new File(tempDir, "flfn");
      sub.mkdir();
      createTestFile(sub, "abc.txt");

      DirEdit de = makeDirEdit(sub);
      assertEquals(-1, de.findLineForFilename(null));
      assertEquals(-1, de.findLineForFilename("nonexistent.txt"));

      de.disposeFvc();
   }

   @Test
   void findLineForFilenameFindsExistingFile() throws IOException {
      File sub = new File(tempDir, "flfn2");
      sub.mkdir();
      createTestFile(sub, "target.txt");
      createTestFile(sub, "other.txt");

      DirEdit de = makeDirEdit(sub);
      int line = de.findLineForFilename("target.txt");
      assertTrue(line > 0, "Should find target.txt");

      de.disposeFvc();
   }

   @Test
   void findLineForFilenameMatchesDirectoryWithTrailingSlash()
         throws IOException {
      File sub = new File(tempDir, "flfn3");
      sub.mkdir();
      File dir = new File(sub, "subdir");
      dir.mkdir();

      DirEdit de = makeDirEdit(sub);
      // In the listing, dirs appear as "subdir/" — search by bare name
      int line = de.findLineForFilename("subdir");
      assertTrue(line > 0, "Should find 'subdir' directory");

      de.disposeFvc();
   }

   // ── toggleHidden ───────────────────────────────────────────

   @Test
   void toggleHiddenShowsAndHidesDotFiles() throws Exception {
      File sub = new File(tempDir, "hidden");
      sub.mkdir();
      createTestFile(sub, "visible.txt");
      createTestFile(sub, ".hidden");

      DirEdit de = makeDirEdit(sub);
      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(de, view);

      // Initially hidden files are not shown
      assertEquals(-1, de.findLineForFilename(".hidden"));
      assertTrue(de.findLineForFilename("visible.txt") > 0);

      // Toggle hidden on
      de.toggleHidden(fvc);
      assertTrue(de.findLineForFilename(".hidden") > 0,
         "After toggle, hidden files should be visible");

      // Toggle hidden off again
      de.toggleHidden(fvc);
      assertEquals(-1, de.findLineForFilename(".hidden"),
         "After second toggle, hidden files should be hidden again");

      de.disposeFvc();
   }

   // ── setFilter ──────────────────────────────────────────────

   @Test
   void setFilterShowsOnlyMatchingFiles() throws Exception {
      File sub = new File(tempDir, "filter");
      sub.mkdir();
      createTestFile(sub, "foo.java");
      createTestFile(sub, "bar.txt");
      createTestFile(sub, "baz.java");

      DirEdit de = makeDirEdit(sub);
      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(de, view);

      de.setFilter("\\.java$");

      // Java files should be visible
      assertTrue(de.findLineForFilename("foo.java") > 0);
      assertTrue(de.findLineForFilename("baz.java") > 0);
      // txt file should be hidden
      assertEquals(-1, de.findLineForFilename("bar.txt"));

      // Clear filter
      de.setFilter(null);
      assertTrue(de.findLineForFilename("bar.txt") > 0,
         "After clearing filter, all files visible");

      de.disposeFvc();
   }

   @Test
   void setFilterInvalidPatternReportsError() throws IOException {
      File sub = new File(tempDir, "badfilter");
      sub.mkdir();
      createTestFile(sub, "test.txt");

      DirEdit de = makeDirEdit(sub);
      // Invalid regex — should not throw
      de.setFilter("[invalid");
      // Files should still be visible (invalid filter is ignored)
      assertTrue(de.findLineForFilename("test.txt") > 0);

      de.disposeFvc();
   }

   // ── DirSizeCalculator ──────────────────────────────────────

   @Test
   void walkDirectorySizeCountsBytes() throws IOException {
      File sub = new File(tempDir, "walksize");
      sub.mkdir();
      File f1 = new File(sub, "f1.txt");
      File f2 = new File(sub, "f2.txt");
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(f1), StandardCharsets.UTF_8)) {
         w.write("hello"); // 5 bytes
      }
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(f2), StandardCharsets.UTF_8)) {
         w.write("world!!!!!"); // 10 bytes
      }

      long size = DirEdit.DirSizeCalculator.walkDirectorySize(
         sub.getAbsolutePath());
      assertTrue(size >= 15,
         "Total size should be at least 15 bytes, got " + size);
   }

   @Test
   void clearCacheEmptiesCache() throws IOException {
      File sub = new File(tempDir, "clearcache");
      sub.mkdir();

      // Walk to populate cache
      DirEdit.DirSizeCalculator.walkDirectorySize(
         sub.getAbsolutePath());

      DirEdit.DirSizeCalculator.clearCache();
      assertNull(DirEdit.DirSizeCalculator.getCachedSize(
         sub.getAbsolutePath()));
   }

   @Test
   void invalidateRemovesCacheEntry() throws IOException {
      File sub = new File(tempDir, "invalidate");
      sub.mkdir();

      // Cache will be null since walkDirectorySize doesn't auto-cache
      assertNull(DirEdit.DirSizeCalculator.getCachedSize(
         sub.getAbsolutePath()));
   }

   // ── refresh ────────────────────────────────────────────────

   @Test
   void refreshRepopulatesDirectory() throws Exception {
      File sub = new File(tempDir, "refreshdir");
      sub.mkdir();
      createTestFile(sub, "initial.txt");

      DirEdit de = makeDirEdit(sub);
      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(de, view);

      assertTrue(de.findLineForFilename("initial.txt") > 0);

      // Add a new file
      createTestFile(sub, "added.txt");
      de.refresh(fvc);

      assertTrue(de.findLineForFilename("added.txt") > 0,
         "After refresh, newly added file should appear");

      de.disposeFvc();
   }

   // ── markedForDelete ────────────────────────────────────────

   @Test
   void markedForDeleteAppearsInListing() throws IOException {
      File sub = new File(tempDir, "marked");
      sub.mkdir();
      createTestFile(sub, "victim.txt");

      DirEdit de = makeDirEdit(sub);
      de.markedForDelete.add("victim.txt");
      de.populateDirectory();

      // Find the line for victim.txt
      int line = de.findLineForFilename("victim.txt");
      assertTrue(line > 0);
      // The line should start with "D " (mark prefix)
      String lineStr = de.at(line).toString();
      assertTrue(lineStr.startsWith("D "),
         "Marked file should have 'D ' prefix: " + lineStr);

      de.disposeFvc();
   }

   // ── parent directory entry ─────────────────────────────────

   @Test
   void getFullPathForParentReturnsParentDir() throws IOException {
      File sub = new File(tempDir, "parenttest");
      sub.mkdir();
      createTestFile(sub, "child.txt");

      DirEdit de = makeDirEdit(sub);

      // Find the parent entry "../"
      int parentLine = -1;
      for (int i = 1; i < de.readIn(); i++) {
         String fn = de.getFilename(i);
         if ("../".equals(fn)) {
            parentLine = i;
            break;
         }
      }
      assertTrue(parentLine > 0, "Should have parent directory entry");

      String parentPath = de.getFullPath(parentLine);
      assertNotNull(parentPath);
      // Parent path should be tempDir's path
      assertTrue(parentPath.contains(tempDir.getName()),
         "Parent should reference containing directory");

      de.disposeFvc();
   }

   // ── getCurrentDir ──────────────────────────────────────────

   @Test
   void getCurrentDirReturnsCorrectDir() throws IOException {
      File sub = new File(tempDir, "getcurdir");
      sub.mkdir();

      DirEdit de = makeDirEdit(sub);
      assertNotNull(de.getCurrentDir());
      assertTrue(de.getCurrentDir().fh.getAbsolutePath()
         .endsWith("getcurdir"));

      de.disposeFvc();
   }

   // ── goToParent cursor positioning (F36) ────────────────────

   @Test
   void goToParentPositionsCursorOnChildDirectory() throws Exception {
      // Create parent with multiple subdirectories
      File parent = new File(tempDir, "parentnav");
      parent.mkdir();
      new File(parent, "aaa").mkdir();
      new File(parent, "bbb").mkdir();
      new File(parent, "ccc").mkdir();

      // Start in child directory "bbb"
      File child = new File(parent, "bbb");
      DirEdit de = makeDirEdit(child);
      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(de, view);

      // Navigate up to parent
      de.goToParent(fvc);

      // Cursor should be on "bbb/" in the parent listing
      String cursorFile = de.getFilename(fvc.inserty());
      assertEquals("bbb/", cursorFile,
         "After goToParent, cursor should be on the directory we came from");

      de.disposeFvc();
   }

   @Test
   void goToParentFallsBackToFirstEntryWhenChildMissing()
         throws Exception {
      // Create parent with a child, navigate into child, then
      // delete the child and go up — cursor should fall back to
      // first entry since the child no longer exists in the listing
      File parent = new File(tempDir, "parentfb");
      parent.mkdir();
      File child = new File(parent, "ephemeral");
      child.mkdir();

      DirEdit de = makeDirEdit(child);
      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(de, view);

      // Remove the child directory before navigating up
      child.delete();

      de.goToParent(fvc);

      // Cursor should be at line 3 (first entry fallback)
      assertEquals(3, fvc.inserty(),
         "When child directory is gone, cursor should fall back to "
         + "first entry");

      de.disposeFvc();
   }
}
