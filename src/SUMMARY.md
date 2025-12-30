# Source Directory Summary

This directory contains the main source code for **Javi** - a vi-like text editor written in Java.

## Directory Structure

### src/main/java/javi/
The core editor package containing:

#### Core Editor Components
- **Javi.java** - Main entry point, handles command line arguments and initialization
- **TextEdit.java** - Main text editing class extending EditContainer, handles text operations
- **EditContainer.java** - Core storage class for editor with undo capability and position tracking
- **EditCache.java** - ArrayList-based cache for edit elements
- **EditGroup.java** - Command group for edit operations (insert, delete, yank, put)
- **View.java** - Abstract base class for views with cursor control and change optimization
- **FvContext.java** - File-View Context, manages the relationship between files and views
- **Position.java** - Immutable position class with file, x, y coordinates
- **MovePos.java** - Mutable position class for cursor movement

#### I/O and File Handling
- **FileDescriptor.java** - File descriptor abstraction with LocalFile and InternalFd variants
- **FileInput.java** - File input converter for reading files
- **FileList.java** - File list management
- **FileProperties.java** - File properties including read-only status
- **IoConverter.java** - Abstract I/O converter with threaded background reading
- **StringIoc.java** - String-based I/O converter
- **PositionIoc.java** - Position list I/O converter

#### Undo/History System
- **UndoHistory.java** - Undo history management with checkpoints and marks
- **BackupStatus.java** - Backup status tracking

#### Command System
- **Command.java** - Main command processor, reads .javini file
- **Rgroup.java** - Abstract command group base class with KeyBinding system
- **MoveGroup.java** - Movement commands (cursor, word, search)
- **KeyGroup.java** - Key binding management
- **MapEvent.java** - Key mapping for vi-style bindings
- **InsertBuffer.java** - Insert mode handling

#### Event System
- **EventQueue.java** - Custom event queue with DebugLock (ReentrantLock)
- **JeyEvent.java** - Custom key event class
- **CommandEvent.java** - Command event
- **ExitEvent.java** - Exit event handling
- **ScrollEvent.java** - Scroll events

#### UI Components
- **UI.java** - Abstract UI base class
- **StreamInterface.java** - Stream-based (terminal) interface

#### Tools and Utilities
- **MiscCommands.java** - Miscellaneous commands (undo, redo, shell)
- **JS.java** - JavaScript integration using Mozilla Rhino
- **Ctag.java** - CTags integration for code navigation
- **MakeCmd.java** - Make/build command integration
- **JavaCompiler.java** - Java compiler integration
- **CheckStyle.java** - CheckStyle integration
- **ClangFormat.java** - Clang format integration
- **DeTabber.java** - Tab/space conversion
- **DirList.java** - Directory listing

#### Terminal Emulation
- **Vt100.java** - VT100 terminal emulator
- **Vt100Parser.java** - VT100 escape sequence parser
- **VScreen.java** - Virtual screen abstraction

#### Server/Plugin
- **Server.java** - TCP server for remote file editing (port 6001)
- **Plugin.java** - Plugin loader using custom ClassLoader

### src/main/java/javi/awt/
AWT-based GUI implementation:

- **AwtInterface.java** - Main AWT interface extending UI (~1640 lines)
- **AwtView.java** - Abstract AWT view base class
- **AtView.java** - Text view implementation
- **OldView.java** - Legacy view implementation
- **StatusBar.java** - Status bar component
- **AwtFontList.java** - Font list management
- **FontEntry.java** - Font entry class
- **AwtCircBuffer.java** - Circular buffer for clipboard integration
- **InHandler.java** - Input handler

### src/main/java/javi/plugin/
Plugin system:
- **FindBugs.java** - FindBugs integration plugin
- **makefile** - Plugin build file

### src/history/java/history/
History/persistence package:

- **PersistantStack.java** - Disk-backed stack for undo history (~721 lines)
- **Tools.java** - Utility methods (trace, execute commands)
- **ByteInput.java** - Byte input wrapper
- **IntArray.java** - Integer array utilities
- **BadBackupFile.java** - Exception for bad backup files
- **FileLockException.java** - Exception for file locking
- **PSTest.java** - PersistantStack tests
- **Testutil.java** - Test utilities

## Key Architectural Patterns

1. **Command Pattern** - Rgroup classes implement command groups
2. **Observer Pattern** - FileStatusListener, FileChangeListener
3. **Iterator Pattern** - Iteratable EditContainer
4. **Singleton Pattern** - UI instance, root TextEdit
5. **Template Method** - IoConverter abstract methods
6. **Factory Pattern** - FileDescriptor.make()

## Threading Model

- Main thread holds EventQueue.biglock2 (ReentrantLock)
- Background I/O threads for file reading (IoConverter)
- Server thread for remote connections
- AWT event dispatch thread for UI

## File Format

- `.dmp2` files - Binary backup/persistence files
- `.javini` - Configuration file (vi-style commands)

## Dependencies

The project uses:
- Mozilla Rhino (JavaScript engine)
- juniversalchardet (Character encoding detection)
- RXTX (Serial port communication for VT100)
- JUnit 3.x (Testing)
