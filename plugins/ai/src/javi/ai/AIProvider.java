package javi.ai;

import java.io.IOException;
import java.util.List;

/**
 * Interface defining an AI provider for text generation.
 *
 * <p>AIProvider abstracts the communication with different AI services
 * (OpenAI, Anthropic, GitHub Copilot, local models). Implementations
 * handle authentication, request formatting, and response parsing
 * specific to each provider's API.</p>
 *
 * <h2>Message Format</h2>
 * <p>Conversations use a list of {@link Message} records with roles:
 * <ul>
 *   <li>{@code "system"} - System prompt setting AI behavior</li>
 *   <li>{@code "user"} - User messages/queries</li>
 *   <li>{@code "assistant"} - AI responses</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Implementations must be thread-safe. API calls may be made from
 * background threads while the editor UI remains responsive.</p>
 *
 * <h2>Known Implementations</h2>
 * <ul>
 *   <li>{@link OpenAIProvider} - OpenAI GPT models</li>
 *   <li>{@link AnthropicProvider} - Anthropic Claude models</li>
 * </ul>
 *
 * @see AIClient
 * @see AIConfig
 */
public interface AIProvider {

   /**
    * A single message in a conversation.
    *
    * @param role the message role: "system", "user", or "assistant"
    * @param content the message text content
    */
   record Message(String role, String content) { }

   /**
    * Send a chat completion request to the AI provider.
    *
    * <p>The messages list represents the full conversation history.
    * The provider should return the assistant's response text.</p>
    *
    * @param messages the conversation messages in chronological order
    * @param maxTokens maximum tokens in the response (0 for provider default)
    * @return the AI-generated response text
    * @throws IOException if a network or API error occurs
    * @throws AIException if the provider returns an error response
    */
   String chatCompletion(List<Message> messages, int maxTokens)
      throws IOException, AIException;

   /**
    * Get the display name of this provider.
    *
    * @return provider name (e.g., "OpenAI", "Anthropic")
    */
   String getName();

   /**
    * Get the model identifier being used.
    *
    * @return model name (e.g., "gpt-4-turbo", "claude-3-opus")
    */
   String getModel();

   /**
    * Test connectivity to the AI provider.
    *
    * <p>Sends a minimal request to verify the API key is valid
    * and the service is reachable.</p>
    *
    * @return true if connection is successful
    */
   boolean testConnection();
}
