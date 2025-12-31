package javi.awt;

import static history.Tools.trace;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

import javi.Buffers;
import javi.FvContext;
import javi.Rgroup;
import javi.Buffers.CircBuffer;

public final class AwtCircBuffer extends CircBuffer implements
      Transferable, ClipboardOwner {

   private static AwtCircBuffer cbuf;

   static void initCmd() {
      if (null == cbuf)
         cbuf = new AwtCircBuffer();
      Buffers.init(cbuf);
   }

   private Clipboard systemclip =
      java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();

   AwtCircBuffer() {
      new BuffersCmd();
   }

   public DataFlavor[] getTransferDataFlavors() {
      //trace("got getTransferDataFlavors");
      DataFlavor[] temp = {DataFlavor.stringFlavor};
      return temp;
   }

   public void lostOwnership(Clipboard clip, Transferable cont) {
      trace("lost clipBoard ownership");
   }

   private final class BuffersCmd extends Rgroup {
      private BuffersCmd() {
         final String[] rnames = {
            "",
            "paste"
         };

         //trace("registern paste command");
         register(rnames);
      }

      public Object doroutine(int rnum, Object arg, int count,
            int rcount, FvContext fvc, boolean dotmode) {
         switch (rnum) {
            case 1:
               getclip();
               return null;
            default:
               trace("doroutine called with " + rnum);
               throw new RuntimeException("invalid circ buffer command");
         }
      }
   }

   public boolean isDataFlavorSupported(DataFlavor flavor) {
      trace("got isdataflavorsupported " + flavor);
      return flavor == DataFlavor.stringFlavor;
   }

   void enableClip(Object arg) {
      //trace("enable clip arg = " + arg);
      if ("on".equals(arg.toString())) {
         try {
            systemclip =
               java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            //           systemclip = java.awt.Toolkit.getDefaultToolkit().getSystemSelection();
            //trace("sytemclip = " + systemclip);
            setclip();

            //trace("sytemclip contentents = " + systemclip.getContents(this));
         } catch (Exception e) {
            e.printStackTrace();
            trace("caught exception getting clipboard" + e);
         }
      } else {
         systemclip = null;
         //trace("disable clipboard");
      }
   }

   public Object getTransferData(DataFlavor flavor) throws
         UnsupportedFlavorException {
      ////trace("got getTransferData" + flavor);

      if (flavor != DataFlavor.stringFlavor)
         throw new UnsupportedFlavorException(flavor);

      Object ret = get(0);
      return null == ret
             ? ""
             : myToString(ret);
   }

   public void setclip() {
      //trace("reached setclip");
      if (null == systemclip)
         return;
      //trace("reached setclip2");

      systemclip.setContents(this, this);
      //trace("exiting setclip");

//        StringSelection temp = new StringSelection(myToString(get(0)));
//        systemclip.setContents(temp,temp);

   }

   void  getclip() {
      if (null == systemclip)
         return;
      try {
         Transferable tr = systemclip.getContents(this);
         //trace("trans = " + tr);
         //trace(" trans flavors size = " + tr.getTransferDataFlavors().length);
         //trace(" trans flavors = " + tr.getTransferDataFlavors()[0]);
         //trace("trans :" + tr.getTransferData(DataFlavor.stringFlavor) +":");
         add(tr.getTransferData(DataFlavor.stringFlavor).toString());
      } catch (Throwable e) {
         e.printStackTrace();
         trace("caught exception in getclip " + e);
      }
   }
}
