package javi.ai;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javi.EventQueue;
import javi.InputException;
import javi.UI;

import static history.Tools.trace;

/**
 * Executes AI API calls on a background thread with status indication
 * and cancellation support.
 *
 * <p>AI API calls are network-bound and can take seconds. Running them
 * on the editor's event thread blocks all user interaction. This class
 * wraps those calls in a background thread and dispatches results back
 * to the editor via {@link EventQueue#insert(EventQueue.IEvent)}.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AIAsyncExecutor.submit(
 *    () -> client.chat(message),     // runs on background thread
 *    response -> handleResponse(response),  // runs on event thread
 *    error -> UI.reportMessage(error.getMessage())  // runs on event thread
 * );
 * }</pre>
 *
 * <h2>Cancellation</h2>
 * <p>Only one AI request can be in-flight at a time. Submitting a new
 * request cancels any pending one. The user can also cancel explicitly
 * via {@link #cancel()}, typically bound to Escape or {@code :ai cancel}.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>All public methods are thread-safe. The {@code onSuccess} and
 * {@code onError} callbacks execute on the editor event thread with
 * {@code biglock2} held, so they can safely modify editor state.</p>
 *
 * @see EventQueue
 * @see AIClient
 */
public final class AIAsyncExecutor {

   private static final ExecutorService executor =
      Executors.newSingleThreadExecutor(r -> {
         Thread t = new Thread(r, "ai-async");
         t.setDaemon(true);
         return t;
      });

   private static final AtomicReference<Future<?>> currentTask =
      new AtomicReference<>();

   private AIAsyncExecutor() {
   }

   /**
    * Submit an AI operation for background execution.
    *
    * <p>The {@code task} supplier runs on a background thread without
    * holding {@code biglock2}. When it completes, the result is posted
    * back to the editor event thread via {@link EventQueue}.</p>
    *
    * <p>Any previously running AI task is cancelled before the new
    * one starts.</p>
    *
    * @param <T> the result type
    * @param task the blocking operation to run (e.g., API call)
    * @param onSuccess callback for successful result (runs on event thread)
    * @param onError callback for errors (runs on event thread)
    */
   public static <T> void submit(Supplier<T> task,
         Consumer<T> onSuccess, Consumer<Exception> onError) {
      cancelCurrent();
      UI.reportMessage("AI: thinking...");

      Future<?> future = executor.submit(() -> {
         try {
            T result = task.get();
            postToEventThread(() -> {
               try {
                  onSuccess.accept(result);
               } catch (Exception e) {
                  trace("AI async onSuccess error: " + e);
                  UI.reportMessage("AI Error: " + e.getMessage());
               }
            });
         } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
               postToEventThread(()
                  -> UI.reportMessage("AI: request cancelled"));
               return;
            }
            trace("AI async task error: " + e);
            postToEventThread(() -> {
               try {
                  onError.accept(e);
               } catch (Exception e2) {
                  trace("AI async onError error: " + e2);
                  UI.reportMessage("AI Error: " + e2.getMessage());
               }
            });
         }
      });
      currentTask.set(future);
   }

   /**
    * Cancel the currently in-flight AI request, if any.
    *
    * @return true if a task was cancelled, false if nothing was running
    */
   public static boolean cancel() {
      boolean cancelled = cancelCurrent();
      if (cancelled) {
         UI.reportMessage("AI: request cancelled");
         trace("AI async task cancelled by user");
      }
      return cancelled;
   }

   /**
    * Check whether an AI request is currently in-flight.
    *
    * @return true if a background AI call is running
    */
   public static boolean isBusy() {
      Future<?> f = currentTask.get();
      return null != f && !f.isDone();
   }

   /**
    * Cancel the current task without reporting to the user.
    *
    * @return true if a task was cancelled
    */
   private static boolean cancelCurrent() {
      Future<?> prev = currentTask.getAndSet(null);
      if (null != prev && !prev.isDone()) {
         prev.cancel(true);
         return true;
      }
      return false;
   }

   /**
    * Post a runnable to the editor event thread via EventQueue.
    *
    * @param action the action to run on the event thread
    */
   private static void postToEventThread(Runnable action) {
      EventQueue.insert(new EventQueue.IEvent() {
         @Override
         public void execute() throws InputException {
            action.run();
         }
      });
   }
}
