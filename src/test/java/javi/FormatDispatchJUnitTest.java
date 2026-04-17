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
   @DisplayName("clang-format reformats Java code with 3-space indent")
   void clangFormatReformatsJava() throws Exception {
      // Create a buffer with zero-indent Java code
      String content = String.join("\n",
         "package test;",
         "public class Foo {",
         "public void bar() {",
         "int x = 1;",
         "}",
         "}");
      StringIoc sio = new StringIoc("Test.java", content);
      TextEdit<String> ex = new TextEdit<>(sio, sio.prop);
      ex.finish();

      // Verify preconditions: no indentation yet
      assertEquals("public void bar() {",
         ex.at(3).toString(),
         "precondition: method should have no indent");

      // Format the entire buffer via FormatDispatch
      try {
         FormatDispatch.formatAll(ex);
      } catch (Exception e) {
         // Skip if clang-format is not available
         if (e.getMessage() != null
               && e.getMessage().contains("reformat"))
            return;
         throw e;
      }

      // Verify clang-format applied 3-space indentation
      assertEquals("   public void bar() {",
         ex.at(3).toString().replaceAll("\\s+$", ""),
         "class member should be indented 3 spaces");
      assertEquals("      int x = 1;",
         ex.at(4).toString().replaceAll("\\s+$", ""),
         "method body should be indented 6 spaces");
   }

   @Test
   @DisplayName("FormatDispatch reports no formatter for unknown types")
   void noFormatterForUnknownType() {
      assertNull(FormatDispatch.detectFileType("readme.md"));
      assertNull(FormatDispatch.detectFileType("Makefile"));
      assertNull(FormatDispatch.detectFileType(""));
   }
}
