package javi;

/**
 * Thrown when a command cannot execute yet during initialization
 * (e.g., fvc is not available).  The command should be retried later.
 */
class DeferCommandException extends RuntimeException {

   private static final long serialVersionUID = 1;

   DeferCommandException(String message) {
      super(message);
   }
}
