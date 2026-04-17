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
    * <p>Supports two formats:</p>
    * <ul>
    *   <li>{@code "move"} / {@code "edit"} — targets the normal keymap</li>
    *   <li>{@code "keymap.move"} / {@code "keymap.edit"} — targets a
    *       named overlay keymap (e.g. {@code "filelist.move"})</li>
    * </ul>
    *
    * @param groupName key group identifier
    * @return the KeyGroup, or null if name not recognized
    */
   static KeyGroup getKeyGroup(String groupName) {
      if (normalKeyMap == null)
         return null;

      // Check for overlay keymap prefix: "keymapName.group"
      int dot = groupName.indexOf('.');
      if (dot > 0) {
         String kmName = groupName.substring(0, dot);
         String group = groupName.substring(dot + 1);
         KeyMap km = KeyMap.get(kmName);
         if (km == null)
            return null;
         return switch (group) {
            case "move" -> km.getMoveKeys();
            case "edit" -> km.getEditKeys();
            default -> null;
         };
      }

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

      bindMovementKeys(normalKeyMap, sentenceRegex, paragraphRegex,
         sectionRegex);
      bindEditKeys(normalKeyMap);

      // Create buffer-type overlay keymaps (filelist, shell, etc.)
      KeyMap.initBufferKeyMaps(normalKeyMap);
   }

   private static void bindMovementKeys(KeyMap km, Matcher sentenceRegex,
         Matcher paragraphRegex, Matcher sectionRegex) {
      // Screen scrolling
      km.bindMoveKey((char) 2, "movescreen", mf1, CTRL_MASK);
      km.bindMoveKey((char) 6, "movescreen", f1, CTRL_MASK);
      km.bindMoveKey((char) 4, "movescreen", half, CTRL_MASK);
      km.bindMoveKey((char) 21, "movescreen", mhalf, CTRL_MASK);
      km.bindMoveKey((char) 25, "movescreenline", mone, CTRL_MASK);
      km.bindMoveKey((char) 5, "movescreenline", one, CTRL_MASK);
      km.bindMoveAction(JeyEvent.VK_PAGE_UP, "movescreen", mf1, 0);
      km.bindMoveAction(JeyEvent.VK_PAGE_DOWN, "movescreen", f1, 0);

      // Character and word motion
      km.bindMoveKey('h', "movechar", FALSE);
      km.bindMoveKey((char) 8, "movechar", FALSE, 0);
      km.bindMoveKey('l', "movechar", TRUE);
      km.bindMoveKey('^', "starttext", null);
      km.bindMoveKey('W', "forwardWord", null);
      km.bindMoveKey('w', "forwardword", null);
      km.bindMoveKey('b', "backwardword", null);
      km.bindMoveKey('B', "backwardWord", null);
      km.bindMoveKey('E', "endWord", null);
      km.bindMoveKey('e', "endword", null);
      km.bindMoveKey('%', "balancechar", null);
      km.bindMoveAction(JeyEvent.VK_LEFT, "backwardword",
         null, CTRL_MASK);
      km.bindMoveAction(JeyEvent.VK_LEFT, "movechar", FALSE, 0);
      km.bindMoveAction(JeyEvent.VK_RIGHT, "movechar", TRUE, 0);
      km.bindMoveAction(JeyEvent.VK_RIGHT, "forwardword",
         null, CTRL_MASK);

      // Line motion
      km.bindMoveKey('k', "moveline", FALSE);
      km.bindMoveKey('j', "moveline", TRUE);
      km.bindMoveAction(JeyEvent.VK_UP, "moveline", FALSE, 0);
      km.bindMoveAction(JeyEvent.VK_UP, "shiftmoveline",
         FALSE, SHIFT_MASK);
      km.bindMoveAction(JeyEvent.VK_UP, "movescreenline",
         mone, CTRL_MASK);
      km.bindMoveAction(JeyEvent.VK_DOWN, "moveline", TRUE, 0);
      km.bindMoveAction(JeyEvent.VK_DOWN, "shiftmoveline",
         TRUE, SHIFT_MASK);
      km.bindMoveAction(JeyEvent.VK_DOWN, "movescreenline",
         one, CTRL_MASK);
      km.bindMoveAction(JeyEvent.VK_END, "linepos", MAX_VALUE, 0);
      km.bindMoveAction(JeyEvent.VK_END, "gotoline",
         MAX_VALUE, SHIFT_MASK);
      km.bindMoveAction(JeyEvent.VK_HOME, "linepos", zero, 0);
      km.bindMoveAction(JeyEvent.VK_HOME, "gotoline",
         one, SHIFT_MASK);
      km.bindMoveAction(JeyEvent.VK_HOME, "gotoline",
         one, CTRL_MASK);
      km.bindMoveAction(JeyEvent.VK_END, "gotoline",
         null, CTRL_MASK);
      km.bindMoveKey('+', "movelinestart", one);
      km.bindMoveKey((char) 13, "movelinestart", one);
      km.bindMoveKey((char) 10, "moveline", TRUE, CTRL_MASK);
      km.bindMoveKey((char) 10, "movelinestart", one, 0);
      km.bindMoveKey('-', "movelinestart", mone);

      // Screen position
      km.bindMoveKey('H', "screenmove", 0.f);
      km.bindMoveKey('M', "screenmove", .5f);
      km.bindMoveKey('L', "screenmove", .999999f);

      // Find/search
      km.bindMoveKey('f', "findchar", tt);
      km.bindMoveKey('F', "findchar", ft);
      km.bindMoveKey('t', "findchar", tf);
      km.bindMoveKey('T', "findchar", ff);
      km.bindMoveKey(';', "repeatfind", tt);
      km.bindMoveKey(',', "repeatfind", ff);
      km.bindMoveKey('n', "regsearch", FALSE);
      km.bindMoveKey('N', "regsearch", TRUE);
      km.bindMoveKey('/', "searchcommand", FALSE);
      km.bindMoveKey('?', "searchcommand", TRUE);

      // Line positioning and goto
      km.bindMoveKey('0', "linepos", zero);
      km.bindMoveKey('$', "linepos", MAX_VALUE);
      km.bindMoveKey('|', "linepos", null); //diff '0' ???
      km.bindMoveKey('G', "gotoline", null);

      // Marks
      km.bindMoveKey('\'', "findmark", null);
      km.bindMoveKey('m', "mark", null);

      // Regex-based motion (sentences, paragraphs, sections)
      km.bindMoveKey(')', "forwardregex", sentenceRegex);
      km.bindMoveKey('(', "backwardregex", sentenceRegex);
      km.bindMoveKey('}', "forwardregex", paragraphRegex);
      km.bindMoveKey('{', "backwardregex", paragraphRegex); //}
      km.bindMoveKey(']', "forwardregex", sectionRegex);
      km.bindMoveKey('[', "backwardregex", sectionRegex);
   }

   private static void bindEditKeys(KeyMap km) {
      // UI / mode switching
      km.bindEditKey('z', "zprocess", null);
      km.bindEditKey((char) 12, "redraw", null, CTRL_MASK);
      km.bindEditKey((char) 7, "togglestatus", null, CTRL_MASK);
      km.bindEditKey(':', "commandproc", null);
      km.bindEditKey('Z', "Zprocess", null);

      // Function keys
      km.bindEditAction(JeyEvent.VK_F1, "nextposwait",
         ff, CTRL_MASK);
      km.bindEditAction(JeyEvent.VK_F1, "nextpos", ff, 0);
      km.bindEditAction(JeyEvent.VK_F1, "contexthelp",
         null, SHIFT_MASK);
      km.bindEditAction(JeyEvent.VK_F2, "gotofilelist", null, 0);
      km.bindEditAction(JeyEvent.VK_F3, "gotodirlist", null, 0);
      km.bindEditAction(JeyEvent.VK_F4, "gotofontlist", null, 0);
      km.bindEditAction(JeyEvent.VK_F5, "gotopositionlist",
         null, 0);
      km.bindEditAction(JeyEvent.VK_F6, "gotopllist", null, 0);
      km.bindEditAction(JeyEvent.VK_F7, "mk", null, 0);
      km.bindEditAction(JeyEvent.VK_F8, "vt", null, 0);
      //km.bindEditAction(JeyEvent.VK_F9, "startcon", null, 0);
      km.bindEditAction(JeyEvent.VK_F10, "comm", null, 0);
      //km.bindEditAction(JeyEvent.VK_F11, "exec", null, 0);
      km.bindEditAction(JeyEvent.VK_F11, "fullscreen", null, 0);

      // Search (Ctrl-F3 in edit group)
      km.bindEditAction(JeyEvent.VK_F3, "regsearch",
         FALSE, CTRL_MASK);

      // Tags and file switching
      km.bindEditKey((char) 29, "gototag", null, CTRL_MASK); //^]
      km.bindEditKey((char) 20, "poptag", null, CTRL_MASK); //^t
      km.bindEditKey('^', "nextfile",
         null, CTRL_MASK | SHIFT_MASK);
      km.bindEditKey('\036', "nextfile",
         null, CTRL_MASK | SHIFT_MASK);

      // Space
      km.bindEditKey(' ', "moveover", TRUE, SHIFT_MASK);
      km.bindEditKey(' ', "moveover", FALSE, 0);

      // Undo / redo
      km.bindEditKey((char) 2, "movescreen", mone, CTRL_MASK);
      km.bindEditKey((char) 18, "redo", null, CTRL_MASK);
      km.bindEditKey((char) 26, "redo",
         null, CTRL_MASK | SHIFT_MASK);
      km.bindEditKey('Y', "redo", null, CTRL_MASK);
      km.bindEditKey((char) 8, "redo",
         null, SHIFT_MASK | JeyEvent.ALT_MASK);
      km.bindEditKey('U', "undoline", null);
      km.bindEditKey('u', "undo", null);
      km.bindEditKey((char) 8, "undo", null, JeyEvent.ALT_MASK);
      km.bindEditKey((char) 26, "undo", null, CTRL_MASK);

      // Insert / append / open
      km.bindEditKey('i', "insert", ff);
      km.bindEditKey('I', "Insert", ff);
      km.bindEditKey('a', "append", ft);
      km.bindEditKey('A', "Append", ft);
      km.bindEditKey('o', "openline", ft);
      km.bindEditKey('O', "Openline", ft);
      km.bindEditKey('s', "substitute", ft);

      // Yank / put
      km.bindEditKey('p', "putafter", null);
      km.bindEditKey('P', "putbefore", null);
      km.bindEditKey('y', "yankmode", null);
      km.bindEditKey('Y', "yank", null);

      // Delete / change
      km.bindEditKey('S', "Substitute", null);
      km.bindEditKey('X', "deletechars", ff);
      km.bindEditKey((char) 127, "deletechars", ff);
      km.bindEditKey('x', "deletechars", tt);
      km.bindEditKey('D', "deletetoend", tt);
      km.bindEditKey('C', "deletetoendi", null);
      km.bindEditKey('c', "changemode", null);
      km.bindEditKey('d', "deletemode", null);

      // Visual mode
      km.bindEditKey('v', "markmode", zero);
      km.bindEditKey('V', "markmode", one);

      // Miscellaneous editing
      km.bindEditKey('J', "joinlines", null);
      km.bindEditKey('r', "subchar", null);
      km.bindEditKey('~', "changecase", null);
      km.bindEditKey('R', "insert", tf);
      km.bindEditKey('.', "doover", tt);
      km.bindEditKey('"', "qmode", null);
      km.bindEditKey('<', "shiftmode", one);
      km.bindEditKey('>', "shiftmode", mone);
      km.bindEditAction(JeyEvent.VK_DELETE, "deletechars", one, 0);
      km.bindEditAction(JeyEvent.VK_DELETE, "deletetoend",
         null, SHIFT_MASK);
      km.bindEditAction(JeyEvent.VK_INSERT, "insert", ft, 0);
      km.bindEditKey('j', "jsevalfile", null, JeyEvent.ALT_MASK);
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
         ContextHelp.onCommandCompleted(fvc);
         return;
      }
   }
}
