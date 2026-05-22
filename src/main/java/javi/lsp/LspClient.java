package javi.lsp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static history.Tools.trace;

/**
 * LSP client for communicating with a single language server instance.
 *
 * <p>Manages the full lifecycle of an LSP session: initialization,
 * document synchronization, and feature requests. Uses {@link JsonRpc}
 * for the underlying transport protocol.</p>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #start()} - Starts the server process and initializes</li>
 *   <li>{@link #didOpen(String, String, String)} - Notify of opened files</li>
 *   <li>{@link #definition(String, int, int)} - Request go-to-definition</li>
 *   <li>{@link #references(String, int, int)} - Request find references</li>
 *   <li>{@link #completion(String, int, int)} - Request completions</li>
 *   <li>{@link #shutdown()} - Graceful shutdown</li>
 * </ol>
 *
 * <h2>Document Versioning</h2>
 * <p>Each file has a monotonically increasing version number, tracked
 * internally. All {@code didChange} notifications include the current
 * version.</p>
 *
 * <h2>Server Capabilities</h2>
 * <p>After initialization, the server advertises its capabilities. The
 * client checks these before making requests. For example, if the server
 * doesn't support {@code referencesProvider}, calls to
 * {@link #references} return null.</p>
 *
 * <h2>Error Handling</h2>
 * <p>If the server process dies unexpectedly, the client detects this
 * via the I/O layer and marks itself as not initialized. The
 * {@link LspManager} can then attempt to restart.</p>
 *
 * <h2>Document Sync Modes</h2>
 * <p>Supports both full document sync (TextDocumentSyncKind=1) and
 * incremental sync (kind=2). The sync kind is determined by the
 * server's capabilities at initialization. When incremental sync is
 * available, only the changed ranges are sent via
 * {@link #didChangeIncremental}.</p>
 *
 * @see LspManager
 * @see JsonRpc
 * @see LspServerConfig
 */
public final class LspClient implements JsonRpc.MessageHandler {

   private final LspServerConfig config;
   private final String rootPath;
   private Process serverProcess;
   private JsonRpc rpc;
   private boolean initialized;
   private Map<String, Object> serverCapabilities;
   private final Map<String, Integer> documentVersions = new HashMap<>();
   private DiagnosticHandler diagnosticHandler;
   private volatile int crashCount;

   /**
    * Callback interface for receiving diagnostic notifications
    * (errors, warnings) from the language server.
    */
   public interface DiagnosticHandler {
      /**
       * Called when the server publishes diagnostics for a file.
       *
       * @param source the language server identifier (e.g. "java", "harper")
       * @param uri the file URI
       * @param diagnostics list of diagnostic maps from the server
       */
      void onDiagnostics(String source, String uri,
         List<Map<String, Object>> diagnostics);
   }

   /**
    * Creates a new LSP client for the given configuration.
    *
    * @param config the language server configuration
    * @param rootPath the workspace root path (used for rootUri)
    */
   public LspClient(LspServerConfig config, String rootPath) {
      this.config = config;
      this.rootPath = rootPath;
   }

   /**
    * Sets the handler for diagnostic notifications.
    *
    * @param handler the diagnostic handler, may be null
    */
   public void setDiagnosticHandler(DiagnosticHandler handler) {
      this.diagnosticHandler = handler;
   }

