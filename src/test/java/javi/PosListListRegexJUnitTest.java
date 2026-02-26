package javi;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 port of the inline tests from
 * {@code PosListList.Cmd#main}.
 *
 * Tests the {@code filereg} regex pattern that extracts
 * filename:lineNumber pairs from compiler/grep output.
 * Pattern: {@code (([a-zA-Z]:)?([^\:\s\(\)\"\']+)):([0-9]+)}
 */
class PosListListRegexJUnitTest {

   /** Same pattern used by {@code PosListList.Cmd.filereg}. */
   private static final Pattern FILE_LINE_PATTERN = Pattern.compile(
      "(([a-zA-Z]:)?([^:\\s\\(\\)\"']+)):([0-9]+)");

   private final Matcher filereg = FILE_LINE_PATTERN.matcher("");

   @Test
   void matchesJavaFileWithLineNumber() {
      filereg.reset("UI.java:1118 java.xxx.event.");
      assertTrue(filereg.find(), "should match UI.java:1118");
   }

   @Test
   void matchesCFileWithTrailingSpace() {
      assertTrue(filereg.reset("smtp_hfilter.c:254 ").find(),
         "should match smtp_hfilter.c:254 with trailing space");
   }

   @Test
   void matchesCFileWithoutTrailingSpace() {
      assertTrue(filereg.reset("smtp_hfilter.c:254").find(),
         "should match smtp_hfilter.c:254 without trailing space");
   }

   @Test
   void extractsCorrectLineNumberFromMultiMatchString() {
      filereg.reset("smtp_hfilter.c:254 hfilter_find SUBJECT"
         + "smtp_hfilter.c:266 hfilter_find SUBJECTsmtp_hfilter.c:"
         + "131 normalize_name_stbuf_ind 0 ,buffer[buf_ind]13");
      assertTrue(filereg.find(), "should find first match");
      assertEquals("254", filereg.group(4),
         "first match line number should be 254");
   }

   @Test
   void extractsCorrectFilenameFromMultiMatchString() {
      filereg.reset("smtp_hfilter.c:254 hfilter_find SUBJECT"
         + "smtp_hfilter.c:266 hfilter_find SUBJECTsmtp_hfilter.c:"
         + "131 normalize_name_stbuf_ind 0 ,buffer[buf_ind]13");
      assertTrue(filereg.find(), "should find first match");
      assertEquals("smtp_hfilter.c", filereg.group(1),
         "first match filename should be smtp_hfilter.c");
   }

   @Test
   void matchesWindowsDrivePath() {
      filereg.reset("C:\\src\\main.cpp:42 some context");
      assertTrue(filereg.find(), "should match Windows drive path");
      assertEquals("C:\\src\\main.cpp", filereg.group(1));
      assertEquals("C:", filereg.group(2));
      assertEquals("42", filereg.group(4));
   }
}
