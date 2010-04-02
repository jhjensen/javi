package javi;

import java.awt.AWTKeyStroke;
import java.awt.Button;
import java.awt.CheckboxMenuItem;
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
import java.awt.event.MouseEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.awt.event.KeyEvent;
import java.awt.im.InputMethodRequests;
import java.awt.AWTEvent;

public abstract class UI {
   private enum Buttons {CHECKOUT , MAKEWRITEABLE , DONOTHING , MAKEBACKUP , USEFILE ,
      USEBACKUP , USEDIFF , OK , WINDOWCLOSE , IOERROR , USESVN };
   
   private static Buttons diaflag;
   private static UI instance = null;
   abstract void itoggleStatus() ;
   abstract void isetStream(Reader inreader) ;
   abstract void ireportDiff(String filename,int linenum,Object filevers,
     Object backupvers,UndoHistory.BackupStatus status ) ;
   abstract FvContext istartComLine() ;
   abstract String iendComLine() ;
   abstract void irepaint() ;
   abstract void idispose() ;
   abstract String igetFile() ;
//   abstract void iadd(Component vi, int index) ;
   abstract boolean iisVisible() ;
   abstract void iremove(View vi) ;
   abstract void ishow() ;
   abstract void ipack() ;
   abstract void ishowmenu(int x, int y) ;
   abstract void itoFront() ;
   abstract void itransferFocus() ;
   abstract void ichooseWriteable(String filename) ;
   abstract void ipopstring(String str) ;
   abstract void isetFont(Font font) ;
   abstract void ivalidate() ;
   abstract void iflush(boolean totalFlush) ;
   abstract void istatusaddline(String str) ;
   abstract void istatusSetline(String str) ;
   abstract void iclearStatus() ;
   abstract boolean iisGotoOk(FvContext fvc);
   abstract void iresize();
   abstract FvContext iconnectfv(TextEdit file,View vi) throws InputException;
   abstract void init2();
   abstract void isetView(FvContext fvc);

   abstract Result ireportModVal(String caption,String units,String []buttonVals,long limit);

   static void saveState(java.io.ObjectOutputStream os) throws IOException {
//      os.writeObject (new Boolean(instance instanceof AwtInterface));
      instance.iflush(true);
      os.writeObject(instance);
   }

   static void restoreState(java.io.ObjectInputStream is) throws IOException,ClassNotFoundException {
//      instance = (Boolean) is.readObject() 
//         ? (UI)new AwtInterface()
//         :(UI) new StreamInterface();
     instance = (AwtInterface)is.readObject();
     instance.ishow();
     FontList.updateFont(); // prevents an extra redraw later
     instance.toFront();
   }

public static void init(boolean isAwt) {
   instance = isAwt
      ? (UI)new AwtInterface()
      :(UI) new StreamInterface();
   instance.init2();
}

static void setStream(Reader inreader) {
   instance.isetStream(inreader);
}

@SuppressWarnings("fallthrough")
static boolean reportDiff(String filename,int linenum, Object filevers,
    Object backupvers,UndoHistory.BackupStatus status,String backupname
     ) throws IOException {
    //trace(
    //   " filename = " + filename
    //   + " linenum = " + linenum
    //   + " filevers = " + filevers
    //   + " backupvers = " + backupvers
    //   + " status = " + status
    // );
   while (true)  {
      //Thread.dumpStack();
      //trace("instance " + instance + " flag " + diaflag);
      instance.ireportDiff(filename,linenum,filevers,backupvers,status);
      //trace("instance " + instance + " flag " + diaflag);
      //trace("" + diaflag);
      switch (diaflag) {
         case USEDIFF:
            try {
               String cmd =System.getProperties().getProperty("java.javi.diffcmd",
                   "C:\\Progra~2\\SourceGear\\DiffMerge\\DiffMerge.exe ");
                   //"C:\\Progra~1\\Beyond~1\\BC2.exe ";
                   //"C:\\Progra~1\\Beyond~1\\BC2.exe ",  filename, backupname};
                   //"cmd /c c:\\PROGRA~1\\Araxis\\ARAXIS~1.0PR\\Merge.exe /NoSplash /NoSplashDelay "
                   //"kdiff3 "
                   //"cmd /c d:\\progra~1\\araxis\\araxis~1\\merge.exe /NoSplash /NoSplashDelay ";
               String [] lstr =  {cmd,filename,backupname};
               Tools.execute(lstr);
            } catch (IllegalArgumentException e) {
               trace("ui.reportDiff caught exception " + e);
               e.printStackTrace();
            } catch (IOException e) {
               trace("ui.reportDiff caught exception " + e);
               e.printStackTrace();
            }

            break;
         case WINDOWCLOSE:
            break;
         case OK:
//trace("got ok backupvers = " + backupvers + " filevers " + filevers);
            if (backupvers==null && filevers == null)
               return false;
            break;
         case USEBACKUP:
           return false;
         case USEFILE:
           return true;
         case IOERROR:
           trace("got error in reportDiff");
           throw new IOException();
         //intentional fall through
         default:
            trace("Thread " + Thread.currentThread() + " filename " + filename + " bad diaflag = " + diaflag);
            try {Thread.sleep(5000);} catch (InterruptedException e) {/*Ignore*/}
            trace("Thread " + Thread.currentThread() + " filename " + filename + " bad diaflag = " + diaflag);
       }
//trace("reportDiff returning diaflag = " + diaflag);
  }
}

static FvContext startComLine() {
   return instance.istartComLine();
}
static String endComLine() {
   return instance.iendComLine();
}
static  boolean isGotoOk(FvContext fvc) {
   return instance.iisGotoOk(fvc);
}
static void repaint() {
   instance.irepaint();
}

static void dispose() {
   if (instance!=null)
      instance.idispose();
   instance = null;
}
static String getFile() {
   return instance.igetFile();
}
//static void add(Component vi, int index) {
//   instance.iadd(vi,index);
//}
static boolean isVisible() {
   return instance.iisVisible();
}
static void remove(View vi) {
   instance.iremove(vi);
}

static void show() {
   instance.ishow();
}
static void pack() {
   instance.ipack();
}
static void showmenu(int x, int y) {
   instance.ishowmenu(x,y);
}
static void toFront(){
   instance.itoFront();
}

static void hide() {
   instance.itransferFocus();
}

static void resize() {
   instance.iresize();
}

static Matcher findfile =  Pattern.compile("(.*[\\\\/])([^\\/]*)$").matcher("");

static void makeWriteable(EditContainer edv,String filename) 
   throws IOException {
   instance.ichooseWriteable(filename);
   switch (diaflag) {

      case CHECKOUT:
         Command.command("vcscheckout",null,filename);
         break;
      case MAKEWRITEABLE:
         edv.setReadOnly(false);
         break;
       case DONOTHING:
       case WINDOWCLOSE:
          break;
       case MAKEBACKUP:
          edv.backup(".orig");
          break;
       case USESVN:
          findfile.reset(filename);
          String svnstr =  findfile.find()
               ? findfile.group(1) + ".svn/text-base/" + findfile.group(2)+ ".svn-base"
               : "./.svn/text-base/" + filename + ".svn-base";
           //trace("svnstr "  + svnstr);
           BufferedReader fr = new BufferedReader(new FileReader(svnstr));
           try {
              int lineno = 0;
              int linemax = edv.finish();
              String line;
              while ((line = fr.readLine()) !=null && ++lineno < linemax) { 
                 if (!line.equals(edv.at(lineno))) {
                    reportMessage("svn base file not equal to current file at " + (lineno -1) + ":" +edv.at(lineno-1) + ":" + line + ":");
                    return;
                 }
              }
              if (line== null && lineno +1==linemax)
                 edv.setReadOnly(false);
              else
                 reportMessage("svn base file not equal to current file");
           } finally {
              fr.close();
           }
          break;
       default:

       throw new RuntimeException("ui.makeWriteable: bad diaflag = " + diaflag);
  }
}
static void popError(String errs,Throwable ex) {
  trace("poperror exception trace" + (errs == null ? "" : errs) + ex);

  StackTraceElement[] st = ex == null
     ? Thread.currentThread().getStackTrace()
     : ex.getStackTrace();

  StringWriter sw = new StringWriter();
  PrintWriter wr = new PrintWriter(sw);

  if (ex!=null) {
     ex.printStackTrace();
     wr.println(errs + ex);
  }

  wr.println();

  for(StackTraceElement ste:st) {
     //trace("   " + ste.toString());
     wr.println(ste);
  }
  if (instance!=null)
     instance.ipopstring(sw.toString());
}

static FvContext connectfv(TextEdit file,View vi) throws InputException {
       return instance.iconnectfv(file,vi);
}
static void setView(FvContext fvc) {
       instance.isetView(fvc);
}
static void flush() {
   instance.iflush(false);
}

static void reportError(String s) {
  instance.istatusaddline(s);
}

static void reportMessage(String s) {
  if (instance!=null)
     instance.istatusaddline(s);
  else {
     Thread.dumpStack();
     trace("unhandled Messege:" + s);
   }
}
static void setline(String s) {
  instance.istatusSetline(s);
}

static void clearStatus() {
  instance.iclearStatus();
}

static public class Result {
   public final int newValue;
   public final String choice;

