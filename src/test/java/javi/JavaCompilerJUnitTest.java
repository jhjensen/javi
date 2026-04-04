package javi;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Tests for the Java compilation pipeline used by
 * {@link JavaCompiler} (the javi command) and the
 * {@link FileDescriptor.Fiter} file iterator adapter.
 *
 * <p>Exercises {@code FileDescriptor.getFileObjs()} which wraps
 * {@code Fiter}, and the {@code javax.tools.JavaCompiler} API
 * that {@code JavaCompilerInst.preRun()} uses internally.
 * No AWT or editor infrastructure required.
 */
class JavaCompilerJUnitTest {

   private Path tempDir;

   @BeforeEach
   void setUp() throws IOException {
      tempDir = Files.createTempDirectory("javi-compiler-test");
   }

   @AfterEach
   void tearDown() {
      try {
         Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
      } catch (IOException e) {
         // best-effort cleanup
      }
   }

   private Path writeJavaFile(String filename, String content)
         throws IOException {
      Path file = tempDir.resolve(filename);
      Files.writeString(file, content);
      return file;
   }

   // ── Fiter / getFileObjs tests ───────────────────────────────

   @Test
   void getFileObjsReturnsJavaFileObjects() throws IOException {
      Path src = writeJavaFile("Hello.java",
         "public class Hello {"
         + " public static void main(String[] a) {} }");

      javax.tools.JavaCompiler compiler =
         ToolProvider.getSystemJavaCompiler();
      StandardJavaFileManager fm =
         compiler.getStandardFileManager(null, null, null);

      FileDescriptor.LocalFile lf =
         FileDescriptor.LocalFile.make(src.toFile());
      ArrayList<FileDescriptor.LocalFile> flist = new ArrayList<>();
      flist.add(lf);

      Iterable<? extends JavaFileObject> fileObjs =
         FileDescriptor.getFileObjs(fm, flist);
      assertNotNull(fileObjs);

      int count = 0;
      for (JavaFileObject fo : fileObjs) {
         assertNotNull(fo);
         assertTrue(fo.getName().contains("Hello.java"));
         count++;
      }
      assertEquals(1, count);
      fm.close();
   }

   @Test
   void getFileObjsHandlesEmptyList() throws IOException {
      javax.tools.JavaCompiler compiler =
         ToolProvider.getSystemJavaCompiler();
      StandardJavaFileManager fm =
         compiler.getStandardFileManager(null, null, null);

      ArrayList<FileDescriptor.LocalFile> flist = new ArrayList<>();
      Iterable<? extends JavaFileObject> fileObjs =
         FileDescriptor.getFileObjs(fm, flist);

      assertNotNull(fileObjs);
      assertFalse(fileObjs.iterator().hasNext());
      fm.close();
   }

   @Test
   void getFileObjsIteratesMultipleFiles() throws IOException {
      writeJavaFile("A.java", "public class A {}");
      writeJavaFile("B.java", "public class B {}");

      javax.tools.JavaCompiler compiler =
         ToolProvider.getSystemJavaCompiler();
      StandardJavaFileManager fm =
         compiler.getStandardFileManager(null, null, null);

      ArrayList<FileDescriptor.LocalFile> flist = new ArrayList<>();
      flist.add(FileDescriptor.LocalFile.make(
         tempDir.resolve("A.java").toFile()));
      flist.add(FileDescriptor.LocalFile.make(
         tempDir.resolve("B.java").toFile()));

      Iterable<? extends JavaFileObject> fileObjs =
         FileDescriptor.getFileObjs(fm, flist);

      int count = 0;
      for (JavaFileObject fo : fileObjs) {
         assertNotNull(fo);
         count++;
      }
      assertEquals(2, count);
      fm.close();
   }

   // ── Compilation success tests ───────────────────────────────

   @Test
   void compilesValidJavaFile() throws IOException {
      Path src = writeJavaFile("ValidClass.java",
         "public class ValidClass {\n"
         + "   public int add(int a, int b) {\n"
         + "      return a + b;\n"
         + "   }\n"
         + "}\n");

      Path outDir = tempDir.resolve("out");
      Files.createDirectories(outDir);

      javax.tools.JavaCompiler compiler =
         ToolProvider.getSystemJavaCompiler();
      StandardJavaFileManager fm =
         compiler.getStandardFileManager(null, null, null);

      FileDescriptor.LocalFile lf =
         FileDescriptor.LocalFile.make(src.toFile());
      ArrayList<FileDescriptor.LocalFile> flist = new ArrayList<>();
      flist.add(lf);

      Iterable<? extends JavaFileObject> fileObjs =
         FileDescriptor.getFileObjs(fm, flist);

      List<String> options = Arrays.asList("-d", outDir.toString());
      List<Diagnostic<? extends JavaFileObject>> diagnostics =
         new ArrayList<>();

      boolean success = compiler.getTask(
         null, fm, diagnostics::add, options, null, fileObjs).call();

      assertTrue(success, "Compilation should succeed");
      assertEquals(0, diagnostics.stream()
         .filter(d -> d.getKind() == Diagnostic.Kind.ERROR).count());
      assertTrue(Files.exists(outDir.resolve("ValidClass.class")));
      fm.close();
   }

