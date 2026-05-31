package javi;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import static history.Tools.trace;

/**
 * Binds a {@link TextEdit} file buffer to a {@link View} for display.
 *
 * <p>
 * FvContext (File-View Context) is the central coordination point between
 * the data model (TextEdit) and the display (View). Each FvContext represents
 * one view of one file, storing:
 * <ul>
 * <li>Cursor position (x, y coordinates)</li>
 * <li>Scroll position</li>
 * <li>Selection state</li>
 * <li>Change tracking for repaints</li>
 * </ul>
 *
 * <h2>Static State Management</h2>
 * <p>
 * FvContext manages global editor state through static fields:
 * </p>
 * <ul>
 * <li>{@code fvmap} - LinkedHashMap of all View -&gt; TextEdit -&gt; FvContext
 * mappings</li>
 * <li>{@code currfvc} - Currently active FvContext</li>
 * <li>{@code defaultFvc} - Default context for new views</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * All FvMap operations require holding {@link EventQueue#biglock2}.
 * The map uses {@code assertOwned()} to verify lock is held.
 * </p>
 *
 * <h2>Key Operations</h2>
 * <ul>
 * <li>{@link #setCurrentFvc} - Switch to this context as active</li>
 * <li>{@link #getPos} / {@link #setPos} - Cursor management</li>
 * <li>{@link #registerUniq} - Ensure unique file/view binding</li>
 * <li>{@link #dispose} - Clean up when view or file closed</li>
 * </ul>
 *
 * @param <OType> Element type of underlying TextEdit (typically String)
 * @see View
 * @see TextEdit
 * @see EventQueue#biglock2
 */
public final class FvContext<OType> implements Serializable {
   private static final long serialVersionUID = 1;

   private static final class FvMap implements Serializable {
      private static final long serialVersionUID = 1;
      private LinkedHashMap<View,
         HashMap<TextEdit<?>, FvContext<?>>>
         viewhash = new LinkedHashMap<>(1);

      FvMap() {
         EditContainer.registerListener(new FS());
      }

      private final class FS implements EditContainer.FileStatusListener {

         public void fileAdded(EditContainer ev) {
         }

         public void fileWritten(EditContainer ev) {
         }

         public boolean fileDisposed(EditContainer ev) {
            if (ev instanceof TextEdit<?> te)
               remove(te);
            return false;
         }
      }

      Collection<View> viewCollection() {
         EventQueue.biglock2.assertOwned();
         return viewhash.keySet();
      }

      int viewCount() {
         return viewhash.size();
      }

      void dump() {
         EventQueue.biglock2.assertOwned();
         for (HashMap<TextEdit<?>, FvContext<?>> ehash : viewhash.values())
            for (FvContext<?> fvc : ehash.values())
               trace("view hash contains " + fvc);
      }

      FvContext<?> get(View vi, TextEdit<?> edvec) {
         EventQueue.biglock2.assertOwned();
         HashMap<TextEdit<?>, FvContext<?>> ehash = viewhash.get(vi);
         if (null == ehash) {
            // this only happens when we get a new view
            ehash = new HashMap<TextEdit<?>, FvContext<?>>(viewhash.size());
            viewhash.put(vi, ehash);
            return null;
         }
         return ehash.get(edvec);
      }

      void put(FvContext<?> fvc) {
         EventQueue.biglock2.assertOwned();
         //trace("putting a new fvcontext " + fvc);
         HashMap<TextEdit<?>, FvContext<?>> ehash = viewhash.get(fvc.vi);
         if (null == ehash) {
            // this only happens when we get a new view
            ehash = new HashMap<TextEdit<?>, FvContext<?>>(viewhash.size());
            viewhash.put(fvc.vi, ehash);
         }
         ehash.put(fvc.edvec, fvc);
      }

      Iterator<FvContext<?>> iterator() {
         EventQueue.biglock2.assertOwned();
         return new FvIterator();
      }

      Collection<HashMap<TextEdit<?>, FvContext<?>>> tmap() {
         EventQueue.biglock2.assertOwned();
         return viewhash.values();
      }

