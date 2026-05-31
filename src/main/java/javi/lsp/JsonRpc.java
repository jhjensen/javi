package javi.lsp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static history.Tools.trace;

/**
 * Lightweight JSON-RPC 2.0 transport for the Language Server Protocol.
 *
 * <p>Implements the LSP base protocol: Content-Length delimited JSON messages
 * over stdin/stdout of a language server process. This avoids depending on
 * heavy libraries like LSP4J by implementing the minimal subset needed.</p>
 *
 * <h2>Message Format</h2>
 * <pre>
 * Content-Length: &lt;length&gt;\r\n
 * \r\n
 * &lt;JSON payload&gt;
 * </pre>
 *
 * <h2>Message Types</h2>
 * <ul>
 *   <li><b>Request</b>: Has id + method + params, expects a response</li>
 *   <li><b>Notification</b>: Has method + params, no response expected</li>
 *   <li><b>Response</b>: Has id + result/error, matches a prior request</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Sending is synchronized on the output stream. Reading runs in a
 * dedicated thread. Pending request futures are stored in a
 * {@link ConcurrentHashMap}.</p>
 *
 * <h2>JSON Handling</h2>
 * <p>Uses a minimal hand-written JSON parser/builder ({@link SimpleJson})
 * rather than pulling in a JSON library dependency. This is sufficient
 * for the LSP protocol subset we use.</p>
 *
 * @see LspClient
 * @see SimpleJson
 */
final class JsonRpc {

   /** Timeout in seconds for waiting for a response to a request. */
   static final int DEFAULT_TIMEOUT_SECONDS = 5;

   private final OutputStream outputStream;
   private final InputStream inputStream;
   private final AtomicInteger nextId = new AtomicInteger(1);
   private final ConcurrentHashMap<Integer, CompletableFuture<Map<String, Object>>>
      pendingRequests = new ConcurrentHashMap<>();
   private volatile boolean running;
   private Thread readerThread;
   private MessageHandler notificationHandler;
   private volatile PrintWriter trafficLog;

   /**
    * Callback interface for handling incoming notifications and requests
    * from the language server.
    */
   interface MessageHandler {
      /**
       * Called when a notification (no id) arrives from the server.
       *
       * @param method the LSP method name (e.g. "textDocument/publishDiagnostics")
       * @param params the notification parameters, may be null
       */
      void onNotification(String method, Map<String, Object> params);

      /**
       * Called when a server-initiated request arrives.
       *
       * @param id the request id
       * @param method the LSP method name
       * @param params the request parameters, may be null
       * @return the result to send back, or null
       */
      Map<String, Object> onRequest(int id, String method,
         Map<String, Object> params);
   }

   /**
    * Creates a new JSON-RPC transport over the given streams.
    *
    * @param input the input stream to read server messages from
    * @param output the output stream to write client messages to
    */
   JsonRpc(InputStream input, OutputStream output) {
      this.inputStream = input;
      this.outputStream = output;
   }

   /**
    * Sets the handler for incoming notifications and server requests.
    *
    * @param handler the message handler
    */
   void setMessageHandler(MessageHandler handler) {
      this.notificationHandler = handler;
   }

   /**
    * Enable JSON-RPC traffic logging to a file for LSP debugging.
    * Each line is prefixed with SEND/RECV and flushed immediately.
    */
   void setTrafficLog(File logFile) {
      try {
         File parent = logFile.getParentFile();
         if (parent != null && !parent.exists() && !parent.mkdirs()) {
            trace("LSP traffic log: cannot create directory " + parent);
            return;
         }
         trafficLog = new PrintWriter(new BufferedWriter(
            new FileWriter(logFile, true)), true);
      } catch (IOException e) {
         trace("LSP traffic log setup failed: " + e);
      }
   }

   /**
    * Starts the reader thread that processes incoming messages.
    * Must be called after construction and before sending requests.
    */
   void startReading() {
      running = true;
      readerThread = new Thread(this::readLoop, "lsp-reader");
      readerThread.setDaemon(true);
      readerThread.start();
   }

