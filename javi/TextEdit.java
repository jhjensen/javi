package javi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;

import static javi.FileDescriptor.LocalFile.make;

public class TextEdit<OType> extends EditContainer<OType> {
   /* Copyright 1996 James Jensen all rights reserved */
   private static final String copyright = "Copyright 1996 James Jensen";

   static TextEdit<String> root;

   static {
      StringIoc strio = new StringIoc("root EditContainer",
                                      "should never see root container");
      root = new TextEdit<String>(strio, strio.prop);
   }

   static void saveState(java.io.ObjectOutputStream os) throws IOException {
      os.writeObject(root);
   }

   static void restoreState(java.io.ObjectInputStream is) throws
         IOException, ClassNotFoundException {
      root = (TextEdit<String>) is.readObject();
   }

   TextEdit(IoConverter<OType> e, OType[] inarr, EditContainer parent,
            FileProperties<OType> prop) {
      super(e, inarr, parent, prop);
   }

   TextEdit(IoConverter<OType> e, EditContainer parent,
            FileProperties<OType> prop) {
      super(e, parent, prop);
   }

   TextEdit(IoConverter<OType> e, FileProperties<OType> prop) {
      super(e, root, prop);
   }

   TextEdit(IoConverter<OType> e, OType[] inarr, FileProperties<OType> prop) {
      super(e, inarr, root, prop);
   }

   final Position inserttext(String iStr, int xstart, int ystart) {
      //trace("adding text:"+iStr);
      //trace("xstart = " + xstart + " ystart = " +ystart);

      if (iStr == null || iStr.length() == 0)
         return new Position(xstart, ystart, fdes(), "inserttext");

      if (ystart >= readIn()) {
         ArrayList<String>  sar = stringtoarray(iStr);
         insertStrings(sar , readIn());
         return new Position(sar.get(sar.size() - 1).length(), readIn() - 1,
                             fdes(), "inserttext");
      }

      String line = at(ystart).toString();

      //trace("line:" + line + ":");
      int xcursor = line.length() - xstart;
      iStr = line.substring(0, xstart) + iStr
             + line.substring(xstart, line.length());

      ArrayList<String> sarr = stringtoarray(iStr);
      changeElementAtStr(sarr.get(0), ystart);
      //trace("changed line " + ystart + " to:" + sarr.get(0));
      if (sarr.size() > 1) {
         sarr.remove(0);
         //trace("inserting string " + ystart + " to:" + sarr.get(0));
         insertStrings(sarr, ystart + 1);
         ystart += sarr.size();
      }
      line = sarr.get(sarr.size() - 1);
      return new Position(line.length() - xcursor, ystart,
         fdes(), "inserttext");
   }

   final void substitute(int lineno, Matcher matchPattern, String subpattern,
                   boolean globflag) {
      int i;
      char c;
      boolean slash = false;
      StringBuilder replace = new StringBuilder();
      String line =  at(lineno).toString();
      do {
         //trace("substitute doing line number " + lineno);
         matchPattern.reset(line);
         if (!matchPattern.find())
            break;
         replace.setLength(0);
         for (i = 0; i < subpattern.length(); i++) {
            c =  subpattern.charAt(i);
            if (slash) {
               slash = false;
               if ((c >= '0' && c <= '9'))
                  replace.append(matchPattern.group(c - 0x30));
               else
                  replace.append(c);
            } else
               switch (c) {
                  case '\\':
                     slash = true;
                     break;
                  case '&':
                     replace.append(matchPattern.group(0));
                     break;
                  default:
                     replace.append(c);
                     break;
               }
         }
         //int matchstart = matchPattern.start()+replace.length();
         line = line.substring(0, matchPattern.start()) + replace
            + line.substring(matchPattern.end(), line.length());
      } while (globflag);
      changeElementAtStr(line, lineno);
   }

   @SuppressWarnings("fallthrough")
   public final int processCommand(String com, int ypos) throws
         InputException, IOException {

      //trace("com = " + com);
      StringIndex str = new StringIndex(com, this);
      int linestart;
      int linefinish;
      int lineto = 0;
      Matcher rangePattern = null;
      Matcher matchPattern = null;
      boolean inverse = false;
      char c, c2;
      String subpattern = null;
      boolean globflag = false;
      boolean defaultflag = false;

      // get line range

      c = str.peek();
      if (c == '%') {
         linestart = 1;
         linefinish = finish() - 1;
         str.next();
      } else {
         if (!str.isnum(c))
            defaultflag = true;
         linestart = str.getline(ypos);
         linefinish = linestart;
         c = str.peek();
         if (c == ',' || c == ';') {
            if (c == ';')
               ypos = linestart;
            str.next();
            linefinish = str.getline(ypos);
            defaultflag = false;
         }
      }
      c = str.peek();
      if (c == 'g') {
         str.next();
         if (linestart == linefinish && linestart == ypos) {
            linestart = 1;
            linefinish = finish() - 1;
         }
         c = str.peek();
         if  (c == '!') {
            inverse = true;
            str.next();
         }
         rangePattern = str.getpattern();
         //trace("pattern = " + rangePattern.pattern());
      }
      c = str.next();
      //trace("command proc com = " + c + " str:" +str);
      switch (c) {
         case 's':
            c2 = str.peek();
            matchPattern = str.getpattern();
            //trace("matchPattern " + matchPattern);
            subpattern = str.getsubstitute(c2);
            if (str.next() == 'g')
               globflag = true;
            break;
         case 'c':
            c = str.peek();
            if (c != 'o') {
               //trace("processCommand returns false");
               return -1;
            }
            str.next();
            c = 't';
         case 't':
         case 'm':
            lineto = str.getline(ypos);
            if (str.next() != 0) {
               //trace("processCommand returns false");
               return -1;
            }
            break;
         case 'd':
            break;
         case 'w':
            if (defaultflag) {
               linestart = 1;
               linefinish = finish() - 1;
            }
            if (str.peek() == 0 && linestart == 1
                  && !containsNow(linefinish + 1)) {
               printout();
               return ypos;
            }
            break;
         case 0:
            return linestart;
         default :
            //trace("processCommand returns false");
            return -1;
      }
      return commandproc2(linestart, linefinish, rangePattern, inverse,
                          matchPattern, globflag, c, subpattern,
                          lineto, str.remainder());
   }

