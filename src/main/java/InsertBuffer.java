package javi;

import java.io.IOException;
import java.text.CharacterIterator;

import static history.Tools.trace;
import static javi.JeyEvent.CTRL_MASK;

public abstract class InsertBuffer extends View.Inserter {

   private static InsertBuffer instance;

   public abstract void  insertReset();

   private StringBuilder buffer = new StringBuilder(40);
   private boolean overwrite;
   private String dotbuffer;
   private KeyGroup ikeys = new KeyGroup();
   private KeyGroup commandikeys = new KeyGroup();
   private boolean verbatim = false;
   private int verbatimAcc = 0;
   private int verbatimCount = 0;
   private int currline;
   private Object original;
   private boolean singleline;
   private FvContext myfvc;

   static final boolean[] ff = {false, false};

   protected InsertBuffer() {
      new Cmd();
      instance = this;
   }

   final String getString() {
      return buffer.toString();
   }

   final boolean isOverwrite() {
      return overwrite;
   }

   static final void insertMode(boolean dotmode, int count, FvContext fvc,
         boolean overwritei, boolean singlelinei) throws
         IOException, InputException {
      instance.insertmode(dotmode, count, fvc, overwritei, singlelinei);
   }

   static final String getcomline(String prompt) {
      FvContext<String> commFvc =  FvContext.startComLine();
      EditContainer ev = commFvc.edvec;
      try {
         if (!(commFvc.at(ev.finish() - 1).toString().equals(prompt))) {
            ev.insertOne(prompt, ev.finish());
         }
         commFvc.cursorabs(prompt.length(), ev.finish() - 1);
         insertMode(false, 1, commFvc, false, true);

      } catch (InputException e) {
         UI.reportMessage(e.toString());
      } catch (Throwable e) {
         UI.popError("exception in command processing ", e);
      }

      String line = FvContext.endComLine();
      if (line.startsWith(prompt, 0))
         return line;
      else {
         UI.reportMessage("deleted prompt");
         return prompt;
      }
   }

   private final class Cmd extends Rgroup {

      Cmd() {
         final String[] rnames = {
            "",
            "imode.toggleinsert",
            "imode.tabinsert",
            "imode.backspace",
            "imode.delete",
            "imode.complete",       //5
            "imode.insertnewline",
            "imode.cancel",
            "imode.setverbatim",
            "imode.prevline",
            "imode.nextline",       //10
            "imode.putbuf",
         };
         register(rnames);
         ikeys.keyactionbind(JeyEvent.VK_INSERT, "imode.toggleinsert", null, 0);
         ikeys.keybind('\t', "imode.tabinsert", null);
         ikeys.keybind('\b', "imode.backspace", null);
         ikeys.keybind('\u007f', "imode.delete", null);
         ikeys.keybind('\u001b', "imode.complete", null);
         ikeys.keybind(']', "imode.complete", null, CTRL_MASK);
         ikeys.keybind('\u0016', "imode.setverbatim", null);
         ikeys.keybind((char) 22, "imode.setverbatim", null, CTRL_MASK);
         ikeys.keybind('\r', "imode.insertnewline", null);
         ikeys.keybind('\r', "imode.insertnewline", null, CTRL_MASK);
         ikeys.keybind('\n', "imode.insertnewline", null);
         ikeys.keybind((char) 16, "imode.putbuf", null);
         ikeys.keybind((char) 16, "imode.putbuf", null, CTRL_MASK);
//         ikeys.keybind((char) 6, "imode.find", ff, CTRL_MASK);
         ikeys.keybind('\n', "imode.insertnewline", null, CTRL_MASK);
         ikeys.keybind((char) 12, "redraw", null, CTRL_MASK);

         commandikeys.keyactionbind(
            JeyEvent.VK_INSERT, "imode.toggleinsert", null, 0);
         commandikeys.keybind('\t', "imode.tabinsert", null);
         commandikeys.keybind((char) 8, "imode.backspace", null);
         commandikeys.keybind((char) 127, "imode.delete", null);
         commandikeys.keybind((char) 27, "imode.cancel", null);
         commandikeys.keybind((char) 22, "imode.setverbatim", null);
         commandikeys.keybind((char) 22,
            "imode.setverbatim", null, CTRL_MASK);
         commandikeys.keybind('\r', "imode.complete", null);
         commandikeys.keybind('\r', "imode.complete", null, CTRL_MASK);
         commandikeys.keybind('\n', "imode.complete", null);
         commandikeys.keybind('\n', "imode.complete", null, CTRL_MASK);
         commandikeys.keyactionbind(JeyEvent.VK_DOWN,
            "imode.nextline", null, 0);
         commandikeys.keyactionbind(JeyEvent.VK_UP, "imode.prevline", null, 0);
         commandikeys.keybind('\u0010', "imode.putbuf", null);
         commandikeys.keybind('\u0010', "imode.putbuf", null, CTRL_MASK);
//         commandikeys.keybind((char) 6, "imode.find", ff, CTRL_MASK);
         commandikeys.keybind('\f', "redraw", null, CTRL_MASK);
      }

