package javi.awt;
/* Copyright 1996 James Jensen all rights reserved */

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.KeyEvent;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;

import java.util.concurrent.TimeUnit;

import javi.DeTabber;
import javi.EventQueue;
import javi.FileList;
import javi.FvContext;
import javi.MarkEvent;
import javi.PosEvent;
import javi.Position;
import javi.UI;
import javi.View;
import javi.ScrollEvent;

import static javi.View.Opcode.*;
import static history.Tools.trace;

//import java.awt.RenderingHints;

class OldView extends AwtView {
   private static final long serialVersionUID = 1;

   private static final String copyright = "Copyright 1996 James Jensen";

   private int screenSize = 24;
   private int minColumns;
   private int pixelWidth;
   private int screenposy;
   private int screenposx;

   private int xoffset = inset;
   private Rectangle cliprect = new Rectangle(inset - 1, 0, 0, 0);

   private FontMetrics fontm;
   private int charascent;

   private int saveScreenX;
   private int charheight;
   private int charwidth;   // not an acurate number
   private boolean boldflag;
   private AtView atIt;
   private int tabStop;
   private final MyCanvas canvas = new MyCanvas();

   Canvas getComponent() {
      return canvas;
   }

   public final boolean isVisible() {
      return canvas.isVisible();
   }

   public final void repaint() {
      canvas.repaint();
   }

   OldView(boolean nextFlag) {

      super(nextFlag);
      screenposx = inset;
      tabStop = 8;
      canvas.setBackground(AtView.background);
//      setBackground(new java.awt.Color(0,255,0));

   }

   public void setTabStop(int ts) {
      tabStop = ts;
      redraw();
   }

   static final String teststr = "                                         "
      + "abcdefghi"
      + " jklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxy"
      + "zABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP"
      + "QRSTUVWXYZabcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ`~1!2@3"
      + "#4$5%6^7&8*9(0)-_=+[{]}\\|;:'\".?/>,<";

   void ssetFont(Font font) {
      //trace("entered " + this  + font);
      fontm = canvas.getFontMetrics(font);
      charwidth = (teststr.length() - 1 + fontm.stringWidth(teststr))
         / teststr.length();
      //trace("charwidth = " + charwidth + this);
      charheight = fontm.getHeight();
      //trace("charheight = " + charheight);
      boldflag = font.isBold();
      atIt = new AtView(font);
      charascent = fontm.getMaxAscent();
   }

   public int getTabStop() {
      return tabStop;
   }

   public int getRows(float scramount) {
      //trace("getRows screenSize" + screenSize + " amount " + scramount + " ret " + (int)(screenSize * scramount));
      return (int) (screenSize * scramount);
   }


   public void insertedElementsdraw(Graphics gr, int start, int amount) {

      int screenstart = start - getfileY() + screenposy;
      int screenend = screenstart + amount; // end of bad screen

      screenstart =  screenstart < 0 ? 0 : screenstart;
      if (screenend >= screenSize)
         screenend =   screenSize;
      //trace(" screenstart =" + screenstart + " screenend = " + screenend);
      if (screenend >= 0 && screenstart <= screenSize
            && screenend > screenstart) {
         if (screenend < screenSize)
            copyLines(gr, screenstart, screenSize - (screenend - screenstart),
                      screenend - screenstart);
         canvas.paintLines(gr, screenstart, screenend);
      }
   }

   public void changeddraw(Graphics gr, int index, int index2) {
      index = index - screenFirstLine();
      index2 = index2 - screenFirstLine() + 1;
      if (index < 0)
         index = 0;
      if (index2 > screenSize)
         index2 = screenSize;
      if (index2 >= 0  && index < screenSize) {
         canvas.paintLines(gr, index, index2);
      }
   }

