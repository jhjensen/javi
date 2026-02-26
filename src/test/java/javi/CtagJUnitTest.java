package javi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 port of the inline tests from {@code Ctag#main}.
 *
 * Tests the Ctag constructor (binary-search tag index) and
 * {@code taglookup()} against a synthetic ctags file.
 */
class CtagJUnitTest {

   /**
    * Standard ctags file format: sorted entries with
    * {@code tagname TAB filename TAB linenum;" extras}
    * Lines starting with '!' are header comments and must be skipped.
    */
   private static final String TAGS_CONTENT =
      "!_TAG_FILE_FORMAT\t2\n"
      + "!_TAG_FILE_SORTED\t1\n"
      + "alpha\tsrc/alpha.c\t10;\"\tf\n"
      + "beta\tsrc/beta.c\t20;\"\tf\n"
      + "beta\tsrc/beta.c\t30;\"\tf\n"
      + "gamma\tsrc/gamma.c\t40;\"\tf\n"
      + "main\tsrc/main.c\t1;\"\tf\n"
      + "main\tsrc/other.c\t50;\"\tf\n"
      + "zeta\tsrc/zeta.c\t99;\"\tf\n";

   @Test
   void lookupFindsKnownTag(@TempDir Path tmpDir) throws IOException {
      Path tagsFile = tmpDir.resolve("tags");
      Files.writeString(tagsFile, TAGS_CONTENT, StandardCharsets.UTF_8);

      Ctag ct = new Ctag(tagsFile.toString());
      Position[] results = ct.taglookup("main");

      assertNotNull(results, "taglookup should return results for 'main'");
      assertEquals(2, results.length,
         "should find 2 tag entries for 'main'");
   }

   @Test
   void lookupReturnsCorrectPosition(@TempDir Path tmpDir) throws IOException {
      Path tagsFile = tmpDir.resolve("tags");
      Files.writeString(tagsFile, TAGS_CONTENT, StandardCharsets.UTF_8);

      Ctag ct = new Ctag(tagsFile.toString());
      Position[] results = ct.taglookup("alpha");

      assertNotNull(results);
      assertEquals(1, results.length);
      assertTrue(results[0].filename.shortName.contains("src/alpha.c"),
         "filename should contain src/alpha.c, got: "
         + results[0].filename.shortName);
      assertEquals(10, results[0].y);
   }

   @Test
   void lookupSingleEntry(@TempDir Path tmpDir) throws IOException {
      Path tagsFile = tmpDir.resolve("tags");
      Files.writeString(tagsFile, TAGS_CONTENT, StandardCharsets.UTF_8);

      Ctag ct = new Ctag(tagsFile.toString());
      Position[] results = ct.taglookup("gamma");

      assertNotNull(results);
      assertEquals(1, results.length);
      assertEquals(40, results[0].y);
   }

   @Test
   void lookupUnknownTagReturnsNull(@TempDir Path tmpDir) throws IOException {
      Path tagsFile = tmpDir.resolve("tags");
      Files.writeString(tagsFile, TAGS_CONTENT, StandardCharsets.UTF_8);

      Ctag ct = new Ctag(tagsFile.toString());
      Position[] results = ct.taglookup("nonexistent");

      assertNull(results, "unknown tag should return null");
   }

   @Test
   void getTagNameExtractsFromComment() {
      // Position.comment format for tags: "tag:name\textras"
      Position p = new Position(0, 10, "file.c", "tag:myFunc\tf");
      String name = Ctag.getTagName(p);
      assertEquals("myFunc", name);
   }

   @Test
   void getTagNameReturnsEmptyForNonTagPosition() {
      Position p = new Position(0, 10, "file.c", "not a tag");
      String name = Ctag.getTagName(p);
      assertEquals("", name);
   }
}
