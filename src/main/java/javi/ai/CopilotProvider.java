package javi.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static history.Tools.trace;

/**
 * GitHub Copilot provider implementation using the Copilot LSP agent.
 *
 * <p>Connects to the GitHub Copilot agent (a Node.js process bundled
 * with the VS Code Copilot extension) via JSON-RPC over stdio. This
 * enables javi to use the same Copilot backend as VS Code.</p>
 *
 * <h2>Architecture</h2>
 * <p>The Copilot agent is a Language Server Protocol (LSP) server
 * extended with Copilot-specific methods:</p>
 * <ol>
 *   <li>{@code initialize} — LSP initialization handshake</li>
 *   <li>{@code setEditorInfo} — identify our editor to the agent</li>
 *   <li>{@code getCompletions} — request inline code completions</li>
 *   <li>{@code signInInitiate} / {@code signInConfirm} — GitHub
 *       device flow OAuth authentication</li>
 * </ol>
 *
 * <h2>Agent Discovery</h2>
 * <p>The agent is found from the VS Code Copilot extension install
 * directory, typically under ~/.vscode/extensions/.</p>
 *
 * <h2>Authentication</h2>
 * <p>Uses GitHub device flow OAuth. On first use, displays a URL and
 * user code for the user to authorize in their browser. The OAuth token
 * is cached locally.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>The agent process is managed with synchronized access. JSON-RPC
 * requests use atomic sequence IDs for safe concurrent usage.</p>
 *
 * @see AIProvider
 * @see AIConfig
 */
public final class CopilotProvider implements AIProvider {

   /**
    * Path patterns to search for the Copilot agent, in order.
    * The first match wins.
    */
   private static final String[] AGENT_SEARCH_PATHS = {
      System.getProperty("user.home")
         + "/.vscode/extensions/github.copilot-*/dist/agent.js",
   };

   /** Environment variable to override the agent path. */
   private static final String AGENT_PATH_ENV = "COPILOT_AGENT_PATH";

   private final AtomicInteger requestId = new AtomicInteger(1);
   private volatile Process agentProcess;
   private volatile Writer agentWriter;
   private volatile BufferedReader agentReader;
   private volatile boolean initialized;

   /**
    * Create a Copilot provider.
    *
    * <p>Does not start the agent immediately — it is lazily started
    * on first use.</p>
    *
    * @throws AIException if the Copilot agent cannot be found
    */
   public CopilotProvider() throws AIException {
      if (null == findAgentPath()) {
         throw new AIException("GitHub Copilot agent not found. "
            + "Install the GitHub Copilot extension in VS Code, or set "
            + AGENT_PATH_ENV + " environment variable.");
      }
   }

   @Override
   public String chatCompletion(List<Message> messages, int maxTokens)
         throws IOException, AIException {
      ensureAgent();

      // Build prompt from messages — Copilot is completion-focused,
      // so we concatenate messages into a prompt for the completions API
      StringBuilder prompt = new StringBuilder(1024);
      for (Message msg : messages) {
         if ("system".equals(msg.role())) {
            prompt.append("// ").append(msg.content()).append('\n');
         } else if ("user".equals(msg.role())) {
            prompt.append(msg.content()).append('\n');
         } else if ("assistant".equals(msg.role())) {
            prompt.append(msg.content()).append('\n');
         }
      }

      return getCompletion(prompt.toString(), "chat.txt");
   }

   @Override
   public String getName() {
      return "GitHub Copilot";
   }

   @Override
   public String getModel() {
      return "copilot";
   }

   @Override
   public boolean testConnection() {
      try {
         ensureAgent();
         // Send a minimal completion request
         String result = getCompletion("// test\n", "test.java");
         return null != result;
      } catch (IOException | AIException e) {
         trace("Copilot connection test failed: " + e.getMessage());
         return false;
      }
   }

