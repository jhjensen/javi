package javi.ai;

import java.io.IOException;

import static history.Tools.trace;

import javi.ai.tools.AIToolRegistry;
import javi.ai.tools.BufferInfoTool;

import javi.EditContainer;
import javi.EventQueue;
import javi.FvContext;
import javi.GhostTextState;
import javi.InputException;
import javi.InsertBuffer;
import javi.JeyEvent;
import javi.MovePos;
import javi.Plugin;
import javi.PosListList;
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
 * <p>Chat uses {@link AIAsyncExecutor} to run API calls on a background
 * thread. Results are dispatched back via {@link javi.EventQueue}.
 * Other commands (explain, review, doc) remain synchronous.</p>
 *
 * @see AIClient
 * @see AIConfig
 * @see Rgroup
 */
public final class AICommands extends Rgroup implements Plugin {

   /** Plugin descriptor. */
   public static final String pluginInfo =
      "AI assistant: chat, explain, review, complete";

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
   private static final int CMD_CANCEL   = 12;
   private static final int CMD_REFACTOR = 13;
   private static final int CMD_AUTH     = 14;
   private static final int CMD_MODELS   = 15;
   private static final int CMD_STATUS   = 16;
   private static final int CMD_TOOLS    = 17;
   private static final int CMD_GPROCESS = 18;
   private static final int CMD_SET_PROVIDER  = 19;
   private static final int CMD_SET_MODEL     = 20;
   private static final int CMD_SET_MAXTOKENS = 21;
   private static final int CMD_SET_APIKEY    = 22;
   private static final int CMD_SET_AUTHFILE  = 23;
   private static final int CMD_SET_DELAY     = 24;
   private static final int CMD_SET_TIMEOUT   = 25;
   private static final int CMD_SET_PROMPT    = 26;

   /** The chat output buffer. */
   private static TextEdit<String> chatBuffer;

   /** Last non-chat source buffer for context capture. */
   private static EditContainer lastSourceBuffer;
   /** Name of the last source buffer. */
   private static String lastSourceName;
   /** Whether the last context capture used a visual selection. */
   private static boolean lastSelectionActive;