   public void deletedElementsdraw(Graphics gr, int start, int amount) {

      if (!gettext().containsNow(1))
         throw new RuntimeException("not ready to draw");

      int gones = start - screenFirstLine(); // start of redrawing
      int gonee = start + amount - screenFirstLine(); // end of bad screen
      gones =  gones < 0 ? 0 : gones;
      if (gonee > screenSize)
         gonee =  screenSize;
      //trace("gones = " + gones +  " gonee = " + gonee
      //      + " start = " + start + " amount = " + amount);
      if (gonee >= 0 && gones < screenSize && gonee > gones) {
         if (gonee < screenSize)
            copyLines(gr, gonee, screenSize, gones - gonee);
         canvas.paintLines(gr, screenSize - (gonee - gones), screenSize);
      }

   }

   private void fixcursor(int xChange, int yChange, int newXpixel) {

      cursorChange(xChange, yChange);

      int oldx = screenposx - xoffset;
      int diffx = newXpixel - oldx;
      int newscreen = newXpixel  + xoffset;
      //trace( xChange + " " + yChange + " diffx = "
      //       + diffx + " xoffset = " + xoffset + " screenposx = "
      //       + screenposx);
      if ((newscreen < pixelWidth) &&  (newscreen >= inset))
         screenposx += diffx;
      else  { // redraw
         xoffset -= diffx;
         //trace("xoffset " + xoffset);
         if (xoffset > inset) {
            diffx = xoffset - inset;
            xoffset = inset;
            //trace("xoffset " + xoffset);
            screenposx -= diffx;
         }
         //trace("doing redraw oldsaveop = " + saveop);
         redraw();
      }

      // if cursor off screen
      if (yChange != 0) {
         if ((screenposy < 0) ||  (screenposy >= screenSize))
            moveScreen(yChange);
      }
   }

   public int yCursorChanged(int newY) {

      //trace(" newY " + newY);

      int yChange = newY - getfileY();
      screenposy += yChange;

      //trace("cursorchanged " + yChange + " screenSaveX " + saveScreenX);
      String oline = gettext().at(newY).toString();
      String nline = oline;

      int nXchange;
      int charoff;
      if (tabStop != 0) {
         int tabOffset = nline.indexOf('\t');
         if (tabOffset != -1)  {
            int[] tvals = new int[1];
            nline = DeTabber.deTab(nline, tabOffset, tabStop, tvals);
            charoff = charOffset(nline, saveScreenX);
            int xTabOff = DeTabber.tabFind(oline, tabOffset, tabStop, charoff);
            //trace("xTabOff = " + xTabOff  + " inx = " + inx + " charoff = " + charoff);
            tvals[0] = xTabOff;
            DeTabber.deTab(oline, tabOffset, tabStop, tvals);
            charoff = tvals[0];
            nXchange = xTabOff - getfileX();
            //trace("nXchange = " + nXchange);
         }  else {
            charoff = charOffset(nline, saveScreenX);
            nXchange = charoff - getfileX();
            //trace("nXchange = " + nXchange);
         }
      } else {
         charoff = charOffset(oline, saveScreenX);
         nXchange = charoff - getfileX();
         //trace("nXchange = " + nXchange);
      }
      int newx = charoff == 0
         ? 0
         : fontm.stringWidth(nline.substring(0, charoff));

      //trace("nXchange = " + nXchange + " charoff " + charoff + " newx " + newx);
      setFilePos(nXchange + getfileX(), newY);
      fixcursor(nXchange, yChange, newx);
      return getfileX();
   }

   public void cursorChanged(int newX, int newY) {


      //trace(" newX " + newX + " newY " + newY);
      int yChange = newY - getfileY();
      screenposy += yChange;

      int newx = 0;

      if (newX != 0) {
         int charoff = newX;
         String oline = gettext().at(newY).toString();
         String nline = oline;

         if (tabStop != 0) {
            int tabOffset = oline.indexOf('\t');
            if (tabOffset != -1) {
               int[] tvals = new int[1];
               tvals[0] = charoff;
               nline = DeTabber.deTab(nline, tabOffset, tabStop, tvals);
               charoff = tvals[0];
            }
         }
         if (charoff != 0)
            newx = fontm.stringWidth(nline.substring(0, charoff));
      }

      saveScreenX = newx;
      setFilePos(newX, newY);
      //trace(" saveScreenX changed " + saveScreenX);

      fixcursor(0, yChange, newx);
   }

