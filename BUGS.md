# Javi Potential Bugs and Issues

This document lists potential bugs and issues discovered during code analysis.

## Verified Test Failures

### T1. ArrayIndexOutOfBoundsException in Backup Reading
**Location**: `ByteInput.java:109`, `PersistantStack.java:525`
**Status**: CONFIRMED - occurs during EditTester1 test16

```
java.lang.ArrayIndexOutOfBoundsException: Array index out of range: 7
    at history.ByteInput.readByte(ByteInput.java:109)
    at history.PersistantStack.setFile(PersistantStack.java:525)
```

### T2. Unclosed File Resource in PersistantStack
**Location**: `PersistantStack.java:644`
**Status**: CONFIRMED - finalize() detects unclosed file during test16

```
PersitantStack.finalize found unclosed file
writtencount = 4 rfile = extest15.dmp2
```

### T3. PSTest Callback Test Failure
**Location**: `PSTest.java:168`
**Status**: CONFIRMED - assertion failure in callbacktest

```
java.lang.RuntimeException: ASSERTION FAILURE callback.test
    at history.Testutil.myassert(Testutil.java:48)
    at history.PSTest.callbacktest(PSTest.java:168)
```

## Critical Issues

### 1. Resource Leaks - Missing Close in Exception Paths

**Location**: Multiple files
**Severity**: High

**Server.java:26-70**
```java
// Issue: instream is never closed if exception occurs after accept()
// The commented code at lines 60-69 mentions this causes socket closure
// but leaving streams unclosed may cause resource leaks
```

**Command.java:73-81**
```java
BufferedReader ini = ifile.getBufferedReader();
try {
    // ...
} finally {
    ini.close();
}
// Issue: If getBufferedReader() succeeds but cmdlist.add() throws,
// the reader will be closed but exception isn't propagated correctly
```

### 2. Potential Deadlock in IoConverter

**Location**: `IoConverter.java:143-179`
**Severity**: High

```java
final synchronized int expandLock(int desired) throws IOException {
    // Lock released inside synchronized block
    EventQueue.biglock2.unlock();
    wait(2000);
    return 2;
}
```

The `expandLock` method releases `biglock2` while holding `this` monitor, which could lead to deadlock if another thread tries to acquire locks in the opposite order.

### 3. Thread Safety Issues in EditContainer

**Location**: `EditContainer.java`
**Severity**: Medium

`EditContainer.listeners` is accessed without synchronization in some paths while being modified with `synchronized (listeners)` in others.

### 4. Swallowed Exceptions

**Location**: Multiple files
**Severity**: Medium

Many catch blocks that catch `Throwable` or `Exception` only log and continue:

**AwtCircBuffer.java:86**
```java
} catch (Exception e) {
    // Continues without proper handling
}
```

**OldView.java:607, 700, 710, 754**
```java
} catch (Throwable e) {
    // Exception is logged but may leave state inconsistent
}
```

### 5. Unchecked Type Casts

**Location**: Multiple files
**Severity**: Medium

**Buffers.java:37-39, 63-65**
```java
((ArrayList<String>) bufo).add(buffer);
// No guarantee bufo is ArrayList<String>
```

**EditCache.java:86**
```java
OType[] outarray = (OType[]) new Object[count];
// This will fail at runtime for primitive types
```

### 6. Potential NullPointerException

**Location**: `Position.java:35-44`
**Severity**: Medium

```java
public boolean equals(Object ob) {
    if (null == ob)
        return false;
    if (ob == this)
        return true;
    if (ob instanceof Position) {
        Position po = (Position) ob;
        return filename.equals(po.filename) && po.x == x && po.y == y;
        // Issue: filename could be null (though constructor throws on null)
    }
    return false;
}
```

### 7. Integer Overflow in CircBuffer

**Location**: `Buffers.java:103-106`
**Severity**: Low

```java
public final void add(String ob) {
    if (++index >= buf.length)
        index = 0;
    // No check for overflow if add called more than Integer.MAX_VALUE times
}
```

## Medium Priority Issues

### 8. Hardcoded Port Number

**Location**: `Server.java:20`

