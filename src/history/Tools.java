package history;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Tools {
   private static long lastTime = System.nanoTime();

   private Tools() { }

   public static void trace(String str, int offset) {
      try {

         throw new Exception("");
      } catch (Exception e) {

         long newTime = System.nanoTime();
         long timediff = newTime - lastTime;
         lastTime = newTime;
         StackTraceElement[] tr = e.getStackTrace();
         StackTraceElement el = tr[1 + offset];
         if (0 == str.length())
            str = el.getMethodName() + "." + el.getClassName();
         System.err.println(timediff / 1000000 + " "
            + el.getFileName() + ":"
            + el.getLineNumber() + " " + str);
         //System.err.println(tr[1+offset].getFileName() + ":" + tr[1+offset].getLineNumber() + " " + str);
      }
   }

   public static void trace(String str) {
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

   public static void Assert(boolean flag, Object dump) {
      if (!flag)
         throw new RuntimeException(" ASSERTION FAILURE " + dump.toString());
   }

   private static final ProcessBuilder pb = new ProcessBuilder();

   public static ArrayList<String> execute(
         String ... command) throws IOException {
      Process proc = iocmd(command);
      BufferedReader in =  new BufferedReader(
         new InputStreamReader(proc.getInputStream()));
      ArrayList<String> output = new ArrayList<String>();
      try {
         for (String str; null != (str = in.readLine());)
            output.add(str);
         proc.waitFor();
      } catch (InterruptedException e) {
         trace("!!!! interupted executing "
            + java.util.Arrays.toString(command));
      } finally {
         in.close();
      }
      return output;
   }

   public static BufferedReader runcmd(List<String>  str) throws IOException {
      return new BufferedReader(
         new InputStreamReader(iocmd(str).getInputStream(), "UTF-8"));
   }

   public static BufferedReader runcmd(String ... str) throws IOException {
      return new BufferedReader(
         new InputStreamReader(iocmd(str).getInputStream(), "UTF-8"));
   }

   public static synchronized Process iocmd(List<String>  str) throws IOException {
      return pb.redirectErrorStream(true).command(str).start();
   }

   public static synchronized Process iocmd(String ...  str) throws IOException {
      return pb.redirectErrorStream(true).command(str).start();
   }

   public static void doGC() {
      for (int ii = 0; ii  < 3; ii++) {
         System.gc();
         System.runFinalization();
      }
   }
}
