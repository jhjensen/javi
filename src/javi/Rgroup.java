package javi;

/* Copyright 1996 James Jensen all rights reserved */

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import static history.Tools.trace;

public abstract class Rgroup {

   final class KeyBinding {
      private final Object arg;
      private final int index;

      KeyBinding(Object argi, int indexi) {
         arg = argi;
         index = indexi;
      }

      public String toString() {
         return Rgroup.this + "|" + arg + "|" + index;
      }

      Object dobind(int count,
         int rcount, FvContext fvc, boolean dotmode) throws
            IOException, InterruptedException, InputException {
         return doroutine(index, arg, count, rcount, fvc, dotmode);
      }

      Object dobind(Object arg2, int count,
         int rcount, FvContext fvc, boolean dotmode) throws
            IOException, InterruptedException, InputException {

         return doroutine(index, null == arg2
               ? arg
               : arg2
            , count, rcount, fvc, dotmode);
      }

      boolean matches(Rgroup rg) {
         return rg == Rgroup.this;
      }

      KeyBinding proto(Object arg2) {
         return (arg != arg2) // use default bind if arguments the same
            ? new KeyBinding(arg2, index)
            : this;

      }
   }

   private static HashMap<String, KeyBinding> cmhash =
      new HashMap<String, KeyBinding>(200);
   private HashMap<String, Object> glist = new HashMap<String, Object>(100);

   static final KeyBinding bindingLookup(String name) {
      //trace("bindingLookup " + name + " ret " + cmhash.get(name));
      return cmhash.get(name);
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

      if (null == arg)
         arg = cm.arg;

      return cm.dobind(arg, count, rcount, fvc, dotmode);
   }

   public final void register(String[] commands) {
      for (int ii = 1; ii < commands.length; ii++) {
         //trace("registering " + commands[ii]);
         if (cmhash.containsKey(commands[ii]))
            throw new RuntimeException("duplicate command:" + commands[ii]);
         else
            cmhash.put(commands[ii], new KeyBinding(null, ii));

      }
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
         Class nclass = Class.forName(lclass);
         glist.put(realfile, nclass.newInstance());
      } catch (IllegalAccessException e) {
         throw new RuntimeException("vigroup ", e);
      } catch (InstantiationException e) {
         throw new RuntimeException("vigroup ", e);
      } catch (ClassNotFoundException e) {
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
