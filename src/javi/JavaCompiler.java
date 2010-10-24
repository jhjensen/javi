package javi;

import java.io.IOException;
import java.io.File;
import javax.tools.DiagnosticListener;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import javax.tools.StandardJavaFileManager;
import java.util.Arrays;
import java.util.ArrayList;

class JavaCompiler extends Rgroup {
   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";

   JavaCompiler() {
      final String[] rnames = {
         "",
         "comp",             //
         "compa",             //
      };
      register(rnames);
   }


   public Object doroutine(int rnum, Object arg, int count, int rcount,
      FvContext fvc, boolean dotmode) throws IOException, InputException {
//trace("vigroup doroutine rnum = " + rnum );
      switch (rnum) {
         case 1:
            compcommand(fvc);
            return null;
         case 2:
            compacommand();
            return null;
         default:
            throw new RuntimeException("vigroup:default");
      }
   }

   static void compcommand(FvContext fvc)  throws IOException {

      try {
         ArrayList<EditContainer> efs =
            FileList.writeModifiedFiles(".*\\.java");

         int count = efs.size();

         if (count == 0 && fvc != null)  {
            ArrayList<FileDescriptor.LocalFile> flist =
               new ArrayList<FileDescriptor.LocalFile>(1);
            flist.add((FileDescriptor.LocalFile) fvc.edvec.fdes());
            PosListList.Cmd.setErrors(new JavaCompilerInst(flist));

         } else {
            ArrayList<FileDescriptor.LocalFile> flist =
               new ArrayList<FileDescriptor.LocalFile>(count);
            for (EditContainer ef : efs)
               flist.add((FileDescriptor.LocalFile) ef.fdes());
            PosListList.Cmd.setErrors(new JavaCompilerInst(flist));
         }

      } catch (InputException e) {
         throw new RuntimeException("cccommand has bad spec", e);
      }
   }
   private void compacommand() throws IOException, InputException {
      trace("compa");
      FileList.writeModifiedFiles(".*\\.java"); // write out java files
      ArrayList<FileDescriptor.LocalFile> dlist = DirList.getDefault().fileList(
         new GrepFilter(".*\\.java$", false));

      if (dlist.size() == 0)
         UI.reportMessage("no files to compile");
      else
         PosListList.Cmd.setErrors(new JavaCompilerInst(dlist));
   }

   private static class JavaCompilerInst extends PositionIoc implements
      DiagnosticListener<JavaFileObject>  {

      private final ArrayList<FileDescriptor.LocalFile> flist;
      private int errcount = 0;
      private int warncount = 0;

      private static String shortString(
            ArrayList<FileDescriptor.LocalFile>  flisti) {
         StringBuffer sb = new StringBuffer("javac ");
         for (FileDescriptor.LocalFile fd : flisti) {
            sb.append(fd.shortName);
            sb.append(' ');
         }
         return sb.toString();
      }

      JavaCompilerInst(ArrayList<FileDescriptor.LocalFile>  flisti) {
         super(shortString(flisti), null);
         flist = flisti;
      }

      public void report(Diagnostic diagnostic) {
         trace("diagnostic.getSource() " + diagnostic.getSource());
         //trace("diagnostic.getClass() " + diagnostic.getClass());
         Object source = diagnostic.getSource();
         String mess = diagnostic.getMessage(null);
         String src = source == null
            ? mess.split(":")[0]
            : diagnostic.getSource().toString();

         switch(diagnostic.getKind()) {
            case ERROR:
            case NOTE:
            case OTHER:
               errcount++;
               break;
            case MANDATORY_WARNING:
            case WARNING:
               warncount++;
               break;
         }
         mess = mess.replace('\n', ' ');
         addElement(new Position((int) diagnostic.getColumnNumber(),
            (int) diagnostic.getLineNumber(), src, mess));
      }

      protected void preRun() {
         //trace(" array = " + array);
         try {
            javax.tools.JavaCompiler compiler =
               ToolProvider.getSystemJavaCompiler();

            StandardJavaFileManager fileManager =
               compiler.getStandardFileManager(null, null, null);

            //trace("fileManager.getLocation cp" + fileManager.getLocation(javax.tools.StandardLocation.CLASS_PATH));
            fileManager.setLocation(javax.tools.StandardLocation.CLASS_PATH,
                  Arrays.asList(
                  new File("./build"),
                  new File("../lib/rhino1_7R2/js.jar"),
                  new File("../lib/junit3.8.2/junit.jar"),
                  new File("../lib/juniversalchardet-1.0.3.jar"),
                  new File("c:/Progra~1/Java/jdk1.6.0_10/lib/tools.jar")
               ));
            Iterable<? extends JavaFileObject> clist =
               FileDescriptor.getFileObjs(fileManager, flist);

            //String [] options = {"-Xlint"};

            String [] options = {"-d", "build"};
            //String [] options = {"-d", "gbuild/java/build", "-cp",
            //   "gbuild/java/build"};

            boolean success = compiler.getTask(null, fileManager,
               this, Arrays.asList(options), null, clist).call();

            UI.reportError("done compiling " + (success
               ? "successfully"
               : (" with " + errcount + " errors and "
                  + warncount + " warnings")
               ));

            fileManager.close();
         } catch (IOException e) {
            UI.reportError("JavaCompiler caught " + e);
         } catch (IllegalArgumentException e) {
            UI.reportError("" + e.getMessage());
         }
      }
   }
}
