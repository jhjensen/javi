package javi;

import java.awt.font.LineBreakMeasurer;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.Point;
import java.awt.font.TextLayout;
import java.awt.font.TextHitInfo;
import java.awt.Shape;
import java.awt.Image;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.lang.ref.WeakReference;
import history.intArray;

class TabbedTextLayout extends view {
   private float tabStops[];
   private static final float[] defaultinfo = {0,0};
   private ArrayList ttvec = new ArrayList(100);
   private static final Color darkpink = new Color(75,15,15); //(85,25,25);(50,10,10);
   private int screenposy;
   private int firstline;
   private int lastline;
   private int minColumns=80;
   private int screenSize=24;
   Graphics oldgr;

   position mousepos(MouseEvent event) {
     trace("mousepos " + event);
     int yPos = event.getY();
     int yindex = fcontext.inserty();
     int yTemp = screenposy;
     if (yPos<yTemp)
        while(--yindex>=0) {
           trace("yindex = " + yindex + " yTemp = " + yTemp + "height = " + gettpl(yindex).getHeight());
           if (yTemp-yPos<gettpl(yindex).getHeight())
              break;
            else
               yTemp -= gettpl(yindex).getHeight();
         }
     else {
        int maxline = fcontext.edvec.readIn();
        for (;yindex<maxline;yindex++) {
           //trace("yindex = " + yindex + " yTemp = " + yTemp + "height = " + gettpl(yindex).getHeight());
           //trace("yPos = = " + yPos );
           if (gettpl(yindex).getHeight()>=yPos-yTemp) {
              yTemp=yPos-yTemp;
              break;
            } else
               yTemp += gettpl(yindex).getHeight();
        }
        if (yindex==maxline) {
          trace("max pos");
          String s = fcontext.edvec.at(maxline-1).toString();
          position p = new position(s.length(),maxline-1,null,null);
          trace("p = " + p);
          return p;
        }
     }
           
     trace("yindex = " + yindex + " yTemp = " + yTemp );
     int xindex = gettpl(yindex).getOffset(new Point(event.getX(),yTemp));
     return new position(xindex,yindex,null,null);
   }

   int getRows(float amount) throws inputexception {
      int tgt = Math.abs((int)(getHeight()*amount));
      int pos = 0;
      trace("getRows " + amount);
      for (int line = firstline;line<=lastline;line++) {
         pos+= gettpl(line).getHeight();
trace("pos = " + pos);
         if (pos>=tgt) {
            trace("returning " + (line-firstline));
            return amount<0
               ? firstline-line
               : line-firstline;
         }
      }
      throw new inputexception("getting illegal amount =" + amount);
   }
   public Dimension getPreferredSize() {
      Dimension d = new Dimension(minColumns*charwidth,screenSize*charheight);
      trace("" + d);
      return d;
   }
   void setSizebyChar(int x, int y) {
      trace("x = " + x + " y = " + y);
      if (x>0)
         minColumns=x;
      if (y>0)
         screenSize = y;
      invalidate();
   }

   int screenFirstLine(){
      return firstline;
   } 
   void screeny(int amount){

      screenposy-= amount * gettpl(fcontext.inserty()).getHeight();

      int lastline = fcontext.edvec.readIn() -1;
      int currheight = (int)gettpl(fcontext.inserty()).getHeight();
      if (screenposy<0)  {
         while (screenposy<0)  {
            if (fcontext.inserty() ==lastline)
               screenposy=0;
            else {
               fcontext.cursory(1);
               currheight = (int)gettpl(fcontext.inserty()).getHeight();
            }
         }
      } else {
         currheight = (int)gettpl(fcontext.inserty()).getHeight();
         int count = 0;
         while (screenposy>getHeight()-currheight)  {
            if (fcontext.inserty()>1)  {
               fcontext.cursory(-1);
               currheight = (int)gettpl(fcontext.inserty()-count++).getHeight();
            }
            else 
               screenposy=getHeight()- currheight;
         }
      }

      paintLines();
   } 

