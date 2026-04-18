package javi.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javi.Command;
import javi.ContextHelp;
import javi.DirEdit;
import javi.EditContainer;
import javi.EventQueue;
import javi.FileList;
import javi.FvContext;
import javi.InputException;
import javi.Plugin;
import javi.Rgroup;
import javi.StringIoc;
import javi.TextEdit;
import javi.UI;
import javi.View;

/**
 * Rgroup subclass providing git commands for the editor.
 *
 * <p>Registers commands: git_status, git_stage, git_unstage,
 * git_commit, git_diff, git_log, git_branch. These are invoked
 * via colon commands like {@code :git_status}.</p>
 *
 * <p>Implements {@link Plugin} so git integration loads through
 * the plugin mechanism rather than direct instantiation.</p>
 *
 * @see GitProcess
 * @see GitStatusBuffer
 */
public final class GitCommands extends Rgroup implements Plugin {

   /** Plugin descriptor for the plugin loader. */
   public static final String pluginInfo = "git integration commands";

   static {
      new GitCommands();
   }

   /** The git status buffer, reused across invocations. */
   private static TextEdit<String> statusBuffer;

   /** The git output buffer for diff/log/branch results. */
   static TextEdit<String> outputBuffer;


   /** Whether the file-write listener has been registered. */
   private static boolean listenerRegistered;










   /** Parsed hunks for the current patch buffer. */
   private static List<GitHunkStaging.Hunk> patchHunks;

   /** Raw diff lines for the current patch buffer. */
   private static List<String> patchDiffLines;

   /** Parsed hunks for the commit view buffer. */
   private static List<GitHunkStaging.Hunk> commitViewHunks;

   /** Whether the current commit view is in amend mode. */
   private static boolean commitAmendMode;

   /** Root path of the repo for the current commit session. */
   private static String commitRepoRoot;

   /** The commit message buffer for split-view commit workflow. */
   private static TextEdit<String> commitMsgBuffer;

   /** The staging buffer for split-view commit workflow. */
   private static TextEdit<String> commitStagingBuffer;

   /** The view displaying the staging buffer (right pane). */
   private static View commitStagingView;

   /**
    * Returns the directory associated with a git log buffer, or null.
    *
    * @param bufferName the buffer's short name (e.g. "*git-log:javi*")
    * @return the directory, or null if not a git log buffer
    */
   public static java.io.File getBufferDir(String bufferName) {
      return GitLogHelper.logBufferDirs.get(bufferName);
   }

   public GitCommands() {
      final String[] rnames = {
         "",
         "git_status",
         "git_stage",
         "git_unstage",
         "git_commit",
         "git_diff",
         "git_log",
         "git_branch",
         "git_do_commit",
         "git_branch_create",
         "git_branch_switch",
         "git_stage_line",
         "git_unstage_line",
         "git_toggle",
         "git_discard",
         "git_refresh",
         "git_stash",
         "git_stash_pop",
         "git_stash_list",
         "git_merge",
         "git_fetch",
         "git_pull",
         "git_push",
         "git_branch_delete",
         "git_rebase",
         "git",
         "git_show",
         "git_log_diff",
         "git_expand",
         "git_expand_all",
         "git_collapse_all",
         "git_blame",
         "git_stage_hunk",
         "git_unstage_hunk",
         "git_patch",
         "git_goto_file",
         "git_amend",
         "git_commit_menu",
         "git_commit_quit",
         "git_commit_finalize",
      };
      final String[] descs = {
         "",
         "Show git status buffer",
         "Stage a file (git add)",
         "Unstage a file (git restore --staged)",
         "Open split-view commit editor",
         "Show diff output",
         "Show git log with graph",
         "Show branch list",
         "Finalize commit from message buffer",
         "Create a new branch",
         "Switch to a branch",
         "Stage file at cursor",
         "Unstage file at cursor",
         "Toggle stage/unstage at cursor",
         "Discard unstaged changes at cursor",
         "Refresh git status or staging view",
         "Stash working directory changes",
         "Pop top stash entry",
         "Show stash list",
         "Merge a branch",
         "Fetch from remote",
         "Pull from remote (fetch + merge)",
         "Push to remote",
         "Delete a branch",
         "Rebase onto a branch",
         "Git dispatcher (:git <subcmd>)",
         "Show full commit details",
         "Show diff for commit at cursor",
         "Toggle fold / expand diff",
         "Expand all folds and diffs",
         "Collapse all folds",
         "Show per-line blame annotations",
         "Stage diff hunk at cursor",
         "Unstage diff hunk at cursor",
         "Show annotated patch for file at cursor",
         "Open file from diff context (^])",
         "Amend previous commit",
         "Commit sub-menu (c=commit a=amend)",
         "Quit commit view with save prompt",
         "ZZ to finalize commit",
      };
      register(rnames, descs);
   }

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws IOException, InputException {
      java.io.File dir = getFileDir(fvc);
      if (!GitProcess.isGitRepo(dir)) {
         UI.reportMessage("Not a git repository");
         return null;
      }
      switch (rnum) {
         case 1:
            gitStatus(fvc);
            return null;
         case 2:
            gitStage(arg, fvc);
            return null;
         case 3:
            gitUnstage(arg, fvc);
            return null;
         case 4:
            gitCommit(fvc);
            return null;
         case 5:
            gitDiff(arg, fvc);
            return null;
         case 6:
            GitLogHelper.gitLog(arg, fvc);
            return null;
         case 7:
            gitBranch(fvc);
            return null;
         case 8:
            gitDoCommit(fvc);
            return null;
         case 9:
            gitBranchCreate(arg, fvc);
            return null;
         case 10:
            gitBranchSwitch(arg, fvc);
            return null;
         case 11:
            gitStageLine(fvc);
            return null;
         case 12:
            gitUnstageLine(fvc);
            return null;
         case 13:
            gitToggle(fvc);
            return null;
         case 14:
            gitDiscard(fvc);
            return null;
         case 15:
            gitRefresh(fvc);
            return null;
         case 16:
            gitStash(fvc);
            return null;
         case 17:
            gitStashPop(fvc);
            return null;
         case 18:
            gitStashList(fvc);
            return null;
         case 19:
            gitMerge(arg, fvc);
            return null;
         case 20:
            gitFetch(fvc);
            return null;
         case 21:
            gitPull(fvc);
            return null;
         case 22:
            gitPush(fvc);
            return null;
         case 23:
            gitBranchDelete(arg, fvc);
            return null;
         case 24:
            gitRebase(arg, fvc);
            return null;
         case 25:
            gitDispatch(arg, fvc);
            return null;
         case 26:
            GitLogHelper.gitShow(fvc);
            return null;
         case 27:
            GitLogHelper.gitLogDiff(fvc);
            return null;
         case 28:
            GitLogHelper.gitExpand(fvc);
            return null;
         case 29:
            GitLogHelper.gitExpandAll(fvc);
            return null;
         case 30:
            GitLogHelper.gitCollapseAll(fvc);
            return null;
         case 31:
            gitBlame(arg, fvc);
            return null;
         case 32:
            gitStageHunk(fvc);
            return null;
         case 33:
            gitUnstageHunk(fvc);
            return null;
         case 34:
            gitPatch(arg, fvc);
            return null;
         case 35:
            gitGotoFile(fvc);
            return null;
         case 36:
            gitAmend(fvc);
            return null;
         case 37:
            gitCommitMenu(fvc);
            return null;
         case 38:
            gitCommitQuit(fvc);
            return null;
         case 39:
            gitCommitFinalize(fvc);
            return null;
         default:
            throw new RuntimeException("GitCommands:default " + rnum);
      }
   }

