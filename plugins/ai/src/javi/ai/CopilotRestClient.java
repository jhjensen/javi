package javi.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static history.Tools.trace;

/**
 * REST API client for GitHub Copilot.
 *
 * <p>Communicates with the GitHub Copilot REST API at
 * {@code https://api.githubcopilot.com}. The API uses
 * OpenAI-compatible request/response format for chat
 * completions.</p>
 *
 * <h2>Authentication Flow</h2>
 * <ol>
 *   <li>Read GitHub OAuth token from
 *       {@code ~/.config/github-copilot/apps.json} or
 *       {@code GH_COPILOT_TOKEN} env var</li>
 *   <li>Exchange OAuth token for a short-lived Copilot
 *       session token via GitHub API</li>
 *   <li>Use session token as Bearer for API requests</li>
 * </ol>
 *
 * <p>If no cached token exists, supports GitHub Device Flow
 * OAuth for initial authentication.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>Thread-safe. Token refresh is synchronized.</p>
 *
 * @see CopilotProvider
 * @see AIProvider
 */
public final class CopilotRestClient {

   /** Default Copilot API endpoint. */
   static final String DEFAULT_API_URL =
      "https://api.githubcopilot.com";

   /** GitHub API endpoint for Copilot token exchange. */
   static final String TOKEN_URL =
      "https://api.github.com/copilot_internal/v2/token";

   /** GitHub device flow code endpoint. */
   static final String DEVICE_CODE_URL =
      "https://github.com/login/device/code";

   /** GitHub device flow token endpoint. */
   static final String DEVICE_TOKEN_URL =
      "https://github.com/login/oauth/access_token";

   /** VS Code Copilot extension OAuth client ID. */
   static final String CLIENT_ID = "Iv1.b507a08c87ecfe98";

   /** OAuth scope for Copilot. */
   private static final String OAUTH_SCOPE = "read:user";

   /** Relative path to cached OAuth token file. */
   public static final String APPS_JSON_PATH =
      ".config/github-copilot/apps.json";

   /** Environment variable for OAuth token override. */
   public static final String TOKEN_ENV = "GH_COPILOT_TOKEN";

   /** Renew session token 5 minutes before expiry. */
   private static final long REFRESH_MARGIN_SECS = 300;

   /** Device flow polling interval (seconds). */
   static final int DEVICE_POLL_SECS = 5;

   /** Device flow maximum wait time (seconds). */
   static final int DEVICE_TIMEOUT_SECS = 600;

   /** Maximum number of tool call round-trips per request. */
   static final int MAX_TOOL_ROUNDS = 10;

   /**
    * Represents a tool call requested by the model.
    *
    * @param id the unique call ID (echoed back in tool result)
    * @param name the tool function name
    * @param arguments the JSON arguments string
    */
   public record ToolCall(String id, String name,
         String arguments) { }

   private final HttpClient httpClient;

   /** Cached GitHub OAuth token (from apps.json). */
   private volatile String oauthToken;

   /** Cached Copilot session token (from exchange). */
   private volatile String sessionToken;

   /**
    * Get the current request timeout from configuration.
    *
    * @return timeout duration
    */
   private static Duration getTimeout() {
      return Duration.ofSeconds(
         AIConfig.getInstance().getTimeoutSeconds());
   }

   /** Copilot API base URL (from token exchange). */
   private volatile String apiUrl = DEFAULT_API_URL;

   /** Session token expiration (epoch seconds). */
   private volatile long tokenExpiresAt;

   /**
    * Create a CopilotRestClient.
    *
    * <p>Attempts to load a cached OAuth token from
    * {@code ~/.config/github-copilot/apps.json} or the
    * {@code GH_COPILOT_TOKEN} environment variable.</p>
    */
   public CopilotRestClient() {
      Duration timeout = Duration.ofSeconds(
         AIConfig.getInstance().getTimeoutSeconds());
      this.httpClient = HttpClient.newBuilder()
         .connectTimeout(timeout)
         .build();
      this.oauthToken = loadOAuthToken();
   }

   /**
    * Create a CopilotRestClient with an explicit token.
    *
    * @param token the GitHub OAuth token
    */
   public CopilotRestClient(String token) {
      Duration timeout = Duration.ofSeconds(
         AIConfig.getInstance().getTimeoutSeconds());
      this.httpClient = HttpClient.newBuilder()
         .connectTimeout(timeout)
         .build();
      this.oauthToken = token;
   }

   /**
    * Check if an OAuth token is available.
    *
    * @return true if a token is loaded
    */
   public boolean hasToken() {
      return null != oauthToken && !oauthToken.isEmpty();
   }

