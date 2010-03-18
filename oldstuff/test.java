package javi;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.datatransfer.StringSelection;

class test  
   implements Transferable,ClipboardOwner { //extends StringSelection{
      Clipboard systemclip;

      test() {
        try {
           systemclip =
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
           systemclip.setContents(this,this);
         System.out.println("sytemclip contentents = " + systemclip.getContents(this));

        } catch (Exception e) {
          System.out.println("test caught exception getting clipboard" +e);
        }
      }

      public DataFlavor[] getTransferDataFlavors() {
         System.out.println("got getTransferDataFlavors");
         DataFlavor[] temp = {DataFlavor.stringFlavor};
         return temp;
      }

      public boolean isDataFlavorSupported(DataFlavor flavor) {
         System.out.println("got isdataflavorsupported");
//         if (flavor==DataFlavor.plainTextFlavor || flavor==DataFlavor.stringFlavor)
         if (flavor==DataFlavor.stringFlavor)
            return true;
         else
            return false;
      }

      public Object getTransferData(DataFlavor flavor) throws
          java.awt.datatransfer.UnsupportedFlavorException  {
         System.out.println("got getTransferData");
         if (flavor==DataFlavor.stringFlavor)
            return("test string");
         else
            throw new UnsupportedFlavorException(flavor);
      }

      public void lostOwnership(Clipboard board,Transferable tt) {
          systemclip=null;
         System.out.println("lost ownership");
}

public static void main (String args[]) {
new test();
}
}