      void clear() {
         EventQueue.biglock2.assertOwned();
         viewhash.clear();
      }

      void remove(TextEdit<?> ed) {
         EventQueue.biglock2.assertOwned();
         for (HashMap<TextEdit<?>, FvContext<?>> ehash : viewhash.values())
            ehash.remove(ed);
      }

      void remove(View vi) {
         EventQueue.biglock2.assertOwned();
         Object ehash = viewhash.remove(vi);
         if (null == ehash)
            throw new RuntimeException("fvcontext.dispose: didnt find " + vi);
      }

      /** Read-only lookup: returns null if not found, no side effects. */
      FvContext<?> findOnly(View vi, TextEdit<?> te) {
         HashMap<TextEdit<?>, FvContext<?>> ehash = viewhash.get(vi);
         return ehash != null ? ehash.get(te) : null;
      }

      private final class FvIterator implements Iterator<FvContext<?>> {

         private Iterator<HashMap<TextEdit<?>,
            FvContext<?>>> viit =
            viewhash.values().iterator();
         private Iterator<FvContext<?>> fvit;

         FvIterator() {
            EventQueue.biglock2.assertOwned();
            fvit = viit.hasNext()
                  ? viit.next().values().iterator()
                  : new ArrayList<FvContext<?>>().iterator();
         }

         public boolean hasNext() {
            EventQueue.biglock2.assertOwned();
            if (fvit.hasNext())
               return true;
            while (viit.hasNext()) {
               fvit = viit.next().values().iterator();
               if (fvit.hasNext())
                  return true;
            }
            return false;
         }

         public FvContext<?> next() {
            EventQueue.biglock2.assertOwned();
            if (fvit.hasNext())
               return fvit.next();
            while (viit.hasNext()) {
               fvit = viit.next().values().iterator();
               if (fvit.hasNext())
                  return fvit.next();
            }
            throw new java.util.NoSuchElementException("no next");
         }

         public void remove() {
            throw new UnsupportedOperationException(
                  "remove unsupported by FvIterator");
         }
      }
   }

   private static FvMap fvmap = new FvMap();

   private static FvContext<?> defaultFvc;
   private static FvContext<?> currfvc; // the main text display area
   private static String alternateFileName; // vim '#' register
   private static final TextEdit<String> defaultText;
   private static Consumer<FvContext<?>> postContextHook;

   public final TextEdit<OType> edvec;
   public final View vi;
   private int fileposy = 1; // the position of the cursor in the file
   private int fileposx = 0; // the position of the cursor in the file
   private boolean vis;
   private KeyMap keyMap; // buffer-specific keymap overlay, null for default
   private transient Object overrideFont;
   private transient FoldModel foldModel;

   /**
    * Callback for providing per-buffer font overrides.
    * Registered by the AWT layer to supply fonts for buffers
    * that need a non-default font (e.g., DirEdit monospace).
    */
   public interface OverrideFontProvider {
      Object getFont(TextEdit<?> textEdit);
   }

   private static transient OverrideFontProvider overrideFontProvider;

   /** Registers a provider that auto-sets override fonts for new FvContexts. */
   public static void setOverrideFontProvider(OverrideFontProvider p) {
      overrideFontProvider = p;
   }

   /** Returns the per-FvContext font override, or null for default font. */
   public Object getOverrideFont() {
      return overrideFont;
   }

   /** Sets a per-FvContext font override. Pass null to use default font. */
   public void setOverrideFont(Object font) {
      overrideFont = font;
   }

   static void dump() {
      fvmap.dump();
   }

   /**
    * Get the buffer-specific keymap overlay for this context.
    *
    * @return the keymap overlay, or null to use the default mode keymap
    */
   KeyMap getKeyMap() {
      return keyMap;
   }

   /**
    * Set a buffer-specific keymap overlay for this context.
    * Pass null to revert to the default mode keymap.
    */
   void setKeyMap(KeyMap km) {
      this.keyMap = km;
   }

   /** Get the fold model for this context, or null if none. */
   public FoldModel getFoldModel() {
      return foldModel;
   }

   /** Set the fold model for this context. */
   public void setFoldModel(FoldModel fm) {
      this.foldModel = fm;
   }

