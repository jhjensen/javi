package javi;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import history.Tools;
import static history.Tools.trace;

public final class Command extends Rgroup {

   enum Cmd {
      UNUSED,        // 0: noop
      READ_FILE,     // 1: read file (r)
      TAB_STOP,      // 2: set tab stop
      TERMINATE,     // 3: terminate editor
      COMMAND_PROC,  // 4: command processor (:)
      CHECKOUT,      // 5: checkout/open file
      SET,           // 6: set option
      RELOAD,        // 7: reload file (e!)
      HELP,          // 8: show help
      MAP,           // 9: show key map
   }

   private static final Cmd[] CMDS = Cmd.values();

   private static Command instance;

   private static final String[] rnames = {
      "",
      "r",
      "tabstop",
      "terminatewep",
      "commandproc",
      "checkout",  //5
      "set",
      "e!",
      "help",       //8
      "map",        //9
   };

   private static final String[] descs = {
      null,
      "read file into buffer",
      "set tab stop width",
      "terminate editor",
      "enter ex command mode",
      "open/checkout file",
      "set editor option",
      "reload current file",
      "show help",
      "show key bindings",
   };

   static void init()  {
      instance = new Command();
      instance.register(rnames, descs);
   }

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws InterruptedException {
      //trace("vic doroutine rnum = " + rnum);
      try  {
         switch (CMDS[rnum]) {
            case UNUSED:
               return null; // noop
            case READ_FILE:
               return readFile(arg, fvc);
            case TAB_STOP:
               fvc.vi.setTabStop(oToInt(arg));
               return null;
            case TERMINATE:
               Runtime.getRuntime().halt(0);
               return null;
            case COMMAND_PROC:
               commandproc(fvc);
               return null;
            case CHECKOUT:
               // Type safety: arg should be String from key mapping
               checkout(arg instanceof String ? (String) arg : arg.toString(), fvc);
               return null;
            case SET:
               // Type safety: arg should be String from key mapping
               setcommand(arg instanceof String ? (String) arg : arg.toString(), fvc);
               return null;
            case RELOAD:
               fvc.edvec.reload();
               return null;
            case HELP:
               showHelp((String) arg, fvc);
               return null;
            case MAP:
               showMap(arg instanceof String ? (String) arg : null, fvc);
               return null;
            default:
               throw new RuntimeException("doroutine called with " + rnum);
         }
      } catch (IOException e)  {
         UI.reportMessage("command caught IOException: " + e.getMessage());
         trace("command caught IOException", e);
      } catch (InputException e) {
         trace("command caught InputException", e);
         UI.reportMessage(e.toString());
      }
      return null;
   }

   private static final ArrayList<String> cmdlist = new ArrayList<>();
   private static final ArrayList<String> awtCmdList = new ArrayList<>();

   // Matches ${NAME} or $NAME (NAME starts with letter/_, then letters/digits/_).
   private static final Pattern VAR_REF = Pattern.compile(
      "\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}|\\$([A-Za-z_][A-Za-z0-9_]*)");

   // Matches: let NAME=value  (whitespace flexible; value may be empty).
   private static final Pattern LET_DEF = Pattern.compile(
      "^\\s*let\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$");

   /**
    * Reads initialization commands from the .javini configuration file.
    * Each line in the file is added to the command list for later execution.
    *
    * <p>Preprocessing:
    * <ul>
    *   <li>Blank lines are skipped.</li>
    *   <li>Lines whose first non-whitespace character is {@code #} are
    *       treated as comments and skipped.</li>
    *   <li>{@code let NAME=VALUE} lines define variables for subsequent
    *       expansion and are not dispatched as commands.</li>
    *   <li>{@code $NAME} and {@code ${NAME}} references are expanded from
    *       in-file {@code let} definitions first, then from the process
    *       environment. Unknown references expand to the empty string.</li>
    * </ul>
    *
    * @throws IOException if an I/O error occurs reading the file
    */
   static void readini() throws IOException {
      FileDescriptor.LocalFile ifile = FileDescriptor.LocalFile.make(".javini");
      if (!ifile.isFile())
         return;
      try (BufferedReader ini = ifile.getBufferedReader()) {
         preprocess(ini, cmdlist);
      }
   }

