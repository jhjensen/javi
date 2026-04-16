package javi.awt;

import static history.Tools.trace;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Color;
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
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;

import java.util.concurrent.TimeUnit;

import static javi.ChangeOpt.Opcode.BLINKCURSOR;
import static javi.ChangeOpt.Opcode.CHANGE;
import static javi.ChangeOpt.Opcode.DELETE;
import static javi.ChangeOpt.Opcode.INSERT;
import static javi.ChangeOpt.Opcode.MSCREEN;
import static javi.ChangeOpt.Opcode.NOOP;
import static javi.ChangeOpt.Opcode.REDRAW;
import javi.DeTabber;
import javi.EventQueue;
import javi.FileList;
import javi.FoldModel;
import javi.FvContext;
import javi.MarkEvent;
import javi.MiscCommands;
import javi.PosEvent;
import javi.Position;
import javi.ShellManager;
import javi.ScreenAttributes;
import javi.UI;
import javi.View;
import javi.ChangeOpt;
import javi.ScrollEvent;

//import java.awt.RenderingHints;

/**
 * Primary text rendering view using AWT Canvas for the Javi editor.
 *
 * <p>
 * OldView handles all text display and cursor rendering:
 * <ul>
 * <li><b>Text rendering</b>: Line-by-line with syntax awareness</li>
 * <li><b>Cursor display</b>: Block/line cursor with blink support</li>
 * <li><b>Scrolling</b>: Vertical scrolling with optimized repainting</li>
 * <li><b>Mouse handling</b>: Click positioning, selection, wheel scroll</li>
 * <li><b>Double buffering</b>: Offscreen image for flicker-free display</li>
 * </ul>
 *
 * <h2>Coordinate Systems</h2>
 * <ul>
 * <li><b>File coordinates</b>: Line number (1-based) and column</li>
 * <li><b>Screen coordinates</b>: Pixel position on canvas</li>
 * <li><b>Character grid</b>: Row/column in monospace character units</li>
 * </ul>
 *
 * <h2>Rendering Optimization</h2>
 * <p>
 * OldView uses {@link javi.ChangeOpt} to minimize repainting:
 * </p>
 * <ul>
 * <li>{@code BLINKCURSOR} - Only toggle cursor visibility</li>
 * <li>{@code INSERT} - Scroll optimization for line insertion</li>
 * <li>{@code CHANGED} - Repaint only changed line range</li>
 * <li>{@code REDRAW} - Full repaint when necessary</li>
 * </ul>
 *
 * <h2>Tab Handling</h2>
 * <p>
 * Uses {@link DeTabber} to convert tab characters to spaces
 * based on configurable tab stop width.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * <b>WARNING</b>: Some methods have race conditions. The {@code imageg}
 * (offscreen graphics) access is not fully thread-safe. See BUGS.md.
 * </p>
 *
 * @see View
 * @see AwtView
 * @see AtView
 */
final class OldView extends AwtView {
   private static final long serialVersionUID = 1;

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
   private int charwidth; // not an acurate number
   private boolean boldflag;
   private AtView atIt;
   private int tabStop;
   private Font activeFont;
   private final MyCanvas canvas = new MyCanvas();

   Canvas getComponent() {
      return canvas;
   }

   public boolean isVisible() {
      return canvas.isVisible();
   }

   public void repaint() {
      canvas.repaint();
   }

   OldView(boolean nextFlag) {

      super(nextFlag);
      screenposx = inset;
      tabStop = 8;
      canvas.setBackground(AtView.background);
      // setBackground(new java.awt.Color(0,255,0));

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
      // trace("entered " + this + font);
      activeFont = font;
      fontm = canvas.getFontMetrics(font);
      charwidth = (teststr.length() - 1 + fontm.stringWidth(teststr))
            / teststr.length();
      // trace("charwidth = " + charwidth + this);
      charheight = fontm.getHeight();
      // trace("charheight = " + charheight);
      boldflag = font.isBold();
      atIt = new AtView(font);
      charascent = fontm.getMaxAscent();
   }

   /**
    * Checks if the current buffer's FvContext has a font override
    * and switches to it if different from the active font.
    * Called from npaint while holding biglock2.
    */
   private void applyOverrideFont() {
      FvContext<?> fvc = FvContext.findContext(OldView.this, gettext());
      Font wantedFont;
      if (fvc != null && fvc.getOverrideFont() instanceof Font) {
         wantedFont = (Font) fvc.getOverrideFont();
      } else if (gettext() == AwtFontList.getList()) {
         // Don't dynamically change view font while browsing the font
         // list — getCurr tracks cursor position, so charheight changes
         // on every cursor move and lines render at wrong positions.
         return;
      } else {
         wantedFont = AwtFontList.getCurr(OldView.this);
      }
      if (null != wantedFont && !wantedFont.equals(activeFont)) {
         ssetFont(wantedFont);
         // Recalculate screen metrics for new font dimensions
         Dimension d = canvas.getSize();
         if (d.height > 0 && charheight > 0) {
            screenSize = d.height / charheight;
            minColumns = (d.width - 2 * inset) / charwidth;
            pixelWidth = minColumns * charwidth + 2 * inset;
            cliprect.width = pixelWidth - 2 * inset + 1;
            cliprect.height = screenSize * charheight;
         }
         // Recreate dbuf with correct dimensions for new font
         // so the current paint cycle can use it immediately
         canvas.dbuf = canvas.createImage(
            pixelWidth * 2, charheight);
         canvas.imageg =
            (Graphics2D) canvas.dbuf.getGraphics();
      }
   }

   public int getTabStop() {
      return tabStop;
   }

   public int getRows(float scramount) {
      // trace("getRows screenSize" + screenSize + " amount " + scramount + " ret " +
      // (int)(screenSize * scramount));
      return (int) (screenSize * scramount);
   }

