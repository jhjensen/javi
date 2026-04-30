package javi;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static history.Tools.trace;

/**
 * Toggleable terminal I/O logger for debugging VT100 escape sequence
 * processing. Logs received bytes, sent bytes, and parser state
 * transitions to a file.
 *
 * <p>Enable via {@code :termlog on} or {@code :termlog /path/to/file}.
 * Disable via {@code :termlog off}.</p>
 *
 * <p>Log format:</p>
 * <ul>
 *   <li>{@code RECV hex=XX ch='c'} — byte received from child process</li>
 *   <li>{@code SEND hex=XX ch='c'} — byte sent to child process</li>
 *   <li>{@code SEND str="..."} — string sent (response/key sequence)</li>
 *   <li>{@code STATE old -> new trigger=XX} — parser state transition</li>
 * </ul>
 */
final class TermLog {

   /** State name table, indexed by parser state constant. */
   private static final String[] STATE_NAMES = {
      "NORM",              // 0
      "ESC",               // 1
      "GETNUM",            // 2
      "MODE",              // 3
      "?4",                // 4 (unused)
      "OSCMODE",           // 5
      "OSCMODE2",          // 6
      "OSCMODE3",          // 7
      "DISCARD",           // 8
      "CR",                // 9
      "CHARSET_DESIGNATE", // 10
      "DEC_LINE_ATTR",     // 11
      "DISCARD_DECSTR",    // 12
      "CSI_GT",            // 13
      "STRING_DISCARD",    // 14
      "CSI_STAR",          // 15
   };

   private volatile BufferedWriter logWriter;
   private volatile boolean enabled;

   TermLog() { }

   /**
    * Returns true if logging is currently active.
    */
   boolean isEnabled() {
      return enabled;
   }

   /**
    * Enables logging to the specified file path.
    *
    * @param path file to write log to (created/appended)
    */
   void enable(Path path) {
      try {
         BufferedWriter old = logWriter;
         logWriter = Files.newBufferedWriter(path,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
         enabled = true;
         if (old != null)
            old.close();
         logWriter.write("=== TERMLOG STARTED "
            + java.time.LocalDateTime.now() + " ===\n");
         logWriter.flush();
         trace("TermLog enabled: " + path);
      } catch (IOException e) {
         trace("TermLog enable failed: " + e);
      }
   }

   /**
    * Enables logging to the default path ({@code /tmp/javi-termlog.txt}
    * is avoided per project rules; uses user home instead).
    */
   void enable() {
      String home = System.getProperty("user.home", ".");
      enable(Path.of(home, "javi-termlog.txt"));
   }

   /**
    * Disables logging and closes the log file.
    */
   void disable() {
      enabled = false;
      BufferedWriter w = logWriter;
      logWriter = null;
      if (w != null) {
         try {
            w.write("=== TERMLOG STOPPED "
               + java.time.LocalDateTime.now() + " ===\n");
            w.flush();
            w.close();
         } catch (IOException e) {
            trace("TermLog close failed: " + e);
         }
      }
      trace("TermLog disabled");
   }

   /**
    * Logs a received byte (from child process to terminal emulator).
    *
    * @param ch the character received
    */
   void logRecv(char ch) {
      if (!enabled)
         return;
      write("RECV " + formatChar(ch));
   }

   /**
    * Logs a sent byte (from terminal emulator to child process).
    *
    * @param ch the character sent
    */
   void logSend(char ch) {
      if (!enabled)
         return;
      write("SEND " + formatChar(ch));
   }

   /**
    * Logs a sent string (response or key escape sequence).
    *
    * @param s the string sent
    */
   void logSend(String s) {
      if (!enabled)
         return;
      StringBuilder buf = new StringBuilder("SEND str=\"");
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c == 27)
            buf.append("\\e");
         else if (c < 0x20)
            buf.append("\\x").append(String.format("%02x", (int) c));
         else
            buf.append(c);
      }
      buf.append('"');
      write(buf.toString());
   }

   /**
    * Logs a parser state transition.
    *
    * @param oldState the previous state
    * @param newState the new state
    * @param trigger the character that caused the transition
    */
   void logState(int oldState, int newState, char trigger) {
      if (!enabled)
         return;
      if (oldState == newState)
         return;
      write("STATE " + stateName(oldState) + " -> "
         + stateName(newState) + " trigger="
         + formatChar(trigger));
   }

   /**
    * Logs a mode set/reset operation (DECSET/DECRST).
    *
    * @param modeNum the mode number
    * @param enable true for set, false for reset
    */
   void logMode(int modeNum, boolean enable) {
      if (!enabled)
         return;
      write("MODE " + (enable ? "SET" : "RST") + " " + modeNum);
   }

   /**
    * Logs an arbitrary diagnostic message.
    *
    * @param msg the message
    */
   void log(String msg) {
      if (!enabled)
         return;
      write("INFO " + msg);
   }

   /**
    * Returns a human-readable name for a parser state constant.
    */
   static String stateName(int state) {
      if (state >= 0 && state < STATE_NAMES.length)
         return STATE_NAMES[state];
      return "?" + state;
   }

   /**
    * Formats a character for logging: hex value + printable form.
    */
   private static String formatChar(char ch) {
      StringBuilder buf = new StringBuilder();
      buf.append("hex=");
      buf.append(String.format("%02x", (int) ch));
      if (ch == 27)
         buf.append(" ESC");
      else if (ch >= 0x20 && ch < 0x7f)
         buf.append(" ch='").append(ch).append('\'');
      else if (ch == '\n')
         buf.append(" LF");
      else if (ch == '\r')
         buf.append(" CR");
      else if (ch == '\t')
         buf.append(" TAB");
      else if (ch == '\b')
         buf.append(" BS");
      else if (ch == 7)
         buf.append(" BEL");
      else if (ch < 0x20)
         buf.append(" ^").append((char) (ch + 64));
      return buf.toString();
   }

   private void write(String line) {
      BufferedWriter w = logWriter;
      if (w == null)
         return;
      try {
         w.write(line);
         w.newLine();
         w.flush();
      } catch (IOException e) {
         // Silently drop — don't disrupt terminal operation
      }
   }
}
