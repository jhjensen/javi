package javi.lsp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link LspDiagnostics}.
 */
class LspDiagnosticsJUnitTest {

   private static Map<String, Object> diagnostic(int startLine,
         int startChar, int endLine, int endChar,
         int severity, String message) {
      Map<String, Object> start = new HashMap<>();
      start.put("line", startLine);
      start.put("character", startChar);

      Map<String, Object> end = new HashMap<>();
      end.put("line", endLine);
      end.put("character", endChar);

      Map<String, Object> range = new HashMap<>();
      range.put("start", start);
      range.put("end", end);

      Map<String, Object> diag = new HashMap<>();
      diag.put("range", range);
      diag.put("severity", severity);
      diag.put("message", message);
      return diag;
   }

   @Test
   @DisplayName("stores diagnostics per source without overwrite")
   void separateSourcesPerUri() {
      LspDiagnostics diagnostics = new LspDiagnostics();
      String uri = "file:///tmp/Test.java";

      diagnostics.update("jdtls", uri,
         List.of(diagnostic(1, 2, 1, 5, 1, "error one")));
      diagnostics.update("harper", uri,
         List.of(diagnostic(3, 1, 3, 4, 2, "warn one")));

      assertEquals(2, diagnostics.forUri(uri).size());
      assertEquals(1, diagnostics.forSource("jdtls").size());
      assertEquals(1, diagnostics.forSource("harper").size());
   }

   @Test
   @DisplayName("returns deterministic order for same uri")
   void deterministicOrdering() {
      LspDiagnostics diagnostics = new LspDiagnostics();
      String uri = "file:///tmp/Test.java";

      diagnostics.update("srcB", uri,
         List.of(diagnostic(8, 0, 8, 1, 2, "late")));
      diagnostics.update("srcA", uri,
         List.of(diagnostic(2, 0, 2, 1, 1, "early")));

      List<LspDiagnostics.Entry> entries = diagnostics.forUri(uri);
      assertEquals(2, entries.size());
      assertEquals(2, entries.get(0).startLine);
      assertEquals(8, entries.get(1).startLine);
   }

   @Test
   @DisplayName("atPosition includes both start and end boundary")
   void positionBoundaries() {
      LspDiagnostics diagnostics = new LspDiagnostics();
      String uri = "file:///tmp/Test.java";

      diagnostics.update("jdtls", uri,
         List.of(diagnostic(5, 2, 5, 6, 1, "range")));

      assertEquals(1, diagnostics.atPosition(uri, 5, 2).size());
      assertEquals(1, diagnostics.atPosition(uri, 5, 6).size());
      assertEquals(0, diagnostics.atPosition(uri, 5, 1).size());
      assertEquals(0, diagnostics.atPosition(uri, 5, 7).size());
   }

   @Test
   @DisplayName("summary tracks only errors and warnings")
   void summaryCounts() {
      LspDiagnostics diagnostics = new LspDiagnostics();
      String uri = "file:///tmp/Test.java";

      diagnostics.update("src1", uri,
         List.of(diagnostic(0, 0, 0, 1, 1, "err")));
      diagnostics.update("src2", uri,
         List.of(diagnostic(1, 0, 1, 1, 2, "warn"),
            diagnostic(2, 0, 2, 1, 3, "info")));

      assertEquals("E:1 W:1", diagnostics.summary());
   }

   @Test
   @DisplayName("position list output uses 1-based display coordinates")
   void posListFormatting() {
      LspDiagnostics diagnostics = new LspDiagnostics();
      String uri = "file:///tmp/Test.java";

      diagnostics.update("jdtls", uri,
         List.of(diagnostic(0, 0, 0, 2, 1, "boom")));
      String formatted = diagnostics.forUri(uri).get(0).toPosListEntry();

      assertTrue(formatted.contains("/tmp/Test.java(1,1)-[Error] boom"));
   }
}