   public void insertedElementsdraw(Graphics gr, int start, int amount) {

      int screenstart = start - getfileY() + screenposy;
      int screenend = screenstart + amount; // end of bad screen

      screenstart = screenstart < 0 ? 0 : screenstart;
      if (screenend >= screenSize)
         screenend = screenSize;
      // trace(" screenstart =" + screenstart + " screenend = " + screenend);
      if (screenend >= 0 && screenstart <= screenSize
            && screenend > screenstart) {
         if (screenend < screenSize)
            copyLines(gr, screenstart, screenSize - (screenend - screenstart),
                  screenend - screenstart);
         canvas.paintLines(gr, screenstart, screenend);
      }
   }

   public void changeddraw(Graphics gr, int index, int index2) {
      index -= screenFirstLine();
      index2 = index2 - screenFirstLine() + 1;
      if (index < 0)
         index = 0;
      if (index2 > screenSize)
         index2 = screenSize;
      if (index2 >= 0 && index < screenSize) {
         canvas.paintLines(gr, index, index2);
      }
   }

   public void deletedElementsdraw(Graphics gr, int start, int amount) {

      if (!gettext().containsNow(1))
         throw new RuntimeException("not ready to draw");

      int gones = start - screenFirstLine(); // start of redrawing
      int gonee = start + amount - screenFirstLine(); // end of bad screen
      gones = gones < 0 ? 0 : gones;
      if (gonee > screenSize)
         gonee = screenSize;
      // trace("gones = " + gones + " gonee = " + gonee
      // + " start = " + start + " amount = " + amount);
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
      int newscreen = newXpixel + xoffset;
      // trace( xChange + " " + yChange + " diffx = "
      // + diffx + " xoffset = " + xoffset + " screenposx = "
      // + screenposx);
      if ((newscreen < pixelWidth) && (newscreen >= inset))
         screenposx += diffx;
      else { // redraw
         xoffset -= diffx;
         // trace("xoffset " + xoffset);
         if (xoffset > inset) {
            diffx = xoffset - inset;
            xoffset = inset;
            // trace("xoffset " + xoffset);
            screenposx -= diffx;
         }
         // trace("doing redraw oldsaveop = " + saveop);
         redraw();
      }

      // if cursor off screen
      if (yChange != 0) {
         if ((screenposy < 0) || (screenposy >= screenSize))
            moveScreen(yChange);
      }
   }

   public int yCursorChanged(int newY) {

      // trace(" newY " + newY);

      int yChange = newY - getfileY();
      FoldModel fm = getActiveFoldModel();
      int visYChange;
      if (fm != null) {
         visYChange =
            visualLineDelta(getfileY(), newY, fm);
         screenposy += visYChange;
      } else {
         visYChange = yChange;
         screenposy += yChange;
      }

      // trace("cursorchanged " + yChange + " screenSaveX " + saveScreenX);
      String oline = gettext().at(newY).toString();
      String nline = oline;

      int nXchange;
      int charoff;
      if (0 != tabStop) {
         int tabOffset = nline.indexOf('\t');
         if (tabOffset != -1) {
            int[] tvals = new int[1];
            nline = DeTabber.deTab(nline, tabOffset, tabStop, tvals);
            charoff = charOffset(nline, saveScreenX);
            int xTabOff = DeTabber.tabFind(oline, tabOffset, tabStop, charoff);
            // trace("xTabOff = " + xTabOff + " inx = " + inx + " charoff = " + charoff);
            tvals[0] = xTabOff;
            DeTabber.deTab(oline, tabOffset, tabStop, tvals);
            charoff = tvals[0];
            nXchange = xTabOff - getfileX();
            // trace("nXchange = " + nXchange);
         } else {
            charoff = charOffset(nline, saveScreenX);
            nXchange = charoff - getfileX();
            // trace("nXchange = " + nXchange);
         }
      } else {
         charoff = charOffset(oline, saveScreenX);
         nXchange = charoff - getfileX();
         // trace("nXchange = " + nXchange);
      }
      int newx = 0 == charoff
            ? 0
            : measureWidth(nline.substring(0, charoff));

      // trace("nXchange = " + nXchange + " charoff " + charoff + " newx " + newx);
      setFilePos(nXchange + getfileX(), newY);
      fixcursor(nXchange, visYChange, newx);
      return getfileX();
   }

   public void cursorChanged(int newX, int newY) {

      // trace(" newX " + newX + " newY " + newY);
      int yChange = newY - getfileY();
      FoldModel fm = getActiveFoldModel();
      int visYChange;
      if (fm != null) {
         visYChange =
            visualLineDelta(getfileY(), newY, fm);
         screenposy += visYChange;
      } else {
         visYChange = yChange;
         screenposy += yChange;
      }

      int newx = 0;

      if (0 != newX) {
         int charoff = newX;
         String oline = gettext().at(newY).toString();
         String nline = oline;

         if (0 != tabStop) {
            int tabOffset = oline.indexOf('\t');
            if (tabOffset != -1) {
               int[] tvals = new int[1];
               tvals[0] = charoff;
               nline = DeTabber.deTab(nline, tabOffset, tabStop, tvals);
               charoff = tvals[0];
            }
         }
         if (0 != charoff)
            newx = measureWidth(nline.substring(0, charoff));
      }

      saveScreenX = newx;
      setFilePos(newX, newY);
      // trace(" saveScreenX changed " + saveScreenX);

      fixcursor(0, visYChange, newx);
   }

   public void refresh(Graphics gr) {
      // trace("refresh " + this);
      // trace("cliprect = " + gr.getClipBounds() + " my cliprect = " + cliprect);
      gr.setClip(null);
      gr.setColor(AtView.interFrame);
      gr.fillRect(0, 0, inset, screenSize * charheight);
      gr.fillRect(pixelWidth - inset, 0, inset, screenSize * charheight);
      gr.setClip(cliprect);
      canvas.paintLines(gr, 0, screenSize);
      // trace(" done REDRAW " + this);
   }

