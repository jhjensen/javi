package javi;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * JUnit 5 port of the inline path-normalization tests from
 * {@link FileDescriptor#main}.
 *
 * <p>Tests verify that {@link FileDescriptor#make} assigns the expected
 * {@code shortName} after canonicalizing path separators, dot-segments,
 * and double-dot segments.</p>
 *
 * <p>Tests annotated {@link Disabled} are Windows-specific (drive-letter or
 * backslash semantics) and are excluded from Unix CI runs.</p>
 */
class FileDescriptorJUnitTest {

   private static void assertShortName(String input, String expected) {
      FileDescriptor fd = FileDescriptor.make(input);
      assertEquals(expected, fd.shortName, "shortName for input: " + input);
   }

   @Test
   void relativePathUnchanged() {
      assertShortName("../ja/xx", "../ja/xx");
   }

   @Test
   void filePatternUnchanged() {
      assertShortName("fi.*java", "fi.*java");
   }

   @Test
   void relativePathWithSubDir() {
      assertShortName("../javitests/ms", "../javitests/ms");
   }

   @Test
   void simpleNameUnchanged() {
      assertShortName("asdf", "asdf");
   }

   @Test
   void canonicalizeDotDotSegments() {
      // Requires cwd deep enough for ../../ to resolve meaningfully;
      // in Docker (/app) the path can't be canonicalized.
      assumeTrue(System.getProperty("user.dir").chars()
         .filter(c -> c == '/').count() >= 3,
         "cwd too shallow for dot-dot resolution");
      assertShortName("../../xxx/../yy/../yy", "../../yy");
   }

   @Test
   void manyDotDotSegmentsPreserved() {
      assertShortName("../../xxx", "../../xxx");
   }

   @Test
   void dotDotFlattened() {
      assertShortName("xx/../yy", "yy");
   }

   @Test
   void dotDotWithSubPath() {
      assertShortName("asdf/xx/../yy", "asdf/yy");
   }

   @Test
   void dotSegmentRemoved() {
      assertShortName("asdf/xx/./yy", "asdf/xx/yy");
   }

   @Test
   void trailingSlashOnRootDotRemoved() {
      assertShortName("./", ".");
   }

   @Test
   void leadingDotSlashRemoved() {
      assertShortName("./xxx", "xxx");
   }

   @Test
   void trailingSlashRemoved() {
      assertShortName("asdf/", "asdf");
   }

   @Test
   void dotSlashPrefixStripped() {
      assertShortName("./asdf", "asdf");
   }

   @Test
   @Disabled("Windows path: backslash may not be treated as separator on Unix")
   void backslashNormalized() {
      assertShortName("asdf\\xx\\.\\yy", "asdf/xx/yy");
   }

   @Test
   @Disabled("Windows path: drive-letter lowercasing")
   void driveLetterNormalized() {
      assertShortName("c:/asdf", "C:/asdf");
   }

   @Test
   @Disabled("Windows path: root backslash requires drive-letter context")
   void rootBackslashToCurrentDrive() {
      assertShortName("\\asdf", "C:/asdf");
   }

   @Test
   @Disabled("Windows path: bare drive letter maps to current directory")
   void bareColonIsDot() {
      assertShortName("c:", ".");
   }

}
