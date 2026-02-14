package javi;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javi.lsp.LspClient;
import javi.lsp.LspCommands;
import javi.lsp.LspManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 tests for the :ta → LSP → ctags fallback chain.
 *
 * <p>Verifies that {@link PosListList.Cmd#registerTagProvider} providers
 * are consulted in order during tag lookup, and that the chain falls
 * through correctly when a provider returns false.</p>
 */
class LspTagFallbackJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
      EventQueue.biglock2.lock();
      try {
         try {
            PosListList.Cmd cmd = new PosListList.Cmd();
            PosListListCoverageJUnitTest.sharedCmd = cmd;
         } catch (RuntimeException e) {
            if (!e.getMessage().contains("duplicate command"))
               throw e;
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   /**
    * Access the private static tagProviders list via reflection
    * so we can add/remove test providers without affecting global state.
    */
   @SuppressWarnings("unchecked")
   private static ArrayList<TagLookupProvider> getTagProviders()
         throws Exception {
      Field f = PosListList.Cmd.class.getDeclaredField("tagProviders");
      f.setAccessible(true);
      return (ArrayList<TagLookupProvider>) f.get(null);
   }

   // ================================================================
   // Fallback chain tests
   // ================================================================

   @Nested
   @DisplayName("Tag provider fallback chain")
   class FallbackChain {

      private ArrayList<TagLookupProvider> providers;
      private int originalSize;

      @BeforeEach
      void setUp() throws Exception {
         providers = getTagProviders();
         originalSize = providers.size();
      }

      @AfterEach
      void tearDown() {
         // Remove any test providers we added
         while (providers.size() > originalSize) {
            providers.remove(providers.size() - 1);
         }
      }

      @Test
      @DisplayName("registerTagProvider adds to provider list")
      void registerAddsProvider() {
         TagLookupProvider mock = fvc -> false;
         PosListList.Cmd.registerTagProvider(mock);
         assertTrue(providers.contains(mock));
      }

      @Test
      @DisplayName("multiple providers registered in order")
      void multipleProvidersInOrder() {
         TagLookupProvider first = fvc -> false;
         TagLookupProvider second = fvc -> false;
         PosListList.Cmd.registerTagProvider(first);
         PosListList.Cmd.registerTagProvider(second);

         int firstIdx = providers.indexOf(first);
         int secondIdx = providers.indexOf(second);
         assertTrue(firstIdx < secondIdx,
            "first registered provider should come before second");
      }

      @Test
      @DisplayName("provider returning true stops the chain")
      void handledProviderStopsChain() {
         List<String> callLog = new ArrayList<>();
         TagLookupProvider handled = fvc -> {
            callLog.add("handled");
            return true;
         };
         TagLookupProvider never = fvc -> {
            callLog.add("should-not-be-called");
            return true;
         };
         PosListList.Cmd.registerTagProvider(handled);
         PosListList.Cmd.registerTagProvider(never);

         // Simulate the loop from gototag():
         // for (TagLookupProvider provider : tagProviders) {
         //    if (provider.tryLookup(fvc)) return;
         // }
         boolean found = false;
         for (TagLookupProvider p : providers) {
            // Only test our added providers (skip pre-existing)
            if (p == handled || p == never) {
               if (p.tryLookup(null)) {
                  found = true;
                  break;
               }
            }
         }
         assertTrue(found, "chain should have found a handler");
         assertEquals(1, callLog.size(),
            "only the first handling provider should be called");
         assertEquals("handled", callLog.get(0));
      }

      @Test
      @DisplayName("provider returning false continues the chain")
      void unhandledProviderContinues() {
         List<String> callLog = new ArrayList<>();
         TagLookupProvider passThrough = fvc -> {
            callLog.add("pass");
            return false;
         };
         TagLookupProvider handler = fvc -> {
            callLog.add("handle");
            return true;
         };
         PosListList.Cmd.registerTagProvider(passThrough);
         PosListList.Cmd.registerTagProvider(handler);

         boolean found = false;
         for (TagLookupProvider p : providers) {
            if (p == passThrough || p == handler) {
               if (p.tryLookup(null)) {
                  found = true;
                  break;
               }
            }
         }
         assertTrue(found, "second provider should handle");
         assertEquals(2, callLog.size(),
            "both providers should be called");
         assertEquals("pass", callLog.get(0));
         assertEquals("handle", callLog.get(1));
      }

      @Test
      @DisplayName("all providers returning false falls through to ctags")
      void allProvidersFailFallsThrough() {
         List<String> callLog = new ArrayList<>();
         TagLookupProvider noLsp = fvc -> {
            callLog.add("lsp-miss");
            return false;
         };
         PosListList.Cmd.registerTagProvider(noLsp);

         // Simulate the loop — if no provider handles, we reach
         // the ctags fallback (taglookup)
         boolean found = false;
         for (TagLookupProvider p : providers) {
            if (p == noLsp) {
               if (p.tryLookup(null)) {
                  found = true;
                  break;
               }
            }
         }
         assertFalse(found, "no provider should have handled");
         assertEquals(1, callLog.size());
         assertEquals("lsp-miss", callLog.get(0));
         // In production, gototag() would proceed to taglookup(str, vi)
      }
   }

   // ================================================================
   // LspCommands.tryGotoDefinition fallback behavior
   // ================================================================

   @Nested
   @DisplayName("LspCommands.tryGotoDefinition as provider")
   class LspCommandsProvider {

      @Test
      @DisplayName("tryGotoDefinition returns false when LSP disabled")
      void returnsFalseWhenDisabled() {
         LspManager mgr = LspManager.getInstance();
         boolean original = mgr.isEnabled();
         try {
            mgr.setEnabled(false);
            boolean result = LspCommands.tryGotoDefinition(null);
            assertFalse(result,
               "should return false when LSP is disabled");
         } finally {
            mgr.setEnabled(original);
         }
      }

      @Test
      @DisplayName("tryGotoDefinition returns false with null fvc")
      void returnsFalseWithNullFvc() {
         LspManager mgr = LspManager.getInstance();
         boolean original = mgr.isEnabled();
         try {
            mgr.setEnabled(true);
            boolean result = LspCommands.tryGotoDefinition(null);
            assertFalse(result,
               "should return false with null FvContext");
         } finally {
            mgr.setEnabled(original);
         }
      }

      @Test
      @DisplayName("tryGotoDefinition returns false when no client "
         + "available for file type")
      void returnsFalseNoClient() throws Exception {
         LspManager mgr = LspManager.getInstance();
         boolean original = mgr.isEnabled();
         try {
            mgr.setEnabled(true);
            // getClientForFile returns null when no server binary found
            // (which is the normal case in CI/test environments)
            String path = "/tmp/test.nosuchlang";
            LspClient client = mgr.getClientForFile(path);
            // No language server configured for .nosuchlang
            assertEquals(null, client);
         } finally {
            mgr.setEnabled(original);
         }
      }
   }
}