   /**
    * If the cursor is on the start line of a collapsed fold,
    * insert an empty line after the fold and move the cursor
    * there. Used by the 'o' command to open a line below a fold.
    *
    * @return true if the cursor was on a collapsed fold and
    *         the line was inserted; false otherwise
    */
   boolean openLineBelowFold() {
      if (foldModel == null)
         return false;
      FoldModel.FoldRange cf =
         foldModel.findFoldAtStart(fileposy);
      if (cf == null || !cf.collapsed)
         return false;
      int target = cf.endLine + 1;
      java.util.ArrayList<String> empty =
         new java.util.ArrayList<>(1);
      empty.add("");
      edvec.insertStrings(empty, target);
      cursoryabs(target);
      cursorxabs(0);
      return true;
   }

   public static int viewCount() {
      return fvmap.viewCount();
   }

   /**
    * Register a hook called after every context change in setCurrView.
    * Used by GitCommands to restore split-view state on navigation.
    */
   public static void setPostContextHook(Consumer<FvContext<?>> hook) {
      postContextHook = hook;
   }

   /** Return the alternate file name (vim '#' register). */
   public static String getAlternateFileName() {
      return alternateFileName;
   }

   private void readObject(
      java.io.ObjectInputStream is)
      throws ClassNotFoundException,
      java.io.IOException {
      is.defaultReadObject();
   }

   static void restoreState(ObjectInputStream is)
      throws IOException, ClassNotFoundException {
      currfvc = ((FvContext) is.readObject());
      fvmap = (FvMap) is.readObject();
   }

   static void saveState(ObjectOutputStream os) throws IOException {
      os.writeObject(currfvc);
      os.writeObject(fvmap);
   }

   private static final class FmListener extends EditContainer.MarkListener {

      void invalidateBack(UndoHistory.EhMark ehm) {
         for (Iterator<FvContext<?>> fit = fvmap.iterator(); fit.hasNext();) {
            FvContext<?> fvc = fit.next();
            if (fvc.vis)
               fvc.vi.checkValid(ehm);
         }
      }
   }

   private static final class FoldChangeListener
         extends EditContainer.FileChangeListener {
      public void addedLines(FileDescriptor fd, int count, int index) {
         for (Iterator<FvContext<?>> fit = fvmap.iterator();
               fit.hasNext();) {
            FvContext<?> fvc = fit.next();
            if (fvc.foldModel != null
                  && fvc.edvec.fdes().equals(fd))
               fvc.foldModel.adjustForEdit(index, count);
         }
      }
   }

   static {

      EditContainer.init(new FmListener());
      EditContainer.registerChangeListen(new FoldChangeListener());
      EditContainer.registerListener(new FoldSaveListener());

      StringIoc str = new StringIoc("FvContext.defaultText",
            "deleted buffer without viewing a different one");
      defaultText = new TextEdit<String>(str, str.prop);
      Runtime.getRuntime().addShutdownHook(
            new Thread(new QuitClass(), "vic.quit thread"));
      EventQueue.registerIdle(new Idler());

   }

   /** Saves fold state when a file is written to disk. */
   private static final class FoldSaveListener
         implements EditContainer.FileStatusListener {
      public void fileAdded(EditContainer ev) {
      }
      public void fileWritten(EditContainer ev) {
         saveFoldsForFile(ev);
      }
      public boolean fileDisposed(EditContainer ev) {
         return false;
      }
   }

   /** Save fold state for all FvContexts viewing the file. */
   static void saveFoldsForFile(EditContainer ev) {
      FileDescriptor fd = ev.fdes();
      if (!(fd instanceof FileDescriptor.LocalFile))
         return;
      String canonPath =
         ((FileDescriptor.LocalFile) fd).canonName;
      for (Iterator<FvContext<?>> fit = fvmap.iterator();
            fit.hasNext();) {
         FvContext<?> fvc = fit.next();
         if (fvc.edvec == ev && fvc.foldModel != null) {
            if (fvc.foldModel.isEmpty())
               FoldModel.deleteFoldState(canonPath);
            else
               fvc.foldModel.saveFolds(canonPath);
            return; // one save is enough per file
         }
      }
   }

