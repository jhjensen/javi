package javi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 port of {@code FileList.main()} test harness.
 *
 * <p>
 * Tests FileList singleton lifecycle: creation via {@code make()},
 * entry count, remove, undo, and cleanup. Ordered because FileList
 * is a singleton and operations depend on prior state.
 * </p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileListJUnitTest {

   private static File testFile1;
   private static File testFile2;

   @BeforeAll
   static void setUp() throws Exception {
      TestInit.init();
      // Create real files so FileList entries have known content
      File testDir = history.Testutil.testDir;
      testFile1 = new File(testDir, "fltest1");
      testFile2 = new File(testDir, "fltest2");
      writeFile(testFile1, "hello");
      writeFile(testFile2, "world");
      // Clean any leftover dmp2 files
      new File(testFile1.getPath() + ".dmp2").delete();
      new File(testFile2.getPath() + ".dmp2").delete();
   }

   @AfterAll
   static void tearDown() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FileList inst = FileList.TestAccess.getInstance();
         if (inst != null) {
            inst.disposeFvc();
            FileList.TestAccess.reset();
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
      // Clean up test files
      testFile1.delete();
      testFile2.delete();
      new File(testFile1.getPath() + ".dmp2").delete();
      new File(testFile2.getPath() + ".dmp2").delete();
   }

   @Test
   @Order(1)
   void makeCreatesFileList() {
      EventQueue.biglock2.lock();
      try {
         String paths = testFile1.getAbsolutePath()
               + "\n" + testFile2.getAbsolutePath();
         FileList.make(paths);

         FileList inst = FileList.TestAccess.getInstance();
         assertNotNull(inst, "FileList.make should create singleton");
         int size = inst.finish();
         // ecache has root element + 2 file entries = 3
         assertTrue(size >= 3,
               "FileList should have at least 3 entries (root + 2 files), got "
                     + size);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   @Order(2)
   void containsNowReportsEntries() {
      EventQueue.biglock2.lock();
      try {
         FileList inst = FileList.TestAccess.getInstance();
         assertNotNull(inst);
         // With root + 2 files, index 2 should exist
         assertTrue(inst.containsNow(2),
               "containsNow(2) should be true for 2 file entries");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   @Order(3)
   void fileEntriesHaveContent() {
      EventQueue.biglock2.lock();
      try {
         FileList inst = FileList.TestAccess.getInstance();
         assertNotNull(inst);
         // Each file has 1 line of content → root + 1 line = finish() == 2
         TextEdit<String> entry1 = inst.at(1);
         assertNotNull(entry1, "entry at index 1 should exist");
         assertEquals(2, entry1.finish(),
               "file entry should have 2 elements (root + 1 content line)");

         TextEdit<String> entry2 = inst.at(2);
         assertNotNull(entry2, "entry at index 2 should exist");
         assertEquals(2, entry2.finish(),
               "file entry should have 2 elements (root + 1 content line)");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   @Order(4)
   void removeAndCheckpoint() {
      EventQueue.biglock2.lock();
      try {
         FileList inst = FileList.TestAccess.getInstance();
         int sizeBefore = inst.finish();
         inst.remove(1, 1);
         inst.checkpoint();
         int sizeAfter = inst.finish();
         assertEquals(sizeBefore - 1, sizeAfter,
               "remove(1,1) should reduce size by 1");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   @Order(5)
   void undoRestoresRemoved() {
      EventQueue.biglock2.lock();
      try {
         FileList inst = FileList.TestAccess.getInstance();
         int sizeBefore = inst.finish();
         inst.undo();
         int sizeAfter = inst.finish();
         assertEquals(sizeBefore + 1, sizeAfter,
               "undo should restore the removed entry");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   private static void writeFile(File f, String content) throws IOException {
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(f), StandardCharsets.UTF_8)) {
         w.write(content);
      }
   }
}
