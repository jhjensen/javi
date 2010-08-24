package javi;
/* Copyright 1996 James Jensen all rights reserved */
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import static javi.View.Opcode.*;

//import java.awt.RenderingHints;

class OldView  extends View {
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

   private transient Image dbuf;
   private transient Graphics2D imageg;
   private int saveScreenX;
   private int charheight;
   private int charwidth;   // not an acurate number
   private boolean boldflag;
   private AtView atIt;
   private int tabStop;

   OldView(boolean nextFlag) {

      super(nextFlag);
      screenposx = inset;
      tabStop = 8;
      setBackground(AtView.background);
//      setBackground(new java.awt.Color(0,255,0));

   }

   void setTabStop(int ts) {
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
      fontm = getFontMetrics(font);
      trace("the First usage of a font sure is slow!!!");
      charwidth = (teststr.length() - 1 + fontm.stringWidth(teststr))
         / teststr.length();
      //trace("charwidth = " + charwidth + this);
      charheight = fontm.getHeight();
      boldflag = font.isBold();
      atIt = new AtView(font);
      charascent = fontm.getMaxAscent();
   }

   int getTabStop() {
      return tabStop;
   }

   int getRows(float scramount) {
      //trace("getRows screenSize" + screenSize + " amount " + scramount + " ret " + (int)(screenSize * scramount));
      return (int) (screenSize * scramount);
   }


   void insertedElementsdraw(Graphics gr, int start, int amount) {

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
         paintLines(gr, screenstart, screenend);
      }
   }

   void changeddraw(Graphics gr, int index, int index2) {
      index = index - screenFirstLine();
      index2 = index2 - screenFirstLine() + 1;
      if (index < 0)
         index = 0;
      if (index2 > screenSize)
         index2 = screenSize;
      if (index2 >= 0  && index < screenSize) {
         paintLines(gr, index, index2);
      }
   }

   void deletedElementsdraw(Graphics gr, int start, int amount) {

      while (!gettext().contains(1))
         throw new RuntimeException();

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
         paintLines(gr, screenSize - (gonee - gones), screenSize);
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

   int yCursorChanged(int newY) {

      //trace(" newY " + newY);

      int yChange = newY - getfileY();
      screenposy += yChange;

      //trace("cursorchanged " + xChange + "," + yChange + " screenSaveX " + saveScreenX + " inx " + inx);
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

   void cursorChanged(int newX, int newY) {

      //trace(" yChange " + yChange);

      int yChange = newY - getfileY();
      screenposy += yChange;

      String oline = gettext().at(newY).toString();
      String nline = oline;

      int charoff = newX;
      if (tabStop != 0) {
         int tabOffset = oline.indexOf('\t');
         if (tabOffset != -1) {
            int[] tvals = new int[1];
            tvals[0] = charoff;
            nline = DeTabber.deTab(nline, tabOffset, tabStop, tvals);
            charoff = tvals[0];
         }
      }

      int newx = charoff == 0
         ? 0
         : fontm.stringWidth(nline.substring(0, charoff));
      saveScreenX = newx;
      setFilePos(newX, newY);
      //trace(" saveScreenX changed " + saveScreenX);

      fixcursor(0, yChange, newx);
   }

   void refresh(Graphics gr) {
      //trace("refresh " + this);
      //trace("cliprect = " + gr.getClipBounds()  + " my cliprect = " + cliprect);
      gr.setClip(null);
      gr.setColor(AtView.interFrame);
      gr.fillRect(0, 0, inset, screenSize * charheight);
      gr.fillRect(pixelWidth - inset, 0, inset , screenSize * charheight);
      gr.setClip(cliprect);
      paintLines(gr, 0, screenSize);
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

   void newGraphics() {
      //trace("allocImage");
      dbuf = createImage(pixelWidth * 2, charheight);
      imageg = (Graphics2D) dbuf.getGraphics();
      //trace("imageg " + imageg);
      if (imageg == null)
         throw new RuntimeException("imageg null!!");  // should never happen

//RenderingHints qualityHints = new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
//qualityHints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
////qualityHints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
//qualityHints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
//imageg.setRenderingHints(qualityHints);

   }

   private int fillheader(Graphics gr, int start) {
      //trace("fillheader");
      if (start + screenFirstLine() < 1) {
         start = 1 - screenFirstLine();
         gr.setColor(AtView.noFile);
         gr.fillRect(0, 0, pixelWidth , start * charheight);
      }
      return start;
   }

   private int filltrailer(Graphics gr, int end) {
      //trace("filltrailer");
      //trace("end = "  + end + " firstline = " + screenFirstLine()+ " fin = " + gettext().finish());
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

   private void paintLines(Graphics gr, int start, int end) {
      //trace("paintLines start = " + start + " end " + end);
      //Thread.dumpStack();
      if (imageg == null)
         newGraphics();
      //trace("imageg " + imageg);
      //assert start >= 0 && end <= screenSize && start < end;
      if (start < 0 || end > screenSize || start >= end)
         throw new RuntimeException("start = " + start + " end = " + end
            + " screenSize = " + screenSize);  // should never happen

      start = fillheader(gr, start);
      end = filltrailer(gr, end);

      //trace("paint2 end = "  + end + " firstline = " + screenFirstLine());
      for (int index = start, tindex = index + screenFirstLine();
              index < end;
              index++, tindex++) {
         //trace("imageg " + imageg);
         imageg.setColor(AtView.background);
         imageg.fillRect(0, 0, pixelWidth , charheight);
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

   int screenFirstLine() {
      //trace( "sfl " + fileposy + " screenposy " + screenposy);
      return getfileY() - screenposy;
   }

   private void moveScreen(int amount) {
      screenposy -= amount;
      mscreen(amount, screenSize);
   }

   void movescreendraw(Graphics gr, int amount) {
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
      paintLines(gr, pstart , pend);
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
         ypos = gettext().finish() - 1;
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
   int screeny(int amount)  {
      //trace("screeny " + amount);
      //move the screen and if necessary the cursor
      if (screenFirstLine() + amount <= -screenSize + 1)
         if (screenFirstLine() <= 1)
            amount = 0;
         else
            amount =  -screenFirstLine();
      else if (!gettext().containsNow(screenFirstLine() + amount))
         if (gettext().containsNow(screenFirstLine() + screenSize))
            amount = gettext().finish() - 1 - screenFirstLine();
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

   public Dimension getPreferredSize() {
      //trace("getPreferredSize screensize " + screenSize + " charheight +" + charheight  + " pixelWidth " + pixelWidth);
      return new Dimension(pixelWidth, screenSize * charheight);
//Thread.dumpStack();
   }

   void setSizebyChar(int x, int y) {
      //trace("setSizebyChar x = " + x + " y = " + y);
      if (x < 0)
         x = minColumns;
      if (y < 0)
         y = screenSize;
      minColumns = x;
      screenSize = y;
      pixelWidth = minColumns * charwidth + 2 * inset;
      //UI.resize();
      //invalidate();//???
      //trace("pixelwidth  = " + pixelWidth + " charwidth = " + charwidth + " screenSize " + screenSize);
   }

   public  void setSize(int newx, int newy) {
      //trace("setSize entered (" + x + "," + y + ")" + this);
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
      super.setSize(pixelWidth , newy);
      if (screenposy >= screenSize)
         moveScreen(screenposy - screenSize + 1);
      //imageg = null;
      //trace("oldview = " + this + " cliprect = " + cliprect);
      //trace("pixelwidth  = " + pixelWidth + " charwidth = " + charwidth + " screenSize " + screenSize);
   }
}
