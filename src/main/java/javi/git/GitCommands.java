package javi.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javi.Command;
import javi.EditContainer;
import javi.FoldModel;
import javi.FvContext;
import javi.InputException;
import javi.Plugin;
import javi.PosListList;
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
   private static TextEdit<String> outputBuffer;

   /** The git log buffer, reused across invocations. */
   private static TextEdit<String> logBuffer;

   /** Whether the file-write listener has been registered. */
   private static boolean listenerRegistered;

   /** Working directory for git log commands. */
   private static java.io.File logDir;

   /** Number of log entries to fetch per page. */
   private static int logPageSize = 100;

   /** Raw graph lines from most recent git log. */
   private static List<String> logLogLines;

   /** SHA-to-message map from most recent git log. */
   private static Map<String, List<String>> logMessages;

   /** SHAs whose diffs are currently expanded. */
   private static final java.util.Set<String> logDiffExpanded =
      new java.util.HashSet<>();

   /** Cached diff content keyed by SHA. */
   private static final Map<String, List<String>> logDiffCache =
      new java.util.HashMap<>();

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
            gitShow(fvc);
            return null;
         case 27:
            gitLogDiff(fvc);
            return null;
         case 28:
            gitExpand(fvc);
            return null;
         case 29:
            gitExpandAll(fvc);
            return null;
         case 30:
            gitCollapseAll(fvc);
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
    * Show git log in a text buffer for in-editor navigation.
    * Graph window is disabled; use :git_show / :git_log_diff
    * to inspect individual commits.
    */
   private static void gitLog(FvContext fvc) throws
         IOException, InputException {
      java.io.File dir = getFileDir(fvc);
      logDir = dir;
      logPageSize = 100;
      logDiffExpanded.clear();
      logDiffCache.clear();
      logLogLines = GitLogBuffer.getLogLines(logPageSize, dir);
      if (!logLogLines.isEmpty()) {
         logMessages =
            GitLogBuffer.getCommitMessages(logPageSize, dir);
         List<int[]> foldRanges = new ArrayList<>();
         List<String> formatted =
            GitLogBuffer.buildFoldedLog(
               logLogLines, logMessages, foldRanges,
               logDiffExpanded, logDiffCache);
         logBuffer = createBuffer("*git-log*", formatted);
         registerLogInPosListList(formatted);
         FvContext<?> logFvc =
            FvContext.connectFv(logBuffer, fvc.vi);
         FoldModel fm = new FoldModel();
         for (int[] range : foldRanges) {
            fm.addFold(range[0], range[1]);
         }
         fm.closeAll();
         fm.setToggleHandler(GitCommands::handleLogFoldToggle);
         logFvc.setFoldModel(fm);
      }
   }

   /**
    * Get the parent directory of the current file for git commands.
    *
    * @return the directory, or null if not a local file
    */
   private static java.io.File getFileDir(FvContext fvc) {
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
    * Registers the git log as a position list so it appears
    * in F6 PosListList for easy navigation back to the log.
    */
   private static void registerLogInPosListList(
         List<String> formatted) {
      Pattern shaPat =
         Pattern.compile("^[*|/\\\\ ]+\\s*([0-9a-f]{7,40})\\b");
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < formatted.size(); i++) {
         Matcher m = shaPat.matcher(formatted.get(i));
         if (m.find()) {
            String sha = m.group(1);
            String rest = formatted.get(i)
               .substring(m.end()).trim();
            // Format: filename(line -comment) for PositionConverter
            sb.append("*git-log*(").append(i + 1)
               .append(" -").append(sha)
               .append(" ").append(rest).append(")\n");
         }
      }
      BufferedReader reader = new BufferedReader(
         new StringReader(sb.toString()));
      PosListList.Cmd.replaceFromReader("git-log", reader);
   }

   /**
    * Show full commit details for the commit on the current line.
    * Extracts the SHA from the cursor line in the log buffer and
    * displays author, date, full message, and diff stat.
    */
   private static void gitShow(FvContext fvc) throws
         IOException, InputException {
      String sha = extractShaAtCursor(fvc);
      if (null == sha) {
         throw new InputException("No commit SHA on current line");
      }
      List<String> details = GitLogBuffer.getCommitDetails(sha);
      outputBuffer = createBuffer("*git-show*", details);
      FvContext.connectFv(outputBuffer, fvc.vi);
   }

   /**
    * Show the full diff for the commit on the current line.
    * Extracts the SHA from the cursor line in the log buffer.
    */
   private static void gitLogDiff(FvContext fvc) throws
         IOException, InputException {
      String sha = extractShaAtCursor(fvc);
      if (null == sha) {
         throw new InputException("No commit SHA on current line");
      }
      List<String> diff = GitLogBuffer.getCommitDiff(sha);
      outputBuffer = createBuffer("*git-diff*", diff);
      FvContext.connectFv(outputBuffer, fvc.vi);
   }

   /**
    * Toggle fold at cursor line. Detects diff markers and
    * pagination sentinels for special handling: fetches diffs
    * or loads more log entries and rebuilds the buffer.
    */
   @SuppressWarnings("unchecked")
   private static void gitExpand(FvContext fvc) throws
         IOException, InputException {
      FoldModel fm = fvc.getFoldModel();
      if (fm == null || fm.isEmpty()) {
         UI.reportMessage("No folds in git log");
         return;
      }
      int line = fvc.inserty();
      // Read line text for marker detection
      TextEdit<String> buf = fvc.edvec;
      String lineText = "";
      if (line >= 1 && line <= buf.readIn()) {
         lineText = buf.at(line).toString();
      }
      // Diff show marker: first-time diff expansion
      if (lineText.startsWith(
            GitLogBuffer.DIFF_SHOW_PREFIX)) {
         String sha =
            GitLogBuffer.extractShaFromMarker(lineText);
         if (sha != null) {
            fetchAndCacheDiff(sha);
            logDiffExpanded.add(sha);
            rebuildLogBuffer(fvc, sha);
            return;
         }
      }
      // Pagination sentinel: load more entries
      if (lineText.startsWith(
            GitLogBuffer.PAGINATION_MARKER)) {
         loadMoreLogEntries();
         rebuildLogBuffer(fvc, null);
         return;
      }
      // Default: regular fold toggle
      FoldModel.FoldRange fold = fm.toggleFold(line);
      if (fold != null) {
         fvc.vi.recalcScreenRow();
         fvc.vi.redraw();
         UI.reportMessage(fold.collapsed
            ? "Folded" : "Unfolded");
      } else {
         UI.reportMessage("No fold at current line");
      }
   }

   /**
    * Open all folds and expand all diffs in the git log.
    * Fetches diffs for every commit and rebuilds the buffer.
    */
   private static void gitExpandAll(FvContext fvc) throws
         IOException, InputException {
      if (logLogLines == null || logLogLines.isEmpty()) {
         // Fallback: no log state, just open folds
         FoldModel fm = fvc.getFoldModel();
         if (fm == null || fm.isEmpty()) {
            UI.reportMessage("No folds in git log");
            return;
         }
         fm.openAll();
         fvc.vi.recalcScreenRow();
         fvc.vi.redraw();
         UI.reportMessage("Expanded all commits");
         return;
      }
      // Expand all diffs
      UI.reportMessage("Loading all diffs...");
      for (String line : logLogLines) {
         String sha = GitLogBuffer.extractSha(line);
         if (sha != null && !logDiffExpanded.contains(sha)) {
            fetchAndCacheDiff(sha);
            logDiffExpanded.add(sha);
         }
      }
      // Rebuild with everything open
      List<int[]> foldRanges = new ArrayList<>();
      List<String> formatted =
         GitLogBuffer.buildFoldedLog(
            logLogLines, logMessages, foldRanges,
            logDiffExpanded, logDiffCache);
      logBuffer = createBuffer("*git-log*", formatted);
      registerLogInPosListList(formatted);
      FvContext<?> logFvc =
         FvContext.connectFv(logBuffer, fvc.vi);
      FoldModel fm = new FoldModel();
      for (int[] range : foldRanges) {
         fm.addFold(range[0], range[1]);
      }
      fm.openAll();
      fm.setToggleHandler(GitCommands::handleLogFoldToggle);
      logFvc.setFoldModel(fm);
      UI.reportMessage("Expanded all commits with diffs");
   }

   /**
    * Close all folds in the git log.
    */
   private static void gitCollapseAll(FvContext fvc) throws
         IOException, InputException {
      FoldModel fm = fvc.getFoldModel();
      if (fm == null || fm.isEmpty()) {
         return;
      }
      fm.closeAll();
      fvc.vi.recalcScreenRow();
      fvc.vi.redraw();
      UI.reportMessage("Collapsed all");
   }

   /**
    * Rebuild the git log buffer from stored state.
    * Preserves open fold state for commits that were open
    * before the rebuild, and opens the trigger SHA's folds.
    *
    * @param fvc current view context
    * @param triggerSha SHA that triggered the rebuild, or null
    */
   @SuppressWarnings("unchecked")
   private static void rebuildLogBuffer(
         FvContext fvc, String triggerSha)
         throws IOException, InputException {
      java.util.Set<String> openShas =
         getOpenCommitShas(fvc);
      if (triggerSha != null) {
         openShas.add(triggerSha);
      }
      List<int[]> foldRanges = new ArrayList<>();
      List<String> formatted =
         GitLogBuffer.buildFoldedLog(
            logLogLines, logMessages, foldRanges,
            logDiffExpanded, logDiffCache);
      logBuffer = createBuffer("*git-log*", formatted);
      registerLogInPosListList(formatted);
      FvContext<?> logFvc =
         FvContext.connectFv(logBuffer, fvc.vi);
      FoldModel fm = new FoldModel();
      for (int[] range : foldRanges) {
         fm.addFold(range[0], range[1]);
      }
      fm.closeAll();
      // Restore open state
      for (FoldModel.FoldRange f : fm.getFolds()) {
         String lt =
            formatted.get(f.startLine - 1);
         String sha = GitLogBuffer.extractSha(lt);
         if (sha != null && openShas.contains(sha)) {
            f.collapsed = false;
         }
         if (lt.startsWith(
               GitLogBuffer.DIFF_HIDE_PREFIX)) {
            String dfSha =
               GitLogBuffer.extractShaFromMarker(lt);
            if (dfSha != null
                  && openShas.contains(dfSha)) {
               f.collapsed = false;
            }
         }
      }
      fm.setToggleHandler(GitCommands::handleLogFoldToggle);
      logFvc.setFoldModel(fm);
   }

   /**
    * Scan the current fold model for open commit folds
    * and return the set of their SHAs.
    */
   @SuppressWarnings("unchecked")
   private static java.util.Set<String> getOpenCommitShas(
         FvContext fvc) {
      java.util.Set<String> result =
         new java.util.HashSet<>();
      FoldModel fm = fvc.getFoldModel();
      if (fm == null || logBuffer == null) {
         return result;
      }
      for (FoldModel.FoldRange f : fm.getFolds()) {
         if (!f.collapsed
               && f.startLine >= 1
               && f.startLine <= logBuffer.readIn()) {
            String lt =
               logBuffer.at(f.startLine).toString();
            String sha = GitLogBuffer.extractSha(lt);
            if (sha != null) {
               result.add(sha);
            }
         }
      }
      return result;
   }

   /**
    * Fetch diff lines for a SHA and store in the cache.
    */
   private static void fetchAndCacheDiff(String sha)
         throws IOException {
      if (!logDiffCache.containsKey(sha)) {
         List<String> diff =
            GitLogBuffer.getDiffLines(sha, logDir);
         logDiffCache.put(sha, diff);
      }
   }

   /**
    * FoldToggleHandler for the git log buffer. Intercepts
    * fold toggle/open on diff markers and pagination sentinels
    * to fetch content and rebuild the buffer. Returns false
    * for regular folds so the default toggle proceeds.
    *
    * @param line the buffer line being toggled
    * @param fvc the current view context
    * @return true if handled, false for default behavior
    */
   @SuppressWarnings("unchecked")
   private static boolean handleLogFoldToggle(
         int line, FvContext<?> fvc)
         throws IOException, InputException {
      TextEdit<?> buf = fvc.edvec;
      if (line < 1 || line > buf.readIn()) {
         return false;
      }
      String lineText = buf.at(line).toString();
      if (lineText.startsWith(
            GitLogBuffer.DIFF_SHOW_PREFIX)) {
         String sha =
            GitLogBuffer.extractShaFromMarker(lineText);
         if (sha != null) {
            fetchAndCacheDiff(sha);
            logDiffExpanded.add(sha);
            rebuildLogBuffer(fvc, sha);
            return true;
         }
      }
      if (lineText.startsWith(
            GitLogBuffer.DIFF_HIDE_PREFIX)) {
         String sha =
            GitLogBuffer.extractShaFromMarker(lineText);
         if (sha != null) {
            logDiffExpanded.remove(sha);
            rebuildLogBuffer(fvc, null);
            return true;
         }
      }
      if (lineText.startsWith(
            GitLogBuffer.PAGINATION_MARKER)) {
         loadMoreLogEntries();
         rebuildLogBuffer(fvc, null);
         return true;
      }
      return false;
   }

   /**
    * Load more log entries by increasing the page size
    * and re-fetching from git.
    */
   private static void loadMoreLogEntries()
         throws IOException {
      logPageSize += 100;
      logLogLines =
         GitLogBuffer.getLogLines(logPageSize, logDir);
      logMessages =
         GitLogBuffer.getCommitMessages(logPageSize, logDir);
   }

   /**
    * Extract a commit SHA from the line at the cursor position.
    *
    * @return the SHA string, or null if none found
    */
   @SuppressWarnings("unchecked")
   private static String extractShaAtCursor(FvContext fvc) {
      TextEdit<String> buf = fvc.edvec;
      int curLine = fvc.inserty();
      if (curLine < 1 || curLine > buf.readIn()) {
         return null;
      }
      String line = buf.at(curLine).toString();
      return GitLogBuffer.extractSha(line);
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
      String sub;
      String rest;
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
