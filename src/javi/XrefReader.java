package javi;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class XrefReader extends PositionIoc {
   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";

   public static void main(String[] args) {
      try {
         XrefReader gr = new XrefReader("dilOpen");
         Position p;
         while (null != (p = gr.getnext()))
            trace(" pos = " + p);
      } catch (Exception e) {
         trace(" caught exception " + e);
      }
   }

   public Position parsefile() {
      //trace("line = " + line);
      String line;
      while (null != (line = getLine())) {
         if (!"done".equals(line)) {
            try {
               Position retval = parseline(line);
               if (retval != null)
                  return retval;

            } catch (Exception e) {
               trace("XrefReader failed line = " + line);
               trace("exception = " + e);
            }
         }
      }
      return null;
   }

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

   private static BufferedReader getIn(String str) throws IOException {
      commandline[3] = str;
      //commandline[2] = "lid -R grep " + str  | perl.exe -p -e \" s/\\/usr\\/include\\/c:\\/cygwin\\/usr\\/include\\//; print; ne\n';\"";
      trace("running command:" + commandline[2]);
      return Tools.runcmd(commandline);
   }


   XrefReader(String s) throws IOException {
      super(s, getIn(s));
   }

   private boolean failflag;

   private static Matcher linepat = Pattern.compile(
      "(^(\\w:)?[~\\w.\\/\\\\]+):([0-9]+): *(.*)").matcher("");
   private Position parseline(String line) {
      //trace("parsing line len =  " + line.length() + " line "  + line);
      linepat.reset(line);
      if (linepat.matches()) {
         int y = Integer.parseInt(linepat.group(3));
         String fname = linepat.group(1);
         return new Position(0, y, fname, linepat.group(4));
      }
      if (line.length() == 0)
         return null;
      try {
         int pos = line.indexOf(':', 3); // three skips over any drive desc
         String file = line.substring(0, pos);
         line = line.substring(pos + 1, line.length());
         pos = line.indexOf(':');
         int y = Integer.parseInt(line.substring(0, pos));
         String comment = line.substring(pos + 1, line.length());
         if (comment.length() > 200)
            comment = comment.substring(0, 200);
         int x = 0;
         failflag = false;
         Position retval = new Position(x, y, file, comment);
         trace("xref reader returning " + retval);
         return retval;
      } catch (Exception e) {
         trace("for line:" + line + ":\ncaught exception " + e);
         trace("this " + this);
         e.printStackTrace();
         if (!failflag) {
            if (line.length() > 200)
               line = line.substring(0, 200);
            trace("greader.parsline FAILED ");
            trace("parsing line len =  " + line.length() + " line "  + line);
         }
         failflag = true;
         return null;
      }
   }
}
