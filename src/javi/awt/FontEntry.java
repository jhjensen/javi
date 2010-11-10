package javi.awt;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javi.FvContext;
import javi.View;
import javi.FvExecute;



final class FontEntry implements FvExecute, java.io.Serializable {
   private static final long serialVersionUID = 1;
   private static Matcher nameReg;
   private static Matcher styleReg;
   private static Matcher sizeReg;
   private static Matcher weightReg;
   private static Matcher postureReg;
   private static FontChanger fontc;
   private transient Font font = null;
   private Map<TextAttribute, Object> atmap;

   FontEntry()  {
      makedefault();
   }

   static void init(FontChanger fci) {
      fontc = fci;
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
      if (str.length() == 0)
         makedefault();
      else {

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

   public void execute(FvContext fvc) {
      fontc.setFont(getFont(), fvc.vi);
   }

   abstract static class FontChanger {
      abstract void setFont(Font fv, View vi);
   }
}
