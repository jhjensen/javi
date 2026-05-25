package javi.lsp;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Immutable command submitted to an LspSession's processing queue.
 *
 * <p>Encapsulates an LSP method invocation with its parameters and a
 * CompletableFuture for delivering the result back to the caller
 * (typically the AWT thread).</p>
 *
 * <p>There are two flavors:
 * <ul>
 *   <li><b>Request</b> — expects a response (has a future)</li>
 *   <li><b>Notification</b> — fire-and-forget (future is null)</li>
 * </ul></p>
 */
final class LspRequest {

   /** The LSP method name (e.g. "textDocument/definition"). */
   final String method;

   /** The request parameters, may be null for parameterless methods. */
   final Map<String, Object> params;

   /**
    * Future completed with the server's response.
    * Null for notifications (no response expected).
    */
   final CompletableFuture<Map<String, Object>> future;

   /**
    * Creates a request that expects a response.
    *
    * @param method the LSP method name
    * @param params the request parameters
    * @param future future to complete with the response
    */
   LspRequest(String method, Map<String, Object> params,
         CompletableFuture<Map<String, Object>> future) {
      this.method = method;
      this.params = params;
      this.future = future;
   }

   /**
    * Creates a notification (no response expected).
    *
    * @param method the LSP method name
    * @param params the notification parameters
    * @return a new LspRequest with null future
    */
   static LspRequest notification(String method, Map<String, Object> params) {
      return new LspRequest(method, params, null);
   }

   /**
    * Creates a request that expects a response.
    *
    * @param method the LSP method name
    * @param params the request parameters
    * @return a new LspRequest with a fresh CompletableFuture
    */
   static LspRequest request(String method, Map<String, Object> params) {
      return new LspRequest(method, params, new CompletableFuture<>());
   }

   /** Returns true if this is a notification (no response expected). */
   boolean isNotification() {
      return null == future;
   }

   @Override
   public String toString() {
      return "LspRequest[" + method + (isNotification() ? " notify" : " req")
         + "]";
   }
}
