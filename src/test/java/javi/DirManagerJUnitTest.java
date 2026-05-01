package javi;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link DirManager} Phase 5 search path integration.
 */
class DirManagerJUnitTest {

   @TempDir
   static File tempDir;

   private DirManager dm;

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void setUp() {
      EventQueue.biglock2.lock();
      dm = DirManager.getInstance();
   }

   @AfterEach
   void tearDown() {
      EventQueue.biglock2.unlock();
   }

   // --- Search path tests ---

   @Test
   void singletonNotNull() {
      assertNotNull(dm);
      assertTrue(dm == DirManager.getInstance(),
         "getInstance returns same instance");
   }

   @Test
   void addAndRemoveSearchDir() {
      File sub = new File(tempDir, "searchtest");
      sub.mkdirs();
      FileDescriptor.LocalDir dir =
         FileDescriptor.LocalDir.make(sub.getAbsolutePath());

      int before = dm.searchPathSize();
      assertTrue(dm.addSearchDir(dir));
      assertEquals(before + 1, dm.searchPathSize());
      assertFalse(dm.addSearchDir(dir), "duplicate add returns false");
      assertEquals(before + 1, dm.searchPathSize());

      assertTrue(dm.removeSearchDir(dir));
      assertEquals(before, dm.searchPathSize());
   }

   @Test
   void isInSearchPathLocalDir() {
      File sub = new File(tempDir, "spcheck");
      sub.mkdirs();
      FileDescriptor.LocalDir dir =
         FileDescriptor.LocalDir.make(sub.getAbsolutePath());

      assertFalse(dm.isInSearchPath(dir));
      dm.addSearchDir(dir);
      assertTrue(dm.isInSearchPath(dir));
      dm.removeSearchDir(dir);
      assertFalse(dm.isInSearchPath(dir));
   }

   @Test
   void isInSearchPathFile() {
      File sub = new File(tempDir, "filecheck");
      sub.mkdirs();
      FileDescriptor.LocalDir dir =
         FileDescriptor.LocalDir.make(sub.getAbsolutePath());

      assertFalse(dm.isInSearchPath(sub));
      dm.addSearchDir(dir);
      assertTrue(dm.isInSearchPath(sub));
      dm.removeSearchDir(dir);
   }

   @Test
   void isInSearchPathFileNotDir() {
      // Regular files never in search path
      File f = new File(tempDir, "notadir.txt");
      try {
         f.createNewFile();
      } catch (Exception e) {
         // ignore
      }
      assertFalse(dm.isInSearchPath(f));
   }

   @Test
   void toggleSearchPath() {
      File sub = new File(tempDir, "toggletest");
      sub.mkdirs();
      FileDescriptor.LocalDir dir =
         FileDescriptor.LocalDir.make(sub.getAbsolutePath());

      // Remove if previously added by another test
      dm.removeSearchDir(dir);

      assertTrue(dm.toggleSearchPath(dir), "toggle on returns true");
      assertTrue(dm.isInSearchPath(dir));
      assertFalse(dm.toggleSearchPath(dir), "toggle off returns false");
      assertFalse(dm.isInSearchPath(dir));
   }

   @Test
   void getSearchPathReturnsDefensiveCopy() {
      int before = dm.getSearchPath().size();
      File sub = new File(tempDir, "defensivecheck");
      sub.mkdirs();
      FileDescriptor.LocalDir dir =
         FileDescriptor.LocalDir.make(sub.getAbsolutePath());

      dm.addSearchDir(dir);
      var list = dm.getSearchPath();
      assertEquals(before + 1, list.size());

      // Mutating returned list should not affect DirManager
      list.clear();
      assertEquals(before + 1, dm.searchPathSize());

      dm.removeSearchDir(dir);
   }

   // --- Search path display tests ---

   @Test
   void showSearchPathIncludesDirectories() {
      File sub = new File(tempDir, "showsp");
      sub.mkdirs();
      FileDescriptor.LocalDir dir =
         FileDescriptor.LocalDir.make(sub.getAbsolutePath());

      dm.addSearchDir(dir);
      try {
         dm.showSearchPath();
         int size = dm.readIn();
         assertTrue(size > 1,
            "buffer should have content after showSearchPath");
         boolean found = false;
         for (int i = 1; i < size; i++) {
            String line = dm.at(i).toString();
            if (line.contains(sub.getName())) {
               found = true;
               break;
            }
         }
         assertTrue(found,
            "showSearchPath should include the added directory");
      } finally {
         dm.removeSearchDir(dir);
      }
   }