   public void refresh(Graphics gr) {
      //trace("refresh " + this);
      //trace("cliprect = " + gr.getClipBounds()  + " my cliprect = " + cliprect);
      gr.setClip(null);
      gr.setColor(AtView.interFrame);
      gr.fillRect(0, 0, inset, screenSize * charheight);
      gr.fillRect(pixelWidth - inset, 0, inset , screenSize * charheight);
      gr.setClip(cliprect);
      canvas.paintLines(gr, 0, screenSize);
      //trace(" done REDRAW " + this);
   }

   private void copyLines(Graphics gr, int start, int end, int delta) {
      //trace("copyLines");
      if (start < 0 || end > screenSize || start >= end
            || start + delta < 0 || end + delta > screenSize)
         throw new RuntimeException("start = " + start + " end = "
            + end + " delta = " + delta);

//     try {
      gr.copyArea(0, start * charheight, pixelWidth,
            (end - start) * charheight, 0, delta * charheight);
//      } catch  (sun.java2d.InvalidPipeException e) {
//         trace("caught exception + " + e);
//         e.printStackTrace();
//      }
   }

   private int fillheader(Graphics gr, int start) {
      //trace("fillheader " + screenFirstLine());
      if (start + screenFirstLine() < 1) {
         start = 1 - screenFirstLine();
         gr.setColor(AtView.noFile);
         gr.fillRect(0, 0, pixelWidth , start * charheight);
      }
      return start;
   }

   private int filltrailer(Graphics gr, int end) {
      //trace("filltrailer end " + end);
      //trace("end = "  + end + " firstline = " + screenFirstLine());
      int numlines = gettext().readIn(); // number of lines read in
      //trace("end = "  + end + " firstline = " + screenFirstLine()+ " numlines " + numlines);
      if (end + screenFirstLine() > numlines) {
         end = numlines - screenFirstLine();
         if (end != screenSize) {
            if (!gettext().donereading())  {
               gr.setColor(AtView.unFinished);
               needMoreText(end + screenFirstLine());
            } else
               gr.setColor(AtView.noFile);
            gr.fillRect(0, end * charheight,
                             pixelWidth , (screenSize - end) * charheight);
         }
      }
      return end;
   }

   public int screenFirstLine() {
      //trace( "sfl " + fileposy + " screenposy " + screenposy);
      return getfileY() - screenposy;
   }

   private void moveScreen(int amount) {
      screenposy -= amount;
      mscreen(amount, screenSize);
   }

   public void movescreendraw(Graphics gr, int amount) {
      int cstart, cend, pstart, pend;
      if (amount > 0) {
         cstart = amount;
         cend = screenSize;
         pstart = screenSize - amount;
         pend = screenSize;
      } else  {
         cstart = 0;
         cend = screenSize + amount;
         pstart = 0;
         pend = -amount;
      }
      copyLines(gr, cstart, cend, -amount);
      canvas.paintLines(gr, pstart , pend);
   }


   Shape updateCursorShape(Shape sh) {
      int   cx = screenposx;
      String iString = getInsertString();
      if (iString != null) {
         int tabOffset = iString.indexOf('\t');
         if  (-1 != tabOffset)
            iString = DeTabber.deTab(iString, tabOffset, tabStop, new int[1]);
         //trace("stringWidth " + fontm.stringWidth(iString) + " iString:" + iString);
         cx += fontm.stringWidth(iString);
      }
      int rx = cx - 1;
      int ry = (screenposy) * (charheight) - 1;
      int rwidth = boldflag ? 2 : 1;
      int rheight = charheight + 1;

      if (sh instanceof Rectangle) {
         Rectangle rec = (Rectangle) sh;
         if (rec.x  == rx && rec.y == ry
               && rec.height == rheight
               && rec.width == rwidth)
            return sh;
      }
      //trace("updateCursorShape returning " + new Rectangle(rx, ry, rwidth, rheight));
      return new Rectangle(rx, ry, rwidth, rheight);
   }