   private static final class Idler implements EventQueue.Idler {
      public void idle() {
         if (!EventQueue.isFocused())
            return;
         for (View vi : fvmap.viewCollection())
            if (vi.needsRepaint())
               vi.repaint();
      }
   }

   static Object getcurobj(TextEdit<?> list) {
      // trace("getcurobj = " +
      // (fvcontext.getcontext(currfvc.vi,list).getCurrentObject()));
      return getcontext(currfvc.vi, list).at();
   }

   public void setCurrView() {
      if (null != currfvc && currfvc.edvec != edvec) {
         alternateFileName = currfvc.edvec.getName();
      }
      activate();
      currfvc = this;
      ContextHelp.onContextChanged(this);
      if (postContextHook != null)
         postContextHook.accept(this);
   }

   /**
    * Activate a view for display without making it the current
    * focus. Used for display-only panels like the help side panel.
    */
   void activateDisplay() {
      activate();
   }

   private void activate() {
      // trace("activate " + this);
      if (null != currfvc) {
         if (currfvc.vi == vi) // the usual case
            currfvc.vis = false;
         else
            for (Iterator<FvContext<?>> fit = fvmap.iterator(); fit.hasNext();) {
               FvContext<?> fvc = fit.next();
               if (fvc.vi == vi)
                  fvc.vis = false;
            }
      }

      if (!edvec.containsNow(1)) {
         fileposy = 1;
         fileposx = 0;
      } else {
         fileposy = inrange(fileposy, 1, edvec.readIn() - 1);
         // If cursor is inside a collapsed fold, move to fold start
         if (foldModel != null && !foldModel.isEmpty()
               && foldModel.isFolded(fileposy)) {
            FoldModel.FoldRange f = foldModel.findFold(fileposy);
            if (f != null && f.collapsed)
               fileposy = f.startLine;
         }
         fileposx = inrange(fileposx, 0,
               edvec.at(fileposy).toString().length());
      }
      vi.newfile(edvec, fileposx, fileposy);
      vis = true;
   }

   public static FvContext<?> getCurrFvc() {
      // trace("returning currfvc " + currfvc);
      return currfvc;
   }

   public View findNextView() {
      Collection<View> views = fvmap.viewCollection();
      for (Iterator<View> eve = views.iterator(); eve.hasNext();) {
         if (vi == eve.next()) {
            while (eve.hasNext()) {
               View nview = eve.next();
               if (nview.isTraverseable())
                  return nview;
            }
            for (View nv : views)
               if (nv.isTraverseable()) {
                  return nv;
               }
            throw new RuntimeException("findNextView is confused");
         }
      }
      throw new RuntimeException("findNextView cant find vi " + vi);
   }

   public static FvContext<?> nextView() {
      View nvi = currfvc.findNextView();
      getcontext(nvi, nvi.getCurrFile()).setCurrView();
      return currfvc;
   }

   public String toString() {
      return "(" + fileposx + "," + fileposy + ")"
            + (vis ? "vis" : "") + edvec + vi;
   }

   private FvContext(View vii, TextEdit<OType> ei) {
      vi = vii;
      edvec = ei;
      // EditContainer.registerListener(this);
      // trace("created new fvc " + this);
   }

   public boolean equiv(Position po) {
      if (null == po)
         return false;
      return edvec.fdes().equals(po.filename)
            && po.x == fileposx && po.y == fileposy;
   }

   private static void fixCursor(TextEdit<?> ed) {
      for (HashMap<TextEdit<?>, FvContext<?>> hmap : fvmap.tmap()) {
         FvContext<?> fv = hmap.get(ed);
         if (null != fv)
            fv.cursorabs(fv.fileposx, fv.fileposy); // fix up cursor position
      }
   }

   void fixCursor() { // should be called with first in chain
      fixCursor(edvec);
   }

