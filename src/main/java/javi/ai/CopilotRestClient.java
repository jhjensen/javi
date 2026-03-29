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
    * Build streaming chat completion request JSON.
    */
   public static String buildStreamingChatJson(
         List<AIProvider.Message> messages,
         String model, int maxTokens) {
      StringBuilder sb = new StringBuilder(512);
      sb.append("{\"model\":\"")
         .append(OpenAIProvider.escapeJson(model))
         .append("\",\"stream\":true,\"messages\":[");
      for (int i = 0; i < messages.size(); i++) {
         if (i > 0)
            sb.append(',');
         AIProvider.Message msg = messages.get(i);
         sb.append("{\"role\":\"")
            .append(OpenAIProvider.escapeJson(
               msg.role()))
            .append("\",\"content\":\"")
            .append(OpenAIProvider.escapeJson(
               msg.content()))
            .append("\"}");
      }
      sb.append(']');
      if (maxTokens > 0) {
         sb.append(",\"max_tokens\":")
            .append(maxTokens);
      }
      sb.append('}');
      return sb.toString();
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

      Path appsJson = Path.of(
         System.getProperty("user.home"),
         APPS_JSON_PATH);
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
      Path appsJson = Path.of(
         System.getProperty("user.home"),
         APPS_JSON_PATH);
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
      StringBuilder sb = new StringBuilder(512);
      sb.append("{\"model\":\"")
         .append(OpenAIProvider.escapeJson(model))
         .append("\",\"messages\":[");
      for (int i = 0; i < messages.size(); i++) {
         if (i > 0)
            sb.append(',');
         AIProvider.Message msg = messages.get(i);
         sb.append("{\"role\":\"")
            .append(OpenAIProvider.escapeJson(msg.role()))
            .append("\",\"content\":\"")
            .append(OpenAIProvider.escapeJson(
               msg.content()))
            .append("\"}");
      }
      sb.append(']');
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
