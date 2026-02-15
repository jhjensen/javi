package javi;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
//import static javi.Tools.trace;

public final class Buffers {

   private Buffers() {
      throw new UnsupportedOperationException("attempt to new singleton");
   }

   // I don't usually use that many buffers
   private static HashMap<Integer, Object> buflist
      = new HashMap<>(10);
   private static final int circSize = 10; // addressable by single digit int.
   private static CircBuffer delbuffer;

   public static void init(CircBuffer cbuf) {
      buflist.clear();
      delbuffer = cbuf;
   }

   @SuppressWarnings("unchecked")
   static void deleted(char bufid, String buffer) {
      if (null == buffer)
         return;

      if ('0' == bufid) {
         delbuffer.add(buffer);
      } else {
         Object bufo;
         if (bufid >= 'A' && bufid <= 'Z') {
            bufid = (char) (bufid + ('a' - 'A'));
            bufo =  buflist.get(Integer.valueOf(bufid));
            if (null == bufo)
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

   @SuppressWarnings("unchecked")
   static void deleted(char bufid, ArrayList<String> buffer) {

      if (null == buffer)
         return;

      if ('0' == bufid) {
         delbuffer.add(buffer);
      } else {
         Object bufo;
         if (bufid >= 'A' && bufid <= 'Z') {
            bufid = (char) (bufid + ('a' - 'A'));
            bufo =  buflist.get(Integer.valueOf(bufid));
            if (null == bufo) {
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

      public abstract void setclip();

      final void flush() {
         Arrays.fill(buf, null);
      }

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

      @SuppressWarnings("unchecked")
      public static final String myToString(Object obj) {
         //trace("reached myToString" + obj.getClass());
         String s;
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

   static void appendCurrBuf(StringBuilder sb, boolean singleline) {
      Object obj = Buffers.getbuf('0');
      if (null != obj)  {
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
