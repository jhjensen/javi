# F17-F21: Keybinding Architecture Redesign — Plan

## Branch
`feature/F17-F21-keybinding-redesign`

## Commits
- `6523941` — Enum refactor (all Rgroup subclasses)
- `9d04e3f` — KeyMap with parent-chain, named registry, overlay factory
- `0ef7a43` — Named keygroups, runtime bind/unbind, :mapkey/:unmapkey (rebased)
- `45ee2a0` — Per-buffer keymap wiring, filelist/shell overlays, :keymap command

## Architecture

### Keymap Layering
```
buffer-specific keymap  (e.g. "filelist", "shell")
       ↓ parent
mode-based keymap       (e.g. "normal")
```

### Dispatch Chain (enhanced)
```
1. MapEvent.hevent(JeyEvent, FvContext)
2. MapEvent.domovement() → MapEvent.getActiveKeyMap(fvc)
3. getActiveKeyMap: fvc.getKeyMap() → auto-detect → normalKeyMap
4. Auto-detect: KeyMap.resolveForBuffer(fvc.edvec)
   - FileList → "filelist" overlay
   - Vt100   → "shell" overlay
   - other   → null (use normalKeyMap)
5. lookupMove(key) with parent-chain fallback
6. lookupEdit(key) with parent-chain fallback
```

### Key Classes
| Class | Role |
|-------|------|
| KeyMap | Named, layered keymap with parent-chain fallback |
| KeyGroup | HashMap-based key→binding storage |
| MapEvent | Dispatch loop, bindCommands(), getActiveKeyMap() |
| FvContext | Per-buffer context, holds keyMap overlay |
| MiscCommands | :mapkey, :unmapkey, :keymap commands |

## Status

### F17: Redesign Keybinding Architecture — **70%**
- [x] Audit complete (dispatch chain documented)
- [x] KeyMap class with parent-chain, registry, overlay factory
- [x] Per-buffer keymap wiring (auto-detect from buffer type)
- [x] FileList overlay (Enter opens file at cursor)
- [x] Shell overlay (extensibility point)
- [x] :keymap command for debugging
- [ ] DirEdit overlay (depends on DirEdit merge from F5)
- [ ] Verify per-buffer keymap switching in live editor

### F18: Capture Legacy Design Constraints — **90%**
- [x] Dispatch chain documented
- [x] Vi mode implementations catalogued
- [x] Safe migration rules documented
- [ ] Document InsertBuffer inline-key-read implications

### F19: Prototype Modern Binding Model — **50%**
- [x] KeyMap class implemented
- [x] KeyGroup enhanced with bind/unbind/getCommandName
- [x] Named keygroup support
- [ ] Full replacement of direct KeyGroup usage with KeyMap API
- [ ] Migrate MapEvent.bindCommands() to use KeyMap directly

### F20: Context-Aware Binding Features — **15%**
- [x] :keymap command shows active keymap chain
- [ ] Context-sensitive :help (show what key does in current buffer)
- [ ] Buffer-type-specific :help redirect
- [ ] Tab-completion for command names in :map

### F21: Incremental Keybinding Migration — **50%**
- [x] Step 1: KeyMap introduced (non-breaking)
- [x] Step 2: :mapkey/:unmapkey commands
- [x] Step 3 (partial): Per-buffer keymap stacks via FvContext
- [ ] Step 3: Persistence (~/.javi/keybindings)
- [ ] Step 4: Context-aware dispatch verification
- [ ] Step 5: Full migration (all bindings through KeyMap API)

## Remaining Work (Priority Order)
1. **DirEdit overlay** — after F5 branch merges, create "directory" overlay with Enter=open, S=toggle-search, etc.
2. **Full KeyMap migration** — route all bindCommands() through KeyMap API
3. **Persistence** — save user keybindings to file
4. **Context-sensitive help** — :help that knows buffer type
5. **Live verification** — test keymap switching in the running editor