   /**
    * Stops the reader thread and cancels all pending requests.
    */
   void stopReading() {
      running = false;
      if (null != readerThread) {
         readerThread.interrupt();
      }
      // Cancel all pending futures
      for (CompletableFuture<Map<String, Object>> future
            : pendingRequests.values()) {
         future.cancel(true);
      }
      pendingRequests.clear();
      PrintWriter log = trafficLog;
      trafficLog = null;
      if (null != log) {
         log.close();
      }
   }

   /**
    * Sends an LSP request and returns a future for the response.
    *
    * <p>The request is assigned a unique integer id. The returned future
    * completes when the server sends back a response with the matching id.</p>
    *
    * @param method the LSP method (e.g. "textDocument/definition")
    * @param params the request parameters as a map
    * @return a future that completes with the response result
    * @throws IOException if writing to the output stream fails
    */
   CompletableFuture<Map<String, Object>> sendRequest(String method,
         Map<String, Object> params) throws IOException {
      int id = nextId.getAndIncrement();
      CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
      pendingRequests.put(id, future);

      Map<String, Object> message = new HashMap<>();
      message.put("jsonrpc", "2.0");
      message.put("id", Integer.valueOf(id));
      message.put("method", method);
      if (null != params) {
         message.put("params", params);
      }

      sendMessage(message);
      return future;
   }

   /**
    * Sends an LSP request and blocks until the response arrives.
    *
    * @param method the LSP method
    * @param params the request parameters
    * @return the response result map, or null on error/timeout
    * @throws IOException if writing fails
    */
   Map<String, Object> sendRequestSync(String method,
         Map<String, Object> params) throws IOException {
      return sendRequestSync(method, params, DEFAULT_TIMEOUT_SECONDS);
   }

