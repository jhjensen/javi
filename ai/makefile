# ai/makefile — Output-capture targets for AI agent automation
#
# Writes command output to files under ai/ so the agent can run make
# targets without ad-hoc shell redirection (which triggers manual approval).
#
# Invocation (from any cwd):
#   make -C /Users/jjensen/javi -f ai/makefile compile.out
#   make -C /Users/jjensen/javi -f ai/makefile junit.out
#   make -C /Users/jjensen/javi -f ai/makefile cstyle.out
#   make -C /Users/jjensen/javi -f ai/makefile cstyle-file.out FILE=src/main/java/javi/Foo.java
#
# After running, read the ai/<name>.out file for results.
# Every target uses FORCE so it re-runs unconditionally.

.PHONY: FORCE
FORCE:

# Compile Java sources
ai/compile.out: FORCE
	$(MAKE) compile > $@ 2>&1; echo "exit: $$?" >> $@

# JUnit 5 tests (Gradle)
ai/junit.out: FORCE
	./gradlew test > $@ 2>&1; echo "exit: $$?" >> $@

# Legacy tests (compile + ctagtest + intarraytest + pstest + edittest)
ai/test.out: FORCE
	$(MAKE) compile test-core > $@ 2>&1; echo "exit: $$?" >> $@

# Checkstyle — all Java source files
ai/cstyle.out: FORCE
	perl cstyle src/main/java/javi/*.java src/history/java/history/*.java > $@ 2>&1; echo "exit: $$?" >> $@

# Checkstyle — single file (pass FILE= on command line)
# Usage: make -C /path/to/javi -f ai/makefile cstyle-file.out FILE=src/main/java/javi/Foo.java
ai/cstyle-file.out: FORCE
	perl cstyle $(FILE) > $@ 2>&1; echo "exit: $$?" >> $@

# Javadoc generation
ai/javadoc.out: FORCE
	$(MAKE) javadoc > $@ 2>&1; echo "exit: $$?" >> $@

# Git diff (for reviewing changes before commit)
ai/diff.out: FORCE
	git diff > $@ 2>&1

# Git status
ai/git-status.out: FORCE
	git status > $@ 2>&1

# Convenience aliases (short names)
compile.out: ai/compile.out
junit.out:   ai/junit.out
test.out:    ai/test.out
cstyle.out:  ai/cstyle.out
javadoc.out: ai/javadoc.out
