package javi;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage tests for {@link CoverageReport} — exercises
 * parseReport with synthetic JaCoCo XML and printReport output.
 */
class CoverageReportExtendedJUnitTest {

   private static final String MINIMAL_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<report name=\"test\">\n"
      + "  <package name=\"com/example\">\n"
      + "    <sourcefile name=\"Foo.java\">\n"
      + "      <line nr=\"10\" mi=\"5\" ci=\"0\" mb=\"0\" cb=\"0\"/>\n"
      + "      <line nr=\"11\" mi=\"0\" ci=\"3\" mb=\"0\" cb=\"0\"/>\n"
      + "      <line nr=\"12\" mi=\"4\" ci=\"0\" mb=\"0\" cb=\"0\"/>\n"
      + "      <line nr=\"13\" mi=\"0\" ci=\"2\" mb=\"0\" cb=\"0\"/>\n"
      + "      <counter type=\"LINE\" missed=\"9\" covered=\"5\"/>\n"
      + "      <counter type=\"BRANCH\" missed=\"2\" covered=\"3\"/>\n"
      + "      <counter type=\"METHOD\" missed=\"1\" covered=\"2\"/>\n"
      + "    </sourcefile>\n"
      + "  </package>\n"
      + "</report>\n";

   private static final String MULTI_PACKAGE_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<report name=\"multi\">\n"
      + "  <package name=\"pkg/alpha\">\n"
      + "    <sourcefile name=\"A.java\">\n"
      + "      <line nr=\"1\" mi=\"3\" ci=\"0\" mb=\"0\" cb=\"0\"/>\n"
      + "      <line nr=\"2\" mi=\"3\" ci=\"0\" mb=\"0\" cb=\"0\"/>\n"
      + "      <counter type=\"LINE\" missed=\"6\" covered=\"0\"/>\n"
      + "      <counter type=\"METHOD\" missed=\"2\" covered=\"0\"/>\n"
      + "    </sourcefile>\n"
      + "  </package>\n"
      + "  <package name=\"pkg/beta\">\n"
      + "    <sourcefile name=\"B.java\">\n"
      + "      <line nr=\"5\" mi=\"0\" ci=\"4\" mb=\"0\" cb=\"0\"/>\n"
      + "      <line nr=\"6\" mi=\"0\" ci=\"4\" mb=\"0\" cb=\"0\"/>\n"
      + "      <counter type=\"LINE\" missed=\"0\" covered=\"8\"/>\n"
      + "      <counter type=\"BRANCH\" missed=\"0\" covered=\"4\"/>\n"
      + "      <counter type=\"METHOD\" missed=\"0\" covered=\"3\"/>\n"
      + "    </sourcefile>\n"
      + "    <sourcefile name=\"C.java\">\n"
      + "      <line nr=\"10\" mi=\"2\" ci=\"1\" mb=\"0\" cb=\"0\"/>\n"
      + "      <counter type=\"LINE\" missed=\"2\" covered=\"1\"/>\n"
      + "      <counter type=\"METHOD\" missed=\"1\" covered=\"1\"/>\n"
      + "    </sourcefile>\n"
      + "  </package>\n"
      + "</report>\n";

   private static final String EMPTY_REPORT_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<report name=\"empty\">\n"
      + "</report>\n";

   @TempDir
   Path tempDir;

   private File writeXml(String content) throws IOException {
      Path xmlPath = tempDir.resolve("test-jacoco.xml");
      Files.writeString(xmlPath, content, StandardCharsets.UTF_8);
      return xmlPath.toFile();
   }

   // ── parseReport with synthetic XML ─────────────────────────

   @Test
   void parseReportSinglePackageSingleFile() throws Exception {
      File xmlFile = writeXml(MINIMAL_XML);
      List<CoverageReport.ClassCoverage> classes =
         CoverageReport.parseReport(xmlFile);

      assertNotNull(classes);
      assertEquals(1, classes.size());

      CoverageReport.ClassCoverage cc = classes.get(0);
      assertEquals("com.example.Foo", cc.className());
      assertEquals(9, cc.lineMissed());
      assertEquals(5, cc.lineCovered());
      assertEquals(2, cc.branchMissed());
      assertEquals(3, cc.branchCovered());
      assertEquals(1, cc.methodMissed());
      assertEquals(2, cc.methodCovered());

      double expectedLinePct = 100.0 * 5 / 14;
      assertEquals(expectedLinePct, cc.linePct(), 0.01);
   }