   /**
    * Starts the language server process and performs LSP initialization.
    *
    * <p>Steps performed:
    * <ol>
    *   <li>Start the server process with the configured command</li>
    *   <li>Set up JSON-RPC transport over stdin/stdout</li>
    *   <li>Send {@code initialize} request with client capabilities</li>
    *   <li>Send {@code initialized} notification</li>
    *   <li>Store server capabilities for feature checking</li>
    * </ol></p>
    *
    * @return true if initialization succeeded
    * @throws IOException if the server process fails to start
    */
   public boolean start() throws IOException {
      trace("LSP: starting server " + config);

      List<String> cmd = new ArrayList<>(
         java.util.Arrays.asList(config.command));

      // JDT LS needs a unique -data directory per project root to
      // avoid Eclipse workspace conflicts between worktrees that
      // share the same project name.
      if ("java".equals(config.languageId)
            && cmd.get(0).contains("jdtls")) {
         boolean hasData = false;
         for (String arg : cmd) {
            if ("-data".equals(arg)) {
               hasData = true;
               break;
            }
         }
         if (!hasData) {
            String dataDir = rootPath + File.separator + ".jdtls-data";
            cmd.add("-data");
            cmd.add(dataDir);
            trace("LSP: auto-added -data " + dataDir);
         }
      }

      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(false);

      // Set working directory to root path
      File rootDir = new File(rootPath);
      if (rootDir.isDirectory()) {
         pb.directory(rootDir);
      }

      serverProcess = pb.start();

      rpc = new JsonRpc(serverProcess.getInputStream(),
         serverProcess.getOutputStream());
      rpc.setMessageHandler(this);
      rpc.startReading();

      // Send initialize request
      Map<String, Object> initParams = buildInitializeParams();
      Map<String, Object> result = rpc.sendRequestSync("initialize",
         initParams);

      if (null == result) {
         trace("LSP: initialize failed - no response");
         stop();
         return false;
      }

      // Extract capabilities
      Object caps = result.get("capabilities");
      if (caps instanceof Map) {
         @SuppressWarnings("unchecked")
         Map<String, Object> capsMap = (Map<String, Object>) caps;
         serverCapabilities = capsMap;
      } else {
         // Some servers wrap capabilities in result
         serverCapabilities = result;
      }
      trace("LSP: server capabilities: " + serverCapabilities);

      // Send initialized notification
      rpc.sendNotification("initialized", new HashMap<>());
      initialized = true;
      crashCount = 0;
      trace("LSP: initialization complete for " + config.languageId);

      // Send workspace configuration to reinforce init options
      sendDidChangeConfiguration();

      // Monitor server process for unexpected exits
      startProcessMonitor();
      return true;
   }

   /**
    * Builds the initialize request parameters.
    *
    * <p>Advertises the client's capabilities to the server. We declare
    * support for the features we can actually handle.</p>
    *
    * @return the initialize params map
    */
   private Map<String, Object> buildInitializeParams() {
      Map<String, Object> params = new HashMap<>();

      // Process ID
      params.put("processId",
         Long.valueOf(ProcessHandle.current().pid()));

      // Root URI
      String rootUri = pathToUri(rootPath);
      params.put("rootUri", rootUri);
      params.put("rootPath", rootPath);

      // Client capabilities
      Map<String, Object> capabilities = new HashMap<>();

      // Text document capabilities
      Map<String, Object> textDoc = new HashMap<>();

      // Synchronization
      Map<String, Object> sync = new HashMap<>();
      sync.put("dynamicRegistration", Boolean.FALSE);
      sync.put("didSave", Boolean.TRUE);
      textDoc.put("synchronization", sync);

      // Completion
      Map<String, Object> completion = new HashMap<>();
      completion.put("dynamicRegistration", Boolean.FALSE);
      Map<String, Object> completionItem = new HashMap<>();
      completionItem.put("snippetSupport", Boolean.FALSE);
      completion.put("completionItem", completionItem);
      textDoc.put("completion", completion);

      // Hover
      Map<String, Object> hover = new HashMap<>();
      hover.put("dynamicRegistration", Boolean.FALSE);
      textDoc.put("hover", hover);

      // Definition
      Map<String, Object> definition = new HashMap<>();
      definition.put("dynamicRegistration", Boolean.FALSE);
      textDoc.put("definition", definition);

      // References
      Map<String, Object> references = new HashMap<>();
      references.put("dynamicRegistration", Boolean.FALSE);
      textDoc.put("references", references);

      // Diagnostics (publishDiagnostics)
      Map<String, Object> publishDiags = new HashMap<>();
      publishDiags.put("relatedInformation", Boolean.TRUE);
      textDoc.put("publishDiagnostics", publishDiags);

      capabilities.put("textDocument", textDoc);

      // Workspace capabilities
      Map<String, Object> workspace = new HashMap<>();
      workspace.put("applyEdit", Boolean.FALSE);

      // Advertise workspace/symbol support so servers enable it
      Map<String, Object> wsSymbol = new HashMap<>();
      wsSymbol.put("dynamicRegistration", Boolean.FALSE);
      workspace.put("symbol", wsSymbol);

      // Advertise didChangeConfiguration support
      Map<String, Object> wsConfig = new HashMap<>();
      wsConfig.put("dynamicRegistration", Boolean.FALSE);
      workspace.put("didChangeConfiguration", wsConfig);

      capabilities.put("workspace", workspace);

      params.put("capabilities", capabilities);

      // Client info
      Map<String, Object> clientInfo = new HashMap<>();
      clientInfo.put("name", "javi");
      clientInfo.put("version", "1.0");
      params.put("clientInfo", clientInfo);

      // Initialization options — language-specific settings
      Map<String, Object> initOptions = buildInitializationOptions();
      if (null != initOptions) {
         params.put("initializationOptions", initOptions);
      }

      return params;
   }

