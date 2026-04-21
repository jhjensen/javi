package javi;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended tests for {@link Ctag} — tag name extraction,
 * tag file lookup with real temp files, and edge cases.
 */
class CtagExtendedJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   // ── getTagName tests ─────────────────────────────────────────

   @Nested
   @DisplayName("getTagName()")
   class GetTagNameTests {

      @Test
      @DisplayName("extracts name from tag comment")
      void extractsTagName() {
         Position p = new Position(0, 1, "file.java",
            "tag:myFunction\tclass:MyClass");
         assertEquals("myFunction", Ctag.getTagName(p));
      }

      @Test
      @DisplayName("extracts name without tab suffix")
      void extractsTagNameNoTab() {
         Position p = new Position(0, 1, "file.java",
            "tag:simpleTag");
         assertEquals("simpleTag", Ctag.getTagName(p));
      }

      @Test
      @DisplayName("returns empty for null position")
      void nullPosition() {
         assertEquals("", Ctag.getTagName(null));
      }

      @Test
      @DisplayName("returns empty for null comment")
      void nullComment() {
         Position p = new Position(0, 1, "file.java", null);
         assertEquals("", Ctag.getTagName(p));
      }

      @Test
      @DisplayName("returns empty for non-tag comment")
      void nonTagComment() {
         Position p = new Position(0, 1, "file.java",
            "some other comment");
         assertEquals("", Ctag.getTagName(p));
      }

      @Test
      @DisplayName("returns empty for empty comment")
      void emptyComment() {
         Position p = new Position(0, 1, "file.java", "");
         assertEquals("", Ctag.getTagName(p));
      }

      @Test
      @DisplayName("extracts tag with underscores and digits")
      void tagWithSpecialChars() {
         Position p = new Position(0, 1, "file.c",
            "tag:my_func_123\ttype");
         assertEquals("my_func_123", Ctag.getTagName(p));
      }

      @Test
      @DisplayName("tag: prefix only returns empty name")
      void tagPrefixOnly() {
         Position p = new Position(0, 1, "file.java", "tag:");
         assertEquals("", Ctag.getTagName(p));
      }
   }

   // ── Ctag construction and lookup with temp files ─────────────

   @Nested
   @DisplayName("Ctag with temp tag files")
   class CtagLookupTests {

      @TempDir
      File tempDir;

      /**
       * Creates a ctags-format file with the given entries.
       * Each entry: name TAB filename TAB linenumber;"
       */
      private File createTagFile(String... entries) throws IOException {
         File tagFile = new File(tempDir, "tags");
         try (PrintWriter pw = new PrintWriter(tagFile,
               StandardCharsets.UTF_8.name())) {
            pw.println("!_TAG_FILE_FORMAT\t2\t/extended format/");
            pw.println("!_TAG_FILE_SORTED\t1\t/0=unsorted/");
            for (String entry : entries) {
               pw.println(entry);
            }
         }
         return tagFile;
      }

      @Test
      @DisplayName("constructs from valid tag file")
      void constructsFromFile() throws IOException {
         File tf = createTagFile(
            "main\tsrc/main.c\t42;\"\tf");
         Ctag ct = new Ctag(tf.getAbsolutePath());
         assertNotNull(ct);
      }

      @Test
      @DisplayName("lookup finds existing tag")
      void findsExistingTag() throws IOException {
         File tf = createTagFile(
            "alpha\tsrc/alpha.c\t10;\"\tf",
            "beta\tsrc/beta.c\t20;\"\tf",
            "gamma\tsrc/gamma.c\t30;\"\tf");
         Ctag ct = new Ctag(tf.getAbsolutePath());

         Position[] positions = ct.taglookup("beta");
         assertNotNull(positions, "should find 'beta'");
         assertTrue(positions.length > 0);
         assertEquals(20, positions[0].y);
      }

      @Test
      @DisplayName("lookup returns null for missing tag")
      void missingTagReturnsNull() throws IOException {
         File tf = createTagFile(
            "aaa\tsrc/a.c\t1;\"\tf",
            "zzz\tsrc/z.c\t99;\"\tf");
         Ctag ct = new Ctag(tf.getAbsolutePath());

         Position[] positions = ct.taglookup("missing");
         assertNull(positions, "should not find 'missing'");
      }

      @Test
      @DisplayName("lookup finds first tag in file")
      void findsFirstTag() throws IOException {
         File tf = createTagFile(
            "aaa\tsrc/a.c\t1;\"\tf",
            "bbb\tsrc/b.c\t2;\"\tf",
            "ccc\tsrc/c.c\t3;\"\tf");
         Ctag ct = new Ctag(tf.getAbsolutePath());

         Position[] positions = ct.taglookup("aaa");
         assertNotNull(positions, "should find 'aaa'");
         assertEquals(1, positions[0].y);
      }

      @Test
      @DisplayName("lookup finds last tag in file")
      void findsLastTag() throws IOException {
         File tf = createTagFile(
            "aaa\tsrc/a.c\t1;\"\tf",
            "bbb\tsrc/b.c\t2;\"\tf",
            "zzz\tsrc/z.c\t99;\"\tf");
         Ctag ct = new Ctag(tf.getAbsolutePath());

         Position[] positions = ct.taglookup("zzz");
         assertNotNull(positions, "should find 'zzz'");
         assertEquals(99, positions[0].y);
      }

      @Test
      @DisplayName("lookup with many entries")
      void manyEntries() throws IOException {
         String[] entries = new String[100];
         for (int i = 0; i < 100; i++) {
            String name = String.format("tag_%03d", i);
            entries[i] = name + "\tsrc/file.c\t" + (i + 1) + ";\"\tf";
         }
         File tf = createTagFile(entries);
         Ctag ct = new Ctag(tf.getAbsolutePath());

         Position[] p50 = ct.taglookup("tag_050");
         assertNotNull(p50, "should find tag_050");
         assertEquals(51, p50[0].y);

         Position[] p99 = ct.taglookup("tag_099");
         assertNotNull(p99, "should find tag_099");
         assertEquals(100, p99[0].y);

         Position[] p0 = ct.taglookup("tag_000");
         assertNotNull(p0, "should find tag_000");
         assertEquals(1, p0[0].y);
      }

      @Test
      @DisplayName("single entry tag file")
      void singleEntry() throws IOException {
         File tf = createTagFile(
            "only\tsrc/only.c\t42;\"\tf");
         Ctag ct = new Ctag(tf.getAbsolutePath());

         Position[] positions = ct.taglookup("only");
         assertNotNull(positions, "should find 'only'");
         assertEquals(42, positions[0].y);
      }

      @Test
      @DisplayName("tag position includes filename")
      void positionIncludesFilename() throws IOException {
         File tf = createTagFile(
            "func\tsrc/myfile.c\t15;\"\tf");
         Ctag ct = new Ctag(tf.getAbsolutePath());

         Position[] positions = ct.taglookup("func");
         assertNotNull(positions);
         assertTrue(positions[0].filename.toString().contains("src/myfile.c"),
            "filename should contain 'src/myfile.c', got: "
            + positions[0].filename);
      }
   }

   // ── XrefReader static method tests ───────────────────────────

   @Nested
   @DisplayName("XrefReader statics")
   class XrefReaderStatics {

      @Test
      @DisplayName("buildCtagKeys returns null for empty array")
      void buildKeysEmpty() {
         assertNull(XrefReader.buildCtagKeys(new Position[0]));
      }

      @Test
      @DisplayName("buildCtagKeys returns null for null")
      void buildKeysNull() {
         assertNull(XrefReader.buildCtagKeys(null));
      }

      @Test
      @DisplayName("buildCtagKeys creates set from positions")
      void buildKeysCreatesSet() {
         Position[] positions = {
            new Position(0, 10, "file.c", "comment"),
            new Position(5, 20, "other.c", "x"),
         };
         java.util.Set<String> keys = XrefReader.buildCtagKeys(positions);
         assertNotNull(keys);
         assertEquals(2, keys.size());
      }

      @Test
      @DisplayName("buildCtagKeys deduplicates same file:line")
      void buildKeysDeduplicates() {
         Position[] positions = {
            new Position(0, 10, "file.c", "first"),
            new Position(5, 10, "file.c", "second"),
         };
         java.util.Set<String> keys = XrefReader.buildCtagKeys(positions);
         assertNotNull(keys);
         // Same file:line → same key → deduplicated
         assertEquals(1, keys.size());
      }

      @Test
      @DisplayName("makeDedupKey includes file and line")
      void makeDedupKeyFormat() {
         Position p = new Position(0, 42, "src/main.c", "comment");
         String key = XrefReader.makeDedupKey(p);
         assertNotNull(key);
         assertTrue(key.contains("42"),
            "key should contain line number, got: " + key);
         assertTrue(key.contains("src/main.c"),
            "key should contain filename, got: " + key);
      }

      @Test
      @DisplayName("makeDedupKey uses canonName")
      void makeDedupKeyUsesCanonName() {
         Position p1 = new Position(0, 10, "file.c", "a");
         Position p2 = new Position(5, 10, "file.c", "b");
         assertEquals(XrefReader.makeDedupKey(p1),
            XrefReader.makeDedupKey(p2),
            "same file:line should produce same key");
      }

      @Test
      @DisplayName("makeDedupKey different lines produce different keys")
      void makeDedupKeyDifferentLines() {
         Position p1 = new Position(0, 10, "file.c", "a");
         Position p2 = new Position(0, 20, "file.c", "b");
         assertTrue(
            !XrefReader.makeDedupKey(p1).equals(
               XrefReader.makeDedupKey(p2)),
            "different lines should produce different keys");
      }
   }
}