   private static final class QuitClass implements Runnable {
      public void run() {
         boolean locked = false;
         try {
            locked = EventQueue.biglock2.tryLock(2, TimeUnit.SECONDS);
            if (!locked)
               trace("failed to acquire big lock, try and exit anyway");
            javi.lsp.LspManager.getInstance().shutdownAll();
            disposeAll(true);
         } catch (Exception e) {
            trace("exit threw exception during shutdown", e);
            // Print full stack trace for debugging shutdown issues
            e.printStackTrace();
         } finally {
            if (locked)
               EventQueue.biglock2.unlock();
         }
      }
   }

   private static void disposeAll(boolean ignoreLock) throws IOException {
      if (!ignoreLock)
         EventQueue.biglock2.assertOwned();

      var allEdits = new HashSet<TextEdit<?>>(100);

      for (var fit = fvmap.iterator(); fit.hasNext();)
         allEdits.add(fit.next().edvec);

      for (TextEdit<?> ev : allEdits)
         try {
            // trace("disposing in fvc quit" + ev);
            ev.disposeFvc();
         } catch (Throwable t) {
            trace("disposeall caught " + t);
            trace("for file " + ev);
            t.printStackTrace();
         }
      EditContainer.disposeAll();
      fvmap.clear();
      currfvc = null;
   }

   public static void dispose(
      TextEdit<?> ed, TextEdit<?> next)
      throws InputException, IOException {
      // trace("disposing " + ed + " currfvc " + currfvc);

      reconnect(ed, next);
      fvmap.remove(ed);
      ed.disposeFvc();
   }

   public static FvContext<?> connectFv(TextEdit<?> file, View vi) throws InputException {

      if (null != tfc && vi == tfc.vi)
         throw new InputException(
               "can't change command window to display other data");
      UI.setTitle(file.toString());
      FvContext<?> fvc = FvContext.getcontext(vi, file);
      fvc.setCurrView();
      return fvc;
   }

   static void reconnect(TextEdit<?> ed, TextEdit<?> next) throws InputException {

      // trace("reconnecting oldfile " + ed + " next " + next);
      if (currfvc != null && currfvc.edvec == ed)
         FvContext.connectFv(next, currfvc.vi);
      // trace("starting iterator");
      for (Iterator<FvContext<?>> fit = fvmap.iterator(); fit.hasNext();) {
         FvContext<?> fvc = fit.next();
         if (fvc.edvec == ed)
            getcontext(fvc.vi, next).activate();
      }
      // trace("done iterator");
   }

   public static FvContext<?> dispose(View vi) {
      // trace("removing " + vi);
      FvContext<?> retval = null;
      if (currfvc.vi == vi)
         retval = nextView();
      fvmap.remove(vi);
      // else
      // for (FvContext fvc :ehash.values())
      // EditContainer.unRegisterListener(fvc);
      return retval;

   }

   private void wrapInc(int val) {
      int ypos = (fileposy + val) % edvec.readIn();
      if (0 == ypos)
         ypos = (val > 0)
               ? 1
               : edvec.readIn() - 1;

      cursoryabs(ypos);
   }

   /** Capture helper: bridges wildcard TextEdit to concrete FvContext constructor. */
   private static <T> FvContext<T> createFvc(View vi, TextEdit<T> te) {
      return new FvContext<>(vi, te);
   }

   FvContext<?> switchContext(TextEdit<?> ev, int incval) {
      FvContext<?> newcontext = fvmap.get(vi, ev);
      if (null == newcontext) {
         newcontext = createFvc(vi, ev);
         fvmap.put(newcontext);
      } else {
         if (newcontext != this)
            newcontext.wrapInc(incval);
      }

      return newcontext;
   }

   public static FvContext<?> getcontext(View viloc, TextEdit<?> te) {
      // trace("fvcontext.getcontext " + e + " and " + viloc);

      FvContext<?> context = fvmap.get(viloc, te);
      if (null == defaultFvc) {
         defaultFvc = new FvContext<>(viloc, defaultText);
         fvmap.put(defaultFvc);
      }

      if (null == context) {
         context = createFvc(viloc, te);
         if (overrideFontProvider != null) {
            Object font = overrideFontProvider.getFont(te);
            if (font != null)
               context.overrideFont = font;
         }
         // Restore persisted fold state for local files
         if (context.foldModel == null) {
            FileDescriptor fd = te.fdes();
            if (fd instanceof FileDescriptor.LocalFile) {
               String canonPath =
                  ((FileDescriptor.LocalFile) fd).canonName;
               FoldModel fm =
                  FoldModel.loadFolds(canonPath);
               if (fm != null) {
                  int maxLine = te.readIn() - 1;
                  if (maxLine > 1 && fm.isValid(maxLine))
                     context.foldModel = fm;
                  else
                     FoldModel.deleteFoldState(canonPath);
               }
            }
         }
         fvmap.put(context);
      }
      return context;
   }

