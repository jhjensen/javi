package javi;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;

import static history.Tools.trace;

public interface Plugin {

   List<Path> pluginPaths = Arrays.asList(
      Path.of("build/plugins"),
      Path.of("dist"),
      getInstallLibDir(),
      Path.of(System.getProperty("user.home") + "/.local/share/javi/lib/")
   );
   private static URL findPluginJar(String name) throws MalformedURLException {
      String jarName = "javi-" + name + ".jar";
      for (Path path : pluginPaths) {
         if (path != null) {
            Path jarf = path.resolve(jarName);
            trace("looking for", jarName, " in ", jarf);
            if (Files.exists(jarf)) {
               trace("found plugin file", jarf);
               return jarf.toUri().toURL();
            }
         }
      }
      // Return build/plugins path for the error message
      return Path.of(jarName).toUri().toURL();
   }

   /** Returns the lib/ directory of the javi installation, or null if
     * not running from an installed JAR.
     */
   private static Path getInstallLibDir() {
      try {
         var source = MiscCommands.class.getProtectionDomain()
            .getCodeSource();
         if (source == null)
            return null;
         var location = source.getLocation();
         if (location == null)
            return null;
         java.io.File jarFile = new java.io.File(location.toURI());
         if (jarFile.isFile())
            return jarFile.getParentFile().toPath();
         // Running from a classes directory (development)
         return null;
      } catch (Exception e) {
         trace("failed to find install lib dir", e);
         return null;
      }
   }

   /** Load a plugin JAR. Discovers plugin classes by scanning
     * the JAR manifest for a Plugin-Class attribute, or by
     * finding classes that implement Plugin.
     */
   static void load(final String pluginName, List<String> args)
         throws IOException, InputException {
      URL[] urls = new URL[] {findPluginJar(pluginName)};

      try {
         ClassLoader pluginLoader =
            new URLClassLoader(
               urls,
               PluginFactory.class.getClassLoader());
   
         ServiceLoader<PluginFactory> loader =
             ServiceLoader.load(PluginFactory.class, pluginLoader);
   
         for (PluginFactory factory : loader) {
            Plugin plugin = factory.create(args);
            trace("Loaded plugin: " + factory.getClass().getName());
            return;
         }
      } catch (Exception e) {
         trace("exception loading plugin ", e);
      }

      trace("failed to loaded plugin: ", pluginName);
      throw new InputException("failed to create plugin " + pluginName);
   }

}
