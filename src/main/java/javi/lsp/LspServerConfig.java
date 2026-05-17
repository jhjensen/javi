package javi.lsp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static history.Tools.trace;

/**
 * Configuration for a language server.
 *
 * <p>Each {@code LspServerConfig} describes how to start and communicate
 * with a specific language server. Configurations are keyed by language
 * identifier (e.g., "java", "python").</p>
 *
 * <h2>Configuration Properties</h2>
 * <ul>
 *   <li>{@link #languageId} - LSP language identifier</li>
 *   <li>{@link #command} - Command line to start the server</li>
 *   <li>{@link #fileExtensions} - File extensions this server handles</li>
 *   <li>{@link #rootPattern} - File/dir to detect project root (e.g., "pom.xml")</li>
 * </ul>
 *
 * <h2>Built-in Configurations</h2>
 * <p>{@link #getDefaults()} provides pre-configured servers for common
 * languages. Users can override via .javini settings.</p>
 *
 * <h2>Example</h2>
 * <pre>
 * LspServerConfig java = new LspServerConfig("java",
 *    new String[]{"jdtls"}, new String[]{".java"}, "build.gradle");
 * </pre>
 *
 * @see LspManager
 */
final class LspServerConfig {

   /** The LSP language identifier (e.g., "java", "python"). */
   final String languageId;

   /** Command and arguments to start the language server process. */
   final String[] command;

   /** File extensions this server handles (e.g., ".java", ".py"). */
   final String[] fileExtensions;

   /**
    * Name of a file or directory used to detect the project root.
    * For example, "build.gradle" or "pom.xml" for Java projects.
    * May be null if no root detection is needed.
    */
   final String rootPattern;

   /**
    * Whether this server has been enabled by the user.
    * Defaults to true for built-in configurations.
    */
   boolean enabled = true;

   /**
    * Whether this server is an overlay that receives notifications
    * for all file types, not just its own extensions. Overlay servers
    * (e.g., spell checkers) run alongside language-specific servers.
    */
   final boolean overlay;

   /**
    * Creates a new language server configuration.
    *
    * @param languageId the LSP language identifier
    * @param command the command to start the server
    * @param fileExtensions file extensions this server handles
    * @param rootPattern file/dir name for project root detection, may be null
    */
   LspServerConfig(String languageId, String[] command,
         String[] fileExtensions, String rootPattern) {
      this(languageId, command, fileExtensions, rootPattern, false);
   }

   /**
    * Creates a new language server configuration with overlay flag.
    *
    * @param languageId the LSP language identifier
    * @param command the command to start the server
    * @param fileExtensions file extensions that auto-start this server
    * @param rootPattern file/dir name for project root detection
    * @param overlay if true, receives notifications for all file types
    */
   LspServerConfig(String languageId, String[] command,
         String[] fileExtensions, String rootPattern,
         boolean overlay) {
      this.languageId = languageId;
      this.command = command;
      this.fileExtensions = fileExtensions;
      this.rootPattern = rootPattern;
      this.overlay = overlay;
   }

   /**
    * Returns the default language server configurations.
    *
    * <p>These represent common language servers that users may have
    * installed. Servers are only started when files matching their
    * extensions are opened.</p>
    *
    * <p>UNCLEAR: Which language servers should be enabled by default?
    * Currently all are enabled but require the server binary to be
    * installed. Should we auto-detect availability?</p>
    *
    * @return map of language id to server configuration
    */
   static Map<String, LspServerConfig> getDefaults() {
      Map<String, LspServerConfig> configs = new HashMap<>();

      // Eclipse JDT Language Server for Java
      // Search common install locations for jdtls
      String jdtlsCmd = findExecutable("jdtls",
         System.getProperty("user.home") + "/.local/share/jdtls/bin/jdtls",
         "/opt/homebrew/bin/jdtls",
         "/usr/local/bin/jdtls");
      configs.put("java", new LspServerConfig("java",
         new String[]{jdtlsCmd},
         new String[]{".java"},
         "build.gradle"));

      // TypeScript/JavaScript language server
      configs.put("typescript", new LspServerConfig("typescript",
         new String[]{"typescript-language-server", "--stdio"},
         new String[]{".ts", ".js", ".tsx", ".jsx"},
         "package.json"));

      // Python language server (Pyright)
      configs.put("python", new LspServerConfig("python",
         new String[]{"pyright-langserver", "--stdio"},
         new String[]{".py"},
         "pyproject.toml"));

      // C/C++ language server (clangd)
      // Search common install locations for clangd
      String clangdCmd = findExecutable("clangd",
         "/opt/homebrew/opt/llvm/bin/clangd",
         "/usr/local/opt/llvm/bin/clangd",
         "/usr/bin/clangd");
      configs.put("c", new LspServerConfig("c",
         new String[]{clangdCmd},
         new String[]{".c", ".h", ".cpp", ".hpp", ".cc", ".cxx"},
         "compile_commands.json"));

      // Rust language server (rust-analyzer)
      configs.put("rust", new LspServerConfig("rust",
         new String[]{"rust-analyzer"},
         new String[]{".rs"},
         "Cargo.toml"));

      // Harper spell/grammar checker (overlay — checks all file types)
      // Handles markdown and typst natively; extracts comments from
      // Java, C, C++, Python, Rust, Go, etc. via tree-sitter.
      // Install: brew install harper  OR  cargo install harper-ls
      String harperCmd = findExecutable("harper-ls",
         "/opt/homebrew/bin/harper-ls",
         "/usr/local/bin/harper-ls",
         System.getProperty("user.home") + "/.cargo/bin/harper-ls");
      configs.put("harper", new LspServerConfig("harper",
         new String[]{harperCmd, "--stdio"},
         new String[]{".md", ".typ"},
         null,
         true));

      return configs;
   }

