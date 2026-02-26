package javi;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link EditContainer} edge cases.
 *
 * <p>
 * Tests low-level operations: remove, readOnly enforcement,
 * moveLine, copyLine, changeElementAt, boundary behavior of
 * at/finish, multi-operation undo/redo, and modified tracking.
 * </p>
 */
class EditContainerJUnitTest {

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

    // --- Helpers ---

    private static String testPath(String name) {
        return history.Testutil.testFile(name).getPath();
    }

    private static FileDescriptor.LocalFile makeLocal(String name) {
        return FileDescriptor.LocalFile.make(history.Testutil.testFile(name));
    }

    private static TextEdit<String> openTestFile(String name) {
        FileDescriptor fd = FileDescriptor.make(testPath(name));
        FileProperties<String> fp =
            new FileProperties<>(fd, StringIoc.converter);
        FileInput fi = new FileInput(fp);
        TextEdit<String> te = new TextEdit<>(fi, fp);
        te.finish();
        assertFalse(te.getError(),
            "File should open without error: " + name);
        return te;
    }

    private static void deleteTestFiles(String... names)
            throws IOException {
        for (String name : names) {
            makeLocal(name).delete();
            makeLocal(name + ".dmp2").delete();
        }
    }

    // ============================================================
    // remove() tests
    // ============================================================

