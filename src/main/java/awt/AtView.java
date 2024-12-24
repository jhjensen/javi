package javi.awt;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.text.AttributedCharacterIterator;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import javi.DeTabber;

final class AtView implements
      AttributedCharacterIterator, Cloneable, java.io.Serializable {

   private HashMap<Attribute, Object> by;
   private HashMap<Attribute, Object> ly;
   private HashMap<Attribute, Object> byu;
   private HashMap<Attribute, Object> lyu;
   private HashMap<Attribute, Object> bpu;
   private HashMap<Attribute, Object> gpu;
   private HashMap<Attribute, Object> gyu;
   private HashMap<Attribute, Object> gy;

   private String text;
   private int pos;
   private int highStart;
   private int highFinish;
   private boolean emphFlag;
   private int line2start;
   private int olineStart;
   private int olineEnd;
//private AttributedCharacterIterator aci;
//private int aciOffset;
//private int aciEnd;

   private static final long serialVersionUID = 1;

   public String toString() {

      StringBuilder sb = new StringBuilder("pos = ");
      sb.append(pos);
      if (highStart != -1)
         sb.append(" highlight(" + highStart + "," + highFinish + ")");
      if  (emphFlag)
         sb.append(" emphasized");
      if (line2start != Integer.MAX_VALUE)
         sb.append(String.valueOf(line2start));

      if (olineStart != -1) {
         sb.append(" outofline (");
         sb.append(olineStart);
         sb.append(",");
         sb.append(olineEnd);
         sb.append(")");
      }
      return sb.toString();
   }

   void setText(String str) {
      //trace("AtView setText:" + text);
      text = str;
      pos = 0;
      highStart = -1;
      highFinish = -1;
      emphFlag = false;
      olineStart = -1;
      olineEnd = -1;
      line2start = Integer.MAX_VALUE;
      //aci = null;
   }

   String getText() {
      return text;
   }

//void insertAci(AttributedCharacterIterator acii,int offset) {
//   aci = acii;
//   aciOffset = offset;
//   aciEnd = offset + aci.getEndIndex();
//}
//private boolean inAci() {
//   return pos >= aciOffset && pos <= aciEnd;
//}
   void addOlineText(String str, int offset, boolean overwrite) {
      olineStart = offset;
      int slen = str.length();
      olineEnd = offset + slen;
      //trace(" addOlineText str = " + str + " offset = " + offset );
      text =  text.substring(0, offset) + str  + text.substring(
         (overwrite
            ? text.length() < offset + slen
               ? text.length() // gives empty string
               : offset + slen
            : offset
         ), text.length());
   }

   void deTab(int tabstop) {
      //trace("deTab " + tabstop);
      int tabOffset = text.indexOf('\t');
      if (tabOffset != -1) {

         if (line2start != Integer.MAX_VALUE)
            throw new RuntimeException(
               "detabing and multiline display not supported");
         int[] tvals = {highStart, highFinish, olineStart, olineEnd};

         text = DeTabber.deTab(text, tabOffset, tabstop, tvals);
         highStart = tvals[0];
         highFinish = tvals[1];
         olineStart = tvals[2];
         olineEnd = tvals[3];
      }
   }

   void addOlineText(char c, boolean overwrite) {
      text =  text.substring(0, olineEnd) + c  + text.substring(
            (overwrite
                  ? olineEnd  + 1
                  : olineEnd)
            , text.length());
      olineEnd++;
   }

   void setHighlight(int start, int finish) {
      highStart = start;
      highFinish = finish;
      //trace(toString());
   }
   boolean line2(int charnum) {
      //trace("line2 " + charnum);
      if (line2start == charnum)
         return true;
      line2start = charnum;
      return false;
   }
   void emphasize(boolean flag) {
      emphFlag = flag;
   }

   int length() {
      return text.length();
   }

   static final Color background = Color.black;
//new Color(0,0,50);// 32 turns black (0,0,96); (0,0,64); (0,0,77);
   static final Color paraBackground = new Color(0, 50, 0);
   static final Color foreground = Color.green;
//static final Color foreground = new Color(255,255,255);
//new Color(255,255,160);
   static final Color cursorColor  = Color.white;
   static final Color noFile  = new Color(0, 0, 60);
   static final Color unFinished = new Color(75, 15, 15);
   static final Color insertCursor = Color.pink;
   static final Color interFrame = Color.gray;
   static final Color emphasize = Color.red;
//static final Color cursorColor  = Color.cyan;

   private static final Color lightBlue = new Color(0, 0, 128);

   AtView(Font font) {
      //trace("this = " + this + " font = " + font);
      by = new HashMap<Attribute, Object>(3);
      by.put(TextAttribute.FONT, font);
//   temp.put(TextAttribute.CHAR_REPLACEMENT,new ShapeGraphicAttribute(
//     new Rectangle(5,5),ShapeGraphicAttribute.CENTER_BASELINE,true));
      by.put(TextAttribute.FOREGROUND, foreground);
      by.put(TextAttribute.BACKGROUND, background);
      byu = new HashMap<Attribute, Object>(by);
      byu.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_LOW_DOTTED);
      bpu = new HashMap<Attribute, Object>(byu);
      bpu.put(TextAttribute.FOREGROUND, insertCursor);
      ly = new HashMap<Attribute, Object>(by);
      ly.put(TextAttribute.BACKGROUND, lightBlue);
      lyu = new HashMap<Attribute, Object>(ly);
      lyu.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_LOW_DOTTED);
      gpu = new HashMap<Attribute, Object>(bpu);
      gpu.put(TextAttribute.BACKGROUND, paraBackground);
      gyu = new HashMap<Attribute, Object>(byu);
      gyu.put(TextAttribute.BACKGROUND, paraBackground);
      gy = new HashMap<Attribute, Object>(by);
      gy.put(TextAttribute.BACKGROUND, paraBackground);
      text = "";

   }

   public Object clone() {
      throw new RuntimeException("clone unimplemented");
   }

   public char current() {
      if (pos >= text.length())
         return DONE;
      return text.charAt(pos);
   }