   /**
    * Read-only FvContext lookup — returns null if not found.
    * Does not create new FvContexts. Safe for use in paint paths.
    */
   public static FvContext<?> findContext(View viloc, TextEdit<?> te) {
      return fvmap.findOnly(viloc, te);
   }

   public static String getCurrState() {
      StringBuilder sb = new StringBuilder(
            currfvc.fileposy + "," + (currfvc.fileposx + 1));
      // trace("char = " +
      // (int)((String)currfvc.edvec.at(currfvc.inserty())).charAt(currfvc.insertx()));
      // trace("sb " + sb);
      // trace("getCurrState " + currfvc);
      currfvc.edvec.addState(sb);

      // Append shell session info if current buffer is a shell
      ShellManager mgr = ShellManager.getInstance();
      ShellSession session = mgr.findByBuffer(currfvc.edvec);
      if (null != session) {
         session.updateLabel();
         int total = mgr.getSessionCount();
         sb.append(" [shell ");
         sb.append(session.getId());
         sb.append(':');
         sb.append(session.getName());
         if (total > 1) {
            sb.append(" (");
            sb.append(mgr.getActiveIndex() + 1);
            sb.append('/');
            sb.append(total);
            sb.append(')');
         }
         sb.append(']');
      }

      return sb.toString();
   }

   public Object at() {
      // trace("fvcontext.getcurrentObject fvc = " + toString() );

      if (fileposy < 1)
         throw new RuntimeException(
               "invalid fileposy " + fileposy);

      return edvec.at(edvec.contains(fileposy)
            ? fileposy
            : edvec.readIn() - 1);
   }

   public Object at(int index) {
      // trace("fvcontext.getcurrentObject fvc = " + toString() );
      return edvec.at(index);
   }

   public Position getPosition(String description) {
      return new Position(fileposx, fileposy, edvec.fdes(), description);
   }

   public int getCurrentIndex() {
      if (!edvec.containsNow(fileposy))
         cursoryabs(edvec.finish() - 1);
      if (!edvec.containsNow(1))
         throw new RuntimeException("editvec missing a line");
      return fileposy;
   }

   public int inserty() {
      return fileposy;
   }

   public int insertx() {
      return fileposx;
   }

   private static int inrange(int val, int min, int max) {
      return val < min
            ? min
            : val > max
                  ? max
                  : val;
   }

   void cursorabs(Position pos) {
      // trace("cursorabs pos = " + pos);
      // cursor(pos.x-fileposx,pos.y-fileposy);
      cursor2abs(pos.x, pos.y);
   }

   void cursorabs(MovePos pos) {
      // trace("cursorabs pos = " + pos);
      // cursor(pos.x-fileposx,pos.y-fileposy);
      cursor2abs(pos.x, pos.y);
   }

   public void cursorabs(int x, int y) {
      // trace("cursorx " + x);
      // cursor(x-fileposx,y-fileposy);
      cursor2abs(x, y);
   }

   void cursorx(int x) {
      // Grapheme-cluster-aware cursor movement: BreakIterator
      // ensures the cursor steps over combined emoji (ZWJ
      // sequences, flags, skin-tone modifiers) as a single unit.
      String line = edvec.at(fileposy).toString();
      int pos = fileposx;
      java.text.BreakIterator bi =
         java.text.BreakIterator.getCharacterInstance();
      bi.setText(line);
      if (x > 0) {
         for (int i = 0; i < x && pos < line.length(); i++) {
            int next = bi.following(pos);
            if (next == java.text.BreakIterator.DONE)
               break;
            pos = next;
         }
      } else {
         for (int i = 0; i < -x && pos > 0; i++) {
            int prev = bi.preceding(pos);
            if (prev == java.text.BreakIterator.DONE)
               break;
            pos = prev;
         }
      }
      cursor2abs(pos, fileposy);
   }