   void cursorchanged(int xchange, int ychange) {
      trace("cursorchanged " + xchange + "," + ychange);
      int yoffset = fcontext.inserty();
      pmark.cursorChanged(fcontext);
      trace("cursorchanged("+xchange+","+ychange+")");
      if (cursorg==null)
         return;
      try {
         if (ychange!=0) {
            int inserty = fcontext.inserty();
            ttvec.set(inserty,null);
            ttvec.set(inserty-ychange,null);
               while (ychange>0)
                  screenposy+= gettpl(yoffset - ychange--).getHeight();
               while (ychange<0)
                  screenposy-= gettpl(yoffset - ychange++ -1).getHeight();
               if (screenposy<0) {
                  screenposy=0;
               }
               int currheight = (int)gettpl(inserty).getHeight();
               if (screenposy> getHeight()-currheight) {
                     screenposy =  getHeight()-currheight;
               }
         } 
         if (xchange !=0)
            ttvec.set(fcontext.inserty(),null);
            
         if (pmark.getMark()!=null ) 
            refresh(); //???
         else
            paintLines(); //copyarea???
      } catch (IndexOutOfBoundsException e) {
           for (int i=ttvec.size();i<fcontext.edvec.readIn();i++)
           ttvec.add(null);
        cursorchanged(xchange,ychange);
      } 
   }


   void insertedElementsdraw(int start,int amount) {
      refresh();
   }

   void deletedElementsdraw(int start,int amount){
      refresh();
   }

   void changeddraw(int start,int amount){
      refresh();
   }

   void movescreendraw(int amount){
      refresh();
   }

   void refresh(){ // throwing a way lots of info ????
      trace("refresh");
      for(int i=0;i<ttvec.size();i++)
         ttvec.set(i,null);
      paintLines();
   }

   public  void setSize(int x,int y) {
      trace("setSize(" + x + "," + y + ")");
      super.setSize(x,y);
      for(int i=0;i<ttvec.size();i++)
         ttvec.set(i,null);
      line.reset();
      
   }

   void newfile(fvcontext newfvc) {
     ttvec.clear();
     super.newfile(newfvc);
   }
  
   public void setFont(Font font) {
      super.setFont(font);
      FontMetrics fontm =getFontMetrics(font);
      int spacewidth = 4 * fontm.stringWidth(" "); //??? constant 4 tabstop
      tabStops=new float[50];
      for (int i = 0;i<tabStops.length;i++)
         tabStops[i]=(i+1)*spacewidth;
   }

   void setTabs(float [] tabi) {
      tabStops=tabi;
   }

   final TabbedParaLayout gettpl(int index) {
      //trace("gettpl " + index);
      Object obj = ttvec.get(index);
      if (obj!=null) {
         TabbedParaLayout o2 =  (TabbedParaLayout)((WeakReference)obj).get();
         if (o2 !=null)
            return o2;
      }
//         return (TabbedParaLayout)obj;

      editvec text=fcontext.edvec;
      atIt.setText(text.at(index).toString());
if (index==1)
   trace("text = " + text.at(index).toString());
      if (fcontext.inserty()==index) {
         atIt.emphasize(true);
              if (insertbuf != null)
                  atIt.addOlineText(insertbuf.buffer.toString(),fcontext.insertx(),insertbuf.overwrite);
      }
      if (pmark != null) 
           atIt.setHighlight(pmark.starth(index),pmark.endh(index));
      
      TabbedParaLayout drawtt = (atIt.getEndIndex()==0)
          ? new TabbedParaLayout(charheight) 
          : new TabbedParaLayout(atIt,cursorg);
      ttvec.set(index,new WeakReference(drawtt));
//      ttvec.set(index,drawtt);
      return drawtt;
   }  

   private void incCursory(int y,boolean byParaGraph) {
      //trace("incCursory " + y + " byParaGraph = " + byParaGraph);
      if (byParaGraph) 
         fcontext.cursory(y);
      else if (y>0)
         while (--y>=0)
            gettpl(fcontext.inserty()).cursorNextLine(
               fcontext.edvec.readIn()== fcontext.inserty() +1 
                  ?  null 
                  :  gettpl(fcontext.inserty()+1),
               fcontext);
      else
        while (++y<=0) {
            trace("incCursory y = " + y);
            gettpl(fcontext.inserty()).cursorPrevLine(
               fcontext.inserty() ==1 
                  ?  null 
                  :  gettpl(fcontext.inserty()-1),
               fcontext);
        }
   }

   Shape updateCursorShape(Shape sh) {
      if (cursorg==null)
         return null;
      try {
         sh = gettpl(fcontext.inserty()).updateCursorShape(sh,screenposy,boldflag);
      } catch (IndexOutOfBoundsException e) {
        for (int i=ttvec.size();i<fcontext.edvec.readIn();i++)
           ttvec.add(null);
         sh = gettpl(fcontext.inserty()).updateCursorShape(sh,screenposy,boldflag);
      } 
      return sh;
   }
   
