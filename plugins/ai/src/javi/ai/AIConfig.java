package javi.ai;

import static history.Tools.trace;

/**
 * Configuration manager for AI integration settings.
 *
 * <p>AIConfig stores and manages provider selection, API keys, model
 * preferences, and other AI-related settings. Configuration can be
 * set via the {@code .javini} file or through {@code :set} commands.</p>
 *
 * <h2>Configuration Keys</h2>
 * <table>
 *   <tr><th>Key</th><th>Default</th><th>Description</th></tr>
 *   <tr><td>ai.provider</td><td>openai</td><td>AI provider: openai, anthropic</td></tr>
 *   <tr><td>ai.model</td><td>(provider default)</td><td>Model name</td></tr>
 *   <tr><td>ai.maxTokens</td><td>2048</td><td>Max response tokens</td></tr>
 *   <tr><td>ai.apikey</td><td>(from env)</td><td>API key (or use env var)</td></tr>
 * </table>
 *
 * <h2>API Key Resolution</h2>
 * <p>API keys are resolved in order:</p>
 * <ol>
 *   <li>Explicit config value from {@code :set ai.apikey=...}</li>
 *   <li>Environment variable: {@code OPENAI_API_KEY} or {@code ANTHROPIC_API_KEY}</li>
 *   <li>GitHub Copilot token from {@code apps.json}
 *       (managed by {@link CopilotRestClient})</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * <p>All fields use volatile or synchronized access for thread safety.</p>
 *
 * @see AIProvider
 * @see AIClient
 * @see AICommands
 */
public final class AIConfig {

   /** Supported AI provider identifiers. */
   public enum Provider {
      /** OpenAI GPT models. */
      OPENAI("openai"),
      /** Anthropic Claude models. */
      ANTHROPIC("anthropic"),
      /** GitHub Copilot via LSP agent. */
      COPILOT("copilot");

      private final String id;

      Provider(String idStr) {
         this.id = idStr;
      }

      /**
       * Get the string identifier for this provider.
       *
       * @return provider id string
       */
      public String getId() {
         return id;
      }

      /**
       * Look up a Provider by string id.
       *
       * @param id the provider id (case-insensitive)
       * @return the matching Provider
       * @throws IllegalArgumentException if id is not recognized
       */
      public static Provider fromId(String id) {
         for (Provider p : values()) {
            if (p.id.equalsIgnoreCase(id)) {
               return p;
            }
         }
         throw new IllegalArgumentException("Unknown AI provider: " + id
            + ". Supported: openai, anthropic, copilot");
      }
   }

   private static final AIConfig INSTANCE = new AIConfig();

   private volatile Provider provider = Provider.OPENAI;
   private volatile String model = null; // null means use provider default
   private volatile int maxTokens = 2048;
   private volatile String apiKey = null;
   private volatile int timeoutSeconds = 30;
   private volatile String systemPrompt = DEFAULT_SYSTEM_PROMPT;
   private volatile int completionDelayMs = 800;
   private volatile String authFile = null; // null means default copilot path

   /** Default system prompt for code assistance. */
   static final String DEFAULT_SYSTEM_PROMPT =
      "You are a helpful coding assistant integrated into a vi-style "
      + "text editor called Javi. Be concise and direct. "
      + "When showing code, match the style of the surrounding code. "
      + "Prefer short, actionable answers.";

   /** Private constructor for singleton. */
   private AIConfig() {
   }

   /**
    * Get the singleton AIConfig instance.
    *
    * @return the global AIConfig
    */
   public static AIConfig getInstance() {
      return INSTANCE;
   }

   /**
    * Get the currently configured provider.
    *
    * @return the AI provider enum value
    */
   public Provider getProvider() {
      return provider;
   }

   /**
    * Set the AI provider.
    *
    * @param providerStr provider id string (e.g., "openai", "anthropic")
    */
   public void setProvider(String providerStr) {
      this.provider = Provider.fromId(providerStr);
      trace("AI provider set to: " + this.provider.getId());
   }

   /**
    * Get the model name, or the provider default if not explicitly set.
    *
    * @return the model name
    */
   public String getModel() {
      if (null != model) {
         return model;
      }
      return switch (provider) {
         case OPENAI -> "gpt-4o";
         case ANTHROPIC -> "claude-sonnet-4-20250514";
         case COPILOT -> "gpt-4.1";
      };
   }

   /**
    * Set the model name.
    *
    * @param modelName the model identifier
    */
   public void setModel(String modelName) {
      this.model = modelName;
      trace("AI model set to: " + modelName);
   }

   /**
    * Get the maximum response tokens.
    *
    * @return max tokens
    */
   public int getMaxTokens() {
      return maxTokens;
   }

   /**
    * Set the maximum response tokens.
    *
    * @param tokens max tokens (must be positive)
    * @throws IllegalArgumentException if tokens is not positive
    */
   public void setMaxTokens(int tokens) {
      if (tokens <= 0) {
         throw new IllegalArgumentException("maxTokens must be positive");
      }
      this.maxTokens = tokens;
   }

   /**
    * Get the API key, resolving from environment if not explicitly set.
    *
    * <p>Resolution order:</p>
    * <ol>
    *   <li>Explicitly configured key</li>
    *   <li>Environment variable ({@code OPENAI_API_KEY} or
    *       {@code ANTHROPIC_API_KEY})</li>
    * </ol>
    *
    * @return the API key, or null if not configured
    */
   public String getApiKey() {
      if (null != apiKey) {
         return apiKey;
      }
      // Try environment variables
      return switch (provider) {
         case OPENAI -> System.getenv("OPENAI_API_KEY");
         case ANTHROPIC -> System.getenv("ANTHROPIC_API_KEY");
         case COPILOT -> "copilot-oauth"; // managed by CopilotRestClient
      };
   }

