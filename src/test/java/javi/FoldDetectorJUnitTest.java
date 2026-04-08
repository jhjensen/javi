package javi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class FoldDetectorJUnitTest {

   private static FoldDetector.LineFetcher arrayFetcher(
         String... lines) {
      return lineNumber -> {
         if (lineNumber < 1 || lineNumber > lines.length)
            return null;
         return lines[lineNumber - 1];
      };
   }

   // --- JSON/brace fold detection ---

   @Test void jsonFoldsSimpleBraces() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "{",     // 1
         "  a",   // 2
         "}"      // 3
      );
      FoldModel m = FoldDetector.detectJsonFolds(buf, 4);
      List<FoldModel.FoldRange> folds = m.getFolds();
      assertEquals(1, folds.size());
      assertEquals(1, folds.get(0).startLine);
      assertEquals(3, folds.get(0).endLine);
   }

   @Test void jsonFoldsNestedBraces() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "{",     // 1
         "  {",   // 2
         "    x", // 3
         "  }",   // 4
         "}"      // 5
      );
      FoldModel m = FoldDetector.detectJsonFolds(buf, 6);
      List<FoldModel.FoldRange> folds = m.getFolds();
      assertEquals(2, folds.size());
   }

   @Test void jsonFoldsSquareBrackets() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "[",    // 1
         "  1,", // 2
         "  2",  // 3
         "]"     // 4
      );
      FoldModel m = FoldDetector.detectJsonFolds(buf, 5);
      List<FoldModel.FoldRange> folds = m.getFolds();
      assertEquals(1, folds.size());
      assertEquals(1, folds.get(0).startLine);
      assertEquals(4, folds.get(0).endLine);
   }

   @Test void jsonFoldsSingleLine() {
      FoldDetector.LineFetcher buf = arrayFetcher("{ }");
      FoldModel m = FoldDetector.detectJsonFolds(buf, 2);
      assertTrue(m.getFolds().isEmpty(),
         "single-line brace pair should not create fold");
   }

   @Test void jsonFoldsEmptyBuffer() {
      FoldDetector.LineFetcher buf = arrayFetcher();
      FoldModel m = FoldDetector.detectJsonFolds(buf, 1);
      assertTrue(m.getFolds().isEmpty());
   }

   @Test void jsonFoldsUnmatchedOpen() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "{",   // 1
         "  x"  // 2
      );
      FoldModel m = FoldDetector.detectJsonFolds(buf, 3);
      assertTrue(m.getFolds().isEmpty(),
         "unmatched open brace should not produce fold");
   }

   @Test void jsonFoldsNullLine() {
      FoldDetector.LineFetcher buf = lineNumber -> null;
      FoldModel m = FoldDetector.detectJsonFolds(buf, 5);
      assertTrue(m.getFolds().isEmpty());
   }

   // --- Indent fold detection ---

   @Test void indentFoldsBasic() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "def foo():",  // 1 level 0
         "   bar()",    // 2 level 1
         "   baz()",    // 3 level 1
         "end"          // 4 level 0
      );
      FoldModel m =
         FoldDetector.detectIndentFolds(buf, 5, 3);
      assertFalse(m.getFolds().isEmpty());
      FoldModel.FoldRange f = m.getFolds().get(0);
      assertEquals(1, f.startLine);
   }

   @Test void indentFoldsEmptyBuffer() {
      FoldDetector.LineFetcher buf = arrayFetcher();
      FoldModel m =
         FoldDetector.detectIndentFolds(buf, 1, 3);
      assertTrue(m.getFolds().isEmpty());
   }

   @Test void indentFoldsTwoLineBuffer() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "hello"
      );
      FoldModel m =
         FoldDetector.detectIndentFolds(buf, 2, 3);
      assertTrue(m.getFolds().isEmpty());
   }

   @Test void indentFoldsBlankLinesBridge() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "class Foo:",  // 1 level 0
         "   a()",      // 2 level 1
         "",            // 3 blank
         "   b()",      // 4 level 1
         "end"          // 5 level 0
      );
      FoldModel m =
         FoldDetector.detectIndentFolds(buf, 6, 3);
      assertFalse(m.getFolds().isEmpty(),
         "blank lines should not break indented folds");
   }

   @Test void indentFoldsTabSize() {
      assertEquals(0,
         FoldDetector.indentLevel("hello", 3));
      assertEquals(1,
         FoldDetector.indentLevel("   hello", 3));
      assertEquals(2,
         FoldDetector.indentLevel("      hello", 3));
      assertEquals(1,
         FoldDetector.indentLevel("\thello", 3));
   }

   @Test void indentFoldsZeroTabSize() {
      assertEquals(0,
         FoldDetector.indentLevel("   hello", 0));
   }

   // --- Marker fold detection ---

   @Test void markerFoldsBasic() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "// {{{",  // 1
         "code",    // 2
         "// }}}"   // 3
      );
      FoldModel m = FoldDetector.detectMarkerFolds(buf, 4);
      List<FoldModel.FoldRange> folds = m.getFolds();
      assertEquals(1, folds.size());
      assertEquals(1, folds.get(0).startLine);
      assertEquals(3, folds.get(0).endLine);
   }

   @Test void markerFoldsLeveled() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "// {{{1",      // 1
         "  // {{{2",    // 2
         "    code",     // 3
         "  // }}}2",    // 4
         "// }}}1"       // 5
      );
      FoldModel m = FoldDetector.detectMarkerFolds(buf, 6);
      assertEquals(2, m.getFolds().size());
   }

   @Test void markerFoldsUnmatchedExtendsToEOF() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "// {{{",    // 1
         "code",      // 2
         "more code"  // 3
      );
      FoldModel m = FoldDetector.detectMarkerFolds(buf, 4);
      List<FoldModel.FoldRange> folds = m.getFolds();
      assertEquals(1, folds.size());
      assertEquals(1, folds.get(0).startLine);
      assertEquals(3, folds.get(0).endLine);
   }

   @Test void markerFoldsEmptyBuffer() {
      FoldDetector.LineFetcher buf = arrayFetcher();
      FoldModel m = FoldDetector.detectMarkerFolds(buf, 1);
      assertTrue(m.getFolds().isEmpty());
   }

   @Test void markerFoldsTwoLineBuffer() {
      FoldDetector.LineFetcher buf = arrayFetcher("x");
      FoldModel m = FoldDetector.detectMarkerFolds(buf, 2);
      assertTrue(m.getFolds().isEmpty());
   }

   // --- parseMarkerLevel ---

   @Test void parseMarkerLevelDigit() {
      assertEquals(1,
         FoldDetector.parseMarkerLevel("{{{1", 3));
      assertEquals(0,
         FoldDetector.parseMarkerLevel("{{{0", 3));
      assertEquals(9,
         FoldDetector.parseMarkerLevel("{{{9", 3));
   }

   @Test void parseMarkerLevelNoDigit() {
      assertEquals(-1,
         FoldDetector.parseMarkerLevel("{{{ ", 3));
      assertEquals(-1,
         FoldDetector.parseMarkerLevel("{{{", 3));
   }

   // --- Markdown fold detection ---

   @Test void markdownFoldsHeaders() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "# Top",         // 1
         "intro text",    // 2
         "## Section A",  // 3
         "some content",  // 4
         "## Section B",  // 5
         "more content"   // 6
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 7);
      List<FoldModel.FoldRange> folds = m.getFolds();
      // Should have 3 folds: # top (1-6), ## A (3-4), ## B (5-6)
      assertEquals(3, folds.size());
   }

   @Test void markdownFoldsNestedHeaders() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "# H1",       // 1
         "## H2",      // 2
         "### H3",     // 3
         "text",       // 4
         "## H2b",     // 5
         "text"        // 6
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 7);
      List<FoldModel.FoldRange> folds = m.getFolds();
      // H1 (1-6), H2 (2-4), H3 (3-4), H2b (5-6)
      assertEquals(4, folds.size());
   }

   @Test void markdownFoldsEmpty() {
      FoldDetector.LineFetcher buf = arrayFetcher("just text");
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 2);
      assertTrue(m.getFolds().isEmpty());
   }

   @Test void markdownFoldsSingleHeader() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "# Title",
         "body"
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 3);
      List<FoldModel.FoldRange> folds = m.getFolds();
      assertEquals(1, folds.size());
      assertEquals(1, folds.get(0).startLine); // start
      assertEquals(2, folds.get(0).endLine); // end
   }

   @Test void markdownHeaderLevelParsing() {
      assertEquals(1, FoldDetector.markdownHeaderLevel("# H1"));
      assertEquals(2, FoldDetector.markdownHeaderLevel("## H2"));
      assertEquals(3,
         FoldDetector.markdownHeaderLevel("### H3"));
      assertEquals(6,
         FoldDetector.markdownHeaderLevel("###### H6"));
      assertEquals(0,
         FoldDetector.markdownHeaderLevel("####### seven"));
      assertEquals(0,
         FoldDetector.markdownHeaderLevel("#nospace"));
      assertEquals(0,
         FoldDetector.markdownHeaderLevel("not a header"));
   }
}
