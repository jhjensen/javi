package javi;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FontList extends TextEdit<FontEntry> {

static void restoreState(java.io.ObjectInputStream is) throws ClassNotFoundException,java.io.IOException {
   init();
   FontEntry fe = (FontEntry) is.readObject();
   inst.changeElementAt (fe,1);
}

static void saveState(java.io.ObjectOutputStream os) throws java.io.IOException {
   os.writeObject(inst.at(1));
}

private Float OToFloat(Object str) throws InputException {
  if (str==null)
     throw new InputException("command needs decimal number");
  try { 
    return Float.valueOf(str.toString().trim());
  } catch (NumberFormatException e) {
     throw new InputException("command needs decimal number",e);
  }
}

private int OToInt(Object str) throws InputException {
  if (str==null)
     throw new InputException("command needs decimal number");
  try { 
    return Integer.parseInt(str.toString().trim());
  } catch (NumberFormatException e) {
     throw new InputException("command needs decimal number",e);
  }
}

private class Commands extends Rgroup {
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
      register (rnames);
   }
   public Object doroutine(int rnum,Object arg,int count,int rcount,FvContext fvc,
        boolean dotmode) throws InputException,ExitException {
       //trace("rnum = " + rnum);
       switch (rnum) {
         case 0: return null; // noop
         case 1: setFontSize(OToFloat(arg),fvc==null ? null : fvc.vi); return null;
         case 2: setFontType((String)arg,fvc==null ? null : fvc.vi); return null;
                    
         case 3: setFontName(arg.toString(),fvc==null ? null : fvc.vi); return null;
         case 4: setFontWeight(OToFloat(arg),fvc==null ? null : fvc.vi); return null;
         case 5:
            setDefaultFontSize(fvc==null ? null : fvc.vi,-1,OToInt(arg));
            return null;
         case 6:
            setDefaultFontSize(fvc==null ? null : fvc.vi,OToInt(arg),-1);
             return null;
         default:
            throw new RuntimeException("doroutine called with " + rnum);
       }
   }

}

private static FontList inst;

static String [] typest = {"plain","bold","italic","bold+italic"};
private static View changedVi;
private static int defwidth;
private static int defheight;

static void init() {
   defwidth=80;
   defheight=80;
 
   inst = new FontList(new FontParser());
}


/*
defheight=53; fontsize= 16; fonttype = Font.PLAIN; fontname = "Courier New";
defheight=43; fontsize= 13; fonttype = Font.PLAIN; fontname = "Verdana";
defheight=50; fontsize= 16; fonttype = Font.BOLD; fontname = "Arial Unicode MS";
//{} not very distinct from[] defheight=61; fontsize= 14; fonttype = Font.BOLD; fontname = "Century Gothic";
defheight=61; fontsize= 14; fonttype = Font.BOLD; fontname = "Century Schoolbook";
//ao are quite difficult to distinguish defheight=56; fontsize= 14; fonttype = Font.BOLD; fontname = "Comic Sans MS";
defheight=65; fontsize= 15; fonttype = Font.PLAIN; fontname = "Courier New";
defheight=61; fontsize= 15; fonttype = Font.BOLD; fontname = "Franklin Gothic Book";
fontsize= 14; fonttype = Font.BOLD; fontname = "Tahoma";
fontsize= 14; fonttype = Font.PLAIN; fontname = "Verdana";
*/
private static FontEntry [] getdefarray(){
 FontEntry[] retval = new FontEntry[1];
 retval[0]= new FontEntry();
 return retval;
}
 
private FontList  (FontParser fp ) {
 super(fp,getdefarray(),fp.prop); //??? should have seperate parser
 checkpoint(); // first record
 new Commands();
}

private static class FontConverter extends ClassConverter<FontEntry> {
   public FontEntry fromString(String str) {
       return new FontEntry(str);
   }
}

static FontConverter converter = new FontConverter();

private static class FontParser extends IoConverter<FontEntry> {

   private static final long serialVersionUID=1;
   public void dispose() { 
	   fontArr=null;
   }

   FontParser() {
       super(new FileProperties(FileDescriptor.InternalFd.make("Font List"),converter),false);
   }
   transient Font [] fontArr;

   transient int index;

   public FontEntry getnext() { // for 1.5 can return FontList
      if (fontArr==null) {
         fontArr = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts(); 
         index =0;
      }
      return index>=fontArr.length
         ? null
         : new FontEntry(fontArr[index++],FontEntry.deffontsize);
   }
}

static String setFontName(String fontname,View vi ) {

   //trace("fontname" + fontname + " vi " + vi);  
   FontEntry fe =(FontEntry)( vi==null
      ? inst.at(1)
      : (FvContext.getcontext(vi,inst).at()));
   String retval =  fe.setName(fontname);
   changedVi = vi;
   //trace("changedVi set");
   MiscCommands.wakeUp();
   return retval;
}

static void setFontType(String type,View vi) {
   //trace("fontname" + type + " fvc " + fvc);  
   FontEntry fe =(FontEntry)( vi==null
      ? inst.at(1)
      : (FvContext.getcontext(vi,inst).at()));
  fe.setFontType(type);
  // trace("changedVi set");
  changedVi = vi;
  MiscCommands.wakeUp();
}