   Color getCursorColor() {
          return (insertbuf==null)
             ? Color.cyan
             : Color.pink;
   }

   public void paint(Graphics graph) {
      if (graph!=oldgr) {
         oldgr=graph;
         cursorg=getGraphics2D();
         trace("new Graphics detected");
      }
      try {
         super.paint(cursorg);
      } catch (IndexOutOfBoundsException e) {
        //??? check for performance trace("caught " + e);
        for (int i=ttvec.size();i<fcontext.edvec.readIn();i++)
           ttvec.add(null);
         super.paint(cursorg);
      }

   }

   private void filltrailer(int end) {       
      editvec text=fcontext.edvec;
      if (!text.donereading())  {
         cursorg.setColor(darkpink);
         if (!delayerflag)
            new Thread(new delayer(text.readIn())).start();
      } else 
         cursorg.setColor(Color.black);
      cursorg.fillRect(0,end,  getWidth(), getHeight());
   } 

   private void paintLines() {
      //trace("paintLines cursor" + cursorg);
//      cursorg.setColor(Color.green); cursorg.fillRect(0,0, getWidth() ,getHeight()); //??? debug
      editvec text=fcontext.edvec;
      TabbedParaLayout drawtt;
      int offset=screenposy;

      int cursorl = fcontext.inserty();
      int poffset=0;

      for  (;offset<getHeight() && text.contains(cursorl + (poffset));poffset++)
         offset = gettpl(cursorl+poffset).paint(this,offset);

      lastline=cursorl+poffset-1;
      if (offset<getHeight())
         filltrailer(offset);
     
      offset=screenposy;
      poffset=-1;

      for (;offset>0 && (cursorl + poffset)> 0;poffset--) 
         offset = gettpl(cursorl+poffset).paintBack(this,offset);
      
      firstline = cursorl+poffset+1;
      if ( offset >0) {
         cursorg.setColor(Color.black);
         cursorg.fillRect(0,0, getWidth() ,offset);
      }
   }

   private static class line {
      private float maxAscent=0;
      private float maxDescent=0;
      private ArrayList layouts = new ArrayList(1);
      private ArrayList penPositions = new ArrayList(1);
      private int charCount=0;

      line() {};

      line(int height) {
         maxAscent = height;
         maxDescent = 0;
      }

      void addElement(TextLayout layout,float offset) {
         layouts.add(layout);
         penPositions.add(new Float(offset));
         maxAscent = Math.max(maxAscent, layout.getAscent());
         maxDescent = Math.max(maxDescent, layout.getDescent() + layout.getLeading());
         charCount += layout.getCharacterCount();
      }

      int getHeight() {
         return (int) (maxAscent+maxDescent);
      }
 
      static void reset() {
         for(int i=0;i<dbufarr.length;i++)
            dbufarr[i]=null;
      }
static private Image[] dbufarr = new Image[50];
static private Graphics2D[] imagegarr= new Graphics2D[50];
static private Image dbuf;
static private Graphics2D imageg;
      private void getImage(int height,TabbedTextLayout ttl) {
           dbuf = dbufarr[height];
           if (dbuf ==null) {
trace("ttl.getWidth " + ttl.getWidth());
             dbuf = ttl.createImage(ttl.getWidth(),height);
             imageg = (Graphics2D)dbuf.getGraphics();
             dbufarr[height]=dbuf;
             imagegarr[height]=imageg;
          } else
             imageg=imagegarr[height];
          //imageg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          if (imageg==null)  {
            trace("imageg null!!");
            throw new RuntimeException();  // should never happen
          }
      }

      void paint(TabbedTextLayout ttl,int verticalStart) {

         // now iterate through layouts and draw them
         int endlast=0;
         int height = getHeight();
         getImage(height,ttl);
         for (int it = 0;it<layouts.size();it++) {
            TextLayout currlay = (TextLayout) layouts.get(it);
            float xoffset = ((Float) penPositions.get(it)).floatValue();
            //trace("painting line " + this + " drawCursor " + drawCursor + " iscursorLayout " + (cursorLayout==currlay));

            if (xoffset!=0) {
               imageg.setColor(atview.darkBlue);
               imageg.fillRect(endlast, 0, (int)xoffset , getHeight());
            }
            currlay.draw(imageg, xoffset,(int)maxAscent );

            endlast =(int)xoffset + (int)currlay.getAdvance();

         }
         imageg.setColor(atview.darkBlue);
         imageg.fillRect(endlast, 0,  ttl.getWidth()-endlast , getHeight());
         ttl.cursorg.drawImage(dbuf,0,verticalStart,null);
      }

