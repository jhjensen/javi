package javi;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import static history.Tools.trace;

/**
 * F5 Phase 5: Unified Directory Manager — replaces both DirList and DirEdit.
 *
 * <p>DirManager combines the interactive directory browsing UI of DirEdit
 * with the search-path management role of DirList. It is a
 * {@code TextEdit<String>} that displays directory contents as formatted
 * strings, while maintaining an internal list of directories marked for
 * the search path.</p>
 *
 * <h2>Migration Plan</h2>
 * <p>Callers currently using DirList's search-path and file-find APIs
 * will be migrated one at a time (see plan-F5-directory-editor.md,
 * Phases 6-8). During migration, DirList remains functional.</p>
 *
 * <h2>Callers to Migrate</h2>
 * <ul>
 *   <li>FileList — getDefault(), fileList(FilenameFilter)</li>
 *   <li>PosListList — globalgrep(String)</li>
 *   <li>JavaCompiler — getDefault(), addSearchDir(dir)</li>
 *   <li>CheckStyle — getDefault() for search path</li>
 *   <li>MiscCommands — gotosearchpath, direct DirList access</li>
 *   <li>Javi — startup insertion of initial directories</li>
 * </ul>
 *
 * @see DirList the legacy search-path manager (to be replaced)
 * @see DirEdit the legacy directory browser (to be replaced)
 */
public final class DirManager extends TextEdit<String> {

   private static final long serialVersionUID = 1;

   // ---------------------------------------------------------------
   // Directory browsing state (from DirEdit)
   // ---------------------------------------------------------------

   /** The directory currently being displayed. */
   private FileDescriptor.LocalDir currentDir;

   /** Whether to show hidden (dot) files. */
   private boolean showHidden;

   /** Current sort order. */
   private SortMode sortMode = SortMode.NAME;

   /** Available sort modes for the directory listing. */
   public enum SortMode { NAME, SIZE, DATE, TYPE }

   // ---------------------------------------------------------------
   // Search-path state (from DirList)
   // ---------------------------------------------------------------

   /**
    * Directories marked as part of the search path.
    * These are the directories that file-find and grep will traverse.
    */
   private final ArrayList<FileDescriptor.LocalDir> searchPath =
      new ArrayList<>();

   // ---------------------------------------------------------------
   // Singleton
   // ---------------------------------------------------------------

   /** The single DirManager instance (replaces DirList.deflist). */
   private static DirManager instance;

   /**
    * Get or create the singleton DirManager.
    *
    * @return the shared DirManager instance
    */
   @SuppressWarnings("unchecked")
   static DirManager getInstance() {
      if (instance == null) {
         FileDescriptor fd = FileDescriptor.InternalFd.make("dirmgr");
         FileProperties<String> fp =
            new FileProperties<>(fd, StringIoc.converter);
         instance = new DirManager(fp);
      }
      return instance;
   }

   @SuppressWarnings("unchecked")
   private DirManager(FileProperties<String> fp) {
      super(new IoConverter(fp, true), fp);
      finish();
   }

   // ---------------------------------------------------------------
   // Directory browsing API (Phase 5 — skeleton)
   // ---------------------------------------------------------------

   /**
    * Open and display a directory in this manager.
    *
    * @param dir the directory to display
    */
   void openDir(FileDescriptor.LocalDir dir) {
      this.currentDir = dir;
      populateDirectory();
   }

   /**
    * Populate the buffer with the contents of {@link #currentDir}.
    * Clears existing content and re-reads the directory.
    */
   void populateDirectory() {
      if (currentDir == null)
         return;

      // Clear existing content
      int size = readIn();
      if (size > 1)
         remove(1, size - 1);

      File dir = currentDir.fh;
      File[] entries = dir.listFiles();
      if (entries == null)
         return;

      // Sort
      java.util.Arrays.sort(entries,
         java.util.Comparator.comparing(File::getName));

      // Header line
      insertOne(currentDir.shortName + ":", 1);

      int line = 2;
      for (File f : entries) {
         if (!showHidden && f.isHidden())
            continue;
         String display = formatEntry(f);
         insertOne(display, line++);
      }

      checkpoint();
   }

   /**
    * Format a single file/directory entry for display.
    *
    * @param f the file to format
    * @return formatted display string
    */
   private String formatEntry(File f) {
      String name = f.getName();
      if (f.isDirectory())
         name += "/";
      long size = f.length();
      return String.format("%-40s %8d", name, size);
   }

   /**
    * Get the current directory being displayed.
    *
    * @return the current directory, or null if none
    */
   public FileDescriptor.LocalDir getCurrentDir() {
      return currentDir;
   }

   // ---------------------------------------------------------------
   // Search-path management API (Phase 6 — skeleton)
   // ---------------------------------------------------------------

   /**
    * Add a directory to the search path.
    *
    * @param dir the directory to add
    * @return true if added, false if already present
    */
   boolean addSearchDir(FileDescriptor.LocalDir dir) {
      if (searchPath.contains(dir))
         return false;
      searchPath.add(dir);
      trace("DirManager: added search dir " + dir);
      return true;
   }

   /**
    * Remove a directory from the search path.
    *
    * @param dir the directory to remove
    * @return true if removed
    */
   boolean removeSearchDir(FileDescriptor.LocalDir dir) {
      return searchPath.remove(dir);
   }

   /**
    * Get all files across the search path matching a filter.
    * (Replaces DirList.fileList(FilenameFilter))
    *
    * @param fl the filename filter
    * @return list of matching files
    */
   ArrayList<FileDescriptor.LocalFile> fileList(FilenameFilter fl) {
      ArrayList<FileDescriptor.LocalFile> result = new ArrayList<>(100);
      for (FileDescriptor.LocalDir dir : searchPath) {
         for (FileDescriptor.LocalFile f : dir.listDes(fl))
            result.add(f);
      }
      return result;
   }

   /**
    * Get the search path directories.
    *
    * @return unmodifiable view of the search path
    */
   ArrayList<FileDescriptor.LocalDir> getSearchPath() {
      return new ArrayList<>(searchPath);
   }

   /**
    * Get the number of directories in the search path.
    *
    * @return search path size
    */
   int searchPathSize() {
      return searchPath.size();
   }

   // ---------------------------------------------------------------
   // Static open helpers (same pattern as DirEdit)
   // ---------------------------------------------------------------

   /**
    * Open a directory in the DirManager, connecting it to a view.
    *
    * @param path directory path
    * @param vi the view to display in
    * @return the FvContext for the directory view
    * @throws IOException on I/O error
    * @throws InputException on invalid path
    */
   public static FvContext openDirectory(String path, View vi)
         throws IOException, InputException {
      File dir = new File(path);
      if (!dir.isDirectory())
         throw new InputException("Not a directory: " + path);
      DirManager dm = getInstance();
      FileDescriptor.LocalDir localDir =
         FileDescriptor.LocalDir.make(dir.getPath());
      dm.openDir(localDir);
      return FvContext.connectFv(dm, vi);
   }
}
