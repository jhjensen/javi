package javi;

import java.awt.Button;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import javax.swing.BoxLayout;

import junit.framework.TestCase;

import static javi.FileDescriptor.LocalFile.make;
   
class ViewTest extends TestCase implements MouseListener,KeyListener{

   ViewTest(String name) {
      super(name);
   }

   private Frame fr = new Frame();
   private Dialog dia = new Dialog(fr,"viewTest test");
   private OldView vi = new OldView(false);

   private static final int UNINIT = 0;
   private static final int OK = 1;
   private static final int BAD = 2;

   private int diaflag = UNINIT;

   public void setUp() throws Exception{
        super.setUp();
        vi.setVisible(true);
        vi.setFont(new Font("Courier",Font.PLAIN,14));
        vi.setSize(500,100);
        

        make("viewTest.txt").delete();
        make("viewTest.txt.dmp2").delete();

//        vic vico = new vic("");
        TextEdit<String> ex = new TextEdit<String>(new FileInput(FileDescriptor.LocalFile.make("viewTest.txt")));
        ex.finish();
        FvContext fvc = FvContext.getcontext(vi,ex);
        ex.inserttext("12345678901234567890123456789012345678901234567890123456789012345678901234567890abc\tdef\nghi\tjkl",1,1);
trace("fvc = " + fvc);
        ex.checkpoint();
        fvc.fixCursor();
        vi.newfile(FvContext.getcontext(vi,ex));
        fvc.cursorx(10);
        vi.addKeyListener(this);
        vi.addMouseListener(this);
        dia.add(vi);
      
        dia.setLayout(new BoxLayout(dia,BoxLayout.Y_AXIS));
        new ALButton("OK",OK,dia);
        new ALButton("Not OK",BAD,dia);
        dia.pack();
        ex.idleSave();
        //fr.setVisible(true);
   }

   public static void main (String args[]) {
      try {
         ViewTest lt = new ViewTest("asdfsaf");
         lt.setUp();
         lt.testbasic();
         trace("all tests passed");
         System.exit(0);
      } catch (Throwable e) {
         trace("main caught exception " + e);
         e.printStackTrace();
         System.exit(0);
      }
    
   }
   int oldcursor=126;
   void testbasic() {

      diaflag=UNINIT;
      while (diaflag==UNINIT)
         dia.setVisible(true);
      assertTrue(diaflag==OK);
  }

    public void keyPressed(KeyEvent e) {
       if (e.getKeyCode()==KeyEvent.VK_LEFT)
           oldcursor--;
       else if (e.getKeyCode()==KeyEvent.VK_RIGHT)
           oldcursor++;
       else {
           trace("keyCode = " + e.getKeyCode());
           return;
       }
    }
    public void keyReleased(KeyEvent e) {/* dont care */ }
    public void keyTyped(KeyEvent e) {/* don't care */}

    public void mouseClicked(MouseEvent e) {
       trace("oldcursor" + oldcursor);
    }
    public void mouseEntered(MouseEvent e) {trace("entered");}
    public void mouseExited(MouseEvent e) {trace("exited");}
    public void mousePressed(MouseEvent e) {trace("pressed");}
    public void mouseReleased(MouseEvent e) {trace("released");}


static void trace(String str) {
   Tools.trace(str,1);
}
private class ALButton implements ActionListener {
   private Button but;
   int flag;
   Dialog dia;
   ALButton(String s,int iflag,Dialog diai) {
     but = new Button(s);
     flag = iflag;
     dia = diai;
     dia.add(but);
     but.addActionListener(this);
   }
   public void actionPerformed(ActionEvent e) {
          diaflag=flag;
          dia.setVisible(false);
   }
}
}