   /**
    * Show git status in a buffer.
    */
   private static void gitStatus(FvContext fvc) throws
         IOException, InputException {
      ensureListenerRegistered();
      List<String> lines = GitStatusBuffer.getStatusLines();
      statusBuffer = createBuffer("*git-status*", lines);
      FvContext.connectFv(statusBuffer, fvc.vi);
   }

   /**
    * Stage a file.  Usage: :git_stage filename
    */
   private static void gitStage(Object arg, FvContext fvc) throws
         IOException, InputException {
      if (null == arg) {
         throw new InputException("git_stage requires a filename argument");
      }
      String filename = arg.toString().trim();
      List<String> output = GitProcess.execute("add", filename);
      if (output.isEmpty()) {
         UI.reportMessage("Staged: " + filename);
      } else {
         UI.reportMessage(String.join(" ", output));
      }
      // Refresh status if visible
      if (null != statusBuffer) {
         gitStatus(fvc);
      }
   }

   /**
    * Unstage a file.  Usage: :git_unstage filename
    */
   private static void gitUnstage(Object arg, FvContext fvc) throws
         IOException, InputException {
      if (null == arg) {
         throw new InputException("git_unstage requires a filename argument");
      }
      String filename = arg.toString().trim();
      List<String> output = GitProcess.execute(
         "restore", "--staged", filename);
      if (output.isEmpty()) {
         UI.reportMessage("Unstaged: " + filename);
      } else {
         UI.reportMessage(String.join(" ", output));
      }
      // Refresh status if visible
      if (null != statusBuffer) {
         gitStatus(fvc);
      }
   }

   /**
    * Open the split-view commit workflow: left pane shows the editable
    * commit message, right pane shows staged files and unstaged diffs.
    * Loads any previously saved commit message for this repo.
    */
   private static void gitCommit(FvContext fvc) throws
         IOException, InputException {
      openSplitCommitView(fvc, false);
   }

   /** Open split-view commit: msg left, staging right. */
   private static void openSplitCommitView(FvContext fvc,
         boolean amend) throws IOException, InputException {
      ensureListenerRegistered();
      java.io.File dir = getFileDir(fvc);
      commitRepoRoot = GitProcess.getRepoRoot(dir);
      commitAmendMode = amend;

      // Build message buffer
      List<String> msgLines = new ArrayList<>();
      List<String> savedMsg =
         GitCommitView.loadMessage(commitRepoRoot);
      if (savedMsg != null && !savedMsg.isEmpty()) {
         msgLines.addAll(savedMsg);
      } else if (amend) {
         List<String> lastMsg = GitProcess.execute(
            "log", "-1", "--pretty=%B");
         for (String m : lastMsg) {
            if (!m.isEmpty() || !msgLines.isEmpty())
               msgLines.add(m);
         }
         while (!msgLines.isEmpty()
               && msgLines.get(msgLines.size() - 1).isEmpty())
            msgLines.remove(msgLines.size() - 1);
      }
      if (msgLines.isEmpty())
         msgLines.add("");
      commitMsgBuffer = createBuffer(
         "*git-commit-msg*", msgLines);

      // Build staging buffer
      List<String> stagingLines =
         GitCommitView.buildStagingView();
      commitViewHunks =
         GitCommitView.parseStagingViewHunks(stagingLines);
      commitStagingBuffer = createBuffer(
         "*git-commit-staging*", stagingLines);

      // Display message in current view (left pane)
      FvContext.connectFv(commitMsgBuffer, fvc.vi);

      // Create second view (right pane) for staging
      if (commitStagingView == null) {
         Command.command("va", fvc, null);
      } else {
         // Reuse existing staging view
         Command.command("vn", null, null);
      }
      // Now the new/next view is current — show staging there
      FvContext<?> stagingFvc = FvContext.getCurrFvc();
      commitStagingView = stagingFvc.vi;
      FvContext.connectFv(commitStagingBuffer, stagingFvc.vi);

      // Switch focus back to message view (left pane)
      Command.command("vn", null, null);
      if (amend) {
         UI.reportMessage(
            "Amend — edit message, ZZ to commit, q to cancel");
      } else {
         UI.reportMessage(
            "Edit message, ZZ to commit, q to cancel");
      }
   }

   /**
    * Show git diff output.  Usage: :git_diff [file]
    */
   private static void gitDiff(Object arg, FvContext fvc) throws
         IOException, InputException {
      List<String> output;
      if (null != arg) {
         String filename = arg.toString().trim();
         output = GitProcess.execute("diff", filename);
      } else {
         output = GitProcess.execute("diff");
      }
      if (output.isEmpty()) {
         UI.reportMessage("No differences");
         return;
      }
      outputBuffer = createBuffer("*git-diff*", output);
      FvContext.connectFv(outputBuffer, fvc.vi);
   }


   /**
    * Get the directory context for git commands.
    *
    * <p>When the current buffer is a DirEdit (directory browser),
    * returns the directory of the entry at the cursor.  When the
    * current buffer is the FileList, returns the directory of the
    * file at the cursor.  Otherwise returns the parent directory
    * of the current file.</p>
    *
    * @return the directory, or null if not determinable
    */
   @SuppressWarnings("unchecked")
   static java.io.File getFileDir(FvContext fvc) {
      // DirEdit: use path of cursor entry
      if (fvc.edvec instanceof DirEdit) {
         DirEdit de = (DirEdit) fvc.edvec;
         String fullPath = de.getFullPath(fvc.inserty());
         if (fullPath != null) {
            java.io.File f = new java.io.File(fullPath);
            return f.isDirectory() ? f : f.getParentFile();
         }
         // Fall back to the directory being browsed
         String dirName = de.fdes().getCanonName();
         if (dirName != null)
            return new java.io.File(dirName);
         return null;
      }
      // FileList: use path of file at cursor
      if (fvc.edvec instanceof FileList) {
         FileList fl = (FileList) fvc.edvec;
         int line = fvc.inserty();
         if (line >= 1 && line < fl.finish()) {
            EditContainer entry = fl.at(line);
            if (entry != null) {
               String entryPath = entry.fdes().getCanonName();
               if (entryPath != null && !entryPath.startsWith("*")) {
                  java.io.File ef = new java.io.File(entryPath);
                  java.io.File ep = ef.getParentFile();
                  if (ep != null && ep.isDirectory())
                     return ep;
               }
            }
         }
         return null;
      }
      // Regular file: use its parent directory
      String path = fvc.edvec.fdes().getCanonName();
      if (path == null || path.startsWith("*"))
         return null;
      java.io.File f = new java.io.File(path);
      java.io.File parent = f.getParentFile();
      if (parent != null && parent.isDirectory())
         return parent;
      return null;
   }








