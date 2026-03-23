package javi;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static history.Tools.trace;

/**
 * Directory browser/editor for navigating filesystem.
 *
 * <p>DirEdit provides a vim netrw-like directory browsing experience:
 * <ul>
 *   <li>Displays directory contents with metadata (size, date, permissions)</li>
 *   <li>Navigate with Enter to open files/directories</li>
 *   <li>Go to parent with '-' or Backspace</li>
 *   <li>Toggle hidden files with '.'</li>
 *   <li>Cycle sort mode with 's'</li>
 * </ul>
 *
 * <h2>Key Bindings</h2>
 * <table>
 *   <tr><th>Key</th><th>Action</th></tr>
 *   <tr><td>Enter</td><td>Edit file in javi or enter directory</td></tr>
 *   <tr><td>x</td><td>Open file with OS default application</td></tr>
 *   <tr><td>-</td><td>Go to parent directory</td></tr>
 *   <tr><td>.</td><td>Toggle hidden files</td></tr>
 *   <tr><td>s</td><td>Cycle sort mode (name/size/date/type)</td></tr>
 *   <tr><td>R</td><td>Refresh directory listing</td></tr>
 *   <tr><td>dd</td><td>Delete file under cursor</td></tr>
 *   <tr><td>yy</td><td>Yank filename to register</td></tr>
 *   <tr><td>Y</td><td>Yank full path to register</td></tr>
 *   <tr><td>q</td><td>Quit directory browser</td></tr>
 * </table>
 *
 * <h2>Ex Commands (File Operations)</h2>
 * <table>
 *   <tr><th>Command</th><th>Action</th></tr>
 *   <tr><td>{@code :diredit_delete}</td><td>Delete file under cursor</td></tr>
 *   <tr><td>{@code :diredit_rename name}</td><td>Rename file under cursor</td></tr>
 *   <tr><td>{@code :diredit_mkdir name}</td><td>Create new subdirectory</td></tr>
 *   <tr><td>{@code :diredit_newfile name}</td><td>Create new empty file</td></tr>
 *   <tr><td>{@code :diredit_copy dest}</td><td>Copy file under cursor</td></tr>
 * </table>
 *
 * <h2>Display Format</h2>
 * <pre>
 * Directory: /path/to/dir
 * drwxr-xr-x   4096  2024-01-15  subdir/
 * -rw-r--r--   1234  2024-01-10  file.txt
 * </pre>
 *
 * @see DirManager
 * @see FileList
 */
public final class DirEdit extends TextEdit<String> {

   private static final long serialVersionUID = 1;

   /** All open DirEdit instances (for search-path-change notifications). */
   private static final ArrayList<DirEdit> openInstances = new ArrayList<>();

   /** The directory being displayed. */
   private FileDescriptor.LocalDir currentDir;

   /** Whether to show hidden (dot) files. */
   private boolean showHidden = false;

   /** Current sort mode. Package-private for testing. */
   SortMode sortMode = SortMode.NAME;

   /** The directory path currently being watched (for WatchService). */
   private String watchedPath;

   /** Files marked for deletion. Package-private for testing. */
   final HashSet<String> markedForDelete = new HashSet<>();

   /** Date formatter for file dates. */
   private static final SimpleDateFormat dateFormat =
      new SimpleDateFormat("yyyy-MM-dd HH:mm");

   /** Sort modes for directory listing. */
   public enum SortMode {
      /** Sort by name. */
      NAME,
      /** Sort by file size. */
      SIZE,
      /** Sort by modification date. */
      DATE,
      /** Sort by file extension/type. */
      TYPE
   }

   /**
    * Creates a new DirEdit for the specified directory.
    * Package-private for testing.
    *
    * @param dir the directory to display
    */
   @SuppressWarnings({"unchecked", "rawtypes"})
   DirEdit(FileDescriptor.LocalDir dir) {
      super(new IoConverter(new FileProperties(
         FileDescriptor.InternalFd.make("diredit:" + dir.shortName),
         StringIoc.converter), true),
         new FileProperties(
            FileDescriptor.InternalFd.make("diredit:" + dir.shortName),
            StringIoc.converter));
      this.currentDir = dir;
      populateDirectoryImpl();
      setReadOnly(true);
      finish();
      openInstances.add(this);
   }

   @Override
   boolean handleKey(JeyEvent jEv, FvContext fvc) throws
         InputException, InterruptedException, IOException {
      char ch = jEv.getKeyChar();
      if (ch == JeyEvent.CHAR_UNDEFINED) {
         return false; // let action keys (arrows, F-keys, etc.) through
      }

      // DirEdit-specific commands
      switch (ch) {
         case 's':
            cycleSortMode(fvc);
            return true;
         case 'S':
            Rgroup.doCommand("dirmanager_toggle_searchpath", null, 0, 0,
               fvc, false);
            return true;
         case 'R':
            refresh(fvc);
            return true;
         case 'q':
            Rgroup.doCommand("gotofilelist", null, 0, 0, fvc, false);
            return true;
         case '\n': case '\r':
            openSelected(fvc);
            return true;
         case '-':
            goToParent(fvc);
            return true;
         case '.':
            toggleHidden(fvc);
            return true;
         case 'D':
            deleteSelected(fvc);
            return true;
         case 'Y':
            yankPath(fvc);
            return true;
         case 'y':
            if ('y' == EventQueue.nextKey(fvc.vi)) {
               yankFilename(fvc);
            }
            return true;
         case 'o': case 'O':
            createInline(fvc);
            return true;
         case 'x':
            openExternal(fvc);
            return true;
         case '!':
            Rgroup.doCommand("diredit_shell", null, 0, 0,
               fvc, false);
            return true;
         case 'r':
            promptRename(fvc);
            return true;
         case 'c':
            promptCopy(fvc);
            return true;
         case 'p':
            togglePermission(fvc);
            return true;
         default:
            break;
      }

      // Allow navigation keys through to mkeys/skeys dispatch
      switch (ch) {
         case 'h': case 'j': case 'k': case 'l':
         case 'H': case 'M': case 'L':
         case 'w': case 'W': case 'b': case 'B': case 'e': case 'E':
         case 'f': case 'F': case 't': case 'T':
         case 'd':
         case 'v': case 'V':
         case 'n': case 'N':
         case ';': case ',':
         case '0': case '1': case '2': case '3': case '4':
         case '5': case '6': case '7': case '8': case '9':
         case '$': case '^': case '|':
         case 'G': case '%': case '+':
         case '(': case ')': case '{': case '}': case '[': case ']':
         case 'm': case '\'':
         case '/': case '?':
         case ':': case 'z': case 'Z':
         case 'P':
         case ' ':
         case 27: // Escape
            return false;
         default:
            break;
      }

      // Allow control characters through (Ctrl-F, Ctrl-B, etc.)
      if (ch < 32 || ch == 27) {
         return false;
      }

      // Keys handled by the directory overlay keymap — let them through
      // (s, S, R, q, Enter, -, ., D, o, O are bound in the overlay)
      // Navigation keys (h,j,k,l, etc.) fall through to normal keymap

      // Block editing commands that don't apply in directory mode
      // (i, a, c, x, p, r, ~, etc.)
      switch (ch) {
         case 'i': case 'I': case 'a': case 'A':
         case 'C':
         case 'X':
         case 'P':
         case 'y': case 'Y':
         case 'J':
         case '~':
         case '>': case '<':
         case '!':
            return true;
         default:
            return false;
      }
   }

