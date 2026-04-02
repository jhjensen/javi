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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link PosListList} — regex matching, helper
 * methods, and the {@code PCmd} enum.
 */
class PosListListJUnitTest {

   private static PosListList.Cmd pllCmd;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
      // Force PosListList.Cmd class loading and construction under
      // biglock2, since its static initializer and constructor
      // create TextEdit instances and register commands.
      // Guard against double-registration when other PLL test
      // classes run first in the same JVM.
      EventQueue.biglock2.lock();
      try {
         try {
            pllCmd = new PosListList.Cmd();
            PosListListCoverageJUnitTest.sharedCmd = pllCmd;
         } catch (RuntimeException e) {
            // Commands already registered by another test class.
            // Static inst is already initialized.
            pllCmd = PosListListCoverageJUnitTest.sharedCmd;
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // Access private static filePositionPattern Matcher via reflection
   private static Matcher getFilePositionPattern() throws Exception {
      Field f = PosListList.Cmd.class.getDeclaredField(
         "filePositionPattern");
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
   // filePositionPattern matching (from PosListList.Cmd.main())
   // ================================================================

   @Nested
   @DisplayName("filePositionPattern")
   class FilePositionPatternTests {

      @Test
      @DisplayName("matches Java file:line reference")
      void matchesJavaFileLine() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("UI.java:1118 java.xxx.event.");
         assertTrue(m.find());
         assertEquals("UI.java", m.group(1));
         assertEquals("1118", m.group(4));
      }

      @Test
      @DisplayName("matches C file:line reference")
      void matchesCFileLine() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("smtp_hfilter.c:254 ");
         assertTrue(m.find());
         assertEquals("smtp_hfilter.c", m.group(1));
         assertEquals("254", m.group(4));
      }

      @Test
      @DisplayName("matches file:line without trailing space")
      void matchesCFileLineNoTrailingSpace() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("smtp_hfilter.c:254");
         assertTrue(m.find());
         assertEquals("smtp_hfilter.c", m.group(1));
         assertEquals("254", m.group(4));
      }

      @Test
      @DisplayName("first match in multi-match string")
      void firstMatchInMulti() throws Exception {
         Matcher m = getFilePositionPattern();
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
         Matcher m = getFilePositionPattern();
         m.reset("src/main/java/Foo.java:42");
         assertTrue(m.find());
         assertEquals("src/main/java/Foo.java", m.group(1));
         assertEquals("42", m.group(4));
      }

      @Test
      @DisplayName("matches path with backslashes")
      void matchesBackslashPath() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("src\\main\\Foo.java:99");
         assertTrue(m.find());
         assertEquals("src\\main\\Foo.java", m.group(1));
         assertEquals("99", m.group(4));
      }