   /**
    * Send a chat completion request to the Copilot API.
    *
    * <p>Uses OpenAI-compatible request format. Automatically
    * handles token exchange and refresh.</p>
    *
    * @param messages the conversation messages
    * @param model the model identifier
    * @param maxTokens max response tokens (0 for default)
    * @return the response body JSON
    * @throws IOException if a network error occurs
    * @throws AIException if auth or API call fails
    */
   public String chatCompletion(
         List<AIProvider.Message> messages,
         String model, int maxTokens)
         throws IOException, AIException {
      ensureSessionToken();

      String body = buildChatJson(messages, model, maxTokens);
      trace("Copilot REST request to model: " + model);

      HttpResponse<String> response =
         sendApiPost("/chat/completions", body);

      int status = response.statusCode();
      if (401 == status) {
         trace("Copilot: 401 — refreshing token");
         invalidateSession();
         ensureSessionToken();
         response =
            sendApiPost("/chat/completions", body);
         status = response.statusCode();
      }

      if (status < 200 || status >= 300) {
         throw new AIException(
            "Copilot API error (HTTP " + status + "): "
            + OpenAIProvider.extractError(response.body()),
            status);
      }
      return response.body();
   }

   /**
    * Send a chat completion request with tool definitions.
    *
    * @param messages the conversation messages
    * @param model the model identifier
    * @param maxTokens max response tokens (0 for default)
    * @param toolsJson JSON tools array, or null
    * @return the response body JSON
    * @throws IOException if a network error occurs
    * @throws AIException if auth or API call fails
    */
   public String chatCompletion(
         List<AIProvider.Message> messages,
         String model, int maxTokens,
         String toolsJson)
         throws IOException, AIException {
      ensureSessionToken();

      String body = buildChatJson(
         messages, model, maxTokens, toolsJson);
      trace("Copilot REST request to model: " + model
         + (null != toolsJson ? " (with tools)" : ""));

      HttpResponse<String> response =
         sendApiPost("/chat/completions", body);

      int status = response.statusCode();
      if (401 == status) {
         trace("Copilot: 401 — refreshing token");
         invalidateSession();
         ensureSessionToken();
         response =
            sendApiPost("/chat/completions", body);
         status = response.statusCode();
      }

      if (status < 200 || status >= 300) {
         throw new AIException(
            "Copilot API error (HTTP " + status + "): "
            + OpenAIProvider.extractError(
               response.body()),
            status);
      }
      return response.body();
   }

   /**
    * Send a raw JSON body as a chat completion request.
    *
    * <p>Used for tool follow-up requests where the JSON is
    * pre-built with special message types (assistant with
    * tool_calls, tool results).</p>
    *
    * @param body the pre-built request JSON
    * @return the response body JSON
    * @throws IOException if a network error occurs
    * @throws AIException if auth or API call fails
    */
   public String chatCompletionRaw(String body)
         throws IOException, AIException {
      ensureSessionToken();
      trace("Copilot raw request (tool followup)");

      HttpResponse<String> response =
         sendApiPost("/chat/completions", body);

      int status = response.statusCode();
      if (401 == status) {
         trace("Copilot: 401 raw — refreshing token");
         invalidateSession();
         ensureSessionToken();
         response =
            sendApiPost("/chat/completions", body);
         status = response.statusCode();
      }

      if (status < 200 || status >= 300) {
         throw new AIException(
            "Copilot tool followup error (HTTP "
            + status + "): "
            + OpenAIProvider.extractError(
               response.body()),
            status);
      }
      return response.body();
   }

   /**
    * Send a streaming chat completion request.
    *
    * <p>Uses SSE (Server-Sent Events) to receive tokens as they
    * are generated. Each content delta is passed to the callback
    * immediately. Returns the concatenated full response.</p>
    *
    * @param messages the conversation messages
    * @param model the model identifier
    * @param maxTokens max response tokens (0 for default)
    * @param onToken callback for each content token chunk
    * @return the full concatenated response text
    * @throws IOException if a network error occurs
    * @throws AIException if auth or API call fails
    */
   public String chatCompletionStreaming(
         List<AIProvider.Message> messages,
         String model, int maxTokens,
         Consumer<String> onToken)
         throws IOException, AIException {
      ensureSessionToken();

      String body = buildStreamingChatJson(
         messages, model, maxTokens);
      trace("Copilot streaming request to model: " + model);

      HttpRequest request = HttpRequest.newBuilder()
         .uri(URI.create(apiUrl + "/chat/completions"))
         .header("Content-Type", "application/json")
         .header("Authorization",
            "Bearer " + sessionToken)
         .header("Editor-Version", "Javi/1.0")
         .header("Copilot-Integration-Id",
            "vscode-chat")
         .header("OpenAI-Intent",
            "conversation-panel")
         .timeout(getTimeout())
         .POST(HttpRequest.BodyPublishers.ofString(body))
         .build();

      try {
         HttpResponse<InputStream> response =
            httpClient.send(request,
               HttpResponse.BodyHandlers.ofInputStream());

         int status = response.statusCode();
         if (401 == status) {
            trace("Copilot: 401 streaming — refresh");
            invalidateSession();
            ensureSessionToken();
            request = HttpRequest.newBuilder()
               .uri(URI.create(
                  apiUrl + "/chat/completions"))
               .header("Content-Type",
                  "application/json")
               .header("Authorization",
                  "Bearer " + sessionToken)
               .header("Editor-Version", "Javi/1.0")
               .header("Copilot-Integration-Id",
                  "vscode-chat")
               .header("OpenAI-Intent",
                  "conversation-panel")
               .timeout(getTimeout())
               .POST(HttpRequest.BodyPublishers
                  .ofString(body))
               .build();
            response = httpClient.send(request,
               HttpResponse.BodyHandlers
                  .ofInputStream());
            status = response.statusCode();
         }

         if (status < 200 || status >= 300) {
            String errorBody = new String(
               response.body().readAllBytes(),
               StandardCharsets.UTF_8);
            throw new AIException(
               "Copilot streaming error (HTTP "
               + status + "): "
               + OpenAIProvider.extractError(errorBody),
               status);
         }

         return readSSEStream(response.body(), onToken);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IOException(
            "Streaming request interrupted", e);
      }
   }

