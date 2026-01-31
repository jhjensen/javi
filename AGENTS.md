# Javi AI Agent Configuration

This file provides context for AI assistants working with the Javi project.

## Project Overview

**Javi** is a vi-like text editor written in Java. It's a mature codebase (~20+ years old) that implements vi-style modal editing with a Java AWT GUI.

### Core Purpose
- Vi-style modal text editing
- Multi-file editing with buffer management  
- Persistent undo history
- JavaScript scripting support
- Integration with external tools (ctags, make, checkstyle, clang-format)

### Technology Stack
- **Language**: Java (targeting Java 22, but uses patterns from Java 5-6 era)
- **GUI**: Java AWT (not Swing, not JavaFX)
- **Build System**: Make (preferred), Gradle (underlying)
- **JavaScript Engine**: Mozilla Rhino
- **Testing**: Custom test classes (EditTester1, PSTest)

## Building the Project

**Always use make targets** for build operations. Run `make help` to see all available targets.

### Prerequisites
- JDK 22+ (set in build.gradle)
- Gradle 8.x (wrapper included)
- Perl (for checkstyle script)

### Build Commands

```bash
# Compile Java sources (default target)
make compile

# Build JAR file
make jar

# Build fat JAR with all dependencies  
make fatjar

# Full build (compile + jar)
make build

# Clean build artifacts
make clean
```

### Build Output
- Classes: `build/classes/java/main/`
- JAR: `build/libs/javi-1.0.jar`
- Fat JAR: `build/libs/javi-all.jar`

### Temporary Files

Use `ai/` directory in the project root for temporary files during agent operations.
This directory is gitignored and allows VS Code command approval to work smoothly
(avoids paths outside the project that require extra approval steps).

The `ai/` directory also contains:
- Plan files for todo items (`ai/plan-*.md`)
- Analysis output and notes
- Any working files agents need

```bash
# Example: writing analysis output
./gradlew compileJava 2>&1 | grep "warning:" > ai/warnings.txt
```

## Running Tests

**Always run tests via make** to ensure consistent classpath and execution:

```bash
# Run all tests (recommended after any code change)
make test

# Run specific test suites
make pstest    # PersistantStack tests
make edittest  # EditTester1 (TextEdit tests)
```

Test files are created in `build/test-output/` and cleaned up on success.
If tests fail, files are preserved for debugging.

## Running the Application

```bash
# Run the editor (GUI mode)
make run

# Run with a specific file
make run-file FILE=myfile.txt

# Using the ms script (alternative)
./ms
```

## Code Quality

```bash
# Run checkstyle on all Java files
make cstyle

# Run checkstyle on a specific file
make cstyle-file FILE=src/main/java/javi/Javi.java
```

Checkstyle configuration: `checkstyle.xml`
- Uses 3-space indentation
- Enforces LF line endings
- Standard Java naming conventions

## Project Structure

```
javi/
├── src/
│   ├── main/java/javi/      # Core editor code
│   │   ├── awt/             # AWT GUI implementation
│   │   └── plugin/          # Plugin system
│   └── history/java/history/ # Persistence/undo system
├── javitests/               # Test files
├── oldstuff/                # Legacy/archived code
├── lib/                     # Third-party JARs
├── build.gradle             # Primary build file
├── build.xml                # Ant build file (alternative)
└── makefile                 # Legacy make build
```

## Key Classes to Know

### Entry Points
- `javi.Javi` - Main class, application entry point
- `javi.awt.AwtInterface` - Main GUI class

### Core Editor
- `EditContainer` - Base storage class with undo capability
- `TextEdit` - Main text editing operations
- `FvContext` - File-View context binding
- `View` - Abstract view for display

### Command System
- `Rgroup` - Command group base class
- `EditGroup` - Edit commands (insert, delete)
- `MoveGroup` - Movement commands
- `MapEvent` - Key bindings

### History/Persistence
- `UndoHistory` - Undo/redo management
- `PersistantStack` - Disk-backed undo stack

## Terminology

