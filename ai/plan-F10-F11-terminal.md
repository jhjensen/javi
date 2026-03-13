# Plan: F10 (Improve VT100 Support) + F11 (Multiple Shells)

## Branch: `feature/F10-F11-terminal`

## F10: Improve VT100 Support — 97% Complete

### Done
- VT100 charset auto-detection (CharsetDetector)
- Enhanced escape sequence parsing (Vt100Parser)
- Alternate screen buffer (ESC[?47h/1047h/1049h)
- Shell PTY detach via `script` (ShellSession.buildCommand)
- F8 passthrough toggle (Vt100.handleKeys)
- Window size + TERM fix (commit `976a829`)
- ZZ shell close bug FIXED (commit `dd1e6a4`)
- Multiple shells: `:vt`, `:shells`, `:shellnext`/`:shellprev`, `:shellclose`, `:shellname`
- Shell-specific `:help` (commit `05619f4`)
- Unicode rendering in shell FIXED (commit `24c3b5d`) — InputStreamReader with UTF-8
- **Resize handler** (commit `63bb6df`): OldView.MyCanvas.setSize() → ShellManager.notifyResize() → ShellSession → Vt100.notifyResize() → stty rows/cols
- **Mouse tracking** — xterm mouse modes 1000/1002/1003/1006 (SGR extended). Parser detects mode enable/disable in private mode sequences. OldView intercepts AWT mouse events and encodes as escape sequences sent to shell. Supports press/release/wheel/drag. Both SGR and legacy X10 encoding.

### Remaining
- SSH session support (dedicated `:ssh host` command, session management)

## F11: Multiple Shells Support — 80% Complete

### Done
- ShellManager singleton with session lifecycle
- ShellSession with ID, name, host, charset
- Commands: `:vt`, `:shells`, `:shellnext`, `:shellprev`, `:shellclose`, `:shellname`
- Buffer cleanup on ZZ close

### Remaining
- Shell-specific environment variable persistence
- Named pipe or socket-based IPC between shells