   private int commandproc2(int linestart, int linefinish,
         Matcher rangePattern, boolean inverse, Matcher matchPattern,
         boolean globflag, char command, String subpattern, int lineto,
         String args) throws IOException, InputException {

      //trace("com = " + command + " args:" + args + ":");
      switch (command) {
         case 'd':
            //if (args.length()!=0) {
            //   trace("");
            //   return -1;
            //}
            ArrayList<String>  delvec = new ArrayList<String>(100);
            for (int i = linestart; i <= linefinish;) {
               if (rangePattern == null
                     || (inverse ^ (searchForward(rangePattern, 0, i))))  {
                  delvec.add(remove(i, 1).get(0));
                  linefinish--;
               } else
                  i++;
            }
            Buffers.deleted('0', delvec);
            checkpoint();
            return linestart;
         case 'w':
            File file = new File(args);
            if (file.exists() && !file.canWrite())
               throw new ReadOnlyException(this, file.getCanonicalPath());
            PrintWriter os = new PrintWriter(new FileWriter(file));
            try {

               for (int i = linestart; i <= linefinish; i++)
                  if (rangePattern == null
                        || (inverse ^ (searchForward(rangePattern, 0, i))))
                     os.println(at(i));
            } finally {
               os.close();
            }
            return linestart;
         case 's':
            if (matchPattern == null)
               throw new InputException("no pattern to match");
            for (int i = linestart; i <= linefinish; i++)
               if (rangePattern == null
                     || (inverse ^ (searchForward(rangePattern, 0, i))))
                  substitute(i, matchPattern, subpattern, globflag);
            checkpoint();
            return linestart;
      }

      boolean before = lineto < linestart;
      if (!before && lineto < linefinish)
         throw new InputException("target within source moving to "
            + lineto + " from [" + linestart + "," + linefinish + "]");

      lineto++;

      for (int i = linestart; i <= linefinish;)
         if (rangePattern == null
               || (inverse ^ (searchForward(rangePattern, 0, i)))) {
            switch(command) {
               case 'm':
                  if (before)
                     moveLine(i++, lineto++);
                  else {
                     moveLine(i, lineto - 1);
                     linefinish--;
                  }
                  break;
               case 't':
                  copyLine(i, lineto++);
                  if (before) {
                     i += 2;
                     linefinish++;
                  } else {
                     i++;
                  }
                  break;
               default:
                  throw new RuntimeException();
            }
         } else
            i++;
      switch (command) {
         case 'c':
         case 't':
         case 'm':
            checkpoint();
            break;
      }
      return linestart;
   }

   final String gettext(int xstart, int ystart, int xend, int yend) {
      return deletetext(true, xstart, ystart, xend, yend);
   }

   final String deletetext(boolean preserve, int xstart, int ystart, int xend,
                     int yend)  {
      //trace("deletetext start("+xstart + "," + ystart + ") end (" + xend  +","  + yend +")");

      StringBuilder delline = new StringBuilder();
      String delline2 = "";
      String line = at(ystart).toString();

//  take care of firstline
      if (yend == ystart) {
         if (xstart > xend)
            throw new RuntimeException();
         if (xstart == xend)
            return null;
         delline.append(line.substring(xstart, xend));
         line = line.substring(0, xstart)
            + line.substring(xend, line.length());
         if (!preserve)
            changeElementAtStr(line, yend);
      } else {
         if (ystart > yend) {
            throw new RuntimeException();
         }
         if (xstart != 0) { // we have the tail of a line to remove
            delline.append(line.substring(xstart, line.length()));
            delline.append("\n");
            line = line.substring(0, xstart);
            if (!preserve)
               changeElementAtStr(line, ystart);
            ystart++;
         }
         // take care of last line
         if (xend > 0) {
            line = at(yend).toString();
            if (xend < line.length()) {
               delline2  = line.substring(0, xend);
               line = line.substring(xend, line.length());
               if (!preserve)
                  changeElementAtStr(line, yend);
            } else
               yend++;
         }
         // take care of whole lines
         if (ystart < yend) {
            ArrayList<String> obarray = preserve
                                        ? getElementsAt(ystart, yend - ystart)
                                        : remove(ystart, yend - ystart);
            for (Object obj : obarray) {
               delline.append(obj);
               delline.append("\n");
            }
         }
      }
      //if (!contains(1)) // deleted entire file
      //   insert(editgroup.emptyline,0);
      delline.append(delline2);
      //trace("deleted text" + delline.toString());
      return delline.toString();
   }

   private int findnonwhite(String line) {
      int i;
      for (i = 0; (i < line.length()); i++)
         if  (line.charAt(i) != ' ')
            break;
      return i;
   }

   private int findalign(int lineno, boolean dir) {

      int i;
      int spcount = 0;
      int linepos = findnonwhite(at(lineno).toString());
      for (i = lineno - 1; i >= 1; i--) {
         spcount = findnonwhite(at(i).toString());
         if (dir ? spcount < linepos : spcount > linepos)
            if (spcount != at(i).toString().length())
               return dir ? linepos - spcount : spcount - linepos;
      }
      return dir ? linepos : 0;
   }

