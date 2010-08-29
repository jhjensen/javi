package javi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

class CheckStyle extends Rgroup {
   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";

   CheckStyle() {
      final String[] rnames = {
         "",
         "cstyle",             //
         "cstylea",             //
      };
      register(rnames);
   }

   public Object doroutine(int rnum, Object arg, int count, int rcount,
          FvContext fvc, boolean dotmode) throws
          IOException, InputException {
      //trace("vigroup doroutine rnum = " + rnum );
      switch(rnum) {
         case 1:
            cstyle(fvc);
            return null;
         case 2:
            cstylea();
            return null;
         default:
            throw new RuntimeException("vigroup:default");
      }
   }

   static List<String>  listModFiles(String spec, FvContext fvc) throws
         InputException, IOException {

      ArrayList<EditContainer> efs = FileList.writeModifiedFiles(spec);

      int count = efs.size();
      LinkedList<String> flist = new LinkedList<String>();

      if (count == 0 && fvc != null)  {
         flist.add(fvc.edvec.getName());
      } else {
         for (EditContainer ef : efs)
            flist.add(ef.getName());
      }

      return flist;
   }

   static void cstyle(FvContext fvc)  throws IOException {

      try {
         List<String> cmd =
            new LinkedList<String>(listModFiles(".*\\.java", fvc));

         cmd.add(0, "cstyle");
         cmd.add(0, "bash");
         PosListList.Cmd.setErrors(new CheckStyleInst(cmd));
      } catch (InputException e) {
         throw new RuntimeException("cccommand has bad spec", e);
      }
   }

   private void cstylea() throws IOException, InputException {
      FileList.writeModifiedFiles(".*\\.java");  // write out java files
      String[] dlist =
         FileDescriptor.LocalFile.cwdlist(new GrepFilter(".*\\.java$", false));

      //trace("dlist = " + dlist);
      if (dlist.length == 0)
         UI.reportMessage("no files to compile");
      else  {
         List<String> cmd =
            new LinkedList<String>(java.util.Arrays.asList(dlist));

         cmd.add(0, "../cstyle");
         cmd.add(0, "bash");
         PosListList.Cmd.setErrors(new CheckStyleInst(cmd));
      }
   }
}

class CheckStyleInst extends PositionIoc {

   CheckStyleInst(List<String> filename) throws IOException {
      super("checkstyle", Tools.runcmd(filename));
   }

   public final Position parsefile() {
      String line;
      while (null != (line = getLine())) {
         if (line.length() == 0)
            continue;

         //trace("parsing len =  " + line.length() + " line: "  + line);

         int pos = line.indexOf(':', 3);  // three skips over any drive desc

         if (pos == -1) {
            if (line.equals("Audit done.")
                  ||  line.equals("Starting audit..."))
               continue;
            trace("unexpected line:" + line);
            return Position.badpos;
         }

         String file = line.substring(0, pos);

         line = line.substring(pos + 1, line.length());
         pos = line.indexOf(':');

         if (pos == -1)
            return new Position(0, 0, file, line);

         int lineno = Integer.parseInt(line.substring(0, pos).trim());

         if (lineno <= 0)
            lineno = 1;

         line = line.substring(pos + 1, line.length());
         pos = line.indexOf(':');

         if (pos == -1)
            return new Position(0, lineno, file, line);

         try {
            int charno = Integer.parseInt(line.substring(0, pos).trim()) - 1;
            line = line.substring(pos + 1, line.length());
            if (charno <= 0)
               charno = 0;

            //trace(" returning " + new Position(0, lineno, file, line));
            return new Position(charno, lineno, file, line);
         } catch (NumberFormatException e) {
            return new Position(0, lineno, file ,
               "failed to parse line:" + line);
         }
      }
      return null;
   }
}