   /**
    * Opens a directory browser for the specified path.
    *
    * @param path the directory path to open
    * @param vi the view to display in
    * @return the FvContext for the directory browser
    * @throws IOException if the directory cannot be accessed
    * @throws InputException if the path is invalid
    */
   public static FvContext openDirectory(String path, View vi) throws
         IOException, InputException {
      return openDirectory(path, vi, null);
   }

   /**
    * Opens a directory browser and optionally positions the cursor
    * on a specific file.
    *
    * @param path the directory path to open
    * @param vi the view to display in
    * @param targetFile filename to position cursor on, or null
    * @return the FvContext for the directory browser
    * @throws IOException if the directory cannot be accessed
    * @throws InputException if the path is invalid
    */
   public static FvContext openDirectory(String path, View vi,
         String targetFile) throws IOException, InputException {
      FileDescriptor.LocalDir dir = FileDescriptor.LocalDir.make(path);
      if (!dir.exists() || !dir.isDirectory()) {
         throw new InputException("Not a directory: " + path);
      }
      DirEdit dirEdit = new DirEdit(dir);
      FvContext fvc = FvContext.connectFv(dirEdit, vi);
      if (null != targetFile) {
         int line = dirEdit.findLineForFilename(targetFile);
         if (line > 0) {
            fvc.cursoryabs(line);
         }
      }
      return fvc;
   }

   /**
    * Opens a directory browser for the current working directory.
    *
    * @param vi the view to display in
    * @return the FvContext for the directory browser
    * @throws IOException if the directory cannot be accessed
    * @throws InputException if the path is invalid
    */
   public static FvContext openCurrentDirectory(View vi) throws
         IOException, InputException {
      return openDirectory(".", vi);
   }

   /**
    * Populates the buffer with directory contents.
    * Temporarily disables read-only mode to allow buffer modification
    * during refresh operations.
    */
   void populateDirectory() {
      // Temporarily allow writes for repopulation
      setReadOnly(false);
      try {
         populateDirectoryImpl();
      } finally {
         setReadOnly(true);
      }
   }

   /**
    * Internal implementation of directory population.
    */
   private void populateDirectoryImpl() {
      // Unwatch previous directory if changed
      String newPath = currentDir.fh.getAbsolutePath();
      if (null != watchedPath && !watchedPath.equals(newPath)) {
         DirSizeCalculator.unwatchDirectory(watchedPath, this);
      }
      watchedPath = newPath;

      // Clear existing content
      if (readIn() > 1) {
         remove(1, readIn());
      }

      ArrayList<String> lines = new ArrayList<>();

      // Add header
      lines.add("  Directory: " + currentDir.shortName);
      lines.add("");

      // Get directory contents
      File dirFile = currentDir.fh;
      File[] files = dirFile.listFiles();

      if (null == files) {
         lines.add("  (Unable to read directory)");
      } else {
         // Filter and sort
         ArrayList<File> fileList = new ArrayList<>();
         for (File f : files) {
            if (showHidden || !f.getName().startsWith(".")) {
               fileList.add(f);
            }
         }

         // Sort the files
         sortFiles(fileList);

         // Add parent directory entry if not root
         // Use canonical path to resolve "." segments properly
         File resolved;
         try {
            resolved = dirFile.getCanonicalFile();
         } catch (IOException e) {
            resolved = dirFile.getAbsoluteFile();
         }
         File parent = resolved.getParentFile();
         if (null != parent) {
            lines.add(formatEntry(parent, true));
         }

         // Add entries
         for (File f : fileList) {
            lines.add(formatEntry(f, false));
         }

         // Submit background size calculations for directories
         for (File f : fileList) {
            if (f.isDirectory()) {
               DirSizeCalculator.submitCalculation(
                  f.getAbsolutePath(), this);
            }
         }
         // Don't calculate size for parent ".." — only recurse down

         // Register WatchService for auto-updates on filesystem changes
         DirSizeCalculator.watchDirectory(
            dirFile.getAbsolutePath(), this);
      }

      // Add help footer
      lines.add("");
      lines.add("  [Enter] edit  [-] parent  [.] hidden  [s] sort:"
         + sortMode.name().toLowerCase()
         + "  [R] refresh  [q] quit");
      lines.add("  [dd] delete/trash  [o] new file/dir  [x] open"
         + "  [S] search path  [!] shell");
      lines.add("  [r] rename  [c] copy  [p] permissions"
         + "  [yy] yank name  [Y] yank path");

      // Insert all lines
      insertStrings(lines, 1);
   }

   /**
    * Sorts the file list according to current sort mode.
    *
    * @param files the list of files to sort
    */
   private void sortFiles(ArrayList<File> files) {
      Comparator<File> secondary;

      switch (sortMode) {
         case SIZE:
            secondary = (f1, f2) -> {
               long s1 = getEffectiveSize(f1);
               long s2 = getEffectiveSize(f2);
               return Long.compare(s1, s2);
            };
            break;
         case DATE:
            secondary = (f1, f2) ->
               Long.compare(f1.lastModified(), f2.lastModified());
            break;
         case TYPE:
            secondary = (f1, f2) -> {
               String ext1 = getExtension(f1.getName());
               String ext2 = getExtension(f2.getName());
               int extCmp = ext1.compareToIgnoreCase(ext2);
               if (extCmp != 0) {
                  return extCmp;
               }
               return f1.getName().compareToIgnoreCase(f2.getName());
            };
            break;
         case NAME:
         default:
            secondary = (f1, f2) ->
               f1.getName().compareToIgnoreCase(f2.getName());
            break;
      }

      // For TYPE sort, directories come first (no extension);
      // for other modes, sort uniformly regardless of type.
      Comparator<File> comparator;
      if (sortMode == SortMode.TYPE) {
         comparator = (f1, f2) -> {
            if (f1.isDirectory() != f2.isDirectory()) {
               return f1.isDirectory() ? -1 : 1;
            }
            return secondary.compare(f1, f2);
         };
      } else {
         comparator = secondary;
      }

      files.sort(comparator);
   }

   /**
    * Returns the effective size for sorting — cached recursive size
    * for directories, file.length() for regular files.
    *
    * @param file the file
    * @return the size in bytes
    */
   private long getEffectiveSize(File file) {
      if (file.isDirectory()) {
         Long cached = DirSizeCalculator.getCachedSize(
            file.getAbsolutePath());
         return (null != cached) ? cached : 0L;
      }
      return file.length();
   }

   /**
    * Gets the file extension from a filename.
    *
    * @param name the filename
    * @return the extension (without dot) or empty string
    */
   private String getExtension(String name) {
      int dotIdx = name.lastIndexOf('.');
      if (dotIdx > 0 && dotIdx < name.length() - 1) {
         return name.substring(dotIdx + 1);
      }
      return "";
   }

