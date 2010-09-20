package javi;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;

final class FontList extends TextEdit<FontEntry> {

   static void restoreState(java.io.ObjectInputStream is) throws
         ClassNotFoundException, IOException {
      init();
      FontEntry fe = (FontEntry) is.readObject();
      inst.changeElementAt(fe, 1);
   }

   static void saveState(java.io.ObjectOutputStream os) throws IOException {
      os.writeObject(inst.at(1));
   }

   private static class Commands extends Rgroup {
      Commands() {
         final String[] rnames = {
            "",
            "fontsize",
            "fonttype",
            "fontname",
            "fontweight",
            "lines", // 5
            "setwidth" ,
         };
         register(rnames);
      }

      private Float oBToFloat(Object str) throws InputException {
         if (str == null)
            throw new InputException("command needs decimal number");
         try {
            return Float.valueOf(str.toString().trim());
         } catch (NumberFormatException e) {
            throw new InputException("command needs decimal number", e);
         }
      }

      private int oBToInt(Object str) throws InputException {
         if (str == null)
            throw new InputException("command needs decimal number");
         try {
            return Integer.parseInt(str.toString().trim());
         } catch (NumberFormatException e) {
            throw new InputException("command needs decimal number", e);
         }
      }

      public Object doroutine(int rnum, Object arg, int count, int rcount,
            FvContext fvc, boolean dotmode) throws
            InputException {

         View vi = fvc == null
            ? null
            : fvc.vi;

         FontEntry fe = (FontEntry) (vi == null
            ? inst.at(1)
            : (FvContext.getcontext(vi, inst).at()));

         //trace("rnum = " + rnum);
         switch (rnum) {
            case 0:
               return null; // noop

            case 1:
               fe.setSize(oBToFloat(arg));
               if (vi != null)
                  UI.fontChange(fe.getFont(), vi);
               return null;

            case 2:
               fe.setFontType(arg.toString());
               if (vi != null)
                  UI.fontChange(fe.getFont(), vi);
               return null;

            case 3:
               fe.setName(arg.toString());
               if (vi != null)
                  UI.fontChange(fe.getFont(), vi);
               return null;

            case 4:
               fe.setWeight(oBToFloat(arg));
               if (vi != null)
                  UI.fontChange(fe.getFont(), vi);
               return null;

            case 5:
               inst.setDefaultFontSize(-1, oBToInt(arg));
               return null;

            case 6:
               inst.setDefaultFontSize(oBToInt(arg), -1);
               return null;

            default:
               throw new RuntimeException("doroutine called with " + rnum);
         }
      }
   }

   private static FontList inst;

   static final String [] typest = {"plain", "bold", "italic", "bold+italic"};
   private static int defwidth;
   private static int defheight;

   static void init() {
      defwidth = 80;
      defheight = 80;

      inst = new FontList(new FontParser());
   }

   private static FontEntry [] getdefarray() {
      FontEntry[] retval = new FontEntry[1];
      retval[0] = new FontEntry();
      return retval;
   }

   private FontList(FontParser fp) {
      super(fp, getdefarray(), fp.prop); //??? should have seperate parser
      checkpoint(); // first record
      new Commands();
   }

   private static class FontConverter extends ClassConverter<FontEntry> {
      public FontEntry fromString(String str) {
         return new FontEntry(str);
      }
   }

   private static FontConverter converter = new FontConverter();

   private static class FontParser extends IoConverter<FontEntry> {

      private static final long serialVersionUID = 1;
      public void dispose() throws IOException {
         super.dispose();
         fontArr = null;
      }

      FontParser() {
         super(new FileProperties(
            FileDescriptor.InternalFd.make("Font List"), converter), false);
      }
      private transient Font [] fontArr;

      private transient int index;

      public FontEntry getnext() { // for 1.5 can return FontList
         if (fontArr == null) {
            fontArr = GraphicsEnvironment
               .getLocalGraphicsEnvironment().getAllFonts();
            index = 0;
         }
         return index >= fontArr.length
                ? null
                : new FontEntry(fontArr[index++], FontEntry.deffontsize);
      }
   }

   static Font getCurr(View vi) {
      if (vi == null) {
         //trace("font.getCurr default " + inst.at(1));
         return inst.at(1).getFont();
      }

      FontEntry fe = (FontEntry) (FvContext.getcontext(vi, inst).at());
      //trace("font.getCurr " + fe);
      return  fe.getFont();
   }

   static int getHeight() {
      return defheight;
   }

   static int getWidth() {
      return defwidth;
   }

   static void setDefaultFontSize(int width, int height) {
      //trace("width " + width + " height " + height);
      if (height != -1)
         defheight = height;
      if (width != -1)
         defwidth = width;
   }

   static TextEdit getList() {
      return inst;
   }
}

