package javi;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.ArrayList;


class StatusBar extends Canvas {
   private static final long serialVersionUID = 1;
   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";


   private int charheight;
   private int charascent;
   private int charwidth;
   private ArrayList<String> messeges = new ArrayList<String>();
   private static final int hoffset = 4;

   StatusBar() {
      super();
      setBackground(AtView.foreground);
   }

   void setUp() {
      repaint();
   }

   void addline(String line) {
      //trace("adding " + line);
      synchronized (this) {
         messeges.add(line);
      }
      if (isVisible()) {
         if (!getPreferredSize().equals(getSize())) {
            setSize(getPreferredSize());
         } else {
            repaint();
         }
      } else
         setVisible(true);
   }

   private void setmet() {
      FontMetrics fontm = getFontMetrics(getFont());
      charheight = fontm.getHeight();
      charascent = fontm.getMaxAscent();
      charwidth = fontm.charWidth('a');
      //trace("fontm = " + fontm);
   }

   public Dimension getPreferredSize() {
      if (charheight == 0)
         setmet();

      if (messeges.size() == 0)
         return new Dimension(getSize().width,  charheight);
      int col = getSize().width / charwidth;
      Dimension d;
      int over = 0;
      if (col < 1)
         d = new Dimension(getSize().width,
                           (messeges.size()) * charheight);
      else  {
         synchronized (this) {
            for (String str : messeges)
               over += str.length() / col;
            d = new Dimension(getSize().width,
               (1 + over + messeges.size()) * charheight);
         }
      }
      return d;
   }

   void setline(String line) {
      //trace("line = " + line);
      messeges.clear();
      addline(line);
   }

   boolean clearlines() {
      if (messeges.size() != 0) {
         messeges.clear();
         return true;
      }
      return false;
   }

   public boolean isFocusable() {
      return false;
   }

   public void setFont(Font f) {
      super.setFont(f);
      charheight = 0;
   }

   public void setVisible(boolean b) {
      if (b == isVisible())
         return;
      if (!b)
         clearlines();
      super.setVisible(b);

   }

   public void paint(Graphics g) {
      try {
         g.setColor(AtView.background);
         int voffset = 0;
         int col = getSize().width / charwidth;

         if (col < 1)
            return;
         synchronized (this) {
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
         }

         String st = "status failure!!!";
         EventQueue.biglock2.lock();
         try {
            st = FvContext.getCurrState();
         } finally {
            EventQueue.biglock2.unlock();
         }
         g.drawString(st, hoffset, charheight * voffset + charascent);
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
   static void trace(String str) {
      Tools.trace(str, 1);
   }
}
