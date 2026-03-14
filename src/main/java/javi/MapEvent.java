package javi;

import java.io.IOException;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.MAX_VALUE;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javi.JeyEvent.SHIFT_MASK;
import static javi.JeyEvent.CTRL_MASK;

import static history.Tools.trace;

/**
 * Key mapping and event dispatch for vi-style modal editing.
 *
 * <p>MapEvent manages the binding of keyboard input to editor commands:
 * <ul>
 *   <li><b>Key bindings</b>: Maps key combinations to command names</li>
 *   <li><b>Modal dispatch</b>: Different bindings for command vs insert mode</li>
 *   <li><b>Count handling</b>: Numeric prefixes (e.g., "5j" moves 5 lines)</li>
 *   <li><b>Dot command</b>: Repeats last edit operation</li>
 * </ul>
 *
 * <h2>Key Groups</h2>
 * <p>A single {@link KeyMap} ("normal") wraps two {@link KeyGroup}s:
 * <ul>
 *   <li>movement keys (hjkl, arrows, word motion, scrolling)</li>
 *   <li>editing keys (function keys, control chars, insert/delete)</li>
 * </ul>
 * Buffer-specific overlay keymaps (filelist, shell) chain to normal.</p>
 *
 * <h2>Binding Methods</h2>
 * <ul>
 *   <li>{@code keybind(char, command, arg)} - Bind character to command</li>
 *   <li>{@code keyactionbind(keyCode, command, arg, modifiers)} - Bind key code</li>
 * </ul>
 *
 * <h2>Event Processing</h2>
 * <p>The main loop calls {@link #nextEvent} which:</p>
 * <ol>
 *   <li>Gets key event from {@link EventQueue}</li>
 *   <li>Accumulates count prefix if digit</li>
 *   <li>Looks up binding in appropriate key group</li>
 *   <li>Executes command via {@link Rgroup#doCommand}</li>
 * </ol>
 *
 * <h2>Initialization</h2>
 * <p>{@link #bindCommands} sets up all default key bindings at startup.
 * Bindings can be modified via {@code :map} ex command.</p>
 *
 * @see KeyGroup
 * @see Rgroup
 * @see JeyEvent
 */
public final class MapEvent {

   private static KeyMap normalKeyMap;

//private FvContext fvc=0;

   private static final boolean[] tt = {true, true};
   private static final boolean[] ff = {false, false};
   private static final boolean[] ft = {false, true};
   private static final boolean[] tf = {true, false};
   private static final Integer one = 1;
   private static final Integer mone = -1;
   private static final Integer zero = 0;
   private static final Float f1 = 1.0f;
   private static final Float mf1 = -1.0f;
   private static final Float half = .5f;
   private static final Float mhalf = -.5f;

   private static int aiterate = 0;
   private static int riterate = 0;   //iterations for command that use 0
   private static int fiterate = 0;  //number of iterations forced to 1

   /**
    * Get all key bindings as formatted strings.
    *
    * @return list of key binding descriptions
    */
   static java.util.List<String> getAllBindings() {
      java.util.List<String> result = new java.util.ArrayList<>();
      if (normalKeyMap == null) {
         result.add("(key bindings not yet initialized)");
         return result;
      }
      result.add("MOVEMENT KEYS");
      result.add("-------------");
      result.addAll(normalKeyMap.getMoveKeys().getBindingList());
      result.add("");
      result.add("COMMAND KEYS");
      result.add("------------");
      result.addAll(normalKeyMap.getEditKeys().getBindingList());
      return result;
   }

   /**
    * Get a named keygroup for runtime binding modification.
    *
    * @param groupName "move" for movement keys, "edit" for editing keys
    * @return the KeyGroup, or null if name not recognized
    */
   static KeyGroup getKeyGroup(String groupName) {
      if (normalKeyMap == null)
         return null;
      return switch (groupName) {
         case "move" -> normalKeyMap.getMoveKeys();
         case "edit" -> normalKeyMap.getEditKeys();
         default -> null;
      };
   }

