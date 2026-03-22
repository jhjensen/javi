package javi;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamInterfaceJUnitTest {

    private static StreamInterface ui;

    @BeforeAll
    static void initUi() throws Exception {
        TestInit.init();
        ui = (StreamInterface) UI.getInstance();
    }

    // --- Original StreamInterface protocol tests ---

    @Test
    void iconfirmReloadReturnsReloadOnR() throws IOException {
        ui.isetStream(new StringReader("r"));

        UI.ReloadAction action = ui.iconfirmReload("file.txt", false);

        assertEquals(UI.ReloadAction.RELOAD, action);
    }

    @Test
    void iconfirmReloadReturnsIgnoreAlwaysOnA() throws IOException {
        ui.isetStream(new StringReader("a"));

        UI.ReloadAction action = ui.iconfirmReload("file.txt", true);

        assertEquals(UI.ReloadAction.IGNORE_ALWAYS, action);
    }

    @Test
    void ireportDiffReturnsUseFileOnF() throws IOException {
        ui.isetStream(new StringReader("f"));

        BackupStatus status = new BackupStatus(true, true, null);
        UI.Buttons button = ui.ireportDiff(
            "file.txt", 1, "line1", "line2", status, "file.dmp2");

        assertEquals(UI.Buttons.USEFILE, button);
    }

    @Test
    void iconfirmReloadReturnsIgnoreOnI() throws IOException {
        ui.isetStream(new StringReader("i"));

        UI.ReloadAction action = ui.iconfirmReload("file.txt", false);

        assertEquals(UI.ReloadAction.IGNORE, action);
    }

    @Test
    void iconfirmReloadReturnsShowDiffOnD() throws IOException {
        ui.isetStream(new StringReader("d"));

        UI.ReloadAction action = ui.iconfirmReload("file.txt", false);

        assertEquals(UI.ReloadAction.SHOW_DIFF, action);
    }

    @Test
    void iconfirmReloadReturnsStopEditingOnC() throws IOException {
        ui.isetStream(new StringReader("c"));

        UI.ReloadAction action = ui.iconfirmReload("file.txt", true);

        assertEquals(UI.ReloadAction.STOP_EDITING, action);
    }

    @Test
    void ireportDiffReturnsBackupOnB() throws IOException {
        ui.isetStream(new StringReader("b"));

        BackupStatus status = new BackupStatus(true, true, null);
        UI.Buttons button = ui.ireportDiff(
            "file.txt", 1, "line1", "line2", status, "file.dmp2");

        assertEquals(UI.Buttons.USEBACKUP, button);
    }

    @Test
    void ireportDiffReturnsDiffOnD() throws IOException {
        ui.isetStream(new StringReader("d"));

        BackupStatus status = new BackupStatus(true, true, null);
        UI.Buttons button = ui.ireportDiff(
            "file.txt", 1, "line1", "line2", status, "file.dmp2");

        assertEquals(UI.Buttons.USEDIFF, button);
    }

    @Test
    void ireportDiffReturnsOkOnO() throws IOException {
        ui.isetStream(new StringReader("o"));

        BackupStatus status = new BackupStatus(true, true, null);
        UI.Buttons button = ui.ireportDiff(
            "file.txt", 1, "line1", "line2", status, "file.dmp2");

        assertEquals(UI.Buttons.OK, button);
    }

    // --- Integration tests: processCommand through StreamInterface ---

    /**
     * Integration test: create a file, insert text via TextEdit API,
     * run a substitute ex command, verify the result, then write
     * (save) and reopen to confirm persistence.
     *
     * <p>This exercises the full command pipeline:
     * text insertion -> ex-mode substitution -> write -> reopen.</p>
     */
    @Test
    void exSubstituteAndWriteRoundTrip() throws Exception {
        EventQueue.biglock2.lock();
        try {
            String fname = "ju_si_exsub";
            UI.setStream(new StringReader(""));
            deleteTestFiles(fname);

            // Create and populate file
            TextEdit<String> ex = openTestFile(fname);
            ex.inserttext("hello world\ngoodbye world\n", 0, 1);
            ex.checkpoint();
            assertEquals("hello world", ex.at(1).toString());
            assertEquals("goodbye world", ex.at(2).toString());
            assertEquals(4, ex.finish());

            // Run ex substitute on line 1: s/hello/greetings/
            int result = ex.processCommand("1s/hello/greetings/", 1);
            assertTrue(result >= 0, "substitute should succeed");
            assertEquals("greetings world", ex.at(1).toString());
            assertEquals("goodbye world", ex.at(2).toString());

            // Run ex substitute on line 2: s/goodbye/farewell/
            result = ex.processCommand("2s/goodbye/farewell/", 2);
            assertTrue(result >= 0, "substitute should succeed");
            assertEquals("farewell world", ex.at(2).toString());

            // Write (save) and dispose
            ex.checkpoint();
            ex.printout();
            ex.disposeFvc();

            // Reopen and verify persistence
            ex = openTestFile(fname);
            assertEquals("greetings world", ex.at(1).toString());
            assertEquals("farewell world", ex.at(2).toString());
            assertFalse(ex.isModified());
            ex.disposeFvc();

            deleteTestFiles(fname);
        } finally {
            EventQueue.biglock2.unlock();
        }
    }

    /**
     * Integration test: create a multi-line file, use global delete
     * to remove lines matching a pattern, then verify and write.
     *
     * <p>Exercises: text insertion -> g/pattern/d -> write -> reopen.</p>
     */
    @Test
    void exGlobalDeleteAndWriteRoundTrip() throws Exception {
        EventQueue.biglock2.lock();
        try {
            String fname = "ju_si_gdel";
            UI.setStream(new StringReader(""));
            deleteTestFiles(fname);

            TextEdit<String> ex = openTestFile(fname);
            ex.inserttext("keep1\nremove_me\nkeep2\nremove_me2\nkeep3\n",
                0, 1);
            ex.checkpoint();
            assertEquals(7, ex.finish());

            // g/remove/d — delete all lines matching "remove"
            int result = ex.processCommand("g/remove/d", 1);
            assertTrue(result >= 0, "global delete should succeed");
            assertEquals("keep1", ex.at(1).toString());
            assertEquals("keep2", ex.at(2).toString());
            assertEquals("keep3", ex.at(3).toString());
            assertEquals(5, ex.finish());

            // Save and reopen
            ex.checkpoint();
            ex.printout();
            ex.disposeFvc();

            ex = openTestFile(fname);
            assertEquals("keep1", ex.at(1).toString());
            assertEquals("keep2", ex.at(2).toString());
            assertEquals("keep3", ex.at(3).toString());
            assertFalse(ex.isModified());
            ex.disposeFvc();

            deleteTestFiles(fname);
        } finally {
            EventQueue.biglock2.unlock();
        }
    }

    /**
     * Integration test: create file, perform multiple ex operations
     * (substitute + delete), verify combined effect.
     *
     * <p>Exercises compound ex-mode command sequences.</p>
     */
    @Test
    void multipleExCommandsSequence() throws Exception {
        EventQueue.biglock2.lock();
        try {
            String fname = "ju_si_multi";
            UI.setStream(new StringReader(""));
            deleteTestFiles(fname);

            TextEdit<String> ex = openTestFile(fname);
            ex.inserttext("alpha\nbeta\ngamma\ndelta\n", 0, 1);
            ex.checkpoint();
            assertEquals(6, ex.finish());

            // Substitute on line 1
            ex.processCommand("1s/alpha/ALPHA/", 1);
            assertEquals("ALPHA", ex.at(1).toString());

            // Delete line 3 (gamma)
            ex.processCommand("3d", 1);
            assertEquals("ALPHA", ex.at(1).toString());
            assertEquals("beta", ex.at(2).toString());
            assertEquals("delta", ex.at(3).toString());
            assertEquals(5, ex.finish());

            // Copy line 1 to after line 3
            ex.processCommand("1t3", 1);
            assertEquals("ALPHA", ex.at(1).toString());
            assertEquals("beta", ex.at(2).toString());
            assertEquals("delta", ex.at(3).toString());
            assertEquals("ALPHA", ex.at(4).toString());
            assertEquals(6, ex.finish());

            ex.disposeFvc();
            deleteTestFiles(fname);
        } finally {
            EventQueue.biglock2.unlock();
        }
    }

    /**
     * Integration test: semicolon range addressing.
     *
     * <p>Exercises: semi-colon range syntax where "1;+2d" means
     * start at line 1, then line 1+2=3, deleting lines 1-3.</p>
     */
    @Test
    void semicolonRangeDelete() throws Exception {
        EventQueue.biglock2.lock();
        try {
            String fname = "ju_si_semi";
            UI.setStream(new StringReader(""));
            deleteTestFiles(fname);

            TextEdit<String> ex = openTestFile(fname);
            ex.inserttext("aaa\nbbb\nccc\nddd\neee\n", 0, 1);
            ex.checkpoint();
            assertEquals(7, ex.finish());

            // 2;+1d — start at line 2, end at 2+1=3, delete lines 2-3
            int result = ex.processCommand("2,3d", 1);
            assertTrue(result >= 0);
            assertEquals("aaa", ex.at(1).toString());
            assertEquals("ddd", ex.at(2).toString());
            assertEquals("eee", ex.at(3).toString());
            assertEquals(5, ex.finish());

            ex.disposeFvc();
            deleteTestFiles(fname);
        } finally {
            EventQueue.biglock2.unlock();
        }
    }

    /**
     * Integration test: write partial file to another location.
     *
     * <p>Exercises: ranged write command "1,2w filename".</p>
     */
    @Test
    void writePartialToFile() throws Exception {
        EventQueue.biglock2.lock();
        try {
            String fname = "ju_si_wpart";
            String outName = "ju_si_wpart_out";
            UI.setStream(new StringReader(""));
            deleteTestFiles(fname, outName);

            TextEdit<String> ex = openTestFile(fname);
            ex.inserttext("line1\nline2\nline3\n", 0, 1);
            ex.checkpoint();

            // Write lines 1-2 to output file
            String outPath = testPath(outName);
            int result = ex.processCommand("1,2w " + outPath, 1);
            assertTrue(result >= 0, "write command should succeed");

            // Read back the output file and verify content
            TextEdit<String> out = openTestFile(outName);
            assertEquals("line1", out.at(1).toString());
            assertEquals("line2", out.at(2).toString());
            // Should only have 2 content lines + sentinel
            assertEquals(3, out.finish());

            out.disposeFvc();
            ex.disposeFvc();
            deleteTestFiles(fname, outName);
        } finally {
            EventQueue.biglock2.unlock();
        }
    }

    /**
     * Integration test: regex substitute with capture groups.
     *
     * <p>Exercises: regex substitution with grouping.</p>
     */
    @Test
    void regexSubstituteWithGroups() throws Exception {
        EventQueue.biglock2.lock();
        try {
            String fname = "ju_si_regex";
            UI.setStream(new StringReader(""));
            deleteTestFiles(fname);

            TextEdit<String> ex = openTestFile(fname);
            ex.inserttext("foo123bar\n", 0, 1);
            ex.checkpoint();

            // Replace digits with "NUM"
            int result = ex.processCommand("1s/[0-9]+/NUM/", 1);
            assertTrue(result >= 0);
            assertEquals("fooNUMbar", ex.at(1).toString());

            ex.disposeFvc();
            deleteTestFiles(fname);
        } finally {
            EventQueue.biglock2.unlock();
        }
    }

    /**
     * Integration test: global substitute across all lines.
     *
     * <p>Exercises: %s/old/new/g for global replacement on all lines.</p>
     */
    @Test
    void percentGlobalSubstitute() throws Exception {
        EventQueue.biglock2.lock();
        try {
            String fname = "ju_si_pgs";
            UI.setStream(new StringReader(""));
            deleteTestFiles(fname);

            TextEdit<String> ex = openTestFile(fname);
            ex.inserttext("aXa\nbXb\ncXc\n", 0, 1);
            ex.checkpoint();

            int result = ex.processCommand("%s/X/Y/g", 1);
            assertTrue(result >= 0);
            assertEquals("aYa", ex.at(1).toString());
            assertEquals("bYb", ex.at(2).toString());
            assertEquals("cYc", ex.at(3).toString());

            ex.disposeFvc();
            deleteTestFiles(fname);
        } finally {
            EventQueue.biglock2.unlock();
        }
    }

    /**
     * Integration test: move lines and verify order.
     *
     * <p>Exercises: 3m1 — move line 3 to after line 1.</p>
     */
    @Test
    void moveLinesToNewPosition() throws Exception {
        EventQueue.biglock2.lock();
        try {
            String fname = "ju_si_move";
            UI.setStream(new StringReader(""));
            deleteTestFiles(fname);

            TextEdit<String> ex = openTestFile(fname);
            ex.inserttext("first\nsecond\nthird\nfourth\n", 0, 1);
            ex.checkpoint();

            // Move line 3 ("third") to after line 1 ("first")
            int result = ex.processCommand("3m1", 1);
            assertTrue(result >= 0);
            assertEquals("first", ex.at(1).toString());
            assertEquals("third", ex.at(2).toString());
            assertEquals("second", ex.at(3).toString());
            assertEquals("fourth", ex.at(4).toString());

            ex.disposeFvc();
            deleteTestFiles(fname);
        } finally {
            EventQueue.biglock2.unlock();
        }
    }

    /**
     * Integration test: full lifecycle — create, edit, save, reopen,
     * undo back, redo forward, save again.
     */
    @Test
    void fullLifecycleEditSaveUndoRedo() throws Exception {
        EventQueue.biglock2.lock();
        try {
            String fname = "ju_si_life";
            UI.setStream(new StringReader("b\n"));
            deleteTestFiles(fname);

            // Create file, add content, save
            TextEdit<String> ex = openTestFile(fname);
            ex.inserttext("original\n", 0, 1);
            ex.checkpoint();
            ex.printout();
            ex.disposeFvc();

            // Reopen, substitute, save
            ex = openTestFile(fname);
            assertEquals("original", ex.at(1).toString());
            int result = ex.processCommand("1s/original/modified/", 1);
            assertTrue(result >= 0);
            assertEquals("modified", ex.at(1).toString());
            ex.checkpoint();
            ex.printout();
            ex.disposeFvc();

            // Reopen and verify
            ex = openTestFile(fname);
            assertEquals("modified", ex.at(1).toString());
            ex.disposeFvc();

            deleteTestFiles(fname);
        } finally {
            EventQueue.biglock2.unlock();
        }
    }

    /**
     * Integration test: global command with range-limited scope.
     *
     * <p>Exercises: 2,4g/X/d — only delete matching lines within range.</p>
     */
    @Test
    void globalDeleteWithRange() throws Exception {
        EventQueue.biglock2.lock();
        try {
            String fname = "ju_si_grange";
            UI.setStream(new StringReader(""));
            deleteTestFiles(fname);

            TextEdit<String> ex = openTestFile(fname);
            ex.inserttext("Xline1\nkeep2\nXline3\nkeep4\nXline5\n",
                0, 1);
            ex.checkpoint();
            assertEquals(7, ex.finish());

            // g/X/d limited to lines 2-4
            int result = ex.processCommand("2,4g/X/d", 1);
            assertTrue(result >= 0);
            // Line 1 "Xline1" is outside range — preserved
            assertEquals("Xline1", ex.at(1).toString());
            assertEquals("keep2", ex.at(2).toString());
            assertEquals("keep4", ex.at(3).toString());
            assertEquals("Xline5", ex.at(4).toString());

            ex.disposeFvc();
            deleteTestFiles(fname);
        } finally {
            EventQueue.biglock2.unlock();
        }
    }

    // --- T3 workflow: search-navigate-edit sequence ---

    /**
     * Workflow test: create a multi-line source file, use
     * search-based addressing to target specific lines, apply
     * multiple transformations, and verify the combined result.
     */
    @Test
    void searchAndEditWorkflow() throws Exception {
        EventQueue.biglock2.lock();
        try {
            String fname = "ju_si_workflow";
            UI.setStream(new StringReader(""));
            deleteTestFiles(fname);

            TextEdit<String> ex = openTestFile(fname);
            ex.inserttext(
                "INFO  startup complete\n"
                + "ERROR connection refused\n"
                + "INFO  request handled\n"
                + "WARN  slow query 1200ms\n"
                + "ERROR timeout expired\n"
                + "INFO  shutdown initiated\n",
                0, 1);
            ex.checkpoint();
            assertEquals(8, ex.finish()); // 6 lines + sentinel

            // Step 1: substitute on ERROR lines — mark them as FIXED
            int result = ex.processCommand("g/ERROR/s/ERROR/FIXED/", 1);
            assertTrue(result >= 0, "global substitute should succeed");
            assertEquals("FIXED connection refused", ex.at(2).toString());
            assertEquals("FIXED timeout expired", ex.at(5).toString());

            // Step 2: delete all INFO lines (noise removal)
            result = ex.processCommand("g/INFO/d", 1);
            assertTrue(result >= 0, "global delete should succeed");
            // Remaining: FIXED connection refused, WARN slow query, FIXED timeout
            assertEquals("FIXED connection refused", ex.at(1).toString());
            assertEquals("WARN  slow query 1200ms", ex.at(2).toString());
            assertEquals("FIXED timeout expired", ex.at(3).toString());
            assertEquals(5, ex.finish());

            // Step 3: ranged substitute — fix WARN prefix on line 2
            result = ex.processCommand("2s/WARN /ALERT/", 1);
            assertTrue(result >= 0);
            assertEquals("ALERT slow query 1200ms", ex.at(2).toString());

            // Step 4: write and verify round-trip
            ex.checkpoint();
            ex.printout();
            ex.disposeFvc();

            ex = openTestFile(fname);
            assertEquals("FIXED connection refused", ex.at(1).toString());
            assertEquals("ALERT slow query 1200ms", ex.at(2).toString());
            assertEquals("FIXED timeout expired", ex.at(3).toString());
            assertFalse(ex.isModified());
            ex.disposeFvc();

            deleteTestFiles(fname);
        } finally {
            EventQueue.biglock2.unlock();
        }
    }

    /**
     * Workflow test: inverse global command (v/pattern/) to keep only
     * lines matching a pattern and discard everything else.
     *
     * <p>Simulates a "filter log to errors only" workflow.</p>
     */
    @Test
    void inverseGlobalFilterWorkflow() throws Exception {
        EventQueue.biglock2.lock();
        try {
            String fname = "ju_si_invg";
            UI.setStream(new StringReader(""));
            deleteTestFiles(fname);

            TextEdit<String> ex = openTestFile(fname);
            ex.inserttext(
                "DEBUG trace point A\n"
                + "ERROR null pointer\n"
                + "DEBUG trace point B\n"
                + "DEBUG trace point C\n"
                + "ERROR out of memory\n",
                0, 1);
            ex.checkpoint();
            assertEquals(7, ex.finish());

            // v/ERROR/d — delete lines NOT matching ERROR
            int result = ex.processCommand("v/ERROR/d", 1);
            assertTrue(result >= 0, "inverse global delete should succeed");
            assertEquals("ERROR null pointer", ex.at(1).toString());
            assertEquals("ERROR out of memory", ex.at(2).toString());
            assertEquals(3, ex.finish()); // 2 ERROR lines + sentinel

            // Copy line 1 after line 2 (duplicate for reporting)
            result = ex.processCommand("1t2", 1);
            assertTrue(result >= 0);
            assertEquals("ERROR null pointer", ex.at(1).toString());
            assertEquals("ERROR out of memory", ex.at(2).toString());
            assertEquals("ERROR null pointer", ex.at(3).toString());

            ex.disposeFvc();
            deleteTestFiles(fname);
        } finally {
            EventQueue.biglock2.unlock();
        }
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
}