   /**
    * Formats a file entry for display.
    *
    * @param file the file to format
    * @param isParent true if this is the parent directory entry
    * @return the formatted line
    */
   private String formatEntry(File file, boolean isParent) {
      StringBuilder sb = new StringBuilder();

      // Show mark flag if file is marked
      String name = file.getName();
      if (!isParent && markedForDelete.contains(name)) {
         sb.append("D ");
      } else if (!isParent && file.isDirectory()
            && DirManager.getInstance().isInSearchPath(file)) {
         sb.append("S ");
      } else {
         sb.append("  ");
      }

      // Permissions (simplified)
      if (file.isDirectory()) {
         sb.append("d");
      } else {
         sb.append("-");
      }
      sb.append(file.canRead() ? "r" : "-");
      sb.append(file.canWrite() ? "w" : "-");
      sb.append(file.canExecute() ? "x" : "-");

      // Size (right-aligned in 10 chars)
      String size;
      if (file.isDirectory()) {
         Long cached = DirSizeCalculator.getCachedSize(
            file.getAbsolutePath());
         if (null != cached) {
            size = formatSize(cached);
         } else {
            size = "...";
         }
      } else {
         size = formatSize(file.length());
      }
      sb.append(String.format("  %10s", size));

      // Date
      sb.append("  ");
      sb.append(dateFormat.format(new Date(file.lastModified())));

      // Name
      sb.append("  ");
      if (isParent) {
         sb.append("../");
      } else {
         sb.append(file.getName());
         if (file.isDirectory()) {
            sb.append("/");
         }
      }

      return sb.toString();
   }

   /**
    * Formats a file size for human-readable display.
    *
    * @param size the size in bytes
    * @return formatted size string
    */
   private String formatSize(long size) {
      if (size < 1024) {
         return size + "B";
      } else if (size < 1024 * 1024) {
         return String.format("%.1fK", size / 1024.0);
      } else if (size < 1024 * 1024 * 1024) {
         return String.format("%.1fM", size / (1024.0 * 1024));
      } else {
         return String.format("%.1fG", size / (1024.0 * 1024 * 1024));
      }
   }

   /**
    * Gets the filename from the current line.
    *
    * <p>Line format after mark prefix (2 chars: "  " or "D "):
    * {@code [d-]rwx  <size>  <date>  <name>}
    *
    * @param lineNum the line number
    * @return the filename, or null if not a file entry line
    */
   String getFilename(int lineNum) {
      if (lineNum < 1 || lineNum >= readIn()) {
         return null;
      }
      String line = at(lineNum).toString();

      // Skip blank or very short lines
      if (line.length() < 3) {
         return null;
      }

      // Skip header ("  Directory: ...") line
      if (line.contains("Directory:")) {
         return null;
      }

      // Skip help footer lines ("  [Enter]..." or "  [dd]...")
      if (line.contains("  [")) {
         return null;
      }

      // File entry lines have a 2-char mark prefix followed by
      // a permission character (d, -, l) at position 2
      char permChar = line.charAt(2);
      if (permChar != 'd' && permChar != '-' && permChar != 'l') {
         return null;
      }

      // Parse filename from end of line (after last "  " separator)
      // Format: "<mark><perm>  <size>  <date>  <name>"
      int lastSpace = line.lastIndexOf("  ");
      if (lastSpace >= 0) {
         String name = line.substring(lastSpace + 2);
         if (name.isEmpty()) {
            return null;
         }
         return name;
      }

      return null;
   }

   /**
    * Returns the full filesystem path for the entry at the given line.
    *
    * @param lineNum the line number
    * @return the full path, or null if not a file entry line
    */
   String getFullPath(int lineNum) {
      String filename = getFilename(lineNum);
      if (null == filename) {
         return null;
      }
      if ("../".equals(filename)) {
         File parent = currentDir.fh.getAbsoluteFile().getParentFile();
         return (null != parent) ? parent.getAbsolutePath() : null;
      }
      String name = filename.endsWith("/")
         ? filename.substring(0, filename.length() - 1)
         : filename;
      return new File(currentDir.fh, name).getAbsolutePath();
   }

   /**
    * Finds the line number displaying the given filename.
    * Matches against the bare name (without trailing slash for dirs).
    *
    * @param targetFile the filename to find
    * @return the 1-based line number, or -1 if not found
    */
   int findLineForFilename(String targetFile) {
      if (null == targetFile) {
         return -1;
      }
      String target = targetFile.endsWith("/")
         ? targetFile.substring(0, targetFile.length() - 1)
         : targetFile;
      int size = readIn();
      for (int i = 1; i < size; i++) {
         String fn = getFilename(i);
         if (null == fn) {
            continue;
         }
         String bare = fn.endsWith("/")
            ? fn.substring(0, fn.length() - 1)
            : fn;
         if (bare.equals(target)) {
            return i;
         }
      }
      return -1;
   }

   /**
    * Yanks the full path of the file under the cursor to the yank
    * register and system clipboard ({@code Y} key in DirEdit).
    *
    * @param fvc the current FvContext
    */
   void yankPath(FvContext fvc) {
      String path = getFullPath(fvc.inserty());
      if (null != path) {
         Buffers.deleted('0', path);
         UI.reportMessage("Yanked path: " + path);
      }
   }

   /**
    * Yanks just the filename (no directory) under the cursor to the
    * yank register and system clipboard ({@code yy} in DirEdit).
    *
    * @param fvc the current FvContext
    */
   void yankFilename(FvContext fvc) {
      String filename = getFilename(fvc.inserty());
      if (null == filename) {
         return;
      }
      // Strip trailing slash from directory names
      String name = filename.endsWith("/")
         ? filename.substring(0, filename.length() - 1)
         : filename;
      Buffers.deleted('0', name);
      UI.reportMessage("Yanked: " + name);
   }

   /**
    * Opens the selected file or enters the selected directory.
    *
    * @param fvc the current FvContext
    * @throws IOException if file cannot be opened
    * @throws InputException if path is invalid
    */
   void openSelected(FvContext fvc) throws IOException, InputException {
      int lineNum = fvc.inserty();
      String filename = getFilename(lineNum);

      if (null == filename) {
         return;
      }

      // Handle parent directory
      if ("../".equals(filename)) {
         goToParent(fvc);
         return;
      }

      // Build full path
      String path;
      if (filename.endsWith("/")) {
         // Directory - remove trailing slash for path construction
         String dirName = filename.substring(0, filename.length() - 1);
         path = currentDir.shortName + File.separator + dirName;
         // Navigate into directory
         FileDescriptor.LocalDir newDir = FileDescriptor.LocalDir.make(path);
         if (newDir.exists() && newDir.isDirectory()) {
            currentDir = newDir;
            populateDirectory();
            fvc.cursoryabs(3); // Position on first entry
         }
      } else {
         // File - open it
         path = currentDir.shortName + File.separator + filename;
         FileList.openFileName(path, fvc.vi);
      }
   }

   /**
    * Opens the selected file with the OS default application.
    * Delegates to the {@link UI} abstraction so that non-GUI
    * environments can provide a stub implementation.
    *
    * @param fvc the current FvContext
    * @throws InputException if no file is under the cursor or open fails
    */
   void openExternal(FvContext fvc) throws InputException {
      String path = getFullPath(fvc.inserty());
      if (null == path) {
         throw new InputException("No file under cursor");
      }
      try {
         UI.openFile(new File(path));
      } catch (IOException e) {
         throw new InputException("Failed to open: " + e.getMessage());
      }
   }

