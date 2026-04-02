package javi;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static history.Tools.trace;

/**
 * Java code formatter plugin using Eclipse JDT formatter.
 *
 * <p>Provides commands to format Java source code in the editor:</p>
 * <ul>
 *   <li>{@code :jformat} - Format enclosing brace block</li>
 *   <li>{@code :jformatr} - Format range or entire buffer</li>
 *   <li>Visual mode 'F' - Format selected range (via FormatDispatch)</li>
 * </ul>
 *
 * <p>Uses Eclipse JDT's {@code CodeFormatter} API for fully configurable
 * formatting. The formatter settings are loaded from
 * {@code conf/eclipse-formatter.xml} which configures 3-space indentation
 * to match checkstyle.xml.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * :jformat          " Format enclosing {} block
 * :jformatr         " Format entire buffer
 * v}F               " Visual select paragraph, then F to format
 * :loadplugin JavaFormat  " Load this plugin
 * }</pre>
 *
 * <h2>Dependencies</h2>
 * <p>This plugin requires Eclipse JDT JARs on the plugin classpath.
 * These are bundled into the plugin fat JAR at build time.</p>
 *
 * @see ClangFormat for C/C++ formatting
 * @see CheckStyle for Java style checking
 */
public final class JavaFormat extends Rgroup implements Plugin {

   /** Plugin descriptor. */
   public static final String pluginInfo =
      "Java code formatter (Eclipse JDT)";

   /** Eclipse formatter config file path. */
   private static final String CONFIG_FILE =
      "conf/eclipse-formatter.xml";

   /** Cached Eclipse CodeFormatter instance. */
   private static Object codeFormatter;

   static {
      new JavaFormat();
   }

   enum Cmd {
      UNUSED,    // 0
      JFORMAT,   // 1: format enclosing block (:jformat)
      JFORMATR,  // 2: format range (:jformatr)
   }

   private static final Cmd[] CMDS = Cmd.values();

   public JavaFormat() {
      final String[] rnames = {
         "",
         "jformat",   // Format enclosing block
         "jformatr",  // Format range
      };
      register(rnames);
   }

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws IOException {
      Cmd cmd = CMDS[rnum];
      switch (cmd) {
         case JFORMAT:
            formatBlock(fvc);
            return null;
         case JFORMATR:
            if (count > 0 && rcount > 0)
               format(count, rcount, fvc.edvec);
            else
               formatAll(fvc.edvec);
            return null;
         default:
            throw new RuntimeException("JavaFormat:default");
      }
   }

   /**
    * Format a range of lines in a TextEdit buffer.
    *
    * @param startLine the first line to format (1-based)
    * @param endLine the last line to format (1-based, inclusive)
    * @param ex the TextEdit buffer to format
    */
   @SuppressWarnings("unchecked")
   static void format(int startLine, int endLine, TextEdit ex) {
      try {
         int totalLines = ex.readIn() - 1;
         if (totalLines <= 0) {
            UI.reportMessage("No lines to format");
            return;
         }

         StringBuilder docb = new StringBuilder();
         for (int i = 1; i <= totalLines; i++) {
            docb.append(ex.at(i).toString());
            docb.append("\n");
         }
         String content = docb.toString();

         String formatted = formatWithEclipse(content);
         if (formatted == null) {
            UI.reportMessage("Formatter returned no changes");
            return;
         }

         ArrayList<String> lines = splitLines(formatted);
         ex.remove(1, totalLines);
         ex.insertStrings(lines, 1);
         ex.checkpoint();
         UI.reportMessage("Formatted " + totalLines + " lines -> "
            + lines.size() + " lines");

      } catch (Exception e) {
         UI.reportError("Java format: " + e.getMessage());
         trace("JavaFormat error", e);
      }
   }

   /**
    * Format the entire buffer.
    *
    * @param ex the TextEdit buffer to format
    */
   static void formatAll(TextEdit ex) {
      int lastLine = ex.readIn() - 1;
      if (lastLine > 0)
         format(1, lastLine, ex);
   }

