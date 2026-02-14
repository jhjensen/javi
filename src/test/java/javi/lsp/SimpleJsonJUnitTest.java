package javi.lsp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link SimpleJson} — minimal JSON encoder/decoder.
 */
class SimpleJsonJUnitTest {

   @Nested
   @DisplayName("encode()")
   class EncodeTests {

      @Test
      @DisplayName("encode null")
      void encodeNull() {
         assertEquals("null", SimpleJson.encode(null));
      }

      @Test
      @DisplayName("encode boolean true")
      void encodeTrue() {
         assertEquals("true", SimpleJson.encode(Boolean.TRUE));
      }

      @Test
      @DisplayName("encode boolean false")
      void encodeFalse() {
         assertEquals("false", SimpleJson.encode(Boolean.FALSE));
      }

      @Test
      @DisplayName("encode integer")
      void encodeInteger() {
         assertEquals("42", SimpleJson.encode(Integer.valueOf(42)));
      }

      @Test
      @DisplayName("encode long")
      void encodeLong() {
         assertEquals("1000000", SimpleJson.encode(Long.valueOf(1000000)));
      }

      @Test
      @DisplayName("encode double")
      void encodeDouble() {
         assertEquals("3.14", SimpleJson.encode(Double.valueOf(3.14)));
      }

      @Test
      @DisplayName("encode simple string")
      void encodeSimpleString() {
         assertEquals("\"hello\"", SimpleJson.encode("hello"));
      }

      @Test
      @DisplayName("encode string with escapes")
      void encodeStringEscapes() {
         String result = SimpleJson.encode("line1\nline2\ttab\"quote\\back");
         assertEquals("\"line1\\nline2\\ttab\\\"quote\\\\back\"", result);
      }

      @Test
      @DisplayName("encode string with control characters")
      void encodeControlChars() {
         String result = SimpleJson.encodeString("\u0001\u001f");
         assertTrue(result.contains("\\u0001"));
         assertTrue(result.contains("\\u001f"));
      }

      @Test
      @DisplayName("encode empty map")
      void encodeEmptyMap() {
         assertEquals("{}", SimpleJson.encode(new HashMap<>()));
      }

      @Test
      @DisplayName("encode map with values")
      void encodeMap() {
         Map<String, Object> map = new HashMap<>();
         map.put("key", "value");
         String json = SimpleJson.encode(map);
         assertTrue(json.contains("\"key\""));
         assertTrue(json.contains("\"value\""));
      }

      @Test
      @DisplayName("encode empty list")
      void encodeEmptyList() {
         assertEquals("[]", SimpleJson.encode(new ArrayList<>()));
      }

      @Test
      @DisplayName("encode list with values")
      void encodeList() {
         List<Object> list = new ArrayList<>();
         list.add("one");
         list.add(Integer.valueOf(2));
         list.add(Boolean.TRUE);
         String json = SimpleJson.encode(list);
         assertEquals("[\"one\",2,true]", json);
      }

      @Test
      @DisplayName("encode nested structure")
      void encodeNested() {
         Map<String, Object> inner = new HashMap<>();
         inner.put("x", Integer.valueOf(10));
         Map<String, Object> outer = new HashMap<>();
         outer.put("inner", inner);
         String json = SimpleJson.encode(outer);
         assertTrue(json.contains("\"inner\""));
         assertTrue(json.contains("\"x\":10"));
      }
   }

   @Nested
   @DisplayName("decodeObject()")
   class DecodeObjectTests {

      @Test
      @DisplayName("decode null input")
      void decodeNull() {
         assertNull(SimpleJson.decodeObject(null));
      }

      @Test
      @DisplayName("decode empty string")
      void decodeEmpty() {
         assertNull(SimpleJson.decodeObject(""));
      }

      @Test
      @DisplayName("decode non-object returns null")
      void decodeNonObject() {
         assertNull(SimpleJson.decodeObject("\"just a string\""));
      }

      @Test
      @DisplayName("decode empty object")
      void decodeEmptyObject() {
         Map<String, Object> map = SimpleJson.decodeObject("{}");
         assertNotNull(map);
         assertTrue(map.isEmpty());
      }

      @Test
      @DisplayName("decode object with string")
      void decodeStringValue() {
         Map<String, Object> map =
            SimpleJson.decodeObject("{\"name\":\"javi\"}");
         assertNotNull(map);
         assertEquals("javi", map.get("name"));
      }

      @Test
      @DisplayName("decode object with number")
      void decodeNumberValue() {
         Map<String, Object> map =
            SimpleJson.decodeObject("{\"count\":42}");
         assertNotNull(map);
         assertTrue(map.get("count") instanceof Number);
         assertEquals(42, ((Number) map.get("count")).intValue());
      }

      @Test
      @DisplayName("decode object with float")
      void decodeFloatValue() {
         Map<String, Object> map =
            SimpleJson.decodeObject("{\"pi\":3.14}");
         assertNotNull(map);
         assertEquals(Double.valueOf(3.14), map.get("pi"));
      }

      @Test
      @DisplayName("decode object with boolean")
      void decodeBoolValue() {
         Map<String, Object> map =
            SimpleJson.decodeObject("{\"ok\":true,\"fail\":false}");
         assertNotNull(map);
         assertEquals(Boolean.TRUE, map.get("ok"));
         assertEquals(Boolean.FALSE, map.get("fail"));
      }

      @Test
      @DisplayName("decode object with null value")
      void decodeNullValue() {
         Map<String, Object> map =
            SimpleJson.decodeObject("{\"key\":null}");
         assertNotNull(map);
         assertTrue(map.containsKey("key"));
         assertNull(map.get("key"));
      }

