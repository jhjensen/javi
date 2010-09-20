package javi;
import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

final class MoveGroup extends Rgroup {
   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";
   private static final MoveGroup inst = new MoveGroup();

   static void init() { /* forces static inst filed to be newed */
   }

   private int searchOffset = 0;
   private EditContainer.SearchType searchOffsetType =
      EditContainer.SearchType.LINE;

   private char lastfindchar;
   private boolean lstforward;
   private boolean lstat;
   private boolean searchdir = false;
   private Position lastmark;
   private Position lastmark2;
   private HashMap<Integer, Position> markpos =
      new HashMap<Integer, Position>();
   private Matcher[] brega;
   private Matcher breg;
   private int dotcommand;
   private int dotcount;
   private int dotrcount;
   private boolean dotrev;
   private Object dotarg;

   private MoveGroup() {
      final String[] rnames = {
         "",
         "movechar",
         "moveline",
         "forwardword",
         "forwardWord",
         "backwardword",   //5
         "backwardWord",
         "endword",
         "endWord",
         "starttext",
         "balancechar",   //10
         "shiftmoveline",
         "movelinestart",
         "screenmove",
         "findchar",
         "repeatfind",   //15
         "regsearch",
         "searchcommand",
         "linepos",
         "gotoline",
         "findmark",   //20
         "mark",
         "forwardregex",
         "backwardregex",
         "moveover",
         "movescreen",    //25
         "unusedm1",
         "movescreenline",
      };
      register(rnames);
   }

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws InputException, IOException {
      if (!dotmode && (rnum != 24)) {
         dotcommand = rnum;
         dotcount = count;
         dotrcount = rcount;
         dotarg = arg;
         dotrev = false;
      }
      //trace("rnum = " + rnum);
      switch (rnum) {
         case 1:
            movechar(((Boolean) arg).booleanValue(), count, fvc);
            return null;
         case 2:
            moveline(((Boolean) arg).booleanValue(), count, fvc);
            return null;
         case 3:
            if (dotrev)
               backwardword(count, fvc);
            else
               forwardword(count, fvc);
            return null;
         case 4:
            if (dotrev)
               backwardWord(count, fvc);
            else
               forwardWord(count, fvc);
            return null;
         case 5:
            if (dotrev)
               forwardword(count, fvc);
            else
               backwardword(count, fvc);
            return null;
         case 6:
            if (dotrev)
               forwardWord(count, fvc);
            else
               backwardWord(count, fvc);
            return null;
         case 7:
            endword(count, fvc);
            return null;
         case 8:
            endWord(count, fvc);
            return null;
         case 9:
            starttext(fvc);
            return null;
         case 10:
            balancechar(fvc);
            return null;
         case 11:
            shiftmoveline(((Boolean) arg).booleanValue(), count, fvc);
            return null;
         case 12:
            movelinestart(arg, count, fvc);
            return null;
         case 13:
            screenmoveabs(arg, rcount, fvc);
            return null;
         case 14:
            findchar((boolean []) arg, fvc, dotmode);
            return null;
         case 15:
            repeatfind((boolean []) arg, fvc);
            return null;
         case 16:
            regsearch(((Boolean) arg).booleanValue(), count, fvc);
            return null;
         case 17:
            searchcommandI(((Boolean) arg).booleanValue(), count, fvc, dotmode);
            return null;
         case 18:
            linepos(arg, rcount, fvc);
            return null;
         case 19:
            gotoline(count, rcount, fvc, arg);
            return null;
         case 20:
            findmark(fvc, EventQueue.nextKey(fvc.vi));
            return null;
         case 21:
            mark(fvc, EventQueue.nextKey(fvc.vi));
            return null;
         case 22:
            rsearch((Matcher) arg, false, count, fvc);
            return null;
         case 23:
            rsearch((Matcher) arg, true, count, fvc);
            return null;
         case 24:
            if (dotcommand != 0) {
               if (rcount == 0)
                  count = dotcount;
               dotcount = count;
               dotrev = ((Boolean)  arg).booleanValue();
               //trace("dotrev = " + dotrev);
               return doroutine(dotcommand, dotarg, dotcount, dotrcount,
                  fvc, true);
            }
            return null;
         case 25:
            screenmoverel(arg, count, fvc);
            return null;
         case 26:
            return null;  //movehalfscreen(arg,count,fvc.vi); return null;
         case 27:
            fvc.screeny((((Integer) arg).intValue() == 1)
               ? count
               : -count);
            return null;
         default:
            throw new RuntimeException();
      }
   }
   private static boolean isalphanum(char c) {
      return (
                (c >= 'a' && c <= 'z')
                ||  (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_'
             );
   }

   private void linepos(Object arg, int rcount, FvContext fvc) {
      if (arg == null)
         fvc.cursorx(rcount - fvc.insertx());
      else {
         int pos = ((Integer) arg).intValue();
         if (pos == 0)
            fvc.cursorx(rcount - fvc.insertx() + rcount);
         else
            fvc.cursorx(pos - fvc.insertx() + rcount);
      }
   }

   private void gotoline(int count, int rcount, FvContext fvc, Object arg) {
      if (rcount == 0) {
         if (arg == null)
            fvc.cursorabs(Integer.MAX_VALUE, fvc.edvec.finish());
         else
            fvc.cursorabs(0, ((Integer) arg).intValue());
      } else
         fvc.cursoryabs(count);
   }

   private void mark(FvContext fvc, char bufid) {
      //trace("lastmark = " + lastmark + "lastmark2= " + lastmark2);
      if (bufid == 27)
         return;
      EditContainer ev = fvc.edvec;

      Integer idObj = Integer.valueOf(bufid);
      Position pos = markpos.get(idObj);
      if (pos != null) {
         EditContainer ev1 = EditContainer.findfile(pos.filename);
         if (ev1 != null)
            ev1.unfixposition(pos);
      }
      pos = fvc.getPosition("mark " + bufid);
      markpos.put(idObj, pos);
      ev.fixposition(pos);

      if (!fvc.equals(lastmark2))
         lastmark2 = pos;

      //trace("exiting lastmark = " + lastmark + " lastmark2 " + lastmark2);
   }

   private void findmark(FvContext fvc, char c) throws InputException {
      //trace("lastmark = " + lastmark + " lastmark2= " + lastmark2);

      if (c == 27)
         return;
      if (c == '\'') {
         if (fvc.equals(lastmark)) {
            Position temp = lastmark;
            lastmark = lastmark2;
            lastmark2 = temp;
         }
      } else {
         if (fvc.equals(lastmark2)) {
            lastmark = lastmark2;
         }

         lastmark2 = lastmark;

         lastmark = markpos.get(Integer.valueOf(c));
      }
      if (lastmark != null)
         FileList.gotoposition(lastmark, false, fvc.vi);
   }

   private static boolean iswhite(char c) {
      return (c == ' ' || c == 9 || c == '\n' || c == '\r');
   }

   private void rsearch(Matcher exp, boolean dir, int count, FvContext fvc) {
      //trace("fvc = " + fvc + " lastmark2 = " + lastmark2);
      Position startpos = fvc.getPosition(null);
      int i = 0;
      do {
         startpos = fvc.edvec.regsearch(startpos, startpos,
               dotrev ^ dir ^ searchdir, exp, searchOffset, searchOffsetType);
         if (null == startpos)
            return;
      } while (++i < count);

//      startpos.filename=fvc.edvec.canonname();
//      startpos.comment="mark " + exp;
      lastmark2 = lastmark;
      lastmark = startpos;
      fvc.cursorabs(startpos);

   }

   private void regsearch(boolean dir, int count, FvContext fvc) {
      rsearch(GState.getRegex(), dir, count, fvc);
   }

//??? move to editvec?
   static void dosearch(boolean direction, int count, FvContext fvc,
         String line) throws InputException {
      inst.dosearchI(direction, count, fvc, line, 0);
   }

   private void dosearchI(boolean direction, int count, FvContext fvc,
         String line, int flags) throws InputException {
      try {
         GState.setRegex(line, flags);
      } catch (Throwable e) {
         throw new InputException("fail to compile regular exception " + e, e);
      }
      //trace("line = " + line + " regex = " + searchRegex);
      searchdir = direction;
      regsearch(false, count, fvc);
      return;
   }

   static void searchcommand(boolean direction, int count, FvContext fvc,
         boolean dotmode) throws InputException {
      inst.searchcommandI(direction, count, fvc, dotmode);
   }

   private void searchcommandI(boolean direction, int count, FvContext fvc,
         boolean dotmode) throws InputException {

      if (dotmode)
         regsearch(false, count, fvc);

      else {
         String line  = Command.getcomline(!direction ? "/" : "?");
         line = line.substring(1, line.length());
         if ("".equals(line))
            regsearch(false, count, fvc);
         else {
            searchOffset = 0;
            int flags = 0;
            searchOffsetType = EditContainer.SearchType.LINE;

            int slashIndex = line.lastIndexOf('/');
            if (slashIndex != -1) {
               boolean minusFlag = false;
               if (slashIndex > 0 && line.charAt(slashIndex - 1) != '\\')
                  for (int lindex = slashIndex; ++lindex < line.length();)
                     switch (line.charAt(lindex)) {
                        case 'i':
                           flags |= Pattern.CASE_INSENSITIVE;
                           break;
                        case 'v':
                           flags |= Pattern.LITERAL;
                           break;
                        case 'e':
                           searchOffsetType = EditContainer.SearchType.END;
                           break;
                        case 'b':
                        case 's':
                           searchOffsetType = EditContainer.SearchType.START;
                           break;
                        case '-':
                           minusFlag = true;
                           break;
                        case '+':
                           minusFlag = false;
                           break;
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                        case '8':
                        case '9':
                           trace("searchOffset = " + searchOffset);
                           searchOffset = searchOffset * 10
                              + (line.charAt(lindex) - '0');
                           trace("searchOffset = " + searchOffset);
                           break;
                        default:
                           throw new InputException("invalid regex modifier");
                     }
               if (minusFlag)
                  searchOffset = -searchOffset;
               line = line.substring(0, slashIndex);
            }
            dosearchI(direction, count, fvc, line, flags);
         }
      }
      return;
   }

   private void findchar(boolean [] arg, FvContext fvc, boolean dotmode) throws
         ExitException {
      if (!dotmode) {
         char tmp = EventQueue.nextKey(fvc.vi);
         if (tmp == 27) // esc
            return;
         lastfindchar = tmp;
      }
      lstforward = arg[0];
      lstat = arg[1];
      findchar(lastfindchar, arg[0], arg[1], fvc);
   }

   private void repeatfind(boolean[] samedir, FvContext fvc) {
      if (samedir[0])
         findchar(lastfindchar, lstforward, lstat, fvc);
      else
         findchar(lastfindchar, !lstforward, lstat, fvc);
   }


   private void findchar(char fchar, boolean forward, boolean atchar,
         FvContext fvc) {
      //trace("forward = " + forward + " dotrev = " + dotrev);
      forward ^= dotrev;
      //trace("forward = " + forward );
      if (forward)
         if (atchar)
            findcharf(fchar, fvc);
         else
            findchart(fchar, fvc);
      else if (atchar)
         findcharF(fchar, fvc);
      else
         findcharT(fchar, fvc);
   }


   private void findcharf(char findchar, FvContext fvc) {
      int xindex;
      String line = fvc.at().toString();
      for (xindex = fvc.insertx() + 1; xindex < line.length(); xindex++)
         if (line.charAt(xindex) == findchar) {
            fvc.cursorx(xindex - fvc.insertx());
            return;
         }
   }

   private void findcharF(char findchar, FvContext fvc) {
      int xindex;
      String line = fvc.at().toString();
      for (xindex = fvc.insertx() - 1; xindex >= 0; xindex--)
         if (line.charAt(xindex) == findchar) {
            fvc.cursorx(xindex - fvc.insertx());
            return;
         }
   }

   private void findchart(char findchar, FvContext fvc) {
      int xindex;
      String line = fvc.at().toString();
      for (xindex = fvc.insertx() + 2; xindex < line.length(); xindex++)
         if (line.charAt(xindex) == findchar) {
            fvc.cursorx(xindex - fvc.insertx() - 1);
            return;
         }
   }

   private void findcharT(char findchar, FvContext fvc) {
      int xindex;
      String line = fvc.at().toString();
      for (xindex = fvc.insertx() - 2; xindex >= 0; xindex--)
         if (line.charAt(xindex) == findchar) {
            fvc.cursorx(xindex - fvc.insertx() + 1);
            return;
         }
   }
   private void screenmoveabs(Object arg, int rcount, FvContext fvc) {
      float amount = ((Float) arg).floatValue();
      fvc.cursoryabs(fvc.vi.screenFirstLine()
         + (int) Math.floor(fvc.vi.getRows(amount)) + rcount);
      fvc.placeline(fvc.inserty(), amount);
   }

   private void screenmoverel(Object arg, int count, FvContext fvc) {
      float amount = ((Float) arg).floatValue();
      fvc.cursory((int) Math.floor((float) (
                                     fvc.vi.getRows(amount) - 1) + count));
   }
   private void movelinestart(Object arg, int count, FvContext fvc) {
      if (((Integer) arg).intValue() == 1)
         fvc.cursory(count);
      else
         fvc.cursory(-count);
      starttext(fvc);
   }

   private void movechar(boolean reverse, int count, FvContext fvc) {
      if (reverse ^ dotrev)
         fvc.cursorx(count);
      else
         fvc.cursorx(-count);
   }

   private void moveline(boolean reverse , int count, FvContext fvc) {
      if (reverse ^ dotrev)
         fvc.cursory(count);
      else
         fvc.cursory(-count);
   }

   private void shiftmoveline(boolean reverse,
         int count, FvContext fvc) {
      int amount =  (int) Math.ceil(Math.sqrt(fvc.vi.getRows((float) 1.)));
      if (reverse ^ dotrev)
         fvc.cursory(count * amount);
      else
         fvc.cursory(-count * amount);
   }

   static void starttext(FvContext fvc) {

      int i = 0;
      String line = fvc.at().toString();

      while ((i < line.length()) && iswhite(line.charAt(i)))
         i++;
      fvc.cursorxabs(i);
   }

   private void forwardPat(int count, FvContext fvc, Matcher pat)  {
      int xindex = fvc.insertx();
      int yindex = fvc.inserty();

      String line = fvc.at().toString();
      pat.reset(line);
      while (count > 0) {
         if (pat.find(xindex)
               && (pat.start() + 1 != line.length())) {
            xindex = 1 + pat.start();
            count--;
         } else if (fvc.edvec.containsNow(yindex + 1)) {
            xindex = 0;
            line = fvc.at(++yindex).toString();
            if (line.length() == 0 || !iswhite(line.charAt(0)))
               count--;
            else
               pat.reset(line);
         } else
            break;
      }
      fvc.cursorabs(xindex, yindex);
   }

   private void forwardword(int count, FvContext fvc)  {
      forwardPat(count, fvc, wordpat);
   }
   private void forwardWord(int count, FvContext fvc)  {
      forwardPat(count, fvc, wordPat);
   }

   private static void endPat(int count, FvContext fvc, Matcher pat)  {
      int xindex = fvc.insertx();
      int yindex = fvc.inserty();

      String line = fvc.at(yindex).toString();
      //trace("endPat xindex " + xindex + " yindex "  + yindex + " line:" + line);
      pat.reset(line);
      while (count > 0) {
         if (pat.find(xindex)) {
            xindex = 1 + pat.start();
            count--;
         } else if (fvc.edvec.containsNow(yindex + 1)) {
            xindex = 0;
            line = fvc.at(++yindex).toString();
            pat.reset(line);
         } else
            break;
      }
      fvc.cursorabs(xindex, yindex);
   }

   private static Matcher endpat = Pattern.compile(
      "([^ \ta-zA-Z_0-9](\\w|$))|(\\S(\\s|$))|(\\w(\\W|$))").matcher("");
   private static void endword(int count, FvContext fvc)  {
      endPat(count, fvc, endpat);
   }

   private static Matcher endpatW = Pattern.compile("\\S(\\s|$)").matcher("");
   private void endWord(int count, FvContext fvc)  {
      endPat(count, fvc, endpatW);
   }

   int searchBackward2(Matcher reg, String str, int start, int offset) {
      //trace("searchBackward start = " + start  + " offset = " +offset + "line:" + str);

      reg.reset(str);
      int lastfound = -1;

      if (!reg.find())
         return -1;

      do {
         int newF = reg.start();
         if (newF + offset >= start)
            break;
         lastfound = newF;
      } while (reg.find(reg.start() + 1));
      return lastfound == -1
         ? -1
         : lastfound + offset;
   }


   private void backwardPattern(int count, FvContext fvc, Matcher pat)  {
      int yindex = fvc.inserty();
      int xindex = fvc.insertx();
      String line = fvc.edvec.at(yindex).toString();
      while (count > 0) {
         int offset = searchBackward2(pat, line, xindex, 1);
         if (offset != -1) {
            xindex = offset;
            count--;
         } else if (xindex != 0
                           && line.length() > 0
                           && !iswhite(line.charAt(0))) {
            xindex = 0;
            count--;
         } else if (yindex > 1) {
            --yindex;
            line = fvc.edvec.at(yindex).toString();
            xindex = line.length();
         } else
            break;
      }
      fvc.cursorabs(xindex, yindex);
   }

   private static Matcher wordpat = Pattern.compile(
      "(\\w[^ \ta-zA-Z_0-9])|(\\s\\S)|(\\W\\w)").matcher("");
   private void backwardword(int count, FvContext fvc)  {
      backwardPattern(count, fvc, wordpat);
   }

   private static Matcher wordPat = Pattern.compile(
      "\\s\\S").matcher("");
   private void backwardWord(int count, FvContext fvc)  {
      backwardPattern(count, fvc, wordPat);
   }

   static final String[] bchars1 = {
      "(<)|(>)",
      "(\\{)|(\\})",
      "(\\[)|(\\])",
      "(\\()|(\\))",
      "(\\/\\*)|(\\*\\/)",
      "(^[ \\t]*#if)|(^[ \\t]*#ifdef)|(^[ \\t]*#endif)"
         + "|(^[ \\t]*#else)|(^[ \\t]*#elif)",
      "(^[ \\t]*ifdef)|(^[ \\t]*ifndef)|(^[ \\t]*ifeq)|"
         + "(^[ \\t]*ifneq)|(^[ \\t]*endif)|(^[ \\t]*else)"
   };
   private void initbalance() {
      brega = new Matcher[bchars1.length];
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < bchars1.length; i++) {
         sb.append(bchars1[i]);
         sb.append('|');
         brega[i] = Pattern.compile(bchars1[i]).matcher("");
      }

      sb.setLength(sb.length() - 1);
      breg = Pattern.compile(sb.toString()).matcher("");
   }

//??? could use editvec searchBackward
   private boolean searchBackward(Matcher matcher, String str, int start) {

      //trace("searchBackward: start = " + start  + " " + str);
      str = str.substring(0, start);
      matcher.reset(str);
      int temp = -1;

      while (matcher.find())
         temp = matcher.start(0);

      if (temp == -1)
         return false;

      matcher.reset();
      while (matcher.find())
         if (temp == matcher.start(0))
            return true;
      throw new RuntimeException("Regexp.java reverse search logic error");

   }

