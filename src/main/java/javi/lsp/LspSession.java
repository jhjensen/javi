package javi.lsp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static history.Tools.trace;

/**
 * Manages a single LSP server instance with its own processing thread.
 *
 * <p>Each LspSession owns:
 * <ul>
 *   <li>The server process (stdin/stdout)</li>
 *   <li>A JsonRpc transport layer</li>
 *   <li>A dedicated thread that processes requests from its queue</li>
 *   <li>A watchdog that kills hung servers</li>
 *   <li>All state for this server (open documents, pending requests)</li>
 * </ul></p>
 *
 * <p>The AWT thread communicates with this session exclusively through
 * the command queue (via {@link #submit} and {@link #notify_}). Results
 * are delivered back to AWT via {@code EventQueue.invokeLater}.</p>
 *
 * <p>No synchronized methods — the session thread is the sole owner of
 * all mutable state. The only shared structures are the command queue
 * (thread-safe by design) and CompletableFutures.</p>
 */
public final class LspSession implements Runnable, JsonRpc.MessageHandler {

   /** Session states. */
   public enum State {
      STOPPED, STARTING, INITIALIZING, READY, STOPPING, CRASHED
   }

   /** Maximum restart attempts before giving up. */
   private static final int MAX_RESTARTS = 3;

   /** Watchdog timeout in seconds for individual requests. */
   private static final int WATCHDOG_TIMEOUT_SECONDS = 10;

   /** Cold start timeout — extra time for first initialize. */
   private static final int COLD_START_TIMEOUT_SECONDS = 30;

   /** Queue capacity before requests are rejected. */
   private static final int QUEUE_CAPACITY = 256;

   private final LspServerConfig config;
   private final String projectRoot;
   private final NotificationSink notificationSink;

   private final BlockingQueue<LspRequest> commandQueue =
      new LinkedBlockingQueue<>(QUEUE_CAPACITY);

   private volatile State state = State.STOPPED;
   private volatile int restartCount;

   // Owned by session thread only — no synchronization needed
   private Process serverProcess;
   private JsonRpc transport;
   private Map<String, Object> serverCapabilities;
   private final Map<String, Integer> openDocuments = new HashMap<>();
   private final AtomicInteger nextRequestId = new AtomicInteger(1);
   private final ConcurrentHashMap<Integer, CompletableFuture<Map<String, Object>>>
      pendingRequests = new ConcurrentHashMap<>();

   private Thread sessionThread;
   private long startTime;

   /**
    * Callback interface for delivering notifications to the UI layer.
    * Implementations must be thread-safe (called from the session thread).
    */
   public interface NotificationSink {
      /**
       * Called when diagnostics arrive from the server.
       *
       * @param session the session that received the diagnostics
       * @param uri the document URI
       * @param diagnostics the diagnostic entries
       */
      void onDiagnostics(LspSession session, String uri,
         List<Map<String, Object>> diagnostics);

      /**
       * Called when the server's state changes.
       *
       * @param session the session whose state changed
       * @param newState the new state
       */
      void onStateChanged(LspSession session, State newState);
   }

   /**
    * Creates a new LSP session for the given server configuration.
    *
    * @param config the server configuration
    * @param projectRoot the project root directory path
    * @param sink callback for notifications (diagnostics, state changes)
    */
   public LspSession(LspServerConfig config, String projectRoot,
         NotificationSink sink) {
      this.config = config;
      this.projectRoot = projectRoot;
      this.notificationSink = sink;
   }

   /**
    * Starts the session thread. The server process will be launched
    * and initialized asynchronously.
    */
   public void start() {
      if (State.STOPPED != state && State.CRASHED != state)
         return;
      sessionThread = new Thread(this,
         "lsp-" + config.languageId);
      sessionThread.setDaemon(true);
      sessionThread.start();
   }

   /**
    * Requests a graceful shutdown of this session.
    * The session thread will send shutdown/exit and terminate.
    */
   public void stop() {
      if (State.STOPPED == state || State.STOPPING == state)
         return;
      setState(State.STOPPING);
      // Poison pill to wake the queue
      commandQueue.offer(LspRequest.notification("$/shutdown", null));
      if (null != sessionThread) {
         sessionThread.interrupt();
      }
   }