   /**
    * Read an SSE stream and extract content deltas.
    *
    * <p>SSE format: lines starting with {@code data: } contain
    * JSON chunks. Stream ends with {@code data: [DONE]}.</p>
    *
    * @param input the response input stream
    * @param onToken callback for each content token
    * @return the full concatenated response
    * @throws IOException if a read error occurs
    */
   static String readSSEStream(InputStream input,
         Consumer<String> onToken) throws IOException {
      StringBuilder full = new StringBuilder(1024);
      try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(
               input, StandardCharsets.UTF_8))) {
         String line;
         while (null != (line = reader.readLine())) {
            if (!line.startsWith("data: ")) {
               continue;
            }
            String data = line.substring(6).trim();
            if ("[DONE]".equals(data)) {
               break;
            }
            String delta = extractStreamDelta(data);
            if (null != delta && !delta.isEmpty()) {
               full.append(delta);
               onToken.accept(delta);
            }
         }
      }
      return full.toString();
   }

   /**
    * Extract content delta from a streaming chunk JSON.
    *
    * <p>Looks for {@code "delta":{"content":"..."}} in the
    * chunk JSON.</p>
    *
    * @param json the chunk JSON
    * @return the content delta, or null
    */
   static String extractStreamDelta(String json) {
      String marker = "\"delta\":{\"content\":\"";
      int idx = json.indexOf(marker);
      if (idx < 0) {
         marker = "\"delta\": {\"content\": \"";
         idx = json.indexOf(marker);
      }
      if (idx < 0) {
         marker = "\"delta\":{\"content\": \"";
         idx = json.indexOf(marker);
      }
      if (idx < 0) {
         return null;
      }
      idx += marker.length();
      try {
         return OpenAIProvider.unescapeJsonString(
            json, idx);
      } catch (AIException e) {
         return null;
      }
   }

   /**
    * Check if a response JSON contains tool calls.
    *
    * @param json the response JSON
    * @return true if tool_calls are present
    */
   static boolean hasToolCalls(String json) {
      return json.contains("\"tool_calls\"");
   }

   /**
    * Extract tool calls from a chat completion response.
    *
    * <p>Parses the {@code tool_calls} array from the first
    * choice's message. Each tool call has an id, function
    * name, and arguments string.</p>
    *
    * @param json the response JSON
    * @return list of tool calls, empty if none found
    */
   static List<ToolCall> extractToolCalls(String json) {
      List<ToolCall> calls = new ArrayList<>();
      String marker = "\"tool_calls\":[";
      int idx = json.indexOf(marker);
      if (idx < 0) {
         marker = "\"tool_calls\": [";
         idx = json.indexOf(marker);
      }
      if (idx < 0) {
         return calls;
      }

      // Parse each tool_call object in the array
      int pos = idx + marker.length();
      while (pos < json.length()) {
         // Find next tool call object
         int objStart = json.indexOf('{', pos);
         if (objStart < 0) {
            break;
         }
         // Check if we've passed the closing ]
         int arrEnd = json.indexOf(']', pos);
         if (arrEnd >= 0 && arrEnd < objStart) {
            break;
         }

         String callId = extractJsonField(json,
            "\"id\"", objStart);
         String funcName = extractToolFuncName(json,
            objStart);
         String funcArgs = extractToolFuncArgs(json,
            objStart);

         if (null != callId && null != funcName) {
            calls.add(new ToolCall(
               callId, funcName, funcArgs));
            trace("Tool call parsed: " + funcName
               + " id=" + callId);
         }

         // Move past this tool call object
         pos = skipJsonObject(json, objStart);
      }
      return calls;
   }

   /**
    * Extract the function name from a tool_call object.
    */
   private static String extractToolFuncName(
         String json, int from) {
      // Look for "function":{"name":"xxx"
      int funcIdx = json.indexOf("\"function\"", from);
      if (funcIdx < 0) {
         return null;
      }
      return extractJsonField(json,
         "\"name\"", funcIdx);
   }

   /**
    * Extract the function arguments from a tool_call object.
    */
   private static String extractToolFuncArgs(
         String json, int from) {
      int funcIdx = json.indexOf("\"function\"", from);
      if (funcIdx < 0) {
         return null;
      }
      // Look for "arguments": which may be a string value
      String marker1 = "\"arguments\":\"";
      int argIdx = json.indexOf(marker1, funcIdx);
      if (argIdx < 0) {
         marker1 = "\"arguments\": \"";
         argIdx = json.indexOf(marker1, funcIdx);
      }
      if (argIdx < 0) {
         return "{}";
      }
      argIdx += marker1.length();
      try {
         return OpenAIProvider.unescapeJsonString(
            json, argIdx);
      } catch (AIException e) {
         return "{}";
      }
   }

   /**
    * Extract a JSON string field value starting search at
    * the given position.
    */
   private static String extractJsonField(String json,
         String fieldName, int from) {
      String marker1 = fieldName + ":\"";
      int idx = json.indexOf(marker1, from);
      if (idx < 0) {
         marker1 = fieldName + ": \"";
         idx = json.indexOf(marker1, from);
      }
      if (idx < 0) {
         return null;
      }
      idx += marker1.length();
      try {
         return OpenAIProvider.unescapeJsonString(
            json, idx);
      } catch (AIException e) {
         return null;
      }
   }

   /**
    * Skip past a JSON object starting at the given position.
    * Returns the position after the closing brace.
    */
   private static int skipJsonObject(String json,
         int from) {
      int depth = 0;
      boolean inString = false;
      for (int i = from; i < json.length(); i++) {
         char c = json.charAt(i);
         if (inString) {
            if ('\\' == c) {
               i++; // skip escaped char
            } else if ('"' == c) {
               inString = false;
            }
         } else {
            if ('"' == c) {
               inString = true;
            } else if ('{' == c) {
               depth++;
            } else if ('}' == c) {
               depth--;
               if (0 == depth) {
                  return i + 1;
               }
            }
         }
      }
      return json.length();
   }

   /**
    * Build a JSON message object for an assistant response
    * with tool calls (for multi-turn tool protocol).
    *
    * <p>The assistant message echoes back the tool_calls
    * array so the API knows which calls are being responded
    * to in the subsequent tool result messages.</p>
    *
    * @param toolCalls the tool calls from the response
    * @return JSON string for the assistant message
    */
   public static String buildAssistantToolCallJson(
         List<ToolCall> toolCalls) {
      StringBuilder sb = new StringBuilder(256);
      sb.append("{\"role\":\"assistant\",\"content\":null,")
         .append("\"tool_calls\":[");
      for (int i = 0; i < toolCalls.size(); i++) {
         if (i > 0) {
            sb.append(',');
         }
         ToolCall tc = toolCalls.get(i);
         sb.append("{\"id\":\"")
            .append(OpenAIProvider.escapeJson(tc.id()))
            .append("\",\"type\":\"function\",")
            .append("\"function\":{\"name\":\"")
            .append(OpenAIProvider.escapeJson(tc.name()))
            .append("\",\"arguments\":\"")
            .append(OpenAIProvider.escapeJson(
               tc.arguments()))
            .append("\"}}");
      }
      sb.append("]}");
      return sb.toString();
   }

   /**
    * Build a JSON message object for a tool result.
    *
    * @param toolCallId the call ID to respond to
    * @param result the tool execution result text
    * @return JSON string for the tool result message
    */
   public static String buildToolResultJson(
         String toolCallId, String result) {
      return "{\"role\":\"tool\",\"tool_call_id\":\""
         + OpenAIProvider.escapeJson(toolCallId)
         + "\",\"content\":\""
         + OpenAIProvider.escapeJson(result) + "\"}";
   }

   /**
    * Build a follow-up request JSON including tool results.
    *
    * <p>Constructs a full chat completion request with the
    * original messages, the assistant's tool_calls message,
    * and the tool result messages appended.</p>
    *
    * @param origMessages the original conversation messages
    * @param toolCalls the tool calls from the assistant
    * @param toolResults tool results in call order
    * @param model the model identifier
    * @param maxTokens max response tokens
    * @param toolsJson tools array JSON, or null
    * @param streaming whether to enable streaming
    * @return the request body JSON
    */
   public static String buildToolFollowupJson(
         List<AIProvider.Message> origMessages,
         List<ToolCall> toolCalls,
         List<String> toolResults,
         String model, int maxTokens,
         String toolsJson, boolean streaming) {
      StringBuilder sb = new StringBuilder(1024);
      sb.append("{\"model\":\"")
         .append(OpenAIProvider.escapeJson(model))
         .append("\"");
      if (streaming) {
         sb.append(",\"stream\":true");
      }
      sb.append(",\"messages\":[");

      // Original messages
      appendMessagesJson(sb, origMessages);

      // Assistant message with tool_calls
      sb.append(',')
         .append(buildAssistantToolCallJson(toolCalls));

      // Tool result messages
      for (int i = 0; i < toolCalls.size(); i++) {
         sb.append(',');
         String result = i < toolResults.size()
            ? toolResults.get(i) : "";
         sb.append(buildToolResultJson(
            toolCalls.get(i).id(), result));
      }

      sb.append(']');
      if (null != toolsJson) {
         sb.append(",\"tools\":").append(toolsJson);
      }
      if (maxTokens > 0) {
         sb.append(",\"max_tokens\":")
            .append(maxTokens);
      }
      sb.append('}');
      return sb.toString();
   }

   /**
    * Parse tool arguments JSON into a simple key-value map.
    *
    * <p>Handles flat JSON objects with string values.
    * Numeric and boolean values are converted to strings.</p>
    *
    * @param argsJson the JSON arguments string
    * @return map of parameter names to string values
    */
   public static java.util.Map<String, String> parseToolArgs(
         String argsJson) {
      java.util.Map<String, String> params =
         new java.util.LinkedHashMap<>();
      if (null == argsJson || argsJson.isEmpty()
            || "{}".equals(argsJson.trim())) {
         return params;
      }
      // Parse key-value pairs from flat JSON object
      int pos = argsJson.indexOf('{');
      if (pos < 0) {
         return params;
      }
      pos++;
      while (pos < argsJson.length()) {
         // Find next key
         int keyStart = argsJson.indexOf('"', pos);
         if (keyStart < 0) {
            break;
         }
         keyStart++;
         int keyEnd = argsJson.indexOf('"', keyStart);
         if (keyEnd < 0) {
            break;
         }
         String key = argsJson.substring(
            keyStart, keyEnd);
         pos = keyEnd + 1;
         // Skip colon and whitespace
         int colonIdx = argsJson.indexOf(':', pos);
         if (colonIdx < 0) {
            break;
         }
         pos = colonIdx + 1;
         while (pos < argsJson.length()
               && ' ' == argsJson.charAt(pos)) {
            pos++;
         }
         // Read value (string, number, or boolean)
         if (pos < argsJson.length()
               && '"' == argsJson.charAt(pos)) {
            pos++;
            try {
               String val =
                  OpenAIProvider.unescapeJsonString(
                     argsJson, pos);
               params.put(key, val);
               // Skip past the string value
               pos = skipJsonString(argsJson, pos);
            } catch (AIException e) {
               break;
            }
         } else {
            // Number or boolean — read to next delimiter
            int end = pos;
            while (end < argsJson.length()) {
               char ch = argsJson.charAt(end);
               if (',' == ch || '}' == ch
                     || ']' == ch) {
                  break;
               }
               end++;
            }
            params.put(key,
               argsJson.substring(pos, end).trim());
            pos = end;
         }
         // Skip comma
         int comma = argsJson.indexOf(',', pos);
         if (comma < 0) {
            break;
         }
         pos = comma + 1;
      }
      return params;
   }

   /**
    * Skip past a JSON string value (past closing quote).
    */
   private static int skipJsonString(String json,
         int from) {
      for (int i = from; i < json.length(); i++) {
         char c = json.charAt(i);
         if ('\\' == c) {
            i++;
         } else if ('"' == c) {
            return i + 1;
         }
      }
      return json.length();
   }

   /**
    * Build streaming chat completion request JSON.
    */
   public static String buildStreamingChatJson(
         List<AIProvider.Message> messages,
         String model, int maxTokens) {
      return buildStreamingChatJson(messages, model,
         maxTokens, null);
   }

   /**
    * Build streaming chat completion request JSON with tools.
    *
    * @param messages the conversation messages
    * @param model the model identifier
    * @param maxTokens max response tokens (0 for default)
    * @param toolsJson JSON tools array, or null to omit
    * @return the request body JSON
    */
   public static String buildStreamingChatJson(
         List<AIProvider.Message> messages,
         String model, int maxTokens,
         String toolsJson) {
      StringBuilder sb = new StringBuilder(512);
      sb.append("{\"model\":\"")
         .append(OpenAIProvider.escapeJson(model))
         .append("\",\"stream\":true,\"messages\":[");
      appendMessagesJson(sb, messages);
      sb.append(']');
      if (null != toolsJson) {
         sb.append(",\"tools\":").append(toolsJson);
      }
      if (maxTokens > 0) {
         sb.append(",\"max_tokens\":")
            .append(maxTokens);
      }
      sb.append('}');
      return sb.toString();
   }

   /**
    * Append messages JSON array content to a StringBuilder.
    *
    * <p>Handles both regular messages (role + content) and
    * tool result messages (role "tool" with tool_call_id).</p>
    */
   private static void appendMessagesJson(
         StringBuilder sb,
         List<AIProvider.Message> messages) {
      for (int i = 0; i < messages.size(); i++) {
         if (i > 0) {
            sb.append(',');
         }
         AIProvider.Message msg = messages.get(i);
         sb.append("{\"role\":\"")
            .append(OpenAIProvider.escapeJson(
               msg.role()))
            .append("\"");
         if (null != msg.content()) {
            sb.append(",\"content\":\"")
               .append(OpenAIProvider.escapeJson(
                  msg.content()))
               .append("\"");
         } else {
            sb.append(",\"content\":null");
         }
         sb.append('}');
      }
   }

   /**
    * List available models from the Copilot API.
    *
    * @return list of model identifier strings
    * @throws IOException if a network error occurs
    * @throws AIException if auth or API call fails
    */
   public List<String> listModels()
         throws IOException, AIException {
      ensureSessionToken();

      HttpRequest request = HttpRequest.newBuilder()
         .uri(URI.create(apiUrl + "/models"))
         .header("Authorization", "Bearer " + sessionToken)
         .header("Editor-Version", "Javi/1.0")
         .header("Copilot-Integration-Id", "vscode-chat")
         .timeout(getTimeout())
         .GET()
         .build();

      try {
         HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());

         int status = response.statusCode();
         if (status < 200 || status >= 300) {
            throw new AIException(
               "Copilot models error (HTTP " + status
               + "): " + truncate(response.body(), 300),
               status);
         }
         return parseModelList(response.body());
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IOException(
            "Copilot models request interrupted", e);
      }
   }

   /**
    * Initiate GitHub Device Flow OAuth authentication.
    *
    * <p>Returns a {@link DeviceFlowInfo} with the user code
    * and verification URL. The caller should display these
    * to the user, then call {@link #pollDeviceFlow} to
    * complete authentication.</p>
    *
    * @return device flow info for user display
    * @throws IOException if a network error occurs
    * @throws AIException if the request fails
    */
   public DeviceFlowInfo startDeviceFlow()
         throws IOException, AIException {
      String body = "{\"client_id\":\"" + CLIENT_ID
         + "\",\"scope\":\"" + OAUTH_SCOPE + "\"}";

      HttpRequest request = HttpRequest.newBuilder()
         .uri(URI.create(DEVICE_CODE_URL))
         .header("Content-Type", "application/json")
         .header("Accept", "application/json")
         .timeout(getTimeout())
         .POST(HttpRequest.BodyPublishers.ofString(body))
         .build();

      try {
         HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());

         int status = response.statusCode();
         if (status < 200 || status >= 300) {
            throw new AIException(
               "Device flow failed (HTTP " + status
               + "): " + response.body(), status);
         }
         return parseDeviceFlowResponse(response.body());
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IOException(
            "Device flow request interrupted", e);
      }
   }

   /**
    * Poll for device flow completion.
    *
    * <p>Blocks until the user completes browser authorization
    * or the timeout is reached. On success, saves the OAuth
    * token to {@code apps.json}.</p>
    *
    * @param deviceCode the code from {@link #startDeviceFlow}
    * @return true if authentication succeeded
    * @throws IOException if a network error occurs
    * @throws AIException if polling fails
    */
   public boolean pollDeviceFlow(String deviceCode)
         throws IOException, AIException {
      long deadline = System.currentTimeMillis()
         + DEVICE_TIMEOUT_SECS * 1000L;

      while (System.currentTimeMillis() < deadline) {
         sleepSeconds(DEVICE_POLL_SECS);

         String body = "{\"client_id\":\"" + CLIENT_ID
            + "\",\"device_code\":\""
            + OpenAIProvider.escapeJson(deviceCode)
            + "\",\"grant_type\":"
            + "\"urn:ietf:params:oauth:grant-type:"
            + "device_code\"}";

         HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(DEVICE_TOKEN_URL))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(getTimeout())
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

         try {
            HttpResponse<String> resp = httpClient.send(
               request,
               HttpResponse.BodyHandlers.ofString());

            String respBody = resp.body();
            if (respBody.contains("\"error\"")) {
               String err =
                  extractJsonField(respBody, "error");
               if ("authorization_pending".equals(err)
                     || "slow_down".equals(err)) {
                  continue;
               }
               throw new AIException(
                  "Device flow error: " + err);
            }

            String token = extractJsonField(
               respBody, "access_token");
            if (null != token && !token.isEmpty()) {
               this.oauthToken = token;
               invalidateSession();
               saveOAuthToken(token);
               trace("Copilot: device flow auth OK");
               return true;
            }
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(
               "Device flow polling interrupted", e);
         }
      }
      trace("Copilot: device flow timed out");
      return false;
   }

   // ── Token Management ─────────────────────────────────────────

   /**
    * Ensure a valid Copilot session token is available.
    *
    * <p>Exchanges the GitHub OAuth token for a Copilot session
    * token if needed, or refreshes an expired one.</p>
    */
   private synchronized void ensureSessionToken()
         throws IOException, AIException {
      if (null == oauthToken || oauthToken.isEmpty()) {
         throw new AIException(
            "No GitHub Copilot OAuth token. "
            + "Run :ai auth to authenticate, or set "
            + TOKEN_ENV + " env var.");
      }

      long now = System.currentTimeMillis() / 1000;
      if (null != sessionToken
            && now < tokenExpiresAt - REFRESH_MARGIN_SECS) {
         return;
      }

      trace("Copilot: exchanging OAuth for session token");

      HttpRequest request = HttpRequest.newBuilder()
         .uri(URI.create(TOKEN_URL))
         .header("Authorization", "token " + oauthToken)
         .header("Accept", "application/json")
         .header("Editor-Version", "Javi/1.0")
         .header("Editor-Plugin-Version",
            "javi-copilot/1.0")
         .timeout(getTimeout())
         .GET()
         .build();

      try {
         HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());

         int status = response.statusCode();
         if (status < 200 || status >= 300) {
            throw new AIException(
               "Copilot token exchange failed (HTTP "
               + status + "): "
               + truncate(response.body(), 300),
               status);
         }

         String body = response.body();
         sessionToken =
            extractJsonField(body, "token");
         if (null == sessionToken
               || sessionToken.isEmpty()) {
            throw new AIException(
               "Token exchange returned no token");
         }

         parseTokenExpiry(body, now);
         parseApiEndpoint(body);

         trace("Copilot: session token obtained, "
            + "expires at " + tokenExpiresAt);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IOException(
            "Token exchange interrupted", e);
      }
   }

   /**
    * Parse token expiry from exchange response.
    */
   private void parseTokenExpiry(String body, long now) {
      String expiresStr =
         extractJsonField(body, "expires_at");
      if (null != expiresStr) {
         try {
            tokenExpiresAt = Long.parseLong(expiresStr);
         } catch (NumberFormatException e) {
            tokenExpiresAt = now + 1800;
         }
      } else {
         tokenExpiresAt = now + 1800;
      }
   }

   /**
    * Parse API endpoint URL from exchange response.
    */
   private void parseApiEndpoint(String body) {
      String endpoint = extractJsonField(body, "api");
      if (null != endpoint && !endpoint.isEmpty()) {
         apiUrl = endpoint;
      }
   }

   /**
    * Invalidate the cached session token.
    */
   private synchronized void invalidateSession() {
      sessionToken = null;
      tokenExpiresAt = 0;
   }

   /**
    * Send a POST request to the Copilot API.
    */
   private HttpResponse<String> sendApiPost(
         String path, String body)
         throws IOException, AIException {
      HttpRequest request = HttpRequest.newBuilder()
         .uri(URI.create(apiUrl + path))
         .header("Content-Type", "application/json")
         .header("Authorization",
            "Bearer " + sessionToken)
         .header("Editor-Version", "Javi/1.0")
         .header("Copilot-Integration-Id",
            "vscode-chat")
         .header("OpenAI-Intent",
            "conversation-panel")
         .timeout(getTimeout())
         .POST(HttpRequest.BodyPublishers.ofString(body))
         .build();

      try {
         return httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IOException(
            "Copilot request interrupted", e);
      }
   }

   // ── Token I/O ────────────────────────────────────────────────

   /**
    * Load OAuth token from apps.json or environment.
    *
    * @return the OAuth token, or null if not found
    */
   public static String loadOAuthToken() {
      String envToken = System.getenv(TOKEN_ENV);
      if (null != envToken && !envToken.isEmpty()) {
         trace("Copilot: using token from " + TOKEN_ENV);
         return envToken;
      }

      Path appsJson = AIConfig.getInstance().resolveAuthFile();
      return readTokenFromAppsJson(appsJson);
   }

   /**
    * Read OAuth token from the apps.json file.
    *
    * <p>File format:
    * {@code {"github.com":{"oauth_token":"gho_xxx"}}}</p>
    *
    * @param path the path to apps.json
    * @return the token, or null if not found
    */
   public static String readTokenFromAppsJson(Path path) {
      if (!Files.isRegularFile(path)) {
         trace("Copilot: apps.json not found: " + path);
         return null;
      }
      try {
         String json = Files.readString(
            path, StandardCharsets.UTF_8);
         String token =
            extractJsonField(json, "oauth_token");
         if (null != token && !token.isEmpty()) {
            trace("Copilot: loaded token from " + path);
            return token;
         }
         trace("Copilot: no oauth_token in " + path);
         return null;
      } catch (IOException e) {
         trace("Copilot: error reading " + path
            + ": " + e);
         return null;
      }
   }

   /**
    * Save OAuth token to apps.json for future use.
    *
    * @param token the OAuth token to save
    */
   public static void saveOAuthToken(String token) {
      Path appsJson = AIConfig.getInstance().resolveAuthFile();
      try {
         Files.createDirectories(appsJson.getParent());
         String json =
            "{\"github.com\":{\"oauth_token\":\""
            + OpenAIProvider.escapeJson(token) + "\"}}";
         Files.writeString(
            appsJson, json, StandardCharsets.UTF_8);
         trace("Copilot: saved token to " + appsJson);
      } catch (IOException e) {
         trace("Copilot: failed to save token: " + e);
      }
   }

   // ── JSON Helpers ─────────────────────────────────────────────

   /**
    * Build chat completion request JSON.
    */
   public static String buildChatJson(
         List<AIProvider.Message> messages,
         String model, int maxTokens) {
      return buildChatJson(messages, model, maxTokens,
         null);
   }

   /**
    * Build chat completion request JSON with optional tools.
    *
    * @param messages the conversation messages
    * @param model the model identifier
    * @param maxTokens max response tokens (0 for default)
    * @param toolsJson JSON tools array, or null to omit
    * @return the request body JSON
    */
   public static String buildChatJson(
         List<AIProvider.Message> messages,
         String model, int maxTokens,
         String toolsJson) {
      StringBuilder sb = new StringBuilder(512);
      sb.append("{\"model\":\"")
         .append(OpenAIProvider.escapeJson(model))
         .append("\",\"messages\":[");
      appendMessagesJson(sb, messages);
      sb.append(']');
      if (null != toolsJson) {
         sb.append(",\"tools\":").append(toolsJson);
      }
      if (maxTokens > 0) {
         sb.append(",\"max_tokens\":")
            .append(maxTokens);
      }
      sb.append('}');
      return sb.toString();
   }

   /**
    * Parse model list from the models API response.
    *
    * <p>Expects OpenAI-compatible format:
    * {@code {"data":[{"id":"model-name",...},...]}}.</p>
    *
    * @param json the response JSON
    * @return list of model IDs
    */
   public static List<String> parseModelList(String json) {
      List<String> models = new ArrayList<>();
      String marker = "\"id\":\"";
      int idx = 0;
      while (true) {
         idx = json.indexOf(marker, idx);
         if (idx < 0)
            break;
         idx += marker.length();
         int end = json.indexOf('"', idx);
         if (end < 0)
            break;
         String modelId = json.substring(idx, end);
         if (!"model".equals(modelId)) {
            models.add(modelId);
         }
         idx = end + 1;
      }
      return models;
   }

   /**
    * Extract a string field value from JSON.
    *
    * <p>Simple extraction — finds the first occurrence of
    * {@code "fieldName":"value"} or with a space after the
    * colon. Also handles numeric values.</p>
    *
    * @param json the JSON string
    * @param fieldName the field name to extract
    * @return the field value, or null if not found
    */
   public static String extractJsonField(
         String json, String fieldName) {
      String marker = "\"" + fieldName + "\":\"";
      int idx = json.indexOf(marker);
      if (idx < 0) {
         marker = "\"" + fieldName + "\": \"";
         idx = json.indexOf(marker);
      }
      if (idx >= 0) {
         idx += marker.length();
         return readQuotedValue(json, idx);
      }

      // Try unquoted (numeric/boolean) value
      marker = "\"" + fieldName + "\":";
      idx = json.indexOf(marker);
      if (idx < 0) {
         marker = "\"" + fieldName + "\": ";
         idx = json.indexOf(marker);
      }
      if (idx >= 0) {
         idx += marker.length();
         return readUnquotedValue(json, idx);
      }
      return null;
   }

   /**
    * Read a quoted string value starting at the given index
    * (just past the opening quote).
    */
   private static String readQuotedValue(
         String json, int idx) {
      int end = idx;
      while (end < json.length()
            && '"' != json.charAt(end)) {
         if ('\\' == json.charAt(end))
            end++;
         end++;
      }
      if (end >= json.length())
         return null;
      return json.substring(idx, end);
   }

   /**
    * Read an unquoted value (number, boolean, etc.) from
    * the given index until a delimiter.
    */
   private static String readUnquotedValue(
         String json, int idx) {
      int end = idx;
      while (end < json.length()) {
         char c = json.charAt(end);
         if (',' == c || '}' == c || ']' == c)
            break;
         end++;
      }
      String val = json.substring(idx, end).trim();
      if (val.startsWith("\"") && val.endsWith("\"")) {
         val = val.substring(1, val.length() - 1);
      }
      return val;
   }

   /**
    * Parse device flow initiation response.
    */
   private static DeviceFlowInfo parseDeviceFlowResponse(
         String json) throws AIException {
      String deviceCode =
         extractJsonField(json, "device_code");
      String userCode =
         extractJsonField(json, "user_code");
      String verUri =
         extractJsonField(json, "verification_uri");
      if (null == deviceCode || null == userCode
            || null == verUri) {
         throw new AIException(
            "Invalid device flow response: "
            + truncate(json, 200));
      }
      return new DeviceFlowInfo(
         deviceCode, userCode, verUri);
   }

   /**
    * Truncate a string to a maximum length.
    */
   private static String truncate(String s, int max) {
      if (null == s)
         return "";
      return s.length() <= max
         ? s : s.substring(0, max) + "...";
   }

   /**
    * Sleep for the given number of seconds.
    */
   private static void sleepSeconds(int secs)
         throws IOException {
      try {
         Thread.sleep(secs * 1000L);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IOException(
            "Interrupted while waiting", e);
      }
   }

   /**
    * Information from device flow initiation.
    *
    * @param deviceCode the device code for polling
    * @param userCode the code to display to the user
    * @param verificationUri URL where user authorizes
    */
   public record DeviceFlowInfo(
      String deviceCode,
      String userCode,
      String verificationUri
   ) { }
}