   /**
    * Set the API key explicitly.
    *
    * <p><b>SECURITY</b>: The key is stored in memory only, never
    * logged or written to files.</p>
    *
    * @param key the API key string
    */
   public void setApiKey(String key) {
      this.apiKey = key;
      // Never log API keys
      trace("AI API key configured (length: " + key.length() + ")");
   }

   /**
    * Get the HTTP timeout for AI requests in seconds.
    *
    * @return timeout in seconds
    */
   public int getTimeoutSeconds() {
      return timeoutSeconds;
   }

   /**
    * Set the HTTP timeout for AI requests.
    *
    * @param seconds timeout in seconds (must be positive)
    */
   public void setTimeoutSeconds(int seconds) {
      if (seconds <= 0) {
         throw new IllegalArgumentException(
            "timeout must be positive");
      }
      this.timeoutSeconds = seconds;
   }

   /**
    * Get the auto-completion delay in milliseconds.
    *
    * <p>When positive, AI completion triggers automatically after
    * the user pauses typing for this many milliseconds in insert
    * mode. Set to 0 to disable auto-trigger (require explicit
    * Tab).</p>
    *
    * @return delay in milliseconds (0 = disabled)
    */
   public int getCompletionDelayMs() {
      return completionDelayMs;
   }

   /**
    * Set the auto-completion delay.
    *
    * @param ms delay in milliseconds (0 to disable, must be non-negative)
    * @throws IllegalArgumentException if ms is negative
    */
   public void setCompletionDelayMs(int ms) {
      if (ms < 0) {
         throw new IllegalArgumentException(
            "completion delay must be non-negative");
      }
      this.completionDelayMs = ms;
   }

   /**
    * Get the system prompt for AI conversations.
    *
    * @return the system prompt string
    */
   public String getSystemPrompt() {
      return systemPrompt;
   }

   /**
    * Set a custom system prompt.
    *
    * @param prompt the system prompt text
    */
   public void setSystemPrompt(String prompt) {
      this.systemPrompt = prompt;
   }

   /**
    * Get the configured auth file path.
    *
    * <p>Special values: {@code "copilot"} resolves to the default
    * GitHub Copilot token path, {@code "claude"} resolves to the
    * Anthropic auth path. Any other value is treated as a literal
    * file path. Null means use the default copilot path.</p>
    *
    * @return the auth file setting, or null for default
    */
   public String getAuthFile() {
      return authFile;
   }

   /**
    * Set the auth file path for OAuth token storage.
    *
    * @param path file path, "copilot", or "claude"
    */
   public void setAuthFile(String path) {
      this.authFile = path;
   }

   /**
    * Resolve the auth file path to an absolute Path.
    *
    * @return resolved path to the OAuth token file
    */
   public java.nio.file.Path resolveAuthFile() {
      String home = System.getProperty("user.home");
      if (null == authFile || "copilot".equalsIgnoreCase(authFile)) {
         return java.nio.file.Path.of(home,
            ".config", "github-copilot", "apps.json");
      }
      if ("claude".equalsIgnoreCase(authFile)) {
         return java.nio.file.Path.of(home,
            ".config", "anthropic", "auth.json");
      }
      // Treat as literal path (expand ~ if present)
      if (authFile.startsWith("~/")) {
         return java.nio.file.Path.of(home,
            authFile.substring(2));
      }
      return java.nio.file.Path.of(authFile);
   }

   /**
    * Check if the AI system is configured with an API key.
    *
    * @return true if an API key is available
    */
   public boolean isConfigured() {
      return null != getApiKey();
   }

   /**
    * Process a set command for AI configuration.
    *
    * <p>Handles commands like:
    * <ul>
    *   <li>{@code set ai.provider=openai}</li>
    *   <li>{@code set ai.model=gpt-4o}</li>
    *   <li>{@code set ai.maxTokens=4096}</li>
    *   <li>{@code set ai.apikey=sk-...}</li>
    * </ul>
    *
    * @param key the setting key (after "ai." prefix)
    * @param value the setting value
    * @return true if the setting was recognized and applied
    */
   public boolean setSetting(String key, String value) {
      switch (key) {
         case "provider":
            setProvider(value);
            return true;
         case "model":
            setModel(value);
            return true;
         case "maxTokens":
         case "maxtokens":
            setMaxTokens(Integer.parseInt(value));
            return true;
         case "apikey":
            setApiKey(value);
            return true;
         case "prompt":
            setSystemPrompt(value);
            return true;
         case "timeout":
            setTimeoutSeconds(Integer.parseInt(value));
            return true;
         case "delay":
            setCompletionDelayMs(Integer.parseInt(value));
            return true;
         case "authfile":
         case "authFile":
            setAuthFile(value);
            return true;
         default:
            return false;
      }
   }

   /**
    * Get a summary of current configuration (without exposing API key).
    *
    * @return human-readable configuration summary
    */
   public String getSummary() {
      String keyStatus = null != getApiKey()
         ? "configured (" + getApiKey().length() + " chars)"
         : "NOT SET";
      return "AI Config: provider=" + provider.getId()
         + " model=" + getModel()
         + " maxTokens=" + maxTokens
         + " timeout=" + timeoutSeconds + "s"
         + " delay=" + completionDelayMs + "ms"
         + " apiKey=" + keyStatus
         + " authFile=" + (null == authFile ? "copilot (default)"
            : authFile);
   }
}
