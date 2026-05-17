#!/bin/sh
# docker-entrypoint.sh — Entrypoint for javi-guitest-base image
#
# Starts Xvfb, initializes a minimal git repo (for build.gradle's
# git describe), runs Gradle with the provided task arguments, and
# writes parsed results to /app/build/guitest-output.txt.
#
# Arguments are passed directly to Gradle. Default: guiTest
# Examples:
#   docker-entrypoint.sh                      # guiTest (dirty build)
#   docker-entrypoint.sh clean guiTest        # clean + guiTest
#   docker-entrypoint.sh test guiTest         # headless + GUI
#   docker-entrypoint.sh clean test guiTest   # clean + all

set -e

TASKS="${*:-guiTest}"
OUTPUT=/app/build/guitest-output.txt
SUMMARY=/app/build/guitest-summary.txt

# Ensure writable directories exist
mkdir -p /app/build /app/.gradle 2>/dev/null || true

# Minimal git repo so build.gradle's `git describe` works
if [ ! -d /app/.git ]; then
    git init -q /app
    git -C /app config user.email "docker@build"
    git -C /app config user.name "docker"
fi
git -C /app add -A 2>/dev/null || true
git -C /app commit -q --allow-empty -m "docker run" 2>/dev/null || true
# Use build dir for gitconfig since HOME may not be writable with --user
export GIT_CONFIG_GLOBAL=/app/build/.gitconfig
git config --global --add safe.directory /app 2>/dev/null || true

# Start Xvfb
Xvfb :99 -screen 0 1280x1024x24 -nolisten tcp &
XVFB_PID=$!
sleep 1

# Run Gradle (AWT threads prevent clean shutdown after tests complete).
# Strategy: run Gradle in background, detect completion by output silence
# (no new data for 30s after at least 1KB written), then kill Gradle.
echo "=== Gradle tasks: $TASKS ===" | tee "$OUTPUT"
echo "=== Started: $(date -u '+%Y-%m-%d %H:%M:%S UTC') ===" | tee -a "$OUTPUT"

set +e
DISPLAY=:99 ./gradlew --no-daemon $TASKS >> "$OUTPUT" 2>&1 &
GRADLE_PID=$!

# Poll for completion: detect output silence
ELAPSED=0
QUIET=0
LASTSIZE=0
while [ $ELAPSED -lt 600 ]; do
    # Check if Gradle exited on its own
    if ! kill -0 $GRADLE_PID 2>/dev/null; then
        break
    fi
    # Check output file size for silence
    CURSIZE=$(wc -c < "$OUTPUT" 2>/dev/null || echo 0)
    if [ "$CURSIZE" = "$LASTSIZE" ]; then
        QUIET=$((QUIET + 3))
        # 30s of silence after substantial output means tests are done
        if [ $QUIET -ge 30 ] && [ "$CURSIZE" -gt 1000 ] 2>/dev/null; then
            kill $GRADLE_PID 2>/dev/null
            sleep 2
            kill -9 $GRADLE_PID 2>/dev/null
            break
        fi
    else
        QUIET=0
        LASTSIZE=$CURSIZE
    fi
    sleep 3
    ELAPSED=$((ELAPSED + 3))
done

# Safety: force kill if still running after timeout
if kill -0 $GRADLE_PID 2>/dev/null; then
    kill -9 $GRADLE_PID 2>/dev/null
fi

wait $GRADLE_PID 2>/dev/null
GRADLE_EXIT=$?
set -e

# SIGKILL (137) or SIGTERM (143) from our kill is expected — AWT threads hang
if [ "$GRADLE_EXIT" -eq 137 ] || [ "$GRADLE_EXIT" -eq 143 ]; then
    # If any tests passed, treat the kill as expected (tests completed, AWT hung)
    if grep -c ' PASSED$' "$OUTPUT" 2>/dev/null | grep -qv '^0$'; then
        GRADLE_EXIT=0
    fi
fi

echo "" >> "$OUTPUT"
echo "=== Finished: $(date -u '+%Y-%m-%d %H:%M:%S UTC') ===" >> "$OUTPUT"
echo "=== Gradle exit: $GRADLE_EXIT ===" >> "$OUTPUT"

# Parse and write summary
{
    echo "=== GUI Test Summary ==="

    # Count PASSED/FAILED from Gradle test events
    # grep -c always prints a count; suppress non-zero exit when count is 0
    PASSED=$(grep -c ' PASSED$' "$OUTPUT" 2>/dev/null || true)
    FAILED=$(grep -c ' FAILED$' "$OUTPUT" 2>/dev/null || true)
    SKIPPED=$(grep -c ' SKIPPED$' "$OUTPUT" 2>/dev/null || true)
    echo "PASSED:  $PASSED"
    echo "FAILED:  $FAILED"
    echo "SKIPPED: $SKIPPED"

    # List failures if any
    if [ "${FAILED:-0}" -gt 0 ] 2>/dev/null; then
        echo ""
        echo "=== Failed Tests ==="
        grep ' FAILED$' "$OUTPUT" | sed 's/ FAILED$//' | sort
    fi

    echo ""
    echo "EXIT: $GRADLE_EXIT"
} | tee "$SUMMARY"

# Cleanup
kill $XVFB_PID 2>/dev/null || true
exit "$GRADLE_EXIT"