   static void bindCommands() {
      Matcher sentenceRegex = Pattern.compile("\\.( |$)").matcher("");
      Matcher paragraphRegex = Pattern.compile("^ *$").matcher("");
      Matcher sectionRegex = Pattern.compile("^[^ ].*\\{").matcher("");

      // Create the normal keymap first, then populate via KeyMap API
      KeyGroup moveKeys = new KeyGroup("normal-move");
      KeyGroup editKeys = new KeyGroup("normal-edit");
      normalKeyMap = new KeyMap("normal", moveKeys, editKeys);
      KeyMap.register(normalKeyMap);

      // --- Movement keys via KeyMap API ---

      // Screen scrolling
      normalKeyMap.bindMoveKey((char) 2, "movescreen", mf1, CTRL_MASK);
      normalKeyMap.bindMoveKey((char) 6, "movescreen", f1, CTRL_MASK);
      normalKeyMap.bindMoveKey((char) 4, "movescreen", half, CTRL_MASK);
      normalKeyMap.bindMoveKey((char) 21, "movescreen", mhalf, CTRL_MASK);
      normalKeyMap.bindMoveKey((char) 25, "movescreenline", mone, CTRL_MASK);
      normalKeyMap.bindMoveKey((char) 5, "movescreenline", one, CTRL_MASK);
      normalKeyMap.bindMoveAction(JeyEvent.VK_PAGE_UP, "movescreen", mf1, 0);
      normalKeyMap.bindMoveAction(JeyEvent.VK_PAGE_DOWN, "movescreen", f1, 0);

      // Character and word motion
      normalKeyMap.bindMoveKey('h', "movechar", FALSE);
      normalKeyMap.bindMoveKey((char) 8, "movechar", FALSE, 0);
      normalKeyMap.bindMoveKey('l', "movechar", TRUE);
      normalKeyMap.bindMoveKey('^', "starttext", null);
      normalKeyMap.bindMoveKey('W', "forwardWord", null);
      normalKeyMap.bindMoveKey('w', "forwardword", null);
      normalKeyMap.bindMoveKey('b', "backwardword", null);
      normalKeyMap.bindMoveKey('B', "backwardWord", null);
      normalKeyMap.bindMoveKey('E', "endWord", null);
      normalKeyMap.bindMoveKey('e', "endword", null);
      normalKeyMap.bindMoveKey('%', "balancechar", null);
      normalKeyMap.bindMoveAction(JeyEvent.VK_LEFT, "backwardword",
         null, CTRL_MASK);
      normalKeyMap.bindMoveAction(JeyEvent.VK_LEFT, "movechar", FALSE, 0);
      normalKeyMap.bindMoveAction(JeyEvent.VK_RIGHT, "movechar", TRUE, 0);
      normalKeyMap.bindMoveAction(JeyEvent.VK_RIGHT, "forwardword",
         null, CTRL_MASK);

      // Line motion
      normalKeyMap.bindMoveKey('k', "moveline", FALSE);
      normalKeyMap.bindMoveKey('j', "moveline", TRUE);
      normalKeyMap.bindMoveAction(JeyEvent.VK_UP, "moveline", FALSE, 0);
      normalKeyMap.bindMoveAction(JeyEvent.VK_UP, "shiftmoveline",
         FALSE, SHIFT_MASK);
      normalKeyMap.bindMoveAction(JeyEvent.VK_UP, "movescreenline",
         mone, CTRL_MASK);
      normalKeyMap.bindMoveAction(JeyEvent.VK_DOWN, "moveline", TRUE, 0);
      normalKeyMap.bindMoveAction(JeyEvent.VK_DOWN, "shiftmoveline",
         TRUE, SHIFT_MASK);
      normalKeyMap.bindMoveAction(JeyEvent.VK_DOWN, "movescreenline",
         one, CTRL_MASK);
      normalKeyMap.bindMoveAction(JeyEvent.VK_END, "linepos", MAX_VALUE, 0);
      normalKeyMap.bindMoveAction(JeyEvent.VK_END, "gotoline",
         MAX_VALUE, SHIFT_MASK);
      normalKeyMap.bindMoveAction(JeyEvent.VK_HOME, "linepos", zero, 0);
      normalKeyMap.bindMoveAction(JeyEvent.VK_HOME, "gotoline",
         one, SHIFT_MASK);
      normalKeyMap.bindMoveAction(JeyEvent.VK_HOME, "gotoline",
         one, CTRL_MASK);
      normalKeyMap.bindMoveAction(JeyEvent.VK_END, "gotoline",
         null, CTRL_MASK);
      normalKeyMap.bindMoveKey('+', "movelinestart", one);
      normalKeyMap.bindMoveKey((char) 13, "movelinestart", one);
      normalKeyMap.bindMoveKey((char) 10, "moveline", TRUE, CTRL_MASK);
      normalKeyMap.bindMoveKey((char) 10, "movelinestart", one, 0);
      normalKeyMap.bindMoveKey('-', "movelinestart", mone);

      // Screen position
      normalKeyMap.bindMoveKey('H', "screenmove", 0.f);
      normalKeyMap.bindMoveKey('M', "screenmove", .5f);
      normalKeyMap.bindMoveKey('L', "screenmove", .999999f);

      // Find/search
      normalKeyMap.bindMoveKey('f', "findchar", tt);
      normalKeyMap.bindMoveKey('F', "findchar", ft);
      normalKeyMap.bindMoveKey('t', "findchar", tf);
      normalKeyMap.bindMoveKey('T', "findchar", ff);
      normalKeyMap.bindMoveKey(';', "repeatfind", tt);
      normalKeyMap.bindMoveKey(',', "repeatfind", ff);
      normalKeyMap.bindMoveKey('n', "regsearch", FALSE);
      normalKeyMap.bindMoveKey('N', "regsearch", TRUE);
      normalKeyMap.bindMoveKey('/', "searchcommand", FALSE);
      normalKeyMap.bindMoveKey('?', "searchcommand", TRUE);

      // Line positioning and goto
      normalKeyMap.bindMoveKey('0', "linepos", zero);
      normalKeyMap.bindMoveKey('$', "linepos", MAX_VALUE);
      normalKeyMap.bindMoveKey('|', "linepos", null); //diff '0' ???
      normalKeyMap.bindMoveKey('G', "gotoline", null);

      // Marks
      normalKeyMap.bindMoveKey('\'', "findmark", null);
      normalKeyMap.bindMoveKey('m', "mark", null);

      // Regex-based motion (sentences, paragraphs, sections)
      normalKeyMap.bindMoveKey(')', "forwardregex", sentenceRegex);
      normalKeyMap.bindMoveKey('(', "backwardregex", sentenceRegex);
      normalKeyMap.bindMoveKey('}', "forwardregex", paragraphRegex);
      normalKeyMap.bindMoveKey('{', "backwardregex", paragraphRegex); //}
      normalKeyMap.bindMoveKey(']', "forwardregex", sectionRegex);
      normalKeyMap.bindMoveKey('[', "backwardregex", sectionRegex);

      // --- Edit/command keys via KeyMap API ---

      // UI / mode switching
      normalKeyMap.bindEditKey('z', "zprocess", null);
      normalKeyMap.bindEditKey((char) 12, "redraw", null, CTRL_MASK);
      normalKeyMap.bindEditKey((char) 7, "togglestatus", null, CTRL_MASK);
      normalKeyMap.bindEditKey(':', "commandproc", null);
      normalKeyMap.bindEditKey('Z', "Zprocess", null);

      // Function keys
      normalKeyMap.bindEditAction(JeyEvent.VK_F1, "nextposwait",
         ff, CTRL_MASK);
      normalKeyMap.bindEditAction(JeyEvent.VK_F1, "nextpos", ff, 0);
      normalKeyMap.bindEditAction(JeyEvent.VK_F1, "nextpos", tt, SHIFT_MASK);
      normalKeyMap.bindEditAction(JeyEvent.VK_F2, "gotofilelist", null, 0);
      normalKeyMap.bindEditAction(JeyEvent.VK_F3, "gotodirlist", null, 0);
      normalKeyMap.bindEditAction(JeyEvent.VK_F4, "gotofontlist", null, 0);
      normalKeyMap.bindEditAction(JeyEvent.VK_F5, "gotopositionlist",
         null, 0);
      normalKeyMap.bindEditAction(JeyEvent.VK_F6, "gotopllist", null, 0);
      normalKeyMap.bindEditAction(JeyEvent.VK_F7, "mk", null, 0);
      normalKeyMap.bindEditAction(JeyEvent.VK_F8, "vt", null, 0);
      //normalKeyMap.bindEditAction(JeyEvent.VK_F9, "startcon", null, 0);
      normalKeyMap.bindEditAction(JeyEvent.VK_F10, "comm", null, 0);
      //normalKeyMap.bindEditAction(JeyEvent.VK_F11, "exec", null, 0);
      normalKeyMap.bindEditAction(JeyEvent.VK_F11, "fullscreen", null, 0);

      // Search (Ctrl-F3 in edit group)
      normalKeyMap.bindEditAction(JeyEvent.VK_F3, "regsearch",
         FALSE, CTRL_MASK);

      // Tags and file switching
      normalKeyMap.bindEditKey((char) 29, "gototag", null, CTRL_MASK); //^]
      normalKeyMap.bindEditKey((char) 20, "poptag", null, CTRL_MASK); //^t
      normalKeyMap.bindEditKey('^', "nextfile",
         null, CTRL_MASK | SHIFT_MASK);
      normalKeyMap.bindEditKey('\036', "nextfile",
         null, CTRL_MASK | SHIFT_MASK);

      // Space
      normalKeyMap.bindEditKey(' ', "moveover", TRUE, SHIFT_MASK);
      normalKeyMap.bindEditKey(' ', "moveover", FALSE, 0);

      // Undo / redo
      normalKeyMap.bindEditKey((char) 2, "movescreen", mone, CTRL_MASK);
      normalKeyMap.bindEditKey((char) 18, "redo", null, CTRL_MASK);
      normalKeyMap.bindEditKey((char) 26, "redo",
         null, CTRL_MASK | SHIFT_MASK);
      normalKeyMap.bindEditKey('Y', "redo", null, CTRL_MASK);
      normalKeyMap.bindEditKey((char) 8, "redo",
         null, SHIFT_MASK | JeyEvent.ALT_MASK);
      normalKeyMap.bindEditKey('U', "undoline", null);
      normalKeyMap.bindEditKey('u', "undo", null);
      normalKeyMap.bindEditKey((char) 8, "undo", null, JeyEvent.ALT_MASK);
      normalKeyMap.bindEditKey((char) 26, "undo", null, CTRL_MASK);

      // Insert / append / open
      normalKeyMap.bindEditKey('i', "insert", ff);
      normalKeyMap.bindEditKey('I', "Insert", ff);
      normalKeyMap.bindEditKey('a', "append", ft);
      normalKeyMap.bindEditKey('A', "Append", ft);
      normalKeyMap.bindEditKey('o', "openline", ft);
      normalKeyMap.bindEditKey('O', "Openline", ft);
      normalKeyMap.bindEditKey('s', "substitute", ft);

      // Yank / put
      normalKeyMap.bindEditKey('p', "putafter", null);
      normalKeyMap.bindEditKey('P', "putbefore", null);
      normalKeyMap.bindEditKey('y', "yankmode", null);
      normalKeyMap.bindEditKey('Y', "yank", null);

      // Delete / change
      normalKeyMap.bindEditKey('S', "Substitute", null);
      normalKeyMap.bindEditKey('X', "deletechars", ff);
      normalKeyMap.bindEditKey((char) 127, "deletechars", ff);
      normalKeyMap.bindEditKey('x', "deletechars", tt);
      normalKeyMap.bindEditKey('D', "deletetoend", tt);
      normalKeyMap.bindEditKey('C', "deletetoendi", null);
      normalKeyMap.bindEditKey('c', "changemode", null);
      normalKeyMap.bindEditKey('d', "deletemode", null);

      // Visual mode
      normalKeyMap.bindEditKey('v', "markmode", zero);
      normalKeyMap.bindEditKey('V', "markmode", one);

      // Miscellaneous editing
      normalKeyMap.bindEditKey('J', "joinlines", null);
      normalKeyMap.bindEditKey('r', "subchar", null);
      normalKeyMap.bindEditKey('~', "changecase", null);
      normalKeyMap.bindEditKey('R', "insert", tf);
      normalKeyMap.bindEditKey('.', "doover", tt);
      normalKeyMap.bindEditKey('"', "qmode", null);
      normalKeyMap.bindEditKey('<', "shiftmode", one);
      normalKeyMap.bindEditKey('>', "shiftmode", mone);
      normalKeyMap.bindEditAction(JeyEvent.VK_DELETE, "deletechars", one, 0);
      normalKeyMap.bindEditAction(JeyEvent.VK_DELETE, "deletetoend",
         null, SHIFT_MASK);
      normalKeyMap.bindEditAction(JeyEvent.VK_INSERT, "insert", ft, 0);
      normalKeyMap.bindEditKey('j', "jsevalfile", null, JeyEvent.ALT_MASK);

      // Create buffer-type overlay keymaps (filelist, shell, etc.)
      KeyMap.initBufferKeyMaps(normalKeyMap);
   }

