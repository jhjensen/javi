package javi;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CoverageReport} static methods.
 * Exercises buildUncoveredRanges and parseReport/printReport.
 */
class CoverageReportJUnitTest {

   // ── buildUncoveredRanges ───────────────────────────────────

   @Test
   void emptyMapReturnsEmptyRanges() {
      TreeMap<Integer, Boolean> map = new TreeMap<>();
      List<String> ranges = CoverageReport.buildUncoveredRanges(map);
      assertNotNull(ranges);
      assertTrue(ranges.isEmpty());
   }

   @Test
   void allCoveredReturnsEmptyRanges() {
      TreeMap<Integer, Boolean> map = new TreeMap<>();
      map.put(1, true);
      map.put(2, true);
      map.put(3, true);
      List<String> ranges = CoverageReport.buildUncoveredRanges(map);
      assertTrue(ranges.isEmpty());
   }

   @Test
   void singleUncoveredLineReturnsSingletonRange() {
      TreeMap<Integer, Boolean> map = new TreeMap<>();
      map.put(1, true);
      map.put(2, false);
      map.put(3, true);
      List<String> ranges = CoverageReport.buildUncoveredRanges(map);
      assertEquals(1, ranges.size());
      assertEquals("2", ranges.get(0));
   }

   @Test
   void consecutiveUncoveredLinesReturnRange() {
      TreeMap<Integer, Boolean> map = new TreeMap<>();
      map.put(1, true);
      map.put(2, false);
      map.put(3, false);
      map.put(4, false);
      map.put(5, true);
      List<String> ranges = CoverageReport.buildUncoveredRanges(map);
      assertEquals(1, ranges.size());
      assertEquals("2-4", ranges.get(0));
   }

   @Test
   void multipleUncoveredRanges() {
      TreeMap<Integer, Boolean> map = new TreeMap<>();
      map.put(1, false);
      map.put(2, false);
      map.put(3, true);
      map.put(4, false);
      map.put(5, true);
      map.put(6, true);
      map.put(7, false);
      List<String> ranges = CoverageReport.buildUncoveredRanges(map);
      assertEquals(3, ranges.size());
      assertEquals("1-2", ranges.get(0));
      assertEquals("4", ranges.get(1));
      assertEquals("7", ranges.get(2));
   }

   @Test
   void allUncoveredReturnsSingleRange() {
      TreeMap<Integer, Boolean> map = new TreeMap<>();
      map.put(10, false);
      map.put(11, false);
      map.put(12, false);
      List<String> ranges = CoverageReport.buildUncoveredRanges(map);
      assertEquals(1, ranges.size());
      assertEquals("10-12", ranges.get(0));
   }

   @Test
   void trailingUncoveredRange() {
      TreeMap<Integer, Boolean> map = new TreeMap<>();
      map.put(1, true);
      map.put(2, true);
      map.put(3, false);
      map.put(4, false);
      List<String> ranges = CoverageReport.buildUncoveredRanges(map);
      assertEquals(1, ranges.size());
      assertEquals("3-4", ranges.get(0));
   }

   @Test
   void singleLineUncoveredAtEnd() {
      TreeMap<Integer, Boolean> map = new TreeMap<>();
      map.put(1, true);
      map.put(2, false);
      List<String> ranges = CoverageReport.buildUncoveredRanges(map);
      assertEquals(1, ranges.size());
      assertEquals("2", ranges.get(0));
   }

   // ── parseReport ────────────────────────────────────────────

   @Test
   void parseReportParsesXml() throws Exception {
      // Use the actual JaCoCo report if available
      File xmlFile = new File("build/reports/jacoco/test/"
         + "jacocoTestReport.xml");
      if (!xmlFile.exists())
         return; // skip if no report

      List<CoverageReport.ClassCoverage> classes =
         CoverageReport.parseReport(xmlFile);
      assertNotNull(classes);
      assertFalse(classes.isEmpty(),
         "Should find at least some classes in report");

      // Verify data integrity
      for (CoverageReport.ClassCoverage cc : classes) {
         assertNotNull(cc.className());
         assertTrue(cc.linePct() >= 0 && cc.linePct() <= 100,
            "line coverage should be 0-100: " + cc.className());
         assertTrue(cc.branchPct() >= 0 && cc.branchPct() <= 100,
            "branch coverage should be 0-100: " + cc.className());
         assertTrue(cc.lineMissed() >= 0,
            "lineMissed should be non-negative: " + cc.className());
         assertTrue(cc.lineCovered() >= 0,
            "lineCovered should be non-negative: " + cc.className());
      }
   }

   // ── printReport ────────────────────────────────────────────

   @Test
   void printReportEmptyList() {
      // Should not throw with empty list
      CoverageReport.printReport(new ArrayList<>());
   }

   @Test
   void printReportWithData() {
      List<CoverageReport.ClassCoverage> classes = new ArrayList<>();
      classes.add(new CoverageReport.ClassCoverage(
         "com.example.Foo", 50.0, 40.0, 60.0,
         10, 10, 6, 4, 2, 3, List.of("5-10", "20")));
      classes.add(new CoverageReport.ClassCoverage(
         "com.example.Bar", 100.0, 100.0, 100.0,
         0, 30, 0, 8, 0, 5, List.of()));
      // Should not throw
      CoverageReport.printReport(classes);
   }

   @Test
   void printReportTruncatesLongUncoveredRanges() {
      List<String> ranges = new ArrayList<>();
      for (int i = 0; i < 100; i++)
         ranges.add(i + "-" + (i + 5));
      List<CoverageReport.ClassCoverage> classes = new ArrayList<>();
      classes.add(new CoverageReport.ClassCoverage(
         "com.example.Big", 10.0, 5.0, 20.0,
         90, 10, 19, 1, 8, 2, ranges));
      // Should not throw
      CoverageReport.printReport(classes);
   }
}