      @Test
      @DisplayName("decode nested object")
      void decodeNested() {
         Map<String, Object> map = SimpleJson.decodeObject(
            "{\"outer\":{\"inner\":\"val\"}}");
         assertNotNull(map);
         @SuppressWarnings("unchecked")
         Map<String, Object> nested = (Map<String, Object>) map.get("outer");
         assertNotNull(nested);
         assertEquals("val", nested.get("inner"));
      }

      @Test
      @DisplayName("decode array value")
      @SuppressWarnings("unchecked")
      void decodeArrayValue() {
         Map<String, Object> map = SimpleJson.decodeObject(
            "{\"items\":[1,2,3]}");
         assertNotNull(map);
         List<Object> items = (List<Object>) map.get("items");
         assertNotNull(items);
         assertEquals(3, items.size());
         assertEquals(1, ((Number) items.get(0)).intValue());
      }

      @Test
      @DisplayName("decode string with escape sequences")
      void decodeEscapes() {
         Map<String, Object> map = SimpleJson.decodeObject(
            "{\"s\":\"line1\\nline2\\ttab\"}");
         assertNotNull(map);
         assertEquals("line1\nline2\ttab", map.get("s"));
      }

      @Test
      @DisplayName("decode string with unicode escape")
      void decodeUnicode() {
         Map<String, Object> map = SimpleJson.decodeObject(
            "{\"ch\":\"\\u0041\"}");
         assertNotNull(map);
         assertEquals("A", map.get("ch"));
      }

      @Test
      @DisplayName("decode with whitespace")
      void decodeWhitespace() {
         Map<String, Object> map = SimpleJson.decodeObject(
            "  { \"a\" : 1 , \"b\" : 2 }  ");
         assertNotNull(map);
         assertEquals(1, ((Number) map.get("a")).intValue());
         assertEquals(2, ((Number) map.get("b")).intValue());
      }

      @Test
      @DisplayName("decode negative number")
      void decodeNegative() {
         Map<String, Object> map = SimpleJson.decodeObject(
            "{\"n\":-17}");
         assertNotNull(map);
         assertEquals(-17, ((Number) map.get("n")).intValue());
      }

      @Test
      @DisplayName("decode scientific notation")
      void decodeScientific() {
         Map<String, Object> map = SimpleJson.decodeObject(
            "{\"e\":1.5e3}");
         assertNotNull(map);
         assertEquals(Double.valueOf(1500.0), map.get("e"));
      }
   }

   @Nested
   @DisplayName("decode()")
   class DecodeGenericTests {

      @Test
      @DisplayName("decode null input")
      void decodeNull() {
         assertNull(SimpleJson.decode(null));
      }

      @Test
      @DisplayName("decode string value")
      void decodeString() {
         Object result = SimpleJson.decode("\"hello\"");
         assertEquals("hello", result);
      }

      @Test
      @DisplayName("decode number")
      void decodeNumber() {
         Object result = SimpleJson.decode("99");
         assertTrue(result instanceof Number);
         assertEquals(99, ((Number) result).intValue());
      }

      @Test
      @DisplayName("decode true")
      void decodeTrue() {
         assertEquals(Boolean.TRUE, SimpleJson.decode("true"));
      }

      @Test
      @DisplayName("decode array")
      @SuppressWarnings("unchecked")
      void decodeArray() {
         Object result = SimpleJson.decode("[1,\"two\",false]");
         assertTrue(result instanceof List);
         List<Object> list = (List<Object>) result;
         assertEquals(3, list.size());
         assertEquals(1, ((Number) list.get(0)).intValue());
         assertEquals("two", list.get(1));
         assertEquals(Boolean.FALSE, list.get(2));
      }
   }

   @Nested
   @DisplayName("roundtrip")
   class RoundtripTests {

      @Test
      @DisplayName("encode then decode preserves data")
      void roundtrip() {
         Map<String, Object> original = new HashMap<>();
         original.put("method", "textDocument/didOpen");
         original.put("version", Integer.valueOf(3));
         original.put("active", Boolean.TRUE);

         List<Object> items = new ArrayList<>();
         items.add("a");
         items.add("b");
         original.put("items", items);

         String json = SimpleJson.encode(original);
         Map<String, Object> decoded = SimpleJson.decodeObject(json);

         assertNotNull(decoded);
         assertEquals("textDocument/didOpen", decoded.get("method"));
         assertEquals(Boolean.TRUE, decoded.get("active"));
      }

      @Test
      @DisplayName("encode then decode LSP-like message")
      @SuppressWarnings("unchecked")
      void roundtripLspMessage() {
         // Build a realistic LSP initialize params
         Map<String, Object> params = new HashMap<>();
         params.put("processId", Long.valueOf(12345));
         params.put("rootUri", "file:///home/user/project");

         Map<String, Object> caps = new HashMap<>();
         Map<String, Object> textDoc = new HashMap<>();
         textDoc.put("synchronization", Boolean.TRUE);
         caps.put("textDocument", textDoc);
         params.put("capabilities", caps);

         String json = SimpleJson.encode(params);
         Map<String, Object> decoded = SimpleJson.decodeObject(json);

         assertNotNull(decoded);
         assertEquals(12345, ((Number) decoded.get("processId")).intValue());
         assertEquals("file:///home/user/project", decoded.get("rootUri"));

         Map<String, Object> dcaps =
            (Map<String, Object>) decoded.get("capabilities");
         assertNotNull(dcaps);
         Map<String, Object> dtd =
            (Map<String, Object>) dcaps.get("textDocument");
         assertNotNull(dtd);
         assertEquals(Boolean.TRUE, dtd.get("synchronization"));
      }
   }
}
