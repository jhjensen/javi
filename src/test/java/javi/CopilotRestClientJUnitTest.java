package javi;

import javi.ai.AIConfig;
import javi.ai.AIProvider;
import javi.ai.CopilotProvider;
import javi.ai.CopilotRestClient;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for CopilotRestClient JSON parsing, token I/O,
 * and CopilotProvider integration.
 */
class CopilotRestClientJUnitTest {

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

   // ── extractJsonField ─────────────────────────────────────

   @Test
   @DisplayName("extractJsonField: quoted string value")
   void extractQuotedString() {
      String json =
         "{\"oauth_token\":\"gho_abc123\"}";
      assertEquals("gho_abc123",
         CopilotRestClient.extractJsonField(
            json, "oauth_token"));
   }

   @Test
   @DisplayName("extractJsonField: quoted with space")
   void extractQuotedWithSpace() {
      String json =
         "{\"oauth_token\": \"gho_xyz\"}";
      assertEquals("gho_xyz",
         CopilotRestClient.extractJsonField(
            json, "oauth_token"));
   }

   @Test
   @DisplayName("extractJsonField: numeric value")
   void extractNumericValue() {
      String json =
         "{\"expires_at\":1700000000}";
      assertEquals("1700000000",
         CopilotRestClient.extractJsonField(
            json, "expires_at"));
   }

   @Test
   @DisplayName("extractJsonField: nested field")
   void extractNestedField() {
      String json = "{\"github.com\":"
         + "{\"oauth_token\":\"gho_nested\"}}";
      assertEquals("gho_nested",
         CopilotRestClient.extractJsonField(
            json, "oauth_token"));
   }

   @Test
   @DisplayName("extractJsonField: missing field")
   void extractMissingField() {
      String json = "{\"other\":\"value\"}";
      assertNull(CopilotRestClient.extractJsonField(
         json, "oauth_token"));
   }

   @Test
   @DisplayName("extractJsonField: escaped chars in value")
   void extractEscapedValue() {
      String json =
         "{\"token\":\"abc\\\"def\"}";
      assertEquals("abc\\\"def",
         CopilotRestClient.extractJsonField(
            json, "token"));
   }

   @Test
   @DisplayName("extractJsonField: boolean value")
   void extractBooleanValue() {
      String json = "{\"chat_enabled\":true}";
      assertEquals("true",
         CopilotRestClient.extractJsonField(
            json, "chat_enabled"));
   }

   // ── parseModelList ───────────────────────────────────────

   @Test
   @DisplayName("parseModelList: standard response")
   void parseModelListStandard() {
      String json = "{\"data\":["
         + "{\"id\":\"gpt-4o\",\"object\":\"model\"},"
         + "{\"id\":\"gpt-3.5-turbo\","
         + "\"object\":\"model\"}"
         + "]}";
      List<String> models =
         CopilotRestClient.parseModelList(json);
      assertEquals(2, models.size());
      assertTrue(models.contains("gpt-4o"));
      assertTrue(models.contains("gpt-3.5-turbo"));
   }

   @Test
   @DisplayName("parseModelList: empty data")
   void parseModelListEmpty() {
      String json = "{\"data\":[]}";
      List<String> models =
         CopilotRestClient.parseModelList(json);
      assertTrue(models.isEmpty());
   }

   @Test
   @DisplayName("parseModelList: skips 'model' object id")
   void parseModelListSkipsObjectId() {
      String json = "{\"data\":["
         + "{\"id\":\"gpt-4o\","
         + "\"object\":\"model\","
         + "\"id\":\"model\"}"
         + "]}";
      List<String> models =
         CopilotRestClient.parseModelList(json);
      // "model" should be filtered out
      assertTrue(models.contains("gpt-4o"));
      assertFalse(models.contains("model"));
   }

   // ── buildChatJson ────────────────────────────────────────

   @Test
   @DisplayName("buildChatJson: basic message")
   void buildChatJsonBasic() {
      List<AIProvider.Message> msgs = List.of(
         new AIProvider.Message("user", "hello"));
      String json = CopilotRestClient.buildChatJson(
         msgs, "gpt-4o", 0);
      assertTrue(json.contains("\"model\":\"gpt-4o\""));
      assertTrue(json.contains("\"role\":\"user\""));
      assertTrue(json.contains("\"content\":\"hello\""));
      assertFalse(json.contains("max_tokens"));
   }

   @Test
   @DisplayName("buildChatJson: with max_tokens")
   void buildChatJsonWithMaxTokens() {
      List<AIProvider.Message> msgs = List.of(
         new AIProvider.Message("user", "hi"));
      String json = CopilotRestClient.buildChatJson(
         msgs, "gpt-4o", 100);
      assertTrue(json.contains("\"max_tokens\":100"));
   }

   @Test
   @DisplayName("buildChatJson: multiple messages")
   void buildChatJsonMultiple() {
      List<AIProvider.Message> msgs = List.of(
         new AIProvider.Message("system", "be helpful"),
         new AIProvider.Message("user", "question"),
         new AIProvider.Message("assistant", "answer"));
      String json = CopilotRestClient.buildChatJson(
         msgs, "gpt-4o", 0);
      assertTrue(json.contains("\"role\":\"system\""));
      assertTrue(json.contains("\"role\":\"user\""));
      assertTrue(json.contains("\"role\":\"assistant\""));
   }

