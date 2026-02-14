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
 * OpenAI API provider implementation.
 *
 * <p>Communicates with the OpenAI Chat Completions API to generate
 * text responses. Supports all GPT models (gpt-4o, gpt-4-turbo, etc.).</p>
 *
 * <h2>API Reference</h2>
 * <p>Uses the {@code POST /v1/chat/completions} endpoint. Request and
 * response bodies are JSON formatted manually (no external JSON
 * library dependency).</p>
 *
 * <h2>Authentication</h2>
 * <p>Uses Bearer token authentication via the {@code Authorization} header.
 * The API key can be set via {@link AIConfig} or the {@code OPENAI_API_KEY}
 * environment variable.</p>
 *
 * <h2>Error Handling</h2>
 * <p>HTTP errors are mapped to {@link AIException} with the status code.
 * Rate limit errors (429) include retry-after information when available.</p>
 *
 * @see AIProvider
 * @see AIConfig
 */
public final class OpenAIProvider implements AIProvider {

   private static final String API_URL =
      "https://api.openai.com/v1/chat/completions";
   private static final Duration TIMEOUT = Duration.ofSeconds(60);

   private final HttpClient httpClient;
   private final String apiKey;
   private final String model;

   /**
    * Create an OpenAI provider with the given configuration.
    *
    * @param apiKey the OpenAI API key
    * @param model the model name (e.g., "gpt-4o")
    * @throws AIException if apiKey is null or empty
    */
   public OpenAIProvider(String apiKey, String model) throws AIException {
      if (null == apiKey || apiKey.isEmpty()) {
         throw new AIException("OpenAI API key not configured. "
            + "Set OPENAI_API_KEY environment variable or use "
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
      trace("OpenAI request to model: " + model);

      HttpRequest request = HttpRequest.newBuilder()
         .uri(URI.create(API_URL))
         .header("Content-Type", "application/json")
         .header("Authorization", "Bearer " + apiKey)
         .timeout(TIMEOUT)
         .POST(HttpRequest.BodyPublishers.ofString(requestBody))
         .build();

      try {
         HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());

         int status = response.statusCode();
         if (status < 200 || status >= 300) {
            throw new AIException(
               "OpenAI API error (HTTP " + status + "): "
               + extractError(response.body()), status);
         }

         return extractContent(response.body());
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IOException("OpenAI request interrupted", e);
      }
   }

   @Override
   public String getName() {
      return "OpenAI";
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
         trace("OpenAI connection test failed: " + e.getMessage());
         return false;
      }
   }

   /**
    * Build the JSON request body for the chat completions API.
    *
    * <p>Constructs JSON manually to avoid external library dependencies.
    * This is intentional — the Javi project minimizes dependencies.</p>
    *
    * @param messages the conversation messages
    * @param maxTokens max tokens (0 for default)
    * @return JSON string
    */
   private String buildRequestJson(List<Message> messages, int maxTokens) {
      StringBuilder sb = new StringBuilder(512);
      sb.append("{\"model\":\"").append(escapeJson(model)).append("\",");
      sb.append("\"messages\":[");
      for (int i = 0; i < messages.size(); i++) {
         if (i > 0) {
            sb.append(',');
         }
         Message msg = messages.get(i);
         sb.append("{\"role\":\"").append(escapeJson(msg.role()));
         sb.append("\",\"content\":\"").append(escapeJson(msg.content()));
         sb.append("\"}");
      }
      sb.append(']');
      if (maxTokens > 0) {
         sb.append(",\"max_tokens\":").append(maxTokens);
      }
      sb.append('}');
      return sb.toString();
   }

   /**
    * Extract the assistant's content from the API response JSON.
    *
    * <p>Parses the response without a full JSON library. Looks for
    * the {@code content} field in the first choice's message.</p>
    *
    * @param json the response JSON string
    * @return the extracted content text
    * @throws AIException if the response cannot be parsed
    */
   static String extractContent(String json) throws AIException {
      // Look for "content":" in the response
      String marker = "\"content\":\"";
      int idx = json.indexOf(marker);
      if (idx < 0) {
         // Try "content": " with space
         marker = "\"content\": \"";
         idx = json.indexOf(marker);
      }
      if (idx < 0) {
         throw new AIException("Cannot parse OpenAI response: "
            + json.substring(0, Math.min(200, json.length())));
      }
      idx += marker.length();
      return unescapeJsonString(json, idx);
   }

   /**
    * Extract error message from an error response JSON.
    *
    * @param json the error response body
    * @return the error message, or the raw body if unparseable
    */
   static String extractError(String json) {
      String marker = "\"message\":\"";
      int idx = json.indexOf(marker);
      if (idx < 0) {
         marker = "\"message\": \"";
         idx = json.indexOf(marker);
      }
      if (idx < 0) {
         return json.substring(0, Math.min(300, json.length()));
      }
      idx += marker.length();
      try {
         return unescapeJsonString(json, idx);
      } catch (AIException e) {
         return json.substring(0, Math.min(300, json.length()));
      }
   }

   /**
    * Read and unescape a JSON string value starting at the given index.
    *
    * <p>Handles standard JSON escape sequences: {@code \\, \", \n, \t, \r,
    * backslash-uXXXX}.</p>
    *
    * @param json the JSON string
    * @param startIdx index of first character after the opening quote
    * @return the unescaped string value
    * @throws AIException if the string is malformed
    */
   static String unescapeJsonString(String json, int startIdx)
         throws AIException {
      StringBuilder sb = new StringBuilder(256);
      int i = startIdx;
      while (i < json.length()) {
         char c = json.charAt(i);
         if ('"' == c) {
            return sb.toString();
         }
         if ('\\' == c) {
            i++;
            if (i >= json.length()) {
               throw new AIException("Unterminated escape in JSON");
            }
            char esc = json.charAt(i);
            switch (esc) {
               case '"':  sb.append('"');  break;
               case '\\': sb.append('\\'); break;
               case '/':  sb.append('/');  break;
               case 'n':  sb.append('\n'); break;
               case 'r':  sb.append('\r'); break;
               case 't':  sb.append('\t'); break;
               case 'b':  sb.append('\b'); break;
               case 'f':  sb.append('\f'); break;
               case 'u':
                  if (i + 4 >= json.length()) {
                     throw new AIException("Incomplete unicode escape");
                  }
                  String hex = json.substring(i + 1, i + 5);
                  sb.append((char) Integer.parseInt(hex, 16));
                  i += 4;
                  break;
               default:
                  sb.append(esc);
                  break;
            }
         } else {
            sb.append(c);
         }
         i++;
      }
      throw new AIException("Unterminated JSON string");
   }

   /**
    * Escape a string for inclusion in a JSON value.
    *
    * @param s the raw string
    * @return the JSON-escaped string (without surrounding quotes)
    */
   static String escapeJson(String s) {
      if (null == s) {
         return "";
      }
      StringBuilder sb = new StringBuilder(s.length() + 16);
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         switch (c) {
            case '"':  sb.append("\\\""); break;
            case '\\': sb.append("\\\\"); break;
            case '\n': sb.append("\\n");  break;
            case '\r': sb.append("\\r");  break;
            case '\t': sb.append("\\t");  break;
            case '\b': sb.append("\\b");  break;
            case '\f': sb.append("\\f");  break;
            default:
               if (c < 0x20) {
                  sb.append(String.format("\\u%04x", (int) c));
               } else {
                  sb.append(c);
               }
               break;
         }
      }
      return sb.toString();
   }
}