   /**
    * Builds language-specific initialization options.
    *
    * <p>For Java/jdtls, disables Gradle import (which fails in worktrees
    * when directory name doesn't match settings.gradle project name) and
    * configures source paths for direct file-based indexing.</p>
    *
    * @return the initializationOptions map, or null if none needed
    */
   private Map<String, Object> buildInitializationOptions() {
      if (!"java".equals(config.languageId))
         return null;

      Map<String, Object> options = new HashMap<>();

      // jdtls reads settings from initializationOptions.settings
      Map<String, Object> settings = new HashMap<>();
      Map<String, Object> java = new HashMap<>();

      // Disable Gradle import — avoids "Invalid project description"
      // error when the directory name differs from rootProject.name
      Map<String, Object> importSettings = new HashMap<>();
      Map<String, Object> gradle = new HashMap<>();
      gradle.put("enabled", Boolean.FALSE);
      importSettings.put("gradle", gradle);
      Map<String, Object> maven = new HashMap<>();
      maven.put("enabled", Boolean.FALSE);
      importSettings.put("maven", maven);
      java.put("import", importSettings);

      // Configure source paths for direct indexing
      Map<String, Object> project = new HashMap<>();
      List<String> sourcePaths = new ArrayList<>();

      // Use user-configured source paths if available
      List<String> userPaths =
         LspManager.getInstance().getSourcePaths("java");
      if (!userPaths.isEmpty()) {
         sourcePaths.addAll(userPaths);
      } else {
         // Auto-detect common source directories
         File srcDir = new File(rootPath, "src/main/java");
         if (srcDir.isDirectory())
            sourcePaths.add("src/main/java");
         File histDir = new File(rootPath, "src/history/java");
         if (histDir.isDirectory())
            sourcePaths.add("src/history/java");
      }
      if (!sourcePaths.isEmpty())
         project.put("sourcePaths", sourcePaths);
      project.put("outputPath", "build/classes/java/main");

      // Include jar libraries
      List<String> libs = new ArrayList<>();
      libs.add("lib/**/*.jar");
      project.put("referencedLibraries", libs);
      java.put("project", project);

      settings.put("java", java);
      options.put("settings", settings);

      return options;
   }

   /**
    * Sends workspace/didChangeConfiguration after initialization.
    *
    * <p>Reinforces initialization options — some servers only read
    * settings from this notification rather than initializationOptions.</p>
    *
    * @throws IOException if sending fails
    */
   private void sendDidChangeConfiguration() throws IOException {
      if (!"java".equals(config.languageId))
         return;

      Map<String, Object> settings = new HashMap<>();
      Map<String, Object> java = new HashMap<>();

      // Disable build tool imports
      Map<String, Object> importSettings = new HashMap<>();
      Map<String, Object> gradle = new HashMap<>();
      gradle.put("enabled", Boolean.FALSE);
      importSettings.put("gradle", gradle);
      Map<String, Object> maven = new HashMap<>();
      maven.put("enabled", Boolean.FALSE);
      importSettings.put("maven", maven);
      java.put("import", importSettings);

      settings.put("java", java);

      Map<String, Object> params = new HashMap<>();
      params.put("settings", settings);

      rpc.sendNotification("workspace/didChangeConfiguration", params);
      trace("LSP: sent didChangeConfiguration for " + config.languageId);
   }

