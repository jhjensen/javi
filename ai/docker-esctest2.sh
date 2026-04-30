#!/bin/bash
# docker-esctest2.sh — Runs inside Docker container
# Builds javi from mounted source, starts Xvfb, runs esctest2 through javi's VT100
set -e

RESULTS_DIR=/results

echo "=== Step 1: Locate javi jar ==="
# Prefer pre-deployed jar at /app/javi.jar, then look in build/libs
if [ -f /app/javi.jar ]; then
    FATJAR=/app/javi.jar
else
    FATJAR=$(find /app/build/libs -name '*-all.jar' 2>/dev/null | head -1)
fi
if [ -z "$FATJAR" ]; then
    echo "Building javi (no pre-built jar found)..."
    git config --global --add safe.directory /app
    git config --global user.email test@test.com
    git config --global user.name test
    cd /app
    [ -d .git ] || git init
    git add -A 2>/dev/null
    git diff-index --quiet HEAD 2>/dev/null || git commit -m init 2>/dev/null
    ./gradlew shadowJar 2>&1 | tail -10
    FATJAR=$(find build/libs -name '*-all.jar' 2>/dev/null | head -1)
fi
if [ -z "$FATJAR" ]; then
    echo "ERROR: Fat jar not found"
    exit 1
fi
echo "Using: $FATJAR"

echo "=== Step 2: Install terminfo ==="
# Install javi's custom terminfo if present
if [ -d /app/terminfo ]; then
    mkdir -p ~/.terminfo
    cp -r /app/terminfo/* ~/.terminfo/ 2>/dev/null || true
    echo "Terminfo installed"
fi

echo "=== Step 3: Start Xvfb ==="
Xvfb :99 -screen 0 1280x1024x24 -nolisten tcp &
XVFB_PID=$!
sleep 2
export DISPLAY=:99
echo "Xvfb started on :99 (pid $XVFB_PID)"

echo "=== Step 4: Configure environment ==="
# Create esctest2 runner as the SHELL
cat > /tmp/esctest-shell.sh << 'SHELLEOF'
#!/bin/bash
# This script is invoked by javi's :shellnew as SHELL
# esctest2 runs here, talking to javi's VT100 via the PTY

sleep 2  # let javi fully initialize

cd /esctest2/esctest

# Run with VT level 2 (VT220), xterm-compatible, increased timeout
python3 esctest.py \
    --expected-terminal=xterm \
    --max-vt-level=2 \
    --xterm-reverse-wrap=383 \
    --logfile=/results/esctest2.log \
    --timeout=3 \
    --no-print-logs \
    2>/results/esctest2-stderr.log

echo $? > /results/esctest2-exit-code.txt

# Signal completion
touch /results/esctest2-done
SHELLEOF
chmod +x /tmp/esctest-shell.sh

export SHELL=/tmp/esctest-shell.sh
export TERM=xterm-256color
export HOME=/root

# Use -c shellnew (runs after full UI init, unlike .javini which runs too early)
# Don't create .javini — it causes NPE because fvc is null during AwtInterface init

echo "=== Step 5: Run javi with esctest2 ==="
echo "Starting javi — esctest2 will run inside javi's terminal emulator..."

# Run javi from /app; -c shellnew opens a shell after full initialization
# The shell forks SHELL env var, which is our esctest2-runner
cd /app
timeout 600 java -jar "$FATJAR" -c shellnew 2>"$RESULTS_DIR/javi-stderr.log" &
JAVI_PID=$!

# Wait for esctest2 to complete (check for done marker)
ELAPSED=0
while [ ! -f /results/esctest2-done ] && [ $ELAPSED -lt 540 ]; do
    sleep 5
    ELAPSED=$((ELAPSED + 5))
    if [ $((ELAPSED % 30)) -eq 0 ]; then
        echo "  waiting... ${ELAPSED}s elapsed"
        # Show progress from log if it exists
        if [ -f /results/esctest2.log ]; then
            LINES=$(wc -l < /results/esctest2.log)
            echo "  esctest2 log: $LINES lines"
        fi
    fi
done

# Kill javi
kill $JAVI_PID 2>/dev/null || true
sleep 1
kill -9 $JAVI_PID 2>/dev/null || true

echo "=== Step 6: Cleanup ==="
kill $XVFB_PID 2>/dev/null || true

echo "=== Results ==="
if [ -f "$RESULTS_DIR/esctest2.log" ]; then
    LINES=$(wc -l < "$RESULTS_DIR/esctest2.log")
    echo "esctest2 log: $LINES lines"
    echo ""
    echo "--- Last 50 lines of esctest2 log ---"
    tail -50 "$RESULTS_DIR/esctest2.log"
else
    echo "WARNING: No esctest2 log found"
fi

if [ -f "$RESULTS_DIR/esctest2-stderr.log" ] && [ -s "$RESULTS_DIR/esctest2-stderr.log" ]; then
    echo ""
    echo "--- esctest2 stderr ---"
    head -50 "$RESULTS_DIR/esctest2-stderr.log"
fi

if [ -f "$RESULTS_DIR/javi-stderr.log" ] && [ -s "$RESULTS_DIR/javi-stderr.log" ]; then
    echo ""
    echo "--- javi stderr (last 20 lines) ---"
    tail -20 "$RESULTS_DIR/javi-stderr.log"
fi

if [ -f "$RESULTS_DIR/esctest2-exit-code.txt" ]; then
    EXIT_CODE=$(cat "$RESULTS_DIR/esctest2-exit-code.txt")
    echo ""
    echo "esctest2 exit code: $EXIT_CODE"
fi
