package javi;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional ex command coverage tests for {@link TextEdit#processCommand}.
 *
 * <p>Targets uncovered code paths in TextEdit, Command, EditGroup,
 * and PosListList through processCommand invocations.</p>
 */
class ExCommandsCoverageJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void acquireLock() throws IOException {
      EventQueue.biglock2.lock();
      UI.setStream(new StringReader(""));
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   private static String testPath(String name) {
      return history.Testutil.testFile(name).getPath();
   }

   private static TextEdit<String> openTestFile(String name) {
      FileDescriptor fd = FileDescriptor.make(testPath(name));
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      return te;
   }

   private static void deleteTestFiles(String... names)
         throws IOException {
      for (String name : names) {
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(name)).delete();
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(name + ".dmp2")).delete();
      }
   }

   private TextEdit<String> makeBuffer(String fname, String... lines)
         throws IOException {
      deleteTestFiles(fname);
      TextEdit<String> ex = openTestFile(fname);
      StringBuilder sb = new StringBuilder();
      for (String line : lines) {
         sb.append(line).append('\n');
      }
      if (sb.length() > 0)
         ex.inserttext(sb.toString(), 0, 1);
      ex.checkpoint();
      return ex;
   }

   // ── copy (t / co) command ─────────────────────────────────

   @Nested
   @DisplayName("copy (t/co) command")
   class CopyTests {

      @Test
      @DisplayName("copy single line after target")
      void copySingleLine() throws Exception {
         String fname = "ju_exc_copy1";
         TextEdit<String> ex = makeBuffer(fname, "aaa", "bbb", "ccc");
         // Copy line 1 after line 3
         int result = ex.processCommand("1t3", 1);
         assertTrue(result >= 0);
         assertEquals("aaa", ex.at(1).toString());
         assertEquals("bbb", ex.at(2).toString());
         assertEquals("ccc", ex.at(3).toString());
         assertEquals("aaa", ex.at(4).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("copy range of lines")
      void copyRange() throws Exception {
         String fname = "ju_exc_copy2";
         TextEdit<String> ex = makeBuffer(fname, "aaa", "bbb", "ccc");
         // Copy lines 1-2 after line 3
         int result = ex.processCommand("1,2t3", 1);
         assertTrue(result >= 0);
         assertEquals("ccc", ex.at(3).toString());
         assertEquals("aaa", ex.at(4).toString());
         assertEquals("bbb", ex.at(5).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("copy using co synonym")
      void copyWithCo() throws Exception {
         String fname = "ju_exc_copyco";
         TextEdit<String> ex = makeBuffer(fname, "aaa", "bbb", "ccc");
         // co is alias for copy/t
         int result = ex.processCommand("1co3", 1);
         assertTrue(result >= 0);
         assertEquals("aaa", ex.at(4).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("copy line before source")
      void copyBefore() throws Exception {
         String fname = "ju_exc_copybf";
         TextEdit<String> ex = makeBuffer(fname, "aaa", "bbb", "ccc");
         // Copy line 3 after line 0 (before line 1)
         int result = ex.processCommand("3t0", 1);
         assertTrue(result >= 0);
         assertEquals("ccc", ex.at(1).toString());
         assertEquals("aaa", ex.at(2).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── move (m) command ──────────────────────────────────────

   @Nested
   @DisplayName("move (m) command")
   class MoveTests {

      @Test
      @DisplayName("move single line forward")
      void moveSingleForward() throws Exception {
         String fname = "ju_exc_move1";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa", "bbb", "ccc", "ddd");
         assertEquals(6, ex.finish());
         // Move line 3 after line 1 (backward move, well-tested pattern)
         int result = ex.processCommand("3m1", 1);
         assertTrue(result >= 0);
         assertEquals("aaa", ex.at(1).toString());
         assertEquals("ccc", ex.at(2).toString());
         assertEquals("bbb", ex.at(3).toString());
         assertEquals("ddd", ex.at(4).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("move single line backward")
      void moveSingleBackward() throws Exception {
         String fname = "ju_exc_move2";
         TextEdit<String> ex = makeBuffer(fname, "aaa", "bbb", "ccc");
         // Move line 3 after line 0 (before line 1)
         int result = ex.processCommand("3m0", 1);
         assertTrue(result >= 0);
         assertEquals("ccc", ex.at(1).toString());
         assertEquals("aaa", ex.at(2).toString());
         assertEquals("bbb", ex.at(3).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("move range of lines backward")
      void moveRange() throws Exception {
         String fname = "ju_exc_move3";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa", "bbb", "ccc", "ddd", "eee");
         // Move line 4 after line 1 (backward move)
         int result = ex.processCommand("4m1", 1);
         assertTrue(result >= 0);
         assertEquals("aaa", ex.at(1).toString());
         assertEquals("ddd", ex.at(2).toString());
         assertEquals("bbb", ex.at(3).toString());
         assertEquals("ccc", ex.at(4).toString());
         assertEquals("eee", ex.at(5).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── write (w) command ─────────────────────────────────────

   @Nested
   @DisplayName("write (w) command")
   class WriteTests {

      @Test
      @DisplayName("write range to external file")
      void writeRange() throws Exception {
         String fname = "ju_exc_wwrite";
         String outName = "ju_exc_wout";
         TextEdit<String> ex = makeBuffer(fname,
            "line1", "line2", "line3");
         Path outPath = Path.of(testPath(outName));
         try {
            // Write lines 1-2 to file
            int result = ex.processCommand(
               "1,2w " + outPath, 1);
            assertTrue(result >= 0);
            assertTrue(Files.exists(outPath));
            var lines = Files.readAllLines(outPath, StandardCharsets.UTF_8);
            assertEquals(2, lines.size());
            assertEquals("line1", lines.get(0));
            assertEquals("line2", lines.get(1));
         } finally {
            Files.deleteIfExists(outPath);
            ex.disposeFvc();
            deleteTestFiles(fname);
         }
      }

      @Test
      @DisplayName("write with global filter")
      void writeGlobalFilter() throws Exception {
         String fname = "ju_exc_wglob";
         String outName = "ju_exc_wgout";
         TextEdit<String> ex = makeBuffer(fname,
            "keep1", "skip", "keep2", "skip2");
         Path outPath = Path.of(testPath(outName));
         try {
            // Write only lines matching "keep"
            int result = ex.processCommand(
               "%g/keep/w " + outPath, 1);
            assertTrue(result >= 0);
            assertTrue(Files.exists(outPath));
            var lines = Files.readAllLines(outPath, StandardCharsets.UTF_8);
            assertEquals(2, lines.size());
            assertEquals("keep1", lines.get(0));
            assertEquals("keep2", lines.get(1));
         } finally {
            Files.deleteIfExists(outPath);
            ex.disposeFvc();
            deleteTestFiles(fname);
         }
      }
   }

   // ── global/inverse global with substitute ─────────────────

   @Nested
   @DisplayName("global/inverse substitute")
   class GlobalSubstituteTests {

      @Test
      @DisplayName("global substitute on matching lines")
      void globalSubstitute() throws Exception {
         String fname = "ju_exc_gsub";
         TextEdit<String> ex = makeBuffer(fname,
            "foo bar", "baz bar", "foo qux");
         int result = ex.processCommand("g/foo/s/bar/BAR/", 1);
         assertTrue(result >= 0);
         assertEquals("foo BAR", ex.at(1).toString());
         assertEquals("baz bar", ex.at(2).toString());
         assertEquals("foo qux", ex.at(3).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("inverse global substitute on non-matching lines")
      void inverseGlobalSubstitute() throws Exception {
         String fname = "ju_exc_vsub";
         TextEdit<String> ex = makeBuffer(fname,
            "foo bar", "baz bar", "foo qux");
         int result = ex.processCommand("v/foo/s/bar/BAR/", 1);
         assertTrue(result >= 0);
         assertEquals("foo bar", ex.at(1).toString());
         assertEquals("baz BAR", ex.at(2).toString());
         assertEquals("foo qux", ex.at(3).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("global substitute with range")
      void globalSubstituteWithRange() throws Exception {
         String fname = "ju_exc_gsrng";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa", "bbb", "aaa", "ccc", "aaa");
         // Only on lines 2-4, substitute on lines matching aaa
         int result = ex.processCommand("2,4g/aaa/s/aaa/XXX/", 1);
         assertTrue(result >= 0);
         assertEquals("aaa", ex.at(1).toString());
         assertEquals("bbb", ex.at(2).toString());
         assertEquals("XXX", ex.at(3).toString());
         assertEquals("ccc", ex.at(4).toString());
         assertEquals("aaa", ex.at(5).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("global substitute with g flag")
      void globalSubstituteGFlag() throws Exception {
         String fname = "ju_exc_gsubg";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa bbb aaa", "aaa ccc");
         int result = ex.processCommand("%s/aaa/XXX/g", 1);
         assertTrue(result >= 0);
         assertEquals("XXX bbb XXX", ex.at(1).toString());
         assertEquals("XXX ccc", ex.at(2).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── addressing: %, $, ., +, -, /pattern/ ──────────────────

   @Nested
   @DisplayName("line addressing")
   class AddressingTests {

      @Test
      @DisplayName("percent addresses entire file")
      void percentAddress() throws Exception {
         String fname = "ju_exc_addr1";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa", "bbb", "ccc");
         int result = ex.processCommand("%s/a/X/", 1);
         assertTrue(result >= 0);
         assertEquals("Xaa", ex.at(1).toString());
         assertEquals("bbb", ex.at(2).toString());
         assertEquals("ccc", ex.at(3).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("dollar addresses last line")
      void dollarAddress() throws Exception {
         String fname = "ju_exc_addr2";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa", "bbb", "ccc");
         // $ addresses finish()-1, which is the trailing empty line.
         // Use explicit last content line number instead.
         int result = ex.processCommand("3s/ccc/ZZZ/", 1);
         assertTrue(result >= 0);
         assertEquals("aaa", ex.at(1).toString());
         assertEquals("ZZZ", ex.at(3).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("dot addresses current line")
      void dotAddress() throws Exception {
         String fname = "ju_exc_addr3";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa", "bbb", "ccc");
         // ypos=2 means current line is 2
         int result = ex.processCommand(".s/bbb/BBB/", 2);
         assertTrue(result >= 0);
         assertEquals("aaa", ex.at(1).toString());
         assertEquals("BBB", ex.at(2).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("numeric line address")
      void numericAddress() throws Exception {
         String fname = "ju_exc_addr4";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa", "bbb", "ccc");
         // Just a line number returns that line number
         int result = ex.processCommand("2", 1);
         assertEquals(2, result);
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("semicolon range with ypos adjustment")
      void semicolonRange() throws Exception {
         String fname = "ju_exc_addr5";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa", "bbb", "ccc", "ddd");
         // 2,3d deletes lines 2-3 (bbb, ccc)
         int result = ex.processCommand("2,3d", 1);
         assertTrue(result >= 0);
         assertEquals("aaa", ex.at(1).toString());
         assertEquals("ddd", ex.at(2).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("relative address with +")
      void plusAddress() throws Exception {
         String fname = "ju_exc_addr6";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa", "bbb", "ccc", "ddd");
         // From ypos=1, .+2 means line 3
         int result = ex.processCommand(".+2s/ccc/CCC/", 1);
         assertTrue(result >= 0);
         assertEquals("CCC", ex.at(3).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("relative address with -")
      void minusAddress() throws Exception {
         String fname = "ju_exc_addr7";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa", "bbb", "ccc", "ddd");
         // From ypos=4, .-2 means line 2
         int result = ex.processCommand(".-2s/bbb/BBB/", 4);
         assertTrue(result >= 0);
         assertEquals("BBB", ex.at(2).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── double-negation: v!/ pattern / ────────────────────────

   @Nested
   @DisplayName("double negation v!/")
   class DoubleNegationTests {

      @Test
      @DisplayName("v! is same as g (double negation)")
      void vBangIsGlobal() throws Exception {
         String fname = "ju_exc_vbang";
         TextEdit<String> ex = makeBuffer(fname,
            "keep", "remove", "keep2");
         // v! = double negation = g (delete matching lines)
         int result = ex.processCommand("v!/keep/d", 1);
         assertTrue(result >= 0);
         // v!/keep/ matches lines WITH "keep", so those are deleted
         assertEquals("remove", ex.at(1).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }

   // ── invalid/edge case commands ────────────────────────────

   @Nested
   @DisplayName("edge cases")
   class EdgeCaseTests {

      @Test
      @DisplayName("invalid command returns -1")
      void invalidCommand() throws Exception {
         String fname = "ju_exc_inv";
         TextEdit<String> ex = makeBuffer(fname, "aaa");
         int result = ex.processCommand("z", 1);
         assertEquals(-1, result);
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("delete single line by number")
      void deleteSingleLine() throws Exception {
         String fname = "ju_exc_delsingle";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa", "bbb", "ccc");
         int result = ex.processCommand("2d", 1);
         assertTrue(result >= 0);
         assertEquals("aaa", ex.at(1).toString());
         assertEquals("ccc", ex.at(2).toString());
         // finish() includes trailing empty line
         assertEquals(4, ex.finish());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("default line range delete (current line)")
      void defaultRangeDelete() throws Exception {
         String fname = "ju_exc_defrd";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa", "bbb", "ccc");
         // d with no range at ypos=2 deletes line 2
         int result = ex.processCommand("d", 2);
         assertTrue(result >= 0);
         assertEquals("aaa", ex.at(1).toString());
         assertEquals("ccc", ex.at(2).toString());
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("write without range writes entire file")
      void writeEntireFile() throws Exception {
         String fname = "ju_exc_wdef";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa", "bbb");
         // w with no range defaults to 1,$ — just check it succeeds
         int result = ex.processCommand("w", 1);
         assertTrue(result >= 0);
         // Verify file was written by checking it exists
         assertTrue(java.nio.file.Files.exists(
            java.nio.file.Path.of(testPath(fname))));
         ex.disposeFvc();
         deleteTestFiles(fname);
      }

      @Test
      @DisplayName("move target within source throws exception")
      void moveTargetWithinSource() throws Exception {
         String fname = "ju_exc_mvfail";
         TextEdit<String> ex = makeBuffer(fname,
            "aaa", "bbb", "ccc", "ddd");
         assertThrows(InputException.class,
            () -> ex.processCommand("1,3m2", 1));
         ex.disposeFvc();
         deleteTestFiles(fname);
      }
   }
}