   /**
    * Notifies the server that a text document was opened.
    *
    * @param filePath the absolute file path
    * @param languageId the language identifier (e.g. "java")
    * @param content the full text content of the file
    * @throws IOException if sending the notification fails
    */
   public void didOpen(String filePath, String languageId, String content)
         throws IOException {
      if (!initialized)
         return;

      String uri = pathToUri(filePath);
      documentVersions.put(uri, Integer.valueOf(1));

      Map<String, Object> textDoc = new HashMap<>();
      textDoc.put("uri", uri);
      textDoc.put("languageId", languageId);
      textDoc.put("version", Integer.valueOf(1));
      textDoc.put("text", content);

      Map<String, Object> params = new HashMap<>();
      params.put("textDocument", textDoc);

      rpc.sendNotification("textDocument/didOpen", params);
   }

   /**
    * Notifies the server that a text document was changed.
    *
    * <p>Sends the full document content (TextDocumentSyncKind.Full).
    * Increments the version number for the document.</p>
    *
    * @param filePath the absolute file path
    * @param content the new full text content
    * @throws IOException if sending the notification fails
    */
   public void didChange(String filePath, String content)
         throws IOException {
      if (!initialized)
         return;

      String uri = pathToUri(filePath);
      int version = documentVersions.getOrDefault(uri, Integer.valueOf(0))
         .intValue() + 1;
      documentVersions.put(uri, Integer.valueOf(version));

      Map<String, Object> textDoc = new HashMap<>();
      textDoc.put("uri", uri);
      textDoc.put("version", Integer.valueOf(version));

      Map<String, Object> contentChange = new HashMap<>();
      contentChange.put("text", content);

      List<Object> changes = new ArrayList<>();
      changes.add(contentChange);

      Map<String, Object> params = new HashMap<>();
      params.put("textDocument", textDoc);
      params.put("contentChanges", changes);

      rpc.sendNotification("textDocument/didChange", params);
   }

   /**
    * Notifies the server of incremental content changes.
    *
    * <p>Sends only the changed ranges (TextDocumentSyncKind.Incremental).
    * Each {@link LspChangeEvent} maps to one entry in the LSP
    * {@code contentChanges} array, applied sequentially.</p>
    *
    * @param filePath the absolute file path
    * @param events the list of change events to send
    * @throws IOException if sending the notification fails
    */
   public void didChangeIncremental(String filePath,
         List<LspChangeEvent> events) throws IOException {
      if (!initialized || events.isEmpty())
         return;

      String uri = pathToUri(filePath);
      int version = documentVersions.getOrDefault(uri, Integer.valueOf(0))
         .intValue() + 1;
      documentVersions.put(uri, Integer.valueOf(version));

      Map<String, Object> textDoc = new HashMap<>();
      textDoc.put("uri", uri);
      textDoc.put("version", Integer.valueOf(version));

      List<Object> contentChanges = new ArrayList<>();
      for (LspChangeEvent evt : events) {
         contentChanges.add(evt.toContentChange());
      }

      Map<String, Object> params = new HashMap<>();
      params.put("textDocument", textDoc);
      params.put("contentChanges", contentChanges);

      rpc.sendNotification("textDocument/didChange", params);
   }

   /**
    * Returns the text document sync kind from server capabilities.
    *
    * <p>Parses the {@code textDocumentSync} capability, which may be
    * a plain integer or a map with a {@code change} field.</p>
    *
    * @return 0 = None, 1 = Full, 2 = Incremental
    */
   int getTextDocumentSyncKind() {
      if (null == serverCapabilities)
         return 1;
      Object syncObj = serverCapabilities.get("textDocumentSync");
      if (syncObj instanceof Number)
         return ((Number) syncObj).intValue();
      if (syncObj instanceof Map) {
         @SuppressWarnings("unchecked")
         Map<String, Object> syncMap = (Map<String, Object>) syncObj;
         Object change = syncMap.get("change");
         if (change instanceof Number)
            return ((Number) change).intValue();
      }
      return 1;
   }

   /**
    * Notifies the server that a text document was closed.
    *
    * @param filePath the absolute file path
    * @throws IOException if sending the notification fails
    */
   public void didClose(String filePath) throws IOException {
      if (!initialized)
         return;

      String uri = pathToUri(filePath);
      documentVersions.remove(uri);

      Map<String, Object> textDoc = new HashMap<>();
      textDoc.put("uri", uri);

      Map<String, Object> params = new HashMap<>();
      params.put("textDocument", textDoc);

      rpc.sendNotification("textDocument/didClose", params);
   }