      Shape updateCursorShape(Shape sh,int yoffset,
           int charOffset,boolean boldflag) {
         //trace("updateCursorShape " + " charOffset " + charOffset +  " yoffset " + yoffset);
//         if (charCount< charOffset)
//            throw new RuntimeException("line.updateCursorShape");

         if (sh==null)
            sh = new Rectangle();
         Rectangle rs = (Rectangle)sh;
         Iterator layit = layouts.iterator();
         Iterator positionEnum = penPositions.iterator();
         while (layit.hasNext() && charOffset >=0) {
            TextLayout currlay = (TextLayout)layit.next();
            float xoffset = ((Float) positionEnum.next()).floatValue();
            if (charOffset > currlay.getCharacterCount())
               charOffset -= currlay.getCharacterCount();
            else {
               float [] cursorinfo =  currlay.getCaretInfo(currlay.getNextLeftHit(charOffset));
               rs.setBounds((int)(cursorinfo[0]+xoffset), yoffset, 
                  boldflag ? 2 :1, (int)getHeight());
               return rs;
           }
         }
         if (charOffset ==1) {
            int last = penPositions.size() - 1;
            if (last==-1)
               rs.setBounds((0),yoffset,  boldflag ? 2 :1, (int)getHeight());
            else {
               float lastAdvance =  ((TextLayout)(layouts.get(last))).getAdvance();
               rs.setBounds((int)(((Float)penPositions.get(last)).floatValue() + lastAdvance),
                  yoffset,  boldflag ? 2 :1, (int)getHeight()); 
            }
            return rs;
         }

         throw new RuntimeException("TabbedText update failure");
      }

      int getCharCount() {
         return charCount;
      }

      int getOffset(float xOffset) {
         //trace("getOffset xOffset = " + xOffset);
         Iterator layit = layouts.iterator();
         Iterator positionit = penPositions.iterator();
         TextLayout currlay = (TextLayout)layit.next();
         int charcount=0;
         float pos =  ((Float)(positionit.next())).floatValue();
         while (layit.hasNext()) {
            float nextpos =  ((Float)(positionit.next())).floatValue();

            //trace("xOffset = " + xOffset + " currlay.getAdvance= " + currlay.getAdvance() + " nextpos = " + nextpos + " charcount = " + charcount);

            if (xOffset <= nextpos)
               return charcount + currlay.hitTestChar(xOffset-pos, 0).getCharIndex();
            charcount += currlay.getCharacterCount();
            currlay = (TextLayout)layit.next();
            pos=nextpos;

         }
         //trace("xOffset1 = " + xOffset + " currlay.getAdvance= " + currlay.getAdvance() + " charcount = " + charcount);
         if (xOffset >currlay.getAdvance() + pos)
            return charcount+currlay.getCharacterCount()-1;
         return charcount + currlay.hitTestChar(xOffset-pos, 0).getCharIndex();
      }

      private float getXoffset(int charOffset) {
         //trace("getXoffset charOffset = " + charOffset + this);
         Iterator layoutEnum = layouts.iterator();
         Iterator positionEnum = penPositions.iterator();

         while (layoutEnum.hasNext()) {
            TextLayout currlay = (TextLayout) layoutEnum.next();
            float xoffset = ((Float) positionEnum.next()).floatValue();
            int currchar =  currlay.getCharacterCount();
            if (charOffset < currchar) {
               float [] cursorinfo = 
                    currlay.getCaretInfo(currlay.getNextLeftHit(charOffset+1));
               return  (cursorinfo[0]+xoffset);
            } else if (charOffset == currchar)
               return  ((Float) positionEnum.next()).floatValue();
                
            charOffset-=currchar+1;
         }
         throw new RuntimeException("getXoffset should never fail charOffset" + charOffset);
      }
      int cursorNext(line nextline,int charOffset) {
           return nextline.getOffset(1+ getXoffset(charOffset));
      }
      //return x char prev
      int cursorPrev(line prevline,int charOffset) {
         trace("cursorPrev " + this + " prevline = " + prevline + " charOffset = " + charOffset);
         int charCount = prevline.charCount - prevline.getOffset(1+ 
             getXoffset(charOffset));
         trace("charcount" + charCount);
//         charCount += charOffset;
         return -charCount;
      }
   }