   /**
    * Finds the first available executable from the given candidates.
    *
    * <p>First checks each explicit path, then falls back to the
    * simple name (which will be resolved via PATH at launch time).</p>
    *
    * @param simpleName the bare command name (fallback)
    * @param candidates absolute paths to check
    * @return the first found executable path, or simpleName
    */
   private static String findExecutable(String simpleName,
         String... candidates) {
      for (String path : candidates) {
         File f = new File(path);
         if (f.canExecute()) {
            trace("LSP: found " + simpleName + " at " + path);
            return path;
         }
      }
      return simpleName;
   }

   /**
    * Returns the language server config for a given file extension.
    *
    * @param configs the map of language configs to search
    * @param extension the file extension including the dot (e.g. ".java")
    * @return the matching config, or null if none found
    */
   static LspServerConfig forExtension(Map<String, LspServerConfig> configs,
         String extension) {
      for (LspServerConfig config : configs.values()) {
         if (config.enabled) {
            for (String ext : config.fileExtensions) {
               if (ext.equals(extension))
                  return config;
            }
         }
      }
      return null;
   }

   /**
    * Returns the file extension from a filename.
    *
    * @param filename the filename to extract extension from
    * @return the extension including dot (e.g. ".java"), or empty string
    */
   static String getExtension(String filename) {
      if (null == filename)
         return "";
      int dot = filename.lastIndexOf('.');
      return (dot >= 0)
         ? filename.substring(dot)
         : "";
   }

   public String toString() {
      return "LspServerConfig[" + languageId + " "
         + String.join(" ", command) + "]";
   }

   /**
    * Checks whether the language server binary is available on PATH.
    *
    * <p>Examines the first element of {@link #command} (the binary name)
    * and searches the system PATH. Also checks for absolute paths.</p>
    *
    * @return true if the server binary can be found
    */
   boolean isAvailable() {
      if (null == command || 0 == command.length)
         return false;

      String binary = command[0];
      File bf = new File(binary);
      if (bf.isAbsolute())
         return bf.canExecute();

      String pathEnv = System.getenv("PATH");
      if (null == pathEnv)
         return false;

      for (String dir : pathEnv.split(File.pathSeparator)) {
         File candidate = new File(dir, binary);
         if (candidate.canExecute()) {
            trace("LSP: found " + binary + " at " + candidate);
            return true;
         }
      }
      trace("LSP: " + binary + " not found on PATH");
      return false;
   }

   // ---------------------------------------------------------------
   // Config persistence (~/.javi/lsp.conf)
   // ---------------------------------------------------------------

   /** Default config directory under user home. */
   private static final String CONFIG_DIR = ".javi";
   /** Config file name within the config directory. */
   private static final String CONFIG_FILE = "lsp.conf";

   /**
    * Returns the config file path ({@code ~/.javi/lsp.conf}).
    *
    * @return the config file
    */
   static File getConfigFile() {
      String home = System.getProperty("user.home");
      return new File(new File(home, CONFIG_DIR), CONFIG_FILE);
   }