      public Object doroutine(int rnum, Object arg, int count, int rcount,
            FvContext fvc, boolean dotmode) throws InputException {
         //trace("rnum = " + rnum);

         switch (rnum) {
            case 1:
               itext(1, fvc);
               if (fvc.edvec instanceof Vt100) {
                  Vt100 v100  = (Vt100) fvc.edvec;
                  v100.handleKeys(fvc);
               }
               overwrite = !overwrite;
               if (overwrite) {
                  return this;
               }
               break;
            case 2:
               //tabConverter tb = (tabConverter)fvc.edvec.getConverter();
               //int tabStop = (tb == null) ? 0 : tb.getTab();
               int linepos = fvc.insertx() + buffer.length();

               for (int cnt = findspacebound(fvc, linepos); --cnt >= 0;)
                  buffer.append(' ');
               fvc.vi.lineChanged(fvc.inserty());
               break;
            case 3:
               if (0 == buffer.length()) {
                  fvc.cursorx(-1);
                  fvc.deleteChars('0', false, true, 1);
               } else {
                  buffer.setLength(buffer.length() - 1);
                  fvc.vi.lineChanged(fvc.inserty());
               }
               break;
            case  4: // delete
               fvc.deleteChars('0', false, true, 1);
               break;
            case 5:
               itext(count, fvc);
               if (count > 1) {
                  if (overwrite)
                     fvc.deleteChars('0', false, true,
                        (count - 1) * dotbuffer.length());
                  for (int ii = 1; ii < count; ii++)
                     fvc.cursorabs(fvc.inserttext(dotbuffer));
               }
               return this;
            case 6:
               buffer.append('\n');
               itext(count, fvc);
               break;
            case 7:
               buffer.setLength(0);
               fvc.changeElement(original);
               return this;
            case 8:
               verbatim = true;
               break;
            case 9:
               int temp = currline;
               char prompt = fvc.at().toString().charAt(0);
               while (--temp > 1)
                  if (prompt == fvc.at(temp).toString().charAt(0))
                     break;
               if (temp > 1) {
                  currline = temp;
                  buffer.setLength(0);
                  fvc.changeElement(fvc.at(currline));
               }
               break;
            case 10:
               temp = currline;
               prompt = fvc.at().toString().charAt(0);
               if (temp == fvc.inserty())
                  return null;
               while (true) {
                  ++temp;
                  if (temp == fvc.inserty()) {
                     fvc.changeElement(Character.toString(prompt));
                     currline = temp;
                     return null;
                  }
                  if (prompt == fvc.at(temp).toString().charAt(0))
                     break;
               }
               currline = temp;
               buffer.setLength(0);
               fvc.changeElement(fvc.at(currline));
               break;
            case 11:
               Buffers.appendCurrBuf(buffer, singleline);
               fvc.changeElement(fvc.at(currline)); // force redraw
               break;
// causes circular dependency, don't think we really need it
//            case 12:
//               itext(count, fvc);
//               MoveGroup.searchcommand(((boolean []) arg)[0] ,
//                  count, fvc, dotmode);
//               break;
            default:
               throw new RuntimeException("unexpected command");
         }
         return null;
      }
   }