   /**
    * Notifies the server that a text document was saved.
    *
    * @param filePath the absolute file path
    * @throws IOException if sending the notification fails
    */
   public void didSave(String filePath) throws IOException {
      if (!initialized)
         return;

      String uri = pathToUri(filePath);

      Map<String, Object> textDoc = new HashMap<>();
      textDoc.put("uri", uri);

      Map<String, Object> params = new HashMap<>();
      params.put("textDocument", textDoc);

      rpc.sendNotification("textDocument/didSave", params);
   }

   /**
    * Requests go-to-definition for a position in a file.
    *
    * <p>Returns a list of location maps, each containing "uri" and
    * "range" keys. Returns null if the server doesn't support definitions
    * or the request fails.</p>
    *
    * @param filePath the absolute file path
    * @param line the 0-based line number
    * @param character the 0-based character offset
    * @return list of location results, or null
    * @throws IOException if the request fails
    */
   @SuppressWarnings("unchecked")
   public List<Map<String, Object>> definition(String filePath,
         int line, int character) throws IOException {
      if (!initialized)
         return null;

      Map<String, Object> params = buildTextDocumentPositionParams(
         filePath, line, character);
      Map<String, Object> result = rpc.sendRequestSync(
         "textDocument/definition", params);

      return extractLocations(result);
   }

   /**
    * Requests find-references for a position in a file.
    *
    * @param filePath the absolute file path
    * @param line the 0-based line number
    * @param character the 0-based character offset
    * @return list of location results, or null
    * @throws IOException if the request fails
    */
   @SuppressWarnings("unchecked")
   public List<Map<String, Object>> references(String filePath,
         int line, int character) throws IOException {
      if (!initialized)
         return null;

      Map<String, Object> params = buildTextDocumentPositionParams(
         filePath, line, character);

      Map<String, Object> context = new HashMap<>();
      context.put("includeDeclaration", Boolean.TRUE);
      params.put("context", context);

      Map<String, Object> result = rpc.sendRequestSync(
         "textDocument/references", params);

      return extractLocations(result);
   }

   /**
    * Requests hover information for a position in a file.
    *
    * @param filePath the absolute file path
    * @param line the 0-based line number
    * @param character the 0-based character offset
    * @return the hover content string, or null
    * @throws IOException if the request fails
    */
   @SuppressWarnings("unchecked")
   public String hover(String filePath, int line, int character)
         throws IOException {
      if (!initialized)
         return null;

      Map<String, Object> params = buildTextDocumentPositionParams(
         filePath, line, character);
      Map<String, Object> result = rpc.sendRequestSync(
         "textDocument/hover", params);

      if (null == result)
         return null;

      // Hover result has "contents" which can be a string,
      // MarkupContent, or array
      Object contents = result.get("contents");
      if (null == contents)
         contents = result.get("result");
      if (null == contents)
         return null;

      if (contents instanceof String) {
         return (String) contents;
      }
      if (contents instanceof Map) {
         Map<String, Object> mc = (Map<String, Object>) contents;
         Object value = mc.get("value");
         return null != value ? value.toString() : contents.toString();
      }
      return contents.toString();
   }

   /**
    * Requests code completions for a position in a file.
    *
    * <p>Returns a list of completion item maps. Each item has at minimum
    * a "label" and optionally "detail", "kind", "insertText".</p>
    *
    * @param filePath the absolute file path
    * @param line the 0-based line number
    * @param character the 0-based character offset
    * @return list of completion item maps, or null
    * @throws IOException if the request fails
    */
   @SuppressWarnings("unchecked")
   public List<Map<String, Object>> completion(String filePath,
         int line, int character) throws IOException {
      if (!initialized)
         return null;

      Map<String, Object> params = buildTextDocumentPositionParams(
         filePath, line, character);
      Map<String, Object> result = rpc.sendRequestSync(
         "textDocument/completion", params);

      if (null == result)
         return null;

      // Completion result can be CompletionList (has "items") or
      // direct array (wrapped in "result")
      Object items = result.get("items");
      if (null == items)
         items = result.get("result");
      if (items instanceof List)
         return (List<Map<String, Object>>) items;

      return null;
   }

