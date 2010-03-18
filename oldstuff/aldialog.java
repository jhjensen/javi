package javi;
import java.awt.Button;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.awt.AWTEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import javax.swing.BoxLayout;

class ALDialog extends Dialog implements MouseListener,KeyListener  {
   int diaflag;
   private static final int UNINIT = 0;
   private static final int OK = 1;
   private static final int BAD = 2;
   AWTEvent lastevent = null;

   void dadd(ALButton al) {
      add(al);
   } 

   ALDialog(Frame fr,String title) {
      super(fr,title);
      new ALButton("OK",OK);
      new ALButton("Not OK",BAD);
      setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
   }
   void reinit() {
      diaflag=UNINIT;
   }

   boolean ok() {
     return diaflag==OK;
   }

   class ALButton extends Button implements ActionListener {
      int flag;
      ALButton(String s,int iflag) {
        super(s);
        flag = iflag;
        dadd(this);
        addActionListener(this);
      }
   
      public void actionPerformed(ActionEvent e) {
             diaflag=flag;
             setVisible(false);
             vic.eventq.insert(new KeyEvent(this,0,0,0,0,'e'));
      }
   }
   AWTEvent getevent() {
      while (diaflag==UNINIT && isVisible())  
         if (lastevent != null) {
            AWTEvent retval = lastevent;
            lastevent = null;
            return retval;
         }
           else try {Thread.sleep(10);} catch (InterruptedException e) {}
      return null;
   }
   public void keyPressed(KeyEvent e) {
        lastevent = e;
       //trace("");
    }
    public void keyReleased(KeyEvent e) {
       //trace("");
    }
    public void keyTyped(KeyEvent e) {//trace("");
    }

    public void mouseClicked(MouseEvent e) {
        lastevent = e;
    }
    public void mouseEntered(MouseEvent e) {/*trace("entered");*/}
    public void mouseExited(MouseEvent e) {/*trace("exited");*/}
    public void mousePressed(MouseEvent e) {/*trace("pressed");*/}
    public void mouseReleased(MouseEvent e) {/*trace("released");*/}
static void trace(String str) {
   ui.trace(str,1);
}
}
