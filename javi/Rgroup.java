package javi;

/* Copyright 1996 James Jensen all rights reserved */

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Enumeration;
import java.io.IOException;


public abstract class Rgroup {

private static HashMap<String,KeyBinding> cmhash= new HashMap<String,KeyBinding>(100);
private HashMap<String,Object> glist= new HashMap<String,Object>();

private static final String copyright = "Copyright 1996 James Jensen";

static KeyBinding bindingLookup(String name) {
   //trace("bindingLookup " + name + " ret " + cmhash.get(name));
   return cmhash.get(name);
}

abstract public Object doroutine(int rnum,Object arg,int count,int rcount,
     FvContext fvc , boolean dotmode) 
 throws IOException,InterruptedException,InputException;

static Object doroutine(String command,Object arg,int count,int rcount,
       FvContext fvc, boolean dotmode)
    throws InterruptedException,InputException,IOException,InputException {
   KeyBinding cm = cmhash.get(command);
   if (cm == null )
	     throw new InputException("unkown command:" + command);
   
   if (arg==null)
      arg=cm.arg;
   
   return cm.rg.doroutine(cm.index,arg,count,rcount,fvc, dotmode) ;
}

public void register(String[] commands) {
   for (int i=1;i<commands.length;i++) {
     //trace("registering " + commands[i]);
     if (cmhash.containsKey(commands[i])) 
        throw new RuntimeException("duplicate command:" + commands[i]);
     else
        cmhash.put(commands[i],new KeyBinding(this,null,i));

   }
}
public void register(String command,int index) {
trace("register");
     if (cmhash.containsKey(command))
        throw new RuntimeException("duplicate command:" + command);
     else
        cmhash.put(command,new KeyBinding(this,null,index));
}


public void unregister()  {
   trace("unregister " + this);
   for (Iterator<Map.Entry<String,KeyBinding>> eve = cmhash.entrySet().iterator();eve.hasNext();) {
      Map.Entry<String,KeyBinding> me = eve.next();
      trace("examine unregistering cmd " + me.getKey() + " rg " + me.getValue().rg);
      if (me.getValue().rg==this) {
        trace("unregistering cmd " + me.getKey());
        eve.remove();
      }
   }
}

void loadgroup(String realfile,String lclass) {
trace("loadgroup file = " + realfile + " lclass = " + lclass);
   if (glist.containsKey(realfile)) {
      ((Rgroup)glist.get(realfile)).unregister();
      glist.remove(realfile);
   }
  try {
      Class nclass=Class.forName(lclass);
      glist.put(realfile,nclass.newInstance());
  } catch (IllegalAccessException e) {
      throw new RuntimeException("vigroup caught IllegalAccessException ",e);
  } catch (InstantiationException e) {
      throw new RuntimeException("vigroup caught InstantiationException",e);
  } catch (ClassNotFoundException e) {
      throw new RuntimeException("vigroup caught ClassNotFoundException",e);
  }
}

public static void trace(String str) {
   Tools.trace(str,1);
}
}
