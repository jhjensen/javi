package javi.awt;

import java.awt.AWTKeyStroke;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Label;
import java.awt.LayoutManager;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Iterator;
import java.awt.event.KeyEvent;

import javi.CommandEvent;
import javi.EventQueue;
import javi.ExitEvent;
import javi.ExitException;
import javi.FileList;
import javi.FvContext;
import javi.InputException;
import javi.Rgroup;
import javi.StringIoc;
import javi.TextEdit;
import javi.UI;
import javi.UndoHistory;
import javi.View;
import history.Tools;

import static history.Tools.trace;

public final class AwtInterface extends UI implements java.io.Serializable,
   WindowListener, FocusListener, ActionListener,
   EventQueue.Idler {


   private static AwtInterface instance;
   private void common() {
      new Commands();
      EventQueue.registerIdle(this);
   }

   private void readObject(java.io.ObjectInputStream is) throws
         ClassNotFoundException, IOException {
      is.defaultReadObject();
      if (fullFrame != null) {
         GraphicsDevice[] devs = java.awt.GraphicsEnvironment
            .getLocalGraphicsEnvironment().getScreenDevices();

         currdev = devs[0];
      }

      common();
   }

   public void iRestoreState(ObjectInputStream is) throws
         IOException, ClassNotFoundException {
      AwtFontList.restoreState(is);
   }

   public void iSaveState(ObjectOutputStream os) throws IOException {
      AwtFontList.saveState(os);
   }

   public AwtInterface() {
      if (instance != null)
         throw new RuntimeException("attempt to create two Awt singletons");
      instance = this;
      //super("vi:");
      //outfor 1.4 FocusManager.disableSwingFocusManager();
      //1.4setUndecorated(true);
      //fr.setUndecorated(true);
      //FontList.updateFont(); //??? avoid calling this?

      AwtFontList.init();
      frm = initfrm("normal");

      normalFrame = frm;
      common();
      new Initer().postWait();

      //trace("this = " + this + " fr = " + fr);
   }


   private static java.awt.EventQueue eventQueue =
      java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue();

   abstract class RunAwt extends AWTEvent implements Runnable {
      public static final int eventId = AWTEvent.RESERVED_ID_MAX + 1;

      RunAwt() {
         super(frm, eventId);
      }
      void post() {
         eventQueue.postEvent(this);
      }
   }

   abstract class SyncAwt<OType> extends RunAwt {
      private OType result;
      OType getResult() {
         return result;
      }

      SyncAwt() {
         super();
      }

      SyncAwt<OType> postWait() {
         synchronized (this) {
            int holdCount = EventQueue.biglock2.getHoldCount();
            for (int i = 0; i < holdCount; i++)
               EventQueue.biglock2.unlock();

            EventQueue.biglock2.assertUnOwned();
            post();
            try {
               wait();
            } catch (InterruptedException e) {
               trace("ignoring InterruptedException");
            }
            for (int i = 0; i < holdCount; i++)
               EventQueue.biglock2.lock();
         }
         return this;
      }

      abstract OType doAwt();

      public void run() {
         //trace("handleDiff fileObj " +fileObj + " backObj "  + backObj);

         result = doAwt();
         synchronized (this) {
            notify();
            //trace("instance " + instance + " flag " + diaflag);
         }
      }
   }

   class Commands extends Rgroup   {
      private final String[] rnames = {
         "",
         "togglestatus" ,
         "va",
         "van",
         "vd",
         "vn", //5
         "fullscreen"
      };
      Commands() {
         register(rnames);
      }

      public Object doroutine(int rnum, Object arg, int count, int rcount,
            FvContext fvc, boolean dotmode) throws InputException {
         switch (rnum) {
            case 1:
               itoggleStatus();
               return null;
            case 2:
               iaddview(false, fvc);
               return null;
            case 3:
               iaddview(true, fvc);
               return null;
            case 4:
               delview(fvc);
               return null;
            case 5:
               inextView(fvc);
               return null;
            case 6:
               fullScreen();
               return null;

            default:
               throw new RuntimeException("doroutine called with " + rnum);
         }
      }


   }
   private TestFrame frm;
   private TestFrame fullFrame;
   private final TestFrame normalFrame;
   private int viewCount = 0;
   private transient GraphicsDevice currdev;
   private transient FileDialog fdialog;
   private transient PopupMenu popmenu; // the menubar
   private transient PopString psinst;
   private transient ChoseWrt chinst;
   private transient Diff rdinst;
   private transient StatusBar statusBar;
   private transient FvContext tfc;  // command context

   public static class ForceIdle extends EventQueue.IEvent {
      public void execute() throws ExitException {
      }
   }

   class TestFrame extends  Frame {

      private final String name;

/*
      void paintViews() {
         int ccount = getComponentCount();
         for (int i = 0; i < ccount; i++) {
            Component cp = getComponent(i);
            if (cp instanceof View)
               ((View) cp).repaint();
         }
      }
*/

      TestFrame(String str, String namei) {
         super(str);
         name = namei;
         HashSet<AWTKeyStroke> keyset = new HashSet<AWTKeyStroke>(
            getFocusTraversalKeys(
               KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));

         for (Iterator it = keyset.iterator(); it.hasNext();) {
            AWTKeyStroke key = (AWTKeyStroke) (it.next());
            if (key.getKeyCode() == KeyEvent.VK_TAB
                  && key.getModifiers() == 0)
               it.remove();
         }

         setFocusTraversalKeys(
            KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, keyset);

         enableInputMethods(false);
         enableEvents(AWTEvent.KEY_EVENT_MASK
            | AWTEvent.MOUSE_EVENT_MASK
            //| AWTEvent.MOUSE_WHEEL_EVENT_MASK
            | AWTEvent.WINDOW_EVENT_MASK);
         //enableEvents(0xffffffffffffffffl);
      }

      public String toString() {
         return name + super.toString();
      }

      private int fullwidth(Component cp, int yleft,
                                  int xsize, Insets inset) {

         Dimension prefSize = cp.getPreferredSize(); // really to get height
         prefSize.width = xsize - inset.left - inset.right;
         if (!cp.getSize().equals(prefSize)) {
            cp.setSize(prefSize);
            trace("full width set size " + cp.getSize() + " " +  cp);
         }
         return cp.isVisible()
                ? yleft - prefSize.height
                : yleft;
      }

      public Dimension getPreferredSize() {

         //trace ("preferredSize getGraphicsConfiguration()  "+ getGraphicsConfiguration());
         Toolkit kit = Toolkit.getDefaultToolkit();
         Insets inset = getInsets();
         if (inset.top == 0)
            inset =   kit.getScreenInsets(getGraphicsConfiguration());

         Dimension fsize = new Dimension(inset.right + inset.left,
                                         inset.top + inset.bottom);
         OldView atv = (OldView) tfc.vi;
         Component cmdComp = atv.getComponent();
         if (cmdComp.isVisible())
            fsize.height +=  cmdComp.getPreferredSize().height;
         if (statusBar.isVisible())
            fsize.height +=  statusBar.getPreferredSize().height;

         int viewheight = 0;
         int ccount = getComponentCount();
         for (int i = 0; i < ccount; i++) {
            Component cp = getComponent(i);
            //trace("component " + cp);
            if ((cp instanceof OldView.MyCanvas) && (cp != cmdComp)) {
               Dimension cpsize = cp.getPreferredSize();
               //trace("component prefsize " + cpsize);
               fsize.width += cpsize.width;
               if (cpsize.height > viewheight)
                  viewheight = cpsize.height;
                  //trace("viewheight " + viewheight);
            }
         }

         fsize.height += viewheight;

         //trace("returning " +fsize);
         return fsize;
      }

      public void setSize(int width, int height) {
         //trace("!!!!!!!!! frame setSize ("+ width + "," + height +")");
         super.setSize(width, height);
      }

      public void setCompSize(int width, int height) {
         //trace("frame setCompsize ("+ width + "," + height +")");
         //trace("tfc " + tfc);
         //trace("statusBar " + statusBar + " pref size " + getPreferredSize());
         //trace("statusBar isVisible" + statusBar.isVisible());
         Insets inset = getInsets();
         int viewHeight = height - inset.top - inset.bottom;


         OldView atv = (OldView) tfc.vi;
         Component cmdComp = atv.getComponent();
         if (tfc.vi.isVisible())
            viewHeight -=  cmdComp.getPreferredSize().height;

         if (statusBar.isVisible())
            viewHeight -=  statusBar.getPreferredSize().height;

         Dimension viewSize = new Dimension(
            (width - inset.left - inset.right) / viewCount, viewHeight);

         int ccount = getComponentCount();
         for (int i = 0; i < ccount; i++) {
            Component cp = getComponent(i);
            //trace("component " + cp);
            if ((cp instanceof OldView.MyCanvas) && (cp != cmdComp)) {
               if (!cp.getSize().equals(viewSize)) {
                  //trace("setting view size " + viewSize);
                  cp.setSize(viewSize);
               }
            }
         }
      }

      @SuppressWarnings("fallthrough")
      public void processEvent(AWTEvent ev) {
         //trace("ev " + ev + "  has focus " + hasFocus());
         switch (ev.getID()) {
            case KeyEvent.KEY_PRESSED:
               if (ev instanceof KeyEvent) {
                  KeyEvent kev = (KeyEvent) ev;
                  if ((kev).getKeyChar() == KeyEvent.CHAR_UNDEFINED)
                     switch ((kev).getKeyCode()) {
                        case KeyEvent.VK_SHIFT:
                        case KeyEvent.VK_CONTROL:
                        case KeyEvent.VK_ALT:
                           super.processEvent(ev);
                           return;
                        default:
                           break;
                     }
//???            if (fcontext.dispatchKeyEvent(kev))
                  //???        break;
               }
               EventQueue.insert(ev);
               break;
            case KeyEvent.KEY_RELEASED:
            case KeyEvent.KEY_TYPED:
            case WindowEvent.WINDOW_ACTIVATED:
            case WindowEvent.WINDOW_DEACTIVATED:
            case WindowEvent.WINDOW_OPENED:
            case WindowEvent.WINDOW_CLOSED:
               break;

            case FocusEvent.FOCUS_LOST:
               EventQueue.focusLost();
               break;

            case FocusEvent.FOCUS_GAINED:
               EventQueue.focusGained();
               break;

            case WindowEvent.WINDOW_CLOSING:
               EventQueue.insert(new ExitEvent());
               break;

            case RunAwt.eventId:
               ((RunAwt) ev).run();
               break;

            default:
               trace("unhandled event ev " + ev + "  has focus "
                  + hasFocus() + " insets " + getInsets());

               super.processEvent(ev);
         }
      }
   }

   private void flusher(boolean total) {
      if (total) {
         if (tfc != null) {
            OldView atv = (OldView) tfc.vi;
            Component cmdComp = atv.getComponent();
            frm.remove(cmdComp);
            tfc.dispose(atv);
            try {
               tfc.dispose(tfc.edvec, null);
            } catch (Exception ex) {
               UI.popError("error in flush", ex);
            }
         }
         if (statusBar != null) {
            frm.remove(statusBar);
            statusBar =  null;
         }
      }
      if (fdialog != null) {
         fdialog.dispose();
         frm.remove(fdialog);
         fdialog = null;
      }
      if (popmenu != null) {
         frm.remove(popmenu);
         popmenu = null;
      }
      if (psinst != null) {
         frm.remove(psinst);
         psinst.dispose();
         psinst = null;
      }
      if (chinst != null) {
         frm.remove(chinst);
         chinst.dispose();
         chinst = null;
      }

      if (rdinst != null) {
         frm.remove(rdinst);
         rdinst.dispose();
         rdinst = null;
      }
   }

   public void actionPerformed(ActionEvent event) {
      //trace("reached actionPerformed " + event);
      EventQueue.insert(new CommandEvent(event.getActionCommand()));
   }

//   public void itemStateChanged(ItemEvent event) {
//      EventQueue.insert(event);
//   }

   class Flusher extends RunAwt {
      private boolean total;
      Flusher(boolean totali) {
         super();
         total = totali;
         synchronized (this) {
            post();
            EventQueue.biglock2.assertUnOwned();
            try {
               wait();
            } catch (InterruptedException e) {
               trace("ignoring interruptedException");
            }
         }
      }

      public void run() {
         //trace("handleDiff fileObj " +fileObj + " backObj "  + backObj);

         synchronized (this) {
            //trace("instance " + instance + " flag " + diaflag);
            flusher(total);
            notify();
            //trace("instance " + instance + " flag " + diaflag);
         }
      }
   }

   public void iflush(boolean total) {
      new Flusher(total);
   }

   private static class Dropper extends DropTarget {
      private static final long serialVersionUID = 1;
      Dropper(Component c) {
         super(c, DnDConstants.ACTION_LINK, null, true);
      }

      public void dragEnter(DropTargetDragEvent dtde)  { /* don't care */
      }
      public void dragExit(DropTargetEvent dte) { /* don't care */
      }
      public void dragOver(DropTargetDragEvent dtde)  { /* don't care */
      }

      public void drop(DropTargetDropEvent dtde)  {
         //ui.trace("" + dtde);
         //ui.trace("" + dtde.getCurrentDataFlavorsAsList());
         dtde.acceptDrop(DnDConstants.ACTION_LINK);
         try {
            //ui.trace("" + dtde.getTransferable().getTransferData(
            //   DataFlavor.javaFileListFlavor).getClass());
            Transferable tran = dtde.getTransferable();
            //trace("flavor count = " + tran.getTransferDataFlavors().length);
            for (DataFlavor flavor : tran.getTransferDataFlavors()) {
               //trace("flavor = " + flavor);
               if (flavor.equals(DataFlavor.javaFileListFlavor)) {
                  EventQueue.insert(new FileList.FileListEvent(
                     (List) tran.getTransferData(flavor)));

                  dtde.dropComplete(true);
                  return;
               } else if (flavor.equals(DataFlavor.stringFlavor)) {
                  String str = tran.getTransferData(flavor).toString();
                  //trace("str len " + str.length() + " " + str);
                  if (str.length() != 0) {
                     UI.reportError("need to implement string for " + str);
                     dtde.dropComplete(true);
                     return;
                  }
               } else
                  trace("unhandled drop flavor " + flavor);
            }

            UI.reportError("drop of unexpected data" + Arrays.toString(
                              tran.getTransferDataFlavors()));

         } catch (UnsupportedFlavorException e) {
            UI.reportError("drop of unexpected data");
            trace("data " + dtde + "\nexception" +  e);
            e.printStackTrace();
         } catch (IOException e) {
            UI.reportError("Unexpected IOerror " + e);
         }
         dtde.dropComplete(false);
      }
      public  void dropActionChanged(DropTargetDragEvent dtde)  {
         /* don't care */
      }
   }

   private TestFrame initfrm(String name) {
      //trace("initfrm");
      TestFrame lFrm = new TestFrame("vi:", name);
      //lFrm.setUndecorated(true);
      //trace("initfrm new Frame sure is slow!!!");
      lFrm.setResizable(true);
      lFrm.setLayout(new Layout());
      //frm.setBackground(AtView.background);
      //frm.setForeground(AtView.foreground);

      lFrm.setFont(AwtFontList.getCurr(null));
      lFrm.addFocusListener(this);
      lFrm.setDropTarget(new Dropper(lFrm));
      return lFrm;
   }



   static void mvcomp(Container from, Container to) {
      for (Component comp : from.getComponents()) {
         //trace("moving comp " + comp);
         to.add(comp);
      }
      from.removeAll();
   }

   class FScreen extends RunAwt {
      FScreen() {
         super();
         post();
      }
      public void run() {
         //trace("full Screen " + frm);
         EventQueue.biglock2.lock();
         try {
            flusher(false);

            if (frm != normalFrame) {
               //trace("changing fullscreen to normal fullFrame " + fullFrame + " normalFrame " + normalFrame);
               currdev.setFullScreenWindow(null);
               mvcomp(fullFrame, normalFrame);
               frm = normalFrame;
               frm.setVisible(true);
            } else {
               frm.setVisible(false);
               //trace("!!!!!!enter fullscreen");
               if (fullFrame == null) {
                  fullFrame = initfrm("fullFrame");
                  fullFrame.setFont(frm.getFont());
                  fullFrame.setUndecorated(true);
                  GraphicsDevice[] devs =
                     java.awt.GraphicsEnvironment.
                     getLocalGraphicsEnvironment().getScreenDevices();
                  int maxarea = 0;
                  int biggestScreen = 0;

                   // find biggest screen
                  trace("dump graphics devices");
                  int scount = 0;
                  for (GraphicsDevice dev : devs)  {
                      //System.err.println("   dev "+ dev);
                     java.awt.DisplayMode dm = dev.getDisplayMode();
                     int area = dm.getWidth() * dm.getHeight();
                     if (area > maxarea) {
                        maxarea = area;
                        biggestScreen = scount;
                     }
                     scount++;
                  }

                  currdev = devs[biggestScreen];
                  fullFrame.setResizable(false);
               }
               mvcomp(normalFrame, fullFrame);
               frm = fullFrame;
               currdev.setFullScreenWindow(fullFrame);
               fullFrame.validate();
            }
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }
   void fullScreen() {
      new FScreen();
   }

   class Initer extends SyncAwt {
       // makeing this run sychronously makes the window become visible about 20-30
       // ms quicker, but ready for events quit 90 ms slower

      public Object doAwt() {
         //trace("start initer");
         OldView tfview = new OldView(false);
         Component cmdComp = tfview.getComponent();

         cmdComp.setVisible(false);
         frm.add(cmdComp, 0);
         frm.setComponentZOrder(cmdComp, 0);
         EventQueue.biglock2.lock();
         try {
            StringIoc sio = new StringIoc("command buffer", null);
            TextEdit<String> cmbuff = new TextEdit<String>(sio, sio.prop);
            cmdComp.setFont(AwtFontList.getCurr(null));
            tfview.setSizebyChar(80, 1);
            tfc = FvContext.getcontext(tfview, cmbuff);
            tfc.setCurrView();
            View vi = mkview(false);
            //trace("connecting " + FileList.getContext(vi).at() + "vi " + vi);
            iconnectfv((TextEdit) FileList.getContext(vi).at(), vi);
         } catch (InputException e) {
            throw new RuntimeException("can't recover iaddview", e);
         } finally {
            EventQueue.biglock2.unlock();
         }

         frm.requestFocus();
         statusBar = new StatusBar();
         statusBar.setVisible(false);
         frm.add(statusBar, 0);
         trace("setting visible");
         ishow();
         return null;
      }

   }

   public FvContext iconnectfv(TextEdit file, View vi) throws InputException {
      //trace("vic.connectfv " + file + " vi " + vi);
      if (tfc != null && vi == tfc.vi)
         throw new InputException(
            "can't change command window to display other data");
      isetTitle(file.toString());

      FvContext fvc = FvContext.getcontext(vi, file);
      fvc.setCurrView();
      return fvc;
   }

   private View iaddview(boolean newview, FvContext fvc) throws
         InputException {

      View ta = mkview(newview);
      iconnectfv(fvc.edvec, ta);
      return ta;
   }

   private View mkview(boolean newview) throws InputException {
      //view ta = newview ? (view) new TabbedTextLayout() : new oldview();
      //trace("mkview");
      OldView ta = new OldView(true);
      Component cmdComp = ta.getComponent();
      viewCount++;
      cmdComp.setFont(AwtFontList.getCurr(null));
      ta.setSizebyChar(AwtFontList.getWidth(), AwtFontList.getHeight());
      frm.add(cmdComp, -1);
      //frm.setComponentZOrder(ta,1);
      //trace("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! about to set visible");
      frm.validate();
      cmdComp.setVisible(true);
      return ta;
   }

   private void delview(FvContext fvc) {
      //trace("viewCount " + viewCount);
      if (viewCount > 1) {
         trace("removing " + fvc.vi);
         iremove(fvc.vi);
         FvContext newfvc = FvContext.dispose(fvc.vi);
         if (newfvc != null)
            isetTitle(newfvc.edvec.toString());
         frm.validate();
      }
   }

   public void isetView(FvContext fvc) {
      fvc.setCurrView();
      isetTitle(fvc.edvec.toString());
   }

   void inextView(FvContext fvc) {
      FvContext newfvc = FvContext.nextView();
      isetTitle(newfvc.edvec.toString());
   }


   public void isetStream(Reader inreader) { /* unimplemented */ }

   public void iremove(View vi) {
      viewCount--;
      OldView ta = (OldView) vi;
      Component cmdComp = ta.getComponent();
      frm.remove(cmdComp);
   }

   public void irepaint() {
      int ccount = frm.getComponentCount();
      for (int i = 0; i < ccount; i++) {
         Component cp = frm.getComponent(i);
         if (cp.isVisible())
            cp.repaint();
      }
      frm.repaint();
   }

   public void ishow() {
      trace("!!! setting frm visible ");
      frm.setSize(frm.getPreferredSize());
      frm.setVisible(true);
      //trace("!!! done set frm visible insets " + frm.getInsets());
   }

   public boolean iisVisible() {
      return frm.isVisible();
   }


   void isetTitle(java.lang.String title) {
      frm.setTitle(title);
   }

   public void idispose() {
      frm.dispose();
   }

   public void itransferFocus() {
      frm.transferFocus();
   }

   public FvContext istartComLine() {
      //tfc.setCurrView();
      OldView ta = (OldView) tfc.vi;
      Component cmdComp = ta.getComponent();
      cmdComp.setVisible(true);
      cmdComp.repaint();
      return tfc;
   }

   public String iendComLine() {
      OldView ta = (OldView) tfc.vi;
      Component cmdComp = ta.getComponent();
      cmdComp.setVisible(false);
      statusBar.clearlines();
      //trace("comline:"+ tfc.at().toString());
      return tfc.at().toString();
   }

   public boolean iisGotoOk(FvContext fvc) {
      return fvc != tfc;
   }

   class IdleEvent extends RunAwt {
      IdleEvent() {
         super();
         post();
      }

      public void run() {
         EventQueue.biglock2.lock();
         try {
            if (statusBar != null && statusBar.isVisible())
               statusBar.repaint();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

   }

   public void idle() {
      new IdleEvent();
   }

   class Validate extends RunAwt {
      Validate() {
         super();
         post();
      }
      public void run() {
         frm.validate();
      }
   }

   public void itoggleStatus() {
      //trace("toggle status " + statusBar);
      statusBar.setVisible(!statusBar.isVisible());

      new Validate();
   }

   public void iclearStatus()  {
      statusBar.clearlines();
      new Validate();
   }

   public void istatusaddline(String str) {
      statusBar.addline(str);
      new Validate();
   }

   public void istatusSetline(String str) {
      statusBar.setline(str);
      new Validate();
   }

   public void itoFront() {

      frm.setVisible(false);
      frm.setVisible(true);
      frm.toFront();
      frm.requestFocus();
   }

   public void ichooseWriteable(String filename) {

      if (chinst == null)
         chinst = new ChoseWrt(this);
      chinst.chosefile(filename);
   }

   public void isetViewSize(View vi, int width, int height) {
      //trace("width " + width + " height " + height + " view = " + vi);
      AwtFontList.setDefaultFontSize(width, height);
      OldView ta = (OldView) vi;
      ta.setSizebyChar(width, height);
      if (normalFrame == frm
            && !((frm.getExtendedState() & Frame.MAXIMIZED_BOTH)
            == Frame.MAXIMIZED_BOTH))
         frm.setSize(frm.getPreferredSize());

      new Validate();
   }

   class SetFont extends RunAwt {
      private final Font font;
      private final View vi;

      SetFont(Font fonti, View vii) {
         font = fonti;
         vi = vii;
         post();
      }

      public void run() {
         popmenu = null;
         psinst = null;
         chinst = null;
         rdinst = null;
         frm.setFont(font);
         OldView ta = (OldView) vi;
         Component cmdComp = ta.getComponent();
         cmdComp.setFont(font);
         int cpi = frm.getComponentCount();
         for (cpi = cpi > 2 ? 1 : cpi - 1; cpi >= 0; --cpi) {
            Component cp = frm.getComponent(cpi);
            if (null != frm.getComponent(cpi))
               cp.setFont(font);
         }
         if (normalFrame == frm
               && !((frm.getExtendedState() & Frame.MAXIMIZED_BOTH)
               == Frame.MAXIMIZED_BOTH))
            frm.setSize(frm.getPreferredSize());

         frm.validate();
      }
   }

   public static void fontChange(Font font, View vi) {
      instance.new SetFont(font, vi);
   }

   private static class MyMenuItem extends MenuItem {

      private static final long serialVersionUID = 1;

      MyMenuItem(String label, String command, Menu men,
                 ActionListener listen) {

         super(label);
         addActionListener(listen);
         setActionCommand(command);
         men.add(this);
      }
   }

   public void ishowmenu(int x, int y) {
      if (popmenu == null) {

         popmenu = new PopupMenu();

         Menu filem = new Menu("File");
         new MyMenuItem("Open", "e", filem, this);
         new MyMenuItem("Quit", "q", filem, this);
         new MyMenuItem("Save file", "w", filem, this);
         popmenu.add(filem);

         Menu sizem = new Menu("Size");
         for (int i = 4; i < 20; i++)
            new MyMenuItem(Integer.toString(i), "fontsize " + i,
               sizem, this);
         popmenu.add(sizem);

         Menu typem  = new Menu("Type");
         for (String mname : AwtFontList.typest)
            new MyMenuItem(mname, "fonttype "
               + mname, typem, this);
         popmenu.add(typem);

         new MyMenuItem("paste", null, popmenu, this);
         new MyMenuItem("fullscreen", null, popmenu, this);

         frm.add(popmenu);

         popmenu.addActionListener(this);
      }
      popmenu.show(frm, x, y);
   }

   boolean dopop(String str) {
      if (null == psinst)
         psinst = new PopString(frm);
      trace("popping string " + str);
      return psinst.pop(str);
   }

   class Popper extends SyncAwt<Boolean> {
      private String str;
      Popper(String stri) {
         super();
         str = stri;
         trace("str " + str);
         postWait();
      }

      Boolean doAwt() {
         trace("str " + str);
         return dopop(str);
      }
   }

   public boolean ipopstring(String str) {

      try {

         throw new Exception("");
      } catch (Exception ex) {

         StackTraceElement[] tr = ex.getStackTrace();
         for (StackTraceElement elem : tr)  {
            if  (elem.getMethodName().indexOf("paint") != -1)
               return dopop(str);
         }
      }
      trace("str " + str);
      return new Popper(str).postWait().getResult();
   }

   static class NDialog extends Dialog implements ActionListener {
      private static final long serialVersionUID = 1;
      private NButton resb = null;
      final NButton getRes() {
         return resb;
      }

      NDialog(Frame frm, String caption, LayoutManager lay) {
         super(frm, caption, true);
         setLayout(lay);
      }

      public void dispose() {
         super.dispose();
      }

      public void actionPerformed(ActionEvent e) {
         resb = (NButton) e.getSource();
         //trace("set resb to " + resb + " lable = " + resb.getLabel());

         setVisible(false);
      }

      static class NText extends TextField {
         private static final long serialVersionUID = 1;
         private Dialog dia;
         NText(String s, NDialog nd) {
            super(s);
            nd.add(this);
            addActionListener(nd);
         }
      }

      static class NButton extends java.awt.Button  {
         private static final long serialVersionUID = 1;

         NButton(String s, NDialog nd) {
            super(s);
            nd.add(this);
            addActionListener(nd);
         }
      }

      public void windowClosing(WindowEvent e) {
         //trace("" + e);
         setVisible(false);
      }

      public void setVisible(boolean vis) {
         if (vis)
            resb = null;
         super.setVisible(vis);
      }
   }

   private static class PopString extends NDialog {
      private static final long serialVersionUID = 1;
      private TextArea ta = new TextArea("", 30, 80);
      private NButton ign = new NButton("Ignore", this);
      private NButton rThrow = new NButton("reThrow", this);

      PopString(Frame frm) {
         super(frm, "exception trace", new FlowLayout());
         add(ta);
      }

      boolean pop(String s) {
         if (isVisible())
            return false;
         ta.setText(s);
         this.pack();
         trace("popstring visible ");
         setVisible(true);
         trace("popstring invisible ");
         return getRes() == rThrow;
      }
   }

   static class ModVal extends NDialog {
      private static final long serialVersionUID = 1;

      private NText tf;

      ModVal(final String caption, final String units,
             final String []buttonVals, final long limit, Frame frame) {

         super(frame, caption, new FlowLayout());
         tf = new NText(Long.toString(limit), this);
         Label cp = new Label(caption);
         add(cp);
         Label unl = new Label(units);
         add(unl);
         for (String bv : buttonVals)
            new NButton(bv, this);
         pack();
         Dimension d1 = cp.getPreferredSize();
         Dimension d2 = tf.getPreferredSize();
         Dimension d3 = unl.getPreferredSize();
         this.setSize(d1.width + d2.width + d3.width + 50, d2.height * 5);
         setVisible(true);
      }
   }

   public Result ireportModVal(final String caption, final String units,
         final String []buttonVals, final long limit) {

      ModVal b1 = new ModVal(caption, units, buttonVals, limit, frm);
      return new Result(Integer.parseInt(
         b1.tf.getText()), b1.getRes().getLabel());
   }

   private static class ChoseWrt extends NDialog {
      private static final long serialVersionUID = 1;

      private Label writelabel = new Label();;

      private NButton svnb =   new NButton("use svn base ", this);
      private NButton backb =   new NButton("back up to .orig file", this);
      private NButton checkoutb =   new NButton(
         "checkout with version control", this);

      private NButton forceWriteable =
         new NButton("force writeable", this);
      private NButton nothing =   new NButton("do nothing", this);

      ChoseWrt(AwtInterface jwin) {

         super(jwin.frm, "Read only file action", new FlowLayout());
         add(writelabel);
      }

      void chosefile(String filename) {
         String tstring =
            "You have tried to write to a read only file:" + filename;

         this.setTitle(tstring);

         writelabel.setText(tstring);

         this.pack();
         Dimension d = writelabel.getPreferredSize();
         this.setSize(d.width, d.height * 7);
         setVisible(true);
         diaflag =
            getRes() == null
               ? UI.Buttons.IOERROR
            : getRes() == svnb
               ? UI.Buttons.USESVN
            : getRes() == backb
               ? UI.Buttons.MAKEBACKUP
            : getRes() == checkoutb
               ? UI.Buttons.CHECKOUT
            : getRes() == forceWriteable
               ? UI.Buttons.MAKEWRITEABLE
            : getRes() == nothing
               ? UI.Buttons.DONOTHING
            : UI.Buttons.IOERROR;
      }

   }

   class HandleDiff extends RunAwt {

      private final String filename;
      private final String backupname;
      private final int linenum;
      private final Object filevers;
      private final Object backupvers;
      private final UndoHistory.BackupStatus status;

      HandleDiff(String filenamei, int linenumi, Object fileversi,
            Object backupversi, UndoHistory.BackupStatus statusi,
            String backupnamei) {
         super();
         synchronized (this) {
            filename = filenamei;
            backupname = backupnamei;
            linenum = linenumi;
            filevers = fileversi;
            backupvers = backupversi;
            status = statusi;
            post();
            EventQueue.biglock2.assertUnOwned();
            try {
               wait();
            } catch (InterruptedException e) {
               trace("ignoring InterruptedException");
            }
         }
      }

      public void run() {
         //trace("handleDiff fileObj " +fileObj + " backObj "  + backObj);

         if (rdinst == null)
            rdinst = new Diff(frm);
         while (true) {
            rdinst.pop(filename, linenum, filevers, backupvers, status);
            if (diaflag == UI.Buttons.USEDIFF) {
               try {
                  String cmd = System.getProperties().getProperty(
                                  "java.javi.diffcmd",
                                  //"C:\\Progra~2\\SourceGear\\DiffMerge\\DiffMerge.exe ");
                                  //"C:\\Progra~1\\Beyond~1\\BC2.exe ";
                                  "C:\\Progra~1\\Beyond~1\\Bcomp.exe ");
                  //"C:\\Progra~1\\Beyond~1\\BC2.exe ",  filename, backupname
                  //"cmd /c c:\\PROGRA~1\\Araxis\\ARAXIS~1.0PR\\Merge.exe /NoSplash /NoSplashDelay "
                  //"kdiff3 "
                  //"cmd /c d:\\progra~1\\araxis\\araxis~1\\merge.exe /NoSplash /NoSplashDelay ";
                  String [] lstr =  {cmd, filename, backupname};
                  Tools.execute(lstr);
               } catch (IllegalArgumentException e) {
                  trace("ui.reportDiff caught exception " + e);
                  e.printStackTrace();
               } catch (IOException e) {
                  trace("ui.reportDiff caught exception " + e);
                  e.printStackTrace();
               }
            } else
               break;
         }

         synchronized (this) {
            //trace("instance " + instance + " flag " + diaflag);
            notify();
            //trace("instance " + instance + " flag " + diaflag);
         }
      }
   }

   public void ireportDiff(String filename, int linenum, Object filevers,
         Object backupvers, UndoHistory.BackupStatus status,
         String backupName) {
      new HandleDiff(filename, linenum, filevers, backupvers,
         status, backupName);
   }

   private static class Diff extends NDialog {
      private static final long serialVersionUID = 1;
      private Label replab1 = new Label();
      private Label replab2 = new Label();
      private Label sa1 = new Label();
      private Label sa2 = new Label();

      private NButton okbut = new NButton("OK", this);
      private NButton backbut = new NButton("use backup version", this);
      private NButton filebut = new NButton("use file version", this);
      private NButton diffbut = new NButton("launch diff", this);

      private String l1, l2, s1, s2;
//   public void setVisible(boolean vf) {
//      if (!vf)
//         Thread.dumpStack();
//      super.setVisible(vf);
//   }
      UI.Buttons mapbut() {
         //trace("backbut" + backbut);
         //trace("okbut" + okbut);
         //trace("filebut" + filebut);
         //trace("diffbut" + diffbut);

         //trace("res" + res);
         return getRes() == null
               ? UI.Buttons.IOERROR
            : getRes() == okbut
               ? UI.Buttons.OK
            : getRes() == backbut
               ? UI.Buttons.USEBACKUP
            : getRes() == filebut
               ? UI.Buttons.USEFILE
            : getRes() == diffbut
               ? UI.Buttons.USEDIFF
            : UI.Buttons.IOERROR;
      }


      private enum Buttons {
         CHECKOUT , MAKEWRITEABLE , DONOTHING , MAKEBACKUP , USEFILE ,
         USEBACKUP , USEDIFF , OK , WINDOWCLOSE , IOERROR , USESVN
      };

      Diff(Frame frm)  {

         super(frm, "file difference problem", new GridBagLayout());

         GridBagLayout gb = (GridBagLayout) getLayout();
         GridBagConstraints gbc = new GridBagConstraints();
         gbc.anchor = GridBagConstraints.WEST;
         gbc.weightx = 1.0;
         gbc.weighty = 1.0;
         gbc.gridwidth = 3;
         gb.setConstraints(replab1, gbc);
         int ycount = 1;
         gbc.gridy = ++ycount;
         gb.setConstraints(replab2, gbc);
         gbc.gridy = ++ycount;
         gb.setConstraints(sa1, gbc);
         gbc.gridy = ++ycount;
         gb.setConstraints(sa2, gbc);
         gbc.gridy = ++ycount;
         gb.setConstraints(okbut, gbc);
         gbc.gridwidth = 1;
         gb.setConstraints(diffbut, gbc);
         gb.setConstraints(filebut, gbc);
         gb.setConstraints(backbut, gbc);
         this.add(replab1);
         this.add(replab2);
         this.add(sa1);
         this.add(sa2);
         setinvis();
      }

      void pop(String filename, int linenum, Object filevers,
                Object backupvers, UndoHistory.BackupStatus status) {
//     try {Thread.sleep(150);} catch (Exception e) {} // work around focus problem ???
         this.setTitle("discrepency in backup file:" + filename);
         setinvis();
         if (status.error != null) {
            replab1.setText(
               "corrupt backup file read in as far as possible. "
               + status.error);

            replab1.setForeground(Color.cyan);
            replab1.setVisible(true);
         } else if (!status.cleanQuit || !status.isQuitAtEnd) {

            replab1.setText((
               !status.cleanQuit
                  ? "javi did not exit cleanly. "
                  : "")
               + (!status.isQuitAtEnd
                  ? "There is undo history that is not in effect"
                  : ""));

            replab1.setVisible(true);
            replab1.setForeground(Color.black);
         }
         if (filevers == null && backupvers == null) {
            replab2.setText(
               "the written versions of the file are consistent");

            replab2.setForeground(Color.black);
            okbut.setVisible(true);
         } else {
            l1 = l2 = s1 = s2 = "";
            if (filevers == null) {
               l1 = "backup version has extra lines at end";
               s2 = backupvers.toString();
            } else if (backupvers == null) {
               l1 = "file version has extra lines at end";
               s1 = filevers.toString();
            } else  {
               l1 = "versions differ at line " + linenum + " :";
               s1 = filevers.toString();
               s2 = backupvers.toString();
            }
            replab2.setText(l1);
            sa1.setText(s1);
            sa2.setText(s2);
            sa1.setVisible(true);
            sa2.setVisible(true);
            replab2.setForeground(Color.red);
            backbut.setVisible(true);
            diffbut.setVisible(true);
            filebut.setVisible(true);
         }

         replab2.setVisible(true);

         //  dia.setSize(d.width,d.height*6);
         this.pack();
         //trace("setting visible modality " + getModalityType() + " thread " + Thread.currentThread().getName());
         setVisible(true);
         diaflag = mapbut();
      }

      private void setinvis() {
         okbut.setVisible(false);
         replab1.setVisible(false);
         replab2.setVisible(false);
         backbut.setVisible(false);
         diffbut.setVisible(false);
         filebut.setVisible(false);
         sa1.setVisible(false);
         sa2.setVisible(false);
      }

   }

   public void windowClosing(WindowEvent e) {
      //trace("" + this);
      e.getComponent().setVisible(false);
      diaflag = Buttons.WINDOWCLOSE;
   }

   public void windowClosed(java.awt.event.WindowEvent e) {
      /*trace("" + e); /* dont care */
   }
   public void windowOpened(WindowEvent e) {
      /*trace("" + e);/* dont care */
   } //
   public void windowActivated(WindowEvent e)  {
      /*trace("" + e);/* dont care */
   }   //
   public void windowDeactivated(WindowEvent e) {
      /*trace("" + e); /* dont care */
   } //
   public void windowDeiconified(WindowEvent e) {
      /*trace("" + e); /* dont care */
   } //
   public void windowIconified(WindowEvent e) {
      /*trace("" + e); /* dont care */
   } //

   private class Layout implements LayoutManager, java.io.Serializable {

      public void addLayoutComponent(String s, Component cont) {
         //trace("" + cont);
      }

      public Dimension minimumLayoutSize(Container cont)  {
         return cont.getSize();
      }

      public Dimension preferredLayoutSize(Container cont) {
         return cont.getPreferredSize();
      }

      public void removeLayoutComponent(Component cont) { /*don't care */ }

      private int fullwidth(Component cp, int yleft,
                                  int xsize, Insets inset) {

         Dimension cpsize = cp.getPreferredSize();
         cpsize.width = xsize;
         int height = cpsize.height;
         //trace("yleft decreased by height " + height + " cp " + cp);
         if (!cp.getSize().equals(cpsize)) {
            cp.setSize(xsize - inset.left - inset.right, height);
            //trace("full width set size " + cp.getSize() + " " +  cp);
         }
         Point newloc = new Point(inset.left, yleft - height);
         Point oldloc = cp.getLocation();
         if (!newloc.equals(oldloc)) {
            //trace("full width set location " + newloc + " " +  cp);
            cp.setLocation(newloc);
         }
         //trace("returns " + yleft + cp);
         return cp.isVisible()
                ? yleft - height
                : yleft;
      }

      public void layoutContainer(Container cont)  {
         Insets inset = frm.getInsets();
         //trace("entered layoutContainer insets = " + inset); //Thread.dumpStack(); for(Component comp:frm.getComponents()) trace("   component " + comp);

         if (cont != frm) {
            trace("laying out wrong contaner ! cont = "
                  + cont + " frame " + frm);
            return;
         }

         // what is the point of layout out before we get our insets?
         if (frm  == normalFrame && inset.top == 0)
            return;

         Dimension startSize = frm.getSize();

         //trace("entered layoutContainer insets = " + frm.getInsets()); //Thread.dumpStack(); for(Component comp:frm.getComponents()) trace("   component " + comp);

         frm.setCompSize(startSize.width, startSize.height);

         if (normalFrame == frm
               && !((frm.getExtendedState() & Frame.MAXIMIZED_BOTH)
               == Frame.MAXIMIZED_BOTH)) {
            Dimension pref = frm.getPreferredSize();
            if (!pref.equals(startSize)) {
               //trace("!!!!! setting frame size pref  " + pref + " startSize" + startSize);
               frm.setSize(pref);
               startSize = pref;
            }
         }

         int ccount = frm.getComponentCount();
         //trace("frame size at start of layout " + startSize + " insets " + inset);

         int xsize = startSize.width;
         int ysize = startSize.height;
         //trace("size =  " + frm.getSize());
         int yleft = ysize - inset.bottom;
         //trace("yleft = " + yleft + " inset.top = " + inset.top);
         yleft = fullwidth(statusBar, yleft, xsize, inset); // status
         OldView ta = (OldView) tfc.vi;
         Component cmdComp = ta.getComponent();
         fullwidth(cmdComp, yleft, xsize, inset); // status
         int left = inset.left;
         for (int i = 0; i < ccount; i++) { // views
            Component cp = frm.getComponent(i);
            //trace("processing component " + cp);
            if (cp.isVisible()) {
               if ((cp instanceof OldView.MyCanvas) && (cp != cmdComp)) {
                  Point oldloc = cp.getLocation();
                  Point newloc = new Point(left, inset.top);
                  if (!oldloc.equals(newloc)) {
                     //trace("!!! setting new location " + newloc);
                     cp.setLocation(newloc);
                  }
                  left += cp.getSize().width;
               }
            }
         }
      }
   }

   public void focusLost(FocusEvent e) {
      //trace("focusLost " + e.toString());
   }

   public void focusGained(FocusEvent e) {
      //trace("focusGained " +e);
   }


   class GetFile extends SyncAwt<String> {

      String doAwt()  {

         if (fdialog == null)
            fdialog = new FileDialog(frm, "open new vifile",
               FileDialog.LOAD);
         fdialog.setVisible(true);
         return   fdialog.getFile();
      }
   }

   public String igetFile() {
      return new GetFile().postWait().getResult();
   }

}
