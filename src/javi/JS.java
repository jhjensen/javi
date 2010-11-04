package javi;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.ImporterTopLevel;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

final class RealJs {
   private RealJs() { }
   static final Class oclass = JS.class;
   static Context ctx;
   static ScriptableObject scope;

   static void jsClear() {
      if (ctx != null)
         Context.exit();
      ctx = Context.enter();
      ImporterTopLevel iscope = new ImporterTopLevel();
      //   scope = new ImporterTopLevel();
      iscope.initStandardObjects(ctx, false);
      //ScriptableObject scope = ctx.initStandardObjects(new ImporterTopLevel());
      scope = iscope;
      Context.enter();
      scope.put("jsobj", scope, new JSObj());
   }

   static {
      jsClear();
   }

}

class JSObj {
   public void myprint(String s)  {
      //trace("jsoutput s " + s + " jsoutput " +  jsoutput);
      JS.jsoutput.insertOne(s, JS.jsoutput.finish());
   }
}

/* Copyright 1996 James Jensen all rights reserved */
public class JS extends Rgroup {
   JS() {
      final String[] rnames = {
         "",
         "jseval",
         "jsevalfile",
         "jsclear",
      };

      register(rnames);
   }

   static final StringIoc sio = new StringIoc("jsoutput", "start");
   static final TextEdit<String> jsoutput = new TextEdit(sio, sio.prop);

   public static class JSgroup extends Rgroup {
      private final Function func;

      public JSgroup(String funcname) throws Exception {
         Object fobj =  RealJs.scope.get(funcname, RealJs.scope);
         if (fobj == null || !(fobj instanceof Function))
            throw new Exception("illegal function name " + funcname);
         else
            func = (Function) fobj;
      }

      public JSgroup(Function funci) throws Exception {
         func = funci;
      }

      public final Object doroutine(int rnum, Object arg, int count,
            int rcount, FvContext fvc , boolean dotmode) throws
            IOException {
         Object[] fargs = {rnum, arg, count, rcount, fvc, dotmode};
         return func.call(RealJs.ctx, RealJs.scope, RealJs.scope, fargs);
      }

   }

   public static JSgroup gfac(String str) throws Exception {
      return new JSgroup(str);
   }

   public final Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc , boolean dotmode) throws IOException {
      //trace("rnum = " + rnum + " count = " + count + " rcount = " + rcount);

      switch (rnum) {
         case 1:
            return execRoutine(rnum, arg, count, rcount, fvc, dotmode);
         case 2:
            try {
               jsEvalIter(fvc.edvec.iterator(), fvc.edvec.getName());
            } catch (org.mozilla.javascript.EcmaError e) {
               UI.reportError(e.toString());
            } catch (org.mozilla.javascript.EvaluatorException e) {
               UI.reportError(e.toString());
            }
            return null;
         case 3:
            RealJs.jsClear();
            return null;
         default:
            throw new RuntimeException();
      }
   }

   private static Object execRoutine(int rnum, Object arg, int count,
         int rcount, FvContext fvc , boolean dotmode) {
      try {
         if (arg != null) {
            Object jresult = RealJs.ctx.evaluateString(
                                RealJs.scope, arg.toString(), "cmd", 1, null);
            Object result = Context.jsToJava(jresult , RealJs.oclass);
            return result;
         }
      } catch (org.mozilla.javascript.EcmaError e) {
         UI.reportError(e.toString());
      } catch (org.mozilla.javascript.EvaluatorException e) {
         UI.reportError(e.toString());
      }
      return null;
   }

   static String jsEvalIter(Iterator src, String lable) throws IOException {
      Object eres = RealJs.ctx.evaluateReader(
                       RealJs.scope, new IteratorReader(src), lable, 1, null);
      return eres.toString();
   }

   static String jsEvalString(String src) throws IOException {
      Object eres =
         RealJs.ctx.evaluateString(RealJs.scope, src, "cmd", 1, null);
      // trace("" + eres);
      return eres.toString();

   }

   static boolean myassert(boolean flag, Object dump) {
      if (!flag)
         throw new RuntimeException(" ASSERTION FAILURE " + dump.toString());
      return flag;
   }

   public static void main(String[] args) {
      try {

         new StreamInterface();
         Object obj =  execRoutine(5, "ok ", 7 , 8 , null , false);
         myassert("ok".equals(obj), obj);

         myassert(jsEvalString(
                     "function f(x){return x+1} f(7)").equals("8.0"), "8.0");
         String[] strarr = {
            "java.lang.System.out.println(300000); ",
            "function f(x){return x+1} ",
            "f(9);"
         };
         myassert(jsEvalIter(Arrays.asList(strarr).iterator(),
            "test").equals("10.0"), "10.0");
         trace("test executed successfully");
      } catch (Throwable e) {
         trace("main caught exception " + e);
         e.printStackTrace();
      }
      System.exit(0);

   }
}

final class IteratorReader extends java.io.Reader {
   private Iterator it;
   private String str;
   private int pos = 0;

   IteratorReader(Iterator iti) {
      it = iti;
      str = it.next().toString();
   }

   public void close() {
      it = null;
      str = null;
   }

   public int read() {
      //Tools.trace("");
      try {
         char ch = getChar();
         Tools.trace("returning " + ch);
         return ch;
      } catch (NoSuchElementException e) {
         return -1;
      }
   }

   public int read(char [] cbuf, int off, int len) {
      //Tools.trace("off " +off + " len " + len);
      int index = 0;
      try {
         for (; index < len; index++)
            cbuf[off + index] = getChar();
         //Tools.trace("index " + index + " read in cbuf " + cbuf);
         return index;
      } catch (NoSuchElementException e) {
         //Tools.trace("index " + index + " read in cbuf " + String.copyValueOf(cbuf,off,index));
         if (index < 1)
            return -1;
         else  {
            cbuf[off + index] = '\n';
            return index + 1;
         }
      }
   }

   char getChar() {
      //Tools.trace("pos " + pos + ":" + str);
      while (pos >= str.length()) {
         str = it.next().toString();
         pos = 0;
         return '\n';
      }
      return str.charAt(pos++);
   }

}
