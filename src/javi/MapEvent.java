package javi;

import java.awt.AWTEvent;
import java.awt.CheckboxMenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.Point;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class MapEvent {
   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";

   private KeyGroup skeys = new KeyGroup();
   private static KeyGroup mkeys = new KeyGroup();

//private FvContext fvc=0;

   private static final boolean [] tt = {true, true};
   private static final boolean [] ff = {false, false};
   private static final boolean [] ft = {false, true};
   private static final boolean [] tf = {true, false};
   private static final Integer one = Integer.valueOf(1);
   private static final Integer mone = Integer.valueOf(-1);
   private static final Integer zero = Integer.valueOf(0);
   private static final Float f1 = Float.valueOf(1.0f);
   private static final Float mf1 = Float.valueOf(-1.0f);
   private static final Float half = Float.valueOf(.5f);
   private static final Float mhalf = Float.valueOf(-.5f);

   private int aiterate = 0;
   private int riterate = 0;   //iterations for command that use 0
   private int fiterate = 0;  //number of iterations forced to 1

   MapEvent() {
      MoveGroup.init();
   }

   public static void mKeybind(char c, String cmd, Object arg, int modifiers) {
      trace("");
      mkeys.keybind(c, cmd, arg, modifiers);
   }

   final void bindCommands() {
      Matcher sentenceRegex = Pattern.compile("\\.( |$)").matcher("");
      Matcher paragraphRegex = Pattern.compile("^ *$").matcher("");
      Matcher sectionRegex = Pattern.compile("^[^ ].*\\{").matcher("");

      mkeys.keybind((char) 2, "movescreen", mf1, InputEvent.CTRL_MASK);
      mkeys.keybind((char) 6, "movescreen", f1, InputEvent.CTRL_MASK);
      mkeys.keybind((char) 4, "movescreen", half, InputEvent.CTRL_MASK);
      mkeys.keybind((char) 21, "movescreen", mhalf, InputEvent.CTRL_MASK);
      mkeys.keybind((char) 25, "movescreenline", mone, InputEvent.CTRL_MASK);
      mkeys.keybind((char) 5, "movescreenline", one, InputEvent.CTRL_MASK);
      mkeys.keyactionbind(KeyEvent.VK_PAGE_UP, "movescreen", mf1 , 0);
      mkeys.keyactionbind(KeyEvent.VK_PAGE_DOWN, "movescreen", f1 , 0);

      skeys.keybind('z', "zprocess", null);
      skeys.keybind((char) 12, "redraw", null, InputEvent.CTRL_MASK);
      skeys.keybind((char) 7, "togglestatus", null, InputEvent.CTRL_MASK);
      skeys.keybind(':', "commandproc", null);
      skeys.keybind('Z', "Zprocess", null);
      skeys.keyactionbind(KeyEvent.VK_F1, "nextpos", ff, 0);
      skeys.keyactionbind(KeyEvent.VK_F1, "nextpos", tt,
         InputEvent.SHIFT_MASK);
      skeys.keyactionbind(KeyEvent.VK_F2, "gotofilelist", null, 0);
      skeys.keyactionbind(KeyEvent.VK_F3, "gotodirlist", null, 0);
      skeys.keyactionbind(KeyEvent.VK_F4, "gotofontlist", null, 0);
      skeys.keyactionbind(KeyEvent.VK_F5, "gotopositionlist", null, 0);
      skeys.keyactionbind(KeyEvent.VK_F6, "gotopllist", null, 0);
      skeys.keyactionbind(KeyEvent.VK_F7, "jdebug", null, 0);
      skeys.keyactionbind(KeyEvent.VK_F8, "vt", null, 0);
      //skeys.keyactionbind(KeyEvent.VK_F9,"startcon",null,0);
      skeys.keyactionbind(KeyEvent.VK_F10, "comm", null, 0);

      //skeys.keyactionbind(KeyEvent.VK_F11,"exec",null,0);
      skeys.keyactionbind(KeyEvent.VK_F11, "fullscreen", null, 0);


      mkeys.keybind('h', "movechar", Boolean.FALSE);
      mkeys.keybind((char) 8, "movechar", Boolean.FALSE, 0);
      mkeys.keybind('l', "movechar", Boolean.TRUE);
      mkeys.keybind('^', "starttext", null);
      mkeys.keybind('W', "forwardWord", null);
      mkeys.keybind('w', "forwardword", null);
      mkeys.keybind('b', "backwardword", null);
      mkeys.keybind('B', "backwardWord", null);
      mkeys.keybind('E', "endWord", null);
      mkeys.keybind('e', "endword", null);
      mkeys.keybind('%', "balancechar", null);
      mkeys.keyactionbind(KeyEvent.VK_LEFT, "backwardword",
         null, InputEvent.CTRL_MASK);
      mkeys.keyactionbind(KeyEvent.VK_LEFT, "movechar", Boolean.FALSE, 0);
      mkeys.keyactionbind(KeyEvent.VK_RIGHT, "movechar", Boolean.TRUE, 0);
      mkeys.keyactionbind(KeyEvent.VK_RIGHT, "forwardword", null,
         InputEvent.CTRL_MASK);
      mkeys.keybind('k', "moveline", Boolean.FALSE);
      mkeys.keybind('j', "moveline", Boolean.TRUE);
      mkeys.keyactionbind(KeyEvent.VK_UP, "moveline", Boolean.FALSE, 0);
      mkeys.keyactionbind(KeyEvent.VK_UP, "shiftmoveline", Boolean.FALSE ,
          InputEvent.SHIFT_MASK);
      mkeys.keyactionbind(KeyEvent.VK_UP, "movescreenline", mone ,
         InputEvent.CTRL_MASK);
      mkeys.keyactionbind(KeyEvent.VK_DOWN, "moveline", Boolean.TRUE, 0);
      mkeys.keyactionbind(KeyEvent.VK_DOWN, "shiftmoveline", Boolean.TRUE ,
          InputEvent.SHIFT_MASK);
      mkeys.keyactionbind(KeyEvent.VK_DOWN, "movescreenline", one ,
         InputEvent.CTRL_MASK);
      mkeys.keyactionbind(KeyEvent.VK_END, "linepos",
         Integer.valueOf(Integer.MAX_VALUE), 0);
      mkeys.keyactionbind(KeyEvent.VK_END, "gotoline",
         Integer.valueOf(Integer.MAX_VALUE), InputEvent.SHIFT_MASK);
      mkeys.keyactionbind(KeyEvent.VK_HOME, "linepos", zero, 0);
      mkeys.keyactionbind(KeyEvent.VK_HOME, "gotoline", one,
         InputEvent.SHIFT_MASK);
      mkeys.keyactionbind(KeyEvent.VK_HOME, "gotoline", one,
         InputEvent.CTRL_MASK);
      mkeys.keyactionbind(KeyEvent.VK_END, "gotoline", null,
         InputEvent.CTRL_MASK);
      mkeys.keybind('+', "movelinestart", one);
      mkeys.keybind((char) 13, "movelinestart", one);
      mkeys.keybind((char) 10, "moveline", Boolean.TRUE, InputEvent.CTRL_MASK);
      mkeys.keybind((char) 10, "movelinestart", one, 0);
      mkeys.keybind('-', "movelinestart", mone);
      mkeys.keybind('H', "screenmove", Float.valueOf(0));
      mkeys.keybind('M', "screenmove", Float.valueOf(.5f));
      mkeys.keybind('L', "screenmove", Float.valueOf(.999999f));
      mkeys.keybind('f', "findchar", tt);
      mkeys.keybind('F', "findchar", ft);
      mkeys.keybind('t', "findchar", tf);
      mkeys.keybind('T', "findchar", ff);
      mkeys.keybind(';', "repeatfind", tt);
      mkeys.keybind(',', "repeatfind", ff);
      mkeys.keybind('n', "regsearch", Boolean.FALSE);
      skeys.keyactionbind(KeyEvent.VK_F3, "regsearch", Boolean.FALSE,
         InputEvent.CTRL_MASK);
      mkeys.keybind('N', "regsearch", Boolean.TRUE);
      mkeys.keybind('/', "searchcommand", Boolean.FALSE);
      mkeys.keybind('?', "searchcommand", Boolean.TRUE);
      mkeys.keybind('0', "linepos", zero);
      mkeys.keybind('$', "linepos", Integer.valueOf(Integer.MAX_VALUE));
      mkeys.keybind('|', "linepos", null); //diff '0' ???
      mkeys.keybind('G', "gotoline", null);
      mkeys.keybind('\'', "findmark", null);
      mkeys.keybind('m', "mark", null);
      mkeys.keybind(')', "forwardregex", sentenceRegex);
      mkeys.keybind('(', "backwardregex", sentenceRegex);
      mkeys.keybind('}', "forwardregex", paragraphRegex);
      mkeys.keybind('{', "backwardregex", paragraphRegex); //}
      mkeys.keybind(']', "forwardregex", sectionRegex);
      mkeys.keybind('[', "backwardregex", sectionRegex);
      skeys.keybind((char) 29, "gototag", null, InputEvent.CTRL_MASK); //^]
      skeys.keybind((char) 20, "poptag", null, InputEvent.CTRL_MASK); //^t
      skeys.keybind('^', "nextfile", null,
         InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK);

      skeys.keybind(' ', "moveover", Boolean.TRUE, InputEvent.SHIFT_MASK);
      skeys.keybind(' ', "moveover", Boolean.FALSE, 0);
      skeys.keybind('\036', "nextfile", null,
         InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK);

      // editing keys
      skeys.keybind((char) 2, "movescreen", mone, InputEvent.CTRL_MASK);
      skeys.keybind((char) 18, "redo", null, InputEvent.CTRL_MASK);
      skeys.keybind((char) 26, "redo", null, InputEvent.CTRL_MASK
         | InputEvent.SHIFT_MASK); // ??? ALT should redo
      skeys.keybind('Y', "redo", null, InputEvent.CTRL_MASK);
      skeys.keybind((char) 8, "redo", null,
         InputEvent.SHIFT_MASK | InputEvent.ALT_MASK);
      skeys.keybind('U', "undoline", null); // ??? ALT should redo
      skeys.keybind('i', "insert", ff);
      skeys.keybind('I', "Insert", ff);
      skeys.keybind('a', "append", ft);
      skeys.keybind('A', "Append", ft);
      skeys.keybind('o', "openline", ft);
      skeys.keybind('O', "Openline", ft);
      skeys.keybind('s', "substitute", ft);
      skeys.keybind('p', "putafter", null);
      skeys.keybind('P', "putbefore", null);
      skeys.keybind('y', "yankmode", null);
      skeys.keybind('Y', "yank", null);
      skeys.keybind('u', "undo", null);
      skeys.keybind((char) 8, "undo", null, InputEvent.ALT_MASK);
      skeys.keybind((char) 26, "undo", null, InputEvent.CTRL_MASK);
      skeys.keybind('S', "Substitute", null);
      skeys.keybind('X', "deletechars", ff);
      skeys.keybind((char) 127, "deletechars", ff);
      skeys.keybind('x', "deletechars", tt);
      skeys.keybind('D', "deletetoend", tt);
      skeys.keybind('C', "deletetoendi", null);
      skeys.keybind('c', "changemode", null);
      skeys.keybind('d', "deletemode", null);
      skeys.keybind('v', "markmode", zero);
      skeys.keybind('V', "markmode", one);
      skeys.keybind('J', "joinlines", null);
      skeys.keybind('r', "subchar", null);
      skeys.keybind('~', "changecase", null);
      skeys.keybind('R', "insert", tf);
      skeys.keybind('.', "doover", tt);
      skeys.keybind('"', "qmode", null); //??? test if still works
      skeys.keybind('<', "shiftmode", one); //??? test if still works
      skeys.keybind('>', "shiftmode", mone); //??? test if still works
      skeys.keyactionbind(KeyEvent.VK_DELETE, "deletechars", one, 0);
      skeys.keyactionbind(KeyEvent.VK_DELETE, "deletetoend", null,
         InputEvent.SHIFT_MASK);
      skeys.keyactionbind(KeyEvent.VK_INSERT, "insert", ft, 0);
   }

   boolean domovement(KeyEvent ein, int fiteratei, int riteratei,
         boolean dotmode, FvContext fvc) throws
         InterruptedException, IOException, InputException {
      //trace("domovement fvc = " + fvc);
      KeyBinding binding = mkeys.get(ein);
      if (binding != null) {
         //trace("binding rg = " + binding.rg+ " event " + ein);
         binding.rg.doroutine(binding.index, binding.arg, fiteratei, riteratei,
            fvc, dotmode);
         return true;
      } else
         return false;
   }

   private boolean screenmovement(AWTEvent e1, FvContext fvc) throws
         InterruptedException, InputException, IOException {
      KeyBinding binding = skeys.get((KeyEvent) e1);
      if (binding == null)
         return false;
      //trace("binding  = " + binding);
      binding.rg.doroutine(binding.index, binding.arg, fiterate, riterate,
          fvc, false);
      return true;

   }

   public final void run() {
//     try {Thread.sleep(20000);} catch (InterruptedException e) {/*Ignore*/}
//trace("" + e  + " exitflag " + exitflag);
      try {
         while (true)
            try {
               while (true) {
                  FvContext fvc = FvContext.getCurrFvc();
                  Object e = EventQueue.nextEvent(fvc.vi);
                  if (!hevent(e, fvc))
                     trace("did not handle event" + e);
               }
            } catch (InterruptedException ex) { /* ignore */

            } catch (ReadOnlyException e) {
               try {
                  UI.makeWriteable(e.getEv(), e.getMessage());
               } catch (IOException e2) {
                  UI.reportError("making file writeable throw exception" + e2);
               }
            } catch (ExitException ex) {
               trace("MapEvent.run caught ExitException");
               throw ex;
            } catch (InputException e) {
               trace("caught InputException " + e);
               UI.reportMessage(e.toString());
            } catch (StackOverflowError e) {
               trace("caught StackOverflowError " + e);
               throw new ExitException(e);
            } catch (Throwable ex) {
               UI.popError("viewevent.run caught", ex);
               StackTraceElement[] tr = ex.getStackTrace();
               for (StackTraceElement elem : tr)  {
                  if  (elem.getMethodName().indexOf("nextEvent") != -1)
                     if  (elem.getClassName().indexOf("EventQueue") != -1)  {
                        trace("caught while processing next event");
                        throw new ExitException(ex);
                     }
               }
            }
      } catch (ExitException ex) {
         //trace("caught exit Exception ex");
      }
      //trace("returning from run");
   }

   abstract static class JaviEvent {
      abstract void execute() throws IOException;
   }

   private static final int buttonmask  =   InputEvent.BUTTON3_MASK
      | InputEvent.BUTTON3_MASK | InputEvent.BUTTON3_MASK;

   final void mousepress(MouseEvent event, FvContext fvc) throws
         InputException {
      //trace("modifiers = " +Integer.toHexString( event.getModifiers()));

      View vi = (View) event.getComponent();
      Position p = vi.mousepos(event);
      //trace("Position " + p + " event vi " + vi);
      FvContext newfvc = FvContext.getcontext(vi, vi.getCurrFile());

      //trace("fvc " + fvc  + " newfvc " + newfvc);
      switch (event.getButton()) {
         case MouseEvent.BUTTON1:
            if (fvc.vi != vi)
               UI.setView(newfvc);
            newfvc.setMark(p);
            break;

         case MouseEvent.BUTTON2:
            if (newfvc.edvec.containsNow(p.y)) {
               Object line = newfvc.edvec.at(p.y);
               if (line instanceof Position) {

                  View nextView = newfvc.findNextView();
                  FileList.gotoposition((Position) line, true, nextView);
               }
            }
            break;

         case MouseEvent.BUTTON3:
            Point pt = vi.getLocation();
            UI.showmenu(event.getX() + pt.x, event.getY() + pt.y);
            break;

         default:
            trace("no button ???? event modifiers = " + Integer.toHexString(
               event.getModifiers()));
      }
   }

   final void mouserelease(MouseEvent event, FvContext fvc) throws
          InputException {
      //trace(" clickcount " + event.getClickCount() + " has focus" + fvc.vi.hasFocus());

      View vi = (View) event.getComponent();
      Position p = vi.mousepos(event);
      //trace("Position " + p + " event vi " + vi);
      if (fvc != FvContext.getcontext(vi, vi.getCurrFile()))
         return;

      if (event.getButton()  == MouseEvent.BUTTON1) {
         //trace("setting markmode ");
         fvc.cursorabs(p);
         Position markpos = vi.getMark();
         if (markpos != null)
            if  (p.x == markpos.x && p.y == markpos.y)
               vi.clearMark();
            else
               try {
                  Rgroup.doroutine("markmode", Integer.valueOf(0), 1, 1,
                     fvc, false);
               } catch (ExitException e) {
                  throw e;
               } catch (Exception e) {
                  e.printStackTrace();
                  throw new RuntimeException(
                     "mouserelease should not have gotten exception " + e , e);
               }
      }
   }

   final boolean hevent(Object ev, FvContext fvc)  throws InputException,
         InterruptedException , IOException {
      //trace("hevent" + awtEv);

      if (ev instanceof AWTEvent) {
         AWTEvent awtEv = (AWTEvent) ev;
         switch (awtEv.getID()) {
            case MouseEvent.MOUSE_CLICKED:
               return true;
            case MouseEvent.MOUSE_PRESSED:
               mousepress((MouseEvent) awtEv, fvc);
               return true;
            case MouseEvent.MOUSE_RELEASED:
               mouserelease((MouseEvent) awtEv, fvc);
               return true;
            case MouseEvent.MOUSE_WHEEL:
               MouseWheelEvent mev = (MouseWheelEvent) ev;
               if (mev.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL)
                  fvc.cursory(fvc.vi.getRows(1.f) * mev.getWheelRotation());
               else if (mev.isControlDown())
                  fvc.cursory(fvc.vi.getRows(1.f) * mev.getWheelRotation());
               else
                  fvc.cursory(mev.getScrollAmount() * mev.getWheelRotation());
               return true;
            case KeyEvent.KEY_PRESSED :
               KeyEvent event = (KeyEvent) awtEv;
               char ch = event.getKeyChar();
               if (((ch != '0') || (aiterate != 0))
                     && (ch >= '0' && ch <= '9')) {
                  aiterate = aiterate * 10 + (ch & 0x0f);
                  return true;
               } else if (event.getKeyChar() == 27) {
                  aiterate = 0;
                  return true;
               }

               riterate = aiterate;   // iterations for command that use 0
               fiterate = aiterate;  // number of iterations forced to 1
               if (fiterate == 0)
                  fiterate = 1;
               if (domovement(event, fiterate, riterate, false, fvc)
                     || screenmovement(awtEv, fvc)) {
                  aiterate = 0;
                  return true;
               }

               break;
            case ActionEvent.ACTION_PERFORMED:
               ActionEvent aevent = (ActionEvent) awtEv;
               //trace("hevent got  " + aevent);
               Command.command(aevent.getActionCommand(), fvc, null);
               return true;
            case ItemEvent.ITEM_STATE_CHANGED:
               ItemEvent ievent = (ItemEvent) awtEv;
               String istr =
                  ((CheckboxMenuItem) (ievent.getSource())).getActionCommand()
                     + (ievent.getStateChange() == ItemEvent.SELECTED
                        ? " on"
                        : " off");
               Command.command(istr, fvc, null);
               return true;
         }
      } else if (ev instanceof JaviEvent)  {
         ((JaviEvent) ev).execute();
         return true;
      }

      return false;
   }


   private static void trace(String str) {
      Tools.trace(str, 1);
   }
}
