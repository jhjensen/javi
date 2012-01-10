package javi;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import history.Tools;
import static history.Tools.trace;

final class XrefReader extends PositionIoc {
   private static final int maxLine = 80;
   private static final long serialVersionUID = 1;

      //trace("greader");
        //static final String[] commandline =  {
        //command[0] = "perl.exe";
        //command[1] = "-e";
        //command[2] = "$_=`lid " + s + "`;";
        //command[3] = "-e";
        //command[4]= "s/\\/home\\/jjensen/; $_= qx(grep -n -H $_ );";
        //command[5] = "-e";
        //command[6]= "print;";
        //command[7] = "-e";
        //command[8]= "print 'done\n';";
        //String commandline =
           //"perl.exe \"-e $_=`lid " + s + "`; s/\\//usr\\/include\\/c:/cygwin/usr/include/\\//; $_= qx(grep -n -H $_ ); print; print 'done\n';\"";

        //String[] command = new String[3];

        //command[0] = "perl.exe ";
        //command[1] = "-e $_=`lid " + s + "`; s/\\/home\\/jjensen/z:\\//;s/\\n/\\r\\n/; $_= qx(grep -n -H $_ ); print; print 'done\n';";
        //command[1] = "-e $_=`lid " + s + "`; s/\\/home\\/jjensen/z:\\//g; $_= qx(grep -n -H $_ ); print; print 'done\n';";

        //command[1] = " -e open(LI,'lid -S newline " + s + " |'); open(LO,' | xargs grep -n -H '); while (<LI>) { s/\\/home\\/jjensen/z:/;  print LO; } print  'done\n';";
        //command[1] = "test.pl" ;
        //command[2] = s;

   static final String[] commandline = {"lid", "-R", "grep", null};
      //static final String[] commandline = {"bash ", "-c",  null};
      //{"ssh", "speedy","cd sidewinder/src ;lid -R grep " + s + "| tr -d \r"};
      //"ssh nowind3 cd sidewinder/src ;lid -R grep " + s + "| tr -d \\\\r";
      //commandline[2] = "lid -R grep " + str  | perl.exe -p -e \" s/\\/usr\\/include\\/c:\\/cygwin\\/usr\\/include\\//; print; ne\n';\"";

   private static BufferedReader getIn(String str) throws IOException {
      commandline[3] = str;
      //trace("running command:" + commandline[2]);
      return Tools.runcmd(commandline);
   }

   XrefReader(String s) throws IOException {
      super(s, getIn(s), xconverter);
   }

   private static final Matcher linepat = Pattern.compile(
      "(^(\\w:)?[~\\w.\\/\\\\]+):([0-9]+): *(.*)").matcher("");

   /*  this results in less character arrays, but more space
         because the filename stays in the big array
   private static final Pattern splitpat = Pattern.compile("\r?\n");

   void dorun() throws InterruptedException {
      // simply reading in one line at a time triples the memory requirements
      char [] carr = new char[10000];
      int coffset=0;

      try {
         do {
            int chars = input.read(carr,coffset,carr.length-coffset);
            if (chars == -1)
               break;
            coffset += chars;
            if (coffset == carr.length)
               carr = java.util.Arrays.copyOf(carr,carr.length*2);
         } while(true);
      } catch (IOException e) {
         trace("caught ioexception " + e);
      }

      for(String line:splitpat.split(new String(carr,0,coffset))) {
         if (linepat.reset(line).matches()) {
            int y = Integer.parseInt(linepat.group(3));
            String fname = linepat.group(1);
            String comment = linepat.group(4);
            addElement(new Position(0, y, fname, comment));
         } else
            trace("line doesn't match " + line);
      }
   }

   */

   private static final XrefConv xconverter = new XrefConv();
   static final class XrefConv extends ClassConverter<Position> {
      private boolean failflag;
      public Position fromString(String line) {

         //trace("parsing line len =  " + line.length() + " line "  + line);

         if (linepat.reset(line).matches()) {
            int y = Integer.parseInt(linepat.group(3));
            String fname = linepat.group(1);
            String comment = linepat.group(4);

            //            if (comment.length() > maxLine - fname.length())
            //               comment = new String(comment.substring(0, maxLine - fname.length()).toCharArray());

            comment = new String(comment);
            return new Position(0, y, fname, comment);
         }
         if (0 == line.length())
            return defpos;
         try {
            int pos = line.indexOf(':', 3); // three skips over any drive desc
            String file = line.substring(0, pos);
            String line2 = line.substring(pos + 1, line.length());
            pos = line2.indexOf(':');
            int y = Integer.parseInt(line2.substring(0, pos));
            String comment = line2.substring(pos + 1, line2.length());
            //            if (comment.length() > maxLine - file.length())
            //               comment = new String(comment.substring(0, maxLine - file.length()));
            comment = new String(comment);
            int x = 0;
            failflag = false;
            Position retval = new Position(x, y, file, comment);
            //trace("xref reader returning " + retval);
            return retval;
         } catch (Exception e) {
            trace("caught exception " + e);
            //trace("for line:" + line);
            if (!failflag) {
               if (line.length() > maxLine)
                  line = line.substring(0, maxLine);
               trace("greader.parsline FAILED ");
               trace("parsing line len =  " + line.length() + " line "  + line);
            }
            failflag = true;
            return defpos;
         }
      }
   }
}