private class TabbedParaLayout {


   private  intArray tabLocations;
   private  int currTabIndex; 
   private ArrayList lines = new ArrayList(1);
   private LineBreakMeasurer measurer;

   TabbedParaLayout(atview atvi,Graphics2D cursorg) {

      measurer = new LineBreakMeasurer(atvi,cursorg.getFontRenderContext());
      tabLocations= new intArray(10);

      for (char c = atvi.first(); c != atvi.DONE; c = atvi.next())
         if (c == '\t')
            tabLocations.add(atvi.getIndex());

      tabLocations.add(atvi.getEndIndex());

      while (!recalc(atvi)) {
         measurer = new LineBreakMeasurer(atvi,cursorg.getFontRenderContext());
         //trace("extra calc");
      }
   }

   TabbedParaLayout(int height) {
      lines.add(new line(height));
   }

   public void insertChar(atview atv, int insertPos) {
      //skip earlier tab locations.
      int locIndex=0;
      for(;locIndex<tabLocations.size();locIndex++)
         if (tabLocations.get(locIndex) >= insertPos)
           break;
      atv.setIndex(insertPos);
      int currloc = tabLocations.size()-1;
      if (atv.current()=='\t') {
         tabLocations.add(1+tabLocations.get(currloc));
         for (int i = currloc;i>locIndex;i--)
            tabLocations.set(currloc,1+tabLocations.get(currloc-1));
         tabLocations.set(locIndex,insertPos);
//for (int i =0;i<tabLocations.size();i++)
//   trace("tabloc= " + tabLocations.get(i));
//trace("tab added tabloc size = " + tabLocations.size());

      } else  {
         for(;locIndex<=currloc;locIndex++)
            tabLocations.set(locIndex,1+tabLocations.get(locIndex));
      }
      measurer.insertChar(atv,insertPos);
      recalc(atv);
   }

   private final float nextTab(float horizontalPos) {
//trace("nextTab horizontalPos = " + horizontalPos + "currTabIndex = " + currTabIndex);
      int tabCount=tabStops.length;
      for (;currTabIndex<tabCount;currTabIndex++)
         if (horizontalPos < tabStops[currTabIndex])
             break;
      if ((currTabIndex >= tabCount) || (horizontalPos > tabStops[currTabIndex]))
         return horizontalPos;

      for (;currTabIndex<tabCount;currTabIndex++)
         if (horizontalPos < tabStops[currTabIndex])
            return tabStops[currTabIndex++];
      return horizontalPos+   tabCount>=2
            ? tabStops[tabCount-1]-tabStops[tabCount-2]
            : tabCount ==1
               ? tabStops[0]
               : 0;
   }

   private boolean recalc(atview atv) {
      int locIndex=0;
      lines.clear();
      while (measurer.getPosition() < atv.getEndIndex()) {

         float xPos = 0;

         TextLayout layout;
         line currline = new line();
         currTabIndex=0;
//trace("line " + lines.size());
         while ((measurer.getPosition() != atv.getEndIndex())
              &&( null != (layout =
                     measurer.nextLayout(getWidth() - xPos,
                                         tabLocations.get(locIndex)+1,
                                         xPos!=0))))
         {
              //trace("mes pos - " + measurer.getPosition() + " xPos = " + xPos + " currTabIndex = " + currTabIndex);
              currline.addElement(layout, xPos);
              //trace("advance = " + layout.getAdvance());
              xPos = nextTab(xPos + layout.getAdvance());
              if (measurer.getPosition() == tabLocations.get(locIndex)+1)
                 locIndex++;
         }
         if ((lines.size()==0) && !atv.line2(measurer.getPosition()))
            return false;
         lines.add(currline);
      }
//trace("recalc numline = " + lines.size());
      return true;
   }

   Shape updateCursorShape(Shape sh,int yoffset,boolean boldflag) {
      //trace("updateCursorShape yoffset = " + yoffset + " fvc = " + fcontext);
      int charOffset=fcontext.insertx() + 1;
      if (insertbuf!=null)
         charOffset+= insertbuf.buffer.length();
      int height = 0;
      Iterator lineit = lines.iterator();
      line currline=null;
      while (lineit.hasNext() && charOffset >=0) {
         currline = (line)lineit.next();
         int charcount = currline.getCharCount();
         if (charcount >= charOffset) 
            return currline.updateCursorShape(sh,yoffset,charOffset,boldflag);
         charOffset -= currline.getCharCount();
         yoffset+=currline.getHeight();
      }
      if (charOffset==1)
            return currline.updateCursorShape(sh,(yoffset-(int)currline.getHeight()),currline.getCharCount()+1,boldflag);
      throw new RuntimeException("updateCursorShape not enough characters");
   }

