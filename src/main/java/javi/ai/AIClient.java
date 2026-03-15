package javi.ai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static history.Tools.trace;

/**
 * High-level AI client that manages provider selection and conversation state.
 *
 * <p>AIClient is the primary entry point for AI functionality in Javi.
 * It manages the provider lifecycle, maintains conversation history,
 * and provides convenience methods for common AI operations (explain,
 * review, document, chat).</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AIClient client = AIClient.getInstance();
 * String response = client.chat("What does this code do?");
 * String explanation = client.explain(codeSnippet);
 * }</pre>
 *
 * <h2>Conversation Management</h2>
 * <p>The client maintains a conversation history that persists across
 * multiple {@link #chat} calls. Use {@link #clearHistory()} to start
 * a fresh conversation. Other convenience methods (explain, review,
 * document) do NOT add to the conversation history.</p>
 *
 * <h2>Provider Management</h2>
 * <p>The provider is lazily initialized from {@link AIConfig} on first
 * use. If configuration changes, call {@link #resetProvider()} to
 * reinitialize.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>The client is thread-safe. API calls are blocking — callers should
 * run them from a background thread if UI responsiveness is needed.</p>
 *
 * @see AIProvider
 * @see AIConfig
 * @see AICommands
 */
public final class AIClient {

   private static final AIClient INSTANCE = new AIClient();

   private volatile AIProvider provider;
   private final List<AIProvider.Message> history =
      Collections.synchronizedList(new ArrayList<>());

   /** Private constructor for singleton. */
   private AIClient() {
   }

   /**
    * Get the singleton AIClient instance.
    *
    * @return the global AIClient
    */
   public static AIClient getInstance() {
      return INSTANCE;
   }

   /**
    * Get or create the current AI provider based on configuration.
    *
    * <p>Lazily initializes the provider on first call. Subsequent calls
    * return the cached provider. Call {@link #resetProvider()} to force
    * reinitialization after config changes.</p>
    *
    * @return the active AIProvider
    * @throws AIException if the provider cannot be configured
    */
   public AIProvider getProvider() throws AIException {
      AIProvider p = provider;
      if (null != p) {
         return p;
      }
      synchronized (this) {
         if (null != provider) {
            return provider;
         }
         AIConfig config = AIConfig.getInstance();
         provider = createProvider(config);
         return provider;
      }
   }

   /**
    * Reset the cached provider, forcing reinitialization on next use.
    *
    * <p>Call after changing AI configuration (provider, model, API key).</p>
    */
   public void resetProvider() {
      synchronized (this) {
         provider = null;
      }
      trace("AI provider reset");
   }

   /**
    * Send a chat message and return the response.
    *
    * <p>Adds both the user message and AI response to the conversation
    * history. The system prompt from {@link AIConfig} is prepended
    * automatically.</p>
    *
    * @param userMessage the user's message text
    * @return the AI response text
    * @throws IOException if a network error occurs
    * @throws AIException if the AI provider returns an error
    */
   public String chat(String userMessage) throws IOException, AIException {
      AIConfig config = AIConfig.getInstance();
      AIProvider p = getProvider();

      // Build full message list: system + history + new message
      List<AIProvider.Message> messages = new ArrayList<>();
      messages.add(new AIProvider.Message("system", config.getSystemPrompt()));
      synchronized (history) {
         messages.addAll(history);
      }
      messages.add(new AIProvider.Message("user", userMessage));

      String response = p.chatCompletion(messages, config.getMaxTokens());

      // Add to history
      history.add(new AIProvider.Message("user", userMessage));
      history.add(new AIProvider.Message("assistant", response));

      return response;
   }

   /**
    * Explain the given code snippet.
    *
    * <p>This is a one-shot request and does NOT modify conversation
    * history.</p>
    *
    * @param code the code to explain
    * @return the explanation text
    * @throws IOException if a network error occurs
    * @throws AIException if the AI provider returns an error
    */
   public String explain(String code) throws IOException, AIException {
      return oneShot("Explain this code concisely. Focus on what it does, "
         + "not how. Mention any notable patterns or potential issues.\n\n"
         + code);
   }

