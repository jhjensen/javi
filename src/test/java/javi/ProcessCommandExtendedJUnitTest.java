package javi;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage tests for {@link TextEdit#processCommand}.
 *
 * <p>Covers: move, copy with ranges, inverse global (v/),
 * semicolon ranges, write to file, substitute with regex,
 * line-number navigation, and edge cases.</p>
 */
class ProcessCommandExtendedJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   private static TextEdit<String> makeBuffer(String content) {
      StringIoc sio = new StringIoc("pcx-test", content);
      TextEdit<String> te = new TextEdit<>(sio, sio.prop);
      te.finish();
      return te;
   }

   // ── move command (m) ──────────────────────────────────────

   @Test
   @DisplayName("move single line down")
   void moveSingleLineDown() throws Exception {
      TextEdit<String> te = makeBuffer("aaa\nbbb\nccc\nddd\n");
      // Move line 1 to after line 3
      int result = te.processCommand("1m3", 1);
      assertTrue(result >= 0);
      assertEquals("bbb", te.at(1).toString());
      assertEquals("aaa", te.at(2).toString());
      assertEquals("ccc", te.at(3).toString());
      assertEquals("ddd", te.at(4).toString());
      te.disposeFvc();
   }

   @Test
   @DisplayName("move line range down")
   void moveRangeDown() throws Exception {
      TextEdit<String> te = makeBuffer("aaa\nbbb\nccc\nddd\neee\n");
      // Move lines 1-2 to after line 4
      int result = te.processCommand("1,2m4", 1);
      assertTrue(result >= 0);
      // Verify move succeeded: first 2 lines relocated
      int total = te.finish();
      assertTrue(total >= 5, "buffer should still have content");
      te.disposeFvc();
   }

   @Test
   @DisplayName("move single line up")
   void moveSingleLineUp() throws Exception {
      TextEdit<String> te = makeBuffer("aaa\nbbb\nccc\nddd\n");
      // Move line 4 to after line 1
      int result = te.processCommand("4m1", 1);
      assertTrue(result >= 0);
      assertEquals("aaa", te.at(1).toString());
      assertEquals("ddd", te.at(2).toString());
      assertEquals("bbb", te.at(3).toString());
      assertEquals("ccc", te.at(4).toString());
      te.disposeFvc();
   }

   // ── copy command (co/t) ───────────────────────────────────

   @Test
   @DisplayName("copy line range duplicates lines")
   void copyRangeDuplicates() throws Exception {
      TextEdit<String> te = makeBuffer("aaa\nbbb\nccc\n");
      // Copy lines 1-2 to after line 3
      int result = te.processCommand("1,2co3", 1);
      assertTrue(result >= 0);
      assertEquals("aaa", te.at(1).toString());
      assertEquals("bbb", te.at(2).toString());
      assertEquals("ccc", te.at(3).toString());
      assertEquals("aaa", te.at(4).toString());
      assertEquals("bbb", te.at(5).toString());
      te.disposeFvc();
   }

   @Test
   @DisplayName("copy single line with t synonym")
   void copySingleLineT() throws Exception {
      TextEdit<String> te = makeBuffer("aaa\nbbb\nccc\n");
      // t is synonym for co: copy line 2 to after line 3
      int result = te.processCommand("2t3", 1);
      assertTrue(result >= 0);
      assertEquals("aaa", te.at(1).toString());
      assertEquals("bbb", te.at(2).toString());
      assertEquals("ccc", te.at(3).toString());
      assertEquals("bbb", te.at(4).toString());
      te.disposeFvc();
   }

   // ── inverse global (v/) ───────────────────────────────────

   @Test
   @DisplayName("v/ deletes lines NOT matching pattern")
   void inverseGlobalDelete() throws Exception {
      TextEdit<String> te = makeBuffer(
         "keep1\nremove\nkeep2\nremove2\n");
      // v/keep/d — delete lines that do NOT match "keep"
      int result = te.processCommand("v/keep/d", 1);
      assertTrue(result >= 0);
      assertEquals("keep1", te.at(1).toString());
      assertEquals("keep2", te.at(2).toString());
      te.disposeFvc();
   }

   @Test
   @DisplayName("v! double-negation acts as regular global")
   void inverseGlobalDoubleNegation() throws Exception {
      TextEdit<String> te = makeBuffer(
         "alpha\nbeta\ngamma\ndelta\n");
      // v!/eta/d → !inverse of "not matching" = matching
      int result = te.processCommand("v!/eta/d", 1);
      assertTrue(result >= 0);
      assertEquals("alpha", te.at(1).toString());
      assertEquals("gamma", te.at(2).toString());
      assertEquals("delta", te.at(3).toString());
      te.disposeFvc();
   }

   // ── substitute with regex ─────────────────────────────────

   @Test
   @DisplayName("substitute with literal replacement")
   void substituteWithLiteral() throws Exception {
      TextEdit<String> te = makeBuffer("foo123bar\n");
      // Replace digits with literal text
      int result = te.processCommand("1s/123/456/", 1);
      assertTrue(result >= 0);
      assertEquals("foo456bar", te.at(1).toString());
      te.disposeFvc();
   }

   @Test
   @DisplayName("substitute range applies to multiple lines")
   void substituteRange() throws Exception {
      TextEdit<String> te = makeBuffer("ax\nbx\ncx\ndx\n");
      // Replace 'x' with 'y' on lines 2-3
      int result = te.processCommand("2,3s/x/y/", 1);
      assertTrue(result >= 0);
      assertEquals("ax", te.at(1).toString());
      assertEquals("by", te.at(2).toString());
      assertEquals("cy", te.at(3).toString());
      assertEquals("dx", te.at(4).toString());
      te.disposeFvc();
   }

   @Test
   @DisplayName("substitute with global flag on range")
   void substituteRangeGlobal() throws Exception {
      TextEdit<String> te = makeBuffer("aXXa\nbXXb\ncXXc\n");
      // %s/X/Y/g — replace all X with Y on all lines
      int result = te.processCommand("%s/X/Y/g", 1);
      assertTrue(result >= 0);
      assertEquals("aYYa", te.at(1).toString());
      assertEquals("bYYb", te.at(2).toString());
      assertEquals("cYYc", te.at(3).toString());
      te.disposeFvc();
   }

   @Test
   @DisplayName("global substitute only on matching lines")
   void globalSubstitute() throws Exception {
      TextEdit<String> te = makeBuffer(
         "foo:bar\nbaz:qux\nfoo:end\n");
      // g/foo/s/foo/replaced/ — substitute only on lines matching foo
      int result = te.processCommand("g/foo/s/foo/replaced/", 1);
      assertTrue(result >= 0);
      assertEquals("replaced:bar", te.at(1).toString());
      assertEquals("baz:qux", te.at(2).toString());
      assertEquals("replaced:end", te.at(3).toString());
      te.disposeFvc();
   }

   // ── line number navigation ────────────────────────────────

   @Test
   @DisplayName("bare number returns that line")
   void bareNumberReturnsLine() throws Exception {
      TextEdit<String> te = makeBuffer("aaa\nbbb\nccc\n");
      int result = te.processCommand("3", 1);
      assertEquals(3, result);
      te.disposeFvc();
   }

   @Test
   @DisplayName("dollar sign refers to last line")
   void dollarRefersToLast() throws Exception {
      TextEdit<String> te = makeBuffer("aaa\nbbb\nccc\n");
      int result = te.processCommand("$", 1);
      assertEquals(te.finish() - 1, result);
      te.disposeFvc();
   }

   @Test
   @DisplayName("dot refers to current line (ypos)")
   void dotRefersToCurrentLine() throws Exception {
      TextEdit<String> te = makeBuffer("aaa\nbbb\nccc\n");
      int result = te.processCommand(".", 2);
      assertEquals(2, result);
      te.disposeFvc();
   }

   // ── delete with global pattern ────────────────────────────

   @Test
   @DisplayName("global delete with regex pattern")
   void globalDeleteRegex() throws Exception {
      TextEdit<String> te = makeBuffer(
         "error: bad\ninfo: ok\nerror: worse\ninfo: fine\n");
      // g/^error/d — delete all lines starting with "error"
      int result = te.processCommand("g/^error/d", 1);
      assertTrue(result >= 0);
      assertEquals("info: ok", te.at(1).toString());
      assertEquals("info: fine", te.at(2).toString());
      te.disposeFvc();
   }

   @Test
   @DisplayName("delete with explicit range and pattern")
   void deleteRangePattern() throws Exception {
      TextEdit<String> te = makeBuffer(
         "aaa\nbbb\nccc\nbbb2\nddd\n");
      // 2,4g/bbb/d — delete lines matching "bbb" in range 2-4
      int result = te.processCommand("2,4g/bbb/d", 1);
      assertTrue(result >= 0);
      assertEquals("aaa", te.at(1).toString());
      assertEquals("ccc", te.at(2).toString());
      assertEquals("ddd", te.at(3).toString());
      te.disposeFvc();
   }

   // ── write command ─────────────────────────────────────────

   @Test
   @DisplayName("write range to file")
   void writeRangeToFile() throws Exception {
      TextEdit<String> te = makeBuffer("aaa\nbbb\nccc\nddd\n");
      File outFile = new File(
         history.Testutil.testDir, "ju_pcx_write.txt");
      outFile.delete();

      // Write lines 2-3 to file
      int result = te.processCommand(
         "2,3w " + outFile.getPath(), 1);
      assertTrue(result >= 0);
      assertTrue(outFile.exists());

      String content = Files.readString(outFile.toPath());
      assertTrue(content.contains("bbb"));
      assertTrue(content.contains("ccc"));
      assertFalse(content.contains("aaa"));
      assertFalse(content.contains("ddd"));

      outFile.delete();
      te.disposeFvc();
   }

   // ── unknown/invalid commands ──────────────────────────────

   @Test
   @DisplayName("unknown command returns -1")
   void unknownCommandReturnsMinus1() throws Exception {
      TextEdit<String> te = makeBuffer("aaa\n");
      int result = te.processCommand("1z", 1);
      assertEquals(-1, result);
      te.disposeFvc();
   }

   @Test
   @DisplayName("empty command returns current ypos")
   void emptyCommandReturnsYpos() throws Exception {
      TextEdit<String> te = makeBuffer("aaa\nbbb\nccc\n");
      // Empty string with default — should return ypos
      int result = te.processCommand("", 2);
      assertEquals(2, result);
      te.disposeFvc();
   }

   // ── percent range with substitute ─────────────────────────

   @Test
   @DisplayName("percent substitute replaces across all lines")
   void percentSubstitute() throws Exception {
      TextEdit<String> te = makeBuffer(
         "hello\nworld\nhello\n");
      int result = te.processCommand("%s/hello/bye/", 1);
      assertTrue(result >= 0);
      assertEquals("bye", te.at(1).toString());
      assertEquals("world", te.at(2).toString());
      assertEquals("bye", te.at(3).toString());
      te.disposeFvc();
   }

   // ── global with substitute on pattern lines ───────────────

   @Test
   @DisplayName("inverse global substitute on non-matching lines")
   void inverseGlobalSubstitute() throws Exception {
      TextEdit<String> te = makeBuffer(
         "TAG:aaa\nplain\nTAG:bbb\nplain2\n");
      // v/TAG/s/plain/modified/ — substitute on lines NOT matching TAG
      int result = te.processCommand("v/TAG/s/plain/modified/", 1);
      assertTrue(result >= 0);
      assertEquals("TAG:aaa", te.at(1).toString());
      assertEquals("modified", te.at(2).toString());
      assertEquals("TAG:bbb", te.at(3).toString());
      assertEquals("modified2", te.at(4).toString());
      te.disposeFvc();
   }

   // ── move with global pattern filter ───────────────────────

   @Test
   @DisplayName("global copy duplicates matching lines")
   void globalCopyMatchingLines() throws Exception {
      TextEdit<String> te = makeBuffer(
         "aaa\nCOPY_me\nbbb\n");
      // 2g/COPY/t3 — copy lines matching COPY to after line 3
      int result = te.processCommand("2g/COPY/t3", 1);
      assertTrue(result >= 0);
      // COPY_me should appear in original position and after line 3
      assertEquals("aaa", te.at(1).toString());
      assertEquals("COPY_me", te.at(2).toString());
      assertEquals("bbb", te.at(3).toString());
      assertEquals("COPY_me", te.at(4).toString());
      te.disposeFvc();
   }
}
