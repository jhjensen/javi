# Javi Modernization Improvements

This document lists opportunities to modernize the Javi codebase to current Java standards.

## High Priority

### 1. Try-With-Resources (Java 7+)
**Current Issue**: Manual resource management with try/finally blocks
**Impact**: Potential resource leaks, verbose code

**Files affected**:
- `Command.java:80` - BufferedReader not in try-with-resources
- `Plugin.java:59,92,96` - ZipFile and ZipInputStream manual closing
- `TextEdit.java:263-270` - PrintWriter manual closing
- `Server.java:53,94,96` - Socket and stream manual closing
- `Ctag.java:83,151` - RandomAccessFile manual closing
- `MapEvent.java:264` - BufferedReader manual closing
- `JavaCompiler.java:159` - FileManager manual closing
- `FileProperties.java:75` - OutputStreamWriter manual closing
- `UI.java:217` - PrintWriter manual closing

**Fix**: Convert to:
```java
try (BufferedReader br = new BufferedReader(...)) {
    // use br
}
```

### 2. Use Diamond Operator (Java 7+)
**Current Issue**: Redundant type parameters on right side of generic instantiation
**Impact**: Code verbosity

**Examples**:
```java
// Current
HashMap<String, KeyBinding> cmhash = new HashMap<String, KeyBinding>(200);
// Modern
HashMap<String, KeyBinding> cmhash = new HashMap<>(200);
```

**Files affected**: Throughout codebase

### 3. Replace finalize() with Cleaner (Java 9+)
**Current Issue**: PersistantStack.java:642 uses deprecated `finalize()`
**Impact**: Deprecated API marked for removal

**Fix**: Use `java.lang.ref.Cleaner` instead

### 4. Replace AccessController (Java 17+)
**Current Issue**: Plugin.java:248 uses deprecated `AccessController.doPrivileged()`
**Impact**: Deprecated API marked for removal in future Java versions

### 5. Remove System.runFinalization() (Java 18+)
**Current Issue**: Tools.java:142 uses deprecated `runFinalization()`
**Impact**: Deprecated and marked for removal

## Medium Priority

### 6. Use Optional Instead of Null Checks
**Current Issue**: Extensive `null != value` and `value != null` checks throughout

**Files with heavy null checking**:
- `IoConverter.java`
- `InsertBuffer.java`
- `EditContainer.java`
- `FvContext.java`

**Fix**: Use `Optional<T>` for return values that may be absent

### 7. Use var for Local Variables (Java 10+)
**Current Issue**: Verbose type declarations for obvious types

**Example**:
```java
// Current
Iterator<Map.Entry<String, KeyBinding>> eve = cmhash.entrySet().iterator();
// Modern
var eve = cmhash.entrySet().iterator();
```

### 8. Switch Expressions (Java 14+)
**Current Issue**: Many switch statements with fall-through

**Files affected**:
- `TextEdit.java:133`
- `EditContainer.java:380`
- `UI.java:92`
- `AwtInterface.java:347`
- `FileList.java:420`
- `OldView.java:577`

**Fix**: Convert to switch expressions where applicable

### 9. Text Blocks (Java 15+)
**Current Issue**: Multi-line strings using concatenation

**Fix**: Use text blocks for multi-line strings:
```java
String sql = """
    SELECT * 
    FROM users
    WHERE active = true
    """;
```

### 10. Records for Data Classes (Java 16+)
**Current Issue**: Position, MovePos could be records

**Example**:
```java
// Current Position.java
public final class Position {
    public final int x;
    public final int y;
    public final FileDescriptor filename;
    public final String comment;
    // constructor, equals, hashCode, toString...
}

// Modern
public record Position(int x, int y, FileDescriptor filename, String comment) {}
```

### 11. Sealed Classes (Java 17+)
**Current Issue**: Class hierarchies could benefit from sealing

**Candidates**:
- `FileDescriptor` and subclasses
- `IoConverter` and subclasses
- `View` and subclasses

### 12. Pattern Matching for instanceof (Java 16+)
**Current Issue**: Manual casting after instanceof

**Example**:
```java
// Current (Buffers.java)
if (bufo instanceof ArrayList)
    ((ArrayList<String>) bufo).add(buffer);

// Modern
if (bufo instanceof ArrayList<String> list)
    list.add(buffer);
```

## Lower Priority

### 13. Update Collection APIs
**Current Issue**: Using older iteration patterns

**Improvements**:
- Use `List.of()`, `Set.of()`, `Map.of()` for immutable collections
- Use `Stream` API where appropriate
- Use `forEach` instead of explicit iteration

### 14. Replace Raw Types with Generics
**Current Issue**: Some raw type usage remains

**Files**:
- `Rgroup.java:119` - `Class nclass = Class.forName(lclass)`
- Various places with raw iterators

### 15. Use @Override Annotation Consistently
**Current Issue**: Very few `@Override` annotations in codebase

**Impact**: Missing compile-time verification of method overriding

### 16. Use Enhanced For-Each Where Possible
**Current Issue**: Some explicit iterator usage

### 17. String API Improvements
- Use `String.isBlank()` instead of `trim().isEmpty()`
- Use `String.repeat()` for repeated strings
- Use `String.strip()` instead of `trim()` for Unicode whitespace

### 18. Modernize Threading
**Current Issue**: Manual thread management

**Consider**:
- `CompletableFuture` for async operations
- `ExecutorService` instead of manual Thread creation
- `AtomicReference` instead of `volatile` + `synchronized`

### 19. Use Files API (Java 7+)
**Current Issue**: Using legacy `File` API in places

**Improvements**:
- `Files.readAllLines()`, `Files.readString()` (Java 11)
- `Files.writeString()` (Java 11)
- `Path` instead of `File` where appropriate

### 20. Dependency Injection
**Current Issue**: Heavy use of singletons and static state

**Consider**: Using a DI framework or at least constructor injection

## Build System Improvements

### 21. Proper Dependency Management
**Current Issue**: JARs in lib/ folder

**Fix**: Use Maven Central dependencies in build.gradle:
```gradle
dependencies {
    implementation 'org.mozilla:rhino:1.7.14'
    implementation 'com.github.albfernandez:juniversalchardet:2.4.0'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
}
```

### 22. Update JUnit
**Current Issue**: JUnit 3.x in use

**Fix**: Migrate to JUnit 5 with modern annotations

### 23. Enable More Compiler Warnings
**Fix**: Add to build.gradle:
```gradle
tasks.withType(JavaCompile) {
    options.compilerArgs += ['-Xlint:all', '-Werror']
}
```

## Code Quality

### 24. Add Javadoc
**Current Issue**: Minimal documentation

### 25. Extract Constants
**Current Issue**: Magic numbers and strings scattered throughout

### 26. Reduce Method Length
**Current Issue**: Some very long methods (AwtInterface.java ~1640 lines)

### 27. Remove Commented Code
**Current Issue**: Lots of commented-out code throughout

## Testing

### 28. Add Unit Tests
**Current Issue**: Very limited test coverage

### 29. Integration Tests
**Current Issue**: No automated integration testing

## Migration Path

Recommended order for modernization:

1. **Phase 1** (Quick wins, no breaking changes):
   - Add @Override annotations
   - Diamond operator
   - Try-with-resources
   - Enhanced for-each

2. **Phase 2** (Java 11 baseline):
   - var keyword
   - New String methods
   - Files API

3. **Phase 3** (Java 17 baseline):
   - Records for data classes
   - Pattern matching for instanceof
   - Sealed classes
   - Switch expressions

4. **Phase 4** (Infrastructure):
   - Build system modernization
   - JUnit 5 migration
   - Dependency management
