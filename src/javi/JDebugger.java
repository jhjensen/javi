package javi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Vector;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;

class JDebugger extends IoConverter<String> {

   private static final long serialVersionUID = 1;
   public String getnext() {
      return null;
      //throw new RuntimeException(" JDebugger does not implement getnext");
   }
   /*
   class debugcom extends Rgroup {
      private static final String[] rnames = {
        "",
        "vdump",
      };

      debugcom() {
         register(rnames);
      }
      Object doroutine(int rnum,Object arg,int count,int rcount,fvcontext fvc,
        eventqueue eventq,boolean dotmode) {
         //trace("rnum = " + rnum);

         switch (rnum) {
            case 1:
               showthreads(); return null; //??? doesn't work
         }
         return null;
      }
   }
   void dumpFrame(StringBuilder sb,StackFrame stf,ThreadReference thr) {
      Location loc = stf.location();
      //trace("loc = " + loc);
      sb.setLength(0);
      try {
         sb.append("   ");
         sb.append(loc.method().toString());
         sb.append("   ");
         sb.append(loc.sourceName());
         sb.append(":");
         sb.append(loc.lineNumber());
         sb.append(' ');
         Iterator varit = stf.visibleVariables().iterator();
         while ( varit.hasNext())  {
            LocalVariable lov = (LocalVariable)varit.next();
            String name = lov.name();
            sb.append(name);
            sb.append('=');
            sb.append(stf.getValue(lov));
            sb.append(',');
         }
   //      ObjectReference tobj =  stf.thisObject();
   //      if (tobj!=null) {
   //         Iterator mit = tobj.referenceType().methodsByName("toString").iterator();
   //         if (mit.hasNext()) {
   //            sb.append("this=");
   //            Method method = (Method) mit.next(); // should always have toString
   //            Object[] args = {};
   //            sb.append(tobj.invokeMethod(thr, method,Arrays.asList(args) , ObjectReference.INVOKE_SINGLE_THREADED).toString());
   //            thr.suspend();
   //         } else
   //            sb.append(tobj + "curiously has no toString");
   //      } else
   //        sb.append("no associated object");

      } catch (AbsentInformationException e) {
   //   } catch (InvalidTypeException e) {
   //   } catch (ClassNotLoadedException e) {
   //   } catch (InvocationException e) {
   //   } catch (IncompatibleThreadStateException e) {
      }
         inarray.add(sb.toString());
   }

   void showthreads() {
     vm.suspend();
     Iterator titer = vm.allThreads().iterator();
     vm.suspend();
     StringBuilder sb = new StringBuilder(200);

     while ( titer.hasNext())  {
        ThreadReference thr = (ThreadReference)titer.next();
        try {
           //Iterator monit = thr.ownedMonitors().iterator();
           //while ( monit.hasNext())  {
           //   ObjectReference mon = (ObjectReference)monit.next();
           //   inarray.add("   owns " +mon);
           //}

           Iterator stit = thr.frames().iterator();
           inarray.add(thr.name());
           while ( stit.hasNext())
              dumpFrame(sb,(StackFrame)stit.next(),thr);
        } catch (IncompatibleThreadStateException e) {
           e.printStackTrace();
            throw new RuntimeException(
               "JDebugger.showthreads got unexpected exception");
        }

     }

   }

   static debugcom comd;
   */
   private transient VirtualMachine vm;
   private transient Vector<String> inarray = new Vector<String>();

   public void dispose() throws IOException {
      super.dispose();
      //trace("Jdebugger disposed");
      //Thread.dumpStack();
      inarray = null;
      vm = null;
   }

   JDebugger(String cname) throws
      IOException , IllegalConnectorArgumentsException, VMStartException {
      super(new FileProperties(FileDescriptor.InternalFd.make(
         "JDebugger" + cname), StringIoc.converter), true);
      LaunchingConnector con =
         com.sun.jdi.Bootstrap.virtualMachineManager().defaultConnector();
//   if (comd == null)
//      comd = new debugcom();

      Map<String, com.sun.jdi.connect.Connector.Argument> cargs =
         con.defaultArguments();
      //for (Iterator cx = cargs.values().iterator() ; cx.hasNext() ;)  {
      //   Connector.Argument ar = (Connector.Argument)cx.next();
      //trace(ar + ar.description());
      //}
      cargs.get("main").setValue(cname);
//   ((Connector.Argument)cargs.get("options")).setValue("-Xbootclasspath/a:d:/j2sdk1.4.1/lib/tools.jar -verbose:class");
//   cargs.get("options").setValue("-Xbootclasspath/a:d:/j2sdk1.4.1/lib/tools.jar");
//   cargs.get("options").setValue("-Xprof -Xshare:off -Dxxx=yyy");
      cargs.get("options").setValue(
         "-Dsun.io.serialization.extendedDebugInfo=true");

      cargs.get("options").setValue("-Xshare:off -Dxxx=yyy");
      vm = con.launch(cargs);
      //trace("classes = " + vm.allClasses());

      vm.resume();

   }

   private class StreamVreader implements Runnable {
      private BufferedReader inStream;
      private boolean running = true;
      StreamVreader(InputStream instr) {
         inStream = new BufferedReader(new InputStreamReader(instr));
         new Thread(this, "jdebugger thread").start();
      }

      public void run() {
         try {
            String str;
            while ((str = inStream.readLine()) != null)
               inarray.add(str);
         } catch (IOException e) {
            /*Ignore Interrupts */
         }
         running = false;

         //trace("JDebugger.run exiting");
      }

      String getnext() {
         return inarray.size() == 0
                ? null
                : inarray.remove(0);
      }

      boolean finished() {
         return !running && inarray.size() == 0;
      }
   }

   void dorun() {

      addElement("running program " + this);
      StreamVreader iinput = new StreamVreader(vm.process().getInputStream());
      StreamVreader einput = new StreamVreader(vm.process().getErrorStream());
      String obj;
      try {
         while (!(iinput.finished() && einput.finished())) {
            while ((obj = einput.getnext()) != null
                  || (obj = iinput.getnext()) != null) {
               addElement(obj);
            }
            Thread.sleep(200);
         }
      } catch (InterruptedException e) { }
   }

   public String fromString(String s) {
      return s;
   }


   public static void main(String[] args) {
      try {
         JDebugger jd = new JDebugger("javi.ehistory");
         new TextEdit<String>(jd, jd.prop);
         //for (Object ob :ev) trace("ev[i]  = " + ev.at(i));

      } catch (IOException e) {
         trace("JDebugger.JDebugger caught " + e);
         e.printStackTrace();
      } catch (com.sun.jdi.connect.VMStartException e) {
         trace("debug.debug caught " + e);
         e.printStackTrace();
      } catch (com.sun.jdi.connect.IllegalConnectorArgumentsException e) {
         trace("JDebugger.JDebugger caught " + e);
         e.printStackTrace();
      }
   }
}