   /**
    * Get the normal-mode keymap (root keymap for command mode).
    */
   static KeyMap getNormalKeyMap() {
      return normalKeyMap;
   }

   /**
    * Resolve the effective keymap for a given context.
    * Checks for a buffer-specific keymap overlay first, otherwise
    * returns the default normal keymap.
    */
   static KeyMap getActiveKeyMap(FvContext fvc) {
      if (fvc != null) {
         KeyMap bufferMap = fvc.getKeyMap();
         if (bufferMap != null)
            return bufferMap;
         // Auto-detect buffer type and cache the keymap
         bufferMap = KeyMap.resolveForBuffer(fvc.edvec);
         if (bufferMap != null) {
            fvc.setKeyMap(bufferMap);
            return bufferMap;
         }
      }
      return normalKeyMap;
   }

   static boolean domovement(JeyEvent ein, int fiteratei, int riteratei,
         boolean dotmode, FvContext fvc) throws
         InterruptedException, IOException, InputException {
      //trace("domovement fvc = " + fvc);
      //trace("domovement ev = " + ein);
      KeyMap active = getActiveKeyMap(fvc);
      Rgroup.KeyBinding binding = active.lookupMove(ein);
      if (null != binding) {
         //trace("binding rg = " + binding.rg + " event " + ein);
         binding.dobind(fiteratei, riteratei, fvc, dotmode);
         return true;
      } else
         return false;
   }

