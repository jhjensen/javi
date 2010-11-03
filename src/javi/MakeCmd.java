package javi;

import java.io.IOException;
import java.util.ArrayList;

class MakeCmd extends Rgroup {
   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";

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
      StringBuilder newstr = new StringBuilder();

      for (EditContainer ef : efs)
         if (!ef.getName().endsWith("h")) {
            newstr.append(' ');
            newstr.append(ef.getName());
         }
      if (newstr.length() == 0)  {
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

   void mkcommand(FvContext fvc)  throws IOException {

      try {
         FileList.writeModifiedFiles(".*");
         String files = fvc.edvec.getName().replace('\\', '/');
         //String[]  cmd = {"ssh", "jjensen@nowind3",
//                 " . .profile ; cd sidewinder/I6/src ;perl make.pl " + files};
//                 " . .profile ; cd sidewinder/src ;perl make.pl " + files};
//               String cmd =System.getProperties().getProperty("java.javi.makecmd",perl make.pl"
//                   "C:\\Progra~1\\SourceGear\\DiffMerge\\DiffMerge.exe ");
//       String[] cmd = {"c:\\cygwin\\bin\\perl","make.pl" , files};
         String[] cmd = {"perl", "make.pl" , files};

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

   private static class GccInst extends PositionIoc {

      private boolean asmflag = false;

      static Position asmparse(String line) {
         trace("parsing line + " + line);
         String file;
         String comment;
         int y;
         int pos = line.indexOf('(', 3); // three skips over any drive desc
         if (pos == -1) {
            return null;
         } else {
            try {
               file = line.substring(0, pos);
               line = line.substring(pos + 1, line.length());
               pos = line.indexOf(')');
               y = Integer.parseInt(line.substring(0, pos));
               comment = line.substring(pos + 3, line.length());
               //trace("comment = " + comment);
               return new Position(0, y, file, comment);
            } catch (Throwable e) {
               return null;
            }
         }
      }

      private String pline;
      public Position fromString(String line) {
         if (line.length() == 0)
            return null;
         if (asmflag)
            return asmparse(line);
         trace("parsing line len =  " + line.length() + " line "  + line);
         if (line.startsWith("In file included")) {
            pline = line;
            return null;
         }

         if (line.startsWith("          ")) {
            pline += line;
            return null;
         }

         int pos = line.indexOf(':', 3); // three skips over any drive desc
         if (pos == -1)
            return null;

         String file = line.substring(0, pos);
         line = line.substring(pos + 1, line.length());
         pos = line.indexOf(':');
         int y;
         try {
            y = Integer.parseInt(line.substring(0, pos).trim());
         } catch (Exception e) {
            y = 1;
            if (!line.substring(0, pos).trim().startsWith("In function"))
               trace("gcc.parseline caught " + e);
            pline = null;
            return null;
         }


         String comment = line.substring(pos + 1, line.length());
         if (pline != null) {
            comment += pline;
            pline = null;
         }

         if (comment.length() > 100)
            comment = comment.substring(0, 100);
         int x = 0;
         return new Position(x, y, file, comment);
      }

      GccInst(String filesi, String comstringi, boolean asmflagi) throws
            IOException {
         super("gcc " +  comstringi + filesi,
            Tools.runcmd(comstringi + filesi));
         asmflag = asmflagi;
         String comstring = comstringi + filesi;
         trace(comstring);
      }
   }
}
