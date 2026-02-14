package javi.lsp;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * JUnit 5 tests for {@link LspChangeEvent} factory methods.
 *
 * <p>Verifies correct LSP position computation for each edit type.
 * All tests use the document model where ecache is 1-indexed and
 * LSP positions are 0-indexed.</p>
 */
class LspChangeEventJUnitTest {

   // === Modify tests ===

   @Nested
   @DisplayName("forModify")
   class ForModify {

      @Test
      @DisplayName("single line modify — correct range and text")
      void modifySingleLine() {
         // ecache[2] = "bbb" -> "xxx" (1-based index 2 = LSP line 1)
         LspChangeEvent evt = LspChangeEvent.forModify(2, "bbb", "xxx");
         assertEquals(1, evt.startLine);
         assertEquals(0, evt.startChar);
         assertEquals(1, evt.endLine);
         assertEquals(3, evt.endChar);
         assertEquals("xxx", evt.text);
      }

      @Test
      @DisplayName("modify first line")
      void modifyFirstLine() {
         LspChangeEvent evt = LspChangeEvent.forModify(1, "aaa", "zzz");
         assertEquals(0, evt.startLine);
         assertEquals(0, evt.startChar);
         assertEquals(0, evt.endLine);
         assertEquals(3, evt.endChar);
         assertEquals("zzz", evt.text);
      }

      @Test
      @DisplayName("modify changes line length")
      void modifyChangesLength() {
         LspChangeEvent evt =
            LspChangeEvent.forModify(3, "short", "much longer text");
         assertEquals(2, evt.startLine);
         assertEquals(0, evt.startChar);
         assertEquals(2, evt.endLine);
         assertEquals(5, evt.endChar);
         assertEquals("much longer text", evt.text);
      }

      @Test
      @DisplayName("modify empty line to content")
      void modifyEmptyLine() {
         LspChangeEvent evt = LspChangeEvent.forModify(1, "", "hello");
         assertEquals(0, evt.startLine);
         assertEquals(0, evt.startChar);
         assertEquals(0, evt.endLine);
         assertEquals(0, evt.endChar);
         assertEquals("hello", evt.text);
      }
   }

   // === Insert tests ===

   @Nested
   @DisplayName("forInsert")
   class ForInsert {

      @Test
      @DisplayName("insert in middle of document")
      void insertMiddle() {
         // 3-line doc, insert ["xxx", "yyy"] at ecache[2]
         // Should be: range (1,0)-(1,0), text "xxx\nyyy\n"
         LspChangeEvent evt = LspChangeEvent.forInsert(
            2, new String[]{"xxx", "yyy"}, 3, 3);
         assertEquals(1, evt.startLine);
         assertEquals(0, evt.startChar);
         assertEquals(1, evt.endLine);
         assertEquals(0, evt.endChar);
         assertEquals("xxx\nyyy\n", evt.text);
      }

      @Test
      @DisplayName("insert at beginning")
      void insertAtBeginning() {
         // 3-line doc, insert ["new"] at ecache[1]
         LspChangeEvent evt = LspChangeEvent.forInsert(
            1, new String[]{"new"}, 3, 3);
         assertEquals(0, evt.startLine);
         assertEquals(0, evt.startChar);
         assertEquals(0, evt.endLine);
         assertEquals(0, evt.endChar);
         assertEquals("new\n", evt.text);
      }

      @Test
      @DisplayName("insert at end of document")
      void insertAtEnd() {
         // 3-line doc (last line len=3), insert at ecache[4]
         // Should be: range (2,3)-(2,3), text "\nxxx"
         LspChangeEvent evt = LspChangeEvent.forInsert(
            4, new String[]{"xxx"}, 3, 3);
         assertEquals(2, evt.startLine);
         assertEquals(3, evt.startChar);
         assertEquals(2, evt.endLine);
         assertEquals(3, evt.endChar);
         assertEquals("\nxxx", evt.text);
      }

      @Test
      @DisplayName("insert into empty document")
      void insertIntoEmpty() {
         LspChangeEvent evt = LspChangeEvent.forInsert(
            1, new String[]{"first", "second"}, 0, 0);
         assertEquals(0, evt.startLine);
         assertEquals(0, evt.startChar);
         assertEquals(0, evt.endLine);
         assertEquals(0, evt.endChar);
         assertEquals("first\nsecond", evt.text);
      }

      @Test
      @DisplayName("insert single line in middle")
      void insertSingleMiddle() {
         LspChangeEvent evt = LspChangeEvent.forInsert(
            2, new String[]{"new"}, 3, 5);
         assertEquals(1, evt.startLine);
         assertEquals(0, evt.startChar);
         assertEquals(1, evt.endLine);
         assertEquals(0, evt.endChar);
         assertEquals("new\n", evt.text);
      }

      @Test
      @DisplayName("insert multiple lines at end")
      void insertMultipleAtEnd() {
         // 2-line doc, last line len=4, insert at ecache[3]
         LspChangeEvent evt = LspChangeEvent.forInsert(
            3, new String[]{"aa", "bb", "cc"}, 2, 4);
         assertEquals(1, evt.startLine);
         assertEquals(4, evt.startChar);
         assertEquals(1, evt.endLine);
         assertEquals(4, evt.endChar);
         assertEquals("\naa\nbb\ncc", evt.text);
      }
   }

