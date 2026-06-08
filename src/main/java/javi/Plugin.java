package javi;

import java.util.HashMap;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import static history.Tools.trace;

/**
 * JarResources: JarResources maps all resources included in a
 * Zip or Jar file. Additionaly, it provides a method to extract one
 * as a blob.
 */
final class JarResources {

   // jar resource mapping tables
   private HashMap<String, Integer> htSizes = new HashMap<>();
   private HashMap<String, byte[]> htJarContents = new HashMap<>();

   // a jar file
   private String jarFileName;

   /**
     * creates a JarResources. It extracts all resources from a Jar
     * into an internal hashtable, keyed by resource names.
     * @param jarFileName a jar or zip file
     */
   JarResources(String jarFileNamei)  throws IOException {
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
      // First pass: extract just sizes
      try (ZipFile zf = new ZipFile(jarFileName)) {
         Enumeration<?> e = zf.entries();
         while (e.hasMoreElements()) {
            ZipEntry ze = (ZipEntry) e.nextElement();
            htSizes.put(ze.getName(), Integer.valueOf((int) ze.getSize()));
         }
      }

      // Second pass: extract resources and put them into the hashtable
      try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(
            new FileInputStream(jarFileName)))) {
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
         //trace( ze.getName()+"  rb="+rb+ ",size="+size+ ",csize="+ze.getCompressedSize() );
      }
   }

   /**
     * Dumps a zip entry into a string.
     * @param ze a ZipEntry
     */
/*
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
*/

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

   private HashMap<String, Class> classes = new HashMap<>();
   private char      classNameReplacementChar;

   MultiClassLoader() {
      super(MultiClassLoader.class.getClassLoader());
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

      //----- Delegate to parent classloader (app classpath)
      try {
         return getParent().loadClass(className);
      } catch (ClassNotFoundException e) {
         trace(">> Not in parent classloader.");
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

   /**
    * Bind a key to a command in a key group. Plugins call this to
    * register keybindings after their commands are registered.
    *
    * @param group key group name: "move", "edit", or overlay
    *        names like "keymap.move"/"keymap.edit"
    * @param keySpec key specification: single char, "C-x" for
    *        ctrl+char, or special names (F1-F12, Up, Down, etc.)
    * @param command registered command name
    * @throws InputException if keySpec or command is invalid
    */
   static void bindKey(String group, String keySpec, String command)
         throws InputException {
      KeyGroup kg = MapEvent.getKeyGroup(group);
      if (kg == null)
         throw new InputException("unknown keygroup: " + group);
      JeyEvent key = MiscCommands.parseKeySpec(keySpec);
      if (Rgroup.bindingLookup(command) == null)
         throw new InputException("unknown command: " + command);
      kg.bind(key, command, null);
   }

   final class Loader {
      private Loader() { }

      /** Load a plugin JAR. Discovers plugin classes by scanning
        * the JAR manifest for a Plugin-Class attribute, or by
        * finding classes that implement Plugin.
        */
      static void load(final String jarFile) throws
         IOException, ClassNotFoundException,
         NoSuchFieldException, IllegalAccessException {

         try {
            final JarLoader jarLoader = new JarLoader(jarFile);

            // Try manifest Plugin-Class attribute first
            String pluginClassName = null;
            try (java.util.jar.JarFile jf =
                  new java.util.jar.JarFile(jarFile)) {
               java.util.jar.Manifest mf = jf.getManifest();
               if (mf != null) {
                  pluginClassName = mf.getMainAttributes()
                     .getValue("Plugin-Class");
               }
            }

            if (pluginClassName == null) {
               // Fallback: scan for classes implementing Plugin
               pluginClassName = scanForPlugin(jarFile, jarLoader);
            }

            if (pluginClassName == null) {
               trace("no Plugin class found in " + jarFile);
               return;
            }

            Class c = jarLoader.loadClass(pluginClassName, true);
            if (Plugin.class.isAssignableFrom(c)) {
               // Instantiate the plugin - constructor registers
               // commands and keybindings
               @SuppressWarnings("unchecked")
               var ctor = c.getDeclaredConstructor();
               ctor.newInstance();
               trace("loaded plugin: " + pluginClassName);
            } else {
               trace("unable to run class " + c);
            }
         } catch (Throwable e) {
            trace("plugin failed to load " + jarFile +  " "  + e);
         }
      }

      private static String scanForPlugin(String jarFile,
            JarLoader loader) {
         try (java.util.zip.ZipFile zf =
               new java.util.zip.ZipFile(jarFile)) {
            java.util.Enumeration<?> entries = zf.entries();
            while (entries.hasMoreElements()) {
               java.util.zip.ZipEntry ze =
                  (java.util.zip.ZipEntry) entries.nextElement();
               String name = ze.getName();
               if (!name.endsWith(".class") || name.contains("$"))
                  continue;
               String className = name.replace('/', '.')
                  .replace(".class", "");
               try {
                  Class c = loader.loadClass(className, true);
                  if (Plugin.class.isAssignableFrom(c))
                     return className;
               } catch (Throwable ignore) {
                  // skip classes that can't be loaded
               }
            }
         } catch (IOException e) {
            trace("error scanning JAR: " + e);
         }
         return null;
      }
   }
}

final class JarLoader extends MultiClassLoader {
   private JarResources    jarResources;
   JarLoader(String jarName) throws IOException {
      // Create the JarResource and suck in the jar file.
      jarResources = new JarResources(jarName);
   }
   protected byte[] loadClassBytes(String className) {

      className = formatClassName(className);
      // Attempt to get the class data from the JarResource.
      return (jarResources.getResource(className));
   }

}