   Result(int newValuei,String choicei) {
      newValue=newValuei;
      choice = choicei;
   }
}

static Result reportModVal(String caption,String units,String []buttonVals,long limit) {
   return instance.ireportModVal(caption,units,buttonVals,limit);
}

private static class StreamInterface extends UI{
   void isetStream(Reader inreader) {
      inStr = inreader;
   }
   Reader inStr = new InputStreamReader(System.in);

 

void ireportDiff(String filename,int linenum,Object filevers,
  Object backupvers,UndoHistory.BackupStatus status ) { 
     StringBuilder sb = new StringBuilder("problem found in file " + filename + '\n');
     if (filevers==null && backupvers==null) {
        sb.append("the written versions of the file are consistent\n");
     } else   if (filevers==null ) {
        sb.append( "backup version has extra lines at end\n");
        sb.append(backupvers.toString());
     } else if (backupvers==null) {
        sb.append( "file version has extra lines at end\n");
        sb.append(filevers.toString());
     } else  {
        sb.append("versions differ at line " + linenum + " :\n") ;
        sb.append(filevers.toString());
        sb.append('\n');
        sb.append(backupvers.toString());
     }
     if (status.error!=null) {
        sb.append("\ncorrupt backup file read in as far as possible. " + status.error);
     } else {
        if (!status.cleanQuit)
           sb.append("\nThe file was not cleanly closed");
        if (!status.isQuitAtEnd)
           sb.append("\nThere is undo history. user ^r");
     }
     sb.append("\nf(file) b(backup) d(diff) O(ok)\n");
     try { while (true) {
        if (!inStr.ready())
           Tools.trace(sb.toString());
        int ch = inStr.read();
        //trace("read in " + (char)ch);
        switch (ch) {
           case 'f':
              diaflag = Buttons.USEFILE ;
              return;
           case 'b':
              diaflag = Buttons.USEBACKUP ;
              return;
           case 'd':
              diaflag = Buttons.USEDIFF ;
              return;
           case 'o':
              diaflag = Buttons.OK;
              return;
           case -1:
              diaflag = Buttons.IOERROR;
              return;
           default:
              trace("stream got unexpected char = " + ch);
         }
     }} catch (IOException e) {
        trace("ireportDiff can not read from input Stream ");
        diaflag = Buttons.IOERROR;
        return;
     }
        
}

void irepaint(){/* unimplemented */}
void idispose() {/* unimplemented */}
String igetFile(){return "filename";}
void iadd(Component vi, int index){/* unimplemented */}
boolean iisVisible() {return true;}
void iremove(View vi){/* unimplemented */}
void ishow(){/* unimplemented */}
void ipack(){/* unimplemented */}
void ishowmenu(int x, int y) {/* unimplemented */}
void itoFront() {/* unimplemented */}
void itransferFocus() {/* unimplemented */}
void ichooseWriteable(java.lang.String str) {/* unimplemented */}
void ipopstring(java.lang.String str) {/* unimplemented */}
void isetFont(java.awt.Font font) {/* unimplemented */}
void ivalidate() {/* unimplemented */}
void iflush(boolean total) {/* unimplemented */}
void itoggleStatus() {/* unimplemented */}
//void idle() {throw new RuntimeException("unimplemented");}
FvContext iconnectfv(TextEdit file,View vi ) {return null;}
void iresize() {/* unimplemented */}
View iaddview(boolean newview,FvContext fvc) {return null;}
void init2() {/* unimplemented */}


void istatusaddline(String s) {
 trace(s);
}
void istatusSetline(String s) {/*unimplemented*/}
void iclearStatus() {/* unimplemented */}
FvContext istartComLine() { throw new RuntimeException("unimplemented");}
String iendComLine() {return ""; }
boolean iisGotoOk(FvContext fvc) {return true;}
public Object doroutine(int rnum,Object arg,int count,int rcount,FvContext fvc,
     boolean dotmode) {
         throw new RuntimeException("doroutine called with " + rnum);
}
void isetView(FvContext fvc) {
}

void inextView(FvContext fvc) {
}

Result ireportModVal(String caption,String units,String []buttonVals,long limit) {
   return null;
}
}

static class AwtInterface extends UI implements java.io.Serializable,
   WindowListener,FocusListener,ActionListener,ItemListener,EventQueue.idler {
  

private void common() {
   new Commands();
   EventQueue.registerIdle(this);
}

private void readObject(java.io.ObjectInputStream is) 
       throws ClassNotFoundException,java.io.IOException {
   is.defaultReadObject();
   if (fullFrame != null) {
      GraphicsDevice[] devs = 
         java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
      currdev = devs[0];
   }

   common();
}

AwtInterface() {
     //super("vi:");
     //outfor 1.4 FocusManager.disableSwingFocusManager();
     //1.4setUndecorated(true);
     //fr.setUndecorated(true);
     //FontList.updateFont(); //??? avoid calling this?
     frm = initfrm("normal");
     normalFrame=frm;
     common();
     irepaintFlag = 1;
     StringIoc sio = new StringIoc("command buffer",null);
     TextEdit<String> cmbuff = new TextEdit<String>(sio,sio.prop);

     View tfview = new OldView(false);
     tfview.setFont(FontList.getCurr(tfview));
     tfview.setSizebyChar(80,1);
     tfview.setVisible(false);
     tfc = FvContext.getcontext(tfview,cmbuff);
     frm.add(tfview,0);
     frm.setComponentZOrder(tfview,0);
     tfc.vi.newfile(tfc);
     //trace("this = " + this + " fr = " + fr);
}

   class Commands extends Rgroup   {
     final String[] rnames = {
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

     public Object doroutine(int rnum,Object arg,int count,int rcount,FvContext fvc,
          boolean dotmode) throws InputException{
         switch (rnum) {
           case 1:
              itoggleStatus();
              return null;
           case 2: iaddview(false,fvc); return null;
           case 3: iaddview(true,fvc); return null;
           case 4: delview(fvc); return null;
           case 5: inextView(fvc); return null;
           case 6:   return fullScreen();

           default:
              throw new RuntimeException("doroutine called with " + rnum);
         }
    }


   }
transient private boolean needpack;
transient private boolean needval;
private TestFrame frm;
private TestFrame fullFrame;
private TestFrame normalFrame;
private int viewCount=0;
transient GraphicsDevice currdev;
transient private FileDialog fdialog;
transient private PopupMenu popmenu; // the menubar
transient private PopString psinst;
transient private ChoseWrt chinst ;
transient private Diff rdinst;
transient private StatusBar statusBar;
transient private FvContext tfc;  // command context //??? may want to save this?
//transient private FrameListener winl;
transient int irepaintFlag;  //TODO this is a tremendous hack.  
//For some reason upgrading java to 6.18 made the cursor stop appearing, 
//but if we keep redrawing long enough it magically appears.

transient boolean willneedval = false;
// JDK=jdk1.6.0_18 makes the cursor not draw until the sceen is redrawn a few times.
// This may have something to do with inadequate locking in View/OldView

private static class ForceIdle extends EventQueue.IEvent {
   void execute() throws MapEvent.ExitException{
   }
}

class TestFrame extends  Frame {

   private final String name;

   void paintViews () {
       int ccount = getComponentCount();
       for (int i =0;i<ccount;i++) {
          Component cp =getComponent(i);
          trace("component " + cp);
          if (cp instanceof View) 
             ((View)cp).npaint();
      }
   }
   TestFrame(String str,String namei) {
      super(str);
      name = namei;
      HashSet<AWTKeyStroke> keyset = new HashSet<AWTKeyStroke>(getFocusTraversalKeys(
          KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
      for (Iterator it = keyset.iterator();it.hasNext();) {
          AWTKeyStroke key = (AWTKeyStroke)(it.next());
          if (key.getKeyCode()==KeyEvent.VK_TAB && key.getModifiers() == 0)
            it.remove();
      }

      setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,keyset);

      enableInputMethods(false);
      enableEvents(AWTEvent.KEY_EVENT_MASK |  
                              AWTEvent.MOUSE_EVENT_MASK| 
                              AWTEvent.MOUSE_WHEEL_EVENT_MASK |
                              AWTEvent.WINDOW_EVENT_MASK
      );
      //enableEvents(0xffffffffffffffffl);
   }

   public String toString() {
      return name + super.toString();
   }

   public void realValidate() {
      //trace("called realinvalidate !!!!"); 
      //super.invalidate();
      super.validate();
  }

   private final int fullwidth(Component cp,int yleft,int xsize,Insets inset) {
      Dimension prefSize = cp.getPreferredSize(); // really to get height
      prefSize.width=xsize-inset.left-inset.right;
      if (!cp.getSize().equals(prefSize)) {
         cp.setSize(prefSize);
         //trace("full width set size " + cp.getSize() + " " +  cp );
      }
      return cp.isVisible()
         ? yleft - prefSize.height
         : yleft;
   }

   public Dimension getPreferredSize() {
       
      //trace ("preferredSize getGraphicsConfiguration()  "+ getGraphicsConfiguration());
      Toolkit kit = Toolkit.getDefaultToolkit();
      Insets inset = getInsets();
      if (inset.top==0)  
         inset =   kit.getScreenInsets(getGraphicsConfiguration());

       Dimension fsize = new Dimension(inset.right+ inset.left,inset.top+inset.bottom);
       if (tfc.vi.isVisible())
          fsize.height +=  tfc.vi.getPreferredSize().height;
       if (statusBar.isVisible())
          fsize.height +=  statusBar.getPreferredSize().height;



       int viewheight = 0;
       int ccount = getComponentCount();
       for (int i =0;i<ccount;i++) {
          Component cp =getComponent(i);
          //trace("component " + cp);
          if ((cp instanceof View) && (cp != tfc.vi)) {
             Dimension cpsize = cp.getPreferredSize(); // check if used all
             //trace("component prefsize " + cpsize);
             fsize.width += cpsize.width;
             if (cpsize.height > viewheight )
                viewheight = cpsize.height;
          }
       }

       fsize.height += viewheight;

       //trace("returning " +fsize);
       return fsize;
   }

   public void setSize(int width,int height) {
      //trace("!!!!!!!!! frame setSize ("+ width + "," + height +")");
      super.setSize(width,height);
   }

   public void setCompSize(int width,int height) {
      //trace("frame setCompsize ("+ width + "," + height +")");
      //trace("tfc " + tfc);
      //trace("statusBar " + statusBar);
      Insets inset = getInsets();
      int viewHeight = height - inset.top - inset.bottom;

   
      if (tfc.vi.isVisible())
         viewHeight -=  tfc.vi.getPreferredSize().height;

      if (statusBar.isVisible())
         viewHeight -=  statusBar.getPreferredSize().height;

      Dimension viewSize = new Dimension( (width - inset.left - inset.right) / viewCount,
         viewHeight);

      int ccount = getComponentCount();
      for (int i =0;i<ccount;i++) {
         Component cp =getComponent(i);
         //trace("component " + cp);
         if ((cp instanceof View) && (cp != tfc.vi)) {
            if (!cp.getSize().equals(viewSize))
               cp.setSize(viewSize);
         }
      }
   }

@SuppressWarnings("fallthrough")
public void processEvent(AWTEvent ev) {
   //trace("ev " + ev + "  has focus " + hasFocus());
   switch (ev.getID()) {
      case KeyEvent.KEY_PRESSED:
         if (ev instanceof KeyEvent) {
            KeyEvent kev = (KeyEvent)ev;
            if ((kev).getKeyChar()==KeyEvent.CHAR_UNDEFINED)
               switch((kev).getKeyCode()) {
                  case KeyEvent.VK_SHIFT:
                  case KeyEvent.VK_CONTROL:
                  case KeyEvent.VK_ALT:
                     super.processEvent(ev);
                     return;
                }
//???            if (fcontext.dispatchKeyEvent(kev))
       //???        break;
            }
      case MouseEvent.MOUSE_WHEEL:
         EventQueue.insert(ev);
         break;
      case KeyEvent.KEY_RELEASED:
      case KeyEvent.KEY_TYPED:
      case WindowEvent.WINDOW_ACTIVATED:
      case WindowEvent.WINDOW_DEACTIVATED:
      case WindowEvent.WINDOW_OPENED:
      case WindowEvent.WINDOW_CLOSED:
      case FocusEvent.FOCUS_LOST:
      case FocusEvent.FOCUS_GAINED:
         break;

      case WindowEvent.WINDOW_CLOSING:
         FileList.quit(true,null); // usually won't return from here
         MiscCommands.wakeUp();
         break;
   
     // browsers may reach here, so wakeup run so it tests flag, and thread returns
      default:
         trace("unhandled event ev " + ev + "  has focus " + hasFocus() + " insets " + getInsets());
         super.processEvent(ev);
   }
}
}


public void actionPerformed(ActionEvent event) {
   //trace("reached actionPerformed " + event);
   EventQueue.insert(event);
}

public void itemStateChanged(ItemEvent event) {
         EventQueue.insert(event);
}

void iflush(boolean total) {
  synchronized (EventQueue.biglock) {
/*
      if (total) {
         if (tfc != null) {
            frm.remove(tfc.vi);
            tfc.dispose(tfc.vi);
            try {
               tfc.dispose(tfc.edvec);
            } catch (IOException e) {
               // should be harmless
            }
            try {
               tfc.dispose(tfc.edvec);
            } catch (IOException ex) {
               popError("error in flush",ex);
            }
         }
         if (statusBar != null) {
            frm.remove(statusBar);
            statusBar =  null;
         }
      }
*/
      if (fdialog != null) {
         fdialog.dispose();
         frm.remove(fdialog);
         fdialog=null;
      }
      if (popmenu != null) {
         frm.remove(popmenu);
         popmenu=null;
      }
      if (psinst != null) {
         frm.remove(psinst);
         psinst.dispose();
         psinst=null;
      }
      if (chinst != null) {
         frm.remove(chinst);
         chinst.dispose();
         chinst=null;
      }
      if (rdinst != null) {
         frm.remove(rdinst);
         rdinst.dispose();
         rdinst=null;
      }
   }
}


private static class Dropper extends DropTarget {
   private static final long serialVersionUID=1;
   Dropper(Component c) {
      super(c, DnDConstants.ACTION_LINK, null, true);
   }

   public void dragEnter(DropTargetDragEvent dtde)  { /* don't care */
   }
   public void dragExit(DropTargetEvent dte) {/* don't care */
   }
   public void dragOver(DropTargetDragEvent dtde)  {/* don't care */
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
          for (DataFlavor flavor:tran.getTransferDataFlavors()) {
             //trace("flavor = " + flavor);
             if (flavor.equals( DataFlavor.javaFileListFlavor)) {
                EventQueue.insert(new FileList.FileListEvent((List) tran.getTransferData(flavor)));
                dtde.dropComplete(true);
                return;
             } else if (flavor.equals( DataFlavor.stringFlavor)) {
                String str = tran.getTransferData(flavor).toString();
                //trace("str len " + str.length() + " " + str);
                if (str.length()!=0) {
                   UI.reportError("need to implement string for " + str);
                   dtde.dropComplete(true);
                   return;
                }
             } else
                trace("unhandled drop flavor " + flavor);
          }
                
          UI.reportError("drop of unexpected data" + Arrays.toString(tran.getTransferDataFlavors()));
      } catch (UnsupportedFlavorException e) {
         UI.reportError("drop of unexpected data");
         trace("data " + dtde + "\nexception" +  e);
         e.printStackTrace();
      } catch (IOException e) {
         UI.reportError("Unexpected IOerror " + e);
      }
      dtde.dropComplete(false);
   }
   public  void dropActionChanged(DropTargetDragEvent dtde)  {/* don't care */
   }
}

private TestFrame initfrm(String name) {
     //trace("initfrm");
     TestFrame lFrm = new TestFrame("vi:",name);
     //lFrm.setUndecorated(true);
     trace("initfrm new Frame sure is slow!!!");
     lFrm.setResizable(true);
     lFrm.setLayout(new Layout());
     //frm.setBackground(AtView.background);
     //frm.setForeground(AtView.foreground);

     lFrm.setFont(FontList.getCurr(null));
     lFrm.addFocusListener(this);
     lFrm.setDropTarget(new Dropper(lFrm));
     return lFrm;
}



static void mvcomp(Container from,Container to) {
      for (Component comp:from.getComponents()) { 
         //trace("moving comp " + comp);
         to.add(comp);
      }
      from.removeAll();
}

Object fullScreen() {

   frm.setVisible(false);
   iflush(false);
   synchronized (frm.getTreeLock()) {
      if (fullFrame == frm)
         currdev.setFullScreenWindow(null);


      if (frm != normalFrame) {
         //trace("exit fullscreen fullFrame " + fullFrame + " normalFrame " + normalFrame);
         mvcomp(fullFrame,normalFrame);
         frm = normalFrame;
         frm.setVisible(true);
      } else {
         //trace("!!!!!!enter fullscreen");
         normalFrame=frm;
         if (fullFrame ==null) {
            fullFrame=initfrm("fullFrame");
            fullFrame.setFont(frm.getFont());
            fullFrame.setUndecorated(true);
            GraphicsDevice[] devs = 
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
   
             currdev = devs[0];
             fullFrame.setResizable(false);
         }
         mvcomp(normalFrame,fullFrame);
         frm = fullFrame;
      }

      //iresize();
      if (fullFrame==frm) {
            currdev.setFullScreenWindow(fullFrame); 
            fullFrame.validate();
      }
      return null;
   }
}

void init2() {
   try {
     View vi = mkview(false);
     FontList.setDefaultFontSize(vi,-1,-1);
     iconnectfv((TextEdit)FileList.getContext(vi).at(),vi);
     frm.requestFocus();
     FontList.updateFont(); // prevents an extra redraw later
     frm.requestFocus();
     statusBar = new StatusBar();
     statusBar.setVisible(false);
     frm.add(statusBar,0);
     ishow();
    } catch (InputException e) {
      throw new RuntimeException("can't recover iaddview",e);
   }
}

FvContext iconnectfv(TextEdit file,View vi) throws InputException {
   //trace("vic.connectfv " + file + " vi " + vi);
   if (/*tfc != null &&*/ vi == tfc.vi)
      throw new InputException("can't change command window to display other data");
   isetTitle(file.toString());

   FvContext fvc = FvContext.getcontext(vi,file);
   fvc.setCurrView();
   return fvc;
}

private View iaddview(boolean newview,FvContext fvc) throws InputException {
   View ta = mkview(newview);
   iconnectfv(fvc.edvec,ta);
   return ta;
}

private View mkview(boolean newview) throws InputException {
     //view ta = newview ? (view) new TabbedTextLayout() : new oldview();
     //trace("mkview");
     View ta = new OldView(true);
     viewCount++;
     ta.setFont(FontList.getCurr(ta));
     frm.add(ta,-1);     
     //frm.setComponentZOrder(ta,1);
     //trace("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! about to set visible");
     iresize();
     ta.setVisible(true);
     //irepaintFlag = 5;
     return ta;
}
private void delview(FvContext fvc) {

   if (FvContext.viewCount() >1) {
      UI.remove(fvc.vi);
      FvContext newfvc = FvContext.dispose(fvc.vi);
      if (newfvc != null)
         isetTitle(newfvc.edvec.toString());
      iresize();
   }
}

void isetView(FvContext fvc) {
    fvc.setCurrView();
    isetTitle(fvc.edvec.toString());
}

void inextView(FvContext fvc) {
  FvContext newfvc = FvContext.nextView();
  isetTitle(newfvc.edvec.toString());
}


void isetStream(Reader inreader) { /* unimplemented */}

void iremove(View vi) {
  viewCount--;
  frm.remove(vi);
}

void irepaint() {
   int ccount = frm.getComponentCount();
   for (int i =0;i<ccount;i++) {
      Component cp =frm.getComponent(i);
      if (cp.isVisible()) 
         cp.repaint();
   }
   frm.repaint();
}

void ishow() {
  //trace("!!! setting frm visible ");
  frm.setSize(frm.getPreferredSize());
  frm.setVisible(true);
  //trace("!!! done set frm visible insets " + frm.getInsets() );
}

void ipack() {
  trace("!!!!!!!!ipack layout " + frm.getLayout());
  frm.invalidate();
  frm.pack();
}

boolean iisVisible() {
  return frm.isVisible();
}

void ivalidate() {
  //trace("validate " + frm.getLayout());
  frm.realValidate();
}

void isetTitle(java.lang.String title) {
//trace("fr = " + fr + " this " + this);
  frm.setTitle(title);
}

void idispose() {
  frm.dispose();
}

void itransferFocus() {
  frm.transferFocus();
}

FvContext istartComLine() {
  if (statusBar.clearlines())
     willneedval=true;
  tfc.vi.setVisible(true);
  //tfc.vi.redraw();
  tfc.vi.npaint();
  return tfc;
}

String iendComLine() {
  tfc.vi.setVisible(false);
  if (willneedval) { 
     needval = true;
     willneedval = false;
  }

  //trace(" comline:"+ tf.getcurrobject());
  return tfc.at().toString();
}

boolean iisGotoOk(FvContext fvc) {
   return fvc != tfc;
}

void iresize() {
  needval=true;
}

public void idle() {
   //trace("reached idle needval " + needval + " needpack " + needval);
   if (irepaintFlag>0) {
      //try {Thread.sleep(200);} catch (InterruptedException e) {/*Ignore*/}
      Tools.doGC();
      irepaint();
      irepaintFlag--;
    }
    View vichanged =  FontList.updateFont();
    if (vichanged != null ) {
       isetFont(FontList.getCurr(vichanged));
       vichanged.setFont(FontList.getCurr(vichanged));
       needpack=true;
    //trace("need pack for font ");
    }
    if (needpack) {
      ipack();
      needpack=false;
    } else if (needval) {
      ivalidate();
      needval=false;
    }
    if (statusBar !=null && statusBar.isVisible()) 
       statusBar.repaint();
}

void itoggleStatus() {
   statusBar.setVisible(!statusBar.isVisible());
   //needpack=true;
}

void iclearStatus()  {
    statusBar.clearlines();
}

void istatusaddline(String str){
   statusBar.addline( str);
   MiscCommands.wakeUp();
}

void istatusSetline(String str) {
   statusBar.setline( str);
}

//void itoFront(boolean front) {
void itoFront() {
  
//   frm.setAlwaysOnTop(true);
//   frm.setAlwaysOnTop(false);
   frm.setVisible(false);
   frm.setVisible(true);
   frm.toFront();
   frm.requestFocus();
}

void ichooseWriteable(String filename) {

   if (chinst == null)
       chinst = new ChoseWrt(this);
   chinst.chosefile(filename);
}

void isetFont(Font font) {
   synchronized (EventQueue.biglock) {
      popmenu=null;
      psinst=null;
      chinst=null;
      rdinst=null;
      frm.setFont(font);
      Component cp;
      int cpi = frm.getComponentCount();
      for (cpi = cpi >2 ? 1 : cpi - 1;
          cpi >=0 && null != (cp = frm.getComponent(cpi)) ;
           --cpi)
         cp.setFont(font);
   }
}

private static class MyMenuItem extends MenuItem {
   private static final long serialVersionUID=1;
   MyMenuItem(String label,String command,Menu men,ActionListener listen) {
      super(label);
      addActionListener(listen);
      setActionCommand(command);
      men.add(this);
   }
}

private static class MyCheckboxMenuItem extends CheckboxMenuItem {
   private static final long serialVersionUID=1;
   MyCheckboxMenuItem(String label,String command,Menu men,ItemListener listen) {
      super(label);
      addItemListener(listen);
      setActionCommand(command);
      men.add(this);
   }
}

void ishowmenu(int x,int y) {
   if (popmenu==null) { 

      popmenu = new PopupMenu(); 

      Menu filem = new Menu("File");
      new MyMenuItem("Open","e",filem,this);
      new MyMenuItem("Quit","q",filem,this);
      new MyMenuItem("Save file","w",filem,this);
      popmenu.add(filem);
 
      Menu sizem = new Menu("Size");
      for (int i=4;i<20;i++)  
          new MyMenuItem(Integer.toString(i),"fontsize " + i,sizem,this);
      popmenu.add(sizem);
 
      Menu typem  = new Menu("Type");
      for (String mname:FontList.typest)
          new MyMenuItem(mname,"fonttype " + 
                mname,typem,this);
      popmenu.add(typem);
 
      new MyCheckboxMenuItem("enableclip",null,popmenu,this);
      new MyMenuItem("paste",null,popmenu,this);
      new MyMenuItem("fullscreen",null,popmenu,this);

      frm.add(popmenu);

      popmenu.addActionListener(this);
   }
   popmenu.show(frm,x,y);
}

void ipopstring(String s) {
   if (null == psinst)
     psinst = new PopString(frm);
   psinst.pop(s);
}

static class NDialog extends Dialog implements ActionListener {
   private static final long serialVersionUID=1;
   NButton resb = null;

   NDialog(Frame frm,String caption,LayoutManager lay) {
      super(frm,caption,true);
      setLayout(lay);
   }

   public void dispose() {
      super.dispose();
   }

   public void actionPerformed(ActionEvent e) {
       resb = (NButton)e.getSource();
       //trace("set resb to " + resb + " lable = " + resb.getLabel());
       
       setVisible(false);
   }

   static class NText extends TextField {
      private static final long serialVersionUID=1;
      Dialog dia;
      NText(String s,NDialog nd) {
        super(s);
        nd.add(this);
        addActionListener(nd);
      }
   }

   static class NButton extends java.awt.Button  {
      private static final long serialVersionUID=1;

      NButton(String s,NDialog nd) {
        super(s);
        nd.add(this);
        addActionListener(nd);
      }
   }

   public void windowClosing(WindowEvent e) {
     //trace("" + e );
     setVisible(false);
   }
   
   public void setVisible(boolean vis) {
     if (vis)
        resb = null;
     super.setVisible(vis);
   }
}

private static class PopString extends NDialog {
   private static final long serialVersionUID=1;
   TextArea ta = new TextArea("",30,80);

   PopString(Frame frm) {
        super(frm,"exception trace",new FlowLayout());
        new NButton("done",this);
        add(ta);
   }

   void pop(String s) {
     ta.setText(s);
     this.pack();
     setVisible(true);
   }  
}

static class ModVal extends NDialog {
   private static final long serialVersionUID=1;

   NText tf;
   ModVal(final String caption,final String units,final String []buttonVals,final long limit,Frame frame) {
      super(frame,caption,new FlowLayout());
      tf = new NText(Long.toString(limit),this);
      Label cp = new Label(caption);
      add(cp);
      Label unl = new Label(units);
      add(unl);
      for (String bv : buttonVals)
         new NButton(bv,this);
      pack();
      Dimension d1 = cp.getPreferredSize();
      Dimension d2 = tf.getPreferredSize();
      Dimension d3 = unl.getPreferredSize();
      this.setSize(d1.width+d2.width+d3.width+50,d2.height*5);
      setVisible(true);
   }
}

Result ireportModVal(final String caption,final String units,final String []buttonVals,final long limit) {

   ModVal b1= new ModVal(caption,units,buttonVals,limit,frm);
   return new Result(Integer.parseInt(b1.tf.getText()),b1.resb.getLabel());

}

private static class ChoseWrt extends NDialog {
   private static final long serialVersionUID=1;

   Label writelabel = new Label();;

   NButton svnb =   new NButton("use svn base ",this);
   NButton backb =   new NButton("back up to .orig file",this);
   NButton checkoutb =   new NButton("checkout with version control",this);
   NButton forceWriteable =   new NButton("force writeable",this);
   NButton nothing =   new NButton("do nothing",this);

   ChoseWrt(AwtInterface jwin) {

     super(jwin.frm,"Read only file action",new FlowLayout());
     add(writelabel);
  }

  void chosefile(String filename) {
     String tstring =  "You have tried to write to a read only file:" +filename;
     this.setTitle(tstring);

     writelabel.setText(tstring);
  
     this.pack();
     Dimension d = writelabel.getPreferredSize();
     this.setSize(d.width,d.height*7);
     setVisible(true);
     diaflag = resb==null
           ? UI.Buttons.IOERROR
        : resb == svnb
           ? UI.Buttons.USESVN
        : resb == checkoutb
           ? UI.Buttons.CHECKOUT
        : resb == forceWriteable
           ? UI.Buttons.MAKEWRITEABLE
        : resb == nothing
           ? UI.Buttons.DONOTHING
        : UI.Buttons.IOERROR;
}

}

//shouldn't need to be synchronized, but it if not two threads can reportDiff
// at the same time
void ireportDiff(String filename,int linenum,Object filevers,
      Object backupvers,UndoHistory.BackupStatus statusi ) {
//   synchronized (EventQueue.eventq) {
      if (rdinst==null)
         rdinst=new Diff(this);
      rdinst.pop(filename,linenum,filevers, backupvers,statusi);
//   }
}

private static class Diff extends NDialog {
   private static final long serialVersionUID=1;
   Label replab1 = new Label();
   Label replab2 = new Label();
   Label sa1 = new Label();
   Label sa2 = new Label();

   NButton okbut = new NButton("OK",this);
   NButton backbut= new NButton("use backup version",this);
   NButton filebut= new NButton("use file version",this);
   NButton diffbut= new NButton("launch diff",this);

   String l1,l2,s1,s2;
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
        return resb==null
           ? UI.Buttons.IOERROR
        : resb == okbut
           ? UI.Buttons.OK
        : resb == backbut
           ? UI.Buttons.USEBACKUP
        : resb == filebut
           ? UI.Buttons.USEFILE
        : resb == diffbut
           ? UI.Buttons.USEDIFF
        : UI.Buttons.IOERROR;
      }
     
        
     private enum Buttons {CHECKOUT , MAKEWRITEABLE , DONOTHING , MAKEBACKUP , USEFILE ,
      USEBACKUP , USEDIFF , OK , WINDOWCLOSE , IOERROR , USESVN };
   Diff(AwtInterface jwin)  {

     super(jwin.frm,"file difference problem",new GridBagLayout());

     GridBagLayout gb = (GridBagLayout)getLayout() ;
     GridBagConstraints gbc =new GridBagConstraints();
     gbc.anchor=GridBagConstraints.WEST;
     gbc.weightx=1.0;
     gbc.weighty=1.0;
     gbc.gridwidth=3;
     gb.setConstraints(replab1,gbc);
     int ycount =1;
     gbc.gridy=++ycount;
     gb.setConstraints(replab2,gbc);
     gbc.gridy=++ycount;
     gb.setConstraints(sa1,gbc);
     gbc.gridy=++ycount;
     gb.setConstraints(sa2,gbc);
     gbc.gridy=++ycount;
     gb.setConstraints(okbut,gbc);
     gbc.gridwidth=1;
     gb.setConstraints(diffbut,gbc);
     gb.setConstraints(filebut,gbc);
     gb.setConstraints(backbut,gbc);
     this.add(replab1);
     this.add(replab2);
     this.add(sa1);
     this.add(sa2);
     setinvis();
  }

  void pop (String filename,int linenum,Object filevers,
      Object backupvers,UndoHistory.BackupStatus status ) {
//     try {Thread.sleep(150);} catch (Exception e) {} // work around focus problem ???
     this.setTitle("discrepency in backup file:" +filename);
     setinvis();
     if (status.error !=null) {
        replab1.setText("corrupt backup file read in as far as possible. " + status.error);
        replab1.setForeground(Color.cyan);
        replab1.setVisible(true);
     } else if (!status.cleanQuit || !status.isQuitAtEnd) {
                         
        replab1.setText((!status.cleanQuit  
            ? "javi did not exit cleanly. " : "") +
            (!status.isQuitAtEnd 
               ? "There is undo history that is not in effect (use ^r to display)" :""));
        replab1.setVisible(true);
        replab1.setForeground(Color.black);
     }
     if (filevers==null && backupvers==null) {
        replab2.setText("the written versions of the file are consistent");
        replab2.setForeground(Color.black);
        okbut.setVisible(true);
     } else {
        l1=l2=s1=s2="";
        if (filevers==null ) {
           l1 = "backup version has extra lines at end";
           s2 = backupvers.toString();
        } else if (backupvers==null) {
           l1 = "file version has extra lines at end";
           s1 = filevers.toString();
        } else  {
           l1 = "versions differ at line " + linenum + " :" ;
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
     diaflag=mapbut();
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
  //trace("" + this );
  e.getComponent().setVisible(false);
  diaflag=Buttons.WINDOWCLOSE;
}

public void windowClosed(java.awt.event.WindowEvent e) {/*trace("" + e ); /* dont care */}
public void windowOpened(WindowEvent e) {/*trace("" + e );/* dont care */} //
public void windowActivated(WindowEvent e)  { /*trace("" + e );/* dont care */}   //
public void windowDeactivated(WindowEvent e) {/*trace("" + e ); /* dont care */} //
public void windowDeiconified(WindowEvent e) {/*trace("" + e ); /* dont care */} //
public void windowIconified(WindowEvent e) {/*trace("" + e ); /* dont care */} //

//public void setSize(int x,int y) {
//   trace("to  " + new Dimension(x,y));
//   frm.setSize(x,y);
//}

private class Layout implements LayoutManager,java.io.Serializable {

   public void addLayoutComponent(String s, Component cont) {
      //trace("" + cont);
   }

   public Dimension minimumLayoutSize(Container cont)  {
       return cont.getSize();
   }

   public Dimension preferredLayoutSize(Container cont) {
       return cont.getPreferredSize();
   }

   public void removeLayoutComponent(Component cont) {/*don't care */}

   private final int fullwidth(Component cp,int yleft,int xsize,Insets inset) {
      Dimension cpsize = cp.getPreferredSize();
      cpsize.width=xsize;
      int height = cpsize.height;
      //trace("yleft decreased by height " + height + " cp " + cp);
      if (!cp.getSize().equals(cpsize)) {
         cp.setSize(xsize-inset.left-inset.right,height);
         //trace("full width set size " + cp.getSize() + " " +  cp );
      }
      Point newloc = new Point(inset.left,yleft -height );
      Point oldloc = cp.getLocation();
      if (!newloc.equals(oldloc)) {
         //trace("full width set y location " + yleft + " " +  cp );
         cp.setLocation(new Point(inset.left,yleft -height ));
      }
      //trace("returns " + yleft + cp);
      return cp.isVisible()
         ? yleft - height
         : yleft;
   }
              
   public void layoutContainer(Container cont)  {
      Insets inset = frm.getInsets();
      //trace("entered layoutContainer insets = " + inset); //Thread.dumpStack(); for(Component comp:frm.getComponents()) trace("   component " + comp);

      if (cont!=frm) {
         trace("laying out wrong contaner ! cont = " + cont + " frame " + frm);
         return;
      }

      if (frm  == normalFrame && inset.top==0)
         return; // what is the point of layout out before we get our insets?

      Dimension startSize = frm.getSize();

      //trace("entered layoutContainer insets = " + frm.getInsets()); //Thread.dumpStack(); for(Component comp:frm.getComponents()) trace("   component " + comp);

      frm.setCompSize(startSize.width,startSize.height);
      
      if (normalFrame == frm && 
            !((frm.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH)) {
         Dimension pref = frm.getPreferredSize();
         if (!pref.equals(startSize)) {
            //trace("!!!!! setting frame size pref  " + pref + " startSize" + startSize);
            frm.setSize(pref);
            startSize = pref;
         }
      }

      int ccount = frm.getComponentCount();
      //trace("frame size at start of layout " + startSize + " insets " + inset);

      int xsize =startSize.width;
      int ysize =startSize.height;
      //trace("size =  " + frm.getSize());
      int yleft = ysize - inset.bottom;
      //trace("yleft = " + yleft + " inset.top = " + inset.top);
      yleft = fullwidth(statusBar,yleft,xsize,inset); // status
     fullwidth(tfc.vi,yleft,xsize,inset); // status
     int left=inset.left;
     int vx = (xsize - inset.left - inset.right)/viewCount;
     for (int i=ccount - viewCount;i<ccount;i++) { // views
         Component cp = frm.getComponent(i);
         //trace("processing component " + cp);
         if (cp.isVisible()) {
           Point oldloc = cp.getLocation();
           Point newloc = new Point(left,inset.top);
           if (!oldloc.equals(newloc)) {
              //trace("!!! setting new location " + newloc);
              cp.setLocation(newloc);
           }
           left+= cp.getSize().width;
         } 
      }
      needval=false;
   }
}

public void focusLost(FocusEvent e) {
    //trace("focusLost " + e.toString());
}

public void focusGained(FocusEvent e) {
    //trace("focusGained " +e);
}

/*
class myFocus extends DefaultFocusManager {

FocusManager lastf;
myFocus() {
    lastf = getCurrentManager();
//ui.trace("manager = " + lastf);
     FocusManager.disableSwingFocusManager();
//ui.trace("manager = " + getCurrentManager());
    setCurrentManager(this);
//ui.trace("manager = " + getCurrentManager());
}
public void focusNextComponent(Component aComponent) {
   super.focusNextComponent(aComponent);
   //ui.trace("entered");
}
public void focusPreviousComponent(Component aComponent) {
   super.focusPreviousComponent(aComponent);
   //ui.trace("entered");
}
public void processKeyEvent(Component focusedComponent, KeyEvent anEvent) {
   super.processKeyEvent(focusedComponent,anEvent);
//   trace("entered");
}
public boolean compareTabOrder(Component a, Component b)  {
   return super.compareTabOrder(a,b);
}
public Component getComponentAfter(Container aContainer, Component aComponent) {
   return super.getComponentAfter(aContainer,aComponent);
}
 public Component getComponentBefore(Container aContainer, Component aComponent) {
 return super.getComponentBefore(aContainer, aComponent) ;
}
 public Component getFirstComponent(Container aContainer) {
 return super.getFirstComponent(aContainer) ;
}
 public Component getLastComponent(Container aContainer) {
 return super.getLastComponent(aContainer);
}


}

//import javax.swing.DefaultFocusManager;
*/
//     new myFocus();
//     FocusManager.disableSwingFocusManager();
//     new myFocus();
//     FocusManager.disableSwingFocusManager();

String igetFile() {
  if (fdialog ==null)
     fdialog = new FileDialog(frm, "open new vifile",FileDialog.LOAD);
  fdialog.setVisible(true);
  return   fdialog.getFile();
}

}
public static void trace(String str) {
   Tools.trace(str,1);
}
}
