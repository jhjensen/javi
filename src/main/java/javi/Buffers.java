package javi;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
//import static javi.Tools.trace;

public final class Buffers {

   static final class RegisterState {
      // Named registers can hold either String or ArrayList<String>.
      private final HashMap<Integer, Object> namedRegisters =
         new HashMap<>(10);
      private int foldSpan;
      // Unnamed register ("") — value of the most recent yank or delete.
      private Object unnamedValue;
      private boolean unnamedLinewise;
      // Small-delete register ("-) — last sub-line delete.
      private String smallDelete;

      void clearNamedRegisters() {
         namedRegisters.clear();
      }

      void clearAll() {
         namedRegisters.clear();
         unnamedValue = null;
         unnamedLinewise = false;
         smallDelete = null;
         foldSpan = 0;
      }

      Object getNamed(char id) {
         return namedRegisters.get(Integer.valueOf(id));
      }

      void putNamed(char id, Object value) {
         namedRegisters.put(Integer.valueOf(id), value);
      }

      int namedCount() {
         return namedRegisters.size();
      }

      void setFoldSpan(int span) {
         foldSpan = span;
      }

      int getFoldSpan() {
         return foldSpan;
      }

      void setUnnamed(Object value, boolean linewise) {
         unnamedValue = value;
         unnamedLinewise = linewise;
      }

      Object getUnnamed() {
         return unnamedValue;
      }

      boolean isUnnamedLinewise() {
         return unnamedLinewise;
      }

      void setSmallDelete(String value) {
         smallDelete = value;
      }

      String getSmallDelete() {
         return smallDelete;
      }
   }

   private Buffers() {
      throw new UnsupportedOperationException("attempt to new singleton");
   }

   private static final RegisterState registerState = new RegisterState();
   private static final int circSize = 10; // addressable by single digit int.
   private static CircBuffer delbuffer;

   /**
    * Fold span of the most recently deleted/yanked content.
    * When dd or yy is used on a collapsed fold, this records
    * the fold span so that p can recreate the fold mark.
    * Zero means no fold metadata.
    */
   /** Record fold span for the most recent yank/delete. */
   static void setLastFoldSpan(int span) {
      registerState.setFoldSpan(span);
   }

   /** Get the fold span from the most recent yank/delete. */
   static int getLastFoldSpan() {
      return registerState.getFoldSpan();
   }

   /** Clear fold metadata (for non-fold yank/delete). */
   static void clearFoldSpan() {
      registerState.setFoldSpan(0);
   }

   static int namedRegisterCount() {
      return registerState.namedCount();
   }

   /** True if the unnamed register was last populated by a linewise op. */
   static boolean isUnnamedLinewise() {
      return registerState.isUnnamedLinewise();
   }

   public static void init(CircBuffer cbuf) {
      registerState.clearAll();
      delbuffer = cbuf;
   }

   /**
    * Record a charwise yank. Updates the addressed register, the
    * numbered ring (via {@link #deleted}), and the unnamed register.
    * Black-hole register ('_') discards the text silently.
    */
   static void recordYank(char bufid, String text) {
      if (null == text)
         return;
      if ('_' == bufid)
         return;
      if ('*' == bufid || '+' == bufid) {
         delbuffer.writeClipboard(text);
         registerState.setUnnamed(text, false);
         return;
      }
      deleted(bufid, text);
      registerState.setUnnamed(text, false);
   }

   /**
    * Record a linewise yank. Updates the addressed register, the
    * numbered ring (via {@link #deleted}), and the unnamed register.
    * Black-hole register ('_') discards the content silently.
    */
   static void recordYank(char bufid, ArrayList<String> lines) {
      if (null == lines)
         return;
      if ('_' == bufid)
         return;
      if ('*' == bufid || '+' == bufid) {
         delbuffer.writeClipboard(CircBuffer.myToString(lines));
         registerState.setUnnamed(lines, true);
         return;
      }
      deleted(bufid, lines);
      registerState.setUnnamed(lines, true);
   }