   /**
    * Show per-line blame annotations for a file.
    * With no argument, blames the current file.
    * Usage: {@code :git_blame} or {@code :git_blame path/to/file}
    */
   private static void gitBlame(Object arg, FvContext fvc) throws
         IOException, InputException {
      String filepath;
      java.io.File dir = null;
      if (null != arg) {
         filepath = arg.toString().trim();
      } else {
         String path = fvc.edvec.fdes().getCanonName();
         if (path == null || path.startsWith("*")) {
            throw new InputException(
               "No file to blame — use :git_blame <file>");
         }
         java.io.File f = new java.io.File(path);
         dir = f.getParentFile();
         filepath = f.getName();
      }
      List<GitBlameBuffer.BlameEntry> entries =
         GitBlameBuffer.getBlameEntries(filepath, dir);
      if (entries.isEmpty()) {
         UI.reportMessage("No blame data for " + filepath);
         return;
      }
      List<String> formatted = GitBlameBuffer.formatBlame(entries);
      String shortName = filepath;
      int slash = shortName.lastIndexOf('/');
      if (slash >= 0)
         shortName = shortName.substring(slash + 1);
      outputBuffer = createBuffer(
         "*git-blame:" + shortName + "*", formatted);
      FvContext.connectFv(outputBuffer, fvc.vi);
   }

   /**
    * Show an annotated diff for a file, enabling hunk-level staging.
    * With no argument, uses the file on the cursor line in the
    * status buffer.  Usage: {@code :git_patch} or
    * {@code :git_patch path/to/file}
    */
   private static void gitPatch(Object arg, FvContext fvc) throws
         IOException, InputException {
      String filepath;
      if (null != arg) {
         filepath = arg.toString().trim();
      } else {
         filepath = extractFilenameAtCursor(fvc);
         if (null == filepath) {
            throw new InputException(
               "No file — use :git_patch <file>");
         }
      }
      String section = findSection(fvc);
      List<String> diffLines;
      if ("Staged".equals(section)) {
         diffLines = GitHunkStaging.getStagedFileDiff(filepath);
      } else {
         diffLines = GitHunkStaging.getFileDiff(filepath);
      }
      if (diffLines.isEmpty()) {
         UI.reportMessage("No diff for " + filepath);
         return;
      }
      patchDiffLines = diffLines;
      patchHunks = GitHunkStaging.parseHunks(diffLines);
      List<String> formatted = GitHunkStaging.formatAnnotatedDiff(
         diffLines, patchHunks);
      outputBuffer = createBuffer("*git-patch*", formatted);
      FvContext.connectFv(outputBuffer, fvc.vi);
      UI.reportMessage(patchHunks.size() + " hunk"
         + (patchHunks.size() == 1 ? "" : "s")
         + " — s=stage  u=unstage");
   }

   /**
    * Stage the diff hunk at the cursor position.
    * Works in both the patch buffer ({@code *git-patch*}) and
    * the combined commit view ({@code *git-commit*}).
    * After a successful stage, the cursor advances to the next hunk.
    * In the commit view, the view is refreshed preserving the message.
    */
   @SuppressWarnings("unchecked")
   private static void gitStageHunk(FvContext fvc) throws
         IOException, InputException {
      TextEdit<String> buf = fvc.edvec;
      String bufName = buf.toString();
      boolean isCommitView = bufName.startsWith("*git-commit");

      List<GitHunkStaging.Hunk> hunks = isCommitView
         ? commitViewHunks : patchHunks;
      if (hunks == null || hunks.isEmpty()) {
         throw new InputException(
            "No hunks — open a patch with :git_patch first");
      }

      int bufferLine;
      if (isCommitView) {
         bufferLine = fvc.inserty();
      } else {
         // Account for the 2-line header in formatAnnotatedDiff
         bufferLine = fvc.inserty() - 2;
      }
      GitHunkStaging.Hunk hunk =
         GitHunkStaging.findHunkAtLine(hunks, bufferLine);
      if (null == hunk) {
         throw new InputException(
            "Cursor is not within a diff hunk");
      }
      String err = GitHunkStaging.stageHunk(hunk);
      if (null == err) {
         UI.reportMessage("Staged hunk " + (hunk.index + 1));
         if (isCommitView) {
            refreshCommitView(fvc);
         } else {
            // Refresh status if visible
            if (null != statusBuffer) {
               List<String> lines =
                  GitStatusBuffer.getStatusLines();
               statusBuffer =
                  createBuffer("*git-status*", lines);
            }
            // Advance cursor to the next hunk
            advanceToNextHunk(fvc, hunk);
         }
      } else {
         UI.reportMessage("Stage failed: " + err);
      }
   }

   /**
    * Unstage the diff hunk at the cursor position.
    * Works in both the patch buffer and the commit view.
    */
   @SuppressWarnings("unchecked")
   private static void gitUnstageHunk(FvContext fvc) throws
         IOException, InputException {
      TextEdit<String> buf = fvc.edvec;
      String bufName = buf.toString();
      boolean isCommitView = bufName.startsWith("*git-commit");

      List<GitHunkStaging.Hunk> hunks = isCommitView
         ? commitViewHunks : patchHunks;

      // In the commit view, check if cursor is in the staged section
      if (isCommitView) {
         int curLine = fvc.inserty();
         String curText = (curLine >= 1 && curLine <= buf.readIn())
            ? buf.at(curLine).toString() : "";
         // If in the staged section (comment lines between separators)
         if (curText.startsWith("#   ")
               && !curText.startsWith("#   (")) {
            // Check we're between staged and unstaged separators
            boolean inStaged = false;
            for (int i = curLine; i >= 1; i--) {
               String lt = buf.at(i).toString();
               if (lt.equals(GitCommitView.UNSTAGED_SEPARATOR))
                  break;
               if (lt.equals(GitCommitView.STAGED_SEPARATOR)) {
                  inStaged = true;
                  break;
               }
            }
            if (inStaged) {
               // Extract filename from "# file | N ++-" stat line
               String stat = curText.substring(4).trim();
               int pipe = stat.indexOf('|');
               if (pipe > 0)
                  stat = stat.substring(0, pipe).trim();
               GitProcess.execute("restore", "--staged", stat);
               UI.reportMessage("Unstaged: " + stat);
               refreshCommitView(fvc);
               return;
            }
         }
      }

      if (hunks == null || hunks.isEmpty()) {
         throw new InputException(
            "No hunks — open a patch with :git_patch first");
      }

      int bufferLine;
      if (isCommitView) {
         bufferLine = fvc.inserty();
      } else {
         bufferLine = fvc.inserty() - 2;
      }
      GitHunkStaging.Hunk hunk =
         GitHunkStaging.findHunkAtLine(hunks, bufferLine);
      if (null == hunk) {
         throw new InputException(
            "Cursor is not within a diff hunk");
      }
      String err = GitHunkStaging.unstageHunk(hunk);
      if (null == err) {
         UI.reportMessage("Unstaged hunk " + (hunk.index + 1));
         if (isCommitView) {
            refreshCommitView(fvc);
         } else {
            if (null != statusBuffer) {
               List<String> lines =
                  GitStatusBuffer.getStatusLines();
               statusBuffer =
                  createBuffer("*git-status*", lines);
            }
         }
      } else {
         UI.reportMessage("Unstage failed: " + err);
      }
   }

   /**
    * Move the cursor to the start of the next hunk after the given one.
    * If there is no next hunk, stay at the current position.
    *
    * @param fvc current view context
    * @param current the hunk just processed
    */
   private static void advanceToNextHunk(FvContext fvc,
         GitHunkStaging.Hunk current) {
      List<GitHunkStaging.Hunk> hunks = patchHunks;
      // Check if we're in the commit view
      String bufName = fvc.edvec.toString();
      if (bufName.startsWith("*git-commit"))
         hunks = commitViewHunks;
      if (hunks == null)
         return;
      for (GitHunkStaging.Hunk h : hunks) {
         if (h.index == current.index + 1) {
            if (bufName.startsWith("*git-commit")) {
               fvc.cursoryabs(h.bufferLine);
            } else {
               // +2 accounts for the 2-line header
               fvc.cursoryabs(h.bufferLine + 2);
            }
            return;
         }
      }
   }

