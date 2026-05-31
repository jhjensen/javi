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
  date: "May 2026",
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

== Building from Source

Javi uses Gradle (wrapper included) and requires Java 22 or later.
A `makefile` provides convenient targets for common operations.

=== Quick Build and Run

```bash
make compile                          # compile Java sources
make fatjar                           # build fat JAR (all dependencies bundled)
java -jar build/libs/javi-all.jar     # run your local build
```

The fat JAR at `build/libs/javi-all.jar` is self-contained and behaves
identically to a released binary.

=== Installing Locally

To install a development build so that `javi` is available on your PATH:

```bash
make install                          # installs to ~/.local (default)
make install PREFIX=/opt/javi         # or choose a different prefix
```

This creates:
- `PREFIX/share/javi/lib/` --- application JARs
- `PREFIX/share/javi/bin/javi` --- launcher script (sets classpath)
- `PREFIX/bin/javi` --- symlink to the launcher

Ensure `~/.local/bin` (or your chosen `PREFIX/bin`) is on your PATH.
After installing, simply run `javi [file ...]` from anywhere.

=== Development Cycle

A typical edit-compile-test cycle:

```bash
make compile          # recompile after edits
make junit            # run JUnit 5 tests (primary test suite)
make test             # run legacy tests
make fatjar           # rebuild fat JAR when ready to test interactively
```

Re-run `make install` after `make fatjar` to update the installed copy.

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