   @Test
   void compilesMultipleFilesWithDependency() throws IOException {
      writeJavaFile("Base.java",
         "public class Base {\n"
         + "   public int value() { return 42; }\n"
         + "}\n");
      writeJavaFile("Derived.java",
         "public class Derived extends Base {\n"
         + "   public int doubleValue() {"
         + " return value() * 2; }\n"
         + "}\n");

      Path outDir = tempDir.resolve("out");
      Files.createDirectories(outDir);

      javax.tools.JavaCompiler compiler =
         ToolProvider.getSystemJavaCompiler();
      StandardJavaFileManager fm =
         compiler.getStandardFileManager(null, null, null);

      ArrayList<FileDescriptor.LocalFile> flist = new ArrayList<>();
      flist.add(FileDescriptor.LocalFile.make(
         tempDir.resolve("Base.java").toFile()));
      flist.add(FileDescriptor.LocalFile.make(
         tempDir.resolve("Derived.java").toFile()));

      Iterable<? extends JavaFileObject> fileObjs =
         FileDescriptor.getFileObjs(fm, flist);

      List<String> options = Arrays.asList("-d", outDir.toString());
      boolean success = compiler.getTask(
         null, fm, null, options, null, fileObjs).call();

      assertTrue(success);
      assertTrue(Files.exists(outDir.resolve("Base.class")));
      assertTrue(Files.exists(outDir.resolve("Derived.class")));
      fm.close();
   }

   // ── Error reporting tests ───────────────────────────────────

