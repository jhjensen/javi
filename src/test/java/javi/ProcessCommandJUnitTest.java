package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link TextEdit#processCommand} — the ex-mode
 * command parser (":" commands in vi).
 *
 * <p>
 * Each test creates a small in-memory file and runs an ex command
 * to verify parsing of line ranges, delete, substitute, and copy/move.
 * </p>
 */
class ProcessCommandJUnitTest {

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
        FileProperties<String> fp = new FileProperties<>(fd, StringIoc.converter);
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

    // --- processCommand: delete ---

    @Test
    void deleteCommandRemovesSingleLine() throws Exception {
        String fname = "ju_pcmd_del1";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();
        assertEquals("aaa", ex.at(1).toString());
        assertEquals("bbb", ex.at(2).toString());
        assertEquals("ccc", ex.at(3).toString());
        assertEquals(5, ex.finish());

        // Delete line 2 ("bbb")
        int result = ex.processCommand("2d", 1);
        assertTrue(result >= 0, "processCommand should succeed");
        assertEquals("aaa", ex.at(1).toString());
        assertEquals("ccc", ex.at(2).toString());
        assertEquals(4, ex.finish());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    @Test
    void deleteCommandRemovesLineRange() throws Exception {
        String fname = "ju_pcmd_del2";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\nddd\n", 0, 1);
        ex.checkpoint();
        assertEquals(6, ex.finish());

        // Delete lines 2-3
        int result = ex.processCommand("2,3d", 1);
        assertTrue(result >= 0, "processCommand should succeed");
        assertEquals("aaa", ex.at(1).toString());
        assertEquals("ddd", ex.at(2).toString());
        assertEquals(4, ex.finish());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    @Test
    void percentDeleteRemovesAllLines() throws Exception {
        String fname = "ju_pcmd_delall";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();
        assertEquals(5, ex.finish());

        // %d deletes all lines
        int result = ex.processCommand("%d", 1);
        assertTrue(result >= 0, "processCommand should succeed");
        // After deleting all content, sentinel line remains
        assertEquals(2, ex.finish());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // --- processCommand: substitute ---

    @Test
    void substituteReplacesFirstOccurrence() throws Exception {
        String fname = "ju_pcmd_sub1";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("hello world\n", 0, 1);
        ex.checkpoint();
        assertEquals("hello world", ex.at(1).toString());

        // s/hello/goodbye/
        int result = ex.processCommand("1s/hello/goodbye/", 1);
        assertTrue(result >= 0, "processCommand should succeed");
        assertEquals("goodbye world", ex.at(1).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    @Test
    void substituteGlobalReplacesAllOccurrences() throws Exception {
        String fname = "ju_pcmd_subg";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aXbXcX\n", 0, 1);
        ex.checkpoint();
        assertEquals("aXbXcX", ex.at(1).toString());

        // s/X/Y/g — replace all X with Y on line 1
        int result = ex.processCommand("1s/X/Y/g", 1);
        assertTrue(result >= 0, "processCommand should succeed");
        assertEquals("aYbYcY", ex.at(1).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // --- processCommand: global delete ---

    @Test
    void globalDeleteRemovesMatchingLines() throws Exception {
        String fname = "ju_pcmd_gdel";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("line1\nline2\nline3\nline2dup\n", 0, 1);
        ex.checkpoint();
        assertEquals(6, ex.finish());

        // g/2/d — delete all lines containing "2"
        int result = ex.processCommand("g/2/d", 1);
        assertTrue(result >= 0, "processCommand should succeed");
        // Should have "line1" and "line3" remaining
        assertEquals("line1", ex.at(1).toString());
        assertEquals("line3", ex.at(2).toString());
        assertEquals(4, ex.finish());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // --- processCommand: line navigation ---

    @Test
    void lineNumberOnlyMovesToLine() throws Exception {
        String fname = "ju_pcmd_nav";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();

        // Just a line number should return that line
        int result = ex.processCommand("3", 1);
        assertEquals(3, result);

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    @Test
    void dollarSignRefersToLastLine() throws Exception {
        String fname = "ju_pcmd_dollar";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();

        // $ should return the last content line number (finish()-1)
        int result = ex.processCommand("$", 1);
        assertEquals(4, result);

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // --- processCommand: unknown/invalid ---

    @Test
    void unknownCommandReturnsMinusOne() throws Exception {
        String fname = "ju_pcmd_unk";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\n", 0, 1);
        ex.checkpoint();

        // 'z' is not a recognized ex command
        int result = ex.processCommand("z", 1);
        assertEquals(-1, result, "unknown command should return -1");

        ex.disposeFvc();
        deleteTestFiles(fname);
    }

    // --- processCommand: copy ---

    @Test
    void copyCommandDuplicatesLine() throws Exception {
        String fname = "ju_pcmd_copy";
        UI.setStream(new StringReader(""));
        deleteTestFiles(fname);

        TextEdit<String> ex = openTestFile(fname);
        ex.inserttext("aaa\nbbb\nccc\n", 0, 1);
        ex.checkpoint();
        assertEquals(5, ex.finish());

        // 1t3 — copy line 1 to after line 3
        int result = ex.processCommand("1t3", 1);
        assertTrue(result >= 0, "copy command should succeed");
        assertEquals(6, ex.finish());
        assertEquals("aaa", ex.at(1).toString());
        assertEquals("bbb", ex.at(2).toString());
        assertEquals("ccc", ex.at(3).toString());
        assertEquals("aaa", ex.at(4).toString());

        ex.disposeFvc();
        deleteTestFiles(fname);
    }
}