   @Test
   void parseReportMultiplePackagesAndFiles() throws Exception {
      File xmlFile = writeXml(MULTI_PACKAGE_XML);
      List<CoverageReport.ClassCoverage> classes =
         CoverageReport.parseReport(xmlFile);

      assertEquals(3, classes.size());

      // Find class A (0% line coverage)
      CoverageReport.ClassCoverage a = classes.stream()
         .filter(c -> c.className().equals("pkg.alpha.A"))
         .findFirst().orElse(null);
      assertNotNull(a);
      assertEquals(0.0, a.linePct(), 0.01);
      assertEquals(6, a.lineMissed());
      assertEquals(0, a.lineCovered());

      // Find class B (100% line coverage)
      CoverageReport.ClassCoverage b = classes.stream()
         .filter(c -> c.className().equals("pkg.beta.B"))
         .findFirst().orElse(null);
      assertNotNull(b);
      assertEquals(100.0, b.linePct(), 0.01);
      assertEquals(0, b.lineMissed());
      assertEquals(8, b.lineCovered());
      assertEquals(100.0, b.branchPct(), 0.01);

      // Find class C (partial coverage)
      CoverageReport.ClassCoverage c = classes.stream()
         .filter(cc -> cc.className().equals("pkg.beta.C"))
         .findFirst().orElse(null);
      assertNotNull(c);
      assertTrue(c.linePct() > 0 && c.linePct() < 100);
   }

   @Test
   void parseReportEmptyReport() throws Exception {
      File xmlFile = writeXml(EMPTY_REPORT_XML);
      List<CoverageReport.ClassCoverage> classes =
         CoverageReport.parseReport(xmlFile);
      assertNotNull(classes);
      assertTrue(classes.isEmpty());
   }

   @Test
   void parseReportUncoveredRangesExtracted() throws Exception {
      File xmlFile = writeXml(MINIMAL_XML);
      List<CoverageReport.ClassCoverage> classes =
         CoverageReport.parseReport(xmlFile);

      CoverageReport.ClassCoverage cc = classes.get(0);
      List<String> ranges = cc.uncoveredRanges();
      assertNotNull(ranges);
      // Lines 10 and 12 are uncovered (mi>0, ci==0)
      // Line 11 and 13 are covered (ci>0)
      assertFalse(ranges.isEmpty());
      assertTrue(ranges.contains("10"));
      assertTrue(ranges.contains("12"));
   }

   // ── printReport ────────────────────────────────────────────

   @Test
   void printReportWithMultipleClasses() throws Exception {
      File xmlFile = writeXml(MULTI_PACKAGE_XML);
      List<CoverageReport.ClassCoverage> classes =
         CoverageReport.parseReport(xmlFile);
      // Verify it doesn't throw
      CoverageReport.printReport(classes);
   }

   @Test
   void printReportSortedByLinePct() throws Exception {
      File xmlFile = writeXml(MULTI_PACKAGE_XML);
      List<CoverageReport.ClassCoverage> classes =
         CoverageReport.parseReport(xmlFile);
      classes.sort(java.util.Comparator.comparingDouble(
         c -> c.linePct()));
      assertEquals("pkg.alpha.A", classes.get(0).className(),
         "class with 0% should be first after sort");
      CoverageReport.printReport(classes);
   }

   // ── ClassCoverage record ───────────────────────────────────

   @Test
   void classCoverageRecordAccessors() {
      CoverageReport.ClassCoverage cc =
         new CoverageReport.ClassCoverage(
            "test.Cls", 75.0, 50.0, 80.0,
            25, 75, 10, 10, 4, 16,
            List.of("5-10", "20"));
      assertEquals("test.Cls", cc.className());
      assertEquals(75.0, cc.linePct(), 0.01);
      assertEquals(50.0, cc.branchPct(), 0.01);
      assertEquals(80.0, cc.methodPct(), 0.01);
      assertEquals(25, cc.lineMissed());
      assertEquals(75, cc.lineCovered());
      assertEquals(10, cc.branchMissed());
      assertEquals(10, cc.branchCovered());
      assertEquals(4, cc.methodMissed());
      assertEquals(16, cc.methodCovered());
      assertEquals(2, cc.uncoveredRanges().size());
   }

   @Test
   void classCoverageNoCountersDefaultsTo100Pct()
         throws Exception {
      String xml =
         "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
         + "<report name=\"noctr\">\n"
         + "  <package name=\"x\">\n"
         + "    <sourcefile name=\"Empty.java\">\n"
         + "    </sourcefile>\n"
         + "  </package>\n"
         + "</report>\n";
      File xmlFile = writeXml(xml);
      List<CoverageReport.ClassCoverage> classes =
         CoverageReport.parseReport(xmlFile);
      assertEquals(1, classes.size());
      assertEquals(100.0, classes.get(0).linePct(), 0.01,
         "No LINE counter → 100% default");
      assertEquals(100.0, classes.get(0).branchPct(), 0.01);
      assertEquals(100.0, classes.get(0).methodPct(), 0.01);
   }
}
