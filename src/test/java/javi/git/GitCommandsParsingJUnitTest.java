package javi.git;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javi.ChangeOpt;
import javi.EventQueue;
import javi.FvContext;
import javi.TestInit;
import javi.TextEdit;
import javi.View;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for private string-parsing methods in {@link GitCommands}:
 * {@code extractFilenameAtCursor} and {@code findSection}.
 *
 * <p>These methods parse git-status buffer content to extract filenames
 * and determine section context.  Tested via reflection with a headless
 * stub {@link View} and {@link FvContext}.</p>
 */
class GitCommandsParsingJUnitTest {

   private View view;
   private TextEdit<String> buf;
   private FvContext fvc;
   private Method extractMethod;
   private Method findSectionMethod;
   private Method createBufferMethod;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void setUp() throws Exception {
      EventQueue.biglock2.lock();
      view = new StubView();
      extractMethod = GitCommands.class.getDeclaredMethod(
         "extractFilenameAtCursor", FvContext.class);
      extractMethod.setAccessible(true);
      findSectionMethod = GitCommands.class.getDeclaredMethod(
         "findSection", FvContext.class);
      findSectionMethod.setAccessible(true);
      createBufferMethod = GitCommands.class.getDeclaredMethod(
         "createBuffer", String.class, List.class);
      createBufferMethod.setAccessible(true);
   }

   @AfterEach
   void tearDown() {
      EventQueue.biglock2.unlock();
   }

   // ── minimal View stub for headless FvContext ────────────────

   private static class StubView extends View {
      StubView() {
         super(true);
      }
      @Override public void cursorChanged(int x, int y) {
      }
      @Override public int yCursorChanged(int y) {
         return y;
      }
      @Override public int getRows(float s) {
         return 24;
      }
      @Override public int screenFirstLine() {
         return 1;
      }
      @Override public int screeny(int a) {
         return a;
      }
      @Override public void setTabStop(int ts) {
      }
      @Override public int getTabStop() {
         return 8;
      }
      @Override public void repaint() {
      }
      @Override public boolean isVisible() {
         return true;
      }
      @Override public void setSizebyChar(int x, int y) {
      }
      @Override protected void startInsertion(Inserter ins) {
      }
      @Override protected void endInsertion(Inserter ins) {
      }
      @Override protected ChangeOpt getChangeOpt() {
         return new COpt() { };
      }
   }

   // ── helpers ─────────────────────────────────────────────────

   @SuppressWarnings("unchecked")
   private void createStatusBuffer(String... lines) throws Exception {
      buf = (TextEdit<String>) createBufferMethod.invoke(null,
         "*gitcmd-parse-test*", Arrays.asList(lines));
      buf.finish(); // wait for IoConverter to load content (releases/reacquires biglock2)
      fvc = FvContext.connectFv(buf, view);
   }

   private String extractAt(int line) throws Exception {
      fvc.cursoryabs(line);
      return (String) extractMethod.invoke(null, fvc);
   }

   private String findSectionAt(int line) throws Exception {
      fvc.cursoryabs(line);
      return (String) findSectionMethod.invoke(null, fvc);
   }

   // ── extractFilenameAtCursor ─────────────────────────────────

   @Nested
   @DisplayName("extractFilenameAtCursor()")
   class ExtractFilename {

      @Test
      @DisplayName("modified prefix extracts filename")
      void modifiedPrefix() throws Exception {
         createStatusBuffer(
            "Unstaged changes:",
            "  modified    src/main/java/Foo.java");
         assertEquals("src/main/java/Foo.java", extractAt(2));
      }

      @Test
      @DisplayName("new file prefix extracts filename")
      void newFilePrefix() throws Exception {
         createStatusBuffer(
            "Staged changes:",
            "  new file    src/test/Bar.java");
         assertEquals("src/test/Bar.java", extractAt(2));
      }

      @Test
      @DisplayName("deleted prefix extracts filename")
      void deletedPrefix() throws Exception {
         createStatusBuffer(
            "Unstaged changes:",
            "  deleted    old/Removed.java");
         assertEquals("old/Removed.java", extractAt(2));
      }

      @Test
      @DisplayName("renamed prefix with arrow extracts new name")
      void renamedWithArrow() throws Exception {
         createStatusBuffer(
            "Staged changes:",
            "  renamed    old/Name.java -> new/Name.java");
         assertEquals("new/Name.java", extractAt(2));
      }

      @Test
      @DisplayName("copied prefix extracts filename")
      void copiedPrefix() throws Exception {
         createStatusBuffer(
            "Staged changes:",
            "  copied    src/Copy.java");
         assertEquals("src/Copy.java", extractAt(2));
      }

      @Test
      @DisplayName("typechange prefix extracts filename")
      void typechangePrefix() throws Exception {
         createStatusBuffer(
            "Unstaged changes:",
            "  typechange    link.txt");
         assertEquals("link.txt", extractAt(2));
      }