   /** Refresh staging buffer after stage/unstage. */
   @SuppressWarnings("unchecked")
   private static void refreshCommitView(FvContext fvc) throws
         IOException, InputException {
      // Save message to disk from the message buffer
      if (commitMsgBuffer != null) {
         List<String> msgLines = new ArrayList<>();
         int size = commitMsgBuffer.readIn();
         for (int i = 1; i < size; i++)
            msgLines.add(commitMsgBuffer.at(i).toString());
         // Trim trailing blanks
         while (!msgLines.isEmpty()
               && msgLines.get(msgLines.size() - 1).isEmpty())
            msgLines.remove(msgLines.size() - 1);
         GitCommitView.saveMessage(commitRepoRoot, msgLines);
      }

      // Rebuild the staging buffer
      List<String> stagingLines =
         GitCommitView.buildStagingView();
      commitViewHunks =
         GitCommitView.parseStagingViewHunks(stagingLines);
      commitStagingBuffer = createBuffer(
         "*git-commit-staging*", stagingLines);

      // Update the staging view if it exists
      if (commitStagingView != null) {
         try {
            FvContext.connectFv(
               commitStagingBuffer, commitStagingView);
         } catch (InputException e) {
            // View may have been closed; fall back to current
            FvContext.connectFv(commitStagingBuffer, fvc.vi);
         }
      } else {
         FvContext.connectFv(commitStagingBuffer, fvc.vi);
      }

      // Also refresh status buffer if visible
      if (null != statusBuffer) {
         List<String> lines = GitStatusBuffer.getStatusLines();
         statusBuffer = createBuffer("*git-status*", lines);
      }
   }







   /**
    * Show git branch list.
    */
   private static void gitBranch(FvContext fvc) throws
         IOException, InputException {
      List<String> output = GitProcess.execute("branch", "-a", "-v");
      if (output.isEmpty()) {
         UI.reportMessage("No branches");
         return;
      }
      outputBuffer = createBuffer("*git-branch*", output);
      FvContext.connectFv(outputBuffer, fvc.vi);
   }

   /** Finalize commit from message buffer, close staging view. */
   @SuppressWarnings("unchecked")
   private static void gitDoCommit(FvContext fvc) throws
         IOException, InputException {
      boolean amend = commitAmendMode;

      // Read message from the dedicated message buffer if available
      String message;
      if (commitMsgBuffer != null) {
         StringBuilder msg = new StringBuilder();
         int size = commitMsgBuffer.readIn();
         for (int i = 1; i < size; i++) {
            String line = commitMsgBuffer.at(i).toString();
            if (msg.length() > 0)
               msg.append('\n');
            msg.append(line);
         }
         message = msg.toString().trim();
      } else {
         // Legacy: extract from combined view buffer
         TextEdit<String> buf = fvc.edvec;
         String bufName = buf.toString();
         if (bufName.startsWith("*git-commit")) {
            message = GitCommitView.extractMessage(buf);
         } else {
            StringBuilder msg = new StringBuilder();
            int size = buf.readIn();
            for (int i = 1; i < size; i++) {
               String line = buf.at(i).toString();
               if (!line.startsWith("#")) {
                  if (msg.length() > 0)
                     msg.append('\n');
                  msg.append(line);
               }
            }
            message = msg.toString().trim();
         }
      }

      if (message.isEmpty()) {
         UI.reportMessage("Aborting: empty commit message");
         return;
      }
      GitProcess.Result result;
      if (amend) {
         result = GitProcess.executeWithResult(
            "commit", "--amend", "-m", message);
      } else {
         result = GitProcess.executeWithResult(
            "commit", "-m", message);
      }
      if (0 == result.exitCode) {
         String verb = amend ? "Amended" : "Committed";
         UI.reportMessage(verb + ": " + firstLine(message));
         commitAmendMode = false;
         GitCommitView.clearSavedMessage(commitRepoRoot);
         closeSplitCommitView(fvc);
         // Return to status view
         if (null != statusBuffer) {
            gitStatus(fvc);
         }
      } else {
         String errMsg = result.output.isEmpty()
            ? "unknown error"
            : String.join(" ", result.output);
         UI.reportMessage("Commit failed: " + errMsg);
      }
   }

   /**
    * Create a new branch.  Usage: :git_branch_create name
    */
   private static void gitBranchCreate(Object arg, FvContext fvc) throws
         IOException, InputException {
      if (null == arg) {
         throw new InputException(
            "git_branch_create requires a branch name");
      }
      String name = arg.toString().trim();
      int rc = GitProcess.executeWithExitCode("branch", name);
      if (0 == rc) {
         UI.reportMessage("Created branch: " + name);
      } else {
         List<String> err = GitProcess.execute("branch", name);
         UI.reportMessage("Failed: " + String.join(" ", err));
      }
   }

   /**
    * Switch to an existing branch.  Usage: :git_branch_switch name
    */
   private static void gitBranchSwitch(Object arg, FvContext fvc) throws
         IOException, InputException {
      if (null == arg) {
         throw new InputException(
            "git_branch_switch requires a branch name");
      }
      String name = arg.toString().trim();
      int rc = GitProcess.executeWithExitCode("switch", name);
      if (0 == rc) {
         UI.reportMessage("Switched to: " + name);
      } else {
         List<String> err = GitProcess.execute("switch", name);
         UI.reportMessage("Switch failed: " + String.join(" ", err));
      }
   }

   /** Return the first line of a multi-line string. */
   private static String firstLine(String s) {
      int nl = s.indexOf('\n');
      return nl < 0 ? s : s.substring(0, nl);
   }

   /**
    * Stage the file on the current cursor line in the status buffer.
    * Uses {@code git add} for modified/new files and {@code git rm}
    * for deleted files.
    */
   private static void gitStageLine(FvContext fvc) throws
         IOException, InputException {
      String filename = extractFilenameAtCursor(fvc);
      if (null == filename) {
         throw new InputException("No file on current line");
      }
      if (isDeletedAtCursor(fvc)) {
         GitProcess.execute("rm", "--", filename);
      } else {
         GitProcess.execute("add", "--", filename);
      }
      UI.reportMessage("Staged: " + filename);
      gitStatus(fvc);
   }

   /**
    * Unstage the file on the current cursor line in the status buffer.
    */
   private static void gitUnstageLine(FvContext fvc) throws
         IOException, InputException {
      String filename = extractFilenameAtCursor(fvc);
      if (null == filename) {
         throw new InputException("No file on current line");
      }
      GitProcess.execute("restore", "--staged", filename);
      UI.reportMessage("Unstaged: " + filename);
      gitStatus(fvc);
   }

   /**
    * Toggle staging: stage if in unstaged/untracked section,
    * unstage if in staged section.
    */
   private static void gitToggle(FvContext fvc) throws
         IOException, InputException {
      String filename = extractFilenameAtCursor(fvc);
      if (null == filename) {
         throw new InputException("No file on current line");
      }
      String section = findSection(fvc);
      if ("Staged".equals(section)) {
         GitProcess.execute("restore", "--staged", filename);
         UI.reportMessage("Unstaged: " + filename);
      } else if (isDeletedAtCursor(fvc)) {
         GitProcess.execute("rm", "--", filename);
         UI.reportMessage("Staged: " + filename);
      } else {
         GitProcess.execute("add", "--", filename);
         UI.reportMessage("Staged: " + filename);
      }
      gitStatus(fvc);
   }