   /**
    * Searches for workspace symbols matching a query string.
    *
    * @param query the symbol name to search for
    * @return list of symbol information maps, or null
    * @throws IOException if the request fails
    */
   @SuppressWarnings("unchecked")
   public List<Map<String, Object>> workspaceSymbol(String query)
         throws IOException {
      return workspaceSymbol(query, JsonRpc.DEFAULT_TIMEOUT_SECONDS);
   }

   /**
    * Searches for workspace symbols with a custom timeout.
    *
    * @param query the symbol name to search for
    * @param timeoutSeconds timeout for the request
    * @return list of symbol information maps, or null
    * @throws IOException if the request fails
    */
   @SuppressWarnings("unchecked")
   public List<Map<String, Object>> workspaceSymbol(String query,
         int timeoutSeconds) throws IOException {
      if (!initialized)
         return null;

      Map<String, Object> params = new HashMap<>();
      params.put("query", query);
      Map<String, Object> result = rpc.sendRequestSync(
         "workspace/symbol", params, timeoutSeconds);

      if (null == result)
         return null;

      Object syms = result.get("result");
      if (syms instanceof List)
         return (List<Map<String, Object>>) syms;
      return null;
   }

   /**
    * Builds the standard TextDocumentPositionParams for LSP requests.
    *
    * @param filePath the absolute file path
    * @param line the 0-based line number
    * @param character the 0-based character offset
    * @return the params map
    */
   private Map<String, Object> buildTextDocumentPositionParams(
         String filePath, int line, int character) {
      String uri = pathToUri(filePath);

      Map<String, Object> textDoc = new HashMap<>();
      textDoc.put("uri", uri);

      Map<String, Object> position = new HashMap<>();
      position.put("line", Integer.valueOf(line));
      position.put("character", Integer.valueOf(character));

      Map<String, Object> params = new HashMap<>();
      params.put("textDocument", textDoc);
      params.put("position", position);
      return params;
   }

   /**
    * Extracts a list of Location maps from an LSP response.
    *
    * <p>Handles the variety of response formats: single Location,
    * array of Locations, LocationLink array, or wrapped in "result".</p>
    *
    * @param result the raw response map
    * @return list of location maps, or null if empty/invalid
    */
   @SuppressWarnings("unchecked")
   private List<Map<String, Object>> extractLocations(
         Map<String, Object> result) {
      if (null == result)
         return null;

      // May be wrapped in "result" key
      Object locs = result.get("result");
      if (null == locs) {
         // The result map itself might be the location
         if (result.containsKey("uri"))
            locs = result;
      }

      if (locs instanceof List) {
         return (List<Map<String, Object>>) locs;
      }
      if (locs instanceof Map) {
         List<Map<String, Object>> list = new ArrayList<>();
         list.add((Map<String, Object>) locs);
         return list;
      }

      return null;
   }

   /**
    * Gracefully shuts down the language server.
    *
    * <p>Sends the {@code shutdown} request, waits for acknowledgment,
    * then sends the {@code exit} notification and destroys the process.</p>
    */
   public void shutdown() {
      if (!initialized)
         return;

      trace("LSP: shutting down " + config.languageId);
      initialized = false;

      try {
         // Use a short 3-second timeout for shutdown to avoid hanging
         rpc.sendRequest("shutdown", null)
            .get(3, TimeUnit.SECONDS);
         rpc.sendNotification("exit", null);
      } catch (java.util.concurrent.TimeoutException e) {
         trace("LSP: shutdown timed out after 3s, force-killing");
      } catch (Exception e) {
         trace("LSP: error during shutdown: " + e);
      }
      stop();
   }

   /**
    * Forcefully stops the server process and reader thread.
    *
    * <p>Waits up to 5 seconds for the process to actually terminate
    * after {@code destroyForcibly()}, ensuring file handles are
    * released before this method returns.</p>
    */
   void stop() {
      initialized = false;
      if (null != rpc) {
         rpc.stopReading();
      }
      if (null != serverProcess) {
         serverProcess.destroyForcibly();
         try {
            serverProcess.waitFor(5, TimeUnit.SECONDS);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }
         serverProcess = null;
      }
   }

