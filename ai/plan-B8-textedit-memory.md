# B8: File Load Memory Regression - Plan

## Completed
1. Stopped retaining full-file String in FileProperties (commit 12c01ae)
2. Streaming BufferedReader read path in FileInput (commit 810a1d7)
3. Memory benchmark + FileInputMemoryJUnitTest (8 tests) (commit 6efb83a)
4. Streaming charset detection in LocalFile.getBufferedReader() (commit c7a22e6)

## Remaining: .dmp2 Persistence Read Path

### Current State
`PersistantStack.readFile()` allocates entire .dmp2 file as ByteBuffer:
```java
ByteBuffer buf = ByteBuffer.allocate(length);  // full file in heap
```

### Memory Profile
- `setFile()` calls `readFile()` → `ByteBuffer.allocate(fileSize)` → `buf.array()` → full byte[] on heap
- `ByteInput(iarray)` wraps that byte[] — no copy, just a reference with offset/limit
- `binp` (instance field) holds the ByteInput, keeping the byte[] alive for random-access reads via `seek()`+`readExternal()`
- `offsets` (IntArray) stores record positions for O(1) lookup into `binp`
- `cache` (ArrayList) holds recently-accessed records in memory
- **Bug found**: `reset()` nulled `offsets` but NOT `binp`, leaking the byte[] until GC of the PersistantStack itself
- **Fix applied**: Added `binp = null` in `reset()` to release the backing byte[] when the file is closed

### Analysis
- .dmp2 files use random access (offset table + seek pattern)
- Memory-mapped approach was considered (commented code at line ~547) but abandoned
- ByteInput wraps byte[] for DataInput interface — changing to streaming requires significant refactor
- Risk: binary format parsing is fragile; changes could corrupt persistence

### Recommendation
- LOW priority: .dmp2 files are typically small (undo history, not full file content)
- The main memory wins have been achieved in FileInput (file content loading)
- The `binp = null` fix in `reset()` ensures the byte[] is released on close
- If profiling shows .dmp2 as a concern, consider MappedByteBuffer approach
- No further code change recommended without measured evidence of impact
