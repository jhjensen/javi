package javi.awt;

import history.Tools;

import java.awt.AWTEvent;
import java.awt.AWTKeyStroke;
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
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Label;
import java.awt.LayoutManager;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.Panel;
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
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import javi.BackupStatus;
import javi.CommandEvent;
import javi.EventQueue;
import javi.ExitEvent;
import javi.ExitException;
import javi.FileList;
import javi.FvContext;
import javi.InputException;
import javi.JeyEvent;
import javi.MiscCommands;
import javi.Rgroup;
import javi.StringIoc;
import javi.TextEdit;
import javi.UI;
import javi.View;
import history.BadBackupFile;

import static history.Tools.trace;

/**
 * Main AWT-based graphical user interface for the Javi editor.
 *
 * <p>AwtInterface is the primary UI implementation, providing:
 * <ul>
 *   <li><b>Window management</b>: Main frame, dialogs, menus</li>
 *   <li><b>Event handling</b>: Keyboard, mouse, focus, window events</li>
 *   <li><b>Text display</b>: Via embedded {@link OldView} canvas</li>
 *   <li><b>Status bar</b>: Command echo, messages via {@link StatusBar}</li>
 *   <li><b>Clipboard</b>: System clipboard integration</li>
 *   <li><b>Drag-and-drop</b>: File drop support</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <p>AwtInterface extends {@link UI} and implements multiple AWT listener interfaces.
 * It's a singleton (enforced by UI base class). The class is large (~1640 lines)
 * and could benefit from refactoring - see todo.md M14.</p>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@code mainframe} - The main Frame window</li>
 *   <li>{@code mfield} - Command line TextField</li>
 *   <li>{@link OldView} - Text rendering canvas</li>
 *   <li>{@link StatusBar} - Status display</li>
 *   <li>{@link AwtFontList} - Font management</li>
 * </ul>
 *
 * <h2>Dialog Methods</h2>
 * <ul>
 *   <li>{@link #ireportDiff} - File/backup conflict resolution</li>
 *   <li>{@link #ichooseWriteable} - Read-only file handling</li>
 *   <li>{@link #iconfirmReload} - Confirm reload from disk</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Most operations run on AWT Event Dispatch Thread. Editor operations
 * must coordinate with {@link EventQueue#biglock2}. Some methods are called
 * from background threads and must handle synchronization.</p>
 *
 * <h2>Known Issues</h2>
 * <ul>
 *   <li>Deadlock potential in file-changed dialog - see BUGS.md B1</li>
 *   <li>Class is too large, should be refactored - see todo.md M14</li>
 * </ul>
 *
 * @see UI
 * @see OldView
 * @see EventQueue
 */
