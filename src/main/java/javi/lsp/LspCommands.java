package javi.lsp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javi.FvContext;
import javi.InputException;
import javi.Plugin;
import javi.Position;
import javi.Rgroup;
import javi.TagLookupProvider;
import javi.TextEdit;
import javi.UI;

import static history.Tools.trace;

/**
 * LSP command group for the vi command system.
 *
 * <p>Integrates LSP features into Javi's command dispatch system as a
 * {@link Rgroup} subclass. Registers vi-style commands that invoke
 * LSP operations on the current file and cursor position.</p>
 *
 * <h2>Registered Commands</h2>
 * <ul>
 *   <li>{@code lsp.def} - Go to definition (LSP textDocument/definition)</li>
 *   <li>{@code lsp.ref} - Find references (LSP textDocument/references)</li>
 *   <li>{@code lsp.hover} - Show hover info (LSP textDocument/hover)</li>
 *   <li>{@code lsp.comp} - Request completions (LSP textDocument/completion)</li>
 *   <li>{@code lsp.status} - Show LSP server status</li>
 *   <li>{@code lsp.restart} - Restart LSP server</li>
 *   <li>{@code lsp.toggle} - Enable/disable LSP globally</li>
 *   <li>{@code lsp.diag} - Show diagnostics for current file</li>
 *   <li>{@code lsp.config} - Show/set server configuration</li>
 * </ul>
 *
 * <h2>Position Mapping</h2>
 * <p>Javi uses 1-based line numbers and column positions. LSP uses
 * 0-based. The conversion is handled in the command methods.</p>
 *
 * <h2>Integration with Ctags</h2>
 * <p>When LSP is not available (server not running, timeout, etc.),
 * commands like {@code lspdef} can fall back to the existing ctags
 * system. The fallback is configured per-command.</p>
 *
 * <h2>Integration with :ta</h2>
 * <p>Rather than dedicating key bindings to LSP commands, the LSP
 * plugin registers as a {@link TagLookupProvider} so that {@code :ta}
 * (and Ctrl-]) tries LSP definition/references before ctags/mkid.
 * Explicit commands ({@code :lspdef}, {@code :lspref}, etc.) remain
 * available for direct invocation.</p>
 *
 * @see LspManager
 * @see Rgroup
 */
