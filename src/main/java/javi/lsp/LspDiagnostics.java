package javi.lsp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static history.Tools.trace;

/**
 * Stores and manages LSP diagnostics for display.
 *
 * <p>Replaces the old {@code DiagnosticDisplay}. This class is ONLY
 * accessed from the AWT thread — no synchronization is needed.</p>
 *
 * <p>Diagnostics are keyed by {@code source|uri} to allow multiple
 * servers to contribute diagnostics for the same file without
 * overwriting each other.</p>
 *
 * <p>Severity levels follow the LSP specification:
 * <ul>
 *   <li>1 = Error</li>
 *   <li>2 = Warning</li>
 *   <li>3 = Information</li>
 *   <li>4 = Hint</li>
 * </ul></p>
 */
public final class LspDiagnostics {

   private static final Comparator<Entry> ENTRY_ORDER =
      Comparator.comparing((Entry e) -> e.uri)
      .thenComparingInt(e -> e.startLine)
      .thenComparingInt(e -> e.startChar)
      .thenComparingInt(e -> e.endLine)
      .thenComparingInt(e -> e.endChar)
      .thenComparingInt(e -> e.severity)
      .thenComparing(e -> e.source)
      .thenComparing(e -> e.message);

   /** A single diagnostic entry with typed fields. */
   public static final class Entry {
      public final String uri;
      public final String source;
      public final int startLine;   // 0-based
      public final int startChar;   // 0-based
      public final int endLine;     // 0-based
      public final int endChar;     // 0-based
      public final int severity;    // 1=Error, 2=Warn, 3=Info, 4=Hint
      public final String message;
      public final String code;     // diagnostic code (nullable)

      public Entry(String uri, String source, int startLine, int startChar,
            int endLine, int endChar, int severity, String message,
            String code) {
         this.uri = uri;
         this.source = source;
         this.startLine = startLine;
         this.startChar = startChar;
         this.endLine = endLine;
         this.endChar = endChar;
         this.severity = severity;
         this.message = message;
         this.code = code;
      }

      /** Returns severity as a display string. */
      public String severityString() {
         switch (severity) {
            case 1: return "Error";
            case 2: return "Warn";
            case 3: return "Info";
            case 4: return "Hint";
            default: return "Unknown";
         }
      }

      /**
       * Formats this diagnostic for position list display.
       * Format: filepath(col,line)-[Severity] message
       *
       * @return formatted string for poslist
       */
      public String toPosListEntry() {
         String path = uri.startsWith("file://")
            ? uri.substring(7)
            : uri;
         // Convert to 1-based for display
         return path + "(" + (startChar + 1) + "," + (startLine + 1)
            + ")-[" + severityString() + "] " + message;
      }
   }

   /**
    * Diagnostics keyed by "source|uri".
    * Each entry is a list of diagnostics from that source for that URI.
    */
   private final Map<String, List<Entry>> store = new HashMap<>();

   /**
    * Updates diagnostics for a given source and URI.
    * Replaces all previous diagnostics from that source for that URI.
    *
    * @param source the diagnostic source (e.g. "harper", "jdtls")
    * @param uri the document URI
    * @param diagnostics the new diagnostics (may be empty to clear)
    */
   public void update(String source, String uri,
         List<Map<String, Object>> diagnostics) {
      String key = source + "|" + uri;
      if (null == diagnostics || diagnostics.isEmpty()) {
         store.remove(key);
         return;
      }

      List<Entry> entries = new ArrayList<>(diagnostics.size());
      for (Map<String, Object> diag : diagnostics) {
         Entry entry = parseDiagnostic(uri, source, diag);
         if (null != entry) {
            entries.add(entry);
         }
      }

      if (entries.isEmpty()) {
         store.remove(key);
      } else {
         store.put(key, entries);
      }
   }

   /**
    * Returns all diagnostics for a given URI (from all sources).
    *
    * @param uri the document URI
    * @return list of entries, may be empty
    */
   public List<Entry> forUri(String uri) {
      List<Entry> result = new ArrayList<>();
      for (Map.Entry<String, List<Entry>> entry : store.entrySet()) {
         if (entry.getKey().endsWith("|" + uri)) {
            result.addAll(entry.getValue());
         }
      }
      result.sort(ENTRY_ORDER);
      return result;
   }

