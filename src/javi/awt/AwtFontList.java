package javi.awt;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;

import javi.ClassConverter;
import javi.FileDescriptor;
import javi.FileProperties;
import javi.FvContext;
import javi.InputException;
import javi.IoConverter;
import javi.Rgroup;
import javi.TextEdit;
import javi.View;
import javi.PosListList;
import javi.Command;

//import static history.Tools.trace;

public final class AwtFontList extends TextEdit<FontEntry> {

   public static void restoreState(java.io.ObjectInputStream is) throws
         ClassNotFoundException, IOException {
      init();
      FontEntry fe = (FontEntry) is.readObject();
      inst.changeElementAt(fe, 1);
   }

   public static void saveState(java.io.ObjectOutputStream os) throws
         IOException {
      os.writeObject(inst.at(1));
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
         };
         register(rnames);
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
                  fe.execute(fvc);
               return null;

            case 2:
               fe.setFontType(arg.toString());
               if (vi != null)
                  fe.execute(fvc);
               return null;

            case 3:
               fe.setName(arg.toString());
               if (vi != null)
                  fe.execute(fvc);
               return null;

            case 4:
               fe.setWeight(oBToFloat(arg));
               if (vi != null)
                  fe.execute(fvc);
               return null;

            case 5:
               PosListList.Cmd.gotoList(fvc, getList());
               return null;

            default:
               throw new RuntimeException("doroutine called with " + rnum);
         }
      }
   }

   private static AwtFontList inst;

   static final String [] typest = {"plain", "bold", "italic", "bold+italic"};

   public static void init() {
      inst = new AwtFontList(new FontParser());
      Command.execCmdList(); // pickup font commands
   }

   private static FontEntry [] getdefarray() {
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

   public static Font getCurr(View vi) {
      if (vi == null) {
         //trace("font.getCurr default " + inst.at(1));
         return inst.at(1).getFont();
      }

      FontEntry fe = (FontEntry) (FvContext.getcontext(vi, inst).at());
      //trace("font.getCurr " + fe);
      return  fe.getFont();
   }

   public static TextEdit getList() {
      return inst;
   }

}
