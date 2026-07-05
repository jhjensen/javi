package javi;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import static history.Tools.trace;

/**
 * Abstract base class for command groups in the vi command system.
 *
 * <p>Rgroup (Routine Group) is the foundation of Javi's command architecture.
 * Each subclass groups related commands (edit, move, misc, etc.) and handles
 * their execution through the abstract {@link #doroutine} method.</p>
 *
 * <h2>Command Registration</h2>
 * <p>Commands are registered via {@link #register(String[])} which populates
 * the static {@code cmhash} HashMap. Each command maps to a {@link KeyBinding}
 * that captures the Rgroup instance and command index.</p>
 *
 * <h2>Key Subclasses</h2>
 * <ul>
 *   <li>{@link EditGroup} - Insert, delete, change commands</li>
 *   <li>{@link MoveGroup} - Cursor movement commands</li>
 *   <li>{@link MiscCommands} - File operations, settings, etc.</li>
 *   <li>{@link KeyGroup} - Keyboard mapping commands</li>
 * </ul>
 *
 * <h2>Command Execution Flow</h2>
 * <ol>
 *   <li>{@link MapEvent} maps keypress to command name</li>
 *   <li>{@link #bindingLookup} finds KeyBinding in cmhash</li>
 *   <li>{@link KeyBinding#dobind} calls {@link #doroutine} on the Rgroup</li>
 *   <li>Subclass {@code doroutine} executes command logic</li>
 * </ol>
 *
 * <h2>Dynamic Loading</h2>
 * <p>{@link #loadgroup} supports loading command groups from external classes
 * at runtime, enabling plugin-style extensibility.</p>
 *
 * @see KeyBinding
 * @see MapEvent
 * @see EditGroup
 * @see MoveGroup
 */
public abstract class Rgroup {

   /**
    * Functional interface for command execution.
    * Commands receive count, repeat-count, view context,
    * and whether this is a dot-repeat invocation.
    */
   @FunctionalInterface
   interface CommandHandler {
      Object execute(int count, int rcount, FvContext fvc,
         boolean dotMode)
         throws IOException, InterruptedException, InputException;
   }

   /**
    * Functional interface for arg-dependent command execution.
    * Like CommandHandler but also receives the command argument
    * from the key mapping (e.g., ":vt hostname" passes "hostname").
    */
   @FunctionalInterface
   public interface ArgCommandHandler {
      Object execute(Object arg, int count, int rcount, FvContext fvc,
         boolean dotMode)
         throws IOException, InterruptedException, InputException;
   }

   /**
    * Self-describing command entry with metadata and handler.
    * Used by Phase 2b keymap-to-help auto-extraction.
    */
   record CommandEntry(
      String name,
      String description,
      String category,
      CommandHandler handler
   ) { }

   final class KeyBinding {
      private final Object arg;
      private final int index;
      private final CommandHandler handler;
      private final ArgCommandHandler argHandler;

      KeyBinding(Object argi, int indexi) {
         arg = argi;
         index = indexi;
         handler = null;
         argHandler = null;
      }

      KeyBinding(CommandHandler handleri) {
         arg = null;
         index = -1;
         handler = handleri;
         argHandler = null;
      }

      KeyBinding(ArgCommandHandler handleri) {
         arg = null;
         index = -1;
         handler = null;
         argHandler = handleri;
      }

      private KeyBinding(Object argi, ArgCommandHandler handleri) {
         arg = argi;
         index = -1;
         handler = null;
         argHandler = handleri;
      }

      public String toString() {
         return Rgroup.this + "|" + arg + "|" + index;
      }

      Object dobind(int count,
         int rcount, FvContext fvc, boolean dotmode) throws
            IOException, InterruptedException, InputException {
         if (handler != null)
            return handler.execute(count, rcount, fvc, dotmode);
         if (argHandler != null)
            return argHandler.execute(arg, count, rcount, fvc,
               dotmode);
         return doroutine(index, arg, count, rcount, fvc, dotmode);
      }

      Object dobind(Object arg2, int count,
         int rcount, FvContext fvc, boolean dotmode) throws
            IOException, InterruptedException, InputException {
         if (handler != null)
            return handler.execute(count, rcount, fvc, dotmode);
         Object resolved = null == arg2 ? arg : arg2;
         if (argHandler != null)
            return argHandler.execute(resolved, count, rcount, fvc,
               dotmode);
         return doroutine(index, resolved, count, rcount, fvc,
            dotmode);
      }

      boolean matches(Rgroup rg) {
         return rg == Rgroup.this;
      }

      Object getArg() {
         return arg;
      }

      KeyBinding proto(Object arg2) {
         if (arg == arg2)
            return this;
         if (argHandler != null)
            return new KeyBinding(arg2, argHandler);
         return new KeyBinding(arg2, index);
      }
   }

   private static HashMap<String, KeyBinding> cmhash =
      new HashMap<>(200);
   private static HashMap<String, String> descHash =
      new HashMap<>(200);
   private static HashMap<String, Integer> commandCounts =
      new HashMap<>(200);
   private HashMap<String, Object> glist = new HashMap<>(100);

   static final KeyBinding bindingLookup(String name) {
      //trace("bindingLookup " + name + " ret " + cmhash.get(name));
      return cmhash.get(name);
   }

   /**
    * Get all registered command names.
    *
    * @return unmodifiable set of command names
    */
   public static java.util.Set<String> getRegisteredCommands() {
      return java.util.Collections.unmodifiableSet(cmhash.keySet());
   }

   /**
    * Get execution counts for all commands invoked so far.
    *
    * @return unmodifiable map of command name to execution count
    */
   public static Map<String, Integer> getCommandCounts() {
      return java.util.Collections.unmodifiableMap(commandCounts);
   }

