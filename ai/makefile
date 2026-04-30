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
#   make -C /Users/jjensen/javi -f ai/makefile validate.out   # junit + cstyle combined
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

# JaCoCo coverage report + verification (runs JUnit tests + legacy tests first)
ai/coverage.out: FORCE
	./gradlew test pstestCoverage intArrayTestCoverage jacocoTestCoverageVerification > $@ 2>&1; echo "exit: $$?" >> $@

# Test coverage (runs JUnit + legacy tests, generates merged JaCoCo report)
ai/test-coverage.out: FORCE
	$(MAKE) test-coverage > $@ 2>&1; echo "exit: $$?" >> $@

# Pre-commit validation: JUnit tests + cstyle (fails if either fails)
ai/validate.out: FORCE
	@rc=0; \
	echo "=== JUnit tests ===" > $@; \
	./gradlew test >> $@ 2>&1; junit_rc=$$?; \
	echo "exit: $$junit_rc" >> $@; \
	echo "" >> $@; \
	echo "=== Cstyle ===" >> $@; \
	perl cstyle src/main/java/javi/*.java src/history/java/history/*.java >> $@ 2>&1; cstyle_rc=$$?; \
	echo "exit: $$cstyle_rc" >> $@; \
	echo "" >> $@; \
	if [ $$junit_rc -ne 0 ] || [ $$cstyle_rc -ne 0 ]; then rc=1; fi; \
	echo "=== Summary ===" >> $@; \
	echo "junit: $$junit_rc  cstyle: $$cstyle_rc  overall: $$rc" >> $@; \
	exit $$rc

# Remote GUI tests (rdesk + Docker + Xvfb)
ai/rdesk-guitest.out: FORCE
	$(MAKE) rdesk-guitest > $@ 2>&1; echo "exit: $$?" >> $@

# Remote ALL tests (headless JUnit + GUI via Docker + Xvfb)
ai/rdesk-alltest.out: FORCE
	$(MAKE) rdesk-alltest > $@ 2>&1; echo "exit: $$?" >> $@

# T3: Full coverage (JUnit + legacy + merge report)
ai/full-coverage.out: FORCE
	$(MAKE) full-coverage > $@ 2>&1; echo "exit: $$?" >> $@

# T3: Coverage dump from running javi
ai/coverage-dump.out: FORCE
	$(MAKE) coverage-dump > $@ 2>&1; echo "exit: $$?" >> $@

# T3: Merge all .exec files and generate report
ai/coverage-merge.out: FORCE
	$(MAKE) coverage-merge > $@ 2>&1; echo "exit: $$?" >> $@

# T1: Full coverage including GUI tests via rdesk Docker
ai/rdesk-coverage.out: FORCE
	$(MAKE) rdesk-coverage > $@ 2>&1; echo "exit: $$?" >> $@

# F22: Remote esctest2 VT100 compliance tests
ai/rdesk-esctest2.out: FORCE
	$(MAKE) rdesk-esctest2 > $@ 2>&1; echo "exit: $$?" >> $@
