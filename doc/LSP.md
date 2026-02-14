# LSP Support in Javi

Language Server Protocol integration for go-to-definition, references, completion, hover, and diagnostics.

## Quick Start

Add to your `~/.javini`:

```
loadclass javi.lsp.LspCommands
```

This registers LSP commands and hooks into `:ta` (tag lookup).
Servers start automatically when you open a file with a matching extension.

## Supported Servers

| Language | Server | Install |
|----------|--------|---------|
| C/C++ | clangd | `brew install llvm` or use `/usr/bin/clangd` |
| Java | jdtls | `brew install jdtls` |
| Python | pyright | `pip install pyright` |
| TypeScript/JS | typescript-language-server | `npm i -g typescript-language-server` |
| Rust | rust-analyzer | `rustup component add rust-analyzer` |

## Commands

| Command | Description |
|---------|-------------|
| `:ta <symbol>` | Tag lookup — tries LSP definition first, then ctags |
| `:lsp.def` | Go to definition at cursor |
| `:lsp.ref` | Find all references at cursor |
| `:lsp.hover` | Show type/doc info at cursor |
| `:lsp.comp` | Show completions at cursor |
| `:lsp.diag` | Show diagnostics for current file |
| `:lsp.status` | Show running servers |
| `:lsp.restart` | Restart server for current file type |
| `:lsp.toggle` | Enable/disable LSP globally |
| `:lsp.config` | Show server configuration |
| `:lsp.config java=/path/to/jdtls` | Set server command |

## How It Works

1. On `loadclass javi.lsp.LspCommands`, the plugin registers itself as a `TagLookupProvider`.
2. When you open a file, `LspManager` auto-starts the matching language server.
3. `:ta` tries LSP definition first; if LSP has no result, falls through to ctags.
4. Hover information from LSP enriches definition descriptions shown in the tag stack.

## Custom Server Configuration

Create `~/.javi/lsp.conf`:

```
# Format: languageId = command [args...]
# Prefix with ! to disable
java = /opt/custom/jdtls/bin/jdtls
python = pylsp
!rust = rust-analyzer
```

## Troubleshooting

- **"class not found: javi.lsp.LspCommands"** — rebuild with `make build`; the jar predates the LSP classes.
- **Commands unknown** — `loadclass javi.lsp.LspCommands` missing from `.javini`.
- **No results from `:ta`** — check `:lspstatus`; server may not be running. Verify the binary is on PATH.
- **Server won't start** — use `:lspconfig` to check availability; ensure the binary is executable.