   /**
    * Returns whether this client is initialized and ready.
    *
    * @return true if the LSP session is active
    */
   public boolean isInitialized() {
      return initialized;
   }

   /**
    * Returns the language server configuration.
    *
    * @return the server config
    */
   public LspServerConfig getConfig() {
      return config;
   }

   /**
    * Returns the workspace root path this client was started with.
    *
    * @return the root path
    */
   public String getRootPath() {
      return rootPath;
   }

   /**
    * Returns the server capabilities map from initialization.
    *
    * @return the capabilities, or null if not initialized
    */
   public Map<String, Object> getServerCapabilities() {
      return serverCapabilities;
   }

   /**
    * Returns the number of times this server has crashed.
    *
    * @return the crash count
    */
   public int getCrashCount() {
      return crashCount;
   }

   /**
    * Starts a daemon thread that monitors the server process.
    * If the process exits while we still expect it to be running,
    * marks the client as not initialized so the manager can restart.
    */
   private void startProcessMonitor() {
      Thread monitor = new Thread(() -> {
         try {
            int exitCode = serverProcess.waitFor();
            if (initialized) {
               initialized = false;
               crashCount++;
               trace("LSP: " + config.languageId
                  + " server exited unexpectedly (code " + exitCode
                  + ", crash #" + crashCount + ")");
            }
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      }, "lsp-monitor-" + config.languageId);
      monitor.setDaemon(true);
      monitor.start();
   }

   // --- MessageHandler implementation ---

   /**
    * Handles notifications from the language server.
    *
    * <p>Currently handles:
    * <ul>
    *   <li>{@code textDocument/publishDiagnostics} - Error/warning reports</li>
    *   <li>{@code window/logMessage} - Server log messages</li>
    *   <li>{@code window/showMessage} - Server UI messages</li>
    * </ul></p>
    */
   @Override
   @SuppressWarnings("unchecked")
   public void onNotification(String method, Map<String, Object> params) {
      trace("LSP notification: " + method);
      switch (method) {
         case "textDocument/publishDiagnostics":
            handleDiagnostics(params);
            break;
         case "window/logMessage":
            if (null != params)
               trace("LSP server log: " + params.get("message"));
            break;
         case "window/showMessage":
            if (null != params)
               trace("LSP server message: " + params.get("message"));
            break;
         default:
            trace("LSP: unhandled notification: " + method);
      }
   }

   /**
    * Handles server-initiated requests.
    *
    * <p>Currently handles:
    * <ul>
    *   <li>{@code window/workDoneProgress/create} - Progress reporting</li>
    *   <li>{@code client/registerCapability} - Dynamic registration</li>
    * </ul></p>
    */
   @Override
   public Map<String, Object> onRequest(int id, String method,
         Map<String, Object> params) {
      trace("LSP server request: " + method);
      // Most server requests just need an empty acknowledgment
      return new HashMap<>();
   }

   /**
    * Processes a publishDiagnostics notification.
    *
    * @param params the notification parameters containing uri and diagnostics
    */
   @SuppressWarnings("unchecked")
   private void handleDiagnostics(Map<String, Object> params) {
      if (null == params)
         return;

      String uri = (String) params.get("uri");
      Object diagsObj = params.get("diagnostics");

      if (null != diagnosticHandler && diagsObj instanceof List) {
         List<Map<String, Object>> diags =
            (List<Map<String, Object>>) diagsObj;
         diagnosticHandler.onDiagnostics(config.languageId, uri, diags);
      }
   }

   // --- Utility methods ---

   /**
    * Converts an absolute file path to a file:// URI.
    *
    * @param path the absolute file path
    * @return the file URI
    */
   static String pathToUri(String path) {
      if (null == path)
         return "";
      if (path.startsWith("file://"))
         return path;
      // Ensure path starts with /
      if (!path.startsWith("/"))
         path = "/" + path;
      return "file://" + path.replace(" ", "%20");
   }

   /**
    * Converts a file:// URI back to an absolute file path.
    *
    * @param uri the file URI
    * @return the absolute file path
    */
   static String uriToPath(String uri) {
      if (null == uri)
         return "";
      if (uri.startsWith("file://"))
         uri = uri.substring("file://".length());
      return uri.replace("%20", " ");
   }
}
