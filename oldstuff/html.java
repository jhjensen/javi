package javi;

import junit.framework.TestCase;
import java.awt.Frame;
import java.awt.AWTEvent;
import java.io.IOException;
import javax.swing.BoxLayout;

class html extends TestCase {


   html(String name) {
      super(name);
   }
   private ALDialog dia;

   public void setUp() throws IOException {
      Frame fr = new Frame();
      //fr.setSize(600,600);
//      fr.setLayout(new BoxLayout(fr,BoxLayout.Y_AXIS));
      //fr.setSize(new Dimension(500,500));
      dia = new ALDialog(fr,"html test");
//      htmlv hp = new htmlv("file:///C:/j2sdk1.4.2/docs/api/javax/swing/text/AbstractDocument.Content.html#createPosition(int)");
      htmlv hp = new htmlv("file:///C:/j2sdk1.4.2/docs/api/index.html");
      //dia.add(hp);
      fr.add(hp);
      fr.setVisible(true);
      fr.pack();
      hp.requestFocus();
      
      dia.pack();
   }

   void basictest() {
      dia.reinit();
      dia.setVisible(true);
      
      AWTEvent e;
      while ((e =  dia.getevent())!=null)
         ;
      assertTrue(dia.ok());
   }
   public static void main (String args[]) {
      try {
         html lt = new html("asdfsaf");
         lt.setUp();
         lt.basictest();
      } catch (Throwable e) {
         trace("main caught exception " + e);
         e.printStackTrace();
         System.exit(0);
      }
      System.err.println("all tests passed");
      System.exit(0);
      System.err.println("exit failed");
   }

static void trace(String str) {
   ui.trace(str,1);
}
}
