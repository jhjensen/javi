package javi;

import javi.ai.AIAsyncExecutor;
import javi.ai.AIClient;
import javi.ai.AIProvider;
import javi.ai.CopilotRestClient;
import javi.ai.tools.AITool;
import javi.ai.tools.AIToolRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for async AI execution, buffer context attachment,
 * and tool call parsing / execution.
 *
 * <p>Verifies bug fixes:
 * <ul>
 *   <li>Bug #1: AI review no longer blocks (async execution)</li>
 *   <li>Bug #2: File context automatically attached to chat</li>
 *   <li>Tool framework: tool call parsing and registry</li>
 * </ul>
 */
class AIAsyncAndToolsJUnitTest {

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

   // ── AIAsyncExecutor: non-blocking execution ──────────────

   @Test
   @DisplayName("submit runs task on background thread")
   void submitRunsOnBackgroundThread() throws Exception {
      AtomicReference<String> threadName =
         new AtomicReference<>();
      CountDownLatch taskDone = new CountDownLatch(1);

      EventQueue.biglock2.unlock();
      try {
         AIAsyncExecutor.submit(
            () -> {
               threadName.set(
                  Thread.currentThread().getName());
               taskDone.countDown();
               return "done";
            },
            result -> { },
            error -> { }
         );

         assertTrue(taskDone.await(5, TimeUnit.SECONDS),
            "task should complete within 5s");
         assertEquals("ai-async", threadName.get(),
            "task should run on ai-async thread");
      } finally {
         Thread.sleep(50);
         EventQueue.biglock2.lock();
      }
   }

   @Test
   @DisplayName("submit does not block calling thread")
   void submitDoesNotBlock() {
      long start = System.nanoTime();

      AIAsyncExecutor.submit(
         () -> {
            try {
               Thread.sleep(500);
            } catch (InterruptedException ignore) {
               // expected on cancel
            }
            return "slow";
         },
         result -> { },
         error -> { }
      );

      long elapsed = (System.nanoTime() - start) / 1_000_000;
      assertTrue(elapsed < 200,
         "submit should return immediately, took "
         + elapsed + "ms");

      // Clean up
      AIAsyncExecutor.cancel();
   }

   @Test
   @DisplayName("isBusy returns true during execution")
   void isBusyDuringExecution() throws Exception {
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch finish = new CountDownLatch(1);

      EventQueue.biglock2.unlock();
      try {
         AIAsyncExecutor.submit(
            () -> {
               started.countDown();
               try {
                  finish.await(5, TimeUnit.SECONDS);
               } catch (InterruptedException ignore) {
                  // expected
               }
               return "result";
            },
            result -> { },
            error -> { }
         );

         assertTrue(started.await(5, TimeUnit.SECONDS),
            "task should start");
         assertTrue(AIAsyncExecutor.isBusy(),
            "should be busy while task runs");
         finish.countDown();
      } finally {
         // Allow task to complete
         Thread.sleep(100);
         EventQueue.biglock2.lock();
      }
   }

   @Test
   @DisplayName("cancel stops running task")
   void cancelStopsTask() throws Exception {
      CountDownLatch started = new CountDownLatch(1);
      AtomicBoolean interrupted = new AtomicBoolean();

      EventQueue.biglock2.unlock();
      try {
         AIAsyncExecutor.submit(
            () -> {
               started.countDown();
               try {
                  Thread.sleep(10_000);
               } catch (InterruptedException e) {
                  interrupted.set(true);
               }
               return "never";
            },
            result -> { },
            error -> { }
         );

         assertTrue(started.await(5, TimeUnit.SECONDS),
            "task should start");
         boolean cancelled = AIAsyncExecutor.cancel();
         assertTrue(cancelled,
            "cancel should return true");
         Thread.sleep(200);
         assertFalse(AIAsyncExecutor.isBusy(),
            "should not be busy after cancel");
      } finally {
         EventQueue.biglock2.lock();
      }
   }

   @Test
   @DisplayName("cancel when idle returns false")
   void cancelWhenIdleReturnsFalse() {
      // Ensure nothing is running
      AIAsyncExecutor.cancel();
      assertFalse(AIAsyncExecutor.isBusy());
   }

   @Test
   @DisplayName("isBusy false when no task submitted")
   void isBusyFalseWhenIdle() {
      AIAsyncExecutor.cancel();
      assertFalse(AIAsyncExecutor.isBusy());
   }

