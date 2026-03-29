package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

/**
 * Tests for {@link MakeCmd} — build/make command integration.
 *
 * <p>Tests the PositionCmd process wrapper and Rgroup registration.
 * Full cccommand/mkcommand testing requires the editor environment
 * (FileList, PosListList), so we focus on what's testable headless.
 */
class MakeCmdJUnitTest {

   private static MakeCmd makeCmdInstance;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
      EventQueue.biglock2.lock();
      try {
         makeCmdInstance = new MakeCmd();
      } catch (RuntimeException e) {
         // already registered from another test — look it up
         if (e.getMessage().contains("duplicate command")) {
            Rgroup.KeyBinding kb = Rgroup.bindingLookup("cc");
            assertNotNull(kb, "'cc' should be registered");
         } else {
            throw e;
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @BeforeEach
   void lock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void unlock() {
      EventQueue.biglock2.unlock();
   }

   // ── PositionCmd.make() tests ────────────────────────────────

   @Test
   void makeCreatesPositionCmd() throws IOException {
      MakeCmd.PositionCmd cmd = MakeCmd.PositionCmd.make(
         "test", "echo", "hello");
      assertNotNull(cmd);
   }

   @Test
   void makeReadsProcessOutput() throws IOException {
      MakeCmd.PositionCmd cmd = MakeCmd.PositionCmd.make(
         "test-read", "printf",
         "Foo.java(10,5 -error: bad thing\n");
      assertNotNull(cmd);
   }

   @Test
   void makeWithInvalidCommandThrows() {
      assertThrows(IOException.class, () -> {
         MakeCmd.PositionCmd.make("bad",
            "/nonexistent/command/that/does/not/exist");
      });
   }

   @Test
   void makeWithEmptyOutputCreatesCmd() throws IOException {
      MakeCmd.PositionCmd cmd = MakeCmd.PositionCmd.make(
         "empty", "true");
      assertNotNull(cmd);
   }

   @Test
   void makeWithMultiLineOutput() throws IOException {
      MakeCmd.PositionCmd cmd = MakeCmd.PositionCmd.make(
         "multi", "printf",
         "A.java(1,1 -err1\nB.java(2,3 -err2\n");
      assertNotNull(cmd);
   }

   @Test
   void positionCmdExtendsPositionIoc() throws IOException {
      MakeCmd.PositionCmd cmd = MakeCmd.PositionCmd.make(
         "type-check", "echo", "x");
      assertTrue(cmd instanceof PositionIoc,
         "PositionCmd should extend PositionIoc");
   }

   // ── Rgroup tests ─────────────────────────────────────────────

   @Test
   void ccCommandRegistered() {
      Rgroup.KeyBinding kb = Rgroup.bindingLookup("cc");
      assertNotNull(kb, "cc command should be registered");
   }

   @Test
   void mkCommandRegistered() {
      Rgroup.KeyBinding kb = Rgroup.bindingLookup("mk");
      assertNotNull(kb, "mk command should be registered");
   }
}