   final int shiftright(int start, int lineamount) {
      //trace("amount = " + amount + " start = " +
      //  start + " lineamount= " + lineamount);
      int amount =  findalign(start, false);
      if (amount != 0) {
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < amount; i++)
            sb.append(' ');
         for (int i = start; i < start + lineamount; i++) {
            String line = at(i).toString();
            changeElementAtStr(sb + line, i);
         }
      }
      return amount;
   }

   final int shiftleft(int start, int lineamount) {
      //trace("amount = " + amount + " start = " +
      //  start + " lineamount= " + lineamount);
//   tabConverter tb = (tabConverter)getConverter();
      int amount =  findalign(start, true);
      int retval = -amount;
      if (amount != 0) {

         for (int i = start; i < start + lineamount; i++) {
            String line = at(i).toString();
            int delamount = amount;
            if (delamount > line.length())
               delamount = line.length();
            for (int j = 0; j < delamount; j++)
               if (line.charAt(j) != ' ') {
                  delamount = j;
                  break;
               }
            if (delamount > 0) {
               line = line.substring(delamount, line.length());
               changeElementAtStr(line, i);
               if (i == start)
                  retval = -delamount;
            }
         }
      }
      return retval;
   }

   final void changecase(int xstart, int ystart, int xend, int yend) {

      int amount = yend - ystart + 1;
      if (amount == 1) { // one line
         String line = at(ystart).toString();
         line = ccase(line, xstart, xend);
         changeElementAtStr(line, ystart);
      } else {
         String line = at(ystart).toString();
         line = ccase(line, xstart, line.length());
         changeElementAtStr(line, ystart);
         line = at(ystart + amount - 1).toString();
         line = ccase(line, 0, xend);
         changeElementAtStr(line, ystart + amount - 1);
         for (int i = ystart + 1; i < ystart + amount - 1; i++) {
            line = at(i).toString();
            line = ccase(line, 0, line.length());
            changeElementAtStr(line, i);
         }
      }
   }
   private static String ccase(String in, int startoffset, int finishoffset) {

      char [] sb = in.toCharArray();
      char c;
      if (finishoffset > sb.length)
         finishoffset = sb.length;
      for (int i = startoffset; i < finishoffset; i++) {
         c = sb[i];
         if (c >= 'a' && c <= 'z')
            c = (char) (c - 32); // lower case;
         else if (c >= 'A' && c <= 'Z')
            c = (char) (c + 32); // lower case;
         sb[i] = c;
      }
      return new String(sb);
   }

   final int joinlines(int count, int start) {
      int i;
      if (count == 1)
         count = 2;
      StringBuilder line = new StringBuilder(at(start).toString());

      int retval = line.length();
      //trace("retval " + retval);

      while (0 != --count) {
         if (!contains(start + 1))
            break;

         if (line.charAt(line.length() - 1) != ' ')
            line.append(' ');

         retval = line.length();

         String line2 = at(start + 1).toString();

         for (i = 0; i < line2.length(); i++)
            if (line2.charAt(i) != ' ')
               break;
         line2 = line2.substring(i, line2.length());

         line.append(line2);
         remove(start + 1, 1);
      }
      changeElementAtStr(line.toString(), start);
      return (retval);
   }

   final void tabfix(int tabstop) throws InputException {
      if (tabstop == 0)
         throw new InputException("tabstop of 0 illegal");
      for (int lineno = 1; lineno < finish(); lineno++) {
         String line = at(lineno).toString();
         int tabOffset = line.indexOf('\t');
         if (tabOffset != -1)
            changeElementAtStr(DeTabber.deTab(line, tabOffset, tabstop,
                                              new int[0]), lineno);
      }
      checkpoint();
   }

   private static class StringIndex {

      private String str;
      private int index;
      private TextEdit ex;

      public String toString()  {
         return index + ":" + str;
      }

      StringIndex(String stri, TextEdit exi) {

         str = stri;
         ex = exi;
         index = 0;
      }

      char next() {
         //trace("next " + index);
         if (index >= str.length()) {
            index = str.length();
            return 0;
         }
         return (str.charAt(index++));
      }

      char peek() {
         if (index >= str.length())
            return 0;
         return str.charAt(index);
      }

      void push() {
         index--;
      }

      static final String nchars = "1234567890+-./?$";
      boolean isnum(char c) {
         return (nchars.indexOf(c) != -1);
      }
      int getline(int curr) throws InputException {

         int lineno;
         int save = 0;
         char c2;
         boolean mflag = false;
         char c = peek();
         if (!isnum(c)) {
            //trace("returning " + curr);
            return curr;
         }
         if (c == '+' || c == '-')
            lineno = curr;
         else lineno = 0;
         while (true) {
            c = next();
            if (c >= '0' && c <= '9') {
               lineno = 0;
               do {
                  lineno = lineno * 10 + c - 0x30;
                  c =  next();
               } while (c >= '0' && c <= '9');
               if (c != 0)
                  push();
            } else
               switch (c) {
                  case '+':
                  case '-':
                     c2 = peek();
                     if (!isnum(c2)  || (c2 == '+' || c2 == '-')) {
                        if (c == '+')
                           lineno++;
                        else
                           lineno--;
                        continue;
                     }
                     save = lineno;
                     mflag = c == '-';
                     continue;
                  case '/':
                  case '?':
                     push();
                     lineno = searchreg(curr);
                     break;
                  case '$':
                     lineno = ex.finish() - 1;
                     break;
                  case '.':
                     lineno = curr;
                     break;
                  default :
                     if (c != 0)
                        push();
                     //trace("returning " + lineno
                     //  + " next char = " + peek());
                     return lineno;
               }
            if (mflag) {
               lineno = save - lineno;
               mflag = false;
            } else
               lineno = save + lineno;
         }
      }

      //move to editvec?
      int searchreg(int curr) throws InputException {
         boolean reverse;
         int i = curr;
         if (peek() == '/')
            reverse = false;
         else if (peek() == '?')
            reverse = true;
         else
            throw new InputException("invalid search expression");
         Matcher exp = getpattern();
         if (reverse) {
            while (i-- > 0)
               if (ex.searchForward(exp, 0, i))
                  return i;
         } else  {
            while (ex.contains(++i))
               if (ex.searchForward(exp, 0, i))
                  return i;
         }
         throw new InputException("pattern not found");
      }

      Matcher getpattern() {
         char stopc = next();
         if (stopc == 0)
            return GState.getRegex();
         int start = index;
         String cstring = null;
         while (cstring == null) {
            char c = next();
            if (c ==  stopc)
               cstring = str.substring(start, index - 1);
            else if (c == 0)
               cstring = str.substring(start, index);
            else if (c == '\\')
               next();
         }
         if (cstring.length() == 0)
            return GState.getRegex();

         GState.setRegex(cstring, 0);
         return GState.getRegex();
      }

      String getsubstitute(char delimit) {
         char c;
         int start = index;
         int end;
         while ((c = next()) !=  delimit)
            if (c == 0)
               break;
         if (c == 0)
            end = index;
         else
            end = index - 1;
         if (start > end)
            end = start;
         return str.substring(start, end);
      }

      String remainder() {
         //trace("remainder index " + index);
         while ((next()) == ' ') { /* skip spaces */ }
         return str.substring(index - 1, str.length());
      }


   }

   static ArrayList<String> stringtoarray(String s) {

      int count = 0;
      int lastindex = 0;
      while ((lastindex = 1 + s.indexOf('\n', lastindex)) != 0)
         count++;

      count++;
      //trace("stringtoarray count " + count);
      ArrayList<String> sarr = new ArrayList<String>(count);
      lastindex = 0;
      for (int i = 0; i < count; i++) {
         int nline = s.indexOf('\n', lastindex);
         if (nline == -1) {
            sarr.add(s.substring(lastindex, s.length()));
         } else {
            sarr.add(s.substring(lastindex, nline));
            lastindex = nline + 1;
         }
      }
      //trace("sarr len " + sarr.size());
      //for (int i=0;i<sarr.size();i++)  trace("stringtoarray sarr[" + i + "].length" + sarr[i].length());
      return sarr;

   }
}