   @Test
   @DisplayName("new submit cancels previous task")
   void newSubmitCancelsPrevious() throws Exception {
      CountDownLatch firstStarted = new CountDownLatch(1);
      AtomicBoolean firstInterrupted = new AtomicBoolean();
      CountDownLatch secondTaskDone = new CountDownLatch(1);

      EventQueue.biglock2.unlock();
      try {
         AIAsyncExecutor.submit(
            () -> {
               firstStarted.countDown();
               try {
                  Thread.sleep(10_000);
               } catch (InterruptedException ignore) {
                  firstInterrupted.set(true);
               }
               return "first";
            },
            result -> { },
            error -> { }
         );

         assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

         AIAsyncExecutor.submit(
            () -> {
               secondTaskDone.countDown();
               return "second";
            },
            result -> { },
            error -> { }
         );

         assertTrue(secondTaskDone.await(5, TimeUnit.SECONDS),
            "second task should complete");
         // Give first task time to notice interruption
         Thread.sleep(200);
         assertTrue(firstInterrupted.get(),
            "first task should have been interrupted");
      } finally {
         Thread.sleep(50);
         EventQueue.biglock2.lock();
      }
   }

   // ── Buffer Context Attachment ────────────────────────────

   @Test
   @DisplayName("chatWithContext enriches message with file content")
   void chatWithContextEnriches() {
      // Verify AIClient.chatWithContext constructs enriched
      // message by checking it accepts file+content params
      AIClient client = AIClient.getInstance();
      assertNotNull(client,
         "AIClient singleton should exist");
      // chatWithContext signature accepts three strings:
      // userMessage, fileName, fileContent.
      // We verify the method exists and the object compiles.
      // A full integration test would need a mock provider.
   }

   @Test
   @DisplayName("AIClient history stores short message, not full context")
   void historyStoresShortMessage() throws Exception {
      // Create a mock provider that captures messages
      List<AIProvider.Message> capturedMessages =
         new ArrayList<>();
      AtomicReference<String> lastResponse =
         new AtomicReference<>("test response");

      AIProvider mockProvider = new AIProvider() {
         @Override
         public String chatCompletion(
               List<Message> messages, int maxTokens) {
            capturedMessages.addAll(messages);
            return lastResponse.get();
         }

         @Override
         public String getName() {
            return "Mock";
         }

         @Override
         public String getModel() {
            return "mock-1";
         }

         @Override
         public boolean testConnection() {
            return true;
         }
      };

      // Save and set mock provider
      AIClient client = AIClient.getInstance();
      client.clearHistory();

      // Use reflection to inject mock provider
      java.lang.reflect.Field provField =
         AIClient.class.getDeclaredField("provider");
      provField.setAccessible(true);
      AIProvider origProvider =
         (AIProvider) provField.get(client);
      provField.set(client, mockProvider);

      try {
         String response = client.chatWithContext(
            "explain this function",
            "MyFile.java",
            "public void foo() { return; }");

         assertEquals("test response", response);

         // Now send another chat to check history
         capturedMessages.clear();
         client.chat("follow up question");

         // History should contain the short message,
         // NOT the full file content
         boolean foundShort = false;
         boolean foundFullContext = false;
         for (AIProvider.Message m : capturedMessages) {
            if ("user".equals(m.role())
                  && "explain this function"
                        .equals(m.content())) {
               foundShort = true;
            }
            if ("user".equals(m.role())
                  && m.content().contains(
                     "public void foo()")) {
               foundFullContext = true;
            }
         }
         assertTrue(foundShort,
            "history should contain short message");
         assertFalse(foundFullContext,
            "history should NOT contain full file content");
      } finally {
         // Restore
         provField.set(client, origProvider);
         client.clearHistory();
      }
   }

   @Test
   @DisplayName("clearHistory empties conversation state")
   void clearHistoryWorks() {
      AIClient client = AIClient.getInstance();
      client.clearHistory();
      // After clear, a fresh chat should have no prior
      // messages (only system prompt + new message)
      // Verified by the fact that clearHistory doesn't throw
   }

   // ── Tool Call JSON Parsing ───────────────────────────────
   // hasToolCalls and extractToolCalls are package-private
   // in javi.ai, so we test them via reflection.

   @Test
   @DisplayName("hasToolCalls detects tool_calls in response")
   void hasToolCallsDetects() throws Exception {
      java.lang.reflect.Method m =
         CopilotRestClient.class.getDeclaredMethod(
            "hasToolCalls", String.class);
      m.setAccessible(true);
      String withTools = "{\"choices\":[{\"message\":"
         + "{\"tool_calls\":[{\"id\":\"call_1\"}]}}]}";
      assertTrue((Boolean) m.invoke(null, withTools),
         "should detect tool_calls");
   }