   /**
    * Navigates to the parent directory.
    *
    * @param fvc the current FvContext
    */
   void goToParent(FvContext fvc) {
      try {
         // Use getCanonicalFile() to resolve "." and ".." segments
         // before getting parent. getAbsoluteFile() leaves "." in the
         // path so parent of "/X/Y/." is "/X/Y" (same directory).
         File resolved = currentDir.fh.getCanonicalFile();
         File parent = resolved.getParentFile();
         if (null != parent) {
            currentDir = FileDescriptor.LocalDir.make(parent.getPath());
            populateDirectory();
            fvc.cursoryabs(3); // Position on first entry
         }
      } catch (IOException e) {
         trace("DirEdit: failed to resolve parent: " + e);
      }
   }

   /**
    * Toggles display of hidden files.
    *
    * @param fvc the current FvContext
    */
   void toggleHidden(FvContext fvc) {
      showHidden = !showHidden;
      populateDirectory();
      UI.reportMessage("Hidden files: " + (showHidden ? "shown" : "hidden"));
   }

   /**
    * Cycles through sort modes.
    *
    * @param fvc the current FvContext
    */
   void cycleSortMode(FvContext fvc) {
      SortMode[] modes = SortMode.values();
      int nextIdx = (sortMode.ordinal() + 1) % modes.length;
      sortMode = modes[nextIdx];
      populateDirectory();
   }

   /**
    * Refreshes the directory listing.
    *
    * @param fvc the current FvContext
    */
   void refresh(FvContext fvc) {
      DirSizeCalculator.clearCache();
      populateDirectory();
      UI.reportMessage("Directory refreshed");
   }

   /**
    * Prompts for a name and creates a file or directory inline.
    * If the entered name ends with {@code /}, creates a directory;
    * otherwise creates an empty file.
    *
    * @param fvc the current FvContext
    * @throws InputException if creation fails
    * @throws IOException if an I/O error occurs
    */
   void createInline(FvContext fvc) throws InputException, IOException {
      String prompt = "New: ";
      String input = InsertBuffer.getcomline(prompt);
      String name = input.substring(prompt.length()).trim();
      if (name.isEmpty()) {
         return;
      }
      if (name.endsWith("/")) {
         createDirectory(fvc, name.substring(0, name.length() - 1));
      } else {
         createFile(fvc, name);
      }
   }

   /**
    * Prompts for a new name and renames the file under the cursor.
    * Bound to 'r' key in the directory browser.
    *
    * @param fvc the current FvContext
    * @throws InputException if the cursor is not on a file entry
    * @throws IOException if I/O fails
    */
   void promptRename(FvContext fvc) throws InputException, IOException {
      String filename = getFilename(fvc.inserty());
      if (null == filename || "../".equals(filename)) {
         return;
      }
      String base = filename.endsWith("/")
         ? filename.substring(0, filename.length() - 1)
         : filename;
      String prompt = "Rename " + base + " to: ";
      String input = InsertBuffer.getcomline(prompt);
      String newName = input.substring(prompt.length()).trim();
      if (!newName.isEmpty()) {
         renameSelected(fvc, newName);
      }
   }

   /**
    * Prompts for a destination and copies the file under the cursor.
    * Bound to 'c' key in the directory browser.
    *
    * @param fvc the current FvContext
    * @throws InputException if the cursor is not on a file entry
    * @throws IOException if I/O fails
    */
   void promptCopy(FvContext fvc) throws InputException, IOException {
      String filename = getFilename(fvc.inserty());
      if (null == filename || "../".equals(filename)) {
         return;
      }
      String base = filename.endsWith("/")
         ? filename.substring(0, filename.length() - 1)
         : filename;
      String prompt = "Copy " + base + " to: ";
      String input = InsertBuffer.getcomline(prompt);
      String dest = input.substring(prompt.length()).trim();
      if (!dest.isEmpty()) {
         copySelected(fvc, dest);
      }
   }

   /**
    * Cycles through permission toggles for the file under the cursor.
    * Shows current permissions and lets user select which to toggle.
    * Bound to 'p' key in the directory browser.
    *
    * @param fvc the current FvContext
    * @throws InputException if the cursor is not on a file entry
    */
   void togglePermission(FvContext fvc) throws InputException {
      String filename = getFilename(fvc.inserty());
      if (null == filename || "../".equals(filename)) {
         return;
      }
      String base = filename.endsWith("/")
         ? filename.substring(0, filename.length() - 1)
         : filename;
      File target = new File(currentDir.fh, base);
      if (!target.exists()) {
         throw new InputException("File not found: " + base);
      }
      Path path = target.toPath();
      try {
         Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
         String current = permString(perms);
         String prompt = base + " [" + current + "] toggle (r/w/x/R/W/X): ";
         String input = InsertBuffer.getcomline(prompt);
         String choice = input.substring(prompt.length()).trim();
         if (choice.isEmpty()) {
            return;
         }
         applyPermissionToggle(perms, choice.charAt(0));
         Files.setPosixFilePermissions(path, perms);
         populateDirectory();
         String updated = permString(
            Files.getPosixFilePermissions(path));
         UI.reportMessage(base + ": " + updated);
      } catch (UnsupportedOperationException e) {
         throw new InputException("Permissions not supported");
      } catch (IOException e) {
         throw new InputException("Cannot change permissions: "
            + e.getMessage());
      }
   }

   /** Permission bits in standard rwxrwxrwx order. */
   private static final PosixFilePermission[] PERM_ORDER = {
      PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
      PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
      PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE,
      PosixFilePermission.OTHERS_EXECUTE,
   };
   private static final String PERM_CHARS = "rwxrwxrwx";

   /** Builds a Unix-style permission string (e.g. "rwxr-xr--"). */
   private static String permString(Set<PosixFilePermission> perms) {
      char[] buf = new char[9];
      for (int i = 0; i < 9; i++)
         buf[i] = perms.contains(PERM_ORDER[i]) ? PERM_CHARS.charAt(i) : '-';
      return new String(buf);
   }

   /** Toggle key mapping: r/w/x = owner, R/W/X = group, +/- = all exec. */
   private static void applyPermissionToggle(
         Set<PosixFilePermission> perms, char ch) {
      String keys = "rwxRWX";
      int idx = keys.indexOf(ch);
      if (idx >= 0) {
         PosixFilePermission p = PERM_ORDER[idx];
         if (perms.contains(p)) perms.remove(p); else perms.add(p);
      } else if (ch == '+') {
         perms.add(PERM_ORDER[2]); perms.add(PERM_ORDER[5]);
         perms.add(PERM_ORDER[8]);
      } else if (ch == '-') {
         perms.remove(PERM_ORDER[2]); perms.remove(PERM_ORDER[5]);
         perms.remove(PERM_ORDER[8]);
      }
   }