   private float getHeight() {
      float height = 0;
      for (int i = 0;i<lines.size();i++)
         height += ((line)lines.get(i)).getHeight();
      return height;
   }

   private void cursorNextLine(TabbedParaLayout nextlayout,fvcontext fvc) {
      //trace("this " + this + " nextlayout = " + nextlayout + "fvc = " + fvc);
      Iterator lineit = lines.iterator();

      // now iterate through layouts and draw them
      int charCount=0;
      float yoffset = 0;
      while (lineit.hasNext()) {
         line currline = (line)lineit.next();
         charCount+= currline.getCharCount();
         if (charCount > fvc.insertx()) {
            if (lineit.hasNext()) {
               line nextline = (line)lineit.next();
               // trace("fvc.insertx() " + fvc.insertx()  + " charCount " +  charCount  + " currline.getCharCount() " + currline.getCharCount());
               fvc.cursorxabs(
                  charCount+currline.cursorNext(nextline,fvc.insertx() - 
                     charCount + currline.getCharCount()));
               return;
            } else if (nextlayout != null) {
               line nextline =  (line)nextlayout.lines.get(0);
               trace("fvc.insertx() " + fvc.insertx()  + " charCount " +  charCount  + " currline.getCharCount() " + currline.getCharCount());
               if (nextline!=null) {
                  fvc.cursorabs(currline.cursorNext(nextline,
                     fvc.insertx() -  charCount + currline.getCharCount()),fvc.inserty()+1);
                }
              
               return;
            } 
         }
      }
   }

   private void cursorPrevLine(TabbedParaLayout prevlayout,fvcontext fvc) {
      trace("this " + this + " nextlayout = " + prevlayout);

      // now iterate through layouts and draw them
      int charCount=0;
      int xchar = fvc.insertx();
      for (int i=0;i<lines.size();i++) {
         line currline = (line)lines.get(i);
         charCount+= currline.getCharCount();
         trace("charCount " + charCount  + " xchar = " + xchar + "currline.charc = " + currline.getCharCount() + " i=" + i);
         if (charCount > xchar) {
            int linecharoffset = xchar-charCount+currline.getCharCount();
            trace("linecharoffset = " + linecharoffset);
            if (i!=0) {
               line prevline = (line)lines.get(i-1);
               fvc.cursorx(currline.cursorPrev(prevline,linecharoffset)-
                           linecharoffset);
               return;
            } else if (prevlayout != null) {
trace("going on to next ttlayout");
               fvc.cursory(-1);
               fvc.cursorxabs(Integer.MAX_VALUE);
               line prevline =  (line)prevlayout.lines.get(
                    prevlayout.lines.size()-1);
               if (prevline!=null)
                  fvc.cursorx(currline.cursorPrev(prevline,linecharoffset));
               return;
            }  else 
               return;
         }
      }
   }
   int getOffset(Point p) {
      //trace("get offset " + p);
      float yOffset = (float)p.getY();
      Iterator lineit = lines.iterator();
      int charcount = 0;

      while (lineit.hasNext() && yOffset >=0) {
         line currline = (line)lineit.next();
         yOffset -= currline.getHeight();
         if (yOffset <=0)
            return charcount + currline.getOffset((float)p.getX());
         charcount += currline.getCharCount();
      }
      return -1;
   }

   public int paint(TabbedTextLayout ttl,int yPos) {
      //trace("paint paintCursor = " + paintCursor + " yPos = " + yPos);
      Iterator lineit = lines.iterator();
      while (lineit.hasNext()) {
         line currline = (line)lineit.next();
         currline.paint(ttl,yPos);
         yPos += currline.getHeight();
     }
     return (int)yPos;
 }

   public int paintBack(TabbedTextLayout ttl,int yPos) {
      //trace("paint paintCursor = " + paintCursor + " yPos = " + yPos);
      for (int i = lines.size()-1;i>=0;i--) {
         line currline = (line)lines.get(i);
         yPos -= currline.getHeight();
         currline.paint(ttl,yPos);
     }
     return (int)yPos;
 }
}
static void trace(String str) {
   ui.trace(str,1);
}
}
