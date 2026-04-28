package javi;

import javi.ai.AIException;
import javi.ai.AIProvider;
import javi.ai.CopilotProvider;
import javi.ai.CopilotRestClient;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live end-to-end tests for the GitHub Copilot integration.
 *
 * <p>These tests exercise the real Copilot API: token exchange,
 * model listing, chat completion, and streaming. They require
 * a valid OAuth token in {@code ~/.config/github-copilot/apps.json}
 * (written by {@code :ai auth}, copilot.vim, or copilot.lua).</p>
 *
 * <p>Tests are skipped (not failed) when no token is available,
 * so they are safe to run in CI without credentials.</p>
 */
@DisplayName("Copilot Live E2E")
class CopilotLiveE2EJUnitTest {

   /** Shared client reused across tests. */
   private static CopilotRestClient sharedClient;

   /** Shared provider reused across tests. */
   private static CopilotProvider sharedProvider;

   /** True if a valid OAuth token was found. */
   private static boolean tokenAvailable;

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
      String token = CopilotRestClient.loadOAuthToken();
      tokenAvailable = (null != token && !token.isEmpty());
      if (tokenAvailable) {
         sharedClient = new CopilotRestClient(token);
         sharedProvider = new CopilotProvider(
            sharedClient, CopilotProvider.DEFAULT_MODEL);
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

   /** Guard — skip if no OAuth token. */
   private static void requireToken() {
      assumeTrue(tokenAvailable,
         "Skipped: no Copilot OAuth token in apps.json "
         + "or " + CopilotRestClient.TOKEN_ENV);
   }

   // ── Token Exchange ────────────────────────────────────────

   @Nested
   @DisplayName("token exchange")
   class TokenExchange {

      @Test
      @DisplayName("OAuth token exchanges for session token")
      void exchangeProducesSessionToken()
            throws IOException, AIException {
         requireToken();
         // listModels internally calls ensureSessionToken
         // — if exchange fails this throws
         List<String> models = sharedClient.listModels();
         assertNotNull(models,
            "listModels should return non-null after "
            + "successful token exchange");
      }
   }

   // ── Model Listing ─────────────────────────────────────────

   @Nested
   @DisplayName("model listing")
   class ModelListing {

      @Test
      @DisplayName("listModels returns at least one model")
      void listModelsNonEmpty()
            throws IOException, AIException {
         requireToken();
         List<String> models = sharedClient.listModels();
         assertFalse(models.isEmpty(),
            "Copilot should expose at least one model");
      }

      @Test
      @DisplayName("listModels includes a GPT model")
      void listModelsContainsGpt()
            throws IOException, AIException {
         requireToken();
         List<String> models = sharedClient.listModels();
         boolean hasGpt = models.stream()
            .anyMatch(m -> m.startsWith("gpt-"));
         assertTrue(hasGpt,
            "Expected at least one gpt-* model, got: "
            + models);
      }

      @Test
      @DisplayName("listModels includes Claude model")
      void listModelsContainsClaude()
            throws IOException, AIException {
         requireToken();
         List<String> models = sharedClient.listModels();
         boolean hasClaude = models.stream()
            .anyMatch(m -> m.contains("claude"));
         assertTrue(hasClaude,
            "Expected at least one claude model, got: "
            + models);
      }
   }

   // ── Chat Completion (via CopilotProvider) ─────────────────

   @Nested
   @DisplayName("chat completion")
   class ChatCompletion {

      @Test
      @DisplayName("simple chat returns non-empty response")
      void simpleChatReturnsContent()
            throws IOException, AIException {
         requireToken();
         List<AIProvider.Message> msgs = List.of(
            new AIProvider.Message("user",
               "Reply with exactly: PONG"));
         String content =
            sharedProvider.chatCompletion(msgs, 32);
         assertNotNull(content,
            "response should contain content");
         assertFalse(content.isBlank(),
            "content should not be blank");
         assertTrue(content.contains("PONG"),
            "Expected PONG in response, got: " + content);
      }

      @Test
      @DisplayName("system + user messages work together")
      void systemAndUserMessages()
            throws IOException, AIException {
         requireToken();
         List<AIProvider.Message> msgs = List.of(
            new AIProvider.Message("system",
               "You are a helpful coding assistant. "
               + "Always reply in under 20 words."),
            new AIProvider.Message("user",
               "What is a Java record?"));
         String content =
            sharedProvider.chatCompletion(msgs, 100);
         assertNotNull(content);
         assertFalse(content.isBlank(),
            "expected a non-blank explanation");
      }

      @Test
      @DisplayName("multi-turn conversation preserves context")
      void multiTurnConversation()
            throws IOException, AIException {
         requireToken();
         List<AIProvider.Message> msgs = List.of(
            new AIProvider.Message("user",
               "My favorite color is blue."),
            new AIProvider.Message("assistant",
               "Got it, your favorite color is blue."),
            new AIProvider.Message("user",
               "What is my favorite color? "
               + "Reply with just the color name."));
         String content =
            sharedProvider.chatCompletion(msgs, 16);
         assertNotNull(content);
         assertTrue(
            content.toLowerCase().contains("blue"),
            "Expected 'blue' in response, got: "
            + content);
      }
   }

   // ── Streaming ─────────────────────────────────────────────

   @Nested
   @DisplayName("streaming")
   class Streaming {

      @Test
      @DisplayName("streaming chat delivers content chunks")
      void streamingDeliversChunks()
            throws IOException, AIException {
         requireToken();
         List<AIProvider.Message> msgs = List.of(
            new AIProvider.Message("user",
               "Reply with exactly: STREAM_OK"));
         List<String> chunks = new ArrayList<>();
         Consumer<String> onToken = chunks::add;
         String full =
            sharedProvider.chatCompletionStreaming(
               msgs, 32, onToken);
         assertNotNull(full, "streaming result not null");
         assertFalse(full.isBlank(),
            "streaming result not blank");
         assertFalse(chunks.isEmpty(),
            "should have received at least one chunk");
      }
   }

   // ── CopilotProvider Integration ───────────────────────────

   @Nested
   @DisplayName("CopilotProvider integration")
   class ProviderIntegration {

      @Test
      @DisplayName("CopilotProvider.testConnection succeeds")
      void testConnectionSucceeds() {
         requireToken();
         assertTrue(sharedProvider.testConnection(),
            "testConnection should succeed with "
            + "valid token");
      }

      @Test
      @DisplayName("provider name is 'GitHub Copilot'")
      void providerReportsCorrectName() {
         requireToken();
         assertTrue(
            "GitHub Copilot".equals(
               sharedProvider.getName()),
            "provider name should be 'GitHub Copilot'");
      }

      @Test
      @DisplayName("provider model matches DEFAULT_MODEL")
      void providerReportsCorrectModel() {
         requireToken();
         assertTrue(
            CopilotProvider.DEFAULT_MODEL.equals(
               sharedProvider.getModel()),
            "model should be " +
            CopilotProvider.DEFAULT_MODEL);
      }

      @Test
      @DisplayName("listModels accessible via provider")
      void providerListModels()
            throws IOException, AIException {
         requireToken();
         CopilotProvider cp =
            new CopilotProvider(sharedClient, "gpt-4.1");
         List<String> models = cp.listModels();
         assertFalse(models.isEmpty(),
            "provider listModels should return models");
      }
   }

   // ── Error Handling ────────────────────────────────────────

   @Nested
   @DisplayName("error handling")
   class ErrorHandling {

      @Test
      @DisplayName("invalid token fails gracefully")
      void invalidTokenFails() {
         CopilotRestClient badClient =
            new CopilotRestClient("gho_invalid_token");
         CopilotProvider provider =
            new CopilotProvider(badClient,
               CopilotProvider.DEFAULT_MODEL);
         assertFalse(provider.testConnection(),
            "testConnection should fail with bad token");
      }
   }
}
