package javi;

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;

import junit.framework.TestCase;
import junit.framework.TestResult;

class AtViewTest extends TestCase implements java.io.Serializable {

   private static final long serialVersionUID=1;
   AtViewTest(String name) {
      super(name);
   }

   private static Color darkBlue = new Color(0,0,50);// 32 turns black (0,0,96); (0,0,64); (0,0,77);
   Frame fr = new Frame();
   Dialog dia = new Dialog(fr,"atview test");
   Canvas canv = new TestCanvas();
   AtView atr = new AtView(new Font("Courier",Font.PLAIN,14));


   private enum DiaState {UNINIT, OK ,BAD };

   private DiaState diaflag = DiaState.UNINIT;

   public void setUp() throws Exception{
        super.setUp();
        canv.setVisible(true);
        canv.setSize(500,50);
        dia.add(canv);
        dia.setBackground(darkBlue);
        dia.setModal(true);
      
        atr = new AtView(new Font("Courier",Font.PLAIN,14));
        dia.setLayout(new BoxLayout(dia,BoxLayout.Y_AXIS));
        new ALButton("OK",DiaState.OK,dia);
        new ALButton("Not OK",DiaState.BAD,dia);
        dia.pack();
        //fr.setVisible(true);
   }
 
   public void run(TestResult result) {
         //testAti();
         testbasic();
         testemph();
         testoline();
         testtab();
   }

   public int countTestCases() {
      return 4;
   }

   public static void main (String args[]) {
      try {
         AtViewTest lt = new AtViewTest("asdfsaf");
         lt.setUp();
         lt.run();
      } catch (Throwable e) {
         trace("main caught exception " + e);
         e.printStackTrace();
         System.exit(0);
      }
      trace("all tests passed");
      System.exit(0);
      trace("exit failed");
    
   }
   
   void testbasic() {
      atr.setText("dark light dark");
      atr.setHighlight(5,10);
      diaflag=DiaState.UNINIT;
      dia.setVisible(true);
      assertTrue(diaflag==DiaState.OK);

      atr.setText("light dark");
      atr.setHighlight(0,5);
      diaflag=DiaState.UNINIT;
      dia.setVisible(true);
      assertTrue(diaflag==DiaState.OK);

      atr.setText("dark light");
      atr.setHighlight(5,Integer.MAX_VALUE);
      diaflag=DiaState.UNINIT;
      dia.setVisible(true);
      assertTrue(diaflag==DiaState.OK);

      assertTrue(!exceptionflag);
   }

//   class SimpleAti implements AttributedCharacterIterator {
//   }
/*
   void testbasic() {
      atr.setText("dark dark");
      atr.setHighlight(5,10);
      diaflag=DiaState.UNINIT;
      dia.setVisible(true);
      assertTrue(diaflag==DiaState.OK);

      atr.setText("light dark");
      atr.setHighlight(0,5);
      diaflag=DiaState.UNINIT;
      dia.setVisible(true);
      assertTrue(diaflag==DiaState.OK);

      atr.setText("dark light");
      atr.setHighlight(5,Integer.MAX_VALUE);
      diaflag=DiaState.UNINIT;
      dia.setVisible(true);
      assertTrue(diaflag==DiaState.OK);

      assertTrue(!exceptionflag);
   }
*/
   void testemph() {
      atr.setText("should be underlined");
      atr.emphasize(true);
      atr.setHighlight(7,9);
      diaflag=DiaState.UNINIT;
      dia.setVisible(true);
   
      atr.emphasize(false);
      assertTrue(diaflag==DiaState.OK);
   }

   void testoline() {
      atr.setText("startpink><endpink should be underlined");
      atr.addOlineText("(pink text)",10,false);
      atr.emphasize(true);
      diaflag=DiaState.UNINIT;
      dia.setVisible(true);
   
      atr.emphasize(false);
      assertTrue(diaflag==DiaState.OK);
   }

   void testtab() {
      atr.setText("tab>\t<tab");
      diaflag=DiaState.UNINIT;
      dia.setVisible(true);
   
      atr.emphasize(false);
      assertTrue(diaflag==DiaState.OK);
   }

   boolean exceptionflag;
   class TestCanvas extends Canvas {
     private static final long serialVersionUID=1;
     public void paint(Graphics g) {
        try {
           g.drawString(atr,20,20);
        } catch (Exception e) {
           exceptionflag = true;
           e.printStackTrace();
        }
    }
   }

static void trace(String str) {
   Tools.trace(str,1);
}

private class ALButton implements ActionListener {
   Button button;
   DiaState flag;
   Dialog dia;
   private static final long serialVersionUID=1;
   ALButton(String s,DiaState iflag,Dialog diai) {
     button = new Button(s);
     flag = iflag;
     dia = diai;
     dia.add(button);
     button.addActionListener(this);
   }
   public void actionPerformed(ActionEvent e) {
          diaflag=flag;
          dia.setVisible(false);
   }
}

}
