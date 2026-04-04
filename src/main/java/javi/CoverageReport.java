package javi;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parses JaCoCo merged XML coverage report and outputs a
 * text summary sorted by lowest line coverage first.
 *
 * <p>Usage: java -cp build/classes/java/main javi.CoverageReport
 *    build/reports/jacoco/merged/merged.xml
 */
public final class CoverageReport {

   private CoverageReport() { }

   public static void main(String[] args) throws Exception {
      if (args.length < 1) {
         System.err.println(
            "Usage: CoverageReport <jacoco-merged.xml>");
         System.exit(1);
      }
      File xmlFile = new File(args[0]);
      if (!xmlFile.exists()) {
         System.err.println("File not found: " + xmlFile);
         System.exit(1);
      }
      List<ClassCoverage> classes = parseReport(xmlFile);
      classes.sort(Comparator.comparingDouble(c -> c.linePct));
      printReport(classes);
   }

   static List<ClassCoverage> parseReport(File xmlFile)
         throws Exception {
      DocumentBuilderFactory factory =
         DocumentBuilderFactory.newInstance();
      factory.setFeature(
         "http://apache.org/xml/features/nonvalidating/"
         + "load-external-dtd", false);
      factory.setFeature(
         "http://xml.org/sax/features/external-general-entities",
         false);
      factory.setFeature(
         "http://xml.org/sax/features/external-parameter-entities",
         false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(xmlFile);

      List<ClassCoverage> result = new ArrayList<>();
      NodeList packages = doc.getElementsByTagName("package");

      for (int p = 0; p < packages.getLength(); p++) {
         Element pkg = (Element) packages.item(p);
         String pkgName = pkg.getAttribute("name")
            .replace('/', '.');
         NodeList sourcefiles =
            pkg.getElementsByTagName("sourcefile");

         for (int s = 0; s < sourcefiles.getLength(); s++) {
            Element sf = (Element) sourcefiles.item(s);
            String fileName = sf.getAttribute("name");
            String className = pkgName + "."
               + fileName.replace(".java", "");

            int lineMissed = 0;
            int lineCovered = 0;
            int branchMissed = 0;
            int branchCovered = 0;
            int methodMissed = 0;
            int methodCovered = 0;

            NodeList counters =
               sf.getElementsByTagName("counter");
            for (int c = 0; c < counters.getLength(); c++) {
               Element ctr = (Element) counters.item(c);
               // Only direct children of sourcefile, not
               // nested in line elements. Check parent.
               if (ctr.getParentNode() != sf)
                  continue;
               String type = ctr.getAttribute("type");
               int missed = Integer.parseInt(
                  ctr.getAttribute("missed"));
               int covered = Integer.parseInt(
                  ctr.getAttribute("covered"));
               switch (type) {
                  case "LINE" -> {
                     lineMissed = missed;
                     lineCovered = covered;
                  }
                  case "BRANCH" -> {
                     branchMissed = missed;
                     branchCovered = covered;
                  }
                  case "METHOD" -> {
                     methodMissed = missed;
                     methodCovered = covered;
                  }
                  default -> { }
               }
            }

            // Collect uncovered line ranges from <line> elements
            TreeMap<Integer, Boolean> lineMap = new TreeMap<>();
            NodeList lines = sf.getElementsByTagName("line");
            for (int l = 0; l < lines.getLength(); l++) {
               Element line = (Element) lines.item(l);
               int nr = Integer.parseInt(
                  line.getAttribute("nr"));
               int mi = Integer.parseInt(
                  line.getAttribute("mi"));
               int ci = Integer.parseInt(
                  line.getAttribute("ci"));
               // A line is uncovered if all instructions missed
               lineMap.put(nr, ci > 0 || mi == 0);
            }

            List<String> uncoveredRanges =
               buildUncoveredRanges(lineMap);

            int totalLines = lineMissed + lineCovered;
            int totalBranches = branchMissed + branchCovered;
            int totalMethods = methodMissed + methodCovered;

            double linePct = totalLines > 0
               ? 100.0 * lineCovered / totalLines : 100.0;
            double branchPct = totalBranches > 0
               ? 100.0 * branchCovered / totalBranches : 100.0;
            double methodPct = totalMethods > 0
               ? 100.0 * methodCovered / totalMethods : 100.0;

            result.add(new ClassCoverage(className,
               linePct, branchPct, methodPct,
               lineMissed, lineCovered,
               branchMissed, branchCovered,
               methodMissed, methodCovered,
               uncoveredRanges));
         }
      }
      return result;
   }

   static List<String> buildUncoveredRanges(
         TreeMap<Integer, Boolean> lineMap) {
      List<String> ranges = new ArrayList<>();
      int rangeStart = -1;
      int rangeEnd = -1;

      for (var entry : lineMap.entrySet()) {
         int nr = entry.getKey();
         boolean covered = entry.getValue();
         if (!covered) {
            if (rangeStart == -1) {
               rangeStart = nr;
               rangeEnd = nr;
            } else {
               rangeEnd = nr;
            }
         } else {
            if (rangeStart != -1) {
               ranges.add(rangeStart == rangeEnd
                  ? String.valueOf(rangeStart)
                  : rangeStart + "-" + rangeEnd);
               rangeStart = -1;
            }
         }
      }
      if (rangeStart != -1) {
         ranges.add(rangeStart == rangeEnd
            ? String.valueOf(rangeStart)
            : rangeStart + "-" + rangeEnd);
      }
      return ranges;
   }

   static void printReport(List<ClassCoverage> classes) {
      System.out.printf("%-45s %6s %6s %6s %10s %12s %s%n",
         "CLASS", "LINE%", "BRNCH%", "MTHD%",
         "LINES", "BRANCHES", "UNCOVERED LINES");
      System.out.println("-".repeat(130));

      int totalLineMissed = 0;
      int totalLineCovered = 0;
      int totalBranchMissed = 0;
      int totalBranchCovered = 0;

      for (ClassCoverage cc : classes) {
         totalLineMissed += cc.lineMissed;
         totalLineCovered += cc.lineCovered;
         totalBranchMissed += cc.branchMissed;
         totalBranchCovered += cc.branchCovered;

         String uncovered = cc.uncoveredRanges.isEmpty()
            ? ""
            : String.join(",", cc.uncoveredRanges);
         // Truncate if too long
         if (uncovered.length() > 60)
            uncovered = uncovered.substring(0, 57) + "...";

         System.out.printf(
            "%-45s %5.1f%% %5.1f%% %5.1f%% %4d/%-4d %4d/%-4d    %s%n",
            cc.className, cc.linePct, cc.branchPct,
            cc.methodPct,
            cc.lineCovered, cc.lineCovered + cc.lineMissed,
            cc.branchCovered,
            cc.branchCovered + cc.branchMissed,
            uncovered);
      }

      System.out.println("-".repeat(130));
      int totalLines = totalLineMissed + totalLineCovered;
      int totalBranches = totalBranchMissed + totalBranchCovered;
      double overallLine = totalLines > 0
         ? 100.0 * totalLineCovered / totalLines : 0;
      double overallBranch = totalBranches > 0
         ? 100.0 * totalBranchCovered / totalBranches : 0;
      System.out.printf(
         "%-45s %5.1f%%         %4d/%-4d %4d/%-4d%n",
         "TOTAL", overallLine,
         totalLineCovered, totalLines,
         totalBranchCovered, totalBranches);
   }

   record ClassCoverage(
      String className,
      double linePct,
      double branchPct,
      double methodPct,
      int lineMissed, int lineCovered,
      int branchMissed, int branchCovered,
      int methodMissed, int methodCovered,
      List<String> uncoveredRanges
   ) { }
}
