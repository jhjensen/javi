package javi.lsp;

import java.util.List;

import javi.FvContext;
import javi.HelpSystem;
import javi.InputException;

/**
 * Command-layer integration for the LSP subsystem.
 *
 * <p>Registers colon commands that let the user query and control
 * language servers from the javi command line:</p>
 * <ul>
 *   <li>{@code :lsp.status} — display status of all configured servers</li>
 *   <li>{@code :lsp.diag} — display diagnostic summary</li>
 *   <li>{@code :lsp.enable <lang>} — enable a language server</li>
 *   <li>{@code :lsp.disable <lang>} — disable a language server</li>
 * </ul>
 *
 * <p>All commands run on the AWT thread with biglock2 held (normal
 * command dispatch). They never block on server I/O — they query
 * local state from the registry and diagnostics store only.</p>
 */
public final class LspCommands {

   private final LspRegistry registry;
   private final LspDiagnostics diagnostics;

   /**
    * Creates a new LspCommands instance bound to the given registry
    * and diagnostics store.
    *
    * @param registry the LSP registry (non-null)
    * @param diagnostics the diagnostics store (non-null)
    */
   public LspCommands(LspRegistry registry, LspDiagnostics diagnostics) {
      this.registry = registry;
      this.diagnostics = diagnostics;
   }

   /**
    * Handles the {@code :lsp.status} command.
    * Displays a list of all configured servers with their current state.
    *
    * @param fvc the file-view context
    * @throws InputException with status output for display
    */
   public void doStatus(FvContext fvc) throws InputException {
      List<String> status = registry.getStatus();
      if (status.isEmpty()) {
         throw new InputException("LSP: no servers configured");
      }
      StringBuilder sb = new StringBuilder("LSP status: ");
      for (int i = 0; i < status.size(); i++) {
         if (i > 0)
            sb.append("; ");
         sb.append(status.get(i));
      }
      throw new InputException(sb.toString());
   }

   /**
    * Handles the {@code :lsp.diag} command.
    * Displays a summary of current diagnostics.
    *
    * @param fvc the file-view context
    * @throws InputException with diagnostic summary for display
    */
   public void doDiag(FvContext fvc) throws InputException {
      String summary = diagnostics.summary();
      int total = diagnostics.totalCount();
      throw new InputException("LSP diagnostics: " + summary
         + " (" + total + " total)");
   }

   /**
    * Handles the {@code :lsp.enable} command.
    * Enables the specified language server.
    *
    * @param languageId the language identifier (e.g. "java", "harper")
    * @throws InputException on success or error
    */
   public void doEnable(String languageId) throws InputException {
      if (null == languageId || languageId.isEmpty()) {
         registry.setGlobalEnabled(true);
         throw new InputException("LSP: globally enabled");
      }
      registry.setEnabled(languageId, true);
      throw new InputException("LSP: enabled " + languageId);
   }

   /**
    * Handles the {@code :lsp.disable} command.
    * Disables the specified language server.
    *
    * @param languageId the language identifier (e.g. "java", "harper")
    * @throws InputException on success or error
    */
   public void doDisable(String languageId) throws InputException {
      if (null == languageId || languageId.isEmpty()) {
         registry.setGlobalEnabled(false);
         throw new InputException("LSP: globally disabled");
      }
      registry.setEnabled(languageId, false);
      throw new InputException("LSP: disabled " + languageId);
   }

   /**
    * Registers the LSP help topic.
    */
   public void registerHelp() {
      HelpSystem.registerHelpTopic("lsp",
         new String[]{"lsp.status", "lsp.diag", "lsp.enable",
            "lsp.disable"},
         () -> {
            // Help content appended dynamically
         });
   }
}