   private static boolean screenmovement(JeyEvent e1, FvContext fvc) throws
         InterruptedException, InputException, IOException {
      KeyMap active = getActiveKeyMap(fvc);
      Rgroup.KeyBinding binding = active.lookupEdit(e1);
      if (null == binding)
         return false;
      //trace("binding  = " + binding);
      binding.dobind(fiterate, riterate, fvc, false);
      return true;

   }

   private static final Matcher findfile =
      Pattern.compile("(.*[\\\\/])([^\\/]*)$").matcher("");

   static void makeWriteable(EditContainer edv, String filename) throws
         IOException {

      UI.Buttons diaflag = UI.chooseWriteable(filename);
      switch (diaflag) {

         case CHECKOUT:
            //Command.command("vcscheckout", null, filename);
            break;
         case MAKEWRITEABLE:
            edv.setReadOnly(false);
            break;
         case DONOTHING:
         case WINDOWCLOSE:
            break;
         case MAKEBACKUP:
            edv.backup(".orig");
            break;
         case USESVN:
/*  its been a long time since this was tested
            String svnstr =  (findfile.reset(filename).find()
               ? findfile.group(1) + ".svn/text-base/" + findfile.group(2)
               : "./.svn/text-base/" + filename
               )  + ".svn-base";

            //trace("svnstr "  + svnstr);
            BufferedReader fr = new BufferedReader(
               new FileReader(svnstr),??? encoding???);
            try {
               int lineno = 0;
               int linemax = edv.finish();
               String line;
               while (null != (line = fr.readLine())) {
                  if ((++lineno  >= linemax))
                     break;
                  if (!line.equals(edv.at(lineno))) {
                     UI.reportMessage(
                        "svn base file not equal to current file at "
                        + (lineno - 1) + ":" + edv.at(lineno - 1) + ":"
                        + line + ":");
                     return;
                  }
               }
               if (null == line && lineno + 1 == linemax)
                  edv.setReadOnly(false);
               else
                  UI.reportMessage("svn base file not equal to current file");
            } finally {
               fr.close();
            }
            break;
*/
         default:

            throw new RuntimeException("bad diaflag = " + diaflag);
      }
   }