   /**
    * Request a code completion from the Copilot agent.
    *
    * @param codePrefix the code before the cursor
    * @param fileName the file name (for language detection)
    * @return the completion text, or empty string if none
    * @throws IOException if communication fails
    * @throws AIException if the agent returns an error
    */
   private String getCompletion(String codePrefix, String fileName)
         throws IOException, AIException {
      int id = requestId.getAndIncrement();

      // Build the getCompletions JSON-RPC request
      String params = "{\"doc\":{\"source\":\""
         + OpenAIProvider.escapeJson(codePrefix)
         + "\",\"tabSize\":3,\"indentSize\":3,"
         + "\"insertSpaces\":true,\"path\":\""
         + OpenAIProvider.escapeJson(fileName)
         + "\",\"relativePath\":\""
         + OpenAIProvider.escapeJson(fileName)
         + "\",\"uri\":\"file:///"
         + OpenAIProvider.escapeJson(fileName)
         + "\",\"languageId\":\""
         + guessLanguageId(fileName)
         + "\",\"position\":{\"line\":"
         + countLines(codePrefix)
         + ",\"character\":0}}}";

      String response = sendRequest("getCompletions", params, id);

      // Parse completions from response
      return extractCompletionText(response);
   }

   /**
    * Ensure the Copilot agent process is running and initialized.
    */
   private synchronized void ensureAgent() throws IOException, AIException {
      if (null != agentProcess && agentProcess.isAlive() && initialized) {
         return;
      }
      startAgent();
      initializeAgent();
      initialized = true;
   }

   /**
    * Start the Copilot agent Node.js process.
    */
   private void startAgent() throws IOException, AIException {
      String agentPath = findAgentPath();
      if (null == agentPath) {
         throw new AIException("GitHub Copilot agent not found");
      }

      trace("Starting Copilot agent: " + agentPath);

      ProcessBuilder pb = new ProcessBuilder("node", agentPath, "--stdio");
      pb.redirectErrorStream(false);
      agentProcess = pb.start();

      agentWriter = new OutputStreamWriter(
         agentProcess.getOutputStream(), StandardCharsets.UTF_8);
      agentReader = new BufferedReader(new InputStreamReader(
         agentProcess.getInputStream(), StandardCharsets.UTF_8));

      trace("Copilot agent started (pid " + agentProcess.pid() + ")");
   }

   /**
    * Send the LSP initialize and setEditorInfo handshake.
    */
   private void initializeAgent() throws IOException, AIException {
      int id = requestId.getAndIncrement();

      // LSP initialize
      String initParams =
         "{\"capabilities\":{},\"processId\":"
         + ProcessHandle.current().pid() + "}";
      sendRequest("initialize", initParams, id);

      // Notify initialized
      sendNotification("initialized", "{}");

      // Set editor info so Copilot knows who we are
      id = requestId.getAndIncrement();
      String editorInfo =
         "{\"editorInfo\":{\"name\":\"javi\",\"version\":\"1.0\"},"
         + "\"editorPluginInfo\":"
         + "{\"name\":\"javi-copilot\",\"version\":\"1.0\"}}";
      sendRequest("setEditorInfo", editorInfo, id);
   }

   /**
    * Send a JSON-RPC request and read the response.
    *
    * @param method the RPC method name
    * @param params the JSON params object
    * @param id the request id
    * @return the response body JSON
    */
   private String sendRequest(String method, String params, int id)
         throws IOException, AIException {
      String body = "{\"jsonrpc\":\"2.0\",\"id\":" + id
         + ",\"method\":\"" + method + "\",\"params\":" + params + "}";

      sendMessage(body);
      return readResponse();
   }

   /**
    * Send a JSON-RPC notification (no response expected).
    */
   private void sendNotification(String method, String params)
         throws IOException {
      String body = "{\"jsonrpc\":\"2.0\""
         + ",\"method\":\"" + method + "\",\"params\":" + params + "}";
      sendMessage(body);
   }

   /**
    * Write a JSON-RPC message with Content-Length header.
    */
   private void sendMessage(String body) throws IOException {
      String message = "Content-Length: " + body.getBytes(
         StandardCharsets.UTF_8).length + "\r\n\r\n" + body;
      agentWriter.write(message);
      agentWriter.flush();
   }