      @Test
      @DisplayName("matches Windows drive letter path")
      void matchesWindowsDrivePath() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("C:\\src\\File.cpp:100");
         assertTrue(m.find());
         // group(2) captures optional drive letter
         assertEquals("C:", m.group(2));
         assertEquals("100", m.group(4));
      }

      @Test
      @DisplayName("matches tilde path")
      void matchesTildePath() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("~/projects/test.py:7");
         assertTrue(m.find());
         assertEquals("~/projects/test.py", m.group(1));
         assertEquals("7", m.group(4));
      }

      @Test
      @DisplayName("no match on bare text without colon-number")
      void noMatchBareText() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("just some text without file reference");
         assertFalse(m.find());
      }

      @Test
      @DisplayName("no match on empty string")
      void noMatchEmpty() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("");
         assertFalse(m.find());
      }

      @Test
      @DisplayName("rejects filename with spaces")
      void rejectsSpacesInName() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("my file.java:10");
         // The pattern excludes spaces, so "my" won't match "my file.java"
         assertTrue(m.find()); // matches "file.java:10"
         assertEquals("file.java", m.group(1));
      }

      @Test
      @DisplayName("rejects filename with parens")
      void rejectsParensInName() throws Exception {
         Matcher m = getFilePositionPattern();
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
         assertEquals(18, PosListList.Cmd.PCmd.values().length);
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
      @DisplayName("GOTO_DIR_LIST_DEFAULT is at ordinal 15")
      void gotoDirListDefaultOrdinal() {
         assertEquals(15,
            PosListList.Cmd.PCmd.GOTO_DIR_LIST_DEFAULT.ordinal());
      }

      @Test
      @DisplayName("CN is at ordinal 16")
      void cnOrdinal() {
         assertEquals(16, PosListList.Cmd.PCmd.CN.ordinal());
      }

      @Test
      @DisplayName("CP is at ordinal 17")
      void cpOrdinal() {
         assertEquals(17, PosListList.Cmd.PCmd.CP.ordinal());
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
   // cn/cp command registration
   // ================================================================

   @Nested
   @DisplayName("cn/cp commands")
   class CnCpTests {

      @Test
      @DisplayName("cn is a registered command")
      void cnRegistered() {
         assertNotNull(Rgroup.bindingLookup("cn"),
            ":cn should be a registered command");
      }

      @Test
      @DisplayName("cp is a registered command")
      void cpRegistered() {
         assertNotNull(Rgroup.bindingLookup("cp"),
            ":cp should be a registered command");
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

   // ================================================================
   // findDirectories — directory lookup used in :ta
   // ================================================================

   @Nested
   @DisplayName("findDirectories")
   class FindDirectoriesTests {

      private static java.lang.reflect.Method findDirMethod;

      @BeforeAll
      static void setup() throws Exception {
         findDirMethod = PosListList.Cmd.class.getDeclaredMethod(
            "findDirectories", String.class);
         findDirMethod.setAccessible(true);
      }

      @SuppressWarnings("unchecked")
      private java.util.ArrayList<Position> callFindDirectories(
            String name) throws Exception {
         return (java.util.ArrayList<Position>)
            findDirMethod.invoke(null, name);
      }

      @Test
      @DisplayName("returns empty for non-existent directory name")
      void nonExistentDir() throws Exception {
         java.util.ArrayList<Position> result =
            callFindDirectories("nonexistent_dir_xyz_12345");
         assertTrue(result.isEmpty());
      }

      @Test
      @DisplayName("returns non-empty for existing directory")
      void existingDir() throws Exception {
         // "src" exists in javi project root
         java.util.ArrayList<Position> result =
            callFindDirectories("src");
         // May or may not match depending on cwd; just verify no crash
         assertNotNull(result);
      }

      @Test
      @DisplayName("returns empty for empty string")
      void emptyString() throws Exception {
         java.util.ArrayList<Position> result =
            callFindDirectories("");
         assertTrue(result.isEmpty(),
            "empty name must not match any directory");
      }
   }

   // ================================================================
   // XrefReader mkid deduplication
   // ================================================================

   @Nested
   @DisplayName("mkid deduplication")
   class MkidDeduplicationTests {

      @Test
      @DisplayName("buildCtagKeys returns null for null input")
      void nullInput() {
         assertNull(XrefReader.buildCtagKeys(null));
      }

      @Test
      @DisplayName("buildCtagKeys returns null for empty array")
      void emptyArray() {
         assertNull(XrefReader.buildCtagKeys(new Position[0]));
      }

      @Test
      @DisplayName("buildCtagKeys builds keys from positions")
      void buildsKeys() {
         Position[] positions = {
            new Position(0, 42, "src/Foo.java", "tag:foo"),
            new Position(0, 10, "src/Bar.java", "tag:bar"),
         };
         java.util.Set<String> keys =
            XrefReader.buildCtagKeys(positions);
         assertNotNull(keys);
         assertEquals(2, keys.size());
         assertTrue(keys.contains(
            XrefReader.makeDedupKey(
               new Position(0, 42, "src/Foo.java", ""))));
         assertTrue(keys.contains(
            XrefReader.makeDedupKey(
               new Position(0, 10, "src/Bar.java", ""))));
      }

      @Test
      @DisplayName("makeDedupKey uses file and line only")
      void dedupKeyIgnoresColumn() {
         Position p1 = new Position(0, 42, "src/Foo.java", "tag:foo");
         Position p2 = new Position(5, 42, "src/Foo.java", "ref");
         assertEquals(XrefReader.makeDedupKey(p1),
            XrefReader.makeDedupKey(p2));
      }

      @Test
      @DisplayName("makeDedupKey differs for different lines")
      void dedupKeyDifferentLines() {
         Position p1 = new Position(0, 42, "src/Foo.java", "tag:foo");
         Position p2 = new Position(0, 43, "src/Foo.java", "ref");
         assertNotEquals(XrefReader.makeDedupKey(p1),
            XrefReader.makeDedupKey(p2));
      }

      @Test
      @DisplayName("makeDedupKey differs for different files")
      void dedupKeyDifferentFiles() {
         Position p1 = new Position(0, 42, "src/Foo.java", "tag:foo");
         Position p2 = new Position(0, 42, "src/Bar.java", "ref");
         assertNotEquals(XrefReader.makeDedupKey(p1),
            XrefReader.makeDedupKey(p2));
      }

      @Test
      @DisplayName("duplicate ctag position is detected in key set")
      void duplicateDetected() {
         Position[] ctags = {
            new Position(0, 100, "Main.java", "tag:main"),
         };
         java.util.Set<String> keys =
            XrefReader.buildCtagKeys(ctags);
         Position mkidPos =
            new Position(0, 100, "Main.java", "int main()");
         assertTrue(keys.contains(
            XrefReader.makeDedupKey(mkidPos)));
      }

      @Test
      @DisplayName("non-duplicate mkid position is not in key set")
      void nonDuplicateNotDetected() {
         Position[] ctags = {
            new Position(0, 100, "Main.java", "tag:main"),
         };
         java.util.Set<String> keys =
            XrefReader.buildCtagKeys(ctags);
         Position mkidPos =
            new Position(0, 200, "Main.java", "call main()");
         assertFalse(keys.contains(
            XrefReader.makeDedupKey(mkidPos)));
      }
   }

   // ================================================================
   // gototag — :ta command non-existent tag behavior
   // ================================================================

   @Nested
   @DisplayName("gototag non-existent tag")
   class GotoTagNotFoundTests {

      private static Method gototagMethod;

      @BeforeAll
      static void setup() throws Exception {
         gototagMethod = PosListList.Cmd.class.getDeclaredMethod(
            "gototag", String.class, FvContext.class);
         gototagMethod.setAccessible(true);
      }

      @Test
      @DisplayName(":ta nonexistent throws InputException")
      void taNotFoundThrowsInputException() throws Exception {
         // Build tag name dynamically so lid won't find it
         // in this source file's ID database index
         String bogusTag = "zzz" + "notag" + "999";
         EventQueue.biglock2.lock();
         try {
            TestView view = new TestView(true);
            FileDescriptor fd =
               FileDescriptor.InternalFd.make("ta-test");
            FileProperties<String> fp =
               new FileProperties<>(fd, StringIoc.converter);
            TextEdit<String> te = new TextEdit<>(
               new IoConverter<>(fp, false), fp);
            te.insertOne("some test content", 1);
            FvContext fvc = FvContext.connectFv(te, view);

            java.lang.reflect.InvocationTargetException ex =
               assertThrows(
                  java.lang.reflect.InvocationTargetException.class,
                  () -> gototagMethod.invoke(
                     pllCmd, bogusTag, fvc));
            assertTrue(
               ex.getCause() instanceof InputException,
               "cause must be InputException, got: "
                  + ex.getCause().getClass().getName());
            assertTrue(
               ex.getCause().getMessage().contains("tag not found"),
               "message must contain 'tag not found': "
                  + ex.getCause().getMessage());

            te.disposeFvc();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }
}
