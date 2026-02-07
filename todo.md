# Javi TODO List

This document consolidates items from todo.txt, BUGS.md, and IMPROVEMENTS.md into actionable tasks for AI-assisted development.

**Plan Files**: Detailed implementation plans are in [ai/](ai/) directory.

---

## Table of Contents
1. [Feature Requests](#feature-requests)
2. [Bug Fixes](#bug-fixes)
3. [Code Modernization](#code-modernization)
4. [Build & Infrastructure](#build--infrastructure)
5. [Documentation & Testing](#documentation--testing)
6. [Unclear Items](#unclear-items)

---

## Feature Requests


### F3. Display Last Line of Output on Status Bar After `mk` Command
**Priority**: Medium  
**Difficulty**: Medium  
**Plan**: [ai/plan-F3-mk-output-statusbar.md](ai/plan-F3-mk-output-statusbar.md)

After the `mk` (make) command completes, display the last line of output on the status bar.

**Challenge**: The line in `positionIOC` that displays completion:
```java
UI.reportMessage(this + "complete " + errcount + " results");
```
...has no access to the previous/last output line from the build.

**Approach**: May need to capture and store the last line from the build output stream before reporting completion.

**Files involved**: `IoConverter.java`, `PositionMark.java` or related, `UI.java`

---

### F4. Integrate Java Code Formatter (like clang-format)
**Priority**: Medium  
**Difficulty**: Medium  
**Plan**: [ai/plan-F4-java-formatter.md](ai/plan-F4-java-formatter.md)

Investigate and integrate a Java code formatter similar to how clang-format is used for C/C++.

**Options to consider**:
- Google Java Format (`google-java-format`)
- Eclipse JDT formatter
- Palantir Java Format

**Requirements**:
1. Research available Java formatters
2. Add integration similar to existing `CheckStyle.java`
3. Add command to format current buffer
4. Consider format-on-save option

---

### F5. Directory Editor (diredit) Similar to Vi
**Priority**: Medium  
**Difficulty**: Hard  
**Plan**: [ai/plan-F5-directory-editor.md](ai/plan-F5-directory-editor.md)

Implement a directory editing mode similar to vim's netrw or dired.

**Phase 1 Features**:
- Display directory contents as a navigable list
- Enter subdirectories with Enter key
- Navigate with standard vi movement keys

**Phase 2 Features**:
- Delete files (with confirmation)
- Sort by size, name, date
- Display file sizes including recursive directory sizes (like `ncdu`)
- Create new files

**Files involved**: New file(s), possibly `FileList.java`, `Rgroup` subclass for commands

---

### F6. Documentation and Help System
**Priority**: Medium  
**Difficulty**: Medium  
**Plan**: [ai/plan-F6-help-system.md](ai/plan-F6-help-system.md)

Implement a comprehensive help system.

**Requirements**:
1. Display list of all key bindings
2. Help for each command
3. Accessible via `:help` or F1
4. Include binding list in help system

**Note**: May already have some command listing capability to build on.

---

### F7. Integrate LSP and/or Tree-sitter
**Priority**: High  
**Difficulty**: Hard  
**Plan**: [ai/plan-F7-lsp-integration.md](ai/plan-F7-lsp-integration.md)

Integrate Language Server Protocol (LSP) or tree-sitter for improved code intelligence.

**Primary use case**: Replace/improve over current ctags/mkid functionality.

**Benefits**:
- Better go-to-definition
- Find references
- Symbol search
- Real-time syntax checking
- Code completion

**Implementation options**:
1. LSP client integration (Eclipse LSP4J)
2. Tree-sitter Java bindings
3. Start with just Java language support

---

### F8. Integrate AI/Copilot
**Priority**: High  
**Difficulty**: Hard  
**Plan**: [ai/plan-F8-ai-integration.md](ai/plan-F8-ai-integration.md)

Add AI-assisted coding features.

**Potential features**:
- Code completion suggestions
- Code explanation
- Refactoring suggestions
- Chat interface for code questions

**Implementation approaches**:
- Integrate with local LLM (llama.cpp, etc.)
- OpenAI/Anthropic API integration
- GitHub Copilot API

---

### F9. Integrate Git (Magit-inspired)
**Priority**: Medium  
**Difficulty**: Hard  
**Plan**: [ai/plan-F9-git-integration.md](ai/plan-F9-git-integration.md)

Add Git integration inspired by Emacs Magit.

**Features to consider**:
- Status view showing staged/unstaged changes
- Stage/unstage hunks interactively
- Commit with message editor
- Branch operations
- Log viewer
- Diff viewer with syntax highlighting

---

### F10. Improve VT100 Support
**Priority**: Medium  
**Difficulty**: Medium  
**Plan**: [ai/plan-F10-F11-terminal.md](ai/plan-F10-F11-terminal.md)

Enhance VT100 terminal emulation.

**Requirements**:
1. Auto-detect current charset from terminal environment
2. Support multiple SSH sessions

**Files involved**: `Vt100.java`, `VTinterface.java`

---

### F11. Multiple Shells Support
**Priority**: Medium  
**Difficulty**: Medium  
**Plan**: [ai/plan-F10-F11-terminal.md](ai/plan-F10-F11-terminal.md)

Allow multiple shell sessions to run concurrently.

**Current state**: Appears to support one shell at a time.

**Requirements**:
- Maintain multiple shell buffers
- Switch between shells
- Named shell sessions

---

### F12. Improve Remote Ctag Support
**Priority**: Low  
**Difficulty**: Medium  
**Plan**: [ai/plan-F12-F14-navigation.md](ai/plan-F12-F14-navigation.md)

Enhance ctags functionality for remote files.

**Note**: Need more context on current remote ctag implementation and its limitations.

**Files involved**: `Ctag.java`, remote file handling code

---

### F13. Improve Local/Remote mkid
**Priority**: Low  
**Difficulty**: Medium  
**Plan**: [ai/plan-F12-F14-navigation.md](ai/plan-F12-F14-navigation.md)

Enhance mkid (ID database) functionality.

**Note**: mkid is part of GNU idutils for creating identifier databases.

**Files involved**: Likely related to tag/search commands

---

### F14. Chinese Character Support
**Priority**: Low  
**Difficulty**: Medium  
**Plan**: [ai/plan-F12-F14-navigation.md](ai/plan-F12-F14-navigation.md)

Improve support for Chinese character editing.

**Requirements**:
- Proper display of CJK characters
- Input method support
- Correct character width handling (double-width characters)

**Related**: See task about charset detection

---

### F15. Edit File List
**Priority**: Low  
**Difficulty**: Easy  
**Plan**: [ai/plan-F15-F16-editor-enhancements.md](ai/plan-F15-F16-editor-enhancements.md)

The file list(shown by F2 key) can be edited, but in a rudimentry way.
Doing any thing more complicated than deleting a line has bad effects.
For instance modifying a line doesn't change the file that is being edited, but makes it hard to find the file.
If the file is modified it becomes difficult to save the file.
At the very least the modifying the lines should be prevented.
deleting a line should act similarly to :w.  If the file is modified a popup should ask if the file should be saved.

---

### F16. Revive TabbedTextLayoutView from Oldstuff
**Priority**: Low  
**Difficulty**: Hard  
**Plan**: [ai/plan-F15-F16-editor-enhancements.md](ai/plan-F15-F16-editor-enhancements.md)

This was an attempt to handle tabs and wrapping text.

---

## Bug Fixes

### B1. Fix Deadlock in File Changed Popup
**Priority**: Critical  
**Difficulty**: Medium  
**Related**: F1  
**Plan**: [ai/plan-B1-B3-critical-bugs.md](ai/plan-B1-B3-critical-bugs.md)

There is a deadlock when the "file modified on disk" popup appears but before the user responds.

**Investigation needed**:
- Identify which lock is being held
- Determine if `ta` command is involved
- Check `EventQueue.biglock2` usage

**Files**: `AwtInterface.java`, `EventQueue.java`, `IoConverter.java`

---

### B2. Resource Leaks - Missing Close in Exception Paths
**Priority**: High  
**Difficulty**: Easy  
**Source**: BUGS.md  
**Plan**: [ai/plan-B1-B3-critical-bugs.md](ai/plan-B1-B3-critical-bugs.md)

Multiple files have resources that aren't properly closed if exceptions occur.

**Affected files**:
- `Server.java:26-70` - instream not closed on exception
- `Command.java:73-81` - BufferedReader exception handling
- See IMPROVEMENTS.md Section 1 for full list

**Fix**: Convert to try-with-resources throughout codebase.

---

### B3. Potential Deadlock in IoConverter
**Priority**: High  
**Difficulty**: Hard  
**Source**: BUGS.md  
**Plan**: [ai/plan-B1-B3-critical-bugs.md](ai/plan-B1-B3-critical-bugs.md)

**Location**: `IoConverter.java:143-179`

```java
final synchronized int expandLock(int desired) throws IOException {
    EventQueue.biglock2.unlock();
    wait(2000);
    return 2;
}
```

The method releases `biglock2` while holding `this` monitor, which could cause deadlock if another thread acquires locks in opposite order.

**Fix**: Review locking strategy, ensure consistent lock ordering.

---

### B4. Thread Safety Issues in EditContainer
**Priority**: Medium  
**Difficulty**: Medium  
**Source**: BUGS.md  
**Plan**: [ai/plan-B4-B7-thread-safety.md](ai/plan-B4-B7-thread-safety.md)

`EditContainer.listeners` is accessed without synchronization in some paths while being modified with `synchronized (listeners)` in others.

**Files**: `EditContainer.java`

---

### B5. Swallowed Exceptions
**Priority**: Medium  
**Difficulty**: Easy  
**Source**: BUGS.md  
**Plan**: [ai/plan-B4-B7-thread-safety.md](ai/plan-B4-B7-thread-safety.md)

Many catch blocks catch `Throwable` or `Exception` and only log:

- `AwtCircBuffer.java:86`
- `OldView.java:607, 700, 710, 754`

**Fix**: Add proper exception handling or re-throw as RuntimeException where appropriate.

---

### B6. Unchecked Type Casts
**Priority**: Medium  
**Difficulty**: Medium  
**Source**: BUGS.md  
**Plan**: [ai/plan-B4-B7-thread-safety.md](ai/plan-B4-B7-thread-safety.md)

**Locations**:
- `Buffers.java:37-39, 63-65` - casting to ArrayList<String> without check
- `EditCache.java:86` - generic array creation

**Fix**: Use proper generics, pattern matching for instanceof (Java 16+).

---

### B7. Lock Not Released on All Paths
**Priority**: Medium  
**Difficulty**: Medium  
**Source**: BUGS.md  
**Plan**: [ai/plan-B4-B7-thread-safety.md](ai/plan-B4-B7-thread-safety.md)

**Location**: `EventQueue.java:88-111`

```java
private static Object inextEvent(CursorControl vi) {
    biglock2.unlock();
    // ... complex logic with multiple return paths
    biglock2.lock();
    return ev;
}
```

If exception thrown, lock state may be incorrect.

**Fix**: Use try-finally to ensure lock is always restored to correct state.

---

### B8. Infinite Loop Potential in UI.java
**Priority**: Medium  
**Difficulty**: Medium  
**Source**: BUGS.md  
**Plan**: [ai/plan-B8-B11-misc-bugs.md](ai/plan-B8-B11-misc-bugs.md)

**Location**: `UI.java:93-133`

If `ireportDiff` consistently returns an unhandled button value, the loop runs forever.

**Fix**: Add timeout or fallback handling.

---

### B9. FindBug Command Not Working
**Priority**: Low  
**Difficulty**: Medium  
**Plan**: [ai/plan-B8-B11-misc-bugs.md](ai/plan-B8-B11-misc-bugs.md)  

The findbug command is not working. May need to update to SpotBugs (FindBugs successor).

**Requirements**:
1. Evaluate SpotBugs as replacement
2. Update integration if switching
3. Test that issues are properly reported

---

### B10. Fix Command Line
**Priority**: Low
**Difficulty**: Unknown  
**Plan**: [ai/plan-B8-B11-misc-bugs.md](ai/plan-B8-B11-misc-bugs.md)

Currently when in commandline mode it is possible to delete the initial : or / in the line.
this results in a "delted prompt" feedback.  It would be better to prevent the deletion

---

### B11. Lock Access to .dmp2 Files
**Priority**: Low  
**Difficulty**: Medium  
**Plan**: [ai/plan-B8-B11-misc-bugs.md](ai/plan-B8-B11-misc-bugs.md)  

The `.dmp2` backup/persistence files should have locked access to prevent corruption.

**Files involved**: `PersistantStack.java`, `FileDescriptor.java`

---

## Code Modernization

### M1. Try-With-Resources Throughout Codebase
**Priority**: High  
**Difficulty**: Easy  
**Source**: IMPROVEMENTS.md  
**Plan**: [ai/plan-M1-M5-modernization.md](ai/plan-M1-M5-modernization.md)

Convert manual resource management to try-with-resources.

**Files affected**:
- `Command.java:80`
- `Plugin.java:59,92,96`
- `TextEdit.java:263-270`
- `Server.java:53,94,96`
- `Ctag.java:83,151`
- `MapEvent.java:264`
- `JavaCompiler.java:159`
- `FileProperties.java:75` (oldstuff)
- `UI.java:217`

---

### M2. Use Diamond Operator
**Priority**: Medium  
**Difficulty**: Easy  
**Source**: IMPROVEMENTS.md  
**Plan**: [ai/plan-M1-M5-modernization.md](ai/plan-M1-M5-modernization.md)

Replace redundant type parameters:
```java
// Before
HashMap<String, KeyBinding> cmhash = new HashMap<String, KeyBinding>(200);
// After
HashMap<String, KeyBinding> cmhash = new HashMap<>(200);
```

---

### M3. Replace finalize() with Cleaner
**Priority**: High  
**Difficulty**: Medium  
**Source**: IMPROVEMENTS.md  
**Plan**: [ai/plan-M1-M5-modernization.md](ai/plan-M1-M5-modernization.md)

**Location**: `PersistantStack.java:642`

The `finalize()` method is deprecated and unreliable.

**Fix**: Use `java.lang.ref.Cleaner` (Java 9+).

---

### M4. Replace AccessController
**Priority**: Medium  
**Difficulty**: Medium  
**Source**: IMPROVEMENTS.md  
**Plan**: [ai/plan-M1-M5-modernization.md](ai/plan-M1-M5-modernization.md)

**Location**: `Plugin.java:248`

`AccessController.doPrivileged()` is deprecated.

**Fix**: Evaluate if security manager is needed; if not, remove. If yes, use modern alternative.

---

### M5. Remove System.runFinalization()
**Priority**: Medium  
**Difficulty**: Easy  
**Source**: IMPROVEMENTS.md  
**Plan**: [ai/plan-M1-M5-modernization.md](ai/plan-M1-M5-modernization.md)

**Location**: `Tools.java:142`

This is deprecated and marked for removal.

**Fix**: Remove the call; use explicit cleanup instead.

---

### M6. Switch Expressions
**Priority**: Medium  
**Difficulty**: Medium  
**Source**: IMPROVEMENTS.md  
**Plan**: [ai/plan-M6-M10-modernization.md](ai/plan-M6-M10-modernization.md)

Convert switch statements to switch expressions (Java 14+).

**Files affected**:
- `TextEdit.java:133`
- `EditContainer.java:380`
- `UI.java:92`
- `AwtInterface.java:347`
- `FileList.java:420`
- `OldView.java:577`

---

### M7. Records for Data Classes
**Priority**: Medium  
**Difficulty**: Medium  
**Source**: IMPROVEMENTS.md  
**Plan**: [ai/plan-M6-M10-modernization.md](ai/plan-M6-M10-modernization.md)

Convert data classes to records (Java 16+).

**Candidates**:
- `Position.java`
- `MovePos.java` (if it exists)

---

### M8. Pattern Matching for instanceof
**Priority**: Medium  
**Difficulty**: Easy  
**Source**: IMPROVEMENTS.md  
**Plan**: [ai/plan-M6-M10-modernization.md](ai/plan-M6-M10-modernization.md)

```java
// Before
if (bufo instanceof ArrayList)
    ((ArrayList<String>) bufo).add(buffer);
// After
if (bufo instanceof ArrayList<String> list)
    list.add(buffer);
```

---

### M9. Use StandardCharsets.UTF_8
**Priority**: Medium  
**Difficulty**: Easy  
**Plan**: [ai/plan-M6-M10-modernization.md](ai/plan-M6-M10-modernization.md)  

Replace hardcoded "UTF-8" strings with `StandardCharsets.UTF_8`.

**Files affected**:
- `Vt100.java:30`
- `Server.java:33`
- `TextEdit.java:262`
- `Tools.java:102,107`

---

### M10. Add @Override Annotations
**Priority**: Low  
**Difficulty**: Easy  
**Source**: IMPROVEMENTS.md  
**Plan**: [ai/plan-M6-M10-modernization.md](ai/plan-M6-M10-modernization.md)

Very few `@Override` annotations in codebase. Add them for compile-time verification.

---

### M11. Use Iterable<OType> in EditContainer
**Priority**: Medium  
**Difficulty**: Medium  
**Plan**: [ai/plan-M11-M15-modernization.md](ai/plan-M11-M15-modernization.md)  

Currently uses `Object[]` arrays. Modernize to use `Iterable<OType>`.

**Related**: EditContainer Container/Iterator pattern, EditVec could be Container, IoConverter could be Generator/Iterator.

---

### M12. Eliminate SuppressWarnings Directives
**Priority**: Low  
**Difficulty**: Medium  
**Plan**: [ai/plan-M11-M15-modernization.md](ai/plan-M11-M15-modernization.md)  

Work on removing `@SuppressWarnings` annotations by fixing the underlying issues.

---

### M13. Redo PersistantStack -> RanFileArray
**Priority**: Low  
**Difficulty**: Hard  
**Plan**: [ai/plan-M11-M15-modernization.md](ai/plan-M11-M15-modernization.md)  

PersistantStack is fairly hard to understand.
At the time I thought that using RanFileArray as a a basis for reimplementing would be useful

Analyze and make a recomendation for doing  this.


---

### M14. Refactor AwtInterface into Separate Files
**Priority**: Medium  
**Difficulty**: Medium  
**Source**: BUGS.md mentions it's ~1640 lines  
**Plan**: [ai/plan-M11-M15-modernization.md](ai/plan-M11-M15-modernization.md)

Break up the monolithic `AwtInterface.java` into smaller, focused classes.

---

### M16. Make BadBackupFile Inherit from IOException
**Priority**: Low  
**Difficulty**: Easy  
**Plan**: [ai/plan-M16-M20-modernization.md](ai/plan-M16-M20-modernization.md)  

**Files**: `BadBackupFile.java`

Currently may not be an IOException subclass, which would simplify exception handling.

---

### M17. Move Flush to Close in PersistantStack
**Priority**: Medium  
**Difficulty**: Medium  
**Plan**: [ai/plan-M16-M20-modernization.md](ai/plan-M16-M20-modernization.md)  

In `PersistantStack.push`, move the flush operation to `close()` and do it occasionally rather than on every push.

**Benefit**: Improved performance

---

### M18. Add safeWrite to FileDescriptor
**Priority**: Medium  
**Difficulty**: Medium  
**Plan**: [ai/plan-M16-M20-modernization.md](ai/plan-M16-M20-modernization.md)  

Add safe write functionality that handles edge cases properly.

**Related**: M19 (copy permissions when writing)

---

### M19. Copy File Permissions When Writing
**Priority**: Medium  
**Difficulty**: Easy  
**Plan**: [ai/plan-M16-M20-modernization.md](ai/plan-M16-M20-modernization.md)  

When writing a file, preserve the original file's permissions (read/write/execute bits).

---

### M20. JS.java Charset Detection
**Priority**: Low  
**Difficulty**: Medium  
**Plan**: [ai/plan-M16-M20-modernization.md](ai/plan-M16-M20-modernization.md)  

Add charset detection to JavaScript file handling.

**Related**: Task about StandardCharsets.UTF_8

---

## Build & Infrastructure

### I6. Jython Support
**Status**: Hold  
**Priority**: Low  
**Difficulty**: Unknown  
**Plan**: 

search the web looking for python implementations of java like Jython that support python 3
**Note**: "jython if it ever supports python3" - Jython is a Python implementation for JVM. Currently Jython only supports Python 2.7. This is blocked on Jython project progress.

**Status**: Waiting on external project

---

## Documentation & Testing

### T1. Improve Test Coverage
**Priority**: High  
**Difficulty**: Medium  
**Plan**: [ai/plan-T1-T3-testing.md](ai/plan-T1-T3-testing.md)  

Current test files are limited:
- `javitests/AtViewTest.java`
- `javitests/ViewTest.java`
- `javitests/perftest.java`
- `src/history/java/history/PSTest.java`
- `src/history/java/history/IntArrayTest.java`

**Requirements**:
1. Add unit tests for core functionality
2. Use StreamInterface for testing (non-GUI mode)
3. Create methodology for UI testing
4. Convert test cases to JUnit 5

---

### T2. Convert Tests to JUnit 5
**Priority**: Medium  
**Difficulty**: Medium  
**Source**: IMPROVEMENTS.md  
**Plan**: [ai/plan-T1-T3-testing.md](ai/plan-T1-T3-testing.md)

Migrate from current custom test classes and JUnit 3.x to JUnit 5.

**Changes**:
- Modern annotations (`@Test`, `@BeforeEach`, etc.)
- Better assertions
- Parameterized tests
- Test lifecycle management

---

### T3. Create UI Testing Methodology
**Priority**: Medium  
**Difficulty**: Hard  
**Plan**: [ai/plan-T1-T3-testing.md](ai/plan-T1-T3-testing.md)  

Design and implement approach for testing the AWT GUI.

**Options to consider**:
- AssertJ Swing
- FEST
- Robot-based testing
- Mock-based approach with interfaces

---

---

## Unclear Items

These items from the original todo.txt are unclear and may need more context.  
**Plan**: [ai/plan-U1-U5-unclear.md](ai/plan-U1-U5-unclear.md)

### U1. "edit file list"
Could mean:
- Edit the list of open buffers
- Edit a file listing/browser
- Edit files as a list

### U2. "new view from oldstuff?"
May refer to reviving code from `oldstuff/` directory as a new View implementation.

### U3. "fix cmd line"
Could mean:
- Command-line argument parsing
- Ex-mode command line
- Shell command execution

### U4. "get rid of getnext?"
References a `getnext` method or pattern - need to search codebase to understand what this refers to.

### U5. "Redo PersistantStack->RanFileArray"
Relationship between these classes and the benefit of refactoring is unclear.

---


## Priority Summary

### Critical
- B1 - Deadlock in file changed popup
- B2 - Resource leaks
- B3 - Deadlock in IoConverter

### High
- F1 - File change detection popup enhancements
- F7 - LSP/tree-sitter integration
- F8 - AI/Copilot integration
- M1 - Try-with-resources
- M3 - Replace finalize()
- I2 - Javadoc
- T1 - Test coverage

### Medium
- F3, F4, F5, F6, F9, F10, F11 - Various feature requests
- B4, B5, B6, B7, B8 - Various bug fixes
- M2, M4-M9, M11, M14, M17-M19 - Code modernization
- I3, I4 - Build improvements
- T2, T3 - Testing improvements

### Low
- F2, F12-F16 - Lower priority features
- B9, B10, B11 - Lower priority bugs
- M10, M12, M13, M15, M16, M20 - Lower priority modernization
- I1, I5, I6 - Infrastructure items
