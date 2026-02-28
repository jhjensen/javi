package javi;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 port of selected integration tests from {@code EditTester1}.
 *
 * <p>
 * Tests core TextEdit operations: insert, undo, redo, insertStream,
 * and multi-line editing with persistence through dispose/reopen cycles.
 * </p>
 *
 * <p>
 * Each test acquires {@link EventQueue#biglock2} because most
 * {@link EditContainer} operations assert ownership of that lock.
 * </p>
 */
class TextEditJUnitTest {

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

    // --- Helpers (mirror EditTester1 utilities) ---

    private static String testPath(String name) {
        return history.Testutil.testFile(name).getPath();
    }

    private static FileDescriptor.LocalFile makeLocal(String name) {
        return FileDescriptor.LocalFile.make(history.Testutil.testFile(name));
    }

    private static TextEdit<String> openTestFile(String name) {
        FileDescriptor fd = FileDescriptor.make(testPath(name));
        FileProperties<String> fp = new FileProperties<>(fd, StringIoc.converter);
        FileInput fi = new FileInput(fp);
        TextEdit<String> te = new TextEdit<>(fi, fp);
        te.finish();
        assertFalse(te.getError(),
                "File should open without error: " + name);
        return te;
    }

    private static void writeTestFile(String name, String contents)
            throws IOException {
        makeLocal(name).delete();
        try (OutputStreamWriter fs = new OutputStreamWriter(
                new FileOutputStream(testPath(name)), StandardCharsets.UTF_8)) {
            fs.write(contents);
        }
    }

    private static void deleteTestFiles(String... names)
            throws IOException {
        for (String name : names) {
            makeLocal(name).delete();
            makeLocal(name + ".dmp2").delete();
        }
    }

    // --- Tests ---

    /**
     * Port of test9: multiple sequential undo/redo operations.
     *
     * <p>
     * Inserts "a", "b", "c", "d" at the same position, then
     * walks undo/redo in various combinations to verify history
     * integrity. Reopens the file to confirm persistence.
     * </p>
     */
    @Test
    void multipleUndoRedo() throws IOException {
        String fname = "ju_test9";
        UI.setStream(new StringReader("b\n"));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("a", 0, 1);
        ex.checkpoint();
        ex.inserttext("b", 0, 1);
        ex.checkpoint();
        ex.inserttext("c", 0, 1);
        ex.checkpoint();
        ex.inserttext("d", 0, 1);
        ex.checkpoint();
        assertEquals("dcba", ex.at(1).toString());

        ex.undo();
        ex.undo();
        ex.undo();
        ex.undo();
        assertEquals(0, ex.at(1).toString().length());

        ex.redo();
        ex.redo();
        assertEquals("ba", ex.at(1).toString());

        ex.redo();
        ex.redo();
        assertEquals("dcba", ex.at(1).toString());

        ex.undo();
        ex.undo();
        assertEquals("ba", ex.at(1).toString());

        ex.undo();
        ex.undo();
        assertEquals(0, ex.at(1).toString().length());
        assertEquals(2, ex.finish());
        ex.disposeFvc();

        // Reopen and verify undo history survived persistence
        ex = openTestFile(fname);
        assertEquals(0, ex.at(1).toString().length());
        ex.redo();
        ex.redo();
        ex.redo();
        ex.redo();
        assertEquals("dcba", ex.at(1).toString());
        ex.undo();
        ex.undo();
        ex.undo();
        ex.undo();
        assertEquals(0, ex.at(1).toString().length());
        ex.disposeFvc();

        deleteTestFiles(fname);
    }

    /**
     * Port of test13: insert without save, reopen shows empty.
     *
     * <p>
     * When no .dmp2 backup exists and the file is new, an insert
     * followed by dispose (without checkpoint) should not persist.
     * </p>
     */
    @Test
    void insertWithoutSaveNotPersisted() throws IOException {
        String fname = "ju_test13";
        UI.setStream(new StringReader("f\n"));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("dmp", 0, 1);
        ex.disposeFvc();

        ex = openTestFile(fname);
        assertEquals(0, ex.at(1).toString().length());
        ex.disposeFvc();

        deleteTestFiles(fname);
    }

    /**
     * Port of test14: multiline inserttext with undo/redo.
     *
     * <p>
     * Inserts three lines, checkpoints, disposes and reopens.
     * Then performs a second round of inserts and verifies that
     * two undo operations walk back through both rounds.
     * </p>
     */
    @Test
    void multilineInsertUndoRedo() throws IOException {
        String fname = "ju_test14";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("a\n", 0, 1);
        ex.inserttext("b\n", 0, 2);
        ex.inserttext("c\n", 0, 3);
        ex.checkpoint();
        ex.printout();
        ex.disposeFvc();

        ex = openTestFile(fname);
        ex.undo();
        ex.redo();
        assertEquals("a", ex.at(1).toString());
        assertEquals("b", ex.at(2).toString());
        assertEquals("c", ex.at(3).toString());

        // Second round of inserts
        ex.inserttext("a\n", 0, 1);
        ex.inserttext("b\n", 0, 2);
        ex.inserttext("c\n", 0, 3);
        ex.checkpoint();

        assertEquals("a", ex.at(1).toString());
        assertEquals("b", ex.at(2).toString());
        assertEquals("c", ex.at(3).toString());
        assertEquals("a", ex.at(4).toString());
        assertEquals("b", ex.at(5).toString());
        assertEquals("c", ex.at(6).toString());

        // Undo both rounds
        ex.undo();
        ex.undo();
        assertEquals(0, ex.at(1).toString().length());
        assertEquals(2, ex.finish());

        // Redo both rounds
        ex.redo();
        ex.redo();
        assertEquals("a", ex.at(1).toString());
        assertEquals("b", ex.at(2).toString());
        assertEquals("c", ex.at(3).toString());
        assertEquals("a", ex.at(4).toString());
        assertEquals("b", ex.at(5).toString());
        assertEquals("c", ex.at(6).toString());

        ex.printout();
        ex.disposeFvc();

        deleteTestFiles(fname);
    }

    /**
     * Port of insertStreamTest: splice a buffered stream into
     * existing text.
     *
     * <p>
     * Creates a three-line file, inserts a two-line stream at
     * line 2, verifying the lines are spliced correctly. Then
     * disposes and reopens to confirm persistence.
     * </p>
     */
    @Test
    void insertStreamSplice() throws IOException, InputException {
        String fname = "ju_insertStream";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("a\nb\nc\n", 0, 1);
        assertEquals("a", ex.at(1).toString());
        assertEquals("b", ex.at(2).toString());
        assertEquals("c", ex.at(3).toString());

        ex.insertStream(
                new BufferedReader(new StringReader("z\ny\n")), 2);
        assertEquals("a", ex.at(1).toString());
        assertEquals("z", ex.at(2).toString());
        assertEquals("y", ex.at(3).toString());
        assertEquals("b", ex.at(4).toString());
        assertEquals("c", ex.at(5).toString());

        ex.checkpoint();
        ex.idleSave();
        ex.printout();
        ex.disposeFvc();

        // Reopen and verify persistence
        ex = openTestFile(fname);
        assertEquals("a", ex.at(1).toString());
        assertEquals("z", ex.at(2).toString());
        assertEquals("y", ex.at(3).toString());
        assertEquals("b", ex.at(4).toString());
        assertEquals("c", ex.at(5).toString());
        assertFalse(ex.isModified());
        ex.idleSave();
        ex.terminate();

        deleteTestFiles(fname);
    }

    /**
     * Port of test1 (simplified): basic insert, checkpoint, undo, redo.
     *
     * <p>
     * Verifies the fundamental edit cycle: insert text, checkpoint,
     * undo to restore original, redo to re-apply.
     * </p>
     */
    @Test
    void basicInsertUndoRedo() throws IOException {
        String fname = "ju_basic";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        assertEquals(2, ex.finish());

        ex.inserttext("aaa", 0, 1);
        ex.checkpoint();
        assertEquals("aaa", ex.at(1).toString());
        assertEquals(2, ex.finish());

        ex.undo();
        assertEquals(0, ex.at(1).toString().length());
        assertFalse(ex.isModified());
        assertEquals(2, ex.finish());

        ex.redo();
        assertEquals("aaa", ex.at(1).toString());
        assertTrue(ex.isModified());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    /**
     * Port of test5 (simplified): insertOne inserts a line before
     * the given index.
     *
     * <p>
     * Creates a single-line file, inserts a line before line 1,
     * verifies the two-line state, then undo restores original.
     * </p>
     */
    @Test
    void insertOneAndUndo() throws IOException {
        String fname = "ju_insertOne";
        UI.setStream(new StringReader("b\n"));
        deleteTestFiles(fname);

        writeTestFile(fname, "aaa\n");
        TextEdit<String> ex = openTestFile(fname);
        assertEquals("aaa", ex.at(1).toString());

        ex.insertOne("bbb", 1);
        ex.checkpoint();
        assertEquals("bbb", ex.at(1).toString());
        assertEquals("aaa", ex.at(2).toString());
        assertEquals(3, ex.finish());
        assertTrue(ex.isModified());

        ex.undo();
        assertFalse(ex.isModified());
        assertEquals("aaa", ex.at(1).toString());
        assertEquals(2, ex.finish());

        ex.redo();
        assertEquals("bbb", ex.at(1).toString());
        assertEquals("aaa", ex.at(2).toString());
        assertTrue(ex.isModified());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // Port of test8: insert, checkpoint, dispose, reopen, undo
    // =================================================================

    /**
     * Port of test8: insert text, checkpoint, dispose.
     * Reopen verifies data persisted, then undo clears it.
     */
    @Test
    void insertCheckpointDisposeReopenUndo() throws IOException {
        String fname = "ju_test8";
        UI.setStream(new StringReader("b\n"));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa", 0, 1);
        ex.checkpoint();
        assertEquals("aaa", ex.at(1).toString());
        assertEquals(2, ex.finish());
        ex.disposeFvc();

        // Reopen — checkpoint should have persisted
        ex = openTestFile(fname);
        assertEquals("aaa", ex.at(1).toString());
        ex.undo();
        assertEquals(0, ex.at(1).toString().length());
        ex.disposeFvc();

        deleteTestFiles(fname);
    }

    // =================================================================
    // Port of test11: inserttext on existing file, undo/redo cycle
    // =================================================================

    /**
     * Port of test11: open file with content, insert more text,
     * checkpoint, undo/redo, dispose/reopen to verify persistence.
     */
    @Test
    void insertOnExistingFileUndoRedoPersist() throws IOException {
        String fname = "ju_test11";
        UI.setStream(new StringReader("b\n"));
        deleteTestFiles(fname);

        writeTestFile(fname, "aaaa\n\bb\n");
        TextEdit<String> ex = openTestFile(fname);
        ex.printout();
        ex.undo();
        assertEquals("aaaa", ex.at(1).toString());
        ex.inserttext("bbb", 0, 1);
        ex.checkpoint();
        assertEquals("bbbaaaa", ex.at(1).toString());
        ex.undo();
        assertEquals("aaaa", ex.at(1).toString());
        ex.printout();
        ex.redo();
        assertEquals("bbbaaaa", ex.at(1).toString());
        ex.disposeFvc();

        // Reopen — redo state should persist
        ex = openTestFile(fname);
        assertEquals("bbbaaaa", ex.at(1).toString());
        ex.undo();
        assertEquals("aaaa", ex.at(1).toString());
        ex.disposeFvc();

        deleteTestFiles(fname);
    }

    // =================================================================
    // Port of test18: insert, undo, dispose, reopen, redo/undo
    // =================================================================

    /**
     * Port of test18: insert text, undo it, dispose.
     * Reopen file, redo then undo then undo again, verifying
     * undo history survives persistence round-trip.
     */
    @Test
    void insertUndoDisposeReopenRedoUndo() throws IOException {
        String fname = "ju_test18";
        UI.setStream(new StringReader("o"));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        assertEquals(2, ex.finish());
        ex.inserttext("aaa", 0, 1);
        ex.checkpoint();
        ex.idleSave();
        assertEquals(2, ex.finish());
        ex.undo();
        ex.printout();
        ex.disposeFvc();

        // Reopen — should still be in undone state
        ex = openTestFile(fname);
        ex.redo();
        ex.undo();
        ex.printout();
        ex.undo();
        ex.disposeFvc();

        deleteTestFiles(fname);
    }

    // =================================================================
    // Port of test6: terminate vs dispose — terminate discards backup
    // =================================================================

    /**
     * Port of test6: uses terminate() instead of disposeFvc() to
     * verify that terminate discards the backup state, so a reopen
     * shows the original file content without redo capability.
     */
    @Test
    void terminateDiscardsBackupState() throws IOException {
        String fname = "ju_test6";
        UI.setStream(new StringReader("bfbb\n"));
        deleteTestFiles(fname);

        writeTestFile(fname, "aaaa\n\bb\n");
        makeLocal(fname + ".dmp2").delete();

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("bbb", 0, 1);
        ex.checkpoint();
        ex.idleSave();
        ex.terminate();

        // After terminate, reopen should show original file content
        ex = openTestFile(fname);
        assertEquals("aaaa", ex.at(1).toString());
        assertFalse(ex.isModified());
        ex.redo();
        // terminate discards backup, so redo restores the insert
        assertEquals("bbbaaaa", ex.at(1).toString());
        assertTrue(ex.isModified());
        ex.idleSave();
        ex.terminate();

        // After second terminate with redo applied then terminated
        ex = openTestFile(fname);
        assertEquals("aaaa", ex.at(1).toString());
        assertFalse(ex.isModified());
        ex.redo();
        assertEquals("aaaa", ex.at(1).toString());
        assertFalse(ex.isModified());
        ex.disposeFvc();

        deleteTestFiles(fname);
    }

    // =================================================================
    // Port of test7: insertOne, undo without checkpoint, dispose
    // =================================================================

    /**
     * Port of test7: insertOne a line, checkpoint, then undo (without
     * saving), dispose. Reopen should show original state but allow
     * redo to recover the insert.
     */
    @Test
    void insertOneUndoDisposeReopenRedo() throws IOException {
        String fname = "ju_test7";
        UI.setStream(new StringReader("ob\n"));
        deleteTestFiles(fname);

        writeTestFile(fname, "aaa\n");
        makeLocal(fname + ".dmp2").delete();

        TextEdit<String> ex = openTestFile(fname);
        assertEquals("aaa", ex.at(1).toString());

        ex.insertOne("bbb", 1);
        ex.checkpoint();
        assertEquals("bbb", ex.at(1).toString());
        assertEquals("aaa", ex.at(2).toString());
        assertEquals(3, ex.finish());
        assertTrue(ex.isModified());
        ex.undo();
        assertFalse(ex.isModified());
        ex.disposeFvc();

        // Reopen — should show original since we undid
        ex = openTestFile(fname);
        assertEquals(2, ex.finish());
        assertEquals("aaa", ex.at(1).toString());
        assertFalse(ex.isModified());
        ex.redo();
        assertEquals("bbb", ex.at(1).toString());
        assertTrue(ex.isModified());
        ex.disposeFvc();

        deleteTestFiles(fname);
    }

    // =================================================================
    // Port of test16: corrupt backup recovery
    // =================================================================

    /**
     * Port of test16: tests handling of a corrupt .dmp2 backup file.
     * When the backup is corrupt, the editor should fall back and
     * allow access to either the file or backup contents depending
     * on user response via StreamInterface.
     */
    @Test
    void corruptBackupRecovery() throws IOException {
        String fname = "ju_test16";
        UI.setStream(new StringReader("bf"));
        deleteTestFiles(fname);

        writeTestFile(fname, "asdfafd\nasdfafdbb");
        writeTestFile(fname + ".dmp2", "asdfafd");

        // First open: corrupt backup, stream sends 'b' -> USEBACKUP
        TextEdit<String> ex = openTestFile(fname);
        assertEquals(0, ex.at(1).toString().length());
        assertEquals(2, ex.finish());
        ex.disposeFvc();

        // Second open: stream sends 'f' -> USEFILE
        ex = openTestFile(fname);
        assertEquals("asdfafd", ex.at(1).toString());
        assertEquals("asdfafdbb", ex.at(2).toString());
        ex.inserttext("a\n", 0, 1);
        ex.idleSave();
        ex.printout();
        ex.disposeFvc();

        // Third open: should have the insert persisted
        ex = openTestFile(fname);
        assertEquals("a", ex.at(1).toString());
        assertEquals("asdfafd", ex.at(2).toString());
        assertEquals("asdfafdbb", ex.at(3).toString());
        ex.disposeFvc();

        deleteTestFiles(fname);
    }

    // =================================================================
    // tabfix: port of EditTester1 test12 (simplified)
    // =================================================================

    /**
     * Port of test12 (simplified): tabfix converts tabs to spaces.
     *
     * <p>
     * Creates a file with tab characters and calls {@link TextEdit#tabfix}
     * to replace tabs with the specified number of spaces.
     * </p>
     */
    @Test
    void tabfixConvertsTabs() throws Exception {
        String fname = "ju_tabfix";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        writeTestFile(fname, "\taaaa\nbb\n");
        TextEdit<String> ex = openTestFile(fname);
        assertEquals("\taaaa", ex.at(1).toString());

        ex.tabfix(3);
        ex.checkpoint();
        assertEquals("   aaaa", ex.at(1).toString());
        // Line without tabs should be unchanged
        assertEquals("bb", ex.at(2).toString());

        // Undo restores original tabs
        ex.undo();
        assertEquals("\taaaa", ex.at(1).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // reload: port of EditTester1 test17
    // =================================================================

    /**
     * Port of test17: reload picks up changes from disk.
     *
     * <p>
     * Opens a file, overwrites it on disk, then calls
     * {@link EditContainer#reload()} to verify the new content
     * is reflected in the buffer.
     * </p>
     */
    @Test
    void reloadReflectsFileChanges() throws IOException {
        String fname = "ju_reload";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        writeTestFile(fname, "original\n");
        TextEdit<String> ex = openTestFile(fname);
        assertEquals("original", ex.at(1).toString());

        // Overwrite file on disk
        try (java.io.OutputStreamWriter fs = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(testPath(fname)),
                StandardCharsets.UTF_8)) {
            fs.write("modified\n");
        }

        // Reload should pick up the change
        ex.reload();
        assertEquals("modified", ex.at(1).toString());
        assertEquals(2, ex.finish());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // joinlines tests
    // =================================================================

    /**
     * Join two adjacent lines into one.
     */
    @Test
    void joinLinesBasic() throws IOException {
        String fname = "ju_join2";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();
        assertEquals(5, ex.finish());

        int cursorPos = ex.joinlines(2, 1);
        // "aaa" + " " + "bbb" = "aaa bbb", cursor at start of joined part
        assertEquals("aaa bbb", ex.at(1).toString());
        assertEquals("ccc", ex.at(2).toString());
        assertEquals(4, ex.finish());
        assertEquals(4, cursorPos); // position of space before "bbb"

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    /**
     * Join three lines into one.
     */
    @Test
    void joinLinesMultiple() throws IOException {
        String fname = "ju_join3";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();

        ex.joinlines(3, 1);
        assertEquals("aaa bbb ccc", ex.at(1).toString());
        assertEquals(3, ex.finish()); // sentinel + joined line + empty

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // deletetext / gettext tests
    // =================================================================

    /**
     * Delete a substring within a single line.
     */
    @Test
    void deleteTextSameLine() throws IOException {
        String fname = "ju_deltxt1";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("hello world\n", 0, 1);
        ex.checkpoint();

        String deleted = ex.deletetext(false, 5, 1, 11, 1);
        assertEquals(" world", deleted);
        assertEquals("hello", ex.at(1).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    /**
     * Delete text spanning multiple lines.
     */
    @Test
    void deleteTextMultiLine() throws IOException {
        String fname = "ju_deltxt2";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();
        assertEquals(5, ex.finish());

        // Delete from position 1 of "aaa" to position 2 of "ccc"
        // Should join "a" + "c" and remove "bbb"
        String deleted = ex.deletetext(false, 1, 1, 2, 3);
        assertEquals("ac", ex.at(1).toString());
        // Deleted text: "aa" from line 1, "\nbbb\n", "cc" from line 3
        assertTrue(deleted.startsWith("aa"));
        assertTrue(deleted.endsWith("cc"));

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    /**
     * gettext extracts text without modifying the buffer.
     */
    @Test
    void getTextPreservesContent() throws IOException {
        String fname = "ju_gettxt";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("hello world\n", 0, 1);
        ex.checkpoint();

        String extracted = ex.gettext(0, 1, 5, 1);
        assertEquals("hello", extracted);
        // Buffer should be unchanged
        assertEquals("hello world", ex.at(1).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // changecase tests
    // =================================================================

    /**
     * Toggle letter case on a single line.
     */
    @Test
    void changeCaseToggles() throws IOException {
        String fname = "ju_ccase";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("Hello World\n", 0, 1);
        ex.checkpoint();

        ex.changecase(0, 1, 11, 1);
        assertEquals("hELLO wORLD", ex.at(1).toString());

        // Toggle again — back to original
        ex.changecase(0, 1, 11, 1);
        assertEquals("Hello World", ex.at(1).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // inserttext edge cases
    // =================================================================

    /**
     * Insert text in the middle of an existing line (non-zero x).
     */
    @Test
    void insertTextMiddleOfLine() throws IOException {
        String fname = "ju_ins_mid";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("ac\n", 0, 1);
        ex.checkpoint();
        assertEquals("ac", ex.at(1).toString());

        // Insert "b" at position 1 (between 'a' and 'c')
        ex.inserttext("b", 1, 1);
        assertEquals("abc", ex.at(1).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    /**
     * Insert text with newlines into the middle of a line,
     * which should split the line.
     */
    @Test
    void insertTextWithNewlineSplitsLine() throws IOException {
        String fname = "ju_ins_nl";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("ac\n", 0, 1);
        ex.checkpoint();

        // Insert "X\nY" at position 1 → should become "aX", "Yc"
        ex.inserttext("X\nY", 1, 1);
        assertEquals("aX", ex.at(1).toString());
        assertEquals("Yc", ex.at(2).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // processCommand: move
    // =================================================================

    /**
     * Move command moves a line to a new position.
     */
    @Test
    void processCommandMove() throws Exception {
        String fname = "ju_pcmd_mv";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();

        // 3m1 — move line 3 to after line 1
        int result = ex.processCommand("3m1", 1);
        assertTrue(result >= 0, "move command should succeed");
        assertEquals("aaa", ex.at(1).toString());
        assertEquals("ccc", ex.at(2).toString());
        assertEquals("bbb", ex.at(3).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // processCommand: range substitute with global range
    // =================================================================

    /**
     * Global substitute on all lines: g/pattern/s/old/new/
     */
    @Test
    void processCommandGlobalSubstitute() throws Exception {
        String fname = "ju_pcmd_gsub";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("foo bar\nbaz foo\nqux\n", 0, 1);
        ex.checkpoint();

        // Substitute on all lines matching "foo"
        int result = ex.processCommand("g/foo/s/foo/FOO/", 1);
        assertTrue(result >= 0, "command should succeed");
        assertEquals("FOO bar", ex.at(1).toString());
        assertEquals("baz FOO", ex.at(2).toString());
        assertEquals("qux", ex.at(3).toString()); // unchanged

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // processCommand: inverse global delete (g!/pattern/d)
    // =================================================================

    /**
     * Inverse global delete: remove lines NOT matching a pattern.
     */
    @Test
    void processCommandInverseGlobalDelete() throws Exception {
        String fname = "ju_pcmd_gid";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("keep_this\nremove_me\nkeep_this_too\nremove_also\n",
            0, 1);
        ex.checkpoint();

        // g!/keep/d — delete all lines NOT containing "keep"
        int result = ex.processCommand("g!/keep/d", 1);
        assertTrue(result >= 0, "command should succeed");
        assertEquals("keep_this", ex.at(1).toString());
        assertEquals("keep_this_too", ex.at(2).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // Port of test5 (full): insertOne + persistence across 3 reopens
    // =================================================================

    /**
     * Port of test5 (full): open file with "aaa", insertOne "bbb"
     * before line 1, verify 2-line state, undo/redo with isModified
     * tracking, then persistence across three reopen cycles.
     */
    @Test
    void insertOnePersistenceThreeReopens() throws IOException {
        String fname = "ju_test5f";
        UI.setStream(new StringReader("b\n"));
        deleteTestFiles(fname);

        writeTestFile(fname, "aaa\n");
        TextEdit<String> ex = openTestFile(fname);
        assertEquals("aaa", ex.at(1).toString());

        ex.insertOne("bbb", 1);
        ex.checkpoint();
        assertEquals("bbb", ex.at(1).toString());
        assertEquals("aaa", ex.at(2).toString());
        assertEquals(3, ex.finish());
        assertTrue(ex.isModified());
        ex.undo();
        assertFalse(ex.isModified());
        ex.redo();
        ex.printout();
        ex.disposeFvc();

        // Reopen 1: verify persistence and undo/redo
        ex = openTestFile(fname);
        assertEquals(3, ex.finish());
        assertEquals("bbb", ex.at(1).toString());
        assertEquals("aaa", ex.at(2).toString());
        assertFalse(ex.isModified());
        ex.undo();
        assertTrue(ex.isModified());
        ex.redo();
        ex.printout();
        ex.disposeFvc();

        // Reopen 2: verify stable after second round-trip
        ex = openTestFile(fname);
        assertEquals(3, ex.finish());
        assertEquals("bbb", ex.at(1).toString());
        assertEquals("aaa", ex.at(2).toString());
        ex.printout();
        ex.disposeFvc();

        deleteTestFiles(fname);
    }

    // =================================================================
    // Port of test12 (full): tabfix with CR/LF + double tabfix
    // =================================================================

    /**
     * Port of test12 (full): file uses CR/LF line endings with tabs.
     * First tabfix(3), checkpoint, dispose, reopen. Then undo the
     * first tabfix, apply tabfix(4) to verify re-expansion.
     * Tests that tabfix persists across reopen via .dmp2.
     */
    @Test
    void tabfixCrLfFullRoundTrip() throws Exception {
        String fname = "ju_test12f";
        UI.setStream(new StringReader("bb\n"));
        deleteTestFiles(fname);

        writeTestFile(fname, "\taaaa\r\nbb\r\n");
        TextEdit<String> ex = openTestFile(fname);
        ex.tabfix(3);
        ex.checkpoint();
        assertEquals("   aaaa", ex.at(1).toString());
        ex.disposeFvc();

        // Reopen — tabfix should persist via .dmp2
        ex = openTestFile(fname);
        assertEquals("   aaaa", ex.at(1).toString());
        ex.undo();
        assertEquals("\taaaa", ex.at(1).toString());
        ex.disposeFvc();

        // Reopen and apply tabfix(4) instead
        ex = openTestFile(fname);
        ex.tabfix(4);
        ex.checkpoint();
        assertEquals("    aaaa", ex.at(1).toString());

        ex.undo();
        ex.printout();
        ex.disposeFvc();

        deleteTestFiles(fname);
    }

    // =================================================================
    // Port of test10: file vs mismatched backup (file response)
    // =================================================================

    /**
     * Port of test10: creates a file and a separate backup state,
     * then opens the file where backup doesn't match. User responds
     * 'f' to keep file content, then inserts text and verifies
     * persistence across reopen.
     */
    @Test
    void mismatchedBackupFileResponse() throws IOException {
        String fname = "ju_test10";
        // First create a known backup state from a simple file
        String prereq = "ju_test10_pre";
        UI.setStream(new StringReader(""));
        deleteTestFiles(prereq, fname);

        // Build prerequisite: simple file with one undo checkpoint
        TextEdit<String> ex = openTestFile(prereq);
        ex.inserttext("aaa", 0, 1);
        ex.checkpoint();
        ex.idleSave();
        ex.undo();
        ex.redo();
        ex.printout();
        ex.disposeFvc();

        // Reopen prereq, undo, insert different text, checkpoint, dispose
        ex = openTestFile(prereq);
        ex.undo();
        ex.inserttext("xxx", 0, 1);
        ex.checkpoint();
        ex.disposeFvc();

        // Copy the backup state to test10, but write different file content
        EditTester1.copyFile(testPath(prereq + ".dmp2"),
           testPath(fname + ".dmp2"));

        // test10 logic: file says "aaaa\n\bb\n", backup has "xxx" state
        UI.setStream(new StringReader("f\n"));
        writeTestFile(fname, "aaaa\n");
        ex = openTestFile(fname);
        // Responded 'f' => use file content
        assertEquals("aaaa", ex.at(1).toString());
        ex.undo();
        // After undo from file state, should still be "aaaa"
        assertEquals("aaaa", ex.at(1).toString());
        ex.disposeFvc();

        deleteTestFiles(prereq, fname);
    }

    // =================================================================
    // processCommand: write to external file
    // =================================================================

    /**
     * The ex-mode write command writes a range of lines to a file.
     */
    @Test
    void processCommandWriteToFile() throws Exception {
        String fname = "ju_pcmd_wf";
        String outName = "ju_pcmd_wf_out";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);
        makeLocal(outName).delete();

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();

        // 1,2w <outfile> — write lines 1-2 to external file
        int result = ex.processCommand(
           "1,2w " + testPath(outName), 1);
        assertTrue(result >= 0, "write command should succeed");

        // Verify the output file was created with expected content
        java.io.File outFile = new java.io.File(testPath(outName));
        assertTrue(outFile.exists(), "output file should exist");

        String content;
        try (java.io.BufferedReader br = new java.io.BufferedReader(
              new java.io.InputStreamReader(
                 new java.io.FileInputStream(testPath(outName)),
                 java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null)
                sb.append(line).append('\n');
            content = sb.toString();
        }
        assertTrue(content.contains("aaa"), "output should contain aaa");
        assertTrue(content.contains("bbb"), "output should contain bbb");
        assertFalse(content.contains("ccc"),
           "output should NOT contain ccc (only 1,2 written)");

        ex.disposeFvc();
        deleteTestFiles(fname);
        makeLocal(outName).delete();
    }

    // =================================================================
    // processCommand: copy alias "co"
    // =================================================================

    /**
     * The "co" command is an alias for copy ("t").
     */
    @Test
    void processCommandCopyAlias() throws Exception {
        String fname = "ju_pcmd_co";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();

        // 1co3 — copy line 1 to after line 3
        int result = ex.processCommand("1co3", 1);
        assertTrue(result >= 0, "co command should succeed");
        assertEquals(6, ex.finish());
        assertEquals("aaa", ex.at(1).toString());
        assertEquals("bbb", ex.at(2).toString());
        assertEquals("ccc", ex.at(3).toString());
        assertEquals("aaa", ex.at(4).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // processCommand: dot reference and relative lines
    // =================================================================

    /**
     * A bare "." refers to the current line; ".d" deletes it.
     */
    @Test
    void processCommandDotReference() throws Exception {
        String fname = "ju_pcmd_dot";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();

        // .d with ypos=2 should delete line 2 ("bbb")
        int result = ex.processCommand(".d", 2);
        assertTrue(result >= 0, "dot delete should succeed");
        assertEquals("aaa", ex.at(1).toString());
        assertEquals("ccc", ex.at(2).toString());
        assertEquals(4, ex.finish());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    /**
     * Relative line reference: "+2" from current position.
     */
    @Test
    void processCommandRelativePlus() throws Exception {
        String fname = "ju_pcmd_rel";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
        ex.checkpoint();

        // With ypos=1, ".+2" refers to line 3. "3" moves to that line.
        int result = ex.processCommand(".+2", 1);
        assertEquals(3, result, "dot+2 from ypos=1 should be line 3");

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    /**
     * Semicolon range: first line sets context for second.
     * "2;+1d" — set ypos to 2, then +1 = 3, delete lines 2-3.
     */
    @Test
    void processCommandSemicolonRange() throws Exception {
        String fname = "ju_pcmd_semi";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
        ex.checkpoint();
        assertEquals(6, ex.finish());

        // 2;+1d — start at 2, ; sets ypos=2, +1 means 3; delete 2 to 3
        int result = ex.processCommand("2;+1d", 1);
        assertTrue(result >= 0, "semicolon range delete should succeed");
        assertEquals("aaa", ex.at(1).toString());
        assertEquals("ddd", ex.at(2).toString());
        assertEquals(4, ex.finish());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // processCommand: range move
    // =================================================================

    /**
     * Move a range of lines: "3,4m1" moves lines 3-4 after line 1.
     * (The "before" case with from > to works correctly for ranges.)
     */
    @Test
    void processCommandRangeMove() throws Exception {
        String fname = "ju_pcmd_rmv";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
        ex.checkpoint();
        assertEquals(6, ex.finish());

        // 3,4m1 — move lines 3-4 after line 1
        int result = ex.processCommand("3,4m1", 1);
        assertTrue(result >= 0, "range move should succeed");
        assertEquals("aaa", ex.at(1).toString());
        assertEquals("ccc", ex.at(2).toString());
        assertEquals("ddd", ex.at(3).toString());
        assertEquals("bbb", ex.at(4).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // processCommand: range copy
    // =================================================================

    /**
     * Copy a range of lines: "1,2t4" copies lines 1-2 after line 4.
     */
    @Test
    void processCommandRangeCopy() throws Exception {
        String fname = "ju_pcmd_rcp";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
        ex.checkpoint();
        assertEquals(6, ex.finish());

        // 1,2t4 — copy lines 1-2 after line 4
        int result = ex.processCommand("1,2t4", 1);
        assertTrue(result >= 0, "range copy should succeed");
        assertEquals(8, ex.finish());
        assertEquals("aaa", ex.at(1).toString());
        assertEquals("bbb", ex.at(2).toString());
        assertEquals("ccc", ex.at(3).toString());
        assertEquals("ddd", ex.at(4).toString());
        assertEquals("aaa", ex.at(5).toString());
        assertEquals("bbb", ex.at(6).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // processCommand: global substitute with constrained range
    // =================================================================

    /**
     * Global substitute restricted to a line range.
     */
    @Test
    void processCommandRangeGlobalSub() throws Exception {
        String fname = "ju_pcmd_rgsub";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("Xaa\nYbb\nXcc\nXdd\n", 0, 1);
        ex.checkpoint();

        // 1,3g/X/s/X/Z/ — on lines 1-3, lines matching X, sub X→Z
        int result = ex.processCommand("1,3g/X/s/X/Z/", 1);
        assertTrue(result >= 0, "range global sub should succeed");
        assertEquals("Zaa", ex.at(1).toString());
        assertEquals("Ybb", ex.at(2).toString()); // no X, unchanged
        assertEquals("Zcc", ex.at(3).toString());
        assertEquals("Xdd", ex.at(4).toString()); // line 4 out of range

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // =================================================================
    // Port of test2+test3: sequential persistence chain
    // =================================================================

    /**
     * Port of test2+test3: tests that undo history persists correctly
     * across a file-copy chain. Creates a file (like test1), copies
     * its files to a new name, opens the copy and verifies undo/redo
     * of the inherited history.
     */
   @Test
   void persistenceChainUndoRedo() throws IOException {
      String base = "ju_chain_base";
      String copy1 = "ju_chain_copy1";
      String copy2 = "ju_chain_copy2";
      UI.setStream(new StringReader("b\n"));
      deleteTestFiles(base, copy1, copy2);

      // Step 1: create base file with undo history (like test1)
      TextEdit<String> ex = openTestFile(base);
      assertEquals(2, ex.finish());
      ex.inserttext("aaa", 0, 1);
      ex.checkpoint();
      assertEquals("aaa", ex.at(1).toString());
      ex.idleSave();
      ex.undo();
      assertEquals(0, ex.at(1).toString().length());
      // Insert different text after undo
      ex.inserttext("xxx", 0, 1);
      ex.checkpoint();
      ex.printout();
      ex.disposeFvc();

      // Step 2: copy files to copy1 (like test2 copies from test1)
      UI.setStream(new StringReader("b\n"));
      EditTester1.copyFile(testPath(base), testPath(copy1));
      EditTester1.copyFile(testPath(base + ".dmp2"),
         testPath(copy1 + ".dmp2"));

      TextEdit<String> ex2 = openTestFile(copy1);
      assertEquals("xxx", ex2.at(1).toString());
      ex2.undo();
      assertEquals(0, ex2.at(1).toString().length());
      ex2.redo();
      assertEquals("xxx", ex2.at(1).toString());
      ex2.undo();
      ex2.disposeFvc();

      // Step 3: copy files to copy2 (like test3 copies from test2)
      UI.setStream(new StringReader("b\n"));
      EditTester1.copyFile(testPath(copy1), testPath(copy2));
      EditTester1.copyFile(testPath(copy1 + ".dmp2"),
         testPath(copy2 + ".dmp2"));

      TextEdit<String> ex3 = openTestFile(copy2);
      assertEquals(0, ex3.at(1).toString().length());
      ex3.redo();
      assertEquals("xxx", ex3.at(1).toString());
      ex3.undo();
      ex3.disposeFvc();

      deleteTestFiles(base, copy1, copy2);
   }

    // =================================================================
    // Port of test15: deleted dmp2 mid-session → BadBackupFile
    // =================================================================

    /**
     * Port of test15: deletes the .dmp2 backup file mid-session,
     * then attempts to save (dispose). The persistence layer detects
     * the missing/corrupt backup and throws {@link history.BadBackupFile}.
     *
     * <p>After the exception, reopening the file should recover to
     * the last successfully-saved state.</p>
     */
   @Test
   void deletedDmp2CausesBadBackup() throws IOException {
      String fname = "ju_test15";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\n", 0, 1);
      ex.idleSave();

      // Delete the backup file mid-session
      FileDescriptor.LocalFile dmpFile = makeLocal(fname + ".dmp2");
      dmpFile.delete();

      // Continue editing after dmp2 is gone
      ex.inserttext("b\n", 0, 2);
      ex.inserttext("c\n", 0, 3);
      ex.checkpoint();
      ex.printout();

      // disposeFvc should throw BadBackupFile because the dmp2 was deleted
      assertThrows(history.BadBackupFile.class, () -> ex.disposeFvc());

      // Force GC to let the PersistantStack finalize cleanly
      history.Tools.doGC();

      // Reopen: should recover to the last successfully-saved state
      UI.setStream(new StringReader(""));
      TextEdit<String> ex2 = openTestFile(fname);
      // Only "a" was saved before dmp2 was deleted
      ex2.undo();
      assertEquals("a", ex2.at(1).toString());
      ex2.disposeFvc();

      deleteTestFiles(fname);
   }

    // =================================================================
    // Performance: coarse regression guard (port of perftest)
    // =================================================================

    /**
     * Coarse port of EditTester1.perftest: creates a 20000-line file,
     * runs a global delete command, verifies line count, reopens and
     * checks persistence. Also asserts the operation completes within
     * a generous time bound.
     */
   @Test
   void perftestGlobalDeleteAndReopen() throws Exception {
      String fname = "ju_perftest";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      int tot = 20000;
      int expectedAfterDelete = 13123;
      // generous bound: 2 seconds on any modern machine
      long maxMillis = 2000;

      // Write the test file
      try (OutputStreamWriter fs = new OutputStreamWriter(
            new FileOutputStream(testPath(fname)),
            StandardCharsets.UTF_8)) {
         for (int i = 0; i < tot; i++) {
            fs.write("xxline " + i + '\n');
         }
      }

      history.Tools.doGC();
      long start = System.currentTimeMillis();

      TextEdit<String> ex = openTestFile(fname);
      assertEquals(tot + 1, ex.finish());

      // global delete: remove all lines containing "9"
      ex.processCommand("g/9/d", 1);
      assertEquals(expectedAfterDelete, ex.finish());
      ex.printout();
      ex.disposeFvc();

      // Reopen and verify persistence
      ex = openTestFile(fname);
      assertEquals(expectedAfterDelete, ex.finish());
      ex.disposeFvc();

      long elapsed = System.currentTimeMillis() - start;
      assertTrue(elapsed < maxMillis,
         "perftest should complete in <" + maxMillis
         + "ms, took " + elapsed + "ms");

      deleteTestFiles(fname);
   }

}
