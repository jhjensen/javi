package javi;

import java.awt.im.InputMethodRequests;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.InputMethodEvent;
import java.awt.font.TextHitInfo;
import java.awt.Rectangle;
import java.text.AttributedCharacterIterator;
import java.io.IOException;
import java.text.CharacterIterator;


/* Copyright 1996 James Jensen all rights reserved */

public final class InsertBuffer extends Rgroup
   implements InputMethodRequests, InputMethodListener {
   static final String copyright = "Copyright 1996 James Jensen";

   private StringBuilder buffer = new StringBuilder();
   private boolean overwrite;

   private class MyInserter  extends View.Inserter {
      String getString() {
         return buffer.toString();
      }

      boolean getOverwrite() {
         return overwrite;
      }

      // this is temporary to get things compiling.
      public InsertBuffer getSuper() {
         return InsertBuffer.this;
      }

   }

   private MyInserter insert = new MyInserter();

   private final MapEvent evhandler;
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
   private int committed;

   static final boolean [] ff = {false, false};

   InsertBuffer(String str) { // fordebug
      buffer.append(str);
      evhandler = null;
   }

   InsertBuffer(MapEvent eventhandleri) {
      evhandler = eventhandleri;
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
         "imode.find",
      };

      register(rnames);
      ikeys.keyactionbind(JeyEvent.VK_INSERT, "imode.toggleinsert", null, 0);
      ikeys.keybind('\t', "imode.tabinsert", null);
      ikeys.keybind((char) 8, "imode.backspace", null);
      ikeys.keybind((char) 127, "imode.delete", null);
      ikeys.keybind((char) 27, "imode.complete", null);
      ikeys.keybind((char) 91, "imode.complete", null, InputEvent.CTRL_MASK);
      ikeys.keybind((char) 22, "imode.setverbatim", null);
      ikeys.keybind((char) 22, "imode.setverbatim", null, InputEvent.CTRL_MASK);
      ikeys.keybind('\r', "imode.insertnewline", null);
      ikeys.keybind('\r', "imode.insertnewline", null, InputEvent.CTRL_MASK);
      ikeys.keybind('\n', "imode.insertnewline", null);
      ikeys.keybind((char) 16, "imode.putbuf", null);
      ikeys.keybind((char) 16, "imode.putbuf", null, InputEvent.CTRL_MASK);
      ikeys.keybind((char) 6, "imode.find", ff, InputEvent.CTRL_MASK);
      ikeys.keybind('\n', "imode.insertnewline", null, InputEvent.CTRL_MASK);
      ikeys.keybind((char) 12, "redraw", null, InputEvent.CTRL_MASK);

      commandikeys.keyactionbind(
         JeyEvent.VK_INSERT, "imode.toggleinsert", null, 0);
      commandikeys.keybind('\t', "imode.tabinsert", null);
      commandikeys.keybind((char) 8, "imode.backspace", null);
      commandikeys.keybind((char) 127, "imode.delete", null);
      commandikeys.keybind((char) 27, "imode.cancel", null);
      commandikeys.keybind((char) 22, "imode.setverbatim", null);
      commandikeys.keybind((char) 22,
         "imode.setverbatim", null, InputEvent.CTRL_MASK);
      commandikeys.keybind('\r', "imode.complete", null);
      commandikeys.keybind('\r', "imode.complete", null, InputEvent.CTRL_MASK);
      commandikeys.keybind('\n', "imode.complete", null);
      commandikeys.keybind('\n', "imode.complete", null, InputEvent.CTRL_MASK);
      commandikeys.keyactionbind(JeyEvent.VK_DOWN, "imode.nextline", null, 0);
      commandikeys.keyactionbind(JeyEvent.VK_UP, "imode.prevline", null, 0);
      commandikeys.keybind((char) 16, "imode.putbuf", null);
      commandikeys.keybind((char) 16,
         "imode.putbuf", null, InputEvent.CTRL_MASK);
      commandikeys.keybind((char) 6, "imode.find", ff, InputEvent.CTRL_MASK);
      commandikeys.keybind((char) 12, "redraw", null, InputEvent.CTRL_MASK);
   }

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws InputException {
      //trace("rnum = " + rnum);

      switch (rnum) {
         case 1:
            itext(1, fvc);
            overwrite = !overwrite;
            if (overwrite) {
               trace("entering terminal mode");
               fvc.addKeyEventDispatcher();
               trace("exit insertmode?");
               return this;
            }
            break;
         case 2:
            //tabConverter tb = (tabConverter)fvc.edvec.getConverter();
            //int tabStop = (tb == null) ? 0 : tb.getTab();
            int linepos = fvc.insertx() + buffer.length();
            int spcount = findspacebound(fvc, linepos);

            while (--spcount >= 0)
               buffer.append(' ');
            fvc.vi.lineChanged(fvc.inserty());
            break;
         case 3:
            if (0 == buffer.length()) {
               fvc.cursorx(-1);
               EditGroup.deleteChars('0', fvc, false, true, 1);
            } else {
               buffer.setLength(buffer.length() - 1);
               fvc.vi.lineChanged(fvc.inserty());
            }
            break;
         case  4: // delete
            EditGroup.deleteChars('0', fvc, false, true, 1);
            break;
         case 5:
            itext(count, fvc);
            if (count > 1) {
               if (overwrite)
                  EditGroup.deleteChars(
                     '0', fvc, false, true, (count - 1) * dotbuffer.length());
               for (int i = 1; i < count; i++)
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
            EditGroup.appendCurrBuf(buffer, singleline);
            fvc.changeElement(fvc.at(currline)); // force redraw
            break;
         case 12:
            itext(count, fvc);
            MoveGroup.searchcommand(((boolean []) arg)[0] ,
               count, fvc, dotmode);
            break;
      }
      return null;
   }

   private void itext(int count, FvContext fvc) {
      //trace("fvc = " + fvc  + fvc.vi);
      //trace("buffer = " + buffer );
      if (buffer.length() != 0) {
         String temps = buffer.toString();

         if (overwrite)
            EditGroup.deleteChars('0', fvc, false,
               true, (count) * temps.length());

         //for (char chr :temps) trace("inserting " + (int)chr);

         if (temps.length() != 0)
            fvc.cursorabs(fvc.inserttext(temps));

         if (!singleline)
            dotbuffer += temps;
         buffer.setLength(0);
      }
      committed = 0;
   }

   void insertmode(boolean dotmode, int count, FvContext fvc,
         boolean overwritei, boolean singlelinei) throws
         IOException, InputException {

      //trace("insertmode");
      myfvc  = fvc;

      int key;
      if (dotmode) {
         if (overwrite)
            EditGroup.deleteChars('0', fvc,
               false, true, count * dotbuffer.length());
         for (int i = 0; i < count; i++)
            fvc.cursorabs(fvc.inserttext(dotbuffer));
      } else {
         try {
            View viewer = fvc.vi;
            viewer.setInsert(insert);
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
               KeyBinding binding;
               if (!verbatim && (binding =
                     activekeys.get(ke.eventToString())) != null) {
                  if (null != binding.rg.doroutine(binding.index,
                        binding.arg, count, 0, fvc, false))
                     break;
               } else if (ke.isActionKey()) {
                  itext(count, fvc);
                  evhandler.hevent(ke, fvc);
               } else {
                  key = ke.getKeyChar();

                  if (verbatim && (key >= '0' && key <= '9')) {
                     verbatimCount++;
                     verbatimAcc = verbatimAcc * 10 + (key - '0');
                     if (verbatimCount == 3) {
                        buffer.append((char) verbatimAcc);
                        viewer.lineChanged(fvc.inserty());
                        verbatim = false;
                        verbatimAcc = 0;
                        verbatimCount = 0;
                     }

                  } else {
                     if (verbatimCount != 0)
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

   static int findspacebound(FvContext fvc, int linepos) {
      int j;
      int lineno;

      for (lineno = fvc.inserty() - 1; lineno > 0; lineno--) {
         String line =  fvc.at(lineno).toString();
         // skip non spaces
         for (j = linepos; j < line.length(); j++)
            if (line.charAt(j) == ' ')
               break;
         // skip spaces
         for (; j < line.length(); j++)
            if (line.charAt(j) != ' ')
               break;
         if (j < line.length()) { // found good line
            return j - linepos;
         }
      }
      return 0;
   }

   void cleanup(FvContext fvc) {
      //trace("insertcontext.cleanup");
      fvc.vi.clearInsert();
      myfvc  = null;
      committed = 0;
   }

   public AttributedCharacterIterator cancelLatestCommittedText(
      AttributedCharacterIterator.Attribute[] attributes)  {
      trace("unexpected");
      return null;
   }
   public AttributedCharacterIterator getCommittedText(int beginIndex,
         int endIndex, AttributedCharacterIterator.Attribute[] attributes)  {
      trace("unexpected getCommittedText");
      return null;
   }
   public int getCommittedTextLength()  {
      trace("unexpected getCommittedTextLength");
      return 0;
   }
   public int getInsertPositionOffset()  {
      trace("");
      return 200;
   }
   public  TextHitInfo getLocationOffset(int x, int y) {
      trace("getLocationOffset (" + x + "," + y + ")");
      return TextHitInfo.afterOffset(0);
   }
   public AttributedCharacterIterator getSelectedText(
         AttributedCharacterIterator.Attribute[] attributes) {
      trace("getSelectedText");
      return null;
   }
   public Rectangle getTextLocation(TextHitInfo offset) {
      trace("getTextLocation" + offset);
      return new Rectangle(50, 50);
//   return null;
   }
   public void caretPositionChanged(InputMethodEvent event) {
      trace(event.toString());
   }

   public void inputMethodTextChanged(InputMethodEvent event) {
      trace("inputMethodTextChanged " + event);
      if (myfvc == null)
         return;
      EventQueue.insert(new TextChanged(event, this));
   }

   class TextChanged extends EventQueue.IEvent {
      private InputMethodEvent ev;

      TextChanged(InputMethodEvent evi, InsertBuffer conti) {
         ev = evi;
      }
      public void execute() {
         trace(ev.toString());
         trace("commited = " + committed + " buffer = " + buffer);

         buffer.setLength(committed);
         CharacterIterator charit = ev.getText();
         if (charit != null) {
            //trace("iterate " + (int)charit.next());
            for (char c = charit.first();
                    c != CharacterIterator.DONE;
                    c = charit.next()) {
               buffer.append(c);
            }
            committed += ev.getCommittedCharacterCount();
         }
         /*
               myfvc.vi.cursoron(); //??? questionable threading
               myfvc.vi.changed(myfvc.inserty());
               try {
                     vic.wakeup();  // repaints the window
               } catch (IOException e) {
                  trace("caught unexpected exception " +e );
                  e.printStackTrace();
               }
         */
         //trace("commited = " + committed + " changed input buffer to " + buffer);
      }

   }
}