   /**
    * Format Java source code using Eclipse JDT formatter.
    *
    * <p>Uses reflection to call Eclipse JDT APIs so this class
    * can compile without Eclipse JDT on the compile classpath.
    * The Eclipse JDT JARs are available at runtime via the
    * plugin classloader.</p>
    *
    * @param code the Java code to format
    * @return formatted code, or null if formatting failed
    */
   static String formatWithEclipse(String code) {
      try {
         Object formatter = getFormatter();
         if (formatter == null) {
            UI.reportError(
               "Eclipse JDT formatter not available");
            return null;
         }

         // CodeFormatter.format(int kind, String source,
         //    int offset, int length, int indentLevel,
         //    String lineSeparator)
         // kind = K_COMPILATION_UNIT | F_INCLUDE_COMMENTS
         //      = 8 | 32 = 40
         java.lang.reflect.Method formatMethod =
            formatter.getClass().getMethod("format",
               int.class, String.class, int.class,
               int.class, int.class, String.class);
         Object textEdit = formatMethod.invoke(formatter,
            40, code, 0, code.length(), 0, "\n");

         if (textEdit == null)
            return null;

         // IDocument doc = new Document(code)
         Class<?> docClass = formatter.getClass()
            .getClassLoader().loadClass(
               "org.eclipse.jface.text.Document");
         Object doc = docClass.getConstructor(String.class)
            .newInstance(code);

         // textEdit.apply(doc)
         textEdit.getClass().getMethod("apply",
            formatter.getClass().getClassLoader().loadClass(
               "org.eclipse.jface.text.IDocument"))
            .invoke(textEdit, doc);

         // doc.get()
         String result = (String) doc.getClass()
            .getMethod("get").invoke(doc);

         return code.equals(result) ? null : result;

      } catch (Exception e) {
         trace("Eclipse format error", e);
         UI.reportError("Format error: " + e.getMessage());
         return null;
      }
   }

   /**
    * Get or create the Eclipse CodeFormatter instance.
    *
    * <p>Loads formatter settings from the Eclipse formatter
    * config XML file and creates a CodeFormatter.</p>
    *
    * @return the CodeFormatter, or null if Eclipse JDT unavailable
    */
   private static Object getFormatter() {
      if (codeFormatter != null)
         return codeFormatter;

      try {
         ClassLoader cl = JavaFormat.class.getClassLoader();
         Map<String, String> options = loadFormatterConfig(cl);

         // ToolFactory.createCodeFormatter(options,
         //    ToolFactory.M_FORMAT_EXISTING)
         // M_FORMAT_EXISTING = 0
         Class<?> toolFactory = cl.loadClass(
            "org.eclipse.jdt.core.ToolFactory");
         java.lang.reflect.Method createMethod =
            toolFactory.getMethod("createCodeFormatter",
               Map.class, int.class);
         codeFormatter = createMethod.invoke(null, options, 0);
         return codeFormatter;

      } catch (ClassNotFoundException e) {
         trace("Eclipse JDT not on classpath: " + e);
         return null;
      } catch (Exception e) {
         trace("Error creating formatter", e);
         return null;
      }
   }

   /**
    * Load formatter options from Eclipse formatter config XML.
    *
    * <p>Parses the XML config file to extract formatter settings
    * as key-value pairs. Falls back to defaults if the config
    * file is not found.</p>
    *
    * @param cl classloader for Eclipse JDT classes
    * @return map of formatter options
    */
   private static Map<String, String> loadFormatterConfig(
         ClassLoader cl) {
      Map<String, String> options = new HashMap<>();

      // Set compiler compliance (needed for Eclipse formatter)
      options.put("org.eclipse.jdt.core.compiler.source", "22");
      options.put(
         "org.eclipse.jdt.core.compiler.compliance", "22");
      options.put(
         "org.eclipse.jdt.core.compiler.codegen.targetPlatform",
         "22");

      // Try loading from config XML
      java.io.File configFile = new java.io.File(CONFIG_FILE);
      if (configFile.exists()) {
         try {
            Map<String, String> xmlOptions =
               parseEclipseConfig(configFile);
            options.putAll(xmlOptions);
            trace("Loaded formatter config from "
               + CONFIG_FILE + " (" + xmlOptions.size()
               + " settings)");
         } catch (Exception e) {
            trace("Error reading config, using defaults: " + e);
            setDefaultOptions(options);
         }
      } else {
         trace("No config file at " + CONFIG_FILE
            + ", using defaults");
         setDefaultOptions(options);
      }
      return options;
   }