   /**
    * Discard unstaged changes to the file on the current line.
    */
   private static void gitDiscard(FvContext fvc) throws
         IOException, InputException {
      String filename = extractFilenameAtCursor(fvc);
      if (null == filename) {
         throw new InputException("No file on current line");
      }
      String section = findSection(fvc);
      if ("Staged".equals(section)) {
         throw new InputException("Use :git_unstage_line for staged files");
      }
      if ("Untracked".equals(section)) {
         throw new InputException("Cannot discard untracked file");
      }
      GitProcess.execute("checkout", "--", filename);
      UI.reportMessage("Discarded changes: " + filename);
      gitStatus(fvc);
   }

   /**
    * Refresh the git status buffer.
    */
   /** Refresh current git buffer (status, commit, or patch view). */
   private static void gitRefresh(FvContext fvc) throws
         IOException, InputException {
      String bufName = fvc.edvec.toString();
      if (bufName.startsWith("*git-commit")) {
         refreshCommitView(fvc);
      } else if ("*git-patch*".equals(bufName)) {
         // Re-run the patch for the same file
         gitPatch(null, fvc);
      } else {
         gitStatus(fvc);
      }
      UI.reportMessage("Refreshed");
   }

   /**
    * Determine which section the cursor is in by scanning backward
    * for a section header.
    *
    * @return "Staged", "Unstaged", or "Untracked"; null if not in section
    */
   @SuppressWarnings("unchecked")
   private static String findSection(FvContext fvc) {
      TextEdit<String> buf = fvc.edvec;
      int curLine = fvc.inserty();
      for (int i = curLine; i >= 1; i--) {
         String line = buf.at(i).toString();
         if (line.startsWith("Staged changes"))
            return "Staged";
         if (line.startsWith("Unstaged changes"))
            return "Unstaged";
         if (line.startsWith("Untracked files"))
            return "Untracked";
      }
      return null;
   }

   /**
    * Extract the filename from the current cursor line in a status buffer.
    * Handles lines like:
    * <pre>
    *   modified    path/to/file
    *   new file    path/to/file
    *   path/to/file  (untracked)
    * </pre>
    *
    * @return the filename, or null if the line has no file
    */
   @SuppressWarnings("unchecked")
   private static String extractFilenameAtCursor(FvContext fvc) {
      TextEdit<String> buf = fvc.edvec;
      int curLine = fvc.inserty();
      if (curLine < 1 || curLine > buf.readIn())
         return null;
      String line = buf.at(curLine).toString();
      // Lines with files start with "  " (2 spaces indent)
      if (!line.startsWith("  ") || line.startsWith("  ("))
         return null;
      String trimmed = line.trim();
      if (trimmed.isEmpty())
         return null;
      // status descriptors that precede the filename
      String[] prefixes = {
         "modified", "new file", "deleted", "renamed",
         "copied", "typechange", "changed", "unmerged"
      };
      for (String prefix : prefixes) {
         if (trimmed.startsWith(prefix)) {
            String rest = trimmed.substring(prefix.length()).trim();
            // Handle rename arrows: "oldpath -> newpath"
            int arrow = rest.indexOf(" -> ");
            if (arrow >= 0)
               return rest.substring(arrow + 4);
            return rest;
         }
      }
      // Untracked files are just the bare filename
      return trimmed;
   }

   /**
    * Check whether the current cursor line describes a deleted file.
    *
    * @return true if the line starts with the "deleted" descriptor
    */
   @SuppressWarnings("unchecked")
   static boolean isDeletedAtCursor(FvContext fvc) {
      TextEdit<String> buf = fvc.edvec;
      int curLine = fvc.inserty();
      if (curLine < 1 || curLine > buf.readIn())
         return false;
      String line = buf.at(curLine).toString();
      return line.trim().startsWith("deleted");
   }

   /**
    * Stash working directory changes.
    */
   private static void gitStash(FvContext fvc) throws
         IOException, InputException {
      List<String> output = GitProcess.execute("stash");
      if (output.isEmpty()) {
         UI.reportMessage("Nothing to stash");
      } else {
         UI.reportMessage(String.join(" ", output));
      }
      if (null != statusBuffer) {
         gitStatus(fvc);
      }
   }

   /**
    * Pop the top stash entry.
    */
   private static void gitStashPop(FvContext fvc) throws
         IOException, InputException {
      List<String> output = GitProcess.execute("stash", "pop");
      if (output.isEmpty()) {
         UI.reportMessage("No stash entries");
      } else {
         UI.reportMessage(output.get(0));
      }
      if (null != statusBuffer) {
         gitStatus(fvc);
      }
   }

   /**
    * Show stash list in a buffer.
    */
   private static void gitStashList(FvContext fvc) throws
         IOException, InputException {
      List<String> output = GitProcess.execute("stash", "list");
      if (output.isEmpty()) {
         UI.reportMessage("No stash entries");
         return;
      }
      outputBuffer = createBuffer("*git-stash-list*", output);
      FvContext.connectFv(outputBuffer, fvc.vi);
   }

   /**
    * Merge a branch into the current branch.
    * Usage: :git_merge branchname
    *
    * <p>On success, reports the merge result.  On conflict, opens a
    * buffer listing the conflicted files so the user can resolve them.</p>
    */
   private static void gitMerge(Object arg, FvContext fvc) throws
         IOException, InputException {
      if (null == arg) {
         throw new InputException(
            "git_merge requires a branch name");
      }
      String branch = arg.toString().trim();
      GitProcess.Result res = GitProcess.executeWithResult(
         "merge", branch);
      if (0 == res.exitCode) {
         if (res.output.isEmpty()) {
            UI.reportMessage("Merged " + branch + " (already up to date)");
         } else {
            UI.reportMessage("Merged " + branch + ": "
               + res.output.get(0));
         }
      } else {
         // Check for merge conflicts
         List<String> conflicts = GitProcess.execute(
            "diff", "--name-only", "--diff-filter=U");
         if (!conflicts.isEmpty()) {
            java.util.ArrayList<String> lines = new java.util.ArrayList<>();
            lines.add("Merge Conflicts");
            lines.add("===============");
            lines.add("");
            lines.add("Branch: " + branch);
            lines.add("Conflicted files (" + conflicts.size() + "):");
            lines.add("");
            for (String f : conflicts) {
               lines.add("  " + f);
            }
            lines.add("");
            lines.add("Resolve conflicts, then :git_stage each file"
               + " and :git_do_commit");
            lines.add("To abort: run 'git merge --abort' in a shell");
            outputBuffer = createBuffer("*git-merge*", lines);
            FvContext.connectFv(outputBuffer, fvc.vi);
         } else {
            // Non-conflict failure — show raw output
            outputBuffer = createBuffer("*git-merge*", res.output);
            FvContext.connectFv(outputBuffer, fvc.vi);
         }
      }
      if (null != statusBuffer) {
         gitStatus(fvc);
      }
   }

   /**
    * Fetch from remote.  Usage: :git_fetch
    */
   private static void gitFetch(FvContext fvc) throws
         IOException, InputException {
      GitProcess.Result res = GitProcess.executeWithResult("fetch");
      if (0 == res.exitCode) {
         if (res.output.isEmpty()) {
            UI.reportMessage("Fetch complete (no changes)");
         } else {
            outputBuffer = createBuffer("*git-fetch*", res.output);
            FvContext.connectFv(outputBuffer, fvc.vi);
         }
      } else {
         UI.reportMessage("Fetch failed: "
            + (res.output.isEmpty() ? "unknown error"
               : res.output.get(0)));
      }
   }

