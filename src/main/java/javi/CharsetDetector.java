package javi;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

/**
 * Utility class for detecting the appropriate character set for terminal I/O.
 *
 * <p>CharsetDetector examines environment variables to determine the correct
 * charset encoding for VT100 terminal emulation. It follows a priority order:</p>
 * <ol>
 *   <li>{@code LC_ALL} - Overrides all other locale settings</li>
 *   <li>{@code LC_CTYPE} - Character type locale</li>
 *   <li>{@code LANG} - Default locale setting</li>
 *   <li>System default charset as fallback</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Charset charset = CharsetDetector.detectTerminalCharset();
 * OutputStreamWriter writer = new OutputStreamWriter(stream, charset);
 * }</pre>
 *
 * <h2>Supported Charsets</h2>
 * <ul>
 *   <li>UTF-8 (detected from locale strings containing "UTF-8" or "utf8")</li>
 *   <li>ISO-8859-1 (Latin-1)</li>
 *   <li>US-ASCII</li>
 *   <li>Other charsets specified in locale strings</li>
 * </ul>
 *
 * @see Vt100
 * @see Vt100Parser
 */
public final class CharsetDetector {

   /** Singleton instance for charset detection. */
   private static final CharsetDetector INSTANCE = new CharsetDetector();

   /** Cached detected charset. */
   private Charset cachedCharset;

   /** Private constructor to enforce singleton pattern. */
   private CharsetDetector() {
      cachedCharset = null;
   }

   /**
    * Detects the appropriate charset for terminal I/O.
    *
    * <p>Examines environment variables in order of priority:
    * LC_ALL, LC_CTYPE, LANG. Extracts charset from locale strings
    * (e.g., "en_US.UTF-8" yields UTF-8).</p>
    *
    * @return the detected Charset, never null
    */
   public static Charset detectTerminalCharset() {
      if (INSTANCE.cachedCharset != null) {
         return INSTANCE.cachedCharset;
      }

      Charset result = INSTANCE.doDetection();
      INSTANCE.cachedCharset = result;
      return result;
   }

   /**
    * Clears the cached charset to force re-detection.
    *
    * <p>Useful if environment variables may have changed.</p>
    */
   public static void clearCache() {
      INSTANCE.cachedCharset = null;
   }

   /**
    * Performs the actual charset detection.
    *
    * @return the detected Charset
    */
   private Charset doDetection() {
      // Check environment variables in priority order
      String[] envVars = {"LC_ALL", "LC_CTYPE", "LANG"};

      for (String envVar : envVars) {
         String value = System.getenv(envVar);
         if (null != value && !value.isEmpty()) {
            Charset detected = parseLocaleCharset(value);
            if (null != detected) {
               return detected;
            }
         }
      }

      // Check TERM variable for special terminal types
      String term = System.getenv("TERM");
      if (null != term) {
         // Modern terminals typically support UTF-8
         if (term.contains("xterm") || term.contains("vt100")
               || term.contains("screen") || term.contains("tmux")) {
            return StandardCharsets.UTF_8;
         }
      }

      // Fallback to system default
      return Charset.defaultCharset();
   }

   /**
    * Parses a locale string to extract the charset.
    *
    * <p>Locale strings typically have the format "ll_CC.CHARSET" or
    * "ll_CC.CHARSET@modifier" where ll is language, CC is country,
    * and CHARSET is the encoding name.</p>
    *
    * @param locale the locale string (e.g., "en_US.UTF-8")
    * @return the parsed Charset, or null if not parseable
    */
   private Charset parseLocaleCharset(String locale) {
      if (null == locale || locale.isEmpty()) {
         return null;
      }

      // Handle "C" or "POSIX" locale
      if ("C".equals(locale) || "POSIX".equals(locale)) {
         return StandardCharsets.US_ASCII;
      }

      // Extract charset from locale string
      // Format: language_territory.charset or language_territory.charset@modifier
      int dotIndex = locale.indexOf('.');
      if (dotIndex == -1) {
         return null;
      }

      String charsetPart = locale.substring(dotIndex + 1);

      // Remove modifier if present (e.g., "@euro")
      int atIndex = charsetPart.indexOf('@');
      if (atIndex != -1) {
         charsetPart = charsetPart.substring(0, atIndex);
      }

      // Normalize common charset names
      String normalizedCharset = normalizeCharsetName(charsetPart);

      try {
         return Charset.forName(normalizedCharset);
      } catch (UnsupportedCharsetException e) {
         return null;
      }
   }

   /**
    * Normalizes charset names to standard Java names.
    *
    * @param name the charset name from locale
    * @return the normalized charset name
    */
   private String normalizeCharsetName(String name) {
      if (null == name) {
         return "UTF-8";
      }

      String upper = name.toUpperCase();

      // Common variations
      if (upper.equals("UTF8") || upper.equals("UTF-8")) {
         return "UTF-8";
      }
      if (upper.startsWith("ISO8859") || upper.startsWith("ISO-8859")) {
         // Normalize ISO-8859-N variants
         String suffix = upper.replaceAll("[^0-9]", "");
         if (!suffix.isEmpty()) {
            return "ISO-8859-" + suffix;
         }
         return "ISO-8859-1";
      }
      if (upper.equals("ASCII") || upper.equals("US-ASCII")
            || upper.equals("USASCII")) {
         return "US-ASCII";
      }
      if (upper.equals("EUCJP") || upper.equals("EUC-JP")) {
         return "EUC-JP";
      }
      if (upper.equals("SJIS") || upper.equals("SHIFT_JIS")
            || upper.equals("SHIFTJIS")) {
         return "Shift_JIS";
      }

      // Return as-is for other charsets
      return name;
   }

   /**
    * Gets a human-readable description of how the charset was detected.
    *
    * <p>Useful for debugging and status display.</p>
    *
    * @return description of the detection source
    */
   public static String getDetectionSource() {
      String[] envVars = {"LC_ALL", "LC_CTYPE", "LANG"};

      for (String envVar : envVars) {
         String value = System.getenv(envVar);
         if (null != value && !value.isEmpty()) {
            Charset detected = INSTANCE.parseLocaleCharset(value);
            if (null != detected) {
               return envVar + "=" + value + " -> " + detected.name();
            }
         }
      }

      String term = System.getenv("TERM");
      if (null != term) {
         return "TERM=" + term + " -> " + Charset.defaultCharset().name();
      }

      return "system default -> " + Charset.defaultCharset().name();
   }
}