   void cursorxabs(int x) {
      // Snap to grapheme cluster boundary
      String line = edvec.at(fileposy).toString();
      if (x > 0 && x < line.length()) {
         java.text.BreakIterator bi =
            java.text.BreakIterator.getCharacterInstance();
         bi.setText(line);
         if (!bi.isBoundary(x))
            x = bi.preceding(x);
      }
      cursor2abs(x, fileposy);
   }

   /**
    * Calculate display width of a string region in columns.
    * Uses BreakIterator for grapheme cluster boundaries.
    * Each grapheme cluster occupies 1 or 2 columns based on
    * its first code point (wide for CJK, emoji, supplementary).
    */
   static int displayWidth(String text, int start, int end) {
      int width = 0;
      java.text.BreakIterator bi =
         java.text.BreakIterator.getCharacterInstance();
      bi.setText(text);
      int pos = start;
      while (pos < end) {
         int cp = Character.codePointAt(text, pos);
         width += isWideCodePoint(cp) ? 2 : 1;
         int next = bi.following(pos);
         if (next == java.text.BreakIterator.DONE
               || next > end)
            break;
         pos = next;
      }
      return width;
   }

   static boolean isWideCodePoint(int cp) {
      if (Character.isSupplementaryCodePoint(cp))
         return true;
      // CJK Unified Ideographs
      if (cp >= 0x4E00 && cp <= 0x9FFF)
         return true;
      // CJK Unified Ideographs Extension A
      if (cp >= 0x3400 && cp <= 0x4DBF)
         return true;
      // CJK Compatibility Ideographs
      if (cp >= 0xF900 && cp <= 0xFAFF)
         return true;
      // Fullwidth forms
      if (cp >= 0xFF01 && cp <= 0xFF60)
         return true;
      return false;
   }

   public void cursoryabs(int y) {
      FoldModel fm = foldModel;
      if (fm != null && !fm.isEmpty() && fm.isFolded(y)) {
         FoldModel.FoldRange f = fm.findFold(y);
         if (f != null && f.collapsed)
            y = f.startLine;
      }
      int newy = inrange(y, 1, edvec.readIn() - 1);
      fileposy = newy;
      if (vis) {
         int newx = vi.yCursorChanged(newy);
         fileposx = inrange(newx,
            0, edvec.at(fileposy).toString().length());
         if (fileposx != newx)
            UI.popError("cursor wrong permission fileposx "
               + fileposx + " newx " + newx, null);
      }
   }

   void cursory(int yoffset) {
      // trace("cursory yoffset = " + yoffset + " fvc " + this);
      FoldModel fm = foldModel;
      int newy;
      if (fm != null && !fm.isEmpty()) {
         newy = fileposy;
         int dir = yoffset > 0 ? 1 : -1;
         int remaining = Math.abs(yoffset);
         while (remaining > 0) {
            int next = dir > 0
               ? fm.nextVisible(newy)
               : fm.prevVisible(newy);
            if (next < 1 || next >= edvec.readIn())
               break;
            newy = next;
            remaining--;
         }
      } else {
         newy = yoffset + fileposy;
      }
      newy = inrange(newy, 1, edvec.readIn() - 1);
      fileposy = newy;
      if (vis) {
         int newx = vi.yCursorChanged(newy);
         if (edvec.containsNow(fileposy)) {
            fileposx = inrange(newx,
               0, edvec.at(fileposy).toString().length());
            if (fileposx != newx)
               UI.popError("cursor wrong permission fileposx "
                     + fileposx + " newx " + newx, null);
         } else {
            fileposx = 0;
         }
      }
   }

