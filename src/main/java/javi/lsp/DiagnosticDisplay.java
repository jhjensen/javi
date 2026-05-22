package javi.lsp;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javi.PosListList;

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

   /** Diagnostics keyed by source + "|" + uri to prevent overwriting. */
   private final Map<String, List<Map<String, Object>>> store =
      new HashMap<>();

   /** Latch for one-shot spell check waiting. */
   private volatile CountDownLatch spellLatch;

   /** Source+URI key we're waiting for in the spell latch. */
   private volatile String spellAwaitKey;

   /**
    * Called when the server publishes diagnostics for a file.
    * Replaces any previous diagnostics for that source+URI pair.
    *
    * @param source the language server identifier
    * @param uri the file URI
    * @param diagnostics list of diagnostic maps from the server
    */
   @Override
   public synchronized void onDiagnostics(String source, String uri,
         List<Map<String, Object>> diagnostics) {
      trace("DiagnosticDisplay: [" + source + "] " + uri + " => "
         + diagnostics.size() + " diagnostics");
      String key = source + "|" + uri;
      if (diagnostics.isEmpty()) {
         store.remove(key);
      } else {
         store.put(key, new ArrayList<>(diagnostics));
      }

      // Signal spell latch if this is what we're waiting for
      CountDownLatch latch = spellLatch;
      String awaitKey = spellAwaitKey;
      if (null != latch && null != awaitKey && key.equals(awaitKey)) {
         latch.countDown();
      }

      updatePoslist();
   }

   /**
    * Rebuilds the "lsp-diag" poslist from all stored diagnostics.
    * Dispatches to the AWT event thread since PosListList operations
    * must happen on that thread.
    */
   private void updatePoslist() {
      List<String> posLines = buildPositionLines();
      if (posLines.isEmpty()) {
         java.awt.EventQueue.invokeLater(() -> {
            PosListList.Cmd.removePositionIoc("lsp-diag");
         });
         return;
      }
      String joined = String.join("\n", posLines) + "\n";
      java.awt.EventQueue.invokeLater(() -> {
         BufferedReader reader = new BufferedReader(
            new StringReader(joined));
         PosListList.Cmd.replaceFromReader("lsp-diag", reader);
      });
   }

   /**
    * Builds position-format lines from all stored diagnostics.
    * Format: {@code filepath(col,line)-[Severity] message}
    *
    * @return list of position-formatted strings
    */
   @SuppressWarnings("unchecked")
   private List<String> buildPositionLines() {
      List<String> lines = new ArrayList<>();
      for (Map.Entry<String, List<Map<String, Object>>> entry
            : store.entrySet()) {
         String key = entry.getKey();
         String uri = key.substring(key.indexOf('|') + 1);
         String filePath = LspClient.uriToPath(uri);
         for (Map<String, Object> diag : entry.getValue()) {
            int line = 1;
            int col = 0;
            Object rangeObj = diag.get("range");
            if (rangeObj instanceof Map) {
               Map<String, Object> range = (Map<String, Object>) rangeObj;
               Object startObj = range.get("start");
               if (startObj instanceof Map) {
                  Map<String, Object> start =
                     (Map<String, Object>) startObj;
                  Object l = start.get("line");
                  if (l instanceof Number)
                     line = ((Number) l).intValue() + 1;
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
            // Position format: filename(col,line)-comment
            if (col > 0) {
               lines.add(filePath + "(" + col + "," + line
                  + ")-[" + sevName + "] " + message);
            } else {
               lines.add(filePath + "(" + line
                  + ")-[" + sevName + "] " + message);
            }
         }
      }
      return lines;
   }

   /**
    * Waits for diagnostics from a specific source and URI.
    * Sets up a latch before the caller sends the file, so no
    * race condition exists.
    *
    * @param source the language server ID (e.g. "harper")
    * @param uri the file URI to wait for
    * @param timeoutMs maximum wait time in milliseconds
    * @return true if diagnostics arrived within timeout
    */
   public boolean awaitDiagnosticsFrom(String source, String uri,
         long timeoutMs) {
      String key = source + "|" + uri;

      // Check if diagnostics already exist
      synchronized (this) {
         if (store.containsKey(key))
            return true;
      }

      // Set up latch before the file is sent
      CountDownLatch latch = new CountDownLatch(1);
      spellAwaitKey = key;
      spellLatch = latch;
      try {
         return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return false;
      } finally {
         spellLatch = null;
         spellAwaitKey = null;
      }
   }

   /**
    * Prepares the latch for awaiting diagnostics. Call this BEFORE
    * sending the file to the server, then call
    * {@link #awaitSpellLatch(long)} after sending.
    *
    * @param source the language server ID (e.g. "harper")
    * @param uri the file URI to wait for
    */
   public void prepareSpellLatch(String source, String uri) {
      String key = source + "|" + uri;
      spellAwaitKey = key;
      spellLatch = new CountDownLatch(1);
   }

   /**
    * Waits on the previously prepared spell latch.
    *
    * @param timeoutMs maximum wait time in milliseconds
    * @return true if diagnostics arrived within timeout
    */
   public boolean awaitSpellLatch(long timeoutMs) {
      CountDownLatch latch = spellLatch;
      if (null == latch)
         return false;
      try {
         return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return false;
      } finally {
         spellLatch = null;
         spellAwaitKey = null;
      }
   }

   /**
    * Builds position-format lines from harper (spell) diagnostics
    * for the given file. Used by the {@code lsp.spell} command to
    * create a one-shot spell position list.
    *
    * @param filePath absolute file path
    * @return list of position-formatted strings, empty if no issues
    */
   @SuppressWarnings("unchecked")
   public synchronized List<String> buildSpellPositionLines(
         String filePath) {
      String uri = LspClient.pathToUri(filePath);
      String key = "harper|" + uri;
      List<Map<String, Object>> diags = store.get(key);
      if (null == diags || diags.isEmpty())
         return Collections.emptyList();

      List<String> lines = new ArrayList<>();
      for (Map<String, Object> diag : diags) {
         int line = 1;
         int col = 0;
         Object rangeObj = diag.get("range");
         if (rangeObj instanceof Map) {
            Map<String, Object> range = (Map<String, Object>) rangeObj;
            Object startObj = range.get("start");
            if (startObj instanceof Map) {
               Map<String, Object> start =
                  (Map<String, Object>) startObj;
               Object l = start.get("line");
               if (l instanceof Number)
                  line = ((Number) l).intValue() + 1;
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
         if (col > 0) {
            lines.add(filePath + "(" + col + "," + line
               + ")-[" + sevName + "] " + message);
         } else {
            lines.add(filePath + "(" + line
               + ")-[" + sevName + "] " + message);
         }
      }
      return lines;
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
      List<Map<String, Object>> merged = new ArrayList<>();
      for (Map.Entry<String, List<Map<String, Object>>> entry
            : store.entrySet()) {
         String key = entry.getKey();
         if (key.substring(key.indexOf('|') + 1).equals(uri))
            merged.addAll(entry.getValue());
      }
      return merged.isEmpty()
         ? Collections.emptyList()
         : Collections.unmodifiableList(merged);
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
