# Javi Testing Methodology

## Overview

Javi uses a layered testing approach combining headless integration tests
with JaCoCo coverage tracking.

## Test Frameworks

### JUnit 5 (primary)

All new tests go in `src/test/java/javi/` as JUnit 5 test classes.

- **TextEditJUnitTest**: 28 tests covering core editing operations —
  insert, undo/redo, persistence, tabfix, joinlines, deletetext,
  changecase, processCommand (move, copy, substitute, global),
  and backup recovery. Ports all 18 original EditTester1 tests.

- **TestInit**: Shared initializer that boots the editor in headless mode
  (no AWT/View). Called via `@BeforeAll`.

- **Lock discipline**: Tests acquire `EventQueue.biglock2` in `@BeforeEach`
  because EditContainer operations assert ownership of that lock.

### Legacy EditTester1

The original `TextEdit.test()` suite in `TextEdit.java` (18 methods,
invoked via `make test-core`). These remain as a regression safety net
but all have JUnit equivalents.

### StreamInterface pattern

The `UI.setStream(new StringReader("..."))` pattern drives interactive
prompts (e.g., "file vs backup?" dialog) without a real terminal.
This is the primary mechanism for testing code paths that prompt the user.

Responses: `"b\n"` = backup, `"f\n"` = file, `"fb\n"` = file then backup
on successive opens.

## Coverage

JaCoCo is integrated via the Gradle `jacoco` plugin:

```bash
./gradlew test jacocoTestReport          # HTML + XML reports
./gradlew jacocoTestCoverageVerification # Enforce minimum threshold
```

Reports: `build/reports/jacoco/test/html/index.html`

Current threshold: **15%** instruction coverage (will increase as tests grow).

Standalone coverage for non-JUnit test drivers:
```bash
./gradlew pstestCoverage      # history.PSTest
./gradlew intArrayTestCoverage # history.IntArrayTest
```

## Adding New Tests

1. Create a JUnit 5 method in the appropriate test class
2. Use `deleteTestFiles(name)` to clean up before/after
3. Use `openTestFile(name)` to create/open a TextEdit instance
4. Use `UI.setStream(...)` if the code path prompts the user
5. Always `disposeFvc()` before reopening the same file
6. Run `./gradlew test jacocoTestReport` and verify coverage

## What Cannot Be Tested Headlessly

- **FvContext / View**: Too tightly coupled to AWT. FvContext tests
  require the full GUI stack (AssertJ Swing or similar). Deferred.
- **Terminal emulation**: Requires a real PTY. Integration tested via
  `plx-tests-py` test suites instead.
