package javi;

import java.util.HashMap;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
/**
 * JarResources: JarResources maps all resources included in a
 * Zip or Jar file. Additionaly, it provides a method to extract one
 * as a blob.
 */
final class JarResources {

   // jar resource mapping tables
   private HashMap<String, Integer> htSizes = new HashMap<String, Integer>();
   private HashMap<String, byte[]> htJarContents =
      new HashMap<String, byte[]>();

   static void trace(String str) {
      Tools.trace(str, 1);
   }
   // a jar file
   private String jarFileName;

   /**
     * creates a JarResources. It extracts all resources from a Jar
     * into an internal hashtable, keyed by resource names.
     * @param jarFileName a jar or zip file
     */
   public JarResources(String jarFileNamei)  throws IOException {
      jarFileName = jarFileNamei;
      init();
   }

   /**
     * Extracts a jar resource as a blob.
     * @param name a resource name.
     */
   public byte[] getResource(String name) {
      return htJarContents.get(name);
   }

   /** initializes internal hash tables with Jar file resources.  */
   private void init() throws IOException {
      ZipFile zf = new ZipFile(jarFileName);
      try {
         // extracts just sizes only.
         Enumeration e = zf.entries();
         while (e.hasMoreElements()) {
            ZipEntry ze = (ZipEntry) e.nextElement();

            //trace(dumpZipEntry(ze));

            htSizes.put(ze.getName(), Integer.valueOf((int) ze.getSize()));
         }
         zf.close();

         // extract resources and put them into the hashtable.
         ZipInputStream zis = new ZipInputStream(new BufferedInputStream(
            new FileInputStream(jarFileName)));

         try {
            ZipEntry ze = null;
            while ((ze = zis.getNextEntry()) != null) {

               if (ze.isDirectory())
                  continue;

               //trace("ze.getName()="+ze.getName()+ ","+"getSize()="+ze.getSize() );

               int size = (int) ze.getSize();
               // -1 means unknown size.
               if (size == -1)
                  size = htSizes.get(ze.getName()).intValue();

               byte[] b = new byte[size];
               int rb = 0;
               int chunk = 0;
               while ((size - rb) > 0) {
                  chunk = zis.read(b, rb, size - rb);
                  if (chunk == -1)
                     break;
                  rb += chunk;
               }
               htJarContents.put(ze.getName(), b);
            }

         } finally {
            zis.close();
         }
         //trace( ze.getName()+"  rb="+rb+ ",size="+size+ ",csize="+ze.getCompressedSize() );
      } finally {
         zf.close();
      }
   }

   /**
     * Dumps a zip entry into a string.
     * @param ze a ZipEntry
     */
   private String dumpZipEntry(ZipEntry ze) {
      StringBuilder sb = new StringBuilder(ze.isDirectory() ? "d " : "f ");

      sb.append(ze.getMethod() == ZipEntry.STORED ? "stored   " : "defalted ");

      sb.append(ze.getName());
      sb.append('\t');
      sb.append(ze.getSize());
      if (ze.getMethod() == ZipEntry.DEFLATED)
         sb.append("/" + ze.getCompressedSize());

      return (sb.toString());
   }

   /**
     * Is a test driver. Given a jar file and a resource name, it trys to
     * extract the resource and then tells us whether it could or not.
     *
     * <strong>Example</strong>
     * Let's say you have a JAR file which jarred up a bunch of gif image
     * files. Now, by using JarResources, you could extract, create, and
     * display those images on-the-fly.
     * <pre>
     *     ...
     *     JarResources JR =new JarResources("GifBundle.jar");
     *     Image image=Toolkit.createImage(JR.getResource("logo.gif");
     *     Image logo=Toolkit.getDefaultToolkit().createImage(
     *                   JR.getResources("logo.gif")
     *                   );
     *     ...
     * </pre>
     */

   public static void main(String[] args) throws IOException {
      if (args.length != 2) {
         trace("usage: java JarResources <jar file name> <resource name>");
         return;
      }

      JarResources jr = new JarResources(args[0]);
      byte[] buff = jr.getResource(args[1]);

      if (buff == null)
         trace("Could not find " + args[1] + ".");
      else
         trace("Found " + args[1] + " (length=" + buff.length + ").");
   }
}