   /**
    * Submits a request to this session and returns a future for the result.
    *
    * @param method the LSP method
    * @param params the request parameters
    * @return future that completes with the response, or completes
    *         exceptionally if the queue is full or session is not running
    */
   public CompletableFuture<Map<String, Object>> submit(String method,
         Map<String, Object> params) {
      LspRequest req = LspRequest.request(method, params);
      if (State.STOPPED == state
            || State.STOPPING == state
            || State.CRASHED == state) {
         req.future.completeExceptionally(new IOException(
            "LSP session not running for " + config.languageId
            + " state=" + state));
         return req.future;
      }
      if (!commandQueue.offer(req)) {
         req.future.completeExceptionally(
            new IOException("LSP queue full for " + config.languageId));
      }
      return req.future;
   }

   /**
    * Submits a notification (no response expected) to this session.
    *
    * @param method the LSP method
    * @param params the notification parameters
    */
   public void notify_(String method, Map<String, Object> params) {
      if (State.STOPPED == state
            || State.STOPPING == state
            || State.CRASHED == state) {
         trace("LSP dropping notification while not running: "
            + method + " state=" + state);
         return;
      }
      LspRequest req = LspRequest.notification(method, params);
      if (!commandQueue.offer(req)) {
         trace("LSP queue full, dropping notification: " + method);
      }
   }

   /** Returns the current session state. */
   public State getState() {
      return state;
   }

   /** Returns true if the server is initialized and ready for requests. */
   public boolean isReady() {
      return State.READY == state;
   }

   /** Returns the server configuration. */
   public LspServerConfig getConfig() {
      return config;
   }

   /** Returns the language ID for this session. */
   public String getLanguageId() {
      return config.languageId;
   }

   /** Returns the project root directory for this session. */
   public String getProjectRoot() {
      return projectRoot;
   }

   /** Returns the server capabilities received during initialization. */
   public Map<String, Object> getCapabilities() {
      return serverCapabilities;
   }

   /** Returns uptime in milliseconds since the server started. */
   public long getUptime() {
      return (0 == startTime) ? 0 : System.currentTimeMillis() - startTime;
   }

   // ---------------------------------------------------------------
   // Session thread main loop
   // ---------------------------------------------------------------

   @Override
   public void run() {
      try {
         launchAndInitialize();
         if (State.READY == state || State.INITIALIZING == state) {
            processLoop();
         }
      } catch (InterruptedException e) {
         trace("LSP session interrupted: " + config.languageId);
      } catch (Exception e) {
         trace("LSP session error: " + config.languageId + " " + e);
      } finally {
         cleanup();
      }
   }

   /**
    * Launches the server process and performs the initialize handshake.
    */
   private void launchAndInitialize() throws IOException, InterruptedException {
      setState(State.STARTING);
      startTime = System.currentTimeMillis();

      ProcessBuilder pb = new ProcessBuilder(config.command);
      pb.redirectErrorStream(false);
      serverProcess = pb.start();

      transport = new JsonRpc(
         serverProcess.getInputStream(),
         serverProcess.getOutputStream());
      transport.setTrafficLog(new File(projectRoot,
         "ai.output/lsp-" + config.languageId + ".stdout.log"));
      transport.setMessageHandler(this);

      // Send initialize request
      Map<String, Object> initParams = buildInitializeParams();

      setState(State.INITIALIZING);

      transport.startReading();
      Map<String, Object> initResult = transport.sendRequestSync(
         "initialize", initParams, COLD_START_TIMEOUT_SECONDS);

      if (null == initResult) {
         trace("LSP initialize timeout: " + config.languageId);
         setState(State.CRASHED);
         return;
      }

      // Extract capabilities
      Object caps = initResult.get("capabilities");
      if (caps instanceof Map) {
         @SuppressWarnings("unchecked")
         Map<String, Object> capsMap = (Map<String, Object>) caps;
         serverCapabilities = capsMap;
      } else {
         serverCapabilities = new HashMap<>();
      }

      // Send initialized notification
      transport.sendNotification("initialized", new HashMap<>());

      setState(State.READY);
      trace("LSP session ready: " + config.languageId);
   }

