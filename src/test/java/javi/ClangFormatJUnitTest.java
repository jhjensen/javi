package javi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import javax.xml.parsers.DocumentBuilder;

/**
 * Tests for {@link ClangFormat} — static initialization and utilities.
 *
 * <p>Full format() testing requires the external clang-format tool,
 * so we focus on the XML DocumentBuilder initialization and class
 * structure that can be tested without external dependencies.
 */
class ClangFormatJUnitTest {

   @Test
   void documentBuilderInitialized() throws Exception {
      // ClangFormat has a static DocumentBuilder — verify it was
      // created successfully during class loading
      Field builderField = ClangFormat.class.getDeclaredField("builder");
      builderField.setAccessible(true);
      DocumentBuilder builder = (DocumentBuilder) builderField.get(null);
      assertNotNull(builder,
         "ClangFormat.builder should be initialized in static block");
   }

   @Test
   void documentBuilderCanParseXml() throws Exception {
      // Verify the builder can parse a minimal replacements XML
      Field builderField = ClangFormat.class.getDeclaredField("builder");
      builderField.setAccessible(true);
      DocumentBuilder builder = (DocumentBuilder) builderField.get(null);

      String xml = "<?xml version='1.0'?>"
         + "<replacements xml:space='preserve' "
         + "incomplete_format='false'>"
         + "<replacement offset='10' length='2'>  </replacement>"
         + "</replacements>";

      var doc = builder.parse(
         new java.io.ByteArrayInputStream(
            xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      doc.getDocumentElement().normalize();
      var nodes = doc.getElementsByTagName("replacement");
      assertEquals(1, nodes.getLength());

      var elem = (org.w3c.dom.Element) nodes.item(0);
      assertEquals("10", elem.getAttribute("offset"));
      assertEquals("2", elem.getAttribute("length"));
      assertEquals("  ", elem.getTextContent());
   }

   @Test
   void documentBuilderParsesEmptyReplacements() throws Exception {
      Field builderField = ClangFormat.class.getDeclaredField("builder");
      builderField.setAccessible(true);
      DocumentBuilder builder = (DocumentBuilder) builderField.get(null);

      String xml = "<?xml version='1.0'?>"
         + "<replacements xml:space='preserve' "
         + "incomplete_format='false'/>";

      var doc = builder.parse(
         new java.io.ByteArrayInputStream(
            xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      var nodes = doc.getElementsByTagName("replacement");
      assertEquals(0, nodes.getLength());
   }

   @Test
   void documentBuilderParsesMultipleReplacements() throws Exception {
      Field builderField = ClangFormat.class.getDeclaredField("builder");
      builderField.setAccessible(true);
      DocumentBuilder builder = (DocumentBuilder) builderField.get(null);

      String xml = "<?xml version='1.0'?>"
         + "<replacements xml:space='preserve'>"
         + "<replacement offset='0' length='1'>X</replacement>"
         + "<replacement offset='5' length='3'>YYY</replacement>"
         + "<replacement offset='20' length='0'>Z</replacement>"
         + "</replacements>";

      var doc = builder.parse(
         new java.io.ByteArrayInputStream(
            xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      var nodes = doc.getElementsByTagName("replacement");
      assertEquals(3, nodes.getLength());
   }
}