   private static int calcinc(int bindex, MatchResult reg) {
      switch (bindex) {
         case 5:
            //trace("ccount 1 = " + ccount);
            if ((-1 != reg.start(1)))
               return 1;
            else if ((-1 != reg.start(2)))
               return 1;
            else if ((-1 != reg.start(3)))
               return -1;
            else if ((-1 != reg.start(4)))
               return 0;
            else if ((-1 != reg.start(5)))
               return 0;
            else
               throw new RuntimeException(
                  "unexpected match number for calcinc");
         case 6:
            //trace("ccount 1 = " + ccount);
            if ((-1 != reg.start(1)))
               return 1;
            else if ((-1 != reg.start(2)))
               return 1;
            else if ((-1 != reg.start(3)))
               return 1;
            else if ((-1 != reg.start(4)))
               return 1;
            else if ((-1 != reg.start(5)))
               return -1;
            else if ((-1 != reg.start(6)))
               return 0;
            else
               throw new RuntimeException(
                  "unexpected match number for calcinc");
         default:
            return (-1 == reg.start(1))
                   ? -1
                   : 1;
      }
   }

   private void balancechar(FvContext fvc) {
      if (brega == null)
         try {
            initbalance();

         } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(
               " unexpected exception in balancechar", e);
         }
      String line = fvc.at().toString();