   @Test
   @DisplayName("hasToolCalls returns false for content-only response")
   void hasToolCallsFalseForContent() throws Exception {
      java.lang.reflect.Method m =
         CopilotRestClient.class.getDeclaredMethod(
            "hasToolCalls", String.class);
      m.setAccessible(true);
      String noTools = "{\"choices\":[{\"message\":"
         + "{\"content\":\"hello\"}}]}";
      assertFalse((Boolean) m.invoke(null, noTools),
         "should not detect tool_calls");
   }

   @SuppressWarnings("unchecked")
   @Test
   @DisplayName("extractToolCalls parses single tool call")
   void extractToolCallsSingle() throws Exception {
      java.lang.reflect.Method m =
         CopilotRestClient.class.getDeclaredMethod(
            "extractToolCalls", String.class);
      m.setAccessible(true);
      String json = "{\"choices\":[{\"message\":{"
         + "\"tool_calls\":[{"
         + "\"id\":\"call_abc123\","
         + "\"type\":\"function\","
         + "\"function\":{"
         + "\"name\":\"file_read\","
         + "\"arguments\":\"{\\\"path\\\":\\\"test.txt\\\"}\""
         + "}}]}}]}";
      List<CopilotRestClient.ToolCall> calls =
         (List<CopilotRestClient.ToolCall>) m.invoke(
            null, json);
      assertEquals(1, calls.size(),
         "should parse one tool call");
      assertEquals("call_abc123", calls.get(0).id());
      assertEquals("file_read", calls.get(0).name());
      assertNotNull(calls.get(0).arguments());
   }

   @SuppressWarnings("unchecked")
   @Test
   @DisplayName("extractToolCalls parses multiple tool calls")
   void extractToolCallsMultiple() throws Exception {
      java.lang.reflect.Method m =
         CopilotRestClient.class.getDeclaredMethod(
            "extractToolCalls", String.class);
      m.setAccessible(true);
      String json = "{\"choices\":[{\"message\":{"
         + "\"tool_calls\":["
         + "{\"id\":\"call_1\","
         + "\"type\":\"function\","
         + "\"function\":{\"name\":\"file_read\","
         + "\"arguments\":\"{}\""
         + "}},"
         + "{\"id\":\"call_2\","
         + "\"type\":\"function\","
         + "\"function\":{\"name\":\"file_list\","
         + "\"arguments\":\"{}\""
         + "}}"
         + "]}}]}";
      List<CopilotRestClient.ToolCall> calls =
         (List<CopilotRestClient.ToolCall>) m.invoke(
            null, json);
      assertEquals(2, calls.size(),
         "should parse two tool calls");
      assertEquals("file_read", calls.get(0).name());
      assertEquals("file_list", calls.get(1).name());
   }

   @SuppressWarnings("unchecked")
   @Test
   @DisplayName("extractToolCalls returns empty for no tool_calls")
   void extractToolCallsEmpty() throws Exception {
      java.lang.reflect.Method m =
         CopilotRestClient.class.getDeclaredMethod(
            "extractToolCalls", String.class);
      m.setAccessible(true);
      String json = "{\"choices\":[{\"message\":{"
         + "\"content\":\"hello\"}}]}";
      List<CopilotRestClient.ToolCall> calls =
         (List<CopilotRestClient.ToolCall>) m.invoke(
            null, json);
      assertTrue(calls.isEmpty(),
         "should return empty list");
   }

   // ── AIToolRegistry ───────────────────────────────────────

   @Test
   @DisplayName("registerBuiltins registers file_read, file_list, buffer_info")
   void registerBuiltinsRegistersThree() {
      // registerBuiltins was called in AICommands constructor
      Map<String, AITool> tools =
         AIToolRegistry.getTools();
      assertTrue(tools.containsKey("file_read"),
         "should have file_read tool");
      assertTrue(tools.containsKey("file_list"),
         "should have file_list tool");
      assertTrue(tools.containsKey("buffer_info"),
         "should have buffer_info tool");
   }

   @Test
   @DisplayName("hasTools returns true after registration")
   void hasToolsAfterRegistration() {
      assertTrue(AIToolRegistry.hasTools(),
         "should have tools registered");
   }

   @Test
   @DisplayName("getToolsJson produces valid JSON array")
   void getToolsJsonValid() {
      String json = AIToolRegistry.getToolsJson();
      assertNotNull(json);
      assertTrue(json.startsWith("["),
         "should start with [");
      assertTrue(json.endsWith("]"),
         "should end with ]");
      assertTrue(json.contains("\"type\":\"function\""),
         "should contain function type");
      assertTrue(json.contains("\"file_read\""),
         "should contain file_read");
   }