   /**
    * Deletes the file or directory under the cursor.
    * Uses system Trash when available; falls back to recursive delete
    * with confirmation for non-empty directories.
    *
    * @param fvc the current FvContext
    * @throws InputException if the cursor is not on a file entry
    */
   void deleteSelected(FvContext fvc) throws InputException {
      int lineNum = fvc.inserty();
      String filename = getFilename(lineNum);
      if (null == filename) {
         throw new InputException("No file under cursor");
      }
      if ("../".equals(filename)) {
         throw new InputException("Cannot delete parent directory reference");
      }

      String name = filename.endsWith("/")
         ? filename.substring(0, filename.length() - 1)
         : filename;
      File target = new File(currentDir.fh, name);

      if (!target.exists()) {
         throw new InputException("File not found: " + name);
      }

      // Skip confirmation for trash (recoverable); prompt only for
      // permanent delete (walkFileTree fallback)
      if (!trashSupported()) {
         String prompt = target.isDirectory() && hasChildren(target)
            ? "Permanently delete " + name + " and all contents? (y/n)"
            : "Delete " + name + "? (y/n)";
         UI.reportMessage(prompt);
         char confirm = EventQueue.nextKey(fvc.vi);
         if (confirm != 'y' && confirm != 'Y') {
            UI.reportMessage("Delete cancelled");
            return;
         }
      }

      if (!removeFile(target)) {
         throw new InputException("Failed to delete: " + name);
      }

      String verb = trashSupported() ? "Trashed" : "Deleted";
      UI.reportMessage(verb + ": " + name);
      trace("DirEdit: " + verb.toLowerCase() + " "
         + target.getAbsolutePath());
      markedForDelete.remove(name);
      DirSizeCalculator.invalidate(currentDir.fh.getAbsolutePath());
      populateDirectory();
      // Keep cursor in bounds (last valid line is readIn()-1)
      if (fvc.inserty() >= readIn()) {
         fvc.cursoryabs(readIn() - 1);
      }
   }

   /**
    * Renames the file under the cursor.
    *
    * @param fvc the current FvContext
    * @param newName the new filename (just the name, not a path)
    * @throws InputException if the cursor is not on a file entry,
    *    the new name is invalid, or the rename fails
    */
   void renameSelected(FvContext fvc, String newName)
         throws InputException {
      int lineNum = fvc.inserty();
      String filename = getFilename(lineNum);
      if (null == filename) {
         throw new InputException("No file under cursor");
      }
      if ("../".equals(filename)) {
         throw new InputException("Cannot rename parent directory reference");
      }

      if (null == newName || newName.trim().isEmpty()) {
         throw new InputException("New name required: :diredit_rename <name>");
      }

      newName = newName.trim();

      // Reject names containing path separators
      if (newName.contains("/") || newName.contains("\\")) {
         throw new InputException(
            "New name must not contain path separators: " + newName);
      }

      String oldName = filename.endsWith("/")
         ? filename.substring(0, filename.length() - 1)
         : filename;
      File source = new File(currentDir.fh, oldName);
      File dest = new File(currentDir.fh, newName);

      if (!source.exists()) {
         throw new InputException("File not found: " + oldName);
      }
      if (dest.exists()) {
         throw new InputException("Already exists: " + newName);
      }

      if (!source.renameTo(dest)) {
         throw new InputException(
            "Failed to rename: " + oldName + " -> " + newName);
      }

      UI.reportMessage("Renamed: " + oldName + " -> " + newName);
      trace("DirEdit: renamed " + source.getAbsolutePath()
         + " -> " + dest.getAbsolutePath());
      DirSizeCalculator.invalidate(currentDir.fh.getAbsolutePath());
      populateDirectory();
   }

   /**
    * Creates a new subdirectory in the current directory.
    *
    * @param fvc the current FvContext
    * @param name the directory name to create
    * @throws InputException if the name is invalid or creation fails
    */
   void createDirectory(FvContext fvc, String name) throws InputException {
      if (null == name || name.trim().isEmpty()) {
         throw new InputException(
            "Directory name required: :diredit_mkdir <name>");
      }

      name = name.trim();

      if (name.contains("/") || name.contains("\\")) {
         throw new InputException(
            "Name must not contain path separators: " + name);
      }

      File newDir = new File(currentDir.fh, name);

      if (newDir.exists()) {
         throw new InputException("Already exists: " + name);
      }

      if (!newDir.mkdir()) {
         throw new InputException("Failed to create directory: " + name);
      }

      UI.reportMessage("Created directory: " + name);
      trace("DirEdit: created directory " + newDir.getAbsolutePath());
      DirSizeCalculator.invalidate(currentDir.fh.getAbsolutePath());
      populateDirectory();
   }

   /**
    * Creates a new empty file in the current directory.
    *
    * @param fvc the current FvContext
    * @param name the file name to create
    * @throws InputException if the name is invalid or creation fails
    * @throws IOException if an I/O error occurs
    */
   void createFile(FvContext fvc, String name)
         throws InputException, IOException {
      if (null == name || name.trim().isEmpty()) {
         throw new InputException(
            "File name required: :diredit_newfile <name>");
      }

      name = name.trim();

      if (name.contains("/") || name.contains("\\")) {
         throw new InputException(
            "Name must not contain path separators: " + name);
      }

      File newFile = new File(currentDir.fh, name);

      if (newFile.exists()) {
         throw new InputException("Already exists: " + name);
      }

      if (!newFile.createNewFile()) {
         throw new InputException("Failed to create file: " + name);
      }

      UI.reportMessage("Created file: " + name);
      trace("DirEdit: created file " + newFile.getAbsolutePath());
      DirSizeCalculator.invalidate(currentDir.fh.getAbsolutePath());
      populateDirectory();
   }

   /**
    * Copies the file under the cursor to a new name.
    *
    * @param fvc the current FvContext
    * @param destName the destination filename (in the same directory)
    * @throws InputException if the cursor is not on a file entry,
    *    the destination is invalid, or the copy fails
    * @throws IOException if an I/O error occurs during copy
    */
   void copySelected(FvContext fvc, String destName)
         throws InputException, IOException {
      int lineNum = fvc.inserty();
      String filename = getFilename(lineNum);
      if (null == filename) {
         throw new InputException("No file under cursor");
      }
      if ("../".equals(filename)) {
         throw new InputException(
            "Cannot copy parent directory reference");
      }

      if (null == destName || destName.trim().isEmpty()) {
         throw new InputException(
            "Destination required: :diredit_copy <name>");
      }

      destName = destName.trim();

      String srcName = filename.endsWith("/")
         ? filename.substring(0, filename.length() - 1)
         : filename;
      File source = new File(currentDir.fh, srcName);
      File dest = new File(currentDir.fh, destName);

      if (!source.exists()) {
         throw new InputException("File not found: " + srcName);
      }
      if (source.isDirectory()) {
         throw new InputException(
            "Cannot copy directories: " + srcName);
      }
      if (dest.exists()) {
         throw new InputException("Already exists: " + destName);
      }

      Files.copy(source.toPath(), dest.toPath(),
         StandardCopyOption.COPY_ATTRIBUTES);

      UI.reportMessage("Copied: " + srcName + " -> " + destName);
      trace("DirEdit: copied " + source.getAbsolutePath()
         + " -> " + dest.getAbsolutePath());
      DirSizeCalculator.invalidate(currentDir.fh.getAbsolutePath());
      populateDirectory();
   }

