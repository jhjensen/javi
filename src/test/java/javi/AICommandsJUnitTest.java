package javi;

import javi.ai.AICommands;
import javi.ai.AIConfig;
import javi.ai.AIException;
import javi.ai.CopilotProvider;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JUnit 5 tests for AI command registration, help screen content,
 * and help system integration.
 */
class AICommandsJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   private String bufferText(TextEdit<String> buf) {
      StringBuilder sb = new StringBuilder();
      int end = buf.finish();
      for (int i = 1; i < end; i++) {
         sb.append(buf.at(i).toString()).append('\n');
      }
      return sb.toString();
   }

   // ── Command Registration ──────────────────────────────────────

   @Test
   @DisplayName("'ai' command is registered")
   void aiCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("ai"),
         "'ai' should be registered in cmhash");
   }

   @Test
   @DisplayName("'ai.chat' command is registered")
   void aiChatCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("ai.chat"),
         "'ai.chat' should be registered");
   }

   @Test
   @DisplayName("'ai.explain' command is registered")
   void aiExplainCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("ai.explain"),
         "'ai.explain' should be registered");
   }

   @Test
   @DisplayName("'ai.review' command is registered")
   void aiReviewCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("ai.review"),
         "'ai.review' should be registered");
   }

   @Test
   @DisplayName("'ai.doc' command is registered")
   void aiDocCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("ai.doc"),
         "'ai.doc' should be registered");
   }

   @Test
   @DisplayName("'ai.config' command is registered")
   void aiConfigCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("ai.config"),
         "'ai.config' should be registered");
   }

   @Test
   @DisplayName("'ai.clear' command is registered")
   void aiClearCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("ai.clear"),
         "'ai.clear' should be registered");
   }

   @Test
   @DisplayName("'ai.test' command is registered")
   void aiTestCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("ai.test"),
         "'ai.test' should be registered");
   }

   @Test
   @DisplayName("'ai.complete' command is registered")
   void aiCompleteCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("ai.complete"),
         "'ai.complete' should be registered");
   }

   @Test
   @DisplayName("'ai.cancel' command is registered")
   void aiCancelCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("ai.cancel"),
         "'ai.cancel' should be registered");
   }

   @Test
   @DisplayName("'ai.refactor' command is registered")
   void aiRefactorCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("ai.refactor"),
         "'ai.refactor' should be registered");
   }

   @Test
   @DisplayName("AICommands implements Plugin")
   void implementsPlugin() {
      assertTrue(Plugin.class.isAssignableFrom(AICommands.class),
         "AICommands should implement Plugin interface");
   }

   // ── Help System Integration ───────────────────────────────────

   @Test
   @DisplayName("getHelp('ai') returns AI help")
   void helpAiTopic() {
      TextEdit<String> buf = HelpSystem.getHelp("ai");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("AI ASSISTANT COMMANDS"),
         "ai help should contain title");
   }

   @Test
   @DisplayName("getHelp('chat') returns AI help")
   void helpChatAlias() {
      TextEdit<String> buf = HelpSystem.getHelp("chat");
      String text = bufferText(buf);
      assertTrue(text.contains("AI ASSISTANT COMMANDS"),
         "'chat' alias should show AI help");
   }

   @Test
   @DisplayName("getHelp('copilot') returns AI help")
   void helpCopilotAlias() {
      TextEdit<String> buf = HelpSystem.getHelp("copilot");
      String text = bufferText(buf);
      assertTrue(text.contains("AI ASSISTANT COMMANDS"),
         "'copilot' alias should show AI help");
   }

   @Test
   @DisplayName("AI help lists all chat commands")
   void helpListsChatCommands() {
      String text = bufferText(HelpSystem.getHelp("ai"));
      assertTrue(text.contains(":ai <message>"), "should list :ai <message>");
      assertTrue(text.contains(":ai chat"), "should list :ai chat");
      assertTrue(text.contains(":ai clear"), "should list :ai clear");
   }

   @Test
   @DisplayName("AI help lists code analysis commands")
   void helpListsAnalysisCommands() {
      String text = bufferText(HelpSystem.getHelp("ai"));
      assertTrue(text.contains(":ai explain"), "should list :ai explain");
      assertTrue(text.contains(":ai review"), "should list :ai review");
      assertTrue(text.contains(":ai doc"), "should list :ai doc");
      assertTrue(text.contains(":ai refactor"), "should list :ai refactor");
   }

   @Test
   @DisplayName("AI help lists completion commands")
   void helpListsCompletionCommands() {
      String text = bufferText(HelpSystem.getHelp("ai"));
      assertTrue(text.contains(":ai complete"), "should list :ai complete");
      assertTrue(text.contains(":ai accept"), "should list :ai accept");
      assertTrue(text.contains(":ai dismiss"), "should list :ai dismiss");
      assertTrue(text.contains(":ai cancel"), "should list :ai cancel");
   }

   @Test
   @DisplayName("AI help lists configuration commands")
   void helpListsConfigCommands() {
      String text = bufferText(HelpSystem.getHelp("ai"));
      assertTrue(text.contains(":ai config"), "should list :ai config");
      assertTrue(text.contains(":ai test"), "should list :ai test");
      assertTrue(text.contains(":ai help"), "should list :ai help");
   }

   @Test
   @DisplayName("AI help lists :set configuration keys")
   void helpListsSetKeys() {
      String text = bufferText(HelpSystem.getHelp("ai"));
      assertTrue(text.contains("ai.provider"), "should list ai.provider");
      assertTrue(text.contains("ai.model"), "should list ai.model");
      assertTrue(text.contains("ai.apikey"), "should list ai.apikey");
      assertTrue(text.contains("ai.maxTokens"), "should list ai.maxTokens");
   }

   @Test
   @DisplayName("Help index lists AI topic")
   void helpIndexListsAi() {
      String text = bufferText(HelpSystem.getHelp("index"));
      assertTrue(text.contains(":help ai"),
         "help index should list :help ai");
   }

   @Test
   @DisplayName("Unknown-topic fallback lists AI")
   void unknownTopicListsAi() {
      String text = bufferText(HelpSystem.getHelp("nonexistent_topic_xyz"));
      assertTrue(text.contains("ai"),
         "unknown topic list should mention 'ai'");
   }

   // ── CopilotProvider Unit Tests ────────────────────────────────

   @Test
   @DisplayName("extractCompletionText with displayText")
   void extractDisplayText() throws AIException {
      String json = "{\"result\":{\"completions\":[{\"displayText\":"
         + "\"hello world\",\"position\":{}}]}}";
      assertEquals("hello world",
         CopilotProvider.extractCompletionText(json));
   }

   @Test
   @DisplayName("extractCompletionText with insertText fallback")
   void extractInsertText() throws AIException {
      String json = "{\"result\":{\"completions\":[{\"insertText\":"
         + "\"foo bar\"}]}}";
      assertEquals("foo bar",
         CopilotProvider.extractCompletionText(json));
   }

   @Test
   @DisplayName("extractCompletionText with no completion")
   void extractNoCompletion() throws AIException {
      String json = "{\"result\":{\"completions\":[]}}";
      assertEquals("",
         CopilotProvider.extractCompletionText(json));
   }

   @Test
   @DisplayName("findAgentPath returns null without legacy extension")
   void findAgentPathNoLegacyExtension() {
      // On this system the legacy github.copilot extension is not
      // installed. findAgentPath should not fall back to cli.js
      // from copilot-chat, which is an unrelated tool.
      String path = CopilotProvider.findAgentPath();
      if (null != path) {
         // If an agent IS found, it must be agent.js, not cli.js
         assertTrue(path.endsWith("agent.js"),
            "findAgentPath should only return agent.js, got: " + path);
      }
      // null is acceptable — means no standalone agent available
   }

   @Test
   @DisplayName("AIConfig.Provider.fromId round-trips")
   void providerFromIdRoundTrip() {
      for (AIConfig.Provider p : AIConfig.Provider.values()) {
         assertEquals(p, AIConfig.Provider.fromId(p.getId()));
      }
   }

   @Test
   @DisplayName("AIConfig.Provider.fromId rejects unknown")
   void providerFromIdRejectsUnknown() {
      assertThrows(IllegalArgumentException.class,
         () -> AIConfig.Provider.fromId("bogus"));
   }
}