   private void cursor2abs(int newx, int newy) {
      // trace("newx = " + newx + " newy = " + newy + " this " + this);
      // trace("edvec.readIn " + edvec.readIn());
      // adjust the insertion point
      if (newy < 1)
         return;
      fileposy = inrange(newy, 1, edvec.readIn() - 1);
      // If the target line is hidden inside a collapsed fold,
      // open enclosing folds so the line becomes visible and
      // the display stays synchronized with the cursor position.
      if (foldModel != null && !foldModel.isEmpty()
            && foldModel.isFolded(fileposy))
         foldModel.openAllEnclosing(fileposy);
      if (!edvec.containsNow(fileposy))
         return;
      String line = edvec.at(fileposy).toString();
      fileposx = inrange(newx, 0, line.length());
      // Snap to grapheme cluster boundary so cursor never lands
      // in the middle of a surrogate pair or combining sequence.
      if (fileposx > 0 && fileposx < line.length()) {
         java.text.BreakIterator bi =
            java.text.BreakIterator.getCharacterInstance();
         bi.setText(line);
         if (!bi.isBoundary(fileposx))
            fileposx = bi.preceding(fileposx);
      }
      if (vis)
         vi.cursorChanged(fileposx, fileposy);
   }

   void placeline(int lineno, float amount) {
      if (vis)
         vi.placeline(lineno, amount);
   }

   void screeny(int count) {
      cursory(vi.screeny(count));
   }

   public void insertStrings(ArrayList<String> obarray, boolean after) {
      int insertAt = fileposy + (after ? 1 : 0);
      if (after && foldModel != null) {
         FoldModel.FoldRange cf =
            foldModel.findFoldAtStart(fileposy);
         if (cf != null && cf.collapsed)
            insertAt = cf.endLine + 1;
      }
      edvec.insertStrings(obarray, insertAt);
   }

   public void changeElement(OType obj) {
      edvec.changeElementAt(obj, fileposy);
   }

   public void changeElementStr(String obj) {
      edvec.changeElementAtStr(obj, fileposy);
   }

   Position inserttext(String buffer) {
      return edvec.inserttext(buffer, fileposx, fileposy);
   }

   ArrayList<String> getElementsAt(int number) {
      return edvec.getElementsAt(fileposy, number);
   }

   void setMark() {
      Position pos = getPosition("mark position");
      vi.setMark(pos);
      cursorabs(pos);
   }

   void setMark(Position pos) {
      vi.setMark(pos);
      cursorabs(pos);
   }

   private static FvContext<?> tfc;

   public static void setCommand(FvContext<?> tfci) {
      tfc = tfci;
   }

   static FvContext<?> startComLine() {
      // tfc.setCurrView();
      UI.showCommand();
      return tfc;
   }

   static String endComLine() {
      // tfc.setCurrView();
      UI.hideCommand();
      return tfc.at().toString();
   }

   boolean isGotoOk() {
      return this != tfc;
   }

   void deleteChars(char bufid, boolean reversable,
         boolean forward, int count) {
      String line = at().toString();
      String deleted = null;

      // trace("count = " + count + " llen = " + line.length());

      if (line.length() == insertx() && reversable)
         forward = false;

      java.text.BreakIterator bi =
         java.text.BreakIterator.getCharacterInstance();
      bi.setText(line);

      if (forward) {
         // Expand count to cover full grapheme clusters
         int end = insertx();
         for (int i = 0; i < count && end < line.length(); i++) {
            int next = bi.following(end);
            if (next == java.text.BreakIterator.DONE)
               break;
            end = next;
         }
         deleted = line.substring(insertx(), end);
         line = line.substring(0, insertx())
               + line.substring(end, line.length());
      } else {
         // Expand count backward to cover full grapheme clusters
         int start = insertx();
         for (int i = 0; i < count && start > 0; i++) {
            int prev = bi.preceding(start);
            if (prev == java.text.BreakIterator.DONE)
               break;
            start = prev;
         }
         if (start == insertx())
            return;
         deleted = line.substring(start, insertx());
         line = line.substring(0, start)
               + line.substring(insertx(), line.length());
         cursor2abs(start, fileposy);
      }
      Buffers.recordDelete(bufid, deleted);
      changeElementStr(line);
      // trace("count = " + count + " llen = " + line.length());
      return;
   }

}
