package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deep coverage for {@link EditContainer} and {@link TextEdit} —
 * insert, delete, change, checkpoint, undo/redo, search, range
 * operations, and processCommand paths not covered elsewhere.
 */
class EditContainerDeepCoverageJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   private static TextEdit<String> makeBuffer(String name, String content)
         throws IOException {
      String path = history.Testutil.testFile(name).getPath();
      FileDescriptor.LocalFile.make(
         history.Testutil.testFile(name)).delete();
      try (java.io.OutputStreamWriter w =
            new java.io.OutputStreamWriter(
               new java.io.FileOutputStream(path),
               java.nio.charset.StandardCharsets.UTF_8)) {
         w.write(content);
      }
      FileDescriptor fd = FileDescriptor.make(path);
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      return te;
   }

   private static TextEdit<String> makeInternalBuffer(String name) {
      FileProperties<String> fp = new FileProperties<>(
         FileDescriptor.InternalFd.make(name), StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      return te;
   }

   private static void cleanup(TextEdit<String> te, String... names)
         throws IOException {
      te.disposeFvc();
      for (String name : names) {
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(name)).delete();
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(name + ".dmp2")).delete();
      }
   }

   // ── Basic insert operations ───────────────────────────────

   @Nested
   @DisplayName("Insert operations")
   class InsertOps {

      @Test
      @DisplayName("insertOne adds element at position")
      void insertOneAdds() throws Exception {
         String fname = "ju_ecd_ins1";
         TextEdit<String> te = makeBuffer(fname, "line1\nline3\n");
         te.insertOne("line2", 1);
         te.checkpoint();
         assertEquals("line2", te.at(1).toString());
         assertEquals("line1", te.at(2).toString());
         cleanup(te, fname);
      }

      @Test
      @DisplayName("inserttext adds multiple lines")
      void inserttextAddsLines() throws Exception {
         String fname = "ju_ecd_insm";
         TextEdit<String> te = makeBuffer(fname, "first\n");
         te.inserttext("a\nb\nc\n", 0, 1);
         te.checkpoint();
         assertTrue(te.readIn() >= 4,
            "should have at least 4 lines after insert");
         cleanup(te, fname);
      }

      @Test
      @DisplayName("inserttext at end of buffer")
      void inserttextAtEnd() throws Exception {
         String fname = "ju_ecd_insend";
         TextEdit<String> te = makeBuffer(fname, "existing\n");
         int lastLine = te.readIn() - 1;
         te.inserttext("appended\n", 0, lastLine);
         te.checkpoint();
         boolean found = false;
         for (int i = 1; i < te.readIn(); i++) {
            if ("appended".equals(te.at(i).toString())) {
               found = true;
               break;
            }
         }
         assertTrue(found, "appended line should be in buffer");
         cleanup(te, fname);
      }
   }

   // ── Delete operations ─────────────────────────────────────

   @Nested
   @DisplayName("Delete operations")
   class DeleteOps {

      @Test
      @DisplayName("remove deletes lines from buffer")
      void removeDeletesLines() throws Exception {
         String fname = "ju_ecd_rm";
         TextEdit<String> te =
            makeBuffer(fname, "a\nb\nc\nd\n");
         int before = te.readIn();
         te.remove(2, 1);  // remove line 2
         te.checkpoint();
         assertEquals(before - 1, te.readIn());
         assertEquals("a", te.at(1).toString());
         assertEquals("c", te.at(2).toString());
         cleanup(te, fname);
      }

      @Test
      @DisplayName("remove multiple lines")
      void removeMultipleLines() throws Exception {
         String fname = "ju_ecd_rmm";
         TextEdit<String> te =
            makeBuffer(fname, "a\nb\nc\nd\ne\n");
         int before = te.readIn();
         te.remove(2, 2);  // remove lines 2-3
         te.checkpoint();
         assertEquals(before - 2, te.readIn());
         assertEquals("a", te.at(1).toString());
         assertEquals("d", te.at(2).toString());
         cleanup(te, fname);
      }
   }

   // ── Change operations ─────────────────────────────────────

   @Nested
   @DisplayName("Change operations")
   class ChangeOps {

      @Test
      @DisplayName("change replaces element at index")
      void changeReplacesElement() throws Exception {
         String fname = "ju_ecd_chg";
         TextEdit<String> te =
            makeBuffer(fname, "original\n");
         te.changeElementAtStr("replaced", 1);
         te.checkpoint();
         assertEquals("replaced", te.at(1).toString());
         cleanup(te, fname);
      }
   }

   // ── Undo/Redo ─────────────────────────────────────────────

   @Nested
   @DisplayName("Undo/Redo operations")
   class UndoRedo {

      @Test
      @DisplayName("undo reverses insert")
      void undoReversesInsert() throws Exception {
         String fname = "ju_ecd_undo1";
         TextEdit<String> te =
            makeBuffer(fname, "a\nb\n");
         te.checkpoint();
         int before = te.readIn();
         te.insertOne("extra", 1);
         te.checkpoint();
         assertEquals(before + 1, te.readIn());

         te.undo();
         assertEquals(before, te.readIn());
         cleanup(te, fname);
      }

      @Test
      @DisplayName("redo restores undone change")
      void redoRestoresChange() throws Exception {
         String fname = "ju_ecd_redo";
         TextEdit<String> te =
            makeBuffer(fname, "line\n");
         te.checkpoint();
         te.changeElementAtStr("modified", 1);
         te.checkpoint();
         te.undo();
         assertEquals("line", te.at(1).toString());
         te.redo();
         assertEquals("modified", te.at(1).toString());
         cleanup(te, fname);
      }

      @Test
      @DisplayName("undo after remove restores lines")
      void undoAfterRemove() throws Exception {
         String fname = "ju_ecd_undorm";
         TextEdit<String> te =
            makeBuffer(fname, "x\ny\nz\n");
         te.checkpoint();
         int before = te.readIn();
         te.remove(2, 1);
         te.checkpoint();
         te.undo();
         assertEquals(before, te.readIn());
         assertEquals("y", te.at(2).toString());
         cleanup(te, fname);
      }
   }

   // ── isModified / containsNow / finish ─────────────────────

   @Nested
   @DisplayName("State queries")
   class StateQueries {

      @Test
      @DisplayName("isModified false for fresh buffer")
      void isModifiedFalse() throws Exception {
         String fname = "ju_ecd_ismod";
         TextEdit<String> te =
            makeBuffer(fname, "data\n");
         assertFalse(te.isModified());
         cleanup(te, fname);
      }

      @Test
      @DisplayName("isModified true after change")
      void isModifiedTrue() throws Exception {
         String fname = "ju_ecd_ismod2";
         TextEdit<String> te =
            makeBuffer(fname, "data\n");
         te.changeElementAtStr("changed", 1);
         te.checkpoint();
         assertTrue(te.isModified());
         cleanup(te, fname);
      }

      @Test
      @DisplayName("containsNow for valid index")
      void containsNowValid() throws Exception {
         String fname = "ju_ecd_cn";
         TextEdit<String> te =
            makeBuffer(fname, "a\nb\n");
         assertTrue(te.containsNow(1));
         assertTrue(te.containsNow(2));
         cleanup(te, fname);
      }

      @Test
      @DisplayName("containsNow false for beyond end")
      void containsNowBeyondEnd() throws Exception {
         String fname = "ju_ecd_cnbey";
         TextEdit<String> te =
            makeBuffer(fname, "a\n");
         assertFalse(te.containsNow(999));
         cleanup(te, fname);
      }

      @Test
      @DisplayName("finish returns line count including header")
      void finishReturnsCount() throws Exception {
         String fname = "ju_ecd_fin";
         TextEdit<String> te =
            makeBuffer(fname, "a\nb\nc\n");
         int count = te.finish();
         assertEquals(4, count, "3 lines + 1 header = 4");
         cleanup(te, fname);
      }

      @Test
      @DisplayName("readIn returns same as finish for completed file")
      void readInEqualsFinish() throws Exception {
         String fname = "ju_ecd_ri";
         TextEdit<String> te =
            makeBuffer(fname, "a\nb\n");
         te.finish();
         assertEquals(te.finish(), te.readIn());
         cleanup(te, fname);
      }

      @Test
      @DisplayName("donereading returns true after finish")
      void donereadingTrue() throws Exception {
         String fname = "ju_ecd_dr";
         TextEdit<String> te =
            makeBuffer(fname, "data\n");
         te.finish();
         assertTrue(te.donereading());
         cleanup(te, fname);
      }
   }

   // ── processCommand ex-mode operations ─────────────────────

   @Nested
   @DisplayName("processCommand ex-mode")
   class ExMode {

      @Test
      @DisplayName("processCommand :d deletes line")
      void processCommandDelete() throws Exception {
         String fname = "ju_ecd_pcd";
         UI.setStream(new StringReader(""));
         TextEdit<String> te =
            makeBuffer(fname, "alpha\nbeta\ngamma\n");
         int before = te.readIn();
         te.processCommand("2d", 1);
         assertEquals(before - 1, te.readIn());
         assertEquals("alpha", te.at(1).toString());
         assertEquals("gamma", te.at(2).toString());
         cleanup(te, fname);
      }

      @Test
      @DisplayName("processCommand :s/old/new/ substitutes")
      void processCommandSubstitute() throws Exception {
         String fname = "ju_ecd_pcs";
         UI.setStream(new StringReader(""));
         TextEdit<String> te =
            makeBuffer(fname, "hello world\n");
         te.processCommand("1s/world/earth/", 1);
         assertEquals("hello earth", te.at(1).toString());
         cleanup(te, fname);
      }

      @Test
      @DisplayName("processCommand :s///g global substitute")
      void processCommandGlobalSub() throws Exception {
         String fname = "ju_ecd_pcsg";
         UI.setStream(new StringReader(""));
         TextEdit<String> te =
            makeBuffer(fname, "aaa bbb aaa\n");
         te.processCommand("1s/aaa/ccc/g", 1);
         assertEquals("ccc bbb ccc", te.at(1).toString());
         cleanup(te, fname);
      }

      @Test
      @DisplayName("processCommand :1,3d range delete")
      void processCommandRangeDelete() throws Exception {
         String fname = "ju_ecd_pcrd";
         UI.setStream(new StringReader(""));
         TextEdit<String> te =
            makeBuffer(fname, "a\nb\nc\nd\ne\n");
         te.processCommand("2,4d", 1);
         // Should remove lines 2-4, leaving a and e
         assertEquals("a", te.at(1).toString());
         assertEquals("e", te.at(2).toString());
         cleanup(te, fname);
      }

      @Test
      @DisplayName("processCommand :y yanks line")
      void processCommandYank() throws Exception {
         String fname = "ju_ecd_pcy";
         UI.setStream(new StringReader(""));
         TextEdit<String> te =
            makeBuffer(fname, "yanked\n");
         te.processCommand("1y", 1);
         // yank should not change buffer
         assertEquals("yanked", te.at(1).toString());
         cleanup(te, fname);
      }

   }

   // ── Search ────────────────────────────────────────────────

   @Nested
   @DisplayName("Search operations")
   class SearchOps {

      @Test
      @DisplayName("searchForward finds pattern in line")
      void searchForwardFindsPattern() throws Exception {
         String fname = "ju_ecd_srch";
         TextEdit<String> te =
            makeBuffer(fname, "foo\nbar\nbaz\n");

         java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("bar").matcher("");
         assertTrue(te.searchForward(m, 0, 2),
            "should find 'bar' in line 2");
         cleanup(te, fname);
      }

      @Test
      @DisplayName("searchForward returns false when not found")
      void searchForwardNotFound() throws Exception {
         String fname = "ju_ecd_nf";
         TextEdit<String> te =
            makeBuffer(fname, "abc\ndef\n");

         java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("xyz").matcher("");
         assertFalse(te.searchForward(m, 0, 1),
            "should return false for no match");
         cleanup(te, fname);
      }
   }

   // ── getName / fdes / toString ─────────────────────────────

   @Nested
   @DisplayName("Identity methods")
   class Identity {

      @Test
      @DisplayName("getName returns file short name")
      void getNameReturnsShortName() throws Exception {
         String fname = "ju_ecd_name";
         TextEdit<String> te = makeBuffer(fname, "x\n");
         String name = te.getName();
         assertNotNull(name);
         assertTrue(name.contains(fname),
            "name should contain filename");
         cleanup(te, fname);
      }

      @Test
      @DisplayName("fdes returns FileDescriptor")
      void fdesReturnsDescriptor() throws Exception {
         String fname = "ju_ecd_fdes";
         TextEdit<String> te = makeBuffer(fname, "x\n");
         assertNotNull(te.fdes());
         cleanup(te, fname);
      }

      @Test
      @DisplayName("toString returns non-null")
      void toStringNonNull() throws Exception {
         String fname = "ju_ecd_ts";
         TextEdit<String> te = makeBuffer(fname, "x\n");
         assertNotNull(te.toString());
         assertFalse(te.toString().isEmpty());
         cleanup(te, fname);
      }
   }

   // ── Iterator ──────────────────────────────────────────────

   @Test
   @DisplayName("iterator traverses all elements")
   void iteratorTraverses() throws Exception {
      String fname = "ju_ecd_iter";
      TextEdit<String> te =
         makeBuffer(fname, "a\nb\nc\n");
      int count = 0;
      for (String s : te) {
         count++;
      }
      assertEquals(3, count, "should iterate over 3 elements");
      cleanup(te, fname);
   }

   // ── setReadOnly ───────────────────────────────────────────

   @Test
   @DisplayName("setReadOnly prevents modification flag")
   void setReadOnly() throws Exception {
      TextEdit<String> te = makeInternalBuffer("ju_ecd_ro");
      te.setReadOnly(true);
      assertFalse(te.getFileProperties().isWriteable());
      te.setReadOnly(false);
      assertTrue(te.getFileProperties().isWriteable());
      te.disposeFvc();
   }
}