   @Test
   void reportsErrorForMissingSemicolon() throws IOException {
      Path src = writeJavaFile("BrokenClass.java",
         "public class BrokenClass {\n"
         + "   public int add(int a, int b) {\n"
         + "      return a + b\n"  // missing semicolon
         + "   }\n"
         + "}\n");

      Path outDir = tempDir.resolve("out");
      Files.createDirectories(outDir);

      javax.tools.JavaCompiler compiler =
         ToolProvider.getSystemJavaCompiler();
      StandardJavaFileManager fm =
         compiler.getStandardFileManager(null, null, null);

      FileDescriptor.LocalFile lf =
         FileDescriptor.LocalFile.make(src.toFile());
      ArrayList<FileDescriptor.LocalFile> flist = new ArrayList<>();
      flist.add(lf);

      Iterable<? extends JavaFileObject> fileObjs =
         FileDescriptor.getFileObjs(fm, flist);

      List<String> options = Arrays.asList("-d", outDir.toString());
      List<Diagnostic<? extends JavaFileObject>> diagnostics =
         new ArrayList<>();

      boolean success = compiler.getTask(
         null, fm, diagnostics::add, options, null, fileObjs).call();

      assertFalse(success, "Compilation should fail");
      assertTrue(diagnostics.stream()
         .anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR));
      fm.close();
   }

   @Test
   void reportsMultipleErrorsFromSeparateFiles() throws IOException {
      writeJavaFile("ErrorA.java",
         "public class ErrorA {\n"
         + "   public void broken() { return 42; }\n"
         + "}\n");
      writeJavaFile("ErrorB.java",
         "public class ErrorB {\n"
         + "   public int wrong() { return \"text\"; }\n"
         + "}\n");

      Path outDir = tempDir.resolve("out");
      Files.createDirectories(outDir);

      javax.tools.JavaCompiler compiler =
         ToolProvider.getSystemJavaCompiler();
      StandardJavaFileManager fm =
         compiler.getStandardFileManager(null, null, null);

      ArrayList<FileDescriptor.LocalFile> flist = new ArrayList<>();
      flist.add(FileDescriptor.LocalFile.make(
         tempDir.resolve("ErrorA.java").toFile()));
      flist.add(FileDescriptor.LocalFile.make(
         tempDir.resolve("ErrorB.java").toFile()));

      Iterable<? extends JavaFileObject> fileObjs =
         FileDescriptor.getFileObjs(fm, flist);

      List<String> options = Arrays.asList("-d", outDir.toString());
      List<Diagnostic<? extends JavaFileObject>> diagnostics =
         new ArrayList<>();

      compiler.getTask(
         null, fm, diagnostics::add, options, null, fileObjs).call();

      long errorCount = diagnostics.stream()
         .filter(d -> d.getKind() == Diagnostic.Kind.ERROR).count();
      assertTrue(errorCount >= 2,
         "Should have errors from both files, got " + errorCount);
      fm.close();
   }

   @Test
   void diagnosticContainsSourceAndLocationInfo() throws IOException {
      Path src = writeJavaFile("SourceInfo.java",
         "public class SourceInfo {\n"
         + "   public void broken() {\n"
         + "      return 42;\n"  // void method returning value
         + "   }\n"
         + "}\n");

      Path outDir = tempDir.resolve("out");
      Files.createDirectories(outDir);

      javax.tools.JavaCompiler compiler =
         ToolProvider.getSystemJavaCompiler();
      StandardJavaFileManager fm =
         compiler.getStandardFileManager(null, null, null);

      FileDescriptor.LocalFile lf =
         FileDescriptor.LocalFile.make(src.toFile());
      ArrayList<FileDescriptor.LocalFile> flist = new ArrayList<>();
      flist.add(lf);

      Iterable<? extends JavaFileObject> fileObjs =
         FileDescriptor.getFileObjs(fm, flist);

      List<String> options = Arrays.asList("-d", outDir.toString());
      List<Diagnostic<? extends JavaFileObject>> diagnostics =
         new ArrayList<>();

      compiler.getTask(
         null, fm, diagnostics::add, options, null, fileObjs).call();

      Diagnostic<? extends JavaFileObject> error = diagnostics.stream()
         .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
         .findFirst().orElseThrow();

      assertNotNull(error.getSource(),
         "Diagnostic should have source");
      assertTrue(error.getSource().getName()
         .contains("SourceInfo.java"));
      assertTrue(error.getLineNumber() > 0);
      assertTrue(error.getColumnNumber() > 0);
      assertNotNull(error.getMessage(null));
      fm.close();
   }

   @Test
   void diagnosticLineNumberMatchesErrorLocation() throws IOException {
      Path src = writeJavaFile("LineCheck.java",
         "public class LineCheck {\n"      // line 1
         + "   int x = 1;\n"              // line 2
         + "   int y = 2;\n"              // line 3
         + "   int z = \"wrong\";\n"      // line 4 — type error
         + "}\n");

      Path outDir = tempDir.resolve("out");
      Files.createDirectories(outDir);

      javax.tools.JavaCompiler compiler =
         ToolProvider.getSystemJavaCompiler();
      StandardJavaFileManager fm =
         compiler.getStandardFileManager(null, null, null);

      FileDescriptor.LocalFile lf =
         FileDescriptor.LocalFile.make(src.toFile());
      ArrayList<FileDescriptor.LocalFile> flist = new ArrayList<>();
      flist.add(lf);

      Iterable<? extends JavaFileObject> fileObjs =
         FileDescriptor.getFileObjs(fm, flist);

      List<String> options = Arrays.asList("-d", outDir.toString());
      List<Diagnostic<? extends JavaFileObject>> diagnostics =
         new ArrayList<>();

      compiler.getTask(
         null, fm, diagnostics::add, options, null, fileObjs).call();

      Diagnostic<? extends JavaFileObject> error = diagnostics.stream()
         .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
         .findFirst().orElseThrow();

      assertEquals(4, error.getLineNumber(),
         "Error should be on line 4");
      fm.close();
   }

   // ── Position creation from diagnostics ──────────────────────

   @Test
   void positionCreatedFromDiagnosticValues() throws IOException {
      // Verify that Position objects can be created from
      // diagnostic data, as JavaCompilerInst.report() does
      Path src = writeJavaFile("PosTest.java",
         "public class PosTest {\n"
         + "   public void bad() {\n"
         + "      return 42;\n"
         + "   }\n"
         + "}\n");

      Path outDir = tempDir.resolve("out");
      Files.createDirectories(outDir);

      javax.tools.JavaCompiler compiler =
         ToolProvider.getSystemJavaCompiler();
      StandardJavaFileManager fm =
         compiler.getStandardFileManager(null, null, null);

      FileDescriptor.LocalFile lf =
         FileDescriptor.LocalFile.make(src.toFile());
      ArrayList<FileDescriptor.LocalFile> flist = new ArrayList<>();
      flist.add(lf);

      Iterable<? extends JavaFileObject> fileObjs =
         FileDescriptor.getFileObjs(fm, flist);

      List<String> options = Arrays.asList("-d", outDir.toString());
      List<Diagnostic<? extends JavaFileObject>> diagnostics =
         new ArrayList<>();

      compiler.getTask(
         null, fm, diagnostics::add, options, null, fileObjs).call();

      Diagnostic<? extends JavaFileObject> diag = diagnostics.stream()
         .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
         .findFirst().orElseThrow();

      // Create Position the same way JavaCompilerInst.report() does
      String mess = diag.getMessage(null).replace('\n', ' ');
      String srcName = diag.getSource() instanceof javax.tools.FileObject fo
         ? fo.getName()
         : diag.getSource().toString();

      Position pos = new Position(
         (int) diag.getColumnNumber(),
         (int) diag.getLineNumber(),
         srcName, mess);

      assertTrue(pos.y > 0);
      assertTrue(pos.x > 0);
      assertNotNull(pos.comment);
      assertTrue(pos.comment.length() > 0);
      fm.close();
   }
}