   /**
    * Pull from remote (fetch + merge).  Usage: :git_pull
    */
   private static void gitPull(FvContext fvc) throws
         IOException, InputException {
      GitProcess.Result res = GitProcess.executeWithResult("pull");
      if (0 == res.exitCode) {
         if (res.output.isEmpty()) {
            UI.reportMessage("Pull complete (up to date)");
         } else {
            UI.reportMessage("Pull: " + res.output.get(0));
         }
      } else {
         // Pull may result in merge conflicts
         List<String> conflicts = GitProcess.execute(
            "diff", "--name-only", "--diff-filter=U");
         if (!conflicts.isEmpty()) {
            java.util.ArrayList<String> lines = new java.util.ArrayList<>();
            lines.add("Pull Merge Conflicts");
            lines.add("====================");
            lines.add("");
            lines.add("Conflicted files (" + conflicts.size() + "):");
            lines.add("");
            for (String f : conflicts) {
               lines.add("  " + f);
            }
            lines.add("");
            lines.add("Resolve conflicts, then :git_stage each file"
               + " and :git_do_commit");
            outputBuffer = createBuffer("*git-pull*", lines);
            FvContext.connectFv(outputBuffer, fvc.vi);
         } else {
            UI.reportMessage("Pull failed: "
               + (res.output.isEmpty() ? "unknown error"
                  : res.output.get(0)));
         }
      }
      if (null != statusBuffer) {
         gitStatus(fvc);
      }
   }

   /**
    * Delete a branch.  Usage: :git_branch_delete name
    *
    * <p>Uses {@code git branch -d} which only deletes fully-merged
    * branches.  For force-delete, the user must use a shell.</p>
    */
   private static void gitBranchDelete(Object arg, FvContext fvc) throws
         IOException, InputException {
      if (null == arg) {
         throw new InputException(
            "git_branch_delete requires a branch name");
      }
      String name = arg.toString().trim();
      GitProcess.Result res = GitProcess.executeWithResult(
         "branch", "-d", name);
      if (0 == res.exitCode) {
         UI.reportMessage("Deleted branch: " + name);
      } else {
         UI.reportMessage("Delete failed: "
            + (res.output.isEmpty() ? "unknown error"
               : res.output.get(0)));
      }
   }

   /**
    * Rebase the current branch.
    * Usage: :git_rebase &lt;branch&gt;  — rebase onto branch
    *        :git_rebase --abort    — abort in-progress rebase
    *        :git_rebase --continue — continue after conflict resolution
    *
    * <p>On conflict, opens a buffer listing conflicted files so the
    * user can resolve them and then run {@code :git_rebase --continue}.</p>
    */
   private static void gitRebase(Object arg, FvContext fvc) throws
         IOException, InputException {
      if (null == arg) {
         throw new InputException(
            "Usage: :git_rebase <branch> | --abort | --continue");
      }
      String param = arg.toString().trim();
      GitProcess.Result res;
      if ("--abort".equals(param)) {
         res = GitProcess.executeWithResult("rebase", "--abort");
         if (0 == res.exitCode) {
            UI.reportMessage("Rebase aborted");
         } else {
            UI.reportMessage("Rebase abort failed: "
               + (res.output.isEmpty() ? "unknown error"
                  : res.output.get(0)));
         }
      } else if ("--continue".equals(param)) {
         res = GitProcess.executeWithResult("rebase", "--continue");
         if (0 == res.exitCode) {
            UI.reportMessage("Rebase continued successfully");
         } else {
            showRebaseConflicts(res, "rebase --continue", fvc);
         }
      } else {
         res = GitProcess.executeWithResult("rebase", param);
         if (0 == res.exitCode) {
            if (res.output.isEmpty()) {
               UI.reportMessage("Rebased onto " + param
                  + " (already up to date)");
            } else {
               UI.reportMessage("Rebased onto " + param + ": "
                  + res.output.get(0));
            }
         } else {
            showRebaseConflicts(res, "rebase " + param, fvc);
         }
      }
      if (null != statusBuffer) {
         gitStatus(fvc);
      }
   }

   /**
    * Show rebase conflict details in a buffer, or raw output if no
    * conflicts are detected.
    */
   private static void showRebaseConflicts(GitProcess.Result res,
         String cmdDesc, FvContext fvc) throws IOException, InputException {
      List<String> conflicts = GitProcess.execute(
         "diff", "--name-only", "--diff-filter=U");
      if (!conflicts.isEmpty()) {
         java.util.ArrayList<String> lines = new java.util.ArrayList<>();
         lines.add("Rebase Conflicts");
         lines.add("================");
         lines.add("");
         lines.add("Command: git " + cmdDesc);
         lines.add("Conflicted files (" + conflicts.size() + "):");
         lines.add("");
         for (String f : conflicts) {
            lines.add("  " + f);
         }
         lines.add("");
         lines.add("Resolve conflicts, then :git_stage each file"
            + " and :git_rebase --continue");
         lines.add("To abort: :git_rebase --abort");
         outputBuffer = createBuffer("*git-rebase*", lines);
         FvContext.connectFv(outputBuffer, fvc.vi);
      } else {
         outputBuffer = createBuffer("*git-rebase*", res.output);
         FvContext.connectFv(outputBuffer, fvc.vi);
      }
   }

   /**
    * Push to remote.  Usage: :git_push
    */
   private static void gitPush(FvContext fvc) throws
         IOException, InputException {
      GitProcess.Result res = GitProcess.executeWithResult("push");
      if (0 == res.exitCode) {
         if (res.output.isEmpty()) {
            UI.reportMessage("Push complete");
         } else {
            UI.reportMessage("Push: " + res.output.get(0));
         }
      } else {
         UI.reportMessage("Push failed: "
            + (res.output.isEmpty() ? "unknown error"
               : res.output.get(0)));
      }
   }

   /**
    * Dispatch {@code :git <subcommand> [args]} to the matching
    * {@code git_<subcommand>} command.  For example, {@code :git status}
    * dispatches to {@code git_status}, and {@code :git stage file.java}
    * dispatches to {@code git_stage} with argument {@code file.java}.
    */
   private static void gitDispatch(Object arg, FvContext fvc) throws
         IOException, InputException {
      if (null == arg) {
         gitStatus(fvc);
         return;
      }
      String full = arg.toString().trim();
      // Try full underscore-joined form (":git do commit" -> git_do_commit)
      String sub;
      String rest;
      String joined = "git_" + full.replace(' ', '_');
      if (Command.hasCommand(joined)) {
         Command.command(joined, fvc, null);
         return;
      }
      int sp = full.indexOf(' ');
      if (sp >= 0) {
         sub = full.substring(0, sp);
         rest = full.substring(sp + 1).trim();
      } else {
         sub = full;
         rest = null;
      }
      String cmdName = "git_" + sub;
      Command.command(cmdName, fvc, rest);
   }

   /**
    * Register the file-write listener for auto-refresh.
    * Called when git_status first opens; idempotent.
    */
   private static void ensureListenerRegistered() {
      if (!listenerRegistered) {
         EditContainer.registerListener(new GitWriteListener());
         listenerRegistered = true;
      }
   }

