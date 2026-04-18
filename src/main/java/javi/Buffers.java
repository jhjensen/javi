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

   /**
    * Fold span of the most recently deleted/yanked content.
    * When dd or yy is used on a collapsed fold, this records
    * the fold span so that p can recreate the fold mark.
    * Zero means no fold metadata.
    */
   private static int lastFoldSpan;

   /** Record fold span for the most recent yank/delete. */
   static void setLastFoldSpan(int span) {
      lastFoldSpan = span;
   }

   /** Get the fold span from the most recent yank/delete. */
   static int getLastFoldSpan() {
      return lastFoldSpan;
   }

   /** Clear fold metadata (for non-fold yank/delete). */
   static void clearFoldSpan() {
      lastFoldSpan = 0;
   }

   public static void init(CircBuffer cbuf) {
      buflist.clear();
      delbuffer = cbuf;
   }

   // B6: unchecked cast unavoidable — buflist is HashMap<Integer, Object>
   // storing both String and ArrayList<String>. Erasure prevents runtime
   // verification of ArrayList<String>; instanceof guards are as safe as
   // possible. Redesigning to typed containers would require splitting the
   // heterogeneous buflist into separate maps, breaking the vi register
   // model where a register can hold either a line or multiple lines.
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

   @SuppressWarnings("unchecked") // B6: same erasure issue as above
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
               } else if (bufo instanceof String s) { // bufo is string
                  buffer.add(0, s);
                  bufo = buffer;
               } else {
                  // Unexpected type - convert to string and prepend
                  buffer.add(0, bufo.toString());
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

      @SuppressWarnings("unchecked") // B6: same erasure — obj may be ArrayList<String>
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