final class FontEntry implements java.io.Serializable {
   private static final long serialVersionUID = 1;
   private static Matcher nameReg;
   private static Matcher styleReg;
   private static Matcher sizeReg;
   private static Matcher weightReg;
   private static Matcher postureReg;
   private transient Font font = null;
   private Map<TextAttribute, Object> atmap;

   FontEntry()  {
      makedefault();
   }

   private static String deffontname = "SansSerif";
   static final Float deffontsize = Float.valueOf(14);
   private static Float deffontweight =  TextAttribute.WEIGHT_REGULAR;
   private static Float deffontposture = TextAttribute.POSTURE_REGULAR;

   private void makedefault()  {
      atmap = new HashMap<TextAttribute, Object>();
      atmap.put(TextAttribute.WEIGHT, deffontweight);
      atmap.put(TextAttribute.SIZE, deffontsize);
      atmap.put(TextAttribute.FAMILY, deffontname);
      atmap.put(TextAttribute.POSTURE, deffontposture);
   }

   public String toString() {
//      for (Iterator cx = atmap.values().iterator() ; cx.hasNext() ;)  {
//         Object val = cx.next();
//     //       trace("new font attr = " + key + " val  = " + atmap.get(key));
//             trace("new font val  = " + val);
//      }
      return
         "name=" + atmap.get(TextAttribute.FAMILY)
         + ",weight=" + atmap.get(TextAttribute.WEIGHT)
         + ",size=" + atmap.get(TextAttribute.SIZE)
         + ",posture=" +  atmap.get(TextAttribute.POSTURE);
   }

   @SuppressWarnings("unchecked")
   FontEntry(Font fonti) {
      font = fonti;
      atmap = (Map<TextAttribute, Object>) font.getAttributes();
   }

   @SuppressWarnings("unchecked")
   FontEntry(Font fonti, Float fontsize) {
      atmap = (Map<TextAttribute, Object>) fonti.getAttributes();
      atmap.put(TextAttribute.SIZE, fontsize);
   }

   FontEntry(String str) {
      if ("".equals(str))
         makedefault();
      else {

         //trace("FontList.fromString " +  str);
         atmap = new HashMap<TextAttribute, Object>();

         nameReg.reset(str);
         atmap.put(TextAttribute.FAMILY,  nameReg.find()
            ? nameReg.group(1)
            : deffontname);

         styleReg.reset(str);
         sizeReg.reset(str);

         atmap.put(TextAttribute.SIZE,  sizeReg.find()
            ? Float.valueOf(sizeReg.group(1))
            : deffontsize);

         if (styleReg.find())
            setFontType(styleReg.group(1));
         else {

            weightReg.reset(str);
            postureReg.reset(str);

            atmap.put(TextAttribute.WEIGHT, weightReg.find()
               ? Float.valueOf(weightReg.group(1))
               : deffontweight);

            atmap.put(TextAttribute.POSTURE,  postureReg.find()
               ? Float.valueOf(postureReg.group(1))
               : deffontposture);

         }
      }

      //trace("returning " + this);
   }

   Font getFont() {
      if (font == null) {
         font = new Font(atmap);
      }
      return font;
   }

   String setName(String fname) {
      String retval = (String) atmap.get(TextAttribute.FAMILY);
      atmap.put(TextAttribute.FAMILY, fname); //??? does put do anything?
      font = null;
      return retval;
   }
   void setFontType(String type) {
      //trace("type = " + type);
      Object weight =
            "plain".equals(type)
         ? TextAttribute.WEIGHT_REGULAR
            : "demi".equals(type)
         ? TextAttribute.WEIGHT_DEMIBOLD
            : "bold".equals(type)
         ? TextAttribute.WEIGHT_BOLD
         : atmap.get(TextAttribute.WEIGHT);

      if (weight != null)
         atmap.put(TextAttribute.WEIGHT, weight);

      atmap.put(TextAttribute.POSTURE,
         "italic".equals(type)
            ? TextAttribute.POSTURE_OBLIQUE
            : TextAttribute.POSTURE_REGULAR);
      font = null;
   }

   Float setSize(Float size) {
      Float retval = (Float) atmap.get(TextAttribute.SIZE);
      atmap.put(TextAttribute.SIZE, size);
      font = null;
      return retval;
   }

   void setWeight(Float size) {
      atmap.put(TextAttribute.WEIGHT, size);
      font = null;
   }
   static {
      nameReg  = Pattern.compile(
         " *(?:name\\=)?([^,\\]]+)[,\\] $]").matcher("");
      styleReg  = Pattern.compile(" *style\\=(\\w+)[,\\] $]").matcher("");
      sizeReg  = Pattern.compile(" *size\\=([0-9.]+)[,\\] $]").matcher("");
      weightReg = Pattern.compile(" *weight\\=([0-9.]+)[ ,\\]$]").matcher("");
      postureReg = Pattern.compile(
         " *posture\\=(\\-?[0-9.]+)(($)|([ ,\\]]))").matcher("");
   }

}
