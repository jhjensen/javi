package javi.awt;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;

import javi.ClassConverter;
import javi.Command;
import javi.DirEdit;
import javi.FileDescriptor;
import javi.ShellManager;
import javi.FileProperties;
import javi.FvContext;
import javi.InputException;
import javi.IoConverter;
import javi.PosListList;
import javi.Rgroup;
import javi.TextEdit;
import javi.UI;
import javi.View;

//import static history.Tools.trace;

public final class AwtFontList extends TextEdit<FontEntry> {

   private static final long serialVersionUID = 1;

   private static String monoFontName = defaultMonoName();

   private static String defaultMonoName() {
      String os = System.getProperty("os.name", "").toLowerCase();
      if (os.contains("mac"))
         return "Menlo";
      if (os.contains("win"))
         return "Consolas";
      return "DejaVu Sans Mono";
   }

   public static void restoreState(java.io.ObjectInputStream is) throws
         ClassNotFoundException, IOException {
      init();
      FontEntry fe = (FontEntry) is.readObject();
      inst.changeElementAt(fe, 1);
      try {
         monoFontName = (String) is.readObject();
      } catch (Exception e) {
         // Old session without monoFontName — use default
      }
   }

   public static void saveState(java.io.ObjectOutputStream os) throws
         IOException {
      os.writeObject(inst.at(1));
      os.writeObject(monoFontName);
   }

   private static final class Commands extends Rgroup {
      Commands() {
         final String[] rnames = {
            "",
            "fontsize",
            "fonttype",
            "fontname",
            "fontweight",
            "gotofontlist",
            "monofontname",
         };
         register(rnames);
      }

      public Object doroutine(int rnum, Object arg, int count, int rcount,
            FvContext fvc, boolean dotmode) throws
            InputException {

         View vi = null == fvc
            ? null
            : fvc.vi;

         FontEntry fe = (FontEntry) (null == vi
            ? inst.at(1)
            : (FvContext.getcontext(vi, inst).at()));

         //trace("rnum = " + rnum);
         switch (rnum) {
            case 0:
               return null; // noop

            case 1:
               fe.setSize(oBToFloat(arg));
               if (null != vi)
                  fe.execute(fvc);
               return null;

            case 2:
               fe.setFontType(arg.toString());
               if (null != vi)
                  fe.execute(fvc);
               return null;

            case 3:
               fe.setName(arg.toString());
               if (null != vi)
                  fe.execute(fvc);
               return null;

            case 4:
               fe.setWeight(oBToFloat(arg));
               if (null != vi)
                  fe.execute(fvc);
               return null;

            case 5:
               PosListList.Cmd.gotoList(fvc, getList());
               return null;

            case 6: // monofontname
               if (null == arg || arg.toString().isEmpty()) {
                  UI.reportMessage("monofontname=" + monoFontName);
               } else {
                  monoFontName = arg.toString();
                  UI.reportMessage("monofontname=" + monoFontName);
               }
               return null;

            default:
               throw new RuntimeException("doroutine called with " + rnum);
         }
      }
   }

   private static AwtFontList inst;

   static final String[] typest = {"plain", "bold", "italic", "bold+italic"};

   public static void init() {
      inst = new AwtFontList(new FontParser());
      Command.execCmdList(); // pickup font commands
      FvContext.setOverrideFontProvider(te -> {
         if (te instanceof DirEdit)
            return getMonoFont(null);
         if (ShellManager.getInstance().isShellBuffer(te))
            return getMonoFont(null);
         return null;
      });
   }

   private static FontEntry[] getdefarray() {
      FontEntry[] retval = new FontEntry[1];
      retval[0] = new FontEntry();
      return retval;
   }

   private AwtFontList(FontParser fp) {
      super(fp, getdefarray(), fp.prop); //??? should have seperate parser
      checkpoint(); // first record
      new Commands();
   }

   private static final class FontConverter extends ClassConverter<FontEntry> {
      private static final long serialVersionUID = 1;
      public FontEntry fromString(String str) {
         return new FontEntry(str);
      }
   }

   private static FontConverter converter = new FontConverter();

   private static final class FontParser extends IoConverter<FontEntry> {

      private static final long serialVersionUID = 1;

      public void dispose() throws IOException {
         super.dispose();
         fontArr = null;
      }

      FontParser() {
         super(new FileProperties<FontEntry>(
            FileDescriptor.InternalFd.make("Font List"), converter), false);
      }

      private transient Font[] fontArr;
      private transient int index;

      public FontEntry getnext() { // for 1.5 can return FontList
         if (null == fontArr) {
            fontArr = GraphicsEnvironment
               .getLocalGraphicsEnvironment().getAllFonts();
            index = 0;
         }
         return index >= fontArr.length
                ? null
                : new FontEntry(fontArr[index++], FontEntry.deffontsize);
      }
   }

   public static Font getCurr(View vi) {
      if (null == vi) {
         //trace("font.getCurr default " + inst.at(1));
         return inst.at(1).getFont();
      }

      FontEntry fe = (FontEntry) (FvContext.getcontext(vi, inst).at());
      //trace("font.getCurr " + fe);
      return  fe.getFont();
   }

   /**
    * Returns a monospace font using the configured monoFontName
    * and the current font size.
    */
   public static Font getMonoFont(View vi) {
      Font curr = getCurr(vi);
      return new Font(monoFontName, Font.PLAIN, curr.getSize());
   }

   public static TextEdit getList() {
      return inst;
   }

}
