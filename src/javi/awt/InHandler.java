package javi.awt;

import java.awt.event.InputMethodListener;
import java.awt.event.InputMethodEvent;
import java.awt.font.TextHitInfo;
import java.awt.Rectangle;
import java.text.AttributedCharacterIterator;

import javi.EventQueue;
import static history.Tools.trace;

final class InHandler extends javi.InsertBuffer implements InputMethodListener {

   private int commited;

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

      if (isActive())
         EventQueue.insert(new TextChanged(event));
   }

   final class TextChanged extends EventQueue.IEvent {
      private InputMethodEvent ev;

      TextChanged(InputMethodEvent evi) {
         ev = evi;
      }
      public void execute() {
         trace(ev.toString());
         //trace("commited = " + committed + " buffer = " + buffer);

         insertChars(ev.getText(), commited);
         commited += ev.getCommittedCharacterCount();
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
   public void insertReset() {
      commited = 0;
   }
}