   /**
    * Marks or unmarks the file under the cursor for deletion.
    * Marked files display a 'D' flag at the start of the line.
    *
    * @param fvc the current FvContext
    * @throws InputException if the cursor is not on a file entry
    */
   void toggleDeleteMark(FvContext fvc) throws InputException {
      int lineNum = fvc.inserty();
      String filename = getFilename(lineNum);
      if (null == filename) {
         throw new InputException("No file under cursor");
      }
      if ("../".equals(filename)) {
         throw new InputException(
            "Cannot mark parent directory reference");
      }

      String name = filename.endsWith("/")
         ? filename.substring(0, filename.length() - 1)
         : filename;

      if (markedForDelete.contains(name)) {
         markedForDelete.remove(name);
         UI.reportMessage("Unmarked: " + name);
      } else {
         markedForDelete.add(name);
         UI.reportMessage("Marked for delete: " + name);
      }
      populateDirectory();
   }

   /**
    * Executes all pending file operations (currently: deletions).
    * Uses system Trash when available; falls back to recursive delete.
    * Clears marks after execution. Reports results.
    *
    * @param fvc the current FvContext
    * @throws InputException if no marks are set
    */
   void executeMarks(FvContext fvc) throws InputException {
      if (markedForDelete.isEmpty()) {
         throw new InputException("No files marked for deletion");
      }

      int deleted = 0;
      int failed = 0;
      ArrayList<String> errors = new ArrayList<>();

      for (String name : new ArrayList<>(markedForDelete)) {
         File target = new File(currentDir.fh, name);
         if (!target.exists()) {
            markedForDelete.remove(name);
            continue;
         }
         if (removeFile(target)) {
            markedForDelete.remove(name);
            deleted++;
            trace("DirEdit: batch deleted " + target.getAbsolutePath());
         } else {
            errors.add(name + " (delete failed)");
            failed++;
         }
      }

      StringBuilder msg = new StringBuilder();
      String verb = trashSupported() ? "Trashed" : "Deleted";
      msg.append(verb).append(" ").append(deleted).append(" file(s)");
      if (failed > 0) {
         msg.append(", ").append(failed).append(" failed: ");
         msg.append(String.join(", ", errors));
      }
      UI.reportMessage(msg.toString());
      DirSizeCalculator.invalidate(currentDir.fh.getAbsolutePath());
      populateDirectory();
      if (fvc.inserty() >= readIn()) {
         fvc.cursoryabs(readIn() - 1);
      }
   }

   /**
    * Checks whether the system Trash is available.
    *
    * @return true if the OS trash/recycle bin is supported
    */
   static boolean trashSupported() {
      return UI.trashSupported();
   }

   /**
    * Checks whether a directory has any children.
    *
    * @param dir the directory to check
    * @return true if the directory is non-empty
    */
   private static boolean hasChildren(File dir) {
      String[] children = dir.list();
      return null != children && children.length > 0;
   }

   /**
    * Removes a file or directory, using system Trash when available.
    * Falls back to recursive delete for directories when Trash is
    * not supported.
    *
    * @param target the file or directory to remove
    * @return true if successfully removed
    */
   static boolean removeFile(File target) {
      if (trashSupported()) {
         return UI.moveToTrash(target);
      }
      if (target.isDirectory()) {
         return deleteRecursively(target);
      }
      return target.delete();
   }

   /**
    * Recursively deletes a directory tree.
    *
    * @param target the directory to delete
    * @return true if the entire tree was deleted
    */
   private static boolean deleteRecursively(File target) {
      try {
         Files.walkFileTree(target.toPath(),
            new SimpleFileVisitor<Path>() {
               @Override
               public FileVisitResult visitFile(Path file,
                     BasicFileAttributes attrs) throws IOException {
                  Files.delete(file);
                  return FileVisitResult.CONTINUE;
               }

               @Override
               public FileVisitResult postVisitDirectory(Path dir,
                     IOException exc) throws IOException {
                  Files.delete(dir);
                  return FileVisitResult.CONTINUE;
               }
            });
         return true;
      } catch (IOException e) {
         trace("DirEdit: recursive delete failed: " + e.getMessage());
         return false;
      }
   }

   /**
    * Returns the current directory.
    *
    * @return the current directory
    */
   public FileDescriptor.LocalDir getCurrentDir() {
      return currentDir;
   }

   /**
    * Refresh all open DirEdit instances.
    * Called by DirManager when the search path changes so that
    * [S] markers are updated immediately.
    */
   static void notifySearchPathChanged() {
      for (DirEdit de : openInstances) {
         de.populateDirectory();
      }
   }

   /**
    * Delete files across a range of lines (for V-mode selection).
    *
    * @param startLine first line of the range
    * @param endLine last line of the range (inclusive)
    * @param fvc the current FvContext
    * @throws InputException if confirmation is declined
    */
   void deleteRange(int startLine, int endLine, FvContext fvc)
         throws InputException {
      ArrayList<String> names = new ArrayList<>();
      for (int i = startLine; i <= endLine; i++) {
         String fn = getFilename(i);
         if (null != fn && !"../".equals(fn)) {
            names.add(fn.endsWith("/")
               ? fn.substring(0, fn.length() - 1) : fn);
         }
      }
      if (names.isEmpty()) {
         throw new InputException("No files in selection");
      }

      // Skip confirmation for trash (recoverable); prompt only for
      // permanent delete (walkFileTree fallback)
      if (!trashSupported()) {
         UI.reportMessage(
            "Permanently delete " + names.size() + " file(s)? (y/n)");
         char confirm = EventQueue.nextKey(fvc.vi);
         if (confirm != 'y' && confirm != 'Y') {
            UI.reportMessage("Delete cancelled");
            return;
         }
      }

      int deleted = 0;
      int failed = 0;
      for (String name : names) {
         File target = new File(currentDir.fh, name);
         if (removeFile(target)) {
            deleted++;
            trace("DirEdit: range-deleted " + target.getAbsolutePath());
         } else {
            failed++;
         }
      }

      String verb = trashSupported() ? "Trashed" : "Deleted";
      StringBuilder msg = new StringBuilder(verb + " ");
      msg.append(deleted).append(" file(s)");
      if (failed > 0)
         msg.append(", ").append(failed).append(" failed");
      UI.reportMessage(msg.toString());
      populateDirectory();
      if (fvc.inserty() >= readIn())
         fvc.cursoryabs(readIn() - 1);
   }

   /**
    * Command group for directory editing commands.
    */
   public static final class Commands extends Rgroup {

      /** Singleton instance. */
      private static Commands instance;

      /** Command names array for registration. */
      private static final String[] RNAMES = {
         "",
         "diredit",           // 1 - open directory editor
         "diredit_open",      // 2 - open file/directory under cursor
         "diredit_parent",    // 3 - go to parent directory
         "diredit_hidden",    // 4 - toggle hidden files
         "diredit_sort",      // 5 - cycle sort mode
         "diredit_refresh",   // 6 - refresh listing
         "diredit_quit",      // 7 - quit directory editor
         "diredit_delete",    // 8 - delete file under cursor
         "diredit_rename",    // 9 - rename file under cursor
         "diredit_mkdir",     // 10 - create new directory
         "diredit_newfile",   // 11 - create new empty file
         "diredit_copy",      // 12 - copy file under cursor
         "diredit_mark",      // 13 - toggle delete mark
         "diredit_execute",   // 14 - execute marked operations
         "dirmanager_toggle_searchpath", // 15 - toggle search path
         "diredit_shell",    // 16 - open shell in current directory
         "diredit_create",   // 17 - inline create (prompts file or dir)
      };