- **Buffer/File/EditContainer**: What Emacs calls a "buffer" is referred to as a "file" or `EditContainer` in this codebase. `EditContainer` is the base class that manages text content with undo capability.
- **View**: The display/window showing a buffer's content
- **FvContext**: File-View Context - binds an EditContainer to a View

## Configuration Files

- `.javini` - Configuration file (vi-style commands)
- `.dmp2` files - Binary backup/persistence files (locked while Javi is running)

### .dmp2 Lock Files

The `.dmp2` files use Java's `FileLock` (OS-level advisory lock) to prevent concurrent access. If Javi exits abnormally:
- On Unix/macOS: The OS automatically releases the lock when the process terminates
- If the lock persists: Another Javi instance may still be running - check with `ps aux | grep -i javi`
- To force unlock: Kill the Javi process, or delete the .dmp2 file (loses undo history)

## Common Development Tasks

### Adding a New Command

1. Create or modify a class extending `Rgroup`
2. Add command name to `rnames` array
3. Implement logic in `doroutine()` method
4. Register keybinding in `MapEvent.bindCommands()` if needed

### Modifying UI

- GUI code is in `src/main/java/javi/awt/`
- `AwtInterface.java` is the main UI controller
- `OldView.java` and `AtView.java` handle text rendering

### Working with Files

- File operations go through `FileDescriptor` and subclasses
- `FileInput` handles reading files
- `IoConverter` manages background I/O

## Code Style Notes

- Uses `null != x` style for null checks (Yoda conditions)
- Heavy use of inner classes
- Static imports from `history.Tools.trace` for logging
- Minimal use of annotations
- Many single-letter variable names

## Known Issues

See [BUGS.md](BUGS.md) for detailed bug list.

Key issues:
- Resource leaks (needs try-with-resources)
- Deprecated API usage (finalize, AccessController)
- Thread safety concerns
- Limited test coverage

## Modernization Opportunities

See [IMPROVEMENTS.md](IMPROVEMENTS.md) for detailed improvement list.

Priority areas:
1. Try-with-resources for all I/O
2. Diamond operator for generics
3. Records for data classes
4. Switch expressions
5. Pattern matching for instanceof

## Tips for AI Assistants

1. **Always use make targets**: Use `make compile`, `make test`, etc. instead of running gradle or java directly. This ensures consistency with manual workflows.

2. **Verify changes with tests**: After any code change, run `make test` to verify nothing is broken.

3. **When modifying code**: Preserve the existing code style (null != x pattern, inner classes, etc.) unless specifically asked to modernize.

4. **When adding features**: Follow the existing `Rgroup` command pattern for new commands.

5. **When debugging**: Use `Tools.trace()` for logging (it includes file:line info).

6. **Thread safety**: Be aware that `EventQueue.biglock2` is the main synchronization lock.

7. **File encoding**: Use "UTF-8" explicitly for all file I/O.

8. **Code quality**: Run `make cstyle` to check for style issues before committing.

9. **Dependencies**: Check `lib/` directory and `build.gradle` for available libraries.

## Directory Summaries

Detailed summaries available in:
- [src/SUMMARY.md](src/SUMMARY.md)
- [oldstuff/SUMMARY.md](oldstuff/SUMMARY.md)
- [lib/SUMMARY.md](lib/SUMMARY.md)

## API Documentation

**Javadoc** is available at `build/docs/javadoc/index.html`. Generate with `make javadoc`.

Key documented classes:
- `EditContainer` - Core storage with undo and lazy loading
- `TextEdit` - Text manipulation operations
- `FvContext` - File-View context binding
- `View` - Abstract display interface
- `EventQueue` - Event dispatch and synchronization (biglock2)
- `Rgroup` - Command system base class
- `PersistantStack` - Disk-backed undo persistence
- `AwtInterface` - Main GUI implementation

## Task Planning

Todo items and their implementation plans are in:
- [todo.md](todo.md) - Consolidated task list with priorities
- `ai/plan-*.md` - Detailed implementation plans for each task

## Contact/History

This is a personal project with 20+ years of history. The codebase reflects Java patterns from the Java 1.4-6 era while being updated to compile on modern Java.
