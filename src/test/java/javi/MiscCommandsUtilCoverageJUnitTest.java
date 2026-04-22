package javi;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for {@link MiscCommands} static utility methods,
 * {@link Rgroup} command registration, and command dispatch via
 * processCommand that exercises MiscCommands code paths.
 */
class MiscCommandsUtilCoverageJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   // ── getHeight / getWidth ──────────────────────────────────

   @Test
   @DisplayName("getHeight returns positive default")
   void getHeightPositive() {
      assertTrue(MiscCommands.getHeight() > 0,
         "default height should be positive");
   }

   @Test
   @DisplayName("getWidth returns positive default")
   void getWidthPositive() {
      assertTrue(MiscCommands.getWidth() > 0,
         "default width should be positive");
   }

   // ── Rgroup binding lookup ────────────────────────────────

   @Test
   @DisplayName("bindingLookup finds registered 'undo' command")
   void bindingLookupUndo() {
      assertNotNull(Rgroup.bindingLookup("undo"),
         "undo should be registered");
   }

   @Test
   @DisplayName("bindingLookup finds registered 'redo' command")
   void bindingLookupRedo() {
      assertNotNull(Rgroup.bindingLookup("redo"),
         "redo should be registered");
   }

   @Test
   @DisplayName("bindingLookup finds registered 'redraw' command")
   void bindingLookupRedraw() {
      assertNotNull(Rgroup.bindingLookup("redraw"),
         "redraw should be registered");
   }

   @Test
   @DisplayName("bindingLookup finds registered 'shells' command")
   void bindingLookupShells() {
      assertNotNull(Rgroup.bindingLookup("shells"),
         "shells should be registered");
   }

   @Test
   @DisplayName("bindingLookup finds registered 'shellnext' command")
   void bindingLookupShellnext() {
      assertNotNull(Rgroup.bindingLookup("shellnext"),
         "shellnext should be registered");
   }

   @Test
   @DisplayName("bindingLookup finds registered 'shellprev' command")
   void bindingLookupShellprev() {
      assertNotNull(Rgroup.bindingLookup("shellprev"),
         "shellprev should be registered");
   }

   @Test
   @DisplayName("bindingLookup finds registered 'mapkey' command")
   void bindingLookupMapkey() {
      assertNotNull(Rgroup.bindingLookup("mapkey"),
         "mapkey should be registered");
   }

   @Test
   @DisplayName("bindingLookup finds registered 'unmapkey' command")
   void bindingLookupUnmapkey() {
      assertNotNull(Rgroup.bindingLookup("unmapkey"),
         "unmapkey should be registered");
   }

   @Test
   @DisplayName("bindingLookup finds registered 'keymap' command")
   void bindingLookupKeymap() {
      assertNotNull(Rgroup.bindingLookup("keymap"),
         "keymap should be registered");
   }

   @Test
   @DisplayName("bindingLookup finds registered 'contexthelp' command")
   void bindingLookupContexthelp() {
      assertNotNull(Rgroup.bindingLookup("contexthelp"),
         "contexthelp should be registered");
   }

   @Test
   @DisplayName("bindingLookup finds registered 'fold' command")
   void bindingLookupFold() {
      assertNotNull(Rgroup.bindingLookup("fold"),
         "fold should be registered");
   }

   @Test
   @DisplayName("bindingLookup finds registered 'savemapkeys' command")
   void bindingLookupSavemapkeys() {
      assertNotNull(Rgroup.bindingLookup("savemapkeys"),
         "savemapkeys should be registered");
   }

   @Test
   @DisplayName("bindingLookup finds registered 'loadmapkeys' command")
   void bindingLookupLoadmapkeys() {
      assertNotNull(Rgroup.bindingLookup("loadmapkeys"),
         "loadmapkeys should be registered");
   }

   @Test
   @DisplayName("bindingLookup returns null for unknown command")
   void bindingLookupUnknown() {
      assertNull(Rgroup.bindingLookup("zzznonexistent"),
         "unknown command should return null");
   }

   // ── Rgroup command descriptions ───────────────────────────

   @Test
   @DisplayName("getDescription returns non-null for registered commands")
   void getDescriptionForRegistered() {
      String desc = Rgroup.getDescription("undo");
      assertNotNull(desc, "undo should have a description");
      assertFalse(desc.isEmpty(), "description should not be empty");
   }

   @Test
   @DisplayName("getDescription returns null for unknown command")
   void getDescriptionForUnknown() {
      String desc = Rgroup.getDescription("zzznonexistent");
      assertNull(desc, "unknown command should have null description");
   }

   @Test
   @DisplayName("getCommandEntry returns entry for registered commands")
   void getCommandEntryRegistered() {
      Rgroup.CommandEntry entry = Rgroup.getCommandEntry("undo");
      assertNotNull(entry, "undo should have a CommandEntry");
      assertEquals("undo", entry.name());
      assertNotNull(entry.description());
      assertNotNull(entry.category());
   }

   @Test
   @DisplayName("getCommandEntry returns null for unknown command")
   void getCommandEntryUnknown() {
      assertNull(Rgroup.getCommandEntry("zzznonexistent"));
   }

   // ── Command registration counts ──────────────────────────

   @Test
   @DisplayName("getRegisteredCommands returns non-empty set")
   void registeredCommandsNotEmpty() {
      var names = Rgroup.getRegisteredCommands();
      assertNotNull(names);
      assertFalse(names.isEmpty(), "should have registered commands");
   }

   @Test
   @DisplayName("getRegisteredCommands includes core commands")
   void registeredCommandsIncludesCore() {
      var names = Rgroup.getRegisteredCommands();
      assertTrue(names.contains("undo"), "should contain undo");
      assertTrue(names.contains("redo"), "should contain redo");
      assertTrue(names.contains("format"), "should contain format");
   }

   // ── FormatDispatch.detectFileType extended ────────────────

   @Test
   @DisplayName("detectFileType: null returns null")
   void detectFileTypeNull() {
      assertNull(FormatDispatch.detectFileType(null));
   }

   @Test
   @DisplayName("detectFileType: empty string returns null")
   void detectFileTypeEmpty() {
      assertNull(FormatDispatch.detectFileType(""));
   }

   @Test
   @DisplayName("detectFileType: .java returns java")
   void detectFileTypeJava() {
      assertEquals("java", FormatDispatch.detectFileType("Foo.java"));
   }

   @Test
   @DisplayName("detectFileType: .c returns cpp")
   void detectFileTypeC() {
      assertEquals("cpp", FormatDispatch.detectFileType("main.c"));
   }

   @Test
   @DisplayName("detectFileType: .cc returns cpp")
   void detectFileTypeCc() {
      assertEquals("cpp", FormatDispatch.detectFileType("util.cc"));
   }

   @Test
   @DisplayName("detectFileType: .cpp returns cpp")
   void detectFileTypeCpp() {
      assertEquals("cpp", FormatDispatch.detectFileType("lib.cpp"));
   }

   @Test
   @DisplayName("detectFileType: .h returns cpp")
   void detectFileTypeH() {
      assertEquals("cpp", FormatDispatch.detectFileType("header.h"));
   }

   @Test
   @DisplayName("detectFileType: .hpp returns cpp")
   void detectFileTypeHpp() {
      assertEquals("cpp", FormatDispatch.detectFileType("tmpl.hpp"));
   }

   @Test
   @DisplayName("detectFileType: .hxx returns cpp")
   void detectFileTypeHxx() {
      assertEquals("cpp", FormatDispatch.detectFileType("impl.hxx"));
   }

   @Test
   @DisplayName("detectFileType: .cxx returns cpp")
   void detectFileTypeCxx() {
      assertEquals("cpp", FormatDispatch.detectFileType("code.cxx"));
   }

   @Test
   @DisplayName("detectFileType: .py returns null (unsupported)")
   void detectFileTypePy() {
      assertNull(FormatDispatch.detectFileType("script.py"));
   }

   @Test
   @DisplayName("detectFileType: no extension returns null")
   void detectFileTypeNoExt() {
      assertNull(FormatDispatch.detectFileType("Makefile"));
   }

   @Test
   @DisplayName("detectFileType: .txt returns null")
   void detectFileTypeTxt() {
      assertNull(FormatDispatch.detectFileType("readme.txt"));
   }

   @Test
   @DisplayName("detectFileType: path with .java returns java")
   void detectFileTypePathJava() {
      assertEquals("java",
         FormatDispatch.detectFileType("/path/to/Foo.java"));
   }
}