   protected abstract Object doroutine(int rnum, Object arg, int count,
      int rcount, FvContext fvc, boolean dotmode) throws
      IOException, InterruptedException, InputException;

   static final Object doCommand(String command, Object arg, int count,
         int rcount, FvContext fvc, boolean dotmode) throws
         InterruptedException, InputException, IOException {
      KeyBinding cm = cmhash.get(command);
      if (null == cm)
         throw new InputException("unkown command:" + command);

      commandCounts.merge(command, 1, Integer::sum);

      if (null == arg)
         arg = cm.arg;

      return cm.dobind(arg, count, rcount, fvc, dotmode);
   }

   public final void register(String[] commands) {
      for (int ii = 1; ii < commands.length; ii++) {
         //trace("registering " + commands[ii]);
         if (commands[ii] == null)
            continue;
         if (cmhash.containsKey(commands[ii]))
            throw new RuntimeException("duplicate command:" + commands[ii]);
         else
            cmhash.put(commands[ii], new KeyBinding(null, ii));

      }
   }

   /**
    * Register commands with descriptions. Each command name at
    * index i is paired with the description at index i.
    * Descriptions make the command self-documenting — the help
    * system extracts them automatically from the active keymap.
    */
   public final void register(String[] commands, String[] descs) {
      register(commands);
      for (int ii = 1; ii < commands.length && ii < descs.length; ii++)
         if (commands[ii] != null && descs[ii] != null)
            descHash.put(commands[ii], descs[ii]);
   }

   /**
    * Get the registered description for a command name.
    *
    * @param commandName the internal command name
    * @return human-readable description, or null if none
    */
   static String getDescription(String commandName) {
      return descHash.get(commandName);
   }

   /**
    * Pre-register descriptions for commands that will be
    * registered later by a subsystem not yet initialized.
    * Safe to call before or after command registration.
    */
   static void registerDescriptions(
         String[] commands, String[] descs) {
      for (int ii = 1; ii < commands.length
            && ii < descs.length; ii++)
         if (descs[ii] != null)
            descHash.put(commands[ii], descs[ii]);
   }

   private static HashMap<String, CommandEntry> entryHash =
      new HashMap<>(200);

   /**
    * Register a self-describing command with closure handler.
    * Phase 2b: commands registered this way carry their own
    * metadata for help auto-extraction. The handler replaces
    * the doroutine dispatch for this command.
    */
   public final void registerCommand(CommandEntry entry) {
      //trace("registering command " , entry);
      if (cmhash.containsKey(entry.name()))
         throw new RuntimeException(
            "duplicate command:" + entry.name());
      cmhash.put(entry.name(), new KeyBinding(entry.handler()));
      entryHash.put(entry.name(), entry);
      descHash.put(entry.name(), entry.description());
   }

   /**
    * Register an arg-dependent command with closure handler.
    * The handler receives the resolved argument from the key
    * mapping (e.g., ":vt hostname" passes "hostname" as arg).
    */
   public final void registerArgCommand(String name, String desc,
         String category, ArgCommandHandler handler) {
      //trace("registering command " , name);
      if (cmhash.containsKey(name))
         throw new RuntimeException("duplicate command:" + name);
      cmhash.put(name, new KeyBinding(handler));
      entryHash.put(name, new CommandEntry(name, desc, category,
         null));
      descHash.put(name, desc);
   }

   /**
    * Get CommandEntry for a registered command, or null.
    */
   static CommandEntry getCommandEntry(String name) {
      return entryHash.get(name);
   }

/*
   public final void register(String command, int index) {
      trace("register");
      if (cmhash.containsKey(command))
         throw new RuntimeException("duplicate command:" + command);
      else
         cmhash.put(command, new KeyBinding(this, null, index));
   }
*/

   public final void unregister()  {
      trace("unregister " + this);
      for (Iterator<Map.Entry<String, KeyBinding>> eve =
            cmhash.entrySet().iterator(); eve.hasNext();) {
         Map.Entry<String, KeyBinding> me = eve.next();
         trace("examine unregistering cmd " + me.getKey() + " rg "
            + me.getValue());
         if (me.getValue().matches(this)) {
            trace("unregistering cmd " + me.getKey());
            eve.remove();
         }
      }
   }

   final void loadgroup(String realfile, String lclass) {
      trace("loadgroup file = " + realfile + " lclass = " + lclass);
      if (glist.containsKey(realfile)) {
         ((Rgroup) glist.get(realfile)).unregister();
         glist.remove(realfile);
      }
      try {
         Class<?> nclass = Class.forName(lclass);
         glist.put(realfile, nclass.getDeclaredConstructor().newInstance());
      } catch (IllegalAccessException e) {
         throw new RuntimeException("vigroup ", e);
      } catch (InstantiationException e) {
         throw new RuntimeException("vigroup ", e);
      } catch (ClassNotFoundException e) {
         throw new RuntimeException("vigroup ", e);
      } catch (NoSuchMethodException e) {
         throw new RuntimeException("vigroup ", e);
      } catch (java.lang.reflect.InvocationTargetException e) {
         throw new RuntimeException("vigroup ", e);
      }
   }

   public static final Float oBToFloat(Object str) throws InputException {
      if (null == str)
         throw new InputException("command needs float number");
      try {
         return Float.valueOf(str.toString().trim());
      } catch (NumberFormatException e) {
         throw new InputException("command needs float number", e);
      }
   }

   public static final int oBToInt(Object str) throws InputException {
      if (null == str)
         throw new InputException("command needs decimal number");
      try {
         return Integer.parseInt(str.toString().trim());
      } catch (NumberFormatException e) {
         throw new InputException("command needs decimal number", e);
      }
   }

}
