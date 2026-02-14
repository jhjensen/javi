package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * JUnit 5 tests for {@link HelpSystem} — built-in help documentation.
 *
 * <p>Covers getHelp() for each topic and the key bindings list.</p>
 */
class HelpSystemJUnitTest {

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

   @Test
   @DisplayName("getHelp(null) returns index")
   void helpNullReturnsIndex() {
      TextEdit<String> buf = HelpSystem.getHelp(null);
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("JAVI EDITOR HELP"),
         "index should contain title");
      assertTrue(text.contains("QUICK REFERENCE"),
         "index should contain quick reference");
   }

   @Test
   @DisplayName("getHelp('') returns index")
   void helpEmptyReturnsIndex() {
      TextEdit<String> buf = HelpSystem.getHelp("");
      String text = bufferText(buf);
      assertTrue(text.contains("JAVI EDITOR HELP"));
   }

   @Test
   @DisplayName("getHelp('index') returns index")
   void helpIndexTopic() {
      TextEdit<String> buf = HelpSystem.getHelp("index");
      String text = bufferText(buf);
      assertTrue(text.contains("JAVI EDITOR HELP"));
   }

   @Test
   @DisplayName("getHelp('movement') returns movement help")
   void helpMovementTopic() {
      TextEdit<String> buf = HelpSystem.getHelp("movement");
      String text = bufferText(buf);
      assertTrue(text.contains("MOVEMENT COMMANDS"));
      assertTrue(text.contains("BASIC MOVEMENT"));
   }

   @Test
   @DisplayName("getHelp('move') alias works")
   void helpMoveAlias() {
      TextEdit<String> buf = HelpSystem.getHelp("move");
      String text = bufferText(buf);
      assertTrue(text.contains("MOVEMENT COMMANDS"));
   }

   @Test
   @DisplayName("getHelp('editing') returns editing help")
   void helpEditingTopic() {
      TextEdit<String> buf = HelpSystem.getHelp("editing");
      String text = bufferText(buf);
      assertTrue(text.contains("EDITING COMMANDS"));
      assertTrue(text.contains("INSERT MODE"));
   }

   @Test
   @DisplayName("getHelp('search') returns search help")
   void helpSearchTopic() {
      TextEdit<String> buf = HelpSystem.getHelp("search");
      String text = bufferText(buf);
      assertTrue(text.contains("SEARCH"));
   }

   @Test
   @DisplayName("getHelp('files') returns file help")
   void helpFileTopic() {
      TextEdit<String> buf = HelpSystem.getHelp("files");
      String text = bufferText(buf);
      assertTrue(text.contains("FILE"));
   }

   @Test
   @DisplayName("getHelp('ex') returns ex command help")
   void helpExTopic() {
      TextEdit<String> buf = HelpSystem.getHelp("ex");
      String text = bufferText(buf);
      assertTrue(text.contains("EX"));
   }

   @Test
   @DisplayName("getHelp('visual') returns visual mode help")
   void helpVisualTopic() {
      TextEdit<String> buf = HelpSystem.getHelp("visual");
      String text = bufferText(buf);
      assertTrue(text.contains("VISUAL"));
   }

   @Test
   @DisplayName("getHelp('undo') returns undo help")
   void helpUndoTopic() {
      TextEdit<String> buf = HelpSystem.getHelp("undo");
      String text = bufferText(buf);
      assertTrue(text.contains("UNDO"));
   }

   @Test
   @DisplayName("getHelp('window') returns window help")
   void helpWindowTopic() {
      TextEdit<String> buf = HelpSystem.getHelp("window");
      String text = bufferText(buf);
      assertTrue(text.contains("WINDOW"));
   }

   @Test
   @DisplayName("getHelp with unknown topic shows error")
   void helpUnknownTopic() {
      TextEdit<String> buf = HelpSystem.getHelp("xyzzynotreal");
      String text = bufferText(buf);
      assertTrue(text.contains("xyzzynotreal")
         || text.contains("Unknown"),
         "unknown topic should show error or topic name");
   }

   @Test
   @DisplayName("getHelp('lsp') returns LSP help")
   void helpLspTopic() {
      TextEdit<String> buf = HelpSystem.getHelp("lsp");
      String text = bufferText(buf);
      assertTrue(text.contains("LSP"),
         "LSP help should contain LSP header");
      assertTrue(text.contains(":lsp.def"),
         "LSP help should document :lsp.def");
      assertTrue(text.contains(":lsp.ref"),
         "LSP help should document :lsp.ref");
      assertTrue(text.contains(":lsp.hover"),
         "LSP help should document :lsp.hover");
      assertTrue(text.contains(":lsp.comp"),
         "LSP help should document :lsp.comp");
      assertTrue(text.contains(":lsp.diag"),
         "LSP help should document :lsp.diag");
      assertTrue(text.contains(":lsp.status"),
         "LSP help should document :lsp.status");
      assertTrue(text.contains(":lsp.config"),
         "LSP help should document :lsp.config");
      assertTrue(text.contains(":lsp.toggle"),
         "LSP help should document :lsp.toggle");
      assertTrue(text.contains(":lsp.restart"),
         "LSP help should document :lsp.restart");
   }

   @Test
   @DisplayName("getHelp('languageserver') alias works")
   void helpLanguageServerAlias() {
      TextEdit<String> buf = HelpSystem.getHelp("languageserver");
      String text = bufferText(buf);
      assertTrue(text.contains("LSP"));
   }

   @Test
   @DisplayName("help index mentions lsp topic")
   void helpIndexMentionsLsp() {
      TextEdit<String> buf = HelpSystem.getHelp("index");
      String text = bufferText(buf);
      assertTrue(text.contains("lsp"),
         "help index should list the lsp topic");
   }

   @Test
   @DisplayName("getHelp is case-insensitive")
   void helpCaseInsensitive() {
      TextEdit<String> buf = HelpSystem.getHelp("MOVEMENT");
      String text = bufferText(buf);
      assertTrue(text.contains("MOVEMENT COMMANDS"));
   }

   @Test
   @DisplayName("getKeyBindings returns buffer")
   void getKeyBindings() {
      TextEdit<String> buf = HelpSystem.getKeyBindings();
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("KEY BINDINGS"));
   }

   @Test
   @DisplayName("calling getHelp twice returns fresh content")
   void helpCalledTwiceRefreshes() {
      HelpSystem.getHelp("movement");
      TextEdit<String> buf = HelpSystem.getHelp("editing");
      String text = bufferText(buf);
      assertTrue(text.contains("EDITING COMMANDS"));
      assertFalse(text.contains("MOVEMENT COMMANDS"),
         "old topic content should be cleared");
   }

   // ============================================================
   // F20 — Inline binding annotation
   // ============================================================

   @Test
   @DisplayName("getHelp('keybindings') contains keybinding architecture")
   void helpKeybindingsTopic() {
      TextEdit<String> buf = HelpSystem.getHelp("keybindings");
      String text = bufferText(buf);
      assertTrue(text.contains("KEY BINDING ARCHITECTURE"),
         "keybindings topic should contain architecture section");
   }

   @Test
   @DisplayName("help annotation does not corrupt header lines")
   void helpAnnotationPreservesHeaders() {
      TextEdit<String> buf = HelpSystem.getHelp("ex");
      String text = bufferText(buf);
      // Header lines with === or --- should not be annotated
      for (String line : text.split("\n")) {
         if (line.startsWith("===") || line.startsWith("---")) {
            assertFalse(line.contains("["),
               "separator lines should not be annotated: " + line);
         }
      }
   }

   @Test
   @DisplayName("help annotation does not add brackets to non-command text")
   void helpAnnotationIgnoresNonCommands() {
      TextEdit<String> buf = HelpSystem.getHelp("undo");
      String text = bufferText(buf);
      // "UNDO AND REDO" is a header, not a command reference
      assertTrue(text.contains("UNDO AND REDO"),
         "undo help should include header");
      // The header line itself should not be annotated with [...]
      for (String line : text.split("\n")) {
         if (line.contains("UNDO AND REDO")) {
            assertFalse(line.contains("["),
               "header should not be annotated");
         }
      }
   }
}
