package javi;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import static history.Tools.trace;

/**
 * F5: Unified Directory Manager — replaces both DirList and DirEdit.
 *
 * <p>DirManager combines the interactive directory browsing UI of DirEdit
 * with the search-path management role of DirList. It is a
 * {@code TextEdit<String>} that displays directory contents as formatted
 * strings, while maintaining an internal list of directories marked for
 * the search path.</p>
 *
 * <h2>Migrated Callers</h2>
 * <ul>
 *   <li>FileList — search, addSearchDir</li>
 *   <li>PosListList — globalgrep, gotosearchpath</li>
 *   <li>JavaCompiler — fileList()</li>
 *   <li>CheckStyle — fileList()</li>
 *   <li>MiscCommands — flushCache()</li>
 *   <li>Javi — getInstance() at startup</li>
 * </ul>
 *
 * <h2>Persistence</h2>
 * <p>DirManager persists the search path independently via a
 * private {@code TextEdit<String>} store ("searchpath" internal file).
 * DirList is no longer referenced.</p>
 *
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
   // Search-path state
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

   /** The single DirManager instance. */
   private static DirManager instance;

   /** Persistent backing store for search path entries. */
   private static TextEdit<String> searchPathStore;

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
      loadSearchPath();
   }

   /**
    * Lazily initialize the persistent search path store.
    *
    * @return the shared search path store
    */
   @SuppressWarnings("unchecked")
   private static TextEdit<String> getSearchPathStore() {
      if (searchPathStore == null) {
         FileDescriptor fd = FileDescriptor.InternalFd.make("searchpath");
         FileProperties<String> fp =
            new FileProperties<>(fd, StringIoc.converter);
         searchPathStore = new TextEdit<>(new IoConverter(fp, true), fp);
         searchPathStore.finish();
      }
      return searchPathStore;
   }

   /**
    * Load search path entries from the persistent store.
    * Called once at startup.
    */
   private void loadSearchPath() {
      try {
         TextEdit<String> store = getSearchPathStore();
         int size = store.readIn();
         for (int ii = 1; ii < size; ii++) {
            String path = store.at(ii);
            if (path != null && !path.isEmpty()) {
               FileDescriptor.LocalDir dir =
                  FileDescriptor.LocalDir.make(path);
               searchPath.add(dir);
            }
         }
      } catch (Exception e) {
         trace("DirManager: loadSearchPath failed: " + e);
      }
   }

   /**
    * Persist the current search path to the backing store.
    */
   private void saveSearchPath() {
      TextEdit<String> store = getSearchPathStore();
      int size = store.readIn();
      if (size > 1)
         store.remove(1, size - 1);
      int line = 1;
      for (FileDescriptor.LocalDir dir : searchPath) {
         store.insertOne(dir.toString(), line++);
      }
      store.checkpoint();
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
    * Directories in the search path get an [S] indicator.
    *
    * @param f the file to format
    * @return formatted display string
    */
   private String formatEntry(File f) {
      String name = f.getName();
      boolean isDir = f.isDirectory();
      if (isDir)
         name += "/";
      long size = f.length();

      // Search path indicator for directories
      String marker = "  ";
      if (isDir && isInSearchPath(f)) {
         marker = "S ";
      }

      return String.format("%s%-40s %8d", marker, name, size);
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
      saveSearchPath();
      trace("DirManager: added search dir " + dir);
      populateDirectory(); // refresh display so [S] marker appears
      DirEdit.notifySearchPathChanged();
      return true;
   }

   /**
    * Remove a directory from the search path.
    *
    * @param dir the directory to remove
    * @return true if removed
    */
   boolean removeSearchDir(FileDescriptor.LocalDir dir) {
      boolean removed = searchPath.remove(dir);
      if (removed) {
         saveSearchPath();
         populateDirectory(); // refresh display so [S] marker disappears
         DirEdit.notifySearchPathChanged();
      }
      return removed;
   }

   /**
    * Get all files across the search path matching a filter.
    *
    * @param fl the filename filter
    * @return list of matching files
    */
   ArrayList<FileDescriptor.LocalFile> fileList(FilenameFilter fl) {
      ArrayList<FileDescriptor.LocalFile> result = new ArrayList<>(100);
      for (FileDescriptor.LocalDir dir : searchPath) {
         ArrayList<FileDescriptor.LocalFile> files = dir.listDes(fl);
         if (files != null)
            for (FileDescriptor.LocalFile f : files)
               result.add(f);
      }
      return result;
   }

   /**
    * Check whether a directory is in the search path.
    *
    * @param dir the directory to check
    * @return true if the directory is in the search path
    */
   boolean isInSearchPath(FileDescriptor.LocalDir dir) {
      return searchPath.contains(dir);
   }

   /**
    * Check whether a file (directory) is in the search path.
    * Convenience method for DirEdit's formatEntry display.
    *
    * @param file the file to check
    * @return true if the file's directory is in the search path
    */
   boolean isInSearchPath(File file) {
      if (!file.isDirectory())
         return false;
      FileDescriptor.LocalDir dir =
         FileDescriptor.LocalDir.make(file.getPath());
      return searchPath.contains(dir);
   }

   /**
    * Toggle a directory's search path membership.
    *
    * @param dir the directory to toggle
    * @return true if the directory is now in the search path
    */
   boolean toggleSearchPath(FileDescriptor.LocalDir dir) {
      if (searchPath.contains(dir)) {
         searchPath.remove(dir);
         trace("DirManager: removed search dir " + dir);
      } else {
         searchPath.add(dir);
         trace("DirManager: added search dir " + dir);
      }
      saveSearchPath();
      return searchPath.contains(dir);
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

   /**
    * Populate the buffer with the search path directory list.
    * Uses compressed display to abbreviate shared prefixes.
    */
   void showSearchPath() {
      currentDir = null;
      int size = readIn();
      if (size > 1)
         remove(1, size - 1);

      int line = 1;
      insertOne("Search Path (" + searchPath.size()
         + " directories):", line++);
      insertOne("", line++);

      if (searchPath.isEmpty()) {
         insertOne("  (empty — use S in DirEdit to add"
            + " directories)", line++);
      } else {
         ArrayList<String> paths =
            new ArrayList<>(searchPath.size());
         for (FileDescriptor.LocalDir dir : searchPath)
            paths.add(dir.toString());

         List<String> display = compressPaths(paths);
         for (String s : display)
            insertOne("  " + s, line++);
      }
      checkpoint();
   }

   /**
    * Compress a list of directory paths for display by replacing
    * the user's home directory with {@code ~}, sorting, and
    * grouping paths that share a common parent as a tree.
    *
    * <p>Example input:</p>
    * <pre>
    * /Users/jjensen/gtools/blbrd_mitigate
    * /Users/jjensen/gtools/blbrd_common
    * /Users/jjensen/gtools/blbrd_ng
    * /Users/jjensen/javi
    * </pre>
    *
    * <p>Example output:</p>
    * <pre>
    * ~/gtools/
    *   blbrd_common/
    *   blbrd_mitigate/
    *   blbrd_ng/
    * ~/javi
    * </pre>
    *
    * @param paths list of absolute directory paths
    * @return compressed display strings
    */
   static List<String> compressPaths(List<String> paths) {
      if (paths == null || paths.isEmpty())
         return new ArrayList<>();

      String home = System.getProperty("user.home");

      // Replace home prefix with ~ and sort
      ArrayList<String> tilded = new ArrayList<>(paths.size());
      for (String p : paths) {
         if (home != null && p.startsWith(home))
            tilded.add("~" + p.substring(home.length()));
         else
            tilded.add(p);
      }
      Collections.sort(tilded);

      // Group by parent directory using sorted map
      TreeMap<String, ArrayList<String>> groups = new TreeMap<>();
      ArrayList<String> ungrouped = new ArrayList<>();
      for (String p : tilded) {
         int lastSlash = p.lastIndexOf('/');
         if (lastSlash > 0) {
            String parent = p.substring(0, lastSlash);
            String leaf = p.substring(lastSlash + 1);
            groups.computeIfAbsent(parent, k -> new ArrayList<>())
               .add(leaf);
         } else {
            ungrouped.add(p);
         }
      }

      // Build tree-style display lines
      ArrayList<String> result = new ArrayList<>();
      for (var e : groups.entrySet()) {
         ArrayList<String> leaves = e.getValue();
         if (leaves.size() == 1) {
            result.add(e.getKey() + "/" + leaves.get(0));
         } else {
            result.add(e.getKey() + "/");
            for (String leaf : leaves)
               result.add("  " + leaf + "/");
         }
      }
      result.addAll(ungrouped);
      return result;
   }

   // ---------------------------------------------------------------
   // Static open helpers (same pattern as DirEdit)
   // ---------------------------------------------------------------

   // --  Search-Path Backend APIs ──────────────────────────────

   /** Current directory index for file-find iteration. */
   private transient int dindex;
   /** Current file index within a directory for regex search. */
   private transient int findex;
   /** Max directory count for current search. */
   private transient int maxIndex;
   /** Target filename for simple file-find search. */
   private transient String searchName;
   /** Compiled regex pattern for regex file-find search. */
   private transient Matcher regex;

   /**
    * Initialize a simple file-name search across the search path.
    * After calling, use {@link #findNextFile()} to iterate results.
    *
    * @param name the file name to search for
    */
   void initSearch(String name) {
      dindex = 0;
      findex = -1;
      maxIndex = searchPath.size() + 1; // +1: dindex 1 probes cwd
      searchName = name;
   }

   /**
    * Initialize a regex file-name search across the search path.
    * After calling, use {@link #findNextFileR()} to iterate results.
    *
    * @return true if the regex compiled successfully
    */
   boolean initSearchR() {
      dindex = 0;
      findex = -1;
      maxIndex = searchPath.size();
      try {
         regex = Pattern.compile(searchName,
            Pattern.CASE_INSENSITIVE).matcher("");
      } catch (PatternSyntaxException e) {
         return false;
      }
      return true;
   }

   /**
    * Find the next file matching {@link #searchName} in the search path.
    * Checks each search-path directory for a file with the exact name.
    *
    * @return the matching file descriptor, or null if no more matches
    */
   FileDescriptor.LocalFile findNextFile() {
      while (++dindex <= maxIndex) {
         if (dindex == 1) {
            // First entry: check current directory
            FileDescriptor.LocalFile fh =
               FileDescriptor.LocalFile.make(searchName);
            if (fh.isFile() || fh.isDirectory())
               return fh;
         } else {
            FileDescriptor.LocalDir dir = searchPath.get(dindex - 2);
            FileDescriptor.LocalFile fh = dir.createFile(searchName);
            if (fh.isFile() || fh.isDirectory())
               return fh;
         }
      }
      return null;
   }

   /**
    * Find the next file matching the regex pattern in the search path.
    * Iterates all files in each search-path directory testing the name.
    *
    * @return the matching file descriptor, or null if no more matches
    */
   FileDescriptor findNextFileR() {
      while (dindex <= maxIndex) {
         FileDescriptor.LocalDir dir;
         String[] flist;

         if (dindex == 0) {
            // First entry: search current directory
            // (skip — handled by simple search)
            dindex++;
            findex = -1;
            continue;
         }

         dir = searchPath.get(dindex - 1);
         flist = dir.fh.list();

         if (null != flist)
            while (++findex < flist.length) {
               if (null == regex)
                  throw new RuntimeException("regex not initialized");
               if (regex.reset(flist[findex]).matches()) {
                  FileDescriptor.LocalFile fh = dir.createFile(flist[findex]);
                  if (fh.isFile() || fh.isDirectory())
                     return fh;
               }
            }

         dindex++;
         findex = -1;
      }
      return null;
   }

   /**
    * Flush cached directory listings across all search-path entries.
    * Forces re-reading file lists from disk on next access.
    */
   void flushCache() {
      // DirManager search path uses FileDescriptor.LocalDir which
      // reads from disk on each access — no persistent cache to flush.
      // Re-populate the current directory view if one is open.
      populateDirectory();
   }

   /**
    * Perform a grep across all files in the search path.
    * Returns a TextEdit containing Position entries for each match.
    *
    * @param searchstr the regex pattern to search for
    * @return a TextEdit of Position results
    */
   @SuppressWarnings("unchecked")
   TextEdit<Position> globalgrep(String searchstr) {
      ArrayList<DirCacheEntry> dlist = new ArrayList<>(searchPath.size());
      for (FileDescriptor.LocalDir dir : searchPath) {
         dlist.add(new DirCacheEntry(dir));
      }
      GrepReader conv = new GrepReader(searchstr, dlist, true);
      return new TextEdit<Position>(conv, conv.prop);
   }

   /**
    * Lightweight directory entry with file-list caching.
    * Used by GrepReader for iterating files in search-path directories.
    */
   private static final class DirCacheEntry {
      final FileDescriptor.LocalDir fh;
      private String[] fcache;

      DirCacheEntry(FileDescriptor.LocalDir dir) {
         fh = dir;
      }

      String[] getCache() {
         if (null == fcache)
            fcache = fh.list();
         return fcache;
      }
   }

   /**
    * Inner class that performs grep across multiple directories.
    * Extends PositionIoc to produce Position results for each match.
    * Grep engine for searching across search-path directories.
    */
   private static final class GrepReader extends PositionIoc {

      private transient Matcher matcher;
      private ArrayList<DirCacheEntry> dirlist;
      private transient boolean invert = false;

      private static final String filespec =
         "(.*\\.bin)|(.*\\.ml3)|(.*\\.rom)|(.*\\.loc)|(.*\\.axe)|"
         + "(.*\\.o)|(.*\\.class)|(.*\\.lib)|(.*\\.obj)|(.*\\.pdb)|"
         + "(.*\\.ilk)|(^tags)|(.*\\.exe)|(^ID)|(.*\\.cla +ss)|"
         + "(.*\\.core)|(.*\\.dll)|(.*\\.gz)|(.*\\.zip)|"
         + "(.*\\.hex)|(.*\\.dmp)|(.*\\.dmp2)|(.*\\.jar)|(^tags)$";

      private static final Matcher fileMatcher = Pattern.compile(
               filespec, Pattern.CASE_INSENSITIVE).matcher("");
      private static final long serialVersionUID = 1;

      private static long sizeLimit = 1;

      GrepReader(String spec, ArrayList<DirCacheEntry> dirlisti,
            boolean inverti) {
         super("grep " + spec, null, pconverter);
         dirlist = dirlisti;
         matcher = Pattern.compile("(^.*(" + spec
            + ").*$)|(^(.*)$)", Pattern.MULTILINE).matcher("");
         invert = inverti;
      }

      protected void dorun() {
         for (DirCacheEntry dir : dirlist) {
            for (String filename : dir.getCache()) {
               if (invert ^ fileMatcher.reset(filename).find()) {
                  FileDescriptor.LocalFile fd = dir.fh.createFile(filename);
                  if (!fd.isDirectory()) {
                     if (fd.length() > sizeLimit * 1000000) {
                        String[] choices = {
                           "skip", "grep", "quit grep", "remove file"};
                        UI.Result res = UI.reportModVal(
                           fd.shortName + " length "
                           + (fd.length() / 1000000)
                           + " Mb is over grep size limit",
                           "MB", choices, sizeLimit);
                        sizeLimit = res.newValue;
                        if ("skip".equals(res.choice))
                           continue;
                        else if ("quit grep".equals(res.choice))
                           return;
                        else if ("remove file".equals(res.choice)) {
                           try {
                              fd.delete();
                           } catch (IOException e) {
                              UI.popError(
                                 "removing file failed" + fd, e);
                           }
                           continue;
                        }
                     }
                     try {
                        int linecount = 1;
                        matcher.reset(fd.getString());
                        while (matcher.find()) {
                           if (matcher.start(2) != -1) {
                              Position pos = new Position(
                                 matcher.start(2) - matcher.start(),
                                 linecount, fd, matcher.group(0));
                              addElement(pos);
                           }
                           linecount++;
                        }
                     } catch (IOException e) {
                        trace("caught IOException grepping " + fd);
                     }
                  }
               }
            }
         }
      }
   }


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
