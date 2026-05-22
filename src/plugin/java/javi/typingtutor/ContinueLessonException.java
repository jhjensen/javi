package javi.typingtutor;

/**
 * Thrown by the continue-handler to signal that the current insert
 * mode should exit and a new lesson should start. This avoids
 * recursive {@code insertMode()} calls which cause event leakage
 * and swallow {@code ExitException}.
 */
final class ContinueLessonException extends RuntimeException {
   ContinueLessonException() {
      super(null, null, true, false); // no stack trace for performance
   }
}