   /**
    * Loads user-customized server configurations from disk.
    *
    * <p>Reads {@code ~/.javi/lsp.conf} and merges entries into the
    * given defaults map. Each line has the format:</p>
    * <pre>
    * languageId = command [args...]
    * !languageId = command [args...]   # disabled
    * </pre>
    *
    * <p>Lines starting with {@code #} are comments. A leading {@code !}
    * on the language id marks the server as disabled. Unknown language
    * ids create new entries with the extensions inferred from the id
    * (e.g., {@code go} gets {@code .go}).</p>
    *
    * @param defaults the default configs map (modified in place)
    */
   static void loadUserConfigs(Map<String, LspServerConfig> defaults) {
      File configFile = getConfigFile();
      if (!configFile.isFile())
         return;

      try (BufferedReader reader = new BufferedReader(
            new FileReader(configFile))) {
         String line;
         while (null != (line = reader.readLine())) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
               continue;

            boolean disabled = false;
            if (line.startsWith("!")) {
               disabled = true;
               line = line.substring(1).trim();
            }

            int eq = line.indexOf('=');
            if (eq <= 0) {
               trace("LSP config: skipping malformed line: " + line);
               continue;
            }

            String langId = line.substring(0, eq).trim();
            String cmdStr = line.substring(eq + 1).trim();
            if (langId.isEmpty() || cmdStr.isEmpty()) {
               trace("LSP config: skipping incomplete line: " + line);
               continue;
            }

            String[] cmdParts = cmdStr.split("\\s+");

            // Merge with existing config (preserve extensions/rootPattern)
            LspServerConfig existing = defaults.get(langId);
            String[] exts;
            String rootPat;
            if (null != existing) {
               exts = existing.fileExtensions;
               rootPat = existing.rootPattern;
            } else {
               // New language: infer extension from id
               exts = new String[]{"." + langId};
               rootPat = null;
            }

            LspServerConfig config = new LspServerConfig(
               langId, cmdParts, exts, rootPat);
            config.enabled = !disabled;
            defaults.put(langId, config);
            trace("LSP config: loaded " + (disabled ? "(disabled) " : "")
               + langId + " = " + cmdStr);
         }
      } catch (IOException e) {
         trace("LSP config: error reading " + configFile + ": " + e);
      }
   }

   /**
    * Saves user-customized configurations to disk.
    *
    * <p>Writes only entries that differ from the built-in defaults
    * to {@code ~/.javi/lsp.conf}. Creates the {@code ~/.javi/}
    * directory if it does not exist.</p>
    *
    * @param configs the current configs map
    */
   static void saveUserConfigs(Map<String, LspServerConfig> configs) {
      Map<String, LspServerConfig> defaults = getDefaults();
      File configFile = getConfigFile();

      // Collect entries that differ from defaults
      Map<String, LspServerConfig> overrides = new HashMap<>();
      for (Map.Entry<String, LspServerConfig> entry : configs.entrySet()) {
         String langId = entry.getKey();
         LspServerConfig config = entry.getValue();
         LspServerConfig def = defaults.get(langId);

         if (null == def || !commandEquals(def.command, config.command)
               || def.enabled != config.enabled) {
            overrides.put(langId, config);
         }
      }

      // If no overrides and no file exists, nothing to do
      if (overrides.isEmpty() && !configFile.exists())
         return;

      // Ensure directory exists
      File dir = configFile.getParentFile();
      if (!dir.isDirectory() && !dir.mkdirs()) {
         trace("LSP config: cannot create directory " + dir);
         return;
      }

      try (BufferedWriter writer = new BufferedWriter(
            new FileWriter(configFile))) {
         writer.write("# Javi LSP server configuration");
         writer.newLine();
         writer.write("# Format: languageId = command [args...]");
         writer.newLine();
         writer.write("# Prefix with ! to disable a server");
         writer.newLine();
         writer.newLine();

         for (Map.Entry<String, LspServerConfig> entry
               : overrides.entrySet()) {
            LspServerConfig config = entry.getValue();
            String prefix = config.enabled ? "" : "!";
            writer.write(prefix + config.languageId + " = "
               + String.join(" ", config.command));
            writer.newLine();
         }
      } catch (IOException e) {
         trace("LSP config: error writing " + configFile + ": " + e);
      }
   }

   /**
    * Compares two command arrays for equality.
    */
   private static boolean commandEquals(String[] a, String[] b) {
      if (a.length != b.length)
         return false;
      for (int i = 0; i < a.length; i++) {
         if (!a[i].equals(b[i]))
            return false;
      }
      return true;
   }
}
