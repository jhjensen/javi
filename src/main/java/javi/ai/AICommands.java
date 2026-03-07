package javi.ai;

import java.io.IOException;

import javi.EditContainer;
import javi.FvContext;
import javi.InputException;
import javi.InsertBuffer;
import javi.Rgroup;
import javi.StringIoc;
import javi.TextEdit;
import javi.UI;
import javi.View;

/**
 * Command group for AI-related editor commands.
 *
 * <p>AICommands extends {@link Rgroup} to provide vi-style colon commands
 * for AI integration. Commands are registered in the standard command
 * dispatch system and can be invoked via:
 * <ul>
 *   <li>{@code :ai chat} - Open interactive AI chat</li>
 *   <li>{@code :ai explain} - Explain selected/current code</li>
 *   <li>{@code :ai review} - Review code for issues</li>
 *   <li>{@code :ai doc} - Generate documentation</li>
 *   <li>{@code :ai config} - Show current AI configuration</li>
 *   <li>{@code :ai clear} - Clear conversation history</li>
 *   <li>{@code :ai test} - Test provider connectivity</li>
 * </ul>
 *
 * <h2>Command Registration</h2>
 * <p>Instantiate this class during editor initialization to register
 * all AI commands. Commands are registered via the standard
 * {@link Rgroup#register(String[])} mechanism.</p>
 *
 * <h2>Chat Buffer</h2>
 * <p>AI responses are displayed in a dedicated {@code *ai-chat*} buffer.
 * The buffer is created on first use and reused for subsequent
 * interactions. Conversation history is maintained by {@link AIClient}.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>AI API calls are made synchronously on the event thread. This is
 * a known limitation — future work should move API calls to a
 * background thread. UNCLEAR: how to best integrate async results
 * with Javi's event/lock model (EventQueue.biglock2).</p>
 *
 * @see AIClient
 * @see AIConfig
 * @see Rgroup
 */
public final class AICommands extends Rgroup {

   /** Index constants for registered commands. */
   private static final int CMD_AI       = 1;
   private static final int CMD_AI_CHAT  = 2;
   private static final int CMD_EXPLAIN  = 3;
   private static final int CMD_REVIEW   = 4;
   private static final int CMD_DOC      = 5;
   private static final int CMD_CONFIG   = 6;
   private static final int CMD_CLEAR    = 7;
   private static final int CMD_TEST     = 8;
   private static final int CMD_COMPLETE = 9;
   private static final int CMD_ACCEPT   = 10;
   private static final int CMD_DISMISS  = 11;

   /** The chat output buffer. */
   private static TextEdit<String> chatBuffer;

   /** Pending ghost text lines for accept/dismiss. */
   private static String[] pendingGhostLines;
   /** Line number where ghost text was placed. */
   private static int pendingGhostLine;
   /** Column offset where ghost text starts. */
   private static int pendingGhostCol;

   /**
    * Create and register all AI commands.
    *
    * <p>Should be called once during editor initialization, typically
    * in {@link javi.Javi#initPostUi()}.</p>
    */
   public AICommands() {
      final String[] rnames = {
         "",
         "ai",             // 1 - dispatch subcommand
         "ai.chat",        // 2 - interactive chat
         "ai.explain",     // 3 - explain code
         "ai.review",      // 4 - review code
         "ai.doc",         // 5 - generate docs
         "ai.config",      // 6 - show config
         "ai.clear",       // 7 - clear history
         "ai.test",        // 8 - test connection
         "ai.complete",    // 9 - inline code completion (ghost text)
         "ai.accept",      // 10 - accept ghost text completion
         "ai.dismiss",     // 11 - dismiss ghost text completion
      };
      register(rnames);
   }

