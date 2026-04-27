package javi;

import javi.ai.AIException;
import javi.ai.tools.FileListTool;
import javi.ai.tools.FileReadTool;
import javi.ai.tools.PermissionLevel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Execution tests for individual AI tools: FileReadTool,
 * FileListTool. Exercises the tool execute() methods with
 * real temp files.
 */
@DisplayName("AI tool execution")
class AIToolExecutionJUnitTest {

   @Nested
   @DisplayName("FileReadTool")
   class FileReadTests {

      private FileReadTool tool;

      @TempDir
      Path tempDir;

      @BeforeEach
      void setUp() {
         tool = new FileReadTool();
      }

      @Test
      @DisplayName("tool metadata is correct")
      void metadata() {
         assertEquals("file_read", tool.name());
         assertNotNull(tool.description());
         assertNotNull(tool.inputSchema());
         assertEquals(PermissionLevel.AUTO,
            tool.permissionLevel());
      }

      @Test
      @DisplayName("reads entire small file")
      void readsEntireFile() throws Exception {
         Path file = tempDir.resolve("hello.txt");
         Files.writeString(file,
            "line one\nline two\nline three\n");

         String result = tool.execute(
            Map.of("path", file.toString()));

         assertTrue(result.contains("line one"));
         assertTrue(result.contains("line two"));
         assertTrue(result.contains("line three"));
      }

      @Test
      @DisplayName("reads line range")
      void readsLineRange() throws Exception {
         Path file = tempDir.resolve("range.txt");
         StringBuilder sb = new StringBuilder();
         for (int i = 1; i <= 20; i++) {
            sb.append("line ").append(i).append('\n');
         }
         Files.writeString(file, sb.toString());

         String result = tool.execute(Map.of(
            "path", file.toString(),
            "start_line", "5",
            "end_line", "8"));

         assertTrue(result.contains("line 5"));
         assertTrue(result.contains("line 8"));
         assertFalse(result.contains("line 4"));
         assertFalse(result.contains("line 9"));
      }

      @Test
      @DisplayName("returns error for nonexistent file")
      void nonexistentFile() throws Exception {
         String result = tool.execute(
            Map.of("path", "/nonexistent/file.txt"));

         assertTrue(result.contains("Error"));
         assertTrue(result.contains("not found"));
      }

      @Test
      @DisplayName("returns error for directory")
      void directoryNotFile() throws Exception {
         String result = tool.execute(
            Map.of("path", tempDir.toString()));

         assertTrue(result.contains("Error"));
      }

      @Test
      @DisplayName("throws on missing path parameter")
      void missingPathParam() {
         assertThrows(AIException.class,
            () -> tool.execute(Map.of()));
      }

      @Test
      @DisplayName("throws on empty path parameter")
      void emptyPathParam() {
         assertThrows(AIException.class,
            () -> tool.execute(
               Map.of("path", "")));
      }

      @Test
      @DisplayName("reads Java source file correctly")
      void readsJavaSource() throws Exception {
         Path file = tempDir.resolve("Test.java");
         Files.writeString(file,
            "package test;\n"
            + "\n"
            + "public class Test {\n"
            + "   public void foo() {\n"
            + "      System.out.println(\"hello\");\n"
            + "   }\n"
            + "}\n");

         String result = tool.execute(
            Map.of("path", file.toString()));

         assertTrue(result.contains("public class Test"));
         assertTrue(result.contains("System.out.println"));
      }
   }

   @Nested
   @DisplayName("FileListTool")
   class FileListTests {

      private FileListTool tool;

      @TempDir
      Path tempDir;

      @BeforeEach
      void setUp() {
         tool = new FileListTool();
      }

      @Test
      @DisplayName("tool metadata is correct")
      void metadata() {
         assertEquals("file_list", tool.name());
         assertNotNull(tool.description());
         assertNotNull(tool.inputSchema());
         assertEquals(PermissionLevel.AUTO,
            tool.permissionLevel());
      }

      @Test
      @DisplayName("lists files in directory")
      void listsFiles() throws Exception {
         Files.writeString(
            tempDir.resolve("alpha.java"), "");
         Files.writeString(
            tempDir.resolve("beta.txt"), "");

         String result = tool.execute(
            Map.of("path", tempDir.toString()));

         assertTrue(result.contains("alpha.java"));
         assertTrue(result.contains("beta.txt"));
         assertTrue(result.contains("2 entries"));
      }

      @Test
      @DisplayName("directories have trailing slash")
      void directoriesHaveSlash() throws Exception {
         Files.createDirectories(
            tempDir.resolve("subdir"));
         Files.writeString(
            tempDir.resolve("file.txt"), "");

         String result = tool.execute(
            Map.of("path", tempDir.toString()));

         assertTrue(result.contains("subdir/"));
         assertTrue(result.contains("file.txt"));
      }

      @Test
      @DisplayName("returns error for nonexistent path")
      void nonexistentPath() throws Exception {
         String result = tool.execute(Map.of(
            "path", "/nonexistent/path/xyz"));

         assertTrue(result.contains("Error"));
         assertTrue(result.contains("not found"));
      }

      @Test
      @DisplayName("returns error when path is a file")
      void pathIsFile() throws Exception {
         Path file = tempDir.resolve("notadir.txt");
         Files.writeString(file, "content");

         String result = tool.execute(
            Map.of("path", file.toString()));

         assertTrue(result.contains("Error"));
         assertTrue(result.contains("not a directory"));
      }

      @Test
      @DisplayName("throws on missing path parameter")
      void missingPathParam() {
         assertThrows(AIException.class,
            () -> tool.execute(Map.of()));
      }

      @Test
      @DisplayName("empty directory shows 0 entries")
      void emptyDirectory() throws Exception {
         Path empty = tempDir.resolve("empty");
         Files.createDirectories(empty);

         String result = tool.execute(
            Map.of("path", empty.toString()));

         assertTrue(result.contains("0 entries"));
      }
   }
}
