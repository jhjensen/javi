package javi.lsp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javi.HelpSystem;

import static history.Tools.trace;

/**
 * Registry that maps file extensions to LSP sessions.
 *
 * <p>Replaces the old {@code LspManager}. This class is only accessed
 * from the AWT thread — no synchronization is needed.</p>
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Maintain the set of configured language servers</li>
 *   <li>Start/stop sessions based on opened files</li>
 *   <li>Route requests to the correct session for a file</li>
 *   <li>Track overlay servers that receive all file notifications</li>
 * </ul></p>
 */
public final class LspRegistry {

   private static LspRegistry instance;

   private final Map<String, LspServerConfig> configs;
   private final Map<String, LspSession> sessions = new HashMap<>();
   private final LspSession.NotificationSink notificationSink;
   private String projectRoot;
   private boolean enabled = true;

   /**
    * Returns the singleton instance, creating it if necessary.
    *
    * @param sink callback for diagnostics and state changes
    * @return the registry instance
    */
   public static LspRegistry getInstance(LspSession.NotificationSink sink) {
      if (null == instance) {
         instance = new LspRegistry(sink);
      }
      return instance;
   }

   /**
    * Returns the existing instance, or null if not yet created.
    *
    * @return the registry instance or null
    */
   public static LspRegistry getInstance() {
      return instance;
   }

   /**
    * Shuts down all running sessions. Safe to call even if no instance
    * exists.
    */
   public static void shutdownAll() {
      if (null != instance) {
         instance.stopAll();
      }
   }

   /**
    * Creates a registry with the given notification sink.
    * Loads default server configurations.
    *
    * @param sink callback for diagnostics and state changes
    */
   public LspRegistry(LspSession.NotificationSink sink) {
      this(LspServerConfig.getDefaults(), sink);
      LspServerConfig.loadUserConfigs(configs);
   }

   /**
    * Creates a registry with explicit configs.
    * Intended for tests to avoid user-home config coupling.
    *
    * @param initialConfigs map of language id to config
    * @param sink callback for diagnostics and state changes
    */
   LspRegistry(Map<String, LspServerConfig> initialConfigs,
         LspSession.NotificationSink sink) {
      this.configs = new HashMap<>(initialConfigs);
      this.notificationSink = sink;
   }

   /**
    * Sets the project root directory used for LSP workspace root.
    *
    * @param root the project root path
    */
   public void setProjectRoot(String root) {
      this.projectRoot = root;
   }

   /** Returns the current project root. */
   public String getProjectRoot() {
      return projectRoot;
   }

   /**
    * Returns the session for the given file extension, starting it
    * if necessary.
    *
    * @param extension the file extension including dot (e.g. ".java")
    * @return the session, or null if no server configured for this extension
    */
   public LspSession sessionFor(String extension) {
      if (!enabled)
         return null;

      LspServerConfig config =
         LspServerConfig.forExtension(configs, extension);
      if (null == config)
         return null;

      return getOrStartSession(config);
   }

   /**
    * Returns a currently running session for the given extension,
    * without starting a new server.
    *
    * @param extension the file extension including dot (e.g. ".java")
    * @return running session, or null if not running / not configured
    */
   public LspSession runningSessionFor(String extension) {
      if (!enabled)
         return null;

      LspServerConfig config =
         LspServerConfig.forExtension(configs, extension);
      if (null == config)
         return null;

      return sessions.get(config.languageId);
   }

   /**
    * Returns the session for a specific language ID.
    *
    * @param languageId the language identifier (e.g. "java", "harper")
    * @return the session, or null if not configured or disabled
    */
   public LspSession sessionForLanguage(String languageId) {
      if (!enabled)
         return null;

      LspServerConfig config = configs.get(languageId);
      if (null == config || !config.enabled)
         return null;

      return getOrStartSession(config);
   }

   /**
    * Returns all currently active sessions.
    *
    * @return list of running sessions (may be empty)
    */
   public List<LspSession> activeSessions() {
      return new ArrayList<>(sessions.values());
   }