   /**
    * Sends an LSP request and blocks with a custom timeout.
    *
    * @param method the LSP method
    * @param params the request parameters
    * @param timeoutSeconds timeout in seconds
    * @return the response result map, or null on error/timeout
    * @throws IOException if writing fails
    */
   Map<String, Object> sendRequestSync(String method,
         Map<String, Object> params, int timeoutSeconds)
         throws IOException {
      try {
         return sendRequest(method, params)
            .get(timeoutSeconds, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
         trace("LSP request timed out: " + method);
         return null;
      } catch (Exception e) {
         trace("LSP request failed: " + method + " " + e);
         return null;
      }
   }

   /**
    * Sends an LSP notification (no response expected).
    *
    * @param method the LSP method (e.g. "textDocument/didOpen")
    * @param params the notification parameters
    * @throws IOException if writing to the output stream fails
    */
   void sendNotification(String method, Map<String, Object> params)
         throws IOException {
      Map<String, Object> message = new HashMap<>();
      message.put("jsonrpc", "2.0");
      message.put("method", method);
      if (null != params) {
         message.put("params", params);
      }
      sendMessage(message);
   }

   /**
    * Writes a JSON-RPC message with the Content-Length header.
    *
    * @param message the message as a map to serialize to JSON
    * @throws IOException if writing fails
    */
   private void sendMessage(Map<String, Object> message) throws IOException {
      String json = SimpleJson.encode(message);
      byte[] content = json.getBytes(StandardCharsets.UTF_8);
      String header = "Content-Length: " + content.length + "\r\n\r\n";

      synchronized (outputStream) {
         outputStream.write(header.getBytes(StandardCharsets.US_ASCII));
         outputStream.write(content);
         outputStream.flush();
      }
      trace("LSP sent: " + json);
      PrintWriter log = trafficLog;
      if (null != log)
         log.println("SEND " + json);
   }

   /**
    * Main read loop running in the reader thread.
    *
    * <p>Reads Content-Length headers, then reads that many bytes of JSON,
    * parses the message, and dispatches to the appropriate handler:
    * response futures for responses, notification handler for
    * notifications/requests.</p>
    */
   @SuppressWarnings("unchecked")
   private void readLoop() {
      try {
         BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, StandardCharsets.UTF_8));

         while (running) {
            // Read headers
            int contentLength = readHeaders(reader);
            if (contentLength < 0) {
               break; // stream closed
            }

            // Read content
            char[] buf = new char[contentLength];
            int offset = 0;
            while (offset < contentLength) {
               int read = reader.read(buf, offset, contentLength - offset);
               if (read < 0)
                  break;
               offset += read;
            }

            if (offset < contentLength) {
               trace("LSP: incomplete message, expected " + contentLength
                  + " got " + offset);
               break;
            }

            String json = new String(buf);
            trace("LSP recv: " + json);
            PrintWriter log = trafficLog;
            if (null != log)
               log.println("RECV " + json);

            Map<String, Object> msg = SimpleJson.decodeObject(json);
            if (null == msg) {
               trace("LSP: failed to parse message");
               continue;
            }

            // Dispatch based on message type
            if (msg.containsKey("id") && msg.containsKey("method")) {
               // Server-initiated request
               handleServerRequest(msg);
            } else if (msg.containsKey("id")) {
               // Response to our request
               handleResponse(msg);
            } else if (msg.containsKey("method")) {
               // Notification
               handleNotification(msg);
            }
         }
      } catch (IOException e) {
         if (running) {
            trace("LSP reader error: " + e);
         }
      }
      trace("LSP reader thread exiting");
   }

   /**
    * Reads HTTP-style headers until the blank line separator.
    *
    * @param reader the buffered reader to read from
    * @return the Content-Length value, or -1 if the stream is closed
    * @throws IOException if reading fails
    */
   private int readHeaders(BufferedReader reader) throws IOException {
      int contentLength = -1;
      String line;
      while (null != (line = reader.readLine())) {
         if (line.isEmpty()) {
            break;
         }
         if (line.startsWith("Content-Length:")) {
            String val = line.substring("Content-Length:".length()).trim();
            contentLength = Integer.parseInt(val);
         }
         // Ignore other headers (Content-Type, etc.)
      }
      return contentLength;
   }

   /**
    * Handles a response message by completing the corresponding future.
    *
    * @param msg the parsed response message
    */
   @SuppressWarnings("unchecked")
   private void handleResponse(Map<String, Object> msg) {
      Object idObj = msg.get("id");
      if (null == idObj)
         return;
      int id;
      if (idObj instanceof Number) {
         id = ((Number) idObj).intValue();
      } else {
         id = Integer.parseInt(idObj.toString());
      }

      CompletableFuture<Map<String, Object>> future =
         pendingRequests.remove(id);
      if (null == future)
         return;

      if (msg.containsKey("error")) {
         Object err = msg.get("error");
         String errMsg = (err instanceof Map)
            ? String.valueOf(((Map<String, Object>) err).get("message"))
            : String.valueOf(err);
         future.completeExceptionally(
            new IOException("LSP error: " + errMsg));
      } else {
         Object result = msg.get("result");
         if (result instanceof Map) {
            future.complete((Map<String, Object>) result);
         } else {
            // Wrap non-map results
            Map<String, Object> wrapper = new HashMap<>();
            wrapper.put("result", result);
            future.complete(wrapper);
         }
      }
   }

   /**
    * Handles a server-initiated request by dispatching to the handler.
    *
    * @param msg the parsed request message
    */
   @SuppressWarnings("unchecked")
   private void handleServerRequest(Map<String, Object> msg) {
      if (null == notificationHandler)
         return;

      Object idObj = msg.get("id");
      int id = (idObj instanceof Number)
         ? ((Number) idObj).intValue()
         : Integer.parseInt(idObj.toString());

      String method = (String) msg.get("method");
      Map<String, Object> params = (msg.get("params") instanceof Map)
         ? (Map<String, Object>) msg.get("params")
         : null;

      Map<String, Object> result =
         notificationHandler.onRequest(id, method, params);

      // Send response
      try {
         Map<String, Object> response = new HashMap<>();
         response.put("jsonrpc", "2.0");
         response.put("id", Integer.valueOf(id));
         response.put("result", result);
         sendMessage(response);
      } catch (IOException e) {
         trace("LSP: failed to send response for " + method + ": " + e);
      }
   }

   /**
    * Handles a notification by dispatching to the handler.
    *
    * @param msg the parsed notification message
    */
   @SuppressWarnings("unchecked")
   private void handleNotification(Map<String, Object> msg) {
      if (null == notificationHandler)
         return;

      String method = (String) msg.get("method");
      Map<String, Object> params = (msg.get("params") instanceof Map)
         ? (Map<String, Object>) msg.get("params")
         : null;

      notificationHandler.onNotification(method, params);
   }

   /**
    * Returns whether the reader thread is running.
    *
    * @return true if currently reading messages
    */
   boolean isRunning() {
      return running;
   }
}