#screenshot-placeholder[Javi main editor window showing a Java source file in command mode]

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
  ([#cmd("help folding")], [Folding commands and detection]),
  ([#cmd("help git")], [Git integration commands]),
)

Help content is displayed in a read-only buffer navigable with normal vi
movement keys.

== Context-Sensitive Help Panel

Javi includes a context-sensitive help side panel that dynamically shows
the key bindings available in the current context. Unlike #cmd("help")
topics which display static documentation, the context help panel
queries the live keymap system and updates as you switch buffers or
modes.

#cmd-table(
  ([#key("Shift-F1")], [Toggle context-sensitive help side panel]),
  ([#cmd("contexthelp")], [Toggle context-sensitive help side panel]),
  ([#cmd("helpscrolldown")], [Scroll help panel down]),
  ([#cmd("helpscrollup")], [Scroll help panel up]),
)

The help panel appears as a fixed-width side panel on the right edge of
the editor window. It does not take keyboard focus --- you can continue
editing while the panel is visible.

The panel content adapts to your current context:

- *Normal mode* --- shows all bound keys from the active keymap chain,
  including any overlay bindings (e.g., DirEdit or file list bindings).
- *Insert mode* --- shows insert-mode key bindings.
- *Sub-mode* (e.g., after pressing #key("d") or #key("z")) --- shows
  the available completions for the pending operator.

#note-box[
  *Tip:* Leave the help panel open while learning Javi. It updates
  automatically as you switch between buffers with different keymap
  overlays (shell, directory editor, file list, etc.).
]

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

== Registers and System Clipboard

Javi supports named registers for yank and put operations, similar to vim.
The `"` prefix selects a register for the next yank or put command.

#cmd-table(
  ([#key("\"a") #key("y")], [Yank into register `a`]),
  ([#key("\"a") #key("p")], [Put from register `a`]),
  ([#key("\"*") #key("y")], [Yank into system clipboard]),
  ([#key("\"*") #key("p")], [Put from system clipboard]),
  ([#key("\"\"") #key("p")], [Put from default (unnamed) register]),
)

Register names `a`--`z` are user registers. The `*` register is the
system clipboard — yanking to `"*` copies to the OS clipboard, and
putting from `"*` pastes from it. This works on macOS, Linux (X11),
and Windows via AWT's `Toolkit.getSystemClipboard()`.

Uppercase register names (`A`--`Z`) append to the register instead of
replacing its contents.

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

#screenshot-placeholder[Directory editor showing a file listing with sort and hidden-file indicators]

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

== Tags and ID Lookup

Javi supports ctags-based navigation for jumping to definitions, and
mkid (GNU ID Utils) for finding references:

#cmd-table(
  ([#key("Ctrl-]")], [Jump to definition (LSP first, then ctags)]),
  ([#key("Ctrl-T")], [Pop tag stack (return to previous location)]),
  ([#cmd("ta _tag_")], [Jump to named tag (LSP first, then ctags)]),
)

When the LSP plugin is loaded, #key("Ctrl-]") and #cmd("ta") try LSP
go-to-definition first. If no LSP server is running or it returns no
result, the lookup falls through to ctags. When looking up via ctags,
Javi also automatically searches the `mkid` ID database (built with
GNU ID Utils) to find references. Both ctag definitions and `lid`
cross-references
are combined in the tag results buffer.

#warning-box[
  *Not yet implemented:* The commands #cmd("tagsauto"), #cmd("tagfiles"),
  #cmd("tagadd"), and #cmd("gid") are planned but do not exist in the
  current codebase. Tag files are loaded from the default `tags` file
  in the project directory.
]

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

Pressing #key("F2") opens the file list, showing all files in the
editor. The file list uses an overlay keymap where #key("Enter") opens
the file at the cursor instead of moving down a line. All other
normal-mode keys work as usual.

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
  ([_n_#key("G")], [Go to line _n_ (e.g., `1G` = first line, `G` alone = last line)]),
  ([#key("Shift-Home") / #key("Ctrl-Home")], [Go to first line]),
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
  ([#key("Ctrl-^")], [Switch to next file in file list]),
)

== Ex Commands

Javi implements a subset of POSIX ex commands. For a complete reference
to traditional ex/vi commands, see the
#link("https://pubs.opengroup.org/onlinepubs/9699919799/utilities/ex.html")[POSIX ex specification].

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

*Differences from POSIX ex:*

- Regular expressions use Java syntax (see @sec-differences) instead of
  POSIX BRE.
- The `global` command (`g/pattern/cmd`) supports only `d` (delete);
  arbitrary ex commands after the pattern are not supported.
- The `open` and `visual` mode transitions from ex are not implemented ---
  Javi always starts in visual (normal) mode.
- Address forms `.` (current line) and `$` (last line) are supported;
  `'a` (mark) addresses and relative offsets (`+n`, `-n`) are not.
- The `set` command uses `=` syntax (#cmd("set option=value")) rather
  than the POSIX toggle form.

// ============================================================================
// 4. FOLDING
// ============================================================================

= Folding
<sec-folding>

Folding lets you collapse regions of text into a single summary line,
reducing visual clutter when working with large files. Collapsed regions
hide their contents from view but remain in the buffer --- unfolding
restores the full text. Fold state is preserved across sessions via
`.foldstate` files.

== Fold Detection Methods

Before using fold commands, you must detect folds using one of the three
detection methods. Each creates a set of fold regions based on different
heuristics.

=== Syntax-Based Folding (`:fold`)

The #cmd("fold") command detects folds by matching braces and brackets.
Every matched `{`...`}` or `[`...`]` pair that spans multiple lines
becomes a foldable region. This works well for JSON, Java, C, and other
brace-delimited languages.

```
:fold
```

=== Indent-Based Folding (`:foldindent`)

The #cmd("foldindent") command creates folds based on indentation level,
similar to vim's `foldmethod=indent`. Lines at deeper indentation are
folded under lines at shallower indentation. Blank lines inherit the
minimum indent level of their surrounding non-blank lines so they do not
break folds.

```
:foldindent           " use default tab size (3)
:foldindent 4         " use 4-space indent levels
```

=== Marker-Based Folding (`:foldmarker`)

The #cmd("foldmarker") command detects explicit fold markers in the text.
Place `\{\{\{` to start a fold region and `\}\}\}` to end it. Markers
can include an optional level number (e.g., `\{\{\{1`, `\}\}\}1`) for
nested folds. This gives you precise control over which regions are
foldable.

```
:foldmarker
```

Example in source code:
```
// Section A {{{1
... code ...
// Section B {{{1
... code ...
// }}}1
```

== Fold Commands (Normal Mode)

Once folds are detected, use `z`-prefixed commands in normal mode to
manipulate them:

#cmd-table(
  ([#key("zo")], [Open the fold at the cursor]),
  ([#key("zc")], [Close the fold at the cursor]),
  ([#key("za")], [Toggle the fold at the cursor (open ↔ close)]),
  ([#key("zR")], [Open all folds in the buffer]),
  ([#key("zM")], [Close all folds in the buffer]),
)

These commands operate on the fold that contains the current cursor
line. If the cursor is not inside a fold, a message is displayed.

== Fold Gutter Indicators

When folds are active, the left gutter displays indicators showing fold
state at a glance:

#cmd-table(
  ([#key("+")], [Start of a collapsed (closed) fold]),
  ([#key("-")], [Start of an open fold]),
  ([#key("|")], [Body of an open fold]),
)

A `+` in the gutter means that fold is collapsed --- the lines between
the fold start and end are hidden. Use #key("zo") or #key("za") on that
line to expand it.

#screenshot-placeholder[Fold gutter showing +/\-/| indicators next to collapsed and open folds in a Java file]

== Fold Persistence

Fold state is automatically saved to `.foldstate` files in the same
directory as the source file. When you reopen a file, Javi restores
the previous fold state (which regions were collapsed or open).

The `.foldstate` file is a plain-text file with one fold per line in the
format `startLine:endLine:collapsed`. These files can be safely deleted
to reset fold state, and they are regenerated the next time you run a
fold detection command.

== Search and Folds

When you search (`/pattern` or `?pattern`), Javi automatically opens
any collapsed folds that contain match results. This ensures search
hits are always visible, even in heavily folded files.

== Tips

- Start with #cmd("fold") for brace-delimited files (JSON, Java, C)
  and #cmd("foldindent") for indentation-structured files (Python, YAML).
- Use #key("zM") to collapse everything, then #key("zo") to selectively
  open regions of interest --- this provides a quick overview of large
  files.
- Fold markers (`\{\{\{`/`\}\}\}`) are ideal for hand-curated sections
  in configuration files or documentation.
- Delete the `.foldstate` file to reset folds for a file.

// ============================================================================
// 5. SHELL / TERMINAL
// ============================================================================

= Shell / Terminal
<sec-shell>

Javi includes a full integrated VT100 terminal emulator. You can run
shell sessions, SSH connections, and interactive programs (vim, htop,
etc.) without leaving the editor.

== Starting a Shell

#cmd-table(
  ([#key("F8")], [Open or toggle to shell]),
  ([#cmd("vt")], [Open or toggle to shell (same as F8)]),
  ([#cmd("shellnew")], [Create a new shell session]),
)

The first time you press #key("F8"), a new shell session starts. The
terminal runs under `script` for PTY support, with `TERM=xterm`,
`COLUMNS`, and `LINES` sent automatically.

#screenshot-placeholder[Integrated terminal showing a shell session inside the Javi editor window]

== Passthrough Mode

In a shell buffer, #key("F8") toggles *passthrough mode*:

- *Normal mode* --- vi keybindings are active over the shell output.
  You can scroll, search, yank text, and use editing commands (delete,
  change, etc.) to modify the scrollback buffer.
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
// 6. AI INTEGRATION
// ============================================================================

= AI Integration (Copilot)

Javi integrates with GitHub Copilot for AI-assisted coding: interactive
chat, code explanation, code review, documentation generation, inline
code completion with ghost text, and tool-augmented responses.

== Setup

=== Loading the AI Plugin

The AI plugin is a separate module loaded via `.javini`:

```
loadclass javi.ai.AICommands
```

The AI source set is compiled as part of the standard Gradle build
and included in the fat JAR (`javi-all.jar`).

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
  ([#cmd("ai complete")], [Request inline code completion (ghost text)]),
  ([#cmd("ai accept")], [Accept ghost text completion]),
  ([#cmd("ai dismiss")], [Dismiss ghost text completion]),
  ([#cmd("ai cancel")], [Cancel an in-flight AI request]),
  ([#cmd("ai refactor _instruction_")], [Refactor code with instructions]),
  ([#cmd("ai config")], [Display current AI configuration]),
  ([#cmd("ai clear")], [Clear conversation history and chat buffer]),
  ([#cmd("ai test")], [Test provider connectivity]),
  ([#cmd("ai auth")], [Authenticate with Copilot (device flow)]),
  ([#cmd("ai models")], [List available Copilot models]),
  ([#cmd("ai status")], [Show request tracking and history]),
  ([#cmd("ai tools")], [List registered AI tools]),
  ([#cmd("ai help")], [Show AI command help in chat buffer]),
)

== Normal Mode Key Bindings (ga prefix)

In normal mode, the `g` key followed by `a` enters an AI sub-mode.
The third key selects the command:

#cmd-table(
  ([#key("g")#key("a")#key("r")], [Review current code]),
  ([#key("g")#key("a")#key("e")], [Explain current code]),
  ([#key("g")#key("a")#key("d")], [Generate documentation]),
  ([#key("g")#key("a")#key("f")], [Refactor (prompts for instruction)]),
  ([#key("g")#key("a")#key("c")], [Open AI chat]),
  ([#key("g")#key("a")#key("s")], [Show AI status]),
  ([#key("g")#key("a")#key("t")], [Test AI connection]),
  ([#key("g")#key("a")#key("m")], [List available models]),
  ([#key("g")#key("a")#key("x")], [Cancel AI request]),
  ([#key("g")#key("a")#key("?")], [Show AI help]),
)

The `gg` sequence (go to first line) continues to work as expected.

== Insert Mode Completion

While in insert mode, AI-powered code completion is available:

#cmd-table(
  ([#key("Tab")], [Trigger AI completion (if text before cursor)]),
  ([#key("Tab") (ghost visible)], [Accept ghost text completion]),
  ([#key("Escape")], [Dismiss ghost text / exit insert mode]),
)

When a completion arrives, it appears as "ghost text" --- dimmed text
after the cursor showing the suggested insertion. Press #key("Tab") to
accept the ghost text, or #key("Escape") to dismiss it and return to
command mode.

== AI Tools

The AI system includes tool-use support, allowing the model to read
files and buffers during conversations:

- *BufferInfoTool* --- provides current buffer metadata
- *BufferReadTool* --- reads lines from the current buffer
- *BufferWriteTool* --- inserts or replaces text in the buffer
- *FileListTool* --- lists files in the project
- *FileReadTool* --- reads file contents from disk
- *GrepTool* --- searches files by pattern

Tools are invoked automatically by the AI model during chat interactions
when it needs additional context to answer your question.

== Chat Buffer

AI responses appear in a dedicated `*ai-chat*` buffer.
The buffer is created on first use and reused across interactions.
Conversation history persists across #cmd("ai chat") calls until #cmd("ai clear") is invoked.

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
:ai status                        " see request history
:ai clear                         " reset conversation
```

#warning-box[
  *Note:* The #cmd("ai review") command may take longer for large files
  due to the volume of context sent to the server. The editor remains
  usable during the request.
]

// ============================================================================
// 7. GIT INTEGRATION
// ============================================================================

= Git Integration

Javi provides Magit-inspired Git integration through colon commands.
All commands require Git to be installed and the editor to be running
inside a Git repository.

A convenient shorthand is available: #cmd("git _subcmd_") expands to
#cmd("git\__subcmd_"). For example, #cmd("git status") is equivalent to
#cmd("git_status"), and #cmd("git log") to #cmd("git_log").

== Status and Staging

#cmd-table(
  ([#cmd("git_status")], [Show staged, unstaged, and untracked files]),
  ([#cmd("git_stage _file_")], [Stage a file (`git add`)]),
  ([#cmd("git_unstage _file_")], [Unstage a file (`git restore --staged`)]),
  ([#cmd("git_stage_line")], [Stage the file on the cursor line (in status buffer)]),
  ([#cmd("git_unstage_line")], [Unstage the file on the cursor line]),
  ([#cmd("git_toggle")], [Toggle staged/unstaged for cursor line]),
  ([#cmd("git_discard")], [Discard unstaged changes for cursor line]),
  ([#cmd("git_refresh")], [Refresh the status buffer]),
)

== Committing

#cmd-table(
  ([#cmd("git_commit")], [Open commit message editor showing staged changes]),
  ([#cmd("git_do_commit")], [Finalize commit with message from the commit buffer]),
  ([#cmd("git_amend")], [Amend the most recent commit]),
)

== Viewing

#cmd-table(
  ([#cmd("git_diff")], [Show `git diff` output in a buffer]),
  ([#cmd("git_diff _file_")], [Show diff for a specific file]),
  ([#cmd("git_log")], [Show last 30 log entries (oneline, graph)]),
  ([#cmd("git_branch")], [Show all branches with latest commit]),
  ([#cmd("git_show")], [Show commit details]),
  ([#cmd("git_blame")], [Show `git blame` for the current file]),
)

== Branch Operations

#cmd-table(
  ([#cmd("git_branch_create _name_")], [Create a new branch]),
  ([#cmd("git_branch_switch _name_")], [Switch to another branch]),
  ([#cmd("git_branch_delete _name_")], [Delete a merged branch]),
  ([#cmd("git_merge _branch_")], [Merge a branch into current]),
  ([#cmd("git_rebase _branch_")], [Rebase current branch onto branch]),
  ([#cmd("git_rebase --continue")], [Continue rebase after resolving conflicts]),
  ([#cmd("git_rebase --abort")], [Abort an in-progress rebase]),
)

== Remote Operations

#cmd-table(
  ([#cmd("git_fetch")], [Fetch from remote]),
  ([#cmd("git_pull")], [Pull from remote (fetch + merge)]),
  ([#cmd("git_push")], [Push to remote]),
)

== Stash

#cmd-table(
  ([#cmd("git_stash")], [Stash working directory changes]),
  ([#cmd("git_stash_pop")], [Pop the top stash entry]),
  ([#cmd("git_stash_list")], [Show stash list in a buffer]),
)

== Hunk-Level Operations

For fine-grained staging, the following commands work in diff and patch
buffers:

#cmd-table(
  ([#cmd("git_stage_hunk")], [Stage the hunk at the cursor]),
  ([#cmd("git_unstage_hunk")], [Unstage the hunk at the cursor]),
  ([#cmd("git_revert_hunk")], [Discard the unstaged hunk at the cursor]),
  ([#cmd("git_patch")], [Open patch view for the file at cursor]),
  ([#cmd("git_goto_file")], [Jump to the source file from a diff or status line]),
)

=== Diff Buffer Key Bindings

When viewing a diff or patch buffer, overlay key bindings provide quick
access to hunk operations:

#cmd-table(
  ([#key("s")], [Stage hunk at cursor]),
  ([#key("u")], [Unstage hunk at cursor]),
  ([#key("X")], [Revert (discard) unstaged hunk at cursor]),
  ([#key("q")], [Quit diff view]),
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

#screenshot-placeholder[Git status buffer showing staged, unstaged, and untracked files]

The status buffer auto-refreshes when files are saved in the editor.

== Log Buffer

The #cmd("git_log") buffer displays commit history with a graph.
Within the log buffer, these commands provide interactive navigation:

#cmd-table(
  ([#cmd("git_log_diff")], [Toggle inline diff for commit at cursor]),
  ([#cmd("git_expand")], [Expand the commit at cursor (show diff)]),
  ([#cmd("git_expand_all")], [Expand all commits]),
  ([#cmd("git_collapse_all")], [Collapse all expanded commits]),
)

== Workflow Example

+ Run #cmd("git_status") to see the current repository state.
+ Stage files with #cmd("git_stage src/main/java/javi/Example.java").
+ Or use #cmd("git_stage_line") on a file in the status buffer.
+ Run #cmd("git_commit") to open the commit message editor.
+ Edit the commit message, then run #cmd("git_do_commit") to finalize.
+ View the log with #cmd("git_log") to verify your commit.
+ Push with #cmd("git_push") when ready.

#note-box[
  *Note:* If the current directory is not inside a Git repository, all
  git commands report "Not a git repository".
]

// ============================================================================
// 8. LSP INTEGRATION
// ============================================================================

= LSP Integration (Language Server Protocol)

Javi includes a Language Server Protocol client for modern IDE features:
go-to-definition, find references, hover information, diagnostics, and
code completion. LSP support works with any standard language server.

== Setup

The LSP plugin is loaded via `.javini`:

```
loadclass javi.lsp.LspCommands
```

Once loaded, LSP commands and keybindings are immediately available.
Javi starts the appropriate language server automatically when you
open a file whose extension matches a configured server.

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
  ([#cmd("lspconfig")], [Show or set language server configuration]),
)

== Default Key Bindings

#cmd-table(
  ([#key("F12")], [Go to definition (#cmd("lspdef"))]),
  ([#key("Shift-F12")], [Find references (#cmd("lspref"))]),
  ([#key("Ctrl-K")], [Hover information (#cmd("lsphover"))]),
  ([#key("F9")], [Code completion (#cmd("lspcomp"))]),
  ([#key("Ctrl-]")], [Go to definition (LSP first, ctags fallback)]),
)

== Integration with `:ta` and `Ctrl-]`

When the LSP plugin is loaded, it registers as a `TagLookupProvider`.
This means #cmd("ta") and #key("Ctrl-]") automatically route through
LSP first:

+ If an LSP server is running for the current file type, the request
  goes to LSP (`textDocument/definition`).
+ If LSP finds a definition, navigation happens immediately.
+ If LSP has no result (or no server is running), the lookup falls
  through to the standard ctags system.

This integration is transparent --- you use the same keys and commands
you always have, and get LSP precision when available.

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

== Spell Checker (Harper)

Javi includes integrated spell checking via the Harper language server.
The spell checker runs as an overlay LSP instance alongside any primary
language server, providing spelling diagnostics without interfering with
code intelligence.

=== Commands

#cmd-table(
  ([#cmd("lsp.spell")], [Run spell check on current file (creates position list)]),
  ([#cmd("lsp.spell on")], [Enable persistent spell checking]),
  ([#cmd("lsp.spell off")], [Disable spell checking]),
  ([#cmd("lsp.spell status")], [Show spell checker status]),
  ([#cmd("lsp.spell restart")], [Restart the spell checker]),
)

=== Workflow

Running #cmd("lsp.spell") performs a one-shot check: it starts Harper
(if not already running), collects diagnostics, populates a position
list with spelling errors, and navigates to the first entry. Use
#key("F1") to cycle through subsequent spelling issues.

With #cmd("lsp.spell on"), Harper runs continuously and reports
diagnostics as you type. Use #cmd("lspdiag") to view all current
issues.

=== Installation

Harper must be installed on your system:

```bash
# macOS (Homebrew)
brew install harper

# or via cargo
cargo install harper-ls
```

// ============================================================================
// 9. TYPING PRACTICE
// ============================================================================

= Typing Practice
<sec-typing>

Javi includes a typing practice plugin with adaptive difficulty and
spaced repetition. It tracks per-key accuracy and speed, progressively
unlocking new letters as you demonstrate mastery, and focuses lessons
on your weakest keys.

== Setup

Load the typing tutor plugin via `.javini`:

```
loadclass javi.typingtutor.TypingTutorPlugin
```

Or load it interactively with #cmd("loadclass javi.typingtutor.TypingTutorPlugin").

== Commands

#cmd-table(
  ([#cmd("typingpractice")], [Start a typing lesson (default: adaptive mode)]),
  ([#cmd("typingpractice homerow")], [Home row progressive lesson]),
  ([#cmd("typingpractice code")], [Code-focused lesson (punctuation, symbols)]),
  ([#cmd("typingpractice editor")], [Editor command patterns]),
  ([#cmd("typingstats")], [Show typing performance statistics]),
  ([#cmd("typingcheck")], [Check current lesson progress]),
  ([#cmd("typingprogress")], [Show progressive letter unlock status]),
  ([#cmd("typingtarget _n_")], [Set target speed in CPM (e.g., 200)]),
  ([#cmd("typinglines _n_")], [Set lesson line count]),
  ([#cmd("typingreset")], [Reset all typing statistics]),
)

== Lesson Modes

- *Adaptive* (default) --- Generates lessons focused on your weakest
  keys using spaced repetition. Difficulty increases as you improve.
- *Home row* --- Progressive lessons starting with `f` and `j`, unlocking
  new letters as you demonstrate accuracy and speed on existing ones.
- *Code* --- Lessons emphasizing punctuation and symbols common in
  programming (braces, semicolons, operators).
- *Editor* --- Patterns that mirror common editor command sequences.

== How It Works

+ Run #cmd("typingpractice") to start a lesson.
+ A practice buffer appears with target text lines.
+ Type below each target line. Press Enter to advance to the next line.
+ After the last line, results are displayed: CPM, accuracy, errors.
+ The plugin records per-key timing and accuracy, updating the spaced
  repetition model for future lessons.

== Progressive Unlock (Home Row)

In home row mode, you start with just two keys (`f` and `j`). As you
demonstrate proficiency (accuracy ≥ 90% and speed near your target),
new keys unlock in the standard touch-typing order. Use
#cmd("typingprogress") to see which keys are unlocked and which is
currently focused.

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
  [#key("z")], [Z-position scroll (`zt`, `zz`, `zb`) and fold commands (`zo`, `zc`, `za`, `zR`, `zM`)],
  [#key("Z")], [Z-process (ZZ = save and quit, ZQ = quit)],
  [#key("Ctrl-L")], [Redraw screen],
  [#key("Ctrl-G")], [Show file status information],
  [#key(":")], [Enter ex command line],
  [#key("Alt-J")], [Evaluate current file as JavaScript],
)

=== Folding

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("zo")], [Open fold at cursor],
  [#key("zc")], [Close fold at cursor],
  [#key("za")], [Toggle fold at cursor],
  [#key("zR")], [Open all folds],
  [#key("zM")], [Close all folds],
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
  [#key("F1")], [Shift], [Toggle context-sensitive help panel],
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
  [#key("Ctrl-]")], [Jump to definition (LSP first, then ctags)],
  [#key("Ctrl-T")], [Pop tag stack (return to previous location)],
  [#key("Ctrl-^")], [Switch to next file in file list],
  [#key("F12")], [Go to definition (LSP)],
  [#key("Shift-F12")], [Find references (LSP)],
  [#key("Ctrl-K")], [Hover information (LSP)],
  [#key("F9")], [Code completion (LSP)],
)

== Insert Mode Keys

While in insert mode, these special keys are active:

#table(
  columns: (auto, 1fr),
  inset: (x: 6pt, y: 4pt),
  stroke: 0.5pt + gray.lighten(70%),
  fill: (_, row) => if row == 0 { gray.lighten(85%) } else { none },
  [*Key*], [*Action*],
  [#key("Escape") / #key("Ctrl-]")], [Complete and exit insert mode (dismisses ghost text)],
  [#key("Tab")], [Trigger AI completion or accept ghost text (falls back to insert tab)],
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
  ([#cmd("vt")], [Open or toggle to shell (same as F8)]),
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
  ([#cmd("ta _tag_")], [Jump to tag (LSP first, then ctags)]),
  ([#cmd("gototag")], [Jump to tag under cursor]),
  ([#cmd("poptag")], [Pop tag stack]),
  ([#cmd("cn")], [Go to next position in position list]),
  ([#cmd("cp")], [Go to previous position in position list]),
)

#warning-box[
  *Not yet implemented:* #cmd("tagsauto"), #cmd("tagfiles"), and
  #cmd("tagadd") are planned but not yet available.
]

=== LSP Commands

#cmd-table(
  ([#cmd("lspdef")], [Go to definition]),
  ([#cmd("lspref")], [Find all references]),
  ([#cmd("lsphover")], [Show hover information]),
  ([#cmd("lspcomp")], [Trigger code completion]),
  ([#cmd("lspdiag")], [Show diagnostics]),
  ([#cmd("lspstatus")], [Show server status]),
  ([#cmd("lsprestart")], [Restart LSP server]),
  ([#cmd("lsptoggle")], [Enable/disable LSP]),
  ([#cmd("lspconfig")], [Show/set server configuration]),
)

=== AI Commands

#cmd-table(
  ([#cmd("ai _message_")], [Send chat message]),
  ([#cmd("ai chat")], [Interactive chat prompt]),
  ([#cmd("ai explain")], [Explain current code]),
  ([#cmd("ai review")], [Review code for issues]),
  ([#cmd("ai doc")], [Generate documentation]),
  ([#cmd("ai complete")], [Request inline completion]),
  ([#cmd("ai accept")], [Accept ghost text]),
  ([#cmd("ai dismiss")], [Dismiss ghost text]),
  ([#cmd("ai cancel")], [Cancel in-flight request]),
  ([#cmd("ai refactor _instruction_")], [Refactor with instruction]),
  ([#cmd("ai config")], [Show configuration]),
  ([#cmd("ai clear")], [Clear chat history]),
  ([#cmd("ai test")], [Test connection]),
  ([#cmd("ai auth")], [Copilot device flow auth]),
  ([#cmd("ai models")], [List available models]),
  ([#cmd("ai status")], [Show request tracking]),
  ([#cmd("ai tools")], [List registered tools]),
  ([#cmd("ai help")], [Show AI help]),
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

=== Folding Commands

#cmd-table(
  ([#cmd("fold")], [Detect folds by brace/bracket matching (syntax-based)]),
  ([#cmd("foldindent")], [Detect folds by indentation level]),
  ([#cmd("foldindent _n_")], [Detect folds with _n_-space indent levels]),
  ([#cmd("foldmarker")], [Detect folds by `\{\{\{`/`\}\}\}` markers]),
)

See @sec-folding for details on fold detection methods and normal-mode
fold commands.

=== Help System

#cmd-table(
  ([#cmd("help")], [Show help index (context-sensitive)]),
  ([#cmd("help _topic_")], [Show help for specific topic]),
  ([#cmd("contexthelp")], [Toggle context-sensitive help side panel]),
  ([#cmd("helpscrolldown")], [Scroll help panel down]),
  ([#cmd("helpscrollup")], [Scroll help panel up]),
)

Available topics: `index`, `movement`, `editing`, `search`, `files`,
`ex`, `visual`, `undo`, `window`, `shell`, `diredit`, `filelist`,
`directory`, `keybindings`, `folding`, `git`.

=== Git Integration Commands

#cmd-table(
  ([#cmd("git _subcmd_")], [Shorthand: expands to #cmd("git\__subcmd_")]),
  ([#cmd("git_status")], [Show staged, unstaged, and untracked files]),
  ([#cmd("git_stage _file_")], [Stage a file]),
  ([#cmd("git_unstage _file_")], [Unstage a file]),
  ([#cmd("git_stage_line")], [Stage file on cursor line]),
  ([#cmd("git_unstage_line")], [Unstage file on cursor line]),
  ([#cmd("git_toggle")], [Toggle staged/unstaged for cursor line]),
  ([#cmd("git_discard")], [Discard unstaged changes for cursor line]),
  ([#cmd("git_refresh")], [Refresh status buffer]),
  ([#cmd("git_commit")], [Open commit message editor]),
  ([#cmd("git_do_commit")], [Finalize commit]),
  ([#cmd("git_amend")], [Amend most recent commit]),
  ([#cmd("git_diff")], [Show diff]),
  ([#cmd("git_log")], [Show log entries]),
  ([#cmd("git_branch")], [Show branches]),
  ([#cmd("git_show")], [Show commit details]),
  ([#cmd("git_blame")], [Show blame for current file]),
  ([#cmd("git_branch_create")], [Create branch]),
  ([#cmd("git_branch_switch")], [Switch branch]),
  ([#cmd("git_branch_delete")], [Delete branch]),
  ([#cmd("git_merge")], [Merge branch]),
  ([#cmd("git_rebase")], [Rebase current branch]),
  ([#cmd("git_fetch")], [Fetch from remote]),
  ([#cmd("git_pull")], [Pull from remote]),
  ([#cmd("git_push")], [Push to remote]),
  ([#cmd("git_stash")], [Stash changes]),
  ([#cmd("git_stash_pop")], [Pop stash]),
  ([#cmd("git_stash_list")], [List stashes]),
  ([#cmd("git_stage_hunk")], [Stage hunk at cursor]),
  ([#cmd("git_unstage_hunk")], [Unstage hunk at cursor]),
  ([#cmd("git_patch")], [Open patch view]),
  ([#cmd("git_goto_file")], [Jump to file from diff/status]),
)

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
  ([#cmd("loadclass _classname_")], [Load a plugin class by name]),
  ([#cmd("persistfile _name_")], [Set persistent file name]),
  ([#cmd("tabfix")], [Fix tab/space indentation]),
  ([#cmd("fullscreen")], [Toggle fullscreen mode]),
)

// ============================================================================
// APPENDIX B: .JAVINI CONFIGURATION REFERENCE
// ============================================================================

= Appendix B: `.javini` Configuration Reference
<sec-javini>

The `.javini` file is Javi's startup configuration file. It is read from
the current directory when Javi launches. Each line in the file is
executed as if it were typed at the colon command prompt, so any valid
colon command can appear in `.javini`.

== File Location

Javi looks for `.javini` in the working directory where it was launched.
There is no global (home directory) configuration file --- each project
can have its own `.javini` with project-specific settings.

== Format

One command per line. Blank lines and lines that cause errors are
silently skipped. Comments are not supported --- every non-empty line
is interpreted as a command.

```
fontname Verdana
fontsize 15.5
fontweight 1.0
lines 70
tabstop 3
loadplugin javi-hello.jar
loadclass javi.git.GitCommands
loadmapkeys
```

== Common Settings

#config-table(
  ([#lit("fontname _name_")], [Set the editor font face (e.g., `Verdana`, `Courier New`, `JetBrains Mono`)]),
  ([#lit("fontsize _n_")], [Set font size in points (float, e.g., `15.5`)]),
  ([#lit("fontweight _n_")], [Set font weight (float: `1.0` = normal, `2.0` = bold)]),
  ([#lit("monofontname _name_")], [Set the monospace font for terminals]),
  ([#lit("lines _n_")], [Set default window height in lines]),
  ([#lit("setwidth _n_")], [Set default window width in columns]),
  ([#lit("tabstop _n_")], [Set tab display width (default: 8)]),
)

== Plugin Loading

#config-table(
  ([#lit("loadplugin _file.jar_")], [Load a plugin JAR file from the current directory or classpath]),
  ([#lit("loadclass _classname_")], [Load a command class by fully qualified name (e.g., `javi.git.GitCommands`)]),
  ([#lit("loadgroup _name_")], [Load a named command group]),
)

Plugins are loaded during startup after the main editor initializes.
A plugin JAR must contain classes that register commands via the Javi
command framework.

== Key Binding Customization

#config-table(
  ([#lit("mapkey _group_ _key_ _command_")], [Bind a key in the named group]),
  ([#lit("unmapkey _group_ _key_")], [Remove a key binding]),
  ([#lit("loadmapkeys")], [Load saved key bindings from `~/.javi/keybindings`]),
)

Add `loadmapkeys` to your `.javini` to restore custom bindings
automatically on startup.

== Editor Options via `set`

The `set` command can also appear in `.javini` for any registered
option:

```
set ai.provider=copilot
set ai.model=gpt-4
```

See the AI Integration section for available `ai.*` options.

== Example `.javini`

A typical development configuration:

```
fontname JetBrains Mono
fontsize 14.0
fontweight 1.0
lines 60
setwidth 120
tabstop 4
loadclass javi.lsp.LspCommands
loadclass javi.ai.AICommands
loadclass javi.git.GitCommands
loadmapkeys
```

#note-box[
  *Tip:* Keep your `.javini` minimal. Use it for font/size preferences
  and essential plugin loading. Per-session settings are better managed
  interactively with colon commands.
]

// ============================================================================
// APPENDIX C: ACCURACY NOTES
// ============================================================================

= Appendix C: Accuracy Notes

The following features described in this manual are planned but *not yet
implemented* in the codebase as of May 2026. The corresponding
sections describe the intended design:

- *Tag management commands* --- The commands `:tagsauto` (auto-regenerate
  ctags), `:tagfiles` (list tag files), `:tagadd` (add tag file), and
  `:gid` (find references via mkid) are planned but not yet registered
  as commands. Basic tag lookup (`:ta`, `Ctrl-]`, `Ctrl-T`) and
  automatic mkid cross-referencing work.

- *`:shell` alias* --- The `:shell` command documented in help text is
  not registered as a colon command. Use `:vt` or press F8 to access
  the terminal. All other shell management commands (`:shellnew`,
  `:shells`, `:shellnext`, etc.) work as documented.

The following features are *fully implemented* and documented accurately:

- *AI Integration* (Section 6) --- All `:ai` commands are implemented
  on `feature/F8-ai-integration`: chat, explain, review, doc, complete,
  accept, dismiss, cancel, refactor, auth, models, status, tools, help.
  Normal-mode `ga` prefix bindings and insert-mode Tab completion with
  ghost text are functional. Multi-provider support (Copilot, OpenAI,
  Anthropic) with tool-use (BufferRead, FileRead, Grep, etc.).
  Loaded via `loadclass javi.ai.AICommands` or the AI plugin JAR.

- *LSP Integration* (Section 8) --- All LSP commands are implemented
  on `feature/F7-lsp-integration`: lspdef, lspref, lsphover, lspcomp,
  lspdiag, lspstatus, lsprestart, lsptoggle, lspconfig. Key bindings
  (F12, Shift-F12, Ctrl-K, F9) are registered by the plugin.
  TagLookupProvider integration routes `:ta` and `Ctrl-]` through LSP
  before ctags. Loaded via `loadclass javi.lsp.LspCommands`.

- *Context-Sensitive Help* (Section 1) --- The `Shift-F1` help side
  panel, `:contexthelp`, and help panel scrolling commands are
  implemented. The panel dynamically reflects the active keymap.

- *Folding* (Section 4) --- All fold detection methods (`:fold`,
  `:foldindent`, `:foldmarker`) and fold manipulation keys
  (`zo`/`zc`/`za`/`zR`/`zM`) are implemented on master.

- *Git Integration* (Section 7) --- All git commands documented in this
  manual are implemented, including advanced commands:
  `git_stage_hunk`, `git_unstage_hunk`, `git_patch`, `git_blame`,
  `git_goto_file`, `git_amend`, `git_log_diff`, `git_expand`,
  `git_expand_all`, and `git_collapse_all`.

All other sections accurately reflect the current codebase.
