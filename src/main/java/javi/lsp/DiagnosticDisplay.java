package javi.lsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static history.Tools.trace;

/**
 * Collects and formats LSP diagnostics for display in the editor.
 *
 * <p>Implements {@link LspClient.DiagnosticHandler} to receive
 * {@code textDocument/publishDiagnostics} notifications from the
 * language server. Stores diagnostics per-file and provides
 * formatted output for both status bar display and buffer listing.</p>
 *
 * <h2>Diagnostic Severity Levels</h2>
 * <ul>
 *   <li>1 = Error</li>
 *   <li>2 = Warning</li>
 *   <li>3 = Information</li>
 *   <li>4 = Hint</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Diagnostic updates arrive on the JSON-RPC reader thread.
 * Access to the stored diagnostics is synchronized.</p>
 *
 * @see LspClient.DiagnosticHandler
 * @see LspCommands
 */
public final class DiagnosticDisplay implements LspClient.DiagnosticHandler {

   /** Severity names indexed by LSP DiagnosticSeverity value. */
   private static final String[] SEVERITY = {
      "???", "Error", "Warn", "Info", "Hint"
   };

   /** Diagnostics keyed by file URI. */
   private final Map<String, List<Map<String, Object>>> store =
      new HashMap<>();

   /**
    * Called when the server publishes diagnostics for a file.
    * Replaces any previous diagnostics for that URI.
    *
    * @param uri the file URI
    * @param diagnostics list of diagnostic maps from the server
    */
   @Override
   public synchronized void onDiagnostics(String uri,
         List<Map<String, Object>> diagnostics) {
      trace("DiagnosticDisplay: " + uri + " => "
         + diagnostics.size() + " diagnostics");
      if (diagnostics.isEmpty()) {
         store.remove(uri);
      } else {
         store.put(uri, new ArrayList<>(diagnostics));
      }
   }

   /**
    * Returns the diagnostics for a file, or an empty list.
    *
    * @param filePath the absolute file path (converted to URI internally)
    * @return unmodifiable list of diagnostic maps
    */
   public synchronized List<Map<String, Object>> getDiagnostics(
         String filePath) {
      String uri = LspClient.pathToUri(filePath);
      List<Map<String, Object>> diags = store.get(uri);
      return (null == diags)
         ? Collections.emptyList()
         : Collections.unmodifiableList(diags);
   }

   /**
    * Returns a one-line summary suitable for the status bar.
    *
    * <p>Format: {@code 3 errors, 2 warnings (MyFile.java)}</p>
    *
    * @param filePath the absolute file path
    * @return summary string, or "No diagnostics" if clean
    */
   public String getSummary(String filePath) {
      List<Map<String, Object>> diags = getDiagnostics(filePath);
      if (diags.isEmpty())
         return "No diagnostics";

      int errors = 0;
      int warnings = 0;
      int info = 0;
      for (Map<String, Object> d : diags) {
         int sev = severityOf(d);
         switch (sev) {
            case 1: errors++; break;
            case 2: warnings++; break;
            default: info++;
         }
      }

      StringBuilder sb = new StringBuilder();
      if (errors > 0)
         sb.append(errors).append(" error").append(errors > 1 ? "s" : "");
      if (warnings > 0) {
         if (sb.length() > 0)
            sb.append(", ");
         sb.append(warnings).append(" warning")
            .append(warnings > 1 ? "s" : "");
      }
      if (info > 0) {
         if (sb.length() > 0)
            sb.append(", ");
         sb.append(info).append(" info");
      }
      return sb.toString();
   }

   /**
    * Formats all diagnostics for a file as lines suitable for
    * display in an editor buffer.
    *
    * <p>Each diagnostic is formatted as:</p>
    * <pre>
    * line:col [Severity] message
    * </pre>
    *
    * @param filePath the absolute file path
    * @return list of formatted lines, empty if no diagnostics
    */
   public List<String> formatForBuffer(String filePath) {
      List<Map<String, Object>> diags = getDiagnostics(filePath);
      if (diags.isEmpty())
         return Collections.singletonList("No diagnostics for " + filePath);

      List<String> lines = new ArrayList<>();
      // Header
      String basename = filePath;
      int slash = filePath.lastIndexOf('/');
      if (slash >= 0)
         basename = filePath.substring(slash + 1);
      lines.add("Diagnostics for " + basename + " ("
         + diags.size() + " issues):");
      lines.add("");

      for (Map<String, Object> d : diags) {
         lines.add(formatOneDiagnostic(d));
      }
      return lines;
   }

   /**
    * Returns a total count of diagnostics across all files.
    *
    * @return the total number of stored diagnostics
    */
   public synchronized int totalCount() {
      int count = 0;
      for (List<Map<String, Object>> diags : store.values()) {
         count += diags.size();
      }
      return count;
   }

   /**
    * Clears all stored diagnostics.
    */
   public synchronized void clear() {
      store.clear();
   }

   // --- internal helpers ---

   /**
    * Formats a single diagnostic map into a display line.
    */
   @SuppressWarnings("unchecked")
   private static String formatOneDiagnostic(Map<String, Object> diag) {
      int line = 0;
      int col = 0;
      Object rangeObj = diag.get("range");
      if (rangeObj instanceof Map) {
         Map<String, Object> range = (Map<String, Object>) rangeObj;
         Object startObj = range.get("start");
         if (startObj instanceof Map) {
            Map<String, Object> start = (Map<String, Object>) startObj;
            Object l = start.get("line");
            if (l instanceof Number)
               line = ((Number) l).intValue() + 1; // 0-based to 1-based
            Object c = start.get("character");
            if (c instanceof Number)
               col = ((Number) c).intValue() + 1;
         }
      }

      int sev = severityOf(diag);
      String sevName = (sev >= 1 && sev < SEVERITY.length)
         ? SEVERITY[sev] : SEVERITY[0];

      String message = "";
      Object msgObj = diag.get("message");
      if (null != msgObj)
         message = msgObj.toString();

      return String.format("%4d:%-3d [%s] %s", line, col, sevName, message);
   }

   /**
    * Extracts the severity from a diagnostic map.
    *
    * @return severity value (1-4), defaults to 1 (Error)
    */
   private static int severityOf(Map<String, Object> diag) {
      Object sevObj = diag.get("severity");
      if (sevObj instanceof Number)
         return ((Number) sevObj).intValue();
      return 1; // default to error
   }
}
