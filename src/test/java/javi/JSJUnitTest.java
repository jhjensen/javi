package javi;

import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * JUnit 5 port of the inline tests from {@code JS.JSR#main}.
 *
 * Tests Rhino JavaScript evaluation through the {@code JS.JSR} helper
 * methods: {@code eval()} and {@code jsEvalIter()}.
 * Requires {@link StreamInterface} to bootstrap the UI subsystem
 * that Rhino context depends on.
 */
class JSJUnitTest {

   @BeforeAll
   static void initUi() throws Exception {
      TestInit.init();
      // JSR.<clinit> creates a TextEdit (jsoutput) which needs biglock2
      EventQueue.biglock2.lock();
      try {
         Class.forName("javi.JS$JSR");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void evalSimpleExpression() {
      Object result = JS.JSR.eval("'ok';");
      assertNotNull(result);
      assertEquals("ok", result.toString());
   }

   @Test
   void evalFunctionDefinitionAndCall() {
      Object result = JS.JSR.eval(
         "function f(x) { return x + 1; } f(7);");
      assertNotNull(result);
      assertEquals("8.0", result.toString());
   }

   @Test
   void jsEvalIterMultiLineScript() throws IOException {
      String[] lines = {
         "java.lang.System.out.println(300000);",
         "function f(x) { return x + 1; }",
         "f(9);"
      };
      String result = JS.JSR.jsEvalIter(
         Arrays.asList(lines).iterator(), "test");
      assertEquals("10.0", result);
   }
}