      @Test
      @DisplayName("changed prefix extracts filename")
      void changedPrefix() throws Exception {
         createStatusBuffer(
            "Unstaged changes:",
            "  changed    config.xml");
         assertEquals("config.xml", extractAt(2));
      }

      @Test
      @DisplayName("unmerged prefix extracts filename")
      void unmergedPrefix() throws Exception {
         createStatusBuffer(
            "Unstaged changes:",
            "  unmerged    conflict/File.java");
         assertEquals("conflict/File.java", extractAt(2));
      }

      @Test
      @DisplayName("untracked bare filename extracted")
      void untrackedBareFilename() throws Exception {
         createStatusBuffer(
            "Untracked files:",
            "  newfile.txt");
         assertEquals("newfile.txt", extractAt(2));
      }

      @Test
      @DisplayName("line without 2-space indent returns null")
      void noIndentReturnsNull() throws Exception {
         createStatusBuffer(
            "Staged changes:",
            "modified    src/Foo.java");
         assertNull(extractAt(2));
      }

      @Test
      @DisplayName("section header line returns null")
      void sectionHeaderReturnsNull() throws Exception {
         createStatusBuffer("Staged changes:");
         assertNull(extractAt(1));
      }

      @Test
      @DisplayName("line starting with '  (' returns null")
      void parenLineReturnsNull() throws Exception {
         createStatusBuffer(
            "Staged changes:",
            "  (use git restore --staged <file> to unstage)");
         assertNull(extractAt(2));
      }

      @Test
      @DisplayName("empty indented line returns null")
      void emptyIndentedLineReturnsNull() throws Exception {
         createStatusBuffer(
            "Staged changes:",
            "  ");
         assertNull(extractAt(2));
      }

      @Test
      @DisplayName("cursor beyond readIn is clamped — extracts line 1")
      void cursorBeyondRange() throws Exception {
         createStatusBuffer("  modified    Foo.java");
         // cursoryabs clamps to [1, readIn-1], so out-of-range is clamped
         assertEquals("Foo.java", extractAt(buf.readIn() + 1));
      }

      @Test
      @DisplayName("renamed without arrow returns full rest")
      void renamedNoArrow() throws Exception {
         createStatusBuffer(
            "Staged changes:",
            "  renamed    justAName.java");
         assertEquals("justAName.java", extractAt(2));
      }

      @Test
      @DisplayName("modified with extra spaces around filename")
      void modifiedExtraSpaces() throws Exception {
         createStatusBuffer(
            "Unstaged changes:",
            "  modified      spaces/File.java");
         assertEquals("spaces/File.java", extractAt(2));
      }
   }

   // ── findSection ─────────────────────────────────────────────

   @Nested
   @DisplayName("findSection()")
   class FindSection {

      @Test
      @DisplayName("cursor in staged section returns Staged")
      void stagedSection() throws Exception {
         createStatusBuffer(
            "Staged changes (2 files):",
            "  modified    a.java",
            "  new file    b.java",
            "",
            "Unstaged changes:");
         assertEquals("Staged", findSectionAt(2));
         assertEquals("Staged", findSectionAt(3));
      }

      @Test
      @DisplayName("cursor in unstaged section returns Unstaged")
      void unstagedSection() throws Exception {
         createStatusBuffer(
            "Staged changes:",
            "  modified    a.java",
            "",
            "Unstaged changes (1 file):",
            "  modified    c.java");
         assertEquals("Unstaged", findSectionAt(5));
      }

      @Test
      @DisplayName("cursor in untracked section returns Untracked")
      void untrackedSection() throws Exception {
         createStatusBuffer(
            "Staged changes:",
            "  modified    a.java",
            "",
            "Untracked files:",
            "  newfile.txt");
         assertEquals("Untracked", findSectionAt(5));
      }

      @Test
      @DisplayName("cursor on section header returns that section")
      void cursorOnHeader() throws Exception {
         createStatusBuffer(
            "Staged changes (2 files):",
            "  modified    a.java");
         assertEquals("Staged", findSectionAt(1));
      }

      @Test
      @DisplayName("no section header found returns null")
      void noHeaderReturnsNull() throws Exception {
         createStatusBuffer(
            "On branch main",
            "Your branch is up to date",
            "");
         assertNull(findSectionAt(2));
      }

      @Test
      @DisplayName("multiple sections — cursor finds nearest above")
      void multipleSectionsNearestAbove() throws Exception {
         createStatusBuffer(
            "Staged changes:",
            "  modified    staged.java",
            "",
            "Unstaged changes:",
            "  modified    unstaged.java",
            "",
            "Untracked files:",
            "  new.txt");
         assertEquals("Staged", findSectionAt(2));
         assertEquals("Unstaged", findSectionAt(5));
         assertEquals("Untracked", findSectionAt(8));
      }

      @Test
      @DisplayName("blank line between sections inherits previous")
      void blankLineBetweenSections() throws Exception {
         createStatusBuffer(
            "Staged changes:",
            "  modified    a.java",
            "",
            "Unstaged changes:");
         assertEquals("Staged", findSectionAt(3));
      }
   }
}
