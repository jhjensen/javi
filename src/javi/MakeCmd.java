package javi;

import java.io.IOException;
import java.util.ArrayList;

import history.Tools;
import static history.Tools.trace;

final class MakeCmd extends Rgroup {

   MakeCmd() {
      final String[] rnames = {
         "",
         "cc",             //
         "mk",
         //  "asm",             //
      };
      register(rnames);
   }

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws IOException {
      switch (rnum) {
         case 1:
            cccommand(fvc);
            return null;
         case 2:
            mkcommand(fvc);
            return null;
//      case 2: asmcommand(fvc); return null;
         default:
            throw new RuntimeException("vigroup:default");
      }
   }

   private static String l2String(ArrayList<EditContainer> efs, String alt) {
      StringBuilder newstr = new StringBuilder(efs.size() * 20);

      for (EditContainer ef : efs)
         if (!ef.getName().endsWith("h")) {
            newstr.append(' ');
            newstr.append(ef.getName());
         }
      if (0 == newstr.length())  {
         newstr.append(' ');
         newstr.append(alt);
      }
      return newstr.toString().replace('\\', '/');
   }

   void cccommand(FvContext fvc)  throws IOException {

      try {
         ArrayList<EditContainer> efs =
            FileList.writeModifiedFiles(".*\\.((cpp)|[hc]|(as))");
         String files2 = l2String(efs, fvc.edvec.getName());
         PosListList.Cmd.setErrors(new GccInst("",
            "d:\\cygwin\\bin\\bash -c  \"gcc -Wall -S "
            + " -DUSESPINE -DETI_ADDED -DSTANDARD_OVERHEAD "
            + files2 + "\" ", false));
      } catch (InputException e) {
         throw new RuntimeException("cccommand has bad spec", e);
      }
   }

   static void mkcommand(FvContext fvc)  throws IOException {

      try {
         FileList.writeModifiedFiles(".*");
         String files = fvc.edvec.getName().replace('\\', '/');
         //String[]  cmd = {"ssh", "jjensen@nowind3",
//                 " . .profile ; cd sidewinder/I6/src ;perl make.pl " + files};
//                 " . .profile ; cd sidewinder/src ;perl make.pl " + files};
//               String cmd =System.getProperties().getProperty("java.javi.makecmd",perl make.pl"
//                   "C:\\Progra~1\\SourceGear\\DiffMerge\\DiffMerge.exe ");
//       String[] cmd = {"c:\\cygwin\\bin\\perl","make.pl" , files};
         String[] cmd = {"perl", "make.pl", files};

         PosListList.Cmd.setErrors(new PositionIoc(
            "mk " + files, Tools.runcmd(cmd)));

      } catch (InputException e) {
         throw new RuntimeException("cccommand has bad spec", e);
      }
   }

   void asmcommand(FvContext fvc)  throws IOException {

      try {
         ArrayList<EditContainer> efs = FileList.writeModifiedFiles(
            ".*\\.((as)|(asm))");
         PosListList.Cmd.setErrors(new GccInst(l2String(efs,
            fvc.edvec.getName()), "c:\\v8\\v8asm.exe ", true));
      } catch (InputException e) {
         throw new RuntimeException("cccommand has bad spec", e);
      }
   }

   private static final class GccInst extends PositionIoc {


      GccInst(String filesi, String comstringi, boolean asmflagi) throws
            IOException {
         super("gcc " +  comstringi + filesi,
            Tools.runcmd(comstringi + filesi));
         String comstring = comstringi + filesi;
         trace(comstring);
      }
   }
}
