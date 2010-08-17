package javi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class JlintRunner extends PositionIoc {
/* Copyright 1996 James Jensen all rights reserved */
static final String copyright = "Copyright 1996 James Jensen";

static private final Matcher ignoreError = Pattern.compile(
   "(Method.*Runnable.*synch)|(can be .*ed from different threads)|(Component 'Copyright' )"
      ).matcher("");
//grep -v 'Field .class\$'

public Position parsefile(String line) {
   //trace(" line "  +line);
   if (line.length()==0)
      return null;
   int pos = line.indexOf(':',3); // three skips over any drive desc
   if (line.startsWith("Verification completed")) {
       line = line.substring(pos+2,line.length());
       pos = line.indexOf(' '); // three skips over any drive desc
       UI.reportError("jlint caught " + 
            Integer.parseInt(line.substring(0,pos)) + 
           " errors") ;
       if (!jlintf)
         startjlint();
       return getnext();
   }
   ignoreError.reset(line);
   if (ignoreError.find())
      return getnext();

   String file = line.substring(0,pos);

   line = line.substring(pos+1,line.length());
   pos = file.lastIndexOf('\\');
   file = file.substring(pos+1,file.length());
   pos = line.indexOf(':');
   int y = Integer.parseInt(line.substring(0,pos));
   String comment= line.substring(pos+1,line.length());
   int x = 0;
   return new Position(x,y,file,comment);
} 

boolean jlintf;

JlintRunner() throws IOException {
      super("jlint *.java");
      String[] dlist = FileDescriptor.LocalFile.cwdlist(new GrepFilter(".*\\.java",false));
      if (dlist.length==0) 
        UI.reportMessage("no files to compile");
      String[] cmd =  {"d:\\jlint-2.3\\jlintwin32\\antic.exe","."};
      input = Tools.runcmd (cmd);
}

private void startjlint() {
   try{
      String[] dlist = FileDescriptor.LocalFile.cwdlist(new GrepFilter(".*\\.class",false));
      if (dlist.length==0) 
        UI.reportMessage("no files to compile");
      int i;

//      StringBuffer sb = new StringBuffer("d:\\jlint-2.3\\jlintwin32\\jlint.exe +verbose -not_overridden -redundant -weak_cmp -bounds -zero_operand -string_cmp -shadow_local");
      StringBuilder sb = new StringBuilder("d:\\jlint-2.3\\jlintwin32\\jlint.exe -not_overridden -redundant -weak_cmp -bounds -zero_operand -string_cmp -shadow_local");
      String flags= " ";
      sb.append(flags);
      for (String str :dlist)  {
         sb.append(" ");
         sb.append(str);
      }
      //trace(sb);
      input = Tools.runcmd(sb.toString());
      jlintf=true;
   } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("jlint.startjlint: unexpected exception" ,e );
   }
   
}

}