   static void run() throws ExitException {
//     try {Thread.sleep(20000);} catch (InterruptedException e) {/*Ignore*/}
//trace("" + e  + " exitflag " + exitflag);
      while (true)
         try {
            while (true) {
               FvContext fvc = FvContext.getCurrFvc();
               JeyEvent e = EventQueue.nextEvent(fvc.vi);
               hevent(e, fvc);
            }
         } catch (InterruptedException ex) {
            trace("!! caught interrupted exception");
         } catch (EditContainer.ReadOnlyException e) {
            try {
               makeWriteable(e.getEv(), e.getMessage());
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

   static void hevent(JeyEvent jEv, FvContext fvc)  throws InputException,
         InterruptedException, IOException {
      //trace("hevent" + jEv);

      char ch = jEv.getKeyChar();
      if ((('0' != ch) || (0 != aiterate))
            && ('0' <= ch && '9' >= ch)) {
         aiterate = aiterate * 10 + (ch & 0x0f);
         return;
      } else if (27 == jEv.getKeyChar()) {
         aiterate = 0;
         return;
      }

      riterate = aiterate;   // iterations for command that use 0
      fiterate = aiterate;  // number of iterations forced to 1
      if (0 == fiterate)
         fiterate = 1;
      if (fvc.edvec.handleKey(jEv, fvc)
            || domovement(jEv, fiterate, riterate, false, fvc)
            || screenmovement(jEv, fvc)) {
         aiterate = 0;
         return;
      }
   }
}