   /**
    * Returns all overlay sessions (servers that receive all file types).
    *
    * @return list of overlay sessions
    */
   public List<LspSession> overlaySessions() {
      List<LspSession> result = new ArrayList<>();
      for (Map.Entry<String, LspSession> entry : sessions.entrySet()) {
         LspServerConfig cfg = configs.get(entry.getKey());
         if (null != cfg && cfg.overlay) {
            result.add(entry.getValue());
         }
      }
      return result;
   }

   /**
    * Starts all configured servers eagerly (for startup initialization).
    * Only starts servers whose binaries are available.
    */
   public void startAll() {
      if (!enabled)
         return;
      for (Map.Entry<String, LspServerConfig> entry : configs.entrySet()) {
         LspServerConfig config = entry.getValue();
         if (config.enabled && config.isAvailable()) {
            getOrStartSession(config);
         }
      }
   }

   /**
    * Stops all running sessions.
    */
   public void stopAll() {
      for (LspSession session : sessions.values()) {
         session.stop();
      }
      sessions.clear();
   }

   /**
    * Enables or disables a specific language server.
    *
    * @param languageId the language identifier
    * @param enable true to enable, false to disable
    */
   public void setEnabled(String languageId, boolean enable) {
      LspServerConfig config = configs.get(languageId);
      if (null == config)
         return;
      config.enabled = enable;
      if (!enable) {
         LspSession session = sessions.remove(languageId);
         if (null != session) {
            session.stop();
         }
      }
   }

   /**
    * Enables or disables the entire LSP subsystem.
    *
    * @param enable true to enable, false to disable all
    */
   public void setGlobalEnabled(boolean enable) {
      this.enabled = enable;
      if (!enable) {
         stopAll();
      }
   }

   /** Returns true if the LSP subsystem is globally enabled. */
   public boolean isEnabled() {
      return enabled;
   }

   /**
    * Returns a status summary for all configured servers.
    *
    * @return list of status strings (one per server)
    */
   public List<String> getStatus() {
      List<String> result = new ArrayList<>();
      for (Map.Entry<String, LspServerConfig> entry : configs.entrySet()) {
         String id = entry.getKey();
         LspServerConfig cfg = entry.getValue();
         LspSession session = sessions.get(id);
         String status;
         if (!cfg.enabled) {
            status = "disabled";
         } else if (null == session) {
            status = cfg.isAvailable() ? "available" : "not installed";
         } else {
            status = session.getState().toString().toLowerCase()
               + " (" + (session.getUptime() / 1000) + "s)";
         }
         result.add(id + ": " + status);
      }
      return result;
   }

   /**
    * Returns one formatted row per currently active LSP session.
    * Each row contains the language id, session state, and project
    * root, separated by tab characters.
    *
    * @return list of rows (empty if no sessions are active)
    */
   public List<String> getActiveSessionStatus() {
      List<String> result = new ArrayList<>();
      for (LspSession session : sessions.values()) {
         result.add(session.getLanguageId()
            + "\tstate=" + session.getState()
            + "\troot=" + session.getProjectRoot());
      }
      return result;
   }

   // ---------------------------------------------------------------
   // Internal
   // ---------------------------------------------------------------

   private LspSession getOrStartSession(LspServerConfig config) {
      LspSession session = sessions.get(config.languageId);
      if (null != session) {
         LspSession.State st = session.getState();
         if (LspSession.State.STOPPED != st
               && LspSession.State.CRASHED != st) {
            return session;
         }
         // Dead session — remove and create a fresh one below.
         sessions.remove(config.languageId);
         trace("LSP: removing dead session: " + config.languageId
            + " state=" + st);
      }

      if (null == projectRoot) {
         trace("LSP: no project root set, cannot start " + config.languageId);
         return null;
      }

      if (!config.isAvailable()) {
         trace("LSP: server not available: " + config.languageId);
         return null;
      }

      session = new LspSession(config, projectRoot, notificationSink);
      sessions.put(config.languageId, session);
      session.start();
      trace("LSP: started session: " + config.languageId);
      return session;
   }
}
