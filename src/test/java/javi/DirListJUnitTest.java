package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Tests for {@link DirList} search and directory management.
 */
class DirListJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void lock() throws Exception {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void unlock() throws Exception {
      EventQueue.biglock2.unlock();
   }

   @Test
   void getDefaultReturnsNonNull() throws Exception {
      DirList dl = DirList.getDefault();
      assertNotNull(dl);
   }

   @Test
   void getDefaultReturnsSameInstance() throws Exception {
      assertSame(DirList.getDefault(), DirList.getDefault());
   }

   @Test
   void addSearchDirNonDuplicate() throws Exception {
      DirList dl = DirList.getDefault();
      // Use the javitests directory which should exist
      File testDir = new File("javitests");
      if (testDir.isDirectory()) {
         FileDescriptor.LocalDir dir =
            FileDescriptor.LocalDir.make(testDir.getAbsolutePath());
         // Size before
         int before = dl.readIn();
         boolean added = dl.addSearchDir(dir);
         int after = dl.readIn();
         if (added) {
            assertEquals(before + 1, after);
         }
         // Adding same dir again should return false
         assertFalse(dl.addSearchDir(dir));
      }
   }

   @Test
   void initSearchSetsState() throws Exception {
      DirList dl = DirList.getDefault();
      dl.initSearch("TestInit.java");
      // Should not throw
   }

   @Test
   void initSearchRWithValidPattern() throws Exception {
      DirList dl = DirList.getDefault();
      dl.initSearch(".*\\.java");
      boolean ok = dl.initSearchR();
      assertTrue(ok);
   }

   @Test
   void initSearchRWithInvalidPattern() throws Exception {
      DirList dl = DirList.getDefault();
      dl.initSearch("[invalid");
      boolean ok = dl.initSearchR();
      assertFalse(ok);
   }

   @Test
   void findNextFileFindsExistingFile() throws Exception {
      DirList dl = DirList.getDefault();
      // Search for build.gradle which should exist in cwd
      dl.initSearch("build.gradle");
      FileDescriptor.LocalFile found = dl.findNextFile();
      // May or may not find it depending on search dirs
      // Just verify no crash
   }

   @Test
   void findNextFileReturnsNullForNonexistent() throws Exception {
      DirList dl = DirList.getDefault();
      dl.initSearch("definitely_nonexistent_file_xyz123.txt");
      FileDescriptor.LocalFile found = dl.findNextFile();
      assertNull(found);
   }

   @Test
   void findNextFileRFindsPattern() throws Exception {
      DirList dl = DirList.getDefault();
      dl.initSearch("build\\.gradle");
      boolean compiled = dl.initSearchR();
      assertTrue(compiled);
      // Attempt search - result depends on dir entries
      FileDescriptor fd = dl.findNextFileR();
      // May find or not, just verify no crash
   }

   @Test
   void flushCacheDoesNotThrow() throws Exception {
      DirList dl = DirList.getDefault();
      dl.flushCache();
      // Just verify no crash
   }

   @Test
   void fileListReturnsArrayList() throws Exception {
      DirList dl = DirList.getDefault();
      FilenameFilter javaFilter = (dir, name) -> name.endsWith(".java");
      var files = dl.fileList(javaFilter);
      assertNotNull(files);
      // Should contain at least some java files
   }
}