   private void itext(int count, FvContext fvc) {
      //trace("fvc = " + fvc  + fvc.vi);
      //trace("buffer = " + buffer );
      if (0 != buffer.length()) {
         String temps = buffer.toString();

         if (overwrite)
            fvc.deleteChars('0', false,
               true, (count) * temps.length());

         //for (char chr :temps) trace("inserting " + (int)chr);

         if (0 != temps.length())
            fvc.cursorabs(fvc.inserttext(temps));

         if (!singleline)
            dotbuffer += temps;
         buffer.setLength(0);
      }
      insertReset();
   }

   final void insertmode(boolean dotmode, int count, FvContext fvc,
         boolean overwritei, boolean singlelinei) throws
         IOException, InputException {

      //trace("insertmode");
      myfvc  = fvc;

      if (dotmode) {
         if (overwrite)
            fvc.deleteChars('0', false, true, count * dotbuffer.length());
         for (int ii = 0; ii < count; ii++)
            fvc.cursorabs(fvc.inserttext(dotbuffer));
      } else {
         try {
            View viewer = fvc.vi;
            viewer.setInsert(this);
            verbatim = false;
            overwrite = overwritei;
            currline = fvc.inserty();
            singleline = singlelinei;
            if (!singleline)
               dotbuffer = "";

            original =  fvc.at();
            KeyGroup activekeys =  singleline ? commandikeys : ikeys;
            while  (true) {
               JeyEvent ke = EventQueue.nextEvent(viewer);
               //trace("event = " + e);
               Rgroup.KeyBinding binding;
               if (!verbatim && null != (binding = activekeys.get(ke))) {
                  if (null != binding.dobind(count, 0, fvc, false))
                     break;
               } else {
                  char key = ke.getKeyChar();
                  if (key == JeyEvent.CHAR_UNDEFINED) {
                     itext(count, fvc);
                     MapEvent.hevent(ke, fvc);
                  } else if (verbatim && (key >= '0' && key <= '9')) {
                     verbatimCount++;
                     verbatimAcc = verbatimAcc * 10 + (key - '0');
                     if (3 == verbatimCount) {
                        buffer.append((char) verbatimAcc);
                        viewer.lineChanged(fvc.inserty());
                        verbatim = false;
                        verbatimAcc = 0;
                        verbatimCount = 0;
                     }

                  } else {
                     if (0 != verbatimCount)
                        buffer.append((char) verbatimAcc);
                     verbatim = false;
                     verbatimAcc = 0;
                     verbatimCount = 0;
                     buffer.append((char) key);
                     viewer.lineChanged(fvc.inserty());
                  }
               }
            }
         } catch (InterruptedException e) {
            trace("Interrupted Exception!!!");
         } finally  {
            cleanup(fvc);
            buffer.setLength(0);
         }
      }
   }

   static final int findspacebound(FvContext fvc, int linepos) {
      for (int lineno = fvc.inserty() - 1; lineno > 0; lineno--) {
         String line =  fvc.at(lineno).toString();
         // skip non spaces
         int tspace;
         for (tspace = linepos; tspace < line.length(); tspace++)
            if (' ' == line.charAt(tspace))
               break;
         // skip spaces
         for (; tspace < line.length(); tspace++)
            if (' ' != line.charAt(tspace))
               break;
         if (tspace < line.length()) { // found good line
            return tspace - linepos;
         }
      }
      return 0;
   }

   public final boolean isActive() {
      return null != myfvc;
   }

   final void cleanup(FvContext fvc) {
      //trace("insertcontext.cleanup");
      fvc.vi.clearInsert();
      myfvc  = null;
      insertReset();
   }

   public final void insertChars(CharacterIterator charit, int trunc) {
      if (null != charit) {
         //trace("iterate " + (int)charit.next());
         for (char c = charit.first();
                 c != CharacterIterator.DONE;
                 c = charit.next()) {
            buffer.append(c);
         }
      }
   }
}