final class EditTester1 {
   private EditTester1() { }
   static void trace(String str) {
      Tools.trace(str, 1);
   }

   static void copyFile(String from, String to) throws IOException {
      FileInputStream input = new FileInputStream(from);
      try {
         int length = (int) (new File(from)).length();
         byte[] iarray = new byte[length];
         int ilen = input.read(iarray, 0, length);
         if (ilen != length)
            throw new RuntimeException(
               "copyFile: read in length doesnt match");
         FileOutputStream output = new FileOutputStream(to);
         try {
            output.write(iarray);
         } finally {
            output.close();
         }
      } finally {
         input.close();
      }
   }

   static boolean myassert(boolean flag, Object dump) {
      //String dstring = (" ASSERTION FAILURE: \n" + dump.toString());
      if (dump instanceof Throwable)
//      dstring = dstring + ((Throwable) dump).printStackTrace();
         ((Throwable) dump).printStackTrace();

      if (!flag)
         throw new RuntimeException(
            " ASSERTION FAILURE: \n" + dump.toString());
      return flag;
   }

   static void starttest() {
      //Tools.trace("",1);
      trace("***************starting " + Tools.caller() + " ****************");
   }

   static TextEdit<String> newTe(String name) {
      FileDescriptor fd = FileDescriptor.make(name);
      FileProperties fp = new FileProperties(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> retVal = new TextEdit(fi, fp);
      retVal.finish();
      myassert(!retVal.getError(), retVal);
      return retVal;
   }

   static void test1() throws IOException {
      UI.setStream(new StringReader("")); // forces an exceptin if it gets read
      starttest();
      make("extest1").delete();
      make("extest1.dmp2").delete();
      TextEdit<String> ex = newTe("extest1");
      myassert(ex.finish() == 2, ex.finish());
      ex.inserttext("aaa", 0, 1);
      ex.checkpoint();
      myassert(ex.at(1).equals("aaa"), ex);
      ex.idleSave();
      myassert(ex.finish() == 2, ex.finish());
      ex.undo();
      myassert(ex.at(1).equals(""), ex.at(1));
      myassert(!ex.isModified(), ex);
      myassert(ex.finish() == 2, ex.finish());
      ex.redo();
      ex.printout();
      ex.disposeFvc();

//System.exit(0);
      ex = newTe("extest1");
      myassert(ex.at(1).equals("aaa"), ex);
      ex.undo();
      myassert(ex.at(1).equals(""), ex.at(1));
      ex.redo();
      myassert(ex.at(1).equals("aaa"), ex.at(1));
      ex.undo();
      ex.inserttext("xxx", 0, 1);
      ex.checkpoint();
      ex.disposeFvc();
   }
   static void test18() throws IOException {
      UI.setStream(new StringReader("o"));
      starttest();
      make("extest18").delete();
      make("extest18.dmp2").delete();
      TextEdit<String> ex = newTe("extest18");
      myassert(ex.finish() == 2, ex.finish());
      ex.inserttext("aaa", 0, 1);
      ex.checkpoint();
      ex.idleSave();
      myassert(ex.finish() == 2, ex.finish());
      ex.undo();
      ex.printout();
      ex.disposeFvc();

      ex = newTe("extest18");
      ex.redo();
      ex.undo();
      ex.printout();
      ex.undo();
      ex.disposeFvc();
   }

   static void test2() throws IOException {

      UI.setStream(new StringReader("b\n"));
      starttest();
      copyFile("extest1", "extest2");
      copyFile("extest1.dmp2", "extest2.dmp2");

      TextEdit<String> ex2 = newTe("extest2");
      myassert(ex2.at(1).equals("xxx"), ex2);
      ex2.undo();
      myassert(ex2.at(1).equals(""), ex2);
      ex2.redo();
      myassert(ex2.at(1).equals("xxx"), ex2);
      ex2.undo();
      ex2.disposeFvc();
   }

   static void test3() throws IOException {
      UI.setStream(new StringReader("b\n"));
      starttest();
      copyFile("extest2", "extest3");
      copyFile("extest2.dmp2", "extest3.dmp2");
      TextEdit<String> ex3 = newTe("extest3");
      myassert(ex3.at(1).equals(""), ex3);
      ex3.redo();
      myassert(ex3.at(1).equals("xxx"), ex3);
      ex3.undo();
      ex3.disposeFvc();
   }

   static void makeFile(String filename, String contents) throws IOException {
      make(filename).delete();
      FileWriter fs = new FileWriter(filename);
      try {
         fs.write(contents);
      } finally {
         fs.close();
      }
   }

   static void checkFile(String filename, String contents) throws IOException {
      File f = new File(filename);
      char [] fchar = new char[(int) f.length() + 20];
      FileReader fs = new FileReader(filename);
      try {
         int len = fs.read(fchar, 0, fchar.length);
         if (len != contents.length())
            throw new IOException("file length unexpected:" + len);

         String str = new String(fchar, 0, len);
         if (!str.equals(contents))
            throw new IOException("file contents not equal");
      } finally {
         fs.close();
      }
   }

   static void test4() throws IOException {
      UI.setStream(new StringReader("fb\n"));
      starttest();
      copyFile("extest1.dmp2", "extest4.dmp2");

      makeFile("extest4", "aaaa\n");
      TextEdit<String> ex4 = newTe("extest4");
      myassert(ex4.at(1).equals("aaaa"), ex4.at(1));
      ex4.undo();
      myassert(ex4.at(1).equals("aaaa"), ex4.at(1));
      ex4.disposeFvc();
      copyFile("extest1.dmp2", "extest4.dmp2");

      ex4 = newTe("extest4");
      myassert(ex4.at(1).equals("xxx"), ex4.at(1));
      ex4.disposeFvc();
   }

   static void test5() throws IOException {
      starttest();

      UI.setStream(new StringReader("b\n"));
///*

      make("extest5.dmp2").delete();

      makeFile("extest5", "aaa\n");
      TextEdit<String> ex = newTe("extest5");
      myassert(ex.at(1).equals("aaa"), ex.at(1));

//     view vi = new oldview();
      ex.insertOne("bbb", 1);
      ex.checkpoint();
      //ex.idleSave(); ex.printout();System.exit(0);
      myassert(ex.at(1).equals("bbb"), ex.at(1));
      myassert(ex.at(2).equals("aaa"), ex);
      myassert(ex.finish() == 3, ex.finish());
      myassert(ex.isModified(), ex);
      ex.undo();
      myassert(!ex.isModified(), ex);
      ex.redo();
      ex.printout();
      ex.disposeFvc();
//*/   extext
      ex = newTe("extest5");
      myassert(ex.finish() == 3, ex.finish());
      myassert(ex.at(1).equals("bbb"), ex);
      myassert(ex.at(2).equals("aaa"), ex.at(2));
      myassert(!ex.isModified(), ex);
      ex.undo();
      myassert(ex.isModified(), ex);
      ex.redo();
      ex.printout();
      ex.disposeFvc();

      ex = newTe("extest5");
      myassert(ex.finish() == 3, ex.finish());
      myassert(ex.at(1).equals("bbb"), ex);
      myassert(ex.at(2).equals("aaa"), ex);
      ex.printout();
      ex.disposeFvc();
   }

   static void test7() throws IOException {
      starttest();

      UI.setStream(new StringReader("ob\n"));
      make("extest7.dmp2").delete();
      makeFile("extest7", "aaa\n");
      TextEdit<String> ex = newTe("extest7");

      myassert(ex.at(1).equals("aaa"), ex.at(1));

      ex.insertOne("bbb", 1);
      ex.checkpoint();
      myassert(ex.at(1).equals("bbb"), ex.at(1));
      myassert(ex.at(2).equals("aaa"), ex);
      myassert(ex.finish() == 3, ex.finish());
      myassert(ex.isModified(), ex);
      ex.undo();
      myassert(!ex.isModified(), ex);
      ex.disposeFvc();
      ex = newTe("extest7");
      myassert(ex.finish() == 2, ex.finish());
      myassert(ex.at(1).equals("aaa"), ex.at(1));
      myassert(!ex.isModified(), ex);
      ex.redo();
      myassert(ex.at(1).equals("bbb"), ex.at(1));
      myassert(ex.isModified(), ex);
      ex.disposeFvc();

   }

   static void test8() throws IOException {
      starttest();
      UI.setStream(new StringReader("b\n"));
      make("extest8").delete();
      make("extest8.dmp2").delete();
      TextEdit<String> ex = newTe("extest8");
      ex.inserttext("aaa", 0, 1);
      ex.checkpoint();
      myassert(ex.at(1).equals("aaa"), ex);
      myassert(ex.finish() == 2, ex.finish());
      ex.disposeFvc();

//System.exit(0);
      ex = newTe("extest8");
      myassert(ex.at(1).equals("aaa"), ex.at(1));
      ex.undo();
      myassert(ex.at(1).equals(""), ex);
      ex.disposeFvc();
   }

   static void test9() throws IOException {
      starttest();
      UI.setStream(new StringReader("b\n"));
///*
      make("extest9").delete();
      make("extest9.dmp2").delete();
      TextEdit<String> ex = newTe("extest9");
      ex.inserttext("a", 0, 1);
      ex.checkpoint();
      ex.inserttext("b", 0, 1);
      ex.checkpoint();
      ex.inserttext("c", 0, 1);
      ex.checkpoint();
      ex.inserttext("d", 0, 1);
      ex.checkpoint();
      myassert(ex.at(1).equals("dcba"), ex);
      ex.undo();
      ex.undo();
      ex.undo();
      ex.undo();
      myassert(ex.at(1).equals(""), ex);
      ex.redo();
      ex.redo();
      myassert(ex.at(1).equals("ba"), ex);
      ex.redo();
      ex.redo();
      myassert(ex.at(1).equals("dcba"), ex);
      ex.undo();
      ex.undo();
      myassert(ex.at(1).equals("ba"), ex);
      ex.undo();
      ex.undo();
      myassert(ex.at(1).equals(""), ex);
      myassert(ex.finish() == 2, ex.finish());
      ex.disposeFvc();

// */extext

      ex = newTe("extest9");
      myassert(ex.at(1).equals(""), ex.at(1));
      ex.redo();
      ex.redo();
      ex.redo();
      ex.redo();
      myassert(ex.at(1).equals("dcba"), ex.at(1));
      ex.undo();
      ex.undo();
      ex.undo();
      ex.undo();
      myassert(ex.at(1).equals(""), ex.at(1));
      ex.disposeFvc();
   }

//*/   extext
   static void perftest() throws IOException, InputException {

      int tot = 20000;
      int after = 13123;
      long targettime = 2300;
      long targetmem = 2300000; // should be 3100000 in old version
      //int tot = 100;
      //int after = 82;
      //int tot = 10;
      //int after = 10;
      {
         make("perftest.dmp2").delete();
         FileWriter fs = new FileWriter("perftest");
         try {
            for (int i = 0; i < tot; i++)
               fs.write("xxline " + i + '\n');
         } finally {
            fs.close();
         }
      }
      Tools.doGC();
      long elapsed = 0;
      {
         trace("start memory " + (Runtime.getRuntime().totalMemory()
             - Runtime.getRuntime().freeMemory()));
         Date start = new Date();
         TextEdit<String> ex = newTe("perftest");
         myassert(ex.finish() == tot + 1, ex.finish());
         //vic vico = new vic("");
         ex.processCommand("g/9/d", 1);
         myassert(ex.finish() == after, ex.finish());
         ex.finish();
         ex.printout();
         ex.disposeFvc();

         ex = newTe("perftest");
         ex.printout();
         myassert(ex.finish() == after, ex.finish());
         Date end = new Date();
         elapsed =  end.getTime() - start.getTime();
         ex.disposeFvc();
      }

      Tools.doGC();
      long mem =  Runtime.getRuntime().totalMemory()
                  - Runtime.getRuntime().freeMemory();
      trace("end memory " + mem);
      trace("elapsed time = " + elapsed + " milliseconds");

      //try {Thread.sleep(1000000);} catch (InterruptedException e) {}
      myassert(mem < targetmem, Long.valueOf(mem));
      myassert(elapsed < targettime, Long.valueOf(elapsed));
   }

   static void test10() throws IOException {
      UI.setStream(new StringReader("f\n"));
      starttest();

      makeFile("extest10", "aaaa\n\bb\n");
      copyFile("extest1.dmp2", "extest10.dmp2");
      TextEdit<String> ex = newTe("extest10");
      ex.inserttext("aaa", 0, 1);
      ex.printout();
      ex.disposeFvc();
//System.exit(0);
      ex = newTe("extest10");
      myassert(ex.at(1).equals("aaaaaaa"), ex.at(1));
      ex.disposeFvc();
   }

   static void test11() throws IOException {
      UI.setStream(new StringReader("b\n"));
      starttest();

      make("extest11.dmp2").delete();
      makeFile("extest11", "aaaa\n\bb\n");
      TextEdit<String> ex = newTe("extest11");
      ex.printout();
      ex.undo();
      myassert(ex.at(1).equals("aaaa"), ex.at(1));
      ex.inserttext("bbb", 0, 1);
      ex.checkpoint();
      myassert(ex.at(1).equals("bbbaaaa"), ex.at(1));
      ex.undo();
      myassert(ex.at(1).equals("aaaa"), ex.at(1));
      ex.printout();
      ex.redo();
      myassert(ex.at(1).equals("bbbaaaa"), ex.at(1));
      ex.disposeFvc();
//System.exit(0);
      ex = newTe("extest11");
      myassert(ex.at(1).equals("bbbaaaa"), ex.at(1));
      ex.undo();
      myassert(ex.at(1).equals("aaaa"), ex.at(1));
      ex.disposeFvc();
   }

   static void test12() throws IOException, InputException  {
      UI.setStream(new StringReader("bb\n"));
      starttest();

      make("extest12.dmp2").delete();
      makeFile("extest12", "\taaaa\r\nbb\r\n");
      TextEdit<String> ex = newTe("extest12");
      ex.tabfix(3);
      ex.checkpoint();
      myassert(ex.at(1).equals("   aaaa"), ex.at(1));
      ex.disposeFvc();

      checkFile("extest12", "\taaaa\r\nbb\r\n");
      ex = newTe("extest12");
      myassert(ex.at(1).equals("   aaaa"), ex.at(1));
      ex.undo();
      myassert(ex.at(1).equals("\taaaa"), ex.at(1));
      ex.disposeFvc();
//System.exit(0);
      ex = newTe("extest12");
//     ex.redoxxx();
      ex.tabfix(4);
      ex.checkpoint();
      myassert(ex.at(1).equals("    aaaa"), ex.at(1));
      ex.printout();
      checkFile("extest12", "\taaaa\r\nbb\r\n");
      ex.inserttext(" ", 0, 1);
//     try {
//       ex.printout();
//       myassert(false,null);
///     } catch (InputException e) {
//       //ok
//    }
      ex.undo();
      ex.printout();
      ex.disposeFvc();
   }

   static void test13() throws IOException  {
      UI.setStream(new StringReader("f\n"));
      starttest();

      make("extest13.dmp2").delete();
      make("extest13").delete();

      TextEdit<String> ex = newTe("extest13");
      ex.inserttext("dmp", 0, 1);
      ex.disposeFvc();
      ex = newTe("extest13");
      myassert("".equals(ex.at(1)), ex.at(1));
      ex.disposeFvc();
   }

   static void test14() throws IOException {
      UI.setStream(new StringReader(""));
      starttest();

      make("extest14.dmp2").delete();
      make("extest14").delete();

      TextEdit<String> ex = newTe("extest14");
      ex.inserttext("a\n", 0, 1);
      ex.inserttext("b\n", 0, 2);
      ex.inserttext("c\n", 0, 3);
      ex.checkpoint();
      ex.printout();
      ex.disposeFvc();

      ex = newTe("extest14");
      ex.undo();
      ex.redo();
      myassert("a".equals(ex.at(1)), ex.at(1));
      myassert("b".equals(ex.at(2)), ex.at(2));
      myassert("c".equals(ex.at(3)), ex.at(3));
      ex.inserttext("a\n", 0, 1);
      ex.inserttext("b\n", 0, 2);
      ex.inserttext("c\n", 0, 3);
      ex.checkpoint();
      myassert("a".equals(ex.at(1)), ex.at(1));
      myassert("b".equals(ex.at(2)), ex.at(2));
      myassert("c".equals(ex.at(3)), ex.at(3));
      myassert("a".equals(ex.at(4)), ex.at(4));
      myassert("b".equals(ex.at(5)), ex.at(5));
      myassert("c".equals(ex.at(6)), ex.at(6));
      ex.undo();
      ex.undo();
      myassert("".equals(ex.at(1)), ex.at(1));
      myassert(ex.finish() == 2, ex.finish());
      ex.redo();
      ex.redo();
      myassert("a".equals(ex.at(1)), ex.at(1));
      myassert("b".equals(ex.at(2)), ex.at(2));
      myassert("c".equals(ex.at(3)), ex.at(3));
      myassert("a".equals(ex.at(4)), ex.at(4));
      myassert("b".equals(ex.at(5)), ex.at(5));
      myassert("c".equals(ex.at(6)), ex.at(6));
      ex.printout();
      ex.disposeFvc();
   }

   static void test15() throws IOException  {
      UI.setStream(new StringReader(""));
      starttest();

      make("extest15").delete();
      FileDescriptor.LocalFile dd = make("extest15.dmp2");
      dd.delete();

      TextEdit<String> ex = newTe("extest15");
      ex.inserttext("a\n", 0, 1);
      ex.idleSave();
      dd.delete();
      ex.inserttext("b\n", 0, 2);
      ex.inserttext("c\n", 0, 3);
      ex.checkpoint();
      ex.printout();
      try {
         ex.disposeFvc();
         myassert(false, ex);
      } catch (IOException e) {
      }

      ex = newTe("extest15");
      ex.undo();
      myassert("a".equals(ex.at(1)), ex.at(1));
      ex.disposeFvc();
   }

   static void test16() throws IOException {
//??? corrupt backup test
      UI.setStream(new StringReader("bf"));
      starttest();

      makeFile("extest16", "asdfafd\nasdfafdbb");
      makeFile("extest16.dmp2", "asdfafd");

      TextEdit<String> ex = newTe("extest16");
      myassert("".equals(ex.at(1)), ex.at(1));
      myassert(2 == ex.finish(), ex.finish());
      ex.disposeFvc();

      ex = newTe("extest16");
      myassert("asdfafd".equals(ex.at(1)), ex.at(1));
      myassert("asdfafdbb".equals(ex.at(2)), ex.at(2));
      ex.inserttext("a\n", 0, 1);
      ex.idleSave();
      ex.printout();
      ex.disposeFvc();

      ex = newTe("extest16");
      myassert("a".equals(ex.at(1)), ex.at(1));
      myassert("asdfafd".equals(ex.at(2)), ex.at(2));
      myassert("asdfafdbb".equals(ex.at(3)), ex.at(3));
      ex.disposeFvc();
   }

   static void test17() throws IOException  {
      // reload test
      starttest();

      UI.setStream(new StringReader("b"));
      copyFile("extest1", "extest17");
      copyFile("extest1.dmp2", "extest17.dmp2");

      TextEdit<String> ex = newTe("extest17");
      makeFile("extest17", "asdfasf\n");
      ex.reload();
      myassert("asdfasf".equals(ex.at(1)), ex.at(1));
      myassert(ex.finish() == 2, ex.finish());
      ex.disposeFvc();
   }

   /*
   static void dumpev(EditContainer ev) {
     trace("ehistory.dumpev dumping " + ev);
     for (int i =0;i<ev.finish();i++)
        trace(ev.at(i).toString());
   }
   */
   static void test6() throws IOException {

      UI.setStream(new StringReader("bfbb\n"));
      starttest();

      make("extest6.dmp2").delete();

      makeFile("extest6", "aaaa\n\bb\n");
      TextEdit<String> ex = newTe("extest6");
      ex.inserttext("bbb", 0, 1);
      ex.checkpoint();
      ex.idleSave();
      ex.terminate();
//System.exit(0);
      ex = newTe("extest6");
      myassert(ex.at(1).equals("aaaa"), ex.at(1));
      myassert(!ex.isModified(), ex);
      ex.redo();
      myassert(ex.at(1).equals("bbbaaaa"), ex.at(1));
      myassert(ex.isModified(), ex);
      ex.idleSave();
      ex.terminate();

      ex = newTe("extest6");
      myassert(ex.at(1).equals("aaaa"), ex.at(1));
      myassert(!ex.isModified(), ex);
      ex.redo();
      myassert(ex.at(1).equals("aaaa"), ex.at(1));
      myassert(!ex.isModified(), ex);
      ex.idleSave();
      ex.terminate();
//System.exit(0);
      ex = newTe("extest6");
      myassert(ex.at(1).equals("aaaa"), ex.at(1));
      ex.redo();
      ex.inserttext("ccc", 0, 1);
      myassert(ex.at(1).equals("cccbbbaaaa"), ex.at(1));
      ex.checkpoint();
      ex.printout();
      ex.idleSave();
      ex.terminate();
//     ex.dispose();
      ex = newTe("extest6");
      myassert(ex.at(1).equals("cccbbbaaaa"), ex.at(1));
      ex.disposeFvc();
   }

   public static void main(String[] args) {
      UI.init(false);
      try { // forces static initialization that makes debugging more confusing
         TextEdit<String> dummy = newTe("dummy");
         dummy.disposeFvc();
      } catch (IOException e) {
         myassert(false, e);
      }
      try {
         test1();
         test2();
         test3();
         test4();
         test5();
         test6();
         test7();
         test8();
         test9();
         test10();
         test11();
         // tests tabfix whcih changed operation test12();
         test13();
         test14();
         test15();
         test16();
         test17();
         test18();
         stringarrtest();
         insertStreamTest();
         perftest();
         trace("test executed successfully");
         Tools.doGC();
         trace("memory before clear " + (Runtime.getRuntime().totalMemory()
                                         - Runtime.getRuntime().freeMemory()));
         UI.flush();
         Buffers.clearmem();
         FvContext.dump();
         EditContainer.dumpStatic();
         Tools.doGC();
         trace("memory after clear " + (Runtime.getRuntime().totalMemory()
                                        - Runtime.getRuntime().freeMemory()));
         //Thread.sleep(10000000);
      } catch (Throwable e) {
         trace("main caught exception " + e);
         e.printStackTrace();
      }
      System.exit(0);

   }

   private static void insertStreamTest() throws IOException, InputException {
      starttest();

      UI.setStream(new StringReader(""));
      make("exinsertStream.dmp2").delete();
      make("exinsertStream").delete();

      //'makeFile("exinsertStream","");
      TextEdit<String> ex = newTe("exinsertStream");
      ex.inserttext("a\nb\nc\n", 0, 1);
      myassert(ex.at(1).equals("a"), ex.at(1));
      myassert(ex.at(2).equals("b"), ex.at(2));
      myassert(ex.at(3).equals("c"), ex.at(3));

      ex.insertStream(new BufferedReader(new StringReader("z\ny\n")), 2);
      myassert(ex.at(1).equals("a"), ex.at(1));
      myassert(ex.at(2).equals("z"), ex.at(2));
      myassert(ex.at(3).equals("y"), ex.at(3));
      myassert(ex.at(4).equals("b"), ex.at(4));
      myassert(ex.at(5).equals("c"), ex.at(5));
      ex.checkpoint();
      ex.idleSave();
      ex.printout();
      ex.disposeFvc();

//System.exit(0);
      ex = newTe("exinsertStream");
      myassert(ex.at(1).equals("a"), ex.at(1));
      myassert(ex.at(2).equals("z"), ex.at(2));
      myassert(ex.at(3).equals("y"), ex.at(3));
      myassert(ex.at(4).equals("b"), ex.at(4));
      myassert(ex.at(5).equals("c"), ex.at(5));
      myassert(!ex.isModified(), ex);
      ex.idleSave();
      ex.terminate();

   }

   private static void stringarrtest() {

      starttest();
      ArrayList<String> sarr = TextEdit.stringtoarray("1\n\n");


      sarr = TextEdit.stringtoarray("1\n\n");
      Tools.Assert(sarr.size() == 3, sarr.size() + "");
      Tools.Assert(sarr.get(0).equals("1"), sarr.get(0));
      Tools.Assert(sarr.get(1).equals(""), sarr.get(1));
      Tools.Assert(sarr.get(2).equals(""), sarr.get(2));

      sarr = TextEdit.stringtoarray("1\n2\n");
      Tools.Assert(sarr.get(0).equals("1"), sarr.get(0));
      Tools.Assert(sarr.get(1).equals("2"), sarr.get(1));
      Tools.Assert(sarr.get(2).equals(""), sarr.get(2));
      Tools.Assert(sarr.size() == 3, sarr.size() + "");

      sarr = TextEdit.stringtoarray("\n1\n2\n");
      Tools.Assert(sarr.size() == 4, sarr.size() + "");

      Tools.Assert(sarr.get(0).equals(""), sarr.get(0));
      Tools.Assert(sarr.get(1).equals("1"), sarr.get(1));
      Tools.Assert(sarr.get(2).equals("2"), sarr.get(2));
      Tools.Assert(sarr.get(3).equals(""), sarr.get(3));

      sarr = TextEdit.stringtoarray("\n1\n");
      Tools.Assert(sarr.size() == 3, sarr.size() + "");
      Tools.Assert(sarr.get(0).equals(""), sarr.get(0));
      Tools.Assert(sarr.get(1).equals("1"), sarr.get(1));
      Tools.Assert(sarr.get(2).equals(""), sarr.get(2));

      sarr = TextEdit.stringtoarray("1\n");
      Tools.Assert(sarr.size() == 2, sarr.size() + "");
      Tools.Assert(sarr.get(0).equals("1"), sarr.get(0));
      Tools.Assert(sarr.get(1).equals(""), sarr.get(1));

      sarr = TextEdit.stringtoarray("\n");
      Tools.Assert(sarr.size() == 2, sarr.size() + "");
      Tools.Assert(sarr.get(0).equals(""), sarr.get(0));
      Tools.Assert(sarr.get(1).equals(""), sarr.get(1));

      sarr = TextEdit.stringtoarray("\n1");
      Tools.Assert(sarr.size() == 2, sarr.size() + "");
      Tools.Assert(sarr.get(0).equals(""), sarr.get(0));
      Tools.Assert(sarr.get(1).equals("1"), sarr.get(1));

      sarr = TextEdit.stringtoarray("1\n2");
      Tools.Assert(sarr.size() == 2, sarr.size() + "");
      Tools.Assert(sarr.get(0).equals("1"), sarr.get(0));
      Tools.Assert(sarr.get(1).equals("2"), sarr.get(1));

      sarr = TextEdit.stringtoarray("\n1\n2");
      Tools.Assert(sarr.size() == 3, sarr.size() + "");
      Tools.Assert(sarr.get(0).equals(""), sarr.get(0));
      Tools.Assert(sarr.get(1).equals("1"), sarr.get(1));
      Tools.Assert(sarr.get(2).equals("2"), sarr.get(2));

      sarr = TextEdit.stringtoarray("1\n2\n");
      Tools.Assert(sarr.size() == 3, sarr.size() + "");
      Tools.Assert(sarr.get(0).equals("1"), sarr.get(0));
      Tools.Assert(sarr.get(1).equals("2"), sarr.get(1));
      Tools.Assert(sarr.get(2).equals(""), sarr.get(2));
   }
}