public final class LspCommands extends Rgroup
      implements Plugin, TagLookupProvider {

   /** Auto-instantiate when class is loaded via Class.forName(). */
   static {
      new LspCommands();
   }

   private static final int CMD_DEF      = 1;
   private static final int CMD_REF      = 2;
   private static final int CMD_HOVER    = 3;
   private static final int CMD_COMP     = 4;
   private static final int CMD_STATUS   = 5;
   private static final int CMD_RESTART  = 6;
   private static final int CMD_TOGGLE   = 7;
   private static final int CMD_DIAG     = 8;
   private static final int CMD_CONFIG   = 9;

   /** Diagnostic collector wired to the LspManager. */
   private final DiagnosticDisplay diagnosticDisplay;

   /**
    * Creates and registers LSP commands.
    *
    * <p>Call this from the editor initialization to make LSP commands
    * available. Commands are registered in the global command hash
    * and can be invoked via ex-mode or key bindings.</p>
    */
   public LspCommands() {
      final String[] rnames = {
         "",
         "lsp.def",       // go to definition
         "lsp.ref",       // find references
         "lsp.hover",     // hover information
         "lsp.comp",      // code completion
         "lsp.status",    // show LSP status
         "lsp.restart",   // restart LSP server
         "lsp.toggle",    // enable/disable LSP
         "lsp.diag",      // show diagnostics
         "lsp.config",    // show/set server configuration
      };
      final String[] descs = {
         "",
         "Go to definition (LSP)",
         "Find all references (LSP)",
         "Show hover/type info (LSP)",
         "Show code completions (LSP)",
         "Show LSP server status",
         "Restart LSP server",
         "Enable/disable LSP globally",
         "Show diagnostics for current file",
         "Show/set LSP server configuration",
      };
      register(rnames, descs);

      registerArgCommand("lsp.disable",
         "disable LSP for a language (e.g. lsp.disable java)", "lsp",
         (arg, count, rcount, fvc, dot) -> {
            String lang = arg instanceof String ? ((String) arg).trim() : "";
            if (lang.isEmpty())
               throw new InputException(
                  "lsp.disable: specify language (e.g. lsp.disable java)");
            LspManager.getInstance().disableLanguage(lang);
            UI.reportMessage("LSP disabled for " + lang);
            return null;
         });

      registerArgCommand("lsp.rootdir",
         "set LSP root directory for a language (e.g. lsp.rootdir java /path)",
         "lsp",
         (arg, count, rcount, fvc, dot) -> {
            String val = arg instanceof String ? ((String) arg).trim() : "";
            int sp = val.indexOf(' ');
            if (sp < 0)
               throw new InputException(
                  "lsp.rootdir: specify language and path"
                  + " (e.g. lsp.rootdir java /src/project)");
            String lang = val.substring(0, sp).trim();
            String dir = val.substring(sp + 1).trim();
            LspManager.getInstance().setRootDir(lang, dir);
            UI.reportMessage("LSP rootdir for " + lang + " = " + dir);
            return null;
         });

      registerArgCommand("lsp.enable",
         "enable LSP for a language (e.g. lsp.enable java)", "lsp",
         (arg, count, rcount, fvc, dot) -> {
            String lang = arg instanceof String
               ? ((String) arg).trim() : "";
            if (lang.isEmpty())
               throw new InputException(
                  "lsp.enable: specify language"
                  + " (e.g. lsp.enable java)");
            LspManager.getInstance().enableLanguage(lang);
            UI.reportMessage("LSP enabled for " + lang);
            return null;
         });

      registerArgCommand("lsp.sourcepath",
         "add source path for a language"
         + " (e.g. lsp.sourcepath java src/main/java)", "lsp",
         (arg, count, rcount, fvc, dot) -> {
            String val = arg instanceof String
               ? ((String) arg).trim() : "";
            if (val.isEmpty())
               throw new InputException(
                  "lsp.sourcepath: specify path or language and path"
                  + " (e.g. lsp.sourcepath java src/main/java"
                  + " or lsp.sourcepath .)");
            int sp = val.indexOf(' ');
            String lang;
            String path;
            if (sp < 0) {
               // Single arg: treat as path for all configured languages
               lang = null;
               path = val;
            } else {
               lang = val.substring(0, sp).trim();
               path = val.substring(sp + 1).trim();
            }
            LspManager mgri = LspManager.getInstance();
            if (null == lang) {
               for (String lid : new String[]{
                     "java", "c", "python", "typescript", "rust"}) {
                  if (null != mgri.getConfig(lid))
                     mgri.addSourcePath(lid, path);
               }
               UI.reportMessage(
                  "LSP: added source path for all languages: " + path);
            } else {
               mgri.addSourcePath(lang, path);
               UI.reportMessage(
                  "LSP: added source path for " + lang + ": " + path);
            }
            return null;
         });

      // Spell checker (overlay LSP) control
      registerArgCommand("lsp.spell",
         "spell checker control: on|off|status|restart", "lsp",
         (arg, count, rcount, fvc, dot) -> {
            String subcmd = arg instanceof String
               ? ((String) arg).trim().toLowerCase() : "";
            LspManager mgr = LspManager.getInstance();
            if (subcmd.equals("off")) {
               mgr.disableLanguage("harper");
               mgr.restartServer("harper");
               UI.reportMessage("Spell checker disabled");
            } else if (subcmd.equals("on")) {
               mgr.enableLanguage("harper");
               mgr.startServerForLanguage("harper");
               // Send current file to harper so it produces
               // diagnostics immediately
               sendCurrentFileToOverlay(fvc, mgr, "harper");
               UI.reportMessage("Spell checker enabled");
            } else if (subcmd.equals("restart")) {
               mgr.restartServer("harper");
               mgr.startServerForLanguage("harper");
               sendCurrentFileToOverlay(fvc, mgr, "harper");
               UI.reportMessage("Spell checker restarted");
            } else {
               // Default: show status
               boolean running = mgr.isOverlayRunning("harper");
               boolean disabled =
                  mgr.isLanguageDisabled("harper");
               LspServerConfig cfg = mgr.getConfig("harper");
               boolean available = (null != cfg && cfg.isAvailable());
               StringBuilder sb = new StringBuilder("Spell checker: ");
               if (disabled) {
                  sb.append("disabled");
               } else if (running) {
                  sb.append("running (harper-ls)");
               } else if (!available) {
                  sb.append("not installed"
                     + " (install: brew install harper)");
               } else {
                  sb.append("not running"
                     + " (use :lsp.spell on)");
               }
               UI.reportMessage(sb.toString());
            }
            return null;
         });

      // Wire up diagnostics collection
      diagnosticDisplay = new DiagnosticDisplay();
      LspManager.getInstance().setDiagnosticHandler(diagnosticDisplay);

      // Register as tag lookup providers for :ta integration.
      // Definition provider first (this), references provider later.
      // Ctags and lid providers are registered by PosListList.Cmd
      // between these two, giving the order:
      // lspdef, ctags, lspref, lid
      javi.PosListList.Cmd.registerTagProvider(this);
      javi.PosListList.Cmd.registerTagProvider(
         new LspRefLookupProvider());

      // Register for file change events (open/close/change)
      javi.EditContainer.registerChangeListen(new LspChangeHandler());

      // Register LSP help topic dynamically
      javi.HelpSystem.registerHelpTopic("lsp",
         new String[]{"languageserver"},
         LspCommands::appendLspHelp);
   }

   /**
    * Append LSP help content. Called by HelpSystem when
    * the user requests {@code :help lsp}.
    */
   private static void appendLspHelp() {
      javi.HelpSystem.appendLine("LSP (LANGUAGE SERVER PROTOCOL)");
      javi.HelpSystem.appendLine("==============================");
      javi.HelpSystem.appendLine("");
      javi.HelpSystem.appendLine(
         "Javi has built-in LSP client support for code intelligence.");
      javi.HelpSystem.appendLine(
         "Language servers provide features like go-to-definition,");
      javi.HelpSystem.appendLine(
         "find references, completions, hover info, and diagnostics.");
      javi.HelpSystem.appendLine("");
      javi.HelpSystem.appendLine("COMMANDS");
      javi.HelpSystem.appendLine("--------");
      javi.HelpSystem.appendLine(
         "  :lsp.def         Go to definition at cursor");
      javi.HelpSystem.appendLine(
         "  :lsp.ref         Find all references at cursor");
      javi.HelpSystem.appendLine(
         "  :lsp.hover       Show type/doc info at cursor");
      javi.HelpSystem.appendLine(
         "  :lsp.comp        Show code completions at cursor");
      javi.HelpSystem.appendLine(
         "  :lsp.diag        Show diagnostics for current file");
      javi.HelpSystem.appendLine(
         "  :lsp.status      Show LSP server status");
      javi.HelpSystem.appendLine(
         "  :lsp.restart     Restart server"
         + " (e.g. :lsp.restart java)");
      javi.HelpSystem.appendLine(
         "  :lsp.toggle      Enable/disable LSP globally");
      javi.HelpSystem.appendLine(
         "  :lsp.config      Show server configurations");
      javi.HelpSystem.appendLine(
         "  :lsp.config lang=cmd"
         + "  Set server command for a language");
      javi.HelpSystem.appendLine("");
      javi.HelpSystem.appendLine("SPELL CHECKER");
      javi.HelpSystem.appendLine("-------------");
      javi.HelpSystem.appendLine(
         "  :lsp.spell          Show spell checker status");
      javi.HelpSystem.appendLine(
         "  :lsp.spell on       Enable spell checker");
      javi.HelpSystem.appendLine(
         "  :lsp.spell off      Disable spell checker");
      javi.HelpSystem.appendLine(
         "  :lsp.spell restart  Restart spell checker");
      javi.HelpSystem.appendLine(
         "  Uses harper-ls: grammar + spell checking.");
      javi.HelpSystem.appendLine(
         "  Checks comments/strings in source code,");
      javi.HelpSystem.appendLine(
         "  and full text in markdown/typst files.");
      javi.HelpSystem.appendLine(
         "  Install: brew install harper");
      javi.HelpSystem.appendLine("");
      javi.HelpSystem.appendLine(":ta INTEGRATION");
      javi.HelpSystem.appendLine("---------------");
      javi.HelpSystem.appendLine(
         "  :ta (and Ctrl-]) tries LSP before ctags/mkid:");
      javi.HelpSystem.appendLine(
         "  1. LSP go-to-definition"
         + " (cursor on usage -> definition)");
      javi.HelpSystem.appendLine(
         "  2. LSP find-references"
         + " (cursor on definition -> usages)");
      javi.HelpSystem.appendLine(
         "  3. Fall through to ctags/mkid if LSP unavailable");
      javi.HelpSystem.appendLine(
         "  Hover info is inserted into the definition"
         + " description.");
      javi.HelpSystem.appendLine("");
      javi.HelpSystem.appendLine("SUPPORTED LANGUAGES");
      javi.HelpSystem.appendLine("-------------------");
      javi.HelpSystem.appendLine(
         "  Java        jdtls (Eclipse JDT Language Server)");
      javi.HelpSystem.appendLine(
         "  TypeScript  typescript-language-server");
      javi.HelpSystem.appendLine(
         "  Python      pyright-langserver");
      javi.HelpSystem.appendLine("  C/C++       clangd");
      javi.HelpSystem.appendLine("  Rust        rust-analyzer");
      javi.HelpSystem.appendLine("");
      javi.HelpSystem.appendLine("Type :help for index.");
   }

   /**
    * FileChangeListener that forwards open/close/change events
    * to LspManager for document synchronization.
    */
   private static final class LspChangeHandler
         extends javi.EditContainer.FileChangeListener {

      private static final long THROTTLE_MS = 500;
      private long lastChangeTime;

      public void addedLines(javi.FileDescriptor fd,
            int count, int index) {
         // Not used for LSP — contentChanged handles this
      }

      @Override
      public void fileOpened(javi.EditContainer<?> ec) {
         if (!ec.fdes().isLocalFile())
            return;
         LspManager.getInstance().notifyDidOpen(
            ec.fdes().getCanonName(), ec.getDocumentText());
      }

      @Override
      public void fileClosed(javi.EditContainer<?> ec) {
         try {
            if (!ec.fdes().isLocalFile())
               return;
            LspManager.getInstance().notifyDidClose(
               ec.fdes().getCanonName());
         } catch (Exception e) {
            // Ignore — may be called during shutdown
         }
      }

      @Override
      public void contentChanged(javi.EditContainer<?> ec) {
         if (!ec.fdes().isLocalFile())
            return;
         long now = System.currentTimeMillis();
         if (now - lastChangeTime < THROTTLE_MS)
            return;
         lastChangeTime = now;
         LspManager.getInstance().notifyDidChange(
            ec.fdes().getCanonName(), ec.getDocumentText());
      }
   }

   /**
    * This provider returns definition positions.
    */
   @Override
   public LookupType getType() {
      return LookupType.DEFINITIONS;
   }

   /**
    * TagLookupProvider implementation — tries LSP definition first,
    * then references as fallback, before falling through to ctags.
    *
    * <p>This allows {@code :ta} to navigate from a usage to its
    * definition, or from a definition to its references — all via
    * a single command without dedicated key bindings.</p>
    *
    * <p>Only uses LSP if the server is already running and initialized.
    * Will NOT start a server or wait for one that is initializing.</p>
    */
   @Override
   public boolean tryLookup(FvContext fvc) {
      LspManager mgr = LspManager.getInstance();
      if (!mgr.isEnabled())
         return false;

      String filePath = getFilePathStatic(fvc);
      if (null == filePath)
         return false;

      // Only proceed if server is already running — never block :ta
      if (!mgr.hasRunningClient(filePath))
         return false;

      if (tryGotoDefinitionFast(fvc, filePath, mgr))
         return true;
      return tryGotoReferencesFast(fvc, filePath, mgr);
   }

   /**
    * Returns LSP definition positions for merging into :ta results.
    * Entries are annotated with "lspdef" in their comment field.
    *
    * <p>Tries cursor-position definition first (works for Ctrl-]),
    * then falls back to workspace/symbol search with the tag name
    * (works for explicit {@code :ta name}).</p>
    *
    * <p>Only queries LSP if a server is already running. Will not
    * start a server or block waiting for initialization.</p>
    */
   @Override
   @SuppressWarnings("unchecked")
   public List<Position> lookupPositions(FvContext fvc, String tagName) {
      LspManager mgr = LspManager.getInstance();
      if (!mgr.isEnabled())
         return java.util.Collections.emptyList();

      String filePath = getFilePathStatic(fvc);
      String projectRoot = null;
      List<Map<String, Object>> locations = null;

      // Try current file's client first (cursor-based definition)
      if (null != filePath && mgr.hasRunningClient(filePath)) {
         int line = fvc.inserty() - 1;
         int character = fvc.insertx();
         locations = mgr.definition(filePath, line, character);
         projectRoot = mgr.getProjectRoot(filePath);
         locations = filterByProjectRoot(locations, projectRoot);
      }

      // Fallback: workspace/symbol search with tagName
      // Try current file's client first, then all running clients
      if ((null == locations || locations.isEmpty())
            && null != tagName && !tagName.isEmpty()) {
         if (null != filePath && mgr.hasRunningClient(filePath)) {
            locations = workspaceSymbolToLocations(
               mgr, filePath, tagName, projectRoot);
         }

         // If still empty, try all running clients
         if (null == locations || locations.isEmpty()) {
            List<String> runningLangs = mgr.getRunningLanguageIds();
            for (String langId : runningLangs) {
               String langRoot = mgr.getRootDirOverride(langId);
               if (null == langRoot)
                  langRoot = System.getProperty("user.dir", ".");
               locations = workspaceSymbolByLangToLocations(
                  mgr, langId, tagName, langRoot);
               if (null != locations && !locations.isEmpty()) {
                  projectRoot = langRoot;
                  break;
               }
            }
         }
      }

      if (null == locations || locations.isEmpty())
         return java.util.Collections.emptyList();

      List<Position> result = new ArrayList<>();
      for (Map<String, Object> loc : locations) {
         String uri = (String) loc.get("uri");
         if (null == uri)
            uri = (String) loc.get("targetUri");
         if (null == uri)
            continue;

         String path = LspClient.uriToPath(uri);
         int defLine = 1;
         Object rangeObj = loc.get("range");
         if (null == rangeObj)
            rangeObj = loc.get("targetSelectionRange");
         if (rangeObj instanceof Map) {
            Map<String, Object> range = (Map<String, Object>) rangeObj;
            Object start = range.get("start");
            if (start instanceof Map) {
               Object lineObj = ((Map<String, Object>) start).get("line");
               if (lineObj instanceof Number)
                  defLine = ((Number) lineObj).intValue() + 1;
            }
         }

         result.add(new Position(0, defLine, path, "lspdef"));
      }
      trace("lookupPositions '" + tagName + "' => "
         + result.size() + " positions");
      return result;
   }

   /**
    * Fast definition lookup for :ta path — no hover enrichment,
    * no server startup. Only proceeds if server is already running.
    */
   private static boolean tryGotoDefinitionFast(FvContext fvc,
         String filePath, LspManager mgr) {
      int line = fvc.inserty() - 1;
      int character = fvc.insertx();

      List<Map<String, Object>> locations =
         mgr.definition(filePath, line, character);

      String projectRoot = mgr.getProjectRoot(filePath);
      locations = filterByProjectRoot(locations, projectRoot);

      if (null == locations || locations.isEmpty())
         return false;

      try {
         navigateToLocationStatic(locations.get(0), fvc, "lsp:definition");
         return true;
      } catch (InputException e) {
         trace("LSP: definition navigation failed: " + e);
         return false;
      }
   }

   /**
    * Fast references lookup for :ta path — no hover enrichment,
    * no server startup. Only proceeds if server is already running.
    */
   private static boolean tryGotoReferencesFast(FvContext fvc,
         String filePath, LspManager mgr) {
      int line = fvc.inserty() - 1;
      int character = fvc.insertx();

      List<Map<String, Object>> locations =
         mgr.references(filePath, line, character);

      String projectRoot = mgr.getProjectRoot(filePath);
      locations = filterByProjectRoot(locations, projectRoot);

      if (null == locations || locations.isEmpty())
         return false;

      try {
         String comment = locations.size() + " reference(s)";
         navigateToLocationStatic(locations.get(0), fvc, comment);
         return true;
      } catch (InputException e) {
         trace("LSP: references navigation failed: " + e);
         return false;
      }
   }

   /**
    * Attempts to navigate to the first reference of the symbol
    * at the cursor position. Used as a fallback when definition
    * returns nothing (e.g., cursor is already on the definition).
    *
    * @param fvc the current file-view context
    * @return true if references were found and navigated to
    */
   private static boolean tryGotoReferences(FvContext fvc) {
      LspManager mgr = LspManager.getInstance();
      if (!mgr.isEnabled())
         return false;

      String filePath = getFilePathStatic(fvc);
      if (null == filePath)
         return false;

      if (null == mgr.getClientForFile(filePath))
         return false;

      int line = fvc.inserty() - 1;
      int character = fvc.insertx();

      List<Map<String, Object>> locations =
         mgr.references(filePath, line, character);

      // Filter to current project root
      String projectRoot = mgr.getProjectRoot(filePath);
      locations = filterByProjectRoot(locations, projectRoot);

      if (null == locations || locations.isEmpty())
         return false;

      String comment = locations.size() + " reference(s)";
      // Enrich with hover info
      String hover = mgr.hover(filePath, line, character);
      if (null != hover && !hover.isEmpty()) {
         if (hover.length() > 100)
            hover = hover.substring(0, 97) + "...";
         comment = hover + " [" + locations.size() + " ref]";
      }

      try {
         navigateToLocationStatic(locations.get(0), fvc, comment);
         return true;
      } catch (InputException e) {
         trace("LSP: references navigation failed: " + e);
         return false;
      }
   }

   /**
    * Returns LSP hover info to enrich Position comment fields.
    */
   @Override
   public String getHoverInfo(FvContext fvc) {
      LspManager mgr = LspManager.getInstance();
      if (!mgr.isEnabled())
         return null;
      String filePath = getFilePathStatic(fvc);
      if (null == filePath)
         return null;
      if (null == mgr.getClientForFile(filePath))
         return null;
      int line = fvc.inserty() - 1;
      int character = fvc.insertx();
      String hover = mgr.hover(filePath, line, character);
      if (null != hover && hover.length() > 120)
         hover = hover.substring(0, 117) + "...";
      return hover;
   }

   /**
    * Tag lookup provider that returns LSP reference positions.
    *
    * <p>Registered as a separate provider with type REFERENCES so
    * that reference results appear after ctags in the merged
    * position list (order: lspdef, ctags, lspref, lid).</p>
    */
   private static final class LspRefLookupProvider
         implements TagLookupProvider {

      @Override
      public LookupType getType() {
         return LookupType.REFERENCES;
      }

      @Override
      public boolean tryLookup(FvContext fvc) {
         return false;
      }

      @Override
      @SuppressWarnings("unchecked")
      public List<Position> lookupPositions(
            FvContext fvc, String tagName) {
         LspManager mgr = LspManager.getInstance();
         if (!mgr.isEnabled())
            return java.util.Collections.emptyList();

         String filePath = getFilePathStatic(fvc);
         if (null == filePath
               || !mgr.hasRunningClient(filePath))
            return java.util.Collections.emptyList();

         int line = fvc.inserty() - 1;
         int character = fvc.insertx();

         List<Map<String, Object>> locations =
            mgr.references(filePath, line, character);

         String projectRoot = mgr.getProjectRoot(filePath);
         locations = filterByProjectRoot(
            locations, projectRoot);

         if (null == locations || locations.isEmpty())
            return java.util.Collections.emptyList();

         List<Position> result = new ArrayList<>();
         for (Map<String, Object> loc : locations) {
            String uri = (String) loc.get("uri");
            if (null == uri)
               continue;
            String path = LspClient.uriToPath(uri);
            int defLine = 1;
            Object rangeObj = loc.get("range");
            if (rangeObj instanceof Map) {
               Map<String, Object> range =
                  (Map<String, Object>) rangeObj;
               Object start = range.get("start");
               if (start instanceof Map) {
                  Object lineObj =
                     ((Map<String, Object>) start)
                        .get("line");
                  if (lineObj instanceof Number)
                     defLine = ((Number) lineObj)
                        .intValue() + 1;
               }
            }
            result.add(new Position(
               0, defLine, path, "lspref"));
         }
         trace("LspRefLookupProvider '" + tagName
            + "' => " + result.size() + " positions");
         return result;
      }
   }

   /**
    * Dispatches LSP commands to their implementations.
    *
    * @param rnum the command index
    * @param arg optional command argument
    * @param count the repeat count
    * @param rcount the raw count
    * @param fvc the current file-view context
    * @param dotmode whether executing via dot-repeat
    * @return command result, or null
    * @throws IOException if an I/O error occurs
    * @throws InputException if the input is invalid
    */
   @Override
   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode)
         throws IOException, InputException {
      switch (rnum) {
         case CMD_DEF:
            gotoDefinition(fvc);
            return null;
         case CMD_REF:
            findReferences(fvc);
            return null;
         case CMD_HOVER:
            showHover(fvc);
            return null;
         case CMD_COMP:
            showCompletion(fvc);
            return null;
         case CMD_STATUS:
            showStatus();
            return null;
         case CMD_RESTART:
            restartServer(fvc, arg);
            return null;
         case CMD_TOGGLE:
            toggleLsp();
            return null;
         case CMD_DIAG:
            showDiagnostics(fvc);
            return null;
         case CMD_CONFIG:
            showConfig(arg, fvc);
            return null;
         default:
            throw new RuntimeException("LspCommands: unknown command "
               + rnum);
      }
   }

   /**
    * Attempts LSP go-to-definition for Ctrl-] fallback.
    *
    * <p>Called by the ctags system ({@code gototag}) to try LSP
    * before falling back to ctags. Returns true if LSP handled
    * the request, false if ctags should proceed.</p>
    *
    * @param fvc the current file-view context
    * @return true if LSP navigated to a definition
    */
   public static boolean tryGotoDefinition(FvContext fvc) {
      LspManager mgr = LspManager.getInstance();
      if (!mgr.isEnabled())
         return false;

      String filePath = getFilePathStatic(fvc);
      if (null == filePath)
         return false;

      // Only try if an LSP client is available for this file type
      if (null == mgr.getClientForFile(filePath))
         return false;

      int line = fvc.inserty() - 1;
      int character = fvc.insertx();

      List<Map<String, Object>> locations =
         mgr.definition(filePath, line, character);

      // Filter to current project root
      String projectRoot = mgr.getProjectRoot(filePath);
      locations = filterByProjectRoot(locations, projectRoot);

      if (null == locations || locations.isEmpty())
         return false;

      // Fetch hover info to enrich the definition description
      String hover = mgr.hover(filePath, line, character);
      String comment = "lsp:definition";
      if (null != hover && !hover.isEmpty()) {
         if (hover.length() > 120)
            hover = hover.substring(0, 117) + "...";
         comment = hover;
      }

      try {
         navigateToLocationStatic(locations.get(0), fvc, comment);
         return true;
      } catch (InputException e) {
         trace("LSP: definition navigation failed: " + e);
         return false;
      }
   }

   /**
    * Go to definition at current cursor position.
    *
    * <p>Gets the file path and cursor position from the current
    * FvContext, sends a definition request to the LSP server, and
    * navigates to the first result location.</p>
    *
    * @param fvc the current file-view context
    * @throws InputException if no definition found
    */
   private void gotoDefinition(FvContext fvc) throws InputException {
      LspManager mgr = LspManager.getInstance();
      String filePath = getFilePath(fvc);
      if (null == filePath) {
         UI.reportMessage("LSP: no file path available");
         return;
      }

      // Fast path: if no server is running, report immediately
      if (!mgr.hasRunningClient(filePath)) {
         String ext = LspServerConfig.getExtension(filePath);
         UI.reportMessage("LSP: no server active for " + ext
            + " — use :lsp.restart to start");
         return;
      }

      // Convert Javi 1-based to LSP 0-based
      int line = fvc.inserty() - 1;
      int character = fvc.insertx();

      // Capture word under cursor before releasing lock
      String lineText = fvc.at().toString();
      String word = extractWordAt(lineText, character);

      // Release the global lock while waiting for LSP response
      // to avoid blocking paint/status threads
      javi.EventQueue.biglock2.unlock();
      List<Map<String, Object>> locations;
      try {
         locations = mgr.definition(filePath, line, character);
      } finally {
         javi.EventQueue.biglock2.lock();
      }

      // Filter to current project root
      String projectRoot = mgr.getProjectRoot(filePath);
      locations = filterByProjectRoot(locations, projectRoot);

      if (null == locations || locations.isEmpty()) {
         // Fallback: try workspace/symbol with word under cursor
         if (null != word && !word.isEmpty()) {
            javi.EventQueue.biglock2.unlock();
            try {
               locations = workspaceSymbolToLocations(
                  mgr, filePath, word, projectRoot);
            } finally {
               javi.EventQueue.biglock2.lock();
            }
         }
      }

      if (null == locations || locations.isEmpty()) {
         UI.reportMessage("LSP: no definition found");
         return;
      }

      navigateToLocation(locations.get(0), fvc);
   }

   /**
    * Find all references at current cursor position.
    *
    * @param fvc the current file-view context
    * @throws InputException if no references found
    */
   private void findReferences(FvContext fvc) throws InputException {
      LspManager mgr = LspManager.getInstance();
      String filePath = getFilePath(fvc);
      if (null == filePath) {
         UI.reportMessage("LSP: no file path available");
         return;
      }

      // Fast path: if no server is running, report immediately
      if (!mgr.hasRunningClient(filePath)) {
         String ext = LspServerConfig.getExtension(filePath);
         UI.reportMessage("LSP: no server active for " + ext
            + " — use :lsp.restart to start");
         return;
      }

      int line = fvc.inserty() - 1;
      int character = fvc.insertx();

      // Release the global lock while waiting for LSP response
      javi.EventQueue.biglock2.unlock();
      List<Map<String, Object>> locations;
      try {
         locations = mgr.references(filePath, line, character);
      } finally {
         javi.EventQueue.biglock2.lock();
      }

      // Filter to current project root
      String projectRoot = mgr.getProjectRoot(filePath);
      locations = filterByProjectRoot(locations, projectRoot);

      if (null == locations || locations.isEmpty()) {
         UI.reportMessage("LSP: no references found");
         return;
      }

      // Report number of references found
      UI.reportMessage("LSP: " + locations.size() + " reference(s) found");

      // Navigate to first reference
      navigateToLocation(locations.get(0), fvc);
   }

   /**
    * Shows hover information at cursor position on the status bar.
    *
    * @param fvc the current file-view context
    */
   private void showHover(FvContext fvc) {
      LspManager mgr = LspManager.getInstance();
      String filePath = getFilePath(fvc);
      if (null == filePath) {
         UI.reportMessage("LSP: no file path available");
         return;
      }

      // Fast path: if no server is running, report immediately
      if (!mgr.hasRunningClient(filePath)) {
         String ext = LspServerConfig.getExtension(filePath);
         UI.reportMessage("LSP: no server active for " + ext
            + " — use :lsp.restart to start");
         return;
      }

      int line = fvc.inserty() - 1;
      int character = fvc.insertx();

      String hoverText = mgr.hover(filePath, line, character);

      if (null == hoverText || hoverText.isEmpty()) {
         UI.reportMessage("LSP: no hover information");
      } else {
         // Truncate for status bar display
         if (hoverText.length() > 120) {
            hoverText = hoverText.substring(0, 117) + "...";
         }
         // Remove newlines for single-line status display
         hoverText = hoverText.replace('\n', ' ').replace('\r', ' ');
         UI.reportMessage(hoverText);
      }
   }

   /**
    * Shows completion suggestions at cursor position in a navigable buffer.
    *
    * <p>Opens completions in a scratch buffer. Navigate with j/k arrows,
    * press Enter on a line to insert that completion at the original cursor
    * position. Use ZZ or :q to dismiss.</p>
    *
    * @param fvc the current file-view context
    */
   @SuppressWarnings("unchecked")
   private void showCompletion(FvContext fvc) {
      LspManager mgr = LspManager.getInstance();
      String filePath = getFilePath(fvc);
      if (null == filePath) {
         UI.reportMessage("LSP: no file path available");
         return;
      }

      int line = fvc.inserty() - 1;
      int character = fvc.insertx();

      List<Map<String, Object>> items =
         mgr.completion(filePath, line, character);

      if (null == items || items.isEmpty()) {
         UI.reportMessage("LSP: no completions");
         return;
      }

      // Build completion text: each line is "label  detail"
      StringBuilder content = new StringBuilder();
      List<String> insertTexts = new ArrayList<>();
      for (Map<String, Object> item : items) {
         String label = String.valueOf(item.get("label"));
         Object insertObj = item.get("insertText");
         String insertText = insertObj != null
            ? String.valueOf(insertObj) : label;
         insertTexts.add(insertText);

         Object detail = item.get("detail");
         if (detail != null)
            content.append(label).append("  ").append(detail);
         else
            content.append(label);
         content.append('\n');
      }

      // Store context for insertion
      lastCompletionInserts = insertTexts;
      lastCompletionFvc = fvc;
      lastCompletionX = fvc.insertx();
      lastCompletionY = fvc.inserty();

      // Open as scratch buffer
      try {
         javi.StringIoc sio = new javi.StringIoc(
            "*lsp-completions*", content.toString());
         TextEdit<String> buf = new TextEdit<>(sio, sio.prop);
         buf.setReadOnly(true);
         FvContext.connectFv(buf, fvc.vi);
         UI.reportMessage(items.size() + " completions — Enter to insert, "
            + "ZZ to dismiss");
      } catch (InputException e) {
         UI.reportMessage("LSP: failed to show completions: " + e);
      }
   }

   /** Insert texts for last completion shown (indexed by line). */
   private static List<String> lastCompletionInserts;
   /** FvContext of the buffer that requested completions. */
   private static FvContext lastCompletionFvc;
   /** Cursor X at completion request. */
   private static int lastCompletionX;
   /** Cursor Y at completion request. */
   private static int lastCompletionY;

   /**
    * Shows the LSP status on the status bar.
    */
   private void showStatus() {
      LspManager mgr = LspManager.getInstance();
      UI.reportMessage(mgr.getStatus());
   }

   /**
    * Restarts the LSP server. Accepts an optional language argument
    * (e.g. {@code :lsp.restart java}). Without argument, determines
    * the language from the current file extension.
    *
    * @param fvc the current file-view context
    * @param arg optional language id string
    */
   private void restartServer(FvContext fvc, Object arg) {
      LspManager mgr = LspManager.getInstance();

      // If language argument provided, restart by language id
      String langArg = (null != arg) ? arg.toString().trim() : "";
      if (!langArg.isEmpty()) {
         LspServerConfig config = mgr.getConfig(langArg);
         if (null == config) {
            UI.reportMessage(
               "LSP: unknown language '" + langArg
               + "' — known: java, c, python, typescript, rust");
            return;
         }
         if (mgr.startServerForLanguage(langArg)) {
            UI.reportMessage(
               "LSP: restarted " + langArg + " server");
         } else {
            UI.reportMessage(
               "LSP: failed to start " + langArg + " server");
         }
         return;
      }

      // No argument — determine from current file
      String filePath = getFilePath(fvc);
      if (null == filePath) {
         UI.reportMessage(
            "LSP: specify language (e.g. :lsp.restart java)"
            + " or navigate to a file of that type");
         return;
      }

      String ext = LspServerConfig.getExtension(filePath);
      LspServerConfig config = LspServerConfig.forExtension(
         mgr.getConfig("java") != null
            ? getAllConfigs(mgr) : LspServerConfig.getDefaults(), ext);
      if (null == config) {
         UI.reportMessage(
            "LSP: no server configured for " + ext
            + " — specify language (e.g. :lsp.restart java)");
         return;
      }

      if (mgr.startServerForLanguage(config.languageId)) {
         UI.reportMessage(
            "LSP: restarted " + config.languageId + " server");
      } else {
         UI.reportMessage(
            "LSP: failed to start " + config.languageId + " server");
      }
   }

   /**
    * Collects all configured server configs for extension matching.
    */
   private static Map<String, LspServerConfig> getAllConfigs(
         LspManager mgr) {
      Map<String, LspServerConfig> result = new java.util.HashMap<>();
      for (String lang : new String[]{
            "java", "c", "python", "typescript", "rust"}) {
         LspServerConfig cfg = mgr.getConfig(lang);
         if (null != cfg)
            result.put(lang, cfg);
      }
      return result;
   }

   /**
    * Toggles LSP support on/off.
    */
   private void toggleLsp() {
      LspManager mgr = LspManager.getInstance();
      boolean newState = !mgr.isEnabled();
      mgr.setEnabled(newState);
      UI.reportMessage("LSP: " + (newState ? "enabled" : "disabled"));
   }

   /**
    * Shows LSP diagnostics for the current file.
    *
    * <p>If no diagnostics exist for the file, displays "No diagnostics"
    * on the status bar. Otherwise, opens a read-only buffer listing
    * all issues with line numbers and severity.</p>
    *
    * @param fvc the current file-view context
    * @throws InputException if buffer creation fails
    */
   private void showDiagnostics(FvContext fvc) throws InputException {
      String filePath = getFilePath(fvc);
      if (null == filePath) {
         UI.reportMessage("LSP: no file path available");
         return;
      }

      java.util.List<String> lines =
         diagnosticDisplay.formatForBuffer(filePath);
      if (lines.size() <= 1) {
         // Just the "No diagnostics" message — show on status bar
         UI.reportMessage(diagnosticDisplay.getSummary(filePath));
         return;
      }

      // Show in a buffer
      String content = String.join("\n", lines);
      javi.StringIoc sio = new javi.StringIoc("*lsp-diag*", content);
      javi.TextEdit<String> buf = new javi.TextEdit<>(sio, sio.prop);
      FvContext.connectFv(buf, fvc.vi);
   }

   /**
    * Shows or sets LSP server configuration.
    *
    * <p>With no argument, opens a buffer showing all configured servers,
    * their availability, and active state. With an argument in the form
    * {@code lang=command}, updates the command for that language.</p>
    *
    * <p>Examples:</p>
    * <ul>
    *   <li>{@code :lsp.config} - show all configs</li>
    *   <li>{@code :lsp.config java=/path/to/jdtls} - set Java server</li>
    *   <li>{@code :lsp.config python=pylsp} - set Python server</li>
    * </ul>
    *
    * @param arg optional "lang=command" string
    * @param fvc the current file-view context
    * @throws InputException if buffer creation fails
    */
   private void showConfig(Object arg, FvContext fvc)
         throws InputException {
      LspManager mgr = LspManager.getInstance();

      // If argument provided, treat as "lang=command" setting
      if (null != arg) {
         String sarg = arg.toString().trim();
         int eq = sarg.indexOf('=');
         if (eq > 0) {
            String lang = sarg.substring(0, eq).trim();
            String cmd = sarg.substring(eq + 1).trim();
            LspServerConfig existing = mgr.getConfig(lang);
            String[] exts = (null != existing)
               ? existing.fileExtensions
               : new String[]{};
            String rootPat = (null != existing)
               ? existing.rootPattern : null;
            String[] cmdParts = cmd.split("\\s+");
            LspServerConfig newConfig = new LspServerConfig(
               lang, cmdParts, exts, rootPat);
            mgr.setConfig(lang, newConfig);
            UI.reportMessage("LSP: set " + lang + " server to: " + cmd);
            return;
         }
      }

      // No argument — show all configs in a buffer
      StringBuilder sb = new StringBuilder();
      sb.append("LSP Server Configuration\n");
      sb.append("========================\n\n");
      sb.append(String.format("%-12s %-30s %-10s %-8s %s\n",
         "Language", "Command", "Available", "Active", "Extensions"));
      sb.append(String.format("%-12s %-30s %-10s %-8s %s\n",
         "--------", "-------", "---------", "------", "----------"));

      for (Map.Entry<String, LspServerConfig> entry
            : LspServerConfig.getDefaults().entrySet()) {
         LspServerConfig config = mgr.getConfig(entry.getKey());
         if (null == config)
            config = entry.getValue();

         String cmdStr = String.join(" ", config.command);
         if (cmdStr.length() > 28)
            cmdStr = cmdStr.substring(0, 25) + "...";
         boolean available = config.isAvailable();
         boolean active = isServerActive(mgr, config.languageId);
         String exts = String.join(", ", config.fileExtensions);

         sb.append(String.format("%-12s %-30s %-10s %-8s %s\n",
            config.languageId, cmdStr,
            available ? "yes" : "no",
            active ? "yes" : "-",
            exts));
      }

      sb.append("\nSet server: :lsp.config lang=/path/to/server\n");
      sb.append("Toggle LSP: :lsp.toggle\n");
      sb.append("Config: "
         + LspServerConfig.getConfigFile().getPath() + "\n");
      sb.append("Status:     " + mgr.getStatus() + "\n");

      // Show disabled languages
      java.util.Set<String> disabled = mgr.getDisabledLanguages();
      if (!disabled.isEmpty()) {
         sb.append("\nDisabled languages: "
            + String.join(", ", disabled) + "\n");
      }

      // Show root directory overrides
      Map<String, String> rootOverrides = mgr.getRootDirOverrides();
      if (!rootOverrides.isEmpty()) {
         sb.append("\nRoot directory overrides:\n");
         for (Map.Entry<String, String> e : rootOverrides.entrySet()) {
            sb.append("  " + e.getKey() + " = " + e.getValue() + "\n");
         }
      }

      // Show source paths
      boolean hasSourcePaths = false;
      for (String lang : new String[]{
            "java", "c", "python", "typescript", "rust"}) {
         java.util.List<String> paths = mgr.getSourcePaths(lang);
         if (!paths.isEmpty()) {
            if (!hasSourcePaths) {
               sb.append("\nSource paths:\n");
               hasSourcePaths = true;
            }
            sb.append("  " + lang + ": "
               + String.join(", ", paths) + "\n");
         }
      }

      javi.StringIoc sio = new javi.StringIoc("*lsp-config*",
         sb.toString());
      javi.TextEdit<String> buf = new javi.TextEdit<>(sio, sio.prop);
      buf.setReadOnly(true);
      FvContext.connectFv(buf, fvc.vi);
   }

   /**
    * Checks if a server for the given language is currently active.
    */
   private static boolean isServerActive(LspManager mgr, String langId) {
      String status = mgr.getStatus();
      return status.contains(langId + "(ok)");
   }

   /**
    * Sends the current file to an overlay server so it produces
    * diagnostics immediately after starting.
    */
   private static void sendCurrentFileToOverlay(FvContext fvc,
         LspManager mgr, String languageId) {
      String filePath = getFilePathStatic(fvc);
      if (null == filePath)
         return;
      if (null == fvc.edvec || !fvc.edvec.fdes().isLocalFile())
         return;
      String content = fvc.edvec.getDocumentText();
      if (null == content)
         return;
      mgr.notifyDidOpen(filePath, content);
   }

   /**
    * Gets the file path from the current context, handling
    * the various FileDescriptor types.
    *
    * @param fvc the file-view context
    * @return the absolute file path, or null
    */
   @SuppressWarnings("unchecked")
   private String getFilePath(FvContext fvc) {
      return getFilePathStatic(fvc);
   }

   /**
    * Static version of getFilePath for use by tryGotoDefinition.
    *
    * @param fvc the file-view context
    * @return the absolute file path, or null
    */
   private static String getFilePathStatic(FvContext fvc) {
      if (null == fvc || null == fvc.edvec)
         return null;
      String name = fvc.edvec.getName();
      if (null == name || name.isEmpty())
         return null;

      // Try to get canonical path
      java.io.File f = new java.io.File(name);
      try {
         return f.getCanonicalPath();
      } catch (java.io.IOException e) {
         return f.getAbsolutePath();
      }
   }

   /**
    * Navigates to a location returned by an LSP response.
    *
    * <p>Converts the LSP location (uri + range) to a Javi
    * Position and navigates to it.</p>
    *
    * @param location the LSP Location map with "uri" and "range"
    * @param fvc the current file-view context
    * @throws InputException if navigation fails
    */
   @SuppressWarnings("unchecked")
   private void navigateToLocation(Map<String, Object> location,
         FvContext fvc) throws InputException {
      navigateToLocationStatic(location, fvc, "lsp:definition");
   }

   /**
    * Static navigation to an LSP location.
    *
    * @param location the LSP Location map with "uri" and "range"
    * @param fvc the current file-view context
    * @param comment description to attach to the Position (e.g. hover info)
    * @throws InputException if navigation fails
    */
   @SuppressWarnings("unchecked")
   private static void navigateToLocationStatic(
         Map<String, Object> location, FvContext fvc, String comment)
         throws InputException {
      String uri = (String) location.get("uri");
      if (null == uri) {
         // Try targetUri (for LocationLink)
         uri = (String) location.get("targetUri");
      }
      if (null == uri) {
         UI.reportMessage("LSP: invalid location (no uri)");
         return;
      }

      String path = LspClient.uriToPath(uri);

      // Extract line from range
      int line = 1; // default to first line
      Map<String, Object> range = null;
      Object rangeObj = location.get("range");
      if (null == rangeObj)
         rangeObj = location.get("targetSelectionRange");
      if (rangeObj instanceof Map) {
         range = (Map<String, Object>) rangeObj;
         Object start = range.get("start");
         if (start instanceof Map) {
            Map<String, Object> startPos = (Map<String, Object>) start;
            Object lineObj = startPos.get("line");
            if (lineObj instanceof Number) {
               line = ((Number) lineObj).intValue() + 1; // LSP 0-based to Javi 1-based
            }
         }
      }

      // Navigate to the position
      Position pos = new Position(0, line, path, comment);
      trace("LSP: navigating to " + pos);
      UI.reportMessage("LSP: " + path + ":" + line);

      // Use the same mechanism as tag navigation
      try {
         javi.FileList.gotoposition(pos, false, fvc.vi);
      } catch (javi.InputException e) {
         throw new InputException("LSP: navigation failed: " + e);
      }
   }

   /**
    * Extracts a Java identifier at the given column position.
    *
    * @param text the line text
    * @param col the cursor column (0-based)
    * @return the identifier, or null
    */
   private static String extractWordAt(String text, int col) {
      if (null == text || col < 0 || col >= text.length())
         return null;

      // Find start of identifier
      int start = col;
      while (start > 0
            && Character.isJavaIdentifierPart(text.charAt(start - 1)))
         start--;

      // Find end of identifier
      int end = col;
      while (end < text.length()
            && Character.isJavaIdentifierPart(text.charAt(end)))
         end++;

      if (start == end)
         return null;
      return text.substring(start, end);
   }

   /**
    * Converts workspace/symbol results to location format for navigation.
    *
    * @param mgr the LspManager
    * @param filePath file path for server lookup
    * @param query the symbol name to search
    * @param projectRoot the project root for filtering
    * @return list of locations, or null
    */
   @SuppressWarnings("unchecked")
   private static List<Map<String, Object>> workspaceSymbolToLocations(
         LspManager mgr, String filePath, String query,
         String projectRoot) {
      List<Map<String, Object>> symbols =
         mgr.workspaceSymbol(filePath, query);
      return filterSymbolsToLocations(symbols, query, projectRoot);
   }

   /**
    * Queries a specific language server for workspace/symbol results.
    *
    * @param mgr the LspManager
    * @param langId the language server to query
    * @param query the symbol name to search
    * @param projectRoot the project root for filtering
    * @return list of locations, or null
    */
   private static List<Map<String, Object>> workspaceSymbolByLangToLocations(
         LspManager mgr, String langId, String query,
         String projectRoot) {
      List<Map<String, Object>> symbols =
         mgr.workspaceSymbolByLanguage(langId, query);
      return filterSymbolsToLocations(symbols, query, projectRoot);
   }

   /**
    * Filters workspace/symbol results to matching locations.
    *
    * @param symbols raw symbol results from LSP
    * @param query the symbol name to match exactly
    * @param projectRoot the project root for filtering
    * @return list of locations, or null
    */
   @SuppressWarnings("unchecked")
   private static List<Map<String, Object>> filterSymbolsToLocations(
         List<Map<String, Object>> symbols, String query,
         String projectRoot) {
      if (null == symbols || symbols.isEmpty())
         return null;

      List<Map<String, Object>> locations = new ArrayList<>();
      for (Map<String, Object> sym : symbols) {
         String name = (String) sym.get("name");
         if (null == name || !name.equals(query))
            continue;
         Object locObj = sym.get("location");
         if (locObj instanceof Map) {
            Map<String, Object> loc = (Map<String, Object>) locObj;
            if (null != projectRoot) {
               String uri = (String) loc.get("uri");
               if (null != uri) {
                  String path = LspClient.uriToPath(uri);
                  if (!path.startsWith(projectRoot))
                     continue;
               }
            }
            locations.add(loc);
         }
      }
      return locations.isEmpty() ? null : locations;
   }

   /**
    * Filters LSP location results to only include paths under the
    * current project root. This prevents results from other worktrees
    * or unrelated directories from appearing.
    *
    * @param locations the raw locations from the LSP server
    * @param projectRoot the project root to filter by, or null to skip
    * @return filtered list (may be empty), or original if root is null
    */
   private static List<Map<String, Object>> filterByProjectRoot(
         List<Map<String, Object>> locations, String projectRoot) {
      if (null == projectRoot || null == locations || locations.isEmpty())
         return locations;

      List<Map<String, Object>> filtered = new ArrayList<>();
      for (Map<String, Object> loc : locations) {
         String uri = (String) loc.get("uri");
         if (null == uri)
            uri = (String) loc.get("targetUri");
         if (null == uri) {
            filtered.add(loc);
            continue;
         }
         String path = LspClient.uriToPath(uri);
         if (path.startsWith(projectRoot)) {
            filtered.add(loc);
         } else {
            trace("LSP: filtered out result outside project root: "
               + path);
         }
      }
      return filtered;
   }
}
