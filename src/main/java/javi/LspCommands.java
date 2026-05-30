package javi;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javi.lsp.LspDiagnostics;
import javi.lsp.LspRegistry;
import javi.lsp.LspSession;

import static history.Tools.trace;

/**
 * Command-layer wiring for the per-thread LSP subsystem.
 *
 * <p>Registers {@code :lsp.*} commands and owns the singleton
 * {@link LspDiagnostics} store and the {@link LspRegistry} instance.
 * All access from this class is on the AWT thread; diagnostics
 * arriving from session threads are posted to AWT via
 * {@code EventQueue.invokeLater} before mutating the store.</p>
 *
 * <p>This class intentionally exposes a small set of commands so the
 * new architecture can be exercised end-to-end without re-introducing
 * the legacy {@code LspManager}/{@code DiagnosticDisplay} code that was
 * removed by the F7-new rewrite:
 * <ul>
 *   <li>{@code :lsp.status} — print per-server status (state + uptime)</li>
 *   <li>{@code :lsp.diag} — print diagnostic summary and total count</li>
 *   <li>{@code :lsp.enable &lt;lang&gt;} — enable a configured server</li>
 *   <li>{@code :lsp.disable &lt;lang&gt;} — disable a configured server</li>
 *   <li>{@code :lsp.start &lt;lang&gt;} — eager-start a server session</li>
 *   <li>{@code :lsp.stop &lt;lang&gt;} — stop a running server session</li>
 *   <li>{@code :lsp.rootdir &lt;path&gt;} — set the project root</li>
 * </ul></p>
 */
public final class LspCommands extends Rgroup {

   private static volatile LspDiagnostics diagnostics;
   private static volatile LspRegistry registry;

   /** Default timeout for synchronous LSP requests in seconds. */
   static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 5;

   /** Creates and registers the LSP command set. */
   public LspCommands() {
      register(new String[]{""});
      ensureInitialized();
      registerLspCommands();
   }

   /**
    * Initialises the singleton registry and diagnostics store if not
    * already done. Safe to call from any thread, but in practice only
    * called from AWT during {@code Javi.initToUi}.
    */
   static synchronized void ensureInitialized() {
      if (null != registry)
         return;
      diagnostics = new LspDiagnostics();
      registry = LspRegistry.getInstance(new SinkImpl());
      PosListList.Cmd.registerTagProvider(new LspTagLookupProvider());
      PosListList.Cmd.registerTagProvider(new LspReferencesProvider());
   }

   /** Returns the shared diagnostics store. */
   public static LspDiagnostics getDiagnostics() {
      ensureInitialized();
      return diagnostics;
   }

   /** Returns the shared LSP registry. */
   public static LspRegistry getRegistry() {
      ensureInitialized();
      return registry;
   }