   /**
    * Record a charwise delete. Updates the addressed register, the
    * numbered ring (via {@link #deleted}), the unnamed register, and
    * the small-delete register ("-).
    * Black-hole register ('_') discards the text silently.
    */
   static void recordDelete(char bufid, String text) {
      if (null == text)
         return;
      if ('_' == bufid)
         return;
      if ('*' == bufid || '+' == bufid) {
         delbuffer.writeClipboard(text);
         registerState.setUnnamed(text, false);
         registerState.setSmallDelete(text);
         return;
      }
      deleted(bufid, text);
      registerState.setUnnamed(text, false);
      registerState.setSmallDelete(text);
   }

   /**
    * Record a linewise delete. Updates the addressed register, the
    * numbered ring (via {@link #deleted}), and the unnamed register.
    * The small-delete register is not touched (vim semantics: "-
    * only holds sub-line deletes).
    * Black-hole register ('_') discards the content silently.
    */
   static void recordDelete(char bufid, ArrayList<String> lines) {
      if (null == lines)
         return;
      if ('_' == bufid)
         return;
      if ('*' == bufid || '+' == bufid) {
         delbuffer.writeClipboard(CircBuffer.myToString(lines));
         registerState.setUnnamed(lines, true);
         return;
      }
      deleted(bufid, lines);
      registerState.setUnnamed(lines, true);
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
            bufo = registerState.getNamed(bufid);
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
         registerState.putNamed(bufid, bufo);
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
            bufo = registerState.getNamed(bufid);
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
         registerState.putNamed(bufid, bufo);
      }
   }

   static Object getbuf(char id) {
      //trace("vic.getbuf: bufid = " + id);
      if (id == '"')
         return registerState.getUnnamed();
      if (id == '-')
         return registerState.getSmallDelete();
      if (id == '_')
         return null;
      if (id == '*' || id == '+')
         return delbuffer.readClipboard();
      if (id >= 'A' && id <= 'Z')
         id = (char) (id + ('a' - 'A'));

      return id >= '0' && id <= '9'
             ? delbuffer.get(id - '0')
               : registerState.getNamed(id);

      //trace("getbuf returning " + retval + " class " + retval.getClass().toString());
      //return retval;
   }


//   private static class CircBuffer implements Transferable,ClipboardOwner
   public abstract static class CircBuffer {
      private Object[] buf;
      private int index;

      public abstract void setclip();

      /** Read text from the system clipboard; returns null if unavailable. */
      public String readClipboard() {
         return null;
      }

      /** Write text to the system clipboard. Default no-op. */
      public void writeClipboard(String text) {
      }

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

   /**
    * Build a summary of register contents for the :registers command.
    * Format matches vim: one line per non-empty register showing
    * type (c=charwise, l=linewise) and truncated content.
    */
   static String getRegisterSummary() {
      StringBuilder sb = new StringBuilder(256);
      sb.append("--- Registers ---\n");
      appendRegLine(sb, '"', registerState.getUnnamed(),
         registerState.isUnnamedLinewise());
      for (int i = 0; i <= 9; i++) {
         Object val = delbuffer.get(i);
         if (val != null)
            appendRegLine(sb, (char) ('0' + i), val,
               val instanceof ArrayList);
      }
      appendRegLine(sb, '-', registerState.getSmallDelete(), false);
      for (char c = 'a'; c <= 'z'; c++) {
         Object val = registerState.getNamed(c);
         if (val != null)
            appendRegLine(sb, c, val, val instanceof ArrayList);
      }
      String clip = delbuffer.readClipboard();
      if (clip != null && !clip.isEmpty())
         appendRegLine(sb, '*', clip, false);
      return sb.toString();
   }

   private static void appendRegLine(StringBuilder sb, char id,
         Object value, boolean linewise) {
      if (value == null)
         return;
      String text = CircBuffer.myToString(value);
      if (text.isEmpty())
         return;
      sb.append('"').append(id).append("  ")
        .append(linewise ? 'l' : 'c').append("  ");
      // Truncate long values for display
      if (text.length() > 60)
         sb.append(text, 0, 57).append("...");
      else
         sb.append(text);
      if (!text.endsWith("\n"))
         sb.append('\n');
   }

}
