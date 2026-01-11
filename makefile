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

# Copy JAR to dist directory
dist: jar
	./gradlew dist

# Copy fat JAR to dist directory  
dist-fat: fatjar
	./gradlew distFat

# Full build (compile + jar)
build: compile jar

#==============================================================================
# Test targets
#==============================================================================

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

# Run all tests
test: ctagtest intarraytest pstest edittest 

# Run PSTest with coverage and generate report
pstest-coverage:
	./gradlew pstestCoverage
	java -jar lib/org.jacoco.cli-0.8.12-nodeps.jar report build/jacoco/pstest.exec \
		--classfiles build/classes/java/main \
		--sourcefiles src/main/java --sourcefiles src/history/java \
		--html build/reports/coverage-pstest
	@echo "Coverage report: build/reports/coverage-pstest/index.html"

# Run all tests with coverage and generate report
test-coverage:
	./gradlew pstestCoverage intArrayTestCoverage
	java -jar lib/org.jacoco.cli-0.8.12-nodeps.jar report build/jacoco/pstest.exec build/jacoco/intarraytest.exec \
		--classfiles build/classes/java/main \
		--sourcefiles src/main/java --sourcefiles src/history/java \
		--html build/reports/coverage
	@echo "Coverage report: build/reports/coverage/index.html"

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
	@echo "  make compile  - Compile Java sources (warnings are errors)"
	@echo "  make jar      - Build JAR file"
	@echo "  make fatjar   - Build fat JAR with dependencies"
	@echo "  make dist     - Copy JAR to dist/ directory"
	@echo "  make dist-fat - Copy fat JAR to dist/ directory"
	@echo "  make build    - Full build (compile + jar)"
	@echo "  make clean    - Clean build artifacts"
	@echo ""
	@echo "Test:"
	@echo "  make test     - Run all tests"
	@echo "  make pstest   - Run PersistantStack tests"
	@echo "  make edittest - Run EditTester1 tests"
	@echo ""
	@echo "Run:"
	@echo "  make run      - Run the application"
	@echo "  make run-file FILE=path/to/file - Run with specific file"
	@echo ""
	@echo "Code Quality:"
	@echo "  make cstyle   - Run checkstyle on all Java files"
	@echo "  make cstyle-file FILE=path - Run checkstyle on specific file"
	@echo ""
	@echo "Development:"
	@echo "  make tags     - Generate ctags"
	@echo "  make ID       - Generate ID database"
	@echo "  make help     - Show this help"
