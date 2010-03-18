package javi;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.io.File;
import java.io.FileWriter;
import java.io.StringReader;
import java.io.IOException ;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import java.text.AttributedCharacterIterator ;

import java.awt.font.TextAttribute;
import java.awt.Font;
import java.awt.Color;

import java.awt.Point;
import java.awt.Canvas;
import java.awt.Frame;
import java.awt.Dialog;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Button;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.awt.AWTEvent;

import javax.swing.BoxLayout;

import junit.framework.TestCase;

class TabbedTextLayoutTest extends TestCase implements MouseListener,KeyListener  {

   TabbedTextLayoutTest(String name) {
      super(name);
   }

   private Frame fr = new Frame();
   private Dialog dia = new Dialog(fr,"TabbedTextLayout test");
   private view ttl;
   private   float [] tabStops = {100,200,300,400};
   private   fvcontext fvc;

   private static final int UNINIT = 0;
   private static final int OK = 1;
   private static final int BAD = 2;

   private int diaflag = UNINIT;

   public void setUp() {
        ttl =  new TabbedTextLayout();
//        ttl =  new oldview(); //20000
        ttl.setVisible(true);
        ttl.setFont(fontlist.getCurr());
        if (ttl instanceof TabbedTextLayout)
           ((TabbedTextLayout)ttl).setTabs(tabStops);
        ttl.setSize(500,175);
        ttl.addKeyListener(this);
        ttl.addMouseListener(this);
        ttl.setBackground(atview.darkBlue);
        ttl.setForeground(Color.cyan);
      
        dia.setLayout(new BoxLayout(dia,BoxLayout.Y_AXIS));
        new ALButton("OK",OK,dia);
        new ALButton("Not OK",BAD,dia);
        dia.add(ttl);
        dia.pack();
        ttl.requestFocus();
        //fr.setVisible(true);
   }

   public static void main (String args[]) {
      try {
         TabbedTextLayoutTest lt = new TabbedTextLayoutTest("asdfsaf");
         lt.setUp();
         lt.basictest();
trace("");
         lt.perftest();
      } catch (Throwable e) {
         trace("main caught exception " + e);
         e.printStackTrace();
         System.exit(0);
      }
      System.err.println("all tests passed");
      System.exit(0);
      System.err.println("exit failed");
   }

   void basictest() throws IOException {
      File dd = new File("viewTest.txt");
      dd.delete();
      dd = new File("viewTest.txt.dmp2");
      dd.delete();


      extext ex = new extext(new filereader(new File("viewTest.txt"),"viewTest.txt"));
      ex.finish();
      fvc = fvcontext.getcontext(ttl,ex);
      ex.changeElementAt("text1\ttab1\ttab3\t\ttab5\tshould_go_on_next_line6789012345678901234567890123456789012345678901234567890123456789012345678901234567890 xxxxxxxxxxxxxxxxxx xxxxxxxxxxxxxxxxxxxxxxxxx  xxxxxxxxxxxxxxxxxxxx",1);
      ex.insertOne("text2xxxxxxxxxxxxxxxxxxxxxxxxx  xxxxxxxxxxxxxxxxxxxx\t\txxxxxxxxxxxxxxxxx",2);
      ex.insertOne("xxxx\t\t\t\t\t\t\txxxxxxxxxxxxxxxxxxxmarked",3);
      ex.checkpoint();
      ttl.newfile(fvc);
      ttl.insertbuf = new insertcontext("insertbuf");
      diaflag=UNINIT;
      dia.setVisible(true);
      
      vic.eventq.setViewer(ttl);
      while (diaflag==UNINIT && dia.isVisible()) {
         AWTEvent e =  vic.eventq.nextEvent();
         if (e instanceof KeyEvent)
            keyPressed((KeyEvent)e);
         else if  (e instanceof MouseEvent)
            mouseClicked((MouseEvent)e);
      }
      
trace("");
      assertTrue(diaflag==OK);

  }

   void perftest() throws IOException {
     ui.setStream(new StringReader("f\n"));
      File dd = new File("perftest.txt.dmp2");
      dd.delete();
      dd = new File("perftest.txt");
      dd.delete();

      ttl.insertbuf=null;
      
      FileWriter fs = new FileWriter("perftest.txt");

      int tot = 2000;
      for (int i = 0;i<tot;i++)
         fs.write("xxline " + i + '\n');
      fs.close();
      System.gc();
      System.runFinalization();
      System.gc();
      System.runFinalization();
      System.gc();


      extext ex = new extext(new filereader(new File("perftest.txt"),"viewTest.txt"));
      ex.finish();
      fvc = fvcontext.getcontext(ttl,ex);
      ex.checkpoint();
      ttl.newfile(fvc);
      ttl.setSizebyChar(80,40);
      ttl.setSize(ttl.getPreferredSize());
      dia.setVisible(true);
      dia.pack();
      Date start = new Date();
      for (int i = 0;i<tot;i++)  {
         fvc.inc();
         ttl.npaint();
      }
      System.gc();
      System.runFinalization();
      System.gc();
      Date end = new Date();
      long elapsed =  end.getTime() - start.getTime();
      long mem =  Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
      trace("elapsed time = " + elapsed + " mem = " + mem);
   }

   boolean exceptionflag;
   int oldMargin ;
   int tt2offset;
    public void keyPressed(KeyEvent e) {
    }
    public void keyReleased(KeyEvent e) {
       //trace("");
    }
    public void keyTyped(KeyEvent e) {//trace("");
    }

    public void mouseClicked(MouseEvent e) {
       trace("mouseClicked" + ttl);
       fvc.cursorabs(ttl.mousepos(e));
    }
    public  void setSize(int x,int y) {
      trace("test size");
    }
    public void mouseEntered(MouseEvent e) {/*trace("entered");*/}
    public void mouseExited(MouseEvent e) {/*trace("exited");*/}
    public void mousePressed(MouseEvent e) {/*trace("pressed");*/}
    public void mouseReleased(MouseEvent e) {/*trace("released");*/}
static void trace(String str) {
   ui.trace(str,1);
}
private class ALButton extends Button implements ActionListener {
   int flag;
   Dialog dia;
   ALButton(String s,int iflag,Dialog diai) {
     super(s);
     flag = iflag;
     dia = diai;
     dia.add(this);
     addActionListener(this);
   }
   public void actionPerformed(ActionEvent e) {
          diaflag=flag;
          dia.setVisible(false);
          vic.eventq.insert(new KeyEvent(this,0,0,0,0,'e'));
   }
}
}
