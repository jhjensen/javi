package javi.ai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javi.ai.tools.AIToolRegistry;

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
      String toolsJson = AIToolRegistry.hasTools()
         ? AIToolRegistry.getToolsJson() : null;
      if (null != toolsJson) {
         trace("Copilot: sending " + AIToolRegistry
            .getTools().size() + " tool definitions");
      }

      String responseJson = client.chatCompletion(
         messages, model, maxTokens, toolsJson);

      // Tool execution loop
      List<Message> conversationMsgs =
         new ArrayList<>(messages);
      int rounds = 0;
      while (CopilotRestClient.hasToolCalls(responseJson)
            && rounds < CopilotRestClient
               .MAX_TOOL_ROUNDS) {
         rounds++;
         List<CopilotRestClient.ToolCall> toolCalls =
            CopilotRestClient.extractToolCalls(
               responseJson);
         if (toolCalls.isEmpty()) {
            break;
         }
         trace("Copilot: tool round " + rounds + ", "
            + toolCalls.size() + " tool call(s)");

         List<String> results = executeToolCalls(
            toolCalls);

         String body =
            CopilotRestClient.buildToolFollowupJson(
               conversationMsgs, toolCalls, results,
               model, maxTokens, toolsJson, false);
         responseJson = client.chatCompletionRaw(body);

         int status = checkResponseStatus(responseJson);
         if (status > 0) {
            throw new AIException(
               "Copilot tool followup error (HTTP "
               + status + ")", status);
         }
      }
      if (rounds > 0) {
         trace("Copilot: tool loop completed after "
            + rounds + " round(s)");
      }

      return OpenAIProvider.extractContent(responseJson);
   }

   /**
    * Send a streaming chat completion request with tool support.
    *
    * <p>If the model requests tool calls, they are executed
    * silently and a follow-up streaming request is made with
    * the tool results. Only the final content response is
    * streamed to the user.</p>
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
      String toolsJson = AIToolRegistry.hasTools()
         ? AIToolRegistry.getToolsJson() : null;

      // First, try a non-streaming request to check for
      // tool calls. If no tools, go straight to streaming.
      if (null == toolsJson) {
         return client.chatCompletionStreaming(
            messages, model, maxTokens, onToken);
      }

      // With tools enabled, do non-streaming first to
      // handle potential tool call rounds
      String responseJson = client.chatCompletion(
         messages, model, maxTokens, toolsJson);

      List<Message> conversationMsgs =
         new ArrayList<>(messages);
      int rounds = 0;
      while (CopilotRestClient.hasToolCalls(responseJson)
            && rounds < CopilotRestClient
               .MAX_TOOL_ROUNDS) {
         rounds++;
         List<CopilotRestClient.ToolCall> toolCalls =
            CopilotRestClient.extractToolCalls(
               responseJson);
         if (toolCalls.isEmpty()) {
            break;
         }
         trace("Copilot streaming: tool round "
            + rounds + ", " + toolCalls.size()
            + " call(s)");

         List<String> results = executeToolCalls(
            toolCalls);

         String body =
            CopilotRestClient.buildToolFollowupJson(
               conversationMsgs, toolCalls, results,
               model, maxTokens, toolsJson, false);
         responseJson = client.chatCompletionRaw(body);
      }

      if (0 == rounds) {
         // No tool calls — the response already has
         // content, deliver it as a single chunk
         String content =
            OpenAIProvider.extractContent(responseJson);
         onToken.accept(content);
         return content;
      }

      // After tool rounds, extract and deliver content
      trace("Copilot streaming: tool loop done after "
         + rounds + " round(s)");
      String content =
         OpenAIProvider.extractContent(responseJson);
      onToken.accept(content);
      return content;
   }

   /**
    * Execute a list of tool calls and return results.
    *
    * @param toolCalls the tool calls to execute
    * @return list of result strings in call order
    */
   private List<String> executeToolCalls(
         List<CopilotRestClient.ToolCall> toolCalls) {
      List<String> results = new ArrayList<>();
      for (CopilotRestClient.ToolCall tc : toolCalls) {
         String result;
         try {
            Map<String, String> params =
               CopilotRestClient.parseToolArgs(
                  tc.arguments());
            trace("Copilot: executing tool '"
               + tc.name() + "' with " + params);
            result = AIToolRegistry.executeTool(
               tc.name(), params);
         } catch (AIException e) {
            result = "Error: " + e.getMessage();
            trace("Copilot: tool '" + tc.name()
               + "' failed: " + e.getMessage());
         }
         results.add(result);
      }
      return results;
   }

   /**
    * Check if a raw response JSON indicates an HTTP error.
    *
    * @param json the response body
    * @return the HTTP status if error, 0 if OK
    */
   private static int checkResponseStatus(String json) {
      // If the response contains an error object at root
      if (json.startsWith("{\"error\"")) {
         return 500;
      }
      return 0;
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
