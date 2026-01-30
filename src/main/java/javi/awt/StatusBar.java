package javi.awt;

//import static history.Tools.trace;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javi.EventQueue;
import javi.FvContext;
import javi.UI;

final class StatusBar extends Canvas {
   private static final long serialVersionUID = 1;

   private int charheight;
   private int charascent;
   private int charwidth;
   private ArrayList<String> messeges = new ArrayList<>();
   private static final int hoffset = 4;
   private boolean sizeChanged;

   StatusBar() {
      setBackground(AtView.foreground);
   }

   void addline(String line) {
      //trace("adding " + line);
      synchronized (this) {
         messeges.add(line);
         sizeChanged = true;
         if (!isVisible())
            setVisible(true);
         repaint();
      }
   }

   private void setmet() {
      FontMetrics fontm = getFontMetrics(getFont());
      charheight = fontm.getHeight();
      charascent = fontm.getMaxAscent();
      charwidth = fontm.charWidth('a');
      //trace("fontm = " + fontm);
   }

   public synchronized Dimension getPreferredSize() {
      if (0 == charheight)
         setmet();

      if (0 == messeges.size())
         return new Dimension(getSize().width,  charheight);

      int col = getSize().width / charwidth;
      Dimension d;
      if (col < 1)
         d = new Dimension(getSize().width,
                           (messeges.size()) * charheight);
      else  {
         int over = 0;
         for (String str : messeges)
            over += str.length() / col;
         d = new Dimension(getSize().width,
            (1 + over + messeges.size()) * charheight);
      }
      return d;
   }

   void setline(String line) {
      //trace("setline = " + line);
      synchronized (this) {
         messeges.clear();
         addline(line);
         sizeChanged = true;
         if (!isVisible())
            setVisible(true);
         repaint();
      }
   }

   void clearlines() {
      //trace("clearlines");
      synchronized (this) {
         if (0 != messeges.size()) {
            messeges.clear();
            sizeChanged = true;
            repaint();
         }
      }
   }

   public boolean isFocusable() {
      return false;
   }

   public synchronized void setFont(Font f) {
      super.setFont(f);
      charheight = 0;
   }

   public synchronized void setVisible(boolean b) {
      if (b == isVisible())
         return;
      super.setVisible(b);
      if (!b)
         clearlines();
   }

   public void paint(Graphics g) {
      try {
         int statusVertical;
         synchronized (this) {
            if (sizeChanged) {
               if (!getPreferredSize().equals(getSize())) {
                  setSize(getPreferredSize());
                  getParent().validate();
               }
            }
            sizeChanged = false;
            g.setColor(AtView.background);
            int voffset = 0;
            int col = getSize().width / charwidth;

            if (col < 1)
               return;
            if (messeges.size() >= 1) {
               for (String line : messeges) {
                  for (int substr = 0; substr < line.length();) {
                     int newsubstr = (line.length()
                        - substr  < col
                           ? line.length()
                           : substr + col);

                     g.drawString(line.substring(substr, newsubstr),
                        hoffset, charheight * voffset++ + charascent);
                     substr = newsubstr;
                  }
               }
            }
            // calc while locked
            statusVertical = charheight * voffset + charascent;
         }

         String st = "status failure!!!";
         if (!EventQueue.biglock2.tryLock(1, TimeUnit.MILLISECONDS)) {
            //trace("status bar repaint because failed lock ");
            repaint(200);
         } else {
            try {
               st = FvContext.getCurrState();
            } finally {
               EventQueue.biglock2.unlock();
            }
            g.drawString(st, hoffset, statusVertical);
         }
      } catch (Throwable e) {
         UI.popError("StatusBar.paint caught exception", e);
      }
   }
   /*
   public void setSize(int x,int y) {
      super.setSize(x,y);
      trace("(" +x+ "," + y + ") " + this);
   }
   public void setLocation(int x,int y) {
      super.setLocation(x,y);
      trace("(" +x+ "," + y + ") " + this);
   }
   */
}