static Float setFontSize(Float size,View vi){
   //trace("size " + size + " vi " + vi);
   FontEntry fe =(FontEntry)( vi==null
      ? inst.at(1)
     : (FvContext.getcontext(vi,inst).at()));
   Float retval = fe.setSize(size);
   changedVi = vi;
   MiscCommands.wakeUp();
   return retval;
}

static void setFontWeight(Float weight,View vi){
   //trace("weight " + weight + " fvc " + fvc);
   FontEntry fe =(FontEntry)( vi==null
      ? inst.at(1)
     : (FvContext.getcontext(vi,inst).at()));
   fe.setWeight(weight);
   changedVi = vi;
   MiscCommands.wakeUp();
}

static void setFontCurr(View vi) {
   changedVi = vi;
   MiscCommands.wakeUp();
}

static Font getCurr(View vi) {
   if (vi == null) {
      //trace("font.getCurr default " + inst.at(1));
      return inst.at(1).getFont();
   }

   FontEntry fe = (FontEntry)(FvContext.getcontext(vi,inst).at());
   //trace("font.getCurr " + fe);
   return  fe.getFont();
}

static View updateFont() {
  if (changedVi !=null) {
     View retval = changedVi;
     changedVi = null;
     return retval;
  }
    
  return null;
}

static void setDefaultFontSize(View vi,int width,int height){
  //trace("width " + width + " height " + height + " view = " + vi);
  if (height!=-1)
     defheight=height;
  if (width!=-1)
     defwidth=width;
  if (vi != null) {
     vi.setSizebyChar(defwidth,defheight);
     changedVi = vi;
   }
}

static TextEdit getList() {
   return inst;
}
}

final class FontEntry implements java.io.Serializable{
   private static final long serialVersionUID=1;
   private static Matcher nameReg;
   private static Matcher styleReg;
   private static Matcher sizeReg;
   private static Matcher weightReg;
   private static Matcher postureReg;
   transient private Font font = null;
   private Map<TextAttribute,Object> atmap;

   FontEntry()  {
      makedefault();
   }

   static String deffontname = "SansSerif";
   static Float deffontsize= Float.valueOf(14);
   static Float deffontweight =  TextAttribute.WEIGHT_REGULAR;
   static Float deffontposture = TextAttribute.POSTURE_REGULAR;

   private void makedefault()  {
      atmap = new HashMap<TextAttribute,Object>();
      atmap.put(TextAttribute.WEIGHT,deffontweight);
      atmap.put(TextAttribute.SIZE,deffontsize);
      atmap.put(TextAttribute.FAMILY,deffontname);
      atmap.put(TextAttribute.POSTURE,deffontposture);
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
      atmap = (Map<TextAttribute,Object>)font.getAttributes();
   } 

@SuppressWarnings("unchecked")
   FontEntry(Font fonti,Float fontsize) {
      atmap = (Map<TextAttribute,Object>)fonti.getAttributes();
      atmap.put(TextAttribute.SIZE, fontsize);
   } 

   FontEntry(String str) {
      if ("".equals(str))
         makedefault();
      else {
      
         //trace("FontList.fromString " +  str);
         atmap = new HashMap<TextAttribute,Object>();
  
         nameReg.reset(str);
         atmap.put(TextAttribute.FAMILY,  nameReg.find()
            ?nameReg.group(1)
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
     
            atmap.put(TextAttribute.WEIGHT,weightReg.find()
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
     if (font==null) {
        font = new Font(atmap);
     }
     return font;
  }

  String setName(String fname) {
     String retval = (String)atmap.get(TextAttribute.FAMILY);
     atmap.put(TextAttribute.FAMILY,fname); //??? does put do anything?
     font = null;
     return retval;
  }
  void setFontType(String type) {
  //trace("type = " + type);
     Object weight = 
        "plain".equals (type)
           ? TextAttribute.WEIGHT_REGULAR
        : "demi".equals (type)
           ? TextAttribute.WEIGHT_DEMIBOLD
        : "bold".equals (type)
           ? TextAttribute.WEIGHT_BOLD
           : atmap.get(TextAttribute.WEIGHT) ;

     if (weight != null)
        atmap.put(TextAttribute.WEIGHT,weight);

     atmap.put(TextAttribute.POSTURE,
        "italic".equals (type)
           ?TextAttribute.POSTURE_OBLIQUE
           :TextAttribute.POSTURE_REGULAR
     );
     font = null;
   }

   Float setSize(Float size) {
     Float retval = (Float)atmap.get(TextAttribute.SIZE);
     atmap.put(TextAttribute.SIZE,size);
     font = null;
     return retval;
  }

   void setWeight(Float size) {
     atmap.put(TextAttribute.WEIGHT,size);
     font = null;
  }
static
{
   nameReg  = Pattern.compile(" *(?:name\\=)?([^,\\]]+)[,\\] $]").matcher("");
   styleReg  = Pattern.compile(" *style\\=(\\w+)[,\\] $]").matcher("");
   sizeReg  = Pattern.compile(" *size\\=([0-9.]+)[,\\] $]").matcher("");
   weightReg = Pattern.compile(" *weight\\=([0-9.]+)[ ,\\]$]").matcher("");
   postureReg = Pattern.compile(" *posture\\=(\\-?[0-9.]+)(($)|([ ,\\]]))").matcher("");
}

}

