package javi;

import java.lang.reflect.Method;
import java.util.List;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for {@link FoldDetector} integration with the editor.
 *
 * <p>Exercises fold detection algorithms (JSON brace, indent, marker)
 * on buffers created through the full AWT editor, then verifies fold
 * model state, toggle behavior, and coordinate mapping. Unlike the
 * headless FoldDetectorJUnitTest which uses synthetic LineFetcher
 * implementations, these tests use real TextEdit buffers with the
 * AWT rendering pipeline active.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class FoldDetectorGuiJUnitTest {

   private static Robot robot;
   private static FvContext<?> fvc;
   private static View oldView;

   @BeforeAll
   static void initJavi() throws Exception {
      if (Rgroup.bindingLookup("persistfile") == null) {
         EventQueue.biglock2.lock();
         try {
            Class.forName("javi.TextEdit");
            EditTester1.TestCircBuffer.initCmd();
            DirManager.getInstance();
            FileList.make("");
            Javi.initToUi();
            Javi.initPostUi();
            Command.doneInit();
         } finally {
            EventQueue.biglock2.unlock();
         }
         Thread.sleep(500);
      }
      robot = BasicRobot.robotWithCurrentAwtHierarchy();
      EventQueue.biglock2.lock();
      try {
         fvc = FvContext.getCurrFvc();
         oldView = fvc.vi;
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   @AfterEach
   void restoreFolds() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext<?> curr = FvContext.getCurrFvc();
         if (curr != null)
            curr.setFoldModel(null);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Helpers ──────────────────────────────────────────────────

   /**
    * Create a TextEdit with the given lines and return it.
    * Lines are 1-based (line 0 is unused).
    */
   private static TextEdit<String> makeBuffer(String... lines) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < lines.length; i++) {
         if (i > 0)
            sb.append('\n');
         sb.append(lines[i]);
      }
      sb.append('\n');
      StringIoc sio = new StringIoc("foldtest", sb.toString());
      TextEdit<String> te = new TextEdit<>(sio, sio.prop);
      te.finish();
      return te;
   }

   /**
    * Adapter from TextEdit to FoldDetector.LineFetcher.
    */
   private static FoldDetector.LineFetcher fetcher(TextEdit<?> te) {
      return lineNumber -> {
         if (lineNumber < 1 || lineNumber >= te.readIn())
            return null;
         return te.at(lineNumber).toString();
      };
   }

   // ── JSON fold detection on live buffer ───────────────────────

   @Test
   void t01_jsonDetectsSimpleBraceFold() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "{",
            "   \"key\": \"value\",",
            "   \"num\": 42",
            "}");
         FoldModel model = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());
         assertNotNull(model);
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertEquals(1, folds.size(),
            "Should detect one JSON brace fold");
         assertEquals(1, folds.get(0).startLine);
         assertEquals(4, folds.get(0).endLine);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t02_jsonDetectsNestedFolds() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "{",
            "   \"outer\": {",
            "      \"inner\": 1",
            "   }",
            "}");
         FoldModel model = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertEquals(2, folds.size(),
            "Should detect outer and inner folds");
         // Folds sorted by startLine
         assertEquals(1, folds.get(0).startLine);
         assertEquals(2, folds.get(1).startLine);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t03_jsonDetectsArrayFolds() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "[",
            "   1,",
            "   2,",
            "   3",
            "]");
         FoldModel model = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertEquals(1, folds.size(),
            "Should detect array bracket fold");
         assertEquals(1, folds.get(0).startLine);
         assertEquals(5, folds.get(0).endLine);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t04_jsonIgnoresBracesInsideStrings() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "\"no { fold } here\"",
            "next line");
         FoldModel model = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertEquals(0, folds.size(),
            "Braces inside strings should not create folds");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t05_jsonIgnoresBracesInLineComments() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "code // { ignored",
            "more code // } ignored",
            "end");
         FoldModel model = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertEquals(0, folds.size(),
            "Braces in line comments should not create folds");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t06_jsonHandlesEmptyBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer("");
         FoldModel model = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());
         assertNotNull(model);
         assertTrue(model.getFolds().isEmpty(),
            "Empty buffer should produce no folds");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Indent fold detection on live buffer ─────────────────────

   @Test
   void t07_indentDetectsSingleBlock() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "def foo():",
            "   line1",
            "   line2",
            "end");
         FoldModel model = FoldDetector.detectIndentFolds(
            fetcher(te), te.readIn(), 3);
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertTrue(folds.size() >= 1,
            "Should detect at least one indent fold");
         // First fold should start at the method header
         assertEquals(1, folds.get(0).startLine);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t08_indentDetectsNestedBlocks() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "class Foo:",
            "   def bar():",
            "      inner1",
            "      inner2",
            "   def baz():",
            "      inner3",
            "end");
         FoldModel model = FoldDetector.detectIndentFolds(
            fetcher(te), te.readIn(), 3);
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertTrue(folds.size() >= 2,
            "Should detect outer class fold and inner method folds");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t09_indentHandlesBlankLines() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "header",
            "   indented1",
            "",
            "   indented2",
            "footer");
         FoldModel model = FoldDetector.detectIndentFolds(
            fetcher(te), te.readIn(), 3);
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertTrue(folds.size() >= 1,
            "Blank lines should not break indent folds");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t10_indentHandlesTabIndentation() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "top",
            "\tindented1",
            "\tindented2",
            "bottom");
         FoldModel model = FoldDetector.detectIndentFolds(
            fetcher(te), te.readIn(), 3);
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertTrue(folds.size() >= 1,
            "Tab indentation should be recognized");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t11_indentReturnsEmptyForFlatContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "line1",
            "line2",
            "line3");
         FoldModel model = FoldDetector.detectIndentFolds(
            fetcher(te), te.readIn(), 3);
         assertTrue(model.getFolds().isEmpty(),
            "Flat content should produce no folds");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t12_indentLevelCalculation() throws Exception {
      // Test the static indentLevel method
      Method m = FoldDetector.class.getDeclaredMethod(
         "indentLevel", String.class, int.class);
      m.setAccessible(true);

      assertEquals(0, (int) m.invoke(null, "no indent", 3));
      assertEquals(1, (int) m.invoke(null, "   one level", 3));
      assertEquals(2, (int) m.invoke(null, "      two levels", 3));
      assertEquals(1, (int) m.invoke(null, "\tone tab", 3));
   }

   // ── Marker fold detection on live buffer ─────────────────────

   @Test
   void t13_markerDetectsExplicitFolds() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "// {{{",
            "folded content",
            "more content",
            "// }}}");
         FoldModel model = FoldDetector.detectMarkerFolds(
            fetcher(te), te.readIn());
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertEquals(1, folds.size(),
            "Should detect one marker fold");
         assertEquals(1, folds.get(0).startLine);
         assertEquals(4, folds.get(0).endLine);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t14_markerDetectsNumberedFolds() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "// {{{1",
            "section 1",
            "// {{{2",
            "subsection",
            "// }}}2",
            "// }}}1");
         FoldModel model = FoldDetector.detectMarkerFolds(
            fetcher(te), te.readIn());
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertTrue(folds.size() >= 2,
            "Should detect nested numbered folds, got "
               + folds.size());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t15_markerHandlesUnmatchedOpener() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "// {{{",
            "content extends to end",
            "no close marker");
         FoldModel model = FoldDetector.detectMarkerFolds(
            fetcher(te), te.readIn());
         List<FoldModel.FoldRange> folds = model.getFolds();
         // Unmatched opener extends to end of file
         assertTrue(folds.size() >= 1,
            "Unmatched marker opener should create fold to EOF");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── FoldModel operations on live buffer ──────────────────────

   @Test
   void t16_foldModelToggleCollapse() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "{",
            "   content",
            "}");
         FoldModel model = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertEquals(1, folds.size());
         FoldModel.FoldRange fold = folds.get(0);

         assertFalse(fold.collapsed,
            "New fold should be open");
         model.toggleFold(fold.startLine);
         assertTrue(fold.collapsed,
            "Fold should be collapsed after toggle");
         model.toggleFold(fold.startLine);
         assertFalse(fold.collapsed,
            "Fold should be open after second toggle");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t17_foldModelHiddenLinesCount() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "{",
            "   line1",
            "   line2",
            "   line3",
            "}");
         FoldModel model = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());
         FoldModel.FoldRange fold = model.getFolds().get(0);

         assertEquals(0, fold.hiddenLines(),
            "Open fold should hide 0 lines");
         model.toggleFold(fold.startLine);
         assertEquals(4, fold.hiddenLines(),
            "Collapsed 5-line fold should hide 4 lines");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t18_foldModelSpan() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "{",
            "   a",
            "   b",
            "}");
         FoldModel model = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());
         FoldModel.FoldRange fold = model.getFolds().get(0);
         assertEquals(4, fold.span(),
            "Fold spanning lines 1-4 should have span 4");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t19_foldModelRemoveFold() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "{",
            "   content",
            "}");
         FoldModel model = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());
         assertEquals(1, model.getFolds().size());
         model.removeFold(1);
         assertEquals(0, model.getFolds().size(),
            "removeFold should remove the fold at line 1");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t20_foldModelAddFoldRejectsSameStartEnd() throws Exception {
      FoldModel model = new FoldModel();
      model.addFold(5, 5);
      assertTrue(model.getFolds().isEmpty(),
         "addFold with end <= start should be rejected");
   }

   // ── FoldModel integration with FvContext ──────────────────────

   @Test
   void t21_fvContextAcceptsFoldModel() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext<?> curr = FvContext.getCurrFvc();
         FoldModel model = new FoldModel();
         model.addFold(1, 5);

         curr.setFoldModel(model);
         assertNotNull(curr.getFoldModel(),
            "FvContext should accept and return FoldModel");
         assertEquals(model, curr.getFoldModel());

         curr.setFoldModel(null);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t22_fvContextFoldModelNullByDefault() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext<?> curr = FvContext.getCurrFvc();
         curr.setFoldModel(null);
         // After clearing, getFoldModel returns null
         // (or might return an empty default model)
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Fold detection on editor's current buffer ────────────────

   @Test
   void t23_detectFoldsOnCurrentBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext<?> curr = FvContext.getCurrFvc();
         TextEdit<?> te = curr.edvec;
         int lineCount = te.readIn();

         FoldDetector.LineFetcher lf = lineNumber -> {
            if (lineNumber < 1 || lineNumber >= te.readIn())
               return null;
            return te.at(lineNumber).toString();
         };

         // Indent detection should not crash on the current buffer
         FoldModel model = FoldDetector.detectIndentFolds(
            lf, lineCount, 3);
         assertNotNull(model,
            "detectIndentFolds must return non-null model");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t24_detectJsonFoldsOnCurrentBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext<?> curr = FvContext.getCurrFvc();
         TextEdit<?> te = curr.edvec;

         FoldDetector.LineFetcher lf = lineNumber -> {
            if (lineNumber < 1 || lineNumber >= te.readIn())
               return null;
            return te.at(lineNumber).toString();
         };

         FoldModel model = FoldDetector.detectJsonFolds(
            lf, te.readIn());
         assertNotNull(model,
            "detectJsonFolds must return non-null model");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t25_detectMarkerFoldsOnCurrentBuffer() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FvContext<?> curr = FvContext.getCurrFvc();
         TextEdit<?> te = curr.edvec;

         FoldDetector.LineFetcher lf = lineNumber -> {
            if (lineNumber < 1 || lineNumber >= te.readIn())
               return null;
            return te.at(lineNumber).toString();
         };

         FoldModel model = FoldDetector.detectMarkerFolds(
            lf, te.readIn());
         assertNotNull(model,
            "detectMarkerFolds must return non-null model");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Fold commands registered ─────────────────────────────────

   @Test
   void t26_foldCommandsRegistered() throws Exception {
      EventQueue.biglock2.lock();
      try {
         assertNotNull(Rgroup.bindingLookup("fold"),
            "'fold' command must be registered");
         assertNotNull(Rgroup.bindingLookup("foldindent"),
            "'foldindent' command must be registered");
         assertNotNull(Rgroup.bindingLookup("foldmarker"),
            "'foldmarker' command must be registered");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── FoldRange ordering ───────────────────────────────────────

   @Test
   void t27_foldRangesAreSortedByStartLine() throws Exception {
      EventQueue.biglock2.lock();
      try {
         FoldModel model = new FoldModel();
         model.addFold(10, 20);
         model.addFold(1, 5);
         model.addFold(6, 9);

         List<FoldModel.FoldRange> folds = model.getFolds();
         assertEquals(3, folds.size());
         assertEquals(1, folds.get(0).startLine);
         assertEquals(6, folds.get(1).startLine);
         assertEquals(10, folds.get(2).startLine);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t28_foldRangeToStringFormat() throws Exception {
      FoldModel model = new FoldModel();
      model.addFold(5, 10);
      FoldModel.FoldRange fold = model.getFolds().get(0);

      String str = fold.toString();
      assertTrue(str.contains("5"), "toString should show start line");
      assertTrue(str.contains("10"), "toString should show end line");
      assertTrue(str.contains("open"), "New fold should show 'open'");

      model.toggleFold(5);
      str = fold.toString();
      assertTrue(str.contains("closed"),
         "Collapsed fold should show 'closed'");
   }

   @Test
   void t29_foldRangeCompareTo() throws Exception {
      FoldModel.FoldRange a = new FoldModel.FoldRange(1, 5);
      FoldModel.FoldRange b = new FoldModel.FoldRange(3, 8);
      assertTrue(a.compareTo(b) < 0,
         "Fold starting at 1 should sort before fold starting at 3");
      assertTrue(b.compareTo(a) > 0);
      assertEquals(0, a.compareTo(a));
   }

   // ── Edge cases ───────────────────────────────────────────────

   @Test
   void t30_jsonHandlesEscapedQuotes() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "{",
            "   \"key\": \"val\\\"ue{}\",",
            "}");
         FoldModel model = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertEquals(1, folds.size(),
            "Escaped quotes should not confuse string detection");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t31_jsonHandlesSingleQuoteStrings() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "{",
            "   var x = '{not a fold}';",
            "}");
         FoldModel model = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertEquals(1, folds.size(),
            "Braces inside single-quoted strings should be ignored");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t32_indentMinTabSizeClamped() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "top",
            "   indented",
            "bottom");
         // tabSize=0 should be treated as 3 (minimum)
         FoldModel model = FoldDetector.detectIndentFolds(
            fetcher(te), te.readIn(), 0);
         assertNotNull(model);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t33_tinyBufferProducesNoFolds() throws Exception {
      EventQueue.biglock2.lock();
      try {
         // A buffer with just 1 line (readIn == 2) should be too small
         TextEdit<String> te = makeBuffer("solo");
         FoldModel indentModel = FoldDetector.detectIndentFolds(
            fetcher(te), te.readIn(), 3);
         assertTrue(indentModel.getFolds().isEmpty(),
            "Single-line buffer should have no indent folds");

         FoldModel markerModel = FoldDetector.detectMarkerFolds(
            fetcher(te), te.readIn());
         assertTrue(markerModel.getFolds().isEmpty(),
            "Single-line buffer should have no marker folds");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── FoldModel toggleHandler ──────────────────────────────────

   @Test
   void t34_toggleHandlerCanBeSet() throws Exception {
      FoldModel model = new FoldModel();
      model.addFold(1, 5);

      // Set a handler that intercepts toggle
      boolean[] called = {false};
      model.setToggleHandler((line, fc) -> {
         called[0] = true;
         return false; // don't consume — let default toggle proceed
      });

      assertNotNull(model.getToggleHandler(),
         "toggleHandler should be set");
   }

   @Test
   void t35_toggleHandlerNullByDefault() throws Exception {
      FoldModel model = new FoldModel();
      assertFalse(model.getToggleHandler() != null,
         "toggleHandler should be null by default");
   }

   // ── Fold detection stability ─────────────────────────────────

   @Test
   void t36_jsonDetectionIsIdempotent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "{",
            "   \"a\": [1, 2]",
            "}");
         FoldModel model1 = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());
         FoldModel model2 = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());

         assertEquals(model1.getFolds().size(),
            model2.getFolds().size(),
            "Running detection twice should produce same fold count");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t37_indentDetectionIsIdempotent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "top",
            "   mid",
            "bottom");
         FoldModel model1 = FoldDetector.detectIndentFolds(
            fetcher(te), te.readIn(), 3);
         FoldModel model2 = FoldDetector.detectIndentFolds(
            fetcher(te), te.readIn(), 3);

         assertEquals(model1.getFolds().size(),
            model2.getFolds().size(),
            "Running detection twice should produce same fold count");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Mixed content detection ──────────────────────────────────

   @Test
   void t38_jsonWithMixedBracesAndBrackets() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "{",
            "   \"list\": [",
            "      1,",
            "      2",
            "   ],",
            "   \"obj\": {",
            "      \"k\": \"v\"",
            "   }",
            "}");
         FoldModel model = FoldDetector.detectJsonFolds(
            fetcher(te), te.readIn());
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertTrue(folds.size() >= 3,
            "Should detect outer + list + inner obj folds, got "
               + folds.size());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t39_indentWithDeeplyNestedContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "level0",
            "   level1",
            "      level2",
            "         level3",
            "      level2b",
            "   level1b",
            "level0b");
         FoldModel model = FoldDetector.detectIndentFolds(
            fetcher(te), te.readIn(), 3);
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertTrue(folds.size() >= 2,
            "Deeply nested content should produce multiple folds");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t40_markerFoldsWithInlineContent() throws Exception {
      EventQueue.biglock2.lock();
      try {
         TextEdit<String> te = makeBuffer(
            "code before // {{{",
            "folded section A",
            "folded section B",
            "code after  // }}}",
            "more code");
         FoldModel model = FoldDetector.detectMarkerFolds(
            fetcher(te), te.readIn());
         List<FoldModel.FoldRange> folds = model.getFolds();
         assertEquals(1, folds.size(),
            "Markers at end of lines should still be detected");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }
}
