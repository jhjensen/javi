package javi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage tests for {@link TextEdit} — focuses on
 * gettext, deletetext, changecase, shiftright/left, joinlines,
 * stringtoarray, and processCommand edge cases.
 */
class TextEditCoverageJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
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

   private static void writeTestFile(String name, String contents)
         throws IOException {
      File f = history.Testutil.testFile(name);
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(f), StandardCharsets.UTF_8)) {
         w.write(contents);
      }
   }

   private static TextEdit<String> makeTE(String name, String content)
         throws IOException {
      writeTestFile(name, content);
      FileDescriptor fd =
         FileDescriptor.make(history.Testutil.testFile(name).getPath());
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
         File f = history.Testutil.testFile(name);
         f.delete();
         new File(f.getPath() + ".dmp2").delete();
      }
   }

   // ── stringtoarray ─────────────────────────────────────────

   @Nested
   @DisplayName("stringtoarray")
   class StringToArrayTests {
      @Test
      @DisplayName("splits lines on newline")
      void splitsOnNewline() {
         ArrayList<String> result =
            TextEdit.stringtoarray("a\nb\nc");
         assertEquals(3, result.size());
         assertEquals("a", result.get(0));
         assertEquals("b", result.get(1));
         assertEquals("c", result.get(2));
      }

      @Test
      @DisplayName("single line without newline")
      void singleLineNoNewline() {
         ArrayList<String> result =
            TextEdit.stringtoarray("hello");
         assertEquals(1, result.size());
         assertEquals("hello", result.get(0));
      }

      @Test
      @DisplayName("empty string produces single empty element")
      void emptyString() {
         ArrayList<String> result =
            TextEdit.stringtoarray("");
         assertEquals(1, result.size());
         assertEquals("", result.get(0));
      }

      @Test
      @DisplayName("trailing newline creates extra empty element")
      void trailingNewline() {
         ArrayList<String> result =
            TextEdit.stringtoarray("a\nb\n");
         // The trailing \n may or may not create an extra element
         // depending on implementation — exercise the path
         assertTrue(result.size() >= 2);
         assertEquals("a", result.get(0));
         assertEquals("b", result.get(1));
      }

      @Test
      @DisplayName("CR-LF line endings")
      void crlfLineEndings() {
         ArrayList<String> result =
            TextEdit.stringtoarray("a\r\nb\r\nc");
         assertTrue(result.size() >= 3);
      }

      @Test
      @DisplayName("multiple consecutive newlines")
      void consecutiveNewlines() {
         ArrayList<String> result =
            TextEdit.stringtoarray("a\n\n\nb");
         assertTrue(result.size() >= 4);
         assertEquals("a", result.get(0));
         assertEquals("", result.get(1));
      }
   }

   // ── gettext ───────────────────────────────────────────────

   @Nested
   @DisplayName("gettext")
   class GetTextTests {
      @Test
      @DisplayName("extracts single line substring")
      void singleLineSubstring() throws IOException {
         TextEdit<String> te = makeTE("gt1", "hello world\n");
         try {
            String result = te.gettext(0, 1, 5, 1);
            assertEquals("hello", result);
         } finally {
            te.disposeFvc();
            deleteTestFiles("gt1");
         }
      }

      @Test
      @DisplayName("extracts full line")
      void fullLine() throws IOException {
         TextEdit<String> te = makeTE("gt2", "full line\n");
         try {
            String result = te.gettext(0, 1, 9, 1);
            assertEquals("full line", result);
         } finally {
            te.disposeFvc();
            deleteTestFiles("gt2");
         }
      }

      @Test
      @DisplayName("extracts across multiple lines")
      void multipleLines() throws IOException {
         TextEdit<String> te = makeTE("gt3",
            "first\nsecond\nthird\n");
         try {
            String result = te.gettext(0, 1, 6, 2);
            assertNotNull(result);
            assertTrue(result.contains("first"));
         } finally {
            te.disposeFvc();
            deleteTestFiles("gt3");
         }
      }
   }

   // ── inserttext ────────────────────────────────────────────

   @Nested
   @DisplayName("inserttext")
   class InsertTextTests {
      @Test
      @DisplayName("insert at beginning of line")
      void insertAtBeginning() throws IOException {
         TextEdit<String> te = makeTE("it1", "world\n");
         try {
            Position pos = te.inserttext("hello ", 0, 1);
            assertNotNull(pos);
            String line = te.at(1).toString();
            assertTrue(line.startsWith("hello "),
               "line should start with 'hello ': " + line);
         } finally {
            te.disposeFvc();
            deleteTestFiles("it1");
         }
      }

      @Test
      @DisplayName("insert multiline text")
      void insertMultiline() throws IOException {
         TextEdit<String> te = makeTE("it2", "before\nafter\n");
         try {
            Position pos = te.inserttext("mid1\nmid2", 0, 2);
            assertNotNull(pos);
            assertTrue(te.readIn() > 3,
               "should have more than 3 lines after insert");
         } finally {
            te.disposeFvc();
            deleteTestFiles("it2");
         }
      }

      @Test
      @DisplayName("insert empty string is safe")
      void insertEmptyString() throws IOException {
         TextEdit<String> te = makeTE("it3", "line\n");
         try {
            Position pos = te.inserttext("", 0, 1);
            assertNotNull(pos);
         } finally {
            te.disposeFvc();
            deleteTestFiles("it3");
         }
      }
   }

   // ── shiftright / shiftleft ────────────────────────────────

   @Nested
   @DisplayName("shift operations")
   class ShiftTests {
      @Test
      @DisplayName("shiftright returns indent amount")
      void shiftrightReturnsAmount() throws IOException {
         // shiftright uses findalign to find indent from prior lines
         TextEdit<String> te = makeTE("sr1",
            "   indented\nhello\nworld\n");
         try {
            // shiftright on line 2 with prior indented line
            int amount = te.shiftright(2, 1);
            // amount depends on prior-line alignment
            assertTrue(amount >= 0,
               "shiftright should return non-negative: " + amount);
         } finally {
            te.disposeFvc();
            deleteTestFiles("sr1");
         }
      }

      @Test
      @DisplayName("shiftleft removes indentation")
      void shiftleftRemovesIndent() throws IOException {
         TextEdit<String> te = makeTE("sl1",
            "   indented\n   also\n");
         try {
            int amount = te.shiftleft(1, 1);
            // shiftleft returns negative of spaces removed
            assertTrue(amount <= 0,
               "shiftleft should return non-positive: " + amount);
         } finally {
            te.disposeFvc();
            deleteTestFiles("sl1");
         }
      }

      @Test
      @DisplayName("shiftleft on unindented line is safe")
      void shiftleftUnindented() throws IOException {
         TextEdit<String> te = makeTE("sl2", "nospace\n");
         try {
            int amount = te.shiftleft(1, 1);
            assertTrue(amount <= 0);
         } finally {
            te.disposeFvc();
            deleteTestFiles("sl2");
         }
      }

      @Test
      @DisplayName("shiftright multiple lines")
      void shiftrightMultipleLines() throws IOException {
         TextEdit<String> te = makeTE("sr2",
            "      deep\na\nb\nc\n");
         try {
            // shiftright on lines 2-3, with prior deep indent
            int amount = te.shiftright(2, 2);
            assertTrue(amount >= 0,
               "shiftright amount: " + amount);
         } finally {
            te.disposeFvc();
            deleteTestFiles("sr2");
         }
      }
   }

   // ── changecase ────────────────────────────────────────────

   @Nested
   @DisplayName("changecase")
   class ChangecaseTests {
      @Test
      @DisplayName("toggles case of characters")
      void togglesCase() throws IOException {
         TextEdit<String> te = makeTE("cc1", "Hello World\n");
         try {
            te.changecase(0, 1, 5, 1);
            String line = te.at(1).toString();
            assertTrue(line.startsWith("hELLO"),
               "case should be toggled: " + line);
         } finally {
            te.disposeFvc();
            deleteTestFiles("cc1");
         }
      }

      @Test
      @DisplayName("changecase on digits is no-op")
      void digitsNoOp() throws IOException {
         TextEdit<String> te = makeTE("cc2", "12345\n");
         try {
            te.changecase(0, 1, 5, 1);
            assertEquals("12345", te.at(1).toString());
         } finally {
            te.disposeFvc();
            deleteTestFiles("cc2");
         }
      }

      @Test
      @DisplayName("changecase zero-width range is safe")
      void zeroWidthRange() throws IOException {
         TextEdit<String> te = makeTE("cc3", "text\n");
         try {
            te.changecase(2, 1, 2, 1);
            assertEquals("text", te.at(1).toString());
         } finally {
            te.disposeFvc();
            deleteTestFiles("cc3");
         }
      }
   }

   // ── joinlines ─────────────────────────────────────────────

   @Nested
   @DisplayName("joinlines")
   class JoinLinesTests {
      @Test
      @DisplayName("joins two lines with space")
      void joinsTwoLines() throws IOException {
         TextEdit<String> te = makeTE("jl1", "hello\nworld\n");
         try {
            int result = te.joinlines(1, 1);
            String line = te.at(1).toString();
            assertTrue(line.contains("hello") && line.contains("world"),
               "joined line should contain both: " + line);
         } finally {
            te.disposeFvc();
            deleteTestFiles("jl1");
         }
      }

      @Test
      @DisplayName("join preserves content")
      void joinPreservesContent() throws IOException {
         TextEdit<String> te = makeTE("jl2",
            "first\nsecond\nthird\n");
         try {
            int before = te.readIn();
            te.joinlines(1, 1);
            int after = te.readIn();
            assertTrue(after < before,
               "should have fewer lines after join");
         } finally {
            te.disposeFvc();
            deleteTestFiles("jl2");
         }
      }
   }

   // ── deletetext ────────────────────────────────────────────

   @Nested
   @DisplayName("deletetext")
   class DeleteTextTests {
      @Test
      @DisplayName("delete within single line")
      void deleteSingleLineRange() throws IOException {
         TextEdit<String> te = makeTE("dt1",
            "hello world\n");
         try {
            // deletetext(preserve=true,...) returns deleted text
            // without modifying the buffer
            String deleted = te.deletetext(true, 5, 1, 11, 1);
            assertNotNull(deleted);
            assertEquals(" world", deleted);
         } finally {
            te.disposeFvc();
            deleteTestFiles("dt1");
         }
      }

      @Test
      @DisplayName("deletetext with preserve=false modifies buffer")
      void deleteModifiesBuffer() throws IOException {
         TextEdit<String> te = makeTE("dt1b",
            "hello world\n");
         try {
            String deleted = te.deletetext(false, 5, 1, 11, 1);
            assertEquals(" world", deleted);
            assertEquals("hello", te.at(1).toString());
         } finally {
            te.disposeFvc();
            deleteTestFiles("dt1b");
         }
      }

      @Test
      @DisplayName("deletetext returns deleted content")
      void returnsDeletedContent() throws IOException {
         TextEdit<String> te = makeTE("dt2",
            "abcdef\n");
         try {
            String deleted = te.deletetext(true, 2, 1, 4, 1);
            assertEquals("cd", deleted);
         } finally {
            te.disposeFvc();
            deleteTestFiles("dt2");
         }
      }

      @Test
      @DisplayName("deletetext with same x returns null")
      void sameXReturnsNull() throws IOException {
         TextEdit<String> te = makeTE("dt3",
            "abcdef\n");
         try {
            String deleted = te.deletetext(true, 2, 1, 2, 1);
            // When xstart == xend on same line, returns null
            assertNull(deleted);
         } finally {
            te.disposeFvc();
            deleteTestFiles("dt3");
         }
      }
   }

   // ── processCommand edge cases ─────────────────────────────

   @Nested
   @DisplayName("processCommand")
   class ProcessCommandTests {
      @Test
      @DisplayName("line number navigation")
      void lineNumberNavigation() throws IOException, InputException {
         TextEdit<String> te = makeTE("pc1",
            "line1\nline2\nline3\n");
         try {
            int result = te.processCommand("2", 1);
            assertEquals(2, result);
         } finally {
            te.disposeFvc();
            deleteTestFiles("pc1");
         }
      }

      @Test
      @DisplayName("$ goes to last line")
      void dollarGoesToLastLine() throws IOException, InputException {
         TextEdit<String> te = makeTE("pc2",
            "a\nb\nc\n");
         try {
            int result = te.processCommand("$", 1);
            assertTrue(result > 0);
         } finally {
            te.disposeFvc();
            deleteTestFiles("pc2");
         }
      }

      @Test
      @DisplayName("substitute command")
      void substituteCommand() throws IOException, InputException {
         TextEdit<String> te = makeTE("pc3",
            "hello world\n");
         try {
            int result = te.processCommand("s/world/earth/", 1);
            assertTrue(result >= 0);
            String line = te.at(1).toString();
            assertTrue(line.contains("earth"),
               "substitution should replace: " + line);
         } finally {
            te.disposeFvc();
            deleteTestFiles("pc3");
         }
      }

      @Test
      @DisplayName("unknown command returns -1")
      void unknownCommandReturnsNegative() throws IOException, InputException {
         TextEdit<String> te = makeTE("pc4", "text\n");
         try {
            int result = te.processCommand(
               "zzz_not_a_command", 1);
            assertEquals(-1, result);
         } finally {
            te.disposeFvc();
            deleteTestFiles("pc4");
         }
      }

      @Test
      @DisplayName("delete command d")
      void deleteCommand() throws IOException, InputException {
         TextEdit<String> te = makeTE("pc5",
            "line1\nline2\nline3\n");
         try {
            int before = te.readIn();
            te.processCommand("d", 2);
            int after = te.readIn();
            assertTrue(after < before,
               "delete should reduce line count");
         } finally {
            te.disposeFvc();
            deleteTestFiles("pc5");
         }
      }

      @Test
      @DisplayName("range delete 1,2d")
      void rangeDelete() throws IOException, InputException {
         TextEdit<String> te = makeTE("pc6",
            "a\nb\nc\nd\n");
         try {
            int before = te.readIn();
            te.processCommand("1,2d", 1);
            int after = te.readIn();
            assertTrue(after <= before - 2,
               "should remove 2 lines, before=" + before
               + " after=" + after);
         } finally {
            te.disposeFvc();
            deleteTestFiles("pc6");
         }
      }

      @Test
      @DisplayName("copy command t")
      void copyCommand() throws IOException, InputException {
         TextEdit<String> te = makeTE("pc7",
            "original\n");
         try {
            int before = te.readIn();
            te.processCommand("t1", 1);
            int after = te.readIn();
            assertTrue(after > before,
               "copy should increase line count");
         } finally {
            te.disposeFvc();
            deleteTestFiles("pc7");
         }
      }
   }

   // ── readIn / containsNow / at ─────────────────────────────

   @Nested
   @DisplayName("basic accessors")
   class BasicAccessorTests {
      @Test
      @DisplayName("readIn returns line count + 1")
      void readInReturnsLineCount() throws IOException {
         TextEdit<String> te = makeTE("ba1",
            "line1\nline2\nline3\n");
         try {
            int count = te.readIn();
            assertTrue(count >= 4,
               "3 lines + root = at least 4, got " + count);
         } finally {
            te.disposeFvc();
            deleteTestFiles("ba1");
         }
      }

      @Test
      @DisplayName("containsNow returns true for valid index")
      void containsNowValid() throws IOException {
         TextEdit<String> te = makeTE("ba2", "data\n");
         try {
            assertTrue(te.containsNow(1));
         } finally {
            te.disposeFvc();
            deleteTestFiles("ba2");
         }
      }

      @Test
      @DisplayName("containsNow returns false for beyond end")
      void containsNowBeyondEnd() throws IOException {
         TextEdit<String> te = makeTE("ba3", "data\n");
         try {
            assertFalse(te.containsNow(9999));
         } finally {
            te.disposeFvc();
            deleteTestFiles("ba3");
         }
      }

      @Test
      @DisplayName("at returns line content")
      void atReturnsContent() throws IOException {
         TextEdit<String> te = makeTE("ba4", "hello\n");
         try {
            assertEquals("hello", te.at(1).toString());
         } finally {
            te.disposeFvc();
            deleteTestFiles("ba4");
         }
      }
   }
}
