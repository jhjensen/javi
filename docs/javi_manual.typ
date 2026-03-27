// ============================================================================
// Javi User Manual
// ============================================================================
// A comprehensive guide for users familiar with basic vi.
//
// Build: typst compile docs/javi_manual.typ
// ============================================================================

#import "javi_style.typ": *

#show: setup

#title-page(
  title: "Javi User Manual",
  subtitle: "A Vi-Like Editor Written in Java",
  date: "March 2026",
)

#outline(indent: auto, depth: 3)
#pagebreak()

// ============================================================================
// 1. INTRODUCTION
// ============================================================================

= Introduction

Javi is a vi-like text editor written in Java using AWT for its graphical
interface. It provides the familiar modal editing experience of vi with
modern extensions: an integrated VT100 terminal emulator, persistent undo
history, a directory editor, Language Server Protocol (LSP) support, Git
integration, and AI-assisted coding via GitHub Copilot.

== Starting Javi

Launch Javi from the command line:

```
java -jar javi-all.jar [file ...]
```

Open one or more files by name. If no files are given, Javi starts with an
empty buffer.

== Modal Editing

Like vi, Javi is a _modal_ editor with two primary modes:

- *Command mode* (default) --- keystrokes are interpreted as commands
  (movement, deletion, yanking, etc.).
- *Insert mode* --- keystrokes insert text into the buffer. Enter insert
  mode with #key("i"), #key("a"), #key("o"), etc. Return to command mode
  with #key("Escape").

All vi conventions apply: #key("h")#key("j")#key("k")#key("l") for
movement, #key("d")#key("d") to delete a line, #key("y")#key("y") to
yank, #key("p") to paste, and so on.

== Help System

Javi has a built-in help system accessible from command mode:

#cmd-table(
  ([#cmd("help")], [Show help index]),
  ([#cmd("help movement")], [Cursor movement commands]),
  ([#cmd("help editing")], [Text editing commands]),
  ([#cmd("help search")], [Search and replace]),
  ([#cmd("help files")], [File and buffer management]),
  ([#cmd("help ex")], [Ex (colon) commands]),
  ([#cmd("help visual")], [Visual selection mode]),
  ([#cmd("help undo")], [Undo and redo]),
  ([#cmd("help window")], [Window and scrolling]),
  ([#cmd("help shell")], [Shell / terminal commands]),
  ([#cmd("help diredit")], [Directory editor]),
  ([#cmd("help filelist")], [File list buffer]),
  ([#cmd("help directory")], [Directory list buffer]),
  ([#cmd("help keybindings")], [Key binding architecture]),
)

Help content is displayed in a read-only buffer navigable with normal vi
movement keys.

// ============================================================================
// 2. DIFFERENCES FROM VI
// ============================================================================

= Differences from Vi
<sec-differences>

Javi is modeled on POSIX vi, not vim. If you know basic vi, you already
know how to use Javi. This section describes where Javi differs from or
extends the original vi specification.

== Persistent Undo

Vi provides a single level of undo. Javi supports *unlimited, persistent
undo*. Your entire undo history is saved in `.dmp2` files alongside each
edited file. You can undo changes even after closing and reopening a file.

#cmd-table(
  ([#key("u")], [Undo last change]),
  ([#key("Ctrl-R")], [Redo last undone change]),
  ([#key("Ctrl-Z")], [Undo (alternate binding)]),
  ([#key("Ctrl-Y")], [Redo (alternate binding)]),
  ([#key("U")], [Undo all changes on current line]),
)

== Regular Expressions

Vi uses Basic Regular Expressions (BRE) with escaped grouping
(`\(` and `\)`). Javi uses *Java regular expressions* (similar to PCRE),
which are more powerful:

- `.` matches any character, `\w` matches word characters, `\s` matches
  whitespace
- `+` means one-or-more (no escaping needed)
- `[abc]` character classes work as expected
- `^` and `$` anchor to line boundaries

== Function Keys

Vi has no function key bindings. Javi maps function keys to frequently
used features:

#cmd-table(
  ([#key("F1")], [Next position in position list]),
  ([#key("F2")], [File list (open buffers)]),
  ([#key("F3")], [Directory list]),
  ([#key("F4")], [Font list]),
  ([#key("F5")], [Position list]),
  ([#key("F6")], [Plugin list]),
  ([#key("F7")], [Make (build)]),
  ([#key("F8")], [Terminal / shell]),
  ([#key("F11")], [Toggle fullscreen]),
)

== Word Movement

Javi supports the full set of vi word-motion commands:

#cmd-table(
  ([#key("w") / #key("W")], [Forward to start of word / WORD]),
  ([#key("b") / #key("B")], [Backward to start of word / WORD]),
  ([#key("e") / #key("E")], [Forward to end of word / WORD]),
)

A _word_ is a sequence of letters, digits, and underscores. A _WORD_ is
any sequence of non-whitespace characters.

== Character Search on Line

#cmd-table(
  ([#key("f") _char_], [Find _char_ forward on current line]),
  ([#key("F") _char_], [Find _char_ backward on current line]),
  ([#key("t") _char_], [To _char_ forward (cursor before _char_)]),
  ([#key("T") _char_], [To _char_ backward (cursor after _char_)]),
  ([#key(";")], [Repeat last f/F/t/T]),
  ([#key(",")], [Repeat last f/F/t/T in opposite direction]),
)

== Visual Selection

Vi does not have visual mode. Javi adds visual selection similar to vim:

#cmd-table(
  ([#key("v")], [Enter character-wise visual mode]),
  ([#key("V")], [Enter line-wise visual mode]),
)

In visual mode, use movement keys to extend the selection, then apply an
operator:

#cmd-table(
  ([#key("d")], [Delete selection]),
  ([#key("y")], [Yank (copy) selection]),
  ([#key("c")], [Change selection (delete + insert)]),
  ([#key(">") / #key("<")], [Shift selection right / left]),
  ([#key("~")], [Toggle case]),
  ([#key("Escape")], [Exit visual mode]),
)

== Directory Editor (DirEdit)

Vi has no built-in directory browsing. Javi provides a netrw-like
directory editor:

```
:e .          Open current directory
:diredit /path   Open specific directory
```

#cmd-table(
  ([#key("Enter")], [Open file or enter directory]),
  ([#key("-")], [Go to parent directory]),
  ([#key(".")], [Toggle hidden (dot) files]),
  ([#key("s")], [Cycle sort mode (name / size / date / type)]),
  ([#key("R")], [Refresh listing]),
  ([#key("D")], [Delete file under cursor (with confirmation)]),
  ([#key("S")], [Toggle directory in/out of search path]),
  ([#key("q")], [Quit directory browser]),
)

File operations via colon commands:

#cmd-table(
  ([#cmd("diredit_rename _name_")], [Rename file under cursor]),
  ([#cmd("diredit_mkdir _name_")], [Create new subdirectory]),
  ([#cmd("diredit_newfile _name_")], [Create new empty file]),
  ([#cmd("diredit_copy _dest_")], [Copy file under cursor]),
)

== Integrated Terminal

Vi requires you to leave the editor to run shell commands (`:!cmd`
executes and returns). Javi includes a full VT100 terminal emulator
inside the editor. See @sec-shell for details.

== Tags

Javi supports ctags-based navigation like vi:

#cmd-table(
  ([#key("Ctrl-]")], [Jump to tag under cursor]),
  ([#key("Ctrl-T")], [Pop tag stack (return to previous location)]),
  ([#cmd("tagsauto")], [Toggle auto-regenerate ctags on file save]),
  ([#cmd("tagfiles")], [List registered tag files]),
  ([#cmd("tagadd _path_")], [Add an additional tag file]),
)

== Key Binding Customization

Vi has limited `:map` support. Javi provides a layered keymap
architecture:

#cmd-table(
  ([#cmd("mapkey _group_ _key_ _command_")], [Bind a key]),
  ([#cmd("unmapkey _group_ _key_")], [Unbind a key]),
  ([#cmd("keymap")], [Show active keymap chain]),
  ([#cmd("map")], [Show all key bindings]),
  ([#cmd("savemapkeys")], [Save user bindings to disk]),
  ([#cmd("loadmapkeys")], [Load user bindings from disk]),
)

_group_ is either `move` (movement keys) or `edit` (editing/command keys).
_key_ can be a single character, `C-x` (Ctrl), `S-x` (Shift), or names
like `F1`--`F12`, `Up`, `Down`, `Home`, `End`, `PgUp`, `PgDn`.

Bindings are saved to `~/.javi/keybindings`. To auto-load on startup, add
`loadmapkeys` to your `.javini` file.

== File List (F2)

Pressing #key("F2") opens the file list, showing all open buffers. The
file list uses an overlay keymap where #key("Enter") opens the file at
the cursor instead of moving down a line. All other normal-mode keys
work as usual.

#cmd-table(
  ([#key("Enter") / #key("F1")], [Open file at cursor]),
  ([#key("Ctrl-F1")], [Open and show position list]),
  ([#key("Shift-F1")], [Open in split view]),
  ([#key("F2")], [Return to previous buffer]),
)

== Settings

#cmd-table(
  ([#cmd("set _option_=_value_")], [Set an editor option]),
  ([#cmd("tabstop _n_")], [Set tab display width]),
  ([#cmd("lines _n_")], [Set default window height]),
  ([#cmd("setwidth _n_")], [Set default window width]),
)

// ============================================================================
// 3. BASIC COMMANDS REFERENCE
// ============================================================================

= Basic Commands Reference

This section provides a condensed reference of the most commonly used
commands. For the full reference, use #cmd("help _topic_") within Javi.

== Movement

#cmd-table(
  ([#key("h") #key("j") #key("k") #key("l")], [Left, down, up, right]),
  ([#key("0") #key("^") #key("$")], [Start of line, first non-blank, end of line]),
  ([#key("w") #key("b") #key("e")], [Word forward, word backward, end of word]),
  ([#key("gg")], [Go to first line]),
  ([#key("G")], [Go to last line (or line _n_ with _n_`G`)]),
  ([#key("H") #key("M") #key("L")], [Top, middle, bottom of screen]),
  ([#key("Ctrl-F") / #key("Ctrl-B")], [Page forward / backward]),
  ([#key("Ctrl-D") / #key("Ctrl-U")], [Half-page down / up]),
  ([#key("%")], [Jump to matching bracket]),
)

== Editing

#cmd-table(
  ([#key("i") / #key("a")], [Insert before / append after cursor]),
  ([#key("I") / #key("A")], [Insert at line start / append at line end]),
  ([#key("o") / #key("O")], [Open line below / above]),
  ([#key("x")], [Delete character under cursor]),
  ([#key("dd")], [Delete entire line]),
  ([#key("d") _motion_], [Delete with motion (e.g., `dw`, `d$`)]),
  ([#key("cc")], [Change entire line]),
  ([#key("c") _motion_], [Change with motion]),
  ([#key("r") _char_], [Replace single character]),
  ([#key("R")], [Enter replace (overwrite) mode]),
  ([#key("yy")], [Yank (copy) entire line]),
  ([#key("p") / #key("P")], [Paste after / before cursor]),
  ([#key("J")], [Join current line with next]),
  ([#key(".")], [Repeat last change]),
  ([#key(">>") / #key("<<")], [Shift current line right / left]),
)

== Search and Replace

#cmd-table(
  ([`/`_pattern_], [Search forward]),
  ([`?`_pattern_], [Search backward]),
  ([#key("n") / #key("N")], [Repeat search same / opposite direction]),
  ([#cmd("s/old/new/")], [Substitute first occurrence on current line]),
  ([#cmd("s/old/new/g")], [Substitute all on current line]),
  ([#cmd("%s/old/new/g")], [Substitute all in entire file]),
)

== File Operations

#cmd-table(
  ([#cmd("e _file_")], [Edit file]),
  ([#cmd("e!")], [Reload current file (discard changes)]),
  ([#cmd("w")], [Write (save) file]),
  ([#cmd("w _file_")], [Write to named file]),
  ([#cmd("wq")], [Write and quit]),
  ([#cmd("q")], [Quit (fails if unsaved changes)]),
  ([#cmd("q!")], [Quit without saving]),
  ([#cmd("r _file_")], [Read file contents into buffer]),
  ([#key("Ctrl-G")], [Show file status information]),
  ([#key("Ctrl-^")], [Switch to alternate file]),
)

== Ex Commands

#cmd-table(
  ([#cmd("_n_")], [Go to line _n_]),
  ([#cmd("_n_,_m_ d")], [Delete lines _n_ through _m_]),
  ([#cmd("_n_,_m_ y")], [Yank lines _n_ through _m_]),
  ([#cmd("_n_,_m_ m _k_")], [Move lines to after line _k_]),
  ([#cmd("g/_pattern_/d")], [Delete all lines matching _pattern_]),
  ([#cmd("v/_pattern_/d")], [Delete all lines NOT matching _pattern_]),
  ([#cmd("!_cmd_")], [Run external shell command]),
  ([#cmd("mk")], [Run make (build)]),
)

// ============================================================================
// 4. SHELL / TERMINAL
// ============================================================================

= Shell / Terminal
<sec-shell>

Javi includes a full integrated VT100 terminal emulator. You can run
shell sessions, SSH connections, and interactive programs (vim, htop,
etc.) without leaving the editor.

== Starting a Shell

#cmd-table(
  ([#key("F8")], [Open or toggle to shell]),
  ([#cmd("shell")], [Alias for F8]),
  ([#cmd("shell _host_")], [Open SSH session to _host_]),
  ([#cmd("shell _n_")], [Switch to shell by ID]),
  ([#cmd("shellnew")], [Create a new shell session]),
)

The first time you press #key("F8"), a new shell session starts. The
terminal runs under `script` for PTY support, with `TERM=xterm`,
`COLUMNS`, and `LINES` sent automatically.

== Passthrough Mode

In a shell buffer, #key("F8") toggles *passthrough mode*:

- *Normal mode* --- vi keybindings are active over the shell output.
  You can scroll, search, and yank text.
- *Passthrough mode* --- all keystrokes go directly to the shell. Use
  this for interactive programs (vim, top, etc.). Press #key("F8") again
  to return to normal mode.

You can also press #key("i") in a shell buffer to enter passthrough mode.

== Managing Multiple Shells

#cmd-table(
  ([#cmd("shells")], [List all active shells in a buffer]),
  ([#cmd("shellnext")], [Switch to next shell]),
  ([#cmd("shellprev")], [Switch to previous shell]),
  ([#cmd("shellname _name_")], [Rename current shell]),
  ([#cmd("shellclose")], [Close current shell and kill its process]),
  ([#cmd("shellclose _n_")], [Close shell by ID]),
  ([#cmd("shellenv K=V")], [Set environment variable in current shell]),
  ([#cmd("shellhistory")], [Open full scrollback in read-only buffer]),
)

#key("ZZ") in a shell buffer closes the shell and kills the process.

The shell list appears in the plugin list (#key("F6")) once at least one
shell has been created.

#note-box[
  *Tip:* Use #cmd("shellname") to give meaningful names to your shells
  (e.g., "build", "test", "ssh-prod") for easier identification in the
  shell list.
]

// ============================================================================
// 5. AI INTEGRATION
// ============================================================================

= AI Integration (Copilot)

Javi integrates with GitHub Copilot for AI-assisted coding: interactive
chat, code explanation, code review, documentation generation, and code
completion.

== Setup

=== Authentication

Authenticate with GitHub Copilot using the device flow:

```
:ai auth
```

This initiates the OAuth device flow --- Javi displays a URL and a
one-time code. Open the URL in a browser, enter the code, and authorize
the application. The access token is stored in memory for the session.

=== Configuration

AI settings use the #lit("ai.") prefix with the #cmd("set") command:

#config-table(
  ([#lit("ai.provider")], [Provider: `copilot` (default), `openai`, or `anthropic`]),
  ([#lit("ai.model")], [Model identifier (provider-specific default if unset)]),
  ([#lit("ai.apikey")], [API key (or set `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` env var)]),
  ([#lit("ai.maxTokens")], [Maximum response token length (default: 2048)]),
  ([#lit("ai.prompt")], [Custom system prompt (uses built-in default if unset)]),
)

Example:
```
:set ai.provider=copilot
:ai auth
:ai test
```

== Commands

All AI commands use the #cmd("ai") prefix:

#cmd-table(
  ([#cmd("ai _message_")], [Send a chat message directly]),
  ([#cmd("ai chat")], [Open interactive chat prompt]),
  ([#cmd("ai explain")], [Explain code in the current buffer]),
  ([#cmd("ai review")], [Review current buffer for bugs and issues]),
  ([#cmd("ai doc")], [Generate Javadoc for current buffer code]),
  ([#cmd("ai complete")], [Request code completion at cursor]),
  ([#cmd("ai cancel")], [Cancel an in-flight AI request]),
  ([#cmd("ai refactor")], [Refactor code with instructions]),
  ([#cmd("ai config")], [Display current AI configuration]),
  ([#cmd("ai clear")], [Clear conversation history and chat buffer]),
  ([#cmd("ai test")], [Test provider connectivity]),
  ([#cmd("ai auth")], [Authenticate with Copilot (device flow)]),
  ([#cmd("ai models")], [List available models]),
)

== Chat Buffer

AI responses appear in a dedicated `*ai-chat*` buffer. The buffer is
created on first use and reused across interactions. Conversation history
persists across #cmd("ai chat") calls until #cmd("ai clear") is invoked.

One-shot commands (#cmd("ai explain"), #cmd("ai review"), #cmd("ai doc"))
do not modify the conversation history.

Chat requests run asynchronously on a background thread --- the editor
remains responsive while waiting for a response.

== Example Session

```
:ai auth                          " authenticate with Copilot
:ai test                          " verify connectivity
:ai What is a record in Java?     " ask a question
:ai explain                       " explain current file
:ai review                        " review current file for issues
:ai doc                           " generate javadoc
:ai clear                         " reset conversation
```

#warning-box[
  *Note:* The #cmd("ai review") command may take longer for large files
  due to the volume of context sent to the server. The editor remains
  usable during the request.
]

// ============================================================================
// 6. GIT INTEGRATION
// ============================================================================

= Git Integration

Javi provides Magit-inspired Git integration through colon commands.
All commands require Git to be installed and the editor to be running
inside a Git repository.

== Commands

#cmd-table(
  ([#cmd("git_status")], [Show staged, unstaged, and untracked files]),
  ([#cmd("git_stage _file_")], [Stage a file (`git add`)]),
  ([#cmd("git_unstage _file_")], [Unstage a file (`git restore --staged`)]),
  ([#cmd("git_commit")], [Open commit message editor showing staged changes]),
  ([#cmd("git_diff")], [Show `git diff` output in a buffer]),
  ([#cmd("git_diff _file_")], [Show diff for a specific file]),
  ([#cmd("git_log")], [Show last 30 log entries (oneline, graph)]),
  ([#cmd("git_branch")], [Show all branches with latest commit]),
)

=== Additional Commands

#cmd-table(
  ([#cmd("git_stash")], [Stash current changes]),
  ([#cmd("git_stash_pop")], [Pop the most recent stash]),
  ([#cmd("git_stash_list")], [List all stashes]),
  ([#cmd("git_fetch")], [Fetch from remote]),
  ([#cmd("git_pull")], [Pull from remote]),
  ([#cmd("git_push")], [Push to remote]),
  ([#cmd("git_branch_create")], [Create a new branch]),
  ([#cmd("git_branch_switch")], [Switch to another branch]),
  ([#cmd("git_branch_delete")], [Delete a branch]),
  ([#cmd("git_merge")], [Merge a branch]),
  ([#cmd("git_rebase")], [Rebase current branch]),
)

== Status Buffer

The #cmd("git_status") command opens a `*git-status*` buffer showing
the repository state:

```
Head: main (abc1234 "Last commit message")
Push: origin/main (up to date)

Staged changes (2)
  modified   src/main/java/javi/Example.java
  new file   src/main/java/javi/NewFile.java

Unstaged changes (1)
  modified   README.md

Untracked files (2)
  tmp/test.txt
  notes.md
```

Output buffers (`*git-status*`, `*git-diff*`, `*git-log*`,
`*git-branch*`) are standard read-only editor buffers navigable with
normal vi keys.

== Workflow Example

+ Run #cmd("git_status") to see the current repository state.
+ Stage files with #cmd("git_stage src/main/java/javi/Example.java").
+ Run #cmd("git_commit") to open the commit message editor.
+ View the log with #cmd("git_log") to verify your commit.
+ Check diffs with #cmd("git_diff") to review changes.

#note-box[
  *Note:* If the current directory is not inside a Git repository, all
  git commands report "Not a git repository".
]

// ============================================================================
// 7. LSP INTEGRATION
// ============================================================================

= LSP Integration (Language Server Protocol)

Javi includes a Language Server Protocol client for modern IDE features:
go-to-definition, find references, hover information, diagnostics, and
code completion. LSP support works with any standard language server.

== Commands

#cmd-table(
  ([#cmd("lspdef")], [Go to definition of symbol under cursor]),
  ([#cmd("lspref")], [Find all references to symbol under cursor]),
  ([#cmd("lsphover")], [Show hover information (type, documentation)]),
  ([#cmd("lspdiag")], [Show diagnostics (errors, warnings) for current file]),
  ([#cmd("lspcomp")], [Trigger code completion at cursor]),
  ([#cmd("lspstatus")], [Show LSP server status]),
  ([#cmd("lsprestart")], [Restart the LSP server for the current file type]),
  ([#cmd("lsptoggle")], [Enable/disable LSP for the current session]),
)

== Default Key Bindings

#cmd-table(
  ([#key("F12")], [Go to definition (#cmd("lspdef"))]),
  ([#key("Shift-F12")], [Find references (#cmd("lspref"))]),
  ([#key("Ctrl-K")], [Hover information (#cmd("lsphover"))]),
  ([#key("F9")], [Code completion (#cmd("lspcomp"))]),
  ([#key("Ctrl-]")], [Go to definition (falls back to ctags if no LSP)]),
)

When LSP is available for the current file type, #key("Ctrl-]") uses LSP
go-to-definition. If no LSP server is running, it falls back to ctags.

== How LSP Works

When you open a file, Javi checks if a language server is configured for
the file's extension. If found, it starts the server process and
communicates via the standard LSP JSON-RPC protocol over stdio.

The LSP client handles:
- *Document synchronization* --- edits are sent incrementally to the
  server as you type.
- *Request/response* --- definition, references, hover, and completion
  requests use the LSP message protocol.
- *Diagnostics* --- the server pushes error and warning information which
  Javi displays on request via #cmd("lspdiag").

== Configuring Language Servers

Javi includes built-in configurations for common language servers. The
server binary must be installed on your system.

=== Built-in Server Configurations

#table(
  columns: (auto, auto, auto, auto),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  align: (left, left, left, left),
  [*Language*], [*Server*], [*Command*], [*Root Pattern*],
  [Java], [Eclipse JDT-LS], [#lit("jdtls")], [#lit("build.gradle")],
  [TypeScript/JS], [typescript-language-server], [#lit("typescript-language-server --stdio")], [#lit("package.json")],
  [Python], [Pyright], [#lit("pyright-langserver --stdio")], [#lit("pyproject.toml")],
  [C/C++], [clangd], [#lit("clangd")], [#lit("compile_commands.json")],
)

Javi searches common installation paths for each server
(#lit("/opt/homebrew/bin"), #lit("/usr/local/bin"), #lit("/usr/bin")).

=== Installing Language Servers

*Java (Eclipse JDT Language Server):*
```bash
# macOS (Homebrew)
brew install jdtls
```

*C/C++ (clangd):*
```bash
# macOS - usually included with Xcode Command Line Tools
# or install via Homebrew:
brew install llvm
# clangd is at /opt/homebrew/opt/llvm/bin/clangd
```

*TypeScript/JavaScript:*
```bash
npm install -g typescript-language-server typescript
```

*Python (Pyright):*
```bash
npm install -g pyright
# or
pip install pyright
```

=== Custom Server Configuration

Server configurations are persisted in `~/.javi/lsp.conf`. You can
add or override servers in this file. The format is one server per line:

```
language_id = command | extensions | root_pattern
```

Example:
```
rust = rust-analyzer | .rs | Cargo.toml
go = gopls | .go | go.mod
```

Use #cmd("lspstatus") to verify which servers are available and running.

#tip-box[
  *Tip:* Javi can use the same language servers that VS Code uses.
  VS Code's language servers are standalone processes speaking the LSP
  protocol --- they work identically with Javi.
]

=== Project Root Detection

When starting a language server, Javi walks up the directory tree from
the current file looking for the _root pattern_ file (e.g.,
#lit("build.gradle"), #lit("package.json")). This directory becomes the
project root sent to the language server in the `initialize` request.

If no root pattern file is found, the directory containing the current
file is used as the project root.

// ============================================================================
// APPENDIX A: COMPREHENSIVE KEY BINDING REFERENCE
// ============================================================================

= Appendix A: Comprehensive Key Binding Reference

This appendix provides a complete reference of every key binding and
colon command registered in Javi, organized by category. Bindings are
derived directly from the source code (`MapEvent.java`, `KeyMap.java`,
and the various `Rgroup` command classes).

== Modes

#cmd-table(
  ([#key("i") #key("a") #key("o") #key("O")], [Enter insert mode]),
  ([#key("v") / #key("V")], [Enter visual mode (char / line)]),
  ([#key("R")], [Enter replace (overwrite) mode]),
  ([#key("Escape")], [Return to command mode]),
  ([#key(":")], [Enter ex command line]),
  ([#key("i") (in shell)], [Enter shell passthrough mode]),
)

== Normal Mode --- Movement Keys

Movement keys can be used standalone or as the _motion_ argument to
operators like #key("d"), #key("c"), #key("y"), and #key(">")/#key("<").

=== Cursor Movement

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("h") / #key("Backspace")], [Move cursor left],
  [#key("l")], [Move cursor right],
  [#key("j") / #key("Down")], [Move cursor down],
  [#key("k") / #key("Up")], [Move cursor up],
  [#key("Left")], [Move cursor left],
  [#key("Right")], [Move cursor right],
  [#key("Space")], [Move over (right, non-editing)],
)

=== Word Movement

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("w")], [Forward to start of word],
  [#key("W")], [Forward to start of WORD (non-whitespace)],
  [#key("b")], [Backward to start of word],
  [#key("B")], [Backward to start of WORD],
  [#key("e")], [Forward to end of word],
  [#key("E")], [Forward to end of WORD],
  [#key("Ctrl-Left")], [Backward word],
  [#key("Ctrl-Right")], [Forward word],
)

=== Line Position

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("0")], [Start of line (column 0)],
  [#key("^")], [First non-blank character],
  [#key("$") / #key("End")], [End of line],
  [#key("|")], [Go to column _n_ (with count)],
  [#key("+") / #key("Enter")], [Move to first non-blank of next line],
  [#key("-")], [Move to first non-blank of previous line],
  [#key("Home")], [Start of line],
)

=== Jumping and Go-To

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("G")], [Go to last line (or line _n_ with _n_`G`)],
  [#key("Shift-Home") / #key("Ctrl-Home")], [Go to first line],
  [#key("Ctrl-End")], [Go to last line],
  [#key("Shift-End")], [Go to last line],
  [#key("H")], [Move to top of screen],
  [#key("M")], [Move to middle of screen],
  [#key("L")], [Move to bottom of screen],
  [#key("%")], [Jump to matching bracket],
)

=== Scrolling

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("Ctrl-F") / #key("PgDn")], [Scroll forward one full page],
  [#key("Ctrl-B") / #key("PgUp")], [Scroll backward one full page],
  [#key("Ctrl-D")], [Scroll forward half page],
  [#key("Ctrl-U")], [Scroll backward half page],
  [#key("Ctrl-E") / #key("Ctrl-Down")], [Scroll one line down],
  [#key("Ctrl-Y") / #key("Ctrl-Up")], [Scroll one line up],
)

=== Character Search on Line

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("f") _char_], [Find _char_ forward on current line],
  [#key("F") _char_], [Find _char_ backward on current line],
  [#key("t") _char_], [Move to just before _char_ forward],
  [#key("T") _char_], [Move to just after _char_ backward],
  [#key(";")], [Repeat last f/F/t/T],
  [#key(",")], [Repeat last f/F/t/T in opposite direction],
)

=== Search

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("/") _pattern_], [Search forward for _pattern_ (Java regex)],
  [#key("?") _pattern_], [Search backward for _pattern_],
  [#key("n")], [Repeat search in same direction],
  [#key("N")], [Repeat search in opposite direction],
  [#key("Ctrl-F3")], [Search forward (via edit key)],
)

=== Regex-Based Motion (Sentences, Paragraphs, Sections)

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key(")")], [Forward to next sentence (`. ` or `.$`)],
  [#key("(")], [Backward to previous sentence],
  [#key("}")], [Forward to next blank line (paragraph)],
  [#key("{")], [Backward to previous blank line],
  [#key("]")], [Forward to next section (line starting with non-space + `{`)],
  [#key("[")], [Backward to previous section],
)

=== Marks

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("m") _char_], [Set mark _char_ at current position],
  [#key("'") _char_], [Jump to mark _char_],
)

== Normal Mode --- Editing Keys

=== Entering Insert Mode

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("i")], [Insert before cursor],
  [#key("I")], [Insert at first non-blank of line],
  [#key("a")], [Append after cursor],
  [#key("A")], [Append at end of line],
  [#key("o")], [Open new line below],
  [#key("O")], [Open new line above],
  [#key("s")], [Substitute character (delete + insert)],
  [#key("S")], [Substitute entire line],
  [#key("R")], [Enter replace (overwrite) mode],
  [#key("Insert")], [Enter insert mode],
)

=== Deleting

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("x") / #key("Delete")], [Delete character under cursor],
  [#key("X") / #key("Backspace")], [Delete character before cursor],
  [#key("d") _motion_], [Delete with motion (e.g., `dw`, `d$`)],
  [#key("dd")], [Delete entire line],
  [#key("D") / #key("Shift-Delete")], [Delete from cursor to end of line],
)

=== Changing

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("c") _motion_], [Change with motion (delete + insert)],
  [#key("cc")], [Change entire line],
  [#key("C")], [Change from cursor to end of line],
  [#key("r") _char_], [Replace single character under cursor],
  [#key("~")], [Toggle case of character under cursor],
)

=== Yank and Put

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("y") _motion_], [Yank (copy) with motion],
  [#key("yy") / #key("Y")], [Yank entire line],
  [#key("p")], [Put (paste) after cursor],
  [#key("P")], [Put (paste) before cursor],
  [#key("\"") _reg_], [Use named register _reg_ for next yank/put],
)

=== Undo and Redo

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("u")], [Undo last change],
  [#key("Ctrl-Z")], [Undo (alternate)],
  [#key("Alt-Backspace")], [Undo (alternate)],
  [#key("U")], [Undo all changes on current line],
  [#key("Ctrl-R")], [Redo],
  [#key("Ctrl-Y")], [Redo (alternate)],
  [#key("Ctrl-Shift-Z")], [Redo (alternate)],
  [#key("Alt-Shift-Backspace")], [Redo (alternate)],
)

=== Visual Mode

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("v")], [Enter character-wise visual mode],
  [#key("V")], [Enter line-wise visual mode],
  [#key("Escape")], [Exit visual mode],
  [#key("d")], [Delete selection],
  [#key("y")], [Yank selection],
  [#key("c")], [Change selection],
  [#key(">") / #key("<")], [Shift selection right / left],
  [#key("~")], [Toggle case of selection],
)

=== Shift / Indent

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key(">>")], [Shift current line right],
  [#key("<<")], [Shift current line left],
  [#key("Shift-Up")], [Shift-move up (for line shifting)],
  [#key("Shift-Down")], [Shift-move down (for line shifting)],
)

=== Miscellaneous Normal Mode

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key(".")], [Repeat last editing command],
  [#key("J")], [Join current line with next],
  [#key("z")], [Z-position scroll (`zt`, `zz`, `zb`)],
  [#key("Z")], [Z-process (ZZ = save and quit, ZQ = quit)],
  [#key("Ctrl-L")], [Redraw screen],
  [#key("Ctrl-G")], [Show file status information],
  [#key(":")], [Enter ex command line],
  [#key("Alt-J")], [Evaluate current file as JavaScript],
)

== Function Keys

Function keys provide quick access to Javi features. Some have
modifier-key variants.

#table(
  columns: (auto, auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Modifier*], [*Action*],
  [#key("F1")], [---], [Next position in position list],
  [#key("F1")], [Shift], [Previous position in position list],
  [#key("F1")], [Ctrl], [Next position with wait],
  [#key("F2")], [---], [File list (open buffers)],
  [#key("F3")], [---], [Directory list],
  [#key("F3")], [Ctrl], [Search forward (regex search)],
  [#key("F4")], [---], [Font list],
  [#key("F5")], [---], [Position list],
  [#key("F6")], [---], [Plugin list],
  [#key("F7")], [---], [Make (build)],
  [#key("F8")], [---], [Open / toggle shell],
  [#key("F10")], [---], [Communication channel],
  [#key("F11")], [---], [Toggle fullscreen],
)

== Tags and Navigation

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("Ctrl-]")], [Jump to tag under cursor (ctags)],
  [#key("Ctrl-T")], [Pop tag stack (return to previous location)],
  [#key("Ctrl-^")], [Switch to alternate (previous) file],
)

== Insert Mode Keys

While in insert mode, these special keys are active:

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("Escape") / #key("Ctrl-]")], [Complete and exit insert mode],
  [#key("Tab")], [Insert tab],
  [#key("Backspace")], [Delete character before cursor],
  [#key("Delete")], [Delete character under cursor],
  [#key("Insert")], [Toggle insert/overwrite mode],
  [#key("Up")], [Move to previous line (while inserting)],
  [#key("Down")], [Move to next line (while inserting)],
  [#key("Ctrl-V")], [Verbatim (insert next character literally)],
  [#key("Ctrl-P")], [Put (paste) buffer contents],
)

== Buffer-Specific Overlay Keymaps

Different buffer types activate overlay keymaps that modify or extend
the normal-mode bindings.

=== File List (F2)

When viewing the file list buffer, these bindings replace their
normal-mode equivalents:

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("Enter")], [Open file at cursor (replaces move-to-next-line)],
  [#key("F1")], [Open file at cursor],
  [#key("Ctrl-F1")], [Open and show position list],
  [#key("Shift-F1")], [Open in split view],
  [#key("F2")], [Return to previous buffer],
)

All other normal-mode keys remain active.

=== Directory Editor (DirEdit)

When viewing a directory, these bindings are active:

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("Enter")], [Open file or enter directory],
  [#key("-")], [Go to parent directory],
  [#key(".")], [Toggle hidden (dot) files],
  [#key("s")], [Cycle sort mode (name / size / date / type)],
  [#key("S")], [Toggle directory in/out of search path],
  [#key("R")], [Refresh listing],
  [#key("D")], [Delete file under cursor],
  [#key("o") / #key("O")], [Create new file or directory (inline prompt)],
  [#key("q")], [Quit directory browser],
)

=== Shell Buffer

When viewing a shell (VT100) buffer in normal mode:

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("i") / #key("Insert")], [Enter passthrough mode (keys go to shell)],
  [#key("F8")], [Toggle passthrough mode],
  [#key("ZZ")], [Close shell and kill process],
)

== Ex Commands Reference

All colon commands available in Javi. Type #key(":") followed by the
command name and press Enter.

=== File Operations

#cmd-table(
  ([#cmd("e _file_")], [Edit (open) file]),
  ([#cmd("e!")], [Reload current file, discarding changes]),
  ([#cmd("vi _file_")], [Edit file (alias for `:e`)]),
  ([#cmd("w")], [Write (save) file]),
  ([#cmd("w _file_")], [Write to named file]),
  ([#cmd("wq")], [Write and quit]),
  ([#cmd("x")], [Write (if changed) and quit]),
  ([#cmd("q")], [Quit (fails if unsaved changes)]),
  ([#cmd("q!")], [Quit without saving]),
  ([#cmd("r _file_")], [Read file contents into buffer]),
  ([#cmd("r <_cmd_")], [Read shell command output into buffer]),
)

=== Line Range Commands (Ex Mode)

These commands accept line number ranges (e.g., #cmd("3,7d")):

#cmd-table(
  ([#cmd("_n_")], [Go to line _n_]),
  ([#cmd("_n_,_m_ d")], [Delete lines _n_ through _m_]),
  ([#cmd("_n_,_m_ y")], [Yank lines _n_ through _m_]),
  ([#cmd("_n_,_m_ m _k_")], [Move lines to after line _k_]),
  ([#cmd("_n_,_m_ t _k_") / #cmd("_n_,_m_ co _k_")], [Copy lines to after line _k_]),
  ([#cmd("s/old/new/")], [Substitute first occurrence on current line]),
  ([#cmd("s/old/new/g")], [Substitute all on current line]),
  ([#cmd("%s/old/new/g")], [Substitute all in entire file]),
  ([#cmd("g/_pattern_/d")], [Delete all lines matching _pattern_]),
  ([#cmd("v/_pattern_/d")], [Delete all lines NOT matching _pattern_]),
)

=== Editor Settings

#cmd-table(
  ([#cmd("set _option_=_value_")], [Set an editor option]),
  ([#cmd("tabstop _n_")], [Set tab display width]),
  ([#cmd("lines _n_")], [Set default window height]),
  ([#cmd("setwidth _n_")], [Set default window width]),
)

=== Shell / Terminal Commands

#cmd-table(
  ([#cmd("shell") / #cmd("vt")], [Open or toggle to shell]),
  ([#cmd("shell _host_")], [Open SSH session to _host_]),
  ([#cmd("shell _n_")], [Switch to shell by ID]),
  ([#cmd("shellnew")], [Create a new shell session]),
  ([#cmd("shells")], [List all active shells]),
  ([#cmd("shellnext")], [Switch to next shell]),
  ([#cmd("shellprev")], [Switch to previous shell]),
  ([#cmd("shellname _name_")], [Rename current shell]),
  ([#cmd("shellclose")], [Close current shell]),
  ([#cmd("shellclose _n_")], [Close shell by ID]),
  ([#cmd("shellenv K=V")], [Set environment variable in current shell]),
  ([#cmd("shellhistory")], [Open full scrollback in read-only buffer]),
)

=== Tags and Navigation

#cmd-table(
  ([#cmd("ta _tag_")], [Jump to tag]),
  ([#cmd("gototag")], [Jump to tag under cursor]),
  ([#cmd("poptag")], [Pop tag stack]),
  ([#cmd("tagsauto")], [Toggle auto-regenerate ctags on file save]),
  ([#cmd("tagfiles")], [List registered tag files]),
  ([#cmd("tagadd _path_")], [Add an additional tag file]),
  ([#cmd("cn")], [Go to next position in position list],),
  ([#cmd("cp")], [Go to previous position in position list],),
)

=== Directory Editor Commands

#cmd-table(
  ([#cmd("diredit _path_")], [Open directory editor]),
  ([#cmd("diredit_open")], [Open file/directory under cursor]),
  ([#cmd("diredit_parent")], [Go to parent directory]),
  ([#cmd("diredit_rename _name_")], [Rename file under cursor]),
  ([#cmd("diredit_mkdir _name_")], [Create new subdirectory]),
  ([#cmd("diredit_newfile _name_")], [Create new empty file]),
  ([#cmd("diredit_copy _dest_")], [Copy file under cursor]),
  ([#cmd("diredit_delete")], [Delete file under cursor]),
  ([#cmd("diredit_mark")], [Toggle delete mark on file]),
  ([#cmd("diredit_execute")], [Execute marked operations]),
  ([#cmd("diredit_shell")], [Open shell in current directory]),
  ([#cmd("diredit_create")], [Create new file or directory (inline prompt)]),
)

=== Key Binding Customization

#cmd-table(
  ([#cmd("mapkey _group_ _key_ _command_")], [Bind a key in group]),
  ([#cmd("unmapkey _group_ _key_")], [Unbind a key]),
  ([#cmd("map")], [Show all key bindings for current context]),
  ([#cmd("map _keymap_")], [Show bindings for named keymap]),
  ([#cmd("keymap")], [Show active keymap chain]),
  ([#cmd("savemapkeys")], [Save user bindings to #lit("~/.javi/keybindings")]),
  ([#cmd("loadmapkeys")], [Load user bindings from disk]),
)

For #cmd("mapkey"), _group_ is `move` or `edit`. _key_ can be a single
character, `C-x` (Ctrl), `S-x` (Shift), or names like `F1`--`F12`,
`Up`, `Down`, `Home`, `End`, `PgUp`, `PgDn`, `Insert`, `Delete`.

=== Build and Compile

#cmd-table(
  ([#cmd("mk")], [Run make (build via make.pl)]),
  ([#cmd("cc")], [Run cc (compile via make.pl)]),
  ([#cmd("comp")], [Compile with Java compiler]),
  ([#cmd("compa")], [Compile all with Java compiler]),
  ([#cmd("cstyle")], [Run Checkstyle on current file]),
)

=== Help System

#cmd-table(
  ([#cmd("help")], [Show help index (context-sensitive)]),
  ([#cmd("help _topic_")], [Show help for specific topic]),
)

Available topics: `index`, `movement`, `editing`, `search`, `files`,
`ex`, `visual`, `undo`, `window`, `shell`, `diredit`, `filelist`,
`directory`, `keybindings`.

=== JavaScript Integration

#cmd-table(
  ([#cmd("jseval _expr_")], [Evaluate JavaScript expression]),
  ([#cmd("jsevalfile")], [Evaluate current file as JavaScript]),
  ([#cmd("jsclear")], [Clear JavaScript output buffer]),
)

=== Miscellaneous

#cmd-table(
  ([#cmd("!")], [Run external shell command]),
  ([#cmd("fl")], [Go to file list]),
  ([#cmd("rep")], [Report / repeat]),
  ([#cmd("te")], [Toggle editor state]),
  ([#cmd("loadgroup")], [Load command group]),
  ([#cmd("persistfile _name_")], [Set persistent file name]),
  ([#cmd("tabfix")], [Fix tab/space indentation]),
  ([#cmd("fullscreen")], [Toggle fullscreen mode]),
)

// ============================================================================
// APPENDIX B: ACCURACY NOTES
// ============================================================================

= Appendix B: Accuracy Notes

The following features described in this manual are planned but *not yet
implemented* in the codebase as of March 2026. The corresponding
sections (5, 6, 7) describe the intended design:

- *AI Integration* (Section 5) --- The `:ai` commands, Copilot
  authentication, and chat buffer are not yet implemented. No
  `AiCommands` class or related command registrations exist in the
  source code.

- *Git Integration* (Section 6) --- The `:git_status`, `:git_stage`,
  `:git_commit`, and related commands are not yet implemented. No
  `GitCommands` class or related command registrations exist.

- *LSP Integration* (Section 7) --- The `:lspdef`, `:lspref`,
  `:lsphover`, and related commands are not yet implemented. No LSP
  client, F12/Shift-F12/F9/Ctrl-K bindings for LSP, or language
  server configuration system exist in the source code.

These sections document the planned feature design for future
implementation. All other sections accurately reflect the current
codebase.
