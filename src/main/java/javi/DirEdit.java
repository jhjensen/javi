package javi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;

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
 *   <tr><td>Enter</td><td>Open file or enter directory</td></tr>
 *   <tr><td>-</td><td>Go to parent directory</td></tr>
 *   <tr><td>.</td><td>Toggle hidden files</td></tr>
 *   <tr><td>s</td><td>Cycle sort mode (name/size/date/type)</td></tr>
 *   <tr><td>R</td><td>Refresh directory listing</td></tr>
 *   <tr><td>dd</td><td>Delete file under cursor</td></tr>
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

   /** Current sort mode. */
   private SortMode sortMode = SortMode.NAME;

   /** Files marked for deletion. Package-private for testing. */
   final HashSet<String> markedForDelete = new HashSet<>();

   /** Date formatter for file dates. */
   private static final SimpleDateFormat dateFormat =
      new SimpleDateFormat("yyyy-MM-dd HH:mm");

   /** Sort modes for directory listing. */
   public enum SortMode {
      /** Sort by name (directories first). */
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
         case ' ':
         case 27: // Escape
            return false;
         default:
            break;
      }

      // Allow control characters through (Ctrl-F, Ctrl-B, etc.)
      if (ch < 32) {
         return false;
      }

      // Block everything else (editing commands: i,a,o,c,d,x,p,r,~, etc.)
      return true;
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
      FileDescriptor.LocalDir dir = FileDescriptor.LocalDir.make(path);
      if (!dir.exists() || !dir.isDirectory()) {
         throw new InputException("Not a directory: " + path);
      }
      DirEdit dirEdit = new DirEdit(dir);
      return FvContext.connectFv(dirEdit, vi);
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
      }

      // Add help footer
      lines.add("");
      lines.add("  [Enter] open  [-] parent  [.] hidden  [s] sort"
         + "  [R] refresh  [q] quit");
      lines.add("  [dd] delete  [S] search path toggle"
         + "  :diredit_rename  :diredit_mkdir  :diredit_newfile");
      lines.add("  :diredit_copy  :dirmanager_toggle_searchpath");

      // Insert all lines
      insertStrings(lines, 1);
   }

   /**
    * Sorts the file list according to current sort mode.
    *
    * @param files the list of files to sort
    */
   private void sortFiles(ArrayList<File> files) {
      Comparator<File> comparator;

      switch (sortMode) {
         case SIZE:
            comparator = (f1, f2) -> {
               // Directories first
               if (f1.isDirectory() != f2.isDirectory()) {
                  return f1.isDirectory() ? -1 : 1;
               }
               return Long.compare(f1.length(), f2.length());
            };
            break;
         case DATE:
            comparator = (f1, f2) -> {
               // Directories first
               if (f1.isDirectory() != f2.isDirectory()) {
                  return f1.isDirectory() ? -1 : 1;
               }
               return Long.compare(f1.lastModified(), f2.lastModified());
            };
            break;
         case TYPE:
            comparator = (f1, f2) -> {
               // Directories first
               if (f1.isDirectory() != f2.isDirectory()) {
                  return f1.isDirectory() ? -1 : 1;
               }
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
            comparator = (f1, f2) -> {
               // Directories first
               if (f1.isDirectory() != f2.isDirectory()) {
                  return f1.isDirectory() ? -1 : 1;
               }
               return f1.getName().compareToIgnoreCase(f2.getName());
            };
            break;
      }

      files.sort(comparator);
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
         size = "<DIR>";
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
      UI.reportMessage("Sort by: " + sortMode.name().toLowerCase());
   }

   /**
    * Refreshes the directory listing.
    *
    * @param fvc the current FvContext
    */
   void refresh(FvContext fvc) {
      populateDirectory();
      UI.reportMessage("Directory refreshed");
   }

   /**
    * Deletes the file or directory under the cursor.
    * Non-empty directories are not deleted (safety guard).
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

      if (target.isDirectory()) {
         String[] children = target.list();
         if (null != children && children.length > 0) {
            throw new InputException("Directory not empty: " + name
               + " (" + children.length + " entries)");
         }
      }

      // Confirmation prompt
      UI.reportMessage("Delete " + name + "? (y/n)");
      char confirm = EventQueue.nextKey(fvc.vi);
      if (confirm != 'y' && confirm != 'Y') {
         UI.reportMessage("Delete cancelled");
         return;
      }

      if (!target.delete()) {
         throw new InputException("Failed to delete: " + name);
      }

      UI.reportMessage("Deleted: " + name);
      trace("DirEdit: deleted " + target.getAbsolutePath());
      markedForDelete.remove(name);
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
         if (target.isDirectory()) {
            String[] children = target.list();
            if (null != children && children.length > 0) {
               errors.add(name + " (not empty)");
               failed++;
               continue;
            }
         }
         if (target.delete()) {
            markedForDelete.remove(name);
            deleted++;
            trace("DirEdit: batch deleted " + target.getAbsolutePath());
         } else {
            errors.add(name + " (delete failed)");
            failed++;
         }
      }

      StringBuilder msg = new StringBuilder();
      msg.append("Deleted ").append(deleted).append(" file(s)");
      if (failed > 0) {
         msg.append(", ").append(failed).append(" failed: ");
         msg.append(String.join(", ", errors));
      }
      UI.reportMessage(msg.toString());
      populateDirectory();
      if (fvc.inserty() >= readIn()) {
         fvc.cursoryabs(readIn() - 1);
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

      UI.reportMessage("Delete " + names.size() + " file(s)? (y/n)");
      char confirm = EventQueue.nextKey(fvc.vi);
      if (confirm != 'y' && confirm != 'Y') {
         UI.reportMessage("Delete cancelled");
         return;
      }

      int deleted = 0;
      int failed = 0;
      for (String name : names) {
         File target = new File(currentDir.fh, name);
         if (target.isDirectory()) {
            String[] ch = target.list();
            if (null != ch && ch.length > 0) {
               failed++;
               continue;
            }
         }
         if (target.delete()) {
            deleted++;
            trace("DirEdit: range-deleted " + target.getAbsolutePath());
         } else {
            failed++;
         }
      }

      StringBuilder msg = new StringBuilder("Deleted ");
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

            default:
               throw new RuntimeException("DirEdit.Commands: invalid rnum "
                  + rnum);
         }
      }
   }
}
