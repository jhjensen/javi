# Javi Threading Model

This document describes the thread safety model for the Javi editor.

## Overview

Javi uses a single-lock model based on `EventQueue.biglock2`, a `ReentrantLock` 
(specifically `DebugLock`) that coordinates nearly all editor operations.

## The Big Lock (biglock2)

`EventQueue.biglock2` is the primary synchronization mechanism in Javi.

### Location
- Defined in: `src/main/java/javi/EventQueue.java`
- Type: `EventQueue.DebugLock` (extends `ReentrantLock`)

### Purpose
- Coordinates all editor state modifications
- Prevents races between UI events and background I/O
- Protects file buffer content during editing operations

### When biglock2 MUST be held

The following operations require holding `biglock2`:

1. **All UI/event loop operations**
   - Processing key events
   - Executing editor commands
   - Cursor movement and display updates

2. **File buffer modifications** (EditContainer/TextEdit)
   - `inserttext()`, `insertStrings()`
   - `delete()`, `remove()`, `changeElementAt()`
   - `substitute()`, `processCommand()`
   - Any method that modifies the edit cache

3. **View state changes**
   - Cursor position updates
   - Screen coordinate changes
   - Insert mode state

4. **File hash operations** (EditContainer static methods)
   - `findfile()`, `grepfile()`
   - `registeruniq()` (adding files)
   - `cleanup()` (removing files)

5. **Undo history operations**
   - Recording changes
   - Undo/redo execution
   - Base record operations

### When biglock2 must NOT be held

Holding `biglock2` during these operations can cause deadlock:

1. **Waiting for external processes**
   - Shell command execution
   - External tool invocation (ctags, make, etc.)

2. **Blocking I/O operations**
   - File reads (especially network files)
   - Socket operations
   - User input dialogs that may block

3. **Acquiring other locks**
   - AWT EDT operations (can cause deadlock with UI)
   - Synchronized blocks on other objects
   - Any wait() call without releasing biglock2 first

### Known Lock Ordering Issues

See BUGS.md B3 for detailed analysis of deadlock risks.

**Resolved: nextKeye/insert ABBA deadlock (B4)**

`nextKeye()` was formerly declared `static synchronized`, holding the
`EventQueue.class` monitor for the entire `nextEvent→inextEvent` call.
`inextEvent` releases and reacquires `biglock2` multiple times (for idle
handlers, cursor blinking, etc.). Meanwhile, `insert()` is also
`static synchronized` (holds `EventQueue.class`). If any thread ever held
`biglock2` and then called `insert()`, classic ABBA deadlock:

```
Thread A (event loop): holds EventQueue.class → wants biglock2
Thread B (any thread):  holds biglock2          → wants EventQueue.class
```

**Fix**: Removed `synchronized` from `nextKeye()`. Queue access is already
protected by fine-grained `synchronized(EventQueue.class)` blocks inside
`inextEvent()`, so the outer monitor was redundant. The main event loop is
single-threaded, so no concurrent `nextKeye` calls are possible.

**Pattern to avoid:**
```java
// DANGEROUS: Nested locking in opposite order
synchronized (someObject) {
    EventQueue.biglock2.lock();  // Can deadlock if another thread holds in reverse
}
```

**Pattern in IoConverter.expandLock():**
```java
// Releases biglock2 while holding 'this' monitor - potential deadlock
synchronized (this) {
    EventQueue.biglock2.unlock();
    wait(2000);
}
```

## Thread Types

### Main Event Thread
- Runs the main event loop in `EventQueue.nextEvent()`
- Holds `biglock2` for most operations
- Releases lock during event waits

### Background I/O Threads (IoConverter)
- Created per-file for background loading
- Run at `MIN_PRIORITY`
- Acquire/release `biglock2` at specific points
- Use `expandLock()` for coordination

### AWT Event Dispatch Thread
- Handles native GUI events
- Forwards to EventQueue
- Should NOT hold `biglock2` during AWT operations

### Server Thread (Server.java)
- Handles external file open requests
- Acquires `biglock2` only when needed

## Synchronized Collections

### listeners (EditContainer)
```java
private static final ArrayList<FileStatusListener> listeners = 
    new ArrayList<FileStatusListener>();
```
- Synchronized separately using `synchronized (listeners)`
- Used for file status notifications
- Thread-safe iteration required

### filehash (EditContainer)
```java
private static HashMap<FileDescriptor, EditContainer> filehash
```
- Protected by `biglock2` (uses `assertOwned()`)
- Maps file descriptors to edit containers
- NOT internally synchronized - relies on biglock2

### event queue (EventQueue)
```java
private static LinkedList<Object> queue
```
- Protected by `synchronized (EventQueue.class)`
- Separate from biglock2 to allow event insertion during lock

## DebugLock Features

The `DebugLock` class extends `ReentrantLock` with:

1. **assertOwned()** - Verify current thread holds lock
   ```java
   EventQueue.biglock2.assertOwned();  // Throws if not held
   ```

2. **assertUnOwned()** - Verify current thread doesn't hold lock
   ```java
   EventQueue.biglock2.assertUnOwned();  // Throws if held
   ```

3. **Timeout-based acquisition** - Logs on contention
   - 2-second timeout before logging
   - Shows owning thread on timeout

## Best Practices

### Adding New Synchronized Code

1. **Prefer using biglock2** over introducing new locks
2. **Assert lock state** at entry to methods requiring synchronization:
   ```java
   void modifyBuffer() {
       EventQueue.biglock2.assertOwned();
       // ... modify state
   }
   ```

3. **Release biglock2 for long operations:**
   ```java
   biglock2.unlock();
   try {
       // long operation
   } finally {
       biglock2.lock();
   }
   ```

### Avoiding Deadlocks

1. **Never acquire multiple locks** if possible
2. **If multiple locks needed**, always acquire in same order:
   - biglock2 first, then any object monitors
3. **Release biglock2 before blocking I/O**
4. **Use tryLock() with timeout** when acquiring from non-main threads

### Testing Thread Safety

1. Run editor with concurrent file operations
2. Test rapid file switching during background load
3. Test external file modifications during editing
4. Monitor for "failed to get lock" messages in trace output

## Files with Thread Safety Documentation

The following files have thread-safety relevant code:

- `EventQueue.java` - Lock definition and event loop
- `EditContainer.java` - Buffer storage, biglock2 assertions
- `TextEdit.java` - Text editing operations
- `IoConverter.java` - Background I/O, lock release
- `View.java` - Display state
- `Server.java` - External requests
- `AwtInterface.java` - GUI event handling
- `StatusBar.java` - Status updates from multiple threads

## Related Documentation

- `BUGS.md` - B3, B4, B7 describe thread safety issues
- `AGENTS.md` - Notes on biglock2 for AI assistants
- Javadoc on individual classes
