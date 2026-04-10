#JDK=jdk1.6.0_18
JDKPATH=/usr/lib/jvm/java-6-sun-1.6.0.15
JDKPATH=
JDKBIN=$(JDKPATH)/bin/
JDKBIN=
JAVALIB= $(JDKPATH)/lib/jvm.lib
#CFLAGS = -c
CC=g++
#CFLAGS += -mno-cygwin
#LDFLAGS += -mno-cygwin -lwsock32
LINK=ld

# Default target
all: ctag

# Classpath for running Java
R=/Users/jjensen/javi
export CLASSPATH=$R/build/classes/java/main:$R/lib/juniversalchardet-1.0.3.jar:$R/lib/rhino-1.7.14.jar

#==============================================================================
# Build targets
#==============================================================================

# Compile Java sources (warnings are errors by default)
compile:
	./gradlew compileJava

# Clean build artifacts
clean:
	./gradlew clean

# Build JAR file
jar:
	./gradlew jar

# Build fat JAR with all dependencies
fatjar:
	./gradlew shadowJar

#==============================================================================
# I3: Release Distribution Targets
#==============================================================================

# Version from git describe (tag-commits-hash) or 'dev' if no tags
VERSION := $(shell git describe --tags --always --dirty 2>/dev/null || echo "dev")

# Copy JAR to dist directory
dist: jar plugins
	./gradlew distAll

# Build all plugin JARs
plugins: compile
	./gradlew plugins

# Copy fat JAR to dist directory  
dist-fat: fatjar
	./gradlew distFat

# Verify working directory is clean (no uncommitted changes)
verify-clean:
	@if [ -n "$$(git status --porcelain)" ]; then \
		echo "Error: Working directory not clean. Commit or stash changes first."; \
		git status --short; \
		exit 1; \
	fi
	@echo "✓ Working directory clean"

# Create a release distribution
# 1. Verifies clean git status
# 2. Compiles and checks for errors
# 3. Runs all tests
# 4. Creates versioned JAR
# 5. Creates git tag
dist-release: verify-clean compile test
	@echo ""
	@echo "Building release $(VERSION)..."
	@mkdir -p dist
	./gradlew jar -Pversion=$(VERSION)
	cp build/libs/javi-$(VERSION).jar dist/
	@echo ""
	@echo "✓ Created: dist/javi-$(VERSION).jar"
	@echo ""
	@echo "To create a git tag, run:"
	@echo "  git tag -a v$(VERSION) -m 'Release $(VERSION)'"
	@echo "  git push origin v$(VERSION)"

# Create a release with fat JAR (includes all dependencies)
dist-release-fat: verify-clean compile test
	@echo ""
	@echo "Building fat release $(VERSION)..."
	@mkdir -p dist
	./gradlew shadowJar -Pversion=$(VERSION)
	cp build/libs/javi-all-$(VERSION).jar dist/
	@echo ""
	@echo "✓ Created: dist/javi-all-$(VERSION).jar"

# Show current version
version:
	@echo "Current version: $(VERSION)"

# Full build (compile + jar)
build: compile jar

#==============================================================================
# Test targets
#==============================================================================

.PHONY: FORCE docs
FORCE:

ai.output:
	mkdir -p $@

ai.output/junit.out: FORCE | ai.output
	./gradlew test > $@ 2>&1

ai.output/test.out: FORCE | ai.output
	$(MAKE) compile test-core > $@ 2>&1

# Run PSTest (PersistantStack tests)
pstest: compile
	java -cp $(CLASSPATH) history.PSTest

# Run EditTester1 (TextEdit tests)
edittest: compile
	java -cp $(CLASSPATH) javi.EditTester1

# Run IntArrayTest (IntArray tests)
intarraytest: compile
	java -ea -cp $(CLASSPATH) history.IntArrayTest

ctagtest: compile
	java -ea -cp $(CLASSPATH) javi.Ctag

# Core legacy test sequence (without wrapper)
test-core: ctagtest intarraytest pstest edittest

# Run all legacy tests and capture output
test: ai.output/test.out

# Run JUnit 5 tests via Gradle and capture output
junit: ai.output/junit.out

# Compile test sources
compile-test:
	./gradlew compileTestJava

# Run GUI tests (AssertJ Swing — requires display/Xvfb)
guitest:
	./gradlew guiTest

#==============================================================================
# Remote GUI testing (rdesk + Docker)
#==============================================================================

RDESK_GUITEST_DIR = /tmp/javi-guitest
GUITEST_IMAGE = javi-guitest

