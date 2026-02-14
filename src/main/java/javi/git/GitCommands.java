package javi.git;

import java.io.IOException;
import java.util.List;

import javi.FvContext;
import javi.InputException;
import javi.Rgroup;
import javi.StringIoc;
import javi.TextEdit;
import javi.UI;

/**
 * Rgroup subclass providing git commands for the editor.
 *
 * <p>Registers commands: git_status, git_stage, git_unstage,
 * git_commit, git_diff, git_log, git_branch. These are invoked
 * via colon commands like {@code :git_status}.</p>
 *
 * @see GitProcess
 * @see GitStatusBuffer
 */
public final class GitCommands extends Rgroup {

   /** The git status buffer, reused across invocations. */
   private static TextEdit<String> statusBuffer;

   /** The git output buffer for diff/log/branch results. */
   private static TextEdit<String> outputBuffer;

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
      };
      register(rnames);
   }

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws IOException, InputException {
      if (!GitProcess.isGitRepo()) {
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
            gitLog(fvc);
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
         default:
            throw new RuntimeException("GitCommands:default " + rnum);
      }
   }

   /**
    * Show git status in a buffer.
    */
   private static void gitStatus(FvContext fvc) throws
         IOException, InputException {
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
    * Open a commit message buffer showing staged changes.
    */
   private static void gitCommit(FvContext fvc) throws
         IOException, InputException {
      // Get staged files for reference
      List<String> staged = GitProcess.execute(
         "diff", "--cached", "--stat");

      java.util.ArrayList<String> lines = new java.util.ArrayList<>();
      lines.add("");
      lines.add("# Enter commit message above, then run :git_do_commit");
      lines.add("# Lines starting with '#' will be ignored.");
      lines.add("#");
      lines.add("# Changes to be committed:");
      for (String s : staged) {
         lines.add("#   " + s);
      }
      if (staged.isEmpty()) {
         lines.add("#   (no staged changes)");
      }

      TextEdit<String> commitBuffer = createBuffer("*git-commit*", lines);
      FvContext.connectFv(commitBuffer, fvc.vi);
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
    * Show git log.
    */
   private static void gitLog(FvContext fvc) throws
         IOException, InputException {
      List<String> output = GitProcess.execute(
         "log", "--oneline", "--graph", "-30");
      if (output.isEmpty()) {
         UI.reportMessage("No log entries");
         return;
      }
      outputBuffer = createBuffer("*git-log*", output);
      FvContext.connectFv(outputBuffer, fvc.vi);
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

   /**
    * Finalize a commit using the message from the *git-commit* buffer.
    * Reads all non-comment, non-empty lines from the current buffer
    * as the commit message, then runs {@code git commit -m "message"}.
    */
   @SuppressWarnings("unchecked") // FvContext raw type in Rgroup API
   private static void gitDoCommit(FvContext fvc) throws
         IOException, InputException {
      TextEdit<String> buf = fvc.edvec;
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
      String message = msg.toString().trim();
      if (message.isEmpty()) {
         UI.reportMessage("Aborting: empty commit message");
         return;
      }
      int rc = GitProcess.executeWithExitCode("commit", "-m", message);
      if (0 == rc) {
         UI.reportMessage("Committed: " + firstLine(message));
      } else {
         List<String> err = GitProcess.execute("commit", "-m", message);
         UI.reportMessage("Commit failed: " + String.join(" ", err));
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
    */
   private static void gitStageLine(FvContext fvc) throws
         IOException, InputException {
      String filename = extractFilenameAtCursor(fvc);
      if (null == filename) {
         throw new InputException("No file on current line");
      }
      GitProcess.execute("add", filename);
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
      } else {
         GitProcess.execute("add", filename);
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
   private static void gitRefresh(FvContext fvc) throws
         IOException, InputException {
      gitStatus(fvc);
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
}
