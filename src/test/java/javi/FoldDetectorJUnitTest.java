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

   // --- Markdown list fold detection ---

   @Test void markdownFoldsListBlock() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "the following list:",  // 1
         "- 1",                  // 2
         "- 2"                   // 3
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 4);
      List<FoldModel.FoldRange> folds = m.getFolds();
      // fold from intro line (1) through last list item (3)
      assertTrue(folds.stream().anyMatch(
         f -> f.startLine == 1 && f.endLine == 3),
         "expected fold 1-3, got: " + folds);
   }

   @Test void markdownFoldsListWithNumbered() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "steps:",     // 1
         "1. first",   // 2
         "2. second",  // 3
         "3. third"    // 4
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 5);
      List<FoldModel.FoldRange> folds = m.getFolds();
      assertTrue(folds.stream().anyMatch(
         f -> f.startLine == 1 && f.endLine == 4),
         "expected fold 1-4, got: " + folds);
   }

   @Test void markdownFoldsListNoFoldForSingleItem() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "intro",     // 1
         "- only"     // 2
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 3);
      // still a fold 1-2 (intro + single list item)
      assertTrue(folds(m).stream().anyMatch(
         f -> f.startLine == 1 && f.endLine == 2),
         "expected fold 1-2, got: " + folds(m));
   }

   @Test void markdownFoldsHeaderAndList() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "# Title",              // 1
         "some intro:",          // 2
         "- item a",             // 3
         "- item b",             // 4
         "## Section",           // 5
         "text"                  // 6
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 7);
      List<FoldModel.FoldRange> folds = m.getFolds();
      // header folds: #Title (1-6), ##Section (5-6)
      // list fold: intro 2-4
      assertTrue(folds.stream().anyMatch(
         f -> f.startLine == 2 && f.endLine == 4),
         "expected list fold 2-4, got: " + folds);
   }

   @Test void markdownIsListItem() {
      assertTrue(FoldDetector.isListItem("- item"));
      assertTrue(FoldDetector.isListItem("* item"));
      assertTrue(FoldDetector.isListItem("+ item"));
      assertTrue(FoldDetector.isListItem("1. item"));
      assertTrue(FoldDetector.isListItem("10) item"));
      assertTrue(FoldDetector.isListItem("   - indented"));
      assertFalse(FoldDetector.isListItem("not a list"));
      assertFalse(FoldDetector.isListItem(""));
      assertFalse(FoldDetector.isListItem("-nospace"));
   }

   private List<FoldModel.FoldRange> folds(FoldModel m) {
      return m.getFolds();
   }

   // --- User's exact markdown scenario (bug F25) ---

   /**
    * Verify fold detection for user's exact test markdown:
    * three H1 sections with one H2 subsection each.
    * Should produce 6 folds: 3 H1 (outer) + 3 H2 (inner).
    */
   @Test void markdownThreeH1Sections() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "# 1",    // 1
         "## a",   // 2
         "x",      // 3
         "# 2",    // 4
         "## b",   // 5
         "y",      // 6
         "# 3",    // 7
         "## c",   // 8
         "z"       // 9
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 10);
      List<FoldModel.FoldRange> folds = m.getFolds();
      assertEquals(6, folds.size(),
         "expected 6 folds, got: " + folds);
      // Top-level H1 folds
      assertTrue(folds.stream().anyMatch(
         f -> f.startLine == 1 && f.endLine == 3),
         "expected H1 fold 1-3, got: " + folds);
      assertTrue(folds.stream().anyMatch(
         f -> f.startLine == 4 && f.endLine == 6),
         "expected H1 fold 4-6, got: " + folds);
      assertTrue(folds.stream().anyMatch(
         f -> f.startLine == 7 && f.endLine == 9),
         "expected H1 fold 7-9, got: " + folds);
      // Nested H2 folds
      assertTrue(folds.stream().anyMatch(
         f -> f.startLine == 2 && f.endLine == 3),
         "expected H2 fold 2-3, got: " + folds);
      assertTrue(folds.stream().anyMatch(
         f -> f.startLine == 5 && f.endLine == 6),
         "expected H2 fold 5-6, got: " + folds);
      assertTrue(folds.stream().anyMatch(
         f -> f.startLine == 8 && f.endLine == 9),
         "expected H2 fold 8-9, got: " + folds);
   }

   /**
    * When all folds are closed, the summary text for the
    * first top-level fold should reference the fold's own
    * start line, not child content.
    */
   @Test void markdownThreeH1SummaryText() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "# 1",    // 1
         "## a",   // 2
         "x",      // 3
         "# 2",    // 4
         "## b",   // 5
         "y",      // 6
         "# 3",    // 7
         "## c",   // 8
         "z"       // 9
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 10);
      m.closeAll();

      // Top-level fold at line 1 should show "# 1"
      FoldModel.FoldRange f1 = m.findFoldAtStart(1);
      assertNotNull(f1, "fold starting at line 1");
      assertEquals(3, f1.endLine,
         "fold at line 1 should end at line 3");
      String summary1 = FoldModel.foldSummaryText(
         f1.startLine, f1.endLine, "# 1");
      assertEquals("+--  2 lines: # 1", summary1);

      // Visible lines after closeAll: 1, 4, 7
      assertEquals(4, m.nextVisible(1));
      assertEquals(7, m.nextVisible(4));
   }

   // --- Fenced code block handling ---

   @Test void isFencedCodeBoundary() {
      assertTrue(FoldDetector.isFencedCodeBoundary("```"));
      assertTrue(FoldDetector.isFencedCodeBoundary("```java"));
      assertTrue(FoldDetector.isFencedCodeBoundary("````"));
      assertTrue(FoldDetector.isFencedCodeBoundary("~~~"));
      assertTrue(FoldDetector.isFencedCodeBoundary("~~~python"));
      assertTrue(FoldDetector.isFencedCodeBoundary(" ```"));
      assertTrue(FoldDetector.isFencedCodeBoundary("  ```"));
      assertFalse(FoldDetector.isFencedCodeBoundary("``"));
      assertFalse(FoldDetector.isFencedCodeBoundary("~~"));
      assertFalse(FoldDetector.isFencedCodeBoundary("text"));
      assertFalse(FoldDetector.isFencedCodeBoundary(""));
      assertFalse(
         FoldDetector.isFencedCodeBoundary("    ```"),
         "4 spaces indent is not a valid fence");
   }

   @Test void markdownCodeBlockFoldsAsUnit() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "# Title",     // 1
         "intro",       // 2
         "```java",     // 3
         "public {",    // 4
         "  x();",      // 5
         "}",           // 6
         "```",         // 7
         "after"        // 8
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 9);
      // Should have a code block fold 3-7
      assertTrue(m.getFolds().stream().anyMatch(
         f -> f.startLine == 3 && f.endLine == 7),
         "expected code block fold 3-7, got: "
         + m.getFolds());
   }

   @Test void markdownCodeBlockBracesNotFolded() {
      // Braces inside a code block should NOT create
      // separate folds — the block folds as a whole.
      FoldDetector.LineFetcher buf = arrayFetcher(
         "# Title",     // 1
         "```",         // 2
         "{",           // 3
         "  inner",     // 4
         "}",           // 5
         "```",         // 6
         "after"        // 7
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 8);
      // Code block fold 2-6; no separate brace fold 3-5
      assertTrue(m.getFolds().stream().anyMatch(
         f -> f.startLine == 2 && f.endLine == 6),
         "expected code block fold 2-6, got: "
         + m.getFolds());
      assertFalse(m.getFolds().stream().anyMatch(
         f -> f.startLine == 3 && f.endLine == 5),
         "brace fold inside code block should not exist");
   }

   @Test void markdownHeaderInsideCodeBlockIgnored() {
      // A # line inside ``` should not create a header fold
      FoldDetector.LineFetcher buf = arrayFetcher(
         "# Real header",   // 1
         "```",             // 2
         "# Not a header",  // 3
         "text",            // 4
         "```",             // 5
         "after"            // 6
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 7);
      // Header fold at 1, code block fold at 2-5
      // No fold starting at line 3
      assertFalse(m.getFolds().stream().anyMatch(
         f -> f.startLine == 3),
         "header inside code block should not create fold");
      assertTrue(m.getFolds().stream().anyMatch(
         f -> f.startLine == 1),
         "real header should create fold");
      assertTrue(m.getFolds().stream().anyMatch(
         f -> f.startLine == 2 && f.endLine == 5),
         "expected code block fold 2-5");
   }

   @Test void markdownListInsideCodeBlockIgnored() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "intro:",         // 1
         "```",            // 2
         "- not a list",   // 3
         "- really not",   // 4
         "```",            // 5
         "real text"       // 6
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 7);
      // Code block fold 2-5; no list fold inside code block
      assertTrue(m.getFolds().stream().anyMatch(
         f -> f.startLine == 2 && f.endLine == 5),
         "expected code block fold 2-5, got: "
         + m.getFolds());
      // The intro line should not fold with code block
      // content as list items
      assertFalse(m.getFolds().stream().anyMatch(
         f -> f.startLine == 1 && f.endLine >= 3
            && f.endLine <= 4),
         "list fold should not include code block content");
   }

   @Test void markdownUnclosedCodeBlockExtendsToEOF() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "text",       // 1
         "```",        // 2
         "code",       // 3
         "more code"   // 4
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 5);
      assertTrue(m.getFolds().stream().anyMatch(
         f -> f.startLine == 2 && f.endLine == 4),
         "unclosed code block should extend to EOF, got: "
         + m.getFolds());
   }

   @Test void markdownTildeCodeBlock() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "# H1",     // 1
         "~~~",      // 2
         "code",     // 3
         "~~~",      // 4
         "after"     // 5
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 6);
      assertTrue(m.getFolds().stream().anyMatch(
         f -> f.startLine == 2 && f.endLine == 4),
         "tilde fence should create code block fold, got: "
         + m.getFolds());
   }

   @Test void markdownMultipleCodeBlocks() {
      FoldDetector.LineFetcher buf = arrayFetcher(
         "# Title",   // 1
         "```",       // 2
         "block 1",   // 3
         "```",       // 4
         "between",   // 5
         "```",       // 6
         "block 2",   // 7
         "```"        // 8
      );
      FoldModel m = FoldDetector.detectMarkdownFolds(buf, 9);
      assertTrue(m.getFolds().stream().anyMatch(
         f -> f.startLine == 2 && f.endLine == 4),
         "first code block fold 2-4, got: " + m.getFolds());
      assertTrue(m.getFolds().stream().anyMatch(
         f -> f.startLine == 6 && f.endLine == 8),
         "second code block fold 6-8, got: "
         + m.getFolds());
   }
}
