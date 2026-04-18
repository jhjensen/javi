package javi.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.io.BufferedReader;
import java.io.StringReader;

import javi.FoldModel;
import javi.FvContext;
import javi.InputException;
import javi.PosListList;
import javi.StringIoc;
import javi.TextEdit;
import javi.UI;


/**
 * Git log commands — extracted from GitCommands to
 * keep that class within checkstyle FileLength limits.
 */
final class GitLogHelper {

   private GitLogHelper() { }

   /** The git log buffer, reused across invocations. */
   static TextEdit<String> logBuffer;

   /** Working directory for git log commands. */
   static java.io.File logDir;

   /** Number of log entries to fetch per page. */
   static int logPageSize = 100;

   /** Raw graph lines from most recent git log. */
   static List<String> logLogLines;

   /** SHA-to-message map from most recent git log. */
   static Map<String, List<String>> logMessages;

   /** SHAs whose diffs are currently expanded. */
   static final java.util.Set<String> logDiffExpanded =
      new java.util.HashSet<>();

   /** Cached diff content keyed by SHA. */
   static final Map<String, List<String>> logDiffCache =
      new java.util.HashMap<>();

   /** Extra arguments for the current git log session (e.g. path filter). */
   static String[] logExtraArgs;

   /** Current git log buffer name (unique per directory). */
   static String logBufferName;

   /** Map of git log buffer names to their associated directories. */
   static final Map<String, java.io.File> logBufferDirs =
      new java.util.LinkedHashMap<>();

   /**
    * Show git log in a text buffer for in-editor navigation.
    * Graph window is disabled; use :git_show / :git_log_diff
    * to inspect individual commits.
    *
    * <p>Optional argument is appended to the git log command.
    * For example, {@code :git_log -- path/to/file} restricts
    * the log to a single file.  If no argument is given, the
    * log runs in the directory of the current file (or, when
    * the current buffer is a file-list or directory browser,
    * in the directory of the entry at the cursor).</p>
    */
   static void gitLog(Object arg, FvContext fvc) throws
         IOException, InputException {
      java.io.File dir = GitCommands.getFileDir(fvc);
      logDir = dir;
      logPageSize = 100;
      logDiffExpanded.clear();
      logDiffCache.clear();
      logExtraArgs = parseLogArgs(arg);
      logLogLines = GitLogBuffer.getLogLines(
         logPageSize, dir, logExtraArgs);
      if (!logLogLines.isEmpty()) {
         logMessages =
            GitLogBuffer.getCommitMessages(
               logPageSize, dir, logExtraArgs);
         List<int[]> foldRanges = new ArrayList<>();
         List<String> formatted =
            GitLogBuffer.buildFoldedLog(
               logLogLines, logMessages, foldRanges,
               logDiffExpanded, logDiffCache);
         String dirLabel = dir != null ? dir.getName() : "repo";
         logBufferName = "*git-log:" + dirLabel + "*";
         logBuffer = createBuffer(logBufferName, formatted);
         logBufferDirs.put(logBufferName, dir);
         registerLogInPosListList();
         FvContext<?> logFvc =
            FvContext.connectFv(logBuffer, fvc.vi);
         FoldModel fm = new FoldModel();
         for (int[] range : foldRanges) {
            fm.addFold(range[0], range[1]);
         }
         fm.closeAll();
         fm.setToggleHandler(GitLogHelper::handleLogFoldToggle);
         logFvc.setFoldModel(fm);
      }
   }

   /**
    * Parse the optional argument to {@code :git_log} into an
    * array of extra git arguments.  Splits on whitespace.
    *
    * @param arg the raw command argument, or null
    * @return array of extra args, or null if none
    */
   static String[] parseLogArgs(Object arg) {
      if (null == arg)
         return null;
      String s = arg.toString().trim();
      if (s.isEmpty())
         return null;
      return s.split("\\s+");
   }