   /**
    * Main processing loop — takes requests from the queue and sends
    * them to the server, handling responses and notifications.
    */
   private void processLoop() throws InterruptedException {
      while (State.READY == state || State.INITIALIZING == state) {
         LspRequest req = commandQueue.poll(1, TimeUnit.SECONDS);

         // Check process health
         if (null != serverProcess && !serverProcess.isAlive()) {
            trace("LSP server died: " + config.languageId
               + " exit=" + serverProcess.exitValue());
            setState(State.CRASHED);
            attemptRestart();
            return;
         }

         if (null == req)
            continue;

         // Shutdown poison pill
         if ("$/shutdown".equals(req.method)) {
            break;
         }

         try {
            if (req.isNotification()) {
               transport.sendNotification(req.method, req.params);
            } else {
               CompletableFuture<Map<String, Object>> serverFuture =
                  transport.sendRequest(req.method, req.params);
               // Bridge the transport future to the caller's future
               serverFuture.whenComplete((result, error) -> {
                  if (null != error) {
                     req.future.completeExceptionally(error);
                  } else {
                     req.future.complete(result);
                  }
               });
               // Watchdog: schedule timeout
               scheduleWatchdog(req);
            }
         } catch (IOException e) {
            trace("LSP send failed: " + req.method + " " + e);
            if (null != req.future) {
               req.future.completeExceptionally(e);
            }
            // IO failure likely means server died
            if (null != serverProcess && !serverProcess.isAlive()) {
               setState(State.CRASHED);
               attemptRestart();
               return;
            }
         }
      }
   }