   /**
    * Returns all diagnostics from a specific source.
    *
    * @param source the source name (e.g. "harper")
    * @return list of entries, may be empty
    */
   public List<Entry> forSource(String source) {
      List<Entry> result = new ArrayList<>();
      for (Map.Entry<String, List<Entry>> entry : store.entrySet()) {
         if (entry.getKey().startsWith(source + "|")) {
            result.addAll(entry.getValue());
         }
      }
      result.sort(ENTRY_ORDER);
      return result;
   }

   /**
    * Returns diagnostics at a specific position in a file.
    *
    * @param uri the document URI
    * @param line 0-based line number
    * @param character 0-based character offset
    * @return matching diagnostics
    */
   public List<Entry> atPosition(String uri, int line, int character) {
      List<Entry> all = forUri(uri);
      List<Entry> result = new ArrayList<>();
      for (Entry e : all) {
         if (line >= e.startLine && line <= e.endLine) {
            if (line == e.startLine && character < e.startChar)
               continue;
            if (line == e.endLine && character > e.endChar)
               continue;
            result.add(e);
         }
      }
      result.sort(ENTRY_ORDER);
      return result;
   }

   /**
    * Clears all diagnostics for a specific source.
    *
    * @param source the source to clear
    */
   public void clearSource(String source) {
      store.entrySet().removeIf(
         e -> e.getKey().startsWith(source + "|"));
   }

   /**
    * Clears all stored diagnostics.
    */
   public void clearAll() {
      store.clear();
   }

   /**
    * Returns total diagnostic count across all sources.
    *
    * @return total number of diagnostics
    */
   public int totalCount() {
      int count = 0;
      for (List<Entry> entries : store.values()) {
         count += entries.size();
      }
      return count;
   }

   /**
    * Returns a summary string for status bar display.
    * Format: "E:2 W:5" or "OK" if no diagnostics.
    *
    * @return summary string
    */
   public String summary() {
      int errors = 0;
      int warnings = 0;
      for (List<Entry> entries : store.values()) {
         for (Entry e : entries) {
            if (1 == e.severity)
               errors++;
            else if (2 == e.severity)
               warnings++;
         }
      }
      if (0 == errors && 0 == warnings)
         return "OK";
      StringBuilder sb = new StringBuilder();
      if (errors > 0)
         sb.append("E:").append(errors);
      if (warnings > 0) {
         if (sb.length() > 0)
            sb.append(' ');
         sb.append("W:").append(warnings);
      }
      return sb.toString();
   }

   // ---------------------------------------------------------------
   // Parsing
   // ---------------------------------------------------------------

   /**
    * Parses a raw LSP diagnostic map into a typed Entry.
    */
   @SuppressWarnings("unchecked")
   private Entry parseDiagnostic(String uri, String source,
         Map<String, Object> diag) {
      Map<String, Object> range = getMap(diag, "range");
      if (null == range)
         return null;

      Map<String, Object> start = getMap(range, "start");
      Map<String, Object> end = getMap(range, "end");
      if (null == start || null == end)
         return null;

      int startLine = getInt(start, "line", 0);
      int startChar = getInt(start, "character", 0);
      int endLine = getInt(end, "line", 0);
      int endChar = getInt(end, "character", 0);

      int severity = getInt(diag, "severity", 4); // default to Hint
      String message = getString(diag, "message", "");
      String code = null;
      Object codeObj = diag.get("code");
      if (null != codeObj)
         code = codeObj.toString();

      return new Entry(uri, source, startLine, startChar,
         endLine, endChar, severity, message, code);
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> getMap(Map<String, Object> map,
         String key) {
      Object val = map.get(key);
      return (val instanceof Map) ? (Map<String, Object>) val : null;
   }

   private static int getInt(Map<String, Object> map, String key,
         int defaultVal) {
      Object val = map.get(key);
      if (val instanceof Number)
         return ((Number) val).intValue();
      return defaultVal;
   }

   private static String getString(Map<String, Object> map, String key,
         String defaultVal) {
      Object val = map.get(key);
      return (null != val) ? val.toString() : defaultVal;
   }
}
