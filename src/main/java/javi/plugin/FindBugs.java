package javi.plugin;

import static history.Tools.trace;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import javi.FvContext;
import javi.Plugin;
import javi.Position;
import javi.BufInIoc;
import javi.Rgroup;
import javi.ClassConverter;
import javi.FileProperties;
import javi.FileDescriptor;

public final class FindBugs extends Rgroup implements Plugin {

   public static final String pluginInfo = "findbug command";

   static {
      //trace("reached static initializer of findbugs");
      new FindBugs();
   }

   FindBugs() {
      final String[] rnames = {
         "",
         "findbug",             //
      };
      register(rnames);
   }

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws IOException {
      trace("rnum = " + rnum);
      switch (rnum) {
         case 1:

            javi.PosListList.Cmd.setErrors(new FindBugRunner("."));
            return null;
         default:
            throw new RuntimeException("vigroup:default");
      }
   }

   static final class FindBugRunner extends BufInIoc<Position> {
      private static final long serialVersionUID = 1;

      private static final FindBugConv converter = new FindBugConv();
      private static final class FindBugConv extends ClassConverter<Position> {
         private static final long serialVersionUID = 1;
         public Position fromString(String line) {

            trace("parsing len =  " + line.length() + " line: "  + line);
            if (0 == line.length()) {
               return Position.badpos;
            }

            int pos = line.indexOf(':', 3); // three skips over any drive desc
            if (pos == -1) {
               trace("unexpected line:" + line);
               return Position.badpos;
            }

            String file = "src/" + line.substring(0, pos);
//            if (file.startsWith("javi/"))
//               file = file.substring(5, pos);
            line = line.substring(pos + 1, line.length());
            pos = line.indexOf(':');
            int lineno;
            lineno = Integer.parseInt(line.substring(0, pos).trim());
            if (lineno <= 0)
               lineno = 1;

            String comment = line.substring(pos + 1, line.length());
            return new Position(0, lineno, file, comment);
         }
         private static final Position defpos = new Position(0, 0, "", null);
      }

      FindBugRunner(String filename) throws
            FileNotFoundException, java.io.UnsupportedEncodingException {
      //     Process proc = Runtime.getRuntime().exec(cstring);
      //     input = new BufferedReader  (new InputStreamReader(proc.getInputStream()));
      super(
         new FileProperties(
            FileDescriptor.InternalFd.make("findbug"), converter),
            true,
            new BufferedReader(
               new InputStreamReader(new FileInputStream("findout"), "UTF-8")));
          //lib/findbugs-3.0.1/bin/findbugs -emacs -medium -textui -auxclasspath "..:$JAVA_HOME/lib/tools.jar:$JAVA_HOME/jre/lib/ext/RXTXcomm.jar:lib/rhino1_7R3/js.jar:lib/juniversalchardet-1.0.3.jar"  -exclude filter.xml  build > findout

      }

      public Position getnext() {
         //trace("getnext input " + " this " + this);

         String line = getLine();
         return line == null
            ? null
            : converter.fromString(line);
      }
   }

   public static void main(String[] args) {
      try {
         new javi.StreamInterface();

         FindBugRunner gr = (0 == args.length)
            ? new FindBugRunner(".")
            : new FindBugRunner(args[0]);

         for (Position pos; null != (pos = gr.getnext());)
            trace(" pos = " + pos);
      } catch (Exception e) {
         trace(" caught exception " + e);
         e.printStackTrace();
      }
      trace("findbug exits");
   }
}