public final class AwtInterface extends UI implements java.io.Serializable,
   WindowListener, FocusListener, ActionListener,
   EventQueue.Idler {

   private void common() {
      new Commands();
      EventQueue.registerIdle(this);
   }

   private void readObject(java.io.ObjectInputStream is) throws
         ClassNotFoundException, IOException {
      is.defaultReadObject();
      if (null != fullFrame) {
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

   public AwtInterface() throws ExitException {
      //super("vi:");
      //outfor 1.4 FocusManager.disableSwingFocusManager();
      //1.4setUndecorated(true);
      //fr.setUndecorated(true);
      //FontList.updateFont(); //??? avoid calling this?

      AwtFontList.init();
      frm = initfrm("normal");

      normalFrame = frm;
      common();
       // do we really need to wait???
      if (null == new Initer().postWait().getResult())
         throw new ExitException();

      //trace("this = " + this + " fr = " + fr);
   }

   private static java.awt.EventQueue eventQueue =
      java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue();

   abstract class RunAwt extends AWTEvent implements Runnable {
      public static final int eventId = AWTEvent.RESERVED_ID_MAX + 1;

      protected RunAwt() {
         super(frm, eventId);
      }

      final void post() {
         eventQueue.postEvent(this);
      }
   }

   abstract class SyncAwt<OType> extends RunAwt {

      private OType result;
      private boolean finished = false;

      final OType getResult() {
         return result;
      }

      final SyncAwt<OType> postWait() {
         synchronized (this) {
            int holdCount = EventQueue.biglock2.getHoldCount();
            for (int ii = 0; ii < holdCount; ii++)
               EventQueue.biglock2.unlock();

            EventQueue.biglock2.assertUnOwned();
            post();
            try {
               while (!finished)
                  wait();
            } catch (InterruptedException e) {
               trace("ignoring InterruptedException");
            }
            for (int ii = 0; ii < holdCount; ii++)
               EventQueue.biglock2.lock();
         }
         return this;
      }

      abstract OType doAwt();

      public final void run() {
         //trace("handleDiff fileObj " +fileObj + " backObj "  + backObj);

         result = doAwt();
         synchronized (this) {
            finished = true;
            notify();
            //trace("instance " + instance + " flag " + diaflag);
         }
      }
   }

   private final class Commands extends Rgroup   {
      private final String[] rnames = {
         "",
         "togglestatus",
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
               new FScreen();
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
   private transient StopProc stpinst;
   private transient Diff rdinst;
   private transient StatusBar statusBar;
   // command context ??? could use FvContext.tfc
   private transient FvContext tfc;

   public static final class ForceIdle extends EventQueue.IEvent {
      public void execute() {
      }
   }

   private final class TestFrame extends  Frame {

      private final String name;

      TestFrame(String str, String namei) {
         super(str);
         name = namei;
         var keyset = new HashSet<AWTKeyStroke>(
            getFocusTraversalKeys(
               KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));

         for (var it = keyset.iterator(); it.hasNext();) {
            var key = it.next();
            if (key.getKeyCode() == KeyEvent.VK_TAB
                  && 0 == key.getModifiers())
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

      public Dimension getPreferredSize() {

         //trace ("preferredSize getGraphicsConfiguration()  "+ getGraphicsConfiguration());
         Toolkit kit = Toolkit.getDefaultToolkit();
         Insets inset = getInsets();
         if (0 == inset.top)
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
         for (int ii = 0; ii < ccount; ii++) {
            Component cp = getComponent(ii);
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
         for (int ii = 0; ii < ccount; ii++) {
            Component cp = getComponent(ii);
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
                  EventQueue.insert(new JeyEvent(
                     JeyEvent.convertExtendedModifiers(kev.getModifiersEx()),
                     kev.getKeyCode(), kev.getKeyChar()));
               }
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
         if (null != tfc) {
            OldView atv = (OldView) tfc.vi;
            Component cmdComp = atv.getComponent();
            frm.remove(cmdComp);
            FvContext.dispose(atv);
            try {
               FvContext.dispose(tfc.edvec, null);
            } catch (Exception ex) {
               UI.popError("error in flush", ex);
            }
            tfc = null;
         }
         if (null != statusBar) {
            frm.remove(statusBar);
            statusBar =  null;
         }
      }
      AwtCircBuffer.initCmd();
      if (null != fdialog) {
         fdialog.dispose();
         frm.remove(fdialog);
         fdialog = null;
      }
      if (null != popmenu) {
         frm.remove(popmenu);
         popmenu = null;
      }
      if (null != psinst) {
         frm.remove(psinst);
         psinst.dispose();
         psinst = null;
      }
      if (null != chinst) {
         frm.remove(chinst);
         chinst.dispose();
         chinst = null;
      }

      if (null != rdinst) {
         frm.remove(rdinst);
         rdinst.dispose();
         rdinst = null;
      }
   }

   public void actionPerformed(ActionEvent event) {
      //trace("reached actionPerformed " + event);
      EventQueue.insert(new CommandEvent(event.getActionCommand()));
   }

   private final class Flusher extends RunAwt {

      private boolean total;
      private boolean flushed = false;

      Flusher(boolean totali) {
         super();
         total = totali;
         synchronized (this) {
            post();
            EventQueue.biglock2.assertUnOwned();
            try {
               while (!flushed)
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
            flushed = true;
            notify();
            //trace("instance " + instance + " flag " + diaflag);
         }
      }
   }

   public void iflush(boolean total) {
      new Flusher(total);
   }

   private static final class Dropper extends DropTarget {
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

      // Set application icon (multiple sizes for better rendering)
      lFrm.setIconImages(IconUtil.createJaviIcons());

      // Set macOS Dock icon using Taskbar API (Java 9+)
      if (java.awt.Taskbar.isTaskbarSupported()) {
         java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
         if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
            taskbar.setIconImage(IconUtil.createJaviIcon(128));
         }
      }

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

   final class FScreen extends RunAwt {
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
               frm.setVisible(false);
               currdev.setFullScreenWindow(null);
               mvcomp(fullFrame, normalFrame);
               frm = normalFrame;
               frm.setVisible(true);
            } else {
               frm.setVisible(false);
               //trace("!!!!!!enter fullscreen");
               if (null == fullFrame) {
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

   final class Initer extends SyncAwt {
       // makeing this run sychronously makes the window become visible about 20-30
       // ms quicker, but ready for events quit 90 ms slower

      public Object doAwt() {
         //trace("start initer");
         try {
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
               FvContext.setCommand(tfc);
               tfc.setCurrView();
               View vi = mkview(false);
               //trace("connecting " + FileList.getContext(vi).at() + "vi " + vi);
               FvContext.connectFv((TextEdit) FileList.getContext(vi).at(), vi);
               AwtCircBuffer.initCmd();
               FontEntry.init(new AFontChanger());
            } catch (InputException e) {
               throw new RuntimeException("can't recover iaddview", e);
            } finally {
               EventQueue.biglock2.unlock();
            }

            frm.requestFocus();
            statusBar = new StatusBar();
            statusBar.setVisible(false);
            frm.add(statusBar, 0);
            trace("setting frame visible");
            ishow();
            new InHandler();
         } catch (Throwable ex) {
            trace("failure in awt Initer ");
            ex.printStackTrace();
            return null;
            // if anything goes wrong during init give up
            // otherwise there is no window to shutdown with
            //EventQueue.insert(new ExitEvent());
         }
         return this;
      }
   }

   private View iaddview(boolean newview, FvContext fvc) throws
         InputException {

      View ta = mkview(newview);
      FvContext.connectFv(fvc.edvec, ta);
      return ta;
   }

   private View mkview(boolean newview) {
      //view ta = newview ? (view) new TabbedTextLayout() : new oldview();
      //trace("mkview");
      OldView ta = new OldView(true);
      Component cmdComp = ta.getComponent();
      viewCount++;
      cmdComp.setFont(AwtFontList.getCurr(null));
      ta.setSizebyChar(MiscCommands.getWidth(), MiscCommands.getHeight());
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
         viewCount--;
         OldView ta = (OldView) fvc.vi;
         Component cmdComp = ta.getComponent();
         frm.remove(cmdComp);

         FvContext newfvc = FvContext.dispose(fvc.vi);
         if (null != newfvc)
            isetTitle(newfvc.edvec.toString());
         frm.validate();
      }
   }

   void inextView(FvContext fvc) {
      FvContext newfvc = FvContext.nextView();
      isetTitle(newfvc.edvec.toString());
   }

   public void isetStream(Reader inreader) { /* unimplemented */ }

   public void irepaint() {
      int ccount = frm.getComponentCount();
      for (int ii = 0; ii < ccount; ii++) {
         Component cp = frm.getComponent(ii);
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

   public void isetTitle(java.lang.String title) {
      frm.setTitle(title);
   }

   public void idispose() {
      frm.dispose();
   }

   public void itransferFocus() {
      frm.transferFocus();
   }

   public void ishowCommand() {
      Component cmdComp = ((OldView) tfc.vi).getComponent();
      cmdComp.setVisible(true);
      cmdComp.repaint();
   }

   public void ihideCommand() {
      Component cmdComp = ((OldView) tfc.vi).getComponent();
      cmdComp.setVisible(false);
      statusBar.clearlines();
      //trace("comline:"+ tfc.at().toString());
   }

   final class IdleEvent extends RunAwt {
      IdleEvent() {
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

   final class Validate extends RunAwt {

      Validate() {
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
      trace("toFront");
      java.awt.EventQueue.invokeLater(() -> {
         frm.toFront();
         frm.setExtendedState(frm.getExtendedState() & ~Frame.ICONIFIED);
         frm.setAlwaysOnTop(true);
         frm.toFront();
         frm.requestFocus();
         frm.setAlwaysOnTop(false);
         frm.repaint();
         frm.setVisible(true);
      });
   }

   private static Buttons diaflag;

   public Buttons ichooseWriteable(String filename) {

      if (null == chinst)
         chinst = new ChoseWrt(this);
      chinst.chosefile(filename);
      return diaflag;
   }

   public void isizeChange() {
      //trace("width " + width + " height " + height + " view = " + vi);
      if (normalFrame == frm
            && !((frm.getExtendedState() & Frame.MAXIMIZED_BOTH)
            == Frame.MAXIMIZED_BOTH))
         frm.setSize(frm.getPreferredSize());

      new Validate();
   }

   final class SetFont extends RunAwt {

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

   static final class AFontChanger extends FontEntry.FontChanger {
      void setFont(Font font, View vi) {
         ((AwtInterface) getInstance()).new SetFont(font, vi);
      }
   }

   private static final class MyMenuItem extends MenuItem {

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
      if (null == popmenu) {

         popmenu = new PopupMenu();

         Menu filem = new Menu("File");
         new MyMenuItem("Open", "e", filem, this);
         new MyMenuItem("Quit", "q", filem, this);
         new MyMenuItem("Save file", "w", filem, this);
         popmenu.add(filem);

         Menu sizem = new Menu("Size");
         for (int ii = 4; ii < 20; ii++)
            new MyMenuItem(Integer.toString(ii), "fontsize " + ii,
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

   final class Popper extends SyncAwt<Boolean> {

      private String str;

      Popper(String stri) {
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

      public void actionPerformed(ActionEvent e) {
         resb = (NButton) e.getSource();
         //trace("set resb to " + resb + " lable = " + resb.getLabel());

         setVisible(false);
      }

      static final class NText extends TextField {
         private static final long serialVersionUID = 1;

         NText(String s, NDialog nd) {
            super(s);
            nd.add(this);
            addActionListener(nd);
         }
      }

      static final class NButton extends java.awt.Button  {
         private static final long serialVersionUID = 1;

         NButton(String s, NDialog nd) {
            super(s);
            nd.add(this);
            addActionListener(nd);
         }
      }

      public void windowClosing(WindowEvent e) {
         trace(e.toString());
         setVisible(false);
      }

      public void setVisible(boolean vis) {
         //trace("setVisible " + vis);
         if (vis)
            resb = null;
         super.setVisible(vis);
      }
   }

   private static final class PopString extends NDialog {
      private static final long serialVersionUID = 1;
      private TextArea ta = new TextArea("", 30, 80);
      private NButton rThrow = new NButton("reThrow", this);

      PopString(Frame frm) {
         super(frm, "exception trace", new FlowLayout());
         new NButton("Ignore", this);
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

   static final class ModVal extends NDialog {
      private static final long serialVersionUID = 1;

      private NText tf;

      ModVal(final String caption, final String units,
             final String[]buttonVals, final long limit, Frame frame) {

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
         final String[]buttonVals, final long limit) {

      ModVal b1 = new ModVal(caption, units, buttonVals, limit, frm);
      return new Result(Integer.parseInt(
         b1.tf.getText()), b1.getRes().getLabel());
   }

   private static final class ChoseWrt extends NDialog {
      private static final long serialVersionUID = 1;

      private Label writelabel = new Label();;

      private NButton svnb =   new NButton("use svn base ", this);
      private NButton backb =   new NButton("back up to .orig file", this);
      private NButton checkoutb =   new NButton(
         "checkout with version control", this);

      private NButton forceWriteable =
         new NButton("force writeable", this);
      private NButton nothing = new NButton("do nothing", this);

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
            null == getRes()
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

   final class HandleDiff extends SyncAwt<Buttons> {

      private final String filename;
      private final String backupname;
      private final int linenum;
      private final Object filevers;
      private final Object backupvers;
      private final BackupStatus status;

      HandleDiff(String filenamei, int linenumi, Object fileversi,
            Object backupversi, BackupStatus statusi,
            String backupnamei) {
         synchronized (this) {
            filename = filenamei;
            backupname = backupnamei;
            linenum = linenumi;
            filevers = fileversi;
            backupvers = backupversi;
            status = statusi;
            EventQueue.biglock2.assertUnOwned();
         }
      }

      public Buttons doAwt() {
         //trace("handleDiff fileObj " +fileObj + " backObj "  + backObj);

         if (null == rdinst)
            rdinst = new Diff(frm);
         while (true) {
            rdinst.pop(filename, linenum, filevers, backupvers, status);
            if (diaflag == UI.Buttons.USEDIFF) {
               try {
                  String cmd = System.getProperties().getProperty(
                     "java.javi.diffcmd",
                     "kdiff3"
                     //"C:\\Progra~2\\SourceGear\\DiffMerge\\DiffMerge.exe ");
                     //"C:\\Progra~1\\Beyond~1\\BC2.exe ";
                     //"C:\\Progra~1\\Beyond~1\\Bcomp.exe ");
                     //"C:\\Program Files\\TortoiseSVN\\bin\\Tortoiseproc.exe" ???? "/command:diff" ,  "/path:" + filename , "/path2:" +  backupname};

                  );
                  //"C:\\Progra~1\\Beyond~1\\BC2.exe ",  filename, backupname
                  //"cmd /c c:\\PROGRA~1\\Araxis\\ARAXIS~1.0PR\\Merge.exe /NoSplash /NoSplashDelay "
                  //"cmd /c d:\\progra~1\\araxis\\araxis~1\\merge.exe /NoSplash /NoSplashDelay ";
                  String[] lstr =  {cmd, filename, backupname};
                  Tools.execute(null, lstr);
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
         return diaflag;
      }
   }

   public Buttons ireportDiff(String filename, int linenum, Object filevers,
         Object backupvers, BackupStatus status,
         String backupName) {
      return new HandleDiff(filename, linenum, filevers, backupvers,
         status, backupName).postWait().getResult();
   }

   final class BadBackupDia extends SyncAwt<Boolean> {

      private String filename;
      private String error;
      private BadButton bad = new BadButton();

      BadBackupDia(String filenamei, String errori) {
         filename = filenamei;
         error = errori;
      }

      private final class BadButton extends NDialog {
         private static final long serialVersionUID = 1;

         private Label writelabel = new Label();

         private NButton delete =   new NButton("delete backup file ", this);
         private NButton ignore =   new NButton("ignore ", this);

         BadButton() {

            super(frm, "Stop Process", new FlowLayout());
            add(writelabel);
         }

         boolean getChoice() {
            String tstring =
               "corrupt backupfile for " + filename;

            this.setTitle(tstring);

            writelabel.setText(tstring
               + " do you want to delete it, or ignore error?");

            this.pack();
            Dimension d = writelabel.getPreferredSize();
            this.setSize(d.width, d.height * 7);
            setVisible(true);
            return getRes() == delete;
         }
      }

      Boolean doAwt()  {
         return bad.getChoice();
      }
   }

   public boolean ireportBadBackup(String filename, BadBackupFile e) {
      return new BadBackupDia(filename, e.toString())
         .postWait().getResult();
   }

   /**
    * Dialog for handling external file modifications.
    * 
    * <p>Shows options when a file has been modified on disk while open in the editor.
    * Uses {@link SyncAwt} to safely release biglock2 before displaying, avoiding
    * deadlock between the idle handler and AWT event thread.</p>
    */
   final class ConfirmReloadDia extends SyncAwt<UI.ReloadAction> {

      private String filename;
      private boolean isModified;
      private ReloadDialog dia = new ReloadDialog();

      ConfirmReloadDia(String filenamei, boolean isModifiedi) {
         filename = filenamei;
         isModified = isModifiedi;
      }

      private final class ReloadDialog extends NDialog {
         private static final long serialVersionUID = 1;

         private Label msglabel = new Label();

         private NButton reload = new NButton("Reload", this);
         private NButton ignore = new NButton("Ignore", this);
         private NButton ignoreAlways = new NButton("Ignore Always", this);
         private NButton showDiff = new NButton("Show Diff", this);
         private NButton stopEdit = new NButton("Stop Edit", this);

         ReloadDialog() {
            super(frm, "File Changed", new GridLayout(3, 1, 5, 5));
            Panel msgPanel = new Panel(new FlowLayout(FlowLayout.CENTER));
            msgPanel.add(msglabel);
            add(msgPanel);
            Panel row1 = new Panel(new FlowLayout(FlowLayout.CENTER, 10, 5));
            row1.add(reload);
            row1.add(ignore);
            row1.add(ignoreAlways);
            add(row1);
            Panel row2 = new Panel(new FlowLayout(FlowLayout.CENTER, 10, 5));
            row2.add(showDiff);
            row2.add(stopEdit);
            add(row2);
         }

         UI.ReloadAction getChoice() {
            String tstring = filename + " modified externally"
               + (isModified ? " and internally" : "");
            this.setTitle("File Changed");

            msglabel.setText(tstring);

            this.pack();
            Dimension d = getPreferredSize();
            this.setSize(Math.min(d.width + 40, 400), d.height + 20);
            setVisible(true);

            Object res = getRes();
            if (res == reload) {
               return UI.ReloadAction.RELOAD;
            } else if (res == ignoreAlways) {
               return UI.ReloadAction.IGNORE_ALWAYS;
            } else if (res == showDiff) {
               return UI.ReloadAction.SHOW_DIFF;
            } else if (res == stopEdit) {
               return UI.ReloadAction.STOP_EDITING;
            } else {
               return UI.ReloadAction.IGNORE;
            }
         }
      }

      UI.ReloadAction doAwt() {
         return dia.getChoice();
      }
   }

   public UI.ReloadAction iconfirmReload(String filename, boolean isModified) {
      return new ConfirmReloadDia(filename, isModified).postWait().getResult();
   }

   private static final class Diff extends NDialog {
      private static final long serialVersionUID = 1;
      private Label replab1 = new Label();
      private Label replab2 = new Label();
      private Label sa1 = new Label();
      private Label sa2 = new Label();

      private NButton okbut = new NButton("OK", this);
      private NButton backbut = new NButton("use backup version", this);
      private NButton filebut = new NButton("use file version", this);
      private NButton diffbut = new NButton("launch diff", this);

      private String l1, s1, s2;

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

         //trace("getres" + getRes());
         return null == getRes()
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
                Object backupvers, BackupStatus status) {
//     try {Thread.sleep(150);} catch (Exception e) {} // work around focus problem ???
         this.setTitle("discrepency in backup file:" + filename);
         setinvis();
         if (null != status.error) {
            if (status.error instanceof history.FileLockException) {
               replab1.setText("unable to Lock backup file. open "
                   + "existing file in readonly mode");
            } else {
               replab1.setText(
                  "corrupt backup file read in as far as possible. "
                  + status.error);
            }

            replab1.setForeground(Color.blue);
            replab1.setVisible(true);
            okbut.setVisible(true);
            backbut.setEnabled(false);
            diffbut.setEnabled(false);
            filebut.setEnabled(false);
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
         if (null == filevers && null == backupvers) {
            replab2.setText(
               "the written versions of the file are consistent");

            replab2.setForeground(Color.black);
            okbut.setVisible(true);
         } else {
            l1 = s1 = s2 = "";
            if (null == filevers) {
               l1 = "backup version has extra lines at end";
               s2 = backupvers.toString();
            } else if (null == backupvers) {
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

   private final class Layout implements LayoutManager, java.io.Serializable {

      private static final long serialVersionUID = 1;
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
         if (frm  == normalFrame && 0 == inset.top)
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
         for (int ii = 0; ii < ccount; ii++) { // views
            Component cp = frm.getComponent(ii);
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

   final class GetFile extends SyncAwt<String> {

      String doAwt()  {

         if (null == fdialog)
            fdialog = new FileDialog(frm, "open new vifile",
               FileDialog.LOAD);
         fdialog.setVisible(true);
         return   fdialog.getFile();
      }
   }

   public String igetFile() {
      return new GetFile().postWait().getResult();
   }

   private static final class StopProc extends NDialog {
      private static final long serialVersionUID = 1;

      private Label writelabel = new Label();;

      private NButton killProc =   new NButton("kill process ", this);
      private NButton waitProc =   new NButton("wait for process to die", this);

      StopProc(AwtInterface jwin) {

         super(jwin.frm, "Stop Process", new FlowLayout());
         add(writelabel);
      }

      void getStopChoice(String filename) {
         String tstring =
            "You are disposing a running process:" + filename;

         this.setTitle(tstring);

         writelabel.setText(tstring
            + " do you want to kill it, or wait for it to exit?");

         this.pack();
         Dimension d = writelabel.getPreferredSize();
         this.setSize(d.width, d.height * 7);
         setVisible(true);
         diaflag =
            null == getRes()
               ? UI.Buttons.IOERROR
            : getRes() == killProc
               ? UI.Buttons.KILLPROC
            : getRes() == waitProc
               ? UI.Buttons.WAITPROC
            : UI.Buttons.IOERROR;
         trace("setting diaflag " + diaflag);
      }
   }

   public Buttons istopConverter(String commandname) {
      if (null == stpinst)
         stpinst = new StopProc(this);
      stpinst.getStopChoice(commandname);
      return diaflag;
   }
}