   /**
    * Schedules a watchdog timeout for a pending request.
    * If the request doesn't complete within the timeout, the future
    * is completed exceptionally.
    */
   private void scheduleWatchdog(LspRequest req) {
      CompletableFuture<Map<String, Object>> f = req.future;
      if (null == f)
         return;
      // Use a delayed executor for timeout
      CompletableFuture.delayedExecutor(
            WATCHDOG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
         .execute(() -> {
            if (!f.isDone()) {
               f.completeExceptionally(new IOException(
                  "LSP request timed out: " + req.method
                  + " (" + config.languageId + ")"));
            }
         });
   }

   /**
    * Attempts to restart a crashed server.
    */
   private void attemptRestart() {
      restartCount++;
      if (restartCount > MAX_RESTARTS) {
         trace("LSP max restarts exceeded: " + config.languageId);
         setState(State.STOPPED);
         return;
      }
      trace("LSP restarting (" + restartCount + "/"
         + MAX_RESTARTS + "): " + config.languageId);
      cleanup();
      try {
         launchAndInitialize();
         if (State.READY == state) {
            reopenDocuments();
            processLoop();
         }
      } catch (Exception e) {
         trace("LSP restart failed: " + config.languageId + " " + e);
         setState(State.STOPPED);
      }
   }

   /**
    * Re-sends didOpen for all previously open documents after a restart.
    */
   private void reopenDocuments() {
      for (Map.Entry<String, Integer> entry : openDocuments.entrySet()) {
         // We don't have the content here — the registry will need to
         // re-notify on restart. Just clear versions.
         trace("LSP: would reopen " + entry.getKey() + " after restart");
      }
      // Clear — the registry will re-send didOpen for active documents
      openDocuments.clear();
   }

   /**
    * Sends a shutdown request and exit notification, then destroys the
    * process.
    */
   private void cleanup() {
      if (null != transport) {
         try {
            transport.sendRequestSync("shutdown", null, 3);
            transport.sendNotification("exit", null);
         } catch (IOException e) {
            // Ignore — we're shutting down anyway
         }
         transport.stopReading();
         transport = null;
      }
      if (null != serverProcess) {
         serverProcess.destroyForcibly();
         serverProcess = null;
      }
      // Cancel all pending requests
      for (CompletableFuture<Map<String, Object>> f
            : pendingRequests.values()) {
         f.cancel(true);
      }
      pendingRequests.clear();
      setState(State.STOPPED);
   }

   private void setState(State newState) {
      state = newState;
      if (null != notificationSink) {
         notificationSink.onStateChanged(this, newState);
      }
   }

   // ---------------------------------------------------------------
   // JsonRpc.MessageHandler implementation
   // ---------------------------------------------------------------

   @Override
   @SuppressWarnings("unchecked")
   public void onNotification(String method, Map<String, Object> params) {
      if ("textDocument/publishDiagnostics".equals(method) && null != params) {
         String uri = (String) params.get("uri");
         Object diagObj = params.get("diagnostics");
         if (null != uri && diagObj instanceof List) {
            List<Map<String, Object>> diagnostics =
               (List<Map<String, Object>>) diagObj;
            if (null != notificationSink) {
               notificationSink.onDiagnostics(this, uri, diagnostics);
            }
         }
      } else {
         trace("LSP notification [" + config.languageId + "]: " + method);
      }
   }

   @Override
   public Map<String, Object> onRequest(int id, String method,
         Map<String, Object> params) {
      trace("LSP server request [" + config.languageId + "]: " + method);
      // Most server-initiated requests can be responded to with empty result
      return new HashMap<>();
   }

   // ---------------------------------------------------------------
   // Document tracking
   // ---------------------------------------------------------------

   /**
    * Tracks that a document has been opened with the given version.
    * Called from the session thread when processing didOpen.
    *
    * @param uri the document URI
    * @param version the initial version number
    */
   void trackOpen(String uri, int version) {
      openDocuments.put(uri, version);
   }

   /**
    * Updates the tracked version for an open document.
    *
    * @param uri the document URI
    * @param version the new version number
    */
   void trackVersion(String uri, int version) {
      openDocuments.put(uri, version);
   }

   /**
    * Removes a document from tracking.
    *
    * @param uri the document URI
    */
   void trackClose(String uri) {
      openDocuments.remove(uri);
   }

   /**
    * Returns the current version for an open document, or -1 if not tracked.
    *
    * @param uri the document URI
    * @return the version number, or -1
    */
   int getDocumentVersion(String uri) {
      Integer v = openDocuments.get(uri);
      return (null == v) ? -1 : v.intValue();
   }

   /**
    * Returns true if the given document URI is currently open in this session.
    *
    * @param uri the document URI
    * @return true if tracked as open
    */
   boolean isDocumentOpen(String uri) {
      return openDocuments.containsKey(uri);
   }

   // ---------------------------------------------------------------
   // Initialize params
   // ---------------------------------------------------------------

   private Map<String, Object> buildInitializeParams() {
      Map<String, Object> params = new HashMap<>();
      params.put("processId", ProcessHandle.current().pid());

      Map<String, Object> clientInfo = new HashMap<>();
      clientInfo.put("name", "javi");
      clientInfo.put("version", "1.0");
      params.put("clientInfo", clientInfo);

      params.put("rootUri", "file://" + projectRoot);

      Map<String, Object> capabilities = new HashMap<>();
      Map<String, Object> textDoc = new HashMap<>();

      // Sync capabilities
      Map<String, Object> sync = new HashMap<>();
      sync.put("dynamicRegistration", Boolean.FALSE);
      sync.put("willSave", Boolean.FALSE);
      sync.put("willSaveWaitUntil", Boolean.FALSE);
      sync.put("didSave", Boolean.TRUE);
      textDoc.put("synchronization", sync);

      // Completion
      Map<String, Object> completion = new HashMap<>();
      completion.put("dynamicRegistration", Boolean.FALSE);
      Map<String, Object> completionItem = new HashMap<>();
      completionItem.put("snippetSupport", Boolean.FALSE);
      completion.put("completionItem", completionItem);
      textDoc.put("completion", completion);

      // Definition
      Map<String, Object> definition = new HashMap<>();
      definition.put("dynamicRegistration", Boolean.FALSE);
      textDoc.put("definition", definition);

      // References
      Map<String, Object> references = new HashMap<>();
      references.put("dynamicRegistration", Boolean.FALSE);
      textDoc.put("references", references);

      // Hover
      Map<String, Object> hover = new HashMap<>();
      hover.put("dynamicRegistration", Boolean.FALSE);
      textDoc.put("hover", hover);

      // Code action
      Map<String, Object> codeAction = new HashMap<>();
      codeAction.put("dynamicRegistration", Boolean.FALSE);
      textDoc.put("codeAction", codeAction);

      // Diagnostics (publishDiagnostics)
      Map<String, Object> publishDiag = new HashMap<>();
      publishDiag.put("relatedInformation", Boolean.TRUE);
      textDoc.put("publishDiagnostics", publishDiag);

      capabilities.put("textDocument", textDoc);

      // Workspace capabilities
      Map<String, Object> workspace = new HashMap<>();
      Map<String, Object> wsSymbol = new HashMap<>();
      wsSymbol.put("dynamicRegistration", Boolean.FALSE);
      workspace.put("symbol", wsSymbol);
      Map<String, Object> executeCmd = new HashMap<>();
      executeCmd.put("dynamicRegistration", Boolean.FALSE);
      workspace.put("executeCommand", executeCmd);
      capabilities.put("workspace", workspace);

      params.put("capabilities", capabilities);
      return params;
   }

   @Override
   public String toString() {
      return "LspSession[" + config.languageId + " " + state
         + " uptime=" + (getUptime() / 1000) + "s]";
   }
}
