package javi.lsp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON encoder/decoder for LSP message handling.
 *
 * <p>Provides just enough JSON support for the Language Server Protocol
 * without requiring an external JSON library. Handles the subset of JSON
 * used by LSP: objects, arrays, strings, numbers, booleans, and null.</p>
 *
 * <h2>Encoding</h2>
 * <p>{@link #encode(Object)} converts Java objects to JSON strings:
 * <ul>
 *   <li>{@link Map} &rarr; JSON object</li>
 *   <li>{@link List} &rarr; JSON array</li>
 *   <li>{@link String} &rarr; JSON string (escaped)</li>
 *   <li>{@link Number} &rarr; JSON number</li>
 *   <li>{@link Boolean} &rarr; JSON boolean</li>
 *   <li>null &rarr; JSON null</li>
 * </ul></p>
 *
 * <h2>Decoding</h2>
 * <p>{@link #decodeObject(String)} parses a JSON string into nested
 * {@link Map} / {@link List} / String / Number / Boolean / null
 * structures.</p>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Numbers are parsed as Long or Double only</li>
 *   <li>Unicode escapes (\\uXXXX) are supported</li>
 *   <li>No streaming - full string must be in memory</li>
 * </ul>
 *
 * @see JsonRpc
 */
final class SimpleJson {

   /** Private constructor - utility class. */
   private SimpleJson() {
   }

   /**
    * Encodes a Java object tree into a JSON string.
    *
    * @param obj the object to encode (Map, List, String, Number, Boolean, null)
    * @return JSON string representation
    */
   @SuppressWarnings("unchecked")
   static String encode(Object obj) {
      if (null == obj) {
         return "null";
      }
      if (obj instanceof Map) {
         return encodeMap((Map<String, Object>) obj);
      }
      if (obj instanceof List) {
         return encodeList((List<Object>) obj);
      }
      if (obj instanceof String) {
         return encodeString((String) obj);
      }
      if (obj instanceof Number) {
         return obj.toString();
      }
      if (obj instanceof Boolean) {
         return obj.toString();
      }
      return encodeString(obj.toString());
   }

   /**
    * Encodes a Map as a JSON object.
    *
    * @param map the map to encode
    * @return JSON object string
    */
   private static String encodeMap(Map<String, Object> map) {
      StringBuilder sb = new StringBuilder();
      sb.append('{');
      boolean first = true;
      for (Map.Entry<String, Object> entry : map.entrySet()) {
         if (!first)
            sb.append(',');
         first = false;
         sb.append(encodeString(entry.getKey()));
         sb.append(':');
         sb.append(encode(entry.getValue()));
      }
      sb.append('}');
      return sb.toString();
   }

   /**
    * Encodes a List as a JSON array.
    *
    * @param list the list to encode
    * @return JSON array string
    */
   private static String encodeList(List<Object> list) {
      StringBuilder sb = new StringBuilder();
      sb.append('[');
      boolean first = true;
      for (Object item : list) {
         if (!first)
            sb.append(',');
         first = false;
         sb.append(encode(item));
      }
      sb.append(']');
      return sb.toString();
   }

   /**
    * Encodes a string with proper JSON escaping.
    *
    * @param str the string to encode
    * @return JSON string with quotes and escapes
    */
   static String encodeString(String str) {
      StringBuilder sb = new StringBuilder();
      sb.append('"');
      for (int i = 0; i < str.length(); i++) {
         char ch = str.charAt(i);
         switch (ch) {
            case '"':
               sb.append("\\\"");
               break;
            case '\\':
               sb.append("\\\\");
               break;
            case '\b':
               sb.append("\\b");
               break;
            case '\f':
               sb.append("\\f");
               break;
            case '\n':
               sb.append("\\n");
               break;
            case '\r':
               sb.append("\\r");
               break;
            case '\t':
               sb.append("\\t");
               break;
            default:
               if (ch < 0x20) {
                  sb.append(String.format("\\u%04x", (int) ch));
               } else {
                  sb.append(ch);
               }
         }
      }
      sb.append('"');
      return sb.toString();
   }

   /**
    * Parses a JSON string into a Map (JSON object expected at top level).
    *
    * @param json the JSON string to parse
    * @return the parsed map, or null if parsing fails
    */
   static Map<String, Object> decodeObject(String json) {
      if (null == json || json.isEmpty())
         return null;
      try {
         Parser p = new Parser(json);
         Object result = p.parseValue();
         if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            return map;
         }
         return null;
      } catch (Exception e) {
         return null;
      }
   }

   /**
    * Parses a JSON string into a generic Object (any JSON value).
    *
    * @param json the JSON string to parse
    * @return the parsed value, or null if parsing fails
    */
   static Object decode(String json) {
      if (null == json || json.isEmpty())
         return null;
      try {
         Parser p = new Parser(json);
         return p.parseValue();
      } catch (Exception e) {
         return null;
      }
   }

   /**
    * Recursive-descent JSON parser.
    *
    * <p>Parses a complete JSON string into Java objects:
    * objects become {@link HashMap}, arrays become {@link ArrayList},
    * strings become {@link String}, numbers become {@link Long} or
    * {@link Double}, booleans become {@link Boolean}, null becomes null.</p>
    */
   private static final class Parser {
      private final String input;
      private int pos;

      Parser(String input) {
         this.input = input;
         this.pos = 0;
      }

      /**
       * Parses any JSON value at the current position.
       *
       * @return the parsed Java object
       */
      Object parseValue() {
         skipWhitespace();
         if (pos >= input.length())
            return null;

         char ch = input.charAt(pos);
         switch (ch) {
            case '{':
               return parseObject();
            case '[':
               return parseArray();
            case '"':
               return parseString();
            case 't':
            case 'f':
               return parseBoolean();
            case 'n':
               return parseNull();
            default:
               return parseNumber();
         }
      }

      /**
       * Parses a JSON object into a HashMap.
       *
       * @return the parsed map
       */
      private Map<String, Object> parseObject() {
         Map<String, Object> map = new HashMap<>();
         pos++; // skip '{'
         skipWhitespace();
         if (pos < input.length() && input.charAt(pos) == '}') {
            pos++;
            return map;
         }

         while (pos < input.length()) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ',') {
               pos++;
            } else {
               break;
            }
         }
         skipWhitespace();
         if (pos < input.length() && input.charAt(pos) == '}')
            pos++;
         return map;
      }

      /**
       * Parses a JSON array into an ArrayList.
       *
       * @return the parsed list
       */
      private List<Object> parseArray() {
         List<Object> list = new ArrayList<>();
         pos++; // skip '['
         skipWhitespace();
         if (pos < input.length() && input.charAt(pos) == ']') {
            pos++;
            return list;
         }

         while (pos < input.length()) {
            list.add(parseValue());
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ',') {
               pos++;
            } else {
               break;
            }
         }
         skipWhitespace();
         if (pos < input.length() && input.charAt(pos) == ']')
            pos++;
         return list;
      }

      /**
       * Parses a JSON string with escape handling.
       *
       * @return the parsed string
       */
      private String parseString() {
         pos++; // skip opening quote
         StringBuilder sb = new StringBuilder();
         while (pos < input.length()) {
            char ch = input.charAt(pos);
            if (ch == '"') {
               pos++;
               return sb.toString();
            }
            if (ch == '\\') {
               pos++;
               if (pos >= input.length())
                  break;
               ch = input.charAt(pos);
               switch (ch) {
                  case '"':
                  case '\\':
                  case '/':
                     sb.append(ch);
                     break;
                  case 'b':
                     sb.append('\b');
                     break;
                  case 'f':
                     sb.append('\f');
                     break;
                  case 'n':
                     sb.append('\n');
                     break;
                  case 'r':
                     sb.append('\r');
                     break;
                  case 't':
                     sb.append('\t');
                     break;
                  case 'u':
                     if (pos + 4 < input.length()) {
                        String hex = input.substring(pos + 1, pos + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                     }
                     break;
                  default:
                     sb.append(ch);
               }
            } else {
               sb.append(ch);
            }
            pos++;
         }
         return sb.toString();
      }

      /**
       * Parses a JSON number (integer or floating point).
       *
       * @return Long for integers, Double for floating point
       */
      private Number parseNumber() {
         int start = pos;
         if (pos < input.length() && input.charAt(pos) == '-')
            pos++;
         while (pos < input.length() && Character.isDigit(input.charAt(pos)))
            pos++;
         boolean isFloat = false;
         if (pos < input.length() && input.charAt(pos) == '.') {
            isFloat = true;
            pos++;
            while (pos < input.length()
                  && Character.isDigit(input.charAt(pos)))
               pos++;
         }
         if (pos < input.length()
               && (input.charAt(pos) == 'e'
               || input.charAt(pos) == 'E')) {
            isFloat = true;
            pos++;
            if (pos < input.length() && (input.charAt(pos) == '+'
                  || input.charAt(pos) == '-'))
               pos++;
            while (pos < input.length()
                  && Character.isDigit(input.charAt(pos)))
               pos++;
         }
         String numStr = input.substring(start, pos);
         return isFloat
            ? Double.valueOf(numStr)
            : Long.valueOf(numStr);
      }

      /**
       * Parses a JSON boolean (true or false).
       *
       * @return the parsed Boolean value
       */
      private Boolean parseBoolean() {
         if (input.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
         }
         pos += 5;
         return Boolean.FALSE;
      }

      /**
       * Parses a JSON null literal.
       *
       * @return null
       */
      private Object parseNull() {
         pos += 4;
         return null;
      }

      /** Advances past whitespace characters. */
      private void skipWhitespace() {
         while (pos < input.length()
               && Character.isWhitespace(input.charAt(pos)))
            pos++;
      }

      /**
       * Expects and consumes a specific character.
       *
       * @param ch the expected character
       */
      private void expect(char ch) {
         skipWhitespace();
         if (pos < input.length() && input.charAt(pos) == ch)
            pos++;
      }
   }
}
