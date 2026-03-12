package javi;

import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import history.Tools;
import static history.Tools.trace;

/**
 * Represents a single shell session within the ShellManager.
 *
 * <p>Each ShellSession wraps a VT100 terminal emulator connected to a shell
 * process. Sessions can be local (bash, sh, etc.) or remote (via SSH).</p>
 *
 * <h2>Session Lifecycle</h2>
 * <ol>
 *   <li>Created via {@link ShellManager#newShell()}</li>
 *   <li>Process started automatically on creation</li>
 *   <li>I/O handled by underlying Vt100 instance</li>
 *   <li>Closed explicitly or when editor exits</li>
 * </ol>
 *
 * <h2>Properties</h2>
 * <ul>
 *   <li><b>id</b> - Unique numeric identifier</li>
 *   <li><b>name</b> - Display name (hostname or "local")</li>
 *   <li><b>buffer</b> - TextEdit buffer containing terminal output</li>
 *   <li><b>alive</b> - Whether the shell process is still running</li>
 * </ul>
 *
 * @see ShellManager
 * @see Vt100
 */
public final class ShellSession {

   /** Unique session identifier. */
   private final int id;

   /** Display name for this session. */
   private String name;

   /** Hostname for SSH sessions, or null for local. */
   private final String host;

   /** The VT100 terminal buffer. */
   private final Vt100 vt100;

   /** The underlying process. */
   private final Process process;

   /** Creation timestamp. */
   private final long createdAt;

   /** Charset used for this session. */
   private final Charset charset;

   /** Per-session environment variables. */
   private final Map<String, String> envVars = new LinkedHashMap<>();

   /**
    * Creates a new shell session.
    *
    * @param id the unique session ID
    * @param host the SSH hostname, or null for local shell
    * @throws IOException if the shell process cannot be started
    */
   ShellSession(int id, String host) throws IOException {
      this(id, host, null);
   }

   /**
    * Creates a new shell session with an optional custom name.
    *
    * @param id the unique session ID
    * @param host the SSH hostname, or null for local shell
    * @param customName user-specified name, or null for default
    * @throws IOException if the shell process cannot be started
    */
   ShellSession(int id, String host, String customName) throws IOException {
      this.id = id;
      this.host = host;
      if (customName != null && !customName.isEmpty())
         this.name = customName;
      else
         this.name = (null == host) ? "local" : host;
      this.createdAt = System.currentTimeMillis();
      this.charset = CharsetDetector.detectTerminalCharset();

      // Build command array
      String[] cmd = buildCommand(host);

      // Start process with terminal environment variables
      int cols = MiscCommands.getWidth();
      int rows = MiscCommands.getHeight();
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      java.util.Map<String, String> env = pb.environment();
      env.put("TERM", "xterm");
      env.put("COLUMNS", Integer.toString(cols));
      env.put("LINES", Integer.toString(rows));
      env.putAll(envVars);
      this.process = pb.start();
      trace("ShellSession " + id + ": started process with charset "
         + charset.name() + " TERM=xterm COLUMNS=" + cols + " LINES=" + rows);

      // Send stty to set PTY dimensions
      try {
         OutputStreamWriter sttyWriter = new OutputStreamWriter(
            process.getOutputStream(), charset);
         sttyWriter.write("stty rows " + rows + " cols " + cols + "\n");
         sttyWriter.flush();
      } catch (IOException e) {
         trace("ShellSession " + id + ": failed to send stty: " + e);
      }

      // Create VT100 terminal
      this.vt100 = new Vt100(
         process.getOutputStream(),
         new BufferedInputStream(process.getInputStream()),
         new StringIoc("shell-" + id + (null != host ? " (" + host + ")" : ""),
            null),
         charset
      );
   }

   /**
    * Builds the command array for starting the shell.
    *
    * @param host the SSH hostname, or null for local
    * @return the command array
    */
   private static String[] buildCommand(String host) {
      if (null == host) {
         // Local shell - try to detect user's preferred shell
         String shell = System.getenv("SHELL");
         if (null == shell || shell.isEmpty()) {
            shell = "bash";  // fallback
         }
         // Use script to create a new PTY session, detaching the shell
         // from javi's controlling terminal to prevent terminal corruption
         String os = System.getProperty("os.name", "").toLowerCase();
         if (os.contains("mac") || os.contains("darwin")) {
            return new String[]{"script", "-q", "/dev/null", shell, "-i"};
         } else {
            // Linux: script uses -c for command
            return new String[]{"script", "-q", "-c",
               shell + " -i", "/dev/null"};
         }
      } else {
         // SSH connection
         return new String[]{"ssh", "-t", "-t", host};
      }
   }

   /**
    * Gets the unique session ID.
    *
    * @return the session ID
    */
   public int getId() {
      return id;
   }

   /**
    * Gets the display name for this session.
    *
    * @return the session name
    */
   public String getName() {
      return name;
   }

