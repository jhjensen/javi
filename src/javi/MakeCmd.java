package javi;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
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

      ArrayList<EditContainer> efs =
         FileList.writeModifiedFiles(".*\\.((cpp)|[hc]|(as))");
      String files2 = l2String(efs, fvc.edvec.getName());
      PosListList.Cmd.setErrors(new GccInst("",
         //"d:\\cygwin\\bin\\bash -c  \"gcc -Wall -S "
         "bash -c  \"gcc -Wall -S "
         + " -DUSESPINE -DETI_ADDED -DSTANDARD_OVERHEAD "
         + files2 + "\" ", false));
   }


   static final class PositionCmd extends PositionIoc {

      private Process proc;
      private static final long serialVersionUID = 1;

      static PositionCmd make(String name, String ... cmd) throws IOException {
         Process proc = Tools.iocmd(cmd);
         BufferedReader buf = new BufferedReader(
            new InputStreamReader(proc.getInputStream(), "UTF-8"));
         return new PositionCmd(name, proc, buf);
      }

      private PositionCmd(String name, Process proci, BufferedReader buf) throws
            IOException {
         super(name, buf, pconverter);
         proc = proci;
      }

      void stopIo() {
         if (proc != null) {
            proc.destroy();
            proc = null;
         }
         super.stopIo();
      }
   }

   static void mkcommand(FvContext fvc)  throws IOException {
      FileList.writeModifiedFiles(".*");
      String file = fvc.edvec.getName().replace('\\', '/');
      //String[]  cmd = {"ssh", "jjensen@nowind3",
//                 " . .profile ; cd sidewinder/I6/src ;perl make.pl " + files};
//                 " . .profile ; cd sidewinder/src ;perl make.pl " + files};
//               String cmd =System.getProperties().getProperty("java.javi.makecmd",perl make.pl"
//                   "C:\\Progra~1\\SourceGear\\DiffMerge\\DiffMerge.exe ");
//      String[] cmd = {"c:\\cygwin\\bin\\perl", "make.pl" , file};
      String[] cmd = {"perl", "make.pl" , file};

      PosListList.Cmd.setErrors(PositionCmd.make("mk " + file, cmd));

   }

   void asmcommand(FvContext fvc)  throws IOException {

      ArrayList<EditContainer> efs = FileList.writeModifiedFiles(
         ".*\\.((as)|(asm))");
      PosListList.Cmd.setErrors(new GccInst(l2String(efs,
         fvc.edvec.getName()), "c:\\v8\\v8asm.exe ", true));
   }

   private static final class GccInst extends PositionIoc {


      private static final long serialVersionUID = 1;

      GccInst(String filesi, String comstringi, boolean asmflagi) throws
            IOException {
         super("gcc " +  comstringi + filesi,
            Tools.runcmd(comstringi + filesi), pconverter);
         String comstring = comstringi + filesi;
         trace(comstring);
      }
   }
}