   int charOffset(String line, int xpos) {
      int charguess = xpos / charwidth;
      //trace("charOffset xpos " + xpos + " line:" + line + " chargues = " + charguess);
      if (charguess > line.length())
         charguess = line.length();
      int xguess = fontm.stringWidth(line.substring(0, charguess));
      int lastxguess = xguess;
      //trace("guess1 = " + charguess);
      if (xpos < xguess) {
         while (xpos < xguess) {
            if (charguess <= 0)
               break;
            charguess--;
            lastxguess = xguess;
            xguess = fontm.stringWidth(line.substring(0, charguess));
           //trace("guess2 = " + charguess + " xguess " + xguess + " lastxguess " + lastxguess);
         }
         return (xpos - xguess) <= (lastxguess - xpos)
                ? charguess
                : charguess + 1;
      } else {
         while (xpos > xguess) {
            if (charguess >= line.length())
               break;
            charguess++;
            lastxguess = xguess;
            xguess = fontm.stringWidth(line.substring(0, charguess));
            //trace("guess3 = " + charguess + " xguess " + xguess + " lastxguess " + lastxguess);
         }
         return (xguess - xpos) <= (xpos - lastxguess)
                ? charguess
                : charguess - 1;
      }
   }

   private int tcharOffset(String line, int xpos) {
      if (tabStop != 0) {
         int tabOff = line.indexOf('\t');
         if (tabOff != -1) {
            String nline = DeTabber.deTab(line, tabOff, tabStop, new int[1]);
            int charoff = charOffset(nline, xpos);
            return DeTabber.tabFind(line, tabOff, tabStop, charoff);
         } else
            return charOffset(line, xpos);
      } else
         return charOffset(line, xpos);
   }

   Position mousepos(MouseEvent event) {
      int ypos = event.getY() / charheight;
      ypos = ypos - screenposy + getfileY();
      if (ypos < 1)
         ypos = 1;
      else if (!gettext().containsNow(ypos))
         ypos = gettext().readIn() - 1;
      // figure out where in the line x is
      String line = gettext().at(ypos).toString();
      //trace("xoffset " + xoffset + " getX " + event.getX());
      int xpos = event.getX() - xoffset;
      if (xpos <= 0)
         xpos = 0;
      return new Position(tcharOffset(line, xpos), ypos,
         gettext().fdes(), "mouse pos");
   }

   // returns amount cursor needs to be adjusted
   public int screeny(int amount)  {
      //trace("screeny " + amount);
      //move the screen and if necessary the cursor
      if (screenFirstLine() + amount <= -screenSize + 1)
         if (screenFirstLine() <= 1)
            amount = 0;
         else
            amount =  -screenFirstLine();
      else if (!gettext().containsNow(screenFirstLine() + amount))
         if (gettext().containsNow(screenFirstLine() + screenSize))
            amount = gettext().readIn() - 1 - screenFirstLine();
         else
            amount = 0;

      if (amount != 0) {
         moveScreen(amount);
         if ((screenposy >= screenSize)
               || (screenposy < 0))
            return amount;
      }
      return 0;
      //trace("exit screensize = " + screenSize + " fcontext.screenFirstLine()= " + fcontext.screenFirstLine());
   }

   public void setSizebyChar(int xchar, int ychar) {
      //trace("setSizebyChar xchar = " + xchar + " ychar = " + ychar);
      if (xchar < 0)
         xchar = minColumns;
      if (ychar < 0)
         ychar = screenSize;
      canvas.setSize(xchar * charwidth, ychar * charheight);
      //UI.resize();
      //invalidate();//???
      //trace("pixelwidth  = " + pixelWidth + " charwidth = " + charwidth + " screenSize " + screenSize);
   }