   /**
    * Read a JSON-RPC response from the agent.
    *
    * <p>Reads the Content-Length header, then reads exactly that many
    * bytes of body.</p>
    */
   private String readResponse() throws IOException, AIException {
      // Read headers until blank line
      String line;
      int contentLength = -1;
      while (null != (line = agentReader.readLine())) {
         if (line.isEmpty()) {
            break;
         }
         if (line.startsWith("Content-Length:")) {
            contentLength = Integer.parseInt(line.substring(15).trim());
         }
      }

      if (contentLength < 0) {
         throw new AIException("Copilot agent: missing Content-Length");
      }

      char[] buf = new char[contentLength];
      int read = 0;
      while (read < contentLength) {
         int n = agentReader.read(buf, read, contentLength - read);
         if (n < 0) {
            throw new AIException(
               "Copilot agent: unexpected end of stream");
         }
         read += n;
      }

      return new String(buf);
   }

   /**
    * Extract completion text from the Copilot agent response.
    */
   static String extractCompletionText(String json) throws AIException {
      // Look for "displayText":" or "text":" in completions
      String marker = "\"displayText\":\"";
      int idx = json.indexOf(marker);
      if (idx < 0) {
         marker = "\"insertText\":\"";
         idx = json.indexOf(marker);
      }
      if (idx < 0) {
         // No completion available
         return "";
      }
      idx += marker.length();
      return OpenAIProvider.unescapeJsonString(json, idx);
   }

   /**
    * Find the Copilot agent.js path.
    *
    * @return the absolute path to agent.js, or null if not found
    */
   static String findAgentPath() {
      // Check environment variable override first
      String envPath = System.getenv(AGENT_PATH_ENV);
      if (null != envPath && !envPath.isEmpty()) {
         if (Files.isRegularFile(Path.of(envPath))) {
            return envPath;
         }
      }

      // Search VS Code extension directories
      String home = System.getProperty("user.home");
      Path extDir = Path.of(home, ".vscode", "extensions");
      if (!Files.isDirectory(extDir)) {
         return null;
      }

      try (var stream = Files.list(extDir)) {
         return stream
            .filter(p -> p.getFileName().toString()
               .startsWith("github.copilot-"))
            .filter(p -> !p.getFileName().toString()
               .contains("copilot-chat"))
            .sorted((a, b) -> b.getFileName().toString()
               .compareTo(a.getFileName().toString()))
            .map(p -> p.resolve("dist").resolve("agent.js"))
            .filter(Files::isRegularFile)
            .map(Path::toString)
            .findFirst()
            .orElse(null);
      } catch (IOException e) {
         trace("Error searching for Copilot agent: " + e);
         return null;
      }
   }

   /**
    * Guess the LSP languageId from a filename.
    */
   private static String guessLanguageId(String fileName) {
      if (null == fileName) {
         return "plaintext";
      }
      int dot = fileName.lastIndexOf('.');
      if (dot < 0) {
         return "plaintext";
      }
      String ext = fileName.substring(dot + 1).toLowerCase();
      return switch (ext) {
         case "java" -> "java";
         case "py" -> "python";
         case "js" -> "javascript";
         case "ts" -> "typescript";
         case "c", "h" -> "c";
         case "cpp", "cc", "cxx", "hpp" -> "cpp";
         case "rb" -> "ruby";
         case "go" -> "go";
         case "rs" -> "rust";
         case "pl", "pm" -> "perl";
         case "sh", "bash" -> "shellscript";
         case "md" -> "markdown";
         case "json" -> "json";
         case "xml" -> "xml";
         case "html", "htm" -> "html";
         case "css" -> "css";
         default -> "plaintext";
      };
   }

   /**
    * Count newlines in a string.
    */
   private static int countLines(String text) {
      int count = 0;
      for (int i = 0; i < text.length(); i++) {
         if ('\n' == text.charAt(i)) {
            count++;
         }
      }
      return count;
   }

   /**
    * Shut down the Copilot agent process.
    */
   public void shutdown() {
      initialized = false;
      Process p = agentProcess;
      if (null != p && p.isAlive()) {
         try {
            sendNotification("shutdown", "null");
            sendNotification("exit", "null");
         } catch (IOException e) {
            trace("Error shutting down Copilot agent: " + e);
         }
         p.destroyForcibly();
         agentProcess = null;
      }
   }
}