   private void copyLines(Graphics gr, int start, int end, int delta) {
      // trace("copyLines");
      if (start < 0 || end > screenSize || start >= end
            || start + delta < 0 || end + delta > screenSize)
         throw new RuntimeException("start = " + start + " end = "
               + end + " delta = " + delta);

      // try {
      gr.copyArea(0, start * charheight, pixelWidth,
            (end - start) * charheight, 0, delta * charheight);
      // } catch (sun.java2d.InvalidPipeException e) {
      // trace("caught exception + " + e);
      // e.printStackTrace();
      // }
   }

   private int fillheader(Graphics gr, int start) {
      // trace("fillheader " + screenFirstLine());
      if (start + screenFirstLine() < 1) {
         start = 1 - screenFirstLine();
         gr.setColor(AtView.noFile);
         gr.fillRect(0, 0, pixelWidth, start * charheight);
      }
      return start;
   }

   private int filltrailer(Graphics gr, int end) {
      // trace("filltrailer end " + end);
      // trace("end = " + end + " firstline = " + screenFirstLine());
      int numlines = gettext().readIn(); // number of lines read in
      // trace("end = " + end + " firstline = " + screenFirstLine()+ " numlines " +
      // numlines);
      if (end + screenFirstLine() > numlines) {
         end = numlines - screenFirstLine();
         if (end != screenSize) {
            if (!gettext().donereading()) {
               gr.setColor(AtView.unFinished);
               needMoreText(end + screenFirstLine());
            } else
               gr.setColor(AtView.noFile);
            gr.fillRect(0, end * charheight,
                  pixelWidth, (screenSize - end) * charheight);
         }
      }
      return end;
   }

   public int screenFirstLine() {
      // trace( "sfl " + fileposy + " screenposy " + screenposy);
      return getfileY() - screenposy;
   }

   /**
    * Compute the buffer line at the top of the screen when
    * folds are active. Walks backward from the cursor by
    * screenposy visible lines.
    */
   private int computeTopBufLine(FoldModel fm) {
      int line = getfileY();
      int rows = screenposy;
      while (rows > 0 && line > 1) {
         line = fm.prevVisible(line);
         if (line < 1)
            break;
         rows--;
      }
      return Math.max(1, line);
   }

   /**
    * Count visible lines between two buffer positions using
    * fold-aware iteration. Returns positive when toLine is
    * below fromLine, negative when above.
    */
   private static int visualLineDelta(
         int fromLine, int toLine, FoldModel fm) {
      if (fromLine == toLine)
         return 0;
      int delta = 0;
      if (toLine > fromLine) {
         int line = fromLine;
         while (line < toLine) {
            line = fm.nextVisible(line);
            delta++;
            if (line >= toLine)
               break;
         }
      } else {
         int line = fromLine;
         while (line > toLine) {
            line = fm.prevVisible(line);
            delta--;
            if (line <= toLine)
               break;
         }
      }
      return delta;
   }

   /**
    * Recompute screenposy based on current fold state.
    * Counts visible lines from line 1 to current fileY.
    */
   public void recalcScreenRow() {
      FoldModel fm = getActiveFoldModel();
      if (fm == null)
         return;
      int target = getfileY();
      int visLine = 0;
      int line = 1;
      while (line < target) {
         line = fm.nextVisible(line);
         visLine++;
         if (line >= target)
            break;
      }
      screenposy = Math.min(visLine, screenSize / 2);
   }

