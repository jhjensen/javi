package javi;
/* Copyright 1996 James Jensen all rights reserved */
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
//import static javi.Tools.trace;

public final class Buffers {

   private Buffers() {
      throw new UnsupportedOperationException("attempt to new singleton");
   }

   private static HashMap<Integer, Object> buflist
      = new HashMap<Integer, Object>();
   private static final int circSize = 10; // addressable by single digit int.
   private static CircBuffer delbuffer;

   public static void init(CircBuffer cbuf) {
      buflist.clear();
      delbuffer = cbuf;
   }

   static void deleted(char bufid, String buffer) {
      if (buffer == null)
         return;

      if (bufid == '0') {
         delbuffer.add(buffer);
      } else {
         Object bufo;
         if (bufid >= 'A' && bufid <= 'Z') {
            bufid = (char) (bufid + ('a' - 'A'));
            bufo =  buflist.get(Integer.valueOf(bufid));
            if (bufo == null)
               bufo = buffer;
            else
               if (bufo instanceof ArrayList)
                  ((ArrayList<String>) bufo).add(buffer);
               else
                  bufo = bufo + buffer;
         } else {
            bufo = buffer;
         }

         //trace("buffers adding id " + bufid + " buffer " + bufo);
         buflist.put(Integer.valueOf(bufid), bufo);
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
         Object bufo;
         if (bufid >= 'A' && bufid <= 'Z') {
            bufid = (char) (bufid + ('a' - 'A'));
            bufo =  buflist.get(Integer.valueOf(bufid));
            if (bufo == null) {
               bufo = buffer;
            } else {
               if (bufo instanceof ArrayList) {
                  ((ArrayList<String>) bufo).addAll(buffer);
               } else { // bufo is string
                  buffer.add(0, (String) bufo);
                  bufo = buffer;
               }
            }
         } else {
            bufo = buffer;
         }
         //trace("buffers adding id " + bufid + " buffer " + bufo);
         buflist.put(Integer.valueOf(bufid), bufo);
      }
   }

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
   public abstract static class CircBuffer {
      private Object[] buf;
      private int index;

      final void flush() {
         Arrays.fill(buf, null);
      }

      public abstract void setclip();
      public CircBuffer() {
         buf = new Object[circSize];
      }

      public final void add(String ob) {
         if (++index >= buf.length)
            index = 0;
         buf[index] = ob;
         setclip();
         //trace("add buffer " + index + " = " + buf[index]);
      }

      final void add(ArrayList<String> ob) {
         if (++index >= buf.length)
            index = 0;
         //trace("add buffer " + index + " = " + ob);
         buf[index] = ob;
         setclip();
      }

      public final Object get(int i) {
         int tindex = index - i;
         if (tindex < 0)
            tindex += buf.length;
         //trace("get " +index  + " = " + buf[tindex]);
         return buf[tindex];

      }
      //public void lostOwnership(Clipboard board,Transferable tt) {
      //   //trace("lost ownership");
      //}

      public static String myToString(Object obj) {
         //trace("reached myToString" + obj.getClass());
         String s = "";
         if (obj instanceof String) {
            s = (String) obj;

         } else if (obj instanceof ArrayList) {
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

   }

   static void appendCurrBuf(StringBuilder sb , boolean singleline) {
      Object obj = Buffers.getbuf('0');
      if (obj != null)  {
         if (obj instanceof ArrayList) {
            for (Object obj1 : (ArrayList) obj)  {
               sb.append(obj1.toString());
               sb.append(singleline ? ' ' : '\n');
            }
         } else
            sb.append(obj.toString());
      }
   }

}
