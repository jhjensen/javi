package javi;

import javi.lsp.LspCommands;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for {@link LspCommands} — command registration.
 *
 * <p>Verifies that all LSP commands are registered in the global
 * command hash and can be looked up by name. This test lives in the
 * {@code javi} package to access package-private {@code bindingLookup}.</p>
 */
class LspCommandsJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
      EventQueue.biglock2.lock();
      try {
         // Register LSP commands if not already registered
         try {
            new LspCommands();
         } catch (RuntimeException e) {
            // "duplicate command" means already registered — OK
            if (!e.getMessage().contains("duplicate command"))
               throw e;
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   @Test
   @DisplayName("lsp.def command is registered")
   void lspdefRegistered() {
      assertNotNull(Rgroup.bindingLookup("lsp.def"),
         ":lsp.def should be registered");
   }

   @Test
   @DisplayName("lsp.ref command is registered")
   void lsprefRegistered() {
      assertNotNull(Rgroup.bindingLookup("lsp.ref"),
         ":lsp.ref should be registered");
   }

   @Test
   @DisplayName("lsp.hover command is registered")
   void lsphoverRegistered() {
      assertNotNull(Rgroup.bindingLookup("lsp.hover"),
         ":lsp.hover should be registered");
   }

   @Test
   @DisplayName("lsp.comp command is registered")
   void lspcompRegistered() {
      assertNotNull(Rgroup.bindingLookup("lsp.comp"),
         ":lsp.comp should be registered");
   }

   @Test
   @DisplayName("lsp.status command is registered")
   void lspstatusRegistered() {
      assertNotNull(Rgroup.bindingLookup("lsp.status"),
         ":lsp.status should be registered");
   }

   @Test
   @DisplayName("lsp.restart command is registered")
   void lsprestartRegistered() {
      assertNotNull(Rgroup.bindingLookup("lsp.restart"),
         ":lsp.restart should be registered");
   }

   @Test
   @DisplayName("lsp.toggle command is registered")
   void lsptoggleRegistered() {
      assertNotNull(Rgroup.bindingLookup("lsp.toggle"),
         ":lsp.toggle should be registered");
   }

   @Test
   @DisplayName("lsp.diag command is registered")
   void lspdiagRegistered() {
      assertNotNull(Rgroup.bindingLookup("lsp.diag"),
         ":lsp.diag should be registered");
   }

   @Test
   @DisplayName("lsp.config command is registered")
   void lspconfigRegistered() {
      assertNotNull(Rgroup.bindingLookup("lsp.config"),
         ":lsp.config should be registered");
   }

   @Test
   @DisplayName("nonexistent command is not found")
   void bogusCommandNotRegistered() {
      assertNull(Rgroup.bindingLookup("lspxyznotreal"));
   }

   // ============================================================
   // :ta fallback chain — LSP disabled returns false
   // ============================================================

   /**
    * Tests that the TagLookupProvider returns false when LSP is
    * disabled, allowing :ta to fall through to ctags/mkid.
    */
   @Nested
   @DisplayName(":ta LSP fallback chain")
   class TagLookupFallbackTests {

      @Test
      @DisplayName("tryGotoDefinition returns false when LSP disabled")
      void tryDefReturnsFalseWhenDisabled() {
         javi.lsp.LspManager mgr = javi.lsp.LspManager.getInstance();
         boolean wasEnabled = mgr.isEnabled();
         try {
            mgr.setEnabled(false);
            boolean result = LspCommands.tryGotoDefinition(null);
            assertTrue(!result,
               "tryGotoDefinition should return false when disabled");
         } finally {
            mgr.setEnabled(wasEnabled);
         }
      }

      @Test
      @DisplayName("tryGotoDefinition returns false with null fvc")
      void tryDefReturnsFalseNullFvc() {
         javi.lsp.LspManager mgr = javi.lsp.LspManager.getInstance();
         boolean wasEnabled = mgr.isEnabled();
         try {
            mgr.setEnabled(true);
            // With null FvContext, getFilePathStatic returns null
            // so both definition and references paths return false
            boolean result = LspCommands.tryGotoDefinition(null);
            assertTrue(!result,
               "tryGotoDefinition should return false with null fvc");
         } finally {
            mgr.setEnabled(wasEnabled);
         }
      }
   }

   // ============================================================
   // Plugin and TagLookupProvider integration
   // ============================================================

   @Test
   @DisplayName("LspCommands implements Plugin interface")
   void implementsPlugin() {
      assertTrue(Plugin.class.isAssignableFrom(LspCommands.class),
         "LspCommands should implement Plugin");
   }

   @Test
   @DisplayName("LspCommands implements TagLookupProvider interface")
   void implementsTagLookupProvider() {
      assertTrue(TagLookupProvider.class.isAssignableFrom(LspCommands.class),
         "LspCommands should implement TagLookupProvider");
   }

   @Test
   @DisplayName("loadclass command is registered")
   void loadclassRegistered() {
      assertNotNull(Rgroup.bindingLookup("loadclass"),
         ":loadclass should be registered");
   }
}
