package javi.lsp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static history.Tools.trace;

/**
 * Manages language server instances and routes requests by language.
 *
 * <p>LspManager is the central coordination point for LSP in Javi.
 * It maintains a pool of {@link LspClient} instances, one per
 * language. Servers are started lazily when a file of that language
 * is first opened.</p>
 *
 * <h2>Singleton Pattern</h2>
 * <p>Use {@link #getInstance()} to get the shared manager instance.
 * The manager is thread-safe for concurrent access from the editor
 * and background threads.</p>
 *
 * <h2>Server Lifecycle</h2>
 * <ol>
 *   <li>File opened: {@link #notifyDidOpen} checks for matching server</li>
 *   <li>No server running: auto-starts based on file extension</li>
 *   <li>Server runs until: editor exit, explicit stop, or crash</li>
 *   <li>On editor exit: {@link #shutdownAll()} stops all servers</li>
 * </ol>
 *
 * <h2>Error Recovery</h2>
 * <p>If a server crashes, the manager detects it on the next request
 * and can optionally restart. Currently limited to manual restart
 * via {@link #restartServer(String)}.</p>
 *
 * <h2>Project Root Detection</h2>
 * <p>Uses the server config's {@code rootPattern} to find the project
 * root by walking up from the current file. Falls back to the
 * current working directory.</p>
 *
 * <h2>UNCLEAR: Multiple Root Folders</h2>
 * <p>LSP supports workspace folders, but Javi currently assumes a
 * single root. Multi-root support would require tracking which files
 * belong to which workspace.</p>
 *
 * @see LspClient
 * @see LspServerConfig
 * @see LspCommands
 */
public final class LspManager {

   /** Maximum number of auto-restarts before giving up. */
   private static final int MAX_RESTARTS = 3;

   private static LspManager instance;

   private final Map<String, LspServerConfig> configs;
   private final Map<String, LspClient> activeClients = new HashMap<>();
   private final Set<String> disabledLanguages = new HashSet<>();
   private final Map<String, String> rootDirOverrides = new HashMap<>();
   private final Map<String, List<String>> sourcePaths = new HashMap<>();
   private boolean enabled = true;
   private int requestTimeoutSeconds = 3;
   private LspClient.DiagnosticHandler diagnosticHandler;

   /**
    * Creates the LSP manager with default server configurations,
    * then overlays any user-customized settings from
    * {@code ~/.javi/lsp.conf}.
    */
   private LspManager() {
      configs = LspServerConfig.getDefaults();
      LspServerConfig.loadUserConfigs(configs);
   }

   /**
    * Returns the singleton LspManager instance.
    *
    * @return the shared manager
    */
   public static synchronized LspManager getInstance() {
      if (null == instance) {
         instance = new LspManager();
      }
      return instance;
   }

   /**
    * Returns whether LSP support is globally enabled.
    *
    * @return true if LSP is enabled
    */
   public boolean isEnabled() {
      return enabled;
   }

   /**
    * Checks if there is an already-running (initialized) client for
    * the given file type. Does NOT start a server or attempt restarts.
    *
    * <p>Used by the :ta tag lookup path to avoid blocking the AWT
    * thread when no server is available.</p>
    *
    * @param filePath the absolute file path
    * @return true if a running client exists for this file type
    */
   public boolean hasRunningClient(String filePath) {
      if (!enabled)
         return false;

      String ext = LspServerConfig.getExtension(filePath);
      LspServerConfig config = LspServerConfig.forExtension(configs, ext);
      if (null == config)
         return false;

      if (disabledLanguages.contains(config.languageId))
         return false;

      synchronized (activeClients) {
         LspClient client = activeClients.get(config.languageId);
         return null != client && client.isInitialized();
      }
   }