   /**
    * Sets a custom display name for this session.
    *
    * @param newName the new name
    */
   public void setName(String newName) {
      if (newName != null && !newName.isEmpty())
         this.name = newName;
   }

   /**
    * Sets an environment variable for this session and exports it
    * to the running shell.
    *
    * @param key the variable name
    * @param value the variable value
    */
   public void setEnvVar(String key, String value) {
      envVars.put(key, value);
      // Export to the running shell process
      if (null != process && process.isAlive()) {
         try {
            OutputStreamWriter w = new OutputStreamWriter(
               process.getOutputStream(), charset);
            w.write("export " + key + "=" + shellQuote(value) + "\n");
            w.flush();
         } catch (IOException e) {
            trace("ShellSession " + id + ": failed to export env var: " + e);
         }
      }
   }

   /**
    * Gets an unmodifiable view of the session environment variables.
    *
    * @return the environment variables map
    */
   public Map<String, String> getEnvVars() {
      return Collections.unmodifiableMap(envVars);
   }

   /**
    * Shell-quotes a value for safe use in an export command.
    */
   private static String shellQuote(String val) {
      // Use single quotes; escape embedded single quotes
      return "'" + val.replace("'", "'\\''") + "'";
   }

   /**
    * Gets the hostname for SSH sessions.
    *
    * @return the hostname, or null for local shells
    */
   public String getHost() {
      return host;
   }

   /**
    * Checks if this is a local shell (not SSH).
    *
    * @return true if local, false if remote
    */
   public boolean isLocal() {
      return null == host;
   }

   /**
    * Gets the TextEdit buffer containing terminal output.
    *
    * @return the terminal buffer
    */
   public TextEdit<String> getBuffer() {
      return vt100;
   }

   /**
    * Gets the underlying VT100 terminal emulator.
    *
    * @return the Vt100 instance
    */
   Vt100 getVt100() {
      return vt100;
   }

   /**
    * Checks if the shell process is still running.
    *
    * @return true if alive, false if terminated
    */
   public boolean isAlive() {
      return null != process && process.isAlive();
   }

   /**
    * Gets the exit code of the shell process.
    *
    * @return the exit code, or -1 if still running
    */
   public int getExitCode() {
      if (null == process || process.isAlive()) {
         return -1;
      }
      return process.exitValue();
   }

   /**
    * Gets the charset used for this session.
    *
    * @return the Charset
    */
   public Charset getCharset() {
      return charset;
   }

   /**
    * Gets the session creation timestamp.
    *
    * @return milliseconds since epoch when created
    */
   public long getCreatedAt() {
      return createdAt;
   }

   /**
    * Gets the session uptime in seconds.
    *
    * @return uptime in seconds
    */
   public long getUptimeSeconds() {
      return (System.currentTimeMillis() - createdAt) / 1000;
   }

   /**
    * Closes this shell session and terminates the process.
    *
    * @throws IOException if an I/O error occurs during close
    */
   void close() throws IOException {
      trace("ShellSession " + id + ": closing");

      // Dispose the VT100 (stops parser thread)
      if (null != vt100) {
         vt100.disposeFvc();
      }

      // Destroy the process
      if (null != process) {
         process.destroy();

         // Wait briefly for graceful termination
         try {
            if (!process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
               // Force kill if still running
               process.destroyForcibly();
            }
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
         }
      }
   }

   /**
    * Destroys only the shell process without disposing the VT100 buffer.
    *
    * <p>Used when the buffer has already been disposed (e.g., via ZZ) and
    * we only need to terminate the underlying process.</p>
    */
   void destroyProcess() {
      trace("ShellSession " + id + ": destroying process");
      if (null != process) {
         process.destroy();
         try {
            if (!process.waitFor(500,
                  java.util.concurrent.TimeUnit.MILLISECONDS)) {
               process.destroyForcibly();
            }
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
         }
      }
   }

   /**
    * Notifies this session that the display has been resized.
    *
    * <p>Propagates the new dimensions to the VT100 terminal, which
    * updates the PTY via stty.</p>
    *
    * @param rows the new number of rows
    * @param cols the new number of columns
    */
   void notifyResize(int rows, int cols) {
      if (isAlive())
         vt100.notifyResize(rows, cols);
   }

   /**
    * Sends a signal to the shell process (Unix only).
    *
    * @param signal the signal name (e.g., "INT", "TERM", "HUP")
    */
   public void sendSignal(String signal) {
      // This requires Unix-specific handling
      // For now, we only support basic kill
      if ("INT".equals(signal) || "TERM".equals(signal)) {
         trace("ShellSession " + id + ": sending " + signal);
         // Process.destroy() sends SIGTERM on Unix
         if (null != process && process.isAlive()) {
            process.destroy();
         }
      }
   }

   @Override
   public String toString() {
      return String.format("ShellSession[id=%d, name=%s, alive=%s]",
         id, name, isAlive());
   }
}
