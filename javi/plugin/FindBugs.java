package javi.plugin;
 
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javi.Rgroup;
import javi.FvContext;
import javi.IoConverter;
import javi.Position;
import javi.EventQueue;
import javi.FileDescriptor;
import javi.PositionIoc;
import javi.Plugin;

public class FindBugs extends Rgroup implements Plugin {

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


public Object doroutine(int rnum,Object arg,int count,int rcount,FvContext fvc,
     boolean dotmode) throws IOException {
   trace("rnum = " + rnum);
   switch (rnum) {
      case 1:
      
       javi.PosListList.Cmd.setErrors(new FindBugRunner("."));return null;
      default:
          throw new RuntimeException("vigroup:default");
   }
}

public static void main (String args[]) {
   javi.UI.init(false);
//   javi.UI.reportMessage("xxx");
   try {
      
      FindBugRunner gr = (args.length==0)
         ? new FindBugRunner(".")
         : new FindBugRunner(args[0]);

      Position p;
      while(null != (p = gr.getnext()))
         trace(" pos = " + p);
   } catch (Exception e) {
     trace(" caught exception " + e);
     e.printStackTrace();
   }
   trace("findbug exits");
}

static class FindBugRunner extends PositionIoc {

FindBugRunner(String filename) throws IOException{
//     Process proc = Runtime.getRuntime().exec(cstring);
//     input = new BufferedReader  (new InputStreamReader(proc.getInputStream()));
  super("findbug",new BufferedReader  (new FileReader("findout")));
//  trace("threadstart " + threadstart);
//  threadstart=true;
/*
../findbugs-1.2.0-rc2/bin/findbugs -emacs -medium -textui -auxclasspath "..;../junit3.8.2\junit.jar;$JDK2\lib\tools.jar;$JDK2\jre\lib\ext\RXTXcomm.jar;../rhino1_6R5/js.jar" . ../history >findout
../findbugs-1.3.2/bin/findbugs -emacs -medium -textui -auxclasspath "..;../junit3.8.2\junit.jar;$JDK2\lib\tools.jar;$JDK2\jre\lib\ext\RXTXcomm.jar;../rhino1_6R5/js.jar" . ../history >findout
../findbugs-1.3.5/bin/findbugs -emacs -medium -textui -auxclasspath "..;../junit3.8.2\junit.jar;$JDK2\lib\tools.jar;$JDK2\jre\lib\ext\RXTXcomm.jar;../rhino1_6R5/js.jar" . ../history >findout
../findbugs-1.3.6/bin/findbugs -emacs -medium -textui -auxclasspath "..;../junit3.8.2\junit.jar;$JDK2\lib\tools.jar;$JDK2\jre\lib\ext\RXTXcomm.jar;../rhino1_6R5/js.jar" . ../history >findout
../findbugs-1.3.6/bin/findbugs -emacs -medium -textui -auxclasspath "..;$JDK2\lib\tools.jar;$JDK2\jre\lib\ext\RXTXcomm.jar;../rhino1_6R5/js.jar"  -exclude filter.xml  . ../history > findout
../findbugs-1.3.9-rc1/bin/findbugs -emacs -high -textui -auxclasspath "..;$JDK2\lib\tools.jar;$JDK2\jre\lib\ext\RXTXcomm.jar;../rhino1_6R5/js.jar"  -exclude filter.xml  . ../history > findout
../findbugs-1.3.9/bin/findbugs -emacs -medium -textui -auxclasspath "..;$JDK2\lib\tools.jar;$JDK2\jre\lib\ext\RXTXcomm.jar;../rhino1_7R2/js.jar;../juniversalchardet-1.0.3.jar"  -exclude filter.xml  . ../history > findout
*/
}

//filename:1:something
//Position parsefile(String line) throws IOException {
public Position parsefile(String line) {
   if (line.length()==0)
     return Position.badpos;
   trace("parsing len =  " + line.length() + " line: "  +line);
     
   int pos = line.indexOf(':',3); // three skips over any drive desc
   if (pos==-1) {
      trace("unexpected line:" +line);
      return Position.badpos;
   }
  
   String file = line.substring(0,pos);
   if (file.startsWith("javi/" ))
      file=file.substring(5,pos);
   line = line.substring(pos+1,line.length());
   pos = line.indexOf(':');
   int lineno;
//   try {
      lineno = Integer.parseInt(line.substring(0,pos).trim());
//   } catch (Exception e) {       
//      lineno=1;
//      trace("gcc.parseline caught " + e);
//      return Position.badpos;
//   }
   if (lineno<=0)
      lineno=1;
   
   String comment= line.substring(pos+1,line.length());
   return new Position(0,lineno,file,comment);
} 

/*
static Matcher mt1 = Pattern.compile("(.*)( At )(.*):\\[lines? ([0-9]*)(-[0-9]*)?.*").matcher("");
static Matcher mt2 = Pattern.compile("(L S Nm: )(The class name javt\\.)([^ \\$]*)(.*)$").matcher("");
static Matcher mt3 = Pattern.compile("(.*: Class javt\\.)([^ \\$]*)(.*)$").matcher("");
static Matcher mt4 = Pattern.compile("(M C Eq: )(javt\\.)([^ \\$]*)(.*)$").matcher("");
static Matcher mt5 = Pattern.compile(".*: (?:(?:The field name)|(?:Unused field:)|(?:Unread field:)||(?:Should)|(?:The field name)) javt\\.([^; .\\$]*)[; \\.\\$].*").matcher("");
static Matcher mt6 = Pattern.compile("(.*: Class )([^ \\$]*)(.*)$").matcher("");
static Matcher mt7 = Pattern.compile("(L C Nm: )(Confusing to have methods javt\\.)([^ \\$]*)(.*)$").matcher("");

Object parseline(String line) {
   //trace("parsing line len =  " + line.length() + " line:"  +line);
   mt1.reset(line);
   if (mt1.find()) {
      Position pos =  new Position(0,Integer.parseInt(mt1.group(4)),mt1.group(3),mt1.group(1));
      //trace("parseline returning pos:" + pos);
      return pos;
      //return null;
   } else {
      mt2.reset(line);
      if (mt2.find()) {
         Position pos =  new Position(0,1,mt2.group(3)+".java",mt2.group(2)+mt2.group(3) + mt2.group(4));
         //trace("parseline returning pos:" + pos);
         return pos;
         //return null;
      } else {
         mt3.reset(line);
         if (mt3.find()) {
            Position pos =  new Position(0,1,mt3.group(2)+".java",mt3.group(1)+mt3.group(2) + mt3.group(3) );
            //trace("parseline returning pos:" + pos);
            return pos;
            //return null;
         } else {
            mt5.reset(line);
            if (mt5.matches()) {
//trace("0 " + mt5.group());
               Position pos =  new Position(0,1,mt5.group(1)+".java",mt5.group());
               //trace("parseline returning pos:" + pos);
               return pos;
               //return null;
            } else {
               mt4.reset(line);
               if (mt4.find()) {
                  Position pos =  new Position(0,1,mt4.group(3)+".java",mt4.group(3)+mt4.group(3) + mt4.group(4));
                  //trace("parseline returning pos:" + pos);
                  return pos;
                  //return null;
               } else {
                  mt6.reset(line);
                  if (mt6.find()) {
                     Position pos =  new Position(0,1,mt6.group(2)+".java",mt6.group(1)+mt6.group(2) + mt6.group(3));
                     //trace("parseline returning pos:" + pos);
                     return pos;
                     //return null;
                  } else {
                     mt7.reset(line);
                     if (mt7.find()) {
                        Position pos =  new Position(0,1,mt7.group(3)+".java",mt7.group(2)+mt7.group(3) + mt7.group(4));
                        //trace("parseline returning pos:" + pos);
                        //return null;
                        return pos;
                      } else {
                         Position pos = new Position(0,0,null,line);
                         //trace("parseline returning pos:" + pos);
                         return pos;
                      }
                   }
               }
            }
         }
      }
   }
} 
*/

}
}
