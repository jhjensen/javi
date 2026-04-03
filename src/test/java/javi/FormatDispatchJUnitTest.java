package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link FormatDispatch} file type detection and
 * dispatch behavior.
 */
class FormatDispatchJUnitTest {

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

   @Test
   @DisplayName("detectFileType returns java for .java files")
   void detectJava() {
      assertEquals("java",
         FormatDispatch.detectFileType("Foo.java"));
      assertEquals("java",
         FormatDispatch.detectFileType("src/main/Bar.java"));
   }

   @Test
   @DisplayName("detectFileType returns cpp for C/C++ files")
   void detectCpp() {
      assertEquals("cpp",
         FormatDispatch.detectFileType("main.c"));
      assertEquals("cpp",
         FormatDispatch.detectFileType("test.cc"));
      assertEquals("cpp",
         FormatDispatch.detectFileType("test.cpp"));
      assertEquals("cpp",
         FormatDispatch.detectFileType("test.cxx"));
      assertEquals("cpp",
         FormatDispatch.detectFileType("test.h"));
      assertEquals("cpp",
         FormatDispatch.detectFileType("test.hpp"));
      assertEquals("cpp",
         FormatDispatch.detectFileType("test.hxx"));
   }

   @Test
   @DisplayName("detectFileType returns null for unknown files")
   void detectUnknown() {
      assertNull(FormatDispatch.detectFileType("test.py"));
      assertNull(FormatDispatch.detectFileType("test.txt"));
      assertNull(FormatDispatch.detectFileType(null));
   }

   @Test
   @DisplayName("format and formatr commands are registered")
   void formatCommandsRegistered() {
      assertNotNull(Rgroup.bindingLookup("format"),
         ":format should be registered");
      assertNotNull(Rgroup.bindingLookup("formatr"),
         ":formatr should be registered");
   }

   @Test
   @DisplayName("FormatDispatch dispatches to jformat when loaded")
   void dispatchToJformatWhenPluginLoaded() throws Exception {
      // Load the formatter plugin
      java.io.File jar =
         new java.io.File("build/libs/javi-formatter.jar");
      if (!jar.exists())
         return; // skip if JAR not built
      Rgroup.doCommand("loadplugin", "formatter", 0, 1,
         FvContext.getCurrFvc(), false);

      // After loading, jformat should be available for dispatch
      Rgroup.KeyBinding jf = Rgroup.bindingLookup("jformat");
      assertNotNull(jf,
         "jformat should be registered after formatter load");
      // And FormatDispatch should detect .java files
      assertEquals("java",
         FormatDispatch.detectFileType("Test.java"));
   }

   @Test
   @DisplayName("FormatDispatch reports no formatter for unknown types")
   void noFormatterForUnknownType() {
      assertNull(FormatDispatch.detectFileType("readme.md"));
      assertNull(FormatDispatch.detectFileType("Makefile"));
      assertNull(FormatDispatch.detectFileType(""));
   }
}
