package javi.typingtutor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static history.Tools.trace;

/**
 * Tracks javi command execution frequency for the typing tutor.
 *
 * <p>Records how often the user invokes each ex-command during
 * normal editing. Frequently-used commands are considered "known"
 * and can be excluded from typing lessons (or given lower weight),
 * while rarely-used commands can be offered as "learn your editor"
 * practice material.</p>
 *
 * <p>Usage data is persisted to {@code ~/.javi/typing-commands.dat}
 * as tab-separated {@code command\tcount} lines.</p>
 */
final class CommandTracker {

   private static final String STATS_DIR = ".javi";
   private static final String STATS_FILE = "typing-commands.dat";

   /**
    * Minimum execution count for a command to be considered "known".
    * Commands used at least this many times are deprioritized in lessons.
    */
   static final int KNOWN_THRESHOLD = 10;

   private final Map<String, Integer> usageCounts = new HashMap<>();

   /**
    * Record that a command was executed.
    *
    * @param commandName the ex-command name (e.g. "w", "nextfile")
    */
   void recordCommand(String commandName) {
      if (commandName == null || commandName.isEmpty())
         return;
      usageCounts.merge(commandName, 1, Integer::sum);
   }

   /**
    * Get the execution count for a command.
    */
   int getCount(String commandName) {
      return usageCounts.getOrDefault(commandName, 0);
   }

   /**
    * Check whether a command is "known" (used frequently).
    */
   boolean isKnown(String commandName) {
      return getCount(commandName) >= KNOWN_THRESHOLD;
   }

   /**
    * Get all commands that the user has NOT used frequently.
    * These are candidates for "learn your editor" lessons.
    *
    * @param allCommands the full set of registered command names
    * @return commands used fewer than KNOWN_THRESHOLD times
    */
   Set<String> getUnknownCommands(Set<String> allCommands) {
      return allCommands.stream()
         .filter(cmd -> !isKnown(cmd))
         .collect(Collectors.toSet());
   }

   /**
    * Get a weight multiplier for a command word.
    * Known commands get lower weight; unknown ones get higher.
    *
    * @param word the word to check against command usage
    * @return weight multiplier (0.1 for well-known, 1.0+ for unknown)
    */
   double commandWeight(String word) {
      int count = getCount(word);
      if (count >= KNOWN_THRESHOLD)
         return 0.1; // heavily deprioritize known commands
      if (count > 0)
         return 1.0 - (0.9 * count / KNOWN_THRESHOLD);
      return 1.5; // boost never-used commands
   }

   boolean hasData() {
      return !usageCounts.isEmpty();
   }

   /**
    * Format a usage report for display.
    */
   String formatReport() {
      if (usageCounts.isEmpty())
         return "No command usage data recorded yet.\n";

      StringBuilder sb = new StringBuilder();
      sb.append("=== COMMAND USAGE ===\n\n");
      sb.append(String.format("%-25s %s%n", "COMMAND", "USES"));
      sb.append("-".repeat(35)).append('\n');

      usageCounts.entrySet().stream()
         .sorted((a, b) -> b.getValue() - a.getValue())
         .limit(40)
         .forEach(e -> sb.append(String.format("%-25s %d%n",
            e.getKey(), e.getValue())));

      long knownCount = usageCounts.values().stream()
         .filter(c -> c >= KNOWN_THRESHOLD).count();
      sb.append('\n').append("Known commands (>=")
         .append(KNOWN_THRESHOLD).append(" uses): ")
         .append(knownCount).append('\n');
      return sb.toString();
   }

   /**
    * Save usage data to ~/.javi/typing-commands.dat.
    */
   void save() {
      File dir = new File(System.getProperty("user.home"), STATS_DIR);
      if (!dir.exists() && !dir.mkdirs())
         return;
      File file = new File(dir, STATS_FILE);
      try (BufferedWriter w = new BufferedWriter(
            new FileWriter(file, StandardCharsets.UTF_8))) {
         for (Map.Entry<String, Integer> e
               : usageCounts.entrySet()) {
            w.write(e.getKey());
            w.write('\t');
            w.write(Integer.toString(e.getValue()));
            w.newLine();
         }
      } catch (IOException e) {
         trace("CommandTracker save failed: " + e);
      }
   }

   /**
    * Load usage data from ~/.javi/typing-commands.dat.
    *
    * @return loaded CommandTracker instance
    */
   static CommandTracker load() {
      CommandTracker tracker = new CommandTracker();
      File file = new File(
         System.getProperty("user.home"),
         STATS_DIR + File.separator + STATS_FILE);
      if (!file.exists())
         return tracker;

      try (BufferedReader r = new BufferedReader(
            new FileReader(file, StandardCharsets.UTF_8))) {
         String line;
         while ((line = r.readLine()) != null) {
            int tab = line.indexOf('\t');
            if (tab <= 0)
               continue;
            String cmd = line.substring(0, tab);
            try {
               int count = Integer.parseInt(
                  line.substring(tab + 1).trim());
               tracker.usageCounts.put(cmd, count);
            } catch (NumberFormatException e) {
               // skip malformed line
            }
         }
      } catch (IOException e) {
         trace("CommandTracker load failed: " + e);
      }
      return tracker;
   }
}