      //trace("line:" + line);
      breg.reset(line);
      if (!breg.find(fvc.insertx()))
         if (!searchBackward(breg, line, fvc.insertx())) {
            breg.reset(line);
            if (!breg.find())
               return;
         }

      int xindex = breg.start(0);
      //trace("xindex = " + xindex + " setting bindex = " + bindex + " " + breg);

      int bindex = 0;
      for (bindex = 1; bindex < 22; bindex++) {
         //trace("trying bindex = " + bindex + " " + breg.start(bindex));
         if (-1 != breg.start(bindex))
            break;
      }
      bindex--; // compensate for getMatch being 1 based

      boolean forward;

      if (bindex == 22)
         throw new RuntimeException("movegroup.balancechar: bad reg index");

      else if (bindex >= 15) { // if case is special
         forward = bindex != 19;
         bindex = 6;

      } else if (bindex >= 10) { // ifdef case is special
         forward = bindex != 12;
         bindex = 5;
      } else {
         forward = ((bindex % 2) == 0);
         bindex /= 2;
      }

      //trace("forward = " + forward);
      Matcher reg = brega[bindex];
      reg.reset(line);
      int ccount = forward
                   ? 1
                   : -1;

      int yindex = fvc.inserty();
      if (forward) {  // forward search
         do  {
            //trace("x = " + x + " searching: " + line.substring(x+1,line.length())); trace("forward result = "  + reg.searchForward(line,x+1));
            if ((++xindex > line.length()) || (!reg.find(xindex)))
               if (!fvc.edvec.containsNow(++yindex))
                  return;
               else {
                  line = fvc.at(yindex).toString();
                  reg.reset(line);
                  //trace("y  = " + y + " line = " + line);
                  xindex = -1;
                  continue;
               }

            xindex = reg.start();
            //trace("got x =" + x + " char = " + line.charAt(x));
            int inc =  calcinc(bindex, reg);
            ccount += inc == 0 && ccount == 1
                      ? -1
                      : inc;
         } while (ccount != 0);
      } else {
         do {
            //if(x >=0) { trace("searching: " + line.substring(0,x)); trace(" backward res = "  + (result = reg.searchBackward(line,x-1))); }
            if ((xindex < 0) || (!searchBackward(reg, line, xindex)))
               if ((--yindex) == 0)
                  return;
               else {
                  line = fvc.at(yindex).toString();
                  reg.reset(line);
                  xindex = line.length();
                  continue;
               }
            xindex = reg.start();
            //trace("got x =" + xindex + " char = " + line.charAt(xindex));
            ccount += calcinc(bindex, reg);

            //trace("ccount " +ccount + " y " + y);
         } while (ccount != 0);


      }
      fvc.cursorabs(xindex, yindex);
   }

}
