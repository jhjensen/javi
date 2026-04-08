package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Extended coverage for {@link HelpSystem} — topics not covered
 * by {@link HelpSystemJUnitTest}: shell, diredit, filelist,
 * directory, folding, plus getTopics(), getContextBindings(),
 * getFilteredBindings(), and all topic aliases.
 */
class HelpSystemCoverageJUnitTest {

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

   // ================================================================
   // Untested topics
   // ================================================================

   @Nested
   @DisplayName("Shell topic")
   class ShellTopicTests {

      @Test
      @DisplayName("getHelp('shell') returns shell help")
      void helpShellTopic() {
         TextEdit<String> buf = HelpSystem.getHelp("shell");
         String text = bufferText(buf);
         assertTrue(text.contains("SHELL"),
            "shell help should contain SHELL header");
         assertTrue(text.contains("TERMINAL") || text.contains("VT100")
            || text.contains("STARTING A SHELL"),
            "shell help should contain terminal content");
      }

      @Test
      @DisplayName("getHelp('terminal') alias works")
      void helpTerminalAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("terminal");
         String text = bufferText(buf);
         assertTrue(text.contains("SHELL"));
      }

      @Test
      @DisplayName("getHelp('vt100') alias works")
      void helpVt100Alias() {
         TextEdit<String> buf = HelpSystem.getHelp("vt100");
         String text = bufferText(buf);
         assertTrue(text.contains("SHELL"));
      }
   }

   @Nested
   @DisplayName("DirEdit topic")
   class DirEditTopicTests {

      @Test
      @DisplayName("getHelp('diredit') returns diredit help")
      void helpDirEditTopic() {
         TextEdit<String> buf = HelpSystem.getHelp("diredit");
         String text = bufferText(buf);
         assertTrue(text.contains("DIRECTORY EDITOR")
            || text.contains("DirEdit"),
            "diredit help should contain directory editor content");
      }

      @Test
      @DisplayName("diredit help has navigation section")
      void helpDirEditNavigation() {
         TextEdit<String> buf = HelpSystem.getHelp("diredit");
         String text = bufferText(buf);
         assertTrue(text.contains("NAVIGATION"),
            "diredit help should contain navigation section");
      }
   }

   @Nested
   @DisplayName("FileList topic")
   class FileListTopicTests {

      @Test
      @DisplayName("getHelp('filelist') returns filelist help")
      void helpFileListTopic() {
         TextEdit<String> buf = HelpSystem.getHelp("filelist");
         String text = bufferText(buf);
         assertTrue(text.contains("FILE LIST"),
            "filelist help should contain FILE LIST header");
      }

      @Test
      @DisplayName("getHelp('files-list') alias works")
      void helpFilesListAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("files-list");
         String text = bufferText(buf);
         assertTrue(text.contains("FILE LIST"));
      }
   }

   @Nested
   @DisplayName("Directory topic")
   class DirectoryTopicTests {

      @Test
      @DisplayName("getHelp('directory') returns directory help")
      void helpDirectoryTopic() {
         TextEdit<String> buf = HelpSystem.getHelp("directory");
         String text = bufferText(buf);
         assertTrue(text.contains("DIRECTORY"),
            "directory help should contain DIRECTORY header");
      }

      @Test
      @DisplayName("getHelp('dir') alias works")
      void helpDirAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("dir");
         String text = bufferText(buf);
         assertTrue(text.contains("DIRECTORY"));
      }

      @Test
      @DisplayName("getHelp('dirlist') alias works")
      void helpDirListAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("dirlist");
         String text = bufferText(buf);
         assertTrue(text.contains("DIRECTORY"));
      }
   }

   @Nested
   @DisplayName("Folding topic")
   class FoldingTopicTests {

      @Test
      @DisplayName("getHelp('folding') returns folding help")
      void helpFoldingTopic() {
         TextEdit<String> buf = HelpSystem.getHelp("folding");
         String text = bufferText(buf);
         assertTrue(text.contains("FOLDING"),
            "folding help should contain FOLDING header");
      }

      @Test
      @DisplayName("getHelp('fold') alias works")
      void helpFoldAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("fold");
         String text = bufferText(buf);
         assertTrue(text.contains("FOLDING"));
      }

      @Test
      @DisplayName("getHelp('folds') alias works")
      void helpFoldsAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("folds");
         String text = bufferText(buf);
         assertTrue(text.contains("FOLDING"));
      }

      @Test
      @DisplayName("folding help contains zo/zc commands")
      void helpFoldingContainsCommands() {
         TextEdit<String> buf = HelpSystem.getHelp("folding");
         String text = bufferText(buf);
         assertTrue(text.contains("zo") || text.contains("zc"),
            "folding help should document fold open/close");
      }
   }

   // ================================================================
   // Additional alias coverage
   // ================================================================

   @Nested
   @DisplayName("Additional aliases")
   class AliasTests {

      @Test
      @DisplayName("getHelp('motion') → movement")
      void motionAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("motion");
         String text = bufferText(buf);
         assertTrue(text.contains("MOVEMENT COMMANDS"));
      }

      @Test
      @DisplayName("getHelp('edit') → editing")
      void editAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("edit");
         String text = bufferText(buf);
         assertTrue(text.contains("EDITING COMMANDS"));
      }

      @Test
      @DisplayName("getHelp('find') → search")
      void findAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("find");
         String text = bufferText(buf);
         assertTrue(text.contains("SEARCH"));
      }

      @Test
      @DisplayName("getHelp('buffer') → files")
      void bufferAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("buffer");
         String text = bufferText(buf);
         assertTrue(text.contains("FILE"));
      }

      @Test
      @DisplayName("getHelp('buffers') → files")
      void buffersAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("buffers");
         String text = bufferText(buf);
         assertTrue(text.contains("FILE"));
      }

      @Test
      @DisplayName("getHelp('command') → ex")
      void commandAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("command");
         String text = bufferText(buf);
         assertTrue(text.contains("EX"));
      }

      @Test
      @DisplayName("getHelp('commands') → ex")
      void commandsAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("commands");
         String text = bufferText(buf);
         assertTrue(text.contains("EX"));
      }

      @Test
      @DisplayName("getHelp('colon') → ex")
      void colonAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("colon");
         String text = bufferText(buf);
         assertTrue(text.contains("EX"));
      }

      @Test
      @DisplayName("getHelp('mark') → visual")
      void markAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("mark");
         String text = bufferText(buf);
         assertTrue(text.contains("VISUAL"));
      }

      @Test
      @DisplayName("getHelp('selection') → visual")
      void selectionAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("selection");
         String text = bufferText(buf);
         assertTrue(text.contains("VISUAL"));
      }

      @Test
      @DisplayName("getHelp('redo') → undo")
      void redoAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("redo");
         String text = bufferText(buf);
         assertTrue(text.contains("UNDO"));
      }

      @Test
      @DisplayName("getHelp('screen') → window")
      void screenAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("screen");
         String text = bufferText(buf);
         assertTrue(text.contains("WINDOW"));
      }

      @Test
      @DisplayName("getHelp('scroll') → window")
      void scrollAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("scroll");
         String text = bufferText(buf);
         assertTrue(text.contains("WINDOW"));
      }

      @Test
      @DisplayName("getHelp('keymap') → keybindings")
      void keymapAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("keymap");
         String text = bufferText(buf);
         assertTrue(text.contains("KEY BINDING ARCHITECTURE"));
      }

      @Test
      @DisplayName("getHelp('bindings') → keybindings")
      void bindingsAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("bindings");
         String text = bufferText(buf);
         assertTrue(text.contains("KEY BINDING ARCHITECTURE"));
      }

      @Test
      @DisplayName("getHelp('keys') → keybindings")
      void keysAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("keys");
         String text = bufferText(buf);
         assertTrue(text.contains("KEY BINDING ARCHITECTURE"));
      }

      @Test
      @DisplayName("getHelp('help') → index")
      void helpAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("help");
         String text = bufferText(buf);
         assertTrue(text.contains("JAVI EDITOR HELP"));
      }

      @Test
      @DisplayName("getHelp('file') → files")
      void fileAlias() {
         TextEdit<String> buf = HelpSystem.getHelp("file");
         String text = bufferText(buf);
         assertTrue(text.contains("FILE"));
      }
   }

   // ================================================================
   // getTopics
   // ================================================================

   @Test
   @DisplayName("getTopics returns non-empty array")
   void getTopicsNotEmpty() {
      String[] topics = HelpSystem.getTopics();
      assertNotNull(topics);
      assertTrue(topics.length > 0, "should have at least one topic");
   }

   @Test
   @DisplayName("getTopics contains known topics")
   void getTopicsContainsKnownTopics() {
      String[] topics = HelpSystem.getTopics();
      boolean hasIndex = false;
      boolean hasMovement = false;
      boolean hasFolding = false;
      for (String t : topics) {
         if ("index".equals(t)) hasIndex = true;
         if ("movement".equals(t)) hasMovement = true;
         if ("folding".equals(t)) hasFolding = true;
      }
      assertTrue(hasIndex, "topics should include 'index'");
      assertTrue(hasMovement, "topics should include 'movement'");
      assertTrue(hasFolding, "topics should include 'folding'");
   }

   // ================================================================
   // getFilteredBindings
   // ================================================================

   @Test
   @DisplayName("getFilteredBindings for unknown keymap")
   void filteredBindingsUnknown() {
      TextEdit<String> buf =
         HelpSystem.getFilteredBindings("nonexistent_keymap_xyz");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("Unknown keymap")
         || text.contains("nonexistent_keymap_xyz"),
         "unknown keymap should produce error message");
   }

   @Test
   @DisplayName("getFilteredBindings for 'normal' keymap")
   void filteredBindingsNormal() {
      TextEdit<String> buf = HelpSystem.getFilteredBindings("normal");
      assertNotNull(buf);
      String text = bufferText(buf);
      assertTrue(text.contains("KEY BINDINGS: normal")
         || text.contains("normal"),
         "normal keymap should appear in output");
   }

   // ================================================================
   // Topic content validation
   // ================================================================

   @Test
   @DisplayName("shell help mentions F8")
   void shellHelpMentionsF8() {
      TextEdit<String> buf = HelpSystem.getHelp("shell");
      String text = bufferText(buf);
      assertTrue(text.contains("F8"),
         "shell help should mention F8 key");
   }

   @Test
   @DisplayName("shell help mentions :shells command")
   void shellHelpMentionsShells() {
      TextEdit<String> buf = HelpSystem.getHelp("shell");
      String text = bufferText(buf);
      assertTrue(text.contains(":shells"),
         "shell help should mention :shells command");
   }

   @Test
   @DisplayName("diredit help mentions file operations")
   void dirEditHelpMentionsOperations() {
      TextEdit<String> buf = HelpSystem.getHelp("diredit");
      String text = bufferText(buf);
      assertTrue(text.contains("FILE OPERATIONS")
         || text.contains("Delete"),
         "diredit help should mention file operations");
   }

   @Test
   @DisplayName("filelist help mentions overlay keymap")
   void fileListHelpMentionsOverlay() {
      TextEdit<String> buf = HelpSystem.getHelp("filelist");
      String text = bufferText(buf);
      assertTrue(text.contains("filelist")
         || text.contains("OVERLAY") || text.contains("keymap"),
         "filelist help should mention keymap overlay");
   }

   @Test
   @DisplayName("unknown topic lists available topics")
   void unknownTopicListsAvailable() {
      TextEdit<String> buf = HelpSystem.getHelp("xyznotatopic");
      String text = bufferText(buf);
      assertTrue(text.contains("movement"),
         "unknown topic should list available topics");
      assertTrue(text.contains("editing"),
         "unknown topic should list editing in available");
   }

   @Test
   @DisplayName("index help mentions all topic names")
   void indexMentionsAllTopics() {
      TextEdit<String> buf = HelpSystem.getHelp("index");
      String text = bufferText(buf);
      assertTrue(text.contains("movement"));
      assertTrue(text.contains("editing"));
      assertTrue(text.contains("search"));
      assertTrue(text.contains("files"));
      assertTrue(text.contains("ex"));
      assertTrue(text.contains("visual"));
      assertTrue(text.contains("undo"));
      assertTrue(text.contains("window"));
      assertTrue(text.contains("shell"));
      assertTrue(text.contains("diredit"));
      assertTrue(text.contains("filelist"));
      assertTrue(text.contains("directory"));
      assertTrue(text.contains("keybindings"));
      assertTrue(text.contains("folding"));
   }

   @Test
   @DisplayName("each topic ends with 'Type :help for index.'")
   void topicEndsWithHelpLink() {
      String[] topics = {"movement", "editing", "search", "files",
         "ex", "visual", "undo", "window", "shell", "diredit",
         "filelist", "directory", "folding"};
      for (String topic : topics) {
         TextEdit<String> buf = HelpSystem.getHelp(topic);
         String text = bufferText(buf);
         assertTrue(text.contains("Type :help for index."),
            topic + " should end with back-link");
      }
   }

   // ================================================================
   // getKeyBindings content
   // ================================================================

   @Test
   @DisplayName("getKeyBindings buffer is not empty")
   void keyBindingsNotEmpty() {
      TextEdit<String> buf = HelpSystem.getKeyBindings();
      assertNotNull(buf);
      assertTrue(buf.finish() > 2,
         "key bindings buffer should have content");
   }

   // ================================================================
   // Repeated calls / buffer reuse
   // ================================================================

   @Test
   @DisplayName("switching from shell to folding clears old content")
   void switchTopicsClearsContent() {
      HelpSystem.getHelp("shell");
      TextEdit<String> buf = HelpSystem.getHelp("folding");
      String text = bufferText(buf);
      assertTrue(text.contains("FOLDING"));
      assertFalse(text.contains("STARTING A SHELL"),
         "shell content should be cleared");
   }

   @Test
   @DisplayName("getHelp returns same buffer object")
   void sameBufferObject() {
      TextEdit<String> buf1 = HelpSystem.getHelp("undo");
      TextEdit<String> buf2 = HelpSystem.getHelp("shell");
      assertTrue(buf1 == buf2,
         "getHelp should reuse the same buffer");
   }
}