   /** Get the fold model for the current view context. */
   private FoldModel getActiveFoldModel() {
      FvContext<?> fvc =
         FvContext.findContext(OldView.this, gettext());
      if (fvc == null)
         return null;
      FoldModel fm = fvc.getFoldModel();
      if (fm == null || fm.isEmpty())
         return null;
      return fm;
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
      } else {
         cstart = 0;
         cend = screenSize + amount;
         pstart = 0;
         pend = -amount;
      }
      copyLines(gr, cstart, cend, -amount);
      canvas.paintLines(gr, pstart, pend);
   }

   Shape updateCursorShape(Shape sh) {
      int cx = screenposx;
      if (getActiveFoldModel() != null)
         cx += charwidth;
      String iString = getInsertString();
      if (null != iString) {
         int tabOffset = iString.indexOf('\t');
         if (-1 != tabOffset)
            iString = DeTabber.deTab(iString, tabOffset, tabStop, new int[1]);
         // trace("stringWidth " + fontm.stringWidth(iString) + " iString:" + iString);
         cx += measureWidth(iString);
      }
      int rx = cx - 1;
      int ry = (screenposy) * (charheight) - 1;
      int rwidth = boldflag ? 2 : 1;
      int rheight = charheight + 1;

      if (sh instanceof Rectangle) {
         Rectangle rec = (Rectangle) sh;
         if (rec.x == rx && rec.y == ry
               && rec.height == rheight
               && rec.width == rwidth)
            return sh;
      }
      // trace("updateCursorShape returning " + new Rectangle(rx, ry, rwidth,
      // rheight));
      return new Rectangle(rx, ry, rwidth, rheight);
   }

   /**
    * Convert a pixel x-position to a character offset in the
    * line. Uses BreakIterator to iterate by grapheme clusters
    * so the result always lands on a cluster boundary (never
    * in the middle of a surrogate pair or ZWJ sequence).
    */
   int charOffset(String line, int xpos) {
      java.text.BreakIterator bi =
         java.text.BreakIterator.getCharacterInstance();
      bi.setText(line);
      int charguess = xpos / charwidth;
      if (charguess > line.length())
         charguess = line.length();
      // Snap initial guess to grapheme cluster boundary
      if (charguess > 0 && charguess < line.length()
            && !bi.isBoundary(charguess)) {
         int snap = bi.preceding(charguess);
         if (snap != java.text.BreakIterator.DONE)
            charguess = snap;
      }
      int xguess = measureWidth(
         line.substring(0, charguess));
      int lastxguess = xguess;
      if (xpos < xguess) {
         int rightPos = charguess;
         while (xpos < xguess) {
            if (charguess <= 0)
               break;
            rightPos = charguess;
            lastxguess = xguess;
            int prev = bi.preceding(charguess);
            if (prev == java.text.BreakIterator.DONE)
               break;
            charguess = prev;
            xguess = measureWidth(
               line.substring(0, charguess));
         }
         return (xpos - xguess) <= (lastxguess - xpos)
               ? charguess
               : rightPos;
      } else {
         int leftPos = charguess;
         while (xpos > xguess) {
            if (charguess >= line.length())
               break;
            leftPos = charguess;
            lastxguess = xguess;
            int next = bi.following(charguess);
            if (next == java.text.BreakIterator.DONE)
               break;
            charguess = next;
            xguess = measureWidth(
               line.substring(0, charguess));
         }
         return (xguess - xpos) <= (xpos - lastxguess)
               ? charguess
               : leftPos;
      }
   }

   /**
    * Check if a string contains any surrogate pairs (emoji, supplementary).
    */
   private static boolean hasSurrogates(String s) {
      for (int i = 0; i < s.length(); i++) {
         if (Character.isHighSurrogate(s.charAt(i)))
            return true;
      }
      return false;
   }

   /**
    * Measure the advance width of a string. Uses TextLayout when
    * surrogate pairs are present so that font substitution (emoji
    * fallback fonts) is accounted for. Falls back to the faster
    * FontMetrics.stringWidth() for plain ASCII/BMP-only text.
    */
   private int measureWidth(String s) {
      if (s.isEmpty())
         return 0;
      if (!hasSurrogates(s))
         return fontm.stringWidth(s);
      FontRenderContext frc =
         new FontRenderContext(null, true, true);
      TextLayout tl = new TextLayout(s, activeFont, frc);
      return Math.round(tl.getAdvance());
   }

   private int tcharOffset(String line, int xpos) {
      if (0 != tabStop) {
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
      int screenRow = event.getY() / charheight;
      int ypos;
      FoldModel fm = getActiveFoldModel();
      if (fm != null) {
         int topBuf = computeTopBufLine(fm);
         int tindex = topBuf;
         int numlines = gettext().readIn();
         for (int row = 0; row < screenRow
               && tindex < numlines; row++)
            tindex = fm.nextVisible(tindex);
         ypos = Math.min(tindex, numlines - 1);
         if (ypos < 1)
            ypos = 1;
      } else {
         ypos = screenRow - screenposy + getfileY();
         if (ypos < 1)
            ypos = 1;
         else if (!gettext().containsNow(ypos))
            ypos = gettext().readIn() - 1;
      }
      // figure out where in the line x is
      String line = gettext().at(ypos).toString();
      // trace("xoffset " + xoffset + " getX " + event.getX());
      int xpos = event.getX() - xoffset;
      if (fm != null)
         xpos -= charwidth;
      if (xpos <= 0)
         xpos = 0;
      return new Position(tcharOffset(line, xpos), ypos,
            gettext().fdes(), "mouse pos");
   }

   // returns amount cursor needs to be adjusted
   public int screeny(int amount) {
      // trace("screeny " + amount);
      FoldModel fm = getActiveFoldModel();
      if (fm != null)
         return screenyFolded(amount, fm);

      // move the screen and if necessary the cursor
      if (screenFirstLine() + amount <= -screenSize + 1)
         if (screenFirstLine() <= 1)
            amount = 0;
         else
            amount = -screenFirstLine();
      else {
         if (!gettext().containsNow(screenFirstLine() + amount))
            if (gettext().containsNow(screenFirstLine() + screenSize))
               amount = gettext().readIn() - 1 - screenFirstLine();
            else
               amount = 0;
      }

      if (0 != amount) {
         moveScreen(amount);
         if ((screenposy >= screenSize)
               || (screenposy < 0))
            return amount;
      }
      return 0;
   }

   /**
    * Fold-aware scrolling. Walks visible lines instead of
    * raw buffer lines for boundary checks and scroll amount.
    */
   private int screenyFolded(int amount, FoldModel fm) {
      int topBuf = computeTopBufLine(fm);
      int numlines = gettext().readIn();
      int newTop = topBuf;

      if (amount > 0) {
         for (int i = 0; i < amount; i++) {
            int next = fm.nextVisible(newTop);
            if (next >= numlines)
               break;
            newTop = next;
         }
      } else {
         for (int i = 0; i > amount; i--) {
            int prev = fm.prevVisible(newTop);
            if (prev < 1)
               break;
            newTop = prev;
         }
      }

      if (newTop == topBuf)
         return 0;

      int visAmount =
         visualLineDelta(topBuf, newTop, fm);
      moveScreen(visAmount);
      if (screenposy >= screenSize || screenposy < 0)
         return visAmount;
      return 0;
   }

   public void setSizebyChar(int xchar, int ychar) {
      // trace("setSizebyChar xchar = " + xchar + " ychar = " + ychar);
      if (xchar < 0)
         xchar = minColumns;
      if (ychar < 0)
         ychar = screenSize;
      canvas.setSize(xchar * charwidth, ychar * charheight);
      // UI.resize();
      // invalidate();//???
      // trace("pixelwidth = " + pixelWidth + " charwidth = " + charwidth + "
      // screenSize " + screenSize);
   }

   protected void startInsertion(javi.View.Inserter ins) {
      canvas.addInputMethodListener((InHandler) ins);
      canvas.enableInputMethods(true);
   }

   protected void endInsertion(javi.View.Inserter ins) {
      canvas.enableInputMethods(false);
      canvas.removeInputMethodListener((InHandler) ins);
   }

   final class MyCanvas extends Canvas {

      private int mousePressed = 0;
      /** True when mouse-down occurred in the fold gutter column. */
      private boolean foldGutterPressed;
      /** True when a fold was toggled by clicking a collapsed summary. */
      private boolean foldClickToggled;
      /** Start position of a shell-mode mouse selection, or null. */
      private Position shellDragStart;
      private transient Graphics oldgr;
      private transient Image dbuf;
      private transient Graphics2D imageg;

      private void common() {
         /*
          * HashSet<AWTKeyStroke> keyset =
          * new HashSet<AWTKeyStroke>(getFocusTraversalKeys(
          * KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
          * );
          *
          * for (Iterator it = keyset.iterator();it.hasNext();) {
          * AWTKeyStroke key = (AWTKeyStroke)(it.next());
          * if (key.getKeyCode()== KeyEvent.VK_TAB
          * && key.getModifiers() == 0)
          * it.remove();
          * }
          * setFocusTraversalKeys(KeyboardFocusManager.
          * FORWARD_TRAVERSAL_KEYS, keyset);
          *
          * enableInputMethods(false);
          */
         enableEvents(AWTEvent.MOUSE_EVENT_MASK
               | AWTEvent.MOUSE_MOTION_EVENT_MASK
               | AWTEvent.MOUSE_WHEEL_EVENT_MASK);
      }

      private void readObject(
            java.io.ObjectInputStream is)
            throws ClassNotFoundException,
            java.io.IOException {

         is.defaultReadObject();
         setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
         common();
      }

      MyCanvas() {
         common();
      }

      public Dimension getPreferredSize() {
         // trace("getPreferredSize screensize " + screenSize + " charheight " +
         // charheight + " pixelWidth " + pixelWidth);
         // trace("screen y = " + screenSize * charheight);
         return new Dimension(pixelWidth, screenSize * charheight);
      }

      public void setSize(int newx, int newy) {
         // trace("setSize entered (" + newx + "," + newy + ")" + this);
         // if (y == 0){
         // Thread.dumpStack();
         // return;
         // }

         screenSize = newy / charheight;

         minColumns = (newx - 2 * inset) / charwidth;
         newy = screenSize * charheight;
         pixelWidth = minColumns * charwidth + 2 * inset;
         cliprect.y = 0;
         cliprect.width = pixelWidth - 2 * inset + 1;
         cliprect.height = newy;
         if (screenposy >= screenSize)
            moveScreen(screenposy - screenSize + 1);
         super.setSize(pixelWidth, newy);
         ShellManager.getInstance().notifyResize(screenSize, minColumns);
         // imageg = null;
         // trace("oldview = " + this + " cliprect = " + cliprect);
         // trace("pixelwidth = " + pixelWidth + " charwidth = " + charwidth + "
         // screenSize " + screenSize);
      }

      @SuppressWarnings({ "fallthrough", "deprecation" })

      private void mousepress(MouseEvent event) {
         // trace("modifiers = " +Integer.toHexString( event.getModifiers()));

         if (event.getButton() == MouseEvent.BUTTON1
               && event.getX() < xoffset + charwidth) {
            FoldModel fm = getActiveFoldModel();
            if (fm != null && !fm.isEmpty()) {
               foldGutterPressed = true;
               return;
            }
         }

         EventQueue.biglock2.lock();

         // trace("Position " + pos + " event vi " + vi);
         FvContext newfvc;
         Position pos;
         try {
            pos = mousepos(event);
            newfvc = FvContext.getcontext(OldView.this, getCurrFile());
         } finally {
            EventQueue.biglock2.unlock();
         }

         // trace("fvc " + fvc + " newfvc " + newfvc);
         switch (event.getButton()) {
            case MouseEvent.BUTTON1:
               // Click on collapsed fold summary toggles it
               FoldModel clickFm = getActiveFoldModel();
               if (clickFm != null) {
                  FoldModel.FoldRange clickFold =
                     clickFm.findFoldAtStart(pos.y);
                  if (clickFold != null
                        && clickFold.collapsed) {
                     EventQueue.biglock2.lock();
                     try {
                        FoldModel.FoldToggleHandler cth =
                           clickFm.getToggleHandler();
                        if (cth != null) {
                           try {
                              if (cth.onToggle(
                                    pos.y, newfvc)) {
                                 foldClickToggled = true;
                                 return;
                              }
                           } catch (Exception e) {
                              // fall through
                           }
                        }
                        clickFm.toggleFold(pos.y);
                        recalcScreenRow();
                        MiscCommands.saveFoldState(newfvc);
                        newfvc.vi.redraw();
                     } finally {
                        EventQueue.biglock2.unlock();
                     }
                     foldClickToggled = true;
                     return;
                  }
               }
               EventQueue.insert(new PosEvent(newfvc, pos));
               break;

            case MouseEvent.BUTTON2:
               if (newfvc.edvec.containsNow(pos.y)) {
                  Object line = newfvc.edvec.at(pos.y);
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

      void mouserelease(MouseEvent event) {

         EventQueue.biglock2.lock();
         try {

            // trace(" clickcount " + event.getClickCount() + " has focus" +
            // fvc.vi.hasFocus());
            FvContext fvc = FvContext.getCurrFvc();
            // trace("Position " + pos + " event vi " + vi);
            if (fvc != FvContext.getcontext(OldView.this, getCurrFile()))
               return;

            if (event.getButton() == MouseEvent.BUTTON1) {
               // trace("setting markmode ");
               // fvc.cursorabs(pos);
               Position pos = mousepos(event);
               if (fvc.inserty() != pos.y || fvc.insertx() != pos.x) {
                  EventQueue.insert(new MarkEvent(pos));
               }
            }
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      public void processEvent(AWTEvent ev) {
         // trace("ev " + ev.getID() + " has focus " + hasFocus());
         switch (ev.getID()) {
            case MouseEvent.MOUSE_PRESSED:
               if (forwardVt100Mouse((MouseEvent) ev, true))
                  return;
               foldGutterPressed = false;
               foldClickToggled = false;
               if (((MouseEvent) ev).getButton() == MouseEvent.BUTTON1
                     && isShellBufferNoTracking()) {
                  EventQueue.biglock2.lock();
                  try {
                     shellDragStart = mousepos((MouseEvent) ev);
                  } finally {
                     EventQueue.biglock2.unlock();
                  }
               } else {
                  shellDragStart = null;
               }
               mousepress((MouseEvent) ev);
               mousePressed = ((MouseEvent) ev).getButton();
               break;

            case MouseEvent.MOUSE_RELEASED:
               if (forwardVt100Mouse((MouseEvent) ev, false))
                  return;
               if (foldClickToggled) {
                  foldClickToggled = false;
               } else if (foldGutterPressed
                     && ((MouseEvent) ev).getButton()
                        == MouseEvent.BUTTON1) {
                  foldGutterPressed = false;
                  if (((MouseEvent) ev).getX()
                        < xoffset + charwidth) {
                     handleFoldGutterClick((MouseEvent) ev);
                  }
               } else if (null != shellDragStart
                     && ((MouseEvent) ev).getButton()
                        == MouseEvent.BUTTON1) {
                  copyShellSelection((MouseEvent) ev);
               } else {
                  mouserelease((MouseEvent) ev);
               }
               mousePressed = 0;
               break;

            case MouseEvent.MOUSE_WHEEL:
               if (forwardVt100Wheel((MouseWheelEvent) ev))
                  return;
               MouseWheelEvent mwv = (MouseWheelEvent) ev;
               int mvAmt = mwv.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL
                     ? getRows(1.f)
                     : mwv.isControlDown()
                           ? getRows(1.f)
                           : mwv.getScrollAmount();
               EventQueue.insert(new ScrollEvent(mvAmt
                     * mwv.getWheelRotation(), mwv.isShiftDown()));
               return;

            case MouseEvent.MOUSE_DRAGGED:
               if (forwardVt100MouseDrag((MouseEvent) ev))
                  return;
               if (foldGutterPressed) {
                  // Mouse moved off fold column — cancel fold
                  if (((MouseEvent) ev).getX()
                        >= xoffset + charwidth) {
                     foldGutterPressed = false;
                  }
                  break;
               }
               if (1 == mousePressed) {
                  EventQueue.biglock2.lock();
                  try {
                     updateTempMarkPos(mousepos((MouseEvent) ev));
                  } finally {
                     EventQueue.biglock2.unlock();
                  }
               }
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

      /**
       * Maps AWT button number to VT100 button code.
       */
      private int vt100Button(MouseEvent event) {
         return switch (event.getButton()) {
            case MouseEvent.BUTTON1 -> 0;
            case MouseEvent.BUTTON2 -> 1;
            case MouseEvent.BUTTON3 -> 2;
            default -> 0;
         };
      }

      /**
       * Converts pixel coordinates to 1-based terminal cell coordinates.
       */
      private int cellCol(MouseEvent event) {
         int x = (event.getX() - xoffset) / charwidth + 1;
         return x < 1 ? 1 : x;
      }

      private int cellRow(MouseEvent event) {
         int y = event.getY() / charheight + 1;
         return y < 1 ? 1 : y;
      }

      /**
       * Checks if the current buffer is a shell buffer without mouse
       * tracking — i.e. the terminal app (bash) has not enabled
       * mouse reporting modes 1000/1002/1003.
       */
      private boolean isShellBufferNoTracking() {
         ShellManager sm = ShellManager.getInstance();
         javi.TextEdit<?> file = getCurrFile();
         return null != sm && null != file
            && sm.isShellBuffer(file)
            && !sm.isMouseTrackingForBuffer(file);
      }

      /**
       * Copies the mouse-selected text from a shell buffer to the
       * system clipboard and clears the visual mark.
       */
      private void copyShellSelection(MouseEvent event) {
         EventQueue.biglock2.lock();
         try {
            Position end = mousepos(event);
            Position start = shellDragStart;
            shellDragStart = null;
            if (start.y == end.y && start.x == end.x)
               return;
            javi.TextEdit<?> buf = gettext();
            if (null == buf)
               return;
            String text = ShellManager.extractBufferText(
               buf, start, end);
            if (text.isEmpty())
               return;
            java.awt.datatransfer.Clipboard clip =
               java.awt.Toolkit.getDefaultToolkit()
                  .getSystemClipboard();
            clip.setContents(
               new java.awt.datatransfer.StringSelection(text),
               null);
            trace("shell selection copied: "
               + text.length() + " chars");
         } catch (Exception e) {
            trace("copyShellSelection failed: " + e);
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      /**
       * Handle a click in the fold gutter area. Determines the
       * buffer line for the clicked screen row and toggles the
       * fold if one starts at that line.
       *
       * @return true if a fold was toggled
       */
      private boolean handleFoldGutterClick(MouseEvent event) {
         EventQueue.biglock2.lock();
         try {
            FoldModel fm = getActiveFoldModel();
            if (fm == null)
               return false;
            int screenRow = event.getY() / charheight;
            int topBuf = computeTopBufLine(fm);
            int numlines = gettext().readIn();
            int tindex = topBuf;
            for (int row = 0; row < screenRow
                  && tindex < numlines; row++)
               tindex = fm.nextVisible(tindex);
            if (tindex < 1 || tindex >= numlines)
               return false;
            char ind = fm.getFoldIndicator(tindex);
            if (ind == '+' || ind == '-') {
               FoldModel.FoldToggleHandler fgh =
                  fm.getToggleHandler();
               FvContext fvc = FvContext.getcontext(
                  OldView.this, getCurrFile());
               if (fgh != null && fvc != null) {
                  try {
                     if (fgh.onToggle(tindex, fvc))
                        return true;
                  } catch (Exception e) {
                     // fall through to default toggle
                  }
               }
               fm.toggleFold(tindex);
               if (fvc != null) {
                  recalcScreenRow();
                  MiscCommands.saveFoldState(fvc);
                  fvc.vi.redraw();
               }
               return true;
            }
            return false;
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      /**
       * Forwards a mouse press/release to VT100 if tracking is active.
       *
       * @return true if the event was consumed
       */
      private boolean forwardVt100Mouse(MouseEvent event, boolean pressed) {
         ShellManager sm = ShellManager.getInstance();
         javi.TextEdit<?> file = getCurrFile();
         if (null == sm || null == file
               || !sm.isMouseTrackingForBuffer(file))
            return false;
         sm.forwardMouseEventToBuffer(file,
            vt100Button(event), cellCol(event), cellRow(event), pressed);
         return true;
      }

      /**
       * Forwards a mouse wheel event to VT100 if tracking is active.
       *
       * @return true if the event was consumed
       */
      private boolean forwardVt100Wheel(MouseWheelEvent event) {
         ShellManager sm = ShellManager.getInstance();
         javi.TextEdit<?> file = getCurrFile();
         if (null == sm || null == file
               || !sm.isMouseTrackingForBuffer(file))
            return false;
         int button = event.getWheelRotation() < 0 ? 64 : 65;
         sm.forwardMouseEventToBuffer(file,
            button, cellCol(event), cellRow(event), true);
         return true;
      }

      /**
       * Forwards a mouse drag event to VT100 if button-event or any-event
       * tracking is active (modes 1002/1003).
       *
       * @return true if the event was consumed
       */
      private boolean forwardVt100MouseDrag(MouseEvent event) {
         ShellManager sm = ShellManager.getInstance();
         javi.TextEdit<?> file = getCurrFile();
         if (null == sm || null == file
               || !sm.isMouseTrackingForBuffer(file))
            return false;
         // Only mode 1002 (button-event) and 1003 (any-event) track motion
         int button = 32 + vt100Button(event); // motion flag = +32
         sm.forwardMouseEventToBuffer(file,
            button, cellCol(event), cellRow(event), true);
         return true;
      }

      public void paint(Graphics g) {
         // trace("paint called ");
         try {
            redraw();
            npaint((Graphics2D) g);
         } catch (Throwable e) {
            UI.popError("unexpected exception", e);
         }
      }

      public void update(Graphics g) {
         try {
            // trace("update called ");
            // if (op.currop == REDRAW) trace(" got update REDRAW!!");
            npaint((Graphics2D) g);
         } catch (Throwable e) {
            UI.popError("unexpected exception", e);
         }
      }

      private void npaint(Graphics2D gr) throws InterruptedException {
         // trace("npaint");
         if (null == gettext())
            return;

         if ((imageg == null) || (gr != oldgr)) {
            dbuf = canvas.createImage(pixelWidth * 2, charheight);
            imageg = (Graphics2D) dbuf.getGraphics();
            // RenderingHints qualityHints = new RenderingHints(
            // RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // qualityHints.put(RenderingHints.KEY_ANTIALIASING,
            // RenderingHints.VALUE_ANTIALIAS_DEFAULT);
            // RenderingHints.VALUE_ANTIALIAS_OFF);
            // qualityHints.put(RenderingHints.KEY_TEXT_ANTIALIASING,
            // RenderingHints.VALUE__TEXT_ANTIALIAS_DEFAULT);
            // RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            // qualityHints.put(RenderingHints.KEY_ANTIALIASING,
            // RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            // qualityHints.put(RenderingHints.KEY_FRACTIONALMETRICS,
            // RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            // qualityHints.put(RenderingHints.KEY_FRACTIONALMETRICS,
            // RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            // imageg.setRenderingHints(qualityHints);

            // trace("imageg " + imageg);
            if (null == imageg)
               throw new RuntimeException("imageg null!!");
            oldgr = gr;
         }

         if (!EventQueue.biglock2.tryLock(1, TimeUnit.MILLISECONDS)) {
            // trace("repaint because failed lock " + gettext() + " or lock");
            repaint(200);
         } else
            try {
               if (gettext().isValid()) {
                  applyOverrideFont();
                  applyChanges();
                  copt.rpaint(gr);
               } else {
                  trace("repaint because of invalid or empty");
                  repaint(200);
               }
            } catch (Throwable e) {
               // Log error but don't pop dialog - this is called frequently during paint
               trace("npaint caught: "
                  + e.getClass().getSimpleName()
                  + ": " + e.getMessage());
            } finally {
               EventQueue.biglock2.unlock();
            }
      }

      public void setFont(Font font) {

         // trace("setting View font " + font + " " + this);
         ssetFont(font);
      }

      void paintLines(Graphics gr, int start, int end) {
         assert start >= 0 && end <= screenSize && start < end;
         if (start < 0 || end > screenSize || start >= end)
            throw new RuntimeException("start = " + start
               + " end = " + end
               + " screenSize = " + screenSize);

         FoldModel fm = getActiveFoldModel();
         if (fm != null) {
            paintLinesFolded(gr, start, end, fm);
            return;
         }

         start = fillheader(gr, start);
         end = filltrailer(gr, end);

         for (int index = start,
               tindex = index + screenFirstLine();
               index < end; index++, tindex++) {
            paintOneLine(gr, index, tindex);
         }
      }

      /**
       * Render a single buffer line at the given screen row.
       * Handles cursor emphasis, marks, detab, and blit.
       */
      private void paintOneLine(
            Graphics gr, int index, int tindex) {
         imageg.setColor(AtView.background);
         imageg.fillRect(0, 0, pixelWidth, charheight);
         atIt.setText(gettext().at(tindex).toString());

         ScreenAttributes scrAttrs =
            gettext().getTerminalAttributes();
         if (scrAttrs != null)
            atIt.setTerminalAttrs(
               scrAttrs.getRow(tindex));

         boolean isDiffBuffer = null != gettext()
            && "*git-diff*".equals(
               gettext().fdes().getShortName());
         if (isDiffBuffer) {
            String lt = atIt.getText();
            if (!lt.isEmpty()) {
               char c0 = lt.charAt(0);
               if (c0 == '-' && !lt.startsWith("---"))
                  atIt.setLineForeground(Color.red);
               else if (c0 == '+' && !lt.startsWith("+++"))
                  atIt.setLineForeground(Color.green);
               else if (c0 == '@')
                  atIt.setLineForeground(Color.cyan);
            }
         }

         if (index == screenposy) {
            atIt.emphasize(true);
            String iString = getInsertString();
            if (null != iString)
               atIt.addOlineText(
                  iString, getfileX(), isOverwrite());
         }

         if (0 != atIt.length()) {
            MarkInfo mpmark = getPmark();
            if (null != mpmark.getMark())
               atIt.setHighlight(
                  mpmark.starth(tindex),
                  mpmark.endh(tindex));
            if (0 != tabStop)
               atIt.deTab(tabStop);
            imageg.drawString(atIt, xoffset, charascent);
         }
         gr.drawImage(dbuf, 0, index * charheight, null);
      }

      /**
       * Fold-aware line painting. Computes the buffer line
       * at screen top using fold model, then iterates visible
       * lines, rendering collapsed folds as summary lines.
       * Shifts text right to make room for fold gutter.
       */
      private void paintLinesFolded(
            Graphics gr, int start, int end,
            FoldModel fm) {
         int numlines = gettext().readIn();
         int topBuf = computeTopBufLine(fm);

         // Widen xoffset to make room for fold gutter column
         int savedXoffset = xoffset;
         xoffset = inset + charwidth;

         // Advance from topBuf to the buffer line at row start
         int tindex = topBuf;
         for (int skip = 0; skip < start
               && tindex < numlines; skip++)
            tindex = fm.nextVisible(tindex);

         for (int index = start; index < end; index++) {
            if (tindex >= numlines) {
               // Past end of file
               if (!gettext().donereading()) {
                  gr.setColor(AtView.unFinished);
                  needMoreText(tindex);
               } else {
                  gr.setColor(AtView.noFile);
               }
               gr.fillRect(0, index * charheight,
                  pixelWidth,
                  (end - index) * charheight);
               break;
            }
            int displayedBufLine = tindex;
            FoldModel.FoldRange fold =
               fm.findFoldAtStart(tindex);
            if (fold != null && fold.collapsed) {
               paintFoldSummary(gr, index, fold);
               tindex = fold.endLine + 1;
            } else {
               paintOneLine(gr, index, tindex);
               tindex++;
            }
            paintFoldIndicator(
               gr, index, displayedBufLine, fm);
         }
         xoffset = savedXoffset;
      }

      /**
       * Draw a fold gutter indicator character at the left
       * edge of the given screen row. The indicator is drawn
       * in the fold gutter column (between inset and text).
       */
      private void paintFoldIndicator(
            Graphics gr, int screenRow,
            int bufLine, FoldModel fm) {
         char ch = fm.getFoldIndicator(bufLine);
         if (ch == '\0')
            return;
         Shape savedClip = gr.getClip();
         gr.setClip(null);
         gr.setColor(AtView.foldGutter);
         int y = screenRow * charheight + charascent;
         gr.drawString(String.valueOf(ch), inset, y);
         gr.setClip(savedClip);
      }

      /**
       * Render a fold summary line at screen row index.
       * Shows "+--  N lines: first-line-text".
       */
      private void paintFoldSummary(
            Graphics gr, int index,
            FoldModel.FoldRange fold) {
         imageg.setColor(AtView.background);
         imageg.fillRect(0, 0, pixelWidth, charheight);
         String firstLine =
            gettext().at(fold.startLine).toString();
         String summary = FoldModel.foldSummaryText(
            fold.startLine, fold.endLine, firstLine);
         summary = summary.replace('\t', ' ');
         imageg.setFont(activeFont);
         imageg.setColor(index == screenposy
            ? AtView.cursorColor
            : AtView.foldSummaryColor);
         imageg.drawString(summary, xoffset, charascent);
         gr.drawImage(dbuf, 0, index * charheight, null);
      }

      public boolean isFocusable() {
         return false;
      }
   }

   final class Ch extends COpt {
      public void rpaint(Graphics2D gr) {
         Opcode currop = resetOp();
         if (currop != NOOP) {
            // if (currop != BLINKCURSOR) trace("rpaint currop = " + currop + " this " +
            // this);
            // trace("rpaint currop = " + currop + " this " + this);

            // cursor must be off before other drawing is done, or it messes up XOR
            if (currop == BLINKCURSOR || isCursorOn()) {
               bcursor(gr);
            }

            // When folds are active, optimized draw paths
            // assume linear screen↔buffer mapping.
            // Fall back to full redraw for correctness.
            if (currop != REDRAW
                  && currop != BLINKCURSOR
                  && currop != NOOP
                  && getActiveFoldModel() != null) {
               currop = REDRAW;
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
               default:
                  break;
            }
            if (currop != BLINKCURSOR)
               bcursor(gr); // always leave cursor on after doing something
         }
      }
   }

   private Ch copt;

   protected ChangeOpt getChangeOpt() {
      if (null == copt)
         copt = new Ch();
      return copt;
   }
}
