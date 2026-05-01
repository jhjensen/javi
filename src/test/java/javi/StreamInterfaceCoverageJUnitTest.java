package javi;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional coverage tests for {@link StreamInterface}.
 *
 * <p>Exercises:</p>
 * <ul>
 *   <li>{@code ireportBadBackup} — both branches and unknown-char loop.</li>
 *   <li>{@code iconfirmReload} — EOF (-1) IGNORE branch and
 *       unknown-char loop.</li>
 *   <li>{@code ireportDiff} — EOF (-1) IOERROR branch, unknown-char
 *       loop, the three message-formatting branches (both versions
 *       null, only filevers null, only backupvers null), and the
 *       error-status branches (FileLockException vs generic, plus
 *       {@code !cleanQuit && !isQuitAtEnd}).</li>
 * </ul>
 */
class StreamInterfaceCoverageJUnitTest {

   private static StreamInterface ui;

   @BeforeAll
   static void initUi() throws Exception {
      TestInit.init();
      ui = (StreamInterface) UI.getInstance();
   }

   // ── ireportBadBackup ──────────────────────────────────────

   @Test
   void ireportBadBackupReturnsTrueOnD() throws IOException {
      ui.isetStream(new StringReader("d"));
      boolean result = ui.ireportBadBackup(
         "f.txt", new history.BadBackupFile("corrupt"));
      assertTrue(result, "'d' returns true (delete)");
   }

   @Test
   void ireportBadBackupReturnsFalseOnI() throws IOException {
      ui.isetStream(new StringReader("i"));
      boolean result = ui.ireportBadBackup(
         "f.txt", new history.BadBackupFile("corrupt"));
      assertFalse(result, "'i' returns false (ignore)");
   }

   @Test
   void ireportBadBackupSkipsUnknownChars() throws IOException {
      // 'x' and 'q' hit the default branch; 'd' terminates.
      ui.isetStream(new StringReader("xqd"));
      boolean result = ui.ireportBadBackup(
         "f.txt", new history.BadBackupFile("corrupt"));
      assertTrue(result,
         "unknown chars skipped, then 'd' returns true");
   }

   // ── iconfirmReload extra branches ─────────────────────────

   @Test
   void iconfirmReloadReturnsIgnoreOnEof() throws IOException {
      // Empty stream -> read() returns -1 -> IGNORE branch.
      ui.isetStream(new StringReader(""));
      UI.ReloadAction action =
         ui.iconfirmReload("file.txt", false);
      assertEquals(UI.ReloadAction.IGNORE, action);
   }

   @Test
   void iconfirmReloadSkipsUnknownChars() throws IOException {
      // 'z' and '?' default-branch; 'r' terminates.
      ui.isetStream(new StringReader("z?r"));
      UI.ReloadAction action =
         ui.iconfirmReload("file.txt", false);
      assertEquals(UI.ReloadAction.RELOAD, action);
   }

   // ── ireportDiff extra branches ────────────────────────────

   @Test
   void ireportDiffReturnsIoErrorOnEof() throws IOException {
      ui.isetStream(new StringReader(""));
      BackupStatus status = new BackupStatus(true, true, null);
      UI.Buttons button = ui.ireportDiff(
         "file.txt", 1, "line1", "line2", status, "f.dmp2");
      assertEquals(UI.Buttons.IOERROR, button);
   }

   @Test
   void ireportDiffWithExtraFileLines() throws IOException {
      // backupvers == null, filevers != null branch
      ui.isetStream(new StringReader("o"));
      BackupStatus status = new BackupStatus(true, true, null);
      UI.Buttons button = ui.ireportDiff(
         "f.txt", 1, "extra-file-lines", null, status, "f.dmp2");
      assertEquals(UI.Buttons.OK, button);
   }

   @Test
   void ireportDiffWithExtraBackupLines() throws IOException {
      // filevers == null, backupvers != null branch
      ui.isetStream(new StringReader("o"));
      BackupStatus status = new BackupStatus(true, true, null);
      UI.Buttons button = ui.ireportDiff(
         "f.txt", 1, null, "extra-backup-lines", status, "f.dmp2");
      assertEquals(UI.Buttons.OK, button);
   }

   @Test
   void ireportDiffWithBothNullVersions() throws IOException {
      // "written versions are consistent" branch
      ui.isetStream(new StringReader("o"));
      BackupStatus status = new BackupStatus(true, true, null);
      UI.Buttons button = ui.ireportDiff(
         "f.txt", 1, null, null, status, "f.dmp2");
      assertEquals(UI.Buttons.OK, button);
   }

   @Test
   void ireportDiffWithFileLockException() throws IOException {
      // FileLockException branch in error formatting
      ui.isetStream(new StringReader("o"));
      BackupStatus status = new BackupStatus(true, true,
         new history.FileLockException("locked"));
      UI.Buttons button = ui.ireportDiff(
         "f.txt", 1, "v1", "v2", status, "f.dmp2");
      assertEquals(UI.Buttons.OK, button);
   }

   @Test
   void ireportDiffWithGenericError() throws IOException {
      // Generic (non-FileLockException) error branch
      ui.isetStream(new StringReader("o"));
      BackupStatus status = new BackupStatus(true, true,
         new IOException("disk read failed"));
      UI.Buttons button = ui.ireportDiff(
         "f.txt", 1, "v1", "v2", status, "f.dmp2");
      assertEquals(UI.Buttons.OK, button);
   }

   @Test
   void ireportDiffWithUncleanQuit() throws IOException {
      // !cleanQuit && !isQuitAtEnd branches in status formatting
      ui.isetStream(new StringReader("o"));
      BackupStatus status = new BackupStatus(false, false, null);
      UI.Buttons button = ui.ireportDiff(
         "f.txt", 1, "v1", "v2", status, "f.dmp2");
      assertEquals(UI.Buttons.OK, button);
   }
}
