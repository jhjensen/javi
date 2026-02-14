package javi.ai;

/**
 * Exception class for AI provider errors.
 *
 * <p>Thrown when an AI provider returns an error response, such as
 * invalid API key, rate limiting, model not found, or content
 * policy violations.</p>
 *
 * <h2>Error Categories</h2>
 * <ul>
 *   <li><b>Authentication</b>: Invalid or missing API key</li>
 *   <li><b>Rate Limit</b>: Too many requests</li>
 *   <li><b>Model Error</b>: Invalid model name or unavailable</li>
 *   <li><b>Content</b>: Content policy violation</li>
 *   <li><b>Server</b>: Provider-side errors (500+)</li>
 * </ul>
 *
 * @see AIProvider
 * @see AIClient
 */
public class AIException extends Exception {

   private final int statusCode;

   /**
    * Create an AIException with a message.
    *
    * @param message the error description
    */
   public AIException(String message) {
      super(message);
      this.statusCode = 0;
   }

   /**
    * Create an AIException with a message and HTTP status code.
    *
    * @param message the error description
    * @param statusCode the HTTP status code from the provider
    */
   public AIException(String message, int statusCode) {
      super(message);
      this.statusCode = statusCode;
   }

   /**
    * Create an AIException wrapping another exception.
    *
    * @param message the error description
    * @param cause the underlying cause
    */
   public AIException(String message, Throwable cause) {
      super(message, cause);
      this.statusCode = 0;
   }

   /**
    * Get the HTTP status code associated with this error.
    *
    * @return the HTTP status code, or 0 if not applicable
    */
   public int getStatusCode() {
      return statusCode;
   }

   /**
    * Check if this error is due to rate limiting.
    *
    * @return true if the provider returned a 429 status
    */
   public boolean isRateLimited() {
      return 429 == statusCode;
   }

   /**
    * Check if this error is an authentication failure.
    *
    * @return true if the provider returned a 401 or 403 status
    */
   public boolean isAuthError() {
      return 401 == statusCode || 403 == statusCode;
   }
}
