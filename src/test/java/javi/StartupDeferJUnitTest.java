package javi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests that startup commands from .javini that require fvc
 * are deferred (not crashed) when fvc is null.
 */
class StartupDeferJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
      EventQueue.biglock2.lock();
      try {
         if (FileList.TestAccess.getInstance() == null
               && Rgroup.bindingLookup("vi") == null) {
            FileList.make("");
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

   @Test
   void eBindingThrowsDeferWhenFvcNull() throws Exception {
      Rgroup.KeyBinding kb = Rgroup.bindingLookup("e");
      assertNotNull(kb, "'e' command should be registered");
      assertThrows(DeferCommandException.class, () ->
         kb.dobind("nonexistent_file_xyz", 0, 0, null, false));
   }

   @Test
   void viBindingThrowsDeferWhenFvcNull() throws Exception {
      Rgroup.KeyBinding kb = Rgroup.bindingLookup("vi");
      assertNotNull(kb, "'vi' command should be registered");
      assertThrows(DeferCommandException.class, () ->
         kb.dobind("nonexistent_file_xyz", 0, 0, null, false));
   }

   @Test
   void eDirectoryArgHandledGracefullyWhenFvcNull() throws Exception {
      // "e ." where "." is always a directory should add to search
      // path without crashing, even when fvc is null.
      Rgroup.KeyBinding kb = Rgroup.bindingLookup("e");
      assertNotNull(kb, "'e' command should be registered");
      assertDoesNotThrow(() ->
         kb.dobind(".", 0, 0, null, false));
   }

   @Test
   void execCmdListDoesNotCrashWithDeferredCommands() {
      assertNotNull(FileList.TestAccess.getInstance(),
         "FileList not initialized — initOnce() should have called FileList.make");
      Command.addToCmdList("e nonexistent_file_xyz_for_test");
      assertDoesNotThrow(() -> Command.execCmdList());
      // Clean up remaining commands
      Command.doneInit();
   }
}

