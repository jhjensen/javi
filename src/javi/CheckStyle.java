package javi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import history.Tools;

final class CheckStyle extends Rgroup {
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

         cmd.add(0, "./cstyle");
         cmd.add(0, "perl");
         PosListList.Cmd.setErrors(new CheckStyleInst(cmd));
      } catch (InputException e) {
         throw new RuntimeException("cccommand has bad spec", e);
      }
   }

   private void cstylea() throws IOException, InputException {
      FileList.writeModifiedFiles(".*\\.java");  // write out java files

      ArrayList<FileDescriptor.LocalFile> fdlist =
         DirList.getDefault().fileList(
            new GrepFilter(".*\\.java$", false));


      //trace("dlist = " + dlist);
      if (fdlist.size() == 0)
         UI.reportMessage("no files to compile");
      else  {
         ArrayList<String> dlist = new ArrayList<String>(fdlist.size());
         dlist.add(0, "cstyle");
         dlist.add(0, "perl");

         for (FileDescriptor fd : fdlist)
            dlist.add(fd.shortName);

         //List<String> cmd =
         //   new LinkedList<String>(java.util.Arrays.asList(dlist));

         PosListList.Cmd.setErrors(new CheckStyleInst(dlist));
      }
   }

   private static final class CheckStyleInst extends PositionIoc {

      CheckStyleInst(List<String> filename) throws IOException {
         super("checkstyle", Tools.runcmd(filename));
      }
   }
}