   /**
    * Listener that auto-refreshes the git status buffer when a file
    * is written.
    */
   private static final class GitWriteListener
         implements EditContainer.FileStatusListener {

      public void fileAdded(EditContainer ev) {
      }

      public void fileWritten(EditContainer ev) {
         if (null != statusBuffer) {
            try {
               List<String> lines = GitStatusBuffer.getStatusLines();
               statusBuffer = createBuffer("*git-status*", lines);
            } catch (IOException e) {
               // silently ignore refresh failures
            }
         }
      }

      public boolean fileDisposed(EditContainer ev) {
         return false;
      }
   }

   /**
    * Navigate to the file and line referenced by the current cursor
    * position in a diff/patch buffer.  Parses the {@code +++} header
    * for the filename and the {@code @@} hunk header plus line offset
    * for the target line number.
    */
   @SuppressWarnings("unchecked")
   private static void gitGotoFile(FvContext fvc) throws
         IOException, InputException {
      TextEdit<String> buf = fvc.edvec;
      String bufName = buf.toString();

      // Status buffer: extract filename and open it
      if ("*git-status*".equals(bufName)) {
         // First check if cursor is on a diff line (+++ or @@)
         int curLine = fvc.inserty();
         String curText = (curLine >= 1 && curLine <= buf.readIn())
            ? buf.at(curLine).toString() : "";
         if (curText.startsWith("+++ ")
               || curText.startsWith("@@ ")
               || curText.startsWith("diff --git ")) {
            // Delegate to diff navigation logic below
            navigateDiffLine(fvc, buf, curLine);
            return;
         }
         String filename = extractFilenameAtCursor(fvc);
         if (filename == null)
            throw new InputException("No file on current line");
         FvContext newFvc = FileList.openFileName(filename, fvc.vi);
         if (newFvc != null)
            newFvc.cursoryabs(1);
         return;
      }

      // Commit staging view: handle stat lines in staged section
      if (bufName.startsWith("*git-commit")) {
         int curLine = fvc.inserty();
         String curText = (curLine >= 1 && curLine <= buf.readIn())
            ? buf.at(curLine).toString() : "";
         // Staged stat lines: "#   file.java | 3 +-"
         if (curText.startsWith("#   ")
               && !curText.startsWith("#   (")) {
            String stat = curText.substring(4).trim();
            int pipe = stat.indexOf('|');
            if (pipe > 0)
               stat = stat.substring(0, pipe).trim();
            if (!stat.isEmpty()) {
               FvContext newFvc =
                  FileList.openFileName(stat, fvc.vi);
               if (newFvc != null)
                  newFvc.cursoryabs(1);
               return;
            }
         }
      }

      navigateDiffLine(fvc, buf, fvc.inserty());
   }

   /**
    * Navigate from a diff context to the corresponding source file.
    * Walks backward from the given line to find {@code +++} (filename)
    * and {@code @@} (hunk start), then computes the target line.
    */
   private static void navigateDiffLine(FvContext fvc,
         TextEdit<String> buf, int curLine) throws
         IOException, InputException {
      // Walk backward to find +++ (filename) and @@ (hunk start)
      String filepath = null;
      int hunkNewStart = 0;
      int hunkBodyStart = 0;
      for (int i = curLine; i >= 1; i--) {
         String line = buf.at(i).toString();
         if (line.startsWith("@@ ") && hunkBodyStart == 0) {
            hunkBodyStart = i + 1;
            // Parse @@ -old,count +new,count @@
            int plus = line.indexOf('+', 3);
            if (plus >= 0) {
               int comma = line.indexOf(',', plus);
               int sp = line.indexOf(' ', plus);
               int end = comma >= 0 && (sp < 0 || comma < sp)
                  ? comma : sp;
               if (end > plus)
                  hunkNewStart = parseIntSafe(
                     line.substring(plus + 1, end));
            }
         }
         if (line.startsWith("+++ ") && filepath == null) {
            filepath = stripDiffPrefix(line.substring(4).trim());
            break;
         }
      }
      // If backward scan failed, scan forward for +++ or
      // extract from diff --git header
      if (filepath == null) {
         filepath = forwardScanForFilepath(buf, curLine);
      }
      if (filepath == null || filepath.equals("/dev/null")) {
         throw new InputException("No file path found");
      }
      // Count new-file lines from hunk body start to cursor
      int lineOffset = 0;
      if (hunkBodyStart > 0) {
         for (int i = hunkBodyStart; i <= curLine; i++) {
            String line = buf.at(i).toString();
            if (!line.startsWith("-"))
               lineOffset++;
         }
      }
      int targetLine = hunkNewStart + lineOffset;
      if (targetLine < 1) targetLine = 1;

      FvContext newFvc = FileList.openFileName(filepath, fvc.vi);
      if (newFvc != null)
         newFvc.cursoryabs(targetLine);
   }

   /**
    * Strip a single-character prefix (b/, w/, i/, etc.) from a
    * diff file path.  Returns the original path if no prefix.
    */
   static String stripDiffPrefix(String path) {
      if (path.length() > 2 && path.charAt(1) == '/')
         return path.substring(2);
      return path;
   }

   /**
    * Scan forward from curLine to find a {@code +++} line for the
    * filename.  Also handles {@code diff --git} lines by extracting
    * the second (destination) path.
    *
    * @return the file path, or null if not found
    */
   @SuppressWarnings("unchecked")
   static String forwardScanForFilepath(
         TextEdit<String> buf, int curLine) {
      // Delegate to List-based overload for testability
      List<String> lines = new ArrayList<>();
      for (int i = 1; i <= buf.readIn(); i++)
         lines.add(buf.at(i).toString());
      return forwardScanForFilepath(lines, curLine);
   }

   /**
    * List-based overload for unit testing.  Line numbers are
    * 1-based (matching TextEdit convention).
    */
   static String forwardScanForFilepath(
         List<String> lines, int curLine) {
      String curText = (curLine >= 1 && curLine <= lines.size())
         ? lines.get(curLine - 1) : "";

      // diff --git a/path b/path  OR  diff --git i/path w/path
      if (curText.startsWith("diff --git ")) {
         return extractPathFromDiffGit(curText);
      }

      // Scan forward (limited to 5 lines) for +++ header
      int limit = Math.min(curLine + 5, lines.size());
      for (int i = curLine + 1; i <= limit; i++) {
         String line = lines.get(i - 1);
         if (line.startsWith("+++ ")) {
            return stripDiffPrefix(line.substring(4).trim());
         }
         // Stop at next diff header or hunk start
         if (line.startsWith("diff --git ")
               || line.startsWith("@@ "))
            break;
      }
      return null;
   }

   /**
    * Extract the destination file path from a {@code diff --git}
    * header line.  Handles both standard ({@code a/...  b/...})
    * and custom ({@code i/...  w/...}) prefixes.
    *
    * @param line the full diff --git header
    * @return extracted path, or null if unparseable
    */
   static String extractPathFromDiffGit(String line) {
      // Format: "diff --git <prefix>/<path> <prefix>/<path>"
      // The second path is the destination.
      String rest = line.substring("diff --git ".length());
      // Find the second path: split on space, but paths can
      // contain spaces.  The reliable marker is the prefix
      // char + '/' appearing twice.
      // Try: find " b/" or " w/" as separator
      int sep = rest.lastIndexOf(" b/");
      if (sep < 0) sep = rest.lastIndexOf(" w/");
      if (sep < 0) {
         // Fallback: split on first space, take second half
         int sp = rest.indexOf(' ');
         if (sp > 0) {
            return stripDiffPrefix(rest.substring(sp + 1).trim());
         }
         return null;
      }
      return rest.substring(sep + 3).trim();
   }