    @Test
    void removeSingleLine() throws IOException {
        String fname = "ju_ec_rem1";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();
        assertEquals(5, ex.finish());
        assertEquals("bbb", ex.at(2).toString());

        ex.remove(2, 1);
        assertEquals(4, ex.finish());
        assertEquals("aaa", ex.at(1).toString());
        assertEquals("ccc", ex.at(2).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    @Test
    void removeRange() throws IOException {
        String fname = "ju_ec_rem2";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
        ex.checkpoint();
        assertEquals(6, ex.finish());

        // Remove lines 2-3 (bbb and ccc)
        ex.remove(2, 2);
        assertEquals(4, ex.finish());
        assertEquals("aaa", ex.at(1).toString());
        assertEquals("ddd", ex.at(2).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    @Test
    void removeAndUndo() throws IOException {
        String fname = "ju_ec_rem_undo";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();
        assertEquals(5, ex.finish());

        ex.remove(2, 1);
        ex.checkpoint();
        assertEquals(4, ex.finish());
        assertEquals("ccc", ex.at(2).toString());

        // Undo the remove — bbb should return
        ex.undo();
        assertEquals(5, ex.finish());
        assertEquals("bbb", ex.at(2).toString());
        assertEquals("ccc", ex.at(3).toString());

        // Redo the remove
        ex.redo();
        assertEquals(4, ex.finish());
        assertEquals("ccc", ex.at(2).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    @Test
    void removeReturnsDeletedLines() throws IOException {
        String fname = "ju_ec_rem_ret";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();

        ArrayList<String> deleted = ex.remove(1, 2);
        assertEquals(2, deleted.size());
        assertEquals("aaa", deleted.get(0));
        assertEquals("bbb", deleted.get(1));

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // ============================================================
    // readOnly enforcement tests
    // ============================================================

    @Test
    void readOnlyPreventsInsert() throws IOException {
        String fname = "ju_ec_ro_ins";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\n", 0, 1);
        ex.checkpoint();
        ex.setReadOnly(true);

        assertThrows(EditContainer.ReadOnlyException.class, () ->
            ex.inserttext("bbb", 0, 1));

        ex.setReadOnly(false);
        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    @Test
    void readOnlyPreventsRemove() throws IOException {
        String fname = "ju_ec_ro_rem";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\n", 0, 1);
        ex.checkpoint();
        ex.setReadOnly(true);

        assertThrows(EditContainer.ReadOnlyException.class, () ->
            ex.remove(1, 1));

        ex.setReadOnly(false);
        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    @Test
    void readOnlyPreventsUndo() throws IOException {
        String fname = "ju_ec_ro_undo";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\n", 0, 1);
        ex.checkpoint();
        ex.setReadOnly(true);

        assertThrows(EditContainer.ReadOnlyException.class, () ->
            ex.undo());

        ex.setReadOnly(false);
        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    @Test
    void readOnlyPreventsChangeElement() throws IOException {
        String fname = "ju_ec_ro_chg";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\n", 0, 1);
        ex.checkpoint();
        ex.setReadOnly(true);

        assertThrows(EditContainer.ReadOnlyException.class, () ->
            ex.changeElementAtStr("zzz", 1));

        ex.setReadOnly(false);
        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // ============================================================
    // moveLine / copyLine tests
    // ============================================================

    @Test
    void moveLineForward() throws IOException {
        String fname = "ju_ec_mvfwd";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();

        // Move line 1 ("aaa") to position 4 (after "ccc")
        ex.moveLine(1, 4);
        assertEquals("bbb", ex.at(1).toString());
        assertEquals("ccc", ex.at(2).toString());
        assertEquals("aaa", ex.at(3).toString());
        assertEquals(5, ex.finish());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    @Test
    void moveLineBackward() throws IOException {
        String fname = "ju_ec_mvbk";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();

        // Move line 3 ("ccc") to position 1
        ex.moveLine(3, 1);
        assertEquals("ccc", ex.at(1).toString());
        assertEquals("aaa", ex.at(2).toString());
        assertEquals("bbb", ex.at(3).toString());
        assertEquals(5, ex.finish());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    @Test
    void copyLineDuplicates() throws IOException {
        String fname = "ju_ec_cpy";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();
        assertEquals(5, ex.finish());

        // Copy line 1 ("aaa") to position 4 (after "ccc")
        ex.copyLine(1, 4);
        assertEquals(6, ex.finish());
        assertEquals("aaa", ex.at(1).toString());
        assertEquals("bbb", ex.at(2).toString());
        assertEquals("ccc", ex.at(3).toString());
        assertEquals("aaa", ex.at(4).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // ============================================================
    // changeElementAtStr tests
    // ============================================================

    @Test
    void changeElementAtStrModifiesLine() throws IOException {
        String fname = "ju_ec_chg";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\n", 0, 1);
        ex.checkpoint();

        ex.changeElementAtStr("xxx", 1);
        assertEquals("xxx", ex.at(1).toString());
        assertEquals("bbb", ex.at(2).toString());

        // Undo restores original
        ex.checkpoint();
        ex.undo();
        assertEquals("aaa", ex.at(1).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // ============================================================
    // Boundary and counting tests
    // ============================================================

    @Test
    void atIndexZeroReturnsSentinel() throws IOException {
        String fname = "ju_ec_at0";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        // Index 0 is the internal sentinel (always empty)
        assertEquals(0, ex.at(0).toString().length());

        ex.inserttext("aaa\n", 0, 1);
        // Sentinel unchanged after insert
        assertEquals(0, ex.at(0).toString().length());
        assertEquals("aaa", ex.at(1).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    @Test
    void finishIncludesSentinel() throws IOException {
        String fname = "ju_ec_finish";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        // New empty file: sentinel(0) + empty-line(1) = 2
        assertEquals(2, ex.finish());

        ex.inserttext("aaa\nbbb\n", 0, 1);
        // sentinel(0) + "aaa"(1) + "bbb"(2) + ""(3) = 4
        assertEquals(4, ex.finish());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    @Test
    void containsReturnsFalseForPastEnd() throws IOException {
        String fname = "ju_ec_contains";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\n", 0, 1);
        // finish() should be 3: sentinel + "aaa" + ""
        assertEquals(3, ex.finish());
        assertTrue(ex.containsNow(2));
        assertFalse(ex.containsNow(3));
        assertFalse(ex.containsNow(100));

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // ============================================================
    // Multi-operation undo/redo tests
    // ============================================================

    @Test
    void multiOperationUndoRedo() throws IOException {
        String fname = "ju_ec_multi";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);

        // Op 1: insert lines
        ex.inserttext("aaa\nbbb\n", 0, 1);
        ex.checkpoint();
        assertEquals("aaa", ex.at(1).toString());
        assertEquals("bbb", ex.at(2).toString());

        // Op 2: modify a line
        ex.changeElementAtStr("ccc", 1);
        ex.checkpoint();
        assertEquals("ccc", ex.at(1).toString());

        // Op 3: remove a line
        ex.remove(2, 1);
        ex.checkpoint();
        assertEquals(3, ex.finish());

        // Undo op 3 — line 2 restored
        ex.undo();
        assertEquals(4, ex.finish());
        assertEquals("bbb", ex.at(2).toString());

        // Undo op 2 — line 1 back to "aaa"
        ex.undo();
        assertEquals("aaa", ex.at(1).toString());

        // Undo op 1 — back to empty
        ex.undo();
        assertEquals(2, ex.finish());
        assertEquals(0, ex.at(1).toString().length());

        // Redo all three operations
        ex.redo();
        assertEquals("aaa", ex.at(1).toString());
        ex.redo();
        assertEquals("ccc", ex.at(1).toString());
        ex.redo();
        assertEquals(3, ex.finish());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // ============================================================
    // isModified tracking tests
    // ============================================================

    @Test
    void isModifiedTracking() throws IOException {
        String fname = "ju_ec_mod";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        assertFalse(ex.isModified(), "new file should not be modified");

        ex.inserttext("aaa", 0, 1);
        ex.checkpoint();
        assertTrue(ex.isModified(), "after insert should be modified");

        ex.undo();
        assertFalse(ex.isModified(), "after undo should not be modified");

        ex.redo();
        assertTrue(ex.isModified(), "after redo should be modified");

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // ============================================================
    // getElementsAt tests
    // ============================================================

    @Test
    void getElementsAtReturnsRange() throws IOException {
        String fname = "ju_ec_getels";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
        ex.checkpoint();

        ArrayList<String> elems = ex.getElementsAt(2, 2);
        assertEquals(2, elems.size());
        assertEquals("bbb", elems.get(0));
        assertEquals("ccc", elems.get(1));

        ex.disposeFvc();
        deleteTestFiles(fname);
    }
}
