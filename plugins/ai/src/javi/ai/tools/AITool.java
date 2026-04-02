package javi.ai.tools;

import java.util.Map;

import javi.ai.AIException;

/**
 * Interface for AI tools that the model can invoke during a conversation.
 *
 * <p>Each tool represents a capability the AI can use: reading files,
 * searching code, listing buffers, etc. Tools are registered in
 * {@link AIToolRegistry} and their definitions are sent to the API
 * as function-calling schemas.</p>
 *
 * <p>The AI model decides when to call tools based on the user's
 * request and the tool descriptions. Tool results are fed back
 * into the conversation so the AI can use them in its response.</p>
 *
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li>Keep {@link #execute} fast — it runs in the API call loop</li>
 *   <li>Return plain text results, not JSON</li>
 *   <li>Limit output size to avoid overwhelming the context window</li>
 *   <li>Throw {@link AIException} for recoverable errors</li>
 * </ul>
 *
 * @see AIToolRegistry
 * @see PermissionLevel
 */
public interface AITool {

   /**
    * The tool name used in API requests (e.g., "file_read").
    *
    * <p>Must be a valid function name: lowercase, underscores,
    * no spaces. Must be unique across all registered tools.</p>
    *
    * @return the tool identifier
    */
   String name();

   /**
    * Human-readable description of what this tool does.
    *
    * <p>Sent to the AI model to help it decide when to use this
    * tool. Should be concise but informative.</p>
    *
    * @return the tool description
    */
   String description();

   /**
    * JSON Schema string defining the tool's input parameters.
    *
    * <p>Must be a valid JSON object with "type": "object" and
    * "properties" defining each parameter. Used by the API for
    * input validation.</p>
    *
    * <p>Example:</p>
    * <pre>{@code
    * {
    *   "type": "object",
    *   "properties": {
    *     "path": {
    *       "type": "string",
    *       "description": "File path to read"
    *     }
    *   },
    *   "required": ["path"]
    * }
    * }</pre>
    *
    * @return JSON Schema string for input parameters
    */
   String inputSchema();

   /**
    * The permission level required to execute this tool.
    *
    * @return the permission level
    * @see PermissionLevel
    */
   PermissionLevel permissionLevel();

   /**
    * Execute the tool with the given parameters.
    *
    * @param params the input parameters parsed from the AI request
    * @return the tool result as plain text
    * @throws AIException if the tool encounters a recoverable error
    */
   String execute(Map<String, String> params) throws AIException;

   /**
    * Whether this tool is safe for concurrent execution.
    *
    * <p>Read-only tools are generally safe. Tools that modify editor
    * state should return false.</p>
    *
    * @return true if concurrent execution is safe
    */
   default boolean isConcurrencySafe() {
      return true;
   }
}