   @Test
   @DisplayName("buildChatJson: escapes special chars")
   void buildChatJsonEscapes() {
      List<AIProvider.Message> msgs = List.of(
         new AIProvider.Message("user",
            "line1\nline2\"quoted\""));
      String json = CopilotRestClient.buildChatJson(
         msgs, "gpt-4o", 0);
      assertTrue(json.contains("\\n"));
      assertTrue(json.contains("\\\"quoted\\\""));
   }

   // ── Token I/O ────────────────────────────────────────────

   @Test
   @DisplayName("readTokenFromAppsJson: valid file")
   void readTokenValid(@TempDir Path tmpDir)
         throws IOException {
      Path apps = tmpDir.resolve("apps.json");
      String json = "{\"github.com\":"
         + "{\"user\":\"test\","
         + "\"oauth_token\":\"gho_test123\"}}";
      Files.writeString(apps, json,
         StandardCharsets.UTF_8);
      String token =
         CopilotRestClient.readTokenFromAppsJson(apps);
      assertEquals("gho_test123", token);
   }

   @Test
   @DisplayName("readTokenFromAppsJson: missing file")
   void readTokenMissing(@TempDir Path tmpDir) {
      Path apps = tmpDir.resolve("nonexistent.json");
      assertNull(
         CopilotRestClient.readTokenFromAppsJson(apps));
   }

   @Test
   @DisplayName("readTokenFromAppsJson: no token field")
   void readTokenNoField(@TempDir Path tmpDir)
         throws IOException {
      Path apps = tmpDir.resolve("apps.json");
      Files.writeString(apps, "{\"github.com\":{}}",
         StandardCharsets.UTF_8);
      assertNull(
         CopilotRestClient.readTokenFromAppsJson(apps));
   }

   @Test
   @DisplayName("saveOAuthToken creates file")
   void saveTokenCreatesFile(@TempDir Path tmpDir)
         throws IOException {
      String origHome =
         System.getProperty("user.home");
      try {
         System.setProperty("user.home",
            tmpDir.toString());
         CopilotRestClient.saveOAuthToken("gho_saved");
         Path saved = tmpDir.resolve(
            CopilotRestClient.APPS_JSON_PATH);
         assertTrue(Files.exists(saved));
         String content = Files.readString(saved,
            StandardCharsets.UTF_8);
         assertTrue(
            content.contains("gho_saved"));
      } finally {
         System.setProperty("user.home", origHome);
      }
   }

   // ── CopilotRestClient construction ───────────────────────

   @Test
   @DisplayName("CopilotRestClient(token) hasToken")
   void constructorWithToken() {
      CopilotRestClient client =
         new CopilotRestClient("gho_test");
      assertTrue(client.hasToken());
   }

   @Test
   @DisplayName("CopilotRestClient(null) no token")
   void constructorWithNull() {
      CopilotRestClient client =
         new CopilotRestClient(null);
      assertFalse(client.hasToken());
   }

   @Test
   @DisplayName("CopilotRestClient(empty) no token")
   void constructorWithEmpty() {
      CopilotRestClient client =
         new CopilotRestClient("");
      assertFalse(client.hasToken());
   }

   // ── CopilotProvider ──────────────────────────────────────

   @Test
   @DisplayName("CopilotProvider with token reports name")
   void providerName() {
      CopilotRestClient client =
         new CopilotRestClient("gho_test");
      CopilotProvider cp =
         new CopilotProvider(client, "gpt-4o");
      assertEquals("GitHub Copilot", cp.getName());
   }

   @Test
   @DisplayName("CopilotProvider returns model")
   void providerModel() {
      CopilotRestClient client =
         new CopilotRestClient("gho_test");
      CopilotProvider cp =
         new CopilotProvider(client, "gpt-4o");
      assertEquals("gpt-4o", cp.getModel());
   }

   @Test
   @DisplayName("CopilotProvider exposes rest client")
   void providerRestClient() {
      CopilotRestClient client =
         new CopilotRestClient("gho_test");
      CopilotProvider cp =
         new CopilotProvider(client, "gpt-4o");
      assertSame(client, cp.getRestClient());
   }

   // ── AIConfig copilot defaults ────────────────────────────

   @Test
   @DisplayName("Copilot default model is gpt-4.1")
   void copilotDefaultModel() {
      AIConfig config = AIConfig.getInstance();
      String origModel = config.getModel();
      AIConfig.Provider origProvider =
         config.getProvider();
      try {
         config.setProvider("copilot");
         config.setModel(null);
         assertEquals("gpt-4.1", config.getModel());
      } finally {
         config.setProvider(origProvider.getId());
         if (null != origModel)
            config.setModel(origModel);
      }
   }

   // ── Command registration ─────────────────────────────────

   @Test
   @DisplayName("'ai.auth' command is registered")
   void aiAuthCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("ai.auth"),
         "'ai.auth' should be registered");
   }

   @Test
   @DisplayName("'ai.models' command is registered")
   void aiModelsCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("ai.models"),
         "'ai.models' should be registered");
   }
}
