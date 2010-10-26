package javi;

import java.awt.AWTEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import static java.awt.event.InputEvent.SHIFT_MASK;
import static java.awt.event.InputEvent.CTRL_MASK;
import static java.awt.event.KeyEvent.VK_DOWN;
import static java.awt.event.KeyEvent.VK_END;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.MAX_VALUE;

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
   private static final Integer one = 1;
   private static final Integer mone = -1;
   private static final Integer zero = 0;
   private static final Float f1 = 1.0f;
   private static final Float mf1 = -1.0f;
   private static final Float half = .5f;
   private static final Float mhalf = -.5f;

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

      mkeys.keybind((char) 2, "movescreen", mf1, CTRL_MASK);
      mkeys.keybind((char) 6, "movescreen", f1, CTRL_MASK);
      mkeys.keybind((char) 4, "movescreen", half, CTRL_MASK);
      mkeys.keybind((char) 21, "movescreen", mhalf, CTRL_MASK);
      mkeys.keybind((char) 25, "movescreenline", mone, CTRL_MASK);
      mkeys.keybind((char) 5, "movescreenline", one, CTRL_MASK);
      mkeys.keyactionbind(KeyEvent.VK_PAGE_UP, "movescreen", mf1 , 0);
      mkeys.keyactionbind(KeyEvent.VK_PAGE_DOWN, "movescreen", f1 , 0);

      skeys.keybind('z', "zprocess", null);
      skeys.keybind((char) 12, "redraw", null, CTRL_MASK);
      skeys.keybind((char) 7, "togglestatus", null, CTRL_MASK);
      skeys.keybind(':', "commandproc", null);
      skeys.keybind('Z', "Zprocess", null);
      skeys.keyactionbind(KeyEvent.VK_F1, "nextposwait", ff, CTRL_MASK);
      skeys.keyactionbind(KeyEvent.VK_F1, "nextpos", ff, 0);
      skeys.keyactionbind(KeyEvent.VK_F1, "nextpos", tt, SHIFT_MASK);
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


      mkeys.keybind('h', "movechar", FALSE);
      mkeys.keybind((char) 8, "movechar", FALSE, 0);
      mkeys.keybind('l', "movechar", TRUE);
      mkeys.keybind('^', "starttext", null);
      mkeys.keybind('W', "forwardWord", null);
      mkeys.keybind('w', "forwardword", null);
      mkeys.keybind('b', "backwardword", null);
      mkeys.keybind('B', "backwardWord", null);
      mkeys.keybind('E', "endWord", null);
      mkeys.keybind('e', "endword", null);
      mkeys.keybind('%', "balancechar", null);
      mkeys.keyactionbind(KeyEvent.VK_LEFT, "backwardword", null, CTRL_MASK);
      mkeys.keyactionbind(KeyEvent.VK_LEFT, "movechar", FALSE, 0);
      mkeys.keyactionbind(KeyEvent.VK_RIGHT, "movechar", TRUE, 0);
      mkeys.keyactionbind(KeyEvent.VK_RIGHT, "forwardword", null, CTRL_MASK);
      mkeys.keybind('k', "moveline", FALSE);
      mkeys.keybind('j', "moveline", TRUE);
      mkeys.keyactionbind(KeyEvent.VK_UP, "moveline", FALSE, 0);
      mkeys.keyactionbind(KeyEvent.VK_UP, "shiftmoveline", FALSE , SHIFT_MASK);
      mkeys.keyactionbind(KeyEvent.VK_UP, "movescreenline", mone , CTRL_MASK);
      mkeys.keyactionbind(VK_DOWN, "moveline", TRUE, 0);
      mkeys.keyactionbind(VK_DOWN, "shiftmoveline", TRUE , SHIFT_MASK);
      mkeys.keyactionbind(VK_DOWN, "movescreenline", one , CTRL_MASK);
      mkeys.keyactionbind(VK_END, "linepos", MAX_VALUE, 0);
      mkeys.keyactionbind(VK_END, "gotoline", MAX_VALUE, SHIFT_MASK);
      mkeys.keyactionbind(KeyEvent.VK_HOME, "linepos", zero, 0);
      mkeys.keyactionbind(KeyEvent.VK_HOME, "gotoline", one, SHIFT_MASK);
      mkeys.keyactionbind(KeyEvent.VK_HOME, "gotoline", one, CTRL_MASK);
      mkeys.keyactionbind(VK_END, "gotoline", null, CTRL_MASK);
      mkeys.keybind('+', "movelinestart", one);
      mkeys.keybind((char) 13, "movelinestart", one);
      mkeys.keybind((char) 10, "moveline", TRUE, CTRL_MASK);
      mkeys.keybind((char) 10, "movelinestart", one, 0);
      mkeys.keybind('-', "movelinestart", mone);
      mkeys.keybind('H', "screenmove", 0);
      mkeys.keybind('M', "screenmove", .5f);
      mkeys.keybind('L', "screenmove", .999999f);
      mkeys.keybind('f', "findchar", tt);
      mkeys.keybind('F', "findchar", ft);
      mkeys.keybind('t', "findchar", tf);
      mkeys.keybind('T', "findchar", ff);
      mkeys.keybind(';', "repeatfind", tt);
      mkeys.keybind(',', "repeatfind", ff);
      mkeys.keybind('n', "regsearch", FALSE);
      skeys.keyactionbind(KeyEvent.VK_F3, "regsearch", FALSE, CTRL_MASK);
      mkeys.keybind('N', "regsearch", TRUE);
      mkeys.keybind('/', "searchcommand", FALSE);
      mkeys.keybind('?', "searchcommand", TRUE);
      mkeys.keybind('0', "linepos", zero);
      mkeys.keybind('$', "linepos", MAX_VALUE);
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
      skeys.keybind((char) 29, "gototag", null, CTRL_MASK); //^]
      skeys.keybind((char) 20, "poptag", null, CTRL_MASK); //^t
      skeys.keybind('^', "nextfile", null, CTRL_MASK | SHIFT_MASK);

      skeys.keybind(' ', "moveover", TRUE, SHIFT_MASK);
      skeys.keybind(' ', "moveover", FALSE, 0);
      skeys.keybind('\036', "nextfile", null, CTRL_MASK | SHIFT_MASK);

      // editing keys
      skeys.keybind((char) 2, "movescreen", mone, CTRL_MASK);
      skeys.keybind((char) 18, "redo", null, CTRL_MASK);
      skeys.keybind((char) 26, "redo", null, CTRL_MASK | SHIFT_MASK);
      skeys.keybind('Y', "redo", null, CTRL_MASK);
      skeys.keybind((char) 8, "redo", null, SHIFT_MASK | InputEvent.ALT_MASK);
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
      skeys.keybind((char) 26, "undo", null, CTRL_MASK);
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
      skeys.keyactionbind(KeyEvent.VK_DELETE, "deletetoend", null, SHIFT_MASK);
      skeys.keyactionbind(KeyEvent.VK_INSERT, "insert", ft, 0);
   }

   final boolean domovement(KeyEvent ein, int fiteratei, int riteratei,
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

   public final void run() throws ExitException {
//     try {Thread.sleep(20000);} catch (InterruptedException e) {/*Ignore*/}
//trace("" + e  + " exitflag " + exitflag);
      while (true)
         try {
            while (true) {
               FvContext fvc = FvContext.getCurrFvc();
               Object e = EventQueue.nextEvent(fvc.vi);
               hevent(e, fvc);
            }
         } catch (InterruptedException ex) {
            trace("!! caught interrupted exception");
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
   //trace("returning from run");
   }

   abstract static class JaviEvent {
      abstract void execute() throws IOException;
   }

   private static final int buttonmask  =   InputEvent.BUTTON3_MASK
      | InputEvent.BUTTON3_MASK | InputEvent.BUTTON3_MASK;


   final void hevent(Object ev, FvContext fvc)  throws InputException,
         InterruptedException , IOException {
      //trace("hevent" + ev);

      if (ev instanceof AWTEvent) {
         AWTEvent awtEv = (AWTEvent) ev;
         switch (awtEv.getID()) {
            case KeyEvent.KEY_PRESSED :
               KeyEvent event = (KeyEvent) awtEv;
               char ch = event.getKeyChar();
               if (((ch != '0') || (aiterate != 0))
                     && (ch >= '0' && ch <= '9')) {
                  aiterate = aiterate * 10 + (ch & 0x0f);
                  return;
               } else if (event.getKeyChar() == 27) {
                  aiterate = 0;
                  return;
               }

               riterate = aiterate;   // iterations for command that use 0
               fiterate = aiterate;  // number of iterations forced to 1
               if (fiterate == 0)
                  fiterate = 1;
               if (domovement(event, fiterate, riterate, false, fvc)
                     || screenmovement(awtEv, fvc)) {
                  aiterate = 0;
                  return;
               }

               break;
            default:
               trace("unhandled event " + ev);

         }
      } else if (ev instanceof JaviEvent)  {
         ((JaviEvent) ev).execute();
//         return true;
      }

     // return false;
   }


   private static void trace(String str) {
      Tools.trace(str, 1);
   }
}
