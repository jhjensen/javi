package javi;

import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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

   /** Minimum interval between label updates in milliseconds. */
   static final long LABEL_UPDATE_INTERVAL_MS = 3000;

   /** Timestamp of last label update (for cache). */
   private long lastLabelUpdate;

   /**
    * Creates a new shell session.
    *
    * @param id the unique session ID
    * @param host the SSH hostname, or null for local shell
    * @throws IOException if the shell process cannot be started
    */
   ShellSession(int sessionId, String newHost) throws IOException {
      this(sessionId, newHost, null, null);
   }

   /**
    * Creates a new shell session with an optional custom name.
    *
    * @param id the unique session ID
    * @param host the SSH hostname, or null for local shell
    * @param customName user-specified name, or null for default
    * @throws IOException if the shell process cannot be started
    */
   ShellSession(int sessionId, String newHost, String customName) throws IOException {
      this(sessionId, newHost, customName, null);
   }

   /**
    * Creates a new shell session with optional custom name and working
    * directory.
    *
    * @param id the unique session ID
    * @param host the SSH hostname, or null for local shell
    * @param customName user-specified name, or null for default
    * @param workDir initial working directory, or null for default
    * @throws IOException if the shell process cannot be started
    */
   ShellSession(int sessionId, String newHost, String customName, java.io.File workDir)
         throws IOException {
      this.id = sessionId;
      this.host = newHost;
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
      env.put("TERM", "xterm-256color");
      env.put("COLUMNS", Integer.toString(cols));
      env.put("LINES", Integer.toString(rows));
      env.putAll(envVars);
      if (null != workDir && workDir.isDirectory()) {
         pb.directory(workDir);
      }
      this.process = pb.start();
      trace("ShellSession " + sessionId + ": started process with charset "
         + charset.name() + " TERM=xterm-256color COLUMNS=" + cols + " LINES=" + rows);

      // Create VT100 terminal
      this.vt100 = new Vt100(
         process.getOutputStream(),
         new BufferedInputStream(process.getInputStream()),
         new StringIoc("shell-" + id + (null != host ? " (" + host + ")" : ""),
            null),
         charset
      );

      // Set initial PTY size in a daemon thread — the PTY created by
      // script(1) starts with 0x0 dimensions because javi has no
      // controlling terminal to inherit from.  We need to wait briefly
      // for the child shell process to appear before we can discover
      // its TTY device and call stty.
      final int initRows = rows;
      final int initCols = cols;
      Thread ptyInit = new Thread(() -> {
         initializePtySize(initRows, initCols);
      }, "pty-init-" + sessionId);
      ptyInit.setDaemon(true);
      ptyInit.start();
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
    * <p>Updates the terminal's internal state first via the event
    * queue, then notifies child processes of the new dimensions.
    * This ordering is critical: the terminal must process the
    * resize (updating rows, scroll region, cursor bounds) before
    * child apps receive SIGWINCH and start redrawing for the new
    * size. Otherwise, the child's new-size escape sequences are
    * processed against stale state, causing garbled output.</p>
    *
    * @param rows the new number of rows
    * @param cols the new number of columns
    */
   void notifyResize(int rows, int cols) {
      if (isAlive()) {
         vt100.notifyResize(rows, cols, () -> {
            // Run PTY update in a daemon thread to avoid
            // blocking the event-processing thread with
            // process spawns (findChildTty, stty, kill).
            Thread t = new Thread(
               () -> updatePtySize(rows, cols),
               "pty-resize-" + id);
            t.setDaemon(true);
            t.start();
         });
      }
   }

   /**
    * Updates the PTY window size by finding the child shell's TTY
    * device and calling stty on it directly. This ensures the PTY
    * reports the correct dimensions even when a fullscreen app
    * (htop, vim) is running.
    */
   /** Returns the stty flag for specifying a device: -f on macOS, -F on Linux. */
   private static String sttyDeviceFlag() {
      String os = System.getProperty("os.name", "").toLowerCase();
      return (os.contains("mac") || os.contains("darwin"))
         ? "-f" : "-F";
   }

   private void updatePtySize(int rows, int cols) {
      if (null == process || !process.isAlive())
         return;
      try {
         // On Linux, use /proc/<pid>/fd/0 to access the child's
         // actual PTY fd.  This bypasses devpts namespace issues
         // where "ps -o tty=" returns "pts/0" but the parent's
         // /dev/pts/0 is a different device than the child's.
         // TIOCSWINSZ via stty on the correct fd also triggers
         // SIGWINCH in the kernel, so sendSigwinch is a fallback.
         if (tryProcFdResize(rows, cols)) {
            sendSigwinch();
            return;
         }
         // Fallback: find TTY device name via ps and use stty.
         // This is the primary path on macOS where /proc doesn't
         // exist and devpts namespaces are not an issue.
         String tty = findChildTty();
         if (null == tty) {
            trace("ShellSession " + id
               + ": updatePtySize: no TTY found for process tree"
               + " — sending SIGWINCH anyway");
            sendSigwinch();
            return;
         }
         String dev = tty.startsWith("/dev/") ? tty : "/dev/" + tty;
         String flag = sttyDeviceFlag();
         Process stty = new ProcessBuilder("stty", flag, dev,
            "rows", Integer.toString(rows),
            "cols", Integer.toString(cols))
            .redirectErrorStream(true).start();
         String sttyOut = new String(
            stty.getInputStream().readAllBytes()).trim();
         int rc = stty.waitFor();
         if (rc != 0) {
            trace("ShellSession " + id
               + ": stty failed (rc=" + rc + "): " + sttyOut);
         } else {
            trace("ShellSession " + id
               + ": stty " + flag + " " + dev + " rows "
               + rows + " cols " + cols);
         }
         sendSigwinch();
      } catch (Exception e) {
         trace("ShellSession " + id
            + ": updatePtySize failed: " + e);
         // Even if something failed, try to send SIGWINCH so
         // child apps at least know a resize occurred.
         try {
            sendSigwinch();
         } catch (Exception e2) {
            trace("ShellSession " + id
               + ": sendSigwinch fallback failed: " + e2);
         }
      }
   }

   /**
    * Tries to resize the child's PTY via /proc/&lt;pid&gt;/fd/0.
    *
    * <p>On Linux, {@code /proc/<pid>/fd/0} is a symlink to the
    * process's actual stdin device (the PTY slave). Using this path
    * avoids issues where {@code ps -o tty=} returns a device name
    * that doesn't map to the same physical device in the parent's
    * devpts namespace.</p>
    *
    * @return true if the resize succeeded
    */
   private boolean tryProcFdResize(int rows, int cols) {
      try {
         for (ProcessHandle child : (Iterable<ProcessHandle>)
               process.toHandle().descendants()::iterator) {
            String procFd = "/proc/" + child.pid() + "/fd/0";
            java.io.File f = new java.io.File(procFd);
            if (!f.exists())
               continue;
            Process stty = new ProcessBuilder("stty", "-F", procFd,
               "rows", Integer.toString(rows),
               "cols", Integer.toString(cols))
               .redirectErrorStream(true).start();
            String out = new String(
               stty.getInputStream().readAllBytes()).trim();
            int rc = stty.waitFor();
            if (rc == 0) {
               trace("ShellSession " + id
                  + ": stty via " + procFd
                  + " rows " + rows + " cols " + cols);
               return true;
            }
            trace("ShellSession " + id
               + ": stty " + procFd + " failed (rc="
               + rc + "): " + out);
         }
      } catch (Exception e) {
         trace("ShellSession " + id
            + ": tryProcFdResize: " + e);
      }
      return false;
   }

   /**
    * Finds the TTY device for a descendant shell process.
    *
    * <p>Scans all descendant processes looking for one with a real
    * TTY device (not "?"). Falls back to the main process PID if
    * no descendants exist.</p>
    *
    * @return the TTY device name (e.g., "ttys007"), or null if none
    */
   private String findChildTty() throws Exception {
      // Try each descendant until we find one with a real TTY
      for (ProcessHandle child : (Iterable<ProcessHandle>)
            process.toHandle().descendants()::iterator) {
         String tty = getTtyForPid(child.pid());
         if (null != tty)
            return tty;
      }
      // Fallback: check the main process itself
      return getTtyForPid(process.pid());
   }

   /**
    * Gets the TTY device name for a given PID via ps.
    *
    * @param pid the process ID to query
    * @return the TTY name, or null if the process has no TTY
    */
   private String getTtyForPid(long pid) throws Exception {
      Process ps = new ProcessBuilder("ps", "-o", "tty=",
         "-p", Long.toString(pid))
         .redirectErrorStream(true).start();
      String tty = new String(
         ps.getInputStream().readAllBytes()).trim();
      ps.waitFor();
      if (!tty.isEmpty() && !"?".equals(tty))
         return tty;
      return null;
   }

   /**
    * Sets the initial PTY size after process startup.
    *
    * <p>Retries up to 10 times with 100ms delays to allow the child
    * shell process to start and acquire a TTY device. Called from a
    * daemon thread started in the constructor.</p>
    *
    * @param rows initial row count
    * @param cols initial column count
    */
   private void initializePtySize(int rows, int cols) {
      try {
         for (int attempt = 0; attempt < 10; attempt++) {
            Thread.sleep(100);
            if (null == process || !process.isAlive())
               return;
            // Try /proc/<pid>/fd/0 first (Linux, namespace-safe)
            if (tryProcFdResize(rows, cols)) {
               trace("ShellSession " + id
                  + ": initial PTY size via proc fd"
                  + " rows " + rows + " cols " + cols);
               sendSigwinch();
               return;
            }
            // Fallback: stty with device name from ps
            String tty = findChildTty();
            if (null != tty) {
               String dev = tty.startsWith("/dev/")
                  ? tty : "/dev/" + tty;
               String flag = sttyDeviceFlag();
               Process stty = new ProcessBuilder("stty", flag, dev,
                  "rows", Integer.toString(rows),
                  "cols", Integer.toString(cols)).start();
               stty.waitFor();
               trace("ShellSession " + id
                  + ": initial stty " + flag + " " + dev
                  + " rows " + rows + " cols " + cols);
               sendSigwinch();
               return;
            }
         }
         trace("ShellSession " + id
            + ": initializePtySize: gave up after 10 attempts");
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      } catch (Exception e) {
         trace("ShellSession " + id
            + ": initializePtySize failed: " + e);
      }
   }

   /**
    * Sends SIGWINCH to the shell process and all of its descendants.
    * This notifies running terminal applications that the window
    * size has changed so they can re-query terminal dimensions.
    *
    * <p>Sends to each descendant individually, then to the direct
    * process. Also looks up the foreground process group of each
    * descendant's TTY and signals that group, ensuring foreground
    * apps (like htop) receive the signal even when they are not
    * direct descendants visible via ProcessHandle.</p>
    */
   private void sendSigwinch() {
      if (null == process || !process.isAlive())
         return;
      try {
         long pid = process.pid();
         java.util.Set<Long> sent = new java.util.HashSet<>();
         java.util.Set<Long> signaledGroups = new java.util.HashSet<>();
         process.toHandle().descendants().forEach(child -> {
            long cpid = child.pid();
            sent.add(cpid);
            try {
               new ProcessBuilder("kill", "-WINCH",
                  Long.toString(cpid)).start().waitFor();
            } catch (Exception e) {
               trace("ShellSession " + id
                  + ": SIGWINCH to " + cpid + " failed");
            }
            // Signal the process group via negative PID.
            // Use signal number to avoid option parsing issues.
            try {
               long pgid = getProcessGroup(cpid);
               if (pgid > 0 && signaledGroups.add(pgid)) {
                  new ProcessBuilder("kill", "-28",
                     Long.toString(-pgid)).start().waitFor();
               }
            } catch (Exception e) {
               // Ignore — not every PID has an accessible PGID
            }
         });
         // Also send to the direct process (script)
         if (!sent.contains(pid)) {
            try {
               new ProcessBuilder("kill", "-WINCH",
                  Long.toString(pid)).start().waitFor();
            } catch (Exception e) {
               trace("ShellSession " + id
                  + ": SIGWINCH to " + pid + " failed");
            }
         }
         trace("ShellSession " + id
            + ": sent SIGWINCH to " + (sent.size() + 1)
            + " processes");
      } catch (Exception e) {
         trace("ShellSession " + id + ": sendSigwinch failed: " + e);
      }
   }

   /**
    * Returns the process group ID for a given PID via ps.
    *
    * @param pid the process ID to query
    * @return the PGID, or -1 if it could not be determined
    */
   private long getProcessGroup(long pid) {
      try {
         Process ps = new ProcessBuilder("ps", "-o", "pgid=",
            "-p", Long.toString(pid))
            .redirectErrorStream(true).start();
         String pgid = new String(
            ps.getInputStream().readAllBytes()).trim();
         ps.waitFor();
         if (!pgid.isEmpty())
            return Long.parseLong(pgid);
      } catch (Exception e) {
         // Ignore
      }
      return -1;
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

   /**
    * Updates the session name to reflect the foreground process.
    *
    * <p>Walks the process tree from the shell's direct child down to
    * the leaf process, so that when the user runs e.g. {@code ssh rdesk}
    * inside the shell, the label shows "ssh" rather than "script" or
    * "bash".</p>
    *
    * <p>Uses the {@link ProcessHandle} API to walk the process tree,
    * so no external processes are spawned.  Results are cached for
    * {@link #LABEL_UPDATE_INTERVAL_MS} milliseconds.  Calling this
    * method from the status-bar repaint path is therefore safe.</p>
    */
   void updateLabel() {
      if (null == process || !process.isAlive())
         return;
      long now = System.currentTimeMillis();
      if (now - lastLabelUpdate < LABEL_UPDATE_INTERVAL_MS)
         return;
      lastLabelUpdate = now;
      String base = (null == host) ? "local" : host;
      String cmd = null;
      try {
         cmd = getLeafProcessName(process.pid());
      } catch (Exception e) {
         trace("ShellSession " + id + ": updateLabel failed: " + e);
      }

      String newName;
      if (null != cmd && !cmd.isEmpty() && !isShellOrWrapper(cmd)) {
         // A foreground command is running (vim, htop, ssh, etc.)
         newName = base + " (" + cmd + ")";
      } else {
         // No foreground command — prefer OSC title, fallback to base
         String title = vt100.getOscTitle();
         newName = (null != title && !title.isEmpty()) ? title : base;
      }
      if (!newName.equals(name)) {
         trace("ShellSession " + id + ": label '"
            + name + "' -> '" + newName + "'");
         name = newName;
      }
   }

   /**
    * Checks if a command name is a shell or PTY wrapper process
    * that should not appear in the shell label.  Handles both
    * bare names ({@code bash}) and full paths ({@code /bin/bash}).
    */
   private static boolean isShellOrWrapper(String cmd) {
      String base = cmd;
      int slash = cmd.lastIndexOf('/');
      if (slash >= 0)
         base = cmd.substring(slash + 1);
      // Strip leading dash (login shell convention: -bash)
      if (base.startsWith("-"))
         base = base.substring(1);
      return base.equals("bash") || base.equals("zsh")
         || base.equals("sh") || base.equals("fish")
         || base.equals("script") || base.equals("login");
   }

   /**
    * Forces the next {@link #updateLabel()} call to re-query the
    * process tree, bypassing the time-based cache.
    */
   void invalidateLabelCache() {
      lastLabelUpdate = 0;
   }

   /**
    * Finds the leaf (deepest descendant) process name starting from
    * the given PID by walking the process tree via
    * {@link ProcessHandle}.
    *
    * <p>Returns the basename of the executable (path stripped) so
    * callers get {@code "htop"} rather than {@code "/usr/bin/htop"}.
    *
    * @param startPid the PID to start from
    * @return the leaf process command name, or null on failure
    */
   static String getLeafProcessName(long startPid) {
      java.util.Optional<ProcessHandle> opt =
         ProcessHandle.of(startPid);
      if (opt.isEmpty())
         return null;
      ProcessHandle current = opt.get();
      // Walk down up to 10 levels to avoid infinite loops
      for (int depth = 0; depth < 10; depth++) {
         java.util.Optional<ProcessHandle> child =
            current.children().findFirst();
         if (child.isEmpty())
            break;
         current = child.get();
      }
      return current.info().command()
         .map(cmd -> {
            int slash = cmd.lastIndexOf('/');
            return slash >= 0 ? cmd.substring(slash + 1) : cmd;
         })
         .orElse(null);
   }
}
