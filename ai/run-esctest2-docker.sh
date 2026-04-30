#!/bin/bash
# run-esctest2-docker.sh — Orchestrates esctest2 testing of javi in Docker
# Runs on rdesk. Builds javi, starts Xvfb, runs esctest2 inside javi's shell.

set -e

WORK_DIR="$HOME/javi-esctest2"
RESULTS_DIR="$WORK_DIR/results"
JAVI_SRC="$WORK_DIR/javi-src"

mkdir -p "$RESULTS_DIR"

echo "=== Step 1: Build javi ==="
cd "$JAVI_SRC"
./gradlew shadowJar 2>&1 | tail -5
FATJAR=$(ls build/libs/*-all.jar 2>/dev/null | head -1)
if [ -z "$FATJAR" ]; then
    echo "ERROR: Fat jar not found"
    exit 1
fi
echo "Fat jar: $FATJAR"
cp "$FATJAR" "$WORK_DIR/javi-all.jar"

echo "=== Step 2: Start Xvfb ==="
Xvfb :99 -screen 0 1280x1024x24 -nolisten tcp &
XVFB_PID=$!
sleep 1
export DISPLAY=:99

echo "=== Step 3: Create .javini ==="
# .javini in home dir: shellnew opens a shell on startup
echo "shellnew" > ~/.javini

echo "=== Step 4: Set SHELL to esctest2 runner ==="
export SHELL="$WORK_DIR/esctest2-runner.sh"
chmod +x "$SHELL"

echo "=== Step 5: Run javi (it will :shellnew → run esctest2-runner.sh) ==="
# Run javi with a timeout; the shell will exit when esctest2 finishes,
# then javi has no more shell and we kill it
export TERM=xterm-256color
timeout 300 java -jar "$WORK_DIR/javi-all.jar" -c shellnew 2>"$RESULTS_DIR/javi-stderr.log" || true

echo "=== Step 6: Cleanup ==="
kill $XVFB_PID 2>/dev/null || true

echo "=== Results ==="
if [ -f "$RESULTS_DIR/esctest2.log" ]; then
    echo "esctest2 log exists, $(wc -l < "$RESULTS_DIR/esctest2.log") lines"
    # Show summary (last section with pass/fail counts)
    tail -30 "$RESULTS_DIR/esctest2.log"
else
    echo "ERROR: No esctest2 log found"
    ls -la "$RESULTS_DIR/"
fi

if [ -f "$RESULTS_DIR/esctest2-stderr.log" ]; then
    echo "=== esctest2 stderr ==="
    cat "$RESULTS_DIR/esctest2-stderr.log"
fi