      /**
       * Creates the commands and registers them.
       */
      Commands() {
         register(RNAMES);
         instance = this;
      }

      /**
       * Gets the singleton instance, creating if needed.
       *
       * @return the Commands instance
       */
      public static Commands getInstance() {
         if (null == instance) {
            new Commands();
         }
         return instance;
      }

      @Override
      public Object doroutine(int rnum, Object arg, int count, int rcount,
            FvContext fvc, boolean dotmode) throws
            IOException, InputException, InterruptedException {

         switch (rnum) {
            case 1: // diredit - open directory editor
               String path = (null != arg) ? arg.toString() : ".";
               return DirEdit.openDirectory(path, fvc.vi);

            case 2: // diredit_open
               if (fvc.edvec instanceof DirEdit) {
                  ((DirEdit) fvc.edvec).openSelected(fvc);
               }
               return null;

            case 3: // diredit_parent
               if (fvc.edvec instanceof DirEdit) {
                  ((DirEdit) fvc.edvec).goToParent(fvc);
               }
               return null;

            case 4: // diredit_hidden
               if (fvc.edvec instanceof DirEdit) {
                  ((DirEdit) fvc.edvec).toggleHidden(fvc);
               }
               return null;

            case 5: // diredit_sort
               if (fvc.edvec instanceof DirEdit) {
                  ((DirEdit) fvc.edvec).cycleSortMode(fvc);
               }
               return null;

            case 6: // diredit_refresh
               if (fvc.edvec instanceof DirEdit) {
                  ((DirEdit) fvc.edvec).refresh(fvc);
               }
               return null;

            case 7: // diredit_quit
               // Switch to file list (same as F2)
               return Rgroup.doCommand("gotofilelist", null, 0, 0,
                  fvc, false);

            case 8: // diredit_delete
               if (fvc.edvec instanceof DirEdit) {
                  ((DirEdit) fvc.edvec).deleteSelected(fvc);
               }
               return null;

            case 9: // diredit_rename
               if (fvc.edvec instanceof DirEdit) {
                  String newName = (null != arg) ? arg.toString() : null;
                  ((DirEdit) fvc.edvec).renameSelected(fvc, newName);
               }
               return null;

            case 10: // diredit_mkdir
               if (fvc.edvec instanceof DirEdit) {
                  String dirName = (null != arg) ? arg.toString() : null;
                  ((DirEdit) fvc.edvec).createDirectory(fvc, dirName);
               }
               return null;

            case 11: // diredit_newfile
               if (fvc.edvec instanceof DirEdit) {
                  String fileName = (null != arg) ? arg.toString() : null;
                  ((DirEdit) fvc.edvec).createFile(fvc, fileName);
               }
               return null;

            case 12: // diredit_copy
               if (fvc.edvec instanceof DirEdit) {
                  String destPath = (null != arg) ? arg.toString() : null;
                  ((DirEdit) fvc.edvec).copySelected(fvc, destPath);
               }
               return null;

            case 13: // diredit_mark
               if (fvc.edvec instanceof DirEdit) {
                  ((DirEdit) fvc.edvec).toggleDeleteMark(fvc);
               }
               return null;

            case 14: // diredit_execute
               if (fvc.edvec instanceof DirEdit) {
                  ((DirEdit) fvc.edvec).executeMarks(fvc);
               }
               return null;

            case 15: // dirmanager_toggle_searchpath
               return handleToggleSearchPath(fvc);

            case 16: // diredit_shell
               if (fvc.edvec instanceof DirEdit) {
                  DirEdit de = (DirEdit) fvc.edvec;
                  java.io.File dir = de.getCurrentDir().fh;
                  EditContainer.registerListener(MiscCommands.fli);
                  ShellSession session =
                     ShellManager.getInstance().newShell(
                        null, dir.getName(), dir);
                  FvContext.connectFv(session.getBuffer(), fvc.vi);
               }
               return null;

            case 17: // diredit_create
               if (fvc.edvec instanceof DirEdit) {
                  ((DirEdit) fvc.edvec).createInline(fvc);
               }
               return null;

            default:
               throw new RuntimeException("DirEdit.Commands: invalid rnum "
                  + rnum);
         }
      }

      private Object handleToggleSearchPath(FvContext fvc) {
         if (fvc.edvec instanceof DirEdit) {
            DirEdit de = (DirEdit) fvc.edvec;
            int lineNum = fvc.inserty();
            String filename = de.getFilename(lineNum);

            if (null == filename) {
               UI.reportMessage("Not a file entry");
               return null;
            }

            if (!filename.endsWith("/")) {
               UI.reportMessage("Not a directory: " + filename);
               return null;
            }

            // Build full path for the directory
            String dirName;
            if ("../".equals(filename)) {
               File parentFile = de.getCurrentDir().fh.getParentFile();
               if (null == parentFile) {
                  UI.reportMessage(
                     "Cannot add root parent to search path");
                  return null;
               }
               dirName = parentFile.getPath();
            } else {
               dirName = de.getCurrentDir().shortName
                  + File.separator
                  + filename.substring(0, filename.length() - 1);
            }

            FileDescriptor.LocalDir dir =
               FileDescriptor.LocalDir.make(dirName);
            boolean nowIn =
               DirManager.getInstance().toggleSearchPath(dir);
            de.populateDirectory();
            UI.reportMessage(nowIn
               ? "Added to search path: " + dirName
               : "Removed from search path: " + dirName);
         }
         return null;
      }
   }

   /**
    * Background calculator for recursive directory sizes.
    *
    * <p>Uses a single daemon thread to walk directory trees and cache
    * results. When a calculation completes, posts an {@link EventQueue.IEvent}
    * to refresh the requesting DirEdit instance so the size column updates
    * from "..." to the actual value.</p>
    *
    * <p>Thread safety: the cache is a {@link ConcurrentHashMap} so reads
    * from the EDT (in {@code formatEntry}) are lock-free. The in-flight
    * set prevents duplicate walks for the same path.</p>
    */
   static final class DirSizeCalculator {

      /** Cache: absolute directory path -> total size in bytes. */
      private static final ConcurrentHashMap<String, Long> sizeCache =
         new ConcurrentHashMap<>();

      /** Paths currently being calculated (deduplication). */
      private static final ConcurrentHashMap<String, Boolean> inFlight =
         new ConcurrentHashMap<>();