   /**
    * Preprocesses .javini-style input, appending command lines to {@code out}.
    * See {@link #readini()} for the supported syntax.
    *
    * @param in source of lines to preprocess
    * @param out list to append non-comment, non-directive command lines to
    * @throws IOException if reading from {@code in} fails
    */
   static void preprocess(Reader in, List<String> out) throws IOException {
      BufferedReader br = (in instanceof BufferedReader)
         ? (BufferedReader) in : new BufferedReader(in);
      Map<String, String> vars = new HashMap<>();
      for (String line; null != (line = br.readLine());) {
         String trimmed = line.stripLeading();
         if (trimmed.isEmpty() || trimmed.charAt(0) == '#')
            continue;
         Matcher lm = LET_DEF.matcher(line);
         if (lm.matches()) {
            vars.put(lm.group(1), expandVars(lm.group(2), vars));
            continue;
         }
         String expanded = expandVars(line, vars);
         if (expanded.stripLeading().startsWith("awt."))
            awtCmdList.add(expanded);
         else
            out.add(expanded);
      }
   }

   private static String expandVars(String s, Map<String, String> vars) {
      Matcher m = VAR_REF.matcher(s);
      StringBuilder sb = new StringBuilder();
      while (m.find()) {
         String name = (null != m.group(1)) ? m.group(1) : m.group(2);
         String val = vars.get(name);
         if (null == val)
            val = System.getenv(name);
         if (null == val)
            val = "";
         m.appendReplacement(sb, Matcher.quoteReplacement(val));
      }
      m.appendTail(sb);
      return sb.toString();
   }

   public static void execCmdList() {
      Iterator<String> cit = cmdlist.iterator();
      while (cit.hasNext()) {
         try {
            command(cit.next(), null, null);
            cit.remove();
         } catch (DeferCommandException e) {
            // leave in list for later execution
         }
      }
   }

   /**
    * Returns and clears the list of {@code awt.*} commands separated
    * during .javini preprocessing. AwtInterface calls this to apply
    * AWT-specific rendering commands directly.
    */
   public static List<String> takeAwtCommands() {
      List<String> result = new ArrayList<>(awtCmdList);
      awtCmdList.clear();
      return result;
   }

   static void doneInit() {
      execCmdList(); // retry commands deferred during early init
      if (cmdlist.size() != 0) {
         StringBuilder bf = new StringBuilder(
            "command list has unexecuted commands:\n");
         Iterator<String> cit = cmdlist.iterator();
         while (cit.hasNext()) {
            bf.append(cit.next());
            cit.remove();
         }
         UI.reportMessage(bf.toString());
      }
   }

   /** Package-private access to cmdlist size for testing. */
   static int cmdListSize() {
      return cmdlist.size();
   }

   /** Package-private: add a command to the pending list for testing. */
   static void addToCmdList(String cmd) {
      cmdlist.add(cmd);
   }

   /**
    *
    * <p>When no topic is specified, shows context-sensitive help
    * that dynamically lists keybindings for the active keymap.
    * If the help buffer is already showing, toggles it off.</p>
    *
    * @param topic the help topic (null for context help)
    * @param fvc the current file-view context
    * @throws InputException if help cannot be displayed
    */
   private static void showHelp(String topic, FvContext fvc)
         throws InputException {
      // Toggle: if no topic and already showing help, dismiss
      if ((null == topic || topic.isEmpty())
            && ContextHelp.isShowingHelp(fvc)) {
         ContextHelp.toggle(fvc);
         return;
      }
      // No topic: show context-sensitive help
      if (null == topic || topic.isEmpty()) {
         ContextHelp.toggle(fvc);
         return;
      }
      // Specific topic: delegate to static HelpSystem
      TextEdit<String> helpBuffer = HelpSystem.getHelp(topic);
      FvContext.connectFv(helpBuffer, fvc.vi);
   }