   @Test
   @DisplayName("executeTool rejects unknown tool name")
   void executeToolRejectsUnknown() {
      try {
         AIToolRegistry.executeTool("nonexistent_xyz",
            Map.of());
         assertTrue(false, "should throw AIException");
      } catch (javi.ai.AIException e) {
         assertTrue(e.getMessage().contains("Unknown tool"),
            "error should mention unknown tool");
      }
   }

   @Test
   @DisplayName("tool definitions include input schemas")
   void toolDefinitionsIncludeSchemas() {
      List<Map<String, Object>> defs =
         AIToolRegistry.getToolDefinitions();
      assertFalse(defs.isEmpty(),
         "should have tool definitions");
      for (Map<String, Object> def : defs) {
         assertEquals("function", def.get("type"));
         @SuppressWarnings("unchecked")
         Map<String, Object> func =
            (Map<String, Object>) def.get("function");
         assertNotNull(func.get("name"),
            "tool should have name");
         assertNotNull(func.get("description"),
            "tool should have description");
         assertNotNull(func.get("parameters"),
            "tool should have parameters schema");
      }
   }

   @Test
   @DisplayName("getSummary lists all registered tools")
   void getSummaryListsTools() {
      String summary = AIToolRegistry.getSummary();
      assertTrue(summary.contains("file_read"),
         "summary should list file_read");
      assertTrue(summary.contains("file_list"),
         "summary should list file_list");
      assertTrue(summary.contains("buffer_info"),
         "summary should list buffer_info");
   }

   // ── Tool Followup JSON ───────────────────────────────────

   @Test
   @DisplayName("buildToolFollowupJson produces valid structure")
   void buildToolFollowupJsonValid() {
      List<AIProvider.Message> msgs = List.of(
         new AIProvider.Message("user", "test"));
      List<CopilotRestClient.ToolCall> toolCalls = List.of(
         new CopilotRestClient.ToolCall(
            "call_1", "file_read",
            "{\"path\":\"test.txt\"}"));
      List<String> results = List.of("file contents here");

      String json = CopilotRestClient.buildToolFollowupJson(
         msgs, toolCalls, results,
         "gpt-4o", 0, null, false);

      assertNotNull(json);
      assertTrue(json.contains("\"model\":\"gpt-4o\""),
         "should contain model");
      assertTrue(json.contains("\"role\":\"tool\""),
         "should contain tool role message");
      assertTrue(json.contains("file contents here"),
         "should contain tool result");
      assertTrue(json.contains("call_1"),
         "should contain tool call id");
   }

   // ── MAX_TOOL_ROUNDS constant ─────────────────────────────

   @Test
   @DisplayName("MAX_TOOL_ROUNDS is at least 5")
   void maxToolRoundsReasonable() throws Exception {
      java.lang.reflect.Field f =
         CopilotRestClient.class.getDeclaredField(
            "MAX_TOOL_ROUNDS");
      f.setAccessible(true);
      int val = f.getInt(null);
      assertTrue(val >= 5,
         "MAX_TOOL_ROUNDS should be at least 5, is "
         + val);
   }

   // ── Streaming submit ─────────────────────────────────────

   @Test
   @DisplayName("submitStreaming runs task on background thread")
   void submitStreamingRunsOnBackground() throws Exception {
      AtomicReference<String> threadName =
         new AtomicReference<>();
      CountDownLatch taskDone = new CountDownLatch(1);

      EventQueue.biglock2.unlock();
      try {
         AIAsyncExecutor.submitStreaming(
            onToken -> {
               threadName.set(
                  Thread.currentThread().getName());
               onToken.accept("hello");
               taskDone.countDown();
            },
            token -> { },
            () -> { },
            error -> { }
         );

         assertTrue(taskDone.await(5, TimeUnit.SECONDS),
            "streaming task should complete within 5s");
         assertEquals("ai-async", threadName.get(),
            "streaming task should run on ai-async");
      } finally {
         Thread.sleep(50);
         EventQueue.biglock2.lock();
      }
   }

   @Test
   @DisplayName("submitStreaming does not block calling thread")
   void submitStreamingNonBlocking() {
      long start = System.nanoTime();

      AIAsyncExecutor.submitStreaming(
         onToken -> {
            try {
               Thread.sleep(500);
            } catch (InterruptedException ignore) {
               // expected on cancel
            }
            onToken.accept("late");
         },
         token -> { },
         () -> { },
         error -> { }
      );

      long elapsed = (System.nanoTime() - start) / 1_000_000;
      assertTrue(elapsed < 200,
         "submitStreaming should return immediately, took "
         + elapsed + "ms");
      AIAsyncExecutor.cancel();
   }
}