   /** Request tracking for premium awareness. */
   private static int totalRequests;
   private static int streamingRequests;
   private static long totalInputChars;
   private static long totalOutputChars;

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
         "ai.cancel",      // 12 - cancel in-flight AI request
         "ai.refactor",   // 13 - refactor code with instruction
         "ai.auth",       // 14 - device flow auth
         "ai.models",     // 15 - list available models
         "ai.status",     // 16 - request history/tracking
         "ai.tools",      // 17 - list registered tools
         "ai.gprocess",   // 18 - g prefix key handler
         "ai.provider",   // 19 - set provider (for :set dispatch)
         "ai.model",      // 20 - set model
         "ai.maxTokens",  // 21 - set max tokens
         "ai.apikey",     // 22 - set API key
         "ai.authfile",   // 23 - set auth file path
         "ai.delay",      // 24 - set completion delay
         "ai.timeout",    // 25 - set request timeout
         "ai.prompt",     // 26 - set system prompt
      };
      register(rnames);
      AIToolRegistry.registerBuiltins();
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
         case CMD_CANCEL:
            return doCancel();
         case CMD_REFACTOR:
            return doRefactor((String) arg, fvc);
         case CMD_AUTH:
            return doAuth(fvc);
         case CMD_MODELS:
            return doModels(fvc);
         case CMD_STATUS:
            return doStatus(fvc);
         case CMD_TOOLS:
            return doTools(fvc);
         case CMD_GPROCESS:
            return doGProcess(count, rcount, fvc);
         case CMD_SET_PROVIDER:
            return doSetSetting("provider", arg);
         case CMD_SET_MODEL:
            return doSetSetting("model", arg);
         case CMD_SET_MAXTOKENS:
            return doSetSetting("maxTokens", arg);
         case CMD_SET_APIKEY:
            return doSetSetting("apikey", arg);
         case CMD_SET_AUTHFILE:
            return doSetSetting("authfile", arg);
         case CMD_SET_DELAY:
            return doSetSetting("delay", arg);
         case CMD_SET_TIMEOUT:
            return doSetSetting("timeout", arg);
         case CMD_SET_PROMPT:
            return doSetSetting("prompt", arg);
         default:
            throw new RuntimeException("AICommands: unknown command " + rnum);
      }
   }

   /**
    * Handle a :set dispatch for an AI configuration setting.
    *
    * @param key the setting key (without "ai." prefix)
    * @param arg the value to set
    * @return null
    * @throws InputException if the value is invalid
    */
   private Object doSetSetting(String key, Object arg) throws InputException {
      String value = arg != null ? arg.toString() : "";
      try {
         if (!AIConfig.getInstance().setSetting(key, value)) {
            throw new InputException("unknown AI setting: " + key);
         }
         UI.reportMessage("ai." + key + "=" + value);
      } catch (IllegalArgumentException e) {
         throw new InputException("invalid value for ai." + key
            + ": " + e.getMessage());
      }
      return null;
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
         case "cancel":
            return doCancel();
         case "refactor":
            return doRefactor(subarg, fvc);
         case "auth":
            return doAuth(fvc);
         case "models":
            return doModels(fvc);
         case "status":
            return doStatus(fvc);
         case "tools":
            return doTools(fvc);
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
   private static final String PROMPT_SEPARATOR =
      "\u001b[33m________________________________\u001b[0m";

   private Object doChat(String message, FvContext fvc)
         throws IOException, InputException {
      if (null == message || message.isEmpty()) {
         // Use visual selection as multiline prompt if active
         String sel = getSelectedText(fvc);
         if (null != sel && !sel.isEmpty()) {
            message = sel;
         } else {
            // If already in chat buffer with a pending separator, send it
            if ("*ai-chat*".equals(fvc.edvec.getName())) {
               message = collectPromptFromSeparator();
               if (null == message || message.isEmpty()) {
                  // No text below separator — add new separator
                  appendPromptSeparator(fvc);
                  return null;
               }
            } else {
               // Not in chat buffer — switch there and add separator
               appendPromptSeparator(fvc);
               return null;
            }
         }
      }

      // Capture context before switching to chat buffer
      String ctxName = fvc.edvec.getName();
      String ctxCode = null;
      if (!"*ai-chat*".equals(ctxName)) {
         ctxCode = getContextCode(fvc);
         lastSourceBuffer = fvc.edvec;
         lastSourceName = ctxName;
         updateBufferInfoContext(fvc);
      } else if (null != lastSourceBuffer) {
         ctxCode = getBufferContent(lastSourceBuffer);
         ctxName = lastSourceName;
      }

      ensureChatBuffer();
      appendToChatBuffer("YOU: " + message);
      appendToChatBuffer("");
      logRequest("chat", ctxName, ctxCode);

      // Show chat buffer immediately
      FvContext.connectFv(chatBuffer, fvc.vi);

      final View vi = fvc.vi;
      final String msg = message;
      final String fileCtx = ctxCode;
      final String fileName = ctxName;

      appendToChatBuffer("AI: ");
      AIAsyncExecutor.submitStreaming(
         onToken -> {
            try {
               AIClient client = AIClient.getInstance();
               if (null != fileCtx) {
                  client.chatWithContextStreaming(
                     msg, fileName, fileCtx, onToken);
               } else {
                  client.chatStreaming(msg, onToken);
               }
            } catch (IOException | AIException e) {
               throw new RuntimeException(e);
            }
         },
         token -> {
            appendStreamToken(token);
            vi.repaint();
         },
         () -> {
            finishStreamResponse(vi);
            UI.reportMessage("AI: response received");
         },
         error -> handleAsyncError(error, vi)
      );
      return null;
   }

   /**
    * Append the yellow prompt separator line to the chat buffer and
    * switch to it.  The user types below the separator and invokes
    * ai.chat again (or Shift-Enter) to send.
    */
   private void appendPromptSeparator(FvContext fvc)
         throws InputException {
      if (!"*ai-chat*".equals(fvc.edvec.getName())) {
         lastSourceBuffer = fvc.edvec;
         lastSourceName = fvc.edvec.getName();
         updateBufferInfoContext(fvc);
      }
      ensureChatBuffer();
      appendToChatBuffer(PROMPT_SEPARATOR);
      chatBuffer.insertOne("", chatBuffer.finish());
      FvContext ctx = FvContext.connectFv(chatBuffer, fvc.vi);
      ctx.cursoryabs(chatBuffer.finish() - 1);
      ctx.cursorabs(0, chatBuffer.finish() - 1);
      UI.reportMessage("Type prompt below line, then :ai.chat or Shift-Enter to send");
   }

   /**
    * Collect text from below the last prompt separator in the chat buffer.
    * Removes the separator and prompt lines from the buffer.
    * Returns null if no separator found or no text below it.
    */
   private String collectPromptFromSeparator() {
      if (null == chatBuffer)
         return null;
      // Find the last separator line
      int separatorLine = -1;
      for (int i = chatBuffer.finish() - 1; i >= 1; i--) {
         if (PROMPT_SEPARATOR.equals(chatBuffer.at(i).toString())) {
            separatorLine = i;
            break;
         }
      }
      if (separatorLine < 0)
         return null;

      // Collect lines below separator
      int endLine = chatBuffer.finish() - 1;
      StringBuilder prompt = new StringBuilder();
      for (int i = separatorLine + 1; i <= endLine; i++) {
         String line = chatBuffer.at(i).toString();
         if (prompt.length() > 0)
            prompt.append('\n');
         prompt.append(line);
      }

      // Remove prompt lines below separator (keep separator itself)
      if (endLine > separatorLine) {
         chatBuffer.remove(separatorLine + 1, endLine - separatorLine);
      }

      return prompt.toString().trim();
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
      String code = getSourceContext(fvc);
      if (null == code) {
         UI.reportMessage("No code to explain");
         return null;
      }

      String name = getSourceName(fvc);
      ensureChatBuffer();
      appendToChatBuffer("EXPLAIN: " + name);
      appendToChatBuffer("");
      logRequest("explain", name, code);
      try {
         FvContext<?> ctx =
            FvContext.connectFv(chatBuffer, fvc.vi);
         ctx.cursoryabs(chatBuffer.finish() - 1);
      } catch (InputException e) {
         UI.reportMessage(
            "Input Error: " + e.getMessage());
      }

      final View vi = fvc.vi;
      final String codeCtx = code;
      AIAsyncExecutor.submitStreaming(
         onToken -> {
            try {
               AIClient.getInstance()
                  .explainStreaming(codeCtx, onToken);
            } catch (IOException | AIException e) {
               throw new RuntimeException(e);
            }
         },
         token -> {
            appendStreamToken(token);
            vi.repaint();
         },
         () -> {
            finishStreamResponse(vi);
            UI.reportMessage("AI: explain complete");
         },
         error -> handleAsyncError(error, vi)
      );
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
      String code = getSourceContext(fvc);
      if (null == code) {
         UI.reportMessage("No code to review");
         return null;
      }

      String name = getSourceName(fvc);
      ensureChatBuffer();
      appendToChatBuffer("REVIEW: " + name);
      appendToChatBuffer("");
      logRequest("review", name, code);
      try {
         FvContext<?> ctx =
            FvContext.connectFv(chatBuffer, fvc.vi);
         ctx.cursoryabs(chatBuffer.finish() - 1);
      } catch (InputException e) {
         UI.reportMessage(
            "Input Error: " + e.getMessage());
      }

      final View vi = fvc.vi;
      final String codeCtx = code;
      AIAsyncExecutor.submitStreaming(
         onToken -> {
            try {
               AIClient.getInstance()
                  .reviewStreaming(codeCtx, onToken);
            } catch (IOException | AIException e) {
               throw new RuntimeException(e);
            }
         },
         token -> {
            appendStreamToken(token);
            vi.repaint();
         },
         () -> {
            finishStreamResponse(vi);
            UI.reportMessage("AI: review complete");
         },
         error -> handleAsyncError(error, vi)
      );
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
      String code = getSourceContext(fvc);
      if (null == code) {
         UI.reportMessage("No code to document");
         return null;
      }

      String name = getSourceName(fvc);
      ensureChatBuffer();
      appendToChatBuffer("DOCUMENT: " + name);
      appendToChatBuffer("");
      logRequest("doc", name, code);
      try {
         FvContext.connectFv(chatBuffer, fvc.vi);
      } catch (InputException e) {
         UI.reportMessage(
            "Input Error: " + e.getMessage());
      }

      final View vi = fvc.vi;
      final String codeCtx = code;
      AIAsyncExecutor.submitStreaming(
         onToken -> {
            try {
               AIClient.getInstance()
                  .documentStreaming(codeCtx, onToken);
            } catch (IOException | AIException e) {
               throw new RuntimeException(e);
            }
         },
         token -> {
            appendStreamToken(token);
            vi.repaint();
         },
         () -> {
            finishStreamResponse(vi);
            UI.reportMessage("AI: doc complete");
         },
         error -> handleAsyncError(error, vi)
      );
      return null;
   }

   /**
    * Refactor code with an AI-generated transformation.
    *
    * <p>Prompts the user for a refactoring instruction if none is
    * provided, then sends the current buffer code along with the
    * instruction to the AI. Result is shown in the chat buffer.</p>
    *
    * @param instruction the refactoring instruction, or null to prompt
    * @param fvc the current file-view context
    * @return null
    * @throws IOException if an I/O error occurs
    * @throws InputException if input fails
    */
   private Object doRefactor(String instruction, FvContext fvc)
         throws IOException, InputException {
      String code = getSourceContext(fvc);
      if (null == code) {
         UI.reportMessage("No code to refactor");
         return null;
      }

      if (null == instruction || instruction.isEmpty()) {
         String line =
            InsertBuffer.getcomline("refactor> ");
         if (line.length() <= 10) {
            return null;
         }
         instruction = line.substring(10).trim();
         if (instruction.isEmpty()) {
            return null;
         }
      }

      String name = getSourceName(fvc);
      ensureChatBuffer();
      appendToChatBuffer("REFACTOR: " + name);
      appendToChatBuffer("Instruction: " + instruction);
      appendToChatBuffer("");
      logRequest("refactor", name, code);
      try {
         FvContext.connectFv(chatBuffer, fvc.vi);
      } catch (InputException e) {
         UI.reportMessage(
            "Input Error: " + e.getMessage());
      }

      final View vi = fvc.vi;
      final String inst = instruction;
      final String codeCtx = code;
      AIAsyncExecutor.submitStreaming(
         onToken -> {
            try {
               AIClient.getInstance()
                  .refactorStreaming(codeCtx, inst,
                     onToken);
            } catch (IOException | AIException e) {
               throw new RuntimeException(e);
            }
         },
         token -> {
            appendStreamToken(token);
            vi.repaint();
         },
         () -> {
            finishStreamResponse(vi);
            UI.reportMessage("AI: refactor complete");
         },
         error -> handleAsyncError(error, vi)
      );
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
      AIAsyncExecutor.submit(
         () -> {
            try {
               AIClient client = AIClient.getInstance();
               AIProvider provider = client.getProvider();
               boolean ok = provider.testConnection();
               if (ok) {
                  return "AI connection OK: "
                     + provider.getName() + " / "
                     + provider.getModel();
               }
               return "AI connection FAILED: "
                  + provider.getName();
            } catch (AIException e) {
               return "AI config error: "
                  + e.getMessage();
            }
         },
         msg -> UI.reportMessage((String) msg),
         error -> UI.reportMessage(
            "AI test error: " + error.getMessage())
      );
      return null;
   }

   /**
    * Perform AI-powered code completion at the current cursor position.
    *
    * <p>Sends the code from the start of the buffer up to the cursor
    * to the AI provider asynchronously. On completion, shows ghost
    * text preview at the original cursor position. The user can then
    * accept or dismiss the completion.</p>
    *
    * <p>Bound to Ctrl+Space in both normal and insert mode.</p>
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
      final int cursorLine = fvc.inserty();
      final int cursorCol = fvc.insertx();
      final View vi = fvc.vi;

      AIAsyncExecutor.submit(
         () -> {
            try {
               return AIClient.getInstance()
                  .complete(code, fileName);
            } catch (IOException | AIException e) {
               throw new RuntimeException(e);
            }
         },
         completion -> {
            if (null == completion
                  || completion.isEmpty()) {
               GhostTextState.reset();
               UI.reportMessage(
                  "AI: no completion available");
               return;
            }
            completion = completion.strip();
            String[] lines =
               completion.split("\n", -1);
            GhostTextState.completionArrived(
               lines, cursorLine, cursorCol);
            vi.repaint();

            String hint = lines.length > 1
               ? "AI: " + lines.length
                  + " lines — Tab to accept,"
                  + " Esc to dismiss"
               : "AI: Tab to accept,"
                  + " Esc to dismiss";
            UI.reportMessage(hint);
         },
         error -> {
            GhostTextState.reset();
            Throwable cause =
               error.getCause() != null
                  ? error.getCause() : error;
            UI.reportMessage(
               "AI Error: " + cause.getMessage());
         }
      );
      return null;
   }

   /**
    * Accept the ghost text completion and insert it into
    * the buffer. Delegates to {@link GhostTextState}.
    */
   private Object doAcceptGhost(FvContext fvc) {
      GhostTextState.accept(fvc);
      return null;
   }

   /**
    * Cancel any in-flight AI request.
    *
    * @return null
    */
   private Object doCancel() {
      if (AIAsyncExecutor.isBusy()) {
         AIAsyncExecutor.cancel();
      } else {
         UI.reportMessage("AI: no active request");
      }
      return null;
   }

   /**
    * Dismiss the ghost text completion without inserting.
    */
   private Object doDismissGhost() {
      GhostTextState.dismiss();
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
    * Authenticate with GitHub Copilot via device flow.
    *
    * <p>Initiates GitHub Device Flow OAuth. Displays a URL and
    * user code in the chat buffer for the user to authorize
    * in their browser. Polls for completion on a background
    * thread.</p>
    *
    * @param fvc the current file-view context
    * @return null
    */
   private Object doAuth(FvContext fvc) {
      ensureChatBuffer();

      AIAsyncExecutor.submit(
         () -> {
            try {
               CopilotRestClient client =
                  new CopilotRestClient();
               if (client.hasToken()) {
                  return "Already authenticated. "
                     + "Token loaded from apps.json.";
               }
               CopilotRestClient.DeviceFlowInfo info =
                  client.startDeviceFlow();
               // Open browser to verification URL
               try {
                  java.awt.Desktop.getDesktop().browse(
                     java.net.URI.create(
                        info.verificationUri()));
               } catch (Exception ignore) {
                  // Browser open is best-effort
                  trace("Could not open browser: "
                     + ignore.getMessage());
               }
               // Show info immediately, then poll
               final String msg =
                  "COPILOT AUTH\n"
                  + "Open: " + info.verificationUri()
                  + "\nCode: " + info.userCode()
                  + "\nWaiting for authorization...";
               javi.EventQueue.insert(
                  new javi.EventQueue.IEvent() {
                     public void execute() {
                        appendToChatBuffer(msg);
                        try {
                           FvContext.connectFv(
                              chatBuffer, fvc.vi);
                        } catch (InputException e) {
                           UI.reportMessage(
                              e.getMessage());
                        }
                     }
                  });
               boolean ok = client.pollDeviceFlow(
                  info.deviceCode());
               if (ok) {
                  AIClient.getInstance()
                     .resetProvider();
                  return "Copilot auth successful!";
               }
               return "Auth timed out. Try again.";
            } catch (Exception e) {
               return "Auth failed: " + e.getMessage();
            }
         },
         response -> {
            appendToChatBuffer((String) response);
            try {
               FvContext.connectFv(chatBuffer, fvc.vi);
            } catch (InputException e) {
               UI.reportMessage(e.getMessage());
            }
         },
         error -> UI.reportMessage(
            "AI auth error: " + error.getMessage())
      );
      return null;
   }

   /**
    * List available Copilot models.
    *
    * <p>Queries the Copilot API for available models and
    * displays them in the chat buffer. Requires Copilot
    * provider to be configured.</p>
    *
    * @param fvc the current file-view context
    * @return null
    */
   private Object doModels(FvContext fvc) {
      ensureChatBuffer();

      AIAsyncExecutor.submit(
         () -> {
            try {
               AIProvider p =
                  AIClient.getInstance().getProvider();
               if (p instanceof CopilotProvider cp) {
                  java.util.List<String> models =
                     cp.listModels();
                  StringBuilder sb =
                     new StringBuilder(256);
                  sb.append("COPILOT MODELS\n");
                  sb.append("==============\n");
                  String current =
                     AIConfig.getInstance().getModel();
                  for (String m : models) {
                     if (m.equals(current))
                        sb.append("* ");
                     else
                        sb.append("  ");
                     sb.append(m).append('\n');
                  }
                  sb.append("\nCurrent: ")
                     .append(current);
                  sb.append("\nUse :set ai.model=<name>"
                     + " to change");
                  return sb.toString();
               }
               return "Models command requires Copilot "
                  + "provider. Current: "
                  + p.getName();
            } catch (Exception e) {
               return "Error listing models: "
                  + e.getMessage();
            }
         },
         response -> {
            appendToChatBuffer((String) response);
            try {
               FvContext.connectFv(chatBuffer, fvc.vi);
            } catch (InputException e) {
               UI.reportMessage(e.getMessage());
            }
         },
         error -> UI.reportMessage(
            "Models error: " + error.getMessage())
      );
      return null;
   }

   /**
    * Handle the 'g' prefix key in normal mode.
    *
    * <p>Reads the next key after 'g'. If 'a', enters AI subcommand
    * mode (ga prefix). If 'g', goes to line 1 (standard vi gg).
    * Other keys are ignored.</p>
    *
    * @param count the numeric count prefix
    * @param rcount the raw count (0 when no digits typed)
    * @param fvc the current file-view context
    * @return null
    * @throws IOException if an I/O error occurs
    * @throws InputException if input fails
    */
   private Object doGProcess(int count, int rcount,
         FvContext fvc)
         throws IOException, InputException {
      JeyEvent next = EventQueue.nextKeye(fvc.vi);
      char ch = next.getKeyChar();
      switch (ch) {
         case 'a':
            return doGaPrefix(fvc);
         case 'g':
            fvc.cursoryabs(rcount > 0 ? rcount : 1);
            return null;
         default:
            return null;
      }
   }

   /**
    * Handle the 'ga' AI prefix in normal mode.
    *
    * <p>Reads the third key to dispatch the AI subcommand:
    * <ul>
    *   <li>{@code r} — review current code</li>
    *   <li>{@code e} — explain current code</li>
    *   <li>{@code d} — generate documentation</li>
    *   <li>{@code f} — refactor current code</li>
    *   <li>{@code c} — open AI chat</li>
    *   <li>{@code s} — show AI status</li>
    *   <li>{@code t} — test AI connection</li>
    *   <li>{@code x} — cancel AI request</li>
    *   <li>{@code ?} — show AI help</li>
    * </ul></p>
    *
    * @param fvc the current file-view context
    * @return null
    * @throws IOException if an I/O error occurs
    * @throws InputException if input fails
    */
   private Object doGaPrefix(FvContext fvc)
         throws IOException, InputException {
      JeyEvent next = EventQueue.nextKeye(fvc.vi);
      char ch = next.getKeyChar();
      switch (ch) {
         case 'r':
            return doReview(fvc);
         case 'e':
            return doExplain(fvc);
         case 'd':
            return doDoc(fvc);
         case 'f':
            return doRefactor(null, fvc);
         case 'c':
            return doChat(null, fvc);
         case 's':
            return doStatus(fvc);
         case 't':
            return doTest();
         case 'm':
            return doModels(fvc);
         case 'x':
            return doCancel();
         case '?':
            showAiHelp(fvc);
            return null;
         default:
            UI.reportMessage(
               "ga: unknown key '" + ch
               + "' — use gar/gae/gad/gaf/gac");
            return null;
      }
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
      appendToChatBuffer("  :ai refactor <ins>  Refactor code with instruction");
      appendToChatBuffer("  :ai complete        Insert AI code completion");
      appendToChatBuffer("  :ai config          Show AI configuration");
      appendToChatBuffer("  :ai clear           Clear chat history");
      appendToChatBuffer("  :ai test            Test provider connection");
      appendToChatBuffer("  :ai cancel          Cancel in-flight request");
      appendToChatBuffer("  :ai auth            Copilot device flow auth");
      appendToChatBuffer("  :ai models          List Copilot models");
      appendToChatBuffer("  :ai status          Show request tracking");
      appendToChatBuffer("  :ai tools           List registered AI tools");
      appendToChatBuffer("  :ai help            Show this help");
      appendToChatBuffer("");
      appendToChatBuffer("NORMAL MODE (ga prefix)");
      appendToChatBuffer("  gar                 Review current code");
      appendToChatBuffer("  gae                 Explain current code");
      appendToChatBuffer("  gad                 Generate documentation");
      appendToChatBuffer("  gaf                 Refactor (prompts for instruction)");
      appendToChatBuffer("  gac                 Open AI chat");
      appendToChatBuffer("  gas                 Show AI status");
      appendToChatBuffer("  gat                 Test AI connection");
      appendToChatBuffer("  gam                 List available models");
      appendToChatBuffer("  gax                 Cancel AI request");
      appendToChatBuffer("  ga?                 Show this help");
      appendToChatBuffer("  gg                  Go to first line");
      appendToChatBuffer("");
      appendToChatBuffer("INSERT MODE");
      appendToChatBuffer("  Tab                 Trigger AI completion "
         + "(if text before cursor)");
      appendToChatBuffer("  Tab (ghost visible) Accept ghost text");
      appendToChatBuffer("  Esc                 Dismiss ghost text "
         + "/ exit insert mode");
      appendToChatBuffer("");
      appendToChatBuffer("CONFIGURATION");
      appendToChatBuffer("  :set ai.provider=openai|anthropic|copilot");
      appendToChatBuffer("  :set ai.model=<model-name>");
      appendToChatBuffer("  :set ai.apikey=<key>");
      appendToChatBuffer("  :set ai.authfile=<path>  (absolute path or ~/...)");
      appendToChatBuffer("  :set ai.maxTokens=<number>");
      appendToChatBuffer("  :set ai.delay=<ms>   (completion delay, 0=disable)");
      appendToChatBuffer("  :set ai.timeout=<seconds>");
      appendToChatBuffer("  :set ai.prompt=<system-prompt>");
      appendToChatBuffer("");
      try {
         FvContext.connectFv(chatBuffer, fvc.vi);
      } catch (InputException e) {
         UI.reportMessage("Input Error: " + e.getMessage());
      }
   }

   /**
    * Log AI request details to the chat buffer and trace.
    *
    * @param command the AI command name
    * @param source the source buffer name
    * @param context the context code, or null
    */
   private static void logRequest(String command,
         String source, String context) {
      AIConfig config = AIConfig.getInstance();
      String model = config.getModel();
      String prov = config.getProvider().getId();
      int ctxLines = 0;
      int ctxChars = 0;
      if (null != context) {
         ctxLines = context.split("\n", -1).length;
         ctxChars = context.length();
      }
      int estTokens = ctxChars / 4; // rough estimate
      boolean premium = isPremiumModel(model);
      totalRequests++;
      totalInputChars += ctxChars;

      RequestLog.logRequest(command, model, prov, premium,
         source, ctxChars, ctxLines);

      String info = "[" + prov + "/" + model
         + (premium ? " PREMIUM" : "") + "] :"
         + command + " — source: " + source
         + ", context: " + ctxLines + " lines/"
         + ctxChars + " chars"
         + ", ~" + estTokens + " tokens"
         + ", timeout: " + config.getTimeoutSeconds()
         + "s, req #" + totalRequests;
      trace("AI request: " + info);
      appendToChatBuffer(info);
   }

   /**
    * Append AI response text and separator to the chat buffer,
    * then refresh the view.
    *
    * @param text the response text
    * @param vi the view to refresh
    */
   private static void appendResponse(
         String text, View vi) {
      appendToChatBuffer(text);
      appendToChatBuffer("");
      appendToChatBuffer("---");
      appendToChatBuffer("");
      try {
         FvContext.connectFv(chatBuffer, vi);
      } catch (InputException e) {
         UI.reportMessage(
            "Input Error: " + e.getMessage());
      }
      vi.repaint();
   }

   /**
    * Handle an async AI error by logging to chat buffer
    * and reporting to the user.
    *
    * @param error the exception from the async task
    * @param vi the view to refresh
    */
   private static void handleAsyncError(
         Exception error, View vi) {
      Throwable cause = error.getCause() != null
         ? error.getCause() : error;
      String errMsg = "AI Error: " + cause.getMessage();
      if (cause instanceof AIException ae
            && ae.isAuthError()) {
         errMsg += " — Run :ai auth to authenticate.";
      }
      appendToChatBuffer(errMsg);
      appendToChatBuffer("");
      try {
         FvContext.connectFv(chatBuffer, vi);
      } catch (InputException e) {
         UI.reportMessage(
            "Input Error: " + e.getMessage());
      }
      vi.repaint();
      UI.reportMessage(errMsg);
   }

   /**
    * Get source code context, preferring the source buffer over
    * the chat buffer. When the current view is *ai-chat*, falls
    * back to the last source buffer.
    *
    * @param fvc the current file-view context
    * @return the source code text, or null if unavailable
    */
   private String getSourceContext(FvContext fvc) {
      String name = fvc.edvec.getName();
      if (!"*ai-chat*".equals(name)) {
         lastSourceBuffer = fvc.edvec;
         lastSourceName = name;
         updateBufferInfoContext(fvc);
         // Prefer visual selection if active
         String selected = getSelectedText(fvc);
         if (null != selected) {
            lastSelectionActive = true;
            return selected;
         }
         lastSelectionActive = false;
         return getContextCode(fvc);
      }
      if (null != lastSourceBuffer) {
         lastSelectionActive = false;
         return getBufferContent(lastSourceBuffer);
      }
      return null;
   }

   /**
    * Update the BufferInfoTool cached context from the
    * current file-view context. Called before AI requests
    * so the tool has access to editor state.
    *
    * @param fvc the current file-view context
    */
   @SuppressWarnings("unchecked")
   private static void updateBufferInfoContext(
         FvContext fvc) {
      String bufName = fvc.edvec.getName();
      int lineCount = fvc.edvec.readIn() - 1;
      int cursorLine = fvc.inserty();
      int cursorCol = fvc.insertx();
      MovePos mark = fvc.vi.getMark();
      boolean hasSel = null != mark;
      BufferInfoTool.setContext(
         bufName, lineCount, cursorLine,
         cursorCol, hasSel);
   }

   /**
    * Get the source buffer name, preferring the real source over
    * the chat buffer. Appends " (selection)" when a visual
    * selection was used for context.
    *
    * @param fvc the current file-view context
    * @return the source buffer name
    */
   private String getSourceName(FvContext fvc) {
      String name = fvc.edvec.getName();
      if (!"*ai-chat*".equals(name)) {
         lastSourceBuffer = fvc.edvec;
         lastSourceName = name;
         return lastSelectionActive
            ? name + " (selection)" : name;
      }
      String base = null != lastSourceName
         ? lastSourceName : "*ai-chat*";
      return lastSelectionActive
         ? base + " (selection)" : base;
   }

   /**
    * Extract visually selected text from the current buffer.
    *
    * <p>If a mark is active (visual selection), extracts the text
    * between the mark position and cursor position. Returns null
    * if no mark is set.</p>
    *
    * @param fvc the current file-view context
    * @return the selected text, or null if no selection
    */
   @SuppressWarnings("unchecked")
   private String getSelectedText(FvContext fvc) {
      MovePos mark = fvc.vi.getMark();
      if (null == mark)
         return null;

      int curY = fvc.inserty();
      int curX = fvc.insertx();
      int sy = mark.y;
      int sx = mark.x;
      int ey = curY;
      int ex = curX;

      // Normalize so start <= end
      if (sy > ey || (sy == ey && sx > ex)) {
         int tmp = sy; sy = ey; ey = tmp;
         tmp = sx; sx = ex; ex = tmp;
      }

      EditContainer ec = fvc.edvec;
      int lastLine = ec.readIn() - 1;
      if (sy < 1) sy = 1;
      if (ey > lastLine) ey = lastLine;

      StringBuilder sb = new StringBuilder();
      for (int y = sy; y <= ey; y++) {
         Object lineObj = ec.at(y);
         if (null == lineObj) break;
         String line = lineObj.toString();
         int from = (y == sy)
            ? Math.min(sx, line.length()) : 0;
         int to = (y == ey)
            ? Math.min(ex, line.length())
            : line.length();
         if (from > to) from = to;
         sb.append(line, from, to);
         if (y < ey) sb.append('\n');
      }

      String result = sb.toString();
      return result.isEmpty() ? null : result;
   }

   /**
    * Get code from the current buffer for AI operations.
    *
    * @param fvc the current file-view context
    * @return the code text, or null if buffer is empty
    */
   @SuppressWarnings("unchecked")
   private String getContextCode(FvContext fvc) {
      return getBufferContent(fvc.edvec);
   }

   /**
    * Extract text content from a buffer.
    *
    * @param ec the edit container to read
    * @return the text, or null if buffer is empty
    */
   @SuppressWarnings("unchecked")
   private static String getBufferContent(EditContainer ec) {
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

   /** Accumulates partial streaming text for current line. */
   private static StringBuilder streamLineAccum =
      new StringBuilder();

   /**
    * Append a streaming token to the chat buffer.
    *
    * <p>Tokens can contain partial lines or multiple lines.
    * This method accumulates text and creates new buffer lines
    * at each newline boundary, so the user sees text flowing in
    * progressively.</p>
    *
    * @param token the token chunk from streaming
    */
   private static void appendStreamToken(String token) {
      ensureChatBuffer();
      for (int i = 0; i < token.length(); i++) {
         char c = token.charAt(i);
         if ('\n' == c) {
            // Finalize current line, start a new one
            int lastLine = chatBuffer.finish() - 1;
            Object existing = chatBuffer.at(lastLine);
            String line = (null != existing
               ? existing.toString() : "")
               + streamLineAccum.toString();
            chatBuffer.changeElementAtStr(
               line, lastLine);
            chatBuffer.insertOne("",
               chatBuffer.finish());
            streamLineAccum.setLength(0);
         } else {
            streamLineAccum.append(c);
         }
      }
      // Update the current (last) line with accumulated text
      if (streamLineAccum.length() > 0) {
         int lastLine = chatBuffer.finish() - 1;
         Object existing = chatBuffer.at(lastLine);
         String base = null != existing
            ? existing.toString() : "";
         chatBuffer.changeElementAtStr(
            base + streamLineAccum.toString(), lastLine);
         streamLineAccum.setLength(0);
      }
   }

   /**
    * Finalize a streaming response: add separator, refresh view.
    *
    * @param vi the view to refresh
    */
   private static void finishStreamResponse(View vi) {
      appendToChatBuffer("");
      appendToChatBuffer("---");
      appendToChatBuffer("");
      try {
         FvContext<?> fvc = FvContext.connectFv(chatBuffer, vi);
         fvc.cursoryabs(chatBuffer.finish() - 1);
      } catch (InputException e) {
         UI.reportMessage(
            "Input Error: " + e.getMessage());
      }
      registerChatPositionList();
      vi.repaint();
   }

   private static void registerChatPositionList() {
      try {
         int responseLine = Math.max(1, chatBuffer.finish() - 3);
         String entry = "*ai-chat*(" + responseLine + ")- AI response";
         java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.StringReader(entry));
         PosListList.Cmd.replaceFromReader("ai-chat", reader);
      } catch (Exception e) {
         trace("registerChatPositionList: " + e);
      }
   }

   /**
    * List registered AI tools.
    *
    * @param fvc the current file-view context
    * @return null
    */
   private Object doTools(FvContext fvc) {
      ensureChatBuffer();
      String summary = AIToolRegistry.getSummary();
      appendToChatBuffer(summary);
      appendToChatBuffer("");
      try {
         FvContext.connectFv(chatBuffer, fvc.vi);
      } catch (InputException e) {
         UI.reportMessage("Error: " + e.getMessage());
      }
      return null;
   }

   /**
    * Show AI request tracking status.
    *
    * @param fvc the current file-view context
    * @return null
    */
   private Object doStatus(FvContext fvc) {
      AIConfig config = AIConfig.getInstance();
      ensureChatBuffer();
      appendToChatBuffer("AI STATUS");
      appendToChatBuffer("=========");
      appendToChatBuffer("Provider: "
         + config.getProvider().getId());
      appendToChatBuffer("Model: " + config.getModel());
      appendToChatBuffer("Timeout: "
         + config.getTimeoutSeconds() + "s");
      appendToChatBuffer("Max tokens: "
         + config.getMaxTokens());
      appendToChatBuffer("");
      appendToChatBuffer("REQUEST HISTORY");
      appendToChatBuffer("Total requests: "
         + totalRequests);
      appendToChatBuffer("Streaming requests: "
         + streamingRequests);
      long estInputTokens = totalInputChars / 4;
      long estOutputTokens = totalOutputChars / 4;
      appendToChatBuffer("Est. input tokens: ~"
         + estInputTokens);
      appendToChatBuffer("Est. output tokens: ~"
         + estOutputTokens);
      if (RequestLog.getToolCallCount() > 0) {
         appendToChatBuffer("Tool calls: "
            + RequestLog.getToolCallCount());
      }
      if (RequestLog.getTotalDurationMs() > 0) {
         appendToChatBuffer("Total API time: "
            + RequestLog.getTotalDurationMs() + "ms");
      }
      appendToChatBuffer("");
      appendToChatBuffer("PREMIUM REQUESTS");
      appendToChatBuffer("Copilot premium models: "
         + "claude-sonnet-4-20250514, gpt-4o, o1, o3-mini");
      String currentModel = config.getModel();
      boolean isPremium = isPremiumModel(currentModel);
      appendToChatBuffer("Current model ("
         + currentModel + "): "
         + (isPremium ? "PREMIUM" : "standard"));
      appendToChatBuffer("Premium request count: "
         + RequestLog.getPremiumCount());
      appendToChatBuffer("History size: "
         + AIClient.getInstance().getHistorySize()
         + " messages");
      try {
         AIProvider provider =
            AIClient.getInstance().getProvider();
         if (provider instanceof CopilotProvider cp) {
            CopilotRestClient rc = cp.getRestClient();
            int remaining = rc.getRateLimitRemaining();
            int total = rc.getRateLimitTotal();
            long resetEpoch = rc.getRateLimitResetEpoch();
            if (remaining >= 0) {
               appendToChatBuffer("Credits remaining: "
                  + remaining
                  + (total > 0 ? " / " + total : ""));
            }
            if (resetEpoch > 0) {
               long now = System.currentTimeMillis() / 1000;
               long secsLeft = resetEpoch - now;
               if (secsLeft > 0) {
                  appendToChatBuffer("Reset in: "
                     + (secsLeft / 60) + "m "
                     + (secsLeft % 60) + "s");
               } else {
                  appendToChatBuffer("Reset: now (limit refreshed)");
               }
            }
            String rateHdrs = rc.getLastRateHeaders();
            if (!rateHdrs.isEmpty()) {
               appendToChatBuffer("Rate headers: " + rateHdrs);
            } else if (remaining < 0 && resetEpoch == 0) {
               appendToChatBuffer(
                  "Budget: Copilot API does not expose usage headers");
            }
         }
      } catch (AIException ignored) { }
      appendToChatBuffer("");
      int logSize = RequestLog.size();
      if (logSize > 0) {
         int showCount = Math.min(logSize, 10);
         appendToChatBuffer("RECENT REQUESTS (last "
            + showCount + " of " + logSize + ")");
         appendToChatBuffer(
            RequestLog.formatRecent(showCount));
      }
      appendToChatBuffer("TOOLS (" + AIToolRegistry.getTools().size() + ")");
      for (var entry : AIToolRegistry.getTools().entrySet()) {
         appendToChatBuffer("  " + entry.getKey()
            + " [" + entry.getValue().permissionLevel() + "]");
      }
      appendToChatBuffer("");
      try {
         FvContext.connectFv(chatBuffer, fvc.vi);
      } catch (InputException e) {
         UI.reportMessage("Error: " + e.getMessage());
      }
      return null;
   }

   /**
    * Check if a model is considered premium by Copilot.
    *
    * <p>Premium models consume more of the user's rate limit
    * or subscription quota.</p>
    *
    * @param model the model identifier
    * @return true if the model is premium
    */
   static boolean isPremiumModel(String model) {
      if (null == model) {
         return false;
      }
      return model.startsWith("o1")
         || model.startsWith("o3")
         || model.startsWith("claude")
         || model.contains("gpt-4o");
   }
}
