package javi;
import java.io.IOException;
import java.util.ArrayList;
import static history.Tools.trace;

final class EditGroup extends Rgroup {

   private int dotcommand;
   private char dotbufid;
   private JeyEvent dotevent2;
   private JeyEvent dotevent3;
   private int dotcount = 1;
   private int dotrcount = 0;
   private char dotchar;
   private Object dotarg;

   EditGroup() {
      final String[] rnames = {
         "",
         "insert",
         "Insert",
         "append",
         "Append",
         "openline",     //5
         "Openline",
         "substitute",
         "Substitute",
         "deletechars",
         "deletetoend",  //10
         "deletetoendi",
         "deletemode",
         "joinlines",
         "subchar",
         "changecase",   //15
         "changemode",
         "putbefore",
         "putafter",
         "qmode",
         "yankmode",     //20
         "yank",
         "doover",
         "markmode",
         "egunused1",
         "egunused2",           //25
         "shiftmode",
         "tabfix"
      };

      register(rnames);
   }

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext  fvc, boolean dotmode) throws
         InterruptedException, IOException, InputException {
      //trace("rnum = " + rnum + " count = " + count + " rcount = " + rcount);
      if (!dotmode && !(rnum >= 20 && rnum <= 22)) {
         dotcommand = rnum;
         dotcount = count;
         dotrcount = rcount;
         dotarg = arg;
         dotrcount = rcount;
      }

      switch (rnum) {
         case 1:
            boolean[] a2 = (boolean[]) arg;
            InsertBuffer.insertMode(dotmode, count, fvc, a2[0], a2[1]);
            break;
         case 2:
            fvc.cursorxabs(0);
            InsertBuffer.insertMode(dotmode, count, fvc, false, false);
            break;
         case 3:
            fvc.cursorx(1);
            InsertBuffer.insertMode(dotmode, count, fvc, false, false);
            break;
         case 4:
            fvc.cursorxabs(Integer.MAX_VALUE);
            InsertBuffer.insertMode(dotmode, count, fvc, false, false);
            break;
         case 5:
            fvc.cursorxabs(Integer.MAX_VALUE);
            fvc.inserttext("\n");
            fvc.cursory(1);
            InsertBuffer.insertMode(dotmode, count, fvc, false, false);
            break;
         case 6:
            fvc.cursorxabs(0);
            fvc.inserttext("\n");

            InsertBuffer.insertMode(dotmode, count, fvc, false, false);
            break;
         case 7:
            substitute(dotmode, count, fvc);
            break;
         case 8:
            ucSubstitute(dotmode, count, fvc);
            break;
         case 9:
            boolean[] a1 = (boolean[]) arg;
            fvc.deleteChars('0', a1[0], a1[1], count);
            break;
         case 10:
            deletetoend('0', count, fvc);
            break;
         case 11:
            deletetoend('0', count, fvc);
            InsertBuffer.insertMode(dotmode, count, fvc, false, false);
            break;
         case 12:
            deletemode('0', dotmode, count, rcount, fvc);
            break;
         case 13:
            fvc.cursorxabs(fvc.edvec.joinlines(count, fvc.inserty()));
            break;
         case 14:
            subChar(dotmode, count, fvc);
            break;
         case 15:
            fvc.edvec.changecase(fvc.insertx(), fvc.inserty(),
               fvc.insertx() + count, fvc.inserty());
            fvc.cursorx(count);
            break;
         case 16:
            changemode('0', dotmode, count, rcount, fvc);
            break;
         case 17:
            putbuffer('0', false, fvc);
            break;
         case 18:
            putbuffer('0', true, fvc);
            break;
         case 19:
            qmode(count, rcount, dotmode, fvc);
            break;
         case 20:
            yankmode('0', false, count, rcount, fvc);
            break;
         case 21:
            ArrayList<String> bufs = fvc.getElementsAt(count);
            //trace("yank " + count + " lines ");
            Buffers.deleted('0', bufs);
            break;
         case 22:
            if (0 != dotcommand) {
               if (0 == rcount)
                  count = dotcount;
               dotcount = count;
               return doroutine(dotcommand, dotarg, dotcount,
                  dotrcount, fvc, true);
            }
            return null;
         case 23:
            markmode('0', dotmode, count, rcount, fvc,
               1 == ((Integer) arg).intValue());
            break;
         case 24:
         case 25:
            return null;

         case 26:
            shiftmode(((Integer) arg).intValue(), count, fvc, dotmode, rcount);
            break;

         case 27:
            fvc.edvec.tabfix(fvc.vi.getTabStop());
            break;
         default:
            throw new RuntimeException("invalid doroutine index");
      }
      fvc.edvec.checkpoint();
      fvc.fixCursor();
      return null;
   }

   private void shiftmode(int direction, int count, FvContext fvc,
         boolean dotmode, int rcount) throws
            InterruptedException, IOException, InputException {
      if (!dotmode)
         dotevent3 = EventQueue.nextKeye(fvc.vi);
      JeyEvent event = dotevent3;

      switch(event.getKeyChar()) {
         case '<':
            fvc.cursorx(fvc.edvec.shiftleft(fvc.inserty(), count));
            break;
         case '>':
            fvc.cursorx(fvc.edvec.shiftright(fvc.inserty(), count));
            break;
         case 27: // esc
            break;
         default:
            int yold = fvc.inserty();
            int starty, amount;
            if (!MapEvent.domovement(event, count, rcount, dotmode, fvc))
               break;
            if (yold < fvc.inserty()) {
               starty = yold;
               amount = fvc.inserty() - yold + 1;
            } else  {
               starty = fvc.inserty();
               amount = yold - starty + 1;
            }
            if (1 != direction)
               fvc.cursorx(fvc.edvec.shiftleft(starty, amount));
            else
               fvc.cursorx(fvc.edvec.shiftright(starty, amount));
            break;
      }
   }

   private int donex, markamount;

   private void markmode(char bufid, boolean dotmode, int count,
         int rcount, FvContext fvc, boolean vMode) throws
         InputException, IOException, InterruptedException {
      int starty, startx, doney;
      int xold = 0;
      int yold = 0;
      if (!dotmode) {
         MovePos markpos = fvc.vi.getMark();
         if (markpos == null) {
            if (!vMode)
               xold = fvc.insertx();
            yold = fvc.inserty();
            fvc.setMark();
         } else {
            xold = markpos.x;
            yold = markpos.y;
         }
      }
   out:
      try {
         do {
            JeyEvent event;
            if (!dotmode) {
               do {
                  event = EventQueue.nextKeye(fvc.vi);
               } while (MapEvent.domovement(
                   event, count, rcount, dotmode, fvc));

               dotevent3 = event;
               if (vMode)
                  fvc.cursorxabs(Integer.MAX_VALUE);
               if (yold < fvc.inserty()) {
                  starty = yold;
                  startx = xold;
                  donex = fvc.insertx();
                  doney = fvc.inserty();
                  markamount = fvc.inserty() - yold + 1;
               } else  {
                  starty = fvc.inserty();
                  startx = fvc.insertx();
                  donex = xold;
                  doney = yold;
                  markamount = yold - starty + 1;
                  if ((yold == fvc.inserty()) && (donex < startx)) {
                     int temp = startx;
                     startx = donex;
                     donex = temp;
                  }
               }
            } else {
               bufid = dotbufid;
               event = dotevent3;
               starty = fvc.inserty();
               startx = fvc.insertx();
               doney = starty + markamount - 1;
               if (vMode) {
                  donex = Integer.MAX_VALUE;
                  startx = 0;
               } else {
                  if (1 == markamount)
                     donex += startx;
                  startx = fvc.insertx();
               }
            }
            char key = event.getKeyChar();
            try {
               switch(key) {
                  case 'o':
                     MovePos markpos = fvc.vi.getMark();
                     xold = fvc.insertx();
                     yold = fvc.inserty();
                     fvc.setMark();
                     fvc.cursorabs(markpos);
                     continue;
                  case 'd':
                     deletetext(bufid, fvc, false, startx,
                        starty, donex, doney);
                     break out;
                  case 'y':
                     deletetext(bufid, fvc, true, startx, starty, donex, doney);
                     break out;
                  case 'v':
                  case 'V':
                  case 27: // esc
                     break out;
                  case 'Y':
                     Buffers.deleted(bufid,
                        fvc.edvec.getElementsAt(starty, markamount));
                     break out;
                  case 'D':
                     fvc.edvec.finish();
                     if (!fvc.edvec.containsNow(starty + markamount - 1))
                        markamount = fvc.edvec.finish() - 1;
                     Buffers.deleted(bufid,
                        fvc.edvec.remove(starty, markamount));
                     fvc.edvec.checkpoint();
                     fvc.fixCursor();
                     break out;
                  case '~':
                     fvc.edvec.changecase(startx, starty, donex, doney);
                     break out;
                  case 'J':
                     fvc.cursorabs(fvc.edvec.joinlines(markamount, starty),
                                   starty);
                     break out;
                  case '<':
                     fvc.cursorx(fvc.edvec.shiftright(starty, markamount));
                     break out;
                  case '>':
                     fvc.cursorx(fvc.edvec.shiftleft(starty, markamount));
                     break out;
                  case 'S':
                  case 's':
                     fvc.cursorabs(startx, starty);
                     String line = fvc.edvec.gettext(startx,
                        starty, donex, doney);
                     MoveGroup.dosearch('S' == key, 1, fvc, line);
                     break out;
                  case '!':
                     line = fvc.edvec.gettext(startx,
                        starty, donex, doney);
                     JS.JSR.eval(line);
                     break out;
                  case 12:
                     MiscCommands.redraw(true);
                     continue;
                  case 29:
                     line = fvc.edvec.gettext(startx, starty, donex, doney);
                     try {
                        Rgroup.doCommand("gototag", line, 1, 1, fvc, false);
                     } catch (IOException e) {
                        throw new RuntimeException(
                           "editgroup.markmode got unexpected ", e);
                     } catch (InterruptedException e) {
                        UI.popError("caught int", e);
                     }

                     break out;
                  default:
                     continue;
               }
            } catch (EditContainer.ReadOnlyException e) {
               fvc.vi.clearMark();
               throw e;
            }
         } while (!dotmode);
      } finally {
         fvc.vi.clearMark();
      }
   }

   private void qmode(int count, int rcount,
         boolean dotmode, FvContext fvc) throws
         InterruptedException, IOException, InputException {

      JeyEvent event;
      char bufid;

      if (!dotmode) {
         bufid = (EventQueue.nextKeye(fvc.vi).getKeyChar());
         event = EventQueue.nextKeye(fvc.vi);
      } else {
         bufid = dotbufid;
         event = dotevent2;
      }

      switch(event.getKeyChar()) {
         case 'p':
            if (dotmode && bufid >= '0' && bufid <= '8')
               bufid++;
            putbuffer(bufid, true, fvc);
            dotbufid = bufid;
            dotevent2 = event;
            break;
         case 'P':
            putbuffer(bufid, false, fvc);
            dotbufid = bufid;
            dotevent2 = event;
            break;
         case 'd':
            deletemode(bufid, dotmode, count, rcount, fvc);
            dotbufid = bufid;
            dotevent2 = event;
            break;
         case 'y':
            yankmode(bufid, dotmode, count, rcount, fvc);
            dotbufid = bufid;
            dotevent2 = event;
            break;
         case 'c':
            changemode(bufid, dotmode, count, rcount, fvc);
            dotbufid = bufid;
            dotevent2 = event;
            break;
         case 'X' :
         case 127 :
            fvc.deleteChars(bufid, false, false, count);
            dotbufid = bufid;
            dotevent2 = event;
            break;
         case 'x' :
            fvc.deleteChars(bufid, true, true, count);
            dotbufid = bufid;
            dotevent2 = event;
            break;
         case 'D' :
            deletetoend(bufid, count, fvc);
            dotbufid = bufid;
            dotevent2 = event;
            break;
         case 'C' :
            deletetoend(bufid, count, fvc);
            InsertBuffer.insertMode(dotmode, count, fvc, false, false);
            dotbufid = bufid;
            dotevent2 = event;
            break;
         case 'Y' :
            Buffers.deleted(bufid, fvc.getElementsAt(count));
            dotbufid = bufid;
            dotevent2 = event;
            break;
         case 'v' :
            markmode(bufid, dotmode, count, rcount, fvc, false);
            dotbufid = bufid;
            dotevent2 = event;
            break;
         case 'V' :
            markmode(bufid, dotmode, count, rcount, fvc, true);
            dotbufid = bufid;
            dotevent2 = event;
            break;
         case 27: //escape
         default:
            break;
      }
   }

   private static void putbuffer(char id, boolean after, FvContext fvc)  {

      Object buf = Buffers.getbuf(id);
      //trace("putbuffer id " + id  + " buf " + buf);
      if (null == buf)
         return;

      if (buf instanceof String) {
         if (after)
            fvc.cursorx(1);
         fvc.cursorabs(fvc.inserttext((String) buf));
      } else {
         ArrayList<String> buf2 = (ArrayList<String>) buf;
         fvc.insertStrings(buf2, after);
         fvc.cursory(buf2.size());
      }
   }

   private static void substitute(boolean dotmode,
         int count, FvContext fvc) throws InputException, IOException {
      fvc.deleteChars('0', true, true, count);
      InsertBuffer.insertMode(dotmode, 1, fvc, false, false);
   }

   private void ucSubstitute(boolean dotmode, int count, FvContext fvc) throws
         InputException, IOException {
      MoveGroup.starttext(fvc);
      deletetoend('0', count, fvc);
      InsertBuffer.insertMode(dotmode, 1, fvc, false, false);
      fvc.edvec.checkpoint();
      fvc.fixCursor();
   }

   static void deletetoend(char bufid, int count, FvContext fvc) {
      int cy = fvc.inserty();
      int lastline = fvc.inserty() - 1 + count;
      deletetext(bufid, fvc, false, fvc.insertx(), fvc.inserty(),
         fvc.at(lastline).toString().length(), lastline);
      fvc.cursory(cy - fvc.inserty());
   }

   private static void deletetext(char bufid, FvContext fvc,
         boolean preserve, int xstart, int ystart, int xend, int yend) {
      //trace("deletetext id " + bufid);
      Buffers.deleted(bufid,
         fvc.edvec.deletetext(preserve, xstart, ystart, xend, yend));
   }

   private void deletemode(char bufid, boolean dotmode, int count, int rcount,
       FvContext fvc) throws
       InterruptedException, IOException, InputException {
//trace("count = " + count + " rcount = " + rcount
//    +  " fvc = " + fvc);
      int xold = fvc.insertx();
      int yold = fvc.inserty();

      if (!dotmode)
         dotevent3 = EventQueue.nextKeye(fvc.vi);

      JeyEvent event = dotevent3;

      switch(event.getKeyChar()) {

         case 'd':
            fvc.edvec.finish();
            if (!fvc.edvec.containsNow(fvc.inserty() + count - 1))
               count = fvc.edvec.finish() - 1;
            Buffers.deleted(bufid, fvc.edvec.remove(fvc.inserty(), count));
            fvc.edvec.checkpoint();
            fvc.fixCursor();
            return;

         default:
            MapEvent.domovement(event, count, rcount, dotmode, fvc);
            if (yold > fvc.inserty() || (yold == fvc.inserty()
               && xold > fvc.insertx())) {
               deletetext(bufid, fvc, false, fvc.insertx(),
                  fvc.inserty(), xold, yold);
            } else {
               deletetext(bufid, fvc, false, xold, yold,
                  fvc.insertx(), fvc.inserty());
               fvc.cursorabs(xold, yold);
            }
            return;
      }
   }

   private void yankmode(char bufid, boolean dotmode, int count,
      int rcount, FvContext fvc) throws
      InterruptedException, IOException, InputException {
//trace("count = " + count + " rcount = " + rcount);
      int xold = fvc.insertx();
      int yold = fvc.inserty();

      if (!dotmode)
         dotevent3 = EventQueue.nextKeye(fvc.vi);

      JeyEvent event =  dotevent3;

      if ('y' == event.getKeyChar()) {
         if (!fvc.edvec.containsNow(fvc.inserty() + count - 1))
            count = fvc.edvec.finish() - 1;
         Buffers.deleted(bufid, fvc.getElementsAt(count));
         trace("yanking a buffer");
         return;
      }
      Position save = fvc.getPosition("yankmark");
      MapEvent.domovement(event, count, rcount, dotmode, fvc);

      if (yold > fvc.inserty()
            || (yold == fvc.inserty() && xold > fvc.insertx())) {

         deletetext(bufid, fvc, true,
            fvc.insertx(), fvc.inserty(), xold, yold);
      } else {
         deletetext(bufid, fvc, true,
            xold, yold, fvc.insertx(), fvc.inserty());
      }
      fvc.cursorabs(save);
   }

   private void changemode(char bufid, boolean dotmode, int count,
         int rcount, FvContext fvc) throws
         InterruptedException, InputException, IOException {

      JeyEvent event = dotmode
         ? dotevent3
         : EventQueue.nextKeye(fvc.vi);

      switch (event.getKeyChar())  {
         case 'c':
            dotevent3 = event;
            MoveGroup.starttext(fvc);
            deletetoend(bufid, count, fvc);
            InsertBuffer.insertMode(dotmode, count, fvc, false, false);
            return;
         case 27: //esc
            return;
         default:
            if (!dotmode)
               EventQueue.pushback(event);

            deletemode(bufid, dotmode, count, rcount, fvc);
            InsertBuffer.insertMode(dotmode, 1, fvc, false, false);
      }
   }

   private void subChar(boolean dotmode, int count, FvContext fvc) throws
      InputException {

      if (!dotmode)
         do
            dotchar = EventQueue.nextKey(fvc.vi);
         while (dotchar  == JeyEvent.CHAR_UNDEFINED);
      if (27 == dotchar)
         return;
      String line = fvc.at().toString();
      //trace("count " + count + " line.length() " + line.length() + " insertx " + fvc.insertx());
      StringBuilder istring =
         new StringBuilder(line.substring(0, fvc.insertx()));
      int icount = line.length() - fvc.insertx();
      icount = icount < count
               ? icount
               : count;
      for (int ii = icount; ii > 0; ii--)
         istring.append(dotchar);
      istring.append(line.substring(fvc.insertx() + icount, line.length()));
      fvc.changeElementStr(istring.toString());
   }


}
