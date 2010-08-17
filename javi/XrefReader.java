package javi;

import java.io.IOException;
import java.util.Arrays;


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

   public Position parsefile(String line) throws IOException {
      //trace("line = " + line);
      do {
         if ("done".equals(line)) {
            //trace("should exit immediatly");
         } else if (!"".equals(line))
            try {
               Position retval = parseline(line);
               if (retval != null)
                  return retval;

            } catch (Exception e) {
               //         trace("positionioc.parseline failed line = " + line);
               //      trace("positionioc.parseline exception = " + e);
            }
      } while (null != (line = input.readLine()));
      return null;
   }

   XrefReader(String s) {
      super(s);
      //trace("greader");
      String[] command = new String[9];
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
           //"perl.exe \"-e $_=`lid " + s + "`; s/\\/home\\/jjensen/z:\\//; $_= qx(grep -n -H $_ ); print; print 'done\n';\"";
        //String[] command = new String[3];

        //command[0] = "perl.exe ";
        //command[1] = "-e $_=`lid " + s + "`; s/\\/home\\/jjensen/z:\\//;s/\\n/\\r\\n/; $_= qx(grep -n -H $_ ); print; print 'done\n';";
        //command[1] = "-e $_=`lid " + s + "`; s/\\/home\\/jjensen/z:\\//g; $_= qx(grep -n -H $_ ); print; print 'done\n';";

        //command[1] = " -e open(LI,'lid -S newline " + s + " |'); open(LO,' | xargs grep -n -H '); while (<LI>) { s/\\/home\\/jjensen/z:/;  print LO; } print  'done\n';";
        //command[1] = "test.pl" ;
        //command[2] = s;

      String[] commandline = {"lid", "-R", "grep", s};
         //{"ssh", "speedy","cd sidewinder/src ;lid -R grep " + s + "| tr -d \r"};
         //"ssh nowind3 cd sidewinder/src ;lid -R grep " + s + "| tr -d \\\\r";
      try {
         //trace("starting " + commandline);
         input = Tools.runcmd(commandline);
      } catch (IOException e) {
         UI.popError("unable to start " + Arrays.toString(commandline) , e);
      }
   }

   private boolean failflag;
   private Position parseline(String line) {
      //trace("parsing line len =  " + line.length() + " line "  +line);
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
         return new Position(x, y, file, comment);
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
