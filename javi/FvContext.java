package javi;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public final class FvContext<OType> implements Serializable {
   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";
   private static final long serialVersionUID = 1;

   private static class FvMap implements Serializable {
      private LinkedHashMap<View, HashMap<TextEdit, FvContext>> viewhash =
         new LinkedHashMap<View, HashMap<TextEdit, FvContext>>(1);

      FvMap() {
         EditContainer.registerListener(new FS());
      }

      private class FS implements FileStatusListener {
         public void fileAdded(EditContainer ev) { }
         public void fileWritten(EditContainer ev) { }
         public boolean fileDisposed(EditContainer ev) {
            if (ev instanceof TextEdit)
               remove((TextEdit) ev);
            return false;
         }
      }

      Collection<View> viewCollection() {
         return viewhash.keySet();
      }

      Iterator<FvContext> fvIterator() {
         return new FvIterator();
      }

      int viewCount() {
         return viewhash.size();
      }

      void dump() {
         for (HashMap<TextEdit, FvContext> ehash : viewhash.values())
            for (FvContext fvc : ehash.values())
               trace("view hash contains" + fvc);
      }

      FvContext get(View vi, TextEdit edvec) {
         HashMap<TextEdit, FvContext> ehash = viewhash.get(vi);
         if (ehash == null) {
            // this only happens when we get a new view
            ehash = new HashMap<TextEdit, FvContext>(viewhash.size());
            viewhash.put(vi, ehash);
            return null;
         }
         return ehash.get(edvec);
      }

      void put(FvContext fvc) {
         HashMap<TextEdit, FvContext> ehash = viewhash.get(fvc.vi);
         if (ehash == null) {
            // this only happens when we get a new view
            ehash = new HashMap<TextEdit, FvContext>(viewhash.size());
            viewhash.put(fvc.vi, ehash);
         }
         ehash.put(fvc.edvec, fvc);
      }

      Iterator<FvContext> iterator() {
         return new FvIterator();
      }

      Collection<HashMap<TextEdit, FvContext>> tmap() {
         return viewhash.values();
      }

      void clear() {
         viewhash.clear();
      }

      void remove(TextEdit ed) {
         for (HashMap<TextEdit, FvContext> ehash : viewhash.values())
            ehash.remove(ed);
      }

      void remove(View vi) {
         Object ehash = viewhash.remove(vi);
         if (null == ehash)
            throw new RuntimeException("fvcontext.dispose: didnt find " + vi);
      }

      private final class FvIterator implements Iterator<FvContext> {

         private Iterator<HashMap<TextEdit, FvContext>> viit =
            viewhash.values().iterator();
         private Iterator<FvContext> fvit;

         FvIterator() {
            fvit = viit.hasNext()
                   ? viit.next().values().iterator()
                   : new ArrayList<FvContext>().iterator();
         }

         public boolean hasNext() {
            if (fvit.hasNext())
               return true;
            while (viit.hasNext())  {
               fvit = viit.next().values().iterator();
               if (fvit.hasNext())
                  return true;
            }
            return false;
         }

         public FvContext next() {
            if (fvit.hasNext())
               return fvit.next();
            while (viit.hasNext())  {
               fvit = viit.next().values().iterator();
               if (fvit.hasNext())
                  return fvit.next();
            }
            throw new java.util.NoSuchElementException();
         }
         public void remove() {
            throw new UnsupportedOperationException();
         }
      }
   }

   private static FvMap fvmap = new FvMap();

   private static FvContext defaultFvc;
   private static FvContext currfvc;             // the main text display area
   private static final TextEdit<String> defaultText;

   final TextEdit<OType> edvec;
   final View vi;
   private int fileposy = 1;     // the position of the cursor in the file
   private int fileposx = 0;     // the position of the cursor in the file
   private boolean vis;
   private transient KeyHandler preDispatch;

   static void dump() {
      fvmap.dump();
   }

   static int viewCount() {
      return fvmap.viewCount();
   }

   private void readObject(java.io.ObjectInputStream is) throws
         ClassNotFoundException, java.io.IOException {
      is.defaultReadObject();
   }

   static void restoreState(ObjectInputStream is) throws
      IOException, ClassNotFoundException {
      currfvc = ((FvContext) is.readObject());
      fvmap = (FvMap) is.readObject();
   }

   static void saveState(ObjectOutputStream os) throws IOException {
      os.writeObject(currfvc);
      os.writeObject(fvmap);
   }

   static {

      StringIoc str = new StringIoc("FvContext.defaultText",
         "deleted buffer without viewing a different one");
      defaultText = new TextEdit<String>(str, str.prop);
      Runtime.getRuntime().addShutdownHook(
         new Thread(new QuitClass(), "vic.quit thread"));
      EventQueue.registerIdle(new Idler());

   }

   private static class Idler implements EventQueue.Idler {
      public void idle() {
         for (View vi : fvmap.viewCollection())
            vi.repaint();
      }
   }

   static Object getcurobj(TextEdit list) {
      //trace("getcurobj = " + (fvcontext.getcontext(currfvc.vi,list).getCurrentObject()));
      return (getcontext(currfvc.vi, list).at());
   }

   void setCurrView() {
      //trace("setting curr fvc to " + this);
      if (currfvc != null) {
         if (currfvc.vi == vi) // the usual case
            currfvc.vis = false;
         else
            for (Iterator<FvContext> fit = fvmap.iterator(); fit.hasNext();)  {
               FvContext fvc = fit.next();
               if (fvc.vi == vi)
                  fvc.vis = false;
            }
      }
      currfvc = this;
      vi.newfile(this);
      currfvc.cursorabs(currfvc.fileposx,currfvc.fileposy); // fix up cursor position
      vis = true;
   }

   static FvContext getCurrFvc() {
      return currfvc;
   }


   View findNextView() {
      Collection<View> views = fvmap.viewCollection();
      for (Iterator<View> eve = views.iterator(); eve.hasNext();) {
         if (vi == eve.next()) {
            while (eve.hasNext()) {
               View nview = eve.next();
               if (nview.nextFlag)
                  return nview;
            }
            for (View nv : views)
               if (nv.nextFlag) {
                  return nv;
               }
            throw new RuntimeException("findNextView is confused");
         }
      }
      throw new RuntimeException("findNextView cant find vi " + vi);
   }

   static FvContext nextView() {
      View nvi = currfvc.findNextView();
      getcontext(nvi, nvi.getCurrFile()).setCurrView();
      return currfvc;
   }

   public String toString() {
      return "(" + fileposx + "," + fileposy + ")" + edvec + vi;
   }

   boolean dispatchKeyEvent(KeyEvent ev) {
      if (preDispatch == null)
         return  false;
      else if (!preDispatch.dispatchKeyEvent(ev)) {
         preDispatch.startDispatch(null);
         preDispatch = null;
      }
      return true;
   }

   void addKeyEventDispatcher() {
      preDispatch = edvec.getKeyHandler();
      //trace("preDispatch = " + preDispatch + " " + edvec.canonname());
      if (preDispatch != null)
         preDispatch.startDispatch(this);
   }

   private FvContext(View vii, TextEdit<OType>  ei) {
      vi = vii;
      edvec = ei;
      //EditContainer.registerListener(this);
      //trace("created new fvc " + this);
   }

   public boolean equals(Position p) {
      if (p == null)
         return false;
      return  edvec.fdes().equals(p.filename)
              && p.x == fileposx && p.y == fileposy;
   }


   static void invalidateBack(UndoHistory.EhMark ehm) {

      for (Iterator<FvContext> fit = fvmap.iterator(); fit.hasNext();)  {
         FvContext fvc = fit.next();
         if (fvc.vis)
            fvc.vi.checkValid(ehm);
      }
   }

   private static void fixCursor(TextEdit  ed) {
      for (HashMap<TextEdit, FvContext> hmap : fvmap.tmap()) {
         FvContext fv = hmap.get(ed);
         if (null != fv)
            fv.cursorabs(fv.fileposx, fv.fileposy); // fix up cursor position
      }
   }

   void fixCursor() { // should be called with first in chain
      fixCursor(edvec);
   }



   private static class QuitClass implements Runnable {
      public void run() {

         try {
            if (!EventQueue.biglock2.tryLock(2, TimeUnit.SECONDS))
               trace("failed to acquire big lock, try and exit anyway");
            disposeAll(true);
         } catch (Exception e) {
            trace("exit threw " + e);
            e.printStackTrace();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }

   private static void disposeAll(boolean ignoreLock) throws IOException {
      if (!ignoreLock)
         EventQueue.biglock2.assertOwned();

      Set<TextEdit> allEdits = new HashSet<TextEdit>();

      for (Iterator<FvContext> fit = fvmap.iterator(); fit.hasNext();)
         allEdits.add(fit.next().edvec);

      for (TextEdit ev : allEdits)
         try {
            //trace("disposing in fvc quit" + ev);
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

   static void dispose(TextEdit  ed) throws IOException {
      //trace("disposing " + ed + " currfvc " + currfvc);

      fvmap.remove(ed);
      ed.disposeFvc();
      if (currfvc.edvec == ed)
         defaultFvc.setCurrView();
   }

   static FvContext dispose(View vi) { // should be called with first in chain
      //trace("removing " + vi);
      FvContext retval = null;
      if (currfvc.vi == vi)
         retval = nextView();
      fvmap.remove(vi);
      //else
      //   for (FvContext fvc :ehash.values())
      //      EditContainer.unRegisterListener(fvc);
      return retval;

   }

   private void wrapInc(int val) {
      int ypos = (fileposy + val) % edvec.readIn();
      if (ypos == 0)
         ypos = (val > 0)
                ? 1
                : edvec.readIn() - 1;

      cursoryabs(ypos);
   }

   FvContext switchContext(TextEdit  ev, int incval) {
      FvContext newcontext = fvmap.get(vi, ev);
      if (newcontext == null) {
         newcontext = new FvContext(vi, ev);
         fvmap.put(newcontext);
      } else {
         if (newcontext != this)
            newcontext.wrapInc(incval);
      }

      return newcontext;
   }

   static FvContext getcontext(View viloc, TextEdit  te) {
      //trace("fvcontext.getcontext " + e + " and " + viloc);

      FvContext context = fvmap.get(viloc, te);
      if (defaultFvc == null) {
         defaultFvc = new FvContext(viloc, defaultText);
         fvmap.put(defaultFvc);
      }

      if (context == null) {
         context = new FvContext(viloc, te);
         fvmap.put(context);
      }
      return context;
   }

   static String getCurrState() {
      StringBuilder sb = new StringBuilder(
         currfvc.fileposy + "," +  (currfvc.fileposx + 1));
      //trace("char = " + (int)((String)currfvc.edvec.at(currfvc.inserty())).charAt(currfvc.insertx()));
      //trace("sb " + sb);
      currfvc.edvec.addState(sb);
      return sb.toString();
   }

   public Object at() {
      //trace("fvcontext.getcurrentObject fvc = " + toString() );

      if (fileposy < 1)
         throw new RuntimeException(
            "invalid fileposy " + fileposy);

      if (!edvec.contains(fileposy))
         throw new RuntimeException(
            "invalid fileposy " + fileposy);

      if (!edvec.contains(1))
         throw new RuntimeException("editvec missing a line");

      return edvec.at(fileposy);
   }


   public Object at(int index) {
      //trace("fvcontext.getcurrentObject fvc = " + toString() );
      return edvec.at(index);
   }

   Position getPosition(String description) {
      return new Position(fileposx, fileposy, edvec.fdes(), description);
   }

   public int getCurrentIndex() {
      if (!edvec.contains(fileposy))
         cursoryabs(edvec.finish() - 1);
      if (!edvec.contains(1))
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
      //trace("cursorabs pos = " + pos);
      //cursor(pos.x-fileposx,pos.y-fileposy);
      cursor2abs(pos.x, pos.y);
   }

   void cursorabs(int x, int y) {
      //trace("cursorx " + x);
      //cursor(x-fileposx,y-fileposy);
      cursor2abs(x, y);
   }

   void cursorx(int x) {
      //trace("cursorx " + x);
      //cursor(x,0);
      cursor2abs(fileposx + x, fileposy);
   }

   void cursorxabs(int x) {
      //trace("cursorxabs " + x);
      //cursor(x-fileposx,0);
      cursor2abs(x, fileposy);
   }

   void cursoryabs(int y) {
      cursory(y - fileposy);
   }

   void cursory(int yoffset) {
      //trace("cursory yoffset = " + yoffset + " fvc " + this);
      //cursor(0,yoffset);

      int newy = inrange(yoffset + fileposy, 1, edvec.readIn() - 1);
      yoffset = newy - fileposy;
      fileposy = newy;
      if (vis)  {
         int newx = fileposx + vi.yCursorChanged(yoffset);
         fileposx = inrange(newx, 0, edvec.at(fileposy).toString().length());
      }
   }

   private void cursor2abs(int newx, int newy) {
      //trace("newx = " + newx + " newy = " + newy + " this " + this);
      // adjust the insertion point
      int yold = fileposy;
      fileposy = inrange(newy, 1, edvec.readIn() - 1);
      fileposx = inrange(newx, 0, edvec.at(fileposy).toString().length());
      if (vis)
         vi.cursorChanged(fileposy - yold);
   }

   void placeline(int lineno, float amount) {
      if (vis)
         vi.placeline(lineno, amount);
   }

//   void setVisible(boolean visi) {
//      vis = visi;
//   }

   public void insertStrings(ArrayList<String> obarray, boolean after) {
      edvec.insertStrings(obarray, fileposy + (after ? 1 : 0));
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

   static void trace(String str) {
      Tools.trace(str, 1);
   }

}