   /**
    * Registers git log buffers in PosListList so they appear
    * in F6 for easy navigation.  Each open git log buffer
    * gets a single entry pointing to line 1 of the buffer.
    */
   static void registerLogInPosListList() {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, java.io.File> e
            : logBufferDirs.entrySet()) {
         String bName = e.getKey();
         java.io.File bDir = e.getValue();
         String label = bDir != null
            ? bDir.getPath() : "repo";
         sb.append(bName).append("(1 -git log ")
            .append(label).append(")\n");
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
   static void gitShow(FvContext fvc) throws
         IOException, InputException {
      String sha = extractShaAtCursor(fvc);
      if (null == sha) {
         throw new InputException("No commit SHA on current line");
      }
      List<String> details = GitLogBuffer.getCommitDetails(sha);
      GitCommands.outputBuffer = GitLogHelper.createBuffer("*git-show*", details);
      FvContext.connectFv(GitCommands.outputBuffer, fvc.vi);
   }

   /**
    * Show the full diff for the commit on the current line.
    * Extracts the SHA from the cursor line in the log buffer.
    */
   static void gitLogDiff(FvContext fvc) throws
         IOException, InputException {
      String sha = extractShaAtCursor(fvc);
      if (null == sha) {
         throw new InputException("No commit SHA on current line");
      }
      List<String> diff = GitLogBuffer.getCommitDiff(sha);
      GitCommands.outputBuffer = GitLogHelper.createBuffer("*git-diff*", diff);
      FvContext.connectFv(GitCommands.outputBuffer, fvc.vi);
   }

   /**
    * Toggle fold at cursor line. Detects diff markers and
    * pagination sentinels for special handling: fetches diffs
    * or loads more log entries and rebuilds the buffer.
    */
   @SuppressWarnings("unchecked")
   static void gitExpand(FvContext fvc) throws
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
   static void gitExpandAll(FvContext fvc) throws
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
      logBuffer = createBuffer(logBufferName, formatted);
      registerLogInPosListList();
      FvContext<?> logFvc =
         FvContext.connectFv(logBuffer, fvc.vi);
      FoldModel fm = new FoldModel();
      for (int[] range : foldRanges) {
         fm.addFold(range[0], range[1]);
      }
      fm.openAll();
      fm.setToggleHandler(GitLogHelper::handleLogFoldToggle);
      logFvc.setFoldModel(fm);
      UI.reportMessage("Expanded all commits with diffs");
   }

   /**
    * Close all folds in the git log.
    */
   static void gitCollapseAll(FvContext fvc) throws
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
   static void rebuildLogBuffer(
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
      logBuffer = createBuffer(logBufferName, formatted);
      registerLogInPosListList();
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
      fm.setToggleHandler(GitLogHelper::handleLogFoldToggle);
      logFvc.setFoldModel(fm);
   }

   /**
    * Scan the current fold model for open commit folds
    * and return the set of their SHAs.
    */
   @SuppressWarnings("unchecked")
   static java.util.Set<String> getOpenCommitShas(
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
   static void fetchAndCacheDiff(String sha)
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
   static boolean handleLogFoldToggle(
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
   static void loadMoreLogEntries()
         throws IOException {
      logPageSize += 100;
      logLogLines =
         GitLogBuffer.getLogLines(
            logPageSize, logDir, logExtraArgs);
      logMessages =
         GitLogBuffer.getCommitMessages(
            logPageSize, logDir, logExtraArgs);
   }

   /**
    * Extract a commit SHA from the line at the cursor position.
    *
    * @return the SHA string, or null if none found
    */
   @SuppressWarnings("unchecked")
   static String extractShaAtCursor(FvContext fvc) {
      TextEdit<String> buf = fvc.edvec;
      int curLine = fvc.inserty();
      if (curLine < 1 || curLine > buf.readIn()) {
         return null;
      }
      String line = buf.at(curLine).toString();
      return GitLogBuffer.extractSha(line);
   }

   static TextEdit<String> createBuffer(String name,
         List<String> bufLines) {
      String content = String.join("\n", bufLines);
      StringIoc sio = new StringIoc(name, content);
      return new TextEdit<>(sio, sio.prop);
   }
}
