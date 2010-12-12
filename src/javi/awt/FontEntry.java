package javi.awt;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javi.FvContext;
import javi.FvExecute;
import javi.View;



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
      atmap = new HashMap<TextAttribute, Object>(4);
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
      if (0 == str.length())
         makedefault();
      else {

         atmap = new HashMap<TextAttribute, Object>(4);

         atmap.put(TextAttribute.FAMILY,  nameReg.reset(str).find()
            ? nameReg.group(1)
            : deffontname);

         atmap.put(TextAttribute.SIZE,  sizeReg.reset(str).find()
            ? Float.valueOf(sizeReg.group(1))
            : deffontsize);

         if (styleReg.reset(str).find())
            setFontType(styleReg.group(1));
         else {

            atmap.put(TextAttribute.WEIGHT, weightReg.reset(str).find()
               ? Float.valueOf(weightReg.group(1))
               : deffontweight);

            atmap.put(TextAttribute.POSTURE,  postureReg.reset(str).find()
               ? Float.valueOf(postureReg.group(1))
               : deffontposture);

         }
      }
   }

   Font getFont() {
      if (null == font) {
         font = new Font(atmap);
      }
      return font;
   }

   void setName(String fname) {
      atmap.put(TextAttribute.FAMILY, fname); //??? does put do anything?
      font = null;
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

      if (null != weight)
         atmap.put(TextAttribute.WEIGHT, weight);

      atmap.put(TextAttribute.POSTURE,
         "italic".equals(type)
            ? TextAttribute.POSTURE_OBLIQUE
            : TextAttribute.POSTURE_REGULAR);
      font = null;
   }

   void setSize(Float size) {
      atmap.put(TextAttribute.SIZE, size);
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
