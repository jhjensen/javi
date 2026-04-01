package javi.ai;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import static history.Tools.trace;

/**
 * GitHub Copilot provider using the REST API.
 *
 * <p>Communicates with the GitHub Copilot REST API at
 * {@code https://api.githubcopilot.com/chat/completions}
 * using OpenAI-compatible request format. Delegates HTTP
 * communication and token management to
 * {@link CopilotRestClient}.</p>
 *
 * <h2>Authentication</h2>
 * <p>Uses GitHub OAuth token cached in
 * {@code ~/.config/github-copilot/apps.json} (written by
 * Neovim copilot.lua, copilot.vim, or javi's own device
 * flow auth via {@code :ai auth}). The token is exchanged
 * for a short-lived Copilot session token automatically.</p>
 *
 * <h2>Model Selection</h2>
 * <p>Model can be configured via {@link AIConfig}. Use
 * {@link #listModels()} to discover available models.
 * Default model is {@code gpt-4.1}.</p>
 *
 * @see AIProvider
 * @see CopilotRestClient
 * @see AIConfig
 */
public final class CopilotProvider implements AIProvider {

   /** Default model when none is explicitly configured. */
   static final String DEFAULT_MODEL = "gpt-4.1";

   private final CopilotRestClient client;
   private final String model;

   /**
    * Create a Copilot provider with default configuration.
    *
    * <p>Loads the OAuth token from {@code apps.json} or the
    * {@code GH_COPILOT_TOKEN} environment variable.</p>
    *
    * @throws AIException if no OAuth token is available
    */
   public CopilotProvider() throws AIException {
      this.client = new CopilotRestClient();
      String cfgModel = AIConfig.getInstance().getModel();
      this.model = "copilot".equals(cfgModel)
         ? DEFAULT_MODEL : cfgModel;
      if (!client.hasToken()) {
         throw new AIException(
            "GitHub Copilot not authenticated. "
            + "Run :ai auth to authenticate via "
            + "device flow, or set "
            + CopilotRestClient.TOKEN_ENV
            + " environment variable.");
      }
   }

   /**
    * Create a Copilot provider with an explicit client.
    *
    * @param restClient the REST client to use
    * @param modelName the model identifier
    */
   public CopilotProvider(CopilotRestClient restClient,
         String modelName) {
      this.client = restClient;
      this.model = modelName;
   }

   @Override
   public String chatCompletion(
         List<Message> messages, int maxTokens)
         throws IOException, AIException {
      String responseJson = client.chatCompletion(
         messages, model, maxTokens);
      return OpenAIProvider.extractContent(responseJson);
   }

   /**
    * Send a streaming chat completion request.
    *
    * <p>Tokens are passed to the callback as they arrive.
    * Returns the full concatenated response.</p>
    *
    * @param messages the conversation messages
    * @param maxTokens max response tokens
    * @param onToken callback for each content delta
    * @return the full response text
    * @throws IOException if a network error occurs
    * @throws AIException if the API call fails
    */
   public String chatCompletionStreaming(
         List<Message> messages, int maxTokens,
         Consumer<String> onToken)
         throws IOException, AIException {
      return client.chatCompletionStreaming(
         messages, model, maxTokens, onToken);
   }

   @Override
   public String getName() {
      return "GitHub Copilot";
   }

   @Override
   public String getModel() {
      return model;
   }

   @Override
   public boolean testConnection() {
      try {
         List<Message> test = List.of(
            new Message("user", "Reply with OK"));
         chatCompletion(test, 5);
         return true;
      } catch (IOException | AIException e) {
         trace("Copilot test failed: " + e.getMessage());
         return false;
      }
   }

   /**
    * List available models from the Copilot API.
    *
    * @return list of model identifier strings
    * @throws IOException if a network error occurs
    * @throws AIException if the request fails
    */
   public List<String> listModels()
         throws IOException, AIException {
      return client.listModels();
   }

   /**
    * Get the underlying REST client.
    *
    * <p>Provides access to device flow auth and other
    * low-level operations.</p>
    *
    * @return the CopilotRestClient instance
    */
   public CopilotRestClient getRestClient() {
      return client;
   }
}