   /**
    * Review the given code for bugs and improvements.
    *
    * <p>This is a one-shot request and does NOT modify conversation
    * history.</p>
    *
    * @param code the code to review
    * @return the review text with findings
    * @throws IOException if a network error occurs
    * @throws AIException if the AI provider returns an error
    */
   public String review(String code) throws IOException, AIException {
      return oneShot("Review this code for bugs, potential issues, and "
         + "improvements. Be concise and actionable.\n\n" + code);
   }

   /**
    * Generate documentation for the given code.
    *
    * <p>This is a one-shot request and does NOT modify conversation
    * history.</p>
    *
    * @param code the code to document
    * @return the generated documentation
    * @throws IOException if a network error occurs
    * @throws AIException if the AI provider returns an error
    */
   public String document(String code) throws IOException, AIException {
      return oneShot("Generate Javadoc documentation for this code. "
         + "Return only the doc comments, ready to paste.\n\n" + code);
   }

   /**
    * Refactor the given code according to the provided instruction.
    *
    * <p>This is a one-shot request and does NOT modify conversation
    * history.</p>
    *
    * @param code the code to refactor
    * @param instruction what refactoring to apply
    * @return the refactored code
    * @throws IOException if a network error occurs
    * @throws AIException if the AI provider returns an error
    */
   public String refactor(String code, String instruction)
         throws IOException, AIException {
      return oneShot("Refactor the following code according to the "
         + "instruction. Return ONLY the refactored code, ready to paste. "
         + "Do NOT include explanation or markdown fences.\n\n"
         + "Instruction: " + instruction + "\n\nCode:\n" + code);
   }

   /**
    * Generate a code completion for the given prefix text.
    *
    * <p>This is a one-shot request. The AI is instructed to return
    * only the completion text that would naturally follow the given
    * code, without explanation or markdown fences.</p>
    *
    * @param codeBefore the code text before the cursor
    * @param fileName the name of the file being edited (for context)
    * @return the completion suggestion text
    * @throws IOException if a network error occurs
    * @throws AIException if the AI provider returns an error
    */
   public String complete(String codeBefore, String fileName)
         throws IOException, AIException {
      String prompt = "Complete the following code. "
         + "Return ONLY the completion text that should be inserted next. "
         + "Do NOT include any explanation, markdown fences, or the "
         + "existing code. Just the new code to insert.\n\n"
         + "File: " + fileName + "\n\n" + codeBefore;
      return oneShot(prompt);
   }

   /**
    * Send a one-shot request without modifying conversation history.
    *
    * @param prompt the complete user prompt
    * @return the AI response text
    * @throws IOException if a network error occurs
    * @throws AIException if the AI provider returns an error
    */
   private String oneShot(String prompt) throws IOException, AIException {
      AIConfig config = AIConfig.getInstance();
      AIProvider p = getProvider();

      List<AIProvider.Message> messages = List.of(
         new AIProvider.Message("system", config.getSystemPrompt()),
         new AIProvider.Message("user", prompt));

      return p.chatCompletion(messages, config.getMaxTokens());
   }

   /**
    * Get the current conversation history.
    *
    * @return unmodifiable copy of the conversation history
    */
   public List<AIProvider.Message> getHistory() {
      synchronized (history) {
         return List.copyOf(history);
      }
   }

   /**
    * Clear the conversation history.
    */
   public void clearHistory() {
      history.clear();
      trace("AI conversation history cleared");
   }

   /**
    * Get the number of messages in the conversation history.
    *
    * @return message count
    */
   public int getHistorySize() {
      return history.size();
   }

   /**
    * Create a provider instance from configuration.
    *
    * @param config the AI configuration
    * @return a new AIProvider instance
    * @throws AIException if the provider cannot be created
    */
   private static AIProvider createProvider(AIConfig config) throws AIException {
      String apiKey = config.getApiKey();
      String model = config.getModel();

      trace("Creating AI provider: " + config.getProvider().getId()
         + " model: " + model);

      return switch (config.getProvider()) {
         case OPENAI -> new OpenAIProvider(apiKey, model);
         case ANTHROPIC -> new AnthropicProvider(apiKey, model);
         case COPILOT -> new CopilotProvider();
      };
   }
}
