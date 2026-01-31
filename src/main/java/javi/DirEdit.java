package javi;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

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
 *   <tr><td>q</td><td>Quit directory browser</td></tr>
 * </table>
 *
 * <h2>Display Format</h2>
 * <pre>
 * Directory: /path/to/dir
 * drwxr-xr-x   4096  2024-01-15  subdir/
 * -rw-r--r--   1234  2024-01-10  file.txt
 * </pre>
 *
 * @see DirList
 * @see FileList
 */
public final class DirEdit extends TextEdit<String> {

   private static final long serialVersionUID = 1;

   /** The directory being displayed. */
   private FileDescriptor.LocalDir currentDir;

   /** Whether to show hidden (dot) files. */
   private boolean showHidden = false;

   /** Current sort mode. */
   private SortMode sortMode = SortMode.NAME;

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
    *
    * @param dir the directory to display
    */
   @SuppressWarnings({"unchecked", "rawtypes"})
   private DirEdit(FileDescriptor.LocalDir dir) {
      super(new IoConverter(new FileProperties(
         FileDescriptor.InternalFd.make("diredit:" + dir.shortName),
         StringIoc.converter), true),
         new FileProperties(
            FileDescriptor.InternalFd.make("diredit:" + dir.shortName),
            StringIoc.converter));
      this.currentDir = dir;
      setReadOnly(true);
      populateDirectory();
      finish();
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
    */
   private void populateDirectory() {
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
         File parent = dirFile.getParentFile();
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
      lines.add("  [Enter] open  [-] parent  [.] hidden  [s] sort  [R] refresh  [q] quit");

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
    * @param lineNum the line number
    * @return the filename, or null if not a file entry line
    */
   String getFilename(int lineNum) {
      if (lineNum < 1 || lineNum > readIn()) {
         return null;
      }
      String line = at(lineNum).toString();

      // Skip header lines (lines starting with spaces before permissions)
      if (line.startsWith("  ") && !line.startsWith("  [")) {
         // Check if it's the "Directory:" line
         if (line.contains("Directory:")) {
            return null;
         }
         // It's a non-file line
         return null;
      }

      // Skip help footer
      if (line.startsWith("  [")) {
         return null;
      }

      // Parse filename from end of line
      // Format: "drwx  <size>  <date>  <name>"
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
      File parent = currentDir.fh.getParentFile();
      if (null != parent) {
         currentDir = FileDescriptor.LocalDir.make(parent.getPath());
         populateDirectory();
         fvc.cursoryabs(3); // Position on first entry
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
    * Returns the current directory.
    *
    * @return the current directory
    */
   public FileDescriptor.LocalDir getCurrentDir() {
      return currentDir;
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

            default:
               throw new RuntimeException("DirEdit.Commands: invalid rnum "
                  + rnum);
         }
      }
   }
}
