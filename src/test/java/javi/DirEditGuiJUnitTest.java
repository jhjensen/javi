package javi;

import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for {@link DirEdit} directory browser in the AWT context.
 *
 * <p>Exercises DirEdit connected to the real OldView/AWT rendering
 * pipeline: opening directories, rendering entries through the canvas,
 * sort mode cycling, hidden file toggle, parent navigation, filtering,
 * and cursor-dependent operations.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class DirEditGuiJUnitTest {

   private static Robot robot;
   private static View oldView;
   private static FvContext<?> originalFvc;

   @TempDir
   static File tempDir;

   @BeforeAll
   static void initJavi() throws Exception {
      if (Rgroup.bindingLookup("persistfile") == null) {
         EventQueue.biglock2.lock();
         try {
            Class.forName("javi.TextEdit");
            EditTester1.TestCircBuffer.initCmd();
            DirManager.getInstance();
            FileList.make("");
            Javi.initToUi();
            Javi.initPostUi();
            Command.doneInit();
         } finally {
            EventQueue.biglock2.unlock();
         }
         Thread.sleep(500);
      }
      robot = BasicRobot.robotWithCurrentAwtHierarchy();
      EventQueue.biglock2.lock();
      try {
         originalFvc = FvContext.getCurrFvc();
         oldView = originalFvc.vi;
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   /**
    * Disposes a DirEdit and reconnects the view to the original buffer.
    * Must be called under biglock2.
    */
   private static void closeDirEdit(DirEdit de) throws Exception {
      // Reconnect the view to the original buffer before disposing
      if (null != originalFvc && null != originalFvc.edvec) {
         FvContext.connectFv(originalFvc.edvec, oldView);
      }
      de.disposeFvc();
   }

   // ── Helpers ──────────────────────────────────────────────────

   private static File createTestFile(File dir, String name)
         throws IOException {
      File f = new File(dir, name);
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(f), StandardCharsets.UTF_8)) {
         w.write("test content for " + name + "\n");
      }
      return f;
   }

   private static File createTestFile(File dir, String name, int size)
         throws IOException {
      File f = new File(dir, name);
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(f), StandardCharsets.UTF_8)) {
         for (int i = 0; i < size; i++)
            w.write('x');
      }
      return f;
   }

   private static DirEdit makeDirEdit(File dir) {
      FileDescriptor.LocalDir localDir =
         FileDescriptor.LocalDir.make(dir.getAbsolutePath());
      return new DirEdit(localDir);
   }

   private static void setupTestDirectory(File dir) throws IOException {
      createTestFile(dir, "alpha.txt");
      createTestFile(dir, "beta.java");
      createTestFile(dir, "gamma.py");
      new File(dir, "subdir").mkdir();
      new File(dir, "another_dir").mkdir();
   }

   // ── Open directory in GUI ────────────────────────────────────

   @Test
   void t01_openDirectoryCreatesPopulatedBuffer() throws Exception {
      File testDir = new File(tempDir, "t01");
      testDir.mkdir();
      setupTestDirectory(testDir);

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         assertNotNull(fvc, "DirEdit FvContext must not be null");
         assertTrue(fvc.edvec instanceof DirEdit,
            "edvec must be DirEdit instance");

         DirEdit de = (DirEdit) fvc.edvec;
         assertTrue(de.readIn() > 3,
            "DirEdit must have header + entries, got " + de.readIn());

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t02_headerShowsDirectoryPath() throws Exception {
      File testDir = new File(tempDir, "t02");
      testDir.mkdir();
      createTestFile(testDir, "file.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         String header = de.at(1).toString();
         assertTrue(header.contains("Directory:"),
            "First line must contain 'Directory:': " + header);
         assertTrue(header.contains("t02"),
            "Header must contain dir name 't02': " + header);

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t03_fileEntriesFormatted() throws Exception {
      File testDir = new File(tempDir, "t03");
      testDir.mkdir();
      createTestFile(testDir, "hello.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         boolean found = false;
         for (int i = 1; i < de.readIn(); i++) {
            String fn = de.getFilename(i);
            if ("hello.txt".equals(fn)) {
               String line = de.at(i).toString();
               assertTrue(line.contains("-r"),
                  "File entry must show permissions: " + line);
               found = true;
               break;
            }
         }
         assertTrue(found, "Must find hello.txt in directory listing");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t04_directoryEntriesHaveTrailingSlash() throws Exception {
      File testDir = new File(tempDir, "t04");
      testDir.mkdir();
      new File(testDir, "mysubdir").mkdir();

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         boolean found = false;
         for (int i = 1; i < de.readIn(); i++) {
            String fn = de.getFilename(i);
            if (null != fn && fn.equals("mysubdir/")) {
               found = true;
               break;
            }
         }
         assertTrue(found, "Directory entry must end with '/'");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t05_parentEntryExists() throws Exception {
      File testDir = new File(tempDir, "t05");
      testDir.mkdir();
      createTestFile(testDir, "x.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         boolean found = false;
         for (int i = 1; i < de.readIn(); i++) {
            String fn = de.getFilename(i);
            if ("../".equals(fn)) {
               found = true;
               break;
            }
         }
         assertTrue(found, "Parent entry '../' must exist");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Sort mode cycling ────────────────────────────────────────

   @Test
   void t06_sortModeCyclesInGui() throws Exception {
      File testDir = new File(tempDir, "t06");
      testDir.mkdir();
      createTestFile(testDir, "a.txt", 100);
      createTestFile(testDir, "b.java", 200);
      createTestFile(testDir, "c.py", 50);

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         assertEquals(DirEdit.SortMode.NAME, de.sortMode);

         de.cycleSortMode(fvc);
         assertEquals(DirEdit.SortMode.SIZE, de.sortMode);

         de.cycleSortMode(fvc);
         assertEquals(DirEdit.SortMode.DATE, de.sortMode);

         de.cycleSortMode(fvc);
         assertEquals(DirEdit.SortMode.TYPE, de.sortMode);

         de.cycleSortMode(fvc);
         assertEquals(DirEdit.SortMode.NAME, de.sortMode);

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t07_sortByNameOrdersAlphabetically() throws Exception {
      File testDir = new File(tempDir, "t07");
      testDir.mkdir();
      createTestFile(testDir, "cherry.txt");
      createTestFile(testDir, "apple.txt");
      createTestFile(testDir, "banana.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         // Collect filenames in order (skip header, blank, parent)
         java.util.List<String> names = new java.util.ArrayList<>();
         for (int i = 1; i < de.readIn(); i++) {
            String fn = de.getFilename(i);
            if (null != fn && !"../".equals(fn))
               names.add(fn);
         }

         assertTrue(names.indexOf("apple.txt")
            < names.indexOf("banana.txt"),
            "apple before banana");
         assertTrue(names.indexOf("banana.txt")
            < names.indexOf("cherry.txt"),
            "banana before cherry");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t08_sortBySizeOrdersBySizeInGui() throws Exception {
      File testDir = new File(tempDir, "t08");
      testDir.mkdir();
      createTestFile(testDir, "small.txt", 10);
      createTestFile(testDir, "medium.txt", 500);
      createTestFile(testDir, "large.txt", 2000);

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         // Switch to SIZE sort
         de.cycleSortMode(fvc);
         assertEquals(DirEdit.SortMode.SIZE, de.sortMode);

         java.util.List<String> names = new java.util.ArrayList<>();
         for (int i = 1; i < de.readIn(); i++) {
            String fn = de.getFilename(i);
            if (null != fn && !"../".equals(fn))
               names.add(fn);
         }

         assertTrue(names.indexOf("small.txt")
            < names.indexOf("medium.txt"),
            "small before medium in size sort");
         assertTrue(names.indexOf("medium.txt")
            < names.indexOf("large.txt"),
            "medium before large in size sort");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t09_sortByTypeGroupsByExtension() throws Exception {
      File testDir = new File(tempDir, "t09");
      testDir.mkdir();
      createTestFile(testDir, "z.py");
      createTestFile(testDir, "a.java");
      createTestFile(testDir, "m.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         // Cycle to TYPE (NAME→SIZE→DATE→TYPE)
         de.cycleSortMode(fvc);
         de.cycleSortMode(fvc);
         de.cycleSortMode(fvc);
         assertEquals(DirEdit.SortMode.TYPE, de.sortMode);

         java.util.List<String> names = new java.util.ArrayList<>();
         for (int i = 1; i < de.readIn(); i++) {
            String fn = de.getFilename(i);
            if (null != fn && !"../".equals(fn))
               names.add(fn);
         }

         // java < py < txt alphabetically by extension
         assertTrue(names.indexOf("a.java")
            < names.indexOf("z.py"),
            "java ext before py ext");
         assertTrue(names.indexOf("z.py")
            < names.indexOf("m.txt"),
            "py ext before txt ext");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Hidden files toggle ──────────────────────────────────────

   @Test
   void t10_hiddenFilesHiddenByDefault() throws Exception {
      File testDir = new File(tempDir, "t10");
      testDir.mkdir();
      createTestFile(testDir, "visible.txt");
      createTestFile(testDir, ".hidden");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         boolean foundHidden = false;
         for (int i = 1; i < de.readIn(); i++) {
            String fn = de.getFilename(i);
            if (".hidden".equals(fn)) {
               foundHidden = true;
               break;
            }
         }
         assertFalse(foundHidden,
            "Hidden file must not appear by default");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t11_toggleHiddenShowsHiddenFiles() throws Exception {
      File testDir = new File(tempDir, "t11");
      testDir.mkdir();
      createTestFile(testDir, "visible.txt");
      createTestFile(testDir, ".hidden");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         de.toggleHidden(fvc);

         boolean foundHidden = false;
         for (int i = 1; i < de.readIn(); i++) {
            String fn = de.getFilename(i);
            if (".hidden".equals(fn)) {
               foundHidden = true;
               break;
            }
         }
         assertTrue(foundHidden,
            "Hidden file must appear after toggle");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t12_toggleHiddenTwiceHidesAgain() throws Exception {
      File testDir = new File(tempDir, "t12");
      testDir.mkdir();
      createTestFile(testDir, "visible.txt");
      createTestFile(testDir, ".secret");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         de.toggleHidden(fvc);
         de.toggleHidden(fvc);

         boolean foundHidden = false;
         for (int i = 1; i < de.readIn(); i++) {
            String fn = de.getFilename(i);
            if (".secret".equals(fn)) {
               foundHidden = true;
               break;
            }
         }
         assertFalse(foundHidden,
            "Hidden file must be hidden again after double toggle");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Parent navigation ────────────────────────────────────────

   @Test
   void t13_goToParentNavigatesUp() throws Exception {
      File parentDir = new File(tempDir, "t13parent");
      parentDir.mkdir();
      File childDir = new File(parentDir, "child");
      childDir.mkdir();
      createTestFile(childDir, "inside.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            childDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         String beforeDir = de.getCurrentDir().shortName;
         assertTrue(beforeDir.contains("child"),
            "Initially in child dir: " + beforeDir);

         de.goToParent(fvc);

         String afterDir = de.getCurrentDir().shortName;
         assertTrue(afterDir.contains("t13parent"),
            "After goToParent, should be in t13parent: " + afterDir);

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t14_goToParentPositionsCursorOnChildDir() throws Exception {
      File parentDir = new File(tempDir, "t14parent");
      parentDir.mkdir();
      File childDir = new File(parentDir, "mychild");
      childDir.mkdir();
      createTestFile(parentDir, "other.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            childDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         de.goToParent(fvc);

         // Cursor should be on the 'mychild' entry
         int cursorLine = fvc.inserty();
         String fn = de.getFilename(cursorLine);
         assertEquals("mychild/", fn,
            "Cursor must be on child dir after goToParent");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Filter ───────────────────────────────────────────────────

   @Test
   void t15_filterShowsOnlyMatchingEntries() throws Exception {
      File testDir = new File(tempDir, "t15");
      testDir.mkdir();
      createTestFile(testDir, "match_one.java");
      createTestFile(testDir, "match_two.java");
      createTestFile(testDir, "no_match.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         de.setFilter("\\.java$");

         boolean foundJava = false;
         boolean foundTxt = false;
         for (int i = 1; i < de.readIn(); i++) {
            String fn = de.getFilename(i);
            if (null == fn || "../".equals(fn))
               continue;
            if (fn.endsWith(".java"))
               foundJava = true;
            if (fn.endsWith(".txt"))
               foundTxt = true;
         }
         assertTrue(foundJava, "Java files must match filter");
         assertFalse(foundTxt, "txt files must be filtered out");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t16_clearFilterShowsAllEntries() throws Exception {
      File testDir = new File(tempDir, "t16");
      testDir.mkdir();
      createTestFile(testDir, "keep.java");
      createTestFile(testDir, "also.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         de.setFilter("\\.java$");
         de.setFilter(null);

         boolean foundJava = false;
         boolean foundTxt = false;
         for (int i = 1; i < de.readIn(); i++) {
            String fn = de.getFilename(i);
            if (null == fn || "../".equals(fn))
               continue;
            if (fn.endsWith(".java"))
               foundJava = true;
            if (fn.endsWith(".txt"))
               foundTxt = true;
         }
         assertTrue(foundJava, "Java file visible after clear");
         assertTrue(foundTxt, "txt file visible after clear");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── getFullPath / findLineForFilename ────────────────────────

   @Test
   void t17_getFullPathReturnsAbsolutePath() throws Exception {
      File testDir = new File(tempDir, "t17");
      testDir.mkdir();
      createTestFile(testDir, "target.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         int line = de.findLineForFilename("target.txt");
         assertTrue(line > 0, "Must find target.txt");

         String fullPath = de.getFullPath(line);
         assertNotNull(fullPath);
         assertTrue(fullPath.endsWith("target.txt"),
            "Full path must end with filename: " + fullPath);
         assertTrue(fullPath.startsWith("/"),
            "Full path must be absolute: " + fullPath);

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t18_getFullPathForParentReturnsParentDir() throws Exception {
      File testDir = new File(tempDir, "t18");
      testDir.mkdir();
      createTestFile(testDir, "x.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         int parentLine = de.findLineForFilename("../");
         assertTrue(parentLine > 0, "Must find parent entry");

         String parentPath = de.getFullPath(parentLine);
         assertNotNull(parentPath);
         // Parent path should be the parent of testDir
         assertFalse(parentPath.contains("t18"),
            "Parent path must not contain 't18': " + parentPath);

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t19_findLineForFilenameFindsDirectory() throws Exception {
      File testDir = new File(tempDir, "t19");
      testDir.mkdir();
      new File(testDir, "findme").mkdir();

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         int line = de.findLineForFilename("findme");
         assertTrue(line > 0,
            "Must find directory 'findme' by bare name");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t20_findLineForFilenameReturnsNegOneForMissing()
         throws Exception {
      File testDir = new File(tempDir, "t20");
      testDir.mkdir();
      createTestFile(testDir, "exists.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         int line = de.findLineForFilename("nonexistent.txt");
         assertEquals(-1, line,
            "Must return -1 for missing file");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Rendering in GUI context ─────────────────────────────────

   @Test
   void t21_dirEditRendersThroughOldView() throws Exception {
      File testDir = new File(tempDir, "t21");
      testDir.mkdir();
      createTestFile(testDir, "render_test.txt");
      new File(testDir, "render_dir").mkdir();

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         // Verify the view can render without crashing
         Canvas foundCanvas = null;
         try {
            Class<?> oldViewClass = oldView.getClass();
            Method gc = oldViewClass.getDeclaredMethod("getComponent");
            gc.setAccessible(true);
            foundCanvas = (Canvas) gc.invoke(oldView);
         } catch (NoSuchMethodException e) {
            // If getComponent doesn't exist, skip rendering check
         }

         if (null != foundCanvas && foundCanvas.isDisplayable()) {
            final Canvas cvs = foundCanvas;
            BufferedImage img = new BufferedImage(
               Math.max(cvs.getWidth(), 1),
               Math.max(cvs.getHeight(), 1),
               BufferedImage.TYPE_INT_ARGB);
            Graphics g = img.createGraphics();
            assertDoesNotThrow(() -> cvs.paint(g),
               "Painting DirEdit content must not throw");
            g.dispose();
         }

         // Verify the buffer content is well-formed
         assertTrue(de.readIn() >= 4,
            "DirEdit must have header + entries + footer");
         String header = de.at(1).toString();
         assertTrue(header.contains("Directory:"),
            "First line must be header");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t22_helpFooterLinesPresent() throws Exception {
      File testDir = new File(tempDir, "t22");
      testDir.mkdir();
      createTestFile(testDir, "x.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         // Check last few lines for help footer
         boolean foundHelp = false;
         int count = de.readIn();
         for (int i = Math.max(1, count - 4); i < count; i++) {
            String line = de.at(i).toString();
            if (line.contains("[Enter]") || line.contains("[dd]")
                  || line.contains("[r] rename")) {
               foundHelp = true;
               break;
            }
         }
         assertTrue(foundHelp,
            "Help footer lines must be present");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Refresh ──────────────────────────────────────────────────

   @Test
   void t23_refreshRepopulatesAfterFileCreation() throws Exception {
      File testDir = new File(tempDir, "t23");
      testDir.mkdir();
      createTestFile(testDir, "original.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         // Verify original file present
         int origLine = de.findLineForFilename("original.txt");
         assertTrue(origLine > 0, "Must find original.txt");

         // Create new file after DirEdit opened
         createTestFile(testDir, "newfile.txt");

         // Before refresh, new file not visible
         int newLine = de.findLineForFilename("newfile.txt");
         assertEquals(-1, newLine,
            "New file must not be visible before refresh");

         // Refresh
         de.refresh(fvc);

         // After refresh, new file visible
         newLine = de.findLineForFilename("newfile.txt");
         assertTrue(newLine > 0,
            "New file must be visible after refresh");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── openSelected enters subdirectory ─────────────────────────

   @Test
   void t24_openSelectedEntersSubdirectory() throws Exception {
      File testDir = new File(tempDir, "t24");
      testDir.mkdir();
      File sub = new File(testDir, "gohere");
      sub.mkdir();
      createTestFile(sub, "inside.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         int subLine = de.findLineForFilename("gohere");
         assertTrue(subLine > 0, "Must find gohere/ entry");

         fvc.cursoryabs(subLine);
         de.openSelected(fvc);

         String currentDir = de.getCurrentDir().shortName;
         assertTrue(currentDir.contains("gohere"),
            "Must be in gohere/ after openSelected: " + currentDir);

         // Verify inside.txt is now listed
         int insideLine = de.findLineForFilename("inside.txt");
         assertTrue(insideLine > 0,
            "inside.txt must be visible in subdirectory");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Cursor-dependent operations ──────────────────────────────

   @Test
   void t25_cursorOnFileReturnsCorrectFilename() throws Exception {
      File testDir = new File(tempDir, "t25");
      testDir.mkdir();
      createTestFile(testDir, "aaa.txt");
      createTestFile(testDir, "bbb.txt");
      createTestFile(testDir, "ccc.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         int line = de.findLineForFilename("bbb.txt");
         assertTrue(line > 0);

         fvc.cursoryabs(line);
         String fn = de.getFilename(fvc.inserty());
         assertEquals("bbb.txt", fn,
            "Filename at cursor position must match");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t26_cursorOnHeaderReturnsNullFilename() throws Exception {
      File testDir = new File(tempDir, "t26");
      testDir.mkdir();
      createTestFile(testDir, "x.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         fvc.cursoryabs(1);
         String fn = de.getFilename(fvc.inserty());
         assertNull(fn,
            "Header line must return null filename");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t27_cursorOnFooterReturnsNullFilename() throws Exception {
      File testDir = new File(tempDir, "t27");
      testDir.mkdir();
      createTestFile(testDir, "x.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         // Last line is a footer help line
         fvc.cursoryabs(de.readIn() - 1);
         String fn = de.getFilename(fvc.inserty());
         assertNull(fn,
            "Footer line must return null filename");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Empty directory ──────────────────────────────────────────

   @Test
   void t28_emptyDirectoryShowsHeaderAndFooter() throws Exception {
      File testDir = new File(tempDir, "t28");
      testDir.mkdir();

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         assertTrue(de.readIn() >= 3,
            "Even empty dir needs header + parent + footer");
         String header = de.at(1).toString();
         assertTrue(header.contains("Directory:"),
            "Header must exist for empty dir");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Sort footer updates ──────────────────────────────────────

   @Test
   void t29_sortModeShownInFooter() throws Exception {
      File testDir = new File(tempDir, "t29");
      testDir.mkdir();
      createTestFile(testDir, "x.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         // Default sort is NAME
         boolean foundName = false;
         for (int i = de.readIn() - 4; i < de.readIn(); i++) {
            if (i < 1) continue;
            String line = de.at(i).toString();
            if (line.contains("sort:name")) {
               foundName = true;
               break;
            }
         }
         assertTrue(foundName,
            "Footer must show current sort mode 'sort:name'");

         // Switch to SIZE
         de.cycleSortMode(fvc);
         boolean foundSize = false;
         for (int i = de.readIn() - 4; i < de.readIn(); i++) {
            if (i < 1) continue;
            String line = de.at(i).toString();
            if (line.contains("sort:size")) {
               foundSize = true;
               break;
            }
         }
         assertTrue(foundSize,
            "Footer must show 'sort:size' after cycling");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Filter shown in footer ───────────────────────────────────

   @Test
   void t30_filterPatternShownInFooter() throws Exception {
      File testDir = new File(tempDir, "t30");
      testDir.mkdir();
      createTestFile(testDir, "a.java");
      createTestFile(testDir, "b.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         de.setFilter("\\.java$");

         boolean foundFilter = false;
         for (int i = de.readIn() - 4; i < de.readIn(); i++) {
            if (i < 1) continue;
            String line = de.at(i).toString();
            if (line.contains("filter:\\.java$")) {
               foundFilter = true;
               break;
            }
         }
         assertTrue(foundFilter,
            "Footer must show active filter pattern");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Mark for delete ──────────────────────────────────────────

   @Test
   void t31_markForDeleteShowsInEntry() throws Exception {
      File testDir = new File(tempDir, "t31");
      testDir.mkdir();
      createTestFile(testDir, "marked.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         // Mark for delete
         de.markedForDelete.add("marked.txt");
         de.populateDirectory();

         int line = de.findLineForFilename("marked.txt");
         assertTrue(line > 0, "Must find marked file");
         String entry = de.at(line).toString();
         assertTrue(entry.startsWith("D "),
            "Marked entry must start with 'D ': " + entry);

         // Unmark
         de.markedForDelete.remove("marked.txt");
         de.populateDirectory();

         line = de.findLineForFilename("marked.txt");
         entry = de.at(line).toString();
         assertTrue(entry.startsWith("  "),
            "Unmarked entry must start with '  ': " + entry);

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── openDirectory with targetFile ────────────────────────────

   @Test
   void t32_openDirectoryWithTargetFilePositionsCursor()
         throws Exception {
      File testDir = new File(tempDir, "t32");
      testDir.mkdir();
      createTestFile(testDir, "aaa.txt");
      createTestFile(testDir, "target.txt");
      createTestFile(testDir, "zzz.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView, "target.txt");
         DirEdit de = (DirEdit) fvc.edvec;

         int cursorLine = fvc.inserty();
         String fn = de.getFilename(cursorLine);
         assertEquals("target.txt", fn,
            "Cursor must be on target file after openDirectory");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── getFilename edge cases ───────────────────────────────────

   @Test
   void t33_getFilenameOutOfBoundsReturnsNull() throws Exception {
      File testDir = new File(tempDir, "t33");
      testDir.mkdir();
      createTestFile(testDir, "x.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         assertNull(de.getFilename(0), "Line 0 must return null");
         assertNull(de.getFilename(-1), "Negative line must return null");
         assertNull(de.getFilename(de.readIn() + 10),
            "Past end must return null");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t34_getFullPathOutOfBoundsReturnsNull() throws Exception {
      File testDir = new File(tempDir, "t34");
      testDir.mkdir();
      createTestFile(testDir, "x.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         assertNull(de.getFullPath(0), "Line 0 must return null");
         assertNull(de.getFullPath(-1), "Negative must return null");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Multiple DirEdits ────────────────────────────────────────

   @Test
   void t35_multipleDirEditsIndependent() throws Exception {
      File dir1 = new File(tempDir, "t35_a");
      dir1.mkdir();
      createTestFile(dir1, "in_a.txt");

      File dir2 = new File(tempDir, "t35_b");
      dir2.mkdir();
      createTestFile(dir2, "in_b.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc1 = DirEdit.openDirectory(
            dir1.getAbsolutePath(), oldView);
         DirEdit de1 = (DirEdit) fvc1.edvec;

         FvContext<?> fvc2 = DirEdit.openDirectory(
            dir2.getAbsolutePath(), oldView);
         DirEdit de2 = (DirEdit) fvc2.edvec;

         assertTrue(de1.findLineForFilename("in_a.txt") > 0);
         assertEquals(-1, de1.findLineForFilename("in_b.txt"));

         assertTrue(de2.findLineForFilename("in_b.txt") > 0);
         assertEquals(-1, de2.findLineForFilename("in_a.txt"));

         closeDirEdit(de2);
         closeDirEdit(de1);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Size formatting in display ───────────────────────────────

   @Test
   void t36_sizeFormattingInEntries() throws Exception {
      File testDir = new File(tempDir, "t36");
      testDir.mkdir();
      createTestFile(testDir, "tiny.txt", 10);
      createTestFile(testDir, "big.txt", 2048);

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         int tinyLine = de.findLineForFilename("tiny.txt");
         assertTrue(tinyLine > 0);
         String tinyEntry = de.at(tinyLine).toString();
         assertTrue(tinyEntry.contains("B"),
            "Tiny file must show bytes: " + tinyEntry);

         int bigLine = de.findLineForFilename("big.txt");
         assertTrue(bigLine > 0);
         String bigEntry = de.at(bigLine).toString();
         assertTrue(bigEntry.contains("K"),
            "2KB file must show K suffix: " + bigEntry);

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Read-only ────────────────────────────────────────────────

   @Test
   void t37_dirEditIsReadOnly() throws Exception {
      File testDir = new File(tempDir, "t37");
      testDir.mkdir();
      createTestFile(testDir, "x.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         // DirEdit sets read-only in constructor; verify via reflection
         java.lang.reflect.Field propField =
            EditContainer.class.getDeclaredField("prop");
         propField.setAccessible(true);
         FileProperties prop = (FileProperties) propField.get(de);
         assertFalse(prop.isWriteable(),
            "DirEdit buffer must be read-only");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Date formatting ──────────────────────────────────────────

   @Test
   void t38_dateFormattingInEntries() throws Exception {
      File testDir = new File(tempDir, "t38");
      testDir.mkdir();
      createTestFile(testDir, "dated.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         int line = de.findLineForFilename("dated.txt");
         assertTrue(line > 0);
         String entry = de.at(line).toString();
         // Date format is yyyy-MM-dd HH:mm
         assertTrue(entry.matches(".*\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}.*"),
            "Entry must contain formatted date: " + entry);

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── openCurrentDirectory ─────────────────────────────────────

   @Test
   void t39_openCurrentDirectoryWorks() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openCurrentDirectory(oldView);
         assertNotNull(fvc);
         assertTrue(fvc.edvec instanceof DirEdit);

         DirEdit de = (DirEdit) fvc.edvec;
         assertTrue(de.readIn() > 2,
            "Current directory must have content");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Invalid filter pattern ───────────────────────────────────

   @Test
   void t40_invalidFilterPatternHandledGracefully() throws Exception {
      File testDir = new File(tempDir, "t40");
      testDir.mkdir();
      createTestFile(testDir, "a.txt");
      createTestFile(testDir, "b.txt");

      EventQueue.biglock2.lock();
      try {
         FvContext<?> fvc = DirEdit.openDirectory(
            testDir.getAbsolutePath(), oldView);
         DirEdit de = (DirEdit) fvc.edvec;

         int countBefore = de.readIn();

         // Invalid regex should not crash, just report error
         assertDoesNotThrow(() -> de.setFilter("[invalid"),
            "Invalid filter must not throw");

         // Content should remain unchanged (filter not applied)
         assertEquals(countBefore, de.readIn(),
            "Buffer size must not change on invalid filter");

         closeDirEdit(de);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
