package javi;

import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import static history.Tools.trace;

/**
 * Manages multiple concurrent shell sessions for the terminal emulator.
 *
 * <p>ShellManager maintains a list of active VT100 terminal sessions and provides
 * methods to create, switch between, and close them. Each shell runs in its own
 * process with independent state.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Create multiple independent shell sessions</li>
 *   <li>Switch between active sessions</li>
 *   <li>List all available sessions with status</li>
 *   <li>Close individual sessions or all at once</li>
 *   <li>Auto-cleanup on editor exit</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ShellManager manager = ShellManager.getInstance();
 * ShellSession session = manager.newShell();
 * manager.switchTo(0);  // Switch to first shell
 * manager.closeShell(session.getId());
 * }</pre>
 *
 * <h2>Commands</h2>
 * <ul>
 *   <li>{@code :shell} - Create new local shell or switch to existing</li>
 *   <li>{@code :shell <n>} - Switch to shell number n</li>
 *   <li>{@code :shells} - List all shells</li>
 *   <li>{@code :shellclose} - Close current shell</li>
 *   <li>{@code :shellclose <n>} - Close shell number n</li>
 * </ul>
 *
 * @see ShellSession
 * @see Vt100
 */
public final class ShellManager {

   /** Singleton instance. */
   private static final ShellManager INSTANCE = new ShellManager();

   /** List of all active shell sessions. */
   private final List<ShellSession> sessions;

   /** Index of the currently active session, or -1 if none. */
   private int activeIndex;

   /** Counter for generating unique session IDs. */
   private int nextId;

   /**
    * Private constructor for singleton pattern.
    */
   private ShellManager() {
      sessions = new ArrayList<>();
      activeIndex = -1;
      nextId = 1;
   }

   /**
    * Gets the singleton ShellManager instance.
    *
    * @return the ShellManager instance
    */
   public static ShellManager getInstance() {
      return INSTANCE;
   }

   /**
    * Creates a new local shell session.
    *
    * @return the new ShellSession
    * @throws IOException if shell process cannot be started
    */
   public synchronized ShellSession newShell() throws IOException {
      return newShell(null);
   }

   /**
    * Creates a new shell session, optionally connecting to a remote host via SSH.
    *
    * @param host the hostname for SSH connection, or null for local shell
    * @return the new ShellSession
    * @throws IOException if shell process cannot be started
    */
   public synchronized ShellSession newShell(String host) throws IOException {
      return newShell(host, null);
   }

   /**
    * Creates a new shell session with optional host and custom name.
    *
    * @param host the hostname for SSH connection, or null for local shell
    * @param customName user-specified name, or null for default
    * @return the new ShellSession
    * @throws IOException if shell process cannot be started
    */
   public synchronized ShellSession newShell(String host, String customName)
         throws IOException {
      ShellSession session = new ShellSession(nextId++, host, customName);
      sessions.add(session);
      activeIndex = sessions.size() - 1;
      trace("ShellManager: created session " + session.getId()
         + " (total: " + sessions.size() + ")");
      return session;
   }

   /**
    * Switches to the shell at the specified index.
    *
    * @param index the zero-based index of the shell to activate
    * @return true if switch was successful, false if index is invalid
    */
   public synchronized boolean switchTo(int index) {
      if (index < 0 || index >= sessions.size()) {
         return false;
      }
      activeIndex = index;
      return true;
   }

   /**
    * Switches to the shell with the specified ID.
    *
    * @param id the unique ID of the shell to activate
    * @return true if switch was successful, false if ID not found
    */
   public synchronized boolean switchToId(int id) {
      for (int i = 0; i < sessions.size(); i++) {
         if (sessions.get(i).getId() == id) {
            activeIndex = i;
            return true;
         }
      }
      return false;
   }

   /**
    * Gets the currently active session.
    *
    * @return the active ShellSession, or null if none
    */
   public synchronized ShellSession getActive() {
      if (activeIndex >= 0 && activeIndex < sessions.size()) {
         return sessions.get(activeIndex);
      }
      return null;
   }

   /**
    * Gets the TextEdit buffer for the currently active session.
    *
    * @return the active session's buffer, or null if no active session
    */
   public synchronized TextEdit<String> getActiveBuffer() {
      ShellSession session = getActive();
      return null != session ? session.getBuffer() : null;
   }

   /**
    * Gets the index of the currently active session.
    *
    * @return the active index, or -1 if none
    */
   public synchronized int getActiveIndex() {
      return activeIndex;
   }