   /**
    * Parse an integer, returning 0 on failure.
    */
   private static int parseIntSafe(String s) {
      try {
         return Integer.parseInt(s.trim());
      } catch (NumberFormatException e) {
         return 0;
      }
   }

   /**
    * Switch to amend mode. Opens the combined commit view
    * pre-populated with the previous commit's message.
    */
   @SuppressWarnings("unchecked")
   /**
    * Open the split-view commit workflow in amend mode.
    * Pre-fills the message buffer with the previous commit message.
    */
   private static void gitAmend(FvContext fvc) throws
         IOException, InputException {
      openSplitCommitView(fvc, true);
   }

   /**
    * Commit sub-menu triggered by 'c' in git status/commit buffers.
    * Reads the next key to decide the action:
    * <ul>
    *   <li>{@code c} — open fresh commit view</li>
    *   <li>{@code a} — open amend commit view</li>
    *   <li>{@code A} — amend without editing (reuse message)</li>
    * </ul>
    * Any other key cancels the sub-menu.
    */
   private static void gitCommitMenu(FvContext fvc) throws
         IOException, InputException {
      ContextHelp.onSubModeEntered("gitcommitmenu");
      UI.reportMessage("c=commit  a=amend  A=amend-no-edit");
      char next = EventQueue.nextKey(fvc.vi);
      switch (next) {
         case 'c':
            gitCommit(fvc);
            break;
         case 'a':
            gitAmend(fvc);
            break;
         case 'A':
            gitAmendNoEdit(fvc);
            break;
         case 27: // ESC
            UI.reportMessage("");
            break;
         default:
            UI.reportMessage("Cancelled");
            break;
      }
   }

   /**
    * Amend the last commit without editing the message.
    * Equivalent to {@code git commit --amend --no-edit}.
    */
   private static void gitAmendNoEdit(FvContext fvc) throws
         IOException, InputException {
      int rc = GitProcess.executeWithExitCode(
         "commit", "--amend", "--no-edit");
      if (0 == rc) {
         UI.reportMessage("Amended (no edit)");
         if (null != statusBuffer)
            gitStatus(fvc);
      } else {
         List<String> err = GitProcess.execute(
            "commit", "--amend", "--no-edit");
         UI.reportMessage("Amend failed: "
            + String.join(" ", err));
      }
   }


   /** Close split-view: remove staging pane, clear state. */
   private static void closeSplitCommitView(FvContext fvc)
         throws IOException, InputException {
      if (commitStagingView != null) {
         // Ensure the staging view is current before closing it
         FvContext<?> curr = FvContext.getCurrFvc();
         if (curr.vi != commitStagingView) {
            FvContext.connectFv(
               commitStagingBuffer, commitStagingView);
            // Navigate to staging view
            while (FvContext.getCurrFvc().vi != commitStagingView) {
               Command.command("vn", null, null);
               if (FvContext.getCurrFvc().vi == curr.vi)
                  break; // Prevent infinite loop
            }
         }
         // Close the current (staging) view
         Command.command("vd", null, null);
         commitStagingView = null;
      }
      commitMsgBuffer = null;
      commitStagingBuffer = null;
      commitViewHunks = null;
   }

   /** Quit commit with confirmation, saving message. */
   private static void gitCommitQuit(FvContext fvc) throws
         IOException, InputException {
      UI.reportMessage("Discard commit? (y/n)");
      char confirm = EventQueue.nextKey(fvc.vi);
      if (confirm != 'y' && confirm != 'Y') {
         UI.reportMessage("");
         return;
      }
      // Save the in-progress message before closing
      if (commitMsgBuffer != null && commitRepoRoot != null) {
         List<String> msgLines = new ArrayList<>();
         int size = commitMsgBuffer.readIn();
         for (int i = 1; i < size; i++)
            msgLines.add(commitMsgBuffer.at(i).toString());
         while (!msgLines.isEmpty()
               && msgLines.get(msgLines.size() - 1).isEmpty())
            msgLines.remove(msgLines.size() - 1);
         GitCommitView.saveMessage(commitRepoRoot, msgLines);
      }
      closeSplitCommitView(fvc);
      // Return to status view
      if (null != statusBuffer) {
         gitStatus(fvc);
      } else {
         UI.reportMessage("Commit cancelled");
      }
   }

   /** ZZ handler: read next key, commit if Z. */
   private static void gitCommitFinalize(FvContext fvc) throws
         IOException, InputException {
      ContextHelp.onSubModeEntered("gitcommitfinalize");
      UI.reportMessage("Z to commit, any other key cancels");
      char next = EventQueue.nextKey(fvc.vi);
      if (next == 'Z') {
         gitDoCommit(fvc);
      } else {
         UI.reportMessage("");
      }
   }

   /**
    * Create a TextEdit buffer with the given name and content lines.
    *
    * @param name the internal buffer name
    * @param lines the content lines
    * @return a new TextEdit buffer
    */
   private static TextEdit<String> createBuffer(String name,
         List<String> lines) {
      String content = String.join("\n", lines);
      StringIoc sio = new StringIoc(name, content);
      return new TextEdit<>(sio, sio.prop);
   }

   /**
    * Visual-mode handler for git commit/patch buffers.
    * Handles 's' (stage selected lines) and 'u' (unstage).
    *
    * @return true if the key was consumed, false for default
    */
   @SuppressWarnings("unchecked")
   public static boolean handleVisualKey(char key, int starty,
         int doney, int startx, int donex, FvContext<?> fvc)
         throws IOException, InputException {
      if (key != 's' && key != 'u')
         return false;

      TextEdit<?> buf = fvc.edvec;
      String bufName = buf.toString();
      boolean isCommitView = bufName.startsWith("*git-commit");

      List<GitHunkStaging.Hunk> hunks = isCommitView
         ? commitViewHunks : patchHunks;
      if (hunks == null || hunks.isEmpty()) {
         UI.reportMessage("No hunks available");
         return true;
      }

      // Find the hunk containing the selection start
      int adjustedStart = starty;
      int adjustedEnd = doney;
      if (!isCommitView) {
         // Account for the 2-line header in formatAnnotatedDiff
         adjustedStart -= 2;
         adjustedEnd -= 2;
      }
      GitHunkStaging.Hunk hunk =
         GitHunkStaging.findHunkAtLine(hunks, adjustedStart);
      if (hunk == null) {
         UI.reportMessage(
            "Selection is not within a diff hunk");
         return true;
      }

      String err;
      if (key == 's') {
         err = GitHunkStaging.stagePartialHunk(
            hunk, starty, doney);
      } else {
         err = GitHunkStaging.unstagePartialHunk(
            hunk, starty, doney);
      }
      if (err == null) {
         String verb = (key == 's') ? "Staged" : "Unstaged";
         UI.reportMessage(verb + " selected lines");
         if (isCommitView) {
            refreshCommitView(fvc);
         } else if (statusBuffer != null) {
            List<String> lines =
               GitStatusBuffer.getStatusLines();
            statusBuffer =
               createBuffer("*git-status*", lines);
         }
      } else {
         UI.reportMessage(
            (key == 's' ? "Stage" : "Unstage")
            + " failed: " + err);
      }
      return true;
   }
}
