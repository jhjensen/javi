package javi.plugin;

import static history.Tools.trace;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import javi.FvContext;
import javi.Plugin;
import javi.Position;
import javi.PositionIoc;
import javi.Rgroup;

public final class FindBugs extends Rgroup implements Plugin {

   /* Copyright 1996 James Jensen all rights reserved */

   static final String copyright = "Copyright 1996 James Jensen";
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

   static final class FindBugRunner extends PositionIoc {

      FindBugRunner(String filename) throws FileNotFoundException {
//     Process proc = Runtime.getRuntime().exec(cstring);
//     input = new BufferedReader  (new InputStreamReader(proc.getInputStream()));
         super("findbug", new BufferedReader(new FileReader("findout")));
//  trace("threadstart " + threadstart);
//  threadstart=true;
//         lib/findbugs-1.3.9/bin/findbugs -emacs -medium -textui -auxclasspath "..;$JDK2\lib\tools.jar;$JDK2\jre\lib\ext\RXTXcomm.jar;lib/rhino1_7R2/js.jar;lib/juniversalchardet-1.0.3.jar"  -exclude filter.xml  build > findout
      }

//filename:1:something
//Position parsefile(String line) throws IOException {
      public Position parsefile(String line) {
         if (0 == line.length())
            return Position.badpos;
         trace("parsing len =  " + line.length() + " line: "  + line);

         int pos = line.indexOf(':', 3); // three skips over any drive desc
         if (pos == -1) {
            trace("unexpected line:" + line);
            return Position.badpos;
         }

         String file = line.substring(0, pos);
         if (file.startsWith("javi/"))
            file = file.substring(5, pos);
         line = line.substring(pos + 1, line.length());
         pos = line.indexOf(':');
         int lineno;
//   try {
         lineno = Integer.parseInt(line.substring(0, pos).trim());
//   } catch (Exception e) {
//      lineno=1;
//      trace("gcc.parseline caught " + e);
//      return Position.badpos;
//   }
         if (lineno <= 0)
            lineno = 1;

         String comment = line.substring(pos + 1, line.length());
         return new Position(0, lineno, file, comment);
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
