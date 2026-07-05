package javi.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks AI request history with per-request detail.
 *
 * <p>Records each AI request with its command type, context source,
 * model, token estimates, and tool calls. The log is queryable
 * via {@link #getEntries()} and displayed by {@code :ai status}.</p>
 *
 * <p>Entries are bounded to {@link #MAX_ENTRIES} to limit memory.
 * Oldest entries are evicted when the limit is reached.</p>
 */
public final class RequestLog {

   /** Maximum number of entries to retain. */
   public static final int MAX_ENTRIES = 200;

   private static final List<Entry> entries =
      new ArrayList<>();
   private static int premiumCount;
   private static int toolCallCount;
   private static long totalDurationMs;

   private RequestLog() {
   }

   /**
    * A single logged AI request.
    *
    * @param timestamp when the request was made
    * @param command the AI command (chat, explain, review, etc.)
    * @param model the model used
    * @param provider the provider id (copilot, openai, etc.)
    * @param premium whether the model is premium
    * @param sourceName the context source buffer name
    * @param contextLines lines of context sent
    * @param contextChars characters of context sent
    * @param estInputTokens estimated input tokens
    * @param estOutputTokens estimated output tokens (filled after response)
    * @param toolCalls number of tool calls in this request
    * @param durationMs wall-clock duration (filled after response)
    */
   public record Entry(
      Instant timestamp,
      String command,
      String model,
      String provider,
      boolean premium,
      String sourceName,
      int contextLines,
      int contextChars,
      int estInputTokens,
      int estOutputTokens,
      int toolCalls,
      long durationMs
   ) { }

   /**
    * Log a new request (before response arrives).
    *
    * @param command AI command name
    * @param model model identifier
    * @param provider provider id
    * @param premium whether the model is premium
    * @param sourceName context source name
    * @param contextChars context character count
    * @param contextLines context line count
    * @return the index of the new entry (for updating later)
    */
   public static synchronized int logRequest(
         String command, String model, String provider,
         boolean premium, String sourceName,
         int contextChars, int contextLines) {
      if (entries.size() >= MAX_ENTRIES) {
         entries.remove(0);
      }
      Entry e = new Entry(
         Instant.now(), command, model, provider, premium,
         sourceName, contextLines, contextChars,
         contextChars / 4, 0, 0, 0);
      entries.add(e);
      if (premium) {
         premiumCount++;
      }
      return entries.size() - 1;
   }

   /**
    * Update a request entry after the response arrives.
    *
    * @param index the entry index from {@link #logRequest}
    * @param outputChars response character count
    * @param toolCalls number of tool calls
    * @param durationMs wall-clock milliseconds
    */
   public static synchronized void updateResponse(
         int index, int outputChars, int toolCalls,
         long durationMs) {
      if (index < 0 || index >= entries.size()) {
         return;
      }
      Entry old = entries.get(index);
      entries.set(index, new Entry(
         old.timestamp(), old.command(), old.model(),
         old.provider(), old.premium(), old.sourceName(),
         old.contextLines(), old.contextChars(),
         old.estInputTokens(), outputChars / 4,
         toolCalls, durationMs));
      toolCallCount += toolCalls;
      totalDurationMs += durationMs;
   }

   /**
    * Get an unmodifiable view of all entries.
    *
    * @return list of request entries
    */
   public static synchronized List<Entry> getEntries() {
      return Collections.unmodifiableList(
         new ArrayList<>(entries));
   }

   /** Get total premium model requests. */
   public static int getPremiumCount() {
      return premiumCount;
   }

   /** Get total tool calls across all requests. */
   public static int getToolCallCount() {
      return toolCallCount;
   }

   /** Get total wall-clock time spent on requests. */
   public static long getTotalDurationMs() {
      return totalDurationMs;
   }

   /** Get total number of logged requests. */
   public static synchronized int size() {
      return entries.size();
   }

   /** Clear the request log. */
   public static synchronized void clear() {
      entries.clear();
      premiumCount = 0;
      toolCallCount = 0;
      totalDurationMs = 0;
   }

   /**
    * Format a brief summary of the last N requests.
    *
    * @param count number of recent entries to show
    * @return multi-line formatted summary
    */
   public static synchronized String formatRecent(int count) {
      StringBuilder sb = new StringBuilder(512);
      int start = Math.max(0, entries.size() - count);
      for (int i = start; i < entries.size(); i++) {
         Entry e = entries.get(i);
         sb.append(String.format("#%d %s :%s",
            i + 1, e.model(), e.command()));
         if (null != e.sourceName()) {
            sb.append(" src=").append(e.sourceName());
         }
         sb.append(String.format(
            " ctx=%dL/%dC ~%dtok",
            e.contextLines(), e.contextChars(),
            e.estInputTokens()));
         if (e.estOutputTokens() > 0) {
            sb.append(String.format(
               " out=~%dtok", e.estOutputTokens()));
         }
         if (e.toolCalls() > 0) {
            sb.append(String.format(
               " tools=%d", e.toolCalls()));
         }
         if (e.durationMs() > 0) {
            sb.append(String.format(
               " %dms", e.durationMs()));
         }
         if (e.premium()) {
            sb.append(" PREMIUM");
         }
         sb.append('\n');
      }
      return sb.toString();
   }
}