   @Test
   void showSearchPathEmptyMessage() {
      // Clear all search dirs for this test
      ArrayList<FileDescriptor.LocalDir> saved = dm.getSearchPath();
      for (FileDescriptor.LocalDir d : saved)
         dm.removeSearchDir(d);

      dm.showSearchPath();
      boolean foundEmpty = false;
      for (int i = 1; i < dm.readIn(); i++) {
         if (dm.at(i).toString().contains("empty")) {
            foundEmpty = true;
            break;
         }
      }
      assertTrue(foundEmpty,
         "showSearchPath with no dirs should show empty message");

      // Restore
      for (FileDescriptor.LocalDir d : saved)
         dm.addSearchDir(d);
   }

   // --- File-find backend tests ---

   @Test
   void initSearchFindsFileInSearchPath() throws IOException {
      File sub = new File(tempDir, "findtest");
      sub.mkdirs();
      new File(sub, "target.txt").createNewFile();
      FileDescriptor.LocalDir dir =
         FileDescriptor.LocalDir.make(sub.getAbsolutePath());

      dm.addSearchDir(dir);
      try {
         dm.initSearch("target.txt");
         FileDescriptor.LocalFile found = dm.findNextFile();
         assertNotNull(found, "should find target.txt in search path");
         assertTrue(found.shortName.endsWith("target.txt"));
      } finally {
         dm.removeSearchDir(dir);
      }
   }

   @Test
   void findNextFileReturnsNullForMissing() {
      dm.initSearch("definitely_nonexistent_xyz123.txt");
      FileDescriptor.LocalFile found = dm.findNextFile();
      assertNull(found, "should return null for nonexistent file");
   }

   @Test
   void initSearchRFindsRegexMatch() throws IOException {
      File sub = new File(tempDir, "regextest");
      sub.mkdirs();
      new File(sub, "alpha.java").createNewFile();
      new File(sub, "beta.txt").createNewFile();
      FileDescriptor.LocalDir dir =
         FileDescriptor.LocalDir.make(sub.getAbsolutePath());

      dm.addSearchDir(dir);
      try {
         dm.initSearch(".*\\.java");
         assertTrue(dm.initSearchR(), "valid regex should compile");
         FileDescriptor found = dm.findNextFileR();
         assertNotNull(found, "should find .java file via regex");
         assertTrue(found.shortName.endsWith(".java"));
      } finally {
         dm.removeSearchDir(dir);
      }
   }

   @Test
   void initSearchRFailsOnBadPattern() {
      dm.initSearch("[invalid");
      assertFalse(dm.initSearchR(), "bad regex should return false");
   }

   @Test
   void fileListReturnsMatchingFiles() throws IOException {
      File sub = new File(tempDir, "filelisttest");
      sub.mkdirs();
      new File(sub, "one.java").createNewFile();
      new File(sub, "two.java").createNewFile();
      new File(sub, "three.txt").createNewFile();
      FileDescriptor.LocalDir dir =
         FileDescriptor.LocalDir.make(sub.getAbsolutePath());

      dm.addSearchDir(dir);
      try {
         FilenameFilter javaFilter = (d, name) -> name.endsWith(".java");
         ArrayList<FileDescriptor.LocalFile> result = dm.fileList(javaFilter);
         assertTrue(result.size() >= 2,
            "should find at least 2 .java files, got " + result.size());
      } finally {
         dm.removeSearchDir(dir);
      }
   }

   @Test
   void flushCacheDoesNotThrow() {
      // flushCache should be safe to call regardless of state
      dm.flushCache();
   }

   @Test
   void fileListHandlesNonExistentDirectory() {
      // B10: listDes() returns null for non-existent dirs — should not NPE
      File ghost = new File(tempDir, "nonexistent_dir_xyz");
      FileDescriptor.LocalDir dir =
         FileDescriptor.LocalDir.make(ghost.getAbsolutePath());

      dm.addSearchDir(dir);
      try {
         FilenameFilter anyFilter = (d, name) -> true;
         ArrayList<FileDescriptor.LocalFile> result =
            dm.fileList(anyFilter);
         assertNotNull(result, "fileList should return non-null");
         // Note: result may be non-empty because fileList() iterates ALL
         // search dirs (not just the ghost). The key assertion is no NPE.
      } finally {
         dm.removeSearchDir(dir);
      }
   }