   /**
    * Set default formatter options for 3-space indentation.
    *
    * @param options the options map to populate
    */
   private static void setDefaultOptions(
         Map<String, String> options) {
      options.put(
         "org.eclipse.jdt.core.formatter.tabulation.char",
         "space");
      options.put(
         "org.eclipse.jdt.core.formatter.tabulation.size",
         "3");
      options.put(
         "org.eclipse.jdt.core.formatter.indentation.size",
         "3");
      options.put(
         "org.eclipse.jdt.core.formatter.lineSplit",
         "90");
      options.put(
         "org.eclipse.jdt.core.formatter.continuation_indentation",
         "1");
   }

   /**
    * Parse Eclipse formatter config XML file.
    *
    * <p>Reads the standard Eclipse formatter profile XML format:
    * {@code <profiles><profile><setting id="..." value="..."/>}
    * </p>
    *
    * @param configFile the XML config file
    * @return map of setting id to value
    * @throws Exception if parsing fails
    */
   private static Map<String, String> parseEclipseConfig(
         java.io.File configFile) throws Exception {
      Map<String, String> settings = new HashMap<>();
      javax.xml.parsers.DocumentBuilderFactory factory =
         javax.xml.parsers.DocumentBuilderFactory.newInstance();
      javax.xml.parsers.DocumentBuilder builder =
         factory.newDocumentBuilder();
      org.w3c.dom.Document doc = builder.parse(configFile);
      org.w3c.dom.NodeList nodes =
         doc.getElementsByTagName("setting");
      for (int i = 0; i < nodes.getLength(); i++) {
         org.w3c.dom.Element el =
            (org.w3c.dom.Element) nodes.item(i);
         String id = el.getAttribute("id");
         String value = el.getAttribute("value");
         if (id != null && !id.isEmpty())
            settings.put(id, value);
      }
      return settings;
   }

   /**
    * Split formatted text into lines for buffer insertion.
    *
    * @param text the formatted text
    * @return list of lines (without newline characters)
    */
   private static ArrayList<String> splitLines(String text) {
      ArrayList<String> lines = new ArrayList<>();
      int start = 0;
      for (int i = 0; i < text.length(); i++) {
         if (text.charAt(i) == '\n') {
            lines.add(text.substring(start, i));
            start = i + 1;
         }
      }
      if (start < text.length())
         lines.add(text.substring(start));
      return lines;
   }

   /**
    * Format the enclosing brace block around the cursor.
    *
    * <p>Finds the innermost {@code {}} pair enclosing the cursor
    * and formats that range. If no enclosing braces are found,
    * falls back to formatting the entire buffer.</p>
    *
    * @param fvc the current file-view context
    */
   @SuppressWarnings("unchecked")
   static void formatBlock(FvContext fvc) {
      TextEdit ex = fvc.edvec;
      int curLine = fvc.inserty();
      int lastLine = ex.readIn() - 1;
      List<String> lines = new ArrayList<>(lastLine);
      for (int i = 1; i <= lastLine; i++)
         lines.add(ex.at(i).toString());

      int[] braces = findEnclosingBraces(lines, curLine - 1);
      if (braces == null)
         formatAll(ex);
      else
         format(braces[0] + 1, braces[1] + 1, ex);
   }

   /**
    * Find the innermost enclosing brace pair around a line.
    *
    * @param lines 0-based list of source lines
    * @param curLine 0-based cursor line index
    * @return [openLine, closeLine] 0-based, or null
    */
   static int[] findEnclosingBraces(List<String> lines,
         int curLine) {
      int braceCount = 0;
      int openLine = -1;
      for (int i = curLine; i >= 0; i--) {
         String line = lines.get(i);
         for (int j = line.length() - 1; j >= 0; j--) {
            char c = line.charAt(j);
            if (c == '}')
               braceCount++;
            else if (c == '{') {
               if (braceCount == 0) {
                  openLine = i;
                  break;
               }
               braceCount--;
            }
         }
         if (openLine >= 0)
            break;
      }
      if (openLine < 0)
         return null;

      braceCount = 0;
      int closeLine = -1;
      for (int i = openLine; i < lines.size(); i++) {
         String line = lines.get(i);
         for (int j = 0; j < line.length(); j++) {
            char c = line.charAt(j);
            if (c == '{')
               braceCount++;
            else if (c == '}') {
               braceCount--;
               if (braceCount == 0) {
                  closeLine = i;
                  break;
               }
            }
         }
         if (closeLine >= 0)
            break;
      }
      if (closeLine < 0)
         return null;

      return new int[]{openLine, closeLine};
   }
}
