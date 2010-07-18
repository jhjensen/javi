package javi;
/* Copyright 1996 James Jensen all rights reserved */
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;

final class Buffers {

   private Buffers() {
      throw new UnsupportedOperationException();
   }

   private static HashMap<Integer, Object> buflist
      = new HashMap<Integer, Object>();
   private static final int circSize = 10; // addressable by single digit int.
   private static CircBuffer delbuffer = new CircBuffer(circSize);
   //private static Buffers bufs = new Buffers(); // this has to be static to force instantiation
   static void initCmd() {
      new BuffersCmd();
   }
   static void clearmem() {
      buflist.clear();
      delbuffer = new CircBuffer(circSize);
   }
   private static final class BuffersCmd extends Rgroup {
      static final String[] rnames = {
         "",
         "enableclip",
         "paste"
      };
      private BuffersCmd() {
         //trace("registern paste command");
         register(rnames);
      }
      public Object doroutine(int rnum, Object arg, int count,
            int rcount, FvContext fvc, boolean dotmode) {
         switch (rnum) {
            case 1:
               delbuffer.enableClip(arg);
               return null;
            case 2:
               delbuffer.getclip();
               return null;
            default:
               trace("doroutine called with " + rnum);
               throw new RuntimeException();
         }
      }
   }

   static void deleted(char bufid, String buffer) {

      if (buffer == null)
         return;

      if (bufid == '0') {
         delbuffer.add(buffer);
      } else {
         if (bufid >= 'A' && bufid <= 'Z') {
            bufid = (char) (bufid + ('a' - 'A'));
            Object bufo =  buflist.get(Integer.valueOf(bufid));
            if (bufo != null) {
               if (bufo instanceof ArrayList) {
                  ((ArrayList<String>) bufo).add(buffer);
                  buflist.put(Integer.valueOf(bufid), bufo);

               } else { // bufo is string
                  buflist.put(Integer.valueOf(bufid), bufo + buffer);
               }
            }
         }
      }
   }

   static void deleted(char bufid, ArrayList<String> buffer) {

      if (buffer == null)
         return;

      if (bufid == '0') {
         ArrayList bufarr = (ArrayList) buffer;
         ArrayList<String> strs = new ArrayList<String>(bufarr.size());
         for (Object obj : bufarr)
            strs.add(obj.toString());
         delbuffer.add(strs);
      } else {
         if (bufid >= 'A' && bufid <= 'Z') {
            bufid = (char) (bufid + ('a' - 'A'));
            Object bufo =  buflist.get(Integer.valueOf(bufid));
            if (bufo != null) {
               if (bufo instanceof ArrayList) {
                  ((ArrayList<String>) bufo).addAll(buffer);
                  buflist.put(Integer.valueOf(bufid), bufo);
               } else { // bufo is string
                  buffer.add(0, (String) bufo);
                  buflist.put(Integer.valueOf(bufid), buffer);
               }
            }
         }
      }
   }

