package javi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 port of the inline path-normalization tests from
 * {@link FileDescriptor#main}.
 *
 * <p>Tests verify that {@link FileDescriptor#make} assigns the expected
 * {@code shortName} after canonicalizing path separators, dot-segments,
 * and double-dot segments.</p>
 *
 * <p>Windows-specific path tests (drive-letter, backslash) were removed
 * because javi only runs on Unix/Linux and the tests can never execute.</p>
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
      // The result of ../../ normalization depends on cwd depth.
      // In deep cwd (3+ slashes), ../.. resolves to a relative path.
      // In shallow cwd (like /app), canonical path resolution differs.
      FileDescriptor fd = FileDescriptor.make("../../xxx/../yy/../yy");
      String shortName = fd.toString();
      // Verify the path was normalized (no redundant segments remain)
      assertFalse(shortName.contains("/xxx/"),
         "redundant /xxx/ segment should be removed: " + shortName);
      assertTrue(shortName.endsWith("yy"),
         "should end with yy: " + shortName);
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

}