   /**
    * Gets the number of active shell sessions.
    *
    * @return the session count
    */
   public synchronized int getSessionCount() {
      return sessions.size();
   }

   /**
    * Gets an unmodifiable list of all sessions.
    *
    * @return list of all ShellSessions
    */
   public synchronized List<ShellSession> getSessions() {
      return Collections.unmodifiableList(new ArrayList<>(sessions));
   }

   /**
    * Switches to the next shell session (wraps around).
    *
    * @return true if there are multiple shells to cycle through
    */
   public synchronized boolean nextShell() {
      if (sessions.size() <= 1) {
         return false;
      }
      activeIndex = (activeIndex + 1) % sessions.size();
      return true;
   }

   /**
    * Switches to the previous shell session (wraps around).
    *
    * @return true if there are multiple shells to cycle through
    */
   public synchronized boolean previousShell() {
      if (sessions.size() <= 1) {
         return false;
      }
      activeIndex = (activeIndex - 1 + sessions.size()) % sessions.size();
      return true;
   }

   /**
    * Closes the shell with the specified ID.
    *
    * @param id the unique ID of the shell to close
    * @return true if shell was found and closed
    */
   public synchronized boolean closeShell(int id) {
      for (int i = 0; i < sessions.size(); i++) {
         if (sessions.get(i).getId() == id) {
            return closeShellAt(i);
         }
      }
      return false;
   }

   /**
    * Closes the shell at the specified index.
    *
    * @param index the index of the shell to close
    * @return true if shell was closed successfully
    */
   public synchronized boolean closeShellAt(int index) {
      if (index < 0 || index >= sessions.size()) {
         return false;
      }

      ShellSession session = sessions.remove(index);
      trace("ShellManager: closing session " + session.getId());

      try {
         session.close();
      } catch (IOException e) {
         trace("ShellManager: error closing session: " + e);
      }

      // Adjust active index
      if (sessions.isEmpty()) {
         activeIndex = -1;
      } else if (activeIndex >= sessions.size()) {
         activeIndex = sessions.size() - 1;
      } else if (activeIndex > index) {
         activeIndex--;
      }

      return true;
   }

   /**
    * Closes the currently active shell.
    *
    * @return true if there was an active shell to close
    */
   public synchronized boolean closeActiveShell() {
      if (activeIndex < 0) {
         return false;
      }
      return closeShellAt(activeIndex);
   }

   /**
    * Closes all shell sessions.
    *
    * <p>Called during editor shutdown.</p>
    */
   public synchronized void closeAll() {
      trace("ShellManager: closing all " + sessions.size() + " sessions");
      while (!sessions.isEmpty()) {
         ShellSession session = sessions.remove(sessions.size() - 1);
         try {
            session.close();
         } catch (IOException e) {
            trace("ShellManager: error closing session: " + e);
         }
      }
      activeIndex = -1;
   }

   /**
    * Finds a session by its associated buffer.
    *
    * @param buffer the TextEdit buffer to search for
    * @return the session, or null if not found
    */
   public synchronized ShellSession findByBuffer(TextEdit<?> buffer) {
      for (ShellSession session : sessions) {
         if (session.getBuffer() == buffer) {
            return session;
         }
      }
      return null;
   }

   /**
    * Finds a session by name.
    *
    * @param name the session name to search for
    * @return the session, or null if not found
    */
   public synchronized ShellSession findByName(String name) {
      for (ShellSession session : sessions) {
         if (session.getName().equals(name))
            return session;
      }
      return null;
   }

   /**
    * Switches to the session with the given name.
    *
    * @param name the session name
    * @return true if found and switched
    */
   public synchronized boolean switchToName(String name) {
      for (int i = 0; i < sessions.size(); i++) {
         if (sessions.get(i).getName().equals(name)) {
            activeIndex = i;
            return true;
         }
      }
      return false;
   }

   /**
    * Gets a formatted list of shells for display.
    *
    * @return multi-line string describing all shells
    */
   public synchronized String getShellList() {
      if (sessions.isEmpty()) {
         return "No active shells";
      }

      StringBuilder sb = new StringBuilder();
      sb.append("Shell Sessions:\n");
      for (int i = 0; i < sessions.size(); i++) {
         ShellSession s = sessions.get(i);
         String marker = (i == activeIndex) ? "* " : "  ";
         sb.append(String.format("%s%d: %s [%s]%n",
            marker, s.getId(), s.getName(),
            s.isAlive() ? "running" : "stopped"));
      }
      return sb.toString();
   }
}