   protected void startInsertion(javi.View.Inserter ins) {
      canvas.addInputMethodListener((InHandler) ins);
      canvas.enableInputMethods(true);
   }

   protected void endInsertion(javi.View.Inserter ins) {
      canvas.enableInputMethods(false);
      canvas.removeInputMethodListener((InHandler) ins);
   }
   class MyCanvas extends Canvas {

      private int mousePressed = 0;
      private transient Graphics oldgr;
      private transient Image dbuf;
      private transient Graphics2D imageg;

//RenderingHints qualityHints = new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
//qualityHints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
////qualityHints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
//qualityHints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
//imageg.setRenderingHints(qualityHints);

      private void common() {
         /*
            HashSet<AWTKeyStroke> keyset =
                new HashSet<AWTKeyStroke>(getFocusTraversalKeys(
                KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
            );

            for (Iterator it = keyset.iterator();it.hasNext();) {
                AWTKeyStroke key = (AWTKeyStroke)(it.next());
                if (key.getKeyCode()== KeyEvent.VK_TAB
                      && key.getModifiers() == 0)
                  it.remove();
            }
            setFocusTraversalKeys(KeyboardFocusManager.
               FORWARD_TRAVERSAL_KEYS, keyset);

            enableInputMethods(false);
         */
         enableEvents(AWTEvent.MOUSE_EVENT_MASK
            | AWTEvent.MOUSE_MOTION_EVENT_MASK
            | AWTEvent.MOUSE_WHEEL_EVENT_MASK
         );
      }

      private void readObject(java.io.ObjectInputStream is) throws
            ClassNotFoundException, java.io.IOException {

         is.defaultReadObject();
         setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
         common();
      }

      MyCanvas() {
         common();
      }

      public Dimension getPreferredSize() {
         //trace("getPreferredSize screensize " + screenSize + " charheight " + charheight  + " pixelWidth " + pixelWidth);
         //trace("screen y = " + screenSize * charheight);
         return new Dimension(pixelWidth, screenSize * charheight);
      }

      public  void setSize(int newx, int newy) {
         //trace("setSize entered (" + newx + "," + newy + ")" + this);
         //if (y == 0){
         // Thread.dumpStack();
         // return;
         //}

         screenSize = newy / charheight;

         minColumns = (newx - 2 * inset) / charwidth;
         newy = screenSize * charheight;
         pixelWidth =  minColumns * charwidth + 2 * inset;
         cliprect.y = 0;
         cliprect.width = pixelWidth - 2 * inset + 1;
         cliprect.height = newy;
         if (screenposy >= screenSize)
            moveScreen(screenposy - screenSize + 1);
         super.setSize(pixelWidth, newy);
         //imageg = null;
         //trace("oldview = " + this + " cliprect = " + cliprect);
         //trace("pixelwidth  = " + pixelWidth + " charwidth = " + charwidth + " screenSize " + screenSize);
      }

      @SuppressWarnings("fallthrough")

      private void mousepress(MouseEvent event) {
         //trace("modifiers = " +Integer.toHexString( event.getModifiers()));

         Position p = mousepos(event);

         //trace("Position " + p + " event vi " + vi);
         FvContext newfvc = FvContext.getcontext(OldView.this, getCurrFile());

         //trace("fvc " + fvc  + " newfvc " + newfvc);
         switch (event.getButton()) {
            case MouseEvent.BUTTON1:
               EventQueue.insert(new PosEvent(newfvc, p));
               break;

            case MouseEvent.BUTTON2:
               if (newfvc.edvec.containsNow(p.y)) {
                  Object line = newfvc.edvec.at(p.y);
                  if (line instanceof Position) {
                     View nextView = newfvc.findNextView();
                     try {
                        FileList.gotoposition((Position) line, true, nextView);
                     } catch (Exception ex) {
                        UI.popError("unexpected Exception ", ex);
                     }
                  }
               }
               break;

            case MouseEvent.BUTTON3:
               Point pt = getLocation();
               UI.showmenu(event.getX() + pt.x, event.getY() + pt.y);
               break;

            default:
               trace("no button ???? event modifiers = " + Integer.toHexString(
                  event.getModifiers()));
         }
      }

