package history;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link Tools} — utility class for tracing,
 * assertions, and process execution helpers.
 *
 * <p>
 * Covers:
 * </p>
 * <ul>
 *   <li>{@code trace()} — varargs formatting</li>
 *   <li>{@code caller()} — stack introspection</li>
 *   <li>{@code Assert()} — runtime assertion</li>
 *   <li>{@code execute()} — external command execution</li>
 * </ul>
 */
class ToolsJUnitTest {

   // ============================================================
   // Assert tests
   // ============================================================

   @Test
   void assertTrueDoesNotThrow() {
      Tools.checkAssertion(true, "should not throw");
      // No exception means success
   }

   @Test
   void assertFalseThrowsRuntimeException() {
      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> Tools.checkAssertion(false, "failure info"));
      assertTrue(ex.getMessage().contains("ASSERTION FAILURE"));
      assertTrue(ex.getMessage().contains("failure info"));
   }

   @Test
   void assertFalseIncludesDumpObjectInMessage() {
      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> Tools.checkAssertion(false, Integer.valueOf(42)));
      assertTrue(ex.getMessage().contains("42"));
   }

   // ============================================================
   // caller() tests
   // ============================================================

   @Test
   void callerReturnsCallingMethodName() {
      String name = helperCallerMethod();
      // caller() returns the name of the method that called
      // the method that called caller(). So from helperCallerMethod,
      // caller() sees helperCallerMethod's caller = this test method.
      assertEquals("callerReturnsCallingMethodName", name);
   }

   private String helperCallerMethod() {
      return Tools.caller();
   }

   // ============================================================
   // trace() formatting tests (output goes to stderr)
   // ============================================================

   @Test
   void traceDoesNotThrowWithNoArgs() {
      // trace() can be called with zero args — should not NPE
      Tools.trace();
   }

   @Test
   void traceDoesNotThrowWithSingleString() {
      Tools.trace("single arg");
   }

   @Test
   void traceDoesNotThrowWithMultipleArgs() {
      Tools.trace("arg1", "arg2", "arg3");
   }

   @Test
   void traceDoesNotThrowWithNullArg() {
      Tools.trace((Object) null);
   }

   @Test
   void traceDoesNotThrowWithMixedTypes() {
      Tools.trace("string", Integer.valueOf(42), null, Double.valueOf(3.14));
   }

   @Test
   void traceHandlesObjectArray() {
      Object[] arr = {"a", "b", "c"};
      Tools.trace((Object) arr);
   }

   @Test
   void traceHandlesIntArray() {
      int[] arr = {1, 2, 3};
      Tools.trace((Object) arr);
   }

   // ============================================================
   // execute() tests — runs real OS commands
   // ============================================================

   @Test
   void executeEchoReturnsOutput() throws Exception {
      ArrayList<String> output = Tools.execute(null, "echo", "hello");
      assertNotNull(output);
      assertEquals(1, output.size());
      assertEquals("hello", output.get(0));
   }

   @Test
   void executeMultiLineOutput() throws Exception {
      ArrayList<String> output = Tools.execute(null,
         "printf", "line1\\nline2\\nline3");
      assertEquals(3, output.size());
      assertEquals("line1", output.get(0));
      assertEquals("line2", output.get(1));
      assertEquals("line3", output.get(2));
   }

   @Test
   void runcmdReturnsBufferedReader() throws Exception {
      try (java.io.BufferedReader br = Tools.runcmd("echo", "test")) {
         String line = br.readLine();
         assertEquals("test", line);
      }
   }

   // ============================================================
   // doGC — smoke test (just shouldn't throw)
   // ============================================================

   @Test
   void doGCDoesNotThrow() {
      Tools.doGC();
   }
}