```java
new Server(6001);
// Should be configurable
```

### 9. Hardcoded Charset

**Location**: Multiple files

Many places use "UTF-8" hardcoded:
- `Vt100.java:30`
- `Server.java:33`
- `TextEdit.java:262`
- `Tools.java:102,107`

Should use `StandardCharsets.UTF_8` constant.

### 10. Improper equals/hashCode

**Location**: `Buffers.CircBuffer`
**Severity**: Low

No `equals()` or `hashCode()` override despite being used as values in collections.

### 11. Infinite Loop Potential

**Location**: `UI.java:93-133`

```java
while (true) {
    while (null == instance)
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            trace("ignoring interrupted exception");
        }
    // ...
}
```

If `ireportDiff` consistently returns an unhandled button value, this loops forever.

### 12. Static Mutable State

**Location**: `FvContext.java`

```java
private static FvMap fvmap = new FvMap();
private static FvContext defaultFvc;
private static FvContext currfvc;
```

Heavy use of static mutable state makes testing difficult and can lead to race conditions.

### 13. Unguarded Array Access

**Location**: `EditCache.java:88-91`

```java
public OType[] getArr(int index, int count) {
    OType[] outarray = (OType[]) new Object[count];
    for (int i = 0; i < count; i++)
        outarray[i] = varray.get(index + i);
    // No bounds checking
    return outarray;
}
```

### 14. Lock Not Released on All Paths

**Location**: `EventQueue.java:88-111`

```java
private static Object inextEvent(CursorControl vi) {
    biglock2.unlock();
    // ... complex logic with multiple return paths
    biglock2.lock();
    return ev;
}
```

If an exception is thrown, the lock state may be incorrect.

### 15. Deprecated API Usage (Runtime Errors)

**Location**: `PersistantStack.java:642`

```java
protected final void finalize() throws Throwable {
    // finalize() is deprecated and unreliable
}
```

This may not be called at all in modern JVMs.

## Low Priority Issues

### 16. Magic Numbers

**Location**: Throughout codebase

Examples:
- `EventQueue.java:64` - `timeout = 500`
- `MiscCommands.java:33-34` - `defwidth = 80`, `defheight = 80`
- `Buffers.java:17` - `circSize = 10`

### 17. Empty Catch Blocks

**Location**: Multiple files

```java
} catch (InterruptedException e) {
    /* Ignore */
}
```

### 18. Unused Variables

Some variables are assigned but never read (would be caught by static analysis).

### 19. Long Methods

**Location**: 
- `AwtInterface.java` - entire class ~1640 lines
- `EditContainer.java` - ~1425 lines  
- `TextEdit.java` - ~1578 lines

These should be refactored into smaller classes/methods.

### 20. Inconsistent Error Handling

Some places throw `RuntimeException`, others return null, others log and continue.

## Testing Gaps

### 21. Limited Test Coverage

Only a few test files exist:
- `javitests/AtViewTest.java`
- `javitests/ViewTest.java`
- `javitests/perftest.java`
- `src/history/java/history/PSTest.java`

Most core functionality is untested.

### 22. No Mocking

Tests directly instantiate heavyweight objects, making testing slow and fragile.

## Configuration Issues

### 23. Hardcoded Paths

**Location**: `makefile`, `ms` script

```bash
jroot=/Users/jjensen/javi
```

Should use relative paths or environment variables.

## Security Issues

### 24. Command Injection Potential

**Location**: `Tools.java:70-130`

Commands are built from strings without proper escaping:
```java
public static InputStream executeIn(String content, String... command)
```

### 25. Server Without Authentication

**Location**: `Server.java`

TCP server on port 6001 accepts any connection without authentication.

## Recommended Fixes Priority

1. **Immediate**: Resource leaks (try-with-resources)
2. **Immediate**: Fix deadlock potential in IoConverter
3. **High**: Add proper exception handling
4. **High**: Fix thread safety issues
5. **Medium**: Add bounds checking
6. **Medium**: Remove deprecated API usage
7. **Low**: Refactor long methods
8. **Low**: Add comprehensive tests
