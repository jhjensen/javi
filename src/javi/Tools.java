package javi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Tools {
   private Tools() { }
   private static long lastTime = System.nanoTime();
   public static void trace(String str, int offset) {
      try {

         throw new Exception("");
      } catch (Exception e) {

         long newTime = System.nanoTime();
         long timediff = newTime - lastTime;
         lastTime = newTime;
         StackTraceElement[] tr = e.getStackTrace();
         System.err.println(timediff / 1000000 + " "
            + tr[1 + offset].getFileName() + ":"
            + tr[1 + offset].getLineNumber() + " " + str);
         //System.err.println(tr[1+offset].getFileName() + ":" + tr[1+offset].getLineNumber() + " " + str);
      }
   }
   static void trace(String str) {
      Tools.trace(str, 1);
   }

   public static String caller() {
      try {
         throw new Exception("");
      } catch (Exception e) {
         StackTraceElement[] tr = e.getStackTrace();
         return tr[2].getMethodName();
      }
   }
   static boolean Assert(boolean flag, Object dump) {
      if (!flag)
         throw new RuntimeException(" ASSERTION FAILURE " + dump.toString());
      return flag;
   }

   private static final ProcessBuilder pb = new ProcessBuilder();

   static ArrayList<String> execute(String ... command) throws IOException {
      Process proc = iocmd(command);
      BufferedReader in =  new BufferedReader(
         new InputStreamReader(proc.getInputStream()));
      ArrayList<String> output = new ArrayList<String>();
      try {
         for (String str = null; null != (str = in.readLine());)
            output.add(str);
         proc.waitFor();
      } catch (InterruptedException e) {
         trace("!!!! interupted executing " + command);
      } finally {
         in.close();
      }
      return output;
   }

   static BufferedReader runcmd(List<String>  str) throws IOException {
      return new BufferedReader(
         new InputStreamReader(iocmd(str).getInputStream(), "UTF-8"));
   }

   static BufferedReader runcmd(String ... str) throws IOException {
      return new BufferedReader(
         new InputStreamReader(iocmd(str).getInputStream(), "UTF-8"));
   }

   static synchronized Process iocmd(List<String>  str) throws IOException {
      pb.redirectErrorStream(true);
      pb.command(str);
      return pb.start();
   }

   static synchronized Process iocmd(String ...  str) throws IOException {
      pb.redirectErrorStream(true);
      pb.command(str);
      return pb.start();
   }

   static void doGC() {
      System.gc();
      System.runFinalization();
      System.gc();
      System.runFinalization();
      System.gc();
      System.runFinalization();
      System.gc();
   }
}