      final void mouserelease(MouseEvent event) {

         //trace(" clickcount " + event.getClickCount() + " has focus" + fvc.vi.hasFocus());
         Position p = mousepos(event);
         FvContext fvc = FvContext.getCurrFvc();
         //trace("Position " + p + " event vi " + vi);
         if (fvc != FvContext.getcontext(OldView.this, getCurrFile()))
            return;

         if (event.getButton()  == MouseEvent.BUTTON1) {
            //trace("setting markmode ");
            //fvc.cursorabs(p);
            if (fvc.inserty() != p.y || fvc.insertx() != p.x)  {
               EventQueue.insert(new MarkEvent(p));
            }
         }
      }

      public void processEvent(AWTEvent ev) {
         //trace("ev " + ev.getID() + "  has focus " + hasFocus());
         switch (ev.getID()) {
            case MouseEvent.MOUSE_PRESSED:
               mousepress((MouseEvent) ev);
               mousePressed = ((MouseEvent) ev).getButton();
               break;

            case MouseEvent.MOUSE_RELEASED:
               mouserelease((MouseEvent) ev);
               mousePressed = 0;
               break;

            case MouseEvent.MOUSE_WHEEL:
               MouseWheelEvent mwv = (MouseWheelEvent) ev;
               int mvAmt = mwv.getScrollType()
                     == MouseWheelEvent.WHEEL_BLOCK_SCROLL
                  ? getRows(1.f)
                  : mwv.isControlDown()
                     ? getRows(1.f)
                     : mwv.getScrollAmount();
               EventQueue.insert(new ScrollEvent(mvAmt
                  * mwv.getWheelRotation()));
               return;

            case MouseEvent.MOUSE_DRAGGED:
               MouseEvent mev = (MouseEvent) ev;
               if (mousePressed == 1)
                  updateTempMarkPos(mousepos(mev));
               break;
            case MouseEvent.MOUSE_MOVED:
            case MouseEvent.MOUSE_ENTERED:
            case MouseEvent.MOUSE_EXITED:
            case MouseEvent.MOUSE_CLICKED:
            case KeyEvent.KEY_RELEASED:
            case KeyEvent.KEY_TYPED:
               break;

            default:
               trace("unhandle event ev " + ev + "  has focus " + hasFocus());
               super.processEvent(ev);
         }
      }

      public void paint(Graphics g) {
         //trace("paint called ");
         try {
            redraw();
            npaint((Graphics2D) g);
         } catch (Throwable e) {
            UI.popError("unexpected exception", e);
         }
      }

      public void update(Graphics g) {
         try {
         //trace("update called ");
            //if (op.currop == REDRAW) trace(" got update REDRAW!!");
            npaint((Graphics2D) g);
         } catch (Throwable e) {
            UI.popError("unexpected exception", e);
         }
      }

      private void npaint(Graphics2D gr) throws InterruptedException {
         //trace("npaint");
         if (gettext() == null)
            return;

         if ((imageg == null) || (gr != oldgr)) {
            dbuf = canvas.createImage(pixelWidth * 2, charheight);
            imageg = (Graphics2D) dbuf.getGraphics();
            //trace("imageg " + imageg);
            if (imageg == null)
               throw new RuntimeException("imageg null!!");
            oldgr = gr;
         }

         if (!EventQueue.biglock2.tryLock(1, TimeUnit.MILLISECONDS)) {
            trace("repaint because failed lock " + gettext() + " or lock");
            repaint(200);
         } else
            try {
               if (gettext().isValid() && gettext().containsNow(1)) {
                  getChanges();
                  copt.rpaint(gr);
               } else {
                  trace("repaint because of invalid or empty");
                  repaint(200);
               }
            } catch (Throwable e) {
               UI.popError("npaint caught", e);
            } finally {
               EventQueue.biglock2.unlock();
            }
      }
      public void setFont(Font font) {

         //trace("setting View font " + font + " "  + this);
         ssetFont(font);
      }

