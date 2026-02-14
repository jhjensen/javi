package javi.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static history.Tools.trace;

/**
 * Anthropic (Claude) API provider implementation.
 *
 * <p>Communicates with the Anthropic Messages API to generate text
 * responses. Supports Claude models (claude-sonnet-4-20250514, claude-3-haiku, etc.).</p>
 *
 * <h2>API Differences from OpenAI</h2>
 * <ul>
 *   <li>Uses {@code x-api-key} header instead of {@code Authorization: Bearer}</li>
 *   <li>System message is a top-level field, not in the messages array</li>
 *   <li>Requires {@code anthropic-version} header</li>
 *   <li>Response format differs (content is an array of blocks)</li>
 * </ul>
 *
 * <h2>Authentication</h2>
 * <p>Uses the {@code x-api-key} header. The API key can be set via
 * {@link AIConfig} or the {@code ANTHROPIC_API_KEY} environment variable.</p>
 *
 * @see AIProvider
 * @see AIConfig
 */
public final class AnthropicProvider implements AIProvider {

   private static final String API_URL =
      "https://api.anthropic.com/v1/messages";
   private static final String API_VERSION = "2023-06-01";
   private static final Duration TIMEOUT = Duration.ofSeconds(60);

   private final HttpClient httpClient;
   private final String apiKey;
   private final String model;

   /**
    * Create an Anthropic provider with the given configuration.
    *
    * @param apiKey the Anthropic API key
    * @param model the model name (e.g., "claude-sonnet-4-20250514")
    * @throws AIException if apiKey is null or empty
    */
   public AnthropicProvider(String apiKey, String model) throws AIException {
      if (null == apiKey || apiKey.isEmpty()) {
         throw new AIException("Anthropic API key not configured. "
            + "Set ANTHROPIC_API_KEY environment variable or use "
            + ":set ai.apikey=<key>");
      }
      this.apiKey = apiKey;
      this.model = model;
      this.httpClient = HttpClient.newBuilder()
         .connectTimeout(TIMEOUT)
         .build();
   }

   @Override
   public String chatCompletion(List<Message> messages, int maxTokens)
         throws IOException, AIException {
      String requestBody = buildRequestJson(messages, maxTokens);
      trace("Anthropic request to model: " + model);

      HttpRequest request = HttpRequest.newBuilder()
         .uri(URI.create(API_URL))
         .header("Content-Type", "application/json")
         .header("x-api-key", apiKey)
         .header("anthropic-version", API_VERSION)
         .timeout(TIMEOUT)
         .POST(HttpRequest.BodyPublishers.ofString(requestBody))
         .build();

      try {
         HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());

         int status = response.statusCode();
         if (status < 200 || status >= 300) {
            throw new AIException(
               "Anthropic API error (HTTP " + status + "): "
               + OpenAIProvider.extractError(response.body()), status);
         }

         return extractContent(response.body());
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IOException("Anthropic request interrupted", e);
      }
   }

   @Override
   public String getName() {
      return "Anthropic";
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
         trace("Anthropic connection test failed: " + e.getMessage());
         return false;
      }
   }

   /**
    * Build the JSON request body for the Anthropic Messages API.
    *
    * <p>Anthropic requires system messages as a top-level field rather
    * than in the messages array. This method separates system messages
    * from the conversation.</p>
    *
    * @param messages the conversation messages
    * @param maxTokens max tokens (must be positive for Anthropic)
    * @return JSON string
    */
   private String buildRequestJson(List<Message> messages, int maxTokens) {
      StringBuilder sb = new StringBuilder(512);
      sb.append("{\"model\":\"")
         .append(OpenAIProvider.escapeJson(model)).append("\",");

      // Anthropic requires max_tokens
      int tokens = maxTokens > 0 ? maxTokens : 2048;
      sb.append("\"max_tokens\":").append(tokens).append(',');

      // Extract system message if present
      String systemMsg = null;
      for (Message msg : messages) {
         if ("system".equals(msg.role())) {
            systemMsg = msg.content();
            break;
         }
      }
      if (null != systemMsg) {
         sb.append("\"system\":\"")
            .append(OpenAIProvider.escapeJson(systemMsg)).append("\",");
      }

      // Add non-system messages
      sb.append("\"messages\":[");
      boolean first = true;
      for (Message msg : messages) {
         if ("system".equals(msg.role())) {
            continue;
         }
         if (!first) {
            sb.append(',');
         }
         first = false;
         sb.append("{\"role\":\"")
            .append(OpenAIProvider.escapeJson(msg.role()));
         sb.append("\",\"content\":\"")
            .append(OpenAIProvider.escapeJson(msg.content()));
         sb.append("\"}");
      }
      sb.append("]}");
      return sb.toString();
   }

   /**
    * Extract text content from Anthropic's response format.
    *
    * <p>Anthropic returns content as an array of blocks:
    * {@code {"content":[{"type":"text","text":"..."}]}}</p>
    *
    * @param json the response JSON string
    * @return the extracted text content
    * @throws AIException if the response cannot be parsed
    */
   static String extractContent(String json) throws AIException {
      // Look for "text":" in the content array
      String marker = "\"text\":\"";
      int idx = json.indexOf(marker);
      if (idx < 0) {
         marker = "\"text\": \"";
         idx = json.indexOf(marker);
      }
      if (idx < 0) {
         throw new AIException("Cannot parse Anthropic response: "
            + json.substring(0, Math.min(200, json.length())));
      }
      idx += marker.length();
      return OpenAIProvider.unescapeJsonString(json, idx);
   }
}