GUITEST_EXCLUDE = --exclude=build --exclude=.gradle --exclude=.git \
   --exclude='*.dmp2' --exclude=ai.output --exclude=ai/*.out \
   --exclude=bin --exclude=oldstuff --exclude=tmp

# Full pipeline: sync, build Docker image, run GUI tests, fetch results
rdesk-guitest: rdesk-guitest-sync rdesk-guitest-build rdesk-guitest-run rdesk-guitest-fetch

# Sync javi source to rdesk
rdesk-guitest-sync:
	rsync -az $(GUITEST_EXCLUDE) ./ rdesk:$(RDESK_GUITEST_DIR)/

# Build Docker image on rdesk
rdesk-guitest-build: rdesk-guitest-sync
	ssh -n -T rdesk 'cd $(RDESK_GUITEST_DIR) && \
	   docker build -f Dockerfile.guitest -t $(GUITEST_IMAGE) .'

# Run GUI tests on rdesk via Docker
rdesk-guitest-run: rdesk-guitest-build
	ssh -n -T rdesk 'cd $(RDESK_GUITEST_DIR) && \
	   docker run --rm \
	      -v $$(pwd)/build:/app/build \
	      $(GUITEST_IMAGE)'

# Fetch test results from rdesk
rdesk-guitest-fetch:
	rsync -az rdesk:$(RDESK_GUITEST_DIR)/build/reports/ \
	   build/reports-rdesk/ 2>/dev/null || true
	rsync -az rdesk:$(RDESK_GUITEST_DIR)/build/test-results/ \
	   build/test-results-rdesk/ 2>/dev/null || true

# Clean remote Docker image and files
rdesk-guitest-clean:
	ssh -n -T rdesk 'docker rmi $(GUITEST_IMAGE) 2>/dev/null; \
	   rm -rf $(RDESK_GUITEST_DIR)'

#==============================================================================
# T1: Remote Docker all-tests (headless JUnit + GUI tests via Xvfb)
#==============================================================================

ALLTEST_IMAGE = javi-alltest

# Full pipeline: sync, build Docker image, run ALL tests, fetch results
rdesk-alltest: rdesk-guitest-sync rdesk-alltest-build rdesk-alltest-run rdesk-guitest-fetch

# Build all-test Docker image on rdesk
rdesk-alltest-build: rdesk-guitest-sync
	ssh -n -T rdesk 'cd $(RDESK_GUITEST_DIR) && \
	   docker build -f Dockerfile.alltest -t $(ALLTEST_IMAGE) .'

# Run ALL tests on rdesk via Docker
rdesk-alltest-run: rdesk-alltest-build
	ssh -n -T rdesk 'cd $(RDESK_GUITEST_DIR) && \
	   docker run --rm \
	      -v $$(pwd)/build:/app/build \
	      $(ALLTEST_IMAGE)'

# Clean all-test Docker image
rdesk-alltest-clean:
	ssh -n -T rdesk 'docker rmi $(ALLTEST_IMAGE) 2>/dev/null'

# Run PSTest with coverage and generate report
pstest-coverage:
	./gradlew pstestCoverage
	java -jar lib/org.jacoco.cli-0.8.12-nodeps.jar report build/jacoco/pstest.exec \
		--classfiles build/classes/java/main \
		--sourcefiles src/main/java --sourcefiles src/history/java \
		--html build/reports/coverage-pstest
	@echo "Coverage report: build/reports/coverage-pstest/index.html"
	@echo "NOTE: For full coverage including JUnit tests, use 'make test-coverage'"

# Run all tests with coverage and generate merged report
test-coverage:
	./gradlew test pstestCoverage intArrayTestCoverage mergedCoverageReport
	@echo "Merged coverage report: build/reports/jacoco/merged/html/index.html"

# Run test-coverage then parse XML into text summary
coverage-report: test-coverage compile
	java -cp build/classes/java/main javi.CoverageReport \
	   build/reports/jacoco/merged/merged.xml

#==============================================================================
# T3: GUI Coverage Targets (JaCoCo agent + tcpserver)
#==============================================================================

# Resolve JaCoCo agent JAR path from Gradle
JACOCO_AGENT = $(shell ./gradlew -q jacocoAgentPath 2>/dev/null)

# Launch javi with JaCoCo agent (coverage via tcpserver on port 6300)
# Usage: make run-coverage [FILE=myfile.txt]
# After exercising the GUI, run: make coverage-dump
run-coverage: compile
	@echo "Starting javi with JaCoCo coverage agent (port 6300)..."
	@echo "Use the editor, then run 'make coverage-dump' before quitting."
	java -javaagent:$(JACOCO_AGENT)=output=tcpserver,port=6300,address=127.0.0.1 \
	   -cp $(CLASSPATH) javi.Javi $(FILE)

# Dump coverage from running javi (must be started with run-coverage)
coverage-dump:
	./gradlew jacocoDump
	@echo "Coverage dumped to build/jacoco/gui.exec"

# Merge all .exec files (JUnit + legacy + GUI) and generate report
coverage-merge:
	./gradlew mergedCoverageReport
	@echo "Merged coverage report: build/reports/jacoco/merged/html/index.html"

# Full coverage workflow: JUnit + legacy tests + merge report
# If gui.exec exists (from prior run-coverage + coverage-dump), it's included
full-coverage: compile
	./gradlew test pstestCoverage intArrayTestCoverage mergedCoverageReport
	@echo "Merged coverage report: build/reports/jacoco/merged/html/index.html"

#==============================================================================
# Run targets
#==============================================================================

# Run the application (GUI mode)
run: compile
	java -cp $(CLASSPATH) javi.Javi

# Run with specific file
# Usage: make run-file FILE=myfile.txt
run-file: compile
	java -cp $(CLASSPATH) javi.Javi $(FILE)

#==============================================================================
# Code quality targets
#==============================================================================

# Run checkstyle on modified Java files
cstyle:
	perl cstyle src/main/java/javi/*.java src/history/java/history/*.java

# Run checkstyle on a specific file
# Usage: make cstyle-file FILE=src/main/java/javi/Javi.java
cstyle-file:
	perl cstyle $(FILE)

# Run checkstyle without filter (all violations are errors)
# Usage: make cstyle-nofilter              (all files)
#        make cstyle-nofilter FILE=<path>  (single file)
cstyle-nofilter:
ifdef FILE
	perl cstyle --nofilter $(FILE)
else
	perl cstyle --nofilter src/main/java/javi/*.java src/history/java/history/*.java
endif

# Show lines longer than 90 characters in a file
# Usage: make longlines FILE=src/main/java/javi/Javi.java
longlines:
	awk 'length > 90 {printf "%d: %d: %s\n", NR, length, $$0}' $(FILE)

#==============================================================================
# Documentation targets
#==============================================================================

# Build Typst documentation (PDF)
docs:
	typst compile docs/javi_manual.typ docs/javi_manual.pdf
	@echo "PDF generated: docs/javi_manual.pdf"

# Generate Javadoc documentation
javadoc:
	mkdir -p build/docs/javadoc
	javadoc -d build/docs/javadoc \
		-sourcepath src/main/java:src/history/java \
		-classpath lib/rhino-1.7.14.jar:lib/juniversalchardet-1.0.3.jar:lib/rxtx-2.1.7.jar \
		-subpackages javi:history \
		-windowtitle "Javi Editor API" \
		-doctitle "Javi - Vi-like Editor in Java" \
		-header "Javi Editor" \
		-quiet \
		-Xdoclint:none
	@echo "Javadoc generated: build/docs/javadoc/index.html"

#==============================================================================
# Development utility targets
#==============================================================================

PORCE:
# Generate tags for code navigation
tags: FORCE
	ctags -n -R src

# Generate ID database for gid/lid
ID: FORCE
	mkid -m ~/cyghome/id-lang.map src

# Update both tags and ID
id: tags ID

#==============================================================================
# Legacy targets (kept for compatibility)
#==============================================================================

jarf=build/libs/javi-all.jar

gbuild: compile

automake: runclass #runclass

runner: jar
	java -cp $(CLASSPATH) -jar build/libs/javi-1.0.jar

runclass:
	echo $$CLASSPATH
	java  -cp $(CLASSPATH) javi.Javi src history main java javi awt history

FORCE:

install: jar
	cp build/libs/javi-1.0.jar vi.pl /usr/share/java
	chmod +x /usr/share/java/vi.pl /usr/share/java/javi-1.0.jar

#==============================================================================
# Help
#==============================================================================

help:
	@echo "Javi Makefile Targets:"
	@echo ""
	@echo "Build:"
	@echo "  make compile      - Compile Java sources (warnings are errors)"
	@echo "  make jar          - Build JAR file"
	@echo "  make fatjar       - Build fat JAR with dependencies"
	@echo "  make build        - Full build (compile + jar)"
	@echo "  make clean        - Clean build artifacts"
	@echo ""
	@echo "Distribution:"
	@echo "  make dist         - Copy JAR to dist/ directory"
	@echo "  make dist-fat     - Copy fat JAR to dist/ directory"
	@echo "  make dist-release - Build verified release (clean + test + versioned JAR)"
	@echo "  make dist-release-fat - Build verified fat JAR release"
	@echo "  make version      - Show current version from git"
	@echo ""
	@echo "Test:"
	@echo "  make test         - Run all tests"
	@echo "  make pstest       - Run PersistantStack tests"
	@echo "  make edittest     - Run EditTester1 tests"
	@echo "  make intarraytest - Run IntArray tests"
	@echo "  make test-coverage - Run tests with coverage report"
	@echo ""
	@echo "Run:"
	@echo "  make run          - Run the application"
	@echo "  make run-file FILE=path/to/file - Run with specific file"
	@echo ""
	@echo "Code Quality:"
	@echo "  make cstyle       - Run checkstyle on all Java files"
	@echo "  make cstyle-file FILE=path - Run checkstyle on specific file"
	@echo ""
	@echo "Documentation:"
	@echo "  make docs         - Build Typst manual (PDF)"
	@echo "  make javadoc      - Generate Javadoc documentation"
	@echo ""
	@echo "Development:"
	@echo "  make tags         - Generate ctags"
	@echo "  make ID           - Generate ID database"
	@echo "  make help         - Show this help"