   /**
    * Enables or disables LSP support globally.
    *
    * <p>When disabled, all servers are shut down and no new
    * servers will be started.</p>
    *
    * @param enabled true to enable, false to disable
    */
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
      if (!enabled) {
         shutdownAll();
      }
   }

   /**
    * Disables LSP for a specific language.
    *
    * @param languageId the language to disable (e.g. "c", "java")
    */
   public void disableLanguage(String languageId) {
      disabledLanguages.add(languageId);
      trace("LSP: disabled language " + languageId);
   }

   /**
    * Enables LSP for a specific language (removes from disabled set).
    *
    * @param languageId the language to enable
    */
   public void enableLanguage(String languageId) {
      disabledLanguages.remove(languageId);
      trace("LSP: enabled language " + languageId);
   }

   /**
    * Returns whether a language is disabled.
    *
    * @param languageId the language identifier
    * @return true if the language is disabled
    */
   public boolean isLanguageDisabled(String languageId) {
      return disabledLanguages.contains(languageId);
   }

   /**
    * Sets the root directory override for a language.
    * LSP results outside this directory will be filtered out.
    *
    * @param languageId the language identifier
    * @param rootDir the root directory (absolute or relative to cwd)
    */
   public void setRootDir(String languageId, String rootDir) {
      File f = new File(rootDir);
      String absolute = f.isAbsolute()
         ? rootDir : new File(System.getProperty("user.dir"), rootDir)
            .getAbsolutePath();
      rootDirOverrides.put(languageId, absolute);
      trace("LSP: set rootdir for " + languageId + " = " + absolute);
   }

   /**
    * Returns the root directory override for a language, or null.
    *
    * @param languageId the language identifier
    * @return the rootdir override, or null if not set
    */
   public String getRootDirOverride(String languageId) {
      return rootDirOverrides.get(languageId);
   }

   /**
    * Returns the request timeout in seconds.
    *
    * @return timeout seconds for LSP requests
    */
   public int getRequestTimeoutSeconds() {
      return requestTimeoutSeconds;
   }

   /**
    * Sets the request timeout in seconds for async LSP operations.
    *
    * @param seconds the timeout value (must be positive)
    */
   public void setRequestTimeoutSeconds(int seconds) {
      if (seconds > 0)
         this.requestTimeoutSeconds = seconds;
   }

   /**
    * Sets the diagnostic handler that receives error/warning notifications.
    *
    * @param handler the diagnostic handler
    */
   public void setDiagnosticHandler(LspClient.DiagnosticHandler handler) {
      this.diagnosticHandler = handler;
      // Update existing clients
      synchronized (activeClients) {
         for (LspClient client : activeClients.values()) {
            client.setDiagnosticHandler(handler);
         }
      }
   }

   /**
    * Gets or starts the LSP client for a given file.
    *
    * <p>Determines the language from the file extension, checks if a
    * matching server is already running, and starts one if needed.</p>
    *
    * @param filePath the absolute path to the file
    * @return the LSP client, or null if no server available/configured
    */
   public LspClient getClientForFile(String filePath) {
      if (!enabled)
         return null;

      String ext = LspServerConfig.getExtension(filePath);
      LspServerConfig config = LspServerConfig.forExtension(configs, ext);
      if (null == config)
         return null;

      if (disabledLanguages.contains(config.languageId))
         return null;

      return getOrStartClient(config, filePath);
   }

   /**
    * Gets or starts the LSP client for a language.
    *
    * @param config the server configuration
    * @param filePath a file path used for root detection
    * @return the LSP client, or null on failure
    */
   private LspClient getOrStartClient(LspServerConfig config,
         String filePath) {
      synchronized (activeClients) {
         LspClient client = activeClients.get(config.languageId);
         if (null != client && client.isInitialized()) {
            return client;
         }

         // If client exists but is dead, remove it (server crashed)
         if (null != client && !client.isInitialized()) {
            int crashes = client.getCrashCount();
            if (crashes >= MAX_RESTARTS) {
               trace("LSP: " + config.languageId
                  + " exceeded max restarts (" + MAX_RESTARTS + ")");
               return null;
            }
            trace("LSP: " + config.languageId
               + " server died, attempting restart #" + (crashes + 1));
            client.stop();
            activeClients.remove(config.languageId);
         }

         // Check if the server binary is available before trying
         if (!config.isAvailable()) {
            trace("LSP: server binary not found for "
               + config.languageId + " (" + config.command[0] + ")");
            return null;
         }

         // Need to start a new server
         String rootPath = detectRootPath(filePath, config.rootPattern);
         client = new LspClient(config, rootPath);
         if (null != diagnosticHandler) {
            client.setDiagnosticHandler(diagnosticHandler);
         }

         try {
            if (client.start()) {
               activeClients.put(config.languageId, client);
               return client;
            } else {
               trace("LSP: failed to start " + config.languageId
                  + " server");
               return null;
            }
         } catch (IOException e) {
            trace("LSP: error starting " + config.languageId
               + " server: " + e);
            return null;
         }
      }
   }

   /**
    * Notifies the appropriate server that a file was opened.
    *
    * <p>Auto-starts the server if not running. Extracts the full
    * file content and sends {@code textDocument/didOpen}.</p>
    * <p>Also notifies any running overlay servers (e.g., spell
    * checkers) so they can check comments and strings.</p>
    *
    * @param filePath the absolute file path
    * @param content the file content
    */
   public void notifyDidOpen(String filePath, String content) {
      LspClient client = getClientForFile(filePath);
      String ext = LspServerConfig.getExtension(filePath);
      LspServerConfig config = LspServerConfig.forExtension(configs, ext);
      String langId = (null != config) ? config.languageId : null;

      if (null != client && null != langId) {
         try {
            client.didOpen(filePath, langId, content);
         } catch (IOException e) {
            trace("LSP: didOpen error: " + e);
         }
      }

      // Notify overlay servers (spell checkers, etc.)
      notifyOverlayDidOpen(filePath, content, langId);
   }

   /**
    * Sends didOpen to all running overlay servers for the file.
    * The languageId tells the overlay server what type of file it
    * is so it can parse comments vs. prose appropriately.
    */
   private void notifyOverlayDidOpen(String filePath, String content,
         String primaryLangId) {
      String overlayLangId = resolveOverlayLanguageId(filePath,
         primaryLangId);
      if (null == overlayLangId)
         return;

      for (LspClient overlay : getRunningOverlayClients(filePath)) {
         try {
            overlay.didOpen(filePath, overlayLangId, content);
         } catch (IOException e) {
            trace("LSP: overlay didOpen error: " + e);
         }
      }
   }

   /**
    * Notifies the server that a file's content changed (full sync).
    * Also notifies running overlay servers.
    *
    * @param filePath the absolute file path
    * @param content the new full content
    */
   public void notifyDidChange(String filePath, String content) {
      LspClient client = getClientForFile(filePath);
      if (null != client) {
         try {
            client.didChange(filePath, content);
         } catch (IOException e) {
            trace("LSP: didChange error: " + e);
         }
      }

      for (LspClient overlay : getRunningOverlayClients(filePath)) {
         try {
            overlay.didChange(filePath, content);
         } catch (IOException e) {
            trace("LSP: overlay didChange error: " + e);
         }
      }
   }

   /**
    * Notifies the server of incremental content changes.
    *
    * <p>Only call this when the server supports incremental sync
    * (checked via {@link #supportsIncrementalSync}).</p>
    *
    * @param filePath the absolute file path
    * @param changes the list of change events
    */
   public void notifyDidChangeIncremental(String filePath,
         List<LspChangeEvent> changes) {
      LspClient client = getClientForFile(filePath);
      if (null == client)
         return;

      try {
         client.didChangeIncremental(filePath, changes);
      } catch (IOException e) {
         trace("LSP: didChangeIncremental error: " + e);
      }
   }

   /**
    * Returns whether the server for the given file supports
    * incremental document sync (TextDocumentSyncKind == 2).
    *
    * @param filePath the file path
    * @return true if incremental sync is supported
    */
   public boolean supportsIncrementalSync(String filePath) {
      LspClient client = getClientForFile(filePath);
      if (null == client)
         return false;
      return client.getTextDocumentSyncKind() == 2;
   }

   /**
    * Notifies the server that a file was closed.
    * Also notifies running overlay servers.
    *
    * @param filePath the absolute file path
    */
   public void notifyDidClose(String filePath) {
      LspClient client = getClientForFile(filePath);
      if (null != client) {
         try {
            client.didClose(filePath);
         } catch (IOException e) {
            trace("LSP: didClose error: " + e);
         }
      }

      for (LspClient overlay : getRunningOverlayClients(filePath)) {
         try {
            overlay.didClose(filePath);
         } catch (IOException e) {
            trace("LSP: overlay didClose error: " + e);
         }
      }
   }

   /**
    * Notifies the server that a file was saved.
    * Also notifies running overlay servers.
    *
    * @param filePath the absolute file path
    */
   public void notifyDidSave(String filePath) {
      LspClient client = getClientForFile(filePath);
      if (null != client) {
         try {
            client.didSave(filePath);
         } catch (IOException e) {
            trace("LSP: didSave error: " + e);
         }
      }

      for (LspClient overlay : getRunningOverlayClients(filePath)) {
         try {
            overlay.didSave(filePath);
         } catch (IOException e) {
            trace("LSP: overlay didSave error: " + e);
         }
      }
   }

   /**
    * Requests go-to-definition at the given position.
    *
    * @param filePath the absolute file path
    * @param line 0-based line number
    * @param character 0-based character offset
    * @return list of location maps, or null
    */
   public List<Map<String, Object>> definition(String filePath,
         int line, int character) {
      LspClient client = getClientForFile(filePath);
      if (null == client)
         return null;

      try {
         return client.definition(filePath, line, character);
      } catch (IOException e) {
         trace("LSP: definition error: " + e);
         return null;
      }
   }

   /**
    * Searches for workspace symbols matching a query string.
    *
    * @param filePath used to identify which LSP server to query
    * @param query the symbol name to search for
    * @return list of symbol information maps, or null
    */
   public List<Map<String, Object>> workspaceSymbol(String filePath,
         String query) {
      LspClient client = getClientForFile(filePath);
      if (null == client)
         return null;

      try {
         // Use a longer timeout for workspace/symbol — server may
         // need time to scan sources on first invocation
         int timeout = Math.max(requestTimeoutSeconds, 10);
         return client.workspaceSymbol(query, timeout);
      } catch (IOException e) {
         trace("LSP: workspaceSymbol error: " + e);
         return null;
      }
   }

   /**
    * Searches for workspace symbols by language ID directly.
    * Used when the current file type doesn't match the target
    * language (e.g., searching from a help buffer).
    *
    * @param languageId the language server to query
    * @param query the symbol name to search for
    * @return list of symbol information maps, or null
    */
   public List<Map<String, Object>> workspaceSymbolByLanguage(
         String languageId, String query) {
      synchronized (activeClients) {
         LspClient client = activeClients.get(languageId);
         if (null == client || !client.isInitialized())
            return null;
         try {
            int timeout = Math.max(requestTimeoutSeconds, 10);
            return client.workspaceSymbol(query, timeout);
         } catch (IOException e) {
            trace("LSP: workspaceSymbol error for " + languageId
               + ": " + e);
            return null;
         }
      }
   }

   /**
    * Requests find-references at the given position.
    *
    * @param filePath the absolute file path
    * @param line 0-based line number
    * @param character 0-based character offset
    * @return list of location maps, or null
    */
   public List<Map<String, Object>> references(String filePath,
         int line, int character) {
      LspClient client = getClientForFile(filePath);
      if (null == client)
         return null;

      try {
         return client.references(filePath, line, character);
      } catch (IOException e) {
         trace("LSP: references error: " + e);
         return null;
      }
   }

   /**
    * Requests hover information at the given position.
    *
    * @param filePath the absolute file path
    * @param line 0-based line number
    * @param character 0-based character offset
    * @return the hover text, or null
    */
   public String hover(String filePath, int line, int character) {
      LspClient client = getClientForFile(filePath);
      if (null == client)
         return null;

      try {
         return client.hover(filePath, line, character);
      } catch (IOException e) {
         trace("LSP: hover error: " + e);
         return null;
      }
   }

   /**
    * Requests code completions at the given position.
    *
    * @param filePath the absolute file path
    * @param line 0-based line number
    * @param character 0-based character offset
    * @return list of completion items, or null
    */
   public List<Map<String, Object>> completion(String filePath,
         int line, int character) {
      LspClient client = getClientForFile(filePath);
      if (null == client)
         return null;

      try {
         return client.completion(filePath, line, character);
      } catch (IOException e) {
         trace("LSP: completion error: " + e);
         return null;
      }
   }

   /**
    * Restarts the server for a given language.
    *
    * @param languageId the language identifier
    * @return true if restart succeeded
    */
   public boolean restartServer(String languageId) {
      synchronized (activeClients) {
         LspClient client = activeClients.remove(languageId);
         if (null != client) {
            client.shutdown();
         }
      }
      // Server will restart on next request
      return true;
   }

   /**
    * Shuts down all active language servers.
    *
    * <p>Called when the editor exits or LSP is disabled.</p>
    */
   public void shutdownAll() {
      synchronized (activeClients) {
         for (LspClient client : activeClients.values()) {
            try {
               client.shutdown();
            } catch (Exception e) {
               trace("LSP: error shutting down: " + e);
            }
         }
         activeClients.clear();
      }
   }

   /**
    * Returns the configuration for a language.
    *
    * @param languageId the language identifier
    * @return the config, or null if not configured
    */
   public LspServerConfig getConfig(String languageId) {
      return configs.get(languageId);
   }

   /**
    * Returns the effective project root for a given file, considering
    * rootdir overrides. Priority:
    * <ol>
    *   <li>Per-language rootdir override (from lsp.rootdir)</li>
    *   <li>Client's detected root path (from rootPattern matching)</li>
    *   <li>Current working directory (fallback)</li>
    * </ol>
    *
    * @param filePath the file path to look up
    * @return the project root, or current directory
    */
   public String getProjectRoot(String filePath) {
      // Check rootdir override for this file's language
      String ext = LspServerConfig.getExtension(filePath);
      LspServerConfig config = LspServerConfig.forExtension(configs, ext);
      if (null != config) {
         String override = rootDirOverrides.get(config.languageId);
         if (null != override)
            return override;
      }

      LspClient client = getClientForFile(filePath);
      if (null == client)
         return System.getProperty("user.dir", ".");
      return client.getRootPath();
   }

   /**
    * Adds or updates a server configuration and persists the
    * change to {@code ~/.javi/lsp.conf}.
    *
    * @param languageId the language identifier
    * @param config the server configuration
    */
   public void setConfig(String languageId, LspServerConfig config) {
      configs.put(languageId, config);
      LspServerConfig.saveUserConfigs(configs);
   }

   /**
    * Returns the language id for a given file path, or null.
    *
    * @param filePath the file path
    * @return the language id, or null if no config matches
    */
   public String getLanguageId(String filePath) {
      String ext = LspServerConfig.getExtension(filePath);
      LspServerConfig config = LspServerConfig.forExtension(configs, ext);
      return (null != config) ? config.languageId : null;
   }

   /**
    * Returns the set of disabled languages (unmodifiable view).
    *
    * @return the disabled language set
    */
   public Set<String> getDisabledLanguages() {
      return java.util.Collections.unmodifiableSet(disabledLanguages);
   }

   /**
    * Returns the rootdir overrides map (unmodifiable view).
    *
    * @return the rootdir overrides
    */
   public Map<String, String> getRootDirOverrides() {
      return java.util.Collections.unmodifiableMap(rootDirOverrides);
   }

   /**
    * Returns the language IDs of all currently running and
    * initialized LSP clients.
    *
    * @return list of running language IDs
    */
   public List<String> getRunningLanguageIds() {
      List<String> result = new ArrayList<>();
      synchronized (activeClients) {
         for (Map.Entry<String, LspClient> entry
               : activeClients.entrySet()) {
            if (entry.getValue().isInitialized())
               result.add(entry.getKey());
         }
      }
      return result;
   }

   /**
    * Returns a status summary of all configured/active servers.
    *
    * @return a formatted status string
    */
   public String getStatus() {
      StringBuilder sb = new StringBuilder();
      sb.append("LSP: ");
      sb.append(enabled ? "enabled" : "disabled");
      synchronized (activeClients) {
         if (!activeClients.isEmpty()) {
            sb.append(" | active: ");
            boolean first = true;
            for (Map.Entry<String, LspClient> entry
                  : activeClients.entrySet()) {
               if (!first)
                  sb.append(", ");
               first = false;
               sb.append(entry.getKey());
               sb.append(entry.getValue().isInitialized()
                  ? "(ok)" : "(dead)");
            }
         } else {
            sb.append(" | no active servers");
         }
      }
      return sb.toString();
   }

   /**
    * Detects the project root path by searching for a marker file
    * in parent directories. Defaults to the current working directory
    * to restrict LSP results to the local project.
    *
    * @param filePath a file in the project
    * @param rootPattern the marker filename (e.g. "build.gradle")
    * @return the detected root path, or current directory
    */
   static String detectRootPath(String filePath, String rootPattern) {
      String cwd = System.getProperty("user.dir", ".");

      if (null == rootPattern || null == filePath)
         return cwd;

      File file = new File(filePath);
      File dir = file.isDirectory() ? file : file.getParentFile();

      while (null != dir) {
         File marker = new File(dir, rootPattern);
         if (marker.exists())
            return dir.getAbsolutePath();
         dir = dir.getParentFile();
      }

      return cwd;
   }

   /**
    * Adds a source path for a language. The LSP server will be told
    * to index files in these directories.
    *
    * @param languageId the language identifier (e.g., "java")
    * @param path the source path (relative to rootdir or absolute)
    */
   public void addSourcePath(String languageId, String path) {
      sourcePaths.computeIfAbsent(languageId,
         k -> new ArrayList<>()).add(path);
      trace("LSP: added source path for " + languageId + ": " + path);
   }

   /**
    * Returns the configured source paths for a language.
    *
    * @param languageId the language identifier
    * @return the source paths, or empty list
    */
   public List<String> getSourcePaths(String languageId) {
      List<String> paths = sourcePaths.get(languageId);
      return (null != paths)
         ? java.util.Collections.unmodifiableList(paths)
         : java.util.Collections.emptyList();
   }

   /**
    * Starts (or restarts) a server for a given language id, using
    * the configured root directory or current working directory.
    * Used by {@code :lsprestart java} from .javini or ex mode
    * when no file of that type is currently open.
    *
    * @param languageId the language to start (e.g. "java")
    * @return true if the server started successfully
    */
   public boolean startServerForLanguage(String languageId) {
      if (!enabled)
         return false;

      LspServerConfig config = configs.get(languageId);
      if (null == config) {
         trace("LSP: no config for language " + languageId);
         return false;
      }

      if (disabledLanguages.contains(languageId))
         return false;

      // Shut down existing server first
      synchronized (activeClients) {
         LspClient client = activeClients.remove(languageId);
         if (null != client) {
            client.shutdown();
         }
      }

      // Determine root path from rootdir override or cwd
      String rootPath = rootDirOverrides.get(languageId);
      if (null == rootPath)
         rootPath = System.getProperty("user.dir", ".");

      if (!config.isAvailable()) {
         trace("LSP: server binary not found for " + languageId
            + " (" + config.command[0] + ")");
         return false;
      }

      LspClient client = new LspClient(config, rootPath);
      if (null != diagnosticHandler)
         client.setDiagnosticHandler(diagnosticHandler);

      try {
         if (client.start()) {
            synchronized (activeClients) {
               activeClients.put(languageId, client);
            }
            return true;
         }
      } catch (IOException e) {
         trace("LSP: error starting " + languageId + " server: " + e);
      }
      return false;
   }

   // ---------------------------------------------------------------
   // Overlay server support (spell checkers, etc.)
   // ---------------------------------------------------------------

   /**
    * Mapping from file extension to the LSP languageId that
    * harper-ls expects for comment extraction. harper-ls uses
    * standard LSP language identifiers.
    */
   private static final Map<String, String> EXT_TO_LANG_ID;

   static {
      Map<String, String> m = new HashMap<>();
      m.put(".java", "java");
      m.put(".c", "c");
      m.put(".h", "c");
      m.put(".cpp", "cpp");
      m.put(".hpp", "cpp");
      m.put(".cc", "cpp");
      m.put(".cxx", "cpp");
      m.put(".py", "python");
      m.put(".rs", "rust");
      m.put(".go", "go");
      m.put(".js", "javascript");
      m.put(".ts", "typescript");
      m.put(".tsx", "typescriptreact");
      m.put(".jsx", "javascriptreact");
      m.put(".rb", "ruby");
      m.put(".sh", "shellscript");
      m.put(".bash", "shellscript");
      m.put(".lua", "lua");
      m.put(".kt", "kotlin");
      m.put(".swift", "swift");
      m.put(".zig", "zig");
      m.put(".md", "markdown");
      m.put(".typ", "typst");
      m.put(".tex", "latex");
      m.put(".toml", "toml");
      m.put(".pl", "perl");
      m.put(".pm", "perl");
      EXT_TO_LANG_ID = java.util.Collections.unmodifiableMap(m);
   }

   /**
    * Resolves the LSP language ID for overlay servers given a file
    * path and the primary language config's ID (if any).
    *
    * @param filePath the file path
    * @param primaryLangId the language ID from the primary config
    * @return the language ID for overlay notification, or null
    */
   private static String resolveOverlayLanguageId(String filePath,
         String primaryLangId) {
      // Try extension-based lookup first (more specific)
      String ext = LspServerConfig.getExtension(filePath);
      String langId = EXT_TO_LANG_ID.get(ext);
      if (null != langId)
         return langId;

      // Fall back to primary language config ID
      if (null != primaryLangId)
         return primaryLangId;

      // Unknown file type — send as plaintext
      return "plaintext";
   }

   /**
    * Returns running overlay clients that should be notified for
    * the given file. Only returns clients that are already running
    * and initialized.
    *
    * @param filePath the file being operated on
    * @return list of running overlay clients (may be empty)
    */
   private List<LspClient> getRunningOverlayClients(String filePath) {
      if (!enabled)
         return java.util.Collections.emptyList();

      List<LspClient> result = new ArrayList<>();
      synchronized (activeClients) {
         for (Map.Entry<String, LspServerConfig> entry
               : configs.entrySet()) {
            LspServerConfig config = entry.getValue();
            if (!config.overlay || !config.enabled)
               continue;
            if (disabledLanguages.contains(config.languageId))
               continue;
            LspClient client = activeClients.get(config.languageId);
            if (null != client && client.isInitialized())
               result.add(client);
         }
      }
      return result;
   }

   /**
    * Returns all configured overlay server configs.
    *
    * @return list of overlay configs
    */
   List<LspServerConfig> getOverlayConfigs() {
      List<LspServerConfig> result = new ArrayList<>();
      for (LspServerConfig config : configs.values()) {
         if (config.overlay)
            result.add(config);
      }
      return result;
   }

   /**
    * Checks whether an overlay server is running.
    *
    * @param languageId the overlay server's language ID
    * @return true if running and initialized
    */
   public boolean isOverlayRunning(String languageId) {
      synchronized (activeClients) {
         LspClient client = activeClients.get(languageId);
         return null != client && client.isInitialized();
      }
   }
}