      /** Single daemon thread for directory walking. */
      private static final ExecutorService executor =
         Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DirSizeCalculator");
            t.setDaemon(true);
            return t;
         });

      private DirSizeCalculator() {
      }

      /**
       * Returns the cached size for a directory, or null if not yet
       * calculated.
       *
       * @param absolutePath the directory's absolute path
       * @return cached size in bytes, or null
       */
      static Long getCachedSize(String absolutePath) {
         return sizeCache.get(absolutePath);
      }

      /**
       * Submits a background size calculation for a directory.
       * If the size is already cached or a calculation is in flight,
       * this is a no-op.
       *
       * @param absolutePath the directory's absolute path
       * @param requester the DirEdit to refresh when done
       */
      static void submitCalculation(String absolutePath,
            DirEdit requester) {
         if (sizeCache.containsKey(absolutePath)) {
            return;
         }
         if (null != inFlight.putIfAbsent(absolutePath, Boolean.TRUE)) {
            return; // already in flight
         }
         executor.submit(() -> {
            try {
               long size = walkDirectorySize(absolutePath);
               sizeCache.put(absolutePath, size);
               // Post UI refresh on the event queue (runs under biglock2)
               EventQueue.insert(new EventQueue.IEvent() {
                  @Override
                  public void execute() {
                     if (openInstances.contains(requester)) {
                        requester.populateDirectory();
                     }
                  }
               });
            } catch (Exception e) {
               trace("DirSizeCalculator: error walking "
                  + absolutePath + ": " + e);
            } finally {
               inFlight.remove(absolutePath);
            }
         });
      }

      /**
       * Walks a directory tree and returns the total size in bytes.
       *
       * @param absolutePath the root directory
       * @return total size of all regular files
       */
      static long walkDirectorySize(String absolutePath) {
         AtomicLong total = new AtomicLong(0);
         try {
            Files.walkFileTree(Path.of(absolutePath),
               new SimpleFileVisitor<Path>() {
                  @Override
                  public FileVisitResult visitFile(Path file,
                        BasicFileAttributes attrs) {
                     total.addAndGet(attrs.size());
                     return FileVisitResult.CONTINUE;
                  }

                  @Override
                  public FileVisitResult visitFileFailed(Path file,
                        IOException exc) {
                     // Skip unreadable files
                     return FileVisitResult.CONTINUE;
                  }
               });
         } catch (IOException e) {
            trace("DirSizeCalculator: walkFileTree failed for "
               + absolutePath + ": " + e);
         }
         return total.get();
      }

      /**
       * Clears the entire size cache. Called on manual refresh.
       */
      static void clearCache() {
         sizeCache.clear();
      }

      /**
       * Clears cached sizes for a specific directory and its children.
       * Useful when files are created/deleted/modified.
       *
       * @param absolutePath the directory whose cache to invalidate
       */
      static void invalidate(String absolutePath) {
         sizeCache.remove(absolutePath);
         // Also invalidate parent directories up to root
         File parent = new File(absolutePath).getParentFile();
         while (null != parent) {
            sizeCache.remove(parent.getAbsolutePath());
            parent = parent.getParentFile();
         }
      }

      /** Returns cache size (for testing). */
      static int cacheSize() {
         return sizeCache.size();
      }

      // ---- WatchService auto-invalidation ----

      /** The shared WatchService instance (lazily created). */
      private static volatile WatchService watchService;

      /** Maps WatchKey -> registered directory path. */
      private static final ConcurrentHashMap<WatchKey, String> watchKeys =
         new ConcurrentHashMap<>();

      /** Maps directory path -> WatchKey for cancellation. */
      private static final ConcurrentHashMap<String, WatchKey> pathToKey =
         new ConcurrentHashMap<>();

      /** Maps directory path -> set of DirEdit instances watching it. */
      private static final ConcurrentHashMap<String, HashSet<DirEdit>>
         watchers = new ConcurrentHashMap<>();

      /**
       * Registers a directory for filesystem change monitoring.
       * When files are created, deleted, or modified in the directory,
       * cached sizes are invalidated and recalculated automatically.
       *
       * @param dirPath absolute path of the directory to watch
       * @param requester the DirEdit instance to refresh on changes
       */
      static void watchDirectory(String dirPath, DirEdit requester) {
         ensureWatchService();
         if (null == watchService) {
            return; // WatchService unavailable
         }
         watchers.computeIfAbsent(dirPath, k -> new HashSet<>())
            .add(requester);
         if (pathToKey.containsKey(dirPath)) {
            return; // already watching
         }
         try {
            Path path = Path.of(dirPath);
            WatchKey key = path.register(watchService,
               StandardWatchEventKinds.ENTRY_CREATE,
               StandardWatchEventKinds.ENTRY_DELETE,
               StandardWatchEventKinds.ENTRY_MODIFY);
            watchKeys.put(key, dirPath);
            pathToKey.put(dirPath, key);
            trace("DirSizeCalculator: watching " + dirPath);
         } catch (IOException e) {
            trace("DirSizeCalculator: cannot watch " + dirPath
               + ": " + e);
         }
      }

      /**
       * Unregisters a DirEdit instance from a watched directory.
       * If no more instances are watching, the WatchKey is cancelled.
       *
       * @param dirPath absolute path of the directory
       * @param requester the DirEdit instance to remove
       */
      static void unwatchDirectory(String dirPath, DirEdit requester) {
         HashSet<DirEdit> set = watchers.get(dirPath);
         if (null == set) {
            return;
         }
         set.remove(requester);
         if (set.isEmpty()) {
            watchers.remove(dirPath);
            WatchKey key = pathToKey.remove(dirPath);
            if (null != key) {
               key.cancel();
               watchKeys.remove(key);
               trace("DirSizeCalculator: unwatched " + dirPath);
            }
         }
      }

      /**
       * Lazily creates the WatchService and starts the polling daemon.
       */
      private static synchronized void ensureWatchService() {
         if (null != watchService) {
            return;
         }
         try {
            watchService = FileSystems.getDefault().newWatchService();
            Thread watcher = new Thread(
               DirSizeCalculator::pollWatchEvents,
               "DirSizeWatcher");
            watcher.setDaemon(true);
            watcher.start();
            trace("DirSizeCalculator: WatchService started");
         } catch (IOException e) {
            trace("DirSizeCalculator: cannot create WatchService: "
               + e);
         }
      }

      /**
       * Polls the WatchService for filesystem events, invalidates
       * cached sizes, and triggers re-calculation for affected
       * DirEdit instances.
       */
      private static void pollWatchEvents() {
         while (true) {
            WatchKey key;
            try {
               key = watchService.take(); // blocks until event
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               return;
            } catch (java.nio.file.ClosedWatchServiceException e) {
               return;
            }

            String dirPath = watchKeys.get(key);
            if (null == dirPath) {
               key.cancel();
               continue;
            }

            // Drain events (we only care that something changed)
            boolean changed = false;
            for (WatchEvent<?> event : key.pollEvents()) {
               if (event.kind() != StandardWatchEventKinds.OVERFLOW) {
                  changed = true;
               }
            }

            if (changed) {
               // Invalidate this directory and its parents
               invalidate(dirPath);

               // Re-submit calculations and refresh requesting DirEdits
               HashSet<DirEdit> requesters = watchers.get(dirPath);
               if (null != requesters) {
                  for (DirEdit de : requesters) {
                     submitCalculation(dirPath, de);
                     // Also refresh the DirEdit listing to show new/removed
                     // files
                     EventQueue.insert(new EventQueue.IEvent() {
                        @Override
                        public void execute() {
                           if (openInstances.contains(de)) {
                              de.populateDirectory();
                           }
                        }
                     });
                  }
               }
            }

            // Reset key — if invalid, directory was deleted
            if (!key.reset()) {
               watchKeys.remove(key);
               if (null != dirPath) {
                  pathToKey.remove(dirPath);
                  watchers.remove(dirPath);
               }
            }
         }
      }

      /** Returns the number of active WatchKeys (for testing). */
      static int watchCount() {
         return watchKeys.size();
      }
   }
}
