package javi.typingtutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Word lists for typing practice lessons.
 *
 * <p>Words are organized into explicit tiers for progressive
 * introduction. The spaced repetition system selects from
 * available tiers based on the lesson mode.</p>
 */
final class WordBank {

   private WordBank() { }

   /** Home row keys used in Tier 1 words. */
   static final String HOME_ROW_KEYS = "asdfghjkl";

   /** Tier 1 — Home row only (a,s,d,f,g,h,j,k,l). */
   static final String[] TIER_HOME_ROW = {
      "the", "and", "has", "had", "all", "add",
      "ask", "last", "fast", "half", "shall", "glad",
      "dash", "flash", "glass", "salt", "tall", "fall",
   };

   /** Tier 2 — Common short words (adds full alphabet). */
   static final String[] TIER_SHORT = {
      "is", "it", "in", "of", "to", "for", "on", "at",
      "be", "as", "or", "an", "we", "do", "no", "so",
      "if", "up", "my", "go", "me", "he", "us",
   };

   /** Tier 3 — Common medium words. */
   static final String[] TIER_MEDIUM = {
      "with", "this", "from", "they", "been", "have",
      "were", "what", "when", "your", "each", "make",
      "like", "long", "look", "many", "some", "time",
      "very", "will", "more", "then", "them", "come",
      "could", "than", "first", "other", "into", "over",
   };

   /** Tier 4 — Programming keywords. */
   static final String[] TIER_CODE = {
      "code", "file", "test", "data", "type", "list",
      "loop", "void", "null", "true", "func", "else",
      "case", "init", "done", "next", "open", "read",
      "send", "stop", "wait", "push", "pull", "sync",
   };

   /** Tier 5 — Longer challenge words. */
   static final String[] TIER_LONG = {
      "through", "before", "should", "between", "system",
      "number", "people", "public", "return", "string",
      "called", "create", "output", "object", "method",
      "update", "delete", "remove", "insert", "search",
      "change", "handle", "manage", "report", "server",
      "buffer", "filter", "render", "select", "scroll",
   };

   /** All words combined (all tiers). */
   static final String[] ALL_WORDS;

   static {
      int total = TIER_HOME_ROW.length + TIER_SHORT.length
         + TIER_MEDIUM.length + TIER_CODE.length + TIER_LONG.length;
      ALL_WORDS = new String[total];
      int pos = 0;
      System.arraycopy(TIER_HOME_ROW, 0, ALL_WORDS, pos,
         TIER_HOME_ROW.length);
      pos += TIER_HOME_ROW.length;
      System.arraycopy(TIER_SHORT, 0, ALL_WORDS, pos,
         TIER_SHORT.length);
      pos += TIER_SHORT.length;
      System.arraycopy(TIER_MEDIUM, 0, ALL_WORDS, pos,
         TIER_MEDIUM.length);
      pos += TIER_MEDIUM.length;
      System.arraycopy(TIER_CODE, 0, ALL_WORDS, pos,
         TIER_CODE.length);
      pos += TIER_CODE.length;
      System.arraycopy(TIER_LONG, 0, ALL_WORDS, pos,
         TIER_LONG.length);
   }

   /**
    * Get the word list for a given lesson mode.
    */
   static String[] wordsForMode(LessonMode mode) {
      return switch (mode) {
         case HOMEROW -> TIER_HOME_ROW;
         case CODE -> TIER_CODE;
         case EDITOR -> editorWords();
         case CUSTOM -> customWords();
         case ADAPTIVE -> ALL_WORDS;
      };
   }

   /**
    * Get editor command names suitable for typing practice.
    *
    * <p>Filters registered commands to those that are at least
    * 3 characters long (short commands like "w" and "q" are
    * too trivial for typing practice).</p>
    *
    * @return array of editor command names
    */
   static String[] editorWords() {
      return javi.Rgroup.getRegisteredCommands().stream()
         .filter(cmd -> cmd.length() >= 3)
         .sorted()
         .toArray(String[]::new);
   }

   /** Path to the custom word list file. */
   private static final Path CUSTOM_WORDS_FILE =
      Path.of(System.getProperty("user.home"),
         ".javi", "typing-words.txt");

   /** Cached custom words (reloaded if file modified). */
   private static String[] cachedCustomWords;
   private static long cachedCustomModTime;

   /**
    * Load words from {@code ~/.javi/typing-words.txt}.
    *
    * <p>The file should contain one word or phrase per line.
    * Blank lines and lines starting with '#' are ignored.
    * Falls back to ALL_WORDS if the file doesn't exist or
    * is empty.</p>
    *
    * @return the custom word list, or ALL_WORDS as fallback
    */
   static String[] customWords() {
      if (!Files.exists(CUSTOM_WORDS_FILE))
         return ALL_WORDS;
      try {
         long modTime = Files.getLastModifiedTime(CUSTOM_WORDS_FILE)
            .toMillis();
         if (cachedCustomWords != null
               && modTime == cachedCustomModTime)
            return cachedCustomWords;

         List<String> words = new ArrayList<>();
         for (String line : Files.readAllLines(CUSTOM_WORDS_FILE)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#"))
               words.add(trimmed);
         }
         if (words.isEmpty())
            return ALL_WORDS;
         cachedCustomWords = words.toArray(String[]::new);
         cachedCustomModTime = modTime;
         return cachedCustomWords;
      } catch (IOException e) {
         return ALL_WORDS;
      }
   }

   /**
    * Get all words that can be spelled using only the given characters.
    *
    * <p>Scans all tiers and returns words whose every character
    * is in the allowed set.</p>
    *
    * @param allowedChars string of allowed characters
    * @return list of matching words (may be empty)
    */
   static List<String> wordsForChars(String allowedChars) {
      List<String> result = new ArrayList<>();
      for (String word : ALL_WORDS) {
         if (usesOnlyChars(word, allowedChars))
            result.add(word);
      }
      return result;
   }

   /**
    * Check whether a word uses only characters from the allowed set.
    */
   private static boolean usesOnlyChars(String word,
         String allowedChars) {
      for (int i = 0; i < word.length(); i++) {
         if (allowedChars.indexOf(word.charAt(i)) < 0)
            return false;
      }
      return true;
   }
}