      void paintLines(Graphics gr, int start, int end) {
         //trace("paintLines start = " + start + " end " + end);
         //Thread.dumpStack();
         //trace("imageg " + imageg);
         assert start >= 0 && end <= screenSize && start < end;
         if (start < 0 || end > screenSize || start >= end)
            throw new RuntimeException("start = " + start + " end = " + end
               + " screenSize = " + screenSize);  // should never happen

         start = fillheader(gr, start);
         end = filltrailer(gr, end);

         //trace("paint2 end = "  + end + " firstline = " + screenFirstLine());
         for (int index = start, tindex = index + screenFirstLine();
                 index < end;
                 index++, tindex++) {
            imageg.setColor(AtView.background);
            imageg.fillRect(0, 0, pixelWidth , charheight);
            //trace("setting text " + gettext().at(tindex).toString());
            atIt.setText(gettext().at(tindex).toString());

            if ((index == screenposy))  {
               atIt.emphasize(true);
               String iString = getInsertString();
               if (iString != null)
                  atIt.addOlineText(iString, getfileX(), getOverwrite());
            }

            if (atIt.length() != 0) {
               MarkInfo mpmark = getPmark();
               if (mpmark.getMark() != null) {
                  //trace("highlight tindex = " + tindex + " pmark " + pmark);
                  //trace("hilight " + pmark.starth(tindex) + "," + pmark.endh(tindex));
                  atIt.setHighlight(mpmark.starth(tindex), mpmark.endh(tindex));
               }
               if (tabStop != 0)
                  atIt.deTab(tabStop);
               imageg.drawString(atIt, xoffset, charascent);
            }
   //      gr.setColor(Color.cyan);
   //      gr.fillRect(xoffset, index * charheight, pixelWidth , charheight);
   //      gr.setColor(atIt.lightYellow);
   //      if (atIt.length() != 0) {
   //         gr.drawString(atIt, xoffset, charascent + index * charheight);
   //      }
            gr.drawImage(dbuf, 0, index * charheight, null);
            //try {Thread.sleep(100);} catch (InterruptedException e) {/*Ignore*/}
         }


      }

      public final boolean isFocusable() {
         return false;
      }
   }
   class Ch extends ChangeOpt {
      public void rpaint(Graphics2D gr) {
         Opcode currop = resetOp();
         if (currop != NOOP) {
            //if (currop != BLINKCURSOR) trace("rpaint currop = " + currop + " this " + this);
            //trace("rpaint currop = " + currop + " this " + this);

            // cursor must be off before other drawing is done, or it messes up XOR
            if (currop == BLINKCURSOR || getCursorOn()) {
               bcursor(gr);
            }

            switch (currop) {

               case REDRAW:
                  refresh(gr);
                  break;

               case INSERT:
                  insertedElementsdraw(gr, getSaveStart(), getSaveAmount());
                  break;

               case DELETE:
                  deletedElementsdraw(gr, getSaveStart(), getSaveAmount());
                  break;

               case CHANGE:
                  changeddraw(gr, getSaveStart(), getSaveAmount());
                  break;

               case MSCREEN:
                  movescreendraw(gr, getSaveAmount());
                  break;

               case NOOP:
               case BLINKCURSOR:
                  break;
            }
            if (currop != BLINKCURSOR)
               bcursor(gr); // always leave cursor on after doing something
         }
      }
   }
   private Ch copt;
   protected ChangeOpt getChangeOpt() {
      if (copt == null)
         copt = new Ch();
      return copt;
   }
}