//  Gets the character at the current position (as returned by getIndex()).
   public char first() {
      pos =  0;
      return text.charAt(0);
   }

//  Sets the position to getBeginIndex() and returns the character at that position.
   public int getBeginIndex() {
      return 0;
   }

   public int getEndIndex() {
//  return aci == null
//     ? text.length()
//     : text.length() + aci.getEndIndex();
      return text.length();
   }

//  Returns the current index.
   public int getIndex() {
      return pos;
   }

   public char last() {
      throw new RuntimeException("last unimplemented");
   }

   public char next() {
      if (++pos >= text.length()) {
         pos = text.length();
         return DONE;
      }
      //trace("returning " + text.charAt(pos));
      return text.charAt(pos);
   }

   public char previous() {
      throw new RuntimeException("previous unimplemented");
   }

   public char setIndex(int position) {
      pos = position;
      return current();
   }

   public Set<Attribute> getAllAttributeKeys() {
      throw new RuntimeException("getAllAttributeKeys unimplemented");
   }

//         Returns the keys of all attributes defined on the iterator's text range.
   public Object getAttribute(AttributedCharacterIterator.Attribute attribute) {
      //trace("" + attribute);
      return getAttributes().get(attribute);
   }

   public Map<Attribute,  Object> getAttributes() {
      return
         olineEnd == -1
         ? pos  <  highStart
         ? emphFlag ? byu : by
         :  pos < highFinish
         ? emphFlag ? lyu : ly
         : pos < line2start
         ? emphFlag ? byu : by
         : emphFlag ? gyu : gy
         : (pos <  olineStart) || (pos >= olineEnd)
         ? pos < line2start
         ?  byu
         :  gyu
         : pos < line2start
         ? bpu
         : gpu;
      //trace ("this= " + this);
      //trace ("pos = " + pos + " returning " + retval.get(TextAttribute.BACKGROUND));
      //return retval;
   }

// Returns a map with the attributes defined on the current character.
   private static int leastgtpos(int val1, int val2, int pos)  {
      return  (val2 != -1 && val2 < val1 && val2 > pos)
            ? val2
            : val1;
   }

   public int getRunLimit() {
      int retval = leastgtpos(text.length(), olineEnd, pos);
      retval = leastgtpos(retval, highStart, pos);
      retval = leastgtpos(retval, highFinish, pos);
      retval = leastgtpos(retval, olineEnd, pos);
      retval = leastgtpos(retval, olineStart, pos);
      return leastgtpos(retval, line2start, pos);
//trace("grun retval = " + retval + " line2start = " + line2start);
   }

//        Returns the index of the first character following the run with respect to all attributes containing the current character.
   public int getRunLimit(AttributedCharacterIterator.Attribute attribute) {
      if (attribute == TextAttribute.BACKGROUND) {
         int retval = leastgtpos(text.length(), olineEnd, pos);
         retval = leastgtpos(retval, highStart, pos);
         retval = leastgtpos(retval, highFinish, pos);
         return leastgtpos(retval, line2start, pos);
      } else if (attribute == TextAttribute.FOREGROUND) {
         int retval = leastgtpos(text.length(), olineEnd, pos);
         retval = leastgtpos(retval, olineEnd, pos);
         return leastgtpos(retval, olineStart, pos);
      } else
         return text.length();
   }

   public int getRunLimit(Set attributes) {
      throw new RuntimeException("getRunStart unimplemented");
   }

   public int getRunStart() {
      throw new RuntimeException("getRunStart unimplemented");
   }

   public int getRunStart(AttributedCharacterIterator.Attribute attribute) {
      throw new RuntimeException("getRunStart unimplemented");
   }

   public int getRunStart(Set attributes) {
      throw new RuntimeException("getRunStart unimplemented");
   }

}
