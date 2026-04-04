package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;

/**
 * Tests for {@link CheckStyle} — the editor's checkstyle integration.
 *
 * <p>Tests focus on Rgroup registration. Full cstyle/cstylea execution
 * requires external tools and a running editor, so not tested here.
 */
class CheckStyleJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.init();
      EventQueue.biglock2.lock();
      try {
         new CheckStyle();
      } catch (RuntimeException e) {
         if (!e.getMessage().contains("duplicate command"))
            throw e;
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

   // ── Registration tests ──────────────────────────────────────

   @Test
   void cstyleCommandRegistered() {
      Rgroup.KeyBinding kb = Rgroup.bindingLookup("cstyle");
      assertNotNull(kb, "cstyle command should be registered");
   }

   @Test
   void cstylaCommandRegistered() {
      Rgroup.KeyBinding kb = Rgroup.bindingLookup("cstylea");
      assertNotNull(kb, "cstylea command should be registered");
   }
}
