package javi;

/* Copyright 1996 James Jensen all rights reserved */

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.io.IOException;
import static history.Tools.trace;

public abstract class Rgroup {

   final class KeyBinding {
   /* Copyright 1996 James Jensen all rights reserved */
      static final String copyright = "Copyright 1996 James Jensen";
      private final Object arg;
      private final int index;
      KeyBinding(Object argi , int indexi) {
         arg = argi;
         index = indexi;
      }
      public String toString() {
         return (Rgroup.this + "|" + arg + "|" + index);
      }

      Object dobind(int count,
         int rcount, FvContext fvc , boolean dotmode) throws
            IOException, InterruptedException, InputException {
         return doroutine(index, arg, count, rcount, fvc, dotmode);
      }

      Object dobind(Object arg2, int count,
         int rcount, FvContext fvc , boolean dotmode) throws
            IOException, InterruptedException, InputException {

         return doroutine(index, arg2 == null
               ? arg
               : arg2
            , count, rcount, fvc, dotmode);
      }


      boolean match(Rgroup rg) {
         return rg == Rgroup.this;
      }

      KeyBinding proto(Object arg2) {
         return (arg != arg2) // use default bind if arguments the same
            ? new KeyBinding(arg2, index)
            : this;

      }
   }

   private static HashMap<String, KeyBinding> cmhash =
      new HashMap<String, KeyBinding>(100);
   private HashMap<String, Object> glist = new HashMap<String, Object>();

   private static final String copyright = "Copyright 1996 James Jensen";

   static KeyBinding bindingLookup(String name) {
      //trace("bindingLookup " + name + " ret " + cmhash.get(name));
      return cmhash.get(name);
   }

   public abstract Object doroutine(int rnum, Object arg, int count,
      int rcount, FvContext fvc , boolean dotmode) throws
      IOException, InterruptedException, InputException;

   static Object doroutine(String command, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws
         InterruptedException, InputException, IOException {
      KeyBinding cm = cmhash.get(command);
      if (cm == null)
         throw new InputException("unkown command:" + command);

      if (arg == null)
         arg = cm.arg;

      return cm.dobind(arg, count, rcount, fvc, dotmode);
   }

   public final void register(String[] commands) {
      for (int i = 1; i < commands.length; i++) {
         //trace("registering " + commands[i]);
         if (cmhash.containsKey(commands[i]))
            throw new RuntimeException("duplicate command:" + commands[i]);
         else
            cmhash.put(commands[i], new KeyBinding(null, i));

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
         if (me.getValue().match(this)) {
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

   public final Float oBToFloat(Object str) throws InputException {
      if (str == null)
         throw new InputException("command needs decimal number");
      try {
         return Float.valueOf(str.toString().trim());
      } catch (NumberFormatException e) {
         throw new InputException("command needs decimal number", e);
      }
   }

   public final int oBToInt(Object str) throws InputException {
      if (str == null)
         throw new InputException("command needs decimal number");
      try {
         return Integer.parseInt(str.toString().trim());
      } catch (NumberFormatException e) {
         throw new InputException("command needs decimal number", e);
      }
   }

}