   @Override
   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode)
         throws IOException, InputException {
      switch (rnum) {
         case CMD_AI:
            return dispatchSubcommand((String) arg, fvc);
         case CMD_AI_CHAT:
            return doChat((String) arg, fvc);
         case CMD_EXPLAIN:
            return doExplain(fvc);
         case CMD_REVIEW:
            return doReview(fvc);
         case CMD_DOC:
            return doDoc(fvc);
         case CMD_CONFIG:
            return doConfig(fvc);
         case CMD_CLEAR:
            return doClear();
         case CMD_TEST:
            return doTest();
         case CMD_COMPLETE:
            return doComplete(fvc);
         case CMD_ACCEPT:
            return doAcceptGhost(fvc);
         case CMD_DISMISS:
            return doDismissGhost();
         default:
            throw new RuntimeException("AICommands: unknown command " + rnum);
      }
   }

   /**
    * Dispatch the {@code :ai <subcommand>} to the appropriate handler.
    *
    * <p>Parses the argument string to determine which AI subcommand
    * to execute. If no subcommand is given, defaults to chat mode.</p>
    *
    * @param arg the subcommand and its arguments
    * @param fvc the current file-view context
    * @return null
    * @throws IOException if an I/O error occurs
    * @throws InputException if the subcommand is invalid
    */
   private Object dispatchSubcommand(String arg, FvContext fvc)
         throws IOException, InputException {
      if (null == arg || arg.isEmpty()) {
         return doChat(null, fvc);
      }

      String subcmd;
      String subarg;
      int spaceIdx = arg.indexOf(' ');
      if (spaceIdx > 0) {
         subcmd = arg.substring(0, spaceIdx).trim();
         subarg = arg.substring(spaceIdx + 1).trim();
      } else {
         subcmd = arg.trim();
         subarg = null;
      }

      switch (subcmd) {
         case "chat":
            return doChat(subarg, fvc);
         case "explain":
            return doExplain(fvc);
         case "review":
            return doReview(fvc);
         case "doc":
            return doDoc(fvc);
         case "config":
            return doConfig(fvc);
         case "clear":
            return doClear();
         case "test":
            return doTest();
         case "complete":
            return doComplete(fvc);
         case "accept":
            return doAcceptGhost(fvc);
         case "dismiss":
            return doDismissGhost();
         case "help":
            showAiHelp(fvc);
            return null;
         default:
            // Treat the entire arg as a chat message
            return doChat(arg, fvc);
      }
   }

   /**
    * Interactive chat with the AI.
    *
    * <p>If {@code message} is null, prompts the user for input via
    * the command line. The response is displayed in the AI chat buffer.</p>
    *
    * @param message the chat message, or null to prompt
    * @param fvc the current file-view context
    * @return null
    * @throws IOException if an I/O error occurs
    * @throws InputException if input fails
    */
   private Object doChat(String message, FvContext fvc)
         throws IOException, InputException {
      if (null == message || message.isEmpty()) {
         String line = InsertBuffer.getcomline("ai> ");
         if (line.length() <= 4) {
            return null; // empty input
         }
         message = line.substring(4).trim();
         if (message.isEmpty()) {
            return null;
         }
      }

      ensureChatBuffer();
      appendToChatBuffer("YOU: " + message);
      appendToChatBuffer("");

      try {
         AIClient client = AIClient.getInstance();
         String response = client.chat(message);

         appendToChatBuffer("AI: " + response);
         appendToChatBuffer("");
         appendToChatBuffer("---");
         appendToChatBuffer("");

         // Show the chat buffer
         FvContext.connectFv(chatBuffer, fvc.vi);

      } catch (AIException e) {
         String errMsg = "AI Error: " + e.getMessage();
         if (e.isAuthError()) {
            errMsg += "\nSet API key with :set ai.apikey=<key> "
               + "or set environment variable.";
         }
         appendToChatBuffer(errMsg);
         appendToChatBuffer("");
         FvContext.connectFv(chatBuffer, fvc.vi);
         UI.reportMessage(errMsg);
      }
      return null;
   }

   /**
    * Explain the code in the current buffer around the cursor.
    *
    * <p>Sends the current buffer contents to the AI for explanation.
    * UNCLEAR: Should this use visual selection if active, or a fixed
    * number of lines around the cursor?</p>
    *
    * @param fvc the current file-view context
    * @return null
    * @throws IOException if an I/O error occurs
    */
   private Object doExplain(FvContext fvc) throws IOException {
      String code = getContextCode(fvc);
      if (null == code) {
         UI.reportMessage("No code to explain");
         return null;
      }

      ensureChatBuffer();
      appendToChatBuffer("EXPLAIN: " + fvc.edvec.getName());
      appendToChatBuffer("");

      try {
         AIClient client = AIClient.getInstance();
         String response = client.explain(code);
         appendToChatBuffer(response);
         appendToChatBuffer("");
         appendToChatBuffer("---");
         appendToChatBuffer("");
         FvContext.connectFv(chatBuffer, fvc.vi);
      } catch (AIException e) {
         UI.reportMessage("AI Error: " + e.getMessage());
      } catch (InputException e) {
         UI.reportMessage("Input Error: " + e.getMessage());
      }
      return null;
   }

   /**
    * Review the code in the current buffer.
    *
    * @param fvc the current file-view context
    * @return null
    * @throws IOException if an I/O error occurs
    */
   private Object doReview(FvContext fvc) throws IOException {
      String code = getContextCode(fvc);
      if (null == code) {
         UI.reportMessage("No code to review");
         return null;
      }

      ensureChatBuffer();
      appendToChatBuffer("REVIEW: " + fvc.edvec.getName());
      appendToChatBuffer("");

      try {
         AIClient client = AIClient.getInstance();
         String response = client.review(code);
         appendToChatBuffer(response);
         appendToChatBuffer("");
         appendToChatBuffer("---");
         appendToChatBuffer("");
         FvContext.connectFv(chatBuffer, fvc.vi);
      } catch (AIException e) {
         UI.reportMessage("AI Error: " + e.getMessage());
      } catch (InputException e) {
         UI.reportMessage("Input Error: " + e.getMessage());
      }
      return null;
   }

   /**
    * Generate documentation for the code in the current buffer.
    *
    * @param fvc the current file-view context
    * @return null
    * @throws IOException if an I/O error occurs
    */
   private Object doDoc(FvContext fvc) throws IOException {
      String code = getContextCode(fvc);
      if (null == code) {
         UI.reportMessage("No code to document");
         return null;
      }

      ensureChatBuffer();
      appendToChatBuffer("DOCUMENT: " + fvc.edvec.getName());
      appendToChatBuffer("");

      try {
         AIClient client = AIClient.getInstance();
         String response = client.document(code);
         appendToChatBuffer(response);
         appendToChatBuffer("");
         appendToChatBuffer("---");
         appendToChatBuffer("");
         FvContext.connectFv(chatBuffer, fvc.vi);
      } catch (AIException e) {
         UI.reportMessage("AI Error: " + e.getMessage());
      } catch (InputException e) {
         UI.reportMessage("Input Error: " + e.getMessage());
      }
      return null;
   }

   /**
    * Show current AI configuration.
    *
    * @param fvc the current file-view context
    * @return null
    */
   private Object doConfig(FvContext fvc) {
      AIConfig config = AIConfig.getInstance();
      UI.reportMessage(config.getSummary());
      return null;
   }

   /**
    * Clear conversation history.
    *
    * @return null
    */
   private Object doClear() {
      AIClient.getInstance().clearHistory();
      if (null != chatBuffer) {
         clearChatBuffer();
         appendToChatBuffer("(conversation cleared)");
         appendToChatBuffer("");
      }
      UI.reportMessage("AI conversation history cleared");
      return null;
   }

   /**
    * Test AI provider connectivity.
    *
    * @return null
    */
   private Object doTest() {
      UI.reportMessage("Testing AI connection...");
      try {
         AIClient client = AIClient.getInstance();
         AIProvider provider = client.getProvider();
         boolean ok = provider.testConnection();
         if (ok) {
            UI.reportMessage("AI connection OK: " + provider.getName()
               + " / " + provider.getModel());
         } else {
            UI.reportMessage("AI connection FAILED: " + provider.getName());
         }
      } catch (AIException e) {
         UI.reportMessage("AI configuration error: " + e.getMessage());
      }
      return null;
   }

   /**
    * Perform AI-powered code completion at the current cursor position.
    *
    * <p>Sends the code from the start of the buffer up to the cursor
    * to the AI provider, requesting a completion. The result is inserted
    * directly at the cursor position (no ghost text preview).</p>
    *
    * <p>If the AI returns multi-line text, each line is inserted
    * sequentially starting at the cursor line.</p>
    *
    * @param fvc the current file-view context
    * @return null
    * @throws IOException if an I/O error occurs
    */
   @SuppressWarnings("unchecked")
   private Object doComplete(FvContext fvc) throws IOException {
      String code = getCodeBeforeCursor(fvc);
      if (null == code || code.isEmpty()) {
         UI.reportMessage("AI: no code context for completion");
         return null;
      }

      String fileName = fvc.edvec.getName();
      UI.reportMessage("AI: generating completion...");

      try {
         AIClient client = AIClient.getInstance();
         String completion = client.complete(code, fileName);

         if (null == completion || completion.isEmpty()) {
            UI.reportMessage("AI: no completion available");
            return null;
         }

         completion = completion.strip();
         String[] lines = completion.split("\n", -1);
         int cursorLine = fvc.inserty();
         int cursorCol = fvc.insertx();

         // Store for accept
         pendingGhostLines = lines;
         pendingGhostLine = cursorLine;
         pendingGhostCol = cursorCol;

         // Show first line as ghost text preview (gray overlay)
         View.setGhostText(lines[0], cursorLine, cursorCol);
         fvc.vi.repaint();

         String hint = lines.length > 1
            ? "AI: " + lines.length + " lines — :ai accept / :ai dismiss"
            : "AI: :ai accept / :ai dismiss";
         UI.reportMessage(hint);

      } catch (AIException e) {
         UI.reportMessage("AI Error: " + e.getMessage());
      }
      return null;
   }

   /**
    * Accept the ghost text completion and insert it into the buffer.
    */
   @SuppressWarnings("unchecked")
   private Object doAcceptGhost(FvContext fvc) throws IOException {
      if (null == pendingGhostLines) {
         UI.reportMessage("AI: no pending completion");
         return null;
      }
      EditContainer ec = fvc.edvec;
      int cursorLine = pendingGhostLine;
      String[] lines = pendingGhostLines;

      // Insert the first line at cursor column into current line
      String currentLine = ec.at(cursorLine).toString();
      String merged = currentLine.substring(0, pendingGhostCol)
         + lines[0] + currentLine.substring(pendingGhostCol);
      ec.changeElementAtStr(merged, cursorLine);

      // Insert remaining lines
      for (int i = 1; i < lines.length; i++) {
         ec.insertOne(lines[i], cursorLine + i);
      }

      View.clearGhostText();
      pendingGhostLines = null;
      fvc.vi.repaint();
      UI.reportMessage("AI: inserted " + lines.length + " line"
         + (lines.length > 1 ? "s" : ""));
      return null;
   }

   /**
    * Dismiss the ghost text completion without inserting.
    */
   private Object doDismissGhost() {
      View.clearGhostText();
      pendingGhostLines = null;
      UI.reportMessage("AI: completion dismissed");
      return null;
   }

   /**
    * Get the code from the current buffer from line 1 up to the
    * cursor position (inclusive).
    *
    * @param fvc the current file-view context
    * @return the code text before cursor, or null if empty
    */
   @SuppressWarnings("unchecked")
   private String getCodeBeforeCursor(FvContext fvc) {
      EditContainer ec = fvc.edvec;
      int cursorLine = fvc.inserty();
      if (cursorLine < 1)
         return null;

      int maxLine = Math.min(cursorLine, ec.readIn() - 1);
      // Limit context to ~200 lines before cursor for manageable prompt
      int startLine = Math.max(1, maxLine - 200);

      StringBuilder sb = new StringBuilder(maxLine * 80);
      for (int i = startLine; i <= maxLine; i++) {
         Object line = ec.at(i);
         if (null != line)
            sb.append(line.toString());
         sb.append('\n');
      }
      return sb.toString();
   }

   /**
    * Show AI help in the status area.
    *
    * @param fvc the current file-view context
    */
   private void showAiHelp(FvContext fvc) {
      ensureChatBuffer();
      appendToChatBuffer("AI COMMANDS");
      appendToChatBuffer("===========");
      appendToChatBuffer("  :ai <message>       Send a chat message");
      appendToChatBuffer("  :ai chat            Interactive chat prompt");
      appendToChatBuffer("  :ai explain         Explain current code");
      appendToChatBuffer("  :ai review          Review code for issues");
      appendToChatBuffer("  :ai doc             Generate documentation");
      appendToChatBuffer("  :ai complete        Insert AI code completion");
      appendToChatBuffer("  :ai config          Show AI configuration");
      appendToChatBuffer("  :ai clear           Clear chat history");
      appendToChatBuffer("  :ai test            Test provider connection");
      appendToChatBuffer("  :ai help            Show this help");
      appendToChatBuffer("");
      appendToChatBuffer("CONFIGURATION");
      appendToChatBuffer("  :set ai.provider=openai|anthropic");
      appendToChatBuffer("  :set ai.model=<model-name>");
      appendToChatBuffer("  :set ai.apikey=<key>");
      appendToChatBuffer("  :set ai.maxTokens=<number>");
      appendToChatBuffer("");
      try {
         FvContext.connectFv(chatBuffer, fvc.vi);
      } catch (InputException e) {
         UI.reportMessage("Input Error: " + e.getMessage());
      }
   }

   /**
    * Get code from the current buffer for AI operations.
    *
    * <p>Extracts text from the current buffer. Currently returns all
    * content up to a reasonable limit. UNCLEAR: Should integrate with
    * visual selection mode to allow selecting specific code regions.</p>
    *
    * @param fvc the current file-view context
    * @return the code text, or null if buffer is empty
    */
   @SuppressWarnings("unchecked")
   private String getContextCode(FvContext fvc) {
      EditContainer<String> ec = fvc.edvec;
      int lines = ec.readIn();
      if (lines <= 1) {
         return null;
      }

      // Limit to ~500 lines to avoid huge context
      int maxLines = Math.min(lines - 1, 500);
      StringBuilder sb = new StringBuilder(maxLines * 80);
      for (int i = 1; i <= maxLines; i++) {
         Object line = ec.at(i);
         if (null != line) {
            sb.append(line.toString());
         }
         sb.append('\n');
      }
      return sb.toString();
   }

   /**
    * Ensure the AI chat buffer exists, creating it if needed.
    */
   private static void ensureChatBuffer() {
      if (null == chatBuffer) {
         StringIoc sio = new StringIoc("*ai-chat*", "");
         chatBuffer = new TextEdit<>(sio, sio.prop);
      }
   }

   /**
    * Clear the contents of the AI chat buffer.
    */
   private static void clearChatBuffer() {
      if (null != chatBuffer) {
         int finish = chatBuffer.finish();
         if (finish > 2) {
            chatBuffer.remove(1, finish - 2);
         }
      }
   }

   /**
    * Append a line of text to the AI chat buffer.
    *
    * <p>Handles multi-line strings by splitting on newlines and
    * appending each line separately.</p>
    *
    * @param text the text to append (may contain newlines)
    */
   private static void appendToChatBuffer(String text) {
      ensureChatBuffer();
      if (null == text) {
         chatBuffer.insertOne("", chatBuffer.finish());
         return;
      }
      // Split on newlines and append each line
      String[] lines = text.split("\n", -1);
      for (String line : lines) {
         chatBuffer.insertOne(line, chatBuffer.finish());
      }
   }
}