abstract class MultiClassLoader extends ClassLoader {

   private HashMap<String, Class> classes = new HashMap<String, Class>();
   private char      classNameReplacementChar;

   public MultiClassLoader() {
   }
//---------- Superclass Overrides ------------------------
   /**
    * This is a simple version for external clients since they
    * will always want the class resolved before it is returned
    * to them.
    */
   public Class loadClass(String className) throws ClassNotFoundException {
      return (loadClass(className, true));
   }
   static void trace(String str) {
      Tools.trace(str, 1);
   }
//---------- Abstract Implementation ---------------------
   public synchronized Class loadClass(String className,
         boolean resolveIt) throws ClassNotFoundException {

      byte[]  classBytes;
      //trace(">> MultiClassLoader.loadClass(" + className + ", " + resolveIt + ")");

      //----- Check our local cache of classes
      Class   result = classes.get(className);
      if (result != null) {
         trace(">> returning cached result.");
         return result;
      }

      //----- Check with the primordial class loader
      try {
         return super.findSystemClass(className);
         //trace(">> returning system class (in CLASSPATH).");
      } catch (ClassNotFoundException e) {
         trace(">> Not a system class.");
      }

      //----- Try to load it from preferred source
      // Note loadClassBytes() is an abstract method
      classBytes = loadClassBytes(className);
      if (classBytes == null) {
         throw new ClassNotFoundException();
      }

      //----- Define it (parse the class file)
      result = defineClass(className, classBytes, 0, classBytes.length);
      if (result == null) {
         throw new ClassFormatError();
      }

      //----- Resolve if necessary
      if (resolveIt) resolveClass(result);

      // Done
      classes.put(className, result);
      trace(">> Returning newly loaded class.");
      return result;
   }
//---------- Public Methods ------------------------------
   /**
    * This optional call allows a class name such as
    * "COM.test.Hello" to be changed to "COM_test_Hello",
    * which is useful for storing classes from different
    * packages in the same retrival directory.
    * In the above example the char would be '_'.
    */
   public void setClassNameReplacementChar(char replacement) {
      classNameReplacementChar = replacement;
   }
//---------- Protected Methods ---------------------------
   protected abstract byte[] loadClassBytes(String className);

   protected String formatClassName(String className) {
      if (classNameReplacementChar == '\u0000') {
         // '/' is used to map the package to the path
         return className.replace('.', '/') + ".class";
      } else {
         // Replace '.' with custom char, such as '_'
         return className.replace('.',
                                  classNameReplacementChar) + ".class";
      }
   }

} // End class

public interface Plugin {
   public static class Loader {
      static void load(final String jarFile)
         throws IOException, ClassNotFoundException,
         NoSuchFieldException, IllegalAccessException {

         java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction()  {
            public Object run() {
               try {
                  final JarLoader jarLoader = new JarLoader(jarFile);


                  /* Load the class from the jar file and resolve it. */
                  Class c = jarLoader.loadClass("javi.plugin.FindBugs", true);
                  //trace("class loaded");
                  if (Plugin.class.isAssignableFrom(c)) {
                     // Yep, lets call a method  we know about.  */
                     //java.lang.reflect.Method m = c.getDeclaredMethod("init");
                     //m.invoke(c);

                     //c.toString();

                     java.lang.reflect.Field m =
                        c.getDeclaredField("pluginInfo");
                     trace("plugin info " + m.get(null));
                  } else {
                     trace("unable to run class " + c);
                  }
               } catch (Throwable e) {
                  UI.popError("unable to load plugin " , e);
               }
               return null;
            }
         });
      }
      static void trace(String str) {
         Tools.trace(str, 1);
      }
   }   // End of nested Class Test.
}

class JarLoader extends MultiClassLoader {
   private JarResources    jarResources;
   public JarLoader(String jarName) throws IOException {
      // Create the JarResource and suck in the jar file.
      jarResources = new JarResources(jarName);
   }
   protected byte[] loadClassBytes(String className) {

      className = formatClassName(className);
      // Attempt to get the class data from the JarResource.
      return (jarResources.getResource(className));
   }

}