   /**
    * Display key bindings, optionally filtered by keymap name.
    *
    * <p>When no filter is given, shows context-aware bindings for
    * the active buffer. When a keymap name is provided (e.g.
    * {@code :map filelist}), shows only bindings from that keymap.</p>
    *
    * @param keymapFilter optional keymap name to filter by, or null
    * @param fvc the current file-view context
    * @throws InputException if bindings cannot be displayed
    */
   private static void showMap(String keymapFilter, FvContext fvc)
         throws InputException {
      TextEdit<String> mapBuffer;
      if (keymapFilter != null && !keymapFilter.isEmpty()) {
         mapBuffer = HelpSystem.getFilteredBindings(keymapFilter.trim());
      } else {
         mapBuffer = HelpSystem.getContextBindings(fvc);
      }
      FvContext.connectFv(mapBuffer, fvc.vi);
   }

   private Object readFile(Object arg, FvContext fvc) throws
         InputException, IOException {
      if (arg == null)
         throw new InputException("read command needs filename");
      String fname = arg.toString().trim();
      if (fname.charAt(0) == '<') {
         // Executer ex = new Executer("bash -c " + fname.substring(1,fname.length()));
         String[] cmd = {"bash", "-c", fname.substring(1, fname.length())};
         fvc.edvec.insertStream(Tools.runcmd(cmd), fvc.inserty());
      } else {
         FileDescriptor.LocalFile ifile =
            FileDescriptor.LocalFile.make(arg.toString());
         fvc.edvec.insertStream(ifile.getBufferedReader(), fvc.inserty());
      }
      return null;
   }

   private static void commandproc(FvContext fvc) {

      ContextHelp.onSubModeEntered("commandproc");
      String line = InsertBuffer.getcomline(":");
      line = line.substring(1, line.length());
      command(line, fvc, null);
   }

   private static void checkout(String filename, FvContext fvc) {
      command("vcscheckout", fvc, filename);
   }

   private Object setcommand(String argline, FvContext fvc) throws
         IOException, InterruptedException, InputException {
      if (argline == null)
         throw new InputException("read command needs filename");
      int eqindex = argline.indexOf('=');
      if (eqindex == -1)
         throw new InputException("invalid set command");
      String var = argline.substring(0, eqindex).trim();
      String val = argline.substring(eqindex + 1, argline.length()).trim();
      KeyBinding kb = bindingLookup(var);
      if (kb == null)
         throw new InputException("setting unknown variable:" + var);

      return kb.dobind(val, 0, 0, fvc, false);
   }

   private int oToInt(Object str) throws InputException {
      if (str == null)
         throw new InputException("command needs decimal number");
      try {
         return Integer.parseInt(str.toString().trim());
      } catch (NumberFormatException e) {
         throw new InputException("command needs decimal number", e);
      }
   }

   /**
    * Check whether a named command exists in the binding table.
    *
    * @param name the command name to look up
    * @return true if the command is registered
    */
   public static boolean hasCommand(String name) {
      return bindingLookup(name) != null;
   }

   public static void command(String line, FvContext fvc, Object args) {
      //trace("command " + line);
      String command;
      if (fvc == null)
         fvc = FvContext.getCurrFvc();
      if (args == null)
         if (line.indexOf(' ') != -1) {
            command = line.substring(0, line.indexOf(' '));
            String l2 = line.substring(command.length(), line.length()).trim();
            args = l2.length() == 0  ? null : l2;
         } else {
            command = line;
         }
      else
         command = line;
      try {
         KeyBinding kb = bindingLookup(command);
         //trace("command kb = " + kb);
         if (kb != null) {
            kb.dobind(args, 0, 0, fvc, false);
         } else if (fvc != null) {
            int newpos = fvc.edvec.processCommand(line, fvc.inserty());
            if (newpos == -1)
               throw new InputException("Unknown Command:" + line);
            else
               fvc.cursoryabs(newpos);
         } else {
            throw new InputException("Unknown Command:" + line);
         }
      } catch (InputException e) {
         UI.reportMessage(e.toString());
      } catch (IOException e) {
         UI.reportMessage(e.toString());
      } catch (InterruptedException e) {
         UI.reportMessage(e.toString());
      }
   }

}