   /**
    * Routes a synchronous LSP request to the session for the given
    * language id. Releases nothing — callers that hold the AWT big
    * lock must drop it before calling this.
    *
    * @param languageId the language id (e.g., "java")
    * @param method the LSP method (e.g., "textDocument/hover")
    * @param params the request parameters
    * @return the server result, or null if no session or on timeout
    */
   public static Map<String, Object> request(String languageId,
         String method, Map<String, Object> params) {
      LspRegistry reg = getRegistry();
      LspSession session = reg.sessionForLanguage(languageId);
      if (null == session) {
         trace("LSP request: no session for " + languageId);
         return null;
      }
      try {
         return session.submit(method, params)
            .get(DEFAULT_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
         trace("LSP request timeout: " + method + " (" + languageId + ")");
         return null;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return null;
      } catch (ExecutionException e) {
         trace("LSP request failed: " + method + " (" + languageId
            + ") " + e.getCause());
         return null;
      }
   }

   private void registerLspCommands() {
      registerCommand(new CommandEntry("lsp.status",
         "show LSP server status", "lsp",
         (count, rcount, fvc, dot) -> {
            showStatus();
            return null;
         }));
      registerCommand(new CommandEntry("lsp.diag",
         "show LSP diagnostic summary", "lsp",
         (count, rcount, fvc, dot) -> {
            showDiagnosticSummary();
            return null;
         }));
      registerArgCommand("lsp.enable",
         "enable LSP for a language", "lsp",
         (arg, count, rcount, fvc, dot) -> {
            setLanguageEnabled(asString(arg), true);
            return null;
         });
      registerArgCommand("lsp.disable",
         "disable LSP for a language", "lsp",
         (arg, count, rcount, fvc, dot) -> {
            setLanguageEnabled(asString(arg), false);
            return null;
         });
      registerArgCommand("lsp.start",
         "start an LSP server for a language", "lsp",
         (arg, count, rcount, fvc, dot) -> {
            startLanguage(asString(arg));
            return null;
         });
      registerArgCommand("lsp.stop",
         "stop a running LSP server", "lsp",
         (arg, count, rcount, fvc, dot) -> {
            stopLanguage(asString(arg));
            return null;
         });
      registerArgCommand("lsp.rootdir",
         "set LSP project root directory", "lsp",
         (arg, count, rcount, fvc, dot) -> {
            setRootDir(asString(arg));
            return null;
         });
      registerCommand(new CommandEntry("lsp.def",
         "goto definition at cursor", "lsp",
         (count, rcount, fvc, dot) -> {
            gotoDefinition(fvc);
            return null;
         }));
      registerCommand(new CommandEntry("lsp.ref",
         "find references at cursor", "lsp",
         (count, rcount, fvc, dot) -> {
            findReferences(fvc);
            return null;
         }));
      registerCommand(new CommandEntry("lsp.hover",
         "show hover info at cursor", "lsp",
         (count, rcount, fvc, dot) -> {
            showHover(fvc);
            return null;
         }));
   }

   private static String asString(Object arg) {
      return arg instanceof String ? (String) arg : null;
   }

   private static void showStatus() {
      LspRegistry reg = getRegistry();
      if (!reg.isEnabled()) {
         UI.reportMessage("LSP: globally disabled");
         return;
      }
      List<String> status = reg.getStatus();
      if (status.isEmpty()) {
         UI.reportMessage("LSP: no servers configured");
         return;
      }
      String root = reg.getProjectRoot();
      UI.reportMessage("LSP root: " + (null == root ? "(unset)" : root));
      List<String> active = reg.getActiveSessionStatus();
      if (active.isEmpty()) {
         UI.reportMessage("LSP: no active sessions");
      } else {
         UI.reportMessage("LSP active sessions:");
         for (String line : active)
            UI.reportMessage("  " + line);
      }
      UI.reportMessage("LSP configured servers:");
      for (String line : status)
         UI.reportMessage("  " + line);
   }

   private static void showDiagnosticSummary() {
      LspDiagnostics diag = getDiagnostics();
      UI.reportMessage("LSP diagnostics: " + diag.summary()
         + " (" + diag.totalCount() + " total)");
   }

   private static void setLanguageEnabled(String lang, boolean enable)
         throws InputException {
      requireLang(lang, enable ? "lsp.enable" : "lsp.disable");
      getRegistry().setEnabled(lang, enable);
      UI.reportMessage("LSP " + (enable ? "enabled: " : "disabled: ")
         + lang);
   }

   private static void startLanguage(String lang) throws InputException {
      requireLang(lang, "lsp.start");
      LspRegistry reg = getRegistry();
      reg.setEnabled(lang, true);
      LspSession s = reg.sessionForLanguage(lang);
      if (null == s) {
         UI.reportMessage("LSP: cannot start " + lang
            + " (binary missing or project root unset)");
         return;
      }
      UI.reportMessage("LSP started: " + lang + " state="
         + s.getState());
   }

   private static void stopLanguage(String lang) throws InputException {
      requireLang(lang, "lsp.stop");
      // setEnabled(false) stops and removes the session.
      getRegistry().setEnabled(lang, false);
      UI.reportMessage("LSP stopped: " + lang);
   }

   private static void setRootDir(String dir) throws InputException {
      if (null == dir || dir.isEmpty())
         throw new InputException(
            "lsp.rootdir requires a directory argument");
      getRegistry().setProjectRoot(dir);
      UI.reportMessage("LSP root set to: " + dir);
   }

   private static void requireLang(String lang, String cmd)
         throws InputException {
      if (null == lang || lang.isEmpty())
         throw new InputException(cmd + " requires a language id");
   }

   private static String getFileUri(FvContext fvc) {
      FileDescriptor fd = fvc.edvec.fdes();
      if (fd instanceof FileDescriptor.LocalFile lf)
         return "file://" + lf.canonName;
      return null;
   }

   private static String getExtension(FvContext fvc) {
      FileDescriptor fd = fvc.edvec.fdes();
      String name = fd.canonName;
      int dot = name.lastIndexOf('.');
      return dot >= 0 ? name.substring(dot) : "";
   }

   private static Map<String, Object> textDocPosition(FvContext fvc) {
      Map<String, Object> pos = new HashMap<>();
      pos.put("line", fvc.inserty() - 1); // LSP is 0-indexed
      pos.put("character", fvc.insertx());
      Map<String, Object> textDoc = new HashMap<>();
      textDoc.put("uri", getFileUri(fvc));
      Map<String, Object> params = new HashMap<>();
      params.put("textDocument", textDoc);
      params.put("position", pos);
      return params;
   }

   private static LspSession sessionForFvc(FvContext fvc)
         throws InputException {
      String ext = getExtension(fvc);
      LspSession s = getRegistry().sessionFor(ext);
      if (null == s)
         throw new InputException(
            "LSP: no server for extension " + ext);
      return s;
   }

   private static void gotoDefinition(FvContext fvc)
         throws InputException {
      sessionForFvc(fvc); // validate
      String ext = getExtension(fvc);
      LspSession session = getRegistry().sessionFor(ext);
      Map<String, Object> params = textDocPosition(fvc);
      Map<String, Object> result = requestSync(session,
         "textDocument/definition", params);
      if (null == result) {
         UI.reportMessage("LSP: no definition found");
         return;
      }
      navigateToLocation(result, fvc);
   }

   private static void findReferences(FvContext fvc)
         throws InputException {
      sessionForFvc(fvc); // validate
      String ext = getExtension(fvc);
      LspSession session = getRegistry().sessionFor(ext);
      Map<String, Object> params = textDocPosition(fvc);
      Map<String, Object> context = new HashMap<>();
      context.put("includeDeclaration", Boolean.TRUE);
      params.put("context", context);
      Map<String, Object> result = requestSync(session,
         "textDocument/references", params);
      if (null == result) {
         UI.reportMessage("LSP: no references found");
         return;
      }
      navigateToLocation(result, fvc);
   }

   private static void showHover(FvContext fvc) throws InputException {
      sessionForFvc(fvc); // validate
      String ext = getExtension(fvc);
      LspSession session = getRegistry().sessionFor(ext);
      Map<String, Object> params = textDocPosition(fvc);
      Map<String, Object> result = requestSync(session,
         "textDocument/hover", params);
      if (null == result) {
         UI.reportMessage("LSP: no hover info");
         return;
      }
      Object contents = result.get("contents");
      if (contents instanceof Map) {
         @SuppressWarnings("unchecked")
         Map<String, Object> mc = (Map<String, Object>) contents;
         Object val = mc.get("value");
         if (val != null)
            UI.reportMessage(val.toString());
      } else if (contents instanceof String) {
         UI.reportMessage((String) contents);
      } else {
         UI.reportMessage("LSP hover: " + result);
      }
   }

   private static Map<String, Object> requestSync(LspSession session,
         String method, Map<String, Object> params) {
      try {
         return session.submit(method, params)
            .get(DEFAULT_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
         trace("LSP request timeout: " + method);
         return null;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return null;
      } catch (ExecutionException e) {
         trace("LSP request failed: " + method + " " + e.getCause());
         return null;
      }
   }

   @SuppressWarnings("unchecked")
   private static void navigateToLocation(Map<String, Object> result,
         FvContext fvc) {
      // Result can be a Location or an array of Locations
      String uri = (String) result.get("uri");
      Map<String, Object> range = (Map<String, Object>) result.get("range");
      if (null == uri && null == range) {
         // Might be wrapped in a "result" key or be an array
         Object inner = result.get("result");
         if (inner instanceof List<?> list && !list.isEmpty()) {
            Map<String, Object> first = (Map<String, Object>) list.get(0);
            uri = (String) first.get("uri");
            range = (Map<String, Object>) first.get("range");
         } else if (inner instanceof Map) {
            Map<String, Object> loc = (Map<String, Object>) inner;
            uri = (String) loc.get("uri");
            range = (Map<String, Object>) loc.get("range");
         }
      }
      if (null == uri) {
         UI.reportMessage("LSP: location has no uri");
         return;
      }
      String path = uri.startsWith("file://")
         ? uri.substring(7) : uri;
      int line = 1;
      if (null != range) {
         Map<String, Object> start =
            (Map<String, Object>) range.get("start");
         if (null != start) {
            Object lnum = start.get("line");
            if (lnum instanceof Number)
               line = ((Number) lnum).intValue() + 1; // LSP->javi
         }
      }
      try {
         FvContext target = FileList.openFileName(path, fvc.vi);
         target.cursoryabs(line);
         UI.reportMessage("LSP: " + path + ":" + line);
      } catch (Exception e) {
         UI.reportMessage("LSP navigate: " + e.getMessage());
      }
   }

   /** Stops all sessions; used during shutdown. */
   public static void shutdown() {
      if (null != registry) {
         registry.stopAll();
      }
   }

   /**
    * Notification sink that bridges session-thread events to the AWT
    * thread before touching the diagnostics store. Implementations
    * are required to be thread-safe by {@link LspSession}.
    */
   private static final class SinkImpl
         implements LspSession.NotificationSink {
      public void onDiagnostics(LspSession session, String uri,
            List<Map<String, Object>> diags) {
         final String source = session.getLanguageId();
         java.awt.EventQueue.invokeLater(() -> {
            try {
               diagnostics.update(source, uri, diags);
            } catch (Exception e) {
               trace("LSP diagnostics update failed: " + e);
            }
         });
      }

      public void onStateChanged(LspSession session,
            LspSession.State newState) {
         trace("LSP state: " + session.getLanguageId()
            + " -> " + newState);
      }
   }

   @Override
   protected Object doroutine(int rnum, Object arg, int count,
         int rcount, FvContext fvc, boolean dotmode)
         throws IOException, InterruptedException, InputException {
      throw new InputException(
         "LspCommands has no index-based commands (rnum=" + rnum + ")");
   }
}