   // === Delete tests ===

   @Nested
   @DisplayName("forDelete")
   class ForDelete {

      @Test
      @DisplayName("delete in middle of document")
      void deleteMiddle() {
         // 5-line doc, delete line at ecache[2] (1 line)
         // Range: (1,0)-(2,0), text ""
         LspChangeEvent evt = LspChangeEvent.forDelete(
            2, 1, 5, 3, 5);
         assertEquals(1, evt.startLine);
         assertEquals(0, evt.startChar);
         assertEquals(2, evt.endLine);
         assertEquals(0, evt.endChar);
         assertEquals("", evt.text);
      }

      @Test
      @DisplayName("delete multiple lines in middle")
      void deleteMultipleMiddle() {
         // 5-line doc, delete ecache[2..3] (2 lines)
         // Range: (1,0)-(3,0), text ""
         LspChangeEvent evt = LspChangeEvent.forDelete(
            2, 2, 5, 3, 5);
         assertEquals(1, evt.startLine);
         assertEquals(0, evt.startChar);
         assertEquals(3, evt.endLine);
         assertEquals(0, evt.endChar);
         assertEquals("", evt.text);
      }

      @Test
      @DisplayName("delete last line")
      void deleteLastLine() {
         // 3-line doc: "aaa"(3), "bbb"(3), "ccc"(3)
         // Delete ecache[3] (last line). start=3, count=1
         // end=3 == oldLineCount(3), start=3 > 1
         // prevLen = len("bbb") = 3
         // Range: (1,3)-(2,3), text ""
         LspChangeEvent evt = LspChangeEvent.forDelete(
            3, 1, 3, 3, 3);
         assertEquals(1, evt.startLine);
         assertEquals(3, evt.startChar);
         assertEquals(2, evt.endLine);
         assertEquals(3, evt.endChar);
         assertEquals("", evt.text);
      }

      @Test
      @DisplayName("delete last two lines")
      void deleteLastTwo() {
         // 4-line doc: len(ecache[1])=3, len(ecache[4])=4
         // Delete ecache[3..4]. start=3, count=2
         // end=4 == oldLineCount(4), start=3 > 1
         // prevLen = len(ecache[2]), lastLen = len(ecache[4])=4
         LspChangeEvent evt = LspChangeEvent.forDelete(
            3, 2, 4, 5, 4);
         assertEquals(1, evt.startLine);
         assertEquals(5, evt.startChar);
         assertEquals(3, evt.endLine);
         assertEquals(4, evt.endChar);
         assertEquals("", evt.text);
      }

      @Test
      @DisplayName("delete all lines")
      void deleteAll() {
         // 3-line doc, delete all (start=1, count=3)
         // end=3 == oldLineCount(3), start=1 (not > 1)
         // Range: (0,0)-(2,3), text ""
         LspChangeEvent evt = LspChangeEvent.forDelete(
            1, 3, 3, -1, 3);
         assertEquals(0, evt.startLine);
         assertEquals(0, evt.startChar);
         assertEquals(2, evt.endLine);
         assertEquals(3, evt.endChar);
         assertEquals("", evt.text);
      }

      @Test
      @DisplayName("delete first line in multi-line doc")
      void deleteFirst() {
         // 3-line doc, delete ecache[1] (first line)
         // end=1 < oldLineCount(3): middle case
         // Range: (0,0)-(1,0), text ""
         LspChangeEvent evt = LspChangeEvent.forDelete(
            1, 1, 3, -1, 3);
         assertEquals(0, evt.startLine);
         assertEquals(0, evt.startChar);
         assertEquals(1, evt.endLine);
         assertEquals(0, evt.endChar);
         assertEquals("", evt.text);
      }
   }

   // === toContentChange tests ===

   @Nested
   @DisplayName("toContentChange")
   class ToContentChange {

      @Test
      @DisplayName("produces valid LSP map structure")
      @SuppressWarnings("unchecked")
      void validMapStructure() {
         LspChangeEvent evt = new LspChangeEvent(1, 5, 3, 10, "hello");
         Map<String, Object> map = evt.toContentChange();

         assertNotNull(map.get("range"));
         assertEquals("hello", map.get("text"));

         Map<String, Object> range = (Map<String, Object>) map.get("range");
         Map<String, Object> start =
            (Map<String, Object>) range.get("start");
         Map<String, Object> end =
            (Map<String, Object>) range.get("end");

         assertEquals(Integer.valueOf(1), start.get("line"));
         assertEquals(Integer.valueOf(5), start.get("character"));
         assertEquals(Integer.valueOf(3), end.get("line"));
         assertEquals(Integer.valueOf(10), end.get("character"));
      }
   }

   // === Sync kind parsing ===

   @Nested
   @DisplayName("getTextDocumentSyncKind")
   class SyncKindParsing {

      @Test
      @DisplayName("toString includes all fields")
      void toStringFormat() {
         LspChangeEvent evt = new LspChangeEvent(1, 2, 3, 4, "hi\nthere");
         String s = evt.toString();
         assertNotNull(s);
         assertEquals(true, s.contains("(1,2)"));
         assertEquals(true, s.contains("(3,4)"));
         assertEquals(true, s.contains("hi\\nthere"));
      }
   }
}
