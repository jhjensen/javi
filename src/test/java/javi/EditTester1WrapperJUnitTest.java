package javi;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.concurrent.TimeUnit;

/**
 * JUnit wrapper for the legacy {@link EditTester1} integration tests.
 *
 * <p>EditTester1 is a comprehensive sequential test suite embedded in
 * TextEdit.java that exercises insert, delete, undo/redo, checkpoint,
 * file I/O, and stream operations. Rather than rewriting 18+ complex
 * interdependent tests, this wrapper calls them individually so that
 * JaCoCo captures coverage for all exercised code paths.
 *
 * <p>Tests are ordered sequentially because later tests depend on files
 * created by earlier tests (e.g. test2 copies files from test1).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EditTester1WrapperJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   @Test @Order(1) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test1() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test1();
   }

   @Test @Order(2) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test18() throws Exception {
      UI.setStream(new StringReader("o"));
      EditTester1.test18();
   }

   @Test @Order(3) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test2() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test2();
   }

   @Test @Order(4) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test3() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test3();
   }

   @Test @Order(5) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test4() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test4();
   }

   @Test @Order(6) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test5() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test5();
   }

   @Test @Order(7) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test6() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test6();
   }

   @Test @Order(8) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test7() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test7();
   }

   @Test @Order(9) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test8() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test8();
   }

   @Test @Order(10) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test9() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test9();
   }

   @Test @Order(11) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test10() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test10();
   }

   @Test @Order(12) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test11() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test11();
   }

   @Test @Order(13) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test13() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test13();
   }

   @Test @Order(14) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test14() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test14();
   }

   @Test @Order(15) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test15() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test15();
   }

   @Test @Order(16) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test16() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test16();
   }

   @Test @Order(17) @Timeout(value = 30, unit = TimeUnit.SECONDS)
   void test17() throws Exception {
      UI.setStream(new StringReader(""));
      EditTester1.test17();
   }

   @Test @Order(18) @Timeout(value = 120, unit = TimeUnit.SECONDS)
   void perftest() throws Exception {
      EditTester1.perftest();
   }

   @AfterAll
   static void cleanup() {
      history.Testutil.cleanupTestFiles();
   }
}
