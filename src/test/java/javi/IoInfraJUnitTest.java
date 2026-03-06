package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JUnit 5 tests for I/O infrastructure classes:
 * {@link ClassConverter}, {@link IoConverter}, {@link ChangeOpt},
 * and {@link JaviFacade}.
 */
class IoInfraJUnitTest {

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

   // ================================================================
   // ClassConverter tests
   // ================================================================

   @Nested
   @DisplayName("ClassConverter")
   class ClassConverterTests {

      @Test
      @DisplayName("StringIoc.converter is not null")
      void stringConverterExists() {
         assertNotNull(StringIoc.converter);
      }

      @Test
      @DisplayName("StringIoc uses converter for props")
      void stringIocUsesConverter() {
         StringIoc sio = new StringIoc("cctest", "data");
         assertNotNull(sio.prop);
      }
   }

   // ================================================================
   // IoConverter tests
   // ================================================================

   @Nested
   @DisplayName("IoConverter")
   class IoConverterTests {

      @Test
      @DisplayName("getnext returns null by default")
      void getnextReturnsNull() {
         FileProperties<String> fp = new FileProperties<>(
            FileDescriptor.InternalFd.make("iotest"),
            StringIoc.converter);
         IoConverter<String> ioc = new IoConverter<>(fp, true);
         assertNull(ioc.getnext());
      }

      @Test
      @DisplayName("prop is accessible")
      void propAccessible() {
         FileProperties<String> fp = new FileProperties<>(
            FileDescriptor.InternalFd.make("ioprop"),
            StringIoc.converter);
         IoConverter<String> ioc = new IoConverter<>(fp, true);
         assertNotNull(ioc.prop);
         assertEquals(fp, ioc.prop);
      }

      @Test
      @DisplayName("toString returns string")
      void toStringWorks() {
         FileProperties<String> fp = new FileProperties<>(
            FileDescriptor.InternalFd.make("iotostr"),
            StringIoc.converter);
         IoConverter<String> ioc = new IoConverter<>(fp, true);
         assertNotNull(ioc.toString());
      }
   }

   // ================================================================
   // ChangeOpt.Opcode tests
   // ================================================================

   @Nested
   @DisplayName("ChangeOpt.Opcode")
   class OpcodeTests {

      @Test
      @DisplayName("all opcodes exist")
      void allOpcodesExist() {
         ChangeOpt.Opcode[] opcodes = ChangeOpt.Opcode.values();
         assertEquals(7, opcodes.length);
      }

      @Test
      @DisplayName("NOOP is first")
      void noopFirst() {
         assertEquals(0, ChangeOpt.Opcode.NOOP.ordinal());
      }

      @Test
      @DisplayName("valueOf round-trips")
      void valueOfRoundTrip() {
         for (ChangeOpt.Opcode op : ChangeOpt.Opcode.values()) {
            assertEquals(op, ChangeOpt.Opcode.valueOf(op.name()));
         }
      }
   }

   // ================================================================
   // JaviFacade tests
   // ================================================================

   @Nested
   @DisplayName("JaviFacade")
   class JaviFacadeTests {

      @Test
      @DisplayName("createFileTE returns TextEdit instance")
      void createFileTE() {
         TextEdit<String> te = JaviFacade.createFileTE("test.txt");
         assertNotNull(te);
      }
   }
}