   @Test
   void globalgrepReturnsTextEdit() throws IOException {
      File sub = new File(tempDir, "greptest");
      sub.mkdirs();
      File tf = new File(sub, "searchme.txt");
      java.io.FileWriter fw = new java.io.FileWriter(tf);
      fw.write("line one\nfindable needle here\nline three\n");
      fw.close();
      FileDescriptor.LocalDir dir =
         FileDescriptor.LocalDir.make(sub.getAbsolutePath());

      dm.addSearchDir(dir);
      try {
         TextEdit<Position> results = dm.globalgrep("needle");
         assertNotNull(results, "globalgrep should return non-null");
      } finally {
         dm.removeSearchDir(dir);
      }
   }

   // --- Search path size tracking ---

   @Test
   void searchPathSizeTracksAddRemove() {
      File sub = new File(tempDir, "sizetrack");
      sub.mkdirs();
      FileDescriptor.LocalDir dir =
         FileDescriptor.LocalDir.make(sub.getAbsolutePath());

      int before = dm.searchPathSize();
      dm.addSearchDir(dir);
      assertEquals(before + 1, dm.searchPathSize());
      dm.removeSearchDir(dir);
      assertEquals(before, dm.searchPathSize());
   }

   // --- compressPaths tests ---

   @Test
   void compressPathsEmpty() {
      assertTrue(DirManager.compressPaths(new ArrayList<>()).isEmpty());
      assertTrue(DirManager.compressPaths(null).isEmpty());
   }

   @Test
   void compressPathsSinglePath() {
      String home = System.getProperty("user.home");
      List<String> result = DirManager.compressPaths(
         List.of(home + "/projects/myapp"));
      assertEquals(1, result.size());
      assertEquals("~/projects/myapp", result.get(0));
   }

   @Test
   void compressPathsGroupsSameParent() {
      String home = System.getProperty("user.home");
      List<String> paths = List.of(
         home + "/gtools/blbrd_mitigate",
         home + "/gtools/blbrd_common",
         home + "/gtools/blbrd_ng");
      List<String> result = DirManager.compressPaths(paths);
      assertEquals(4, result.size());
      assertEquals("~/gtools/", result.get(0));
      assertEquals("  blbrd_common/", result.get(1));
      assertEquals("  blbrd_mitigate/", result.get(2));
      assertEquals("  blbrd_ng/", result.get(3));
   }

   @Test
   void compressPathsMixedParents() {
      String home = System.getProperty("user.home");
      List<String> paths = List.of(
         home + "/gtools/blbrd_mitigate",
         home + "/gtools/blbrd_common",
         home + "/javi");
      List<String> result = DirManager.compressPaths(paths);
      assertEquals(4, result.size());
      assertEquals("~/javi", result.get(0));
      assertEquals("~/gtools/", result.get(1));
      assertEquals("  blbrd_common/", result.get(2));
      assertEquals("  blbrd_mitigate/", result.get(3));
   }

   @Test
   void compressPathsNonHomePaths() {
      List<String> paths = List.of(
         "/opt/tools/a",
         "/opt/tools/b",
         "/var/log");
      List<String> result = DirManager.compressPaths(paths);
      assertEquals(4, result.size());
      assertEquals("/opt/tools/", result.get(0));
      assertEquals("  a/", result.get(1));
      assertEquals("  b/", result.get(2));
      assertEquals("/var/log", result.get(3));
   }

   @Test
   void compressPathsPreservesOrder() {
      String home = System.getProperty("user.home");
      List<String> paths = List.of(
         home + "/alpha",
         home + "/gtools/one",
         home + "/beta",
         home + "/gtools/two");
      List<String> result = DirManager.compressPaths(paths);
      // Sorted: ~/alpha, ~/beta under ~; ~/gtools/one, ~/gtools/two
      assertEquals(6, result.size());
      assertEquals("~/", result.get(0));
      assertEquals("  alpha/", result.get(1));
      assertEquals("  beta/", result.get(2));
      assertEquals("~/gtools/", result.get(3));
      assertEquals("  one/", result.get(4));
      assertEquals("  two/", result.get(5));
   }

   @Test
   void compressPathsSortsOutput() {
      String home = System.getProperty("user.home");
      List<String> paths = List.of(
         home + "/gtools/zebra",
         home + "/gtools/alpha",
         home + "/abc");
      List<String> result = DirManager.compressPaths(paths);
      assertEquals(4, result.size());
      assertEquals("~/abc", result.get(0));
      assertEquals("~/gtools/", result.get(1));
      assertEquals("  alpha/", result.get(2));
      assertEquals("  zebra/", result.get(3));
   }
}
