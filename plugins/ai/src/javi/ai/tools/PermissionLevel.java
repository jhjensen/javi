package javi.ai.tools;

/**
 * Permission levels for AI tool execution.
 *
 * <p>Modeled after the 4-level permission system in Claude Code.
 * Controls whether tool execution requires user confirmation.</p>
 *
 * @see AITool
 */
public enum PermissionLevel {
   /** Automatically allowed — no confirmation needed (read-only tools). */
   AUTO,
   /** Confirm on first use per session, then auto-allow. */
   CONFIRM_FIRST,
   /** Confirm every invocation (write, delete operations). */
   CONFIRM_ALWAYS
}
