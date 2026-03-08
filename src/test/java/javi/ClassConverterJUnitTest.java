package javi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ClassConverter} and additional {@link Ctag} edge cases.
 */
class ClassConverterJUnitTest {

   // ── ClassConverter tests ────────────────────────────────────

   /** Concrete subclass for testing the abstract ClassConverter. */
   private static class StringConverter extends ClassConverter<String> {
      private static final long serialVersionUID = 1;
      @Override
      public String fromString(String s) {
         return s;
      }
   }

   /** Converter that transforms input. */
   private static class UpperCaseConverter extends ClassConverter<String> {
      private static final long serialVersionUID = 1;
      @Override
      public String fromString(String s) {
         return s.toUpperCase();
      }
   }

   @Test
   void fromStringIdentity() {
      ClassConverter<String> conv = new StringConverter();
      assertEquals("hello", conv.fromString("hello"));
   }

   @Test
   void fromStringEmpty() {
      ClassConverter<String> conv = new StringConverter();
      assertEquals("", conv.fromString(""));
   }

   @Test
   void fromStringTransform() {
      ClassConverter<String> conv = new UpperCaseConverter();
      assertEquals("HELLO", conv.fromString("hello"));
   }

   @Test
   void saveExternalWritesUTF() throws IOException {
      ClassConverter<String> conv = new StringConverter();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      conv.saveExternal("test data", dos);
      dos.flush();
      // DataOutputStream.writeUTF writes a 2-byte length prefix + UTF data
      byte[] bytes = baos.toByteArray();
      assertTrue(bytes.length > 2, "should have length prefix + data");
      // verify we can read back the UTF
      java.io.DataInputStream dis = new java.io.DataInputStream(
         new java.io.ByteArrayInputStream(bytes));
      assertEquals("test data", dis.readUTF());
   }

   @Test
   void saveExternalUsesToString() throws IOException {
      ClassConverter<String> conv = new StringConverter();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      // Pass an Integer — saveExternal calls ob.toString()
      conv.saveExternal(Integer.valueOf(42), dos);
      dos.flush();
      java.io.DataInputStream dis = new java.io.DataInputStream(
         new java.io.ByteArrayInputStream(baos.toByteArray()));
      assertEquals("42", dis.readUTF());
   }

   @Test
   void saveExternalEmptyString() throws IOException {
      ClassConverter<String> conv = new StringConverter();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      conv.saveExternal("", dos);
      dos.flush();
      java.io.DataInputStream dis = new java.io.DataInputStream(
         new java.io.ByteArrayInputStream(baos.toByteArray()));
      assertEquals("", dis.readUTF());
   }

   @Test
   void saveExternalSpecialChars() throws IOException {
      ClassConverter<String> conv = new StringConverter();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      conv.saveExternal("line1\nline2\ttab", dos);
      dos.flush();
      java.io.DataInputStream dis = new java.io.DataInputStream(
         new java.io.ByteArrayInputStream(baos.toByteArray()));
      assertEquals("line1\nline2\ttab", dis.readUTF());
   }

   // ── Additional Ctag tests ──────────────────────────────────

   private static final String TAGS =
      "!_TAG_FILE_FORMAT\t2\n"
      + "!_TAG_FILE_SORTED\t1\n"
      + "alpha\tsrc/alpha.c\t10;\"\tf\n"
      + "beta\tsrc/beta.c\t20;\"\tf\n"
      + "delta\tsrc/delta.c\t40;\"\tf\n"
      + "gamma\tsrc/gamma.c\t30;\"\tf\n";

   @Test
   void ctagLookupFirstEntry(@TempDir Path tmpDir) throws IOException {
      Path tags = tmpDir.resolve("tags");
      Files.writeString(tags, TAGS, StandardCharsets.UTF_8);
      Ctag ct = new Ctag(tags.toString());
      Position[] results = ct.taglookup("alpha");
      assertNotNull(results);
      assertEquals(1, results.length);
      assertEquals(10, results[0].y);
   }

   @Test
   void ctagLookupLastEntry(@TempDir Path tmpDir) throws IOException {
      Path tags = tmpDir.resolve("tags");
      Files.writeString(tags, TAGS, StandardCharsets.UTF_8);
      Ctag ct = new Ctag(tags.toString());
      Position[] results = ct.taglookup("gamma");
      assertNotNull(results);
      assertEquals(30, results[0].y);
   }

   @Test
   void ctagLookupBeforeFirstReturnsNull(@TempDir Path tmpDir)
         throws IOException {
      Path tags = tmpDir.resolve("tags");
      Files.writeString(tags, TAGS, StandardCharsets.UTF_8);
      Ctag ct = new Ctag(tags.toString());
      assertNull(ct.taglookup("aaa"));
   }

   @Test
   void ctagLookupAfterLastReturnsNull(@TempDir Path tmpDir)
         throws IOException {
      Path tags = tmpDir.resolve("tags");
      Files.writeString(tags, TAGS, StandardCharsets.UTF_8);
      Ctag ct = new Ctag(tags.toString());
      assertNull(ct.taglookup("zzz"));
   }

   @Test
   void getTagNameWithTabExtras() {
      Position p = new Position(0, 1, "f.c", "tag:myFunc\tclass:Foo");
      assertEquals("myFunc", Ctag.getTagName(p));
   }

   @Test
   void getTagNameNullPosition() {
      assertEquals("", Ctag.getTagName(null));
   }

   @Test
   void getTagNameNullComment() {
      Position p = new Position(0, 1, "f.c", null);
      assertEquals("", Ctag.getTagName(p));
   }

   @Test
   void getTagNameNoTagPrefix() {
      Position p = new Position(0, 1, "f.c", "noprefix");
      assertEquals("", Ctag.getTagName(p));
   }
}