   /*
   static void deleted(char bufid,Object buffer) {
      //trace("Buffers.deleted: bufid = " + bufid + " class " + buffer.getClass().toString());
      //if (buffer instanceof Object[]) {
      //   for (Object ob:(Object [])buffer)
      //       trace("Buffers.deleted: " + ob);
      //} else
      //   trace(" buffer = " + buffer);

     if (buffer==null)
        return;

     if (buffer instanceof Object[]) {
         throw new RuntimeException(
            "buffers deleted only does collections and strings");
     }
     if (bufid == '0') {
   //     if (buffer instanceof Object[]) {
   //        Object[] bufarr = (Object[] )buffer;
   //        ArrayList<String> strs = new ArrayList<String>(bufarr.length);
   //        for (Object obj :bufarr )
   //           strs.add(obj.toString());
   //        delbuffer.add(strs);
   //     } else
         if (buffer instanceof ArrayList) {
           ArrayList bufarr = (ArrayList)buffer;
           ArrayList<String> strs = new ArrayList<String>(bufarr.size());
           for (Object obj :bufarr)
              strs.add(obj.toString());
           //trace("adding array size " + strs.length);
           delbuffer.add(strs);
        } else
           delbuffer.add(buffer.toString());
     } else {
         if (bufid >= 'A' && bufid <= 'Z') {
            bufid = (char)(bufid + ('a' -'A'));
            Object bufo =  buflist.get(Integer.valueOf(bufid));
            if (bufo != null) {
               if (bufo instanceof ArrayList ) {
                  if (buffer instanceof ArrayList)
                     ((ArrayList)bufo).addAll((ArrayList)buffer);
                  else
                     ((ArrayList)bufo).add(buffer);
                  buffer = bufo;

               } else { // bufo is string
                  if (buffer instanceof ArrayList)
                     ((ArrayList)buffer).add(0,bufo);
                  else
                     buffer = (String)bufo + (String)buffer;
               }
            }
        }
        buflist.put(Integer.valueOf(bufid),buffer);
     }
   }
   */
   static Object getbuf(char id) {
      //trace("vic.getbuf: bufid = " + id);
      if (id >= 'A' && id <= 'Z')
         id = (char) (id + ('a' - 'A'));

      return id >= '0' && id <= '9'
             ? delbuffer.get(id - '0')
             : buflist.get(Integer.valueOf(id));

      //trace("getbuf returning " + retval + " class " + retval.getClass().toString());
      //return retval;
   }


//   private static class CircBuffer implements Transferable,ClipboardOwner
   private static final class CircBuffer extends StringSelection {
      private Object[] buf;
      private int index;
      private Clipboard systemclip =
         java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();

      void flush() {
         Arrays.fill(buf, null);
      }

      CircBuffer(int size) {
         super("");
         buf = new Object[size];
         setclip();
      }

      /*
            public DataFlavor[] getTransferDataFlavors() {
               //trace("got getTransferDataFlavors");
               DataFlavor[] temp = {DataFlavor.stringFlavor};
               return temp;
            }

      */
      public boolean isDataFlavorSupported(DataFlavor flavor) {
         Tools.trace("got isdataflavorsupported " + flavor);
         return  (flavor == DataFlavor.stringFlavor)
                 ? true
                 : false;
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
               Tools.trace("caught exception getting clipboard" + e);
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
         return ret == null
                ? ""
                : myToString(ret);
      }

      void add(String ob) {
         if (++index >= buf.length)
            index = 0;
         buf[index] = ob;
         setclip();
         //trace("add buffer " + index + " = " + buf[index]);
      }

      void add(ArrayList<String> ob) {
         if (++index >= buf.length)
            index = 0;
         //trace("add buffer " + index + " = " + ob);
         buf[index] = ob;
         setclip();
      }

      Object get(int i) {
         int tindex = index - i;
         if (tindex < 0)
            tindex += buf.length;
         //trace("get " +index  + " = " + buf[tindex]);
         return buf[tindex];

      }
      //public void lostOwnership(Clipboard board,Transferable tt) {
      //   //trace("lost ownership");
      //}

      private static String myToString(Object obj) {
         //trace("reached myToString" + obj.getClass());
         String s = "";
         if (obj instanceof String) {
            s = (String) obj;

         } else  if (obj instanceof ArrayList) {
            ArrayList<String> o2 = (ArrayList<String>) obj;
            int len = 0;
            for (String str : o2)
               len += 1 + str.length();
            StringBuilder sb = new StringBuilder(len);
            for (String str : o2) {
               sb.append(str);
               sb.append('\n');
            }
            s = sb.toString();

         } else  {
            s = (obj.toString());
            //trace("adding string " + s);
         }
         //trace("mts :" + s +":");
         return s;
      }

      void setclip() {
         //trace("reached setclip");
         if (systemclip == null)
            return;
         //trace("reached setclip2");

         systemclip.setContents(this, this);
         //trace("exiting setclip");


//        StringSelection temp = new StringSelection(myToString(get(0)));
//        systemclip.setContents(temp,temp);

      }

      void  getclip() {
         if (systemclip == null)
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
            Tools.trace("caught exception in getclip " + e);
         }
      }
   }
}
