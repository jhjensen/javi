package javi.ai.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javi.ai.AIException;
import javi.ai.OpenAIProvider;

import static history.Tools.trace;

/**
 * Central registry for AI tools.
 *
 * <p>Maintains the set of tools available to the AI model and handles
 * tool execution dispatching. Tool definitions are formatted for the
 * OpenAI function-calling API format used by both OpenAI and Copilot.</p>
 *
 * <h2>Tool Lifecycle</h2>
 * <ol>
 *   <li>Tools are registered at startup via {@link #register}</li>
 *   <li>{@link #getToolDefinitions} formats them for API requests</li>
 *   <li>When the API responds with a {@code tool_use} block,
 *       {@link #executeTool} dispatches to the right tool</li>
 *   <li>The result is fed back into the conversation</li>
 * </ol>
 *
 * <h2>Permission Tracking</h2>
 * <p>Tools with {@link PermissionLevel#CONFIRM_FIRST} permission
 * are tracked per-session. Once the user confirms, subsequent
 * invocations are auto-allowed until {@link #resetPermissions}.</p>
 *
 * @see AITool
 * @see PermissionLevel
 */
public final class AIToolRegistry {

   private static final Map<String, AITool> tools =
      new LinkedHashMap<>();

   private static final Set<String> confirmedTools =
      Collections.synchronizedSet(new HashSet<>());

   private AIToolRegistry() {
   }

   /**
    * Register a tool. Replaces any existing tool with the same name.
    *
    * @param tool the tool to register
    */
   public static void register(AITool tool) {
      tools.put(tool.name(), tool);
      trace("AI tool registered: " + tool.name());
   }

   /**
    * Unregister a tool by name.
    *
    * @param name the tool name to remove
    */
   public static void unregister(String name) {
      tools.remove(name);
      confirmedTools.remove(name);
   }

   /**
    * Get an unmodifiable view of all registered tools.
    *
    * @return map of tool name to tool instance
    */
   public static Map<String, AITool> getTools() {
      return Collections.unmodifiableMap(tools);
   }

   /**
    * Get tool definitions formatted for OpenAI function-calling API.
    *
    * <p>Returns a list of tool definition maps suitable for inclusion
    * in the {@code tools} array of an API request. Each entry follows
    * the format:
    * <pre>{@code
    * {
    *   "type": "function",
    *   "function": {
    *     "name": "file_read",
    *     "description": "Read contents of a file",
    *     "parameters": { ... JSON Schema ... }
    *   }
    * }
    * }</pre>
    *
    * @return list of tool definitions
    */
   public static List<Map<String, Object>> getToolDefinitions() {
      List<Map<String, Object>> defs = new ArrayList<>();
      for (AITool tool : tools.values()) {
         Map<String, Object> funcDef = new LinkedHashMap<>();
         funcDef.put("name", tool.name());
         funcDef.put("description", tool.description());
         funcDef.put("parameters", tool.inputSchema());

         Map<String, Object> toolDef = new LinkedHashMap<>();
         toolDef.put("type", "function");
         toolDef.put("function", funcDef);
         defs.add(toolDef);
      }
      return defs;
   }

   /**
    * Execute a tool by name with the given parameters.
    *
    * <p>Checks permissions before execution. For
    * {@link PermissionLevel#AUTO} tools, executes immediately.
    * For {@link PermissionLevel#CONFIRM_FIRST}, checks the
    * per-session confirmation cache.</p>
    *
    * @param name the tool name
    * @param params the input parameters
    * @return the tool result text
    * @throws AIException if the tool fails or is not found
    */
   public static String executeTool(String name,
         Map<String, String> params) throws AIException {
      AITool tool = tools.get(name);
      if (null == tool) {
         throw new AIException("Unknown tool: " + name);
      }

      if (!checkPermission(tool)) {
         return "Tool '" + name
            + "' requires user permission (not yet implemented)";
      }

      trace("AI tool execute: " + name + " params=" + params);
      String result = tool.execute(params);
      trace("AI tool result: " + name + " length="
         + (null != result ? result.length() : 0));
      return result;
   }

   /**
    * Check whether a tool has permission to execute.
    *
    * @param tool the tool to check
    * @return true if execution is allowed
    */
   private static boolean checkPermission(AITool tool) {
      switch (tool.permissionLevel()) {
         case AUTO:
            return true;
         case CONFIRM_FIRST:
            return confirmedTools.contains(tool.name());
         case CONFIRM_ALWAYS:
            return false;
         default:
            return false;
      }
   }

   /**
    * Grant permission for a CONFIRM_FIRST tool.
    *
    * @param toolName the tool to confirm
    */
   public static void confirmTool(String toolName) {
      confirmedTools.add(toolName);
   }

   /**
    * Reset all per-session permission confirmations.
    */
   public static void resetPermissions() {
      confirmedTools.clear();
   }

   /**
    * Check if any tools are registered.
    *
    * @return true if tools are available
    */
   public static boolean hasTools() {
      return !tools.isEmpty();
   }

   /**
    * Get human-readable summary of registered tools.
    *
    * @return multi-line string listing all tools
    */
   public static String getSummary() {
      StringBuilder sb = new StringBuilder();
      sb.append("Registered AI tools (")
         .append(tools.size()).append("):\n");
      for (AITool tool : tools.values()) {
         sb.append("  ").append(tool.name())
            .append(" [").append(tool.permissionLevel())
            .append("] — ").append(tool.description())
            .append('\n');
      }
      return sb.toString();
   }

   /**
    * Build the tools JSON array for inclusion in API requests.
    *
    * <p>Returns a JSON array string following the OpenAI
    * function-calling format. Returns null if no tools are
    * registered.</p>
    *
    * @return JSON tools array string, or null if empty
    */
   public static String getToolsJson() {
      if (tools.isEmpty()) {
         return null;
      }
      StringBuilder sb = new StringBuilder(512);
      sb.append('[');
      boolean first = true;
      for (AITool tool : tools.values()) {
         if (!first) {
            sb.append(',');
         }
         first = false;
         sb.append("{\"type\":\"function\",\"function\":{");
         sb.append("\"name\":\"")
            .append(OpenAIProvider.escapeJson(tool.name()))
            .append("\",");
         sb.append("\"description\":\"")
            .append(OpenAIProvider.escapeJson(
               tool.description()))
            .append("\",");
         sb.append("\"parameters\":")
            .append(tool.inputSchema());
         sb.append("}}");
      }
      sb.append(']');
      return sb.toString();
   }

   /**
    * Register all built-in tools.
    *
    * <p>Called during AI plugin initialization to register the
    * default set of read-only tools.</p>
    */
   public static void registerBuiltins() {
      register(new FileReadTool());
      register(new FileListTool());
      register(new GrepTool());
      register(new BufferInfoTool());
      register(new BufferReadTool());
      register(new BufferWriteTool());
   }
}
