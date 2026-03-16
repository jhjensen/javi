package javi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Matcher;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link PosListList} — regex matching, helper
 * methods, and the {@code PCmd} enum.
 */
class PosListListJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
      // Force PosListList.Cmd class loading under biglock2, since its
      // static initializer creates TextEdit instances that assert lock.
      EventQueue.biglock2.lock();
      try {
         Class.forName("javi.PosListList$Cmd");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // Access private static filereg Matcher via reflection
   private static Matcher getFileReg() throws Exception {
      Field f = PosListList.Cmd.class.getDeclaredField("filereg");
      f.setAccessible(true);
      return (Matcher) f.get(null);
   }

   // Access private static getLastSym via reflection
   private static String getLastSym(String str, int startid) throws Exception {
      Method m = PosListList.Cmd.class.getDeclaredMethod(
         "getLastSym", String.class, int.class);
      m.setAccessible(true);
      return (String) m.invoke(null, str, startid);
   }

   // ================================================================
   // filereg pattern matching (from PosListList.Cmd.main())
   // ================================================================

   @Nested
   @DisplayName("filereg pattern")
   class FileRegTests {

      @Test
      @DisplayName("matches Java file:line reference")
      void matchesJavaFileLine() throws Exception {
         Matcher m = getFileReg();
         m.reset("UI.java:1118 java.xxx.event.");
         assertTrue(m.find());
         assertEquals("UI.java", m.group(1));
         assertEquals("1118", m.group(4));
      }

      @Test
      @DisplayName("matches C file:line reference")
      void matchesCFileLine() throws Exception {
         Matcher m = getFileReg();
         m.reset("smtp_hfilter.c:254 ");
         assertTrue(m.find());
         assertEquals("smtp_hfilter.c", m.group(1));
         assertEquals("254", m.group(4));
      }

      @Test
      @DisplayName("matches file:line without trailing space")
      void matchesCFileLineNoTrailingSpace() throws Exception {
         Matcher m = getFileReg();
         m.reset("smtp_hfilter.c:254");
         assertTrue(m.find());
         assertEquals("smtp_hfilter.c", m.group(1));
         assertEquals("254", m.group(4));
      }

      @Test
      @DisplayName("first match in multi-match string")
      void firstMatchInMulti() throws Exception {
         Matcher m = getFileReg();
         m.reset("smtp_hfilter.c:254 hfilter_find SUBJECT"
            + "smtp_hfilter.c:266 hfilter_find SUBJECTsmtp_hfilter.c:"
            + "131 normalize_name_stbuf_ind 0 ,buffer[buf_ind]13");
         assertTrue(m.find());
         assertEquals("254", m.group(4));
         assertEquals("smtp_hfilter.c", m.group(1));
      }

      @Test
      @DisplayName("matches path with directories")
      void matchesPathWithDirs() throws Exception {
         Matcher m = getFileReg();
         m.reset("src/main/java/Foo.java:42");
         assertTrue(m.find());
         assertEquals("src/main/java/Foo.java", m.group(1));
         assertEquals("42", m.group(4));
      }

      @Test
      @DisplayName("matches path with backslashes")
      void matchesBackslashPath() throws Exception {
         Matcher m = getFileReg();
         m.reset("src\\main\\Foo.java:99");
         assertTrue(m.find());
         assertEquals("src\\main\\Foo.java", m.group(1));
         assertEquals("99", m.group(4));
      }

      @Test
      @DisplayName("matches Windows drive letter path")
      void matchesWindowsDrivePath() throws Exception {
         Matcher m = getFileReg();
         m.reset("C:\\src\\File.cpp:100");
         assertTrue(m.find());
         // group(2) captures optional drive letter
         assertEquals("C:", m.group(2));
         assertEquals("100", m.group(4));
      }

      @Test
      @DisplayName("matches tilde path")
      void matchesTildePath() throws Exception {
         Matcher m = getFileReg();
         m.reset("~/projects/test.py:7");
         assertTrue(m.find());
         assertEquals("~/projects/test.py", m.group(1));
         assertEquals("7", m.group(4));
      }

      @Test
      @DisplayName("no match on bare text without colon-number")
      void noMatchBareText() throws Exception {
         Matcher m = getFileReg();
         m.reset("just some text without file reference");
         assertFalse(m.find());
      }

      @Test
      @DisplayName("no match on empty string")
      void noMatchEmpty() throws Exception {
         Matcher m = getFileReg();
         m.reset("");
         assertFalse(m.find());
      }

      @Test
      @DisplayName("rejects filename with spaces")
      void rejectsSpacesInName() throws Exception {
         Matcher m = getFileReg();
         m.reset("my file.java:10");
         // The pattern excludes spaces, so "my" won't match "my file.java"
         assertTrue(m.find()); // matches "file.java:10"
         assertEquals("file.java", m.group(1));
      }

      @Test
      @DisplayName("rejects filename with parens")
      void rejectsParensInName() throws Exception {
         Matcher m = getFileReg();
         m.reset("(File).java:10");
         assertTrue(m.find());
         // Should match only the non-paren portion
         assertFalse(m.group(1).contains("("));
      }
   }

   // ================================================================
   // getLastSym helper
   // ================================================================

   @Nested
   @DisplayName("getLastSym")
   class GetLastSymTests {

      @Test
      @DisplayName("extracts symbol from start of string")
      void extractsFromStart() throws Exception {
         assertEquals("myMethod", getLastSym("myMethod(args)", 0));
      }

      @Test
      @DisplayName("extracts symbol from mid-string offset")
      void extractsFromOffset() throws Exception {
         assertEquals("method", getLastSym("  method()", 2));
      }

      @Test
      @DisplayName("includes dots in qualified names")
      void includesDotsInQualifiedNames() throws Exception {
         assertEquals("com.example.Class", getLastSym("com.example.Class:42", 0));
      }

      @Test
      @DisplayName("stops at space")
      void stopsAtSpace() throws Exception {
         assertEquals("word", getLastSym("word rest", 0));
      }

      @Test
      @DisplayName("stops at paren")
      void stopsAtParen() throws Exception {
         assertEquals("func", getLastSym("func(arg)", 0));
      }

      @Test
      @DisplayName("empty result from non-identifier start")
      void emptyFromNonIdentifier() throws Exception {
         assertEquals("", getLastSym("(rest)", 0));
      }

      @Test
      @DisplayName("handles end of string")
      void handlesEndOfString() throws Exception {
         assertEquals("abc", getLastSym("abc", 0));
      }

      @Test
      @DisplayName("handles single character")
      void handlesSingleChar() throws Exception {
         assertEquals("x", getLastSym("x", 0));
      }
   }

   // ================================================================
   // PCmd enum
   // ================================================================

   @Nested
   @DisplayName("PCmd enum")
   class PCmdTests {

      @Test
      @DisplayName("enum has expected number of values")
      void enumCount() {
         assertEquals(16, PosListList.Cmd.PCmd.values().length);
      }

      @Test
      @DisplayName("TA is at ordinal 1")
      void taOrdinal() {
         assertEquals(1, PosListList.Cmd.PCmd.TA.ordinal());
      }

      @Test
      @DisplayName("NEXT_POS is at ordinal 9")
      void nextPosOrdinal() {
         assertEquals(9, PosListList.Cmd.PCmd.NEXT_POS.ordinal());
      }

      @Test
      @DisplayName("GOTO_DIR_LIST_DEFAULT is last at ordinal 15")
      void gotoDirListDefaultOrdinal() {
         assertEquals(15, PosListList.Cmd.PCmd.GOTO_DIR_LIST_DEFAULT.ordinal());
      }

      @Test
      @DisplayName("valueOf round-trips")
      void valueOfRoundTrips() {
         for (PosListList.Cmd.PCmd cmd : PosListList.Cmd.PCmd.values()) {
            assertEquals(cmd, PosListList.Cmd.PCmd.valueOf(cmd.name()));
         }
      }

      @Test
      @DisplayName("invalid name throws")
      void invalidNameThrows() {
         assertThrows(IllegalArgumentException.class,
            () -> PosListList.Cmd.PCmd.valueOf("NONEXISTENT"));
      }
   }

   // ================================================================
   // myassert
   // ================================================================

   @Nested
   @DisplayName("myassert")
   class MyAssertTests {

      @Test
      @DisplayName("true does not throw")
      void trueDoesNotThrow() {
         assertDoesNotThrow(
            () -> PosListList.Cmd.myassert(true, "ok"));
      }

      @Test
      @DisplayName("false throws RuntimeException")
      void falseThrowsRuntime() {
         RuntimeException ex = assertThrows(RuntimeException.class,
            () -> PosListList.Cmd.myassert(false, "bad"));
         assertTrue(ex.getMessage().contains("ASSERTION FAILURE"));
         assertTrue(ex.getMessage().contains("bad"));
      }
   }

   // ================================================================
   // main() smoke test — existing regression
   // ================================================================

   @Test
   @DisplayName("main() runs without exception")
   void mainRunsCleanly() {
      assertDoesNotThrow(
         () -> PosListList.Cmd.main(new String[0]));
   }
}
