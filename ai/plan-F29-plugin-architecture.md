# F29: Plugin Build Architecture Rework

## Goal

Restructure javi's plugin system so that plugins (F4 formatter, F7 LSP, F8 AI/Copilot, F9 Git) are built as separate JARs from per-plugin source directories, loadable via `:loadplugin` at runtime or from `.javini` at startup.

## Current State

### Plugin Loading (Plugin.java)
- `Plugin` interface with nested `Loader` class
- `JarResources` / `MultiClassLoader` / `JarLoader` — custom classloader that loads classes from JAR files
- `Plugin.Loader.load()` loads a JAR, finds `javi.plugin.FindBugs` class (hardcoded), checks it implements `Plugin`, reads its `pluginInfo` field
- Called from `Javi.java` startup: `Plugin.Loader.load("plugin/plugin.jar")`
- Only one sample plugin exists: `src/main/java/javi/plugin/FindBugs.java` (extends `Rgroup`, implements `Plugin`)

### Command Registration (Rgroup)
- `Rgroup.loadgroup(file, className)` — loads a class by name via `Class.forName()`, instantiates it, puts it in `glist` map
- `register(String[])` — registers command names into static `cmhash`
- Plugins that extend `Rgroup` and call `register()` in their constructor automatically register commands

### Build System (build.gradle)
- Single Gradle project builds everything into one JAR
- `jar` task produces `build/libs/javi-dev.jar`
- `dist` task copies to `dist/`
- JAR manifest has `Implementation-Version` from project property or 'dev'
- No `git describe` in manifest

### No `:loadplugin` Command
- No `:loadplugin` command exists in MiscCommands
- Plugin loading is only via hardcoded call in `Javi.java` startup

## Proposed Architecture

### Directory Structure

```
src/main/java/javi/plugin/     # Plugin interface + loader (existing)
plugins/
  git/src/javi/git/            # F9: Git commands (future — source on F9 branch)
  formatter/src/javi/format/   # F4: Java formatter (future — source on F4 branch)
  lsp/src/javi/lsp/            # F7: LSP client (future — source on F7 branch)
  ai/src/javi/ai/              # F8: AI/Copilot (future — source on F8 branch)
```

Each plugin directory contains source files in the `javi.*` namespace. They compile against the main javi classes (as a dependency) but produce a separate JAR.

### JAR Output

- `build/libs/javi-dev.jar` — main editor (unchanged)
- `build/libs/javi-git.jar` — git plugin
- `build/libs/javi-formatter.jar` — formatter plugin
- `build/libs/javi-lsp.jar` — LSP plugin
- `build/libs/javi-ai.jar` — AI plugin

### Build Approach

Gradle task-based (not multi-project) — simpler for this use case:
- One `Jar` task per plugin, compiling against main sourceset
- `make jar` builds main JAR (existing behavior unchanged)
- `make plugins` builds all plugin JARs
- `make dist` copies everything to `dist/`

### `:loadplugin` Command

New command in MiscCommands:
- `:loadplugin <name>` — loads `build/libs/javi-<name>.jar`, or `dist/javi-<name>.jar`
- Also accepts full path: `:loadplugin /path/to/plugin.jar`
- Uses existing `Plugin.Loader` mechanism (updated to accept arbitrary entry class)
- Works from `.javini` at startup via `loadplugin <name>`

### JAR Manifest

Add `git describe --always --dirty` output:
```
Git-Describe: v1.0-3-gabcdef
```

## Implementation Steps

1. [x] Add `git describe` to JAR manifest in build.gradle
2. [x] Add `:loadplugin` command to MiscCommands
3. [x] Update Plugin.Loader to support flexible class discovery
4. [x] Create Gradle task template for building a plugin JAR (proof of concept)
5. [x] Create `plugins/` directory structure with a template plugin
6. [x] Add `make plugins` target to makefile
7. [x] Plugin.bindKey() API for keybinding registration
8. [ ] Plugin.Loader classloader delegation — plugins currently can't reference main javi classes at runtime (isolated classloader). URLClassLoader with parent delegation needed.
9. [ ] Test `:loadplugin hello` end-to-end in running javi instance
10. [ ] Verify `.javini` `loadplugin` works at startup
11. [ ] Move existing plugin sources (F4/F7/F8/F9) to plugins/ directories (future — per-branch)

## Risks

- Plugin JARs need the main javi classes on the classpath — currently Plugin.Loader uses an isolated classloader. May need to use a URLClassLoader that delegates to the parent instead.
- `.javini` command execution happens early in startup — must ensure `:loadplugin` is registered before `.javini` is processed.

## Progress

- 2026-03-28: Initial plan created. Investigation complete.
- 2026-03-29: Implemented plugin build architecture — Gradle tasks (plugins, pluginJar, distAll), makefile targets, Plugin.bindKey() API, HelloPlugin template. Fixed ant.jar manifest issue (Gradle 8.14 compat) and distAll duplicate entry. Compile + junit pass. Commit bae0caf.
