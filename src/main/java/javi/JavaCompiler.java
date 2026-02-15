package javi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import javax.tools.DiagnosticListener;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import javax.tools.StandardJavaFileManager;
//import static history.Tools.trace;

final class JavaCompiler extends Rgroup {

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
         case 1 -> compcommand(fvc);
         case 2 -> compacommand();
         default -> throw new RuntimeException("vigroup:default");
      }
      return null;
   }

   private static void compcommand(FvContext fvc)  throws IOException {

      ArrayList<EditContainer> efs = FileList.writeModifiedFiles(".*\\.java");

      int count = efs.size();

      if (0 == count && null != fvc)  {
         ArrayList<FileDescriptor.LocalFile> flist =
            new ArrayList<>(1);
         flist.add((FileDescriptor.LocalFile) fvc.edvec.fdes());
         PosListList.Cmd.setErrors(new JavaCompilerInst(flist));

      } else {
         ArrayList<FileDescriptor.LocalFile> flist =
            new ArrayList<>(count);
         for (EditContainer ef : efs)
            flist.add((FileDescriptor.LocalFile) ef.fdes());
         PosListList.Cmd.setErrors(new JavaCompilerInst(flist));
      }
   }

   private static void compacommand() throws IOException, InputException {
      FileList.writeModifiedFiles(".*\\.java"); // write out java files
      ArrayList<FileDescriptor.LocalFile> dlist = DirList.getDefault().fileList(
         new GrepFilter(".*\\.java$", false));

      if (0 == dlist.size())
         UI.reportMessage("no files to compile");
      else
         PosListList.Cmd.setErrors(new JavaCompilerInst(dlist));
   }

   private static final class JavaCompilerInst extends PositionIoc implements
      DiagnosticListener<JavaFileObject>  {

      private static final long serialVersionUID = 1;
      private final ArrayList<FileDescriptor.LocalFile> flist;
      private int errcount = 0;
      private int warncount = 0;

      private static String shortString(
            ArrayList<FileDescriptor.LocalFile>  flisti) {
         StringBuilder sb = new StringBuilder("javac ");
         for (FileDescriptor.LocalFile fd : flisti) {
            sb.append(fd.shortName);
            sb.append(' ');
         }
         return sb.toString();
      }

      JavaCompilerInst(ArrayList<FileDescriptor.LocalFile>  flisti) {
         super(shortString(flisti), null, pconverter);
         flist = flisti;
      }

      public void report(Diagnostic diagnostic) {
         //trace("diagnostic.getSource() " + diagnostic.getSource() +  " class " + diagnostic.getSource().getClass().toString());
         Object source = diagnostic.getSource();

         String mess = diagnostic.getMessage(null);
         String src = source == null
            ? mess.split(":")[0]
            : source instanceof javax.tools.FileObject fo
               ? fo.getName()
               : diagnostic.getSource().toString();

         switch (diagnostic.getKind()) {
            case ERROR, NOTE, OTHER -> errcount++;
            case MANDATORY_WARNING, WARNING -> warncount++;
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
                  new File("./build/classes/java/main"),
                  new File("lib/rhino-1.7.14.jar"),
                  new File("lib/rxtx-2.1.7.jar"),
                  new File("lib/junit3.8.2/junit.jar"),
                  new File("lib/juniversalchardet-1.0.3.jar"),
                  new File("c:/Progra~1/Java/"
                        + System.getenv("JDK") + "/lib/tools.jar")
               ));
            Iterable<? extends JavaFileObject> clist =
               FileDescriptor.getFileObjs(fileManager, flist);

            //String [] options = {"-Xlint:all"};

            String[] options = {"-d", "build/classes/java/main"};
            //String [] options = {"-d", "gbuild/java/build", "-cp",
             //  "gbuild/java/build","-Xlint"};

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
            UI.reportError(e.getMessage());
         }
      }
   }
}
